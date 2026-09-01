package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerExperienceCapability {
    public static final Supplier<AttachmentType<PlayerExperienceCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_EXPERIENCE;

    private PlayerExperienceCapability() {
    }
}
