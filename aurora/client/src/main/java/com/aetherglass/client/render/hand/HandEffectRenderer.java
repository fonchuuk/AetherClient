package com.aetherglass.client.render.hand;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.shader.FaceGradientMode;
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
 * Рендерер эффектов руки и предметов от первого лица ({@code HandsShader}).
 *
 * <p>Оригинальная текстура предмета и руки рисуется игрой без изменений (100% видима),
 * а поверх неё накладывается лёгкая полупрозрачная стеклянная глазурь (15–25% альфа)
 * и аккуратный светящийся контур по силуэту.</p>
 */
public final class HandEffectRenderer {
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
    private static FaceGradientMode faceGradientMode = FaceGradientMode.MENISCUS_DROP;
    private static int shaderColor = ShaderEffect.LIQUID_GLASS.defaultColor();
    private static float shaderIntensity = 1.0F;
    private static float shaderSpeed = 1.0F;
    private static float shaderOpacity = 0.58F;
    private static float outlineWidth = 0.012F;
    private static float modelAlpha = 1.0F;

    private static Item lastItem;
    private static float appear = 1.0F;
    private static float swing;
    private static boolean holdingMainItem;
    private static boolean posePushed;

    private HandEffectRenderer() {
    }

    public static void configure(HandStyle styleValue, HandAnimation animationValue, int colorValue,
                                 float intensityValue, float appearSpeedValue, float swingStrengthValue) {
        style = styleValue == null ? HandStyle.NONE : styleValue;
        animation = animationValue == null ? HandAnimation.NONE : animationValue;
        color = colorValue;
        intensity = Math.max(0.0F, Math.min(1.0F, intensityValue));
        appearSpeed = Math.max(0.5F, Math.min(20.0F, appearSpeedValue));
        swingStrength = Math.max(0.0F, Math.min(1.5F, swingStrengthValue));
    }

    public static void configureShader(boolean enabled, ShaderEffect effect, int colorValue,
                                       float intensityValue, float speedValue, float opacityValue) {
        configureShader(enabled, effect, faceGradientMode, colorValue, intensityValue, speedValue, opacityValue, outlineWidth, modelAlpha);
    }

    public static void configureShader(boolean enabled, ShaderEffect effect, FaceGradientMode modeValue,
                                       int colorValue, float intensityValue, float speedValue, float opacityValue,
                                       float outlineWidthValue, float modelAlphaValue) {
        useShader = enabled;
        shaderEffect = effect == null ? ShaderEffect.LIQUID_GLASS : effect;
        faceGradientMode = modeValue == null ? FaceGradientMode.MENISCUS_DROP : modeValue;
        shaderColor = colorValue;
        shaderIntensity = Math.max(0.1F, Math.min(2.0F, intensityValue));
        shaderSpeed = Math.max(0.1F, Math.min(3.0F, speedValue));
        shaderOpacity = Math.max(0.05F, Math.min(1.0F, opacityValue));
        outlineWidth = Math.max(0.002F, Math.min(0.05F, outlineWidthValue));
        modelAlpha = Math.max(0.15F, Math.min(1.0F, modelAlphaValue));
    }

    public static void reset() {
        appear = 1.0F;
        swing = 0.0F;
        lastItem = null;
        holdingMainItem = false;
        posePushed = false;
    }

    public static void update(float deltaSeconds, LocalPlayer player) {
        if (player == null) {
            holdingMainItem = false;
            return;
        }
        ItemStack held = player.getMainHandItem();
        holdingMainItem = !held.isEmpty();
        Item current = held.isEmpty() ? null : held.getItem();
        if (current != lastItem) {
            lastItem = current;
            appear = 0.0F;
        }
        appear = Math.min(1.0F, appear + deltaSeconds * appearSpeed);
        if (player.swinging) {
            swing = (float) Math.sin(player.getAttackAnim(1.0F) * Math.PI);
        } else {
            swing = Math.max(0.0F, swing - deltaSeconds * 6.0F);
        }
    }

    public static void transformPose(PoseStack pose, LocalPlayer player, float partialTick) {
        posePushed = false;
        if (animation == HandAnimation.NONE && appear >= 0.999F) {
            return;
        }
        pose.pushPose();
        posePushed = true;
        float shown = Easing.easeOutQuint(appear);
        float drop = (1.0F - shown) * 0.35F;
        pose.translate(0.0F, -drop, 0.0F);
        if (appear < 0.999F) {
            float scale = 0.88F + 0.12F * shown;
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
    }

    public static void restorePose(PoseStack pose) {
        if (!posePushed) {
            return;
        }
        posePushed = false;
        pose.popPose();
    }

    public static void submitAccent(SubmitNodeCollector collector, PoseStack pose) {
        if (useShader) {
            // 3D-глазурь и контур добавляются через ItemInHandRendererMixin по реальным полигонам предмета и руки
            return;
        }
        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        int accent = ColorUtil.fade(ColorController.resolve(color), 0.25F * intensity);
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
    }

    public static void drawOverlay(GuiGraphics graphics, int screenWidth, int screenHeight) {
        // Не рисуем 2D-ореол поверх экрана при использовании 3D HandsShader, чтобы предмет оставался идеально читаемым
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui || useShader) {
            return;
        }
        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        int accent = ColorController.resolve(color);
        int width = Math.round(Math.min(screenWidth * 0.30F, 160.0F));
        int height = Math.round(Math.min(screenHeight * 0.30F, 130.0F));
        int x = screenWidth - width - Math.round(screenWidth * 0.06F);
        int y = screenHeight - height - Math.round(screenHeight * 0.05F);
        float alpha = 0.35F * intensity;
        DrawUtil.fillRounded(graphics, x, y, width, height, Math.round(width * 0.20F), ColorUtil.fade(accent, alpha * 0.25F));
    }

    public static String debugState() {
        return (int) (appear * 100) + "% / размах " + (int) (swing * 100) + "%";
    }

    public static boolean isActive() {
        if (useShader) {
            return shaderOpacity > 0.01F;
        }
        return style != HandStyle.NONE && intensity > 0.01F;
    }

    public static boolean isShaderActive() {
        return useShader && shaderOpacity > 0.01F;
    }

    public static ShaderEffect shaderEffect() {
        return shaderEffect;
    }

    public static FaceGradientMode faceGradientMode() {
        return faceGradientMode;
    }

    public static int shaderColor() {
        return ColorController.resolve(shaderColor);
    }

    public static float shaderIntensity() {
        return shaderIntensity;
    }

    public static float shaderSpeed() {
        return shaderSpeed;
    }

    public static float shaderOpacity() {
        return shaderOpacity * (0.55F + 0.45F * Easing.easeOutCubic(appear));
    }

    public static float outlineWidth() {
        return outlineWidth;
    }

    public static float modelAlpha() {
        return modelAlpha;
    }
}
