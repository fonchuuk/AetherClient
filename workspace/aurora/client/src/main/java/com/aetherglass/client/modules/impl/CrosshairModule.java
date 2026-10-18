package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.crosshair.CrosshairRenderer;
import com.aetherglass.client.render.crosshair.CrosshairStyle;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;

/**
 * Свой прицел.
 *
 * <p>Модуль подменяет ванильный элемент прицела через реестр Fabric API, но не удаляет
 * его: при выключении модуля ванильный прицел рисуется как обычно. Никаких правок
 * игрового кода «в лоб» — только официальная точка расширения.</p>
 */
public final class CrosshairModule extends Module {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("aetherglass", "crosshair");

    private final EnumSetting<CrosshairStyle> style = register(new EnumSetting<>("style", "Форма",
            "Как выглядит прицел", CrosshairStyle.CROSS, CrosshairStyle.values(),
            setting -> settingChanged(setting)));
    private final ColorSetting color = register(new ColorSetting("color", "Цвет", "Цвет прицела",
            0xFFFFFFFF, setting -> settingChanged(setting)));
    private final FloatSetting size = register(new FloatSetting("size", "Длина луча",
            "Длина лучей прицела в пикселях", 6.0F, 2.0F, 20.0F, 0.5F, setting -> settingChanged(setting)));
    private final FloatSetting thickness = register(new FloatSetting("thickness", "Толщина",
            "Толщина линий", 1.5F, 1.0F, 6.0F, 0.5F, setting -> settingChanged(setting)));
    private final FloatSetting gap = register(new FloatSetting("gap", "Зазор",
            "Расстояние от центра до луча", 3.0F, 0.0F, 16.0F, 0.5F, setting -> settingChanged(setting)));
    private final BooleanSetting outline = register(new BooleanSetting("outline", "Тёмная окантовка",
            "Тёмная подложка, чтобы прицел был виден на светлом фоне", true,
            setting -> settingChanged(setting)));
    private final BooleanSetting dynamic = register(new BooleanSetting("dynamic", "Развод при ударе",
            "Прицел расходится во время удара", true, setting -> settingChanged(setting)));

    private boolean installed;

    public CrosshairModule() {
        super("crosshair", "Свой прицел", "Своя форма, цвет и размер прицела вместо ванильного",
                ModuleCategory.INTERFACE);
    }

    /**
     * Подключает прицел к реестру элементов экрана.
     *
     * <p>Вызывается один раз при запуске клиента: повторная регистрация недопустима,
     * поэтому включение и выключение модуля переключает только режим рисования.</p>
     */
    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, original ->
                (GuiGraphics context, net.minecraft.client.DeltaTracker tickCounter) -> {
                    if (isEnabled() && shouldDraw()) {
                        draw(context);
                    } else {
                        original.render(context, tickCounter);
                    }
                });
    }

    private boolean shouldDraw() {
        Minecraft client = Minecraft.getInstance();
        if (client.options == null || client.options.hideGui || client.player == null) {
            return false;
        }
        return client.options.getCameraType().isFirstPerson();
    }

    private void draw(GuiGraphics context) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        float spread = 0.0F;
        if (this.dynamic.isEnabled() && player != null) {
            spread = 1.0F - player.getAttackStrengthScale(1.0F);
            if (spread < 0.0F) {
                spread = 0.0F;
            }
        }
        CrosshairRenderer.draw(context, context.guiWidth() / 2, context.guiHeight() / 2, this.style.get(),
                ColorController.resolve(this.color.get()), this.size.get(), this.thickness.get(), this.gap.get(),
                this.outline.isEnabled(), spread, (float) client.getWindow().getGuiScale());
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        return this.style.get().title().toLowerCase();
    }
}
