package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Переключатель перечисления: левая кнопка — назад, правая — вперёд.
 *
 * @param <E> тип значений
 */
public final class EnumButton<E extends Enum<E>> extends AuroraWidget {
    private static final int HEIGHT = 16;

    private final EnumSetting<E> setting;
    private final Consumer<E> listener;

    public EnumButton(int x, int y, int width, EnumSetting<E> setting, Consumer<E> listener) {
        super(x, y, width, HEIGHT, Component.empty());
        this.setting = setting;
        this.listener = listener;
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
        int accent = AuroraTheme.accent();

        graphics.pose().pushMatrix();
        float scale = feedbackScale();
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;
        if (scale != 1.0F) {
            graphics.pose().translate(centerX, centerY);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-centerX, -centerY);
        }
        graphics.pose().translate(0.0F, entranceOffset(8.0F));

        DrawUtil.drawGlow(graphics, x, y, width, height, radius, accent, 0.4F * glowAmount());
        DrawUtil.fillGlass(graphics, x, y, width, height, radius, pressAmount(), hoverAmount());

        int arrowColor = ColorUtil.fade(AuroraTheme.textSecondary(), alpha);
        int arrowY = y + height / 2 - 3;
        // Левая стрелка.
        for (int row = 0; row < 7; row++) {
            int span = 3 - Math.abs(row - 3);
            graphics.fill(x + 5 + (3 - span), arrowY + row, x + 5 + (3 - span) + span + 1, arrowY + row + 1,
                    arrowColor);
        }
        // Правая стрелка.
        for (int row = 0; row < 7; row++) {
            int span = 3 - Math.abs(row - 3);
            graphics.fill(x + width - 12 + (3 - span), arrowY + row, x + width - 12 + (3 - span) + span + 1,
                    arrowY + row + 1, arrowColor);
        }

        renderRipple(graphics, x, y, width, height, radius, accent);
        Component label = Component.literal(label());
        drawCentered(graphics, font, label, x + 12, y + (height - font.lineHeight) / 2, width - 24,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha));
        graphics.pose().popMatrix();
    }

    private String label() {
        return switch (this.setting.get().name()) {
            case "CIRCLE" -> "Круг";
            case "SQUARE" -> "Квадрат";
            case "STAR" -> "Звезда";
            case "SPARK" -> "Искра";
            case "HEART" -> "Сердце";
            case "SMOKE" -> "Дым";
            case "OUTLINE" -> "Контур";
            case "GLOW" -> "Свечение";
            case "FILLED" -> "Заливка";
            case "PULSE" -> "Пульсация";
            case "GRADIENT" -> "Градиент";
            case "AURORA" -> "Сияние";
            case "SUNSET" -> "Закат";
            case "OCEAN" -> "Океан";
            case "VOID" -> "Пустота";
            case "CUSTOM" -> "Своё";
            case "CROSS" -> "Крест";
            case "DOT" -> "Точка";
            case "ARROW" -> "Стрелка";
            case "CIRCLE_DOT" -> "Круг с точкой";
            case "BRACKET" -> "Скобки";
            case "SWING" -> "Размах";
            case "HOLD" -> "Удержание";
            case "BOB" -> "Покачивание";
            case "NONE" -> "Нет";
            case "GLASS" -> "Стекло";
            case "NEON" -> "Неон";
            case "RAINBOW" -> "Радуга";
            case "FADE" -> "Затухание";
            case "SLIDE" -> "Сдвиг";
            case "RIPPLE" -> "Волна";
            case "GROW" -> "Рост";
            case "COMPACT" -> "Компактная";
            case "COMFORTABLE" -> "Обычная";
            case "SPACIOUS" -> "Просторная";
            case "SLOW" -> "Медленно";
            case "NORMAL" -> "Обычная";
            case "FAST" -> "Быстро";
            default -> this.setting.get().name();
        };
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (!isActive() || !visible) {
            return false;
        }
        if (!isMouseOver(event.x(), event.y())) {
            return false;
        }
        boolean backwards = event.x() < getX() + 12.0D || event.button() != 0;
        beginFeedback(event.x(), event.y());
        E next = this.setting.next(backwards);
        this.setting.set(next);
        if (this.listener != null) {
            this.listener.accept(next);
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        return true;
    }
}
