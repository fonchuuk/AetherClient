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

    @Test
    @DisplayName("глобальная тема Liquid Glass: Hue, Blur и Radius")
    void liquidGlassThemeTokens() {
        AuroraTheme.accentHue(0.32F);
        assertEquals(0.32F, AuroraTheme.accentHue(), 0.001F);

        AuroraTheme.blur(0.15F);
        int lowBlur = AuroraTheme.glassTint();
        AuroraTheme.blur(0.95F);
        int highBlur = AuroraTheme.glassTint();
        assertTrue(ColorUtil.alpha(highBlur) > ColorUtil.alpha(lowBlur),
                "увеличение силы размытия (Blur) должно уплотнять стеклянную подложку");

        AuroraTheme.radius(14.6F);
        assertEquals(15, AuroraTheme.radiusPx());

        AuroraTheme.borderColor(0xFFAA3DFF);
        assertEquals(0xFFAA3DFF, AuroraTheme.borderColor());
        AuroraTheme.borderWidth(1.0F);
        assertEquals(1.0F, AuroraTheme.borderWidth(), 0.001F);
        assertEquals(3, com.aetherglass.client.gui.screen.AuroraInlineMenuScreen.NUM_COLS);
        AuroraTheme.blur(0.65F);
        AuroraTheme.radius(10.0F);
    }

    @Test
    @DisplayName("событийный KeybindManager, Чистый экран и счётчик CPS")
    void keybindManagerAndCpsTracker() {
        com.aetherglass.client.input.KeybindManager km = com.aetherglass.client.input.KeybindManager.get();
        assertEquals("RSHIFT", com.aetherglass.client.input.KeybindManager.formatKey(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT));
        assertEquals("F8", com.aetherglass.client.input.KeybindManager.formatKey(org.lwjgl.glfw.GLFW.GLFW_KEY_F8));
        assertTrue(com.aetherglass.client.input.KeybindManager.isClearKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE));
        assertTrue(com.aetherglass.client.input.KeybindManager.isClearKey(org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE));

        // Режим «Чистый экран» плавно гасит и возвращает HUD
        boolean initialClean = km.isCleanScreenActive();
        km.toggleCleanScreen();
        assertEquals(!initialClean, km.isCleanScreenActive());
        float vis = km.updateHudVisibility(0.5F);
        assertTrue(vis >= 0.0F && vis <= 1.0F);
        if (km.isCleanScreenActive()) {
            km.toggleCleanScreen();
        }
        assertFalse(km.isCleanScreenActive());

        // Событийный счётчик CPS
        com.aetherglass.client.input.InputTracker tracker = com.aetherglass.client.input.InputTracker.get();
        int beforeLeft = tracker.leftCps();
        tracker.recordClick(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        tracker.recordClick(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertEquals(beforeLeft + 2, tracker.leftCps());
    }

    @Test
    @DisplayName("сгруппированный HUD (image-2): все панели и сохранение координат")
    void groupedHudPanelsAndLayout() {
        com.aetherglass.client.render.hud.HudLayout layout = com.aetherglass.client.render.hud.HudLayout.get();
        layout.resetAll();

        // Проверяем наличие всех ключевых панелей из Блока 4 и Блока 5
        assertTrue(com.aetherglass.client.render.hud.HudElementType.WATERMARK.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.PERFORMANCE.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.FRIENDS.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.POTIONS.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.COOLDOWNS.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.COORDINATES.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.PLAYER_STATUS.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.QUICK_ACCESS.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.KEYSTROKES.defaultEnabled());
        assertTrue(com.aetherglass.client.render.hud.HudElementType.CPS.defaultEnabled());

        layout.setFromPixels(com.aetherglass.client.render.hud.HudElementType.COOLDOWNS, 400, 250, 800, 500, 120, 60);
        assertEquals(0.5F, layout.x(com.aetherglass.client.render.hud.HudElementType.COOLDOWNS), 0.001F);
        assertEquals(0.5F, layout.y(com.aetherglass.client.render.hud.HudElementType.COOLDOWNS), 0.001F);
        layout.resetAll();
    }

    @Test
    @DisplayName("матовое жидкое стекло (Lunar / Feather) и мировые частицы в радиусе")
    void frostedLiquidGlassAndWorldParticles() {
        AuroraTheme.palette(AuroraTheme.Palette.DARK);
        AuroraTheme.blur(0.65F);
        assertTrue(ColorUtil.alpha(AuroraTheme.panelSurface()) <= 95,
                "панели должны быть полупрозрачными (25-30%), а не глухими чёрными плашками");
        assertTrue(ColorUtil.alpha(AuroraTheme.glassTint()) <= 95,
                "карточки HUD и модулей должны быть полупрозрачными");

        com.aetherglass.client.modules.impl.ParticleModule pm =
                new com.aetherglass.client.modules.impl.ParticleModule();
        assertTrue(pm.radius() >= 3.0F && pm.radius() <= 28.0F,
                "ParticleModule должен иметь настройку мирового радиуса спавна вокруг игрока");
        assertTrue(pm.rateSetting().get() >= 1 && pm.rateSetting().get() <= 100,
                "ParticleModule должен иметь ползунок частоты появления частиц");
    }

    @Test
    @DisplayName("оффлайн-аккаунты (Блок 2.3): валидация, Offline UUID и сохранение в конфиг")
    void offlineAccountManagerSwitching() {
        com.aetherglass.client.account.AccountManager am = com.aetherglass.client.account.AccountManager.get();
        assertEquals("Aether_01", com.aetherglass.client.account.AccountManager.sanitizeUsername("Aether_01!@#"));
        assertTrue(com.aetherglass.client.account.AccountManager.isValidUsername("LunarGlass"));
        assertFalse(com.aetherglass.client.account.AccountManager.isValidUsername("ab"));

        assertTrue(am.switchAccount("CrystalFox"));
        assertTrue(am.accounts().contains("CrystalFox"));
        assertTrue(com.aetherglass.client.account.AccountManager.offlineUuid("CrystalFox") != null);

        String randomNick = am.switchRandomAccount();
        assertTrue(com.aetherglass.client.account.AccountManager.isValidUsername(randomNick));
        assertTrue(am.accounts().contains(randomNick));
    }

    @Test
    @DisplayName("BlockShader, HandsShader, Crosshair и SkyShader (Блок 4.1–4.4)")
    void blockHandCrosshairSkySettings() {
        // 4.1: BlockShader — заливка и контур работают одновременно, есть ползунки толщины и свечения
        com.aetherglass.client.modules.impl.BlockEffectsModule blocks =
                new com.aetherglass.client.modules.impl.BlockEffectsModule();
        assertTrue(blocks.showFill(), "заливка блока включена по умолчанию");
        assertTrue(blocks.showOutline(), "обводка контура включена поверх заливки");
        blocks.setShowOutline(true);
        assertTrue(blocks.showFill(), "включение обводки не должно отключать заливку");
        blocks.outlineWidthSetting().set(0.032F);
        assertEquals(0.032F, blocks.outlineWidth(), 0.001F);
        blocks.glowSetting().set(1.15F);
        assertEquals(1.15F, blocks.glow(), 0.001F);

        // 4.2: HandsShader — модель предмета остаётся 100% видимой
        com.aetherglass.client.modules.impl.HandEffectsModule hands =
                new com.aetherglass.client.modules.impl.HandEffectsModule();
        assertEquals(1.00F, hands.modelAlpha(), 0.001F);

        // 4.3: Crosshair — поддержка минимального размера в 1–2 пикселя
        assertEquals(5, com.aetherglass.client.render.crosshair.CrosshairStyle.values().length);

        // 4.4: SkyShader — перелив неба и горизонта
        int blendedSky = com.aetherglass.client.render.sky.SkyEffectBlender.blendSky(0x78A7FF);
        int blendedSunrise = com.aetherglass.client.render.sky.SkyEffectBlender.blendSunrise(0x00000000);
        assertTrue(blendedSky >= 0);
        assertTrue(ColorUtil.alpha(blendedSunrise) > 0, "горизонт получает свечение сияния");
    }
}
