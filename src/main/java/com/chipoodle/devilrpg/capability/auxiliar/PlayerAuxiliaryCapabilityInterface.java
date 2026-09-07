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

    /** Punto de inicio del mundo (el círculo ritual). Se usa para la dificultad y los objetivos; es distinto del respawn (cama). */
    Vec3 getAnchorPoint();
    void setAnchorPoint(Vec3 anchorPoint, Player player);

    /** Índice del objetivo de progresión actual (crece al completarlo; obliga a alejarse del spawn). */
    int getObjectiveIndex();
    void setObjectiveIndex(int objectiveIndex, Player player);

}
