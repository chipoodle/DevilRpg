package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class ModDamageTypes {
    public static final ResourceKey<DamageType> MINION_DEATH = register("minion_death");

    private static ResourceKey<DamageType> register(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(DevilRpg.MODID, name));
    }

}
