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
 * из каталога Modrinth (API v2) в один клик с мгновенным предзагруженным каталогом и резервным CDN.
 */
public final class ModrinthCatalog {
    public static final String API_BASE = "https://api.modrinth.com/v2";
    public static final String TYPE_MOD = "mod";
    public static final String TYPE_RESOURCEPACK = "resourcepack";

    private static final List<Hit> BUILTIN_MODS = List.of(
            new Hit("P7dR8mSH", "fabric-api", "Fabric API",
                    "Базовая библиотека API для модов Fabric (обязательна для большинства модов)",
                    "modmuss50", 284_303_510L, TYPE_MOD),
            new Hit("AANobbMI", "sodium", "Sodium",
                    "Мощная оптимизация рендеринга чанков, повышение FPS и снижение микрофризов",
                    "jellysquid3", 249_863_192L, TYPE_MOD),
            new Hit("YL57xq9U", "iris", "Iris Shaders",
                    "Современный загрузчик шейдеров для Fabric, полностью совместимый с Sodium",
                    "coderbot", 193_773_077L, TYPE_MOD),
            new Hit("NNAgCjsB", "entityculling", "EntityCulling",
                    "Отсечение невидимых сущностей и блок-энтити за стенами через трассировку",
                    "tr7zw", 186_595_626L, TYPE_MOD),
            new Hit("9s6osm5g", "cloth-config", "Cloth Config API",
                    "Библиотека экранов настроек для клиентских модов Fabric",
                    "shedaniel", 182_947_727L, TYPE_MOD),
            new Hit("gvQqBUqZ", "lithium", "Lithium",
                    "Оптимизация физики, ИИ мобов, тиков мира и логики без изменения ванильной механики",
                    "jellysquid3", 174_510_400L, TYPE_MOD),
            new Hit("uXXizFIs", "ferrite-core", "FerriteCore",
                    "Снижение потребления оперативной памяти (RAM) моделями и блок-стейтами",
                    "malte0811", 168_220_910L, TYPE_MOD),
            new Hit("mOgUt4GM", "modmenu", "Mod Menu",
                    "Удобное меню установленных модов с быстрым доступом к их настройкам",
                    "Prospector", 159_480_120L, TYPE_MOD),
            new Hit("5ZwdcRci", "immediatelyfast", "ImmediatelyFast",
                    "Ускорение отрисовки интерфейса (HUD, текста, сущностей, частиц)",
                    "RK_01", 118_390_500L, TYPE_MOD),
            new Hit("1IjD5062", "continuity", "Continuity",
                    "Соединённые текстуры стекла, песчаника и книжных полок (OptiFine CTM)",
                    "PepperCode1", 96_420_110L, TYPE_MOD),
            new Hit("yBW8D80W", "lambdynamiclights", "LambDynamicLights",
                    "Динамическое освещение от факелов и светящихся предметов в руке",
                    "LambdAurora", 92_150_800L, TYPE_MOD),
            new Hit("EsAfCjCV", "appleskin", "AppleSkin",
                    "Отображение насыщения и восстановления голода от еды на панели HUD",
                    "squeek502", 89_310_450L, TYPE_MOD),
            new Hit("w7ThoJFB", "zoomify", "Zoomify",
                    "Плавное приближение камеры (зум) с настраиваемой анимацией и колесом мыши",
                    "isXander", 84_600_200L, TYPE_MOD),
            new Hit("PtjYWJkn", "sodium-extra", "Sodium Extra",
                    "Дополнительные настройки графики, анимаций, частиц и FPS для Sodium",
                    "FlashyReese", 81_205_900L, TYPE_MOD),
            new Hit("Bh37bMuy", "reeses-sodium-options", "Reese's Sodium Options",
                    "Удобное вертикальное меню настроек графики Sodium с поиском",
                    "FlashyReese", 79_880_300L, TYPE_MOD),
            new Hit("LQ3K71Q1", "dynamic-fps", "Dynamic FPS",
                    "Снижение нагрузки на видеокарту и процессор, когда окно игры свёрнуто",
                    "juliand665", 76_410_000L, TYPE_MOD),
            new Hit("1bokaNcj", "xaeros-minimap", "Xaero's Minimap",
                    "Быстрая и настраиваемая миникарта с метками (вейпоинтами)",
                    "xaero96", 71_900_000L, TYPE_MOD),
            new Hit("NcUtCpym", "xaeros-world-map", "Xaero's World Map",
                    "Полноэкранная карта исследованного мира с поддержкой миникарты Xaero",
                    "xaero96", 68_300_000L, TYPE_MOD),
            new Hit("9eGKb6K1", "voicechat", "Simple Voice Chat",
                    "Голосовой чат по близости в игре с поддержкой групп и 3D-звука",
                    "henkelmax", 65_120_000L, TYPE_MOD),
            new Hit("zV5r3pPn", "3dskinlayers", "3D Skin Layers",
                    "Объёмный 3D-рендер второго слоя скина игрока и голов",
                    "tr7zw", 61_800_000L, TYPE_MOD),
            new Hit("VSNURh3q", "c2me-fabric", "Concurrent Chunk Management Engine (C2ME)",
                    "Многопоточная генерация и загрузка чанков для ускорения мира",
                    "ishland", 54_200_000L, TYPE_MOD),
            new Hit("fQEb0iXm", "krypton", "Krypton",
                    "Оптимизация сетевого стека Minecraft (Netty, сжатие пакетов и пинг)",
                    "astei", 49_700_000L, TYPE_MOD),
            new Hit("g96Z4WVZ", "badoptimizations", "BadOptimizations",
                    "Микрооптимизации рендера неба, света и кеширования данных кадра",
                    "imjustnao", 44_500_000L, TYPE_MOD),
            new Hit("Ps1zyz6x", "scalablelux", "ScalableLux",
                    "Многопоточный движок пересчёта освещения (на базе Starlight)",
                    "spottedleaf", 38_900_000L, TYPE_MOD)
    );

