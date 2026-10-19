package com.aetherglass.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/**
 * Результат входа: данные, которые нужны игре для авторизации (Microsoft OAuth или офлайн-профиль).
 */
public final class AuthSession {
    private final String playerName;
    private final String uuid;
    private final String accessToken;
    private final String xuid;
    private final String clientId;
    private final long expiresAtMillis;
    private final String refreshToken;
    private final boolean offline;

    public AuthSession(String playerName, String uuid, String accessToken, String xuid, String clientId,
                       long expiresAtMillis, String refreshToken) {
        this(playerName, uuid, accessToken, xuid, clientId, expiresAtMillis, refreshToken, false);
    }

    public AuthSession(String playerName, String uuid, String accessToken, String xuid, String clientId,
                       long expiresAtMillis, String refreshToken, boolean offline) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.xuid = xuid;
        this.clientId = clientId;
        this.expiresAtMillis = expiresAtMillis;
        this.refreshToken = refreshToken;
        this.offline = offline;
    }

    /** Создаёт офлайн-профиль (без лицензии Microsoft) с детерминированным UUID v3. */
    public static AuthSession offline(String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.length() < 3 || name.length() > 16 || !name.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException(
                    "Никнейм должен содержать от 3 до 16 символов (латиница, цифры и _)");
        }
        String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
        return new AuthSession(name, uuid, "0", "", "", Long.MAX_VALUE, "", true);
    }

    public String playerName() {
        return this.playerName;
    }

    public String uuid() {
        return this.uuid;
    }

    public String accessToken() {
        return this.accessToken;
    }

    public String xuid() {
        return this.xuid;
    }

    public String clientId() {
        return this.clientId;
    }

    public String refreshToken() {
        return this.refreshToken;
    }

    public boolean offline() {
        return this.offline;
    }

    /** Срок действия токена Minecraft (миллисекунды с эпохи). */
    public long expiresAtMillis() {
        return this.expiresAtMillis;
    }

    /** Истинно, если сессия действительна (офлайн-профиль или живой токен Microsoft). */
    public boolean valid() {
        if (this.playerName == null || this.playerName.isBlank()
                || this.uuid == null || this.uuid.isBlank()) {
            return false;
        }
        if (this.offline) {
            return true;
        }
        return this.accessToken != null && !this.accessToken.isBlank()
                && this.expiresAtMillis > System.currentTimeMillis() + 300_000L;
    }

    /** True, если токен Microsoft уже истёк или истекает в ближайшие пять минут. */
    public boolean needsRefresh() {
        return !this.offline && !this.valid();
    }

    public AuthSession withToken(String newAccessToken, long newExpiry, String newRefreshToken, String newXuid) {
        return new AuthSession(this.playerName, this.uuid, newAccessToken,
                newXuid == null ? this.xuid : newXuid, this.clientId, newExpiry,
                newRefreshToken == null ? this.refreshToken : newRefreshToken, this.offline);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthSession session)) {
            return false;
        }
        return Objects.equals(this.uuid, session.uuid) && Objects.equals(this.playerName, session.playerName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.uuid, this.playerName);
    }

    @Override
    public String toString() {
        return this.playerName + " (" + this.uuid + ")";
    }
}
