package com.chipoodle.devilrpg.client.gui.scrollableskillscreen;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.config.ConfigHolder;
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
            applySavedSelection();
        }
        return backgrounds;
    }

    /**
     * Aplica el fondo guardado en la config de cliente (el que se eligió con los botones en una sesión
     * anterior). Si el archivo ya no existe, se queda el de por defecto.
     */
    private static void applySavedSelection() {
        String saved = ConfigHolder.getSkillBackground();
        if (saved == null || saved.isEmpty()) {
            return;
        }
        for (int i = 0; i < backgrounds.size(); i++) {
            if (fileNameOf(backgrounds.get(i)).equals(saved)) {
                selectedIndex = i;
                return;
            }
        }
    }

    /** Nombre de archivo de un fondo (sin la ruta). */
    private static String fileNameOf(ResourceLocation resourceLocation) {
        String path = resourceLocation.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
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
        return fileNameOf(getSelected());
    }

    /** Índice del fondo seleccionado (0-based), para poder mostrar "3/39" bajo los botones. */
    public static int getSelectedIndex() {
        List<ResourceLocation> list = getBackgrounds();
        return Math.max(0, Math.min(selectedIndex, list.size() - 1));
    }

    /** Cuántos fondos hay disponibles. */
    public static int getBackgroundCount() {
        return getBackgrounds().size();
    }

    /** Avanza al siguiente fondo (vuelve al primero al pasar el último) y lo guarda en la config. */
    public static void next() {
        List<ResourceLocation> list = getBackgrounds();
        selectedIndex = (selectedIndex + 1) % list.size();
        persistSelection();
    }

    /** Retrocede al fondo anterior (vuelve al último al pasar el primero) y lo guarda en la config. */
    public static void prev() {
        List<ResourceLocation> list = getBackgrounds();
        selectedIndex = (selectedIndex - 1 + list.size()) % list.size();
        persistSelection();
    }

    /** Guarda el fondo elegido para que siga puesto la próxima vez que se abra el juego. */
    private static void persistSelection() {
        ConfigHolder.setSkillBackground(getSelectedName());
    }
}
