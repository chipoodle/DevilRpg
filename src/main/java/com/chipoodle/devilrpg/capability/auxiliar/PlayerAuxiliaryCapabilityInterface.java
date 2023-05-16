package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.nbt.CompoundTag;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.INBTSerializable;

public interface PlayerAuxiliaryCapabilityInterface extends IGenericCapability {
    boolean isWerewolfAttack();

    void setWerewolfAttack(boolean active, Player player);

    boolean isWerewolfTransformation();

    void setWerewolfTransformation(boolean active, Player player);

    boolean isSwingingMainHand();

    void setSwingingMainHand(boolean active, Player player);

    InteractionHand swingHands(Player player);

}
