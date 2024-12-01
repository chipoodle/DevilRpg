package com.chipoodle.devilrpg.entity.goal;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.entity.SoulWispChopper;
import net.minecraft.core.BlockPos;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SoulWispChopWoodGoal extends Goal {
    // Parámetros de configuración para la IA del SoulWisp
    public static final double SPEED = 1.0;                       // Velocidad de movimiento
    public static final int TICKS_UNTIL_NEXT_HIT_LOG = 15;        // Ticks de espera entre golpes al talar troncos
    public static final int TICKS_UNTIL_NEXT_HIT_LEAVES = 4;      // Ticks de espera entre golpes al talar hojas
    public static final int TICKS_WITHOUT_CHOPPING = 40;          // Ticks sin cortar antes de intentar una nueva posición
    public static final double MAXIMUM_DISTANCE_TO_SQR = 2.8;     // Distancia máxima para iniciar el corte
    public static final double MAXIMUM_DISTANCE_FOR_DELIVERY = 2.5;
    private static final int INVENTORY_FULL_COOLDOWN = 100;       // Tiempo de espera en ticks antes de intentar otra vez

    // Referencias y variables de estado para el comportamiento del SoulWisp
    private final SoulWispChopper soulWisp;                       // Entidad SoulWisp que ejecuta este objetivo
    private final int radius;                                     // Radio de búsqueda para bloques e items
    private BlockPos targetBlockPos;                              // Posición del bloque objetivo
    private int ticksUntilNextHit;                                // Contador de ticks para el siguiente golpe
    private int currentTicksWithoutChopping;                             // Contador de ticks sin cortar
    private boolean hasLogItemsNear = false;                      // Bandera para saber si hay items de tronco cerca
    private boolean isInventoryFull = false;                      // Bandera que indica si el inventario del jugador estaba lleno
    private int inventoryFullTicks = 0;                           // Contador de tiempo de espera para inventario lleno

    public SoulWispChopWoodGoal(SoulWispChopper soulWisp) {
        this.soulWisp = soulWisp;
        this.ticksUntilNextHit = TICKS_UNTIL_NEXT_HIT_LOG;
        this.currentTicksWithoutChopping = 0;
        this.radius = 5; // Configura el radio de búsqueda en 5 bloques
    }

    @Override
    public boolean canUse() {
        BlockPos soulWispBlockPos = this.soulWisp.blockPosition();

        // Ignora items en el suelo si el inventario del jugador estaba lleno recientemente
        if (isInventoryFull) {
            inventoryFullTicks++;
            if (inventoryFullTicks > INVENTORY_FULL_COOLDOWN) {
                isInventoryFull = false; // Restablece la bandera después del tiempo de espera
                inventoryFullTicks = 0;
            }
            return false;
        }

        // Revisa si hay items de tronco caídos en el suelo cercanos
        List<ItemEntity> items = getNearbyLogItems();
        if (!items.isEmpty()) {
            this.hasLogItemsNear = true;
            return true;
        }

        // Si no hay items de tronco en el suelo, busca un árbol para talar
        BlockPos closestBlockPos = getBlockPos(soulWispBlockPos, BlockTags.LOGS);
        if (closestBlockPos == null || currentTicksWithoutChopping > TICKS_WITHOUT_CHOPPING) {
            closestBlockPos = getBlockPos(soulWispBlockPos, BlockTags.LEAVES);
        }

        // Si encuentra un bloque objetivo y tiene un item en la mano principal pero no en la secundaria, puede usar el objetivo
        if (closestBlockPos != null && soulWisp.hasItemInMainHand()) {
            this.targetBlockPos = closestBlockPos;
            return true;
        }

        DevilRpg.LOGGER.info("====== this.soulWisp.hasItemInOffHand(); {}",this.soulWisp.hasItemInOffHand());
        return this.soulWisp.hasItemInOffHand(); // Si tiene un item en la mano secundaria, intenta entregar el item al jugador
    }

    @Override
    public boolean canContinueToUse() {
        return super.canContinueToUse();
    }

    // Busca la posición del bloque más cercano de un tipo específico (ej. troncos, hojas) en un radio definido
    private BlockPos getBlockPos(BlockPos blockPos, TagKey<Block> blockTag) {
        double closestDistanceSq = Double.MAX_VALUE;
        BlockPos closestBlockPos = null;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = blockPos.offset(x, y, z);
                    BlockState blockState = this.soulWisp.level.getBlockState(pos);
                    if (blockState.is(blockTag)) {
                        double distanceSq = this.soulWisp.distanceToSqr(pos.getX(), pos.getY(), pos.getZ());
                        if (distanceSq < closestDistanceSq) {
                            closestDistanceSq = distanceSq;
                            closestBlockPos = pos;
                        }
                    }
                }
            }
        }
        return closestBlockPos;
    }

    @Override
    public void start() {
        // Define el objetivo cuando encuentra items en el suelo o un bloque para talar
        if (hasLogItemsNear) {
            this.soulWisp.getNavigation().moveTo(Objects.requireNonNull(this.soulWisp.getOwner()), SPEED);
        } else if (targetBlockPos != null) {
            this.soulWisp.getNavigation().moveTo(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ(), SPEED);
        }
    }

    @Override
    public void stop() {
        this.targetBlockPos = null;
        this.ticksUntilNextHit = TICKS_UNTIL_NEXT_HIT_LOG;
        this.currentTicksWithoutChopping = 0;
        this.hasLogItemsNear = false;
        this.isInventoryFull = false;
        this.inventoryFullTicks = 0;
    }

    @Override
    public void tick() {
        // Si hay log item cercano en el suelo
        if (hasLogItemsNear) {
            this.soulWisp.setChopping(false);
            // Si no tiene un log en la mano secundaria (offhand)
            if (!this.soulWisp.hasItemInOffHand()) {
                lookForNearestLogItemAndTakeItOffHand();
            } else {
                tryToGiveLogItemToPlayerInventoryNotFull();
            }
        }
        // Si no hay log item cercano en el suelo
        else
            //Si existe un Block objetivo
            if (this.targetBlockPos != null) {
                BlockState targetBlockState = this.soulWisp.level.getBlockState(this.targetBlockPos);

                //Si el Block objetivo es un log o leaf
                if (targetBlockState.is(BlockTags.LOGS) || targetBlockState.is(BlockTags.LEAVES)) {
                    double distanceToSqr = this.soulWisp.distanceToSqr(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ());

                    // Si el block está cercano set chopping = true e intenta cortar el log
                    if (distanceToSqr <= MAXIMUM_DISTANCE_TO_SQR) {
                        this.soulWisp.setChopping(true);
                        tryChopLogWithAxe(targetBlockState, this.targetBlockPos);
                    }
                    // Si el block no está cercano
                    else {
                        this.soulWisp.setChopping(false);
                        currentTicksWithoutChopping++;
                        if (currentTicksWithoutChopping > TICKS_WITHOUT_CHOPPING) {
                            setRandomPosition();
                            DevilRpg.LOGGER.info("======= set Random position ");
                        } else {
                            DevilRpg.LOGGER.info("======= Move to {} {} {} ", this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ());
                            this.soulWisp.getNavigation().moveTo(this.targetBlockPos.getX(), this.targetBlockPos.getY(), this.targetBlockPos.getZ(), SPEED);
                        }
                    }
                }
                else{
                    DevilRpg.LOGGER.info("======= No es un log o leaf {} ", ticksUntilNextHit);
                }
            }
            else{
                DevilRpg.LOGGER.info("======= No existe objetivo {} ", ticksUntilNextHit);
            }
    }

    private void tryToGiveLogItemToPlayerInventoryNotFull() {
        // Intento de entrega al jugador
        Player owner = (Player) this.soulWisp.getOwner();
        if (owner != null && this.soulWisp.distanceTo(owner) <= MAXIMUM_DISTANCE_FOR_DELIVERY) {
            boolean addedSuccessfully = owner.addItem(this.soulWisp.getOffhandItem());
            if (addedSuccessfully) {
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            } else {
                // Inventario lleno, suelta el item y activa la bandera
                this.soulWisp.spawnAtLocation(this.soulWisp.getOffhandItem());
                this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                this.isInventoryFull = true; // Activa la bandera para ignorar logs temporalmente
                this.inventoryFullTicks = 0; // Reinicia el contador de tiempo de espera
            }
            this.hasLogItemsNear = false;
        } else if (owner != null) {
            this.soulWisp.getNavigation().moveTo(owner, SPEED);
        }
    }

    private void lookForNearestLogItemAndTakeItOffHand() {
        List<ItemEntity> items = getNearbyLogItems();
        if (!items.isEmpty()) {
            ItemEntity itemEntity = items.get(0);
            ItemStack logStack = itemEntity.getItem();
            this.soulWisp.setItemSlot(EquipmentSlot.OFFHAND, logStack);
            itemEntity.discard(); // Remueve el item del mundo
        }
    }

    private void tryChopLogWithAxe(BlockState blockState, BlockPos blockPos) {
        this.soulWisp.level.destroyBlockProgress(soulWisp.getId(), blockPos, (-1 * (ticksUntilNextHit % -10) + 1));

        if (this.ticksUntilNextHit <= 0) {
            DevilRpg.LOGGER.info("======= tryChopLogWithAxe {} ", ticksUntilNextHit);
            ItemStack mainHandItem = soulWisp.getMainHandItem();
            //if (mainHandItem.getItem() instanceof AxeItem) {
            this.ticksUntilNextHit = blockState.is(BlockTags.LOGS) ? TICKS_UNTIL_NEXT_HIT_LOG : TICKS_UNTIL_NEXT_HIT_LEAVES;
            this.hurtAndBreak(1, this.soulWisp, (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND), mainHandItem, (Player) soulWisp.getOwner());
            this.soulWisp.level.destroyBlock(targetBlockPos, true, soulWisp);
            this.currentTicksWithoutChopping = 0;
            // }
        } else {
            this.ticksUntilNextHit--;
        }
    }

    private void setRandomPosition() {
        Vec3 randomPos = DefaultRandomPos.getPosTowards(this.soulWisp, radius, 3, Vec3.atBottomCenterOf(this.soulWisp.blockPosition()), SPEED);
        if (randomPos != null) {
            PathNavigation navigation = this.soulWisp.getNavigation();
            WalkNodeEvaluator nodeEvaluator = (WalkNodeEvaluator) navigation.getNodeEvaluator();
            nodeEvaluator.setCanPassDoors(true);
            navigation.moveTo(randomPos.x, randomPos.y, randomPos.z, SPEED);
            currentTicksWithoutChopping = 0;
        }
    }

    private List<ItemEntity> getNearbyLogItems() {
        return soulWisp.level.getEntitiesOfClass(ItemEntity.class, soulWisp.getBoundingBox().inflate(radius),
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
            }
        }
    }
}
