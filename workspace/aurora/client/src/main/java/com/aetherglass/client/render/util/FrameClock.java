package com.aetherglass.client.render.util;

/**
 * Реальное время кадра для анимаций.
 *
 * <p>Все движения в клиенте считают длительность в секундах, а не в кадрах: на мощном
 * и на слабом компьютере анимация длится одинаково. Слишком большой шаг (после паузы
 * или зависания) обрезается, чтобы частицы не «прыгали».</p>
 */
public final class FrameClock {
    private static final float MAX_STEP_SECONDS = 0.1F;

    private static long previousNanos = -1L;
    private static float deltaSeconds;
    private static float elapsedSeconds;

    private FrameClock() {
    }

    /** Обновляет часы; вызывается один раз за кадр. */
    public static void tick() {
        long now = System.nanoTime();
        if (previousNanos < 0L) {
            previousNanos = now;
            deltaSeconds = 1.0F / 60.0F;
        } else {
            deltaSeconds = Math.min(MAX_STEP_SECONDS, (now - previousNanos) / 1_000_000_000.0F);
            previousNanos = now;
        }
        elapsedSeconds += deltaSeconds;
    }

    /** Длительность предыдущего кадра в секундах. */
    public static float deltaSeconds() {
        return deltaSeconds;
    }

    /** Время с первого кадра: основа для непрерывных анимаций фона и радуги. */
    public static float elapsedSeconds() {
        return elapsedSeconds;
    }

    /** Сбрасывает накопленное время: при сворачивании окна, чтобы анимации не «догоняли». */
    public static void reset() {
        previousNanos = -1L;
        deltaSeconds = 1.0F / 60.0F;
    }
}
