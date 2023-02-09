package com.chipoodle.devilrpg.capability.mana;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class PlayerManaCapability {
    public static final Capability<PlayerManaCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerManaCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerManaCapabilityInterface.class);
    }
}
