package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.resources.Identifier;

/**
 * Видео-пресеты и палитры купола неба ({@code SkyShader}, Блок 3.1–3.2).
 * Каждый пресет содержит основную текстуру видео-кадра и облегчённый размытый плейсхолдер.
 */
public enum SkyPreset {
    AURORA("Видео · Северное сияние", 0xFF0E2A47, 0xFF3BF0C5, 0xFF7B5CFF, ShaderEffect.AURORA,
            "textures/gui/menu_backdrop_aurora_b0.png", "textures/gui/menu_backdrop_aurora_b2.png"),
    NEBULA("Видео · Туманность Ориона", 0xFF140D36, 0xFFD946EF, 0xFF38BDF8, ShaderEffect.COSMIC_DUST,
            "textures/gui/menu_backdrop_night_b0.png", "textures/gui/menu_backdrop_night_b2.png"),
    SUNSET("Видео · Закатный неон", 0xFF281236, 0xFFFF6B6B, 0xFFFFB347, ShaderEffect.FIRE_CORE,
            "textures/gui/menu_backdrop_b0.png", "textures/gui/menu_backdrop_b2.png"),
    CYBER_PRISM("Видео · Кибер-призма", 0xFF0A192F, 0xFF00F5D4, 0xFFFF2A85, ShaderEffect.SPECTRAL_OUTLINE,
            "textures/gui/menu_backdrop_glass_b0.png", "textures/gui/menu_backdrop_glass_b2.png"),
    NIGHT("Видео · Хрустальная ночь", 0xFF091326, 0xFF4CC9F0, 0xFF7209B7, ShaderEffect.ICE_CRYSTAL,
            "textures/gui/menu_backdrop_night_b0.png", "textures/gui/menu_backdrop_night_b2.png"),
    CUSTOM("Видео · Свой перелив", 0xFF102542, 0xFF4AD8FF, 0xFFA855F7, ShaderEffect.LIQUID_GLASS,
            "textures/gui/menu_backdrop_aurora_b0.png", "textures/gui/menu_backdrop_aurora_b2.png");

    private final String title;
    private final int first;
    private final int second;
    private final int third;
    private final ShaderEffect defaultShader;
    private final Identifier videoFrame;
    private final Identifier blurredPlaceholder;
    private final Identifier skySheet;

    SkyPreset(String title, int first, int second, int third, ShaderEffect defaultShader,
              String framePath, String placeholderPath) {
        this.title = title;
        this.first = first;
        this.second = second;
        this.third = third;
        this.defaultShader = defaultShader;
        this.videoFrame = Identifier.fromNamespaceAndPath("aetherglass", framePath);
        this.blurredPlaceholder = Identifier.fromNamespaceAndPath("aetherglass", placeholderPath);
        this.skySheet = Identifier.fromNamespaceAndPath("aetherglass",
                "textures/sky/sky_video_" + sheetName(name()) + ".png");
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

    public Identifier videoFrame() {
        return this.videoFrame;
    }

    public Identifier blurredPlaceholder() {
        return this.blurredPlaceholder;
    }

    /**
     * Лист видео-кадров для купола неба: 12 равноугольных панорам, уложенных
     * вертикально. Купол выбирает нужную строку по времени, поэтому «видео»
     * крутится без единой операции шейдера и без переключения текстур.
     */
    public Identifier skySheet() {
        return this.skySheet;
    }

    private static String sheetName(String enumName) {
        return switch (enumName) {
            case "NEBULA" -> "nebula";
            case "SUNSET" -> "sunset";
            case "CYBER_PRISM" -> "prism";
            case "NIGHT" -> "night";
            default -> "aurora";
        };
    }

    @Override
    public String toString() {
        return this.title;
    }
}
