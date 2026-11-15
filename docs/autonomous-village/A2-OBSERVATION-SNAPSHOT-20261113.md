# A2 Observation Snapshot â€” 2026-11-13

Status: observation-only world; not an acceptance run.

- Source run: a2n-20261113-181340
- Source world: build/a2n/a2n-20261113-181340/game/a2n
- Playable copy: run/saves/A2-Atascos-20261113-181340
- Snapshot captured at phase B boundary before C4 attacker cleanup.
- fin.txt: OBSERVATION_SNAPSHOT (raid) - phaseB=6000 saved before C4 cleanup; not an A2 acceptance result
- The playable world contains the actual saved terrain and persisted aggressive zombie entities.
- run/saves/New World was not accessed or modified.
- The original build/a2n run remains unchanged.

Open the world named **A2 - Diagnostico de zombies atascados** from the development client's Singleplayer menu. It starts in Creative/flying above the village centre. Use /tp @s 612 108 593 to inspect idx3 or /tp @s 590 96 510 for idx4.

See run/saves/A2-Atascos-20261113-181340/A2-OBSERVE.txt and RAID-NAV-SNAPSHOT.txt for entity details.

Important limitation: AggressiveZombieEntity does not serialize the transient NavExecutor state/route. Reloading reconstructs navigation from the saved position; this is a reproducible observation starting point, not a frozen in-memory FAILED state.

No production source or mobility redesign was changed. The observation hook was added only to the ignored temporary debug harness and restored after the snapshot.