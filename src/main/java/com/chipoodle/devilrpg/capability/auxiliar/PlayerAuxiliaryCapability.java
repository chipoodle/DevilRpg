package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerAuxiliaryCapability {
    public static final Supplier<AttachmentType<PlayerAuxiliaryCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_AUXILIARY;

    private PlayerAuxiliaryCapability() {
    }
}
