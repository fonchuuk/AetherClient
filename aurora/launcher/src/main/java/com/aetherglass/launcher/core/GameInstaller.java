package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Установка игры: сам Minecraft, библиотеки, ресурсы, Fabric Loader.
 *
 * <p>Порядок полностью повторяет официальный лаунчер: JSON версии → клиентский jar →
 * библиотеки (с учётом правил ОС) → ресурсы (индекс и объекты) → профиль Fabric.
 * Каждый файл проверяется по размеру и SHA-1, поэтому повреждённая загрузка будет замечена
 * и файл будет скачан заново. Загрузку можно отменить в любой момент.</p>
 */
public final class GameInstaller {
    public static final String FABRIC_PROFILE_URL =
            "https://meta.fabricmc.net/v2/versions/loader/%s/%s/profile/json";
    public static final String QUILT_PROFILE_URL =
            "https://meta.quiltmc.org/v3/versions/loader/%s/%s/profile/json";

    private final LauncherPaths paths;
    private final String minecraftVersion;
    private final String loaderType;
    private final String loaderVersion;
    private final Progress progress;
    private final Http.CancelFlag cancel;

    private final Set<Path> classpath = new HashSet<>();
    private final List<Object> gameArguments = new ArrayList<>();

    public GameInstaller(LauncherPaths paths, String minecraftVersion, String loaderVersion,
                         Progress progress, Http.CancelFlag cancel) {
        this(paths, minecraftVersion, ModpackProfile.LOADER_FABRIC, loaderVersion, progress, cancel);
    }

    public GameInstaller(LauncherPaths paths, String minecraftVersion, String loaderType, String loaderVersion,
                         Progress progress, Http.CancelFlag cancel) {
        this.paths = paths;
        this.minecraftVersion = minecraftVersion;
        this.loaderType = ModpackProfile.normalizeLoader(loaderType);
        this.loaderVersion = loaderVersion;
        this.progress = progress == null ? new SilentProgress() : progress;
        this.cancel = cancel;
    }

    /** Описание установленной версии: всё, что нужно для запуска. */
    public record Installation(String versionId,
                               String fabricId,
                               String mainClass,
                               List<Path> classpath,
                               List<Object> jvmArguments,
                               List<Object> gameArguments,
                               String assetIndexId,
                               Path nativesDir,
                               Path logConfigFile,
                               String logConfigArgument,
                               Path javaExecutable,
                               String javaSource) {
    }

    /** Ход установки: этапы и файлы. */
    public interface Progress extends Http.ProgressListener {
        void stage(String text);
    }

