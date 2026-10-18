package com.aetherglass.launcher.core;

import com.aetherglass.launcher.auth.MicrosoftClientId;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки лаунчера. Хранятся в читаемом JSON-файле в каталоге игры.
 *
 * <p>Все значения проходят проверку: повреждённый или рукоправный файл не приводит к падению —
 * вместо него подставляются значения по умолчанию.</p>
 */
public final class LauncherConfig {
    public static final int MIN_MEMORY_MB = 512;
    public static final int DEFAULT_MIN_MEMORY_MB = 1024;
    public static final int DEFAULT_MAX_MEMORY_MB = 4096;

    private Path gameDirectory;
    private String javaExecutable;
    private int minMemoryMb = DEFAULT_MIN_MEMORY_MB;
    private int maxMemoryMb = DEFAULT_MAX_MEMORY_MB;
    private String selectedVersion = "1.21.11";
    private int windowWidth;
    private int windowHeight;
    private String clientId = MicrosoftClientId.DEFAULT_ID;
    private boolean installMods = true;

    public LauncherConfig(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.javaExecutable = "";
    }

    public static LauncherConfig defaults() {
        return new LauncherConfig(defaultGameDirectory());
    }

    public static Path defaultGameDirectory() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".aetherglass");
    }

    /** Загружает настройки; при любой ошибке возвращает файл с значениями по умолчанию. */
    public static LauncherConfig load(Path file) {
        LauncherConfig config = new LauncherConfig(file.getParent() == null ? defaultGameDirectory() : file.getParent());
        if (!Files.isRegularFile(file)) {
            return config;
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, Object> data = Json.asObject(Json.parse(text));
            config.gameDirectory = Path.of(Json.string(data, "gameDirectory", config.gameDirectory.toString()))
                    .toAbsolutePath().normalize();
            config.javaExecutable = Json.string(data, "javaExecutable", "");
            config.minMemoryMb = clampMemory(Json.intValue(data, "minMemoryMb", DEFAULT_MIN_MEMORY_MB));
            config.maxMemoryMb = clampMemory(Json.intValue(data, "maxMemoryMb", DEFAULT_MAX_MEMORY_MB));
            if (config.maxMemoryMb < config.minMemoryMb) {
                int swap = config.maxMemoryMb;
                config.maxMemoryMb = config.minMemoryMb;
                config.minMemoryMb = swap;
            }
            config.selectedVersion = Json.string(data, "selectedVersion", "1.21.11");
            config.windowWidth = Math.max(0, Json.intValue(data, "windowWidth", 0));
            config.windowHeight = Math.max(0, Json.intValue(data, "windowHeight", 0));
            config.clientId = Json.string(data, "clientId", MicrosoftClientId.DEFAULT_ID);
            config.installMods = Json.boolValue(data, "installMods", true);
        } catch (RuntimeException | IOException exception) {
            // Повреждённый файл настроек — не причина отказываться от запуска.
            System.err.println("Не удалось прочитать настройки лаунчера: " + exception.getMessage());
        }
        return config;
    }

    public void save(Path file) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("gameDirectory", this.gameDirectory.toString());
        data.put("javaExecutable", this.javaExecutable);
        data.put("minMemoryMb", this.minMemoryMb);
        data.put("maxMemoryMb", this.maxMemoryMb);
        data.put("selectedVersion", this.selectedVersion);
        data.put("windowWidth", this.windowWidth);
        data.put("windowHeight", this.windowHeight);
        data.put("clientId", this.clientId);
        data.put("installMods", this.installMods);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, Json.writePretty(data), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Не удалось сохранить настройки лаунчера: " + exception.getMessage());
        }
    }

    private static int clampMemory(int value) {
        return Math.max(MIN_MEMORY_MB, Math.min(131072, value));
    }

    public Path gameDirectory() {
        return this.gameDirectory;
    }

    public LauncherConfig gameDirectory(Path path) {
        this.gameDirectory = path.toAbsolutePath().normalize();
        return this;
    }

    public String javaExecutable() {
        return this.javaExecutable;
    }

    public LauncherConfig javaExecutable(String value) {
        this.javaExecutable = value == null ? "" : value.trim();
        return this;
    }

    public int minMemoryMb() {
        return this.minMemoryMb;
    }

    public LauncherConfig minMemoryMb(int value) {
        int clamped = clampMemory(value);
        this.minMemoryMb = Math.min(clamped, this.maxMemoryMb);
        return this;
    }

    public int maxMemoryMb() {
        return this.maxMemoryMb;
    }

    public LauncherConfig maxMemoryMb(int value) {
        this.maxMemoryMb = Math.max(clampMemory(value), this.minMemoryMb);
        return this;
    }

    public String selectedVersion() {
        return this.selectedVersion;
    }

    public LauncherConfig selectedVersion(String value) {
        if (value != null && !value.isBlank()) {
            this.selectedVersion = value.trim();
        }
        return this;
    }

    public int windowWidth() {
        return this.windowWidth;
    }

    public LauncherConfig windowWidth(int value) {
        this.windowWidth = Math.max(0, value);
        return this;
    }

    public int windowHeight() {
        return this.windowHeight;
    }

    public LauncherConfig windowHeight(int value) {
        this.windowHeight = Math.max(0, value);
        return this;
    }

    public String clientId() {
        return this.clientId == null || this.clientId.isBlank() ? MicrosoftClientId.DEFAULT_ID : this.clientId;
    }

    public LauncherConfig clientId(String value) {
        this.clientId = value == null || value.isBlank() ? MicrosoftClientId.DEFAULT_ID : value.trim();
        return this;
    }

    public boolean installMods() {
        return this.installMods;
    }

    public LauncherConfig installMods(boolean value) {
        this.installMods = value;
        return this;
    }
}
