# AUTONOMOUS VILLAGE — CAMPAÑA DE EVOLUCIÓN · INCREMENTO 1 (2026-11-23)

> Estado: **incremento validado; campaña ABIERTA** (quedan tareas ejecutables de los objetivos C/D/E/F).
> Producción: solo se añadió un verificador puro y su integración de diagnóstico; no se alteró ningún
> comportamiento aceptado de A2/C4.

---

## 1. Alcance entregado

### 1.1 Invariantes del censo de la aldea (Objetivo A/B)

- **Nuevo** `VillageRosterInvariants` (puro, `src/main/java/.../world`): valida un censo contra el contrato del
  pueblo — **12 puestos**, **4 guardias de fundación**, **un solo dueño por puesto** (`JOB_SITE`), **cupos por
  oficio** (`puestosPorOficio`), identidad única (UUID), profesión válida y sin miembros nulos.
- **Nuevo** `VillageRosterInvariantsTest` (11 aserciones): censo válido, UUID duplicado, puesto duplicado,
  oficio excedido, profesionales de más/menos, fundadores de menos, profesión vacía, profesión fuera del cupo,
  miembro nulo, censo nulo y puesto 0 repetido (permitido).
- **Integración de diagnóstico** en el arnés C4 (`tools/c4n/C4StandaloneHarness.java`): cada fase calcula los
  invariantes del censo cargado y los registra (`ROSTER_INVARIANTS ok=... violaciones=...` y
  `roster_invariantes_ok` en `phase-<fase>.json`). Es **informativo**: no invalida las campañas ya aceptadas;
  queda listo para exigirlo cuando se revalide.

### 1.2 Cobertura de persistencia y ownership (Objetivo B)

Ampliación de `VillageJobSiteOwnershipPolicyTest` (de 9 a **13 aserciones**):

- **Idempotencia**: tras excluir a los sobrantes, un segundo pase no encuentra nada.
- **No destrucción**: el titular válido conserva su puesto.
- **Dos ciclos**: los sobrantes reasignados a POIs libres dejan el siguiente pase sin duplicados.

## 2. Evidencia

```text
compileJava      BUILD SUCCESSFUL
compileTestJava  BUILD SUCCESSFUL
test             BUILD SUCCESSFUL
policyTest       BUILD SUCCESSFUL  (VILLAGE_ROSTER_INVARIANTS_TEST, VILLAGE_JOB_SITE... 13)
lint_aldea.py --strict   OK
git diff --check         0
```

SHA probado: `340f7f466bdb43b2bef01cd869e31a7b9cce403b` + este incremento (commit siguiente).

## 3. Preservación de A2 y C4

- No se ha tocado navegación, planificación, movimiento, BREAK/BUILD/DROP, ni el reparto/persistencia.
- El verificador es **de solo lectura** (no crea, no modifica, no libera tickets).
- Los tests nuevos son puros (sin juego).
- Las evidencias de A2 (3 corridas) y C4 (3 campañas) permanecen intactas.

## 4. Inventario de pendientes de la campaña (honesto)

| Objetivo | Estado | Pendiente ejecutable | Justificación |
|---|---|---|---|
| A · invariantes de aldea | **DONE (incremento)** | exigir los invariantes en una campaña C4 nueva | el arnés ya los calcula; falta revalidar |
| B · persistencia/ownership | **PARCIAL** | pruebas de integración NBT/save-reload en `test`; escenarios de carga parcial | requieren infraestructura de test con mundo/NBT |
| C · matriz de navegación | **PENDIENTE** | matriz sistemática DROP/BREAK/BUILD/STEP_UP con resultados esperados | requiere leer y fijar las firmas de las políticas de navegación |
| D · rendimiento | **PENDIENTE** | medición de expansiones/tiempo por plan; comparativa | requiere arnés con instrumentación de tiempos |
| E · herramientas | **PARCIAL** | reconstruir GuardHarness/verificador contra AV; recolección de métricas | depende de C/D |
| F · documentación | **EN CURSO** | actualizar arquitectura/invariantes con el verificador | continuo |

## 5. Commits y estado

- Commit de este incremento: ver `git log -1` (docs + verificador + test + harness).
- Árbol: limpio salvo untracked preservados; sin operaciones destructivas; sin push.
- Worktrees: `migration-neoforge-1.21.1` intacto en su worktree; no se ha tocado.

## 6. Siguiente paso recomendado

1. **Objetivo C**: construir la matriz de navegación como test puro (usando las políticas existentes) y, si
   aparece un defecto, reproducirlo y corregirlo con su test.
2. **Objetivo B**: añadir una prueba de integración de save/reload en `test` (NBT) si la infraestructura lo
   permite sin runtime.
3. **Objetivo D**: instrumentar el planner con contadores de expansión/tiempo y medir en el arnés C4/A2.
4. **Revalidación**: una campaña C4 nueva que exija `roster_invariantes_ok=true`.

No se declara la campaña completa: quedan tareas ejecutables de C/D/E y la revalidación de A.

---

## INCREMENTO 2 (2026-11-23) — Objetivo C: matriz de navegación

