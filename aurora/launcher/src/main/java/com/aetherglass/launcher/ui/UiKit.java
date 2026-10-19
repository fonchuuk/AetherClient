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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Внешний вид лаунчера: «жидкое стекло» в яркой, молодёжной подаче.
 *
 * <p>Ничего не берётся из чужих библиотек и картинок: панели, кнопки, ползунки,
 * вкладки и индикатор загрузки рисуются кодом на {@link Graphics2D}. Цвета и
 * шрифты лежат в {@link UiTheme}, геометрия — здесь.</p>
 *
 * <p>Анимации считаются от времени, а не от количества кадров: один общий таймер
 * тикает 30 раз в секунду и перерисовывает только те компоненты, у которых есть
 * незавершённое движение. Когда движение закончилось, компонент из списка
 * убирается — процессор не тратится зря.</p>
 */
public final class UiKit {

    /** Радиус скругления карточек. */
    public static final int CARD_RADIUS = 18;

    /** Радиус скругления кнопок. */
    public static final int BUTTON_RADIUS = 14;

    /** Общий таймер анимаций: один на всё приложение. */
    private static final int TICK_MS = 33;

    private static final List<Animated> ANIMATED = new ArrayList<>();
    private static Timer timer;
    private static long clock;

    private UiKit() {
    }

    // ─────────────────────────── общий таймер ───────────────────────────

    /** Компонент, умеющий двигаться: сам решает, закончил он или нет. */
    private interface Animated {
        /** @return {@code true}, если компонент ещё движется */
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

    /** Время анимаций в секундах: по нему считаются все плавные движения. */
    static float seconds() {
        return clock / 1000.0F;
    }

    // ─────────────────────────── фон и карточки ───────────────────────────

    /**
     * Фон окна: вертикальный градиент и три мягких световых пятна, которые
     * медленно дрейфуют. Пятна считаются от времени, поэтому картинка живая,
     * но почти ничего не стоит процессору.
     */
    public static JPanel background() {
        return new Backdrop();
    }

    /** Фон окна: градиент и дрейфующие пятна; сам себя ставит на общий таймер. */
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

