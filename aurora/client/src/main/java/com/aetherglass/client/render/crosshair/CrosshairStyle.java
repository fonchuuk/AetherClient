package com.aetherglass.client.render.crosshair;

/** Форма прицела (Блок 4.3: от микро-точки 1–2px до тонкого крестика, кольца и уголков). */
public enum CrosshairStyle {
    DOT("Точка (1–2px)"),
    CROSS("Крестик"),
    CIRCLE("Кольцо"),
    CIRCLE_DOT("Кольцо с точкой"),
    CORNERS("Уголки");

    private final String title;

    CrosshairStyle(String title) {
        this.title = title;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
