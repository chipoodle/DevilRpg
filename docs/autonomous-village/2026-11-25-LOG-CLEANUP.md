# Limpieza de logs — solo estado y medición (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Origen:** el `latest.log` de la partida tenía 382 líneas en ~4 min, con 120 `[Village] PARAR`,
32 avisos de plantillas, 20 de portón, minions periódicos y diagnósticos de asedio por tick.

## 1 · Política aplicada

- **INFO:** solo transiciones/estado que sirven para medir o reconstruir la partida:
  aldea generada, asedio iniciado/oleada/cierre, campana destruida, ocupación, aldea caída, sello,
  hambre, objetivo, errores y warnings.
- **DEBUG:** todo lo periódico o por entidad/tick: `PARAR` de guardias, portones, ruta resumida,
  huerta, camas, rebaño, reparto de puestos, plantillas de casas, `FARMER-GROW`, `TREE-CLAIM`,
  `[Siege] asaltante ... va por/pica/puente/escalón/recuperación`, `CADENA-DIAG`, `JUMPUP`,
  `[NAV]`, `[A2N]`, `[RECOVERY]`, `Spawn Check`, `[Sculk] Sacrificio` y las copias periódicas de minions.
- **WARN/ERROR:** intactos.

Se mantienen en INFO, entre otros:

- `[Village] Aldea N: asedio iniciado...`
- `[Village] Aldea N: OLEADA de ... asediadores...`
- `[Village] Aldea N: ... DESTRUYO la campana...`
- `[Siege] asaltante #N ... ENTRO en el recinto...`
- `[Siege] asaltante #N ... entra en OCUPACION...`
- `[Siege] asaltante #N ABRE BRECHA...`
- `[Siege] asaltante #N RETREAT_STARTED...`
- `[Village] La aldea N ha CAÍDO y queda en ruinas`
- `[Village] ... CIMIENTO ...`, hambre, sello, errores y warnings.

## 2 · Archivos tocados

`VillageManager`, `VillageGenerator`, `DoubleGateBlock`, `AggressiveZombieEntity`, `NavExecutor`,
`VillagerGuardGoal`, `VillagerLumberjackGoal`, `VillagerFarmGoal`, `SculkCultivatorEntity` y
`PlayerMinionCapabilityImplementation`.

## 3 · Efecto esperado

- El `latest.log` deja de llenarse con **PARAR**, portones, casas, huerta, camas, cosecha, árboles,
  diagnósticos de asedio y copias de minions.
- `debug.log` conserva todo eso para diagnóstico cuando se active el nivel DEBUG.
- El juego **no cambia de comportamiento**; solo cambia el nivel de log.

## 4 · Límites

- Los cambios no están activos hasta **reiniciar** el cliente/mundo.
- No se tocó `AggressiveZombieSpawnProfile` (el cambio `minDistance 67→40` sigue sin commit, ajeno a
  esta limpieza) ni el candidato de ancho de brecha.
- Gate completo: **BUILD SUCCESSFUL** (1m28s); skills 416, persistencia 553, ownership 59.
