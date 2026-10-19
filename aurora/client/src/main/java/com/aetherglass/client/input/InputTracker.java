package com.aetherglass.client.input;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Событийный трекер кликов мыши (CPS) для левой и правой кнопок (Блок 5.4).
 *
 * <p>Пополняется напрямую из {@code MouseHandlerMixin} при физических событиях
 * {@code GLFW_PRESS}, а не опросом в {@code onTick}, поэтому фиксирует каждый клик
 * без потерь на высоких CPS.</p>
 */
public final class InputTracker {
    private static final InputTracker INSTANCE = new InputTracker();
    private static final long WINDOW_NANOS = 1_000_000_000L;

    private final Deque<Long> leftClicks = new ArrayDeque<>(64);
    private final Deque<Long> rightClicks = new ArrayDeque<>(64);

    private InputTracker() {
    }

    public static InputTracker get() {
        return INSTANCE;
    }

    public synchronized void recordClick(int button) {
        recordClickAt(button, System.nanoTime());
    }

    public synchronized void recordClickAt(int button, long nanoTime) {
        prune(nanoTime);
        if (button == 0) {
            this.leftClicks.addLast(nanoTime);
        } else if (button == 1) {
            this.rightClicks.addLast(nanoTime);
        }
    }

    public synchronized int leftCps() {
        return leftCpsAt(System.nanoTime());
    }

    public synchronized int rightCps() {
        return rightCpsAt(System.nanoTime());
    }

    public synchronized int leftCpsAt(long nanoTime) {
        prune(nanoTime);
        return this.leftClicks.size();
    }

    public synchronized int rightCpsAt(long nanoTime) {
        prune(nanoTime);
        return this.rightClicks.size();
    }

    public synchronized void clear() {
        this.leftClicks.clear();
        this.rightClicks.clear();
    }

    private void prune(long now) {
        long cutoff = now - WINDOW_NANOS;
        while (!this.leftClicks.isEmpty() && this.leftClicks.peekFirst() < cutoff) {
            this.leftClicks.removeFirst();
        }
        while (!this.rightClicks.isEmpty() && this.rightClicks.peekFirst() < cutoff) {
            this.rightClicks.removeFirst();
        }
    }
}
