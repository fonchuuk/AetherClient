package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.hand.HandAnimation;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.render.hand.HandStyle;
import com.aetherglass.client.render.shader.FaceGradientMode;
import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.client.Minecraft;

/**
 * Эффекты руки, ViewModel и анимации рук ({@code HandsShader} + {@code ViewModel}, Блок 1.3 и Блок 4.1–4.3):
 * <ul>
 *   <li>16 процедурных шейдер-эффектов и светящийся контур поверх 100% видимой текстуры предмета;</li>
 *   <li>Подраздел ViewModel: позиция X/Y/Z (-1.0..1.0), масштаб (0.5..1.5), поворот X/Y/Z и галочка
 *       «Не загораживать экран» при еде и питье;</li>
 *   <li>Плавные анимации с Easing: Swing, Equip, Walking Sway, Idle и Eating/Drinking.</li>
 * </ul>
 */
public final class HandEffectsModule extends Module {
    // ── HandsShader ──────────────────────────────────────────────────────────────
    private final BooleanSetting useShaderEffect = register(new BooleanSetting("useShader",
            "Шейдер поверх предмета (HandsShader)",
            "Накладывает выбранный шейдер-эффект поверх 100% видимой текстуры предмета и руки",
            true, setting -> apply()));
    private final EnumSetting<ShaderEffect> shaderEffect = register(new EnumSetting<>("shaderEffect",
            "Эффект HandsShader",
            "Выбор одного из 16 уникальных шейдер-эффектов для рук и предметов",
            ShaderEffect.LIQUID_GLASS, ShaderEffect.values(), setting -> apply()));
    private final FloatSetting shaderOpacity = register(new FloatSetting("shaderOpacity",
            "Плотность шейдера поверх",
            "Непрозрачность шейдерной глазури поверх оригинальной текстуры предмета",
            0.42F, 0.05F, 0.85F, 0.02F, setting -> apply()));
    private final FloatSetting outlineWidth = register(new FloatSetting("outlineWidth",
            "Толщина обводки предмета",
            "Толщина светящегося силуэтного контура вокруг предмета и руки",
            0.012F, 0.002F, 0.045F, 0.002F, setting -> apply()));
    private final FloatSetting shaderIntensity = register(new FloatSetting("shaderIntensity",
            "Сила свечения и блика",
            "Яркость и выраженность шейдер-эффекта поверх предмета",
            1.00F, 0.10F, 2.00F, 0.05F, setting -> apply()));
    private final FloatSetting shaderSpeed = register(new FloatSetting("shaderSpeed",
            "Скорость перелива",
            "Скорость течения волн и анимации шейдера рук",
            1.00F, 0.10F, 3.00F, 0.05F, setting -> apply()));
    private final EnumSetting<FaceGradientMode> faceGradientMode = register(new EnumSetting<>("faceGradientMode",
            "Режим блика (Капля / Блик)",
            "Как накладывается стеклянный блик на предмет и руку",
            FaceGradientMode.MENISCUS_DROP, FaceGradientMode.values(), setting -> apply()));
    private final ColorSetting shaderColor = register(new ColorSetting("shaderColor",
            "Цвет шейдера и контура",
            "Основной цвет выбранного шейдер-эффекта рук и предмета",
            ShaderEffect.LIQUID_GLASS.defaultColor(), setting -> apply()));

    // ── Блок 4.1: ViewModel (Позиция, масштаб, поворот, NoClip при еде) ──────────
    private final FloatSetting viewOffsetX = register(new FloatSetting("viewOffsetX",
            "ViewModel: Позиция X",
            "Смещение руки влево/вправо (-1.0 .. 1.0)",
            0.00F, -1.00F, 1.00F, 0.02F, setting -> apply()));
    private final FloatSetting viewOffsetY = register(new FloatSetting("viewOffsetY",
            "ViewModel: Позиция Y",
            "Смещение руки вверх/вниз (-1.0 .. 1.0)",
            0.00F, -1.00F, 1.00F, 0.02F, setting -> apply()));
    private final FloatSetting viewOffsetZ = register(new FloatSetting("viewOffsetZ",
            "ViewModel: Позиция Z",
            "Смещение руки ближе/дальше (-1.0 .. 1.0)",
            0.00F, -1.00F, 1.00F, 0.02F, setting -> apply()));
    private final FloatSetting viewScale = register(new FloatSetting("viewScale",
            "ViewModel: Масштаб руки",
            "Размер предмета и руки от первого лица (0.5 .. 1.5)",
            1.00F, 0.50F, 1.50F, 0.05F, setting -> apply()));
    private final FloatSetting viewRotX = register(new FloatSetting("viewRotX",
            "ViewModel: Поворот X (°)",
            "Угол наклона руки по оси X",
            0.0F, -180.0F, 180.0F, 2.0F, setting -> apply()));
    private final FloatSetting viewRotY = register(new FloatSetting("viewRotY",
            "ViewModel: Поворот Y (°)",
            "Угол поворота руки по оси Y",
            0.0F, -180.0F, 180.0F, 2.0F, setting -> apply()));
    private final FloatSetting viewRotZ = register(new FloatSetting("viewRotZ",
            "ViewModel: Поворот Z (°)",
            "Угол крена руки по оси Z",
            0.0F, -180.0F, 180.0F, 2.0F, setting -> apply()));
    private final BooleanSetting noClipEating = register(new BooleanSetting("noClipEating",
            "Не загораживать экран (NoClip Еда)",
            "Опускает и отодвигает предмет при еде или питье зелья, чтобы не перекрывать обзор",
            true, setting -> apply()));

