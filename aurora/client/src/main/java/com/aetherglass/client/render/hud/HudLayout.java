package com.aetherglass.client.render.hud;

import com.aetherglass.client.config.ModConfig;

/**
 * Расположение элементов экрана.
 *
 * <p>Координаты хранятся в долях экрана (0..1), поэтому при смене разрешения или
 * масштаба интерфейса элементы остаются на своих местах. Значения пишутся в общий
 * файл настроек, но в собственном разделе — отдельно от настроек модулей.</p>
 */
public final class HudLayout {
    private static final HudLayout INSTANCE = new HudLayout();

    private final float[] positions = new float[HudElementType.values().length * 2];

    private HudLayout() {
        resetAll();
    }

    public static HudLayout get() {
        return INSTANCE;
    }

    public float x(HudElementType type) {
        return this.positions[type.ordinal() * 2];
    }

    public float y(HudElementType type) {
        return this.positions[type.ordinal() * 2 + 1];
    }

    /** Перемещает элемент; координаты ограничиваются диапазоном 0..1. */
    public void move(HudElementType type, float x, float y) {
        this.positions[type.ordinal() * 2] = Math.max(0.0F, Math.min(1.0F, x));
        this.positions[type.ordinal() * 2 + 1] = Math.max(0.0F, Math.min(1.0F, y));
    }

    public void reset(HudElementType type) {
        move(type, type.defaultX(), type.defaultY());
    }

    public void resetAll() {
        for (HudElementType type : HudElementType.values()) {
            reset(type);
        }
    }

    /** Читает расположение из настроек. */
    public void load(ModConfig config) {
        for (HudElementType type : HudElementType.values()) {
            float x = config.getFloat(key(type) + ".x", type.defaultX(), 0.0F, 1.0F);
            float y = config.getFloat(key(type) + ".y", type.defaultY(), 0.0F, 1.0F);
            move(type, x, y);
        }
    }

    /** Записывает расположение в настройки. */
    public void save(ModConfig config) {
        for (HudElementType type : HudElementType.values()) {
            config.set(key(type) + ".x", Float.toString(x(type)));
            config.set(key(type) + ".y", Float.toString(y(type)));
        }
    }

    private static String key(HudElementType type) {
        return "hud.pos." + type.settingsId();
    }
}
