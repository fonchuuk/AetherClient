package com.aetherglass.client.mixin;

import com.aetherglass.client.modules.ModuleManager;
import com.aetherglass.client.modules.impl.InterfaceModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ультра-легковесный (0 аллокаций в кадр) неоновый плащ AetherGlass и бейдж [A] у ника.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin extends Player {
    @Unique
    private static final ClientAsset.Texture AETHER_CAPE = new ClientAsset.ResourceTexture(
            Identifier.fromNamespaceAndPath("aetherglass", "cape"),
            Identifier.fromNamespaceAndPath("aetherglass", "textures/cape/aetherglass_cape.png")
    );
    @Unique
    private static final Component AETHER_BADGE = Component.literal("[A] ")
            .withStyle(s -> s.withColor(0xB44CFF).withBold(true));

    @Unique
    private PlayerSkin aetherglass$lastRawSkin;
    @Unique
    private PlayerSkin aetherglass$cachedCapeSkin;
    @Unique
    private Component aetherglass$lastRawName;
    @Unique
    private Component aetherglass$cachedBadgedName;

    protected AbstractClientPlayerMixin() {
        super(null, null);
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void aetherglass$applyNeonCape(CallbackInfoReturnable<PlayerSkin> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (Object) this != mc.player) {
            return;
        }
        InterfaceModule look = ModuleManager.get().typed("interface", InterfaceModule.class);
        if (look == null || !look.capeAndBadge()) {
            return;
        }
        PlayerSkin raw = cir.getReturnValue();
        if (raw == null) {
            return;
        }
        if (raw != this.aetherglass$lastRawSkin || this.aetherglass$cachedCapeSkin == null) {
            this.aetherglass$lastRawSkin = raw;
            this.aetherglass$cachedCapeSkin = new PlayerSkin(
                    raw.body(), AETHER_CAPE, AETHER_CAPE, raw.model(), raw.secure()
            );
        }
        cir.setReturnValue(this.aetherglass$cachedCapeSkin);
    }

    @Override
    public Component getDisplayName() {
        Component raw = super.getDisplayName();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (Object) this != mc.player || raw == null) {
            return raw;
        }
        InterfaceModule look = ModuleManager.get().typed("interface", InterfaceModule.class);
        if (look == null || !look.capeAndBadge()) {
            return raw;
        }
        if (raw != this.aetherglass$lastRawName || this.aetherglass$cachedBadgedName == null) {
            this.aetherglass$lastRawName = raw;
            MutableComponent combined = Component.empty();
            combined.append(AETHER_BADGE);
            combined.append(raw);
            this.aetherglass$cachedBadgedName = combined;
        }
        return this.aetherglass$cachedBadgedName;
    }
}
