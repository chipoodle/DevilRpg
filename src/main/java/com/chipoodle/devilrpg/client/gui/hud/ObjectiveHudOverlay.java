package com.chipoodle.devilrpg.client.gui.hud;

import com.chipoodle.devilrpg.capability.IGenericCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapability;
import com.chipoodle.devilrpg.capability.auxiliar.PlayerAuxiliaryCapabilityInterface;
import com.chipoodle.devilrpg.survival.ObjectiveTargets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * HUD del objetivo de progresión. Muestra en la parte superior central el objetivo actual (índice), la
 * distancia horizontal que falta y una flecha que indica hacia dónde ir (relativa al giro del jugador).
 * <p>
 * Todo se calcula en el cliente a partir de la capability auxiliar sincronizada (spawn + índice del
 * objetivo) y de la posición determinista del objetivo.
 */
public class ObjectiveHudOverlay {

    public static final LayeredDraw.Layer HUD_OBJECTIVE = (guiGraphics, deltaTracker) -> {
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
        Vec3 spawn = aux.getSpawnPoint();
        if (spawn == null) {
            return; // sin spawn -> sin objetivo
        }
        int index = aux.getObjectiveIndex();
        BlockPos target = ObjectiveTargets.targetOf(spawn, index);

        double dx = target.getX() - player.getX();
        double dz = target.getZ() - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        String text = "Objetivo " + (index + 1) + "  (" + (int) distance + " m) " + directionArrow(dx, dz, player.getYRot());
        int screenW = guiGraphics.guiWidth();
        int x = screenW / 2;
        int y = 12;

        String line = text;
        int w = font.width(line);
        int bgX = x - w / 2 - 4;
        guiGraphics.fill(bgX, y, bgX + w + 8, y + font.lineHeight + 6, 0x66000000);
        guiGraphics.drawString(font, line, bgX + 4, y + 2, 0xFFFFDD88, true);
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
