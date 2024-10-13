package com.chipoodle.devilrpg.skillsystem;

import com.chipoodle.devilrpg.capability.skill.PlayerSkillCapabilityInterface;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public abstract class AbstractSkillSeedsInInventoryExecutor extends AbstractSkillExecutor {
    public AbstractSkillSeedsInInventoryExecutor(PlayerSkillCapabilityInterface parentCapability) {
        super(parentCapability);
    }

    public boolean arePreconditionsMetBeforeConsumingResource(Player player) {

        // Verificar si hay semillas en el inventario
        return player.getInventory().contains(new ItemStack(Items.WHEAT_SEEDS)) ||
                player.getInventory().contains(new ItemStack(Items.BEETROOT_SEEDS)) ||
                player.getInventory().contains(new ItemStack(Items.MELON_SEEDS)) ||
                player.getInventory().contains(new ItemStack(Items.PUMPKIN_SEEDS));
    }

    protected void consumeSeed(Player player) {
        // Recorrer el inventario para encontrar y consumir una semilla
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.WHEAT_SEEDS ||
                    stack.getItem() == Items.BEETROOT_SEEDS ||
                    stack.getItem() == Items.MELON_SEEDS ||
                    stack.getItem() == Items.PUMPKIN_SEEDS) {

                stack.shrink(1); // Reducir la cantidad de la semilla en 1
                break;
            }
        }
    }
}
