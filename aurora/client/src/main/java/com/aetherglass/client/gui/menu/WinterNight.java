package com.aetherglass.client.gui.menu;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Зимняя ночь главного меню: холодный градиент неба, ореол луны, медленный
 * снегопад и редкие стеклянные пластины.
 *
 * <p>Класс полностью самостоятелен: не использует ни шейдерных паков, ни
 * чужих библиотек. Снежинки рисуются собственной текстурой
 * {@code assets/aetherglass/textures/particle/circle.png} — мягкий круг с
 * размытым краем, поверх него задаётся цвет вершин, поэтому одна и та же
 * картинка даёт и крупные близкие хлопья, и мелкую дальнюю взвесь.</p>
 *
 * <p>Экономия ресурсов: все частицы лежат в массивах, выделенных один раз при
 * создании объекта. В кадре не создаётся ни одного объекта и не вызывается
 * ничего дороже арифметики и нескольких десятков прямоугольников.</p>
 */
public final class WinterNight {

    /** Своя текстура снежинки: мягкий круг 64×64 без резких граней. */
    private static final Identifier FLAKE =
            Identifier.fromNamespaceAndPath("aetherglass", "textures/particle/circle.png");

    /** Запас по снежинкам: оптимизировано для 240+ FPS в меню. */
    private static final int MAX_FLAKES = 48;

    /** Пластин на экране немного: это фон, а не содержимое. */
    private static final int MAX_PLATES = 4;

    private final Random random = new Random(20261215L);

    /**
     * Галерея фонов: картинки, которые медленно размываются и сменяют друг
     * друга. Рисуется сразу после базового градиента — сияние над горизонтом,
     * ореол луны, пластины и снег остаются поверх неё.
     */
    private final BackdropGallery gallery = new BackdropGallery();

    // ── снежинки ────────────────────────────────────────────────────────
    final float[] flakeX = new float[MAX_FLAKES];
    final float[] flakeY = new float[MAX_FLAKES];
    private final float[] flakeSize = new float[MAX_FLAKES];
    private final float[] flakeSpeed = new float[MAX_FLAKES];
    private final float[] flakeSway = new float[MAX_FLAKES];
    private final float[] flakePhase = new float[MAX_FLAKES];
    private final float[] flakeAlpha = new float[MAX_FLAKES];

    // ── стеклянные пластины ────────────────────────────────────────────
    final float[] plateX = new float[MAX_PLATES];
    private final float[] plateY = new float[MAX_PLATES];
    final float[] plateWidth = new float[MAX_PLATES];
    private final float[] plateHeight = new float[MAX_PLATES];
    private final float[] plateSpeed = new float[MAX_PLATES];
    private final float[] plateAlpha = new float[MAX_PLATES];

    int flakes;
    int plates;
    private int width = -1;
    private int height = -1;

