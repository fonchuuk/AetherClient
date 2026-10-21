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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Мировая система частиц ({@code Particles}, Блок 4.5):
 * <ul>
 *   <li>Частицы спавнятся в абсолютных координатах мира {@code (X, Y, Z)} вокруг игрока;</li>
 *   <li>Если игрок отходит или поворачивает камеру — частицы остаются висеть на том самом месте
 *       в мире, где появились;</li>
 *   <li>Включает выбор формы частиц ({@link ParticleShape}) и ползунок частоты их появления ({@code rate}).</li>
 * </ul>
 */
public final class ParticleModule extends Module {
    private static final Random RANDOM = new Random();

    private final EnumSetting<ParticleShape> shape = register(new EnumSetting<>("shape", "Форма частиц",
            "Выбор формы 3D-частиц в мире (Звезда, Искра, Кольцо, Снежинка, Сфера, Кристалл, Сердце)",
            ParticleShape.STAR, ParticleShape.values(), setting -> settingChanged(setting)));
    private final IntSetting rate = register(new IntSetting("rate", "Частота появления",
            "Сколько новых частиц в секунду появляется в координатах мира вокруг игрока",
            16, 1, 100, setting -> settingChanged(setting)));
    private final FloatSetting radius = register(new FloatSetting("radius", "Радиус спавна в мире",
            "Радиус в блоках (X, Z), в котором вокруг игрока появляются мировые частицы",
            12.0F, 3.0F, 28.0F, 0.5F, setting -> settingChanged(setting)));
    private final FloatSetting lifetime = register(new FloatSetting("lifetime", "Время жизни в мире",
            "Сколько секунд частица остаётся на месте (X, Y, Z), когда игрок отходит",
            7.5F, 1.5F, 15.0F, 0.5F, setting -> settingChanged(setting)));
    private final FloatSetting size = register(new FloatSetting("size", "Размер частицы",
            "Размер 3D-спрайта частицы в мире",
            0.17F, 0.04F, 0.55F, 0.01F, setting -> settingChanged(setting)));
    private final FloatSetting speed = register(new FloatSetting("speed", "Локальный дрейф",
            "Лёгкое покачивание частицы вокруг своей мировой точки (0 — неподвижна в точке спавна)",
            0.16F, 0.0F, 1.5F, 0.02F, setting -> settingChanged(setting)));
    private final FloatSetting gravity = register(new FloatSetting("gravity", "Вертикальная тяга",
            "Отрицательная — плавно опускается, положительная — всплывает",
            -0.02F, -0.6F, 0.6F, 0.01F, setting -> settingChanged(setting)));
    private final FloatSetting alpha = register(new FloatSetting("alpha", "Яркость частиц",
            "Непрозрачность ядра и ореола свечения частицы",
            0.90F, 0.10F, 1.0F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting fade = register(new FloatSetting("fade", "Плавность появления",
            "Доля времени жизни на плавное появление и затухание",
            0.25F, 0.05F, 0.80F, 0.05F, setting -> settingChanged(setting)));
    private final EnumSetting<ParticlePreset> preset = register(new EnumSetting<>("preset", "Готовый пресет",
            "Быстрый выбор стиля мировых частиц",
            ParticlePreset.DEFAULT, ParticlePreset.values(), setting -> applyPreset()));
    private final ColorSetting colorFrom = register(new ColorSetting("colorA", "Начальный цвет",
            "Цвет частицы при появлении в мире", 0xFF7FD4FF, setting -> settingChanged(setting)));
    private final ColorSetting colorTo = register(new ColorSetting("colorB", "Конечный цвет",
            "Цвет частицы перед растворением", 0xFFB98CFF, setting -> settingChanged(setting)));
    private final IntSetting limit = register(new IntSetting("limit", "Максимум частиц в мире",
            "Предел одновременных мировых частиц", 160, 64, 1200, setting -> applyLimit()));
    private final BooleanSetting trail = register(new BooleanSetting("trail", "Спавн в мировом радиусе",
            "Постоянно спавнит частицы в мировых координатах (X, Y, Z) вокруг игрока",
            true, setting -> settingChanged(setting)));
    private final BooleanSetting hitBurst = register(new BooleanSetting("hitBurst", "Всплеск при ударе",
            "Вспышка частиц в мировой точке удара по блоку или сущности",
            true, setting -> settingChanged(setting)));
    private final BooleanSetting motion = register(new BooleanSetting("onMove", "След на месте шагов",
            "Оставляет частицы в мировых координатах позади идущего игрока",
            true, setting -> settingChanged(setting)));

    private final ParticleSystem system = new ParticleSystem(160);
    private float spawnAccumulator;
    private float stepAccumulator;
    private boolean prevAttackDown;
    private boolean prevUseDown;

    public ParticleModule() {
        super("particles", "Частицы",
                "Мировые 3D-частицы в координатах (X, Y, Z), остающиеся на месте при уходе игрока",
                ModuleCategory.VISUAL);
    }

    private void applyPreset() {
        applyPreset(this.preset.get());
    }

    public void applyPreset(ParticlePreset value) {
        if (value == null) {
            return;
        }
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
        this.stepAccumulator = 0.0F;
    }

    @Override
    protected void onDisable() {
        this.system.clear();
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public ParticleSystem system() {
        return this.system;
    }

    public EnumSetting<ParticleShape> shapeSetting() {
        return this.shape;
    }

    public IntSetting rateSetting() {
        return this.rate;
    }

    public EnumSetting<ParticlePreset> presetSetting() {
        return this.preset;
    }

    public float fadeSeconds() {
        return this.fade.get();
    }

    public float radius() {
        return this.radius.get();
    }

    /**
     * Спавнит частицы в абсолютных мировых координатах {@code (x, y, z)}.
     * Каждая частица хранит свои мировые координаты {@code (X, Y, Z)} и остаётся
     * на том месте, где появилась, когда игрок отходит.
     */
    public void spawnAt(double x, double y, double z, double spread, int count) {
        int from = ColorController.resolve(this.colorFrom.get());
        int to = ColorController.resolve(this.colorTo.get());
        for (int index = 0; index < count; index++) {
            double motionX = (RANDOM.nextDouble() - 0.5D) * spread;
            double motionY = (RANDOM.nextDouble() - 0.45D) * spread * 0.65D;
            double motionZ = (RANDOM.nextDouble() - 0.5D) * spread;
            float life = this.lifetime.get() * (0.85F + 0.35F * RANDOM.nextFloat());
            this.system.spawn(x, y, z, motionX, motionY, motionZ, life,
                    this.size.get() * (0.80F + 0.40F * RANDOM.nextFloat()),
                    this.alpha.get(), this.gravity.get(),
                    0.12F, this.fade.get(),
                    from, to, this.shape.get());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        this.system.update(deltaSeconds);

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        // Если в мире ещё нет частиц (например, только зашли в мир) — сразу засеваем мировые точки вокруг
        if (this.trail.isEnabled() && this.system.alive() == 0 && this.rate.get() > 0) {
            spawnWorldRingAround(px, py, pz, Math.min(24, Math.max(8, this.rate.get())));
        }

        // 1. Постоянный спавн частиц в мировых координатах (X, Y, Z) в радиусе вокруг игрока
        if (this.trail.isEnabled() && this.rate.get() > 0) {
            this.spawnAccumulator += deltaSeconds * this.rate.get();
            int count = (int) this.spawnAccumulator;
            if (count > 0) {
                this.spawnAccumulator -= count;
                spawnWorldRingAround(px, py, pz, Math.min(count, 12));
            }
        }

        // 2. След частиц в мировых координатах (остаётся позади игрока, когда он уходит!)
        if (this.motion.isEnabled()) {
            Vec3 vel = player.getDeltaMovement();
            double horiz = Math.hypot(vel.x, vel.z);
            if (horiz > 0.03D) {
                this.stepAccumulator += deltaSeconds * (float) (horiz * 28.0D);
                int sc = (int) this.stepAccumulator;
                if (sc > 0) {
                    this.stepAccumulator -= sc;
                    double ox = (RANDOM.nextDouble() - 0.5D) * 0.8D;
                    double oz = (RANDOM.nextDouble() - 0.5D) * 0.8D;
                    spawnAt(px + ox, py + 0.25D + RANDOM.nextDouble() * 0.9D, pz + oz,
                            this.speed.get() * 0.30D, Math.min(sc, 3));
                }
            }
        }

        // 3. Всплеск частиц в мировой точке удара или взаимодействия
        if (this.hitBurst.isEnabled() && client.options != null) {
            boolean attackDown = client.options.keyAttack.isDown();
            boolean useDown = client.options.keyUse.isDown();
            if ((attackDown && !this.prevAttackDown) || (useDown && !this.prevUseDown)) {
                HitResult hit = client.hitResult;
                if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                    Vec3 loc = bhr.getLocation();
                    spawnAt(loc.x, loc.y, loc.z, Math.max(0.45D, this.speed.get() * 1.6D), 10);
                } else if (hit instanceof EntityHitResult ehr && hit.getType() == HitResult.Type.ENTITY) {
                    Vec3 loc = ehr.getLocation();
                    spawnAt(loc.x, loc.y, loc.z, Math.max(0.55D, this.speed.get() * 1.8D), 14);
                }
            }
            this.prevAttackDown = attackDown;
            this.prevUseDown = useDown;
        }
    }

    private void spawnWorldRingAround(double px, double py, double pz, int spawnCount) {
        double maxR = Math.max(3.0D, this.radius.get());
        double minR = 1.8D;
        for (int i = 0; i < spawnCount; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double u = RANDOM.nextDouble();
            double dist = Math.sqrt(minR * minR + u * (maxR * maxR - minR * minR));
            double wx = px + Math.cos(angle) * dist;
            double wz = pz + Math.sin(angle) * dist;
            double wy = py - 0.3D + RANDOM.nextDouble() * 4.5D;
            spawnAt(wx, wy, wz, this.speed.get() * 0.35D, 1);
        }
    }

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
