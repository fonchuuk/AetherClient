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
 * Компактная матовая стеклянная кнопка-капсула (Lunar / Feather Frosted Glass Pill).
 *
 * <p>Вместо громоздких синих труб рисует аккуратную полупрозрачную дымчатую линзу
 * с мягкой тенью, солнечным бликом по поверхности и плавным откликом при наведении.</p>
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
        int radius = Math.max(5, Math.min(height / 2, Math.round(AuroraTheme.radius() * 0.85F)));
        int accentColor = accent();

        graphics.pose().pushMatrix();
        float hover = Math.max(0.0F, Math.min(1.0F, hoverAmount()));
        float scale = feedbackScale() * (1.0F + 0.012F * hover);
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        if (scale != 1.0F) {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-centerX, -centerY);
        }
        graphics.pose().translate(0.0F, entranceOffset(6.0F) - hover * 0.6F);

        DrawUtil.drawFrostedPill(
                graphics, x, y, width, height, radius,
                alpha, hover, (this.emphasized ? 0.28F : 0.0F) + pressAmount() * 0.45F
        );

        // Аккуратная боковая световая капля для основных кнопок вместо заливки всей кнопки синим
        if (this.emphasized || hover > 0.05F) {
            int dotH = Math.max(6, height - 10);
            int dotY = y + (height - dotH) / 2;
            float dotAlpha = (this.emphasized ? 0.72F : 0.0F) + 0.28F * hover;
            DrawUtil.fillRounded(graphics, x + 5, dotY, 3, dotH, 1,
                    ColorUtil.fade(accentColor, Math.min(1.0F, dotAlpha) * alpha));
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
