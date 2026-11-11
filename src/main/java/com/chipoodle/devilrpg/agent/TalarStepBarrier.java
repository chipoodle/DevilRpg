package com.chipoodle.devilrpg.agent;

import com.chipoodle.devilrpg.DevilRpg;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/**
 * <b>TEST-ONLY synchronization barrier at the TALAR Step-consumption / navigation-authorization
 * boundary.</b>
 *
 * <p>It is disabled by default ({@code devilrpg.test.talarBarrier} absent). A disposable fixture
 * must set {@code devilrpg.test.talarBarrier=true} and
 * {@code devilrpg.test.talarBarrierWorker=<villager UUID>} before the Goal selects a target.</p>
 *
 * <p>Where it sits: after the Goal has received the {@link TalarStepPlanner.Step} from
 * {@code nextTalarStep()} and before {@code syncNavigationLease}/{@code navegar} run. The Goal's
 * own tick returns while paused; the server keeps ticking. The fixture removes the target
 * through the world and calls {@link #release(long)}. On the next Goal tick the already selected
 * Step is consumed again (held here, never re-planned and never rewritten) and the normal
 * lifecycle continues through the real authorization path.</p>
 *
 * <p>It never acquires, renews, releases or invalidates a lease, never stops or replaces a path,
 * never touches a planner cache, and never changes the Goal's target or phase. It only holds the
 * selected Step and the pause/release state for one explicitly named worker.</p>
 */
public final class TalarStepBarrier {

    public static final String ENABLED_PROPERTY = "devilrpg.test.talarBarrier";
    public static final String WORKER_PROPERTY = "devilrpg.test.talarBarrierWorker";

    public enum State {
        IDLE,
        PAUSED,
        RELEASED,
        CONSUMED
    }

    private static final Object LOCK = new Object();

    private static State state = State.IDLE;
    private static UUID worker;
    private static BlockPos target;
    private static BlockPos destination;
    private static boolean standingCell;
    private static boolean wallGate;
    private static long selectedTick = -1L;
    private static long releasedTick = -1L;
    private static long resumedTick = -1L;

    private TalarStepBarrier() {
    }

    /** Enables the barrier for exactly one disposable fixture worker and clears old state. */
    public static void arm(UUID workerId) {
        synchronized (LOCK) {
            resetLocked();
            worker = workerId;
            System.setProperty(ENABLED_PROPERTY, "true");
            System.setProperty(WORKER_PROPERTY, workerId.toString());
        }
        DevilRpg.LOGGER.info("[C1-BARRIER] event=ARMED worker={}", workerId);
    }

    /** Disarms the barrier (no property -> every hook below is inert). */
    public static void reset() {
        synchronized (LOCK) {
            resetLocked();
            System.clearProperty(ENABLED_PROPERTY);
            System.clearProperty(WORKER_PROPERTY);
        }
    }

    private static void resetLocked() {
        state = State.IDLE;
        worker = null;
        target = null;
        destination = null;
        standingCell = false;
        wallGate = false;
        selectedTick = -1L;
        releasedTick = -1L;
        resumedTick = -1L;
    }

    /** Default-off gate. Both properties must name this worker. */
    public static boolean enabledFor(UUID workerId) {
        if (workerId == null || !Boolean.getBoolean(ENABLED_PROPERTY)) {
            return false;
        }
        String configured = System.getProperty(WORKER_PROPERTY, "");
        return !configured.isBlank() && configured.equalsIgnoreCase(workerId.toString());
    }

    /**
     * Called by the Goal immediately after it received a TALAR Step (before authorization).
     * While IDLE it records the selected Step without logging; the pause event is emitted by
     * {@link #pauseAfterStepSelected}. After the first selection it is a no-op.
     */
    public static void observeStep(UUID workerId, BlockPos targetPos, TalarStepPlanner.Step step,
                                   BlockPos stepDestination, long tick) {
        if (step == null || stepDestination == null || targetPos == null) {
            return;
        }
        if (!enabledFor(workerId)) {
            return;
        }
        synchronized (LOCK) {
            if (state != State.IDLE) {
                return;
            }
            if (worker == null || !worker.equals(workerId)) {
                return;
            }
            target = targetPos.immutable();
            destination = stepDestination.immutable();
            standingCell = step.standingCell();
            wallGate = step.wallGate();
        }
    }

