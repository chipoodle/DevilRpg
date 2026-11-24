# L1 — Cierre del libro de puestos al desaparecer el titular (24-nov-2026)

**RUN_ID:** `av-l1-20261124-1020`
**Rama:** `feature/autonomous-village`
**INITIAL_HEAD:** `2d687921cd9841c7a8a6afea3cbc792f3ce14aa7`
**Auditoría:** `2b32ba086964b231622e941b4b723415cce23f40` (solo docs)
**TESTED_HEAD (código y pruebas):** `7db821eabbbd7dc8ec25e8ff50617de7f601edbc`
**FINAL_HEAD:** `941ac75bef6ba2d2f4718525ad4e2f51bf8b8031` (commit de este informe; después se añadió
P1 de skills, que no toca el código de L1).
**Origen previo:** `origin/feature/autonomous-village`; rama local 53 commits por delante al empezar.
**Árbol inicial:** limpio salvo untracked preservados (los mismos de la auditoría).

---

## 1 · Hallazgo y clasificación

`2026-11-24-PENDING-AUDIT.md` clasificó **L1** como `IMPLEMENTED_BUT_DEFECTIVE`:

- El libro persistido `VillageSavedData.JobSiteOwners` (POS → UUID) es la autoridad del titular de una
  estación.
- Al morir un aldeano, vanilla libera el ticket (`Villager.die → releaseAllPois`), pero **el libro no se
  enteraba**.
- `VillageSavedData.claimJobSite(...)` rechaza cualquier reclamación si ya hay un dueño distinto. Por
  tanto, la estación quedaba **libre en `PoiManager` pero reservada en el libro para siempre**, hasta una
  limpieza administrativa (`forgetJobSite`).
- La misma pérdida ocurría en una **conversión** (aldeano → aldeano zombi o bruja): `Mob.convertTo` hace
  `discard()` **sin** pasar por `Villager.die`, así que no hay evento de muerte ni liberación de POI.

**Consecuencia funcional:** un aldeano profesional que muere o se convierte deja su estación muerta para
los reemplazos. En un soak largo, la cobertura de puestos cae y no se recupera sin intervención.

**Evidencia documental:** `2026-11-24-TECHNICAL-CLOSURE-CAMPAIGN.md` §9.8 (limitación declarada) y
`2026-11-24-CONTINUITY-CHECKPOINT.md` L1.

**Evidencia de código (antes):** `grep` de `releaseJobSite`/`forgetJobSite` no mostraba ningún hook de
muerte o conversión; el único consumidor era la reconciliación de aldeanos vivos.

---

## 2 · Modificación (mínima, reutilizando la arquitectura existente)

No se añadió un sistema nuevo. Se reutilizan las dos piezas que ya existían:

1. **Política pura** `VillageJobSiteOwnershipPolicy.puestosDelTitular(Map<Long, UUID>, UUID)`:
   devuelve los POI del titular, ordenados de forma determinista. No muta el mapa, no inventa entradas,
   no toca a otros titulares y tolera `null` (idempotente).
2. **Datos** `VillageSavedData.releaseJobSitesOwnedBy(UUID)`:
   borra del libro y de `OwnerPos` exactamente los POI que devuelve la política, marca `setDirty()` una
   sola vez y devuelve cuántos olvidó.
3. **Autoridad de aldea** `VillageManager.olvidarPuestosDeTitular(ServerLevel, UUID)`:
   aplica la limpieza y deja una línea de log solo si hizo algo.
4. **Disparadores** en `VillagerAgentRegistry`, que ya recibía la muerte de aldeanos para liberar los
   claims de silvicultura:
   - `onDeath(LivingDeathEvent)` → limpia el libro.
   - `onConversion(LivingConversionEvent.Post)` → limpia el libro en la conversión.

**Lo que NO se hace (deliberadamente):**

- No se limpia en `EntityLeaveLevelEvent`: la descarga de un chunk también lo dispara y el dueño sigue
  vivo. Borrar ahí perdería dueños reales.
- No se fuerza `release` del ticket: en la muerte lo libera vanilla; en la conversión el ticket huérfano
  lo recupera la reconciliación existente (`soltarTicketsPerdidos`), ahora que el libro no lo bloquea.
- No se toca la reconciliación, la cobertura, C4, POI, navegación ni presupuestos.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `src/main/java/com/chipoodle/devilrpg/world/VillageJobSiteOwnershipPolicy.java` | `puestosDelTitular` (pura) |
| `src/main/java/com/chipoodle/devilrpg/world/VillageSavedData.java` | `releaseJobSitesOwnedBy` |
| `src/main/java/com/chipoodle/devilrpg/world/VillageManager.java` | `olvidarPuestosDeTitular` + log |
| `src/main/java/com/chipoodle/devilrpg/agent/VillagerAgentRegistry.java` | hooks de muerte y conversión |
| `src/test/java/.../VillageJobSiteOwnershipPolicyTest.java` | +7 aserciones (39 total) |
| `src/villageTest/java/.../VillagePersistenceIntegrationTest.java` | +19 aserciones (544 total) |

