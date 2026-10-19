package com.aetherglass.launcher.auth;

import com.aetherglass.launcher.util.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Хранилище токенов на диске.
 *
 * <p>Сохраняется только то, что выдал сервер: имя профиля, UUID, токен доступа Minecraft,
 * срок действия и токен обновления Microsoft. Пароль пользователя не фигурирует в потоке
 * вообще, поэтому хранить его не нужно и негде.</p>
 *
 * <p>В POSIX-системах на файл выставляются права 600 (только владелец).</p>
 */
public final class AuthStore {
    private final Path file;

    public AuthStore(Path file) {
        this.file = file;
    }

    public Path file() {
        return this.file;
    }

    public AuthSession load() {
        if (!Files.isRegularFile(this.file)) {
            return null;
        }
        try {
            Map<String, Object> data = Json.asObject(Json.parse(Files.readString(this.file, StandardCharsets.UTF_8)));
            String name = Json.string(data, "playerName", null);
            String uuid = Json.string(data, "uuid", null);
            boolean offline = Boolean.TRUE.equals(data.get("offline"));
            String token = Json.string(data, "accessToken", offline ? "0" : null);
            if (name == null || uuid == null || token == null) {
                return null;
            }
            return new AuthSession(name, uuid, token,
                    Json.string(data, "xuid", null),
                    Json.string(data, "clientId", null),
                    Json.longValue(data, "expiresAtMillis", offline ? Long.MAX_VALUE : 0L),
                    Json.string(data, "refreshToken", null),
                    offline);
        } catch (IOException | RuntimeException exception) {
            // Повреждённый файл — пользователь просто входит заново.
            return null;
        }
    }

    public void save(AuthSession session) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("playerName", session.playerName());
        data.put("uuid", session.uuid());
        data.put("accessToken", session.accessToken() == null ? "0" : session.accessToken());
        data.put("xuid", session.xuid() == null ? "" : session.xuid());
        data.put("clientId", session.clientId() == null ? "" : session.clientId());
        data.put("expiresAtMillis", session.expiresAtMillis());
        data.put("refreshToken", session.refreshToken() == null ? "" : session.refreshToken());
        data.put("offline", session.offline());
        Files.createDirectories(this.file.getParent());
        Files.writeString(this.file, Json.writePretty(data), StandardCharsets.UTF_8);
        restrictPermissions();
    }

    public void clear() {
        try {
            Files.deleteIfExists(this.file);
        } catch (IOException exception) {
            // Удалить не удалось — не критично, при следующем входе файл перезапишется.
        }
    }

    /**
     * Ограничивает доступ к файлу с токенами: читать и писать его должен
     * только сам пользователь.
     *
     * <p>На Linux и macOS для этого хватает прав 600. На Windows прав POSIX
     * нет, поэтому наследование отключается и полный доступ выдаётся одному
     * текущему пользователю — штатной командой {@code icacls}.</p>
     */
    private void restrictPermissions() {
        try {
            Files.setPosixFilePermissions(this.file,
                    Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException exception) {
            // Файловая система без POSIX-прав: значит, это Windows.
            restrictOnWindows();
        }
    }

    private void restrictOnWindows() {
        if (!isWindows()) {
            return;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(
                    windowsAclCommand(this.file, System.getProperty("user.name", "")));
            builder.redirectErrorStream(true);
            Process process = builder.start();
            process.getInputStream().readAllBytes();
            process.waitFor();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            // Не получилось ограничить права — токен всё равно сохранён,
            // а при следующем входе попытка повторится.
        }
    }

    /** Команда ограничения прав для Windows. Вынесена отдельно, чтобы её можно было проверить тестом. */
    static List<String> windowsAclCommand(Path file, String user) {
        String account = user == null || user.isBlank() ? "%USERNAME%" : user;
        return List.of("icacls", file.toAbsolutePath().toString(), "/inheritance:r", "/grant:r", account + ":F");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
