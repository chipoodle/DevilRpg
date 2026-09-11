package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Gestor del fondo del árbol de habilidades.
 * <p>
 * Mantiene la lista de fondos disponibles (el mosaico por defecto {@code mandala-tile.png} y cada una de las
 * imágenes de {@code textures/gui/mandalas/}) y el índice del seleccionado; {@code SkillTab} pide el fondo con
 * {@link #getSelected()}.
 * <p>
 * Los botones temporales que recorrían los fondos para elegir el definitivo <b>se quitaron</b> (eran
 * herramienta de desarrollo), así que hoy siempre se usa el fondo por defecto (índice 0). La lista de mandalas
 * se sigue cargando para cuando exista un selector de verdad.
 */
public final class SkillBackgroundManager {

    /** Fondo por defecto: el mosaico teselado (mandala-tile.png) que se pintaba antes de poder cambiarlo. */
    public static final ResourceLocation DEFAULT_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            DevilRpg.MODID, "textures/gui/skill/mandala-tile.png");

    /** Ruta lógica (sin namespace) del directorio donde están las imágenes de mandalas seleccionables. */
    private static final String MANDALAS_PATH = "textures/gui/mandalas";

    /** Lista de fondos: índice 0 = default, resto = imágenes de mandalas/ (ordenadas por nombre). */
    private static List<ResourceLocation> backgrounds;

    /** Índice del fondo seleccionado. */
    private static int selectedIndex = 0;

    private SkillBackgroundManager() {
    }

    private static List<ResourceLocation> buildBackgrounds() {
        List<ResourceLocation> list = new ArrayList<>();
        list.add(DEFAULT_BACKGROUND);
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getResourceManager() != null) {
                mc.getResourceManager()
                        .listResources(MANDALAS_PATH, rl -> {
                            String path = rl.getPath();
                            return path.endsWith(".png") || path.endsWith(".jpg");
                        })
                        .keySet().stream()
                        .sorted(Comparator.comparing(ResourceLocation::getPath))
                        .forEach(list::add);
            }
        } catch (Exception e) {
            DevilRpg.LOGGER.error("Error cargando fondos de mandalas", e);
        }
        if (list.isEmpty()) {
            list.add(DEFAULT_BACKGROUND);
        }
        return list;
    }

    private static List<ResourceLocation> getBackgrounds() {
        if (backgrounds == null) {
            backgrounds = buildBackgrounds();
        }
        return backgrounds;
    }

    /** Devuelve el ResourceLocation del fondo actualmente seleccionado. */
    public static ResourceLocation getSelected() {
        List<ResourceLocation> list = getBackgrounds();
        int idx = Math.max(0, Math.min(selectedIndex, list.size() - 1));
        return list.get(idx);
    }

    /** true si el fondo seleccionado es el mosaico por defecto (mandala-tile.png). */
    public static boolean isDefaultSelected() {
        return getSelected().equals(DEFAULT_BACKGROUND);
    }

    /** Nombre de archivo del fondo seleccionado (sin la ruta), para mostrarlo bajo los botones. */
    public static String getSelectedName() {
        String path = getSelected().getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** Avanza al siguiente fondo (vuelve al primero al pasar el último). */
    public static void next() {
        List<ResourceLocation> list = getBackgrounds();
        selectedIndex = (selectedIndex + 1) % list.size();
    }

    /** Retrocede al fondo anterior (vuelve al último al pasar el primero). */
    public static void prev() {
        List<ResourceLocation> list = getBackgrounds();
        selectedIndex = (selectedIndex - 1 + list.size()) % list.size();
    }
}