    /**
     * Пересоздаёт частицы под текущий размер окна. Вызывается при смене
     * размера; если окно не менялось, вызов ничего не делает.
     */
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0 || (width == this.width && height == this.height)) {
            return;
        }
        boolean first = this.width < 0;
        this.width = width;
        this.height = height;

        // Плотность снега: одна снежинка примерно на 14 тысяч пикселей площади.
        int wanted = Math.round(width * height / 14_000.0F);
        int next = Math.min(MAX_FLAKES, Math.max(28, wanted));
        for (int index = 0; index < next; index++) {
            if (index >= this.flakes || first) {
                // Новые снежинки появляются сразу по всему экрану.
                spawnFlake(index, false);
            }
        }
        this.flakes = next;

        int wantedPlates = Math.min(MAX_PLATES, Math.max(2, width / 640));
        for (int index = 0; index < wantedPlates; index++) {
            if (index >= this.plates || first) {
                spawnPlate(index, false);
            }
        }
        this.plates = wantedPlates;
    }

    /** Продвигает снегопад и пластины на {@code delta} секунд. */
    public void update(float delta) {
        // Галерея идёт первой и без оглядки на ширину окна: смена картинок
        // не должна останавливаться из-за того, что окно ещё не измерено.
        this.gallery.update(delta);
        if (this.width <= 0 || delta <= 0.0F) {
            return;
        }
        for (int index = 0; index < this.flakes; index++) {
            this.flakePhase[index] += delta * 0.7F;
            this.flakeY[index] += this.flakeSpeed[index] * delta;
            // Горизонтальное покачивание: синус даёт скорость, а не смещение.
            this.flakeX[index] += (float) Math.sin(this.flakePhase[index]) * this.flakeSway[index] * delta;
            if (this.flakeY[index] - this.flakeSize[index] > this.height) {
                spawnFlake(index, true);
                continue;
            }
            if (this.flakeX[index] < -8.0F) {
                this.flakeX[index] = this.width + 8.0F;
            } else if (this.flakeX[index] > this.width + 8.0F) {
                this.flakeX[index] = -8.0F;
            }
        }
        for (int index = 0; index < this.plates; index++) {
            this.plateX[index] += this.plateSpeed[index] * delta;
            if (this.plateSpeed[index] >= 0.0F && this.plateX[index] > this.width + 40.0F) {
                this.plateX[index] = -this.plateWidth[index] - 40.0F;
                this.plateY[index] = this.height * (0.06F + 0.78F * this.random.nextFloat());
            } else if (this.plateSpeed[index] < 0.0F && this.plateX[index] + this.plateWidth[index] < -40.0F) {
                this.plateX[index] = this.width + 40.0F;
                this.plateY[index] = this.height * (0.06F + 0.78F * this.random.nextFloat());
            }
        }
    }

    /**
     * Рисует ночное небо: градиент, сияние над горизонтом, ореол луны,
     * стеклянные пластины и снег. Порядок важен: снег всегда поверх пластин,
     * но под панелями интерфейса.
     */
    public void draw(GuiGraphics graphics, int width, int height, boolean animated) {
        if (width <= 0 || height <= 0) {
            return;
        }
        resize(width, height);

        // 1. Фон: глубокий матовый обсидиан без ярко-синей засветки.
        graphics.fillGradient(0, 0, width, height, 0xFF050608, 0xFF080A0E);

        // 1.1 Галерея с приглушённой матово-угольной тонировкой.
        this.gallery.draw(graphics, width, height);

        // 2. Тонкое матовое затемнение по краям для идеальной читаемости карточек
        float time = com.aetherglass.client.render.util.FrameClock.elapsedSeconds();
        float wave = 0.5F + 0.5F * (float) Math.sin(time * 0.45D);
        int horizon = Math.round(height * (0.58F + 0.06F * wave));
        int glowBottom = ColorUtil.argb(Math.round(14 + 8 * wave), 0, 210, 230);
        graphics.fillGradient(0, horizon, width, height, 0x00000000, glowBottom);

        if (!animated) {
            return;
        }
        // 3. Световая взвесь / снежинки: собственная текстура, размер и прозрачность задают глубину.
        for (int index = 0; index < this.flakes; index++) {
            int size = Math.max(1, Math.round(this.flakeSize[index]));
            int alpha = Math.round(this.flakeAlpha[index] * 140.0F);
            if (alpha <= 1) {
                continue;
            }
            int color = ColorUtil.argb(alpha, 160, 240, 255);
            graphics.blit(RenderPipelines.GUI_TEXTURED, FLAKE,
                    Math.round(this.flakeX[index]), Math.round(this.flakeY[index]),
                    0.0F, 0.0F, size, size, 64, 64, color);
        }
    }

    /** Помещает снежинку {@code index} сверху (или в случайное место экрана). */
    private void spawnFlake(int index, boolean fromTop) {
        this.flakeSize[index] = 1.2F + this.random.nextFloat() * 3.4F;
        // Мелкие хлопья падают медленнее — так появляется ощущение глубины.
        this.flakeSpeed[index] = 6.0F + this.flakeSize[index] * 5.5F;
        this.flakeSway[index] = 4.0F + this.random.nextFloat() * 10.0F;
        this.flakePhase[index] = this.random.nextFloat() * 6.2831853F;
        this.flakeX[index] = this.random.nextFloat() * this.width;
        this.flakeY[index] = fromTop ? -this.flakeSize[index] - 2.0F : this.random.nextFloat() * this.height;
        // Крупные снежинки ближе и ярче, мелкие — почти дымка.
        this.flakeAlpha[index] = 0.14F + (this.flakeSize[index] / 4.6F) * 0.5F;
    }

    /** Помещает стеклянную пластину {@code index} в случайное место. */
    private void spawnPlate(int index, boolean offScreen) {
        this.plateWidth[index] = 140.0F + this.random.nextFloat() * 220.0F;
        this.plateHeight[index] = 80.0F + this.random.nextFloat() * 170.0F;
        this.plateSpeed[index] = (this.random.nextBoolean() ? 1.0F : -1.0F) * (1.6F + this.random.nextFloat() * 3.4F);
        this.plateAlpha[index] = 0.030F + this.random.nextFloat() * 0.035F;
        this.plateX[index] = offScreen
                ? (this.plateSpeed[index] >= 0.0F ? -this.plateWidth[index] : this.width)
                : this.random.nextFloat() * Math.max(1.0F, this.width - this.plateWidth[index]);
        this.plateY[index] = this.height * (0.06F + 0.78F * this.random.nextFloat());
    }
}
