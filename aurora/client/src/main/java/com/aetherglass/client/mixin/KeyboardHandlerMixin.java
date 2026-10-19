package com.aetherglass.client.mixin;

import com.aetherglass.client.input.KeybindManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Событийный перехват нажатий клавиш для {@link KeybindManager} без опроса в {@code onTick}.
 */
@Mixin(KeyboardHandler.class)
public final class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("TAIL"))
    private void aurora$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || event == null) {
            return;
        }
        if (this.minecraft == null || this.minecraft.screen != null) {
            return;
        }
        if (window != this.minecraft.getWindow().handle()) {
            return;
        }
        KeybindManager.get().onKeyPress(event.key());
    }
}
