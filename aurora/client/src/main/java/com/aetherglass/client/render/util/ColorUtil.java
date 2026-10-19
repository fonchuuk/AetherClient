package com.aetherglass.client.render.util;

/** Работа с цветом в формате ARGB: без выделений памяти и без зависимости от AWT. */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static int alpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    public static int red(int color) {
        return (color >> 16) & 0xFF;
    }

    public static int green(int color) {
        return (color >> 8) & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int argb(int alpha, int red, int green, int blue) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    public static int withAlpha(int color, int alpha) {
        return (clamp(alpha) << 24) | (color & 0x00FFFFFF);
    }

    /** Умножает прозрачность цвета на множитель 0..1. */
    public static int fade(int color, float factor) {
        return withAlpha(color, Math.round(alpha(color) * Math.max(0.0F, Math.min(1.0F, factor))));
    }

    public static int lerp(int from, int to, float t) {
        float amount = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round(alpha(from) + (alpha(to) - alpha(from)) * amount);
        int r = Math.round(red(from) + (red(to) - red(from)) * amount);
        int g = Math.round(green(from) + (green(to) - green(from)) * amount);
        int b = Math.round(blue(from) + (blue(to) - blue(from)) * amount);
        return argb(a, r, g, b);
    }

    /**
     * Цвет HSV → ARGB. Параметры в пределах 0..1, прозрачность 0..1.
     *
     * <p>Перевод сделан вручную, без {@code java.awt.Color}: клиентский код не должен
     * тянуть за собой AWT только ради арифметики цвета.</p>
     */
    public static int hsb(float hue, float saturation, float brightness, float opacity) {
        float sector = (((hue % 1.0F) + 1.0F) % 1.0F) * 6.0F;
        float s = clamp01(saturation);
        float v = clamp01(brightness);
        int index = (int) sector % 6;
        float f = sector - (int) sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * f);
        float t = v * (1.0F - s * (1.0F - f));
        float r;
        float g;
        float b;
        switch (index) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return argb(Math.round(255.0F * clamp01(opacity)),
                Math.round(255.0F * r), Math.round(255.0F * g), Math.round(255.0F * b));
    }

    /** Цвет HSV → ARGB с полной непрозрачностью. */
    public static int hsb(float hue, float saturation, float brightness) {
        return hsb(hue, saturation, brightness, 1.0F);
    }

    /**
     * ARGB → HSV: тон (0..1). Обратная операция к {@link #hsb(float, float, float)}.
     *
     * <p>Отдельные методы вместо массива — чтобы не выделять память при каждом
     * обращении (например, при разборе цвета в цикле отрисовки).</p>
     */
    public static float hueOf(int color) {
        float r = red(color) / 255.0F;
        float g = green(color) / 255.0F;
        float b = blue(color) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float delta = max - Math.min(r, Math.min(g, b));
        if (delta <= 1.0E-5F) {
            return 0.0F;
        }
        float hue;
        if (max == r) {
            hue = (g - b) / delta;
        } else if (max == g) {
            hue = (b - r) / delta + 2.0F;
        } else {
            hue = (r - g) / delta + 4.0F;
        }
        hue /= 6.0F;
        return ((hue % 1.0F) + 1.0F) % 1.0F;
    }

    /** ARGB → HSV: насыщенность (0..1). */
    public static float saturationOf(int color) {
        float r = red(color) / 255.0F;
        float g = green(color) / 255.0F;
        float b = blue(color) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        if (max <= 0.0F) {
            return 0.0F;
        }
        return (max - Math.min(r, Math.min(g, b))) / max;
    }

    /** ARGB → HSV: яркость (0..1). */
    public static float brightnessOf(int color) {
        return Math.max(red(color), Math.max(green(color), blue(color))) / 255.0F;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Увеличивает яркость цвета на величину 0..1 (блики «жидкого стекла»). */
    public static int brighten(int color, float amount) {
        float factor = 1.0F + Math.max(-1.0F, Math.min(1.0F, amount));
        return argb(alpha(color),
                Math.round(Math.min(255, red(color) * factor)),
                Math.round(Math.min(255, green(color) * factor)),
                Math.round(Math.min(255, blue(color) * factor)));
    }

    /** Затемняет цвет: нижняя кромка стекла. */
    public static int darken(int color, float amount) {
        return brighten(color, -Math.abs(amount));
    }

    public static String toHex(int color) {
        return String.format("#%08X", color);
    }

    /** Разбирает шестнадцатеричный цвет; при ошибке возвращает запасной вариант. */
    public static int parseHex(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(text.trim().replace("#", "").replace("0x", ""), 16);
            return (int) (parsed & 0xFFFFFFFFL);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
