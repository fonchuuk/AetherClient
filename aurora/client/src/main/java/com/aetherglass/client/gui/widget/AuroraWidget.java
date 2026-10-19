package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.Spring;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Общая основа для всех элементов интерфейса клиента.
 *
 * <p>Даёт каждому элементу одинаковый «отклик» на действия: плавное появление, реакцию
 * на наведение, прогиб при нажатии и волну от точки клика. Все анимации считаются от
 * реального времени кадра ({@code deltaSeconds}), поэтому одинаково выглядят и на 30,
 * и на 300 кадрах в секунду.</p>
 */
public abstract class AuroraWidget extends AbstractWidget {
    private static final float PRESS_DEPTH = 0.055F;
    /** Насколько элемент выпрыгивает после нажатия. */
    private static final float POP_HEIGHT = 0.033F;
    private static final float RIPPLE_SECONDS = 0.45F;
    private static final float ENTRANCE_SECONDS = 0.36F;

    // Пружины, а не сглаживание: элемент чуть перелетает цель и возвращается.
    // Жёсткость и вязкость подобраны так, чтобы движение было упругим, но не
    // «резиновым»: перелёт около 12 % при наведении и около 20 % при появлении.
    private final Spring hover = new Spring(0.0F);
    private final Spring press = new Spring(0.0F);
    private final Spring entrance = new Spring(0.0F);

    private float rippleX;
    private float rippleY;
    private float rippleTime = -1.0F;
    private float entranceDelay;

    protected AuroraWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /** Задержка появления: элементы входят на сцену «лесенкой», а не все сразу. */
    public final void appearAfter(float seconds) {
        this.entranceDelay = Math.max(0.0F, seconds);
        this.entrance.snap(0.0F);
    }

    /**
     * Продвигает анимации элемента.
     *
     * <p>Вызывается экраном один раз за кадр до отрисовки: так анимации всех элементов
     * синхронизированы и не зависят от порядка отрисовки.</p>
     */
    public void tickAnimation(float deltaSeconds, boolean hovered) {
        float step = Math.max(0.0F, deltaSeconds) * AuroraTheme.motion();
        if (this.entranceDelay > 0.0F) {
            this.entranceDelay -= step;
        } else {
            this.entrance.setTarget(1.0F);
        }
        this.hover.setTarget(hovered ? 1.0F : 0.0F);
        // Нажатие — это импульс: элемент прогибается и сам возвращается в исходное состояние.
        this.press.setTarget(0.0F);
        this.hover.update(step);
        this.press.update(step);
        this.entrance.update(step);
        tuneSprings();
        if (this.rippleTime >= 0.0F) {
            this.rippleTime += step;
            if (this.rippleTime >= RIPPLE_SECONDS) {
                this.rippleTime = -1.0F;
            }
        }
    }

    /**
     * Запускает волну от нажатия и «прогиб» элемента.
     *
     * @param mouseX экранная координата X точки нажатия
     * @param mouseY экранная координата Y точки нажатия
     */
    protected final void beginFeedback(double mouseX, double mouseY) {
        // Нажатие — это толчок: пружина сама возвращается к нулю и перелетает.
        this.press.snap(0.85F);
        this.press.setTarget(0.0F);
        this.rippleX = (float) (mouseX - getX());
        this.rippleY = (float) (mouseY - getY());
        this.rippleTime = 0.0F;
    }

    /**
     * Настраивает пружины под текущую «пружинистость» оформления.
     *
     * <p>Значение берётся из темы: «Пружинистость» в настройках оформления
     * меняет вязкость, поэтому один и тот же код даёт и спокойное, и упругое
     * движение — без правок в каждом виджете.</p>
     */
    private void tuneSprings() {
        float bounce = AuroraTheme.bounce();
        this.hover.configure(240.0F, 26.0F - 9.0F * bounce);
        this.press.configure(320.0F, 22.0F - 7.0F * bounce);
        this.entrance.configure(190.0F, 24.0F - 9.0F * bounce);
    }

