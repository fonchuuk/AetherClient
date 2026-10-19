package com.aetherglass.client.render.hand;

import com.aetherglass.client.render.RenderResources;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * Обёртка над {@link SubmitNodeCollector} для перехвата и отложенной отрисовки
 * полупрозрачной стеклянной глазури и контура {@code HandsShader}.
 *
 * <p>Все стандартные вызовы руки и предмета делегируются напрямую в оригинальный
 * {@code SubmitNodeStorage} (реализующий {@code AccessRenderCommandQueue} Fabric Indigo),
 * благодаря чему ванильная текстура предмета в руке остаётся 100% видимой,
 * а полупрозрачная стеклянная глазурь и контур рисуются сразу после завершения
 * {@code renderHandsWithItems} поверх предмета без перезаписи буфера глубины.</p>
 */
public final class HandShaderCollector implements SubmitNodeCollector {
    private static final List<CustomGeometryEntry> PENDING = new ArrayList<>();

    private record CustomGeometryEntry(PoseStack.Pose pose, CustomGeometryRenderer renderer) {
    }

    private final SubmitNodeCollector delegate;

    private HandShaderCollector(SubmitNodeCollector delegate) {
        this.delegate = delegate;
    }

    public static SubmitNodeCollector wrap(SubmitNodeCollector original) {
        PENDING.clear();
        if (original == null || original instanceof HandShaderCollector) {
            return original;
        }
        return new HandShaderCollector(original);
    }

    public SubmitNodeCollector delegate() {
        return this.delegate;
    }

    /**
     * Выполняет отрисовку накопленной полупрозрачной глазури и контура {@code HandsShader}
     * сразу после того, как ванильный {@code FeatureRenderDispatcher} нарисовал руку и предмет.
     */
    public static void flushPostHands() {
        if (PENDING.isEmpty()) {
            return;
        }
        MultiBufferSource.BufferSource buffers = RenderResources.acquire();
        if (buffers == null) {
            PENDING.clear();
            return;
        }
        try {
            RenderType layer = RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE);
            VertexConsumer consumer = buffers.getBuffer(layer);
            for (int i = 0; i < PENDING.size(); i++) {
                CustomGeometryEntry entry = PENDING.get(i);
                entry.renderer().render(entry.pose(), consumer);
            }
            buffers.endBatch(layer);
        } catch (Throwable ignored) {
            RenderResources.release();
        } finally {
            PENDING.clear();
        }
    }

    private static PoseStack.Pose copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        PoseStack.Pose last = copy.last();
        last.pose().set(source.last().pose());
        last.normal().set(source.last().normal());
        return last;
    }

    @Override
    public OrderedSubmitNodeCollector order(int order) {
        return this;
    }

    @Override
    public void submitCustomGeometry(PoseStack pose, RenderType renderType, CustomGeometryRenderer renderer) {
        if (pose != null && renderer != null) {
            PENDING.add(new CustomGeometryEntry(copyPose(pose), renderer));
        }
    }

    @Override
    public void submitShadow(PoseStack pose, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        this.delegate.submitShadow(pose, radius, pieces);
    }

    @Override
    public void submitNameTag(PoseStack pose, Vec3 pos, int yOffset, Component text, boolean seeThrough,
                              int light, double distSq, CameraRenderState camera) {
        this.delegate.submitNameTag(pose, pos, yOffset, text, seeThrough, light, distSq, camera);
    }

    @Override
    public void submitText(PoseStack pose, float x, float y, FormattedCharSequence text, boolean dropShadow,
                           Font.DisplayMode displayMode, int light, int color, int backgroundColor, int outlineColor) {
        this.delegate.submitText(pose, x, y, text, dropShadow, displayMode, light, color, backgroundColor, outlineColor);
    }

    @Override
    public void submitFlame(PoseStack pose, EntityRenderState state, Quaternionf rotation) {
        this.delegate.submitFlame(pose, state, rotation);
    }

    @Override
    public void submitLeash(PoseStack pose, EntityRenderState.LeashState leash) {
        this.delegate.submitLeash(pose, leash);
    }

    @Override
    public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType renderType,
                                int light, int overlay, int tintColor, TextureAtlasSprite sprite,
                                int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumbling) {
        this.delegate.submitModel(model, state, pose, renderType, light, overlay, tintColor, sprite, outlineColor, crumbling);
    }

    @Override
    public void submitModelPart(ModelPart part, PoseStack pose, RenderType renderType,
                                int light, int overlay, TextureAtlasSprite sprite,
                                boolean sheeted, boolean hasFoil, int tintColor,
                                ModelFeatureRenderer.CrumblingOverlay crumbling, int outlineColor) {
        this.delegate.submitModelPart(part, pose, renderType, light, overlay, sprite, sheeted, hasFoil, tintColor, crumbling, outlineColor);
    }

    @Override
    public void submitBlock(PoseStack pose, BlockState state, int light, int overlay, int outlineColor) {
        this.delegate.submitBlock(pose, state, light, overlay, outlineColor);
    }

    @Override
    public void submitMovingBlock(PoseStack pose, MovingBlockRenderState state) {
        this.delegate.submitMovingBlock(pose, state);
    }

    @Override
    public void submitBlockModel(PoseStack pose, RenderType renderType, BlockStateModel model,
                                 float r, float g, float b, int light, int overlay, int outlineColor) {
        this.delegate.submitBlockModel(pose, renderType, model, r, g, b, light, overlay, outlineColor);
    }

    @Override
    public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay, int outlineColor,
                           int[] tintLayers, List<BakedQuad> quads, RenderType renderType,
                           ItemStackRenderState.FoilType foilType) {
        this.delegate.submitItem(pose, context, light, overlay, outlineColor, tintLayers, quads, renderType, foilType);
    }

    @Override
    public void submitParticleGroup(ParticleGroupRenderer renderer) {
        this.delegate.submitParticleGroup(renderer);
    }
}
