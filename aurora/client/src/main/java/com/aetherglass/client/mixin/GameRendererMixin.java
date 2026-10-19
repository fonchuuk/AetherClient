package com.aetherglass.client.mixin;

import com.aetherglass.client.render.hand.HandShaderCollector;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Страховочный сброс очереди {@link HandShaderCollector} после завершения рендера руки.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderItemInHand", at = @At("RETURN"))
    private void aetherglass$afterRenderItemInHand(float partialTick, boolean sleeping, Matrix4f projectionMatrix, CallbackInfo ci) {
        HandShaderCollector.flushPostHands();
    }
}
