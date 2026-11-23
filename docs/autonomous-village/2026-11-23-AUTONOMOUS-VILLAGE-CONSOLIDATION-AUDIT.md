# AUTONOMOUS VILLAGE — AUDITORÍA DE CONSOLIDACIÓN Y HOJA DE RUTA

> **RESULT: COMPLETE.** Auditoría técnica (solo documentación; producción y tests intactos).
> Fecha: **2026-11-23**. Rama: `feature/autonomous-village`. HEAD: **`d27bd6c734178f26bbe0255f9217c6fc77f3a4c1`**.

---

## 1. Resumen ejecutivo

- **A2 (asedio)**: **CLOSED — ACCEPTED** (3 corridas consecutivas, 8/8 estricto, C4 integrado PASS, 12000 ticks).
- **C4 standalone / Save-Reload**: **PASS — ACCEPTED** (3 campañas consecutivas, PREPARE+RELOAD1+RELOAD2 PASS).
- **Git**: rama `feature/autonomous-village`, **33 commits por delante** de `origin/feature/autonomous-village`, merge-base `fea54ef7`; árbol limpio salvo untracked preservados.
- **NeoForge**: AV **ya es NeoForge 1.21.1** (moddev 2.0.146 / neoforge 21.1.249). La rama `migration-neoforge-1.21.1` comparte base `c2fb3699` y tiene **6 commits exclusivos** (A1: guardias de fundación, docs + `VillageManager`), **no portados** a AV.
- **Defectos abiertos demostrados**: **ninguno** en AV ni C4. Quedan **limitaciones conocidas**, **deuda técnica** y **una decisión pendiente de ramas**.
- **Full12K**: no existe una campaña independiente; equivale a las corridas de 12000 ticks ya aceptadas.
- **Muro**: la política `5ad02088` se conserva; cumple banda/cadencia/alcance y no toca invariantes de seguridad.
- **Contrato de finalización**: AV puede declararse terminado con el contrato de §12; el trabajo restante es de consolidación/portabilidad, no correctivo.

---

## 2. Estado de A2 (verificado)

- Informe: `docs/autonomous-village/2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md`.
- Corridas: `a2n-20261122-054142`, `-094529`, `-100646`; `fin.txt` = `C4_PASS`; `srv.log` con `DENTRO-PARED` para los 8 índices; `RESUMEN FASE B` con `entraronPerimetroEver=8`; ventana oficial 12000 ticks; semilla 12345.
- Build aceptada: `5ad020882a184e8e6e95d1398175fb598b600754`.
- Estado: **CLOSED — ACCEPTED**. No reabrir sin evidencia de regresión.

## 3. Estado de C4 standalone / Save-Reload (verificado)

- Informe: `docs/autonomous-village/2026-11-22-C4-STANDALONE-SAVE-RELOAD-ACCEPTANCE.md`.
- Campañas: `c4n-20261123-021`, `-022`, `-023`; `fin.txt` = `C4N_PASS`; las tres fases PASS en cada una; 12/12 presentes, 0 pérdidas, 12/12 profesiones/goals/reanudados, **0 duplicados**, actividad y desplazamiento, guardado/cierre/reapertura.
- SHA probado: `0ab01d0d`; producción: `ff48e257` sobre `2fb687ab`, `41e58087`, `09255bfd`.
- Estado: **PASS — ACCEPTED**.

## 4. Estado de Git (real, no asumido)

| Dato | Valor |
|---|---|
| Branch | `feature/autonomous-village` |
| HEAD | `d27bd6c734178f26bbe0255f9217c6fc77f3a4c1` |
| Upstream | `origin/feature/autonomous-village` |
| Divergencia | **ahead 33 / behind 0** (la afirmación histórica de «27» ya no es válida) |
| Merge-base con origin | `fea54ef73520b5e28e6426c4696d7f4fa1da8fec` |
| Árbol | limpio; solo untracked preservados (`.a2-backups/`, `src/foodTest/`, `tools/a2n/A2NavHarness.java.partial`, `tools/a2n/consultar-c4.ps1`, `tools/a2n/lanzar-c4.ps1`, `tools/a5-closure/`) |
| Ramas locales | `master`, `migration-neoforge-1.21.1` (en worktree `D:/Documents/DevilRpg`), `migration_1.19.3` |

