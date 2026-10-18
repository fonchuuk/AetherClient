package com.aetherglass.installer;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Команды реестра и текст сценария ярлыка собираются отдельными методами,
 * поэтому проверить их можно и без Windows.
 */
final class WindowsIntegrationTest {

    @Test
    void regAddBuildsFullCommand() {
        List<String> command = WindowsIntegration.regAdd("HKCU\\Software\\Test", "DisplayName",
                "AetherGlass Launcher", WindowsIntegration.ValueType.STRING);
        assertEquals(List.of("reg", "add", "HKCU\\Software\\Test", "/v", "DisplayName",
                "/t", "REG_SZ", "/d", "AetherGlass Launcher", "/f"), command);
    }

    @Test
    void regDeleteForcesRemoval() {
        assertEquals(List.of("reg", "delete", "HKCU\\Software\\Test", "/f"),
                WindowsIntegration.regDelete("HKCU\\Software\\Test"));
    }

    @Test
    void regQueryAsksForOneValue() {
        assertEquals(List.of("reg", "query", "HKCU\\Software\\Test", "/v", "InstallLocation"),
                WindowsIntegration.regQuery("HKCU\\Software\\Test", "InstallLocation"));
    }

    @Test
    void shortcutScriptPointsToLauncher() {
        Path directory = Path.of("C:", "Users", "player", "AppData", "Local", "AetherGlass");
        Path target = directory.resolve("AetherGlassLauncher.exe");
        Path link = Path.of("C:", "Users", "player", "Desktop").resolve("AetherGlass Launcher.lnk");
        String script = WindowsIntegration.shortcutScript(target, directory, target, link, "Тест");

        assertTrue(script.contains("CreateShortcut(\"" + link + "\")"), script);
        assertTrue(script.contains("shortcut.TargetPath = \"" + target + "\""), script);
        assertTrue(script.contains("shortcut.WorkingDirectory = \"" + directory + "\""), script);
        assertTrue(script.contains("shortcut.IconLocation = \"" + target + ",0\""), script);
        assertTrue(script.contains("shortcut.Save"), script);
    }

    @Test
    void shortcutScriptSurvivesMissingWorkingDirectory() {
        Path target = Path.of("C:", "AetherGlass", "AetherGlassLauncher.exe");
        String script = WindowsIntegration.shortcutScript(target, null, target,
                Path.of("C:", "link.lnk"), "Тест");
        assertTrue(script.contains("shortcut.WorkingDirectory = "), script);
    }

    void vbsEscapesQuotes() {
        assertEquals("\"say \"\"hi\"\"\"", WindowsIntegration.vbs("say \"hi\""));
    }

    @Test
    void registrationContainsUninstallAndModifyCommands() {
        Path installDir = Path.of("C:\\Users\\player\\AppData\\Local\\AetherGlass");
        Path launcher = installDir.resolve("AetherGlassLauncher.exe");
        Path uninstaller = installDir.resolve("AetherGlass-Uninstall.exe");
        Path java = Path.of("C:\\Program Files\\Java\\jdk-21\\bin\\javaw.exe");

        List<List<String>> commands = WindowsIntegration.uninstallRegistration(installDir, launcher,
                uninstaller, java, "1.0.0", 512_000);
        String joined = commands.toString();

        assertTrue(joined.contains("UninstallString"), joined);
        assertTrue(joined.contains("--uninstall"), joined);
        assertTrue(joined.contains("--repair"), joined);
        assertTrue(joined.contains("DisplayIcon"), joined);
        assertTrue(joined.contains("EstimatedSize"), joined);
        assertTrue(joined.contains("500"), joined); // 512000 байт → 500 КБ
        for (List<String> command : commands) {
            assertEquals("reg", command.get(0));
            assertTrue(command.contains("/f"), command.toString());
        }
    }

    @Test
    void deleteScriptChecksExistence() {
        Path file = Path.of("C:", "Users", "player", "Desktop").resolve("AetherGlass Launcher.lnk");
        String script = WindowsIntegration.deleteScript(file);
        assertTrue(script.contains("FileExists"), script);
        assertTrue(script.contains("DeleteFile"), script);
    }
}
