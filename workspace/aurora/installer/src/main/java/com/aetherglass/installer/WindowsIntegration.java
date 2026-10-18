package com.aetherglass.installer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Настоящая интеграция с Windows: запись в «Программы и компоненты» и ярлыки.
 *
 * <p>Ничего не изобретается: реестр правится штатной {@code reg.exe}, ярлыки
 * создаются сценарием VBScript через {@code cscript} — оба средства есть в
 * любой Windows. Каждая команда и текст сценария собираются отдельными
 * методами, поэтому их можно проверять тестами без Windows.</p>
 */
public final class WindowsIntegration {

    /** Типы значений реестра, которые мы записываем. */
    public enum ValueType {
        STRING("REG_SZ"),
        NUMBER("REG_DWORD");

        private final String token;

        ValueType(String token) {
            this.token = token;
        }
    }

    private WindowsIntegration() {
    }

    /** Команда записи одного значения реестра. */
    public static List<String> regAdd(String key, String name, String value, ValueType type) {
        List<String> command = new ArrayList<>(List.of("reg", "add", key, "/v", name, "/t", type.token));
        command.add("/d");
        command.add(value);
        command.add("/f");
        return command;
    }

    /** Команда чтения одного значения реестра. */
    public static List<String> regQuery(String key, String name) {
        return List.of("reg", "query", key, "/v", name);
    }

    /** Команда удаления ключа реестра. */
    public static List<String> regDelete(String key) {
        return List.of("reg", "delete", key, "/f");
    }

    /**
     * Сценарий создания ярлыка. {@code WScript.Shell} — штатный объект Windows;
     * по-другому .lnk из Java создать нельзя без сторонних библиотек.
     */
    public static String shortcutScript(Path target, Path workingDir, Path icon, Path link, String description) {
        Path directory = workingDir != null ? workingDir : target.getParent();
        String folder = directory != null ? directory.toString() : target.toString();
        return "Option Explicit\n"
                + "Dim shell, shortcut\n"
                + "Set shell = CreateObject(\"WScript.Shell\")\n"
                + "Set shortcut = shell.CreateShortcut(" + vbs(link.toString()) + ")\n"
                + "shortcut.TargetPath = " + vbs(target.toString()) + "\n"
                + "shortcut.WorkingDirectory = " + vbs(folder) + "\n"
                + "shortcut.IconLocation = " + vbs(icon.toString() + ",0") + "\n"
                + "shortcut.Description = " + vbs(description) + "\n"
                + "shortcut.Save\n";
    }

    /** Сценарий удаления файла: пригодится, если ярлык занят проводником. */
    public static String deleteScript(Path file) {
        return "Option Explicit\n"
                + "Dim fs\n"
                + "Set fs = CreateObject(\"Scripting.FileSystemObject\")\n"
                + "If fs.FileExists(" + vbs(file.toString()) + ") Then fs.DeleteFile " + vbs(file.toString()) + "\n";
    }

    /** Экранирование строки для VBScript: кавычки удваиваются. */
    static String vbs(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /** Запись в «Программы и компоненты»: все значения, по которым Windows удаляет программу. */
    public static List<List<String>> uninstallRegistration(Path installDir, Path launcher, Path uninstaller,
                                                           Path javaExecutable, String version, long sizeBytes) {
        String quotedJava = InstallPaths.quote(javaExecutable);
        String uninstallCommand = quotedJava + " -jar " + InstallPaths.quote(uninstaller) + " --uninstall";
        String modifyCommand = quotedJava + " -jar " + InstallPaths.quote(uninstaller) + " --repair";
        String key = InstallPaths.uninstallKey();
        Map<String, String> values = new LinkedHashMap<>();
        values.put("DisplayName", "AetherGlass Launcher");
        values.put("DisplayVersion", version);
        values.put("Publisher", "AetherGlass Client");
        values.put("InstallLocation", installDir.toAbsolutePath().toString());
        values.put("DisplayIcon", launcher.toAbsolutePath().toString() + ",0");
        values.put("UninstallString", uninstallCommand);
        values.put("ModifyPath", modifyCommand);
        values.put("NoRepair", "0");
        values.put("EstimatedSize", String.valueOf(Math.max(1, sizeBytes / 1024)));

        List<List<String>> commands = new ArrayList<>();
        values.forEach((name, value) -> commands.add(regAdd(key, name, value,
                name.equals("NoRepair") ? ValueType.NUMBER : ValueType.STRING)));
        return commands;
    }

    /** Запускает команду и возвращает её код завершения. */
    public static int run(List<String> command) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /** Запуск команды «тихо»: ошибки не роняют установщик, а возвращаются кодом. */
    public static int runQuietly(List<String> command) {
        try {
            return run(command);
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Пишет сценарий во временный файл и выполняет его через cscript. */
    public static int runScript(String script, String name) throws IOException {
        Path file = Files.createTempFile("aetherglass-" + name + "-", ".vbs");
        try {
            Files.writeString(file, script, StandardCharsets.UTF_8);
            return run(List.of("cscript", "//nologo", file.toAbsolutePath().toString()));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    /** Признак Windows: команды реестра и cscript есть только там. */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
