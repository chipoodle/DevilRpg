package com.chipoodle.devilrpg.capability.stamina;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerStaminaCapability {
    public static final Supplier<AttachmentType<PlayerStaminaCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_STAMINA;

    private PlayerStaminaCapability() {
    }
}
