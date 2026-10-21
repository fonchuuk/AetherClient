package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.util.FrameClock;
import com.aetherglass.client.render.util.Projector;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Бой: хитмаркер у прицела и всплывающие цифры урона над существом.
 *
 * <p>Урон считается на клиенте по изменению здоровья существа, поэтому работает и в
 * одиночной игре, и на сервере без модов. Всё состояние — переиспользуемые массивы и
 * {@link HashMap} на {@code Integer}, без выделений в кадре: цифры живут в кольцевом буфере.</p>
 */
public final class CombatModule extends Module {
    private static final int MAX_NUMBERS = 32;
    private static final float NUMBER_LIFETIME = 1.15F;
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath("aetherglass", "combat");

    private final BooleanSetting hitmarker = register(new BooleanSetting("hitmarker",
            "Хитмаркер у прицела", "Короткий неоновый «крест» при попадании по существу", true,
            this::onUserSettingChanged));
    private final BooleanSetting damageNumbers = register(new BooleanSetting("damageNumbers",
            "Цифры урона", "Всплывающие числа урона над существом", true, this::onUserSettingChanged));
    private final BooleanSetting hurtTint = register(new BooleanSetting("hurtTint",
            "Красная вспышка по цели", "Подсветка существа, по которому попали", true, this::onUserSettingChanged));
    private final FloatSetting hitmarkerTime = register(new FloatSetting("hitmarkerTime",
            "Длительность хитмаркера", "Сколько держится хитмаркер (секунды)", 0.22F, 0.08F, 0.8F, 0.02F,
            this::onUserSettingChanged));

    private final Map<Integer, Float> lastHealth = new HashMap<>();
    private final double[] numberX = new double[MAX_NUMBERS];
    private final double[] numberY = new double[MAX_NUMBERS];
    private final double[] numberZ = new double[MAX_NUMBERS];
    private final float[] numberValue = new float[MAX_NUMBERS];
    private final float[] numberAge = new float[MAX_NUMBERS];
    private final float[] numberSeed = new float[MAX_NUMBERS];
    private int numberCount;
    private int numberCursor;
    private final float[] projected = new float[3];
    private float hitmarkerLeft;
    private float hitmarkerStrength;
    private boolean installed;
    private long lastHitEntity = -1L;

    public CombatModule() {
        super("combat", "Бой и попадания",
                "Хитмаркер у прицела, всплывающие цифры урона и вспышка по цели",
                ModuleCategory.VISUAL);
    }

    private void onUserSettingChanged(Setting<?> setting) {
        // Настройки читаются в кадре напрямую — пересчёт не требуется.
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    /** Подключает слой HUD (вызывается из инициализации клиента). */
    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.addLast(LAYER_ID, this::render);
    }

    @Override
    protected void onEnable() {
        this.lastHealth.clear();
        this.numberCount = 0;
        this.numberCursor = 0;
    }

