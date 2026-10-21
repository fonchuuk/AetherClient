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
        String description,
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
            LOADER_VANILLA, LOADER_FABRIC, LOADER_FORGE, LOADER_NEOFORGE
    );

    public ModpackProfile(
            String id,
            String name,
            String minecraftVersion,
            String loader,
            boolean installAetherGlassClient,
            boolean autoOptimizeMods
    ) {
        this(id, name, defaultDescription(name, minecraftVersion, installAetherGlassClient, autoOptimizeMods),
                minecraftVersion, loader, installAetherGlassClient, autoOptimizeMods);
    }

    public ModpackProfile {
        id = (id == null || id.isBlank()) ? "pack-" + System.currentTimeMillis() : id.trim();
        minecraftVersion = (minecraftVersion == null || minecraftVersion.isBlank())
                ? TARGET_AETHERGLASS_MC : minecraftVersion.trim();
        name = (name == null || name.isBlank()) ? minecraftVersion : name.trim();
        loader = normalizeLoader(loader);
        if (!isAetherGlassSupported(minecraftVersion, loader)) {
            installAetherGlassClient = false;
        }
        description = (description == null || description.isBlank())
                ? defaultDescription(name, minecraftVersion, installAetherGlassClient, autoOptimizeMods)
                : description.trim();
    }

    private static String defaultDescription(String name, String mcVer, boolean aether, boolean opt) {
        if (aether) {
            return "Сборка для визуала (10 модов на оптимизацию + AetherGlass)";
        }
        if (opt) {
            return "Версия " + mcVer + " и моды на оптимизацию";
        }
        return "Чистая сборка Minecraft " + mcVer;
    }

    /** Техническая строка через запятую, например: {@code 1.21.11, Fabric, AetherGlass}. */
    public String technicalInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.minecraftVersion).append(", ").append(this.loader);
        if (this.installAetherGlassClient) {
            sb.append(", AetherGlass");
        } else if (this.autoOptimizeMods) {
            sb.append(", Оптимизация");
        }
        return sb.toString();
    }

    /** Первые 1–2 буквы названия сборки для иконки-аватарки. */
    public String iconInitials() {
        String clean = this.name == null ? "" : this.name.trim();
        if (clean.isEmpty()) {
            return "AG";
        }
        if (Character.isDigit(clean.charAt(0))) {
            return clean.substring(0, Math.min(2, clean.length())).toUpperCase(Locale.ROOT);
        }
        String[] parts = clean.split("[\\s_-]+");
        if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
        }
        StringBuilder caps = new StringBuilder();
        for (int i = 0; i < clean.length() && caps.length() < 2; i++) {
            char c = clean.charAt(i);
            if (i == 0 || Character.isUpperCase(c)) {
                caps.append(Character.toUpperCase(c));
            }
        }
        if (caps.length() >= 2) {
            return caps.toString();
        }
        return clean.substring(0, Math.min(2, clean.length())).toUpperCase(Locale.ROOT);
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
                new ModpackProfile(
                        "aether-visuals",
                        "AetherVisuals",
                        "Сборка для визуала",
                        "1.21.11",
                        LOADER_FABRIC,
                        true,
                        true
                ),
                new ModpackProfile(
                        "mc-26.2",
                        "26.2",
                        "Версия 26.2 и моды на оптимизацию",
                        "26.2",
                        LOADER_FABRIC,
                        false,
                        true
                )
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", this.id);
        map.put("name", this.name);
        map.put("description", this.description);
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
        String name = Json.string(map, "name", version);
        boolean aether = Json.boolValue(map, "installAetherGlassClient", defaultAether);
        boolean opt = Json.boolValue(map, "autoOptimizeMods", LOADER_FABRIC.equalsIgnoreCase(loader));
        return new ModpackProfile(
                Json.string(map, "id", "pack-" + version),
                name,
                Json.string(map, "description", defaultDescription(name, version, aether, opt)),
                version,
                loader,
                aether,
                opt
        );
    }

    @Override
    public String toString() {
        return this.name + " [" + this.minecraftVersion + " · " + this.loader + "]";
    }
}
