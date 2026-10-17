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
import com.mojang.realmsclient.RealmsMainScreen;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
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
        int heroWidth = clamp(Math.round(available * 0.30F), 250, 360);
        int dockWidth = clamp(Math.round(available * 0.24F), 230, 330);
        int gap = 26;
        int left = Math.max(MARGIN, (this.width - (heroWidth + gap + dockWidth)) / 2);

        int actions = 11;
        int maxPanel = Math.min(300, this.height - MARGIN * 2 - TITLE_HEIGHT - 26);
        int panelHeight = Math.max(150, maxPanel);
        int buttonHeight = clamp((panelHeight - DOCK_PADDING * 2 - BUTTON_GAP * (actions - 1)) / actions,
                MIN_BUTTON_HEIGHT, MAX_BUTTON_HEIGHT);
        int dockContent = DOCK_PADDING * 2 + buttonHeight * actions + BUTTON_GAP * (actions - 1);
        int panelTop = Math.max(contentTop() - 6, (this.height - panelHeight) / 2);

        this.heroX = left;
        this.heroY = panelTop;
        this.heroWidth = heroWidth;
        this.heroHeight = panelHeight;
        this.dockX = left + heroWidth + gap;
        this.dockY = panelTop;
        this.dockWidth = dockWidth;
        this.dockHeight = dockContent;

        int buttonX = this.dockX + DOCK_PADDING;
        int buttonWidth = Math.max(120, this.dockWidth - DOCK_PADDING * 2);
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
        DrawUtil.fillGlass(graphics, this.heroX, this.heroY, this.heroWidth, this.heroHeight, radius);

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
        DrawUtil.fillGlass(graphics, this.dockX, this.dockY, this.dockWidth, this.dockHeight, radius);
        graphics.drawString(this.font, "навигация", this.dockX + DOCK_PADDING,
                this.dockY + DOCK_PADDING - 12, AuroraTheme.textMuted(), false);
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
