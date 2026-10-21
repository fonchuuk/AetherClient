package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.auth.AuthStore;
import com.aetherglass.launcher.auth.MicrosoftAuth;
import com.aetherglass.launcher.core.AutoUpdater;
import com.aetherglass.launcher.core.CrashDoctor;
import com.aetherglass.launcher.core.GameInstaller;
import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.JavaRuntime;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.LauncherPaths;
import com.aetherglass.launcher.core.ModInstaller;
import com.aetherglass.launcher.core.ModpackArchive;
import com.aetherglass.launcher.core.ModpackProfile;
import com.aetherglass.launcher.core.ModrinthCatalog;
import com.aetherglass.launcher.core.Rules;
import com.aetherglass.launcher.core.VersionIndex;
import com.aetherglass.launcher.util.Http;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Содержимое окна лаунчера: вкладки «Игра», «Моды», «Настройки», «Логи».
 *
 * <p>Класс намеренно не наследуется от {@code JFrame}: интерфейс строится как обычный
 * компонент, поэтому его можно проверить и отрисовать без графического окружения,
 * а окно — всего лишь оболочка (см. {@link LauncherWindow}).</p>
 *
 * <p>Вся тяжёлая работа (сеть, файлы, запуск процесса) выполняется в {@link SwingWorker},
 * чтобы интерфейс не подвисал.</p>
 */
public final class LauncherUi {
    static final String LOADER_VERSION = "0.19.5";

