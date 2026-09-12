package com.chipoodle.devilrpg.block;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Roca de los clérigos: la piedra central del círculo ritual de invocación del druida. Al darle clic
 * muestra la historia de su invocación y le da la dirección a la primera aldea (el objetivo ya apunta
 * hacia allí desde el ancla).
 * <p>
 * Además, la <b>primera</b> lectura le regala al jugador la experiencia justa para <b>subir un nivel</b>
 * (una sola vez por jugador: si no, la piedra sería una granja de XP infinita).
 */
public class LoreStoneBlock extends Block {

    private static final Component LORE = Component.literal(
            "La piedra está grabada con runas antiguas. 'Fuiste invocado desde otro mundo por los clérigos"
                    + " para combatir la corrupción que pudre esta tierra. La runa ardiente marca el camino"
                    + " hacia la primera aldea.'");

    public LoreStoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        showLore(level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        showLore(level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private void showLore(Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            player.displayClientMessage(LORE, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
            grantFirstReadLevelUp(player);
        }
    }

    /**
     * Regala la experiencia justa para subir un nivel, <b>solo la primera vez</b> que el jugador lee la
     * piedra (el estado se guarda en la capability auxiliar del jugador, así que sobrevive al reinicio).
     * <p>
     * {@code getXpNeededForNextLevel()} es lo que pide la barra del nivel actual, así que al darla el jugador
     * sube exactamente un nivel y se queda con el progreso que ya tuviera de sobra. Al subir de nivel, el mod
     * concede además el punto de habilidad que corresponde (evento {@code PlayerXpEvent.LevelChange}).
     */
    private void grantFirstReadLevelUp(Player player) {
        PlayerAuxiliaryCapabilityInterface aux =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null || aux.isLoreStoneRead()) {
            return;
        }
        aux.setLoreStoneRead(true, player);

        int xp = Math.max(1, player.getXpNeededForNextLevel());
        int levelBefore = player.experienceLevel;
        player.giveExperiencePoints(xp);
        player.displayClientMessage(Component.literal(
                "La piedra te bendice: +" + xp + " de experiencia (subes de nivel)."), false);
        DevilRpg.LOGGER.info("[LoreStone] Primera lectura de {}: +{} XP (nivel {} -> {})",
                player.getName().getString(), xp, levelBefore, player.experienceLevel);
    }
}
