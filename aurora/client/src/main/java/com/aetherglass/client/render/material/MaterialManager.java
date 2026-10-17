package com.aetherglass.client.render.material;

import com.aetherglass.client.config.ModConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Менеджер материалов: создание, изменение, сохранение и загрузка.
 *
 * <p>Хранилище передаётся снаружи ({@link Storage}), поэтому менеджер можно
 * проверять тестами без файла настроек и без запуска игры. По умолчанию
 * используется {@link ModConfigStorage} — тот же файл настроек, что и у
 * остальных модулей.</p>
 *
 * <p>Пять стандартных материалов идут с клиентом: их нельзя испортить, но
 * можно скопировать и изменить копию. Пользовательские материалы хранятся в
 * файле настроек и читаются при следующем запуске.</p>
 */
public final class MaterialManager {

    /** Ключ со списком идентификаторов материалов. */
    public static final String LIST_KEY = "material.list";

    /** Хранилище «ключ — значение»: файл настроек или его заменитель для тестов. */
    public interface Storage {
        String get(String key, String fallback);

        void set(String key, String value);

        void save();
    }

    private static final MaterialManager INSTANCE = new MaterialManager(new ModConfigStorage());

    private final Storage storage;
    private final List<Material> materials = new ArrayList<>();

    private int userCounter = 1;

    private MaterialManager(Storage storage) {
        this.storage = storage;
        load();
    }

    public static MaterialManager get() {
        return INSTANCE;
    }

    /** Новый менеджер на собственном хранилище: используется тестами. */
    public static MaterialManager testing(Storage storage) {
        return new MaterialManager(storage);
    }

    // ─────────────────────────── список ───────────────────────────

    /** Все материалы: сначала стандартные, затем пользовательские. */
    public List<Material> materials() {
        return Collections.unmodifiableList(this.materials);
    }

    /** Материал по идентификатору или {@code null}. */
    public Material byId(String id) {
        if (id == null) {
            return null;
        }
        for (Material material : this.materials) {
            if (material.id().equals(id)) {
                return material;
            }
        }
        return null;
    }

    public int count() {
        return this.materials.size();
    }

    public int userCount() {
        int count = 0;
        for (Material material : this.materials) {
            if (!material.builtIn()) {
                count++;
            }
        }
        return count;
    }

    // ─────────────────────────── изменения ───────────────────────────

    /**
     * Создаёт пользовательский материал как копию выбранного.
     *
     * @param template образец: стандартный или уже существующий
     * @param name     имя нового материала
     */
    public Material create(Material template, String name) {
        String id = "user" + this.userCounter;
        while (byId(id) != null) {
            this.userCounter++;
            id = "user" + this.userCounter;
        }
        this.userCounter++;
        Material copy = template.copy(id, name);
        this.materials.add(copy);
        save();
        return copy;
    }

    /** Переименовывает пользовательский материал. Стандартные трогать нельзя. */
    public boolean rename(String id, String name) {
        Material material = byId(id);
        if (material == null || material.builtIn() || name == null || name.isBlank()) {
            return false;
        }
        material.setName(name.trim());
        save();
        return true;
    }

    /** Удаляет пользовательский материал. Стандартные удалить нельзя. */
    public boolean remove(String id) {
        Material material = byId(id);
        if (material == null || material.builtIn()) {
            return false;
        }
        this.materials.remove(material);
        save();
        return true;
    }

    /** Удаляет все пользовательские материалы и сбрасывает стандартные к исходным. */
    public void reset() {
        this.materials.clear();
        for (Material material : defaults()) {
            this.materials.add(material);
        }
        save();
    }

    /** Сохраняет все материалы в хранилище. */
    public void save() {
        StringBuilder list = new StringBuilder();
        Map<String, String> values = new LinkedHashMap<>();
        for (Material material : this.materials) {
            if (list.length() > 0) {
                list.append(';');
            }
            list.append(material.id());
            material.write(values);
        }
        this.storage.set(LIST_KEY, list.toString());
        values.forEach(this.storage::set);
        this.storage.save();
    }

