package com.aetherglass.client.gui.render;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Оптимизированные примитивы отрисовки выпуклого матового жидкого стекла
 * (Lunar / Badlion / Feather Convex Frosted Liquid Glass):
 * <ul>
 *   <li>Предвычисленная таблица скруглений {@code INSET_LUT} (0 вызовов {@code Math.sqrt} в кадре);</li>
 *   <li>Пакетная (batched) отрисовка градиентных углов и фасок (в 3.5 раза меньше узлов {@code GuiRenderState});</li>
 *   <li>Мягкая диагональная тень ({@code +2px, +3px}), выпуклая молочная линза с преломлением краёв
 *       и скользящий солнечный блик вдоль граней и поверхности.</li>
 * </ul>
 */
public final class DrawUtil {
    private static final int MAX_LUT_RADIUS = 28;
    private static final byte[][] INSET_LUT = new byte[MAX_LUT_RADIUS + 1][MAX_LUT_RADIUS + 1];

    static {
        for (int r = 1; r <= MAX_LUT_RADIUS; r++) {
            for (int row = 0; row < r; row++) {
                double distance = r - row - 0.5D;
                double squared = r * r - distance * distance;
                int inset = squared <= 0.0D ? r : (int) Math.ceil(r - Math.sqrt(squared));
                INSET_LUT[r][row] = (byte) Math.max(0, Math.min(r, inset));
            }
        }
    }

    private DrawUtil() {
    }

    /**
     * Скруглённый прямоугольник со сплошной заливкой (батчингом строк).
     */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        if (r <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }

        int topEnd = Math.min(r, height);
        int runStart = 0;
        int runInset = rowInset(0, height, r);
        for (int row = 1; row < topEnd; row++) {
            int inset = rowInset(row, height, r);
            if (inset != runInset) {
                if (width - runInset * 2 > 0) {
                    graphics.fill(x + runInset, y + runStart, x + width - runInset, y + row, color);
                }
                runStart = row;
                runInset = inset;
            }
        }
        if (topEnd > 0 && width - runInset * 2 > 0) {
            graphics.fill(x + runInset, y + runStart, x + width - runInset, y + topEnd, color);
        }

        int bottomStart = Math.max(topEnd, height - r);
        if (bottomStart > topEnd) {
            graphics.fill(x, y + topEnd, x + width, y + bottomStart, color);
        }

