package com.aetherglass.client.gui.inventory;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Аниматор плавного перелёта предметов по дуге при {@code Shift-Click} и {@code Quick Move / Drag} (Блок 6.1–6.2).
 *
 * <p>Рисуется поверх интерфейса контейнера (инвентарь, сундук, верстак, печка, бочка, шалкер, эндер-сундук).
 * Во время полёта целевой слот временно подавляется, чтобы предмет не двоился.</p>
 */
public final class InventoryTransferAnimator {
    private static final int MAX_ACTIVE_FLIGHTS = 36;
    private static final List<FlyingItem> FLIGHTS = new ArrayList<>();

    private InventoryTransferAnimator() {
    }

    public static final class FlyingItem {
        public final ItemStack stack;
        public final float startX;
        public final float startY;
        public final float endX;
        public final float endY;
        public final int destSlotIndex;
        public final float startTime;
        public final float duration;
        public final float arcHeight;

        FlyingItem(ItemStack stack, float startX, float startY, float endX, float endY,
                   int destSlotIndex, float startTime, float duration) {
            this.stack = stack;
            this.startX = startX;
            this.startY = startY;
            this.endX = endX;
            this.endY = endY;
            this.destSlotIndex = destSlotIndex;
            this.startTime = startTime;
            this.duration = Math.max(0.10F, Math.min(0.45F, duration));
            float dist = (float) Math.hypot(endX - startX, endY - startY);
            this.arcHeight = Math.min(28.0F, Math.max(10.0F, dist * 0.18F));
        }

        public float progress(float now) {
            return Math.max(0.0F, Math.min(1.0F, (now - this.startTime) / this.duration));
        }

        public float currentX(float now) {
            float t = Easing.easeOutCubic(progress(now));
            return this.startX + (this.endX - this.startX) * t;
        }

        public float currentY(float now) {
            float raw = progress(now);
            float t = Easing.easeOutCubic(raw);
            float base = this.startY + (this.endY - this.startY) * t;
            // Параболическая дуга: максимальный подъём в середине траектории
            float parabola = 4.0F * t * (1.0F - t);
            return base - parabola * this.arcHeight;
        }
    }

    public static void spawnFlight(ItemStack stack, float startX, float startY,
                                   float endX, float endY, int destSlotIndex, float durationSeconds) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (Math.abs(endX - startX) < 1.0F && Math.abs(endY - startY) < 1.0F) {
            return;
        }
        if (FLIGHTS.size() >= MAX_ACTIVE_FLIGHTS) {
            FLIGHTS.remove(0);
        }
        FLIGHTS.add(new FlyingItem(
                stack.copy(),
                startX,
                startY,
                endX,
                endY,
                destSlotIndex,
                FrameClock.elapsedSeconds(),
                durationSeconds
        ));
    }

    /** Возвращает {@code true}, если предмет ещё летит в указанный целевой слот (чтобы не двоился). */
    public static boolean isSlotInFlight(int slotIndex) {
        if (FLIGHTS.isEmpty() || slotIndex < 0) {
            return false;
        }
        float now = FrameClock.elapsedSeconds();
        for (int i = 0; i < FLIGHTS.size(); i++) {
            FlyingItem f = FLIGHTS.get(i);
            if (f.destSlotIndex == slotIndex && f.progress(now) < 0.92F) {
                return true;
            }
        }
        return false;
    }

    /** Рисует все летящие предметы поверх всех слотов контейнера. */
    public static void renderFlights(GuiGraphics graphics) {
        if (FLIGHTS.isEmpty() || graphics == null) {
            return;
        }
        float now = FrameClock.elapsedSeconds();
        Minecraft mc = Minecraft.getInstance();
        int accent = ColorController.currentAccent();

        Iterator<FlyingItem> it = FLIGHTS.iterator();
        while (it.hasNext()) {
            FlyingItem f = it.next();
            float p = f.progress(now);
            if (p >= 1.0F) {
                it.remove();
                continue;
            }
            int x = Math.round(f.currentX(now));
            int y = Math.round(f.currentY(now));

            // Мягкий стеклянный ореол-след под летящим предметом
            float glowAlpha = (float) Math.sin(p * Math.PI) * 0.42F;
            DrawUtil.fillRounded(graphics, x - 2, y - 2, 20, 20, 6, ColorUtil.fade(accent, glowAlpha));

            graphics.renderItem(f.stack, x, y);
            if (mc != null && mc.font != null) {
                graphics.renderItemDecorations(mc.font, f.stack, x, y);
            }
        }
    }

    public static void clear() {
        FLIGHTS.clear();
    }

    public static int activeFlightCount() {
        return FLIGHTS.size();
    }
}
