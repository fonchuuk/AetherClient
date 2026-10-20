package com.aetherglass.client.gui.render;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
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

    public static final Style STYLE_MODERN = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("aetherglass", "modern")));
    public static final Style STYLE_MODERN_BOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("aetherglass", "modern_bold")));
    public static final Style STYLE_MODERN_SMALL = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("aetherglass", "modern_small")));

    private static final int NINE_TEX_SIZE = 256;
    private static final int NINE_CORNER_TEX = 64;
    private static final int NINE_CENTER_TEX = NINE_TEX_SIZE - NINE_CORNER_TEX * 2; // 128

    /** Символы шифра при появлении текста (*!&#^!@^&#!@#(&!). */
    private static final char[] CIPHER_CHARS = "*!&#^!@^&#!@#(&!$%?+=~".toCharArray();
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> SEEN_TEXT_NANOS =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile long scrambleStartNanos = System.nanoTime();
    private static volatile float scrambleDurationSec = 0.72F;
    private static volatile float scramblePhaseOverride = -1.0F;

    private DrawUtil() {
    }

    /**
     * Запускает глобальную анимацию расшифровки текста (*!&#^!@^&#!@#(&! -> обычные буквы)
     * при открытии экрана, вкладки или модального окна.
     */
    public static void triggerTextScramble() {
        triggerTextScramble(0.72F);
    }

    public static void triggerTextScramble(float durationSeconds) {
        scrambleDurationSec = Math.max(0.18F, Math.min(2.5F, durationSeconds));
        SEEN_TEXT_NANOS.clear();
        scrambleStartNanos = System.nanoTime();
    }

    public static void setScramblePhaseOverride(float phase) {
        scramblePhaseOverride = phase;
    }

    private static boolean isDynamicCounter(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                return true;
            }
        }
        return false;
    }

    /**
     * Преобразует строку в процессе анимации открытия: сначала символы выглядят как
     * {@code *!&#^!@^&#!@#(&!}, а затем слева направо проявляются в нормальные буквы.
     */
    public static String scrambleText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        long now = System.nanoTime();
        float progress;
        if (scramblePhaseOverride >= 0.0F) {
            progress = scramblePhaseOverride;
        } else {
            long start = scrambleStartNanos;
            float globalElapsed = (now - start) * 1.0e-9F;
            long stringStart = start;
            if (!isDynamicCounter(text) && SEEN_TEXT_NANOS.size() < 1024) {
                Long prev = SEEN_TEXT_NANOS.putIfAbsent(text, now);
                if (prev == null) {
                    stringStart = now;
                } else {
                    stringStart = Math.max(start, prev);
                }
            }
            float elapsed = (now - stringStart) * 1.0e-9F;
            float dur = (stringStart > start + 150_000_000L && globalElapsed > 0.15F)
                    ? 0.48F
                    : Math.max(0.18F, scrambleDurationSec);
            progress = elapsed / dur;
        }
        if (progress >= 1.0F) {
            return text;
        }
        progress = Math.max(0.0F, progress);
        int len = text.length();
        char[] out = new char[len];
        int strHash = text.hashCode();
        int tickStep = (int) ((now - scrambleStartNanos) / 38_000_000L);

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                out[i] = c;
                continue;
            }
            float charOrder = len <= 1 ? 0.5F : (float) i / (float) (len - 1);
            int charMix = (strHash * 31 + i * 17) & 0x7FFFFFFF;
            float jitter = (charMix % 100) / 100.0F;
            // Первые ~18% времени все символы зашифрованы (*!&#^!@^&#!@#(&!), затем плавно проявляются
            float revealAt = 0.16F + charOrder * 0.62F + jitter * 0.20F;
            if (progress >= revealAt) {
                out[i] = c;
            } else {
                int glyphIdx = (charMix + tickStep * 7 + i * 13) & 0x7FFFFFFF;
                out[i] = CIPHER_CHARS[glyphIdx % CIPHER_CHARS.length];
            }
        }
        return new String(out);
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
     * Матовая чёрная прямоугольная карточка со скруглёнными углами (10–12px) и неоновой обводкой с эффектом свечения (glow).
     *
     * <ul>
     *   <li>Фон карточки всегда матовый чёрный ({@code #0B0C10}) с лёгкой прозрачностью — никаких перекрашиваний самой карточки.</li>
     *   <li>Когда модуль ВЫКЛЮЧЕН ({@code neonGlow <= 0.015F}): обводки нет вообще (или еле заметная серая без свечения).</li>
     *   <li>Когда модуль ВКЛЮЧЕН ({@code neonGlow > 0.015F}): вокруг периметра загорается неоновая обводка с мягким внешним свечением (glow).</li>
     * </ul>
     */
    public static void drawMatteNeonCard(GuiGraphics graphics, int x, int y, int width, int height,
                                         int radius, float alpha, float neonGlow, int neonColor) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        float a = Math.max(0.0F, Math.min(1.0F, alpha));
        float glow = Math.max(0.0F, Math.min(1.0F, neonGlow));
        int r = limit(Math.max(3, Math.min(8, radius)), width, height);
        float cfgGlow = AuroraTheme.borderGlow();
        float cfgWidth = AuroraTheme.borderWidth();

        // 1. Деликатное внешнее свечение (регулируется «Свечение рамки» в модуле Оформление)
        if (glow > 0.015F && cfgGlow > 0.01F) {
            int haloAlpha = Math.round(95 * a * glow * cfgGlow);
            if (haloAlpha > 2) {
                drawNineSlice(graphics, TEX_NINE_SHADOW, x - 2, y - 2, width + 4, height + 4,
                        Math.min(16, r + 2), NINE_CORNER_TEX, NINE_TEX_SIZE,
                        ColorUtil.withAlpha(neonColor, haloAlpha));
            }
        }

        // 2. Фон: строгий матовый прямоугольник со скруглёнными углами (#0B0C10)
        int matteBlack = ColorUtil.withAlpha(0xFF0B0C10, Math.round(234 * a));
        fillRoundedPixelExact(graphics, x, y, width, height, r, matteBlack);

        // 3. Тонкая чёткая рамка по периметру (регулируется по цвету и жирности в «Оформление»)
        if (glow > 0.015F) {
            float thicknessAlphaScale = cfgWidth < 1.0F ? (0.45F + 0.55F * cfgWidth) : 1.0F;
            int borderAlpha = Math.round(235 * a * glow * thicknessAlphaScale);
            int neonBorder = ColorUtil.withAlpha(neonColor, borderAlpha);
            int pxThickness = Math.max(1, Math.round(cfgWidth));
            drawThinRoundedOutline(graphics, x, y, width, height, r, neonBorder, pxThickness);
        } else {
            int subtleGrey = ColorUtil.withAlpha(0xFFFFFFFF, Math.round(22 * a));
            drawThinRoundedOutline(graphics, x, y, width, height, r, subtleGrey, 1);
        }
    }

    /**
     * Заполняет прямоугольник со скруглёнными углами с субпиксельной точностью (0.5 GUI px = 1 физ. пиксель при guiScale=2).
     */
    public static void fillRoundedPixelExact(GuiGraphics graphics, int x, int y, int width, int height,
                                             int radius, int color) {
        if (graphics == null || width <= 0 || height <= 0 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        int sx = x * 2;
        int sy = y * 2;
        int sw = width * 2;
        int sh = height * 2;
        int sr = Math.max(0, Math.min(radius * 2, Math.min(sw / 2, sh / 2)));

        graphics.pose().pushMatrix();
        graphics.pose().scale(0.5F, 0.5F);
        if (sr <= 1) {
            graphics.fill(sx, sy, sx + sw, sy + sh, color);
        } else {
            graphics.fill(sx, sy + sr, sx + sw, sy + sh - sr, color);
            for (int ry = 0; ry < sr; ry++) {
                int inset = rowInset(ry, sh, sr);
                if (sw - inset * 2 > 0) {
                    graphics.fill(sx + inset, sy + ry, sx + sw - inset, sy + ry + 1, color);
                    int botY = sy + sh - 1 - ry;
                    graphics.fill(sx + inset, botY, sx + sw - inset, botY + 1, color);
                }
            }
        }
        graphics.pose().popMatrix();
    }

    /**
     * Отрисовывает ультра-тонкую чёткую рамку прямоугольника со скруглёнными углами
     * с разрешением в 1 физический пиксель (0.5 GUI px).
     */
    public static void drawThinRoundedOutline(GuiGraphics graphics, int x, int y, int width, int height,
                                              int radius, int color, int thickness) {
        if (graphics == null || width <= 2 || height <= 2 || ColorUtil.alpha(color) <= 0) {
            return;
        }
        int sx = x * 2;
        int sy = y * 2;
        int sw = width * 2;
        int sh = height * 2;
        int sr = Math.max(0, Math.min(radius * 2, Math.min(sw / 2, sh / 2)));
        int bw = Math.max(1, Math.min(6, thickness));

        graphics.pose().pushMatrix();
        graphics.pose().scale(0.5F, 0.5F);
        if (sr <= 1) {
            graphics.fill(sx, sy, sx + sw, sy + bw, color);
            graphics.fill(sx, sy + sh - bw, sx + sw, sy + sh, color);
            graphics.fill(sx, sy + bw, sx + bw, sy + sh - bw, color);
            graphics.fill(sx + sw - bw, sy + bw, sx + sw, sy + sh - bw, color);
        } else {
            // Прямые участки сторон (верх, низ, лево, право) толщиной ровно bw физ. пикселей
            graphics.fill(sx + sr, sy, sx + sw - sr, sy + bw, color);
            graphics.fill(sx + sr, sy + sh - bw, sx + sw - sr, sy + sh, color);
            graphics.fill(sx, sy + sr, sx + bw, sy + sh - sr, color);
            graphics.fill(sx + sw - bw, sy + sr, sx + sw, sy + sh - sr, color);

            // Гладкие субпиксельные дуги скруглённых углов
            for (int ry = 0; ry < sr; ry++) {
                int outerInset = rowInset(ry, sh, sr);
                int spanEnd;
                if (ry < bw) {
                    spanEnd = sr;
                } else {
                    int prevOuter = rowInset(ry - bw, sh, sr);
                    spanEnd = Math.max(outerInset + bw, prevOuter);
                }
                int topY = sy + ry;
                int botY = sy + sh - 1 - ry;
                graphics.fill(sx + outerInset, topY, sx + spanEnd, topY + 1, color);
                graphics.fill(sx + sw - spanEnd, topY, sx + sw - outerInset, topY + 1, color);
                graphics.fill(sx + outerInset, botY, sx + spanEnd, botY + 1, color);
                graphics.fill(sx + sw - spanEnd, botY, sx + sw - outerInset, botY + 1, color);
            }
        }
        graphics.pose().popMatrix();
    }

    /**
     * Прямоугольная матовая чёрная плашка/кнопка с неоновым контуром при наведении или активности.
     */
    public static void drawFrostedPill(GuiGraphics graphics, int x, int y, int width, int height,
                                       int radius, float alpha, float hoverAmount, float activeAmount) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(Math.min(8, radius), width, height);
        float hover = Math.max(0.0F, Math.min(1.0F, hoverAmount));
        float active = Math.max(0.0F, Math.min(1.0F, activeAmount));
        float glow = Math.max(active, hover * 0.72F);
        drawMatteNeonCard(graphics, x, y, width, height, r, alpha, glow, AuroraTheme.accent());
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
     * Матовое чёрное окно контейнера / столбика: прямоугольник со скруглёнными углами и тонкой рамкой.
     */
    public static void drawLiquidGlassPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                            int radius, float alpha) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(Math.min(8, radius), width, height);
        int panelFill = ColorUtil.withAlpha(0xFF08090D, Math.round(236 * alpha));
        fillRoundedPixelExact(graphics, x, y, width, height, r, panelFill);
        int pxThickness = Math.max(1, Math.round(AuroraTheme.borderWidth()));
        int rim = ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(78 * alpha));
        drawThinRoundedOutline(graphics, x, y, width, height, r, rim, pxThickness);
    }

    /**
     * Матовая чёрная карточка с неоновой обводкой при активности.
     */
    public static void drawLiquidGlassCard(GuiGraphics graphics, int x, int y, int width, int height,
                                           int radius, float alpha, boolean hoveredOrActive) {
        if (graphics == null || width <= 0 || height <= 0 || alpha <= 0.01F) {
            return;
        }
        int r = limit(Math.min(11, radius), width, height);
        drawMatteNeonCard(graphics, x, y, width, height, r, alpha, hoveredOrActive ? 0.90F : 0.0F, AuroraTheme.accent());
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
        String shown = scrambleText(text);
        int haloAlpha = Math.max(6, Math.round(alpha * 0.14F));
        int halo = ColorUtil.withAlpha(glowColor, haloAlpha);
        graphics.drawString(font, shown, x - 1, y, halo, false);
        graphics.drawString(font, shown, x + 1, y, halo, false);
        graphics.drawString(font, shown, x, y, color, true);
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
        String raw = text.getString();
        String shown = scrambleText(raw);
        Component comp = shown.equals(raw) ? text : Component.literal(shown).withStyle(text.getStyle());
        int haloAlpha = Math.max(6, Math.round(alpha * 0.14F));
        int halo = ColorUtil.withAlpha(glowColor, haloAlpha);
        graphics.drawString(font, comp, x - 1, y, halo, false);
        graphics.drawString(font, comp, x + 1, y, halo, false);
        graphics.drawString(font, comp, x, y, color, true);
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

    public static Component modernComponent(String text, Style style) {
        return Component.literal(text == null ? "" : text).withStyle(style);
    }

    public static int modernWidth(Font font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        return font.width(modernComponent(text, STYLE_MODERN));
    }

    public static int modernBoldWidth(Font font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        return font.width(modernComponent(text, STYLE_MODERN_BOLD));
    }

    public static int modernSmallWidth(Font font, String text) {
        if (font == null || text == null || text.isEmpty()) {
            return 0;
        }
        return font.width(modernComponent(text, STYLE_MODERN_SMALL));
    }

    public static void drawModern(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty() || ColorUtil.alpha(color) <= 0) {
            return;
        }
        graphics.drawString(font, modernComponent(scrambleText(text), STYLE_MODERN), x, y, color, false);
    }

    public static void drawModernBold(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty() || ColorUtil.alpha(color) <= 0) {
            return;
        }
        graphics.drawString(font, modernComponent(scrambleText(text), STYLE_MODERN_BOLD), x, y, color, false);
    }

    public static void drawModernSmall(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        if (text == null || text.isEmpty() || ColorUtil.alpha(color) <= 0) {
            return;
        }
        graphics.drawString(font, modernComponent(scrambleText(text), STYLE_MODERN_SMALL), x, y, color, false);
    }

    public static void drawTextScaled(GuiGraphics graphics, Font font, String text, int x, int y,
                                      int color, float scale, boolean shadow) {
        if (text == null || text.isEmpty() || ColorUtil.alpha(color) <= 0) {
            return;
        }
        Style style = scale >= 0.88F ? STYLE_MODERN_BOLD : (scale <= 0.78F ? STYLE_MODERN_SMALL : STYLE_MODERN);
        Component comp = modernComponent(scrambleText(text), style);
        graphics.drawString(font, comp, x, y, color, false);
    }

    public static void drawCenteredTextScaled(GuiGraphics graphics, Font font, String text, int centerX, int y,
                                              int color, float scale, boolean shadow) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Style style = scale >= 0.88F ? STYLE_MODERN_BOLD : (scale <= 0.78F ? STYLE_MODERN_SMALL : STYLE_MODERN);
        int width = font.width(modernComponent(text, style));
        Component comp = modernComponent(scrambleText(text), style);
        graphics.drawString(font, comp, centerX - width / 2, y, color, false);
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
