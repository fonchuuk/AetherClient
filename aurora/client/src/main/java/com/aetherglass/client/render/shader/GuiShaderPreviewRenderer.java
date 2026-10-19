package com.aetherglass.client.render.shader;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Отрисовщик живых превью 16 шейдер-эффектов для карточек меню, раздельного
 * предпросмотра блока ({@code BlockShader}) и руки ({@code HandsShader}),
 * а также экранного ореола рук от первого лица.
 */
public final class GuiShaderPreviewRenderer {
    private static final int CARD_COLS = 6;
    private static final int CARD_ROWS = 4;
    private static final int[][] CACHED_CARD_SAMPLES = new int[16][CARD_COLS * CARD_ROWS];
    private static final int[] CACHED_CARD_TICK = new int[16];
    private static final int[] CACHED_CARD_COLOR = new int[16];

    private GuiShaderPreviewRenderer() {
    }

    /**
     * Живое мини-превью эффекта внутри карточки списка из 16 шейдеров (с кэшированием кадров 20 FPS).
     */
    public static void renderCardPreview(GuiGraphics graphics, int x, int y, int width, int height,
                                         ShaderEffect effect, int baseColor,
                                         float intensity, float speed, float opacity,
                                         float timeSeconds, float uiAlpha) {
        if (width <= 2 || height <= 2 || uiAlpha <= 0.01F) {
            return;
        }
        ShaderEffect eff = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        int idx = eff.ordinal();
        int frameTick = (int) (timeSeconds * 20.0F);

        int[] cache = CACHED_CARD_SAMPLES[idx];
        if (CACHED_CARD_TICK[idx] != frameTick || CACHED_CARD_COLOR[idx] != baseColor) {
            CACHED_CARD_TICK[idx] = frameTick;
            CACHED_CARD_COLOR[idx] = baseColor;
            int k = 0;
            for (int r = 0; r < CARD_ROWS; r++) {
                float v = (r + 0.5F) / CARD_ROWS;
                for (int c = 0; c < CARD_COLS; c++) {
                    float u = (c + 0.5F) / CARD_COLS;
                    cache[k++] = ShaderMath.sampleSurface(eff, baseColor, intensity, speed,
                            Math.max(0.55F, opacity), u, v, 0.0F, 1.0F, 0.0F, u, v, 0.0F, timeSeconds);
                }
            }
        }

        int innerX = x + 1;
        int innerY = y + 1;
        int innerW = Math.max(4, width - 2);
        int innerH = Math.max(4, height - 2);

        int k = 0;
        for (int r = 0; r < CARD_ROWS; r++) {
            int y0 = innerY + (r * innerH) / CARD_ROWS;
            int y1 = innerY + ((r + 1) * innerH) / CARD_ROWS;
            for (int c = 0; c < CARD_COLS; c++) {
                int x0 = innerX + (c * innerW) / CARD_COLS;
                int x1 = innerX + ((c + 1) * innerW) / CARD_COLS;
                graphics.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1),
                        ColorUtil.fade(cache[k++], uiAlpha));
            }
        }
    }

    /**
     * Живое 3D-изометрическое превью блока ({@code BlockShader}) с учётом нормалей трёх граней и рёбер.
     */
    public static void renderBlockPreview(GuiGraphics graphics, int x, int y, int width, int height,
                                          ShaderEffect effect, boolean enabled, int baseColor,
                                          float intensity, float speed, float opacity,
                                          float timeSeconds, float uiAlpha) {
        if (width <= 8 || height <= 8 || uiAlpha <= 0.01F) {
            return;
        }
        DrawUtil.fillRounded(graphics, x, y, width, height, 8, ColorUtil.fade(0xFF080C15, 0.88F * uiAlpha));

        float effAlpha = enabled ? uiAlpha : uiAlpha * 0.35F;
        float bob = 2.0F * ShaderMath.sin(timeSeconds * 1.8F);
        int cx = x + width / 2;
        int cy = y + height / 2 + Math.round(bob);
        int radius = Math.max(14, Math.min(width, height) / 3);
        int halfW = Math.round(radius * 0.92F);
        int quarterH = Math.round(radius * 0.48F);
        int sideH = Math.round(radius * 0.95F);

        // Фоновое свечение вокруг блока
        int glowCol = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, opacity, 0.5F, 0.0F, 1.0F, 0.0F, timeSeconds);
        DrawUtil.drawGlow(graphics, cx - halfW, cy - quarterH, halfW * 2, quarterH * 2 + sideH, 10,
                glowCol, (enabled ? 0.55F : 0.15F) * uiAlpha);

        // Подложка ванильного каменного блока под шейдером
        drawIsoCubeBase(graphics, cx, cy, halfW, quarterH, sideH, uiAlpha);

        // 1. Верхняя грань (+Y, нормаль 0, 1, 0)
        int steps = 5;
        for (int iu = 0; iu < steps; iu++) {
            float u0 = iu / (float) steps;
            float u1 = (iu + 1) / (float) steps;
            float uMid = (u0 + u1) * 0.5F;
            for (int iv = 0; iv < steps; iv++) {
                float v0 = iv / (float) steps;
                float v1 = (iv + 1) / (float) steps;
                float vMid = (v0 + v1) * 0.5F;

                int px = cx + Math.round((uMid - vMid) * halfW);
                int py = (cy - sideH / 2) + Math.round((uMid + vMid - 1.0F) * quarterH);
                int cellW = Math.max(2, (halfW * 2) / steps);
                int cellH = Math.max(2, (quarterH * 2) / steps + 1);
                int col = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        uMid, vMid, 0.0F, 1.0F, 0.0F, uMid, 1.0F, vMid, timeSeconds);
                graphics.fill(px - cellW / 2, py - cellH / 2, px + cellW / 2 + 1, py + cellH / 2 + 1,
                        ColorUtil.fade(col, effAlpha));
            }
        }

        // 2. Левая передняя грань (-X, нормаль -1, 0, 0) и правая передняя грань (+Z, нормаль 0, 0, 1)
        for (int colIdx = 0; colIdx < steps; colIdx++) {
            float u = (colIdx + 0.5F) / steps;
            int lx0 = cx - halfW + (colIdx * halfW) / steps;
            int lx1 = cx - halfW + ((colIdx + 1) * halfW) / steps;
            int rx0 = cx + (colIdx * halfW) / steps;
            int rx1 = cx + ((colIdx + 1) * halfW) / steps;

            int leftTopY = (cy - sideH / 2) + Math.round((u - 0.5F) * quarterH * 2.0F * 0.5F);
            int rightTopY = (cy - sideH / 2) + Math.round((0.5F - u) * quarterH * 2.0F * 0.5F);

            for (int rowIdx = 0; rowIdx < steps; rowIdx++) {
                float v = (rowIdx + 0.5F) / steps;
                int ly0 = leftTopY + (rowIdx * sideH) / steps;
                int ly1 = leftTopY + ((rowIdx + 1) * sideH) / steps;
                int ry0 = rightTopY + (rowIdx * sideH) / steps;
                int ry1 = rightTopY + ((rowIdx + 1) * sideH) / steps;

                int leftColor = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u, 1.0F - v, -1.0F, 0.0F, 0.0F, 0.0F, 1.0F - v, u, timeSeconds);
                int rightColor = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u, 1.0F - v, 0.0F, 0.0F, 1.0F, u, 1.0F - v, 1.0F, timeSeconds);

                graphics.fill(lx0, ly0, Math.max(lx0 + 1, lx1), Math.max(ly0 + 1, ly1),
                        ColorUtil.fade(leftColor, effAlpha));
                graphics.fill(rx0, ry0, Math.max(rx0 + 1, rx1), Math.max(ry0 + 1, ry1),
                        ColorUtil.fade(rightColor, effAlpha));
            }
        }

        // 3. Светящиеся изометрические рёбра куба
        int topX = cx;
        int topY = cy - sideH / 2 - quarterH;
        int midY = cy - sideH / 2 + quarterH;
        int leftX = cx - halfW;
        int leftY = cy - sideH / 2;
        int rightX = cx + halfW;
        int rightY = cy - sideH / 2;
        int botMidY = midY + sideH;
        int botLeftY = leftY + sideH;
        int botRightY = rightY + sideH;

        drawPreviewLine(graphics, topX, topY, leftX, leftY, effect, baseColor, intensity, speed, opacity, 0.1F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, topX, topY, rightX, rightY, effect, baseColor, intensity, speed, opacity, 0.3F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, leftX, leftY, cx, midY, effect, baseColor, intensity, speed, opacity, 0.5F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, rightX, rightY, cx, midY, effect, baseColor, intensity, speed, opacity, 0.7F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, leftX, leftY, leftX, botLeftY, effect, baseColor, intensity, speed, opacity, 0.2F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, cx, midY, cx, botMidY, effect, baseColor, intensity, speed, opacity, 0.6F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, rightX, rightY, rightX, botRightY, effect, baseColor, intensity, speed, opacity, 0.8F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, leftX, botLeftY, cx, botMidY, effect, baseColor, intensity, speed, opacity, 0.4F, timeSeconds, effAlpha);
        drawPreviewLine(graphics, rightX, botRightY, cx, botMidY, effect, baseColor, intensity, speed, opacity, 0.9F, timeSeconds, effAlpha);

        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, 8, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), uiAlpha), 1);
    }

    /**
     * Живое превью руки и предмета от первого лица ({@code HandsShader}).
     */
    public static void renderHandPreview(GuiGraphics graphics, int x, int y, int width, int height,
                                         ShaderEffect effect, boolean enabled, int baseColor,
                                         float intensity, float speed, float opacity,
                                         float timeSeconds, float uiAlpha) {
        if (width <= 8 || height <= 8 || uiAlpha <= 0.01F) {
            return;
        }
        DrawUtil.fillRounded(graphics, x, y, width, height, 8, ColorUtil.fade(0xFF080C15, 0.88F * uiAlpha));

        float effAlpha = enabled ? uiAlpha : uiAlpha * 0.35F;
        float swing = ShaderMath.sin(timeSeconds * 2.2F) * 3.0F;
        float bobY = ShaderMath.cos(timeSeconds * 1.6F) * 2.5F;

        int armW = Math.max(18, width / 4);
        int armH = Math.max(24, Math.round(height * 0.56F));
        int armX = x + width / 2 + Math.round(swing) - armW / 4;
        int armY = y + height - armH - 6 + Math.round(bobY);

        int itemW = Math.max(12, armW - 4);
        int itemH = Math.max(18, Math.round(height * 0.44F));
        int itemX = armX - itemW / 2 - 2;
        int itemY = armY - itemH / 2 + 4;

        int edgeCol = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, opacity, 0.5F, 1.0F, 0.5F, 0.0F, timeSeconds);
        DrawUtil.drawGlow(graphics, itemX - 2, itemY - 2, armX + armW - itemX + 4, armY + armH - itemY + 4,
                8, edgeCol, (enabled ? 0.55F : 0.15F) * uiAlpha);

        // Силуэт предмета в руке (клинок/кристалл) и предплечья от первого лица
        DrawUtil.fillRounded(graphics, itemX, itemY, itemW, itemH, 4, ColorUtil.fade(0xFF28354A, 0.92F * uiAlpha));
        DrawUtil.fillRounded(graphics, armX, armY, armW, armH, 5, ColorUtil.fade(0xFF342838, 0.95F * uiAlpha));

        // Наложение шейдерной сетки на предмет и руку
        renderShadedRectGrid(graphics, itemX, itemY, itemW, itemH, effect, baseColor, intensity, speed, opacity,
                0.6F, 0.8F, 0.0F, 1.0F, timeSeconds, 6, 8, effAlpha);
        renderShadedRectGrid(graphics, armX, armY, armW, armH, effect, baseColor, intensity, speed, opacity,
                0.8F, 0.4F, 0.4F, 2.0F, timeSeconds, 6, 8, effAlpha);

        // Светящийся контур вокруг предмета и руки
        int itemRim = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, 0.95F, 0.3F, 1.0F, 1.0F, 0.0F, timeSeconds);
        int armRim = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, 0.95F, 0.7F, 2.0F, 0.5F, 0.0F, timeSeconds);
        DrawUtil.fillRoundedBordered(graphics, itemX, itemY, itemW, itemH, 4, 0, ColorUtil.fade(itemRim, effAlpha), 1);
        DrawUtil.fillRoundedBordered(graphics, armX, armY, armW, armH, 5, 0, ColorUtil.fade(armRim, effAlpha), 1);

        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, 8, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), uiAlpha), 1);
    }

    /**
     * Экранный 2D-оверлей шейдера рук в нижней части экрана от первого лица.
     */
    public static void renderFirstPersonHandOverlay(GuiGraphics graphics, int screenWidth, int screenHeight,
                                                    ShaderEffect effect, int baseColor,
                                                    float intensity, float speed, float opacity,
                                                    float swingMain, float swingOff, float timeSeconds) {
        if (graphics == null || screenWidth <= 0 || screenHeight <= 0 || opacity <= 0.01F) {
            return;
        }
        int zoneW = Math.max(70, Math.round(screenWidth * 0.24F));
        int zoneH = Math.max(46, Math.round(screenHeight * 0.22F));
        int baseY = screenHeight - zoneH - 8;

        int leftX = Math.round(screenWidth * 0.08F);
        int rightX = screenWidth - leftX - zoneW;

        float leftLift = ShaderMath.sin((float) (Math.sqrt(Math.max(0.0F, swingOff)) * Math.PI)) * 14.0F;
        float rightLift = ShaderMath.sin((float) (Math.sqrt(Math.max(0.0F, swingMain)) * Math.PI)) * 14.0F;

        renderHandAuraZone(graphics, leftX, baseY - Math.round(leftLift), zoneW, zoneH,
                effect, baseColor, intensity, speed, opacity * 0.42F, -1.0F, timeSeconds);
        renderHandAuraZone(graphics, rightX, baseY - Math.round(rightLift), zoneW, zoneH,
                effect, baseColor, intensity, speed, opacity * 0.48F, 1.0F, timeSeconds);
    }

    private static void renderHandAuraZone(GuiGraphics graphics, int x, int y, int width, int height,
                                           ShaderEffect effect, int baseColor,
                                           float intensity, float speed, float opacity,
                                           float side, float timeSeconds) {
        int strips = 10;
        for (int i = 0; i < strips; i++) {
            float u = (i + 0.5F) / strips;
            float fadeEdge = 1.0F - Math.abs(u * 2.0F - 1.0F);
            int sx0 = x + (i * width) / strips;
            int sx1 = x + ((i + 1) * width) / strips;
            int topColor = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity * fadeEdge * 0.25F,
                    u, 0.1F, side, 0.5F, 0.5F, side, 0.1F, u, timeSeconds);
            int botColor = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity * fadeEdge,
                    u, 0.9F, side, 0.5F, 0.5F, side, 0.9F, u, timeSeconds);
            graphics.fillGradient(sx0, y, Math.max(sx0 + 1, sx1), y + height,
                    ColorUtil.withAlpha(topColor, 0), botColor);
        }
        int barColor = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, Math.min(1.0F, opacity * 1.4F),
                0.5F, side, 0.0F, 0.0F, timeSeconds);
        DrawUtil.fillRounded(graphics, x + width / 6, y + height - 3, (width * 2) / 3, 2, 1, barColor);
    }

    private static void renderShadedRectGrid(GuiGraphics graphics, int x, int y, int width, int height,
                                             ShaderEffect effect, int baseColor,
                                             float intensity, float speed, float opacity,
                                             float nx, float ny, float nz, float worldSeed,
                                             float timeSeconds, int cols, int rows, float uiAlpha) {
        for (int r = 0; r < rows; r++) {
            int y0 = y + (r * height) / rows;
            int y1 = y + ((r + 1) * height) / rows;
            float v = (r + 0.5F) / rows;
            for (int c = 0; c < cols; c++) {
                int x0 = x + (c * width) / cols;
                int x1 = x + ((c + 1) * width) / cols;
                float u = (c + 0.5F) / cols;
                int col = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u, v, nx, ny, nz, worldSeed + u, v, 0.5F, timeSeconds);
                graphics.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), ColorUtil.fade(col, uiAlpha));
            }
        }
    }

    private static void drawIsoCubeBase(GuiGraphics graphics, int cx, int cy, int halfW, int quarterH, int sideH, float uiAlpha) {
        int topY = cy - sideH / 2;
        DrawUtil.fillRounded(graphics, cx - halfW, topY - quarterH / 2, halfW * 2, sideH + quarterH, 6,
                ColorUtil.fade(0xFF1E2636, 0.85F * uiAlpha));
    }

    private static void drawPreviewLine(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                                        ShaderEffect effect, int baseColor,
                                        float intensity, float speed, float opacity,
                                        float seedPhase, float timeSeconds, float uiAlpha) {
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            float t = i / (float) (steps - 1);
            int px = Math.round(x0 + (x1 - x0) * t);
            int py = Math.round(y0 + (y1 - y0) * t);
            int col = ShaderMath.sampleEdge(effect, baseColor, intensity, speed, Math.max(0.75F, opacity),
                    t, seedPhase, t, 0.0F, timeSeconds);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, ColorUtil.fade(col, uiAlpha));
        }
    }
}
