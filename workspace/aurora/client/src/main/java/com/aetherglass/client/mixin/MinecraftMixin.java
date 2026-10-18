package com.aetherglass.client.mixin;

import com.aetherglass.client.AetherGlassClient;
import com.aetherglass.client.gui.screen.AuroraMenuScreen;
import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.InterfaceModule;
import com.aetherglass.client.utils.AuroraLog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Подмена главного меню.
 *
 * <p>Перехватывается только момент, когда игра собирается показать своё титульное меню.
 * Если в настройках включён «свой главный экран», вместо него открывается наш —
 * и только он. Остальные экраны игра открывает как обычно.</p>
 */
@Mixin(Minecraft.class)
public final class MinecraftMixin {

    /**
     * Наше меню уже однажды не открылось.
     *
     * <p>После первой ошибки подмена отключается до перезапуска игры: иначе
     * каждая новая попытка снова падала бы и снова писала бы в лог.</p>
     */
    private boolean menuBroken;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void aurora$useOwnMenu(Screen screen, CallbackInfo callback) {
        if (this.menuBroken) {
            return;
        }
        if (!(screen instanceof TitleScreen) || screen instanceof AuroraMenuScreen) {
            return;
        }
        InterfaceModule look = ModuleManager.get().typed("interface", InterfaceModule.class);
        if (look == null || !look.customMenuEnabled()) {
            return;
        }
        try {
            callback.cancel();
            Minecraft client = Minecraft.getInstance();
            client.setScreen(new AuroraMenuScreen());
            AetherGlassClient.noteMenuReplaced();
        } catch (Throwable throwable) {
            // Если наш экран по какой-то причине не открылся, игра должна показать свой.
            // Поэтому отмену надо снять: иначе игра останется вообще без меню.
            AuroraLog.error("не удалось открыть главное меню клиента", throwable);
            this.menuBroken = true;
        }
    }
}
