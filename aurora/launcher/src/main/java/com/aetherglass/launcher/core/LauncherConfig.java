package com.aetherglass.launcher.core;

import com.aetherglass.launcher.auth.MicrosoftClientId;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки лаунчера и менеджер пользовательских сборок (Modpacks, Блок 5.1–5.3).
 * Хранятся в читаемом JSON-файле в каталоге игры.
 */
public final class LauncherConfig {
    public static final int MIN_MEMORY_MB = 512;
    public static final int DEFAULT_MIN_MEMORY_MB = 1024;
    public static final int DEFAULT_MAX_MEMORY_MB = 4096;
    public static final int DEFAULT_LAUNCHER_WIDTH = 1280;
    public static final int DEFAULT_LAUNCHER_HEIGHT = 800;

    private Path gameDirectory;
    private String javaExecutable;
    private int minMemoryMb = DEFAULT_MIN_MEMORY_MB;
    private int maxMemoryMb = DEFAULT_MAX_MEMORY_MB;
    private String selectedVersion = "1.21.11";
    private String selectedLoader = ModpackProfile.LOADER_FABRIC;
    private boolean installAetherGlassClient = true;
    private int windowWidth;
    private int windowHeight;
    private int launcherWidth = DEFAULT_LAUNCHER_WIDTH;
    private int launcherHeight = DEFAULT_LAUNCHER_HEIGHT;
    private String clientId = MicrosoftClientId.DEFAULT_ID;
    private boolean installMods = true;
    private String selectedModpackId = "aetherglass-1.21.11";
    private final List<ModpackProfile> modpacks = new ArrayList<>();

    public LauncherConfig(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.javaExecutable = "";
        this.modpacks.addAll(ModpackProfile.defaults());
    }

    public static LauncherConfig defaults() {
        return new LauncherConfig(defaultGameDirectory());
    }

