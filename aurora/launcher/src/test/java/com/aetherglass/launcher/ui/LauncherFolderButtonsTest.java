package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.auth.AuthSession;
import com.aetherglass.launcher.auth.AuthStore;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.ModrinthCatalog;
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
 * Проверяет наличие и работу кнопок папок, офлайн-входа и каталога Modrinth в лаунчере.
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
        long rpBtnCount = buttonTexts.stream().filter("Папка Ресурспаков"::equals).count();
        long offlineBtnCount = buttonTexts.stream().filter("Войти офлайн"::equals).count();
        long oneClickBtnCount = buttonTexts.stream().filter("Скачать в 1 клик"::equals).count();

        assertTrue(rootBtnCount >= 1, "В лаунчере должна быть кнопка «Коренная папка»");
        assertTrue(modsBtnCount >= 1, "В лаунчере должна быть кнопка «Папка Mods»");
        assertTrue(rpBtnCount >= 1, "В лаунчере должна быть кнопка «Папка Ресурспаков»");
        assertTrue(offlineBtnCount >= 1, "В лаунчере должна быть кнопка «Войти офлайн»");
        assertTrue(oneClickBtnCount >= 1, "В лаунчере должна быть кнопка «Скачать в 1 клик»");

        Path rootPath = ui.rootDirectory();
        Path modsPath = ui.modsDirectory();
        Path rpPath = ui.resourcePacksDirectory();

        assertEquals(gameDir.toAbsolutePath().normalize(), rootPath.toAbsolutePath().normalize());
        assertEquals(gameDir.resolve("mods").toAbsolutePath().normalize(), modsPath.toAbsolutePath().normalize());
        assertEquals(gameDir.resolve("resourcepacks").toAbsolutePath().normalize(), rpPath.toAbsolutePath().normalize());
        assertTrue(Files.isDirectory(rootPath), "Коренная папка должна создаваться при обращении");
        assertTrue(Files.isDirectory(modsPath), "Папка Mods должна создаваться при обращении");
        assertTrue(Files.isDirectory(rpPath), "Папка Ресурспаков должна создаваться при обращении");

        assertDoesNotThrow(ui::openRootFolder);
        assertDoesNotThrow(ui::openModsFolder);
        assertDoesNotThrow(ui::openResourcePacksFolder);

        // Проверяем офлайн-вход и сохранение сессии
        ui.loginOffline("AetherPlayer_1");
        AuthSession loaded = new AuthStore(gameDir.resolve("aurora-auth.json")).load();
        assertNotNull(loaded);
        assertTrue(loaded.offline());
        assertTrue(loaded.valid());
        assertFalse(loaded.needsRefresh());
        assertEquals("AetherPlayer_1", loaded.playerName());

        // Проверяем санитизацию файлов Modrinth для модов и ресурспаков
        assertEquals("sodium-fabric.jar", ModrinthCatalog.sanitizeFilename("mod", "../../sodium-fabric.jar"));
        assertEquals("", ModrinthCatalog.sanitizeFilename("mod", "pack.zip"));
        assertEquals("faithful-64x.zip", ModrinthCatalog.sanitizeFilename("resourcepack", "../faithful-64x.zip"));
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

    @Test
    void modpacksSupportMultipleLoadersAndGateAetherGlassClientTo12111Fabric(@TempDir Path tempDir) {
        Path gameDir = tempDir.resolve("modpack-test");
        LauncherConfig config = new LauncherConfig(gameDir);
        LauncherUi ui = new LauncherUi(config, null);

        // На 1.21.11 (Fabric) галочка «Установить AetherGlass Client» активна и включена по умолчанию
        ui.selectVersionAndLoader("1.21.11", "Fabric");
        assertTrue(ui.isAetherGlassCheckboxEnabled(), "На 1.21.11 (Fabric) галочка должна быть активна");
        assertTrue(ui.isAetherGlassCheckboxSelected(), "На 1.21.11 (Fabric) галочка должна быть включена по умолчанию");

        // На 1.20.1 (Fabric) галочка становится неактивной и выключается, но сборку создать можно
        ui.selectVersionAndLoader("1.20.1", "Fabric");
        assertFalse(ui.isAetherGlassCheckboxEnabled(), "На 1.20.1 галочка AetherGlass должна быть неактивной");
        assertFalse(ui.isAetherGlassCheckboxSelected(), "На 1.20.1 галочка AetherGlass должна быть снята");

        var customPack = ui.createModpack("My Custom 1.20.1", "1.20.1", "Fabric", true, true);
        assertEquals("1.20.1", customPack.minecraftVersion());
        assertEquals("Fabric", customPack.loader());
        assertFalse(customPack.installAetherGlassClient(), "Для 1.20.1 AetherGlass Client не должен ставиться");

        // На 1.16.5 (Forge) и 1.19.4 (Vanilla) также создаётся сборка без AetherGlass Client
        var forgePack = ui.createModpack("Forge 1.16.5", "1.16.5", "Forge", true, false);
        assertEquals("Forge", forgePack.loader());
        assertFalse(forgePack.installAetherGlassClient());
    }
}
