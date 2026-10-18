package com.aetherglass.client.modules;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ModuleShutdownTest {
    private static final class TestModule extends Module {
        int disabled;

        TestModule(String id) {
            super(id, id, "", ModuleCategory.VISUAL);
        }

        @Override
        protected void onDisable() {
            disabled++;
        }
    }

    @Test
    void savesUserSelectionBeforeDisablingResources() {
        ModuleManager manager = new ModuleManager();
        TestModule enabled = new TestModule("enabled");
        TestModule disabled = new TestModule("disabled");
        manager.register(enabled);
        manager.register(disabled);
        enabled.setEnabled(true);
        int[] saves = {0};
        manager.shutdown(() -> {
            saves[0]++;
            assertTrue(enabled.isEnabled(), "В конфиг должно попасть состояние до shutdown");
            assertFalse(disabled.isEnabled());
            assertEquals(0, enabled.disabled);
        });
        assertEquals(1, saves[0]);
        assertFalse(enabled.isEnabled());
        assertEquals(1, enabled.disabled);
        assertEquals(0, disabled.disabled);
    }

    @Test
    void releasesResourcesEvenIfSavingFails() {
        ModuleManager manager = new ModuleManager();
        TestModule module = new TestModule("enabled");
        manager.register(module);
        module.setEnabled(true);
        RuntimeException failure = new RuntimeException("disk full");
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> manager.shutdown(() -> { throw failure; })));
        assertFalse(module.isEnabled());
        assertEquals(1, module.disabled);
    }
}
