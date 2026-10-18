package com.aetherglass.client.render.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Пружина: движение должно быть упругим, но предсказуемым — перелёт есть,
 * бесконечного разгона нет, результат не зависит от числа кадров.
 */
final class SpringTest {

    /** Прогоняет пружину заданное время шагом {@code step} и возвращает максимум. */
    private static float run(Spring spring, float seconds, float step, float target) {
        spring.setTarget(target);
        float max = 0.0F;
        for (float time = 0.0F; time < seconds; time += step) {
            spring.update(step);
            max = Math.max(max, spring.value());
        }
        return max;
    }

    @Test
    void overshootsWhenUnderdamped() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 16.0F); // вязкость ниже критической 2·√240 ≈ 31
        float max = run(spring, 2.0F, 1.0F / 60.0F, 1.0F);
        assertTrue(max > 1.02F, "пружина должна перелетать цель, максимум: " + max);
        assertTrue(max < 1.35F, "перелёт не должен быть диким, максимум: " + max);
    }

    @Test
    void settlesExactlyOnTarget() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 16.0F);
        run(spring, 6.0F, 1.0F / 60.0F, 1.0F);
        assertEquals(1.0F, spring.value(), 0.001F);
        assertTrue(spring.settled(), "после успокоения значение и скорость должны быть точными");
    }

    @Test
    void criticalDampingHasNoOvershoot() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 2.0F * (float) Math.sqrt(240.0F));
        float max = run(spring, 3.0F, 1.0F / 120.0F, 1.0F);
        assertTrue(max <= 1.001F, "критическая вязкость — без перелёта, максимум: " + max);
        assertEquals(0.0F, spring.overshoot(), 0.02F);
    }

    @Test
    void frameRateDoesNotChangeResult() {
        Spring fast = new Spring(0.0F);
        Spring slow = new Spring(0.0F);
        fast.configure(240.0F, 16.0F);
        slow.configure(240.0F, 16.0F);
        run(fast, 0.5F, 1.0F / 240.0F, 1.0F);
        run(slow, 0.5F, 1.0F / 30.0F, 1.0F);
        assertEquals(fast.value(), slow.value(), 0.02F,
                "на 30 и на 240 FPS движение обязано совпадать");
    }

    @Test
    void hugeTimeStepDoesNotExplode() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 16.0F);
        spring.setTarget(1.0F);
        spring.update(5.0F); // окно «заморозилось» на пять секунд
        assertTrue(Float.isFinite(spring.value()), "значение должно остаться числом");
        assertEquals(1.0F, spring.value(), 0.001F);
    }

    @Test
    void snapStopsMotionImmediately() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 16.0F);
        spring.setTarget(1.0F);
        spring.update(0.1F);
        spring.snap(0.5F);
        assertEquals(0.5F, spring.value(), 0.0001F);
        assertEquals(0.0F, spring.velocity(), 0.0001F);
        assertTrue(spring.settled());
    }

    @Test
    void pushAddsVelocityWithoutChangingTarget() {
        Spring spring = new Spring(0.0F);
        spring.configure(240.0F, 16.0F);
        spring.setTarget(1.0F);
        float before = spring.velocity();
        spring.push(-5.0F);
        assertEquals(before - 5.0F, spring.velocity(), 0.0001F);
        assertEquals(1.0F, spring.target(), 0.0001F);
    }

    @Test
    void overshootFormulaMatchesBehaviour() {
        Spring spring = new Spring(0.0F);
        spring.configure(400.0F, 12.0F);
        float expected = spring.overshoot();
        float max = run(spring, 4.0F, 1.0F / 480.0F, 1.0F);
        assertEquals(1.0F + expected, max, 0.06F);
        assertFalse(expected <= 0.0F, "при такой вязкости перелёт обязан быть");
    }
}
