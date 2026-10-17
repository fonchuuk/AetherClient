package com.aetherglass.launcher.core;

import com.aetherglass.launcher.util.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Вычисление правил из JSON версии Mojang и профиля Fabric.
 *
 * <p>Правила решают, нужна ли библиотека на этой системе и применяется ли аргумент.
 * Реализована стандартная схема: правила проверяются по порядку, побеждает последнее
 * подходящее; если правил нет, запись применяется; если правила есть, но ни одно не подошло,
 * запись не применяется.</p>
 */
public final class Rules {
    private Rules() {
    }

    /** Имя текущей ОС в форме, используемой Mojang: windows, linux, osx. */
    public static String osName() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return "windows";
        }
        if (name.contains("mac") || name.contains("darwin")) {
            return "osx";
        }
        return "linux";
    }

    public static String osVersion() {
        return System.getProperty("os.version", "");
    }

    /** Архитектура: x86, x86_64, arm64, arm32. */
    public static String osArch() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (arch) {
            case "amd64", "x86_64" -> "x86_64";
            case "x86", "i386", "i486", "i586", "i686" -> "x86";
            case "aarch64", "arm64" -> "arm64";
            case "arm", "armv7", "arm32" -> "arm32";
            default -> arch;
        };
    }

    /**
     * Проверяет условие правила.
     *
     * @param rule     объект правила из JSON
     * @param features дополнительные признаки запуска (например {@code has_custom_resolution})
     */
    public static boolean matches(Map<String, Object> rule, Map<String, Boolean> features) {
        Map<String, Object> os = rule.get("os") instanceof Map
                ? Json.asObject(rule.get("os")) : null;
        if (os != null) {
            String name = Json.string(os, "name", null);
            if (name != null && !name.equals(osName())) {
                return false;
            }
            String arch = Json.string(os, "arch", null);
            if (arch != null && !arch.equals(osArch())) {
                return false;
            }
            String version = Json.string(os, "version", null);
            if (version != null && !osVersion().matches(version)) {
                return false;
            }
        }
        Map<String, Object> ruleFeatures = rule.get("features") instanceof Map
                ? Json.asObject(rule.get("features")) : null;
        if (ruleFeatures != null && features != null) {
            for (Map.Entry<String, Object> entry : ruleFeatures.entrySet()) {
                boolean expected = Boolean.TRUE.equals(entry.getValue());
                if (expected != Boolean.TRUE.equals(features.get(entry.getKey()))) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Разбирает список правил и возвращает итоговое решение.
     *
     * <p>Такая схема соответствует официальному формату: платформенные библиотеки
     * (например {@code org.lwjgl:lwjgl:3.3.3:natives-macos}) помечены правилом
     * {@code allow} для своей системы и не должны попадать в путь поиска классов на других.</p>
     */
    public static boolean evaluate(Object rulesValue, String defaultAction, Map<String, Boolean> features) {
        if (!(rulesValue instanceof List<?> rules) || rules.isEmpty()) {
            return true;
        }
        boolean matched = false;
        boolean allowed = false;
        for (Object item : rules) {
            if (!(item instanceof Map)) {
                continue;
            }
            Map<String, Object> rule = Json.asObject(item);
            if (matches(rule, features)) {
                matched = true;
                allowed = "allow".equals(Json.string(rule, "action", "allow"));
            }
        }
        return matched ? allowed : "allow".equals(defaultAction);
    }

    /** Удобная обёртка: объект библиотеки или аргумента с полями {@code rules}/{@code os}/{@code action}. */
    public static boolean allows(Map<String, Object> entry, Map<String, Boolean> features) {
        Object rules = entry.get("rules");
        if (rules != null) {
            return evaluate(rules, Json.string(entry, "action", null), features);
        }
        if (entry.containsKey("os") || entry.containsKey("features")) {
            // Условие записано прямо в объекте — такой вариант встречается в старых версиях формата.
            boolean matches = matches(entry, features);
            boolean allowAction = !"disallow".equals(Json.string(entry, "action", null));
            return matches == allowAction;
        }
        return true;
    }
}
