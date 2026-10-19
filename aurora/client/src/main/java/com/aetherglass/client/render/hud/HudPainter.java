package com.aetherglass.client.render.hud;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.input.InputTracker;
import com.aetherglass.client.input.KeybindManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.render.ColorController;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.EntityHitResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Отрисовка сгруппированных панелей HUD в стиле матового жидкого стекла
 * (Lunar / Badlion / Feather Frosted Glass, по образцу {@code image-2.png}).
 *
 * <p>Не содержит списка чит-модулей. Все показатели оформлены в полупрозрачные
 * дымчатые стеклянные линзы (25–30% непрозрачности) с мягкой внешней тенью,
 * молочной диффузией сверху и скользящим солнечным бликом без резких рамок.</p>
 */
public final class HudPainter {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final Item[] COOLDOWN_ITEMS = {
            Items.ENDER_PEARL,
            Items.GOLDEN_APPLE,
            Items.CHORUS_FRUIT,
            Items.WIND_CHARGE,
            Items.SHIELD
    };
    private static final String[] COOLDOWN_NAMES = {
            "Эндер-жемчуг",
            "Золотое яблоко",
            "Плод хоруса",
            "Заряд ветра",
            "Щит"
    };
    private static final float[] COOLDOWN_MAX_SECONDS = {
            1.0F, 2.0F, 1.0F, 0.5F, 5.0F
    };

    private static final Map<HudElementType, Box> LAST_BOXES = new EnumMap<>(HudElementType.class);
    private static final List<QuickButtonRect> QUICK_BUTTONS = new ArrayList<>(6);
    private static float smoothedHp = 20.0F;
    private static float smoothedTargetHp = 20.0F;

    public record Box(int x, int y, int width, int height) {
        public boolean contains(double mx, double my) {
            return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
        }
    }

    public record QuickButtonRect(int x, int y, int width, int height, String moduleId, boolean cleanScreenButton) {
        public boolean contains(double mx, double my) {
            return mx >= this.x && mx <= this.x + this.width && my >= this.y && my <= this.y + this.height;
        }
    }

    private HudPainter() {
    }

    public static Box box(HudElementType type) {
        return LAST_BOXES.get(type);
    }

    public static List<QuickButtonRect> quickButtons() {
        return QUICK_BUTTONS;
    }

