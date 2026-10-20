package com.aetherglass.installer;

import com.aetherglass.launcher.ui.UiKit;
import com.aetherglass.launcher.ui.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Окно установщика: тот же «стеклянный» язык, что у клиента и лаунчера.
 *
 * <p>Работа (копирование, ярлыки, запись в реестр) идёт в фоновом потоке, поэтому
 * окно не замирает; каждый шаг пишется в журнал, который видит пользователь.
 * Никаких «имитаций прогресса»: полоса движется по реальным шагам.</p>
 *
 * <p>Оформление не дублируется: используются цвета и компоненты {@link UiKit}
 * из модуля лаунчера, поэтому окно установки выглядит продолжением продукта.</p>
 */
public final class InstallerUi {

    private static final String WELCOME = "welcome";
    private static final String FOLDER = "folder";
    private static final String WORK = "work";
    private static final String DONE = "done";

    private final InstallPaths paths;
    private final String version;
    private final Installation installation;

    private static final String WINDOW_TITLE = "Установка AetherGlass Launcher";

    private final JPanel root = UiKit.background();
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);

    private final JTextField folderField = UiKit.field(new JTextField());
    private final JCheckBox desktopBox = UiKit.check(new JCheckBox("Ярлык на рабочем столе", true));
    private final JCheckBox startMenuBox = UiKit.check(new JCheckBox("Ярлык в меню «Пуск»", true));
    private final JCheckBox runBox = UiKit.check(new JCheckBox("Запустить лаунчер", true));
    private final JTextArea logArea = new JTextArea(10, 46);
    private final JTextArea resultArea = new JTextArea(10, 46);
    private final JProgressBar progress = UiKit.progress();
    private final JButton backButton = UiKit.button("Назад");
    private final JButton nextButton = UiKit.primaryButton("Установить");
    private final JButton cancelButton = UiKit.button("Отмена");

    private JFrame frame;
    private boolean finished;
    private Path installedLauncher;

    public InstallerUi(InstallPaths paths, String version) {
        this.paths = paths;
        this.version = version;
        this.installation = new Installation(paths, version);
    }

    /** Показывает окно; вызывается в потоке событий Swing. */
    public void show() {
        build();
        if (this.frame == null) {
            this.frame = new JFrame(WINDOW_TITLE);
            this.frame.setContentPane(this.root);
            this.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        }
        this.frame.pack();
        this.frame.setLocationRelativeTo(null);
        this.frame.setVisible(true);
    }

    /** Закрывает окно и завершает процесс установщика. */
    private void closeWindow() {
        if (this.frame != null) {
            this.frame.setVisible(false);
            this.frame.dispose();
            if (!GraphicsEnvironment.isHeadless()) {
                System.exit(0);
            }
        }
    }

    /**
     * Содержимое окна без показа: по нему можно отрисовать установщик
     * и проверить разметку без графического окружения.
     */
    public JComponent content() {
        build();
        return this.root;
    }

    /** Имена страниц в порядке следования: нужны проверке без графики. */
    public List<String> pageNames() {
        return List.of(WELCOME, FOLDER, WORK, DONE);
    }

    /** Переключает страницу: используется проверкой без графики. */
    public void showPage(String name) {
        cards.show(pages, name);
    }

    private void build() {
        JPanel root = this.root;
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));

        JPanel head = new JPanel();
        head.setOpaque(false);
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("AetherGlass Launcher " + version);
        title.setForeground(UiTheme.TEXT);
        title.setFont(UiTheme.TITLE);
        JLabel subtitle = UiKit.muted("установка, обновление и удаление");
        head.add(title);
        head.add(subtitle);
        head.add(Box.createVerticalStrut(12));

        pages.setOpaque(false);
        pages.add(welcomePage(), WELCOME);
        pages.add(folderPage(), FOLDER);
        pages.add(workPage(), WORK);
        pages.add(donePage(), DONE);

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 0));
        buttons.setOpaque(false);
        backButton.setVisible(false);
        backButton.addActionListener(event -> cards.show(pages, FOLDER));
        nextButton.addActionListener(event -> onNext());
        cancelButton.addActionListener(event -> closeWindow());
        buttons.add(backButton);
        buttons.add(cancelButton);
        buttons.add(nextButton);

        root.add(head, BorderLayout.NORTH);
        root.add(pages, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
    }

    private JPanel welcomePage() {
        InstallManifest existing = InstallManifest.read(paths.defaultInstallDir().resolve("install.json"));
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(paragraph(existing.isEmpty()
                ? "Установка лаунчера и клиента AetherGlass 1.21.11 в один клик."
                : "Найдена установленная версия " + existing.version() + ". Она будет обновлена, "
                + "настройки и аккаунты сохранятся."));

        folderField.setText(paths.defaultInstallDir().toString());
        folderField.setMaximumSize(new Dimension(520, 34));

        JButton choose = UiKit.button("Выбрать…");
        choose.addActionListener(event -> {
            JFileChooser dialog = new JFileChooser(Path.of(folderField.getText()).toFile());
            dialog.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (dialog.showOpenDialog(this.frame) == JFileChooser.APPROVE_OPTION) {
                folderField.setText(dialog.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(0f);
        row.setMaximumSize(new Dimension(620, 34));
        row.add(folderField, BorderLayout.CENTER);
        row.add(choose, BorderLayout.EAST);

        body.add(Box.createVerticalStrut(8));
        body.add(paragraph("Папка установки:"));
        body.add(row);
        body.add(Box.createVerticalStrut(10));
        body.add(checkbox(desktopBox));
        body.add(checkbox(startMenuBox));
        body.add(checkbox(runBox));
        body.add(Box.createVerticalStrut(6));
        body.add(paragraph("Нажмите «Установить» — все файлы и ярлыки будут созданы автоматически."));
        return UiKit.card("Быстрая установка AetherGlass", body, true);
    }

    private JPanel folderPage() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(paragraph("Выбор каталога установки и ярлыков доступен на главном экране."));
        return UiKit.card("Каталог и ярлыки", body);
    }

    private JPanel workPage() {
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        logArea.setEditable(false);
        console(logArea);
        progress.setIndeterminate(true);
        body.add(progress, BorderLayout.NORTH);
        body.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return UiKit.card("Установка", body);
    }

    private JPanel donePage() {
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.add(paragraph("Готово."));
        body.add(checkbox(runBox));
        resultArea.setEditable(false);
        console(resultArea);
        body.add(Box.createVerticalStrut(6));
        body.add(paragraph("Журнал:"));
        body.add(new JScrollPane(resultArea));
        return UiKit.card("Результат", body);
    }

    private void onNext() {
        if (!finished) {
            cards.show(pages, WORK);
            nextButton.setEnabled(false);
            backButton.setVisible(false);
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    Path target = Path.of(folderField.getText().trim());
                    boolean ok = installation.install(target, desktopBox.isSelected(),
                            startMenuBox.isSelected(), JavaFinder.find());
                    if (ok) {
                        installedLauncher = target.resolve("AetherGlassLauncher.exe");
                    }
                    return ok;
                }

                @Override
                protected void done() {
                    try {
                        finish(get());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        finish(false);
                    } catch (Exception e) {
                        logArea.append("Ошибка: " + e.getMessage() + "\n");
                        finish(false);
                    }
                }
            }.execute();
            return;
        }
        if (runBox.isSelected() && installedLauncher != null && Files.isRegularFile(installedLauncher)) {
            try {
                new ProcessBuilder(installedLauncher.toString()).start();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this.frame, "Не удалось запустить лаунчер: " + e.getMessage());
            }
        }
        closeWindow();
    }

    private void finish(boolean success) {
        finished = true;
        progress.setIndeterminate(false);
        progress.setValue(100);
        for (String line : installation.log()) {
            resultArea.append("• " + line + "\n");
        }
        resultArea.append(success ? "\nУстановка завершена.\n" : "\nУстановка не удалась.\n");
        resultArea.setCaretPosition(0);
        nextButton.setEnabled(true);
        nextButton.setText(success ? "Запустить и закрыть" : "Закрыть");
        cancelButton.setVisible(false);
        runBox.setVisible(success && WindowsIntegration.isWindows());
        cards.show(pages, DONE);
        if (success && runBox.isSelected() && WindowsIntegration.isWindows()
                && installedLauncher != null && Files.isRegularFile(installedLauncher)) {
            try {
                new ProcessBuilder(installedLauncher.toString())
                        .directory(installedLauncher.getParent().toFile())
                        .start();
                runBox.setSelected(false);
            } catch (IOException ignored) {
            }
        }
        if (success) {
            closeWindow();
        }
    }

    // ─────────────────────────── оформление ───────────────────────────

    private static JLabel paragraph(String text) {
        JLabel label = new JLabel("<html><div style='width:480px'>" + text + "</div></html>");
        label.setFont(UiTheme.BASE);
        label.setForeground(UiTheme.TEXT_MUTED);
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        label.setAlignmentX(0f);
        return label;
    }

    private static JCheckBox checkbox(JCheckBox box) {
        box.setAlignmentX(0f);
        return box;
    }

    /** Журнал: моноширинный текст на тёмном фоне, как в лаунчере. */
    private static void console(JTextArea area) {
        area.setBackground(UiTheme.CONSOLE);
        area.setForeground(UiTheme.TEXT);
        area.setCaretColor(UiTheme.ACCENT);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    }

    /** Есть ли графический режим. */
    public static boolean available() {
        return !GraphicsEnvironment.isHeadless();
    }

    /** Показывает окно в потоке событий Swing. */
    public static void open(InstallPaths paths, String version) {
        SwingUtilities.invokeLater(() -> new InstallerUi(paths, version).show());
    }
}
