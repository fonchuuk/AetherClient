package com.aetherglass.client.modules.impl;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.material.ColorSource;
import com.aetherglass.client.render.material.FillMode;
import com.aetherglass.client.render.material.Material;
import com.aetherglass.client.render.shader.ShaderEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Эффекты блоков ({@code BlockShader}): 16 процедурных шейдер-эффектов, подсветка блока
 * под прицелом по реальной геометрии {@code VoxelShape}, частицы при взаимодействии
 * и частицы вокруг отдельных категорий блоков.
 *
 * <p>Модуль ничего не меняет в самом мире: он только рисует. Свойства блоков
 * (прочность, ломаемость, свет, коллизии) остаются ванильными.</p>
 */
public final class BlockEffectsModule extends Module {
    private static final Random RANDOM = new Random();
    private static final int MAX_TRACKED = 48;
    private static final float SCAN_PERIOD_SECONDS = 0.35F;

    // ─────────────────────── Настройки 16 шейдер-эффектов (BlockShader) ───────────────────────
    private final BooleanSetting useShaderEffect = register(new BooleanSetting("useShader",
            "Шейдер-эффект (BlockShader)",
            "Включает один из 16 процедурных шейдер-эффектов для блока под прицелом",
            true, setting -> settingChanged(setting)));
    private final EnumSetting<ShaderEffect> shaderEffect = register(new EnumSetting<>("shaderEffect",
            "Эффект BlockShader",
            "Выбор одного из 16 уникальных шейдер-эффектов для блоков",
            ShaderEffect.LIQUID_GLASS, ShaderEffect.values(), setting -> settingChanged(setting)));
    private final ColorSetting shaderColor = register(new ColorSetting("shaderColor",
            "Цвет шейдера блока",
            "Основной цвет выбранного шейдер-эффекта блока",
            ShaderEffect.LIQUID_GLASS.defaultColor(), setting -> settingChanged(setting)));
    private final FloatSetting shaderIntensity = register(new FloatSetting("shaderIntensity",
            "Интенсивность шейдера",
            "Яркость, контраст и выраженность шейдер-эффекта блока",
            1.00F, 0.10F, 2.00F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting shaderSpeed = register(new FloatSetting("shaderSpeed",
            "Скорость анимации шейдера",
            "Скорость течения волн, разрядов и переливов шейдера блока",
            1.00F, 0.10F, 3.00F, 0.05F, setting -> settingChanged(setting)));
    private final FloatSetting shaderOpacity = register(new FloatSetting("shaderOpacity",
            "Прозрачность шейдера",
            "Непрозрачность поверхности и контура шейдера блока",
            0.78F, 0.05F, 1.00F, 0.02F, setting -> settingChanged(setting)));

    // ─────────────────────── Общие настройки заливки и частиц ───────────────────────
    private final EnumSetting<FillMode> mode = register(new EnumSetting<>("mode", "Режим заливки",
            "Чем закрашивается блок под прицелом", FillMode.SHADER_FILL, FillMode.values(),
            setting -> settingChanged(setting)));
    private final EnumSetting<ColorSource> colorSource = register(new EnumSetting<>("colorSource", "Источник цвета",
            "Откуда берётся цвет подсветки", ColorSource.CUSTOM, ColorSource.values(),
            setting -> settingChanged(setting)));
    private final ColorSetting color = register(new ColorSetting("color", "Свой цвет",
            "Цвет подсветки в режиме «свой цвет»", 0xFF8FE3FF, setting -> settingChanged(setting)));
    private final FloatSetting fillAlpha = register(new FloatSetting("fillAlpha", "Плотность заливки",
            "Насколько плотно закрашен блок", 0.34F, 0.0F, 1.0F, 0.05F,
            setting -> settingChanged(setting)));
    private final FloatSetting outlineWidth = register(new FloatSetting("outlineWidth", "Толщина контура",
            "Толщина линий по рёбрам блока", 0.02F, 0.004F, 0.08F, 0.002F,
            setting -> settingChanged(setting)));
    private final FloatSetting outlineAlpha = register(new FloatSetting("outlineAlpha", "Яркость контура",
            "Насколько яркие линии контура", 0.9F, 0.0F, 1.0F, 0.05F,
            setting -> settingChanged(setting)));
    private final FloatSetting glow = register(new FloatSetting("glow", "Свечение",
            "Мягкое свечение по граням блока", 0.4F, 0.0F, 1.0F, 0.05F,
            setting -> settingChanged(setting)));
    private final FloatSetting gradientSpeed = register(new FloatSetting("gradientSpeed", "Скорость блика",
            "Как быстро бежит блик по градиенту", 0.55F, 0.0F, 2.0F, 0.05F,
            setting -> settingChanged(setting)));
    private final BooleanSetting rim = register(new BooleanSetting("rim", "Блик по кромке",
            "Светлая линия по верхним рёбрам блока", true, setting -> settingChanged(setting)));
    private final FloatSetting pulseSpeed = register(new FloatSetting("pulse", "Пульсация",
            "Как быстро «дышит» подсветка: 0 — ровный свет", 0.8F, 0.0F, 4.0F, 0.1F,
            setting -> settingChanged(setting)));

    private final BooleanSetting hitParticles = register(new BooleanSetting("hitParticles", "Частицы при ударе",
            "Вспышка частиц в точке взаимодействия", true, setting -> settingChanged(setting)));
    private final IntSetting hitCount = register(new IntSetting("hitCount", "Частиц за удар",
            "Сколько частиц рождается за одно нажатие", 8, 1, 40, setting -> settingChanged(setting)));
    private final FloatSetting hitSpeed = register(new FloatSetting("hitSpeed", "Разлёт",
            "Скорость разлёта частиц взаимодействия", 1.4F, 0.2F, 5.0F, 0.1F,
            setting -> settingChanged(setting)));

    private final BooleanSetting ambient = register(new BooleanSetting("ambient", "Частицы у блоков",
            "Фоновые частицы вокруг особых блоков", true, setting -> settingChanged(setting)));
    private final IntSetting radius = register(new IntSetting("radius", "Радиус поиска",
            "В каком радиусе искать особые блоки", 6, 2, 16, setting -> settingChanged(setting)));
    private final IntSetting ambientRate = register(new IntSetting("ambientRate", "Частота",
            "Частиц в секунду на один блок", 2, 1, 20, setting -> settingChanged(setting)));
    private final IntSetting ambientBlocks = register(new IntSetting("ambientBlocks", "Блоков за раз",
            "Сколько блоков обслуживается одновременно", 12, 1, MAX_TRACKED,
            setting -> settingChanged(setting)));
    private final BooleanSetting ores = register(new BooleanSetting("ores", "Руды",
            "Частицы над рудами", true, setting -> scanNow()));
    private final BooleanSetting containers = register(new BooleanSetting("containers", "Хранилища",
            "Частицы над сундуками, печами и другими блоками с содержимым", true, setting -> scanNow()));
    private final BooleanSetting lights = register(new BooleanSetting("lights", "Источники света",
            "Частицы над светящимися блоками", false, setting -> scanNow()));
    private final BooleanSetting plants = register(new BooleanSetting("plants", "Растения",
            "Частицы над цветами и саженцами", false, setting -> scanNow()));

    private final BlockPos.MutableBlockPos[] tracked = new BlockPos.MutableBlockPos[MAX_TRACKED];
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int trackedCount;
    private float scanCooldown;
    private float ambientAccumulator;
    private float pulseTime;

    /** Ключ настройки с выбранным материалом. */
    public static final String MATERIAL_KEY = "module.blocks.material";

    /** Материал, выбранный по умолчанию. */
    public static final String DEFAULT_MATERIAL = "builtin.liquid";

    private String activeMaterial = DEFAULT_MATERIAL;

    private BlockPos outlined;
    private float outlineFade;
    private float outlineStrength;
    private float effectTime;
    private boolean attackWasDown;
    private boolean useWasDown;

    public BlockEffectsModule() {
        super("blocks", "Эффекты блоков", "BlockShader: 16 шейдер-эффектов, подсветка блока под прицелом и частицы",
                ModuleCategory.VISUAL);
        for (int index = 0; index < this.tracked.length; index++) {
            this.tracked[index] = new BlockPos.MutableBlockPos();
        }
    }

    private void scanNow() {
        this.scanCooldown = 0.0F;
        settingChanged(this.ores);
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    protected void onEnable() {
        this.trackedCount = 0;
        this.scanCooldown = 0.0F;
        this.pulseTime = 0.0F;
        this.attackWasDown = false;
        this.useWasDown = false;
        this.activeMaterial = ModConfig.get().getString(MATERIAL_KEY, DEFAULT_MATERIAL);
    }

    @Override
    protected void onDisable() {
        this.trackedCount = 0;
        this.outlined = null;
        this.outlineFade = 0.0F;
    }

    /** Сколько блоков сейчас под наблюдением и какой шейдер активен: показывается в списке модулей. */
    public String activity() {
        if (this.useShaderEffect.isEnabled()) {
            return this.shaderEffect.get().displayName() + " · блоков: " + this.trackedCount;
        }
        return "блоков под наблюдением: " + this.trackedCount;
    }

    // ─────────────────────── BlockShader (16 эффектов) ───────────────────────

    public boolean useShaderEffect() {
        return this.useShaderEffect.isEnabled();
    }

    public void setUseShaderEffect(boolean enabled) {
        this.useShaderEffect.set(enabled);
    }

    public ShaderEffect shaderEffect() {
        return this.shaderEffect.get();
    }

    public void setShaderEffect(ShaderEffect effect) {
        if (effect != null) {
            this.shaderEffect.set(effect);
        }
    }

    /** Выбирает шейдер-эффект и применяет его эталонные параметры (цвет, интенсивность, скорость, прозрачность). */
    public void selectShaderPreset(ShaderEffect effect) {
        if (effect == null) {
            return;
        }
        this.shaderEffect.set(effect);
        this.shaderColor.set(effect.defaultColor());
        this.shaderIntensity.set(effect.defaultIntensity());
        this.shaderSpeed.set(effect.defaultSpeed());
        this.shaderOpacity.set(effect.defaultOpacity());
    }

    /** Сбрасывает параметры текущего шейдер-эффекта к его эталонным значениям. */
    public void resetCurrentShaderEffect() {
        selectShaderPreset(this.shaderEffect.get());
    }

    /** Полный сброс всех настроек BlockShader к стандартным значениям. */
    public void resetShaderDefaults() {
        this.useShaderEffect.reset();
        selectShaderPreset(ShaderEffect.LIQUID_GLASS);
        this.mode.reset();
        this.fillAlpha.reset();
        this.outlineWidth.reset();
        this.outlineAlpha.reset();
        this.glow.reset();
    }

    public int shaderColor() {
        return this.shaderColor.get();
    }

    public void setShaderColor(int colorValue) {
        this.shaderColor.set(colorValue);
    }

    public ColorSetting shaderColorSetting() {
        return this.shaderColor;
    }

    public float shaderIntensity() {
        return this.shaderIntensity.get();
    }

    public void setShaderIntensity(float value) {
        this.shaderIntensity.set(value);
    }

    public FloatSetting shaderIntensitySetting() {
        return this.shaderIntensity;
    }

    public float shaderSpeed() {
        return this.shaderSpeed.get();
    }

    public void setShaderSpeed(float value) {
        this.shaderSpeed.set(value);
    }

    public FloatSetting shaderSpeedSetting() {
        return this.shaderSpeed;
    }

    public float shaderOpacity() {
        return this.shaderOpacity.get();
    }

    public void setShaderOpacity(float value) {
        this.shaderOpacity.set(value);
    }

    public FloatSetting shaderOpacitySetting() {
        return this.shaderOpacity;
    }

    // ─────────────────────── Состояние для отрисовки ───────────────────────

    /** Блок под прицелом или {@code null}. */
    public BlockPos outlined() {
        return this.outlineFade > 0.01F ? this.outlined : null;
    }

    /** Текущая плотность подсветки с учётом плавного появления и пульсации. */
    public float outlineStrength() {
        return this.outlineFade * this.outlineStrength;
    }

    /** Текущий режим заливки. */
    public FillMode mode() {
        return this.mode.get();
    }

    /** Плотность заливки 0..1. */
    public float fillAlpha() {
        return this.fillAlpha.get();
    }

    /** Яркость контура 0..1. */
    public float outlineAlpha() {
        return this.outlineAlpha.get();
    }

    /** Толщина линий контура в долях блока. */
    public float outlineWidth() {
        return this.outlineWidth.get();
    }

    /** Сила свечения 0..1. */
    public float glow() {
        return this.glow.get();
    }

    /** Скорость бегущего блика. */
    public float gradientSpeed() {
        return this.gradientSpeed.get();
    }

    /** Нужен ли блик по верхним рёбрам. */
    public boolean rim() {
        return this.rim.get();
    }

    /**
     * Цвет блока с учётом выбранного источника: свой цвет, общий перелив
     * радуги или акцент оформления.
     */
    public int resolvedColor() {
        return switch (this.colorSource.get()) {
            case RAINBOW -> ColorController.current();
            case ACCENT -> AuroraTheme.accent();
            default -> ColorController.resolve(this.color.get());
        };
    }

    /**
     * Время с момента запуска модуля в секундах: от него идут бегущий блик
     * и другие плавные анимации, чтобы они не зависели от частоты кадров.
     */
    public float effectTime() {
        return this.effectTime;
    }

    /** Фаза пульсации 0..1. */
    public float pulsePhase() {
        return (float) (0.5D + 0.5D * Math.sin(this.pulseTime * Math.PI * 2.0D));
    }

    @Override
    public void tick(float deltaSeconds) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        this.pulseTime += deltaSeconds * this.pulseSpeed.get();
        if (this.pulseTime > 4096.0F) {
            this.pulseTime = 0.0F;
        }
        this.effectTime += deltaSeconds;
        if (this.effectTime > 4096.0F) {
            this.effectTime = 0.0F;
        }
        if (player == null || client.level == null) {
            this.outlineFade = Math.max(0.0F, this.outlineFade - deltaSeconds * 4.0F);
            this.trackedCount = 0;
            return;
        }
        updateOutline(client, deltaSeconds);
        updateInteractions(client);
        if (this.ambient.isEnabled()) {
            this.scanCooldown -= deltaSeconds;
            if (this.scanCooldown <= 0.0F) {
                this.scanCooldown = SCAN_PERIOD_SECONDS;
                scanAround(client, player);
            }
            spawnAmbient(deltaSeconds);
        } else {
            this.trackedCount = 0;
        }
    }

    private void updateOutline(Minecraft client, float deltaSeconds) {
        HitResult hit = client.hitResult;
        if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos position = blockHit.getBlockPos();
            if (!position.equals(this.outlined)) {
                this.outlined = position.immutable();
            }
            this.outlineFade = Math.min(1.0F, this.outlineFade + deltaSeconds * 6.0F);
            if (this.pulseSpeed.get() > 0.0F) {
                this.outlineStrength = this.outlineAlpha.get() * (0.45F + 0.55F * pulsePhase());
            } else {
                this.outlineStrength = this.outlineAlpha.get();
            }
        } else {
            this.outlineFade = Math.max(0.0F, this.outlineFade - deltaSeconds * 5.0F);
            if (this.outlineFade <= 0.0F) {
                this.outlined = null;
            }
        }
    }

    /** Реагирует только на нажатие (front edge), а не на удержание кнопки. */
    private void updateInteractions(Minecraft client) {
        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        if (this.hitParticles.isEnabled()) {
            if (attackDown && !this.attackWasDown) {
                burst(client, this.hitCount.get(), this.hitSpeed.get());
            }
            if (useDown && !this.useWasDown) {
                burst(client, Math.max(2, this.hitCount.get() / 2), this.hitSpeed.get() * 0.6F);
            }
        }
        this.attackWasDown = attackDown;
        this.useWasDown = useDown;
    }

    private void burst(Minecraft client, int count, float speed) {
        HitResult hit = client.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        ParticleModule particles = ModuleManager.get().typed("particles", ParticleModule.class);
        if (particles == null || !particles.isEnabled()) {
            return;
        }
        Vec3 point = blockHit.getLocation();
        particles.spawnAt(point.x, point.y, point.z, speed, count);
    }

    // ─────────────────────── Фоновые частицы у блоков ───────────────────────

    private void scanAround(Minecraft client, LocalPlayer player) {
        if (client.level == null) {
            return;
        }
        this.trackedCount = 0;
        int limit = Math.min(this.tracked.length, this.ambientBlocks.get());
        int range = this.radius.get();
        BlockPos center = player.blockPosition();
        for (int offsetY = -2; offsetY <= 4 && this.trackedCount < limit; offsetY++) {
            for (int offsetX = -range; offsetX <= range && this.trackedCount < limit; offsetX++) {
                for (int offsetZ = -range; offsetZ <= range && this.trackedCount < limit; offsetZ++) {
                    this.cursor.set(center.getX() + offsetX, center.getY() + offsetY, center.getZ() + offsetZ);
                    if (isInteresting(client.level.getBlockState(this.cursor))) {
                        this.tracked[this.trackedCount].set(this.cursor);
                        this.trackedCount++;
                    }
                }
            }
        }
    }

    private boolean isInteresting(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (this.ores.isEnabled() && isOre(state)) {
            return true;
        }
        if (this.containers.isEnabled() && state.hasBlockEntity()) {
            return true;
        }
        if (this.lights.isEnabled() && state.getLightEmission() > 3) {
            return true;
        }
        return this.plants.isEnabled() && (state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS));
    }