Los **33 commits no publicados** incluyen A2 (aceptación, cierre), C4 (4 fixes + tests + arnés) y documentación; ninguno se ha subido (`origin` no tiene esos commits).

## 5. Auditoría de ramas: `feature/autonomous-village` vs `migration-neoforge-1.21.1`

- **Merge-base**: `c2fb3699f1c41bc3dc898be7e979c3b587c9025e`.
- **Exclusivos de AV**: **756** commits (todo Autonomous Village: navegación, asedio, C4, campañas).
- **Exclusivos de NeoForge**: **6** commits, todos **A1 (guardias de fundación)**:
  - `35d539e4` I389 · cuatro guardias desde el principio (arreglo parcial de milicia).
  - `c5f14507` I390 · los cuatro guardias de fundación (VillageManager +245 líneas).
  - `8655f4f8` I390 (bis) · Javadoc del equipo de guardia.
  - `7c193f18` · encargo A1 escrito en `docs/CONTINUAR.md` (se pide aplicarlo **a esa rama**, no a la autonomous).
  - `9634d06c` · cierre I391+I392+I393 (docs).
  - `7c3e9585` · A0/oso (docs).
- **AV ya es NeoForge 1.21.1**: `build.gradle` usa `net.neoforged.moddev 2.0.146` y el entorno es NeoForge 21.1.249 / MC 1.21.1; la migración está en la **base común**. Lo que falta no es «migrar AV a NeoForge», sino **decidir si el trabajo A1 de la otra rama se porta a AV** (y en qué orden).
- Riesgo de integración: los 6 commits son pequeños y de bajo acoplamiento con AV (mundo/aldea, docs); el `VillageManager` de AV ha cambiado desde `c2fb3699` (C4: reconciliación, ventana de carga, reparto aplazado), así que un cherry-pick directo de A1 exigiría revisión, pero es viable.
- **No se ha hecho** merge/rebase/cherry-pick (restricción de la fase).

## 6. Auditoría NeoForge: la instrucción de `docs/CONTINUAR.md` L2

- **Qué pide**: mantener `feature/autonomous-village` sincronizada con `migration-neoforge-1.21.1`.
- **Por qué existe**: la migración se desarrolló en esa rama; la autonomous nació de la base migrada y se pidió no divergir.
- **Realidad**: AV divergió 756 commits (todo AV) y la otra rama 6 (A1). La sincronización **no se hizo**; ambas siguen sobre la base `c2fb3699`.
- **Alternativas** (evaluadas):
  1. **Mantener separadas temporalmente** (estado actual): riesgo de divergencia creciente; coste de integración futuro.
  2. **Migrar primero la base** (la base ya es NeoForge): no aplica; ya está.
  3. **Portar AV a la otra rama**: coste enorme (756 commits) y conflictos en `VillageManager`.
  4. **Cherry-pick selectivo de A1 a AV** (6 commits, con revisión de `VillageManager`): **menor riesgo** si el dueño quiere A1 en AV.
  5. Estrategia recomendada: **AV como línea autoritativa de Autonomous Village**; portar A1 a AV por cherry-pick revisado (o reimplementarlo allí) **solo si el dueño lo pide**; no rebasar AV.
- **Inventario**: los 6 hashes de §5; sus ficheros: `VillageManager.java`, `docs/CONTINUAR.md`, `docs/aldea-invariantes.md`.

## 7. Estado de navegación (`01-navigation-architecture.md` §K/§L)

