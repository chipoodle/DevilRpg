package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.entity.goal.*;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SoulWispForester extends SoulWisp {

    private static final EntityDataAccessor<Boolean> DATA_CHOPPING = SynchedEntityData.defineId(SoulWispForester.class, EntityDataSerializers.BOOLEAN);

    public SoulWispForester(EntityType<? extends SoulWispForester> type, Level worldIn) {
        super(type, worldIn);
    }

    protected void registerGoals() {
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(1, new SoulWispFollowOwnerGoal(this, 1.0D, 8.0F, 12.0F, true));
        this.goalSelector.addGoal(2, new SoulWispPlantSaplingsGoal(this));
       // this.goalSelector.addGoal(3, new SoulWispHarvestGrassGoal(this));

        this.goalSelector.addGoal(4, new FloatGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CHOPPING, false);
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, null, null, SkillEnum.SUMMON_WISP_FORESTER, true);
        //this.goalSelector.addGoal(3, new SoulWispHarvestGrassGoal(this));
    }


    @Nullable
    @Override
    public SoulWispForester getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_FORESTER.get().create(level);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.getOwner() != null && player == this.getOwner() && hand == InteractionHand.MAIN_HAND) {
            if (this.isOwnedBy(player) || this.isTame()) {
                ItemStack playerItemStack = player.getItemInHand(hand);

                // Verifica si el jugador tiene la mano vacía para regresar el hacha
                if (playerItemStack.isEmpty()) {

                }

            }
        }
        return super.mobInteract(player,hand);
    }

    private void dropPreviousItem(@NotNull InteractionHand hand) {
        ItemStack mobItemStack = this.getItemInHand(hand);
        Item item = mobItemStack.getItem();
        if (!item.equals(Items.AIR)) {
            ItemEntity itementity = new ItemEntity(this.level, this.getX(), this.getY(), this.getZ(), mobItemStack);
            this.level.addFreshEntity(itementity);
        }
    }

}
