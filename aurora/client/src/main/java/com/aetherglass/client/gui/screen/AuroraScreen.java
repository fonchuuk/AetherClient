package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraList;
import com.aetherglass.client.gui.widget.AuroraWidget;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
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
 * Основа всех экранов клиента: анимированный фон, «стеклянные» панели и общий порядок.
 *
 * <p>Экран ничего не берёт из готовых картинок: фон, панели и подсветка рисуются кодом.
 * Все движения считаются от времени кадра, поэтому одинаково выглядят и на 30, и на
 * 300 кадрах в секунду. Элементы управляются вручную — так проще гарантировать,
 * что они не наедут друг на друга и не выйдут за пределы окна.</p>
 */
public abstract class AuroraScreen extends Screen {
    /** Отступы и размеры: считаются от размера окна, чтобы интерфейс не «плыл». */
    protected static final int MARGIN = 18;
    protected static final int PANEL_GAP = 12;
    protected static final int TITLE_HEIGHT = 34;

    protected final List<AuroraWidget> widgets = new ArrayList<>();
    protected final AuroraList list;
    protected boolean animated;

    private final Component subtitle;
    private float appear;

    protected AuroraScreen(Component title, Component subtitle) {
        super(title);
        this.subtitle = subtitle;
        this.list = new AuroraList(0, 0, 10, 10);
        this.animated = true;
    }

    // ─────────────────────────── построение ───────────────────────────

    /** Создаёт содержимое экрана; вызывается при каждом перестроении. */
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
            this.widgets.get(index).appearAfter(0.04F * index);
        }
        this.appear = 0.0F;
    }

    /** Перестраивает экран: после изменения настроек или размера окна. */
    protected final void rebuild() {
        this.appear = 0.0F;
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

    // ─────────────────────────── отрисовка ───────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        FrameClock.tick();
        float delta = FrameClock.deltaSeconds();
        this.appear = Math.min(1.0F, this.appear + delta * 3.0F);

        drawBackdrop(graphics);
        drawHeading(graphics);
        drawCustom(graphics, mouseX, mouseY, partialTick);

        for (AuroraWidget widget : this.widgets) {
            widget.tickAnimation(delta, widget.isHovered());
        }
        this.list.update(delta);
        this.list.render(graphics, mouseX, mouseY, partialTick);
        for (AuroraWidget widget : this.widgets) {
            if (widget.visible) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        drawFooter(graphics);
    }

    /** Пусто: наследники могут нарисовать здесь собственное содержимое. */
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** Пусто: наследники могут дописать подсказку внизу экрана. */
    protected void drawFooter(GuiGraphics graphics) {
    }

    /**
     * Анимированный фон: два градиента, медленно движущиеся «пятна» света и виньетка.
     *
     * <p>Пятна рисуются концентрическими слоями — так получается мягкое сияние без
     * единого шейдера и без тяжелых вычислений на каждый пиксель.</p>
     */
    protected void drawBackdrop(GuiGraphics graphics) {
        float time = FrameClock.elapsedSeconds();
        int accent = AuroraTheme.accent();
        graphics.fillGradient(0, 0, this.width, this.height,
                ColorUtil.withAlpha(AuroraTheme.backdrop(), 255),
                ColorUtil.darken(AuroraTheme.backdrop(), 0.35F));
        if (!this.animated) {
            return;
        }
        float drift = (float) Math.sin(time * 0.12D);
        drawOrb(graphics, (int) (this.width * (0.22F + 0.05F * drift)), (int) (this.height * 0.28F),
                Math.round(Math.min(this.width, this.height) * 0.55F), accent, 0.16F);
        drawOrb(graphics, (int) (this.width * (0.82F - 0.04F * drift)), (int) (this.height * 0.78F),
                Math.round(Math.min(this.width, this.height) * 0.48F),
                ColorUtil.hsb(AuroraTheme.accentHue() + 0.28F, 0.7F, 1.0F), 0.13F);
        drawOrb(graphics, (int) (this.width * 0.55F), (int) (this.height * (0.12F + 0.03F * drift)),
                Math.round(Math.min(this.width, this.height) * 0.35F),
                ColorUtil.hsb(AuroraTheme.accentHue() + 0.6F, 0.6F, 1.0F), 0.09F);
        // Виньетка: края затемнены, центр остаётся светлым.
        int edge = Math.max(40, Math.min(this.width, this.height) / 4);
        graphics.fillGradient(0, 0, this.width, edge, 0x55000000, 0x00000000);
        graphics.fillGradient(0, this.height - edge, this.width, this.height, 0x00000000, 0x66000000);
    }

    /** Мягкое световое пятно: слои уменьшающейся прозрачности. */
    protected void drawOrb(GuiGraphics graphics, int centerX, int centerY, int radius, int color, float alpha) {
        int layers = 9;
        for (int layer = layers; layer >= 1; layer--) {
            int size = Math.max(4, radius * layer / layers);
            int layerAlpha = Math.round(255 * alpha * (1.0F - (float) layer / (layers + 1.0F)));
            if (layerAlpha <= 0) {
                continue;
            }
            DrawUtil.fillRounded(graphics, centerX - size, centerY - size, size * 2, size * 2, size,
                    ColorUtil.withAlpha(color, layerAlpha));
        }
    }

    /** Заголовок с подзаголовком: входят с лёгким сдвигом вверх. */
    protected void drawHeading(GuiGraphics graphics) {
        Font font = this.font;
        float progress = com.aetherglass.client.render.util.Easing.easeOutQuint(this.appear);
        int shift = Math.round(10 * (1.0F - progress));
        int alpha = Math.round(255 * progress);
        graphics.drawString(font, this.title, MARGIN, MARGIN + shift,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        if (this.subtitle != null) {
            graphics.drawString(font, this.subtitle, MARGIN, MARGIN + font.lineHeight + 4 + shift,
                    ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
        }
        int lineY = MARGIN + font.lineHeight * 2 + 8;
        DrawUtil.separator(graphics, MARGIN, lineY, Math.round((this.width - MARGIN * 2) * progress),
                ColorUtil.fade(AuroraTheme.panelBorder(), 120));
    }

    /** Стеклянная панель заданного размера: общий фон для групп настроек. */
    protected void drawPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        DrawUtil.fillGlass(graphics, x, y, width, height, Math.round(AuroraTheme.radius()));
    }

    // ─────────────────────────── события ───────────────────────────

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

    /** Клиент: используется наследниками для доступа к общим службам. */
    protected Minecraft client() {
        return Minecraft.getInstance();
    }
}
