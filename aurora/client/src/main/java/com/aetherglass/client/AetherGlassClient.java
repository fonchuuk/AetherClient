package com.aetherglass.client;

import com.aetherglass.client.gui.screen.AuroraMenuScreen;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.ModuleRegistry;
import com.aetherglass.client.modules.impl.CrosshairModule;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.render.RenderResources;
import com.aetherglass.client.render.WorldRenderDispatcher;
import com.aetherglass.client.utils.AuroraLog;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;

/**
 * Точка входа клиента.
 *
 * <p>Порядок запуска важен: сначала создаются модули, затем читается файл настроек,
 * затем модули подключаются к событиям игры. Если что-то ломается на одном шаге,
 * клиент всё равно остаётся рабочим: ошибка пишется в журнал, а игра продолжается.</p>
 */
public final class AetherGlassClient implements ClientModInitializer {
    /** Идентификатор мода: совпадает с {@code fabric.mod.json}. */
    public static final String MOD_ID = "aetherglass";

    /** Версия клиента. */
    public static final String VERSION = "1.0.8";

    private static final String FALLBACK_MINECRAFT = "1.21.11";
    private static final String FALLBACK_LOADER = "0.19.5";

    private static String minecraftVersion = FALLBACK_MINECRAFT;
    private static String loaderVersion = FALLBACK_LOADER;
    private static KeyMapping menuKey;
    private static boolean menuAnnounced;

    @Override
    public void onInitializeClient() {
        AuroraLog.info("AetherGlass Client " + VERSION + ": запуск");

        // Каждый шаг — сам по себе. Раньше весь метод шёл монолитом: стоило
        // одной строке бросить исключение, как мод умирал наполовину, Fabric
        // это проглатывал, игра продолжалась с обычным ванильным меню — и
        // при этом не появлялось НИ ОДНОГО файла краша, потому что игра-то не
        // падала. Из-за этого вылет был неотличим от «мод просто не работает»,
        // и искать его приходилось вслепую. Теперь сломавшийся шаг отключает
        // только себя, а остальные поднимаются.
        step("чтение версий", AetherGlassClient::readVersions);
        step("создание модулей", ModuleRegistry::createAll);
        step("загрузка настроек", () -> ModuleManager.get().loadFromConfig());
        step("оффлайн-аккаунты", () -> com.aetherglass.client.account.AccountManager.get().loadFromConfig());

        step("экранные элементы", () -> {
            HudModule hud = ModuleManager.get().typed("hud", HudModule.class);
            if (hud != null) {
                hud.loadLayout();
                hud.install();
            }
        });
        step("прицел", () -> {
            CrosshairModule crosshair = ModuleManager.get().typed("crosshair", CrosshairModule.class);
            if (crosshair != null) {
                crosshair.install();
            }
        });
        step("обработчики отрисовки мира", WorldRenderDispatcher::register);
        step("клавиша вызова меню", AetherGlassClient::registerKeyBinding);
        step("тикающие события", this::registerTickEvents);

        AuroraLog.info("AetherGlass Client готов: Minecraft " + minecraftVersion + ", загрузчик " + loaderVersion);
    }

    /** Выполняет шаг инициализации, не давая его ошибке обрушить весь мод. */
    private static void step(String name, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            // Не «запишем и забудем»: именно это сообщение теперь говорит,
            // какой кусок не поднялся. Без него причина выглядела как
            // «мод не работает» без всяких подробностей.
            AuroraLog.error("шаг инициализации не выполнен: " + name
                    + ". Часть возможностей будет недоступна", throwable);
        }
    }

    /** Тикающие события: отдельно, чтобы их сбой не похоронил остальное. */
    private void registerTickEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                onTick();
            } catch (Throwable throwable) {
                AuroraLog.error("тик клиента", throwable);
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            try {
                onStop();
            } catch (Throwable throwable) {
                AuroraLog.error("остановка клиента", throwable);
            }
        });
    }

    /** Версии берутся из настоящих метаданных, а не из констант «на всякий случай». */
    private static void readVersions() {
        Optional<ModContainer> game = FabricLoader.getInstance().getModContainer("minecraft");
        game.ifPresent(container -> minecraftVersion = container.getMetadata().getVersion().getFriendlyString());
        Optional<ModContainer> loader = FabricLoader.getInstance().getModContainer("fabricloader");
        loader.ifPresent(container -> loaderVersion = container.getMetadata().getVersion().getFriendlyString());
    }

    private static void registerKeyBinding() {
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        // Правый шифт: он не занят ни ванильной игрой, ни опциями, и его
        // удобно нажать одной рукой, не отрываясь от мыши.
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.aurora.menu",
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT, category));
    }

    private static void onTick() {
        ModuleManager.get().saveIfNeeded();
        if (menuKey == null || !menuKey.consumeClick()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.screen != null) {
            // Экран уже открыт: не подменяем его, чтобы не прерывать начатое действие.
            return;
        }
        client.setScreen(new com.aetherglass.client.gui.screen.AuroraInlineMenuScreen(null));
    }

    private static void onStop() {
        ModuleManager.get().shutdown();
        RenderResources.release();
        AuroraLog.info("AetherGlass Client остановлен, ресурсы освобождены");
    }

    /**
     * Однократное сообщение в журнал о том, что своё меню действительно открылось.
     *
     * <p>Нужно не для красоты: по этой строке стенд {@code tools/harness/HeadlessRun}
     * понимает, что игра дошла до главного меню и экран можно снимать.</p>
     */
    public static void noteMenuReplaced() {
        if (!menuAnnounced) {
            menuAnnounced = true;
            AuroraLog.info("главное меню заменено на собственное");
        }
    }

    public static String minecraftVersion() {
        return minecraftVersion;
    }

    public static String loaderVersion() {
        return loaderVersion;
    }

    /** Клавиша вызова меню: нужна экрану «О клиенте», чтобы показать назначение. */
    public static KeyMapping menuKey() {
        return menuKey;
    }
}
