package com.chipoodle.devilrpg.capability.stamina;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class PlayerStaminaCapability {
    public static final Capability<PlayerStaminaCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerStaminaCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerStaminaCapabilityInterface.class);
    }
}
