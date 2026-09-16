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
 * Para lo <b>repetible</b> (hordas del mundo, que llegan cada pocos minutos) hay además
 * {@link #giveSkillPointFraction}: la enésima parte de la barra del nivel, o sea la enésima parte de un punto.
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

    /**
     * Paga <b>una fracción de un punto de habilidad</b>, en experiencia. Un punto de habilidad cuesta un nivel
     * entero del mod (lo que pide la barra es {@code getXpNeededForNextLevel()}), así que dar la enésima parte
     * de esa barra es dar la enésima parte del punto: con {@code partes = 6}, seis recompensas hacen un nivel
     * completo y su punto llega por el camino normal.
     * <p>
     * Es la forma de pagar lo <b>repetible</b>: una horda del mundo se rechaza cada pocos minutos, y pagar un
     * nivel entero cada vez regalaba un punto de habilidad por horda (el árbol entero en una tarde). Lo que se
     * cobra aquí es progreso de verdad: sube la barra del jugador, escala con su nivel (a más nivel, más vale
     * la misma fracción) y no inventa puntos sueltos.
     *
     * @return la experiencia concedida ({@code 0} si no había jugador o la fracción no tenía sentido)
     */
    public static int giveSkillPointFraction(@Nullable Player player, int partes) {
        if (player == null || partes <= 0) {
            return 0;
        }
        // Mínimo 1: a nivel 0 la barra pide 7 y una sexta parte es 1. Nunca cero, para que cobrar la recompensa
        // no sea "no ha pasado nada".
        int xp = Math.max(1, player.getXpNeededForNextLevel() / partes);
        player.giveExperiencePoints(xp);
        return xp;
    }

    /** Texto de premio de la fracción, listo para el chat: "+18 de experiencia (1/6 de un punto de habilidad)". */
    public static String describeFraction(int xp, int partes) {
        return "+" + xp + " de experiencia (1/" + partes + " de un punto de habilidad)";
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
