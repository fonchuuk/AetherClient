package com.aetherglass.client.render.crosshair;

/** Форма прицела. */
public enum CrosshairStyle {
    CROSS("Крест"),
    DOT("Точка"),
    CIRCLE("Круг"),
    CIRCLE_DOT("Круг с точкой"),
    BRACKET("Скобки"),
    ARROW("Стрелки");

    private final String title;

    CrosshairStyle(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
