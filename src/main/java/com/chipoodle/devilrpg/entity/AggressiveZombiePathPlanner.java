package com.chipoodle.devilrpg.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Planificación acotada de rutas, compartida sin importar qué goal eligió el destino.
 * Los goals escogen el destino; la entidad y sus goals de traversal se ocupan de cambiar bloques y recuperarse.
 */
final class AggressiveZombiePathPlanner {
    private static final int MAX_REMEMBERED_WAYPOINTS = 32;
    private static final long WAYPOINT_REJECTION_TICKS = 200L;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final int STUCK_TICKS = 160;
    private static final int DESTINATION_STUCK_TICKS = 160;
    private static final double DESTINATION_REPLAN_DISTANCE_SQR = 64.0D;
    private static final double MAX_DETOUR_FROM_BEST_BLOCKS = 16.0D;
    private static final double WAYPOINT_ARRIVAL_DISTANCE_SQR = 4.0D;
    private static final double MIN_WAYPOINT_DISTANCE_SQR = 4.0D;
    private static final double DESTINATION_PROGRESS_BLOCKS = 1.0D;
    private static final double MIN_INTERMEDIATE_PROGRESS_BLOCKS = 3.0D;
    private static final double MAX_LOCAL_DETOUR_BLOCKS = 4.0D;
    private static final int[] WAYPOINT_RADII = {48, 32, 16, 8};
    private static final int WAYPOINTS_PER_RING = 8;
    private static final int[] LOCAL_ESCAPE_RADII = {8, 16, 24, 32};

    enum Result {
        MOVING,
        ARRIVED,
        NO_ROUTE,
        STALLED
    }

    private final AggressiveZombieEntity zombie;
    private final PathNavigation navigation;
    private final ArrayDeque<BlockPos> rejectedWaypoints = new ArrayDeque<>();
    private final Map<BlockPos, Long> rejectedAt = new HashMap<>();

    private BlockPos destinationAnchor;
    private BlockPos activeWaypoint;
    private boolean activeRouteIsPartial;
    private double bestDistanceToWaypoint = Double.MAX_VALUE;
    private double bestDistanceToDestination = Double.MAX_VALUE;
    private double maxDestinationDistanceSqr = Double.MAX_VALUE;
    private int stalledTicks;
    private int destinationStalledTicks;
    private boolean trackingDestinationProgress;
    private long retryAt;
    private String lastPlan = "idle";

    AggressiveZombiePathPlanner(AggressiveZombieEntity zombie) {
        this.zombie = zombie;
        this.navigation = zombie.getNavigation();
    }

