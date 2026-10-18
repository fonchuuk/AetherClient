package com.aetherglass.client.render.util;

/**
 * Анимация по времени кадра: значение плавно приближается к цели за заданное время.
 *
 * <p>Скорость не зависит от числа кадров в секунду — только от реального времени,
 * поэтому на 30 и на 300 FPS движение выглядит одинаково.</p>
 */
public final class AnimationTimer {
    private float current;
    private float target;
    private float speed = 8.0F;

    public AnimationTimer(float initial) {
        this.current = initial;
        this.target = initial;
    }

    /** Продвигает анимацию на {@code deltaSeconds}. */
    public void update(float deltaSeconds) {
        if (this.current == this.target) {
            return;
        }
        float factor = 1.0F - (float) Math.exp(-this.speed * Math.max(0.0F, deltaSeconds));
        this.current += (this.target - this.current) * factor;
        if (Math.abs(this.target - this.current) < 0.001F) {
            this.current = this.target;
        }
    }

    /** Устанавливает цель (0 или 1 для обычных анимаций наведения). */
    public void setTarget(float target) {
        this.target = target;
    }

    /** Мгновенно ставит значение: используется при появлении элемента. */
    public void snap(float value) {
        this.current = value;
        this.target = value;
    }

    public float current() {
        return this.current;
    }

    public float target() {
        return this.target;
    }

    /** Скорость сходимости: больше — быстрее. */
    public void speed(float speed) {
        this.speed = Math.max(0.01F, speed);
    }
}
