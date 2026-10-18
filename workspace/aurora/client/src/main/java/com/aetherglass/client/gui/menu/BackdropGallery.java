package com.aetherglass.client.gui.menu;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.resources.Identifier;

/**
 * Галерея фонов главного меню: четыре картинки, которые медленно размываются
 * и так же медленно сменяют друг друга.
 *
 * <p><b>Размытие считается заранее, а не в игре.</b> Для каждой картинки
 * приготовлено четыре ступени: резкая и три всё более размытых. Ступени
 * лежат в ресурсах готовыми PNG, поэтому в кадре игра не считает ровным
 * счётом ничего — она лишь сводит две соседние ступени. Размытие целой
 * картинки 1376×768 вживую стоило бы десятки миллионов операций на пиксель
 * и заметно подвесило бы меню на входе; здесь этого нет вовсе.</p>
 *
 * <p>Размытые ступени хранятся в меньшем разрешении — 688×384, 344×192 и
 * 172×96. На таком размытии потеря разрешения неразличима, зато все четыре
 * ступени весят меньше двух мегабайт вместо восьми.</p>
 *
 * <p>Порядок показа: картинка появляется резкой, за время показа плавно
 * уходит в размытие, а в последние секунды растворяется в следующей, которая
 * снова приходит резкой. Получается непрерывный круговорот без рывков.</p>
 */
public final class BackdropGallery {

    /** Четыре фона в одной гамме: исходный и три сгенерированных. */
    private static final String[] NAMES = {
            "menu_backdrop",
            "menu_backdrop_aurora",
            "menu_backdrop_glass",
            "menu_backdrop_night"
    };

    /** Сколько ступеней размытия у каждой картинки. */
    private static final int LEVELS = 4;

    /**
     * Размер каждой ступени в пикселях. Должен совпадать с тем, что
     * нагенерировал make_backdrop_levels.py: отрисовка растягивает ступень
     * на весь экран, и неверный размер дал бы обрезанную картинку.
     */
    private static final int[][] SIZE = {
            {1376, 768},
            {688, 384},
            {344, 192},
            {172, 96}
    };

    /** Сколько секунд картинка живёт до начала перехода. */
    private static final float HOLD = 26.0F;

    /** Сколько секунд длится растворение в следующую картинку. */
    private static final float FADE = 5.0F;

    /**
     * Насколько непрозрачна картинка. Чуть меньше единицы, чтобы базовый
     * градиент ночи просвечивал и связывал фон с остальным меню.
     */
    private static final float OPACITY = 0.92F;

    private final Identifier[][] textures = new Identifier[NAMES.length][LEVELS];

    /** Какая картинка сейчас показывается. */
    private int index;

    /** Сколько секунд текущая картинка уже на экране. */
    private float age;

    public BackdropGallery() {
        for (int image = 0; image < NAMES.length; image++) {
            for (int level = 0; level < LEVELS; level++) {
                this.textures[image][level] = Identifier.fromNamespaceAndPath(
                        "aetherglass", "textures/gui/" + NAMES[image] + "_b" + level + ".png");
            }
        }
    }

    /** Продвигает смену картинок. Вызывается раз в кадр. */
    public void update(float delta) {
        if (delta <= 0.0F) {
            return;
        }
        this.age += delta;
        if (this.age >= HOLD + FADE) {
            this.age -= HOLD + FADE;
            this.index = (this.index + 1) % NAMES.length;
        }
    }

    /**
     * Рисует фон: резкую ступень, поверх неё — следующую по размытию с
     * прозрачностью, равной доле пройденного пути, а в конце показа ещё и
     * следующую картинку.
     */
    public void draw(GuiGraphics graphics, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;

        // Доля жизни картинки превращается в номер ступени: 0 → резкая,
        // 1 → самая размытая. Дробная часть — сколько подмешать следующей.
        float life = Math.min(1.0F, this.age / HOLD);
        float position = life * (LEVELS - 1);
        int low = (int) position;
        if (low > LEVELS - 2) {
            low = LEVELS - 2;
        }
        if (low < 0) {
            low = 0;
        }
        float blend = position - low;
        if (blend < 0.0F) {
            blend = 0.0F;
        } else if (blend > 1.0F) {
            blend = 1.0F;
        }

        // Растворение в следующую картинку: сглаженное, чтобы не было стыка.
        float toNext = this.age <= HOLD ? 0.0F : (this.age - HOLD) / FADE;
        if (toNext > 1.0F) {
            toNext = 1.0F;
        }
        float ease = toNext * toNext * (3.0F - 2.0F * toNext);

        drawLevel(graphics, pipeline, this.index, low, width, height, OPACITY);
        if (blend > 0.001F) {
            drawLevel(graphics, pipeline, this.index, low + 1, width, height, blend * OPACITY);
        }
        if (ease > 0.001F) {
            drawLevel(graphics, pipeline, (this.index + 1) % NAMES.length, 0,
                    width, height, ease * OPACITY);
        }
    }

    /**
     * Рисует одну ступень одной картинки в режиме «вписать с обрезкой»:
     * картинка заполняет экран целиком, лишнее уходит за края. Пропорции
     * при этом не искажаются, как было бы при простом растяжении.
     */
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
        // Прозрачность задаётся цветом на конце blit. Оттенок белый, поэтому
        // порядок байтов в цвете неважен: при равных каналах он одинаковый
        // и в ARGB, и в ABGR. Так мы не зависим от того, как именно Minecraft
        // раскладывает этот int.
        int tint = ColorUtil.argb(Math.round(alpha * 255.0F), 255, 255, 255);
        graphics.blit(pipeline, texture, x, y, 0, 0,
                drawWidth, drawHeight, textureWidth, textureHeight, textureWidth, textureHeight, tint);
    }
}
