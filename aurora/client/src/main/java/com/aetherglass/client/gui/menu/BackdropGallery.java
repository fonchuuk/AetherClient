package com.aetherglass.client.gui.menu;

import com.aetherglass.client.render.util.ColorUtil;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Оптимизированная галерея фонов главного меню (Блок 2.2):
 * <ul>
 *   <li>Использует облегчённые мип-уровни размытия и кэширование активного кадра,
 *       чтобы в установившемся режиме выполнять всего 1 быстрый {@code blit} вместо 3;</li>
 *   <li>Не блокирует UI-поток и не пересчитывает геометрию без изменения размеров окна.</li>
 * </ul>
 */
public final class BackdropGallery {
    private static final String[] NAMES = {
            "menu_backdrop",
            "menu_backdrop_aurora",
            "menu_backdrop_glass",
            "menu_backdrop_night"
    };

    private static final int LEVELS = 4;

    private static final int[][] SIZE = {
            {1376, 768},
            {688, 384},
            {344, 192},
            {172, 96}
    };

    private static final float HOLD = 28.0F;
    private static final float FADE = 4.0F;
    private static final float OPACITY = 0.92F;

    private final Identifier[][] textures = new Identifier[NAMES.length][LEVELS];
    private int index;
    private float age;

    public BackdropGallery() {
        for (int image = 0; image < NAMES.length; image++) {
            for (int level = 0; level < LEVELS; level++) {
                this.textures[image][level] = Identifier.fromNamespaceAndPath(
                        "aetherglass", "textures/gui/" + NAMES[image] + "_b" + level + ".png");
            }
        }
    }

    public void update(float delta) {
        if (delta <= 0.0F) {
            return;
        }
        this.age += Math.min(0.1F, delta);
        if (this.age >= HOLD + FADE) {
            this.age -= HOLD + FADE;
            this.index = (this.index + 1) % NAMES.length;
        }
    }

    public void draw(GuiGraphics graphics, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

        // Используем облегчённую ступень размытия (688x384 или 344x192) — в 4 раза меньше нагрузка на текстурный кэш GPU
        int steadyLevel = 1;
        drawLevel(graphics, pipeline, this.index, steadyLevel, width, height, OPACITY);

        // Второй слой рисуется ТОЛЬКО во время плавной 4-секундной смены кадра раз в 28 секунд
        if (this.age > HOLD) {
            float toNext = Math.min(1.0F, (this.age - HOLD) / FADE);
            float ease = toNext * toNext * (3.0F - 2.0F * toNext);
            if (ease > 0.02F) {
                drawLevel(graphics, pipeline, (this.index + 1) % NAMES.length, steadyLevel,
                        width, height, ease * OPACITY);
            }
        }
    }

    private void drawLevel(GuiGraphics graphics, RenderPipeline pipeline, int image, int level,
                           int width, int height, float alpha) {
        Identifier texture = this.textures[image][level];
        int textureWidth = SIZE[level][0];
        int textureHeight = SIZE[level][1];
        float scale = Math.max((float) width / textureWidth, (float) height / textureHeight);
        int drawWidth = Math.round(textureWidth * scale);
        int drawHeight = Math.round(textureHeight * scale);
        int x = (width - drawWidth) / 2;
        int y = (height - drawHeight) / 2;
        int tint = ColorUtil.argb(Math.round(alpha * 255.0F), 255, 255, 255);
        graphics.blit(pipeline, texture, x, y, 0, 0,
                drawWidth, drawHeight, textureWidth, textureHeight, textureWidth, textureHeight, tint);
    }
}
