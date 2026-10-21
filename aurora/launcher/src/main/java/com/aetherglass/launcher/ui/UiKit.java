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
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;

/**
 * Оформление и 60 FPS анимации лаунчера и установщика AetherGlass (Matte Black + Neon Purple #AA3DFF):
 * <ul>
 *   <li>Живой анимированный фон: плавно дрейфующие неоновые облака сияния + парящие светящиеся частицы с параллаксом от курсора;</li>
 *   <li>Прямоугольные карточки со скруглёнными углами, матовым чёрным фоном ({@code #0B0C10}) и тонкой неоновой рамкой с плавной анимацией наведения и переключения;</li>
 *   <li>Кнопки с пульсирующим неоновым свечением, диагональным бликом (shimmer) и пружинящим откликом при клике;</li>
 *   <li>Плавное скольжение активного индикатора по вкладкам левого меню и анимированная неоновая полоса прогресса.</li>
 * </ul>
 */
public final class UiKit {

    /** Радиус скругления прямоугольных карточек (9px). */
    public static final int CARD_RADIUS = 9;

    /** Радиус скругления кнопок (8px). */
    public static final int BUTTON_RADIUS = 8;

    private static final int TICK_MS = 16; // ~60 FPS

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

    // ─────────────────────────── 60 FPS Анимированный фон (Aurora + Частицы) ───────────────────────────

    public static JPanel background() {
        return new Backdrop();
    }

    private static final class Backdrop extends JPanel implements Animated {
        private static final int NUM_PARTICLES = 42;
        private final float[] px = new float[NUM_PARTICLES];
        private final float[] py = new float[NUM_PARTICLES];
        private final float[] pSpeedY = new float[NUM_PARTICLES];
        private final float[] pPhase = new float[NUM_PARTICLES];
        private final float[] pSize = new float[NUM_PARTICLES];
        private final int[] pColorType = new int[NUM_PARTICLES];
        private float mouseX = -1f;
        private float mouseY = -1f;
        private float smoothMouseX = 0.5f;
        private float smoothMouseY = 0.5f;

        Backdrop() {
            super(new BorderLayout());
            setOpaque(true);
            Random rng = new Random(0xAE74E2L);
            for (int i = 0; i < NUM_PARTICLES; i++) {
                this.px[i] = rng.nextFloat();
                this.py[i] = rng.nextFloat();
                this.pSpeedY[i] = 0.012F + rng.nextFloat() * 0.026F;
                this.pPhase[i] = rng.nextFloat() * 6.28318F;
                this.pSize[i] = 1.6F + rng.nextFloat() * 2.8F;
                this.pColorType[i] = i % 3;
            }
            MouseMotionAdapter motion = new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    updateMouse(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    updateMouse(e);
                }

                private void updateMouse(MouseEvent e) {
                    if (getWidth() > 0 && getHeight() > 0) {
                        mouseX = e.getX() / (float) getWidth();
                        mouseY = e.getY() / (float) getHeight();
                    }
                }
            };
            addMouseMotionListener(motion);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            register(this);
        }

        @Override
        public boolean tick() {
            float dt = TICK_MS / 1000.0F;
            for (int i = 0; i < NUM_PARTICLES; i++) {
                this.py[i] -= this.pSpeedY[i] * dt;
                if (this.py[i] < -0.04F) {
                    this.py[i] = 1.04F;
                }
            }
            float targetMx = this.mouseX >= 0f ? this.mouseX : 0.5f;
            float targetMy = this.mouseY >= 0f ? this.mouseY : 0.5f;
            this.smoothMouseX += (targetMx - this.smoothMouseX) * 0.08f;
            this.smoothMouseY += (targetMy - this.smoothMouseY) * 0.08f;
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
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            float t = seconds();

            // 1. Глубокий матовый чёрный базовый градиент
            g.setPaint(new GradientPaint(0, 0, UiTheme.BACKGROUND_TOP, 0, height, UiTheme.BACKGROUND_BOTTOM));
            g.fillRect(0, 0, width, height);

            // 2. Плавно дрейфующие неоновые облака сияния (Aurora Orbs)
            paintAuroraOrb(g,
                    width * (0.22F + 0.14F * (float) Math.sin(t * 0.45F)),
                    height * (0.28F + 0.12F * (float) Math.cos(t * 0.35F)),
                    Math.max(width, height) * 0.42F,
                    new Color(0xAA, 0x3D, 0xFF, 28));

            paintAuroraOrb(g,
                    width * (0.78F + 0.12F * (float) Math.cos(t * 0.38F + 1.4F)),
                    height * (0.72F + 0.14F * (float) Math.sin(t * 0.48F + 0.8F)),
                    Math.max(width, height) * 0.38F,
                    new Color(0x93, 0x33, 0xEA, 24));

            // 3. Мягкий интерактивный ореол за курсором мыши
            paintAuroraOrb(g,
                    width * this.smoothMouseX,
                    height * this.smoothMouseY,
                    Math.max(width, height) * 0.28F,
                    new Color(0xC0, 0x84, 0xFC, 20));

            // 4. Парящие светящиеся неоновые частицы с мягким параллаксом
            float parallaxX = (this.smoothMouseX - 0.5F) * -18.0F;
            float parallaxY = (this.smoothMouseY - 0.5F) * -12.0F;
            for (int i = 0; i < NUM_PARTICLES; i++) {
                float sway = (float) Math.sin(t * 1.3F + this.pPhase[i]) * 12.0F;
                float cx = this.px[i] * width + sway + parallaxX * (0.4F + 0.6F * (this.pSize[i] / 4.4F));
                float cy = this.py[i] * height + parallaxY * (0.4F + 0.6F * (this.pSize[i] / 4.4F));
                float pulse = 0.45F + 0.55F * (0.5F + 0.5F * (float) Math.sin(t * 2.4F + this.pPhase[i] * 1.7F));
                float sz = this.pSize[i];

                Color baseCol = switch (this.pColorType[i]) {
                    case 0 -> UiTheme.ACCENT;
                    case 1 -> UiTheme.ACCENT_3;
                    default -> Color.WHITE;
                };

                // Внешний мягкий ореол частицы
                g.setColor(fade(baseCol, 0.16F * pulse));
                float haloSz = sz * 2.8F;
                g.fill(new Ellipse2D.Float(cx - haloSz * 0.5F, cy - haloSz * 0.5F, haloSz, haloSz));

                // Яркое ядро частицы
                g.setColor(fade(baseCol, 0.68F * pulse));
                g.fill(new Ellipse2D.Float(cx - sz * 0.5F, cy - sz * 0.5F, sz, sz));
            }

            g.dispose();
        }

