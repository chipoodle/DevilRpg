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

    /**
     * ¿Ya ha leído la piedra de lore del círculo ritual? La primera lectura da la experiencia de un nivel,
     * una sola vez por jugador (si no, la piedra sería una granja de XP infinita).
     */
    boolean isLoreStoneRead();
    void setLoreStoneRead(boolean read, Player player);

    /**
     * ¿Ha <b>entrado</b> ya en la aldea de ese objetivo? Ese es el <b>descubrimiento</b>: la barra de aldea le pone
     * su nombre (antes solo dice "Aldea") y la aldea queda apuntada en el <b>Diario del Invocado</b> con sus
     * coordenadas. Lo pidió el jugador: *"un libro o algo que vaya guardando las aldeas descubiertas (sólo las que
     * uno ya haya entrado) junto con su estatus y sus coordenadas"*.
     */
    boolean isAldeaVisitada(int objectiveIndex);

    /** Apunta que el jugador ha entrado en esa aldea (idempotente). */
    void visitarAldea(int objectiveIndex, Player player);

    /**
     * ¿Le han <b>revelado la dirección</b> de esa aldea? La primera la revela la <b>piedra de invocación</b> y las
     * siguientes el <b>clérigo</b> al vencer el asedio: hasta entonces la barra de aldea no enseña ni la dirección
     * (*"la siguiente aldea no va a aparecer su dirección hasta que uno obtenga algo…"*).
     */
    boolean isAldeaRevelada(int objectiveIndex);

    /** Revela la dirección de esa aldea para este jugador (idempotente). */
    void revelarAldea(int objectiveIndex, Player player);

    /** Aldeas que el jugador ya ha entrado (para el Diario del Invocado). */
    java.util.Set<Integer> getAldeasVisitadas();

    /** Aldeas cuya dirección ya le han revelado (visitadas o no). */
    java.util.Set<Integer> getAldeasReveladas();

}
