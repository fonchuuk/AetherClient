package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.gui.widget.AuroraToggle;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Настройки одного модуля.
 *
 * <p>Каждое изменение тут же попадает в файл настроек (с небольшой задержкой, чтобы
 * ползунок не дёргал диск) и сразу применяется. Кнопка «По умолчанию» возвращает
 * значения, с которыми модуль поставляется.</p>
 */
public final class ModuleSettingsScreen extends AuroraScreen {
    private final Screen parent;
    private final Module module;

    public ModuleSettingsScreen(Screen parent, Module module) {
        super(Component.literal(module.name()), Component.literal(module.description()));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void build() {
        int x = contentLeft();
        int width = contentWidth();

        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Раздел: " + this.module.category().title()), AuroraLabel.Style.MUTED), 4);
        this.list.add(new AuroraToggle(x + 4, 0, this.module.isEnabled(),
                value -> this.module.setEnabled(value)), 8);
        this.list.add(new AuroraLabel(x + 54, 0, 200, Component.literal("модуль включён"),
                AuroraLabel.Style.BODY), -14);

        if ("blocks".equals(this.module.id()) || "hands".equals(this.module.id())) {
            ShaderEffectsScreen.Target target = "hands".equals(this.module.id())
                    ? ShaderEffectsScreen.Target.HANDS
                    : ShaderEffectsScreen.Target.BLOCK;
            this.list.add(new AuroraButton(x + 4, 0, Math.min(280, width - 8), 20,
                    Component.literal("Открыть меню 16 шейдер-эффектов…"),
                    button -> client().setScreen(new ShaderEffectsScreen(this, target))), 10);
        }

        SettingWidgets.addGroup(this.list, "Настройки", x, width);
        if (this.module.settings().isEmpty()) {
            this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                    Component.literal("У модуля нет собственных настроек."), AuroraLabel.Style.MUTED), 4);
        } else {
            SettingWidgets.addAll(this.list, this.module, x, width, () -> {
            });
        }

        SettingWidgets.addGroup(this.list, "Действия", x, width);
        this.list.add(new AuroraButton(x + 4, 0, 200, 20, Component.literal("По умолчанию"), button -> {
            for (com.aetherglass.client.modules.settings.Setting<?> setting : this.module.settings()) {
                setting.reset();
            }
            rebuild();
        }), 6);
        this.list.add(new AuroraButton(x + 4, 0, 200, 20, Component.literal("Сохранить и закрыть"), button -> {
            ModuleManager.get().saveNow();
            close();
        }), 6);
        this.list.add(new AuroraButton(x + 4, 0, 200, 20, Component.literal("Назад"), button -> close()), 6);
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int height = this.list.totalHeight();
        if (height <= 0) {
            return;
        }
        int x = contentLeft() - 8;
        int y = contentTop() - 8;
        int width = contentWidth() + 16;
        int panelHeight = Math.min(this.list.height(), height + 16);
        DrawUtil.fillGlass(graphics, x, y, width, panelHeight, Math.round(AuroraTheme.radius() + 2));
        if (this.module.isEnabled()) {
            DrawUtil.fillRounded(graphics, x, y, width, 2, 1, ColorUtil.fade(AuroraTheme.accent(), 0.9F));
        }
    }

    private void close() {
        ModuleManager.get().saveNow();
        if (this.parent instanceof AuroraMenuScreen menu) {
            client().setScreen(menu);
            return;
        }
        client().setScreen(this.parent);
    }

    @Override
    public void removed() {
        ModuleManager.get().saveNow();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void onClose() {
        close();
    }
}
