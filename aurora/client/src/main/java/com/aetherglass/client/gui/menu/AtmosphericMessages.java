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
 * <p><b>Производительность.</b> Физика считается фиксированными шагами
 * {@value #STEP_SECONDS} секунды — двадцать раз в секунду, а не каждый кадр.
 * Отрисовка при этом не теряет плавности: между шагами положение
 * интерполируется, поэтому движение остаётся гладким на любом количестве
 * кадров. Поиск свободного места происходит только в момент появления надписи
 * и стоит в разы дешевле прежнего: раньше на один только подбор места уходило
 * до нескольких тысяч проверок в одном кадре, что и давало заметный фриз
 * каждые несколько секунд.</p>
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

    /** Шаг физики в секундах: 1/20 — двадцать раз в секунду. */
    private static final float STEP_SECONDS = 0.05F;

    /** Защита от «догоняющего» накопителя: не больше трёх шагов за вызов. */
    private static final int MAX_STEPS_PER_FRAME = 3;

    private static final float FADE_IN = 2.6F;
    private static final float FADE_OUT = 3.4F;
    private static final float MIN_LIFE = 15.0F;
    private static final float LIFE_SPREAD = 8.0F;
    private static final float CURSOR_RADIUS = 140.0F;
    private static final float CURSOR_PUSH = 11.0F;
    private static final int TEXT_COLOR = 0xFFDCE7FF;

    /** Насколько быстро надпись догоняет свою цель. Меньше — спокойнее. */
    private static final float FOLLOW = 2.4F;

    private final Random random = new Random();
    final List<Entry> active = new ArrayList<>(MAX_ACTIVE + 1);
    private final int[] bag = new int[PHRASES.length];
    private final List<Zone> zones = new ArrayList<>();

    /** Накопитель времени: остаток до следующего шага физики. */
    private float accumulator;

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

    /**
     * Продвигает жизнь надписей. Время копится, а физика считается шагами:
     * поэтому на мощной машине с сотнями кадров работа не растёт.
     */
    public void update(float delta, int mouseX, int mouseY, int width, int height) {
        if (width <= 0 || height <= 0 || delta <= 0.0F) {
            return;
        }
        // Курсор сглаживается: реакция должна быть мягкой, а не дёрганой.
        float ease = Math.min(1.0F, delta * 4.0F);
        this.cursorX += (mouseX - this.cursorX) * ease;
        this.cursorY += (mouseY - this.cursorY) * ease;

        this.accumulator += delta;
        int steps = 0;
        while (this.accumulator >= STEP_SECONDS && steps < MAX_STEPS_PER_FRAME) {
            this.accumulator -= STEP_SECONDS;
            steps++;
            step(STEP_SECONDS, width, height);
        }
        if (steps == MAX_STEPS_PER_FRAME) {
            // Кадр слишком длинный: выбрасываем остаток, чтобы не копить долг.
            this.accumulator = 0.0F;
        }
    }

    /** Один шаг физики: старение, дрейф, реакция на курсор, выталкивание. */
    private void step(float delta, int width, int height) {
        this.cooldown -= delta;
        if (this.cooldown <= 0.0F && this.active.size() < MAX_ACTIVE) {
            spawn(width, height);
            this.cooldown = 6.5F + this.random.nextFloat() * 7.0F;
        }

        for (int index = this.active.size() - 1; index >= 0; index--) {
            Entry entry = this.active.get(index);
            entry.age += delta;
            if (entry.age >= entry.life) {
                this.active.remove(index);
                continue;
            }

            // Точка интерполяции: откуда надпись начала этот шаг.
            entry.prevX = entry.drawX;
            entry.prevY = entry.drawY;

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

            float follow = Math.min(1.0F, delta * FOLLOW);
            entry.drawX += (entry.targetX - entry.drawX) * follow;
            entry.drawY += (entry.targetY - entry.drawY) * follow;
            entry.alpha = fade(entry);
        }
    }

    /**
     * Рисует надписи. Пластина под текстом появляется примерно в половине
     * случаев. Положение интерполируется между шагами физики, поэтому
     * движение остаётся плавным и на двухстах кадрах в секунду.
     */
    public void render(GuiGraphics graphics, Font font) {
        if (this.active.isEmpty()) {
            return;
        }
        int lineHeight = font.lineHeight;
        // Доля, пройденная внутри текущего шага: 0 — только что был шаг, 1 — вот-вот.
        float blend = Math.min(1.0F, this.accumulator / STEP_SECONDS);
        for (int index = 0; index < this.active.size(); index++) {
            Entry entry = this.active.get(index);
            int alpha = Math.round(entry.alpha * 230.0F);
            if (alpha <= 2) {
                continue;
            }
            // Ширина текста измеряется один раз: фраза за надписью закреплена.
            if (entry.textWidth <= 0) {
                entry.textWidth = font.width(entry.text);
            }
            int textWidth = entry.textWidth;
            float x = entry.prevX + (entry.drawX - entry.prevX) * blend - textWidth / 2.0F;
            float y = entry.prevY + (entry.drawY - entry.prevY) * blend - lineHeight / 2.0F;
            int px = Math.round(x);
            int py = Math.round(y);
            if (entry.plate) {
                // Стеклянная пластина: кромка светлее, внутри почти прозрачно.
                DrawUtil.fillRounded(graphics, px - 11, py - 7, textWidth + 22, lineHeight + 14, 9,
                        ColorUtil.argb(Math.round(alpha * 0.22F), 190, 208, 255));
                DrawUtil.fillRounded(graphics, px - 10, py - 6, textWidth + 20, lineHeight + 12, 8,
                        ColorUtil.argb(Math.round(alpha * 0.10F), 150, 174, 240));
            }
            // Мягкое свечение: один слой под текстом вместо прежних трёх.
            DrawUtil.drawGlow(graphics, px - 4, py - 2, textWidth + 8, lineHeight + 4, 6,
                    ColorUtil.argb(Math.round(alpha * 0.26F), 120, 150, 230), 0.7F);
            graphics.drawString(font, entry.text, px, py, ColorUtil.withAlpha(TEXT_COLOR, alpha), false);
        }
    }

    /** Убирает все надписи — используется при закрытии экрана. */
    public void clear() {
        this.active.clear();
        this.cooldown = 1.4F;
        this.accumulator = 0.0F;
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
     *
     * <p>Поиск короткий: четырнадцать попыток вместо сорока, а проверка пути
     * делает четыре замера вместо четырнадцати. Раньше на появление одной
     * надписи уходило до нескольких тысяч проверок пересечений в одном кадре,
     * что и давало заметный фриз; теперь их меньше сотни.</p>
     */
    private void spawn(int width, int height) {
        int phrase = nextPhrase();
        Entry entry = new Entry();
        entry.text = PHRASES[phrase];
        entry.textWidth = entry.text.length() * 6; // оценка; шрифт уточнит при отрисовке
        entry.life = MIN_LIFE + this.random.nextFloat() * LIFE_SPREAD;
        // Дрейф заметно тише прежнего: надпись скорее парит, чем ползёт.
        entry.drift = (this.random.nextBoolean() ? 1.0F : -1.0F) * (1.2F + this.random.nextFloat() * 2.6F);
        entry.sway = 3.0F + this.random.nextFloat() * 4.5F;
        entry.swaySpeed = 0.16F + this.random.nextFloat() * 0.20F;
        entry.phase = this.random.nextFloat() * 6.2831853F;
        entry.plate = this.random.nextFloat() < 0.45F;

        float bestX = width * 0.5F;
        float bestY = height * 0.5F;
        float bestScore = Float.MAX_VALUE;
        for (int attempt = 0; attempt < 14; attempt++) {
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
        entry.prevX = bestX;
        entry.prevY = bestY;
        this.active.add(entry);
    }

    /**
     * Насколько путь надписи задевает зоны интерфейса. Ноль — путь свободен.
     *
     * <p>Проверяется вся полоса, которую надпись пройдёт за время жизни, с
     * запасом на покачивание вверх и вниз. Покачивание учитывается одной
     * расширенной полосой, а не двумя отдельными замерами: точность та же,
     * работы вдвое меньше.</p>
     */
    private float pathScore(Entry entry, float x, float y, int width, int height) {
        float travel = entry.drift * entry.life;
        float from = Math.min(0.0F, travel);
        float to = Math.max(0.0F, travel);
        float savedX = entry.targetX;
        float savedY = entry.targetY;
        float savedHalfHeight = entry.pathHalfHeight;
        // Полоса выше и ниже центра: покрывает оба крайних покачивания сразу.
        entry.pathHalfHeight = 12.0F + entry.sway;
        float total = 0.0F;
        for (int step = 0; step <= 3; step++) {
            entry.targetX = x + from + (to - from) * step / 3.0F;
            entry.targetY = y;
            total += overlap(entry);
        }
        entry.targetX = savedX;
        entry.targetY = savedY;
        entry.pathHalfHeight = savedHalfHeight;

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
     *
     * <p>Перебор укорочен вдвое против прежнего и останавливается сразу, как
     * только найдено свободное место. В обычном кадре метод вообще не делает
     * работы: надписи почти всегда свободны, и срабатывает быстрый выход.</p>
     */
    private void settleFree(Entry entry, int width, int height) {
        if (overlap(entry) <= 0.0F) {
            return;
        }
        float startX = entry.targetX;
        float startY = entry.targetY;
        float bestY = startY;
        float best = Float.MAX_VALUE;
        for (int step = 1; step <= 13; step++) {
            int offset = step * 20;
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
        float halfHeight = entry.pathHalfHeight > 0.0F ? entry.pathHalfHeight : 12.0F;
        float left = entry.targetX - entry.textWidth / 2.0F - 10.0F;
        float right = entry.targetX + entry.textWidth / 2.0F + 10.0F;
        float top = entry.targetY - halfHeight;
        float bottom = entry.targetY + halfHeight;
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

        /** Положение на начале текущего шага физики — для интерполяции. */
        float prevX;
        float prevY;

        /** Полувысота полосы при проверке пути; ноль — считать как обычно. */
        float pathHalfHeight;

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
