package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Профиль пользовательской сборки (Modpack, Блок 5.1–5.3):
 * хранит название сборки, версию Minecraft, загрузчик модов
 * ({@code Vanilla}, {@code Fabric}, {@code Forge}, {@code NeoForge}, {@code Quilt}),
 * флаг установки {@code AetherGlass Client} (доступен только на {@code 1.21.11 Fabric})
 * и флаг автоматической докачки базовых модов оптимизации ({@code Sodium}, {@code Lithium} и др.).
 */
public record ModpackProfile(
        String id,
        String name,
        String minecraftVersion,
        String loader,
        boolean installAetherGlassClient,
        boolean autoOptimizeMods
) {
    public static final String TARGET_AETHERGLASS_MC = "1.21.11";
    public static final String LOADER_VANILLA = "Vanilla";
    public static final String LOADER_FABRIC = "Fabric";
    public static final String LOADER_FORGE = "Forge";
    public static final String LOADER_NEOFORGE = "NeoForge";
    public static final String LOADER_QUILT = "Quilt";

    public static final List<String> SUPPORTED_LOADERS = List.of(
            LOADER_FABRIC, LOADER_VANILLA, LOADER_FORGE, LOADER_NEOFORGE, LOADER_QUILT
    );

    public ModpackProfile {
        id = (id == null || id.isBlank()) ? "pack-" + System.currentTimeMillis() : id.trim();
        name = (name == null || name.isBlank()) ? "Сборка " + minecraftVersion : name.trim();
        minecraftVersion = (minecraftVersion == null || minecraftVersion.isBlank())
                ? TARGET_AETHERGLASS_MC : minecraftVersion.trim();
        loader = normalizeLoader(loader);
        // Блок 5.2: AetherGlass Client доступен исключительно для 1.21.11 (Fabric)
        if (!isAetherGlassSupported(minecraftVersion, loader)) {
            installAetherGlassClient = false;
        }
    }

    /** Проверяет, поддерживает ли выбранная комбинация версии и загрузчика установку AetherGlass Client. */
    public static boolean isAetherGlassSupported(String mcVersion, String loaderName) {
        if (mcVersion == null || loaderName == null) {
            return false;
        }
        return TARGET_AETHERGLASS_MC.equals(mcVersion.trim())
                && LOADER_FABRIC.equalsIgnoreCase(loaderName.trim());
    }

    public boolean supportsAetherGlassClient() {
        return isAetherGlassSupported(this.minecraftVersion, this.loader);
    }

    public static String normalizeLoader(String raw) {
        if (raw == null || raw.isBlank()) {
            return LOADER_FABRIC;
        }
        String clean = raw.trim().toLowerCase(Locale.ROOT);
        return switch (clean) {
            case "vanilla" -> LOADER_VANILLA;
            case "forge" -> LOADER_FORGE;
            case "neoforge", "neo-forge" -> LOADER_NEOFORGE;
            case "quilt" -> LOADER_QUILT;
            default -> LOADER_FABRIC;
        };
    }

    public static List<ModpackProfile> defaults() {
        return List.of(
                new ModpackProfile("aetherglass-1.21.11", "AetherGlass 1.21.11", "1.21.11", LOADER_FABRIC, true, true),
                new ModpackProfile("fabric-opt-1.20.1", "Sodium Boost 1.20.1", "1.20.1", LOADER_FABRIC, false, true),
                new ModpackProfile("pvp-classic-1.19.4", "Classic PvP 1.19.4", "1.19.4", LOADER_VANILLA, false, false),
                new ModpackProfile("forge-legacy-1.16.5", "Forge Mods 1.16.5", "1.16.5", LOADER_FORGE, false, false)
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", this.id);
        map.put("name", this.name);
        map.put("minecraftVersion", this.minecraftVersion);
        map.put("loader", this.loader);
        map.put("installAetherGlassClient", this.installAetherGlassClient);
        map.put("autoOptimizeMods", this.autoOptimizeMods);
        return map;
    }

    public static ModpackProfile fromMap(Map<String, Object> map) {
        String version = Json.string(map, "minecraftVersion", TARGET_AETHERGLASS_MC);
        String loader = normalizeLoader(Json.string(map, "loader", LOADER_FABRIC));
        boolean defaultAether = isAetherGlassSupported(version, loader);
        return new ModpackProfile(
                Json.string(map, "id", "pack-" + version),
                Json.string(map, "name", "Сборка " + version),
                version,
                loader,
                Json.boolValue(map, "installAetherGlassClient", defaultAether),
                Json.boolValue(map, "autoOptimizeMods", LOADER_FABRIC.equalsIgnoreCase(loader))
        );
    }

    @Override
    public String toString() {
        return this.name + " [" + this.minecraftVersion + " · " + this.loader + "]";
    }
}
