package com.chipoodle.devilrpg.entity.nav;

/**
 * DESIGN-02/IMPLEMENT-06 · pure block-interaction policy. Advisory only: the execution gate
 * ({@code breakBlockAt}/{@code puentearHacia}) must revalidate the live world before mutating it.
 */
public final class BlockInteractionPolicy {

    private BlockInteractionPolicy() {
    }

    public enum Action {
        BREAK, CLIMB, BRIDGE, DIG, DETOUR, ABANDON_OR_REPLAN
    }

    /**
     * @param protectedTarget target lies inside the authoritative village protection
     * @param perimeterWall   validated generated wall column/material/layer
     * @param ownBridge       siege-owned bridge block
     * @param blockEntity     state.hasBlockEntity()
     * @param unbreakable    bedrock or hardness < 0
     * @param obsidian       obsidian or crying obsidian
     * @param hazard         water/lava/air/unsupported
     * @param ordinary       breakable ordinary terrain
     * @param climbable      existing geometry says step/climb is feasible
     * @param bridgeCandidate existing placement geometry/budget says bridge is feasible
     * @param breaksUsed     successful removals already made in this recovery attempt
     * @param now            game time
     * @param failedUntil    failed-target veto expiry
     */
    public record Candidate(boolean protectedTarget, boolean perimeterWall, boolean ownBridge,
                            boolean blockEntity, boolean unbreakable, boolean obsidian, boolean hazard,
                            boolean ordinary, boolean climbable, boolean bridgeCandidate,
                            int breaksUsed, long now, long failedUntil) {
    }

    public record Decision(Action action, String reason) {
    }

    public static Decision decide(final Candidate c) {
        if (c == null) {
            return new Decision(Action.DETOUR, "sin-candidato");
        }
        if (c.protectedTarget && !c.perimeterWall) {
            return new Decision(Action.DETOUR, "protegido-por-aldea");
        }
        if (c.ownBridge) {
            return new Decision(Action.DETOUR, "obra-propia-del-asedio");
        }
        if (c.unbreakable) {
            return new Decision(Action.DETOUR, "inquebrantable/bedrock");
        }
        if (c.obsidian) {
            return new Decision(Action.DETOUR, "obsidiana-fuera-de-alcance-ordinario");
        }
        if (c.blockEntity) {
            return new Decision(Action.DETOUR, "block-entity");
        }
        if (c.hazard) {
            return new Decision(Action.DETOUR, "peligroso/sin-soporte");
        }
        if (c.breaksUsed >= 3) {
            return new Decision(Action.DETOUR, "limite-3-roturas");
        }
        if (c.now < c.failedUntil) {
            return new Decision(Action.DETOUR, "objetivo-vetado");
        }
        if (c.bridgeCandidate) {
            return new Decision(Action.BRIDGE, "puente-viable");
        }
        if (c.climbable) {
            return new Decision(Action.CLIMB, "escalon-viable");
        }
        if (c.ordinary) {
            return new Decision(c.perimeterWall ? Action.BREAK : Action.BREAK, "bloque-rompible");
        }
        return new Decision(Action.DETOUR, "sin-accion-segura");
    }
}
