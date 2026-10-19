package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.shader.ShaderMath;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Шейдерный генератор и 3D-купол неба ({@code SkyShader}, Блок 4.4):
 * <ul>
 *   <li>Многоточечный непрерывный перелив зенита, горизонта и звёздного сияния в реальном времени;</li>
 *   <li>Процедурный 3D-купол неба (северное сияние, туманности и 16 шейдер-эффектов),
 *       отрисовываемый прямо на небесной сфере за геометрией мира.</li>
 * </ul>
 */
public final class SkyEffectBlender {
    private static final int LIGHT_FULLBRIGHT = 0x00F000F0;

    private static int first = SkyPreset.AURORA.first();
    private static int second = SkyPreset.AURORA.second();
    private static int third = SkyPreset.AURORA.third();
    private static float speed = 0.45F;
    private static float intensity = 0.78F;
    private static boolean renderDome = true;
    private static float domeOpacity = 0.55F;
    private static ShaderEffect domeShader = ShaderEffect.AURORA;

    private static boolean invokedThisSession;
    private static int lastResultColor;

    private SkyEffectBlender() {
    }

    public static void configure(int firstColor, int secondColor, float cycleSpeed, float blendIntensity) {
        configure(firstColor, secondColor, ColorUtil.lerp(firstColor, secondColor, 0.5F),
                cycleSpeed, blendIntensity, true, 0.55F, ShaderEffect.AURORA);
    }

    public static void configure(int firstColor, int secondColor, int thirdColor,
                                 float cycleSpeed, float blendIntensity,
                                 boolean enableDome, float domeAlpha, ShaderEffect shader) {
        first = firstColor;
        second = secondColor;
        third = thirdColor;
        speed = Math.max(0.02F, Math.min(3.0F, cycleSpeed));
        intensity = Math.max(0.0F, Math.min(1.0F, blendIntensity));
        renderDome = enableDome;
        domeOpacity = Math.max(0.05F, Math.min(1.0F, domeAlpha));
        domeShader = shader != null ? shader : ShaderEffect.AURORA;
    }

    /** Непрерывная фаза перелива [0..1]. */
    public static float phase() {
        float time = FrameClock.elapsedSeconds();
        double wave = 0.5D + 0.5D * Math.sin(time * speed * 1.8D);
        return (float) wave;
    }

    /** Многоступенчатый плавный перелив между тремя цветами и шейдерной гармонией. */
    public static int currentTargetColor() {
        float time = FrameClock.elapsedSeconds() * speed;
        float w1 = 0.5F + 0.5F * (float) Math.sin(time * 1.4F);
        float w2 = 0.5F + 0.5F * (float) Math.sin(time * 0.9F + 1.7F);
        int c1 = ColorController.resolve(first);
        int c2 = ColorController.resolve(second);
        int c3 = ColorController.resolve(third);
        int mix12 = ColorUtil.lerp(c1, c2, w1);
        return ColorUtil.lerp(mix12, c3, w2 * 0.65F);
    }

    /** Живой перелив основного купола неба. */
    public static int blendSky(int vanillaSkyRgb) {
        invokedThisSession = true;
        int vanilla = 0xFF000000 | (vanillaSkyRgb & 0x00FFFFFF);
        int target = currentTargetColor();
        int mixed = ColorUtil.lerp(vanilla, target, intensity);
        lastResultColor = mixed;
        return mixed & 0x00FFFFFF;
    }

    /** Живой перелив горизонта и зарева. */
    public static int blendSunrise(int vanillaArgb) {
        float time = FrameClock.elapsedSeconds() * speed;
        float horizonWave = 0.5F + 0.5F * (float) Math.cos(time * 1.6F + 0.8F);
        int c2 = ColorController.resolve(second);
        int c3 = ColorController.resolve(third);
        int horizonTarget = ColorUtil.lerp(c2, c3, horizonWave);
        int vanillaAlpha = ColorUtil.alpha(vanillaArgb);
        int minAuroraAlpha = Math.round(145 * intensity);
        int outAlpha = Math.max(vanillaAlpha, minAuroraAlpha);
        int baseRgb = vanillaAlpha > 16 ? vanillaArgb : ColorController.resolve(first);
        int mixed = ColorUtil.lerp(baseRgb, horizonTarget, Math.min(1.0F, intensity * 0.92F));
        return ColorUtil.withAlpha(mixed, outAlpha);
    }

