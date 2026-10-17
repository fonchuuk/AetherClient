package com.aetherglass.client.render.hand;

/** Движение руки в первом лице. */
public enum HandAnimation {
    NONE("Нет"),
    SWING("Размах"),
    HOLD("Удержание"),
    BOB("Покачивание");

    private final String title;

    HandAnimation(String title) {
        this.title = title;
    }

    public String title() {
        return this.title;
    }
}
