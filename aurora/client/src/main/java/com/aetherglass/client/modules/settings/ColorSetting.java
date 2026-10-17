package com.aetherglass.client.modules.settings;

import java.util.function.Consumer;

/** Цвет в формате ARGB (как принимает GuiGraphics). */
public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String id, String name, String description, int defaultValue,
                        Consumer<Setting<Integer>> onChange) {
        super(id, name, description, defaultValue, onChange);
    }

    @Override
    public Kind kind() {
        return Kind.COLOR;
    }

    @Override
    public void read(String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            long parsed = Long.parseLong(raw.trim().replace("#", ""), 16);
            assign((int) (parsed & 0xFFFFFFFFL));
        } catch (NumberFormatException exception) {
            // Цвет записан с ошибкой — оставляем текущий.
        }
    }

    @Override
    public String write() {
        return String.format("#%08X", get());
    }

    /** RGB без прозрачности (для виджетов, которые прозрачностью не управляют). */
    public int rgb() {
        return get() | 0xFF000000;
    }

    public void setRgb(int rgb) {
        set((rgb & 0x00FFFFFF) | 0xFF000000);
    }
}
