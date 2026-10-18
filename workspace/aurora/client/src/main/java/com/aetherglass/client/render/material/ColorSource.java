package com.aetherglass.client.render.material;

/** Откуда берётся цвет материала. */
public enum ColorSource {
    /** Свой цвет из настроек материала. */
    CUSTOM("Свой цвет"),
    /** Общий контроллер радуги: цвет медленно меняется сам. */
    RAINBOW("Радуга"),
    /** Акцентный цвет оформления клиента. */
    ACCENT("Акцент клиента");

    private final String title;

    ColorSource(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
