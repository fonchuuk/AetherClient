package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.shader.ShaderEffect;

/**
 * Пресеты переливающегося шейдерного неба ({@code SkyShader}, Блок 4.4).
 */
public enum SkyPreset {
    AURORA("Северное сияние", 0xFF0E2A47, 0xFF3BF0C5, 0xFF7B5CFF, ShaderEffect.AURORA),
    NEBULA("Туманность Ориона", 0xFF140D36, 0xFFD946EF, 0xFF38BDF8, ShaderEffect.COSMIC_DUST),
    SUNSET("Закатный прибой", 0xFF281236, 0xFFFF6B6B, 0xFFFFB347, ShaderEffect.FIRE_CORE),
    CYBER_PRISM("Кибер-призма", 0xFF0A192F, 0xFF00F5D4, 0xFFFF2A85, ShaderEffect.SPECTRAL_OUTLINE),
    NIGHT("Хрустальная ночь", 0xFF091326, 0xFF4CC9F0, 0xFF7209B7, ShaderEffect.ICE_CRYSTAL),
    CUSTOM("Свой перелив", 0xFF102542, 0xFF4AD8FF, 0xFFA855F7, ShaderEffect.LIQUID_GLASS);

    private final String title;
    private final int first;
    private final int second;
    private final int third;
    private final ShaderEffect defaultShader;

    SkyPreset(String title, int first, int second, int third, ShaderEffect defaultShader) {
        this.title = title;
        this.first = first;
        this.second = second;
        this.third = third;
        this.defaultShader = defaultShader;
    }

    public int first() {
        return this.first;
    }

    public int second() {
        return this.second;
    }

    public int third() {
        return this.third;
    }

    public ShaderEffect defaultShader() {
        return this.defaultShader;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
