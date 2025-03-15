package com.chipoodle.devilrpg.capability.auxiliar;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public interface PlayerAuxiliaryCapabilityInterface extends IGenericCapability {
    boolean isWerewolfAttack();

    void setWerewolfAttack(boolean active, Player player);

    boolean isWerewolfTransformation();

    void setWerewolfTransformation(boolean active, Player player);

    boolean isSwingingMainHand();

    void setSwingingMainHand(boolean active, Player player);

    InteractionHand swingHands(Player player);

    Vec3 getSpawnPoint();
    void setSpawnPoint(Vec3 blockPos, Player player);

}
