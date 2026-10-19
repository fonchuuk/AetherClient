package com.aetherglass.client.render.util;

/** Кривые анимации: аргумент — прогресс 0..1. */
public final class Easing {
    private Easing() {
    }

    public static float linear(float t) {
        return clamp01(t);
    }

    public static float easeOutCubic(float t) {
        float value = 1.0F - clamp01(t);
        return 1.0F - value * value * value;
    }

    public static float easeInCubic(float t) {
        float value = clamp01(t);
        return value * value * value;
    }

    public static float easeInOutSine(float t) {
        float value = clamp01(t);
        return (float) (-(Math.cos(Math.PI * value) - 1.0D) * 0.5D);
    }

    public static float easeInOutQuad(float t) {
        float value = clamp01(t);
        return value < 0.5F ? 2.0F * value * value : 1.0F - (2.0F - 2.0F * value) * (1.0F - value);
    }

    public static float easeOutQuint(float t) {
        float value = 1.0F - clamp01(t);
        return 1.0F - value * value * value * value * value;
    }

    /** Небольшой «перелёт» в конце: подходит для отпускания кнопки. */
    public static float easeOutBack(float t) {
        float value = clamp01(t);
        float c = 1.70158F;
        float inverted = value - 1.0F;
        return 1.0F + (c + 1.0F) * inverted * inverted * inverted + c * inverted * inverted;
    }

    /** Пружинистое затухание. */
    public static float easeOutElastic(float t) {
        float value = clamp01(t);
        if (value == 0.0F || value == 1.0F) {
            return value;
        }
        float period = 0.3F;
        float position = value - 1.0F;
        return (float) (Math.pow(2.0D, -10.0D * value)
                * Math.sin((position - period / 4.0F) * (2.0F * Math.PI) / period)) + 1.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
