package com.aetherglass.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/**
 * Цвета и шрифты лаунчера: тёмный фон, неоновые акценты, крупные заголовки.
 *
 * <p>Палитра намеренно яркая — лаунчер должен выглядеть живым, а не
 * казённым. При этом она собрана из тех же оттенков, что и оформление клиента
 * (холодный синий, фиолетовый, циан), поэтому меню игры и окно лаунчера
 * выглядят одним продуктом.</p>
 */
public final class UiTheme {

    // ── фон ──
    public static final Color BACKGROUND_TOP = new Color(0x0B, 0x10, 0x26);
    public static final Color BACKGROUND_BOTTOM = new Color(0x14, 0x0A, 0x26);
    public static final Color BACKGROUND = BACKGROUND_TOP;
    public static final Color PANEL = new Color(0x1A, 0x1F, 0x3D);
    public static final Color PANEL_LIGHT = new Color(0x23, 0x2A, 0x4D);

    /** Световые пятна на фоне. */
    public static final Color BLOB_VIOLET = new Color(0x7C, 0x3A, 0xED);
    public static final Color BLOB_CYAN = new Color(0x22, 0xD3, 0xEE);
    public static final Color BLOB_PINK = new Color(0xF4, 0x72, 0xB6);

    // ── стекло ──
    public static final Color CARD_TOP = new Color(0x1C, 0x22, 0x44);
    public static final Color CARD_BOTTOM = new Color(0x15, 0x18, 0x34);
    public static final Color CARD_BORDER = new Color(0x2E, 0x36, 0x5C);
    public static final Color TRACK = new Color(0x17, 0x1C, 0x33);
    public static final Color FIELD = new Color(0x15, 0x1A, 0x33);

    // ── акценты ──
    public static final Color ACCENT = new Color(0x22, 0xD3, 0xEE);
    public static final Color ACCENT_2 = new Color(0xA8, 0x55, 0xF7);
    public static final Color ACCENT_3 = new Color(0xF4, 0x72, 0xB6);
    public static final Color ON_ACCENT = new Color(0x0A, 0x10, 0x22);
    public static final Color BUTTON = new Color(0x23, 0x2A, 0x4D);
    public static final Color BUTTON_HOVER = new Color(0x33, 0x3D, 0x70);

    // ── текст и состояния ──
    public static final Color TEXT = new Color(0xEA, 0xF0, 0xFF);
    public static final Color TEXT_MUTED = new Color(0x9A, 0xA6, 0xC8);
    public static final Color SUCCESS = new Color(0x34, 0xD3, 0x99);
    public static final Color WARNING = new Color(0xFB, 0xBF, 0x24);
    public static final Color DANGER = new Color(0xFB, 0x71, 0x85);
    public static final Color BORDER = CARD_BORDER;
    public static final Color CONSOLE = new Color(0x0A, 0x0D, 0x1A);

    // ── шрифты ──
    public static final Font BASE = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font SMALL = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    public static final Font SMALL_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 12);
    public static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 15);
    public static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 26);
    public static final Font SUBTITLE = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    public static final Font TAB = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
    public static final Font TAB_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font BUTTON_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 14);
    public static final Font BUTTON_BOLD = new Font(Font.SANS_SERIF, Font.BOLD, 16);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private UiTheme() {
    }

    /** Включает системный вид и задаёт базовые цвета Swing. */
    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception exception) {
            // Системный вид недоступен — остаётся стандартный Metal.
        }
        UIManager.put("Panel.background", BACKGROUND_TOP);
        UIManager.put("OptionPane.background", CARD_TOP);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("TabbedPane.background", BACKGROUND_TOP);
        UIManager.put("TabbedPane.foreground", TEXT);
        UIManager.put("TabbedPane.contentAreaColor", BACKGROUND_TOP);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Label.font", BASE);
        UIManager.put("CheckBox.foreground", TEXT);
        UIManager.put("CheckBox.background", CARD_TOP);
        UIManager.put("CheckBox.font", BASE);
        UIManager.put("TextField.background", FIELD);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", ACCENT);
        UIManager.put("TextField.font", BASE);
        UIManager.put("ComboBox.background", FIELD);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.font", BASE);
        UIManager.put("Button.background", BUTTON);
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.font", BUTTON_FONT);
        UIManager.put("ProgressBar.foreground", ACCENT);
        UIManager.put("ProgressBar.background", TRACK);
        UIManager.put("List.background", FIELD);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("Table.background", CARD_BOTTOM);
        UIManager.put("Table.foreground", TEXT);
        UIManager.put("Table.font", BASE);
        UIManager.put("TableHeader.background", CARD_BORDER);
        UIManager.put("TableHeader.foreground", TEXT_MUTED);
        UIManager.put("TextArea.background", CONSOLE);
        UIManager.put("TextArea.foreground", TEXT);
        UIManager.put("ScrollPane.background", CARD_BOTTOM);
        UIManager.put("Viewport.background", CARD_BOTTOM);
        UIManager.put("Slider.background", CARD_TOP);
        UIManager.put("Slider.foreground", ACCENT);
    }

    /** Скруглённая рамка для простых панелей. */
    public static Border roundedBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CARD_BORDER, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12));
    }
}
