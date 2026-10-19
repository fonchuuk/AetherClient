package com.aetherglass.client.gui.screen;

import com.aetherglass.client.AetherGlassClient;
import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.menu.AtmosphericMessages;
import com.aetherglass.client.gui.menu.WinterNight;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.gui.widget.AuroraLabel;
import com.aetherglass.client.gui.widget.ModuleRow;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.modules.impl.InterfaceModule;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.realmsclient.RealmsMainScreen;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню клиента: три страницы — «Главная», «Функции» и «Настройки».
 *
 * <p>Главная страница полностью заменяет ванильное меню и собрана в одном
 * стиле «жидкое стекло»: крупная стеклянная панель с названием и док с
 * действиями. Кнопок «для вида» нет — каждая открывает реальный экран игры
 * или выполняет реальное действие: одиночная игра, сетевая игра, Realms,
 * настройки, язык, доступность, модули, редактор элементов, выход.</p>
 *
 * <p>Фон — зимняя ночь: холодный градиент, сияние над горизонтом, ореол луны,
 * редкие стеклянные пластины и медленный снегопад. Поверх фона всплывают
 * атмосферные надписи: они дрейфуют, плавно гаснут, чуть реагируют на курсор
 * и никогда не заходят на панели и кнопки.</p>
 *
 * <p>Все размеры считаются от размера окна, поэтому при любом разрешении и
 * масштабе интерфейса элементы не наезжают друг на друга.</p>
 */
public final class AuroraMenuScreen extends AuroraScreen {
    // Настоящее стекло: спрайты посчитаны заранее (преломление, блик, толщина
    // кромки) инструментом tools/make_glass.py и лежат внутри мода. Процедурная
    // отрисовка рисуется поверх — она даёт реакцию на наведение и анимацию.
    private static final Identifier TEX_LOGO =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/menu_glass_logo.png");
    private static final Identifier TEX_PANEL =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/menu_glass_panel.png");
    private static final int TEX_LOGO_SIZE = 426;   // спрайт квадратный, форма внутри — овал
    private static final int TEX_PANEL_W = 715;
    private static final int TEX_PANEL_H = 506;

    /** Страница меню. */
    private enum Tab {
        HOME("Главная"),
        FUNCTIONS("Функции"),
        SETTINGS("Настройки");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final int BUTTON_GAP = 6;
    private static final int DOCK_PADDING = 14;
    private static final int MAX_BUTTON_HEIGHT = 22;
    private static final int MIN_BUTTON_HEIGHT = 16;

    /** Название клиента по символам: градиент рисуется отдельно для каждого. */
    private static final String BRAND = "AetherGlass";
    private static final String[] BRAND_GLYPHS = split(BRAND);

    private final WinterNight winter = new WinterNight();
    private final AtmosphericMessages messages = new AtmosphericMessages();

    private Tab tab = Tab.HOME;

    /** Габариты левой панели и правого дока: считаются при построении. */
    private int heroX;
    private int heroY;
    private int heroWidth;
    private int heroHeight;
    private int dockX;
    private int dockY;
    private int dockWidth;
    private int dockHeight;

    public AuroraMenuScreen() {
        super(Component.literal("AetherGlass Client"), Component.literal("своё меню и визуальные эффекты"));
    }

    // ─────────────────────────── построение ───────────────────────────

    @Override
    protected void build() {
        InterfaceModule look = look();
        this.animated = look == null || look.animatedBackground();
        buildTabs();
        switch (this.tab) {
            case FUNCTIONS -> buildFunctions();
            case SETTINGS -> buildSettings();
            default -> buildHome();
        }
    }

    private void buildTabs() {
        int width = 96;
        int total = Tab.values().length * width;
        int startX = this.width - MARGIN - total;
        int y = MARGIN;
        for (Tab value : Tab.values()) {
            boolean active = value == this.tab;
            addWidget(new AuroraButton(startX, y, width - 6, 24, Component.literal(value.title),
                    button -> {
                        this.tab = value;
                        rebuild();
                    }, 0, active));
            startX += width;
        }
    }

