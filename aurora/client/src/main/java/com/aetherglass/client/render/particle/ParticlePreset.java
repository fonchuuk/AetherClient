package com.aetherglass.client.render.particle;

/**
 * Готовый набор значений для модуля частиц.
 *
 * <p>Пресет не «включает» ничего сам по себе: он хранит числа, а модуль применяет их
 * к своим настройкам. Поэтому после выбора пресета все ползунки можно крутить дальше —
 * получится свой вариант.</p>
 */
public enum ParticlePreset {
    DEFAULT("Обычный", ParticleShape.CIRCLE, 0xFF7FD4FF, 0xFFB98CFF, 0.16F, 0.85F, 0.55F, 2.4F, -0.12F, 12, 0.25F),
    SNOW("Снег", ParticleShape.CIRCLE, 0xFFFFFFFF, 0xFFDCE9FF, 0.13F, 0.75F, 0.30F, 4.5F, -0.30F, 22, 0.35F),
    EMBER("Угольки", ParticleShape.SPARK, 0xFFFF9A3C, 0xFFFF3B1F, 0.12F, 0.95F, 0.70F, 1.6F, 0.22F, 26, 0.18F),
    MAGIC("Магия", ParticleShape.STAR, 0xFFB388FF, 0xFF5BE8FF, 0.18F, 0.90F, 0.45F, 3.0F, 0.05F, 20, 0.30F),
    SMOKE("Дым", ParticleShape.RING, 0xFFB9C0CC, 0xFF6E7683, 0.34F, 0.45F, 0.25F, 3.6F, 0.12F, 10, 0.55F),
    HEARTS("Сердца", ParticleShape.HEART, 0xFFFF6B8B, 0xFFFFB3C6, 0.16F, 0.95F, 0.40F, 2.2F, -0.05F, 8, 0.28F);

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
}
