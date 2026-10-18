package com.aetherglass.client.modules.settings;

import java.util.function.Consumer;

/**
 * Выбор из набора значений.
 *
 * @param <E> тип перечисления
 */
public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
    private final E[] values;

    public EnumSetting(String id, String name, String description, E defaultValue, E[] values,
                       Consumer<Setting<E>> onChange) {
        super(id, name, description, defaultValue, onChange);
        this.values = values.clone();
    }

    @Override
    public Kind kind() {
        return Kind.ENUM;
    }

    @Override
    public void read(String raw) {
        if (raw == null) {
            return;
        }
        for (E value : this.values) {
            if (value.name().equalsIgnoreCase(raw.trim())) {
                assign(value);
                return;
            }
        }
    }

    @Override
    public String write() {
        return get().name();
    }

    public E[] values() {
        return this.values.clone();
    }

    /** Следующее значение по кругу: используется виджетом-переключателем. */
    public E next(boolean backwards) {
        int index = get().ordinal() + (backwards ? -1 : 1);
        int count = this.values.length;
        index = ((index % count) + count) % count;
        return this.values[index];
    }
}
