package com.chipoodle.devilrpg.capability.skill;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class PlayerSkillCapability {
    public static final Capability<PlayerSkillCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private PlayerSkillCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(PlayerSkillCapabilityInterface.class);
    }

}
