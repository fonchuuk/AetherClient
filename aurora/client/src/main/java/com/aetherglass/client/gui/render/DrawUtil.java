package com.aetherglass.client.gui.render;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Примитивы рисования интерфейса.
 *
 * <p>Всё рисуется кодом: ни одной готовой картинки. Округлые фигуры строятся построчно —
 * это позволяет делать градиент и полупрозрачность с правильными скруглёнными углами
 * без единого шейдера.</p>
 */
public final class DrawUtil {
    private DrawUtil() {
    }

    /** Скруглённый прямоугольник со сплошной заливкой. */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        for (int row = 0; row < height; row++) {
            int inset = rowInset(row, height, r);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    /** Скруглённый прямоугольник с вертикальным градиентом. */
    public static void fillRoundedGradient(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        float step = height > 1 ? 1.0F / (height - 1.0F) : 1.0F;
        for (int row = 0; row < height; row++) {
            int inset = rowInset(row, height, r);
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1,
                    ColorUtil.lerp(topColor, bottomColor, row * step));
        }
    }

    /** Скруглённый прямоугольник с рамкой. */
    public static void fillRoundedBordered(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int fill, int border, int borderWidth) {
        fillRounded(graphics, x, y, width, height, radius, fill);
        int width2 = Math.max(1, borderWidth);
        // Рамка рисуется четырьмя узкими скруглёнными полосами — углы остаются аккуратными.
        fillRounded(graphics, x, y, width, width2, radius, border);
        fillRounded(graphics, x, y + height - width2, width, width2, radius, border);
        fillRounded(graphics, x, y, width2, height, radius, border);
        fillRounded(graphics, x + width - width2, y, width2, height, radius, border);
    }

    /**
     * «Жидкое стекло»: полупрозрачная панель с бликом сверху, тенью снизу и светлой кромкой.
     *
     * <p>Эффект достигается послойной отрисовкой, а не шейдером: полупрозрачная подложка
     * пропускает анимированный фон, блик имитирует преломление, кромка — толщину стекла.</p>
     */
    public static void fillGlass(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                 float pressAmount, float hoverAmount) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = limit(radius, width, height);

        // 1. Подложка: при нажатии стекло «прогибается» и становится плотнее.
        int tint = ColorUtil.brighten(AuroraTheme.glassTint(), 0.06F * hoverAmount - 0.10F * pressAmount);
        fillRounded(graphics, x, y, width, height, r, tint);

        // 2. Блик: яркая полоса сверху, плавно сходящая на нет к середине.
        int highlightHeight = Math.max(2, Math.round(height * 0.55F));
        int highlightTop = ColorUtil.fade(AuroraTheme.glassHighlight(), 0.85F + 0.15F * hoverAmount);
        fillRowsGradient(graphics, x, y, width, highlightHeight, r, Math.min(r, height - r), highlightTop,
                ColorUtil.withAlpha(highlightTop, 0), true);

        // 3. Тень снизу: придаёт объём.
        int shadeHeight = Math.max(2, Math.round(height * 0.35F));
        int shade = AuroraTheme.glassShadow();
        fillRowsGradient(graphics, x, y + height - shadeHeight, width, shadeHeight, r,
                Math.min(r, height - r), ColorUtil.withAlpha(shade, 0), shade, false);

        // 4. Кромка: сверху светлее, снизу темнее.
        int rimTop = ColorUtil.fade(AuroraTheme.glassHighlight(), 0.55F + 0.45F * hoverAmount);
        int rimSide = ColorUtil.fade(AuroraTheme.panelBorder(), 0.7F + 0.3F * hoverAmount);
        int rimBottom = ColorUtil.fade(AuroraTheme.glassShadow(), 1.0F);
        fillRowsGradient(graphics, x, y, width, Math.max(1, Math.round(height * 0.5F)), r,
                Math.min(r, height - r), rimTop, ColorUtil.withAlpha(rimTop, 0), true);
        fillRounded(graphics, x, y, Math.max(1, r), height, r, rimSide);
        fillRounded(graphics, x + width - Math.max(1, r), y, Math.max(1, r), height, r, rimSide);
        fillRounded(graphics, x, y + height - 1, width, 1, r, rimBottom);
    }

    /** Панель «жидкое стекло» без состояния наведения. */
    public static void fillGlass(GuiGraphics graphics, int x, int y, int width, int height, int radius) {
        fillGlass(graphics, x, y, width, height, radius, 0.0F, 0.0F);
    }

    /** Мягкое свечение вокруг элемента: три расходящихся слоя. */
    public static void drawGlow(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color,
                                float strength) {
        if (strength <= 0.0F) {
            return;
        }
        float amount = Math.min(1.0F, strength);
        for (int layer = 3; layer >= 1; layer--) {
            int spread = layer * 2;
            int alpha = Math.round((40 - layer * 9) * amount);
            if (alpha <= 0) {
                continue;
            }
            fillRounded(graphics, x - spread, y - spread, width + spread * 2, height + spread * 2,
                    Math.round(radius + spread), ColorUtil.withAlpha(color, alpha));
        }
    }

    /** Тонкая разделительная линия. */
    public static void separator(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    /** Полоса прогресса с закруглёнными краями. */
    public static void progressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress,
                                   int trackColor, int fillColor) {
        int radius = Math.max(1, height / 2);
        fillRounded(graphics, x, y, width, height, radius, trackColor);
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            fillRounded(graphics, x, y, filled, height, radius, fillColor);
        }
    }

    /** Индикатор загрузки/установки: та же полоса, но с эффектом «дыхания». */
    public static void progressPulse(GuiGraphics graphics, int x, int y, int width, int height, float phase,
                                     int color) {
        int segments = 12;
        int segmentWidth = Math.max(2, width / segments);
        for (int index = 0; index < segments; index++) {
            float local = (phase + index / (float) segments) % 1.0F;
            float alpha = 0.25F + 0.75F * Easing.easeInOutQuad(1.0F - Math.abs(local * 2.0F - 1.0F));
            int segmentX = x + index * (width / segments);
            fillRounded(graphics, segmentX, y, segmentWidth, height, height / 2,
                    ColorUtil.fade(color, alpha));
        }
    }

    /** Отступ строки для скруглённой фигуры: сколько пикселей срезано слева и справа. */
    public static int rowInset(int row, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int last = height - 1;
        double distance;
        if (row < radius) {
            distance = radius - row - 0.5D;
        } else if (row > last - radius) {
            distance = row - (last - radius) + 0.5D;
        } else {
            return 0;
        }
        double squared = radius * radius - distance * distance;
        if (squared <= 0.0D) {
            return radius;
        }
        return (int) Math.ceil(radius - Math.sqrt(squared));
    }

    /** Градиент только по части высоты: верхняя полоса блика или нижняя полоса тени. */
    private static void fillRowsGradient(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                         int corner, int from, int to, boolean top) {
        if (height <= 0 || width <= 0) {
            return;
        }
        float step = height > 1 ? 1.0F / (height - 1.0F) : 1.0F;
        for (int row = 0; row < height; row++) {
            int inset;
            if (top) {
                inset = row < corner ? rowInset(row, corner * 2, Math.min(radius, corner)) : 0;
            } else {
                int offset = corner - 1 - row;
                inset = row > height - corner ? rowInset(Math.max(0, offset), corner * 2, Math.min(radius, corner)) : 0;
            }
            int color = ColorUtil.lerp(from, to, row * step);
            if (ColorUtil.alpha(color) == 0) {
                continue;
            }
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int limit(int radius, int width, int height) {
        int max = Math.min(width, height) / 2;
        return Math.max(0, Math.min(radius, max));
    }
}
