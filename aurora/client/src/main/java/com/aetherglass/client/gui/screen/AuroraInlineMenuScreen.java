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
 * Классический ClickGUI по вертикальным столбикам категорий (Matte Black + Neon):
 * <ul>
 *   <li>На экране стоят 4 отдельных вертикальных столбика по категориям:
 *       <b>1. Визуал</b>, <b>2. Интерфейс</b>, <b>3. Экран / HUD</b>, <b>4. Аккаунты</b>;</li>
 *   <li>У каждого столбика сверху своя шапка с неоновым акцентом, а внутри карточки модулей
 *       идут строго сверху вниз одна под другой;</li>
 *   <li>Карточки — матовый чёрный прямоугольник ({@code #0B0C10}, скругление 10–12px):
 *       когда модуль выключен — обводки нет; когда включён — вокруг карточки загорается
 *       неоновая бирюзовая обводка ({@code #00F2FF}) с мягким внешним свечением (glow);</li>
 *   <li>ЛКМ включает/выключает модуль, ПКМ плавно раскрывает настройки вниз внутри карточки,
 *       СКМ открывает компактное окошко бинда с иконкой клавиатуры.</li>
 * </ul>
 */
public final class AuroraInlineMenuScreen extends Screen {
    public static final int NUM_COLS = 4;
    public static final int COL_GAP = 9;
    public static final int CARD_GAP = 6;
    public static final int CARD_RADIUS = 10;
    public static final int CARD_HEADER_H = 38;
    public static final int COL_HEADER_H = 24;

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
     * Отрисовывает 4 отдельных вертикальных столбика по категориям (ClickGUI)
     * и компактную верхнюю полосу управления.
     */
    public void renderEmbedded(GuiGraphics graphics, int mouseX, int mouseY, float progress, float dt,
                               int screenW, int screenH, boolean titleScreenMode) {
        this.hitZones.clear();

        if (!titleScreenMode) {
            int dimColor = ColorUtil.fade(AuroraTheme.screenDim(), progress);
            graphics.fill(0, 0, screenW, screenH, dimColor);
        }

        int totalW = Math.min(616, screenW - 16);
        int startX = (screenW - totalW) / 2;
        int slideY = Math.round((1.0F - progress) * 10.0F);

        // Компактная верхняя плашка (бренд, поиск, а в главном меню — быстрые кнопки запуска мира)
        int topBarY = 7 + slideY;
        int topBarH = 22;
        renderTopBar(graphics, startX, topBarY, totalW, topBarH, mouseX, mouseY, progress, titleScreenMode);

        // 4 отдельных вертикальных столбика по категориям (Визуал, Интерфейс, Экран/HUD, Аккаунты)
        int colsTopY = topBarY + topBarH + 7;
        int maxColH = screenH - colsTopY - 8;
        int colW = (totalW - COL_GAP * (NUM_COLS - 1)) / NUM_COLS;

        String[] colTitles = {"✦ ВИЗУАЛ", "◎ ИНТЕРФЕЙС", "◈ ЭКРАН / HUD", "👤 АККАУНТЫ"};
        ModuleCategory[] colCats = {
                ModuleCategory.VISUAL,
                ModuleCategory.INTERFACE,
                ModuleCategory.HUD,
                null
        };

        for (int col = 0; col < NUM_COLS; col++) {
            int cx = startX + col * (colW + COL_GAP);
            this.colLeftX[col] = cx;
            this.colRightX[col] = cx + colW;

            if (col < 3) {
                renderCategoryColumn(graphics, col, cx, colsTopY, colW, maxColH,
                        colTitles[col], colCats[col], mouseX, mouseY, progress, dt);
            } else {
                renderAccountsColumn(graphics, col, cx, colsTopY, colW, maxColH,
                        colTitles[col], mouseX, mouseY, progress);
            }
        }
    }

    // ─────────────────── Верхняя компактная панель ClickGUI ───────────────────

    private void renderTopBar(GuiGraphics graphics, int x, int y, int width, int height,
                              int mouseX, int mouseY, float alpha, boolean titleScreenMode) {
        DrawUtil.drawLiquidGlassPanel(graphics, x, y, width, height, 10, alpha);

        DrawUtil.drawTextScaled(graphics, this.font, "§lAETHERGLASS", x + 9, y + 7,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.86F, false);
        DrawUtil.drawTextScaled(graphics, this.font, "1.0.8", x + 78, y + 8,
                ColorUtil.fade(AuroraTheme.accent(), alpha), 0.72F, false);

        // Поле быстрого поиска модулей
        int searchX = x + 106;
        int searchW = 128;
        int searchY = y + 3;
        int searchH = height - 6;
        boolean searchHov = inside(mouseX, mouseY, searchX, searchY, searchW, searchH);
        DrawUtil.drawMatteNeonCard(graphics, searchX, searchY, searchW, searchH, 7, alpha,
                this.searchFocused ? 0.90F : (searchHov ? 0.35F : 0.0F), AuroraTheme.accent());
        String shown = this.searchQuery.isEmpty()
                ? (this.searchFocused ? "🔍 Поиск...|" : "🔍 Поиск модулей...")
                : ("🔍 " + this.searchQuery + (this.searchFocused ? "|" : ""));
        int searchCol = (this.searchQuery.isEmpty() && !this.searchFocused) ? 0xFF727B8E : 0xFFFFFFFF;
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(shown, Math.round((searchW - 10) / 0.75F)),
                searchX + 6, searchY + 5, ColorUtil.fade(searchCol, alpha), 0.75F, false);
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
            int rightX = x + width - 5;

            rightX = renderTopBarButton(graphics, rightX, btnY, 48, btnH, "✕ Выход", false, true, mouseX, mouseY, alpha,
                    () -> this.minecraft.stop()) - 5;
            rightX = renderTopBarButton(graphics, rightX, btnY, 64, btnH, "⚙ Опции MC", false, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new OptionsScreen(host, this.minecraft.options))) - 5;
            rightX = renderTopBarButton(graphics, rightX, btnY, 62, btnH, "🌐 Сетевая", false, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new JoinMultiplayerScreen(host))) - 5;
            renderTopBarButton(graphics, rightX, btnY, 72, btnH, "▶ Одиночная", true, false, mouseX, mouseY, alpha,
                    () -> this.minecraft.setScreen(new SelectWorldScreen(host)));
        } else {
            String hint = "ЛКМ: Вкл/Выкл · ПКМ: Настройки · СКМ: Бинд";
            int hintW = Math.round(this.font.width(hint) * 0.72F);
            int closeX = x + width - 19;
            int closeY = y + 3;
            if (closeX - hintW - 10 > searchX + searchW + 8) {
                DrawUtil.drawTextScaled(graphics, this.font, hint, closeX - hintW - 10, y + 7,
                        ColorUtil.fade(0xFF7A8396, alpha), 0.72F, false);
            }
            boolean closeHov = inside(mouseX, mouseY, closeX, closeY, 15, height - 6);
            DrawUtil.drawMatteNeonCard(graphics, closeX, closeY, 15, height - 6, 6, alpha,
                    closeHov ? 0.90F : 0.0F, AuroraTheme.danger());
            DrawUtil.drawCenteredTextScaled(graphics, this.font, "✕", closeX + 7, closeY + 4,
                    ColorUtil.fade(closeHov ? AuroraTheme.danger() : 0xFF9AA4B8, alpha), 0.75F, false);
            this.hitZones.add(new HitZone(closeX, closeY, 15, height - 6, this::onClose, null, null));
        }
    }

    private int renderTopBarButton(GuiGraphics graphics, int rightX, int y, int w, int h, String label,
                                   boolean primary, boolean danger, int mouseX, int mouseY, float alpha, Runnable action) {
        int bx = rightX - w;
        boolean hov = inside(mouseX, mouseY, bx, y, w, h);
        int neonCol = danger ? AuroraTheme.danger() : AuroraTheme.accent();
        float glow = primary ? (hov ? 1.0F : 0.75F) : (hov ? 0.85F : 0.0F);
        DrawUtil.drawMatteNeonCard(graphics, bx, y, w, h, 7, alpha, glow, neonCol);
        DrawUtil.drawCenteredTextScaled(graphics, this.font, label, bx + w / 2, y + 4,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.74F, false);
        this.hitZones.add(new HitZone(bx, y, w, h, action, null, null));
        return bx;
    }

    // ─────────────────── Отдельный вертикальный столбик категории (ClickGUI Column) ───────────────────

    private void renderCategoryColumn(GuiGraphics graphics, int colIdx, int colX, int colY, int colW, int maxColH,
                                      String title, ModuleCategory category,
                                      int mouseX, int mouseY, float alpha, float dt) {
        List<Module> modules = filteredByCategory(category);

        // Вычисляем полную высоту карточек внутри этого столбика
        int innerContentH = 6;
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

        int targetColH = Math.min(maxColH, Math.max(COL_HEADER_H + 54, COL_HEADER_H + innerContentH + 4));

        // Матовый чёрный корпус вертикального столбика
        DrawUtil.drawLiquidGlassPanel(graphics, colX, colY, colW, targetColH, 11, alpha);

        // Шапка столбика сверху + тонкая неоновая линия-акцент под шапкой
        renderColumnHeader(graphics, colX, colY, colW, title, modules.size(), alpha);

        int bodyTop = colY + COL_HEADER_H + 4;
        int bodyH = targetColH - COL_HEADER_H - 8;
        int cardX = colX + 5;
        int cardW = colW - 10;

        this.colMaxScroll[colIdx] = Math.max(0.0F, innerContentH - bodyH);
        if (this.colScroll[colIdx] > this.colMaxScroll[colIdx]) {
            this.colScroll[colIdx] = this.colMaxScroll[colIdx];
        }

        int curY = bodyTop - Math.round(this.colScroll[colIdx]);

        // Оставляем запас по X для мягкого неонового свечения (glow) вокруг карточек внутри столбика
        graphics.enableScissor(colX - 2, bodyTop - 2, colX + colW + 2, bodyTop + bodyH + 2);

        for (Module module : modules) {
            int cardH = renderModuleCard(graphics, module, cardX, curY, cardW,
                    bodyTop - 2, bodyTop + bodyH + 2, mouseX, mouseY, alpha, dt);
            curY += cardH + CARD_GAP;
        }

        // В столбике «Экран / HUD» ниже карточки HUD идут карточки «Редактор HUD» и «Чистый экран»
        if (category == ModuleCategory.HUD) {
            curY += renderHudExtraCards(graphics, cardX, curY, cardW,
                    bodyTop - 2, bodyTop + bodyH + 2, mouseX, mouseY, alpha);
        }

        graphics.disableScissor();
    }

    private void renderColumnHeader(GuiGraphics graphics, int colX, int colY, int colW,
                                    String title, int count, float alpha) {
        DrawUtil.drawTextScaled(graphics, this.font, "§l" + title, colX + 8, colY + 8,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.80F, false);

        String cntStr = Integer.toString(count);
        int cntW = Math.round(this.font.width(cntStr) * 0.74F) + 8;
        int cntX = colX + colW - cntW - 6;
        int cntY = colY + 6;
        DrawUtil.drawMatteNeonCard(graphics, cntX, cntY, cntW, 12, 5, alpha, 0.55F, AuroraTheme.accent());
        DrawUtil.drawCenteredTextScaled(graphics, this.font, cntStr, cntX + cntW / 2, cntY + 3,
                ColorUtil.fade(AuroraTheme.accent(), alpha), 0.74F, false);

        // Тонкая неоновая линия под шапкой столбика
        graphics.fill(colX + 6, colY + COL_HEADER_H - 1, colX + colW - 6, colY + COL_HEADER_H,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(140 * alpha)));
    }

    private int renderHudExtraCards(GuiGraphics graphics, int x, int startY, int width,
                                    int clipTop, int clipBottom, int mouseX, int mouseY, float alpha) {
        int curY = startY;

        // 1. Карточка «Редактор HUD (Drag & Drop)»
        if (curY + CARD_HEADER_H >= clipTop && curY <= clipBottom) {
            boolean hov = inside(mouseX, mouseY, x, curY, width, CARD_HEADER_H)
                    && mouseY >= clipTop && mouseY <= clipBottom;
            DrawUtil.drawMatteNeonCard(graphics, x, curY, width, CARD_HEADER_H, CARD_RADIUS, alpha,
                    hov ? 0.85F : 0.0F, AuroraTheme.accent());
            DrawUtil.drawTextScaled(graphics, this.font, "§lРедактор HUD", x + 8, curY + 8,
                    ColorUtil.fade(0xFFFFFFFF, alpha), 0.84F, false);
            DrawUtil.drawTextScaled(graphics, this.font, "Перетаскивание панелей", x + 8, curY + 21,
                    ColorUtil.fade(0xFF8C94A6, alpha), 0.72F, false);
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

        // 2. Карточка «Чистый экран» (F8)
        if (curY + CARD_HEADER_H >= clipTop && curY <= clipBottom) {
            KeybindManager km = KeybindManager.get();
            boolean cleanOn = km.isCleanScreenActive();
            boolean cleanListening = "CLEAN_SCREEN".equals(this.listeningBindTarget);
            DrawUtil.drawMatteNeonCard(graphics, x, curY, width, CARD_HEADER_H, CARD_RADIUS, alpha,
                    cleanOn ? 1.0F : 0.0F, AuroraTheme.accent());

            String keyStr = KeybindManager.formatKey(km.cleanScreenKeybind());
            int badgeW = cleanListening ? 28 : Math.max(16, Math.round(this.font.width(keyStr) * 0.70F) + 8);
            int bx = x + width - 6 - badgeW;
            int by = curY + 6;
            DrawUtil.drawMatteNeonCard(graphics, bx, by, badgeW, 12, 5, alpha,
                    cleanListening ? 1.0F : 0.40F, AuroraTheme.accent());
            if (cleanListening) {
                drawKeyboardIcon(graphics, bx + 4, by + 3, ColorUtil.fade(AuroraTheme.accent(), alpha));
                DrawUtil.drawTextScaled(graphics, this.font, "...", bx + 15, by + 3,
                        ColorUtil.fade(AuroraTheme.accent(), alpha), 0.70F, false);
            } else {
                DrawUtil.drawCenteredTextScaled(graphics, this.font, keyStr, bx + badgeW / 2, by + 3,
                        ColorUtil.fade(0xFFE8F0FF, alpha), 0.70F, false);
            }

            DrawUtil.drawTextScaled(graphics, this.font, "§lЧистый экран", x + 8, curY + 8,
                    ColorUtil.fade(0xFFFFFFFF, alpha), 0.84F, false);
            DrawUtil.drawTextScaled(graphics, this.font, "Скрытие HUD по клавише", x + 8, curY + 21,
                    ColorUtil.fade(0xFF8C94A6, alpha), 0.72F, false);

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

    // ─────────────────── Карточка модуля внутри вертикального столбика ───────────────────

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

        // 1. Матовый чёрный прямоугольник (#0B0C10) + Неоновая обводка с glow ТОЛЬКО при включённом модуле
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, totalH, CARD_RADIUS, alpha, toggleCurr, AuroraTheme.accent());

        // 2. Окошко бинда (СКМ) в правом верхнем углу карточки (иконка клавиатуры -> буква)
        int bindBadgeW = renderCornerKeybindPopup(graphics, module, module.keybind(),
                key -> {
                    module.setKeybind(key);
                    NotificationManager.get().pushKeybindChanged(module.name(), KeybindManager.formatKey(key));
                },
                x + width - 6, y + 6, mouseX, mouseY, clipTop, clipBottom, alpha, dt);

        // 3. Текст внутри карточки: Название (белым жирным) + короткое описание (серым мелким шрифтом)
        float titleScale = 0.84F;
        int availTitlePx = Math.max(32, width - 14 - (bindBadgeW > 0 ? bindBadgeW + 4 : 0));
        String titleStr = trimToWidth(module.name(), Math.round(availTitlePx / titleScale));
        DrawUtil.drawTextScaled(graphics, this.font, "§l" + titleStr, x + 7, y + 8,
                ColorUtil.fade(0xFFFFFFFF, alpha), titleScale, false);

        float descScale = 0.71F;
        int availDescPx = Math.max(36, width - 14);
        String descStr = trimToWidth(module.description(), Math.round(availDescPx / descScale));
        DrawUtil.drawTextScaled(graphics, this.font, descStr, x + 7, y + 21,
                ColorUtil.fade(0xFF8C94A6, alpha), descScale, false);

        // 4. Управление (ЛКМ / ПКМ / СКМ):
        // ЛКМ — включает/выключает модуль (зажигает/гасит неоновую обводку)
        // ПКМ — плавно раскрывает настройки вниз внутри карточки (аккордеон)
        // СКМ — вызывает окошко бинда с иконкой клавиатуры
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
                graphics.fill(x + 6, y + CARD_HEADER_H - 1, x + width - 6, y + CARD_HEADER_H,
                        ColorUtil.withAlpha(0xFFFFFFFF, Math.round(18 * alpha * expandCurr)));

                graphics.enableScissor(x + 2, innerTop, x + width - 2, innerBottom);
                renderInlineSettings(graphics, module, x + 6, y + CARD_HEADER_H + 4, width - 12,
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
        float scale = 0.70F;
        int baseW = Math.max(14, Math.round(this.font.width(keyLetter.isEmpty() ? "K" : keyLetter) * scale) + 8);
        int listenW = 28;
        int badgeW = Math.round(baseW + (listenW - baseW) * Easing.easeOutCubic(anim));
        int badgeH = 12;
        int bx = rightX - badgeW;

        boolean hov = inside(mouseX, mouseY, bx, topY, badgeW, badgeH) && mouseY >= clipTop && mouseY <= clipBottom;
        DrawUtil.drawMatteNeonCard(graphics, bx, topY, badgeW, badgeH, 5, alpha,
                listening ? 1.0F : (hov ? 0.65F : 0.35F), AuroraTheme.accent());

        int textCol = listening ? AuroraTheme.accent() : 0xFFE8F0FF;
        if (listening || anim > 0.35F) {
            drawKeyboardIcon(graphics, bx + 4, topY + 3, ColorUtil.fade(AuroraTheme.accent(), alpha));
            DrawUtil.drawTextScaled(graphics, this.font, "...", bx + 15, topY + 3,
                    ColorUtil.fade(textCol, alpha), scale, false);
        } else {
            DrawUtil.drawCenteredTextScaled(graphics, this.font, keyLetter, bx + badgeW / 2, topY + 3,
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
                    ColorUtil.fade(active ? AuroraTheme.accent() : 0xFF9AA4B8, alpha), 0.76F, false);

            if (mouseY >= clipTop && mouseY <= clipBottom) {
                this.hitZones.add(new HitZone(cx, y, cellW, cellH, () -> module.shapeSetting().set(shape), null, null));
            }
        }
    }

    // ─────────────────── Настройки внутри раскрытой карточки ───────────────────

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
                int swW = 20;
                int swH = 10;
                int swX = x + width - swW;
                int swY = y + (SETTING_ROW_H - swH) / 2;

                float scale = 0.73F;
                int maxLabelW = Math.round((swX - x - 4) / scale);
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
                float scale = 0.70F;
                String valText = "◂ " + enumSetting.get().toString() + " ▸";
                int pillW = Math.min(width * 54 / 100, Math.round(this.font.width(valText) * scale) + 8);
                int pillX = x + width - pillW;
                int pillY = y + 3;

                int maxLabelW = Math.round((pillX - x - 4) / 0.72F);
                DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(enumSetting.name(), maxLabelW),
                        x, y + 5, ColorUtil.fade(0xFFB4BECE, alpha), 0.72F, false);

                boolean pillHov = inside(mouseX, mouseY, pillX, pillY, pillW, 13);
                DrawUtil.drawMatteNeonCard(graphics, pillX, pillY, pillW, 13, 6, alpha,
                        pillHov ? 0.80F : 0.30F, AuroraTheme.accent());
                String trimmedVal = trimToWidth(valText, Math.round((pillW - 5) / scale));
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

                float scale = 0.73F;
                int maxLabelW = Math.round((circleX - x - 5) / scale);
                DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(colorSetting.name(), maxLabelW),
                        x, y + 5, ColorUtil.fade(0xFFB4BECE, alpha), scale, false);

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
        float scale = 0.72F;

        int valW = Math.round(this.font.width(valueText) * scale);
        int maxLabelW = Math.round((width - valW - 5) / scale);
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(setting.name(), maxLabelW),
                x, y + 2, ColorUtil.fade(0xFFB4BECE, alpha), scale, false);
        DrawUtil.drawTextScaled(graphics, this.font, valueText,
                x + width - valW, y + 2, ColorUtil.fade(AuroraTheme.accent(), alpha), scale, false);

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

    private void renderCompactColorPalettePopover(GuiGraphics graphics, ColorSetting setting,
                                                  int x, int y, int width, int clipTop, int clipBottom,
                                                  int mouseX, int mouseY, float alpha) {
        DrawUtil.drawMatteNeonCard(graphics, x, y, width, COLOR_EXPANDED_H - 2, 7, alpha, 0.35F, AuroraTheme.accent());

        int dotSize = 8;
        int totalDotsW = QUICK_SWATCHES.length * dotSize;
        int gap = Math.max(2, (width - 8 - totalDotsW) / (QUICK_SWATCHES.length - 1));
        int dotY = y + 5;
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

        int barY = y + 21;
        int halfW = (width - 14) / 2;
        int hueX = x + 4;
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

    // ─────────────────── 4-й вертикальный столбик: «Аккаунты» (ClickGUI Column) ───────────────────

    private void renderAccountsColumn(GuiGraphics graphics, int colIdx, int colX, int colY, int colW, int maxColH,
                                      String title, int mouseX, int mouseY, float alpha) {
        AccountManager am = AccountManager.get();
        List<String> saved = am.accounts();
        String currentName = am.currentUsername();

        int innerContentH = 6 + (CARD_HEADER_H + CARD_GAP) * (2 + saved.size());
        int targetColH = Math.min(maxColH, Math.max(COL_HEADER_H + 54, COL_HEADER_H + innerContentH + 4));

        DrawUtil.drawLiquidGlassPanel(graphics, colX, colY, colW, targetColH, 11, alpha);
        renderColumnHeader(graphics, colX, colY, colW, title, saved.size(), alpha);

        int bodyTop = colY + COL_HEADER_H + 4;
        int bodyH = targetColH - COL_HEADER_H - 8;
        int cardX = colX + 5;
        int cardW = colW - 10;

        this.colMaxScroll[colIdx] = Math.max(0.0F, innerContentH - bodyH);
        if (this.colScroll[colIdx] > this.colMaxScroll[colIdx]) {
            this.colScroll[colIdx] = this.colMaxScroll[colIdx];
        }

        int curY = bodyTop - Math.round(this.colScroll[colIdx]);

        graphics.enableScissor(colX - 2, bodyTop - 2, colX + colW + 2, bodyTop + bodyH + 2);

        // 1. Карточка «Случайный ник»
        boolean rndHov = inside(mouseX, mouseY, cardX, curY, cardW, CARD_HEADER_H);
        DrawUtil.drawMatteNeonCard(graphics, cardX, curY, cardW, CARD_HEADER_H, CARD_RADIUS, alpha,
                rndHov ? 0.85F : 0.0F, AuroraTheme.accent());
        DrawUtil.drawTextScaled(graphics, this.font, "§l🎲 Случайный ник", cardX + 7, curY + 8,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.82F, false);
        DrawUtil.drawTextScaled(graphics, this.font, "Сгенерировать в 1 клик", cardX + 7, curY + 21,
                ColorUtil.fade(0xFF8C94A6, alpha), 0.71F, false);
        this.hitZones.add(new HitZone(cardX, curY, cardW, CARD_HEADER_H, () -> {
            this.accountInput = am.switchRandomAccount();
        }, null, null));
        curY += CARD_HEADER_H + CARD_GAP;

        // 2. Карточка ввода нового ника
        boolean boxHov = inside(mouseX, mouseY, cardX, curY, cardW, CARD_HEADER_H);
        DrawUtil.drawMatteNeonCard(graphics, cardX, curY, cardW, CARD_HEADER_H, CARD_RADIUS, alpha,
                this.accountInputFocused ? 0.95F : (boxHov ? 0.45F : 0.0F), AuroraTheme.accent());
        DrawUtil.drawTextScaled(graphics, this.font, "§l✎ Новый аккаунт", cardX + 7, curY + 8,
                ColorUtil.fade(0xFFFFFFFF, alpha), 0.82F, false);
        String shownInput = this.accountInput.isEmpty()
                ? (this.accountInputFocused ? "Введите ник...|" : "Нажмите для ввода ника")
                : (this.accountInput + (this.accountInputFocused ? "|" : ""));
        DrawUtil.drawTextScaled(graphics, this.font, trimToWidth(shownInput, Math.round((cardW - 14) / 0.72F)),
                cardX + 7, curY + 21,
                ColorUtil.fade(this.accountInput.isEmpty() ? 0xFF8C94A6 : AuroraTheme.accent(), alpha), 0.72F, false);
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

            DrawUtil.drawTextScaled(graphics, this.font, "§l" + trimToWidth(acc, Math.round((cardW - 26) / 0.84F)),
                    cardX + 7, curY + 8, ColorUtil.fade(0xFFFFFFFF, alpha), 0.84F, false);
            DrawUtil.drawTextScaled(graphics, this.font, isCurrent ? "● Активен в сессии" : "ЛКМ — выбрать ник",
                    cardX + 7, curY + 21, ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF8C94A6, alpha), 0.71F, false);

            int delX = cardX + cardW - 16;
            int delY = curY + 7;
            DrawUtil.drawCenteredTextScaled(graphics, this.font, "✕", delX + 4, delY + 2,
                    ColorUtil.fade(0xFF727B8E, alpha), 0.72F, false);

            this.hitZones.add(new HitZone(cardX, curY, cardW - 20, CARD_HEADER_H, () -> am.switchAccount(acc), null, null));
            this.hitZones.add(new HitZone(delX, delY, 14, 14, () -> am.removeAccount(acc), null, null));
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
                        this.colScroll[c] - (float) verticalAmount * 22.0F));
                return true;
            }
        }
        for (int c = 0; c < NUM_COLS; c++) {
            this.colScroll[c] = Math.max(0.0F, Math.min(this.colMaxScroll[c],
                    this.colScroll[c] - (float) verticalAmount * 22.0F));
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

        // F6: Раскрытие настроек внутри карточек в столбиках Визуал и Интерфейс + компактная палитра цветов
        if (key == GLFW.GLFW_KEY_F6) {
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
