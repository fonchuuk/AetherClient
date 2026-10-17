package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.SkyEffectsModule;
import com.aetherglass.client.render.sky.SkyEffectBlender;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эффекты неба (5.4).
 *
 * <p>Мы не закрываем экран цветной плашкой и не используем шейдеры: меняется состояние
 * отрисовки неба, которое игра и так заполняет каждый кадр. Поэтому цвет неба настоящий —
 * он работает при любом угле обзора и в любом измерении, где небо рисуется.</p>
 */
@Mixin(SkyRenderer.class)
public final class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void aurora$blendSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
                                 CallbackInfo callback) {
        SkyEffectsModule module = ModuleManager.get().typed("sky", SkyEffectsModule.class);
        if (module == null || !module.isEnabled() || state == null) {
            return;
        }
        if (state.skybox != DimensionType.Skybox.OVERWORLD) {
            // В других измерениях небо рисуется иначе: не подменяем то, чего нет.
            return;
        }
        try {
            state.skyColor = SkyEffectBlender.blendSky(state.skyColor);
            state.sunriseAndSunsetColor = SkyEffectBlender.blendSunrise(state.sunriseAndSunsetColor);
        } catch (Throwable throwable) {
            AuroraLog.error("эффекты неба", throwable);
        }
    }
}
