package com.aetherglass.client.gui.screen;

import com.aetherglass.client.AetherGlassClient;
import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.menu.AtmosphericMessages;
import com.aetherglass.client.gui.menu.WinterNight;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.gui.widget.AuroraButton;
import com.aetherglass.client.input.KeybindManager;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.InterfaceModule;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Главное меню AetherGlass в стиле матового жидкого стекла (Lunar / Badlion / Feather Frosted Glass).
 *
 * <p>Вместо громоздких синих труб и плоских пластин использует компактные полупрозрачные
 * стеклянные капсулы (25–30% дымчатая тонировка, мягкая внешняя тень, молочный купол
 * и поверхностный солнечный блик) поверх живого ночного неба {@link WinterNight}.
 * Все кнопки модулей, шейдеров и оформления открывают единое инлайн-окно
 * {@link AuroraInlineMenuScreen} с боковой навигацией и аккордеоном карточек.</p>
 */
public final class AuroraMenuScreen extends AuroraScreen {
    @SuppressWarnings("unused")
    private static final Identifier TEX_LOGO =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/menu_glass_logo.png");
    @SuppressWarnings("unused")
    private static final Identifier TEX_PANEL =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/menu_glass_panel.png");
    @SuppressWarnings("unused")
    private static final int TEX_LOGO_SIZE = 426;
    @SuppressWarnings("unused")
    private static final int TEX_PANEL_W = 715;
    @SuppressWarnings("unused")
    private static final int TEX_PANEL_H = 506;

    /** Вкладки быстрого перехода из главного меню. */
    public enum Tab {
        HOME("Главная"),
        FUNCTIONS("Модули"),
        SHADERS("16 Шейдеров"),
        SETTINGS("Оформление");

        private final String title;

        Tab(String title) {
            this.title = title;
        }
    }

    private static final String BRAND = "AetherGlass";
    private static final String[] BRAND_GLYPHS = split(BRAND);

    private final WinterNight winter = new WinterNight();
    private final AtmosphericMessages messages = new AtmosphericMessages();

    private Tab tab = Tab.HOME;

    private int heroX;
    private int heroY;
    private int heroWidth;
    private int heroHeight;
    private int dockX;
    private int dockY;
    private int dockWidth;
    private int dockHeight;

    public AuroraMenuScreen() {
        this(Tab.HOME);
    }

    public AuroraMenuScreen(Tab initialTab) {
        super(Component.literal("AetherGlass"),
                Component.literal("Liquid Glass · Minecraft 1.21.11"));
        this.tab = initialTab != null ? initialTab : Tab.HOME;
    }

