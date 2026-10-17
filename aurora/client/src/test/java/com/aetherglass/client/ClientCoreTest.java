package com.aetherglass.client;

import com.aetherglass.client.gui.theme.AuroraTheme;
import com.aetherglass.client.render.particle.ParticlePreset;
import com.aetherglass.client.render.particle.ParticleShape;
import com.aetherglass.client.render.particle.ParticleSystem;
import com.aetherglass.client.render.util.AnimationTimer;
import com.aetherglass.client.render.util.ColorUtil;
import com.aetherglass.client.render.util.Easing;
import com.aetherglass.client.render.util.FrameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверки чистой логики клиента: цвет, кривые, анимации по времени и пул частиц.
 *
 * <p>Эти проверки не требуют запуска игры — они исполняются обычным Gradle-тестом
 * и ловят ошибки в том, что легко посчитать точно.</p>
 */
final class ClientCoreTest {
    @Test
    @DisplayName("цвет: разбор и запись шестнадцатеричного значения")
    void colorHexRoundTrip() {
        int color = ColorUtil.argb(200, 18, 90, 240);
        assertEquals(color, ColorUtil.parseHex(ColorUtil.toHex(color), 0));
        assertEquals(200, ColorUtil.alpha(color));
        assertEquals(18, ColorUtil.red(color));
        assertEquals(90, ColorUtil.green(color));
        assertEquals(240, ColorUtil.blue(color));
    }

    @Test
    @DisplayName("цвет: прозрачность и затухание")
    void colorAlpha() {
        int color = ColorUtil.argb(255, 10, 20, 30);
        assertEquals(128, ColorUtil.alpha(ColorUtil.fade(color, 0.5F)), 1);
        assertEquals(0, ColorUtil.alpha(ColorUtil.fade(color, 0.0F)));
        assertEquals(color, ColorUtil.withAlpha(color, 255));
    }

    @Test
    @DisplayName("цвет: смешивание попадает в диапазон")
    void colorLerp() {
        int from = ColorUtil.argb(255, 0, 0, 0);
        int to = ColorUtil.argb(255, 255, 255, 255);
        assertEquals(from, ColorUtil.lerp(from, to, 0.0F));
        assertEquals(to, ColorUtil.lerp(from, to, 1.0F));
        int middle = ColorUtil.lerp(from, to, 0.5F);
        assertEquals(128, ColorUtil.red(middle), 2);
    }

