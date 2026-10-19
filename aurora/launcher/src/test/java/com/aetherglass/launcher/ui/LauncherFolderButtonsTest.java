package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.core.LauncherConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверяет наличие и работу кнопок «Коренная папка» и «Папка Mods» в лаунчере.
 */
class LauncherFolderButtonsTest {

    @Test
    void launcherUiContainsRootAndModsFolderButtonsAndCreatesDirectories(@TempDir Path tempDir) throws IOException {
        Path gameDir = tempDir.resolve("game-root");
        LauncherConfig config = new LauncherConfig(gameDir);
        LauncherUi ui = new LauncherUi(config, null);

        List<String> buttonTexts = new ArrayList<>();
        collectButtons(ui.root(), buttonTexts);

        long rootBtnCount = buttonTexts.stream().filter("Коренная папка"::equals).count();
        long modsBtnCount = buttonTexts.stream().filter("Папка Mods"::equals).count();

        assertTrue(rootBtnCount >= 1, "В лаунчере должна быть кнопка «Коренная папка»");
        assertTrue(modsBtnCount >= 1, "В лаунчере должна быть кнопка «Папка Mods»");

        Path rootPath = ui.rootDirectory();
        Path modsPath = ui.modsDirectory();

        assertEquals(gameDir.toAbsolutePath().normalize(), rootPath.toAbsolutePath().normalize());
        assertEquals(gameDir.resolve("mods").toAbsolutePath().normalize(), modsPath.toAbsolutePath().normalize());
        assertTrue(Files.isDirectory(rootPath), "Коренная папка должна создаваться при обращении");
        assertTrue(Files.isDirectory(modsPath), "Папка Mods должна создаваться при обращении");

        assertDoesNotThrow(ui::openRootFolder);
        assertDoesNotThrow(ui::openModsFolder);
    }

    private static void collectButtons(Component component, List<String> out) {
        if (component instanceof AbstractButton button && button.getText() != null) {
            out.add(button.getText());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collectButtons(child, out);
            }
        }
    }
}
