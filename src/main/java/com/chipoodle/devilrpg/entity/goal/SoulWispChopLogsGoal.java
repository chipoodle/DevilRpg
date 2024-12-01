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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SoulWispChopLogsGoal extends Goal {
    private static final double SPEED = 1.0;
    private static final double MAXIMUM_DISTANCE_TO_SQR = 2.6;
    private static final int TICKS_UNTIL_NEXT_HIT_LOG = 15;
    private static final int TICKS_UNTIL_NEXT_HIT_LEAVES = 3;
    private static final int TICKS_WITHOUT_CHOPPING = 30;
    private final SoulWispChopper soulWisp;
    private final int RADIUS = 5;
    private BlockPos targetBlockPos;
    private int ticksUntilNextHit;
    private int currentTicksWithoutChopping;


    public SoulWispChopLogsGoal(SoulWispChopper soulWisp) {
        this.soulWisp = soulWisp;
        resetTargetBlock();
    }

    @Override
    public boolean canUse() {
        BlockPos closestLogPos = getClosestLogOrLeafBlock();
        if (closestLogPos != null && soulWisp.hasItemInMainHand() && !soulWisp.hasItemInOffHand()) {
            this.targetBlockPos = closestLogPos;
            return true;
        }
        return false;
    }

    @Override
    public void start() {
        DevilRpg.LOGGER.info("======= start SoulWispChopLogsGoal");
        if (targetBlockPos != null) {
            this.soulWisp.getNavigation().moveTo(targetBlockPos.getX(), targetBlockPos.getY(), targetBlockPos.getZ(), SPEED);
        }
    }

    public void stop() {
        DevilRpg.LOGGER.info("======= stop SoulWispChopLogsGoal");
        resetTargetBlock();
    }

    @Override
    public void tick() {
        //DevilRpg.LOGGER.info("======= tick targetBlockPos != null {} ", targetBlockPos != null);
        if (targetBlockPos != null) {
            BlockState targetBlockState = this.soulWisp.level.getBlockState(targetBlockPos);
            //if (targetBlockState.is(BlockTags.LOGS) || targetBlockState.is(BlockTags.LEAVES)) {
            double distanceToSqr = this.soulWisp.distanceToSqr(targetBlockPos.getX(), targetBlockPos.getY(), targetBlockPos.getZ());
            if (distanceToSqr <= MAXIMUM_DISTANCE_TO_SQR) {
                this.soulWisp.setChopping(true);
                chopLog(targetBlockState, targetBlockPos);
            } else {
                boolean moveSuccessful = this.soulWisp.getNavigation().moveTo(targetBlockPos.getX(), targetBlockPos.getY(), targetBlockPos.getZ(), SPEED);
                this.soulWisp.setChopping(false);
                currentTicksWithoutChopping++;
                if (!moveSuccessful) {
                    DevilRpg.LOGGER.info("======= move not successful");
                    setRandomPosition();
                }
                if (currentTicksWithoutChopping > TICKS_WITHOUT_CHOPPING) {
                    DevilRpg.LOGGER.info("======= time passed to chop");
                    //setRandomPosition();
                    ArrayList<TagKey<Block>> tagKeys = new ArrayList<>();
                    tagKeys.add(BlockTags.LEAVES);
                    getBlockPos(targetBlockPos, tagKeys);
                    chopLog(targetBlockState, targetBlockPos);
                    //resetTargetBlock();

                }
            }

        }
    }

    private void setRandomPosition() {
        Vec3 randomPos = DefaultRandomPos.getPosTowards(this.soulWisp, RADIUS, 3, Vec3.atBottomCenterOf(this.soulWisp.blockPosition()), SPEED);
        if (randomPos != null) {
            PathNavigation navigation = this.soulWisp.getNavigation();
            WalkNodeEvaluator nodeEvaluator = (WalkNodeEvaluator) navigation.getNodeEvaluator();
            nodeEvaluator.setCanPassDoors(true);
            navigation.moveTo(randomPos.x, randomPos.y, randomPos.z, SPEED);
            //currentTicksWithoutChopping = 0;
        }
    }

    private BlockPos getClosestLogOrLeafBlock() {
        BlockPos soulWispBlockPos = this.soulWisp.blockPosition();
        ArrayList<TagKey<Block>> tagKeys = new ArrayList<>();
        tagKeys.add(BlockTags.LOGS);
        tagKeys.add(BlockTags.LEAVES);

        /*BlockPos closestBlockPos = getBlockPos(soulWispBlockPos, BlockTags.LOGS);
        if (closestBlockPos == null || currentTicksWithoutChopping > TICKS_WITHOUT_CHOPPING) {
            closestBlockPos = getBlockPos(soulWispBlockPos, BlockTags.LEAVES);
        }*/
        return getBlockPos(soulWispBlockPos, new ArrayList<>(tagKeys));
    }

    private void chopLog(BlockState blockState, BlockPos blockPos) {
        this.soulWisp.level.destroyBlockProgress(soulWisp.getId(), blockPos, (-1 * (ticksUntilNextHit % -10) + 1));
        if (this.ticksUntilNextHit <= 0) {
            DevilRpg.LOGGER.info("======= tryChopLogWithAxe {} ", ticksUntilNextHit);
            ItemStack mainHandItem = soulWisp.getMainHandItem();
            this.ticksUntilNextHit = blockState.is(BlockTags.LOGS) ? TICKS_UNTIL_NEXT_HIT_LOG : TICKS_UNTIL_NEXT_HIT_LEAVES;
            this.hurtAndBreak(1, this.soulWisp, (entity) -> entity.broadcastBreakEvent(EquipmentSlot.MAINHAND), mainHandItem, (Player) soulWisp.getOwner());
            if (this.soulWisp.level.destroyBlock(targetBlockPos, true, soulWisp))
                resetTargetBlock();
            //this.currentTicksWithoutChopping = 0;
        } else {
            this.ticksUntilNextHit--;
        }
    }

    private void resetTargetBlock() {
        this.targetBlockPos = null;
        this.ticksUntilNextHit = TICKS_UNTIL_NEXT_HIT_LOG;
        this.currentTicksWithoutChopping = 0;
        this.soulWisp.setChopping(false);
    }

    private BlockPos getBlockPos(BlockPos blockPos, List<TagKey<Block>> blockTags) {
        double closestLogDistanceSq = Double.MAX_VALUE;
        BlockPos closestLogPos = null;
        double closestOtherBlockDistanceSq = Double.MAX_VALUE;
        BlockPos closestOtherBlockPos = null;

        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int y = -RADIUS; y <= RADIUS; y++) {
                for (int z = -RADIUS; z <= RADIUS; z++) {
                    BlockPos pos = blockPos.offset(x, y, z);
                    BlockState blockState = this.soulWisp.level.getBlockState(pos);

                    // Verificar si el bloque pertenece a una de las etiquetas
                    for (TagKey<Block> tag : blockTags) {
                        if (blockState.is(tag)) {
                            double distanceSq = blockPos.distSqr(pos);

                            // Priorizar los LOGS sobre otros bloques
                            if (tag == BlockTags.LOGS) {
                                if (distanceSq < closestLogDistanceSq) {
                                    closestLogDistanceSq = distanceSq;
                                    closestLogPos = pos;
                                }
                            } else {
                                if (distanceSq < closestOtherBlockDistanceSq) {
                                    closestOtherBlockDistanceSq = distanceSq;
                                    closestOtherBlockPos = pos;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Verificar si el bloque más cercano de otro tipo se interpone en el camino hacia el Log
        if (closestLogPos != null && closestOtherBlockPos != null) {
            if (isBlockBetween(blockPos, closestLogPos, closestOtherBlockPos)) {
                return closestOtherBlockPos; // Devuelve el bloque que interfiere en el camino hacia el Log
            } else {
                return closestLogPos; // No hay interferencia; devuelve el Log
            }
        }

        // Si no hay Logs, devuelve el bloque más cercano de otro tipo, si existe
        return closestLogPos != null ? closestLogPos : closestOtherBlockPos;
    }

    // Método que comprueba si un bloque está en el camino entre dos posiciones
    private boolean isBlockBetween(BlockPos startPos, BlockPos logPos, BlockPos otherBlockPos) {
        int dx = logPos.getX() - startPos.getX();
        int dy = logPos.getY() - startPos.getY();
        int dz = logPos.getZ() - startPos.getZ();

        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        double stepX = dx / (double) steps;
        double stepY = dy / (double) steps;
        double stepZ = dz / (double) steps;

        double currentX = startPos.getX();
        double currentY = startPos.getY();
        double currentZ = startPos.getZ();

        for (int i = 0; i < steps; i++) {
            currentX += stepX;
            currentY += stepY;
            currentZ += stepZ;

            BlockPos currentPos = new BlockPos(
                    (int) Math.round(currentX),
                    (int) Math.round(currentY),
                    (int) Math.round(currentZ)
            );

            // Si alcanzamos la posición del Log, salimos
            if (currentPos.equals(logPos)) {
                break;
            }

            // Si llegamos al bloque interferente antes del Log, significa que está en el camino
            if (currentPos.equals(otherBlockPos)) {
                return true;
            }
        }
        return false;  // No hay bloque que interfiera en el camino
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
