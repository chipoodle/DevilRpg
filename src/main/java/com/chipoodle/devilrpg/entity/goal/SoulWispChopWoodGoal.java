package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.entity.SoulWisp;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

public class SoulWispChopWoodGoal extends Goal {
    public static final double SPEED = 1.0;
    public static final int TICKS_UNTIL_NEXT_HIT_LOG = 10;
    public static final int TICKS_UNTIL_NEXT_HIT_LEAVES = 3;
    public static final int TICKS_WITHOUTH_CHOPPING = 30;
    public static final double MAXIMUM_DISTANCE_TO_SQR = 2.5;
    private final SoulWisp soulWisp;
    private final int radius;
    private BlockPos targetBlockPos;
    private int ticksUntilNextHit;
    private int tickswithoutChopping;

    public SoulWispChopWoodGoal(SoulWisp soulWisp) {
        this.soulWisp = soulWisp;
        this.ticksUntilNextHit = 0;
        this.tickswithoutChopping = 0;
        this.radius = 5;
    }

    @Override
    public boolean canUse() {
        BlockPos blockPos = this.soulWisp.blockPosition();
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos closestBlockPos = null;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = blockPos.offset(x, y, z);
                    BlockState blockState = this.soulWisp.level.getBlockState(pos);
                    if (blockState.is(BlockTags.LOGS) || blockState.is(BlockTags.LEAVES)) {
                        double distanceSq = this.soulWisp.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                        if (distanceSq < closestDistanceSq) {
                            closestDistanceSq = distanceSq;
                            closestBlockPos = pos;
                        }
                    }
                }
            }
        }

        if (closestBlockPos != null && soulWisp.hasItemInHand()) {
            this.targetBlockPos = closestBlockPos;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        this.soulWisp.setIsWorking(true);
        BlockState blockState = this.soulWisp.level.getBlockState(this.targetBlockPos);
        if (this.targetBlockPos != null && (blockState.is(BlockTags.LOGS) || blockState.is(BlockTags.LEAVES))) {
            this.soulWisp.getNavigation().moveTo(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ(), SPEED);
        }
    }

    @Override
    public void stop() {
        this.targetBlockPos = null;
        this.ticksUntilNextHit = 0;
        this.tickswithoutChopping = 0;
    }

    @Override
    public void tick() {
        if (this.targetBlockPos != null) {
            BlockState blockState = this.soulWisp.level.getBlockState(this.targetBlockPos);
            if (blockState.is(BlockTags.LOGS) || blockState.is(BlockTags.LEAVES)) {
                double distanceToSqr = this.soulWisp.distanceToSqr(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ());
                //DevilRpg.LOGGER.debug("distanceToSqr {} tickswithoutChopping {}", distanceToSqr, tickswithoutChopping);
                if (distanceToSqr <= MAXIMUM_DISTANCE_TO_SQR) {
                    if (this.ticksUntilNextHit <= 0) {
                        ItemStack mainHandItem = soulWisp.getMainHandItem();
                        if (mainHandItem.getItem() instanceof AxeItem) {

                            if (blockState.is(BlockTags.LOGS)) {
                                this.ticksUntilNextHit = TICKS_UNTIL_NEXT_HIT_LOG;
                            }
                            if (blockState.is(BlockTags.LEAVES)) {
                                this.ticksUntilNextHit = TICKS_UNTIL_NEXT_HIT_LEAVES;
                            }
                            this.hurtAndBreak(1, this.soulWisp, (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND), mainHandItem, (Player) soulWisp.getOwner());
                            this.soulWisp.level.destroyBlock(targetBlockPos, true, soulWisp);
                            tickswithoutChopping = 0;
                            //DevilRpg.LOGGER.debug("mainHandItem getUseDuration {}", mainHandItem.getUseDuration());
                        }
                    } else {
                        this.ticksUntilNextHit--;
                    }
                } else {
                    tickswithoutChopping++;
                    if (tickswithoutChopping > TICKS_WITHOUTH_CHOPPING) {
                        Vec3 randomPos = DefaultRandomPos.getPosTowards(this.soulWisp, radius, 3, Vec3.atBottomCenterOf(this.soulWisp.blockPosition()), SPEED);
                        if (randomPos != null) {
                            PathNavigation navigation = this.soulWisp.getNavigation();
                            WalkNodeEvaluator nodeEvaluator = (WalkNodeEvaluator) navigation.getNodeEvaluator();
                            nodeEvaluator.setCanPassDoors(true);
                            navigation.moveTo(randomPos.x, randomPos.y, randomPos.z, SPEED);
                            tickswithoutChopping = 0;
                        }
                    } else {
                        this.soulWisp.getNavigation().moveTo(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ(), SPEED);
                    }
                }
            }
        }
    }

    public <T extends LivingEntity> void hurtAndBreak(int damage, T livingEntity, Consumer<T> tConsumer, ItemStack itemStack, Player owner) {
        if (!livingEntity.level.isClientSide && (!(livingEntity instanceof Player) || !((Player) livingEntity).getAbilities().instabuild)) {
            if (itemStack.isDamageableItem()) {
                damage = itemStack.getItem().damageItem(itemStack, damage, livingEntity, tConsumer);
                if (itemStack.hurt(damage, livingEntity.getRandom(), null)) {
                    tConsumer.accept(livingEntity);
                    Item item = itemStack.getItem();
                    itemStack.shrink(1);
                    owner.awardStat(Stats.ITEM_BROKEN.get(item));
                    itemStack.setDamageValue(0);
                }

                //DevilRpg.LOGGER.debug("damage: {} max damage: {}",itemStack.getDamageValue(), itemStack.getMaxDamage());

            }
        }
    }

}