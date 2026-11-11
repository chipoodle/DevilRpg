package com.chipoodle.devilrpg.agent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ephemeral, server-side cross-worker claim for a TALAR tree.
 *
 * <p><b>Scope:</b> one claim per {@code (dimension, tree position)}. The tree position is
 * globally unique, so the claim is world-scoped rather than per-village or per-agent. This
 * prevents two lumberjacks in the same or different villages from working the same tree.
 * Claims are intentionally <b>not</b> persisted: a task claim is transient, and a reloaded
 * worker re-discovers and re-claims through the normal lifecycle.</p>
 *
 * <p><b>Lifetime:</b> a claim is renewed by its owner every Goal tick. If the owner stops
 * renewing (death, removal, unload, crash), the claim expires after
 * {@link #CLAIM_TTL_TICKS} and another worker may acquire it. Release is owner- and
 * task-validated, so a stale owner cannot erase a newer claim.</p>
 *
 * <p>Pure Java on purpose: dimension id + packed tree position, no Minecraft/NeoForge
 * references, so the focused policy test runs without the game classpath. The Goal and the
 * agent registry bridge from {@code level.dimension()} / {@code BlockPos.asLong()} and emit
 * the human-readable claim events.</p>
 */
public final class ForestryTreeClaims {

    /** Ticks without renewal before another worker may take the claim. */
    public static final long CLAIM_TTL_TICKS = 200L;

    private static final Map<Key, Claim> CLAIMS = new HashMap<>();

    private ForestryTreeClaims() {
    }

    private record Key(String dimension, long tree) {
    }

    private record Claim(UUID worker, UUID task, long acquiredAt, long renewedAt) {
    }

    /**
     * Acquires the claim, or renews it when this worker/task already owns it.
     *
     * @return true when the caller now owns the claim; false when another owner has it
     */
    public static synchronized boolean acquire(String dimension, long tree,
                                               UUID worker, UUID task, long gameTime) {
        Key key = new Key(dimension, tree);
        Claim current = CLAIMS.get(key);
        if (current != null && gameTime - current.renewedAt() >= CLAIM_TTL_TICKS) {
            CLAIMS.remove(key);
            current = null;
        }
        if (current == null) {
            CLAIMS.put(key, new Claim(worker, task, gameTime, gameTime));
            return true;
        }
        if (current.worker().equals(worker) && current.task().equals(task)) {
            CLAIMS.put(key, new Claim(worker, task, current.acquiredAt(), gameTime));
            return true;
        }
        return false;
    }

    /** Renews this worker's claim; false when it no longer owns it or it expired. */
    public static synchronized boolean renew(String dimension, long tree,
                                             UUID worker, UUID task, long gameTime) {
        Key key = new Key(dimension, tree);
        Claim current = CLAIMS.get(key);
        if (current == null) {
            return false;
        }
        if (gameTime - current.renewedAt() >= CLAIM_TTL_TICKS) {
            CLAIMS.remove(key);
            return false;
        }
        if (!current.worker().equals(worker) || !current.task().equals(task)) {
            return false;
        }
        CLAIMS.put(key, new Claim(worker, task, current.acquiredAt(), gameTime));
        return true;
    }

    /** Releases only when the caller still owns this exact claim. */
    public static synchronized boolean release(String dimension, long tree,
                                               UUID worker, UUID task) {
        Key key = new Key(dimension, tree);
        Claim current = CLAIMS.get(key);
        if (current == null) {
            return false;
        }
        if (!current.worker().equals(worker) || !current.task().equals(task)) {
            return false;
        }
        CLAIMS.remove(key);
        return true;
    }

    /** Releases every claim owned by a worker (death, removal, unload). */
    public static synchronized int releaseAll(UUID worker) {
        int released = 0;
        Iterator<Map.Entry<Key, Claim>> it = CLAIMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Claim> entry = it.next();
            if (entry.getValue().worker().equals(worker)) {
                it.remove();
                released++;
            }
        }
        return released;
    }

    /** True when no live claim exists or the requester already owns it. */
    public static synchronized boolean isAvailable(String dimension, long tree,
                                                   UUID worker, long gameTime) {
        Key key = new Key(dimension, tree);
        Claim current = CLAIMS.get(key);
        if (current == null) {
            return true;
        }
        if (gameTime - current.renewedAt() >= CLAIM_TTL_TICKS) {
            CLAIMS.remove(key);
            return true;
        }
        return current.worker().equals(worker);
    }

    /** Diagnostic owner string, or {@code empty}. */
    public static synchronized String ownerOf(String dimension, long tree, long gameTime) {
        Key key = new Key(dimension, tree);
        Claim current = CLAIMS.get(key);
        if (current == null || gameTime - current.renewedAt() >= CLAIM_TTL_TICKS) {
            if (current != null) {
                CLAIMS.remove(key);
            }
            return "empty";
        }
        return current.worker() + "/" + current.task();
    }

    /** Server stopping: no claim may survive into another server instance. */
    public static synchronized void clearAll() {
        CLAIMS.clear();
    }

    /** Test-only helper kept package-visible for focused tests. */
    static synchronized int size() {
        return CLAIMS.size();
    }
}
