package com.chipoodle.devilrpg.blockentity;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.block.BloomingSanctuaryBlock;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.init.ModEntityBlocks;
import com.chipoodle.devilrpg.util.IRenderUtilities;
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
    //private static final int EFFECT_INTERVAL = 80; // 80 ticks = 4 segundos
    private static final int REGENERATION_DURATION = 25; // 1.25 segundos (25 ticks)
    private static final int EFFECT_RADIUS = 5; // Radio de efecto
    Player owner;
    private Long timeOfCreation = null;
    //private int tickCounter = 0;
    private boolean directionChanged = false;
    // Propietario del bloque
    private UUID ownerUUID;

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
            world.destroyBlock(currentBlockPos, true);
            return;
        }

        int skillLevel = state.getValue(LEVEL);
        int currentDecay = state.getValue(DECAY_STAGE);
        boolean isTop = state.getValue(IS_TOP);
        Integer duration = skillLevel * TICK_FACTOR + 40; // Duración en función del nivel
        long timeElapsed = world.getGameTime() - timeOfCreation;
        int newDecayStage = (int) ((timeElapsed * 4) / duration); // 4 etapas de decadencia
        int radius = (int) (EFFECT_RADIUS + skillLevel * 0.25);
        int effectAmplifier = (int) (skillLevel * 0.11);

        // Actualiza el estado de decadencia
        if (newDecayStage != currentDecay && newDecayStage <= 3) {
            state = state.setValue(DECAY_STAGE, newDecayStage);
            world.setBlockAndUpdate(currentBlockPos, state);
        }

        // Cambiar dirección si no se ha hecho aún
        if (!directionChanged) {
            this.setChanged();
            state = setNewDirection(state, world, currentBlockPos);
            directionChanged = true;
        }

        if(!isTop) {
            applyRegenerationEffect(owner, radius, effectAmplifier);
            IRenderUtilities.renderEffectArea(world, currentBlockPos, radius, ParticleTypes.HAPPY_VILLAGER);
        }

        //Destruir el bloque si es top y no tiene base
        BlockPos below = currentBlockPos.below();
        if(isTop && !world.getBlockState(below).getBlock().equals(ModBlocks.BLOOMING_SANCTUARY_BLOCK.get())){
            world.destroyBlock(currentBlockPos, true);
        }


        // Destruir el bloque cuando se acaba el tiempo de vida
        if (timeOfCreation + duration < world.getGameTime() || ownerUUID == null) {
            world.destroyBlock(currentBlockPos, true);
        }
    }

    private BlockState setNewDirection(@NotNull BlockState state, @NotNull ServerLevel world, @NotNull BlockPos pos) {
        Direction currentDirection = state.getValue(DIRECTIONS);
        switch (currentDirection) {
            case UP -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.EAST);
            case DOWN -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.WEST);
            case EAST -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.UP);
            case WEST -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.DOWN);
            case NORTH -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.NORTH);
            case SOUTH -> state = state.setValue(BloomingSanctuaryBlock.BLOOMING_SANCTUARY_FACING, Direction.SOUTH);
        }
        world.setBlockAndUpdate(pos, state);
        return state;
    }

    private void applyRegenerationEffect(Player owner, int radius, int effectAmplifier) {
        //DevilRpg.LOGGER.info("============> Applying effects sanctuary. radius: {} effectAmplifier: {}" , radius,effectAmplifier);
        // Define el área de efecto
        //AABB effectArea = new AABB(pos).inflate(EFFECT_RADIUS);
        double k = this.getBlockPos().getX();
        double l = this.getBlockPos().getY();
        double i1 = this.getBlockPos().getZ();
        AABB effectArea = (new AABB(k, l, i1, (k + 1), (l + 1), (i1 + 1)))
                .inflate(radius).expandTowards(0.0D, Objects.requireNonNull(this.level).getHeight(), 0.0D);

        // Busca entidades aliadas al propietario
        List<LivingEntity> entities = TargetUtils.getAlliesListWithinAABBRangeIncludingOwner(effectArea, owner);
        // Aplica el efecto de regeneración a las entidades aliadas
        applyPrimaryEffect(MobEffects.REGENERATION, effectAmplifier, entities);

    }

    private void applyPrimaryEffect(MobEffect primaryEffect, int amplifierIn, List<LivingEntity> alliesList) {
        for (LivingEntity entity : alliesList) {
            MobEffectInstance pri = new MobEffectInstance(primaryEffect, REGENERATION_DURATION, amplifierIn, true, true);
            MobEffectInstance active = entity.getEffect(primaryEffect);
            if (active == null || pri.getAmplifier() > active.getAmplifier()) {
                entity.addEffect(pri);
            } else
                active.update(pri);
        }
    }


    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
}
