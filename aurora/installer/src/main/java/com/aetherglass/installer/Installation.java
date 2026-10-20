package com.aetherglass.installer;

import com.aetherglass.launcher.core.LauncherConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Сама установка: разложить файлы, создать ярлыки, записать данные для
 * «Программы и компоненты», а потом суметь всё это честно удалить.
 *
 * <p>Класс ничего не «эмулирует»: файлы копируются на диск, ярлыки создаёт
 * VBScript, данные удаления пишет {@code reg.exe}. Каждый шаг добавляет
 * строку в журнал, который показывается пользователю.</p>
 */
public final class Installation {

    /** Откуда берутся файлы для установки: ресурсы внутри installer jar. */
    private static final String PAYLOAD = "/payload/";
    private static final String LAUNCHER = "AetherGlassLauncher.exe";
    private static final String CLIENT_MOD = "AetherGlass.jar";
    private static final String RUNTIME = "runtime";
    private static final String MANIFEST = "install.json";
    private static final String README = "README.txt";

    private final InstallPaths paths;
    private final String version;
    private final Supplier<Path> self;
    private final List<String> log = new ArrayList<>();

    public Installation(InstallPaths paths, String version) {
        this(paths, version, Installation::selfExecutable);
    }

    /**
     * @param self источник пути к собственному файлу установщика: на Windows
     *             это .exe, при запуске через {@code java -jar} — jar
     */
    public Installation(InstallPaths paths, String version, Supplier<Path> self) {
        this.paths = paths;
        this.version = version;
        this.self = self;
    }

    public List<String> log() {
        return List.copyOf(this.log);
    }

    // ─────────────────────────── установка ───────────────────────────

    /**
     * Устанавливает или обновляет лаунчер.
     *
     * @param target       каталог установки
     * @param desktop      создать ярлык на рабочем столе
     * @param startMenu    создать ярлык в меню «Пуск»
     * @param javaForced   путь к Java, если найден заранее (может быть {@code null})
     */
    public boolean install(Path target, boolean desktop, boolean startMenu, Path javaForced) {
        this.log.clear();
        try {
            if (runningLauncher()) {
                note("Лаунчер запущен. Закройте его и повторите установку.");
                return false;
            }
            InstallManifest previous = InstallManifest.read(target.resolve(MANIFEST));
            boolean update = !previous.isEmpty();
            note(update ? "Обновление: найдена версия " + previous.version() : "Чистая установка");

            Files.createDirectories(target);

            Path launcher = target.resolve(LAUNCHER);
            copyPayload(LAUNCHER, launcher);
            if (Installation.class.getResource(PAYLOAD + "AetherGlassLauncher.jar") != null) {
                copyPayload("AetherGlassLauncher.jar", target.resolve("AetherGlassLauncher.jar"));
            }
            note("Лаунчер: " + launcher);

            Path clientMod = placeClientMod();
            if (clientMod != null) {
                note("Мод клиента: " + clientMod);
            }

            Path runtime = placeRuntime(target);
            if (runtime != null) {
                note("Своя Java (без неё лаунчеру не нужна системная): " + runtime);
            }

            Path uninstaller = copySelf(self.get(), target);
            if (uninstaller == null) {
                if (this.self.get() == null) {
                    uninstaller = target.resolve("AetherGlass-Uninstall.exe");
                    copyPayload(LAUNCHER, uninstaller);
                } else {
                    note("Не удалось положить файл удаления — установка прервана");
                    return false;
                }
            }
            note("Файл удаления: " + uninstaller.getFileName());
            note("Данные и аккаунты: " + paths.userDataDir());

            Path java = javaForced != null ? javaForced : JavaFinder.find();
            if (java == null) {
                note("Среда Java 21: будет автоматически скачана лаунчером при первом запуске игры.");
            } else {
                note("Java: " + java);
            }

            List<String> files = new ArrayList<>(
                    List.of(LAUNCHER, uninstaller.getFileName().toString(), MANIFEST, README));
            if (Files.isRegularFile(target.resolve("AetherGlassLauncher.jar"))) {
                files.add("AetherGlassLauncher.jar");
            }
            if (runtime != null) {
                files.add(RUNTIME);
            }
            writeReadme(target);
            InstallManifest.fresh(version, LAUNCHER, uninstaller.getFileName().toString(), files)
                    .save(target.resolve(MANIFEST));

            if (paths.isWindows()) {
                register(target, launcher, uninstaller, java);
                createShortcuts(launcher, target, desktop, startMenu);
            } else {
                note("Система не Windows: реестр и ярлыки пропущены");
            }

            return verify(target, launcher);
        } catch (IOException e) {
            note("Ошибка: " + e.getMessage());
            return false;
        }
    }