    private final LogPanel logPanel = new LogPanel();
    private final GameLauncher gameLauncher = new GameLauncher();
    private final AtomicBoolean cancelRequested = new AtomicBoolean(false);
    private final DefaultTableModel modsModel = new DefaultTableModel(
            new Object[]{"Мод", "Версия", "Назначение", "Состояние"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel modrinthModel = new DefaultTableModel(
            new Object[]{"Название", "Автор", "Загрузки", "Описание"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final List<ModrinthCatalog.Hit> modrinthHits = new ArrayList<>();
    private final java.util.concurrent.atomic.AtomicInteger modrinthRequestSeq =
            new java.util.concurrent.atomic.AtomicInteger(0);
    private String modrinthProjectType = ModrinthCatalog.TYPE_MOD;
    private String modrinthQuery = "";
    private int modrinthOffset = 0;
    private long modrinthTotal = 0L;
    private volatile boolean modrinthLoading = false;

    private final LauncherConfig config;
    private final Component dialogParent;
    private LauncherPaths paths;
    private AuthStore authStore;
    private AuthSession session;
    private ModInstaller.Manifest modManifest;
    private JTabbedPane tabs;
    private final JComponent root;

    private final JComboBox<String> versionBox = UiKit.combo(new JComboBox<>(
            new String[]{"1.21.11", "26.2", "1.21.4", "1.20.4", "1.20.1", "1.19.4", "1.16.5"}));
    private final JComboBox<String> loaderBox = UiKit.combo(new JComboBox<>(
            ModpackProfile.SUPPORTED_LOADERS.toArray(new String[0])));
    private final JComboBox<ModpackProfile> modpackBox = UiKit.combo(new JComboBox<>());
    private final JComboBox<LauncherConfig.JvmPreset> jvmPresetBox = UiKit.combo(new JComboBox<>(
            LauncherConfig.JvmPreset.values()));
    private final JPanel visualModpacksGrid = new JPanel(new java.awt.GridLayout(0, 2, 14, 14));
    private final JPanel screenshotsGrid = new JPanel(new java.awt.GridLayout(0, 3, 10, 10));
    private JTable installedModsTable;
    private final JCheckBox aetherClientBox = UiKit.check(
            new JCheckBox("Установить AetherGlass"));
    private final JCheckBox modalOptimizeModsBox = UiKit.check(
            new JCheckBox("Установить моды на оптимизацию (Sodium, Lithium и др.)", true));
    private final DefaultTableModel modpacksTableModel = new DefaultTableModel(
            new Object[]{"Сборка", "Версия MC", "Загрузчик", "AetherGlass Client", "Оптимизация"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextField newPackNameField = UiKit.field(new JTextField(18));
    private final JTextField newPackDescField = UiKit.field(new JTextField(18));
    private JPanel profileDropdownOverlay;
    private JPanel accountModalOverlay;
    private JPanel createModpackModalOverlay;
    private final JLabel accountLabel = UiKit.chip("Steve", UiTheme.ACCENT);
    private final JLabel javaInfoLabel = UiKit.muted("Java не проверена");
    private final JLabel statusLabel = UiKit.muted("Готов к работе");
    private final JProgressBar progressBar = UiKit.progress();
    private final JButton playButton = UiKit.primaryButton("Играть");
    private final JButton cancelButton = UiKit.button("Отмена");
    private final JButton stopButton = UiKit.dangerButton("Остановить игру");

    private final JTextField gameDirField = UiKit.field(new JTextField(28));
    private final JTextField javaField = UiKit.field(new JTextField(28));
    private final JTextField clientIdField = UiKit.field(new JTextField(20));
    private final JTextField widthField = UiKit.field(new JTextField(6));
    private final JTextField heightField = UiKit.field(new JTextField(6));
    private final JTextField offlineNickField = UiKit.field(new JTextField(14));
    private final JTextField modrinthQueryField = UiKit.field(new JTextField(10));
    private final JLabel modrinthInfoLabel = UiKit.muted("Каталог Modrinth: моды и ресурспаки");
    private JTable modrinthTable;
    private final JSlider memorySlider = UiKit.slider(new JSlider(1024, 16384, 4096));
    private final JCheckBox installModsBox = UiKit.check(
            new JCheckBox("устанавливать оптимизационные моды и клиент AetherGlass"));

    private List<VersionIndex.Entry> availableVersions = List.of();
    private volatile boolean busy;

    public LauncherUi(LauncherConfig config, Component dialogParent) {
        this.config = config;
        this.dialogParent = dialogParent;
        this.paths = new LauncherPaths(config.gameDirectory());
        this.authStore = new AuthStore(this.paths.authFile());
        UiTheme.applyPalette(UiTheme.NeonPalette.fromName(config.accentTheme()));
        UiTheme.syncPaletteToClient(this.paths.base(), UiTheme.currentPalette());
        this.root = buildUi();
        installDragAndDrop(this.root);
        reloadSession();
        applyModrinthPage(ModrinthCatalog.builtinPage(this.modrinthProjectType, "", 0), false);
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            loadModrinthPage("", false);
        }
        loadVersionsInBackground();
        loadManifestInBackground();
        log("Лаунчер запущен. Каталог игры: " + this.paths.base());
        log("Операционная система: " + Rules.osName() + " (" + Rules.osArch() + ")");
    }

    // ─────────────────────────────── построение интерфейса ───────────────────────────────

    private JComponent buildUi() {
        JPanel panel = UiKit.background();

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(12, 18, 6, 18));
        JLabel title = new JLabel("AETHERGLASS");
        title.setFont(UiTheme.TITLE);
        title.setForeground(UiTheme.TEXT);
        JLabel subtitle = UiKit.muted("Matte Black · Neon Launcher · v1.0.8 · 1.21.11");
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);

        JPanel brandWest = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        brandWest.setOpaque(false);
        brandWest.add(UiKit.neonLogoBadge());
        brandWest.add(titles);
        brandWest.add(UiKit.paletteSwitcher(palette -> {
            this.config.accentTheme(palette.name());
            this.config.save(this.paths.launcherConfig());
            UiTheme.syncPaletteToClient(this.paths.base(), palette);
            status("Тема неона: " + palette.displayName() + " (синхронизирована с меню RSHIFT в игре)");
            if (this.root != null) {
                this.root.repaint();
            }
        }));

        long sysRamMb = JavaRuntime.totalMemoryMb();
        String ramText = (sysRamMb > 0)
                ? (this.config.maxMemoryMb() / 1024) + " / " + Math.max(1L, Math.round(sysRamMb / 1024.0)) + " ГБ"
                : this.config.maxMemoryMb() + " МБ";
        String diskText = formatFreeDiskSpace();
        JButton updateGithubBtn = UiKit.primaryButton("Обновить с GitHub");
        updateGithubBtn.setToolTipText("Проверить релиз на GitHub в 1 клик, скачать свежую версию и перезапустить лаунчер");
        updateGithubBtn.addActionListener(e -> checkAndUpdateFromGitHub());

        JButton crashDoctorBtn = UiKit.button("Доктор крашей");
        crashDoctorBtn.setToolTipText("Умная диагностика логов, конфликтов модов и авто-починка в 1 клик");
        crashDoctorBtn.addActionListener(e -> runCrashDoctorDialog());

        JPanel rightTelemetry = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightTelemetry.setOpaque(false);
        rightTelemetry.add(updateGithubBtn);
        rightTelemetry.add(crashDoctorBtn);
        rightTelemetry.add(UiKit.profileDropdownButton(
                () -> this.session != null ? this.session.playerName()
                        : (this.offlineNickField.getText().isBlank() ? "Steve" : this.offlineNickField.getText()),
                () -> toggleProfileDropdown(this.profileDropdownOverlay == null || !this.profileDropdownOverlay.isVisible())
        ));

        header.add(brandWest, BorderLayout.WEST);
        header.add(rightTelemetry, BorderLayout.EAST);

        this.tabs = new JTabbedPane(JTabbedPane.LEFT);
        UiKit.styleTabs(this.tabs);
        this.tabs.addTab("Сборки", modpacksTab());
        this.tabs.addTab("Моды", modsTab());
        this.tabs.addTab("Аккаунты", gameTab());
        this.tabs.addTab("Скриншоты", screenshotsTab());
        this.tabs.addTab("Настройки", settingsTab());
        this.tabs.addTab("Логи", this.logPanel);
        this.tabs.addChangeListener(e -> {
            if (this.tabs.getSelectedIndex() == 1 && !java.awt.GraphicsEnvironment.isHeadless()) {
                loadModrinthPage(this.modrinthQueryField.getText().trim(), false);
            } else if (this.tabs.getSelectedIndex() == 3) {
                refreshScreenshotsGallery();
            }
        });
        syncModpackControls();

        panel.add(header, BorderLayout.NORTH);
        panel.add(this.tabs, BorderLayout.CENTER);
        updateButtons();

        JPanel layeredRoot = new JPanel() {
            @Override
            public boolean isOptimizedDrawingEnabled() {
                return false;
            }
        };
        layeredRoot.setLayout(new javax.swing.OverlayLayout(layeredRoot));
        layeredRoot.setOpaque(false);

        this.profileDropdownOverlay = buildProfileDropdownOverlay();
        this.accountModalOverlay = buildAccountModalOverlay();
        this.createModpackModalOverlay = buildCreateModpackModalOverlay();

        layeredRoot.add(this.createModpackModalOverlay);
        layeredRoot.add(this.accountModalOverlay);
        layeredRoot.add(this.profileDropdownOverlay);
        layeredRoot.add(panel);
        return layeredRoot;
    }

    public void toggleProfileDropdown(boolean visible) {
        if (this.profileDropdownOverlay != null) {
            if (visible) {
                if (this.accountModalOverlay != null) {
                    this.accountModalOverlay.setVisible(false);
                }
                if (this.createModpackModalOverlay != null) {
                    this.createModpackModalOverlay.setVisible(false);
                }
            }
            this.profileDropdownOverlay.setVisible(visible);
            if (this.root != null) {
                this.root.revalidate();
                this.root.repaint();
            }
        }
    }

    public void openAccountSwitchModal(boolean visible) {
        if (this.profileDropdownOverlay != null) {
            this.profileDropdownOverlay.setVisible(false);
        }
        if (this.createModpackModalOverlay != null && visible) {
            this.createModpackModalOverlay.setVisible(false);
        }
        if (this.accountModalOverlay != null) {
            this.accountModalOverlay.setVisible(visible);
            if (this.root != null) {
                this.root.revalidate();
                this.root.repaint();
            }
        }
    }

    public void openCreateModpackModal(boolean visible) {
        if (this.profileDropdownOverlay != null) {
            this.profileDropdownOverlay.setVisible(false);
        }
        if (this.accountModalOverlay != null && visible) {
            this.accountModalOverlay.setVisible(false);
        }
        if (this.createModpackModalOverlay != null) {
            if (visible) {
                onVersionOrLoaderChanged();
            }
            this.createModpackModalOverlay.setVisible(visible);
            if (this.root != null) {
                this.root.revalidate();
                this.root.repaint();
            }
        }
    }

    private JPanel buildProfileDropdownOverlay() {
        JPanel overlay = new JPanel(new BorderLayout());
        overlay.setOpaque(false);
        overlay.setVisible(false);
        overlay.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                toggleProfileDropdown(false);
            }
        });

        JPanel topRightHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 52));
        topRightHolder.setOpaque(false);

        JButton switchAccBtn = new JButton("Сменить аккаунт") {
            {
                setFont(UiTheme.SMALL_BOLD);
                setForeground(UiTheme.TEXT);
                setOpaque(false);
                setContentAreaFilled(false);
                setBorderPainted(false);
                setFocusPainted(false);
                setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
                setBorder(BorderFactory.createEmptyBorder(0, 36, 0, 12));
                setPreferredSize(new java.awt.Dimension(196, 36));
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            }

            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hov = getModel().isRollover();
                g2.setColor(hov ? new java.awt.Color(36, 22, 58, 220) : new java.awt.Color(18, 16, 28, 210));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 9, 9);
                g2.setColor(UiTheme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1.2F));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 9, 9);

                // Векторный значок смены аккаунта (две встречные стрелки)
                int ix = 12;
                int iy = getHeight() / 2;
                g2.setColor(UiTheme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1.6F, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                // Верхняя стрелка вправо
                g2.drawLine(ix, iy - 3, ix + 12, iy - 3);
                g2.drawLine(ix + 9, iy - 6, ix + 12, iy - 3);
                g2.drawLine(ix + 9, iy, ix + 12, iy - 3);
                // Нижняя стрелка влево
                g2.drawLine(ix + 12, iy + 3, ix, iy + 3);
                g2.drawLine(ix + 3, iy, ix, iy + 3);
                g2.drawLine(ix + 3, iy + 6, ix, iy + 3);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        switchAccBtn.addActionListener(e -> openAccountSwitchModal(true));

        JPanel glassMenu = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(10, 11, 17, 242));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                g2.setColor(UiTheme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1.2F));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 12, 12);
                g2.dispose();
            }
        };
        glassMenu.setOpaque(false);
        glassMenu.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        glassMenu.add(switchAccBtn, BorderLayout.CENTER);

        topRightHolder.add(glassMenu);
        overlay.add(topRightHolder, BorderLayout.NORTH);
        return overlay;
    }

    private JPanel buildAccountModalOverlay() {
        JPanel backdrop = createModalBackdrop(() -> openAccountSwitchModal(false));

        JTextField modalNickField = UiKit.field(new JTextField(16));
        modalNickField.setText(this.session != null ? this.session.playerName() : "Steve");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridx = 0;
        c.gridy = 0;
        form.add(UiKit.skinAvatarBadge(modalNickField::getText), c);
        c.gridx = 1;
        form.add(UiKit.muted("Новый никнейм:"), c);
        c.gridx = 2;
        form.add(modalNickField, c);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancelBtn = UiKit.button("Отмена");
        JButton applyBtn = UiKit.primaryButton("Сохранить аккаунт");
        cancelBtn.addActionListener(e -> openAccountSwitchModal(false));
        Runnable applyNick = () -> {
            String nick = modalNickField.getText().trim();
            if (!nick.isEmpty()) {
                this.offlineNickField.setText(nick);
                loginOffline(nick);
                openAccountSwitchModal(false);
            }
        };
        applyBtn.addActionListener(e -> applyNick.run());
        modalNickField.addActionListener(e -> applyNick.run());
        actions.add(cancelBtn);
        actions.add(applyBtn);

        JPanel modalCard = createGlassModalBox("Сменить оффлайн-аккаунт",
                "Введите новый никнейм игрока (3–16 символов)", form, actions, 430, 195);
        backdrop.add(modalCard);
        return backdrop;
    }

    private JPanel buildCreateModpackModalOverlay() {
        JPanel backdrop = createModalBackdrop(() -> openCreateModpackModal(false));

        this.versionBox.setPreferredSize(new java.awt.Dimension(220, 30));
        this.loaderBox.setPreferredSize(new java.awt.Dimension(220, 30));
        this.newPackNameField.setPreferredSize(new java.awt.Dimension(220, 30));
        this.newPackDescField.setPreferredSize(new java.awt.Dimension(220, 30));
        this.newPackNameField.setText("MyPack");
        this.newPackDescField.setText("Сборка с оптимизацией");

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.0;
        form.add(UiKit.muted("Название сборки:"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        form.add(this.newPackNameField, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.0;
        form.add(UiKit.muted("Краткое описание:"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        form.add(this.newPackDescField, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.0;
        form.add(UiKit.muted("Версия Minecraft:"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        form.add(this.versionBox, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 0.0;
        form.add(UiKit.muted("Загрузчик модов:"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        form.add(this.loaderBox, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        form.add(this.aetherClientBox, c);

        c.gridy++;
        form.add(this.modalOptimizeModsBox, c);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);
        JButton folderBtn = UiKit.button("Папка модов");
        JButton cloneBtn = UiKit.button("Клонировать");
        JButton cancelBtn = UiKit.button("Отмена");
        JButton createBtn = UiKit.primaryButton("Сохранить");
        folderBtn.addActionListener(e -> openModsFolder());
        cloneBtn.addActionListener(e -> {
            ModpackProfile active = this.config.activeModpack();
            if (active != null) {
                ModpackProfile copy = this.config.addOrUpdateModpack(
                        active.name() + " Copy",
                        active.description(),
                        active.minecraftVersion(),
                        active.loader(),
                        active.installAetherGlassClient(),
                        active.autoOptimizeMods()
                );
                this.config.save(this.paths.launcherConfig());
                syncModpackControls();
                openCreateModpackModal(false);
                status("Клонирована сборка: " + copy.name());
            }
        });
        cancelBtn.addActionListener(e -> openCreateModpackModal(false));
        createBtn.addActionListener(e -> {
            String ver = this.versionBox.getSelectedItem() != null
                    ? this.versionBox.getSelectedItem().toString() : "1.21.11";
            String loader = this.loaderBox.getSelectedItem() != null
                    ? this.loaderBox.getSelectedItem().toString() : "Fabric";
            String name = this.newPackNameField.getText().trim();
            if (name.isEmpty()) {
                name = ver;
            }
            String desc = this.newPackDescField.getText().trim();
            ModpackProfile created = this.config.addOrUpdateModpack(
                    name,
                    desc,
                    ver,
                    loader,
                    this.aetherClientBox.isEnabled() && this.aetherClientBox.isSelected(),
                    this.modalOptimizeModsBox.isSelected()
            );
            this.config.save(this.paths.launcherConfig());
            this.newPackNameField.setText("");
            this.newPackDescField.setText("");
            syncModpackControls();
            openCreateModpackModal(false);
            status("Создана сборка: " + created.name());
        });
        actions.add(folderBtn);
        actions.add(cloneBtn);
        actions.add(cancelBtn);
        actions.add(createBtn);

        JPanel modalCard = createGlassModalBox("Добавить / Настроить сборку",
                "Настройте версию, загрузчик и моды оптимизации", form, actions, 560, 360);
        backdrop.add(modalCard);
        return backdrop;
    }

    private static JPanel createModalBackdrop(Runnable onClose) {
        JPanel backdrop = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(new java.awt.Color(4, 5, 9, 185));
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        backdrop.setOpaque(false);
        backdrop.setVisible(false);
        backdrop.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (e.getComponent() == backdrop && onClose != null) {
                    onClose.run();
                }
            }
        });
        return backdrop;
    }

    private static JPanel createGlassModalBox(String titleText, String subText,
                                              JComponent body, JComponent footer,
                                              int width, int height) {
        JPanel box = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new java.awt.Color(11, 12, 18, 246));
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
                g2.setColor(UiTheme.ACCENT);
                g2.setStroke(new java.awt.BasicStroke(1.3F));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 14, 14);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setPreferredSize(new java.awt.Dimension(width, height));
        box.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        box.addMouseListener(new java.awt.event.MouseAdapter() {});

        JPanel head = new JPanel(new BorderLayout(0, 3));
        head.setOpaque(false);
        JLabel t = new JLabel(titleText);
        t.setFont(UiTheme.HEADING);
        t.setForeground(UiTheme.TEXT);
        head.add(t, BorderLayout.NORTH);
        head.add(UiKit.muted(subText), BorderLayout.SOUTH);

        box.add(head, BorderLayout.NORTH);
        box.add(body, BorderLayout.CENTER);
        box.add(footer, BorderLayout.SOUTH);
        return box;
    }

    /** Корневой компонент со всем содержимым окна. */
    public JComponent root() {
        return this.root;
    }

    /** Вкладки — нужны, чтобы показать нужную при проверке без графики. */
    public JTabbedPane tabs() {
        return this.tabs;
    }

    public GameLauncher gameLauncher() {
        return this.gameLauncher;
    }

    /** Возвращает коренной каталог игры (создавая его при необходимости). */
    public Path rootDirectory() throws IOException {
        Path dir = this.paths.base();
        Files.createDirectories(dir);
        return dir;
    }

    /** Возвращает каталог модов {@code <gameDir>/mods} (создавая его при необходимости). */
    public Path modsDirectory() throws IOException {
        Path dir = this.paths.mods();
        Files.createDirectories(dir);
        return dir;
    }

    /** Возвращает каталог ресурспаков {@code <gameDir>/resourcepacks} (создавая его при необходимости). */
    public Path resourcePacksDirectory() throws IOException {
        Path dir = this.paths.resourcePacks();
        Files.createDirectories(dir);
        return dir;
    }

    /** Возвращает каталог шейдерпаков {@code <gameDir>/shaderpacks} (создавая его при необходимости). */
    public Path shaderPacksDirectory() throws IOException {
        Path dir = this.paths.shaderPacks();
        Files.createDirectories(dir);
        return dir;
    }

    /** Возвращает каталог скриншотов {@code <gameDir>/screenshots} (создавая его при необходимости). */
    public Path screenshotsDirectory() throws IOException {
        Path dir = this.paths.screenshots();
        Files.createDirectories(dir);
        return dir;
    }

    /** Открывает папку Shaderpacks в системном проводнике. */
    public void openShaderPacksFolder() {
        try {
            openDirectoryInSystem(shaderPacksDirectory());
        } catch (IOException exception) {
            showError("Не удалось подготовить папку Шейдерпаков: " + exception.getMessage());
        }
    }

    /** Открывает папку Screenshots в системном проводнике. */
    public void openScreenshotsFolder() {
        try {
            openDirectoryInSystem(screenshotsDirectory());
        } catch (IOException exception) {
            showError("Не удалось подготовить папку Скриншотов: " + exception.getMessage());
        }
    }

    /** Очищает устаревшие архивы логов и временный кэш лаунчера. */
    public void cleanCacheAndOldLogs() {
        long freedBytes = 0L;
        int removedFiles = 0;
        try {
            Path logsDir = this.paths.logsDir();
            if (Files.isDirectory(logsDir)) {
                try (var stream = Files.list(logsDir)) {
                    for (Path p : stream.toList()) {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        if (name.endsWith(".log.gz") || name.endsWith(".tmp")) {
                            freedBytes += Files.size(p);
                            Files.deleteIfExists(p);
                            removedFiles++;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
        }
        String summary = String.format(Locale.ROOT,
                "Кэш очищен: удалено файлов %d (освобождено %.1f МБ)",
                removedFiles, freedBytes / (1024.0 * 1024.0));
        status(summary);
        log(summary);
    }

    private String formatFreeDiskSpace() {
        try {
            Path target = Files.exists(this.paths.base()) ? this.paths.base() : Path.of(".");
            long usable = Files.getFileStore(target).getUsableSpace();
            return Math.max(1L, usable / (1024L * 1024L * 1024L)) + " ГБ своб.";
        } catch (Exception ignored) {
            return "OK";
        }
    }

    /** Открывает коренную папку игры в системном проводнике. */
    public void openRootFolder() {
        try {
            openDirectoryInSystem(rootDirectory());
        } catch (IOException exception) {
            showError("Не удалось подготовить коренную папку игры: " + exception.getMessage());
        }
    }

    /** Открывает папку Mods в системном проводнике. */
    public void openModsFolder() {
        try {
            openDirectoryInSystem(modsDirectory());
        } catch (IOException exception) {
            showError("Не удалось подготовить папку Mods: " + exception.getMessage());
        }
    }

    /** Открывает папку Resource Packs в системном проводнике. */
    public void openResourcePacksFolder() {
        try {
            openDirectoryInSystem(resourcePacksDirectory());
        } catch (IOException exception) {
            showError("Не удалось подготовить папку Ресурспаков: " + exception.getMessage());
        }
    }

    private void openDirectoryInSystem(Path directory) {
        log("Открытие папки: " + directory.toAbsolutePath());
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.OPEN)) {
                java.awt.Desktop.getDesktop().open(directory.toFile());
                return;
            }
        } catch (Throwable ignored) {
            // Запасной путь через команду ОС ниже
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("explorer.exe", directory.toAbsolutePath().toString()).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", directory.toAbsolutePath().toString()).start();
            } else {
                new ProcessBuilder("xdg-open", directory.toAbsolutePath().toString()).start();
            }
        } catch (IOException exception) {
            showError("Не удалось открыть папку " + directory + ": " + exception.getMessage());
        }
    }

    private JPanel gameTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 16, 16, 16));

        JPanel accountRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        accountRow.setOpaque(false);
        JButton loginButton = UiKit.button("Войти через Microsoft");
        JButton offlineButton = UiKit.primaryButton("Войти офлайн");
        JButton logoutButton = UiKit.button("Выйти");
        this.offlineNickField.setToolTipText("Никнейм для офлайн-игры (3–16 символов: латиница, цифры, _)");
        loginButton.addActionListener(event -> login());
        offlineButton.addActionListener(event -> loginOffline(this.offlineNickField.getText()));
        this.offlineNickField.addActionListener(event -> loginOffline(this.offlineNickField.getText()));
        logoutButton.addActionListener(event -> logout());
        accountRow.add(UiKit.skinAvatarBadge(() -> this.offlineNickField.getText()));
        accountRow.add(UiKit.muted("Ник:"));
        accountRow.add(this.offlineNickField);
        accountRow.add(offlineButton);
        accountRow.add(loginButton);
        accountRow.add(logoutButton);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);
        this.modpackBox.setPreferredSize(new java.awt.Dimension(220, 26));
        this.jvmPresetBox.setPreferredSize(new java.awt.Dimension(210, 26));
        this.jvmPresetBox.setSelectedItem(this.config.jvmPreset());
        this.jvmPresetBox.addActionListener(e -> {
            Object sel = this.jvmPresetBox.getSelectedItem();
            if (sel instanceof LauncherConfig.JvmPreset preset) {
                this.config.jvmPreset(preset);
                this.config.save(this.paths.launcherConfig());
                status("Профиль JVM: " + preset.displayName());
            }
        });
        this.versionBox.setPreferredSize(new java.awt.Dimension(92, 26));
        this.loaderBox.setPreferredSize(new java.awt.Dimension(92, 26));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(3, 4, 3, 4);
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        settings.add(UiKit.muted("Сборка и JVM:"), constraints);
        constraints.gridx = 1;
        JPanel packAndJvmRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        packAndJvmRow.setOpaque(false);
        packAndJvmRow.add(this.modpackBox);
        packAndJvmRow.add(UiKit.muted("Флаги JVM:"));
        packAndJvmRow.add(this.jvmPresetBox);
        settings.add(packAndJvmRow, constraints);
        this.versionBox.addActionListener(e -> onVersionOrLoaderChanged());
        this.loaderBox.addActionListener(e -> onVersionOrLoaderChanged());
        this.aetherClientBox.addActionListener(e -> {
            if (this.aetherClientBox.isEnabled()) {
                this.config.installAetherGlassClient(this.aetherClientBox.isSelected());
            }
        });
        this.modpackBox.addActionListener(e -> {
            Object sel = this.modpackBox.getSelectedItem();
            if (sel instanceof ModpackProfile pack) {
                this.config.selectModpack(pack.id());
                this.versionBox.setSelectedItem(pack.minecraftVersion());
                this.loaderBox.setSelectedItem(pack.loader());
                updateAetherClientGate(pack.minecraftVersion(), pack.loader(), pack.installAetherGlassClient());
            }
        });
        constraints.gridx = 0;
        constraints.gridy++;
        settings.add(UiKit.muted("Память (МБ):"), constraints);
        constraints.gridx = 1;
        JPanel memory = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        memory.setOpaque(false);
        JLabel memoryValue = new JLabel(String.valueOf(this.config.maxMemoryMb()));
        memoryValue.setFont(UiTheme.BASE);
        memoryValue.setForeground(UiTheme.ACCENT);
        this.memorySlider.setValue(this.config.maxMemoryMb());
        this.memorySlider.addChangeListener(event -> {
            int value = roundMemory(this.memorySlider.getValue());
            memoryValue.setText(String.valueOf(value));
            this.config.maxMemoryMb(value);
            this.config.minMemoryMb(Math.min(1024, value));
        });
        long totalMemory = JavaRuntime.totalMemoryMb();
        memory.add(this.memorySlider);
        memory.add(memoryValue);
        for (int presetGb : new int[]{2, 4, 6, 8}) {
            int presetMb = presetGb * 1024;
            JButton presetBtn = UiKit.button(presetGb + " ГБ");
            presetBtn.setToolTipText("Выделить " + presetGb + " ГБ (" + presetMb + " МБ) оперативной памяти");
            presetBtn.addActionListener(e -> this.memorySlider.setValue(presetMb));
            memory.add(presetBtn);
        }
        if (totalMemory > 0) {
            JLabel hint = UiKit.muted("из " + totalMemory + " МБ");
            memory.add(hint);
        }
        settings.add(memory, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        settings.add(UiKit.muted("Быстрые действия:"), constraints);
        constraints.gridx = 1;
        JPanel foldersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        foldersRow.setOpaque(false);
        JButton gameRootBtn = UiKit.button("Коренная папка");
        JButton gameModsBtn = UiKit.button("Папка Mods");
        JButton shadersBtn = UiKit.button("Шейдерпаки");
        JButton screensBtn = UiKit.button("Скриншоты");
        JButton backupBtn = UiKit.button("Бэкап миров (.zip)");
        JButton cleanCacheBtn = UiKit.button("Очистить кэш");
        gameRootBtn.addActionListener(event -> openRootFolder());
        gameModsBtn.addActionListener(event -> openModsFolder());
        shadersBtn.addActionListener(event -> openShaderPacksFolder());
        screensBtn.addActionListener(event -> openScreenshotsFolder());
        backupBtn.addActionListener(event -> backupWorldsInBackground());
        cleanCacheBtn.addActionListener(event -> cleanCacheAndOldLogs());
        foldersRow.add(gameRootBtn);
        foldersRow.add(gameModsBtn);
        foldersRow.add(shadersBtn);
        foldersRow.add(screensBtn);
        foldersRow.add(backupBtn);
        foldersRow.add(cleanCacheBtn);
        settings.add(foldersRow, constraints);

        JPanel bottom = new JPanel(new BorderLayout(10, 8));
        bottom.setOpaque(false);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        this.playButton.setPreferredSize(new java.awt.Dimension(220, 44));
        this.playButton.setFont(UiTheme.HEADING);
        this.playButton.addActionListener(event -> startGame());
        this.cancelButton.addActionListener(event -> {
            this.cancelRequested.set(true);
            status("Отмена…");
        });
        this.stopButton.addActionListener(event -> {
            this.gameLauncher.stop();
            log("Игра остановлена по запросу пользователя.");
        });
        buttons.add(this.cancelButton);
        buttons.add(this.stopButton);
        buttons.add(this.playButton);
        bottom.add(this.statusLabel, BorderLayout.NORTH);
        bottom.add(this.progressBar, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        boolean[] cardStates = {true, true, true, false, true, false};

        JPanel col1 = new JPanel(new java.awt.GridLayout(3, 1, 0, 7));
        col1.setOpaque(false);
        col1.add(UiKit.matteNeonGridCard(
                "AetherGlass Client",
                "16 шейдеров, HUD и 3D-эффекты",
                () -> this.aetherClientBox.isSelected(),
                () -> {
                    if (this.aetherClientBox.isEnabled()) {
                        this.aetherClientBox.setSelected(!this.aetherClientBox.isSelected());
                        this.config.installAetherGlassClient(this.aetherClientBox.isSelected());
                    }
                }));
        col1.add(UiKit.matteNeonGridCard(
                "Iris Shaders",
                "Поддержка 3D и видео-шейдеров",
                () -> cardStates[0],
                () -> cardStates[0] = !cardStates[0]));
        col1.add(UiKit.matteNeonGridCard(
                "Continuity + Indium",
                "Связные текстуры и API рендера",
                () -> cardStates[3],
                () -> cardStates[3] = !cardStates[3]));

        JPanel col2 = new JPanel(new java.awt.GridLayout(3, 1, 0, 7));
        col2.setOpaque(false);
        col2.add(UiKit.matteNeonGridCard(
                "Sodium 1.21.11",
                "Ускорение рендера чанков",
                () -> this.installModsBox.isSelected(),
                () -> {
                    this.installModsBox.setSelected(!this.installModsBox.isSelected());
                    this.config.installMods(this.installModsBox.isSelected());
                }));
        col2.add(UiKit.matteNeonGridCard(
                "Lithium + Ferrite",
                "Оптимизация тиков и ОЗУ",
                () -> cardStates[2],
                () -> cardStates[2] = !cardStates[2]));
        col2.add(UiKit.matteNeonGridCard(
                "EntityCulling",
                "Отсечение невидимых сущностей",
                () -> cardStates[5],
                () -> cardStates[5] = !cardStates[5]));

        JPanel col3 = new JPanel(new java.awt.GridLayout(3, 1, 0, 7));
        col3.setOpaque(false);
        col3.add(UiKit.matteNeonGridCard(
                "ImmediatelyFast",
                "Пакетный рендер GUI и HUD",
                () -> cardStates[4],
                () -> cardStates[4] = !cardStates[4]));
        col3.add(UiKit.matteNeonGridCard(
                "ModMenu + Reese",
                "Удобный список модов в игре",
                () -> cardStates[1],
                () -> cardStates[1] = !cardStates[1]));
        col3.add(UiKit.matteNeonGridCard(
                "Dynamic FPS",
                "Экономия ресурсов в фоне",
                () -> false,
                () -> {}));

        JPanel columnsRow = new JPanel(new java.awt.GridLayout(1, 3, 10, 0));
        columnsRow.setOpaque(false);
        columnsRow.add(UiKit.card("ВИЗУАЛ И КЛИЕНТ", col1));
        columnsRow.add(UiKit.card("ОПТИМИЗАЦИЯ", col2));
        columnsRow.add(UiKit.card("ИНТЕРФЕЙС И HUD", col3));

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(columnsRow, BorderLayout.NORTH);
        JPanel midStack = new JPanel(new BorderLayout(0, 10));
        midStack.setOpaque(false);
        midStack.add(UiKit.card("Аккаунт", accountRow), BorderLayout.NORTH);
        midStack.add(UiKit.card("Параметры запуска", settings), BorderLayout.CENTER);
        center.add(midStack, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);
        panel.add(UiKit.card(null, bottom, true), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel modpacksTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));

        JPanel topBar = new JPanel(new BorderLayout(12, 0));
        topBar.setOpaque(false);

        JPanel titleBlock = new JPanel(new BorderLayout(0, 2));
        titleBlock.setOpaque(false);
        JLabel sectionTitle = new JLabel("Мои сборки");
        sectionTitle.setFont(UiTheme.HEADING);
        sectionTitle.setForeground(UiTheme.TEXT);
        titleBlock.add(sectionTitle, BorderLayout.NORTH);
        titleBlock.add(UiKit.muted("Выберите сборку или нажмите «Играть» на карточке"), BorderLayout.SOUTH);

        JPanel actionsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionsRight.setOpaque(false);
        JButton addPackBtn = UiKit.primaryButton("+ Добавить новую сборку");
        JButton importPackBtn = UiKit.button("Импорт (.zip / .mrpack)");
        JButton exportPackBtn = UiKit.button("Экспорт (.zip)");
        JButton deletePackBtn = UiKit.dangerButton("Удалить");

        addPackBtn.addActionListener(e -> openCreateModpackModal(true));
        importPackBtn.addActionListener(e -> importModpackDialog());
        exportPackBtn.addActionListener(e -> exportActiveModpack());
        deletePackBtn.addActionListener(e -> {
            ModpackProfile active = this.config.activeModpack();
            if (active != null && this.config.removeModpack(active.id())) {
                this.config.save(this.paths.launcherConfig());
                syncModpackControls();
                status("Удалена сборка: " + active.name());
            } else {
                status("Нельзя удалить единственную базовую сборку");
            }
        });

        actionsRight.add(addPackBtn);
        actionsRight.add(importPackBtn);
        actionsRight.add(exportPackBtn);
        actionsRight.add(deletePackBtn);

        topBar.add(titleBlock, BorderLayout.WEST);
        topBar.add(actionsRight, BorderLayout.EAST);

        this.visualModpacksGrid.setOpaque(false);
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.add(this.visualModpacksGrid, BorderLayout.NORTH);

        JPanel statusStrip = new JPanel(new BorderLayout(12, 0));
        statusStrip.setOpaque(false);
        statusStrip.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        statusStrip.add(this.statusLabel, BorderLayout.WEST);
        statusStrip.add(this.progressBar, BorderLayout.CENTER);

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(UiKit.scroll(gridHolder), BorderLayout.CENTER);
        panel.add(statusStrip, BorderLayout.SOUTH);
        return panel;
    }

    private void onVersionOrLoaderChanged() {
        String ver = this.versionBox.getSelectedItem() != null
                ? this.versionBox.getSelectedItem().toString() : this.config.selectedVersion();
        String loader = this.loaderBox.getSelectedItem() != null
                ? this.loaderBox.getSelectedItem().toString() : this.config.selectedLoader();
        boolean wasSupported = ModpackProfile.isAetherGlassSupported(this.config.selectedVersion(), this.config.selectedLoader());
        this.config.selectedVersion(ver);
        this.config.selectedLoader(loader);
        boolean nowSupported = ModpackProfile.isAetherGlassSupported(ver, loader);
        boolean checkState = nowSupported && (!wasSupported || this.aetherClientBox.isSelected());
        updateAetherClientGate(ver, loader, checkState);
    }

    private void updateAetherClientGate(String ver, String loader, boolean preferredSelected) {
        boolean supported = ModpackProfile.isAetherGlassSupported(ver, loader);
        this.aetherClientBox.setEnabled(supported);
        this.aetherClientBox.setSelected(supported && preferredSelected);
        this.aetherClientBox.setToolTipText(supported
                ? "Установить мод AetherGlass Client (1.21.11 Fabric)"
                : "AetherGlass доступен только для версии 1.21.11 (Fabric)");
        this.config.installAetherGlassClient(this.aetherClientBox.isSelected());
    }

    private void syncModpackControls() {
        List<ModpackProfile> packs = this.config.modpacks();
        DefaultComboBoxModel<ModpackProfile> comboModel = new DefaultComboBoxModel<>(
                packs.toArray(new ModpackProfile[0]));
        this.modpackBox.setModel(comboModel);
        ModpackProfile active = this.config.activeModpack();
        this.modpackBox.setSelectedItem(active);
        this.versionBox.setSelectedItem(this.config.selectedVersion());
        this.loaderBox.setSelectedItem(this.config.selectedLoader());
        updateAetherClientGate(this.config.selectedVersion(), this.config.selectedLoader(),
                this.config.installAetherGlassClient());

        this.modpacksTableModel.setRowCount(0);
        this.visualModpacksGrid.removeAll();
        for (ModpackProfile p : packs) {
            this.modpacksTableModel.addRow(new Object[]{
                    p.name(),
                    p.minecraftVersion(),
                    p.loader(),
                    p.installAetherGlassClient() ? "Включён (1.21.11)" : "Отключён (свои моды)",
                    p.autoOptimizeMods() ? "Авто (Sodium/Lithium)" : "Базовый"
            });
            this.visualModpacksGrid.add(UiKit.modpackCard(
                    p.iconInitials(),
                    p.name(),
                    p.description(),
                    p.technicalInfo(),
                    () -> this.config.activeModpack().id().equals(p.id()),
                    () -> {
                        this.config.selectModpack(p.id());
                        this.config.save(this.paths.launcherConfig());
                        syncModpackControls();
                        status("Выбрана сборка: " + p.name() + " (" + p.technicalInfo() + ")");
                    },
                    () -> {
                        this.config.selectModpack(p.id());
                        this.config.save(this.paths.launcherConfig());
                        syncModpackControls();
                        startGame();
                    },
                    () -> {
                        this.config.selectModpack(p.id());
                        this.newPackNameField.setText(p.name());
                        this.newPackDescField.setText(p.description());
                        selectVersionAndLoader(p.minecraftVersion(), p.loader());
                        this.aetherClientBox.setSelected(p.installAetherGlassClient());
                        this.modalOptimizeModsBox.setSelected(p.autoOptimizeMods());
                        openCreateModpackModal(true);
                    }
            ));
        }
        this.visualModpacksGrid.revalidate();
        this.visualModpacksGrid.repaint();
    }

    /** Программный выбор версии и загрузчика (Блок 5.2). */
    public void selectVersionAndLoader(String mcVersion, String loader) {
        DefaultComboBoxModel<String> verModel = (DefaultComboBoxModel<String>) this.versionBox.getModel();
        if (verModel.getIndexOf(mcVersion) < 0) {
            verModel.addElement(mcVersion);
        }
        this.versionBox.setSelectedItem(mcVersion);
        this.loaderBox.setSelectedItem(ModpackProfile.normalizeLoader(loader));
        onVersionOrLoaderChanged();
    }

    public boolean isAetherGlassCheckboxEnabled() {
        return this.aetherClientBox.isEnabled();
    }

    public boolean isAetherGlassCheckboxSelected() {
        return this.aetherClientBox.isSelected();
    }

    public ModpackProfile createModpack(String name, String mcVersion, String loader,
                                        boolean installAetherClient, boolean autoOptimize) {
        ModpackProfile created = this.config.addOrUpdateModpack(
                name, mcVersion, loader, installAetherClient, autoOptimize);
        this.config.save(this.paths.launcherConfig());
        syncModpackControls();
        return created;
    }

    public List<ModpackProfile> modpacks() {
        return this.config.modpacks();
    }

    private JPanel modsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        JPanel catalogPanel = new JPanel(new BorderLayout(0, 8));
        catalogPanel.setOpaque(false);
        JPanel searchBar = new JPanel(new BorderLayout(6, 0));
        searchBar.setOpaque(false);
        JButton typeModsBtn = UiKit.primaryButton("Моды (Fabric)");
        JButton typeRpBtn = UiKit.button("Ресурспаки");
        JButton typeShadersBtn = UiKit.button("Шейдеры (Iris)");
        JButton searchBtn = UiKit.primaryButton("Найти");
        JButton popularBtn = UiKit.button("Популярные");
        JButton moreBtn = UiKit.button("Показать ещё");
        JButton installOneClickBtn = UiKit.primaryButton("Скачать в 1 клик");

        typeModsBtn.addActionListener(event -> {
            this.modrinthProjectType = ModrinthCatalog.TYPE_MOD;
            loadModrinthPage(this.modrinthQueryField.getText().trim(), false);
        });
        typeRpBtn.addActionListener(event -> {
            this.modrinthProjectType = ModrinthCatalog.TYPE_RESOURCEPACK;
            loadModrinthPage(this.modrinthQueryField.getText().trim(), false);
        });
        typeShadersBtn.addActionListener(event -> {
            this.modrinthProjectType = ModrinthCatalog.TYPE_SHADER;
            loadModrinthPage(this.modrinthQueryField.getText().trim(), false);
        });
        searchBtn.addActionListener(event -> loadModrinthPage(this.modrinthQueryField.getText().trim(), false));
        this.modrinthQueryField.addActionListener(event -> loadModrinthPage(this.modrinthQueryField.getText().trim(), false));
        popularBtn.addActionListener(event -> {
            this.modrinthQueryField.setText("");
            loadModrinthPage("", false);
        });
        moreBtn.addActionListener(event -> loadModrinthPage(this.modrinthQuery, true));
        installOneClickBtn.addActionListener(event -> installSelectedModrinthHit());

        JPanel leftTypeBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftTypeBar.setOpaque(false);
        leftTypeBar.add(typeModsBtn);
        leftTypeBar.add(typeRpBtn);
        leftTypeBar.add(typeShadersBtn);

        JPanel rightActionBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightActionBar.setOpaque(false);
        for (String tag : new String[]{"FPS", "PvP", "HUD"}) {
            JButton tagBtn = UiKit.button(tag);
            tagBtn.setToolTipText("Быстрый фильтр каталога по тегу: " + tag);
            tagBtn.addActionListener(e -> {
                String q = switch (tag) {
                    case "FPS" -> "sodium";
                    case "PvP" -> "zoom";
                    default -> "hud";
                };
                this.modrinthQueryField.setText(q);
                loadModrinthPage(q, false);
            });
            rightActionBar.add(tagBtn);
        }
        rightActionBar.add(searchBtn);
        rightActionBar.add(popularBtn);

        searchBar.add(leftTypeBar, BorderLayout.WEST);
        searchBar.add(this.modrinthQueryField, BorderLayout.CENTER);
        searchBar.add(rightActionBar, BorderLayout.EAST);

        this.modrinthTable = new JTable(this.modrinthModel);
        UiKit.styleTable(this.modrinthTable);
        this.modrinthTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    installSelectedModrinthHit();
                }
            }
        });
        JScrollPane catalogScroll = UiKit.scroll(this.modrinthTable);
        catalogScroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (event.getValueIsAdjusting() || this.modrinthLoading) {
                return;
            }
            var sb = catalogScroll.getVerticalScrollBar();
            if (sb.getMaximum() > 0
                    && sb.getValue() + sb.getVisibleAmount() >= sb.getMaximum() - 24
                    && this.modrinthOffset > 0
                    && this.modrinthOffset < this.modrinthTotal) {
                loadModrinthPage(this.modrinthQuery, true);
            }
        });

        JPanel catalogFooter = new JPanel(new BorderLayout(8, 0));
        catalogFooter.setOpaque(false);
        JPanel catalogFooterBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        catalogFooterBtns.setOpaque(false);
        catalogFooterBtns.add(moreBtn);
        catalogFooterBtns.add(installOneClickBtn);
        catalogFooter.add(this.modrinthInfoLabel, BorderLayout.CENTER);
        catalogFooter.add(catalogFooterBtns, BorderLayout.EAST);

        catalogPanel.add(searchBar, BorderLayout.NORTH);
        catalogPanel.add(catalogScroll, BorderLayout.CENTER);
        catalogPanel.add(catalogFooter, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        this.installedModsTable = new JTable(this.modsModel);
        UiKit.styleTable(this.installedModsTable);
        top.add(UiKit.scroll(this.installedModsTable), BorderLayout.CENTER);
        top.add(UiKit.muted("Моды ставятся в папку игры. Выберите строку и нажмите «Вкл / Выкл мод», чтобы временно отключить мод (.disabled)."), BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refresh = UiKit.button("Проверить");
        JButton install = UiKit.primaryButton("Установить / обновить");
        JButton toggleModBtn = UiKit.button("Вкл / Выкл мод (.disabled)");
        JButton rootFolder = UiKit.button("Коренная папка");
        JButton modsFolder = UiKit.button("Папка Mods");
        JButton rpFolder = UiKit.button("Папка Ресурспаков");
        JButton shadersFolder = UiKit.button("Папка Шейдеров");
        refresh.addActionListener(event -> refreshModsTable());
        install.addActionListener(event -> installModsInBackground());
        toggleModBtn.addActionListener(event -> toggleSelectedInstalledMod());
        rootFolder.addActionListener(event -> openRootFolder());
        modsFolder.addActionListener(event -> openModsFolder());
        rpFolder.addActionListener(event -> openResourcePacksFolder());
        shadersFolder.addActionListener(event -> openShaderPacksFolder());
        controls.add(refresh);
        controls.add(install);
        controls.add(toggleModBtn);
        controls.add(rootFolder);
        controls.add(modsFolder);
        controls.add(rpFolder);
        controls.add(shadersFolder);

        JPanel split = new JPanel(new java.awt.GridLayout(2, 1, 0, 10));
        split.setOpaque(false);
        split.add(UiKit.card("Каталог Modrinth (Моды, Ресурспаки и Шейдеры Iris — 1 клик)", catalogPanel));
        split.add(UiKit.card("Оптимизационные моды", top));

        panel.add(split, BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel settingsTab() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 5, 7, 5);
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.fill = GridBagConstraints.HORIZONTAL;

        this.gameDirField.setText(this.config.gameDirectory().toString());
        this.javaField.setText(this.config.javaExecutable());
        this.clientIdField.setText(this.config.clientId());
        this.widthField.setText(this.config.windowWidth() > 0 ? String.valueOf(this.config.windowWidth()) : "");
        this.heightField.setText(this.config.windowHeight() > 0 ? String.valueOf(this.config.windowHeight()) : "");
        this.installModsBox.setSelected(this.config.installMods());

        addRow(panel, constraints, 0, "Каталог игры:", this.gameDirField, browseButton(this.gameDirField, true));

        JPanel resolution = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        resolution.setOpaque(false);
        resolution.add(this.widthField);
        resolution.add(UiKit.muted("×"));
        resolution.add(this.heightField);
        resolution.add(UiKit.muted("пусто — размер окна по умолчанию"));
        addRow(panel, constraints, 4, "Размер окна игры:", resolution, null);

        addRow(panel, constraints, 5, "Client ID приложения Azure:", this.clientIdField, null);

        constraints.gridx = 1;
        constraints.gridy = 6;
        constraints.gridwidth = 2;
        panel.add(this.installModsBox, constraints);
        constraints.gridwidth = 1;

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        controls.setOpaque(false);
        JButton save = UiKit.primaryButton("Сохранить");
        JButton rootFolder = UiKit.button("Коренная папка");
        JButton modsFolder = UiKit.button("Папка Mods");
        save.addActionListener(event -> saveSettings());
        rootFolder.addActionListener(event -> openRootFolder());
        modsFolder.addActionListener(event -> openModsFolder());
        controls.add(save);
        controls.add(rootFolder);
        controls.add(modsFolder);
        constraints.gridx = 1;
        constraints.gridy = 7;
        constraints.gridwidth = 2;
        panel.add(controls, constraints);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));
        wrapper.add(UiKit.card("Настройки", panel), BorderLayout.CENTER);
        return wrapper;
    }

    private void addRow(JPanel panel, GridBagConstraints constraints, int row,
                        String text, Component field, Component extra) {
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        constraints.gridy = row;
        panel.add(label(text), constraints);
        constraints.gridx = 1;
        panel.add(field, constraints);
        if (extra != null) {
            constraints.gridx = 2;
            panel.add(extra, constraints);
        }
    }

    private Component browseButton(JTextField field, boolean directory) {
        JButton button = UiKit.button("Обзор…");
        button.addActionListener(event -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(Path.of(field.getText().isBlank() ? "." : field.getText()).toFile());
            chooser.setFileSelectionMode(directory ? JFileChooser.DIRECTORIES_ONLY : JFileChooser.FILES_ONLY);
            if (chooser.showOpenDialog(this.dialogParent) == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        return button;
    }

    private static JLabel label(String text) {
        return UiKit.muted(text);
    }

    private static int roundMemory(int value) {
        return Math.max(LauncherConfig.MIN_MEMORY_MB, Math.round(value / 256.0F) * 256);
    }

    // ─────────────────────────────── вход и сессия ───────────────────────────────

    private void reloadSession() {
        this.session = this.authStore.load();
        if (this.session == null) {
            this.accountLabel.setText("Вход не выполнен");
            this.accountLabel.setForeground(UiTheme.TEXT_MUTED);
        } else if (this.session.offline()) {
            this.accountLabel.setText("Офлайн: " + this.session.playerName());
            this.accountLabel.setForeground(UiTheme.ACCENT);
            if (this.offlineNickField.getText().isBlank()) {
                this.offlineNickField.setText(this.session.playerName());
            }
        } else {
            this.accountLabel.setText("Игрок: " + this.session.playerName()
                    + (this.session.valid() ? "" : " (токен истёк)"));
            this.accountLabel.setForeground(this.session.valid() ? UiTheme.SUCCESS : UiTheme.DANGER);
        }
        this.accountLabel.repaint();
    }

    /** Вход в офлайн-аккаунт (без лицензии Microsoft) по указанному никнейму. */
    public void loginOffline(String rawName) {
        if (this.busy) {
            return;
        }
        try {
            AuthSession offlineSession = AuthSession.offline(rawName);
            this.session = offlineSession;
            this.authStore.save(offlineSession);
            this.offlineNickField.setText(offlineSession.playerName());
            reloadSession();
            status("Офлайн-профиль активирован: " + offlineSession.playerName());
            log("Офлайн-вход выполнен: " + offlineSession.playerName() + " (" + offlineSession.uuid() + ")");
        } catch (IllegalArgumentException | IOException exception) {
            showError(exception.getMessage());
        }
    }

    private void applyModrinthPage(ModrinthCatalog.SearchPage page, boolean append) {
        if (page == null) {
            return;
        }
        if (!append) {
            this.modrinthHits.clear();
            this.modrinthModel.setRowCount(0);
        }
        for (ModrinthCatalog.Hit hit : page.hits()) {
            this.modrinthHits.add(hit);
            if (hit.iconUrl() != null && !hit.iconUrl().isBlank()) {
                UiKit.preloadModrinthIcon(hit.title().toLowerCase(Locale.ROOT), hit.iconUrl(), this.modrinthTable);
            }
            this.modrinthModel.addRow(new Object[]{
                    hit.title(),
                    hit.author(),
                    ModrinthCatalog.formatDownloads(hit.downloads()),
                    hit.description()
            });
        }
        if (this.modrinthTable != null && this.modrinthModel.getRowCount() > 0
                && this.modrinthTable.getSelectedRow() < 0) {
            this.modrinthTable.setRowSelectionInterval(0, 0);
        }
        this.modrinthOffset = page.offset();
        this.modrinthTotal = Math.max(page.totalHits(), this.modrinthHits.size());
        String kindLabel = switch (this.modrinthProjectType) {
            case ModrinthCatalog.TYPE_RESOURCEPACK -> "ресурспаков";
            case ModrinthCatalog.TYPE_SHADER -> "шейдерпаков (Iris)";
            default -> "модов";
        };
        this.modrinthInfoLabel.setText("Показано " + this.modrinthHits.size()
                + " из " + this.modrinthTotal + " " + kindLabel
                + " · Двойной клик или «Скачать в 1 клик» для установки");
    }

    private void loadModrinthPage(String query, boolean append) {
        final String projectType = this.modrinthProjectType;
        final String cleanQuery = query == null ? "" : query.trim();
        final int offset = append ? this.modrinthOffset : 0;
        final int reqId = this.modrinthRequestSeq.incrementAndGet();
        this.modrinthQuery = cleanQuery;

        if (!append) {
            // Мгновенно отображаем предзагруженный каталог без ожидания сети
            applyModrinthPage(ModrinthCatalog.builtinPage(projectType, cleanQuery, 0), false);
        }
        this.modrinthLoading = true;
        this.modrinthInfoLabel.setText("Обновление каталога Modrinth… (" + this.modrinthHits.size() + " в списке)");

        new SwingWorker<ModrinthCatalog.SearchPage, Void>() {
            private String failure;

            @Override
            protected ModrinthCatalog.SearchPage doInBackground() {
                try {
                    return ModrinthCatalog.search(projectType, cleanQuery,
                            LauncherUi.this.config.selectedVersion(), 20, offset);
                } catch (IOException | InterruptedException | RuntimeException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    this.failure = exception.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                if (reqId != LauncherUi.this.modrinthRequestSeq.get()) {
                    return;
                }
                LauncherUi.this.modrinthLoading = false;
                ModrinthCatalog.SearchPage page = null;
                try {
                    page = get();
                } catch (Exception exception) {
                    this.failure = exception.getMessage();
                }
                if (page == null || page.hits().isEmpty()) {
                    if (LauncherUi.this.modrinthHits.isEmpty()) {
                        applyModrinthPage(ModrinthCatalog.builtinPage(projectType, cleanQuery, 0), false);
                    } else {
                        String kindLabel = ModrinthCatalog.TYPE_RESOURCEPACK.equals(projectType)
                                ? "ресурспаков" : "модов";
                        LauncherUi.this.modrinthInfoLabel.setText("Показано " + LauncherUi.this.modrinthHits.size()
                                + " " + kindLabel + " · Двойной клик или «Скачать в 1 клик» для установки");
                    }
                    return;
                }
                applyModrinthPage(page, append);
            }
        }.execute();
    }

    private void installSelectedModrinthHit() {
        if (this.busy || this.modrinthTable == null) {
            return;
        }
        int row = this.modrinthTable.getSelectedRow();
        if (row < 0 || row >= this.modrinthHits.size()) {
            showError("Выберите мод или ресурспак в таблице каталога Modrinth.");
            return;
        }
        ModrinthCatalog.Hit hit = this.modrinthHits.get(row);
        String projectType = hit.projectType() == null || hit.projectType().isBlank()
                ? this.modrinthProjectType : hit.projectType();
        setBusy(true);
        status("Скачивание: " + hit.title() + "…");
        new SwingWorker<Path, Void>() {
            private String failure;

            @Override
            protected Path doInBackground() {
                try {
                    LauncherUi.this.cancelRequested.set(false);
                    return ModrinthCatalog.installOneClick(
                            LauncherUi.this.paths,
                            projectType,
                            hit.projectId(),
                            LauncherUi.this.config.selectedVersion(),
                            new ProgressBridge(),
                            LauncherUi.this.cancelRequested::get
                    );
                } catch (IOException | InterruptedException | RuntimeException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    this.failure = exception.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                Path target = null;
                try {
                    target = get();
                } catch (Exception exception) {
                    this.failure = exception.getMessage();
                }
                if (target != null) {
                    status("Установлено: " + target.getFileName());
                    log("Modrinth 1-click: установлен " + hit.title() + " -> " + target);
                } else {
                    status("Ошибка скачивания");
                    showError("Не удалось скачать " + hit.title() + ": " + this.failure);
                }
            }
        }.execute();
    }

    private void login() {
        if (this.busy) {
            return;
        }
        setBusy(true);
        status("Ожидание входа в браузере…");
        SwingWorker<AuthSession, Void> worker = new SwingWorker<>() {
            private String failure;

            @Override
            protected AuthSession doInBackground() {
                try {
                    LauncherUi.this.cancelRequested.set(false);
                    return MicrosoftAuth.login(LauncherUi.this.config.clientId(),
                            url -> {
                                log("Ссылка для входа: " + url);
                                MicrosoftAuth.SYSTEM_BROWSER.open(url);
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                                        LauncherUi.this.dialogParent,
                                        "В браузере откроется страница входа Microsoft.\n"
                                                + "Если окно не открылось, скопируйте ссылку из вкладки «Логи»:\n" + url,
                                        "Вход через Microsoft", JOptionPane.INFORMATION_MESSAGE));
                            },
                            LauncherUi.this.cancelRequested::get);
                } catch (Http.CancelledException exception) {
                    this.failure = "вход отменён";
                    return null;
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    this.failure = exception.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                AuthSession result = null;
                try {
                    result = get();
                } catch (Exception exception) {
                    this.failure = exception.getCause() == null
                            ? exception.getMessage() : exception.getCause().getMessage();
                }
                if (result != null) {
                    LauncherUi.this.session = result;
                    try {
                        LauncherUi.this.authStore.save(result);
                    } catch (IOException exception) {
                        log("Не удалось сохранить данные входа: " + exception.getMessage());
                    }
                    reloadSession();
                    status("Вход выполнен: " + result.playerName());
                    log("Вход выполнен: " + result.playerName() + " (" + result.uuid() + ")");
                } else {
                    status("Вход не выполнен");
                    log("Ошибка входа: " + this.failure);
                    JOptionPane.showMessageDialog(LauncherUi.this.dialogParent,
                            "Не удалось выполнить вход:\n" + this.failure,
                            "Ошибка входа", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void logout() {
        this.authStore.clear();
        this.session = null;
        reloadSession();
        log("Данные входа удалены.");
    }

    // ─────────────────────────────── запуск игры ───────────────────────────────

    private void startGame() {
        if (this.busy || this.gameLauncher.isRunning()) {
            JOptionPane.showMessageDialog(this.dialogParent, "Игра уже запущена.",
                    "Запуск", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (this.session == null) {
            JOptionPane.showMessageDialog(this.dialogParent,
                    "Сначала войдите в учётную запись Microsoft — без токена игра не запустится.",
                    "Требуется вход", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Path manual = null;
        if (!this.config.javaExecutable().isBlank()) {
            manual = manualJava();
            if (manual == null) {
                return;
            }
        }
        // Внутрь рабочего потока переменная попадает один раз: так она честно final.
        final Path preferredJava = manual;
        this.cancelRequested.set(false);
        setBusy(true);
        status("Подготовка к запуску…");
        log("Запуск игры: версия " + this.config.selectedVersion() + ", Fabric Loader " + LOADER_VERSION);

        SwingWorker<Boolean, String> worker = new SwingWorker<>() {
            private String failure;

            @Override
            protected Boolean doInBackground() {
                try {
                    if (LauncherUi.this.session.needsRefresh()) {
                        publish("Обновление токена…");
                        AuthSession refreshed = MicrosoftAuth.refresh(LauncherUi.this.session);
                        LauncherUi.this.session = refreshed;
                        LauncherUi.this.authStore.save(refreshed);
                        LauncherUi.this.cancelRequested.set(false);
                    }
                    GameInstaller.Installation installation = new GameInstaller(
                            LauncherUi.this.paths,
                            LauncherUi.this.config.selectedVersion(),
                            LauncherUi.this.config.selectedLoader(),
                            LOADER_VERSION,
                            new ProgressBridge(),
                            LauncherUi.this.cancelRequested::get).install();
                    publish("Игра установлена, подготовка модов…");

                    if (LauncherUi.this.config.installMods()) {
                        installMods(installation);
                    }
                    Path executable = preferredJava != null ? preferredJava : installation.javaExecutable();
                    if (executable == null) {
                        this.failure = "не удалось подготовить Java " + JavaRuntime.REQUIRED_MAJOR
                                + ": укажите путь к java.exe (java) на вкладке «Настройки»";
                        return Boolean.FALSE;
                    }
                    LauncherUi.this.logPanel.append("[лаунчер] Java: " + executable + " ("
                            + (preferredJava != null ? "путь из настроек" : installation.javaSource()) + ")");
                    GameLauncher.LaunchPlan plan = LauncherUi.this.gameLauncher.plan(installation,
                            LauncherUi.this.paths, LauncherUi.this.config,
                            LauncherUi.this.session, executable);
                    publish("Запуск…");
                    log("Команда запуска: " + String.join(" ", shortened(plan.command())));
                    LauncherUi.this.gameLauncher.launch(plan,
                            line -> LauncherUi.this.logPanel.append(line),
                            () -> SwingUtilities.invokeLater(() -> {
                                setBusy(false);
                                status("Игра завершена");
                                log("Игра завершена.");
                            }));
                    return Boolean.TRUE;
                } catch (Http.CancelledException exception) {
                    this.failure = "отменено пользователем";
                    return Boolean.FALSE;
                } catch (IOException | InterruptedException | RuntimeException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    this.failure = exception.getMessage();
                    return Boolean.FALSE;
                }
            }

            @Override
            protected void process(List<String> chunks) {
                for (String message : chunks) {
                    status(message);
                    log(message);
                }
            }

            @Override
            protected void done() {
                boolean started;
                try {
                    started = Boolean.TRUE.equals(get());
                } catch (Exception exception) {
                    started = false;
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    this.failure = cause.getMessage();
                }
                setBusy(started);
                if (started) {
                    status("Игра запущена — закрытие лаунчера…");
                    LauncherUi.this.config.save(LauncherUi.this.paths.launcherConfig());
                    closeLauncherOnGameStart();
                } else {
                    status("Запуск не выполнен");
                    log("Ошибка запуска: " + this.failure);
                    JOptionPane.showMessageDialog(LauncherUi.this.dialogParent,
                            "Не удалось запустить игру:\n" + this.failure,
                            "Ошибка запуска", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void closeLauncherOnGameStart() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        javax.swing.Timer exitTimer = new javax.swing.Timer(300, event -> {
            if (this.dialogParent instanceof java.awt.Window window) {
                window.setVisible(false);
                window.dispose();
            }
            System.exit(0);
        });
        exitTimer.setRepeats(false);
        exitTimer.start();
    }

    private void installMods(GameInstaller.Installation installation) throws IOException, InterruptedException {
        String ver = this.config.selectedVersion();
        String loader = this.config.selectedLoader();
        if (ModpackProfile.LOADER_VANILLA.equalsIgnoreCase(loader)) {
            log("Выбран загрузчик Vanilla (" + ver + ") — моды не требуются.");
            return;
        }
        if (ModpackProfile.isAetherGlassSupported(ver, loader)) {
            ModInstaller installer = ModInstaller.fromResource(this.paths, new ProgressBridge(),
                    this.cancelRequested::get);
            List<Path> installed = installer.install();
            log("Установлено модов оптимизации (1.21.11): " + installed.size());
            if (installer.manifest().fabricApi() != null) {
                ModInstaller.Manifest.Mod api = installer.manifest().fabricApi();
                Path target = this.paths.mods().resolve(api.fileName());
                if (!Files.isRegularFile(target) || !Http.sha512(target).equalsIgnoreCase(api.sha512())) {
                    Http.download(api.url(), target, api.size(), api.sha1(), new ProgressBridge(),
                            this.cancelRequested::get);
                    log("Установлен Fabric API: " + api.fileName());
                } else {
                    log("Fabric API уже установлен: " + api.fileName());
                }
            }
            if (this.config.installAetherGlassClient()) {
                Path clientJar = findClientModJar();
                if (clientJar == null) {
                    if (!showMissingClientModDialog()) {
                        throw new IOException("запуск отменён: не найден собранный мод клиента");
                    }
                    log("Внимание: мод клиента AetherGlass не найден, игра запускается без него.");
                } else {
                    Path copied = installer.installClientMod(clientJar);
                    log("Мод клиента установлен: " + copied.getFileName());
                }
            } else {
                log("Галочка «Установить AetherGlass Client» снята — запуск без мода клиента.");
            }
            refreshModsTable();
        } else if (ModpackProfile.LOADER_FABRIC.equalsIgnoreCase(loader)) {
            // Блок 5.3: Авто-докачка базовых модов оптимизации для любой другой версии Fabric
            log("Авто-докачка базовых модов оптимизации для Fabric " + ver + "…");
            String[] coreOptimizationSlugs = {"P7dR8mSH", "AANobbMI", "gvQqBUqZ", "uXXizFIs", "NNAgCjsB"};
            for (String projectId : coreOptimizationSlugs) {
                if (this.cancelRequested.get()) {
                    break;
                }
                try {
                    Path downloaded = ModrinthCatalog.installOneClick(
                            this.paths, ModrinthCatalog.TYPE_MOD, projectId, ver,
                            new ProgressBridge(), this.cancelRequested::get);
                    log("Авто-докачка (" + ver + "): " + downloaded.getFileName());
                } catch (Exception ignored) {
                    // Если конкретный мод ещё не выпущен под старую версию, пропускаем без срыва запуска
                }
            }
        }
    }

    private boolean showMissingClientModDialog() {
        int[] answer = new int[1];
        try {
            SwingUtilities.invokeAndWait(() -> answer[0] = JOptionPane.showConfirmDialog(this.dialogParent,
                    "Мод клиента AetherGlass.jar не найден: ни рядом с лаунчером,\n"
                            + "ни в каталоге сборки, ни в каталоге модов игры (" + LauncherConfig.defaultGameDirectory().resolve("mods")
                            + ").\n\n"
                            + "Запустить игру без клиента AetherGlass?",
                    "Мод клиента не найден", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE));
        } catch (Exception exception) {
            return false;
        }
        return answer[0] == JOptionPane.YES_OPTION;
    }

    /**
     * Ищет собранный jar клиента: рядом с запущенным exe, в каталоге сборки
     * проекта и в каталоге модов игры.
     */
    static Path findClientModJar() {
        List<Path> roots = new ArrayList<>();
        // Автономная поставка: exe лежит в одном каталоге с модом клиента.
        Path self = selfDirectory();
        if (self != null) {
            roots.add(self);
            roots.add(self.resolve("mods"));
        }
        roots.add(Path.of("client", "build", "libs"));
        roots.add(Path.of("..", "client", "build", "libs"));
        // Сюда мод кладёт установщик: без этого пользователь получил бы
        // ванильную игру с модами оптимизации, но без самого клиента.
        roots.add(LauncherConfig.defaultGameDirectory().resolve("mods"));
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (var stream = Files.list(root)) {
                Path found = stream.filter(Files::isRegularFile)
                        .filter(LauncherUi::isClientMod)
                        .max(Comparator.comparingLong(path -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException exception) {
                                return Long.MIN_VALUE;
                            }
                        }))
                        .orElse(null);
                if (found != null) {
                    return found;
                }
            } catch (IOException exception) {
                // Каталог недоступен — пробуем следующий вариант.
            }
        }
        return null;
    }

    /**
     * Каталог, из которого запущен лаунчер. Изнутри jar это не определяется:
     * launch4j распаковывает jar во временный каталог, поэтому путь берётся у
     * самого процесса — именно он указывает на exe.
     */
    private static Path selfDirectory() {
        try {
            String command = ProcessHandle.current().info().command().orElse(null);
            if (command != null) {
                Path parent = Path.of(command).getParent();
                if (parent != null && Files.isDirectory(parent)) {
                    return parent;
                }
            }
        } catch (SecurityException exception) {
            // Платформа не отдаёт путь к процессу — работаем без этого каталога.
        }
        return null;
    }

    /** Это собранный мод клиента: не исходники и не сторонняя библиотека. */
    private static boolean isClientMod(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.startsWith("aetherglass") && name.endsWith(".jar") && !name.endsWith("-sources.jar");
    }

    private static List<String> shortened(List<String> command) {
        List<String> result = new ArrayList<>();
        for (String part : command) {
            result.add(part.length() > 120 ? part.substring(0, 117) + "…" : part);
        }
        return result;
    }

    /** Проверяет выбранную Java и при необходимости подбирает её автоматически. */
    /**
     * Java, путь к которой игрок указал в настройках.
     *
     * <p>Если путь не задан, возвращается {@code null} — тогда Java подберёт
     * установщик: сначала системная, а если её нет или она стара — официальный
     * рантайм Mojang той версии, которую требует игра. Неподходящий путь —
     * это ошибка, а не повод молча запустить что-то другое.</p>
     */
    private Path manualJava() {
        JavaRuntime.JavaInfo info = JavaRuntime.inspect(Path.of(this.config.javaExecutable()));
        if (!info.meetsRequirement()) {
            showError("Выбранная Java не подходит: "
                    + (info.error() != null ? info.error()
                    : "версия " + info.versionText() + ", требуется " + JavaRuntime.REQUIRED_MAJOR
                    + " или новее (Minecraft 1.21.11)"));
            return null;
        }
        return info.executable();
    }

    // ─────────────────────────────── прочие действия ───────────────────────────────

    private void detectJava() {
        Path detected = JavaRuntime.detect();
        if (detected == null) {
            this.javaInfoLabel.setText("Подходящая Java не найдена (нужна версия "
                    + JavaRuntime.REQUIRED_MAJOR + " или новее). Ничего страшного: при установке "
                    + "игры лаунчер сам скачает официальный рантайм Mojang.");
            this.javaInfoLabel.setForeground(UiTheme.DANGER);
            return;
        }
        this.javaField.setText(detected.toString());
        this.config.javaExecutable(detected.toString());
        JavaRuntime.JavaInfo info = JavaRuntime.inspect(detected);
        this.javaInfoLabel.setText("Найдена Java " + info.versionText() + " (" + info.vendor() + "): " + detected);
        this.javaInfoLabel.setForeground(info.meetsRequirement() ? UiTheme.SUCCESS : UiTheme.DANGER);
    }

    private void saveSettings() {
        Path gameDir = Path.of(this.gameDirField.getText().isBlank()
                ? LauncherConfig.defaultGameDirectory().toString() : this.gameDirField.getText().trim());
        try {
            Files.createDirectories(gameDir);
        } catch (IOException exception) {
            showError("Не удалось создать каталог игры " + gameDir + ": " + exception.getMessage());
            return;
        }
        this.config.gameDirectory(gameDir);
        this.config.javaExecutable(this.javaField.getText());
        this.config.clientId(this.clientIdField.getText());
        this.config.installMods(this.installModsBox.isSelected());
        this.config.windowWidth(parseIntOrZero(this.widthField.getText()));
        this.config.windowHeight(parseIntOrZero(this.heightField.getText()));
        this.config.save(this.paths.launcherConfig());
        this.paths = new LauncherPaths(this.config.gameDirectory());
        this.authStore = new AuthStore(this.paths.authFile());
        reloadSession();
        log("Настройки сохранены. Каталог игры: " + this.paths.base());
        status("Настройки сохранены");
        refreshModsTable();
    }

    private static int parseIntOrZero(String text) {
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private void loadVersionsInBackground() {
        SwingWorker<List<VersionIndex.Entry>, Void> worker = new SwingWorker<>() {
            private String failure;

            @Override
            protected List<VersionIndex.Entry> doInBackground() {
                try {
                    return VersionIndex.fetch().releases();
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    this.failure = exception.getMessage();
                    return List.of();
                }
            }

            @Override
            protected void done() {
                List<VersionIndex.Entry> entries;
                try {
                    entries = get();
                } catch (Exception exception) {
                    entries = List.of();
                }
                if (entries.isEmpty()) {
                    LauncherUi.this.versionBox.setModel(
                            new DefaultComboBoxModel<>(new String[]{LauncherUi.this.config.selectedVersion()}));
                    log("Не удалось получить список версий: " + this.failure);
                    return;
                }
                LauncherUi.this.availableVersions = entries;
                String[] ids = new String[entries.size()];
                for (int i = 0; i < ids.length; i++) {
                    ids[i] = entries.get(i).id();
                }
                DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(ids);
                LauncherUi.this.versionBox.setModel(model);
                String selected = LauncherUi.this.config.selectedVersion();
                if (model.getIndexOf(selected) >= 0) {
                    model.setSelectedItem(selected);
                } else if (ids.length > 0) {
                    model.setSelectedItem(ids[0]);
                }
                LauncherUi.this.versionBox.addActionListener(event -> {
                    Object item = LauncherUi.this.versionBox.getSelectedItem();
                    if (item != null) {
                        LauncherUi.this.config.selectedVersion(item.toString());
                    }
                });
                log("Доступно версий Minecraft: " + ids.length);
            }
        };
        worker.execute();
    }

    private void loadManifestInBackground() {
        try {
            this.modManifest = ModInstaller.fromResource(this.paths, null, null).manifest();
            refreshModsTable();
        } catch (IOException exception) {
            log("Не удалось прочитать манифест модов: " + exception.getMessage());
        }
    }

    private void refreshModsTable() {
        this.modsModel.setRowCount(0);
        if (this.modManifest == null) {
            return;
        }
        for (ModInstaller.Manifest.Mod mod : this.modManifest.mods()) {
            this.modsModel.addRow(new Object[]{mod.name(), mod.version(), mod.purpose(), statusOf(mod)});
        }
        ModInstaller.Manifest.Mod api = this.modManifest.fabricApi();
        if (api != null) {
            this.modsModel.addRow(new Object[]{api.name(), api.version(), api.purpose(), statusOf(api)});
        }
        Path clientJar = findClientModJar();
        this.modsModel.addRow(new Object[]{"AetherGlass Client (мод клиента)",
                clientJar == null ? "не собран" : "локальная сборка",
                "Пользовательское меню и визуальные эффекты",
                clientJar == null ? "не найден" : "готов к установке"});
    }

    private String statusOf(ModInstaller.Manifest.Mod mod) {
        Path disabledTarget = this.paths.mods().resolve(mod.fileName() + ".disabled");
        if (Files.isRegularFile(disabledTarget)) {
            return "отключён (.disabled)";
        }
        Path target = this.paths.mods().resolve(mod.fileName());
        if (!Files.isRegularFile(target)) {
            return "отсутствует";
        }
        try {
            if (Files.size(target) != mod.size()) {
                return "повреждён (размер)";
            }
            return Http.sha512(target).equalsIgnoreCase(mod.sha512()) ? "установлен" : "повреждён (сумма)";
        } catch (IOException exception) {
            return "не проверен: " + exception.getMessage();
        }
    }

    private JPanel screenshotsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        this.screenshotsGrid.setOpaque(false);
        refreshScreenshotsGallery();

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        controls.setOpaque(false);
        JButton refreshBtn = UiKit.primaryButton("Обновить галерею");
        JButton openFolderBtn = UiKit.button("Открыть папку скриншотов");
        refreshBtn.addActionListener(e -> refreshScreenshotsGallery());
        openFolderBtn.addActionListener(e -> openScreenshotsFolder());
        controls.add(refreshBtn);
        controls.add(openFolderBtn);

        panel.add(UiKit.card("Галерея скриншотов (<gameDir>/screenshots)", UiKit.scroll(this.screenshotsGrid)), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshScreenshotsGallery() {
        this.screenshotsGrid.removeAll();
        List<Path> files = new ArrayList<>();
        try {
            Path dir = screenshotsDirectory();
            if (Files.isDirectory(dir)) {
                try (var stream = Files.list(dir)) {
                    files.addAll(stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                            .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                            .limit(12)
                            .toList());
                }
            }
        } catch (IOException ignored) {
        }

        if (files.isEmpty()) {
            this.screenshotsGrid.add(UiKit.matteNeonGridCard(
                    "Скриншотов пока нет",
                    "Нажмите F2 в игре — снимки появятся здесь автоматически",
                    () -> false,
                    this::openScreenshotsFolder
            ));
        } else {
            for (Path shot : files) {
                String name = shot.getFileName().toString();
                long kb = 0L;
                try {
                    kb = Math.max(1L, Files.size(shot) / 1024L);
                } catch (IOException ignored) {
                }
                this.screenshotsGrid.add(UiKit.screenshotThumbnailCard(
                        shot,
                        name,
                        kb + " КБ · Клик — в буфер обмена и открыть",
                        () -> copyAndOpenScreenshot(shot)
                ));
            }
        }
        this.screenshotsGrid.revalidate();
        this.screenshotsGrid.repaint();
    }

    private void copyAndOpenScreenshot(Path file) {
        try {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                var img = javax.imageio.ImageIO.read(file.toFile());
                if (img != null) {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                            new java.awt.datatransfer.Transferable() {
                                @Override
                                public java.awt.datatransfer.DataFlavor[] getTransferDataFlavors() {
                                    return new java.awt.datatransfer.DataFlavor[]{java.awt.datatransfer.DataFlavor.imageFlavor};
                                }

                                @Override
                                public boolean isDataFlavorSupported(java.awt.datatransfer.DataFlavor flavor) {
                                    return java.awt.datatransfer.DataFlavor.imageFlavor.equals(flavor);
                                }

                                @Override
                                public Object getTransferData(java.awt.datatransfer.DataFlavor flavor) {
                                    return img;
                                }
                            }, null);
                }
            }
            status("Скриншот скопирован в буфер обмена: " + file.getFileName());
            openDirectoryInSystem(file.getParent());
        } catch (Exception e) {
            status("Открытие папки скриншотов: " + file.getFileName());
        }
    }

    private void toggleSelectedInstalledMod() {
        if (this.installedModsTable == null || this.modManifest == null) {
            return;
        }
        int row = this.installedModsTable.getSelectedRow();
        List<ModInstaller.Manifest.Mod> list = this.modManifest.mods();
        if (row < 0 || row >= list.size()) {
            status("Выберите мод из таблицы «Оптимизационные моды» для переключения Вкл / Выкл");
            return;
        }
        ModInstaller.Manifest.Mod mod = list.get(row);
        Path activeFile = this.paths.mods().resolve(mod.fileName());
        Path disabledFile = this.paths.mods().resolve(mod.fileName() + ".disabled");
        try {
            if (Files.isRegularFile(activeFile)) {
                Files.move(activeFile, disabledFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                status("Мод отключён: " + mod.name() + " (.disabled)");
            } else if (Files.isRegularFile(disabledFile)) {
                Files.move(disabledFile, activeFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                status("Мод включён: " + mod.name());
            } else {
                status("Мод «" + mod.name() + "» ещё не скачан — нажмите «Установить / обновить»");
            }
            refreshModsTable();
        } catch (IOException e) {
            showError("Не удалось переключить мод: " + e.getMessage());
        }
    }

    private void checkAndUpdateFromGitHub() {
        if (this.busy) {
            return;
        }
        setBusy(true);
        status("Проверка обновлений на GitHub…");
        new SwingWorker<Path, Void>() {
            private AutoUpdater.ReleaseInfo release;
            private String error;

            @Override
            protected Path doInBackground() {
                try {
                    this.release = AutoUpdater.checkLatestRelease();
                    LauncherUi.this.cancelRequested.set(false);
                    return AutoUpdater.downloadUpdate(
                            this.release,
                            LauncherUi.this.paths,
                            new ProgressBridge()::onFile,
                            LauncherUi.this.cancelRequested::get
                    );
                } catch (Exception e) {
                    this.error = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                if (this.error != null || this.release == null) {
                    status("Проверка обновлений: " + (this.error != null ? this.error : "нет соединения"));
                    return;
                }
                Path downloaded = null;
                try {
                    downloaded = get();
                } catch (Exception ignored) {
                }
                if (downloaded != null && Files.isRegularFile(downloaded)) {
                    String msg = "Скачан релиз " + this.release.tagName()
                            + " (" + downloaded.getFileName() + ").\nПерезапустить лаунчер сейчас для применения обновления?";
                    if (java.awt.GraphicsEnvironment.isHeadless()) {
                        status("Обновление " + this.release.tagName() + " готово: " + downloaded);
                        return;
                    }
                    int ans = JOptionPane.showConfirmDialog(
                            LauncherUi.this.dialogParent,
                            msg,
                            "Автообновление AetherGlass",
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    if (ans == JOptionPane.YES_OPTION) {
                        try {
                            AutoUpdater.applyAndRestart(downloaded);
                        } catch (IOException ex) {
                            showError("Не удалось перезапустить лаунчер: " + ex.getMessage());
                        }
                    } else {
                        status("Обновление скачано в " + downloaded + " (применится при следующем запуске)");
                    }
                }
            }
        }.execute();
    }

    private void runCrashDoctorDialog() {
        CrashDoctor.Diagnosis diag = CrashDoctor.analyze(this.paths, this.config);
        log("[Доктор крашей] " + diag.title() + " — " + diag.summary());
        status("Доктор крашей: " + diag.title());
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        String fullText = diag.title() + "\n\n" + diag.summary() + "\n\n" + diag.details();
        if (diag.fixType() != CrashDoctor.FixType.NONE) {
            Object[] options = {diag.fixType().label(), "Закрыть"};
            int choice = JOptionPane.showOptionDialog(
                    this.dialogParent,
                    fullText,
                    "Умный Доктор Крашей AetherGlass",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0]
            );
            if (choice == 0) {
                try {
                    String fixed = CrashDoctor.applyFix(diag, this.config, this.paths);
                    this.memorySlider.setValue(this.config.maxMemoryMb());
                    refreshModsTable();
                    status(fixed);
                    log("[Доктор крашей] " + fixed);
                } catch (IOException e) {
                    showError("Не удалось применить исправление: " + e.getMessage());
                }
            }
        } else {
            JOptionPane.showMessageDialog(
                    this.dialogParent,
                    fullText,
                    "Умный Доктор Крашей AetherGlass",
                    JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private void exportActiveModpack() {
        ModpackProfile active = this.config.activeModpack();
        if (active == null) {
            return;
        }
        try {
            Path exportsDir = this.paths.base().resolve("exports");
            Files.createDirectories(exportsDir);
            Path outZip = exportsDir.resolve(active.id() + ".zip");
            ModpackArchive.exportModpack(active, this.paths, outZip);
            status("Сборка экспортирована: " + outZip.getFileName());
            log("Экспорт сборки завершён: " + outZip.toAbsolutePath());
            openDirectoryInSystem(exportsDir);
        } catch (IOException e) {
            showError("Не удалось экспортировать сборку: " + e.getMessage());
        }
    }

    private void importModpackDialog() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        JFileChooser chooser = new JFileChooser(this.paths.base().toFile());
        chooser.setDialogTitle("Выберите архив сборки (.zip / .mrpack) или мод (.jar)");
        if (chooser.showOpenDialog(this.dialogParent) == JFileChooser.APPROVE_OPTION) {
            Path selected = chooser.getSelectedFile().toPath();
            try {
                String res = ModpackArchive.importSmartFile(selected, this.paths, this.config);
                syncModpackControls();
                refreshModsTable();
                status(res);
                log(res);
            } catch (IOException e) {
                showError("Ошибка импорта: " + e.getMessage());
            }
        }
    }

    private void backupWorldsInBackground() {
        new SwingWorker<Path, Void>() {
            private String err;

            @Override
            protected Path doInBackground() {
                try {
                    return ModpackArchive.backupWorldsAndConfigs(LauncherUi.this.paths);
                } catch (IOException e) {
                    this.err = e.getMessage();
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    Path zip = get();
                    if (zip != null) {
                        status("Создан бэкап миров и настроек: " + zip.getFileName());
                        log("Бэкап сохранён: " + zip.toAbsolutePath());
                    } else if (this.err != null) {
                        showError("Ошибка бэкапа: " + this.err);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    @SuppressWarnings("unchecked")
    private void installDragAndDrop(JComponent component) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            new java.awt.dnd.DropTarget(component, new java.awt.dnd.DropTargetAdapter() {
                @Override
                public void drop(java.awt.dnd.DropTargetDropEvent dtde) {
                    try {
                        dtde.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                        Object data = dtde.getTransferable().getTransferData(
                                java.awt.datatransfer.DataFlavor.javaFileListFlavor);
                        if (data instanceof List<?> rawFiles) {
                            for (Object obj : rawFiles) {
                                if (obj instanceof java.io.File f) {
                                    String msg = ModpackArchive.importSmartFile(
                                            f.toPath(), LauncherUi.this.paths, LauncherUi.this.config);
                                    status(msg);
                                    log("[Drag & Drop] " + msg);
                                }
                            }
                            syncModpackControls();
                            refreshModsTable();
                        }
                        dtde.dropComplete(true);
                    } catch (Exception e) {
                        dtde.dropComplete(false);
                        status("Ошибка Drag & Drop: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private void installModsInBackground() {
        if (this.busy) {
            return;
        }
        setBusy(true);
        status("Установка модов…");
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    LauncherUi.this.cancelRequested.set(false);
                    ModInstaller installer = ModInstaller.fromResource(LauncherUi.this.paths,
                            new ProgressBridge(), LauncherUi.this.cancelRequested::get);
                    List<Path> installed = installer.install();
                    ModInstaller.Manifest.Mod api = installer.manifest().fabricApi();
                    if (api != null) {
                        Path target = LauncherUi.this.paths.mods().resolve(api.fileName());
                        if (!Files.isRegularFile(target)
                                || !Http.sha512(target).equalsIgnoreCase(api.sha512())) {
                            Http.download(api.url(), target, api.size(), api.sha1(), new ProgressBridge(),
                                    LauncherUi.this.cancelRequested::get);
                        }
                    }
                    return "Установлено модов: " + installed.size()
                            + (api == null ? "" : " + Fabric API");
                } catch (Http.CancelledException exception) {
                    return "Установка отменена";
                } catch (IOException | InterruptedException exception) {
                    if (exception instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                    }
                    return "Ошибка: " + exception.getMessage();
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                String message;
                try {
                    message = get();
                } catch (Exception exception) {
                    message = "Ошибка: " + exception.getMessage();
                }
                status(message);
                log(message);
                refreshModsTable();
            }
        };
        worker.execute();
    }

    // ─────────────────────────────── состояние ───────────────────────────────

    private void setBusy(boolean value) {
        this.busy = value;
        updateButtons();
    }

    private void updateButtons() {
        boolean running = this.gameLauncher.isRunning();
        this.playButton.setEnabled(!this.busy && !running);
        this.cancelButton.setEnabled(this.busy && !running);
        this.stopButton.setEnabled(running);
        if (!this.busy && !running) {
            this.progressBar.setValue(0);
            this.progressBar.setIndeterminate(false);
        }
    }

    private void status(String text) {
        this.statusLabel.setText(text);
    }

    private void log(String text) {
        this.logPanel.append("[лаунчер] " + text);
    }

    private void showError(String message) {
        log("Ошибка: " + message);
        JOptionPane.showMessageDialog(this.dialogParent, message, "Ошибка", JOptionPane.ERROR_MESSAGE);
    }

    /** Останавливает игру и сохраняет настройки: вызывается при закрытии окна. */
    public void shutdown() {
        this.gameLauncher.stop();
        this.config.save(this.paths.launcherConfig());
    }

    /** Передаёт ход загрузки из рабочих потоков в строку состояния и индикатор. */
    private final class ProgressBridge implements GameInstaller.Progress {
        @Override
        public void stage(String text) {
            SwingUtilities.invokeLater(() -> {
                status(text);
                LauncherUi.this.progressBar.setIndeterminate(text.startsWith("Ресурсы:"));
            });
            LauncherUi.this.logPanel.append("[установка] " + text);
        }

        @Override
        public void onFile(String name, long done, long total) {
            if (total <= 0) {
                return;
            }
            int percent = (int) Math.max(0, Math.min(100, done * 100 / total));
            SwingUtilities.invokeLater(() -> {
                LauncherUi.this.progressBar.setIndeterminate(false);
                LauncherUi.this.progressBar.setValue(percent);
                status(name + " — " + percent + "%");
            });
        }
    }
}
