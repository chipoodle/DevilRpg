# Tiempo de amenaza por perfil y diagnóstico del spawn agresivo (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Código:** cambio de `SpawnScaleProfile` (pendiente de commit en este informe)
**Origen:** dos preguntas: (1) el tiempo de encogimiento debe ser por perfil; (2) no aparecían
zombis agresivos a mitad de camino.

---

## 1 · El `13` no era tiempo

`13` es `minHardDistance` en `AggressiveZombieSpawnProfile`: la distancia a la que se reduce la
zona protegida cuando la amenaza llega a 1. El tiempo estaba en `ThreatLevel`, global:

- `devilrpg-server.toml [threat] threatFullHours = 3.0` (horas **jugadas**, con el mundo cargado).
- `ThreatLevel.current` = `clamp(gameTime / (threatFullHours × 72000), 0, 1)`.
- `SpawnScaleProfile.effectiveMinDistance(threat)` = `67 + (13 − 67) × threat`.

Con 3 h por defecto: 0 h → 67; 1 h → 49; 2 h → 31; 3 h → 13. Es lineal, no un salto.

## 2 · Tiempo por perfil (implementado)

`SpawnScaleProfile` tiene ahora un décimo campo:

```java
double threatFullHours // horas jugadas hasta la amenaza máxima de ESTE perfil
```

- `AggressiveZombieSpawnProfile`: `3.0` (último argumento).
- `VexSpawnProfile`: `3.0`.
- `SpawnScaleProfile.threat(Level)` usa ese valor; `<= 0` cae en la config global.
- `ThreatLevel.current(Level, double fullHours)` es la versión por perfil.
- Lo usan `AggressiveZombieSpawnRule`, `VexSpawnRule`, `AggressiveZombieEntity`,
  `FrostVexEntity` y `SculkCultivatorEntity` para **su** zona protegida y escalado.
- La config global `[threat] threatFullHours` sigue mandando en las hordas y el resto del mundo.

Cambiar el tiempo de un mob = editar su perfil (p. ej. `AggressiveZombieSpawnProfile`, tercer
bloque) y recompilar; no hay clave TOML por perfil todavía.

## 3 · Por qué no aparecían zombis agresivos

La regla **sí funciona**. En `run/logs/debug.log`:

| Hora | Distancia al ancla | Probabilidad | Resultado |
|---|---:|---:|---|
| 01:04:09 | 28 | 0.00 | `< 67` (zona protegida) |
| 01:05:24 | 98 | 0.0108 | fallo |
| 01:06:52 | 115 | 0.0166 | fallo |
| 01:12:28 | 358 | 0.0996 | fallo |
| 01:14:11 | 567 | 0.170 | **3 spawneados** (273,592 / 151,548 / 189,503) |

Causa: la probabilidad es **lineal de 67 a 3000** (`maxDistance = 3000`). A 500 bloques del ancla
solo hay ~15–17 % por intento, y el intervalo es 20 s–3 min. Por eso tarda ~10 minutos y a veces
parece que no existe.

Opciones de balance (no aplicadas):
1. bajar `maxDistance` del perfil agresivo (p. ej. 1000) → más probabilidad a media distancia;
2. bajar `minDistance`/`minHardDistance`;
3. dejar la curva como está y esperar (la regla ya está funcionando).

## 4 · Evidencia

- Gate completo: **BUILD SUCCESSFUL** (1m51s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.
- Logs citados en `run/logs/debug.log` (líneas 597–700); no son una corrida nueva.

## 5 · Límites

- El campo `threatFullHours` es un valor de perfil en Java; para configurarlo sin recompilar
  haría falta añadir una sección `[spawn_profiles]` a la config del servidor.
- La probabilidad sigue siendo la curva 67→3000; este cambio **no** aumenta la frecuencia de
  zombis, solo hace que cada perfil tenga su propio tiempo de amenaza.
- No se ejecutó una corrida nueva para medir la tasa de spawn con el tiempo por perfil.
