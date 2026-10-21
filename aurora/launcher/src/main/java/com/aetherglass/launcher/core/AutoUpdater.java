package com.aetherglass.launcher.core;

import com.aetherglass.launcher.LauncherMain;
import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Автообновление лаунчера и клиентского мода AetherGlass напрямую с GitHub Releases
 * в 1 клик с последующим автоматическим перезапуском обновлённого лаунчера.
 */
public final class AutoUpdater {
    public static final String RELEASES_LATEST_URL =
            "https://api.github.com/repos/fonchuuk/AetherClient/releases/latest";
    public static final String RELEASES_LIST_URL =
            "https://api.github.com/repos/fonchuuk/AetherClient/releases";

    private AutoUpdater() {
    }

    public record AssetInfo(String name, String downloadUrl, long size) {
    }

    public record ReleaseInfo(
            String tagName,
            String title,
            String notes,
            boolean newerThanCurrent,
            List<AssetInfo> assets
    ) {
        public AssetInfo findAsset(String name) {
            for (AssetInfo asset : this.assets) {
                if (asset.name().equalsIgnoreCase(name)) {
                    return asset;
                }
            }
            return null;
        }
    }

    /**
     * Проверяет последний релиз на GitHub и сравнивает версию с {@link GameLauncher#LAUNCHER_VERSION}.
     */
    public static ReleaseInfo checkLatestRelease() throws IOException, InterruptedException {
        Map<String, Object> releaseObj = null;
        try {
            Object raw = Http.getJson(RELEASES_LATEST_URL);
            if (raw instanceof Map<?, ?>) {
                releaseObj = Json.asObject(raw);
            }
        } catch (IOException ignored) {
            // Запасной путь через общий список релизов (включая pre-release)
        }
        if (releaseObj == null || releaseObj.isEmpty()) {
            Object listRaw = Http.getJson(RELEASES_LIST_URL);
            if (listRaw instanceof List<?> list && !list.isEmpty()) {
                releaseObj = Json.asObject(list.get(0));
            }
        }
        if (releaseObj == null || releaseObj.isEmpty()) {
            throw new IOException("Не удалось получить данные о релизах с GitHub");
        }

        String tag = Json.string(releaseObj, "tag_name", "v" + GameLauncher.LAUNCHER_VERSION);
        String name = Json.string(releaseObj, "name", tag);
        String body = Json.string(releaseObj, "body", "");
        List<AssetInfo> assets = new ArrayList<>();
        if (releaseObj.get("assets") instanceof List<?> rawAssets) {
            for (Object item : rawAssets) {
                if (item instanceof Map<?, ?>) {
                    Map<String, Object> aObj = Json.asObject(item);
                    String aName = Json.string(aObj, "name", "");
                    String aUrl = Json.string(aObj, "browser_download_url", "");
                    long aSize = Json.longValue(aObj, "size", 0L);
                    if (!aName.isEmpty() && !aUrl.isEmpty()) {
                        assets.add(new AssetInfo(aName, aUrl, aSize));
                    }
                }
            }
        }
        boolean newer = compareVersions(cleanVersion(tag), cleanVersion(GameLauncher.LAUNCHER_VERSION)) > 0;
        return new ReleaseInfo(tag, name, body, newer, List.copyOf(assets));
    }

