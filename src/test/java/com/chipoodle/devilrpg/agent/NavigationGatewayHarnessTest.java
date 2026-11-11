package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.agent.ForestryNavigationGateway.Mode;
import com.chipoodle.devilrpg.agent.ForestryNavigationGateway.Result;

import java.util.UUID;

/** Exercises the actual gateway core with an observable fake delegate. No world required. */
public final class NavigationGatewayHarnessTest {

    public static void main(String[] args) {
        UUID agent = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID taskA = UUID.randomUUID();
        UUID taskB = UUID.randomUUID();
        Mode en = Mode.ENFORCE;
        Mode off = Mode.OFF;
        Mode audit = Mode.AUDIT;

        NavigationLease lease = new NavigationLease(agent, 20L);
        check(lease.acquire(agent, taskA, NavigationPriority.WORK, 0L), "lease A");
        Counter c = new Counter();
        check(ForestryNavigationGateway.moveToCore(en, agent, lease, taskA, 1L, c)
                == Result.AUTHORIZED && c.calls == 1, "A1 authorized move");

        NavigationLease empty = new NavigationLease(agent, 20L);
        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(en, agent, empty, taskA, 1L, c)
                == Result.DENIED_EXPIRED && c.calls == 0, "A2 missing lease");

        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(en, agent, lease, taskB, 1L, c)
                == Result.DENIED_WRONG_TASK && c.calls == 0, "A3 wrong task");
        check(lease.owns(agent, taskA, 1L), "A3 lease retained");

        NavigationLease expired = new NavigationLease(agent, 5L);
        check(expired.acquire(agent, taskA, NavigationPriority.WORK, 0L), "expiring lease");
        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(en, agent, expired, taskA, 5L, c)
                == Result.DENIED_EXPIRED && c.calls == 0, "A4 expired");

        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(en, other, lease, taskA, 1L, c)
                == Result.DENIED_WRONG_TASK && c.calls == 0, "A5 wrong agent");

        NavigationLease preempt = new NavigationLease(agent, 20L);
        check(preempt.acquire(agent, taskA, NavigationPriority.WORK, 0L), "A preempt");
        check(preempt.acquire(agent, taskB, NavigationPriority.EMERGENCY, 1L), "B preempts A");
        c = new Counter();
        check(ForestryNavigationGateway.stopCore(en, agent, preempt, taskA, taskA, 2L, c)
                == Result.DENIED_WRONG_TASK && c.calls == 0, "A6 stale stop");
        check(ForestryNavigationGateway.stopCore(en, agent, preempt, taskB, taskB, 2L, c)
                == Result.AUTHORIZED && c.calls == 1, "A6 current stop");

        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(off, agent, empty, taskA, 1L, c)
                == Result.OFF_DELEGATED && c.calls == 1, "A7 OFF");

        c = new Counter();
        check(ForestryNavigationGateway.moveToCore(audit, agent, empty, taskA, 1L, c)
                == Result.UNAUTHORIZED_AUDITED && c.calls == 1, "A8 AUDIT");

        c = new Counter();
        check(ForestryNavigationGateway.stopCore(en, agent, empty, null, null, 1L, c)
                == Result.CLEANUP_ALLOWED && c.calls == 1, "ENFORCE unattributed cleanup");

        System.out.println("NAVIGATION_GATEWAY_HARNESS_TEST: OK");
    }

    private static final class Counter implements NavigationDelegate {
        int calls;

        @Override
        public void run() {
            calls++;
        }
    }

    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
