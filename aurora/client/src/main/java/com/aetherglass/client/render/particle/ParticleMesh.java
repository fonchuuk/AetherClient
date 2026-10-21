package com.aetherglass.client.render.particle;

import com.aetherglass.client.render.util.ColorUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Настоящая 3D-геометрия частиц: сфера, куб и кольцо с освещением по нормалям.
 *
 * <p>Раньше частица была одним билбордом с градиентной текстурой — на экране это
 * читается как плоская наклейка, развёрнутая к камере. Здесь у каждой частицы
 * есть реальный объём: сетка предвычислена один раз (вершины и нормали в
 * единичных координатах), в кадре она только масштабируется, поворачивается и
 * раскрашивается по закону Ламберта. Глубина, силуэт и блик на боку получаются
 * сами собой, без текстур и без шейдеров.</p>
 *
 * <p>Стоимость: 60 вершин на сферу (15 квадов). Никаких аллокаций в кадре —
 * массивы статические, временные векторы переиспользуются.</p>
 */
public final class ParticleMesh {

    /** Направление света: чуть сверху-слева-спереди, как солнце в кадре. */
    private static final float LIGHT_X = -0.35F;
    private static final float LIGHT_Y = 0.82F;
    private static final float LIGHT_Z = 0.45F;

    /** Грани сферы: каждая — 4 вершины (x, y, z) единичного радиуса. */
    private static final float[] SPHERE_MESH = buildSphere();
    private static final float[] CUBE_MESH = buildCube();
    private static final float[] RING_MESH = buildRing();

    private ParticleMesh() {
    }

    /**
     * Рисует частицу объёмной сеткой.
     *
     * @param shape   форма частицы
     * @param consumer приёмник вершин (один RenderType на форму)
     * @param matrix  матрица позы (мир уже переведён в координаты камеры)
     * @param cx      центр частицы по X (относительно камеры)
     * @param cy      центр по Y
     * @param cz      центр по Z
     * @param radius  радиус частицы в блоках
     * @param yaw     поворот вокруг вертикали (у каждой частицы свой, от фазы)
     * @param tilt    наклон (чтобы частицы не были выстроены в одну линию)
     * @param color   цвет центра
     * @param alpha   прозрачность 0…1
     */
    public static void draw(PoseStack.Pose pose, VertexConsumer consumer, Matrix4f matrix,
                            ParticleShape shape,
                            float cx, float cy, float cz, float radius,
                            float yaw, float tilt, int color, float alpha) {
        if (alpha <= 0.01F || radius <= 1.0E-4F) {
            return;
        }
        // Форма частицы определяет её геометрию: кольца — тор-подобное кольцо,
        // снежинки/звёзды — гранёный кристалл (куб), всё остальное — сфера.
        float[] mesh = switch (shape) {
            case RING -> RING_MESH;
            case SNOWFLAKE, STAR -> CUBE_MESH;
            default -> SPHERE_MESH;
        };
        float cy1 = (float) Math.cos(yaw);
        float sy1 = (float) Math.sin(yaw);
        float ct = (float) Math.cos(tilt);
        float st = (float) Math.sin(tilt);

        int quads = mesh.length / 12;   // 4 вершины × 3 координаты
        for (int q = 0; q < quads; q++) {
            int base = q * 12;
            float ax = mesh[base];
            float ay = mesh[base + 1];
            float az = mesh[base + 2];

            // нормаль грани по первым трём вершинам (в локальных координатах)
            float bx = mesh[base + 3] - ax;
            float by = mesh[base + 4] - ay;
            float bz = mesh[base + 5] - az;
            float cxx = mesh[base + 6] - ax;
            float cyy = mesh[base + 7] - ay;
            float czz = mesh[base + 8] - az;
            float nx = by * czz - bz * cyy;
            float ny = bz * cxx - bx * czz;
            float nz = bx * cyy - by * cxx;
            float nLen = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nLen > 1.0E-6F) {
                nx /= nLen;
                ny /= nLen;
                nz /= nLen;
            }
            float lambert = Math.max(0.0F, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
            // мягкий блик на боку: силуэт не сливается с фоном
            float rim = (float) Math.pow(1.0F - Math.abs(ny), 2.0D) * 0.28F;
            float shade = 0.42F + 0.58F * lambert + rim;
            int faceColor = ColorUtil.brighten(color, Math.min(0.85F, (shade - 0.6F) * 0.9F));
            int packed = ColorUtil.fade(faceColor, alpha);

            for (int v = 0; v < 4; v++) {
                int idx = base + v * 3;
                float vx = mesh[idx];
                float vy = mesh[idx + 1];
                float vz = mesh[idx + 2];
                // наклон вокруг X, затем поворот вокруг Y
                float ry = vy * ct - vz * st;
                float rz = vy * st + vz * ct;
                float rx = vx * cy1 + rz * sy1;
                float rz2 = rz * cy1 - vx * sy1;
                consumer.addVertex(matrix,
                                cx + rx * radius, cy + ry * radius, cz + rz2 * radius)
                        .setColor(packed)
                        .setUv((v == 1 || v == 2) ? 1.0F : 0.0F, (v >= 2) ? 1.0F : 0.0F)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(rx, ry, rz2);
            }
        }
    }

