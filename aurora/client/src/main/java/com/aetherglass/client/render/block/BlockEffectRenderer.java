package com.aetherglass.client.render.block;

import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.material.FillMode;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.shader.WorldShaderRenderer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Отрисовка выделенного блока ({@code BlockShader}, Блок 4.1):
 * <ul>
 *   <li>Проход 1: полупрозрачная шейдерная или стеклянная заливка граней {@code VoxelShape};</li>
 *   <li>Проход 2 (после {@code buffers.endBatch()}): 3D-обводка контура и ореол свечения
 *       рисуются строго ПОВЕРХ заливки, никогда не отключая и не стирая её.</li>
 * </ul>
 */
public final class BlockEffectRenderer {
    /** Единичная матрица: исключает повторное вращение геометрии при повороте камеры. */
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    private static final float[] VALUES = new float[6];
    private static final float[] BOX_TOPS = new float[BlockOutlineGeometry.MAX_BOXES];

    private BlockEffectRenderer() {
    }

    public static void render(PoseStack pose, Camera camera, BlockEffectsModule module) {
        BlockPos position = module.outlined();
        float strength = module.outlineStrength();
        if (position == null || strength <= 0.01F || camera == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        BlockState state = client.level.getBlockState(position);
        if (state.isAir()) {
            return;
        }

        BlockOutlineGeometry.update(state, client.level, position);
        int boxes = BlockOutlineGeometry.boxCount();
        int edges = BlockOutlineGeometry.edgeCount();
        if (boxes == 0 && edges == 0) {
            return;
        }

        MultiBufferSource.BufferSource buffers = RenderResources.acquire();
        if (buffers == null) {
            return;
        }

        Vec3 origin = camera.position();
        float worldX = position.getX();
        float worldY = position.getY();
        float worldZ = position.getZ();
        float shiftX = (float) (worldX - origin.x);
        float shiftY = (float) (worldY - origin.y);
        float shiftZ = (float) (worldZ - origin.z);

        PoseStack.Pose entry = IDENTITY_POSE;
        float[] values = VALUES;
        float time = module.effectTime();

        int topCount = 0;
        for (int index = 0; index < boxes; index++) {
            if (!BlockOutlineGeometry.box(index, values)) {
                break;
            }
            BOX_TOPS[topCount++] = values[4];
        }

        int baseColor = module.shaderColor();
        float fillOpacity = module.shaderOpacity() * strength;
        float lineAlpha = module.outlineAlpha() * strength;
        float thickness = module.outlineWidth();
        float glowStrength = module.glow();

        if (module.useShaderEffect()) {
            ShaderEffect effect = module.shaderEffect();
            FaceGradientMode gradMode = module.faceGradientMode();
            if (gradMode == FaceGradientMode.OUTLINE_ONLY && module.showFill()) {
                gradMode = FaceGradientMode.MENISCUS_DROP;
            }
            float intensity = module.shaderIntensity();
            float speed = module.shaderSpeed();

            // ПРОХОД 1: Полупрозрачная шейдерная заливка граней + мягкое объёмное свечение
            if (module.showFill() && fillOpacity > 0.01F && boxes > 0) {
                VertexConsumer fillConsumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE));
                if (glowStrength > 0.02F) {
                    int haloCol = ColorUtil.fade(baseColor, Math.min(0.32F, 0.14F * glowStrength * strength));
                    float pad = 0.008F + 0.006F * Math.min(1.5F, glowStrength);
                    for (int index = 0; index < boxes; index++) {
                        if (!BlockOutlineGeometry.box(index, values)) {
                            break;
                        }
                        ShapeBuffer.addBoxFaces(entry, fillConsumer,
                                shiftX + values[0] - pad, shiftY + values[1] - pad, shiftZ + values[2] - pad,
                                shiftX + values[3] + pad, shiftY + values[4] + pad, shiftZ + values[5] + pad, haloCol);
                    }
                }

                int gridSteps = boxes <= 2 ? 2 : 1;
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    WorldShaderRenderer.renderShadedBox(
                            entry, fillConsumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            worldX + values[0], worldY + values[1], worldZ + values[2],
                            effect, gradMode, baseColor, intensity, speed, fillOpacity,
                            time, gridSteps
                    );
                }
                // Сбрасываем буфер заливки, чтобы контур во 2-м проходе гарантированно лёг ПОВЕРХ заливки!
                buffers.endBatch();
            }