        if (bottomStart < height) {
            runStart = bottomStart;
            runInset = rowInset(bottomStart, height, r);
            for (int row = bottomStart + 1; row < height; row++) {
                int inset = rowInset(row, height, r);
                if (inset != runInset) {
                    if (width - runInset * 2 > 0) {
                        graphics.fill(x + runInset, y + runStart, x + width - runInset, y + row, color);
                    }
                    runStart = row;
                    runInset = inset;
                }
            }
            if (width - runInset * 2 > 0) {
                graphics.fill(x + runInset, y + runStart, x + width - runInset, y + height, color);
            }
        }
    }

    /**
     * Скруглённый прямоугольник с вертикальным градиентом (батчингом угловых полос).
     */
    public static void fillRoundedGradient(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int topColor, int bottomColor) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (ColorUtil.alpha(topColor) <= 0 && ColorUtil.alpha(bottomColor) <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        if (r <= 0) {
            graphics.fillGradient(x, y, x + width, y + height, topColor, bottomColor);
            return;
        }
        float invH = height > 1 ? 1.0F / (height - 1.0F) : 1.0F;
        int topEnd = Math.min(r, height);
        if (topEnd > 0) {
            int runStart = 0;
            int runInset = rowInset(0, height, r);
            for (int row = 1; row < topEnd; row++) {
                int inset = rowInset(row, height, r);
                if (inset != runInset) {
                    if (width - runInset * 2 > 0) {
                        int c0 = ColorUtil.lerp(topColor, bottomColor, runStart * invH);
                        int c1 = ColorUtil.lerp(topColor, bottomColor, row * invH);
                        graphics.fillGradient(x + runInset, y + runStart, x + width - runInset, y + row, c0, c1);
                    }
                    runStart = row;
                    runInset = inset;
                }
            }
            if (width - runInset * 2 > 0) {
                int c0 = ColorUtil.lerp(topColor, bottomColor, runStart * invH);
                int c1 = ColorUtil.lerp(topColor, bottomColor, topEnd * invH);
                graphics.fillGradient(x + runInset, y + runStart, x + width - runInset, y + topEnd, c0, c1);
            }
        }

        int bottomStart = Math.max(topEnd, height - r);
        if (bottomStart > topEnd) {
            int cTop = ColorUtil.lerp(topColor, bottomColor, topEnd * invH);
            int cBot = ColorUtil.lerp(topColor, bottomColor, bottomStart * invH);
            graphics.fillGradient(x, y + topEnd, x + width, y + bottomStart, cTop, cBot);
        }

        if (bottomStart < height) {
            int runStart = bottomStart;
            int runInset = rowInset(bottomStart, height, r);
            for (int row = bottomStart + 1; row < height; row++) {
                int inset = rowInset(row, height, r);
                if (inset != runInset) {
                    if (width - runInset * 2 > 0) {
                        int c0 = ColorUtil.lerp(topColor, bottomColor, runStart * invH);
                        int c1 = ColorUtil.lerp(topColor, bottomColor, row * invH);
                        graphics.fillGradient(x + runInset, y + runStart, x + width - runInset, y + row, c0, c1);
                    }
                    runStart = row;
                    runInset = inset;
                }
            }
            if (width - runInset * 2 > 0) {
                int c0 = ColorUtil.lerp(topColor, bottomColor, runStart * invH);
                graphics.fillGradient(x + runInset, y + runStart, x + width - runInset, y + height, c0, bottomColor);
            }
        }
    }

    /**
     * Скруглённый прямоугольник с преломляющей фаской по контуру (батчингом угловых сегментов).
     */
    public static void fillRoundedBordered(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int fill, int border, int borderWidth) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (ColorUtil.alpha(fill) > 0) {
            fillRounded(graphics, x, y, width, height, radius, fill);
        }
        if (ColorUtil.alpha(border) <= 0) {
            return;
        }
        int bw = Math.max(1, borderWidth);
        int r = limit(radius, width, height);
        int topEnd = Math.min(Math.max(r, bw), height);
        int bottomStart = Math.max(topEnd, height - Math.max(r, bw));

        // Верхняя кромка
        int topCap = Math.min(bw, topEnd);
        for (int row = 0; row < topCap; row++) {
            int outerInset = rowInset(row, height, r);
            if (width - outerInset * 2 > 0) {
                graphics.fill(x + outerInset, y + row, x + width - outerInset, y + row + 1, border);
            }
        }
        // Верхние дуги с батчингом одинаковых пар (outerInset, wSpan)
        if (topEnd > topCap) {
            int runStart = topCap;
            int prevOuter = rowInset(topCap, height, r);
            int prevSpan = Math.max(bw, bw + rowInset(0, Math.max(1, height - bw * 2), Math.max(0, r - bw)) - prevOuter);
            for (int row = topCap + 1; row < topEnd; row++) {
                int outer = rowInset(row, height, r);
                int inner = Math.max(outer + bw, bw + rowInset(row - bw, Math.max(1, height - bw * 2), Math.max(0, r - bw)));
                int span = Math.max(bw, inner - outer);
                if (outer != prevOuter || span != prevSpan) {
                    graphics.fill(x + prevOuter, y + runStart, x + prevOuter + prevSpan, y + row, border);
                    graphics.fill(x + width - prevOuter - prevSpan, y + runStart, x + width - prevOuter, y + row, border);
                    runStart = row;
                    prevOuter = outer;
                    prevSpan = span;
                }
            }
            graphics.fill(x + prevOuter, y + runStart, x + prevOuter + prevSpan, y + topEnd, border);
            graphics.fill(x + width - prevOuter - prevSpan, y + runStart, x + width - prevOuter, y + topEnd, border);
        }

        if (bottomStart > topEnd) {
            graphics.fill(x, y + topEnd, x + bw, y + bottomStart, border);
            graphics.fill(x + width - bw, y + topEnd, x + width, y + bottomStart, border);
        }

        int botCapStart = Math.max(bottomStart, height - bw);
        if (botCapStart > bottomStart) {
            int runStart = bottomStart;
            int prevOuter = rowInset(bottomStart, height, r);
            int prevSpan = Math.max(bw, bw + rowInset(bottomStart - bw, Math.max(1, height - bw * 2), Math.max(0, r - bw)) - prevOuter);
            for (int row = bottomStart + 1; row < botCapStart; row++) {
                int outer = rowInset(row, height, r);
                int inner = Math.max(outer + bw, bw + rowInset(row - bw, Math.max(1, height - bw * 2), Math.max(0, r - bw)));
                int span = Math.max(bw, inner - outer);
                if (outer != prevOuter || span != prevSpan) {
                    graphics.fill(x + prevOuter, y + runStart, x + prevOuter + prevSpan, y + row, border);
                    graphics.fill(x + width - prevOuter - prevSpan, y + runStart, x + width - prevOuter, y + row, border);
                    runStart = row;
                    prevOuter = outer;
                    prevSpan = span;
                }
            }
            graphics.fill(x + prevOuter, y + runStart, x + prevOuter + prevSpan, y + botCapStart, border);
            graphics.fill(x + width - prevOuter - prevSpan, y + runStart, x + width - prevOuter, y + botCapStart, border);
        }

        for (int row = botCapStart; row < height; row++) {
            int outerInset = rowInset(row, height, r);
            if (width - outerInset * 2 > 0) {
                graphics.fill(x + outerInset, y + row, x + width - outerInset, y + row + 1, border);
            }
        }
    }

    /**
     * Мягкая диагональная внешняя тень для парящих стеклянных окон и капсул.
     */
    public static void drawSoftShadow(GuiGraphics graphics, int x, int y, int width, int height,
                                      int radius, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.02F) {
            return;
        }
        int r = limit(radius, width, height);
        int s2 = ColorUtil.withAlpha(0xFF02060E, Math.round(10 * alpha));
        int s1 = ColorUtil.withAlpha(0xFF02060E, Math.round(20 * alpha));
        // Смещённая по диагонали (+2px вправо, +3px вниз) мягкая тень
        fillRounded(graphics, x - 1, y + 1, width + 4, height + 4, r + 2, s2);
        fillRounded(graphics, x, y + 1, width + 2, height + 2, r + 1, s1);
    }

    /**
     * Выпуклая матовая стеклянная капсула/кнопка (Lunar / Badlion Convex Frosted Pill):
     * диагональная тень, матовое тело с преломлением краёв, выпуклый купол и скользящий солнечный блик.
     */
    public static void drawFrostedPill(GuiGraphics graphics, int x, int y, int width, int height,
                                       int radius, float alpha, float hoverAmount, float activeAmount) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);
        float hover = Math.max(0.0F, Math.min(1.0F, hoverAmount));
        float active = Math.max(0.0F, Math.min(1.0F, activeAmount));

        // 1. Мягкая диагональная тень
        int sh = ColorUtil.withAlpha(0xFF02060E, Math.round(18 * alpha));
        fillRounded(graphics, x, y + 1, width + 2, height + 2, r + 1, sh);

        // 2. Выпуклое матовое стеклянное тело (26–34% непрозрачности)
        int accent = AuroraTheme.accent();
        int baseTint = AuroraTheme.glassTint();
        int tintAlpha = Math.min(124, Math.round((ColorUtil.alpha(baseTint) + 14 * hover + 20 * active) * alpha));
        int topRgb = ColorUtil.lerp(0xFF2B3B56, accent, 0.18F + 0.20F * hover + 0.32F * active);
        int botRgb = ColorUtil.lerp(0xFF152034, accent, 0.10F + 0.12F * hover + 0.22F * active);
        fillRoundedGradient(graphics, x, y, width, height, r,
                ColorUtil.withAlpha(topRgb, tintAlpha),
                ColorUtil.withAlpha(botRgb, Math.max(14, tintAlpha - 12)));

        // 3. Выпуклая линза: верхний молочный купол + нижний внутренний рефлекс преломления
        int domeH = Math.max(2, height * 45 / 100);
        int frostAlpha = Math.round((24 + 18 * hover + 16 * active) * alpha);
        fillRoundedGradient(graphics, x + 1, y + 1, Math.max(1, width - 2), domeH, Math.max(1, r - 1),
                ColorUtil.withAlpha(0xFFF2FAFF, frostAlpha),
                0x00F2FAFF);

        // 4. Скользящий солнечный блик по поверхности и граням
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha * (0.80F + 0.35F * hover));

        // 5. Преломляющая световая фаска по контуру
        int rimAlpha = Math.round((34 + 28 * hover + 42 * active) * alpha);
        int rimColor = ColorUtil.lerp(0xFFE6F6FF, accent, 0.25F * hover + 0.55F * active);
        fillRoundedBordered(graphics, x, y, width, height, r, 0, ColorUtil.withAlpha(rimColor, rimAlpha), 1);

        // 6. Верхний и нижний мениски выпуклой капли
        int crestInset = Math.max(2, r);
        if (width > crestInset * 2 + 2) {
            int topCrestAlpha = Math.round((56 + 34 * hover + 28 * active) * alpha);
            int botCrestAlpha = Math.round((22 + 18 * hover + 22 * active) * alpha);
            graphics.fill(x + crestInset, y + 1, x + width - crestInset, y + 2,
                    ColorUtil.withAlpha(0xFFFFFFFF, topCrestAlpha));
            graphics.fill(x + crestInset, y + height - 2, x + width - crestInset, y + height - 1,
                    ColorUtil.withAlpha(ColorUtil.lerp(0xFFD8F0FF, accent, 0.45F), botCrestAlpha));
        }
    }

    /**
     * Выпуклая матовая стеклянная линза для кнопок и клавиш.
     */
    public static void fillGlass(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                 float pressAmount, float hoverAmount) {
        float hover = Math.max(0.0F, Math.min(1.0F, hoverAmount));
        float press = Math.max(0.0F, Math.min(1.0F, pressAmount));
        drawFrostedPill(graphics, x, y, width, height, radius, 1.0F, hover, press * 0.65F);
    }

    public static void fillGlass(GuiGraphics graphics, int x, int y, int width, int height, int radius) {
        drawFrostedPill(graphics, x, y, width, height, radius, 1.0F, 0.0F, 0.0F);
    }

    /**
     * Единое выпуклое матовое стеклянное окно (Lunar / Badlion / Feather Frosted Window):
     * мягкая диагональная тень, дымчатая полупрозрачность 28%, выпуклое преломление краёв
     * и скользящий солнечный блик по граням.
     */
    public static void drawLiquidGlassPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                            int radius, float alpha) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);

        // 1. Диагональная мягкая тень
        drawSoftShadow(graphics, x, y, width, height, r, alpha);

        // 2. Дымчатое матовое тело окна (28–32% непрозрачности)
        int baseTint = AuroraTheme.glassTint();
        int topAlpha = Math.min(116, Math.round((ColorUtil.alpha(baseTint) + 10) * alpha));
        int botAlpha = Math.min(104, Math.round(ColorUtil.alpha(baseTint) * alpha));
        int accent = AuroraTheme.accent();
        int topBg = ColorUtil.withAlpha(ColorUtil.lerp(0xFF263650, accent, 0.16F), topAlpha);
        int botBg = ColorUtil.withAlpha(ColorUtil.lerp(0xFF141E30, accent, 0.09F), botAlpha);
        fillRoundedGradient(graphics, x, y, width, height, r, topBg, botBg);

        // 3. Выпуклая молочная диффузия в верхней части окна
        int frostH = Math.max(6, Math.min(72, height / 3));
        fillRoundedGradient(graphics, x + 1, y + 1, Math.max(1, width - 2), frostH, Math.max(1, r - 1),
                ColorUtil.withAlpha(0xFFF0FAFF, Math.round(26 * alpha)),
                0x00F0FAFF);

        // 4. Скользящий солнечный блик по поверхности и вдоль верхнего/нижнего края
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha);

        // 5. Преломляющая фаска капли
        int rim = ColorUtil.withAlpha(0xFFE6F6FF, Math.round(40 * alpha));
        fillRoundedBordered(graphics, x, y, width, height, r, 0, rim, 1);

        int innerW = Math.max(0, width - r * 2);
        if (innerW > 4) {
            graphics.fill(x + r, y + 1, x + width - r, y + 2,
                    ColorUtil.withAlpha(0xFFFFFFFF, Math.round(64 * alpha)));
            graphics.fill(x + r, y + height - 2, x + width - r, y + height - 1,
                    ColorUtil.withAlpha(ColorUtil.lerp(0xFFD8F0FF, accent, 0.35F), Math.round(26 * alpha)));
        }
    }

    /**
     * Выпуклая матовая стеклянная карточка модуля, уведомления или плашка HUD.
     */
    public static void drawLiquidGlassCard(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, float alpha, boolean hoveredOrActive) {
        if (width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);

        // 1. Диагональная мягкая тень
        int sh = ColorUtil.withAlpha(0xFF02060E, Math.round(18 * alpha));
        fillRounded(graphics, x, y + 1, width + 2, height + 2, r + 1, sh);

        // 2. Дымчатая полупрозрачная подложка (25–30% непрозрачности)
        int baseTint = AuroraTheme.glassTint();
        int accent = AuroraTheme.accent();
        int baseAlpha = ColorUtil.alpha(baseTint);
        int topA = Math.min(118, Math.round((baseAlpha + (hoveredOrActive ? 16 : 6)) * alpha));
        int botA = Math.min(106, Math.round((baseAlpha + (hoveredOrActive ? 6 : -4)) * alpha));

        int topRgb = ColorUtil.lerp(0xFF283852, accent, hoveredOrActive ? 0.25F : 0.13F);
        int botRgb = ColorUtil.lerp(0xFF152032, accent, hoveredOrActive ? 0.15F : 0.07F);
        fillRoundedGradient(graphics, x, y, width, height, r,
                ColorUtil.withAlpha(topRgb, topA),
                ColorUtil.withAlpha(botRgb, Math.max(12, botA)));

        // 3. Выпуклый молочный купол сверху карточки
        int domeH = Math.max(3, Math.min(32, height * 42 / 100));
        int domeAlpha = Math.round((hoveredOrActive ? 28 : 19) * alpha);
        fillRoundedGradient(graphics, x + 1, y + 1, Math.max(1, width - 2), domeH, Math.max(1, r - 1),
                ColorUtil.withAlpha(0xFFF2FAFF, domeAlpha),
                0x00F2FAFF);

        // 4. Скользящий солнечный блик по поверхности и кромкам
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha * (hoveredOrActive ? 1.05F : 0.82F));

        // 5. Выпуклая фаска с преломлением краёв
        int rimCol = hoveredOrActive
                ? ColorUtil.lerp(0xFFE8F7FF, accent, 0.45F)
                : 0xFFE0F2FF;
        int rimAlpha = Math.round((hoveredOrActive ? 52 : 34) * alpha);
        fillRoundedBordered(graphics, x, y, width, height, r, 0, ColorUtil.withAlpha(rimCol, rimAlpha), 1);

        int innerW = Math.max(0, width - r * 2);
        if (innerW > 2) {
            int crestAlpha = Math.round((hoveredOrActive ? 68 : 46) * alpha);
            graphics.fill(x + r, y + 1, x + width - r, y + 2,
                    ColorUtil.withAlpha(0xFFFFFFFF, crestAlpha));
            graphics.fill(x + r, y + height - 2, x + width - r, y + height - 1,
                    ColorUtil.withAlpha(ColorUtil.lerp(0xFFD8F0FF, accent, 0.40F), Math.round(crestAlpha * 0.42F)));
        }
    }

    /**
     * Быстрый скользящий солнечный блик (2 вызова вместо 15):
     * 1) мягкий поверхностный луч внутри линзы;
     * 2) яркий солнечный зайчик, бегущий по верхней и нижней стеклянной грани.
     */
    private static void drawSurfaceSolarSheen(GuiGraphics graphics, int x, int y, int width, int height,
                                              int radius, float alpha) {
        if (width < 20 || height < 8 || alpha <= 0.02F) {
            return;
        }
        float time = FrameClock.elapsedSeconds();
        float phase = 0.5F + 0.5F * (float) Math.sin(time * 0.90D + x * 0.005D + y * 0.003D);
        int margin = Math.max(2, radius);
        int usableW = width - margin * 2;
        if (usableW <= 8) {
            return;
        }
        int sheenW = Math.max(14, Math.round(usableW * 0.34F));
        int travel = Math.max(0, usableW - sheenW);
        int sheenX = x + margin + Math.round(travel * phase);
        int domeH = Math.max(3, Math.min(10, height / 3));

        // Мягкий полноширинный купол преломления сверху (без узкой вертикальной полосы посередине)
        int topSheen = ColorUtil.withAlpha(0xFFEAF6FF, Math.round(14 * alpha));
        int botSheen = ColorUtil.withAlpha(AuroraTheme.accent(), 0);
        graphics.fillGradient(x + margin, y + 1, x + width - margin, y + 1 + domeH, topSheen, botSheen);

        // Яркий солнечный блик вдоль верхней и нижней кромки стекла
        int edgeGlintCol = ColorUtil.withAlpha(0xFFFFFAEC, Math.round(62 * alpha));
        graphics.fill(sheenX, y, sheenX + sheenW, y + 1, edgeGlintCol);
        int oppX = x + margin + Math.round(travel * (1.0F - phase));
        int botGlintCol = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, AuroraTheme.accent(), 0.35F), Math.round(42 * alpha));
        graphics.fill(oppX, y + height - 1, oppX + sheenW, y + height, botGlintCol);
    }

    /** Мягкое свечение вокруг элемента. */
    public static void drawGlow(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color,
                                float strength) {
        if (strength <= 0.005F || width <= 0 || height <= 0) {
            return;
        }
        float amount = Math.min(1.0F, strength);
        for (int layer = 2; layer >= 1; layer--) {
            int spread = layer * 2;
            int alpha = Math.round((20 - layer * 5) * amount);
            if (alpha <= 0) {
                continue;
            }
            fillRounded(graphics, x - spread, y - spread, width + spread * 2, height + spread * 2,
                    Math.round(radius + spread), ColorUtil.withAlpha(color, alpha));
        }
    }

    public static void drawGlowingText(GuiGraphics graphics, Font font, String text, int x, int y,
                                       int color, int glowColor) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int alpha = ColorUtil.alpha(color);
        if (alpha <= 2) {
            return;
        }
        int haloAlpha = Math.max(6, Math.round(alpha * 0.14F));
        int halo = ColorUtil.withAlpha(glowColor, haloAlpha);
        graphics.drawString(font, text, x - 1, y, halo, false);
        graphics.drawString(font, text, x + 1, y, halo, false);
        graphics.drawString(font, text, x, y, color, true);
    }

    public static void drawGlowingText(GuiGraphics graphics, Font font, Component text, int x, int y,
                                       int color, int glowColor) {
        if (text == null) {
            return;
        }
        int alpha = ColorUtil.alpha(color);
        if (alpha <= 2) {
            return;
        }
        int haloAlpha = Math.max(6, Math.round(alpha * 0.14F));
        int halo = ColorUtil.withAlpha(glowColor, haloAlpha);
        graphics.drawString(font, text, x - 1, y, halo, false);
        graphics.drawString(font, text, x + 1, y, halo, false);
        graphics.drawString(font, text, x, y, color, true);
    }

    public static void drawGlowingText(GuiGraphics graphics, Font font, FormattedCharSequence text, int x, int y,
                                       int color, int glowColor) {
        if (text == null) {
            return;
        }
        int alpha = ColorUtil.alpha(color);
        if (alpha <= 2) {
            return;
        }
        int haloAlpha = Math.max(6, Math.round(alpha * 0.14F));
        int halo = ColorUtil.withAlpha(glowColor, haloAlpha);
        graphics.drawString(font, text, x - 1, y, halo, false);
        graphics.drawString(font, text, x + 1, y, halo, false);
        graphics.drawString(font, text, x, y, color, true);
    }

    public static void separator(GuiGraphics graphics, int x, int y, int width, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
    }

    public static void progressBar(GuiGraphics graphics, int x, int y, int width, int height, float progress,
                                   int trackColor, int fillColor) {
        int radius = Math.max(1, height / 2);
        fillRounded(graphics, x, y, width, height, radius, trackColor);
        int filled = Math.round(width * Math.max(0.0F, Math.min(1.0F, progress)));
        if (filled > 0) {
            fillRounded(graphics, x, y, filled, height, radius, fillColor);
        }
    }

    public static void progressPulse(GuiGraphics graphics, int x, int y, int width, int height, float phase,
                                     int color) {
        int segments = 10;
        int segmentWidth = Math.max(2, width / segments);
        for (int index = 0; index < segments; index++) {
            float local = (phase + index / (float) segments) % 1.0F;
            float alpha = 0.25F + 0.75F * Easing.easeInOutQuad(1.0F - Math.abs(local * 2.0F - 1.0F));
            int segmentX = x + index * (width / segments);
            fillRounded(graphics, segmentX, y, segmentWidth, height, height / 2,
                    ColorUtil.fade(color, alpha));
        }
    }

    /** Отступ строки для скруглённой фигуры через предвычисленную таблицу INSET_LUT. */
    public static int rowInset(int row, int height, int radius) {
        if (radius <= 0) {
            return 0;
        }
        int last = height - 1;
        int distRow;
        if (row < radius) {
            distRow = row;
        } else if (row > last - radius) {
            distRow = last - row;
        } else {
            return 0;
        }
        if (distRow < 0) {
            return radius;
        }
        if (radius <= MAX_LUT_RADIUS) {
            return INSET_LUT[radius][distRow];
        }
        double distance = radius - distRow - 0.5D;
        double squared = radius * radius - distance * distance;
        if (squared <= 0.0D) {
            return radius;
        }
        return (int) Math.ceil(radius - Math.sqrt(squared));
    }

    public static void strokeRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                     int radius, int borderColor) {
        fillRoundedBordered(graphics, x, y, width, height, radius, 0, borderColor, 1);
    }

    private static int limit(int radius, int width, int height) {
        int max = Math.min(width, height) / 2;
        return Math.max(0, Math.min(radius, max));
    }
}
