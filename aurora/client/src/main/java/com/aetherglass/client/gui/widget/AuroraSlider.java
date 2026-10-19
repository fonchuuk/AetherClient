package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Компактный и аккуратный ползунок значения из матового стекла (Блок 3.3). */
public final class AuroraSlider extends AuroraWidget {
    private static final int HEIGHT = 12;

    /** Связь ползунка с настройкой. */
    public interface Binding {
        float get();

        void set(float value);

        /** Подпись справа от ползунка. */
        String display();
    }

    private final Binding binding;
    private final float min;
    private final float max;
    private final float step;
    private final AnimationTimer fill = new AnimationTimer(0.0F);
    private final Consumer<Float> onCommit;

    public AuroraSlider(int x, int y, int width, Binding binding, float min, float max, float step,
                        Consumer<Float> onCommit) {
        super(x, y, Math.min(width, 96), HEIGHT, Component.empty());
        this.binding = binding;
        this.min = min;
        this.max = max;
        this.step = step > 0.0F ? step : 0.01F;
        this.onCommit = onCommit;
        this.fill.snap(progress());
    }

    private float progress() {
        float span = this.max - this.min;
        if (span <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (this.binding.get() - this.min) / span));
    }

    private void setFromMouse(double mouseX) {
        float span = getWidth() - 6;
        float raw = (float) ((mouseX - getX() - 3) / Math.max(1.0F, span));
        float value = this.min + (this.max - this.min) * Math.max(0.0F, Math.min(1.0F, raw));
        value = Math.round(value / this.step) * this.step;
        value = Math.max(this.min, Math.min(this.max, value));
        float previous = this.binding.get();
        if (Math.abs(previous - value) < this.step * 0.0001F) {
            return;
        }
        this.binding.set(value);
        if (this.onCommit != null) {
            this.onCommit.accept(value);
        }
    }

    @Override
    public void tickAnimation(float deltaSeconds, boolean hovered) {
        super.tickAnimation(deltaSeconds, hovered);
        this.fill.setTarget(progress());
        this.fill.update(Math.max(0.0F, deltaSeconds) * AuroraTheme.motion());
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
        int trackH = 4;
        int trackY = y + (height - trackH) / 2;

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, entranceOffset(6.0F));

        // Компактная дорожка из матового стекла
        DrawUtil.fillRounded(graphics, x, trackY, width, trackH, 2,
                ColorUtil.withAlpha(0xFF152034, Math.round(110 * alpha)));
        DrawUtil.fillRoundedBordered(graphics, x, trackY, width, trackH, 2, 0,
                ColorUtil.withAlpha(0xFFE0F2FF, Math.round(32 * alpha)), 1);

        float progress = this.fill.current();
        int filled = Math.max(3, Math.round(width * progress));
        DrawUtil.fillRoundedGradient(graphics, x, trackY, filled, trackH, 2,
                ColorUtil.fade(AuroraTheme.accent(), 0.92F * alpha),
                ColorUtil.fade(ColorUtil.brighten(AuroraTheme.accent(), 0.22F), 0.92F * alpha));

        // Выпуклая стеклянная ручка 8x8
        int knobSize = 8;
        int knobX = x + Math.round((width - knobSize) * progress);
        int knobY = y + (height - knobSize) / 2;
        DrawUtil.fillRounded(graphics, knobX, knobY, knobSize, knobSize, 4,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        DrawUtil.fillRoundedBordered(graphics, knobX, knobY, knobSize, knobSize, 4, 0,
                ColorUtil.fade(AuroraTheme.accent(), 0.75F * alpha), 1);

        String text = this.binding.display();
        graphics.drawString(font, text, x + width + 5, y + (height - font.lineHeight) / 2,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
        graphics.pose().popMatrix();
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        beginFeedback(event.x(), event.y());
        setFromMouse(event.x());
    }

    @Override
    protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        setFromMouse(event.x());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float delta = scrollY > 0 ? this.step : -this.step;
        float value = Math.max(this.min, Math.min(this.max, this.binding.get() + delta));
        value = Math.round(value / this.step) * this.step;
        this.binding.set(value);
        if (this.onCommit != null) {
            this.onCommit.accept(value);
        }
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        float delta;
        if (event.key() == 263) {
            delta = -this.step;
        } else if (event.key() == 262) {
            delta = this.step;
        } else {
            return false;
        }
        float value = Math.max(this.min, Math.min(this.max, this.binding.get() + delta));
        value = Math.round(value / this.step) * this.step;
        this.binding.set(value);
        if (this.onCommit != null) {
            this.onCommit.accept(value);
        }
        return true;
    }

    public static Binding forInt(com.aetherglass.client.modules.settings.IntSetting setting) {
        return new Binding() {
            @Override
            public float get() {
                return setting.get().floatValue();
            }

            @Override
            public void set(float value) {
                setting.set(Math.round(value));
            }

            @Override
            public String display() {
                return Integer.toString(setting.get());
            }
        };
    }

    public static Binding forFloat(com.aetherglass.client.modules.settings.FloatSetting setting) {
        return new Binding() {
            @Override
            public float get() {
                return setting.get();
            }

            @Override
            public void set(float value) {
                setting.set(value);
            }

            @Override
            public String display() {
                return String.format("%.2f", setting.get());
            }
        };
    }

    public static Binding of(Supplier<Float> getter, Consumer<Float> setter, Supplier<String> display) {
        return new Binding() {
            @Override
            public float get() {
                return getter.get();
            }

            @Override
            public void set(float value) {
                setter.accept(value);
            }

            @Override
            public String display() {
                return display.get();
            }
        };
    }
}
