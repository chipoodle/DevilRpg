package com.chipoodle.devilrpg.capability.experience;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import net.minecraft.world.entity.player.Player;

public interface PlayerExperienceCapabilityInterface extends IGenericCapability {
    int getCurrentLevel();

    int getMaximumLevel();

    int getUnspentPoints();

    void setCurrentLevel(int currentLevel, Player pe);

    /**
     * Regala puntos de habilidad ya disponibles, sin pasar por subir de nivel. Herramienta genérica (por ejemplo
     * para pruebas o para algún regalo puntual): las <b>recompensas de misión ya NO usan esto</b>, ahora pagan
     * <b>niveles de experiencia</b> y el punto llega por {@link #setCurrentLevel(int, Player)}
     * (ver {@code com.chipoodle.devilrpg.util.MissionRewards}).
     */
    void addUnspentPoints(int points, Player pe);

    int consumePoint();

    /**
     * Gasta un punto en el <b>servidor</b> (o en el lado que corresponda) y sincroniza al cliente si
     * procede. El cliente ya no manda su NBT de experiencia; el gasto lo autoriza el servidor al validar
     * el árbol de skills.
     */
    int consumePoint(Player player);

}
