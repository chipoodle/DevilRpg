package com.chipoodle.devilrpg.skillsystem.skillinstance;

import com.chipoodle.devilrpg.block.SoulVineBlock;
import com.chipoodle.devilrpg.blockentity.SoulVineBlockEntity;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapability;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityImplementation;
import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import com.chipoodle.devilrpg.init.ModBlocks;
import com.chipoodle.devilrpg.skillsystem.AbstractSkillSeedsInInventoryExecutor;
import com.chipoodle.devilrpg.util.SkillEnum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Random;

public class SkillSoulVine extends AbstractSkillSeedsInInventoryExecutor {

    public SkillSoulVine(PlayerSkillCapabilityImplementation parentCapability) {
        super(parentCapability);
    }

    @Override
    public SkillEnum getSkillEnum() {
        return SkillEnum.SOULVINE;
    }

    @Override
    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {
        if (player.getCooldowns().isOnCooldown(icon.getItem())) {
            return false;
        }
        // Antes se exigía una PARED al lado (la vid se agarraba a ella), y por eso no se podía lanzar al vacío.
        // Para que sirva de puente solo se pide que el sitio de delante esté libre: la vid ya crece recta desde
        // el primer bloque y se agarra sola cuando tope con algo.
        BlockPos start = player.blockPosition().relative(lookDirection(player));
        return puedeEmpezarEn(player.level(), start)
                && super.arePreconditionsMetBeforeConsumingResource(player);
    }

    /**
     * Dirección en la que crece la vid: la mirada del jugador <b>en 3D</b> (así puedes lanzarla hacia abajo para
     * bajar por una barranca o hacia arriba para subir), con <b>respaldo horizontal</b> si apuntar en vertical no
     * deja sitio para el primer bloque (por ejemplo, mirando al suelo que pisas).
     */
    private static Direction lookDirection(Player player) {
        Vec3 look = player.getLookAngle();
        Direction completa = Direction.getNearest(look.x, look.y, look.z);
        if (puedeEmpezarEn(player.level(), player.blockPosition().relative(completa))) {
            return completa;
        }
        return Direction.getNearest(look.x, 0.0D, look.z);
    }

    private static boolean puedeEmpezarEn(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || state.canBeReplaced();
    }

    @Override
    public void execute(Level levelIn, Player player, HashMap<String, String> parameters) {
        if (!player.getCooldowns().isOnCooldown(icon.getItem())) {
            if (!levelIn.isClientSide) {
                Random rand = new Random();
                levelIn.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.5F,
                        0.4F / (rand.nextFloat() * 0.4F + 0.8F));

                /*PlayerSkillCapabilityInterface skillCap = IGenericCapability.getUnwrappedPlayerCapability(player,
                        PlayerSkillCapability.INSTANCE);*/
                setVine(levelIn, player, parentCapability);
                player.getCooldowns().addCooldown(icon.getItem(), 20);
            }
        }
    }

    private void setVine(Level level, Player playerIn, PlayerSkillCapabilityInterface skillCap) {
        BlockPos playerBlockPos = playerIn.blockPosition();
        SoulVineBlock createdBlock = ModBlocks.SOUL_VINE_BLOCK.get();
        // La vid nace apuntando a donde miras: crece siguiendo la trayectoria exacta de la mirada.
        Direction nearestDirection = lookDirection(playerIn);
        BlockPos newBlockpos = playerBlockPos.relative(nearestDirection);
        {

            // Consumir una semilla del inventario
            consumeSeed(playerIn);

            int skillPoints = skillCap.getSkillsPoints(SkillEnum.SOULVINE);
            level
                    .setBlockAndUpdate(
                            newBlockpos,
                            createdBlock.defaultBlockState()
                                    .setValue(SoulVineBlock.AGE, 1)
                                    .setValue(SoulVineBlock.DIRECTIONS, nearestDirection)
                                    .setValue(SoulVineBlock.HAS_CHILDREN, false)
                    );

            // El nivel de la vid se guarda en el BlockEntity (ya no como propiedad del blockstate,
            // para reducir el espacio de estados del bloque y acelerar la carga).
            if (level.getBlockEntity(newBlockpos) instanceof SoulVineBlockEntity svbe) {
                svbe.setSkillLevel(skillPoints);
                // Modo puente: crece RECTO hacia donde miras, sin necesitar pared, hasta topar con suelo,
                // pared o techo (entonces vuelve a la mecánica normal de agarre).
                svbe.setBridging(true);
                // La trayectoria que persigue TODA la vid: el vector de la mirada tal cual (en 3D), para que
                // un ángulo de 45° dé una escalera de 45° y no se encasille en un solo eje.
                Vec3 look = playerIn.getLookAngle();
                svbe.setAim(look.x, look.y, look.z);
            }


        }
    }
}
