package com.aetherglass.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSlider;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Оформление лаунчера в едином с клиентом стиле Matte Black + Neon Cyan:
 * <ul>
 *   <li>Прямоугольные карточки со скруглёнными углами (10–12px), матовый чёрный фон ({@code #0B0C10}) без перекрашивания;</li>
 *   <li>Неоновая бирюзовая обводка ({@code #00F2FF}) с мягким внешним свечением (glow) только для активных/включённых карточек;</li>
 *   <li>Тонкие ползунки с неоновым кружком и узкая панель навигации слева.</li>
 * </ul>
 */
public final class UiKit {

    /** Радиус скругления прямоугольных карточек (11px). */
    public static final int CARD_RADIUS = 12;

    /** Радиус скругления кнопок (10px, без капсул). */
    public static final int BUTTON_RADIUS = 10;

    private static final int TICK_MS = 33;

    private static final List<Animated> ANIMATED = new ArrayList<>();
    private static Timer timer;
    private static long clock;

    private UiKit() {
    }

    private interface Animated {
        boolean tick();

        void repaint();
    }

    private static void register(Animated animated) {
        synchronized (ANIMATED) {
            if (ANIMATED.contains(animated)) {
                return;
            }
            ANIMATED.add(animated);
            if (timer == null) {
                timer = new Timer(TICK_MS, event -> step());
                timer.setRepeats(true);
                timer.start();
            }
        }
    }

    private static void step() {
        clock += TICK_MS;
        List<Animated> snapshot;
        synchronized (ANIMATED) {
            snapshot = new ArrayList<>(ANIMATED);
        }
        List<Animated> finished = new ArrayList<>();
        for (Animated animated : snapshot) {
            if (!animated.tick()) {
                finished.add(animated);
            }
            animated.repaint();
        }
        if (!finished.isEmpty()) {
            synchronized (ANIMATED) {
                ANIMATED.removeAll(finished);
            }
        }
    }

    static float seconds() {
        return clock / 1000.0F;
    }

    // ─────────────────────────── Матовый чёрный фон и карточки ───────────────────────────

    public static JPanel background() {
        return new Backdrop();
    }

    private static final class Backdrop extends JPanel implements Animated {
        Backdrop() {
            super(new BorderLayout());
            setOpaque(true);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            register(this);
        }

        @Override
        public boolean tick() {
            return isShowing();
        }

        @Override
        public void repaint() {
            repaint(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            g.setPaint(new java.awt.GradientPaint(0, 0, UiTheme.BACKGROUND_TOP, 0, height,
                    UiTheme.BACKGROUND_BOTTOM));
            g.fillRect(0, 0, width, height);
            g.dispose();
        }
    }

    /**
     * Рисует матовую чёрную карточку ({@code #0B0C10}) с неоновой обводкой и внешним свечением (glow),
     * которое загорается ТОЛЬКО когда {@code glowStrength > 0.02f}.
     */
    public static void paintMatteNeonRect(Graphics2D g, int x, int y, int w, int h, int radius, float glowStrength) {
        if (w <= 2 || h <= 2) {
            return;
        }
        float glow = Math.max(0.0F, Math.min(1.0F, glowStrength));

        // 1. Мягкое внешнее неоновое свечение (glow) — только при включении
        if (glow > 0.02F) {
            for (int layer = 4; layer >= 1; layer--) {
                float alpha = (0.07F * (5 - layer)) * glow;
                g.setColor(fade(UiTheme.ACCENT, alpha));
                g.setStroke(new BasicStroke(layer * 1.4F));
                g.draw(new RoundRectangle2D.Float(
                        x + 4 - layer, y + 4 - layer,
                        w - 8 + layer * 2, h - 8 + layer * 2,
                        radius + layer, radius + layer));
            }
        }

        // 2. Фон: всегда матовый чёрный (#0B0C10), без перекрашивания
        Shape body = new RoundRectangle2D.Float(x + 4, y + 4, w - 8, h - 8, radius, radius);
        g.setColor(UiTheme.CARD_TOP);
        g.fill(body);

        // 3. Обводка по периметру
        if (glow > 0.02F) {
            g.setColor(fade(UiTheme.ACCENT, 0.95F * glow));
            g.setStroke(new BasicStroke(1.6F));
            g.draw(body);
        } else {
            g.setColor(new Color(255, 255, 255, 18));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(body);
        }
    }

    /**
     * Компактная прямоугольная карточка для 3-колоночной сетки (Matte Black + Neon).
     * ЛКМ переключает/выбирает карточку и зажигает/гасит неоновую обводку.
     */
    public static JPanel matteNeonGridCard(String title, String description,
                                           BooleanSupplier isEnabled, Runnable onLeftClick) {
        JPanel card = new JPanel(new BorderLayout(0, 3)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = isEnabled != null && isEnabled.getAsBoolean();
                paintMatteNeonRect(g, 0, 0, getWidth(), getHeight(), CARD_RADIUS, active ? 1.0F : 0.0F);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(UiTheme.HEADING);
        titleLbl.setForeground(UiTheme.TEXT);

        JLabel descLbl = new JLabel(description);
        descLbl.setFont(UiTheme.SMALL);
        descLbl.setForeground(UiTheme.TEXT_MUTED);

        card.add(titleLbl, BorderLayout.NORTH);
        card.add(descLbl, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && onLeftClick != null) {
                    onLeftClick.run();
                    card.repaint();
                }
            }
        });
        return card;
    }

    /** Карточка-контейнер: матовый чёрный прямоугольник со скруглением 12px. */
    public static JPanel card(String title, JComponent content) {
        return card(title, content, false);
    }

    public static JPanel card(String title, JComponent content, boolean neonOutline) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                paintMatteNeonRect(g, 0, 0, getWidth(), getHeight(), CARD_RADIUS, neonOutline ? 1.0F : 0.0F);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        if (title != null && !title.isBlank()) {
            card.add(heading(title), BorderLayout.NORTH);
        }
        card.add(content, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    // ─────────────────────────── Текст ───────────────────────────

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.HEADING);
        label.setForeground(UiTheme.TEXT);
        return label;
    }

    public static JLabel muted(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UiTheme.SMALL);
        label.setForeground(UiTheme.TEXT_MUTED);
        return label;
    }

    public static JLabel chip(String text, Color color) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(UiTheme.CARD_TOP);
                g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
                g.setColor(fade(color, 0.85F));
                g.setStroke(new BasicStroke(1.2F));
                g.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 10, 10));
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(UiTheme.SMALL_BOLD);
        label.setForeground(UiTheme.TEXT);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        label.setOpaque(false);
        return label;
    }

    // ─────────────────────────── Кнопки (Matte Black + Neon) ───────────────────────────

    public static JButton button(String text) {
        return new GlassButton(text, false, false);
    }

    public static JButton primaryButton(String text) {
        GlassButton result = new GlassButton(text, true, false);
        result.setFont(UiTheme.BUTTON_BOLD);
        return result;
    }

    public static JButton dangerButton(String text) {
        return new GlassButton(text, false, true);
    }

    // ─────────────────────────── Прочие элементы ───────────────────────────

    public static JProgressBar progress() {
        JProgressBar bar = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int height = getHeight();
                int radius = 8;
                g.setColor(UiTheme.TRACK);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), height, radius, radius));
                double percent = getPercentComplete() < 0 ? 0.0F : getPercentComplete();
                int filled = Math.round((float) (getWidth() * percent));
                if (filled > 2) {
                    g.setColor(UiTheme.ACCENT);
                    g.fill(new RoundRectangle2D.Float(0, 0, filled, height, radius, radius));
                }
                if (isStringPainted()) {
                    String text = getString();
                    g.setFont(UiTheme.SMALL_BOLD);
                    FontMetrics metrics = g.getFontMetrics();
                    g.setColor(UiTheme.TEXT);
                    g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                            (height + metrics.getAscent() - metrics.getDescent()) / 2);
                }
                g.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setStringPainted(true);
        bar.setString("");
        bar.setPreferredSize(new Dimension(10, 14));
        bar.setBorder(BorderFactory.createEmptyBorder());
        return bar;
    }

    public static JTextField field(JTextField field) {
        field.setFont(UiTheme.BASE);
        field.setBackground(UiTheme.FIELD);
        field.setForeground(UiTheme.TEXT);
        field.setCaretColor(UiTheme.ACCENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                new RoundedLineBorder(UiTheme.CARD_BORDER, BUTTON_RADIUS),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));
        return field;
    }

    public static <T> JComboBox<T> combo(JComboBox<T> box) {
        box.setFont(UiTheme.BASE);
        box.setBackground(UiTheme.FIELD);
        box.setForeground(UiTheme.TEXT);
        box.setBorder(new RoundedLineBorder(UiTheme.CARD_BORDER, BUTTON_RADIUS));
        if (box.getRenderer() instanceof JLabel label) {
            label.setHorizontalAlignment(SwingConstants.LEFT);
        }
        return box;
    }

    public static JSlider slider(JSlider slider) {
        slider.setOpaque(false);
        slider.setForeground(UiTheme.ACCENT);
        slider.setBackground(UiTheme.TRACK);
        slider.setFont(UiTheme.SMALL);
        slider.setPaintTicks(false);
        slider.setPaintLabels(false);
        slider.setUI(new GlassSlider(slider));
        return slider;
    }

    public static JCheckBox check(JCheckBox box) {
        box.setOpaque(false);
        box.setFont(UiTheme.BASE);
        box.setForeground(UiTheme.TEXT);
        box.setBackground(UiTheme.CARD_TOP);
        return box;
    }

    public static void styleTabs(javax.swing.JTabbedPane tabs) {
        tabs.setOpaque(false);
        tabs.setFont(UiTheme.TAB);
        tabs.setForeground(UiTheme.TEXT_MUTED);
        tabs.setBackground(UiTheme.BACKGROUND_TOP);
        tabs.setUI(new PillTabs());
    }

    public static void styleTable(JTable table) {
        table.setOpaque(false);
        table.setRowHeight(26);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setFont(UiTheme.BASE);
        table.setForeground(UiTheme.TEXT);
        table.setBackground(UiTheme.CARD_BOTTOM);
        table.setSelectionBackground(fade(UiTheme.ACCENT, 0.18F));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        JTableHeader header = table.getTableHeader();
        header.setOpaque(false);
        header.setFont(UiTheme.SMALL_BOLD);
        header.setBackground(UiTheme.CARD_TOP);
        header.setForeground(UiTheme.TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 28));
        header.setDefaultRenderer(new CellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table2, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component result = super.getTableCellRendererComponent(table2, value, selected, focus, row, column);
                result.setForeground(UiTheme.TEXT_MUTED);
                return result;
            }
        });
    }

    public static javax.swing.JScrollPane scroll(JComponent content) {
        javax.swing.JScrollPane pane = new javax.swing.JScrollPane(content);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(UiTheme.CARD_BOTTOM);
        return pane;
    }

    /** Тонкая полоска слайдера с неоновым кружком. */
    private static final class GlassSlider extends javax.swing.plaf.basic.BasicSliderUI {
        GlassSlider(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(14, 14);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = 4;
            int y = trackRect.y + (trackRect.height - height) / 2;
            g2.setColor(UiTheme.TRACK);
            g2.fill(new RoundRectangle2D.Float(trackRect.x, y, trackRect.width, height, height, height));
            int filled = thumbRect.x + thumbRect.width / 2 - trackRect.x;
            if (filled > 2) {
                g2.setColor(UiTheme.ACCENT);
                g2.fill(new RoundRectangle2D.Float(trackRect.x, y, filled, height, height, height));
            }
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float size = 12.0F;
            float x = thumbRect.x + (thumbRect.width - size) / 2.0F;
            float y = thumbRect.y + (thumbRect.height - size) / 2.0F;
            g2.setColor(fade(UiTheme.ACCENT, 0.35F));
            g2.fill(new Ellipse2D.Float(x - 3, y - 3, size + 6, size + 6));
            g2.setColor(UiTheme.ACCENT);
            g2.fill(new Ellipse2D.Float(x, y, size, size));
            g2.setColor(Color.WHITE);
            g2.fill(new Ellipse2D.Float(x + 3.5F, y + 3.5F, size - 7.0F, size - 7.0F));
            g2.dispose();
        }

        @Override
        public void paintFocus(Graphics g) {
        }
    }

    static Color fade(Color color, float alpha) {
        int clamped = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), clamped);
    }

    static Color mix(Color from, Color to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped));
    }

    static float overshoot(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        float c = 1.9F;
        float p = clamped - 1.0F;
        return 1.0F + (c + 1.0F) * p * p * p + c * p * p;
    }

    /** Матовая чёрная прямоугольная кнопка с неоновой обводкой (glow). */
    private static final class GlassButton extends JButton implements Animated {
        private final boolean primaryNeon;
        private final boolean dangerNeon;
        private float hoverAmount;

        GlassButton(String text, boolean primaryNeon, boolean dangerNeon) {
            super(text);
            this.primaryNeon = primaryNeon;
            this.dangerNeon = dangerNeon;
            setFont(UiTheme.BUTTON_FONT);
            setForeground(UiTheme.TEXT);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    register(GlassButton.this);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    register(GlassButton.this);
                }
            });
        }

        @Override
        public boolean tick() {
            boolean inside = getModel().isRollover();
            float step = TICK_MS / 120.0F;
            this.hoverAmount += (inside ? step : -step);
            this.hoverAmount = Math.max(0.0F, Math.min(1.0F, this.hoverAmount));
            return (inside && this.hoverAmount < 1.0F) || (!inside && this.hoverAmount > 0.0F);
        }

        @Override
        public void repaint() {
            repaint(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            Color neonCol = this.dangerNeon ? UiTheme.DANGER : UiTheme.ACCENT;
            float glow = this.primaryNeon ? 1.0F : (this.dangerNeon ? 0.85F : this.hoverAmount * 0.90F);

            if (glow > 0.02F) {
                for (int layer = 3; layer >= 1; layer--) {
                    g.setColor(fade(neonCol, 0.08F * (4 - layer) * glow));
                    g.setStroke(new BasicStroke(layer * 1.3F));
                    g.draw(new RoundRectangle2D.Float(3 - layer, 3 - layer,
                            w - 6 + layer * 2, h - 6 + layer * 2,
                            BUTTON_RADIUS + layer, BUTTON_RADIUS + layer));
                }
            }

            Shape body = new RoundRectangle2D.Float(3, 3, w - 6, h - 6, BUTTON_RADIUS, BUTTON_RADIUS);
            g.setColor(UiTheme.CARD_TOP);
            g.fill(body);

            if (glow > 0.02F) {
                g.setColor(fade(neonCol, 0.95F * glow));
                g.setStroke(new BasicStroke(1.5F));
                g.draw(body);
            } else {
                g.setColor(new Color(255, 255, 255, 24));
                g.setStroke(new BasicStroke(1.0F));
                g.draw(body);
            }

            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Component result = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            result.setFont(UiTheme.BASE);
            result.setForeground(UiTheme.TEXT);
            if (!selected) {
                result.setBackground(row % 2 == 0 ? new Color(0x0E, 0x10, 0x16) : UiTheme.CARD_BOTTOM);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            String text = String.valueOf(value);
            if ("установлен".equals(text) || "готов".equals(text) || text.startsWith("Включён")) {
                result.setForeground(UiTheme.ACCENT);
            } else if ("отсутствует".equals(text) || "ошибка".equals(text)) {
                result.setForeground(UiTheme.DANGER);
            } else if ("устарел".equals(text)) {
                result.setForeground(UiTheme.WARNING);
            }
            return result;
        }
    }

    /** Вкладки узкой левой панели навигации в стиле Matte Black + Neon. */
    private static final class PillTabs extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            this.contentBorderInsets = new Insets(0, 8, 0, 0);
            this.tabAreaInsets = new Insets(6, 10, 6, 4);
            this.selectedTabPadInsets = new Insets(0, 0, 0, 0);
            this.tabInsets = new Insets(8, 16, 8, 16);
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return 36;
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            if (tabPlacement == SwingConstants.LEFT) {
                return 134;
            }
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 22;
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int width,
                                          int height, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintMatteNeonRect(g2, x, y, width, height, 10, isSelected ? 1.0F : 0.0F);
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int width, int height,
                                      boolean isSelected) {
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                                 String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(isSelected ? UiTheme.TAB_BOLD : UiTheme.TAB);
            g2.setColor(isSelected ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
            FontMetrics own = g2.getFontMetrics();
            int x = textRect.x + 6;
            int baseline = textRect.y + (textRect.height + own.getAscent() - own.getDescent()) / 2;
            g2.drawString(title, x, baseline);
            g2.dispose();
        }
    }

    public static final class RoundedLineBorder implements Border {
        private final Color color;
        private final int radius;

        public RoundedLineBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(this.color);
            g2.setStroke(new BasicStroke(1.1F));
            g2.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, width - 1, height - 1, this.radius, this.radius));
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return new Insets(4, 10, 4, 10);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