    private static final List<Hit> BUILTIN_RESOURCEPACKS = List.of(
            new Hit("AANobbMI_rp1", "fresh-animations", "Fresh Animations",
                    "Плавные и живые анимации мобов и существ для современного Minecraft",
                    "FreshLX", 42_800_000L, TYPE_RESOURCEPACK),
            new Hit("w0TnApzs", "faithful-32x", "Faithful 32x",
                    "Классические текстуры Minecraft в удвоенном разрешении 32×32",
                    "Faithful_Pack", 31_400_000L, TYPE_RESOURCEPACK),
            new Hit("42X2v2I2", "faithful-64x", "Faithful 64x",
                    "Детализированные текстуры в ванильном стиле с разрешением 64×64",
                    "Faithful_Pack", 24_900_000L, TYPE_RESOURCEPACK),
            new Hit("50dA9Sha", "fullbright-ub", "Fullbright UB",
                    "Максимальная яркость в пещерах и ночью без потери атмосферы",
                    "ims", 19_600_000L, TYPE_RESOURCEPACK),
            new Hit("tN4E9NfV", "dark-mode-everywhere", "Default Dark Mode",
                    "Тёмное оформление всего интерфейса, инвентаря и контейнеров",
                    "nebuIr", 17_850_000L, TYPE_RESOURCEPACK),
            new Hit("2RKFTmiB", "low-on-fire", "Low On Fire (PvP)",
                    "Уменьшенный огонь на экране для отличного обзора в PvP",
                    "Haikis", 15_300_000L, TYPE_RESOURCEPACK),
            new Hit("H8CaAYZC", "stay-true", "Stay True",
                    "Улучшенная вариативность листвы, травы и блоков в ванильном стиле",
                    "haimcyfly", 14_100_000L, TYPE_RESOURCEPACK),
            new Hit("6KrNtW32", "vanilla-tweaks", "Vanilla Tweaks Pack",
                    "Чистые стекла, укороченные мечи, индикаторы роста и удобный прицел",
                    "VanillaTweaks", 12_500_000L, TYPE_RESOURCEPACK),
            new Hit("b93awgkg", "bare-bones", "Bare Bones",
                    "Минималистичные яркие текстуры в стиле официальных трейлеров Minecraft",
                    "RobotPantaloons", 11_900_000L, TYPE_RESOURCEPACK),
            new Hit("nfn13YXA", "reimagined", "Minecraft Reimagined",
                    "Обновлённые модели блоков, руд и факелов с сохранением духа игры",
                    "Reijvi", 9_400_000L, TYPE_RESOURCEPACK)
    );

