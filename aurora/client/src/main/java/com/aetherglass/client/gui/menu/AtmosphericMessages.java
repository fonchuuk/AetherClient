package com.aetherglass.client.gui.menu;

import com.aetherglass.client.gui.render.DrawUtil;
import com.aetherglass.client.render.util.ColorUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Атмосферные надписи главного меню: одиннадцать фраз, которые медленно
 * всплывают, дрейфуют, реагируют на курсор и так же медленно гаснут.
 *
 * <p>Правила, заложенные в класс:
 * <ul>
 *   <li>фразы заданы дословно и не переводятся;</li>
 *   <li>показывается не больше трёх одновременно;</li>
 *   <li>порядок берётся из «мешка»: пока не показаны все одиннадцать фраз,
 *       повторa не будет;</li>
 *   <li>надписи никогда не заходят на зоны интерфейса (панели и кнопки):
 *       при пересечении они мягко выталкиваются;</li>
 *   <li>никаких настроек: включить, выключить, изменить количество или
 *       прозрачность нельзя — это часть атмосферы, а не опция.</li>
 * </ul>
 *
 * <p>Объекты создаются только в момент появления новой надписи — по одной
 * штуке примерно в семь секунд. В самом кадре allocations нет.</p>
 */
public final class AtmosphericMessages {

    /** Одиннадцать фраз, заданных автором. Менять формулировки нельзя. */
    static final String[] PHRASES = {
            "привет, мне очень страшно, покушай",
            "пора выйти на улицу, друг",
            "ты сегодня уже пил воду?",
            "ты не обязан успевать всё сразу",
            "мир никуда не убежит",
            "береги себя, ладно?",
            "главное меню переживёт, если ты немного отдохнёшь",
            "покушай, пожалуйста. это системное требование",
            "я маленькая надпись и я верю в тебя",
            "сегодня можно не спасать весь мир",
            "привет. я просто хотел, чтобы ты это прочитал"
    };

    /** Прямоугольник интерфейса, куда надписям заходить нельзя. */
    public record Zone(int x, int y, int width, int height) {
    }

    private static final int MAX_ACTIVE = 3;
    private static final float FADE_IN = 2.4F;
    private static final float FADE_OUT = 3.2F;
    private static final float MIN_LIFE = 13.0F;
    private static final float LIFE_SPREAD = 7.0F;
    private static final float CURSOR_RADIUS = 150.0F;
    private static final float CURSOR_PUSH = 13.0F;
    private static final int TEXT_COLOR = 0xFFDCE7FF;

    private final Random random = new Random();
    final List<Entry> active = new ArrayList<>(MAX_ACTIVE + 1);
    private final int[] bag = new int[PHRASES.length];
    private final List<Zone> zones = new ArrayList<>();

    /** Индекс в мешке: {@code length} означает «мешок пуст, пора набрать новый». */
    private int bagIndex = PHRASES.length;

    /**
     * Минимальный разрыв между повторами одной фразы, в показах. Четыре
     * показа — это примерно минута: фраза успевает забыться.
     */
    private static final int MIN_GAP = 4;

    /** Кольцевая история показов: нужна, чтобы новый круг не начинался повтором. */
    private static final int HISTORY = MIN_GAP;
    private final int[] history = new int[HISTORY];
    private int historyCount;

    /** Вспомогательные буферы набора нового круга: без выделений в кадре. */
    private final int[] candidates = new int[PHRASES.length];
    private final boolean[] used = new boolean[PHRASES.length];

    private float cooldown = 1.4F;
    private float cursorX = -10_000.0F;
    private float cursorY = -10_000.0F;

    /** Задаёт зоны, в которых надписи появляться не должны. */
    public void setZones(List<Zone> newZones) {
        this.zones.clear();
        this.zones.addAll(newZones);
    }

