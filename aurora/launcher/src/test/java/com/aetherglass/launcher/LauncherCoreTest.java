package com.aetherglass.launcher;

import com.aetherglass.launcher.core.GameInstaller;
import com.aetherglass.launcher.core.GameLauncher;
import com.aetherglass.launcher.core.JavaRuntime;
import com.aetherglass.launcher.core.LauncherConfig;
import com.aetherglass.launcher.core.ModInstaller;
import com.aetherglass.launcher.core.Rules;
import com.aetherglass.launcher.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Проверки чистых функций лаунчера: они не требуют сети и каталога игры. */
class LauncherCoreTest {

    @Test
    void jsonParsesObjectsArraysAndEscapes() {
        Object parsed = Json.parse("{\"a\":[1,2.5,true,null],\"b\":\"строк\\\"а\\n\"}");
        Map<String, Object> object = Json.asObject(parsed);
        List<Object> array = Json.asArray(object.get("a"));
        assertEquals(4, array.size());
        assertEquals(1L, array.get(0));
        assertEquals(2.5D, array.get(1));
        assertEquals(Boolean.TRUE, array.get(2));
        assertEquals(null, array.get(3));
        assertEquals("строк\"а\n", Json.string(object, "b", ""));
        assertEquals(7, Json.intValue(object, "missing", 7));
    }

    @Test
    void jsonIgnoresByteOrderMark() {
        // Файлы Mojang иногда начинаются с BOM — парсер обязан его отбрасывать.
        Map<String, Object> object = Json.asObject(Json.parse("\uFEFF{\"objects\":{\"a\":1}}"));
        assertEquals(1, Json.asObject(object.get("objects")).size());
    }

    @Test
    void jsonRoundTripKeepsStructure() {
        Map<String, Object> original = Map.of("key", "value", "number", 42L, "list", List.of(1L, 2L));
        String text = Json.write(original);
        Map<String, Object> parsed = Json.asObject(Json.parse(text));
        assertEquals("value", Json.string(parsed, "key", ""));
        assertEquals(42, Json.intValue(parsed, "number", 0));
        assertEquals(2, Json.asArray(parsed.get("list")).size());
    }

    @Test
    void brokenJsonReportsClearError() {
        Json.JsonException exception = assertThrows(Json.JsonException.class, () -> Json.parse("{\"a\": }"));
        assertNotNull(exception.getMessage());
        assertFalse(exception.getMessage().isBlank());
    }

    @Test
    void rulesDetectCurrentPlatform() {
        String os = Rules.osName();
        assertTrue(List.of("windows", "linux", "osx").contains(os), "неизвестная ОС: " + os);
        assertFalse(Rules.osArch().isBlank());
        Map<String, Object> allowWindows = Map.of("action", "allow", "os", Map.of("name", "windows"));
        boolean expected = "windows".equals(os);
        assertEquals(expected, Rules.allows(allowWindows, Map.of()));
        Map<String, Object> disallowOther = Map.of("action", "disallow", "os", Map.of("name", "windows"));
        assertEquals(!expected, Rules.allows(disallowOther, Map.of()));
    }

    @Test
    void platformRulesExcludeForeignLibraries() {
        // Платформенная библиотека чужой системы не должна попадать в classpath.
        Map<String, Object> macOnly = Map.of("rules", List.of(
                Map.of("action", "allow", "os", Map.of("name", "osx"))));
        assertEquals("osx".equals(Rules.osName()), Rules.allows(macOnly, Map.of()));
    }

    @Test
    void featureGatesAreRespected() {
        Map<String, Object> entry = Map.of("rules", List.of(
                Map.of("action", "allow", "features", Map.of("has_custom_resolution", Boolean.TRUE))),
                "value", "--width");
        assertFalse(Rules.allows(entry, Map.of("has_custom_resolution", Boolean.FALSE)));
        assertTrue(Rules.allows(entry, Map.of("has_custom_resolution", Boolean.TRUE)));
    }

    @Test
    void argumentsSubstituteEveryToken() {
        String result = GameLauncher.substitute("--gameDir ${game_directory} --assets ${assets_root} ${unknown}",
                Map.of("game_directory", "/games/aurora", "assets_root", "/games/aurora/assets"));
        assertEquals("--gameDir /games/aurora --assets /games/aurora/assets ${unknown}", result);
    }

