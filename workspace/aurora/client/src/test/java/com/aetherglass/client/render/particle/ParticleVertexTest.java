package com.aetherglass.client.render.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class ParticleVertexTest {
    @Test
    void emitsEveryEntityVertexAttribute() {
        Set<String> attributes = new HashSet<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    attributes.add(method.getName());
                    return proxy;
                });
        ParticleSystem.vertex(consumer, new Matrix4f(), 1, 2, 3,
                0, 1, 0xFFFFFFFF, 0, 0, 1);
        assertTrue(attributes.containsAll(Set.of("addVertex", "setUv", "setColor",
                "setOverlay", "setLight", "setNormal")),
                "entityTranslucent не допускает пропущенных overlay/normal: " + attributes);
    }
}
