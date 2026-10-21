package com.aetherglass.client.gui.inventory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.Identifier;

/**
 * Темы оформления инвентаря и контейнеров (Блок 6.3).
 */
public enum InventoryTheme {
    LIGHT("Светлый (дефолтный)"),
    DARK("Тёмный (более чёрный)"),
    LIQUID_GLASS("Матовый (Liquid Glass)");

    private static final Map<Identifier, Identifier> GLASS_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Identifier> DARK_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private final String title;

    InventoryTheme(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }

    public boolean replacesContainerTexture() {
        return this != LIGHT;
    }

    public Identifier resolveContainerTexture(Identifier original) {
        if (original == null || !replacesContainerTexture()) {
            return original;
        }
        if (!"minecraft".equals(original.getNamespace())) {
            return original;
        }
        String path = original.getPath();
        if (!path.startsWith("textures/gui/container/")) {
            return original;
        }
        if (this == LIQUID_GLASS) {
            return GLASS_TEXTURE_CACHE.computeIfAbsent(original, id ->
                    Identifier.fromNamespaceAndPath("aetherglass",
                            id.getPath().replaceFirst("^textures/gui/container/", "textures/gui/container_glass/")));
        }
        return DARK_TEXTURE_CACHE.computeIfAbsent(original, id ->
                Identifier.fromNamespaceAndPath("aetherglass",
                        id.getPath().replaceFirst("^textures/gui/container/", "textures/gui/container_dark/")));
    }

    @Override
    public String toString() {
        return this.title;
    }
}
