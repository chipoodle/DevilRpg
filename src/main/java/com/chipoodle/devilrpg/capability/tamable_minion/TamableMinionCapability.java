package com.chipoodle.devilrpg.capability.tamable_minion;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;


public class TamableMinionCapability {
    public static final Capability<TamableMinionCapabilityInterface> INSTANCE = CapabilityManager.get(new CapabilityToken<>() {
    });

    private TamableMinionCapability() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(TamableMinionCapabilityInterface.class);
    }
}