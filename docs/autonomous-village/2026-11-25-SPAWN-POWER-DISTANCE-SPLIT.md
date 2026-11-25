# Spawn — probabilidad y poder con distancias máximas independientes (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** `maxDistance` servía a la vez para la probabilidad de spawn y para el escalado de
atributos/XP. Ahora son dos parámetros.

---

## 1 · `SpawnScaleProfile`

Antes:

```java
int maxDistance; // probabilidad 1 Y escala 1
```

Ahora:

```java
int maxSpawnDistance; // probabilidad de spawn = 1
int maxPowerDistance; // atributos/XP = 1
```

Métodos separados:

- `normalizeSpawn(distance, threat)` → probabilidad.
- `normalizePower(distance, threat)` → escalado.
- `probability(...)` usa **maxSpawnDistance**.
- `scaleFactor(...)` y `experienceReward(...)` usan **maxPowerDistance**.

No se puede confundir una con otra: cambiar la distancia de spawn ya no cambia la curva de poder.

## 2 · Valores actuales

Para no alterar el balance sin pedirlo:

| Perfil | maxSpawnDistance | maxPowerDistance |
|---|---:|---:|
| `AggressiveZombieSpawnProfile` | 3000 | 3000 |
| `VexSpawnProfile` | 3000 | 3000 |

A partir de aquí se pueden ajustar por separado. Por ejemplo, bajar solo `maxSpawnDistance` hace que
aparezcan antes sin volver antes a los mobs fuertes.

## 3 · Consumidores actualizados

- `AggressiveZombieSpawnRule`: probabilidad con `maxSpawnDistance`.
- `AggressiveZombieEntity` / `SculkCultivatorEntity`: búsqueda del jugador más cercano con
  `maxPowerDistance` (es la distancia que importa para el escalado).
- `HordeManager`: la curva de horda usa `probability`, que ahora lee `maxSpawnDistance`.
- `VexSpawnRule`: igual.

## 4 · Cambio previo del usuario

Se commiteó tu ajuste de `minDistance` **67 → 40** en
`AggressiveZombieSpawnProfile` (commit `21fea398`), separado de este cambio.

## 5 · Evidencia

- `VILLAGE_TERRAIN_POLICY_TEST: OK (28 aserciones)`; 3 nuevas:
  - probabilidad 1 a `maxSpawnDistance`;
  - poder por debajo de 1 a esa misma distancia;
  - poder 1 a `maxPowerDistance`.
- Gate completo: **BUILD SUCCESSFUL** (1m26s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.

## 6 · Límites

- Los valores siguen siendo **3000/3000**; este cambio separa los ejes, no altera el balance.
- El candidato de **ancho de brecha** sigue sin commit.
- Requiere reiniciar para que el juego use el código nuevo.
