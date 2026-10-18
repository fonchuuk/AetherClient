package com.aetherglass.client.modules.impl;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.render.hud.HudElementType;
import com.aetherglass.client.render.hud.HudLayout;
import com.aetherglass.client.render.hud.HudPainter;
import com.aetherglass.client.render.hud.PerformanceProbe;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Экранные элементы (HUD): пятнадцать независимых панелей.
 *
 * <p>Каждый элемент включается отдельно и перетаскивается в редакторе. Расположение
 * хранится в долях экрана, поэтому не «уезжает» при смене разрешения. Элемент
 * «Производительность» показывает настоящие замеры: частоту кадров, загрузку
 * процессора, память и задержку до сервера.</p>
 */
public final class HudModule extends Module {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("aetherglass", "hud");

    private final Map<HudElementType, BooleanSetting> toggles = new EnumMap<>(HudElementType.class);
    private boolean installed;

    public HudModule() {
        super("hud", "Экранные элементы", "Пятнадцать панелей: производительность, координаты, эффекты и другое",
                ModuleCategory.HUD);
        for (HudElementType type : HudElementType.values()) {
            BooleanSetting setting = new BooleanSetting(type.settingsId(), type.title(),
                    "Показывать элемент «" + type.title() + "»", type.defaultEnabled(),
                    value -> settingChanged(value));
            this.toggles.put(type, register(setting));
        }
    }

    /**
     * Регистрирует общий элемент экрана.
     *
     * <p>Регистрация выполняется один раз: включение и выключение модуля меняет только
     * то, что рисуется внутри.</p>
     */
    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.addLast(ID, (graphics, tickCounter) -> {
            if (!isEnabled()) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            if (client.options == null || client.options.hideGui || client.player == null) {
                return;
            }
            PerformanceProbe.sample();
            HudPainter.render(graphics, graphics.guiWidth(), graphics.guiHeight(), this::isElementEnabled, false,
                    null);
        });
    }

    /** Элемент экрана включается только вместе с модулем. */
    public boolean isElementEnabled(HudElementType type) {
        BooleanSetting setting = this.toggles.get(type);
        return setting != null && setting.isEnabled();
    }

    /** Читает расположение элементов из настроек. */
    public void loadLayout() {
        HudLayout.get().load(ModConfig.get());
    }

    /** Записывает расположение элементов в настройки. */
    public void saveLayout() {
        HudLayout.get().save(ModConfig.get());
        ModConfig.get().save();
    }

    @Override
    protected void onEnable() {
        loadLayout();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public void tick(float deltaSeconds) {
        HudPainter.update(deltaSeconds);
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        int count = 0;
        for (HudElementType type : HudElementType.values()) {
            if (isElementEnabled(type)) {
                count++;
            }
        }
        return "включено " + count + " из " + HudElementType.values().length;
    }
}
