package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraList;
import com.aetherglass.client.gui.widget.AuroraWidget;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Основа всех экранов клиента: размытие фона GPU ({@link #renderBlurredBackground}),
 * матовые стеклянные панели и плавные переходы.
 */
public abstract class AuroraScreen extends Screen {
    protected static final int MARGIN = 18;
    protected static final int PANEL_GAP = 12;
    protected static final int TITLE_HEIGHT = 34;

    protected final List<AuroraWidget> widgets = new ArrayList<>();
    protected final AuroraList list;
    protected boolean animated;

    private final Component subtitle;
    private float appear;
    private boolean closing;

    protected AuroraScreen(Component title, Component subtitle) {
        super(title);
        this.subtitle = subtitle;
        this.list = new AuroraList(0, 0, 10, 10);
        this.animated = true;
    }

    public void beginSmoothClose() {
        if (!this.closing) {
            this.closing = true;
        }
    }

    public float transitionProgress() {
        return com.aetherglass.client.render.util.Easing.easeOutCubic(Math.max(0.0F, Math.min(1.0F, this.appear)));
    }

    protected abstract void build();

    @Override
    protected final void init() {
        this.widgets.clear();
        this.list.clear();
        int left = contentLeft();
        int top = contentTop();
        this.list.setBounds(left, top, contentWidth(), contentHeight());
        build();
        for (int index = 0; index < this.widgets.size(); index++) {
            this.widgets.get(index).appearAfter(0.03F * index);
        }
        this.appear = 0.0F;
        DrawUtil.triggerTextScramble(0.72F);
    }

    protected final void rebuild() {
        this.appear = 0.0F;
        DrawUtil.triggerTextScramble(0.60F);
        rebuildWidgets();
    }

    protected void addWidget(AuroraWidget widget) {
        this.widgets.add(widget);
    }

    protected int contentLeft() {
        return MARGIN;
    }

    protected int contentTop() {
        return TITLE_HEIGHT + MARGIN;
    }

    protected int contentWidth() {
        return Math.max(120, this.width - MARGIN * 2);
    }

    protected int contentHeight() {
        return Math.max(80, this.height - MARGIN * 2 - TITLE_HEIGHT);
    }

    private boolean backdropBroken;
    private boolean headingBroken;
    private boolean customBroken;
    private boolean listBroken;
    private boolean widgetsBroken;
    private boolean footerBroken;

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft != null && this.minecraft.level != null) {
            try {
                this.renderBlurredBackground(graphics);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FrameClock.tick();
        float delta = FrameClock.deltaSeconds();
        if (this.closing) {
            this.appear = Math.max(0.0F, this.appear - delta * 6.5F);
            if (this.appear <= 0.01F) {
                this.closing = false;
                super.onClose();
                return;
            }
        } else {
            this.appear = Math.min(1.0F, this.appear + delta * 4.5F);
        }

        if (!this.backdropBroken) {
            try {
                drawBackdrop(graphics);
            } catch (Throwable throwable) {
                this.backdropBroken = true;
                AuroraLog.error("этап отрисовки отключён: фон", throwable);
            }
        }
        if (!this.headingBroken) {
            try {
                drawHeading(graphics);
            } catch (Throwable throwable) {
                this.headingBroken = true;
                AuroraLog.error("этап отрисовки отключён: заголовок", throwable);
            }
        }
        if (!this.customBroken) {
            try {
                drawCustom(graphics, mouseX, mouseY, partialTick);
            } catch (Throwable throwable) {
                this.customBroken = true;
                AuroraLog.error("этап отрисовки отключён: содержимое", throwable);
            }
        }

        for (AuroraWidget widget : this.widgets) {
            widget.tickAnimation(delta, widget.isHovered());
        }
        if (!this.listBroken) {
            try {
                this.list.update(delta);
                this.list.render(graphics, mouseX, mouseY, partialTick);
            } catch (Throwable throwable) {
                this.listBroken = true;
                AuroraLog.error("этап отрисовки отключён: список", throwable);
            }
        }
        if (!this.widgetsBroken) {
            try {
                for (AuroraWidget widget : this.widgets) {
                    if (widget.visible) {
                        widget.render(graphics, mouseX, mouseY, partialTick);
                    }
                }
            } catch (Throwable throwable) {
                this.widgetsBroken = true;
                AuroraLog.error("этап отрисовки отключён: элементы", throwable);
            }
        }
        if (!this.footerBroken) {
            try {
                drawFooter(graphics);
            } catch (Throwable throwable) {
                this.footerBroken = true;
                AuroraLog.error("этап отрисовки отключён: подвал", throwable);
            }
        }
    }

    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void drawFooter(GuiGraphics graphics) {
    }

    protected void drawBackdrop(GuiGraphics graphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            graphics.fill(0, 0, this.width, this.height, AuroraTheme.screenDim());
            return;
        }
        graphics.fillGradient(0, 0, this.width, this.height,
                ColorUtil.withAlpha(0xFF06070A, 255),
                ColorUtil.withAlpha(0xFF090B10, 255));
    }

    protected void drawOrb(GuiGraphics graphics, int centerX, int centerY, int radius, int color, float alpha) {
        int topY = Math.max(0, centerY - radius);
        int botY = Math.min(this.height, centerY + radius);
        if (botY <= topY) {
            return;
        }
        int peak = ColorUtil.withAlpha(color, Math.max(0, Math.min(255, Math.round(90 * alpha))));
        int midY = (topY + botY) / 2;
        graphics.fillGradient(0, topY, this.width, midY, 0x00000000, peak);
        graphics.fillGradient(0, midY, this.width, botY, peak, 0x00000000);
    }

    protected void drawHeading(GuiGraphics graphics) {
        Font font = this.font;
        float progress = com.aetherglass.client.render.util.Easing.easeOutQuint(this.appear);
        int shift = Math.round(8 * (1.0F - progress));
        int alpha = Math.round(255 * progress);
        DrawUtil.drawGlowingText(graphics, font, this.title, MARGIN, MARGIN + shift,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), AuroraTheme.accent());
        if (this.subtitle != null) {
            DrawUtil.drawGlowingText(graphics, font, this.subtitle, MARGIN, MARGIN + font.lineHeight + 3 + shift,
                    ColorUtil.fade(AuroraTheme.textSecondary(), alpha), AuroraTheme.accent());
        }
        int lineY = MARGIN + font.lineHeight * 2 + 6;
        DrawUtil.separator(graphics, MARGIN, lineY, Math.round((this.width - MARGIN * 2) * progress),
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(28 * progress)));
    }

    protected void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        DrawUtil.drawLiquidGlassPanel(graphics, x, y, width, height, Math.round(AuroraTheme.radius()), 1.0F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.list.mouseClicked(event, doubleClick)) {
            return true;
        }
        for (AuroraWidget widget : this.widgets) {
            if (widget.visible && widget.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.list.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        for (AuroraWidget widget : this.widgets) {
            if (widget.visible && widget.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = this.list.mouseReleased(event);
        for (AuroraWidget widget : this.widgets) {
            if (widget.visible) {
                handled |= widget.mouseReleased(event);
            }
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.list.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.list.keyPressed(event)) {
            return true;
        }
        for (AuroraWidget widget : this.widgets) {
            if (widget.visible && widget.keyPressed(event)) {
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected Minecraft client() {
        return Minecraft.getInstance();
    }
}
