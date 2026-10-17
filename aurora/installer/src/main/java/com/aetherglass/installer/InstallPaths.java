package com.aetherglass.installer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Function;

/**
 * Пути установки. Все значения считаются из переменных окружения Windows,
 * поэтому класс ничего не «придумывает»: если переменной нет — путь не
 * выдумывается, а помещается в каталог пользователя.
 *
 * <p>Переменные окружения передаются снаружи (функцией), поэтому пути можно
 * проверять тестами без Windows.</p>
 */
public final class InstallPaths {

    /** Имя продукта: каталог установки и ключ реестра. */
    public static final String PRODUCT = "AetherGlass";

    private final Function<String, String> environment;
    private final Path userHome;

    public InstallPaths(Function<String, String> environment, Path userHome) {
        this.environment = environment;
        this.userHome = userHome;
    }

    /** Пути текущей системы. */
    public static InstallPaths system() {
        return new InstallPaths(System::getenv, Path.of(System.getProperty("user.home", ".")));
    }

    /** Каталог установки по умолчанию: в данных пользователя, без прав администратора. */
    public Path defaultInstallDir() {
        String localAppData = environment.apply("LOCALAPPDATA");
        Path base = localAppData != null && !localAppData.isBlank()
                ? Path.of(localAppData)
                : userHome.resolve("AppData").resolve("Local");
        return base.resolve(PRODUCT);
    }

    /** Куда лаунчер складывает настройки, аккаунты и логи: не удаляем при обновлении. */
    public Path userDataDir() {
        String appData = environment.apply("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : userHome.resolve("AppData").resolve("Roaming");
        return base.resolve(PRODUCT);
    }

    /** Рабочий стол пользователя или {@code null}, если путь неизвестен. */
    public Path desktopDir() {
        String value = environment.apply("USERPROFILE");
        if (value == null || value.isBlank()) {
            return null;
        }
        Path desktop = Path.of(value).resolve("Desktop");
        return Files.isDirectory(desktop) ? desktop : null;
    }

    /** Каталог меню «Пуск» → «Программы» или {@code null}, если путь неизвестен. */
    public Path startMenuDir() {
        String appData = environment.apply("APPDATA");
        if (appData == null || appData.isBlank()) {
            return null;
        }
        Path programs = Path.of(appData)
                .resolve("Microsoft").resolve("Windows").resolve("Start Menu").resolve("Programs");
        return Files.isDirectory(programs) ? programs : null;
    }

    /** Ключ реестра для записи в «Программы и компоненты». */
    public static String uninstallKey() {
        return "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\" + PRODUCT;
    }

    /** Приводит путь к виду, понятному командной строке Windows. */
    public static String quote(Path path) {
        String text = path.toAbsolutePath().toString();
        return "\"" + text + "\"";
    }

    /** Имя файла ярлыка без расширения. */
    public static String shortcutName() {
        return PRODUCT + " Launcher";
    }

    /** Определяет разрядность системы по переменной PROCESSOR_ARCHITECTURE. */
    public boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win");
    }
}
