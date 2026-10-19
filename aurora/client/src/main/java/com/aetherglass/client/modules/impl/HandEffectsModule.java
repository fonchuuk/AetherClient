package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.hand.HandAnimation;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.hand.HandStyle;
import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.client.Minecraft;

/**
 * Эффекты руки ({@code HandsShader}): 16 процедурных шейдер-эффектов,
 * своя анимация появления и исчезновения, размах, удержание, покачивание,
 * цветная подсветка и «отсвет» вокруг руки.
 *
 * <p>Полное выключение возвращает ванильный вид: рука и удерживаемые предметы
 * рисуются игрой как обычно, сохраняя исходные текстуры и анимации.</p>
 */
public final class HandEffectsModule extends Module {
    private final BooleanSetting useShaderEffect = register(new BooleanSetting("useShader",
            "Шейдер-эффект (HandsShader)",
            "Включает один из 16 процедурных шейдер-эффектов для рук и предметов от первого лица",
            true, setting -> apply()));
    private final EnumSetting<ShaderEffect> shaderEffect = register(new EnumSetting<>("shaderEffect",
            "Эффект HandsShader",
            "Выбор одного из 16 уникальных шейдер-эффектов для рук и предметов",
            ShaderEffect.LIQUID_GLASS, ShaderEffect.values(), setting -> apply()));
    private final ColorSetting shaderColor = register(new ColorSetting("shaderColor",
            "Цвет шейдера рук",
            "Основной цвет выбранного шейдер-эффекта рук",
            ShaderEffect.LIQUID_GLASS.defaultColor(), setting -> apply()));
    private final FloatSetting shaderIntensity = register(new FloatSetting("shaderIntensity",
            "Интенсивность шейдера",
            "Яркость и выраженность шейдер-эффекта рук",
            1.00F, 0.10F, 2.00F, 0.05F, setting -> apply()));
    private final FloatSetting shaderSpeed = register(new FloatSetting("shaderSpeed",
            "Скорость анимации шейдера",
            "Скорость течения волн и анимации шейдера рук",
            1.00F, 0.10F, 3.00F, 0.05F, setting -> apply()));
    private final FloatSetting shaderOpacity = register(new FloatSetting("shaderOpacity",
            "Прозрачность шейдера",
            "Непрозрачность 3D-оболочки и ореола шейдера рук",
            0.75F, 0.05F, 1.00F, 0.02F, setting -> apply()));

    private final EnumSetting<HandStyle> style = register(new EnumSetting<>("style", "Стиль",
            "Чем подсвечивается рука", HandStyle.GLOW, HandStyle.values(), setting -> apply()));
    private final EnumSetting<HandAnimation> animation = register(new EnumSetting<>("animation", "Движение",
            "Своя анимация руки в первом лице", HandAnimation.SWING, HandAnimation.values(),
            setting -> apply()));
    private final ColorSetting color = register(new ColorSetting("color", "Цвет",
            "Цвет подсветки и «отсвета»", 0xFF7FD4FF, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Интенсивность",
            "Насколько заметен эффект", 0.6F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final FloatSetting appearSpeed = register(new FloatSetting("appearSpeed", "Скорость появления",
            "Как быстро рука возвращается после смены предмета", 6.0F, 0.5F, 20.0F, 0.5F, setting -> apply()));
    private final FloatSetting swingStrength = register(new FloatSetting("swing", "Сила размаха",
            "Насколько сильно рука уходит при ударе", 0.5F, 0.0F, 1.5F, 0.05F, setting -> apply()));

    public HandEffectsModule() {
        super("hands", "Эффекты руки", "HandsShader: 16 шейдер-эффектов, анимация и подсветка руки в первом лице",
                ModuleCategory.VISUAL);
    }

    private void apply() {
        HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.color.get(),
                this.intensity.get(), this.appearSpeed.get(), this.swingStrength.get());
        HandEffectRenderer.configureShader(this.useShaderEffect.isEnabled(), this.shaderEffect.get(),
                this.shaderColor.get(), this.shaderIntensity.get(),
                this.shaderSpeed.get(), this.shaderOpacity.get());
        settingChanged(this.style);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        HandEffectRenderer.reset();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        if (isEnabled()) {
            HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.color.get(),
                    this.intensity.get(), this.appearSpeed.get(), this.swingStrength.get());
            HandEffectRenderer.configureShader(this.useShaderEffect.isEnabled(), this.shaderEffect.get(),
                    this.shaderColor.get(), this.shaderIntensity.get(),
                    this.shaderSpeed.get(), this.shaderOpacity.get());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        HandEffectRenderer.update(deltaSeconds, Minecraft.getInstance().player);
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        if (this.useShaderEffect.isEnabled()) {
            return this.shaderEffect.get().displayName() + " · " + HandEffectRenderer.debugState();
        }
        return HandEffectRenderer.debugState();
    }

    // ─────────────────────── HandsShader (16 эффектов) ───────────────────────

    public boolean useShaderEffect() {
        return this.useShaderEffect.isEnabled();
    }

    public void setUseShaderEffect(boolean enabled) {
        this.useShaderEffect.set(enabled);
        apply();
    }

    public ShaderEffect shaderEffect() {
        return this.shaderEffect.get();
    }

    public void setShaderEffect(ShaderEffect effect) {
        if (effect != null) {
            this.shaderEffect.set(effect);
            apply();
        }
    }

    /** Выбирает шейдер-эффект для рук и применяет его эталонные параметры. */
    public void selectShaderPreset(ShaderEffect effect) {
        if (effect == null) {
            return;
        }
        this.shaderEffect.set(effect);
        this.shaderColor.set(effect.defaultColor());
        this.shaderIntensity.set(effect.defaultIntensity());
        this.shaderSpeed.set(effect.defaultSpeed());
        this.shaderOpacity.set(effect.defaultOpacity());
        apply();
    }

    /** Сбрасывает параметры текущего шейдер-эффекта рук к его эталонным значениям. */
    public void resetCurrentShaderEffect() {
        selectShaderPreset(this.shaderEffect.get());
    }

    /** Полный сброс всех настроек HandsShader к заводским значениям. */
    public void resetShaderDefaults() {
        this.useShaderEffect.reset();
        selectShaderPreset(ShaderEffect.LIQUID_GLASS);
        this.style.reset();
        this.animation.reset();
        this.color.reset();
        this.intensity.reset();
        this.appearSpeed.reset();
        this.swingStrength.reset();
        apply();
    }

    public int shaderColor() {
        return this.shaderColor.get();
    }

    public void setShaderColor(int colorValue) {
        this.shaderColor.set(colorValue);
        apply();
    }

    public ColorSetting shaderColorSetting() {
        return this.shaderColor;
    }

    public float shaderIntensity() {
        return this.shaderIntensity.get();
    }

    public void setShaderIntensity(float value) {
        this.shaderIntensity.set(value);
        apply();
    }

    public FloatSetting shaderIntensitySetting() {
        return this.shaderIntensity;
    }

    public float shaderSpeed() {
        return this.shaderSpeed.get();
    }

    public void setShaderSpeed(float value) {
        this.shaderSpeed.set(value);
        apply();
    }

    public FloatSetting shaderSpeedSetting() {
        return this.shaderSpeed;
    }

    public float shaderOpacity() {
        return this.shaderOpacity.get();
    }

    public void setShaderOpacity(float value) {
        this.shaderOpacity.set(value);
        apply();
    }

    public FloatSetting shaderOpacitySetting() {
        return this.shaderOpacity;
    }
}
