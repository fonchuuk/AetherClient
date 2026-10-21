package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.shader.WorldShaderRenderer;
import com.aetherglass.client.render.util.FrameClock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;

/**
 * Перехват отрисовки руки и удерживаемых предметов от первого лица ({@code HandsShader} + {@code ViewModel}, Блок 4).
 *
 * <p>Ключевые гарантии:
 * <ol>
 *   <li>Трансформации {@code ViewModel} применяются в начале {@code renderArmWithItem} вокруг естественного
 *       центра руки/предмета и снимаются в конце {@code renderArmWithItem};</li>
 *   <li>Шейдерная оболочка пустой руки {@code renderPlayerArm} перехватывается ДО вызова {@code pose.popPose()}
 *       и применяет смещение пивота {@code ModelPart} ({@code PartPose.offset(-5, 2, 0)} и {@code zRot = 0.1F}),
 *       как это делает {@code ModelPartFeatureRenderer}, поэтому шейдер облегает саму руку игрока;</li>
 *   <li>Оригинальный {@link SubmitNodeCollector} НЕ подменяется обёрткой: предмет в руке всегда остаётся
 *       на 100% видимым со своей оригинальной текстурой.</li>
 * </ol>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Final
    private net.minecraft.client.renderer.entity.EntityRenderDispatcher entityRenderDispatcher;
    private static final List<ModelPart.Cube> RIGHT_ARM_WIDE_CUBES = List.of(
            new ModelPart.Cube(
                    0, 0, -3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F,
                    0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, EnumSet.allOf(Direction.class)
            )
    );
    private static final List<ModelPart.Cube> LEFT_ARM_WIDE_CUBES = List.of(
            new ModelPart.Cube(
                    0, 0, -1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F,
                    0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, EnumSet.allOf(Direction.class)
            )
    );
    private static final List<ModelPart.Cube> RIGHT_ARM_SLIM_CUBES = List.of(
            new ModelPart.Cube(
                    0, 0, -2.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F,
                    0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, EnumSet.allOf(Direction.class)
            )
    );
    private static final List<ModelPart.Cube> LEFT_ARM_SLIM_CUBES = List.of(
            new ModelPart.Cube(
                    0, 0, -1.0F, -2.0F, -2.0F, 3.0F, 12.0F, 4.0F,
                    0.0F, 0.0F, 0.0F, false, 64.0F, 64.0F, EnumSet.allOf(Direction.class)
            )
    );

    private static Field layersField;
    private static Field transformField;
    private static Field displayContextField;
    private static Field quadsField;
    private static Field mutableMeshField;
    private static Method getMutableMeshMethod;
    private static Method meshForEachMethod;
    private static Method quadXMethod;
    private static Method quadYMethod;
    private static Method quadZMethod;
    private static Method quadNominalFaceMethod;
    private static Method quadLightFaceMethod;
    private static boolean reflectionInitialized;

    private static void ensureReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        try {
            for (Field f : ItemStackRenderState.class.getDeclaredFields()) {
                if (f.getType().isArray()
                        && f.getType().getComponentType() == ItemStackRenderState.LayerRenderState.class) {
                    f.setAccessible(true);
                    layersField = f;
                } else if (f.getType() == ItemDisplayContext.class) {
                    f.setAccessible(true);
                    displayContextField = f;
                }
            }
            for (Field f : ItemStackRenderState.LayerRenderState.class.getDeclaredFields()) {
                if (f.getType() == ItemTransform.class) {
                    f.setAccessible(true);
                    transformField = f;
                } else if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    quadsField = f;
                } else if (f.getName().contains("mutableMesh")) {
                    f.setAccessible(true);
                    mutableMeshField = f;
                }
            }
            try {
                getMutableMeshMethod = ItemStackRenderState.LayerRenderState.class.getMethod("fabric_getMutableMesh");
                getMutableMeshMethod.setAccessible(true);
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void aetherglass$beforeArmWithItem(AbstractClientPlayer player, float partialTick, float pitch,
                                               InteractionHand hand, float swingProgress, ItemStack item,
                                               float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                               int light, CallbackInfo info) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled() || !HandEffectRenderer.isActive()) {
            return;
        }
        HandEffectRenderer.transformHandPose(pose, player, hand, item, partialTick);
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void aetherglass$afterArmWithItem(AbstractClientPlayer player, float partialTick, float pitch,
                                              InteractionHand hand, float swingProgress, ItemStack item,
                                              float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                              int light, CallbackInfo info) {
        HandEffectRenderer.restorePose(pose);
    }

    @Inject(method = "renderPlayerArm", at = @At("TAIL"))
    private void aetherglass$afterPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light,
                                            float equipProgress, float swingProgress, HumanoidArm arm,
                                            CallbackInfo info) {
        aetherglass$emitArmShaderBox(pose, collector, arm);
    }

    @Inject(
            method = "renderMapHand",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"
            )
    )
    private void aetherglass$beforeMapHandPop(PoseStack pose, SubmitNodeCollector collector, int light,
                                              HumanoidArm arm, CallbackInfo info) {
        aetherglass$emitArmShaderBox(pose, collector, arm);
    }

    @Inject(
            method = "renderItem",
            at = @At("TAIL"),
            locals = LocalCapture.CAPTURE_FAILEXCEPTION
    )
    private void aetherglass$afterRenderItem(LivingEntity entity, ItemStack stack,
                                             ItemDisplayContext displayContext, PoseStack pose,
                                             SubmitNodeCollector collector, int light,
                                             CallbackInfo info, ItemStackRenderState state) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled() || !HandEffectRenderer.isShaderActive()
                || state == null || state.isEmpty() || collector == null) {
            return;
        }
        if (displayContext != null && !displayContext.firstPerson()) {
            return;
        }
        ensureReflection();
        if (layersField == null) {
            return;
        }
        try {
            ItemStackRenderState.LayerRenderState[] layers =
                    (ItemStackRenderState.LayerRenderState[]) layersField.get(state);
            if (layers == null || layers.length == 0) {
                return;
            }
            ItemDisplayContext ctx = displayContextField != null
                    ? (ItemDisplayContext) displayContextField.get(state)
                    : displayContext;
            boolean leftHand = (ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND);

            ShaderEffect activeEffect = HandEffectRenderer.shaderEffect();
            FaceGradientMode gradMode = HandEffectRenderer.faceGradientMode();
            int activeColor = HandEffectRenderer.shaderColor();
            float activeIntensity = HandEffectRenderer.shaderIntensity();
            float activeSpeed = HandEffectRenderer.shaderSpeed();
            float activeOpacity = Math.min(0.68F, HandEffectRenderer.shaderOpacity());
            float activeOutlineW = HandEffectRenderer.outlineWidth();
            float time = FrameClock.elapsedSeconds();

            for (ItemStackRenderState.LayerRenderState layer : layers) {
                if (layer == null) {
                    continue;
                }
                List<WorldShaderRenderer.ItemQuad> extracted = aetherglass$extractLayerQuadsReadOnly(layer);
                if (extracted.isEmpty()) {
                    continue;
                }
                ItemTransform transform = transformField != null
                        ? (ItemTransform) transformField.get(layer)
                        : ItemTransform.NO_TRANSFORM;

                PoseStack layerPose = new PoseStack();
                layerPose.last().pose().set(pose.last().pose());
                layerPose.last().normal().set(pose.last().normal());
                if (transform != null && transform != ItemTransform.NO_TRANSFORM) {
                    transform.apply(leftHand, layerPose.last());
                } else {
                    layerPose.translate(-0.5F, -0.5F, -0.5F);
                }

                collector.submitCustomGeometry(layerPose, RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE),
                        (poseMatrix, consumer) -> {
                            try {
                                var mc = net.minecraft.client.Minecraft.getInstance();
                                if (mc != null && mc.renderBuffers() != null) {
                                    var bs = mc.renderBuffers().bufferSource();
                                    bs.endBatch(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
                                    bs.endBatch(net.minecraft.client.renderer.Sheets.solidBlockSheet());
                                    bs.endBatch(net.minecraft.client.renderer.Sheets.translucentItemSheet());
                                    bs.endBatch(net.minecraft.client.renderer.Sheets.translucentBlockItemSheet());
                                }
                            } catch (Throwable ignored) {
                            }
                            WorldShaderRenderer.renderItemQuadsShader3D(
                                    poseMatrix, consumer, extracted,
                                    activeEffect, gradMode, activeColor,
                                    activeIntensity, activeSpeed, activeOpacity, activeOutlineW, time
                            );
                        });
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static List<WorldShaderRenderer.ItemQuad> aetherglass$extractLayerQuadsReadOnly(
            ItemStackRenderState.LayerRenderState layer) {
        List<WorldShaderRenderer.ItemQuad> result = new ArrayList<>(64);
        try {
            List<BakedQuad> vanillaQuads = quadsField != null
                    ? (List<BakedQuad>) quadsField.get(layer)
                    : null;
            if (vanillaQuads != null && !vanillaQuads.isEmpty()) {
                for (int i = 0; i < vanillaQuads.size(); i++) {
                    BakedQuad q = vanillaQuads.get(i);
                    if (q == null) {
                        continue;
                    }
                    Direction dir = q.direction() != null ? q.direction() : Direction.UP;
                    result.add(new WorldShaderRenderer.ItemQuad(
                            q.position0().x(), q.position0().y(), q.position0().z(),
                            q.position1().x(), q.position1().y(), q.position1().z(),
                            q.position2().x(), q.position2().y(), q.position2().z(),
                            q.position3().x(), q.position3().y(), q.position3().z(),
                            dir
                    ));
                }
                return result;
            }

            Object mesh = null;
            if (getMutableMeshMethod != null) {
                mesh = getMutableMeshMethod.invoke(layer);
            } else if (mutableMeshField != null) {
                mesh = mutableMeshField.get(layer);
            }
            if (mesh != null) {
                if (meshForEachMethod == null) {
                    meshForEachMethod = mesh.getClass().getMethod("forEach", Consumer.class);
                    meshForEachMethod.setAccessible(true);
                }
                Consumer<Object> quadConsumer = quad -> {
                    try {
                        if (quadXMethod == null) {
                            Class<?> qc = quad.getClass();
                            quadXMethod = qc.getMethod("x", int.class);
                            quadYMethod = qc.getMethod("y", int.class);
                            quadZMethod = qc.getMethod("z", int.class);
                            quadNominalFaceMethod = qc.getMethod("nominalFace");
                            quadLightFaceMethod = qc.getMethod("lightFace");
                            quadXMethod.setAccessible(true);
                            quadYMethod.setAccessible(true);
                            quadZMethod.setAccessible(true);
                            quadNominalFaceMethod.setAccessible(true);
                            quadLightFaceMethod.setAccessible(true);
                        }
                        Direction dir = (Direction) quadNominalFaceMethod.invoke(quad);
                        if (dir == null) {
                            dir = (Direction) quadLightFaceMethod.invoke(quad);
                        }
                        if (dir == null) {
                            dir = Direction.UP;
                        }
                        float x0 = (float) quadXMethod.invoke(quad, 0);
                        float y0 = (float) quadYMethod.invoke(quad, 0);
                        float z0 = (float) quadZMethod.invoke(quad, 0);
                        float x1 = (float) quadXMethod.invoke(quad, 1);
                        float y1 = (float) quadYMethod.invoke(quad, 1);
                        float z1 = (float) quadZMethod.invoke(quad, 1);
                        float x2 = (float) quadXMethod.invoke(quad, 2);
                        float y2 = (float) quadYMethod.invoke(quad, 2);
                        float z2 = (float) quadZMethod.invoke(quad, 2);
                        float x3 = (float) quadXMethod.invoke(quad, 3);
                        float y3 = (float) quadYMethod.invoke(quad, 3);
                        float z3 = (float) quadZMethod.invoke(quad, 3);
                        result.add(new WorldShaderRenderer.ItemQuad(
                                x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, dir
                        ));
                    } catch (Throwable ignored) {
                    }
                };
                meshForEachMethod.invoke(mesh, quadConsumer);
            }
        } catch (Throwable ignored) {
        }
        return result;
    }

    private void aetherglass$emitArmShaderBox(PoseStack pose, SubmitNodeCollector collector, HumanoidArm arm) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled() || !HandEffectRenderer.isShaderActive()
                || pose == null || collector == null) {
            return;
        }
        final ShaderEffect activeEffect = HandEffectRenderer.shaderEffect();
        final FaceGradientMode gradMode = HandEffectRenderer.faceGradientMode();
        final int activeColor = HandEffectRenderer.shaderColor();
        final float activeIntensity = HandEffectRenderer.shaderIntensity();
        final float activeSpeed = HandEffectRenderer.shaderSpeed();
        final float activeOpacity = Math.min(0.75F, HandEffectRenderer.shaderOpacity());
        final float activeOutlineW = HandEffectRenderer.outlineWidth();
        final float time = FrameClock.elapsedSeconds();

        boolean left = (arm == HumanoidArm.LEFT);
        net.minecraft.client.player.LocalPlayer localPlayer = net.minecraft.client.Minecraft.getInstance().player;
        ModelPart liveArmPart = null;
        if (localPlayer != null && this.entityRenderDispatcher != null) {
            var avatarRenderer = this.entityRenderDispatcher.getPlayerRenderer(localPlayer);
            if (avatarRenderer != null && avatarRenderer.getModel() != null) {
                var playerModel = avatarRenderer.getModel();
                playerModel.leftSleeve.visible = false;
                playerModel.rightSleeve.visible = false;
                liveArmPart = left ? playerModel.leftArm : playerModel.rightArm;
            }
        }

        if (liveArmPart != null) {
            final boolean[] emitted = {false};
            liveArmPart.visit(pose, (cubePose, path, index, cube) -> {
                if (emitted[0]) {
                    return;
                }
                emitted[0] = true;
                PoseStack captured = new PoseStack();
                captured.last().pose().set(cubePose.pose());
                captured.last().normal().set(cubePose.normal());
                List<ModelPart.Cube> singleCube = List.of(cube);
                collector.submitCustomGeometry(captured, RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE), (poseMatrix, consumer) -> {
                    WorldShaderRenderer.renderHandPartShader3D(
                            poseMatrix, consumer, singleCube,
                            activeEffect, gradMode, activeColor,
                            activeIntensity, activeSpeed, activeOpacity, activeOutlineW,
                            time
                    );
                });
            });
            return;
        }

        float pivotX = (left ? 5.0F : -5.0F) / 16.0F;
        float pivotY = 2.0F / 16.0F;
        PoseStack armPose = new PoseStack();
        armPose.last().pose().set(pose.last().pose());
        armPose.last().normal().set(pose.last().normal());
        armPose.translate(pivotX, pivotY, 0.0F);
        armPose.mulPose(Axis.ZP.rotation(0.1F));

        boolean slim = localPlayer != null && localPlayer.getSkin() != null
                && localPlayer.getSkin().model() == net.minecraft.world.entity.player.PlayerModelType.SLIM;
        List<ModelPart.Cube> armCubes = left
                ? (slim ? LEFT_ARM_SLIM_CUBES : LEFT_ARM_WIDE_CUBES)
                : (slim ? RIGHT_ARM_SLIM_CUBES : RIGHT_ARM_WIDE_CUBES);
        collector.submitCustomGeometry(armPose, RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE), (poseMatrix, consumer) -> {
            WorldShaderRenderer.renderHandPartShader3D(
                    poseMatrix, consumer, armCubes,
                    activeEffect, gradMode, activeColor,
                    activeIntensity, activeSpeed, activeOpacity, activeOutlineW,
                    time
            );
        });
    }
}
