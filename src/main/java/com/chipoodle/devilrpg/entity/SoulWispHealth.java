package com.chipoodle.devilrpg.entity;

import net.minecraft.core.Holder;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SoulWispHealth extends SoulWisp {
    public SoulWispHealth(EntityType<? extends SoulWispHealth> type, Level worldIn) {
        super(type, worldIn);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, MobEffects.HEALTH_BOOST, null, SkillEnum.SUMMON_WISP_HEALTH, true);
    }

    @Nullable
    @Override
    public SoulWispHealth getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_HEALTH.get().create(level);
    }

    public void setSecondaryEffect(Integer points, Holder<MobEffect> regeneration) {
        if (points > 0) efectoSecundario = regeneration;
    }
}
