package com.aetherglass.client.modules;

import com.aetherglass.client.modules.impl.BlockEffectsModule;
import com.aetherglass.client.modules.impl.CombatModule;
import com.aetherglass.client.modules.impl.CrosshairModule;
import com.aetherglass.client.modules.impl.DiscordRpcModule;
import com.aetherglass.client.modules.impl.HandEffectsModule;
import com.aetherglass.client.modules.impl.HudModule;
import com.aetherglass.client.modules.impl.InterfaceModule;
import com.aetherglass.client.modules.impl.InventoryModule;
import com.aetherglass.client.modules.impl.MobInfoModule;
import com.aetherglass.client.modules.impl.ParticleModule;
import com.aetherglass.client.modules.impl.RainbowModule;
import com.aetherglass.client.modules.impl.SkyEffectsModule;

/** Единственное место, где перечислены все модули клиента. */
public final class ModuleRegistry {
    private ModuleRegistry() {
    }

    /** Создаёт модули и передаёт их менеджеру. */
    public static void createAll() {
        ModuleManager manager = ModuleManager.get();
        manager.register(new ParticleModule());
        manager.register(new BlockEffectsModule());
        manager.register(new HandEffectsModule());
        manager.register(new SkyEffectsModule());
        manager.register(new RainbowModule());
        manager.register(new CrosshairModule());
        manager.register(new HudModule());
        manager.register(new InventoryModule());
        manager.register(new InterfaceModule());
        manager.register(new DiscordRpcModule());
        manager.register(new CombatModule());
        manager.register(new MobInfoModule());
    }
}
