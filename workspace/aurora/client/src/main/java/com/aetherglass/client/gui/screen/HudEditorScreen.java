package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.render.hud.HudElementType;
import com.aetherglass.client.render.hud.HudLayout;
import com.aetherglass.client.render.hud.HudPainter;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Редактор экранных элементов.
 *
 * <p>Элементы можно таскать мышью, а коротким щелчком — включать и выключать.
 * Расположение запоминается сразу после отпускания кнопки мыши и хранится в долях
 * экрана, поэтому не зависит ни от разрешения, ни от масштаба интерфейса.</p>
 */
public final class HudEditorScreen extends AuroraScreen {
    private static final int GRID = 8;

    private final Screen parent;
    private final int[] bounds = new int[4];

    private HudElementType dragging;
    private double grabOffsetX;
    private double grabOffsetY;
    private boolean moved;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Экранные элементы"),
                Component.literal("перетащите мышью · щелчок включает и выключает"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        int x = contentLeft();
        int bottom = this.height - MARGIN - 22;
        int width = 168;
        addWidget(new AuroraButton(x, bottom, width, 22, Component.literal("Сбросить расположение"),
                button -> {
                    HudLayout.get().resetAll();
                    save();
                }));
        addWidget(new AuroraButton(x + width + 8, bottom, width, 22, Component.literal("Включить все"),
                button -> setAll(true)));
        addWidget(new AuroraButton(x + (width + 8) * 2, bottom, width, 22, Component.literal("Выключить все"),
                button -> setAll(false)));
        addWidget(new AuroraButton(this.width - MARGIN - 120, bottom, 120, 22, Component.literal("Назад"),
                button -> close()));
        this.list.add(new AuroraLabel(x + 4, 0, 320,
                Component.literal("Серые плашки — выключенные элементы; их тоже можно перетащить."),
                AuroraLabel.Style.MUTED), 4);
    }

    private void setAll(boolean value) {
        HudModule module = hud();
        if (module == null) {
            return;
        }
        for (HudElementType type : HudElementType.values()) {
            if (module.isElementEnabled(type) != value) {
                toggle(type);
            }
        }
    }

    private void toggle(HudElementType type) {
        HudModule module = hud();
        if (module == null) {
            return;
        }
        com.aetherglass.client.modules.settings.Setting<?> setting = module.setting(type.settingsId());
        if (setting instanceof BooleanSetting booleanSetting) {
            booleanSetting.toggle();
        }
    }

    private HudModule hud() {
        return com.aetherglass.client.modules.ModuleManager.get().typed("hud", HudModule.class);
    }

    private void save() {
        HudModule module = hud();
        if (module != null) {
            module.saveLayout();
        }
    }

    // ─────────────────────────── отрисовка ───────────────────────────

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HudModule module = hud();
        HudPainter.render(graphics, this.width, this.height,
                module == null ? type -> false : module::isElementEnabled, true, hovered(mouseX, mouseY));
        if (this.dragging != null) {
            drawGrid(graphics);
        }
    }

    private void drawGrid(GuiGraphics graphics) {
        int color = ColorUtil.withAlpha(AuroraTheme.textMuted(), 30);
        for (int x = 0; x < this.width; x += GRID * 2) {
            graphics.fill(x, 0, x + 1, this.height, color);
        }
        for (int y = 0; y < this.height; y += GRID * 2) {
            graphics.fill(0, y, this.width, y + 1, color);
        }
    }

    private HudElementType hovered(int mouseX, int mouseY) {
        HudElementType found = null;
        for (HudElementType type : HudElementType.values()) {
            HudPainter.lastBounds(type, this.bounds);
            if (this.bounds[2] <= 0 || this.bounds[3] <= 0) {
                continue;
            }
            if (mouseX >= this.bounds[0] && mouseX <= this.bounds[0] + this.bounds[2]
                    && mouseY >= this.bounds[1] && mouseY <= this.bounds[1] + this.bounds[3]) {
                found = type;
            }
        }
        return found;
    }

    // ─────────────────────────── мышь ───────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudElementType target = hovered((int) event.x(), (int) event.y());
        if (target != null) {
            HudPainter.lastBounds(target, this.bounds);
            this.dragging = target;
            this.grabOffsetX = event.x() - this.bounds[0];
            this.grabOffsetY = event.y() - this.bounds[1];
            this.moved = false;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null) {
            this.moved = true;
            float x = (float) ((event.x() - this.grabOffsetX) / Math.max(1, this.width));
            float y = (float) ((event.y() - this.grabOffsetY) / Math.max(1, this.height));
            HudLayout.get().move(this.dragging, x, y);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            HudElementType type = this.dragging;
            this.dragging = null;
            if (!this.moved) {
                toggle(type);
            }
            save();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void close() {
        save();
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

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
