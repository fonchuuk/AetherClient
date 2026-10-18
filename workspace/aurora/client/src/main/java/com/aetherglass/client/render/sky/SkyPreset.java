package com.aetherglass.client.render.sky;

/** Готовый набор неба: два цвета, между которыми идёт плавный переход. */
public enum SkyPreset {
    AURORA("Сияние", 0xFF2A3F7A, 0xFF6BE3D0),
    SUNSET("Закат", 0xFF3A2A55, 0xFFFF8A5B),
    OCEAN("Океан", 0xFF102A45, 0xFF4FC3F7),
    VOID("Пустота", 0xFF08060F, 0xFF2B1E4A),
    CUSTOM("Своё", 0xFF1B2A4A, 0xFF7FD4FF);

    private final String title;
    private final int first;
    private final int second;

    SkyPreset(String title, int first, int second) {
        this.title = title;
        this.first = first;
        this.second = second;
    }

    public String title() {
        return this.title;
    }

    public int first() {
        return this.first;
    }

    public int second() {
        return this.second;
    }
}
