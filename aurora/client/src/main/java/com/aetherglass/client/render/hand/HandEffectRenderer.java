package com.aetherglass.client.render.hand;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
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
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Рендерер эффектов руки, ViewModel и анимаций рук от первого лица ({@code HandsShader} + {@code ViewModel}, Блок 4).
 *
 * <p>Все трансформации ViewModel и 5 процедурных анимаций (Swing, Equip, Walking Sway, Idle, Eating/Drinking)
 * применяются к {@link PoseStack} до передачи в ванильный {@code SubmitNodeStorage} и в {@code HandsShader},
 * поэтому шейдерная глазурь и контур всегда на 100% совпадают с новой позицией и поворотом предмета.</p>
 */
public final class HandEffectRenderer {
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath("aetherglass",
            "textures/particle/white.png");

    private static HandStyle style = HandStyle.GLOW;
    private static HandAnimation animation = HandAnimation.SWING;
    private static int color = 0xFF7FD4FF;
    private static float intensity = 0.6F;
    private static float appearSpeed = 6.5F;
    private static float swingStrength = 0.65F;

    private static boolean useShader = true;
    private static ShaderEffect shaderEffect = ShaderEffect.LIQUID_GLASS;
    private static FaceGradientMode faceGradientMode = FaceGradientMode.MENISCUS_DROP;
    private static int shaderColor = ShaderEffect.LIQUID_GLASS.defaultColor();
    private static float shaderIntensity = 1.0F;
    private static float shaderSpeed = 1.0F;
    private static float shaderOpacity = 0.42F;
    private static float outlineWidth = 0.012F;
    private static float modelAlpha = 1.0F;

    // ── ViewModel & Hand Animations (Блок 4.1–4.2) ─────────────────────────────
    private static float viewOffsetX = 0.0F;
    private static float viewOffsetY = 0.0F;
    private static float viewOffsetZ = 0.0F;
    private static float viewScale = 1.0F;
    private static float viewRotX = 0.0F;
    private static float viewRotY = 0.0F;
    private static float viewRotZ = 0.0F;
    private static boolean noClipEating = true;

    private static boolean swingAnimEnabled = true;
    private static boolean equipAnimEnabled = true;
    private static boolean walkingSwayEnabled = true;
    private static boolean idleAnimEnabled = true;
    private static boolean eatingAnimEnabled = true;

    private static Item lastItem;
    private static int lastSelectedSlot = -1;
    private static float appear = 1.0F;
    private static float swing;
    private static float walkPhase;
    private static float walkAmplitude;
    private static float eatingProgress;
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

    public static void configureViewModel(float offX, float offY, float offZ, float scaleVal,
                                          float rx, float ry, float rz, boolean hideOnEat,
                                          boolean swingOn, boolean equipOn, boolean walkOn,
                                          boolean idleOn, boolean eatOn) {
        viewOffsetX = Math.max(-1.0F, Math.min(1.0F, offX));
        viewOffsetY = Math.max(-1.0F, Math.min(1.0F, offY));
        viewOffsetZ = Math.max(-1.0F, Math.min(1.0F, offZ));
        viewScale = Math.max(0.5F, Math.min(1.5F, scaleVal));
        viewRotX = Math.max(-180.0F, Math.min(180.0F, rx));
        viewRotY = Math.max(-180.0F, Math.min(180.0F, ry));
        viewRotZ = Math.max(-180.0F, Math.min(180.0F, rz));
        noClipEating = hideOnEat;
        swingAnimEnabled = swingOn;
        equipAnimEnabled = equipOn;
        walkingSwayEnabled = walkOn;
        idleAnimEnabled = idleOn;
        eatingAnimEnabled = eatOn;
    }

    public static void reset() {
        appear = 1.0F;
        swing = 0.0F;
        walkPhase = 0.0F;
        walkAmplitude = 0.0F;
        eatingProgress = 0.0F;
        lastItem = null;
        lastSelectedSlot = -1;
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
        int currentSlot = player.getInventory().getSelectedSlot();
        if (current != lastItem || (lastSelectedSlot != -1 && currentSlot != lastSelectedSlot)) {
            lastItem = current;
            lastSelectedSlot = currentSlot;
            if (equipAnimEnabled) {
                appear = 0.0F;
            }
        }
        lastSelectedSlot = currentSlot;
        appear = Math.min(1.0F, appear + deltaSeconds * appearSpeed);

        if (player.swinging) {
            float rawAtk = player.getAttackAnim(1.0F);
            // Резкий и красивый нелинейный импульс удара
            swing = (float) Math.sin(Math.pow(rawAtk, 0.72D) * Math.PI);
        } else {
            swing = Math.max(0.0F, swing - deltaSeconds * 7.5F);
        }

        // Плавная интерполяция шага для Walking Sway
        double horizSpeed = player.getDeltaMovement().horizontalDistance();
        float targetWalk = (player.onGround() && horizSpeed > 0.015D)
                ? (float) Math.min(1.0D, horizSpeed * 5.2D)
                : 0.0F;
        walkAmplitude += (targetWalk - walkAmplitude) * Math.min(1.0F, deltaSeconds * 10.0F);
        if (walkAmplitude > 0.01F) {
            walkPhase += deltaSeconds * (5.5F + 4.0F * walkAmplitude);
        }

        // Проверка еды и питья
        boolean isEatingOrDrinking = false;
        if (player.isUsingItem()) {
            ItemStack useStack = player.getUseItem();
            if (!useStack.isEmpty()) {
                ItemUseAnimation useAnim = useStack.getUseAnimation();
                if (useAnim == ItemUseAnimation.EAT || useAnim == ItemUseAnimation.DRINK) {
                    isEatingOrDrinking = true;
                }
            }
        }
        float targetEat = isEatingOrDrinking ? 1.0F : 0.0F;
        eatingProgress += (targetEat - eatingProgress) * Math.min(1.0F, deltaSeconds * 9.0F);
    }

