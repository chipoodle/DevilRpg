package com.chipoodle.devilrpg.skillsystem;

/**
 * <b>Skills runtime · gasto de puntos.</b>
 * <p>
 * El cliente mandaba el árbol completo y el servidor lo aceptaba. La regla de servidor: el total de
 * puntos invertidos solo puede <b>quedarse igual</b> (recolocar poderes) o <b>subir exactamente 1</b>
 * (gastar un punto), y para subir 1 tiene que haber un punto sin gastar en el servidor. Nunca se
 * permite bajar el total ni subir más de un punto por paquete.
 */
public final class SkillTreeSpendPolicy {

    private SkillTreeSpendPolicy() {
    }

    public static boolean permite(int totalAntes, int totalDespues, int puntosSinGastar) {
        if (totalDespues < totalAntes) {
            return false;
        }
        if (totalDespues == totalAntes) {
            return true;
        }
        return totalDespues == totalAntes + 1 && puntosSinGastar > 0;
    }
}
