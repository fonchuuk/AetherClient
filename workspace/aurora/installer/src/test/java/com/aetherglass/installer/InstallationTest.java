package com.aetherglass.installer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка установки на настоящих файлах — во временном каталоге.
 *
 * <p>Windows-специфичные шаги (реестр, ярлыки) пропускаются самой логикой,
 * поэтому тест проходит и на Linux: проверяется именно работа с файлами,
 * манифестом и журналом.</p>
 */
final class InstallationTest {

    @TempDir
    Path temp;

    private static final Map<String, String> ENVIRONMENT = Map.of(
            "LOCALAPPDATA", "C:\\Users\\player\\AppData\\Local",
            "APPDATA", "C:\\Users\\player\\AppData\\Roaming",
            "USERPROFILE", "C:\\Users\\player");

    /** Установщик, у которого «свой файл» — заранее подготовленный .exe во временном каталоге. */
    private Installation installation(String version, Path self) {
        InstallPaths paths = new InstallPaths(ENVIRONMENT::get, temp.resolve("home"));
        return new Installation(paths, version, () -> self);
    }

    private Path fakeInstallerExe() throws IOException {
        Path file = temp.resolve("downloads").resolve("AetherGlass-Setup.exe");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "это не настоящий exe, а метка для теста");
        return file;
    }

    @Test
    void installsFilesAndManifest() throws IOException {
        Path target = temp.resolve("AetherGlass");
        Installation installation = installation("1.0.0", fakeInstallerExe());

        assertTrue(installation.install(target, true, true, null), "установка должна пройти");

        assertTrue(Files.isRegularFile(target.resolve("AetherGlassLauncher.exe")), "нет файла лаунчера");
        assertTrue(Files.isRegularFile(target.resolve("AetherGlass-Uninstall.exe")), "нет файла удаления");
        assertTrue(Files.isRegularFile(target.resolve("install.json")), "нет манифеста");
        assertTrue(Files.isRegularFile(target.resolve("README.txt")), "нет описания");

        InstallManifest manifest = InstallManifest.read(target.resolve("install.json"));
        assertEquals("1.0.0", manifest.version());
        assertEquals("AetherGlassLauncher.exe", manifest.launcher());
        assertEquals("AetherGlass-Uninstall.exe", manifest.uninstaller());
        assertTrue(manifest.files().contains("README.txt"));
        assertFalse(manifest.installedAt().isEmpty(), "дата установки должна быть записана");

        String log = String.join("\n", installation.log());
        assertTrue(log.contains("Чистая установка"), log);
        assertTrue(log.contains("Проверка пройдена"), log);
    }

    @Test
    void secondRunIsAnUpdate() throws IOException {
        Path target = temp.resolve("AetherGlass");
        Path self = fakeInstallerExe();
        assertTrue(installation("1.0.0", self).install(target, false, false, null));
        Installation second = installation("1.1.0", self);

        assertTrue(second.install(target, false, false, null), "обновление должно пройти");

        String log = String.join("\n", second.log());
        assertTrue(log.contains("Обновление: найдена версия 1.0.0"), log);
        assertEquals("1.1.0", InstallManifest.read(target.resolve("install.json")).version());
    }

    @Test
    void uninstallRemovesFilesAndKeepsData() throws IOException {
        Path target = temp.resolve("AetherGlass");
        assertTrue(installation("1.0.0", fakeInstallerExe()).install(target, false, false, null));
        Files.writeString(target.resolve("AetherGlassLauncher.exe"), "заменён");

        Installation remover = installation("1.0.0", fakeInstallerExe());
        assertTrue(remover.uninstall(target, true), "удаление должно пройти");

        assertFalse(Files.exists(target.resolve("AetherGlassLauncher.exe")), "лаунчер остался");
        assertFalse(Files.exists(target.resolve("install.json")), "манифест остался");
        assertFalse(Files.isDirectory(target), "каталог установки остался");
        String log = String.join("\n", remover.log());
        assertTrue(log.contains("сохранены"), log);
    }

    @Test
    void uninstallWithoutManifestFails() {
        Installation remover = installation("1.0.0", temp.resolve("missing-installer.exe"));
        assertFalse(remover.uninstall(temp.resolve("empty-install"), true),
                "удалять нечего — установщик обязан сказать об этом");
        assertTrue(String.join("\n", remover.log()).contains("не найдена"));
    }

    @Test
    void repairRestoresFiles() throws IOException {
        Path target = temp.resolve("AetherGlass");
        Path self = fakeInstallerExe();
        assertTrue(installation("1.0.0", self).install(target, false, false, null));
        Files.delete(target.resolve("AetherGlassLauncher.exe"));

        Installation repair = installation("1.0.0", self);
        assertTrue(repair.repair(target), "восстановление должно пройти: " + String.join(" | ", repair.log()));
        assertTrue(Files.isRegularFile(target.resolve("AetherGlassLauncher.exe")), "файл не восстановлен");
        assertTrue(String.join("\n", repair.log()).contains("Восстановление версии 1.0.0"));
    }

    @Test
    void repairWithoutInstallFails() throws IOException {
        Installation repair = installation("1.0.0", fakeInstallerExe());
        assertFalse(repair.repair(temp.resolve("empty-install")));
        assertTrue(String.join("\n", repair.log()).contains("сначала установите"));
    }

    @Test
    void withoutSelfFileInstallationStops() throws IOException {
        Path target = temp.resolve("AetherGlass");
        Installation installation = installation("1.0.0", temp.resolve("missing-installer.jar"));
        assertFalse(installation.install(target, false, false, null),
                "без файла удаления устанавливать нельзя");
        assertTrue(String.join("\n", installation.log()).contains("удаления"));
    }

    @Test
    void readmeMentionsUninstall() throws IOException {
        Path target = temp.resolve("AetherGlass");
        assertTrue(installation("1.0.0", fakeInstallerExe()).install(target, false, false, null));
        String readme = Files.readString(target.resolve("README.txt"), StandardCharsets.UTF_8);
        assertTrue(readme.contains("--uninstall"), readme);
        assertTrue(readme.contains("AetherGlass Launcher 1.0.0"), readme);
    }

    @Test
    void logIsNotEmptyOnFailure() {
        Installation installation = installation("1.0.0", temp.resolve("missing-installer.jar"));
        installation.install(temp.resolve("AetherGlass"), false, false, null);
        List<String> log = installation.log();
        assertFalse(log.isEmpty(), "журнал обязателен: пользователь должен видеть причину");
    }
}
