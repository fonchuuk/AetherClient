package com.aetherglass.launcher.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * Умный Доктор Крашей и Авто-диагностика профиля:
 * анализирует логи игры, отчёты о вылетах ({@code crash-reports}) и целостность {@code mods/*.jar},
 * выявляет причину сбоя простым языком и предлагает авто-починку в 1 клик.
 */
public final class CrashDoctor {

    private static final Pattern MOD_JAR_PATTERN =
            Pattern.compile("([A-Za-z0-9._+-]+\\.jar)", Pattern.CASE_INSENSITIVE);

    private CrashDoctor() {
    }

    public enum FixType {
        NONE("Починка не требуется"),
        INCREASE_RAM("Увеличить выделенную память ОЗУ до 4096+ МБ"),
        DISABLE_MOD("Отключить конфликтующий мод (.disabled)"),
        DELETE_CORRUPT_JAR("Удалить повреждённый .jar и перекачать моды"),
        RESET_JAVA_PATH("Переключить на встроенную Java 21");

        private final String label;

        FixType(String label) {
            this.label = label;
        }

        public String label() {
            return this.label;
        }
    }

    public record Diagnosis(
            boolean healthy,
            String title,
            String summary,
            String details,
            FixType fixType,
            Path targetFile
    ) {
    }

    /**
     * Полная диагностика папки игры, логов и установленных модов.
     */
    public static Diagnosis analyze(LauncherPaths paths, LauncherConfig config) {
        // 1. Проверяем целостность всех .jar файлов в папке mods/
        Path modsDir = paths.mods();
        if (Files.isDirectory(modsDir)) {
            try (var stream = Files.list(modsDir)) {
                for (Path jar : stream.toList()) {
                    String name = jar.getFileName().toString();
                    if (!name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                        continue;
                    }
                    long size = Files.size(jar);
                    if (size < 128L) {
                        return new Diagnosis(
                                false,
                                "Обнаружен пустой или недокачанный мод",
                                "Файл «" + name + "» весит всего " + size + " байт и вызывает сбой загрузчика Fabric.",
                                "Путь: " + jar.toAbsolutePath(),
                                FixType.DELETE_CORRUPT_JAR,
                                jar
                        );
                    }
                    try (ZipFile zip = new ZipFile(jar.toFile())) {
                        if (zip.size() == 0) {
                            return new Diagnosis(
                                    false,
                                    "Повреждённый архив мода",
                                    "Архив «" + name + "» не содержит записей.",
                                    "Путь: " + jar.toAbsolutePath(),
                                    FixType.DELETE_CORRUPT_JAR,
                                    jar
                            );
                        }
                    } catch (IOException zipErr) {
                        return new Diagnosis(
                                false,
                                "Повреждённый ZIP/JAR заголовок мода",
                                "Мод «" + name + "» повреждён при скачивании и не может быть открыт Java.",
                                "Ошибка: " + zipErr.getMessage() + "\nФайл: " + jar.toAbsolutePath(),
                                FixType.DELETE_CORRUPT_JAR,
                                jar
                        );
                    }
                }
            } catch (IOException ignored) {
            }
        }

        // 2. Читаем свежие логи игры и crash-reports
        String logContent = readRecentLogs(paths);
        if (!logContent.isBlank()) {
            String lower = logContent.toLowerCase(Locale.ROOT);

            if (lower.contains("java.lang.outofmemoryerror") || lower.contains("out of memory")) {
                return new Diagnosis(
                        false,
                        "Нехватка оперативной памяти (OutOfMemoryError)",
                        "Игре не хватило выделенной памяти ОЗУ (" + config.maxMemoryMb() + " МБ).",
                        "Рекомендуется увеличить лимит ОЗУ минимум до 4096 МБ или включить профиль ZGC.",
                        FixType.INCREASE_RAM,
                        null
                );
            }

            if (lower.contains("unsupportedclassversionerror") || lower.contains("class file version 65")) {
                return new Diagnosis(
                        false,
                        "Неподходящая версия Java",
                        "Для Minecraft 1.21.11 и Fabric требуется Java 21 (класс-файлы версии 65.0).",
                        "Лаунчер может автоматически сбросить путь к Java и скачать портативную OpenJDK 21.",
                        FixType.RESET_JAVA_PATH,
                        null
                );
            }

            if (lower.contains("incompatible mod set")
                    || lower.contains("modresolutionexception")
                    || lower.contains("mixinapplyerror")
                    || lower.contains("mixinprepareerror")) {
                Path culprit = findCulpritModJar(logContent, paths.mods());
                String modName = culprit != null ? culprit.getFileName().toString() : "несовместимый мод";
                return new Diagnosis(
                        false,
                        "Конфликт модов или версий Fabric",
                        "Обнаружен конфликт мода «" + modName + "» с текущей версией игры (" + config.selectedVersion() + ").",
                        extractRelevantLines(logContent),
                        culprit != null ? FixType.DISABLE_MOD : FixType.NONE,
                        culprit
                );
            }
        }

        int activeMods = countMods(paths.mods(), ".jar");
        int disabledMods = countMods(paths.mods(), ".jar.disabled");
        return new Diagnosis(
                true,
                "Профиль полностью исправен",
                "Конфликтов и повреждённых модов не обнаружено. Активных модов: "
                        + activeMods + (disabledMods > 0 ? " (отключено: " + disabledMods + ")" : "") + ".",
                "Выделено ОЗУ: " + config.maxMemoryMb() + " МБ · Профиль JVM: " + config.jvmPreset().displayName(),
                FixType.NONE,
                null
        );
    }

    /**
     * Выполняет автоматическое исправление в 1 клик по результату диагностики.
     */
    public static String applyFix(Diagnosis diagnosis, LauncherConfig config, LauncherPaths paths) throws IOException {
        if (diagnosis == null || diagnosis.fixType() == FixType.NONE) {
            return "Исправление не требуется — профиль в порядке.";
        }
        return switch (diagnosis.fixType()) {
            case INCREASE_RAM -> {
                int nextRam = Math.max(4096, config.maxMemoryMb() + 1024);
                config.maxMemoryMb(nextRam);
                config.save(paths.launcherConfig());
                yield "Лимит ОЗУ автоматически увеличен до " + nextRam + " МБ.";
            }
            case DISABLE_MOD -> {
                Path target = diagnosis.targetFile();
                if (target != null && Files.isRegularFile(target)) {
                    Path disabled = target.resolveSibling(target.getFileName().toString() + ".disabled");
                    Files.move(target, disabled, StandardCopyOption.REPLACE_EXISTING);
                    yield "Конфликтующий мод отключён: " + disabled.getFileName();
                }
                yield "Не удалось найти файл конфликтующего мода.";
            }
            case DELETE_CORRUPT_JAR -> {
                Path target = diagnosis.targetFile();
                if (target != null && Files.exists(target)) {
                    Files.deleteIfExists(target);
                    yield "Повреждённый файл удалён: " + target.getFileName();
                }
                yield "Повреждённый файл уже удалён.";
            }
            case RESET_JAVA_PATH -> {
                config.javaExecutable("");
                config.save(paths.launcherConfig());
                yield "Путь к Java сброшен: при запуске будет использована встроенная Java 21.";
            }
            default -> "Готово.";
        };
    }

    private static Path findCulpritModJar(String logContent, Path modsDir) {
        if (!Files.isDirectory(modsDir)) {
            return null;
        }
        Matcher matcher = MOD_JAR_PATTERN.matcher(logContent);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.equalsIgnoreCase("AetherGlass.jar")
                    || candidate.toLowerCase(Locale.ROOT).startsWith("fabric-loader")) {
                continue;
            }
            Path p = modsDir.resolve(candidate);
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    private static String readRecentLogs(LauncherPaths paths) {
        StringBuilder sb = new StringBuilder();
        Path launcherLog = paths.logsDir().resolve("launcher-game.log");
        appendTail(sb, launcherLog, 120);
        Path latestLog = paths.logsDir().resolve("latest.log");
        appendTail(sb, latestLog, 120);

        Path crashDir = paths.base().resolve("crash-reports");
        if (Files.isDirectory(crashDir)) {
            try (var stream = Files.list(crashDir)) {
                Path newest = stream
                        .filter(Files::isRegularFile)
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .orElse(null);
                if (newest != null) {
                    appendTail(sb, newest, 80);
                }
            } catch (IOException ignored) {
            }
        }
        return sb.toString();
    }

    private static void appendTail(StringBuilder out, Path file, int maxLines) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int start = Math.max(0, lines.size() - maxLines);
            for (int i = start; i < lines.size(); i++) {
                out.append(lines.get(i)).append('\n');
            }
        } catch (IOException ignored) {
        }
    }

    private static String extractRelevantLines(String text) {
        String[] lines = text.split("\r?\n");
        List<String> picked = new ArrayList<>();
        for (String line : lines) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("error") || l.contains("exception") || l.contains("incompatible") || l.contains("mixin")) {
                picked.add(line.trim());
                if (picked.size() >= 6) {
                    break;
                }
            }
        }
        return picked.isEmpty() ? "См. вкладку «Логи» для подробностей." : String.join("\n", picked);
    }

    private static int countMods(Path modsDir, String suffix) {
        if (!Files.isDirectory(modsDir)) {
            return 0;
        }
        try (var stream = Files.list(modsDir)) {
            return (int) stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(suffix))
                    .count();
        } catch (IOException e) {
            return 0;
        }
    }
}
