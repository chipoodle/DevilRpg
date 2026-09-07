# SpawnScaleProfile — Entidades con spawnRule y atributos dinámicos

Patrón del mod **DevilRpg** para entidades que se generan con una `SpawnRule` propia y cuyos atributos
crecen de **"débil a fuerte"** según la distancia del jugador a su punto de inicio (spawn point).

> Código fuente: `src/main/java/com/chipoodle/devilrpg/spawner/` y `src/main/java/com/chipoodle/devilrpg/spawnprofile/`

---

## 1) ¿Qué problema resuelve?

Antes, los límites de distancia que definen la dificultad estaban **duplicados**:

- En la **entidad** (`AggressiveZombieEntity`): `MIN_DISTANCE = 200` / `MAX_DISTANCE = 1500`, usados para
  la curva de spawn **y** el escalado de atributos.
- En la **spawnRule** (`AggressiveZombieSpawnRule`): `MIN_PLAYER_DISTANCE = 200` / `MAX_PLAYER_DISTANCE = 1500`,
  usados para la misma curva de probabilidad.

Eso obligaba a tocar **dos** sitios al cambiar un valor, y la curva de probabilidad se calculaba dos veces.

## 2) La solución: un perfil neutro compartido

Se crea un **objeto de configuración neutro** (`SpawnScaleProfile`, un `record`) que concentra:

- Los **límites de distancia** (`minDistance`, `maxDistance`).
- La **base/escala de atributos** (`baseHealth`, `baseSpeed`, `baseDamage`, `maxScaleMultiplier`).
- Los **métodos** que derivan las curvas: `probability()`, `normalize()` y `scaleFactor()`.

Lo comparten **la entidad** (para aplicar el escalado) y **su spawnRule** (para la probabilidad de spawn):

```
        ┌──────────────────────────────┐
        │  SpawnScaleProfile (record)  │  ← origen único de verdad
        └──────────────┬───────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼                            ▼
   AggressiveZombieEntity          AggressiveZombieSpawnRule
   (escale atributos)               (probabilidad de spawn)
```

**Sin acoplamiento**: la entidad no depende de la spawnRule, ni la spawnRule de los internos de la
entidad. Ambas dependen solo del perfil (un `record` puro).

## 3) Componentes

| Archivo | Rol |
|---|---|
| `SpawnScaleProfile.java` | Record genérico + métodos (`probability`, `normalize`, `scaleFactor`). **El patrón reutilizable.** |
| `AggressiveZombieSpawnProfile.java` | Clase neutra que expone la `INSTANCE` concreta del zombie. |
| `AggressiveZombieEntity.java` | Entidad que se configura sola (captura distancia y escale atributos). |
| `AggressiveZombieSpawnRule.java` | `CustomSpawnRule` que registra la probabilidad/spawn. |
| `CustomSpawner.java` | Spawneador por tick del servidor (evalúa las reglas). |
| `CustomSpawnerTickHandler.java` | Hookea el spawner al tick y **registra las reglas** una vez por dimensión. |

### Métodos de `SpawnScaleProfile`

```java
SpawnScaleProfile p = new SpawnScaleProfile(200, 1500, 50, 1.5, 20.0, 0.2, 3.25, 20, 4.0);
// campos: minDistance, maxDistance, minHardDistance, maxScaleMultiplier,
//         baseHealth, baseSpeed, baseDamage, baseXp, maxXpMultiplier

p.probability(distance, threat); // 0..1 -> probabilidad de spawn (0 bajo el minimo EFECTIVO, 1 sobre max, lineal)
p.normalize(distance, threat);   // 0..1 -> (distance-minEfectivo)/(max-minEfectivo), satura en los bordes
p.scaleFactor(distance, threat); // 1.0 + normalize*maxScaleMultiplier -> multiplicador de atributos
p.experienceReward(distance, threat); // baseXp * (1 + normalize*maxXpMultiplier) -> experiencia que suelta
// Los overloads sin threat usan amenaza 0 (zona protegida sin encoger).
```

