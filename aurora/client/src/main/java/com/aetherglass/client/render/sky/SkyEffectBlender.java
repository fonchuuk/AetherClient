package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;

/**
 * Цвета неба (5.4).
 *
 * <p>Переход между двумя цветами считается от реального времени, поэтому небо «дышит»
 * плавно и одинаково на любой частоте кадров. Красится само небо в мире — через
 * состояние отрисовки неба, а не прямоугольник поверх экрана: значит, эффект работает
 * и с любым углом обзора, и в любом измерении, где небо рисуется.</p>
 */
public final class SkyEffectBlender {
    private static int first = SkyPreset.AURORA.first();
    private static int second = SkyPreset.AURORA.second();
    private static float speed = 0.05F;
    private static float intensity = 0.6F;

    private SkyEffectBlender() {
    }

    public static void configure(int firstColor, int secondColor, float cyclesPerSecond, float blend) {
        first = firstColor;
        second = secondColor;
        speed = Math.max(0.005F, Math.min(1.0F, cyclesPerSecond));
        intensity = Math.max(0.0F, Math.min(1.0F, blend));
    }

    /** Текущая точка перехода: 0 — первый цвет, 1 — второй. */
    public static float phase() {
        return (float) (0.5D + 0.5D * Math.sin(FrameClock.elapsedSeconds() * speed * Math.PI * 2.0D));
    }

    /** Цвет неба с учётом эффекта; на входе — ванильный цвет этого кадра. */
    public static int blendSky(int vanillaColor) {
        return blend(vanillaColor, ColorController.resolve(mix()));
    }

    /** Цвет рассвета/заката: второй цвет эффекта заметнее всего именно здесь. */
    public static int blendSunrise(int vanillaColor) {
        return blend(vanillaColor, ColorController.resolve(ColorUtil.lerp(second, first, 0.35F)));
    }

    private static int mix() {
        return ColorUtil.lerp(first, second, phase());
    }

    private static int blend(int vanillaColor, int effectColor) {
        if (intensity <= 0.001F) {
            return vanillaColor;
        }
        // Прозрачность ванильного цвета сохраняем: её использует игра.
        return ColorUtil.withAlpha(ColorUtil.lerp(vanillaColor, effectColor, intensity),
                ColorUtil.alpha(vanillaColor));
    }

    /** Для экрана «О клиенте»: какой сейчас цвет неба. */
    public static String debugState() {
        return ColorUtil.toHex(blendSky(0xFF000000));
    }
}
