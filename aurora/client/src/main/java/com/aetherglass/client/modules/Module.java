package com.aetherglass.client.modules;

import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.utils.AuroraLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Один модуль клиента.
 *
 * <p>Модуль описывает себя (имя, описание, раздел), хранит список настроек, умеет
 * включаться и выключаться с освобождением ресурсов и сообщает, доступны ли ему
 * необходимые ресурсы. Любая ошибка включения не роняет игру: модуль откатывается
 * в выключенное состояние.</p>
 */
public abstract class Module {
    private final String id;
    private final String name;
    private final String description;
    private final ModuleCategory category;
    private final Map<String, Setting<?>> settings = new LinkedHashMap<>();
    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private int keybind = -1;
    private Runnable changeHandler;

    protected Module(String id, String name, String description, ModuleCategory category) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.description = description == null ? "" : description;
        this.category = Objects.requireNonNull(category, "category");
    }

    /** Регистрирует настройку и возвращает её — для записи в поле модуля. */
    protected final <T extends Setting<?>> T register(T setting) {
        if (this.settings.putIfAbsent(setting.id(), setting) != null) {
            throw new IllegalStateException("настройка " + setting.id() + " уже есть в модуле " + this.id);
        }
        return setting;
    }

    /** Вызывается при включении: здесь выделяются ресурсы. */
    protected void onEnable() {
    }

    /** Вызывается при выключении: здесь ресурсы освобождаются. */
    protected void onDisable() {
    }

    /** Продвигает состояние модуля на один кадр; вызывается только у включённых модулей. */
    public void tick(float deltaSeconds) {
    }

    /** Пустая строка — ресурсы доступны; иначе текст, который покажет интерфейс. */
    public String availabilityNote() {
        return "";
    }

    /** Включён ли модуль при первом запуске. */
    public boolean enabledByDefault() {
        return false;
    }

    /** Можно ли выключить модуль вовсе (оформление всегда работает). */
    public boolean canBeDisabled() {
        return true;
    }

    /** Реакция модуля на изменение собственной настройки. */
    protected void onSettingChanged(Setting<?> setting) {
    }

    /** Передаётся из настроек в {@link #settingChanged(Setting)}: модуль уведомляет менеджера. */
    protected final <T> void settingChanged(Setting<T> setting) {
        // Реакция модуля на смену настройки не должна ронять игру: именно
        // этот путь срабатывает, когда в интерфейсе щёлкают переключатель,
        // и исключение отсюда уходит прямиком в обработчик мыши, а из него —
        // в отрисовку кадра. Раньше try был только на обработчике ниже.
        try {
            onSettingChanged(setting);
        } catch (Throwable throwable) {
            AuroraLog.error("реакция модуля " + this.id + " на настройку " + setting.id(), throwable);
            forceDisable();
        }
        Runnable handler = this.changeHandler;
        if (handler != null) {
            try {
                handler.run();
            } catch (Throwable throwable) {
                AuroraLog.error("уведомление об изменении " + this.id + "." + setting.id(), throwable);
            }
        }
    }

    /** Устанавливается менеджером: сохранение конфигурации при изменении настройки. */
    public final void setChangeHandler(Runnable handler) {
        this.changeHandler = handler;
    }

    public final String id() {
        return this.id;
    }

    public final String name() {
        return this.name;
    }

    public final String description() {
        return this.description;
    }

    public final ModuleCategory category() {
        return this.category;
    }

    /** Ключ параметра в файле настроек. */
    public final String key(String settingId) {
        return this.id + "." + settingId;
    }

    public final boolean isEnabled() {
        return this.enabled.get();
    }

    public final int keybind() {
        return this.keybind;
    }

    public final void setKeybind(int keyCode) {
        int normalized = keyCode <= 0 ? -1 : keyCode;
        if (this.keybind == normalized) {
            return;
        }
        this.keybind = normalized;
        Runnable handler = this.changeHandler;
        if (handler != null) {
            try {
                handler.run();
            } catch (Throwable throwable) {
                AuroraLog.error("сохранение бинда " + this.id, throwable);
            }
        }
    }

    public final void toggle() {
        if (canBeDisabled() || !isEnabled()) {
            setEnabled(!isEnabled());
        }
    }

    /** Включает или выключает модуль с обработкой ошибок и откатом при неудаче. */
    public final void setEnabled(boolean value) {
        if (value == this.enabled.get()) {
            return;
        }
        if (value) {
            String note = availabilityNote();
            if (!note.isEmpty()) {
                AuroraLog.warn("модуль " + this.id + " недоступен: " + note);
                return;
            }
            this.enabled.set(true);
            com.aetherglass.client.gui.notification.NotificationManager.get().pushModuleToggle(this.name, true);
            try {
                onEnable();
            } catch (Throwable throwable) {
                this.enabled.set(false);
                AuroraLog.error("включение модуля " + this.id, throwable);
            }
        } else {
            this.enabled.set(false);
            com.aetherglass.client.gui.notification.NotificationManager.get().pushModuleToggle(this.name, false);
            try {
                onDisable();
            } catch (Throwable throwable) {
                AuroraLog.error("выключение модуля " + this.id, throwable);
            }
        }
        Runnable handler = this.changeHandler;
        if (handler != null) {
            try {
                handler.run();
            } catch (Throwable throwable) {
                AuroraLog.error("сохранение состояния " + this.id, throwable);
            }
        }
    }

    /** Выключает модуль без проверки состояния: при аварийном откате. */
    public final void forceDisable() {
        if (this.enabled.compareAndSet(true, false)) {
            try {
                onDisable();
            } catch (Throwable throwable) {
                AuroraLog.error("выключение модуля " + this.id, throwable);
            }
        }
    }

    public final List<Setting<?>> settings() {
        return Collections.unmodifiableList(new ArrayList<>(this.settings.values()));
    }

    public final Setting<?> setting(String settingId) {
        return this.settings.get(settingId);
    }

    @Override
    public final boolean equals(Object other) {
        return other instanceof Module module && this.id.equals(module.id);
    }

    @Override
    public final int hashCode() {
        return this.id.hashCode();
    }
}
