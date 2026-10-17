package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Подпись: просто текст, который не реагирует на мышь. */
public final class AuroraLabel extends AuroraWidget {
    /** Вариант начертания. */
    public enum Style {
        TITLE, BODY, MUTED
    }

    private final Style style;
    private final int color;

    public AuroraLabel(int x, int y, int width, Component text, Style style) {
        super(x, y, width, 12, text);
        this.style = style;
        this.color = switch (style) {
            case TITLE -> AuroraTheme.textPrimary();
            case MUTED -> AuroraTheme.textMuted();
            default -> AuroraTheme.textSecondary();
        };
        // Подпись не должна перехватывать щелчки.
        this.active = false;
    }

    public AuroraLabel(int x, int y, int width, Component text) {
        this(x, y, width, text, Style.BODY);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        float alpha = entranceAlpha();
        if (alpha <= 0.002F) {
            return;
        }
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, entranceOffset(8.0F));
        Component text = getMessage();
        if (this.style == Style.TITLE) {
            graphics.drawString(font, text, getX(), getY(), ColorUtil.fade(this.color, alpha), false);
        } else {
            // Длинный текст переносится по словам, чтобы не уезжать за край панели.
            graphics.drawWordWrap(font, text, getX(), getY(), getWidth(), ColorUtil.fade(this.color, alpha), false);
        }
        graphics.pose().popMatrix();
    }

    /** Высота текста с переносом: нужна, чтобы элементы ниже не наехали. */
    public static int wrappedHeight(Font font, Component text, int width) {
        return font.wordWrapHeight(text, width);
    }
}
