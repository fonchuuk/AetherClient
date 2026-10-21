package com.aetherglass.client.render.shader;

import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;

import java.util.List;

/**
 * 3D-рендерер 16 шейдер-эффектов для блоков ({@code BlockShader}) и рук ({@code HandsShader}):
 * <ul>
 *   <li><b>Блоки ({@code BlockShader})</b>: полупрозрачная стеклянная глазурь (20–32% альфа)
 *       по граням + 3D светящийся контур по реальной геометрии {@code VoxelShape};</li>
 *   <li><b>Предметы и рука ({@code HandsShader})</b>: лёгкая полупрозрачная глазурь (15–22% альфа)
 *       и бесшовный силуэтный контур поверх 100% оригинальной текстуры предмета.</li>
 * </ul>
 */
public final class WorldShaderRenderer {
    public static final int DEFAULT_GRID_STEPS = 2;
    public static final int DEFAULT_EDGE_SEGMENTS = 2;
    private static final float SURFACE_OFFSET = 0.0020F;

    /**
     * Легковесное представление одного квада модели предмета в координатах {@code [0..1]},
     * извлечённое без модификации буферов Fabric Indigo.
     */
    public record ItemQuad(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            Direction direction
    ) {
    }

    private WorldShaderRenderer() {
    }

    public static void renderShadedBox(PoseStack.Pose pose, VertexConsumer consumer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float worldX, float worldY, float worldZ,
                                       ShaderEffect effect, int baseColor,
                                       float intensity, float speed, float opacity,
                                       float timeSeconds, int gridSteps) {
        renderShadedBox(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                worldX, worldY, worldZ, effect, FaceGradientMode.MENISCUS_DROP,
                baseColor, intensity, speed, opacity, timeSeconds, gridSteps);
    }

