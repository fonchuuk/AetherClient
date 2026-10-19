package com.aetherglass.client.modules.impl;

import com.aetherglass.client.gui.inventory.InventoryTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;

/**
 * Кастомный инвентарь и анимации переноса предметов (Блок 6.1–6.3):
 * <ul>
 *   <li>6.1: Плавный перелёт предмета по дуге с Easing при {@code Shift-Click} (0.15–0.25 сек)
 *       поверх интерфейса во всех контейнерах (сундук, верстак, печка, бочка, шалкер, эндер-сундук);</li>
 *   <li>6.2: Быстрый перенос зажатием ({@code Quick Move / Drag}, «Лассо» при ведении курсора с зажатым Shift);</li>
 *   <li>6.3: Темы оформления инвентаря: Светлый (дефолтный), Тёмный (более чёрный), Матовый (Liquid Glass).</li>
 * </ul>
 */
public final class InventoryModule extends Module {
    private final EnumSetting<InventoryTheme> theme = register(new EnumSetting<>("theme",
            "Тема инвентаря",
            "Светлый (дефолтный), Тёмный (более чёрный) или Матовый (Liquid Glass)",
            InventoryTheme.LIQUID_GLASS, InventoryTheme.values(), setting -> settingChanged(setting)));
    private final BooleanSetting shiftClickFlight = register(new BooleanSetting("shiftClickFlight",
            "Анимация перелёта (Shift-Click)",
            "Предмет плавно перелетает по дуге в целевой слот вместо мгновенной телепортации",
            true, setting -> settingChanged(setting)));
    private final FloatSetting flightDuration = register(new FloatSetting("flightDuration",
            "Время перелёта (сек)",
            "Длительность анимации полёта предмета между слотами (0.15–0.25 сек)",
            0.20F, 0.12F, 0.35F, 0.01F, setting -> settingChanged(setting)));
    private final BooleanSetting quickMoveDrag = register(new BooleanSetting("quickMoveDrag",
            "Быстрый перенос (Лассо / Зажатие)",
            "Зажмите Shift + ЛКМ и проведите по слотам для автоматического переноса всех предметов",
            true, setting -> settingChanged(setting)));

    public InventoryModule() {
        super("inventory", "Инвентарь и контейнеры",
                "Liquid Glass тема инвентаря, полёт предметов при Shift-Click и быстрый перенос Лассо",
                ModuleCategory.INTERFACE);
        setEnabled(true);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public InventoryTheme theme() {
        return this.theme.get();
    }

    public void setTheme(InventoryTheme value) {
        if (value != null) {
            this.theme.set(value);
        }
    }

    public boolean shiftClickFlight() {
        return isEnabled() && this.shiftClickFlight.isEnabled();
    }

    public float flightDuration() {
        return this.flightDuration.get();
    }

    public boolean quickMoveDrag() {
        return isEnabled() && this.quickMoveDrag.isEnabled();
    }

    public String activity() {
        return this.theme.get().title();
    }
}
