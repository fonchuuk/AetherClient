package com.aetherglass.client.render.hud;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Рисует элементы экрана: каждому — своя «стеклянная» плашка с содержимым.
 *
 * <p>Плашки строятся кодом из текста и примитивов, поэтому выглядят одинаково при
 * любом масштабе интерфейса. В редакторе элементы рисуются тем же кодом — то, что
 * видит пользователь, и есть то, что он перетаскивает.</p>
 */
public final class HudPainter {
    private static final int PADDING = 5;
    private static final int LINE_GAP = 2;

    private static final int[] BOUNDS = new int[HudElementType.values().length * 4];

    private static float clickWindow;
    private static int clickCount;
    private static int clicksPerSecond;
    private static boolean attackWasDown;
    private static boolean useWasDown;
    private static boolean jumpWasDown;
    private static boolean sneakWasDown;

    private HudPainter() {
    }

    /** Обновляет счётчики, которые нельзя посчитать за один кадр. */
    public static void update(float deltaSeconds) {
        Minecraft client = Minecraft.getInstance();
        if (client.options != null) {
            boolean attack = client.options.keyAttack.isDown();
            boolean use = client.options.keyUse.isDown();
            if (attack && !attackWasDown) {
                clickCount++;
            }
            if (use && !useWasDown) {
                clickCount++;
            }
            attackWasDown = attack;
            useWasDown = use;
            jumpWasDown = client.options.keyJump.isDown();
            sneakWasDown = client.options.keyShift.isDown();
        }
        clickWindow += deltaSeconds;
        if (clickWindow >= 1.0F) {
            clicksPerSecond = Math.round(clickCount / clickWindow);
            clickCount = 0;
            clickWindow = 0.0F;
        }
    }

    public static int clicksPerSecond() {
        return clicksPerSecond;
    }

    /** Границы элемента, посчитанные при последней отрисовке: нужны редактору. */
    public static void lastBounds(HudElementType type, int[] target) {
        int base = type.ordinal() * 4;
        target[0] = BOUNDS[base];
        target[1] = BOUNDS[base + 1];
        target[2] = BOUNDS[base + 2];
        target[3] = BOUNDS[base + 3];
    }

    /**
     * Рисует все включённые элементы.
     *
     * @param editing      режим редактора: показываются и выключенные элементы
     * @param highlighted  элемент под курсором в редакторе
     */
    public static void render(GuiGraphics graphics, int width, int height,
                              Predicate<HudElementType> enabled, boolean editing, HudElementType highlighted) {
        Minecraft client = Minecraft.getInstance();
        Font font = client.font;
        for (HudElementType type : HudElementType.values()) {
            boolean active = enabled.test(type);
            if (!active && !editing) {
                BOUNDS[type.ordinal() * 4 + 2] = 0;
                BOUNDS[type.ordinal() * 4 + 3] = 0;
                continue;
            }
            List<String> lines = content(type, client);
            int panelWidth = 0;
            for (String line : lines) {
                panelWidth = Math.max(panelWidth, font.width(line));
            }
            int lineHeight = font.lineHeight;
            int panelHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * LINE_GAP + PADDING * 2;
            panelWidth += PADDING * 2;

            float anchorX = HudLayout.get().x(type);
            float anchorY = HudLayout.get().y(type);
            int x = Math.round(anchorX * width);
            int y = Math.round(anchorY * height);
            x = switch (type.alignment()) {
                case RIGHT -> x - panelWidth;
                case CENTER -> x - panelWidth / 2;
                default -> x;
            };
            x = Math.max(2, Math.min(width - panelWidth - 2, x));
            y = Math.max(2, Math.min(height - panelHeight - 2, y));

            int base = type.ordinal() * 4;
            BOUNDS[base] = x;
            BOUNDS[base + 1] = y;
            BOUNDS[base + 2] = panelWidth;
            BOUNDS[base + 3] = panelHeight;

            if (type == HudElementType.KEYSTROKES) {
                drawKeystrokes(graphics, font, x, y, panelWidth, panelHeight, client, active);
            } else {
                int accent = type == HudElementType.WATERMARK ? AuroraTheme.accent() : AuroraTheme.success();
                if (editing && !active) {
                    accent = AuroraTheme.textMuted();
                }
                drawPanel(graphics, font, x, y, panelWidth, panelHeight, lines, accent,
                        type == highlighted, active);
            }
            if (editing) {
                int outline = type == highlighted ? AuroraTheme.accent()
                        : ColorUtil.withAlpha(AuroraTheme.textMuted(), 150);
                DrawUtil.fillRoundedBordered(graphics, x, y, panelWidth, panelHeight, 6, 0, outline, 1);
                graphics.drawString(font, type.title(), x,
                        y - font.lineHeight - 2, ColorUtil.fade(AuroraTheme.textSecondary(), 0.9F), false);
            }
        }
    }

