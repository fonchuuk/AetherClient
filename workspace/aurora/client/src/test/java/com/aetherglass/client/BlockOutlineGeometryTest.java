package com.aetherglass.client;

import com.aetherglass.client.render.block.BlockOutlineGeometry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Проверка ключа ребра: именно он отвечает за то, что общее ребро двух соседних
 * параллелепипедов формы не будет нарисовано дважды.
 */
final class BlockOutlineGeometryTest {
    @Test
    @DisplayName("ребро не зависит от порядка точек")
    void edgeKeyIsOrderIndependent() {
        long forward = BlockOutlineGeometry.edgeKey(0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D);
        long backward = BlockOutlineGeometry.edgeKey(1.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        assertEquals(forward, backward);
    }

    @Test
    @DisplayName("разные рёбра дают разные ключи")
    void differentEdgesProduceDifferentKeys() {
        long bottom = BlockOutlineGeometry.edgeKey(0.0D, 0.0D, 0.0D, 1.0D, 0.0D, 0.0D);
        long top = BlockOutlineGeometry.edgeKey(0.0D, 1.0D, 0.0D, 1.0D, 1.0D, 0.0D);
        long side = BlockOutlineGeometry.edgeKey(0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.0D);
        assertNotEquals(bottom, top);
        assertNotEquals(bottom, side);
        assertNotEquals(top, side);
    }

    @Test
    @DisplayName("совпадающие рёбра с микроскопической погрешностью склеиваются")
    void nearlyIdenticalEdgesCollapse() {
        long exact = BlockOutlineGeometry.edgeKey(0.5D, 0.5D, 0.0D, 0.5D, 0.5D, 1.0D);
        long jitter = BlockOutlineGeometry.edgeKey(0.5D + 1.0E-9D, 0.5D - 1.0E-9D, 0.0D,
                0.5D, 0.5D, 1.0D + 1.0E-9D);
        assertEquals(exact, jitter, "погрешность меньше тысячной доли блока не должна плодить рёбра");
    }

    @Test
    @DisplayName("координаты вне диапазона блока обрезаются, а не ломают ключ")
    void outOfRangeCoordinatesAreClamped() {
        long negative = BlockOutlineGeometry.edgeKey(-5.0D, -5.0D, -5.0D, 0.0D, 0.0D, 0.0D);
        long clamped = BlockOutlineGeometry.edgeKey(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
        assertEquals(clamped, negative);

        long huge = BlockOutlineGeometry.edgeKey(50.0D, 50.0D, 50.0D, 0.0D, 0.0D, 0.0D);
        long atLimit = BlockOutlineGeometry.edgeKey(1.023D, 1.023D, 1.023D, 0.0D, 0.0D, 0.0D);
        assertEquals(atLimit, huge);
    }
}
