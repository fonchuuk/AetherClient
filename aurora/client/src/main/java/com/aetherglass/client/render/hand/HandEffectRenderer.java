package com.aetherglass.client.render.hand;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.shader.GuiShaderPreviewRenderer;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.shader.WorldShaderRenderer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Эффекты руки ({@code HandsShader}): 16 процедурных шейдер-эффектов, движение,
 * 3D-оболочка вокруг рук и удерживаемых предметов и «отсвет» на экран.
 *
 * <p>Руку и предметы по-прежнему рисует игра своим рендером и со своими текстурами.
 * Мы накладываем собственное преобразование к стеку матриц и добавляем 3D/2D
 * процедурный шейдер-эффект ({@link ShaderEffect}), не повреждая геометрию или
 * анимации использования предметов, удара, блока или смены слота.</p>
 */
public final class HandEffectRenderer {
    /** Текстура-заготовка: полностью белый спрайт для цветной геометрии. */
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath("aetherglass",
            "textures/particle/white.png");

    private static HandStyle style = HandStyle.GLOW;
    private static HandAnimation animation = HandAnimation.SWING;
    private static int color = 0xFF7FD4FF;
    private static float intensity = 0.6F;
    private static float appearSpeed = 6.0F;
    private static float swingStrength = 0.5F;

    private static boolean useShader = true;
    private static ShaderEffect shaderEffect = ShaderEffect.LIQUID_GLASS;
    private static int shaderColor = ShaderEffect.LIQUID_GLASS.defaultColor();
    private static float shaderIntensity = 1.0F;
    private static float shaderSpeed = 1.0F;
    private static float shaderOpacity = 0.75F;

    private static Item lastItem;
    private static float appear = 1.0F;
    private static float swing;
    private static boolean holdingMainItem;
    /** Мы положили слой в стек матриц и обязаны его снять. */
    private static boolean posePushed;

    private HandEffectRenderer() {
    }

    /** Модуль передаёт свои настройки стиля и движения. */
    public static void configure(HandStyle styleValue, HandAnimation animationValue, int colorValue,
                                 float intensityValue, float appearSpeedValue, float swingStrengthValue) {
        style = styleValue == null ? HandStyle.NONE : styleValue;
        animation = animationValue == null ? HandAnimation.NONE : animationValue;
        color = colorValue;
        intensity = Math.max(0.0F, Math.min(1.0F, intensityValue));
        appearSpeed = Math.max(0.5F, Math.min(20.0F, appearSpeedValue));
        swingStrength = Math.max(0.0F, Math.min(1.5F, swingStrengthValue));
    }

    /** Модуль передаёт настройки одного из 16 шейдер-эффектов рук (HandsShader). */
    public static void configureShader(boolean enabled, ShaderEffect effect, int colorValue,
                                       float intensityValue, float speedValue, float opacityValue) {
        useShader = enabled;
        shaderEffect = effect == null ? ShaderEffect.LIQUID_GLASS : effect;
        shaderColor = colorValue;
        shaderIntensity = Math.max(0.1F, Math.min(2.0F, intensityValue));
        shaderSpeed = Math.max(0.1F, Math.min(3.0F, speedValue));
        shaderOpacity = Math.max(0.05F, Math.min(1.0F, opacityValue));
    }

    /** Сбрасывает состояние при выключении модуля: рука мгновенно становится обычной. */
    public static void reset() {
        appear = 1.0F;
        swing = 0.0F;
        lastItem = null;
        holdingMainItem = false;
        posePushed = false;
    }

