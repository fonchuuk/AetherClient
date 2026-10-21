package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Экспорт и импорт сборок ({@code .zip} / {@code .mrpack}), обработка Drag & Drop файлов
 * и резервное копирование миров и настроек ({@code backups/*.zip}).
 */
public final class ModpackArchive {
    private static final String PACK_MANIFEST = "aetherglass-pack.json";
    private static final DateTimeFormatter TS_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ModpackArchive() {
    }

    /**
     * Упаковывает выбранную сборку (метаданные + моды + ресурспаки + шейдеры + конфиг клиента) в .zip архив.
     */
    public static Path exportModpack(ModpackProfile pack, LauncherPaths paths, Path targetZip) throws IOException {
        if (targetZip.getParent() != null) {
            Files.createDirectories(targetZip.getParent());
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(targetZip), StandardCharsets.UTF_8)) {
            Map<String, Object> meta = new LinkedHashMap<>(pack.toMap());
            meta.put("exportedBy", "AetherGlassLauncher " + GameLauncher.LAUNCHER_VERSION);
            byte[] metaBytes = Json.writePretty(meta).getBytes(StandardCharsets.UTF_8);
            zos.putNextEntry(new ZipEntry(PACK_MANIFEST));
            zos.write(metaBytes);
            zos.closeEntry();

            addDirectoryFiles(zos, paths.mods(), "mods/", ".jar");
            addDirectoryFiles(zos, paths.resourcePacks(), "resourcepacks/", ".zip");
            addDirectoryFiles(zos, paths.shaderPacks(), "shaderpacks/", ".zip");

            Path clientConf = paths.base().resolve("aetherglass").resolve("aetherglass.conf");
            if (Files.isRegularFile(clientConf)) {
                zos.putNextEntry(new ZipEntry("aetherglass/aetherglass.conf"));
                Files.copy(clientConf, zos);
                zos.closeEntry();
            }
        }
        return targetZip;
    }

    /**
     * Умный импорт файла (при Drag & Drop в окно лаунчера или через диалог «Импорт»):
     * автоматически распознаёт мод (.jar), шейдерпак (.zip), ресурспак (.zip) или полную сборку (.zip / .mrpack).
     */
    public static String importSmartFile(Path file, LauncherPaths paths, LauncherConfig config) throws IOException {
        if (file == null || !Files.isRegularFile(file)) {
            throw new IOException("Файл не найден: " + file);
        }
        String fileName = file.getFileName().toString();
        String lower = fileName.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".jar")) {
            Files.createDirectories(paths.mods());
            Path dest = paths.mods().resolve(fileName);
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
            return "Добавлен мод в папку mods/: " + fileName;
        }

        if (lower.endsWith(".zip") || lower.endsWith(".mrpack")) {
            try (ZipFile zip = new ZipFile(file.toFile())) {
                boolean hasPackManifest = zip.getEntry(PACK_MANIFEST) != null
                        || zip.getEntry("modrinth.index.json") != null;
                boolean hasModsFolder = zip.stream().anyMatch(e -> e.getName().startsWith("mods/"));
                boolean hasShadersFolder = zip.stream().anyMatch(e -> e.getName().startsWith("shaders/"));
                boolean hasPackMcmeta = zip.getEntry("pack.mcmeta") != null;

                if (hasPackManifest || hasModsFolder || lower.endsWith(".mrpack")) {
                    return extractModpackArchive(zip, fileName, paths, config);
                }
                if (hasShadersFolder) {
                    Files.createDirectories(paths.shaderPacks());
                    Path dest = paths.shaderPacks().resolve(fileName);
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    return "Установлен шейдерпак (Iris): " + fileName;
                }
                if (hasPackMcmeta) {
                    Files.createDirectories(paths.resourcePacks());
                    Path dest = paths.resourcePacks().resolve(fileName);
                    Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
                    return "Установлен ресурспак: " + fileName;
                }
            }
            // По умолчанию кладём .zip в resourcepacks/
            Files.createDirectories(paths.resourcePacks());
            Path dest = paths.resourcePacks().resolve(fileName);
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
            return "Добавлен архив в resourcepacks/: " + fileName;
        }

        throw new IOException("Неподдерживаемый формат файла: " + fileName + " (ожидается .jar, .zip или .mrpack)");
    }

    /**
     * Создаёт резервную копию настроек игры (options.txt), конфигов AetherGlass и сохранений миров (saves/).
     */
    public static Path backupWorldsAndConfigs(LauncherPaths paths) throws IOException {
        Path backupsDir = paths.base().resolve("backups");
        Files.createDirectories(backupsDir);
        String stamp = LocalDateTime.now().format(TS_FORMAT);
        Path archive = backupsDir.resolve("aetherglass-backup-" + stamp + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archive), StandardCharsets.UTF_8)) {
            Path optionsTxt = paths.base().resolve("options.txt");
            if (Files.isRegularFile(optionsTxt)) {
                zos.putNextEntry(new ZipEntry("options.txt"));
                Files.copy(optionsTxt, zos);
                zos.closeEntry();
            }
            Path launcherJson = paths.launcherConfig();
            if (Files.isRegularFile(launcherJson)) {
                zos.putNextEntry(new ZipEntry("aurora-launcher.json"));
                Files.copy(launcherJson, zos);
                zos.closeEntry();
            }
            addTree(zos, paths.base().resolve("aetherglass"), "aetherglass/");
            addTree(zos, paths.base().resolve("config"), "config/");
            addTree(zos, paths.base().resolve("saves"), "saves/");
        }
        return archive;
    }

    private static String extractModpackArchive(ZipFile zip, String archiveName,
                                                LauncherPaths paths, LauncherConfig config) throws IOException {
        String packName = archiveName.replaceAll("(?i)\\.(zip|mrpack)$", "");
        String mcVer = config.selectedVersion();
        String loader = config.selectedLoader();
        boolean aether = true;
        boolean optimize = true;

        ZipEntry manifestEntry = zip.getEntry(PACK_MANIFEST);
        if (manifestEntry != null) {
            try (InputStream in = zip.getInputStream(manifestEntry)) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Map<String, Object> map = Json.asObject(Json.parse(text));
                ModpackProfile parsed = ModpackProfile.fromMap(map);
                packName = parsed.name();
                mcVer = parsed.minecraftVersion();
                loader = parsed.loader();
                aether = parsed.installAetherGlassClient();
                optimize = parsed.autoOptimizeMods();
            } catch (Exception ignored) {
            }
        }

        int extractedFiles = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String name = entry.getName().replace('\\', '/');
            if (name.contains("..") || name.equals(PACK_MANIFEST)) {
                continue;
            }
            if (name.startsWith("mods/") || name.startsWith("resourcepacks/")
                    || name.startsWith("shaderpacks/") || name.startsWith("aetherglass/")
                    || name.startsWith("overrides/")) {
                String rel = name.startsWith("overrides/") ? name.substring("overrides/".length()) : name;
                if (rel.isEmpty() || rel.contains("..")) {
                    continue;
                }
                Path out = paths.base().resolve(rel).normalize();
                if (!out.startsWith(paths.base())) {
                    continue;
                }
                if (out.getParent() != null) {
                    Files.createDirectories(out.getParent());
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    extractedFiles++;
                }
            }
        }

        ModpackProfile created = config.addOrUpdateModpack(packName, mcVer, loader, aether, optimize);
        config.save(paths.launcherConfig());
        return "Импортирована сборка «" + created.name() + "» (файлов распаковано: " + extractedFiles + ")";
    }

    private static void addDirectoryFiles(ZipOutputStream zos, Path dir, String prefix, String ext) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var stream = Files.list(dir)) {
            for (Path p : stream.toList()) {
                if (Files.isRegularFile(p)
                        && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ext)) {
                    zos.putNextEntry(new ZipEntry(prefix + p.getFileName().toString()));
                    Files.copy(p, zos);
                    zos.closeEntry();
                }
            }
        }
    }

    private static void addTree(ZipOutputStream zos, Path rootDir, String zipPrefix) throws IOException {
        if (!Files.isDirectory(rootDir)) {
            return;
        }
        try (var walk = Files.walk(rootDir)) {
            for (Path p : walk.toList()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String rel = rootDir.relativize(p).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(zipPrefix + rel));
                Files.copy(p, zos);
                zos.closeEntry();
            }
        }
    }
}
