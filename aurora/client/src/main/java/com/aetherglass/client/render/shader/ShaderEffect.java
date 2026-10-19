package com.aetherglass.client.render.shader;

/**
 * 16 уникальных процедурных шейдер-эффектов для {@code BlockShader} и {@code HandsShader}.
 *
 * <p>Каждый эффект описывает собственный алгоритм расчёта поверхности, граней и
 * дополнительных геометрических слоёв (разряды молний, кибер-сетка, голографические
 * scanline-линии, кристаллы льда, огненное ядро, звёздная пыль, разломы пустоты и т.д.).</p>
 */
public enum ShaderEffect {
    LIQUID_GLASS(
            1,
            "liquid_glass",
            "Liquid Glass",
            "Полупрозрачное жидкое стекло с радужными бликами, мягкими отражениями и преломлением света.",
            0xC878DCFF,
            0xFFE8F8FF,
            1.00F,
            1.00F,
            0.78F
    ),
    RAINBOW_FLOW(
            2,
            "rainbow_flow",
            "Rainbow Flow",
            "Плавный непрерывный перелив всех цветов спектра по поверхности с бегущей волной.",
            0xDCFF64B4,
            0xFF64FFDA,
            1.05F,
            1.10F,
            0.80F
    ),
    NEON_PULSE(
            3,
            "neon_pulse",
            "Neon Pulse",
            "Неоновое свечение с глубокой пульсацией яркости, яркими гранями и мягким ореолом.",
            0xE000F5D4,
            0xFFFF2A85,
            1.20F,
            1.00F,
            0.82F
    ),
    ENERGY_WAVES(
            4,
            "energy_waves",
            "Energy Waves",
            "Бегущие энергетические волны и концентрические импульсы по поверхности.",
            0xD838BDF8,
            0xFFA855F7,
            1.10F,
            1.15F,
            0.76F
    ),
    PLASMA(
            5,
            "plasma",
            "Plasma",
            "Анимированная многочастотная плазма с органичными цветовыми переходами.",
            0xDCDA44FF,
            0xFF38F9D7,
            1.15F,
            0.95F,
            0.78F
    ),
    ELECTRIC_ARCS(
            6,
            "electric_arcs",
            "Electric Arcs",
            "Высоковольтные электрические разряды и ветвящиеся линии молний по граням.",
            0xE64CC9F0,
            0xFFFFFFFF,
            1.25F,
            1.35F,
            0.85F
    ),
    HOLOGRAM(
            7,
            "hologram",
            "Hologram",
            "Голографическая проекция с горизонтальными scanline-полосами, мерцанием и хроматическим сдвигом.",
            0xCC00E5FF,
            0xFFFF40A0,
            1.05F,
            1.10F,
            0.72F
    ),
    CYBER_GRID(
            8,
            "cyber_grid",
            "Cyber Grid",
            "Анимированная кибер-сетка с чёткими линиями матрицы и пульсирующими узлами.",
            0xDC10F5A0,
            0xFF00B4D8,
            1.15F,
            1.00F,
            0.80F
    ),
    LIQUID_METAL(
            9,
            "liquid_metal",
            "Liquid Metal",
            "Хромированная поверхность жидкого металла с бегущими зеркальными бликами.",
            0xE0C8DCFF,
            0xFF5A78A8,
            1.20F,
            0.90F,
            0.86F
    ),
    AURORA(
            10,
            "aurora",
            "Aurora",
            "Мягкие северные сияния с плавными ленточными переходами изумрудного и фиолетового.",
            0xD448F5B6,
            0xFF9B5DE5,
            1.05F,
            0.85F,
            0.76F
    ),
    GLITCH(
            11,
            "glitch",
            "Glitch",
            "Цифровые артефакты, RGB-сдвиг каналов, разрывы строк и фрагментированные искажения.",
            0xE0FF2A6D,
            0xFF05D9E8,
            1.20F,
            1.40F,
            0.82F
    ),
    FIRE_CORE(
            12,
            "fire_core",
            "Fire Core",
            "Огненные турбулентные потоки, раскалённое ядро и восходящее движение пламени.",
            0xE6FF6B1A,
            0xFFFFF07C,
            1.25F,
            1.20F,
            0.84F
    ),
    ICE_CRYSTAL(
            13,
            "ice_crystal",
            "Ice Crystal",
            "Кристаллическая ледяная грань с холодными бликами и ветвящимися морозными линиями.",
            0xDC7CE8FF,
            0xFFEAFBFF,
            1.10F,
            0.80F,
            0.80F
    ),
    SPECTRAL_OUTLINE(
            14,
            "spectral_outline",
            "Spectral Outline",
            "Спектральный контур поверхности с многослойным свечением и бегом цвета по рёбрам.",
            0xE68A5CFF,
            0xFF00F5D4,
            1.20F,
            1.15F,
            0.84F
    ),
    COSMIC_DUST(
            15,
            "cosmic_dust",
            "Cosmic Dust",
            "Космическая туманность с мерцающими звёздными точками и глубоким смещением цвета.",
            0xDC7B2CBF,
            0xFF48CAE4,
            1.15F,
            0.75F,
            0.82F
    ),
    VOID_DISTORTION(
            16,
            "void_distortion",
            "Void Distortion",
            "Тёмная энергия пустоты с фиолетово-синими вихрями, пульсирующими разломами и нестабильностью.",
            0xE65A189A,
            0xFFC77DFF,
            1.30F,
            1.05F,
            0.86F
    );

