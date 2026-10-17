package com.aetherglass.installer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Описание установленной копии: версия, каталог, список файлов.
 *
 * <p>Формат — обычный JSON, но читается и пишется своим кодом: сторонних
 * библиотек у установщика нет. Файл нужен, чтобы отличать обновление от
 * чистой установки и чтобы удалять именно те файлы, которые мы положили.</p>
 */
public final class InstallManifest {

    private static final String VERSION = "version";
    private static final String INSTALLED_AT = "installedAt";
    private static final String LAUNCHER = "launcher";
    private static final String UNINSTALLER = "uninstaller";
    private static final String FILES = "files";

    private final String version;
    private final String installedAt;
    private final String launcher;
    private final String uninstaller;
    private final List<String> files;

    public InstallManifest(String version, String installedAt, String launcher, String uninstaller,
                           List<String> files) {
        this.version = version == null ? "" : version;
        this.installedAt = installedAt == null ? "" : installedAt;
        this.launcher = launcher == null ? "" : launcher;
        this.uninstaller = uninstaller == null ? "" : uninstaller;
        this.files = files == null ? List.of() : List.copyOf(files);
    }

    /** Новый манифест для только что выполненной установки. */
    public static InstallManifest fresh(String version, String launcher, String uninstaller, List<String> files) {
        return new InstallManifest(version, LocalDate.now().toString(), launcher, uninstaller, files);
    }

    public String version() {
        return this.version;
    }

    public String installedAt() {
        return this.installedAt;
    }

    public String launcher() {
        return this.launcher;
    }

    public String uninstaller() {
        return this.uninstaller;
    }

    public List<String> files() {
        return this.files;
    }

    public boolean isEmpty() {
        return this.version.isEmpty();
    }

    /** Читает манифест; если файла нет или он испорчен — возвращает пустой манифест. */
    public static InstallManifest read(Path file) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new InstallManifest("", "", "", "", List.of());
        }
        return parse(text);
    }

    /** Разбор текста без внешних библиотек: по одному ключу на строку. */
    public static InstallManifest parse(String text) {
        Map<String, String> values = new LinkedHashMap<>();
        List<String> files = new ArrayList<>();
        try {
            for (String raw : text.split("\\R")) {
                String line = raw.trim();
                if (line.isEmpty() || line.equals("{") || line.equals("}")) {
                    continue;
                }
                int colon = line.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                String key = unquote(line.substring(0, colon).trim());
                String value = unquote(line.substring(colon + 1).trim().replaceAll(",$", ""));
                if (key.equals(FILES)) {
                    files.addAll(parseList(value));
                } else {
                    values.put(key, value);
                }
            }
        } catch (RuntimeException e) {
            // Испорченный файл не должен ломать установщик: считаем, что установки нет.
            return new InstallManifest("", "", "", "", List.of());
        }
        return new InstallManifest(values.get(VERSION), values.get(INSTALLED_AT),
                values.get(LAUNCHER), values.get(UNINSTALLER), files);
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        String inner = value.replace("[", "").replace("]", "").trim();
        if (inner.isEmpty()) {
            return result;
        }
        for (String part : inner.split(",")) {
            String item = unquote(part.trim());
            if (!item.isEmpty()) {
                result.add(item);
            }
        }
        return result;
    }

    private static String unquote(String value) {
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return text;
    }

    /** Текст манифеста в том же простом формате. */
    public String write() {
        StringBuilder out = new StringBuilder(256);
        out.append("{\n");
        append(out, VERSION, this.version, true);
        append(out, INSTALLED_AT, this.installedAt, true);
        append(out, LAUNCHER, this.launcher, true);
        append(out, UNINSTALLER, this.uninstaller, true);
        out.append("  \"").append(FILES).append("\": [");
        for (int index = 0; index < this.files.size(); index++) {
            out.append(index == 0 ? "" : ", ").append('"').append(escape(this.files.get(index))).append('"');
        }
        out.append("]\n}\n");
        return out.toString();
    }

    private static void append(StringBuilder out, String key, String value, boolean comma) {
        out.append("  \"").append(key).append("\": \"").append(escape(value)).append('"');
        out.append(comma ? ",\n" : "\n");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Сохраняет манифест; каталог создаётся при необходимости. */
    public void save(Path file) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, write(), StandardCharsets.UTF_8);
    }
}
