package com.chipoodle.devilrpg.capability.auxiliar;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;

public class PlayerAuxiliaryCapability {
    public static final Capability<PlayerAuxiliaryCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerAuxiliaryCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerAuxiliaryCapabilityInterface.class);
    }

}