Los métodos aceptan un parámetro `threat` (0..1) para que la **zona protegida se encoga con el tiempo**
(`minDistance` -> `minHardDistance`). Los overloads sin `threat` usan amenaza 0 (mínimo sin encoger).
`experienceReward` escala la experiencia con la distancia (`baseXp` en la zona protegida hasta
`baseXp*(1+maxXpMultiplier)` a la distancia máxima).

---

## 4) Cómo crear una **SpawnRule** (interfaz `CustomSpawnRule`)

Una spawnRule implementa `CustomSpawnRule` y define **qué**, **cada cuánto**, **con qué probabilidad**,
**dónde** y **cómo** spawnea. El `CustomSpawner` la evalúa cada intervalo.

```java
package com.chipoodle.devilrpg.spawner;

public class BanditSpawnRule implements CustomSpawnRule {

    private static final SpawnScaleProfile PROFILE = BanditSpawnProfile.INSTANCE;

    // --- Intervalo aleatorio entre intentos (20 ticks = 1 s) ---
    @Override public int getMinIntervalTicks() { return 30 * 20; }          // 30 s
    @Override public int getMaxIntervalTicks() { return 10 * 60 * 20; }     // 10 min

    // --- Qué entidad spawnea ---
    @Override public EntityType<? extends Mob> getEntityType() { return ModEntities.BANDIT.get(); }

    // --- Probabilidad 0..1 según contexto (aquí, según la distancia del jugador a su spawn) ---
    @Override public float getSpawnChance(ServerLevel level, ServerPlayer player) {
        Vec3 spawnPoint = getSpawnPoint(player);
        if (spawnPoint == null) return 0.0F;
        double distance = Math.sqrt(player.distanceToSqr(spawnPoint));
        if (distance < PROFILE.minDistance()) return 0.0F;   // zona protegida
        if (distance > PROFILE.maxDistance()) return 1.0F;   // máximo
        return (float) PROFILE.probability(distance);
    }

    // --- Posición válida lejos del jugador (suelo + aire encima), o null si no hay ---
    @Override public @Nullable BlockPos findSpawnPosition(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 25; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double distance = 48 + level.random.nextDouble() * (96 - 48);
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos surface = findSurface(level, new BlockPos(x, player.getBlockY(), z));
            if (surface != null) return surface;
        }
        return null;
    }

    // --- Opcionales ---
    @Override public int getMaxAliveInLevel() { return 30; }   // límite de vivas simultáneas
    @Override public int getMinSpawnCount() { return 1; }      // se elige aleatorio entre min..max
    @Override public int getMaxSpawnCount() { return 3; }

    // --- Ajustar la entidad tras crearla (por defecto no hace nada) ---
    @Override public void configureEntity(Mob entity, ServerLevel level, ServerPlayer player) { }

    private Vec3 getSpawnPoint(ServerPlayer p) {
        PlayerAuxiliaryCapabilityInterface cap =
                IGenericCapability.getUnwrappedPlayerCapability(p, PlayerAuxiliaryCapability.INSTANCE);
        return cap == null ? null : cap.getSpawnPoint();
    }

    private @Nullable BlockPos findSurface(ServerLevel level, BlockPos start) {
        for (int y = start.getY(); y > start.getY() - 16; y--) {
            BlockPos pos = new BlockPos(start.getX(), y, start.getZ());
            if (level.getBlockState(pos).isSolid() && level.getBlockState(pos.above()).isAir())
                return pos.above();
        }
        return null;
    }
}
```

### Registrar la regla en el `CustomSpawner`

En `CustomSpawnerTickHandler.onServerTick`, dentro del `computeIfAbsent`, agrega tu regla:

```java
CustomSpawner spawner = SPAWNERS.computeIfAbsent(level, l -> {
    CustomSpawner s = new CustomSpawner(l);
    s.register(new AggressiveZombieSpawnRule());
    s.register(new BanditSpawnRule());      // ← tu nueva regla aquí
    return s;
});
spawner.tick();
```

---

## 5) Atributos dinámicos en la **entidad**

La entidad se configura sola: guarda la distancia al spawn al crearse y, una vez, escala sus atributos
según `scaleFactor(distance)`.

### 5.1 Registrar la entidad (tipo + atributos base)

En `ModEntities` registra el `EntityType`; en la entidad, define el `AttributeSupplier`:

