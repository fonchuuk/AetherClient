package com.aetherglass.client.gui.screen;

import com.aetherglass.client.account.AccountManager;
import com.aetherglass.client.gui.menu.AtmosphericMessages;
import com.aetherglass.client.gui.menu.WinterNight;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.settings.ColorSetting;
import com.aetherglass.client.modules.settings.Setting;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Главное меню AetherGlass: аккуратный прямоугольник со скруглёнными углами и тонкой рамкой
 * поверх 3D-панорамы мира Minecraft.
 * <ul>
 *   <li>Слева внутри окна — кнопки игры («Одиночная игра», «Сетевая игра», «Настройки MC», «Выход из игры»);</li>
 *   <li>Справа внутри окна — полноценная смена ников (поле ввода нового ника, кнопка «Войти», «Случайный ник» и список сохранённых аккаунтов);</li>
 *   <li>Настройки модулей (ClickGUI) вынесены отдельно на клавишу {@code Right Shift}.</li>
 * </ul>
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
    private final AtmosphericMessages atmosphere = new AtmosphericMessages();
    private final List<HitBox> hitBoxes = new ArrayList<>();

    private String nickInput = "";
    private boolean nickInputFocused;
    private float accountScroll;

    public AuroraMenuScreen() {
        this(Tab.HOME);
    }

    public AuroraMenuScreen(Tab initialTab) {
        super(Component.literal("AetherGlass"), Component.literal("Matte Black · Neon Purple"));
    }

    @Override
    protected void build() {
        this.winterNight.resize(this.width, this.height);
        int boxW = 304;
        int boxH = 162;
        int boxX = (this.width - boxW) / 2;
        int boxY = (this.height - boxH) / 2;
        this.atmosphere.setZones(List.of(new AtmosphericMessages.Zone(boxX - 18, boxY - 18, boxW + 36, boxH + 36)));
    }

    @Override
    protected void drawBackdrop(GuiGraphics graphics) {
        float delta = FrameClock.deltaSeconds();
        try {
            this.renderPanorama(graphics, delta);
        } catch (Throwable ignored) {
        }
        this.winterNight.update(delta);
        this.winterNight.draw(graphics, this.width, this.height, this.animated);
    }

    @Override
    protected void drawHeading(GuiGraphics graphics) {
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = FrameClock.deltaSeconds();
        float progress = transitionProgress();
        this.hitBoxes.clear();

        this.atmosphere.update(delta, mouseX, mouseY, this.width, this.height);
        this.atmosphere.render(graphics, this.font);

        AccountManager am = AccountManager.get();
        String activeNick = am.currentUsername();
        List<String> savedAccounts = am.accounts();

        int boxW = 304;
        int boxH = 162;
        int boxX = (this.width - boxW) / 2;
        int slideY = Math.round((1.0F - progress) * 8.0F);
        int boxY = (this.height - boxH) / 2 + slideY;

        // Строгий прямоугольник со скруглёнными углами (radius = 7px) и тонкой рамкой
        DrawUtil.drawLiquidGlassPanel(graphics, boxX, boxY, boxW, boxH, 7, progress);

        // Шапка главного меню: бренд слева + текущий активный ник справа
        DrawUtil.drawSliderKnob(graphics, boxX + 10, boxY + 9, 4, ColorUtil.fade(AuroraTheme.accent(), progress));
        DrawUtil.drawModernBold(graphics, this.font, "AETHERGLASS", boxX + 17, boxY + 8,
                ColorUtil.fade(0xFFFFFFFF, progress));
        int titleW = DrawUtil.modernBoldWidth(this.font, "AETHERGLASS");
        DrawUtil.drawModernSmall(graphics, this.font, "v1.0.8", boxX + 20 + titleW, boxY + 9,
                ColorUtil.fade(AuroraTheme.accent(), progress));

        String activeLabel = "Активен: " + activeNick;
        int activeW = DrawUtil.modernSmallWidth(this.font, activeLabel);
        DrawUtil.drawModernSmall(graphics, this.font, activeLabel, boxX + boxW - activeW - 10, boxY + 9,
                ColorUtil.fade(AuroraTheme.accent(), progress));

        // Тонкая 1px разделительная линия под шапкой
        graphics.fill(boxX + 8, boxY + 21, boxX + boxW - 8, boxY + 22,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(95 * progress)));

        // ── Левая половина прямоугольника: Кнопки игры ──
        int leftX = boxX + 10;
        int leftW = 128;
        int contentTop = boxY + 28;

        DrawUtil.drawModernBold(graphics, this.font, "ИГРА", leftX + 2, contentTop,
                ColorUtil.fade(0xFFB4BECE, progress));

        int btnY = contentTop + 12;
        int btnH = 21;
        int btnGap = 6;

        renderMenuButton(graphics, leftX, btnY, leftW, btnH, "Одиночная игра", true, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SelectWorldScreen(this));
                    }
                });
        btnY += btnH + btnGap;

        renderMenuButton(graphics, leftX, btnY, leftW, btnH, "Сетевая игра", false, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new JoinMultiplayerScreen(this));
                    }
                });
        btnY += btnH + btnGap;

        renderMenuButton(graphics, leftX, btnY, leftW, btnH, "Настройки MC", false, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
                    }
                });
        btnY += btnH + btnGap;

        renderMenuButton(graphics, leftX, btnY, leftW, btnH, "Выход из игры", false, true, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.stop();
                    }
                });

        // Вертикальный 1px разделитель между кнопками игры и сменой ников
        int divX = leftX + leftW + 9;
        graphics.fill(divX, contentTop, divX + 1, boxY + boxH - 10,
                ColorUtil.withAlpha(0xFFFFFFFF, Math.round(20 * progress)));

        // ── Правая половина прямоугольника: СМЕНА НИКА ──
        int rightX = divX + 9;
        int rightW = boxX + boxW - 10 - rightX;

        DrawUtil.drawModernBold(graphics, this.font, "СМЕНА НИКА", rightX + 2, contentTop,
                ColorUtil.fade(0xFFB4BECE, progress));
        String cntText = Integer.toString(savedAccounts.size());
        int cntW = DrawUtil.modernSmallWidth(this.font, cntText);
        DrawUtil.drawModernSmall(graphics, this.font, cntText, rightX + rightW - cntW - 2, contentTop + 1,
                ColorUtil.fade(AuroraTheme.accent(), progress));

        // 1. Поле ввода ника + кнопка «Войти»
        int inputY = contentTop + 12;
        int inputH = 18;
        int applyBtnW = 34;
        int fieldW = rightW - applyBtnW - 4;
        boolean fieldHov = inside(mouseX, mouseY, rightX, inputY, fieldW, inputH);
        DrawUtil.drawMatteNeonCard(graphics, rightX, inputY, fieldW, inputH, 5, progress,
                this.nickInputFocused ? 1.0F : (fieldHov ? 0.55F : 0.0F), AuroraTheme.accent());
        String shownNick = this.nickInput.isEmpty()
                ? (this.nickInputFocused ? "Введите ник...|" : "Введите ник...")
                : (this.nickInput + (this.nickInputFocused ? "|" : ""));
        int nickCol = (this.nickInput.isEmpty() && !this.nickInputFocused) ? 0xFF727C90 : 0xFFFFFFFF;
        DrawUtil.drawModernSmall(graphics, this.font,
                trimStyle(shownNick, fieldW - 10, DrawUtil.STYLE_MODERN_SMALL),
                rightX + 5, inputY + 5, ColorUtil.fade(nickCol, progress));
        this.hitBoxes.add(new HitBox(rightX, inputY, fieldW, inputH,
                () -> this.nickInputFocused = true,
                () -> this.nickInput = ""));

        int applyX = rightX + fieldW + 4;
        boolean applyHov = inside(mouseX, mouseY, applyX, inputY, applyBtnW, inputH);
        DrawUtil.drawMatteNeonCard(graphics, applyX, inputY, applyBtnW, inputH, 5, progress,
                applyHov ? 1.0F : 0.70F, AuroraTheme.accent());
        int applyTw = DrawUtil.modernSmallWidth(this.font, "Войти");
        DrawUtil.drawModernSmall(graphics, this.font, "Войти", applyX + (applyBtnW - applyTw) / 2, inputY + 5,
                ColorUtil.fade(0xFFFFFFFF, progress));
        this.hitBoxes.add(new HitBox(applyX, inputY, applyBtnW, inputH, () -> {
            if (!this.nickInput.isBlank() && am.switchAccount(this.nickInput)) {
                this.nickInput = "";
                this.nickInputFocused = false;
            }
        }, null));

        // 2. Кнопка «Случайный ник»
        int rndY = inputY + inputH + 4;
        int rndH = 17;
        boolean rndHov = inside(mouseX, mouseY, rightX, rndY, rightW, rndH);
        DrawUtil.drawMatteNeonCard(graphics, rightX, rndY, rightW, rndH, 5, progress,
                rndHov ? 0.90F : 0.25F, AuroraTheme.accent());
        int rndTw = DrawUtil.modernSmallWidth(this.font, "Случайный ник (в 1 клик)");
        DrawUtil.drawModernSmall(graphics, this.font, "Случайный ник (в 1 клик)",
                rightX + (rightW - rndTw) / 2, rndY + 4, ColorUtil.fade(0xFFE8E4F6, progress));
        this.hitBoxes.add(new HitBox(rightX, rndY, rightW, rndH, am::switchRandomAccount, null));

        // 3. Список сохранённых ников (прямоугольники с тонкой рамкой)
        int listTop = rndY + rndH + 5;
        int listBottom = boxY + boxH - 8;
        int rowH = 17;
        int rowGap = 3;
        int totalListH = savedAccounts.size() * (rowH + rowGap);
        float maxScroll = Math.max(0.0F, totalListH - (listBottom - listTop));
        this.accountScroll = Math.max(0.0F, Math.min(maxScroll, this.accountScroll));

        graphics.enableScissor(rightX - 1, listTop - 1, rightX + rightW + 1, listBottom + 1);
        int curAccY = listTop - Math.round(this.accountScroll);
        for (String acc : savedAccounts) {
            boolean isCurrent = acc.equalsIgnoreCase(activeNick);
            if (curAccY + rowH >= listTop && curAccY <= listBottom) {
                boolean rowHov = inside(mouseX, mouseY, rightX, curAccY, rightW, rowH)
                        && mouseY >= listTop && mouseY <= listBottom;
                DrawUtil.drawMatteNeonCard(graphics, rightX, curAccY, rightW, rowH, 4, progress,
                        isCurrent ? 1.0F : (rowHov ? 0.65F : 0.0F), AuroraTheme.accent());
                DrawUtil.drawSliderKnob(graphics, rightX + 5, curAccY + 6, 3,
                        ColorUtil.fade(isCurrent ? AuroraTheme.accent() : 0xFF424A5C, progress));
                DrawUtil.drawModernSmall(graphics, this.font,
                        trimStyle(acc, rightW - 26, DrawUtil.STYLE_MODERN_SMALL),
                        rightX + 11, curAccY + 4, ColorUtil.fade(0xFFFFFFFF, progress));

                int delX = rightX + rightW - 13;
                int delY = curAccY + 3;
                DrawUtil.drawModernSmall(graphics, this.font, "×", delX + 3, delY + 1,
                        ColorUtil.fade(0xFF7A8396, progress));

                if (mouseY >= listTop && mouseY <= listBottom) {
                    this.hitBoxes.add(new HitBox(rightX, curAccY, rightW - 15, rowH,
                            () -> am.switchAccount(acc), null));
                    this.hitBoxes.add(new HitBox(delX, delY, 11, 11,
                            () -> am.removeAccount(acc), null));
                }
            }
            curAccY += rowH + rowGap;
        }
        graphics.disableScissor();

        // Подсказка внизу под прямоугольником меню
        String hint = "RSHIFT — открыть ClickGUI (Визуал · Интерфейс · HUD)";
        int hw = DrawUtil.modernSmallWidth(this.font, hint);
        DrawUtil.drawModernSmall(graphics, this.font, hint, (this.width - hw) / 2, boxY + boxH + 6,
                ColorUtil.fade(0xFF9690AA, progress * 0.90F));

        NotificationManager.get().render(graphics, this.width, this.height);
    }

    private void renderMenuButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                                  boolean primary, boolean danger, int mouseX, int mouseY,
                                  float alpha, Runnable action) {
        boolean hov = inside(mouseX, mouseY, x, y, w, h);
        int neonCol = danger ? AuroraTheme.danger() : AuroraTheme.accent();
        float glow = primary ? (hov ? 1.0F : 0.90F) : (hov ? 0.85F : 0.0F);
        DrawUtil.drawMatteNeonCard(graphics, x, y, w, h, 5, alpha, glow, neonCol);
        int tw = DrawUtil.modernWidth(this.font, label);
        DrawUtil.drawModern(graphics, this.font, label, x + (w - tw) / 2, y + 6,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        this.hitBoxes.add(new HitBox(x, y, w, h, action, null));
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
        this.accountScroll = Math.max(0.0F, this.accountScroll - (float) scrollY * 14.0F);
        return true;
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
