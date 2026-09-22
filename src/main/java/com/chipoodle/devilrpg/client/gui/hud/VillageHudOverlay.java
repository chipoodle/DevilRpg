package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import com.chipoodle.devilrpg.survival.VillageBarText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * <b>La barra de ALDEA</b> (antes "barra de objetivos"): arriba en el centro, el nombre de la aldea a la que va el
 * jugador, la distancia que falta y una flecha hacia dónde queda (relativa a su giro).
 * <p>
 * Lo pidió el jugador: *"arriba donde está la barra de objetivos, que no diga objetivo 1, 2 etc, sino aldea, y cuando
 * se descubra que diga su nombre"*, y con el revelado progresivo: *"la siguiente aldea no va a aparecer su dirección
 * hasta que uno obtenga algo del mundo o alguien de la primera aldea o piedra de invocación… y ya así pueda aparecer
 * arriba, pero primero sin nombre y ya después con nombre cuando se descubra"*.
 * <p>
 * Los tres estados (todo lo sabe el cliente sin sincronizar aldeas, ver {@link VillageNames}):
 * <ul>
 *   <li><b>Sin revelar</b>: no hay barra. La dirección no se conoce hasta que la piedra de invocación (la primera) o
 *       el clérigo al vencer un asedio (las siguientes) se la digan.</li>
 *   <li><b>Revelada, no visitada</b>: {@code Aldea  (1.234 m) →} — la dirección sí, el nombre todavía no.</li>
 *   <li><b>Visitada</b> (el jugador ha entrado en ella): {@code Aldea de Valdehierro  (12 m) ↑}.</li>
 * </ul>
 * Todo se calcula en el cliente a partir de la capability auxiliar sincronizada (ancla + índice del objetivo + lo que
 * el jugador ha visitado y lo que le han revelado) y de la posición determinista del objetivo.
 */
public class VillageHudOverlay {

    public static final LayeredDraw.Layer HUD_ALDEA = (guiGraphics, deltaTracker) -> {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Font font = mc.font;
        if (player == null) {
            return;
        }

        PlayerAuxiliaryCapabilityInterface aux = IGenericCapability.getUnwrappedPlayerCapability(player, PlayerAuxiliaryCapability.INSTANCE);
        if (aux == null) {
            return;
        }
        Vec3 spawn = aux.getAnchorPoint();
        if (spawn == null) {
            spawn = aux.getSpawnPoint();
        }
        if (spawn == null) {
            return; // sin ancla -> sin aldea
        }
        int index = aux.getObjectiveIndex();
        boolean visitada = aux.isAldeaVisitada(index);
        boolean revelada = aux.isAldeaRevelada(index);
        BlockPos target = ObjectiveTargets.targetOf(spawn, index);

        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        // El TEXTO lo decide VillageBarText, que es compartido a propósito: así el arnés (servidor headless) mide
        // exactamente las mismas palabras que se dibujan aquí (los tres estados de I87).
        String text = VillageBarText.texto(index, visitada, revelada, (int) distance,
                directionArrow(dx, dz, player.getYRot()));
        if (text == null) {
            return; // todavía no sabe ni hacia dónde: sin barra (la dirección la revela la piedra o el clérigo)
        }
        int screenW = guiGraphics.guiWidth();
        int x = screenW / 2;
        int y = 12;

        int w = font.width(text);
        int bgX = x - w / 2 - 4;
        guiGraphics.fill(bgX, y, bgX + w + 8, y + font.lineHeight + 6, 0x66000000);
        // La aldea descubierta se pinta con nombre (dorado); la que solo está revelada, más apagada.
        guiGraphics.drawString(font, text, bgX + 4, y + 2, VillageBarText.color(visitada), true);
    };

    /**
     * Flecha cardinal (↑ ← → ↓) que indica la dirección del objetivo relativa al giro del jugador.
     * Usa la convención de yaw de MC (0 = sur, yaw positivo hacia la izquierda).
     */
    private static String directionArrow(double dx, double dz, float playerYaw) {
        // Yaw del objetivo en la convención de MC: vector delantero = (-sin(yaw), 0, cos(yaw)),
        // por lo que yaw que mira hacia (dx,dz) es atan2(-dx, dz).
        // En MC el yaw positivo gira a la DERECHA, así que rel>0 = el objetivo queda a tu derecha.
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = targetYaw - playerYaw;
        while (rel > 180.0) rel -= 360.0;
        while (rel < -180.0) rel += 360.0;
        if (rel > -45.0 && rel <= 45.0) return "↑";   // adelante
        if (rel > 45.0 && rel <= 135.0) return "→";   // a la derecha
        if (rel < -45.0 && rel >= -135.0) return "←"; // a la izquierda
        return "↓";                                    // atrás
    }
}
