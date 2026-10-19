package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Переключатель: стеклянная дорожка и «жидкий» ползунок, переливающийся при смене. */
public final class AuroraToggle extends AuroraWidget {
    private static final int HEIGHT = 18;
    private static final int WIDTH = 36;

    private final AnimationTimer state = new AnimationTimer(0.0F);
    private final Consumer<Boolean> listener;
    private boolean value;

    public AuroraToggle(int x, int y, boolean initial, Consumer<Boolean> listener) {
        super(x, y, WIDTH, HEIGHT, Component.empty());
        this.value = initial;
        this.listener = listener;
        this.state.snap(initial ? 1.0F : 0.0F);
    }

    public boolean value() {
        return this.value;
    }

    /** Ставит значение без вызова слушателя: используется при загрузке конфигурации. */
    public void setSilently(boolean value) {
        this.value = value;
        this.state.snap(value ? 1.0F : 0.0F);
    }

    @Override
    public void tickAnimation(float deltaSeconds, boolean hovered) {
        super.tickAnimation(deltaSeconds, hovered);
        float step = Math.max(0.0F, deltaSeconds) * AuroraTheme.motion();
        this.state.setTarget(this.value ? 1.0F : 0.0F);
        this.state.update(step);
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
        int radius = height / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, entranceOffset(8.0F));

        // Дорожка: в выключенном состоянии простое стекло, во включённом — акцентный градиент.
        if (this.state.current() > 0.001F) {
            DrawUtil.fillRounded(graphics, x, y, width, height, radius,
                    ColorUtil.fade(AuroraTheme.accent(), 0.35F * this.state.current() * alpha));
        }
        DrawUtil.fillGlass(graphics, x, y, width, height, radius, pressAmount(), hoverAmount());

        int knobSize = height - 6;
        int travel = width - height + 2;
        float eased = Easing.easeOutCubic(this.state.current());
        int knobX = Math.round(x + 3 + travel * eased);
        int knobY = y + 3;

        DrawUtil.drawGlow(graphics, knobX, knobY, knobSize, knobSize, knobSize / 2, AuroraTheme.accent(),
                0.25F + 0.55F * this.state.current());
        int knobColor = ColorUtil.lerp(AuroraTheme.textSecondary(), AuroraTheme.accent(), eased);
        DrawUtil.drawSliderKnob(graphics, knobX, knobY, knobSize, ColorUtil.fade(knobColor, alpha));

        renderRipple(graphics, x, y, width, height, radius, AuroraTheme.accent());

        Component label = Component.translatable(this.value ? "aurora.common.on" : "aurora.common.off");
        int textColor = ColorUtil.fade(this.value ? AuroraTheme.accent() : AuroraTheme.textMuted(), alpha);
        graphics.drawString(font, label, x + width + 6, y + (height - font.lineHeight) / 2, textColor, false);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        beginFeedback(event.x(), event.y());
        this.value = !this.value;
        if (this.listener != null) {
            this.listener.accept(this.value);
        }
    }
}
