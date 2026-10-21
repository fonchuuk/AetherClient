package com.aetherglass.client.render.particle;

import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.ShapeBuffer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Высокопроизводительный пул 3D-частиц в мировых координатах (X, Y, Z) в стиле Liquid Glass.
 *
 * <p>Все частицы хранят абсолютные мировые координаты {@code (x, y, z)} и остаются
 * на своих местах в мире, когда игрок отходит или поворачивает камеру. Для каждой
 * частицы ведётся кольцевой буфер точек шлейфа ({@code TRAIL_STEPS}), который
 * отрисовывается объёмной лентой через {@link ShapeBuffer#addQuad} со всеми
 * атрибутами {@code NEW_ENTITY} (POSITION, COLOR, UV0, UV1, UV2, NORMAL).</p>
 */
public final class ParticleSystem {
    private static final int MIN_CAPACITY = 16;
    private static final int TRAIL_STEPS = 3;
    private static final Identifier WHITE =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/particle/circle.png");
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();
    /** Кэш значений enum: {@code values()} каждый раз копирует массив — в кадре это аллокация. */
    private static final ParticleShape[] SHAPES = ParticleShape.values();
    /** Угол второго «креста» билборда: даёт объём вместо плоской наклейки. */
    private static final float CROSS_ANGLE_COS = (float) Math.cos(Math.toRadians(46.0D));
    private static final float CROSS_ANGLE_SIN = (float) Math.sin(Math.toRadians(46.0D));

    private int capacity;
    private int count;

    private double[] x;
    private double[] y;
    private double[] z;
    private double[] vx;
    private double[] vy;
    private double[] vz;
    private float[] age;
    private float[] lifetime;
    private float[] size;
    private float[] baseAlpha;
    private float[] gravity;
    private float[] drag;
    private float[] fadeFraction;
    private float[] phase;
    private int[] startColor;
    private int[] endColor;
    private ParticleShape[] shape;

    /** Кольцевая история мировых координат для плавных 3D-шлейфов. */
    private double[][] trailX;
    private double[][] trailY;
    private double[][] trailZ;
    private int[] trailHead;
    private int[] trailCount;
    private float[] trailTimer;
    private final Vector3f rightVec = new Vector3f();
    private final Vector3f upVec = new Vector3f();
    private final Vector3f viewDirVec = new Vector3f();
    private final Vector3f crossRight = new Vector3f();
    private final Vector3f crossUp = new Vector3f();

    public ParticleSystem(int initialCapacity) {
        allocate(Math.max(MIN_CAPACITY, initialCapacity));
    }

    public void setCapacity(int desired) {
        int clamped = Math.max(MIN_CAPACITY, desired);
        if (clamped == this.capacity) {
            return;
        }
        int keep = Math.min(this.count, clamped);
        double[] nx = new double[clamped];
        double[] ny = new double[clamped];
        double[] nz = new double[clamped];
        double[] nvx = new double[clamped];
        double[] nvy = new double[clamped];
        double[] nvz = new double[clamped];
        float[] nage = new float[clamped];
        float[] nlife = new float[clamped];
        float[] nsize = new float[clamped];
        float[] nalpha = new float[clamped];
        float[] ngrav = new float[clamped];
        float[] ndrag = new float[clamped];
        float[] nfade = new float[clamped];
        float[] nphase = new float[clamped];
        int[] nsc = new int[clamped];
        int[] nec = new int[clamped];
        ParticleShape[] nshape = new ParticleShape[clamped];
        double[][] ntx = new double[clamped][TRAIL_STEPS];
        double[][] nty = new double[clamped][TRAIL_STEPS];
        double[][] ntz = new double[clamped][TRAIL_STEPS];
        int[] nhead = new int[clamped];
        int[] ntcnt = new int[clamped];
        float[] nttim = new float[clamped];

        if (keep > 0) {
            System.arraycopy(this.x, 0, nx, 0, keep);
            System.arraycopy(this.y, 0, ny, 0, keep);
            System.arraycopy(this.z, 0, nz, 0, keep);
            System.arraycopy(this.vx, 0, nvx, 0, keep);
            System.arraycopy(this.vy, 0, nvy, 0, keep);
            System.arraycopy(this.vz, 0, nvz, 0, keep);
            System.arraycopy(this.age, 0, nage, 0, keep);
            System.arraycopy(this.lifetime, 0, nlife, 0, keep);
            System.arraycopy(this.size, 0, nsize, 0, keep);
            System.arraycopy(this.baseAlpha, 0, nalpha, 0, keep);
            System.arraycopy(this.gravity, 0, ngrav, 0, keep);
            System.arraycopy(this.drag, 0, ndrag, 0, keep);
            System.arraycopy(this.fadeFraction, 0, nfade, 0, keep);
            System.arraycopy(this.phase, 0, nphase, 0, keep);
            System.arraycopy(this.startColor, 0, nsc, 0, keep);
            System.arraycopy(this.endColor, 0, nec, 0, keep);
            System.arraycopy(this.shape, 0, nshape, 0, keep);
            for (int i = 0; i < keep; i++) {
                System.arraycopy(this.trailX[i], 0, ntx[i], 0, TRAIL_STEPS);
                System.arraycopy(this.trailY[i], 0, nty[i], 0, TRAIL_STEPS);
                System.arraycopy(this.trailZ[i], 0, ntz[i], 0, TRAIL_STEPS);
            }
            System.arraycopy(this.trailHead, 0, nhead, 0, keep);
            System.arraycopy(this.trailCount, 0, ntcnt, 0, keep);
            System.arraycopy(this.trailTimer, 0, nttim, 0, keep);
        }
        this.capacity = clamped;
        this.count = keep;
        this.x = nx;
        this.y = ny;
        this.z = nz;
        this.vx = nvx;
        this.vy = nvy;
        this.vz = nvz;
        this.age = nage;
        this.lifetime = nlife;
        this.size = nsize;
        this.baseAlpha = nalpha;
        this.gravity = ngrav;
        this.drag = ndrag;
        this.fadeFraction = nfade;
        this.phase = nphase;
        this.startColor = nsc;
        this.endColor = nec;
        this.shape = nshape;
        this.trailX = ntx;
        this.trailY = nty;
        this.trailZ = ntz;
        this.trailHead = nhead;
        this.trailCount = ntcnt;
        this.trailTimer = nttim;
    }

    private void allocate(int size) {
        this.capacity = size;
        this.count = 0;
        this.x = new double[size];
        this.y = new double[size];
        this.z = new double[size];
        this.vx = new double[size];
        this.vy = new double[size];
        this.vz = new double[size];
        this.age = new float[size];
        this.lifetime = new float[size];
        this.size = new float[size];
        this.baseAlpha = new float[size];
        this.gravity = new float[size];
        this.drag = new float[size];
        this.fadeFraction = new float[size];
        this.phase = new float[size];
        this.startColor = new int[size];
        this.endColor = new int[size];
        this.shape = new ParticleShape[size];
        this.trailX = new double[size][TRAIL_STEPS];
        this.trailY = new double[size][TRAIL_STEPS];
        this.trailZ = new double[size][TRAIL_STEPS];
        this.trailHead = new int[size];
        this.trailCount = new int[size];
        this.trailTimer = new float[size];
    }

    public boolean spawn(double x, double y, double z,
                         double vx, double vy, double vz,
                         float lifetime, float size, float alpha,
                         float gravity, float drag, float fadeFraction,
                         int startColor, int endColor, ParticleShape shape) {
        if (this.count >= this.capacity) {
            return false;
        }
        int index = this.count++;
        this.x[index] = x;
        this.y[index] = y;
        this.z[index] = z;
        this.vx[index] = vx;
        this.vy[index] = vy;
        this.vz[index] = vz;
        this.age[index] = 0.0F;
        this.lifetime[index] = Math.max(0.05F, lifetime);
        this.size[index] = Math.max(0.01F, size);
        this.baseAlpha[index] = Math.max(0.0F, Math.min(1.0F, alpha));
        this.gravity[index] = gravity;
        this.drag[index] = Math.max(0.0F, Math.min(0.99F, drag));
        this.fadeFraction[index] = Math.max(0.05F, Math.min(0.95F, fadeFraction));
        this.phase[index] = (float) ((index * 1.6180339887D + x * 0.37D + z * 0.53D) % (Math.PI * 2.0D));
        this.startColor[index] = startColor;
        this.endColor[index] = endColor;
        this.shape[index] = shape != null ? shape : ParticleShape.STAR;

        this.trailX[index][0] = x;
        this.trailY[index][0] = y;
        this.trailZ[index][0] = z;
        this.trailHead[index] = 0;
        this.trailCount[index] = 1;
        this.trailTimer[index] = 0.0F;
        return true;
    }

    public void update(float dt) {
        if (dt <= 0.0F || this.count == 0) {
            return;
        }
        int i = 0;
        while (i < this.count) {
            float a = this.age[i] + dt;
            float life = this.lifetime[i];
            if (a >= life) {
                removeAt(i);
                continue;
            }
            this.age[i] = a;
            float damping = (float) Math.pow(1.0F - this.drag[i], dt * 20.0F);
            this.vy[i] -= this.gravity[i] * dt;
            this.vx[i] *= damping;
            this.vy[i] *= damping;
            this.vz[i] *= damping;

            // Мягкое локальное парение вокруг мировой точки спавна (X, Y, Z)
            float ph = this.phase[i];
            float swirlX = (float) Math.cos(a * 1.8F + ph) * 0.06F;
            float swirlY = (float) Math.sin(a * 1.4F + ph * 0.7F) * 0.04F;
            float swirlZ = (float) Math.sin(a * 1.8F + ph) * 0.06F;

            this.x[i] += (this.vx[i] + swirlX) * dt;
            this.y[i] += (this.vy[i] + swirlY) * dt;
            this.z[i] += (this.vz[i] + swirlZ) * dt;

            // Запись истории мировых координат для 3D-шлейфа
            float tt = this.trailTimer[i] + dt;
            if (tt >= 0.025F) {
                this.trailTimer[i] = 0.0F;
                int nextHead = (this.trailHead[i] + 1) % TRAIL_STEPS;
                this.trailX[i][nextHead] = this.x[i];
                this.trailY[i][nextHead] = this.y[i];
                this.trailZ[i][nextHead] = this.z[i];
                this.trailHead[i] = nextHead;
                if (this.trailCount[i] < TRAIL_STEPS) {
                    this.trailCount[i]++;
                }
            } else {
                this.trailTimer[i] = tt;
            }

            i++;
        }
    }

    private void removeAt(int index) {
        int last = --this.count;
        if (index != last) {
            this.x[index] = this.x[last];
            this.y[index] = this.y[last];
            this.z[index] = this.z[last];
            this.vx[index] = this.vx[last];
            this.vy[index] = this.vy[last];
            this.vz[index] = this.vz[last];
            this.age[index] = this.age[last];
            this.lifetime[index] = this.lifetime[last];
            this.size[index] = this.size[last];
            this.baseAlpha[index] = this.baseAlpha[last];
            this.gravity[index] = this.gravity[last];
            this.drag[index] = this.drag[last];
            this.fadeFraction[index] = this.fadeFraction[last];
            this.phase[index] = this.phase[last];
            this.startColor[index] = this.startColor[last];
            this.endColor[index] = this.endColor[last];
            this.shape[index] = this.shape[last];
            double[] tx = this.trailX[index]; this.trailX[index] = this.trailX[last]; this.trailX[last] = tx;
            double[] ty = this.trailY[index]; this.trailY[index] = this.trailY[last]; this.trailY[last] = ty;
            double[] tz = this.trailZ[index]; this.trailZ[index] = this.trailZ[last]; this.trailZ[last] = tz;
            this.trailHead[index] = this.trailHead[last];
            this.trailCount[index] = this.trailCount[last];
            this.trailTimer[index] = this.trailTimer[last];
        }
    }

    public void render(PoseStack matrices, MultiBufferSource buffers, Camera camera, float fadeInSeconds, float fadeOutSeconds) {
        render(matrices, buffers, camera, true);
    }

    /**
     * Отрисовка частиц строго в мировых координатах относительно позиции камеры {@code camPos}.
     * Использует {@code IDENTITY_POSE}, чтобы матрицы стека не смещали и не вращали мировые точки.
     */
    public void render(PoseStack matrices, MultiBufferSource buffers, Camera camera, boolean depthTest) {
        if (this.count == 0 || buffers == null || camera == null) {
            return;
        }
        Vec3 camPos = camera.position();
        Quaternionf rotation = camera.rotation();
        Vector3f right = this.rightVec.set(1.0F, 0.0F, 0.0F).rotate(rotation);
        Vector3f up = this.upVec.set(0.0F, 1.0F, 0.0F).rotate(rotation);
        Vector3f viewDir = this.viewDirVec.set(0.0F, 0.0F, -1.0F).rotate(rotation);

        // 1. Отрисовка плавных 3D-шлейфов ленточными квадами (со всеми атрибутами NEW_ENTITY)
        VertexConsumer trailConsumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(WHITE));
        for (int i = 0; i < this.count; i++) {
            int steps = this.trailCount[i];
            if (steps < 2) {
                continue;
            }
            float t = this.age[i] / this.lifetime[i];
            float fade = computeFade(t, this.fadeFraction[i]) * this.baseAlpha[i];
            if (fade <= 0.01F) {
                continue;
            }
            int baseCol = ColorUtil.lerp(this.startColor[i], this.endColor[i], Easing.easeInOutQuad(t));
            int head = this.trailHead[i];
            float prevX = (float) (this.x[i] - camPos.x);
            float prevY = (float) (this.y[i] - camPos.y);
            float prevZ = (float) (this.z[i] - camPos.z);
            float baseRibbonW = Math.max(0.012F, this.size[i] * 0.22F);

            for (int s = 0; s < steps; s++) {
                int idx = (head - s + TRAIL_STEPS) % TRAIL_STEPS;
                float currX = (float) (this.trailX[i][idx] - camPos.x);
                float currY = (float) (this.trailY[i][idx] - camPos.y);
                float currZ = (float) (this.trailZ[i][idx] - camPos.z);

                float dx = currX - prevX;
                float dy = currY - prevY;
                float dz = currZ - prevZ;
                float segLenSq = dx * dx + dy * dy + dz * dz;
                if (segLenSq > 1.0E-5F) {
                    float segRatio = 1.0F - (s / (float) steps);
                    int segCol = ColorUtil.fade(baseCol, fade * segRatio * 0.42F);
                    if (ColorUtil.alpha(segCol) > 2) {
                        // Вектор ширины ленты перпендикулярен направлению движения и взгляду камеры
                        float rx = dy * viewDir.z - dz * viewDir.y;
                        float ry = dz * viewDir.x - dx * viewDir.z;
                        float rz = dx * viewDir.y - dy * viewDir.x;
                        float rLen = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                        float halfW = baseRibbonW * segRatio;
                        if (rLen > 1.0E-4F) {
                            float inv = halfW / rLen;
                            rx *= inv;
                            ry *= inv;
                            rz *= inv;
                        } else {
                            rx = right.x * halfW;
                            ry = right.y * halfW;
                            rz = right.z * halfW;
                        }
                        ShapeBuffer.addQuad(IDENTITY_POSE, trailConsumer,
                                prevX - rx, prevY - ry, prevZ - rz,
                                prevX + rx, prevY + ry, prevZ + rz,
                                currX + rx, currY + ry, currZ + rz,
                                currX - rx, currY - ry, currZ - rz,
                                segCol);
                    }
                }
                prevX = currX;
                prevY = currY;
                prevZ = currZ;
            }
        }

        // 2. Отрисовка билбордов частиц по формам (пропускаем формы, которых сейчас нет в пуле)
        int presentMask = 0;
        for (int i = 0; i < this.count; i++) {
            ParticleShape sh = this.shape[i];
            if (sh != null) {
                presentMask |= (1 << sh.ordinal());
            }
        }
        for (ParticleShape targetShape : SHAPES) {
            if ((presentMask & (1 << targetShape.ordinal())) == 0) {
                continue;
            }
            VertexConsumer consumer = null;
            for (int i = 0; i < this.count; i++) {
                if (this.shape[i] != targetShape) {
                    continue;
                }
                float t = this.age[i] / this.lifetime[i];
                float fade = computeFade(t, this.fadeFraction[i]) * this.baseAlpha[i];
                if (fade <= 0.005F) {
                    continue;
                }
                int baseColor = ColorUtil.lerp(this.startColor[i], this.endColor[i], Easing.easeInOutQuad(t));
                int coreColor = ColorUtil.fade(ColorUtil.brighten(baseColor, 0.28F), fade);
                if (ColorUtil.alpha(coreColor) <= 2) {
                    continue;
                }
                if (consumer == null) {
                    consumer = buffers.getBuffer(RenderTypes.entityTranslucentEmissive(targetShape.texture()));
                }

                float px = (float) (this.x[i] - camPos.x);
                float py = (float) (this.y[i] - camPos.y);
                float pz = (float) (this.z[i] - camPos.z);

                float pulse = 0.90F + 0.16F * (float) Math.sin(this.age[i] * 5.0F + this.phase[i]);
                float coreRadius = this.size[i] * (0.75F + 0.35F * (1.0F - t)) * pulse;
                float haloRadius = coreRadius * 1.95F;

                int haloColor = ColorUtil.fade(baseColor, fade * 0.38F);
                if (ColorUtil.alpha(haloColor) > 2) {
                    drawBillboard(IDENTITY_POSE, consumer, px, py, pz, right, up, haloRadius, haloColor);
                }
                drawBillboard(IDENTITY_POSE, consumer, px, py, pz, right, up, coreRadius, coreColor);

                // Второй билборд, повёрнутый в плоскости экрана: капля выглядит
                // объёмной и не «схлопывается» в плоский диск при взгляде сбоку.
                // Стоимость — один дополнительный квад на частицу, ни одной
                // новой аллокации: базисные векторы переиспользуются.
                int crossColor = ColorUtil.fade(coreColor, fade * 0.34F);
                if (ColorUtil.alpha(crossColor) > 2) {
                    float ux = up.x * CROSS_ANGLE_SIN;
                    float uy = up.y * CROSS_ANGLE_SIN;
                    float uz = up.z * CROSS_ANGLE_SIN;
                    float rx = right.x * CROSS_ANGLE_SIN;
                    float ry = right.y * CROSS_ANGLE_SIN;
                    float rz = right.z * CROSS_ANGLE_SIN;
                    crossRight.set(right.x * CROSS_ANGLE_COS + ux, right.y * CROSS_ANGLE_COS + uy,
                            right.z * CROSS_ANGLE_COS + uz);
                    crossUp.set(up.x * CROSS_ANGLE_COS - rx, up.y * CROSS_ANGLE_COS - ry,
                            up.z * CROSS_ANGLE_COS - rz);
                    drawBillboard(IDENTITY_POSE, consumer, px, py, pz, crossRight, crossUp,
                            coreRadius * 0.92F, crossColor);
                }
            }
        }
    }

    private static void drawBillboard(PoseStack.Pose pose, VertexConsumer consumer,
                                      float px, float py, float pz,
                                      Vector3f right, Vector3f up,
                                      float r, int color) {
        float rx = right.x * r;
        float ry = right.y * r;
        float rz = right.z * r;
        float ux = up.x * r;
        float uy = up.y * r;
        float uz = up.z * r;

        ShapeBuffer.addTexturedQuad(pose, consumer,
                px - rx - ux, py - ry - uy, pz - rz - uz, 0.0F, 1.0F,
                px + rx - ux, py + ry - uy, pz + rz - uz, 1.0F, 1.0F,
                px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 0.0F,
                px - rx + ux, py - ry + uy, pz - rz + uz, 0.0F, 0.0F,
                color);
    }

    private static float computeFade(float t, float fadeFraction) {
        float inEnd = Math.min(0.25F, fadeFraction * 0.5F);
        if (t < inEnd && inEnd > 0.001F) {
            return Easing.easeOutCubic(t / inEnd);
        }
        float outStart = 1.0F - fadeFraction;
        if (t > outStart && fadeFraction > 0.001F) {
            return Easing.easeOutCubic(Math.max(0.0F, (1.0F - t) / fadeFraction));
        }
        return 1.0F;
    }

    public void clear() {
        this.count = 0;
    }

    public int alive() {
        return this.count;
    }

    public int capacity() {
        return this.capacity;
    }

    /** entityTranslucent требует все поля NEW_ENTITY, включая overlay и normal. */
    static void vertex(VertexConsumer consumer, org.joml.Matrix4f matrix, float x, float y, float z,
                       float u, float v, int color, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z).setUv(u, v).setColor(color)
                .setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT)
                .setNormal(nx, ny, nz);
    }
}
