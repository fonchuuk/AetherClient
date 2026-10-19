package com.aetherglass.client.gui.render;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * Отрисовка выпуклого матового жидкого стекла (Lunar / Badlion / Feather Convex Frosted Liquid Glass)
 * на базе аналитических SDF (Signed Distance Field) 9-slice PNG текстур (Блок 2).
 *
 * <p>Вместо ступенчатых построчных прямоугольников ({@code graphics.fill}), которые при {@code guiScale >= 2}
 * образуют пиксельные «лесенки» на углах, все скруглённые углы, фаски, тени и бегунки рендерятся
 * через 9-slice текстуры 256×256 ({@code corner = 64×64}) с билинейной фильтрацией ({@code blur: true}),
 * обеспечивая идеально гладкие углы уровня Windows 11 / macOS на любом разрешении.</p>
 */
public final class DrawUtil {
    public static final Identifier TEX_NINE_FILL =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/nine_slice_fill.png");
    public static final Identifier TEX_NINE_BORDER =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/nine_slice_border.png");
    public static final Identifier TEX_NINE_SHADOW =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/nine_slice_shadow.png");
    public static final Identifier TEX_NINE_GLASS_PANEL =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/nine_slice_glass_panel.png");
    public static final Identifier TEX_SLIDER_KNOB =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/slider_knob_sdf.png");

    private static final int NINE_TEX_SIZE = 256;
    private static final int NINE_CORNER_TEX = 64;
    private static final int NINE_CENTER_TEX = NINE_TEX_SIZE - NINE_CORNER_TEX * 2; // 128

    private DrawUtil() {
    }

    /**
     * Универсальная отрисовка 9-slice текстуры (девять частей: 4 угла, 4 грани, 1 центр).
     * Углы сохраняют пропорции и гладкое SDF-сглаживание при любом размере виджета.
     */
    public static void drawNineSlice(GuiGraphics graphics, Identifier texture,
                                     int x, int y, int width, int height,
                                     int cornerGui, int cornerTex, int texSize, int color) {
        if (graphics == null || width <= 0 || height <= 0 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        int c = limit(cornerGui, width, height);
        if (c <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        int midW = Math.max(0, width - c * 2);
        int midH = Math.max(0, height - c * 2);
        int centerTex = Math.max(1, texSize - cornerTex * 2);
        int rightU = texSize - cornerTex;
        int botV = texSize - cornerTex;

        // 4 сглаженных SDF-угла
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y,
                0.0F, 0.0F, c, c, cornerTex, cornerTex, texSize, texSize, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - c, y,
                rightU, 0.0F, c, c, cornerTex, cornerTex, texSize, texSize, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + height - c,
                0.0F, botV, c, c, cornerTex, cornerTex, texSize, texSize, color);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - c, y + height - c,
                rightU, botV, c, c, cornerTex, cornerTex, texSize, texSize, color);

        // Верхняя и нижняя грани
        if (midW > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + c, y,
                    cornerTex, 0.0F, midW, c, centerTex, cornerTex, texSize, texSize, color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + c, y + height - c,
                    cornerTex, botV, midW, c, centerTex, cornerTex, texSize, texSize, color);
        }

