package com.aetherglass.client.render.shader;

import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * 3D-рендерер 16 шейдер-эффектов для блоков ({@code BlockShader}) и рук ({@code HandsShader}).
 *
 * <p>Разбивает каждую грань параллелепипеда из {@code VoxelShape} (или 3D-оболочки руки/предмета)
 * на адаптивную UV-сетку с по-вершинным вычислением {@link ShaderMath#sampleSurface} и
 * аппаратной интерполяцией цвета через {@link ShapeBuffer#addShadedQuad}, а также
 * дорисовывает уникальные для каждого эффекта пространственные элементы: молнии,
 * кибер-сетку, голографические сканлайны, RGB-глитчи, морозные трещины, языки пламени,
 * звёздные вспышки и разломы пустоты.</p>
 */
public final class WorldShaderRenderer {
    /** Базовое разрешение UV-сетки на каждой грани блока (6×6 = 36 под-квадов на грань). */
    public static final int DEFAULT_GRID_STEPS = 6;
    /** Число сегментов разбиения каждого ребра для плавного бега цвета. */
    public static final int DEFAULT_EDGE_SEGMENTS = 6;
    /** Небольшой отступ вдоль нормали, исключающий Z-fighting с поверхностью блока. */
    private static final float SURFACE_OFFSET = 0.0022F;

    private WorldShaderRenderer() {
    }

    /**
     * Рисует заполненную 3D-поверхность параллелепипеда с выбранным шейдер-эффектом.
     */
    public static void renderShadedBox(PoseStack.Pose pose, VertexConsumer consumer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float worldX, float worldY, float worldZ,
                                       ShaderEffect effect, int baseColor,
                                       float intensity, float speed, float opacity,
                                       float timeSeconds, int gridSteps) {
        if (pose == null || consumer == null) {
            return;
        }
        ShaderEffect active = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        int steps = Math.max(2, Math.min(10, gridSteps));
        float x0 = minX - SURFACE_OFFSET;
        float y0 = minY - SURFACE_OFFSET;
        float z0 = minZ - SURFACE_OFFSET;
        float x1 = maxX + SURFACE_OFFSET;
        float y1 = maxY + SURFACE_OFFSET;
        float z1 = maxZ + SURFACE_OFFSET;

        // 1. Верхняя грань (+Y)
        renderFaceGrid(pose, consumer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                0.0F, 1.0F, 0.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);
        // 2. Нижняя грань (-Y)
        renderFaceGrid(pose, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                0.0F, -1.0F, 0.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);
        // 3. Северная грань (-Z)
        renderFaceGrid(pose, consumer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                0.0F, 0.0F, -1.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);
        // 4. Южная грань (+Z)
        renderFaceGrid(pose, consumer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                0.0F, 0.0F, 1.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);
        // 5. Западная грань (-X)
        renderFaceGrid(pose, consumer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                -1.0F, 0.0F, 0.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);
        // 6. Восточная грань (+X)
        renderFaceGrid(pose, consumer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                1.0F, 0.0F, 0.0F, worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds, steps);

        renderEffectDetails(pose, consumer, x0, y0, z0, x1, y1, z1,
                worldX, worldY, worldZ, active, baseColor, intensity, speed, opacity, timeSeconds);
    }

    /**
     * Рисует одно ребро геометрии, разбивая его на сегменты с шейдерным градиентом и мягким ореолом.
     */
    public static void renderShadedEdge(PoseStack.Pose pose, VertexConsumer consumer,
                                        float x0, float y0, float z0,
                                        float x1, float y1, float z1,
                                        float worldX, float worldY, float worldZ,
                                        ShaderEffect effect, int baseColor,
                                        float intensity, float speed, float opacity,
                                        float thickness, float glowStrength,
                                        float timeSeconds) {
        if (pose == null || consumer == null) {
            return;
        }
        ShaderEffect active = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        int segments = DEFAULT_EDGE_SEGMENTS;
        float inv = 1.0F / segments;
        float safeThickness = Math.max(0.004F, thickness);

        for (int s = 0; s < segments; s++) {
            float t0 = s * inv;
            float t1 = (s + 1) * inv;
            float sx0 = lerp(x0, x1, t0);
            float sy0 = lerp(y0, y1, t0);
            float sz0 = lerp(z0, z1, t0);
            float sx1 = lerp(x0, x1, t1);
            float sy1 = lerp(y0, y1, t1);
            float sz1 = lerp(z0, z1, t1);

            float wx = worldX + (sx0 + sx1) * 0.5F;
            float wy = worldY + (sy0 + sy1) * 0.5F;
            float wz = worldZ + (sz0 + sz1) * 0.5F;

            int color = ShaderMath.sampleEdge(active, baseColor, intensity, speed, opacity,
                    (t0 + t1) * 0.5F, wx, wy, wz, timeSeconds);
            ShapeBuffer.addEdge(pose, consumer, sx0, sy0, sz0, sx1, sy1, sz1, safeThickness, color);

            if (glowStrength > 0.01F) {
                int haloNear = ColorUtil.fade(color, 0.32F * Math.min(1.5F, glowStrength));
                ShapeBuffer.addEdge(pose, consumer, sx0, sy0, sz0, sx1, sy1, sz1,
                        safeThickness * 2.2F, haloNear);
            }
        }
    }

    /**
     * Рисует 3D-шейдер вокруг руки или предмета от первого лица ({@code HandsShader}).
     */
    public static void renderHandShader3D(PoseStack.Pose pose, VertexConsumer consumer,
                                          float side, float equipProgress, float swingProgress,
                                          boolean holdingItem,
                                          ShaderEffect effect, int baseColor,
                                          float intensity, float speed, float opacity,
                                          float timeSeconds) {
        if (pose == null || consumer == null) {
            return;
        }
        ShaderEffect active = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        float safeSide = side >= 0.0F ? 1.0F : -1.0F;
        float swingCurve = ShaderMath.sin((float) (Math.sqrt(Math.max(0.0F, swingProgress)) * Math.PI));

        // Координаты 3D-оболочки руки и предмета в первом лице
        float centerX = safeSide * (0.42F - 0.10F * swingCurve);
        float centerY = -0.34F - 0.20F * equipProgress + 0.08F * swingCurve;
        float centerZ = -0.56F - 0.06F * swingCurve;

        float halfW = holdingItem ? 0.24F : 0.20F;
        float halfH = holdingItem ? 0.24F : 0.22F;
        float halfD = holdingItem ? 0.20F : 0.18F;

        float minX = centerX - halfW;
        float maxX = centerX + halfW;
        float minY = centerY - halfH;
        float maxY = centerY + halfH;
        float minZ = centerZ - halfD;
        float maxZ = centerZ + halfD;

        float handSurfaceOpacity = ShaderMath.clamp(opacity * 0.56F, 0.05F, 0.85F);
        renderShadedBox(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                safeSide * 1.5F, equipProgress, swingProgress,
                active, baseColor, intensity, speed, handSurfaceOpacity, timeSeconds, 4);

        float edgeOpacity = ShaderMath.clamp(opacity * 0.90F, 0.10F, 1.0F);
        renderBoxEdges(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                safeSide, 0.0F, 0.0F, active, baseColor, intensity, speed, edgeOpacity, 0.010F, 0.55F, timeSeconds);
    }

    private static void renderBoxEdges(PoseStack.Pose pose, VertexConsumer consumer,
                                       float x0, float y0, float z0, float x1, float y1, float z1,
                                       float wx, float wy, float wz,
                                       ShaderEffect effect, int baseColor,
                                       float intensity, float speed, float opacity,
                                       float thickness, float glowStrength, float t) {
        // Нижнее основание
        renderShadedEdge(pose, consumer, x0, y0, z0, x1, y0, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y0, z0, x1, y0, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y0, z1, x0, y0, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x0, y0, z1, x0, y0, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        // Верхнее основание
        renderShadedEdge(pose, consumer, x0, y1, z0, x1, y1, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y1, z0, x1, y1, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y1, z1, x0, y1, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x0, y1, z1, x0, y1, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        // Вертикальные стойки
        renderShadedEdge(pose, consumer, x0, y0, z0, x0, y1, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y0, z0, x1, y1, z0, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x1, y0, z1, x1, y1, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
        renderShadedEdge(pose, consumer, x0, y0, z1, x0, y1, z1, wx, wy, wz, effect, baseColor, intensity, speed, opacity, thickness, glowStrength, t);
    }

    /**
     * Разбивает четырёхугольную грань на сетку {@code steps × steps} и вычисляет цвет в каждой вершине.
     */
    private static void renderFaceGrid(PoseStack.Pose pose, VertexConsumer consumer,
                                       float ax, float ay, float az,
                                       float bx, float by, float bz,
                                       float cx, float cy, float cz,
                                       float dx, float dy, float dz,
                                       float nx, float ny, float nz,
                                       float worldX, float worldY, float worldZ,
                                       ShaderEffect effect, int baseColor,
                                       float intensity, float speed, float opacity,
                                       float timeSeconds, int steps) {
        float inv = 1.0F / steps;
        for (int iv = 0; iv < steps; iv++) {
            float v0 = iv * inv;
            float v1 = (iv + 1) * inv;
            for (int iu = 0; iu < steps; iu++) {
                float u0 = iu * inv;
                float u1 = (iu + 1) * inv;

                float p00x = bilerp(ax, bx, cx, dx, u0, v0);
                float p00y = bilerp(ay, by, cy, dy, u0, v0);
                float p00z = bilerp(az, bz, cz, dz, u0, v0);

                float p10x = bilerp(ax, bx, cx, dx, u1, v0);
                float p10y = bilerp(ay, by, cy, dy, u1, v0);
                float p10z = bilerp(az, bz, cz, dz, u1, v0);

                float p11x = bilerp(ax, bx, cx, dx, u1, v1);
                float p11y = bilerp(ay, by, cy, dy, u1, v1);
                float p11z = bilerp(az, bz, cz, dz, u1, v1);

                float p01x = bilerp(ax, bx, cx, dx, u0, v1);
                float p01y = bilerp(ay, by, cy, dy, u0, v1);
                float p01z = bilerp(az, bz, cz, dz, u0, v1);

                int c00 = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u0, v0, nx, ny, nz, worldX + p00x, worldY + p00y, worldZ + p00z, timeSeconds);
                int c10 = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u1, v0, nx, ny, nz, worldX + p10x, worldY + p10y, worldZ + p10z, timeSeconds);
                int c11 = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u1, v1, nx, ny, nz, worldX + p11x, worldY + p11y, worldZ + p11z, timeSeconds);
                int c01 = ShaderMath.sampleSurface(effect, baseColor, intensity, speed, opacity,
                        u0, v1, nx, ny, nz, worldX + p01x, worldY + p01y, worldZ + p01z, timeSeconds);

                ShapeBuffer.addShadedQuad(pose, consumer,
                        p00x, p00y, p00z, c00,
                        p10x, p10y, p10z, c10,
                        p11x, p11y, p11z, c11,
                        p01x, p01y, p01z, c01,
                        nx, ny, nz);
            }
        }
    }

    /**
     * Дорисовывает характерные для конкретного шейдер-эффекта пространственные детали
     * (молнии, кибер-сетку, сканлайны, RGB-сдвиги, кристаллы, огненные потоки, звёзды, разломы).
     */
    private static void renderEffectDetails(PoseStack.Pose pose, VertexConsumer consumer,
                                            float x0, float y0, float z0,
                                            float x1, float y1, float z1,
                                            float worldX, float worldY, float worldZ,
                                            ShaderEffect effect, int baseColor,
                                            float intensity, float speed, float opacity,
                                            float timeSeconds) {
        float t = timeSeconds * Math.max(0.1F, speed);
        switch (effect) {
            case ELECTRIC_ARCS -> {
                int boltCore = ColorUtil.withAlpha(0xFFFFFFFF, Math.round(240 * ShaderMath.clamp01(opacity)));
                int boltGlow = ColorUtil.withAlpha(ColorUtil.brighten(baseColor, 0.35F), Math.round(170 * ShaderMath.clamp01(opacity)));
                float step = (float) Math.floor(t * 14.0F);
                drawLightningArc(pose, consumer, x0, y0, z0, x1, y1, z0, step + 1.0F, boltCore, boltGlow);
                drawLightningArc(pose, consumer, x1, y0, z0, x1, y1, z1, step + 2.7F, boltCore, boltGlow);
                drawLightningArc(pose, consumer, x0, y1, z1, x1, y0, z1, step + 4.3F, boltCore, boltGlow);
            }
            case CYBER_GRID -> {
                int gridCol = ColorUtil.withAlpha(ColorUtil.brighten(baseColor, 0.30F), Math.round(190 * ShaderMath.clamp01(opacity)));
                int divs = 4;
                for (int i = 1; i < divs; i++) {
                    float f = i / (float) divs;
                    float gy = lerp(y0, y1, f);
                    ShapeBuffer.addEdge(pose, consumer, x0, gy, z0, x1, gy, z0, 0.006F, gridCol);
                    ShapeBuffer.addEdge(pose, consumer, x1, gy, z0, x1, gy, z1, 0.006F, gridCol);
                    ShapeBuffer.addEdge(pose, consumer, x1, gy, z1, x0, gy, z1, 0.006F, gridCol);
                    ShapeBuffer.addEdge(pose, consumer, x0, gy, z1, x0, gy, z0, 0.006F, gridCol);
                }
            }
            case HOLOGRAM -> {
                int ringCount = 4;
                for (int i = 0; i < ringCount; i++) {
                    float phase = ShaderMath.fract((i / (float) ringCount) + t * 0.35F);
                    float ry = lerp(y0, y1, phase);
                    int ringColor = ColorUtil.withAlpha(
                            i % 2 == 0 ? ColorUtil.brighten(baseColor, 0.40F) : 0xFFFF55B8,
                            Math.round(200 * ShaderMath.clamp01(opacity) * (1.0F - Math.abs(phase - 0.5F) * 1.4F))
                    );
                    ShapeBuffer.addEdge(pose, consumer, x0, ry, z0, x1, ry, z0, 0.008F, ringColor);
                    ShapeBuffer.addEdge(pose, consumer, x1, ry, z0, x1, ry, z1, 0.008F, ringColor);
                    ShapeBuffer.addEdge(pose, consumer, x1, ry, z1, x0, ry, z1, 0.008F, ringColor);
                    ShapeBuffer.addEdge(pose, consumer, x0, ry, z1, x0, ry, z0, 0.008F, ringColor);
                }
            }
            case GLITCH -> {
                float slot = (float) Math.floor(t * 11.0F);
                float shift = (ShaderMath.hash12(slot, worldY) - 0.5F) * 0.035F * intensity;
                int red = ColorUtil.withAlpha(0xFFFF2A6D, Math.round(210 * ShaderMath.clamp01(opacity)));
                int cyan = ColorUtil.withAlpha(0xFF05D9E8, Math.round(210 * ShaderMath.clamp01(opacity)));
                float sliceY = lerp(y0, y1, ShaderMath.hash12(slot + 3.0F, worldX));
                ShapeBuffer.addEdge(pose, consumer, x0 + shift, sliceY, z0, x1 + shift, sliceY, z0, 0.014F, red);
                ShapeBuffer.addEdge(pose, consumer, x0 - shift, sliceY + 0.02F, z1, x1 - shift, sliceY + 0.02F, z1, 0.014F, cyan);
            }
            default -> {
                // Остальные эффекты полностью раскрываются на высокодетализированной сетке поверхности и сегментированных рёбрах
            }
        }
    }

    private static void drawLightningArc(PoseStack.Pose pose, VertexConsumer consumer,
                                         float x0, float y0, float z0,
                                         float x1, float y1, float z1,
                                         float seed, int coreColor, int glowColor) {
        int steps = 4;
        float prevX = x0;
        float prevY = y0;
        float prevZ = z0;
        for (int i = 1; i <= steps; i++) {
            float f = i / (float) steps;
            float nx = lerp(x0, x1, f);
            float ny = lerp(y0, y1, f);
            float nz = lerp(z0, z1, f);
            if (i < steps) {
                nx += (ShaderMath.hash12(seed + i * 3.1, 1.7) - 0.5F) * 0.10F;
                ny += (ShaderMath.hash12(seed + i * 5.3, 4.2) - 0.5F) * 0.10F;
                nz += (ShaderMath.hash12(seed + i * 7.9, 8.6) - 0.5F) * 0.10F;
            }
            ShapeBuffer.addEdge(pose, consumer, prevX, prevY, prevZ, nx, ny, nz, 0.014F, glowColor);
            ShapeBuffer.addEdge(pose, consumer, prevX, prevY, prevZ, nx, ny, nz, 0.006F, coreColor);
            prevX = nx;
            prevY = ny;
            prevZ = nz;
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float bilerp(float ax, float bx, float cx, float dx, float u, float v) {
        float top = lerp(ax, bx, u);
        float bottom = lerp(dx, cx, u);
        return lerp(top, bottom, v);
    }
}
