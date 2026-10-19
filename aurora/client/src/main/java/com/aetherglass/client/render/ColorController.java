package com.aetherglass.client.render;

import com.aetherglass.client.render.util.ColorUtil;

/**
 * Общий источник цвета для всех эффектов.
 *
 * <p>Радуга (модуль 5.5) не имеет собственного механизма окраски: она крутит оттенок
 * здесь, а частицы, подсветка блоков, руки и небо читают цвет отсюда. Поэтому включение
 * радуги мгновенно перекрашивает все эффекты сразу, а выключение возвращает им их
 * собственные цвета. Дублирующих «своих» радуг у модулей нет — это принципиально.</p>
 */
public final class ColorController {
    private static boolean cycling;
    private static float speed = 0.12F;
    private static float intensity = 0.85F;
    private static float saturation = 0.85F;
    private static float brightness = 1.0F;
    private static float hue;

    private ColorController() {
    }

    /** Продвигает перелив; вызывается один раз за кадр. */
    public static void update(float deltaSeconds) {
        if (!cycling) {
            return;
        }
        hue = (hue + deltaSeconds * speed) % 1.0F;
        if (hue < 0.0F) {
            hue += 1.0F;
        }
    }

    public static void setCycling(boolean value) {
        cycling = value;
    }

    public static boolean isCycling() {
        return cycling;
    }

    /** Скорость перелива: полных оборотов в секунду. */
    public static void setSpeed(float cyclesPerSecond) {
        speed = Math.max(0.005F, Math.min(2.0F, cyclesPerSecond));
    }

    /** Насколько сильно перелив перебивает собственный цвет эффекта: 0..1. */
    public static void setIntensity(float value) {
        intensity = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void setSaturation(float value) {
        saturation = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void setBrightness(float value) {
        brightness = Math.max(0.2F, Math.min(1.0F, value));
    }

    public static float hue() {
        return hue;
    }

    /** Текущий цвет перелива с полной непрозрачностью. */
    public static int current() {
        return ColorUtil.hsb(hue, saturation, brightness);
    }

    /**
     * Цвет эффекта с учётом радуги.
     *
     * @param ownColor собственный цвет из настроек эффекта
     * @return сам цвет, если радуга выключена, или смесь с переливом
     */
    public static int resolve(int ownColor) {
        if (!cycling) {
            return ownColor;
        }
        return ColorUtil.lerp(ownColor, ColorUtil.hsb(hue, saturation, brightness), intensity);
    }

    /** Тот же цвет, но с наложенной прозрачностью эффекта. */
    public static int resolve(int ownColor, float alpha) {
        return ColorUtil.fade(resolve(ownColor), alpha);
    }

    /** Цвет с индивидуальным фазовым сдвигом при включённом переливе. */
    public static int resolve(int ownColor, boolean rainbowOverride, float hueOffset) {
        if (!cycling && !rainbowOverride) {
            return ownColor;
        }
        float h = (hue + hueOffset) % 1.0F;
        if (h < 0.0F) {
            h += 1.0F;
        }
        return ColorUtil.lerp(ownColor, ColorUtil.hsb(h, saturation, brightness), Math.max(0.65F, intensity));
    }

    /** Акцентный цвет темы с учётом глобального радужного перелива. */
    public static int accent() {
        return resolve(com.aetherglass.client.gui.theme.AuroraTheme.accent());
    }
}