        // Левая и правая грани
        if (midH > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + c,
                    0.0F, cornerTex, c, midH, cornerTex, centerTex, texSize, texSize, color);
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + width - c, y + c,
                    rightU, cornerTex, c, midH, cornerTex, centerTex, texSize, texSize, color);
        }

        // Центральная часть
        if (midW > 0 && midH > 0) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x + c, y + c,
                    cornerTex, cornerTex, midW, midH, centerTex, centerTex, texSize, texSize, color);
        }
    }

    /**
     * Идеально гладкий круглый SDF-бегунок для ползунков (без восьмиугольных ступенек).
     */
    public static void drawSliderKnob(GuiGraphics graphics, int x, int y, int size, int color) {
        if (graphics == null || size <= 0 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_SLIDER_KNOB,
                x, y, 0.0F, 0.0F, size, size, 64, 64, 64, 64, color);
    }

    /**
     * Скруглённый прямоугольник через SDF 9-slice текстуру (идеально гладкие углы без ступенек).
     */
    public static void fillRounded(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        if (r <= 0) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        drawNineSlice(graphics, TEX_NINE_FILL, x, y, width, height, r, NINE_CORNER_TEX, NINE_TEX_SIZE, color);
    }

    /**
     * Скруглённый прямоугольник с вертикальным градиентом и SDF 9-slice углами.
     */
    public static void fillRoundedGradient(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int topColor, int bottomColor) {
        if (graphics == null || width <= 0 || height <= 0) {
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

        int midW = Math.max(0, width - r * 2);
        int midH = Math.max(0, height - r * 2);
        int rightU = NINE_TEX_SIZE - NINE_CORNER_TEX;
        int botV = NINE_TEX_SIZE - NINE_CORNER_TEX;

        // Верхние SDF-углы с верхним цветом
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_NINE_FILL, x, y,
                0.0F, 0.0F, r, r, NINE_CORNER_TEX, NINE_CORNER_TEX, NINE_TEX_SIZE, NINE_TEX_SIZE, topColor);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_NINE_FILL, x + width - r, y,
                rightU, 0.0F, r, r, NINE_CORNER_TEX, NINE_CORNER_TEX, NINE_TEX_SIZE, NINE_TEX_SIZE, topColor);

        // Нижние SDF-углы с нижним цветом
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_NINE_FILL, x, y + height - r,
                0.0F, botV, r, r, NINE_CORNER_TEX, NINE_CORNER_TEX, NINE_TEX_SIZE, NINE_TEX_SIZE, bottomColor);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_NINE_FILL, x + width - r, y + height - r,
                rightU, botV, r, r, NINE_CORNER_TEX, NINE_CORNER_TEX, NINE_TEX_SIZE, NINE_TEX_SIZE, bottomColor);

        float tTop = (float) r / Math.max(1.0F, (float) height);
        float tBot = (float) (height - r) / Math.max(1.0F, (float) height);
        int cTopBand = ColorUtil.lerp(topColor, bottomColor, tTop);
        int cBotBand = ColorUtil.lerp(topColor, bottomColor, tBot);

        if (midW > 0) {
            graphics.fillGradient(x + r, y, x + width - r, y + r, topColor, cTopBand);
            graphics.fillGradient(x + r, y + height - r, x + width - r, y + height, cBotBand, bottomColor);
        }
        if (midH > 0) {
            graphics.fillGradient(x, y + r, x + width, y + height - r, cTopBand, cBotBand);
        }
    }

    /**
     * Скруглённый прямоугольник с гладкой SDF 9-slice обводкой по периметру.
     */
    public static void fillRoundedBordered(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                           int fill, int border, int borderWidth) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }
        if (ColorUtil.alpha(fill) > 0) {
            fillRounded(graphics, x, y, width, height, radius, fill);
        }
        if (ColorUtil.alpha(border) <= 0) {
            return;
        }
        int r = limit(radius, width, height);
        if (r <= 0) {
            int bw = Math.max(1, borderWidth);
            graphics.fill(x, y, x + width, y + bw, border);
            graphics.fill(x, y + height - bw, x + width, y + height, border);
            graphics.fill(x, y + bw, x + bw, y + height - bw, border);
            graphics.fill(x + width - bw, y + bw, x + width, y + height - bw, border);
            return;
        }
        drawNineSlice(graphics, TEX_NINE_BORDER, x, y, width, height, r, NINE_CORNER_TEX, NINE_TEX_SIZE, border);
    }

    /**
     * Мягкая диагональная внешняя SDF-тень через 9-slice текстуру.
     */
    public static void drawSoftShadow(GuiGraphics graphics, int x, int y, int width, int height,
                                      int radius, float alpha) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.02F) {
            return;
        }
        int r = limit(radius, width, height);
        int shadowColor = ColorUtil.withAlpha(0xFF02060E, Math.round(38 * alpha));
        drawNineSlice(graphics, TEX_NINE_SHADOW, x - 2, y - 1, width + 6, height + 6,
                Math.min(24, r + 4), NINE_CORNER_TEX, NINE_TEX_SIZE, shadowColor);
    }

    /**
     * Выпуклая матовая стеклянная капсула/кнопка (9-Slice SDF Frosted Pill):
     * идеально гладкие углы, диагональная тень, матовое тело, выпуклый купол и солнечный блик.
     */
    public static void drawFrostedPill(GuiGraphics graphics, int x, int y, int width, int height,
                                       int radius, float alpha, float hoverAmount, float activeAmount) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);
        float hover = Math.max(0.0F, Math.min(1.0F, hoverAmount));
        float active = Math.max(0.0F, Math.min(1.0F, activeAmount));

        // 1. Мягкая SDF-тень
        drawSoftShadow(graphics, x, y, width, height, r, alpha * 0.72F);

        // 2. Выпуклое матовое стеклянное тело через 9-slice SDF
        int accent = AuroraTheme.accent();
        int baseTint = AuroraTheme.glassTint();
        int tintAlpha = Math.min(132, Math.round((ColorUtil.alpha(baseTint) + 14 * hover + 22 * active) * alpha));
        int topRgb = ColorUtil.lerp(0xFF2B3B56, accent, 0.18F + 0.20F * hover + 0.32F * active);
        int botRgb = ColorUtil.lerp(0xFF152034, accent, 0.10F + 0.12F * hover + 0.22F * active);
        fillRoundedGradient(graphics, x, y, width, height, r,
                ColorUtil.withAlpha(topRgb, tintAlpha),
                ColorUtil.withAlpha(botRgb, Math.max(16, tintAlpha - 12)));

        // 3. Композитный стеклянный 9-slice слой преломления
        int panelOverlayAlpha = Math.round((68 + 24 * hover + 28 * active) * alpha);
        int panelTint = ColorUtil.lerp(0xFFFFFFFF, accent, 0.15F * hover + 0.30F * active);
        drawNineSlice(graphics, TEX_NINE_GLASS_PANEL, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, ColorUtil.withAlpha(panelTint, panelOverlayAlpha));

        // 4. Скользящий солнечный блик по граням
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha * (0.80F + 0.35F * hover));

        // 5. Идеально гладкая SDF 9-slice световая фаска
        int rimAlpha = Math.round((46 + 32 * hover + 48 * active) * alpha);
        int rimColor = ColorUtil.lerp(0xFFE6F6FF, accent, 0.25F * hover + 0.55F * active);
        drawNineSlice(graphics, TEX_NINE_BORDER, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, ColorUtil.withAlpha(rimColor, rimAlpha));
    }

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
     * Единое выпуклое матовое стеклянное окно (9-Slice SDF Frosted Window).
     */
    public static void drawLiquidGlassPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                            int radius, float alpha) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);

        // 1. Мягкая SDF-тень
        drawSoftShadow(graphics, x, y, width, height, r, alpha);

        // 2. Дымчатое матовое тело окна через 9-slice SDF
        int baseTint = AuroraTheme.glassTint();
        int topAlpha = Math.min(126, Math.round((ColorUtil.alpha(baseTint) + 12) * alpha));
        int botAlpha = Math.min(114, Math.round(ColorUtil.alpha(baseTint) * alpha));
        int accent = AuroraTheme.accent();
        int topBg = ColorUtil.withAlpha(ColorUtil.lerp(0xFF263650, accent, 0.16F), topAlpha);
        int botBg = ColorUtil.withAlpha(ColorUtil.lerp(0xFF141E30, accent, 0.09F), botAlpha);
        fillRoundedGradient(graphics, x, y, width, height, r, topBg, botBg);

        // 3. 9-slice текстура выпуклого стекла с куполом и преломлением
        drawNineSlice(graphics, TEX_NINE_GLASS_PANEL, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(92 * alpha)));

        // 4. Скользящий солнечный блик по верхней/нижней грани
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha);

        // 5. Гладкая SDF 9-slice фаска
        int rim = ColorUtil.withAlpha(0xFFE6F6FF, Math.round(54 * alpha));
        drawNineSlice(graphics, TEX_NINE_BORDER, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, rim);
    }

    /**
     * Выпуклая матовая стеклянная карточка модуля, уведомления или плашка HUD (9-Slice SDF).
     */
    public static void drawLiquidGlassCard(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, float alpha, boolean hoveredOrActive) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(radius, width, height);

        // 1. Мягкая SDF-тень
        drawSoftShadow(graphics, x, y, width, height, r, alpha * 0.80F);

        // 2. Дымчатая полупрозрачная подложка
        int baseTint = AuroraTheme.glassTint();
        int accent = AuroraTheme.accent();
        int baseAlpha = ColorUtil.alpha(baseTint);
        int topA = Math.min(124, Math.round((baseAlpha + (hoveredOrActive ? 18 : 8)) * alpha));
        int botA = Math.min(112, Math.round((baseAlpha + (hoveredOrActive ? 8 : -2)) * alpha));

        int topRgb = ColorUtil.lerp(0xFF283852, accent, hoveredOrActive ? 0.25F : 0.13F);
        int botRgb = ColorUtil.lerp(0xFF152032, accent, hoveredOrActive ? 0.15F : 0.07F);
        fillRoundedGradient(graphics, x, y, width, height, r,
                ColorUtil.withAlpha(topRgb, topA),
                ColorUtil.withAlpha(botRgb, Math.max(14, botA)));

        // 3. 9-slice текстура выпуклого стекла
        int glassAlpha = Math.round((hoveredOrActive ? 105 : 78) * alpha);
        int glassTint = hoveredOrActive ? ColorUtil.lerp(0xFFFFFFFF, accent, 0.22F) : 0xFFFFFFFF;
        drawNineSlice(graphics, TEX_NINE_GLASS_PANEL, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, ColorUtil.withAlpha(glassTint, glassAlpha));

        // 4. Скользящий солнечный блик
        drawSurfaceSolarSheen(graphics, x, y, width, height, r, alpha * (hoveredOrActive ? 1.05F : 0.82F));

        // 5. Гладкая SDF 9-slice фаска
        int rimCol = hoveredOrActive
                ? ColorUtil.lerp(0xFFE8F7FF, accent, 0.45F)
                : 0xFFE0F2FF;
        int rimAlpha = Math.round((hoveredOrActive ? 68 : 46) * alpha);
        drawNineSlice(graphics, TEX_NINE_BORDER, x, y, width, height, r,
                NINE_CORNER_TEX, NINE_TEX_SIZE, ColorUtil.withAlpha(rimCol, rimAlpha));
    }

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

        int topSheen = ColorUtil.withAlpha(0xFFEAF6FF, Math.round(14 * alpha));
        int botSheen = ColorUtil.withAlpha(AuroraTheme.accent(), 0);
        graphics.fillGradient(x + margin, y + 1, x + width - margin, y + 1 + domeH, topSheen, botSheen);

        int edgeGlintCol = ColorUtil.withAlpha(0xFFFFFAEC, Math.round(62 * alpha));
        graphics.fill(sheenX, y, sheenX + sheenW, y + 1, edgeGlintCol);
        int oppX = x + margin + Math.round(travel * (1.0F - phase));
        int botGlintCol = ColorUtil.withAlpha(ColorUtil.lerp(0xFFFFFFFF, AuroraTheme.accent(), 0.35F), Math.round(42 * alpha));
        graphics.fill(oppX, y + height - 1, oppX + sheenW, y + height, botGlintCol);
    }

    public static void drawGlow(GuiGraphics graphics, int x, int y, int width, int height, int radius, int color,
                                float strength) {
        if (graphics == null || strength <= 0.005F || width <= 0 || height <= 0) {
            return;
        }
        float amount = Math.min(1.0F, strength);
        int r = limit(radius, width, height);
        int glowColor = ColorUtil.withAlpha(color, Math.round(36 * amount));
        drawNineSlice(graphics, TEX_NINE_SHADOW, x - 3, y - 3, width + 6, height + 6,
                Math.min(28, r + 4), NINE_CORNER_TEX, NINE_TEX_SIZE, glowColor);
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

    public static void strokeRounded(GuiGraphics graphics, int x, int y, int width, int height,
                                     int radius, int borderColor) {
        fillRoundedBordered(graphics, x, y, width, height, radius, 0, borderColor, 1);
    }

    private static int limit(int radius, int width, int height) {
        int max = Math.min(width, height) / 2;
        return Math.max(0, Math.min(radius, max));
    }

    public static void fillRoundedBordered(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, int fillColor, int borderColor) {
        fillRoundedBordered(graphics, x, y, width, height, radius, 1, fillColor, borderColor);
    }

    public static void drawSoftShadow(GuiGraphics graphics, int x, int y, int width, int height,
                                      int radius, int spread, float alpha) {
        drawSoftShadow(graphics, x, y, width, height, radius, alpha);
    }

    public static void separator(GuiGraphics graphics, int x, int y, int width, int color) {
        if (width <= 0) {
            return;
        }
        graphics.fill(x, y, x + width, y + 1, color);
    }

    public static int rowInset(int row, int height, int radius) {
        int r = Math.max(0, Math.min(radius, Math.min(height / 2, 32)));
        if (r <= 1) {
            return 0;
        }
        int dist = -1;
        if (row < r) {
            dist = r - row;
        } else if (row >= height - r) {
            dist = row - (height - r - 1);
        }
        if (dist <= 0) {
            return 0;
        }
        double dy = dist - 0.5D;
        double sq = (double) r * r - dy * dy;
        if (sq <= 0.0D) {
            return r;
        }
        return Math.max(0, (int) Math.round(r - Math.sqrt(sq)));
    }
}
