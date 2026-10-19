package com.aetherglass.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Поиск и проверка Java.
 *
 * <p>Minecraft 1.21.11 требует Java 21 (это указано в самом манифесте версии:
 * {@code javaVersion.majorVersion = 21}), поэтому лаунчер проверяет реальную версию
 * выбранной Java, а не только наличие файла.</p>
 */
public final class JavaRuntime {
    /** Минимальная версия Java для Minecraft 1.21.11. */
    public static final int REQUIRED_MAJOR = 21;

    private static final Pattern VERSION_PATTERN = Pattern.compile("version \"([0-9]+)(?:\\.([0-9]+))?");

    private JavaRuntime() {
    }

    /** Сведения о найденной Java. */
    public record JavaInfo(Path executable, int majorVersion, String versionText, String vendor, String error) {
        public boolean valid() {
            return this.error == null;
        }

        public boolean meetsRequirement() {
            return this.valid() && this.majorVersion >= REQUIRED_MAJOR;
        }
    }

    /** Проверяет указанный исполняемый файл Java, запуская {@code java -version}. */
    public static JavaInfo inspect(Path executable) {
        if (executable == null || executable.toString().isBlank()) {
            return new JavaInfo(null, 0, "", "", "путь к Java не задан");
        }
        Path resolved = executable.toAbsolutePath().normalize();
        if (!Files.isRegularFile(resolved)) {
            return new JavaInfo(resolved, 0, "", "", "файл не найден: " + resolved);
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(resolved.toString(), "-version");
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new JavaInfo(resolved, 0, "", "", "Java не ответила за 20 секунд");
            }
            return parse(resolved, output);
        } catch (IOException exception) {
            return new JavaInfo(resolved, 0, "", "", "не удалось запустить Java: " + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new JavaInfo(resolved, 0, "", "", "проверка Java прервана");
        }
    }

    /** Разбирает вывод {@code java -version}. */
    public static JavaInfo parse(Path executable, String output) {
        Matcher matcher = VERSION_PATTERN.matcher(output);
        if (!matcher.find()) {
            return new JavaInfo(executable, 0, "", "", "не удалось определить версию Java из вывода: " + output.trim());
        }
        int major = Integer.parseInt(matcher.group(1));
        String versionText = matcher.group(1) + (matcher.group(2) == null ? "" : "." + matcher.group(2));
        String vendor = output.contains("OpenJDK") ? "OpenJDK" : (output.contains("Oracle") ? "Oracle" : "неизвестный поставщик");
        return new JavaInfo(executable, major, versionText, vendor, null);
    }

    /**
     * Ищет подходящую Java автоматически: JAVA_HOME, затем {@code java} из PATH,
     * затем типовые каталоги установки.
     */
    public static List<Path> candidates() {
        List<Path> result = new ArrayList<>();
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) {
            result.add(Path.of(javaHome, "bin", executableName()));
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {
                if (entry.isBlank()) {
                    continue;
                }
                Path candidate = Path.of(entry, executableName());
                if (Files.isRegularFile(candidate)) {
                    result.add(candidate);
                }
            }
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            for (Path root : List.of(Path.of("C:\\Program Files\\Java"), Path.of("C:\\Program Files\\Eclipse Adoptium"),
                    Path.of("C:\\Program Files\\Zulu"), Path.of("C:\\Program Files\\BellSoft"),
                    Path.of("C:\\Program Files\\Amazon Corretto"), Path.of("C:\\Program Files\\Semeru"),
                    Path.of("C:\\Program Files\\Microsoft"))) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                            .map(directory -> directory.resolve("bin").resolve(executableName()))
                            .filter(Files::isRegularFile)
                            .forEach(result::add);
                } catch (IOException exception) {
                    // Каталог недоступен — просто пропускаем его.
                }
            }
        } else {
            for (Path root : List.of(Path.of("/usr/lib/jvm"), Path.of("/usr/java"), Path.of("/opt/java"),
                    Path.of("/Library/Java/JavaVirtualMachines"))) {
                if (!Files.isDirectory(root)) {
                    continue;
                }
                try (var stream = Files.list(root)) {
                    stream.filter(Files::isDirectory)
                            .map(directory -> directory.resolve("bin").resolve(executableName()))
                            .filter(Files::isRegularFile)
                            .forEach(result::add);
                } catch (IOException exception) {
                    // Каталог недоступен — просто пропускаем его.
                }
            }
        }
        return result;
    }

    /** Возвращает первую подходящую Java из найденных или null. */
    public static Path detect() {
        for (Path candidate : candidates()) {
            JavaInfo info = inspect(candidate);
            if (info.meetsRequirement()) {
                return info.executable();
            }
        }
        return null;
    }

    public static String executableName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
    }

    /** Объём оперативной памяти компьютера в мегабайтах (для подсказки по объёму кучи). */
    public static long totalMemoryMb() {
        try {
            java.lang.management.OperatingSystemMXBean bean =
                    java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                return extended.getTotalMemorySize() / (1024L * 1024L);
            }
        } catch (RuntimeException exception) {
            // Метод недоступен в этой сборке Java — подсказка просто не показывается.
        }
        return 0L;
    }
}
