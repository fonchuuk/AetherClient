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
    private static final int WIDTH = 940;
    private static final int HEIGHT = 640;

    private UiPreview() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length > 0 ? args[0] : "build/preview");
        Files.createDirectories(directory);

        // Тот же порядок, что и при обычном запуске: сначала тема, потом интерфейс.
        UiTheme.install();
        LauncherConfig config = LauncherConfig.defaults()
                .gameDirectory(Path.of(System.getProperty("java.io.tmpdir"), "aurora-preview"));
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
            root.doLayout();
            BufferedImage image = render(root);
            String name = "launcher-" + switch (index) {
                case 0 -> "game";
                case 1 -> "mods";
                case 2 -> "settings";
                default -> "logs";
            } + ".png";
            Path file = directory.resolve(name);
            ImageIO.write(image, "png", file.toFile());
            System.out.println("сохранено: " + file + " (" + nonBlankPixels(image) + " непустых пикселей)");
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
