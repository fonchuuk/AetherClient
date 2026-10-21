package com.aetherglass.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Сверяет растровые ресурсы главного меню с тем, что от них ждёт код.
 *
 * <p>Тест появился после настоящей поломки: {@code BackdropGallery} просила
 * шестнадцать фонов {@code menu_backdrop*_b0…b3.png}, которых в ресурсах не
 * было вовсе, а спрайты стекла лежали на диске в размере 1376×768, тогда как
 * {@code AuroraMenuScreen} читала их как 426×426 и 715×506 — девять частей
 * растягивали не стекло, а его левый верхний угол. Ни сборка, ни прежние
 * тесты этого не видели: компилятору всё равно, есть ли картинка.</p>
 *
 * <p>Размеры берутся не «на глаз», а разбираются из самих констант Java,
 * поэтому тест падает ровно тогда, когда код и ресурсы разъехались.</p>
 */
final class MenuAssetTest {

    /** Каталог исходников клиента: из него читаются константы размеров. */
    private static final Path CLIENT = findClientDir();

    private static final Path JAVA = CLIENT.resolve("src/main/java");
    private static final Path RESOURCES = CLIENT.resolve("src/main/resources");

    /** {@code Identifier.fromNamespaceAndPath("aetherglass", "textures/…")}. */
    private static final Pattern TEXTURE_REF = Pattern.compile(
            "Identifier\\.fromNamespaceAndPath\\(\\s*\"aetherglass\"\\s*,\\s*\"([^\"]+)\"\\s*\\)");

    /** Склеенные имена: {@code "textures/gui/" + NAMES[i] + "_b" + level + ".png"}. */
    private static final Pattern CONCAT_REF = Pattern.compile(
            "\"(textures/[a-z_]+/)\"\\s*\\+\\s*NAMES\\[image\\]\\s*\\+\\s*\"([^\"]*)\"");

