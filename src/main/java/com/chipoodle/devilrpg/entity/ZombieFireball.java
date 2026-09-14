package com.chipoodle.devilrpg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Bola de fuego del <b>zombi agresivo</b>: igual que la del Blaze pero <b>sin incendiar el terreno</b>.
 * <p>
 * El problema era que el goal lanzaba un {@code SmallFireball} de vanilla, y {@code SmallFireball.onHitBlock}
 * hace {@code setBlockAndUpdate(pos, FIRE)}: cada disparo que daba en el suelo dejaba un foco ardiendo (y con la
 * aldea de madera, un incendio). En partida se veía como "fuego que aparece al azar por el suelo".
 * <p>
 * Se sigue usando la clase del juego (heredamos de ella, así que el cliente lo dibuja con el renderer de
 * vanilla y no hay que registrar nada): lo único que cambia es que, después del impacto, <b>se apaga el fuego</b>
 * que el proyectil acaba de encender. El daño a las entidades no se toca: al que le da, le sigue prendiendo.
 */
public class ZombieFireball extends SmallFireball {

    public ZombieFireball(Level level, LivingEntity owner, Vec3 movement) {
        super(level, owner, movement);
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide) {
            return;
        }
        // El bloque donde el proyectil habría dejado el fuego (la cara por la que ha entrado).
        BlockPos pos = result.getBlockPos().relative(result.getDirection());
        BlockState state = level().getBlockState(pos);
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            level().removeBlock(pos, false);
        }
    }
}