    /** Обновляет состояние анимации; вызывается один раз за кадр. */
    public static void update(float deltaSeconds, LocalPlayer player) {
        Item current = null;
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            current = stack.isEmpty() ? null : stack.getItem();
            holdingMainItem = !stack.isEmpty();
        } else {
            holdingMainItem = false;
        }
        if (current != lastItem) {
            lastItem = current;
            appear = 0.0F;
        }
        appear = Math.min(1.0F, appear + deltaSeconds * appearSpeed);
        float targetSwing = player == null ? 0.0F : player.getAttackAnim(1.0F);
        swing += (targetSwing - swing) * Math.min(1.0F, deltaSeconds * 12.0F);
    }

    /**
     * Применяет движение к стеку матриц перед тем, как игра начнёт рисовать руку.
     */
    public static void applyPose(PoseStack pose, LocalPlayer player, float partialTick) {
        pose.pushPose();
        posePushed = true;
        try {
            applyPoseInner(pose, player, partialTick);
        } catch (Throwable throwable) {
            posePushed = false;
            pose.popPose();
            throw throwable;
        }
    }

    private static void applyPoseInner(PoseStack pose, LocalPlayer player, float partialTick) {
        float hidden = 1.0F - Easing.easeOutCubic(appear);
        if (hidden > 0.001F) {
            pose.translate(0.12F * hidden, -0.55F * hidden, 0.10F * hidden);
            float scale = 1.0F - 0.25F * hidden;
            pose.scale(scale, scale, scale);
        }
        switch (animation) {
            case SWING -> pose.mulPose(Axis.XP.rotationDegrees(-swing * 45.0F * swingStrength));
            case HOLD -> {
                if (player != null && player.isUsingItem()) {
                    pose.translate(0.0F, -0.06F, 0.10F);
                    pose.mulPose(Axis.YP.rotationDegrees(6.0F * swingStrength));
                }
            }
            case BOB -> {
                float bob = (float) Math.sin(FrameClock.elapsedSeconds() * 2.4D) * 0.02F * swingStrength;
                pose.translate(0.0F, bob, 0.0F);
            }
            default -> {
            }
        }
        if (partialTick < 0.0F) {
            return;
        }
    }

    /**
     * Возвращает стек матриц в исходное состояние.
     */
    public static void restorePose(PoseStack pose) {
        if (!posePushed) {
            return;
        }
        posePushed = false;
        pose.popPose();
    }

    /**
     * Добавляет 3D-шейдер ({@code HandsShader}) или классический отсвет рядом с рукой и предметом.
     */
    public static void submitAccent(SubmitNodeCollector collector, PoseStack pose) {
        if (useShader) {
            if (shaderOpacity <= 0.01F) {
                return;
            }
            final ShaderEffect activeEffect = shaderEffect;
            final int activeColor = shaderColor;
            final float activeIntensity = shaderIntensity;
            final float activeSpeed = shaderSpeed;
            final float activeOpacity = shaderOpacity * (0.45F + 0.55F * Easing.easeOutCubic(appear));
            final float equipProgress = 1.0F - appear;
            final float swingProgress = swing;
            final boolean hasItem = holdingMainItem;
            final float time = FrameClock.elapsedSeconds();

            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (poseMatrix, consumer) -> {
                WorldShaderRenderer.renderHandShader3D(
                        poseMatrix, consumer,
                        1.0F, equipProgress, swingProgress, hasItem,
                        activeEffect, activeColor, activeIntensity, activeSpeed, activeOpacity,
                        time
                );
            });
            return;
        }

        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        int accent = ColorUtil.fade(ColorController.resolve(color), 0.30F * intensity);
        int edge = ColorUtil.fade(ColorController.resolve(color), 0.0F);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (poseMatrix, consumer) -> {
            float half = 0.9F;
            float depth = -0.55F;
            float lift = -0.25F;
            ShapeBuffer.addGradientQuad(poseMatrix, consumer,
                    -half, lift - half, depth, half, lift - half, depth, half, lift, depth, -half, lift, depth,
                    edge, accent);
            ShapeBuffer.addGradientQuad(poseMatrix, consumer,
                    -half, lift, depth, half, lift, depth, half, lift + half, depth, -half, lift + half, depth,
                    accent, edge);
        });
        emitRim(collector, pose);
    }

    /** Тонкая цветная рамка по краю руки: стиль «контур». */
    private static void emitRim(SubmitNodeCollector collector, PoseStack pose) {
        if (style != HandStyle.OUTLINE) {
            return;
        }
        int rim = ColorUtil.fade(ColorController.resolve(color), 0.85F * intensity);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (poseMatrix, consumer) -> {
            float half = 0.42F;
            float depth = -0.52F;
            float lift = -0.30F;
            float thickness = 0.012F;
            ShapeBuffer.addBox(poseMatrix, consumer, -half, lift - half, depth, half, lift - half + thickness, depth,
                    rim);
            ShapeBuffer.addBox(poseMatrix, consumer, -half, lift + half - thickness, depth, half, lift + half, depth,
                    rim);
            ShapeBuffer.addBox(poseMatrix, consumer, -half, lift - half, depth, -half + thickness, lift + half, depth,
                    rim);
            ShapeBuffer.addBox(poseMatrix, consumer, half - thickness, lift - half, depth, half, lift + half, depth,
                    rim);
        });
    }

    /**
     * Рисуется поверх уже нарисованной руки в координатах экрана.
     */
    public static void drawOverlay(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui) {
            return;
        }

        if (useShader) {
            if (shaderOpacity <= 0.01F) {
                return;
            }
            GuiShaderPreviewRenderer.renderFirstPersonHandOverlay(
                    graphics, screenWidth, screenHeight,
                    shaderEffect, shaderColor,
                    shaderIntensity, shaderSpeed,
                    shaderOpacity * 0.52F,
                    swing, swing * 0.5F, FrameClock.elapsedSeconds()
            );
            return;
        }

        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        int accent = ColorController.resolve(color);
        int width = Math.round(Math.min(screenWidth * 0.34F, 190.0F));
        int height = Math.round(Math.min(screenHeight * 0.34F, 150.0F));
        int x = screenWidth - width - Math.round(screenWidth * 0.06F);
        int y = screenHeight - height - Math.round(screenHeight * 0.05F);
        float alpha = 0.55F * intensity;

        if (style == HandStyle.GLOW) {
            for (int layer = 4; layer >= 1; layer--) {
                int spread = layer * Math.round(width * 0.06F);
                int ring = ColorUtil.fade(accent, alpha * (0.16F - layer * 0.03F));
                if (ColorUtil.alpha(ring) <= 0) {
                    continue;
                }
                DrawUtil.fillRounded(graphics, x - spread, y - spread, width + spread * 2, height + spread * 2,
                        Math.round((width + spread * 2) * 0.22F), ring);
            }
        } else if (style == HandStyle.OUTLINE) {
            DrawUtil.fillRoundedBordered(graphics, x, y, width, height, Math.round(width * 0.14F), 0,
                    ColorUtil.fade(accent, alpha), Math.max(1, Math.round(2.0F + 2.0F * intensity)));
        } else {
            DrawUtil.fillRoundedGradient(graphics, x, y + height / 2, width, height / 2,
                    Math.round(width * 0.14F), ColorUtil.fade(accent, alpha),
                    ColorUtil.fade(accent, 0.0F));
            DrawUtil.fillRoundedGradient(graphics, x, y, width, height / 2, Math.round(width * 0.14F),
                    ColorUtil.fade(ColorUtil.brighten(accent, 0.35F), alpha * 0.7F),
                    ColorUtil.fade(accent, alpha));
        }
    }

    /**
     * Отладочная строка для экрана «О клиенте»: показывает, что состояние руки живое.
     */
    public static String debugState() {
        return (int) (appear * 100) + "% / размах " + (int) (swing * 100) + "%";
    }

    /** Прозрачность и размер «отсвета»: используются миксином для проверки активности. */
    public static boolean isActive() {
        if (useShader) {
            return shaderOpacity > 0.01F;
        }
        return style != HandStyle.NONE && intensity > 0.01F;
    }
}
