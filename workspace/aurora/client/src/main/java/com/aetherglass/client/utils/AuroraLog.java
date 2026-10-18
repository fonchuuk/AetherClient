package com.aetherglass.client.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Логирование клиента: все сообщения идут под общим префиксом. */
public final class AuroraLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("AetherGlassClient");

    private AuroraLog() {
    }

    public static void info(String message) {
        LOGGER.info("[Aurora] {}", message);
    }

    public static void warn(String message) {
        LOGGER.warn("[Aurora] {}", message);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error("[Aurora] {}", message, throwable);
    }

    /** Сообщение об ошибке без исключения: для понятных, заранее известных сбоев. */
    public static void error(String message) {
        LOGGER.error("[Aurora] {}", message);
    }
}
