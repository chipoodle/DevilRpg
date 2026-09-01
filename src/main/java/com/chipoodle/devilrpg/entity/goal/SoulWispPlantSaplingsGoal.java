package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWisp;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;

import java.util.Comparator;
import java.util.Optional;

public class SoulWispPlantSaplingsGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final int RADIUS = 10; // Radio de búsqueda de saplings y lugares para plantar
    private static final int MIN_DISTANCE_BETWEEN_SAPLINGS = 3; // Evita plantar muy cerca de otro sapling

    private final SoulWisp soulWisp;
    private ItemEntity targetSapling = null;
    private BlockPos targetPlantingPos = null;

    public SoulWispPlantSaplingsGoal(SoulWisp soulWisp) {
        this.soulWisp = soulWisp;
    }

    @Override
    public boolean canUse() {
        return findNearbySapling().isPresent() || hasSaplingsInHands();
    }

    @Override
    public void start() {
        DevilRpg.LOGGER.info("======= start SoulWispPlantSaplingsGoal");
    }

    @Override
    public void stop() {
        targetSapling = null;
        targetPlantingPos = null;
        DevilRpg.LOGGER.info("======= stop SoulWispPlantSaplingsGoal");
    }

    @Override
    public void tick() {
        if (!hasSaplingsInHands()) {
            moveToNearestSaplingAndTakeIt();
        } else {
            moveToPlantingPositionAndPlant();
        }
    }

    // Busca el sapling más cercano en el suelo
    private Optional<ItemEntity> findNearbySapling() {
        if (soulWisp.getOwner() == null)
            return Optional.empty();

        return soulWisp.level().getEntitiesOfClass(ItemEntity.class, soulWisp.getOwner().getBoundingBox().inflate(RADIUS),
                        item -> isSapling(item.getItem()))
                .stream()
                .min(Comparator.comparingDouble(item -> item.distanceTo(soulWisp)));
    }

    // Se mueve hacia el sapling más cercano y lo recoge
    private void moveToNearestSaplingAndTakeIt() {
        if (targetSapling == null) {
            findNearbySapling().ifPresent(item -> targetSapling = item);
        }

        if (targetSapling != null) {
            soulWisp.getNavigation().moveTo(targetSapling, SPEED);
            if (soulWisp.distanceTo(targetSapling) < 1.5) {
                pickUpSapling(targetSapling);
                targetSapling = null;
            }
        }
    }

    // Recoge el sapling y lo guarda en la mano
    private void pickUpSapling(ItemEntity saplingEntity) {
        ItemStack saplingStack = saplingEntity.getItem();

        if (!isSapling(soulWisp.getItemBySlot(EquipmentSlot.OFFHAND))) {
            soulWisp.setItemSlot(EquipmentSlot.OFFHAND, saplingStack);
            saplingEntity.discard();
        } else if (!isSapling(soulWisp.getItemBySlot(EquipmentSlot.MAINHAND))) {
            soulWisp.setItemSlot(EquipmentSlot.MAINHAND, saplingStack);
            saplingEntity.discard();
        }
    }

    // Busca un lugar válido para plantar el sapling y se mueve hacia allí
    private void moveToPlantingPositionAndPlant() {
        Level level = soulWisp.level();

        LivingEntity owner = soulWisp.getOwner();
        if (owner == null)
            return;

        if (targetPlantingPos == null) {
            targetPlantingPos = findValidPlantingPosition(level, owner.blockPosition()).orElse(null);
        }

        if (targetPlantingPos != null) {
            soulWisp.getNavigation().moveTo(targetPlantingPos.getX(), targetPlantingPos.getY(), targetPlantingPos.getZ(), SPEED);
            if (soulWisp.blockPosition().closerThan(targetPlantingPos, 1.5)) {
                if (plantSapling(level, targetPlantingPos, EquipmentSlot.OFFHAND) ||
                        plantSapling(level, targetPlantingPos, EquipmentSlot.MAINHAND)) {
                    DevilRpg.LOGGER.info("Sapling plantado en " + targetPlantingPos);
                    targetPlantingPos = null; // Reinicia la búsqueda
                }
            }
        }
    }

    private boolean hasSaplingsInHands() {
        return isSapling(soulWisp.getItemBySlot(EquipmentSlot.OFFHAND)) ||
                isSapling(soulWisp.getItemBySlot(EquipmentSlot.MAINHAND));
    }

    private boolean plantSapling(Level level, BlockPos pos, EquipmentSlot hand) {
        ItemStack stack = soulWisp.getItemBySlot(hand);
        if (isSapling(stack)) {
            BlockItem blockItem = (BlockItem) stack.getItem();

            clearGrassAndFlowers(level, pos);

            if (isAir(level, pos)) {
                level.setBlock(pos, blockItem.getBlock().defaultBlockState(), 3);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    soulWisp.setItemSlot(hand, ItemStack.EMPTY);
                }
                return true;
            }
        }
        return false;
    }

    private Optional<BlockPos> findValidPlantingPosition(Level level, BlockPos center) {
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (isValidPlantingSpot(level, pos)) {
                    return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }

    private boolean isValidPlantingSpot(Level level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        boolean isValidSoil = level.getBlockState(belowPos).is(Blocks.GRASS_BLOCK) ||
                level.getBlockState(belowPos).is(Blocks.DIRT) ||
                level.getBlockState(belowPos).is(Blocks.PODZOL);

        boolean isAirAbove = isAir(level, pos);
        boolean isNotCrowded = !isNearbySapling(level, pos);

        return isValidSoil && isAirAbove && isNotCrowded;
    }

    private boolean isNearbySapling(Level level, BlockPos pos) {
        for (int dx = -MIN_DISTANCE_BETWEEN_SAPLINGS; dx <= MIN_DISTANCE_BETWEEN_SAPLINGS; dx++) {
            for (int dz = -MIN_DISTANCE_BETWEEN_SAPLINGS; dz <= MIN_DISTANCE_BETWEEN_SAPLINGS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos checkPos = pos.offset(dx, 0, dz);
                if (level.getBlockState(checkPos).getBlock() instanceof SaplingBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isSapling(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock;
    }

    private boolean isAir(Level level, BlockPos pos) {
        return level.getBlockState(pos).isAir();
    }

    private void clearGrassAndFlowers(Level level, BlockPos pos) {
        level.destroyBlock(pos, true);
    }
}
