package com.aetherglass.client.modules.settings;

import java.util.function.Consumer;

/** Переключатель. */
public final class BooleanSetting extends Setting<Boolean> {
    public BooleanSetting(String id, String name, String description, boolean defaultValue,
                          Consumer<Setting<Boolean>> onChange) {
        super(id, name, description, defaultValue, onChange);
    }

    @Override
    public Kind kind() {
        return Kind.BOOLEAN;
    }

    @Override
    public void read(String raw) {
        if (raw != null) {
            assign(Boolean.parseBoolean(raw.trim()));
        }
    }

    @Override
    public String write() {
        return Boolean.toString(get());
    }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(get());
    }

    public void toggle() {
        set(!isEnabled());
    }
}
