package com.chipoodle.devilrpg.entity.goal;

/** Reglas puras de consistencia del estado forestal persistido. */
public final class ForestryTaskPolicy {
    private ForestryTaskPolicy() {}

    public static boolean consistent(String phase, String controller, boolean hasTarget) {
        if ("SCOUTING".equals(controller)) return !hasTarget;
        if (!hasTarget) return false;
        return switch (phase) {
            case "TALAR" -> "TRAVELLING".equals(controller) || "HARVESTING".equals(controller);
            case "PLANTAR" -> "REPLANTING".equals(controller);
            case "ABONAR" -> "GROWING".equals(controller);
            case "RECOGER_HARINA" -> "TRAVELLING".equals(controller);
            case "ENTREGAR" -> "DELIVERING".equals(controller) || "RETURNING".equals(controller);
            default -> false;
        };
    }
}
