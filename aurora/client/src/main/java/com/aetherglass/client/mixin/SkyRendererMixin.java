package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.SkyEffectsModule;
import com.aetherglass.client.render.sky.SkyEffectBlender;
import com.aetherglass.client.render.util.FrameClock;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подмешивает живой перелив цвета неба и рисует 3D-анимированный шейдерный купол
 * ({@code SkyShader}, Блок 4.4) непосредственно в проходе отрисовки неба.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    private static boolean aetherglass$loggedError;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void aetherglass$blendSky(ClientLevel level, float partialTick, Camera camera, SkyRenderState state,
                                      CallbackInfo info) {
        SkyEffectsModule module = ModuleManager.get().typed("sky", SkyEffectsModule.class);
        if (module == null || !module.isEnabled() || state == null) {
            return;
        }
        try {
            state.skyColor = SkyEffectBlender.blendSky(state.skyColor);
            state.sunriseAndSunsetColor = SkyEffectBlender.blendSunrise(state.sunriseAndSunsetColor);
            // Звёзды на видео-куполе уже нарисованы в самой панораме: ванильные
            // поверх неё дают двойной слой и «мусор» на стыках кадров.
            if (SkyEffectBlender.domeActive()) {
                state.starBrightness = 0.0F;
            } else {
                float shimmer = 0.45F + 0.25F * (float) Math.sin(FrameClock.elapsedSeconds() * 1.7D);
                state.starBrightness = Math.max(state.starBrightness, shimmer * SkyEffectBlender.intensity());
            }
        } catch (Throwable throwable) {
            if (!aetherglass$loggedError) {
                aetherglass$loggedError = true;
                System.err.println("[AetherGlass] Ошибка в SkyRendererMixin: " + throwable.getMessage());
            }
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("RETURN"))
    private void aetherglass$renderAnimatedSkyDome(PoseStack poseStack, float sunAngle, float moonAngle,
                                                   float starAngle, MoonPhase moonPhase,
                                                   float rainBrightness, float starBrightness,
                                                   CallbackInfo info) {
        SkyEffectsModule module = ModuleManager.get().typed("sky", SkyEffectsModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        SkyEffectBlender.renderSkyDome(poseStack);
    }
}
