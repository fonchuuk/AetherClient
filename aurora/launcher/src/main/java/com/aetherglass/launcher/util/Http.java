package com.aetherglass.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;

/** Обёртка над {@link HttpClient}: запросы JSON и загрузка файлов с прогрессом и отменой. */
public final class Http {
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "AetherGlassLauncher/1.0.8 (github.com/fonchuuk/AetherClient)";
    private static volatile HttpClient client;

    private Http() {
    }

    public static HttpClient client() {
        HttpClient local = client;
        if (local == null) {
            synchronized (Http.class) {
                local = client;
                if (local == null) {
                    local = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(CONNECT_TIMEOUT)
                            .build();
                    client = local;
                }
            }
        }
        return local;
    }

    /** Выполняет GET и разбирает ответ как JSON. */
    public static Object getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("сервер вернул код " + response.statusCode() + " на " + url);
        }
        return Json.parse(response.body());
    }

    /** Выполняет POST с телом JSON и возвращает ответ как JSON (или null при пустом теле). */
    public static Object postJson(String url, Map<String, Object> body, Map<String, String> headers)
            throws IOException, InterruptedException {
        String payload = Json.write(body);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.body() == null || response.body().isBlank()) {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("сервер вернул код " + response.statusCode() + " на " + url);
            }
            return null;
        }
        return Json.parse(response.body());
    }

    /** Выполняет POST с формой {@code application/x-www-form-urlencoded} (используется в OAuth). */
    public static Map<String, Object> postForm(String url, String form) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.body() == null || response.body().isBlank()) {
            throw new IOException("пустой ответ от " + url + " (код " + response.statusCode() + ")");
        }
        return Json.asObject(Json.parse(response.body()));
    }

    /**
     * Скачивает файл с проверкой контрольной суммы.
     *
     * @param expectedSha1 ожидаемая сумма SHA-1 (может быть null — тогда проверка не выполняется)
     * @param listener     получает прогресс; может быть null
     * @param cancelled    проверяется между блоками данных; может быть null
     */
    public static void download(String url, Path target, Long expectedSize, String expectedSha1,
                                ProgressListener listener, CancelFlag cancelled)
            throws IOException, InterruptedException {
        String name = target.getFileName().toString();
        if (Files.isRegularFile(target) && expectedSize != null
                && Files.size(target) == expectedSize && (expectedSha1 == null || verify(target, expectedSha1))) {
            if (listener != null) {
                listener.onFile(name, expectedSize, expectedSize);
            }
            return;
        }
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(target.getFileName() + ".part");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("не удалось скачать " + name + ": код " + response.statusCode());
        }
        long total = expectedSize != null ? expectedSize : response.headers().firstValueAsLong("content-length").orElse(-1L);
        long done = 0L;
        try (InputStream stream = response.body(); OutputStream output = Files.newOutputStream(temporary)) {
            byte[] buffer = new byte[65536];
            int read;
            long lastReport = 0L;
            while ((read = stream.read(buffer)) > 0) {
                if (cancelled != null && cancelled.isCancelled()) {
                    throw new CancelledException();
                }
                output.write(buffer, 0, read);
                done += read;
                // Сообщения отправляются не чаще 20 раз в секунду, чтобы не забивать очередь событий Swing.
                if (listener != null && done - lastReport > 262144L) {
                    lastReport = done;
                    listener.onFile(name, done, total);
                }
            }
        } catch (CancelledException exception) {
            Files.deleteIfExists(temporary);
            throw exception;
        }
        if (listener != null) {
            listener.onFile(name, done, total);
        }
        if (expectedSize != null && Files.size(temporary) != expectedSize) {
            Files.deleteIfExists(temporary);
            throw new IOException("неверный размер файла " + name + ": ожидался " + expectedSize
                    + ", получен " + Files.size(temporary));
        }
        if (expectedSha1 != null && !verify(temporary, expectedSha1)) {
            Files.deleteIfExists(temporary);
            throw new IOException("контрольная сумма файла " + name + " не совпадает с ожидаемой");
        }
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Проверяет SHA-1 файла без его полной загрузки в память. */
    public static boolean verify(Path file, String expectedSha1) throws IOException {
        String actual = sha1(file);
        return actual.equalsIgnoreCase(expectedSha1);
    }

    public static String sha1(Path file) throws IOException {
        return digest(file, "SHA-1");
    }

    public static String sha512(Path file) throws IOException {
        return digest(file, "SHA-512");
    }

    private static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            try (InputStream stream = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                int read;
                while ((read = stream.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder builder = new StringBuilder(digest.getDigestLength() * 2);
            for (byte value : digest.digest()) {
                builder.append(Character.forDigit((value >> 4) & 0xF, 16));
                builder.append(Character.forDigit(value & 0xF, 16));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("алгоритм " + algorithm + " недоступен в этой Java", exception);
        }
    }

    /** Прогресс загрузки: вызывается из рабочего потока, обновляет индикаторы в окне. */
    public interface ProgressListener {
        void onFile(String name, long done, long total);
    }

    /** Простой флаг отмены, который выставляет кнопка «Отмена». */
    public interface CancelFlag {
        boolean isCancelled();
    }

    /** Исключение, возникающее при отмене загрузки пользователем. */
    public static final class CancelledException extends IOException {
        private static final long serialVersionUID = 1L;

        public CancelledException() {
            super("загрузка отменена пользователем");
        }
    }
}
