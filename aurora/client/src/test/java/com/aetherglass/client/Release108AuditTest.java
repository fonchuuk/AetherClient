package com.aetherglass.client;

import com.aetherglass.client.gui.inventory.InventoryTheme;
import com.aetherglass.client.gui.inventory.InventoryTransferAnimator;
import com.aetherglass.client.gui.notification.NotificationManager;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.modules.impl.InventoryModule;
import com.aetherglass.client.modules.impl.SkyEffectsModule;
import com.aetherglass.client.render.sky.SkyPreset;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Комплексная проверка всех нововведений версии 1.0.8 (Блоки 1–7).
 */
class Release108AuditTest {

    @Test
    void versionIsBumpedTo108() {
        assertEquals("1.1.2", AetherGlassClient.VERSION);
    }

    @Test
    void nineSliceSdfTexturesAndMcmetaExist() {
        String[] names = {
                "nine_slice_fill.png",
                "nine_slice_border.png",
                "nine_slice_shadow.png",
                "nine_slice_glass_panel.png",
                "slider_knob_sdf.png"
        };
        ClassLoader cl = Release108AuditTest.class.getClassLoader();
        for (String name : names) {
            String pngPath = "assets/aetherglass/textures/gui/" + name;
            String metaPath = pngPath + ".mcmeta";
            try (InputStream png = cl.getResourceAsStream(pngPath);
                 InputStream meta = cl.getResourceAsStream(metaPath)) {
                assertNotNull(png, "Отсутствует 9-slice текстура: " + pngPath);
                assertNotNull(meta, "Отсутствует .mcmeta для билинейного сглаживания: " + metaPath);
            } catch (Exception e) {
                fail("Ошибка чтения 9-slice ресурса " + name + ": " + e.getMessage());
            }
        }
    }

    @Test
    void skyVideoPresetsHaveFramesAndBlurredPlaceholders() {
        ClassLoader cl = Release108AuditTest.class.getClassLoader();
        for (SkyPreset preset : SkyPreset.values()) {
            assertNotNull(preset.videoFrame());
            assertNotNull(preset.blurredPlaceholder());
            String frameRes = "assets/" + preset.videoFrame().getNamespace() + "/" + preset.videoFrame().getPath();
            String blurRes = "assets/" + preset.blurredPlaceholder().getNamespace() + "/" + preset.blurredPlaceholder().getPath();
            assertNotNull(cl.getResource(frameRes), "Не найден видео-кадр неба: " + frameRes);
            assertNotNull(cl.getResource(blurRes), "Не найден размытый плейсхолдер неба: " + blurRes);
        }
        SkyEffectsModule sky = new SkyEffectsModule();
        assertTrue(sky.settings().size() >= 8);
    }

    @Test
    void viewModelAndHandAnimationsAreRegisteredAndConfigurable() {
        HandEffectsModule hands = new HandEffectsModule();
        assertNotNull(hands.setting("viewOffsetX"), "Должна быть настройка позиции X");
        assertNotNull(hands.setting("viewOffsetY"), "Должна быть настройка позиции Y");
        assertNotNull(hands.setting("viewOffsetZ"), "Должна быть настройка позиции Z");
        assertNotNull(hands.setting("viewScale"), "Должна быть настройка масштаба руки");
        assertNotNull(hands.setting("viewRotX"), "Должна быть настройка поворота X");
        assertNotNull(hands.setting("viewRotY"), "Должна быть настройка поворота Y");
        assertNotNull(hands.setting("viewRotZ"), "Должна быть настройка поворота Z");
        assertNotNull(hands.setting("noClipEating"), "Должна быть галочка «Не загораживать экран»");
        assertNotNull(hands.setting("swingAnim"), "Должна быть настройка Swing Animation");
        assertNotNull(hands.setting("equipAnim"), "Должна быть настройка Equip Animation");
        assertNotNull(hands.setting("walkingSway"), "Должна быть настройка Walking Sway");
        assertNotNull(hands.setting("idleAnim"), "Должна быть настройка Idle Animation");
        assertNotNull(hands.setting("eatingAnim"), "Должна быть настройка Eating/Drinking Animation");
        assertTrue(hands.noClipEating());
        assertEquals(1.12F, hands.viewScale(), 0.001F);
    }

    @Test
    void inventoryModuleSupportsThemesAndShiftClickFlight() {
        InventoryModule inv = new InventoryModule();
        assertEquals(InventoryTheme.LIQUID_GLASS, inv.theme());
        assertTrue(inv.shiftClickFlight());
        assertTrue(inv.quickMoveDrag());
        assertEquals(0.20F, inv.flightDuration(), 0.01F);

        inv.setTheme(InventoryTheme.DARK);
        assertEquals(InventoryTheme.DARK, inv.theme());
        inv.setTheme(InventoryTheme.LIGHT);
        assertEquals(InventoryTheme.LIGHT, inv.theme());
    }

    @Test
    void notificationManagerPushesAdvancementToasts() {
        NotificationManager mgr = NotificationManager.get();
        mgr.setSuppressedDuringInit(false);
        mgr.clear();
        mgr.pushAdvancement("Алмазы!");
        assertEquals(1, mgr.activeToasts().size());
        NotificationManager.Toast t = mgr.activeToasts().get(0);
        assertEquals(NotificationManager.Type.ACHIEVEMENT, t.type());
        assertEquals("Алмазы!", t.subtitle());
        mgr.clear();
        mgr.setSuppressedDuringInit(true);
    }
}
