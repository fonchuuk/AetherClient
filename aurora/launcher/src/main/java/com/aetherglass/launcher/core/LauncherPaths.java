package com.aetherglass.launcher.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Раскладка каталогов лаунчера.
 *
 * <p>Структура совпадает с принятой в официальном лаунчере Mojang: библиотеки лежат в {@code libraries},
 * ресурсы — в {@code assets}, версии — в {@code versions}. Благодаря этому установленные файлы
 * совместимы с другими инструментами и не скачиваются повторно.</p>
 */
public final class LauncherPaths {
    private final Path base;
    private final Path libraries;
    private final Path assets;
    private final Path assetObjects;
    private final Path assetIndexes;
    private final Path logConfigs;
    private final Path versions;
    private final Path mods;
    private final Path resourcePacks;
    private final Path shaderPacks;
    private final Path screenshots;

    public LauncherPaths(Path base) {
        this.base = base.toAbsolutePath().normalize();
        this.libraries = this.base.resolve("libraries");
        this.assets = this.base.resolve("assets");
        this.assetObjects = this.assets.resolve("objects");
        this.assetIndexes = this.assets.resolve("indexes");
        this.logConfigs = this.assets.resolve("log_configs");
        this.versions = this.base.resolve("versions");
        this.mods = this.base.resolve("mods");
        this.resourcePacks = this.base.resolve("resourcepacks");
        this.shaderPacks = this.base.resolve("shaderpacks");
        this.screenshots = this.base.resolve("screenshots");
    }

    /** Каталог, в котором лежит игра (он же рабочий каталог Minecraft). */
    public Path base() {
        return this.base;
    }

    public Path libraries() {
        return this.libraries;
    }

    public Path assets() {
        return this.assets;
    }

    public Path assetObjects() {
        return this.assetObjects;
    }

    public Path assetIndexes() {
        return this.assetIndexes;
    }

    public Path logConfigs() {
        return this.logConfigs;
    }

    public Path versions() {
        return this.versions;
    }

    public Path mods() {
        return this.mods;
    }

    public Path resourcePacks() {
        return this.resourcePacks;
    }

    public Path shaderPacks() {
        return this.shaderPacks;
    }

    public Path screenshots() {
        return this.screenshots;
    }

    public Path versionDir(String versionId) {
        return this.versions.resolve(safe(versionId));
    }

    public Path versionJson(String versionId) {
        return this.versionDir(versionId).resolve(safe(versionId) + ".json");
    }

    public Path versionJar(String versionId) {
        return this.versionDir(versionId).resolve(safe(versionId) + ".jar");
    }

    public Path nativesDir(String versionId) {
        return this.versionDir(versionId).resolve("natives-" + safe(versionId));
    }

    public Path launcherConfig() {
        return this.base.resolve("aurora-launcher.json");
    }

    public Path authFile() {
        return this.base.resolve("aurora-auth.json");
    }

    public Path logsDir() {
        return this.base.resolve("logs");
    }

    /** Каталог скачанных Java-рантаймов Mojang. */
    public Path runtimes() {
        return this.base.resolve("runtime");
    }

    /** Путь к объекту ресурсов: первые два символа хэша — имя каталога. */
    public Path assetObject(String hash) {
        return this.assetObjects.resolve(hash.substring(0, 2)).resolve(hash);
    }

    /** Путь к файлу библиотеки Maven по имени вида {@code group:artifact:version}. */
    public Path library(String name, String classifier) {
        String[] parts = name.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("некорректное имя библиотеки: " + name);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String file = artifact + "-" + version + (classifier == null || classifier.isEmpty() ? "" : "-" + classifier)
                + ".jar";
        return this.libraries.resolve(group).resolve(artifact).resolve(version).resolve(file);
    }

    /** Путь к файлу библиотеки, заданному явным путём (используется в версиях Mojang). */
    public Path libraryByPath(String path) {
        return this.libraries.resolve(path.replace('\\', '/'));
    }

    public static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    /** Создаёт все каталоги, которые нужны для работы. */
    public List<Path> createDirectories() throws IOException {
        List<Path> created = new ArrayList<>();
        for (Path directory : List.of(this.base, this.libraries, this.assetObjects, this.assetIndexes,
                this.logConfigs, this.versions, this.mods, this.logsDir())) {
            if (!Files.isDirectory(directory)) {
                Files.createDirectories(directory);
                created.add(directory);
            }
        }
        return created;
    }
}
