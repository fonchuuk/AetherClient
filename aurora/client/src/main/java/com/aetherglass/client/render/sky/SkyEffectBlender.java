package com.aetherglass.client.render.sky;

import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.shader.ShaderEffect;
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
import org.joml.Quaternionf;

/**
 * Видео-купол неба ({@code SkyShader}).
 *
 * <p>Купол — равноугольная (equirectangular) развёртка видео-панорамы: столбцы
 * UV соответствуют азимуту 0…360°, строки — высоте от горизонта до зенита.
 * Кадры лежат листом ({@code sky_video_*.png}, 12 панорам по 512×192), и номер
 * строки выбирается по времени — то есть по небу действительно крутится видео,
 * а не заливается цвет.</p>
 *
 * <p>Что было не так раньше и почему переделано:</p>
 * <ul>
 *   <li>UV считались радиально от центра («диск»), поэтому текстура сходилась
 *       в точку в зените и выглядела как пятно, а не как небо;</li>
 *   <li>каждая вершина вызывала полный шейдерный расчёт ({@code sampleSurface}:
 *       несколько {@code sin/cos} и {@code pow}) — 768 вершин × 60 раз в секунду
 *       на пустом месте съедали кадровое время;</li>
 *   <li>цвет вершины перебивал текстуру, поэтому видео было не видно.</li>
 * </ul>
 *
 * <p>Сейчас: геометрия и UV предвычислены один раз, в кадре считается только
 * поворот купола (одна кватернионная матрица) и номер кадра. Ноль трансцендентных
 * функций на вершину, одна текстура, один батч.</p>
 */
public final class SkyEffectBlender {
    private static final int LIGHT_FULLBRIGHT = 0x00F000F0;

    /** Шагов по азимуту: 64 даёт гладкую дугу без «многоугольника» на горизонте. */
    private static final int AZ_STEPS = 64;
    /** Поясов по высоте: 16 — плавный переход зенит → горизонт. */
    private static final int EL_BANDS = 16;
    private static final int GRID_VERTS = (EL_BANDS + 1) * (AZ_STEPS + 1);

    /** Кадров в видео-листе (см. tools/gen_sky_video.py). */
    public static final int VIDEO_FRAMES = 12;
    private static final float FRAME_H = 192.0F;
    /** Полпикселя внутрь строки — чтобы билинейная фильтрация не смешала соседние кадры. */
    private static final float FRAME_INSET = 0.5F / (VIDEO_FRAMES * FRAME_H);

    private static final float DOME_RADIUS = 78.0F;
    private static final float MIN_EL = -0.05F;              // чуть ниже горизонта, чтобы не было щели
    private static final float MAX_EL = (float) (Math.PI * 0.5D);

    private static final float[] AZ_COS = new float[AZ_STEPS + 1];
    private static final float[] AZ_SIN = new float[AZ_STEPS + 1];
    private static final float[] EL_COS = new float[EL_BANDS + 1];
    private static final float[] EL_SIN = new float[EL_BANDS + 1];
    private static final float[] EL_V = new float[EL_BANDS + 1];
    private static final float[] EL_ALPHA = new float[EL_BANDS + 1];

    // Предвычисленная геометрия купола: в кадре только поворот матрицей
    private static final float[] VX = new float[GRID_VERTS];
    private static final float[] VY = new float[GRID_VERTS];
    private static final float[] VZ = new float[GRID_VERTS];
    private static final float[] VU = new float[GRID_VERTS];
    private static final int[] VALPHA = new int[GRID_VERTS];

    private static final Quaternionf DOME_ROT = new Quaternionf();

    static {
        for (int a = 0; a <= AZ_STEPS; a++) {
            // Последний столбец замыкается на первый: шва на стыке 0°/360° нет
            int wrapped = (a == AZ_STEPS) ? 0 : a;
            double az = (wrapped / (double) AZ_STEPS) * Math.PI * 2.0D;
            AZ_COS[a] = (float) Math.cos(az);
            AZ_SIN[a] = (float) Math.sin(az);
        }
        for (int b = 0; b <= EL_BANDS; b++) {
            float t = b / (float) EL_BANDS;
            float el = MIN_EL + (MAX_EL - MIN_EL) * t;
            EL_COS[b] = (float) Math.cos(el);
            EL_SIN[b] = (float) Math.sin(el);
            EL_V[b] = t;
            // Плотность у горизонта падает — стык купола с землёй мягкий, без линии
            EL_ALPHA[b] = (float) Math.pow(Math.min(1.0F, t * 1.25F), 0.65D);
        }
        int stride = AZ_STEPS + 1;
        for (int b = 0; b <= EL_BANDS; b++) {
            float cosEl = EL_COS[b];
            float sinEl = EL_SIN[b];
            for (int a = 0; a < AZ_STEPS; a++) {
                int idx = b * stride + a;
                VX[idx] = AZ_COS[a] * cosEl * DOME_RADIUS;
                VY[idx] = sinEl * DOME_RADIUS;
                VZ[idx] = AZ_SIN[a] * cosEl * DOME_RADIUS;
                VU[idx] = a / (float) AZ_STEPS;
                VALPHA[idx] = Math.round(255.0F * EL_ALPHA[b]);
            }
            int first = b * stride;
            int last = first + AZ_STEPS;
            VX[last] = VX[first];
            VY[last] = VY[first];
            VZ[last] = VZ[first];
            VU[last] = 1.0F;
            VALPHA[last] = VALPHA[first];
        }
    }

    private static SkyPreset activePreset = SkyPreset.AURORA;
    private static int first = SkyPreset.AURORA.first();
    private static int second = SkyPreset.AURORA.second();
    private static int third = SkyPreset.AURORA.third();
    private static float speed = 0.55F;
    private static float intensity = 0.82F;
    private static boolean renderDome = true;
    private static float domeOpacity = 0.92F;
    private static ShaderEffect domeShader = ShaderEffect.AURORA;

