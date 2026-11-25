package com.chipoodle.devilrpg.util;

/**
 * Ritmo de regeneración de maná/stamina, compartido por cliente (predicción) y servidor (autoridad).
 * Antes solo lo aplicaba el cliente y el servidor dependía del NBT que le mandaba el cliente; al
 * cerrar esa vía, el servidor debe regenerar por su cuenta con los mismos números.
 */
public final class PlayerResourceRegenPolicy {

    public static final float BASE_MANA_DEGENERATION = 0.7F;
    public static final float BASE_STAMINA_DEGENERATION = 0.05F;

    private PlayerResourceRegenPolicy() {
    }

    /** Degeneración de maná por tick de regeneración; 0 si no está transformado. */
    public static float manaDegeneration(boolean transformado, int puntosTransformacion) {
        return transformado ? BASE_MANA_DEGENERATION - 0.01F * puntosTransformacion : 0.0F;
    }
}
