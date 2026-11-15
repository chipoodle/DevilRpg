# Aggressive Zombie Mobility — Design Proposal

Status: **AUTHORIZED FOR IMPLEMENTATION (2026-11-14)**

> **Authorization record:** The user explicitly authorized implementation of this proposal on
> 2026-11-14, including WALK/JUMP/BREAK/BUILD capability/context separation and vertical mobility
> needed for idx8's unreachable upward BREAK waypoint. This does not authorize arbitrary
> destruction of village defenses, weakening the wall, or changing the approved A2/C4 contracts.
>
> The original proposal text below is preserved as design history. Implementation status and
> limitations are tracked in `A2-VERTICAL-MOBILITY-IMPLEMENTATION.md`.

> **WARNING (historical):** This document originally described a future design direction only.
> Do not implement proposals beyond the recorded authorization scope without explicit user
> authorization. This restriction applies to every AI agent or developer who opens the repository.

## Purpose

Propose a long-term, general mobility design for aggressive zombies. This is not an A2
implementation plan and does not authorize replacing the current navigation architecture.

## Agreed principles

The intended design gives all aggressive zombies general mobility capabilities, not
siege-only abilities:

- **WALK** — ordinary movement.
- **JUMP** — jump obstacles, gaps and terrain differences when physically feasible.
- **BREAK** — destroy permitted obstacles.
- **BUILD** — place blocks to create a climbable route when appropriate.

Future architecture should separate:

1. **Capabilities** — what the entity can physically do.
2. **Context** — where it is and what it is doing.
3. **Action policy** — which actions are allowed in that context and against which blocks.

## Contextual destruction rules

### Outside the village

Aggressive zombies may destroy obstacles that prevent reaching their target, subject to
tactical rules and block permissions. They must not destroy arbitrary terrain without reason.

### During a village siege

They may destroy only blocks belonging to the designated village perimeter wall that are
permitted siege targets. They must not destroy arbitrary terrain outside or inside the wall as
a navigation shortcut.

### Inside the village

They must not destroy blocks. They may walk, jump, navigate around obstacles and use other
permitted mobility capabilities, but must preserve houses, warehouses, farms/crops, job sites,
villager infrastructure and other village structures.

### Construction

Construction is a general mobility capability, not siege-only. A zombie pursuing a target may
use construction when jumping is not safe/feasible, a valid route exists, materials are
available, support/collision requirements are met, and the current context permits the action.

## Capability/context matrix

| Capability | Outside village | Siege | Inside village |
|---|---|---|---|
| Walk | Allowed | Allowed | Allowed |
| Jump | Allowed | Allowed | Allowed |
| Break ordinary obstacles | Contextually allowed | Not allowed unless designated wall block | Forbidden |
| Break perimeter wall | Not applicable | Allowed for designated targets | Forbidden |
| Build to climb | Contextually allowed | Allowed | Contextually allowed |
| Destroy houses/crops/job sites | Forbidden | Forbidden | Forbidden |

Exact block classifications, permissions, jump ranges, construction limits and tactical
priorities require future design and testing. No numerical jump distance is claimed as feasible.

## Future architectural direction

- Separate physical mobility from strategic target selection.
- Let a shared mobility system evaluate walking, jumping, breaking and building.
- Preserve context-sensitive action permissions.
- Avoid competing Goals controlling movement simultaneously.
- Validate full collision bounds, trajectory and landing support for jumps.
- Replan after meaningful terrain changes.
- Keep the wall meaningful; do not allow jumping to bypass the wall or defeat siege mechanics.
- Prefer the least destructive valid movement strategy.
- Use construction when simpler alternatives are unavailable.
- Evaluate against the current architecture before implementation.

## Authorization gate

> This proposal is documentation only. No source-code changes, prototypes, tests intended to
> implement the proposal, architecture migration, class creation, capability integration, or
> replacement of existing navigation behavior may be performed until the user explicitly
> authorizes implementation.
>
> The current A2 investigation must continue using the existing architecture. If the current
> approach fails, the agent must report the evidence and request or await explicit authorization
> before implementing this alternative design.

Continuing A2 is not authorization to implement this proposal.
