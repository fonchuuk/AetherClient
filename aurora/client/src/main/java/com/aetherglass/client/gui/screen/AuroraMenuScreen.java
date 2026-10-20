package com.aetherglass.client.gui.screen;

import com.aetherglass.client.gui.menu.AtmosphericMessages;
import com.aetherglass.client.gui.menu.WinterNight;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.modules.ModuleCategory;
import com.aetherglass.client.render.util.FrameClock;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Главное меню AetherGlass в стиле Matte Black + Neon (сетка карточек в 3 колонки):
 * <ul>
 *   <li>Слева — узкая панель навигации (Главная, Визуал, Интерфейс, HUD, Аккаунты + быстрые кнопки игры);</li>
 *   <li>Справа — аккуратная сетка прямоугольных карточек в 3 колонки с отступами 9px;</li>
 *   <li>Матовый чёрный фон карточек (#0B0C10) без перекрашивания и неоновая обводка с glow (#00F2FF) только при включении модуля;</li>
 *   <li>ЛКМ включает/выключает модуль, ПКМ плавно выкатывает настройки внутри карточки, СКМ вызывает окошко бинда с иконкой клавиатуры.</li>
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

        public String title() {
            return this.title;
        }
    }

    private final WinterNight winterNight = new WinterNight();
    private final AtmosphericMessages atmosphere = new AtmosphericMessages();
    private final Tab initialTab;
    private AuroraInlineMenuScreen inlineMenu;

    public AuroraMenuScreen() {
        this(Tab.HOME);
    }

    public AuroraMenuScreen(Tab initialTab) {
        super(Component.literal("AetherGlass"), Component.literal("Matte Black · Neon"));
        this.initialTab = initialTab == null ? Tab.HOME : initialTab;
    }

    @Override
    protected void build() {
        this.winterNight.resize(this.width, this.height);
        this.inlineMenu = new AuroraInlineMenuScreen(this);
        this.inlineMenu.attachHost(this.width, this.height);
        if (this.initialTab == Tab.SHADERS) {
            this.inlineMenu.setCategoryFilter(ModuleCategory.VISUAL);
        } else if (this.initialTab == Tab.SETTINGS) {
            this.inlineMenu.setCategoryFilter(ModuleCategory.INTERFACE);
        }

        int panelW = Math.min(620, this.width - 20);
        int panelH = Math.min(344, this.height - 16);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;
        this.atmosphere.setZones(List.of(new AtmosphericMessages.Zone(panelX - 12, panelY - 12, panelW + 24, panelH + 24)));
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
        // Заголовок отрисовывается внутри узкой левой панели навигации
    }

    @Override
    protected void drawCustom(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = FrameClock.deltaSeconds();
        float progress = transitionProgress();
        this.atmosphere.update(delta, mouseX, mouseY, this.width, this.height);
        this.atmosphere.render(graphics, this.font);

        if (this.inlineMenu != null) {
            this.inlineMenu.renderEmbedded(graphics, mouseX, mouseY, progress, delta, this.width, this.height, true);
        }
        NotificationManager.get().render(graphics, this.width, this.height);
    }

    @Override
    protected void drawFooter(GuiGraphics graphics) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.inlineMenu != null && this.inlineMenu.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.inlineMenu != null && this.inlineMenu.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.inlineMenu != null) {
            this.inlineMenu.mouseReleased(event);
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.inlineMenu != null && this.inlineMenu.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.inlineMenu != null && this.inlineMenu.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.inlineMenu != null && this.inlineMenu.charTyped(event)) {
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
