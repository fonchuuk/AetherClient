package com.aetherglass.client.render.shader;

import com.aetherglass.client.render.util.ColorUtil;

/**
 * Математическое ядро 16 процедурных шейдер-эффектов.
 *
 * <p>Не выделяет объектов в куче (zero-allocation), полностью детерминировано
 * от координат поверхности {@code (u, v)}, нормали грани {@code (nx, ny, nz)},
 * мировых координат {@code (wx, wy, wz)} и времени в секундах {@code timeSeconds}.
 * Благодаря этому оно одинаково быстро работает в 3D-мире (на гранях и рёбрах
 * реального {@code VoxelShape} блока и модели руки/предмета), в 2D-оверлее
 * и в живых превью меню эффектов.</p>
 */
public final class ShaderMath {
    private static final float TAU = (float) (Math.PI * 2.0);

    private ShaderMath() {
    }

    /**
     * Вычисляет цвет точки поверхности для выбранного шейдер-эффекта.
     *
     * @param effect      один из 16 шейдер-эффектов
     * @param baseColor   основной цвет, выбранный пользователем (ARGB)
     * @param intensity   интенсивность (0.1..2.0)
     * @param speed       скорость анимации (0.1..3.0)
     * @param opacity     непрозрачность (0.05..1.0)
     * @param u           локальная координата U на грани (0..1)
     * @param v           локальная координата V на грани (0..1)
     * @param nx          компонент X нормали грани (-1..1)
     * @param ny          компонент Y нормали грани (-1..1)
     * @param nz          компонент Z нормали грани (-1..1)
     * @param wx          мировая/локальная координата X
     * @param wy          мировая/локальная координата Y
     * @param wz          мировая/локальная координата Z
     * @param timeSeconds время в секундах
     * @return итоговый ARGB-цвет точки поверхности
     */
    public static int sampleSurface(ShaderEffect effect, int baseColor,
                                    float intensity, float speed, float opacity,
                                    float u, float v,
                                    float nx, float ny, float nz,
                                    float wx, float wy, float wz,
                                    float timeSeconds) {
        if (effect == null) {
            effect = ShaderEffect.LIQUID_GLASS;
        }
        float safeU = clamp01(finite(u, 0.5F));
        float safeV = clamp01(finite(v, 0.5F));
        float safeIntensity = clamp(finite(intensity, 1.0F), 0.1F, 2.5F);
        float safeSpeed = clamp(finite(speed, 1.0F), 0.05F, 4.0F);
        float safeOpacity = clamp(finite(opacity, 0.78F), 0.02F, 1.0F);
        float t = finite(timeSeconds, 0.0F) * safeSpeed;

        // Учитываем ориентацию грани по нормали: верхняя грань светлее, нижняя глубже.
        float normalShade = 0.88F + 0.12F * clamp(ny * 0.65F + nx * 0.25F - nz * 0.20F, -1.0F, 1.0F);
        float edgeDist = Math.min(Math.min(safeU, 1.0F - safeU), Math.min(safeV, 1.0F - safeV));

        return switch (effect) {
            case LIQUID_GLASS -> evalLiquidGlass(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case RAINBOW_FLOW -> evalRainbowFlow(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case NEON_PULSE -> evalNeonPulse(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case ENERGY_WAVES -> evalEnergyWaves(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case PLASMA -> evalPlasma(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case ELECTRIC_ARCS -> evalElectricArcs(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case HOLOGRAM -> evalHologram(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case CYBER_GRID -> evalCyberGrid(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case LIQUID_METAL -> evalLiquidMetal(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, nx, ny, nz, wx, wy, wz, t);
            case AURORA -> evalAurora(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case GLITCH -> evalGlitch(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case FIRE_CORE -> evalFireCore(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case ICE_CRYSTAL -> evalIceCrystal(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case SPECTRAL_OUTLINE -> evalSpectralOutline(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case COSMIC_DUST -> evalCosmicDust(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
            case VOID_DISTORTION -> evalVoidDistortion(baseColor, safeIntensity, safeOpacity, safeU, safeV, edgeDist, normalShade, wx, wy, wz, t);
        };
    }

    /**
     * Вычисляет цвет точки на ребре (контуре) блока или руки.
     *
     * @param edgeT положение вдоль ребра (0..1)
     */
    public static int sampleEdge(ShaderEffect effect, int baseColor,
                                 float intensity, float speed, float opacity,
                                 float edgeT, float wx, float wy, float wz,
                                 float timeSeconds) {
        if (effect == null) {
            effect = ShaderEffect.LIQUID_GLASS;
        }
        float p = clamp01(finite(edgeT, 0.5F));
        float safeIntensity = clamp(finite(intensity, 1.0F), 0.1F, 2.5F);
        float safeSpeed = clamp(finite(speed, 1.0F), 0.05F, 4.0F);
        float safeOpacity = clamp(finite(opacity, 0.85F), 0.05F, 1.0F);
        float t = finite(timeSeconds, 0.0F) * safeSpeed;
        float worldPhase = (wx * 0.73F + wy * 1.13F + wz * 0.91F);

        int surfaceSample = sampleSurface(effect, baseColor, safeIntensity, safeSpeed, safeOpacity,
                p, 0.02F, 0.0F, 1.0F, 0.0F, wx, wy, wz, timeSeconds);

        return switch (effect) {
            case RAINBOW_FLOW, SPECTRAL_OUTLINE -> {
                float hue = fract(p * 0.85F + worldPhase * 0.25F - t * 0.42F);
                int rainbow = ColorUtil.hsb(hue, 0.82F, 1.0F);
                float pulse = 0.72F + 0.28F * sin(TAU * (p * 2.0F - t * 1.1F + worldPhase));
                int mixed = ColorUtil.lerp(rainbow, 0xFFFFFFFF, clamp01((safeIntensity - 0.8F) * 0.35F * pulse));
                yield ColorUtil.withAlpha(mixed, Math.round(255.0F * clamp01(safeOpacity * (0.78F + 0.22F * pulse))));
            }
            case LIQUID_GLASS -> {
                float iridHue = fract(ColorUtil.hueOf(baseColor) + 0.18F * sin(TAU * (p + t * 0.35F + worldPhase)));
                int irid = ColorUtil.hsb(iridHue, 0.45F, 1.0F);
                float glint = pow4(0.5F + 0.5F * sin(TAU * (p * 1.5F - t * 0.65F + worldPhase)));
                int rim = ColorUtil.lerp(ColorUtil.brighten(baseColor, 0.35F), irid, 0.5F);
                rim = ColorUtil.lerp(rim, 0xFFFFFFFF, glint * 0.65F);
                yield ColorUtil.withAlpha(rim, Math.round(255.0F * clamp01(safeOpacity * 0.95F)));
            }
            case NEON_PULSE -> {
                float beat = 0.55F + 0.45F * sin(t * 4.2F + worldPhase * 0.5F);
                int neon = ColorUtil.lerp(baseColor, 0xFFFFFFFF, clamp01(0.30F + 0.40F * beat * safeIntensity * 0.6F));
                yield ColorUtil.withAlpha(neon, Math.round(255.0F * clamp01(safeOpacity * (0.75F + 0.25F * beat))));
            }
            case ELECTRIC_ARCS -> {
                float spark = hash13(p * 18.0F, Math.floor(t * 16.0F), worldPhase);
                int arcColor = spark > 0.55F ? 0xFFFFFFFF : ColorUtil.brighten(baseColor, 0.42F);
                yield ColorUtil.withAlpha(arcColor, Math.round(255.0F * clamp01(safeOpacity * (0.78F + 0.22F * spark))));
            }
            case GLITCH -> {
                float slot = (float) Math.floor(t * 12.0F + p * 6.0F);
                float h = hash12(slot, worldPhase);
                int glitchCol = h < 0.33F ? 0xFFFF2A6D : (h < 0.66F ? 0xFF05D9E8 : baseColor);
                yield ColorUtil.withAlpha(glitchCol, Math.round(255.0F * clamp01(safeOpacity * (h > 0.15F ? 0.95F : 0.35F))));
            }
            default -> {
                int bright = ColorUtil.brighten(surfaceSample, 0.28F * safeIntensity);
                int alpha = Math.max(ColorUtil.alpha(surfaceSample), Math.round(235.0F * safeOpacity));
                yield ColorUtil.withAlpha(bright, clamp255(alpha));
            }
        };
    }

    // ─────────────────────────── 01. LIQUID GLASS ───────────────────────────

    private static int evalLiquidGlass(int baseColor, float intensity, float opacity,
                                       float u, float v, float edgeDist, float normalShade,
                                       float wx, float wy, float wz, float t) {
        // Волновое преломление (рефракция) и каустика жидкого стекла
        float wave1 = sin((u * 4.2F + v * 3.1F + wx * 0.5F) + t * 1.6F);
        float wave2 = cos((u * 3.5F - v * 4.8F + wz * 0.5F) - t * 1.25F);
        float caustic = 0.5F + 0.5F * sin((u - v) * 6.0F + wave1 * 1.4F + wave2 * 1.2F + t * 1.9F);
        float specularBand = pow4(clamp01(1.0F - Math.abs((u * 0.65F + v * 0.75F) - (0.5F + 0.28F * sin(t * 0.85F))) * 2.6F));

        // Радужная дисперсия у кромки стекла (хроматический блик)
        float baseHue = ColorUtil.hueOf(baseColor);
        float dispersionHue = fract(baseHue + 0.22F * (wave1 * 0.5F + (0.5F - edgeDist)));
        int prismColor = ColorUtil.hsb(dispersionHue, 0.52F, 1.0F);

        float rim = clamp01(1.0F - edgeDist * 4.5F);
        int glassBody = ColorUtil.lerp(baseColor, prismColor, 0.38F * caustic);
        glassBody = ColorUtil.lerp(glassBody, 0xFFF4FBFF, clamp01((specularBand * 0.65F + rim * 0.45F) * intensity));
        glassBody = scaleBrightness(glassBody, normalShade);

        float alphaFactor = clamp01(opacity * (0.42F + 0.36F * rim + 0.28F * specularBand + 0.14F * caustic) * clamp(intensity, 0.4F, 1.5F));
        return ColorUtil.withAlpha(glassBody, Math.round(255.0F * alphaFactor));
    }

    // ─────────────────────────── 02. RAINBOW FLOW ───────────────────────────

    private static int evalRainbowFlow(int baseColor, float intensity, float opacity,
                                       float u, float v, float edgeDist, float normalShade,
                                       float wx, float wy, float wz, float t) {
        float diag = (u * 0.85F + v * 0.65F) + (wx + wy + wz) * 0.18F;
        float ripple = 0.08F * sin(TAU * (u * 2.0F - v * 2.0F) + t * 1.8F);
        float hue = fract(diag + ripple - t * 0.38F);
        float sat = clamp01(0.76F + 0.16F * sin(t * 1.5F + u * 4.0F));
        float val = clamp01((0.88F + 0.12F * normalShade) * clamp(intensity, 0.5F, 1.35F));
        int spectrum = ColorUtil.hsb(hue, sat, val);

        // Мягкое подмешивание базового оттенка и яркий гребень волны
        float crest = pow4(0.5F + 0.5F * sin(TAU * (diag * 1.5F - t * 0.65F)));
        int finalColor = ColorUtil.lerp(spectrum, 0xFFFFFFFF, crest * 0.32F * clamp01(intensity));
        float rim = clamp01(1.0F - edgeDist * 5.0F);
        float alpha = clamp01(opacity * (0.58F + 0.24F * crest + 0.22F * rim));
        return ColorUtil.withAlpha(finalColor, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 03. NEON PULSE ───────────────────────────

    private static int evalNeonPulse(int baseColor, float intensity, float opacity,
                                     float u, float v, float edgeDist, float normalShade,
                                     float wx, float wy, float wz, float t) {
        // Двухфазный неоновый пульс (эффект дыхания + вторичный резонанс)
        float pulse = 0.55F + 0.45F * (0.7F * sin(t * 3.8F) + 0.3F * sin(t * 7.6F + (wx + wy + wz)));
        float edgeGlow = (float) Math.exp(-edgeDist * 7.5F);
        float cornerGlow = (float) Math.exp(-Math.hypot(Math.min(u, 1.0F - u), Math.min(v, 1.0F - v)) * 6.0F);

        int darkCore = ColorUtil.darken(baseColor, 0.62F);
        int neonEdge = ColorUtil.brighten(baseColor, 0.35F * intensity);
        int hotCore = ColorUtil.lerp(neonEdge, 0xFFFFFFFF, clamp01(edgeGlow * 0.68F * pulse * intensity));

        int color = ColorUtil.lerp(darkCore, hotCore, clamp01(edgeGlow * 0.85F + cornerGlow * 0.35F + 0.18F * pulse));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.28F + 0.62F * edgeGlow + 0.22F * pulse) * clamp(intensity, 0.5F, 1.5F));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 04. ENERGY WAVES ───────────────────────────

    private static int evalEnergyWaves(int baseColor, float intensity, float opacity,
                                       float u, float v, float edgeDist, float normalShade,
                                       float wx, float wy, float wz, float t) {
        float cx = u - 0.5F;
        float cy = v - 0.5F;
        float radial = (float) Math.sqrt(cx * cx + cy * cy);
        // Расходящиеся концентрические + продольные энергетические волны
        float waveRing = 0.5F + 0.5F * sin(radial * 22.0F - t * 5.2F);
        float waveSweep = 0.5F + 0.5F * sin((u * 5.0F + v * 7.0F + wy * 2.0F) - t * 4.0F);
        float energy = pow3(waveRing) * 0.65F + pow3(waveSweep) * 0.45F;

        int secondary = ColorUtil.hsb(fract(ColorUtil.hueOf(baseColor) + 0.14F), 0.85F, 1.0F);
        int trough = ColorUtil.darken(baseColor, 0.55F);
        int crest = ColorUtil.lerp(baseColor, secondary, waveSweep);
        crest = ColorUtil.lerp(crest, 0xFFFFFFFF, clamp01((energy - 0.45F) * 0.85F * intensity));

        int color = ColorUtil.lerp(trough, crest, clamp01(energy * intensity));
        color = scaleBrightness(color, normalShade);
        float rim = clamp01(1.0F - edgeDist * 5.5F);
        float alpha = clamp01(opacity * (0.34F + 0.54F * energy + 0.20F * rim));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 05. PLASMA ───────────────────────────

    private static int evalPlasma(int baseColor, float intensity, float opacity,
                                  float u, float v, float edgeDist, float normalShade,
                                  float wx, float wy, float wz, float t) {
        float su = u * 4.5F + wx * 0.35F;
        float sv = v * 4.5F + wz * 0.35F;
        float p1 = sin(su + t * 1.7F);
        float p2 = sin(sv - t * 1.35F);
        float p3 = sin((su + sv) * 0.85F + t * 1.15F);
        float dx = su + 0.5F * sin(t * 0.7F);
        float dy = sv + 0.5F * cos(t * 0.9F);
        float p4 = sin((float) Math.sqrt(dx * dx + dy * dy + 1.0F) * 2.2F - t * 2.1F);
        float plasma = (p1 + p2 + p3 + p4) * 0.25F; // -1..1
        float norm = 0.5F + 0.5F * plasma;

        float baseHue = ColorUtil.hueOf(baseColor);
        int stopA = baseColor;
        int stopB = ColorUtil.hsb(fract(baseHue + 0.22F), 0.88F, 0.98F);
        int stopC = ColorUtil.hsb(fract(baseHue + 0.52F), 0.82F, 1.00F);

        int color = norm < 0.5F
                ? ColorUtil.lerp(stopA, stopB, norm * 2.0F)
                : ColorUtil.lerp(stopB, stopC, (norm - 0.5F) * 2.0F);
        float hot = pow4(norm);
        color = ColorUtil.lerp(color, 0xFFFFFFFF, hot * 0.35F * clamp01(intensity));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.56F + 0.32F * norm + 0.12F * (1.0F - edgeDist * 3.0F)));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 06. ELECTRIC ARCS ───────────────────────────

    private static int evalElectricArcs(int baseColor, float intensity, float opacity,
                                        float u, float v, float edgeDist, float normalShade,
                                        float wx, float wy, float wz, float t) {
        // Две пересекающиеся траектории электрического разряда на грани
        float timeStep = (float) Math.floor(t * 14.0F);
        float jitter1 = (fbm(u * 9.0F + timeStep * 1.7F, v * 3.0F + wx) - 0.5F) * 0.28F;
        float arcPath1 = Math.abs((v - (0.25F + 0.5F * u + jitter1)));
        float jitter2 = (fbm(v * 9.0F - timeStep * 2.1F, u * 3.0F + wz) - 0.5F) * 0.28F;
        float arcPath2 = Math.abs((u - (0.78F - 0.55F * v + jitter2)));

        float boltCore = Math.max(
                (float) Math.exp(-arcPath1 * 38.0F),
                (float) Math.exp(-arcPath2 * 42.0F)
        );
        float boltHalo = Math.max(
                (float) Math.exp(-arcPath1 * 10.0F),
                (float) Math.exp(-arcPath2 * 11.0F)
        );
        float edgeCharge = (float) Math.exp(-edgeDist * 9.0F);

        int darkField = ColorUtil.darken(baseColor, 0.68F);
        int electricGlow = ColorUtil.brighten(baseColor, 0.32F);
        int color = ColorUtil.lerp(darkField, electricGlow, clamp01((boltHalo * 0.75F + edgeCharge * 0.55F) * intensity));
        color = ColorUtil.lerp(color, 0xFFFFFFFF, clamp01(boltCore * 0.95F * intensity));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.26F + 0.50F * boltHalo + 0.65F * boltCore + 0.35F * edgeCharge));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 07. HOLOGRAM ───────────────────────────

    private static int evalHologram(int baseColor, float intensity, float opacity,
                                    float u, float v, float edgeDist, float normalShade,
                                    float wx, float wy, float wz, float t) {
        // Горизонтальные scanline-полосы + хроматический сдвиг + голографическое мерцание
        float scanCoord = (v + wy * 0.5F) * 28.0F - t * 6.5F;
        float scanline = 0.5F + 0.5F * sin(scanCoord * TAU);
        float wideBand = pow3(0.5F + 0.5F * sin(((v + wy) * 2.5F - t * 1.4F) * TAU));
        float flicker = 0.86F + 0.14F * sin(t * 23.0F) * cos(t * 17.0F);

        // Хроматический сдвиг к краям
        float shift = 0.5F + 0.5F * sin((u * 8.0F + t * 3.0F));
        int chromaColor = ColorUtil.hsb(fract(ColorUtil.hueOf(baseColor) + 0.08F * (shift - 0.5F)), 0.85F, 1.0F);
        int holoColor = ColorUtil.lerp(baseColor, chromaColor, 0.45F);
        holoColor = ColorUtil.lerp(holoColor, 0xFFD8FFFF, wideBand * 0.45F * clamp01(intensity));

        float rim = clamp01(1.0F - edgeDist * 6.0F);
        float scanMask = 0.32F + 0.52F * scanline + 0.32F * wideBand + 0.35F * rim;
        float alpha = clamp01(opacity * scanMask * flicker * clamp(intensity, 0.5F, 1.5F));
        return ColorUtil.withAlpha(scaleBrightness(holoColor, normalShade), Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 08. CYBER GRID ───────────────────────────

    private static int evalCyberGrid(int baseColor, float intensity, float opacity,
                                     float u, float v, float edgeDist, float normalShade,
                                     float wx, float wy, float wz, float t) {
        float cells = 6.0F;
        float gu = fract(u * cells);
        float gv = fract(v * cells);
        float distU = Math.min(gu, 1.0F - gu);
        float distV = Math.min(gv, 1.0F - gv);

        float lineMask = Math.max(
                clamp01(1.0F - distU * 14.0F),
                clamp01(1.0F - distV * 14.0F)
        );
        // Пульсирующие узлы на пересечениях сетки + бегущий импульс данных
        float nodeDist = (float) Math.hypot(distU, distV);
        float cellId = (float) (Math.floor(u * cells) * 7.0 + Math.floor(v * cells) * 13.0);
        float nodePulse = 0.5F + 0.5F * sin(t * 4.5F + cellId);
        float nodeMask = clamp01(1.0F - nodeDist * 7.5F) * (0.55F + 0.45F * nodePulse);
        float dataSweep = pow4(0.5F + 0.5F * sin((u + v) * 4.0F - t * 2.8F));

        int substrate = ColorUtil.darken(baseColor, 0.74F);
        int gridColor = ColorUtil.brighten(baseColor, 0.25F * intensity);
        int nodeColor = ColorUtil.lerp(gridColor, 0xFFFFFFFF, 0.65F * nodePulse);

        int color = ColorUtil.lerp(substrate, gridColor, clamp01(lineMask * (0.65F + 0.35F * dataSweep)));
        color = ColorUtil.lerp(color, nodeColor, clamp01(nodeMask * intensity));
        color = scaleBrightness(color, normalShade);

        float rim = clamp01(1.0F - edgeDist * 6.0F);
        float alpha = clamp01(opacity * (0.24F + 0.58F * lineMask + 0.68F * nodeMask + 0.30F * rim));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 09. LIQUID METAL ───────────────────────────

    private static int evalLiquidMetal(int baseColor, float intensity, float opacity,
                                       float u, float v, float edgeDist, float normalShade,
                                       float nx, float ny, float nz,
                                       float wx, float wy, float wz, float t) {
        // Анизотропные хромированные полосы отражения и течение расплавленного металла
        float flow = sin(u * 5.2F + v * 3.8F + t * 1.55F) + 0.6F * cos(u * 8.4F - v * 6.1F - t * 1.15F);
        float normalBias = nx * 0.45F + ny * 0.75F - nz * 0.35F;
        float reflBand = 0.5F + 0.5F * sin((u * 3.2F - v * 2.8F + normalBias) * 3.0F + flow * 1.35F);
        float specular = pow4(reflBand);
        float darkHorizon = clamp01(1.0F - Math.abs(reflBand - 0.38F) * 4.2F);

        int darkChrome = ColorUtil.lerp(0xFF161D2B, ColorUtil.darken(baseColor, 0.58F), 0.55F);
        int midChrome = ColorUtil.lerp(0xFF9EB4D0, baseColor, 0.55F);
        int brightChrome = ColorUtil.lerp(baseColor, 0xFFFFFFFF, 0.78F);

        int color = ColorUtil.lerp(midChrome, darkChrome, darkHorizon * 0.75F);
        color = ColorUtil.lerp(color, brightChrome, clamp01(specular * intensity));
        color = scaleBrightness(color, normalShade);

        float rim = clamp01(1.0F - edgeDist * 5.0F);
        float alpha = clamp01(opacity * (0.62F + 0.28F * specular + 0.15F * rim));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 10. AURORA ───────────────────────────

    private static int evalAurora(int baseColor, float intensity, float opacity,
                                  float u, float v, float edgeDist, float normalShade,
                                  float wx, float wy, float wz, float t) {
        // Ленточные завесы северного сияния
        float sway = 0.22F * sin(u * 5.5F + wx * 0.8F + t * 1.3F)
                + 0.12F * cos(u * 11.0F - wz * 0.8F - t * 1.9F);
        float ribbon1 = (float) Math.exp(-Math.pow((v - (0.38F + sway)) * 4.5F, 2.0));
        float ribbon2 = (float) Math.exp(-Math.pow((v - (0.68F - sway * 0.8F)) * 5.0F, 2.0));
        float verticalStreaks = 0.65F + 0.35F * sin((u * 18.0F + sway * 10.0F) + t * 2.2F);

        int emerald = ColorUtil.lerp(0xFF2AF598, baseColor, 0.45F);
        int cyan = 0xFF00C9FF;
        int violet = 0xFF9B5DE5;

        int auroraColor = ColorUtil.lerp(emerald, cyan, clamp01(u + sway));
        auroraColor = ColorUtil.lerp(auroraColor, violet, clamp01(ribbon2 * 0.85F));
        float curtain = clamp01((ribbon1 + ribbon2 * 0.85F) * verticalStreaks * intensity);

        int nightBase = ColorUtil.darken(baseColor, 0.72F);
        int color = ColorUtil.lerp(nightBase, auroraColor, curtain);
        color = ColorUtil.lerp(color, 0xFFE6FFF9, pow4(curtain) * 0.35F);
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.30F + 0.62F * curtain + 0.16F * (1.0F - edgeDist * 4.0F)));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 11. GLITCH ───────────────────────────

    private static int evalGlitch(int baseColor, float intensity, float opacity,
                                  float u, float v, float edgeDist, float normalShade,
                                  float wx, float wy, float wz, float t) {
        float tick = (float) Math.floor(t * 10.0F);
        float bandY = (float) Math.floor(v * 10.0F);
        float bandHash = hash12(bandY, tick + Math.floor(wy));
        boolean glitchBar = bandHash > (0.76F - 0.14F * clamp01(intensity - 0.5F));

        float cellX = (float) Math.floor((u + (glitchBar ? (bandHash - 0.5F) * 0.35F : 0.0F)) * 8.0F);
        float blockHash = hash13(cellX, bandY, tick);

        int cyanChannel = 0xFF05D9E8;
        int redChannel = 0xFFFF2A6D;
        int color = baseColor;
        if (glitchBar) {
            color = blockHash < 0.45F ? redChannel : (blockHash < 0.85F ? cyanChannel : 0xFFFFFFFF);
        } else if (blockHash > 0.88F) {
            color = ColorUtil.lerp(baseColor, cyanChannel, 0.75F);
        } else {
            float scan = 0.82F + 0.18F * sin(v * 40.0F - t * 12.0F);
            color = scaleBrightness(baseColor, scan * normalShade);
        }

        float alphaBoost = glitchBar ? 0.92F : (blockHash > 0.88F ? 0.85F : 0.48F);
        float rim = clamp01(1.0F - edgeDist * 6.0F);
        float alpha = clamp01(opacity * (alphaBoost + 0.25F * rim));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 12. FIRE CORE ───────────────────────────

    private static int evalFireCore(int baseColor, float intensity, float opacity,
                                    float u, float v, float edgeDist, float normalShade,
                                    float wx, float wy, float wz, float t) {
        // Движение пламени снизу вверх (по V и мировой оси Y)
        float flameNoise = fbm(u * 5.5F + sin(v * 4.0F - t * 2.4F) * 0.45F, v * 5.5F - t * 3.2F);
        float centerHeat = 1.0F - Math.abs(u - 0.5F) * 1.45F;
        float verticalHeat = 1.0F - v * 0.72F; // снизу горячее
        float heat = clamp01((verticalHeat * 0.58F + flameNoise * 0.62F + centerHeat * 0.22F) * clamp(intensity, 0.5F, 1.6F));

        int emberDark = 0xFF3B0900;
        int fireRed = ColorUtil.lerp(0xFFD92B04, baseColor, 0.45F);
        int fireOrange = 0xFFFF7B00;
        int fireWhiteYellow = 0xFFFFF6A8;

        int color;
        if (heat < 0.35F) {
            color = ColorUtil.lerp(emberDark, fireRed, heat / 0.35F);
        } else if (heat < 0.72F) {
            color = ColorUtil.lerp(fireRed, fireOrange, (heat - 0.35F) / 0.37F);
        } else {
            color = ColorUtil.lerp(fireOrange, fireWhiteYellow, (heat - 0.72F) / 0.28F);
        }
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.35F + 0.60F * heat));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 13. ICE CRYSTAL ───────────────────────────

    private static int evalIceCrystal(int baseColor, float intensity, float opacity,
                                      float u, float v, float edgeDist, float normalShade,
                                      float wx, float wy, float wz, float t) {
        // Кристаллическая решётка Вороного и морозные грани у краёв
        float voronoi = voronoiCell(u * 4.8F + wx * 0.25F, v * 4.8F + wz * 0.25F, t * 0.25F);
        float facetHighlight = pow4(1.0F - voronoi);
        float frostEdge = clamp01(1.0F - edgeDist * (3.2F + 1.4F * fbm(u * 8.0F, v * 8.0F)));
        float coldGlint = pow4(0.5F + 0.5F * sin((u * 7.0F - v * 5.0F) + t * 1.8F));

        int deepIce = ColorUtil.lerp(0xFF1B5E8A, ColorUtil.darken(baseColor, 0.35F), 0.55F);
        int crystalCyan = ColorUtil.lerp(0xFF8BE9FD, baseColor, 0.5F);
        int frostWhite = 0xFFF2FCFF;

        int color = ColorUtil.lerp(deepIce, crystalCyan, clamp01(facetHighlight * 0.85F + frostEdge * 0.55F));
        color = ColorUtil.lerp(color, frostWhite, clamp01((frostEdge * 0.65F + coldGlint * 0.45F) * intensity));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.48F + 0.38F * frostEdge + 0.22F * coldGlint));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 14. SPECTRAL OUTLINE ───────────────────────────

    private static int evalSpectralOutline(int baseColor, float intensity, float opacity,
                                           float u, float v, float edgeDist, float normalShade,
                                           float wx, float wy, float wz, float t) {
        // Яркий бегущий спектральный контур вдоль граней и деликатная стеклянная середина
        float perimeterAngle = (float) (Math.atan2(v - 0.5F, u - 0.5F) / TAU + 0.5);
        float spectralHue = fract(perimeterAngle + (wx + wy + wz) * 0.15F - t * 0.38F);
        int spectral = ColorUtil.hsb(spectralHue, 0.84F, 1.0F);
        spectral = ColorUtil.lerp(spectral, baseColor, 0.28F);

        float contour = (float) Math.exp(-edgeDist * 9.5F);
        float outerHalo = (float) Math.exp(-edgeDist * 3.8F);
        int color = ColorUtil.lerp(ColorUtil.darken(spectral, 0.55F), spectral, outerHalo);
        color = ColorUtil.lerp(color, 0xFFFFFFFF, clamp01(contour * 0.65F * intensity));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.14F + 0.42F * outerHalo + 0.52F * contour) * clamp(intensity, 0.5F, 1.6F));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 15. COSMIC DUST ───────────────────────────

    private static int evalCosmicDust(int baseColor, float intensity, float opacity,
                                      float u, float v, float edgeDist, float normalShade,
                                      float wx, float wy, float wz, float t) {
        // Туманность глубокого космоса + мерцающие звёздные точки
        float nebula1 = fbm(u * 3.5F + t * 0.28F + wx * 0.3F, v * 3.5F - t * 0.22F + wz * 0.3F);
        float nebula2 = fbm(u * 6.2F - t * 0.35F, v * 6.2F + t * 0.31F);

        // Звёздная решётка
        float starGrid = 10.0F;
        float cellU = (float) Math.floor(u * starGrid);
        float cellV = (float) Math.floor(v * starGrid);
        float localU = fract(u * starGrid) - 0.5F;
        float localV = fract(v * starGrid) - 0.5F;
        float starSeed = hash13(cellU, cellV, Math.floor(wx + wy + wz));
        float starTwinkle = 0.5F + 0.5F * sin(t * (3.5F + starSeed * 4.0F) + starSeed * TAU);
        float starDist = (float) Math.hypot(localU, localV);
        float starPoint = starSeed > 0.68F ? clamp01(1.0F - starDist * 4.2F) * starTwinkle : 0.0F;

        int spaceVoid = 0xFF0E0720;
        int nebulaViolet = ColorUtil.lerp(0xFF7B2CBF, baseColor, 0.55F);
        int nebulaCyan = 0xFF48CAE4;

        int color = ColorUtil.lerp(spaceVoid, nebulaViolet, clamp01(nebula1 * 1.15F));
        color = ColorUtil.lerp(color, nebulaCyan, clamp01(nebula2 * 0.65F));
        color = ColorUtil.lerp(color, 0xFFFFFFFF, clamp01(starPoint * 1.35F * intensity));
        color = scaleBrightness(color, normalShade);

        float alpha = clamp01(opacity * (0.52F + 0.32F * nebula1 + 0.45F * starPoint));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── 16. VOID DISTORTION ───────────────────────────

    private static int evalVoidDistortion(int baseColor, float intensity, float opacity,
                                          float u, float v, float edgeDist, float normalShade,
                                          float wx, float wy, float wz, float t) {
        // Гравитационный вихрь пустоты и пульсирующие пространственные разломы
        float dx = u - 0.5F;
        float dy = v - 0.5F;
        float radius = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.atan2(dy, dx);

        float swirl = sin(angle * 3.0F - radius * 16.0F + t * 3.1F);
        float rift = pow4(0.5F + 0.5F * sin((u * 6.5F - v * 6.5F) + swirl * 1.5F - t * 2.4F));
        float implosion = clamp01(1.0F - Math.abs(radius - fract(0.65F - t * 0.28F) * 0.65F) * 6.5F);

        int abyssBlack = 0xFF070312;
        int voidPurple = ColorUtil.lerp(0xFF5A189A, baseColor, 0.55F);
        int riftViolet = 0xFFE0AAFF;

        int color = ColorUtil.lerp(abyssBlack, voidPurple, clamp01(0.35F + 0.55F * swirl + 0.45F * implosion));
        color = ColorUtil.lerp(color, riftViolet, clamp01((rift * 0.78F + implosion * 0.55F) * intensity));
        color = scaleBrightness(color, normalShade);

        float rim = clamp01(1.0F - edgeDist * 5.5F);
        float alpha = clamp01(opacity * (0.58F + 0.32F * rift + 0.25F * rim));
        return ColorUtil.withAlpha(color, Math.round(255.0F * alpha));
    }

    // ─────────────────────────── ВСПОМОГАТЕЛЬНАЯ МАТЕМАТИКА ───────────────────────────

    public static float sin(float rad) {
        return (float) Math.sin(rad);
    }

    public static float cos(float rad) {
        return (float) Math.cos(rad);
    }

    public static float fract(float x) {
        return x - (float) Math.floor(x);
    }

    public static float clamp01(float x) {
        return x < 0.0F ? 0.0F : (x > 1.0F ? 1.0F : x);
    }

    public static float clamp(float x, float min, float max) {
        return x < min ? min : (x > max ? max : x);
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static float finite(float v, float fallback) {
        return Float.isFinite(v) ? v : fallback;
    }

    private static float pow3(float x) {
        float c = clamp01(x);
        return c * c * c;
    }

    private static float pow4(float x) {
        float c = clamp01(x);
        float sq = c * c;
        return sq * sq;
    }

    /** Плавный 2D-шум (Value Noise + билинейная сглаженная интерполяция). */
    public static float noise2D(float x, float y) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float ux = fx * fx * (3.0F - 2.0F * fx);
        float uy = fy * fy * (3.0F - 2.0F * fy);

        float a = hash12(ix, iy);
        float b = hash12(ix + 1.0F, iy);
        float c = hash12(ix, iy + 1.0F);
        float d = hash12(ix + 1.0F, iy + 1.0F);

        return a + (b - a) * ux + (c - a) * uy + (a - b - c + d) * ux * uy;
    }

    /** Двухоктавный фрактальный шум (FBM) в диапазоне 0..1. */
    public static float fbm(float x, float y) {
        return 0.65F * noise2D(x, y) + 0.35F * noise2D(x * 2.03F + 13.7F, y * 2.03F + 7.3F);
    }

    /** Расстояние до ближайшего центра ячейки Вороного (для кристаллов льда). */
    public static float voronoiCell(float x, float y, float phase) {
        float ix = (float) Math.floor(x);
        float iy = (float) Math.floor(y);
        float fx = x - ix;
        float fy = y - iy;
        float minDist = 1.0F;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                float cx = ix + dx;
                float cy = iy + dy;
                float h1 = hash12(cx, cy);
                float h2 = hash12(cx + 19.1F, cy + 47.3F);
                float px = dx + 0.5F + 0.38F * sin(phase + TAU * h1) - fx;
                float py = dy + 0.5F + 0.38F * cos(phase + TAU * h2) - fy;
                float dist = (float) Math.sqrt(px * px + py * py);
                if (dist < minDist) {
                    minDist = dist;
                }
            }
        }
        return clamp01(minDist);
    }

    public static float hash12(double x, double y) {
        double dot = x * 127.1 + y * 311.7;
        double s = Math.sin(dot) * 43758.5453123;
        return (float) (s - Math.floor(s));
    }

    public static float hash13(double x, double y, double z) {
        double dot = x * 127.1 + y * 311.7 + z * 74.7;
        double s = Math.sin(dot) * 43758.5453123;
        return (float) (s - Math.floor(s));
    }

    private static int scaleBrightness(int color, float factor) {
        float f = clamp(factor, 0.0F, 1.5F);
        int a = ColorUtil.alpha(color);
        int r = clamp255(Math.round(ColorUtil.red(color) * f));
        int g = clamp255(Math.round(ColorUtil.green(color) * f));
        int b = clamp255(Math.round(ColorUtil.blue(color) * f));
        return ColorUtil.argb(a, r, g, b);
    }
}
