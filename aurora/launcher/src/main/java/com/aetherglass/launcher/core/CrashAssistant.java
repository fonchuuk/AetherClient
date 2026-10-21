package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Http;
import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Нейро-помощник: находит последний краш-репорт (или ошибку в {@code latest.log}),
 * отправляет выжимку в бесплатную модель и возвращает причину сбоя и способ починки.
 *
 * <p>Почему так, а не «поле для API-ключа»: пользователь не должен ничего
 * настраивать. По умолчанию используется открытый OpenAI-совместимый шлюз
 * {@code text.pollinations.ai}, которому ключ не нужен. Если сети нет или шлюз
 * ответил ошибкой, включается локальный разбор по правилам
 * ({@link #localDiagnosis}) — ответ будет, просто без «человеческого» текста от
 * модели. Ключ и адрес при желании переопределяются переменными окружения
 * {@code AETHERGLASS_AI_URL} / {@code AETHERGLASS_AI_KEY} / {@code AETHERGLASS_AI_MODEL}.</p>
 *
 * <p>В модель уходит только выжимка: описание краша, строки стека с причиной и
 * список имён модов. Полный лог (мегабайты) не отправляется — это и быстрее, и
 * приватнее.</p>
 */
public final class CrashAssistant {

    /** Шлюз по умолчанию: OpenAI-совместимый, работает без ключа. */
    private static final String DEFAULT_URL = "https://text.pollinations.ai/openai";
    private static final String DEFAULT_MODEL = "openai";
    /** Сколько символов выжимки максимум уходит в модель. */
    private static final int MAX_EXCERPT = 7000;
    private static final Duration AI_TIMEOUT = Duration.ofSeconds(75);

    private CrashAssistant() {
    }

    /** Найденное доказательство сбоя: файл, вид и подготовленная выжимка. */
    public record Evidence(Path file, String kind, String excerpt, long modifiedMillis) {
        public boolean found() {
            return this.file != null;
        }

        public String displayName() {
            return this.file == null ? "не найдено" : this.file.getFileName().toString();
        }
    }

    /** Готовый ответ: текст, источник и то, на чём он основан. */
    public record Answer(String text, String source, boolean fromAi, Evidence evidence) {
    }

    // ─────────────────────────── Поиск отчёта ───────────────────────────

    /**
     * Ищет самый свежий источник сбоя: сначала {@code crash-reports/*.txt},
     * затем {@code logs/latest.log} с признаками ошибки.
     */
    public static Evidence collect(LauncherPaths paths) {
        Evidence crash = latestCrashReport(paths.base().resolve("crash-reports"));
        Evidence log = latestLogError(paths.logsDir());
        if (crash.found() && (!log.found() || crash.modifiedMillis() >= log.modifiedMillis())) {
            return crash;
        }
        return log.found() ? log : crash;
    }

    private static Evidence latestCrashReport(Path crashDir) {
        if (!Files.isDirectory(crashDir)) {
            return new Evidence(null, "нет папки crash-reports", "", 0L);
        }
        try (Stream<Path> stream = Files.list(crashDir)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".txt"))
                    .sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed())
                    .limit(3)
                    .toList();
            for (Path file : files) {
                String text = readHead(file, 220_000);
                String excerpt = condenseCrashReport(text);
                if (!excerpt.isBlank()) {
                    return new Evidence(file, "crash-report", excerpt, file.toFile().lastModified());
                }
            }
        } catch (IOException ignored) {
        }
        return new Evidence(null, "crash-reports пуста", "", 0L);
    }

    private static Evidence latestLogError(Path logsDir) {
        Path latest = logsDir.resolve("latest.log");
        if (!Files.isRegularFile(latest)) {
            return new Evidence(null, "нет latest.log", "", 0L);
        }
        String text = readHead(latest, 900_000);
        String excerpt = condenseLog(text);
        if (excerpt.isBlank()) {
            // Ошибок нет: отдаём хвост лога, чтобы модель могла оценить контекст.
            String tail = text.length() > 4000 ? text.substring(text.length() - 4000) : text;
            return new Evidence(latest, "latest.log (без явных ошибок)", tail, latest.toFile().lastModified());
        }
        return new Evidence(latest, "latest.log", excerpt, latest.toFile().lastModified());
    }

    private static String readHead(Path file, int limit) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int length = Math.min(bytes.length, limit);
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        } catch (IOException error) {
            return "";
        }
    }

    /** Из crash-отчёта берём шапку, описание, стек и блок «-- System Details --». */
    static String condenseCrashReport(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("=== crash-report ===\n");
        builder.append(head(text, 60)).append('\n');

        int caused = text.lastIndexOf("Caused by:");
        if (caused > 0) {
            int end = Math.min(text.length(), caused + 1200);
            builder.append("--- причина (последняя Caused by) ---\n")
                    .append(text, caused, end).append('\n');
        }

        int stack = text.indexOf("\tat ");
        if (stack > 0) {
            int end = Math.min(text.length(), stack + 1800);
            builder.append("--- стек ---\n").append(text, stack, end).append('\n');
        }
        return cut(builder.toString(), MAX_EXCERPT);
    }

    /** Из лога — окна вокруг строк ERROR/Exception/FATAL, плюс шапка. */
    static String condenseLog(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String lower = line.toLowerCase(Locale.ROOT);
            boolean interesting = lower.contains("/error") || lower.contains("exception")
                    || lower.contains("fatal") || lower.contains("crash") || line.contains("\tat ");
            if (interesting) {
                for (int from = Math.max(0, i - 3); from <= Math.min(lines.length - 1, i + 6); from++) {
                    if (!kept.contains(lines[from])) {
                        kept.add(lines[from]);
                    }
                }
            }
            if (kept.size() > 220) {
                break;
            }
        }
        if (kept.isEmpty()) {
            return "";
        }
        return cut("=== latest.log ===\n" + String.join("\n", kept), MAX_EXCERPT);
    }

    private static String head(String text, int lines) {
        String[] split = text.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Math.min(lines, split.length); i++) {
            builder.append(split[i]).append('\n');
        }
        return builder.toString();
    }

    private static String cut(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "\n… (обрезано)";
    }

    // ─────────────────────────── Анализ ───────────────────────────

    /**
     * Главная точка входа: собирает доказательство, пробует нейросеть, при
     * неудаче возвращает локальный разбор. Исключений не бросает.
     */
    public static Answer analyze(LauncherPaths paths, LauncherConfig config) {
        Evidence evidence = collect(paths);
        String local = localDiagnosis(evidence, config, paths);
        if (!evidence.found()) {
            return new Answer(local, "локальный разбор", false, evidence);
        }
        String ai = askAi(evidence, config, paths, local);
        if (ai != null && !ai.isBlank()) {
            return new Answer(sanitize(ai), "нейросеть", true, evidence);
        }
        return new Answer(local, "локальный разбор (сеть недоступна)", false, evidence);
    }

    /** Список установленных модов — короткими именами, для контекста модели. */
    public static List<String> installedMods(LauncherPaths paths) {
        List<String> names = new ArrayList<>();
        Path mods = paths.mods();
        if (!Files.isDirectory(mods)) {
            return names;
        }
        try (Stream<Path> stream = Files.list(mods)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .limit(120)
                    .forEach(names::add);
        } catch (IOException ignored) {
        }
        return names;
    }

    /**
     * Локальные правила. Работают всегда — даже без интернета, поэтому ответ
     * есть в любом случае.
     */
    static String localDiagnosis(Evidence evidence, LauncherConfig config, LauncherPaths paths) {
        if (!evidence.found()) {
            return "Крашей не найдено: в crash-reports и logs/latest.log нет ни отчётов, ни ошибок.\n\n"
                    + "Если игра падала — запусти её ещё раз и вернись сюда: лаунчер сам подхватит свежий отчёт.";
        }
        String text = evidence.excerpt().toLowerCase(Locale.ROOT);
        String reason;
        String fix;
        if (text.contains("outofmemoryerror") || text.contains("out of memory") || text.contains("java heap space")) {
            reason = "Нехватка оперативной памяти: игра упёрлась в лимит -Xmx (сейчас "
                    + config.maxMemoryMb() + " МБ) и упала с OutOfMemoryError.";
            fix = "Подними лимит ОЗУ до 4096 МБ (сборка 100+ модов — 6144–8192 МБ) и перезапусти игру.";
        } else if (text.contains("unsupportedclassversionerror") || text.contains("class file version")) {
            reason = "Неподходящая версия Java: мод собран под другую версию (для 1.21.x нужна Java 21).";
            fix = "В «Настройки → Java» переключись на встроенную Java 21 и запусти игру заново.";
        } else if (text.contains("modresolutionexception") || text.contains("incompatible mod set")
                || text.contains("mixinapplyerror") || text.contains("mixinprepareerror")) {
            reason = "Конфликт модов или версий: загрузчик не смог собрать совместимый набор.";
            fix = "Отключи мод, который назван в стеке (переименуй в .jar.disabled), обнови его под версию игры и проверь зависимости Fabric API.";
        } else if (text.contains("noclassdeffounderror") || text.contains("classnotfoundexception")) {
            reason = "Не хватает класса: обычно это отсутствующая зависимость мода (например, Fabric API).";
            fix = "Поставь Fabric API и все обязательные зависимости указанного мода, затем перезапусти игру.";
        } else if (text.contains("accessviolation") || text.contains("opengl") || text.contains("glfw")
                || text.contains("wgpu") || text.contains("failed to create window")) {
            reason = "Сбой графики: драйвер OpenGL не смог создать контекст или буферы (часто после обновления драйвера).";
            fix = "Обнови драйвер видеокарты, отключи шейдеры/оптимизаторы рендера и запусти игру без них.";
        } else if (text.contains("corrupt") || text.contains("zip") || text.contains("invalid mod file")) {
            reason = "Повреждённый файл мода: архив не читается или докачался не полностью.";
            fix = "Нажми «Доктор крашей» — он найдёт битый .jar, удалит его и перекачает моды.";
        } else {
            reason = "В отчёте есть исключение, но оно не попало под известные шаблоны — нужен разбор моделью "
                    + "или глазами по стеку ниже.";
            fix = "Посмотри последнюю строку «Caused by» в отчёте: там обычно указан виновный мод или класс.";
        }
        return "Причина: " + reason + "\n\nЧто делать: " + fix
                + "\n\nИсточник: " + evidence.displayName() + " (" + evidence.kind() + ")"
                + " · лимит ОЗУ " + config.maxMemoryMb() + " МБ · модов: " + installedMods(paths).size();
    }

    // ─────────────────────────── Нейросеть ───────────────────────────

    /** Спрашивает модель. Возвращает {@code null}, если сеть/шлюз не ответили. */
    static String askAi(Evidence evidence, LauncherConfig config, LauncherPaths paths, String localHint) {
        String url = envOr("AETHERGLASS_AI_URL", DEFAULT_URL);
        String model = envOr("AETHERGLASS_AI_MODEL", DEFAULT_MODEL);
        String key = System.getenv("AETHERGLASS_AI_KEY");

        String prompt = """
                Ты диагностируешь краш модового Minecraft. Ниже — выжимка из отчёта.
                Ответь по-русски, кратко и по делу, без вступлений:
                1) «Причина:» — 1–2 предложения, назови виновника (мод/память/Java/драйвер).
                2) «Что делать:» — 2–4 конкретных шага.
                Не выдумывай модов, которых нет в списке. Если данных мало — так и скажи.
                """;
        StringBuilder user = new StringBuilder();
        user.append("Версия игры: ").append(config.selectedVersion()).append('\n');
        user.append("Лимит ОЗУ: ").append(config.maxMemoryMb()).append(" МБ\n");
        List<String> mods = installedMods(paths);
        if (!mods.isEmpty()) {
            user.append("Моды (").append(mods.size()).append("): ").append(String.join(", ", mods)).append('\n');
        }
        if (localHint != null && !localHint.isBlank()) {
            user.append("\nПодсказка локального разбора (можешь уточнить или опровергнуть):\n")
                    .append(cut(localHint, 800)).append('\n');
        }
        user.append("\nВыжимка отчёта:\n").append(evidence.excerpt());

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", user.toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompt),
                message));
        body.put("temperature", 0.2);
        body.put("max_tokens", 700);
        body.put("private", true);

        try {
            String payload = Json.write(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(AI_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "AetherGlassLauncher/1.1.2");
            if (key != null && !key.isBlank()) {
                builder.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> response = Http.client().send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return extractContent(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable ignored) {
            // Сеть, DNS, таймаут, кривой JSON — всё едино: отдаём локальный разбор.
            return null;
        }
    }

    /** Достаёт {@code choices[0].message.content} из OpenAI-совместимого ответа. */
    static String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = Json.asObject(Json.parse(responseBody));
            List<Object> choices = Json.asArray(root.get("choices"));
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> first = Json.asObject(choices.get(0));
            Map<String, Object> message = Json.asObject(first.get("message"));
            String content = Json.string(message, "content", "");
            return content == null || content.isBlank() ? null : content;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Модель любит markdown, а в блоке заключения обычный текст: убираем
     * звёздочки, решётки заголовков и тройные кавычки, чтобы не читалось как мусор.
     */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("```", "")
                .replace("`", "")
                .replace("**", "")
                .replace("__", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*]\\s+", "• ")
                .strip();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