    Result navigateTo(BlockPos destination, double speed) {
        if (!(zombie.level() instanceof ServerLevel serverLevel) || destination == null) {
            return Result.NO_ROUTE;
        }

        BlockPos requested = destination.immutable();
        if (destinationAnchor == null
                || destinationAnchor.distToCenterSqr(requested.getX() + 0.5D, requested.getY() + 0.5D,
                requested.getZ() + 0.5D) > DESTINATION_REPLAN_DISTANCE_SQR) {
            resetForDestination(requested);
        }

        Result destinationProgress = updateDestinationProgress();
        if (destinationProgress != null) {
            return destinationProgress;
        }

        Result activeResult = updateActiveWaypoint();
        if (activeResult != null) {
            return activeResult;
        }

        long now = zombie.level().getGameTime();
        if (now < retryAt) {
            lastPlan = "retry-delay";
            return Result.NO_ROUTE;
        }

        Path directPath = navigation.createPath(requested, 1);
        BlockPos directTarget = directPath == null ? null : directPath.getTarget();
        if (directPath != null && directPath.canReach()) {
            Result directResult = tryPath(directPath, directTarget, speed);
            if (directResult != null) {
                lastPlan = "direct";
                return directResult;
            }
        }

        // A partial route toward the final destination often ends at the very wall that blocks it. Do not
        // treat that segment as the best fallback until we have looked for a fully reachable intermediate
        // destination that lets the zombie approach the obstruction from a better side.
        Set<BlockPos> candidates = buildCandidates(serverLevel, requested);
        Path waypointPath = null;
        if (!candidates.isEmpty()) {
            waypointPath = navigation.createPath(candidates, 1);
            BlockPos pathTarget = waypointPath == null ? null : waypointPath.getTarget();
            if (waypointPath != null && waypointPath.canReach() && pathTarget != null
                    && candidates.contains(pathTarget) && !isRejected(pathTarget)) {
                Result waypointResult = tryPath(waypointPath, pathTarget, speed);
                if (waypointResult != null) {
                    lastPlan = "intermediate";
                    return waypointResult;
                }
            }
        }

        // A reachable local waypoint is only a detour; it must not hide a partial path that already approaches
        // the obstruction. Follow the partial path to its last validated node, then yield one planner cycle to the
        // physical traversal goals so they can breach, bridge, climb, or dig before asking for another route.
        Path partialPath = choosePartialPath(directPath, waypointPath, null, requested);
        if (partialPath != null) {
            Node end = partialPath.getNode(partialPath.getNodeCount() - 1);
            BlockPos waypoint = end.asBlockPos();
            if (startPath(partialPath, waypoint, speed, true)) {
                lastPlan = partialPath == directPath ? "partial-direct" : "partial-intermediate";
                return Result.MOVING;
            }
        }

        // If neither the destination nor a destination-side staging point is reachable, probe a small local ring.
        // It lets the pathfinder choose an actual walkable detour around a nearby cliff, ravine, or wall instead of
        // repeatedly asking for another route from the same blocked position.
        Set<BlockPos> localCandidates = buildLocalCandidates(serverLevel);
        Path localPath = localCandidates.isEmpty() ? null : navigation.createPath(localCandidates, 1);
        BlockPos localTarget = localPath == null ? null : localPath.getTarget();
        if (localPath != null && localPath.canReach() && localTarget != null
                && localCandidates.contains(localTarget) && !isRejected(localTarget)) {
            Result localResult = tryPath(localPath, localTarget, speed);
            if (localResult != null) {
                lastPlan = "local-escape";
                return localResult;
            }
        }

        retryAt = now + RETRY_DELAY_TICKS;
        lastPlan = "no-route";
        return Result.NO_ROUTE;
    }

    void reset() {
        navigation.stop();
        navigation.resetMaxVisitedNodesMultiplier();
        destinationAnchor = null;
        activeWaypoint = null;
        activeRouteIsPartial = false;
        bestDistanceToWaypoint = Double.MAX_VALUE;
        bestDistanceToDestination = Double.MAX_VALUE;
        maxDestinationDistanceSqr = Double.MAX_VALUE;
        stalledTicks = 0;
        destinationStalledTicks = 0;
        trackingDestinationProgress = false;
        retryAt = 0L;
        rejectedWaypoints.clear();
        rejectedAt.clear();
        lastPlan = "idle";
    }

    /** Stop following a now-stale route after traversal changes the terrain; retain loop-prevention memory. */
    void terrainChanged() {
        navigation.stop();
        activeWaypoint = null;
        activeRouteIsPartial = false;
        bestDistanceToWaypoint = Double.MAX_VALUE;
        stalledTicks = 0;
        destinationStalledTicks = 0;
        trackingDestinationProgress = false;
        retryAt = 0L;
        lastPlan = "terrain-changed";
    }

    /** Small state snapshot consumed by the focused A2 navigation harness. */
    String debugState() {
        long retryIn = Math.max(0L, retryAt - zombie.level().getGameTime());
        return "plan=" + lastPlan + ", waypoint=" + (activeWaypoint == null ? "-" : activeWaypoint.toShortString())
                + ", stalled=" + stalledTicks + ", destinationStalled=" + destinationStalledTicks
                + ", rejected=" + rejectedAt.size() + ", retry=" + retryIn;
    }