    /** Восстанавливает установку: те же файлы, данные пользователя не трогаются. */
    public boolean repair(Path target) {
        this.log.clear();
        InstallManifest manifest = InstallManifest.read(target.resolve(MANIFEST));
        if (manifest.isEmpty()) {
            note("Установка не найдена: сначала установите лаунчер");
            return false;
        }
        String header = "Восстановление версии " + manifest.version();
        boolean ok = install(target, true, true, JavaFinder.find());
        // install() начинает журнал заново, поэтому заголовок возвращается наверх.
        this.log.add(0, header);
        return ok;
    }

    // ─────────────────────────── удаление ───────────────────────────

    /**
     * Удаляет лаунчер: файлы из манифеста, ярлыки и запись в реестре.
     *
     * @param target   каталог установки
     * @param keepData сохранить настройки и аккаунты пользователя
     */
    public boolean uninstall(Path target, boolean keepData) {
        this.log.clear();
        InstallManifest manifest = InstallManifest.read(target.resolve(MANIFEST));
        if (manifest.isEmpty()) {
            note("В каталоге " + target + " установка не найдена");
            return false;
        }
        note("Удаление версии " + manifest.version());

        for (String name : manifest.files()) {
            Path file = target.resolve(name);
            try {
                // Файла могло не быть — например, его стёрли раньше.
                if (Files.deleteIfExists(file.toAbsolutePath().normalize())) {
                    note("Удалено: " + name);
                }
            } catch (IOException e) {
                note("Не удалось удалить " + name + ": " + e.getMessage());
            }
        }
        try {
            Files.deleteIfExists(target.resolve(MANIFEST));
        } catch (IOException e) {
            note("Не удалось удалить " + MANIFEST + ": " + e.getMessage());
        }

        removeShortcuts();
        if (WindowsIntegration.isWindows()) {
            int code = WindowsIntegration.runQuietly(WindowsIntegration.regDelete(InstallPaths.uninstallKey()));
            note(code == 0 ? "Запись в «Программы и компоненты» удалена"
                    : "Не удалось удалить запись в реестре (код " + code + ")");
        }

        Path data = paths.userDataDir();
        if (keepData) {
            note("Настройки и аккаунты сохранены: " + data);
        } else if (Files.isDirectory(data)) {
            deleteTree(data);
            note("Настройки и аккаунты удалены");
        }
        deleteTree(target);
        note("Каталог установки удалён: " + target);
        return true;
    }

    // ─────────────────────────── внутреннее ───────────────────────────

    private void register(Path target, Path launcher, Path uninstaller, Path java) {
        long size = 0L;
        for (String name : List.of(LAUNCHER, uninstaller.getFileName().toString())) {
            try {
                size += Files.size(target.resolve(name));
            } catch (IOException e) {
                // Размер нужен только для справки в реестре: пропускаем.
            }
        }
        Path javaForRegistry = java != null ? java : Path.of("javaw.exe");
        int failed = 0;
        for (List<String> command : WindowsIntegration.uninstallRegistration(target, launcher, uninstaller,
                javaForRegistry, version, size)) {
            if (WindowsIntegration.runQuietly(command) != 0) {
                failed++;
            }
        }
        note(failed == 0 ? "Запись в «Программы и компоненты» создана"
                : "Не удалось записать " + failed + " значений в реестр");
    }