        private static void paintAuroraOrb(Graphics2D g, float cx, float cy, float radius, Color centerColor) {
            if (radius <= 4.0F) {
                return;
            }
            RadialGradientPaint rgp = new RadialGradientPaint(
                    new Point2D.Float(cx, cy),
                    radius,
                    new float[]{0.0F, 0.55F, 1.0F},
                    new Color[]{
                            centerColor,
                            new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(),
                                    Math.max(0, centerColor.getAlpha() / 3)),
                            new Color(centerColor.getRed(), centerColor.getGreen(), centerColor.getBlue(), 0)
                    });
            g.setPaint(rgp);
            int r = Math.round(radius);
            g.fillOval(Math.round(cx) - r, Math.round(cy) - r, r * 2, r * 2);
        }
    }

    /**
     * Рисует матовую чёрную прямоугольную карточку ({@code #0B0C10}) со скруглёнными углами
     * и тонкой неоновой рамкой ({@code 1.1px}) + мягким внешним свечением.
     */
    public static void paintMatteNeonRect(Graphics2D g, int x, int y, int w, int h, int radius, float glowStrength) {
        if (w <= 4 || h <= 4) {
            return;
        }
        float glow = Math.max(0.0F, Math.min(1.0F, glowStrength));

        // 1. Деликатное внешнее неоновое свечение (glow) по периметру
        if (glow > 0.015F) {
            for (int layer = 3; layer >= 1; layer--) {
                float alpha = (0.048F * (4 - layer)) * glow;
                g.setColor(fade(UiTheme.ACCENT, alpha));
                g.setStroke(new BasicStroke(layer * 1.15F));
                g.draw(new RoundRectangle2D.Float(
                        x + 3 - layer, y + 3 - layer,
                        w - 6 + layer * 2, h - 6 + layer * 2,
                        radius + layer, radius + layer));
            }
        }

        // 2. Фон: матовый чёрный прямоугольник (#0B0C10) с едва заметным фиолетовым отливом при активности
        Shape body = new RoundRectangle2D.Float(x + 3, y + 3, w - 6, h - 6, radius, radius);
        if (glow > 0.05F) {
            Color topTint = mix(UiTheme.CARD_TOP, UiTheme.ACCENT, 0.06F * glow);
            g.setPaint(new GradientPaint(x, y + 3, topTint, x, y + h - 3, UiTheme.CARD_BOTTOM));
        } else {
            g.setColor(UiTheme.CARD_TOP);
        }
        g.fill(body);

        // 3. Тонкая чёткая рамка по периметру (1.1px)
        if (glow > 0.015F) {
            Color borderCol = mix(new Color(255, 255, 255, 40), UiTheme.ACCENT, Math.min(1.0F, 0.35F + 0.65F * glow));
            g.setColor(fade(borderCol, 0.30F + 0.68F * glow));
            g.setStroke(new BasicStroke(1.15F));
            g.draw(body);
        } else {
            g.setColor(new Color(255, 255, 255, 24));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(body);
        }
    }

    /**
     * Компактная прямоугольная карточка с плавной 60 FPS анимацией наведения и переключения.
     */
    public static JPanel matteNeonGridCard(String title, String description,
                                           BooleanSupplier isEnabled, Runnable onLeftClick) {
        return new AnimatedGridCard(title, description, isEnabled, onLeftClick);
    }

    private static final class AnimatedGridCard extends JPanel implements Animated {
        private final BooleanSupplier isEnabled;
        private float hoverAnim;
        private float activeAnim;
        private float pressAnim;
        private boolean hovered;

        AnimatedGridCard(String title, String description, BooleanSupplier isEnabled, Runnable onLeftClick) {
            super(new BorderLayout(0, 1));
            this.isEnabled = isEnabled;
            boolean initial = isEnabled != null && isEnabled.getAsBoolean();
            this.activeAnim = initial ? 1.0F : 0.0F;

            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(7, 22, 7, 10));

            JLabel titleLbl = new JLabel(title);
            titleLbl.setFont(UiTheme.SMALL_BOLD);
            titleLbl.setForeground(UiTheme.TEXT);

            JLabel descLbl = new JLabel(description);
            descLbl.setFont(UiTheme.SMALL);
            descLbl.setForeground(UiTheme.TEXT_MUTED);

            add(titleLbl, BorderLayout.NORTH);
            add(descLbl, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    register(AnimatedGridCard.this);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    register(AnimatedGridCard.this);
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) {
                        pressAnim = 1.0F;
                        if (onLeftClick != null) {
                            onLeftClick.run();
                        }
                        register(AnimatedGridCard.this);
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            register(this);
        }

        @Override
        public boolean tick() {
            boolean active = this.isEnabled != null && this.isEnabled.getAsBoolean();
            float targetHover = this.hovered ? 1.0F : 0.0F;
            float targetActive = active ? 1.0F : 0.0F;

            this.hoverAnim += (targetHover - this.hoverAnim) * 0.22F;
            this.activeAnim += (targetActive - this.activeAnim) * 0.20F;
            this.pressAnim *= 0.78F;

            if (Math.abs(targetHover - this.hoverAnim) < 0.008F) {
                this.hoverAnim = targetHover;
            }
            if (Math.abs(targetActive - this.activeAnim) < 0.008F) {
                this.activeAnim = targetActive;
            }
            if (this.pressAnim < 0.01F) {
                this.pressAnim = 0.0F;
            }
            return this.hoverAnim != targetHover || this.activeAnim != targetActive || this.pressAnim > 0.0F;
        }

        @Override
        public void repaint() {
            repaint(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean active = this.isEnabled != null && this.isEnabled.getAsBoolean();
            if (active && this.activeAnim < 0.05F) {
                register(this);
            }
            float glow = Math.max(this.activeAnim, this.hoverAnim * 0.65F);
            paintMatteNeonRect(g, 0, 0, getWidth(), getHeight(), 8, glow);

            // Плавный неоновый индикатор слева
            Color dotCol = mix(new Color(0x3E, 0x46, 0x58), UiTheme.ACCENT, Math.max(this.activeAnim, this.hoverAnim * 0.5F));
            if (this.activeAnim > 0.05F) {
                g.setColor(fade(UiTheme.ACCENT, 0.32F * this.activeAnim));
                g.fill(new Ellipse2D.Float(9.0F, 10.0F, 10.0F, 10.0F));
            }
            g.setColor(dotCol);
            g.fill(new Ellipse2D.Float(11.0F, 12.0F, 6.0F, 6.0F));
            g.dispose();
        }
    }

    /** Карточка-контейнер: матовый чёрный прямоугольник со скруглением 9px и плавной подсветкой при наведении. */
    public static JPanel card(String title, JComponent content) {
        return card(title, content, false);
    }

    public static JPanel card(String title, JComponent content, boolean neonOutline) {
        return new AnimatedContainerCard(title, content, neonOutline);
    }

    private static final class AnimatedContainerCard extends JPanel implements Animated {
        private final boolean neonOutline;
        private float hoverAnim;
        private boolean hovered;

        AnimatedContainerCard(String title, JComponent content, boolean neonOutline) {
            super(new BorderLayout(0, 5));
            this.neonOutline = neonOutline;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 11, 9, 11));
            if (title != null && !title.isBlank()) {
                add(heading(title), BorderLayout.NORTH);
            }
            add(content, BorderLayout.CENTER);

            MouseAdapter hoverTracker = new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovered = true;
                    register(AnimatedContainerCard.this);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    Point p = e.getPoint();
                    if (p.x <= 0 || p.y <= 0 || p.x >= getWidth() || p.y >= getHeight()) {
                        hovered = false;
                        register(AnimatedContainerCard.this);
                    }
                }
            };
            addMouseListener(hoverTracker);
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (this.neonOutline) {
                register(this);
            }
        }

        @Override
        public boolean tick() {
            float target = this.hovered ? 1.0F : 0.0F;
            this.hoverAnim += (target - this.hoverAnim) * 0.18F;
            if (Math.abs(target - this.hoverAnim) < 0.008F) {
                this.hoverAnim = target;
            }
            return this.neonOutline ? isShowing() : (this.hoverAnim != target);
        }

        @Override
        public void repaint() {
            repaint(0, 0, getWidth(), getHeight());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float pulse = this.neonOutline
                    ? (0.78F + 0.22F * (0.5F + 0.5F * (float) Math.sin(seconds() * 2.6F)))
                    : (0.18F + 0.52F * this.hoverAnim);
            paintMatteNeonRect(g, 0, 0, getWidth(), getHeight(), CARD_RADIUS, pulse);
            g.dispose();
        }
    }

    /**
     * Карточка «вопрос — ответ» для FAQ: вопрос жирным сверху, ответ мелким
     * шрифтом под ним. Никаких аккордеонов — всё видно сразу, карточки только
     * подсвечиваются при наведении.
     */
    public static JPanel faqCard(String question, String answer) {
        JPanel card = card(null, new JPanel(), false);
        card.removeAll();
        card.setLayout(new BorderLayout(0, 5));
        card.setBorder(BorderFactory.createEmptyBorder(11, 13, 12, 14));

        JLabel questionLabel = new JLabel(question);
        questionLabel.setFont(UiTheme.FAQ_QUESTION);
        questionLabel.setForeground(UiTheme.TEXT);

        WrappingText answerLabel = new WrappingText(answer);
        answerLabel.setFont(UiTheme.SMALL);
        answerLabel.setForeground(UiTheme.TEXT_MUTED);
        answerLabel.setAlignmentY(Component.TOP_ALIGNMENT);

        JPanel questionRow = new JPanel(new BorderLayout(0, 0));
        questionRow.setOpaque(false);
        questionRow.add(neonDot(), BorderLayout.WEST);
        questionRow.add(questionLabel, BorderLayout.CENTER);

        card.add(questionRow, BorderLayout.NORTH);
        card.add(answerLabel, BorderLayout.CENTER);
        return card;
    }

    /**
     * Текст с переносом по фактической ширине карточки.
     *
     * <p>Почему не {@code JLabel} с HTML: HTML-метка сообщает «мне нужно 900 px»,
     * и горизонтальная прокрутка в ленте FAQ появляется из-за самого же переноса.
     * Здесь ширина в {@code getPreferredSize()} жёстко маленькая — родитель
     * растягивает текст на всю карточку, а высота считается по числу строк при
     * этой ширине. Ничего не «прыгает» и не появляется лишних полос прокрутки.</p>
     */
    private static final class WrappingText extends javax.swing.JTextArea {
        WrappingText(String text) {
            super(text);
            setEditable(false);
            setFocusable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setBorder(BorderFactory.createEmptyBorder());
            setRows(1);
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth() > 40 ? getWidth() : 640;
            return new Dimension(80, measureHeight(getText(), getFontMetrics(getFont()), width));
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        /** Число строк при переносе по словам × высота строки. */
        static int measureHeight(String text, java.awt.FontMetrics metrics, int width) {
            int lineHeight = metrics.getHeight();
            int lines = 0;
            for (String paragraph : text.split("\n", -1)) {
                int current = 0;
                int count = 1;
                for (String word : paragraph.split(" ")) {
                    int wordWidth = metrics.stringWidth(word + " ");
                    if (current > 0 && current + wordWidth > width) {
                        count++;
                        current = wordWidth;
                    } else {
                        current += wordWidth;
                    }
                }
                lines += count;
            }
            return Math.max(lineHeight, lines * lineHeight + 2);
        }
    }

    /** Маленькая неоновая точка-маркер для строк FAQ. */
    private static JComponent neonDot() {
        JComponent dot = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = (getHeight() - 6) / 2;
                g.setColor(fade(UiTheme.ACCENT, 0.30F));
                g.fillOval(0, cy - 3, 10, 10);
                g.setColor(UiTheme.ACCENT);
                g.fillOval(2, cy, 5, 5);
                g.dispose();
            }
        };
        dot.setOpaque(false);
        dot.setPreferredSize(new Dimension(14, 16));
        return dot;
    }

    // ─────────────────────────── Текст ───────────────────────────

    public static JLabel heading(String text) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cy = (getHeight() - 5) / 2;
                g.setColor(fade(UiTheme.ACCENT, 0.32F));
                g.fillOval(-2, cy - 2, 9, 9);
                g.setColor(UiTheme.ACCENT);
                g.fillOval(0, cy, 5, 5);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
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
                g.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
                g.setColor(fade(color, 0.85F));
                g.setStroke(new BasicStroke(1.1F));
                g.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, 8, 8));
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

    // ─────────────────────────── Кнопки (Matte Black + Neon + Shimmer) ───────────────────────────

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

    // ─────────────────────────── Анимированная полоса прогресса и элементы ───────────────────────────

    public static JProgressBar progress() {
        return new AnimatedProgressBar();
    }

    private static final class AnimatedProgressBar extends JProgressBar implements Animated {
        private float smoothRatio;

        AnimatedProgressBar() {
            super(0, 100);
            setOpaque(false);
            setStringPainted(true);
            setString("");
            setPreferredSize(new Dimension(10, 14));
            setBorder(BorderFactory.createEmptyBorder());
        }

        @Override
        public void addNotify() {
            super.addNotify();
            register(this);
        }

        @Override
        public boolean tick() {
            float target = isIndeterminate() ? 1.0F : (float) Math.max(0.0, Math.min(1.0, getPercentComplete()));
            this.smoothRatio += (target - this.smoothRatio) * 0.18F;
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
            int w = getWidth();
            int h = getHeight();
            int radius = 7;

            g.setColor(UiTheme.TRACK);
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
            g.setColor(new Color(255, 255, 255, 20));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, w - 1, h - 1, radius, radius));

            float t = seconds();
            if (isIndeterminate()) {
                int barW = Math.max(48, w / 3);
                float wave = (t * 0.85F) % 1.0F;
                int bx = Math.round((w + barW) * wave) - barW;
                Shape clip = g.getClip();
                g.clip(new RoundRectangle2D.Float(0, 0, w, h, radius, radius));
                g.setPaint(new GradientPaint(bx, 0, fade(UiTheme.ACCENT, 0.15F),
                        bx + barW * 0.5F, 0, UiTheme.ACCENT_3, true));
                g.fill(new RoundRectangle2D.Float(bx, 0, barW, h, radius, radius));
                g.setClip(clip);
            } else {
                int filled = Math.round(w * this.smoothRatio);
                if (filled > 2) {
                    Shape fillShape = new RoundRectangle2D.Float(0, 0, filled, h, radius, radius);
                    g.setPaint(new GradientPaint(0, 0, UiTheme.ACCENT_2, filled, 0, UiTheme.ACCENT));
                    g.fill(fillShape);

                    // Бегущий неоновый блик по заполненной части
                    Shape oldClip = g.getClip();
                    g.clip(fillShape);
                    float shimmerX = ((t * 0.75F) % 1.0F) * (filled + 80) - 40;
                    g.setPaint(new GradientPaint(shimmerX - 24, 0, new Color(255, 255, 255, 0),
                            shimmerX, 0, new Color(255, 255, 255, 90), true));
                    g.fillRect(Math.round(shimmerX - 24), 0, 48, h);
                    g.setClip(oldClip);
                }
            }

            if (isStringPainted()) {
                String text = getString();
                if (text != null && !text.isEmpty()) {
                    g.setFont(UiTheme.SMALL_BOLD);
                    FontMetrics metrics = g.getFontMetrics();
                    g.setColor(UiTheme.TEXT);
                    g.drawString(text, (w - metrics.stringWidth(text)) / 2,
                            (h + metrics.getAscent() - metrics.getDescent()) / 2);
                }
            }
            g.dispose();
        }
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
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    public static void styleTabs(javax.swing.JTabbedPane tabs) {
        tabs.setOpaque(false);
        tabs.setFont(UiTheme.TAB);
        tabs.setForeground(UiTheme.TEXT_MUTED);
        tabs.setBackground(UiTheme.BACKGROUND_TOP);
        tabs.setUI(new PillTabs(tabs));
    }

    public static void styleTable(JTable table) {
        table.setOpaque(false);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 4));
        table.setFont(UiTheme.BASE);
        table.setForeground(UiTheme.TEXT);
        table.setBackground(UiTheme.CARD_BOTTOM);
        table.setSelectionBackground(fade(UiTheme.ACCENT, 0.22F));
        table.setSelectionForeground(UiTheme.TEXT);
        table.setDefaultRenderer(Object.class, new CellRenderer());
        JTableHeader header = table.getTableHeader();
        header.setOpaque(false);
        header.setFont(UiTheme.SMALL_BOLD);
        header.setBackground(UiTheme.CARD_TOP);
        header.setForeground(UiTheme.TEXT_MUTED);
        header.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 28));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table2, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                Component result = super.getTableCellRendererComponent(table2, value, selected, focus, row, column);
                result.setFont(UiTheme.SMALL_BOLD);
                result.setBackground(UiTheme.CARD_TOP);
                result.setForeground(UiTheme.TEXT_MUTED);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
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
                g2.setPaint(new GradientPaint(trackRect.x, y, UiTheme.ACCENT_2, trackRect.x + filled, y, UiTheme.ACCENT));
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

    /** Матовая чёрная прямоугольная кнопка с неоновой обводкой, дыханием свечения и бегущим бликом (shimmer). */
    private static final class GlassButton extends JButton implements Animated {
        private final boolean primaryNeon;
        private final boolean dangerNeon;
        private float hoverAmount;
        private float pressAmount;

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

                @Override
                public void mousePressed(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON1) {
                        pressAmount = 1.0F;
                        register(GlassButton.this);
                    }
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (this.primaryNeon) {
                register(this);
            }
        }

        @Override
        public boolean tick() {
            boolean inside = getModel().isRollover();
            float target = inside ? 1.0F : 0.0F;
            this.hoverAmount += (target - this.hoverAmount) * 0.22F;
            if (Math.abs(target - this.hoverAmount) < 0.008F) {
                this.hoverAmount = target;
            }
            this.pressAmount *= 0.76F;
            if (this.pressAmount < 0.01F) {
                this.pressAmount = 0.0F;
            }
            return this.primaryNeon ? isShowing() : (this.hoverAmount != target || this.pressAmount > 0.0F);
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
            float t = seconds();
            Color neonCol = this.dangerNeon ? UiTheme.DANGER : UiTheme.ACCENT;

            float pulse = 0.80F + 0.20F * (0.5F + 0.5F * (float) Math.sin(t * 3.2F));
            float glow = this.primaryNeon
                    ? Math.min(1.0F, pulse + this.hoverAmount * 0.25F)
                    : (this.dangerNeon ? (0.65F + 0.35F * this.hoverAmount) : (0.15F + 0.85F * this.hoverAmount));

            int inset = Math.round(3 + this.pressAmount * 1.4F);
            int bw = w - inset * 2;
            int bh = h - inset * 2;

            if (glow > 0.02F) {
                for (int layer = 3; layer >= 1; layer--) {
                    g.setColor(fade(neonCol, 0.075F * (4 - layer) * glow));
                    g.setStroke(new BasicStroke(layer * 1.25F));
                    g.draw(new RoundRectangle2D.Float(inset - layer, inset - layer,
                            bw + layer * 2, bh + layer * 2,
                            BUTTON_RADIUS + layer, BUTTON_RADIUS + layer));
                }
            }

            Shape body = new RoundRectangle2D.Float(inset, inset, bw, bh, BUTTON_RADIUS, BUTTON_RADIUS);
            if (this.primaryNeon || this.hoverAmount > 0.02F) {
                float fillTint = this.primaryNeon ? (0.18F + 0.14F * this.hoverAmount) : (0.12F * this.hoverAmount);
                Color topC = mix(UiTheme.CARD_TOP, neonCol, fillTint);
                Color botC = mix(UiTheme.CARD_BOTTOM, neonCol, fillTint * 0.4F);
                g.setPaint(new GradientPaint(0, inset, topC, 0, inset + bh, botC));
            } else {
                g.setColor(UiTheme.CARD_TOP);
            }
            g.fill(body);

            // Диагональный бегущий блик (shimmer) на главных кнопках и при наведении
            if (this.primaryNeon || this.hoverAmount > 0.1F) {
                Shape oldClip = g.getClip();
                g.clip(body);
                float cycle = (t * 0.55F) % 1.6F;
                if (cycle <= 1.0F) {
                    float sx = -40 + cycle * (bw + 80);
                    int alpha = Math.round((this.primaryNeon ? 46 : 32) * (0.5F + 0.5F * this.hoverAmount));
                    g.setPaint(new GradientPaint(sx - 22, inset, new Color(255, 255, 255, 0),
                            sx, inset + bh * 0.5F, new Color(255, 255, 255, alpha), true));
                    g.fillRect(Math.round(sx - 22), inset, 44, bh);
                }
                g.setClip(oldClip);
            }

            // Тонкая неоновая рамка (1.2px)
            g.setColor(fade(neonCol, 0.35F + 0.65F * glow));
            g.setStroke(new BasicStroke(1.2F));
            g.draw(body);

            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final java.util.Map<String, java.awt.image.BufferedImage> ICON_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> ICON_LOADING =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static void preloadModrinthIcon(String key, String iconUrl, Component repaintTarget) {
        if (key == null || iconUrl == null || iconUrl.isBlank()
                || ICON_CACHE.containsKey(key) || !ICON_LOADING.add(key)) {
            return;
        }
        Thread loader = new Thread(() -> {
            try (var in = java.net.URI.create(iconUrl).toURL().openStream()) {
                var img = javax.imageio.ImageIO.read(in);
                if (img != null) {
                    ICON_CACHE.put(key, img);
                    if (repaintTarget != null) {
                        javax.swing.SwingUtilities.invokeLater(repaintTarget::repaint);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                ICON_LOADING.remove(key);
            }
        }, "modrinth-icon-" + key);
        loader.setDaemon(true);
        loader.start();
    }

    private static class CellRenderer extends DefaultTableCellRenderer {
        private int currentColumn;
        private int currentRow;
        private boolean isRowSelected;
        private String cellText = "";

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus,
                                                       int row, int column) {
            this.currentColumn = column;
            this.currentRow = row;
            this.isRowSelected = selected;
            this.cellText = value == null ? "" : String.valueOf(value);

            Component result = super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            result.setFont(column == 0 ? UiTheme.HEADING : UiTheme.BASE);
            result.setForeground(UiTheme.TEXT);
            if (!selected) {
                result.setBackground(row % 2 == 0 ? new Color(0x0E, 0x10, 0x16) : UiTheme.CARD_BOTTOM);
            }
            setBorder(BorderFactory.createEmptyBorder(0, column == 0 ? 44 : 10, 0, 10));
            String text = this.cellText;
            if ("установлен".equals(text) || text.startsWith("готов") || text.startsWith("Включён")) {
                result.setForeground(UiTheme.SUCCESS);
                result.setFont(UiTheme.SMALL_BOLD);
            } else if ("отсутствует".equals(text) || text.startsWith("ошибка") || text.startsWith("повреждён")) {
                result.setForeground(UiTheme.DANGER);
                result.setFont(UiTheme.SMALL_BOLD);
            } else if (text.startsWith("отключён") || "устарел".equals(text)) {
                result.setForeground(UiTheme.WARNING);
                result.setFont(UiTheme.SMALL_BOLD);
            } else if (column == 2 && (text.endsWith("M") || text.endsWith("K"))) {
                result.setForeground(UiTheme.ACCENT_3);
                result.setFont(UiTheme.SMALL_BOLD);
            }
            return result;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (this.currentColumn == 0 && !this.cellText.isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int size = 26;
                int ix = 10;
                int iy = Math.max(2, (getHeight() - size) / 2);
                paintProjectIconBadge(g2, this.cellText, ix, iy, size);
                g2.dispose();
            }
        }
    }

    private static void paintProjectIconBadge(Graphics2D g2, String title, int x, int y, int size) {
        String lower = title.toLowerCase(java.util.Locale.ROOT);
        java.awt.image.BufferedImage cached = ICON_CACHE.get(lower);
        if (cached != null) {
            Shape oldClip = g2.getClip();
            g2.setClip(new RoundRectangle2D.Float(x, y, size, size, 7, 7));
            g2.drawImage(cached, x, y, size, size, null);
            g2.setClip(oldClip);
            g2.setColor(UiTheme.ACCENT);
            g2.setStroke(new BasicStroke(1.1F));
            g2.draw(new RoundRectangle2D.Float(x, y, size, size, 7, 7));
            return;
        }

        // Определяем цветовую тему и пиктограмму для конкретного мода / ресурспака / шейдера
        Color bgTop;
        Color bgBot;
        Color rim;
        int iconType; // 0=cube(Sodium), 1=iris, 2=bolt(Lithium), 3=scroll(Fabric), 4=creeper(RP), 5=grass(Faithful), 6=shader(Sun), 7=aether(A), 8=chip(Ferrite), 9=eye(Culling)

        if (lower.contains("aetherglass")) {
            bgTop = new Color(0x24, 0x10, 0x3D);
            bgBot = new Color(0x0E, 0x08, 0x1A);
            rim = new Color(0xAA, 0x3D, 0xFF);
            iconType = 7;
        } else if (lower.contains("sodium") || lower.contains("reeses")) {
            bgTop = new Color(0x09, 0x38, 0x28);
            bgBot = new Color(0x06, 0x1F, 0x17);
            rim = new Color(0x00, 0xF5, 0xA0);
            iconType = 0;
        } else if (lower.contains("iris") || lower.contains("complementary") || lower.contains("bsl")
                || lower.contains("solas") || lower.contains("bliss") || lower.contains("shader")
                || lower.contains("makeup") || lower.contains("voxels")) {
            bgTop = new Color(0x2E, 0x14, 0x4D);
            bgBot = new Color(0x12, 0x26, 0x4A);
            rim = new Color(0x00, 0xF2, 0xFF);
            iconType = lower.contains("iris") ? 1 : 6;
        } else if (lower.contains("fabric")) {
            bgTop = new Color(0x3D, 0x2E, 0x14);
            bgBot = new Color(0x21, 0x18, 0x0A);
            rim = new Color(0xFF, 0xC8, 0x57);
            iconType = 3;
        } else if (lower.contains("lithium") || lower.contains("immediatelyfast") || lower.contains("c2me")
                || lower.contains("krypton") || lower.contains("dynamic") || lower.contains("badoptimizations")) {
            bgTop = new Color(0x36, 0x15, 0x42);
            bgBot = new Color(0x1A, 0x0B, 0x24);
            rim = new Color(0xFF, 0x3D, 0xAE);
            iconType = 2;
        } else if (lower.contains("ferrite") || lower.contains("cloth") || lower.contains("mod menu") || lower.contains("modmenu")) {
            bgTop = new Color(0x3B, 0x1D, 0x12);
            bgBot = new Color(0x1F, 0x0E, 0x08);
            rim = new Color(0xFF, 0x8A, 0x3D);
            iconType = 8;
        } else if (lower.contains("culling") || lower.contains("zoom") || lower.contains("continuity") || lower.contains("xaero")) {
            bgTop = new Color(0x0C, 0x2D, 0x3E);
            bgBot = new Color(0x07, 0x17, 0x22);
            rim = new Color(0x00, 0xE5, 0xFF);
            iconType = 9;
        } else if (lower.contains("fresh") || lower.contains("animation") || lower.contains("skin")) {
            bgTop = new Color(0x14, 0x3B, 0x1C);
            bgBot = new Color(0x0B, 0x21, 0x10);
            rim = new Color(0x4A, 0xDE, 0x80);
            iconType = 4;
        } else if (lower.contains("faithful") || lower.contains("stay true") || lower.contains("bare bones")
                || lower.contains("vanilla") || lower.contains("reimagined") || lower.contains("dark") || lower.contains("fire") || lower.contains("fullbright")) {
            bgTop = new Color(0x1E, 0x36, 0x18);
            bgBot = new Color(0x2E, 0x1E, 0x12);
            rim = new Color(0x86, 0xEF, 0xAC);
            iconType = 5;
        } else {
            bgTop = new Color(0x1E, 0x16, 0x33);
            bgBot = new Color(0x0F, 0x0C, 0x1C);
            rim = UiTheme.ACCENT;
            iconType = Math.abs(lower.hashCode()) % 7;
        }

        g2.setPaint(new GradientPaint(x, y, bgTop, x, y + size, bgBot));
        g2.fill(new RoundRectangle2D.Float(x, y, size, size, 7, 7));

        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (iconType) {
            case 0 -> { // Куб-кристалл Sodium
                g2.setColor(rim);
                int[] px = {cx, x + size - 5, cx, x + 5};
                int[] py = {y + 4, cy, y + size - 4, cy};
                g2.fillPolygon(px, py, 4);
                g2.setColor(Color.WHITE);
                g2.fillOval(cx - 2, cy - 2, 4, 4);
            }
            case 1 -> { // Линза Iris Shaders
                g2.setColor(new Color(0xFF, 0x3D, 0xAE, 190));
                g2.fillOval(x + 4, y + 4, size - 8, size - 8);
                g2.setColor(rim);
                g2.setStroke(new BasicStroke(1.8F));
                g2.drawOval(x + 5, y + 5, size - 10, size - 10);
                g2.setColor(Color.WHITE);
                g2.fillOval(cx - 3, cy - 3, 6, 6);
            }
            case 2 -> { // Молния ускорения (Lithium / ImmediatelyFast)
                g2.setColor(rim);
                int[] bx = {cx + 2, x + 6, cx, cx - 2, x + size - 6, cx};
                int[] by = {y + 3, cy + 1, cy + 1, y + size - 3, cy - 1, cy - 1};
                g2.fillPolygon(bx, by, 6);
            }
            case 3 -> { // Эмблема свитка Fabric API
                g2.setColor(new Color(0xFD, 0xE6, 0x8A));
                g2.fillRoundRect(x + 6, y + 5, size - 12, size - 10, 4, 4);
                g2.setColor(new Color(0x92, 0x40, 0x0E));
                g2.drawLine(x + 9, y + 9, x + size - 9, y + 9);
                g2.drawLine(x + 9, y + 13, x + size - 9, y + 13);
                g2.drawLine(x + 9, y + 17, x + size - 11, y + 17);
            }
            case 4 -> { // Пиксель-арт Крипера (Fresh Animations)
                g2.setColor(new Color(0x4A, 0xDE, 0x80));
                g2.fillRect(x + 5, y + 5, size - 10, size - 10);
                g2.setColor(new Color(0x07, 0x17, 0x0B));
                g2.fillRect(x + 7, y + 8, 4, 4);
                g2.fillRect(x + size - 11, y + 8, 4, 4);
                g2.fillRect(cx - 2, y + 12, 4, 6);
                g2.fillRect(cx - 4, y + 15, 2, 4);
                g2.fillRect(cx + 2, y + 15, 2, 4);
            }
            case 5 -> { // Пиксель-арт блока травы (Faithful / Ресурспаки)
                g2.setColor(new Color(0x85, 0x4D, 0x27));
                g2.fillRect(x + 5, y + 10, size - 10, size - 15);
                g2.setColor(new Color(0x4A, 0xDE, 0x80));
                g2.fillRect(x + 5, y + 5, size - 10, 6);
                g2.fillRect(x + 7, y + 11, 3, 2);
                g2.fillRect(x + 14, y + 11, 4, 2);
            }
            case 6 -> { // Солнце и облака (Шейдерпак Iris)
                g2.setColor(new Color(0xFF, 0xB8, 0x4D));
                g2.fillOval(cx - 5, y + 5, 10, 10);
                g2.setColor(new Color(0xE0, 0xF2, 0xFE));
                g2.fillRoundRect(x + 5, y + 13, size - 10, 6, 5, 5);
            }
            case 7 -> { // Фирменная буква «A» AetherGlass
                g2.setColor(rim);
                g2.setStroke(new BasicStroke(2.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx, y + 5, x + 6, y + size - 5);
                g2.drawLine(cx, y + 5, x + size - 6, y + size - 5);
                g2.drawLine(x + 8, y + size - 9, x + size - 8, y + size - 9);
            }
            case 8 -> { // Чип ОЗУ (FerriteCore / Настройки)
                g2.setColor(rim);
                g2.fillRoundRect(x + 6, y + 6, size - 12, size - 12, 3, 3);
                g2.setColor(bgBot);
                g2.fillRect(x + 9, y + 9, size - 18, size - 18);
            }
            default -> { // Оптический радар (EntityCulling / Zoomify / Xaero)
                g2.setColor(rim);
                g2.setStroke(new BasicStroke(1.6F));
                g2.drawOval(x + 5, y + 7, size - 10, size - 14);
                g2.fillOval(cx - 3, cy - 3, 6, 6);
            }
        }

        g2.setColor(rim);
        g2.setStroke(new BasicStroke(1.15F));
        g2.draw(new RoundRectangle2D.Float(x, y, size, size, 7, 7));
    }

    /** Анимированные вкладки узкой левой панели навигации со скользящим неоновым индикатором. */
    private static final class PillTabs extends BasicTabbedPaneUI implements Animated {
        private final javax.swing.JTabbedPane host;
        private final float[] tabHover = new float[12];
        private int hoveredTab = -1;
        private float animIndicatorY = -1f;

        PillTabs(javax.swing.JTabbedPane host) {
            this.host = host;
            register(this);
            host.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int idx = tabForCoordinate(host, e.getX(), e.getY());
                    if (idx != hoveredTab) {
                        hoveredTab = idx;
                        register(PillTabs.this);
                    }
                }
            });
            host.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hoveredTab = -1;
                    register(PillTabs.this);
                }
            });
            host.addChangeListener(e -> register(this));
        }

        @Override
        public boolean tick() {
            boolean moving = false;
            int count = Math.min(this.tabHover.length, this.host.getTabCount());
            for (int i = 0; i < count; i++) {
                float target = (i == this.hoveredTab) ? 1.0F : 0.0F;
                this.tabHover[i] += (target - this.tabHover[i]) * 0.22F;
                if (Math.abs(target - this.tabHover[i]) > 0.008F) {
                    moving = true;
                } else {
                    this.tabHover[i] = target;
                }
            }
            int sel = this.host.getSelectedIndex();
            if (sel >= 0 && sel < this.rects.length && this.rects[sel] != null) {
                float targetY = this.rects[sel].y;
                if (this.animIndicatorY < 0f) {
                    this.animIndicatorY = targetY;
                } else {
                    this.animIndicatorY += (targetY - this.animIndicatorY) * 0.25F;
                    if (Math.abs(targetY - this.animIndicatorY) > 0.4F) {
                        moving = true;
                    } else {
                        this.animIndicatorY = targetY;
                    }
                }
            }
            return moving;
        }

        @Override
        public void repaint() {
            this.host.repaint();
        }

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
            float hov = (tabIndex >= 0 && tabIndex < this.tabHover.length) ? this.tabHover[tabIndex] : 0.0F;
            float glow = isSelected ? 1.0F : (hov * 0.55F);
            paintMatteNeonRect(g2, x, y, width, height, 8, glow);

            // Вертикальная неоновая полоска-акцент слева у выбранной вкладки (плавно скользящая)
            if (isSelected) {
                int pillY = (this.animIndicatorY >= 0f) ? Math.round(this.animIndicatorY) + 9 : y + 9;
                g2.setColor(UiTheme.ACCENT);
                g2.fill(new RoundRectangle2D.Float(x + 6, pillY, 3, height - 18, 3, 3));
            }
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
            float hov = (tabIndex >= 0 && tabIndex < this.tabHover.length) ? this.tabHover[tabIndex] : 0.0F;
            g2.setFont(isSelected ? UiTheme.TAB_BOLD : UiTheme.TAB);
            g2.setColor(isSelected ? UiTheme.TEXT : mix(UiTheme.TEXT_MUTED, UiTheme.TEXT, hov * 0.75F));
            FontMetrics own = g2.getFontMetrics();
            int x = textRect.x + 8 + Math.round(hov * 2.0F);
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

    /**
     * Фирменный 38×38 матовый чёрный бейдж со светящейся неоново-фиолетовой буквой «A» для шапки лаунчера.
     */
    public static JComponent neonLogoBadge() {
        return new NeonLogoBadge();
    }

    private static final class NeonLogoBadge extends JComponent implements Animated {
        NeonLogoBadge() {
            setOpaque(false);
            setPreferredSize(new Dimension(38, 38));
            setMinimumSize(new Dimension(38, 38));
            register(this);
        }

        @Override
        public boolean tick() {
            return isDisplayable();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            float t = seconds();
            float pulse = 0.5F + 0.5F * (float) Math.sin(t * 2.2F);

            // Внешнее мягкое неоновое свечение
            int glowAlpha = Math.max(0, Math.min(255, Math.round(28 + pulse * 26)));
            g2.setColor(new Color(UiTheme.ACCENT.getRed(), UiTheme.ACCENT.getGreen(), UiTheme.ACCENT.getBlue(), glowAlpha));
            g2.fill(new RoundRectangle2D.Float(1.0F, 1.0F, w - 2.0F, h - 2.0F, 11, 11));

            // Матовая чёрная основа (#0B0C10)
            g2.setColor(UiTheme.CARD_TOP);
            g2.fill(new RoundRectangle2D.Float(2.0F, 2.0F, w - 4.0F, h - 4.0F, 9, 9));

            // Тонкая неоновая рамка (1.2px)
            g2.setColor(mix(UiTheme.ACCENT, UiTheme.ACCENT_3, pulse * 0.45F));
            g2.setStroke(new BasicStroke(1.25F));
            g2.draw(new RoundRectangle2D.Float(2.0F, 2.0F, w - 4.0F, h - 4.0F, 9, 9));

            // Силуэт неоновой буквы «A» с градиентом
            float cx = w * 0.5F;
            float topY = 8.5F;
            float botY = h - 8.5F;
            float leftX = 10.5F;
            float rightX = w - 10.5F;

            g2.setPaint(new GradientPaint(leftX, topY, UiTheme.ACCENT_3, rightX, botY, UiTheme.ACCENT));
            g2.setStroke(new BasicStroke(3.0F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Float(cx, topY, leftX, botY));
            g2.draw(new java.awt.geom.Line2D.Float(cx, topY, rightX, botY));
            float barY = topY + (botY - topY) * 0.62F;
            g2.setStroke(new BasicStroke(2.4F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Float(leftX + 3.2F, barY, rightX - 3.2F, barY));

            g2.dispose();
        }
    }

    /**
     * Компактная информационная капсула (телеметрия ОЗУ / Диска / Профиля) для верхней панели лаунчера.
     */
    public static JPanel telemetryPill(String label, JLabel valueLabel, Color dotColor) {
        JPanel pill = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 3)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.CARD_TOP);
                g2.fill(new RoundRectangle2D.Float(0.5F, 0.5F, getWidth() - 1.0F, getHeight() - 1.0F, 8, 8));
                g2.setColor(new Color(UiTheme.ACCENT.getRed(), UiTheme.ACCENT.getGreen(), UiTheme.ACCENT.getBlue(), 95));
                g2.setStroke(new BasicStroke(1.0F));
                g2.draw(new RoundRectangle2D.Float(0.5F, 0.5F, getWidth() - 1.0F, getHeight() - 1.0F, 8, 8));
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        JComponent dot = new JComponent() {
            {
                setPreferredSize(new Dimension(7, 7));
                setOpaque(false);
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(dotColor);
                g2.fill(new Ellipse2D.Float(0.5F, 0.5F, 6.0F, 6.0F));
                g2.dispose();
            }
        };
        JLabel keyLbl = new JLabel(label);
        keyLbl.setFont(UiTheme.SMALL);
        keyLbl.setForeground(UiTheme.TEXT_MUTED);
        valueLabel.setFont(UiTheme.SMALL_BOLD);
        valueLabel.setForeground(UiTheme.TEXT);
        pill.add(dot);
        pill.add(keyLbl);
        pill.add(valueLabel);
        return pill;
    }

    /**
     * Переключатель неоновых тем в 1 клик (6 цветных кружков с подсветкой активной темы).
     */
    public static JPanel paletteSwitcher(java.util.function.Consumer<UiTheme.NeonPalette> onSelect) {
        JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 5, 2));
        bar.setOpaque(false);
        for (UiTheme.NeonPalette palette : UiTheme.NeonPalette.values()) {
            JComponent dot = new JComponent() {
                {
                    setPreferredSize(new Dimension(16, 16));
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    setToolTipText("Тема неона: " + palette.displayName() + " (синхронизируется с RSHIFT в игре)");
                    addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            UiTheme.applyPalette(palette);
                            if (onSelect != null) {
                                onSelect.accept(palette);
                            }
                            bar.repaint();
                        }
                    });
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean active = UiTheme.currentPalette() == palette;
                    if (active) {
                        g2.setColor(new Color(palette.accent().getRed(), palette.accent().getGreen(), palette.accent().getBlue(), 90));
                        g2.fill(new Ellipse2D.Float(0.0F, 0.0F, 16.0F, 16.0F));
                    }
                    g2.setColor(palette.accent());
                    g2.fill(new Ellipse2D.Float(3.0F, 3.0F, 10.0F, 10.0F));
                    if (active) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(1.3F));
                        g2.draw(new Ellipse2D.Float(1.5F, 1.5F, 13.0F, 13.0F));
                    }
                    g2.dispose();
                }
            };
            bar.add(dot);
        }
        return bar;
    }

    /**
     * 2D/3D-превью головы скина игрока с неоновой рамкой (генерирует стильный пиксель-арт аватар по нику).
     */
    public static JComponent skinAvatarBadge(java.util.function.Supplier<String> nickSupplier) {
        return new JComponent() {
            {
                setPreferredSize(new Dimension(32, 32));
                setMinimumSize(new Dimension(32, 32));
                setOpaque(false);
                setToolTipText("Превью скина текущего профиля");
            }

            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(UiTheme.CARD_TOP);
                g2.fill(new RoundRectangle2D.Float(0.5F, 0.5F, w - 1.0F, h - 1.0F, 8, 8));

                String nick = nickSupplier != null ? nickSupplier.get() : "Player";
                if (nick == null || nick.isBlank()) {
                    nick = "Steve";
                }
                int hash = Math.abs(nick.hashCode());
                Color hair = mix(UiTheme.ACCENT_2, new Color(0x28, 0x22, 0x38), 0.55F);
                Color skin = new Color(0xE2, 0xB8, 0x9B);
                Color eyes = UiTheme.ACCENT;

                int ox = (w - 20) / 2;
                int oy = (h - 20) / 2;
                // Основа лица 8×8 в масштабе 2.5px
                g2.setColor(skin);
                g2.fillRect(ox, oy, 20, 20);
                // Волосы / шлем
                g2.setColor(hair);
                g2.fillRect(ox, oy, 20, 6);
                g2.fillRect(ox, oy + 6, 3, 4);
                g2.fillRect(ox + 17, oy + 6, 3, 4);
                // Светящиеся неоновые глаза
                g2.setColor(Color.WHITE);
                g2.fillRect(ox + 3, oy + 9, 5, 3);
                g2.fillRect(ox + 12, oy + 9, 5, 3);
                g2.setColor(eyes);
                g2.fillRect(ox + 5, oy + 9, 3, 3);
                g2.fillRect(ox + 12, oy + 9, 3, 3);
                // Нос/рот
                g2.setColor(new Color(0x9C, 0x6E, 0x54));
                g2.fillRect(ox + 8, oy + 14, 4, 2);
                if ((hash & 1) == 0) {
                    // Неоновый визор/ободок
                    g2.setColor(new Color(UiTheme.ACCENT.getRed(), UiTheme.ACCENT.getGreen(), UiTheme.ACCENT.getBlue(), 90));
                    g2.fillRect(ox + 2, oy + 8, 16, 5);
                }

                g2.setColor(UiTheme.ACCENT);
                g2.setStroke(new BasicStroke(1.2F));
                g2.draw(new RoundRectangle2D.Float(1.0F, 1.0F, w - 2.0F, h - 2.0F, 8, 8));
                g2.dispose();
            }
        };
    }

    /**
     * Карточка-миниатюра скриншота с отрисовкой самого изображения внутри неоновой рамки.
     */
    public static JComponent screenshotThumbnailCard(java.nio.file.Path imageFile,
                                                     String title,
                                                     String subtitle,
                                                     Runnable onClick) {
        java.awt.image.BufferedImage loaded = null;
        try {
            loaded = javax.imageio.ImageIO.read(imageFile.toFile());
        } catch (Exception ignored) {
        }
        final java.awt.image.BufferedImage previewImg = loaded;
        return new JComponent() {
            private float hover = 0.0F;

            {
                setPreferredSize(new Dimension(280, 195));
                setMinimumSize(new Dimension(240, 175));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = 1.0F;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = 0.0F;
                        repaint();
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (onClick != null) {
                            onClick.run();
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g2.setColor(UiTheme.CARD_TOP);
                g2.fill(new RoundRectangle2D.Float(1.0F, 1.0F, w - 2.0F, h - 2.0F, CARD_RADIUS, CARD_RADIUS));

                if (previewImg != null) {
                    int imgX = 8;
                    int imgY = 8;
                    int imgW = Math.max(10, w - 16);
                    int imgH = Math.max(10, h - 54);
                    Shape oldClip = g2.getClip();
                    g2.setClip(new RoundRectangle2D.Float(imgX, imgY, imgW, imgH, 6, 6));
                    g2.drawImage(previewImg, imgX, imgY, imgW, imgH, null);
                    g2.setClip(oldClip);
                }

                g2.setFont(UiTheme.SMALL_BOLD);
                g2.setColor(UiTheme.TEXT);
                g2.drawString(title, 10, h - 24);

                g2.setFont(UiTheme.SMALL);
                g2.setColor(UiTheme.TEXT_MUTED);
                g2.drawString(subtitle, 10, h - 9);

                g2.setColor(hover > 0.5F ? UiTheme.ACCENT_3 : UiTheme.ACCENT);
                g2.setStroke(new BasicStroke(hover > 0.5F ? 1.6F : 1.15F));
                g2.draw(new RoundRectangle2D.Float(1.0F, 1.0F, w - 2.0F, h - 2.0F, CARD_RADIUS, CARD_RADIUS));
                g2.dispose();
            }
        };
    }

    /**
     * Кнопка профиля в правом верхнем углу: аватар Стива/Алекс + никнейм + треугольник вниз (▼).
     */
    public static JComponent profileDropdownButton(java.util.function.Supplier<String> nickSupplier,
                                                   Runnable onToggleDropdown) {
        return new JComponent() {
            private float hover = 0.0F;

            {
                setPreferredSize(new Dimension(172, 36));
                setMinimumSize(new Dimension(148, 36));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Профиль игрока · Нажмите для смены аккаунта");
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = 1.0F;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = 0.0F;
                        repaint();
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (onToggleDropdown != null) {
                            onToggleDropdown.run();
                        }
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Shape box = new RoundRectangle2D.Float(1.0F, 1.0F, w - 2.0F, h - 2.0F, 10.0F, 10.0F);
                g2.setColor(new Color(12, 13, 20, 230));
                g2.fill(box);

                // Аватар Стива/Алекс 22x22 слева
                int ax = 8;
                int ay = (h - 22) / 2;
                g2.setColor(new Color(0xE2, 0xB8, 0x9B));
                g2.fillRoundRect(ax, ay, 22, 22, 6, 6);
                g2.setColor(new Color(0x3B, 0x27, 0x1E));
                g2.fillRect(ax, ay, 22, 6);
                g2.fillRect(ax, ay + 6, 3, 4);
                g2.fillRect(ax + 19, ay + 6, 3, 4);
                g2.setColor(Color.WHITE);
                g2.fillRect(ax + 3, ay + 10, 5, 3);
                g2.fillRect(ax + 14, ay + 10, 5, 3);
                g2.setColor(UiTheme.ACCENT);
                g2.fillRect(ax + 5, ay + 10, 3, 3);
                g2.fillRect(ax + 14, ay + 10, 3, 3);
                g2.setColor(new Color(0x9C, 0x6E, 0x54));
                g2.fillRect(ax + 9, ay + 15, 4, 2);

                // Никнейм рядом с аватаркой
                String rawNick = nickSupplier != null ? nickSupplier.get() : "Steve";
                String nick = (rawNick == null || rawNick.isBlank()) ? "Steve" : rawNick.trim();
                g2.setFont(UiTheme.SMALL_BOLD);
                g2.setColor(UiTheme.TEXT);
                FontMetrics fm = g2.getFontMetrics();
                int ty = (h - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(nick, ax + 30, ty);

                // Маленький треугольник (стрелочка вниз) справа
                int cx = w - 16;
                int cy = h / 2 - 2;
                int[] xs = {cx - 4, cx + 4, cx};
                int[] ys = {cy, cy, cy + 5};
                g2.setColor(hover > 0.5F ? UiTheme.ACCENT : UiTheme.TEXT_MUTED);
                g2.fillPolygon(xs, ys, 3);

                g2.setColor(fade(UiTheme.ACCENT, 0.35F + 0.65F * hover));
                g2.setStroke(new BasicStroke(1.15F));
                g2.draw(box);
                g2.dispose();
            }
        };
    }

    /**
     * Карточка сборки в стиле Frosted Liquid Glass:
     * слева — закруглённый квадрат-иконка с 1–2 буквами названия,
     * справа — жирное название, серое описание и техническая строка через запятую,
     * плюс аккуратная кнопка «Играть».
     */
    public static JComponent modpackCard(String initials,
                                         String title,
                                         String description,
                                         String techInfo,
                                         java.util.function.Supplier<Boolean> activeSupplier,
                                         Runnable onSelect,
                                         Runnable onPlay) {
        return modpackCard(initials, title, description, techInfo, activeSupplier, onSelect, onPlay, null);
    }

    public static JComponent modpackCard(String initials,
                                         String title,
                                         String description,
                                         String techInfo,
                                         java.util.function.Supplier<Boolean> activeSupplier,
                                         Runnable onSelect,
                                         Runnable onPlay,
                                         Runnable onGear) {
        return new JComponent() {
            private float hover = 0.0F;
            private boolean playHover = false;
            private boolean gearHover = false;

            {
                setPreferredSize(new Dimension(440, 88));
                setMinimumSize(new Dimension(340, 88));
                setMaximumSize(new Dimension(900, 92));
                setOpaque(false);
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                MouseAdapter adapter = new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = 1.0F;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = 0.0F;
                        playHover = false;
                        gearHover = false;
                        repaint();
                    }

                    @Override
                    public void mouseMoved(MouseEvent e) {
                        boolean inPlay = isInsidePlayButton(e.getX(), e.getY());
                        boolean inGear = isInsideGearButton(e.getX(), e.getY());
                        if (inPlay != playHover || inGear != gearHover) {
                            playHover = inPlay;
                            gearHover = inGear;
                            repaint();
                        }
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (e.getButton() == MouseEvent.BUTTON3 || isInsideGearButton(e.getX(), e.getY())) {
                            if (onSelect != null) {
                                onSelect.run();
                            }
                            if (onGear != null) {
                                onGear.run();
                            }
                        } else if (isInsidePlayButton(e.getX(), e.getY())) {
                            if (onSelect != null) {
                                onSelect.run();
                            }
                            if (onPlay != null) {
                                onPlay.run();
                            }
                        } else if (onSelect != null) {
                            onSelect.run();
                        }
                        repaint();
                    }
                };
                addMouseListener(adapter);
                addMouseMotionListener(adapter);
            }

            private boolean isInsidePlayButton(int mx, int my) {
                int bw = 88;
                int bh = 30;
                int bx = getWidth() - bw - 14;
                int by = (getHeight() - bh) / 2;
                return mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            }

            private boolean isInsideGearButton(int mx, int my) {
                int gs = 30;
                int gx = getWidth() - 88 - 14 - gs - 8;
                int gy = (getHeight() - gs) / 2;
                return mx >= gx && mx <= gx + gs && my >= gy && my <= gy + gs;
            }

            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth();
                int h = getHeight();
                boolean active = activeSupplier != null && Boolean.TRUE.equals(activeSupplier.get());

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // 1) Прямоугольник с закруглёнными углами (радиус 11px), матовый чёрный (Frosted Liquid Glass)
                Shape cardShape = new RoundRectangle2D.Float(1.5F, 1.5F, w - 3.0F, h - 3.0F, 12.0F, 12.0F);
                Color topBg = active || hover > 0.1F
                        ? mix(new Color(14, 15, 22, 236), UiTheme.ACCENT, active ? 0.09F : 0.05F)
                        : new Color(11, 12, 18, 228);
                Color botBg = new Color(8, 9, 14, 238);
                g2.setPaint(new GradientPaint(0, 0, topBg, 0, h, botBg));
                g2.fill(cardShape);

                // 2) Слева: закруглённый квадрат (как иконка приложения) с 1–2 буквами названия
                int iconSize = 52;
                int ix = 14;
                int iy = (h - iconSize) / 2;
                Shape iconShape = new RoundRectangle2D.Float(ix, iy, iconSize, iconSize, 11.0F, 11.0F);
                g2.setPaint(new GradientPaint(ix, iy,
                        mix(new Color(18, 16, 28), UiTheme.ACCENT, 0.28F),
                        ix + iconSize, iy + iconSize,
                        new Color(10, 10, 16)));
                g2.fill(iconShape);
                g2.setColor(fade(UiTheme.ACCENT, active ? 0.85F : 0.45F));
                g2.setStroke(new BasicStroke(1.2F));
                g2.draw(iconShape);

                g2.setFont(UiTheme.HEADING);
                g2.setColor(UiTheme.TEXT);
                FontMetrics ifm = g2.getFontMetrics();
                String badgeText = (initials == null || initials.isBlank()) ? "AG" : initials;
                int itx = ix + (iconSize - ifm.stringWidth(badgeText)) / 2;
                int ity = iy + (iconSize - ifm.getHeight()) / 2 + ifm.getAscent();
                g2.drawString(badgeText, itx, ity);

                // 3) Справа от иконки: Название (жирным), Описание (мелким серым), Тех. инфо через запятую
                int tx = ix + iconSize + 14;
                g2.setFont(UiTheme.HEADING);
                g2.setColor(UiTheme.TEXT);
                g2.drawString(title != null ? title : "", tx, iy + 17);

                g2.setFont(UiTheme.SMALL);
                g2.setColor(UiTheme.TEXT_MUTED);
                g2.drawString(description != null ? description : "", tx, iy + 34);

                g2.setFont(UiTheme.SMALL);
                g2.setColor(mix(UiTheme.TEXT_MUTED, UiTheme.ACCENT, 0.55F));
                g2.drawString(techInfo != null ? techInfo : "", tx, iy + 50);

                // 4) Аккуратная компактная кнопка «Играть» справа внутри карточки
                int bw = 88;
                int bh = 30;
                int bx = w - bw - 14;
                int by = (h - bh) / 2;
                Shape playShape = new RoundRectangle2D.Float(bx, by, bw, bh, 9.0F, 9.0F);
                g2.setColor(playHover
                        ? mix(new Color(22, 18, 36), UiTheme.ACCENT, 0.35F)
                        : mix(new Color(16, 15, 26), UiTheme.ACCENT, active ? 0.22F : 0.12F));
                g2.fill(playShape);
                g2.setColor(fade(UiTheme.ACCENT, playHover ? 0.95F : (active ? 0.75F : 0.45F)));
                g2.setStroke(new BasicStroke(1.15F));
                g2.draw(playShape);

                g2.setFont(UiTheme.SMALL_BOLD);
                g2.setColor(UiTheme.TEXT);
                String playLabel = "▶ Играть";
                FontMetrics pfm = g2.getFontMetrics();
                g2.drawString(playLabel, bx + (bw - pfm.stringWidth(playLabel)) / 2,
                        by + (bh - pfm.getHeight()) / 2 + pfm.getAscent());

                // 4b) Аккуратная кнопка «⚙» (настройка / ПКМ меню сборки) слева от «Играть»
                if (onGear != null) {
                    int gs = 30;
                    int gx = bx - gs - 8;
                    int gy = by;
                    Shape gearBox = new RoundRectangle2D.Float(gx, gy, gs, gs, 8.0F, 8.0F);
                    g2.setColor(gearHover
                            ? mix(new Color(22, 18, 36), UiTheme.ACCENT, 0.30F)
                            : new Color(15, 16, 24, 220));
                    g2.fill(gearBox);
                    g2.setColor(fade(UiTheme.ACCENT, gearHover ? 0.90F : 0.38F));
                    g2.setStroke(new BasicStroke(1.1F));
                    g2.draw(gearBox);
                    // Векторная шестерёнка
                    int gcx = gx + gs / 2;
                    int gcy = gy + gs / 2;
                    g2.setColor(gearHover ? UiTheme.TEXT : UiTheme.TEXT_MUTED);
                    g2.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int a = 0; a < 8; a++) {
                        double rad = a * (Math.PI / 4.0);
                        int x1 = gcx + (int) Math.round(Math.cos(rad) * 4.5);
                        int y1 = gcy + (int) Math.round(Math.sin(rad) * 4.5);
                        int x2 = gcx + (int) Math.round(Math.cos(rad) * 6.8);
                        int y2 = gcy + (int) Math.round(Math.sin(rad) * 6.8);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                    g2.drawOval(gcx - 4, gcy - 4, 8, 8);
                }

                // 5) Неоновая обводка карточки при наведении или выборе
                float borderAlpha = active ? 0.88F : (hover > 0.1F ? 0.72F : 0.24F);
                g2.setColor(fade(UiTheme.ACCENT, borderAlpha));
                g2.setStroke(new BasicStroke(active || hover > 0.1F ? 1.35F : 1.0F));
                g2.draw(cardShape);
                g2.dispose();
            }
        };
    }
}
