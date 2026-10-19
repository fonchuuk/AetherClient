package com.aetherglass.installer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Разбор вывода {@code java -version}: от него зависит, запустится ли лаунчер. */
final class JavaFinderTest {

    @Test
    void readsModernVersion() {
        String output = "openjdk version \"21.0.5\" 2024-10-15\n"
                + "OpenJDK Runtime Environment Temurin-21.0.5+11 (build 21.0.5+11-LTS)\n";
        assertEquals(21, JavaFinder.versionOf(output));
    }

    @Test
    void readsNewerVersion() {
        assertEquals(25, JavaFinder.versionOf("openjdk version \"25.0.1\" 2025-10-21"));
    }

    @Test
    void readsOldVersionFormat() {
        assertEquals(8, JavaFinder.versionOf("java version \"1.8.0_362\""));
    }

    @Test
    void readsPreviewAndBuildSuffixes() {
        assertEquals(22, JavaFinder.versionOf("openjdk version \"22-ea\" 2024-03-19"));
    }

    @Test
    void garbageGivesMinusOne() {
        assertEquals(-1, JavaFinder.versionOf(""));
        assertEquals(-1, JavaFinder.versionOf("not a version at all"));
        assertEquals(-1, JavaFinder.versionOf("openjdk version \"abc\""));
    }

    @Test
    void minimumIsTwentyOne() {
        assertEquals(21, JavaFinder.MINIMUM);
        assertTrue(JavaFinder.versionOf("openjdk version \"21\"") >= JavaFinder.MINIMUM);
        assertFalse(JavaFinder.versionOf("openjdk version \"17.0.9\"") >= JavaFinder.MINIMUM);
    }

    @Test
    void unusableExecutableIsRejected() {
        // Такого файла нет: проверка обязана вернуть false, а не упасть.
        assertFalse(JavaFinder.isUsable(java.nio.file.Path.of("missing-java-executable")));
    }

    @Test
    void describeHandlesMissingJava() {
        assertEquals("не найдена", JavaFinder.describe(null));
    }
}
