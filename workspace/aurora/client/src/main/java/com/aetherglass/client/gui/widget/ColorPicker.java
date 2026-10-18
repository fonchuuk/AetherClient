package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.Consumer;

/**
 * Компактный выбор цвета: квадрат «насыщенность — яркость», полоса оттенка и полоса прозрачности.
 *
 * <p>Квадрат рисуется двумя проходами градиентов: сначала вертикальный переход
 * «цвет — чёрный», затем поверх него по одному столбцу — переход «белый — чёрный»
 * с прозрачностью, обратной насыщенности. Так получается точная плоскость HSV
 * без единой готовой текстуры.</p>
 */
public final class ColorPicker extends AuroraWidget {
    private static final int SV_HEIGHT = 64;
    private static final int BAR_HEIGHT = 8;
    private static final int GAP = 4;
    private static final int TOTAL_HEIGHT = 12 + GAP + SV_HEIGHT + GAP + BAR_HEIGHT + GAP + BAR_HEIGHT;

    private final ColorSetting setting;
    private final Consumer<Integer> listener;

    private float hue;
    private float saturation;
    private float brightness;
    private float opacity;

    public ColorPicker(int x, int y, int width, ColorSetting setting, Consumer<Integer> listener) {
        super(x, y, width, TOTAL_HEIGHT, setting.label());
        this.setting = setting;
        this.listener = listener;
        int color = setting.get();
        this.hue = ColorUtil.hueOf(color);
        this.saturation = ColorUtil.saturationOf(color);
        this.brightness = ColorUtil.brightnessOf(color);
        this.opacity = ColorUtil.alpha(color) / 255.0F;
    }

    private int svTop() {
        return getY() + 12 + GAP;
    }

    private int hueTop() {
        return svTop() + SV_HEIGHT + GAP;
    }

    private int alphaTop() {
        return hueTop() + BAR_HEIGHT + GAP;
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

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, entranceOffset(8.0F));

        int current = currentColor();
        // Образец и шестнадцатеричное значение.
        DrawUtil.fillRounded(graphics, x, y, 24, 12, 4, ColorUtil.fade(current, alpha));
        DrawUtil.fillRoundedBordered(graphics, x, y, 24, 12, 4, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), alpha), 1);
        graphics.drawString(font, ColorUtil.toHex(current), x + 30, y + 2,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

        drawSaturationValue(graphics, x, svTop(), width, SV_HEIGHT, alpha);
        drawHueBar(graphics, x, hueTop(), width, BAR_HEIGHT, alpha);
        drawAlphaBar(graphics, x, alphaTop(), width, BAR_HEIGHT, current, alpha);

        // Курсор на плоскости.
        int cursorX = x + Math.round((width - 1) * this.saturation);
        int cursorY = svTop() + Math.round((SV_HEIGHT - 1) * (1.0F - this.brightness));
        DrawUtil.fillRoundedBordered(graphics, cursorX - 3, cursorY - 3, 7, 7, 3, 0,
                ColorUtil.fade(0xFF101010, 0.75F * alpha), 1);
        DrawUtil.fillRoundedBordered(graphics, cursorX - 2, cursorY - 2, 5, 5, 2, 0,
                ColorUtil.fade(0xFFFFFFFF, alpha), 1);
        // Курсоры на полосах.
        int hueCursor = x + Math.round((width - 1) * this.hue);
        DrawUtil.fillRounded(graphics, hueCursor - 1, hueTop() - 1, 3, BAR_HEIGHT + 2, 1,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        int alphaCursor = x + Math.round((width - 1) * this.opacity);
        DrawUtil.fillRounded(graphics, alphaCursor - 1, alphaTop() - 1, 3, BAR_HEIGHT + 2, 1,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        renderRipple(graphics, x, y, width, TOTAL_HEIGHT, 6, current);
        graphics.pose().popMatrix();
    }

    private void drawSaturationValue(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int full = ColorUtil.hsb(this.hue, 1.0F, 1.0F);
        graphics.fillGradient(x, y, x + width, y + height, ColorUtil.fade(full, alpha),
                ColorUtil.fade(0xFF000000, alpha));
        for (int column = 0; column < width; column++) {
            float sat = column / (float) Math.max(1, width - 1);
            int veil = Math.round(255 * (1.0F - sat));
            if (veil <= 0) {
                continue;
            }
            graphics.fillGradient(x + column, y, x + column + 1, y + height,
                    ColorUtil.fade(0xFFFFFFFF, (veil / 255.0F) * alpha),
                    ColorUtil.fade(0xFF000000, (veil / 255.0F) * alpha));
        }
        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, 4, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), alpha), 1);
    }

    private void drawHueBar(GuiGraphics graphics, int x, int y, int width, int height, float alpha) {
        int step = Math.max(1, width / 120);
        for (int column = 0; column < width; column += step) {
            int color = ColorUtil.hsb(column / (float) Math.max(1, width - 1), 1.0F, 1.0F);
            graphics.fill(x + column, y, x + Math.min(width, column + step), y + height,
                    ColorUtil.fade(color, alpha));
        }
        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, 3, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), alpha), 1);
    }

    private void drawAlphaBar(GuiGraphics graphics, int x, int y, int width, int height, int color, float alpha) {
        // Шахматная подложка: так видно, где цвет прозрачный.
        for (int cell = 0; cell * 4 < width; cell++) {
            int shade = cell % 2 == 0 ? 0xFF3A3A3A : 0xFF5A5A5A;
            graphics.fill(x + cell * 4, y, x + Math.min(width, (cell + 1) * 4), y + height,
                    ColorUtil.fade(shade, alpha));
        }
        int step = Math.max(1, width / 120);
        for (int column = 0; column < width; column += step) {
            float value = column / (float) Math.max(1, width - 1);
            graphics.fill(x + column, y, x + Math.min(width, column + step), y + height,
                    ColorUtil.fade(ColorUtil.withAlpha(color, Math.round(255 * value)), alpha));
        }
        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, 3, 0,
                ColorUtil.fade(AuroraTheme.panelBorder(), alpha), 1);
    }

    private int currentColor() {
        return ColorUtil.hsb(this.hue, this.saturation, this.brightness, this.opacity);
    }

    private void commit() {
        int color = currentColor();
        this.setting.set(color);
        if (this.listener != null) {
            this.listener.accept(color);
        }
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !visible || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        beginFeedback(event.x(), event.y());
        applyMouse(event.x(), event.y());
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }

    @Override
    protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double dragX, double dragY) {
        applyMouse(event.x(), event.y());
    }

    private void applyMouse(double mouseX, double mouseY) {
        int x = getX();
        int width = getWidth();
        double localX = Math.max(0.0D, Math.min(width - 1, mouseX - x));
        float horizontal = (float) (localX / Math.max(1, width - 1));

        if (mouseY >= svTop() && mouseY < svTop() + SV_HEIGHT) {
            this.saturation = horizontal;
            this.brightness = 1.0F - (float) ((mouseY - svTop()) / (SV_HEIGHT - 1.0D));
        } else if (mouseY >= hueTop() && mouseY < hueTop() + BAR_HEIGHT) {
            this.hue = horizontal;
        } else if (mouseY >= alphaTop() && mouseY < alphaTop() + BAR_HEIGHT) {
            this.opacity = Math.round(horizontal * 255.0F) / 255.0F;
        } else {
            return;
        }
        commit();
    }
}
