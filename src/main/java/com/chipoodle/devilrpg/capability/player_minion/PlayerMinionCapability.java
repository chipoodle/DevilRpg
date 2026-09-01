package com.chipoodle.devilrpg.capability.player_minion;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerMinionCapability {
    public static final Supplier<AttachmentType<PlayerMinionCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_MINION;

    private PlayerMinionCapability() {
    }
}
