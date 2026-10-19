package com.aetherglass.client.render.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

final class ShapeVertexTest {
    @Test
    void everyQuadVertexIncludesAllEntityAttributes() {
        Map<String, Integer> calls = new HashMap<>();
        VertexConsumer consumer = (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(), new Class<?>[]{VertexConsumer.class},
                (proxy, method, args) -> {
                    calls.merge(method.getName(), 1, Integer::sum);
                    return proxy;
                });
        ShapeBuffer.addQuad(new PoseStack().last(), consumer,
                0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0xFFFFFFFF);
        for (String attribute : new String[]{"addVertex", "setColor", "setUv", "setOverlay", "setLight", "setNormal"}) {
            assertEquals(4, calls.getOrDefault(attribute, 0), attribute);
        }
    }
}
