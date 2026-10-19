package com.aetherglass.client.modules.settings;

import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Одна настройка модуля.
 *
 * <p>Настройка умеет сбрасываться к значению по умолчанию, читать и писать себя в конфигурацию,
 * хранить собственную горячую клавишу ({@link #keybind()}) и уведомлять модуль об изменении.</p>
 */
public abstract class Setting<T> {
    /** Тип настройки: по нему экран выбирает виджет. */
    public enum Kind {
        BOOLEAN, INT, FLOAT, COLOR, ENUM
    }

    private final String id;
    private final String name;
    private final String description;
    private final T defaultValue;
    private final Consumer<Setting<T>> onChange;
    private T value;
    private int keybind = -1;

    protected Setting(String id, String name, String description, T defaultValue, Consumer<Setting<T>> onChange) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
        this.onChange = onChange;
    }

    public abstract Kind kind();

    /** Читает значение из строки конфигурации; при ошибке значение остаётся прежним. */
    public abstract void read(String raw);

    /** Значение в виде строки конфигурации. */
    public abstract String write();

    public final String id() {
        return this.id;
    }

    public final String name() {
        return this.name;
    }

    public final String description() {
        return this.description;
    }

    public final Component label() {
        return Component.literal(this.name);
    }

    public final T defaultValue() {
        return this.defaultValue;
    }

    public final T get() {
        return this.value;
    }

    public final int keybind() {
        return this.keybind;
    }

    public final void setKeybind(int keybind) {
        int normalized = keybind <= 0 ? -1 : keybind;
        if (this.keybind != normalized) {
            this.keybind = normalized;
            notifyChanged();
        }
    }

    protected final void assign(T newValue) {
        if (!Objects.equals(this.value, newValue)) {
            this.value = newValue;
            notifyChanged();
        }
    }

    public void set(T newValue) {
        assign(newValue);
    }

    public final void reset() {
        assign(this.defaultValue);
    }

    public final boolean isDefault() {
        return Objects.equals(this.value, this.defaultValue);
    }

    protected final void notifyChanged() {
        if (this.onChange == null) {
            return;
        }
        try {
            this.onChange.accept(this);
        } catch (Throwable throwable) {
            AuroraLog.error("обработчик настройки " + this.id, throwable);
        }
    }
}
