package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.sky.SkyEffectBlender;
import com.aetherglass.client.render.sky.SkyPreset;

/**
 * Видео-шейдер неба ({@code SkyShader}, Блок 3.1–3.2):
 * бесшовное проигрывание видео-пресетов на 3D-куполе неба с размытым плейсхолдером,
 * выбором видео-пресета, настройкой скорости проигрывания и интенсивности.
 */
public final class SkyEffectsModule extends Module {
    private final EnumSetting<SkyPreset> preset = register(new EnumSetting<>("preset", "Видео-пресет неба",
            "Выбор видео-сцены и палитры для купола неба", SkyPreset.AURORA,
            SkyPreset.values(), setting -> applyPreset()));
    private final BooleanSetting auroraDome = register(new BooleanSetting("auroraDome",
            "Видео на 3D-куполе",
            "Проигрывает бесшовное видео и шейдерные волны прямо на куполе неба",
            true, setting -> apply()));
    private final FloatSetting speed = register(new FloatSetting("speed", "Скорость видео неба",
            "Скорость проигрывания и вращения видео-купола неба",
            0.55F, 0.05F, 3.00F, 0.05F, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Интенсивность неба",
            "Насколько ярко видео-пресет окрашивает небосвод и горизонт",
            0.82F, 0.10F, 1.20F, 0.05F, setting -> apply()));
    private final FloatSetting domeOpacity = register(new FloatSetting("domeOpacity",
            "Прозрачность видео-купола",
            "Плотность отображения видео-слоя на небесной сфере",
            0.68F, 0.10F, 1.00F, 0.05F, setting -> apply()));
    private final EnumSetting<ShaderEffect> domeShader = register(new EnumSetting<>("domeShader",
            "Шейдер-фильтр купола",
            "Дополнительный шейдерный слой поверх видео-купола неба",
            ShaderEffect.AURORA, ShaderEffect.values(), setting -> apply()));
    private final ColorSetting first = register(new ColorSetting("colorA", "Цвет зенита",
            "Первый цвет перелива неба", SkyPreset.AURORA.first(), setting -> apply()));
    private final ColorSetting second = register(new ColorSetting("colorB", "Цвет сияния",
            "Второй цвет перелива неба", SkyPreset.AURORA.second(), setting -> apply()));
    private final ColorSetting third = register(new ColorSetting("colorC", "Цвет горизонта",
            "Третий цвет перелива для плавной игры оттенков", SkyPreset.AURORA.third(), setting -> apply()));

    public SkyEffectsModule() {
        super("sky", "Эффекты неба",
                "Видео-небо (SkyShader): бесшовный купол с пресетами и скоростью",
                ModuleCategory.VISUAL);
    }

    private void applyPreset() {
        SkyPreset value = this.preset.get();
        if (value != SkyPreset.CUSTOM) {
            this.first.set(value.first());
            this.second.set(value.second());
            this.third.set(value.third());
            this.domeShader.set(value.defaultShader());
        }
        apply();
        settingChanged(this.preset);
    }

    private void apply() {
        SkyEffectBlender.configure(
                this.preset.get(),
                this.first.get(),
                this.second.get(),
                this.third.get(),
                this.speed.get(),
                this.intensity.get(),
                this.auroraDome.isEnabled(),
                this.domeOpacity.get(),
                this.domeShader.get()
        );
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        if (isEnabled()) {
            apply();
        }
    }

    public String activity() {
        return "фаза " + (int) (SkyEffectBlender.phase() * 100) + "% · " + SkyEffectBlender.debugState();
    }
}
