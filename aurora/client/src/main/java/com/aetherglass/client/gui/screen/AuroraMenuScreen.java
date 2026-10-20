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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Чистое современное главное меню AetherGlass (Matte Black + Neon Purple)
 * без настроек модулей на главном экране:
 * <ul>
 *   <li>Сзади видна настоящая 3D-панорама мира Minecraft с лёгкой фиолетовой дымкой;</li>
 *   <li>По центру — компактная карточка главного меню со шрифтом Montserrat («Одиночная игра», «Сетевая игра», «Настройки MC», «Выход» и быстрый переключатель ника);</li>
 *   <li>Настройки модулей (ClickGUI по столбикам) убраны с главного экрана и открываются по клавише {@code Right Shift}.</li>
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

    private record MenuButton(int x, int y, int w, int h, Runnable onClick) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final WinterNight winterNight = new WinterNight();
    private final AtmosphericMessages atmosphere = new AtmosphericMessages();
    private final List<MenuButton> buttons = new ArrayList<>();

    public AuroraMenuScreen() {
        this(Tab.HOME);
    }

    public AuroraMenuScreen(Tab initialTab) {
        super(Component.literal("AetherGlass"), Component.literal("Matte Black · Neon Purple"));
    }

    @Override
    protected void build() {
        this.winterNight.resize(this.width, this.height);
        int cardW = 164;
        int cardH = 152;
        int cardX = (this.width - cardW) / 2;
        int cardY = (this.height - cardH) / 2;
        this.atmosphere.setZones(List.of(new AtmosphericMessages.Zone(cardX - 18, cardY - 18, cardW + 36, cardH + 36)));
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
        this.buttons.clear();

        this.atmosphere.update(delta, mouseX, mouseY, this.width, this.height);
        this.atmosphere.render(graphics, this.font);

        int cardW = 164;
        int cardH = 150;
        int cardX = (this.width - cardW) / 2;
        int slideY = Math.round((1.0F - progress) * 8.0F);
        int cardY = (this.height - cardH) / 2 + slideY;

        // Компактная матово-чёрная карточка главного меню по центру экрана
        DrawUtil.drawLiquidGlassPanel(graphics, cardX, cardY, cardW, cardH, 9, progress);

        // Шапка бренда AETHERGLASS v1.0.8
        DrawUtil.drawSliderKnob(graphics, cardX + 12, cardY + 11, 5, ColorUtil.fade(AuroraTheme.accent(), progress));
        DrawUtil.drawModernBold(graphics, this.font, "AETHERGLASS", cardX + 20, cardY + 10,
                ColorUtil.fade(0xFFFFFFFF, progress));
        int titleW = DrawUtil.modernBoldWidth(this.font, "AETHERGLASS");
        DrawUtil.drawModernSmall(graphics, this.font, "v1.0.8", cardX + 24 + titleW, cardY + 11,
                ColorUtil.fade(AuroraTheme.accent(), progress));

        // Тонкая фиолетовая неоновая линия под заголовком
        graphics.fill(cardX + 10, cardY + 23, cardX + cardW - 10, cardY + 24,
                ColorUtil.withAlpha(AuroraTheme.accent(), Math.round(135 * progress)));

        // Компактная плашка текущего аккаунта (клик — смена ника)
        AccountManager am = AccountManager.get();
        String nick = am.currentUsername();
        int accX = cardX + 10;
        int accY = cardY + 29;
        int accW = cardW - 20;
        int accH = 16;
        boolean accHov = inside(mouseX, mouseY, accX, accY, accW, accH);
        DrawUtil.drawMatteNeonCard(graphics, accX, accY, accW, accH, 5, progress,
                accHov ? 0.75F : 0.25F, AuroraTheme.accent());
        DrawUtil.drawSliderKnob(graphics, accX + 5, accY + 6, 4, ColorUtil.fade(AuroraTheme.accent(), progress));
        DrawUtil.drawModernSmall(graphics, this.font, "Ник: " + nick, accX + 12, accY + 4,
                ColorUtil.fade(0xFFEAE6F8, progress));
        this.buttons.add(new MenuButton(accX, accY, accW, accH, am::switchRandomAccount));

        // Компактные современные кнопки главного меню (строго без настроек модулей)
        int btnX = cardX + 10;
        int btnW = cardW - 20;
        int btnH = 19;
        int gap = 5;
        int curY = accY + accH + 7;

        renderMenuButton(graphics, btnX, curY, btnW, btnH, "Одиночная игра", true, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new SelectWorldScreen(this));
                    }
                });
        curY += btnH + gap;

        renderMenuButton(graphics, btnX, curY, btnW, btnH, "Сетевая игра", false, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new JoinMultiplayerScreen(this));
                    }
                });
        curY += btnH + gap;

        renderMenuButton(graphics, btnX, curY, btnW, btnH, "Настройки MC", false, false, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options));
                    }
                });
        curY += btnH + gap;

        renderMenuButton(graphics, btnX, curY, btnW, btnH, "Выход из игры", false, true, mouseX, mouseY, progress,
                () -> {
                    if (this.minecraft != null) {
                        this.minecraft.stop();
                    }
                });

        // Ненавязчивая подсказка в самом низу экрана
        String hint = "RSHIFT — открыть ClickGUI";
        int hw = DrawUtil.modernSmallWidth(this.font, hint);
        DrawUtil.drawModernSmall(graphics, this.font, hint, (this.width - hw) / 2, cardY + cardH + 6,
                ColorUtil.fade(0xFF8A849E, progress * 0.85F));

        NotificationManager.get().render(graphics, this.width, this.height);
    }

    private void renderMenuButton(GuiGraphics graphics, int x, int y, int w, int h, String label,
                                  boolean primary, boolean danger, int mouseX, int mouseY,
                                  float alpha, Runnable action) {
        boolean hov = inside(mouseX, mouseY, x, y, w, h);
        int neonCol = danger ? AuroraTheme.danger() : AuroraTheme.accent();
        float glow = primary ? (hov ? 1.0F : 0.85F) : (hov ? 0.85F : 0.0F);
        DrawUtil.drawMatteNeonCard(graphics, x, y, w, h, 5, alpha, glow, neonCol);
        int tw = DrawUtil.modernWidth(this.font, label);
        DrawUtil.drawModern(graphics, this.font, label, x + (w - tw) / 2, y + 5,
                ColorUtil.fade(0xFFFFFFFF, alpha));
        this.buttons.add(new MenuButton(x, y, w, h, action));
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double mx = event.x();
            double my = event.y();
            for (MenuButton btn : this.buttons) {
                if (btn.contains(mx, my) && btn.onClick() != null) {
                    btn.onClick().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT || key == GLFW.GLFW_KEY_F9) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AuroraInlineMenuScreen(this));
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F6) {
            if (this.minecraft != null) {
                AuroraInlineMenuScreen clickGui = new AuroraInlineMenuScreen(this);
                Module blocks = ModuleManager.get().module("blocks");
                clickGui.expandOnly(blocks);
                if (blocks != null) {
                    blocks.setEnabled(true);
                }
                Module crosshair = ModuleManager.get().module("crosshair");
                if (crosshair != null) {
                    crosshair.setEnabled(true);
                    for (Setting<?> s : crosshair.settings()) {
                        if (s instanceof ColorSetting) {
                            break;
                        }
                    }
                }
                this.minecraft.setScreen(clickGui);
                clickGui.keyPressed(event);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_F7) {
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

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
