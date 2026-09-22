package com.chipoodle.devilrpg.survival;

import org.jetbrains.annotations.Nullable;

/**
 * <b>El texto de la barra de ALDEA</b>, en un solo sitio y sin nada del cliente: lo dibuja
 * {@code VillageHudOverlay} y lo <b>mide el arnés</b> (que es un servidor headless y no puede cargar clases del
 * cliente). Así lo que se comprueba en la medida es exactamente lo que se dibuja.
 * <p>
 * Los tres estados (lo pidió el jugador en I87):
 * <ul>
 *   <li><b>Sin revelar</b> (ni dirección ni visita): {@code null} — no se dibuja NADA.</li>
 *   <li><b>Revelada, sin visitar</b>: {@code Aldea  (1.234 m) →} — dirección sí, nombre no.</li>
 *   <li><b>Visitada</b> (ha entrado): {@code Aldea de Valdehierro  (12 m) ↑} — con su nombre.</li>
 * </ul>
 */
public final class VillageBarText {

    /** Color del texto cuando la aldea ya está <b>descubierta</b> (dorado). */
    public static final int COLOR_DESCUBIERTA = 0xFFFFDD88;
    /** Color del texto cuando solo está <b>revelada</b> (más apagado). */
    public static final int COLOR_SOLO_REVELADA = 0xFFCFC6AE;

    private VillageBarText() {
    }

    /**
     * Lo que dice la barra, o {@code null} si no hay que dibujar barra ninguna (el jugador todavía no sabe ni hacia
     * dónde queda esa aldea).
     *
     * @param objectiveIndex aldea de la que habla la barra
     * @param visitada       ¿ha entrado en ella? (entonces se enseña su nombre)
     * @param revelada       ¿le han dado la dirección?
     * @param metros         distancia horizontal que falta
     * @param flecha         la flecha cardinal ya calculada relativa a su giro
     */
    @Nullable
    public static String texto(int objectiveIndex, boolean visitada, boolean revelada, int metros, String flecha) {
        if (!visitada && !revelada) {
            return null;
        }
        String nombre = visitada ? VillageNames.nombre(objectiveIndex) : "Aldea";
        return nombre + "  (" + metros + " m) " + flecha;
    }

    /** El color con el que se pinta ese texto (dorado si la ha descubierto, apagado si solo se la han revelado). */
    public static int color(boolean visitada) {
        return visitada ? COLOR_DESCUBIERTA : COLOR_SOLO_REVELADA;
    }
}
