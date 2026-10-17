package com.aetherglass.client.gui.menu;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка атмосферы главного меню без запуска игры: фразы, порядок показа,
 * отсутствие повторов и то, что надписи не заходят на панели интерфейса.
 *
 * <p>Тест живёт в том же пакете, поэтому видит внутренние поля: так проверка
 * не требует никаких дополнительных «тестовых» методов в рабочем коде.</p>
 */
final class MenuAtmosphereTest {

    private static final int WIDTH = 1600;
    private static final int HEIGHT = 900;

    /** Прогоняет {@code steps} кадров по 1/60 секунды и собирает показанные фразы. */
    private static List<String> shown(AtmosphericMessages messages, int steps, int mouseX, int mouseY) {
        List<String> order = new ArrayList<>();
        // Отмечаем именно объекты: каждая новая надпись — это событие показа.
        Set<AtmosphericMessages.Entry> seen =
                Collections.newSetFromMap(new IdentityHashMap<>());
        for (int frame = 0; frame < steps; frame++) {
            messages.update(1.0F / 60.0F, mouseX, mouseY, WIDTH, HEIGHT);
            for (AtmosphericMessages.Entry entry : messages.active) {
                if (seen.add(entry)) {
                    order.add(entry.text);
                }
            }
        }
        return order;
    }

    @Test
    void phrasesAreFixedAndVerbatim() {
        String[] phrases = AtmosphericMessages.PHRASES;
        assertEquals(11, phrases.length, "должно быть ровно одиннадцать фраз");
        assertEquals("привет, мне очень страшно, покушай", phrases[0]);
        assertEquals("пора выйти на улицу, друг", phrases[1]);
        assertEquals("ты сегодня уже пил воду?", phrases[2]);
        assertEquals("ты не обязан успевать всё сразу", phrases[3]);
        assertEquals("мир никуда не убежит", phrases[4]);
        assertEquals("береги себя, ладно?", phrases[5]);
        assertEquals("главное меню переживёт, если ты немного отдохнёшь", phrases[6]);
        assertEquals("покушай, пожалуйста. это системное требование", phrases[7]);
        assertEquals("я маленькая надпись и я верю в тебя", phrases[8]);
        assertEquals("сегодня можно не спасать весь мир", phrases[9]);
        assertEquals("привет. я просто хотел, чтобы ты это прочитал", phrases[10]);
    }

    @Test
    void noPhraseRepeatsUntilAllAreShown() {
        AtmosphericMessages messages = new AtmosphericMessages();
        List<String> order = shown(messages, 60 * 60 * 12, -1000, -1000);

        assertTrue(order.size() >= 22, "за двенадцать минут должно показаться не меньше двух кругов");

        // Гарантия «мешка»: в пределах одного круга из одиннадцати показов
        // повторов нет. Хотя бы один такой круг в выборке обязан найтись.
        boolean fullCycle = false;
        for (int start = 0; start + 11 <= order.size(); start++) {
            Set<String> window = new LinkedHashSet<>(order.subList(start, start + 11));
            if (window.size() == 11) {
                fullCycle = true;
            }
        }
        assertTrue(fullCycle, "не найдено ни одного круга без повторов");

        // И главное: между повторами одной фразы — минимум три показа.
        for (int index = 0; index < order.size(); index++) {
            for (int next = index + 1; next < Math.min(order.size(), index + 4); next++) {
                assertFalse(order.get(index).equals(order.get(next)),
                        "фраза повторилась слишком скоро: " + order.get(index));
            }
        }
        Set<String> all = new HashSet<>(order);
        assertEquals(11, all.size(), "должны быть показаны все одиннадцать фраз");
    }

    @Test
    void neverMoreThanThreeAtOnce() {
        AtmosphericMessages messages = new AtmosphericMessages();
        for (int frame = 0; frame < 60 * 60 * 6; frame++) {
            messages.update(1.0F / 60.0F, WIDTH / 2, HEIGHT / 2, WIDTH, HEIGHT);
            assertTrue(messages.active.size() <= 3, "одновременно больше трёх надписей");
        }
    }

    @Test
    void messagesStayOutOfInterfaceZones() {
        AtmosphericMessages messages = new AtmosphericMessages();
        List<AtmosphericMessages.Zone> zones = List.of(
                new AtmosphericMessages.Zone(200, 120, 360, 300),
                new AtmosphericMessages.Zone(620, 120, 330, 290),
                new AtmosphericMessages.Zone(1200, 20, 300, 26));
        messages.setZones(zones);

        for (int frame = 0; frame < 60 * 60 * 8; frame++) {
            messages.update(1.0F / 60.0F, 900, 500, WIDTH, HEIGHT);
            for (AtmosphericMessages.Entry entry : messages.active) {
                if (entry.textWidth == 0) {
                    // В игре ширину измеряет шрифт; в тесте берём честную оценку — 6 пикселей на символ.
                    entry.textWidth = entry.text.length() * 6;
                }
                if (entry.age < 3.0F) {
                    continue; // надпись ещё «усаживается» на место
                }
                float left = entry.drawX - entry.textWidth / 2.0F - 4.0F;
                float right = entry.drawX + entry.textWidth / 2.0F + 4.0F;
                float top = entry.drawY - 9.0F;
                float bottom = entry.drawY + 9.0F;
                for (AtmosphericMessages.Zone zone : zones) {
                    boolean overlaps = left < zone.x() + zone.width() && right > zone.x()
                            && top < zone.y() + zone.height() && bottom > zone.y();
                    assertFalse(overlaps, "надпись зашла на панель интерфейса: " + entry.text);
                }
                assertTrue(left >= -20.0F && right <= WIDTH + 20.0F, "надпись вышла за пределы экрана");
                assertTrue(top >= -20.0F && bottom <= HEIGHT + 20.0F, "надпись вышла за пределы экрана");
            }
        }
    }