    private final int number;
    private final String id;
    private final String displayName;
    private final String description;
    private final int defaultColor;
    private final int secondaryColor;
    private final float defaultIntensity;
    private final float defaultSpeed;
    private final float defaultOpacity;

    ShaderEffect(int number, String id, String displayName, String description,
                 int defaultColor, int secondaryColor,
                 float defaultIntensity, float defaultSpeed, float defaultOpacity) {
        this.number = number;
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.defaultColor = defaultColor;
        this.secondaryColor = secondaryColor;
        this.defaultIntensity = defaultIntensity;
        this.defaultSpeed = defaultSpeed;
        this.defaultOpacity = defaultOpacity;
    }

    /** Порядковый номер эффекта (1..16). */
    public int number() {
        return this.number;
    }

    /** Строковый идентификатор эффекта (например, {@code liquid_glass}). */
    public String id() {
        return this.id;
    }

    /** Название эффекта (например, {@code Liquid Glass}). */
    public String displayName() {
        return this.displayName;
    }

    /** Форматированный заголовок карточки с двузначным номером. */
    public String cardTitle() {
        return String.format("%02d · %s", this.number, this.displayName);
    }

    /** Краткое описание визуального характера эффекта. */
    public String description() {
        return this.description;
    }

    /** Основной цвет эффекта по умолчанию (ARGB). */
    public int defaultColor() {
        return this.defaultColor;
    }

    /** Дополнительный акцентный цвет эффекта (ARGB). */
    public int secondaryColor() {
        return this.secondaryColor;
    }

    /** Стандартная интенсивность эффекта (0.1..2.0). */
    public float defaultIntensity() {
        return this.defaultIntensity;
    }

    /** Стандартная скорость анимации (0.1..3.0). */
    public float defaultSpeed() {
        return this.defaultSpeed;
    }

    /** Стандартная непрозрачность эффекта (0.05..1.0). */
    public float defaultOpacity() {
        return this.defaultOpacity;
    }

    /** Поиск эффекта по идентификатору, имени константы или отображаемому имени. */
    public static ShaderEffect byId(String raw) {
        if (raw == null || raw.isBlank()) {
            return LIQUID_GLASS;
        }
        String normalized = raw.trim().toLowerCase().replace(' ', '_').replace('-', '_');
        for (ShaderEffect effect : values()) {
            if (effect.id.equalsIgnoreCase(normalized)
                    || effect.name().equalsIgnoreCase(normalized)
                    || effect.displayName.equalsIgnoreCase(raw.trim())) {
                return effect;
            }
        }
        return LIQUID_GLASS;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
