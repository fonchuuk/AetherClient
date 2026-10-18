package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.hand.HandAnimation;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.hand.HandStyle;
import net.minecraft.client.Minecraft;

/**
 * Эффекты руки (5.3).
 *
 * <p>Своя анимация появления и исчезновения, размах, удержание, покачивание,
 * цветная подсветка и «отсвет» вокруг руки. Полное выключение возвращает ванильный вид:
 * рука рисуется игрой, как обычно.</p>
 */
public final class HandEffectsModule extends Module {
    private final EnumSetting<HandStyle> style = register(new EnumSetting<>("style", "Стиль",
            "Чем подсвечивается рука", HandStyle.GLOW, HandStyle.values(), setting -> apply()));
    private final EnumSetting<HandAnimation> animation = register(new EnumSetting<>("animation", "Движение",
            "Своя анимация руки в первом лице", HandAnimation.SWING, HandAnimation.values(),
            setting -> apply()));
    private final ColorSetting color = register(new ColorSetting("color", "Цвет",
            "Цвет подсветки и «отсвета»", 0xFF7FD4FF, setting -> apply()));
    private final FloatSetting intensity = register(new FloatSetting("intensity", "Интенсивность",
            "Насколько заметен эффект", 0.6F, 0.0F, 1.0F, 0.05F, setting -> apply()));
    private final FloatSetting appearSpeed = register(new FloatSetting("appearSpeed", "Скорость появления",
            "Как быстро рука возвращается после смены предмета", 6.0F, 0.5F, 20.0F, 0.5F, setting -> apply()));
    private final FloatSetting swingStrength = register(new FloatSetting("swing", "Сила размаха",
            "Насколько сильно рука уходит при ударе", 0.5F, 0.0F, 1.5F, 0.05F, setting -> apply()));

    public HandEffectsModule() {
        super("hands", "Эффекты руки", "Своя анимация, цвет и подсветка руки в первом лице",
                ModuleCategory.VISUAL);
    }

    private void apply() {
        HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.color.get(),
                this.intensity.get(), this.appearSpeed.get(), this.swingStrength.get());
        settingChanged(this.style);
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        HandEffectRenderer.reset();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        if (isEnabled()) {
            HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.color.get(),
                    this.intensity.get(), this.appearSpeed.get(), this.swingStrength.get());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        HandEffectRenderer.update(deltaSeconds, Minecraft.getInstance().player);
    }

    /** Строка состояния для списка модулей. */
    public String activity() {
        return HandEffectRenderer.debugState();
    }
}