---

## 3 · Criterios de aceptación y evidencia

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Solo se olvidan las entradas del titular, incluida `OwnerPos`; idempotente | `VillagePersistenceIntegrationTest` bloque 11: ALICE en A/B, `releaseJobSitesOwnedBy(ALICE)=2`, A/B libres, `OwnerPos` nulas, segunda llamada `0`; CAROL intacta | **PASS** |
| 2 | Otro aldeano puede reclamar tras la limpieza | Bloque 11: `claimJobSite(poiA, BOB, posA)` devuelve `true` y A queda de BOB; antes de la limpieza devolvía `false` | **PASS** |
| 3 | Se dispara en muerte y conversión, no en descarga de chunk | Hooks en `VillagerAgentRegistry.onDeath` y `onConversion`; no se toca `onLeave`. Verificado por compilación y revisión de código; **no hay runtime con aldeano muerto** en esta sesión | **WIRING OK; runtime no medido** |
| 4 | El NBT no resucita entradas limpiadas y no toca a otros | Bloque 11: save/load conserva BOB y CAROL, `poiB` (ALICE muerta) sigue `null`; tras recargar, `releaseJobSitesOwnedBy(BOB)=1` sin tocar CAROL | **PASS** |
| 5 | Gates verdes | ver §4 | **PASS** |

**Limitación declarada:** el criterio 3 está demostrado a nivel de código y compilación, no con una
corrida runtime de un aldeano muriendo. C4/POI no se reejecutaron: no matan aldeanos y el cambio no
altera sus rutas (solo se ejecuta en muerte/conversión). La prueba de integración cubre la semántica
persistida, que es donde estaba el defecto.

---

## 4 · Pruebas ejecutadas

| Comando | Resultado |
|---|---|
| `.\gradlew.bat test compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true --console=plain --offline` | **BUILD SUCCESSFUL** (código `7db821ea`) |
| `VILLAGE_PERSISTENCE_INTEGRATION_TEST` | **OK (544 assertions)** — 525 previas + 19 nuevas |
| `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST` | **OK (39 assertions)** — 32 previas + 7 nuevas |
| `policyTest` (80 clases) | **OK**, sin fallos |
| `navScenarioContractTest` | **OK (253)** |
| `poiPersistenceSelftest` | **PASS 15** |
| `NAV_SEARCH_BASELINE_TEST` | **OK (119)** |
| `python tools\lint_aldea.py --strict` | **OK (sin patrones peligrosos)**, exit 0 |
| `git diff --check` | **0 errores** (solo warnings CRLF del árbol) |

**Antes/después de aserciones:** persistencia 525 → **544**; ownership 32 → **39**. No se debilitó
ninguna aserción existente.

**Regresión funcional:** los gates de navegación, POI, políticas y persistencia pasan con el cambio. No
se modificaron presupuestos, protección, lease, soporte, AABB, headroom, C4 ni criterios de aceptación.

---

## 5 · Riesgos y seguimiento

- **Runtime no medido:** no se ejecutó un servidor con un aldeano que muera/convierta. El hook es una
  llamada directa y compila; la semántica del libro está probada. Si se quiere aceptación runtime, el
  fixture sería: aldea C4, matar al titular de un puesto, esperar la reconciliación y comprobar que otro
  aldeano reclama la estación y que el libro la registra.
- **Conversión:** el ticket no lo libera vanilla; la recuperación depende de la reconciliación existente.
  Con el libro limpio, `soltarTicketsPerdidos` ya puede actuar. No se añadió una liberación forzada para
  no robar tickets en escenarios de doble memoria (L2).
- **Datos históricos:** un mundo con una entrada de un muerto anterior a este fix sigue bloqueado hasta
  que el aldeano se cargue (no hay hook retroactivo). El fix aplica a muertes/conversiones futuras; para
  limpiar datos viejos habría que cargar al dueño o usar `forgetJobSite` administrativo. Se documenta para
  no declarar una limpieza retroactiva que no existe.

---

## 6 · Estado de la deuda

| Deuda | Estado tras este cierre |
|---|---|
| **L1** muerte/conversión del titular | **RESUELTO** (código + pruebas headless; runtime no medido) |
| L2 keeper no cargado | Abierta |
| L3 `restaurar` con `yaDeOtro` | Abierta |
| L4 `OwnerPos` | Abierta (más estrecha: se refresca en la reconciliación de `JOB_SITE` cargados) |
| L5–L8 | Sin cambios |
| Soak multi-día | Sin cambios (siguiente objetivo independiente) |
| Skills sin tests / dependencia de cliente | Sin cambios (siguiente objetivo independiente) |

---

## 7 · Veredicto

**L1 cerrado** con la modificación mínima, reutilizando la política y el libro existentes, sin duplicar
autoridades ni debilitar criterios. La semántica persistida está probada (544 aserciones) y la deuda
queda registrada con su límite exacto: **falta la corrida runtime de muerte real**.
