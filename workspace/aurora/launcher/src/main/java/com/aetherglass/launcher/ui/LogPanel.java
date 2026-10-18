package com.aetherglass.launcher.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Просмотр логов лаунчера и игры.
 *
 * <p>Вывод процесса читается в отдельном потоке и складывается в очередь, поэтому интерфейс
 * не подвисает даже при очень частом выводе. Хранится не более {@code MAX_LINES} строк.</p>
 */
public final class LogPanel extends JPanel {
    private static final int MAX_LINES = 5000;

    private final JTextArea area = new JTextArea();
    private final Deque<String> lines = new ArrayDeque<>();
    private final JCheckBox autoScroll = new JCheckBox("прокручивать автоматически", true);
    private Path lastSaveDirectory;

    public LogPanel() {
        super(new BorderLayout(6, 6));
        setBackground(UiTheme.BACKGROUND);
        this.area.setEditable(false);
        this.area.setFont(UiTheme.MONO);
        this.area.setBackground(new Color(0x0A, 0x0D, 0x14));
        this.area.setForeground(UiTheme.TEXT);
        this.area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(this.area);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        add(scroll, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.setBackground(UiTheme.BACKGROUND);
        this.autoScroll.setBackground(UiTheme.BACKGROUND);
        this.autoScroll.setForeground(UiTheme.TEXT_MUTED);
        JButton save = new JButton("Сохранить в файл");
        JButton clear = new JButton("Очистить");
        save.addActionListener(event -> saveToFile());
        clear.addActionListener(event -> {
            this.lines.clear();
            this.area.setText("");
        });
        controls.add(this.autoScroll);
        controls.add(save);
        controls.add(clear);
        add(controls, BorderLayout.SOUTH);
    }

    /** Добавляет строку в лог. Можно вызывать из любого потока. */
    public void append(String line) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendNow(line);
            return;
        }
        SwingUtilities.invokeLater(() -> appendNow(line));
    }

    private void appendNow(String line) {
        this.lines.addLast(line);
        while (this.lines.size() > MAX_LINES) {
            this.lines.removeFirst();
        }
        this.area.append(line);
        this.area.append("\n");
        if (this.autoScroll.isSelected()) {
            this.area.setCaretPosition(this.area.getDocument().getLength());
        }
    }

    private void saveToFile() {
        JFileChooser chooser = new JFileChooser();
        if (this.lastSaveDirectory != null) {
            chooser.setCurrentDirectory(this.lastSaveDirectory.toFile());
        }
        chooser.setSelectedFile(new java.io.File("aurora-launcher.log"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        this.lastSaveDirectory = target.getParent();
        try {
            Files.writeString(target, String.join("\n", this.lines) + "\n", StandardCharsets.UTF_8);
            JOptionPane.showMessageDialog(this, "Лог сохранён: " + target,
                    "Сохранение лога", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, "Не удалось сохранить лог: " + exception.getMessage(),
                    "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
    }
}
