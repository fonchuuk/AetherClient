package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.RenderResources;
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
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Видео-купол и шейдерный генератор неба ({@code SkyShader}, Блок 3.1–3.2):
 * <ul>
 *   <li>Бесшовная полярная UV-развёртка (0 разрывов на стыках 0°/360° и в зените);</li>
 *   <li>Предвычисленная таблица геометрии купола (0 вызовов {@code Math.sin/cos} на вершину в кадре);</li>
 *   <li>Поддержка размытого плейсхолдера ({@code _b2.png}) на первых кадрах без блокировки UI;</li>
 *   <li>Регулировка видео-пресета, скорости проигрывания и интенсивности свечения.</li>
 * </ul>
 */
public final class SkyEffectBlender {
    private static final int LIGHT_FULLBRIGHT = 0x00F000F0;
    private static final int AZ_STEPS = 16;
    private static final int EL_BANDS = 4;
    private static final int GRID_VERTS = (EL_BANDS + 1) * (AZ_STEPS + 1);
    private static final float DOME_RADIUS = 78.0F;
    private static final float MIN_EL = -0.10F;
    private static final float MAX_EL = 1.48F;

    private static final float[] AZ_COS = new float[AZ_STEPS + 1];
    private static final float[] AZ_SIN = new float[AZ_STEPS + 1];
    private static final float[] EL_COS = new float[EL_BANDS + 1];
    private static final float[] EL_SIN = new float[EL_BANDS + 1];
    private static final float[] EL_T = new float[EL_BANDS + 1];
    private static final float[] EL_FADE = new float[EL_BANDS + 1];

    private static final float[] VX = new float[GRID_VERTS];
    private static final float[] VY = new float[GRID_VERTS];
    private static final float[] VZ = new float[GRID_VERTS];
    private static final float[] VU = new float[GRID_VERTS];
    private static final float[] VV = new float[GRID_VERTS];
    private static final int[] VCOL = new int[GRID_VERTS];

    static {
        for (int a = 0; a <= AZ_STEPS; a++) {
            // При a == AZ_STEPS угол точно замыкается на a == 0, исключая щель на стыке
            int wrapped = (a == AZ_STEPS) ? 0 : a;
            double az = (wrapped / (double) AZ_STEPS) * Math.PI * 2.0D;
            AZ_COS[a] = (float) Math.cos(az);
            AZ_SIN[a] = (float) Math.sin(az);
        }
        for (int b = 0; b <= EL_BANDS; b++) {
            float t = b / (float) EL_BANDS;
            float el = MIN_EL + (MAX_EL - MIN_EL) * t;
            EL_T[b] = t;
            EL_COS[b] = (float) Math.cos(el);
            EL_SIN[b] = (float) Math.sin(el);
            // Мягкое затухание у самого горизонта, а в зените — полная плотность без дыры
            EL_FADE[b] = (float) Math.sin(Math.min(1.0F, t * 1.35F) * (Math.PI * 0.5D));
        }
    }

    private static SkyPreset activePreset = SkyPreset.AURORA;
    private static int first = SkyPreset.AURORA.first();
    private static int second = SkyPreset.AURORA.second();
    private static int third = SkyPreset.AURORA.third();
    private static float speed = 0.55F;
    private static float intensity = 0.82F;
    private static boolean renderDome = true;
    private static float domeOpacity = 0.68F;
    private static ShaderEffect domeShader = ShaderEffect.AURORA;

    private static boolean invokedThisSession;
    private static int lastResultColor;
    private static int renderedFrames;

    private SkyEffectBlender() {
    }

    public static void configure(int firstColor, int secondColor, float cycleSpeed, float blendIntensity) {
        configure(SkyPreset.AURORA, firstColor, secondColor, ColorUtil.lerp(firstColor, secondColor, 0.5F),
                cycleSpeed, blendIntensity, true, 0.68F, ShaderEffect.AURORA);
    }