            // ПРОХОД 2: Светящийся 3D-контур и ореол рёбер ПОВЕРХ заливки
            if (module.showOutline() && lineAlpha > 0.01F && edges > 0) {
                VertexConsumer edgeConsumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE));
                float edgeOpacity = Math.min(1.0F, Math.max(0.35F, lineAlpha));
                for (int index = 0; index < edges; index++) {
                    if (!BlockOutlineGeometry.edge(index, values)) {
                        break;
                    }
                    WorldShaderRenderer.renderShadedEdge(
                            entry, edgeConsumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            worldX, worldY, worldZ,
                            effect, baseColor, intensity, speed, edgeOpacity,
                            thickness, glowStrength, time
                    );
                }
                if (module.rim()) {
                    int rimColor = ColorUtil.fade(ColorUtil.lerp(baseColor, 0xFFFFFFFF, 0.72F),
                            Math.min(1.0F, edgeOpacity * 0.90F));
                    float rimThickness = Math.max(0.003F, thickness * 0.65F);
                    for (int index = 0; index < edges; index++) {
                        if (!BlockOutlineGeometry.edge(index, values)) {
                            break;
                        }
                        if (!isRim(values[1], values[4], topCount)) {
                            continue;
                        }
                        ShapeBuffer.addEdge(entry, edgeConsumer,
                                shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                                shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                                rimThickness, rimColor);
                    }
                }
                buffers.endBatch();
            }
            return;
        }

        // Классический режим (при выключенном тумблере Шейдер-эффекта): также 2 раздельных прохода (Заливка -> Контур поверх)
        FillMode mode = module.mode();
        float fill = Math.min(0.55F, fillOpacity * 0.65F);

        if (module.showFill() && fill > 0.004F && boxes > 0) {
            VertexConsumer fillConsumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE));
            if (glowStrength > 0.01F) {
                int haloColor = ColorUtil.fade(baseColor, Math.min(0.30F, 0.12F * glowStrength * strength));
                float pad = 0.010F * Math.min(1.5F, glowStrength);
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    ShapeBuffer.addBoxFaces(entry, fillConsumer,
                            shiftX + values[0] - pad, shiftY + values[1] - pad, shiftZ + values[2] - pad,
                            shiftX + values[3] + pad, shiftY + values[4] + pad, shiftZ + values[5] + pad, haloColor);
                }
            }
            int alpha = Math.round(255 * fill);
            if (mode == FillMode.SHADER_FILL) {
                float phase = (time * module.gradientSpeed()) % 1.0F;
                float wave = (float) (0.5D + 0.5D * Math.sin(phase * Math.PI * 2.0D));
                int bright = ColorUtil.lerp(baseColor, 0xFFFFFFFF, 0.25F + 0.30F * wave);
                int deep = ColorUtil.lerp(baseColor, 0xFF0A1424, 0.35F);
                int topColor = ColorUtil.withAlpha(bright, alpha);
                int bottomColor = ColorUtil.withAlpha(deep, Math.round(alpha * 0.55F));
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    ShapeBuffer.addGradientBoxFaces(entry, fillConsumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5], topColor, bottomColor);
                }
            } else {
                int flat = ColorUtil.withAlpha(baseColor, alpha);
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    ShapeBuffer.addBoxFaces(entry, fillConsumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5], flat);
                }
            }
            buffers.endBatch();
        }

        if (module.showOutline() && lineAlpha > 0.01F && edges > 0) {
            VertexConsumer edgeConsumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE));
            int edgeColor = ColorUtil.withAlpha(baseColor, Math.round(255 * Math.min(1.0F, lineAlpha)));
            for (int index = 0; index < edges; index++) {
                if (!BlockOutlineGeometry.edge(index, values)) {
                    break;
                }
                if (glowStrength > 0.05F) {
                    int haloEdge = ColorUtil.withAlpha(baseColor, Math.round(72 * Math.min(1.2F, glowStrength) * lineAlpha));
                    ShapeBuffer.addEdge(entry, edgeConsumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            thickness * (1.8F + glowStrength * 0.9F), haloEdge);
                }
                ShapeBuffer.addEdge(entry, edgeConsumer,
                        shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                        shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                        thickness, edgeColor);
            }
            buffers.endBatch();
        }
    }

    private static boolean isRim(float y0, float y1, int topCount) {
        for (int index = 0; index < topCount; index++) {
            float top = BOX_TOPS[index];
            if (Math.abs(y0 - top) < 0.002F && Math.abs(y1 - top) < 0.002F) {
                return true;
            }
        }
        return false;
    }
}
