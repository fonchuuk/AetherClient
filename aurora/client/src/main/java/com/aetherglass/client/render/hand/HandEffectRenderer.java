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
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/**
 * Рендерер эффектов руки, ViewModel и анимаций рук от первого лица ({@code HandsShader} + {@code ViewModel}, Блок 4).
 *
 * <p>Все трансформации ViewModel (позиция X/Y/Z, масштаб и поворот X/Y/Z вокруг точки хвата руки)
 * и 5 процедурных анимаций (Swing, Equip, Walking Sway, Idle, Eating/Drinking) применяются к {@link PoseStack}
 * в начале {@code renderArmWithItem}, поэтому и сама рука/предмет, и шейдерная оболочка {@code HandsShader}
 * двигаются, вращаются и масштабируются синхронно с точностью до субпикселя.</p>
 */
public final class HandEffectRenderer {
    public static final Identifier WHITE = Identifier.fromNamespaceAndPath("aetherglass",
            "textures/particle/white.png");

    private static boolean moduleEnabled = true;
    private static HandStyle style = HandStyle.GLOW;
    private static HandAnimation animation = HandAnimation.SWING;
    private static int color = 0xFFAA3DFF;
    private static float intensity = 0.6F;
    private static float appearSpeed = 6.5F;
    private static float swingStrength = 0.65F;

    private static boolean useShader = true;
    private static ShaderEffect shaderEffect = ShaderEffect.LIQUID_GLASS;
    private static FaceGradientMode faceGradientMode = FaceGradientMode.MENISCUS_DROP;
    private static int shaderColor = 0xFFAA3DFF;
    private static float shaderIntensity = 1.15F;
    private static float shaderSpeed = 1.0F;
    private static float shaderOpacity = 0.46F;
    private static float outlineWidth = 0.014F;
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
    private static int pushedDepth;

    private HandEffectRenderer() {
    }

    public static void configure(HandStyle styleValue, HandAnimation animationValue, int colorValue,
                                 float intensityValue, float appearSpeedValue, float swingStrengthValue) {
        moduleEnabled = true;
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
        moduleEnabled = true;
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
        moduleEnabled = true;
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
        moduleEnabled = false;
        appear = 1.0F;
        swing = 0.0F;
        walkPhase = 0.0F;
        walkAmplitude = 0.0F;
        eatingProgress = 0.0F;
        lastItem = null;
        lastSelectedSlot = -1;
        holdingMainItem = false;
        pushedDepth = 0;
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
            swing = (float) Math.sin(Math.pow(rawAtk, 0.72D) * Math.PI);
        } else {
            swing = Math.max(0.0F, swing - deltaSeconds * 7.5F);
        }

