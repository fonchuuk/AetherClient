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
import net.minecraft.network.chat.Style;
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
 * Современный компактный ClickGUI по вертикальным столбикам категорий (Matte Black + Neon, v1.0.8)
 * с гладким векторным шрифтом Montserrat TrueType.
 */
public final class AuroraInlineMenuScreen extends Screen {
    public static final int NUM_COLS = 3;
    public static final int COL_GAP = 9;
    public static final int CARD_GAP = 3;
    public static final int CARD_RADIUS = 5;
    public static final int CARD_HEADER_H = 21;
    public static final int COL_HEADER_H = 17;

    private static final int SETTING_ROW_H = 13;
    private static final int SETTING_SLIDER_H = 16;
    private static final int COLOR_EXPANDED_H = 22;
    private static final int PARTICLE_SHAPES_H = 13;

    private static final int[] QUICK_SWATCHES = {
            0xFFAA3DFF, 0xFFC084FC, 0xFF00F2FF, 0xFF38B6FF,
            0xFFFF5CE1, 0xFFFF9A3C, 0xFF00F5A0, 0xFFFFFFFF
    };

    private final Screen parent;
    private final BackdropGallery backdrop = new BackdropGallery();
    private final AnimationTimer openAnimation = new AnimationTimer(0.0F);
    private final Map<Module, Float> expandAnims = new IdentityHashMap<>();
    private final Map<Module, Float> toggleAnims = new IdentityHashMap<>();
    private final Set<Module> expandedModules = new HashSet<>();
    private final Set<ColorSetting> expandedColors = new HashSet<>();
    private final Map<Object, Float> bindPopupAnims = new HashMap<>();

    private final float[] colScroll = new float[NUM_COLS];
    private final float[] colMaxScroll = new float[NUM_COLS];
    private final int[] colLeftX = new int[NUM_COLS];
    private final int[] colRightX = new int[NUM_COLS];

