package com.aetherglass.client.modules.impl;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.material.ColorSource;
import com.aetherglass.client.render.material.FillMode;
import com.aetherglass.client.render.material.Material;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Эффекты блоков ({@code BlockShader}): 16 процедурных шейдер-эффектов, полупрозрачная
 * стеклянная заливка граней и 3D-светящаяся обводка контура ПОВЕРХ заливки
 * по реальной геометрии {@code VoxelShape} блока под прицелом.
 */
public final class BlockEffectsModule extends Module {
    private static final String MATERIAL_KEY = "blocks.material";

    // ─────────────────────── Главные настройки BlockShader (Блок 4.1) ───────────────────────
    private final BooleanSetting useShaderEffect = register(new BooleanSetting("useShader",
            "Шейдер-эффект (BlockShader)",
            "Включает один из 16 процедурных шейдер-эффектов для блока под прицелом",
            true, setting -> settingChanged(setting)));
    private final EnumSetting<ShaderEffect> shaderEffect = register(new EnumSetting<>("shaderEffect",
            "Эффект BlockShader",
            "Выбор одного из 16 уникальных шейдер-эффектов для блоков",
            ShaderEffect.LIQUID_GLASS, ShaderEffect.values(), setting -> settingChanged(setting)));
    private final BooleanSetting showFill = register(new BooleanSetting("showFill",
            "Заливка граней блока",
            "Полупрозрачная шейдерная заливка граней блока (не пропадает при включении обводки)",
            true, setting -> settingChanged(setting)));
    private final FloatSetting shaderOpacity = register(new FloatSetting("shaderOpacity",
            "Плотность заливки",
            "Непрозрачность полупрозрачной заливки граней блока",
            0.46F, 0.05F, 1.00F, 0.02F, setting -> {
                syncFillAlphaFromOpacity();
                settingChanged(setting);
            }));
    private final BooleanSetting showOutline = register(new BooleanSetting("showOutline",
            "Обводка контура (поверх)",
            "Рисует светящийся 3D-контур поверх полупрозрачной заливки граней",
            true, setting -> settingChanged(setting)));
    private final FloatSetting outlineWidth = register(new FloatSetting("outlineWidth",
            "Толщина обводки",
            "Толщина светящихся линий контура поверх заливки блока",
            0.018F, 0.004F, 0.060F, 0.002F, setting -> settingChanged(setting)));
    private final FloatSetting glow = register(new FloatSetting("glow",
            "Сила свечения",
            "Интенсивность мягкого ореола свечения вокруг рёбер и граней блока",
            0.65F, 0.00F, 1.50F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting outlineAlpha = register(new FloatSetting("outlineAlpha",
            "Яркость обводки",
            "Яркость линий контура поверх заливки",
            0.92F, 0.10F, 1.00F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting shaderIntensity = register(new FloatSetting("shaderIntensity",
            "Интенсивность шейдера",
            "Яркость, контраст и выраженность шейдер-эффекта блока",
            1.00F, 0.10F, 2.00F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting shaderSpeed = register(new FloatSetting("shaderSpeed",
            "Скорость анимации",
            "Скорость течения волн, разрядов и переливов шейдера блока",
            1.00F, 0.10F, 3.00F, 0.05F, setting -> settingChanged(setting)));
    private final EnumSetting<FaceGradientMode> faceGradientMode = register(new EnumSetting<>("faceGradientMode",
            "Режим граней (Капля / Блик)",
            "Как заполняются грани блока: капля/линза, солнечный блик или полный шейдер",
            FaceGradientMode.MENISCUS_DROP, FaceGradientMode.values(), setting -> settingChanged(setting)));
    private final ColorSetting shaderColor = register(new ColorSetting("shaderColor",
            "Цвет шейдера и обводки",
            "Основной цвет выбранного шейдер-эффекта и контура блока",
            0xFFAA3DFF, setting -> {
                syncCustomColorFromShader();
                settingChanged(setting);
            }));
    private final EnumSetting<ColorSource> colorSource = register(new EnumSetting<>("colorSource",
            "Источник цвета",
            "Откуда берётся цвет подсветки: свой цвет, акцент темы или радуга",
            ColorSource.CUSTOM, ColorSource.values(), setting -> settingChanged(setting)));
    private final BooleanSetting rim = register(new BooleanSetting("rim",
            "Солнечный блик по кромке",
            "Дополнительная яркая грань по верхним рёбрам блока",
            true, setting -> settingChanged(setting)));
    private final FloatSetting pulseSpeed = register(new FloatSetting("pulse",
            "Дыхание подсветки",
            "Скорость плавной пульсации: 0 — ровный свет",
            0.4F, 0.0F, 4.0F, 0.1F, setting -> settingChanged(setting)));

    private final BooleanSetting hitParticles = register(new BooleanSetting("hitParticles",
            "Частицы при касании блока",
            "Вспышка частиц в точке удара или взаимодействия с блоком",
            true, setting -> settingChanged(setting)));
    private final IntSetting hitCount = register(new IntSetting("hitCount",
            "Частиц за касание",
            "Сколько частиц рождается за одно нажатие по блоку",
            8, 1, 40, setting -> settingChanged(setting)));
    private final FloatSetting hitSpeed = register(new FloatSetting("hitSpeed",
            "Скорость разлёта частиц",
            "Скорость разлёта частиц взаимодействия",
            1.2F, 0.2F, 5.0F, 0.1F, setting -> settingChanged(setting)));

    // Внутренние совместимые настройки для материалов и тестов
    private final EnumSetting<FillMode> mode = new EnumSetting<>("mode", "Режим заливки",
            "Режим заливки материала", FillMode.SHADER_FILL, FillMode.values(), setting -> settingChanged(setting));
    private final ColorSetting color = new ColorSetting("color", "Свой цвет",
            "Цвет материала", 0xFF8FE3FF, setting -> {
                this.shaderColor.set(setting.get());
                settingChanged(setting);
            });
    private final FloatSetting fillAlpha = new FloatSetting("fillAlpha", "Плотность заливки",
            "Плотность заливки материала", 0.46F, 0.0F, 1.0F, 0.05F, setting -> {
                this.shaderOpacity.set(Math.max(0.05F, setting.get()));
                settingChanged(setting);
            });
    private final FloatSetting gradientSpeed = new FloatSetting("gradientSpeed", "Скорость блика",
            "Скорость блика материала", 0.55F, 0.0F, 2.0F, 0.05F, setting -> settingChanged(setting));

    private boolean syncingColors;
    private boolean syncingOpacity;
    private BlockPos outlined;
    private float outlineStrength;
    private float outlineFade;
    private float effectTime;
    private boolean attackWasDown;
    private boolean useWasDown;
    private String activeMaterial = "glass";

    public BlockEffectsModule() {
        super("blocks", "Эффекты блоков",
                "BlockShader: полупрозрачная заливка и 3D-обводка поверх заливки с настройкой толщины и свечения",
                ModuleCategory.VISUAL);
        String saved = ModConfig.get().getString(MATERIAL_KEY, "");
        if (!saved.isBlank()) {
            this.activeMaterial = saved;
        }
    }

    private void syncCustomColorFromShader() {
        if (this.syncingColors || this.color == null) {
            return;
        }
        this.syncingColors = true;
        try {
            this.color.set(this.shaderColor.get());
        } finally {
            this.syncingColors = false;
        }
    }

    private void syncFillAlphaFromOpacity() {
        if (this.syncingOpacity || this.fillAlpha == null) {
            return;
        }
        this.syncingOpacity = true;
        try {
            this.fillAlpha.set(this.shaderOpacity.get());
        } finally {
            this.syncingOpacity = false;
        }
    }

    @Override
    protected void onDisable() {
        this.outlined = null;
        this.outlineStrength = 0.0F;
        this.outlineFade = 0.0F;
        this.attackWasDown = false;
        this.useWasDown = false;
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    // ─────────────────────── BlockShader (16 эффектов) ───────────────────────

    public boolean useShaderEffect() {
        return this.useShaderEffect.isEnabled();
    }

    public void setUseShaderEffect(boolean enabled) {
        this.useShaderEffect.set(enabled);
        settingChanged(this.useShaderEffect);
    }

    public boolean showFill() {
        return this.showFill.isEnabled();
    }

    public void setShowFill(boolean enabled) {
        this.showFill.set(enabled);
        settingChanged(this.showFill);
    }

    public boolean showOutline() {
        return this.showOutline.isEnabled();
    }

    public void setShowOutline(boolean enabled) {
        this.showOutline.set(enabled);
        settingChanged(this.showOutline);
    }

    public ShaderEffect shaderEffect() {
        return this.shaderEffect.get();
    }

    public void setShaderEffect(ShaderEffect effect) {
        if (effect != null) {
            this.shaderEffect.set(effect);
            settingChanged(this.shaderEffect);
        }
    }

    public FaceGradientMode faceGradientMode() {
        return this.faceGradientMode.get();
    }

    public EnumSetting<FaceGradientMode> faceGradientModeSetting() {
        return this.faceGradientMode;
    }

    /** Выбирает шейдер-эффект и применяет его эталонные параметры по умолчанию. */
    public void selectShaderPreset(ShaderEffect effect) {
        if (effect == null) {
            return;
        }
        this.shaderEffect.set(effect);
        this.shaderColor.set(effect.defaultColor());
        this.shaderIntensity.set(effect.defaultIntensity());
        this.shaderSpeed.set(effect.defaultSpeed());
        this.shaderOpacity.set(effect.defaultOpacity());
        this.showFill.set(true);
        this.showOutline.set(true);
        settingChanged(this.shaderEffect);
    }

    /** Сбрасывает параметры текущего выбранного шейдер-эффекта к его эталонным значениям. */
    public void resetCurrentShaderEffect() {
        selectShaderPreset(this.shaderEffect.get());
    }

    /** Полный сброс всех настроек BlockShader к заводским значениям. */
    public void resetShaderDefaults() {
        this.useShaderEffect.reset();
        this.showFill.reset();
        this.showOutline.reset();
        selectShaderPreset(ShaderEffect.LIQUID_GLASS);
        this.faceGradientMode.reset();
        this.outlineWidth.reset();
        this.glow.reset();
        this.outlineAlpha.reset();
        settingChanged(this.shaderEffect);
    }

    public int shaderColor() {
        return switch (this.colorSource.get()) {
            case ACCENT -> ColorController.accent();
            case RAINBOW -> ColorController.isCycling() ? ColorController.current() : AuroraTheme.accent();
            case CUSTOM -> ColorController.resolve(this.shaderColor.get());
        };
    }

    public void setShaderColor(int colorValue) {
        this.shaderColor.set(colorValue);
        settingChanged(this.shaderColor);
    }

    public ColorSetting shaderColorSetting() {
        return this.shaderColor;
    }

    public float shaderIntensity() {
        return this.shaderIntensity.get();
    }

    public void setShaderIntensity(float value) {
        this.shaderIntensity.set(value);
        settingChanged(this.shaderIntensity);
    }

    public FloatSetting shaderIntensitySetting() {
        return this.shaderIntensity;
    }

    public float shaderSpeed() {
        return this.shaderSpeed.get();
    }

    public void setShaderSpeed(float value) {
        this.shaderSpeed.set(value);
        settingChanged(this.shaderSpeed);
    }

    public FloatSetting shaderSpeedSetting() {
        return this.shaderSpeed;
    }

    public float shaderOpacity() {
        return this.shaderOpacity.get();
    }

    public void setShaderOpacity(float value) {
        this.shaderOpacity.set(value);
        settingChanged(this.shaderOpacity);
    }

    public FloatSetting shaderOpacitySetting() {
        return this.shaderOpacity;
    }

    // ─────────────────────── Общие геттеры ───────────────────────

    public FillMode mode() {
        return this.mode.get();
    }

    public ColorSource colorSource() {
        return this.colorSource.get();
    }

    public int customColor() {
        return this.shaderColor.get();
    }

    public float fillAlpha() {
        return this.shaderOpacity.get();
    }

    public float outlineWidth() {
        return this.outlineWidth.get();
    }

    public FloatSetting outlineWidthSetting() {
        return this.outlineWidth;
    }

    public float outlineAlpha() {
        return this.outlineAlpha.get();
    }

    public float glow() {
        return this.glow.get();
    }

    public FloatSetting glowSetting() {
        return this.glow;
    }

    public float gradientSpeed() {
        return this.gradientSpeed.get();
    }

    public boolean rim() {
        return this.rim.isEnabled();
    }

    public BlockPos outlined() {
        return this.outlined;
    }

    public float outlineStrength() {
        return this.outlineStrength * this.outlineFade;
    }

    public float effectTime() {
        return this.effectTime;
    }

    public int resolvedColor() {
        return shaderColor();
    }

    private float pulsePhase() {
        return (float) (0.5D + 0.5D * Math.sin(this.effectTime * this.pulseSpeed.get() * Math.PI * 2.0D));
    }

    public String activity() {
        String prefix = this.useShaderEffect.isEnabled() ? (this.shaderEffect.get().displayName() + " · ") : "";
        return prefix + (this.outlined != null ? "блок выбран" : "ожидание прицела");
    }

    @Override
    public void tick(float deltaSeconds) {
        this.effectTime += deltaSeconds;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            return;
        }
        updateOutlineTarget(client, deltaSeconds);
        updateInteractions(client);
    }

    private void updateOutlineTarget(Minecraft client, float deltaSeconds) {
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos position = blockHit.getBlockPos();
            if (!client.level.getBlockState(position).isAir()) {
                if (!position.equals(this.outlined)) {
                    this.outlined = position.immutable();
                }
                this.outlineFade = Math.min(1.0F, this.outlineFade + deltaSeconds * 9.0F);
                if (this.pulseSpeed.get() > 0.0F) {
                    this.outlineStrength = 0.72F + 0.28F * pulsePhase();
                } else {
                    this.outlineStrength = 1.0F;
                }
                return;
            }
        }
        this.outlineFade = Math.max(0.0F, this.outlineFade - deltaSeconds * 7.0F);
        if (this.outlineFade <= 0.0F) {
            this.outlined = null;
        }
    }

    private void updateInteractions(Minecraft client) {
        if (client.options == null) {
            return;
        }
        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        if (this.hitParticles.isEnabled()) {
            if (attackDown && !this.attackWasDown) {
                burst(client, this.hitCount.get(), this.hitSpeed.get());
            }
            if (useDown && !this.useWasDown) {
                burst(client, Math.max(2, this.hitCount.get() / 2), this.hitSpeed.get() * 0.6F);
            }
        }
        this.attackWasDown = attackDown;
        this.useWasDown = useDown;
    }

    private void burst(Minecraft client, int count, float speed) {
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        ParticleModule particles = ModuleManager.get().typed("particles", ParticleModule.class);
        if (particles == null || !particles.isEnabled()) {
            return;
        }
        Vec3 point = blockHit.getLocation();
        particles.spawnAt(point.x, point.y, point.z, speed, count);
    }

    // ─────────────────────────── Материалы ───────────────────────────

    public String activeMaterialId() {
        return this.activeMaterial;
    }

    public void applyMaterial(Material material) {
        if (material == null) {
            return;
        }
        this.mode.set(material.mode());
        this.colorSource.set(material.colorSource());
        this.shaderColor.set(material.color());
        this.shaderOpacity.set(Math.max(0.15F, material.fillAlpha()));
        this.outlineWidth.set(material.outlineWidth());
        this.outlineAlpha.set(material.outlineAlpha());
        this.glow.set(material.glow());
        this.gradientSpeed.set(material.gradientSpeed());
        this.rim.set(material.rim());
        this.showFill.set(true);
        this.showOutline.set(true);
        this.activeMaterial = material.id();
        ModConfig.get().set(MATERIAL_KEY, material.id());
        ModConfig.get().saveIfNeeded();
    }

    public Material toMaterial(String id, String name) {
        return new Material(id, name, false, this.mode.get(), this.colorSource.get(), this.shaderColor.get(),
                this.shaderOpacity.get(), this.outlineWidth.get(), this.outlineAlpha.get(), this.glow.get(),
                this.gradientSpeed.get(), this.rim.get());
    }
}
