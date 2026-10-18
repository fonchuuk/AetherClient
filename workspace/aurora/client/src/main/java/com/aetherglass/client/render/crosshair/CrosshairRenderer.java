package com.aetherglass.client.render.crosshair;

import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Свой прицел: рисуется кодом, поверх настоящего игрового кадра.
 *
 * <p>Прицел заменяет ванильный через реестр элементов экрана Fabric API, поэтому
 * он появляется ровно там же, где был обычный, и так же скрывается вместе с
 * интерфейсом. Ванильный прицел при этом не «удаляется» навсегда: при выключении
 * модуля он продолжает рисоваться как обычно.</p>
 */
public final class CrosshairRenderer {
    private CrosshairRenderer() {
    }

    /**
     * Рисует прицел.
     *
     * @param centerX  центр экрана по горизонтали
     * @param centerY  центр экрана по вертикали
     * @param spread   развод прицела от динамики (0 — сомкнут, 1 — максимально)
     */
    public static void draw(GuiGraphics graphics, int centerX, int centerY, CrosshairStyle style, int color,
                            float size, float thickness, float gap, boolean outline, float spread,
                            float guiScale) {
        int accent = ColorController.resolve(color);
        float scale = Math.max(0.5F, guiScale);
        int length = Math.round(size * scale);
        int bar = Math.max(1, Math.round(thickness * scale));
        int space = Math.round(gap * scale * (1.0F + spread * 2.5F));
        int shadow = ColorUtil.withAlpha(0xFF000000, Math.round(90 + 80 * (1.0F - spread)));

        switch (style) {
            case DOT -> {
                int dot = Math.max(2, Math.round(size * 0.45F * scale));
                fillCentered(graphics, centerX, centerY, dot, dot, bar, shadow, accent, outline);
            }
            case CIRCLE, CIRCLE_DOT -> {
                drawRing(graphics, centerX, centerY, length, bar, accent, shadow);
                if (style == CrosshairStyle.CIRCLE_DOT) {
                    int dot = Math.max(1, Math.round(bar * 1.2F));
                    fillCentered(graphics, centerX, centerY, dot * 2, dot * 2, 0, shadow, accent, false);
                }
            }
            case BRACKET -> {
                int corner = Math.max(3, length);
                int offset = space + corner;
                // Четыре уголка: рисуем по два отрезка на каждый угол.
                bar(graphics, centerX - offset, centerY - offset - bar, corner, bar, shadow, accent, outline, true);
                bar(graphics, centerX - offset - bar, centerY - offset, bar, corner, shadow, accent, outline, true);
                bar(graphics, centerX + offset - corner + bar, centerY - offset - bar, corner, bar, shadow, accent,
                        outline, true);
                bar(graphics, centerX + offset, centerY - offset, bar, corner, shadow, accent, outline, true);
                bar(graphics, centerX - offset, centerY + offset, corner, bar, shadow, accent, outline, true);
                bar(graphics, centerX - offset - bar, centerY + offset - corner + bar, bar, corner, shadow, accent,
                        outline, true);
                bar(graphics, centerX + offset - corner + bar, centerY + offset, corner, bar, shadow, accent, outline,
                        true);
                bar(graphics, centerX + offset, centerY + offset - corner + bar, bar, corner, shadow, accent, outline,
                        true);
            }
            case ARROW -> {
                bar(graphics, centerX - space - length, centerY - bar / 2, length, bar, shadow, accent, outline, true);
                bar(graphics, centerX + space, centerY - bar / 2, length, bar, shadow, accent, outline, true);
                bar(graphics, centerX - bar / 2, centerY - space - length, bar, length, shadow, accent, outline, true);
                bar(graphics, centerX - bar / 2, centerY + space, bar, length, shadow, accent, outline, true);
                // Наконечники: короткие штрихи под углом к лучам.
                int tip = Math.max(2, bar);
                fillCentered(graphics, centerX - space - length, centerY, tip * 2, tip * 3, 0, shadow, accent, false);
                fillCentered(graphics, centerX + space + length, centerY, tip * 2, tip * 3, 0, shadow, accent, false);
            }
            default -> {
                bar(graphics, centerX - space - length, centerY - bar / 2, length, bar, shadow, accent, outline, true);
                bar(graphics, centerX + space, centerY - bar / 2, length, bar, shadow, accent, outline, true);
                bar(graphics, centerX - bar / 2, centerY - space - length, bar, length, shadow, accent, outline, true);
                bar(graphics, centerX - bar / 2, centerY + space, bar, length, shadow, accent, outline, true);
            }
        }
    }

    private static void bar(GuiGraphics graphics, int x, int y, int width, int height, int shadow, int accent,
                            boolean outline, boolean withShadow) {
        if (withShadow) {
            graphics.fill(x + 1, y + 1, x + width + 1, y + height + 1, shadow);
        }
        if (outline) {
            graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, ColorUtil.withAlpha(0xFF000000, 110));
        }
        graphics.fill(x, y, x + width, y + height, accent);
    }

    private static void fillCentered(GuiGraphics graphics, int centerX, int centerY, int width, int height,
                                     int outlineSize, int shadow, int accent, boolean outline) {
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        if (outlineSize > 0) {
            graphics.fill(x - outlineSize, y - outlineSize, x + width + outlineSize, y + height + outlineSize, shadow);
        }
        if (outline) {
            graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, ColorUtil.withAlpha(0xFF000000, 110));
        }
        graphics.fill(x, y, x + width, y + height, accent);
    }

    /** Кольцо: рисуется построчно, поэтому остаётся ровным при любом размере. */
    private static void drawRing(GuiGraphics graphics, int centerX, int centerY, int radius, int thickness,
                                 int accent, int shadow) {
        int outer = radius;
        int inner = Math.max(1, radius - thickness);
        for (int row = -outer; row <= outer; row++) {
            int rowAbsolute = Math.abs(row);
            if (rowAbsolute > outer) {
                continue;
            }
            int span;
            if (rowAbsolute >= inner) {
                span = (int) Math.ceil(Math.sqrt(Math.max(0.0D, outer * outer - rowAbsolute * rowAbsolute)));
            } else {
                double innerSpan = Math.sqrt(Math.max(0.0D, outer * outer - rowAbsolute * rowAbsolute));
                double holeSpan = Math.sqrt(Math.max(0.0D, inner * inner - rowAbsolute * rowAbsolute));
                int from = (int) Math.ceil(holeSpan);
                int to = (int) Math.floor(innerSpan);
                if (to <= from) {
                    continue;
                }
                graphics.fill(centerX + from, centerY + row, centerX + to, centerY + row + 1, accent);
                graphics.fill(centerX - to, centerY + row, centerX - from, centerY + row + 1, accent);
                graphics.fill(centerX + from + 1, centerY + row + 1, centerX + to + 1, centerY + row + 2, shadow);
                continue;
            }
            if (span <= 0) {
                continue;
            }
            graphics.fill(centerX - span, centerY + row, centerX + span, centerY + row + 1, accent);
        }
    }
}
