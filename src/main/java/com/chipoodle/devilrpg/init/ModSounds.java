package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, DevilRpg.MODID);
    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_SWORD_SOUND = SOUND_EVENTS.register("metal_sword_sound", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(DevilRpg.MODID, "metal_sword_sound")));

}
