package com.aetherglass.client.modules.impl;

import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.util.Projector;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Полоски здоровья и ник над мобами и игроками: видно, сколько осталось, без наведения.
 *
 * <p>Рендер 2D в HUD: точка над существом проецируется на экран, полоска рисуется
 * только для видимых целей в 48 блоках. Никаких аллокаций в кадре — один общий буфер
 * на проекцию и готовые цвета.</p>
 */
public final class MobInfoModule extends Module {
    private static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath("aetherglass", "mob_info");

    private final BooleanSetting healthBars = register(new BooleanSetting("healthBars",
            "Полоски HP над мобами", "Показывать здоровье мобов и игроков", true, this::noop));
    private final BooleanSetting names = register(new BooleanSetting("names",
            "Имена над существами", "Показывать имя и расстояние до существа (по умолчанию выключено)", false, this::noop));
    private final BooleanSetting playersOnly = register(new BooleanSetting("playersOnly",
            "Только игроки", "Показывать полоски только над игроками", false, this::noop));

    private final float[] projected = new float[3];
    private boolean installed;
    private boolean renderBroken;

    public MobInfoModule() {
        super("mob_info", "Инфо о существах",
                "Полоски здоровья и имена над мобами и игроками",
                ModuleCategory.HUD);
    }

    private void noop(Setting<?> setting) {
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    public void install() {
        if (this.installed) {
            return;
        }
        this.installed = true;
        HudElementRegistry.addLast(LAYER_ID, this::render);
    }

    private void render(GuiGraphics graphics, net.minecraft.client.DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || this.renderBroken || mc.options == null || mc.options.hideGui || mc.level == null) {
            return;
        }
        try {
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            boolean onlyPlayers = this.playersOnly.isEnabled();
            boolean withNames = this.names.isEnabled();
            boolean withBars = this.healthBars.isEnabled();

            int drawn = 0;
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (drawn >= 8) {
                    break;
                }
                if (!(entity instanceof LivingEntity living) || entity == mc.player) {
                    continue;
                }
                if (onlyPlayers && !(entity instanceof Player)) {
                    continue;
                }
                double distance = living.distanceToSqr(mc.player);
                if (distance > 18.0D * 18.0D) {
                    continue;
                }
                float height = living.getBbHeight() + 0.55F;
                if (!Projector.toScreen(living.getX(), living.getY() + height, living.getZ(), partial, sw, sh, this.projected)) {
                    continue;
                }
                int x = (int) this.projected[0];
                int y = (int) this.projected[1];
                float maxHealth = Math.max(1.0F, living.getMaxHealth());
                float ratio = Math.max(0.0F, Math.min(1.0F, living.getHealth() / maxHealth));

                if (withNames) {
                    String label = living.getName().getString();
                    if (entity instanceof Player) {
                        label = label + "  " + (int) Math.sqrt(distance) + "м";
                    }
                    graphics.drawString(mc.font, label, x - mc.font.width(label) / 2, y - 9, 0xFFE9DCFF, true);
                }
                if (withBars) {
                    int width = 42;
                    int barX = x - width / 2;
                    int filled = Math.round(width * ratio);
                    graphics.fill(barX - 1, y - 1, barX + width + 1, y + 4, 0x8A120A1E);
                    int barColor = ratio > 0.6F ? 0xFF4BE38A : (ratio > 0.3F ? 0xFFFFC24B : 0xFFFF5C6E);
                    graphics.fill(barX, y, barX + filled, y + 3, barColor);
                }
                drawn++;
            }
        } catch (Throwable throwable) {
            this.renderBroken = true;
        }
    }
}
