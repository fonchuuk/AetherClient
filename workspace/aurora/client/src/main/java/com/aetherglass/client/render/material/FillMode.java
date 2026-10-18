package com.aetherglass.client.render.material;

/**
 * Чем именно закрашивается блок под прицелом.
 *
 * <p>Геометрия берётся из настоящей формы блока (VoxelShape), поэтому любой
 * режим повторяет ступеньку, плиту или забор, а не рисует куб поверх блока.</p>
 */
public enum FillMode {
    /** Только контур по рёбрам: ничего не закрывает обзор. */
    OUTLINE("Контур"),
    /** Ровная полупрозрачная заливка по граням формы. */
    FILL("Заливка"),
    /** Градиентная заливка с бегущим бликом: «жидкое стекло». */
    SHADER_FILL("Градиент");

    private final String title;

    FillMode(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
