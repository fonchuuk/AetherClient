package com.aetherglass.client.modules.impl;

import com.aetherglass.client.AetherGlassClient;
import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.input.KeybindManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.render.hud.HudElementType;
import com.aetherglass.client.render.hud.HudLayout;
import com.aetherglass.client.render.hud.HudPainter;
import com.aetherglass.client.render.hud.PerformanceProbe;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.utils.AuroraLog;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Экранные элементы (HUD, Блок 4 и Блок 5): сгруппированные стеклянные панели по образцу {@code image-2.png}.
 *
 * <p>Поддерживает свободное перетаскивание ЛКМ и быстрое скрытие/показ любого виджета ПКМ
 * прямо в редакторе HUD с сохранением позиций и видимости в {@code ModConfig}.</p>
 */
public final class HudModule extends Module {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(AetherGlassClient.MOD_ID, "hud");

    private final Map<HudElementType, BooleanSetting> toggles = new EnumMap<>(HudElementType.class);
    private boolean installed;
    private boolean renderBroken;
    private long sessionStartMillis = System.currentTimeMillis();

    public HudModule() {
        super("hud", "Экранные элементы",
                "Сгруппированные панели Liquid Glass: Инфо-бар, Друзья, Зелья, Перезарядки, Координаты, Статус игрока, CPS и Keystrokes",
                ModuleCategory.HUD);
        for (HudElementType type : HudElementType.values()) {
            this.toggles.put(type, register(new BooleanSetting(type.settingsId(), type.title(),
                    "Показывать панель «" + type.title() + "» на экране", type.defaultEnabled(),
                    setting -> settingChanged(setting))));
        }
    }

    /** Подключает слой к реестру экранных слоёв Fabric. */
    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.addLast(LAYER_ID, this::onHudRender);
    }

    private void onHudRender(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui) {
            return;
        }
        int sw = client.getWindow().getGuiScaledWidth();
        int sh = client.getWindow().getGuiScaledHeight();

        float dt = FrameClock.deltaSeconds();
        float cleanAlpha = KeybindManager.get().updateHudVisibility(dt);

        if (isEnabled() && !this.renderBroken && cleanAlpha > 0.01F) {
            try {
                PerformanceProbe.sample();
                InterfaceModule look = ModuleManager.get().typed("interface", InterfaceModule.class);
                boolean glass = look == null || look.hudGlass();
                HudPainter.render(graphics, client, this, glass, false);
            } catch (Throwable throwable) {
                this.renderBroken = true;
                AuroraLog.error("отрисовка элементов экрана", throwable);
            }
        }

        if (cleanAlpha > 0.05F) {
            NotificationManager.get().render(graphics, sw, sh);
        }
    }

    @Override
    protected void onEnable() {
        if (this.sessionStartMillis == 0L) {
            this.sessionStartMillis = System.currentTimeMillis();
        }
        loadLayout();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public boolean isElementEnabled(HudElementType type) {
        return isShown(type);
    }

    public boolean isShown(HudElementType type) {
        BooleanSetting setting = this.toggles.get(type);
        return setting != null && setting.isEnabled();
    }

    public void setShown(HudElementType type, boolean shown) {
        BooleanSetting setting = this.toggles.get(type);
        if (setting != null) {
            setting.set(shown);
        }
    }

    public void toggleShown(HudElementType type) {
        BooleanSetting setting = this.toggles.get(type);
        if (setting != null) {
            setting.toggle();
            NotificationManager.get().push(
                    "HUD Виджет",
                    type.title() + (setting.isEnabled() ? " — показан" : " — скрыт"),
                    setting.isEnabled() ? NotificationManager.Type.ENABLED : NotificationManager.Type.DISABLED,
                    1.8F
            );
        }
    }

    public HudLayout layout() {
        return HudLayout.get();
    }

    public void loadLayout() {
        HudLayout.get().load(ModConfig.get());
    }

    public void saveLayout() {
        HudLayout.get().save(ModConfig.get());
        ModConfig.get().save();
    }

    public long sessionSeconds() {
        return Math.max(0L, (System.currentTimeMillis() - this.sessionStartMillis) / 1000L);
    }

    public String activity() {
        int count = 0;
        for (HudElementType type : HudElementType.values()) {
            if (isShown(type)) {
                count++;
            }
        }
        return "активно панелей: " + count;
    }
}