    @Test
    @DisplayName("цвет: HSV → RGB совпадает с основными цветами")
    void colorHsbPrimary() {
        assertEquals(0xFFFF0000, ColorUtil.hsb(0.0F, 1.0F, 1.0F));
        assertEquals(0xFF00FF00, ColorUtil.hsb(1.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFF0000FF, ColorUtil.hsb(2.0F / 3.0F, 1.0F, 1.0F));
        assertEquals(0xFFFFFF00, ColorUtil.hsb(1.0F / 6.0F, 1.0F, 1.0F));
        assertEquals(0xFF000000, ColorUtil.hsb(0.5F, 1.0F, 0.0F), "нулевая яркость — чёрный");
        assertEquals(0xFFFFFFFF, ColorUtil.hsb(0.5F, 0.0F, 1.0F), "нулевая насыщенность — белый");
    }

    @Test
    @DisplayName("цвет: прямое и обратное преобразование HSV не расходятся")
    void colorHsbRoundTrip() {
        int[] samples = {0xFF7FD4FF, 0xFFB98CFF, 0xFFFF9A3C, 0xFF5BE8FF, 0xFF123456, 0xFF808080};
        for (int color : samples) {
            int restored = ColorUtil.hsb(ColorUtil.hueOf(color), ColorUtil.saturationOf(color),
                    ColorUtil.brightnessOf(color));
            assertEquals(ColorUtil.red(color), ColorUtil.red(restored), 1, "красный: " + ColorUtil.toHex(color));
            assertEquals(ColorUtil.green(color), ColorUtil.green(restored), 1, "зелёный: " + ColorUtil.toHex(color));
            assertEquals(ColorUtil.blue(color), ColorUtil.blue(restored), 1, "синий: " + ColorUtil.toHex(color));
        }
        // Оттенок круговой: пол-оборота от красного — голубой.
        assertEquals(0.5F, ColorUtil.hueOf(0xFF00FFFF), 0.01F);
        assertEquals(0.0F, ColorUtil.saturationOf(0xFF808080), 0.001F);
        assertEquals(1.0F, ColorUtil.brightnessOf(0xFFFFFFFF), 0.001F);
    }

    @Test
    @DisplayName("кривые анимации: границы и монотонность")
    void easingBounds() {
        assertEquals(0.0F, Easing.easeOutCubic(0.0F), 0.0001F);
        assertEquals(1.0F, Easing.easeOutCubic(1.0F), 0.0001F);
        assertEquals(1.0F, Easing.easeInOutQuad(1.0F), 0.0001F);
        assertEquals(1.0F, Easing.easeOutQuint(1.0F), 0.0001F);
        float previous = -1.0F;
        for (int step = 0; step <= 20; step++) {
            float value = Easing.easeInOutQuad(step / 20.0F);
            assertTrue(value >= previous, "кривая не должна идти назад");
            previous = value;
        }
    }

    @Test
    @DisplayName("анимация: значение доходит до цели и не зависит от числа кадров")
    void animationTimerConverges() {
        AnimationTimer fast = new AnimationTimer(0.0F);
        AnimationTimer slow = new AnimationTimer(0.0F);
        fast.setTarget(1.0F);
        slow.setTarget(1.0F);
        int frames = 60;
        float step = 0.5F / frames;
        for (int frame = 0; frame < frames; frame++) {
            fast.update(step);
        }
        slow.update(0.5F);
        // Шестьдесят мелких шагов и один крупный дают один и тот же результат:
        // именно поэтому анимация выглядит одинаково при любой частоте кадров.
        assertEquals(fast.current(), slow.current(), 0.001F);
        assertTrue(fast.current() > 0.9F, "за полсекунды анимация должна почти закончиться");
    }

    @Test
    @DisplayName("часы кадра: шаг ограничен, время накапливается")
    void frameClockSteps() {
        FrameClock.reset();
        float before = FrameClock.elapsedSeconds();
        for (int frame = 0; frame < 10; frame++) {
            FrameClock.tick();
        }
        assertTrue(FrameClock.deltaSeconds() > 0.0F);
        assertTrue(FrameClock.deltaSeconds() <= 0.1F, "слишком большой шаг должен обрезаться");
        assertTrue(FrameClock.elapsedSeconds() > before);
    }

    @Test
    @DisplayName("пул частиц: предел, учёт живых и исчезновение по времени")
    void particlePool() {
        // Минимальный пул — шестнадцать частиц: меньше бессмысленно для эффекта.
        ParticleSystem system = new ParticleSystem(4);
        int capacity = system.capacity();
        assertEquals(0, system.alive());
        for (int index = 0; index < capacity + 4; index++) {
            boolean created = system.spawn(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 1.0F, 0.1F, 1.0F, 0.0F, 0.0F, 0.0F,
                    0xFFFFFFFF, 0xFFFFFFFF, ParticleShape.CIRCLE);
            assertEquals(index < capacity, created, "создаются только свободные частицы");
        }
        assertEquals(capacity, system.alive());
        system.update(0.5F);
        assertEquals(capacity, system.alive(), "раньше времени частицы не исчезают");
        system.update(0.6F);
        assertEquals(0, system.alive(), "просроченные частицы освобождают место");

        system.spawn(1.0D, 2.0D, 3.0D, 0.0D, 0.0D, 0.0D, 1.0F, 0.1F, 1.0F, 0.0F, 0.0F, 0.0F,
                0xFFFFFFFF, 0xFFFFFFFF, ParticleShape.STAR);
        system.setCapacity(2);
        assertEquals(1, system.alive(), "частицы сохраняются при изменении предела");
        assertEquals(16, system.capacity(), "ниже минимального предела пул не опускается");
        system.clear();
        assertEquals(0, system.alive());
    }

    @Test
    @DisplayName("пресеты частиц: значения в допустимых пределах")
    void presetsAreSane() {
        for (ParticlePreset preset : ParticlePreset.values()) {
            assertTrue(preset.lifetime() > 0.0F, preset.name());
            assertTrue(preset.size() > 0.0F, preset.name());
            assertTrue(preset.alpha() > 0.0F && preset.alpha() <= 1.0F, preset.name());
            assertTrue(preset.rate() >= 0, preset.name());
            assertTrue(preset.fade() > 0.0F, preset.name());
        }
    }

    @Test
    @DisplayName("оформление: смена палитры и плотности меняет цвета")
    void themeTokens() {
        AuroraTheme.palette(AuroraTheme.Palette.DARK);
        AuroraTheme.density(0.0F);
        int transparent = AuroraTheme.glassTint();
        AuroraTheme.density(1.0F);
        int dense = AuroraTheme.glassTint();
        assertTrue(ColorUtil.alpha(dense) > ColorUtil.alpha(transparent),
                "плотное стекло должно быть непрозрачнее");

        AuroraTheme.palette(AuroraTheme.Palette.LIGHT);
        int light = AuroraTheme.textPrimary();
        AuroraTheme.palette(AuroraTheme.Palette.DARK);
        assertFalse(light == AuroraTheme.textPrimary(), "палитры должны отличаться");

        AuroraTheme.radius(999.0F);
        assertTrue(AuroraTheme.radius() <= 24.0F, "радиус ограничен");
        AuroraTheme.motion(10.0F);
        assertTrue(AuroraTheme.motion() <= 2.5F, "скорость ограничена");
        AuroraTheme.palette(AuroraTheme.Palette.DARK);
        AuroraTheme.density(0.55F);
        AuroraTheme.radius(10.0F);
        AuroraTheme.motion(1.0F);
    }
}