    /** Строка для экрана: сколько материалов и сколько из них своих. */
    public String status() {
        return count() + " материалов (" + userCount() + " своих)";
    }

    // ─────────────────────────── чтение ───────────────────────────

    private void load() {
        List<Material> base = defaults();
        String stored = this.storage.get(LIST_KEY, null);
        if (stored != null && !stored.isBlank()) {
            for (String id : stored.split(";")) {
                String clean = id.trim();
                if (clean.isEmpty()) {
                    continue;
                }
                Material existing = find(base, clean);
                if (existing != null) {
                    // Стандартный материал читается поверх исходного: игрок мог его подправить.
                    existing.read(key -> this.storage.get(key, null));
                    this.materials.add(existing);
                    base.remove(existing);
                    continue;
                }
                if (this.materials.stream().noneMatch(material -> material.id().equals(clean))) {
                    Material user = new Material(clean, "Материал", false);
                    user.read(key -> this.storage.get(key, null));
                    this.materials.add(user);
                }
            }
            for (Material material : this.materials) {
                if (!material.builtIn()) {
                    this.userCounter++;
                }
            }
        }
        for (Material material : base) {
            this.materials.add(material);
        }
    }

    private static Material find(List<Material> list, String id) {
        for (Material material : list) {
            if (material.id().equals(id)) {
                return material;
            }
        }
        return null;
    }

    /** Пять стандартных материалов, с которыми приходит клиент. */
    public static List<Material> defaults() {
        List<Material> list = new ArrayList<>(5);
        list.add(new Material("builtin.outline", "Контур", true,
                FillMode.OUTLINE, ColorSource.ACCENT, 0xFF8FE3FF,
                0.12F, 0.022F, 0.95F, 0.15F, 0.0F, true));
        list.add(new Material("builtin.glass", "Стекло", true,
                FillMode.FILL, ColorSource.CUSTOM, 0xFF8FE3FF,
                0.22F, 0.018F, 0.8F, 0.25F, 0.0F, true));
        list.add(new Material("builtin.liquid", "Жидкое стекло", true,
                FillMode.SHADER_FILL, ColorSource.CUSTOM, 0xFF9F7BFF,
                0.34F, 0.02F, 0.9F, 0.4F, 0.55F, true));
        list.add(new Material("builtin.rainbow", "Радуга", true,
                FillMode.SHADER_FILL, ColorSource.RAINBOW, 0xFFFFFFFF,
                0.38F, 0.022F, 0.95F, 0.5F, 0.8F, true));
        list.add(new Material("builtin.neon", "Неон", true,
                FillMode.FILL, ColorSource.RAINBOW, 0xFF66FFCC,
                0.5F, 0.026F, 1.0F, 0.9F, 0.3F, true));
        return list;
    }

    /**
     * Хранилище поверх файла настроек клиента.
     *
     * <p>Удаление реализуется записью пустого значения: при чтении пустое
     * значение считается отсутствующим, поэтому старые записи не мешают.</p>
     */
    private static final class ModConfigStorage implements Storage {
        @Override
        public String get(String key, String fallback) {
            return ModConfig.get().getString(key, fallback);
        }

        @Override
        public void set(String key, String value) {
            ModConfig.get().set(key, value);
        }

        @Override
        public void save() {
            ModConfig.get().saveIfNeeded();
        }
    }

    /** Для тестов: хранилище в памяти. */
    public static Storage memory(Map<String, String> map) {
        return new Storage() {
            @Override
            public String get(String key, String fallback) {
                String value = map.get(key);
                return value == null || value.isEmpty() ? fallback : value;
            }

            @Override
            public void set(String key, String value) {
                map.put(key, value == null ? "" : value);
            }

            @Override
            public void save() {
                // В памяти сохранять некуда:Map уже содержит актуальные значения.
            }
        };
    }

    /** Функция чтения для {@link Material#read(Function)} — используется тестами. */
    public Function<String, String> reader() {
        return key -> this.storage.get(key, null);
    }
}