    private static Path findClientDir() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 6 && dir != null; depth++) {
            if (Files.isDirectory(dir.resolve("src/main/resources/assets/aetherglass"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Не найден каталог клиента: тест нужно запускать из модуля :client");
    }

    private static String source(String relative) throws IOException {
        return Files.readString(JAVA.resolve(relative));
    }

    private static int constant(String source, String name) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*(-?\\d+)").matcher(source);
        assertTrue(matcher.find(), "В коде не найдена константа " + name);
        return Integer.parseInt(matcher.group(1));
    }

    /** Читает ширину и высоту прямо из IHDR: библиотека PNG тесту не нужна. */
    private static int[] pngSize(Path file) throws IOException {
        byte[] header = new byte[24];
        try (var input = Files.newInputStream(file)) {
            if (input.readNBytes(header, 0, 24) != 24) {
                fail("Файл короче заголовка PNG: " + file);
            }
        }
        assertEquals((byte) 0x89, header[0], "Это не PNG: " + file.getFileName());
        int width = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
        int height = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
        return new int[]{width, height};
    }

    private static Path asset(String namespacePath) {
        return RESOURCES.resolve("assets/aetherglass").resolve(namespacePath);
    }

    // ── проверки ────────────────────────────────────────────────────

    @Test
    void everyTextureReferencedFromCodeExists() throws IOException {
        TreeSet<String> referenced = new TreeSet<>();
        List<Path> files;
        try (Stream<Path> stream = Files.walk(JAVA)) {
            files = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
        for (Path file : files) {
            Matcher matcher = TEXTURE_REF.matcher(Files.readString(file));
            while (matcher.find()) {
                String path = matcher.group(1);
                // Финальный фрагмент без расширения собирается в рантайме —
                // такие имена проверяет отдельный тест.
                if (path.endsWith(".png")) {
                    referenced.add(path);
                }
            }
        }
        assertTrue(referenced.size() >= 4,
                "Подозрительно мало найденных ссылок на текстуры: " + referenced.size());

        List<String> missing = new ArrayList<>();
        for (String path : referenced) {
            if (!Files.isRegularFile(asset(path))) {
                missing.add(path);
            }
        }
        assertTrue(missing.isEmpty(),
                "Код ссылается на отсутствующие текстуры: " + missing);
    }

    @Test
    void everyParticleShapeHasItsSprite() throws IOException {
        // ParticleShape собирает имя в рантайме:
        // "textures/particle/" + file + ".png", поэтому обычный поиск по
        // готовой строке такие ссылки не видит — их проверяем отдельно.
        String shapes = source("com/aetherglass/client/render/particle/ParticleShape.java");
        Matcher matcher = Pattern.compile("^\\s{4}([A-Z_]+)\\(\"([a-z_]+)\"",
                Pattern.MULTILINE).matcher(shapes);
        List<String> files = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        while (matcher.find()) {
            files.add(matcher.group(2));
            Path sprite = asset("textures/particle/" + matcher.group(2) + ".png");
            if (!Files.isRegularFile(sprite)) {
                missing.add(matcher.group(1) + " → " + sprite.getFileName());
            }
        }
        assertEquals(6, files.size(),
                "Ожидались шесть форм частиц, найдено: " + files);
        assertTrue(missing.isEmpty(),
                "У форм частиц нет спрайтов: " + missing);
    }

    @Test
    void backdropGalleryLevelsAllExistWithExpectedSize() throws IOException {
        String gallery = source("com/aetherglass/client/gui/menu/BackdropGallery.java");

        Matcher names = Pattern.compile("\"(menu_backdrop[a-z_]*)\"").matcher(gallery);
        List<String> backdropNames = new ArrayList<>();
        while (names.find()) {
            if (!backdropNames.contains(names.group(1))) {
                backdropNames.add(names.group(1));
            }
        }
        assertEquals(4, backdropNames.size(),
                "Галерея должна держать четыре фона: " + backdropNames);

        Matcher levels = Pattern.compile("private static final int LEVELS\\s*=\\s*(\\d+)")
                .matcher(gallery);
        assertTrue(levels.find(), "В галерее не найдено число ступеней размытия");
        int levelCount = Integer.parseInt(levels.group(1));

        // Таблица размеров разбирается прямо из кода: {1376, 768}, {688, 384}, …
        Matcher sizes = Pattern.compile("\\{(\\d+),\\s*(\\d+)\\}").matcher(
                gallery.substring(gallery.indexOf("int[][] SIZE")));
        List<int[]> expected = new ArrayList<>();
        while (sizes.find() && expected.size() < levelCount) {
            expected.add(new int[]{Integer.parseInt(sizes.group(1)),
                    Integer.parseInt(sizes.group(2))});
        }
        assertEquals(levelCount, expected.size(),
                "Таблица SIZE не совпадает по числу ступеней с LEVELS");

        // Шаблон имени: "textures/gui/" + NAMES[image] + "_b" + level + ".png".
        Matcher concat = CONCAT_REF.matcher(gallery);
        assertTrue(concat.find(), "Не найден шаблон имени фона в BackdropGallery");
        String prefix = concat.group(1);
        String infix = concat.group(2);
        assertEquals("textures/gui/", prefix);

        for (String name : backdropNames) {
            for (int level = 0; level < levelCount; level++) {
                Path file = asset(prefix + name + infix + level + ".png");
                assertTrue(Files.isRegularFile(file),
                        "Галерея просит отсутствующий фон: " + file.getFileName());
                int[] size = pngSize(file);
                assertEquals(expected.get(level)[0], size[0],
                        "Ширина " + file.getFileName() + " не совпадает с BackdropGallery.SIZE");
                assertEquals(expected.get(level)[1], size[1],
                        "Высота " + file.getFileName() + " не совпадает с BackdropGallery.SIZE");
            }
        }
    }

    @Test
    void glassSpritesMatchDeclaredSpriteSize() throws IOException {
        String menu = source("com/aetherglass/client/gui/screen/AuroraMenuScreen.java");

        int logo = constant(menu, "TEX_LOGO_SIZE");
        int panelW = constant(menu, "TEX_PANEL_W");
        int panelH = constant(menu, "TEX_PANEL_H");

        int[] logoSize = pngSize(asset("textures/gui/menu_glass_logo.png"));
        assertEquals(logo, logoSize[0],
                "Спрайт логотипа уже спрайта в коде: девять частей растянут угол");
        assertEquals(logo, logoSize[1], "Спрайт логотипа обязан быть квадратным");

        int[] panelSize = pngSize(asset("textures/gui/menu_glass_panel.png"));
        assertEquals(panelW, panelSize[0],
                "Спрайт панели не совпадает с TEX_PANEL_W: стекло поедет");
        assertEquals(panelH, panelSize[1],
                "Спрайт панели не совпадает с TEX_PANEL_H: стекло поедет");
    }

    @Test
    void menuTexturesStayLightEnoughForMainMenu() throws IOException {
        // Фон главного меню висит в памяти всё время, пока открыто меню,
        // поэтому общий вес растровых ресурсов ограничен сверху.
        // Видео-листы неба сюда не входят: они грузятся только в мире и только
        // для выбранного пресета (у них отдельный предел ниже).
        // Предел поднят с 1,6 МБ: скрипт сборки перегенерировал фоны меню
        // (menu_backdrop_*) с более плавными градиентами, и они честно весят
        // больше. Смысл проверки прежний — меню не тянет мегабайты на пустом месте.
        long limit = 1_900_000L;
        long total = 0;
        try (Stream<Path> stream = Files.walk(
                RESOURCES.resolve("assets/aetherglass/textures"))) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".png"))
                    .filter(p -> !p.toString().contains("/textures/sky/")).toList()) {
                total += Files.size(file);
            }
        }
        assertTrue(total < limit,
                "Текстуры клиента весят " + total + " байт при пределе " + limit
                        + ": главное меню не должно тянуть мегабайты на ровном месте");
    }

    @Test
    void skyVideoSheetsExistAndStaySmall() throws IOException {
        // Купол проигрывает видео из листа кадров: 12 равноугольных панорам.
        // Проверяем, что листы есть у каждого пресета и что они не распухли.
        Path skyDir = RESOURCES.resolve("assets/aetherglass/textures/sky");
        assertTrue(Files.isDirectory(skyDir), "нет каталога видео-неба: " + skyDir);
        long total = 0;
        try (Stream<Path> stream = Files.list(skyDir)) {
            for (Path file : stream.filter(p -> p.toString().endsWith(".png")).toList()) {
                long size = Files.size(file);
                assertTrue(size < 900_000L, "лист неба слишком тяжёлый: " + file.getFileName() + " = " + size);
                total += size;
            }
        }
        assertTrue(total < 3_200_000L, "все листы неба весят " + total + " байт");
    }
}
