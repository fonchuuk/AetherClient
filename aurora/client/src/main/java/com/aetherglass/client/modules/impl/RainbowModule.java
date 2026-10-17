package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.render.ColorController;

/**
 * Радуга (5.5): один общий перелив для всех эффектов.
 *
 * <p>Модуль не красит ничего сам: он крутит оттенок в общем контроллере цвета,
 * откуда его читают частицы, подсветка блоков, рука и небо. Поэтому включение радуги
 * перекрашивает все эффекты одновременно, а выключение мгновенно возвращает им
 * собственные цвета. Отдельных «своих» радуг у модулей нет — это и есть принцип
 * единого источника цвета.</p>
 */
public final class RainbowModule extends Module {
    private final FloatSetting speed = register(new FloatSetting("speed", "Скорость",
            "Полных оборотов цвета в секунду", 0.12F, 0.005F, 2.0F, 0.005F, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Интенсивность",
            "Насколько перелив перебивает собственный цвет эффекта", 0.85F, 0.0F, 1.0F, 0.05F,
            setting -> apply()));
    private final FloatSetting saturation = register(new FloatSetting("saturation", "Насыщенность",
            "Насколько сочные цвета", 0.85F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final FloatSetting brightness = register(new FloatSetting("brightness", "Яркость",
            "Светлота цвета", 1.0F, 0.2F, 1.0F, 0.05F, setting -> apply()));
    private final BooleanSetting affectSky = register(new BooleanSetting("affectSky", "Влияет на небо",
            "Перелив также меняет цвет неба", true, setting -> settingChanged(setting)));

    public RainbowModule() {
        super("rainbow", "Радуга", "Непрерывный перелив цвета для всех эффектов через общий контроллер",
                ModuleCategory.VISUAL);
    }

    private void apply() {
        ColorController.setSpeed(this.speed.get());
        ColorController.setIntensity(this.intensity.get());
        ColorController.setSaturation(this.saturation.get());
        ColorController.setBrightness(this.brightness.get());
        settingChanged(this.speed);
    }

    @Override
    protected void onEnable() {
        apply();
        ColorController.setCycling(true);
    }

    @Override
    protected void onDisable() {
        // Важно выключить перелив именно здесь: иначе эффекты остались бы «радужными».
        ColorController.setCycling(false);
    }

    public boolean affectsSky() {
        return this.affectSky.isEnabled();
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        return "оттенок " + (int) (ColorController.hue() * 360) + "°";
    }
}
