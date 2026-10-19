package com.aetherglass.client.gui.screen;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.modules.impl.InterfaceModule;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.hud.HudElementType;
import com.aetherglass.client.render.hud.HudPainter;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Полноэкранный редактор расположения и видимости панелей HUD (Блок 4: Drag & Drop + ПКМ скрытие).
 *
 * <ul>
 *     <li><b>ЛКМ:</b> Перетаскивание любого виджета в любое место экрана с привязкой к сетке и направляющим.</li>
 *     <li><b>ПКМ:</b> Мгновенно скрыть или показать виджет (в редакторе скрытый виджет обводится серой рамкой,
 *         а в игре пропадает полностью).</li>
 *     <li>Все позиции и состояния сохраняются в {@link ModConfig}.</li>
 * </ul>
 */
public final class HudEditorScreen extends AuroraScreen {
    private static final int GRID = 6;

    private final Screen parent;
    private HudElementType dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Редактор экранных элементов (HUD)"),
                Component.literal("ЛКМ — перетащить панель · ПКМ по панели — скрыть/показать"));
        this.parent = parent;
    }

    @Override
    protected void build() {
        int barWidth = 320;
        int x = (this.width - barWidth) / 2;
        int y = this.height - 34;
        addWidget(new AuroraButton(x, y, 102, 22, Component.literal("Сброс позиций"), button -> {
            HudModule hud = hudModule();
            if (hud != null) {
                hud.layout().resetAll();
                hud.saveLayout();
                NotificationManager.get().push("HUD Редактор", "Позиции панелей сброшены по умолчанию",
                        NotificationManager.Type.INFO, 2.0F);
            }
        }));
        addWidget(new AuroraButton(x + 108, y, 102, 22, Component.literal("Показать все"), button -> {
            HudModule hud = hudModule();
            if (hud != null) {
                for (HudElementType t : HudElementType.values()) {
                    hud.setShown(t, true);
                }
                ModuleManager.get().saveNow();
                hud.saveLayout();
                NotificationManager.get().push("HUD Редактор", "Все панели HUD включены",
                        NotificationManager.Type.ENABLED, 2.0F);
            }
        }));
        addWidget(new AuroraButton(x + 216, y, 104, 22, Component.literal("Готово"),
                button -> onClose(), 0, true));
    }

    @Override
    protected void drawHeading(GuiGraphics graphics) {
        // Не рисуем массивную шапку поверх HUD: оставляем чистый экран для точного позиционирования
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float openProgress = transitionProgress();
        drawGuideLines(graphics, openProgress);

        HudModule hud = hudModule();
        if (hud == null) {
            return;
        }
        InterfaceModule look = ModuleManager.get().typed("interface", InterfaceModule.class);
        boolean glass = look == null || look.hudGlass();
        HudPainter.render(graphics, client(), hud, glass, true);

        for (HudElementType type : HudElementType.values()) {
            HudPainter.Box box = HudPainter.box(type);
            if (box == null) {
                continue;
            }
            boolean isHovered = box.contains(mouseX, mouseY);
            boolean active = type == this.dragging || isHovered;
            boolean shown = hud.isShown(type);
            int outline = active
                    ? ColorController.accent()
                    : (shown ? AuroraTheme.panelBorder() : 0xFF707B8E);
            DrawUtil.strokeRounded(graphics, box.x() - 2, box.y() - 2, box.width() + 4, box.height() + 4,
                    Math.max(4, Math.round(AuroraTheme.radius() * 0.7F)),
                    ColorUtil.fade(outline, active ? 0.95F : 0.55F));

            if (active) {
                String stateSuffix = shown ? " [ПКМ — скрыть]" : " [ПКМ — показать]";
                String tag = type.title() + stateSuffix;
                int tagW = this.font.width(tag) + 10;
                int tagX = Math.max(4, Math.min(this.width - tagW - 4, box.x()));
                int tagY = Math.max(4, box.y() - 15);
                DrawUtil.drawFrostedPill(graphics, tagX, tagY, tagW, 13, 4, openProgress, 0.5F, 0.5F);
                graphics.drawString(this.font, tag, tagX + 5, tagY + 3, AuroraTheme.textPrimary(), false);
            }
        }

        String hint = "ЛКМ — перетащить панель  ·  ПКМ по панели — скрыть/показать  ·  ESC — сохранить и выйти";
        int hintWidth = this.font.width(hint) + 18;
        int hintX = (this.width - hintWidth) / 2;
        int hintY = this.height - 56;
        DrawUtil.drawLiquidGlassCard(graphics, hintX, hintY, hintWidth, 18, 6, openProgress, false);
        graphics.drawString(this.font, hint, hintX + 9, hintY + 5, AuroraTheme.textPrimary(), false);

        NotificationManager.get().render(graphics, this.width, this.height);
    }

    private void drawGuideLines(GuiGraphics graphics, float alpha) {
        int line = ColorUtil.fade(AuroraTheme.panelBorder(), 0.35F * alpha);
        int cx = this.width / 2;
        int cy = this.height / 2;
        graphics.fill(cx, 0, cx + 1, this.height, line);
        graphics.fill(0, cy, this.width, cy + 1, line);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();

        HudModule hud = hudModule();
        if (hud == null) {
            return false;
        }

        HudElementType[] values = HudElementType.values();
        for (int i = values.length - 1; i >= 0; i--) {
            HudElementType type = values[i];
            HudPainter.Box box = HudPainter.box(type);
            if (box != null && box.contains(mouseX, mouseY)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    hud.toggleShown(type);
                    ModuleManager.get().saveNow();
                    hud.saveLayout();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    this.dragging = type;
                    this.dragOffsetX = (int) Math.round(mouseX) - box.x();
                    this.dragOffsetY = (int) Math.round(mouseY) - box.y();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging != null && event.button() == 0) {
            HudModule hud = hudModule();
            HudPainter.Box box = HudPainter.box(this.dragging);
            if (hud != null && box != null) {
                int targetX = snap((int) Math.round(event.x()) - this.dragOffsetX, box.width(), this.width);
                int targetY = snap((int) Math.round(event.y()) - this.dragOffsetY, box.height(), this.height);
                hud.layout().setFromPixels(this.dragging, targetX, targetY,
                        this.width, this.height, box.width(), box.height());
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging != null) {
            this.dragging = null;
            HudModule hud = hudModule();
            if (hud != null) {
                hud.saveLayout();
            }
            ModuleManager.get().saveNow();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        HudModule hud = hudModule();
        if (hud != null) {
            hud.saveLayout();
        }
        ModuleManager.get().saveNow();
        ModConfig.get().save();
        client().setScreen(this.parent);
    }

    private static int snap(int value, int elemSize, int screenSize) {
        int center = (screenSize - elemSize) / 2;
        if (Math.abs(value - center) <= 6) {
            return center;
        }
        return Math.round(value / (float) GRID) * GRID;
    }

    private static HudModule hudModule() {
        return ModuleManager.get().typed("hud", HudModule.class);
    }
}
