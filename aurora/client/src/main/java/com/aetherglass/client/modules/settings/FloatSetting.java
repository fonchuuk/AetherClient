package com.aetherglass.client.modules.settings;

import java.util.function.Consumer;

/** Число с плавающей точкой в заданных пределах — скорость, интенсивность, размер. */
public final class FloatSetting extends Setting<Float> {
    private final float min;
    private final float max;
    private final float step;

    public FloatSetting(String id, String name, String description, float defaultValue, float min, float max,
                        float step, Consumer<Setting<Float>> onChange) {
        super(id, name, description, clamp(defaultValue, min, max), onChange);
        this.min = min;
        this.max = max;
        this.step = step > 0.0F ? step : 0.01F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public Kind kind() {
        return Kind.FLOAT;
    }

    @Override
    public void read(String raw) {
        if (raw == null) {
            return;
        }
        try {
            assign(clamp(Float.parseFloat(raw.trim()), this.min, this.max));
        } catch (NumberFormatException exception) {
            // Значение из конфигурации нечитаемо — оставляем текущее.
        }
    }

    @Override
    public String write() {
        return Float.toString(get());
    }

    @Override
    public void set(Float newValue) {
        float value = newValue == null ? this.min : newValue;
        if (this.step > 0.0F) {
            value = Math.round(value / this.step) * this.step;
        }
        assign(clamp(value, this.min, this.max));
    }

    public float min() {
        return this.min;
    }

    public float max() {
        return this.max;
    }

    public float step() {
        return this.step;
    }
}