    /**
     * Главная страница: панель с названием слева и док с действиями справа.
     * Обе панели — «жидкое стекло»; высота подстраивается под окно, поэтому
     * кнопки всегда помещаются целиком.
     */
    private void buildHome() {
        int available = this.width - MARGIN * 2;

        // Формы крупнее: логотип слева и панель по центру должны читаться
        // издалека, а не выглядеть двумя узкими полосками по краям.
        int heroWidth = clamp(Math.round(available * 0.34F), 300, 480);
        int gap = 40;
        int heroX = MARGIN + 10;

        // Панель стоит по центру оставшегося места, а не приклеена справа:
        // ровно по середине между логотипом и правым краем окна.
        int restLeft = heroX + heroWidth + gap;
        int restWidth = Math.max(240, this.width - MARGIN - restLeft);
        int panelWidth = Math.min(clamp(Math.round(available * 0.36F), 330, 560), restWidth);
        int panelX = restLeft + Math.max(0, (restWidth - panelWidth) / 2);

        int actions = 12;
        int maxPanel = Math.min(396, this.height - MARGIN * 2 - TITLE_HEIGHT - 20);
        int panelHeight = Math.max(190, maxPanel);
        int buttonHeight = clamp((panelHeight - DOCK_PADDING * 2 - BUTTON_GAP * (actions - 1)) / actions,
                MIN_BUTTON_HEIGHT, MAX_BUTTON_HEIGHT);
        int dockContent = DOCK_PADDING * 2 + buttonHeight * actions + BUTTON_GAP * (actions - 1);
        int panelTop = Math.max(contentTop() - 6, (this.height - dockContent) / 2);

        this.heroX = heroX;
        this.heroY = panelTop;
        this.heroWidth = heroWidth;
        this.heroHeight = dockContent;
        this.dockX = panelX;
        this.dockY = panelTop;
        this.dockWidth = panelWidth;
        this.dockHeight = dockContent;

        int buttonX = this.dockX + DOCK_PADDING;
        int buttonWidth = Math.max(140, this.dockWidth - DOCK_PADDING * 2);
        int y = this.dockY + DOCK_PADDING;

        // Обязательные действия ванильного меню — все рабочие.
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Одиночная игра", true,
                () -> client().setScreen(new SelectWorldScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Сетевая игра", true,
                () -> client().setScreen(new JoinMultiplayerScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Realms", true,
                () -> client().setScreen(new RealmsMainScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Настройки", false,
                () -> client().setScreen(new OptionsScreen(this, client().options)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Язык", false,
                () -> client().setScreen(new LanguageSelectScreen(this, client().options,
                        client().getLanguageManager())));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Доступность", false,
                () -> client().setScreen(new AccessibilityOptionsScreen(this, client().options)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Функции клиента", false, () -> {
            this.tab = Tab.FUNCTIONS;
            rebuild();
        });
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Экранные элементы", false,
                () -> client().setScreen(new HudEditorScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Шейдер-эффекты (16)", true,
                () -> client().setScreen(new ShaderEffectsScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "Материалы", false,
                () -> client().setScreen(new MaterialsScreen(this)));
        y = addAction(buttonX, y, buttonWidth, buttonHeight, "О клиенте", false,
                () -> client().setScreen(new ClientInfoScreen(this)));
        addAction(buttonX, y, buttonWidth, buttonHeight, "Выход", false, this::confirmQuit);

        updateMessageZones();
    }

    /** Добавляет кнопку действия и возвращает позицию следующей. */
    private int addAction(int x, int y, int width, int height, String text, boolean emphasized, Runnable action) {
        addWidget(new AuroraButton(x, y, width, height, Component.literal(text),
                button -> action.run(), 0, emphasized));
        return y + height + BUTTON_GAP;
    }

    /** Выход подтверждается: случайное нажатие не должно закрывать игру. */
    private void confirmQuit() {
        BooleanConsumer answer = accepted -> {
            if (accepted) {
                client().stop();
            } else {
                client().setScreen(this);
            }
        };
        client().setScreen(new ConfirmScreen(answer,
                Component.literal("Выход из игры"),
                Component.literal("Выйти из игры?"),
                Component.literal("Выйти"),
                Component.literal("Отмена")));
    }

    private void buildFunctions() {
        ModuleManager manager = ModuleManager.get();
        int x = contentLeft();
        int width = contentWidth();
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Модули включаются и выключаются сразу: перезапуск не требуется. "
                        + "Щелчок по карточке тоже переключает модуль."), AuroraLabel.Style.MUTED), 4);
        this.list.add(new AuroraButton(x + 4, 0, Math.min(280, width - 8), 22,
                Component.literal("Открыть меню 16 шейдер-эффектов (Block / Hands)…"),
                button -> client().setScreen(new ShaderEffectsScreen(this)), 0, true), 6);
        for (Module module : manager.modules()) {
            this.list.add(new ModuleRow(x, 0, width, module, activity(module), this::rebuild,
                    () -> client().setScreen(new ModuleSettingsScreen(this, module))), 8);
        }
        for (String problem : manager.problems()) {
            this.list.add(new AuroraLabel(x + 4, 0, width - 8, Component.literal("• " + problem),
                    AuroraLabel.Style.MUTED), 4);
        }
    }