```java
public static AttributeSupplier.Builder setAttributes() {
    return Zombie.createAttributes()
            .add(Attributes.MAX_HEALTH, 20.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.2D)
            .add(Attributes.ATTACK_DAMAGE, 6.5D)
            .add(Attributes.FOLLOW_RANGE, 64.0D);
}
```

### 5.2 Capturar la distancia al spawn

En `setPos` (o en el `spawn` del `configureEntity`), guarda la distancia al punto de inicio:

```java
@Override
public void setPos(double x, double y, double z) {
    super.setPos(x, y, z);
    if (spawnDistance == 0) {   // solo al momento del spawn
        Player nearest = this.level().getNearestPlayer(this, PROFILE.maxDistance());
        if (nearest != null) {
            PlayerAuxiliaryCapabilityInterface cap =
                    IGenericCapability.getUnwrappedPlayerCapability(nearest, PlayerAuxiliaryCapability.INSTANCE);
            Vec3 spawn = cap.getSpawnPoint();
            if (spawn != null)
                spawnDistance = Math.sqrt(this.blockPosition().distSqr(
                        new BlockPos((int) spawn.x, (int) spawn.y, (int) spawn.z)));
        }
    }
}
```

### 5.3 Aplicar el escalado una sola vez

```java
private boolean attributesAdjusted = false;

@Override
public void aiStep() {
    super.aiStep();
    if (!attributesAdjusted) {
        adjustAttributesBasedOnSpawnDistance();
        attributesAdjusted = true;   // solo una vez
    }
}

private void adjustAttributesBasedOnSpawnDistance() {
    double factor = PROFILE.scaleFactor(spawnDistance);
    Objects.requireNonNull(this.getAttribute(Attributes.MAX_HEALTH)).setBaseValue(PROFILE.baseHealth() * factor);
    Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(PROFILE.baseSpeed() * factor);
    Objects.requireNonNull(this.getAttribute(Attributes.ATTACK_DAMAGE)).setBaseValue(PROFILE.baseDamage() * factor);
}
```

---

## 6) Cómo implementar para una **entidad nueva** (paso a paso)

1. **Crea su perfil**: un `SpawnScaleProfile` (o una clase `XxxSpawnProfile` con una `INSTANCE`).
2. **Crea su entidad**: registra el `EntityType` en `ModEntities`, define `setAttributes()`, y haz que
   capture `spawnDistance` (5.2) y escale sus atributos una vez (5.3).
3. **Crea su spawnRule**: implementa `CustomSpawnRule` (sección 4), usando
   `PROFILE.probability(distance)` para la chance.
4. **Regístrala** en `CustomSpawnerTickHandler` (sección 4, "Registrar la regla").
5. Compila y prueba.

> Consejo: si la entidad solo necesita **probabilidad** (no escalar atributos), puedes usar el
> `SpawnScaleProfile` solo en la spawnRule; si solo necesita **escalar** (sin spawnRule), úsalo solo en
> la entidad. El record es opcional en cada lado.

## 7) Consideraciones

- **Consistencia garantizada**: `probability()` y `scaleFactor()` usan la misma `normalize()`, así que la
  entidad y su regla nunca divergen.
- **Un solo `SpawnScaleProfile` por entidad** (una única instancia compartida). No lo dupliques.
- El record es **neutro**: no importa entidades, spawnRules ni capacidades, para evitar dependencias
  circulares. Vive en su propio paquete y lo pueden usar tanto `entity` como `spawner`.
- El `CustomSpawner` registra las reglas **una vez por dimensión** (solo overworld en `CustomSpawnerTickHandler`).

## 8) Referencia rápida de `AggressiveZombieSpawnProfile`

| Parámetro | Valor | Significado |
|---|---|---|
| `minDistance` | 200 | No spawnea dentro de los primeros 200 bloques (zona protegida). |
| `maxDistance` | 1500 | Probabilidad 1 a partir de 1500 bloques. |
| `maxScaleMultiplier` | 1.3 | Los atributos suben hasta +30% en la distancia máxima. |
| `baseHealth` | 20.0 | Vida base. |
| `baseSpeed` | 0.2 | Velocidad base. |
| `baseDamage` | 3.25 | Daño base. |
