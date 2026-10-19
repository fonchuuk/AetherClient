package com.aetherglass.client.render.util;

/**
 * Пружина: значение не просто «подъезжает» к цели, а может перелететь её и
 * вернуться — именно это даёт упругое, «живое» движение интерфейса.
 *
 * <p>Физика настоящая: ускорение считается как {@code k·(цель − x) − c·v},
 * где {@code k} — жёсткость, {@code c} — вязкость. При {@code c < 2·√k}
 * пружина недодемпфирована и делает перелёт; при {@code c = 2·√k} —
 * идеальная, без перелёта.</p>
 *
 * <p>Шаг интегрирования фиксированный (1/240 с), поэтому результат не зависит
 * от числа кадров: на 30 и на 240 FPS движение одинаковое, а большой скачок
 * времени не разносит значения в бесконечность.</p>
 */
public final class Spring {

    /** Внутренний шаг: меньше кадра любого разумного FPS. */
    private static final float STEP = 1.0F / 240.0F;

    /** За сколько шагов максимум догоняем пропущенное время (защита от «заморозки» окна). */
    private static final int MAX_STEPS = 120;

    /** Самое большое окно времени, которое отыгрывается целиком: дольше — значит окно стояло. */
    private static final float MAX_WINDOW = 5.0F;

    /** Граница устойчивости: (шаг × частота)² больше этого значения — шаг уже слишком крупный. */
    private static final float STABILITY_LIMIT = 3.0F;

    private float value;
    private float velocity;
    private float target;
    private float stiffness = 220.0F;
    private float damping = 17.0F;

    public Spring(float initial) {
        this.value = initial;
        this.target = initial;
    }

    /** Куда пружина стремится. */
    public void setTarget(float target) {
        this.target = target;
    }

    /** Мгновенно ставит значение и гасит скорость: для появления элемента. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
        this.velocity = 0.0F;
    }

    /** Настройка пружины: жёсткость и вязкость. */
    public void configure(float stiffness, float damping) {
        this.stiffness = Math.max(0.01F, stiffness);
        this.damping = Math.max(0.0F, damping);
    }

    /** Продвигает пружину на {@code deltaSeconds}. */
    public void update(float deltaSeconds) {
        float total = Math.min(Math.max(0.0F, deltaSeconds), MAX_WINDOW);
        // Шаг не крупнее кадра 240 FPS. Но если окно простояло, шаг растёт: иначе
        // пружина замрёт на полпути и так и останется висеть. 120 шагов хватает
        // на любое разумное время, а устойчивость проверяется ниже.
        float step = Math.max(STEP, total / MAX_STEPS);
        if (step * step * Math.max(0.0F, this.stiffness) > STABILITY_LIMIT) {
            // Такой крупный шаг для этой жёсткости уже неустойчив: пружина за это
            // время всё равно давно успокоилась, поэтому просто ставим цель.
            this.value = this.target;
            this.velocity = 0.0F;
            return;
        }
        float remaining = total;
        while (remaining > 0.0F) {
            float current = Math.min(step, remaining);
            remaining -= current;
            float acceleration = this.stiffness * (this.target - this.value) - this.damping * this.velocity;
            this.velocity += acceleration * current;
            this.value += this.velocity * current;
        }
        if (Math.abs(this.target - this.value) < 0.0005F && Math.abs(this.velocity) < 0.0005F) {
            this.value = this.target;
            this.velocity = 0.0F;
        }
    }

    public float value() {
        return this.value;
    }

    public float velocity() {
        return this.velocity;
    }

    public float target() {
        return this.target;
    }

    /** Успокоилась ли пружина. */
    public boolean settled() {
        return this.value == this.target && this.velocity == 0.0F;
    }

    /**
     * Насколько пружина «пружинистая»: доля перелёта при прыжке 0 → 1.
     *
     * <p>Считается по формуле для гармонического осциллятора: чем меньше
     * вязкость относительно критической, тем больше перелёт.</p>
     */
    public float overshoot() {
        float critical = 2.0F * (float) Math.sqrt(this.stiffness);
        if (this.damping >= critical) {
            return 0.0F;
        }
        float zeta = this.damping / critical;
        return (float) Math.exp(-zeta * Math.PI / (float) Math.sqrt(1.0F - zeta * zeta));
    }

    /** Импульс: толкает пружину, не меняя цель. Используется при нажатии. */
    public void push(float amount) {
        this.velocity += amount;
    }
}