    private void resetForDestination(BlockPos destination) {
        navigation.stop();
        destinationAnchor = destination;
        activeWaypoint = null;
        activeRouteIsPartial = false;
        bestDistanceToWaypoint = Double.MAX_VALUE;
        stalledTicks = 0;
        destinationStalledTicks = 0;
        trackingDestinationProgress = false;
        retryAt = 0L;
        bestDistanceToDestination = distanceToDestinationSqr(destination);
        maxDestinationDistanceSqr = maxDistanceFromBestProgressSqr(bestDistanceToDestination);
    }

    /** Devuelve null cuando ya se puede buscar una ruta nueva. */
    private Result updateActiveWaypoint() {
        if (activeWaypoint == null) {
            return null;
        }

        double distance = distanceToWaypointSqr(activeWaypoint);
        if (distance <= WAYPOINT_ARRIVAL_DISTANCE_SQR) {
            remember(activeWaypoint);
            activeWaypoint = null;
            bestDistanceToWaypoint = Double.MAX_VALUE;
            stalledTicks = 0;
            if (activeRouteIsPartial) {
                activeRouteIsPartial = false;
                trackingDestinationProgress = false;
                destinationStalledTicks = 0;
                navigation.stop();
                retryAt = zombie.level().getGameTime() + RETRY_DELAY_TICKS;
                lastPlan = "partial-route-ended";
                return Result.STALLED;
            }
            retryAt = 0L;
            lastPlan = "waypoint-arrived";
            return Result.ARRIVED;
        }

        if (navigation.isDone()) {
            remember(activeWaypoint);
            activeWaypoint = null;
            bestDistanceToWaypoint = Double.MAX_VALUE;
            stalledTicks = 0;
            if (activeRouteIsPartial) {
                activeRouteIsPartial = false;
                trackingDestinationProgress = false;
                destinationStalledTicks = 0;
                retryAt = zombie.level().getGameTime() + RETRY_DELAY_TICKS;
                lastPlan = "partial-route-ended";
                return Result.STALLED;
            }
            lastPlan = "waypoint-ended";
            return null;
        }

        if (distance < bestDistanceToWaypoint - 1.0D) {
            bestDistanceToWaypoint = distance;
            stalledTicks = 0;
        } else if (++stalledTicks >= STUCK_TICKS) {
            remember(activeWaypoint);
            activeWaypoint = null;
            bestDistanceToWaypoint = Double.MAX_VALUE;
            stalledTicks = 0;
            navigation.stop();
            lastPlan = "stalled";
            return Result.STALLED;
        }
        return Result.MOVING;
    }

    /** Reachable waypoints and partial paths can both loop locally; every route must keep progressing to its goal. */
    private Result updateDestinationProgress() {
        if (!trackingDestinationProgress || destinationAnchor == null) {
            return null;
        }
        double distance = Math.sqrt(distanceToDestinationSqr(destinationAnchor));
        if (distance * distance > maxDestinationDistanceSqr
                && (activeWaypoint == null || !withinDestinationEnvelope(activeWaypoint)
                || distanceToWaypointSqr(activeWaypoint, destinationAnchor) >= distance * distance)) {
            rejectActiveWaypoint();
            destinationStalledTicks = 0;
            trackingDestinationProgress = false;
            navigation.stop();
            lastPlan = "detour-limit";
            return Result.STALLED;
        }
        if (distance + DESTINATION_PROGRESS_BLOCKS <= Math.sqrt(bestDistanceToDestination)) {
            bestDistanceToDestination = distance * distance;
            maxDestinationDistanceSqr = maxDistanceFromBestProgressSqr(bestDistanceToDestination);
            destinationStalledTicks = 0;
            return null;
        }
        if (++destinationStalledTicks < DESTINATION_STUCK_TICKS) {
            return null;
        }

        rejectActiveWaypoint();
        stalledTicks = 0;
        destinationStalledTicks = 0;
        trackingDestinationProgress = false;
        navigation.stop();
        lastPlan = "destination-progress-stalled";
        return Result.STALLED;
    }

    private void rejectActiveWaypoint() {
        if (activeWaypoint != null) {
            remember(activeWaypoint);
            activeWaypoint = null;
            activeRouteIsPartial = false;
            bestDistanceToWaypoint = Double.MAX_VALUE;
        }
    }

