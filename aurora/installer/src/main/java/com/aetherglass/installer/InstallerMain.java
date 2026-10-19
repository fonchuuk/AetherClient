package com.aetherglass.installer;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/**
 * Точка входа установщика.
 *
 * <p>Режимы запуска (именно их прописывает инсталлятор в реестр Windows):
 * <ul>
 *   <li>без ключей — обычная установка или обновление (окно мастера);</li>
 *   <li>{@code --uninstall [каталог]} — удаление;</li>
 *   <li>{@code --repair [каталог]} — восстановление или обновление файлов;</li>
 *   <li>{@code --console} — работа без графического интерфейса.</li>
 * </ul>
 * Каталог можно указать сразу; если не указан, берётся каталог установки по
 * умолчанию. Код завершения: 0 — успех, 1 — ошибка.
 */
public final class InstallerMain {

    /** Версия устанавливаемого лаунчера: совпадает с версией проекта. */
    public static final String VERSION = version();

    private InstallerMain() {
    }

    public static void main(String[] args) {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        out.println("AetherGlass Launcher — установщик " + VERSION);

        if (has(args, "--help") || has(args, "-h")) {
            usage().forEach(out::println);
            System.exit(0);
        }
        boolean uninstall = has(args, "--uninstall");
        boolean repair = has(args, "--repair");
        boolean console = has(args, "--console") || !InstallerUi.available();
        Path directory = path(args);

        InstallPaths paths = InstallPaths.system();
        Path target = directory != null ? directory : paths.defaultInstallDir();

        if (console) {
            System.exit(consoleMode(out, paths, target, uninstall, repair));
            return;
        }
        if (uninstall) {
            if (!confirm(target)) {
                out.println("Удаление отменено");
                System.exit(0);
            }
        }
        InstallerUi.open(paths, VERSION);
    }

    /** Работа без окон: устанавливает, восстанавливает или удаляет, печатая журнал. */
    static int consoleMode(PrintStream out, InstallPaths paths, Path target, boolean uninstall, boolean repair) {
        Installation installation = new Installation(paths, VERSION);
        boolean ok;
        if (uninstall) {
            ok = installation.uninstall(target, true);
        } else if (repair) {
            ok = installation.repair(target);
        } else {
            ok = installation.install(target, true, true, JavaFinder.find());
        }
        for (String line : installation.log()) {
            out.println("  • " + line);
        }
        out.println(ok ? "Готово." : "Не удалось.");
        return ok ? 0 : 1;
    }

    /** Подтверждение удаления в графическом режиме. */
    private static boolean confirm(Path target) {
        InstallManifest manifest = InstallManifest.read(target.resolve("install.json"));
        String message = manifest.isEmpty()
                ? "Удалить AetherGlass Launcher из " + target + "?"
                : "Удалить AetherGlass Launcher " + manifest.version() + " из " + target
                + "? Настройки и аккаунты сохранятся.";
        int answer = javax.swing.JOptionPane.showConfirmDialog(null, message, "Удаление",
                javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.QUESTION_MESSAGE);
        return answer == javax.swing.JOptionPane.YES_OPTION;
    }

    private static boolean has(String[] args, String key) {
        for (String arg : args) {
            if (arg.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /** Каталог из аргументов: первый аргумент, который не начинается с «--». */
    static Path path(String[] args) {
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                return Path.of(arg);
            }
        }
        return null;
    }

    /** Версия из ресурсов установщика; если ресурсов нет — из манифеста jar. */
    private static String version() {
        try (var stream = InstallerMain.class.getResourceAsStream("/version.txt")) {
            if (stream != null) {
                String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        } catch (java.io.IOException e) {
            // Ниже есть запасной вариант: берём версию из манифеста.
        }
        String fallback = InstallerMain.class.getPackage().getImplementationVersion();
        return fallback != null ? fallback : "1.0.0";
    }

    /** Справка по ключам: печатается по {@code --help}. */
    static List<String> usage() {
        return List.of(
                "AetherGlass Launcher — установщик",
                "  без ключей            установка или обновление",
                "  --uninstall [каталог] удаление (настройки сохраняются)",
                "  --repair [каталог]    восстановление файлов",
                "  --console             без графического интерфейса");
    }
}