    @Test
    void javaVersionParsing() {
        JavaRuntime.JavaInfo modern = JavaRuntime.parse(Path.of("java"),
                "openjdk version \"21.0.12\" 2025-01-21\nOpenJDK Runtime Environment");
        assertEquals(21, modern.majorVersion());
        assertTrue(modern.meetsRequirement());
        JavaRuntime.JavaInfo old = JavaRuntime.parse(Path.of("java"), "openjdk version \"17.0.9\" 2023-10-17");
        assertFalse(old.meetsRequirement());
        JavaRuntime.JavaInfo broken = JavaRuntime.parse(Path.of("java"), "какой-то мусор");
        assertFalse(broken.valid());
        assertNotNull(broken.error());
    }

    @Test
    void mavenPathIsBuiltFromCoordinates() {
        assertEquals("org/ow2/asm/asm/9.10.1/asm-9.10.1.jar",
                GameInstaller.mavenPath("org.ow2.asm:asm:9.10.1"));
    }

    @Test
    void configValidatesMemoryBounds() {
        LauncherConfig config = LauncherConfig.defaults();
        config.maxMemoryMb(128);
        assertTrue(config.maxMemoryMb() >= LauncherConfig.MIN_MEMORY_MB);
        config.maxMemoryMb(4096);
        config.minMemoryMb(8192);
        assertTrue(config.minMemoryMb() <= config.maxMemoryMb());
    }

    @Test
    void modManifestFromResourcesContainsTenMods() throws Exception {
        ModInstaller.Manifest manifest = ModInstaller
                .fromResource(new com.aetherglass.launcher.core.LauncherPaths(
                        Path.of(System.getProperty("java.io.tmpdir"), "aurora-test")), null, null)
                .manifest();
        assertEquals("1.21.11", manifest.minecraftVersion());
        assertEquals(10, manifest.mods().size(), "в манифесте должно быть ровно 10 оптимизационных модов");
        assertNotNull(manifest.fabricApi(), "Fabric API обязателен как зависимость");
        for (ModInstaller.Manifest.Mod mod : manifest.mods()) {
            assertEquals(128, mod.sha512().length(), "у мода " + mod.name() + " отсутствует SHA-512");
            assertTrue(mod.url().startsWith("https://cdn.modrinth.com/"),
                    "мод " + mod.name() + " должен скачиваться с Modrinth");
            assertTrue(mod.size() > 0, "у мода " + mod.name() + " не указан размер");
        }
    }

    @Test
    void modInstallerInstallsFabricApiTogetherWithTenMods() throws Exception {
        ModInstaller installer = ModInstaller.fromResource(
                new com.aetherglass.launcher.core.LauncherPaths(
                        Path.of(System.getProperty("java.io.tmpdir"), "aurora-test")), null, null);
        List<ModInstaller.Manifest.Mod> required = installer.required();
        // Десять оптимизаций плюс Fabric API: без библиотеки часть модов не запускается,
        // это выяснилось на реальном запуске игры, а не в теории.
        assertEquals(11, required.size(), "должны устанавливаться 10 модов и Fabric API");
        assertTrue(required.stream().anyMatch(mod -> "fabric-api".equals(mod.id())),
                "Fabric API обязан попадать в каталог модов");
        assertEquals(10, required.stream().filter(mod -> !"fabric-api".equals(mod.id())).count());
        installer.checkDependencies();
    }

    @Test
    void modInstallerRejectsManifestWithUnsatisfiedDependency() throws Exception {
        String json = "{\"minecraft_version\":\"1.21.11\",\"loader\":\"fabric\","
                + "\"mods\":[{\"id\":\"alpha\",\"name\":\"Alpha\",\"version\":\"1\",\"purpose\":\"\",\"environment\":\"fabric\","
                + "\"file\":{\"name\":\"alpha.jar\",\"url\":\"https://cdn.modrinth.com/x/alpha.jar\",\"size\":1,\"sha1\":\"a\",\"sha512\":\"b\"},"
                + "\"dependencies\":[\"missing-lib\"],\"project_url\":\"\"}],\"fabric_api\":null}";
        ModInstaller.Manifest manifest = ModInstaller.Manifest.parse(json);
        ModInstaller installer = new ModInstaller(
                new com.aetherglass.launcher.core.LauncherPaths(
                        Path.of(System.getProperty("java.io.tmpdir"), "aurora-test")), manifest, null, null);
        IOException failure = assertThrows(IOException.class, installer::checkDependencies);
        assertTrue(failure.getMessage().contains("missing-lib"),
                "ошибка должна называть недостающий проект, а не молчать");
    }
}
