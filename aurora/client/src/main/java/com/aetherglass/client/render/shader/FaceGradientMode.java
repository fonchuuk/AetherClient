package com.aetherglass.client.render.shader;

/**
 * Режимы перелива и заполнения граней для {@code BlockShader} и {@code HandsShader}.
 */
public enum FaceGradientMode {
    FULL_SHADER("Полный шейдер граней"),
    MENISCUS_DROP("Капля / Линза (Liquid Glass)"),
    SOLAR_GLINT("Скользящий солнечный блик"),
    SOFT_GRADIENT("Плавный градиент по граням"),
    RADIAL_PULSE("Волновой перелив от центра"),
    OUTLINE_ONLY("Только светящийся контур");

    /** Алиас для совместимости с прежними вызовами. */
    public static final FaceGradientMode FULL_SURFACE = FULL_SHADER;

    private final String label;

    FaceGradientMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
