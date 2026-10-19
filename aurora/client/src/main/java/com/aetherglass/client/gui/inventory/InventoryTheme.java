package com.aetherglass.client.gui.inventory;

/**
 * Темы оформления инвентаря и контейнеров (Блок 6.3).
 */
public enum InventoryTheme {
    LIGHT("Светлый (дефолтный)"),
    DARK("Тёмный (более чёрный)"),
    LIQUID_GLASS("Матовый (Liquid Glass)");

    private final String title;

    InventoryTheme(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