    public static void configure(int firstColor, int secondColor, int thirdColor,
                                 float cycleSpeed, float blendIntensity,
                                 boolean enableDome, float domeAlpha, ShaderEffect shader) {
        configure(activePreset, firstColor, secondColor, thirdColor,
                cycleSpeed, blendIntensity, enableDome, domeAlpha, shader);
    }

    public static void configure(SkyPreset preset, int firstColor, int secondColor, int thirdColor,
                                 float cycleSpeed, float blendIntensity,
                                 boolean enableDome, float domeAlpha, ShaderEffect shader) {
        activePreset = preset != null ? preset : SkyPreset.AURORA;
        first = firstColor;
        second = secondColor;
        third = thirdColor;
        speed = Math.max(0.02F, Math.min(3.0F, cycleSpeed));
        intensity = Math.max(0.0F, Math.min(1.2F, blendIntensity));
        renderDome = enableDome;
        domeOpacity = Math.max(0.05F, Math.min(1.0F, domeAlpha));
        domeShader = shader != null ? shader : ShaderEffect.AURORA;
    }

    public static float phase() {
        float time = FrameClock.elapsedSeconds();
        double wave = 0.5D + 0.5D * Math.sin(time * speed * 1.8D);
        return (float) wave;
    }

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

    public static int blendSky(int vanillaSkyRgb) {
        invokedThisSession = true;
        int vanilla = 0xFF000000 | (vanillaSkyRgb & 0x00FFFFFF);
        int target = currentTargetColor();
        int mixed = ColorUtil.lerp(vanilla, target, Math.min(1.0F, intensity));
        lastResultColor = mixed;
        return mixed & 0x00FFFFFF;
    }

    public static int blendSunrise(int vanillaArgb) {
        float time = FrameClock.elapsedSeconds() * speed;
        float horizonWave = 0.5F + 0.5F * (float) Math.cos(time * 1.6F + 0.8F);
        int c2 = ColorController.resolve(second);
        int c3 = ColorController.resolve(third);
        int horizonTarget = ColorUtil.lerp(c2, c3, horizonWave);
        int vanillaAlpha = ColorUtil.alpha(vanillaArgb);
        int minAuroraAlpha = Math.round(145 * Math.min(1.0F, intensity));
        int outAlpha = Math.max(vanillaAlpha, minAuroraAlpha);
        int baseRgb = vanillaAlpha > 16 ? vanillaArgb : ColorController.resolve(first);
        int mixed = ColorUtil.lerp(baseRgb, horizonTarget, Math.min(1.0F, intensity * 0.92F));
        return ColorUtil.withAlpha(mixed, outAlpha);
    }