        double horizSpeed = player.getDeltaMovement().horizontalDistance();
        float targetWalk = (player.onGround() && horizSpeed > 0.015D)
                ? (float) Math.min(1.0D, horizSpeed * 5.2D)
                : 0.0F;
        walkAmplitude += (targetWalk - walkAmplitude) * Math.min(1.0F, deltaSeconds * 10.0F);
        if (walkAmplitude > 0.01F) {
            walkPhase += deltaSeconds * (5.5F + 4.0F * walkAmplitude);
        }

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
        ItemStack held = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        transformHandPose(pose, player, InteractionHand.MAIN_HAND, held, partialTick);
    }

    /**
     * Трансформирует матрицу руки/предмета от первого лица вокруг естественного центра хвата
     * {@code (pivotX, pivotY, pivotZ)}, чтобы масштаб и повороты крутили сам предмет/руку на месте,
     * а смещение X/Y/Z двигало руку влево-вправо, вниз-вверх и ближе-дальше от камеры.
     */
    public static void transformHandPose(PoseStack pose, AbstractClientPlayer player,
                                         InteractionHand hand, ItemStack item, float partialTick) {
        if (pose == null || !moduleEnabled) {
            return;
        }
        pose.pushPose();
        pushedDepth++;

        HumanoidArm arm = HumanoidArm.RIGHT;
        if (player != null) {
            arm = (hand == InteractionHand.OFF_HAND)
                    ? player.getMainArm().getOpposite()
                    : player.getMainArm();
        }
        float side = (arm == HumanoidArm.RIGHT) ? 1.0F : -1.0F;
        boolean emptyArm = (item == null || item.isEmpty());

        // Точка вращения и масштабирования — естественная позиция руки/предмета в ванильном рендерере
        float pivotX = side * (emptyArm ? 0.64F : 0.56F);
        float pivotY = emptyArm ? -0.60F : -0.52F;
        float pivotZ = -0.72F;

        float time = FrameClock.elapsedSeconds();

        // 1. ViewModel: Позиция X (влево/вправо), Y (вниз/вверх), Z (ближе/дальше вглубь экрана)
        float tx = viewOffsetX * 0.28F * side;
        float ty = viewOffsetY * 0.25F;
        float tz = -viewOffsetZ * 0.35F;

        // 2. Equip Animation (Вынимание предмета снизу и чуть сбоку с Easing)
        if (equipAnimEnabled && appear < 0.999F) {
            float shown = Easing.easeOutCubic(appear);
            float inv = 1.0F - shown;
            tx += inv * 0.14F * side;
            ty -= inv * 0.32F;
            tz -= inv * 0.08F;
        }

        // 3. Walking Sway (Покачивание в такт шагам как в шутерах)
        if (walkingSwayEnabled && walkAmplitude > 0.005F) {
            float amp = walkAmplitude * swingStrength;
            tx += (float) Math.cos(walkPhase) * 0.028F * amp;
            ty += (float) Math.abs(Math.sin(walkPhase)) * -0.022F * amp;
        }

        // 4. Idle Animation (Лёгкое дыхание руки в покое)
        if (idleAnimEnabled) {
            float idleFactor = Math.max(0.15F, 1.0F - walkAmplitude);
            tx += (float) Math.cos(time * 1.35D) * 0.005F * idleFactor;
            ty += (float) Math.sin(time * 2.10D) * 0.008F * idleFactor;
        }

        // 5. NoClip при еде/питье + Eating/Drinking Animation
        if (eatingProgress > 0.005F) {
            float eatEased = Easing.easeInOutSine(eatingProgress);
            if (noClipEating) {
                tx += 0.22F * eatEased * side;
                ty -= 0.26F * eatEased;
                tz -= 0.14F * eatEased;
            }
            if (eatingAnimEnabled) {
                float sipWave = (float) Math.sin(time * 11.5D) * 0.020F * eatEased;
                ty += sipWave;
            }
        }

        // 6. Повороты по осям X, Y, Z + Swing Animation
        float rx = viewRotX;
        float ry = viewRotY * side;
        float rz = viewRotZ * side;

        if (swingAnimEnabled && swing > 0.002F && hand == InteractionHand.MAIN_HAND) {
            float s = Easing.easeOutCubic(swing) * swingStrength;
            rx -= s * 44.0F;
            ry += s * 16.0F * side;
            rz -= s * 12.0F * side;
        }
        if (equipAnimEnabled && appear < 0.999F) {
            float inv = 1.0F - Easing.easeOutCubic(appear);
            rx -= inv * 18.0F;
            rz += inv * 10.0F * side;
        }
        if (eatingAnimEnabled && eatingProgress > 0.005F) {
            float eatEased = Easing.easeInOutSine(eatingProgress);
            rx += (float) Math.sin(time * 11.5D) * 6.5F * eatEased;
            ry -= 9.0F * eatEased * side;
        }

        // 7. Масштаб руки
        float finalScale = viewScale;
        if (equipAnimEnabled && appear < 0.999F) {
            finalScale *= (0.88F + 0.12F * Easing.easeOutCubic(appear));
        }

        boolean hasRot = Math.abs(rx) > 0.01F || Math.abs(ry) > 0.01F || Math.abs(rz) > 0.01F;
        boolean hasScale = Math.abs(finalScale - 1.0F) > 0.002F;

        pose.translate(tx, ty, tz);
        if (hasRot || hasScale) {
            pose.translate(pivotX, pivotY, pivotZ);
            if (Math.abs(rx) > 0.01F) {
                pose.mulPose(Axis.XP.rotationDegrees(rx));
            }
            if (Math.abs(ry) > 0.01F) {
                pose.mulPose(Axis.YP.rotationDegrees(ry));
            }
            if (Math.abs(rz) > 0.01F) {
                pose.mulPose(Axis.ZP.rotationDegrees(rz));
            }
            if (hasScale) {
                pose.scale(finalScale, finalScale, finalScale);
            }
            pose.translate(-pivotX, -pivotY, -pivotZ);
        }
    }

    public static void restorePose(PoseStack pose) {
        if (pushedDepth <= 0 || pose == null) {
            return;
        }
        pushedDepth--;
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
        return moduleEnabled;
    }

    public static boolean isShaderActive() {
        return moduleEnabled && useShader && shaderOpacity > 0.01F;
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

    public static float viewOffsetX() {
        return viewOffsetX;
    }

    public static float viewOffsetY() {
        return viewOffsetY;
    }

    public static float viewOffsetZ() {
        return viewOffsetZ;
    }

    public static float viewScale() {
        return viewScale;
    }

    public static float viewRotX() {
        return viewRotX;
    }

    public static float viewRotY() {
        return viewRotY;
    }

    public static float viewRotZ() {
        return viewRotZ;
    }
}
