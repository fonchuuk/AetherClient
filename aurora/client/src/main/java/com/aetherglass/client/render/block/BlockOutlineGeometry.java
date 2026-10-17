package com.aetherglass.client.render.block;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Реальная геометрия блока: рёбра и составляющие параллелепипеды его формы.
 *
 * <p>Почему это отдельный класс: форма блока — это не всегда куб. Ступенька,
 * плита, забор, лестница, стекло и редстоун имеют разные наборы
 * параллелепипедов, и контур должен повторять именно их, а не общую рамку.
 * Иначе «обводка» выглядит как куб, висящий в воздухе.</p>
 *
 * <p>Источник геометрии — сама игра: {@link VoxelShape#forAllEdges} и
 * {@link VoxelShape#forAllBoxes}. Ничего не считается «на глаз».</p>
 *
 * <p>Результат кэшируется по состоянию блока: пока игрок смотрит на тот же
 * блок, геометрия не пересчитывается и память не выделяется — это важно,
 * потому что код вызывается из цикла отрисовки.</p>
 */
public final class BlockOutlineGeometry {
    /** Ограничение числа рёбер: у самых сложных форм их считанные десятки, запас взят с запасом. */
    private static final int MAX_EDGES = 768;
    /** Ограничение числа параллелепипедов в форме. */
    public static final int MAX_BOXES = 64;
    /** Точность, до которой координаты считаются одинаковыми (тысячные доли блока). */
    private static final double QUANT = 1000.0D;

    private static final float[] EDGES = new float[MAX_EDGES * 6];
    private static final float[] BOXES = new float[MAX_BOXES * 6];
    /** Ключи уже добавленных рёбер: чтобы shared-рёбра соседних боксов не рисовались дважды. */
    private static final long[] SEEN = new long[MAX_EDGES];

    private static BlockState cachedState;
    private static int edgeCount;
    private static int boxCount;

    private BlockOutlineGeometry() {
    }

    /**
     * Пересчитывает геометрию, если состояние блока изменилось.
     *
     * @param state состояние блока, форму которого нужно получить
     * @param level мир (нужен, чтобы игра учла контекст формы блока)
     * @param pos   позиция блока
     */
    public static void update(BlockState state, LevelReader level, BlockPos pos) {
        if (state == null || state.isAir()) {
            edgeCount = 0;
            boxCount = 0;
            cachedState = null;
            return;
        }
        if (state == cachedState) {
            return;
        }
        cachedState = state;
        edgeCount = 0;
        boxCount = 0;
        VoxelShape shape = state.getShape(level, pos);
        if (shape == null || shape.isEmpty()) {
            return;
        }
        shape.forAllBoxes(BlockOutlineGeometry::addBox);
        shape.forAllEdges(BlockOutlineGeometry::addEdge);
    }

    /** Сколько рёбер в текущей форме. */
    public static int edgeCount() {
        return edgeCount;
    }

    /** Сколько параллелепипедов в текущей форме. */
    public static int boxCount() {
        return boxCount;
    }

    /**
     * Заполняет шесть чисел очередного ребра.
     *
     * @param out      массив длиной не меньше шести
     * @param index    номер ребра (от 0 до {@link #edgeCount()})
     * @return true, если ребро существует и массив заполнен
     */
    public static boolean edge(int index, float[] out) {
        if (index < 0 || index >= edgeCount || out.length < 6) {
            return false;
        }
        int base = index * 6;
        System.arraycopy(EDGES, base, out, 0, 6);
        return true;
    }

    /** Заполняет шесть чисел очередного параллелепипеда (minX, minY, minZ, maxX, maxY, maxZ). */
    public static boolean box(int index, float[] out) {
        if (index < 0 || index >= boxCount || out.length < 6) {
            return false;
        }
        int base = index * 6;
        System.arraycopy(BOXES, base, out, 0, 6);
        return true;
    }

    private static void addBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (boxCount >= MAX_BOXES) {
            return;
        }
        int base = boxCount * 6;
        BOXES[base] = (float) minX;
        BOXES[base + 1] = (float) minY;
        BOXES[base + 2] = (float) minZ;
        BOXES[base + 3] = (float) maxX;
        BOXES[base + 4] = (float) maxY;
        BOXES[base + 5] = (float) maxZ;
        boxCount++;
    }

    private static void addEdge(double x0, double y0, double z0, double x1, double y1, double z1) {
        if (edgeCount >= MAX_EDGES) {
            return;
        }
        // Ребро, общее для двух соседних параллелепипедов, приходит дважды —
        // второй раз его рисовать не нужно.
        long key = edgeKey(x0, y0, z0, x1, y1, z1);
        for (int index = 0; index < edgeCount; index++) {
            if (SEEN[index] == key) {
                return;
            }
        }
        int base = edgeCount * 6;
        EDGES[base] = (float) x0;
        EDGES[base + 1] = (float) y0;
        EDGES[base + 2] = (float) z0;
        EDGES[base + 3] = (float) x1;
        EDGES[base + 4] = (float) y1;
        EDGES[base + 5] = (float) z1;
        SEEN[edgeCount] = key;
        edgeCount++;
    }

    /**
     * Ключ ребра: обе точки квантуются и упаковываются в 60 бит.
     *
     * <p>Порядок точек не важен (ребро AB то же, что BA), поэтому точки
     * сортируются перед упаковкой.</p>
     *
     * <p>Метод открыт намеренно: на нём есть отдельные проверки, потому что
     * ошибка в ключе означает либо дыры в контуре, либо двойные рёбра.</p>
     */
    public static long edgeKey(double ax, double ay, double az, double bx, double by, double bz) {
        long first = pack(ax, ay, az);
        long second = pack(bx, by, bz);
        return first <= second ? (first << 30) | second : (second << 30) | first;
    }

    /** Упаковывает точку блока (0..1) в 30 бит: по 10 бит на координату. */
    private static long pack(double x, double y, double z) {
        return (quantize(x) << 20) | (quantize(y) << 10) | quantize(z);
    }

    private static long quantize(double value) {
        long scaled = Math.round(value * QUANT);
        return Math.max(0L, Math.min(1023L, scaled));
    }
}
