package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Установка модов из встроенного манифеста.
 *
 * <p>Манифест {@code mods.json} собран из Modrinth API и содержит точные версии, размеры
 * и контрольные суммы SHA-512. Лаунчер скачивает только то, чего нет, и проверяет сумму —
 * случайно изменённый или подменённый файл будет перескачан.</p>
 */
public final class ModInstaller {
    private static final String MANIFEST = "/mods.json";

    private final LauncherPaths paths;
    private final Http.CancelFlag cancel;
    private final Http.ProgressListener progress;
    private final Manifest manifest;

    /**
     * Собирает установщик по уже разобранному манифесту.
     *
     * <p>Открытый, потому что манифестом можно управлять: проверять его отдельно
     * от ресурсов лаунчера (что и делает тест) или брать не из ресурсов, а из файла.</p>
     */
    public ModInstaller(LauncherPaths paths, Manifest manifest, Http.ProgressListener progress,
                        Http.CancelFlag cancel) {
        this.paths = paths;
        this.manifest = manifest;
        this.progress = progress;
        this.cancel = cancel;
    }

    /** Читает встроенный манифест модов. */
    public static ModInstaller fromResource(LauncherPaths paths, Http.ProgressListener progress,
                                            Http.CancelFlag cancel) throws IOException {
        try (InputStream stream = ModInstaller.class.getResourceAsStream(MANIFEST)) {
            if (stream == null) {
                throw new IOException("в ресурсах лаунчера отсутствует файл " + MANIFEST);
            }
            String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return new ModInstaller(paths, Manifest.parse(text), progress, cancel);
        }
    }

    public Manifest manifest() {
        return this.manifest;
    }

    /**
     * Скачивает отсутствующие моды в каталог {@code mods}.
     *
     * @return список установленных файлов
     */
    public List<Path> install() throws IOException, InterruptedException {
        Files.createDirectories(this.paths.mods());
        checkDependencies();
        List<Path> installed = new ArrayList<>();
        for (Manifest.Mod mod : required()) {
            if (this.cancel != null && this.cancel.isCancelled()) {
                throw new Http.CancelledException();
            }
            Path target = this.paths.mods().resolve(mod.fileName());
            if (Files.isRegularFile(target) && Files.size(target) == mod.size()
                    && Http.sha512(target).equalsIgnoreCase(mod.sha512())) {
                installed.add(target);
                continue;
            }
            Http.download(mod.url(), target, mod.size(), mod.sha1(), this.progress, this.cancel);
            String actual = Http.sha512(target);
            if (!actual.equalsIgnoreCase(mod.sha512())) {
                Files.deleteIfExists(target);
                throw new IOException("контрольная сумма SHA-512 мода " + mod.name() + " не совпадает");
            }
            installed.add(target);
        }
        return installed;
    }

    /** Файлы модов, которые отсутствуют или повреждены. */
    public List<Manifest.Mod> missing() throws IOException {
        List<Manifest.Mod> result = new ArrayList<>();
        for (Manifest.Mod mod : required()) {
            Path target = this.paths.mods().resolve(mod.fileName());
            if (!Files.isRegularFile(target) || Files.size(target) != mod.size()
                    || !Http.sha512(target).equalsIgnoreCase(mod.sha512())) {
                result.add(mod);
            }
        }
        return result;
    }

    /**
     * Все записи, которые нужно установить: десять оптимизаций плюс Fabric API.
     *
     * <p>Fabric API хранится в манифесте отдельным полем, потому что в список
     * оптимизаций не входит. Но без него часть модов вообще не запускается —
     * это выяснилось на реальном запуске игры, а не в теории: моды требуют
     * Fabric API, и Fabric Loader отказывается стартовать с понятной ошибкой.
     * Поэтому устанавливается ровно одиннадцать файлов: десять модов и библиотека.</p>
     */
    public List<Manifest.Mod> required() {
        List<Manifest.Mod> result = new ArrayList<>(this.manifest.mods());
        if (this.manifest.fabricApi() != null) {
            result.add(this.manifest.fabricApi());
        }
        return List.copyOf(result);
    }

