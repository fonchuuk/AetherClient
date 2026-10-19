package com.aetherglass.client.mixin;

import com.aetherglass.client.input.InputTracker;
import com.aetherglass.client.input.KeybindManager;
import com.aetherglass.client.modules.Module;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.render.hud.HudPainter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Событийный перехват кликов мыши для точного счётчика CPS ({@link InputTracker})
 * и быстрых иконок панели {@code Quick Access} на HUD при открытом чате.
 */
@Mixin(MouseHandler.class)
public final class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void aurora$onMouseButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (action != GLFW.GLFW_PRESS || buttonInfo == null || this.minecraft == null) {
            return;
        }
        if (window != this.minecraft.getWindow().handle()) {
            return;
        }
        int btn = buttonInfo.button();

        if (this.minecraft.screen instanceof ChatScreen && btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            double guiX = this.minecraft.mouseHandler.xpos()
                    * (double) this.minecraft.getWindow().getGuiScaledWidth()
                    / (double) Math.max(1, this.minecraft.getWindow().getScreenWidth());
            double guiY = this.minecraft.mouseHandler.ypos()
                    * (double) this.minecraft.getWindow().getGuiScaledHeight()
                    / (double) Math.max(1, this.minecraft.getWindow().getScreenHeight());
            for (HudPainter.QuickButtonRect rect : HudPainter.quickButtons()) {
                if (rect.contains(guiX, guiY)) {
                    if (rect.cleanScreenButton()) {
                        KeybindManager.get().toggleCleanScreen();
                    } else {
                        Module m = ModuleManager.get().module(rect.moduleId());
                        if (m != null) {
                            m.toggle();
                        }
                    }
                    return;
                }
            }
        }

        if (this.minecraft.screen == null
                && (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT || btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            InputTracker.get().recordClick(btn);
        }
    }
}
