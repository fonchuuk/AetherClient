package com.aetherglass.client.render;

import com.aetherglass.client.utils.AuroraLog;

/** Отдельный предохранитель прохода: считает только последовательные ошибки. */
final class RenderFailureTracker {
    static final int MAX_FAILURES = 12;
    private final String module;
    private final String stage;
    private int failures;
    private boolean disabled;

    RenderFailureTracker(String module, String stage) {
        this.module = module;
        this.stage = stage;
    }

    boolean disabled() {
        return disabled;
    }

    void success() {
        if (!disabled) {
            failures = 0;
        }
    }

    void failure(Throwable cause) {
        if (disabled) {
            return;
        }
        failures++;
        if (failures <= 3 || failures == MAX_FAILURES) {
            AuroraLog.error("module=" + module + ", stage=" + stage
                    + ", consecutiveFailures=" + failures + "/" + MAX_FAILURES, cause);
        }
        if (failures == MAX_FAILURES) {
            disabled = true;
            AuroraLog.error("module=" + module + ", stage=" + stage
                    + ": проход отключён до перезапуска клиента. Остальные проходы не отключены;"
                    + " пришлите logs/latest.log с полным стеком ошибки выше.");
        }
    }
}
