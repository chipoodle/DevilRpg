package com.chipoodle.devilrpg.capability.experience;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class PlayerExperienceCapability {
    public static final Capability<PlayerExperienceCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerExperienceCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerExperienceCapabilityInterface.class);
    }
}
