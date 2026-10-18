package com.aetherglass.installer;

import com.aetherglass.launcher.ui.UiPreview;
import com.aetherglass.launcher.ui.UiTheme;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Отрисовка окна установщика в PNG без графического окружения.
 *
 * <p>Тот же приём, что и в {@link UiPreview}: интерфейс собирается и
 * печатается в изображение. Если в разметке ошибка, отрисовка упадёт или
 * получится пустое изображение — это видно сразу, без запуска Windows.</p>
 *
 * <p>Запуск: {@code java -cp <classes> com.aetherglass.installer.InstallerPreview <каталог>}</p>
 */
public final class InstallerPreview {

    private static final int WIDTH = 940;
    private static final int HEIGHT = 640;

    private InstallerPreview() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args.length > 0 ? args[0] : "build/preview");
        Files.createDirectories(directory);

        UiTheme.install();
        InstallerUi ui = new InstallerUi(InstallPaths.system(), "1.0.0");
        JComponent root = ui.content();
        root.setDoubleBuffered(false);
        root.addNotify();
        root.setSize(WIDTH, HEIGHT);
        settle(root);

        List<String> pages = ui.pageNames();
        for (String page : pages) {
            ui.showPage(page);
            // Страницы переключаются: разметку нужно пересчитать заново.
            root.revalidate();
            settle(root);
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            try {
                root.printAll(graphics);
            } finally {
                graphics.dispose();
            }
            Path file = directory.resolve("installer-" + page + ".png");
            ImageIO.write(image, "png", file.toFile());
            System.out.println("сохранено: " + file + " (" + nonBlank(image) + " непустых пикселей)");
        }
    }

    /**
     * Пересчитывает разметку всего дерева.
     *
     * <p>Без окна Swing не раскладывает вложенные контейнеры сам, поэтому
     * приходится явно пройти по дереву: иначе все вложенные панели останутся
     * нулевого размера и на изображении будет только фон.</p>
     */
    private static void settle(JComponent root) {
        for (int attempt = 0; attempt < 4; attempt++) {
            root.validate();
            root.doLayout();
        }
    }

    /** Считает пиксели, отличающиеся от цвета фона: пустой интерфейс даст ноль. */
    static int nonBlank(BufferedImage image) {
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