    /** Sigue la misma Path validada; las rutas parciales solo sirven como puntos intermedios acotados. */
    private Result tryPath(Path path, BlockPos waypoint, double speed) {
        if (path == null || !path.canReach() || waypoint == null || path.getNodeCount() < 2
                || isRejected(waypoint) || !withinDestinationEnvelope(waypoint)) {
            return null;
        }
        if (distanceToWaypointSqr(waypoint) <= MIN_WAYPOINT_DISTANCE_SQR) {
            return null;
        }
        return startPath(path, waypoint, speed, false) ? Result.MOVING : null;
    }

    private Path choosePartialPath(Path directPath, Path waypointPath, Path localPath, BlockPos destination) {
        Path best = preferPartialPath(null, waypointPath, targetOf(waypointPath, destination), destination);
        best = preferPartialPath(best, directPath, destination, destination);
        return preferPartialPath(best, localPath, targetOf(localPath, destination), destination);
    }

    private BlockPos targetOf(Path path, BlockPos fallback) {
        return path == null || path.getTarget() == null ? fallback : path.getTarget();
    }

    /** Rank pathfinder-produced segments toward their actual waypoint, even if they first circle away from the goal. */
    private Path preferPartialPath(Path currentBest, Path candidate, BlockPos segmentTarget, BlockPos destination) {
        if (candidate == null || candidate.canReach() || candidate.getNodeCount() < 2) {
            return currentBest;
        }
        BlockPos endpoint = candidate.getNode(candidate.getNodeCount() - 1).asBlockPos();
        if (isRejected(endpoint) || !withinDestinationEnvelope(endpoint)
                || distanceToWaypointSqr(endpoint) <= MIN_WAYPOINT_DISTANCE_SQR) {
            return currentBest;
        }
        double distanceBefore = distanceToWaypointSqr(segmentTarget);
        double candidateProgress = distanceBefore - distanceToWaypointSqr(endpoint, segmentTarget);
        if (candidateProgress <= 0.0D) {
            return currentBest;
        }
        if (currentBest == null) {
            return candidate;
        }
        BlockPos currentEnd = currentBest.getNode(currentBest.getNodeCount() - 1).asBlockPos();
        BlockPos currentTarget = targetOf(currentBest, destination);
        double currentProgress = distanceToWaypointSqr(currentTarget)
                - distanceToWaypointSqr(currentEnd, currentTarget);
        return candidateProgress > currentProgress ? candidate : currentBest;
    }

    private boolean startPath(Path path, BlockPos waypoint, double speed, boolean partial) {
        if (!navigation.moveTo(path, speed)) {
            remember(waypoint);
            retryAt = zombie.level().getGameTime() + RETRY_DELAY_TICKS;
            return false;
        }
        activeWaypoint = waypoint.immutable();
        activeRouteIsPartial = partial;
        bestDistanceToWaypoint = distanceToWaypointSqr(activeWaypoint);
        stalledTicks = 0;
        retryAt = 0L;
        if (bestDistanceToDestination == Double.MAX_VALUE) {
            bestDistanceToDestination = distanceToDestinationSqr(destinationAnchor);
            maxDestinationDistanceSqr = maxDistanceFromBestProgressSqr(bestDistanceToDestination);
            destinationStalledTicks = 0;
        }
        trackingDestinationProgress = true;
        return true;
    }