    private static void drawPanel(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                  List<String> lines, int accent, boolean highlighted, boolean active) {
        int radius = Math.min(8, height / 2);
        float glow = highlighted ? 0.9F : 0.25F;
        DrawUtil.drawGlow(graphics, x, y, width, height, radius, accent, active ? glow : 0.0F);
        DrawUtil.fillGlass(graphics, x, y, width, height, radius, 0.0F, highlighted ? 1.0F : 0.0F);
        // Акцентная полоса слева: по ней видно, что элемент «живой».
        DrawUtil.fillRounded(graphics, x + 1, y + 3, 2, Math.max(2, height - 6), 1,
                ColorUtil.fade(accent, active ? 0.9F : 0.4F));
        int lineY = y + PADDING;
        for (String line : lines) {
            graphics.drawString(font, line, x + PADDING + 4, lineY,
                    active ? AuroraTheme.textPrimary() : AuroraTheme.textMuted(), false);
            lineY += font.lineHeight + LINE_GAP;
        }
    }

    private static void drawKeystrokes(GuiGraphics graphics, Font font, int x, int y, int width, int height,
                                       Minecraft client, boolean active) {
        int radius = 6;
        DrawUtil.fillGlass(graphics, x, y, width, height, radius);
        int keySize = Math.min(20, height / 2 - 4);
        int centerX = x + PADDING;
        int centerY = y + PADDING;
        key(graphics, font, centerX + keySize + 2, centerY, keySize, "W", pressed(client, "forward"), active);
        key(graphics, font, centerX, centerY + keySize + 2, keySize, "A", pressed(client, "left"), active);
        key(graphics, font, centerX + keySize + 2, centerY + keySize + 2, keySize, "S", pressed(client, "back"),
                active);
        key(graphics, font, centerX + (keySize + 2) * 2, centerY + keySize + 2, keySize, "D",
                pressed(client, "right"), active);
        key(graphics, font, centerX, centerY + (keySize + 2) * 2, keySize * 2 + 2, "Shift", sneakWasDown, active);
        key(graphics, font, centerX + (keySize + 2) * 2, centerY + (keySize + 2) * 2, keySize, "Space", jumpWasDown,
                active);
        int mouseX = centerX + (keySize + 2) * 3 + 6;
        key(graphics, font, mouseX, centerY + keySize + 2, keySize + 4, "ЛКМ", attackWasDown, active);
        key(graphics, font, mouseX, centerY + (keySize + 2) * 2, keySize + 4, "ПКМ", useWasDown, active);
    }

    private static void key(GuiGraphics graphics, Font font, int x, int y, int size, String label, boolean pressed,
                            boolean active) {
        int accent = pressed ? AuroraTheme.accent() : AuroraTheme.textSecondary();
        int fill = pressed ? ColorUtil.fade(accent, active ? 0.55F : 0.25F)
                : ColorUtil.fade(AuroraTheme.glassTint(), 0.7F);
        DrawUtil.fillRoundedBordered(graphics, x, y, size, size, 4, fill,
                ColorUtil.fade(accent, pressed ? 0.9F : 0.35F), 1);
        int textWidth = font.width(label);
        graphics.drawString(font, label, x + (size - textWidth) / 2, y + (size - font.lineHeight) / 2,
                pressed ? AuroraTheme.textPrimary() : AuroraTheme.textSecondary(), false);
    }

    private static boolean pressed(Minecraft client, String movement) {
        if (client.options == null) {
            return false;
        }
        return switch (movement) {
            case "forward" -> client.options.keyUp.isDown();
            case "back" -> client.options.keyDown.isDown();
            case "left" -> client.options.keyLeft.isDown();
            case "right" -> client.options.keyRight.isDown();
            default -> false;
        };
    }

    // ───────────────────────────── содержимое ─────────────────────────────

