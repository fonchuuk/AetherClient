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
                default -> "logs";
            } + ".png";
            Path file = directory.resolve(name);
            ImageIO.write(image, "png", file.toFile());
            System.out.println("сохранено: " + file + " (" + nonBlankPixels(image) + " непустых пикселей)");
        }

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
