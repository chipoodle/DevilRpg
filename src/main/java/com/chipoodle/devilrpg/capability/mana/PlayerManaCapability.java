package com.chipoodle.devilrpg.capability.mana;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerManaCapability {
    public static final Supplier<AttachmentType<PlayerManaCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_MANA;

    private PlayerManaCapability() {
    }
}
