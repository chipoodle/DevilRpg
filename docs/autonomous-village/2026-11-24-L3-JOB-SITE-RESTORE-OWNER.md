# L3 — Restauración del titular cuando otro aldeano ocupa su puesto (24-nov-2026)

**RUN_ID:** `av-l3-20261124-1050`
**Rama:** `feature/autonomous-village`
**INITIAL_HEAD:** `8fb9a6ef108f781458938976cba26934481a1c82`
**TESTED_HEAD (código y pruebas):** `e9afbdfca5d2eeba7c6157d4d61848ab638a32e4`
**FINAL_HEAD:** commit de este informe.
**Origen:** `2026-11-24-PENDING-AUDIT.md` §4 (L3) y `2026-11-24-CONTINUITY-CHECKPOINT.md` L3.

---

## 1 · Hallazgo y causa raíz

`VillageManager.restaurarDueniosDelLibro` devuelve el `JOB_SITE` a los dueños persistidos que lo
perdieron, pero tenía esta guarda:

```java
// Y no se duplica el puesto si otro aldeano cargado ya lo tiene.
boolean yaDeOtro = ... otro aldeano cargado con JOB_SITE en el mismo POI ...;
if (yaDeOtro) {
    continue;
}
```

Con el libro diciendo que el puesto es de **A** y **B** (cargado) conservando la memoria del mismo POI:

- A no tenía `JOB_SITE` (por eso entraba en la restauración).
- La guarda veía a B y **no restauraba a A**.
- `reconciliarPuestosDuplicados` tampoco actuaba: solo mira dos o más aldeanos con el **mismo**
  `JOB_SITE`; aquí solo B lo tenía.
- Resultado: A quedaba sin estación **hasta que B la soltara**, aunque el libro (autoridad persistida,
  I393) decía que era de A. Es la limitación L3.

No era un fallo de tickets: el ticket seguía cogido; el problema era que la memoria de B bloqueaba la
restauración del titular legítimo.

---

## 2 · Modificación

### 2.1 Política pura

En `VillageJobSiteReconciliationPolicy`:

- `record Restauracion(UUID titular, long poi, boolean titularCargado, boolean titularSinJob,
  boolean poiExiste, boolean tipoDelOficio, boolean dentroDeLaAldea, boolean ticketCogido,
  List<UUID> dueniosCargadosAjenos)` — foto sin clases de Minecraft.
- `record PlanRestauracion(boolean restaurar, List<UUID> limpiarMemorias, String motivo)`.
- `planificarRestauracion(Restauracion)` decide:
  - **no restaura** si el titular no está cargado o ya tiene `JOB_SITE`, el POI no existe, no es de su
    oficio, está fuera de la aldea o el ticket no está cogido (un `JOB` sin ticket sería un fantasma);
  - **restaura** en caso contrario, y devuelve las UUID de los aldeanos cargados que no son el titular
    para limpiarles la memoria, **sin duplicados y ordenadas por UUID** (determinista e idempotente);
  - no suelta tickets.

### 2.2 Ejecución en el gestor

`restaurarDueniosDelLibro` ahora:

1. Recoge una sola vez los aldeanos cargados dentro del radio de la valla.
2. Para cada entrada del libro, construye la foto (`tipo`, dentro del muro, ticket cogido, aldeanos
   ajenos con el mismo POI).
3. Pide el plan a la política. Si no restaura, no toca nada.
4. Si restaura, **primero** limpia `JOB_SITE`/`POTENTIAL_JOB_SITE` de las memorias ajenas indicadas
   (con log) y **después** devuelve el puesto al titular. No toca el ticket.

**Archivos:**

| Archivo | Cambio |
|---|---|
| `src/main/java/.../world/VillageJobSiteReconciliationPolicy.java` | `Restauracion`, `PlanRestauracion`, `planificarRestauracion` |
| `src/main/java/.../world/VillageManager.java` | `restaurarDueniosDelLibro` usa el plan y limpia memorias ajenas |
| `src/test/java/.../VillageJobSiteOwnershipPolicyTest.java` | +15 aserciones (54 total) |

No se modificó `claimJobSite`, el libro, la reconciliación de `POTENTIAL`, los tickets, C4, POI,
navegación ni presupuestos.

---

## 3 · Criterios de aceptación y evidencia

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | El titular recupera el puesto aunque otro cargado tenga la memoria | Política: `Restauracion` con `dueniosCargadosAjenos=[B]` → `restaurar=true`, `limpiar=[B]`; el gestor limpia y restaura | **Decisión PASS; ejecución no runtime** |
| 2 | No se duplica el puesto | Las memorias ajenas se limpian antes de restaurar; el plan no incluye al titular | **PASS (política)** |
| 3 | No se restaura un JOB sin ticket ni un POI inexistente/fuera/de otro oficio | 6 casos negativos en el test: sin cargar, con JOB, POI inexistente, tipo incorrecto, fuera de aldea, ticket libre | **PASS** |
| 4 | Determinista e idempotente | Plan con `[C,B,B,A]` → `limpiar=[B,C]`; dos pasadas devuelven el mismo plan | **PASS** |
| 5 | No se sueltan tickets | El código no llama a `release`; solo limpia memorias | **PASS (revisión)** |
| 6 | Gates verdes | ver §4 | **PASS** |

**Límites declarados:**

- **No hay fixture runtime** que enfrente a A y B en un `ServerLevel` real. La **decisión** está probada
  headless (15 aserciones) y el cableado compila; la ejecución física (limpiar la memoria de B y
  restaurar A) no se ha medido en un servidor.
- C4/POI no se reejecutaron: sus campañas no conservan este escenario (keeper cargado + intruso).
- Si el libro estuviera obsoleto y B fuera el titular real, la política sigue al libro (I393). Es la
  regla documentada; no se añade una heurística alternativa.

---

## 4 · Pruebas ejecutadas

| Comando | Resultado |
|---|---|
| `.\gradlew.bat test compileJava -Pa2nDiagnostics=true -Pa2nProbeOnly=true --console=plain --offline` | **BUILD SUCCESSFUL** |
| `VILLAGE_JOB_SITE_OWNERSHIP_POLICY_TEST` | **OK (54 assertions)** — 39 previas + 15 nuevas |
| `VILLAGE_PERSISTENCE_INTEGRATION_TEST` | **OK (544)** |
| `SKILL_CAPABILITY_INTEGRATION_TEST` | **OK (376)** |
| `policyTest` (80 clases), NAV contract 253, POI 15, baseline 119 | **OK** |
| `python tools\lint_aldea.py --strict` | **OK**, exit 0 |
| `git diff --check` | **0 errores** |

---

## 5 · Estado de la deuda

| Deuda | Estado |
|---|---|
| **L3** titular bloqueado por otro aldeano cargado | **RESUELTO a nivel de decisión y cableado**; runtime no medido |
| L2 keeper no cargado | Abierta |
| L4 `OwnerPos` | Abierta (se refresca en la reconciliación de `JOB_SITE` cargados) |
| L5–L8 | Sin cambios |
| Soak multi-día | Sin cambios (siguiente objetivo independiente) |

---

## 6 · Veredicto

**L3 cerrado en su parte ejecutable**: la decisión ya no se rinde ante `yaDeOtro`, es pura,
determinista y tiene 15 aserciones; el gestor limpia la memoria ajena y devuelve el puesto sin tocar el
ticket. Queda declarada la ausencia de una corrida runtime con dos aldeanos reales.