    /** Продвигает жизнь надписей и реакцию на курсор. */
    public void update(float delta, int mouseX, int mouseY, int width, int height) {
        if (width <= 0 || height <= 0 || delta <= 0.0F) {
            return;
        }
        // Курсор сглаживается: реакция должна быть мягкой, а не дёрганой.
        float ease = Math.min(1.0F, delta * 4.0F);
        this.cursorX += (mouseX - this.cursorX) * ease;
        this.cursorY += (mouseY - this.cursorY) * ease;

        this.cooldown -= delta;
        if (this.cooldown <= 0.0F && this.active.size() < MAX_ACTIVE) {
            spawn(width, height);
            this.cooldown = 5.5F + this.random.nextFloat() * 6.0F;
        }

        for (int index = this.active.size() - 1; index >= 0; index--) {
            Entry entry = this.active.get(index);
            entry.age += delta;
            if (entry.age >= entry.life) {
                this.active.remove(index);
                continue;
            }
            entry.phase += delta * entry.swaySpeed;
            entry.targetX = entry.baseX + entry.drift * entry.age;
            entry.targetY = entry.baseY + (float) Math.sin(entry.phase) * entry.sway;

            float plainX = entry.targetX;
            float plainY = entry.targetY;

            // Реакция на курсор: надпись чуть отодвигается, но не убегает.
            float dx = entry.targetX - this.cursorX;
            float dy = entry.targetY - this.cursorY;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            if (distance < CURSOR_RADIUS && distance > 0.001F) {
                float force = (1.0F - distance / CURSOR_RADIUS);
                entry.targetX += dx / distance * force * CURSOR_PUSH;
                entry.targetY += dy / distance * force * CURSOR_PUSH;
            }

            // Выталкивание из зон интерфейса: по ближайшей оси, трёх проходов хватает.
            resolveZones(entry, width, height);
            settleFree(entry, width, height);
            if (overlap(entry) > 0.0F) {
                // Курсор не смеет заталкивать надпись под панель: если после
                // реакции она всё равно перекрыта, возвращаем как было.
                entry.targetX = plainX;
                entry.targetY = plainY;
            }

            float follow = Math.min(1.0F, delta * 3.2F);
            entry.drawX += (entry.targetX - entry.drawX) * follow;
            entry.drawY += (entry.targetY - entry.drawY) * follow;
            entry.alpha = fade(entry);
        }
    }

    /** Рисует надписи. Пластина под текстом появляется примерно в половине случаев. */
    public void render(GuiGraphics graphics, Font font) {
        int lineHeight = font.lineHeight;
        for (int index = 0; index < this.active.size(); index++) {
            Entry entry = this.active.get(index);
            int alpha = Math.round(entry.alpha * 230.0F);
            if (alpha <= 2) {
                continue;
            }
            int textWidth = font.width(entry.text);
            entry.textWidth = textWidth;
            int x = Math.round(entry.drawX - textWidth / 2.0F);
            int y = Math.round(entry.drawY - lineHeight / 2.0F);
            if (entry.plate) {
                // Стеклянная пластина: кромка светлее, внутри почти прозрачно.
                DrawUtil.fillRounded(graphics, x - 11, y - 7, textWidth + 22, lineHeight + 14, 9,
                        ColorUtil.argb(Math.round(alpha * 0.22F), 190, 208, 255));
                DrawUtil.fillRounded(graphics, x - 10, y - 6, textWidth + 20, lineHeight + 12, 8,
                        ColorUtil.argb(Math.round(alpha * 0.10F), 150, 174, 240));
            }
            // Мягкое свечение: три слоя под текстом заменяют размытие.
            DrawUtil.drawGlow(graphics, x - 4, y - 2, textWidth + 8, lineHeight + 4, 6,
                    ColorUtil.argb(Math.round(alpha * 0.30F), 120, 150, 230), 0.7F);
            graphics.drawString(font, entry.text, x, y, ColorUtil.withAlpha(TEXT_COLOR, alpha), false);
        }
    }

    /** Убирает все надписи — используется при закрытии экрана. */
    public void clear() {
        this.active.clear();
        this.cooldown = 1.4F;
    }

    // ─────────────────────────── внутреннее ───────────────────────────

    /** Прозрачность: плавный вход, долгая пауза, плавный выход. */
    private static float fade(Entry entry) {
        float left = entry.life - entry.age;
        if (entry.age < FADE_IN) {
            float t = entry.age / FADE_IN;
            return t * t * (3.0F - 2.0F * t);
        }
        if (left < FADE_OUT) {
            float t = Math.max(0.0F, left / FADE_OUT);
            return t * t * (3.0F - 2.0F * t);
        }
        return 1.0F;
    }

