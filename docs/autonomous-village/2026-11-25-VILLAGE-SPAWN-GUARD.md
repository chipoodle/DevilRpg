# Campo de fuerza — spawn de hostiles dentro de la aldea (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** partida `New World`, noche del 25-nov-2026; `Dorotea (Minero)` muere
`slain by Zombie` en **1023.41, 63, 964.39**, dentro del radio 62 de la aldea 1 (centro 990,63,990).
**Estado:** corrección estática + gate; **runtime pendiente de que el jugador vuelva a probar**.

---

## 1 · Lo que dice el guardado, no la impresión

`run/saves/New World/data/devilrpg_villages.dat`:

```text
Generated = [0, 1]
Resolved  = [0, 1]
Fallen    = []
Settlement[1] = Center 990,63,990
```

Es decir, **el campo está activo** y la aldea 1 está protegida. El fallo no era “no hay campo”:
era la **puerta de entrada** del campo.

## 2 · Causa

Había dos filtros:

1. `MobSpawnEvent.PositionCheck`: cancelaba si `VillageManager.estaProtegida(pos)`, pero solo para
   spawns que pasan por las reglas de vanilla.
2. `EntityJoinLevelEvent`: usaba `esSpawnQueElSelloCorta(tipo)`, una **lista cerrada**:
   `NATURAL, CHUNK_GENERATION, STRUCTURE, SPAWNER, EVENT, REINFORCEMENT, PATROL, TRIGGERED`.
   Cualquier tipo fuera de esa lista —o un camino que no pase por `PositionCheck`— entraba.
   Además, `VillageSiege` de vanilla no llama a `PositionCheck`; depende del segundo filtro.

El **asedio de zombis de vanilla** y otros caminos podían dejar un zombi dentro; el `expulsar` del
latido esperaba **2 min** (pensado para que la milicia peleara a los asediadores del mod) y el zombi
mataba antes.

## 3 · Corrección

- **`VillageSpawnGuard`**: política inversa y cerrada por el otro lado. Dentro de una aldea protegida
  se cancela **todo** hostil sin excepciones de tipo de spawn:
  - `SPAWN_EGG` (huevo del jugador),
  - `COMMAND` (`/summon`),
  - `MOB_SUMMONED` (asediadores y rebaño del mod).
  El tipo `null` (mecánicas/mod con `addFreshEntity`) se deja pasar, como antes, para no romper
  criaturas legítimas.
- `MobSpawnEvent.PositionCheck` y `EntityJoinLevelEvent` usan la política y **registran**
  `[SpawnGuard] denegado/entrada cancelada ... tipo=...` para poder identificar el camino exacto si
  vuelve a ocurrir.
- **Expulsión de vanilla inmediata:** `VillageManager.expulsarHostilesDeLaAldea` mantiene los 2 min
  para los asediadores del mod, pero a un hostil **vanilla** (namespace `minecraft`) lo rechaza en el
  siguiente latido cuando el campo está activo y **no** hay asedio. Así no mata aldeanos mientras la
  milicia decide.

La regla de no tocar aldeanos-zombi (curación) se conserva.

## 4 · Evidencia

- `VILLAGE_TERRAIN_POLICY_TEST: OK (22 aserciones)`: 11 nuevas de `VillageSpawnGuard`
  (NATURAL/EVENT/REINFORCEMENT/SPAWNER/STRUCTURE/SPAWN_EGG/COMMAND/MOB_SUMMONED/null cancelan;
  fuera/monstruo falso/null no).
- Gate completo: **BUILD SUCCESSFUL** (1m36s); skills **416**, persistencia **553**, ownership **59**.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.

## 5 · Límites

- **No hay una corrida runtime nueva con un zombi entrando**: el arreglo es de política + gate. Para
  cerrar la evidencia hace falta reiniciar la partida, dejar pasar una noche y revisar:
  - `[SpawnGuard]` (si el spawn se intentó dentro),
  - `[Village] ... el sello ha expulsado a ... hostil(es)` (si entró),
  - que no haya `slain by Zombie` de aldeanos dentro.
- Si no aparece ningún `[SpawnGuard]` y aun así hay zombis dentro, el camino es **entrada por un
  portón/brecha**, no spawn; el `expulsar` vanilla inmediato debería sacarlos, pero habría que medir
  el tiempo entre la entrada y la expulsión.
- El cambio de **ancho de brecha** (`ensancharBrecha`) sigue **sin commit** y sin runtime; no se
  mezcla con esta corrección.

## 6 · Veredicto

El campo estaba activo; el defecto era la lista cerrada de tipos y la espera de 2 min para vanilla.
La política nueva cierra cualquier `MobSpawnType` hostil no intencional y expulsa vanilla al
siguiente latido. **Runtime pendiente de la noche de prueba.**


## Actualización 26-nov-2026

El campo de fuerza queda activo desde que la aldea está generada y hasta que `Fallen` sea verdadero.
No depende de `Resolved`, ni del estado de un asedio activo. `Resolved` solo evita iniciar otra
campaña; no apaga la protección. Todo tipo de spawn hostil se cancela dentro, incluidas invocaciones
del mod, huevos y comandos. Los zombies agresivos además descartan cada candidato interior antes de
devolver su posición de spawn.
