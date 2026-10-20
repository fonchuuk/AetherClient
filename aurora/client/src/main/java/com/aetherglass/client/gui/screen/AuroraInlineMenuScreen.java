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
import com.aetherglass.client.modules.impl.ParticleModule;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.FloatSetting;
import com.aetherglass.client.modules.settings.IntSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.particle.ParticlePreset;
import com.aetherglass.client.render.particle.ParticleShape;
import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
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
import java.util.function.IntConsumer;

/**
 * Меню модулей AetherGlass в стиле Matte Black + Neon (сетка карточек в 3 колонки):
 * <ul>
 *   <li>Компактные прямоугольные карточки со скруглёнными углами (10–12px), матовый чёрный фон (#0B0C10);</li>
 *   <li>Когда модуль выключен — обводки и свечения нет; когда включён — загорается неоновая обводка (#00F2FF) с мягким внешним glow;</li>
 *   <li>ЛКМ включает/выключает модуль, ПКМ плавно раскрывает настройки внутри карточки (аккордеон), СКМ вызывает окошко бинда с иконкой клавиатуры;</li>
 *   <li>Слева — узкая панель навигации (Главная, Визуал, Интерфейс, HUD, Аккаунты), справа — аккуратная сетка карточек в 3 колонки с отступами 9px.</li>
 * </ul>
 */
public final class AuroraInlineMenuScreen extends Screen {
    public static final int COLS = 3;
    public static final int COL_GAP = 9;
    public static final int ROW_GAP = 9;
    public static final int CARD_RADIUS = 11;
    public static final int CARD_HEADER_H = 44;

    private static final int SETTING_ROW_H = 19;
    private static final int SETTING_SLIDER_H = 24;
    private static final int COLOR_EXPANDED_H = 34;
    private static final int PARTICLE_SHAPES_H = 22;

    private static final int[] QUICK_SWATCHES = {
            0xFF00F2FF, 0xFF38B6FF, 0xFF9D6BFF, 0xFFFF5CE1,
            0xFFFF9A3C, 0xFF00F5A0, 0xFFFF4D6D, 0xFFFFFFFF
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
    private int activeSliderX;
    private int activeSliderW;
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
        this(parent, null, null);
    }

    public AuroraInlineMenuScreen(Screen parent, Module initialExpanded) {
        this(parent, initialExpanded, null);
    }

    public AuroraInlineMenuScreen(Screen parent, Module initialExpanded, ModuleCategory initialCategory) {
        super(Component.literal("Модули AetherGlass"));
        this.parent = parent;
        this.selectedCategory = initialCategory;
        this.openAnimation.setTarget(1.0F);
        for (Module m : ModuleManager.get().modules()) {
            this.toggleAnims.put(m, m.isEnabled() ? 1.0F : 0.0F);
            boolean exp = (initialExpanded != null && m == initialExpanded);
            this.expandAnims.put(m, exp ? 1.0F : 0.0F);
            if (exp) {
                this.expandedModules.add(m);
            }
        }
    }

    public static AuroraInlineMenuScreen openAccounts(Screen parent) {
        AuroraInlineMenuScreen screen = new AuroraInlineMenuScreen(parent, null, null);
        screen.accountsTabActive = true;
        return screen;
    }

    public void attachHost(int w, int h) {
        this.width = w;
        this.height = h;
    }

    public void setCategoryFilter(ModuleCategory category) {
        this.accountsTabActive = false;
        this.selectedCategory = category;
        this.scrollOffset = 0.0F;
    }

    public void setAccountsTab(boolean active) {
        this.accountsTabActive = active;
        this.scrollOffset = 0.0F;
    }

    public void expandOnly(Module module) {
        this.expandedModules.clear();
        for (Module m : ModuleManager.get().modules()) {
            this.expandAnims.put(m, 0.0F);
        }
        if (module != null) {
            this.expandedModules.add(module);
            this.expandAnims.put(module, 1.0F);
        }
    }

    public void startBindForModule(Module module) {
        this.listeningBindTarget = module;
        if (module != null) {
            this.bindPopupAnims.put(module, 1.0F);
        }
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
            if (this.minecraft != null && this.parent != null) {
                this.minecraft.setScreen(this.parent);
            } else if (this.minecraft != null && this.minecraft.level != null) {
                this.minecraft.setScreen(null);
            }
            return;
        }

