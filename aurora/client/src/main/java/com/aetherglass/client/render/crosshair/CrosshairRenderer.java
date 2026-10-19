package com.aetherglass.client.render.crosshair;

import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Отрисовка прицела в точных физических пикселях экрана (Блок 4.3).
 *
 * <p>В ванильном Minecraft координаты {@link GuiGraphics} уже умножены на {@code guiScale}.
 * Чтобы прицел можно было сделать буквально в 1–2 физических пикселя независимо от масштаба
 * интерфейса, мы компенсируем {@code guiScale} через матрицу {@code pose().scale(1 / guiScale)}
 * относительно центра экрана.</p>
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {
    }

    public static void draw(GuiGraphics graphics, int guiWidth, int guiHeight,
                            CrosshairStyle style, int color,
                            float sizePx, float thicknessPx, float gapPx, float spreadPx,
                            boolean centerDot, boolean outline) {
        Minecraft mc = Minecraft.getInstance();
        double scale = (mc != null && mc.getWindow() != null) ? mc.getWindow().getGuiScale() : 2.0D;
        float invScale = (float) (1.0D / Math.max(1.0D, scale));

        float centerX = guiWidth * 0.5F;
        float centerY = guiHeight * 0.5F;

        int size = Math.max(1, Math.round(sizePx));
        int thick = Math.max(1, Math.round(thicknessPx));
        int gap = Math.max(0, Math.round(gapPx + spreadPx));
        int halfThick = thick / 2;

        int alpha = ColorUtil.alpha(color);
        int shadowColor = ColorUtil.withAlpha(0xFF050811, Math.round(alpha * 0.65F));

        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, centerY);
        graphics.pose().scale(invScale, invScale);

        switch (style) {
            case DOT -> {
                int dotD = Math.max(1, Math.round(sizePx));
                int x0 = -dotD / 2;
                int y0 = -dotD / 2;
                drawRect(graphics, x0, y0, dotD, dotD, color, shadowColor, outline);
            }
            case CROSS -> {
                // Горизонтальные лучи
                drawRect(graphics, -gap - size, -halfThick, size, thick, color, shadowColor, outline);
                drawRect(graphics, gap + (thick % 2), -halfThick, size, thick, color, shadowColor, outline);
                // Вертикальные лучи
                drawRect(graphics, -halfThick, -gap - size, thick, size, color, shadowColor, outline);
                drawRect(graphics, -halfThick, gap + (thick % 2), thick, size, color, shadowColor, outline);
                if (centerDot) {
                    drawRect(graphics, -halfThick, -halfThick, thick, thick, color, shadowColor, outline);
                }
            }
            case CIRCLE, CIRCLE_DOT -> {
                int radius = Math.max(2, size + gap);
                drawPixelRing(graphics, radius, thick, color, shadowColor, outline);
                if (style == CrosshairStyle.CIRCLE_DOT || centerDot) {
                    int dotD = Math.max(1, thick);
                    drawRect(graphics, -dotD / 2, -dotD / 2, dotD, dotD, color, shadowColor, outline);
                }
            }
            case CORNERS -> {
                int offset = Math.max(1, gap + 1);
                int arm = Math.max(2, size);
                // Верхний левый уголок
                drawRect(graphics, -offset - arm, -offset - thick, arm, thick, color, shadowColor, outline);
                drawRect(graphics, -offset - thick, -offset - arm, thick, arm, color, shadowColor, outline);
                // Верхний правый уголок
                drawRect(graphics, offset, -offset - thick, arm, thick, color, shadowColor, outline);
                drawRect(graphics, offset, -offset - arm, thick, arm, color, shadowColor, outline);
                // Нижний левый уголок
                drawRect(graphics, -offset - arm, offset, arm, thick, color, shadowColor, outline);
                drawRect(graphics, -offset - thick, offset, thick, arm, color, shadowColor, outline);
                // Нижний правый уголок
                drawRect(graphics, offset, offset, arm, thick, color, shadowColor, outline);
                drawRect(graphics, offset, offset, thick, arm, color, shadowColor, outline);
                if (centerDot) {
                    drawRect(graphics, -halfThick, -halfThick, thick, thick, color, shadowColor, outline);
                }
            }
        }

        graphics.pose().popMatrix();
    }

    private static void drawRect(GuiGraphics graphics, int x, int y, int w, int h,
                                 int color, int shadowColor, boolean outline) {
        if (w <= 0 || h <= 0) {
            return;
        }
        if (outline) {
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, shadowColor);
        }
        graphics.fill(x, y, x + w, y + h, color);
    }

    private static void drawPixelRing(GuiGraphics graphics, int radius, int thickness,
                                      int color, int shadowColor, boolean outline) {
        int steps = Math.max(16, radius * 4);
        double stepAngle = (Math.PI * 2.0D) / steps;
        int halfT = thickness / 2;
        for (int i = 0; i < steps; i++) {
            double angle = i * stepAngle;
            int px = (int) Math.round(Math.cos(angle) * radius) - halfT;
            int py = (int) Math.round(Math.sin(angle) * radius) - halfT;
            if (outline) {
                graphics.fill(px - 1, py - 1, px + thickness + 1, py + thickness + 1, shadowColor);
            }
            graphics.fill(px, py, px + thickness, py + thickness, color);
        }
    }
}
