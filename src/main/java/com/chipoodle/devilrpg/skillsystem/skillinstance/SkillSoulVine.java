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
        // La vid nace en el bloque donde estas parado (justo encima del suelo que pisas, para que no parezca
        // que flota), asi que solo se pide que ESE sitio se pueda ocupar. Antes se pedia el sitio de delante,
        // de la epoca en que la vid se agarraba a una pared.
        return puedeEmpezarEn(player.level(), player.blockPosition())
                && super.arePreconditionsMetBeforeConsumingResource(player);
    }

    /**
     * Dirección con la que nace el bloque raíz: la mirada del jugador <b>en 3D</b>, con <b>respaldo
     * horizontal</b> si apuntar en vertical no deja sitio (por ejemplo, mirando al suelo que pisas). Es solo la
     * dirección "de cara" del primer bloque; el crecimiento lo lleva la trayectoria guardada en el BlockEntity.
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
        // Tambien vale si hay fluido (nadando): el bloque de vid sustituye al agua sin problema.
        return state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty();
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
        // La raiz nace en el bloque donde estas parado, es decir JUSTO ENCIMA del bloque que pisas: asi la vid
        // siempre sale del suelo y no queda flotando cuando el terreno de al lado esta mas bajo.
        BlockPos newBlockpos = playerIn.blockPosition();
        SoulVineBlock createdBlock = ModBlocks.SOUL_VINE_BLOCK.get();
        // La vid nace apuntando a donde miras: crece siguiendo la trayectoria exacta de la mirada.
        Direction nearestDirection = lookDirection(playerIn);
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
