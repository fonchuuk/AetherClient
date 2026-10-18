package com.aetherglass.installer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Поиск Java 21 или новее. Лаунчеру нужна именно 21-я: Minecraft 1.21.11 на
 * более старой версии не запускается.
 *
 * <p>Проверка идёт в три шага: {@code JAVA_HOME}, затем {@code java} из PATH,
 * затем типовые каталоги установки. Версия читается из вывода
 * {@code java -version} — разбор строки вынесен в отдельный метод и
 * покрыт тестом.</p>
 */
public final class JavaFinder {

    /** Минимальная подходящая версия Java. */
    public static final int MINIMUM = 21;

    private JavaFinder() {
    }

    /** Найденная Java или {@code null}. */
    public static Path find() {
        for (Path candidate : candidates()) {
            if (candidate != null && isUsable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Проверяет конкретный исполняемый файл: запускает {@code -version} и разбирает вывод. */
    public static boolean isUsable(Path javaExecutable) {
        try {
            ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-version");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            process.waitFor();
            return versionOf(output) >= MINIMUM;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Достаёт номер версии из вывода {@code java -version}.
     *
     * <p>Вывод выглядит так: {@code openjdk version "21.0.5" 2024-10-15}.
     * Исторически первая цифра после 1. (например, 1.8.0) означает 8, поэтому
     * старый формат учитывается отдельно.</p>
     *
     * @return номер версии (21 для «21.0.5», 8 для «1.8.0_362») или {@code -1}
     */
    public static int versionOf(String output) {
        int start = output.indexOf('"');
        int end = output.indexOf('"', start + 1);
        if (start < 0 || end <= start) {
            return -1;
        }
        String version = output.substring(start + 1, end).trim();
        String[] parts = version.split("[._+-]");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return -1;
        }
        try {
            int major = Integer.parseInt(parts[0]);
            if (major == 1 && parts.length > 1) {
                major = Integer.parseInt(parts[1]);
            }
            return major;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Кандидаты на проверку: JAVA_HOME, PATH, типовые каталоги установки. */
    static List<Path> candidates() {
        List<Path> result = new ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            Path bin = Path.of(javaHome).resolve("bin");
            result.add(bin.resolve("javaw.exe"));
            result.add(bin.resolve("java.exe"));
        }
        result.add(Path.of("javaw.exe"));
        result.add(Path.of("java.exe"));
        for (String root : List.of("C:\\Program Files\\Java",
                "C:\\Program Files\\Eclipse Adoptium",
                "C:\\Program Files\\Microsoft",
                "C:\\Program Files\\Zulu",
                "C:\\Program Files\\BellSoft",
                "C:\\Program Files\\Amazon Corretto",
                "C:\\Program Files\\Semeru",
                "C:\\Program Files\\GraalVM",
                "C:\\Program Files\\Oracle\\Java")) {
            result.addAll(installedJava(root));
        }
        return result;
    }

    /** Все {@code java.exe} и {@code javaw.exe} внутри типового каталога установки. */
    static List<Path> installedJava(String root) {
        List<Path> result = new ArrayList<>();
        Path directory = Path.of(root);
        if (!Files.isDirectory(directory)) {
            return result;
        }
        try (var stream = Files.list(directory)) {
            stream.filter(Files::isDirectory).sorted().forEach(javaHome -> {
                Path bin = javaHome.resolve("bin");
                Path javaw = bin.resolve("javaw.exe");
                Path java = bin.resolve("java.exe");
                if (Files.isRegularFile(javaw)) {
                    result.add(javaw);
                } else if (Files.isRegularFile(java)) {
                    result.add(java);
                }
            });
        } catch (IOException e) {
            // Каталог может быть недоступен: это не ошибка, просто пропускаем его.
            return result;
        }
        return result;
    }

    /** Для отчёта: понятное имя найденной Java. */
    public static String describe(Path javaExecutable) {
        if (javaExecutable == null) {
            return "не найдена";
        }
        return javaExecutable.toAbsolutePath().toString().toLowerCase(Locale.ROOT).endsWith("javaw.exe")
                ? "javaw (" + javaExecutable.getParent().getParent() + ")"
                : javaExecutable.toString();
    }
}
