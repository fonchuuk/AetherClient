package com.aetherglass.client.gui.screen;

import com.aetherglass.client.account.AccountManager;
import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.menu.BackdropGallery;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.input.KeybindManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.modules.impl.ParticleModule;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.particle.ParticlePreset;
import com.aetherglass.client.render.particle.ParticleShape;
import com.aetherglass.client.render.shader.GuiShaderPreviewRenderer;
import com.aetherglass.client.render.shader.ShaderEffect;
import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Единое матовое стеклянное окно модулей с боковой навигацией и инлайн-аккордеоном
 * (Feather / Lunar / Badlion Frosted Glass Style).
 *
 * <ul>
 *   <li>Настоящий GPU-блюр фона ({@link #renderBlurredBackground}) + дымчатая полупрозрачность 25–30%;</li>
 *   <li>Левая стеклянная панель навигации с поиском, категориями, редактором HUD и кнопкой «Чистый экран»;</li>
 *   <li>Двухколоночная сетка просторных карточек модулей с плавным раскрытием настроек вниз
 *       прямо внутри карточки (как по ЛКМ на кнопку «⚙ ▾», так и по ПКМ на шапку карточки);</li>
 *   <li>Встроенная сетка 16 шейдеров с живыми превью, компактные ползунки, инлайн-палитра цветов
 *       и назначение клавиш по СКМ (колёсику мыши).</li>
 * </ul>
 */
public final class AuroraInlineMenuScreen extends Screen {
    private static final int COLS = 2;
    private static final int COL_GAP = 10;
    private static final int CARD_HEADER_H = 38;
    private static final int SETTING_ROW_H = 21;
    private static final int COLOR_EXPANDED_H = 44;
    private static final int SHADER_SECTION_H = 152;
    private static final int PARTICLE_SHAPES_H = 26;

    private static final int[] QUICK_SWATCHES = {
            0xFF5CE1FF, 0xFF7FD4FF, 0xFFB98CFF, 0xFFFF6AD5,
            0xFFFF9A3C, 0xFF5CE6A0, 0xFFFF6E80, 0xFFFFFFFF
    };

    private final Screen parent;
    private final BackdropGallery backdrop = new BackdropGallery();
    private final AnimationTimer openAnimation = new AnimationTimer(0.0F);
    private final Map<Module, Float> expandAnims = new IdentityHashMap<>();
    private final Map<Module, Float> toggleAnims = new IdentityHashMap<>();
    private final Set<Module> expandedModules = new HashSet<>();
    private final Set<ColorSetting> expandedColors = new HashSet<>();
    private final Map<Object, Float> bindPopupAnims = new HashMap<>();

    private ModuleCategory selectedCategory;
    private boolean accountsTabActive;
    private boolean closing;
    private String searchQuery = "";
    private boolean searchFocused;
    private String accountInput = "";
    private boolean accountInputFocused;
    private float scrollOffset;
    private float maxScroll;

    private Setting<?> draggingSlider;
    private ColorSetting draggingColor;
    private int draggingColorChannel = -1;
    private Object listeningBindTarget;

    private final List<HitZone> hitZones = new ArrayList<>();

    private record HitZone(int x, int y, int w, int h, Runnable onLeftClick, Runnable onRightClick, Runnable onMiddleClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public AuroraInlineMenuScreen(Screen parent) {
        this(parent, ModuleManager.get().module("blocks"));
    }

    public AuroraInlineMenuScreen(Screen parent, Module initialExpanded) {
        this(parent, initialExpanded, null);
    }

    public AuroraInlineMenuScreen(Screen parent, Module initialExpanded, ModuleCategory initialCategory) {
        super(Component.literal("Модули AetherGlass"));
        this.parent = parent;
        this.selectedCategory = initialCategory;
        this.openAnimation.setTarget(1.0F);
        Module defaultExpand = initialExpanded != null ? initialExpanded : ModuleManager.get().module("blocks");
        for (Module m : ModuleManager.get().modules()) {
            this.toggleAnims.put(m, m.isEnabled() ? 1.0F : 0.0F);
            boolean exp = (m == defaultExpand);
            this.expandAnims.put(m, exp ? 1.0F : 0.0F);
            if (exp) {
                this.expandedModules.add(m);
            }
        }
    }

    public static AuroraInlineMenuScreen openAccounts(Screen parent) {
        AuroraInlineMenuScreen screen = new AuroraInlineMenuScreen(parent);
        screen.accountsTabActive = true;
        return screen;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        float dt = FrameClock.deltaSeconds();
        if (this.minecraft != null && this.minecraft.level == null) {
            this.backdrop.update(dt);
            this.backdrop.draw(graphics, this.width, this.height);
        }
        try {
            this.renderBlurredBackground(graphics);
        } catch (Throwable ignored) {
            // Если blurBeforeThisStratum уже был вызван в этом кадре
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        FrameClock.tick();
        float dt = FrameClock.deltaSeconds();
        this.openAnimation.speed(9.0F * AuroraTheme.motion());
        this.openAnimation.update(dt);
        float raw = this.openAnimation.current();
        float progress = Easing.easeOutCubic(raw);

        if (this.closing && raw <= 0.02F) {
            ModuleManager.get().saveNow();
            if (this.minecraft != null) {
                this.minecraft.setScreen(this.parent);
            }
            return;
        }

        this.hitZones.clear();

        // Лёгкая дымчатая вуаль поверх размытого мира/фона
        int dimColor = ColorUtil.fade(AuroraTheme.screenDim(), progress);
        graphics.fill(0, 0, this.width, this.height, dimColor);

        int panelW = Math.min(736, this.width - 24);
        int panelH = Math.min(448, this.height - 20);
        int slideY = Math.round((1.0F - progress) * 12.0F);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2 + slideY;
        int radius = Math.max(10, AuroraTheme.radiusPx());

        // Главное матовое стеклянное окно (Lunar / Feather стиль)
        DrawUtil.drawLiquidGlassPanel(graphics, panelX, panelY, panelW, panelH, radius, progress);

        int sidebarW = 154;
        int sidebarX = panelX + 8;
        int sidebarY = panelY + 8;
        int sidebarH = panelH - 16;

        renderSidebar(graphics, sidebarX, sidebarY, sidebarW, sidebarH, mouseX, mouseY, progress, dt);

        int bodyX = sidebarX + sidebarW + 10;
        int bodyY = panelY + 8;
        int bodyW = panelW - sidebarW - 26;
        int bodyH = panelH - 16;

        renderHeaderBar(graphics, bodyX, bodyY, bodyW, mouseX, mouseY, progress);
        if (this.accountsTabActive) {
            renderAccountsTab(graphics, bodyX, bodyY + 28, bodyW, bodyH - 28, mouseX, mouseY, progress, dt);
        } else {
            renderModuleColumns(graphics, bodyX, bodyY + 28, bodyW, bodyH - 28, mouseX, mouseY, progress, dt);
        }

        NotificationManager.get().render(graphics, this.width, this.height);
    }

    // ─────────────────── Левая стеклянная панель навигации (Feather / Lunar) ───────────────────

    private void renderSidebar(GuiGraphics graphics, int sx, int sy, int sw, int sh,
                               int mouseX, int mouseY, float alpha, float dt) {
        int radius = Math.max(8, AuroraTheme.radiusPx() - 2);
        DrawUtil.drawLiquidGlassCard(graphics, sx, sy, sw, sh, radius, alpha * 0.90F, false);

        // Эмблема и название клиента
        DrawUtil.drawFrostedPill(graphics, sx + 10, sy + 10, 22, 22, 7, alpha, 0.25F, 0.55F);
        graphics.drawString(this.font, "✦", sx + 18, sy + 17, ColorUtil.fade(AuroraTheme.accent(), alpha), false);
        graphics.drawString(this.font, "AETHERGLASS", sx + 38, sy + 12,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        graphics.drawString(this.font, "Liquid Glass · 1.21.11", sx + 38, sy + 22,
                ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);

        // Поле поиска внутри боковой панели
        int searchX = sx + 8;
        int searchY = sy + 38;
        int searchW = sw - 16;
        int searchH = 20;
        boolean searchHov = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        DrawUtil.drawFrostedPill(graphics, searchX, searchY, searchW, searchH, 6, alpha,
                searchHov ? 0.5F : 0.0F, this.searchFocused ? 0.65F : 0.0F);
        String shown = this.searchQuery.isEmpty()
                ? (this.searchFocused ? "🔍 Поиск...|" : "🔍 Поиск модулей...")
                : ("🔍 " + this.searchQuery + (this.searchFocused ? "|" : ""));
        int searchTextCol = (this.searchQuery.isEmpty() && !this.searchFocused)
                ? AuroraTheme.textMuted()
                : AuroraTheme.textPrimary();
        graphics.drawString(this.font, trimToWidth(shown, searchW - 12), searchX + 7, searchY + 6,
                ColorUtil.fade(searchTextCol, alpha), false);
        this.hitZones.add(new HitZone(searchX, searchY, searchW, searchH,
                () -> this.searchFocused = true,
                () -> {
                    this.searchQuery = "";
                    this.searchFocused = false;
                },
                null));

        // Разделитель
        graphics.fill(sx + 10, sy + 64, sx + sw - 10, sy + 65,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * alpha)));

        // Вертикальные вкладки категорий
        int navY = sy + 72;
        navY = renderSidebarCategory(graphics, sx + 8, navY, sw - 16, "❖ Все модули",
                null, false, ModuleManager.get().modules().size(), mouseX, mouseY, alpha);
        String[] catIcons = {"✦ ", "◎ ", "◈ ", "⚙ "};
        ModuleCategory[] cats = ModuleCategory.values();
        for (int i = 0; i < cats.length; i++) {
            ModuleCategory cat = cats[i];
            String icon = i < catIcons.length ? catIcons[i] : "• ";
            int count = ModuleManager.get().byCategory(cat).size();
            navY = renderSidebarCategory(graphics, sx + 8, navY, sw - 16, icon + cat.title(),
                    cat, false, count, mouseX, mouseY, alpha);
        }

        // Отдельная вкладка «Аккаунты» (Блок 2.3: смена оффлайн-аккаунта прямо в игре без перезапуска)
        graphics.fill(sx + 10, navY + 2, sx + sw - 10, navY + 3,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * alpha)));
        navY += 7;
        renderSidebarCategory(graphics, sx + 8, navY, sw - 16, "👤 Аккаунты",
                null, true, AccountManager.get().accounts().size(), mouseX, mouseY, alpha);

        // Быстрые кнопки внизу боковой панели
        int botY = sy + sh - 52;
        graphics.fill(sx + 10, botY - 6, sx + sw - 10, botY - 5,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(24 * alpha)));

        // 1. Кнопка HUD Редактора
        int btnX = sx + 8;
        int btnW = sw - 16;
        int btnH = 20;
        boolean hudHov = inside(mouseX, mouseY, btnX, botY, btnW, btnH);
        DrawUtil.drawFrostedPill(graphics, btnX, botY, btnW, btnH, 6, alpha, hudHov ? 0.7F : 0.0F, 0.2F);
        String hudLabel = "◎ Редактор HUD";
        graphics.drawString(this.font, hudLabel, btnX + (btnW - this.font.width(hudLabel)) / 2, botY + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        this.hitZones.add(new HitZone(btnX, botY, btnW, btnH, () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudEditorScreen(this));
            }
        }, null, null));

        // 2. Кнопка «Чистый экран» [F8]
        int cleanY = botY + 24;
        KeybindManager km = KeybindManager.get();
        String cleanKey = KeybindManager.formatKey(km.cleanScreenKeybind());
        boolean cleanListening = "CLEAN_SCREEN".equals(this.listeningBindTarget);
        String cleanLabel = cleanListening ? "⌨ Нажмите клавишу..." : ("📷 Чистый экран [" + cleanKey + "]");
        boolean cleanHov = inside(mouseX, mouseY, btnX, cleanY, btnW, btnH);
        DrawUtil.drawFrostedPill(graphics, btnX, cleanY, btnW, btnH, 6, alpha,
                cleanHov ? 0.65F : 0.0F, (km.isCleanScreenActive() || cleanListening) ? 0.75F : 0.0F);
        graphics.drawString(this.font, trimToWidth(cleanLabel, btnW - 8),
                btnX + (btnW - this.font.width(trimToWidth(cleanLabel, btnW - 8))) / 2, cleanY + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        this.hitZones.add(new HitZone(btnX, cleanY, btnW, btnH,
                km::toggleCleanScreen,
                km::toggleCleanScreen,
                () -> this.listeningBindTarget = cleanListening ? null : "CLEAN_SCREEN"));
    }

    private int renderSidebarCategory(GuiGraphics graphics, int x, int y, int w, String title,
                                      ModuleCategory category, boolean isAccountsTab, int count,
                                      int mouseX, int mouseY, float alpha) {
        boolean active = isAccountsTab ? this.accountsTabActive : (!this.accountsTabActive && this.selectedCategory == category);
        int h = 21;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);

        if (active || hovered) {
            DrawUtil.drawFrostedPill(graphics, x, y, w, h, 6, alpha,
                    hovered ? 0.6F : 0.0F, active ? 0.75F : 0.0F);
        }
        if (active) {
            DrawUtil.fillRounded(graphics, x + 3, y + 5, 3, h - 10, 1,
                    ColorUtil.fade(AuroraTheme.accent(), alpha));
        }

        int textCol = active ? AuroraTheme.textPrimary() : (hovered ? AuroraTheme.textPrimary() : AuroraTheme.textSecondary());
        graphics.drawString(this.font, trimToWidth(title, w - 28), x + 9, y + 6,
                ColorUtil.fade(textCol, alpha), false);

        String cntStr = Integer.toString(count);
        int cntW = this.font.width(cntStr) + 8;
        int cntX = x + w - cntW - 5;
        DrawUtil.fillRounded(graphics, cntX, y + 4, cntW, 12, 4,
                ColorUtil.fade(active ? AuroraTheme.accent() : 0xFF28364E, (active ? 0.28F : 0.35F) * alpha));
        graphics.drawString(this.font, cntStr, cntX + 4, y + 6,
                ColorUtil.fade(active ? AuroraTheme.accent() : AuroraTheme.textMuted(), alpha), false);

        this.hitZones.add(new HitZone(x, y, w, h, () -> {
            this.accountsTabActive = isAccountsTab;
            if (!isAccountsTab) {
                this.selectedCategory = category;
            }
            this.scrollOffset = 0.0F;
        }, null, null));
        return y + h + 3;
    }

    // ─────────────────── Верхняя информационная полоса над карточками ───────────────────

    private void renderHeaderBar(GuiGraphics graphics, int x, int y, int width,
                                 int mouseX, int mouseY, float alpha) {
        DrawUtil.drawLiquidGlassCard(graphics, x, y, width, 22, 7, alpha * 0.85F, false);
        String catName = this.accountsTabActive
                ? "Аккаунты (Offline-смена в игре)"
                : (this.selectedCategory == null ? "Все модули" : this.selectedCategory.title());
        graphics.drawString(this.font, "✦ " + catName, x + 9, y + 7,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);

        // Быстрая капсула текущего аккаунта в шапке
        String curUser = "👤 " + AccountManager.get().currentUsername();
        int accPillW = Math.min(118, this.font.width(curUser) + 14);
        int accPillX = x + width - 24 - accPillW;
        int accPillY = y + 3;
        boolean accHov = inside(mouseX, mouseY, accPillX, accPillY, accPillW, 16);
        DrawUtil.drawFrostedPill(graphics, accPillX, accPillY, accPillW, 16, 5, alpha,
                accHov ? 0.70F : 0.0F, this.accountsTabActive ? 0.80F : 0.25F);
        String clippedUser = trimToWidth(curUser, accPillW - 8);
        graphics.drawString(this.font, clippedUser,
                accPillX + (accPillW - this.font.width(clippedUser)) / 2, accPillY + 4,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        this.hitZones.add(new HitZone(accPillX, accPillY, accPillW, 16, () -> {
            this.accountsTabActive = !this.accountsTabActive;
            this.scrollOffset = 0.0F;
        }, null, null));

        // Кнопка закрытия [✕]
        int closeX = x + width - 20;
        int closeY = y + 3;
        boolean closeHov = inside(mouseX, mouseY, closeX, closeY, 16, 16);
        DrawUtil.drawFrostedPill(graphics, closeX, closeY, 16, 16, 5, alpha, closeHov ? 0.8F : 0.0F, 0.0F);
        graphics.drawString(this.font, "✕", closeX + 5, closeY + 4,
                ColorUtil.fade(closeHov ? AuroraTheme.danger() : AuroraTheme.textSecondary(), alpha), false);
        this.hitZones.add(new HitZone(closeX, closeY, 16, 16, this::onClose, null, null));
    }

    // ─────────────────── Вкладка «Аккаунты» (Блок 2.3: смена ника в игре) ───────────────────

    private void renderAccountsTab(GuiGraphics graphics, int bodyX, int bodyY, int bodyW, int bodyH,
                                   int mouseX, int mouseY, float alpha, float dt) {
        AccountManager am = AccountManager.get();
        String currentName = am.currentUsername();
        String currentUuid = AccountManager.offlineUuid(currentName).toString();
        int radius = Math.max(8, AuroraTheme.radiusPx() - 2);

        // 1. Верхняя карточка активного аккаунта
        int topCardH = 54;
        DrawUtil.drawLiquidGlassCard(graphics, bodyX, bodyY, bodyW, topCardH, radius, alpha, true);

        // Аватар-монограмма
        int avX = bodyX + 12;
        int avY = bodyY + 11;
        DrawUtil.drawFrostedPill(graphics, avX, avY, 32, 32, 8, alpha, 0.35F, 0.75F);
        String initial = currentName.isEmpty() ? "A" : currentName.substring(0, 1).toUpperCase(Locale.ROOT);
        graphics.drawString(this.font, initial,
                avX + (32 - this.font.width(initial)) / 2, avY + 12,
                ColorUtil.fade(0xFFFFFFFF, alpha), false);

        graphics.drawString(this.font, "Активный аккаунт: " + currentName,
                avX + 42, bodyY + 11, ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        String activeShortUuid = currentUuid.length() > 18 ? currentUuid.substring(0, 18) + "..." : currentUuid;
        graphics.drawString(this.font, "Offline UUID: " + activeShortUuid,
                avX + 42, bodyY + 23, ColorUtil.fade(AuroraTheme.textSecondary(), 0.90F * alpha), false);
        graphics.drawString(this.font, "● Мгновенная смена в игре (сохраняется в конфиг)",
                avX + 42, bodyY + 35, ColorUtil.fade(AuroraTheme.accent(), 0.92F * alpha), false);

        // Кнопка «Случайный ник»
        int rndW = 114;
        int rndH = 22;
        int rndX = bodyX + bodyW - rndW - 12;
        int rndY = bodyY + (topCardH - rndH) / 2;
        boolean rndHov = inside(mouseX, mouseY, rndX, rndY, rndW, rndH);
        DrawUtil.drawFrostedPill(graphics, rndX, rndY, rndW, rndH, 6, alpha, rndHov ? 0.75F : 0.15F, 0.45F);
        String rndLabel = "🎲 Случайный ник";
        graphics.drawString(this.font, rndLabel,
                rndX + (rndW - this.font.width(rndLabel)) / 2, rndY + 7,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        this.hitZones.add(new HitZone(rndX, rndY, rndW, rndH, () -> {
            String generated = am.switchRandomAccount();
            this.accountInput = generated;
        }, null, null));

        // 2. Панель ввода нового ника и переключения
        int inputCardY = bodyY + topCardH + 8;
        int inputCardH = 44;
        DrawUtil.drawLiquidGlassCard(graphics, bodyX, inputCardY, bodyW, inputCardH, radius, alpha, this.accountInputFocused);

        int boxX = bodyX + 12;
        int boxY = inputCardY + 11;
        int applyW = 132;
        int boxW = bodyW - applyW - 32;
        int boxH = 22;
        boolean boxHov = inside(mouseX, mouseY, boxX, boxY, boxW, boxH);
        DrawUtil.drawFrostedPill(graphics, boxX, boxY, boxW, boxH, 6, alpha,
                boxHov ? 0.55F : 0.0F, this.accountInputFocused ? 0.75F : 0.15F);

        String shownInput = this.accountInput.isEmpty()
                ? (this.accountInputFocused ? "Введите ник (3–16 символов A-Z, 0-9, _)|" : "✎ Введите новый ник (3–16 символов)...")
                : ("👤 " + this.accountInput + (this.accountInputFocused ? "|" : ""));
        int inputCol = (this.accountInput.isEmpty() && !this.accountInputFocused)
                ? AuroraTheme.textMuted()
                : AuroraTheme.textPrimary();
        graphics.drawString(this.font, trimToWidth(shownInput, boxW - 14), boxX + 8, boxY + 7,
                ColorUtil.fade(inputCol, alpha), false);

        this.hitZones.add(new HitZone(boxX, boxY, boxW, boxH,
                () -> {
                    this.accountInputFocused = true;
                    this.searchFocused = false;
                },
                () -> this.accountInput = "",
                null));

        int applyX = boxX + boxW + 8;
        int applyY = boxY;
        boolean validCandidate = AccountManager.isValidUsername(AccountManager.sanitizeUsername(this.accountInput));
        boolean applyHov = inside(mouseX, mouseY, applyX, applyY, applyW, boxH);
        DrawUtil.drawFrostedPill(graphics, applyX, applyY, applyW, boxH, 6, alpha,
                applyHov ? 0.80F : 0.20F, validCandidate ? 0.85F : 0.20F);
        String applyLabel = "✓ Сменить аккаунт";
        graphics.drawString(this.font, applyLabel,
                applyX + (applyW - this.font.width(applyLabel)) / 2, applyY + 7,
                ColorUtil.fade(validCandidate ? 0xFFFFFFFF : AuroraTheme.textSecondary(), alpha), false);
        this.hitZones.add(new HitZone(applyX, applyY, applyW, boxH, () -> {
            if (am.switchAccount(this.accountInput)) {
                this.accountInput = "";
                this.accountInputFocused = false;
            }
        }, null, null));

        // 3. Сетка сохранённых аккаунтов
        int listTop = inputCardY + inputCardH + 10;
        graphics.drawString(this.font, "❖ Сохранённые оффлайн-аккаунты (" + am.accounts().size() + ")",
                bodyX + 4, listTop, ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

        int gridTop = listTop + 14;
        int gridH = Math.max(40, bodyY + bodyH - gridTop);
        List<String> saved = am.accounts();
        int cols = 2;
        int gap = 8;
        int cardW = (bodyW - gap) / cols;
        int cardH = 34;

        graphics.enableScissor(bodyX - 2, gridTop, bodyX + bodyW + 2, gridTop + gridH);

        int totalRows = (saved.size() + cols - 1) / cols;
        for (int i = 0; i < saved.size(); i++) {
            String acc = saved.get(i);
            int r = i / cols;
            int c = i % cols;
            int cx = bodyX + c * (cardW + gap);
            int cy = gridTop + r * (cardH + gap) - Math.round(this.scrollOffset);

            if (cy + cardH < gridTop || cy > gridTop + gridH) {
                continue;
            }

            boolean isCurrent = acc.equalsIgnoreCase(currentName);
            boolean cardHov = inside(mouseX, mouseY, cx, cy, cardW, cardH)
                    && mouseY >= gridTop && mouseY <= gridTop + gridH;

            DrawUtil.drawLiquidGlassCard(graphics, cx, cy, cardW, cardH, 7, alpha, isCurrent || cardHov);
            if (isCurrent) {
                DrawUtil.fillRounded(graphics, cx + 3, cy + 7, 3, cardH - 14, 1,
                        ColorUtil.fade(AuroraTheme.accent(), alpha));
            }

            // Мини-бейдж с первой буквой
            DrawUtil.drawFrostedPill(graphics, cx + 9, cy + 7, 20, 20, 6, alpha, 0.2F, isCurrent ? 0.75F : 0.25F);
            String ch = acc.substring(0, 1).toUpperCase(Locale.ROOT);
            graphics.drawString(this.font, ch, cx + 9 + (20 - this.font.width(ch)) / 2, cy + 13,
                    ColorUtil.fade(0xFFFFFFFF, alpha), false);

            graphics.drawString(this.font, trimToWidth(acc, cardW - 110), cx + 34, cy + 7,
                    ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
            String shortUuid = AccountManager.offlineUuid(acc).toString().substring(0, 13) + "...";
            graphics.drawString(this.font, shortUuid, cx + 34, cy + 19,
                    ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);

            // Кнопка удаления [✕]
            int delW = 18;
            int delH = 18;
            int delX = cx + cardW - delW - 7;
            int delY = cy + (cardH - delH) / 2;
            boolean delHov = inside(mouseX, mouseY, delX, delY, delW, delH);
            DrawUtil.drawFrostedPill(graphics, delX, delY, delW, delH, 5, alpha, delHov ? 0.85F : 0.0F, 0.0F);
            graphics.drawString(this.font, "✕", delX + 6, delY + 5,
                    ColorUtil.fade(delHov ? AuroraTheme.danger() : AuroraTheme.textMuted(), alpha), false);

            // Кнопка «Активен» или «Выбрать»
            int selW = 58;
            int selH = 18;
            int selX = delX - selW - 5;
            int selY = delY;
            boolean selHov = inside(mouseX, mouseY, selX, selY, selW, selH);
            DrawUtil.drawFrostedPill(graphics, selX, selY, selW, selH, 5, alpha,
                    selHov ? 0.75F : 0.0F, isCurrent ? 0.85F : 0.25F);
            String selText = isCurrent ? "● Активен" : "▶ Войти";
            graphics.drawString(this.font, selText,
                    selX + (selW - this.font.width(selText)) / 2, selY + 5,
                    ColorUtil.fade(isCurrent ? AuroraTheme.accent() : AuroraTheme.textPrimary(), alpha), false);

            if (mouseY >= gridTop && mouseY <= gridTop + gridH) {
                this.hitZones.add(new HitZone(cx, cy, cardW - delW - 12, cardH,
                        () -> am.switchAccount(acc), null, null));
                this.hitZones.add(new HitZone(delX, delY, delW, delH,
                        () -> am.removeAccount(acc), null, null));
            }
        }

        graphics.disableScissor();
        this.maxScroll = Math.max(0.0F, totalRows * (cardH + gap) - gridH + 8.0F);
    }

    // ─────────────────── Сетка карточек модулей с инлайн-аккордеоном ───────────────────

    private void renderModuleColumns(GuiGraphics graphics, int bodyX, int bodyY, int bodyW, int bodyH,
                                     int mouseX, int mouseY, float alpha, float dt) {
        List<Module> modules = filteredModules();
        int colW = (bodyW - COL_GAP * (COLS - 1)) / COLS;
        int[] colY = new int[COLS];
        for (int i = 0; i < COLS; i++) {
            colY[i] = bodyY - Math.round(this.scrollOffset);
        }

        graphics.enableScissor(bodyX - 2, bodyY, bodyX + bodyW + 2, bodyY + bodyH);

        int maxBottom = bodyY;
        for (Module module : modules) {
            int bestCol = 0;
            for (int c = 1; c < COLS; c++) {
                if (colY[c] < colY[bestCol]) {
                    bestCol = c;
                }
            }
            int cardX = bodyX + bestCol * (colW + COL_GAP);
            int cardY = colY[bestCol];

            int cardH = renderModuleCard(graphics, module, cardX, cardY, colW,
                    bodyY, bodyY + bodyH, mouseX, mouseY, alpha, dt);
            colY[bestCol] += cardH + 8;
            maxBottom = Math.max(maxBottom, colY[bestCol] + Math.round(this.scrollOffset));
        }

        graphics.disableScissor();

        this.maxScroll = Math.max(0.0F, (maxBottom - bodyY) - bodyH + 12.0F);
        if (this.scrollOffset > this.maxScroll) {
            this.scrollOffset = this.maxScroll;
        }
    }

    private int renderModuleCard(GuiGraphics graphics, Module module, int x, int y, int width,
                                 int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, float dt) {
        boolean expanded = this.expandedModules.contains(module);
        float expandCurr = this.expandAnims.getOrDefault(module, 0.0F);
        float expandTarget = expanded ? 1.0F : 0.0F;
        expandCurr += (expandTarget - expandCurr) * Math.min(1.0F, dt * 14.0F * AuroraTheme.motion());
        if (Math.abs(expandTarget - expandCurr) < 0.005F) {
            expandCurr = expandTarget;
        }
        this.expandAnims.put(module, expandCurr);

        float toggleCurr = this.toggleAnims.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
        float toggleTarget = module.isEnabled() ? 1.0F : 0.0F;
        toggleCurr += (toggleTarget - toggleCurr) * Math.min(1.0F, dt * 14.0F * AuroraTheme.motion());
        this.toggleAnims.put(module, toggleCurr);

        int contentFullH = computeExpandedContentHeight(module);
        int visibleContentH = Math.round(contentFullH * Easing.easeOutCubic(expandCurr));
        int totalH = CARD_HEADER_H + visibleContentH;

        if (y + totalH < clipTop || y > clipBottom) {
            return totalH;
        }

        int radius = Math.max(7, Math.min(13, AuroraTheme.radiusPx()));
        boolean headerHover = inside(mouseX, mouseY, x, y, width, CARD_HEADER_H)
                && mouseY >= clipTop && mouseY <= clipBottom;

        // Матовая стеклянная карточка модуля (25–30% полупрозрачность + мягкая тень + солнечный блик)
        DrawUtil.drawLiquidGlassCard(graphics, x, y, width, totalH, radius, alpha, module.isEnabled() || headerHover);
        if (module.isEnabled()) {
            DrawUtil.fillRounded(graphics, x + 3, y + 8, 3, CARD_HEADER_H - 16, 1,
                    ColorUtil.fade(AuroraTheme.accent(), 0.92F * alpha));
        }

        // Тумблер включения справа
        int switchW = 26;
        int switchH = 14;
        int switchX = x + width - switchW - 8;
        int switchY = y + (CARD_HEADER_H - switchH) / 2;

        // Кнопка раскрытия настроек «⚙ ▾» слева от тумблера (работает по ЛКМ и ПКМ!)
        int gearW = 26;
        int gearH = 16;
        int gearX = switchX - gearW - 5;
        int gearY = y + (CARD_HEADER_H - gearH) / 2;
        boolean gearHover = inside(mouseX, mouseY, gearX, gearY, gearW, gearH)
                && mouseY >= clipTop && mouseY <= clipBottom;
        DrawUtil.drawFrostedPill(graphics, gearX, gearY, gearW, gearH, 5, alpha,
                gearHover ? 0.75F : 0.0F, expanded ? 0.70F : 0.0F);
        String gearLabel = expanded ? "⚙▴" : "⚙▾";
        int gw = this.font.width(gearLabel);
        graphics.drawString(this.font, gearLabel, gearX + (gearW - gw) / 2, gearY + 4,
                ColorUtil.fade(expanded ? AuroraTheme.accent() : AuroraTheme.textPrimary(), alpha), false);

        // Инлайн-бейдж привязки клавиши (СКМ)
        int bindRightEdge = gearX - 5;
        int bindW = renderInlineKeybindBadge(graphics, module, module.keybind(),
                key -> {
                    module.setKeybind(key);
                    NotificationManager.get().pushKeybindChanged(module.name(), KeybindManager.formatKey(key));
                },
                bindRightEdge, y + (CARD_HEADER_H - 14) / 2, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

        int maxTitleW = Math.max(44, bindRightEdge - bindW - (x + 11));
        graphics.drawString(this.font, trimToWidth(module.name(), maxTitleW), x + 10, y + 7,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        graphics.drawString(this.font, trimToWidth(module.description(), maxTitleW), x + 10, y + 20,
                ColorUtil.fade(AuroraTheme.textSecondary(), 0.88F * alpha), false);

        // Отрисовка стеклянного тумблера
        DrawUtil.drawFrostedPill(graphics, switchX, switchY, switchW, switchH, 7, alpha,
                headerHover ? 0.35F : 0.0F, toggleCurr * 0.85F);
        int knobX = switchX + 2 + Math.round(toggleCurr * (switchW - 12));
        DrawUtil.fillRounded(graphics, knobX, switchY + 2, 10, 10, 5, ColorUtil.fade(0xFFFFFFFF, alpha));

        Runnable toggleExpand = () -> {
            if (this.expandedModules.contains(module)) {
                this.expandedModules.remove(module);
            } else {
                this.expandedModules.add(module);
            }
        };

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            // Клик по шапке карточки: ЛКМ переключает модуль, ПКМ раскрывает аккордеон, СКМ назначает бинд
            this.hitZones.add(new HitZone(x, y, width, CARD_HEADER_H,
                    module::toggle,
                    toggleExpand,
                    () -> this.listeningBindTarget = (this.listeningBindTarget == module) ? null : module));
            // Клик ЛКМ/ПКМ конкретно по кнопке «⚙▾» раскрывает/сворачивает настройки!
            this.hitZones.add(new HitZone(gearX - 2, gearY - 2, gearW + 4, gearH + 4,
                    toggleExpand,
                    toggleExpand,
                    () -> this.listeningBindTarget = (this.listeningBindTarget == module) ? null : module));
        }

        if (visibleContentH > 3) {
            int innerTop = Math.max(clipTop, y + CARD_HEADER_H);
            int innerBottom = Math.min(clipBottom, y + CARD_HEADER_H + visibleContentH);
            if (innerBottom > innerTop) {
                // Углублённая полупрозрачная стеклянная ниша для настроек внутри карточки
                DrawUtil.fillRounded(graphics, x + 5, y + CARD_HEADER_H, width - 10, visibleContentH - 4,
                        Math.max(4, radius - 3),
                        ColorUtil.withAlpha(0xFF0C1422, Math.round(42 * alpha * expandCurr)));
                graphics.fill(x + 9, y + CARD_HEADER_H, x + width - 9, y + CARD_HEADER_H + 1,
                        ColorUtil.withAlpha(0xFFE4F4FF, Math.round(26 * alpha * expandCurr)));

                renderInlineSettings(graphics, module, x + 9, y + CARD_HEADER_H + 5, width - 18,
                        innerTop, innerBottom, mouseX, mouseY, alpha * expandCurr, dt);
            }
        }

        return totalH;
    }

    private int computeExpandedContentHeight(Module module) {
        int h = 10;
        if (module instanceof BlockEffectsModule || module instanceof HandEffectsModule) {
            h += SHADER_SECTION_H + 4;
        } else if (module instanceof ParticleModule) {
            h += PARTICLE_SHAPES_H + 4;
        }
        for (Setting<?> setting : module.settings()) {
            h += SETTING_ROW_H;
            if (setting instanceof ColorSetting colorSetting && this.expandedColors.contains(colorSetting)) {
                h += COLOR_EXPANDED_H;
            }
        }
        return h;
    }

    private void renderInlineSettings(GuiGraphics graphics, Module module, int x, int startY, int width,
                                      int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, float dt) {
        int curY = startY;

        if (module instanceof BlockEffectsModule blockMod) {
            renderInlineShaderGrid(graphics, x, curY, width,
                    blockMod.shaderEffect(), blockMod::selectShaderPreset,
                    blockMod.shaderColor(), blockMod.shaderIntensity(), blockMod.shaderSpeed(), blockMod.shaderOpacity(),
                    true, blockMod.isEnabled(), clipTop, clipBottom, mouseX, mouseY, alpha);
            curY += SHADER_SECTION_H + 4;
        } else if (module instanceof HandEffectsModule handMod) {
            renderInlineShaderGrid(graphics, x, curY, width,
                    handMod.shaderEffect(), handMod::selectShaderPreset,
                    handMod.shaderColor(), handMod.shaderIntensity(), handMod.shaderSpeed(), handMod.shaderOpacity(),
                    false, handMod.isEnabled(), clipTop, clipBottom, mouseX, mouseY, alpha);
            curY += SHADER_SECTION_H + 4;
        } else if (module instanceof ParticleModule particleMod) {
            renderInlineParticleShapeBar(graphics, particleMod, x, curY, width, clipTop, clipBottom, mouseX, mouseY, alpha);
            curY += PARTICLE_SHAPES_H + 4;
        }

        for (Setting<?> setting : module.settings()) {
            curY = renderSettingRow(graphics, module, setting, x, curY, width, clipTop, clipBottom, mouseX, mouseY, alpha, dt);
        }
    }

    private void renderInlineShaderGrid(GuiGraphics graphics, int x, int y, int width,
                                        ShaderEffect currentEffect, java.util.function.Consumer<ShaderEffect> onSelect,
                                        int baseColor, float intensity, float speed, float opacity,
                                        boolean isBlock, boolean enabled,
                                        int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        float time = FrameClock.elapsedSeconds();

        int previewBoxW = 52;
        int previewBoxH = 36;
        if (isBlock) {
            GuiShaderPreviewRenderer.renderBlockPreview(graphics, x, y, previewBoxW, previewBoxH,
                    currentEffect, enabled, baseColor, intensity, speed, opacity, time, alpha);
        } else {
            GuiShaderPreviewRenderer.renderHandPreview(graphics, x, y, previewBoxW, previewBoxH,
                    currentEffect, enabled, baseColor, intensity, speed, opacity, time, alpha);
        }
        graphics.drawString(this.font,
                trimToWidth(currentEffect.number() + ". " + currentEffect.displayName(), width - previewBoxW - 6),
                x + previewBoxW + 6, y + 6, ColorUtil.fade(AuroraTheme.accent(), alpha), false);
        graphics.drawString(this.font,
                trimToWidth(currentEffect.description(), width - previewBoxW - 6),
                x + previewBoxW + 6, y + 19, ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

        int gridY = y + previewBoxH + 5;
        int gridCols = 4;
        int gap = 3;
        int cellW = (width - gap * (gridCols - 1)) / gridCols;
        int cellH = 24;

        ShaderEffect[] effects = ShaderEffect.values();
        for (int i = 0; i < effects.length; i++) {
            ShaderEffect effect = effects[i];
            int r = i / gridCols;
            int c = i % gridCols;
            int cx = x + c * (cellW + gap);
            int cy = gridY + r * (cellH + gap);

            boolean selected = effect == currentEffect;
            boolean hovered = inside(mouseX, mouseY, cx, cy, cellW, cellH)
                    && mouseY >= clipTop && mouseY <= clipBottom;

            GuiShaderPreviewRenderer.renderCardPreview(graphics, cx, cy, cellW, cellH,
                    effect, selected ? baseColor : effect.defaultColor(),
                    intensity, speed, opacity, time, alpha);

            if (selected || hovered) {
                int rimCol = selected ? AuroraTheme.accent() : 0xFFEAF7FF;
                DrawUtil.fillRoundedBordered(graphics, cx, cy, cellW, cellH, 5, 0,
                        ColorUtil.fade(rimCol, (selected ? 0.85F : 0.45F) * alpha), 1);
            }

            String shortTitle = trimToWidth(effect.displayName(), cellW - 4);
            int tw = this.font.width(shortTitle);
            DrawUtil.fillRounded(graphics, cx + 1, cy + cellH - 10, cellW - 2, 9, 3,
                    ColorUtil.withAlpha(0xFF0A1220, Math.round(120 * alpha)));
            graphics.drawString(this.font, shortTitle, cx + (cellW - tw) / 2, cy + cellH - 9,
                    ColorUtil.fade(selected ? AuroraTheme.accent() : 0xFFF0F6FF, alpha), false);

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(cx, cy, cellW, cellH, () -> onSelect.accept(effect), null, null));
            }
        }
    }

    private void renderInlineParticleShapeBar(GuiGraphics graphics, ParticleModule module, int x, int y, int width,
                                              int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        ParticleShape[] shapes = ParticleShape.values();
        int gap = 3;
        int cellW = (width - gap * (shapes.length - 1)) / shapes.length;
        int cellH = 22;
        String[] icons = {"✦", "⚡", "◎", "❄", "●", "❖"};

        for (int i = 0; i < shapes.length; i++) {
            ParticleShape shape = shapes[i];
            int cx = x + i * (cellW + gap);
            boolean active = module.shapeSetting().get() == shape;
            boolean hovered = inside(mouseX, mouseY, cx, y, cellW, cellH)
                    && mouseY >= clipTop && mouseY <= clipBottom;

            DrawUtil.drawFrostedPill(graphics, cx, y, cellW, cellH, 5, alpha,
                    hovered ? 0.65F : 0.0F, active ? 0.80F : 0.0F);

            String icon = i < icons.length ? icons[i] : "✦";
            String label = trimToWidth(icon + " " + shape.toString(), cellW - 4);
            int lw = this.font.width(label);
            graphics.drawString(this.font, label, cx + (cellW - lw) / 2, y + 7,
                    ColorUtil.fade(active ? AuroraTheme.accent() : AuroraTheme.textPrimary(), alpha), false);

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(cx, y, cellW, cellH, () -> module.shapeSetting().set(shape), null, null));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int renderSettingRow(GuiGraphics graphics, Module module, Setting<?> setting,
                                 int x, int y, int width, int clipTop, int clipBottom,
                                 int mouseX, int mouseY, float alpha, float dt) {
        boolean visible = y + SETTING_ROW_H >= clipTop && y <= clipBottom;

        if (setting instanceof BooleanSetting boolSetting) {
            if (visible) {
                int swW = 24;
                int swH = 12;
                int swX = x + width - swW;
                int swY = y + (SETTING_ROW_H - swH) / 2;

                int bindW = renderInlineKeybindBadge(graphics, boolSetting, boolSetting.keybind(),
                        key -> {
                            boolSetting.setKeybind(key);
                            ModConfig.get().set(module.key(boolSetting.id() + ".keybind"), boolSetting.keybind());
                            NotificationManager.get().pushKeybindChanged(boolSetting.name(), KeybindManager.formatKey(key));
                        },
                        swX - 4, y + 4, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

                int maxLabelW = Math.max(36, swX - 6 - bindW - x);
                graphics.drawString(this.font, trimToWidth(boolSetting.name(), maxLabelW), x, y + 6,
                        ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

                DrawUtil.drawFrostedPill(graphics, swX, swY, swW, swH, 6, alpha,
                        0.0F, boolSetting.isEnabled() ? 0.85F : 0.0F);
                int knobX = swX + (boolSetting.isEnabled() ? swW - 10 : 2);
                DrawUtil.fillRounded(graphics, knobX, swY + 2, 8, 8, 4, ColorUtil.fade(0xFFFFFFFF, alpha));

                this.hitZones.add(new HitZone(x, y, width, SETTING_ROW_H,
                        boolSetting::toggle,
                        boolSetting::reset,
                        () -> this.listeningBindTarget = (this.listeningBindTarget == boolSetting) ? null : boolSetting));
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof FloatSetting floatSetting) {
            if (visible) {
                renderCompactSliderRow(graphics, module, floatSetting,
                        (floatSetting.get() - floatSetting.min()) / Math.max(0.0001F, floatSetting.max() - floatSetting.min()),
                        formatFloat(floatSetting.get(), floatSetting.step()),
                        x, y, width, clipTop, clipBottom, mouseX, mouseY, alpha, dt);
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof IntSetting intSetting) {
            if (visible) {
                renderCompactSliderRow(graphics, module, intSetting,
                        (intSetting.get() - intSetting.min()) / (float) Math.max(1, intSetting.max() - intSetting.min()),
                        Integer.toString(intSetting.get()),
                        x, y, width, clipTop, clipBottom, mouseX, mouseY, alpha, dt);
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof EnumSetting enumSetting) {
            if (visible) {
                String valText = "◂ " + enumSetting.get().toString() + " ▸";
                int pillW = Math.min(width * 52 / 100, this.font.width(valText) + 12);
                int pillX = x + width - pillW;
                int pillY = y + 3;

                int bindW = renderInlineKeybindBadge(graphics, enumSetting, enumSetting.keybind(),
                        key -> {
                            enumSetting.setKeybind(key);
                            ModConfig.get().set(module.key(enumSetting.id() + ".keybind"), enumSetting.keybind());
                            NotificationManager.get().pushKeybindChanged(enumSetting.name(), KeybindManager.formatKey(key));
                        },
                        pillX - 4, y + 4, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

                int maxLabelW = Math.max(36, pillX - 6 - bindW - x);
                graphics.drawString(this.font, trimToWidth(enumSetting.name(), maxLabelW), x, y + 6,
                        ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

                boolean pillHov = inside(mouseX, mouseY, pillX, pillY, pillW, 15);
                DrawUtil.drawFrostedPill(graphics, pillX, pillY, pillW, 15, 5, alpha, pillHov ? 0.65F : 0.0F, 0.35F);
                String trimmedVal = trimToWidth(valText, pillW - 6);
                int vw = this.font.width(trimmedVal);
                graphics.drawString(this.font, trimmedVal, pillX + (pillW - vw) / 2, pillY + 4,
                        ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);

                this.hitZones.add(new HitZone(x, y, width, SETTING_ROW_H,
                        () -> {
                            Enum next = enumSetting.next(false);
                            enumSetting.set(next);
                            if (module instanceof ParticleModule pm && "preset".equals(enumSetting.id())) {
                                pm.applyPreset((ParticlePreset) next);
                            }
                        },
                        () -> {
                            Enum prev = enumSetting.next(true);
                            enumSetting.set(prev);
                            if (module instanceof ParticleModule pm && "preset".equals(enumSetting.id())) {
                                pm.applyPreset((ParticlePreset) prev);
                            }
                        },
                        () -> this.listeningBindTarget = (this.listeningBindTarget == enumSetting) ? null : enumSetting));
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof ColorSetting colorSetting) {
            boolean expanded = this.expandedColors.contains(colorSetting);
            if (visible) {
                int color = colorSetting.rgb();
                String hex = ColorUtil.toHex(color);
                int hexW = this.font.width(hex);
                int swatchW = 20;
                int swatchX = x + width - swatchW;
                int swatchY = y + 3;

                graphics.drawString(this.font, trimToWidth(colorSetting.name(), width - swatchW - hexW - 12), x, y + 6,
                        ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
                graphics.drawString(this.font, hex, swatchX - hexW - 5, y + 6,
                        ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);

                DrawUtil.drawSoftShadow(graphics, swatchX, swatchY, swatchW, 14, 5, alpha * 0.65F);
                DrawUtil.fillRounded(graphics, swatchX, swatchY, swatchW, 14, 5, ColorUtil.fade(color, alpha));
                DrawUtil.fillRoundedBordered(graphics, swatchX, swatchY, swatchW, 14, 5, 0,
                        ColorUtil.fade(expanded ? AuroraTheme.accent() : 0xFFEAF7FF, 0.55F * alpha), 1);

                this.hitZones.add(new HitZone(x, y, width, SETTING_ROW_H,
                        () -> {
                            if (expanded) {
                                this.expandedColors.remove(colorSetting);
                            } else {
                                this.expandedColors.add(colorSetting);
                            }
                        },
                        colorSetting::reset,
                        null));
            }
            int nextY = y + SETTING_ROW_H;
            if (expanded) {
                if (nextY + COLOR_EXPANDED_H >= clipTop && nextY <= clipBottom) {
                    renderCompactInlineColorPicker(graphics, colorSetting, x, nextY, width, clipTop, clipBottom, mouseX, mouseY, alpha);
                }
                nextY += COLOR_EXPANDED_H;
            }
            return nextY;
        }

        return y + SETTING_ROW_H;
    }

    private void renderCompactSliderRow(GuiGraphics graphics, Module module, Setting<?> setting, float normalized,
                                        String valueText, int x, int y, int width,
                                        int clipTop, int clipBottom, int mouseX, int mouseY,
                                        float alpha, float dt) {
        float clamped = Math.max(0.0F, Math.min(1.0F, normalized));
        int sliderW = Math.min(68, Math.max(52, width * 30 / 100));
        int sliderX = x + width - sliderW;
        int trackH = 4;
        int trackY = y + (SETTING_ROW_H - trackH) / 2;

        int valW = this.font.width(valueText);
        int valX = sliderX - valW - 5;

        int bindW = renderInlineKeybindBadge(graphics, setting, setting.keybind(),
                key -> {
                    setting.setKeybind(key);
                    ModConfig.get().set(module.key(setting.id() + ".keybind"), setting.keybind());
                    NotificationManager.get().pushKeybindChanged(setting.name(), KeybindManager.formatKey(key));
                },
                valX - 4, y + 4, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

        int maxLabelW = Math.max(30, valX - 5 - bindW - x);
        graphics.drawString(this.font, trimToWidth(setting.name(), maxLabelW), x, y + 6,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);

        graphics.drawString(this.font, valueText, valX, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), 0.94F * alpha), false);

        // Компактная дорожка ползунка из матового стекла
        DrawUtil.fillRounded(graphics, sliderX, trackY, sliderW, trackH, 2,
                ColorUtil.withAlpha(0xFF141E30, Math.round(108 * alpha)));
        DrawUtil.fillRoundedBordered(graphics, sliderX, trackY, sliderW, trackH, 2, 0,
                ColorUtil.withAlpha(0xFFE0F2FF, Math.round(30 * alpha)), 1);
        int fillW = Math.max(3, Math.round(sliderW * clamped));
        DrawUtil.fillRounded(graphics, sliderX, trackY, fillW, trackH, 2,
                ColorUtil.fade(AuroraTheme.accent(), 0.90F * alpha));

        int knobSize = 7;
        int knobX = sliderX + Math.round((sliderW - knobSize) * clamped);
        int knobY = trackY - 1;
        DrawUtil.fillRounded(graphics, knobX, knobY, knobSize, knobSize, 3, ColorUtil.fade(0xFFFFFFFF, alpha));
        DrawUtil.fillRoundedBordered(graphics, knobX, knobY, knobSize, knobSize, 3, 0,
                ColorUtil.fade(AuroraTheme.accent(), 0.75F * alpha), 1);

        if (this.draggingSlider == setting) {
            updateSliderFromMouse(setting, mouseX, sliderX, sliderW);
        }

        this.hitZones.add(new HitZone(x, y, width, SETTING_ROW_H,
                () -> {
                    this.draggingSlider = setting;
                    updateSliderFromMouse(setting, mouseX, sliderX, sliderW);
                },
                setting::reset,
                () -> this.listeningBindTarget = (this.listeningBindTarget == setting) ? null : setting));
    }

    private void updateSliderFromMouse(Setting<?> setting, double mouseX, int sliderX, int sliderW) {
        float t = (float) ((mouseX - sliderX) / (double) Math.max(1, sliderW));
        t = Math.max(0.0F, Math.min(1.0F, t));
        if (setting instanceof FloatSetting fs) {
            fs.set(fs.min() + t * (fs.max() - fs.min()));
        } else if (setting instanceof IntSetting is) {
            is.set(Math.round(is.min() + t * (is.max() - is.min())));
        }
    }

    private void renderCompactInlineColorPicker(GuiGraphics graphics, ColorSetting setting,
                                                int x, int y, int width, int clipTop, int clipBottom,
                                                int mouseX, int mouseY, float alpha) {
        DrawUtil.drawLiquidGlassCard(graphics, x, y, width, COLOR_EXPANDED_H - 2, 6, alpha * 0.90F, false);

        int color = setting.rgb();
        float hue = ColorUtil.hueOf(color);
        float sat = ColorUtil.saturationOf(color);
        float bri = ColorUtil.brightnessOf(color);

        int barX = x + 16;
        int barW = width - 22;
        int barH = 5;

        int hueY = y + 5;
        graphics.drawString(this.font, "H", x + 5, hueY - 2, ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);
        int segs = 12;
        for (int i = 0; i < segs; i++) {
            int sx0 = barX + (i * barW) / segs;
            int sx1 = barX + ((i + 1) * barW) / segs;
            int c0 = ColorUtil.fade(ColorUtil.hsb((i + 0.5F) / segs, 0.85F, 1.0F), alpha);
            graphics.fill(sx0, hueY, Math.max(sx0 + 1, sx1), hueY + barH, c0);
        }
        int hueKnob = barX + Math.round(hue * (barW - 3));
        graphics.fill(hueKnob, hueY - 1, hueKnob + 3, hueY + barH + 1, ColorUtil.fade(0xFFFFFFFF, alpha));

        int sbY = y + 15;
        int halfBarW = (barW - 18) / 2;
        int satX = barX;
        int briX = barX + halfBarW + 18;

        graphics.drawString(this.font, "S", x + 5, sbY - 2, ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);
        DrawUtil.fillRounded(graphics, satX, sbY, halfBarW, barH, 2, ColorUtil.fade(0xFF28354C, alpha));
        DrawUtil.fillRounded(graphics, satX, sbY, Math.max(2, Math.round(sat * halfBarW)), barH, 2,
                ColorUtil.fade(ColorUtil.hsb(hue, 1.0F, Math.max(0.4F, bri)), alpha));
        int satKnob = satX + Math.round(sat * (halfBarW - 3));
        graphics.fill(satKnob, sbY - 1, satKnob + 3, sbY + barH + 1, ColorUtil.fade(0xFFFFFFFF, alpha));

        graphics.drawString(this.font, "B", briX - 10, sbY - 2, ColorUtil.fade(AuroraTheme.textMuted(), alpha), false);
        DrawUtil.fillRounded(graphics, briX, sbY, halfBarW, barH, 2, ColorUtil.fade(0xFF182234, alpha));
        DrawUtil.fillRounded(graphics, briX, sbY, Math.max(2, Math.round(bri * halfBarW)), barH, 2,
                ColorUtil.fade(ColorUtil.hsb(hue, sat, 1.0F), alpha));
        int briKnob = briX + Math.round(bri * (halfBarW - 3));
        graphics.fill(briKnob, sbY - 1, briKnob + 3, sbY + barH + 1, ColorUtil.fade(0xFFFFFFFF, alpha));

        if (this.draggingColor == setting) {
            if (this.draggingColorChannel == 0) {
                float newH = clamp01((float) (mouseX - barX) / Math.max(1, barW));
                setting.setRgb(ColorUtil.hsb(newH, sat, bri));
            } else if (this.draggingColorChannel == 1) {
                float newS = clamp01((float) (mouseX - satX) / Math.max(1, halfBarW));
                setting.setRgb(ColorUtil.hsb(hue, newS, bri));
            } else if (this.draggingColorChannel == 2) {
                float newB = clamp01((float) (mouseX - briX) / Math.max(1, halfBarW));
                setting.setRgb(ColorUtil.hsb(hue, sat, newB));
            }
        }

        this.hitZones.add(new HitZone(barX, hueY - 2, barW, barH + 4, () -> {
            this.draggingColor = setting;
            this.draggingColorChannel = 0;
            float newH = clamp01((float) (mouseX - barX) / Math.max(1, barW));
            setting.setRgb(ColorUtil.hsb(newH, sat, bri));
        }, null, null));
        this.hitZones.add(new HitZone(satX, sbY - 2, halfBarW, barH + 4, () -> {
            this.draggingColor = setting;
            this.draggingColorChannel = 1;
            float newS = clamp01((float) (mouseX - satX) / Math.max(1, halfBarW));
            setting.setRgb(ColorUtil.hsb(hue, newS, bri));
        }, null, null));
        this.hitZones.add(new HitZone(briX, sbY - 2, halfBarW, barH + 4, () -> {
            this.draggingColor = setting;
            this.draggingColorChannel = 2;
            float newB = clamp01((float) (mouseX - briX) / Math.max(1, halfBarW));
            setting.setRgb(ColorUtil.hsb(hue, sat, newB));
        }, null, null));

        int swY = y + 26;
        int swGap = 4;
        int swW = (width - 12 - swGap * (QUICK_SWATCHES.length - 1)) / QUICK_SWATCHES.length;
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            int presetCol = QUICK_SWATCHES[i];
            int sx = x + 6 + i * (swW + swGap);
            DrawUtil.fillRounded(graphics, sx, swY, swW, 11, 4, ColorUtil.fade(presetCol, alpha));
            this.hitZones.add(new HitZone(sx, swY, swW, 11, () -> setting.setRgb(presetCol), null, null));
        }
    }

    private int renderInlineKeybindBadge(GuiGraphics graphics, Object target, int currentKey,
                                         java.util.function.IntConsumer onBind,
                                         int rightEdgeX, int y, int mouseX, int mouseY,
                                         int clipTop, int clipBottom, float alpha, float dt) {
        boolean listening = (this.listeningBindTarget == target);
        boolean hasKey = currentKey > 0;
        float anim = this.bindPopupAnims.getOrDefault(target, listening ? 1.0F : 0.0F);
        float animTarget = listening ? 1.0F : 0.0F;
        anim += (animTarget - anim) * Math.min(1.0F, dt * 16.0F * AuroraTheme.motion());
        if (Math.abs(animTarget - anim) < 0.005F) {
            anim = animTarget;
        }
        this.bindPopupAnims.put(target, anim);

        if (!hasKey && anim <= 0.01F) {
            return 0;
        }

        String keyText = KeybindManager.formatKey(currentKey);
        int compactW = hasKey ? Math.max(18, this.font.width(keyText) + 8) : 0;
        String popupLabel = "⌨ клавиша...";
        int expandedW = this.font.width(popupLabel) + 12;

        int currentW = Math.max(16, Math.round(compactW + (expandedW - compactW) * Easing.easeOutCubic(anim)));
        int x = rightEdgeX - currentW;
        int h = 14;

        DrawUtil.drawFrostedPill(graphics, x, y, currentW, h, 4, alpha, 0.2F, listening ? 0.85F : 0.25F);

        String display = anim > 0.45F ? popupLabel : keyText;
        String clipped = trimToWidth(display, currentW - 4);
        int tw = this.font.width(clipped);
        graphics.drawString(this.font, clipped, x + (currentW - tw) / 2, y + 3,
                ColorUtil.fade(listening ? AuroraTheme.accent() : AuroraTheme.textPrimary(), alpha), false);

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            this.hitZones.add(new HitZone(x, y, currentW, h,
                    () -> this.listeningBindTarget = listening ? null : target,
                    () -> {
                        onBind.accept(-1);
                        this.listeningBindTarget = null;
                    },
                    () -> this.listeningBindTarget = listening ? null : target));
        }

        return currentW + 4;
    }

    private List<Module> filteredModules() {
        List<Module> all = (this.selectedCategory == null)
                ? ModuleManager.get().modules()
                : ModuleManager.get().byCategory(this.selectedCategory);
        if (this.searchQuery == null || this.searchQuery.isBlank()) {
            return all;
        }
        String q = this.searchQuery.trim().toLowerCase(Locale.ROOT);
        List<Module> result = new ArrayList<>();
        for (Module m : all) {
            if (m.name().toLowerCase(Locale.ROOT).contains(q)
                    || m.description().toLowerCase(Locale.ROOT).contains(q)
                    || m.id().toLowerCase(Locale.ROOT).contains(q)) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        this.searchFocused = false;
        this.accountInputFocused = false;

        for (int i = this.hitZones.size() - 1; i >= 0; i--) {
            HitZone zone = this.hitZones.get(i);
            if (zone.contains(mx, my)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && zone.onLeftClick() != null) {
                    zone.onLeftClick().run();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && zone.onRightClick() != null) {
                    zone.onRightClick().run();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && zone.onMiddleClick() != null) {
                    zone.onMiddleClick().run();
                    return true;
                }
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            this.listeningBindTarget = null;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingSlider = null;
        this.draggingColor = null;
        this.draggingColorChannel = -1;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        this.scrollOffset = Math.max(0.0F, Math.min(this.maxScroll,
                this.scrollOffset - (float) verticalAmount * 22.0F));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (this.listeningBindTarget != null) {
            int newBind = KeybindManager.isClearKey(key) ? -1 : key;
            String keyLabel = KeybindManager.formatKey(newBind);
            if ("CLEAN_SCREEN".equals(this.listeningBindTarget)) {
                KeybindManager.get().setCleanScreenKeybind(newBind);
                NotificationManager.get().pushKeybindChanged("Чистый экран", keyLabel);
            } else if (this.listeningBindTarget instanceof Module mod) {
                mod.setKeybind(newBind);
                NotificationManager.get().pushKeybindChanged(mod.name(), keyLabel);
            } else if (this.listeningBindTarget instanceof Setting<?> set) {
                set.setKeybind(newBind);
                ModuleManager.get().saveNow();
                NotificationManager.get().pushKeybindChanged(set.name(), keyLabel);
            }
            this.listeningBindTarget = null;
            return true;
        }

        if (this.accountInputFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.accountInputFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (AccountManager.get().switchAccount(this.accountInput)) {
                    this.accountInput = "";
                    this.accountInputFocused = false;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !this.accountInput.isEmpty()) {
                this.accountInput = this.accountInput.substring(0, this.accountInput.length() - 1);
                return true;
            }
            return true;
        }

        if (this.searchFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                this.searchFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !this.searchQuery.isEmpty()) {
                this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
                return true;
            }
            return true;
        }

        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.listeningBindTarget == null) {
            int codepoint = event.codepoint();
            if (this.accountInputFocused) {
                if ((codepoint >= 'a' && codepoint <= 'z')
                        || (codepoint >= 'A' && codepoint <= 'Z')
                        || (codepoint >= '0' && codepoint <= '9')
                        || codepoint == '_') {
                    if (this.accountInput.length() < 16) {
                        this.accountInput += Character.toString(codepoint);
                    }
                }
                return true;
            }
            if (this.searchFocused) {
                if (Character.isLetterOrDigit(codepoint) || codepoint == ' ' || codepoint == '_' || codepoint == '-') {
                    if (this.searchQuery.length() < 24) {
                        this.searchQuery += Character.toString(codepoint);
                    }
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (!this.closing) {
            this.closing = true;
            this.openAnimation.setTarget(0.0F);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private String trimToWidth(String text, int maxW) {
        if (text == null || maxW <= 4) {
            return "";
        }
        if (this.font.width(text) <= maxW) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(4, maxW - 6)) + "..";
    }

    private static String formatFloat(float val, float step) {
        if (step >= 1.0F) {
            return Integer.toString(Math.round(val));
        }
        if (step >= 0.1F) {
            return String.format(Locale.ROOT, "%.1f", val);
        }
        return String.format(Locale.ROOT, "%.2f", val);
    }

    private static float clamp01(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
