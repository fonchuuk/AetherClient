package com.aetherglass.client.mixin;

import com.aetherglass.client.gui.notification.NotificationManager;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Перехватывает получение достижений (Advancements, Блок 7.1) и показывает
 * стеклянное уведомление в правом нижнем углу экрана.
 */
@Mixin(AdvancementToast.class)
public abstract class AdvancementToastMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void aetherglass$onAdvancementToast(AdvancementHolder advancement, CallbackInfo info) {
        if (advancement != null && advancement.value() != null) {
            advancement.value().display().ifPresent(display -> {
                String title = display.getTitle() != null ? display.getTitle().getString() : "Новое достижение";
                NotificationManager.get().pushAdvancement(title);
            });
        }
    }
}