| Elemento | Clasificación | Evidencia / recomendación | Prioridad |
|---|---|---|---|
| §K.1 política de ramas | **DECISIÓN PENDIENTE (dueño)** | §6; aplicar cherry-pick selectivo si procede | Alta (decisión) |
| §K.2 CONTINUAR §5.6 (aldeano vs zombi) | **RESUELTO** | ámbitos distintos; documentado en el cierre A2 | — |
| §K.3 `aldea-cerebro.md` (fase retirada) | **DECISIÓN PENDIENTE (baja)** | confirmar que la reutilización de §I no es un regreso | Baja |
| §K.4 numeración de documentos | **RESUELTO** | numeración `01-` adoptada y en uso | — |
| §L identidad canónica del sucesor | **RESUELTO** | `NavPlanner.sucesorCanonico` + tests (`NavPlanner...` en policyTest) | — |
| §L límites de DROP/BREAK/BUILD y combinaciones no probadas | **LIMITACIÓN CONOCIDA + DEUDA TÉCNICA** | el sistema pasa A2 con evidencia; no hay defecto reproducible abierto; faltan pruebas de combinaciones | Media |
| §L «un PASS aislado no es aceptación A2» | **RESUELTO** | 3 corridas consecutivas aceptadas | — |
| Navegación global/local, A*, movimiento, lease, BREAK/BUILD/DROP | **RESUELTO (A2)** | 17 correcciones con evidencia; informe A2 §4 | — |
| Bimodalidad 7/8 vs 4/8 (observada en la campaña) | **DEUDA TÉCNICA (no bloqueante)** | A2 aceptado con 3 corridas; documentada | Media-baja |
| Rendimiento (tope 30 ms/plan, 8000 expansiones globales) | **LIMITACIÓN CONOCIDA** | documentada; sin umbral de rendimiento acordado | Media |

## 8. Estado de persistencia y aldea (C4)

| Elemento | Estado | Nota |
|---|---|---|
| Identidad de aldea/centro/profesiones/almacén | **RESUELTO** | C5 previo + C4 |
| Save/Reload de trabajadores (identidad, goals, reanudación) | **RESUELTO** | 3 campañas |
| Un puesto, un dueño (memoria + ticket) | **RESUELTO** | `VillageJobSiteOwnershipPolicy` + reconciliación |
| Repoblación antes de la carga | **RESUELTO** | `VillagePopulationLoadPolicy` + ventana |
| Goals que liberan tickets ajenos | **RESUELTO** | `puestoReclamadoPorOtro` |
| Reparto durante la carga | **RESUELTO** | aplazado 1200 ticks |
| Duplicado intermitente (020) | **RESUELTO en lo demostrado** | causa: mantenimiento del arnés antes de la carga; arnés corregido + puertas de producción |
| Riesgo residual de duplicado por rutas vanilla no instrumentadas | **DEUDA TÉCNICA (monitorizar)** | la política se aplica en el reparto y en los goals; el cerebro vanilla no se intercepta; 3 campañas sin duplicados |
| Tests de persistencia | **RESUELTO (parcial)** | 2 tests puros; faltan pruebas de integración NBT/save-reload automatizadas en `test` |

## 9. Reconciliación histórica

- Los **17 defectos A2 resueltos** están en el informe A2 §4 y no reaparecen como pendientes.
- Las correcciones de navegación están en los informes por ronda (NEXT-SESSION histórico) y en el informe A2.
- Las correcciones de ownership/POI están en el informe C4 y en este documento.
- La política un-puesto-un-dueño está documentada en `VillageJobSiteOwnershipPolicy` (javadoc) y en el informe C4.
- El orden de carga y «no mantener antes de la carga» están documentados en `VillagePopulationLoadPolicy` y en el arnés.
- Los checkpoints marcaban A2 OPEN históricamente; los banners de cierre (A2/C4) los marcan como históricos sin reescribir informes.
- **Contradicciones detectadas**: `docs/CONTINUAR.md` L2 sigue pidiendo sincronizar con `migration-neoforge-1.21.1` (§K.1); se deja anotado como decisión del dueño, no como defecto.

## 10. Política del muro (`5ad02088`) — auditoría

Código (`AggressiveZombieEntity.romperNodoDeRuta`): la exención se aplica **solo** si
`esLaMuralla(nodo, cota)` **y** `60.5 ≤ distancia horizontal al objetivo ≤ 65`, con `tunelEspera` (cadencia)
respetada; fuera de esa banda el contrato de presupuesto es el original (40 bloques) y `breakBlockAt` sigue
pasando por protección/lease/soporte. No modifica AABB/headroom. **Conclusión: se conserva**; no hay
evidencia de defecto y permitió la aceptación A2. Documentada en el informe A2 §5.

## 11. Full12K

No existe una definición independiente de «Full12K» en scripts, informes ni checkpoints distinta de las
corridas de **12000 ticks** ya aceptadas (A2 y C4). **No constituye una tarea pendiente adicional.**

## 12. Contrato de finalización de Autonomous Village

Se considera **terminada la etapa actual** cuando se cumplan, con evidencia:

