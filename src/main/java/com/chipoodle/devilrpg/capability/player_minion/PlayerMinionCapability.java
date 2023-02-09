package com.chipoodle.devilrpg.capability.player_minion;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class PlayerMinionCapability {
    public static final Capability<PlayerMinionCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerMinionCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerMinionCapabilityInterface.class);
    }
}
