package com.aetherglass.client.render.block;

import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.material.FillMode;
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
 * Отрисовка выделенного блока ({@code BlockShader}): 16 процедурных шейдер-эффектов,
 * полупрозрачная заливка, контур и мягкое свечение по реальной геометрии {@code VoxelShape}.
 *
 * <p>Форма берётся из настоящей формы блока ({@link BlockOutlineGeometry}), поэтому
 * шейдер повторяет контуры ступенек, плит, заборов и факелов, учитывает мировые
 * координаты, нормали граней и локальные UV, не меняя игровых свойств блока.</p>
 */
public final class BlockEffectRenderer {
    /** Многоразовый буфер для шести чисел: рёбра и боксы читаются через него, без выделений. */
    private static final float[] VALUES = new float[6];
    /** Верх каждой части формы: по нему определяется кромка для блика. */
    private static final float[] BOX_TOPS = new float[BlockOutlineGeometry.MAX_BOXES];

    private BlockEffectRenderer() {
    }

    /**
     * Рисует подсветку и выбранный шейдер-эффект ({@code BlockShader}) на блоке под прицелом.
     */
    public static void render(PoseStack pose, Camera camera, BlockEffectsModule module) {
        BlockPos position = module.outlined();
        float strength = module.outlineStrength();
        if (position == null || strength <= 0.01F) {
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
        // Геометрия пересчитывается только при смене состояния блока — внутри кадра никаких выделений.
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

        VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityTranslucent(HandEffectRenderer.WHITE));
        PoseStack.Pose entry = pose.last();
        float[] values = VALUES;
        float time = module.effectTime();

        if (module.useShaderEffect()) {
            ShaderEffect effect = module.shaderEffect();
            int baseColor = module.shaderColor();
            float intensity = module.shaderIntensity();
            float speed = module.shaderSpeed();
            float effectiveOpacity = module.shaderOpacity() * strength;
            float thickness = module.outlineWidth();
            float haloStrength = module.glow();

            if (module.mode() != FillMode.OUTLINE && boxes > 0) {
                int gridSteps = boxes <= 2 ? WorldShaderRenderer.DEFAULT_GRID_STEPS : (boxes <= 6 ? 4 : 3);
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    WorldShaderRenderer.renderShadedBox(
                            entry, consumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            worldX + values[0], worldY + values[1], worldZ + values[2],
                            effect, baseColor, intensity, speed, effectiveOpacity,
                            time, gridSteps
                    );
                }
            }

            if (edges > 0) {
                float edgeOpacity = Math.min(1.0F, effectiveOpacity * 1.15F);
                for (int index = 0; index < edges; index++) {
                    if (!BlockOutlineGeometry.edge(index, values)) {
                        break;
                    }
                    WorldShaderRenderer.renderShadedEdge(
                            entry, consumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            worldX, worldY, worldZ,
                            effect, baseColor, intensity, speed, edgeOpacity,
                            thickness, haloStrength, time
                    );
                }
            }
            buffers.endBatch();
            return;
        }

        int color = module.resolvedColor();
        FillMode mode = module.mode();
        float fill = module.fillAlpha() * strength;
        float line = module.outlineAlpha() * strength;
        float halo = module.glow() * strength;
        float thickness = module.outlineWidth();

        int topCount = 0;
        for (int index = 0; index < boxes; index++) {
            if (!BlockOutlineGeometry.box(index, values)) {
                break;
            }
            BOX_TOPS[topCount++] = values[4];
        }

        if (halo > 0.01F && boxes > 0) {
            int haloColor = ColorUtil.fade(color, Math.round(255 * 0.16F * halo));
            float pad = 0.03F;
            for (int index = 0; index < boxes; index++) {
                if (!BlockOutlineGeometry.box(index, values)) {
                    break;
                }
                ShapeBuffer.addBoxFaces(entry, consumer,
                        shiftX + values[0] - pad, shiftY + values[1] - pad, shiftZ + values[2] - pad,
                        shiftX + values[3] + pad, shiftY + values[4] + pad, shiftZ + values[5] + pad, haloColor);
            }
        }

        if (mode != FillMode.OUTLINE && fill > 0.004F && boxes > 0) {
            int alpha = Math.round(255 * fill);
            if (mode == FillMode.SHADER_FILL) {
                float phase = (time * module.gradientSpeed()) % 1.0F;
                float wave = (float) (0.5D + 0.5D * Math.sin(phase * Math.PI * 2.0D));
                int bright = ColorUtil.lerp(color, 0xFFFFFFFF, 0.20F + 0.35F * wave);
                int deep = ColorUtil.lerp(color, 0xFF000000, 0.40F);
                int topColor = ColorUtil.fade(bright, alpha);
                int bottomColor = ColorUtil.fade(deep, Math.round(alpha * 0.45F));
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    ShapeBuffer.addGradientBoxFaces(entry, consumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5], topColor, bottomColor);
                }
            } else {
                int flat = ColorUtil.fade(color, alpha);
                for (int index = 0; index < boxes; index++) {
                    if (!BlockOutlineGeometry.box(index, values)) {
                        break;
                    }
                    ShapeBuffer.addBoxFaces(entry, consumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5], flat);
                }
            }
        }

        if (line > 0.01F && edges > 0) {
            int edgeColor = ColorUtil.fade(color, Math.round(255 * line));
            for (int index = 0; index < edges; index++) {
                if (!BlockOutlineGeometry.edge(index, values)) {
                    break;
                }
                ShapeBuffer.addEdge(entry, consumer,
                        shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                        shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                        thickness, edgeColor);
            }
            if (module.rim()) {
                int rimColor = ColorUtil.fade(ColorUtil.lerp(color, 0xFFFFFFFF, 0.75F),
                        Math.round(255 * line * 0.9F));
                float rimThickness = thickness * 0.75F;
                for (int index = 0; index < edges; index++) {
                    if (!BlockOutlineGeometry.edge(index, values)) {
                        break;
                    }
                    if (!isRim(values[1], values[4], topCount)) {
                        continue;
                    }
                    ShapeBuffer.addEdge(entry, consumer,
                            shiftX + values[0], shiftY + values[1], shiftZ + values[2],
                            shiftX + values[3], shiftY + values[4], shiftZ + values[5],
                            rimThickness, rimColor);
                }
            }
        }
        buffers.endBatch();
    }

    private static boolean isRim(float y0, float y1, int topCount) {
        for (int index = 0; index < topCount; index++) {
            if (Math.abs(y0 - BOX_TOPS[index]) < 0.002F && Math.abs(y1 - BOX_TOPS[index]) < 0.002F) {
                return true;
            }
        }
        return false;
    }
}