    /** Текст элемента: одна строка на строку плашки. */
    private static List<String> content(HudElementType type, Minecraft client) {
        List<String> lines = new ArrayList<>(2);
        LocalPlayer player = client.player;
        switch (type) {
            case WATERMARK -> {
                lines.add("§lAetherGlass Client");
                lines.add("сборка " + com.aetherglass.client.AetherGlassClient.VERSION);
            }
            case FPS -> lines.add("FPS " + PerformanceProbe.fps());
            case PERFORMANCE -> {
                lines.add("процессор " + PerformanceProbe.cpuPercent() + "%");
                lines.add("память " + PerformanceProbe.usedMemoryMb() + " / " + PerformanceProbe.maxMemoryMb()
                        + " МБ (" + PerformanceProbe.memoryPercent() + "%)");
                int latency = PerformanceProbe.latency();
                lines.add(latency < 0 ? "пинг —" : "пинг " + latency + " мс");
            }
            case COORDINATES -> {
                if (player != null) {
                    Vec3 position = player.position();
                    lines.add(String.format(Locale.ROOT, "X %.1f  Y %.1f  Z %.1f", position.x, position.y,
                            position.z));
                } else {
                    lines.add("нет игрока");
                }
            }
            case DIRECTION -> {
                if (player != null) {
                    float yaw = ((player.getYRot() % 360.0F) + 360.0F) % 360.0F;
                    lines.add(compass(yaw) + "  " + Math.round(yaw) + "°");
                } else {
                    lines.add("—");
                }
            }
            case CLOCK -> {
                if (client.level != null) {
                    long time = client.level.getDayTime();
                    long hours = (time / 1000L + 6L) % 24L;
                    long minutes = time % 1000L * 60L / 1000L;
                    lines.add(String.format(Locale.ROOT, "%02d:%02d", hours, minutes));
                } else {
                    lines.add("—");
                }
            }
            case BIOME -> {
                if (client.level != null && player != null) {
                    Holder<Biome> biome = client.level.getBiome(player.blockPosition());
                    lines.add(biome.getRegisteredName());
                } else {
                    lines.add("—");
                }
            }
            case SESSION -> lines.add("в игре " + PerformanceProbe.sessionTime());
            case POTIONS -> {
                if (player == null) {
                    lines.add("—");
                    break;
                }
                List<MobEffectInstance> effects = new ArrayList<>(player.getActiveEffects());
                if (effects.isEmpty()) {
                    lines.add("эффектов нет");
                    break;
                }
                for (int index = 0; index < effects.size() && index < 4; index++) {
                    MobEffectInstance effect = effects.get(index);
                    Component name = effect.getEffect().value().getDisplayName();
                    lines.add(name.getString() + " " + (effect.getAmplifier() + 1) + " · "
                            + effect.getDuration() / 20 + " с");
                }
            }
            case TARGET -> {
                Entity target = client.crosshairPickEntity;
                if (target == null) {
                    lines.add("цели нет");
                    break;
                }
                lines.add(target.getDisplayName() == null ? target.getName().getString()
                        : target.getDisplayName().getString());
                if (target instanceof LivingEntity living) {
                    lines.add(String.format(Locale.ROOT, "%.0f / %.0f здоровья", living.getHealth(),
                            living.getMaxHealth()));
                }
            }
            case HELD_ITEM -> {
                if (player == null) {
                    lines.add("—");
                    break;
                }
                ItemStack stack = player.getMainHandItem();
                lines.add(stack.isEmpty() ? "пустая рука" : stack.getHoverName().getString());
                if (!stack.isEmpty() && stack.isDamageableItem()) {
                    lines.add("прочность " + (stack.getMaxDamage() - stack.getDamageValue()) + " / "
                            + stack.getMaxDamage());
                }
            }
            case EQUIPMENT -> {
                if (player == null) {
                    lines.add("—");
                    break;
                }
                ItemStack offhand = player.getOffhandItem();
                if (!offhand.isEmpty()) {
                    lines.add("второй рукой: " + offhand.getHoverName().getString());
                }
                lines.add("броня " + player.getArmorValue());
            }
            case ARROWS -> {
                if (player == null) {
                    lines.add("—");
                    break;
                }
                int arrows = 0;
                net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack stack = inventory.getItem(slot);
                    if (stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW)) {
                        arrows += stack.getCount();
                    }
                }
                lines.add("стрел " + arrows);
            }
            case KEYSTROKES -> lines.add("");
            case CPS -> lines.add("кликов в секунду " + clicksPerSecond());
            default -> lines.add("—");
        }
        if (lines.isEmpty()) {
            lines.add("—");
        }
        return lines;
    }

    private static String compass(float yaw) {
        String[] names = {"Ю", "ЮЗ", "З", "СЗ", "С", "СВ", "В", "ЮВ"};
        int index = Math.round(((yaw + 180.0F) % 360.0F) / 45.0F) % 8;
        return names[index];
    }

    /** Подсказка для редактора: что показывает элемент. */
    public static String description(HudElementType type) {
        return I18n.exists("aurora.hud." + type.settingsId())
                ? I18n.get("aurora.hud." + type.settingsId())
                : type.title();
    }

    /** Время кадра: используется редактором, чтобы не тянуть часы напрямую. */
    public static float frameDelta() {
        return FrameClock.deltaSeconds();
    }
}