    /**
     * Boundary hook: returns {@code true} while the Goal must not progress (no lease sync, no
     * navigation command). Returns {@code false} on the resumed tick, when the held Step must be
     * handed to the real authorization path.
     */
    public static boolean pauseAfterStepSelected(UUID workerId, BlockPos targetPos,
                                                 BlockPos stepDestination, long tick) {
        if (!enabledFor(workerId)) {
            return false;
        }
        synchronized (LOCK) {
            if (worker == null || !worker.equals(workerId)) {
                return false;
            }
            if (state == State.IDLE) {
                if (target == null || destination == null || targetPos == null
                        || !target.equals(targetPos) || stepDestination == null
                        || !destination.equals(stepDestination)) {
                    return false;
                }
                state = State.PAUSED;
                selectedTick = tick;
                DevilRpg.LOGGER.info("[C1-BARRIER] event=STEP-SELECTED worker={} target={} step={}"
                                + " standing={} gate={} tick={} navAuthorized=false",
                        workerId, target.toShortString(), destination.toShortString(),
                        standingCell, wallGate, tick);
                return true;
            }
            if (state == State.PAUSED) {
                return true;
            }
            if (state == State.RELEASED) {
                state = State.CONSUMED;
                resumedTick = tick;
                DevilRpg.LOGGER.info("[C1-BARRIER] event=STEP-RESUMED worker={} target={} step={}"
                                + " tick={} navAuthorized=false->lifecycle",
                        workerId, target == null ? "-" : target.toShortString(),
                        destination == null ? "-" : destination.toShortString(), tick);
                return false;
            }
            return false;
        }
    }

    /**
     * The Goal uses this instead of re-planning while the selected Step is held, so resumption
     * continues the same Step consumption instead of silently recomputing a different one.
     */
    public static BlockPos heldDestination(UUID workerId, BlockPos targetPos) {
        if (!enabledFor(workerId) || targetPos == null) {
            return null;
        }
        synchronized (LOCK) {
            if ((state == State.PAUSED || state == State.RELEASED)
                    && worker != null && worker.equals(workerId)
                    && target != null && target.equals(targetPos) && destination != null) {
                return destination;
            }
            return null;
        }
    }

    /** Fixture-side release: the Goal's next tick resumes at the boundary. */
    public static boolean release(long tick) {
        synchronized (LOCK) {
            if (state != State.PAUSED) {
                return false;
            }
            state = State.RELEASED;
            releasedTick = tick;
            DevilRpg.LOGGER.info("[C1-BARRIER] event=RELEASED worker={} target={} step={} tick={}"
                            + " navAuthorized=false",
                    worker, target == null ? "-" : target.toShortString(),
                    destination == null ? "-" : destination.toShortString(), tick);
            return true;
        }
    }

    public static State state() {
        synchronized (LOCK) {
            return state;
        }
    }

    public static BlockPos selectedTarget() {
        synchronized (LOCK) {
            return target;
        }
    }

    public static BlockPos selectedStep() {
        synchronized (LOCK) {
            return destination;
        }
    }

    public static boolean selectedStandingCell() {
        synchronized (LOCK) {
            return standingCell;
        }
    }

    public static boolean selectedWallGate() {
        synchronized (LOCK) {
            return wallGate;
        }
    }

    public static long selectedTick() {
        synchronized (LOCK) {
            return selectedTick;
        }
    }

    public static long releasedTick() {
        synchronized (LOCK) {
            return releasedTick;
        }
    }

    public static long resumedTick() {
        synchronized (LOCK) {
            return resumedTick;
        }
    }
}
