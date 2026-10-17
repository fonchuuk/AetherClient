package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.sky.SkyEffectBlender;
import com.aetherglass.client.render.sky.SkyPreset;

/**
 * Эффекты неба (5.4): плавный переход между двумя цветами, пресеты, интенсивность.
 *
 * <p>Эффект меняет цвет настоящего неба в мире, а не закрывает экран полупрозрачной
 * плашкой. Никаких шейдеров и шейдер-паков: цвет передаётся в состояние отрисовки
 * неба, которое игра и так использует каждый кадр.</p>
 */
public final class SkyEffectsModule extends Module {
    private final EnumSetting<SkyPreset> preset = register(new EnumSetting<>("preset", "Пресет",
            "Готовая пара цветов; после выбора цвета можно менять вручную", SkyPreset.AURORA,
            SkyPreset.values(), setting -> applyPreset()));
    private final ColorSetting first = register(new ColorSetting("colorA", "Основной цвет",
            "Первый цвет перехода", SkyPreset.AURORA.first(), setting -> apply()));
    private final ColorSetting second = register(new ColorSetting("colorB", "Дополнительный цвет",
            "Второй цвет перехода", SkyPreset.AURORA.second(), setting -> apply()));
    private final FloatSetting speed = register(new FloatSetting("speed", "Скорость",
            "Полных переходов в секунду", 0.05F, 0.005F, 1.0F, 0.005F, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Интенсивность",
            "Насколько эффект перебивает ванильное небо", 0.6F, 0.0F, 1.0F, 0.05F, setting -> apply()));

    public SkyEffectsModule() {
        super("sky", "Эффекты неба", "Плавный переход цвета неба, пресеты и интенсивность",
                ModuleCategory.VISUAL);
    }

    private void applyPreset() {
        SkyPreset value = this.preset.get();
        if (value != SkyPreset.CUSTOM) {
            this.first.set(value.first());
            this.second.set(value.second());
        }
        apply();
        settingChanged(this.preset);
    }

    private void apply() {
        SkyEffectBlender.configure(this.first.get(), this.second.get(), this.speed.get(), this.intensity.get());
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

    /** Строка состояния для списка модулей. */
    public String activity() {
        return "фаза " + (int) (SkyEffectBlender.phase() * 100) + "% · " + SkyEffectBlender.debugState();
    }
}