    /**
     * Проверка зависимостей до скачивания: если какой-то мод требует проект,
     * которого нет в манифесте, лучше сказать об этом сразу и понятно,
     * чем получить отказ игры уже после установки.
     */
    public void checkDependencies() throws IOException {
        Set<String> present = new HashSet<>();
        for (Manifest.Mod mod : required()) {
            present.add(mod.id());
        }
        List<String> problems = new ArrayList<>();
        for (Manifest.Mod mod : required()) {
            for (String dependency : mod.dependencies()) {
                if (!present.contains(dependency)) {
                    problems.add(mod.id() + " требует " + dependency + ", а его нет в манифесте");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IOException("манифест модов неполный: " + String.join("; ", problems));
        }
    }

    /**
     * Копирует собранный мод клиента (jar из {@code client/build/libs}) в каталог модов,
     * чтобы игра запускалась с самим клиентом Aurora.
     */
    public Path installClientMod(Path builtJar) throws IOException {
        if (builtJar == null || !Files.isRegularFile(builtJar)) {
            throw new IOException("собранный мод клиента не найден: " + builtJar
                    + " — выполните сборку Gradle перед установкой");
        }
        Files.createDirectories(this.paths.mods());
        Path target = this.paths.mods().resolve(builtJar.getFileName().toString());
        if (Files.exists(target) && Files.isSameFile(builtJar, target)) {
            return target;
        }
        Files.copy(builtJar, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }

    /** Разобранный манифест модов. */
    public static final class Manifest {
        private final String minecraftVersion;
        private final String loader;
        private final List<Mod> mods;
        private final Mod fabricApi;

        private Manifest(String minecraftVersion, String loader, List<Mod> mods, Mod fabricApi) {
            this.minecraftVersion = minecraftVersion;
            this.loader = loader;
            this.mods = mods;
            this.fabricApi = fabricApi;
        }

        public static Manifest parse(String json) throws IOException {
            Map<String, Object> root = Json.asObject(Json.parse(json));
            List<Mod> mods = new ArrayList<>();
            for (Object item : Json.asArray(root.get("mods"))) {
                mods.add(Mod.parse(Json.asObject(item)));
            }
            Mod fabricApi = root.get("fabric_api") instanceof Map
                    ? Mod.parse(Json.asObject(root.get("fabric_api"))) : null;
            return new Manifest(Json.string(root, "minecraft_version", "?"),
                    Json.string(root, "loader", "fabric"), List.copyOf(mods), fabricApi);
        }

        public String minecraftVersion() {
            return this.minecraftVersion;
        }

        public String loader() {
            return this.loader;
        }

        public List<Mod> mods() {
            return this.mods;
        }

        public Mod fabricApi() {
            return this.fabricApi;
        }

        /** Одна запись манифеста. */
        public record Mod(String id, String name, String version, String purpose, String fileName, String url,
                          long size, String sha1, String sha512, List<String> dependencies, String projectUrl) {
            static Mod parse(Map<String, Object> data) {
                Map<String, Object> file = Json.asObject(data.get("file"));
                List<String> dependencies = new ArrayList<>();
                for (Object item : Json.asArray(data.get("dependencies"))) {
                    dependencies.add(String.valueOf(item));
                }
                return new Mod(Json.string(data, "id", "?"),
                        Json.string(data, "name", "?"),
                        Json.string(data, "version", "?"),
                        Json.string(data, "purpose", ""),
                        Json.requireString(file, "name"),
                        Json.requireString(file, "url"),
                        Json.longValue(file, "size", -1L),
                        Json.string(file, "sha1", null),
                        Json.requireString(file, "sha512"),
                        List.copyOf(dependencies),
                        Json.string(data, "project_url", ""));
            }

            @Override
            public String toString() {
                return this.name;
            }
        }
    }
}
