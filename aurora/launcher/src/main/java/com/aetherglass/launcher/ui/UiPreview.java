package com.aetherglass.launcher.ui;

import com.aetherglass.launcher.core.LauncherConfig;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Отрисовка интерфейса лаунчера в PNG без графического окружения.
 *
 * <p>Полезно по двум причинам: во-первых, можно посмотреть, как выглядит окно, не запуская его;
 * во-вторых, это проверка, что интерфейс вообще собирается — если в разметке ошибка,
 * отрисовка упадёт или получится пустое изображение.</p>
 *
 * <p>Запуск: {@code java -cp <classes> com.aetherglass.launcher.ui.UiPreview <каталог>}</p>
 */
public final class UiPreview {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 760;

    private UiPreview() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length > 0 ? args[0] : "build/preview");
        Files.createDirectories(directory);

        // Тот же порядок, что и при обычном запуске: сначала тема, потом интерфейс.
        UiTheme.install();
        Path previewDir = Path.of(System.getProperty("java.io.tmpdir"), "aurora-preview");
        Path previewShots = previewDir.resolve("screenshots");
        Files.createDirectories(previewShots);
        for (String sample : new String[]{"01_clean_hand_shader.png", "03_clean_sword_shader.png", "08_clean_rshift_menu.png"}) {
            Path src = directory.resolve(sample);
            if (Files.isRegularFile(src)) {
                Files.copy(src, previewShots.resolve(sample), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
        LauncherConfig config = LauncherConfig.defaults().gameDirectory(previewDir);
        seedAssistantSample(previewDir);
        LauncherUi ui = new LauncherUi(config, null);
        JComponent root = ui.root();
        root.setDoubleBuffered(false);
        // Без addNotify вложенные компоненты (вкладки) не получают размеры и не рисуются.
        root.addNotify();
        root.setSize(WIDTH, HEIGHT);
        root.doLayout();

        // Фоновые задачи догружают список версий и манифест модов — дадим им время.
        for (int attempt = 0; attempt < 40; attempt++) {
            Thread.sleep(250);
            root.revalidate();
            root.doLayout();
        }

        int tabs = ui.tabs().getTabCount();
        for (int index = 0; index < tabs; index++) {
            ui.tabs().setSelectedIndex(index);
            layoutTree(root);
            BufferedImage image = render(root);
            String name = "launcher-" + switch (index) {
                case 0 -> "modpacks";
                case 1 -> "mods";
                case 2 -> "game";
                case 3 -> "screenshots";
                case 4 -> "settings";
                case 5 -> "assistant";
                default -> "logs";
            } + ".png";
            Path file = directory.resolve(name);
            ImageIO.write(image, "png", file.toFile());
            System.out.println("сохранено: " + file + " (" + nonBlankPixels(image) + " непустых пикселей)");
        }

        // Вкладка «Помощник» с настоящим прогоном: на скриншоте должен быть
        // реальный ответ, а не заготовка.
        ui.tabs().setSelectedIndex(5);
        layoutTree(root);
        ui.runAssistantSynchronously();
        layoutTree(root);
        ImageIO.write(render(root), "png", directory.resolve("launcher-4-assistant.png").toFile());
        System.out.println("сохранено: assistant");

        // 1) Главный экран со сборками (AetherVisuals и 26.2)
        ui.tabs().setSelectedIndex(0);
        ui.toggleProfileDropdown(false);
        ui.openAccountSwitchModal(false);
        ui.openCreateModpackModal(false);
        layoutTree(root);
        ImageIO.write(render(root), "png", directory.resolve("launcher-1-main-modpacks.png").toFile());

        // 2) Раскрытое меню профиля «Сменить аккаунт» + окно смены ника
        ui.toggleProfileDropdown(true);
        layoutTree(root);
        BufferedImage dropdownShot = render(root);
        ImageIO.write(dropdownShot, "png", directory.resolve("launcher-2a-profile-dropdown.png").toFile());

        ui.openAccountSwitchModal(true);
        layoutTree(root);
        ImageIO.write(render(root), "png", directory.resolve("launcher-2-account-modal.png").toFile());

        // 3) Модальное окно «Добавить новую сборку» (с проверкой блокировки галочки AetherGlass на версии != 1.21.11)
        ui.selectVersionAndLoader("26.2", "Fabric");
        ui.openCreateModpackModal(true);
        layoutTree(root);
        ImageIO.write(render(root), "png", directory.resolve("launcher-3-create-modpack-modal.png").toFile());

        System.exit(0);
    }

    /**
     * Готовит для вкладки «Помощник» правдоподобный набор: пару модов и
     * crash-report, чтобы стенд показывал реальный разбор, а не пустое окно.
     */
    private static void seedAssistantSample(Path gameDir) throws IOException {
        Path mods = gameDir.resolve("mods");
        Files.createDirectories(mods);
        for (String mod : new String[]{"fabric-api-0.141.6.jar", "sodium-0.6.9.jar",
                "iris-1.8.1.jar", "sodium-extra-0.6.0.jar", "lithium-0.15.3.jar"}) {
            Files.writeString(mods.resolve(mod), "preview");
        }
        Path crashes = gameDir.resolve("crash-reports");
        Files.createDirectories(crashes);
        Files.writeString(crashes.resolve("crash-2026-10-21_14-52-11-client.txt"), """
                ---- Minecraft Crash Report ----
                // Don't be sad, have a hug! <3

                Time: 2026-10-21 14:52:11
                Description: Rendering overlay

                java.lang.RuntimeException: Mixin transformation of net.minecraft.class_757 failed
                	at net.fabricmc.loader.impl.launch.knot.KnotClassDelegate.transform(KnotClassDelegate.java:412)
                	at net.fabricmc.loader.impl.launch.knot.KnotClassLoader.loadClass(KnotClassLoader.java:231)
                	at net.minecraft.client.renderer.GameRenderer.<init>(GameRenderer.java:142)
                Caused by: org.spongepowered.asm.mixin.throwables.MixinApplyError: Mixin sodium-extra.mixins.json:GameRendererMixin from mod sodium-extra failed
                	at org.spongepowered.asm.mixin.transformer.MixinProcessor.applyMixins(MixinProcessor.java:392)
                	at org.spongepowered.asm.mixin.transformer.MixinTransformer.transformClass(MixinTransformer.java:230)
                	... 12 more
                Caused by: java.lang.NoSuchMethodError: 'void net.minecraft.class_757.method_3192(int, int)'
                	at net.caffeinemc.mods.sodiumextra.mixin.render.GameRendererMixin.method_3192(GameRendererMixin.java:41)

                -- System Details --
                Minecraft Version: 1.21.11
                Fabric Loader: 0.19.5
                Java Version: 21.0.5, Eclipse Adoptium
                Memory: 1844 MB / 4096 MB
                """);
    }

    private static void layoutTree(java.awt.Component comp) {
        comp.doLayout();
        if (comp instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    /** Рисует компонент в изображение фиксированного размера. */
    public static BufferedImage render(JComponent component) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        try {
            component.printAll(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    /** Считает пиксели, отличающиеся от цвета фона: пустой интерфейс даст ноль. */
    static int nonBlankPixels(BufferedImage image) {
        int background = image.getRGB(0, 0);
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }

    static void write(Path file, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", file.toFile());
    }
}
