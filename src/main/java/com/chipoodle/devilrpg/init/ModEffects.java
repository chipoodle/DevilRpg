package com.chipoodle.devilrpg.init;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.effects.MobEffectEntangling;
import com.chipoodle.devilrpg.effects.MobEffectKnockbackResistance;
import com.chipoodle.devilrpg.effects.MobEffectVineFleshPuppet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, DevilRpg.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> KNOCKBACK_RESISTANCE = EFFECTS.register("knockback_resistance", MobEffectKnockbackResistance::new);
    public static final DeferredHolder<MobEffect, MobEffect> VINE_FLESH_PUPPET = EFFECTS.register("vine_flesh_puppet", MobEffectVineFleshPuppet::new);
    public static final DeferredHolder<MobEffect, MobEffect> ENTANGLING = EFFECTS.register("entangling", MobEffectEntangling::new);

}
