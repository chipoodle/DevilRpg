package com.chipoodle.devilrpg.capability.skill;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class PlayerSkillCapability {
    public static final Supplier<AttachmentType<PlayerSkillCapabilityInterface>> INSTANCE = ModCapabilities.PLAYER_SKILL;

    private PlayerSkillCapability() {
    }
}
