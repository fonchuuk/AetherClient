package com.aetherglass.client.gui.theme;

import com.aetherglass.client.render.util.ColorUtil;

/**
 * Оформление интерфейса в стиле матового жидкого стекла (Lunar / Badlion / Feather Frosted Glass).
 *
 * <p>Тонировка панелей выдержана в диапазоне 25–30% дымчатой полупрозрачности,
 * чтобы размытый мир и фоновая сцена мягко просвечивали сквозь стеклянные линзы
 * без чёрных прямоугольников и резких рамок.</p>
 */
public final class AuroraTheme {
    /** Доступные палитры. */
    public enum Palette {
        DARK("Тёмная"),
        LIGHT("Светлая"),
        NEON("Неон");

        private final String title;

        Palette(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    private static Palette palette = Palette.DARK;
    private static float accentHue = 0.54F;
    private static float density = 0.55F;
    private static float blur = 0.68F;
    private static float radius = 11.0F;
    private static float motion = 1.0F;
    private static float bounce = 0.6F;

    private AuroraTheme() {
    }

    public static Palette palette() {
        return palette;
    }

    public static void palette(Palette value) {
        palette = value == null ? Palette.DARK : value;
    }

    public static float accentHue() {
        return accentHue;
    }

    public static void accentHue(float hue) {
        if (hue > 1.0F) {
            accentHue = ((hue % 360.0F) + 360.0F) % 360.0F / 360.0F;
        } else if (hue < 0.0F) {
            accentHue = 0.0F;
        } else {
            accentHue = hue;
        }
    }

    /** Сила размытия стеклянных подложек (0..1). */
    public static float blur() {
        return blur;
    }

    public static void blur(float value) {
        blur = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static int radiusPx() {
        return Math.round(radius);
    }

    public static int glassTintTop() {
        int base = glassTint();
        int a = Math.min(115, ColorUtil.alpha(base) + 10);
        return ColorUtil.withAlpha(ColorUtil.brighten(base, 0.14F), a);
    }

    /** Плотность стекла: 0 — почти прозрачное, 1 — более плотное матовое стекло. */
    public static float density() {
        return density;
    }

    public static void density(float value) {
        density = Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Базовый радиус каплевидного скругления панелей и капсул. */
    public static float radius() {
        return radius;
    }

    public static void radius(float value) {
        radius = Math.max(0.0F, Math.min(24.0F, value));
    }

    /** Множитель скорости анимаций: 0.5 — медленнее, 2 — быстрее. */
    public static float motion() {
        return motion;
    }

    public static float bounce() {
        return bounce;
    }

    public static void bounce(float value) {
        bounce = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void motion(float value) {
        motion = Math.max(0.25F, Math.min(2.5F, value));
    }

    // ─────────────────────────────── цвета матового жидкого стекла ───────────────────────────────

    /** Лёгкая дымчатая вуаль поверх размытого мира при открытом меню. */
    public static int backdrop() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFEEF4FC, Math.round(28 + 38 * density));
            case NEON -> ColorUtil.withAlpha(0xFF09061A, Math.round(32 + 46 * density));
            default -> ColorUtil.withAlpha(0xFF0A101C, Math.round(30 + 44 * density));
        };
    }

    /**
     * Дымчатая полупрозрачная подложка матового жидкого стекла (25–30% непрозрачности
     * при стандартных настройках, без глухого чёрного цвета).
     */
    public static int glassTint() {
        int baseAlpha = Math.round(40 + 42 * density + 16 * blur);
        int base = switch (palette) {
            case LIGHT -> 0xFFF4F8FF;
            case NEON -> 0xFF1E173C;
            default -> 0xFF1E293D;
        };
        int hueTint = ColorUtil.hsb(accentHue, palette == Palette.LIGHT ? 0.18F : 0.42F,
                palette == Palette.LIGHT ? 0.98F : 0.34F);
        return ColorUtil.withAlpha(ColorUtil.lerp(base, hueTint, 0.24F), Math.min(128, baseAlpha));
    }

    public static int screenDim() {
        return backdrop();
    }

    public static int panelSurface() {
        return glassTint();
    }

    public static int panelHover() {
        int base = glassTint();
        return ColorUtil.withAlpha(ColorUtil.brighten(base, 0.18F), Math.min(145, ColorUtil.alpha(base) + 14));
    }

    public static int accentSoft() {
        return ColorUtil.fade(accent(), 0.22F);
    }

    public static int accentSecondary() {
        float shifted = (accentHue + 0.16F) % 1.0F;
        return ColorUtil.hsb(shifted, 0.62F, 0.96F);
    }

    /** Молочно-кристальный блик по верхней кромке линзы. */
    public static int glassHighlight() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFFFFFFF, Math.round(90 + 50 * density));
            case NEON -> ColorUtil.withAlpha(0xFFDCCEFF, Math.round(52 + 38 * density));
            default -> ColorUtil.withAlpha(0xFFE6F3FF, Math.round(48 + 36 * density));
        };
    }

    /** Мягкая внешняя и внутренняя тень стеклянной капли. */
    public static int glassShadow() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFF7B8BA3, Math.round(24 + 28 * density));
            default -> ColorUtil.withAlpha(0xFF030710, Math.round(28 + 32 * density));
        };
    }

    /** Тончайшая световая фаска стеклянной линзы (без резких белых/синих рамок). */
    public static int panelBorder() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFFFFFFF, 68);
            case NEON -> ColorUtil.withAlpha(0xFFC2B0FF, 44);
            default -> ColorUtil.withAlpha(0xFFDDF0FF, 34);
        };
    }

    public static int accent() {
        return switch (palette) {
            case LIGHT -> ColorUtil.hsb(accentHue, 0.72F, 0.58F);
            case NEON -> ColorUtil.hsb(accentHue, 0.88F, 1.0F);
            default -> ColorUtil.hsb(accentHue, 0.62F, 0.96F);
        };
    }

    public static int textPrimary() {
        return switch (palette) {
            case LIGHT -> 0xFF101828;
            case NEON -> 0xFFF5F3FF;
            default -> 0xFFF0F5FC;
        };
    }

    public static int textSecondary() {
        return switch (palette) {
            case LIGHT -> 0xFF47546A;
            case NEON -> 0xFFC5BCEB;
            default -> 0xFFB8C5DA;
        };
    }

    public static int textMuted() {
        return switch (palette) {
            case LIGHT -> 0xFF6B7789;
            case NEON -> 0xFF948AC2;
            default -> 0xFF8694AA;
        };
    }

    public static int success() {
        return palette == Palette.DARK ? 0xFF5CE6A0 : 0xFF2FA96B;
    }

    public static int danger() {
        return 0xFFFF6E80;
    }
}
