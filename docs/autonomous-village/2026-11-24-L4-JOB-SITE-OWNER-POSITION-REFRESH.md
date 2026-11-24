# L4 — Refresco de la posición persistida del dueño del puesto (24-nov-2026)

**RUN_ID:** `av-l4-20261124-1105`
**Rama:** `feature/autonomous-village`
**INITIAL_HEAD:** `fca8a658091c0f91f77678a75d07664c4f52cfaf`
**TESTED_HEAD (código y pruebas):** `e335d8676a81997d9ca71d37ec0ac975db4df89a`
**FINAL_HEAD:** commit de este informe.
**Origen:** `2026-11-24-PENDING-AUDIT.md` §6 (L4) y `2026-11-24-CONTINUITY-CHECKPOINT.md` L4.

---

## 1 · Hallazgo

`VillageSavedData` guarda, por puesto, `OwnerPos` (la última posición conocida del dueño) para poder
cargar su chunk cuando la estación se reconcilia y el dueño no está cargado. La limitación L4 decía que
esa posición **no se refrescaba** al moverse el dueño: solo se escribía al reclamar.

Verificado en código: `cargarDueniosDePuestos` hacía

```java
if (level.getEntity(entrada.getValue()) != null) {
    continue; // dueño cargado: no se toca OwnerPos
}
```

Así que un dueño cargado **sin `JOB_SITE`** (o fuera del censo de la aldea) conservaba la posición de su
último claim. Si después se descargaba, la carga se dirigía a un sitio viejo y la restauración podía
esperar a que el aldeano se cargara por otra vía. La reconciliación de `POTENTIAL` sí refrescaba a los
titulares con `JOB_SITE`, pero no cubría el resto.

---

## 2 · Modificación

`VillageManager.cargarDueniosDePuestos`:

- Si el dueño del libro está **cargado, vivo y no removido**, se llama a
  `saved.claimJobSite(clave, dueño, v.blockPosition())`. Es el mismo dueño, así que no cambia la
  titularidad; solo actualiza `OwnerPos` y marca `dirty` si la posición cambió.
- Si no está cargado, se mantiene el comportamiento anterior: cargar el chunk de `OwnerPos` y no borrar
  la entrada si el UUID no aparece (el dueño pudo moverse).

No se toca el libro de otros dueños, ni los tickets, ni la reconciliación, ni la restauración.

| Archivo | Cambio |
|---|---|
| `src/main/java/.../world/VillageManager.java` | refresco de `OwnerPos` para dueños cargados |
| `src/villageTest/java/.../VillagePersistenceIntegrationTest.java` | bloque 12, +9 aserciones (553) |

---

## 3 · Criterios de aceptación y evidencia

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | El mismo dueño actualiza su posición sin cambiar de titular | Bloque 12: claim con posición A, claim con posición B, `getJobSiteOwner` sigue siendo ALICE | **PASS** |
| 2 | Un intento de otro dueño no pisa la posición | `claimJobSite(poi, BOB, primera)` devuelve `false` y `OwnerPos` sigue en B | **PASS** |
| 3 | La posición refrescada persiste en NBT | save/load: `jobSiteOwnerPosition` == B, dueño == ALICE | **PASS** |
| 4 | No se borra al dueño cargado ni se toca el ticket | El código solo llama a `claimJobSite` con el mismo UUID; no hay `release`/`forget` | **PASS (revisión)** |
| 5 | Gates verdes | ver §4 | **PASS** |

**Límites:**

- No hay corrida runtime con un aldeano que se aleje y se descargue; la semántica del refresco está
  probada a nivel de `VillageSavedData` y el cableado es una llamada directa.
- El refresco ocurre en el latido de la aldea (cada `VILLAGE_POLL_TICKS`, 10 s), no cada tick. Puede
  marcar el `SavedData` como dirty cuando el aldeano se ha movido; el patrón ya existía para los
  titulares con `JOB_SITE` en `reconciliarPuestosPotenciales`.

---

## 4 · Pruebas ejecutadas

| Comando | Resultado |
|---|---|
| `.\gradlew.bat test compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true --console=plain --offline` | **BUILD SUCCESSFUL** |
| `VILLAGE_PERSISTENCE_INTEGRATION_TEST` | **OK (553 assertions)** — 544 + 9 nuevas |
| `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST` | **OK (54)** |
| `SKILL_CAPABILITY_INTEGRATION_TEST` | **OK (376)** |
| `policyTest`, NAV contract 253, POI 15, baseline 119 | **OK** |
| `python tools\lint_aldea.py --strict` | **OK**, exit 0 |
| `git diff --check` | **0 errores** |

---

## 5 · Estado de la deuda

| Deuda | Estado |
|---|---|
| **L4** `OwnerPos` no se refrescaba | **RESUELTO** (datos + cableado; runtime no medido) |
| L2 keeper no cargado | Abierta |
| L5–L8 | Sin cambios |
| Soak multi-día | Sin cambios |

---

## 6 · Veredicto

**L4 cerrado en su parte ejecutable**: un dueño cargado refresca su última posición en el libro, sin
cambiar de titular ni tocar el ticket, y la posición sobrevive al NBT. Queda declarada la ausencia de una
corrida runtime de alejamiento/descarga.
