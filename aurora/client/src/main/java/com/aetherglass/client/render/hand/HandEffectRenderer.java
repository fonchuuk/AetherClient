package com.aetherglass.client.render.hand;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
 * Эффекты руки (5.3): движение, подсветка и «отсвет» на мир.
 *
 * <p>Руку по-прежнему рисует игра — своим рендером, со своей моделью предмета.
 * Мы только накладываем сверху: применяем собственное преобразование к стеку матриц
 * (появление/исчезновение, размах, удержание, покачивание) и добавляем цвет,
 * который берём из общего контроллера цвета. Никакой замены руки картинкой нет.</p>
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

    private static Item lastItem;
    private static float appear = 1.0F;
    private static float swing;

    private HandEffectRenderer() {
    }

    /** Модуль передаёт свои настройки; вызывается при включении и при изменении настройки. */
    public static void configure(HandStyle styleValue, HandAnimation animationValue, int colorValue,
                                 float intensityValue, float appearSpeedValue, float swingStrengthValue) {
        style = styleValue == null ? HandStyle.NONE : styleValue;
        animation = animationValue == null ? HandAnimation.NONE : animationValue;
        color = colorValue;
        intensity = Math.max(0.0F, Math.min(1.0F, intensityValue));
        appearSpeed = Math.max(0.5F, Math.min(20.0F, appearSpeedValue));
        swingStrength = Math.max(0.0F, Math.min(1.5F, swingStrengthValue));
    }

    /** Сбрасывает состояние при выключении модуля: рука мгновенно становится обычной. */
    public static void reset() {
        appear = 1.0F;
        swing = 0.0F;
        lastItem = null;
    }

    /** Обновляет состояние анимации; вызывается один раз за кадр. */
    public static void update(float deltaSeconds, LocalPlayer player) {
        Item current = null;
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            current = stack.isEmpty() ? null : stack.getItem();
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
     *
     * <p>Вызывается из миксина в самом начале отрисовки руки; парный вызов
     * {@link #restorePose(PoseStack)} должен произойти в конце.</p>
     */
    public static void applyPose(PoseStack pose, LocalPlayer player, float partialTick) {
        pose.pushPose();
        float hidden = 1.0F - Easing.easeOutCubic(appear);
        if (hidden > 0.001F) {
            // Рука «выезжает» снизу и чуть сбоку, а не появляется рывком.
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
                // Без собственного движения: рука рисуется как в ванильной игре.
            }
        }
        if (partialTick < 0.0F) {
            // Некорректное время кадра: ничего не делаем, состояние уже согласовано.
            return;
        }
    }

    /** Возвращает стек матриц в исходное состояние. */
    public static void restorePose(PoseStack pose) {
        pose.popPose();
    }

    /**
     * Добавляет цветную геометрию рядом с рукой.
     *
     * <p>Это мягкий «отсвет»: плоскость перед камерой, окрашенная цветом эффекта.
     * Она не перекрывает предмет и не заменяет руку — только подсвечивает пространство
     * вокруг неё, как это делает настоящий источник света.</p>
     */
    public static void submitAccent(SubmitNodeCollector collector, PoseStack pose) {
        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        int accent = ColorUtil.fade(ColorController.resolve(color), 0.30F * intensity);
        int edge = ColorUtil.fade(ColorController.resolve(color), 0.0F);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(WHITE), (poseMatrix, consumer) -> {
            float half = 0.9F;
            float depth = -0.55F;
            float lift = -0.25F;
            // Три полосы: центральная яркая и две крайние прозрачные — имитация размытия.
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
     * Чёткая часть эффекта: рисуется поверх уже нарисованной руки в координатах экрана.
     *
     * <p>Здесь же виден «жидкий» градиент: он ложится поверх настоящей модели руки
     * и предмета, поэтому форма руки и её анимация остаются ванильными.</p>
     */
    public static void drawOverlay(GuiGraphics graphics, int screenWidth, int screenHeight) {
        if (style == HandStyle.NONE || intensity <= 0.01F) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui) {
            return;
        }
        int accent = ColorController.resolve(color);
        int width = Math.round(Math.min(screenWidth * 0.34F, 190.0F));
        int height = Math.round(Math.min(screenHeight * 0.34F, 150.0F));
        int x = screenWidth - width - Math.round(screenWidth * 0.06F);
        int y = screenHeight - height - Math.round(screenHeight * 0.05F);
        float alpha = 0.55F * intensity;

        if (style == HandStyle.GLOW) {
            // Кольца разного радиуса: чем шире, тем прозрачнее.
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
        return style != HandStyle.NONE && intensity > 0.01F;
    }
}
