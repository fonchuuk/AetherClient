package com.aetherglass.client.modules.impl;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;

/**
 * Оформление: палитра и «физика» интерфейса.
 *
 * <p>Здесь лежат плотность стекла, радиус скругления и скорость анимаций — те самые
 * параметры, из-за которых интерфейс можно перестроить целиком, не переписывая код
 * отрисовки. Меняются они сразу, без перезапуска.</p>
 */
public final class InterfaceModule extends Module {
    /** Готовый набор оформления. */
    public enum Preset {
        DEFAULT("Обычный"),
        GLASS("Стекло"),
        NEON("Неон"),
        CONTRAST("Контраст");

        private final String title;

        Preset(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    private final ColorSetting borderColor = register(new ColorSetting("borderColor", "Цвет рамки",
            "Цвет тонкой неоновой рамки столбиков, карточек и меню", 0xFFAA3DFF, setting -> apply()));
    private final FloatSetting borderWidth = register(new FloatSetting("borderWidth", "Жирность рамки",
            "Толщина рамки прямоугольников (от 0.5px до 3.0px)", 1.0F, 0.5F, 3.0F, 0.25F, setting -> apply()));
    private final FloatSetting borderGlow = register(new FloatSetting("borderGlow", "Свечение рамки",
            "Сила мягкого свечения вокруг тонкой рамки", 0.25F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final EnumSetting<Preset> preset = register(new EnumSetting<>("preset", "Оформление",
            "Готовый набор палитры, плотности и радиуса", Preset.DEFAULT, Preset.values(),
            setting -> applyPreset()));
    private final EnumSetting<AuroraTheme.Palette> palette = register(new EnumSetting<>("palette", "Палитра",
            "Тёмная, светлая или неоновая", AuroraTheme.Palette.DARK, AuroraTheme.Palette.values(),
            setting -> apply()));
    private final FloatSetting accentHue = register(new FloatSetting("hue", "Оттенок стекла (Hue)",
            "Цвет подсветки и тонировки стекла интерфейса", 0.76F, 0.0F, 1.0F, 0.01F, setting -> {
                if (this.borderColor != null) {
                    this.borderColor.setRgb(com.aetherglass.client.render.util.ColorUtil.hsb(setting.get(), 0.76F, 1.0F));
                }
                apply();
            }));
    private final FloatSetting blur = register(new FloatSetting("blur", "Сила размытия (Blur)",
            "Интенсивность матового размытия стеклянных подложек меню и HUD", 0.72F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final FloatSetting density = register(new FloatSetting("density", "Плотность стекла",
            "Насколько плотные панели", 0.55F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final IntSetting radius = register(new IntSetting("radius", "Скругление углов (Radius)",
            "Радиус углов панелей, карточек и кнопок", 10, 0, 20, setting -> apply()));
    private final FloatSetting motion = register(new FloatSetting("motion", "Скорость анимаций",
            "Множитель скорости всех движений", 1.0F, 0.25F, 2.5F, 0.05F, setting -> apply()));
    private final FloatSetting bounce = register(new FloatSetting("bounce", "Пружинистость",
            "Насколько упруго элементы пролетают цель: 0 — спокойно, 1 — с перелётом",
            0.6F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final BooleanSetting customMenu = register(new BooleanSetting("customMenu", "Свой главный экран",
            "Заменять ванильное главное меню нашим", true, setting -> settingChanged(setting)));
    private final BooleanSetting animatedBackground = register(new BooleanSetting("animated", "Анимированный фон",
            "Движущийся градиент и сияние за панелями", true, setting -> settingChanged(setting)));
    private final BooleanSetting hudBackground = register(new BooleanSetting("hudGlass", "Стеклянные панели экрана",
            "Плашки элементов экрана в виде стекла", true, setting -> settingChanged(setting)));

    public InterfaceModule() {
        super("interface", "Оформление", "Палитра, плотность стекла, радиус и скорость анимаций",
                ModuleCategory.INTERFACE);
    }

    private void applyPreset() {
        switch (this.preset.get()) {
            case GLASS -> {
                this.palette.set(AuroraTheme.Palette.DARK);
                this.density.set(0.35F);
                this.radius.set(14);
            }
            case NEON -> {
                this.palette.set(AuroraTheme.Palette.NEON);
                this.density.set(0.70F);
                this.radius.set(12);
            }
            case CONTRAST -> {
                this.palette.set(AuroraTheme.Palette.LIGHT);
                this.density.set(0.90F);
                this.radius.set(6);
            }
            default -> {
                this.palette.set(AuroraTheme.Palette.DARK);
                this.density.set(0.55F);
                this.radius.set(10);
            }
        }
        apply();
        settingChanged(this.preset);
    }

    private void apply() {
        AuroraTheme.palette(this.palette.get());
        AuroraTheme.accentHue(this.accentHue.get());
        if (this.borderColor != null) {
            AuroraTheme.borderColor(this.borderColor.rgb());
        }
        if (this.borderWidth != null) {
            AuroraTheme.borderWidth(this.borderWidth.get());
        }
        if (this.borderGlow != null) {
            AuroraTheme.borderGlow(this.borderGlow.get());
        }
        if (this.blur != null) {
            AuroraTheme.blur(this.blur.get());
        }
        AuroraTheme.density(this.density.get());
        AuroraTheme.radius(this.radius.get());
        AuroraTheme.motion(this.motion.get());
        AuroraTheme.bounce(this.bounce.get());
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    public boolean customMenuEnabled() {
        return this.customMenu.isEnabled();
    }

    public boolean animatedBackground() {
        return this.animatedBackground.isEnabled();
    }

    public boolean hudGlass() {
        return this.hudBackground.isEnabled();
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        return this.palette.get().title().toLowerCase() + " · " + this.preset.get().title().toLowerCase();
    }
}
