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
    public static final int DEFAULT_GRID_STEPS = 4;
    public static final int DEFAULT_EDGE_SEGMENTS = 6;
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

        // Ограничиваем плотность граней (20–34%), чтобы блок всегда оставался прозрачным стеклом, а не глухим кубом
        float glazeOpacity = Math.max(0.05F, Math.min(0.36F, opacity * 0.42F));

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

        // Тонкая стеклянная глазурь (15–22% альфа), чтобы текстура предмета была видна на 100%
        float glazeOpacity = Math.max(0.04F, Math.min(0.22F, opacity * 0.25F));
        float edgeOpacity = Math.max(0.18F, Math.min(0.76F, opacity * 0.80F));
        float contourScale = 1.0F + Math.max(0.004F, Math.min(0.036F, outlineWidth * 1.15F));

        for (int i = 0; i < quads.size(); i++) {
            ItemQuad q = quads.get(i);
            Direction dir = q.direction() != null ? q.direction() : Direction.UP;
            float nx = dir.getStepX();
            float ny = dir.getStepY();
            float nz = dir.getStepZ();

            boolean isFrontOrBack = (dir == Direction.NORTH || dir == Direction.SOUTH);

            if (isFrontOrBack) {
                if (mode == FaceGradientMode.OUTLINE_ONLY) {
                    continue;
                }
                // Лёгкий сдвиг вдоль нормали (0.0012F), чтобы глазурь лежала поверх текстуры без Z-fighting
                float offX = nx * 0.0012F;
                float offY = ny * 0.0012F;
                float offZ = nz * 0.0012F;

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
                // Боковая грань силуэта предмета: масштабируем ТОЛЬКО относительно центра (cx, cy, cz)
                // без смещения вдоль нормали грани, чтобы соседние ступеньки 1/16 пикселя имели
                // строго общие углы и никогда не распадались на отдельные квадраты!
                float sx0 = cx + (q.x0() - cx) * contourScale;
                float sy0 = cy + (q.y0() - cy) * contourScale;
                float sz0 = cz + (q.z0() - cz) * contourScale;

                float sx1 = cx + (q.x1() - cx) * contourScale;
                float sy1 = cy + (q.y1() - cy) * contourScale;
                float sz1 = cz + (q.z1() - cz) * contourScale;

                float sx2 = cx + (q.x2() - cx) * contourScale;
                float sy2 = cy + (q.y2() - cy) * contourScale;
                float sz2 = cz + (q.z2() - cz) * contourScale;

                float sx3 = cx + (q.x3() - cx) * contourScale;
                float sy3 = cy + (q.y3() - cy) * contourScale;
                float sz3 = cz + (q.z3() - cz) * contourScale;

                float edgeT = clamp01(((q.x0() - minX) / spanX + (q.y0() - minY) / spanY) * 0.5F);
                int rimCol = ShaderMath.sampleEdge(active, baseColor, intensity, speed, edgeOpacity,
                        edgeT, q.x0(), q.y0(), q.z0(), timeSeconds);

                ShapeBuffer.addQuad(pose, consumer,
                        sx0, sy0, sz0,
                        sx1, sy1, sz1,
                        sx2, sy2, sz2,
                        sx3, sy3, sz3,
                        nx, ny, nz, rimCol);
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
        float pad = 0.0025F;
        float armGlazeOpacity = Math.max(0.04F, Math.min(0.24F, opacity * 0.28F));
        float armEdgeOpacity = Math.max(0.18F, Math.min(0.72F, opacity * 0.78F));
        float edgeThick = Math.max(0.003F, Math.min(0.022F, outlineWidth * 0.85F));

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
        float inv = 1.0F / steps;
        for (int iu = 0; iu < steps; iu++) {
            float u0 = iu * inv;
            float u1 = (iu + 1) * inv;
            for (int iv = 0; iv < steps; iv++) {
                float v0 = iv * inv;
                float v1 = (iv + 1) * inv;

                float p00x = bilerp(ax, bx, cx, dx, u0, v0);
                float p00y = bilerp(ay, by, cy, dy, u0, v0);
                float p00z = bilerp(az, bz, cz, dz, u0, v0);

                float p01x = bilerp(ax, bx, cx, dx, u0, v1);
                float p01y = bilerp(ay, by, cy, dy, u0, v1);
                float p01z = bilerp(az, bz, cz, dz, u0, v1);

                float p11x = bilerp(ax, bx, cx, dx, u1, v1);
                float p11y = bilerp(ay, by, cy, dy, u1, v1);
                float p11z = bilerp(az, bz, cz, dz, u1, v1);

                float p10x = bilerp(ax, bx, cx, dx, u1, v0);
                float p10y = bilerp(ay, by, cy, dy, u1, v0);
                float p10z = bilerp(az, bz, cz, dz, u1, v0);

                int c00 = ShaderMath.sampleSurfaceWithMode(effect, mode, baseColor, intensity, speed, opacity,
                        u0, v0, nx, ny, nz, wx + u0, wy + v0, wz + (u0 + v0) * 0.5F, timeSeconds);
                int c01 = ShaderMath.sampleSurfaceWithMode(effect, mode, baseColor, intensity, speed, opacity,
                        u0, v1, nx, ny, nz, wx + u0, wy + v1, wz + (u0 + v1) * 0.5F, timeSeconds);
                int c11 = ShaderMath.sampleSurfaceWithMode(effect, mode, baseColor, intensity, speed, opacity,
                        u1, v1, nx, ny, nz, wx + u1, wy + v1, wz + (u1 + v1) * 0.5F, timeSeconds);
                int c10 = ShaderMath.sampleSurfaceWithMode(effect, mode, baseColor, intensity, speed, opacity,
                        u1, v0, nx, ny, nz, wx + u1, wy + v0, wz + (u1 + v0) * 0.5F, timeSeconds);

                ShapeBuffer.addShadedQuad(pose, consumer,
                        p00x, p00y, p00z, c00,
                        p01x, p01y, p01z, c01,
                        p11x, p11y, p11z, c11,
                        p10x, p10y, p10z, c10,
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