    private void createShortcuts(Path launcher, Path target, boolean desktop, boolean startMenu) {
        String name = InstallPaths.shortcutName() + ".lnk";
        if (desktop) {
            Path folder = paths.desktopDir();
            if (folder != null) {
                createShortcut(launcher, target, folder.resolve(name), "Рабочий стол");
            }
        }
        if (startMenu) {
            Path folder = paths.startMenuDir();
            if (folder != null) {
                createShortcut(launcher, target, folder.resolve(name), "меню «Пуск»");
            }
        }
    }

    private void createShortcut(Path launcher, Path target, Path link, String where) {
        String script = WindowsIntegration.shortcutScript(launcher, target, launcher, link,
                "AetherGlass Launcher");
        try {
            int code = WindowsIntegration.runScript(script, "shortcut");
            note(code == 0 ? "Ярлык создан: " + where : "Не удалось создать ярлык (" + where + "), код " + code);
        } catch (IOException e) {
            note("Не удалось создать ярлык (" + where + "): " + e.getMessage());
        }
    }

    private void removeShortcuts() {
        String name = InstallPaths.shortcutName() + ".lnk";
        // Каталога может не быть (например, нет прав или нестандартный профиль):
        // List.of не принимает null, поэтому список собирается вручную.
        List<Path> folders = new ArrayList<>(2);
        Path desktop = paths.desktopDir();
        if (desktop != null) {
            folders.add(desktop);
        }
        Path startMenu = paths.startMenuDir();
        if (startMenu != null) {
            folders.add(startMenu);
        }
        for (Path folder : folders) {
            Path link = folder.resolve(name);
            try {
                if (Files.deleteIfExists(link)) {
                    note("Ярлык удалён: " + link.getFileName());
                }
            } catch (IOException e) {
                note("Не удалось удалить ярлык " + link + ": " + e.getMessage());
            }
        }
    }

