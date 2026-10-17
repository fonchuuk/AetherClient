package com.aetherglass.client.render.particle;

import net.minecraft.resources.Identifier;

/**
 * Форма частицы: каждая форма — свой спрайт с мягкими краями.
 *
 * <p>Спрайты генерируются инструментом {@code tools/gen_particle_textures.py};
 * без альфа-канала нельзя было бы плавно гасить частицу по краям.</p>
 */
public enum ParticleShape {
    CIRCLE("circle", "Круг"),
    SQUARE("square", "Квадрат"),
    STAR("star", "Звезда"),
    RING("ring", "Кольцо"),
    HEART("heart", "Сердце"),
    SPARK("spark", "Искра");

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
}
