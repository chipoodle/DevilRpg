package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-villager DevilRpg agent. Transient in A7-01: no entity reference is retained and no
 * new NBT is written. It records identity, profession, provisional dimension identity,
 * current task and navigation lease.
 */
public final class VillagerAgent {

    private static final boolean DIAG = Boolean.getBoolean("devilrpg.agentdiag");

    private final UUID entityId;
    private final ResourceKey<Level> dimension;
    private final NavigationLease navigationLease;
    private String profession;
    private UUID taskId;
    private String taskKind;
    private AgentTaskState state = AgentTaskState.IDLE;
    private ForestryTaskSnapshot forestrySnapshot;
    private UUID lastNavigationTaskId;
    private BlockPos selectedTalarTarget;
    private final TalarStepPlanner.Cache talarStepCache = new TalarStepPlanner.Cache();

    public VillagerAgent(UUID entityId, ResourceKey<Level> dimension, String profession) {
        this.entityId = Objects.requireNonNull(entityId, "entityId");
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        this.profession = profession == null ? "unknown" : profession;
        this.navigationLease = new NavigationLease(entityId);
    }

    public UUID entityId() {
        return entityId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public String profession() {
        return profession;
    }

    /** Provisional identity until VillageSpatialModel exists in A7-02. */
    public String villageKey() {
        return dimension.location() + ":unassigned";
    }

    public NavigationLease navigationLease() {
        return navigationLease;
    }

    public Optional<UUID> taskId() {
        return Optional.ofNullable(taskId);
    }

    public Optional<String> taskKind() {
        return Optional.ofNullable(taskKind);
    }

    public AgentTaskState state() {
        return state;
    }

    public Optional<ForestryTaskSnapshot> forestrySnapshot() {
        return Optional.ofNullable(forestrySnapshot);
    }

    public java.util.Optional<UUID> lastNavigationTaskId() {
        return java.util.Optional.ofNullable(lastNavigationTaskId);
    }

    public void recordNavigation(UUID taskId) {
        if (taskId != null) lastNavigationTaskId = taskId;
    }

    public void clearNavigation(UUID taskId) {
        if (taskId == null || taskId.equals(lastNavigationTaskId)) lastNavigationTaskId = null;
    }

    public void observeForestry(ForestryTaskSnapshot snapshot) {
        if (snapshot != null && entityId.equals(snapshot.villagerId())) {
            this.forestrySnapshot = snapshot;
        }
    }

    public void observe(Villager villager) {
        if (villager == null || !entityId.equals(villager.getUUID())) {
            return;
        }
        profession = villager.getVillagerData().getProfession().toString();
    }

    public boolean beginTask(UUID newTaskId, String kind, NavigationPriority priority, long gameTime) {
        if (state != AgentTaskState.IDLE && state != AgentTaskState.COMPLETED) {
            if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] DENIED uuid={} task={} state={} owner={}",
                    entityId, newTaskId, state, navigationLease.currentOwner(gameTime));
            return false;
        }
        if (!navigationLease.acquire(entityId, newTaskId, priority, gameTime)) {
            if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] DENIED uuid={} task={} priority={} owner={}",
                    entityId, newTaskId, priority, navigationLease.currentOwner(gameTime));
            return false;
        }
        this.taskId = newTaskId;
        this.taskKind = kind;
        this.state = AgentTaskState.TRAVELLING;
        if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] ACQUIRE uuid={} task={} kind={} priority={} until={}",
                entityId, newTaskId, kind, priority,
                navigationLease.currentOwner(gameTime).orElseThrow().expiresAt());
        return true;
    }

    public boolean renewNavigation(long gameTime) {
        if (taskId == null) {
            return false;
        }
        return navigationLease.renew(entityId, taskId, gameTime);
    }

    public void completeTask(long gameTime) {
        UUID old = taskId;
        if (taskId != null) {
            navigationLease.release(entityId, taskId);
        }
        taskId = null;
        taskKind = null;
        state = AgentTaskState.COMPLETED;
        if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] COMPLETE uuid={} task={} tick={}", entityId, old, gameTime);
    }

    public void interruptTask(String reason) {
        UUID old = taskId;
        if (taskId != null) {
            navigationLease.release(entityId, taskId);
        }
        taskId = null;
        taskKind = null;
        state = AgentTaskState.INTERRUPTED;
        if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] INTERRUPT uuid={} task={} reason={}", entityId, old, reason);
    }

    public void invalidate() {
        UUID old = taskId;
        navigationLease.invalidate();
        lastNavigationTaskId = null;
        taskId = null;
        taskKind = null;
        state = AgentTaskState.IDLE;
        if (DIAG) DevilRpg.LOGGER.info("[AGENT-LEASE] INVALIDATE uuid={} task={}", entityId, old);
    }

    /** Agent-owned final TALAR target choice. Candidate generation stays in the Goal. */
    public Optional<BlockPos> selectTalarTarget(List<BlockPos> inside, List<BlockPos> outside,
                                                 BlockPos workerPosition) {
        Optional<BlockPos> selected = TalarTargetSelector.select(inside, outside, workerPosition);
        selectedTalarTarget = selected.orElse(null);
        if (selected.isPresent() && Boolean.getBoolean("devilrpg.agentdiag")) {
            DevilRpg.LOGGER.info("[AGENT-TARGET] TALAR uuid={} target={} inside={} outside={}",
                    entityId, selected.get(), inside, outside);
        }
        return selected;
    }

    public Optional<BlockPos> selectedTalarTarget() {
        return Optional.ofNullable(selectedTalarTarget);
    }
    /** Agent-owned TALAR next-step decision using the shared planner. */
    public TalarStepPlanner.Step nextTalarStep(net.minecraft.server.level.ServerLevel level,
                                                BlockPos target, BlockPos center,
                                                Villager villager) {
        TalarStepPlanner.Step step = TalarStepPlanner.next(level, target, center, villager, talarStepCache, true);
        if (Boolean.getBoolean("devilrpg.agentdiag") && step.destination() != null) {
            DevilRpg.LOGGER.info("[AGENT-STEP] TALAR uuid={} target={} step={} standing={} gate={}",
                    entityId, target, step.destination(), step.standingCell(), step.wallGate());
        }
        if (step.destination() == null) talarStepCache.clear();
        return step;
    }
    /** Agent-owned forestry navigation command. Returns the gateway result. */
    public ForestryNavigationGateway.Result navigateTo(Villager villager, BlockPos target,
                                                       long gameTime, float speed) {
        ForestryTaskSnapshot snapshot = forestrySnapshot;
        UUID task = snapshot == null ? null : snapshot.taskId();
        if (task == null && ForestryNavigationGateway.mode() == ForestryNavigationGateway.Mode.ENFORCE) {
            return ForestryNavigationGateway.Result.DENIED_NO_LEASE;
        }
        if (ForestryNavigationGateway.mode() != ForestryNavigationGateway.Mode.OFF
                && task != null) {
            syncForestryNavigation(gameTime);
        }
        ForestryNavigationGateway.Result result = ForestryNavigationGateway.moveTo(this, villager, task, target, gameTime, speed);
        if (Boolean.getBoolean("devilrpg.r9diag")) {
            var p = villager.getNavigation().getPath();
            DevilRpg.LOGGER.info("[R9-NAV] uuid={} task={} target={} result={} owner={} path={} active={} tick={}",
                    entityId, task, target, result, navigationLease.currentOwner(gameTime),
                    p == null ? 0 : System.identityHashCode(p), villager.getNavigation().isInProgress(), gameTime);
        }
        return result;
    }

    /** Agent-owned stop for its current/last forestry navigation task. */
    public ForestryNavigationGateway.Result stopNavigation(Villager villager, long gameTime) {
        UUID task = navigationLease.currentOwner(gameTime)
                .map(NavigationOwner::taskId).orElse(lastNavigationTaskId);
        if (task == null) {
            return ForestryNavigationGateway.Result.CLEANUP_ALLOWED;
        }
        ForestryNavigationGateway.Result result = ForestryNavigationGateway.stop(
                this, villager, task, gameTime);
        if (result == ForestryNavigationGateway.Result.AUTHORIZED
                || result == ForestryNavigationGateway.Result.CLEANUP_ALLOWED
                || result == ForestryNavigationGateway.Result.OFF_DELEGATED) {
            if (task != null) navigationLease.release(entityId, task);
        }
        return result;
    }

    /** Renews or creates the forestry lease from the latest observed snapshot. */
    public boolean syncForestryNavigation(long gameTime) {
        if (ForestryNavigationGateway.mode() == ForestryNavigationGateway.Mode.OFF) {
            return false;
        }
        ForestryTaskSnapshot snapshot = forestrySnapshot;
        if (snapshot == null || !snapshot.active() || snapshot.taskId() == null) {
            return false;
        }
        UUID task = snapshot.taskId();
        NavigationOwner owner = navigationLease.currentOwner(gameTime).orElse(null);
        if (owner != null && !owner.taskId().equals(task)) {
            navigationLease.release(entityId, owner.taskId());
        }
        boolean ok = navigationLease.owns(entityId, task, gameTime)
                ? navigationLease.renew(entityId, task, gameTime)
                : navigationLease.acquire(entityId, task, NavigationPriority.WORK, gameTime);
        if (ok) {
            boolean changed = owner == null || !owner.taskId().equals(task);
            taskId = task;
            lastNavigationTaskId = task;
            state = AgentTaskState.TRAVELLING;
            if (changed && Boolean.getBoolean("devilrpg.agentdiag")) {
                DevilRpg.LOGGER.info("[AGENT-AUTH] ACQUIRE uuid={} task={} owner={}",
                        entityId, task, navigationLease.currentOwner(gameTime));
            }
        }
        return ok;
    }

    public void releaseNavigation() {
        UUID task = navigationLease.currentOwner(0L)
                .map(NavigationOwner::taskId).orElse(lastNavigationTaskId);
        if (task != null) navigationLease.release(entityId, task);
    }
    public void tick(long gameTime) {
        if (navigationLease.purgeExpired(gameTime) && DIAG) {
            DevilRpg.LOGGER.info("[AGENT-LEASE] EXPIRED uuid={} tick={}", entityId, gameTime);
        }
    }
}
