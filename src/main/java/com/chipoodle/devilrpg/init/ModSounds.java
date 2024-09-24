package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DevilRpg.MODID);
    public static final RegistryObject<SoundEvent> METAL_SWORD_SOUND = SOUND_EVENTS.register("metal_sword_sound", () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(DevilRpg.MODID, "metal_sword_sound")));

}
