package com.chipoodle.devilrpg.block;

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
        }
    }
}
