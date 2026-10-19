package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.LauncherConfig;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Оконная оболочка лаунчера.
 *
 * <p>Здесь только окно и реакция на его закрытие; всё содержимое живёт в {@link LauncherUi},
 * поэтому интерфейс можно собрать и отрисовать без графического окружения.</p>
 */
public final class LauncherWindow extends JFrame {
    private final LauncherUi ui;

    public LauncherWindow(LauncherConfig config) {
        super("Aurora Launcher " + GameLauncher.LAUNCHER_VERSION);
        this.ui = new LauncherUi(config, this);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (LauncherWindow.this.ui.gameLauncher().isRunning()) {
                    int answer = JOptionPane.showConfirmDialog(LauncherWindow.this,
                            "Игра запущена. Закрыть лаунчер и остановить игру?",
                            "Игра запущена", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (answer != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
                config.launcherWidth(getWidth());
                config.launcherHeight(getHeight());
                LauncherWindow.this.ui.shutdown();
                dispose();
                System.exit(0);
            }
        });
        setContentPane(this.ui.root());
        setMinimumSize(new java.awt.Dimension(1100, 700));
        setSize(Math.max(1100, config.launcherWidth()), Math.max(700, config.launcherHeight()));
        setLocationRelativeTo(null);
    }

    /** Содержимое окна: используется при проверке интерфейса без графики. */
    public LauncherUi ui() {
        return this.ui;
    }
}
