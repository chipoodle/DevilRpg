package com.chipoodle.devilrpg.agent;

import java.util.UUID;

public final class NavigationLeaseTest {

    public static void main(String[] args) {
        UUID agent = UUID.randomUUID();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        NavigationLease lease = new NavigationLease(agent, 20L);

        check(lease.acquire(agent, taskA, NavigationPriority.WORK, 100L), "initial acquire");
        check(lease.owns(agent, taskA, 100L), "owner matches");
        check(lease.renew(agent, taskA, 110L), "renew");
        check(lease.currentOwner(110L).orElseThrow().expiresAt() == 130L, "renewed expiry");

        check(!lease.owns(UUID.randomUUID(), taskA, 110L), "other agent rejected");
        lease.release(UUID.randomUUID(), taskA);
        check(lease.owns(agent, taskA, 110L), "stale release ignored");
        lease.release(agent, taskB);
        check(lease.owns(agent, taskA, 110L), "wrong task release ignored");

        check(lease.acquire(agent, taskB, NavigationPriority.EMERGENCY, 115L), "higher priority preempts");
        check(lease.owns(agent, taskB, 115L), "new task owns");
        check(!lease.owns(agent, taskA, 115L), "old task does not own");
        check(!lease.acquire(agent, taskA, NavigationPriority.WORK, 116L), "lower priority denied");
        check(!lease.acquire(agent, UUID.randomUUID(), NavigationPriority.WORK, 116L), "other task denied");

        check(lease.acquire(agent, taskB, NavigationPriority.EMERGENCY, 130L), "same task renew after expiry");
        check(lease.owns(agent, taskB, 130L), "same task owns after renewal");

        lease.invalidate();
        check(lease.currentOwner(130L).isEmpty(), "invalidate clears");
        check(!lease.owns(agent, taskB, 130L), "invalidated task not owner");

        NavigationLease expiring = new NavigationLease(agent, 5L);
        check(expiring.acquire(agent, taskA, NavigationPriority.WORK, 0L), "acquire expiring");
        check(!expiring.owns(agent, taskA, 5L), "expiry at boundary");
        check(expiring.currentOwner(5L).isEmpty(), "expired owner empty");

        System.out.println("NAVIGATION_LEASE_TEST: OK");
    }

    private static void check(boolean condition, String name) {
        if (!condition) {
            throw new AssertionError(name);
        }
    }
}
