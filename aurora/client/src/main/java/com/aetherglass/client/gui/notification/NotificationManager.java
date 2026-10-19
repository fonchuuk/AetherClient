package com.aetherglass.client.gui.notification;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Система всплывающих уведомлений в правом нижнем углу экрана в стиле матового
 * жидкого стекла (Lunar / Feather Frosted Glass).
 *
 * <p>Показывает плавно выезжающие дымчатые полупрозрачные стеклянные линзы с мягкой
 * парящей тенью, солнечным бликом по поверхности и каплевидным таймером.</p>
 */
public final class NotificationManager {
    private static final NotificationManager INSTANCE = new NotificationManager();
    private static final int MAX_VISIBLE = 5;
    private static final float DEFAULT_DURATION = 2.6F;

    public enum Type {
        ENABLED("ON", 0xFF5CE6A0),
        DISABLED("OFF", 0xFFFF6E80),
        KEYBIND("KEY", 0xFF78DCFF),
        ACHIEVEMENT("ACH", 0xFFFFD166),
        INFO("INFO", 0xFFC89BFF);

        private final String badge;
        private final int accent;

        Type(String badge, int accent) {
            this.badge = badge;
            this.accent = accent;
        }

        public String badge() {
            return this.badge;
        }

        public int accent() {
            return this.accent;
        }
    }

    public static final class Toast {
        private final String title;
        private final String subtitle;
        private final Type type;
        private final float duration;
        private float age;
        private float yOffsetSmooth;

        public Toast(String title, String subtitle, Type type, float duration) {
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.type = type == null ? Type.INFO : type;
            this.duration = Math.max(0.8F, duration);
            this.yOffsetSmooth = -1.0F;
        }

        public String title() {
            return this.title;
        }

        public String subtitle() {
            return this.subtitle;
        }

        public Type type() {
            return this.type;
        }

        public float age() {
            return this.age;
        }

        public float duration() {
            return this.duration;
        }
    }

    private final List<Toast> toasts = new ArrayList<>();
    private boolean suppressedDuringInit = true;

    private NotificationManager() {
    }

    public static NotificationManager get() {
        return INSTANCE;
    }

    public void setSuppressedDuringInit(boolean suppressed) {
        this.suppressedDuringInit = suppressed;
    }

    public boolean isSuppressedDuringInit() {
        return this.suppressedDuringInit;
    }

    public synchronized void push(String title, String subtitle, Type type) {
        push(title, subtitle, type, DEFAULT_DURATION);
    }

    public synchronized void push(String title, String subtitle, Type type, float duration) {
        if (this.suppressedDuringInit) {
            return;
        }
        if (this.toasts.size() >= MAX_VISIBLE + 2) {
            this.toasts.remove(0);
        }
        this.toasts.add(new Toast(title, subtitle, type, duration));
    }

    public void pushModuleToggle(String moduleName, boolean enabled) {
        if (this.suppressedDuringInit) {
            return;
        }
        push(
                moduleName,
                enabled ? "Модуль активирован" : "Модуль отключён",
                enabled ? Type.ENABLED : Type.DISABLED,
                2.3F
        );
    }

    public void pushAdvancement(String advancementTitle) {
        if (this.suppressedDuringInit) {
            return;
        }
        push(
                "Достижение получено",
                advancementTitle == null || advancementTitle.isBlank() ? "Новое достижение!" : advancementTitle,
                Type.ACHIEVEMENT,
                3.4F
        );
    }

    public void pushKeybindChanged(String targetName, String keyName) {
        if (this.suppressedDuringInit) {
            return;
        }
        push(
                "Привязка клавиши",
                targetName + " -> [" + keyName + "]",
                Type.KEYBIND,
                2.5F
        );
    }

    public synchronized List<Toast> activeToasts() {
        return new ArrayList<>(this.toasts);
    }

    public synchronized void clear() {
        this.toasts.clear();
    }

