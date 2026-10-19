package com.aetherglass.client.render.particle;

/**
 * Готовый набор значений для модуля мировых частиц ({@code Particles}, Блок 4.5).
 */
public enum ParticlePreset {
    DEFAULT("Кристаллы мира", ParticleShape.STAR, 0xFF7FD4FF, 0xFFB98CFF, 0.17F, 0.90F, 0.18F, 7.5F, -0.02F, 24, 0.25F),
    SNOW("Зимний снегопад", ParticleShape.SNOWFLAKE, 0xFFFFFFFF, 0xFFDCE9FF, 0.15F, 0.85F, 0.15F, 8.5F, -0.18F, 30, 0.30F),
    EMBER("Огненные искры", ParticleShape.SPARK, 0xFFFF9A3C, 0xFFFF3B1F, 0.14F, 0.95F, 0.25F, 6.0F, 0.12F, 28, 0.20F),
    MAGIC("Эфирная магия", ParticleShape.DIAMOND, 0xFFB388FF, 0xFF5BE8FF, 0.18F, 0.92F, 0.16F, 8.0F, 0.02F, 26, 0.28F),
    SMOKE("Кольца тумана", ParticleShape.RING, 0xFFB9C0CC, 0xFF6E7683, 0.28F, 0.55F, 0.12F, 9.0F, 0.04F, 16, 0.45F),
    HEARTS("Сердца", ParticleShape.HEART, 0xFFFF6B8B, 0xFFFFB3C6, 0.17F, 0.95F, 0.16F, 7.0F, -0.02F, 18, 0.25F);

    private final String title;
    private final ParticleShape shape;
    private final int colorA;
    private final int colorB;
    private final float size;
    private final float alpha;
    private final float speed;
    private final float lifetime;
    private final float gravity;
    private final int rate;
    private final float fade;

    @SuppressWarnings("checkstyle:ParameterNumber")
    ParticlePreset(String title, ParticleShape shape, int colorA, int colorB, float size, float alpha, float speed,
                   float lifetime, float gravity, int rate, float fade) {
        this.title = title;
        this.shape = shape;
        this.colorA = colorA;
        this.colorB = colorB;
        this.size = size;
        this.alpha = alpha;
        this.speed = speed;
        this.lifetime = lifetime;
        this.gravity = gravity;
        this.rate = rate;
        this.fade = fade;
    }

    public String title() {
        return this.title;
    }

    public ParticleShape shape() {
        return this.shape;
    }

    public int colorA() {
        return this.colorA;
    }

    public int colorB() {
        return this.colorB;
    }

    public float size() {
        return this.size;
    }

    public float alpha() {
        return this.alpha;
    }

    public float speed() {
        return this.speed;
    }

    public float lifetime() {
        return this.lifetime;
    }

    public float gravity() {
        return this.gravity;
    }

    public int rate() {
        return this.rate;
    }

    public float fade() {
        return this.fade;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
