package com.chipoodle.devilrpg.mixin;

import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HarvestFarmland.class)
public abstract class HarvestFarmlandMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean devilrpg$awardHarvestXp(ServerLevel level, BlockPos pos, boolean drop, Entity entity) {
        boolean destroyed = level.destroyBlock(pos, drop, entity);
        if (destroyed && entity instanceof Villager villager) {
            VillageManager.premiarOficio(villager, 1);
        }
        return destroyed;
    }
}
