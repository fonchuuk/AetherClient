package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.gui.widget.AuroraList;
import com.aetherglass.client.gui.widget.AuroraSlider;
import com.aetherglass.client.gui.widget.AuroraToggle;
import com.aetherglass.client.gui.widget.AuroraWidget;
import com.aetherglass.client.gui.widget.ColorPicker;
import com.aetherglass.client.gui.widget.EnumButton;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.modules.settings.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Строит элементы управления по настройкам модуля.
 *
 * <p>Для каждого типа настройки есть свой элемент: переключатель, ползунок, выбор из
 * списка или выбор цвета. Добавить новую настройку в модуль — значит просто
 * зарегистрировать её: экран подхватит её сам, без правок интерфейса.</p>
 */
public final class SettingWidgets {
    private static final int CONTROL_WIDTH = 92;

    private SettingWidgets() {
    }

    /** Добавляет в список все настройки модуля с подписями. */
    public static void addAll(AuroraList list, Module module, int x, int width, Runnable onChanged) {
        addAll(list, module.settings(), x, width, onChanged);
    }

    /**
     * Добавляет в список произвольный набор настроек с подписями.
     *
     * <p>Нужен экранам, у которых настройки живут не в модуле, а в своём
     * объекте: например, у материалов.</p>
     */
    public static void addAll(AuroraList list, List<Setting<?>> settings, int x, int width, Runnable onChanged) {
        Minecraft client = Minecraft.getInstance();
        int labelWidth = Math.max(60, width - CONTROL_WIDTH - 24);
        for (Setting<?> setting : settings) {
            int labelHeight = Math.max(12, client.font.wordWrapHeight(setting.label(), labelWidth));
            AuroraLabel label = new AuroraLabel(x + 4, 0, labelWidth, setting.label(), AuroraLabel.Style.BODY);
            label.setHeight(labelHeight);
            list.add(label, 12);
            list.add(control(setting, x + width - CONTROL_WIDTH, 0, onChanged), 6);
        }
    }

    private static AuroraWidget control(Setting<?> setting, int x, int y, Runnable onChanged) {
        return switch (setting.kind()) {
            case BOOLEAN -> {
                BooleanSetting booleanSetting = (BooleanSetting) setting;
                yield new AuroraToggle(x, y, booleanSetting.isEnabled(), value -> {
                    booleanSetting.set(value);
                    onChanged.run();
                });
            }
            case INT -> {
                IntSetting intSetting = (IntSetting) setting;
                yield new AuroraSlider(x, y, CONTROL_WIDTH, AuroraSlider.forInt(intSetting),
                        intSetting.min(), intSetting.max(), 1.0F, value -> onChanged.run());
            }
            case FLOAT -> {
                FloatSetting floatSetting = (FloatSetting) setting;
                yield new AuroraSlider(x, y, CONTROL_WIDTH, AuroraSlider.forFloat(floatSetting),
                        floatSetting.min(), floatSetting.max(), floatSetting.step(), value -> onChanged.run());
            }
            case ENUM -> enumControl((EnumSetting<?>) setting, x, y, onChanged);
            case COLOR -> {
                ColorSetting colorSetting = (ColorSetting) setting;
                yield new ColorPicker(x, y, CONTROL_WIDTH, colorSetting, value -> onChanged.run());
            }
        };
    }

    private static <E extends Enum<E>> AuroraWidget enumControl(EnumSetting<E> setting, int x, int y,
                                                                Runnable onChanged) {
        return new EnumButton<>(x, y, CONTROL_WIDTH, setting, value -> onChanged.run());
    }

    /** Заголовок группы настроек. */
    public static void addGroup(AuroraList list, String title, int x, int width) {
        list.add(new AuroraLabel(x + 4, 0, width, Component.literal(title), AuroraLabel.Style.TITLE), 16);
    }
}
