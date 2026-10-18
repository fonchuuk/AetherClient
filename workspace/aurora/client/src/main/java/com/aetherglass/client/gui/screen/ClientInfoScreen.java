package com.aetherglass.client.gui.screen;

import com.aetherglass.client.AetherGlassClient;
import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.hud.PerformanceProbe;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран «О клиенте»: версии, состояние систем и честный список ограничений.
 *
 * <p>Здесь нет «проверок, которые всегда успешны»: показывается то, что действительно
 * измерено — состояние файла настроек, занятость видеобуфера, частота кадров.</p>
 */
public final class ClientInfoScreen extends AuroraScreen {
    private final Screen parent;

    public ClientInfoScreen(Screen parent) {
        super(Component.literal("О клиенте"), Component.literal("версия, состояние и ограничения"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        int x = contentLeft();
        int width = contentWidth();
        SettingWidgets.addGroup(this.list, "Версии", x, width);
        line("Клиент", AetherGlassClient.VERSION);
        line("Minecraft", AetherGlassClient.minecraftVersion());
        line("Загрузчик", "Fabric Loader " + AetherGlassClient.loaderVersion());
        line("Среда", "Java " + System.getProperty("java.version", "?") + " · "
                + System.getProperty("os.name", "?"));

        SettingWidgets.addGroup(this.list, "Состояние", x, width);
        line("Настройки", ModConfig.get().status());
        line("Файл настроек", ModConfig.get().file().toAbsolutePath().toString());
        line("Видеобуфер эффектов", RenderResources.allocated() ? "выделен" : "не выделен");
        line("Кадры в секунду", PerformanceProbe.fps() + " (обновляется в игре)");
        line("Перелив цвета", ColorController.isCycling() ? "включён" : "выключен");
        line("Модулей включено", ModuleManager.get().enabledCount() + " из "
                + ModuleManager.get().modules().size());

        SettingWidgets.addGroup(this.list, "Ограничения, о которых надо знать", x, width);
        line("Шейдеры", "не используются: эффекты нарисованы средствами игры");
        line("Сторонние клиенты", "код писан с нуля, чужие исходники не копировались");
        line("Моды-оптимизаторы", "десять проверенных модов, версии закреплены в лаунчере");

        SettingWidgets.addGroup(this.list, "Действия", x, width);
        this.list.add(new AuroraButton(x + 4, 0, 210, 20, Component.literal("Открыть каталог настроек"),
                button -> openFolder()), 6);
        this.list.add(new AuroraButton(x + 4, 0, 210, 20, Component.literal("Назад"), button -> close()), 6);
    }

    private void line(String name, String value) {
        int x = contentLeft();
        int width = contentWidth();
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal(name + ": " + value), AuroraLabel.Style.BODY), 4);
    }

    private void openFolder() {
        try {
            java.nio.file.Path directory = ModConfig.get().file().getParent();
            if (directory == null || !java.nio.file.Files.isDirectory(directory)) {
                return;
            }
            // Открываем каталог средствами системы: без запуска любых программ из интернета.
            String value = directory.toString();
            if (net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.WINDOWS) {
                new ProcessBuilder("explorer.exe", value).start();
            } else if (net.minecraft.util.Util.getPlatform() == net.minecraft.util.Util.OS.OSX) {
                new ProcessBuilder("open", value).start();
            } else {
                new ProcessBuilder("xdg-open", value).start();
            }
        } catch (Exception exception) {
            com.aetherglass.client.utils.AuroraLog.warn("не удалось открыть каталог настроек: "
                    + exception.getMessage());
        }
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int height = this.list.totalHeight();
        if (height <= 0) {
            return;
        }
        int x = contentLeft() - 8;
        int y = contentTop() - 8;
        int panelHeight = Math.min(this.list.height(), height + 16);
        DrawUtil.fillGlass(graphics, x, y, contentWidth() + 16, panelHeight,
                Math.round(AuroraTheme.radius() + 2));
        DrawUtil.fillRounded(graphics, x, y, contentWidth() + 16, 2, 1,
                ColorUtil.fade(AuroraTheme.accent(), 0.9F));
    }

    private void close() {
        Minecraft client = client();
        if (this.parent instanceof AuroraMenuScreen menu) {
            client.setScreen(menu);
            return;
        }
        client.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        close();
    }
}
