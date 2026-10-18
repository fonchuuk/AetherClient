package com.aetherglass.client.render.block;

import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.material.FillMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Подсветка блока под прицелом (часть 5.2).
 *
 * <p>Форма берётся из настоящей формы блока, поэтому подсветка повторяет контуры
 * ступенек, плит и факелов, а не рисует ровный куб поверх всего. Цвет приходит из
 * общего контроллера цвета, значит радуга перекрашивает подсветку вместе со всем
 * остальным.</p>
 */
public final class BlockEffectRenderer {
    /** Многоразовый буфер для шести чисел: рёбра и боксы читаются через него, без выделений. */
    private static final float[] VALUES = new float[6];
    /** Верх каждой части формы: по нему определяется кромка для блика. */
    private static final float[] BOX_TOPS = new float[BlockOutlineGeometry.MAX_BOXES];

    private BlockEffectRenderer() {
    }

    /**
     * Рисует подсветку выбранного блока.
     *
     * <p>Геометрия берётся из настоящей формы блока, поэтому контур повторяет
     * ступеньку, плиту, забор или лестницу, а не рисует куб поверх всего блока.
     * Игровые свойства блока не меняются: меняется только отрисовка.</p>
     *
     * @param pose   стек матриц события отрисовки мира
     * @param camera камера: её положение нужно, чтобы перевести координаты мира в кадр
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
        // Геометрия пересчитывается только при смене блока — внутри кадра никаких выделений.
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
        float shiftX = (float) (position.getX() - origin.x);
        float shiftY = (float) (position.getY() - origin.y);
        float shiftZ = (float) (position.getZ() - origin.z);

        int color = module.resolvedColor();
        FillMode mode = module.mode();
        float fill = module.fillAlpha() * strength;
        float line = module.outlineAlpha() * strength;
        float halo = module.glow() * strength;
        float thickness = module.outlineWidth();
        float time = module.effectTime();

        VertexConsumer consumer = buffers.getBuffer(RenderTypes.entityTranslucent(HandEffectRenderer.WHITE));
        PoseStack.Pose entry = pose.last();
        float[] values = VALUES;

        // Верх каждой части формы запоминается заранее: по нему ищется кромка для блика.
        int topCount = 0;
        for (int index = 0; index < boxes; index++) {
            if (!BlockOutlineGeometry.box(index, values)) {
                break;
            }
            BOX_TOPS[topCount++] = values[4];
        }

        // Свечение: те же грани формы, чуть шире и заметно прозрачнее — мягкий ореол.
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

        // Заливка по граням: ровная или с градиентом и бегущим бликом.
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

        // Контур по рёбрам настоящей формы блока.
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
    /** Верхнее ли это ребро: оба конца лежат на верхней грани какой-то части формы. */
    private static boolean isRim(float y0, float y1, int topCount) {
        for (int index = 0; index < topCount; index++) {
            if (Math.abs(y0 - BOX_TOPS[index]) < 0.002F && Math.abs(y1 - BOX_TOPS[index]) < 0.002F) {
                return true;
            }
        }
        return false;
    }
}
