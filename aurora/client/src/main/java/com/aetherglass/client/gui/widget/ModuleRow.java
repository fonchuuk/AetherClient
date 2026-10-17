package com.aetherglass.client.gui.widget;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Карточка модуля в списке функций.
 *
 * <p>Внутри карточки — свои элементы (переключатель и кнопка настроек), и карточка
 * сама передаёт им события мыши: это избавляет экран от необходимости знать, как
 * устроен каждый модуль. При перетаскивании списка карточка двигает детей вместе
 * с собой, поэтому ничего не «разъезжается».</p>
 */
public final class ModuleRow extends AuroraWidget {
    private final Module module;
    private final List<AuroraWidget> children = new ArrayList<>();
    private final String activity;
    private final Runnable onToggle;
    private final Runnable onSettings;

    public ModuleRow(int x, int y, int width, Module module, String activity, Runnable onToggle,
                     Runnable onSettings) {
        super(x, y, width, 60, Component.literal(module.name()));
        this.module = module;
        this.activity = activity == null ? "" : activity;
        this.onToggle = onToggle;
        this.onSettings = onSettings;
        buildChildren();
    }

    private void buildChildren() {
        int right = getX() + getWidth();
        int top = getY() + 16;
        AuroraToggle toggle = new AuroraToggle(right - 44, top, this.module.isEnabled(), value -> {
            this.module.setEnabled(value);
            if (this.onToggle != null) {
                this.onToggle.run();
            }
        });
        if (!this.module.canBeDisabled()) {
            toggle.active = false;
        }
        this.children.add(toggle);
        if (!this.module.settings().isEmpty()) {
            this.children.add(new AuroraButton(right - 44 - 92, top + 1, 88, 16,
                    Component.literal("Настроить"), button -> {
                        if (this.onSettings != null) {
                            this.onSettings.run();
                        }
                    }));
        }
    }

    @Override
    public void setY(int y) {
        int delta = y - getY();
        super.setY(y);
        for (AuroraWidget child : this.children) {
            child.setY(child.getY() + delta);
        }
    }

    @Override
    public void setX(int x) {
        int delta = x - getX();
        super.setX(x);
        for (AuroraWidget child : this.children) {
            child.setX(child.getX() + delta);
        }
    }

    @Override
    public void tickAnimation(float deltaSeconds, boolean hovered) {
        super.tickAnimation(deltaSeconds, hovered);
        for (AuroraWidget child : this.children) {
            child.tickAnimation(deltaSeconds, child.isHovered());
        }
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

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0F, entranceOffset(12.0F));

        int accent = this.module.isEnabled() ? AuroraTheme.accent() : AuroraTheme.textMuted();
        DrawUtil.drawGlow(graphics, x, y, width, height, radius, accent,
                this.module.isEnabled() ? 0.25F + 0.35F * glowAmount() : 0.0F);
        DrawUtil.fillGlass(graphics, x, y, width, height, radius, pressAmount(), hoverAmount());
        DrawUtil.fillRounded(graphics, x + 1, y + 8, 3, height - 16, 1,
                ColorUtil.fade(accent, this.module.isEnabled() ? 0.95F : 0.4F));

        int textX = x + 12;
        graphics.drawString(font, this.module.name(), textX, y + 9,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        String description = this.module.description();
        int maxWidth = width - 170;
        if (font.width(description) > maxWidth) {
            description = font.plainSubstrByWidth(description, maxWidth - font.width("…")) + "…";
        }
        graphics.drawString(font, description, textX, y + 22,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
        String note = this.module.availabilityNote();
        String status = note.isEmpty() ? this.activity : note;
        if (!status.isEmpty()) {
            graphics.drawString(font, status, textX, y + 35,
                    ColorUtil.fade(note.isEmpty() ? AuroraTheme.textMuted() : AuroraTheme.danger(), alpha), false);
        }

        for (AuroraWidget child : this.children) {
            child.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        for (AuroraWidget child : this.children) {
            if (child.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        if (!isActive() || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        // Щелчок по свободному месту карточки переключает модуль: быстро и понятно.
        beginFeedback(event.x(), event.y());
        if (this.module.canBeDisabled()) {
            this.module.setEnabled(!this.module.isEnabled());
        }
        playDownSound(Minecraft.getInstance().getSoundManager());
        if (this.onToggle != null) {
            this.onToggle.run();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean handled = false;
        for (AuroraWidget child : this.children) {
            handled |= child.mouseReleased(event);
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        for (AuroraWidget child : this.children) {
            if (child.keyPressed(event)) {
                return true;
            }
        }
        return false;
    }

    /** Переключатель внутри карточки: доступен редактору и тестам. */
    public AuroraToggle toggle() {
        return (AuroraToggle) this.children.get(0);
    }

    public Module module() {
        return this.module;
    }
}
