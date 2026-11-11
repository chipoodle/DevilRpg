package com.chipoodle.devilrpg.agent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * DevilRpg-side navigation ownership for one villager.
 *
 * <p>This is a lease over navigation <b>intent</b>, not an interception of
 * Minecraft's {@code PathNavigation}. Vanilla Brain writers are not blocked by
 * this class in A7-01. The lease makes ownership observable and prevents two
 * DevilRpg tasks from both claiming the same villager.</p>
 *
 * <p>Pure Java, server-thread intended, no path or entity references.</p>
 */
public final class NavigationLease {

    public static final long DEFAULT_TTL_TICKS = 200L;

    private final UUID agentId;
    private final long ttlTicks;
    private NavigationOwner owner;
    private long eventSequence;

    public NavigationLease(UUID agentId) {
        this(agentId, DEFAULT_TTL_TICKS);
    }

    public NavigationLease(UUID agentId, long ttlTicks) {
        this.agentId = Objects.requireNonNull(agentId, "agentId");
        if (ttlTicks <= 0L) {
            throw new IllegalArgumentException("ttlTicks must be positive");
        }
        this.ttlTicks = ttlTicks;
    }

    public UUID agentId() {
        return agentId;
    }

    public long ttlTicks() {
        return ttlTicks;
    }

    /** Acquires or renews a lease. Only strictly higher priority may preempt another task. */
    public synchronized boolean acquire(UUID requesterAgentId, UUID taskId,
                                        NavigationPriority priority, long gameTime) {
        Objects.requireNonNull(requesterAgentId, "requesterAgentId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(priority, "priority");
        if (!agentId.equals(requesterAgentId)) {
            return false;
        }
        purgeExpired(gameTime);
        if (owner == null) {
            owner = new NavigationOwner(agentId, taskId, priority, gameTime, gameTime,
                    gameTime + ttlTicks);
            eventSequence++;
            return true;
        }
        if (owner.taskId().equals(taskId)) {
            owner = new NavigationOwner(agentId, taskId, priority, owner.acquiredAt(), gameTime,
                    gameTime + ttlTicks);
            eventSequence++;
            return true;
        }
        if (priority.rank() > owner.priority().rank()) {
            owner = new NavigationOwner(agentId, taskId, priority, gameTime, gameTime,
                    gameTime + ttlTicks);
            eventSequence++;
            return true;
        }
        return false;
    }

    public synchronized boolean renew(UUID requesterAgentId, UUID taskId, long gameTime) {
        if (!matches(requesterAgentId, taskId, gameTime)) {
            return false;
        }
        owner = new NavigationOwner(agentId, taskId, owner.priority(), owner.acquiredAt(),
                gameTime, gameTime + ttlTicks);
        eventSequence++;
        return true;
    }

    public synchronized boolean owns(UUID requesterAgentId, UUID taskId, long gameTime) {
        return matches(requesterAgentId, taskId, gameTime);
    }

    /** Releases only the exact agent/task pair; stale releases are ignored. */
    public synchronized void release(UUID requesterAgentId, UUID taskId) {
        if (owner != null && owner.agentId().equals(requesterAgentId)
                && owner.taskId().equals(taskId)) {
            owner = null;
            eventSequence++;
        }
    }

    /** Invalidates the lease regardless of task; used on death/removal/interruption. */
    public synchronized void invalidate() {
        if (owner != null) {
            owner = null;
            eventSequence++;
        }
    }

    public synchronized Optional<NavigationOwner> currentOwner(long gameTime) {
        purgeExpired(gameTime);
        return Optional.ofNullable(owner);
    }

    /** Returns true when an owner existed and was expired by this call. */
    public synchronized boolean purgeExpired(long gameTime) {
        if (owner != null && gameTime >= owner.expiresAt()) {
            owner = null;
            eventSequence++;
            return true;
        }
        return false;
    }

    public synchronized long eventSequence() {
        return eventSequence;
    }

    private boolean matches(UUID requesterAgentId, UUID taskId, long gameTime) {
        if (owner == null || !owner.agentId().equals(requesterAgentId)
                || !owner.taskId().equals(taskId)) {
            return false;
        }
        if (gameTime >= owner.expiresAt()) {
            owner = null;
            eventSequence++;
            return false;
        }
        return true;
    }
}
