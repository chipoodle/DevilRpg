package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.block.BloomingSanctuaryBlock;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import com.chipoodle.devilrpg.util.IRenderUtilities;
import com.chipoodle.devilrpg.util.SkillEnum;
import com.chipoodle.devilrpg.util.TargetUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.chipoodle.devilrpg.block.BloomingSanctuaryBlock.*;

public class BloomingSanctuaryBlockEntity extends BlockEntity {

    public static final int TICK_FACTOR = 20; // 20 ticks = 1 segundo
    public static final int MANA_TO_ADD = -1;
    private static final int REGENERATION_DURATION = 25; // 1.25 segundos (25 ticks)
    private static final int EFFECT_RADIUS = 5; // Radio de efecto
    private static final double BONE_MEAL_CHANCE = 0.50; // 50% de probabilidad por tick


    private Player owner;
    private Long timeOfCreation = null;
    private boolean directionChanged = false;
    private UUID ownerUUID;
    private PlayerManaCapabilityInterface manaCapability;
    private AABB effectArea;
    private PlayerSkillCapabilityInterface skillCap;
    private int florasVigorPoints = 0;

    public BloomingSanctuaryBlockEntity(BlockPos pos, BlockState state) {
        super(ModEntityBlocks.BLOOMING_SANCTUARY_ENTITY_BLOCK.get(), pos, state);
    }

    public void tick(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos currentBlockPos, @NotNull RandomSource randomSource) {
        if (timeOfCreation == null) {
            timeOfCreation = world.getGameTime();
        }

        if (owner == null && ownerUUID != null) {
            owner = world.getPlayerByUUID(ownerUUID);
        }

        if (owner == null) {
            destroyBlock(world, currentBlockPos);
            return;
        }

        if (manaCapability == null) {
            manaCapability = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerManaCapability.INSTANCE);
        }

        if (skillCap == null) {
            skillCap = IGenericCapability.getUnwrappedPlayerCapability(owner, PlayerSkillCapability.INSTANCE);
            florasVigorPoints = skillCap.getSkillsPoints().get(SkillEnum.FLORAS_VIGOR);
        }

        manaCapability.addMana(MANA_TO_ADD, owner); // Gasto de mana por tick

        if (manaCapability.getMana() == 0) {
            destroyBlock(world, currentBlockPos);
            return;
        }

        int skillLevel = state.getValue(LEVEL);
        int currentDecay = state.getValue(DECAY_STAGE);
        boolean isTop = state.getValue(IS_TOP);
        int duration = skillLevel * TICK_FACTOR + 40;
        long currentTime = world.getGameTime();
        int newDecayStage = (int) ((currentTime - timeOfCreation) * 4 / duration);
        int radius = (int) (EFFECT_RADIUS + skillLevel * 0.25);
        int effectAmplifier = (int) (skillLevel * 0.11);

        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        if (!directionChanged) {
            state = setNewDirection(state, world, currentBlockPos);
            directionChanged = true;
        }

        if (!isTop) {
            applyRegenerationEffect(owner, radius, effectAmplifier);
            if (florasVigorPoints != 0)
                applyBoneMealEffect(world, currentBlockPos, radius, randomSource, florasVigorPoints);
            IRenderUtilities.renderEffectArea(world, currentBlockPos, radius, ParticleTypes.HAPPY_VILLAGER);
        }

        BlockPos below = currentBlockPos.below();
        if (isTop && !world.getBlockState(below).getBlock().equals(ModBlocks.BLOOMING_SANCTUARY_BLOCK.get())) {
            destroyBlock(world, currentBlockPos);
        }

        if (timeOfCreation + duration < currentTime || ownerUUID == null) {
            destroyBlock(world, currentBlockPos);
        }
    }

    /**
     * Aplica bonemeal aleatoriamente a bloques dentro del radio si pueden crecer o generar vegetación.
     */
    private void applyBoneMealEffect(@NotNull ServerLevel world, @NotNull BlockPos centerPos, int radius, @NotNull RandomSource random, int florasVigorPoints) {
        if (random.nextDouble() < BONE_MEAL_CHANCE + (florasVigorPoints * 0.166666667D)) {
            BlockPos targetPos;
            do {
                targetPos = centerPos.offset(random.nextInt(radius * 2) - radius, -1, random.nextInt(radius * 2) - radius);
            }
            while (centerPos.equals(targetPos));

            BlockState targetState = world.getBlockState(targetPos);

            // Verificar si el bloque es fertilizable con bonemeal (cultivos, plantas y pasto)
            if (targetState.getBlock() instanceof BonemealableBlock bonemealableBlock) {
                if (bonemealableBlock.isValidBonemealTarget(world, targetPos, targetState, false)) {
                    ItemStack bonemealStack = new ItemStack(Items.BONE_MEAL); // Crear un stack de bonemeal
                    BoneMealItem.applyBonemeal(bonemealStack, world, targetPos, owner);
                    world.levelEvent(2005, targetPos, 0); // Efecto de partículas de bonemeal
                    manaCapability.addMana(MANA_TO_ADD, owner); // Gasto de mana por aplicación de bonemeal
                }
            }
        }
    }

    private BlockState setNewDirection(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos pos) {
        Direction currentDirection = state.getValue(DIRECTIONS);
        Direction newDirection = switch (currentDirection) {
            case UP -> Direction.EAST;
            case DOWN -> Direction.WEST;
            case EAST -> Direction.UP;
            case WEST -> Direction.DOWN;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
        };
        state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, newDirection);
        world.setBlockAndUpdate(pos, state);
        return state;
    }

    private void applyRegenerationEffect(Player owner, int radius, int effectAmplifier) {
        if (effectArea == null) {
            double posX = this.getBlockPos().getX();
            double posY = this.getBlockPos().getY();
            double posZ = this.getBlockPos().getZ();
            effectArea = new AABB(posX, posY, posZ, posX + 1, posY + 1, posZ + 1)
                    .inflate(radius).expandTowards(0.0D, Objects.requireNonNull(this.level).getHeight(), 0.0D);
        }

        List<LivingEntity> entities = TargetUtils.getAlliesListWithinAABBRangeIncludingOwner(effectArea, owner);
        applyEffectToEntities(MobEffects.REGENERATION, effectAmplifier, entities);
    }

    private void applyEffectToEntities(MobEffect effect, int amplifier, List<LivingEntity> entities) {
        for (LivingEntity entity : entities) {
            MobEffectInstance effectInstance = new MobEffectInstance(effect, REGENERATION_DURATION, amplifier, true, true);
            MobEffectInstance activeEffect = entity.getEffect(effect);
            if (activeEffect == null || effectInstance.getAmplifier() > activeEffect.getAmplifier()) {
                entity.addEffect(effectInstance);
            } else {
                activeEffect.update(effectInstance);
            }
        }
    }

    private void destroyBlock(ServerLevel world, BlockPos pos) {
        world.destroyBlock(pos, true); // Destruye el bloque y suelta los ítems
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
}
