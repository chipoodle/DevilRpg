package com.chipoodle.devilrpg.world;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IMPLEMENT-08 · ephemeral breach-target claims for one siege. Not persisted.
 * Claims are acquired only immediately before the real destruction attempt.
 */
public final class RaidTacticalBoard {

    public static final long CLAIM_TICKS = 100L;
    public static final int MAX_CLAIMS = 64;

    public record Claim(UUID owner, int objectiveIndex, long target, long claimedAt, long expiresAt) {
    }

    private final Map<Long, Claim> claims = new HashMap<>();

    public boolean tryClaim(final UUID owner, final int objectiveIndex, final long target, final long now) {
        if (owner == null) {
            return false;
        }
        claims.values().removeIf(c -> now >= c.expiresAt());
        Claim existing = claims.get(target);
        if (existing != null) {
            if (existing.owner().equals(owner)) {
                return true;
            }
            return false; // active lease of another raider: no duplicate break target
        }
        if (claims.size() >= MAX_CLAIMS) {
            return false;
        }
        claims.put(target, new Claim(owner, objectiveIndex, target, now, now + CLAIM_TICKS));
        return true;
    }

    public boolean isClaimedByOther(final UUID owner, final long target, final long now) {
        Claim c = claims.get(target);
        return c != null && now < c.expiresAt() && !c.owner().equals(owner);
    }

    public void release(final long target) {
        claims.remove(target);
    }

    public void releaseOwner(final UUID owner) {
        claims.values().removeIf(c -> c.owner().equals(owner));
    }

    public void clear() {
        claims.clear();
    }

    public int size() {
        return claims.size();
    }

    public Claim claim(final long target) {
        return claims.get(target);
    }
}
