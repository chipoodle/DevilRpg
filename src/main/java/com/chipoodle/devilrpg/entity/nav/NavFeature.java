package com.chipoodle.devilrpg.entity.nav;

/**
 * A2 · <b>Bandera reversible</b> del subsistema de navegación propia.
 * <p>
 * Por defecto está <b>APAGADA</b>: la implementación que corre es la de siempre
 * ({@code AggressiveZombiePathPlanner}), tal y como exige el plan de migración («mantener la implementación actual
 * disponible para comparar»). Encenderla enruta los objetivos de navegación por {@link NavExecutor}.
 * <p>
 * Se controla por propiedad de sistema, así que <b>no</b> hay que recompilar para comparar las dos:
 * {@code -Ddevilrpg.nav.custom=true}. También vale la variable de entorno {@code DEVILRPG_NAV_CUSTOM=1}, que es lo
 * que usa el arnés para encenderla en una corrida concreta.
 */
public final class NavFeature {

    public static final String PROPERTY = "devilrpg.nav.custom";
    public static final String ENV = "DEVILRPG_NAV_CUSTOM";

    private static final boolean ENABLED = resolve();

    private NavFeature() {
    }

    private static boolean resolve() {
        String prop = System.getProperty(PROPERTY);
        if (prop != null) {
            return "true".equalsIgnoreCase(prop) || "1".equals(prop);
        }
        String env = System.getenv(ENV);
        return env != null && ("1".equals(env) || "true".equalsIgnoreCase(env));
    }

    /** ¿Está encendida la navegación propia? Se lee una sola vez, al cargar la clase. */
    public static boolean customNavigation() {
        return ENABLED;
    }
}
