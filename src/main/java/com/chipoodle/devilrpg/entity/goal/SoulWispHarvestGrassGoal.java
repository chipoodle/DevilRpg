package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWispForester;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

public class SoulWispHarvestGrassGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final double MAXIMUM_DISTANCE_FOR_DELIVERY = 2.5;
    private static final int INVENTORY_FULL_COOLDOWN = 100;
    private static final int SEARCH_GRASS_RADIUS = 10; // Radio de búsqueda de pasto y semillas
    private static final int PICKUP_SEED_RADIUS = 2;

    private static final Random RANDOM = new Random();

    private final SoulWispForester soulWisp;
    private boolean isInventoryFull = false;
    private int inventoryFullTicks = 0;
    private BlockPos targetGrassPos = null; // Posición del pasto que está recolectando

    public SoulWispHarvestGrassGoal(SoulWispForester soulWisp) {
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

        // 🔴 Verificar si tiene un sapling en la mano principal o secundaria
        ItemStack mainHandItem = soulWisp.getMainHandItem();
        ItemStack offHandItem = soulWisp.getOffhandItem();

        if (isSapling(mainHandItem) || isSapling(offHandItem) || findNearbySapling().isPresent()) {
            return false; // 🔴 No ejecutar si tiene un sapling
        }

        return findNearbyGrassBlock() != null;
    }

    @Override
    public void start() {
        DevilRpg.LOGGER.info("======= start SoulWispHarvestGrassGoal");
    }

    @Override
    public void stop() {
        DevilRpg.LOGGER.info("======= stop SoulWispHarvestGrassGoal");
        targetGrassPos = null;
    }

    @Override
    public void tick() {
        if (hasSaplingInHand()) {
            //plantSaplingFirst();
        } else if (!this.soulWisp.hasItemInOffHand()) {
            harvestNearbyGrass();
        } else {
            tryToGiveSeedsToPlayer();
        }
    }

    /**
     * Verifica si el SoulWisp tiene un sapling en cualquier mano
     */
    private boolean hasSaplingInHand() {
        ItemStack mainHandItem = this.soulWisp.getMainHandItem();
        ItemStack offHandItem = this.soulWisp.getOffhandItem();
        return isSapling(mainHandItem) || isSapling(offHandItem);
    }

    /**
     * Intenta plantar el sapling antes de hacer otra tarea
     */
    private void plantSaplingFirst() {
        BlockPos suitablePos = findSuitableSoil();
        if (suitablePos != null) {
            Level level = soulWisp.level;
            level.setBlock(suitablePos, Blocks.OAK_SAPLING.defaultBlockState(), 3);

            // Remueve el sapling de su inventario
            if (isSapling(soulWisp.getMainHandItem())) {
                soulWisp.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else {
                soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
        }
    }

    /**
     * Busca un bloque de pasto en el radio
     */
    private BlockPos findNearbyGrassBlock() {
        Level level = soulWisp.level;
        LivingEntity owner = soulWisp.getOwner();
        if (owner == null)
            return null;

        BlockPos soulWispPos = Objects.requireNonNull(owner).blockPosition();

        for (int x = -SEARCH_GRASS_RADIUS; x <= SEARCH_GRASS_RADIUS; x++) {
            for (int z = -SEARCH_GRASS_RADIUS; z <= SEARCH_GRASS_RADIUS; z++) {
                for (int y = -1; y <= 2; y++) { // Busca a diferentes alturas
                    BlockPos pos = soulWispPos.offset(x, y, z);
                    BlockState blockState = level.getBlockState(pos);
                    Block block = blockState.getBlock();

                    if (block == Blocks.GRASS || block == Blocks.TALL_GRASS) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private Optional<ItemEntity> findNearbySapling() {

        if (soulWisp.getOwner() == null)
            return Optional.empty();

        return soulWisp.level.getEntitiesOfClass(ItemEntity.class, soulWisp.getOwner().getBoundingBox().inflate(SEARCH_GRASS_RADIUS),
                        item -> isSapling(item.getItem()))
                .stream()
                .min(Comparator.comparingDouble(item -> item.distanceTo(soulWisp)));
    }

    /**
     * Corta el pasto y recoge semillas si aparecen
     */
    private void harvestNearbyGrass() {
        if (targetGrassPos == null) {
            targetGrassPos = findNearbyGrassBlock();
        }

        if (targetGrassPos != null) {
            Level level = soulWisp.level;
            BlockState blockState = level.getBlockState(targetGrassPos);
            Block block = blockState.getBlock();

            // Moverse al bloque antes de romperlo
            if (soulWisp.blockPosition().closerThan(targetGrassPos, 1.5)) {
                level.destroyBlock(targetGrassPos, true); // Elimina el bloque soltando  drops
                targetGrassPos = null;

                lookForNearestSeedItemAndTakeItOffHand();



                /*// Probabilidad de obtener semillas
                if (RANDOM.nextFloat() < 0.5) { // 50% de probabilidad
                    ItemStack seedStack = new ItemStack(Items.WHEAT_SEEDS, 1);
                    this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, seedStack);
                }*/
            } else {
                soulWisp.getNavigation().moveTo(targetGrassPos.getX(), targetGrassPos.getY(), targetGrassPos.getZ(), SPEED);
            }
        }
    }

    private void lookForNearestSeedItemAndTakeItOffHand() {
        List<ItemEntity> items = getNearbySeedItems();
        if (!items.isEmpty()) {
            ItemEntity itemEntity = items.get(0);
            ItemStack seedStack = itemEntity.getItem();
            this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, seedStack);
            itemEntity.discard();
        }
    }

    private List<ItemEntity> getNearbySeedItems() {
        return soulWisp.level.getEntitiesOfClass(ItemEntity.class, soulWisp.getBoundingBox().inflate(PICKUP_SEED_RADIUS),
                item -> {
                    ItemStack stack = item.getItem();
                    return stack.is(Items.WHEAT_SEEDS); // Si es ua semilla
                }
        );
    }


    /**
     * Busca un suelo adecuado para plantar un sapling
     */
    private BlockPos findSuitableSoil() {
        Level level = soulWisp.level;
        BlockPos soulWispPos = soulWisp.blockPosition();

        for (int x = -SEARCH_GRASS_RADIUS; x <= SEARCH_GRASS_RADIUS; x++) {
            for (int z = -SEARCH_GRASS_RADIUS; z <= SEARCH_GRASS_RADIUS; z++) {
                BlockPos pos = soulWispPos.offset(x, 0, z);
                BlockState blockState = level.getBlockState(pos.below());

                if (blockState.is(Blocks.GRASS_BLOCK) || blockState.is(Blocks.DIRT)) {
                    if (level.getBlockState(pos).isAir() || level.getBlockState(pos).is(Blocks.GRASS_BLOCK) || level.getBlockState(pos).is(Blocks.TALL_GRASS)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Entrega las semillas al jugador
     */
    private void tryToGiveSeedsToPlayer() {
        Player owner = (Player) this.soulWisp.getOwner();
        if (owner == null) {
            return;
        }

        ItemStack offHandItem = this.soulWisp.getOffhandItem();
        if (offHandItem.isEmpty()) {
            return;
        }

        if (this.soulWisp.distanceTo(owner) <= MAXIMUM_DISTANCE_FOR_DELIVERY) {
            boolean addedSuccessfully = owner.addItem(offHandItem);
            if (addedSuccessfully) {
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            } else {
                this.soulWisp.spawnAtLocation(offHandItem);
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                this.isInventoryFull = true;
                this.inventoryFullTicks = 0;
            }
        } else {
            this.soulWisp.getNavigation().moveTo(owner, SPEED);
        }
    }

    /**
     * Verifica si un item es un sapling
     */
    private boolean isSapling(ItemStack itemStack) {
        return itemStack.is(Items.OAK_SAPLING) ||
                itemStack.is(Items.BIRCH_SAPLING) ||
                itemStack.is(Items.SPRUCE_SAPLING) ||
                itemStack.is(Items.JUNGLE_SAPLING) ||
                itemStack.is(Items.ACACIA_SAPLING) ||
                itemStack.is(Items.DARK_OAK_SAPLING);
    }
}