    public static void renderShadedBox(PoseStack.Pose pose, VertexConsumer consumer,
                                       float minX, float minY, float minZ,
                                       float maxX, float maxY, float maxZ,
                                       float worldX, float worldY, float worldZ,
                                       ShaderEffect effect, FaceGradientMode gradientMode,
                                       int baseColor, float intensity, float speed, float opacity,
                                       float timeSeconds, int gridSteps) {
        if (pose == null || consumer == null) {
            return;
        }
        FaceGradientMode mode = gradientMode != null ? gradientMode : FaceGradientMode.MENISCUS_DROP;
        if (mode == FaceGradientMode.OUTLINE_ONLY) {
            return;
        }
        ShaderEffect active = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        int steps = Math.max(2, Math.min(8, gridSteps));
        float x0 = minX - SURFACE_OFFSET;
        float y0 = minY - SURFACE_OFFSET;
        float z0 = minZ - SURFACE_OFFSET;
        float x1 = maxX + SURFACE_OFFSET;
        float y1 = maxY + SURFACE_OFFSET;
        float z1 = maxZ + SURFACE_OFFSET;

        // Ограничиваем плотность граней, чтобы блок/рука оставались прозрачными с ярким внутренним шейдером
        float glazeOpacity = (mode == FaceGradientMode.INSIDE_SHADER || mode == FaceGradientMode.FULL_SHADER)
                ? Math.max(0.24F, Math.min(0.64F, opacity * 0.76F))
                : Math.max(0.05F, Math.min(0.36F, opacity * 0.42F));

        // 1. Верхняя грань (+Y)
        renderFaceGrid(pose, consumer, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0,
                0.0F, 1.0F, 0.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
        // 2. Нижняя грань (-Y)
        renderFaceGrid(pose, consumer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1,
                0.0F, -1.0F, 0.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
        // 3. Северная грань (-Z)
        renderFaceGrid(pose, consumer, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0,
                0.0F, 0.0F, -1.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
        // 4. Южная грань (+Z)
        renderFaceGrid(pose, consumer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                0.0F, 0.0F, 1.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
        // 5. Западная грань (-X)
        renderFaceGrid(pose, consumer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                -1.0F, 0.0F, 0.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
        // 6. Восточная грань (+X)
        renderFaceGrid(pose, consumer, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1,
                1.0F, 0.0F, 0.0F, worldX, worldY, worldZ, active, mode, baseColor, intensity, speed, glazeOpacity, timeSeconds, steps);
    }

    /**
     * Рисует одно ребро {@code VoxelShape} с плавным бегом цвета и мягким свечением.
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
        float coreHalf = Math.max(0.0025F, Math.min(0.032F, thickness * 0.50F));
        float haloHalf = coreHalf * (1.75F + Math.max(0.0F, Math.min(1.2F, glowStrength)));

        for (int s = 0; s < segments; s++) {
            float t0 = s * inv;
            float t1 = (s + 1) * inv;
            float tMid = (t0 + t1) * 0.5F;

            float sx0 = x0 + (x1 - x0) * t0;
            float sy0 = y0 + (y1 - y0) * t0;
            float sz0 = z0 + (z1 - z0) * t0;

            float sx1 = x0 + (x1 - x0) * t1;
            float sy1 = y0 + (y1 - y0) * t1;
            float sz1 = z0 + (z1 - z0) * t1;

            int edgeColor = ShaderMath.sampleEdge(active, baseColor, intensity, speed, opacity,
                    tMid, worldX + sx0, worldY + sy0, worldZ + sz0, timeSeconds);

            float minEx = Math.min(sx0, sx1) - coreHalf;
            float minEy = Math.min(sy0, sy1) - coreHalf;
            float minEz = Math.min(sz0, sz1) - coreHalf;
            float maxEx = Math.max(sx0, sx1) + coreHalf;
            float maxEy = Math.max(sy0, sy1) + coreHalf;
            float maxEz = Math.max(sz0, sz1) + coreHalf;

            ShapeBuffer.addBoxFaces(pose, consumer, minEx, minEy, minEz, maxEx, maxEy, maxEz, edgeColor);

            if (glowStrength > 0.02F) {
                int haloAlpha = Math.round(ColorUtil.alpha(edgeColor) * 0.24F * Math.min(1.0F, glowStrength));
                if (haloAlpha > 3) {
                    int haloCol = ColorUtil.withAlpha(edgeColor, haloAlpha);
                    ShapeBuffer.addBoxFaces(pose, consumer,
                            Math.min(sx0, sx1) - haloHalf,
                            Math.min(sy0, sy1) - haloHalf,
                            Math.min(sz0, sz1) - haloHalf,
                            Math.max(sx0, sx1) + haloHalf,
                            Math.max(sy0, sy1) + haloHalf,
                            Math.max(sz0, sz1) + haloHalf,
                            haloCol);
                }
            }
        }
    }

    /**
     * Накладывает полупрозрачную стеклянную глазурь (15–22% альфа) и бесшовный силуэтный
     * контур поверх полигонов предмета в руке ({@link ItemQuad}), сохраняя 100% видимость
     * оригинальной текстуры предмета.
     */
    public static void renderItemQuadsShader3D(PoseStack.Pose pose, VertexConsumer consumer,
                                               List<ItemQuad> quads,
                                               ShaderEffect effect, FaceGradientMode gradientMode,
                                               int baseColor, float intensity, float speed,
                                               float opacity, float outlineWidth, float timeSeconds) {
        if (pose == null || consumer == null || quads == null || quads.isEmpty() || opacity <= 0.01F) {
            return;
        }
        ShaderEffect active = effect != null ? effect : ShaderEffect.LIQUID_GLASS;
        FaceGradientMode mode = gradientMode != null ? gradientMode : FaceGradientMode.MENISCUS_DROP;

        // 1. Вычисляем геометрический центр предмета
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < quads.size(); i++) {
            ItemQuad q = quads.get(i);
            minX = Math.min(minX, Math.min(Math.min(q.x0(), q.x1()), Math.min(q.x2(), q.x3())));
            minY = Math.min(minY, Math.min(Math.min(q.y0(), q.y1()), Math.min(q.y2(), q.y3())));
            minZ = Math.min(minZ, Math.min(Math.min(q.z0(), q.z1()), Math.min(q.z2(), q.z3())));
            maxX = Math.max(maxX, Math.max(Math.max(q.x0(), q.x1()), Math.max(q.x2(), q.x3())));
            maxY = Math.max(maxY, Math.max(Math.max(q.y0(), q.y1()), Math.max(q.y2(), q.y3())));
            maxZ = Math.max(maxZ, Math.max(Math.max(q.z0(), q.z1()), Math.max(q.z2(), q.z3())));
        }
        float cx = (minX + maxX) * 0.5F;
        float cy = (minY + maxY) * 0.5F;
        float cz = (minZ + maxZ) * 0.5F;
        float spanX = Math.max(0.05F, maxX - minX);
        float spanY = Math.max(0.05F, maxY - minY);
        float spanZ = Math.max(0.005F, maxZ - minZ);

        // Проверяем, является ли предмет плоским 2D-спрайтом (меч, яблоко, жемчуг и т.д., где толщина Z ~ 1/16)
        boolean isFlatSpriteItem = (spanZ <= 0.085F && quads.size() >= 6);

        float glazeOpacity = (mode == FaceGradientMode.INSIDE_SHADER || mode == FaceGradientMode.FULL_SHADER)
                ? Math.max(0.32F, Math.min(0.68F, opacity * 0.82F))
                : Math.max(0.18F, Math.min(0.48F, opacity * 0.62F));
        float edgeOpacity = Math.max(0.48F, Math.min(0.96F, opacity * 1.15F));
        float outShift = Math.max(0.0045F, Math.min(0.026F, outlineWidth * 0.85F));

        if (isFlatSpriteItem && mode != FaceGradientMode.OUTLINE_ONLY) {
            // Для 2D-предметов (алмазный меч, золотое яблоко и т.д.) НЕ рисуем полный квадрат 16x16 NORTH/SOUTH,
            // чтобы вокруг меча не было прямоугольного фона. Вместо этого заполняем только непрозрачные
            // полосы между боковыми гранями WEST/EAST строго ВНУТРИ пиксельного силуэта предмета!
            float frontZ = minZ - 0.0022F;
            float backZ = maxZ + 0.0022F;
            float[] xBounds = new float[16];
            for (int row = 0; row < 16; row++) {
                float ry0 = row / 16.0F;
                float ry1 = (row + 1) / 16.0F;
                float rMid = (ry0 + ry1) * 0.5F;
                int bCount = 0;
                for (int i = 0; i < quads.size(); i++) {
                    ItemQuad q = quads.get(i);
                    Direction dir = q.direction();
                    float qMinX = Math.min(Math.min(q.x0(), q.x1()), Math.min(q.x2(), q.x3()));
                    float qMaxX = Math.max(Math.max(q.x0(), q.x1()), Math.max(q.x2(), q.x3()));
                    boolean isVerticalXWall = (dir == Direction.WEST || dir == Direction.EAST)
                            || (Math.abs(qMaxX - qMinX) <= 0.002F);
                    if (!isVerticalXWall) {
                        continue;
                    }
                    float qMinY = Math.min(Math.min(q.y0(), q.y1()), Math.min(q.y2(), q.y3()));
                    float qMaxY = Math.max(Math.max(q.y0(), q.y1()), Math.max(q.y2(), q.y3()));
                    if (rMid > qMinY + 0.001F && rMid < qMaxY - 0.001F && bCount < xBounds.length) {
                        xBounds[bCount++] = (qMinX + qMaxX) * 0.5F;
                    }
                }
                int pairs = bCount / 2;
                if (pairs <= 0) {
                    continue;
                }
                java.util.Arrays.sort(xBounds, 0, bCount);
                for (int p = 0; p < pairs; p++) {
                    float spanL = xBounds[p * 2];
                    float spanR = xBounds[p * 2 + 1];
                    if (spanR <= spanL + 0.001F) {
                        continue;
                    }
                    int subCols = Math.max(1, Math.min(16, Math.round((spanR - spanL) * 16.0F)));
                    float stepX = (spanR - spanL) / subCols;
                    for (int c = 0; c < subCols; c++) {
                        float xL = spanL + c * stepX;
                        float xR = (c == subCols - 1) ? spanR : (xL + stepX);
                        float uL = clamp01((xL - minX) / spanX);
                        float uR = clamp01((xR - minX) / spanX);
                        float v0 = clamp01((ry0 - minY) / spanY);
                        float v1 = clamp01((ry1 - minY) / spanY);

                        int c0 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                                uL, v0, 0.0F, 0.0F, 1.0F, xL, ry0, backZ, timeSeconds);
                        int c1 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                                uR, v0, 0.0F, 0.0F, 1.0F, xR, ry0, backZ, timeSeconds);
                        int c2 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                                uR, v1, 0.0F, 0.0F, 1.0F, xR, ry1, backZ, timeSeconds);
                        int c3 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                                uL, v1, 0.0F, 0.0F, 1.0F, xL, ry1, backZ, timeSeconds);

                        // Двусторонние ячейки шейдера ВНУТРИ силуэта предмета (frontZ и backZ)
                        ShapeBuffer.addShadedQuad(pose, consumer,
                                xL, ry0, backZ, c0, xR, ry0, backZ, c1, xR, ry1, backZ, c2, xL, ry1, backZ, c3,
                                0.0F, 0.0F, 1.0F);
                        ShapeBuffer.addShadedQuad(pose, consumer,
                                xL, ry1, backZ, c3, xR, ry1, backZ, c2, xR, ry0, backZ, c1, xL, ry0, backZ, c0,
                                0.0F, 0.0F, -1.0F);
                        ShapeBuffer.addShadedQuad(pose, consumer,
                                xL, ry0, frontZ, c0, xR, ry0, frontZ, c1, xR, ry1, frontZ, c2, xL, ry1, frontZ, c3,
                                0.0F, 0.0F, 1.0F);
                        ShapeBuffer.addShadedQuad(pose, consumer,
                                xL, ry1, frontZ, c3, xR, ry1, frontZ, c2, xR, ry0, frontZ, c1, xL, ry0, frontZ, c0,
                                0.0F, 0.0F, -1.0F);
                    }
                }
            }
        }

        for (int i = 0; i < quads.size(); i++) {
            ItemQuad q = quads.get(i);
            Direction dir = q.direction() != null ? q.direction() : Direction.UP;
            float nx = dir.getStepX();
            float ny = dir.getStepY();
            float nz = dir.getStepZ();

            boolean isFrontOrBack = (dir == Direction.NORTH || dir == Direction.SOUTH);

            if (isFrontOrBack) {
                if (isFlatSpriteItem || mode == FaceGradientMode.OUTLINE_ONLY) {
                    continue;
                }
                float offX = nx * 0.0020F;
                float offY = ny * 0.0020F;
                float offZ = nz * 0.0020F;

                float u0 = clamp01((q.x0() - minX) / spanX);
                float v0 = clamp01((q.y0() - minY) / spanY);
                float u1 = clamp01((q.x1() - minX) / spanX);
                float v1 = clamp01((q.y1() - minY) / spanY);
                float u2 = clamp01((q.x2() - minX) / spanX);
                float v2 = clamp01((q.y2() - minY) / spanY);
                float u3 = clamp01((q.x3() - minX) / spanX);
                float v3 = clamp01((q.y3() - minY) / spanY);

                int c0 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                        u0, v0, nx, ny, nz, q.x0(), q.y0(), q.z0(), timeSeconds);
                int c1 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                        u1, v1, nx, ny, nz, q.x1(), q.y1(), q.z1(), timeSeconds);
                int c2 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                        u2, v2, nx, ny, nz, q.x2(), q.y2(), q.z2(), timeSeconds);
                int c3 = ShaderMath.sampleSurfaceWithMode(active, mode, baseColor, intensity, speed, glazeOpacity,
                        u3, v3, nx, ny, nz, q.x3(), q.y3(), q.z3(), timeSeconds);

