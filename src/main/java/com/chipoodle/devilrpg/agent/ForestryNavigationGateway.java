package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import com.chipoodle.devilrpg.world.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.npc.Villager;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Gateway for selected DevilRpg forestry navigation commands. It never plans paths and
 * never intercepts vanilla Brain navigation. OFF is the default.
 */
public final class ForestryNavigationGateway {

    public enum Mode {
        OFF,
        AUDIT,
        ENFORCE
    }

    public enum Result {
        OFF_DELEGATED,
        AUTHORIZED,
        CLEANUP_ALLOWED,
        UNAUTHORIZED_AUDITED,
        DENIED_NO_LEASE,
        DENIED_WRONG_TASK,
        DENIED_EXPIRED
    }

    private ForestryNavigationGateway() {
    }

    public static Mode mode() {
        String raw = System.getProperty("devilrpg.agent.navigationMode", "OFF");
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.OFF;
        }
    }

    public static Result moveTo(VillagerAgent agent, Villager villager, UUID taskId,
                                BlockPos target, long gameTime, float speed) {
        Mode mode = mode();
        Result result = moveToCore(mode,
                agent == null ? null : agent.entityId(),
                agent == null ? null : agent.navigationLease(),
                taskId, gameTime,
                () -> VillageManager.caminarHacia(villager, target, speed));
        if (agent != null && (result == Result.AUTHORIZED || result == Result.OFF_DELEGATED
                || result == Result.UNAUTHORIZED_AUDITED)) {
            agent.recordNavigation(taskId);
        }
        log(agent, taskId, "MOVE", target, mode, result, gameTime);
        return result;
    }

    public static Result stop(VillagerAgent agent, Villager villager, UUID taskId,
                              long gameTime) {
        Mode mode = mode();
        Result result = stopCore(mode,
                agent == null ? null : agent.entityId(),
                agent == null ? null : agent.navigationLease(),
                taskId,
                agent == null ? null : agent.lastNavigationTaskId().orElse(null),
                gameTime,
                () -> villager.getNavigation().stop());
        if (agent != null && (result == Result.AUTHORIZED || result == Result.OFF_DELEGATED
                || result == Result.CLEANUP_ALLOWED || result == Result.UNAUTHORIZED_AUDITED)) {
            agent.clearNavigation(taskId);
        }
        log(agent, taskId, "STOP", null, mode, result, gameTime);
        return result;
    }

    /** Actual movement decision/delegation core, testable without a world. */
    public static Result moveToCore(Mode mode, UUID agentId, NavigationLease lease,
                                    UUID taskId, long gameTime, NavigationDelegate delegate) {
        boolean owns = agentId != null && taskId != null && lease != null
                && lease.owns(agentId, taskId, gameTime);
        NavigationGatewayPolicy.Action action = NavigationGatewayPolicy.movement(mode, owns);
        if (action == NavigationGatewayPolicy.Action.DENY) {
            return denied(lease, gameTime);
        }
        delegate.run();
        if (mode == Mode.OFF) return Result.OFF_DELEGATED;
        return owns ? Result.AUTHORIZED : Result.UNAUTHORIZED_AUDITED;
    }

    /** Actual stop/cancellation decision/delegation core, testable without a world. */
    public static Result stopCore(Mode mode, UUID agentId, NavigationLease lease,
                                  UUID taskId, UUID lastNavigationTaskId,
                                  long gameTime, NavigationDelegate delegate) {
        boolean owns = agentId != null && taskId != null && lease != null
                && lease.owns(agentId, taskId, gameTime);
        Optional<NavigationOwner> current = lease == null
                ? Optional.empty() : lease.currentOwner(gameTime);
        boolean otherOwner = current.filter(o -> !o.taskId().equals(taskId)).isPresent();
        boolean hasAttribution = lastNavigationTaskId != null;
        boolean attributionMatches = taskId != null && taskId.equals(lastNavigationTaskId);
        NavigationGatewayPolicy.Action action = NavigationGatewayPolicy.stop(
                mode, owns, otherOwner, hasAttribution, attributionMatches);
        if (action == NavigationGatewayPolicy.Action.DENY) {
            return denied(lease, gameTime);
        }
        delegate.run();
        if (owns) return Result.AUTHORIZED;
        if (mode == Mode.OFF) return Result.OFF_DELEGATED;
        if (action == NavigationGatewayPolicy.Action.CLEANUP) return Result.CLEANUP_ALLOWED;
        return Result.UNAUTHORIZED_AUDITED;
    }

    private static Result denied(NavigationLease lease, long gameTime) {
        if (lease == null) {
            return Result.DENIED_NO_LEASE;
        }
        if (lease.currentOwner(gameTime).isEmpty()) {
            return Result.DENIED_EXPIRED;
        }
        return Result.DENIED_WRONG_TASK;
    }

    private static void log(VillagerAgent agent, UUID taskId, String command,
                            BlockPos target, Mode mode, Result result, long gameTime) {
        boolean unauthorized = result == Result.UNAUTHORIZED_AUDITED
                || result == Result.DENIED_NO_LEASE
                || result == Result.DENIED_WRONG_TASK
                || result == Result.DENIED_EXPIRED;
        if (!unauthorized && !Boolean.getBoolean("devilrpg.agent.navdiag")) {
            return;
        }
        DevilRpg.LOGGER.info("[NAV-GATE] mode={} result={} source=forestry command={} uuid={} task={} target={} owner={}",
                mode, result, command, agent == null ? "-" : agent.entityId(), taskId,
                target == null ? "-" : target.toShortString(),
                agent == null ? "-" : agent.navigationLease().currentOwner(gameTime));
    }
}
