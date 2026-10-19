package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.shader.WorldShaderRenderer;
import com.aetherglass.client.render.util.FrameClock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
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
import java.util.List;
import java.util.function.Consumer;

/**
 * Перехват отрисовки руки и удерживаемых предметов от первого лица ({@code HandsShader}, Блок 4.2).
 *
 * <p>Ключевые гарантии:
 * <ol>
 *   <li>Оригинальный {@link SubmitNodeCollector} ({@code SubmitNodeStorage}) НЕ подменяется обёрткой
 *       и НЕ перехватывается через {@code @Redirect}: ванильный {@code ItemStackRenderState.submit}
 *       и Fabric Indigo отрабатывают на 100% штатно, поэтому предмет в руке всегда остаётся
 *       полностью видимым со своей оригинальной текстурой;</li>
 *   <li>Полупрозрачный шейдерный слой и контур добавляются через штатный
 *       {@link SubmitNodeCollector#submitCustomGeometry}, который {@code FeatureRenderDispatcher}
 *       выполняет в {@code CustomFeatureRenderer} строго ПОСЛЕ {@code ItemFeatureRenderer}
 *       поверх уже нарисованного предмета.</li>
 * </ol>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
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

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void aetherglass$beforeHands(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                         LocalPlayer player, int light, CallbackInfo info) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled() || !HandEffectRenderer.isActive()) {
            return;
        }
        HandEffectRenderer.transformPose(pose, player, partialTick);
    }

    @Inject(method = "renderPlayerArm", at = @At("RETURN"))
    private void aetherglass$afterPlayerArm(PoseStack pose, SubmitNodeCollector collector, int light,
                                            float equipProgress, float swingProgress, HumanoidArm arm,
                                            CallbackInfo info) {
        if (!HandEffectRenderer.isShaderActive()) {
            return;
        }
        aetherglass$emitArmShaderBox(pose, collector);
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderRightHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void aetherglass$afterRightHand(AbstractClientPlayer player, float partialTick, float pitch,
                                            InteractionHand hand, float swingProgress, ItemStack item,
                                            float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                            int light, CallbackInfo info) {
        if (HandEffectRenderer.isShaderActive()) {
            aetherglass$emitArmShaderBox(pose, collector);
        }
    }

    @Inject(
            method = "renderArmWithItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/player/AvatarRenderer;renderLeftHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/resources/Identifier;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void aetherglass$afterLeftHand(AbstractClientPlayer player, float partialTick, float pitch,
                                           InteractionHand hand, float swingProgress, ItemStack item,
                                           float equipProgress, PoseStack pose, SubmitNodeCollector collector,
                                           int light, CallbackInfo info) {
        if (HandEffectRenderer.isShaderActive()) {
            aetherglass$emitArmShaderBox(pose, collector);
        }
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
        if (!HandEffectRenderer.isShaderActive() || state == null || state.isEmpty() || collector == null) {
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
            // Ограничиваем альфу глазури поверх предмета, чтобы текстура предмета была 100% читаемой
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
                        (poseMatrix, consumer) -> WorldShaderRenderer.renderItemQuadsShader3D(
                                poseMatrix, consumer, extracted,
                                activeEffect, gradMode, activeColor,
                                activeIntensity, activeSpeed, activeOpacity, activeOutlineW, time
                        ));
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

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void aetherglass$afterHands(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                        LocalPlayer player, int light, CallbackInfo info) {
        HandEffectRenderer.restorePose(pose);
    }

    private static void aetherglass$emitArmShaderBox(PoseStack pose, SubmitNodeCollector collector) {
        final ShaderEffect activeEffect = HandEffectRenderer.shaderEffect();
        final FaceGradientMode gradMode = HandEffectRenderer.faceGradientMode();
        final int activeColor = HandEffectRenderer.shaderColor();
        final float activeIntensity = HandEffectRenderer.shaderIntensity();
        final float activeSpeed = HandEffectRenderer.shaderSpeed();
        final float activeOpacity = Math.min(0.68F, HandEffectRenderer.shaderOpacity());
        final float activeOutlineW = HandEffectRenderer.outlineWidth();
        final float time = FrameClock.elapsedSeconds();

        ModelPart.Cube armCube = new ModelPart.Cube(
                0, 0, -2.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F,
                0.08F, 0.08F, 0.08F, false, 64.0F, 64.0F, Direction.stream().collect(java.util.stream.Collectors.toSet())
        );
        List<ModelPart.Cube> cubes = List.of(armCube);

        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE), (poseMatrix, consumer) -> {
            WorldShaderRenderer.renderHandPartShader3D(
                    poseMatrix, consumer, cubes,
                    activeEffect, gradMode, activeColor,
                    activeIntensity, activeSpeed, activeOpacity, activeOutlineW,
                    time
            );
        });
    }
}
