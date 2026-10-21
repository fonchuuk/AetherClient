package com.aetherglass.client.render.hud;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Размытый «снимок» мира под панелями HUD (настоящий backdrop blur, а не имитация).
 *
 * <p>Как это работает и почему так:</p>
 * <ol>
 *   <li>В начале кадра GUI (мир уже отрисован, ни одного пакета GUI ещё не
 *       отправлено) кадровый буфер копируется в маленькую мишень {@code 1/8}
 *       через {@code blitAndBlendToTexture} — это обычный блит квадом с
 *       линейной фильтрацией, то есть честное уменьшение с усреднением.</li>
 *   <li>Уменьшенная картинка переносится в {@link DynamicTexture} — у неё есть
 *       обычный идентификатор, поэтому её можно рисовать штатным
 *       {@code GuiGraphics.blit} внутри панелей.</li>
 *   <li>Панель растягивает эту текстуру на себя — увеличение с билинейной
 *       фильтрацией даёт мягкое размытие. Стоимость: один блит и одна копия
 *       на кадр, GPU-только, ноль чтения пикселей на CPU.</li>
 * </ol>
 *
 * <p>Любая ошибка (старый драйвер, отсутствие нужного usage-флага текстуры)
 * один раз логируется, после чего модуль навсегда переключается в безопасный
 * режим: панели рисуются обычным полупрозрачным стеклом. Клиент из-за размытия
 * упасть не может — весь путь обёрнут в try/catch.</p>
 */
public final class BackdropBlur {
    private static final Identifier ID =
            Identifier.fromNamespaceAndPath("aetherglass", "hud_backdrop_blur");
    private static final int DOWNSCALE = 14;
    private static final int MIN_W = 32;
    private static final int MIN_H = 18;

    private static TextureTarget downscale;
    private static DynamicTexture texture;
    private static boolean failed;
    private static int width;
    private static int height;
    private static int captures;
    private static String lastError = "";

    private BackdropBlur() {
    }

    /** Идентификатор размытого фона или {@code null}, если путь недоступен. */
    public static Identifier texture() {
        return failed ? null : ID;
    }

    public static int width() {
        return width;
    }

    public static int height() {
        return height;
    }

    public static boolean available() {
        return !failed && texture != null && captures > 0;
    }

    public static String status() {
        if (failed) {
            return "blur недоступен: " + lastError;
        }
        return available() ? "blur " + width + "×" + height : "blur ждёт кадр";
    }

    /**
     * Снимает размытый фон текущего кадра. Вызывается один раз за кадр в начале
     * отрисовки GUI, до любых вызовов {@code GuiGraphics}.
     */
    public static void capture() {
        if (failed) {
            return;
        }
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null || client.level == null) {
                return;
            }
            RenderTarget main = client.getMainRenderTarget();
            if (main == null || main.width <= 0 || main.height <= 0) {
                return;
            }
            int w = Math.max(MIN_W, main.width / DOWNSCALE);
            int h = Math.max(MIN_H, main.height / DOWNSCALE);
            if (downscale == null || width != w || height != h) {
                rebuild(client, w, h);
            }
            if (downscale == null || texture == null) {
                return;
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            // Старое содержимое стираем: блит идёт с наложением, иначе картинка
            // копилась бы кадр за кадром и «размазывалась» по экрану.
            encoder.clearColorTexture(downscale.getColorTexture(), 0);
            main.blitAndBlendToTexture(downscale.getColorTextureView());
            // срез 1:1 из уменьшенной мишени в обычную текстуру (одинаковый размер)
            encoder.copyTextureToTexture(downscale.getColorTexture(), texture.getTexture(),
                    0, 0, 0, 0, 0, width, height);
            captures++;
        } catch (Throwable throwable) {
            failed = true;
            lastError = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
            System.err.println("[AetherGlass] Размытие фона HUD отключено — " + lastError);
            close();
        }
    }

    private static void rebuild(Minecraft client, int w, int h) {
        close();
        downscale = new TextureTarget("aetherglass-hud-blur-down", w, h, false);
        texture = new DynamicTexture("aetherglass-hud-blur", w, h, false);
        client.getTextureManager().register(ID, texture);
        width = w;
        height = h;
        captures = 0;
    }

    private static void close() {
        if (downscale != null) {
            try {
                downscale.destroyBuffers();
            } catch (Throwable ignored) {
            }
            downscale = null;
        }
    }

    /** Сброс при выходе из мира: мишени живут в контексте окна, их пересоздаём. */
    public static void reset() {
        close();
        texture = null;
        width = 0;
        height = 0;
        captures = 0;
        failed = false;
        lastError = "";
    }
}