    private ModuleCategory selectedCategory;
    private boolean accountsTabActive;
    private boolean closing;
    private String searchQuery = "";
    private boolean searchFocused;
    private String accountInput = "";
    private boolean accountInputFocused;

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
        super(Component.literal("ClickGUI AetherGlass"));
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
        this.selectedCategory = category;
    }

    public void setAccountsTab(boolean active) {
        this.accountsTabActive = active;
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
        if (this.minecraft != null && this.minecraft.level == null) {
            try {
                this.renderPanorama(graphics, delta);
            } catch (Throwable ignored) {
            }
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
     * Отрисовывает 4 компактных современных столбика по категориям (ClickGUI)
     * и аккуратную верхнюю полосу управления.
     */
    public void renderEmbedded(GuiGraphics graphics, int mouseX, int mouseY, float progress, float dt,
                               int screenW, int screenH, boolean titleScreenMode) {
        this.hitZones.clear();

        if (!titleScreenMode) {
            int dimColor = ColorUtil.fade(AuroraTheme.screenDim(), progress);
            graphics.fill(0, 0, screenW, screenH, dimColor);
        }

        int totalW = Math.min(414, screenW - 56);
        int startX = (screenW - totalW) / 2;
        int slideY = Math.round((1.0F - progress) * 8.0F);

        int topBarY = 10 + slideY;
        int topBarH = 18;
        renderTopBar(graphics, startX, topBarY, totalW, topBarH, mouseX, mouseY, progress, titleScreenMode);

        int colsTopY = topBarY + topBarH + 6;
        int maxColH = screenH - colsTopY - 12;
        int colW = (totalW - COL_GAP * (NUM_COLS - 1)) / NUM_COLS;

        String[] colTitles = {"ВИЗУАЛ", "ИНТЕРФЕЙС", "ЭКРАН / HUD"};
        ModuleCategory[] colCats = {
                ModuleCategory.VISUAL,
                ModuleCategory.INTERFACE,
                ModuleCategory.HUD
        };

        for (int col = 0; col < NUM_COLS; col++) {
            int cx = startX + col * (colW + COL_GAP);
            this.colLeftX[col] = cx;
            this.colRightX[col] = cx + colW;

            renderCategoryColumn(graphics, col, cx, colsTopY, colW, maxColH,
                    colTitles[col], colCats[col], mouseX, mouseY, progress, dt);
        }
    }

    // ─────────────────── Компактная современная верхняя панель ───────────────────

    private void renderTopBar(GuiGraphics graphics, int x, int y, int width, int height,
                              int mouseX, int mouseY, float alpha, boolean titleScreenMode) {
        DrawUtil.drawLiquidGlassPanel(graphics, x, y, width, height, 7, alpha);

        // Неоновая точка-индикатор бренда + современный логотип Montserrat Bold
        DrawUtil.drawSliderKnob(graphics, x + 6, y + 7, 4, ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawModernBold(graphics, this.font, "AETHERGLASS", x + 13, y + 5,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        int logoW = DrawUtil.modernBoldWidth(this.font, "AETHERGLASS");
        DrawUtil.drawModernSmall(graphics, this.font, "v1.0.8", x + 16 + logoW, y + 6,
                ColorUtil.fade(AuroraTheme.accent(), alpha));

        // Компактное поле поиска
        int searchX = x + 42 + logoW;
        int searchW = 94;
        int searchY = y + 3;
        int searchH = height - 6;
        boolean searchHov = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        DrawUtil.drawMatteNeonCard(graphics, searchX, searchY, searchW, searchH, 4, alpha,
                this.searchFocused ? 0.85F : (searchHov ? 0.35F : 0.0F), AuroraTheme.accent());
        String shown = this.searchQuery.isEmpty()
                ? (this.searchFocused ? "Поиск...|" : "Поиск модулей...")
                : (this.searchQuery + (this.searchFocused ? "|" : ""));
        int searchCol = (this.searchQuery.isEmpty() && !this.searchFocused) ? 0xFF727C90 : 0xFFFFFFFF;
        DrawUtil.drawModernSmall(graphics, this.font, trimStyle(shown, searchW - 8, DrawUtil.STYLE_MODERN_SMALL),
                searchX + 5, searchY + 2, ColorUtil.fade(searchCol, alpha));
        this.hitZones.add(new HitZone(searchX, searchY, searchW, searchH,
                () -> this.searchFocused = true,
                () -> {
                    this.searchQuery = "";
                    this.searchFocused = false;
                },
                null));

        if (titleScreenMode && this.minecraft != null && this.minecraft.level == null) {
            Screen host = this.parent != null ? this.parent : this;
            int btnH = height - 6;
            int btnY = y + 3;
            int rightX = x + width - 4;

            rightX = renderTopBarButton(graphics, rightX, btnY, 34, btnH, "Выход", false, true, mouseX, mouseY, alpha,
                    () -> this.minecraft.stop()) - 3;
            rightX = renderTopBarButton(graphics, rightX, btnY, 38, btnH, "Опции", false, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new OptionsScreen(host, this.minecraft.options))) - 3;
            rightX = renderTopBarButton(graphics, rightX, btnY, 42, btnH, "Сетевая", false, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new JoinMultiplayerScreen(host))) - 3;
            renderTopBarButton(graphics, rightX, btnY, 48, btnH, "Одиночная", true, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new SelectWorldScreen(host)));
        } else {
            String hint = "ЛКМ: Вкл · ПКМ: Настройки · СКМ: Бинд";
            int hintW = DrawUtil.modernSmallWidth(this.font, hint);
            int closeX = x + width - 15;
            int closeY = y + 3;
            if (closeX - hintW - 6 > searchX + searchW + 5) {
                DrawUtil.drawModernSmall(graphics, this.font, hint, closeX - hintW - 6, y + 6,
                        ColorUtil.fade(0xFF7A8396, alpha));
            }
            boolean closeHov = inside(mouseX, mouseY, closeX, closeY, 12, height - 6);
            DrawUtil.drawMatteNeonCard(graphics, closeX, closeY, 12, height - 6, 4, alpha,
                    closeHov ? 0.90F : 0.0F, AuroraTheme.danger());
            DrawUtil.drawCenteredTextScaled(graphics, this.font, "×", closeX + 6, closeY + 2,
                    ColorUtil.fade(closeHov ? AuroraTheme.danger() : 0xFF9AA4B8, alpha), 0.72F, false);
            this.hitZones.add(new HitZone(closeX, closeY, 12, height - 6, this::onClose, null, null));
        }
    }

    private int renderTopBarButton(GuiGraphics graphics, int rightX, int y, int w, int h, String label,
                                   boolean primary, boolean danger, int mouseX, int mouseY, float alpha, Runnable action) {
        int bx = rightX - w;
        boolean hov = inside(mouseX, mouseY, bx, y, w, h);
        int neonCol = danger ? AuroraTheme.danger() : AuroraTheme.accent();
        float glow = primary ? (hov ? 1.0F : 0.75F) : (hov ? 0.85F : 0.0F);
        DrawUtil.drawMatteNeonCard(graphics, bx, y, w, h, 4, alpha, glow, neonCol);
        int tw = DrawUtil.modernSmallWidth(this.font, label);
        DrawUtil.drawModernSmall(graphics, this.font, label, bx + (w - tw) / 2, y + 2,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        this.hitZones.add(new HitZone(bx, y, w, h, action, null, null));
        return bx;
    }

    // ─────────────────── Вертикальный столбик категории (ClickGUI Column) ───────────────────

    private void renderCategoryColumn(GuiGraphics graphics, int colIdx, int colX, int colY, int colW, int maxColH,
                                      String title, ModuleCategory category,
                                      int mouseX, int mouseY, float alpha, float dt) {
        List<Module> modules = filteredByCategory(category);

        int innerContentH = 4;
        for (Module m : modules) {
            boolean expanded = this.expandedModules.contains(m);
            float expandCurr = this.expandAnims.getOrDefault(m, 0.0F);
            float expandTarget = expanded ? 1.0F : 0.0F;
            expandCurr += (expandTarget - expandCurr) * Math.min(1.0F, dt * 14.0F * AuroraTheme.motion());
            if (Math.abs(expandTarget - expandCurr) < 0.005F) {
                expandCurr = expandTarget;
            }
            this.expandAnims.put(m, expandCurr);
            int visSettingsH = Math.round(computeExpandedContentHeight(m) * Easing.easeOutCubic(expandCurr));
            innerContentH += CARD_HEADER_H + visSettingsH + CARD_GAP;
        }
        if (category == ModuleCategory.HUD) {
            innerContentH += (CARD_HEADER_H + CARD_GAP) * 2;
        }

        int targetColH = Math.min(maxColH, Math.max(COL_HEADER_H + 40, COL_HEADER_H + innerContentH + 4));

        // Матовый чёрный корпус вертикального столбика
        DrawUtil.drawLiquidGlassPanel(graphics, colX, colY, colW, targetColH, 8, alpha);

        // Шапка столбика сверху + тонкая неоновая линия-акцент
        renderColumnHeader(graphics, colX, colY, colW, title, modules.size(), alpha);

        int bodyTop = colY + COL_HEADER_H + 3;
        int bodyH = targetColH - COL_HEADER_H - 6;
        int cardX = colX + 4;
        int cardW = colW - 8;

        this.colMaxScroll[colIdx] = Math.max(0.0F, innerContentH - bodyH);
        if (this.colScroll[colIdx] > this.colMaxScroll[colIdx]) {
            this.colScroll[colIdx] = this.colMaxScroll[colIdx];
        }

        int curY = bodyTop - Math.round(this.colScroll[colIdx]);

        graphics.enableScissor(colX - 2, bodyTop - 2, colX + colW + 2, bodyTop + bodyH + 2);

        for (Module module : modules) {
            int cardH = renderModuleCard(graphics, module, cardX, curY, cardW,
                    bodyTop - 2, bodyTop + bodyH + 2, mouseX, mouseY, alpha, dt);
            curY += cardH + CARD_GAP;
        }

        if (category == ModuleCategory.HUD) {
            curY += renderHudExtraCards(graphics, cardX, curY, cardW,
                    bodyTop - 2, bodyTop + bodyH + 2, mouseX, mouseY, alpha);
        }

        graphics.disableScissor();
    }

    private void renderColumnHeader(GuiGraphics graphics, int colX, int colY, int colW,
                                    String title, int count, float alpha) {
        // Маленький неоновый индикатор категории слева от заголовка
        DrawUtil.drawSliderKnob(graphics, colX + 5, colY + 6, 4, ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawModernBold(graphics, this.font, title, colX + 11, colY + 5,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        String cntStr = Integer.toString(count);
        int cntW = Math.max(11, DrawUtil.modernSmallWidth(this.font, cntStr) + 5);
        int cntX = colX + colW - cntW - 4;
        int cntY = colY + 4;
        DrawUtil.drawMatteNeonCard(graphics, cntX, cntY, cntW, 9, 4, alpha, 0.45F, AuroraTheme.accent());
        int tw = DrawUtil.modernSmallWidth(this.font, cntStr);
        DrawUtil.drawModernSmall(graphics, this.font, cntStr, cntX + (cntW - tw) / 2, cntY + 1,
                ColorUtil.fade(AuroraTheme.accent(), alpha));

        graphics.fill(colX + 4, colY + COL_HEADER_H - 1, colX + colW - 4, colY + COL_HEADER_H,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(125 * alpha)));
    }

    private int renderHudExtraCards(GuiGraphics graphics, int x, int startY, int width,
                                    int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        int curY = startY;

        // 1. Компактная кнопка-карточка «Редактор HUD»
        if (curY + CARD_HEADER_H >= clipTop && curY <= clipBottom) {
            boolean hov = inside(mouseX, mouseY, x, curY, width, CARD_HEADER_H)
                    && mouseY >= clipTop && mouseY <= clipBottom;
            DrawUtil.drawMatteNeonCard(graphics, x, curY, width, CARD_HEADER_H, CARD_RADIUS, alpha,
                    hov ? 0.85F : 0.0F, AuroraTheme.accent());
            DrawUtil.drawSliderKnob(graphics, x + 4, curY + 5, 3,
                    ColorUtil.fade(hov ? AuroraTheme.accent() : 0xFF424A5C, alpha));
            DrawUtil.drawModernBold(graphics, this.font, "Редактор HUD", x + 9, curY + 3,
                    ColorUtil.fade(0xFFFFFFFF, alpha));
            DrawUtil.drawModernSmall(graphics, this.font, "Перетаскивание панелей", x + 9, curY + 12,
                    ColorUtil.fade(0xFF7D8699, alpha));
            if (mouseY >= clipTop && mouseY <= clipBottom) {
                Screen host = this.parent != null ? this.parent : this;
                this.hitZones.add(new HitZone(x, curY, width, CARD_HEADER_H, () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new HudEditorScreen(host));
                    }
                }, null, null));
            }
        }
        curY += CARD_HEADER_H + CARD_GAP;

        // 2. Компактная кнопка-карточка «Чистый экран» (F8)
        if (curY + CARD_HEADER_H >= clipTop && curY <= clipBottom) {
            KeybindManager km = KeybindManager.get();
            boolean cleanOn = km.isCleanScreenActive();
            boolean cleanListening = "CLEAN_SCREEN".equals(this.listeningBindTarget);
            DrawUtil.drawMatteNeonCard(graphics, x, curY, width, CARD_HEADER_H, CARD_RADIUS, alpha,
                    cleanOn ? 1.0F : 0.0F, AuroraTheme.accent());

            String keyStr = KeybindManager.formatKey(km.cleanScreenKeybind());
            int badgeW = cleanListening ? 22 : Math.max(12, DrawUtil.modernSmallWidth(this.font, keyStr) + 5);
            int bx = x + width - 3 - badgeW;
            int by = curY + 3;
            DrawUtil.drawMatteNeonCard(graphics, bx, by, badgeW, 9, 3, alpha,
                    cleanListening ? 1.0F : 0.35F, AuroraTheme.accent());
            if (cleanListening) {
                drawKeyboardIcon(graphics, bx + 2, by + 2, ColorUtil.fade(AuroraTheme.accent(), alpha));
                DrawUtil.drawModernSmall(graphics, this.font, "..", bx + 12, by + 1,
                        ColorUtil.fade(AuroraTheme.accent(), alpha));
            } else {
                int kw = DrawUtil.modernSmallWidth(this.font, keyStr);
                DrawUtil.drawModernSmall(graphics, this.font, keyStr, bx + (badgeW - kw) / 2, by + 1,
                        ColorUtil.fade(0xFFE8F0FF, alpha));
            }

            DrawUtil.drawSliderKnob(graphics, x + 4, curY + 5, 3,
                    ColorUtil.fade(cleanOn ? AuroraTheme.accent() : 0xFF424A5C, alpha));
            DrawUtil.drawModernBold(graphics, this.font, "Чистый экран", x + 9, curY + 3,
                    ColorUtil.fade(0xFFFFFFFF, alpha));
            DrawUtil.drawModernSmall(graphics, this.font, "Скрытие HUD по клавише", x + 9, curY + 12,
                    ColorUtil.fade(0xFF7D8699, alpha));

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(x, curY, width, CARD_HEADER_H,
                        km::toggleCleanScreen,
                        km::toggleCleanScreen,
                        () -> this.listeningBindTarget = cleanListening ? null : "CLEAN_SCREEN"));
            }
        }
        curY += CARD_HEADER_H + CARD_GAP;
        return curY - startY;
    }

    // ─────────────────── Компактная кнопка-карточка модуля в столбике ───────────────────

    private int renderModuleCard(GuiGraphics graphics, Module module, int x, int y, int width,
                                 int clipTop, int clipBottom, int mouseX, int mouseY, float alpha, float dt) {
        float expandCurr = this.expandAnims.getOrDefault(module, 0.0F);

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

        // 1. Матовый чёрный прямоугольник (#0B0C10) + тонкая неоновая обводка с glow при включении
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, totalH, CARD_RADIUS, alpha, toggleCurr, AuroraTheme.accent());

        // 2. Компактное окошко бинда (СКМ) в правом верхнем углу карточки
        int bindBadgeW = renderCornerKeybindPopup(graphics, module, module.keybind(),
                key -> {
                    module.setKeybind(key);
                    NotificationManager.get().pushKeybindChanged(module.name(), KeybindManager.formatKey(key));
                },
                x + width - 3, y + 3, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

        // 3. Точка-индикатор статуса + гладкий шрифт Montserrat (название + микро-описание)
        int dotCol = ColorUtil.lerp(0xFF3E4658, AuroraTheme.accent(), toggleCurr);
        DrawUtil.drawSliderKnob(graphics, x + 4, y + 5, 3, ColorUtil.fade(dotCol, alpha));

        int availTitlePx = Math.max(24, width - 13 - (bindBadgeW > 0 ? bindBadgeW + 3 : 0));
        String titleStr = trimStyle(module.name(), availTitlePx, DrawUtil.STYLE_MODERN_BOLD);
        DrawUtil.drawModernBold(graphics, this.font, titleStr, x + 9, y + 3,
                ColorUtil.fade(0xFFFFFFFF, alpha));

        int availDescPx = Math.max(28, width - 12);
        String descStr = trimStyle(module.description(), availDescPx, DrawUtil.STYLE_MODERN_SMALL);
        DrawUtil.drawModernSmall(graphics, this.font, descStr, x + 9, y + 12,
                ColorUtil.fade(0xFF7D8699, alpha));

        // 4. Управление (ЛКМ / ПКМ / СКМ)
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

        // 5. Настройки внутри карточки (при раскрытии по ПКМ)
        if (visibleContentH > 3) {
            int innerTop = Math.max(clipTop, y + CARD_HEADER_H);
            int innerBottom = Math.min(clipBottom, y + CARD_HEADER_H + visibleContentH);
            if (innerBottom > innerTop) {
                graphics.fill(x + 5, y + CARD_HEADER_H - 1, x + width - 5, y + CARD_HEADER_H,
                        ColorUtil.withAlpha(0xFFFFFFFF, Math.round(16 * alpha * expandCurr)));

                graphics.enableScissor(x + 2, innerTop, x + width - 2, innerBottom);
                renderInlineSettings(graphics, module, x + 5, y + CARD_HEADER_H + 3, width - 10,
                        innerTop, innerBottom, mouseX, mouseY, alpha * expandCurr, dt);
                graphics.disableScissor();
            }
        }

        return totalH;
    }

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
        int baseW = Math.max(11, DrawUtil.modernSmallWidth(this.font, keyLetter.isEmpty() ? "K" : keyLetter) + 5);
        int listenW = 22;
        int badgeW = Math.round(baseW + (listenW - baseW) * Easing.easeOutCubic(anim));
        int badgeH = 9;
        int bx = rightX - badgeW;

        boolean hov = inside(mouseX, mouseY, bx, topY, badgeW, badgeH) && mouseY >= clipTop && mouseY <= clipBottom;
        DrawUtil.drawMatteNeonCard(graphics, bx, topY, badgeW, badgeH, 3, alpha,
                listening ? 1.0F : (hov ? 0.65F : 0.35F), AuroraTheme.accent());

        int textCol = listening ? AuroraTheme.accent() : 0xFFE8F0FF;
        if (listening || anim > 0.35F) {
            drawKeyboardIcon(graphics, bx + 2, topY + 2, ColorUtil.fade(AuroraTheme.accent(), alpha));
            DrawUtil.drawModernSmall(graphics, this.font, "..", bx + 12, topY + 1,
                    ColorUtil.fade(textCol, alpha));
        } else {
            int kw = DrawUtil.modernSmallWidth(this.font, keyLetter);
            DrawUtil.drawModernSmall(graphics, this.font, keyLetter, bx + (badgeW - kw) / 2, topY + 1,
                    ColorUtil.fade(textCol, alpha));
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
        graphics.fill(x, y, x + 9, y + 1, color);
        graphics.fill(x, y + 5, x + 9, y + 6, color);
        graphics.fill(x, y + 1, x + 1, y + 5, color);
        graphics.fill(x + 8, y + 1, x + 9, y + 5, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
        graphics.fill(x + 4, y + 2, x + 5, y + 3, color);
        graphics.fill(x + 6, y + 2, x + 7, y + 3, color);
        graphics.fill(x + 2, y + 4, x + 7, y + 5, color);
    }

    private int computeExpandedContentHeight(Module module) {
        int h = 6;
        if (module instanceof ParticleModule) {
            h += PARTICLE_SHAPES_H + 2;
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
            curY += PARTICLE_SHAPES_H + 2;
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
        int cellH = 14;
        String[] icons = {"✦", "⚡", "◎", "❄", "●", "❖"};

        for (int i = 0; i < shapes.length; i++) {
            ParticleShape shape = shapes[i];
            int cx = x + i * (cellW + gap);
            boolean active = module.shapeSetting().get() == shape;
            boolean hovered = inside(mouseX, mouseY, cx, y, cellW, cellH)
                    && mouseY >= clipTop && mouseY <= clipBottom;

            DrawUtil.drawMatteNeonCard(graphics, cx, y, cellW, cellH, 4, alpha,
                    active ? 0.90F : (hovered ? 0.35F : 0.0F), AuroraTheme.accent());

            String icon = i < icons.length ? icons[i] : "✦";
            int iw = DrawUtil.modernSmallWidth(this.font, icon);
            DrawUtil.drawModernSmall(graphics, this.font, icon, cx + (cellW - iw) / 2, y + 3,
                    ColorUtil.fade(active ? AuroraTheme.accent() : 0xFF9AA4B8, alpha));

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(cx, y, cellW, cellH, () -> module.shapeSetting().set(shape), null, null));
            }
        }
    }

    // ─────────────────── Компактные настройки внутри карточки ───────────────────

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
                int swW = 14;
                int swH = 7;
                int swX = x + width - swW;
                int swY = y + (SETTING_ROW_H - swH) / 2;

                int maxLabelW = swX - x - 4;
                DrawUtil.drawModernSmall(graphics, this.font,
                        trimStyle(boolSetting.name(), maxLabelW, DrawUtil.STYLE_MODERN_SMALL),
                        x, y + 3, ColorUtil.fade(0xFFB4BECE, alpha));

                boolean on = boolSetting.isEnabled();
                DrawUtil.drawMatteNeonCard(graphics, swX, swY, swW, swH, 3, alpha,
                        on ? 0.90F : 0.0F, AuroraTheme.accent());
                int knobX = swX + (on ? swW - 5 : 2);
                int knobCol = on ? AuroraTheme.accent() : 0xFF727C90;
                DrawUtil.drawSliderKnob(graphics, knobX, swY + 2, 3, ColorUtil.fade(knobCol, alpha));

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
                String valText = "‹ " + enumSetting.get().toString() + " ›";
                int pillW = Math.min(width * 54 / 100, DrawUtil.modernSmallWidth(this.font, valText) + 5);
                int pillX = x + width - pillW;
                int pillY = y + 1;

                int maxLabelW = pillX - x - 4;
                DrawUtil.drawModernSmall(graphics, this.font,
                        trimStyle(enumSetting.name(), maxLabelW, DrawUtil.STYLE_MODERN_SMALL),
                        x, y + 3, ColorUtil.fade(0xFFB4BECE, alpha));

                boolean pillHov = inside(mouseX, mouseY, pillX, pillY, pillW, 10);
                DrawUtil.drawMatteNeonCard(graphics, pillX, pillY, pillW, 10, 4, alpha,
                        pillHov ? 0.80F : 0.30F, AuroraTheme.accent());
                String trimmedVal = trimStyle(valText, pillW - 4, DrawUtil.STYLE_MODERN_SMALL);
                int vw = DrawUtil.modernSmallWidth(this.font, trimmedVal);
                DrawUtil.drawModernSmall(graphics, this.font, trimmedVal,
                        pillX + (pillW - vw) / 2, pillY + 1, ColorUtil.fade(0xFFFFFFFF, alpha));

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
                int circleSize = 7;
                int circleX = x + width - circleSize - 1;
                int circleY = y + (SETTING_ROW_H - circleSize) / 2;

                int maxLabelW = circleX - x - 4;
                DrawUtil.drawModernSmall(graphics, this.font,
                        trimStyle(colorSetting.name(), maxLabelW, DrawUtil.STYLE_MODERN_SMALL),
                        x, y + 3, ColorUtil.fade(0xFFB4BECE, alpha));

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

    private void renderNeonSliderRow(GuiGraphics graphics, Setting<?> setting, float normalized,
                                     String valueText, int x, int y, int width,
                                     int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        float clamped = clamp01(normalized);

        int valW = DrawUtil.modernSmallWidth(this.font, valueText);
        int maxLabelW = width - valW - 4;
        DrawUtil.drawModernSmall(graphics, this.font,
                trimStyle(setting.name(), maxLabelW, DrawUtil.STYLE_MODERN_SMALL),
                x, y + 1, ColorUtil.fade(0xFFB4BECE, alpha));
        DrawUtil.drawModernSmall(graphics, this.font, valueText,
                x + width - valW, y + 1, ColorUtil.fade(AuroraTheme.accent(), alpha));

        int trackX = x + 1;
        int trackW = width - 2;
        int trackY = y + 10;
        int trackH = 2;

        graphics.fill(trackX, trackY, trackX + trackW, trackY + trackH,
                ColorUtil.withAlpha(0xFF1F2430, Math.round(230 * alpha)));
        int fillW = Math.max(2, Math.round(trackW * clamped));
        graphics.fill(trackX, trackY, trackX + fillW, trackY + trackH,
                ColorUtil.fade(AuroraTheme.accent(), alpha));

        int knobSize = 4;
        int knobX = trackX + Math.round((trackW - knobSize) * clamped);
        int knobY = trackY - 1;
        DrawUtil.drawSliderKnob(graphics, knobX - 1, knobY - 1, knobSize + 2,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(85 * alpha)));
        DrawUtil.drawSliderKnob(graphics, knobX, knobY, knobSize,
                ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawSliderKnob(graphics, knobX + 1, knobY + 1, knobSize - 2,
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

    private void renderCompactColorPalettePopover(GuiGraphics graphics, ColorSetting setting,
                                                  int x, int y, int width, int clipTop, int clipBottom,
                                                  int mouseX, int mouseY, float alpha) {
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, COLOR_EXPANDED_H - 2, 4, alpha, 0.35F, AuroraTheme.accent());

        int dotSize = 6;
        int totalDotsW = QUICK_SWATCHES.length * dotSize;
        int gap = Math.max(2, (width - 8 - totalDotsW) / (QUICK_SWATCHES.length - 1));
        int dotY = y + 3;
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            int presetCol = QUICK_SWATCHES[i];
            int dx = x + 4 + i * (dotSize + gap);
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

        int color = setting.rgb();
        float hue = ColorUtil.hueOf(color);
        float sat = ColorUtil.saturationOf(color);
        float bri = ColorUtil.brightnessOf(color);

        int barY = y + 13;
        int halfW = (width - 14) / 2;
        int hueX = x + 4;
        int briX = hueX + halfW + 6;

        graphics.fill(hueX, barY, hueX + halfW, barY + 2, ColorUtil.withAlpha(0xFF222836, Math.round(230 * alpha)));
        graphics.fill(hueX, barY, hueX + Math.max(2, Math.round(hue * halfW)), barY + 2,
                ColorUtil.fade(ColorUtil.hsb(hue, 0.95F, 1.0F), alpha));
        DrawUtil.drawSliderKnob(graphics, hueX + Math.round(hue * (halfW - 4)), barY - 1, 4,
                ColorUtil.fade(ColorUtil.hsb(hue, 0.95F, 1.0F), alpha));

        graphics.fill(briX, barY, briX + halfW, barY + 2, ColorUtil.withAlpha(0xFF222836, Math.round(230 * alpha)));
        graphics.fill(briX, barY, briX + Math.max(2, Math.round(bri * halfW)), barY + 2,
                ColorUtil.fade(AuroraTheme.accent(), alpha));
        DrawUtil.drawSliderKnob(graphics, briX + Math.round(bri * (halfW - 4)), barY - 1, 4,
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
            this.hitZones.add(new HitZone(hueX - 2, barY - 4, halfW + 4, 9, () -> {
                this.draggingColor = setting;
                this.draggingColorChannel = 0;
                float newH = clamp01((float) (mouseX - hueX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(newH, Math.max(0.75F, sat), Math.max(0.5F, bri)));
            }, null, null));
            this.hitZones.add(new HitZone(briX - 2, barY - 4, halfW + 4, 9, () -> {
                this.draggingColor = setting;
                this.draggingColorChannel = 1;
                float newB = clamp01((float) (mouseX - briX) / Math.max(1, halfW));
                setting.setRgb(ColorUtil.hsb(hue, sat, Math.max(0.15F, newB)));
            }, null, null));
        }
    }

    // ─────────────────── 4-й вертикальный столбик: «Аккаунты» (ClickGUI Column) ───────────────────

    private void renderAccountsColumn(GuiGraphics graphics, int colIdx, int colX, int colY, int colW, int maxColH,
                                      String title, int mouseX, int mouseY, float alpha) {
        AccountManager am = AccountManager.get();
        List<String> saved = am.accounts();
        String currentName = am.currentUsername();

        int innerContentH = 4 + (CARD_HEADER_H + CARD_GAP) * (2 + saved.size());
        int targetColH = Math.min(maxColH, Math.max(COL_HEADER_H + 40, COL_HEADER_H + innerContentH + 4));

        DrawUtil.drawLiquidGlassPanel(graphics, colX, colY, colW, targetColH, 8, alpha);
        renderColumnHeader(graphics, colX, colY, colW, title, saved.size(), alpha);

        int bodyTop = colY + COL_HEADER_H + 3;
        int bodyH = targetColH - COL_HEADER_H - 6;
        int cardX = colX + 4;
        int cardW = colW - 8;

        this.colMaxScroll[colIdx] = Math.max(0.0F, innerContentH - bodyH);
        if (this.colScroll[colIdx] > this.colMaxScroll[colIdx]) {
            this.colScroll[colIdx] = this.colMaxScroll[colIdx];
        }

        int curY = bodyTop - Math.round(this.colScroll[colIdx]);

        graphics.enableScissor(colX - 2, bodyTop - 2, colX + colW + 2, bodyTop + bodyH + 2);

        // 1. Компактная кнопка «Случайный ник»
        boolean rndHov = inside(mouseX, mouseY, cardX, curY, cardW, CARD_HEADER_H);
        DrawUtil.drawMatteNeonCard(graphics, cardX, curY, cardW, CARD_HEADER_H, CARD_RADIUS, alpha,
                rndHov ? 0.85F : 0.0F, AuroraTheme.accent());
        DrawUtil.drawSliderKnob(graphics, cardX + 4, curY + 5, 3,
                ColorUtil.fade(rndHov ? AuroraTheme.accent() : 0xFF424A5C, alpha));
        DrawUtil.drawModernBold(graphics, this.font, "Случайный ник", cardX + 9, curY + 3,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        DrawUtil.drawModernSmall(graphics, this.font, "Сгенерировать в 1 клик", cardX + 9, curY + 12,
                ColorUtil.fade(0xFF7D8699, alpha));
        this.hitZones.add(new HitZone(cardX, curY, cardW, CARD_HEADER_H, () -> {
            this.accountInput = am.switchRandomAccount();
        }, null, null));
        curY += CARD_HEADER_H + CARD_GAP;

        // 2. Компактная кнопка ввода нового ника
        boolean boxHov = inside(mouseX, mouseY, cardX, curY, cardW, CARD_HEADER_H);
        DrawUtil.drawMatteNeonCard(graphics, cardX, curY, cardW, CARD_HEADER_H, CARD_RADIUS, alpha,
                this.accountInputFocused ? 0.95F : (boxHov ? 0.45F : 0.0F), AuroraTheme.accent());
        DrawUtil.drawSliderKnob(graphics, cardX + 4, curY + 5, 3,
                ColorUtil.fade(this.accountInputFocused ? AuroraTheme.accent() : 0xFF424A5C, alpha));
        DrawUtil.drawModernBold(graphics, this.font, "Новый аккаунт", cardX + 9, curY + 3,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        String shownInput = this.accountInput.isEmpty()
                ? (this.accountInputFocused ? "Введите ник...|" : "Нажмите для ввода ника")
                : (this.accountInput + (this.accountInputFocused ? "|" : ""));
        DrawUtil.drawModernSmall(graphics, this.font,
                trimStyle(shownInput, cardW - 12, DrawUtil.STYLE_MODERN_SMALL),
                cardX + 9, curY + 12,
                ColorUtil.fade(this.accountInput.isEmpty() ? 0xFF7D8699 : AuroraTheme.accent(), alpha));
        this.hitZones.add(new HitZone(cardX, curY, cardW, CARD_HEADER_H, () -> {
            this.accountInputFocused = true;
            this.searchFocused = false;
        }, () -> this.accountInput = "", null));
        curY += CARD_HEADER_H + CARD_GAP;

        // 3. Вертикальный список карточек сохранённых аккаунтов
        for (String acc : saved) {
            boolean isCurrent = acc.equalsIgnoreCase(currentName);
            DrawUtil.drawMatteNeonCard(graphics, cardX, curY, cardW, CARD_HEADER_H, CARD_RADIUS, alpha,
                    isCurrent ? 1.0F : 0.0F, AuroraTheme.accent());

            DrawUtil.drawSliderKnob(graphics, cardX + 4, curY + 5, 3,
                    ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF424A5C, alpha));
            DrawUtil.drawModernBold(graphics, this.font,
                    trimStyle(acc, cardW - 22, DrawUtil.STYLE_MODERN_BOLD),
                    cardX + 9, curY + 3, ColorUtil.fade(0xFFFFFFFF, alpha));
            DrawUtil.drawModernSmall(graphics, this.font, isCurrent ? "Активен в сессии" : "ЛКМ — выбрать ник",
                    cardX + 9, curY + 12, ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF7D8699, alpha));

            int delX = cardX + cardW - 12;
            int delY = curY + 3;
            DrawUtil.drawModernSmall(graphics, this.font, "×", delX + 3, delY + 1,
                    ColorUtil.fade(0xFF727B8E, alpha));

            this.hitZones.add(new HitZone(cardX, curY, cardW - 16, CARD_HEADER_H, () -> am.switchAccount(acc), null, null));
            this.hitZones.add(new HitZone(delX, delY, 12, 12, () -> am.removeAccount(acc), null, null));
            curY += CARD_HEADER_H + CARD_GAP;
        }

        graphics.disableScissor();
    }

    private List<Module> filteredByCategory(ModuleCategory category) {
        List<Module> all = ModuleManager.get().byCategory(category);
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
        for (int c = 0; c < NUM_COLS; c++) {
            if (mouseX >= this.colLeftX[c] && mouseX <= this.colRightX[c]) {
                this.colScroll[c] = Math.max(0.0F, Math.min(this.colMaxScroll[c],
                        this.colScroll[c] - (float) verticalAmount * 18.0F));
                return true;
            }
        }
        for (int c = 0; c < NUM_COLS; c++) {
            this.colScroll[c] = Math.max(0.0F, Math.min(this.colMaxScroll[c],
                    this.colScroll[c] - (float) verticalAmount * 18.0F));
        }
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

        // F6: Раскрытие настроек внутри карточек в столбиках Визуал и Интерфейс («Оформление»: Цвет и Жирность рамки)
        if (key == GLFW.GLFW_KEY_F6) {
            Module blocks = ModuleManager.get().module("blocks");
            expandOnly(blocks);
            if (blocks != null) {
                blocks.setEnabled(true);
                this.toggleAnims.put(blocks, 1.0F);
            }
            Module iface = ModuleManager.get().module("interface");
            if (iface != null) {
                iface.setEnabled(true);
                this.toggleAnims.put(iface, 1.0F);
                this.expandedModules.add(iface);
                this.expandAnims.put(iface, 1.0F);
                for (Setting<?> s : iface.settings()) {
                    if (s instanceof ColorSetting cs) {
                        this.expandedColors.add(cs);
                        break;
                    }
                }
            }
            return true;
        }
        // F7: СКМ-окошко бинда с иконкой клавиатуры («[⌨ ...]») и назначенная буква («[R]»)
        if (key == GLFW.GLFW_KEY_F7) {
            Module particles = ModuleManager.get().module("particles");
            if (particles != null) {
                particles.setKeybind(GLFW.GLFW_KEY_R);
            }
            Module sky = ModuleManager.get().module("sky");
            if (sky != null) {
                startBindForModule(sky);
            }
            return true;
        }
        // F5: Раскрытие карточки «Эффекты руки» в столбике «Визуал»
        if (key == GLFW.GLFW_KEY_F5) {
            Module hands = ModuleManager.get().module("hands");
            expandOnly(hands);
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

    private String trimStyle(String text, int maxW, Style style) {
        if (text == null || maxW <= 4) {
            return "";
        }
        if (this.font.width(DrawUtil.modernComponent(text, style)) <= maxW) {
            return text;
        }
        int len = text.length();
        while (len > 1 && this.font.width(DrawUtil.modernComponent(text.substring(0, len) + "..", style)) > maxW) {
            len--;
        }
        return text.substring(0, Math.max(1, len)) + "..";
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
