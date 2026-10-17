package com.aetherglass.client.render.hud;

/** Элемент экрана: его можно включить, выключить и перетащить в редакторе. */
public enum HudElementType {
    WATERMARK("Логотип", 0.008F, 0.008F, Alignment.LEFT, true),
    FPS("Кадры в секунду", 0.008F, 0.060F, Alignment.LEFT, true),
    PERFORMANCE("Производительность", 0.008F, 0.112F, Alignment.LEFT, true),
    COORDINATES("Координаты", 0.008F, 0.230F, Alignment.LEFT, false),
    DIRECTION("Направление", 0.500F, 0.020F, Alignment.CENTER, false),
    CLOCK("Время", 0.992F, 0.020F, Alignment.RIGHT, false),
    BIOME("Биом", 0.992F, 0.070F, Alignment.RIGHT, false),
    SESSION("Время в мире", 0.992F, 0.120F, Alignment.RIGHT, false),
    POTIONS("Эффекты", 0.992F, 0.330F, Alignment.RIGHT, true),
    TARGET("Цель", 0.500F, 0.430F, Alignment.CENTER, true),
    HELD_ITEM("Предмет в руке", 0.500F, 0.820F, Alignment.CENTER, false),
    EQUIPMENT("Снаряжение", 0.500F, 0.890F, Alignment.CENTER, true),
    ARROWS("Стрелы", 0.440F, 0.860F, Alignment.RIGHT, false),
    KEYSTROKES("Нажатия клавиш", 0.020F, 0.600F, Alignment.LEFT, false),
    CPS("Клики в секунду", 0.020F, 0.760F, Alignment.LEFT, false);

    /** К какой стороне «приклеен» элемент по горизонтали. */
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }

    private final String title;
    private final float defaultX;
    private final float defaultY;
    private final Alignment alignment;
    private final boolean defaultEnabled;

    HudElementType(String title, float defaultX, float defaultY, Alignment alignment, boolean defaultEnabled) {
        this.title = title;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.alignment = alignment;
        this.defaultEnabled = defaultEnabled;
    }

    public String title() {
        return this.title;
    }

    public float defaultX() {
        return this.defaultX;
    }

    public float defaultY() {
        return this.defaultY;
    }

    public Alignment alignment() {
        return this.alignment;
    }

    public boolean defaultEnabled() {
        return this.defaultEnabled;
    }

    /** Ключ настройки включённости. */
    public String settingsId() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