    public Installation install() throws IOException, InterruptedException {
        this.paths.createDirectories();
        Map<String, Object> version = ensureVersionJson();
        this.progress.stage("Клиент Minecraft " + this.minecraftVersion);
        Path clientJar = ensureClientJar(version);

        this.progress.stage("Библиотеки Minecraft");
        Files.createDirectories(this.paths.nativesDir(this.minecraftVersion));
        downloadVanillaLibraries(version);

        this.progress.stage("Ресурсы игры");
        String assetIndexId = Json.string(Json.asObject(version.get("assetIndex")), "id", this.minecraftVersion);
        ensureAssets(version);

        Map<String, Object> fabric = Map.of();
        String fabricId;
        String resolvedMainClass;
        if (ModpackProfile.LOADER_VANILLA.equalsIgnoreCase(this.loaderType)) {
            this.progress.stage("Профиль Vanilla " + this.minecraftVersion);
            fabricId = "vanilla-" + this.minecraftVersion;
            resolvedMainClass = Json.string(version, "mainClass", "net.minecraft.client.main.Main");
        } else {
            this.progress.stage("Профиль " + this.loaderType + " Loader " + this.loaderVersion);
            fabric = ensureFabricProfile();
            fabricId = Json.requireString(fabric, "id");
            resolvedMainClass = Json.requireString(fabric, "mainClass");
            downloadFabricLibraries(fabric);
        }

        Map<String, Object> versionArguments = version.get("arguments") instanceof Map
                ? Json.asObject(version.get("arguments")) : Map.of();
        Map<String, Object> fabricArguments = fabric.get("arguments") instanceof Map
                ? Json.asObject(fabric.get("arguments")) : Map.of();

        List<Object> jvmArguments = new ArrayList<>();
        if (versionArguments.get("jvm") instanceof List<?> list) {
            jvmArguments.addAll(list);
        }
        if (fabricArguments.get("jvm") instanceof List<?> list) {
            jvmArguments.addAll(list);
        }
        if (versionArguments.get("game") instanceof List<?> list) {
            this.gameArguments.addAll(list);
        }
        if (fabricArguments.get("game") instanceof List<?> list) {
            this.gameArguments.addAll(list);
        }

        Path logConfig = ensureLogging(version);

        // Java: своя подходит — берём её, нет — скачиваем официальный рантайм
        // Mojang, ровно тот, который требует эта версия игры.
        Map<String, Object> javaVersion = Json.asObject(version.get("javaVersion"));
        String component = Json.string(javaVersion, "component", "java-runtime-gamma");
        int requiredMajor = Json.intValue(javaVersion, "majorVersion", JavaRuntime.REQUIRED_MAJOR);
        this.progress.stage("Java " + requiredMajor);
        JavaProvisioner.Provision java = JavaProvisioner.ensure(this.paths, component, requiredMajor,
                this.progress, this.cancel);

        List<Path> orderedClasspath = new ArrayList<>(this.classpath);
        orderedClasspath.add(clientJar);

        return new Installation(this.minecraftVersion, fabricId, resolvedMainClass,
                orderedClasspath, jvmArguments, List.copyOf(this.gameArguments), assetIndexId,
                this.paths.nativesDir(this.minecraftVersion), logConfig,
                logConfigArgumentOf(version), java.executable(), java.source().title());
    }

    private Map<String, Object> ensureVersionJson() throws IOException, InterruptedException {
        Path file = this.paths.versionJson(this.minecraftVersion);
        VersionIndex index = VersionIndex.fetch();
        VersionIndex.Entry entry = index.find(this.minecraftVersion);
        if (entry == null) {
            throw new IOException("версия " + this.minecraftVersion + " отсутствует в официальном манифесте Mojang");
        }
        Files.createDirectories(file.getParent());
        Object downloaded = Http.getJson(entry.url());
        Files.writeString(file, Json.write(downloaded), StandardCharsets.UTF_8);
        return Json.asObject(downloaded);
    }

    private Path ensureClientJar(Map<String, Object> version) throws IOException, InterruptedException {
        Map<String, Object> downloads = Json.asObject(version.get("downloads"));
        Map<String, Object> client = Json.asObject(downloads.get("client"));
        Path target = this.paths.versionJar(this.minecraftVersion);
        Http.download(Json.requireString(client, "url"), target,
                Json.longValue(client, "size", -1L),
                Json.string(client, "sha1", null),
                this.progress, this.cancel);
        return target;
    }

    private void downloadVanillaLibraries(Map<String, Object> version) throws IOException, InterruptedException {
        List<Object> libraries = Json.asArray(version.get("libraries"));
        for (Object item : libraries) {
            throwIfCancelled();
            Map<String, Object> library = Json.asObject(item);
            if (!Rules.allows(library, FEATURES)) {
                continue;
            }
            Map<String, Object> downloads = library.get("downloads") instanceof Map
                    ? Json.asObject(library.get("downloads")) : Map.of();

            Map<String, Object> artifact = downloads.get("artifact") instanceof Map
                    ? Json.asObject(downloads.get("artifact")) : null;
            if (artifact != null) {
                Path target = this.paths.libraryByPath(Json.requireString(artifact, "path"));
                download(Json.requireString(artifact, "url"), target,
                        Json.longValue(artifact, "size", -1L), Json.string(artifact, "sha1", null));
                this.classpath.add(target);
            }

            Map<String, Object> natives = library.get("natives") instanceof Map
                    ? Json.asObject(library.get("natives")) : null;
            if (natives == null) {
                continue;
            }
            String classifier = Json.string(natives, Rules.osName(), null);
            if (classifier == null) {
                continue;
            }
            Map<String, Object> classifiers = downloads.get("classifiers") instanceof Map
                    ? Json.asObject(downloads.get("classifiers")) : Map.of();
            Map<String, Object> nativeArtifact = classifiers.get(classifier) instanceof Map
                    ? Json.asObject(classifiers.get(classifier)) : null;
            if (nativeArtifact == null) {
                continue;
            }
            Path target = this.paths.libraryByPath(Json.requireString(nativeArtifact, "path"));
            download(Json.requireString(nativeArtifact, "url"), target,
                    Json.longValue(nativeArtifact, "size", -1L), Json.string(nativeArtifact, "sha1", null));
            this.classpath.add(target);
            extractNatives(target, library);
        }
    }

