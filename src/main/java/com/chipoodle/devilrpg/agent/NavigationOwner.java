package com.chipoodle.devilrpg.agent;

import java.util.UUID;

/** Immutable snapshot of the DevilRpg-side navigation owner. */
public record NavigationOwner(
        UUID agentId,
        UUID taskId,
        NavigationPriority priority,
        long acquiredAt,
        long renewedAt,
        long expiresAt) {
}
