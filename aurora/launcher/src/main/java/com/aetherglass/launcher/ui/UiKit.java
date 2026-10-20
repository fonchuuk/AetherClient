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
        table.setRowHeight(26);
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
}