    private void extractNatives(Path archive, Map<String, Object> library) throws IOException {
        Set<String> excluded = new HashSet<>();
        Map<String, Object> extract = library.get("extract") instanceof Map
                ? Json.asObject(library.get("extract")) : null;
        if (extract != null && extract.get("exclude") instanceof List<?> list) {
            for (Object item : list) {
                excluded.add(String.valueOf(item));
            }
        }
        Path target = this.paths.nativesDir(this.minecraftVersion);
        Files.createDirectories(target);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.startsWith("META-INF/") || isExcluded(name, excluded)) {
                    continue;
                }
                Path destination = target.resolve(name).normalize();
                if (!destination.startsWith(target)) {
                    continue; // Защита от записи за пределы каталога.
                }
                Files.createDirectories(destination.getParent());
                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(destination)) {
                    input.transferTo(output);
                }
            }
        }
    }

    private static boolean isExcluded(String name, Set<String> excluded) {
        for (String prefix : excluded) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /** Ресурсы качаются параллельно: объектов больше четырёх тысяч, последовательно — слишком долго. */
    private void ensureAssets(Map<String, Object> version) throws IOException, InterruptedException {
        Map<String, Object> index = Json.asObject(version.get("assetIndex"));
        String id = Json.requireString(index, "id");
        Path indexFile = this.paths.assetIndexes().resolve(id + ".json");
        Http.download(Json.requireString(index, "url"), indexFile,
                Json.longValue(index, "size", -1L), Json.string(index, "sha1", null), this.progress, this.cancel);
        Map<String, Object> assetIndex = Json.asObject(Json.parse(Files.readString(indexFile, StandardCharsets.UTF_8)));
        Map<String, Object> objects = Json.asObject(assetIndex.get("objects"));

        boolean skipSounds = Boolean.getBoolean("aetherglass.skipSounds");
        List<AssetTask> pending = new ArrayList<>();
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            if (skipSounds && entry.getKey().endsWith(".ogg")) {
                continue;
            }
            Map<String, Object> object = Json.asObject(entry.getValue());
            String hash = Json.requireString(object, "hash");
            Path target = this.paths.assetObject(hash);
            long size = Json.longValue(object, "size", -1L);
            if (Files.isRegularFile(target) && Files.size(target) == size && Http.verify(target, hash)) {
                continue;
            }
            pending.add(new AssetTask(hash, target, size));
        }

        int total = objects.size();
        AtomicInteger done = new AtomicInteger(total - pending.size());
        Http.ProgressListener counter = (name, fileDone, fileTotal) -> this.progress.onFile(
                "ресурсы", done.get(), total);
        if (pending.isEmpty()) {
            this.progress.onFile("ресурсы", total, total);
            return;
        }

        int threads = Math.max(8, Math.min(24, Runtime.getRuntime().availableProcessors() * 4));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Future<?>> futures = new ArrayList<>(pending.size());
        try {
            for (AssetTask task : pending) {
                futures.add(pool.submit(() -> {
                    if (failure.get() != null || (this.cancel != null && this.cancel.isCancelled())) {
                        return;
                    }
                    try {
                        Http.download("https://resources.download.minecraft.net/"
                                        + task.hash().substring(0, 2) + "/" + task.hash(),
                                task.target(), task.size(), task.hash(), counter, this.cancel);
                        done.incrementAndGet();
                    } catch (IOException | RuntimeException exception) {
                        failure.compareAndSet(null, exception);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        failure.compareAndSet(null, exception);
                    }
                }));
            }
            pool.shutdown();
            // Ожидаем завершения, но продолжаем проверять флаг отмены.
            while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                if (this.cancel != null && this.cancel.isCancelled()) {
                    pool.shutdownNow();
                    throw new Http.CancelledException();
                }
                this.progress.stage("Ресурсы: " + done.get() + " из " + total);
            }
        } finally {
            pool.shutdownNow();
        }
        Throwable problem = failure.get();
        if (problem instanceof IOException ioException) {
            throw ioException;
        }
        if (problem instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (problem instanceof Error error) {
            throw error;
        }
        if (problem != null) {
            throw new IOException("не удалось скачать ресурсы: " + problem.getMessage(), problem);
        }
        this.progress.stage("Ресурсы загружены: " + total);
    }

    /** Один файл ресурсов, ожидающий загрузки. */
    private record AssetTask(String hash, Path target, long size) {
    }

    private Map<String, Object> ensureFabricProfile() throws IOException, InterruptedException {
        String url = String.format(FABRIC_PROFILE_URL, this.minecraftVersion, this.loaderVersion);
        Path file = this.paths.versionDir("fabric-loader-" + this.loaderVersion + "-" + this.minecraftVersion)
                .resolve("fabric-profile.json");
        Files.createDirectories(file.getParent());
        Object profile = Http.getJson(url);
        Files.writeString(file, Json.write(profile), StandardCharsets.UTF_8);
        return Json.asObject(profile);
    }

    private void downloadFabricLibraries(Map<String, Object> profile) throws IOException, InterruptedException {
        for (Object item : Json.asArray(profile.get("libraries"))) {
            throwIfCancelled();
            Map<String, Object> library = Json.asObject(item);
            String name = Json.requireString(library, "name");
            String url = Json.string(library, "url", null);
            if (url == null) {
                continue;
            }
            Path target = this.paths.library(name, null);
            String fileUrl = url.endsWith("/") ? url : url + "/";
            fileUrl += mavenPath(name);
            download(fileUrl, target, Json.longValue(library, "size", -1L), Json.string(library, "sha1", null));
            this.classpath.add(target);
        }
    }

    /** Путь к файлу библиотеки в репозитории Maven: {@code group/artifact/version/artifact-version.jar}. */
    public static String mavenPath(String name) {
        String[] parts = name.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("некорректное имя библиотеки: " + name);
        }
        String group = parts[0].replace('.', '/');
        return group + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
    }

    private Path ensureLogging(Map<String, Object> version) throws IOException, InterruptedException {
        Map<String, Object> logging = version.get("logging") instanceof Map
                ? Json.asObject(version.get("logging")) : null;
        if (logging == null || !(logging.get("client") instanceof Map)) {
            return null;
        }
        Map<String, Object> client = Json.asObject(logging.get("client"));
        Map<String, Object> file = Json.asObject(client.get("file"));
        Path target = this.paths.logConfigs().resolve(Json.requireString(file, "id"));
        Http.download(Json.requireString(file, "url"), target,
                Json.longValue(file, "size", -1L), Json.string(file, "sha1", null), this.progress, this.cancel);
        return target;
    }

    private static String logConfigArgumentOf(Map<String, Object> version) {
        Map<String, Object> logging = version.get("logging") instanceof Map
                ? Json.asObject(version.get("logging")) : null;
        if (logging == null || !(logging.get("client") instanceof Map)) {
            return null;
        }
        return Json.string(Json.asObject(logging.get("client")), "argument", null);
    }

    private void download(String url, Path target, long size, String sha1) throws IOException, InterruptedException {
        Http.download(url, target, size < 0 ? null : size, sha1, this.progress, this.cancel);
    }

    private void throwIfCancelled() throws IOException {
        if (this.cancel != null && this.cancel.isCancelled()) {
            throw new Http.CancelledException();
        }
    }

    private static final Map<String, Boolean> FEATURES = Map.of(
            "is_demo_user", Boolean.FALSE,
            "has_custom_resolution", Boolean.FALSE);

    /** Заглушка прогресса, чтобы вызывающий код не проверял слушателя на null. */
    private static final class SilentProgress implements Progress {
        @Override
        public void stage(String text) {
            // Прогресс никому не нужен.
        }

        @Override
        public void onFile(String name, long done, long total) {
            // Прогресс никому не нужен.
        }
    }
}