- **Nuevo `NavTerrainMatrixTest`** (registrado en `policyTest`): matriz de **~40 aserciones** que combina
  STEP_UP (celda de pies válida/protegida/sin soporte/colisión/fluido/chunk descargado), DROP (bordes 1-3,
  fuera de alcance, aterrizaje sin soporte/holgura, en el aire, despegue sin soporte), **DROP+overshoot**
  (cae al aterrizaje, cae por debajo dentro de la caída segura, exceso rechazado, por encima rechazado,
  horizontal >1), **BREAK+BUILD** (escalón de ascenso: despeje, fases CLEAR/PLACE/MOVE, aterrizaje
  verificado) y **presupuestos** (túnel: primer plan, cooldown, topes de planes/pasos, pasos disponibles).
- **Invariantes del censo ahora OBLIGATORIOS** en el arnés C4: la fase falla si `VillageRosterInvariants` no
  se cumple (`okFase && invariantes.ok()`), además de registrarlo en el JSON.
- Escenarios que **requieren runtime** (documentados en la propia prueba): DROP entre columnas reales,
  BREAK con línea de visión, BUILD con colocación, reanudación de A*, cambios dinámicos de terreno y
  objetivos inaccesibles con presupuesto.
- Gates verdes: compileJava/compileTestJava/test/policyTest/lint/diff-check.

### Pendiente tras el incremento 2
- **Objetivo A (revalidación)**: campaña C4 nueva con `roster_invariantes_ok=true` obligatorio (runtime).
- **Objetivo B**: prueba NBT/save-reload en `test`.
- **Objetivo D**: instrumentación de expansiones/tiempo y línea base.
- **Objetivo E**: herramientas (GuardHarness/verificador/métricas) contra AV.

---

## INCREMENTO 3 (2026-11-23) — A revalidado; B, D y E implementados

**Informe completo:** `2026-11-23-INCREMENT-3-RUNTIME-PERSISTENCE-INSTRUMENTATION.md`.

- **A · C4 runtime con invariantes obligatorios: PASS.** `c4n-20261123-024` (SHA `1abab57d`) pasó PREPARE,
  RELOAD1 y RELOAD2 con `roster_invariantes_ok=true` en las tres; 12/12 profesionales, 4 fundadores, 0 UUID
  duplicados, 0 pérdidas, 0 `JOB_SITE` duplicados/huérfanos, identidades y goals estables. Limitación declarada:
  solo 2–3 de 12 aldeanos tienen `JOB_SITE` en la ventana; el verificador exige un dueño por puesto **presente**,
  no cobertura total. La campaña final sobre el SHA de código se registra en el informe §6.
- **B · NBT/save-reload real: DONE.** Nuevo `src/villageTest/.../VillagePersistenceIntegrationTest.java` (484
  aserciones) con `Bootstrap` headless, `VillageSavedData.save/load`, `NbtIo` a fichero, `Villager` real +
  `EntityType.create` + `Brain/JOB_SITE`, dos ciclos, carga parcial, corrupción, reconciliación y máscara de
  fundadores. Defecto real corregido: `VillageSavedData.readBlueprint` descarta planos corruptos (arrays
  desalineados, índice fuera de paleta, `Name` inválido) que antes podían lanzar al cargar o al recorrerlos.
- **D · Instrumentación del planner: DONE.** `NavPlannerMetrics` (flag `devilrpg.nav.metrics`, default off) mide
  expansiones, duración, slices, nodos/avance, reanudadas, replanificaciones, fallos, presupuesto, coste por
  entidad y agregado; `NavGraphSearch` intacto; `NavPlanner` conserva el cuerpo original. `NavPlannerMetricsTest`
  (68) y `NavSearchBaselineTest` (119) con 5 escenarios deterministas y no-regresión ON/OFF.
- **E · Herramientas: DONE.** Arnés C4 con `AL_CARGAR`, `jobsite-<fase>.txt`, comparación de propiedad,
  métricas de guardias, `esperado/observado/invariantes/causa_conocida/causa_desconocida`, `guardado_ok` y
  `done` después del guardado. Nuevos `tools/av/diagnose.ps1`, `collect.ps1`, `nav-baseline.ps1` y `LEEME.md`.
  `GuardHarness` evaluado y **no portado** (hardcodes, mutación del mundo, duplicaría el mantenimiento actual).
- **F · Documentación:** informe del incremento, invariantes I387–I391, matriz de pruebas y checkpoints.
- **Gates:** `compileJava`, `compileTestJava`, `test` (incluye `policyTest` y `villagePersistenceTest`),
  `navBaselineTest`, `compileJava -Pa2nDiagnostics=true`, `lint_aldea.py --strict`, `git diff --check` — verdes.
- **Estado:** A, B, D, E y F completados. Campaña final `c4n-20261123-1550` sobre el SHA de código
  `69d7e55a6c848fd6c0542e61a8efe8913431f13c` → `C4N_PASS` en las 3 fases, con `roster_invariantes_ok=true`,
  `guardado_ok=true`, 12/12, 4 fundadores, 0 duplicados/huérfanos y `JOB_SITE` 3/3 estable en reload2.
  Evidencia completa en el informe del incremento §6.