    /** Сдвигает целевую точку надписи прочь из зон интерфейса и за пределы экрана. */
    private void resolveZones(Entry entry, int width, int height) {
        int halfWidth = Math.round(entry.textWidth / 2.0F) + 14;
        int halfHeight = 12;
        for (int pass = 0; pass < 3; pass++) {
            boolean moved = false;
            for (int index = 0; index < this.zones.size(); index++) {
                Zone zone = this.zones.get(index);
                float left = zone.x() - halfWidth;
                float right = zone.x() + zone.width() + halfWidth;
                float top = zone.y() - halfHeight;
                float bottom = zone.y() + zone.height() + halfHeight;
                if (entry.targetX <= left || entry.targetX >= right
                        || entry.targetY <= top || entry.targetY >= bottom) {
                    continue;
                }
                // Выход по ближайшей стороне: так сдвиг получается минимальным.
                float toLeft = entry.targetX - left;
                float toRight = right - entry.targetX;
                float toTop = entry.targetY - top;
                float toBottom = bottom - entry.targetY;
                float smallest = Math.min(Math.min(toLeft, toRight), Math.min(toTop, toBottom));
                if (smallest == toLeft) {
                    entry.targetX = left;
                } else if (smallest == toRight) {
                    entry.targetX = right;
                } else if (smallest == toTop) {
                    entry.targetY = top;
                } else {
                    entry.targetY = bottom;
                }
                moved = true;
            }
            if (!moved) {
                break;
            }
        }
        entry.targetX = Math.min(width - halfWidth, Math.max(halfWidth, entry.targetX));
        entry.targetY = Math.min(height - halfHeight, Math.max(halfHeight, entry.targetY));
    }

    /**
     * Создаёт новую надпись. Место подбирается так, чтобы весь её путь —
     * от рождения до исчезновения вместе с горизонтальным дрейфом — проходил
     * вне панелей: тогда надписи не приходится «убегать» от интерфейса.
     */
    private void spawn(int width, int height) {
        int phrase = nextPhrase();
        Entry entry = new Entry();
        entry.text = PHRASES[phrase];
        entry.textWidth = entry.text.length() * 6; // оценка; шрифт уточнит при отрисовке
        entry.life = MIN_LIFE + this.random.nextFloat() * LIFE_SPREAD;
        entry.drift = (this.random.nextBoolean() ? 1.0F : -1.0F) * (2.5F + this.random.nextFloat() * 5.5F);
        entry.sway = 4.0F + this.random.nextFloat() * 7.0F;
        entry.swaySpeed = 0.25F + this.random.nextFloat() * 0.35F;
        entry.phase = this.random.nextFloat() * 6.2831853F;
        entry.plate = this.random.nextFloat() < 0.45F;

        float bestX = width * 0.5F;
        float bestY = height * 0.5F;
        float bestScore = Float.MAX_VALUE;
        for (int attempt = 0; attempt < 40; attempt++) {
            float x = width * (0.12F + 0.76F * this.random.nextFloat());
            float y = height * (0.14F + 0.68F * this.random.nextFloat());
            float score = pathScore(entry, x, y, width, height);
            if (score < bestScore) {
                bestScore = score;
                bestX = x;
                bestY = y;
            }
            if (bestScore <= 0.0F) {
                break;
            }
        }
        entry.baseX = bestX;
        entry.baseY = bestY;
        entry.targetX = bestX;
        entry.targetY = bestY;
        entry.drawX = bestX;
        entry.drawY = bestY;
        this.active.add(entry);
    }

    /**
     * Насколько путь надписи задевает зоны интерфейса. Ноль — путь свободен:
     * проверяется не одна точка, а вся полоса, которую надпись пройдёт за
     * время жизни, с запасом на покачивание вверх и вниз.
     */
    private float pathScore(Entry entry, float x, float y, int width, int height) {
        float travel = entry.drift * entry.life;
        float from = Math.min(0.0F, travel);
        float to = Math.max(0.0F, travel);
        float savedX = entry.targetX;
        float savedY = entry.targetY;
        float total = 0.0F;
        for (int step = 0; step <= 6; step++) {
            entry.targetX = x + from + (to - from) * step / 6.0F;
            for (int sway = -1; sway <= 1; sway += 2) {
                entry.targetY = y + sway * entry.sway;
                total += overlap(entry);
            }
        }
        entry.targetX = savedX;
        entry.targetY = savedY;

        // Весь путь должен лежать внутри экрана: иначе надпись упрется в край
        // и её придётся прижимать на ходу. Штраф proportional величине выхода.
        float marginX = entry.textWidth / 2.0F + 24.0F;
        float marginY = entry.sway + 44.0F;
        total += Math.max(0.0F, marginX - (x + from)) * 60.0F;
        total += Math.max(0.0F, (x + to) - (width - marginX)) * 60.0F;
        total += Math.max(0.0F, marginY - y) * 60.0F;
        total += Math.max(0.0F, y - (height - marginY)) * 60.0F;
        return total;
    }