    /** Прогресс наведения: 0 — нет, 1 — полностью; из-за перелёта может быть и 1,1. */
    protected final float hoverAmount() {
        return this.hover.value();
    }

    /** Прогресс нажатия: 0 — отпущено, 1 — нажато. */
    protected final float pressAmount() {
        return this.press.value();
    }

    /** Прогресс появления элемента: может слегка перелететь единицу. */
    protected final float entranceAmount() {
        return this.entrance.value();
    }

    /** Масштаб «прогиба»: 1.0 — обычное состояние, меньше — нажатое, больше — перелёт. */
    protected final float feedbackScale() {
        float value = this.press.value();
        if (value >= 0.0F) {
            return 1.0F - PRESS_DEPTH * Easing.easeOutCubic(value);
        }
        // Перелёт после отпускания: элемент чуть выпрыгивает вперёд.
        return 1.0F + POP_HEIGHT * Easing.easeOutCubic(-value);
    }

    /** Сдвиг появления по вертикали в пикселях: элемент «всплывает» на место. */
    protected final float entranceOffset(float maxPixels) {
        return maxPixels * (1.0F - Easing.easeOutQuint(this.entrance.value()));
    }

    /** Прозрачность появления. */
    protected final float entranceAlpha() {
        return Easing.easeOutCubic(this.entrance.value());
    }

    /** Сила свечения: растёт при наведении и при нажатии. */
    protected final float glowAmount() {
        return Math.min(1.0F, 0.55F * this.hover.value() + 0.45F * this.press.value());
    }

    /**
     * Рисует волну от клика, обрезанную по форме элемента.
     *
     * <p>Волна — это круг, который расходится от точки нажатия и гаснет; обрезка по
     * скруглённому прямоугольнику делается построчно, без отдельного буфера.</p>
     */
    protected final void renderRipple(GuiGraphics graphics, int x, int y, int width, int height, int radius,
                                      int color) {
        if (this.rippleTime < 0.0F) {
            return;
        }
        float progress = this.rippleTime / RIPPLE_SECONDS;
        float eased = Easing.easeOutCubic(progress);
        float centerX = Math.max(0.0F, Math.min(width, this.rippleX));
        float centerY = Math.max(0.0F, Math.min(height, this.rippleY));
        double farX = Math.max(centerX, width - centerX);
        double farY = Math.max(centerY, height - centerY);
        float maxRadius = (float) Math.sqrt(farX * farX + farY * farY);
        float current = Math.max(1.0F, maxRadius * eased);
        int alpha = Math.round(90 * (1.0F - progress) * (1.0F - progress));
        if (alpha <= 0) {
            return;
        }
        int rippleColor = ColorUtil.withAlpha(color, alpha);
        int limited = Math.min(radius, Math.min(width, height) / 2);
        for (int row = 0; row < height; row++) {
            double dy = row + 0.5D - centerY;
            if (Math.abs(dy) > current) {
                continue;
            }
            double half = Math.sqrt(Math.max(0.0D, current * current - dy * dy));
            int from = (int) Math.ceil(centerX - half);
            int to = (int) Math.floor(centerX + half);
            if (to < 0 || from >= width) {
                continue;
            }
            int inset = com.aetherglass.client.gui.render.DrawUtil.rowInset(row, height, limited);
            int left = Math.max(from, inset);
            int right = Math.min(to, width - inset);
            if (right > left) {
                graphics.fill(x + left, y + row, x + right, y + row + 1, rippleColor);
            }
        }
    }

    /** Прозрачность текста с учётом появления элемента. */
    protected final int textColor(int base) {
        return ColorUtil.fade(base, entranceAlpha());
    }

    /** Центрирует строку по ширине элемента с чётким контрастом и лёгким подводным свечением. */
    protected final void drawCentered(GuiGraphics graphics, Font font, Component text, int x, int y, int width,
                                      int color) {
        int textWidth = font.width(text);
        com.aetherglass.client.gui.render.DrawUtil.drawGlowingText(
                graphics, font, text, x + (width - textWidth) / 2, y,
                color, AuroraTheme.accent()
        );
    }

    @Override
    protected final void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