    public static void render(GuiGraphics graphics, Minecraft client, HudModule module, boolean glass, boolean preview) {
        LAST_BOXES.clear();
        QUICK_BUTTONS.clear();

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        Font font = client.font;
        HudLayout layout = module.layout();

        for (HudElementType type : HudElementType.values()) {
            boolean shown = module.isShown(type);
            boolean legacySingleBadge = switch (type) {
                case FPS, CLOCK, DIRECTION, BIOME, SESSION, ARROWS, QUICK_ACCESS, EQUIPMENT, HELD_ITEM -> true;
                default -> false;
            };
            if (!shown && (!preview || legacySingleBadge)) {
                continue;
            }
            Box drawn = switch (type) {
                case WATERMARK -> drawTopInfoBar(graphics, font, client, layout, screenWidth, screenHeight, glass);
                case PERFORMANCE -> drawServerPerfBar(graphics, font, client, layout, screenWidth, screenHeight, glass);
                case FRIENDS -> drawFriendsPanel(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case POTIONS -> drawPotionsPanel(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case COOLDOWNS -> drawCooldownsPanel(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case COORDINATES -> drawCoordinatesCluster(graphics, font, client, layout, screenWidth, screenHeight, glass);
                case PLAYER_STATUS -> drawPlayerStatusCard(graphics, font, client, layout, screenWidth, screenHeight, glass);
                case TARGET -> drawTargetCard(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case KEYSTROKES -> drawKeystrokes(graphics, font, client, layout, screenWidth, screenHeight, glass);
                case CPS -> drawCpsPill(graphics, font, layout, screenWidth, screenHeight, glass);
                case QUICK_ACCESS -> drawQuickAccessBar(graphics, font, layout, screenWidth, screenHeight, glass);
                case EQUIPMENT -> drawEquipmentRow(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case HELD_ITEM -> drawHeldItem(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case ARROWS -> drawArrows(graphics, font, client, layout, screenWidth, screenHeight, glass, preview);
                case FPS -> drawSingleBadge(graphics, font, layout, type, screenWidth, screenHeight, glass,
                        "FPS", PerformanceProbe.fps() + " fps");
                case CLOCK -> drawSingleBadge(graphics, font, layout, type, screenWidth, screenHeight, glass,
                        "Время", LocalTime.now().format(CLOCK_FORMAT));
                case DIRECTION -> drawSingleBadge(graphics, font, layout, type, screenWidth, screenHeight, glass,
                        "Курс", directionLabel(client.player));
                case BIOME -> drawSingleBadge(graphics, font, layout, type, screenWidth, screenHeight, glass,
                        "Биом", biomeLabel(client));
                case SESSION -> drawSingleBadge(graphics, font, layout, type, screenWidth, screenHeight, glass,
                        "Сессия", formatDuration(module.sessionSeconds()));
            };
            if (drawn != null) {
                if (preview && !shown) {
                    DrawUtil.drawFrostedPill(graphics, drawn.x(), drawn.y(), drawn.width(), drawn.height(),
                            Math.max(5, AuroraTheme.radiusPx() - 2), 0.55F, 0.0F, 0.0F);
                    String hiddenTag = "СКРЫТО (ПКМ)";
                    int tw = font.width(hiddenTag);
                    graphics.drawString(font, hiddenTag,
                            drawn.x() + (drawn.width() - tw) / 2,
                            drawn.y() + (drawn.height() - 8) / 2,
                            0xFFB8C4D8, false);
                }
                LAST_BOXES.put(type, drawn);
            }
        }
    }

    // ─────────────────── 1. Верхний левый Инфо-бар (image-2.png) ───────────────────

    private static Box drawTopInfoBar(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                      int sw, int sh, boolean glass) {
        String brand = "✦ AETHER";
        String player = client.player != null ? client.player.getGameProfile().name() : client.getUser().getName();
        String fps = PerformanceProbe.fps() + " fps";
        String ping = getPlayerPing(client) + " ms";
        String clock = LocalTime.now().format(CLOCK_FORMAT);

        String[] segments = {brand, player, fps, ping, clock};
        int padX = 7;
        int segGap = 6;
        int totalW = 8;
        for (int i = 0; i < segments.length; i++) {
            totalW += font.width(segments[i]) + padX * 2;
            if (i < segments.length - 1) {
                totalW += segGap;
            }
        }
        int h = 20;
        int x = layout.pixelX(HudElementType.WATERMARK, sw, totalW);
        int y = layout.pixelY(HudElementType.WATERMARK, sh, h);

        drawGlassPlate(graphics, x, y, totalW, h, glass);

        int curX = x + 4;
        float cleanAlpha = KeybindManager.get().hudVisibility();
        int accent = ColorController.accent();
        for (int i = 0; i < segments.length; i++) {
            String seg = segments[i];
            int segW = font.width(seg) + padX * 2;
            if (i == 0) {
                DrawUtil.drawFrostedPill(graphics, curX, y + 3, segW, h - 6, 5, cleanAlpha, 0.2F, 0.45F);
                graphics.drawString(font, seg, curX + padX, y + 6,
                        ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
            } else {
                graphics.fill(curX - segGap / 2, y + 5, curX - segGap / 2 + 1, y + h - 5,
                        ColorUtil.withAlpha(0xFFE4F4FF, Math.round(28 * cleanAlpha)));
                int col = (i == 2 || i == 3) ? accent : AuroraTheme.textPrimary();
                graphics.drawString(font, seg, curX + padX, y + 6,
                        ColorUtil.fade(col, cleanAlpha), false);
            }
            curX += segW + segGap;
        }
        return new Box(x, y, totalW, h);
    }

    private static Box drawServerPerfBar(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                         int sw, int sh, boolean glass) {
        String server = (client.getCurrentServer() != null && client.getCurrentServer().ip != null)
                ? client.getCurrentServer().ip
                : (client.isLocalServer() ? "local" : "menu");
        String tps = "20.0 tps";
        String cpuRam = PerformanceProbe.cpuPercent() + "% CPU · " + PerformanceProbe.usedMemoryMb() + "MB";

        String leftPart = "🌐 " + server + " · " + tps;
        String rightPart = "⚙ " + cpuRam;
        int w1 = font.width(leftPart) + 12;
        int w2 = font.width(rightPart) + 12;
        int totalW = w1 + 4 + w2;
        int h = 18;
        int x = layout.pixelX(HudElementType.PERFORMANCE, sw, totalW);
        int y = layout.pixelY(HudElementType.PERFORMANCE, sh, h);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, w1, h, glass);
        graphics.drawString(font, leftPart, x + 6, y + 5,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);

        drawGlassPlate(graphics, x + w1 + 4, y, w2, h, glass);
        graphics.drawString(font, rightPart, x + w1 + 10, y + 5,
                ColorUtil.fade(AuroraTheme.textSecondary(), cleanAlpha), false);

        return new Box(x, y, totalW, h);
    }

    // ─────────────────── 2. Левая панель: Игроки рядом / Друзья (image-2.png) ───────────────────

    private static Box drawFriendsPanel(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                        int sw, int sh, boolean glass, boolean preview) {
        List<String> rows = new ArrayList<>();
        List<String> badges = new ArrayList<>();

        if (client.level != null && client.player != null) {
            for (AbstractClientPlayer other : client.level.players()) {
                if (other == client.player) {
                    continue;
                }
                int dist = Math.round(other.distanceTo(client.player));
                int hp = Math.round(other.getHealth());
                rows.add(other.getGameProfile().name());
                badges.add(hp + "hp · " + dist + "m");
                if (rows.size() >= 5) {
                    break;
                }
            }
        }
        if (rows.isEmpty()) {
            String selfName = client.player != null ? client.player.getGameProfile().name() : client.getUser().getName();
            rows.add(selfName);
            badges.add("online · " + getPlayerPing(client) + "ms");
            if (preview) {
                rows.add("AuroraFriend");
                badges.add("20hp · 6m");
            }
        }

        String header = "👥 Игроки рядом";
        String countBadge = Integer.toString(rows.size());
        int width = Math.max(126, font.width(header) + 38);
        for (int i = 0; i < rows.size(); i++) {
            int rowW = font.width(rows.get(i)) + font.width(badges.get(i)) + 26;
            width = Math.max(width, rowW);
        }
        int rowH = 14;
        int height = 22 + rows.size() * rowH + 4;
        int x = layout.pixelX(HudElementType.FRIENDS, sw, width);
        int y = layout.pixelY(HudElementType.FRIENDS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, width, height, glass);
        int accent = ColorController.accent();
        graphics.drawString(font, header, x + 7, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        int cbW = font.width(countBadge) + 8;
        DrawUtil.fillRounded(graphics, x + width - cbW - 6, y + 4, cbW, 12, 4,
                ColorUtil.fade(accent, 0.20F * cleanAlpha));
        graphics.drawString(font, countBadge, x + width - cbW - 2, y + 6,
                ColorUtil.fade(accent, cleanAlpha), false);
        graphics.fill(x + 7, y + 18, x + width - 7, y + 19,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * cleanAlpha)));

        int ry = y + 22;
        for (int i = 0; i < rows.size(); i++) {
            DrawUtil.fillRounded(graphics, x + 7, ry + 3, 5, 5, 2,
                    ColorUtil.fade(AuroraTheme.success(), cleanAlpha));
            graphics.drawString(font, rows.get(i), x + 16, ry + 2,
                    ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
            String b = badges.get(i);
            graphics.drawString(font, b, x + width - font.width(b) - 7, ry + 2,
                    ColorUtil.fade(AuroraTheme.textSecondary(), cleanAlpha), false);
            ry += rowH;
        }
        return new Box(x, y, width, height);
    }

    // ─────────────────── 3. Правая панель: Зелья и Перезарядки (image-2.png) ───────────────────

    private static Box drawPotionsPanel(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                        int sw, int sh, boolean glass, boolean preview) {
        LocalPlayer player = client.player;
        Collection<MobEffectInstance> active = player != null ? player.getActiveEffects() : List.of();

        List<String> names = new ArrayList<>();
        List<String> times = new ArrayList<>();
        for (MobEffectInstance inst : active) {
            String label = inst.getEffect().value().getDisplayName().getString();
            if (inst.getAmplifier() > 0) {
                label += " " + toRoman(inst.getAmplifier() + 1);
            }
            names.add(label);
            times.add(formatDuration(inst.getDuration() / 20L));
            if (names.size() >= 6) {
                break;
            }
        }
        if (names.isEmpty()) {
            if (preview) {
                names.add("Скорость II");
                times.add("1:22");
                names.add("Сила II");
                times.add("0:35");
            } else {
                names.add("Нет эффектов");
                times.add("—");
            }
        }

        String header = "🧪 Зелья";
        int width = 124;
        for (int i = 0; i < names.size(); i++) {
            width = Math.max(width, font.width(names.get(i)) + font.width(times.get(i)) + 28);
        }
        int rowH = 15;
        int height = 22 + names.size() * rowH + 4;
        int x = layout.pixelX(HudElementType.POTIONS, sw, width);
        int y = layout.pixelY(HudElementType.POTIONS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, width, height, glass);
        int accent = ColorController.accent();
        graphics.drawString(font, header, x + 7, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        graphics.fill(x + 7, y + 18, x + width - 7, y + 19,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * cleanAlpha)));

        int ry = y + 22;
        for (int i = 0; i < names.size(); i++) {
            graphics.drawString(font, names.get(i), x + 7, ry + 2,
                    ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
            String t = times.get(i);
            int tw = font.width(t) + 8;
            int tx = x + width - tw - 6;
            DrawUtil.fillRounded(graphics, tx, ry, tw, 12, 4,
                    ColorUtil.fade(accent, 0.18F * cleanAlpha));
            graphics.drawString(font, t, tx + 4, ry + 2,
                    ColorUtil.fade(accent, cleanAlpha), false);
            ry += rowH;
        }
        return new Box(x, y, width, height);
    }

    private static Box drawCooldownsPanel(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                          int sw, int sh, boolean glass, boolean preview) {
        LocalPlayer player = client.player;
        List<ItemStack> shownStacks = new ArrayList<>();
        List<String> shownNames = new ArrayList<>();
        List<Float> shownProgress = new ArrayList<>();
        List<String> shownTimers = new ArrayList<>();

        if (player != null) {
            for (int i = 0; i < COOLDOWN_ITEMS.length; i++) {
                Item item = COOLDOWN_ITEMS[i];
                ItemStack stack = new ItemStack(item);
                float cd = player.getCooldowns().getCooldownPercent(stack, 0.0F);
                boolean hasInInv = hasItemInInventory(player, item);
                if (cd > 0.005F) {
                    shownStacks.add(stack);
                    shownNames.add(COOLDOWN_NAMES[i]);
                    shownProgress.add(cd);
                    float secs = Math.max(0.1F, cd * COOLDOWN_MAX_SECONDS[i] * 15.0F);
                    shownTimers.add(String.format(Locale.ROOT, "%.1fс", secs));
                } else if (hasInInv && shownStacks.size() < 3) {
                    shownStacks.add(stack);
                    shownNames.add(COOLDOWN_NAMES[i]);
                    shownProgress.add(0.0F);
                    shownTimers.add("Готово");
                }
            }
        }

        if (shownStacks.isEmpty()) {
            shownStacks.add(new ItemStack(Items.ENDER_PEARL));
            shownNames.add("Эндер-жемчуг");
            shownProgress.add(preview ? 0.65F : 0.0F);
            shownTimers.add(preview ? "9.8с" : "Готово");

            shownStacks.add(new ItemStack(Items.GOLDEN_APPLE));
            shownNames.add("Золотое яблоко");
            shownProgress.add(preview ? 0.35F : 0.0F);
            shownTimers.add(preview ? "1.4с" : "Готово");

            shownStacks.add(new ItemStack(Items.CHORUS_FRUIT));
            shownNames.add("Плод хоруса");
            shownProgress.add(preview ? 0.80F : 0.0F);
            shownTimers.add(preview ? "0.8с" : "Готово");
        }

        String header = "⏳ Перезарядки";
        int width = 136;
        for (int i = 0; i < shownNames.size(); i++) {
            width = Math.max(width, 22 + font.width(shownNames.get(i)) + font.width(shownTimers.get(i)) + 26);
        }
        int rowH = 18;
        int height = 22 + shownNames.size() * rowH + 4;
        int x = layout.pixelX(HudElementType.COOLDOWNS, sw, width);
        int y = layout.pixelY(HudElementType.COOLDOWNS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, width, height, glass);
        int accent = ColorController.accent();
        graphics.drawString(font, header, x + 7, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        graphics.fill(x + 7, y + 18, x + width - 7, y + 19,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * cleanAlpha)));

        int ry = y + 21;
        for (int i = 0; i < shownNames.size(); i++) {
            graphics.renderItem(shownStacks.get(i), x + 5, ry);
            graphics.drawString(font, shownNames.get(i), x + 23, ry + 3,
                    ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);

            float prog = shownProgress.get(i);
            String timer = shownTimers.get(i);
            int tw = font.width(timer) + 8;
            int tx = x + width - tw - 6;
            int pillCol = prog > 0.01F ? accent : AuroraTheme.success();
            DrawUtil.fillRounded(graphics, tx, ry + 2, tw, 12, 4,
                    ColorUtil.fade(pillCol, 0.18F * cleanAlpha));
            graphics.drawString(font, timer, tx + 4, ry + 4,
                    ColorUtil.fade(pillCol, cleanAlpha), false);

            if (prog > 0.01F) {
                int barMax = width - 30;
                int barW = Math.max(2, Math.round(barMax * prog));
                DrawUtil.fillRounded(graphics, x + 23, ry + 14, barW, 2, 1,
                        ColorUtil.fade(accent, 0.82F * cleanAlpha));
            }
            ry += rowH;
        }
        return new Box(x, y, width, height);
    }

    // ─────────────────── 4. Снизу слева: Координаты, BPS и Направление (image-2.png) ───────────────────

    private static Box drawCoordinatesCluster(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                              int sw, int sh, boolean glass) {
        LocalPlayer p = client.player;
        double bps = 0.0D;
        String coordsText = "XYZ: 0, 0, 0";
        if (p != null) {
            double dx = p.getX() - p.xo;
            double dz = p.getZ() - p.zo;
            bps = Math.hypot(dx, dz) * 20.0D;
            coordsText = String.format(Locale.ROOT, "XYZ: %d, %d, %d",
                    p.getBlockX(), p.getBlockY(), p.getBlockZ());
        }
        String topText = String.format(Locale.ROOT, "⚡ %.2f bps  ·  🧭 %s", bps, directionLabel(p));
        int topW = font.width(topText) + 14;
        int botW = font.width("📍 " + coordsText) + 14;
        int width = Math.max(topW, botW);
        int height = 38;
        int x = layout.pixelX(HudElementType.COORDINATES, sw, width);
        int y = layout.pixelY(HudElementType.COORDINATES, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, topW, 17, glass);
        graphics.drawString(font, topText, x + 7, y + 5,
                ColorUtil.fade(AuroraTheme.textSecondary(), cleanAlpha), false);

        drawGlassPlate(graphics, x, y + 19, botW, 18, glass);
        graphics.drawString(font, "📍 " + coordsText, x + 7, y + 24,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);

        return new Box(x, y, width, height);
    }

    // ─────────────────── 5. По центру: Статус игрока (HP, Броня, Сытость, image-2.png) ───────────────────

    private static Box drawPlayerStatusCard(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                            int sw, int sh, boolean glass) {
        LocalPlayer p = client.player;
        float hp = p != null ? p.getHealth() : 20.0F;
        float maxHp = p != null ? Math.max(1.0F, p.getMaxHealth()) : 20.0F;
        float absorption = p != null ? p.getAbsorptionAmount() : 0.0F;
        int armor = p != null ? p.getArmorValue() : 20;
        int food = p != null ? p.getFoodData().getFoodLevel() : 20;
        String name = p != null ? p.getGameProfile().name() : client.getUser().getName();

        float dt = FrameClock.deltaSeconds();
        smoothedHp += (hp - smoothedHp) * Math.min(1.0F, dt * 12.0F);

        int width = 154;
        int height = 34;
        int x = layout.pixelX(HudElementType.PLAYER_STATUS, sw, width);
        int y = layout.pixelY(HudElementType.PLAYER_STATUS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, width, height, glass);
        int accent = ColorController.accent();

        // Стеклянный каплевидный аватар-линза слева (без резкой рамки)
        DrawUtil.drawFrostedPill(graphics, x + 5, y + 5, 24, 24, 7, cleanAlpha, 0.25F, 0.35F);
        String initial = name.isEmpty() ? "A" : name.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawString(font, initial, x + 17 - font.width(initial) / 2, y + 13,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);

        graphics.drawString(font, name, x + 34, y + 5,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        String hpLabel = String.format(Locale.ROOT, "%.1f HP", hp + absorption);
        graphics.drawString(font, hpLabel, x + width - font.width(hpLabel) - 6, y + 5,
                ColorUtil.fade(AuroraTheme.success(), cleanAlpha), false);

        int barX = x + 34;
        int barW = width - 40;
        int trackCol = ColorUtil.withAlpha(0xFF141E30, Math.round(78 * cleanAlpha));
        float hpRatio = Math.max(0.0F, Math.min(1.0F, smoothedHp / maxHp));
        DrawUtil.fillRounded(graphics, barX, y + 17, barW, 5, 2, trackCol);
        int hpFillW = Math.max(3, Math.round(barW * hpRatio));
        int hpColor = hpRatio > 0.5F ? AuroraTheme.success() : (hpRatio > 0.25F ? 0xFFFFB84D : AuroraTheme.danger());
        DrawUtil.fillRounded(graphics, barX, y + 17, hpFillW, 5, 2, ColorUtil.fade(hpColor, cleanAlpha));

        float armorRatio = Math.max(0.0F, Math.min(1.0F, armor / 20.0F));
        float foodRatio = Math.max(0.0F, Math.min(1.0F, food / 20.0F));
        int halfW = (barW - 4) / 2;
        DrawUtil.fillRounded(graphics, barX, y + 25, halfW, 4, 2, trackCol);
        if (armorRatio > 0.01F) {
            DrawUtil.fillRounded(graphics, barX, y + 25, Math.max(2, Math.round(halfW * armorRatio)), 4, 2,
                    ColorUtil.fade(accent, cleanAlpha));
        }
        DrawUtil.fillRounded(graphics, barX + halfW + 4, y + 25, halfW, 4, 2, trackCol);
        if (foodRatio > 0.01F) {
            DrawUtil.fillRounded(graphics, barX + halfW + 4, y + 25, Math.max(2, Math.round(halfW * foodRatio)), 4, 2,
                    ColorUtil.fade(0xFFFFB35C, cleanAlpha));
        }

        return new Box(x, y, width, height);
    }

    // ─────────────────── 6. Карточка цели (Target HUD) ───────────────────

    private static Box drawTargetCard(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                      int sw, int sh, boolean glass, boolean preview) {
        LivingEntity living = null;
        if (client.hitResult instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity candidate) {
            living = candidate;
        }
        if (living == null && !preview) {
            return null;
        }
        String name = living != null ? living.getName().getString() : "Цель (Target)";
        float health = living != null ? living.getHealth() : 16.0F;
        float maxHealth = living != null ? Math.max(1.0F, living.getMaxHealth()) : 20.0F;
        float dist = (living != null && client.player != null) ? living.distanceTo(client.player) : 3.2F;

        float dt = FrameClock.deltaSeconds();
        smoothedTargetHp += (health - smoothedTargetHp) * Math.min(1.0F, dt * 12.0F);
        float ratio = Math.max(0.0F, Math.min(1.0F, smoothedTargetHp / maxHealth));

        int width = Math.max(134, font.width(name) + 52);
        int height = 34;
        int x = layout.pixelX(HudElementType.TARGET, sw, width);
        int y = layout.pixelY(HudElementType.TARGET, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, width, height, glass);
        int accent = ColorController.accent();
        graphics.drawString(font, name, x + 7, y + 5,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        String distText = String.format(Locale.ROOT, "%.1fm", dist);
        graphics.drawString(font, distText, x + width - font.width(distText) - 7, y + 5,
                ColorUtil.fade(AuroraTheme.textSecondary(), cleanAlpha), false);

        String hpText = String.format(Locale.ROOT, "%.1f / %.0f HP", health, maxHealth);
        graphics.drawString(font, hpText, x + 7, y + 15,
                ColorUtil.fade(accent, cleanAlpha), false);

        int barW = width - 14;
        DrawUtil.fillRounded(graphics, x + 7, y + 25, barW, 4, 2,
                ColorUtil.withAlpha(0xFF141E30, Math.round(78 * cleanAlpha)));
        DrawUtil.fillRounded(graphics, x + 7, y + 25, Math.max(3, Math.round(barW * ratio)), 4, 2,
                ColorUtil.fade(accent, cleanAlpha));

        return new Box(x, y, width, height);
    }

    // ─────────────────── 7. Keystrokes и событийный CPS ───────────────────

    private static Box drawKeystrokes(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                      int sw, int sh, boolean glass) {
        int keySize = 20;
        int gap = 2;
        int width = keySize * 3 + gap * 2;
        int height = keySize * 2 + gap * 2 + 16;
        int x = layout.pixelX(HudElementType.KEYSTROKES, sw, width);
        int y = layout.pixelY(HudElementType.KEYSTROKES, sh, height);

        boolean w = client.options.keyUp.isDown();
        boolean a = client.options.keyLeft.isDown();
        boolean s = client.options.keyDown.isDown();
        boolean d = client.options.keyRight.isDown();
        boolean lmb = client.options.keyAttack.isDown();
        boolean rmb = client.options.keyUse.isDown();

        drawKeyCap(graphics, font, x + keySize + gap, y, keySize, keySize, "W", w, glass);
        drawKeyCap(graphics, font, x, y + keySize + gap, keySize, keySize, "A", a, glass);
        drawKeyCap(graphics, font, x + keySize + gap, y + keySize + gap, keySize, keySize, "S", s, glass);
        drawKeyCap(graphics, font, x + (keySize + gap) * 2, y + keySize + gap, keySize, keySize, "D", d, glass);

        int half = (width - gap) / 2;
        int rowY = y + (keySize + gap) * 2;
        int lCps = InputTracker.get().leftCps();
        int rCps = InputTracker.get().rightCps();
        drawKeyCap(graphics, font, x, rowY, half, 16, "LMB " + lCps, lmb, glass);
        drawKeyCap(graphics, font, x + half + gap, rowY, width - half - gap, 16, "RMB " + rCps, rmb, glass);
        return new Box(x, y, width, height);
    }

    private static void drawKeyCap(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                   String text, boolean pressed, boolean glass) {
        int radius = Math.max(4, Math.min(7, AuroraTheme.radiusPx() / 2));
        float cleanAlpha = KeybindManager.get().hudVisibility();
        if (glass) {
            DrawUtil.drawFrostedPill(graphics, x, y, width, height, radius,
                    cleanAlpha, pressed ? 0.9F : 0.0F, pressed ? 0.85F : 0.0F);
        } else {
            int fill = pressed ? ColorUtil.fade(ColorController.accent(), 0.55F * cleanAlpha)
                    : ColorUtil.fade(AuroraTheme.glassTint(), cleanAlpha);
            DrawUtil.fillRounded(graphics, x, y, width, height, radius, fill);
        }
        int color = pressed ? 0xFFFFFFFF : AuroraTheme.textPrimary();
        graphics.drawString(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2,
                ColorUtil.fade(color, cleanAlpha), false);
    }

    private static Box drawCpsPill(GuiGraphics graphics, Font font, HudLayout layout,
                                   int sw, int sh, boolean glass) {
        int left = InputTracker.get().leftCps();
        int right = InputTracker.get().rightCps();
        String text = "🖱 CPS: " + left + " | " + right;
        int width = font.width(text) + 14;
        int height = 18;
        int x = layout.pixelX(HudElementType.CPS, sw, width);
        int y = layout.pixelY(HudElementType.CPS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();
        drawGlassPlate(graphics, x, y, width, height, glass);
        graphics.drawString(font, text, x + 7, y + 5,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        return new Box(x, y, width, height);
    }

    // ─────────────────── 8. Быстрый доступ (Quick Access) ───────────────────

    private static Box drawQuickAccessBar(GuiGraphics graphics, Font font, HudLayout layout,
                                          int sw, int sh, boolean glass) {
        String[][] items = {
                {"blocks", "✦ Блоки"},
                {"hands", "✋ Руки"},
                {"particles", "❄ Частицы"},
                {"sky", "☀ Небо"},
                {"rainbow", "🌈 Перелив"},
                {"CLEAN", "📷 Экран"}
        };
        int gap = 3;
        int padX = 5;
        int totalW = 6;
        for (int i = 0; i < items.length; i++) {
            totalW += font.width(items[i][1]) + padX * 2;
            if (i < items.length - 1) {
                totalW += gap;
            }
        }
        int height = 18;
        int x = layout.pixelX(HudElementType.QUICK_ACCESS, sw, totalW);
        int y = layout.pixelY(HudElementType.QUICK_ACCESS, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();

        drawGlassPlate(graphics, x, y, totalW, height, glass);

        int curX = x + 3;
        for (String[] item : items) {
            String modId = item[0];
            String label = item[1];
            int btnW = font.width(label) + padX * 2;
            boolean isClean = "CLEAN".equals(modId);
            boolean active;
            if (isClean) {
                active = KeybindManager.get().isCleanScreenActive();
            } else {
                Module m = ModuleManager.get().module(modId);
                active = m != null && m.isEnabled();
            }

            if (active) {
                DrawUtil.drawFrostedPill(graphics, curX, y + 2, btnW, height - 4, 4, cleanAlpha, 0.25F, 0.55F);
            }
            graphics.drawString(font, label, curX + padX, y + 5,
                    ColorUtil.fade(active ? AuroraTheme.textPrimary() : AuroraTheme.textSecondary(), cleanAlpha), false);

            QUICK_BUTTONS.add(new QuickButtonRect(curX, y + 2, btnW, height - 4, modId, isClean));
            curX += btnW + gap;
        }
        return new Box(x, y, totalW, height);
    }

    // ─────────────────── 9. Экипировка, предмет в руке и стрелы ───────────────────

    private static Box drawEquipmentRow(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                        int sw, int sh, boolean glass, boolean preview) {
        LocalPlayer player = client.player;
        List<ItemStack> equipped = new ArrayList<>(4);
        if (player != null) {
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    equipped.add(stack);
                }
            }
        }
        if (equipped.isEmpty() && preview) {
            equipped.add(new ItemStack(Items.DIAMOND_HELMET));
            equipped.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
        }
        if (equipped.isEmpty()) {
            return null;
        }
        int cell = 20;
        int width = equipped.size() * cell + 6;
        int height = 22;
        int x = layout.pixelX(HudElementType.EQUIPMENT, sw, width);
        int y = layout.pixelY(HudElementType.EQUIPMENT, sh, height);
        drawGlassPlate(graphics, x, y, width, height, glass);
        int ix = x + 3;
        for (ItemStack stack : equipped) {
            graphics.renderItem(stack, ix, y + 3);
            ix += cell;
        }
        return new Box(x, y, width, height);
    }

    private static Box drawHeldItem(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                    int sw, int sh, boolean glass, boolean preview) {
        LocalPlayer player = client.player;
        ItemStack stack = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        if (stack.isEmpty() && !preview) {
            return null;
        }
        String name = stack.isEmpty() ? "Алмазный меч" : stack.getHoverName().getString();
        int width = 24 + font.width(name) + 8;
        int height = 20;
        int x = layout.pixelX(HudElementType.HELD_ITEM, sw, width);
        int y = layout.pixelY(HudElementType.HELD_ITEM, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();
        drawGlassPlate(graphics, x, y, width, height, glass);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 4, y + 2);
        }
        graphics.drawString(font, name, x + 22, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        return new Box(x, y, width, height);
    }

    private static Box drawArrows(GuiGraphics graphics, Font font, Minecraft client, HudLayout layout,
                                  int sw, int sh, boolean glass, boolean preview) {
        LocalPlayer player = client.player;
        int arrows = 0;
        if (player != null) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW)) {
                    arrows += stack.getCount();
                }
            }
        }
        return drawSingleBadge(graphics, font, layout, HudElementType.ARROWS, sw, sh, glass, "Стрелы", Integer.toString(arrows));
    }

    private static Box drawSingleBadge(GuiGraphics graphics, Font font, HudLayout layout, HudElementType type,
                                       int sw, int sh, boolean glass, String label, String value) {
        String text = label + ": " + value;
        int width = font.width(text) + 14;
        int height = 18;
        int x = layout.pixelX(type, sw, width);
        int y = layout.pixelY(type, sh, height);
        float cleanAlpha = KeybindManager.get().hudVisibility();
        drawGlassPlate(graphics, x, y, width, height, glass);
        graphics.drawString(font, label + ": ", x + 7, y + 5,
                ColorUtil.fade(AuroraTheme.textSecondary(), cleanAlpha), false);
        graphics.drawString(font, value, x + 7 + font.width(label + ": "), y + 5,
                ColorUtil.fade(AuroraTheme.textPrimary(), cleanAlpha), false);
        return new Box(x, y, width, height);
    }

    // ─────────────────── общие утилиты ───────────────────

    private static void drawGlassPlate(GuiGraphics graphics, int x, int y, int width, int height, boolean glass) {
        int radius = Math.max(5, Math.min(12, Math.round(AuroraTheme.radius() * 0.85F)));
        float cleanAlpha = KeybindManager.get().hudVisibility();
        if (glass) {
            DrawUtil.drawLiquidGlassCard(graphics, x, y, width, height, radius, cleanAlpha, false);
        } else {
            DrawUtil.fillRounded(graphics, x, y, width, height, radius, ColorUtil.fade(AuroraTheme.glassTint(), cleanAlpha));
        }
    }

    private static int getPlayerPing(Minecraft client) {
        if (client.player != null && client.getConnection() != null) {
            PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
            if (info != null) {
                return Math.max(0, info.getLatency());
            }
        }
        return 0;
    }

    private static boolean hasItemInInventory(LocalPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item)) {
                return true;
            }
        }
        return false;
    }

    private static String directionLabel(LocalPlayer p) {
        if (p == null) {
            return "N";
        }
        float yaw = ((p.getYRot() % 360.0F) + 360.0F) % 360.0F;
        String[] names = {"S (+Z)", "SW", "W (-X)", "NW", "N (-Z)", "NE", "E (+X)", "SE"};
        int idx = Math.round(yaw / 45.0F) & 7;
        return names[idx];
    }

    private static String biomeLabel(Minecraft client) {
        if (client.level == null || client.player == null) {
            return "—";
        }
        Holder<Biome> holder = client.level.getBiome(client.player.blockPosition());
        return holder.unwrapKey().map(k -> {
            Identifier loc = k.identifier();
            return loc.getPath().replace('_', ' ');
        }).orElse("равнина");
    }

    private static String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> Integer.toString(number);
        };
    }

    private static String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }
}
