package com.aetherglass.client.account;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.mixin.MinecraftUserAccessor;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Менеджер оффлайн-аккаунтов (Блок 2.3): позволяет мгновенно менять игровой ник
 * и профиль {@link User} прямо в игре (через меню Right Shift) без перезапуска клиента
 * с сохранением списка аккаунтов в {@link ModConfig}.
 */
public final class AccountManager {
    private static final String KEY_ACTIVE = "accounts.active";
    private static final String KEY_LIST = "accounts.list";
    private static final int MAX_ACCOUNTS = 24;

    private static final String[] RANDOM_PREFIXES = {
            "Aether", "Lunar", "Frost", "Crystal", "Nebula", "Aurora", "Velvet", "Zephyr", "Prism", "Glacier"
    };
    private static final String[] RANDOM_SUFFIXES = {
            "Glass", "Ray", "Wave", "Star", "Fox", "Knight", "Pulse", "Mist", "Shard", "lynx"
    };

    private static final AccountManager INSTANCE = new AccountManager();
    private final List<String> accounts = new ArrayList<>();
    private final Random random = new Random();
    private String activeUsername = "";

    private AccountManager() {
    }

    public static AccountManager get() {
        return INSTANCE;
    }

    /** Загружает сохранённые аккаунты из конфига при старте клиента. */
    public void loadFromConfig() {
        this.accounts.clear();
        ModConfig cfg = ModConfig.get();
        String rawList = cfg.getString(KEY_LIST, "");
        if (!rawList.isBlank()) {
            for (String part : rawList.split(",")) {
                String clean = sanitizeUsername(part);
                if (isValidUsername(clean) && !containsIgnoreCase(clean)) {
                    this.accounts.add(clean);
                }
            }
        }
        String savedActive = sanitizeUsername(cfg.getString(KEY_ACTIVE, ""));
        if (isValidUsername(savedActive)) {
            this.activeUsername = savedActive;
            if (!containsIgnoreCase(savedActive)) {
                this.accounts.add(0, savedActive);
            }
            applyToMinecraft(savedActive);
        } else {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getUser() != null) {
                    String current = sanitizeUsername(mc.getUser().getName());
                    if (isValidUsername(current)) {
                        this.activeUsername = current;
                        if (!containsIgnoreCase(current)) {
                            this.accounts.add(0, current);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /** Сохраняет список аккаунтов и активный ник в {@link ModConfig}. */
    public void saveToConfig() {
        ModConfig cfg = ModConfig.get();
        cfg.set(KEY_ACTIVE, this.activeUsername != null ? this.activeUsername : "");
        cfg.set(KEY_LIST, String.join(",", this.accounts));
        cfg.save();
    }

    /**
     * Мгновенно переключает оффлайн-аккаунт в запущенном клиенте без перезапуска,
     * добавляет его в список сохранённых и записывает в конфиг.
     */
    public boolean switchAccount(String rawUsername) {
        String clean = sanitizeUsername(rawUsername);
        if (!isValidUsername(clean)) {
            return false;
        }
        this.activeUsername = clean;
        removeIgnoreCase(clean);
        this.accounts.add(0, clean);
        while (this.accounts.size() > MAX_ACCOUNTS) {
            this.accounts.remove(this.accounts.size() - 1);
        }
        applyToMinecraft(clean);
        saveToConfig();
        NotificationManager.get().push("Аккаунт изменён",
                "Активный ник: " + clean + " (Offline UUID)",
                NotificationManager.Type.ENABLED, 2.4F);
        return true;
    }

    /** Удаляет аккаунт из сохранённого списка. */
    public void removeAccount(String username) {
        if (username == null) {
            return;
        }
        removeIgnoreCase(username.trim());
        saveToConfig();
    }

    /** Генерирует красивый случайный ник и сразу переключается на него. */
    public String switchRandomAccount() {
        String p = RANDOM_PREFIXES[this.random.nextInt(RANDOM_PREFIXES.length)];
        String s = RANDOM_SUFFIXES[this.random.nextInt(RANDOM_SUFFIXES.length)];
        int num = 10 + this.random.nextInt(89);
        String candidate = sanitizeUsername(p + s + num);
        switchAccount(candidate);
        return candidate;
    }

    public List<String> accounts() {
        return Collections.unmodifiableList(this.accounts);
    }

    public String currentUsername() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getUser() != null && mc.getUser().getName() != null) {
                return mc.getUser().getName();
            }
        } catch (Throwable ignored) {
        }
        return this.activeUsername.isEmpty() ? "Player" : this.activeUsername;
    }

    public static UUID offlineUuid(String username) {
        String clean = sanitizeUsername(username);
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + clean).getBytes(StandardCharsets.UTF_8));
    }

    public static String sanitizeUsername(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < raw.length() && sb.length() < 16; i++) {
            char c = raw.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isValidUsername(String username) {
        if (username == null || username.length() < 3 || username.length() > 16) {
            return false;
        }
        for (int i = 0; i < username.length(); i++) {
            char c = username.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private void applyToMinecraft(String username) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            UUID uuid = offlineUuid(username);
            User newUser = new User(username, uuid, "0", Optional.empty(), Optional.empty());
            if (mc instanceof MinecraftUserAccessor accessor) {
                accessor.aetherglass$setUser(newUser);
                accessor.aetherglass$setProfileFuture(java.util.concurrent.CompletableFuture.completedFuture(null));
                AuroraLog.info("Оффлайн-аккаунт переключён на " + username + " (" + uuid + ")");
            }
        } catch (Throwable t) {
            AuroraLog.error("Не удалось применить новый профиль User", t);
        }
    }

    private boolean containsIgnoreCase(String name) {
        for (String existing : this.accounts) {
            if (existing.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void removeIgnoreCase(String name) {
        this.accounts.removeIf(existing -> existing.equalsIgnoreCase(name));
    }
}