    /**
     * Скачивает свежие файлы клиента ({@code AetherGlass.jar}) и лаунчера
     * ({@code AetherGlassLauncher.exe} / {@code AetherGlassLauncher.jar}) из релиза.
     */
    public static Path downloadUpdate(ReleaseInfo release,
                                      LauncherPaths paths,
                                      Http.ProgressListener progress,
                                      Http.CancelFlag cancelled) throws IOException, InterruptedException {
        Path updatesDir = paths.base().resolve("updates");
        Files.createDirectories(updatesDir);

        // 1. Обновляем клиентский мод AetherGlass.jar в папке mods/
        AssetInfo clientAsset = release.findAsset("AetherGlass.jar");
        if (clientAsset != null) {
            Files.createDirectories(paths.mods());
            Path modTarget = paths.mods().resolve("AetherGlass.jar");
            Http.download(clientAsset.downloadUrl(), modTarget,
                    clientAsset.size() > 0 ? clientAsset.size() : null, null, progress, cancelled);
        }

        // 2. Определяем, в каком формате запущен текущий лаунчер (.exe или .jar)
        Path currentBinary = detectCurrentLauncherBinary();
        boolean isExe = currentBinary != null
                && currentBinary.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe");

        AssetInfo launcherAsset = isExe
                ? release.findAsset("AetherGlassLauncher.exe")
                : release.findAsset("AetherGlassLauncher.jar");
        if (launcherAsset == null) {
            launcherAsset = release.findAsset("AetherGlassLauncher.jar");
        }
        if (launcherAsset == null) {
            launcherAsset = release.findAsset("AetherGlassLauncher.exe");
        }
        if (launcherAsset == null) {
            throw new IOException("В релизе " + release.tagName() + " не найден файл лаунчера");
        }

        Path stagedFile = updatesDir.resolve(launcherAsset.name());
        Http.download(launcherAsset.downloadUrl(), stagedFile,
                launcherAsset.size() > 0 ? launcherAsset.size() : null, null, progress, cancelled);
        return stagedFile;
    }

    /**
     * Запускает скачанную обновлённую версию лаунчера и завершает текущий процесс.
     */
    public static void applyAndRestart(Path downloadedLauncher) throws IOException {
        if (downloadedLauncher == null || !Files.isRegularFile(downloadedLauncher)) {
            throw new IOException("Файл обновления не найден: " + downloadedLauncher);
        }
        Path currentBinary = detectCurrentLauncherBinary();
        Path targetToLaunch = downloadedLauncher.toAbsolutePath().normalize();

        if (currentBinary != null && Files.isRegularFile(currentBinary)) {
            String curName = currentBinary.getFileName().toString().toLowerCase(Locale.ROOT);
            String dlName = downloadedLauncher.getFileName().toString().toLowerCase(Locale.ROOT);
            if (curName.endsWith(".jar") && dlName.endsWith(".jar")) {
                try {
                    Files.copy(downloadedLauncher, currentBinary, StandardCopyOption.REPLACE_EXISTING);
                    targetToLaunch = currentBinary;
                } catch (IOException ignored) {
                    // На Windows файл может быть заблокирован: запускаем напрямую из updates/
                }
            }
        }

        String fileName = targetToLaunch.getFileName().toString().toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        if (fileName.endsWith(".exe")) {
            cmd.add(targetToLaunch.toString());
        } else {
            String javaBin = ProcessHandle.current().info().command().orElse("java");
            cmd.add(javaBin);
            cmd.add("-jar");
            cmd.add(targetToLaunch.toString());
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        if (targetToLaunch.getParent() != null) {
            pb.directory(targetToLaunch.getParent().toFile());
        }
        pb.start();
        System.exit(0);
    }

    public static Path detectCurrentLauncherBinary() {
        try {
            var codeSource = LauncherMain.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                URI uri = codeSource.getLocation().toURI();
                Path path = Path.of(uri).toAbsolutePath().normalize();
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String cleanVersion(String raw) {
        if (raw == null) {
            return "0.0.0";
        }
        String s = raw.trim();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1).trim();
        }
        int dash = s.indexOf('-');
        if (dash > 0) {
            s = s.substring(0, dash);
        }
        return s;
    }

    public static int compareVersions(String a, String b) {
        String[] pa = cleanVersion(a).split("\\.");
        String[] pb = cleanVersion(b).split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int va = i < pa.length ? parsePart(pa[i]) : 0;
            int vb = i < pb.length ? parsePart(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parsePart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