    private void buildSettings() {
        InterfaceModule look = look();
        int x = contentLeft();
        int width = contentWidth();
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Оформление применяется сразу: закройте экран и посмотрите на меню."),
                AuroraLabel.Style.MUTED), 4);
        if (look != null) {
            SettingWidgets.addGroup(this.list, "Оформление", x, width);
            SettingWidgets.addAll(this.list, look, x, width, () -> {
            });
        }
        SettingWidgets.addGroup(this.list, "Экранные элементы", x, width);
        this.list.add(button(x + 4, 0, 200, "Открыть редактор",
                () -> client().setScreen(new HudEditorScreen(this))), 8);
        HudModule hud = ModuleManager.get().typed("hud", HudModule.class);
        if (hud != null) {
            SettingWidgets.addAll(this.list, hud, x, width, () -> {
            });
        }
        SettingWidgets.addGroup(this.list, "Файл настроек", x, width);
        ModConfig config = ModConfig.get();
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Расположение: " + config.file().toAbsolutePath()),
                AuroraLabel.Style.MUTED), 6);
        this.list.add(new AuroraLabel(x + 4, 0, width - 8,
                Component.literal("Состояние: " + config.status()), AuroraLabel.Style.MUTED), 2);
        this.list.add(button(x + 4, 0, 200, "Сохранить настройки", () -> {
            ModuleManager.get().saveNow();
            rebuild();
        }), 8);
        this.list.add(button(x + 4, 0, 200, "Сбросить оформление", () -> {
            if (look != null) {
                for (com.aetherglass.client.modules.settings.Setting<?> setting : look.settings()) {
                    setting.reset();
                }
            }
            rebuild();
        }), 6);
    }

    private AuroraButton button(int x, int y, int width, String text, Runnable action) {
        return new AuroraButton(x, y, width, MAX_BUTTON_HEIGHT, Component.literal(text), button -> action.run());
    }

    private InterfaceModule look() {
        return ModuleManager.get().typed("interface", InterfaceModule.class);
    }

    private static String activity(Module module) {
        return switch (module.id()) {
            case "particles" -> "частиц: " + ((com.aetherglass.client.modules.impl.ParticleModule) module).activity();
            case "blocks" -> ((com.aetherglass.client.modules.impl.BlockEffectsModule) module).activity();
            case "hands" -> "появление: " + ((com.aetherglass.client.modules.impl.HandEffectsModule) module).activity();
            case "sky" -> ((com.aetherglass.client.modules.impl.SkyEffectsModule) module).activity();
            case "rainbow" -> ((com.aetherglass.client.modules.impl.RainbowModule) module).activity();
            case "crosshair" -> ((com.aetherglass.client.modules.impl.CrosshairModule) module).activity();
            case "hud" -> ((HudModule) module).activity();
            case "interface" -> ((InterfaceModule) module).activity();
            default -> "";
        };
    }

    // ─────────────────────────── отрисовка ───────────────────────────

    /**
     * Фон всех страниц меню — зимняя ночь, один визуальный язык для меню,
     * настроек и списка модулей.
     */
    @Override
    protected void drawBackdrop(GuiGraphics graphics) {
        this.winter.draw(graphics, this.width, this.height, this.animated);
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = FrameClock.deltaSeconds();
        if (this.animated) {
            this.winter.update(delta);
        }
        if (this.tab != Tab.HOME) {
            this.messages.clear();
            return;
        }
        drawHero(graphics);
        drawDock(graphics);
        this.messages.update(delta, mouseX, mouseY, this.width, this.height);
        this.messages.render(graphics, this.font);
    }

    /** Левая панель: название, описание и «чипы» с реальными сведениями. */
    private void drawHero(GuiGraphics graphics) {
        int radius = Math.round(AuroraTheme.radius());
        // Сначала спрайт настоящего стекла, потом толстая кромка поверх.
        drawGlassPlate(graphics, TEX_LOGO, TEX_LOGO_SIZE, TEX_LOGO_SIZE,
                this.heroX, this.heroY, this.heroWidth, this.heroHeight, radius);

        Font font = this.font;
        int textX = this.heroX + 22;
        int textY = this.heroY + 26;

        // Заголовок: градиент по символам, без шейдеров и без картинок.
        float scale = this.heroWidth >= 320 ? 2.1F : 1.8F;
        drawBrand(graphics, textX, textY, scale);
        textY += Math.round(font.lineHeight * scale) + 6;

        graphics.drawString(font, "жидкое стекло · свои эффекты · без шейдер-паков",
                textX, textY, AuroraTheme.textSecondary(), false);
        textY += font.lineHeight + 16;

        ModuleManager manager = ModuleManager.get();
        textY = chip(graphics, textX, textY, "Minecraft " + AetherGlassClient.minecraftVersion()
                + " · Fabric", true) + 8;
        textY = chip(graphics, textX, textY, "клиент " + AetherGlassClient.VERSION, true) + 8;
        textY = chip(graphics, textX, textY, "модулей включено: " + manager.enabledCount()
                + " из " + manager.modules().size(), true) + 8;
        chip(graphics, textX, textY, "настройки: " + ModConfig.get().status(), true);

        String hint = "меню клиента — клавиша " + keyName();
        graphics.drawString(font, hint, textX, this.heroY + this.heroHeight - 20,
                AuroraTheme.textMuted(), false);
    }

    /** Правый док: стеклянная подложка под кнопками и подпись сверху. */
    private void drawDock(GuiGraphics graphics) {
        int radius = Math.round(AuroraTheme.radius());
        drawGlassPlate(graphics, TEX_PANEL, TEX_PANEL_W, TEX_PANEL_H,
                this.dockX, this.dockY, this.dockWidth, this.dockHeight, radius);

        // Подпись — по центру панели: панель стоит по середине окна, и левый
        // край для заголовка читался бы как сдвиг в сторону.
        String title = "навигация";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title,
                this.dockX + (this.dockWidth - titleWidth) / 2,
                this.dockY + DOCK_PADDING - 12, AuroraTheme.textMuted(), false);
    }

    /**
     * Стеклянная пластина: заранее посчитанный спрайт плюс многослойная кромка.
     *
     * <p>Спрайт даёт преломление и размытие фона, которых процедурной отрисовкой
     * не получить: она работает пиксельными прямоугольниками и не умеет
     * размывать то, что уже нарисовано под ней.</p>
     */
    private void drawGlassPlate(GuiGraphics graphics, Identifier texture, int texWidth, int texHeight,
                                int x, int y, int width, int height, int radius) {
        int corner = Math.min(Math.min(texWidth, texHeight) / 3, radius * 2);
        if (width <= 0 || height <= 0) {
            return;
        }
        drawNineSlice(graphics, texture, x, y, width, height, texWidth, texHeight, corner);
        drawThickRim(graphics, x, y, width, height, radius);
    }

    /**
     * Растяжение спрайта по частям: углы, стороны и центр тянутся по-разному.
     *
     * <p>Обычное растяжение тянет картинку целиком, из-за чего скруглённые
     * углы вытягиваются в эллипсы и стекло выглядит резиновым. Здесь четыре
     * угла рисуются в своём исходном размере, четыре стороны тянутся только
     * вдоль себя, а центр — в обе стороны. Пластина любого размера сохраняет
     * форму углов и толщину кромки.</p>
     */
    private void drawNineSlice(GuiGraphics graphics, Identifier texture, int x, int y, int width, int height,
                               int texWidth, int texHeight, int corner) {
        // Окно может быть каким угодно узким: если пластина меньше двух
        // углов, рисовать её частями нечего — выходим, не передавая в blit
        // отрицательные размеры.
        if (width < 4 || height < 4 || texWidth < 4 || texHeight < 4) {
            return;
        }
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        int cw = clamp(corner, 1, Math.max(1, texWidth / 2));
        int ch = clamp(corner, 1, Math.max(1, texHeight / 2));
        // Углы не должны съесть всю пластину, когда окно узкое.
        while (cw * 2 > width && cw > 1) {
            cw--;
        }
        while (ch * 2 > height && ch > 1) {
            ch--;
        }
        int srcMidW = texWidth - cw * 2;
        int srcMidH = texHeight - ch * 2;
        int dstMidW = width - cw * 2;
        int dstMidH = height - ch * 2;
        int rightX = x + width - cw;
        int bottomY = y + height - ch;

        // Углы — один в один, форма скругления не искажается.
        graphics.blit(pipeline, texture, x, y, 0, 0, cw, ch, cw, ch, texWidth, texHeight);
        graphics.blit(pipeline, texture, rightX, y, texWidth - cw, 0, cw, ch, cw, ch, texWidth, texHeight);
        graphics.blit(pipeline, texture, x, bottomY, 0, texHeight - ch, cw, ch, cw, ch, texWidth, texHeight);
        graphics.blit(pipeline, texture, rightX, bottomY, texWidth - cw, texHeight - ch,
                cw, ch, cw, ch, texWidth, texHeight);

        // Стороны — тянутся только вдоль себя.
        graphics.blit(pipeline, texture, x + cw, y, cw, 0, dstMidW, ch, srcMidW, ch, texWidth, texHeight);
        graphics.blit(pipeline, texture, x + cw, bottomY, cw, texHeight - ch,
                dstMidW, ch, srcMidW, ch, texWidth, texHeight);
        graphics.blit(pipeline, texture, x, y + ch, 0, ch, cw, dstMidH, cw, srcMidH, texWidth, texHeight);
        graphics.blit(pipeline, texture, rightX, y + ch, texWidth - cw, ch,
                cw, dstMidH, cw, srcMidH, texWidth, texHeight);

        // Центр — в обе стороны.
        graphics.blit(pipeline, texture, x + cw, y + ch, cw, ch,
                dstMidW, dstMidH, srcMidW, srcMidH, texWidth, texHeight);
    }

    /**
     * Толстая кромка стекла: три слоя.
     *
     * <p>Одна тонкая линия читается как обычная рамка. Три — как торец
     * настоящего стекла: внешняя полоса тусклая, средняя телесная, внутренняя
     * яркая, и по самому верху идёт почти белый блик. Снизу наоборот — тень,
     * иначе пластина выглядит плоской наклейкой.</p>
     */
    private void drawThickRim(GuiGraphics graphics, int x, int y, int width, int height, int radius) {
        int r = Math.max(2, radius);
        int outer = ColorUtil.fade(AuroraTheme.panelBorder(), 0.60F);
        int mid = ColorUtil.fade(AuroraTheme.glassHighlight(), 0.45F);
        int top = ColorUtil.fade(AuroraTheme.glassHighlight(), 0.92F);
        int bottom = ColorUtil.fade(AuroraTheme.glassShadow(), 0.90F);

        DrawUtil.fillRoundedBordered(graphics, x, y, width, height, r, 0, outer, 2);
        DrawUtil.fillRoundedBordered(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.max(1, r - 1), 0, mid, 1);

        int inset = Math.min(r, 3);
        DrawUtil.fillRounded(graphics, x + inset + 1, y + 2,
                Math.max(1, width - (inset + 1) * 2), 1, r, top);
        DrawUtil.fillRounded(graphics, x + inset + 1, y + height - 3,
                Math.max(1, width - (inset + 1) * 2), 1, r, bottom);
    }

    /** Название клиента: каждый символ — свой оттенок, получается градиент. */
    private void drawBrand(GuiGraphics graphics, int x, int y, float scale) {
        Font font = this.font;
        float hue = AuroraTheme.accentHue();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        int cursor = 0;
        for (int index = 0; index < BRAND_GLYPHS.length; index++) {
            String glyph = BRAND_GLYPHS[index];
            float shift = BRAND_GLYPHS.length <= 1 ? 0.0F : (float) index / (BRAND_GLYPHS.length - 1);
            int color = ColorUtil.hsb(hue - 0.13F + shift * 0.26F, 0.55F, 1.0F);
            graphics.drawString(font, glyph, cursor, 0, color, false);
            cursor += font.width(glyph);
        }
        graphics.pose().popMatrix();
    }

    /** «Чип»: стеклянная капсула с короткой подписью. Возвращает низ элемента. */
    private int chip(GuiGraphics graphics, int x, int y, String text, boolean emphasized) {
        Font font = this.font;
        int height = font.lineHeight + 6;
        int width = Math.min(this.heroWidth - 44, font.width(text) + 18);
        int color = emphasized ? AuroraTheme.textSecondary() : AuroraTheme.textMuted();
        DrawUtil.fillRounded(graphics, x, y, width, height, height / 2,
                ColorUtil.withAlpha(AuroraTheme.glassHighlight(), emphasized ? 28 : 18));
        graphics.drawString(font, text, x + 9, y + 3, color, false);
        return y + height;
    }

    /** Зоны, в которых атмосферные надписи появляться не должны. */
    private void updateMessageZones() {
        List<AtmosphericMessages.Zone> zones = new ArrayList<>(4);
        zones.add(new AtmosphericMessages.Zone(this.heroX, this.heroY, this.heroWidth, this.heroHeight));
        zones.add(new AtmosphericMessages.Zone(this.dockX, this.dockY, this.dockWidth, this.dockHeight));
        int tabWidth = Tab.values().length * 96;
        zones.add(new AtmosphericMessages.Zone(this.width - MARGIN - tabWidth, MARGIN, tabWidth, 26));
        zones.add(new AtmosphericMessages.Zone(MARGIN, this.height - MARGIN - 16,
                Math.min(this.width / 2, 420), 18));
        this.messages.setZones(zones);
    }

    private static String keyName() {
        net.minecraft.client.KeyMapping key = AetherGlassClient.menuKey();
        if (key == null) {
            return "не назначена";
        }
        return key.getTranslatedKeyMessage().getString();
    }

    private static String[] split(String value) {
        String[] glyphs = new String[value.length()];
        for (int index = 0; index < value.length(); index++) {
            glyphs[index] = String.valueOf(value.charAt(index));
        }
        return glyphs;
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
        String hint = switch (this.tab) {
            case FUNCTIONS -> "Щелчок по карточке — включить или выключить модуль";
            case SETTINGS -> "Изменения применяются сразу и сами записываются в файл";
            default -> "Все эффекты рисуются средствами игры: ни одного шейдер-пака";
        };
        graphics.drawString(this.font, hint, MARGIN, this.height - MARGIN, AuroraTheme.textMuted(), false);
    }

    /** Окно изменило размер: надписи расставляются заново по новой геометрии. */
    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.messages.clear();
        this.winter.resize(width, height);
    }

    @Override
    public void removed() {
        this.messages.clear();
        ModuleManager.get().saveNow();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // Из главного меню закрываться некуда: Escape просто ничего не делает.
        // Если же меню открыто во время игры, Escape возвращает в игру.
        return client().level != null;
    }
}
