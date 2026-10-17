package com.aetherglass.client.render.particle;

import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3fc;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Пул частиц клиента.
 *
 * <p>Частицы живут в заранее выделенном массиве: в цикле отрисовки не создаётся ни одного
 * объекта, а число частиц ограничено настройкой. Каждая частица умеет плавно появляться,
 * жить, менять цвет от начального к конечному и плавно гаснуть.</p>
 */
public final class ParticleSystem {
    private Particle[] pool;
    private int alive;

    public ParticleSystem(int capacity) {
        this.pool = new Particle[Math.max(16, capacity)];
        for (int index = 0; index < this.pool.length; index++) {
            this.pool[index] = new Particle();
        }
    }

    /** Частица: простые поля, никаких объектов и коллекций внутри. */
    private static final class Particle {
        private double x;
        private double y;
        private double z;
        private double motionX;
        private double motionY;
        private double motionZ;
        private float age;
        private float life;
        private float size;
        private float alpha;
        private float gravity;
        private float drag;
        private float spin;
        private float spinSpeed;
        private int colorFrom;
        private int colorTo;
        private ParticleShape shape;

        private void reset(double x, double y, double z, double motionX, double motionY, double motionZ, float life,
                           float size, float alpha, float gravity, float drag, float spinSpeed, int colorFrom,
                           int colorTo, ParticleShape shape) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.life = life;
            this.size = size;
            this.alpha = alpha;
            this.gravity = gravity;
            this.drag = drag;
            this.colorFrom = colorFrom;
            this.colorTo = colorTo;
            this.shape = shape;
            this.age = 0.0F;
            this.spin = (float) (Math.random() * Math.PI * 2.0D);
            this.spinSpeed = spinSpeed;
        }
    }

    public int alive() {
        return this.alive;
    }

    public int capacity() {
        return this.pool.length;
    }

    /** Меняет предел частиц; существующие частицы сохраняются. */
    public void setCapacity(int capacity) {
        int size = Math.max(16, capacity);
        if (size == this.pool.length) {
            return;
        }
        Particle[] next = new Particle[size];
        int keep = Math.min(this.alive, size);
        System.arraycopy(this.pool, 0, next, 0, keep);
        for (int index = keep; index < size; index++) {
            next[index] = new Particle();
        }
        this.pool = next;
        this.alive = keep;
    }

    public void clear() {
        this.alive = 0;
    }

    /**
     * Пытается создать частицу.
     *
     * @return {@code true}, если частица создана; при достижении предела — {@code false}
     */
    public boolean spawn(double x, double y, double z, double motionX, double motionY, double motionZ, float life,
                         float size, float alpha, float gravity, float drag, float spinSpeed, int colorFrom,
                         int colorTo, ParticleShape shape) {
        if (this.alive >= this.pool.length) {
            return false;
        }
        this.pool[this.alive].reset(x, y, z, motionX, motionY, motionZ, Math.max(0.05F, life), size, alpha, gravity,
                drag, spinSpeed, colorFrom, colorTo, shape);
        this.alive++;
        return true;
    }

    /** Продвигает физику и время жизни всех частиц. */
    public void update(float deltaSeconds) {
        int index = 0;
        while (index < this.alive) {
            Particle particle = this.pool[index];
            particle.age += deltaSeconds;
            if (particle.age >= particle.life) {
                // Меняемся местами с последней живой частицей: без сдвигов массива.
                Particle last = this.pool[this.alive - 1];
                this.pool[this.alive - 1] = particle;
                this.pool[index] = last;
                this.alive--;
                continue;
            }
            double damping = Math.pow(1.0F - particle.drag, deltaSeconds * 60.0F);
            particle.motionX *= damping;
            particle.motionZ *= damping;
            particle.motionY = particle.motionY * damping + particle.gravity * deltaSeconds;
            particle.x += particle.motionX * deltaSeconds;
            particle.y += particle.motionY * deltaSeconds;
            particle.z += particle.motionZ * deltaSeconds;
            particle.spin += particle.spinSpeed * deltaSeconds;
            index++;
        }
    }

    /**
     * Рисует частицы в мире: каждая — повёрнутая к камере плоскость со спрайтом формы.
     *
     * @param fadeInSeconds  длительность появления
     * @param fadeOutSeconds длительность затухания
     */
    public void render(PoseStack pose, MultiBufferSource buffers, Camera camera, float fadeInSeconds,
                       float fadeOutSeconds) {
        if (this.alive == 0) {
            return;
        }
        Matrix4f matrix = pose.last().pose();
        Vec3 origin = camera.position();
        float cameraX = (float) origin.x;
        float cameraY = (float) origin.y;
        float cameraZ = (float) origin.z;
        // Базис плоскости: «вправо» и «вверх» относительно взгляда.
        Vector3fc left = camera.leftVector();
        Vector3fc up = camera.upVector();
        float rightX = -left.x();
        float rightY = -left.y();
        float rightZ = -left.z();
        float upX = up.x();
        float upY = up.y();
        float upZ = up.z();

        // Проход по формам: так буферы переключаются не чаще шести раз за кадр.
        ParticleShape[] shapes = ParticleShape.values();
        for (ParticleShape shape : shapes) {
            VertexConsumer consumer = null;
            for (int index = 0; index < this.alive; index++) {
                Particle particle = this.pool[index];
                if (particle.shape != shape) {
                    continue;
                }
                if (consumer == null) {
                    consumer = buffers.getBuffer(RenderTypes.entityTranslucent(shape.texture()));
                }
                float progress = particle.age / particle.life;
                float appear = fadeInSeconds > 0.0F
                        ? Easing.easeOutCubic(Math.min(1.0F, particle.age / fadeInSeconds))
                        : 1.0F;
                float remaining = particle.life - particle.age;
                float disappear = fadeOutSeconds > 0.0F
                        ? Easing.easeInCubic(Math.min(1.0F, remaining / fadeOutSeconds))
                        : 1.0F;
                float opacity = particle.alpha * appear * disappear;
                if (opacity <= 0.004F) {
                    continue;
                }
                float scale = particle.size * (0.35F + 0.65F * appear);
                int color = ColorUtil.withAlpha(ColorUtil.lerp(particle.colorFrom, particle.colorTo, progress),
                        Math.round(255 * Math.min(1.0F, opacity)));

                float spin = particle.spin;
                float cos = (float) Math.cos(spin);
                float sin = (float) Math.sin(spin);
                float rx = (rightX * cos - upX * sin) * scale;
                float ry = (rightY * cos - upY * sin) * scale;
                float rz = (rightZ * cos - upZ * sin) * scale;
                float ux = (rightX * sin + upX * cos) * scale;
                float uy = (rightY * sin + upY * cos) * scale;
                float uz = (rightZ * sin + upZ * cos) * scale;

                float px = (float) particle.x - cameraX;
                float py = (float) particle.y - cameraY;
                float pz = (float) particle.z - cameraZ;

                consumer.addVertex(matrix, px - rx - ux, py - ry - uy, pz - rz - uz)
                        .setUv(0.0F, 0.0F).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertex(matrix, px + rx - ux, py + ry - uy, pz + rz - uz)
                        .setUv(1.0F, 0.0F).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertex(matrix, px + rx + ux, py + ry + uy, pz + rz + uz)
                        .setUv(1.0F, 1.0F).setColor(color).setLight(LightTexture.FULL_BRIGHT);
                consumer.addVertex(matrix, px - rx + ux, py - ry + uy, pz - rz + uz)
                        .setUv(0.0F, 1.0F).setColor(color).setLight(LightTexture.FULL_BRIGHT);
            }
        }
    }
}
