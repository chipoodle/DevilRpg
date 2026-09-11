package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.world.entity.player.Player;

public interface PlayerExperienceCapabilityInterface extends IGenericCapability {
    int getCurrentLevel();

    int getMaximumLevel();

    int getUnspentPoints();

    void setCurrentLevel(int currentLevel, Player pe);

    /**
     * Regala puntos de habilidad ya disponibles, sin pasar por subir de nivel. Son la moneda del árbol de
     * skills, así que se usan para recompensas de misión (salvar una aldea, destruir un núcleo de guarida...).
     */
    void addUnspentPoints(int points, Player pe);

    int consumePoint();

}
