package com.aetherglass.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Манифест установки: запись, чтение и устойчивость к испорченному файлу. */
final class InstallManifestTest {

    @TempDir
    Path temp;

    @Test
    void roundTripKeepsValues() throws IOException {
        InstallManifest manifest = InstallManifest.fresh("1.2.3", "AetherGlassLauncher.exe",
                "AetherGlass-Uninstall.exe", List.of("a.exe", "b.txt"));
        Path file = temp.resolve("install.json");
        manifest.save(file);

        InstallManifest read = InstallManifest.read(file);
        assertEquals("1.2.3", read.version());
        assertEquals("AetherGlassLauncher.exe", read.launcher());
        assertEquals("AetherGlass-Uninstall.exe", read.uninstaller());
        assertEquals(List.of("a.exe", "b.txt"), read.files());
        assertTrue(read.installedAt().matches("\\d{4}-\\d{2}-\\d{2}"), read.installedAt());
    }

    @Test
    void pathsWithSpacesAndCyrillicSurvive() throws IOException {
        InstallManifest manifest = InstallManifest.fresh("1.0.0", "C:\\Игры\\Aether Glass\\launcher.exe",
                "C:\\Игры\\Aether Glass\\uninstall.exe", List.of("C:\\Игры\\Aether Glass\\readme.txt"));
        Path file = temp.resolve("install.json");
        manifest.save(file);

        InstallManifest read = InstallManifest.read(file);
        assertEquals("C:\\Игры\\Aether Glass\\launcher.exe", read.launcher());
        assertEquals(List.of("C:\\Игры\\Aether Glass\\readme.txt"), read.files());
    }

    @Test
    void missingFileGivesEmptyManifest() {
        InstallManifest read = InstallManifest.read(temp.resolve("missing-manifest.json"));
        assertTrue(read.isEmpty(), "отсутствующий файл — это «не установлено», а не ошибка");
    }

    @Test
    void brokenFileGivesEmptyManifest() throws IOException {
        Path file = temp.resolve("install.json");
        Files.writeString(file, "{ это совсем не json \"version\": ", StandardCharsets.UTF_8);
        assertTrue(InstallManifest.read(file).isEmpty(), "испорченный файл не должен ломать установщик");
    }

    @Test
    void emptyFileGivesEmptyManifest() throws IOException {
        Path file = temp.resolve("install.json");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        assertTrue(InstallManifest.read(file).isEmpty());
    }

    @Test
    void writtenTextIsReadableByHand() {
        String text = InstallManifest.fresh("1.0.0", "l.exe", "u.exe", List.of("l.exe", "u.exe")).write();
        assertTrue(text.contains("\"version\": \"1.0.0\""), text);
        assertTrue(text.contains("\"files\": [\"l.exe\", \"u.exe\"]"), text);
    }
}