            float time = seconds();
            blob(g, width, height,
                    width * (0.18F + 0.05F * (float) Math.sin(time * 0.13D)),
                    height * (0.16F + 0.05F * (float) Math.cos(time * 0.11D)),
                    Math.max(width, height) * 0.42F, UiTheme.BLOB_VIOLET, 0.40F);
            blob(g, width, height,
                    width * (0.84F + 0.04F * (float) Math.cos(time * 0.09D)),
                    height * (0.28F + 0.06F * (float) Math.sin(time * 0.15D)),
                    Math.max(width, height) * 0.36F, UiTheme.BLOB_CYAN, 0.32F);
            blob(g, width, height,
                    width * (0.52F + 0.06F * (float) Math.sin(time * 0.07D)),
                    height * (0.92F + 0.03F * (float) Math.cos(time * 0.10D)),
                    Math.max(width, height) * 0.40F, UiTheme.BLOB_PINK, 0.26F);
            g.dispose();
        }
    }

    private static void blob(Graphics2D g, int width, int height, float cx, float cy, float radius, Color color,
                             float alpha) {
        RadialGradientPaint paint = new RadialGradientPaint(new Point2D.Float(cx, cy), radius,
                new float[] {0.0F, 1.0F},
                new Color[] {fade(color, alpha), fade(color, 0.0F)});
        g.setPaint(paint);
        g.fillRect(0, 0, width, height);
    }

    /** Карточка: полупрозрачное стекло со скруглением и мягкой подсветкой сверху. */
    public static JPanel card(String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Shape shape = new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, CARD_RADIUS,
                        CARD_RADIUS);
                g.setPaint(new java.awt.GradientPaint(0, 0, UiTheme.CARD_TOP, 0, getHeight(), UiTheme.CARD_BOTTOM));
                g.fill(shape);
                g.setColor(UiTheme.CARD_BORDER);
                g.setStroke(new BasicStroke(1.2F));
                g.draw(shape);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        if (title != null && !title.isBlank()) {
            card.add(heading(title), BorderLayout.NORTH);
        }
        card.add(content, BorderLayout.CENTER);
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(card, BorderLayout.CENTER);
        return wrapper;
    }

    // ─────────────────────────── текст ───────────────────────────

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

    /**
     * Цветная «таблетка»: состояние аккаунта, версии, результат проверки.
     *
     * @param color цвет рамки и текста таблетки
     */
    public static JLabel chip(String text, Color color) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fade(color, 0.18F));
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), getHeight(), getHeight()));
                g.setColor(fade(color, 0.55F));
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, getWidth() - 1, getHeight() - 1, getHeight() - 1,
                        getHeight() - 1));
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(UiTheme.SMALL_BOLD);
        label.setForeground(color);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        label.setOpaque(false);
        return label;
    }

    // ─────────────────────────── кнопки ───────────────────────────

    public static JButton button(String text) {
        return new GlassButton(text, UiTheme.BUTTON, UiTheme.BUTTON_HOVER, UiTheme.TEXT);
    }

    /** Главная кнопка: градиентный акцент — «Играть». */
    public static JButton primaryButton(String text) {
        GlassButton result = new GlassButton(text, UiTheme.ACCENT, UiTheme.ACCENT_2, UiTheme.ON_ACCENT);
        result.setFont(UiTheme.BUTTON_BOLD);
        return result;
    }

    public static JButton dangerButton(String text) {
        return new GlassButton(text, UiTheme.DANGER, UiTheme.DANGER.brighter(), UiTheme.ON_ACCENT);
    }

    // ─────────────────────────── прочие элементы ───────────────────────────

    /** Индикатор загрузки: скруглённая дорожка и градиентное заполнение. */
    public static JProgressBar progress() {
        JProgressBar bar = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int height = getHeight();
                int radius = height;
                g.setColor(UiTheme.TRACK);
                g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), height, radius, radius));
                double percent = getPercentComplete() < 0 ? 0.0F : getPercentComplete();
                int filled = Math.round((float) (getWidth() * percent));
                if (filled > 2) {
                    g.setPaint(new java.awt.GradientPaint(0, 0, UiTheme.ACCENT, filled, height, UiTheme.ACCENT_2));
                    g.fill(new RoundRectangle2D.Float(0, 0, filled, height, radius, radius));
                    g.setColor(fade(Color.WHITE, 0.30F));
                    g.fill(new RoundRectangle2D.Float(2, 2, Math.max(0, filled - 4), Math.max(1, height / 3),
                            height / 3, height / 3));
                }
                if (isStringPainted()) {
                    String text = getString();
                    g.setFont(UiTheme.SMALL_BOLD);
                    FontMetrics metrics = g.getFontMetrics();
                    g.setColor(filled > getWidth() / 2 ? UiTheme.ON_ACCENT : UiTheme.TEXT);
                    g.drawString(text, (getWidth() - metrics.stringWidth(text)) / 2,
                            (height + metrics.getAscent() - metrics.getDescent()) / 2);
                }
                g.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setStringPainted(true);
        bar.setString("");
        bar.setPreferredSize(new Dimension(10, 18));
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
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));
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

    /** Оформляет вкладки: «таблетки» вместо стандартных прямоугольников. */
    public static void styleTabs(javax.swing.JTabbedPane tabs) {
        tabs.setOpaque(false);
        tabs.setFont(UiTheme.TAB);
        tabs.setForeground(UiTheme.TEXT_MUTED);
        tabs.setBackground(UiTheme.BACKGROUND_TOP);
        tabs.setUI(new PillTabs());
    }

    /** Оформляет таблицу модов: мягкие строки и цветные состояния. */
    public static void styleTable(JTable table) {
        table.setOpaque(false);
        table.setRowHeight(28);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 2));
        table.setFont(UiTheme.BASE);
        table.setForeground(UiTheme.TEXT);
        table.setBackground(UiTheme.CARD_BOTTOM);
        table.setSelectionBackground(fade(UiTheme.ACCENT, 0.22F));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        JTableHeader header = table.getTableHeader();
        header.setOpaque(false);
        header.setFont(UiTheme.SMALL_BOLD);
        header.setBackground(fade(UiTheme.CARD_BORDER, 0.55F));
        header.setForeground(UiTheme.TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 30));
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

    /** Прозрачная прокрутка: внутри карточки видно стекло, а не серый прямоугольник. */
    public static javax.swing.JScrollPane scroll(JComponent content) {
        javax.swing.JScrollPane pane = new javax.swing.JScrollPane(content);
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getViewport().setBackground(UiTheme.CARD_BOTTOM);
        return pane;
    }

    /** Ползунок: скруглённая дорожка и градиентный бегунок вместо системного вида. */
    private static final class GlassSlider extends javax.swing.plaf.basic.BasicSliderUI {
        GlassSlider(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(18, 18);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = 8;
            int y = trackRect.y + (trackRect.height - height) / 2;
            g2.setColor(UiTheme.TRACK);
            g2.fill(new RoundRectangle2D.Float(trackRect.x, y, trackRect.width, height, height, height));
            int filled = thumbRect.x + thumbRect.width / 2 - trackRect.x;
            if (filled > 2) {
                g2.setPaint(new java.awt.GradientPaint(trackRect.x, y, UiTheme.ACCENT_2, trackRect.x + filled, y,
                        UiTheme.ACCENT));
                g2.fill(new RoundRectangle2D.Float(trackRect.x, y, filled, height, height, height));
            }
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float size = 18.0F;
            float x = thumbRect.x + (thumbRect.width - size) / 2.0F;
            float y = thumbRect.y + (thumbRect.height - size) / 2.0F;
            g2.setColor(fade(UiTheme.ACCENT, 0.35F));
            g2.fill(new java.awt.geom.Ellipse2D.Float(x - 3, y - 3, size + 6, size + 6));
            g2.setPaint(new java.awt.GradientPaint(x, y, UiTheme.ACCENT, x, y + size, UiTheme.ACCENT_2));
            g2.fill(new java.awt.geom.Ellipse2D.Float(x, y, size, size));
            g2.setColor(fade(Color.WHITE, 0.85F));
            g2.fill(new java.awt.geom.Ellipse2D.Float(x + size * 0.30F, y + size * 0.26F, size * 0.22F,
                    size * 0.22F));
            g2.dispose();
        }

        @Override
        public void paintFocus(Graphics g) {
            // Рамка фокуса не нужна: бегунок и так светится.
        }
    }

    // ─────────────────────────── вспомогательное ───────────────────────────

    /** Цвет с заданной прозрачностью. */
    public static Color fade(Color color, float alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(255 * alpha));
    }

    /** Смешивает два цвета. */
    public static Color mix(Color from, Color to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * clamped),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * clamped),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * clamped));
    }

    /**
     * Упругая кривая: сначала перелёт за цель, потом возврат.
     * Именно из-за неё кнопки «пружинят» при нажатии.
     */
    static float overshoot(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        float c = 1.9F;
        float p = clamped - 1.0F;
        return 1.0F + (c + 1.0F) * p * p * p + c * p * p;
    }

    /** Кнопка из стекла: градиент, скругление, упругое нажатие. */
    private static final class GlassButton extends JButton implements Animated {
        private final Color base;
        private final Color hover;
        private float hoverAmount;
        private float pressTime = 1.0F;

        GlassButton(String text, Color base, Color hover, Color foreground) {
            super(text);
            this.base = base;
            this.hover = hover;
            setFont(UiTheme.BUTTON_FONT);
            setForeground(foreground);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent event) {
                    register(GlassButton.this);
                }

                @Override
                public void mouseExited(MouseEvent event) {
                    register(GlassButton.this);
                }

                @Override
                public void mousePressed(MouseEvent event) {
                    pressTime = 0.0F;
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
            this.pressTime = Math.min(1.0F, this.pressTime + TICK_MS / 260.0F);
            return (inside && this.hoverAmount < 1.0F)
                    || (!inside && this.hoverAmount > 0.0F)
                    || this.pressTime < 1.0F;
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

            // Нажатие: кнопка сжимается, а отпускание догоняет цель с перелётом.
            float press = 1.0F - 0.10F * (1.0F - overshoot(this.pressTime));
            float inset = (1.0F - press) * getHeight() * 0.5F;
            float radius = Math.min(BUTTON_RADIUS, (getHeight() - inset * 2) / 2.0F);
            float x = inset;
            float y = inset;
            float width = getWidth() - inset * 2;
            float height = getHeight() - inset * 2;

            Color top = mix(this.base, this.hover, this.hoverAmount);
            Color bottom = mix(this.base, this.hover, this.hoverAmount * 0.65F);
            // Свечение под кнопкой: заметно при наведении.
            if (this.hoverAmount > 0.01F) {
                g.setColor(fade(this.hover, 0.22F * this.hoverAmount));
                g.fill(new RoundRectangle2D.Float(x - 3, y - 2, width + 6, height + 5, radius + 4, radius + 4));
            }
            g.setPaint(new java.awt.GradientPaint(x, y, top, x, y + height, bottom));
            g.fill(new RoundRectangle2D.Float(x, y, width, height, radius, radius));
            g.setColor(fade(Color.WHITE, 0.16F + 0.12F * this.hoverAmount));
            g.fill(new RoundRectangle2D.Float(x + 2, y + 2, width - 4, Math.max(2, height * 0.32F),
                    radius - 3, radius - 3));
            if (!isEnabled()) {
                g.setColor(fade(UiTheme.BACKGROUND_BOTTOM, 0.55F));
                g.fill(new RoundRectangle2D.Float(x, y, width, height, radius, radius));
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    /** Ячейка таблицы: мягкий фон строки, состояние — своим цветом. */
    private static class CellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            Component result = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            result.setFont(UiTheme.BASE);
            result.setForeground(UiTheme.TEXT);
            if (!selected) {
                result.setBackground(row % 2 == 0 ? fade(UiTheme.CARD_BOTTOM, 0.85F) : UiTheme.CARD_BOTTOM);
            }
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            String text = String.valueOf(value);
            if ("установлен".equals(text) || "готов".equals(text)) {
                result.setForeground(UiTheme.SUCCESS);
            } else if ("отсутствует".equals(text) || "ошибка".equals(text)) {
                result.setForeground(UiTheme.DANGER);
            } else if ("устарел".equals(text)) {
                result.setForeground(UiTheme.WARNING);
            }
            return result;
        }
    }

    /** Вкладки-«таблетки»: выбранная подсвечена градиентом. */
    private static final class PillTabs extends BasicTabbedPaneUI {
        @Override
        protected void installDefaults() {
            super.installDefaults();
            this.contentBorderInsets = new Insets(0, 0, 0, 0);
            this.tabAreaInsets = new Insets(6, 6, 6, 6);
            this.selectedTabPadInsets = new Insets(0, 0, 0, 0);
            this.tabInsets = new Insets(8, 18, 8, 18);
        }

        @Override
        protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
            return 38;
        }

        @Override
        protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
            return super.calculateTabWidth(tabPlacement, tabIndex, metrics) + 26;
        }

        @Override
        protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int width,
                                          int height, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (isSelected) {
                g2.setPaint(new java.awt.GradientPaint(x, y, UiTheme.ACCENT, x + width, y + height,
                        UiTheme.ACCENT_2));
            } else {
                g2.setColor(fade(UiTheme.CARD_TOP, 0.85F));
            }
            g2.fill(new RoundRectangle2D.Float(x + 2, y + 3, width - 4, height - 6, height - 6, height - 6));
            g2.dispose();
        }

        @Override
        protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int width, int height,
                                      boolean isSelected) {
            // Рамка не нужна: форму задаёт заливка.
        }

        @Override
        protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
            // Содержимое лежит на общем фоне, без рамки.
        }

        @Override
        protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex,
                                           Rectangle iconRect, Rectangle textRect, boolean isSelected) {
            // Рамка фокуса портит «стекло».
        }

        @Override
        protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex,
                                 String title, Rectangle textRect, boolean isSelected) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(isSelected ? UiTheme.TAB_BOLD : UiTheme.TAB);
            g2.setColor(isSelected ? UiTheme.ON_ACCENT : UiTheme.TEXT_MUTED);
            FontMetrics own = g2.getFontMetrics();
            int x = textRect.x + (textRect.width - own.stringWidth(title)) / 2;
            int baseline = textRect.y + (textRect.height + own.getAscent() - own.getDescent()) / 2;
            g2.drawString(title, x, baseline);
            g2.dispose();
        }
    }

    /** Скруглённая рамка-линия: обычная {@code LineBorder} не умеет углы. */
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
            g2.setStroke(new BasicStroke(1.2F));
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
