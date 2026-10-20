package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.auth.AuthStore;
import com.aetherglass.launcher.auth.MicrosoftAuth;
import com.aetherglass.launcher.core.GameInstaller;
import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.JavaRuntime;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.LauncherPaths;
import com.aetherglass.launcher.core.ModInstaller;
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
            new String[]{"1.21.11", "1.21.4", "1.20.4", "1.20.1", "1.19.4", "1.18.2", "1.16.5"}));
    private final JComboBox<String> loaderBox = UiKit.combo(new JComboBox<>(
            ModpackProfile.SUPPORTED_LOADERS.toArray(new String[0])));
    private final JComboBox<ModpackProfile> modpackBox = UiKit.combo(new JComboBox<>());
    private final JCheckBox aetherClientBox = UiKit.check(
            new JCheckBox("Установить AetherGlass Client"));
    private final DefaultTableModel modpacksTableModel = new DefaultTableModel(
            new Object[]{"Сборка", "Версия MC", "Загрузчик", "AetherGlass Client", "Оптимизация"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTextField newPackNameField = UiKit.field(new JTextField(16));
    private final JLabel accountLabel = UiKit.chip("Вход не выполнен", UiTheme.DANGER);
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
    private final JTextField modrinthQueryField = UiKit.field(new JTextField(20));
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
        this.root = buildUi();
        reloadSession();
        loadVersionsInBackground();
        loadManifestInBackground();
        log("Лаунчер запущен. Каталог игры: " + this.paths.base());
        log("Операционная система: " + Rules.osName() + " (" + Rules.osArch() + ")");
    }

    // ─────────────────────────────── построение интерфейса ───────────────────────────────

    private JComponent buildUi() {
        JPanel panel = UiKit.background();

        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 6, 20));
        JLabel title = new JLabel("AETHERGLASS");
        title.setFont(UiTheme.TITLE);
        title.setForeground(UiTheme.TEXT);
        JLabel subtitle = UiKit.muted("Matte Black · Neon Launcher · 1.21.11");
        JPanel titles = new JPanel(new BorderLayout(0, 2));
        titles.setOpaque(false);
        titles.add(title, BorderLayout.NORTH);
        titles.add(subtitle, BorderLayout.SOUTH);
        header.add(titles, BorderLayout.WEST);
        header.add(this.accountLabel, BorderLayout.EAST);

        this.tabs = new JTabbedPane(JTabbedPane.LEFT);
        UiKit.styleTabs(this.tabs);
        this.tabs.addTab("Главная", gameTab());
        this.tabs.addTab("Сборки", modpacksTab());
        this.tabs.addTab("Моды", modsTab());
        this.tabs.addTab("Настройки", settingsTab());
        this.tabs.addTab("Логи", this.logPanel);
        syncModpackControls();

        panel.add(header, BorderLayout.NORTH);
        panel.add(this.tabs, BorderLayout.CENTER);
        updateButtons();
        return panel;
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
        accountRow.add(UiKit.muted("Ник:"));
        accountRow.add(this.offlineNickField);
        accountRow.add(offlineButton);
        accountRow.add(loginButton);
        accountRow.add(logoutButton);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 4, 6, 4);
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.gridx = 0;
        constraints.gridy = 0;
        settings.add(UiKit.muted("Активная сборка:"), constraints);
        constraints.gridx = 1;
        settings.add(this.modpackBox, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        settings.add(UiKit.muted("Версия и загрузчик:"), constraints);
        constraints.gridx = 1;
        JPanel verLoaderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        verLoaderRow.setOpaque(false);
        verLoaderRow.add(this.versionBox);
        verLoaderRow.add(this.loaderBox);
        verLoaderRow.add(this.aetherClientBox);
        settings.add(verLoaderRow, constraints);

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
        if (totalMemory > 0) {
            JLabel hint = UiKit.muted("из " + totalMemory + " МБ установлено в системе");
            memory.add(hint);
        }
        settings.add(memory, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        settings.add(UiKit.muted("Папки игры:"), constraints);
        constraints.gridx = 1;
        JPanel foldersRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        foldersRow.setOpaque(false);
        JButton gameRootBtn = UiKit.button("Коренная папка");
        JButton gameModsBtn = UiKit.button("Папка Mods");
        gameRootBtn.addActionListener(event -> openRootFolder());
        gameModsBtn.addActionListener(event -> openModsFolder());
        foldersRow.add(gameRootBtn);
        foldersRow.add(gameModsBtn);
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
        columnsRow.add(UiKit.card("✦ ВИЗУАЛ И КЛИЕНТ", col1));
        columnsRow.add(UiKit.card("◎ ОПТИМИЗАЦИЯ", col2));
        columnsRow.add(UiKit.card("◈ ИНТЕРФЕЙС И HUD", col3));

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
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        JTable table = new JTable(this.modpacksTableModel);
        UiKit.styleTable(table);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = table.getSelectedRow();
            List<ModpackProfile> packs = this.config.modpacks();
            if (row >= 0 && row < packs.size()) {
                ModpackProfile pack = packs.get(row);
                this.config.selectModpack(pack.id());
                syncModpackControls();
            }
        });

        JPanel createRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        createRow.setOpaque(false);
        this.newPackNameField.setToolTipText("Название новой сборки (например: My PvP 1.20.1)");
        JButton createPackBtn = UiKit.primaryButton("Создать сборку");
        createPackBtn.addActionListener(e -> {
            String name = this.newPackNameField.getText().trim();
            String ver = this.versionBox.getSelectedItem() != null
                    ? this.versionBox.getSelectedItem().toString() : this.config.selectedVersion();
            String loader = this.loaderBox.getSelectedItem() != null
                    ? this.loaderBox.getSelectedItem().toString() : this.config.selectedLoader();
            ModpackProfile created = createModpack(name, ver, loader,
                    this.aetherClientBox.isEnabled() && this.aetherClientBox.isSelected(),
                    this.installModsBox.isSelected());
            this.newPackNameField.setText("");
            status("Создана сборка: " + created.name());
            log("Создана сборка: " + created);
        });
        createRow.add(UiKit.muted("Название:"));
        createRow.add(this.newPackNameField);
        createRow.add(createPackBtn);

        panel.add(UiKit.card("Карточки сборок (Modpacks — Vanilla / Fabric / Forge / NeoForge / Quilt)",
                UiKit.scroll(table)), BorderLayout.CENTER);
        panel.add(UiKit.card("Новая сборка", createRow), BorderLayout.SOUTH);
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
                : "AetherGlass Client доступен только для 1.21.11 (Fabric). Для этой версии будут запущены ваши моды.");
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
        for (ModpackProfile p : packs) {
            this.modpacksTableModel.addRow(new Object[]{
                    p.name(),
                    p.minecraftVersion(),
                    p.loader(),
                    p.installAetherGlassClient() ? "Включён (1.21.11)" : "Отключён (свои моды)",
                    p.autoOptimizeMods() ? "Авто (Sodium/Lithium)" : "Базовый"
            });
        }
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
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchBar.setOpaque(false);
        JButton typeModsBtn = UiKit.primaryButton("Моды (Fabric)");
        JButton typeRpBtn = UiKit.button("Ресурспаки");
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
        searchBtn.addActionListener(event -> loadModrinthPage(this.modrinthQueryField.getText().trim(), false));
        this.modrinthQueryField.addActionListener(event -> loadModrinthPage(this.modrinthQueryField.getText().trim(), false));
        popularBtn.addActionListener(event -> {
            this.modrinthQueryField.setText("");
            loadModrinthPage("", false);
        });
        moreBtn.addActionListener(event -> loadModrinthPage(this.modrinthQuery, true));
        installOneClickBtn.addActionListener(event -> installSelectedModrinthHit());

        searchBar.add(typeModsBtn);
        searchBar.add(typeRpBtn);
        searchBar.add(this.modrinthQueryField);
        searchBar.add(searchBtn);
        searchBar.add(popularBtn);
        searchBar.add(moreBtn);
        searchBar.add(installOneClickBtn);

        this.modrinthTable = new JTable(this.modrinthModel);
        UiKit.styleTable(this.modrinthTable);
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
        catalogPanel.add(searchBar, BorderLayout.NORTH);
        catalogPanel.add(catalogScroll, BorderLayout.CENTER);
        catalogPanel.add(this.modrinthInfoLabel, BorderLayout.SOUTH);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        JTable table = new JTable(this.modsModel);
        UiKit.styleTable(table);
        top.add(UiKit.scroll(table), BorderLayout.CENTER);
        top.add(UiKit.muted("Моды ставятся в папку игры и обновляются той же кнопкой."), BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        controls.setOpaque(false);
        JButton refresh = UiKit.button("Проверить");
        JButton install = UiKit.primaryButton("Установить / обновить");
        JButton rootFolder = UiKit.button("Коренная папка");
        JButton modsFolder = UiKit.button("Папка Mods");
        JButton rpFolder = UiKit.button("Папка Ресурспаков");
        refresh.addActionListener(event -> refreshModsTable());
        install.addActionListener(event -> installModsInBackground());
        rootFolder.addActionListener(event -> openRootFolder());
        modsFolder.addActionListener(event -> openModsFolder());
        rpFolder.addActionListener(event -> openResourcePacksFolder());
        controls.add(refresh);
        controls.add(install);
        controls.add(rootFolder);
        controls.add(modsFolder);
        controls.add(rpFolder);

        JPanel split = new JPanel(new java.awt.GridLayout(2, 1, 0, 10));
        split.setOpaque(false);
        split.add(UiKit.card("Каталог Modrinth (Моды и Ресурспаки — 1 клик)", catalogPanel));
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

    private void loadModrinthPage(String query, boolean append) {
        if (this.modrinthLoading) {
            return;
        }
        this.modrinthLoading = true;
        final String projectType = this.modrinthProjectType;
        final String cleanQuery = query == null ? "" : query.trim();
        final int offset = append ? this.modrinthOffset : 0;
        this.modrinthInfoLabel.setText("Загрузка каталога Modrinth…");

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
                LauncherUi.this.modrinthLoading = false;
                ModrinthCatalog.SearchPage page = null;
                try {
                    page = get();
                } catch (Exception exception) {
                    this.failure = exception.getMessage();
                }
                if (page == null) {
                    LauncherUi.this.modrinthInfoLabel.setText("Не удалось загрузить каталог: " + this.failure);
                    return;
                }
                if (!append) {
                    LauncherUi.this.modrinthHits.clear();
                    LauncherUi.this.modrinthModel.setRowCount(0);
                }
                for (ModrinthCatalog.Hit hit : page.hits()) {
                    LauncherUi.this.modrinthHits.add(hit);
                    LauncherUi.this.modrinthModel.addRow(new Object[]{
                            hit.title(),
                            hit.author(),
                            String.valueOf(hit.downloads()),
                            hit.description()
                    });
                }
                LauncherUi.this.modrinthQuery = cleanQuery;
                LauncherUi.this.modrinthOffset = page.offset();
                LauncherUi.this.modrinthTotal = page.totalHits();
                String kindLabel = ModrinthCatalog.TYPE_RESOURCEPACK.equals(projectType) ? "ресурспаков" : "модов";
                LauncherUi.this.modrinthInfoLabel.setText("Показано " + LauncherUi.this.modrinthHits.size()
                        + " из " + page.totalHits() + " " + kindLabel
                        + " (прокрутите вниз или нажмите «Показать ещё»)");
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
                    status("Игра запущена");
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
