package com.aetherglass.client.mixin;

import com.mojang.authlib.yggdrasil.ProfileResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.CompletableFuture;

/**
 * Доступ к полям {@code user} и {@code profileFuture} в {@link Minecraft}
 * для мгновенной смены оффлайн-аккаунта прямо в игре без перезапуска клиента.
 */
@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {
    @Accessor("user")
    @Mutable
    void aetherglass$setUser(User user);

    @Accessor("profileFuture")
    @Mutable
    void aetherglass$setProfileFuture(CompletableFuture<ProfileResult> profileFuture);
}
