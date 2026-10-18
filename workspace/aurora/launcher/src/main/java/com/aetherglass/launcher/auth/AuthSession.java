package com.aetherglass.launcher.auth;

import java.util.Objects;

/**
 * Результат входа: данные, которые нужны игре для авторизации.
 *
 * <p>Пароли не используются и не хранятся нигде: весь поток идёт через браузер
 * (Microsoft OAuth) и обмен токенами.</p>
 */
public final class AuthSession {
    private final String playerName;
    private final String uuid;
    private final String accessToken;
    private final String xuid;
    private final String clientId;
    private final long expiresAtMillis;
    private final String refreshToken;

    public AuthSession(String playerName, String uuid, String accessToken, String xuid, String clientId,
                       long expiresAtMillis, String refreshToken) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.xuid = xuid;
        this.clientId = clientId;
        this.expiresAtMillis = expiresAtMillis;
        this.refreshToken = refreshToken;
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

    /** Срок действия токена Minecraft (миллисекунды с эпохи). */
    public long expiresAtMillis() {
        return this.expiresAtMillis;
    }

    /** Истинно, если токен есть и до его окончания больше пяти минут. */
    public boolean valid() {
        return this.playerName != null && !this.playerName.isBlank()
                && this.uuid != null && !this.uuid.isBlank()
                && this.accessToken != null && !this.accessToken.isBlank()
                && this.expiresAtMillis > System.currentTimeMillis() + 300_000L;
    }

    /** True, если токен уже истёк или истекает в ближайшие пять минут. */
    public boolean needsRefresh() {
        return !this.valid();
    }

    public AuthSession withToken(String newAccessToken, long newExpiry, String newRefreshToken, String newXuid) {
        return new AuthSession(this.playerName, this.uuid, newAccessToken,
                newXuid == null ? this.xuid : newXuid, this.clientId, newExpiry,
                newRefreshToken == null ? this.refreshToken : newRefreshToken);
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
