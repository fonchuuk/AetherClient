package com.aetherglass.client.render.hud;

import java.util.Locale;

/**
 * Сгруппированные стеклянные панели и виджеты HUD в стиле Liquid Glass (по образцу {@code image-2.png}, Блок 4 и Блок 5).
 *
 * <p>Не содержит списка чит-модулей (KillAura, Scaffold и т.п.): только легитные информационные панели,
 * сгруппированные по зонам экрана, с возможностью свободного перетаскивания (ЛКМ) и скрытия (ПКМ)
 * в редакторе HUD.</p>
 */
public enum HudElementType {
    // 1. Верхний левый угол: единый инфо-блок (Логотип + Ник + FPS + Пинг + Время + IP/TPS + CPU/RAM)
    WATERMARK("watermark", "Инфо-бар (Лого · Ник · FPS · Пинг · Время)", 0.012F, 0.018F, Alignment.LEFT, true),
    PERFORMANCE("performance", "Сервер · TPS · CPU / RAM", 0.012F, 0.062F, Alignment.LEFT, true),

    // 2. Слева: клавиатура Keystrokes + CPS
    KEYSTROKES("keystrokes", "Клавиши (WASD + LMB/RMB)", 0.012F, 0.340F, Alignment.LEFT, true),
    CPS("cps", "Частота кликов (CPS)", 0.012F, 0.520F, Alignment.LEFT, true),

    // 3. Справа: активные зелья (Potions) и перезарядки предметов (Cooldowns: Жемчуг, Яблоко, Хорус)
    POTIONS("potions", "Активные зелья (Potions)", 0.835F, 0.240F, Alignment.RIGHT, true),
    COOLDOWNS("cooldowns", "Перезарядки (Cooldowns)", 0.835F, 0.410F, Alignment.RIGHT, true),

    // 4. Снизу слева: координаты (X, Y, Z), скорость (BPS), направление и биом
    COORDINATES("coords", "Координаты · Скорость · Направление", 0.012F, 0.905F, Alignment.LEFT, true),
    DIRECTION("direction", "Компас и направление", 0.012F, 0.855F, Alignment.LEFT, false),
    BIOME("biome", "Текущий биом", 0.145F, 0.905F, Alignment.LEFT, false),

    // 5. По центру: стеклянная карточка статуса игрока (HP, Броня, Сытость) + Цель + Экипировка
    PLAYER_STATUS("player_status", "Статус игрока (HP · Броня · Сытость)", 0.365F, 0.785F, Alignment.CENTER, true),
    TARGET("target", "Карточка цели (Target HUD)", 0.545F, 0.680F, Alignment.CENTER, true),
    EQUIPMENT("equipment", "Броня и экипировка", 0.365F, 0.855F, Alignment.CENTER, false),
    HELD_ITEM("held", "Предмет в руке", 0.595F, 0.855F, Alignment.CENTER, false),
    ARROWS("arrows", "Боезапас стрел", 0.685F, 0.855F, Alignment.RIGHT, false),

    // 6. Панель быстрого доступа (Quick Access, Блок 5.3) и дополнительные капсулы
    QUICK_ACCESS("quick_access", "Быстрый доступ (Quick Access)", 0.985F, 0.062F, Alignment.RIGHT, true),
    FPS("fps", "Отдельный индикатор FPS", 0.245F, 0.018F, Alignment.LEFT, false),
    CLOCK("clock", "Отдельные часы", 0.245F, 0.062F, Alignment.RIGHT, false),
    SESSION("session", "Время сессии", 0.145F, 0.855F, Alignment.RIGHT, false);

    /** К какой стороне «приклеен» элемент по горизонтали. */
    public enum Alignment {
        LEFT, CENTER, RIGHT
    }

    private final String id;
    private final String title;
    private final float defaultX;
    private final float defaultY;
    private final Alignment alignment;
    private final boolean defaultEnabled;

    HudElementType(String id, String title, float defaultX, float defaultY,
                   Alignment alignment, boolean defaultEnabled) {
        this.id = id;
        this.title = title;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.alignment = alignment;
        this.defaultEnabled = defaultEnabled;
    }

    public String id() {
        return this.id;
    }

    public String settingsId() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String title() {
        return this.title;
    }

    public float defaultX() {
        return this.defaultX;
    }

    public float defaultY() {
        return this.defaultY;
    }

    public Alignment alignment() {
        return this.alignment;
    }

    public boolean defaultEnabled() {
        return this.defaultEnabled;
    }

    @Override
    public String toString() {
        return this.title;
    }
}
