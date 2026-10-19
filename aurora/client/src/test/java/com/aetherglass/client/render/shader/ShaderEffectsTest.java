package com.aetherglass.client.render.shader;

import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.render.util.ColorUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Модульные тесты 16 уникальных шейдер-эффектов для {@code BlockShader} и {@code HandsShader}.
 */
class ShaderEffectsTest {

    @Test
    void exactlySixteenUniqueShaderEffectsExist() {
        ShaderEffect[] effects = ShaderEffect.values();
        assertEquals(16, effects.length, "Должно быть ровно 16 шейдер-эффектов");

        String[] expectedIds = {
                "liquid_glass",
                "rainbow_flow",
                "neon_pulse",
                "energy_waves",
                "plasma",
                "electric_arcs",
                "hologram",
                "cyber_grid",
                "liquid_metal",
                "aurora",
                "glitch",
                "fire_core",
                "ice_crystal",
                "spectral_outline",
                "cosmic_dust",
                "void_distortion"
        };

        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < effects.length; i++) {
            ShaderEffect effect = effects[i];
            assertEquals(i + 1, effect.number());
            assertEquals(expectedIds[i], effect.id());
            assertTrue(ids.add(effect.id()), "Дублирующийся id: " + effect.id());
            assertTrue(names.add(effect.displayName()), "Дублирующееся имя: " + effect.displayName());
            assertNotNull(effect.description());
            assertFalse(effect.description().isBlank());
            assertEquals(effect, ShaderEffect.byId(effect.id()));
            assertEquals(effect, ShaderEffect.byId(effect.displayName()));
            assertEquals(effect, ShaderEffect.byId(effect.name()));
        }
    }

    @Test
    void allSixteenEffectsProduceDistinctSurfaceSignatures() {
        ShaderEffect[] effects = ShaderEffect.values();
        int[][] signatures = new int[effects.length][36];

        for (int idx = 0; idx < effects.length; idx++) {
            ShaderEffect effect = effects[idx];
            int cursor = 0;
            for (int iu = 0; iu < 6; iu++) {
                float u = (iu + 0.5F) / 6.0F;
                for (int iv = 0; iv < 6; iv++) {
                    float v = (iv + 0.5F) / 6.0F;
                    int color = ShaderMath.sampleSurface(
                            effect, effect.defaultColor(),
                            effect.defaultIntensity(), effect.defaultSpeed(), effect.defaultOpacity(),
                            u, v, 0.0F, 1.0F, 0.0F,
                            10.0F + u, 64.0F + v, -4.0F + u,
                            1.37F
                    );
                    assertTrue(ColorUtil.alpha(color) > 0,
                            "Эффект " + effect.id() + " вернул полностью прозрачный пиксель");
                    signatures[idx][cursor++] = color;
                }
            }
        }

        // Проверяем, что ни одна пара из 16 эффектов не совпадает по выходному паттерну
        for (int i = 0; i < effects.length; i++) {
            for (int j = i + 1; j < effects.length; j++) {
                assertFalse(Arrays.equals(signatures[i], signatures[j]),
                        "Эффекты " + effects[i].id() + " и " + effects[j].id() + " дали одинаковый паттерн");
            }
        }
    }

    @Test
    void surfaceAndEdgeShadersAnimateOverTimeAndRespectOpacity() {
        for (ShaderEffect effect : ShaderEffect.values()) {
            boolean animated = false;
            for (int sample = 0; sample < 8; sample++) {
                float u = (sample + 1) / 9.0F;
                int c0 = ShaderMath.sampleSurface(effect, effect.defaultColor(),
                        1.1F, 1.2F, 0.80F, u, 0.35F, 0.0F, 1.0F, 0.0F, 1.0F, 2.0F, 3.0F, 0.10F);
                int c1 = ShaderMath.sampleSurface(effect, effect.defaultColor(),
                        1.1F, 1.2F, 0.80F, u, 0.35F, 0.0F, 1.0F, 0.0F, 1.0F, 2.0F, 3.0F, 0.95F);
                if (c0 != c1) {
                    animated = true;
                    break;
                }
            }
            assertTrue(animated, "Эффект " + effect.id() + " должен анимироваться во времени");

            int lowAlpha = ShaderMath.sampleEdge(effect, effect.defaultColor(),
                    1.0F, 1.0F, 0.15F, 0.4F, 1.0F, 2.0F, 3.0F, 0.5F);
            int highAlpha = ShaderMath.sampleEdge(effect, effect.defaultColor(),
                    1.0F, 1.0F, 0.95F, 0.4F, 1.0F, 2.0F, 3.0F, 0.5F);
            assertTrue(ColorUtil.alpha(highAlpha) > ColorUtil.alpha(lowAlpha),
                    "Непрозрачность ребра должна масштабироваться настройкой opacity для " + effect.id());
        }
    }

    @Test
    void worldShaderRendererEmitsCompleteVerticesForBlockAndHands() {
        PoseStack stack = new PoseStack();
        PoseStack.Pose pose = stack.last();

        for (ShaderEffect effect : ShaderEffect.values()) {
            CountingNewEntityConsumer blockConsumer = new CountingNewEntityConsumer();
            WorldShaderRenderer.renderShadedBox(
                    pose, blockConsumer,
                    0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F,
                    12.0F, 65.0F, -8.0F,
                    effect, effect.defaultColor(),
                    effect.defaultIntensity(), effect.defaultSpeed(), effect.defaultOpacity(),
                    0.75F, 4
            );
            WorldShaderRenderer.renderShadedEdge(
                    pose, blockConsumer,
                    0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F,
                    12.0F, 65.0F, -8.0F,
                    effect, effect.defaultColor(),
                    effect.defaultIntensity(), effect.defaultSpeed(), effect.defaultOpacity(),
                    0.02F, 0.4F, 0.75F
            );
            blockConsumer.finishLastVertex();
            assertTrue(blockConsumer.vertices >= 6 * 16 * 4,
                    "Недостаточно вершин для BlockShader эффекта " + effect.id());
            assertEquals(0, blockConsumer.vertices % 4,
                    "Количество вершин квадов BlockShader должно быть кратно 4 для " + effect.id());

            CountingNewEntityConsumer handConsumer = new CountingNewEntityConsumer();
            WorldShaderRenderer.renderHandShader3D(
                    pose, handConsumer,
                    1.0F, 0.1F, 0.25F, true,
                    effect, effect.defaultColor(),
                    effect.defaultIntensity(), effect.defaultSpeed(), effect.defaultOpacity(),
                    1.2F
            );
            handConsumer.finishLastVertex();
            assertTrue(handConsumer.vertices > 0,
                    "HandsShader должен генерировать 3D-геометрию для " + effect.id());
            assertEquals(0, handConsumer.vertices % 4,
                    "Количество вершин квадов HandsShader должно быть кратно 4 для " + effect.id());
        }
    }

    @Test
    void blockAndHandModulesMaintainIndependentShaderSettings() {
        BlockEffectsModule blocks = new BlockEffectsModule();
        HandEffectsModule hands = new HandEffectsModule();

        blocks.selectShaderPreset(ShaderEffect.CYBER_GRID);
        hands.selectShaderPreset(ShaderEffect.VOID_DISTORTION);

        assertEquals(ShaderEffect.CYBER_GRID, blocks.shaderEffect());
        assertEquals(ShaderEffect.VOID_DISTORTION, hands.shaderEffect());

        blocks.setShaderColor(0xFF112233);
        blocks.setShaderIntensity(1.65F);
        blocks.setShaderSpeed(2.10F);
        blocks.setShaderOpacity(0.45F);

        hands.setShaderColor(0xAABBCCDD);
        hands.setShaderIntensity(0.75F);
        hands.setShaderSpeed(0.55F);
        hands.setShaderOpacity(0.90F);

        assertNotEquals(blocks.shaderEffect(), hands.shaderEffect());
        assertNotEquals(blocks.shaderColor(), hands.shaderColor());
        assertNotEquals(blocks.shaderIntensity(), hands.shaderIntensity());
        assertNotEquals(blocks.shaderSpeed(), hands.shaderSpeed());
        assertNotEquals(blocks.shaderOpacity(), hands.shaderOpacity());

        // Сброс эффекта блока не должен затрагивать настройки руки
        blocks.resetCurrentShaderEffect();
        assertEquals(ShaderEffect.CYBER_GRID.defaultColor(), blocks.shaderColor());
        assertEquals(0xAABBCCDD, hands.shaderColor());

        // Полный сброс к заводским значениям возвращает LIQUID_GLASS
        blocks.resetShaderDefaults();
        hands.resetShaderDefaults();
        assertEquals(ShaderEffect.LIQUID_GLASS, blocks.shaderEffect());
        assertEquals(ShaderEffect.LIQUID_GLASS, hands.shaderEffect());
    }

    /**
     * Проверяет, что каждая вершина, отправленная в буфер {@code NEW_ENTITY},
     * содержит все 6 обязательных атрибутов (Position, Color, UV0, Overlay, Light, Normal)
     * и конечные координаты.
     */
    private static final class CountingNewEntityConsumer implements VertexConsumer {
        private static final int ALL_ELEMENTS = 0b111111;
        private int mask = ALL_ELEMENTS;
        int vertices;

        void finishLastVertex() {
            assertEquals(ALL_ELEMENTS, this.mask, "Последняя вершина не заполнила все атрибуты NEW_ENTITY");
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            assertEquals(ALL_ELEMENTS, this.mask, "Предыдущая вершина не заполнила все атрибуты NEW_ENTITY");
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            this.mask = 0b000001;
            this.vertices++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            this.mask |= 0b000010;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            this.mask |= 0b000010;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.mask |= 0b000100;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.mask |= 0b001000;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.mask |= 0b010000;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            assertTrue(Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z));
            this.mask |= 0b100000;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
