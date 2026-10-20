package com.aetherglass.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/**
 * Цвета и шрифты лаунчера в едином с клиентом стиле Matte Black + Neon Cyan:
 * <ul>
 *   <li>Глубокий матовый чёрный фон карточек ({@code #0B0C10}) без перекрашивания;</li>
 *   <li>Яркая неоновая бирюзовая обводка ({@code #00F2FF}) с внешним свечением (glow) для активных карточек и элементов;</li>
 *   <li>Белый жирный текст заголовков и спокойный серый текст описаний.</li>
 * </ul>
 */
public final class UiTheme {

    // ── Матовый чёрный фон ──
    public static final Color BACKGROUND_TOP = new Color(0x06, 0x07, 0x0A);
    public static final Color BACKGROUND_BOTTOM = new Color(0x08, 0x09, 0x0D);
    public static final Color BACKGROUND = BACKGROUND_TOP;
    public static final Color PANEL = new Color(0x0B, 0x0C, 0x10);
    public static final Color PANEL_LIGHT = new Color(0x10, 0x12, 0x18);

    /** Деликатное фоновое свечение без цветных пятен. */
    public static final Color BLOB_VIOLET = new Color(0xAA, 0x3D, 0xFF);
    public static final Color BLOB_CYAN = new Color(0x93, 0x33, 0xEA);
    public static final Color BLOB_PINK = new Color(0xC0, 0x84, 0xFC);

    // ── Матовые чёрные карточки (#0B0C10) ──
    public static final Color CARD_TOP = new Color(0x0B, 0x0C, 0x10);
    public static final Color CARD_BOTTOM = new Color(0x0B, 0x0C, 0x10);
    public static final Color CARD_BORDER = new Color(0x1E, 0x22, 0x2C);
    public static final Color TRACK = new Color(0x1A, 0x1E, 0x28);
    public static final Color FIELD = new Color(0x0E, 0x10, 0x16);

    // ── Неоновый фиолетовый (#AA3DFF) по умолчанию ──
    public static final Color ACCENT = new Color(0xAA, 0x3D, 0xFF);
    public static final Color ACCENT_2 = new Color(0x93, 0x33, 0xEA);
    public static final Color ACCENT_3 = new Color(0xC0, 0x84, 0xFC);
    public static final Color ON_ACCENT = new Color(0xFFFFFF);
    public static final Color BUTTON = new Color(0x0B, 0x0C, 0x10);
    public static final Color BUTTON_HOVER = new Color(0x12, 0x15, 0x1E);

    // ── Текст и состояния ──
    public static final Color TEXT = new Color(0xFF, 0xFF, 0xFF);
    public static final Color TEXT_MUTED = new Color(0x8C, 0x94, 0xA6);
    public static final Color SUCCESS = new Color(0x00, 0xF5, 0xA0);
    public static final Color WARNING = new Color(0xFF, 0xB8, 0x4D);
    public static final Color DANGER = new Color(0xFF, 0x4D, 0x6D);
    public static final Color BORDER = CARD_BORDER;
    public static final Color CONSOLE = new Color(0x07, 0x08, 0x0B);

    // ── Шрифты (Montserrat TrueType, компактные и современные) ──
    private static final Font BASE_MEDIUM = loadFont("/fonts/montserrat_medium.ttf", Font.PLAIN, 12f);
    private static final Font BASE_BOLD = loadFont("/fonts/montserrat_bold.ttf", Font.BOLD, 12f);

    public static final Font BASE = BASE_MEDIUM.deriveFont(11.5f);
    public static final Font SMALL = BASE_MEDIUM.deriveFont(10.0f);
    public static final Font SMALL_BOLD = BASE_BOLD.deriveFont(10.5f);
    public static final Font HEADING = BASE_BOLD.deriveFont(12.0f);
    public static final Font TITLE = BASE_BOLD.deriveFont(18.0f);
    public static final Font SUBTITLE = BASE_MEDIUM.deriveFont(11.0f);
    public static final Font TAB = BASE_MEDIUM.deriveFont(11.5f);
    public static final Font TAB_BOLD = BASE_BOLD.deriveFont(11.5f);
    public static final Font BUTTON_FONT = BASE_BOLD.deriveFont(11.5f);
    public static final Font BUTTON_BOLD = BASE_BOLD.deriveFont(13.0f);
    public static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 11);

    private static Font loadFont(String resourcePath, int fallbackStyle, float size) {
        try (java.io.InputStream in = UiTheme.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Font f = Font.createFont(Font.TRUETYPE_FONT, in);
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
                return f.deriveFont(size);
            }
        } catch (Exception ignored) {
        }
        return new Font(Font.SANS_SERIF, fallbackStyle, Math.round(size));
    }

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
                BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }
}
