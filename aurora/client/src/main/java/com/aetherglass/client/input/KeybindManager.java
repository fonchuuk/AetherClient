package com.aetherglass.client.input;

import com.aetherglass.client.config.ModConfig;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.settings.BooleanSetting;
import com.aetherglass.client.modules.settings.EnumSetting;
import com.aetherglass.client.modules.settings.Setting;
import org.lwjgl.glfw.GLFW;

/**
 * Событийный менеджер горячих клавиш (Блок 2) и режима «Чистый экран» (Блок 5.5).
 *
 * <p>Вызывается исключительно по событиям нажатия клавиш из {@code KeyboardHandlerMixin}
 * (без опроса в {@code onTick}). Поддерживает привязку клавиш к модулям, отдельным
 * настройкам и режиму «Чистый экран» (плавное скрытие всего HUD, кроме прицела).</p>
 */
public final class KeybindManager {
    private static final KeybindManager INSTANCE = new KeybindManager();
    private static final String CLEAN_SCREEN_KEY = "client.clean_screen.keybind";

    private int cleanScreenKeybind = GLFW.GLFW_KEY_F8;
    private boolean cleanScreenActive;
    private float hudVisibility = 1.0F;

    private KeybindManager() {
    }

    public static KeybindManager get() {
        return INSTANCE;
    }

    public void loadFrom(ModConfig config) {
        if (config == null) {
            return;
        }
        this.cleanScreenKeybind = config.getInt(CLEAN_SCREEN_KEY, GLFW.GLFW_KEY_F8, -1, 1024);
    }

    public void saveTo(ModConfig config) {
        if (config == null) {
            return;
        }
        config.set(CLEAN_SCREEN_KEY, this.cleanScreenKeybind);
    }

    public int cleanScreenKeybind() {
        return this.cleanScreenKeybind;
    }

    public void setCleanScreenKeybind(int keyCode) {
        this.cleanScreenKeybind = keyCode <= 0 ? -1 : keyCode;
        ModConfig.get().set(CLEAN_SCREEN_KEY, this.cleanScreenKeybind);
    }

    public boolean isCleanScreenActive() {
        return this.cleanScreenActive;
    }

    public void setCleanScreenActive(boolean active) {
        if (this.cleanScreenActive == active) {
            return;
        }
        this.cleanScreenActive = active;
        NotificationManager.get().push(
                "Чистый экран",
                active ? "HUD плавно скрыт для скриншота" : "HUD восстановлен",
                active ? NotificationManager.Type.INFO : NotificationManager.Type.ENABLED,
                2.0F
        );
    }

    public void toggleCleanScreen() {
        setCleanScreenActive(!this.cleanScreenActive);
    }

    /**
     * Возвращает плавный коэффициент видимости HUD (1.0 — показан, 0.0 — скрыт режимом «Чистый экран»).
     */
    public float updateHudVisibility(float dt) {
        float target = this.cleanScreenActive ? 0.0F : 1.0F;
        float speed = Math.min(1.0F, Math.max(0.01F, dt) * 10.0F);
        this.hudVisibility += (target - this.hudVisibility) * speed;
        if (Math.abs(target - this.hudVisibility) < 0.004F) {
            this.hudVisibility = target;
        }
        return this.hudVisibility;
    }

    public float hudVisibility() {
        return this.hudVisibility;
    }

    /**
     * Обрабатывает однократное событие нажатия клавиши в игровом мире (когда не открыт экран GUI).
     */
    public void onKeyPress(int keyCode) {
        if (keyCode <= 0 || keyCode == GLFW.GLFW_KEY_UNKNOWN) {
            return;
        }
        if (keyCode == this.cleanScreenKeybind) {
            toggleCleanScreen();
        }
        for (Module module : ModuleManager.get().all()) {
            if (module.keybind() == keyCode) {
                module.toggle();
            }
            for (Setting<?> setting : module.settings()) {
                if (setting.keybind() == keyCode) {
                    triggerSettingBind(module, setting);
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void triggerSettingBind(Module module, Setting<?> setting) {
        if (setting instanceof BooleanSetting boolSetting) {
            boolSetting.toggle();
            NotificationManager.get().push(
                    module.name(),
                    boolSetting.name() + ": " + (boolSetting.isEnabled() ? "ВКЛ" : "ВЫКЛ"),
                    boolSetting.isEnabled() ? NotificationManager.Type.ENABLED : NotificationManager.Type.DISABLED,
                    2.1F
            );
        } else if (setting instanceof EnumSetting enumSetting) {
            Enum next = enumSetting.next(false);
            enumSetting.set(next);
            NotificationManager.get().push(
                    module.name(),
                    enumSetting.name() + " -> " + next.toString(),
                    NotificationManager.Type.INFO,
                    2.1F
            );
        }
    }

    public static boolean isClearKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_ESCAPE
                || keyCode == GLFW.GLFW_KEY_BACKSPACE
                || keyCode == GLFW.GLFW_KEY_DELETE;
    }

    /**
     * Короткое человекочитаемое обозначение клавиши для стеклянного бейджа в меню и HUD.
     */
    public static String formatKey(int keyCode) {
        if (keyCode <= 0) {
            return "—";
        }
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) ('A' + (keyCode - GLFW.GLFW_KEY_A)));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) ('0' + (keyCode - GLFW.GLFW_KEY_0)));
        }
        if (keyCode >= GLFW.GLFW_KEY_F1 && keyCode <= GLFW.GLFW_KEY_F25) {
            return "F" + (1 + (keyCode - GLFW.GLFW_KEY_F1));
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return "NP" + (keyCode - GLFW.GLFW_KEY_KP_0);
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> "SPC";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_TAB -> "TAB";
            case GLFW.GLFW_KEY_CAPS_LOCK -> "CAPS";
            case GLFW.GLFW_KEY_ENTER -> "ENT";
            case GLFW.GLFW_KEY_INSERT -> "INS";
            case GLFW.GLFW_KEY_HOME -> "HOME";
            case GLFW.GLFW_KEY_END -> "END";
            case GLFW.GLFW_KEY_PAGE_UP -> "PGUP";
            case GLFW.GLFW_KEY_PAGE_DOWN -> "PGDN";
            case GLFW.GLFW_KEY_UP -> "UP";
            case GLFW.GLFW_KEY_DOWN -> "DN";
            case GLFW.GLFW_KEY_LEFT -> "LEFT";
            case GLFW.GLFW_KEY_RIGHT -> "RGHT";
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> "~";
            case GLFW.GLFW_KEY_MINUS -> "-";
            case GLFW.GLFW_KEY_EQUAL -> "=";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_SEMICOLON -> ";";
            case GLFW.GLFW_KEY_APOSTROPHE -> "'";
            case GLFW.GLFW_KEY_COMMA -> ",";
            case GLFW.GLFW_KEY_PERIOD -> ".";
            case GLFW.GLFW_KEY_SLASH -> "/";
            case GLFW.GLFW_KEY_BACKSLASH -> "\\";
            default -> "K" + keyCode;
        };
    }
}
