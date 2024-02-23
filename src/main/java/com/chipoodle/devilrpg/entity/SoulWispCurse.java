package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SoulWispCurse extends SoulWisp{
    public SoulWispCurse(EntityType<? extends SoulWispCurse> type, Level worldIn) {
        super(type, worldIn);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, MobEffects.POISON, MobEffects.GLOWING, SkillEnum.SUMMON_WISP_HEALTH, false);
    }

    @Nullable
    @Override
    public SoulWispCurse getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_CURSE.get().create(level);
    }
}