    @Override
    protected void build() {
        InterfaceModule look = look();
        this.animated = look == null || look.animatedBackground();

        // Если запрошена вкладка модулей/шейдеров/настроек — сразу открываем единое инлайн-окно
        if (this.tab == Tab.FUNCTIONS && client() != null) {
            this.tab = Tab.HOME;
            client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks")));
            return;
        } else if (this.tab == Tab.SHADERS && client() != null) {
            this.tab = Tab.HOME;
            client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks"), ModuleCategory.VISUAL));
            return;
        } else if (this.tab == Tab.SETTINGS && client() != null) {
            this.tab = Tab.HOME;
            client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("interface"), ModuleCategory.INTERFACE));
            return;
        }

        buildTopQuickBar();
        buildHome();
    }

    /** Компактные стеклянные капсулы быстрого перехода в правом верхнем углу. */
    private void buildTopQuickBar() {
        int btnW = 84;
        int btnH = 20;
        int gap = 5;
        int count = 5;
        int totalW = count * btnW + (count - 1) * gap;
        int startX = this.width - MARGIN - totalW;
        int y = MARGIN;

        addWidget(new AuroraButton(startX, y, btnW, btnH, Component.literal("✦ Модули"),
                btn -> client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks"))),
                0, true));
        startX += btnW + gap;

        addWidget(new AuroraButton(startX, y, btnW, btnH, Component.literal("👤 Аккаунты"),
                btn -> client().setScreen(AuroraInlineMenuScreen.openAccounts(this)),
                0, false));
        startX += btnW + gap;

        addWidget(new AuroraButton(startX, y, btnW, btnH, Component.literal("❖ 16 Шейдеров"),
                btn -> client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks"), ModuleCategory.VISUAL)),
                0, false));
        startX += btnW + gap;

        addWidget(new AuroraButton(startX, y, btnW, btnH, Component.literal("◎ HUD Редактор"),
                btn -> client().setScreen(new HudEditorScreen(this)),
                0, false));
        startX += btnW + gap;

        addWidget(new AuroraButton(startX, y, btnW, btnH, Component.literal("⚙ Оформление"),
                btn -> client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("interface"), ModuleCategory.INTERFACE)),
                0, false));
    }

    /**
     * Компактная сбалансированная компоновка главного меню в стиле Lunar / Feather:
     * слева — полупрозрачная стеклянная карточка статуса, справа — компактный док с аккуратными капсулами.
     */
    private void buildHome() {
        int cardGap = 16;
        this.heroWidth = Math.min(242, Math.max(210, Math.round(this.width * 0.28F)));
        this.dockWidth = Math.min(248, Math.max(208, Math.round(this.width * 0.28F)));

        int totalClusterW = this.heroWidth + cardGap + this.dockWidth;
        int clusterLeft = Math.max(MARGIN, (this.width - totalClusterW) / 2);

        int pad = 12;
        int rowGap = 6;
        int primaryH = 22;
        int secondaryH = 20;

        // 3 основные компактные капсулы + 4 ряда парных мини-капсул
        int dockContentH = pad + 20 + 3 * primaryH + 2 * rowGap + 8 + 4 * secondaryH + 3 * rowGap + pad;
        int cardTop = Math.max(contentTop() + 4, (this.height - dockContentH) / 2 + 8);

        this.heroX = clusterLeft;
        this.heroY = cardTop;
        this.heroHeight = dockContentH;

        this.dockX = clusterLeft + this.heroWidth + cardGap;
        this.dockY = cardTop;
        this.dockHeight = dockContentH;

        int innerX = this.dockX + pad;
        int innerW = this.dockWidth - pad * 2;
        int y = this.dockY + pad + 18;

        // 3 главные компактные стеклянные капсулы
        addWidget(new AuroraButton(innerX, y, innerW, primaryH, Component.literal("▶ Одиночная игра"),
                btn -> client().setScreen(new SelectWorldScreen(this)), 0, true));
        y += primaryH + rowGap;

        addWidget(new AuroraButton(innerX, y, innerW, primaryH, Component.literal("◈ Сетевая игра"),
                btn -> client().setScreen(new JoinMultiplayerScreen(this)), 0, true));
        y += primaryH + rowGap;

        addWidget(new AuroraButton(innerX, y, innerW, primaryH, Component.literal("✦ Модули клиента (RShift)"),
                btn -> client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks"))), 0, true));
        y += primaryH + 10;

        // Аккуратная двухколоночная сетка компактных стеклянных капсул
        int colGap = 6;
        int halfW = (innerW - colGap) / 2;
        int rightColX = innerX + halfW + colGap;

        addWidget(new AuroraButton(innerX, y, halfW, secondaryH, Component.literal("◎ Редактор HUD"),
                btn -> client().setScreen(new HudEditorScreen(this)), 0, false));
        addWidget(new AuroraButton(rightColX, y, innerW - halfW - colGap, secondaryH, Component.literal("❖ 16 Шейдеров"),
                btn -> client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks"), ModuleCategory.VISUAL)), 0, false));
        y += secondaryH + rowGap;

        addWidget(new AuroraButton(innerX, y, halfW, secondaryH, Component.literal("⚙ Настройки"),
                btn -> client().setScreen(new OptionsScreen(this, client().options)), 0, false));
        addWidget(new AuroraButton(rightColX, y, innerW - halfW - colGap, secondaryH, Component.literal("🌐 Язык"),
                btn -> client().setScreen(new LanguageSelectScreen(this, client().options, client().getLanguageManager())), 0, false));
        y += secondaryH + rowGap;

        addWidget(new AuroraButton(innerX, y, halfW, secondaryH, Component.literal("✦ Realms"),
                btn -> client().setScreen(new RealmsMainScreen(this)), 0, false));
        addWidget(new AuroraButton(rightColX, y, innerW - halfW - colGap, secondaryH, Component.literal("♿ Доступность"),
                btn -> client().setScreen(new AccessibilityOptionsScreen(this, client().options)), 0, false));
        y += secondaryH + rowGap;

        addWidget(new AuroraButton(innerX, y, halfW, secondaryH, Component.literal("ℹ О клиенте"),
                btn -> client().setScreen(new ClientInfoScreen(this)), 0, false));
        addWidget(new AuroraButton(rightColX, y, innerW - halfW - colGap, secondaryH, Component.literal("✕ Выход"),
                btn -> confirmQuit(), AuroraTheme.danger(), false));

        updateMessageZones();
    }

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

    private InterfaceModule look() {
        return ModuleManager.get().typed("interface", InterfaceModule.class);
    }

    // ─────────────────────────── отрисовка ───────────────────────────

    @Override
    protected void drawBackdrop(GuiGraphics graphics) {
        if (client().level != null) {
            float progress = transitionProgress();
            graphics.fill(0, 0, this.width, this.height, ColorUtil.fade(AuroraTheme.screenDim(), progress));
            return;
        }
        this.winter.draw(graphics, this.width, this.height, this.animated);
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = FrameClock.deltaSeconds();
        if (this.animated && client().level == null) {
            this.winter.update(delta);
        }
        drawHero(graphics);
        drawDock(graphics);
        this.messages.update(delta, mouseX, mouseY, this.width, this.height);
        this.messages.render(graphics, this.font);
    }

    /**
     * Левая информационная карточка в стиле матового жидкого стекла (Lunar / Feather):
     * полупрозрачная дымчатая подложка 28% + компактные стеклянные капсулы со сведениями.
     */
    private void drawHero(GuiGraphics graphics) {
        float alpha = transitionProgress();
        int radius = Math.max(11, AuroraTheme.radiusPx());
        DrawUtil.drawLiquidGlassPanel(graphics, this.heroX, this.heroY, this.heroWidth, this.heroHeight, radius, alpha);

        Font font = this.font;
        int pad = 11;
        int textX = this.heroX + pad;
        int textY = this.heroY + 14;
        int innerW = this.heroWidth - pad * 2;

        drawBrand(graphics, textX, textY, 1.55F);
        textY += Math.round(font.lineHeight * 1.55F) + 4;

        graphics.drawString(font, "Матовое жидкое стекло · 1.21.11", textX, textY,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
        textY += font.lineHeight + 8;

        graphics.fill(textX, textY, textX + innerW, textY + 1,
                ColorUtil.withAlpha(0xFFE4F4FF, Math.round(26 * alpha)));
        textY += 8;

        ModuleManager manager = ModuleManager.get();
        textY = drawInfoPill(graphics, textX, textY, innerW,
                "● Minecraft " + AetherGlassClient.minecraftVersion() + " · Fabric", alpha) + 5;
        textY = drawInfoPill(graphics, textX, textY, innerW,
                "✦ Клиент v" + AetherGlassClient.VERSION + " · Liquid Glass", alpha) + 5;
        textY = drawInfoPill(graphics, textX, textY, innerW,
                "◈ Модулей активно: " + manager.enabledCount() + " из " + manager.modules().size(), alpha) + 5;
        textY = drawInfoPill(graphics, textX, textY, innerW,
                "❖ 16 шейдеров: Блоки и Руки", alpha) + 5;
        drawInfoPill(graphics, textX, textY, innerW,
                "⚙ Конфиг: " + ModConfig.get().status(), alpha);

        // Нижняя стеклянная плашка с горячими клавишами
        int hintH = 20;
        int hintY = this.heroY + this.heroHeight - hintH - 10;
        DrawUtil.drawFrostedPill(graphics, textX, hintY, innerW, hintH, 6, alpha, 0.15F, 0.35F);
        String hint = "⌨ Меню: " + keyName() + " · Экран: "
                + KeybindManager.formatKey(KeybindManager.get().cleanScreenKeybind());
        int hw = font.width(hint);
        graphics.drawString(font, hint, textX + Math.max(4, (innerW - hw) / 2), hintY + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
    }

    /** Правый компактный стеклянный док навигации. */
    private void drawDock(GuiGraphics graphics) {
        float alpha = transitionProgress();
        int radius = Math.max(11, AuroraTheme.radiusPx());
        DrawUtil.drawLiquidGlassPanel(graphics, this.dockX, this.dockY, this.dockWidth, this.dockHeight, radius, alpha);

        String title = "✦ Навигация и Игра";
        int titleWidth = this.font.width(title);
        graphics.drawString(this.font, title,
                this.dockX + (this.dockWidth - titleWidth) / 2,
                this.dockY + 11,
                ColorUtil.fade(AuroraTheme.textSecondary(), alpha), false);
    }

    private int drawInfoPill(GuiGraphics graphics, int x, int y, int width, String text, float alpha) {
        int h = 19;
        DrawUtil.drawFrostedPill(graphics, x, y, width, h, 6, alpha * 0.92F, 0.0F, 0.15F);
        String clipped = this.font.plainSubstrByWidth(text, Math.max(20, width - 14));
        graphics.drawString(this.font, clipped, x + 7, y + 6,
                ColorUtil.fade(AuroraTheme.textPrimary(), alpha), false);
        return y + h;
    }

    private void drawBrand(GuiGraphics graphics, int x, int y, float baseScale) {
        Font font = this.font;
        float time = FrameClock.elapsedSeconds();
        float advance = 0.0F;
        for (int index = 0; index < BRAND_GLYPHS.length; index++) {
            String glyph = BRAND_GLYPHS[index];
            float wave = (float) Math.sin(time * 2.0D - index * 0.35D);
            float bounce = this.animated ? Math.max(0.0F, wave) : 0.0F;
            float lift = Easing.easeInOutQuad(bounce);

            float scale = baseScale * (1.0F + 0.04F * lift);
            float dy = -1.5F * lift;
            int color = ColorUtil.lerp(AuroraTheme.textPrimary(), AuroraTheme.accent(),
                    0.18F + 0.42F * lift);

            graphics.pose().pushMatrix();
            graphics.pose().translate(x + advance, y + dy);
            graphics.pose().scale(scale, scale);
            DrawUtil.drawGlowingText(graphics, font, glyph, 0, 0, color, AuroraTheme.accent());
            graphics.pose().popMatrix();

            advance += font.width(glyph) * baseScale;
        }
    }

    private static String[] split(String word) {
        String[] out = new String[word.length()];
        for (int index = 0; index < word.length(); index++) {
            out[index] = String.valueOf(word.charAt(index));
        }
        return out;
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
        Font font = this.font;
        String left = "AetherGlass " + AetherGlassClient.VERSION + " · Lunar / Feather Frosted Liquid Glass";
        String right = "Меню модулей: " + keyName() + " · Чистый экран: "
                + KeybindManager.formatKey(KeybindManager.get().cleanScreenKeybind());
        int y = this.height - font.lineHeight - 6;
        graphics.drawString(font, left, MARGIN, y, AuroraTheme.textMuted(), false);
        graphics.drawString(font, right, this.width - MARGIN - font.width(right), y,
                AuroraTheme.textSecondary(), false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            if (client().level != null) {
                beginSmoothClose();
            } else {
                client().setScreen(new AuroraInlineMenuScreen(this, ModuleManager.get().module("blocks")));
            }
            return true;
        }
        return super.keyPressed(event);
    }

    private void updateMessageZones() {
        int topWidth = 4 * 92;
        this.messages.setZones(List.of(
                new AtmosphericMessages.Zone(0, 0, 260, TITLE_HEIGHT + MARGIN),
                new AtmosphericMessages.Zone(this.width - MARGIN - topWidth, 0,
                        topWidth + MARGIN, TITLE_HEIGHT + MARGIN),
                new AtmosphericMessages.Zone(this.heroX - 8, this.heroY - 12, this.heroWidth + 16, this.heroHeight + 34),
                new AtmosphericMessages.Zone(this.dockX - 8, this.dockY - 12, this.dockWidth + 16, this.dockHeight + 34),
                new AtmosphericMessages.Zone(0, this.height - 22, this.width, 22)));
    }

    private static String keyName() {
        try {
            return AetherGlassClient.menuKey().getTranslatedKeyMessage().getString();
        } catch (Throwable throwable) {
            return "Right Shift";
        }
    }
}
