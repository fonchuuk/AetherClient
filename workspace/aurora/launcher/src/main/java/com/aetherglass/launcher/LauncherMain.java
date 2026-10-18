package com.aetherglass.launcher;

import com.aetherglass.launcher.core.JavaRuntime;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.LauncherPaths;
import com.aetherglass.launcher.ui.LauncherWindow;
import com.aetherglass.launcher.ui.UiTheme;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.nio.file.Path;

/**
 * Точка входа лаунчера.
 *
 * <p>Лаунчер не требует Minecraft для запуска: он проверяет окружение, загружает настройки
 * и открывает окно. Всё остальное (скачивание игры, вход, запуск) делается по команде
 * пользователя в фоновых задачах.</p>
 */
public final class LauncherMain {
    private LauncherMain() {
    }

    public static void main(String[] args) {
        Path gameDirectory = readGameDirectoryArgument(args);
        if (gameDirectory == null) {
            gameDirectory = LauncherConfig.defaults().gameDirectory();
        }
        LauncherConfig config = LauncherConfig.load(new LauncherPaths(gameDirectory).launcherConfig());
        if (config == null) {
            config = LauncherConfig.defaults();
        }

        int runtimeVersion = Runtime.version().feature();
        if (runtimeVersion < 17) {
            System.err.println("Для работы лаунчера нужна Java 17 или новее, текущая версия: " + runtimeVersion);
            System.exit(2);
            return;
        }

        LauncherConfig finalConfig = config;
        SwingUtilities.invokeLater(() -> {
            UiTheme.install();
            LauncherWindow window = new LauncherWindow(finalConfig);
            window.setVisible(true);
            if (finalConfig.javaExecutable().isBlank()) {
                Path detected = JavaRuntime.detect();
                if (detected == null) {
                    JOptionPane.showMessageDialog(window,
                            "Не найдена Java " + JavaRuntime.REQUIRED_MAJOR
                                    + " или новее. Укажите путь к ней на вкладке «Настройки».",
                            "Java не найдена", JOptionPane.WARNING_MESSAGE);
                }
            }
        });
    }

    private static Path readGameDirectoryArgument(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String argument = args[i];
            if (argument.equals("--help") || argument.equals("-h")) {
                System.out.println("Aurora Launcher");
                System.out.println("  --game-dir <путь>   каталог игры (по умолчанию ~/.aetherglass)");
                System.exit(0);
            }
            if ((argument.equals("--game-dir") || argument.equals("--gameDir")) && i + 1 < args.length) {
                return Path.of(args[i + 1]).toAbsolutePath().normalize();
            }
        }
        return null;
    }
}