                ShapeBuffer.addShadedQuad(pose, consumer,
                        q.x0() + offX, q.y0() + offY, q.z0() + offZ, c0,
                        q.x1() + offX, q.y1() + offY, q.z1() + offZ, c1,
                        q.x2() + offX, q.y2() + offY, q.z2() + offZ, c2,
                        q.x3() + offX, q.y3() + offY, q.z3() + offZ, c3,
                        nx, ny, nz);
            } else {
                // Боковая грань силуэта предмета: сдвигаем наружу вдоль нормали (nx, ny) на outShift
                // и слегка расширяем по Z, рисуя с двусторонним обходом вершин
                float ox = nx * outShift;
                float oy = ny * outShift;
                float zPad = 0.0025F;
                float z0 = q.z0() + (q.z0() < cz ? -zPad : zPad);
                float z1 = q.z1() + (q.z1() < cz ? -zPad : zPad);
                float z2 = q.z2() + (q.z2() < cz ? -zPad : zPad);
                float z3 = q.z3() + (q.z3() < cz ? -zPad : zPad);

                float edgeT = clamp01(((q.x0() - minX) / spanX + (q.y0() - minY) / spanY) * 0.5F);
                int rimCol = ShaderMath.sampleEdge(active, baseColor, intensity, speed, edgeOpacity,
                        edgeT, q.x0(), q.y0(), q.z0(), timeSeconds);

                ShapeBuffer.addQuad(pose, consumer,
                        q.x0() + ox, q.y0() + oy, z0,
                        q.x1() + ox, q.y1() + oy, z1,
                        q.x2() + ox, q.y2() + oy, z2,
                        q.x3() + ox, q.y3() + oy, z3,
                        nx, ny, nz, rimCol);
                ShapeBuffer.addQuad(pose, consumer,
                        q.x3() + ox, q.y3() + oy, z3,
                        q.x2() + ox, q.y2() + oy, z2,
                        q.x1() + ox, q.y1() + oy, z1,
                        q.x0() + ox, q.y0() + oy, z0,
                        -nx, -ny, -nz, rimCol);
            }
        }
    }

    /**
     * Рисует полупрозрачную стеклянную глазурь и контур вокруг руки от первого лица.
     */
    public static void renderHandPartShader3D(PoseStack.Pose pose, VertexConsumer consumer,
                                              List<ModelPart.Cube> cubes,
                                              ShaderEffect effect, FaceGradientMode gradientMode,
                                              int baseColor, float intensity, float speed,
                                              float opacity, float outlineWidth, float timeSeconds) {
        if (pose == null || consumer == null || cubes == null || cubes.isEmpty() || opacity <= 0.01F) {
            return;
        }
        float scale = 1.0F / 16.0F;
        float pad = 0.0020F;
        // Заливка внутри руки: держим её заметной, но полупрозрачной — ванильная текстура руки остаётся видимой
        float armGlazeOpacity = Math.max(0.40F, Math.min(0.66F, 0.34F + opacity * 0.42F));
        float armEdgeOpacity = Math.max(0.55F, Math.min(0.96F, opacity * 1.15F));
        float edgeThick = Math.max(0.004F, Math.min(0.018F, outlineWidth * 0.75F));

        for (int i = 0; i < cubes.size(); i++) {
            ModelPart.Cube cube = cubes.get(i);
            float minX = cube.minX * scale - pad;
            float minY = cube.minY * scale - pad;
            float minZ = cube.minZ * scale - pad;
            float maxX = cube.maxX * scale + pad;
            float maxY = cube.maxY * scale + pad;
            float maxZ = cube.maxZ * scale + pad;

            renderShadedBox(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                    minX, minY, minZ, effect, gradientMode, baseColor, intensity, speed, armGlazeOpacity, timeSeconds, 3);
            renderBoxPerimeterEdges(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                    effect, baseColor, intensity, speed, armEdgeOpacity, edgeThick, timeSeconds);
        }
    }

    /**
     * Метод 3D-оболочки руки и предмета (используется также модульными тестами {@code ShaderEffectsTest}).
     */
    public static void renderHandShader3D(PoseStack.Pose pose, VertexConsumer consumer,
                                          float armDirection, float equipProgress, float swingProgress,
                                          boolean holdingItem, ShaderEffect effect, int baseColor,
                                          float intensity, float speed, float opacity, float timeSeconds) {
        if (pose == null || consumer == null || opacity <= 0.01F) {
            return;
        }
        float minX = 0.22F * armDirection;
        float maxX = 0.56F * armDirection;
        if (minX > maxX) {
            float t = minX;
            minX = maxX;
            maxX = t;
        }
        float minY = -0.58F - equipProgress * 0.15F;
        float maxY = -0.14F + swingProgress * 0.08F;
        float minZ = -0.78F;
        float maxZ = -0.32F;

        renderShadedBox(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                minX, minY, minZ, effect, FaceGradientMode.MENISCUS_DROP,
                baseColor, intensity, speed, opacity, timeSeconds, 3);
        renderBoxPerimeterEdges(pose, consumer, minX, minY, minZ, maxX, maxY, maxZ,
                effect, baseColor, intensity, speed, Math.min(1.0F, opacity * 1.1F), 0.010F, timeSeconds);
    }

    private static void renderBoxPerimeterEdges(PoseStack.Pose pose, VertexConsumer consumer,
                                                float x0, float y0, float z0, float x1, float y1, float z1,
                                                ShaderEffect effect, int baseColor,
                                                float intensity, float speed, float opacity,
                                                float thickness, float timeSeconds) {
        // Нижние 4 ребра
        renderShadedEdge(pose, consumer, x0, y0, z0, x1, y0, z0, x0, y0, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y0, z0, x1, y0, z1, x1, y0, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y0, z1, x0, y0, z1, x1, y0, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x0, y0, z1, x0, y0, z0, x0, y0, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        // Верхние 4 ребра
        renderShadedEdge(pose, consumer, x0, y1, z0, x1, y1, z0, x0, y1, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y1, z0, x1, y1, z1, x1, y1, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y1, z1, x0, y1, z1, x1, y1, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x0, y1, z1, x0, y1, z0, x0, y1, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        // Вертикальные 4 ребра
        renderShadedEdge(pose, consumer, x0, y0, z0, x0, y1, z0, x0, y0, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y0, z0, x1, y1, z0, x1, y0, z0, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x1, y0, z1, x1, y1, z1, x1, y0, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
        renderShadedEdge(pose, consumer, x0, y0, z1, x0, y1, z1, x0, y0, z1, effect, baseColor, intensity, speed, opacity, thickness, 0.25F, timeSeconds);
    }

    private static void renderFaceGrid(PoseStack.Pose pose, VertexConsumer consumer,
                                       float ax, float ay, float az,
                                       float bx, float by, float bz,
                                       float cx, float cy, float cz,
                                       float dx, float dy, float dz,
                                       float nx, float ny, float nz,
                                       float wx, float wy, float wz,
                                       ShaderEffect effect, FaceGradientMode mode,
                                       int baseColor, float intensity, float speed, float opacity,
                                       float timeSeconds, int steps) {
        int s = Math.max(1, Math.min(4, steps));
        int n = s + 1;
        float inv = 1.0F / s;

        int[] gridColors = new int[n * n];
        float[] gx = new float[n * n];
        float[] gy = new float[n * n];
        float[] gz = new float[n * n];

        for (int iu = 0; iu <= s; iu++) {
            float u = iu * inv;
            int rowOff = iu * n;
            for (int iv = 0; iv <= s; iv++) {
                float v = iv * inv;
                int idx = rowOff + iv;
                gx[idx] = bilerp(ax, bx, cx, dx, u, v);
                gy[idx] = bilerp(ay, by, cy, dy, u, v);
                gz[idx] = bilerp(az, bz, cz, dz, u, v);
                gridColors[idx] = ShaderMath.sampleSurfaceWithMode(effect, mode, baseColor, intensity, speed, opacity,
                        u, v, nx, ny, nz, wx + u, wy + v, wz + (u + v) * 0.5F, timeSeconds);
            }
        }

        for (int iu = 0; iu < s; iu++) {
            int r0 = iu * n;
            int r1 = (iu + 1) * n;
            for (int iv = 0; iv < s; iv++) {
                int i00 = r0 + iv;
                int i01 = r0 + iv + 1;
                int i11 = r1 + iv + 1;
                int i10 = r1 + iv;
                ShapeBuffer.addShadedQuad(pose, consumer,
                        gx[i00], gy[i00], gz[i00], gridColors[i00],
                        gx[i01], gy[i01], gz[i01], gridColors[i01],
                        gx[i11], gy[i11], gz[i11], gridColors[i11],
                        gx[i10], gy[i10], gz[i10], gridColors[i10],
                        nx, ny, nz);
            }
        }
    }

    private static float bilerp(float a, float b, float c, float d, float u, float v) {
        float top = a + (d - a) * u;
        float bottom = b + (c - b) * u;
        return top + (bottom - top) * v;
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }
}
