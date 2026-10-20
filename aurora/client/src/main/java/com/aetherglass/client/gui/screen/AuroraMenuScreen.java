package com.aetherglass.client.gui.screen;

import com.aetherglass.client.account.AccountManager;
import com.aetherglass.client.gui.menu.WinterNight;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню AetherGlass в стиле панорамного горного пейзажа с тремя вертикальными
 * иллюстрированными карточками («Одиночная игра», «Сетевая игра», «Аккаунты»),
 * плавными hover-анимациями, параллаксом и шифр-эффектом проявления текста ({@code *!&#^!@^&#!@#(&!}).
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

    private static final Identifier TEX_MOUNTAIN_BG =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/menu_backdrop_b0.png");
    private static final int BG_TEX_W = 1376;
    private static final int BG_TEX_H = 768;

    private static final Identifier TEX_CARD_SINGLE =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/card_single.png");
    private static final Identifier TEX_CARD_MULTI =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/card_multi.png");
    private static final Identifier TEX_CARD_ACCOUNTS =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/gui/card_accounts.png");
    private static final int CARD_TEX_W = 256;
    private static final int CARD_TEX_H = 352;

    /** Вкладки быстрого перехода (сохранены для совместимости API). */
    public enum Tab {
        HOME("Главная"),
        FUNCTIONS("Модули"),
        SHADERS("16 Шейдеров"),
        SETTINGS("Оформление");

        private final String title;

        Tab(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    private record HitBox(int x, int y, int w, int h, Runnable onLeftClick, Runnable onRightClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final WinterNight winterNight = new WinterNight();
    private final List<HitBox> hitBoxes = new ArrayList<>();

    private final float[] cardHover = new float[3];
    private final float[] pillHover = new float[3];
    private float topBadgeHover;
    private float parallaxX;
    private float parallaxY;

    private boolean accountModalOpen;
    private float accountModalAnim;
    private String nickInput = "";
    private boolean nickInputFocused;
    private float accountScroll;

    public AuroraMenuScreen() {
        this(Tab.HOME);
    }

    public AuroraMenuScreen(Tab initialTab) {
        super(Component.literal("AetherGlass"), Component.literal("Scenic Mountain UI"));
    }

    @Override
    protected void build() {
        this.winterNight.resize(this.width, this.height);
    }

    private void openAccountModal() {
        this.accountModalOpen = true;
        this.nickInputFocused = false;
        DrawUtil.triggerTextScramble(0.48F);
    }

    private void closeAccountModal() {
        this.accountModalOpen = false;
        this.nickInputFocused = false;
    }

    @Override
    protected void drawBackdrop(GuiGraphics graphics) {
        float delta = FrameClock.deltaSeconds();
        float time = FrameClock.elapsedSeconds();

        float scale = Math.max((float) this.width / BG_TEX_W, (float) this.height / BG_TEX_H) * 1.035F;
        float breathe = 1.0F + 0.006F * (float) Math.sin(time * 0.45D);
        int drawW = Math.round(BG_TEX_W * scale * breathe);
        int drawH = Math.round(BG_TEX_H * scale * breathe);
        int drawX = (this.width - drawW) / 2 + Math.round(this.parallaxX);
        int drawY = (this.height - drawH) / 2 + Math.round(this.parallaxY);

        graphics.blit(RenderPipelines.GUI_TEXTURED, TEX_MOUNTAIN_BG,
                drawX, drawY, 0.0F, 0.0F,
                drawW, drawH, BG_TEX_W, BG_TEX_H, BG_TEX_W, BG_TEX_H, 0xFFFFFFFF);

        // Мягкое кинематографическое виньетирование сверху и снизу
        graphics.fillGradient(0, 0, this.width, Math.max(48, this.height / 4),
                0x6404070D, 0x0004070D);
        graphics.fillGradient(0, this.height - Math.max(64, this.height / 3), this.width, this.height,
                0x0004070D, 0x8204060B);

        this.winterNight.update(delta);
        this.winterNight.draw(graphics, this.width, this.height, this.animated);
    }

    @Override
    protected void drawHeading(GuiGraphics graphics) {
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = Math.min(0.1F, FrameClock.deltaSeconds());
        float progress = transitionProgress();
        this.hitBoxes.clear();

        // Плавный параллакс фона от движения мыши
        if (this.width > 0 && this.height > 0) {
            float targetPx = ((float) mouseX / this.width - 0.5F) * -10.0F;
            float targetPy = ((float) mouseY / this.height - 0.5F) * -6.0F;
            float lerp = Math.min(1.0F, delta * 5.5F);
            this.parallaxX += (targetPx - this.parallaxX) * lerp;
            this.parallaxY += (targetPy - this.parallaxY) * lerp;
        }

        // Анимация модального окна аккаунтов
        float modalTarget = this.accountModalOpen ? 1.0F : 0.0F;
        this.accountModalAnim += (modalTarget - this.accountModalAnim) * Math.min(1.0F, delta * 12.0F);
        boolean blockMainHover = this.accountModalOpen;

        AccountManager am = AccountManager.get();
        String activeNick = am.currentUsername();
        List<String> savedAccounts = am.accounts();

        // ── 1. Верхняя панель: логотип слева + плашка текущего аккаунта справа ──
        renderTopBar(graphics, activeNick, mouseX, mouseY, delta, progress, blockMainHover);

        // ── 2. Расчёт геометрии 3 центральных карточек ──
        int sideW = Math.max(96, Math.min(148, Math.round(this.width * 0.156F)));
        int sideH = Math.max(128, Math.min(196, Math.round(this.height * 0.370F)));
        int centerW = Math.round(sideW * 1.068F);
        int centerH = Math.round(sideH * 1.065F);
        int gap = Math.max(7, Math.min(12, Math.round(this.width * 0.011F)));

        int totalW = sideW * 2 + centerW + gap * 2;
        int startX = (this.width - totalW) / 2;
        int centerY = Math.round(this.height * 0.525F) - centerH / 2;
        int sideY = centerY + (centerH - sideH) / 2;

        // ── 3. Центральный заголовок над карточками («С возвращением, <ник>») ──
        int headerShiftY = Math.round((1.0F - progress) * 8.0F);
        String welcomeTitle = "С возвращением, " + activeNick;
        int welcomeW = DrawUtil.modernBoldWidth(this.font, welcomeTitle);
        int welcomeX = (this.width - welcomeW) / 2;
        int welcomeY = Math.max(26, centerY - 34) + headerShiftY;
        DrawUtil.drawModernBold(graphics, this.font, welcomeTitle, welcomeX + 1, welcomeY + 1,
                ColorUtil.withAlpha(0xFF04070E, Math.round(170 * progress)));
        DrawUtil.drawModernBold(graphics, this.font, welcomeTitle, welcomeX, welcomeY,
                ColorUtil.fade(0xFFFFFFFF, progress));

        String subTitle = "Выбери, с чего начать";
        int subW = DrawUtil.modernSmallWidth(this.font, subTitle);
        int subX = (this.width - subW) / 2;
        int subY = welcomeY + 12;
        DrawUtil.drawModernSmall(graphics, this.font, subTitle, subX + 1, subY + 1,
                ColorUtil.withAlpha(0xFF04070E, Math.round(140 * progress)));
        DrawUtil.drawModernSmall(graphics, this.font, subTitle, subX, subY,
                ColorUtil.fade(0xFFAEC0D6, progress));

        // ── 4. Три вертикальные иллюстрированные карточки ──
        int c0X = startX;
        int c1X = startX + sideW + gap;
        int c2X = c1X + centerW + gap;

        renderModeCard(graphics, 0, c0X, sideY, sideW, sideH, TEX_CARD_SINGLE,
                "Одиночная игра", "Миры и выживание", false,
                mouseX, mouseY, delta, progress, blockMainHover, () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SelectWorldScreen(this));
                    }
                });

        renderModeCard(graphics, 1, c1X, centerY, centerW, centerH, TEX_CARD_MULTI,
                "Сетевая игра", "Серверы и друзья", true,
                mouseX, mouseY, delta, progress, blockMainHover, () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new JoinMultiplayerScreen(this));
                    }
                });

        String accountsSubtitle = activeNick + " · " + savedAccounts.size() + " сохранено";
        renderModeCard(graphics, 2, c2X, sideY, sideW, sideH, TEX_CARD_ACCOUNTS,
                "Аккаунты", accountsSubtitle, false,
                mouseX, mouseY, delta, progress, blockMainHover, this::openAccountModal);

        // ── 5. Нижний ряд из 3 кнопок-капсул («Настройки», «Моды и конфиги», «Выход») ──
        int pillsY = Math.min(this.height - 26, centerY + centerH + 18) + Math.round((1.0F - progress) * 6.0F);
        renderBottomPills(graphics, pillsY, mouseX, mouseY, delta, progress, blockMainHover);

        // ── 6. Модальное окно быстрой смены аккаунтов ──
        if (this.accountModalAnim > 0.01F) {
            renderAccountModal(graphics, am, activeNick, savedAccounts, mouseX, mouseY, this.accountModalAnim);
        }

        NotificationManager.get().render(graphics, this.width, this.height);
    }

    private void renderTopBar(GuiGraphics graphics, String activeNick, int mouseX, int mouseY,
                              float delta, float progress, boolean blockHover) {
        int topY = 14 + Math.round((1.0F - progress) * -5.0F);

        // Слева: значок + название бренда
        int logoX = 18;
        DrawUtil.fillRoundedPixelExact(graphics, logoX, topY, 16, 16, 4,
                ColorUtil.withAlpha(0xFF101622, Math.round(210 * progress)));
        DrawUtil.drawThinRoundedOutline(graphics, logoX, topY, 16, 16, 4,
                ColorUtil.withAlpha(0xFF5CA8FF, Math.round(145 * progress)), 1);
        // Мини-иконка кристалла/гор внутри значка
        int iconCol = ColorUtil.fade(0xFF72BCFF, progress);
        graphics.fill(logoX + 7, topY + 4, logoX + 9, topY + 6, iconCol);
        graphics.fill(logoX + 5, topY + 6, logoX + 11, topY + 9, iconCol);
        graphics.fill(logoX + 4, topY + 9, logoX + 12, topY + 12, ColorUtil.fade(0xFFFFFFFF, progress));

        DrawUtil.drawModernBold(graphics, this.font, "AetherGlass", logoX + 21, topY + 4,
                ColorUtil.fade(0xFFFFFFFF, progress));

        // Справа: плашка профиля [<аватар> <ник> ▾]
        int nickW = DrawUtil.modernSmallWidth(this.font, activeNick);
        int pillW = Math.max(58, nickW + 32);
        int pillH = 20;
        int pillX = this.width - pillW - 18;
        int pillY = topY - 1;

        boolean hov = !blockHover && inside(mouseX, mouseY, pillX, pillY, pillW, pillH);
        this.topBadgeHover += ((hov ? 1.0F : 0.0F) - this.topBadgeHover) * Math.min(1.0F, delta * 10.0F);

        int bgCol = ColorUtil.lerp(0xC6121721, 0xDF1B2434, this.topBadgeHover);
        int brdCol = ColorUtil.lerp(0x42FFFFFF, 0xCC70B8FF, this.topBadgeHover);
        DrawUtil.fillRoundedPixelExact(graphics, pillX, pillY, pillW, pillH, 6,
                ColorUtil.fade(bgCol, progress));
        DrawUtil.drawThinRoundedOutline(graphics, pillX, pillY, pillW, pillH, 6,
                ColorUtil.fade(brdCol, progress), 1);

        // Пиксельная иконка головы игрока (12x12)
        int avX = pillX + 5;
        int avY = pillY + 4;
        graphics.fill(avX, avY, avX + 12, avY + 12, ColorUtil.fade(0xFFB57B52, progress));
        graphics.fill(avX, avY, avX + 12, avY + 3, ColorUtil.fade(0xFF422818, progress));
        graphics.fill(avX, avY + 3, avX + 2, avY + 5, ColorUtil.fade(0xFF422818, progress));
        graphics.fill(avX + 10, avY + 3, avX + 12, avY + 5, ColorUtil.fade(0xFF422818, progress));
        graphics.fill(avX + 2, avY + 5, avX + 4, avY + 7, ColorUtil.fade(0xFF2B3A67, progress));
        graphics.fill(avX + 8, avY + 5, avX + 10, avY + 7, ColorUtil.fade(0xFF2B3A67, progress));
        graphics.fill(avX + 4, avY + 8, avX + 8, avY + 10, ColorUtil.fade(0xFF7D4930, progress));

        DrawUtil.drawModernSmall(graphics, this.font, activeNick, avX + 16, pillY + 6,
                ColorUtil.fade(0xFFFFFFFF, progress));
        DrawUtil.drawModernSmall(graphics, this.font, "▾", pillX + pillW - 10, pillY + 6,
                ColorUtil.fade(0xFF9EB0C8, progress));

        if (!blockHover) {
            this.hitBoxes.add(new HitBox(pillX, pillY, pillW, pillH, this::openAccountModal, null));
        }
    }

    private void renderModeCard(GuiGraphics graphics, int index, int x, int baseY, int w, int h,
                                Identifier texture, String title, String subtitle, boolean heroCenter,
                                int mouseX, int mouseY, float delta, float progress, boolean blockHover,
                                Runnable action) {
        float cardAppear = Easing.easeOutCubic(Math.max(0.0F, Math.min(1.0F, (progress - index * 0.06F) / 0.88F)));
        boolean hov = !blockHover && inside(mouseX, mouseY, x, baseY, w, h);
        this.cardHover[index] += ((hov ? 1.0F : 0.0F) - this.cardHover[index]) * Math.min(1.0F, delta * 10.0F);
        float hv = this.cardHover[index];

        int entranceOffset = Math.round((1.0F - cardAppear) * 14.0F);
        int hoverLift = Math.round(hv * 4.0F);
        int y = baseY + entranceOffset - hoverLift;

        // Внешняя мягкая тень и деликатное свечение при наведении
        DrawUtil.drawSoftShadow(graphics, x, y + 2, w, h, 9, cardAppear * (0.75F + hv * 0.25F));
        if (hv > 0.02F || heroCenter) {
            int glowCol = heroCenter ? 0xFF4CA8FF : AuroraTheme.accent();
            float glowStrength = heroCenter ? (0.28F + hv * 0.45F) : (hv * 0.42F);
            DrawUtil.drawGlow(graphics, x, y, w, h, 9, glowCol, glowStrength * cardAppear);
        }

        // Иллюстрация карточки (256x352 со скруглёнными альфа-углами)
        int brightness = Math.round(242 + hv * 13);
        int tint = ColorUtil.argb(Math.round(255 * cardAppear), brightness, brightness, brightness);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0.0F, 0.0F,
                w, h, CARD_TEX_W, CARD_TEX_H, CARD_TEX_W, CARD_TEX_H, tint);

        // Тонкая 1px скруглённая рамка вокруг карточки + светящийся верхний блик на центральной карточке
        int baseBorder = heroCenter ? 0x78B8DCFF : 0x36FFFFFF;
        int hoverBorder = heroCenter ? 0xFFD4ECFF : 0xAA88C4FF;
        int borderCol = ColorUtil.fade(ColorUtil.lerp(baseBorder, hoverBorder, hv), cardAppear);
        DrawUtil.drawThinRoundedOutline(graphics, x, y, w, h, 9, borderCol, 1);

        if (heroCenter || hv > 0.05F) {
            float topHighlightAlpha = (heroCenter ? 0.65F : 0.0F) + hv * 0.35F;
            int hlCol = ColorUtil.withAlpha(0xFFEAF6FF, Math.round(160 * Math.min(1.0F, topHighlightAlpha) * cardAppear));
            graphics.fill(x + 9, y, x + w - 9, y + 1, hlCol);
        }

        // Значок категории и подписи в нижней части карточки
        int padX = heroCenter ? 12 : 11;
        int badgeSize = heroCenter ? 18 : 16;
        int badgeY = heroCenter ? (y + h - 66) : (y + h - 46);
        int titleY = heroCenter ? (y + h - 44) : (y + h - 25);
        int subY = heroCenter ? (y + h - 33) : (y + h - 14);

        int badgeFill = heroCenter
                ? ColorUtil.lerp(0xD8183D78, 0xEE20529E, hv)
                : ColorUtil.lerp(0xCC161C26, 0xE0202938, hv);
        int badgeBorder = heroCenter
                ? ColorUtil.lerp(0x994CA8FF, 0xFF82C4FF, hv)
                : ColorUtil.lerp(0x40FFFFFF, 0x88FFFFFF, hv);

        DrawUtil.fillRoundedPixelExact(graphics, x + padX, badgeY, badgeSize, badgeSize, 4,
                ColorUtil.fade(badgeFill, cardAppear));
        DrawUtil.drawThinRoundedOutline(graphics, x + padX, badgeY, badgeSize, badgeSize, 4,
                ColorUtil.fade(badgeBorder, cardAppear), 1);

        drawCardIcon(graphics, index, x + padX, badgeY, badgeSize, ColorUtil.fade(0xFFFFFFFF, cardAppear));

        DrawUtil.drawModernBold(graphics, this.font, title, x + padX, titleY,
                ColorUtil.fade(0xFFFFFFFF, cardAppear));
        DrawUtil.drawModernSmall(graphics, this.font,
                trimStyle(subtitle, w - padX * 2, DrawUtil.STYLE_MODERN_SMALL),
                x + padX, subY, ColorUtil.fade(0xFF96A4B8, cardAppear));

        // На центральной карточке «Сетевая игра» рисуем капсульную кнопку «Играть ->»
        if (heroCenter) {
            int btnW = 62;
            int btnH = 15;
            int btnX = x + padX;
            int btnY = y + h - 21;
            int btnFill = ColorUtil.lerp(0x68243246, 0xB830588C, hv);
            int btnBrd = ColorUtil.lerp(0x66FFFFFF, 0xFF88C8FF, hv);
            DrawUtil.fillRoundedPixelExact(graphics, btnX, btnY, btnW, btnH, 4,
                    ColorUtil.fade(btnFill, cardAppear));
            DrawUtil.drawThinRoundedOutline(graphics, btnX, btnY, btnW, btnH, 4,
                    ColorUtil.fade(btnBrd, cardAppear), 1);

            String playLabel = "Играть  →";
            int playW = DrawUtil.modernSmallWidth(this.font, playLabel);
            int arrowShift = Math.round(hv * 2.0F);
            DrawUtil.drawModernSmall(graphics, this.font, playLabel,
                    btnX + (btnW - playW) / 2 + arrowShift, btnY + 4,
                    ColorUtil.fade(0xFFFFFFFF, cardAppear));
        }

        if (!blockHover) {
            this.hitBoxes.add(new HitBox(x, y, w, h, action, null));
        }
    }

    private void drawCardIcon(GuiGraphics graphics, int index, int bx, int by, int size, int color) {
        int cx = bx + size / 2;
        int cy = by + size / 2;
        if (index == 0) {
            // Иконка куба / мира для «Одиночная игра»
            graphics.fill(cx - 3, cy - 4, cx + 3, cy - 3, color);
            graphics.fill(cx - 4, cy - 3, cx + 4, cy + 3, color);
            graphics.fill(cx - 3, cy + 3, cx + 3, cy + 4, color);
            int cut = ColorUtil.withAlpha(0xFF141A24, ColorUtil.alpha(color));
            graphics.fill(cx - 2, cy - 2, cx + 2, cy, cut);
            graphics.fill(cx - 2, cy + 1, cx, cy + 2, cut);
        } else if (index == 1) {
            // Иконка глобуса / сети для «Сетевая игра»
            graphics.fill(cx - 3, cy - 4, cx + 3, cy + 4, color);
            graphics.fill(cx - 4, cy - 3, cx + 4, cy + 3, color);
            int cut = ColorUtil.withAlpha(0xFF1A4280, ColorUtil.alpha(color));
            graphics.fill(cx - 3, cy - 2, cx - 1, cy, cut);
            graphics.fill(cx + 1, cy - 2, cx + 3, cy, cut);
            graphics.fill(cx - 3, cy + 1, cx - 1, cy + 3, cut);
            graphics.fill(cx + 1, cy + 1, cx + 3, cy + 3, cut);
        } else {
            // Иконка профиля / пользователя для «Аккаунты»
            graphics.fill(cx - 2, cy - 4, cx + 2, cy - 1, color);
            graphics.fill(cx - 4, cy + 1, cx + 4, cy + 4, color);
            graphics.fill(cx - 3, cy, cx + 3, cy + 1, color);
        }
    }

    private void renderBottomPills(GuiGraphics graphics, int y, int mouseX, int mouseY,
                                   float delta, float progress, boolean blockHover) {
        String[] labels = {"⚙  Настройки", "❖  Моды и конфиги", "⏻  Выход"};
        int[] widths = new int[3];
        int gap = 6;
        int pillH = 20;
        int totalW = 0;
        for (int i = 0; i < 3; i++) {
            widths[i] = DrawUtil.modernSmallWidth(this.font, labels[i]) + 22;
            totalW += widths[i];
        }
        totalW += gap * 2;

        int curX = (this.width - totalW) / 2;
        Runnable[] actions = {
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
                    }
                },
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new AuroraInlineMenuScreen(this));
                    }
                },
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.stop();
                    }
                }
        };

        for (int i = 0; i < 3; i++) {
            int w = widths[i];
            boolean hov = !blockHover && inside(mouseX, mouseY, curX, y, w, pillH);
            this.pillHover[i] += ((hov ? 1.0F : 0.0F) - this.pillHover[i]) * Math.min(1.0F, delta * 11.0F);
            float hv = this.pillHover[i];
            int drawY = y - Math.round(hv * 1.5F);

            boolean isExit = (i == 2);
            int fillCol = isExit
                    ? ColorUtil.lerp(0xD4522226, 0xEC782C32, hv)
                    : ColorUtil.lerp(0xCC131822, 0xE61D2636, hv);
            int brdCol = isExit
                    ? ColorUtil.lerp(0x88A8444A, 0xFFFF6E76, hv)
                    : ColorUtil.lerp(0x38FFFFFF, 0xAA78BCFF, hv);

            DrawUtil.fillRoundedPixelExact(graphics, curX, drawY, w, pillH, 6,
                    ColorUtil.fade(fillCol, progress));
            DrawUtil.drawThinRoundedOutline(graphics, curX, drawY, w, pillH, 6,
                    ColorUtil.fade(brdCol, progress), 1);

            int tw = DrawUtil.modernSmallWidth(this.font, labels[i]);
            int textCol = isExit ? ColorUtil.lerp(0xFFFFD6D8, 0xFFFFFFFF, hv) : 0xFFFFFFFF;
            DrawUtil.drawModernSmall(graphics, this.font, labels[i],
                    curX + (w - tw) / 2, drawY + 6, ColorUtil.fade(textCol, progress));

            if (!blockHover) {
                this.hitBoxes.add(new HitBox(curX, drawY, w, pillH, actions[i], null));
            }
            curX += w + gap;
        }
    }

    private void renderAccountModal(GuiGraphics graphics, AccountManager am, String activeNick,
                                    List<String> savedAccounts, int mouseX, int mouseY, float anim) {
        float eased = Easing.easeOutCubic(Math.max(0.0F, Math.min(1.0F, anim)));
        graphics.fill(0, 0, this.width, this.height,
                ColorUtil.withAlpha(0xFF04060B, Math.round(165 * eased)));

        int modalW = 224;
        int modalH = 166;
        int modalX = (this.width - modalW) / 2;
        int modalY = (this.height - modalH) / 2 + Math.round((1.0F - eased) * 10.0F);

        DrawUtil.drawSoftShadow(graphics, modalX, modalY, modalW, modalH, 8, eased);
        DrawUtil.drawLiquidGlassPanel(graphics, modalX, modalY, modalW, modalH, 8, eased);

        DrawUtil.drawModernBold(graphics, this.font, "АККАУНТЫ", modalX + 12, modalY + 10,
                ColorUtil.fade(0xFFFFFFFF, eased));
        String sub = "Активен: " + activeNick + " (" + savedAccounts.size() + ")";
        DrawUtil.drawModernSmall(graphics, this.font, sub, modalX + 12, modalY + 21,
                ColorUtil.fade(0xFF98A8C0, eased));

        // Кнопка закрытия модального окна [×]
        int closeX = modalX + modalW - 24;
        int closeY = modalY + 9;
        boolean closeHov = inside(mouseX, mouseY, closeX, closeY, 15, 15);
        DrawUtil.drawMatteNeonCard(graphics, closeX, closeY, 15, 15, 4, eased,
                closeHov ? 0.85F : 0.0F, AuroraTheme.danger());
        DrawUtil.drawModernSmall(graphics, this.font, "×", closeX + 5, closeY + 3,
                ColorUtil.fade(0xFFFFFFFF, eased));
        this.hitBoxes.add(new HitBox(closeX, closeY, 15, 15, this::closeAccountModal, null));

        // Поле ввода нового ника + кнопка «Войти»
        int padX = modalX + 12;
        int contentW = modalW - 24;
        int inputY = modalY + 34;
        int inputH = 18;
        int applyW = 42;
        int fieldW = contentW - applyW - 4;

        boolean fieldHov = inside(mouseX, mouseY, padX, inputY, fieldW, inputH);
        DrawUtil.drawMatteNeonCard(graphics, padX, inputY, fieldW, inputH, 5, eased,
                this.nickInputFocused ? 1.0F : (fieldHov ? 0.55F : 0.0F), AuroraTheme.accent());
        String shownNick = this.nickInput.isEmpty()
                ? (this.nickInputFocused ? "Введите ник...|" : "Введите ник...")
                : (this.nickInput + (this.nickInputFocused ? "|" : ""));
        int nickCol = (this.nickInput.isEmpty() && !this.nickInputFocused) ? 0xFF727E92 : 0xFFFFFFFF;
        DrawUtil.drawModernSmall(graphics, this.font,
                trimStyle(shownNick, fieldW - 10, DrawUtil.STYLE_MODERN_SMALL),
                padX + 6, inputY + 5, ColorUtil.fade(nickCol, eased));
        this.hitBoxes.add(new HitBox(padX, inputY, fieldW, inputH,
                () -> this.nickInputFocused = true,
                () -> this.nickInput = ""));

        int applyX = padX + fieldW + 4;
        boolean applyHov = inside(mouseX, mouseY, applyX, inputY, applyW, inputH);
        DrawUtil.drawMatteNeonCard(graphics, applyX, inputY, applyW, inputH, 5, eased,
                applyHov ? 1.0F : 0.72F, AuroraTheme.accent());
        int applyTw = DrawUtil.modernSmallWidth(this.font, "Войти");
        DrawUtil.drawModernSmall(graphics, this.font, "Войти",
                applyX + (applyW - applyTw) / 2, inputY + 5, ColorUtil.fade(0xFFFFFFFF, eased));
        this.hitBoxes.add(new HitBox(applyX, inputY, applyW, inputH, () -> {
            if (!this.nickInput.isBlank() && am.switchAccount(this.nickInput)) {
                this.nickInput = "";
                this.nickInputFocused = false;
            }
        }, null));

        // Кнопка «Случайный ник (в 1 клик)»
        int rndY = inputY + inputH + 5;
        int rndH = 17;
        boolean rndHov = inside(mouseX, mouseY, padX, rndY, contentW, rndH);
        DrawUtil.drawMatteNeonCard(graphics, padX, rndY, contentW, rndH, 5, eased,
                rndHov ? 0.90F : 0.28F, AuroraTheme.accent());
        String rndLabel = "Случайный ник (в 1 клик)";
        int rndTw = DrawUtil.modernSmallWidth(this.font, rndLabel);
        DrawUtil.drawModernSmall(graphics, this.font, rndLabel,
                padX + (contentW - rndTw) / 2, rndY + 4, ColorUtil.fade(0xFFE8E4F8, eased));
        this.hitBoxes.add(new HitBox(padX, rndY, contentW, rndH, am::switchRandomAccount, null));

        // Прокручиваемый список сохранённых аккаунтов
        int listTop = rndY + rndH + 6;
        int listBottom = modalY + modalH - 10;
        int rowH = 17;
        int rowGap = 3;
        int totalListH = savedAccounts.size() * (rowH + rowGap);
        float maxScroll = Math.max(0.0F, totalListH - (listBottom - listTop));
        this.accountScroll = Math.max(0.0F, Math.min(maxScroll, this.accountScroll));

        graphics.enableScissor(padX - 1, listTop - 1, padX + contentW + 1, listBottom + 1);
        int curY = listTop - Math.round(this.accountScroll);
        for (String acc : savedAccounts) {
            boolean isCurrent = acc.equalsIgnoreCase(activeNick);
            if (curY + rowH >= listTop && curY <= listBottom) {
                boolean rowHov = inside(mouseX, mouseY, padX, curY, contentW, rowH)
                        && mouseY >= listTop && mouseY <= listBottom;
                DrawUtil.drawMatteNeonCard(graphics, padX, curY, contentW, rowH, 4, eased,
                        isCurrent ? 1.0F : (rowHov ? 0.65F : 0.0F), AuroraTheme.accent());
                DrawUtil.drawSliderKnob(graphics, padX + 5, curY + 6, 3,
                        ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF465064, eased));
                DrawUtil.drawModernSmall(graphics, this.font,
                        trimStyle(acc, contentW - 28, DrawUtil.STYLE_MODERN_SMALL),
                        padX + 12, curY + 4, ColorUtil.fade(0xFFFFFFFF, eased));

                int delX = padX + contentW - 14;
                int delY = curY + 3;
                DrawUtil.drawModernSmall(graphics, this.font, "×", delX + 3, delY + 1,
                        ColorUtil.fade(0xFF828C9E, eased));

                if (mouseY >= listTop && mouseY <= listBottom) {
                    this.hitBoxes.add(new HitBox(padX, curY, contentW - 16, rowH,
                            () -> am.switchAccount(acc), null));
                    this.hitBoxes.add(new HitBox(delX, delY, 12, 12,
                            () -> am.removeAccount(acc), null));
                }
            }
            curY += rowH + rowGap;
        }
        graphics.disableScissor();

        // Клик вне модального окна закрывает его
        if (!inside(mouseX, mouseY, modalX, modalY, modalW, modalH)) {
            this.hitBoxes.add(0, new HitBox(0, 0, this.width, this.height, this::closeAccountModal, null));
        }
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        this.nickInputFocused = false;

        for (int i = this.hitBoxes.size() - 1; i >= 0; i--) {
            HitBox box = this.hitBoxes.get(i);
            if (box.contains(mx, my)) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && box.onLeftClick() != null) {
                    box.onLeftClick().run();
                    return true;
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && box.onRightClick() != null) {
                    box.onRightClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.accountModalOpen) {
            this.accountScroll = Math.max(0.0F, this.accountScroll - (float) scrollY * 14.0F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();

        if (this.nickInputFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                this.nickInputFocused = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                if (!this.nickInput.isBlank() && AccountManager.get().switchAccount(this.nickInput)) {
                    this.nickInput = "";
                    this.nickInputFocused = false;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE && !this.nickInput.isEmpty()) {
                this.nickInput = this.nickInput.substring(0, this.nickInput.length() - 1);
                return true;
            }
            return true;
        }

        if (this.accountModalOpen && key == GLFW.GLFW_KEY_ESCAPE) {
            closeAccountModal();
            return true;
        }

        if (key == GLFW.GLFW_KEY_F8) {
            if (this.accountModalOpen) {
                closeAccountModal();
            } else {
                openAccountModal();
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F12) {
            DrawUtil.triggerTextScramble(2.4F);
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_F9) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AuroraInlineMenuScreen(this));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F6 || key == GLFW.GLFW_KEY_F7) {
            if (this.minecraft != null) {
                AuroraInlineMenuScreen clickGui = new AuroraInlineMenuScreen(this);
                this.minecraft.setScreen(clickGui);
                clickGui.keyPressed(event);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F10) {
            com.aetherglass.client.render.hud.HudLayout.get().resetAll();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new HudEditorScreen(this));
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.nickInputFocused) {
            int cp = event.codepoint();
            if ((cp >= 'a' && cp <= 'z')
                    || (cp >= 'A' && cp <= 'Z')
                    || (cp >= '0' && cp <= '9')
                    || cp == '_') {
                if (this.nickInput.length() < 16) {
                    this.nickInput += Character.toString(cp);
                }
            }
            return true;
        }
        return super.charTyped(event);
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

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