    /**
     * Отрисовывает бесшовный видео-купол неба с полярной развёрткой (0 разрывов на стыках)
     * и плавным вращением/перетеканием видео-слоёв.
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
            renderedFrames++;
            // Первые 4 кадра используем лёгкий размытый плейсхолдер, чтобы не было рывка при подгрузке
            Identifier skyTex = (renderedFrames <= 4)
                    ? activePreset.blurredPlaceholder()
                    : activePreset.videoFrame();
            RenderType layer = RenderTypes.entityTranslucentEmissive(skyTex);
            VertexConsumer consumer = buffers.getBuffer(layer);
            Matrix4f matrix = poseStack.last().pose();

            float time = FrameClock.elapsedSeconds();
            float rotAngle = time * speed * 0.14F;
            float cosRot = ShaderMath.cos(rotAngle);
            float sinRot = ShaderMath.sin(rotAngle);

            int c1 = ColorController.resolve(first);
            int c2 = ColorController.resolve(second);
            int c3 = ColorController.resolve(third);

            int stride = AZ_STEPS + 1;
            float shaderInt = 0.85F + Math.min(1.0F, intensity) * 0.65F;
            float shaderSpd = speed * 1.35F;

            for (int b = 0; b <= EL_BANDS; b++) {
                float tEl = EL_T[b];
                float cosEl = EL_COS[b];
                float sinEl = EL_SIN[b];
                float bandFade = EL_FADE[b];
                int baseColor = (b == 0) ? c3 : (b < EL_BANDS ? ColorUtil.lerp(c3, c2, tEl) : ColorUtil.lerp(c2, c1, tEl));
                float effOpacity = Math.min(1.0F, domeOpacity * intensity * bandFade);
                float polarR = 0.42F * (1.0F - tEl * 0.58F);
                int rowBase = b * stride;

                for (int a = 0; a < AZ_STEPS; a++) {
                    float cosAz = AZ_COS[a];
                    float sinAz = AZ_SIN[a];

                    float wave = 2.4F * ShaderMath.sin(cosAz * 2.0F + sinAz * 2.0F + time * speed * 1.8F + tEl * 2.2F);
                    float x = cosAz * cosEl * DOME_RADIUS;
                    float z = sinAz * cosEl * DOME_RADIUS;
                    float y = sinEl * DOME_RADIUS + wave * (1.0F - tEl * 0.6F);

                    float rx = cosAz * cosRot - sinAz * sinRot;
                    float rz = sinAz * cosRot + cosAz * sinRot;
                    float u = 0.5F + rx * polarR;
                    float v = 0.5F + rz * polarR;

                    int col = ShaderMath.sampleSurface(domeShader, baseColor,
                            shaderInt, shaderSpd, effOpacity,
                            u, v, 0.0F, 1.0F, 0.0F, u * 2.5F, v * 2.5F, 0.0F, time);
                    int alpha = Math.max(0, Math.min(255, Math.round(ColorUtil.alpha(col) * bandFade * 0.78F)));
                    int packedCol = (alpha << 24) | (col & 0x00FFFFFF);

                    int idx = rowBase + a;
                    VX[idx] = x;
                    VY[idx] = y;
                    VZ[idx] = z;
                    VU[idx] = u;
                    VV[idx] = v;
                    VCOL[idx] = packedCol;
                }
                // Замыкаем шов 360° -> 0° без повторного вычисления шейдера
                int firstIdx = rowBase;
                int lastIdx = rowBase + AZ_STEPS;
                VX[lastIdx] = VX[firstIdx];
                VY[lastIdx] = VY[firstIdx];
                VZ[lastIdx] = VZ[firstIdx];
                VU[lastIdx] = VU[firstIdx];
                VV[lastIdx] = VV[firstIdx];
                VCOL[lastIdx] = VCOL[firstIdx];
            }

            for (int b = 0; b < EL_BANDS; b++) {
                int r0 = b * stride;
                int r1 = (b + 1) * stride;
                for (int a = 0; a < AZ_STEPS; a++) {
                    int i00 = r0 + a;
                    int i10 = r0 + a + 1;
                    int i11 = r1 + a + 1;
                    int i01 = r1 + a;

                    emitCachedVertex(consumer, matrix, i00);
                    emitCachedVertex(consumer, matrix, i10);
                    emitCachedVertex(consumer, matrix, i11);
                    emitCachedVertex(consumer, matrix, i01);

                    emitCachedVertex(consumer, matrix, i01);
                    emitCachedVertex(consumer, matrix, i11);
                    emitCachedVertex(consumer, matrix, i10);
                    emitCachedVertex(consumer, matrix, i00);
                }
            }
            buffers.endBatch(layer);
        } catch (Throwable ignored) {
            RenderResources.release();
        }
    }

    private static void emitCachedVertex(VertexConsumer consumer, Matrix4f matrix, int idx) {
        consumer.addVertex(matrix, VX[idx], VY[idx], VZ[idx])
                .setColor(VCOL[idx])
                .setUv(VU[idx], VV[idx])
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

    public static SkyPreset activePreset() {
        return activePreset;
    }

    public static String debugState() {
        if (!invokedThisSession) {
            return "ожидание мира";
        }
        return String.format("%s · #%06X", activePreset.toString(), lastResultColor & 0xFFFFFF);
    }
}
