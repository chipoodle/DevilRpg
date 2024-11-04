package com.chipoodle.devilrpg.entity;

import com.chipoodle.devilrpg.entity.goal.SoulWispChopWoodGoal;
import com.chipoodle.devilrpg.entity.goal.SoulWispFollowOwnerGoal;
import com.chipoodle.devilrpg.init.ModEntities;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
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

public class SoulWispChopper extends SoulWisp {
    public SoulWispChopper(EntityType<? extends SoulWispChopper> type, Level worldIn) {
        super(type, worldIn);
    }

    protected void registerGoals() {
        // super.registerGoals();
        //this.goalSelector.addGoal(0, new SoulWispCollectLogsGoal(this,5));
        this.goalSelector.addGoal(1, new SoulWispChopWoodGoal(this));

        this.goalSelector.addGoal(2, new FloatGoal(this));
        // this.goalSelector.addGoal(3, new WaterAvoidingRandomFlyingGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new SoulWispFollowOwnerGoal(this, 1.0D, 3.0F, 7.0F, true));
        this.goalSelector.addGoal(9, new SoulWisp.WanderGoal());
        //this.goalSelector.addGoal(10, new LookAtPlayerGoal(this, Mob.class, 8.0F));
        //this.goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    public void updateLevel(Player owner) {
        super.updateLevel(owner, null, null, SkillEnum.SUMMON_WISP_CHOPPER, true);
    }

    @Nullable
    @Override
    public SoulWispChopper getBreedOffspring(@NotNull ServerLevel level, @NotNull AgeableMob ageableMob) {
        return ModEntities.WISP_CHOPPER.get().create(level);
    }

    @Override
    public @NotNull InteractionResult mobInteract(@NotNull Player player, @NotNull InteractionHand hand) {
        if (this.getOwner() != null && player == this.getOwner() && hand == InteractionHand.MAIN_HAND) {
            if (this.isOwnedBy(player) || this.isTame()) {
                ItemStack playerItemStack = player.getItemInHand(hand);

                if (playerItemStack.getItem() instanceof AxeItem axeItem) {
                    dropPreviousItem(hand);
                    ItemStack itemstack = playerItemStack.copy();
                    itemstack.setCount(1);
                    this.setItemInHand(InteractionHand.MAIN_HAND, itemstack);
                    this.removeInteractionItem(player, playerItemStack);
                    this.level.playSound(player, this, SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 2.0F, 1.0F);
                    return InteractionResult.SUCCESS;
                }
            }
            //return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