        renderEmbedded(graphics, mouseX, mouseY, progress, dt, this.width, this.height, false);
        NotificationManager.get().render(graphics, this.width, this.height);
    }

    /**
     * Позволяет рендерить всю сетку карточек и узкую панель навигации как внутри {@code AuroraInlineMenuScreen},
     * так и напрямую внутри {@code AuroraMenuScreen}.
     */
    public void renderEmbedded(GuiGraphics graphics, int mouseX, int mouseY, float progress, float dt,
                               int screenW, int screenH, boolean titleScreenMode) {
        this.hitZones.clear();

        if (!titleScreenMode) {
            int dimColor = ColorUtil.fade(AuroraTheme.screenDim(), progress);
            graphics.fill(0, 0, screenW, screenH, dimColor);
        }

        int panelW = Math.min(620, screenW - 20);
        int panelH = Math.min(344, screenH - 16);
        int slideY = Math.round((1.0F - progress) * 10.0F);
        int panelX = (screenW - panelW) / 2;
        int panelY = (screenH - panelH) / 2 + slideY;

        // Внешний матовый чёрный корпус окна (#07080B)
        DrawUtil.drawLiquidGlassPanel(graphics, panelX, panelY, panelW, panelH, 12, progress);

        // Слева — узкая панель навигации (116px)
        int sidebarW = 116;
        int sidebarX = panelX + 7;
        int sidebarY = panelY + 7;
        int sidebarH = panelH - 14;
        renderSidebar(graphics, sidebarX, sidebarY, sidebarW, sidebarH, mouseX, mouseY, progress, titleScreenMode);

        // Справа — сетка карточек в 3 колонки
        int bodyX = sidebarX + sidebarW + 9;
        int bodyY = panelY + 7;
        int bodyW = panelW - sidebarW - 23;
        int bodyH = panelH - 14;

        renderHeaderBar(graphics, bodyX, bodyY, bodyW, mouseX, mouseY, progress, titleScreenMode);
        if (this.accountsTabActive) {
            renderAccountsTab(graphics, bodyX, bodyY + 25, bodyW, bodyH - 25, mouseX, mouseY, progress);
        } else {
            renderModuleColumns(graphics, bodyX, bodyY + 25, bodyW, bodyH - 25, mouseX, mouseY, progress, dt);
        }
    }

    // ─────────────────── Узкая левая панель навигации (Matte Black + Neon) ───────────────────

    private void renderSidebar(GuiGraphics graphics, int sx, int sy, int sw, int sh,
                               int mouseX, int mouseY, float alpha, boolean titleScreenMode) {
        DrawUtil.drawMatteNeonCard(graphics, sx, sy, sw, sh, 11, alpha, 0.0F, AuroraTheme.accent());

        // Заголовок клиента
        DrawUtil.drawTextScaled(graphics, this.font, "§lAETHERGLASS", sx + 8, sy + 8,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.88F, false);
        DrawUtil.drawTextScaled(graphics, this.font, "Matte · Neon 1.0.8", sx + 8, sy + 18,
                ColorUtil.fade(0xFF00F2FF, 0.88F * alpha), 0.72F, false);

        // Компактный поиск
        int searchX = sx + 6;
        int searchY = sy + 29;
        int searchW = sw - 12;
        int searchH = 16;
        boolean searchHov = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        DrawUtil.drawMatteNeonCard(graphics, searchX, searchY, searchW, searchH, 8, alpha,
                this.searchFocused ? 0.85F : (searchHov ? 0.35F : 0.0F), AuroraTheme.accent());
        String shown = this.searchQuery.isEmpty()
                ? (this.searchFocused ? "🔍 Поиск...|" : "🔍 Поиск...")
                : ("🔍 " + this.searchQuery + (this.searchFocused ? "|" : ""));
        int searchCol = (this.searchQuery.isEmpty() && !this.searchFocused) ? 0xFF727B8E : 0xFFFFFFFF;
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(shown, Math.round((searchW - 10) / 0.78F)),
                searchX + 5, searchY + 5, ColorUtil.fade(searchCol, alpha), 0.78F, false);
        this.hitZones.add(new HitZone(searchX, searchY, searchW, searchH,
                () -> this.searchFocused = true,
                () -> {
                    this.searchQuery = "";
                    this.searchFocused = false;
                },
                null));

        graphics.fill(sx + 8, sy + 49, sx + sw - 8, sy + 50, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(16 * alpha)));

        // Разделы навигации: Главная, Визуал, Интерфейс, HUD, Аккаунты
        int navY = sy + 54;
        navY = renderSidebarCategory(graphics, sx + 6, navY, sw - 12, "Главная",
                null, false, ModuleManager.get().modules().size(), mouseX, mouseY, alpha);
        for (ModuleCategory cat : ModuleCategory.values()) {
            int count = ModuleManager.get().byCategory(cat).size();
            navY = renderSidebarCategory(graphics, sx + 6, navY, sw - 12, cat.title(),
                    cat, false, count, mouseX, mouseY, alpha);
        }

        // Кнопка HUD Редактора в навигации
        navY = renderSidebarAction(graphics, sx + 6, navY, sw - 12, "HUD Редактор", false, mouseX, mouseY, alpha, () -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudEditorScreen(this.parent != null ? this.parent : this));
            }
        });

        // Вкладка Аккаунты
        navY = renderSidebarCategory(graphics, sx + 6, navY, sw - 12, "Аккаунты",
                null, true, AccountManager.get().accounts().size(), mouseX, mouseY, alpha);

        // Внизу узкой левой панели — быстрые кнопки игры / чистого экрана
        if (titleScreenMode && this.minecraft != null && this.minecraft.level == null) {
            int botY = sy + sh - 76;
            graphics.fill(sx + 8, botY - 4, sx + sw - 8, botY - 3, ColorUtil.withAlpha(0xFFFFFFFF, Math.round(16 * alpha)));
            Screen host = this.parent != null ? this.parent : this;
            botY = renderSidebarAction(graphics, sx + 6, botY, sw - 12, "▶ Одиночная", true, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new SelectWorldScreen(host)));
            botY = renderSidebarAction(graphics, sx + 6, botY, sw - 12, "🌐 Сетевая", false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new JoinMultiplayerScreen(host)));
            botY = renderSidebarAction(graphics, sx + 6, botY, sw - 12, "⚙ Опции MC", false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new OptionsScreen(host, this.minecraft.options)));
            renderSidebarAction(graphics, sx + 6, botY, sw - 12, "✕ Выход", false, mouseX, mouseY, alpha,
                    () -> this.minecraft.stop());
        } else {
            int botY = sy + sh - 24;
            KeybindManager km = KeybindManager.get();
            String cleanKey = KeybindManager.formatKey(km.cleanScreenKeybind());
            boolean cleanListening = "CLEAN_SCREEN".equals(this.listeningBindTarget);
            String cleanLabel = cleanListening ? "⌨ Клавиша..." : ("Чистый [" + cleanKey + "]");
            int btnX = sx + 6;
            int btnW = sw - 12;
            int btnH = 18;
            boolean cleanHov = inside(mouseX, mouseY, btnX, botY, btnW, btnH);
            DrawUtil.drawMatteNeonCard(graphics, btnX, botY, btnW, btnH, 8, alpha,
                    (km.isCleanScreenActive() || cleanListening) ? 0.90F : (cleanHov ? 0.35F : 0.0F), AuroraTheme.accent());
            DrawUtil.drawCenteredTextScaled(graphics, this.font, cleanLabel, btnX + btnW / 2, botY + 6,
                    ColorUtil.fade(0xFFFFFFFF, alpha), 0.78F, false);
            this.hitZones.add(new HitZone(btnX, botY, btnW, btnH,
                    km::toggleCleanScreen,
                    km::toggleCleanScreen,
                    () -> this.listeningBindTarget = cleanListening ? null : "CLEAN_SCREEN"));
        }
    }

    private int renderSidebarCategory(GuiGraphics graphics, int x, int y, int w, String title,
                                      ModuleCategory category, boolean isAccountsTab, int count,
                                      int mouseX, int mouseY, float alpha) {
        boolean active = isAccountsTab ? this.accountsTabActive : (!this.accountsTabActive && this.selectedCategory == category);
        int h = 18;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);

        DrawUtil.drawMatteNeonCard(graphics, x, y, w, h, 8, alpha,
                active ? 0.95F : (hovered ? 0.30F : 0.0F), AuroraTheme.accent());

        int textCol = active ? 0xFFFFFFFF : (hovered ? 0xFFE6ECF8 : 0xFF949EB2);
        DrawUtil.drawTextScaled(graphics, this.font, (active ? "§l" : "") + title, x + 7, y + 6,
                ColorUtil.fade(textCol, alpha), 0.80F, false);

        String cntStr = Integer.toString(count);
        int cntW = Math.round(this.font.width(cntStr) * 0.75F);
        DrawUtil.drawTextScaled(graphics, this.font, cntStr, x + w - cntW - 6, y + 6,
                ColorUtil.fade(active ? AuroraTheme.accent() : 0xFF687285, alpha), 0.75F, false);

        this.hitZones.add(new HitZone(x, y, w, h, () -> {
            this.accountsTabActive = isAccountsTab;
            if (!isAccountsTab) {
                this.selectedCategory = category;
            }
            this.scrollOffset = 0.0F;
        }, null, null));
        return y + h + 3;
    }

    private int renderSidebarAction(GuiGraphics graphics, int x, int y, int w, String label,
                                    boolean highlighted, int mouseX, int mouseY, float alpha, Runnable action) {
        int h = 16;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        DrawUtil.drawMatteNeonCard(graphics, x, y, w, h, 8, alpha,
                hovered ? 0.85F : (highlighted ? 0.45F : 0.0F), AuroraTheme.accent());
        int col = (hovered || highlighted) ? 0xFFFFFFFF : 0xFF9AA4B8;
        DrawUtil.drawTextScaled(graphics, this.font, label, x + 7, y + 5,
                ColorUtil.fade(col, alpha), 0.78F, false);
        this.hitZones.add(new HitZone(x, y, w, h, action, null, null));
        return y + h + 3;
    }

    // ─────────────────── Верхняя информационная плашка над сеткой карточек ───────────────────

    private void renderHeaderBar(GuiGraphics graphics, int x, int y, int width,
                                 int mouseX, int mouseY, float alpha, boolean titleScreenMode) {
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, 20, 9, alpha, 0.0F, AuroraTheme.accent());
        String catName = this.accountsTabActive
                ? "Аккаунты (Offline-смена в игре)"
                : (this.selectedCategory == null ? "Главная · Все модули (сетка 3 колонки)" : this.selectedCategory.title());
        DrawUtil.drawTextScaled(graphics, this.font, "§l" + catName, x + 8, y + 6,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.82F, false);

        String hint = "ЛКМ: Вкл/Выкл · ПКМ: Настройки · СКМ: Бинд";
        int hintW = Math.round(this.font.width(hint) * 0.72F);
        int rightEdge = titleScreenMode ? (x + width - 8) : (x + width - 24);
        int accW = 84;
        int accX = rightEdge - accW;
        int accY = y + 3;

        if (accX - hintW - 10 > x + 130) {
            DrawUtil.drawTextScaled(graphics, this.font, hint, accX - hintW - 8, y + 7,
                    ColorUtil.fade(0xFF727B8E, alpha), 0.72F, false);
        }

        // Кнопка текущего аккаунта
        String curUser = "👤 " + AccountManager.get().currentUsername();
        boolean accHov = inside(mouseX, mouseY, accX, accY, accW, 14);
        DrawUtil.drawMatteNeonCard(graphics, accX, accY, accW, 14, 7, alpha,
                this.accountsTabActive ? 0.90F : (accHov ? 0.50F : 0.0F), AuroraTheme.accent());
        DrawUtil.drawCenteredTextScaled(graphics, this.font,
                trimToWidth(curUser, Math.round((accW - 8) / 0.75F)),
                accX + accW / 2, accY + 4, ColorUtil.fade(0xFFFFFFFF, alpha), 0.75F, false);
        this.hitZones.add(new HitZone(accX, accY, accW, 14, () -> {
            this.accountsTabActive = !this.accountsTabActive;
            this.scrollOffset = 0.0F;
        }, null, null));

        if (!titleScreenMode) {
            int closeX = x + width - 18;
            int closeY = y + 3;
            boolean closeHov = inside(mouseX, mouseY, closeX, closeY, 14, 14);
            DrawUtil.drawMatteNeonCard(graphics, closeX, closeY, 14, 14, 6, alpha,
                    closeHov ? 0.85F : 0.0F, AuroraTheme.danger());
            DrawUtil.drawCenteredTextScaled(graphics, this.font, "✕", closeX + 7, closeY + 4,
                    ColorUtil.fade(closeHov ? AuroraTheme.danger() : 0xFF9AA4B8, alpha), 0.78F, false);
            this.hitZones.add(new HitZone(closeX, closeY, 14, 14, this::onClose, null, null));
        }
    }

    // ─────────────────── СЕТКА КАРТОЧЕК МОДУЛЕЙ В 3 КОЛОНКИ (MATTE BLACK + NEON) ───────────────────

    private void renderModuleColumns(GuiGraphics graphics, int bodyX, int bodyY, int bodyW, int bodyH,
                                     int mouseX, int mouseY, float alpha, float dt) {
        List<Module> modules = filteredModules();
        int colW = (bodyW - COL_GAP * (COLS - 1)) / COLS;
        int[] colY = new int[COLS];
        for (int i = 0; i < COLS; i++) {
            colY[i] = bodyY + 2 - Math.round(this.scrollOffset);
        }

        // Оставляем +6px по краям scissor для мягкого неонового свечения (glow) крайних карточек
        graphics.enableScissor(bodyX - 6, bodyY - 4, bodyX + bodyW + 6, bodyY + bodyH + 4);

        int maxBottom = bodyY;
        for (int idx = 0; idx < modules.size(); idx++) {
            Module module = modules.get(idx);
            int col = idx % COLS;
            int cardX = bodyX + col * (colW + COL_GAP);
            int cardY = colY[col];

            int cardH = renderModuleCard(graphics, module, cardX, cardY, colW,
                    bodyY - 4, bodyY + bodyH + 4, mouseX, mouseY, alpha, dt);
            colY[col] += cardH + ROW_GAP;
            maxBottom = Math.max(maxBottom, colY[col] + Math.round(this.scrollOffset));
        }

        graphics.disableScissor();

        this.maxScroll = Math.max(0.0F, (maxBottom - bodyY) - bodyH + 10.0F);
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
        if (Math.abs(toggleTarget - toggleCurr) < 0.005F) {
            toggleCurr = toggleTarget;
        }
        this.toggleAnims.put(module, toggleCurr);

        int contentFullH = computeExpandedContentHeight(module);
        int visibleContentH = Math.round(contentFullH * Easing.easeOutCubic(expandCurr));
        int totalH = CARD_HEADER_H + visibleContentH;

        if (y + totalH < clipTop || y > clipBottom) {
            return totalH;
        }

        // 1. ВНЕШНИЙ ВИД КАРТОЧКИ: Матовый чёрный (#0B0C10) + Неоновая обводка с glow ТОЛЬКО при включённом модуле!
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, totalH, CARD_RADIUS, alpha, toggleCurr, AuroraTheme.accent());

        // 2. Окошко бинда (СКМ) в правом верхнем углу карточки (иконка клавиатуры -> буква)
        int bindBadgeW = renderCornerKeybindPopup(graphics, module, module.keybind(),
                key -> {
                    module.setKeybind(key);
                    NotificationManager.get().pushKeybindChanged(module.name(), KeybindManager.formatKey(key));
                },
                x + width - 7, y + 7, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

        // 3. ТЕКСТ ВНУТРИ КАРТОЧКИ: Название белым жирным + короткое описание серым мелким шрифтом
        float titleScale = 0.88F;
        int availTitlePx = Math.max(36, width - 16 - (bindBadgeW > 0 ? bindBadgeW + 4 : 0));
        String titleStr = trimToWidth(module.name(), Math.round(availTitlePx / titleScale));
        DrawUtil.drawTextScaled(graphics, this.font, "§l" + titleStr, x + 8, y + 9,
                ColorUtil.fade(0xFFFFFFFF, alpha), titleScale, false);

        float descScale = 0.74F;
        int availDescPx = Math.max(40, width - 16);
        String descStr = trimToWidth(module.description(), Math.round(availDescPx / descScale));
        DrawUtil.drawTextScaled(graphics, this.font, descStr, x + 8, y + 24,
                ColorUtil.fade(0xFF8C94A6, alpha), descScale, false);

        // 4. УПРАВЛЕНИЕ НА ШАПКЕ КАРТОЧКИ:
        // ЛКМ — включает/выключает модуль (зажигает/гасит неоновую обводку)
        // ПКМ — плавно выкатывает настройки вниз внутри карточки (аккордеон)
        // СКМ — вызывает маленькое окошко с иконкой клавиатуры для назначения клавиши
        Runnable toggleExpand = () -> {
            if (this.expandedModules.contains(module)) {
                this.expandedModules.remove(module);
            } else {
                this.expandedModules.add(module);
            }
        };

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            this.hitZones.add(new HitZone(x, y, width, CARD_HEADER_H,
                    module::toggle,
                    toggleExpand,
                    () -> this.listeningBindTarget = (this.listeningBindTarget == module) ? null : module));
        }

        // 5. АККОРДЕОН НАСТРОЕК ВНУТРИ КАРТОЧКИ (при раскрытии по ПКМ)
        if (visibleContentH > 3) {
            int innerTop = Math.max(clipTop, y + CARD_HEADER_H);
            int innerBottom = Math.min(clipBottom, y + CARD_HEADER_H + visibleContentH);
            if (innerBottom > innerTop) {
                graphics.fill(x + 7, y + CARD_HEADER_H - 1, x + width - 7, y + CARD_HEADER_H,
                        ColorUtil.withAlpha(0xFFFFFFFF, Math.round(18 * alpha * expandCurr)));

                graphics.enableScissor(x + 2, innerTop, x + width - 2, innerBottom);
                renderInlineSettings(graphics, module, x + 7, y + CARD_HEADER_H + 4, width - 14,
                        innerTop, innerBottom, mouseX, mouseY, alpha * expandCurr, dt);
                // Восстанавливаем внешний scissor сетки
                graphics.disableScissor();
                graphics.enableScissor(x - 500, clipTop, x + 1500, clipBottom);
            }
        }

        return totalH;
    }

    /**
     * Окошко назначения клавиши (по СКМ):
     * - При нажатии СКМ плавно выезжает плашка с иконкой клавиатуры «⌨ ...»;
     * - После нажатия клавиши окошко плавно уменьшается и показывает назначенную букву («R», «G», и т.д.).
     */
    private int renderCornerKeybindPopup(GuiGraphics graphics, Object target, int currentKey,
                                         IntConsumer onBindChanged, int rightX, int topY,
                                         int mouseX, int mouseY, int clipTop, int clipBottom,
                                         float alpha, float dt) {
        boolean listening = (this.listeningBindTarget == target);
        boolean hasKey = currentKey > 0;
        float anim = this.bindPopupAnims.getOrDefault(target, 0.0F);
        float targetAnim = listening ? 1.0F : 0.0F;
        anim += (targetAnim - anim) * Math.min(1.0F, dt * 14.0F * AuroraTheme.motion());
        if (Math.abs(targetAnim - anim) < 0.005F) {
            anim = targetAnim;
        }
        this.bindPopupAnims.put(target, anim);

        if (!listening && !hasKey && anim <= 0.02F) {
            return 0;
        }

        String keyLetter = hasKey ? KeybindManager.formatKey(currentKey) : "";
        String text = listening ? "  ..." : keyLetter;
        float scale = 0.72F;
        int baseW = Math.max(14, Math.round(this.font.width(keyLetter.isEmpty() ? "K" : keyLetter) * scale) + 8);
        int listenW = 30;
        int badgeW = Math.round(baseW + (listenW - baseW) * Easing.easeOutCubic(anim));
        int badgeH = 12;
        int bx = rightX - badgeW;

        boolean hov = inside(mouseX, mouseY, bx, topY, badgeW, badgeH) && mouseY >= clipTop && mouseY <= clipBottom;
        DrawUtil.drawMatteNeonCard(graphics, bx, topY, badgeW, badgeH, 5, alpha,
                listening ? 1.0F : (hov ? 0.65F : 0.35F), AuroraTheme.accent());

        int textCol = listening ? AuroraTheme.accent() : 0xFFE8F0FF;
        if (listening || anim > 0.35F) {
            drawKeyboardIcon(graphics, bx + 4, topY + 3, ColorUtil.fade(AuroraTheme.accent(), alpha));
            DrawUtil.drawTextScaled(graphics, this.font, "...", bx + 16, topY + 3,
                    ColorUtil.fade(textCol, alpha), scale, false);
        } else {
            DrawUtil.drawCenteredTextScaled(graphics, this.font, text, bx + badgeW / 2, topY + 3,
                    ColorUtil.fade(textCol, alpha), scale, false);
        }

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            this.hitZones.add(new HitZone(bx - 1, topY - 1, badgeW + 2, badgeH + 2,
                    () -> this.listeningBindTarget = listening ? null : target,
                    () -> onBindChanged.accept(-1),
                    () -> this.listeningBindTarget = listening ? null : target));
        }
        return badgeW;
    }

    private static void drawKeyboardIcon(GuiGraphics graphics, int x, int y, int color) {
        // Компактная пиксельная иконка клавиатуры 9×6px
        graphics.fill(x, y, x + 9, y + 1, color);
        graphics.fill(x, y + 5, x + 9, y + 6, color);
        graphics.fill(x, y + 1, x + 1, y + 5, color);
        graphics.fill(x + 8, y + 1, x + 9, y + 5, color);
        // Клавиши
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
        graphics.fill(x + 4, y + 2, x + 5, y + 3, color);
        graphics.fill(x + 6, y + 2, x + 7, y + 3, color);
        // Пробел
        graphics.fill(x + 2, y + 4, x + 7, y + 5, color);
    }

    private int computeExpandedContentHeight(Module module) {
        int h = 8;
        if (module instanceof ParticleModule) {
            h += PARTICLE_SHAPES_H + 3;
        }
        for (Setting<?> setting : module.settings()) {
            if (setting instanceof FloatSetting || setting instanceof IntSetting) {
                h += SETTING_SLIDER_H;
            } else {
                h += SETTING_ROW_H;
            }
            if (setting instanceof ColorSetting colorSetting && this.expandedColors.contains(colorSetting)) {
                h += COLOR_EXPANDED_H;
            }
        }
        return h;
    }

    private void renderInlineSettings(GuiGraphics graphics, Module module, int x, int startY, int width,
                                      int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, float dt) {
        int curY = startY;

        if (module instanceof ParticleModule particleMod) {
            renderInlineParticleShapeBar(graphics, particleMod, x, curY, width, clipTop, clipBottom, mouseX, mouseY, alpha);
            curY += PARTICLE_SHAPES_H + 3;
        }

        for (Setting<?> setting : module.settings()) {
            curY = renderSettingRow(graphics, module, setting, x, curY, width, clipTop, clipBottom, mouseX, mouseY, alpha, dt);
        }
    }

    private void renderInlineParticleShapeBar(GuiGraphics graphics, ParticleModule module, int x, int y, int width,
                                              int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        ParticleShape[] shapes = ParticleShape.values();
        int gap = 2;
        int cellW = (width - gap * (shapes.length - 1)) / shapes.length;
        int cellH = 18;
        String[] icons = {"✦", "⚡", "◎", "❄", "●", "❖"};

        for (int i = 0; i < shapes.length; i++) {
            ParticleShape shape = shapes[i];
            int cx = x + i * (cellW + gap);
            boolean active = module.shapeSetting().get() == shape;
            boolean hovered = inside(mouseX, mouseY, cx, y, cellW, cellH)
                    && mouseY >= clipTop && mouseY <= clipBottom;

            DrawUtil.drawMatteNeonCard(graphics, cx, y, cellW, cellH, 6, alpha,
                    active ? 0.95F : (hovered ? 0.40F : 0.0F), AuroraTheme.accent());

            String icon = i < icons.length ? icons[i] : "✦";
            DrawUtil.drawCenteredTextScaled(graphics, this.font, icon, cx + cellW / 2, y + 5,
                    ColorUtil.fade(active ? AuroraTheme.accent() : 0xFF9AA4B8, alpha), 0.78F, false);

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(cx, y, cellW, cellH, () -> module.shapeSetting().set(shape), null, null));
            }
        }
    }

    // ─────────────────── НАСТРОЙКИ ВНУТРИ КАРТОЧКИ (ПОЛЗУНКИ / КРУЖОК ЦВЕТА / ТУМБЛЕРЫ) ───────────────────

    @SuppressWarnings({"unchecked", "rawtypes"})
    private int renderSettingRow(GuiGraphics graphics, Module module, Setting<?> setting,
                                 int x, int y, int width, int clipTop, int clipBottom,
                                 int mouseX, int mouseY, float alpha, float dt) {
        if (setting instanceof FloatSetting floatSetting) {
            if (y + SETTING_SLIDER_H >= clipTop && y <= clipBottom) {
                float norm = (floatSetting.get() - floatSetting.min())
                        / Math.max(0.0001F, floatSetting.max() - floatSetting.min());
                renderNeonSliderRow(graphics, floatSetting, norm,
                        formatFloat(floatSetting.get(), floatSetting.step()),
                        x, y, width, clipTop, clipBottom, mouseX, mouseY, alpha);
            }
            return y + SETTING_SLIDER_H;
        }

        if (setting instanceof IntSetting intSetting) {
            if (y + SETTING_SLIDER_H >= clipTop && y <= clipBottom) {
                float norm = (intSetting.get() - intSetting.min())
                        / (float) Math.max(1, intSetting.max() - intSetting.min());
                renderNeonSliderRow(graphics, intSetting, norm,
                        Integer.toString(intSetting.get()),
                        x, y, width, clipTop, clipBottom, mouseX, mouseY, alpha);
            }
            return y + SETTING_SLIDER_H;
        }

        boolean visible = y + SETTING_ROW_H >= clipTop && y <= clipBottom;

        if (setting instanceof BooleanSetting boolSetting) {
            if (visible) {
                // Компактный переключатель (тумблер)
                int swW = 20;
                int swH = 10;
                int swX = x + width - swW;
                int swY = y + (SETTING_ROW_H - swH) / 2;

                float scale = 0.75F;
                int maxLabelW = Math.round((swX - x - 5) / scale);
                DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(boolSetting.name(), maxLabelW),
                        x, y + 5, ColorUtil.fade(0xFFB4BECE, alpha), scale, false);

                boolean on = boolSetting.isEnabled();
                DrawUtil.drawMatteNeonCard(graphics, swX, swY, swW, swH, 5, alpha,
                        on ? 0.95F : 0.0F, AuroraTheme.accent());
                int knobX = swX + (on ? swW - 8 : 2);
                int knobCol = on ? AuroraTheme.accent() : 0xFF7A8499;
                DrawUtil.drawSliderKnob(graphics, knobX, swY + 2, 6, ColorUtil.fade(knobCol, alpha));

                if (mouseY >= clipTop && mouseY <= clipBottom) {
                    this.hitZones.add(new HitZone(x, y, width, SETTING_ROW_H,
                            boolSetting::toggle,
                            boolSetting::reset,
                            null));
                }
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof EnumSetting enumSetting) {
            if (visible) {
                float scale = 0.72F;
                String valText = "◂ " + enumSetting.get().toString() + " ▸";
                int pillW = Math.min(width * 54 / 100, Math.round(this.font.width(valText) * scale) + 10);
                int pillX = x + width - pillW;
                int pillY = y + 3;

                int maxLabelW = Math.round((pillX - x - 4) / 0.74F);
                DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(enumSetting.name(), maxLabelW),
                        x, y + 5, ColorUtil.fade(0xFFB4BECE, alpha), 0.74F, false);

                boolean pillHov = inside(mouseX, mouseY, pillX, pillY, pillW, 13);
                DrawUtil.drawMatteNeonCard(graphics, pillX, pillY, pillW, 13, 6, alpha,
                        pillHov ? 0.80F : 0.30F, AuroraTheme.accent());
                String trimmedVal = trimToWidth(valText, Math.round((pillW - 6) / scale));
                DrawUtil.drawCenteredTextScaled(graphics, this.font, trimmedVal,
                        pillX + pillW / 2, pillY + 3, ColorUtil.fade(0xFFFFFFFF, alpha), scale, false);

                if (mouseY >= clipTop && mouseY <= clipBottom) {
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
                            null));
                }
            }
            return y + SETTING_ROW_H;
        }

        if (setting instanceof ColorSetting colorSetting) {
            boolean expanded = this.expandedColors.contains(colorSetting);
            if (visible) {
                int color = colorSetting.rgb();
                int circleSize = 11;
                int circleX = x + width - circleSize - 1;
                int circleY = y + (SETTING_ROW_H - circleSize) / 2;

                float scale = 0.75F;
                int maxLabelW = Math.round((circleX - x - 6) / scale);
                DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(colorSetting.name(), maxLabelW),
                        x, y + 5, ColorUtil.fade(0xFFB4BECE, alpha), scale, false);

                // Аккуратный кружок выбора цвета (без растянутой радуги на пол-экрана!)
                DrawUtil.drawSliderKnob(graphics, circleX - 1, circleY - 1, circleSize + 2,
                        ColorUtil.fade(expanded ? AuroraTheme.accent() : 0xFF3E4656, alpha));
                DrawUtil.drawSliderKnob(graphics, circleX, circleY, circleSize, ColorUtil.fade(color, alpha));

                if (mouseY >= clipTop && mouseY <= clipBottom) {
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
            }
            int nextY = y + SETTING_ROW_H;
            if (expanded) {
                if (nextY + COLOR_EXPANDED_H >= clipTop && nextY <= clipBottom) {
                    renderCompactColorPalettePopover(graphics, colorSetting, x, nextY, width, clipTop, clipBottom, mouseX, mouseY, alpha);
                }
                nextY += COLOR_EXPANDED_H;
            }
            return nextY;
        }

        return y + SETTING_ROW_H;
    }

    /**
     * Ползунок (слайдер): аккуратная тонкая полоска с неоновым кружком.
     */
    private void renderNeonSliderRow(GuiGraphics graphics, Setting<?> setting, float normalized,
                                     String valueText, int x, int y, int width,
                                     int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        float clamped = clamp01(normalized);
        float scale = 0.74F;

        // Верхняя строка: название слева, числовое значение справа
        int valW = Math.round(this.font.width(valueText) * scale);
        int maxLabelW = Math.round((width - valW - 6) / scale);
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(setting.name(), maxLabelW),
                x, y + 2, ColorUtil.fade(0xFFB4BECE, alpha), scale, false);
        DrawUtil.drawTextScaled(graphics, this.font, valueText,
                x + width - valW, y + 2, ColorUtil.fade(AuroraTheme.accent(), alpha), scale, false);

        // Нижняя строка: тонкая полоска (2px) с неоновым кружком
        int trackX = x + 1;
        int trackW = width - 2;
        int trackY = y + 15;
        int trackH = 2;

        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH,
                ColorUtil.withAlpha(0xFF202532, Math.round(230 * alpha)));
        int fillW = Math.max(2, Math.round(trackW * clamped));
        graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH,
                ColorUtil.fade(AuroraTheme.accent(), alpha));

        int knobSize = 7;
        int knobX = trackX + Math.round((trackW - knobSize) * clamped);
        int knobY = trackY - 3;
        // Мягкое неоновое свечение вокруг кружка ползунка + яркий неоновый кружок
        DrawUtil.drawSliderKnob(graphics, knobX - 2, knobY - 2, knobSize + 4,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(95 * alpha)));
        DrawUtil.drawSliderKnob(graphics, knobX, knobY, knobSize,
                ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawSliderKnob(graphics, knobX + 2, knobY + 2, knobSize - 4,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        if (this.draggingSlider == setting) {
            updateSliderFromMouse(setting, mouseX, trackX, trackW);
        }

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            this.hitZones.add(new HitZone(x, y, width, SETTING_SLIDER_H,
                    () -> {
                        this.draggingSlider = setting;
                        this.activeSliderX = trackX;
                        this.activeSliderW = trackW;
                        updateSliderFromMouse(setting, mouseX, trackX, trackW);
                    },
                    setting::reset,
                    null));
        }
    }

    private void updateSliderFromMouse(Setting<?> setting, double mouseX, int sliderX, int sliderW) {
        float t = (float) ((mouseX - sliderX) / (double) Math.max(1, sliderW));
        t = clamp01(t);
        if (setting instanceof FloatSetting fs) {
            fs.set(fs.min() + t * (fs.max() - fs.min()));
        } else if (setting instanceof IntSetting is) {
            is.set(Math.round(is.min() + t * (is.max() - is.min())));
        }
    }

    /**
     * Компактная палитра цветов, открывающаяся по клику на кружок цвета:
     * 8 аккуратных кружков-пресетов + 2 коротких мини-ползунка тона и яркости внутри карточки.
     */
    private void renderCompactColorPalettePopover(GuiGraphics graphics, ColorSetting setting,
                                                  int x, int y, int width, int clipTop, int clipBottom,
                                                  int mouseX, int mouseY, float alpha) {
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, COLOR_EXPANDED_H - 2, 7, alpha, 0.35F, AuroraTheme.accent());

        // 1. Ряд из 8 аккуратных цветных кружков
        int dotSize = 9;
        int totalDotsW = QUICK_SWATCHES.length * dotSize;
        int gap = Math.max(2, (width - 10 - totalDotsW) / (QUICK_SWATCHES.length - 1));
        int dotY = y + 5;
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            int presetCol = QUICK_SWATCHES[i];
            int dx = x + 5 + i * (dotSize + gap);
            boolean selected = (setting.rgb() & 0xFFFFFF) == (presetCol & 0xFFFFFF);
            if (selected) {
                DrawUtil.drawSliderKnob(graphics, dx - 1, dotY - 1, dotSize + 2, ColorUtil.fade(0xFFFFFFFF, alpha));
            }
            DrawUtil.drawSliderKnob(graphics, dx, dotY, dotSize, ColorUtil.fade(presetCol, alpha));
            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(dx - 1, dotY - 1, dotSize + 2, dotSize + 2,
                        () -> setting.setRgb(presetCol), null, null));
            }
        }

        // 2. Короткий тонкий мини-ползунок оттенка (Tone) и яркости (Bri)
        int color = setting.rgb();
        float hue = ColorUtil.hueOf(color);
        float sat = ColorUtil.saturationOf(color);
        float bri = ColorUtil.brightnessOf(color);

        int barY = y + 21;
        int halfW = (width - 16) / 2;
        int hueX = x + 5;
        int briX = hueX + halfW + 6;

        graphics.fill(hueX, barY, hueX + halfW, barY + 2, ColorUtil.withAlpha(0xFF222836, Math.round(230 * alpha)));
        graphics.fill(hueX, barY, hueX + Math.max(2, Math.round(hue * halfW)), barY + 2,
                ColorUtil.fade(ColorUtil.hsb(hue, 0.95F, 1.0F), alpha));
        DrawUtil.drawSliderKnob(graphics, hueX + Math.round(hue * (halfW - 5)), barY - 2, 6,
                ColorUtil.fade(ColorUtil.hsb(hue, 0.95F, 1.0F), alpha));

        graphics.fill(briX, barY, briX + halfW, barY + 2, ColorUtil.withAlpha(0xFF222836, Math.round(230 * alpha)));
        graphics.fill(briX, barY, briX + Math.max(2, Math.round(bri * halfW)), barY + 2,
                ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawSliderKnob(graphics, briX + Math.round(bri * (halfW - 5)), barY - 2, 6,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        if (this.draggingColor == setting) {
            if (this.draggingColorChannel == 0) {
                float newH = clamp01((float) (mouseX - hueX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(newH, Math.max(0.75F, sat), Math.max(0.5F, bri)));
            } else if (this.draggingColorChannel == 1) {
                float newB = clamp01((float) (mouseX - briX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(hue, sat, Math.max(0.15F, newB)));
            }
        }

        if (mouseY >= clipTop && mouseY <= clipBottom) {
            this.hitZones.add(new HitZone(hueX - 2, barY - 4, halfW + 4, 10, () -> {
                this.draggingColor = setting;
                this.draggingColorChannel = 0;
                float newH = clamp01((float) (mouseX - hueX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(newH, Math.max(0.75F, sat), Math.max(0.5F, bri)));
            }, null, null));
            this.hitZones.add(new HitZone(briX - 2, barY - 4, halfW + 4, 10, () -> {
                this.draggingColor = setting;
                this.draggingColorChannel = 1;
                float newB = clamp01((float) (mouseX - briX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(hue, sat, Math.max(0.15F, newB)));
            }, null, null));
        }
    }

    // ─────────────────── ВКЛАДКА «АККАУНТЫ» (СЕТКА КАРТОЧЕК В 3 КОЛОНКИ) ───────────────────

    private void renderAccountsTab(GuiGraphics graphics, int bodyX, int bodyY, int bodyW, int bodyH,
                                   int mouseX, int mouseY, float alpha) {
        AccountManager am = AccountManager.get();
        String currentName = am.currentUsername();

        // Верхняя матовая карточка ввода и быстрой смены ника
        int topH = 38;
        DrawUtil.drawMatteNeonCard(graphics, bodyX, bodyY, bodyW, topH, CARD_RADIUS, alpha, 0.90F, AuroraTheme.accent());
        DrawUtil.drawTextScaled(graphics, this.font, "§lАктивный: " + currentName,
                bodyX + 10, bodyY + 8, ColorUtil.fade(0xFFFFFFFF, alpha), 0.85F, false);
        DrawUtil.drawTextScaled(graphics, this.font, "Мгновенная Offline-смена без перезапуска",
                bodyX + 10, bodyY + 21, ColorUtil.fade(0xFF8C94A6, alpha), 0.74F, false);

        int rndW = 78;
        int rndX = bodyX + bodyW - rndW - 8;
        int rndY = bodyY + 10;
        boolean rndHov = inside(mouseX, mouseY, rndX, rndY, rndW, 18);
        DrawUtil.drawMatteNeonCard(graphics, rndX, rndY, rndW, 18, 7, alpha, rndHov ? 0.95F : 0.40F, AuroraTheme.accent());
        DrawUtil.drawCenteredTextScaled(graphics, this.font, "🎲 Случайный", rndX + rndW / 2, rndY + 6,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.76F, false);
        this.hitZones.add(new HitZone(rndX, rndY, rndW, 18, () -> {
            this.accountInput = am.switchRandomAccount();
        }, null, null));

        int boxW = 132;
        int boxX = rndX - boxW - 6;
        int boxY = bodyY + 10;
        boolean boxHov = inside(mouseX, mouseY, boxX, boxY, boxW, 18);
        DrawUtil.drawMatteNeonCard(graphics, boxX, boxY, boxW, 18, 7, alpha,
                this.accountInputFocused ? 0.95F : (boxHov ? 0.45F : 0.0F), AuroraTheme.accent());
        String shownInput = this.accountInput.isEmpty()
                ? (this.accountInputFocused ? "Введите ник...|" : "✎ Новый ник + Enter...")
                : (this.accountInput + (this.accountInputFocused ? "|" : ""));
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(shownInput, Math.round((boxW - 10) / 0.76F)),
                boxX + 6, boxY + 6, ColorUtil.fade(this.accountInput.isEmpty() ? 0xFF727B8E : 0xFFFFFFFF, alpha), 0.76F, false);
        this.hitZones.add(new HitZone(boxX, boxY, boxW, 18, () -> {
            this.accountInputFocused = true;
            this.searchFocused = false;
        }, () -> this.accountInput = "", null));

        // Сетка сохранённых аккаунтов в 3 колонки
        int gridTop = bodyY + topH + 9;
        int gridH = Math.max(40, bodyY + bodyH - gridTop);
        List<String> saved = am.accounts();
        int cardW = (bodyW - COL_GAP * (COLS - 1)) / COLS;
        int cardH = 40;

        graphics.enableScissor(bodyX - 6, gridTop - 4, bodyX + bodyW + 6, gridTop + gridH + 4);
        for (int i = 0; i < saved.size(); i++) {
            String acc = saved.get(i);
            int r = i / COLS;
            int c = i % COLS;
            int cx = bodyX + c * (cardW + COL_GAP);
            int cy = gridTop + r * (cardH + ROW_GAP);

            boolean isCurrent = acc.equalsIgnoreCase(currentName);
            DrawUtil.drawMatteNeonCard(graphics, cx, cy, cardW, cardH, CARD_RADIUS, alpha,
                    isCurrent ? 1.0F : 0.0F, AuroraTheme.accent());

            DrawUtil.drawTextScaled(graphics, this.font, "§l" + trimToWidth(acc, Math.round((cardW - 30) / 0.85F)),
                    cx + 9, cy + 9, ColorUtil.fade(0xFFFFFFFF, alpha), 0.85F, false);
            DrawUtil.drawTextScaled(graphics, this.font, isCurrent ? "● Активен в сессии" : "ЛКМ — переключить",
                    cx + 9, cy + 22, ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF8C94A6, alpha), 0.72F, false);

            int delX = cx + cardW - 18;
            int delY = cy + 7;
            DrawUtil.drawCenteredTextScaled(graphics, this.font, "✕", delX + 5, delY + 2,
                    ColorUtil.fade(0xFF727B8E, alpha), 0.75F, false);

            this.hitZones.add(new HitZone(cx, cy, cardW - 22, cardH, () -> am.switchAccount(acc), null, null));
            this.hitZones.add(new HitZone(delX, delY, 14, 14, () -> am.removeAccount(acc), null, null));
        }
        graphics.disableScissor();
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingSlider != null && this.activeSliderW > 0) {
            updateSliderFromMouse(this.draggingSlider, event.x(), this.activeSliderX, this.activeSliderW);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
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

        // Быстрые клавиши демонстрации состояний (F5/F6/F7/F9/F10):
        // F6: Раскрытие настроек внутри карточки (ПКМ) + компактная палитра цветов + ползунки
        if (key == GLFW.GLFW_KEY_F6) {
            this.accountsTabActive = false;
            this.selectedCategory = null;
            Module blocks = ModuleManager.get().module("blocks");
            expandOnly(blocks);
            if (blocks != null) {
                blocks.setEnabled(true);
                this.toggleAnims.put(blocks, 1.0F);
            }
            Module crosshair = ModuleManager.get().module("crosshair");
            if (crosshair != null) {
                crosshair.setEnabled(true);
                this.toggleAnims.put(crosshair, 1.0F);
                this.expandedModules.add(crosshair);
                this.expandAnims.put(crosshair, 1.0F);
                for (Setting<?> s : crosshair.settings()) {
                    if (s instanceof ColorSetting cs) {
                        this.expandedColors.add(cs);
                        break;
                    }
                }
            }
            this.scrollOffset = 0.0F;
            return true;
        }
        // F7: Демонстрация СКМ-окошка бинда с иконкой клавиатуры («⌨ ...») и назначенной буквой («R»)
        if (key == GLFW.GLFW_KEY_F7) {
            this.accountsTabActive = false;
            this.selectedCategory = null;
            this.expandedColors.clear();
            Module particles = ModuleManager.get().module("particles");
            if (particles != null) {
                particles.setKeybind(GLFW.GLFW_KEY_R);
            }
            Module sky = ModuleManager.get().module("sky");
            if (sky != null) {
                startBindForModule(sky);
            }
            this.scrollOffset = 0.0F;
            return true;
        }
        // F5: Раскрытие карточки ViewModel & Руки (ПКМ) в 3-колоночной сетке
        if (key == GLFW.GLFW_KEY_F5) {
            this.accountsTabActive = false;
            this.selectedCategory = null;
            Module hands = ModuleManager.get().module("hands");
            expandOnly(hands);
            this.scrollOffset = 0.0F;
            return true;
        }
        // F9: Вкладка «Визуал» в 3-колоночной сетке
        if (key == GLFW.GLFW_KEY_F9) {
            this.accountsTabActive = false;
            this.selectedCategory = ModuleCategory.VISUAL;
            this.expandedModules.clear();
            this.scrollOffset = 0.0F;
            return true;
        }
        // F10: Редактор HUD
        if (key == GLFW.GLFW_KEY_F10) {
            com.aetherglass.client.render.hud.HudLayout.get().resetAll();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudEditorScreen(this));
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
