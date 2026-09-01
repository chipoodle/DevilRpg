package com.chipoodle.devilrpg.capability.tamable_minion;

import com.chipoodle.devilrpg.init.ModCapabilities;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

public class TamableMinionCapability {
    public static final Supplier<AttachmentType<TamableMinionCapabilityInterface>> INSTANCE = ModCapabilities.TAMABLE_MINION;

    private TamableMinionCapability() {
    }
}