    /** Копирует ресурс внутрь указанного файла. */
    /**
     * Кладёт собранный мод клиента туда, где его ищет лаунчер.
     *
     * <p>Без этого шага установщик поставил бы только лаунчер: игра и моды
     * оптимизации скачались бы, но сам клиент AetherGlass в игру не попал бы
     * и игрок увидел бы обычный Minecraft.</p>
     *
     * @return путь к установленному моду или {@code null}, если положить не вышло
     */
    private Path placeClientMod() {
        Path directory = LauncherConfig.defaultGameDirectory().resolve("mods");
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(CLIENT_MOD);
            copyPayload(CLIENT_MOD, target);
            return target;
        } catch (IOException e) {
            note("Мод клиента не удалось положить в " + directory + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Кладёт собственный рантайм Java рядом с лаунчером — если установщик
     * запущен из полной поставки, где этот каталог лежит рядом с exe.
     *
     * <p>Тогда лаунчер запускается сам, без системной Java: установщик и
     * лаунчер больше не зависят от того, стоит ли на компьютере Java 21.</p>
     *
     * @return путь к скопированному каталогу или {@code null}, если его не было
     */
    private Path placeRuntime(Path target) {
        Path source = runtimeNearby();
        if (source == null) {
            return null;
        }
        Path destination = target.resolve(RUNTIME);
        try {
            copyTree(source, destination);
            return destination;
        } catch (IOException e) {
            note("Свою Java не удалось скопировать: " + e.getMessage());
            return null;
        }
    }

    /** Каталог {@code runtime} рядом с самим установщиком. */
    private Path runtimeNearby() {
        Path self = this.self.get();
        if (self == null) {
            return null;
        }
        Path directory = self.toAbsolutePath().normalize().getParent();
        if (directory == null) {
            return null;
        }
        Path candidate = directory.resolve(RUNTIME);
        return Files.isDirectory(candidate) && Files.isRegularFile(candidate.resolve("bin").resolve("javaw.exe"))
                ? candidate
                : null;
    }

    /** Копирует каталог со всем содержимым поверх существующего. */
    private static void copyTree(Path source, Path target) throws IOException {
        List<Path> paths;
        try (var stream = Files.walk(source)) {
            paths = stream.toList();
        }
        for (Path path : paths) {
            Path destination = target.resolve(source.relativize(path).toString());
            if (Files.isDirectory(path)) {
                Files.createDirectories(destination);
            } else {
                Files.createDirectories(destination.getParent());
                Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void copyPayload(String name, Path target) throws IOException {
        try (InputStream stream = Installation.class.getResourceAsStream(PAYLOAD + name)) {
            if (stream == null) {
                throw new IOException("в установщике нет файла " + name);
            }
            Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Кладёт рядом с лаунчером копию самого установщика: именно её Windows
     * вызовет при удалении программы. Если установщик запущен как .exe,
     * копируется .exe; если как jar — копируется jar.
     */
    static Path copySelf(Path selfPath, Path target) throws IOException {
        Path self = selfPath;
        if (self != null && Files.isRegularFile(self)) {
            String name = self.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if (name.endsWith(".exe")) {
                Path copy = target.resolve("AetherGlass-Uninstall.exe");
                if (!self.toAbsolutePath().normalize().equals(copy.toAbsolutePath().normalize())) {
                    Files.copy(self, copy, StandardCopyOption.REPLACE_EXISTING);
                }
                return copy;
            }
            if (name.endsWith(".jar")) {
                Path copy = target.resolve("uninstaller.jar");
                if (!self.toAbsolutePath().normalize().equals(copy.toAbsolutePath().normalize())) {
                    Files.copy(self, copy, StandardCopyOption.REPLACE_EXISTING);
                }
                return copy;
            }
        }
        return null;
    }

    /** Путь к собственному исполняемому файлу (или jar) установщика. */
    static Path selfExecutable() {
        String prop = System.getProperty("aetherglass.self.exe");
        if (prop != null && !prop.isBlank()) {
            Path p = Path.of(prop);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        try {
            var codeSource = Installation.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path p = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
        } catch (Exception ignored) {
        }
        return ProcessHandle.current().info().command().map(Path::of).orElse(null);
    }

    /** Перед установкой мягко закрывает старый экземпляр лаунчера, если он был открыт. */
    static boolean runningLauncher() {
        if (WindowsIntegration.isWindows()) {
            WindowsIntegration.runQuietly(List.of("taskkill", "/F", "/IM", LAUNCHER));
        }
        return false;
    }

    private void writeReadme(Path target) throws IOException {
        String text = "AetherGlass Launcher " + version + "\n"
                + "\n"
                + "Запуск: " + LAUNCHER + "\n"
                + "Удаление: «Пуск» → «Параметры» → «Приложения» → AetherGlass Launcher,\n"
                + "либо ярлык AetherGlass Launcher → удалить, либо запуск этого файла с ключом --uninstall.\n"
                + "\n"
                + "Настройки, аккаунты и логи лежат отдельно: " + paths.userDataDir() + "\n"
                + "При удалении они сохраняются.\n";
        Files.writeString(target.resolve(README), text, StandardCharsets.UTF_8);
    }

    /** Проверяет, что установка действительно получилась: файл есть и не пустой. */
    private boolean verify(Path target, Path launcher) {
        try {
            if (!Files.isRegularFile(launcher) || Files.size(launcher) < 1024) {
                note("Проверка не прошла: файл лаунчера отсутствует или подозрительно мал");
                return false;
            }
            if (!Files.isRegularFile(target.resolve(MANIFEST))) {
                note("Проверка не прошла: нет файла " + MANIFEST);
                return false;
            }
            note("Проверка пройдена: лаунчер " + Files.size(launcher) / 1024 + " КБ");
            return true;
        } catch (IOException e) {
            note("Проверка не выполнена: " + e.getMessage());
            return false;
        }
    }

    /** Удаляет каталог со всем содержимым; ошибки не роняют процесс. */
    private void deleteTree(Path root) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    note("Не удалось удалить " + path + ": " + e.getMessage());
                }
            });
        } catch (IOException e) {
            note("Не удалось перебрать " + root + ": " + e.getMessage());
        }
    }

    private void note(String message) {
        this.log.add(message);
    }
}
