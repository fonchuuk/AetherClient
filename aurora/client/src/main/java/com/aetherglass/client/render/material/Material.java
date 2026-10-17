package com.aetherglass.client.render.material;

import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Материал: полный набор параметров подсветки и заливки блока.
 *
 * <p>Материал — это сохраняемый набор: режим заливки, источник цвета, сам
 * цвет, плотность, толщина контура, свечение, скорость блика и кромка. Его
 * можно выбрать, изменить, сохранить под своим именем и применить к модулю
 * блоков. Стандартные материалы изменить нельзя — только скопировать.</p>
 *
 * <p>Настройки для экрана создаются лениво и привязаны к полям материала:
 * изменение настройки сразу меняет поле, а чтение материала из файла
 * обновляет и сами настройки ({@link #sync()}).</p>
 */
public final class Material {

    /** Ключ имени в хранилище. */
    public static final String NAME = "name";

    private final String id;
    private final boolean builtIn;

    private String name;
    private FillMode mode;
    private ColorSource colorSource;
    private int color;
    private float fillAlpha;
    private float outlineWidth;
    private float outlineAlpha;
    private float glow;
    private float gradientSpeed;
    private boolean rim;

    private List<Setting<?>> settings;

    public Material(String id, String name, boolean builtIn) {
        this.id = id;
        this.builtIn = builtIn;
        this.name = name;
        this.mode = FillMode.SHADER_FILL;
        this.colorSource = ColorSource.CUSTOM;
        this.color = 0xFF8FE3FF;
        this.fillAlpha = 0.32F;
        this.outlineWidth = 0.02F;
        this.outlineAlpha = 0.85F;
        this.glow = 0.35F;
        this.gradientSpeed = 0.6F;
        this.rim = true;
    }

    public Material(String id, String name, boolean builtIn, FillMode mode, ColorSource colorSource, int color,
                    float fillAlpha, float outlineWidth, float outlineAlpha, float glow, float gradientSpeed,
                    boolean rim) {
        this.id = id;
        this.builtIn = builtIn;
        this.name = name;
        this.mode = mode;
        this.colorSource = colorSource;
        this.color = color;
        this.fillAlpha = fillAlpha;
        this.outlineWidth = outlineWidth;
        this.outlineAlpha = outlineAlpha;
        this.glow = glow;
        this.gradientSpeed = gradientSpeed;
        this.rim = rim;
    }

    // ─────────────────────────── свойства ───────────────────────────

    public String id() {
        return this.id;
    }

    public boolean builtIn() {
        return this.builtIn;
    }

    public String name() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? this.name : name;
    }

    public FillMode mode() {
        return this.mode;
    }

    public ColorSource colorSource() {
        return this.colorSource;
    }

    public int color() {
        return this.color;
    }

    public float fillAlpha() {
        return this.fillAlpha;
    }

    public float outlineWidth() {
        return this.outlineWidth;
    }

    public float outlineAlpha() {
        return this.outlineAlpha;
    }

    public float glow() {
        return this.glow;
    }

    public float gradientSpeed() {
        return this.gradientSpeed;
    }

    public boolean rim() {
        return this.rim;
    }

    /** Копия с другими именем и номером: основа пользовательского материала. */
    public Material copy(String newId, String newName) {
        return new Material(newId, newName, false, this.mode, this.colorSource, this.color, this.fillAlpha,
                this.outlineWidth, this.outlineAlpha, this.glow, this.gradientSpeed, this.rim);
    }

    // ─────────────────────────── настройки ───────────────────────────

    /**
     * Настройки материала для экрана. Создаются один раз; каждое изменение
     * сразу записывается в поля материала.
     */
    public List<Setting<?>> settings() {
        if (this.settings == null) {
            List<Setting<?>> list = new ArrayList<>(9);
            list.add(new EnumSetting<>("mode", "Режим", "Чем закрашивается блок под прицелом",
                    this.mode, FillMode.values(), setting -> this.mode = setting.get()));
            list.add(new EnumSetting<>("colorSource", "Источник цвета", "Откуда берётся цвет",
                    this.colorSource, ColorSource.values(),
                    setting -> this.colorSource = setting.get()));
            list.add(new ColorSetting("color", "Свой цвет", "Цвет для режима «свой цвет»",
                    this.color, setting -> this.color = setting.get()));
            list.add(new FloatSetting("fillAlpha", "Плотность заливки", "Насколько плотно закрашен блок",
                    this.fillAlpha, 0.0F, 1.0F, 0.05F, setting -> this.fillAlpha = setting.get()));
            list.add(new FloatSetting("outlineWidth", "Толщина контура", "Толщина линий по рёбрам блока",
                    this.outlineWidth, 0.004F, 0.08F, 0.002F, setting -> this.outlineWidth = setting.get()));
            list.add(new FloatSetting("outlineAlpha", "Яркость контура", "Насколько яркие линии",
                    this.outlineAlpha, 0.0F, 1.0F, 0.05F, setting -> this.outlineAlpha = setting.get()));
            list.add(new FloatSetting("glow", "Свечение", "Мягкое свечение по граням блока",
                    this.glow, 0.0F, 1.0F, 0.05F, setting -> this.glow = setting.get()));
            list.add(new FloatSetting("gradientSpeed", "Скорость блика", "Как быстро бежит блик по градиенту",
                    this.gradientSpeed, 0.0F, 2.0F, 0.05F, setting -> this.gradientSpeed = setting.get()));
            list.add(new BooleanSetting("rim", "Блик по кромке", "Светлая линия по верхним рёбрам",
                    this.rim, setting -> this.rim = setting.get()));
            this.settings = List.copyOf(list);
        }
        return this.settings;
    }

    /** Перекладывает значения полей в настройки: вызывается после чтения из файла. */
    public void sync() {
        if (this.settings == null) {
            return;
        }
        set("mode", this.mode);
        set("colorSource", this.colorSource);
        set("color", this.color);
        set("fillAlpha", this.fillAlpha);
        set("outlineWidth", this.outlineWidth);
        set("outlineAlpha", this.outlineAlpha);
        set("glow", this.glow);
        set("gradientSpeed", this.gradientSpeed);
        set("rim", this.rim);
    }

    @SuppressWarnings("unchecked")
    private void set(String settingId, Object value) {
        for (Setting<?> setting : this.settings) {
            if (setting.id().equals(settingId)) {
                ((Setting<Object>) setting).set(value);
                return;
            }
        }
    }

    // ─────────────────────────── хранение ───────────────────────────

    /** Ключ свойства материала в хранилище настроек. */
    public String key(String property) {
        return "material." + this.id + "." + property;
    }

    /** Пишет материал записями «ключ — значение». */
    public void write(java.util.Map<String, String> out) {
        out.put(key(NAME), this.name);
        out.put(key("mode"), this.mode.name());
        out.put(key("colorSource"), this.colorSource.name());
        out.put(key("color"), Integer.toString(this.color));
        out.put(key("fillAlpha"), Float.toString(this.fillAlpha));
        out.put(key("outlineWidth"), Float.toString(this.outlineWidth));
        out.put(key("outlineAlpha"), Float.toString(this.outlineAlpha));
        out.put(key("glow"), Float.toString(this.glow));
        out.put(key("gradientSpeed"), Float.toString(this.gradientSpeed));
        out.put(key("rim"), Boolean.toString(this.rim));
    }

    /**
     * Читает материал из хранилища. Если значение отсутствует или испорчено,
     * подставляется разумное значение по умолчанию: испорченная запись не
     * должна ломать клиент.
     */
    public void read(Function<String, String> storage) {
        String storedName = storage.apply(key(NAME));
        if (storedName != null && !storedName.isBlank()) {
            this.name = storedName;
        }
        this.mode = readEnum(storage.apply(key("mode")), FillMode.class, this.mode);
        this.colorSource = readEnum(storage.apply(key("colorSource")), ColorSource.class, this.colorSource);
        this.color = readInt(storage.apply(key("color")), this.color, 0, 0xFFFFFF);
        this.fillAlpha = readFloat(storage.apply(key("fillAlpha")), this.fillAlpha, 0.0F, 1.0F);
        this.outlineWidth = readFloat(storage.apply(key("outlineWidth")), this.outlineWidth, 0.004F, 0.08F);
        this.outlineAlpha = readFloat(storage.apply(key("outlineAlpha")), this.outlineAlpha, 0.0F, 1.0F);
        this.glow = readFloat(storage.apply(key("glow")), this.glow, 0.0F, 1.0F);
        this.gradientSpeed = readFloat(storage.apply(key("gradientSpeed")), this.gradientSpeed, 0.0F, 2.0F);
        if (storage.apply(key("rim")) != null) {
            this.rim = Boolean.parseBoolean(storage.apply(key("rim")));
        }
        sync();
    }

    private static <E extends Enum<E>> E readEnum(String raw, Class<E> type, E fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static int readInt(String raw, int fallback, int min, int max) {
        if (raw == null) {
            return fallback;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw.trim())));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float readFloat(String raw, float fallback, float min, float max) {
        if (raw == null) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(raw.trim());
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return this.name;
    }
}
