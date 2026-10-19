package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Поиск и установка модов ({@code project_type:mod}) и ресурспаков ({@code project_type:resourcepack})
 * из каталога Modrinth (API v2) в один клик.
 */
public final class ModrinthCatalog {
    public static final String API_BASE = "https://api.modrinth.com/v2";
    public static final String TYPE_MOD = "mod";
    public static final String TYPE_RESOURCEPACK = "resourcepack";

    private ModrinthCatalog() {
    }

    public record Hit(
            String projectId,
            String slug,
            String title,
            String description,
            String author,
            long downloads,
            String projectType
    ) {
    }

    public record SearchPage(List<Hit> hits, long totalHits, int offset) {
    }

    public record VersionFile(String url, String filename, long size, String sha1, String sha512) {
    }

    public static SearchPage search(String projectType, String query, String gameVersion,
                                    int limit, int offset) throws IOException, InterruptedException {
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        String ver = (gameVersion == null || gameVersion.isBlank()) ? "1.21.11" : gameVersion.trim();
        int safeLimit = Math.max(1, Math.min(50, limit));
        int safeOffset = Math.max(0, offset);

        String facetsJson;
        if (TYPE_RESOURCEPACK.equals(type)) {
            facetsJson = "[[\"project_type:resourcepack\"],[\"versions:" + ver + "\"]]";
        } else {
            facetsJson = "[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:" + ver + "\"]]";
        }
        String q = query == null ? "" : query.trim();
        String url = API_BASE + "/search?query=" + encode(q)
                + "&limit=" + safeLimit
                + "&offset=" + safeOffset
                + "&index=downloads"
                + "&facets=" + encode(facetsJson);
        Map<String, Object> root = Json.asObject(Http.getJson(url));
        long total = Json.longValue(root, "total_hits", 0L);
        Object rawHitsObj = root.get("hits");
        List<Object> rawHits = rawHitsObj instanceof List<?> ? Json.asArray(rawHitsObj) : List.of();
        List<Hit> hits = new ArrayList<>(rawHits.size());
        for (Object item : rawHits) {
            Map<String, Object> obj = Json.asObject(item);
            hits.add(new Hit(
                    Json.string(obj, "project_id", ""),
                    Json.string(obj, "slug", ""),
                    Json.string(obj, "title", Json.string(obj, "slug", "")),
                    Json.string(obj, "description", ""),
                    Json.string(obj, "author", ""),
                    Json.longValue(obj, "downloads", 0L),
                    Json.string(obj, "project_type", type)
            ));
        }
        return new SearchPage(List.copyOf(hits), total, safeOffset + hits.size());
    }

    public static VersionFile resolveLatestFile(String projectType, String projectId, String gameVersion)
            throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank()) {
            throw new IOException("не выбран проект Modrinth");
        }
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        String ver = (gameVersion == null || gameVersion.isBlank()) ? "1.21.11" : gameVersion.trim();
        String url;
        if (TYPE_RESOURCEPACK.equals(type)) {
            url = API_BASE + "/project/" + encode(projectId)
                    + "/version?game_versions=" + encode("[\"" + ver + "\"]");
        } else {
            url = API_BASE + "/project/" + encode(projectId)
                    + "/version?game_versions=" + encode("[\"" + ver + "\"]")
                    + "&loaders=" + encode("[\"fabric\"]");
        }
        Object parsed = Http.getJson(url);
        List<Object> versions = parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
        if (versions.isEmpty() && TYPE_RESOURCEPACK.equals(type)) {
            Object fallback = Http.getJson(API_BASE + "/project/" + encode(projectId) + "/version");
            if (fallback instanceof List<?> list) {
                versions = new ArrayList<>(list);
            }
        }
        VersionFile best = null;
        for (Object rawVer : versions) {
            Map<String, Object> vObj = Json.asObject(rawVer);
            String vType = Json.string(vObj, "version_type", "release");
            VersionFile file = extractPrimaryFile(vObj);
            if (file == null) {
                continue;
            }
            if (best == null) {
                best = file;
            }
            if ("release".equalsIgnoreCase(vType)) {
                return file;
            }
        }
        if (best == null) {
            throw new IOException("нет подходящего файла для Minecraft " + ver);
        }
        return best;
    }

    public static Path installOneClick(LauncherPaths paths, String projectType, String projectId,
                                       String gameVersion, Http.ProgressListener progress,
                                       Http.CancelFlag cancelled) throws IOException, InterruptedException {
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        VersionFile file = resolveLatestFile(type, projectId, gameVersion);
        String safeName = sanitizeFilename(type, file.filename());
        if (safeName.isEmpty()) {
            throw new IOException("недопустимое имя файла: " + file.filename());
        }
        Path dir = TYPE_RESOURCEPACK.equals(type) ? paths.resourcePacks() : paths.mods();
        Files.createDirectories(dir);
        Path target = dir.resolve(safeName);
        Http.download(file.url(), target, file.size() > 0 ? file.size() : null,
                file.sha1().isEmpty() ? null : file.sha1(), progress, cancelled);
        return target;
    }

    public static String sanitizeFilename(String projectType, String rawName) {
        if (rawName == null) {
            return "";
        }
        String normalized = rawName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (base.isEmpty() || ".".equals(base) || "..".equals(base)) {
            return "";
        }
        String lower = base.toLowerCase(Locale.ROOT);
        if (TYPE_RESOURCEPACK.equalsIgnoreCase(projectType)) {
            return (lower.endsWith(".zip") || lower.endsWith(".jar")) ? base : "";
        }
        return lower.endsWith(".jar") ? base : "";
    }

    private static VersionFile extractPrimaryFile(Map<String, Object> versionObj) {
        Object rawFiles = versionObj.get("files");
        List<Object> files = rawFiles instanceof List<?> ? Json.asArray(rawFiles) : List.of();
        if (files.isEmpty()) {
            return null;
        }
        Map<String, Object> chosen = null;
        for (Object item : files) {
            Map<String, Object> fObj = Json.asObject(item);
            if (Boolean.TRUE.equals(fObj.get("primary"))) {
                chosen = fObj;
                break;
            }
            if (chosen == null) {
                chosen = fObj;
            }
        }
        if (chosen == null) {
            return null;
        }
        String url = Json.string(chosen, "url", "");
        String filename = Json.string(chosen, "filename", "");
        if (url.isEmpty() || filename.isEmpty()) {
            return null;
        }
        long size = Json.longValue(chosen, "size", 0L);
        Object rawHashes = chosen.get("hashes");
        Map<String, Object> hashes = rawHashes instanceof Map<?, ?> ? Json.asObject(rawHashes) : null;
        String sha1 = hashes == null ? "" : Json.string(hashes, "sha1", "");
        String sha512 = hashes == null ? "" : Json.string(hashes, "sha512", "");
        return new VersionFile(url, filename, size, sha1, sha512);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
