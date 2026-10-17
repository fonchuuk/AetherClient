package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.particle.ParticlePreset;
import com.aetherglass.client.render.particle.ParticleShape;
import com.aetherglass.client.render.particle.ParticleSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Random;

/**
 * Частицы (5.1): собственная система частиц, которая рисуется прямо в мире.
 *
 * <p>Частицы появляются у игрока, разлетаются, меняют цвет от начального к конечному,
 * плавно растворяются и исчезают. Число частиц ограничено, поэтому система не «съедает»
 * кадр даже на слабом компьютере.</p>
 */
public final class ParticleModule extends Module {
    private static final Random RANDOM = new Random();

    private final EnumSetting<ParticleShape> shape = register(new EnumSetting<>("shape", "Форма", "Спрайт частицы",
            ParticleShape.CIRCLE, ParticleShape.values(), setting -> settingChanged(setting)));
    private final EnumSetting<ParticlePreset> preset = register(new EnumSetting<>("preset", "Пресет",
            "Готовый набор значений; после выбора можно крутить ползунки дальше",
            ParticlePreset.DEFAULT, ParticlePreset.values(), setting -> applyPreset()));
    private final ColorSetting colorFrom = register(new ColorSetting("colorA", "Начальный цвет",
            "Цвет в момент появления", 0xFF7FD4FF, setting -> settingChanged(setting)));
    private final ColorSetting colorTo = register(new ColorSetting("colorB", "Конечный цвет",
            "Цвет перед исчезновением: переход между цветами плавный", 0xFFB98CFF,
            setting -> settingChanged(setting)));
    private final FloatSetting size = register(new FloatSetting("size", "Размер", "Половина стороны спрайта",
            0.16F, 0.02F, 0.8F, 0.01F, setting -> settingChanged(setting)));
    private final FloatSetting alpha = register(new FloatSetting("alpha", "Прозрачность", "Насколько плотная частица",
            0.85F, 0.05F, 1.0F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting fade = register(new FloatSetting("fade", "Появление и затухание",
            "Секунд на появление и столько же на растворение", 0.25F, 0.02F, 1.5F, 0.01F,
            setting -> settingChanged(setting)));
    private final FloatSetting speed = register(new FloatSetting("speed", "Скорость", "Разлёт в блоках в секунду",
            0.55F, 0.0F, 3.0F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting lifetime = register(new FloatSetting("lifetime", "Время жизни",
            "Сколько секунд живёт частица", 2.4F, 0.2F, 10.0F, 0.1F, setting -> settingChanged(setting)));
    private final FloatSetting gravity = register(new FloatSetting("gravity", "Тяга",
            "Отрицательная — частица падает, положительная — всплывает", -0.12F, -1.0F, 1.0F, 0.01F,
            setting -> settingChanged(setting)));
    private final IntSetting rate = register(new IntSetting("rate", "Частота", "Частиц в секунду у игрока",
            12, 0, 80, setting -> settingChanged(setting)));
    private final IntSetting limit = register(new IntSetting("limit", "Предел частиц",
            "Максимум одновременных частиц", 160, 16, 1200, setting -> applyLimit()));
    private final BooleanSetting trail = register(new BooleanSetting("trail", "След за игроком",
            "Частицы рождаются вокруг игрока", true, setting -> settingChanged(setting)));
    private final BooleanSetting motion = register(new BooleanSetting("onMove", "Только в движении",
            "След появляется, когда игрок идёт", false, setting -> settingChanged(setting)));

    private final ParticleSystem system = new ParticleSystem(160);
    private float spawnAccumulator;

    public ParticleModule() {
        super("particles", "Частицы", "Свои частицы в мире: форма, цвет, размер, скорость и время жизни",
                ModuleCategory.VISUAL);
    }

    private void applyPreset() {
        ParticlePreset value = this.preset.get();
        this.shape.set(value.shape());
        this.colorFrom.set(value.colorA());
        this.colorTo.set(value.colorB());
        this.size.set(value.size());
        this.alpha.set(value.alpha());
        this.speed.set(value.speed());
        this.lifetime.set(value.lifetime());
        this.gravity.set(value.gravity());
        this.rate.set(value.rate());
        this.fade.set(value.fade());
        settingChanged(this.preset);
    }

    private void applyLimit() {
        this.system.setCapacity(this.limit.get());
        settingChanged(this.limit);
    }

    @Override
    protected void onEnable() {
        this.system.setCapacity(this.limit.get());
        this.spawnAccumulator = 0.0F;
    }

    @Override
    protected void onDisable() {
        // Ресурсы пула освобождаются сразу: частицы не висят в памяти выключенного модуля.
        this.system.clear();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public ParticleSystem system() {
        return this.system;
    }

    public float fadeSeconds() {
        return this.fade.get();
    }

    /** Создаёт частицу в заданной точке с текущими настройками. */
    public void spawnAt(double x, double y, double z, double spread, int count) {
        int from = ColorController.resolve(this.colorFrom.get());
        int to = ColorController.resolve(this.colorTo.get());
        for (int index = 0; index < count; index++) {
            double motionX = (RANDOM.nextDouble() - 0.5D) * spread;
            double motionY = (RANDOM.nextDouble() - 0.5D) * spread + this.speed.get() * 0.35D;
            double motionZ = (RANDOM.nextDouble() - 0.5D) * spread;
            float life = this.lifetime.get() * (0.7F + 0.6F * RANDOM.nextFloat());
            this.system.spawn(x, y, z, motionX, motionY, motionZ, life,
                    this.size.get() * (0.7F + 0.6F * RANDOM.nextFloat()), this.alpha.get(), this.gravity.get(),
                    0.02F, (RANDOM.nextFloat() - 0.5F) * 6.0F, from, to, this.shape.get());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        this.system.update(deltaSeconds);
        if (!this.trail.isEnabled() || this.rate.get() <= 0) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            return;
        }
        if (this.motion.isEnabled() && player.getDeltaMovement().horizontalDistance() < 0.02D) {
            return;
        }
        this.spawnAccumulator += deltaSeconds * this.rate.get();
        int count = (int) this.spawnAccumulator;
        if (count <= 0) {
            return;
        }
        this.spawnAccumulator -= count;
        double x = player.getX() + (RANDOM.nextDouble() - 0.5D) * 0.8D;
        double y = player.getY() + 0.15D + RANDOM.nextDouble() * 1.6D;
        double z = player.getZ() + (RANDOM.nextDouble() - 0.5D) * 0.8D;
        spawnAt(x, y, z, this.speed.get(), count);
    }

    /** Показывает в списке модулей, сколько частиц сейчас живёт. */
    public String activity() {
        return this.system.alive() + " / " + this.system.capacity();
    }

    @Override
    protected void onSettingChanged(Setting<?> setting) {
        if (setting == this.limit) {
            this.system.setCapacity(this.limit.get());
        }
    }
}
