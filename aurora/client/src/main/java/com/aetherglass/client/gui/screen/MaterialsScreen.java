package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.material.Material;
import com.aetherglass.client.render.material.MaterialManager;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Экран материалов: чем и как закрашивается блок под прицелом.
 *
 * <p>Слева — список материалов, справа — настройки выбранного. Любая правка
 * тут же уходит в файл настроек и, если материал сейчас выбран, сразу видна
 * в игре. Стандартные материалы менять можно: правка сохраняется отдельно и
 * не портит образец для новых копий.</p>
 */
public final class MaterialsScreen extends AuroraScreen {

    private static final int ROW_WIDTH = 236;

    private final Screen parent;
    private final MaterialManager manager = MaterialManager.get();
    private Material selected;

    public MaterialsScreen(Screen parent) {
        super(Component.literal("Материалы"),
                Component.literal("Режим заливки, цвет, плотность и свечение блока под прицелом"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        int x = contentLeft();
        int width = contentWidth();
        BlockEffectsModule blocks = ModuleManager.get().typed("blocks", BlockEffectsModule.class);

        if (this.selected == null || this.manager.byId(this.selected.id()) == null) {
            List<Material> all = this.manager.materials();
            this.selected = all.isEmpty() ? null : all.get(0);
        }
        if (this.selected == null) {
            this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                    Component.literal("Материалы не загрузились."), AuroraLabel.Style.BODY), 8);
            this.list.add(new AuroraButton(x + 4, 0, 200, 20, Component.literal("Сбросить к стандартным"),
                    button -> {
                        this.manager.reset();
                        rebuild();
                    }), 8);
            return;
        }

        String activeId = blocks == null ? "" : blocks.activeMaterialId();

        SettingWidgets.addGroup(this.list, "Материал", x, width);
        for (Material material : this.manager.materials()) {
            boolean active = material.id().equals(activeId);
            String prefix = active ? "• " : "";
            String suffix = material.builtIn() ? "" : "  (свой)";
            this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                    Component.literal(prefix + material.name() + suffix), button -> {
                        this.selected = material;
                        rebuild();
                    }), 6);
        }
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Отмечен материал, который сейчас применён к блокам."),
                AuroraLabel.Style.MUTED), 2);

        SettingWidgets.addGroup(this.list, "Настройки: " + this.selected.name(), x, width);
        List<Setting<?>> settings = new ArrayList<>(this.selected.settings());
        SettingWidgets.addAll(this.list, settings, x, width, () -> changed(blocks));

        SettingWidgets.addGroup(this.list, "Действия", x, width);
        Material current = this.selected;
        this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                Component.literal("Применить к блокам"), button -> {
                    if (blocks != null) {
                        blocks.applyMaterial(current);
                        rebuild();
                    }
                }), 6);
        this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                Component.literal("Сохранить как новый материал"), button -> {
                    Material copy = this.manager.create(current, "Мой материал " + (this.manager.userCount() + 1));
                    this.selected = copy;
                    rebuild();
                }), 6);
        if (!current.builtIn()) {
            this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                    Component.literal("Удалить материал"), button -> {
                        this.manager.remove(current.id());
                        this.selected = null;
                        rebuild();
                    }), 6);
        }
        this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                Component.literal("Вернуть стандартные материалы"), button -> {
                    this.manager.reset();
                    this.selected = null;
                    rebuild();
                }), 6);
        this.list.add(new AuroraButton(x + 4, 0, ROW_WIDTH, 20,
                Component.literal("Назад"), button -> close()), 6);
    }

    /** Правка настройки: сохраняем материал и, если он активен, обновляем модуль. */
    private void changed(BlockEffectsModule blocks) {
        this.manager.save();
        if (blocks != null && this.selected != null && this.selected.id().equals(blocks.activeMaterialId())) {
            blocks.applyMaterial(this.selected);
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
        int width = contentWidth() + 16;
        int panelHeight = Math.min(this.list.height(), height + 16);
        DrawUtil.fillGlass(graphics, x, y, width, panelHeight, Math.round(AuroraTheme.radius() + 2));
        DrawUtil.fillRounded(graphics, x, y, width, 2, 1, ColorUtil.fade(AuroraTheme.accent(), 0.9F));
    }

    private void close() {
        this.manager.save();
        client().setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.manager.save();
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
