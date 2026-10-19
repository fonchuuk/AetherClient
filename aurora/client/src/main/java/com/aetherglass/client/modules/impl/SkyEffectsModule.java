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
 * Шейдер неба ({@code SkyShader}, Блок 4.4): живое многоцветное переливание купола неба,
 * анимированные ленты северного сияния / туманности и возможность наложить любой из
 * 16 процедурных шейдер-эффектов прямо на 3D-купол неба.
 */
public final class SkyEffectsModule extends Module {
    private final EnumSetting<SkyPreset> preset = register(new EnumSetting<>("preset", "Пресет неба",
            "Готовая палитра и шейдер купола неба", SkyPreset.AURORA,
            SkyPreset.values(), setting -> applyPreset()));
    private final BooleanSetting auroraDome = register(new BooleanSetting("auroraDome",
            "Анимированный 3D-купол",
            "Рисует живые переливающиеся волны и шейдерную анимацию прямо на куполе неба",
            true, setting -> apply()));
    private final EnumSetting<ShaderEffect> domeShader = register(new EnumSetting<>("domeShader",
            "Шейдер на куполе неба",
            "Выбор анимации на куполе неба (Северное сияние, Туманность, Призма, Жидкое стекло и др.)",
            ShaderEffect.AURORA, ShaderEffect.values(), setting -> apply()));
    private final FloatSetting domeOpacity = register(new FloatSetting("domeOpacity",
            "Яркость анимации купола",
            "Насыщенность переливающихся волн на куполе неба",
            0.58F, 0.10F, 1.00F, 0.05F, setting -> apply()));
    private final FloatSetting speed = register(new FloatSetting("speed", "Скорость перелива",
            "Скорость течения цветовых волн и сияния на небе",
            0.45F, 0.05F, 2.50F, 0.05F, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Глубина оттенка неба",
            "Насколько сильно шейдер окрашивает небосвод и горизонт",
            0.78F, 0.10F, 1.00F, 0.05F, setting -> apply()));
    private final ColorSetting first = register(new ColorSetting("colorA", "Цвет зенита",
            "Первый цвет перелива неба", SkyPreset.AURORA.first(), setting -> apply()));
    private final ColorSetting second = register(new ColorSetting("colorB", "Цвет сияния",
            "Второй цвет перелива неба", SkyPreset.AURORA.second(), setting -> apply()));
    private final ColorSetting third = register(new ColorSetting("colorC", "Цвет горизонта",
            "Третий цвет перелива для плавной игры оттенков", SkyPreset.AURORA.third(), setting -> apply()));

    public SkyEffectsModule() {
        super("sky", "Эффекты неба",
                "SkyShader: плавный многоцветный перелив и живая 3D-анимация на куполе неба",
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
