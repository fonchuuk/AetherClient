package com.aetherglass.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Пути установки считаются из переменных окружения и ничего не выдумывают. */
final class InstallPathsTest {

    @TempDir
    Path temp;

    private static InstallPaths paths(Map<String, String> environment, Path userHome) {
        return new InstallPaths(environment::get, userHome);
    }

    @Test
    void installDirUsesLocalAppData() {
        InstallPaths paths = paths(Map.of("LOCALAPPDATA", "C:/Users/player/AppData/Local"), temp);
        assertEquals(Path.of("C:/Users/player/AppData/Local").resolve("AetherGlass"),
                paths.defaultInstallDir());
    }

    @Test
    void installDirFallsBackToUserHome() {
        InstallPaths paths = paths(Map.of(), temp);
        assertEquals(temp.resolve("AppData").resolve("Local").resolve("AetherGlass"),
                paths.defaultInstallDir());
    }

    @Test
    void userDataDirUsesAppData() {
        InstallPaths paths = paths(Map.of("APPDATA", "C:/Users/player/AppData/Roaming"), temp);
        assertEquals(Path.of("C:/Users/player/AppData/Roaming").resolve("AetherGlass"),
                paths.userDataDir());
    }

    @Test
    void desktopFoundOnlyWhenItExists() throws IOException {
        Path profile = temp.resolve("profile");
        Files.createDirectories(profile.resolve("Desktop"));
        InstallPaths found = paths(Map.of("USERPROFILE", profile.toString()), temp);
        assertEquals(profile.resolve("Desktop"), found.desktopDir());

        Path bare = temp.resolve("bare");
        Files.createDirectories(bare);
        InstallPaths missing = paths(Map.of("USERPROFILE", bare.toString()), temp);
        assertNull(missing.desktopDir(), "несуществующий каталог нельзя использовать");
    }

    @Test
    void startMenuFoundOnlyWhenItExists() throws IOException {
        Path appData = temp.resolve("roaming");
        Path programs = appData.resolve("Microsoft").resolve("Windows").resolve("Start Menu").resolve("Programs");
        Files.createDirectories(programs);
        InstallPaths paths = paths(Map.of("APPDATA", appData.toString()), temp);
        assertEquals(programs, paths.startMenuDir());
    }

    @Test
    void missingVariablesGiveNothingInsteadOfGuesses() {
        Map<String, String> empty = new HashMap<>();
        InstallPaths paths = paths(empty, temp);
        assertNull(paths.desktopDir());
        assertNull(paths.startMenuDir());
        assertNotNull(paths.defaultInstallDir());
    }

    @Test
    void uninstallKeyIsPerUser() {
        assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\AetherGlass",
                InstallPaths.uninstallKey());
    }

    @Test
    void quoteWrapsPathInQuotes() {
        assertEquals("\"" + temp.toAbsolutePath() + "\"", InstallPaths.quote(temp));
    }
}