    private static final Map<String, VersionFile> FALLBACK_FILES = Map.ofEntries(
            Map.entry("P7dR8mSH", new VersionFile(
                    "https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar",
                    "fabric-api-0.141.6+1.21.11.jar", 2426039L,
                    "c98467cbbaf4d197377266795ae015f4130d65b6",
                    "852d3682c4f353fd0ca86546af8578513e8bcf628377fa9d49c556bc59c5024c69ab56c92fc6dfba11fed19e4316a3f5636e7e07fd3d96f7043b531aef59b637")),
            Map.entry("AANobbMI", new VersionFile(
                    "https://cdn.modrinth.com/data/AANobbMI/versions/rkdTcxoT/sodium-fabric-0.8.14%2Bmc1.21.11.jar",
                    "sodium-fabric-0.8.14+mc1.21.11.jar", 1909150L,
                    "4a8733fe544168068d12fa94b2ab03e6c0c7bf91",
                    "04c43f9e8534b87a52c42ffd51b0e344d4ef92dc9cc52da33d13af6a18bf74b05380d2027f1e0db982698d0aff274c42741c1e21b313b0cd75ed3af005fc98d9")),
            Map.entry("gvQqBUqZ", new VersionFile(
                    "https://cdn.modrinth.com/data/gvQqBUqZ/versions/Ow7wA0kG/lithium-fabric-0.21.4%2Bmc1.21.11.jar",
                    "lithium-fabric-0.21.4+mc1.21.11.jar", 900462L,
                    "203bdcb26e97b3217b045e1182651a7d7b6462ec",
                    "f14a5c3d2fad786347ca25083f902139694f618b7c103947f2fd067a7c5ee88a63e1ef8926f7d693ea79ed7d00f57317bae77ef9c2d630bf5ed01ac97a752b94")),
            Map.entry("5ZwdcRci", new VersionFile(
                    "https://cdn.modrinth.com/data/5ZwdcRci/versions/4EwhsTu7/ImmediatelyFast-Fabric-1.14.3%2B1.21.11.jar",
                    "ImmediatelyFast-Fabric-1.14.3+1.21.11.jar", 302709L,
                    "43e451464f979bdf9ff4ac288b358a2f91b79ed8",
                    "2564cb28259352c3aa5759aceca62667de08a7860a40910dad40a043af851a074b2d75cef50b9fa6f1971febb3a5ecebe4067cbd4b60e63ef711b85f4bc21407")),
            Map.entry("NNAgCjsB", new VersionFile(
                    "https://cdn.modrinth.com/data/NNAgCjsB/versions/CTabrwB1/entityculling-fabric-1.11.3-mc1.21.11.jar",
                    "entityculling-fabric-1.11.3-mc1.21.11.jar", 1529094L,
                    "ce2e1cf7e343a625d184c80ccea582d468686a2e",
                    "4b5516bf8718d74db0e0368ad2fd2f5ce3e986c3abbf65426418ce3ec162651d29579b9be8aa0d34b88f47e310f6b7bab56bde7ff1223c35998bff0bcbed58c5")),
            Map.entry("uXXizFIs", new VersionFile(
                    "https://cdn.modrinth.com/data/uXXizFIs/versions/Ii0gP3D8/ferritecore-8.2.0-fabric.jar",
                    "ferritecore-8.2.0-fabric.jar", 80138L,
                    "c2e2f5e008f5bc9f401dfad8ca94909917006b65",
                    "3210926a82eb32efd9bcebabe2f6c053daf5c4337eebc6d5bacba96d283510afbde646e7e195751de795ec70a2ea44fef77cb54bf22c8e57bb832d6217418869")),
            Map.entry("g96Z4WVZ", new VersionFile(
                    "https://cdn.modrinth.com/data/g96Z4WVZ/versions/Q3Dusz2j/BadOptimizations-2.4.1-1.21.11.jar",
                    "BadOptimizations-2.4.1-1.21.11.jar", 269545L,
                    "70fa28d5a7a1fdd61dd804ca6ac411750da1473f",
                    "ff91f1866f21a13a32d379080b8efb5d4e07577591228c1d307b34d052696c9bdcbaf7706208dfbac56abc2c29482a15edcbf11f69fc0a01e459c4c20a5c2c28")),
            Map.entry("Ps1zyz6x", new VersionFile(
                    "https://cdn.modrinth.com/data/Ps1zyz6x/versions/PV9KcrYQ/ScalableLux-0.1.6%2Bfabric.c25518a-all.jar",
                    "ScalableLux-0.1.6+fabric.c25518a-all.jar", 182846L,
                    "9df93ab6442fa374c17fab6b5181fe2c215d9d5b",
                    "729515c1e75cf8d9cd704f12b3487ddb9664cf9928e7b85b12289c8fbbc7ed82d0211e1851375cbd5b385820b4fedbc3f617038fff5e30b302047b0937042ae7")),
            Map.entry("VSNURh3q", new VersionFile(
                    "https://cdn.modrinth.com/data/VSNURh3q/versions/olrVZpJd/c2me-fabric-mc1.21.11-0.3.6.0.0.jar",
                    "c2me-fabric-mc1.21.11-0.3.6.0.0.jar", 4687147L,
                    "587f9a3d9e4591e8e1a1c3eda61ed2f7bfa5dc46",
                    "c9b11100572fb71c3080ff11b011467624e8013b9942aade09a5c77eb62b3289667bad70501ddea8f35deb0a5d26884b79f76d4ed112d32342471ca7384b788a")),
            Map.entry("fQEb0iXm", new VersionFile(
                    "https://cdn.modrinth.com/data/fQEb0iXm/versions/O9LmWYR7/krypton-0.2.10.jar",
                    "krypton-0.2.10.jar", 270195L,
                    "94ddaeeae6ca7fecc79e54a0aa02a5438a775a99",
                    "4dcd7228d1890ddfc78c99ff284b45f9cf40aae77ef6359308e26d06fa0d938365255696af4cc12d524c46c4886cdcd19268c165a2bf0a2835202fe857da5cab")),
            Map.entry("LQ3K71Q1", new VersionFile(
                    "https://cdn.modrinth.com/data/LQ3K71Q1/versions/Fab7e5Th/dynamic-fps-3.11.6%2Bminecraft-1.21.11-fabric.jar",
                    "dynamic-fps-3.11.6+minecraft-1.21.11-fabric.jar", 219961L,
                    "2e5ab45e69b43812491cfa34f8f7a4ce7623c233",
                    "c45652252e2176cb8e2c44dfd9032c617135a100dcf161b9e6e6717803c14d1359d0f940fa10dc1aa6ad80669983f4b9c5636c0c2953d3314e06f893f0cdc736"))
    );

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