    private static boolean isOre(BlockState state) {
        return state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES)
                || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.REDSTONE_ORES);
    }

    private void spawnAmbient(float deltaSeconds) {
        ParticleModule particles = ModuleManager.get().typed("particles", ParticleModule.class);
        if (particles == null || !particles.isEnabled() || this.trackedCount == 0) {
            return;
        }
        this.ambientAccumulator += deltaSeconds * this.ambientRate.get() * this.trackedCount;
        int budget = (int) this.ambientAccumulator;
        if (budget <= 0) {
            return;
        }
        this.ambientAccumulator -= budget;
        if (budget > 24) {
            budget = 24;
        }
        for (int index = 0; index < budget; index++) {
            BlockPos position = this.tracked[RANDOM.nextInt(this.trackedCount)];
            particles.spawnAt(position.getX() + 0.5D, position.getY() + 1.05D, position.getZ() + 0.5D, 0.25D, 1);
        }
    }

    // ─────────────────────────── Материалы ───────────────────────────

    public String activeMaterialId() {
        return this.activeMaterial;
    }

    public void applyMaterial(Material material) {
        if (material == null) {
            return;
        }
        this.mode.set(material.mode());
        this.colorSource.set(material.colorSource());
        this.color.set(material.color());
        this.fillAlpha.set(material.fillAlpha());
        this.outlineWidth.set(material.outlineWidth());
        this.outlineAlpha.set(material.outlineAlpha());
        this.glow.set(material.glow());
        this.gradientSpeed.set(material.gradientSpeed());
        this.rim.set(material.rim());
        this.activeMaterial = material.id();
        ModConfig.get().set(MATERIAL_KEY, material.id());
        ModConfig.get().saveIfNeeded();
    }

    public Material toMaterial(String id, String name) {
        return new Material(id, name, false, this.mode.get(), this.colorSource.get(), this.color.get(),
                this.fillAlpha.get(), this.outlineWidth.get(), this.outlineAlpha.get(), this.glow.get(),
                this.gradientSpeed.get(), this.rim.get());
    }
}
