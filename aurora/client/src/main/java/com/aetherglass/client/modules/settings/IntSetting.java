package com.aetherglass.client.modules.settings;

import java.util.function.Consumer;

/** Целое число в заданных пределах. */
public final class IntSetting extends Setting<Integer> {
    private final int min;
    private final int max;

    public IntSetting(String id, String name, String description, int defaultValue, int min, int max,
                      Consumer<Setting<Integer>> onChange) {
        super(id, name, description, clamp(defaultValue, min, max), onChange);
        this.min = min;
        this.max = max;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public Kind kind() {
        return Kind.INT;
    }

    @Override
    public void read(String raw) {
        if (raw == null) {
            return;
        }
        try {
            assign(clamp(Integer.parseInt(raw.trim()), this.min, this.max));
        } catch (NumberFormatException exception) {
            // Значение из конфигурации нечитаемо — оставляем текущее.
        }
    }

    @Override
    public String write() {
        return Integer.toString(get());
    }

    @Override
    public void set(Integer newValue) {
        assign(clamp(newValue == null ? this.min : newValue, this.min, this.max));
    }

    public int min() {
        return this.min;
    }

    public int max() {
        return this.max;
    }
}