    /** Сфера низкого разрешения: 5 долгот × 3 широты = 15 квадов. */
    private static float[] buildSphere() {
        final int lon = 5;
        final int lat = 3;
        float[] out = new float[lon * lat * 12];
        int i = 0;
        for (int a = 0; a < lon; a++) {
            float a0 = (float) (a / (double) lon * Math.PI * 2.0D);
            float a1 = (float) ((a + 1) / (double) lon * Math.PI * 2.0D);
            for (int b = 0; b < lat; b++) {
                float v0 = b / (float) lat;
                float v1 = (b + 1) / (float) lat;
                float el0 = (v0 - 0.5F) * (float) Math.PI;
                float el1 = (v1 - 0.5F) * (float) Math.PI;
                float r0 = (float) Math.cos(el0);
                float y0 = (float) Math.sin(el0);
                float r1 = (float) Math.cos(el1);
                float y1 = (float) Math.sin(el1);
                // 4 вершины квада
                i = put(out, i, r0 * (float) Math.cos(a0), y0, r0 * (float) Math.sin(a0));
                i = put(out, i, r0 * (float) Math.cos(a1), y0, r0 * (float) Math.sin(a1));
                i = put(out, i, r1 * (float) Math.cos(a1), y1, r1 * (float) Math.sin(a1));
                i = put(out, i, r1 * (float) Math.cos(a0), y1, r1 * (float) Math.sin(a0));
            }
        }
        return out;
    }

    private static float[] buildCube() {
        float h = 0.62F;   // чуть меньше единицы: куб не выпирает из своего радиуса
        float[][] faces = {
                {-h, -h, h, h, -h, h, h, h, h, -h, h, h},        // передняя
                {h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h},    // задняя
                {-h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h},    // левая
                {h, -h, h, h, -h, -h, h, h, -h, h, h, h},        // правая
                {-h, h, h, h, h, h, h, h, -h, -h, h, -h},        // верх
                {-h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h},    // низ
        };
        float[] out = new float[faces.length * 12];
        int i = 0;
        for (float[] face : faces) {
            for (float value : face) {
                out[i++] = value;
            }
        }
        return out;
    }

    /** Кольцо: 6 сегментов, каждое ребро — узкий квад. */
    private static float[] buildRing() {
        final int seg = 6;
        final float outer = 1.0F;
        final float inner = 0.62F;
        float[] out = new float[seg * 12];
        int i = 0;
        for (int s = 0; s < seg; s++) {
            float a0 = (float) (s / (double) seg * Math.PI * 2.0D);
            float a1 = (float) ((s + 1) / (double) seg * Math.PI * 2.0D);
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            i = put(out, i, c0 * inner, s0 * inner, 0.10F);
            i = put(out, i, c0 * outer, s0 * outer, 0.10F);
            i = put(out, i, c1 * outer, s1 * outer, 0.10F);
            i = put(out, i, c1 * inner, s1 * inner, 0.10F);
        }
        return out;
    }

    private static int put(float[] out, int idx, float x, float y, float z) {
        out[idx] = x;
        out[idx + 1] = y;
        out[idx + 2] = z;
        return idx + 3;
    }

    /** Общая проверка: сетки непустые и кратны кваду. */
    public static boolean meshesReady() {
        return SPHERE_MESH.length > 0 && SPHERE_MESH.length % 12 == 0
                && CUBE_MESH.length % 12 == 0 && RING_MESH.length % 12 == 0;
    }

    static int sphereQuadCount() {
        return SPHERE_MESH.length / 12;
    }

    static Vector3f lightDirection(Vector3f target) {
        return target.set(LIGHT_X, LIGHT_Y, LIGHT_Z);
    }
}
