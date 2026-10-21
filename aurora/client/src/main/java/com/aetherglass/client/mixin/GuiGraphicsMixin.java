package com.aetherglass.client.mixin;

import com.aetherglass.client.gui.inventory.InventoryTheme;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.InventoryModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Прямая подмена текстур контейнеров ({@code textures/gui/container/*.png})
 * и цвета заголовков контейнеров при активном модуле {@link InventoryModule}.
 */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {

    @ModifyVariable(
            method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private Identifier aetherglass$replaceContainerTexture(Identifier texture) {
        if (texture == null || !"minecraft".equals(texture.getNamespace())) {
            return texture;
        }
        String path = texture.getPath();
        if (!path.startsWith("textures/gui/container/")) {
            return texture;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module == null || !module.isEnabled()) {
            return texture;
        }
        InventoryTheme theme = module.theme();
        return theme != null ? theme.resolveContainerTexture(texture) : texture;
    }

    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private int aetherglass$recolorContainerComponentLabel(int color) {
        if ((color & 0x00FFFFFF) != 0x404040) {
            return color;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof AbstractContainerScreen<?>)) {
            return color;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module != null && module.isEnabled() && module.theme().replacesContainerTexture()) {
            return 0xFFF2E8FF;
        }
        return color;
    }

    @ModifyVariable(
            method = "drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private int aetherglass$recolorContainerStringLabel(int color) {
        if ((color & 0x00FFFFFF) != 0x404040) {
            return color;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof AbstractContainerScreen<?>)) {
            return color;
        }
        InventoryModule module = ModuleManager.get().typed("inventory", InventoryModule.class);
        if (module != null && module.isEnabled() && module.theme().replacesContainerTexture()) {
            return 0xFFF2E8FF;
        }
        return color;
    }
}
