package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Откуда берётся Java для запуска игры.
 *
 * <p>Порядок такой:</p>
 * <ol>
 *   <li>Java, которую игрок указал сам, или подходящая системная Java
 *       ({@link JavaRuntime#detect}) — если она есть, ничего не скачивается;</li>
 *   <li>уже скачанный ранее официальный рантайм Mojang в каталоге игры;</li>
 *   <li>скачивание официального рантайма Mojang — ровно того компонента,
 *       который указан в JSON версии игры (например {@code java-runtime-gamma}).</li>
 * </ol>
 *
 * <p>Никаких сторонних сборок и «своих» установщиков Java: источник — официальный
 * список рантаймов Mojang, тот самый, которым пользуется официальный лаунчер.
 * Файлы проверяются по SHA-1, загрузку можно отменить.</p>
 */
public final class JavaProvisioner {

    /** Официальный список Java-рантаймов Mojang. */
    public static final String RUNTIME_INDEX =
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

    /** Откуда взялась Java. */
    public enum Source {
        /** Системная или указанная игроком. */
        SYSTEM("системная"),
        /** Официальный рантайм Mojang, скачанный в каталог игры. */
        MOJANG("рантайм Mojang");

        private final String title;

        Source(String title) {
            this.title = title;
        }

        public String title() {
            return this.title;
        }
    }

    /** Результат: какой исполняемый файл Java использовать и откуда он. */
    public record Provision(Path executable, Source source, String versionText) {
    }

    /** Один файл рантайма из манифеста Mojang. */
    public record RuntimeFile(String path, String url, String sha1, long size, boolean executable) {
    }

    private JavaProvisioner() {
    }

    // ─────────────────────────── платформа ───────────────────────────

    /**
     * Ключ платформы в официальном списке рантаймов Mojang.
     *
     * @param os   имя ОС в форме Mojang: {@code windows}, {@code linux}, {@code osx}
     * @param arch архитектура: {@code x86_64}, {@code x86}, {@code arm64}
     */
    public static String platformKey(String os, String arch) throws IOException {
        return switch (os + "-" + arch) {
            case "windows-x86_64" -> "windows-x64";
            case "windows-x86" -> "windows-x86";
            case "windows-arm64" -> "windows-arm64";
            case "linux-x86_64" -> "linux";
            case "linux-x86" -> "linux-i386";
            case "osx-x86_64" -> "mac-os";
            case "osx-arm64" -> "mac-os-arm64";
            default -> throw new IOException("у Mojang нет готового рантайма Java для " + os + " (" + arch + ")");
        };
    }

    /** Каталог рантайма: {@code <игра>/runtime/<компонент>/<платформа>}. */
    public static Path runtimeDir(LauncherPaths paths, String component, String platform) {
        return paths.runtimes().resolve(component).resolve(platform);
    }

    /**
     * Исполняемый файл Java внутри уже скачанного рантайма.
     *
     * <p>Раскладка отличается: на Windows и Linux это {@code bin/java},
     * на macOS — внутри {@code jre.bundle}. Проверяем все известные варианты.</p>
     */
    public static Path executableIn(Path root) {
        List<Path> candidates = List.of(
                root.resolve("bin").resolve("javaw.exe"),
                root.resolve("bin").resolve("java.exe"),
                root.resolve("bin").resolve("java"),
                root.resolve("jre.bundle").resolve("Contents").resolve("Home").resolve("bin").resolve("java"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return root.resolve("bin").resolve("java");
    }

    // ─────────────────────────── подготовка ───────────────────────────

    /**
     * Находит подходящую Java или скачивает официальный рантайм Mojang.
     *
     * @param component     компонент рантайма из JSON версии игры
     * @param requiredMajor требуемая мажорная версия Java
     */
    public static Provision ensure(LauncherPaths paths, String component, int requiredMajor,
                                   Http.ProgressListener progress, Http.CancelFlag cancel)
            throws IOException, InterruptedException {
        return ensure(paths, component, requiredMajor, progress, cancel,
                JavaRuntime::detect, Rules::osName, Rules::osArch);
    }

    /**
     * То же самое, но с подменяемыми источниками: тестам не нужна ни сеть,
     * ни настоящая системная Java.
     */
    static Provision ensure(LauncherPaths paths, String component, int requiredMajor,
                            Http.ProgressListener progress, Http.CancelFlag cancel,
                            Supplier<Path> systemJava, Supplier<String> os, Supplier<String> arch)
            throws IOException, InterruptedException {
        Path system = systemJava.get();
        if (system != null) {
            JavaRuntime.JavaInfo info = JavaRuntime.inspect(system);
            if (info.meetsRequirement() && info.majorVersion() >= requiredMajor) {
                return new Provision(info.executable(), Source.SYSTEM, info.versionText());
            }
        }

        String platform = platformKey(os.get(), arch.get());
        Path root = runtimeDir(paths, component, platform);
        Path existing = executableIn(root);
        if (Files.isRegularFile(existing)) {
            JavaRuntime.JavaInfo info = JavaRuntime.inspect(existing);
            if (info.meetsRequirement()) {
                return new Provision(existing, Source.MOJANG, info.versionText());
            }
        }

        if (progress instanceof GameInstaller.Progress stages) {
            stages.stage("Java " + requiredMajor + ": официальный рантайм Mojang (" + platform + ")");
        }
        Map<String, Object> manifest = Json.asObject(Http.getJson(manifestUrl(component, platform)));
        List<RuntimeFile> files = filesOf(manifest);
        for (RuntimeFile file : files) {
            Path target = root.resolve(file.path());
            if (Files.isRegularFile(target) && Http.verify(target, file.sha1())) {
                markExecutable(target, file.executable());
                continue;
            }
            Files.createDirectories(target.getParent());
            Http.download(file.url(), target, file.size(), file.sha1(), progress, cancel);
            markExecutable(target, file.executable());
        }

        Path downloaded = executableIn(root);
        if (!Files.isRegularFile(downloaded)) {
            throw new IOException("в скачанном рантайме Mojang нет исполняемого файла Java: " + downloaded);
        }
        JavaRuntime.JavaInfo info = JavaRuntime.inspect(downloaded);
        if (!info.meetsRequirement()) {
            throw new IOException("скачанный рантайм не запускается: "
                    + (info.error() != null ? info.error() : info.versionText()));
        }
        return new Provision(downloaded, Source.MOJANG, info.versionText());
    }

    private static void markExecutable(Path file, boolean executable) {
        if (executable) {
            file.toFile().setExecutable(true, false);
        }
    }

    // ─────────────────────────── разбор ответов Mojang ───────────────────────────

    /**
     * Ссылка на манифест рантайма: из официального списка Mojang берётся
     * запись для нужной платформы и нужного компонента.
     */
    static String manifestUrl(String component, String platform) throws IOException, InterruptedException {
        return manifestUrl(component, platform, Json.asObject(Http.getJson(RUNTIME_INDEX)));
    }

    /**
     * Чистый разбор: из уже полученного списка Mojang. Отделён от сети,
     * поэтому проверяется тестом на настоящем формате ответа.
     */
    static String manifestUrl(String component, String platform, Map<String, Object> index) throws IOException {
        // Отсутствующие ключи — это данные, а не ошибка в коде: сообщаем понятно.
        if (!(index.get(platform) instanceof Map)) {
            throw new IOException("в списке рантаймов Mojang нет платформы " + platform);
        }
        Map<String, Object> platformEntry = Json.asObject(index.get(platform));
        if (!(platformEntry.get(component) instanceof List)) {
            throw new IOException("у Mojang нет рантайма " + component + " для платформы " + platform);
        }
        List<Object> entries = Json.asArray(platformEntry.get(component));
        if (entries.isEmpty()) {
            throw new IOException("у Mojang нет рантайма " + component + " для платформы " + platform);
        }
        Map<String, Object> latest = Json.asObject(entries.get(0));
        if (!(latest.get("manifest") instanceof Map)) {
            throw new IOException("в ответе Mojang у рантайма " + component + " нет описания манифеста");
        }
        return Json.requireString(Json.asObject(latest.get("manifest")), "url");
    }

    /** Список файлов из манифеста рантайма; записи про каталоги пропускаются. */
    static List<RuntimeFile> filesOf(Map<String, Object> manifest) {
        Map<String, Object> files = Json.asObject(manifest.get("files"));
        List<RuntimeFile> result = new ArrayList<>();
        files.forEach((path, value) -> {
            Map<String, Object> entry = Json.asObject(value);
            if (!"file".equals(Json.string(entry, "type", "file"))) {
                return;
            }
            Map<String, Object> raw = Json.asObject(Json.asObject(entry.get("downloads")).get("raw"));
            result.add(new RuntimeFile(path, Json.requireString(raw, "url"), Json.requireString(raw, "sha1"),
                    Json.longValue(raw, "size", 0L), Json.boolValue(entry, "executable", false)));
        });
        return List.copyOf(result);
    }
}