    // ── Блок 4.2: Анимации рук (Hand Animations) ─────────────────────────────────
    private final BooleanSetting swingAnim = register(new BooleanSetting("swingAnim",
            "Swing Animation (Удар)",
            "Резкий и красивый взмах с Easing при ударе",
            true, setting -> apply()));
    private final BooleanSetting equipAnim = register(new BooleanSetting("equipAnim",
            "Equip Animation (Вынимание)",
            "Плавный выезд предмета снизу и сбоку при переключении слота хотбара",
            true, setting -> apply()));
    private final BooleanSetting walkingSway = register(new BooleanSetting("walkingSway",
            "Walking Sway (При ходьбе)",
            "Мягкое инерционное покачивание руки в такт шагам как в шутерах",
            true, setting -> apply()));
    private final BooleanSetting idleAnim = register(new BooleanSetting("idleAnim",
            "Idle Animation (Дыхание)",
            "Лёгкое дыхание руки, когда персонаж стоит на месте",
            true, setting -> apply()));
    private final BooleanSetting eatingAnim = register(new BooleanSetting("eatingAnim",
            "Eating/Drinking Animation",
            "Плавное движение руки при еде и питье зелий",
            true, setting -> apply()));
    private final FloatSetting appearSpeed = register(new FloatSetting("appearSpeed",
            "Скорость Equip-анимации",
            "Как быстро рука выезжает после смены слота",
            6.5F, 1.0F, 20.0F, 0.5F, setting -> apply()));
    private final FloatSetting swingStrength = register(new FloatSetting("swing",
            "Амплитуда анимаций",
            "Интенсивность взмаха и покачивания руки",
            0.65F, 0.0F, 1.5F, 0.05F, setting -> apply()));

    // Совместимые настройки для внешних вызовов и тестов
    private final EnumSetting<HandAnimation> animation = new EnumSetting<>("animation",
            "Анимация взмаха руки", "Плавная анимация руки в первом лице",
            HandAnimation.SWING, HandAnimation.values(), setting -> apply());
    private final FloatSetting modelAlpha = new FloatSetting("modelAlpha",
            "Видимость предмета", "Оригинальный предмет всегда 100% видим",
            1.00F, 0.15F, 1.00F, 0.05F, setting -> apply());
    private final EnumSetting<HandStyle> style = new EnumSetting<>("style", "Стиль",
            "Стиль подсветки", HandStyle.GLOW, HandStyle.values(), setting -> apply());

    public HandEffectsModule() {
        super("hands", "Эффекты руки",
                "HandsShader + ViewModel: шейдер поверх предмета, позиция X/Y/Z и 5 анимаций",
                ModuleCategory.VISUAL);
    }

    private void apply() {
        HandEffectRenderer.configure(this.style.get(), this.animation.get(), this.shaderColor.get(),
                Math.min(1.0F, this.shaderOpacity.get()), this.appearSpeed.get(), this.swingStrength.get());
        HandEffectRenderer.configureShader(this.useShaderEffect.isEnabled(), this.shaderEffect.get(),
                this.faceGradientMode.get(), this.shaderColor.get(), this.shaderIntensity.get(),
                this.shaderSpeed.get(), this.shaderOpacity.get(), this.outlineWidth.get(), 1.00F);
        HandEffectRenderer.configureViewModel(
                this.viewOffsetX.get(),
                this.viewOffsetY.get(),
                this.viewOffsetZ.get(),
                this.viewScale.get(),
                this.viewRotX.get(),
                this.viewRotY.get(),
                this.viewRotZ.get(),
                this.noClipEating.isEnabled(),
                this.swingAnim.isEnabled(),
                this.equipAnim.isEnabled(),
                this.walkingSway.isEnabled(),
                this.idleAnim.isEnabled(),
                this.eatingAnim.isEnabled()
        );
        settingChanged(this.shaderEffect);
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
            apply();
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        HandEffectRenderer.update(deltaSeconds, Minecraft.getInstance().player);
    }

