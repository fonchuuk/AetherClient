package com.aetherglass.client.render.hand;

/** Стиль подсветки руки. */
public enum HandStyle {
    GLOW("Свечение"),
    OUTLINE("Контур"),
    GRADIENT("Градиент"),
    NONE("Нет");

    private final String title;

    HandStyle(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
