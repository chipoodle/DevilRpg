package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWispChopper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class SoulWispGatherLogItemsGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final double MAXIMUM_DISTANCE_FOR_DELIVERY = 2.5;
    private static final int INVENTORY_FULL_COOLDOWN = 100;
    private final SoulWispChopper soulWisp;
    private final int RADIUS = 5;                                     // Radio de búsqueda para bloques e items
    private boolean isInventoryFull = false;
    private int inventoryFullTicks = 0;

    public SoulWispGatherLogItemsGoal(SoulWispChopper soulWisp) {
        this.soulWisp = soulWisp;
    }

    @Override
    public boolean canUse() {
        if (isInventoryFull) {
            inventoryFullTicks++;
            if (inventoryFullTicks > INVENTORY_FULL_COOLDOWN) {
                isInventoryFull = false;
                inventoryFullTicks = 0;
            }
            return false;
        }
        return !getNearbyLogItems().isEmpty() || soulWisp.hasItemInOffHand();
    }

    @Override
    public void start() {
        DevilRpg.LOGGER.info("======= start SoulWispGatherLogItemsGoal");
        if (this.soulWisp.getOwner() != null)
            this.soulWisp.getNavigation().moveTo(this.soulWisp.getOwner(), SPEED);
    }

    public void stop(){
        DevilRpg.LOGGER.info("======= stop SoulWispGatherLogItemsGoal");
    }

    @Override
    public void tick() {
        if (!this.soulWisp.hasItemInOffHand()) {
            lookForNearestLogItemAndTakeItOffHand();
        } else {
            tryToGiveLogItemToPlayer();
        }
    }

    private List<ItemEntity> getNearbyLogItems() {
        return soulWisp.level.getEntitiesOfClass(ItemEntity.class, soulWisp.getBoundingBox().inflate(RADIUS),
                item -> {
                    ItemStack stack = item.getItem();
                    if (stack.getItem() instanceof BlockItem blockItem) {
                        Block block = blockItem.getBlock();
                        return block.defaultBlockState().is(BlockTags.LOGS);
                    }
                    return false;
                }
        );
    }

    private void lookForNearestLogItemAndTakeItOffHand() {
        List<ItemEntity> items = getNearbyLogItems();
        if (!items.isEmpty()) {
            ItemEntity itemEntity = items.get(0);
            ItemStack logStack = itemEntity.getItem();
            this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, logStack);
            itemEntity.discard();
        }
    }

    private void tryToGiveLogItemToPlayer() {
        Player owner = (Player) this.soulWisp.getOwner();
        if (owner != null && this.soulWisp.distanceTo(owner) <= MAXIMUM_DISTANCE_FOR_DELIVERY) {
            boolean addedSuccessfully = owner.addItem(this.soulWisp.getOffhandItem());
            if (addedSuccessfully) {
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            } else {
                this.soulWisp.spawnAtLocation(this.soulWisp.getOffhandItem());
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                this.isInventoryFull = true;
                this.inventoryFullTicks = 0;
            }
        } else if (owner != null) {
            this.soulWisp.getNavigation().moveTo(owner, SPEED);
        }
    }
}
