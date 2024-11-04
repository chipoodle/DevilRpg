package com.chipoodle.devilrpg.item;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapability;
import com.chipoodle.devilrpg.capability.mana.PlayerManaCapabilityInterface;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ManaBerryItem extends Item {
    public ManaBerryItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        // Duración estándar del consumo de alimentos en ticks
        return 32;
    }

    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        // Establecer la animación de "comer"
        return UseAnim.EAT;
    }

    @Override
    public boolean isEdible() {
        return true;
    }


    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, Level world, @NotNull LivingEntity entity) {
        if (!world.isClientSide && entity instanceof Player player) {
            PlayerManaCapabilityInterface manaCap = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerManaCapability.INSTANCE);
            manaCap.addMana(4, player);
        }
        return super.finishUsingItem(stack, world, entity);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level world, Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }


}
