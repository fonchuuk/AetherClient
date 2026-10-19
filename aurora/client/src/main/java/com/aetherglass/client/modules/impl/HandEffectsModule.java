package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.hand.HandAnimation;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.hand.HandStyle;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.client.Minecraft;

/**
 * Эффекты руки ({@code HandsShader}, Блок 4.2): 16 процедурных шейдер-эффектов,
 * полупрозрачная стеклянная глазурь и контур, накладываемые ПОВЕРХ 100% видимого
 * предмета в руке со своей оригинальной текстурой.
 */
public final class HandEffectsModule extends Module {
    private final BooleanSetting useShaderEffect = register(new BooleanSetting("useShader",
            "Шейдер поверх предмета (HandsShader)",
            "Накладывает выбранный шейдер-эффект поверх 100% видимой текстуры предмета и руки",
            true, setting -> apply()));
    private final EnumSetting<ShaderEffect> shaderEffect = register(new EnumSetting<>("shaderEffect",
            "Эффект HandsShader",
            "Выбор одного из 16 уникальных шейдер-эффектов для рук и предметов",
            ShaderEffect.LIQUID_GLASS, ShaderEffect.values(), setting -> apply()));
    private final FloatSetting shaderOpacity = register(new FloatSetting("shaderOpacity",
            "Плотность шейдера поверх",
            "Непрозрачность шейдерной глазури поверх оригинальной текстуры предмета",
            0.42F, 0.05F, 0.85F, 0.02F, setting -> apply()));
    private final FloatSetting outlineWidth = register(new FloatSetting("outlineWidth",
            "Толщина обводки предмета",
            "Толщина светящегося силуэтного контура вокруг предмета и руки",
            0.012F, 0.002F, 0.045F, 0.002F, setting -> apply()));
    private final FloatSetting shaderIntensity = register(new FloatSetting("shaderIntensity",
            "Сила свечения и блика",
            "Яркость и выраженность шейдер-эффекта поверх предмета",
            1.00F, 0.10F, 2.00F, 0.05F, setting -> apply()));
    private final FloatSetting shaderSpeed = register(new FloatSetting("shaderSpeed",
            "Скорость перелива",
            "Скорость течения волн и анимации шейдера рук",
            1.00F, 0.10F, 3.00F, 0.05F, setting -> apply()));
    private final EnumSetting<FaceGradientMode> faceGradientMode = register(new EnumSetting<>("faceGradientMode",
            "Режим блика (Капля / Блик)",
            "Как накладывается стеклянный блик на предмет и руку",
            FaceGradientMode.MENISCUS_DROP, FaceGradientMode.values(), setting -> apply()));
    private final ColorSetting shaderColor = register(new ColorSetting("shaderColor",
            "Цвет шейдера и контура",
            "Основной цвет выбранного шейдер-эффекта рук и предмета",
            ShaderEffect.LIQUID_GLASS.defaultColor(), setting -> apply()));
    private final EnumSetting<HandAnimation> animation = register(new EnumSetting<>("animation",
            "Анимация взмаха руки",
            "Плавная анимация руки в первом лице",
            HandAnimation.SWING, HandAnimation.values(), setting -> apply()));
    private final FloatSetting appearSpeed = register(new FloatSetting("appearSpeed",
            "Скорость смены предмета",
            "Как быстро рука поднимается после переключения слота",
            6.5F, 1.0F, 20.0F, 0.5F, setting -> apply()));
    private final FloatSetting swingStrength = register(new FloatSetting("swing",
            "Амплитуда взмаха",
            "Насколько плавно рука уходит при ударе",
            0.50F, 0.0F, 1.5F, 0.05F, setting -> apply()));

    // Совместимые настройки для внешних вызовов
    private final FloatSetting modelAlpha = new FloatSetting("modelAlpha",
            "Видимость предмета", "Оригинальный предмет всегда 100% видим",
            1.00F, 0.15F, 1.00F, 0.05F, setting -> apply());
    private final EnumSetting<HandStyle> style = new EnumSetting<>("style", "Стиль",
            "Стиль подсветки", HandStyle.GLOW, HandStyle.values(), setting -> apply());

    public HandEffectsModule() {
        super("hands", "Эффекты руки",
                "HandsShader: шейдер и контур поверх 100% видимой текстуры предмета в руке",
                ModuleCategory.VISUAL);
    }

    private void apply() {
        HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.shaderColor.get(),
                Math.min(1.0F, this.shaderOpacity.get()), this.appearSpeed.get(), this.swingStrength.get());
        HandEffectRenderer.configureShader(this.useShaderEffect.isEnabled(), this.shaderEffect.get(),
                this.faceGradientMode.get(), this.shaderColor.get(), this.shaderIntensity.get(),
                this.shaderSpeed.get(), this.shaderOpacity.get(), this.outlineWidth.get(), 1.00F);
        settingChanged(this.shaderEffect);
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
            HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.shaderColor.get(),
                    Math.min(1.0F, this.shaderOpacity.get()), this.appearSpeed.get(), this.swingStrength.get());
            HandEffectRenderer.configureShader(this.useShaderEffect.isEnabled(), this.shaderEffect.get(),
                    this.faceGradientMode.get(), this.shaderColor.get(), this.shaderIntensity.get(),
                    this.shaderSpeed.get(), this.shaderOpacity.get(), this.outlineWidth.get(), 1.00F);
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        HandEffectRenderer.update(deltaSeconds, Minecraft.getInstance().player);
    }

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

    public FaceGradientMode faceGradientMode() {
        return this.faceGradientMode.get();
    }

    public EnumSetting<FaceGradientMode> faceGradientModeSetting() {
        return this.faceGradientMode;
    }

    public float outlineWidth() {
        return this.outlineWidth.get();
    }

    public FloatSetting outlineWidthSetting() {
        return this.outlineWidth;
    }

    public float modelAlpha() {
        return this.modelAlpha.get();
    }

    public FloatSetting modelAlphaSetting() {
        return this.modelAlpha;
    }

    public void selectShaderPreset(ShaderEffect effect) {
        if (effect == null) {
            return;
        }
        this.shaderEffect.set(effect);
        this.shaderColor.set(effect.defaultColor());
        this.shaderIntensity.set(effect.defaultIntensity());
        this.shaderSpeed.set(effect.defaultSpeed());
        this.shaderOpacity.set(Math.min(0.55F, effect.defaultOpacity()));
        apply();
    }

    public void resetCurrentShaderEffect() {
        selectShaderPreset(this.shaderEffect.get());
    }

    public void resetShaderDefaults() {
        this.useShaderEffect.reset();
        selectShaderPreset(ShaderEffect.LIQUID_GLASS);
        this.faceGradientMode.reset();
        this.outlineWidth.reset();
        this.animation.reset();
        this.appearSpeed.reset();
        this.swingStrength.reset();
        apply();
    }

    public int shaderColor() {
        return ColorController.resolve(this.shaderColor.get());
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
