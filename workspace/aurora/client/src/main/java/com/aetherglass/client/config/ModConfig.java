package com.aetherglass.client.config;

import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Настройки клиента: собственный каталог, человекочитаемый формат, безопасное восстановление.
 *
 * <p>Файл — обычный текст со строками {@code ключ=значение}; комментарии начинаются с «#».
 * Все значения проверяются при чтении теми настройками, которым они принадлежат: непонятная
 * строка не ломает запуск, а просто игнорируется. Запись происходит не чаще раза в полторы
 * секунды и только если что-то действительно изменилось — игра не дёргает диск каждый кадр.</p>
 */
public final class ModConfig {
    private static final String FILE_NAME = "aetherglass.conf";
    private static final long SAVE_DELAY_NANOS = 1_500_000_000L;

    private static ModConfig instance;

    private final Map<String, String> values = new LinkedHashMap<>();
    private final Path directory;
    private final Path file;

    private boolean dirty;
    private long dirtySinceNanos;
    private String status = "настройки не загружались";
    private int ignoredLines;

    private ModConfig(Path directory) {
        this.directory = directory;
        this.file = directory.resolve(FILE_NAME);
    }

    /** Создаёт конфигурацию в каталоге {@code <каталог игры>/aetherglass}. */
    public static synchronized ModConfig get() {
        if (instance == null) {
            Path gameDir = Path.of(".");
            try {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.gameDirectory != null) {
                    gameDir = client.gameDirectory.toPath();
                }
            } catch (Throwable throwable) {
                AuroraLog.warn("каталог игры недоступен, конфигурация будет в текущем каталоге: "
                        + throwable.getMessage());
            }
            instance = new ModConfig(gameDir.resolve("aetherglass"));
        }
        return instance;
    }

    public Path file() {
        return this.file;
    }

    public String status() {
        return this.status;
    }

    public int ignoredLines() {
        return this.ignoredLines;
    }

    // ─────────────────────────────── чтение ───────────────────────────────

    public synchronized String getString(String key, String fallback) {
        String value = this.values.get(key);
        return value == null ? fallback : value;
    }

    public synchronized boolean getBoolean(String key, boolean fallback) {
        String raw = this.values.get(key);
        if (raw == null) {
            return fallback;
        }
        if ("true".equalsIgnoreCase(raw) || "1".equals(raw) || "да".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "0".equals(raw) || "нет".equalsIgnoreCase(raw)) {
            return false;
        }
        // Непонятное значение не считается «истиной»: берём то, что было по умолчанию.
        return fallback;
    }

    public synchronized int getInt(String key, int fallback, int min, int max) {
        String raw = this.values.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public synchronized float getFloat(String key, float fallback, float min, float max) {
        String raw = this.values.get(key);
        if (raw == null) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    // ─────────────────────────────── запись ───────────────────────────────

    /** Запоминает значение и помечает конфигурацию изменённой. */
    public synchronized void set(String key, String value) {
        if (value == null || value.equals(this.values.get(key))) {
            return;
        }
        this.values.put(key, value);
        markDirty();
    }

    public synchronized void set(String key, boolean value) {
        set(key, Boolean.toString(value));
    }

    public synchronized void set(String key, int value) {
        set(key, Integer.toString(value));
    }

    public synchronized void set(String key, float value) {
        set(key, Float.toString(value));
    }

    private void markDirty() {
        if (!this.dirty) {
            this.dirtySinceNanos = System.nanoTime();
        }
        this.dirty = true;
    }

    /**
     * Сохраняет настройки, если они менялись и с момента изменения прошло достаточно времени.
     *
     * <p>Вызывается из игрового цикла: при частых изменениях (ползунок) файл пишется
     * один раз, когда пользователь остановился.</p>
     */
    public void saveIfNeeded() {
        if (!this.dirty) {
            return;
        }
        if (System.nanoTime() - this.dirtySinceNanos < SAVE_DELAY_NANOS) {
            return;
        }
        save();
    }

    /** Немедленная запись: при закрытии экрана настроек или завершении работы клиента. */
    public synchronized void save() {
        this.dirty = false;
        try {
            Files.createDirectories(this.directory);
            Path temporary = this.file.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(temporary, render(), StandardCharsets.UTF_8);
            Files.move(temporary, this.file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            this.status = "сохранено: " + this.values.size() + " параметров";
        } catch (IOException exception) {
            this.status = "не удалось записать файл: " + exception.getMessage();
            AuroraLog.error("запись конфигурации " + this.file, exception);
        }
    }

    private String render() {
        List<String> keys = new ArrayList<>(this.values.keySet());
        keys.sort(String::compareTo);
        StringBuilder builder = new StringBuilder();
        builder.append("# Настройки AetherGlass Client\n");
        builder.append("# Формат: ключ=значение. Строки с # — комментарии и не читаются.\n");
        builder.append("# Файл создан автоматически; при повреждении рядом появится копия .broken\n");
        String group = null;
        for (String key : keys) {
            String prefix = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
            if (!prefix.equals(group)) {
                builder.append('\n').append('#').append(' ').append(prefix).append('\n');
                group = prefix;
            }
            builder.append(key).append('=').append(this.values.get(key)).append('\n');
        }
        return builder.toString();
    }

    // ─────────────────────────────── загрузка ───────────────────────────────

    /** Читает файл; при любой проблеме делает резервную копию и оставляет значения по умолчанию. */
    public synchronized void load() {
        this.values.clear();
        this.ignoredLines = 0;
        if (!Files.isRegularFile(this.file)) {
            this.status = "файла нет, используются значения по умолчанию";
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(this.file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            backupBroken();
            this.status = "файл не читается, взяты значения по умолчанию";
            AuroraLog.error("чтение конфигурации " + this.file, exception);
            return;
        }
        int blank = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                this.ignoredLines++;
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                this.ignoredLines++;
                continue;
            }
            if (this.values.put(key, value) != null) {
                blank++;
            }
        }
        this.status = "загружено параметров: " + this.values.size() + ", пропущено строк: " + this.ignoredLines;
        if (blank > 0) {
            AuroraLog.warn("в конфигурации повторяющиеся ключи: " + blank);
        }
    }

    /** Сбрасывает настройки: используется кнопкой «сбросить» в экране настроек. */
    public synchronized void reset() {
        this.values.clear();
        this.ignoredLines = 0;
        this.status = "настройки сброшены";
        markDirty();
    }

    private void backupBroken() {
        try {
            Path backup = this.file.resolveSibling(FILE_NAME + ".broken");
            Files.copy(this.file, backup, StandardCopyOption.REPLACE_EXISTING);
            AuroraLog.warn("сделана копия повреждённого файла настроек: " + backup);
        } catch (IOException exception) {
            AuroraLog.error("не удалось сделать копию файла настроек", exception);
        }
    }
}