    @Test
    void cursorPushesMessageAwaySlightly() {
        AtmosphericMessages messages = new AtmosphericMessages();
        messages.setZones(List.of());
        int checked = 0;
        int strong = 0;
        float smoothX = 800.0F;
        float smoothY = 450.0F;
        for (int frame = 0; frame < 60 * 60 * 6; frame++) {
            // Курсор «ведёт» первую надпись на расстоянии 40 пикселей: так
            // проверка детерминирована и не зависит от того, где надпись родилась.
            int cursorX = 800;
            int cursorY = 450;
            if (!messages.active.isEmpty()) {
                AtmosphericMessages.Entry followed = messages.active.get(0);
                cursorX = Math.round(followed.drawX) + 40;
                cursorY = Math.round(followed.drawY);
            }
            // Сам класс сглаживает положение курсора; повторяем то же самое,
            // иначе расстояния считались бы до разных точек.
            float ease = 1.0F / 60.0F * 4.0F;
            smoothX += (cursorX - smoothX) * ease;
            smoothY += (cursorY - smoothY) * ease;
            messages.update(1.0F / 60.0F, cursorX, cursorY, WIDTH, HEIGHT);
            // Пока сглаженная позиция не догнала настоящую (курсор «прыгнул»
            // к другой надписи), расстояния считать нельзя.
            boolean converged = Math.abs(smoothX - cursorX) < 2.0F
                    && Math.abs(smoothY - cursorY) < 2.0F;
            if (!converged) {
                continue;
            }
            for (AtmosphericMessages.Entry entry : messages.active) {
                if (entry.age < 4.0F) {
                    continue; // надпись ещё «доезжает» до нужного места
                }
                // Позиция без учёта курсора: дрейф и покачивание.
                float plainX = entry.baseX + entry.drift * entry.age;
                float plainY = entry.baseY + (float) Math.sin(entry.phase) * entry.sway;
                float plainDistance = distance(plainX, plainY, smoothX, smoothY);
                if (plainDistance > 140.0F) {
                    continue; // вне радиуса реакции
                }
                if (plainX < 220.0F || plainX > WIDTH - 220.0F
                        || plainY < 60.0F || plainY > HEIGHT - 60.0F) {
                    continue; // у края экрана работает прижим, а не реакция на курсор
                }
                float targetDistance = distance(entry.targetX, entry.targetY, smoothX, smoothY);
                checked++;
                assertTrue(targetDistance >= plainDistance - 0.5F,
                        "надпись рядом с курсором должна отодвинуться, а не притянуться");
                assertTrue(targetDistance - plainDistance <= 16.0F,
                        "реакция на курсор обязана оставаться небольшой");
                if (plainDistance <= 100.0F) {
                    strong++;
                    assertTrue(targetDistance - plainDistance >= 3.0F,
                            "вблизи курсора сдвиг должен быть заметным");
                }
            }
        }
        assertTrue(checked > 0, "проверка не состоялась: надписи не подходили к курсору");
        assertTrue(strong > 0, "проверка не состоялась: не было ни одного близкого подхода");
    }

    private static float distance(float x, float y, float cursorX, float cursorY) {
        float dx = x - cursorX;
        float dy = y - cursorY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    @Test
    void snowStaysInsideScreen() {
        WinterNight night = new WinterNight();
        night.resize(WIDTH, HEIGHT);
        assertTrue(night.flakes > 20, "снежинок должно быть заметное количество");
        for (int frame = 0; frame < 60 * 120; frame++) {
            night.update(1.0F / 60.0F);
        }
        for (int index = 0; index < night.flakes; index++) {
            assertTrue(night.flakeX[index] >= -10.0F && night.flakeX[index] <= WIDTH + 10.0F,
                    "снежинка ушла по горизонтали");
            assertTrue(night.flakeY[index] >= -10.0F && night.flakeY[index] <= HEIGHT + 10.0F,
                    "снежинка ушла по вертикали");
        }
    }

    @Test
    void snowSurvivesResize() {
        WinterNight night = new WinterNight();
        night.resize(800, 600);
        night.update(1.0F);
        night.resize(WIDTH, HEIGHT);
        assertTrue(night.flakes > 20, "после смены размера снег должен остаться");
        for (int frame = 0; frame < 600; frame++) {
            night.update(1.0F / 60.0F);
        }
        for (int index = 0; index < night.flakes; index++) {
            assertTrue(night.flakeX[index] >= -10.0F && night.flakeX[index] <= WIDTH + 10.0F);
            assertTrue(night.flakeY[index] <= HEIGHT + 10.0F);
        }
    }
}
