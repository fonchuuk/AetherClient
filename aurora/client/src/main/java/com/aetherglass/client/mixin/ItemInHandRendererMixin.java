package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.render.hand.HandEffectRenderer;
import com.aetherglass.client.utils.AuroraLog;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Эффекты руки (5.3).
 *
 * <p>В самом начале отрисовки руки мы накладываем своё преобразование на стек матриц,
 * а в конце обязательно его снимаем — так состояние рендера остаётся согласованным,
 * даже если что-то пойдёт не так. Руку по-прежнему рисует игра.</p>
 */
@Mixin(ItemInHandRenderer.class)
public final class ItemInHandRendererMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void aurora$beginHands(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                   LocalPlayer player, int light, CallbackInfo callback) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        try {
            HandEffectRenderer.applyPose(pose, player, partialTick);
            HandEffectRenderer.submitAccent(collector, pose);
        } catch (Throwable throwable) {
            AuroraLog.error("эффекты руки", throwable);
        }
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void aurora$endHands(float partialTick, PoseStack pose, SubmitNodeCollector collector,
                                 LocalPlayer player, int light, CallbackInfo callback) {
        HandEffectsModule module = ModuleManager.get().typed("hands", HandEffectsModule.class);
        if (module == null || !module.isEnabled()) {
            return;
        }
        try {
            HandEffectRenderer.restorePose(pose);
        } catch (Throwable throwable) {
            AuroraLog.error("возврат стека матриц руки", throwable);
        }
    }
}
