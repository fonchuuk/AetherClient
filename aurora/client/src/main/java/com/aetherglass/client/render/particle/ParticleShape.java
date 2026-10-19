package com.aetherglass.client.render.particle;

import net.minecraft.resources.Identifier;

/**
 * Форма частиц в стиле Liquid Glass со светящимися градиентными спрайтами (Блок 1.3).
 *
 * <p>Спрайты генерируются инструментом {@code tools/gen_particle_textures.py}
 * в разрешении 64×64 с мягким радиальным свечением и ярким ядром.</p>
 */
public enum ParticleShape {
    STAR("star", "Звезды"),
    SPARK("spark", "Искры"),
    RING("ring", "Кольца"),
    SNOWFLAKE("snowflake", "Снежинки"),
    CIRCLE("circle", "Сферы"),
    HEART("heart", "Сердца");

    /** Алиасы для совместимости со старыми пресетами. */
    public static final ParticleShape SQUARE = SNOWFLAKE;
    public static final ParticleShape DIAMOND = SNOWFLAKE;

    private final Identifier texture;
    private final String title;

    ParticleShape(String file, String title) {
        this.texture = Identifier.fromNamespaceAndPath("aetherglass", "textures/particle/" + file + ".png");
        this.title = title;
    }

    public Identifier texture() {
        return this.texture;
    }

    public String title() {
        return this.title;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
