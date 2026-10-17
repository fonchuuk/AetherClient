package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Кнопка из «жидкого стекла» с откликом на нажатие.
 *
 * <p>При наведении подсвечивается акцентной полосой и мягким свечением, при нажатии
 * прогибается и расходится волной от точки клика. Действие выполняется обязательно:
 * кнопок «для вида» в интерфейсе нет.</p>
 */
public final class AuroraButton extends AuroraWidget {
    private final Consumer<AuroraButton> action;
    private final int accent;
    private final boolean emphasized;

    public AuroraButton(int x, int y, int width, int height, Component message, Consumer<AuroraButton> action) {
        this(x, y, width, height, message, action, 0, false);
    }

    public AuroraButton(int x, int y, int width, int height, Component message, Consumer<AuroraButton> action,
                        int accent, boolean emphasized) {
        super(x, y, width, height, message);
        this.action = action;
        this.accent = accent;
        this.emphasized = emphasized;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float alpha = entranceAlpha();
        if (alpha <= 0.002F) {
            return;
        }
        int x = getX();
        int y = getY();
        int width = getWidth();
        int height = getHeight();
        int radius = Math.round(AuroraTheme.radius());
        int accentColor = accent();

        graphics.pose().pushMatrix();
        float scale = feedbackScale();
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        if (scale != 1.0F) {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-centerX, -centerY);
        }
        graphics.pose().translate(0.0F, entranceOffset(10.0F));

        DrawUtil.drawGlow(graphics, x, y, width, height, radius, accentColor,
                (this.emphasized ? 0.35F : 0.0F) + 0.65F * glowAmount());
        DrawUtil.fillGlass(graphics, x, y, width, height, radius, pressAmount(), hoverAmount());

        // Акцентная полоса снизу выезжает при наведении — нажатие «читается» заранее.
        float barProgress = Math.max(this.emphasized ? 0.35F : 0.0F, hoverAmount());
        int barHeight = Math.max(1, Math.round(height * 0.06F));
        int barWidth = Math.round((width - radius) * barProgress);
        if (barWidth > 2) {
            DrawUtil.fillRounded(graphics, x + (width - barWidth) / 2, y + height - barHeight - 1, barWidth, barHeight,
                    barHeight / 2, ColorUtil.fade(accentColor, 0.9F * alpha));
        }

        renderRipple(graphics, x, y, width, height, radius, accentColor);
        drawCentered(graphics, font, getMessage(), x, y + (height - font.lineHeight) / 2, width,
                ColorUtil.fade(isActive() ? AuroraTheme.textPrimary() : AuroraTheme.textMuted(), alpha));
        graphics.pose().popMatrix();
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        beginFeedback(event.x(), event.y());
        if (this.action == null) {
            AuroraLog.warn("кнопка «" + getMessage().getString() + "» без действия");
            return;
        }
        try {
            this.action.accept(this);
        } catch (Throwable throwable) {
            AuroraLog.error("действие кнопки «" + getMessage().getString() + "»", throwable);
        }
    }

    private int accent() {
        if (this.accent != 0) {
            return this.accent;
        }
        return this.emphasized ? AuroraTheme.accent() : ColorUtil.fade(AuroraTheme.accent(), 0.85F);
    }
}
