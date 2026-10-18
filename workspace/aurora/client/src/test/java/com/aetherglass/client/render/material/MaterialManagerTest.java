package com.aetherglass.client.render.material;

import com.aetherglass.client.modules.settings.Setting;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка менеджера материалов: создание, копирование, сохранение,
 * чтение испорченных значений и защита стандартных материалов.
 */
class MaterialManagerTest {

    private static MaterialManager fresh(Map<String, String> storage) {
        return MaterialManager.testing(MaterialManager.memory(storage));
    }

    @Test
    void standardMaterialsAreLoaded() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager manager = fresh(storage);

        assertEquals(5, manager.count(), "с клиентом приходит пять стандартных материалов");
        assertEquals(0, manager.userCount(), "своих материалов пока нет");
        Set<String> ids = new HashSet<>();
        for (Material material : manager.materials()) {
            assertTrue(material.builtIn(), material.id() + " должен быть стандартным");
            assertTrue(ids.add(material.id()), "идентификаторы не должны повторяться: " + material.id());
        }
    }

    @Test
    void createdMaterialIsAnIndependentCopy() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager manager = fresh(storage);
        Material rainbow = manager.byId("builtin.rainbow");
        assertNotNull(rainbow, "материал «Радуга» должен существовать");

        Material copy = manager.create(rainbow, "Моя радуга");
        assertEquals(6, manager.count(), "материал добавлен в список");
        assertFalse(copy.builtIn(), "копия — пользовательская");
        assertEquals(rainbow.mode(), copy.mode(), "режим копируется");
        assertEquals(rainbow.colorSource(), copy.colorSource(), "источник цвета копируется");
        assertEquals(rainbow.color(), copy.color(), "цвет копируется");

        // Правка копии не портит образец.
        for (Setting<?> setting : copy.settings()) {
            if (setting.id().equals("fillAlpha")) {
                settingValue(setting, 0.9F);
            }
        }
        assertEquals(0.9F, copy.fillAlpha(), 0.0001F, "копия изменилась");
        assertEquals(0.38F, rainbow.fillAlpha(), 0.0001F, "образец остался прежним");
    }

    @Test
    void materialsSurviveReload() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager first = fresh(storage);
        Material created = first.create(first.byId("builtin.liquid"), "Моё стекло");
        for (Setting<?> setting : created.settings()) {
            if (setting.id().equals("gradientSpeed")) {
                settingValue(setting, 1.25F);
            }
        }
        first.save();

        MaterialManager second = fresh(storage);
        Material loaded = second.byId(created.id());
        assertNotNull(loaded, "материал прочитан из хранилища");
        assertEquals("Моё стекло", loaded.name());
        assertEquals(1.25F, loaded.gradientSpeed(), 0.0001F, "настройка сохранилась");
        assertEquals(6, second.count(), "стандартные материалы тоже на месте");
    }

    @Test
    void standardMaterialsCannotBeChanged() {
        Map<String, String> storage = newHashMapWithList("builtin.glass");
        MaterialManager manager = fresh(storage);

        assertFalse(manager.remove("builtin.glass"), "стандартный материал удалять нельзя");
        assertFalse(manager.rename("builtin.glass", "Другое имя"), "стандартный материал переименовывать нельзя");
        assertNotNull(manager.byId("builtin.glass"), "материал остался в списке");
    }

    @Test
    void userMaterialsCanBeRenamedAndRemoved() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager manager = fresh(storage);
        Material created = manager.create(manager.byId("builtin.neon"), "Неон");

        assertTrue(manager.rename(created.id(), "Неон ярче"), "свой материал переименовывается");
        assertEquals("Неон ярче", manager.byId(created.id()).name());
        assertFalse(manager.rename(created.id(), "   "), "пустое имя не принимается");

        assertTrue(manager.remove(created.id()), "свой материал удаляется");
        assertNull(manager.byId(created.id()), "материала больше нет");
        assertEquals(5, manager.count(), "остались только стандартные");
    }

    @Test
    void resetKeepsOnlyStandardMaterials() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager manager = fresh(storage);
        manager.create(manager.byId("builtin.glass"), "Первый");
        manager.create(manager.byId("builtin.glass"), "Второй");
        assertEquals(2, manager.userCount());

        manager.reset();
        assertEquals(5, manager.count(), "остались только стандартные материалы");
        assertEquals(0, manager.userCount(), "своих материалов не осталось");
    }

    @Test
    void corruptValuesAreClamped() {
        Map<String, String> storage = new HashMap<>();
        storage.put(MaterialManager.LIST_KEY, "user1");
        storage.put("material.user1.name", "Испорченный");
        storage.put("material.user1.fillAlpha", "много");
        storage.put("material.user1.outlineAlpha", "5");
        storage.put("material.user1.glow", "-3");
        storage.put("material.user1.color", "123456789012345");
        storage.put("material.user1.mode", "ЧУДО-РЕЖИМ");

        MaterialManager manager = fresh(storage);
        Material material = manager.byId("user1");
        assertNotNull(material, "материал прочитан даже с испорченными значениями");
        assertEquals("Испорченный", material.name());
        assertEquals(0.32F, material.fillAlpha(), 0.0001F, "непонятное значение заменено на обычное");
        assertEquals(1.0F, material.outlineAlpha(), 0.0001F, "значение сверх диапазона обрезано");
        assertEquals(0.0F, material.glow(), 0.0001F, "значение ниже диапазона обрезано");
        assertEquals(0xFF8FE3FF, material.color(), "нечитаемый цвет заменён на обычный");
        assertEquals(FillMode.SHADER_FILL, material.mode(), "неизвестный режим заменён на обычный");
    }

    @Test
    void statusCountsMaterials() {
        Map<String, String> storage = new HashMap<>();
        MaterialManager manager = fresh(storage);
        manager.create(manager.byId("builtin.glass"), "Своё");

        assertEquals("6 материалов (1 своих)", manager.status(), "строка состояния для экрана");
    }

    private static Map<String, String> newHashMapWithList(String ids) {
        Map<String, String> storage = new HashMap<>();
        storage.put(MaterialManager.LIST_KEY, ids);
        return storage;
    }

    @SuppressWarnings("unchecked")
    private static void settingValue(Setting<?> setting, float value) {
        ((Setting<Float>) setting).set(value);
    }
}