    public static Path defaultGameDirectory() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".aetherglass");
    }

    /** Загружает настройки; при любой ошибке возвращает файл со значениями по умолчанию. */
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
            config.selectedLoader = ModpackProfile.normalizeLoader(
                    Json.string(data, "selectedLoader", ModpackProfile.LOADER_FABRIC));
            boolean defaultAether = ModpackProfile.isAetherGlassSupported(config.selectedVersion, config.selectedLoader);
            config.installAetherGlassClient = defaultAether
                    && Json.boolValue(data, "installAetherGlassClient", true);
            config.windowWidth = Math.max(0, Json.intValue(data, "windowWidth", 0));
            config.windowHeight = Math.max(0, Json.intValue(data, "windowHeight", 0));
            config.launcherWidth = Math.max(1100, Json.intValue(data, "launcherWidth", DEFAULT_LAUNCHER_WIDTH));
            config.launcherHeight = Math.max(700, Json.intValue(data, "launcherHeight", DEFAULT_LAUNCHER_HEIGHT));
            config.clientId = Json.string(data, "clientId", MicrosoftClientId.DEFAULT_ID);
            config.installMods = Json.boolValue(data, "installMods", true);
            config.selectedModpackId = Json.string(data, "selectedModpackId", "aetherglass-1.21.11");

            if (data.get("modpacks") instanceof List<?> rawList && !rawList.isEmpty()) {
                List<ModpackProfile> parsed = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map<?, ?>) {
                        parsed.add(ModpackProfile.fromMap(Json.asObject(item)));
                    }
                }
                if (!parsed.isEmpty()) {
                    config.modpacks.clear();
                    config.modpacks.addAll(parsed);
                }
            }
        } catch (RuntimeException | IOException exception) {
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
        data.put("selectedLoader", this.selectedLoader);
        data.put("installAetherGlassClient", this.installAetherGlassClient());
        data.put("windowWidth", this.windowWidth);
        data.put("windowHeight", this.windowHeight);
        data.put("launcherWidth", this.launcherWidth);
        data.put("launcherHeight", this.launcherHeight);
        data.put("clientId", this.clientId);
        data.put("installMods", this.installMods);
        data.put("selectedModpackId", this.selectedModpackId);

        List<Map<String, Object>> serializedPacks = new ArrayList<>(this.modpacks.size());
        for (ModpackProfile pack : this.modpacks) {
            serializedPacks.add(pack.toMap());
        }
        data.put("modpacks", serializedPacks);
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
            if (!supportsAetherGlassClient()) {
                this.installAetherGlassClient = false;
            }
        }
        return this;
    }

    public String selectedLoader() {
        return this.selectedLoader;
    }

    public LauncherConfig selectedLoader(String value) {
        this.selectedLoader = ModpackProfile.normalizeLoader(value);
        if (!supportsAetherGlassClient()) {
            this.installAetherGlassClient = false;
        }
        return this;
    }

    public boolean supportsAetherGlassClient() {
        return ModpackProfile.isAetherGlassSupported(this.selectedVersion, this.selectedLoader);
    }

    public boolean installAetherGlassClient() {
        return supportsAetherGlassClient() && this.installAetherGlassClient;
    }

    public LauncherConfig installAetherGlassClient(boolean value) {
        this.installAetherGlassClient = supportsAetherGlassClient() && value;
        return this;
    }

    public List<ModpackProfile> modpacks() {
        return List.copyOf(this.modpacks);
    }

    public String selectedModpackId() {
        return this.selectedModpackId;
    }

    public ModpackProfile activeModpack() {
        for (ModpackProfile pack : this.modpacks) {
            if (pack.id().equals(this.selectedModpackId)) {
                return pack;
            }
        }
        return this.modpacks.isEmpty() ? ModpackProfile.defaults().get(0) : this.modpacks.get(0);
    }

    public LauncherConfig selectModpack(String id) {
        for (ModpackProfile pack : this.modpacks) {
            if (pack.id().equals(id)) {
                this.selectedModpackId = pack.id();
                this.selectedVersion = pack.minecraftVersion();
                this.selectedLoader = pack.loader();
                this.installAetherGlassClient = pack.installAetherGlassClient();
                this.installMods = pack.autoOptimizeMods();
                return this;
            }
        }
        return this;
    }

    public ModpackProfile addOrUpdateModpack(String name, String mcVersion, String loader,
                                             boolean installAetherClient, boolean autoOptimize) {
        String cleanVer = (mcVersion == null || mcVersion.isBlank()) ? "1.21.11" : mcVersion.trim();
        String cleanLoader = ModpackProfile.normalizeLoader(loader);
        String cleanName = (name == null || name.isBlank())
                ? "Сборка " + cleanVer + " (" + cleanLoader + ")"
                : name.trim();
        String id = cleanName.toLowerCase().replaceAll("[^a-zа-я0-9._-]+", "-") + "-" + cleanVer;
        ModpackProfile created = new ModpackProfile(
                id, cleanName, cleanVer, cleanLoader, installAetherClient, autoOptimize
        );
        int existingIdx = -1;
        for (int i = 0; i < this.modpacks.size(); i++) {
            if (this.modpacks.get(i).id().equals(id)
                    || this.modpacks.get(i).name().equalsIgnoreCase(cleanName)) {
                existingIdx = i;
                break;
            }
        }
        if (existingIdx >= 0) {
            this.modpacks.set(existingIdx, created);
        } else {
            this.modpacks.add(created);
        }
        selectModpack(created.id());
        return created;
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

    public int launcherWidth() {
        return this.launcherWidth;
    }

    public LauncherConfig launcherWidth(int value) {
        this.launcherWidth = Math.max(1100, value);
        return this;
    }

    public int launcherHeight() {
        return this.launcherHeight;
    }

    public LauncherConfig launcherHeight(int value) {
        this.launcherHeight = Math.max(700, value);
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
