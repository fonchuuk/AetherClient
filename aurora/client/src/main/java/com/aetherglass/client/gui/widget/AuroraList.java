package com.aetherglass.client.gui.widget;

import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.Easing;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Прокручиваемый список элементов.
 *
 * <p>Список сам расставляет элементы по вертикали, двигает их при прокрутке и не даёт
 * им рисоваться за пределами видимой области. Прокрутка плавная: она «догоняет» цель,
 * а не прыгает рывком.</p>
 */
public final class AuroraList {
    private final List<AuroraWidget> items = new ArrayList<>();
    private final List<Integer> offsets = new ArrayList<>();
    private final AnimationTimer scroll = new AnimationTimer(0.0F);

    private int x;
    private int y;
    private int width;
    private int height;
    private int totalHeight;
    private float maxScroll;

    public AuroraList(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scroll.speed(14.0F);
    }

    public void setBounds(int newX, int newY, int newWidth, int newHeight) {
        this.x = newX;
        this.y = newY;
        this.width = newWidth;
        this.height = newHeight;
        clampScroll();
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public int totalHeight() {
        return this.totalHeight;
    }

    public void clear() {
        this.items.clear();
        this.offsets.clear();
        this.totalHeight = 0;
        this.maxScroll = 0.0F;
        this.scroll.snap(0.0F);
    }

    /** Добавляет элемент; {@code gap} — отступ сверху. */
    public void add(AuroraWidget widget, int gap) {
        this.totalHeight += gap;
        this.offsets.add(this.totalHeight);
        this.items.add(widget);
        this.totalHeight += widget.getHeight();
        this.maxScroll = Math.max(0.0F, this.totalHeight - this.height);
    }

    public List<AuroraWidget> items() {
        return this.items;
    }

    private void clampScroll() {
        this.maxScroll = Math.max(0.0F, this.totalHeight - this.height);
        if (this.scroll.target() > this.maxScroll) {
            this.scroll.setTarget(this.maxScroll);
        }
        if (this.scroll.target() < 0.0F) {
            this.scroll.setTarget(0.0F);
        }
    }

    public void scrollBy(float amount) {
        clampScroll();
        this.scroll.setTarget(Math.max(0.0F, Math.min(this.maxScroll, this.scroll.target() + amount)));
    }

    public void scrollToStart() {
        this.scroll.setTarget(0.0F);
    }

    /** Обновляет анимацию прокрутки и всех элементов. */
    public void update(float deltaSeconds) {
        clampScroll();
        this.scroll.update(deltaSeconds);
        int offset = Math.round(this.scroll.current());
        for (int index = 0; index < this.items.size(); index++) {
            AuroraWidget widget = this.items.get(index);
            widget.tickAnimation(deltaSeconds, widget.isHovered() && isVisible(widget));
            widget.setY(this.y + this.offsets.get(index) - offset);
        }
        if (this.maxScroll <= 0.0F) {
            this.scroll.snap(0.0F);
        }
    }

    private boolean isVisible(AuroraWidget widget) {
        return widget.getY() + widget.getHeight() > this.y && widget.getY() < this.y + this.height;
    }

    /** Рисует видимую часть списка. */
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.enableScissor(this.x, this.y, this.x + this.width, this.y + this.height);
        for (AuroraWidget widget : this.items) {
            if (!isVisible(widget)) {
                continue;
            }
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
        if (this.maxScroll > 1.0F) {
            drawScrollbar(graphics);
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int trackX = this.x + this.width - 3;
        int trackHeight = this.height;
        int thumb = Math.max(16, Math.round(trackHeight * (this.height / (float) this.totalHeight)));
        float progress = this.scroll.current() / this.maxScroll;
        int thumbY = Math.round(this.y + (trackHeight - thumb) * Math.min(1.0F, Math.max(0.0F, progress)));
        graphics.fill(trackX, this.y, trackX + 2, this.y + trackHeight, 0x30FFFFFF);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumb, 0x90FFFFFF);
    }

    // ─────────────────────────── события ───────────────────────────

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (AuroraWidget widget : this.items) {
            if (!isVisible(widget) || !widget.visible) {
                continue;
            }
            if (widget.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        for (AuroraWidget widget : this.items) {
            if (!widget.visible) {
                continue;
            }
            if (widget.mouseDragged(event, dragX, dragY)) {
                return true;
            }
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = false;
        for (AuroraWidget widget : this.items) {
            if (!widget.visible) {
                continue;
            }
            handled |= widget.mouseReleased(event);
        }
        return handled;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < this.x || mouseX > this.x + this.width || mouseY < this.y || mouseY > this.y + this.height) {
            return false;
        }
        if (this.maxScroll <= 0.0F) {
            return false;
        }
        scrollBy((float) (-scrollY * 28.0D));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        for (AuroraWidget widget : this.items) {
            if (widget.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    /** Плавность прокрутки: 0..1, используется для подсветки полосы. */
    public float scrollProgress() {
        return Easing.easeOutCubic(this.scroll.current() / Math.max(1.0F, this.maxScroll));
    }
}
