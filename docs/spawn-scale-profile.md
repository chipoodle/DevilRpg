# SpawnScaleProfile — Entidades con spawnRule y atributos dinámicos

Patrón del mod **DevilRpg** para entidades que se generan con una `SpawnRule` propia y cuyos atributos
crecen de **"débil a fuerte"** según la distancia del jugador a su punto de inicio (spawn point).

> Código fuente: `src/main/java/com/chipoodle/devilrpg/spawnprofile/`

---

## 1) ¿Qué problema resuelve?

Antes, los límites de distancia que definen la dificultad estaban **duplicados**:

- En la **entidad** (`AggressiveZombieEntity`): `MIN_DISTANCE = 200` / `MAX_DISTANCE = 1500`, usados para
  la curva de spawn **y** el escalado de atributos.
- En la **spawnRule** (`AggressiveZombieSpawnRule`): `MIN_PLAYER_DISTANCE = 200` / `MAX_PLAYER_DISTANCE = 1500`,
  usados para la misma curva de probabilidad.

Eso significaba que cambiar un valor exigía tocar **dos** sitios, y la curva de probabilidad se calculaba
dos veces (posiblemente distinta).

## 2) La solución: un perfil neutro compartido

Se crea un **objeto de configuración neutro** (`SpawnScaleProfile`, un `record`) que concentra:

- Los **límites de distancia** (`minDistance`, `maxDistance`).
- La **base/escala de atributos** (`baseHealth`, `baseSpeed`, `baseDamage`, `maxScaleMultiplier`).
- Los **métodos** que derivan las curvas: `probability()`, `normalize()` y `scaleFactor()`.

Lo comparten **la entidad** (para aplicar el escalado) y **su spawnRule** (para la probabilidad de spawn),
de modo que:

- Hay **un único origen de verdad** (un solo número de `minDistance`, un solo `maxDistance`, etc.).
- **No hay acoplamiento**: la entidad **no depende** de la spawnRule, ni la spawnRule de los internos de la
  entidad. Ambas dependen solo del perfil (un `record` puro, sin lógica de entidad).

```
        ┌──────────────────────────┐
        │  SpawnScaleProfile (record) │  ← origen único de verdad
        └────────────┬─────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                        ▼
   AggressiveZombieEntity     AggressiveZombieSpawnRule
   (aplica atributos)          (calcula probabilidad de spawn)
```

## 3) Componentes

| Archivo | Rol |
|---|---|
| `SpawnScaleProfile.java` | Record genérico + métodos (`probability`, `normalize`, `scaleFactor`). **El patrón reutilizable.** |
| `AggressiveZombieSpawnProfile.java` | Clase neutra que expone la `INSTANCE` concreta del zombie. **Solo la config de esa entidad.** |
| `AggressiveZombieEntity.java` | Usa el perfil para acotar la búsqueda del jugador y escalar vida/velocidad/daño. |
| `AggressiveZombieSpawnRule.java` | Usa el perfil para la probabilidad de spawn y los logs. |

### Métodos de `SpawnScaleProfile`

```java
SpawnScaleProfile p = new SpawnScaleProfile(200, 1500, 1.3, 20.0, 0.2, 3.25);

p.probability(distance); // 0..1 → probabilidad de spawn (0 bajo min, 1 sobre max, lineal entre medio)
p.normalize(distance);   // 0..1 → (distance-min)/(max-min), satura en los bordes
p.scaleFactor(distance); // 1.0 + normalize*distancia*maxScaleMultiplier → multiplicador de atributos
```

## 4) Cómo implementarlo para una **entidad nueva**

Ejemplo: queremos un **"Bandido"** que spawnea con su propia regla y escala con la distancia.

### Paso 1 — Crea su instancia del perfil

Crea una clase neutra (o una constante en la entidad) que guarde **una** `SpawnScaleProfile`:

```java
package com.chipoodle.devilrpg.spawnprofile;

public final class BanditSpawnProfile {
    public static final SpawnScaleProfile INSTANCE = new SpawnScaleProfile(
            300,   // minDistance: no spawnea dentro de 300 bloques
            2000,  // maxDistance: probabilidad 1 a partir de 2000 bloques
            1.5,   // maxScaleMultiplier: hasta +50% de atributos
            24.0,  // baseHealth
            0.25,  // baseSpeed
            4.0    // baseDamage
    );
    private BanditSpawnProfile() {}
}
```

> Alternativa sin clase extra: declara `public static final SpawnScaleProfile SPAWN_PROFILE = ...` en la
> entidad y lee ese valor desde la spawnRule (spawnRule → entidad, una dirección natural).

### Paso 2 — En la entidad, aplica el escalado

```java
private static final SpawnScaleProfile PROFILE = BanditSpawnProfile.INSTANCE;

// Guarda la distancia al spawn al crearse (en setPos / al spawnear).
private double spawnDistance = 0;

@Override
public void aiStep() {
    super.aiStep();
    if (!attributesAdjusted) {
        adjustAttributesBasedOnSpawnDistance();
        attributesAdjusted = true;
    }
}

private void adjustAttributesBasedOnSpawnDistance() {
    if (spawnDistance < PROFILE.minDistance()) return;  // zona protegida → no escala
    double factor = PROFILE.scaleFactor(spawnDistance);
    this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(PROFILE.baseHealth() * factor);
    this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(PROFILE.baseSpeed() * factor);
    this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(PROFILE.baseDamage() * factor);
}
```

### Paso 3 — En la spawnRule, usa la probabilidad del perfil

```java
private static final SpawnScaleProfile PROFILE = BanditSpawnProfile.INSTANCE;

@Override
public float getSpawnChance(ServerLevel level, ServerPlayer player) {
    Vec3 spawnPoint = getSpawnPoint(player);
    if (spawnPoint == null) return 0.0F;
    double distance = Math.sqrt(player.distanceToSqr(spawnPoint));
    if (distance < PROFILE.minDistance()) return 0.0F;   // zona protegida
    if (distance > PROFILE.maxDistance()) return 1.0F;   // máximo
    return (float) PROFILE.probability(distance);        // 0..1
}
```

## 5) Consideraciones

- **Consistencia garantizada**: la curva de probabilidad (`probability`) y la de escalado (`scaleFactor`)
  usan la misma `normalize`, así que la entidad y su regla nunca divergen.
- **Un solo `SpawnScaleProfile` por entidad** (una sola instancia compartida). No lo dupliques en la
  entidad y la regla.
- El record es **neutro**: no importa entidades, spawnRules ni capacidades, para no crear dependencias
  circulares. Vive en su propio paquete y puede ser usado por cualquier paquete (entidad, spawner, etc.) sin
  acoplarse.

## 6) Referencia rápida de valores de `AggressiveZombieSpawnProfile`

| Parámetro | Valor | Significado |
|---|---|---|
| `minDistance` | 200 | No spawnea dentro de los primeros 200 bloques (zona protegida). |
| `maxDistance` | 1500 | Probabilidad 1 a partir de 1500 bloques. |
| `maxScaleMultiplier` | 1.3 | Los atributos suben hasta +30% en la distancia máxima. |
| `baseHealth` | 20.0 | Vida base. |
| `baseSpeed` | 0.2 | Velocidad base. |
| `baseDamage` | 3.25 | Daño base. |