    private Set<BlockPos> buildCandidates(ServerLevel level, BlockPos destination) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        double currentDistance = Math.sqrt(distanceToDestinationSqr(destination));
        for (int radius : WAYPOINT_RADII) {
            if (radius >= currentDistance - 2.0D) {
                continue;
            }
            for (int i = 0; i < WAYPOINTS_PER_RING; i++) {
                double angle = Math.PI * 2.0D * i / WAYPOINTS_PER_RING;
                int x = destination.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = destination.getZ() + (int) Math.round(Math.sin(angle) * radius);
                BlockPos column = new BlockPos(x, destination.getY(), z);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                int y = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column).getY();
                BlockPos candidate = new BlockPos(x, y, z);
                double candidateDistance = Math.sqrt(distanceToDestinationSqr(candidate, destination));
                if (!candidate.equals(destination)
                        && distanceToWaypointSqr(candidate) > MIN_WAYPOINT_DISTANCE_SQR
                        && currentDistance - candidateDistance >= MIN_INTERMEDIATE_PROGRESS_BLOCKS
                        && withinDestinationEnvelope(candidate)
                        && !isRejected(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private Set<BlockPos> buildLocalCandidates(ServerLevel level) {
        Set<BlockPos> candidates = new LinkedHashSet<>();
        BlockPos origin = zombie.blockPosition();
        double currentDestinationDistance = Math.sqrt(distanceToDestinationSqr(destinationAnchor));
        for (int radius : LOCAL_ESCAPE_RADII) {
            for (int i = 0; i < WAYPOINTS_PER_RING; i++) {
                double angle = Math.PI * 2.0D * i / WAYPOINTS_PER_RING;
                int x = origin.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int) Math.round(Math.sin(angle) * radius);
                BlockPos column = new BlockPos(x, origin.getY(), z);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                int y = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column).getY();
                BlockPos candidate = new BlockPos(x, y, z);
                // A local route may move sideways briefly to get around a cliff or wall, but its destination
                // distance stays inside a small bound. Otherwise the partial route reaches its endpoint and yields
                // to physical traversal/recovery. Rejected waypoints and the destination watchdog bound retries.
                if (distanceToWaypointSqr(candidate) > MIN_WAYPOINT_DISTANCE_SQR
                        && Math.sqrt(distanceToDestinationSqr(candidate, destinationAnchor))
                        <= currentDestinationDistance + MAX_LOCAL_DETOUR_BLOCKS
                        && withinDestinationEnvelope(candidate)
                        && !isRejected(candidate)) {
                    candidates.add(candidate);
                }
            }
        }
        return candidates;
    }

    private double distanceToWaypointSqr(BlockPos waypoint) {
        return zombie.distanceToSqr(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D);
    }

    private double distanceToWaypointSqr(BlockPos position, BlockPos waypoint) {
        return position.distToCenterSqr(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D);
    }

    private double distanceToDestinationSqr(BlockPos destination) {
        return zombie.distanceToSqr(destination.getX() + 0.5D, destination.getY() + 0.5D,
                destination.getZ() + 0.5D);
    }

    private double distanceToDestinationSqr(BlockPos position, BlockPos destination) {
        return position.distToCenterSqr(destination.getX() + 0.5D, destination.getY() + 0.5D,
                destination.getZ() + 0.5D);
    }

    private boolean withinDestinationEnvelope(BlockPos position) {
        return destinationAnchor != null
                && position.distToCenterSqr(destinationAnchor.getX() + 0.5D, destinationAnchor.getY() + 0.5D,
                destinationAnchor.getZ() + 0.5D) <= maxDestinationDistanceSqr;
    }

    private double maxDistanceFromBestProgressSqr(double distanceSqr) {
        double maxDistance = Math.sqrt(distanceSqr) + MAX_DETOUR_FROM_BEST_BLOCKS;
        return maxDistance * maxDistance;
    }

    private boolean isRejected(BlockPos waypoint) {
        Long rejectedTime = rejectedAt.get(waypoint);
        if (rejectedTime == null) {
            return false;
        }
        if (zombie.level().getGameTime() - rejectedTime <= WAYPOINT_REJECTION_TICKS) {
            return true;
        }
        rejectedAt.remove(waypoint);
        rejectedWaypoints.remove(waypoint);
        return false;
    }

    private void remember(BlockPos waypoint) {
        rejectedWaypoints.remove(waypoint);
        rejectedWaypoints.addLast(waypoint.immutable());
        rejectedAt.put(waypoint.immutable(), zombie.level().getGameTime());
        while (rejectedWaypoints.size() > MAX_REMEMBERED_WAYPOINTS) {
            BlockPos expired = rejectedWaypoints.removeFirst();
            rejectedAt.remove(expired);
        }
    }
}