    /**
     * Доводит надпись до свободного места, если выталкивания оказалось мало.
     *
     * <p>Длинная фраза физически не помещается в узкий проём между двумя
     * панелями: вытолкнуть её влево — попадёт на левую панель, вправо — на
     * правую. Поэтому, если после выталкивания надпись всё ещё чем-то
     * перекрыта, место ищется по вертикали: панели стоят колонками, а сверху
     * и снизу от них почти всегда есть свободное пространство.</p>
     */
    private void settleFree(Entry entry, int width, int height) {
        if (overlap(entry) <= 0.0F) {
            return;
        }
        float startX = entry.targetX;
        float startY = entry.targetY;
        float bestY = startY;
        float best = Float.MAX_VALUE;
        for (int step = 1; step <= 26; step++) {
            int offset = step * 16;
            for (int sign = -1; sign <= 1; sign += 2) {
                float candidate = startY + sign * offset;
                if (candidate < 34.0F || candidate > height - 34.0F) {
                    continue;
                }
                entry.targetY = candidate;
                float amount = overlap(entry);
                if (amount < best) {
                    best = amount;
                    bestY = candidate;
                }
            }
            if (best <= 0.0F) {
                break;
            }
        }
        entry.targetX = startX;
        entry.targetY = bestY;
    }

    /** Суммарная площадь пересечения надписи с зонами интерфейса. */
    private float overlap(Entry entry) {
        float left = entry.targetX - entry.textWidth / 2.0F - 10.0F;
        float right = entry.targetX + entry.textWidth / 2.0F + 10.0F;
        float top = entry.targetY - 12.0F;
        float bottom = entry.targetY + 12.0F;
        float total = 0.0F;
        for (int index = 0; index < this.zones.size(); index++) {
            Zone zone = this.zones.get(index);
            float overlapWidth = Math.min(right, zone.x() + zone.width()) - Math.max(left, zone.x());
            float overlapHeight = Math.min(bottom, zone.y() + zone.height()) - Math.max(top, zone.y());
            if (overlapWidth > 0.0F && overlapHeight > 0.0F) {
                total += overlapWidth * overlapHeight;
            }
        }
        return total;
    }

    /** Следующая фраза из «мешка». Пока круг не пройден, повторов нет. */
    private int nextPhrase() {
        if (this.bagIndex >= this.bag.length) {
            refillBag();
        }
        int phrase = this.bag[this.bagIndex++];
        this.history[this.historyCount % HISTORY] = phrase;
        this.historyCount++;
        return phrase;
    }

    /**
     * Набирает новый круг из всех одиннадцати фраз.
     *
     * <p>Места заполняются по одному: на каждое место годится только та фраза,
     * которая не звучала слишком недавно. Поэтому минимальный разрыв между
     * повторами соблюдается всегда, а не «в среднем».</p>
     */
    private void refillBag() {
        int length = this.bag.length;
        for (int index = 0; index < length; index++) {
            this.used[index] = false;
        }
        for (int position = 0; position < length; position++) {
            int count = 0;
            for (int candidate = 0; candidate < length; candidate++) {
                if (!this.used[candidate] && !tooRecent(candidate, position)) {
                    this.candidates[count++] = candidate;
                }
            }
            if (count == 0) {
                // Недостижимо: запретов не больше трёх, а фраз одиннадцать.
                for (int candidate = 0; candidate < length; candidate++) {
                    if (!this.used[candidate]) {
                        this.candidates[count++] = candidate;
                    }
                }
            }
            int pick = this.candidates[this.random.nextInt(count)];
            this.used[pick] = true;
            this.bag[position] = pick;
        }
        this.bagIndex = 0;
    }

    /**
     * Не звучала ли фраза слишком недавно для места {@code position} нового круга.
     *
     * <p>Фраза, показанная {@code age} показов назад, окажется от прошлого
     * показа на расстоянии {@code position + age}: именно столько других
     * надписей увидит игрок между повторами.</p>
     */
    private boolean tooRecent(int candidate, int position) {
        int known = Math.min(this.historyCount, HISTORY);
        for (int age = 1; age <= known; age++) {
            int past = this.history[(this.historyCount - age) % HISTORY];
            if (past == candidate && position + age < MIN_GAP) {
                return true;
            }
        }
        return false;
    }

    /** Одна живая надпись. Поля изменяемые: объект существует десятки кадров. */
    static final class Entry {
        String text = "";
        int textWidth;
        float baseX;
        float baseY;
        float targetX;
        float targetY;
        float drawX;
        float drawY;
        float drift;
        float sway;
        float swaySpeed;
        float phase;
        float age;
        float life;
        float alpha;
        boolean plate;
    }
}
