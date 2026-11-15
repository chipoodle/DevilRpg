package com.chipoodle.devilrpg.mixin;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.FoodStateNbt;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shared pre-sleep handshake for both vanilla SleepInBed and the forced bedtime path.
 * It runs immediately before the actual sleep transition and never cancels it; a failed
 * meal leaves the villager hungry but does not block sleep.
 */
@Mixin(LivingEntity.class)
public abstract class SleepMealMixin {
    @Inject(method = "startSleeping", at = @At("HEAD"))
    private void devilrpg$mealBeforeSleep(BlockPos bed, CallbackInfo ci) {
        if ((Object) this instanceof Villager villager
                && villager.level() instanceof ServerLevel level) {
            if (Boolean.getBoolean("devilrpg.food.sleepdiag")) {
                DevilRpg.LOGGER.info("[SLEEP-MEAL-HOOK] uuid={} bed={} hunger={} lastMeal={} gameTime={} callback={}",
                        villager.getUUID(), bed.toShortString(), VillageManager.hambre(villager),
                        FoodStateNbt.lastMeal(villager.getPersistentData()), level.getGameTime(), ci.getId());
            }
            VillageManager.comerAntesDeDormir(level, villager);
        }
    }
}