    public String activity() {
        if (this.useShaderEffect.isEnabled()) {
            return this.shaderEffect.get().displayName() + " · " + HandEffectRenderer.debugState();
        }
        return HandEffectRenderer.debugState();
    }

    public boolean useShaderEffect() {
        return this.useShaderEffect.isEnabled();
    }

    public void setUseShaderEffect(boolean enabled) {
        this.useShaderEffect.set(enabled);
        apply();
    }

    public ShaderEffect shaderEffect() {
        return this.shaderEffect.get();
    }

    public void setShaderEffect(ShaderEffect effect) {
        if (effect != null) {
            this.shaderEffect.set(effect);
            apply();
        }
    }

    public FaceGradientMode faceGradientMode() {
        return this.faceGradientMode.get();
    }

    public EnumSetting<FaceGradientMode> faceGradientModeSetting() {
        return this.faceGradientMode;
    }

    public float outlineWidth() {
        return this.outlineWidth.get();
    }

    public FloatSetting outlineWidthSetting() {
        return this.outlineWidth;
    }

    public float modelAlpha() {
        return this.modelAlpha.get();
    }

    public FloatSetting modelAlphaSetting() {
        return this.modelAlpha;
    }

    public float viewOffsetX() {
        return this.viewOffsetX.get();
    }

    public float viewOffsetY() {
        return this.viewOffsetY.get();
    }

    public float viewOffsetZ() {
        return this.viewOffsetZ.get();
    }

    public float viewScale() {
        return this.viewScale.get();
    }

    public boolean noClipEating() {
        return this.noClipEating.isEnabled();
    }

    public void selectShaderPreset(ShaderEffect effect) {
        if (effect == null) {
            return;
        }
        this.shaderEffect.set(effect);
        this.shaderColor.set(effect.defaultColor());
        this.shaderIntensity.set(effect.defaultIntensity());
        this.shaderSpeed.set(effect.defaultSpeed());
        this.shaderOpacity.set(Math.min(0.55F, effect.defaultOpacity()));
        apply();
    }

    public void resetCurrentShaderEffect() {
        selectShaderPreset(this.shaderEffect.get());
    }

    public void resetShaderDefaults() {
        this.useShaderEffect.reset();
        selectShaderPreset(ShaderEffect.LIQUID_GLASS);
        this.faceGradientMode.reset();
        this.outlineWidth.reset();
        this.viewOffsetX.reset();
        this.viewOffsetY.reset();
        this.viewOffsetZ.reset();
        this.viewScale.reset();
        this.viewRotX.reset();
        this.viewRotY.reset();
        this.viewRotZ.reset();
        this.noClipEating.reset();
        this.swingAnim.reset();
        this.equipAnim.reset();
        this.walkingSway.reset();
        this.idleAnim.reset();
        this.eatingAnim.reset();
        this.appearSpeed.reset();
        this.swingStrength.reset();
        apply();
    }

    public int shaderColor() {
        return ColorController.resolve(this.shaderColor.get());
    }

    public void setShaderColor(int colorValue) {
        this.shaderColor.set(colorValue);
        apply();
    }

    public ColorSetting shaderColorSetting() {
        return this.shaderColor;
    }

    public float shaderIntensity() {
        return this.shaderIntensity.get();
    }

    public void setShaderIntensity(float value) {
        this.shaderIntensity.set(value);
        apply();
    }

    public FloatSetting shaderIntensitySetting() {
        return this.shaderIntensity;
    }

    public float shaderSpeed() {
        return this.shaderSpeed.get();
    }

    public void setShaderSpeed(float value) {
        this.shaderSpeed.set(value);
        apply();
    }

    public FloatSetting shaderSpeedSetting() {
        return this.shaderSpeed;
    }

    public float shaderOpacity() {
        return this.shaderOpacity.get();
    }

    public void setShaderOpacity(float value) {
        this.shaderOpacity.set(value);
        apply();
    }

    public FloatSetting shaderOpacitySetting() {
        return this.shaderOpacity;
    }
}