    private static boolean invokedThisSession;
    private static boolean domeDrawn;
    private static int lastResultColor;
    private static int lastFrame;

    private SkyEffectBlender() {
    }

    public static void configure(int firstColor, int secondColor, float cycleSpeed, float blendIntensity) {
        configure(SkyPreset.AURORA, firstColor, secondColor, ColorUtil.lerp(firstColor, secondColor, 0.5F),
                cycleSpeed, blendIntensity, true, 0.92F, ShaderEffect.AURORA);
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

    /**
     * Когда видео-купол включён, ванильный цвет неба не подмешивается: иначе небо
     * «меняет цвет как /time set» и перебивает видео. Купол сам закрывает свод.
     */
    public static boolean domeActive() {
        return renderDome && domeOpacity > 0.02F;
    }

    public static int blendSky(int vanillaSkyRgb) {
        invokedThisSession = true;
        if (domeActive()) {
            // Оставляем глубокий тёмный фон под куполом: он нужен только как
            // подложка на случай, если текстура ещё не загрузилась.
            lastResultColor = 0xFF05070E;
            return 0x0005070E;
        }
        int vanilla = 0xFF000000 | (vanillaSkyRgb & 0x00FFFFFF);
        int target = currentTargetColor();
        int mixed = ColorUtil.lerp(vanilla, target, Math.min(1.0F, intensity));
        lastResultColor = mixed;
        return mixed & 0x00FFFFFF;
    }

    public static int blendSunrise(int vanillaArgb) {
        if (domeActive()) {
            // Нижняя полоса горизонта у купола тоже из видео — не подкрашиваем её
            return ColorUtil.withAlpha(0x00000000, 0);
        }
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
     * Рисует видео-купол: равноугольная развёртка кадра из листа, поворот по
     * времени, один батч. В кадре нет ни одной тригонометрической функции на
     * вершину — только поворот предвычисленной сетки.
     */
    public static void renderSkyDome(PoseStack poseStack) {
        if (!domeActive() || intensity <= 0.01F || poseStack == null) {
            return;
        }
        MultiBufferSource.BufferSource buffers = RenderResources.acquire();
        if (buffers == null) {
            return;
        }
        try {
            float time = FrameClock.elapsedSeconds();
            int frame = (int) (time * (3.0F + speed * 9.0F)) % VIDEO_FRAMES;
            if (frame < 0) {
                frame = 0;
            }
            lastFrame = frame;
            float v0 = frame / (float) VIDEO_FRAMES + FRAME_INSET;
            float v1 = (frame + 1) / (float) VIDEO_FRAMES - FRAME_INSET;
            float dv = v1 - v0;

            int alphaScale = Math.round(255.0F * domeOpacity * Math.min(1.0F, intensity));
            if (alphaScale <= 3) {
                return;
            }

            Identifier sheet = activePreset.skySheet();
            RenderType layer = RenderTypes.entityTranslucentEmissive(sheet);
            VertexConsumer consumer = buffers.getBuffer(layer);

            poseStack.pushPose();
            // Купол медленно вращается вокруг оси мира: небо «крутится», а не мигает
            DOME_ROT.rotationY((time * (0.06F + speed * 0.10F)) % (float) (Math.PI * 2.0D));
            poseStack.mulPose(DOME_ROT);
            Matrix4f matrix = poseStack.last().pose();

            int stride = AZ_STEPS + 1;
            for (int b = 0; b < EL_BANDS; b++) {
                int r0 = b * stride;
                int r1 = (b + 1) * stride;
                for (int a = 0; a < AZ_STEPS; a++) {
                    int i00 = r0 + a;
                    int i10 = r0 + a + 1;
                    int i11 = r1 + a + 1;
                    int i01 = r1 + a;

                    // лицевая сторона (смотрим изнутри купола)
                    vertex(consumer, matrix, i00, VU[i00], v0 + dv * EL_V[b]);
                    vertex(consumer, matrix, i10, VU[i10], v0 + dv * EL_V[b]);
                    vertex(consumer, matrix, i11, VU[i11], v0 + dv * EL_V[b + 1]);
                    vertex(consumer, matrix, i01, VU[i01], v0 + dv * EL_V[b + 1]);
                    // обратная сторона: купол виден и на развороте камеры
                    vertex(consumer, matrix, i01, VU[i01], v0 + dv * EL_V[b + 1]);
                    vertex(consumer, matrix, i11, VU[i11], v0 + dv * EL_V[b + 1]);
                    vertex(consumer, matrix, i10, VU[i10], v0 + dv * EL_V[b]);
                    vertex(consumer, matrix, i00, VU[i00], v0 + dv * EL_V[b]);
                }
            }
            buffers.endBatch(layer);
            poseStack.popPose();
            domeDrawn = true;
        } catch (Throwable ignored) {
            RenderResources.release();
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, int idx, float u, float v) {
        int alpha = Math.round(VALPHA[idx] * (domeOpacity > 0 ? 1.0F : 0.0F));
        consumer.addVertex(matrix, VX[idx], VY[idx], VZ[idx])
                .setColor(255, 255, 255, Math.min(255, alpha))
                .setUv(u, v)
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

    public static int currentFrame() {
        return lastFrame;
    }

    public static String debugState() {
        if (!invokedThisSession) {
            return "ожидание мира";
        }
        if (!domeDrawn) {
            return activePreset.toString() + " · купол выкл.";
        }
        return String.format("%s · кадр %d/%d", activePreset.toString(), lastFrame + 1, VIDEO_FRAMES);
    }
}
