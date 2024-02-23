package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.effects.MobEffectKnockbackResistance;
import com.chipoodle.devilrpg.effects.MobEffectSlowness;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, DevilRpg.MODID);

    public static final RegistryObject<MobEffect> KNOCKBACK_RESISTANCE = EFFECTS.register("knockback_resistance", MobEffectKnockbackResistance::new);
    public static final RegistryObject<MobEffect> SLOWNESS = EFFECTS.register("slowness", MobEffectSlowness::new);

}
