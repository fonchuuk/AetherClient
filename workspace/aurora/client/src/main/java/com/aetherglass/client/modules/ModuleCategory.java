package com.aetherglass.client.modules;

/** Раздел модуля в списке. */
public enum ModuleCategory {
    VISUAL("Визуал"),
    INTERFACE("Интерфейс"),
    HUD("Экран");

    private final String title;

    ModuleCategory(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
