package com.aetherglass.launcher.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Хранение токенов: файл переживает перезапуск, а прочитать его может только
 * владелец. Проверяется на настоящих файлах во временном каталоге.
 */
final class AuthStoreTest {

    @TempDir
    Path temp;

    private static AuthSession session() {
        return new AuthSession("Player", "11112222-3333-4444-5555-666677778888", "minecraft-token",
                "xuid-1", "client-id", System.currentTimeMillis() + 86_400_000L, "refresh-token");
    }

    @Test
    void savedSessionIsLoadedBack() throws IOException {
        AuthStore store = new AuthStore(temp.resolve("accounts").resolve("account.json"));
        store.save(session());

        AuthSession loaded = store.load();
        assertNotNull(loaded, "сохранённая сессия должна читаться");
        assertEquals("Player", loaded.playerName());
        assertEquals("11112222-3333-4444-5555-666677778888", loaded.uuid());
        assertEquals("minecraft-token", loaded.accessToken());
        assertEquals("refresh-token", loaded.refreshToken());
        assertEquals("client-id", loaded.clientId());
        assertEquals("xuid-1", loaded.xuid());
        assertTrue(loaded.expiresAtMillis() > System.currentTimeMillis());
    }

    @Test
    void tokenFileIsOwnerOnly() throws IOException {
        Path file = temp.resolve("account.json");
        new AuthStore(file).save(session());

        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
            assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE), permissions);
        } catch (UnsupportedOperationException exception) {
            // Windows: здесь права ограничивает icacls — проверяем саму команду.
            assertTrue(AuthStore.windowsAclCommand(file, "player").contains("/inheritance:r"));
        }
    }

    @Test
    void windowsCommandGrantsOnlyCurrentUser() {
        Path file = temp.resolve("account.json");
        assertEquals(java.util.List.of("icacls", file.toAbsolutePath().toString(),
                        "/inheritance:r", "/grant:r", "player:F"),
                AuthStore.windowsAclCommand(file, "player"));
        assertTrue(AuthStore.windowsAclCommand(file, "").get(4).endsWith(":F"), "без имени — подставляется %USERNAME%");
    }

    @Test
    void missingFileIsNotAnError() {
        assertNull(new AuthStore(temp.resolve("no-session-yet.json")).load(),
                "отсутствующий файл — это «не входили», а не ошибка");
    }

    @Test
    void brokenFileIsNotAnError() throws IOException {
        Path file = temp.resolve("account.json");
        Files.writeString(file, "{ сломанный json", StandardCharsets.UTF_8);
        assertNull(new AuthStore(file).load(), "испорченный файл не должен ронять лаунчер");
    }

    @Test
    void fileWithoutTokensIsIgnored() throws IOException {
        Path file = temp.resolve("account.json");
        Files.writeString(file, "{\"playerName\": \"Player\"}", StandardCharsets.UTF_8);
        assertNull(new AuthStore(file).load(), "без токена сессия недействительна");
    }

    @Test
    void clearRemovesFile() throws IOException {
        Path file = temp.resolve("account.json");
        AuthStore store = new AuthStore(file);
        store.save(session());
        store.clear();
        assertTrue(Files.notExists(file), "после выхода из аккаунта файл удаляется");
    }
}
