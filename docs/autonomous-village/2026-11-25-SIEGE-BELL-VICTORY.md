# Asedio — la campana destruida debe conquistar la aldea (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** partida `New World`; los asaltantes rompieron la campana y la aldea siguió viva.
**Evidencia del guardado:** `Occupied [0,1]`, `Resolved [0,1]`, `Fallen []`.

---

## 1 · Causa

`AggressiveZombieEntity.tickObjetivoCampana` ya hacía lo correcto cuando un asaltante llegaba a la
campana y la destruía: marcaba `VillageSavedData.markOccupied(index)` y ponía al zombi en ocupación.

Pero **`VillageManager.tick` no consultaba `Occupied` al resolver**:

- `waveCleared` (todas las bajas confirmadas) → **aldea salvada + recompensa**;
- `timeout` con `ALGUNO_FUERA` → **aldea salvada**;
- `sinConfirmar` → **aldea salvada sin recompensa**;
- solo el resto → `fallVillage`.

Por eso una aldea con la campana rota podía declararse salvada si los asaltantes morían después. La
horda del mundo tenía el mismo hueco: `WorldSiegePolicy` no mira `Occupied`.

## 2 · Corrección

- **`VillageSiegeBellPolicy.laAldeaCaePorCampana(campanaDestruida, aldeaYaCaida)`**: campana destruida
  y aldea aún viva → **cae**.
- **Asedio clásico:** en `tick`, si `Occupied`, se resuelve **en ese tick** como derrota:
  `fallVillage`, mensaje al jugador, avance de objetivo y retirada de la defensa. No se espera a que
  muera la ola ni se paga recompensa.
- **Horda del mundo:** mismo chequeo al principio del bucle; se retira el registro, cae la aldea y
  avanza el objetivo.
- **Reparación de partidas viejas:** cada 200 ticks, `reconciliarAldeasOcupadas` corrige
  `Occupied && !Fallen` a caída, con log explícito. Esto afecta a la partida actual:
  **`Occupied [0,1]` → ambas aldeas pasan a caídas**; es la consecuencia de la condición antigua
  (campana rota + resuelta como salvada).
- La aldea sigue su camino normal de `fallVillage`: `Fallen`, `Resolved`, `ruin` y sin más asedios.

## 3 · Evidencia

- `VILLAGE_TERRAIN_POLICY_TEST: OK (25 aserciones)`: 11 de `VillageSpawnGuard`, 3 nuevas de
  `VillageSiegeBellPolicy` (campana rota+viva cae; rota+caída no repite; sin campana no cae) y las 11
  anteriores.
- Gate completo: **BUILD SUCCESSFUL** (1m30s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.

## 4 · Límites

- **No hay una corrida runtime nueva**: el arreglo es de resolución + política. Falta reiniciar,
  comprobar que `Occupied` resuelve como caída y que no se paga recompensa.
- La reparación de la partida actual **arrasa las dos aldeas marcadas `Occupied`**. Si no se quiere
  conservar ese efecto, hay que restaurar una copia del mundo anterior; el código no puede
  distinguir “campana rota por asedio” de “aldea que debía seguir viva” porque el guardado la marcó
  como salvada.
- El candidato de ancho de brecha sigue **sin commit** y sin runtime.

## 5 · Veredicto

La campana es la condición de victoria: destruida, la aldea cae en el siguiente tick y el asedio no
puede volver a declararse salvado. **Runtime pendiente de reinicio.**
