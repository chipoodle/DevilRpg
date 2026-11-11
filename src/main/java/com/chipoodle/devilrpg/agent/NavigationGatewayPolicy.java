package com.chipoodle.devilrpg.agent;

/** Pure decision core of {@link ForestryNavigationGateway}; testable without a world. */
public final class NavigationGatewayPolicy {

    public enum Action {
        DELEGATE,
        CLEANUP,
        DENY
    }

    private NavigationGatewayPolicy() {
    }

    public static Action movement(ForestryNavigationGateway.Mode mode, boolean leaseOwns) {
        if (mode == ForestryNavigationGateway.Mode.OFF) return Action.DELEGATE;
        if (leaseOwns) return Action.DELEGATE;
        return mode == ForestryNavigationGateway.Mode.AUDIT ? Action.DELEGATE : Action.DENY;
    }

    public static Action stop(ForestryNavigationGateway.Mode mode, boolean leaseOwns,
                              boolean otherOwner, boolean hasAttribution, boolean attributionMatches) {
        if (mode == ForestryNavigationGateway.Mode.OFF) return Action.DELEGATE;
        if (leaseOwns) return Action.DELEGATE;
        if (otherOwner) return Action.DENY;
        if (!hasAttribution || attributionMatches) return Action.CLEANUP;
        return mode == ForestryNavigationGateway.Mode.AUDIT ? Action.DELEGATE : Action.DENY;
    }
}