    public synchronized void tick(float dt) {
        if (dt <= 0.0F || this.toasts.isEmpty()) {
            return;
        }
        Iterator<Toast> it = this.toasts.iterator();
        while (it.hasNext()) {
            Toast toast = it.next();
            toast.age += dt;
            if (toast.age >= toast.duration) {
                it.remove();
            }
        }
    }

    public synchronized void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        float dt = FrameClock.deltaSeconds();
        tick(dt);
        if (this.toasts.isEmpty() || graphics == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }
        Font font = client.font;

        int cardH = 32;
        int gap = 6;
        int marginRight = 10;
        int marginBottom = 12;
        int count = Math.min(MAX_VISIBLE, this.toasts.size());
        int startIdx = Math.max(0, this.toasts.size() - count);

        for (int i = startIdx; i < this.toasts.size(); i++) {
            Toast toast = this.toasts.get(i);
            int slot = (this.toasts.size() - 1) - i;

            float enterT = Math.min(1.0F, toast.age / 0.24F);
            float exitT = Math.min(1.0F, Math.max(0.0F, (toast.duration - toast.age) / 0.28F));
            float visibility = Easing.easeOutCubic(Math.min(enterT, exitT));
            if (visibility <= 0.02F) {
                continue;
            }

            int titleW = font.width(toast.title);
            int subW = font.width(toast.subtitle);
            int cardW = Math.max(144, Math.min(228, Math.max(titleW, subW) + 52));

            float targetY = screenHeight - marginBottom - cardH - slot * (cardH + gap);
            if (toast.yOffsetSmooth < 0.0F) {
                toast.yOffsetSmooth = targetY;
            } else {
                toast.yOffsetSmooth += (targetY - toast.yOffsetSmooth) * Math.min(1.0F, dt * 16.0F);
            }

            int slideX = Math.round((1.0F - visibility) * (cardW + 18));
            int x = screenWidth - marginRight - cardW + slideX;
            int y = Math.round(toast.yOffsetSmooth);

            int radius = Math.max(6, Math.min(12, AuroraTheme.radiusPx()));
            int accent = toast.type.accent();

            // Матовая стеклянная карточка уведомления с мягкой тенью и солнечным бликом
            DrawUtil.drawLiquidGlassCard(graphics, x, y, cardW, cardH, radius, visibility, true);

            // Каплевидная стеклянная иконка-капсула слева
            int badgeX = x + 6;
            int badgeY = y + 6;
            int badgeW = 26;
            int badgeH = cardH - 12;
            DrawUtil.drawFrostedPill(graphics, badgeX, badgeY, badgeW, badgeH, 6, visibility, 0.2F, 0.45F);
            DrawUtil.fillRounded(graphics, badgeX, badgeY, badgeW, badgeH, 6,
                    ColorUtil.fade(accent, 0.16F * visibility));

            int badgeTextW = font.width(toast.type.badge());
            graphics.drawString(font, toast.type.badge(),
                    badgeX + (badgeW - badgeTextW) / 2, badgeY + (badgeH - 8) / 2,
                    ColorUtil.fade(accent, visibility), false);

            // Заголовок и подпись
            int textX = badgeX + badgeW + 7;
            graphics.drawString(font, toast.title, textX, y + 6,
                    ColorUtil.fade(AuroraTheme.textPrimary(), visibility), false);
            graphics.drawString(font, toast.subtitle, textX, y + 17,
                    ColorUtil.fade(AuroraTheme.textSecondary(), 0.92F * visibility), false);

            // Каплевидная полоска оставшегося времени внизу карточки
            float progress = Math.max(0.0F, Math.min(1.0F, 1.0F - (toast.age / toast.duration)));
            int barMaxW = cardW - radius * 2;
            int barW = Math.max(3, Math.round(barMaxW * progress));
            DrawUtil.fillRounded(graphics, x + radius, y + cardH - 3, barW, 2, 1,
                    ColorUtil.fade(accent, 0.78F * visibility));
        }
    }
}