    public static void transformPose(PoseStack pose, LocalPlayer player, float partialTick) {
        posePushed = false;
        if (pose == null) {
            return;
        }
        pose.pushPose();
        posePushed = true;

        float time = FrameClock.elapsedSeconds();

        // 1. ViewModel: Базовое смещение X / Y / Z
        float tx = viewOffsetX * 0.45F;
        float ty = viewOffsetY * 0.45F;
        float tz = viewOffsetZ * 0.45F;

        // 2. Equip Animation (Вынимание предмета снизу и чуть сбоку с Easing)
        if (equipAnimEnabled && appear < 0.999F) {
            float shown = Easing.easeOutCubic(appear);
            float inv = 1.0F - shown;
            tx += inv * 0.14F;
            ty -= inv * 0.34F;
            tz += inv * 0.08F;
        }

        // 3. Walking Sway (Покачивание в такт шагам как в шутерах)
        if (walkingSwayEnabled && walkAmplitude > 0.005F) {
            float amp = walkAmplitude * swingStrength;
            tx += (float) Math.cos(walkPhase) * 0.032F * amp;
            ty += (float) Math.abs(Math.sin(walkPhase)) * -0.026F * amp;
        }

        // 4. Idle Animation (Лёгкое дыхание руки в покое)
        if (idleAnimEnabled) {
            float idleFactor = Math.max(0.15F, 1.0F - walkAmplitude);
            tx += (float) Math.cos(time * 1.35D) * 0.007F * idleFactor;
            ty += (float) Math.sin(time * 2.10D) * 0.011F * idleFactor;
        }

        // 5. NoClip при еде/питье + Eating/Drinking Animation
        if (eatingProgress > 0.005F) {
            float eatEased = Easing.easeInOutSine(eatingProgress);
            if (noClipEating) {
                // Отодвигаем и опускаем руку с едой/зельем, чтобы не перекрывать прицел и центр экрана
                tx += 0.22F * eatEased;
                ty -= 0.28F * eatEased;
                tz -= 0.12F * eatEased;
            }
            if (eatingAnimEnabled) {
                float sipWave = (float) Math.sin(time * 11.5D) * 0.022F * eatEased;
                ty += sipWave;
            }
        }

        pose.translate(tx, ty, tz);

        // 6. ViewModel: Повороты по осям X, Y, Z + Swing Animation
        float rx = viewRotX;
        float ry = viewRotY;
        float rz = viewRotZ;

        if (swingAnimEnabled && swing > 0.002F) {
            float s = Easing.easeOutCubic(swing) * swingStrength;
            rx -= s * 52.0F;
            ry += s * 18.0F;
            rz -= s * 14.0F;
        }
        if (equipAnimEnabled && appear < 0.999F) {
            float inv = 1.0F - Easing.easeOutCubic(appear);
            rx -= inv * 22.0F;
            rz += inv * 12.0F;
        }
        if (eatingAnimEnabled && eatingProgress > 0.005F) {
            float eatEased = Easing.easeInOutSine(eatingProgress);
            rx += (float) Math.sin(time * 11.5D) * 7.5F * eatEased;
            ry -= 10.0F * eatEased;
        }

        if (Math.abs(rx) > 0.01F) {
            pose.mulPose(Axis.XP.rotationDegrees(rx));
        }
        if (Math.abs(ry) > 0.01F) {
            pose.mulPose(Axis.YP.rotationDegrees(ry));
        }
        if (Math.abs(rz) > 0.01F) {
            pose.mulPose(Axis.ZP.rotationDegrees(rz));
        }

        // 7. ViewModel: Масштаб руки
        float finalScale = viewScale;
        if (equipAnimEnabled && appear < 0.999F) {
            finalScale *= (0.86F + 0.14F * Easing.easeOutCubic(appear));
        }
        if (Math.abs(finalScale - 1.0F) > 0.002F) {
            pose.scale(finalScale, finalScale, finalScale);
        }
    }

    public static void restorePose(PoseStack pose) {
        if (!posePushed || pose == null) {
            return;
        }
        posePushed = false;
        pose.popPose();
    }

    public static void submitAccent(SubmitNodeCollector collector, PoseStack pose) {
        if (useShader) {
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
        return String.format("VM %.2f,%.2f,%.2f · x%.2f", viewOffsetX, viewOffsetY, viewOffsetZ, viewScale);
    }

    public static boolean isActive() {
        return true;
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
