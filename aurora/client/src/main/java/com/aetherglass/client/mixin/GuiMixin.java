package com.aetherglass.client.mixin;

import com.aetherglass.client.render.hud.BackdropBlur;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Снимает размытый фон для стеклянных панелей HUD.
 *
 * <p>Точка важна: здесь мир уже отрисован в кадровый буфер, но ни один элемент
 * интерфейса ещё не отправлен — значит копию экрана можно делать без риска
 * разъехавшегося порядка отрисовки.</p>
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void aetherglass$captureBackdrop(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo info) {
        BackdropBlur.capture();
    }
}
