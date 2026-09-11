package com.chipoodle.devilrpg.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This holds the Client & Server Configs and the Client & Server ConfigSpecs.
 * It can be merged into the main ExampleModConfig class, but is separate
 * because of personal preference and to keep the code organised
 *
 * @author Cadiboo
 */
public final class ConfigHolder {

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec SERVER_SPEC;
    static final ClientConfig CLIENT;
    static final ServerConfig SERVER;

    static {
        {
            final Pair<ClientConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ClientConfig::new);
            CLIENT = specPair.getLeft();
            CLIENT_SPEC = specPair.getRight();
        }
        {
            final Pair<ServerConfig, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(ServerConfig::new);
            SERVER = specPair.getLeft();
            SERVER_SPEC = specPair.getRight();
        }
    }

    /**
     * Fondo del árbol de habilidades elegido por el jugador (nombre de archivo; {@code ""} = por defecto).
     * <p>
     * La GUI de skills es de cliente, así que la elección se guarda en la config de cliente para que
     * sobreviva al reinicio del juego. Si la config aún no está cargada (o algo falla), devuelve {@code ""}:
     * nunca debe tumbar la pantalla de habilidades.
     */
    public static String getSkillBackground() {
        try {
            return CLIENT.skillBackground.get();
        } catch (Exception e) {
            return "";
        }
    }

    /** Guarda el fondo elegido (lo llaman los botones de la pantalla de skills). */
    public static void setSkillBackground(String fileName) {
        try {
            CLIENT.skillBackground.set(fileName == null ? "" : fileName);
            CLIENT_SPEC.save();
        } catch (Exception e) {
            // Si la config no está lista, la elección simplemente no persiste: no es motivo para crashear.
        }
    }

    /** Skin de widget de los nodos elegido por el jugador (nombre de archivo; {@code ""} = por defecto). */
    public static String getSkillWidgetSkin() {
        try {
            return CLIENT.skillWidgetSkin.get();
        } catch (Exception e) {
            return "";
        }
    }

    /** Guarda el skin de widget elegido (lo llaman los botones de la pantalla de skills). */
    public static void setSkillWidgetSkin(String fileName) {
        try {
            CLIENT.skillWidgetSkin.set(fileName == null ? "" : fileName);
            CLIENT_SPEC.save();
        } catch (Exception e) {
            // Igual que arriba: mejor perder la preferencia que crashear la pantalla.
        }
    }
}
