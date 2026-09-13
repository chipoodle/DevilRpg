package com.chipoodle.devilrpg.util;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapability;
import com.chipoodle.devilrpg.capability.experience.PlayerExperienceCapabilityInterface;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Recompensas de misión (salvar una aldea, destruir el núcleo de una guarida...).
 * <p>
 * Se pagan <b>en experiencia</b>, no en puntos sueltos: subir un nivel dispara
 * {@code PlayerXpEvent.LevelChange}, que es lo que el mod usa para dar <b>1 punto de habilidad por nivel</b>
 * ({@link PlayerExperienceCapabilityInterface#setCurrentLevel(int, Player)}). Así la recompensa <b>también sube
 * la barra y el nivel</b> del jugador en vez de regalar puntos que no cuentan para la progresión.
 * <p>
 * Ojo con el orden si algún día se toca: NeoForge inyecta el evento <b>antes</b> de sumar el nivel
 * ({@code patches/net/minecraft/world/entity/player/Player.java.patch}), y {@code getLevels()} es el delta, así
 * que el cálculo {@code experienceLevel + niveles} que hace el suscriptor es el correcto.
 */
public final class MissionRewards {

    private MissionRewards() {
    }

    /**
     * Sube {@code niveles} de experiencia vanilla al jugador y devuelve <b>cuántos puntos de habilidad</b> ha
     * ganado con ellos (normalmente uno por nivel; {@code 0} si esos niveles ya los tenía cobrados, cosa que
     * puede pasar porque el mod solo paga cada nivel una vez).
     */
    public static int giveExperienceLevels(@Nullable Player player, int niveles) {
        if (player == null || niveles <= 0) {
            return 0;
        }
        PlayerExperienceCapabilityInterface expCap =
                IGenericCapability.getUnwrappedPlayerCapability(player, PlayerExperienceCapability.INSTANCE);
        int antes = expCap != null ? expCap.getUnspentPoints() : 0;
        // El evento de subida de nivel es síncrono: al volver de aquí el punto ya está sumado.
        player.giveExperienceLevels(niveles);
        return expCap != null ? expCap.getUnspentPoints() - antes : 0;
    }

    /** Texto de premio listo para el chat: "+2 niveles de experiencia (+2 puntos de habilidad)". */
    public static String describe(int niveles, int puntosGanados) {
        String texto = "+" + niveles + (niveles == 1 ? " nivel de experiencia" : " niveles de experiencia");
        if (puntosGanados > 0) {
            texto += " (+" + puntosGanados + (puntosGanados == 1 ? " punto de habilidad)" : " puntos de habilidad)");
        }
        return texto;
    }
}
