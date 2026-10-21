package com.aetherglass.client.modules;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.utils.AuroraLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Хранит модули, связывает их с настройками и продвигает их состояние во времени.
 *
 * <p>Менеджер — единственное место, которое знает, как состояние модуля превращается
 * в строку файла настроек и обратно. Сам модуль ничего не пишет на диск: он только
 * сообщает об изменении.</p>
 */
public final class ModuleManager {
    /** Один фоновый поток на все сохранения конфига. */
    private static final java.util.concurrent.ExecutorService SAVE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "aetherglass-config-save");
                thread.setDaemon(true);
                return thread;
            });
    private static final ModuleManager INSTANCE = new ModuleManager();

    private final Map<String, Module> byId = new LinkedHashMap<>();
    private final List<Module> order = new ArrayList<>();
    private boolean configReady;

    ModuleManager() {
    }

    public static ModuleManager get() {
        return INSTANCE;
    }

    /** Регистрирует модуль; повторная регистрация невозможна. */
    public void register(Module module) {
        if (this.byId.putIfAbsent(module.id(), module) != null) {
            throw new IllegalStateException("модуль " + module.id() + " уже зарегистрирован");
        }
        this.order.add(module);
        module.setChangeHandler(() -> {
            if (this.configReady) {
                writeModule(module);
            }
        });
        if (this.configReady) {
            readModule(module);
        }
    }

    public List<Module> modules() {
        return Collections.unmodifiableList(this.order);
    }

    public List<Module> all() {
        return modules();
    }

    public Module module(String id) {
        return this.byId.get(id);
    }

    /** Модуль нужного типа или {@code null}, если он не зарегистрирован. */
    public <M extends Module> M typed(String id, Class<M> type) {
        Module module = this.byId.get(id);
        return type.isInstance(module) ? type.cast(module) : null;
    }

    public List<Module> byCategory(ModuleCategory category) {
        List<Module> result = new ArrayList<>();
        for (Module module : this.order) {
            if (module.category() == category) {
                result.add(module);
            }
        }
        return result;
    }

    // ─────────────────────────── настройки ───────────────────────────

    /** Читает значения всех модулей из конфигурации. */
    public void loadFromConfig() {
        com.aetherglass.client.gui.notification.NotificationManager.get().setSuppressedDuringInit(true);
        try {
            ModConfig config = ModConfig.get();
            config.load();
            this.configReady = true;
            for (Module module : this.order) {
                readModule(module);
            }
            com.aetherglass.client.input.KeybindManager.get().loadFrom(config);
            AuroraLog.info("настройки прочитаны: " + config.status());
        } finally {
            com.aetherglass.client.gui.notification.NotificationManager.get().setSuppressedDuringInit(false);
        }
    }

    private void readModule(Module module) {
        ModConfig config = ModConfig.get();
        boolean enabled = config.getBoolean(module.key("enabled"), module.enabledByDefault());
        module.setKeybind(config.getInt(module.key("keybind"), -1, -1, 1024));
        for (Setting<?> setting : module.settings()) {
            setting.read(config.getString(module.key(setting.id()), null));
            setting.setKeybind(config.getInt(module.key(setting.id() + ".keybind"), -1, -1, 1024));
        }
        module.setEnabled(enabled);
    }

    private void writeModule(Module module) {
        ModConfig config = ModConfig.get();
        config.set(module.key("enabled"), module.isEnabled());
        config.set(module.key("keybind"), module.keybind());
        for (Setting<?> setting : module.settings()) {
            config.set(module.key(setting.id()), setting.write());
            config.set(module.key(setting.id() + ".keybind"), setting.keybind());
        }
    }

    /** Записывает текущее состояние всех модулей. */
    public void saveNow() {
        for (Module module : this.order) {
            writeModule(module);
        }
        ModConfig.get().save();
    }

    /**
     * Сохраняет конфиг в отдельном потоке.
     *
     * <p>Закрытие меню не должно ждать диск: запись всех модулей и файла
     * {@code aetherglass.conf} раньше шла прямо в потоке отрисовки, из-за чего
     * последний кадр меню «залипал». Очередь из одного потока — чтобы две
     * записи не наложились друг на друга.</p>
     */
    public void saveAsync() {
        SAVE_EXECUTOR.execute(() -> {
            try {
                saveNow();
            } catch (Throwable throwable) {
                AuroraLog.error("асинхронное сохранение конфига", throwable);
            }
        });
    }

    /** Сохраняет с задержкой: вызывается из игрового цикла. */
    public void saveIfNeeded() {
        ModConfig.get().saveIfNeeded();
    }

    // ─────────────────────────── состояние ───────────────────────────

    /**
     * Продвигает все модули на один кадр.
     *
     * @param deltaSeconds реальная длительность кадра
     */
    public void update(float deltaSeconds) {
        ColorController.update(deltaSeconds);
        for (Module module : this.order) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.tick(deltaSeconds);
            } catch (Throwable throwable) {
                AuroraLog.error("обновление модуля " + module.id(), throwable);
                module.forceDisable();
            }
        }
    }

    /** Освобождает ресурсы всех модулей: при завершении работы клиента. */
    public void shutdown() {
        shutdown(this::saveNow);
    }

    /** Сохранение отделено для проверки порядка без запуска Minecraft. */
    void shutdown(Runnable save) {
        try {
            // Сохраняем выбор пользователя, а не состояние после освобождения ресурсов.
            save.run();
        } finally {
            for (Module module : this.order) {
                module.forceDisable();
            }
        }
    }

    /** Строка для экрана «О клиенте»: сколько модулей включено. */
    public int enabledCount() {
        int count = 0;
        for (Module module : this.order) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    /** Список причин, по которым модули недоступны: показывается в списке модулей. */
    public List<String> problems() {
        List<String> notes = new ArrayList<>();
        for (Module module : this.order) {
            String note = module.availabilityNote();
            if (!note.isEmpty()) {
                notes.add(module.name() + ": " + note);
            }
        }
        return notes;
    }

    /** Длительность кадра: ею пользуются экраны, чтобы не тянуть часы напрямую. */
    public static float frameDelta() {
        return FrameClock.deltaSeconds();
    }
}
