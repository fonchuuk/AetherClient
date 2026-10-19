package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Json;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка поставщика Java: разбор официальных ответов Mojang, выбор
 * платформы и reuse уже скачанного рантайма. Сеть не нужна ни одному тесту.
 */
class JavaProvisionerTest {

    /** Ответ Mojang со списком рантаймов: настоящий формат, одна платформа. */
    private static final String INDEX = """
            {
              "windows-x64": {
                "java-runtime-gamma": [
                  {
                    "manifest": {
                      "sha1": "1111111111111111111111111111111111111111",
                      "size": 2048,
                      "url": "https://example.invalid/gamma-windows.json"
                    },
                    "version": { "name": "21.0.5.0", "released": "2026-01-20T09:00:00+00:00" }
                  }
                ]
              }
            }
            """;

    /** Манифест рантайма: два файла и одна запись про каталог. */
    private static final String MANIFEST = """
            {
              "files": {
                "bin/java": {
                  "type": "file",
                  "executable": true,
                  "downloads": {
                    "raw": {
                      "sha1": "2222222222222222222222222222222222222222",
                      "size": 512,
                      "url": "https://example.invalid/bin-java"
                    }
                  }
                },
                "lib/modules": {
                  "type": "file",
                  "executable": false,
                  "downloads": {
                    "raw": {
                      "sha1": "3333333333333333333333333333333333333333",
                      "size": 4096,
                      "url": "https://example.invalid/modules"
                    }
                  }
                },
                "bin": { "type": "directory" }
              }
            }
            """;

    @TempDir
    Path temporary;

    @Test
    void platformKeysFollowMojangNaming() throws IOException {
        assertEquals("windows-x64", JavaProvisioner.platformKey("windows", "x86_64"));
        assertEquals("windows-x86", JavaProvisioner.platformKey("windows", "x86"));
        assertEquals("windows-arm64", JavaProvisioner.platformKey("windows", "arm64"));
        assertEquals("linux", JavaProvisioner.platformKey("linux", "x86_64"));
        assertEquals("linux-i386", JavaProvisioner.platformKey("linux", "x86"));
        assertEquals("mac-os", JavaProvisioner.platformKey("osx", "x86_64"));
        assertEquals("mac-os-arm64", JavaProvisioner.platformKey("osx", "arm64"));
        // Неизвестная платформа — честная ошибка, а не молчаливый выбор «чего-нибудь».
        assertThrows(IOException.class, () -> JavaProvisioner.platformKey("solaris", "sparc"));
    }

    @Test
    void manifestUrlIsTakenFromTheLatestEntry() throws IOException {
        Map<String, Object> index = Json.asObject(Json.parse(INDEX));
        assertEquals("https://example.invalid/gamma-windows.json",
                JavaProvisioner.manifestUrl("java-runtime-gamma", "windows-x64", index));
        assertThrows(IOException.class,
                () -> JavaProvisioner.manifestUrl("java-runtime-alpha", "windows-x64", index),
                "компонента в ответе нет — должна быть ошибка, а не пустая ссылка");
        assertThrows(IOException.class,
                () -> JavaProvisioner.manifestUrl("java-runtime-gamma", "linux", index),
                "платформы в ответе нет — должна быть ошибка");
    }

    @Test
    void manifestFilesAreParsedAndDirectoriesSkipped() {
        List<JavaProvisioner.RuntimeFile> files =
                JavaProvisioner.filesOf(Json.asObject(Json.parse(MANIFEST)));

        assertEquals(2, files.size(), "запись про каталог не должна попасть в список файлов");
        JavaProvisioner.RuntimeFile java = files.stream()
                .filter(file -> file.path().equals("bin/java")).findFirst().orElseThrow();
        assertEquals("https://example.invalid/bin-java", java.url());
        assertEquals("2222222222222222222222222222222222222222", java.sha1());
        assertEquals(512L, java.size());
        assertTrue(java.executable(), "исполняемый файл помечен как исполняемый");

        JavaProvisioner.RuntimeFile modules = files.stream()
                .filter(file -> file.path().equals("lib/modules")).findFirst().orElseThrow();
        assertFalse(modules.executable(), "библиотека не исполняемая");
    }

    @Test
    void runtimeIsStoredInsideTheGameDirectory() {
        LauncherPaths paths = new LauncherPaths(this.temporary);
        Path root = JavaProvisioner.runtimeDir(paths, "java-runtime-gamma", "linux");
        assertEquals(this.temporary.resolve("runtime").resolve("java-runtime-gamma").resolve("linux"), root);
        assertEquals(root.resolve("bin").resolve("java"), JavaProvisioner.executableIn(root));
    }

    @Test
    void systemJavaIsUsedWhenItIsNewEnough() throws Exception {
        Path fake = fakeJava("openjdk version \"21.0.5\" 2024-10-15");
        LauncherPaths paths = new LauncherPaths(this.temporary);

        JavaProvisioner.Provision provision = JavaProvisioner.ensure(paths, "java-runtime-gamma", 21,
                null, null, () -> fake, () -> "linux", () -> "x86_64");

        assertEquals(JavaProvisioner.Source.SYSTEM, provision.source(),
                "своя подходящая Java берётся без скачивания");
        assertEquals(fake, provision.executable());
    }

    @Test
    void oldSystemJavaIsRejected() throws Exception {
        Path old = fakeJava("openjdk version \"17.0.9\" 2023-10-17");
        JavaRuntime.JavaInfo info = JavaRuntime.inspect(old);
        assertEquals(17, info.majorVersion(), "версия распознаётся из вывода java -version");
        assertFalse(info.meetsRequirement(), "Java 17 для 1.21.11 не годится");
    }

    @Test
    void alreadyDownloadedRuntimeIsReusedWithoutNetwork() throws Exception {
        LauncherPaths paths = new LauncherPaths(this.temporary);
        Path root = JavaProvisioner.runtimeDir(paths, "java-runtime-gamma", "linux");
        Files.createDirectories(root.resolve("bin"));
        Path java = fakeJavaIn(root.resolve("bin"), "openjdk version \"21.0.7\" 2025-04-15");

        JavaProvisioner.Provision provision = JavaProvisioner.ensure(paths, "java-runtime-gamma", 21,
                null, null, () -> null, () -> "linux", () -> "x86_64");

        assertEquals(JavaProvisioner.Source.MOJANG, provision.source(),
                "ранее скачанный рантайм используется повторно");
        assertEquals(java, provision.executable());
        assertNotNull(provision.versionText());
    }

    /**
     * Поддельная Java: настоящий скрипт, который печатает вывод в формате
     * {@code java -version}. Проверка идёт через настоящий запуск процесса.
     */
    private Path fakeJava(String versionLine) throws IOException {
        return fakeJavaIn(this.temporary, versionLine);
    }

    private Path fakeJavaIn(Path directory, String versionLine) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("java");
        Files.writeString(file, "#!/bin/sh\necho '" + versionLine + "'\n", StandardCharsets.UTF_8);
        if (!file.toFile().setExecutable(true, false)) {
            throw new IOException("не удалось сделать файл исполняемым: " + file);
        }
        return file;
    }
}