    /**
     * Форматирует число скачиваний для таблицы (например: 284.3M, 950K).
     */
    public static String formatDownloads(long count) {
        if (count >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0);
        }
        if (count >= 1_000L) {
            return String.format(Locale.ROOT, "%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }

    /**
     * Мгновенная выдача встроенного каталога (работает за 0 мс при открытии вкладки и при недоступности API).
     */
    public static SearchPage builtinPage(String projectType, String query, int offset) {
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        List<Hit> source = TYPE_RESOURCEPACK.equals(type) ? BUILTIN_RESOURCEPACKS : BUILTIN_MODS;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Hit> filtered = new ArrayList<>();
        for (Hit hit : source) {
            if (q.isEmpty()
                    || hit.title().toLowerCase(Locale.ROOT).contains(q)
                    || hit.slug().toLowerCase(Locale.ROOT).contains(q)
                    || hit.description().toLowerCase(Locale.ROOT).contains(q)
                    || hit.author().toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(hit);
            }
        }
        int safeOffset = Math.max(0, Math.min(offset, filtered.size()));
        List<Hit> slice = filtered.subList(safeOffset, filtered.size());
        return new SearchPage(List.copyOf(slice), filtered.size(), safeOffset + slice.size());
    }

    public static SearchPage search(String projectType, String query, String gameVersion,
                                    int limit, int offset) throws IOException, InterruptedException {
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        String ver = (gameVersion == null || gameVersion.isBlank()) ? "1.21.11" : gameVersion.trim();
        int safeLimit = Math.max(1, Math.min(50, limit));
        int safeOffset = Math.max(0, offset);
        String q = query == null ? "" : query.trim();

        try {
            String facetsWithVer = TYPE_RESOURCEPACK.equals(type)
                    ? "[[\"project_type:resourcepack\"],[\"versions:" + ver + "\"]]"
                    : "[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:" + ver + "\"]]";
            SearchPage page = fetchSearchPage(type, q, safeLimit, safeOffset, facetsWithVer);
            if (page.hits().isEmpty() && safeOffset == 0) {
                // Если под конкретную подверсию 1.21.11 фильтр пуст, ищем по всем версиям Fabric/Resourcepack
                String relaxedFacets = TYPE_RESOURCEPACK.equals(type)
                        ? "[[\"project_type:resourcepack\"]]"
                        : "[[\"project_type:mod\"],[\"categories:fabric\"]]";
                page = fetchSearchPage(type, q, safeLimit, safeOffset, relaxedFacets);
            }
            if (!page.hits().isEmpty()) {
                return page;
            }
        } catch (IOException exception) {
            // Если api.modrinth.com заблокирован провайдером или недоступен, отдаём встроенный каталог
            SearchPage fallback = builtinPage(type, q, safeOffset);
            if (!fallback.hits().isEmpty()) {
                return fallback;
            }
            throw exception;
        }
        return builtinPage(type, q, safeOffset);
    }

    private static SearchPage fetchSearchPage(String type, String q, int limit, int offset, String facetsJson)
            throws IOException, InterruptedException {
        String url = API_BASE + "/search?query=" + encode(q)
                + "&limit=" + limit
                + "&offset=" + offset
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
        return new SearchPage(List.copyOf(hits), total, offset + hits.size());
    }

    public static VersionFile resolveLatestFile(String projectType, String projectId, String gameVersion)
            throws IOException, InterruptedException {
        if (projectId == null || projectId.isBlank()) {
            throw new IOException("не выбран проект Modrinth");
        }
        String type = TYPE_RESOURCEPACK.equalsIgnoreCase(projectType) ? TYPE_RESOURCEPACK : TYPE_MOD;
        String ver = (gameVersion == null || gameVersion.isBlank()) ? "1.21.11" : gameVersion.trim();

        // Если это встроенный мок-идентификатор (например AANobbMI_rp1), ищем по slug
        String lookupId = projectId.endsWith("_rp1") ? "fresh-animations" : projectId;

        try {
            String url;
            if (TYPE_RESOURCEPACK.equals(type)) {
                url = API_BASE + "/project/" + encode(lookupId)
                        + "/version?game_versions=" + encode("[\"" + ver + "\"]");
            } else {
                url = API_BASE + "/project/" + encode(lookupId)
                        + "/version?game_versions=" + encode("[\"" + ver + "\"]")
                        + "&loaders=" + encode("[\"fabric\"]");
            }
            Object parsed = Http.getJson(url);
            List<Object> versions = parsed instanceof List<?> list ? new ArrayList<>(list) : List.of();
            if (versions.isEmpty()) {
                // Запасной запрос: последняя доступная версия под Fabric (для модов) или любая версия (для ресурспаков)
                String fallbackUrl = TYPE_RESOURCEPACK.equals(type)
                        ? API_BASE + "/project/" + encode(lookupId) + "/version"
                        : API_BASE + "/project/" + encode(lookupId) + "/version?loaders=" + encode("[\"fabric\"]");
                Object fallback = Http.getJson(fallbackUrl);
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
            if (best != null) {
                return best;
            }
        } catch (IOException exception) {
            VersionFile direct = FALLBACK_FILES.get(lookupId);
            if (direct != null) {
                return direct;
            }
            throw exception;
        }
        VersionFile direct = FALLBACK_FILES.get(lookupId);
        if (direct != null) {
            return direct;
        }
        throw new IOException("нет подходящего файла для Minecraft " + ver);
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