    /**
     * Рисует процедурный анимированный 3D-купол неба (северное сияние / туманность / 16 шейдеров)
     * прямо на небесной сфере во время отрисовки неба.
     */
    public static void renderSkyDome(PoseStack poseStack) {
        if (!renderDome || intensity <= 0.02F || domeOpacity <= 0.02F || poseStack == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = RenderResources.acquire();
        if (buffers == null) {
            return;
        }
        try {
            RenderType layer = RenderTypes.entityTranslucentEmissive(HandEffectRenderer.WHITE);
            VertexConsumer consumer = buffers.getBuffer(layer);
            Matrix4f matrix = poseStack.last().pose();

            float time = FrameClock.elapsedSeconds();
            int c1 = ColorController.resolve(first);
            int c2 = ColorController.resolve(second);
            int c3 = ColorController.resolve(third);

            int azSteps = 20;
            int elBands = 5;
            float radius = 76.0F;
            float minEl = -0.12F;
            float maxEl = 1.32F;

            for (int b = 0; b < elBands; b++) {
                float t0 = (float) b / elBands;
                float t1 = (float) (b + 1) / elBands;
                float el0 = minEl + (maxEl - minEl) * t0;
                float el1 = minEl + (maxEl - minEl) * t1;

                float cosEl0 = (float) Math.cos(el0);
                float sinEl0 = (float) Math.sin(el0);
                float cosEl1 = (float) Math.cos(el1);
                float sinEl1 = (float) Math.sin(el1);

                float bandFade0 = (float) Math.sin(t0 * Math.PI);
                float bandFade1 = (float) Math.sin(t1 * Math.PI);

                for (int a = 0; a < azSteps; a++) {
                    float u0 = (float) a / azSteps;
                    float u1 = (float) (a + 1) / azSteps;
                    float az0 = u0 * (float) (Math.PI * 2.0D);
                    float az1 = u1 * (float) (Math.PI * 2.0D);

                    float wave0 = 3.2F * ShaderMath.sin(az0 * 3.0F + time * speed * 2.1F + t0 * 2.5F);
                    float wave1 = 3.2F * ShaderMath.sin(az1 * 3.0F + time * speed * 2.1F + t1 * 2.5F);

                    float x00 = (float) Math.cos(az0) * cosEl0 * radius;
                    float z00 = (float) Math.sin(az0) * cosEl0 * radius;
                    float y00 = sinEl0 * radius + wave0;

                    float x10 = (float) Math.cos(az1) * cosEl0 * radius;
                    float z10 = (float) Math.sin(az1) * cosEl0 * radius;
                    float y10 = sinEl0 * radius + wave1;

                    float x11 = (float) Math.cos(az1) * cosEl1 * radius;
                    float z11 = (float) Math.sin(az1) * cosEl1 * radius;
                    float y11 = sinEl1 * radius + wave1;

                    float x01 = (float) Math.cos(az0) * cosEl1 * radius;
                    float z01 = (float) Math.sin(az0) * cosEl1 * radius;
                    float y01 = sinEl1 * radius + wave0;

                    int baseBand0 = ColorUtil.lerp(c2, c3, t0);
                    int baseBand1 = ColorUtil.lerp(c1, c3, t1);

                    int col0 = ShaderMath.sampleSurface(domeShader, baseBand0, 0.9F + intensity * 0.6F,
                            speed * 1.4F, domeOpacity * intensity * bandFade0,
                            u0, t0, 0.0F, 1.0F, 0.0F, u0 * 3.0F, t0 * 3.0F, 0.0F, time);
                    int col1 = ShaderMath.sampleSurface(domeShader, baseBand1, 0.9F + intensity * 0.6F,
                            speed * 1.4F, domeOpacity * intensity * bandFade1,
                            u1, t1, 0.0F, 1.0F, 0.0F, u1 * 3.0F, t1 * 3.0F, 0.0F, time);

                    int a0 = Math.round(ColorUtil.alpha(col0) * bandFade0 * 0.68F);
                    int a1 = Math.round(ColorUtil.alpha(col1) * bandFade1 * 0.68F);
                    if (a0 <= 2 && a1 <= 2) {
                        continue;
                    }
                    int cBot = ColorUtil.withAlpha(col0, a0);
                    int cTop = ColorUtil.withAlpha(col1, a1);

                    emitDomeVertex(consumer, matrix, x00, y00, z00, cBot);
                    emitDomeVertex(consumer, matrix, x10, y10, z10, cBot);
                    emitDomeVertex(consumer, matrix, x11, y11, z11, cTop);
                    emitDomeVertex(consumer, matrix, x01, y01, z01, cTop);

                    emitDomeVertex(consumer, matrix, x01, y01, z01, cTop);
                    emitDomeVertex(consumer, matrix, x11, y11, z11, cTop);
                    emitDomeVertex(consumer, matrix, x10, y10, z10, cBot);
                    emitDomeVertex(consumer, matrix, x00, y00, z00, cBot);
                }
            }
            buffers.endBatch(layer);
        } catch (Throwable ignored) {
            RenderResources.release();
        }
    }

    private static void emitDomeVertex(VertexConsumer consumer, Matrix4f matrix,
                                       float x, float y, float z, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LIGHT_FULLBRIGHT)
                .setNormal(0.0F, 1.0F, 0.0F);
    }

    public static float intensity() {
        return intensity;
    }

    public static float speed() {
        return speed;
    }

    public static String debugState() {
        if (!invokedThisSession) {
            return "ожидание мира";
        }
        return String.format("%s · #%06X", domeShader.displayName(), lastResultColor & 0xFFFFFF);
    }
}