    @Override
    public void tick(float deltaSeconds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        for (int i = 0; i < this.numberCount; i++) {
            this.numberAge[i] += deltaSeconds;
        }
        // Гасим истёкшие цифры: сдвигаем хвост буфера, без новых объектов.
        for (int i = this.numberCount - 1; i >= 0; i--) {
            if (this.numberAge[i] > NUMBER_LIFETIME) {
                int last = this.numberCount - 1;
                this.numberX[i] = this.numberX[last];
                this.numberY[i] = this.numberY[last];
                this.numberZ[i] = this.numberZ[last];
                this.numberValue[i] = this.numberValue[last];
                this.numberAge[i] = this.numberAge[last];
                this.numberSeed[i] = this.numberSeed[last];
                this.numberCount--;
            }
        }
        if (this.hitmarkerLeft > 0.0F) {
            this.hitmarkerLeft = Math.max(0.0F, this.hitmarkerLeft - deltaSeconds);
        }

        this.lastHealth.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player) {
                continue;
            }
            float health = living.getHealth();
            Float previous = this.lastHealth.put(entity.getId(), health);
            if (previous == null || health >= previous - 0.05F) {
                continue;
            }
            float damage = previous - health;
            this.onDamage(mc, living, damage);
        }
    }

    private void onDamage(Minecraft mc, LivingEntity target, float damage) {
        if (this.damageNumbers.isEnabled() && this.numberCount < MAX_NUMBERS) {
            int i = this.numberCursor % MAX_NUMBERS;
            this.numberCursor++;
            this.numberX[i] = target.getX();
            this.numberY[i] = target.getY() + target.getBbHeight() * 0.85D;
            this.numberZ[i] = target.getZ();
            this.numberValue[i] = damage;
            this.numberAge[i] = 0.0F;
            this.numberSeed[i] = (target.getId() * 37 % 9) / 9.0F - 0.5F;
            if (this.numberCount < MAX_NUMBERS) {
                this.numberCount++;
            }
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(player.getEyePosition(1.0F));
        double distance = toTarget.length();
        if (distance > 0.001D && look.dot(toTarget.scale(1.0D / distance)) > 0.86D && distance < 26.0D) {
            this.hitmarkerLeft = this.hitmarkerTime.get();
            this.hitmarkerStrength = Math.min(1.0F, 0.45F + damage / 8.0F);
            this.lastHitEntity = target.getId();
        }
    }

    /** Существо, по которому только что попали (для вспышки/рендера). */
    public long lastHitEntity() {
        return this.hitmarkerLeft > 0.0F ? this.lastHitEntity : -1L;
    }

    public boolean hurtTintEnabled() {
        return this.hurtTint.isEnabled();
    }

    private void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.options == null || mc.options.hideGui || mc.level == null) {
            return;
        }
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        if (this.hitmarker.isEnabled() && this.hitmarkerLeft > 0.0F) {
            renderHitmarker(graphics, sw, sh);
        }
        if (this.damageNumbers.isEnabled()) {
            renderNumbers(graphics, mc, partial, sw, sh);
        }
    }

    private void renderHitmarker(GuiGraphics graphics, int sw, int sh) {
        float progress = 1.0F - this.hitmarkerLeft / Math.max(0.001F, this.hitmarkerTime.get());
        int alpha = (int) (255.0F * (1.0F - progress) * this.hitmarkerStrength);
        if (alpha <= 4) {
            return;
        }
        int color = (Math.min(255, alpha) << 24) | 0x00B044FF;
        int cx = sw / 2;
        int cy = sh / 2;
        int gap = 4 + (int) (progress * 6.0F);
        int len = 5;
        int step = FrameClock.deltaSeconds() > 0.0F ? 1 : 1;
        for (int k = 0; k < len; k += step) {
            int offset = gap + k;
            graphics.fill(cx + offset, cy + offset, cx + offset + 1, cy + offset + 2, color);
            graphics.fill(cx - offset - 1, cy + offset, cx - offset, cy + offset + 2, color);
            graphics.fill(cx + offset, cy - offset - 2, cx + offset + 1, cy - offset, color);
            graphics.fill(cx - offset - 1, cy - offset - 2, cx - offset, cy - offset, color);
        }
    }

    private void renderNumbers(GuiGraphics graphics, Minecraft mc, float partial, int sw, int sh) {
        for (int i = 0; i < this.numberCount; i++) {
            float age = this.numberAge[i];
            if (age > NUMBER_LIFETIME) {
                continue;
            }
            float progress = age / NUMBER_LIFETIME;
            if (!Projector.toScreen(this.numberX[i], this.numberY[i] + progress * 0.75D, this.numberZ[i],
                    partial, sw, sh, this.projected)) {
                continue;
            }
            int alpha = (int) (255.0F * Math.min(1.0F, (1.0F - progress) * 1.8F));
            int color = (Math.max(0, Math.min(255, alpha)) << 24) | 0x00FF9A3C;
            String text = "-" + String.format(java.util.Locale.ROOT, "%.1f", this.numberValue[i]);
            int x = (int) (this.projected[0] + this.numberSeed[i] * 14.0F) - mc.font.width(text) / 2;
            int y = (int) (this.projected[1] - progress * 16.0F);
            graphics.drawString(mc.font, text, x, y, color, true);
        }
    }
}
