package com.aetherglass.client.gui.theme;

import com.aetherglass.client.render.util.ColorUtil;

/**
 * Оформление интерфейса: палитры и токены.
 *
 * <p>Палитра задаёт общий характер (тёмная, светлая, неоновая), а токены — «физику»
 * интерфейса: плотность стекла, радиус скругления и скорость движения. Меняя токены,
 * можно одним движением перестроить весь вид, не переписывая код отрисовки.</p>
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
    private static float accentHue = 0.52F;
    private static float density = 0.55F;
    private static float radius = 10.0F;
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
        accentHue = hue;
    }

    /** Плотность стекла: 0 — почти прозрачное, 1 — почти плотное. */
    public static float density() {
        return density;
    }

    public static void density(float value) {
        density = Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Базовый радиус скругления панелей и кнопок. */
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

    /**
     * Пружинистость интерфейса: 0 — движения без перелёта, 1 — заметный
     * упругий перелёт. Влияет на виджеты через {@code Spring}.
     */
    public static float bounce() {
        return bounce;
    }

    public static void bounce(float value) {
        bounce = Math.max(0.0F, Math.min(1.0F, value));
    }

    public static void motion(float value) {
        motion = Math.max(0.25F, Math.min(2.5F, value));
    }

    // ─────────────────────────────── цвета ───────────────────────────────

    /** Фон окна поверх мира: чем выше плотность, тем он плотнее. */
    public static int backdrop() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFF4F7FB, Math.round(60 + 120 * density));
            case NEON -> ColorUtil.withAlpha(0xFF05030F, Math.round(90 + 140 * density));
            default -> ColorUtil.withAlpha(0xFF080B14, Math.round(80 + 150 * density));
        };
    }

    /** Тонировка стеклянной панели. */
    public static int glassTint() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFFFFFFF, Math.round(70 + 130 * density));
            case NEON -> ColorUtil.withAlpha(0xFF171033, Math.round(60 + 110 * density));
            default -> ColorUtil.withAlpha(0xFF151B2B, Math.round(60 + 120 * density));
        };
    }

    /** Блик по верхней кромке стекла. */
    public static int glassHighlight() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFFFFFFFF, Math.round(150 + 80 * density));
            case NEON -> ColorUtil.withAlpha(0xFFB69BFF, Math.round(80 + 60 * density));
            default -> ColorUtil.withAlpha(0xFFDCE6F7, Math.round(70 + 70 * density));
        };
    }

    /** Тень по нижней кромке. */
    public static int glassShadow() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFF9AA7BD, Math.round(50 + 60 * density));
            default -> ColorUtil.withAlpha(0xFF000000, Math.round(50 + 70 * density));
        };
    }

    /** Кромка панели. */
    public static int panelBorder() {
        return switch (palette) {
            case LIGHT -> ColorUtil.withAlpha(0xFF8C9AB2, 120);
            case NEON -> ColorUtil.withAlpha(0xFF8E7BFF, 140);
            default -> ColorUtil.withAlpha(0xFFFFFFFF, 40);
        };
    }

    public static int accent() {
        return switch (palette) {
            case LIGHT -> ColorUtil.hsb(accentHue, 0.75F, 0.55F);
            case NEON -> ColorUtil.hsb(accentHue, 1.0F, 1.0F);
            default -> ColorUtil.hsb(accentHue, 0.65F, 0.95F);
        };
    }

    public static int textPrimary() {
        return switch (palette) {
            case LIGHT -> 0xFF101828;
            case NEON -> 0xFFF2F0FF;
            default -> 0xFFE9EEF8;
        };
    }

    public static int textSecondary() {
        return switch (palette) {
            case LIGHT -> 0xFF47546A;
            case NEON -> 0xFFBDB4E8;
            default -> 0xFFAEB9CE;
        };
    }

    public static int textMuted() {
        return switch (palette) {
            case LIGHT -> 0xFF6B7789;
            case NEON -> 0xFF8E84BE;
            default -> 0xFF7C8799;
        };
    }

    public static int success() {
        return palette == Palette.DARK ? 0xFF5DE09E : 0xFF2FA96B;
    }

    public static int danger() {
        return 0xFFF56B7A;
    }
}
