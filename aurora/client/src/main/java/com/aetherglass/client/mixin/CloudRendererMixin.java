package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.SkyEffectsModule;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.renderer.CloudRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Убирает ванильные кубические облака, пока включён «Эффекты неба».
 *
 * <p>Раньше видео-купол рисовался поверх ванильных облаков, и чёрные кубические
 * разводы перекрывали половину небосвода. Облака — часть ванильного прохода
 * {@code CloudRenderer#render}, который полностью пересобирается каждый кадр;
 * отменяем его целиком (не «прячем за куполом», а не рисуем — это ещё и экономит
 * время кадра).</p>
 */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void aetherglass$hideVanillaClouds(int ticks, CloudStatus status, float partialTick,
                                               Vec3 cameraPos, long gameTime, float cloudHeight,
                                               CallbackInfo info) {
        SkyEffectsModule module = ModuleManager.get().typed("sky", SkyEffectsModule.class);
        if (module != null && module.isEnabled() && module.hidesVanillaClouds()) {
            info.cancel();
        }
    }
}