**Obligatorio (ya cumplido):**
1. Generación de aldeas e identidad persistente (centro, profesiones, almacén) — C5 + C4.
2. 12 puestos, asignación y propiedad única (memoria y ticket) — C4.
3. Save/Reload con 12/12 identidades, goals y reanudación; dos ciclos — C4 (3 campañas).
4. Navegación global y local con A*, BUILD/BREAK/DROP y autoridad única de movimiento — A2.
5. Asedio A2: 8/8 estricto en 3 corridas con C4 integrado — A2.
6. Protección de estructuras, lease, soporte, AABB, headroom y presupuestos intactos — A2/C4.
7. Recuperación acotada y sin watchdog en las corridas aceptadas — A2.
8. MC 1.21.1 / NeoForge 21.1.249 — build actual.

**No pertenece a esta etapa (sistemas futuros del mod):** progresión de enemigos, fortaleza,
`02-CUSTOM-BRAIN-VISION`, `03-TERRAIN-GEOMETRY-VISION`, `04-ENEMY-PROGRESSION-FORTRESS-VISION`.

**Criterio de cierre documental:** informe de aceptación A2 + C4 publicados, checkpoints coherentes,
§K/§L clasificados y hoja de ruta aprobada por el dueño.

## 13. Hoja de ruta priorizada (sin implementar en esta fase)

| # | Etapa | Objetivo | Módulos | Riesgo | Pruebas | Aceptación |
|---|---|---|---|---|---|---|
| 1 | **Decisión de ramas** (obligatoria) | Resolver §K.1: AV autoritativa + A1 por cherry-pick o reimplementación | `VillageManager`, docs | Medio | revisión de diff + compile/lint | decisión escrita del dueño |
| 2 | **Portar A1 (si se decide)** | 4 guardias de fundación en AV sin tocar los 12 puestos | `VillageManager`, `VillageGenerator` | Medio | policyTest + arnés de aldea | 4 guardias + 12 puestos intactos |
| 3 | **Monitorizar duplicados** | Cerrar la deuda técnica de la ruta vanilla | arnés C4 + política | Bajo | 3 campañas extra | 0 duplicados en N campañas |
| 4 | **Cobertura de tests de persistencia** | Pruebas NBT/save-reload en `test` (no solo puros) | tests | Bajo | `test` | tests verdes y deterministas |
| 5 | **Combinaciones DROP/BREAK/BUILD** (§L) | Cubrir los límites no probados | tests/arnés | Bajo-medio | policyTest/arnés | matriz documentada |
| 6 | **Rendimiento** | Umbral de plan/expansiones y medición | NavPlanner/NavExecutor | Medio | arnés | umbral acordado y medido |
| 7 | **Documentación** | Cerrar §K.2/K.3/K.4 y el contrato | docs | Bajo | lint | docs coherentes |

**Orden**: 1 (decisión) → 2 (si procede) → 3/4 (deuda) → 5/6 (opcionales) → 7 (continuo).
No cosmética mientras haya decisiones pendientes.

## 14. Riesgos

- **Divergencia de ramas** creciente (756 vs 6): el coste de integración futuro aumenta; mitigar con la decisión 1.
- **Ruta vanilla de asignación de POI** no interceptada: residual, mitigado por reparto/goals y 3 campañas.
- **Bimodalidad de A2** (7/8 vs 4/8 observada en campaña): aceptada con 3 corridas; documentar si se pide estabilidad extra.
- **Watchdog de generación de mundo** (hostil): riesgo de entorno, no de producción.
- **Documentación histórica** que aún dice «A2 OPEN»: mitigada con banners; no borrar.

## 15. Decisiones que requieren al dueño

1. **Estrategia de ramas**: ¿AV autoritativa y A1 portado por cherry-pick/reimplementación, o mantener separadas?
2. **A1 (guardias de fundación)**: ¿se quiere en AV? (el encargo histórico lo pedía para la otra rama).
3. **§K.3**: confirmar que la reutilización de infraestructura para aldeanos no reabre la fase retirada.
4. **Política del muro**: se propone **aceptarla tal cual** (sin cambios).
5. **Alcance de «terminado»**: aprobar el contrato de §12 como cierre de la etapa actual.

---

### Apéndice · Commits del cierre documental de esta auditoría

Se registrarán en el commit de este informe (solo docs). A2/C4 permanecen inalterados; producción y tests
no se han modificado en esta fase.
