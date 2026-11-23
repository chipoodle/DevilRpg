# A1 — AUDITORÍA FORENSE DE LOS SEIS COMMITS EXCLUSIVOS DE `migration-neoforge-1.21.1`

> **RESULT: COMPLETE.** Fase de investigación: sin cambios de producción, tests ni operaciones Git destructivas.
> Fecha: **2026-11-23**.

---

## 1. HEAD y estado de ambas ramas (verificado)

| Rama | HEAD completo | Estado |
|---|---|---|
| `feature/autonomous-village` (autoritativa) | `fd58adefc39c90531b8d625abac2f866986f3fb2` | limpia; 34 commits por delante de origin |
| `migration-neoforge-1.21.1` | `7c3e9585428a0e63f313260bd9508e2f5e9752c9` | worktree separado `D:/Documents/DevilRpg`; 6 commits por encima |
| Base común | `c2fb3699f1c41bc3dc898be7e979c3b587c9025e` | — |

Exclusivos: **AV 756** / **NeoForge 6**. AV ya es NeoForge 1.21.1 (moddev 2.0.146 / neoforge 21.1.249).

## 2. SHA completos de los seis commits

| # | SHA completo | Tipo | Título abreviado |
|---|---|---|---|
| 1 | `35d539e490e7a8e51fad773973688a3b9ac26bd2` | código | I389 · cuatro guardias desde el principio (milicia mínima 4) |
| 2 | `c5f14507617224c87c03a9efdfce398e3e99bba2` | código+docs | I390 · los cuatro guardias de fundación (versión final) |
| 3 | `8655f4f82b4c8f26b33d40fabc7051f731160a7c` | código (comentario) | I390 (bis) · javadoc del equipo de la guardia |
| 4 | `7c193f184dde754ec64873219d387cbe5423e989` | docs | Encargo A1 escrito en `CONTINUAR.md` (para esa rama) |
| 5 | `9634d06c0f83a8b4631cbaf85ed6105bd6ac5253` | docs | I391+I392+I393 cerrados |
| 6 | `7c3e9585428a0e63f313260bd9508e2f5e9752c9` | docs | A0 · el oso (no verificado, no se toca) |

## 3. Análisis individual

### 3.1 `35d539e4` — milicia mínima 4 sacada de los puestos fijos

- **Objetivo**: una aldea recién fundada no tenía guardias porque la milicia salía solo de los «sobrantes»
  (aldeanos que no cubrían un puesto fijo). El commit añade `MILICIA_MINIMA = 4` y, si faltan sobrantes,
  **se lleva aldeanos que cubren puestos fijos** (les quita el puesto).
- **Archivos**: solo `VillageManager.java` (12 líneas).
- **Miembros afectados**: método de reparto de la milicia (`alistarGuardia`/reparto de sobrantes), listas
  `usados`/`sobrantes`, nuevo `cubrenPuestos`, constante local `MILICIA_MINIMA`.
- **Comportamiento**: con pocos aldeanos, 4 pasan a la milicia **a costa de puestos de profesión**.
- **Dependencias**: ninguna externa; usa el reparto existente.
- **¿Existe en AV?**: AV **no** tiene `MILICIA_MINIMA` y **no** quita titulares de puestos: sus guardias de
  fundación son **4 aldeanos adicionales** con plaza propia (`FOUNDING_GUARD_TAG`), sin tocar los 12 puestos.
- **Duplicación/contradicción**: contradice el diseño final de la propia rama (el commit siguiente
  `c5f14507` lo sustituye; el mensaje de `7c193f18` pide decidir si se retira). En AV sería un cambio de
  balance de población que puede romper el contrato C4 (12 puestos intactos).
- **Riesgo A2**: bajo (no toca navegación), pero **riesgo C4 alto**: los 12 puestos y su propiedad son
  criterio aceptado; sacar titulares los alteraría.
- **Valor verificable**: ninguno para AV (ya hay 4 guardias de fundación sin tocar puestos).
- **Clasificación: DISCARD.**

### 3.2 `c5f14507` — los cuatro guardias de fundación (versión NeoForge)

- **Objetivo**: al fundar la aldea, crear 4 guardias **adicionales** (2 espadachines + 2 arqueros), armados
  desde el almacén, con marca persistente para no repetirlos, sin tocar los 12 puestos.
- **Archivos**: `VillageManager.java` (+245), `VillageGenerator.java` (+80), `VillageSavedData.java` (+33),
  `VillageStorage.java` (+11), `docs/CONTINUAR.md` (+28), `docs/aldea-invariantes.md` (+100),
  `tools/arnes/GuardHarness.java` (+213).
- **Miembros añadidos (NeoForge)**: `GUARD_FOUNDING_TAG = "DevilRpgGuardiaDeFundacion"`,
  `GUARDIA_DE_FUNDACION = 4`, `esGuardiaDeFundacion(...)`, `repartirGuardia(...)`,
  `VillageGenerator.spawnGuardiasDeFundacion(...)`, `equiparAlGuardiaDeFundacion(...)`,
  `VillageSavedData.guardiaFundada` (`Set<Integer>`), hooks en `VillageStorage`.
- **Comportamiento**: fundación → 4 guardias en plazas fijas, equipados, marcados; la marca persistente
  evita repetirlos al cargar/repoblar.
- **Dependencias**: `35d539e4` (milicia) y `8655f4f8` (javadoc) forman parte de la misma línea; el javadoc
  describe `equiparAlGuardiaDeFundacion`.
- **¿Existe en AV?**: **SÍ, equivalente y validado por A2**:
  - `VillageManager.FOUNDING_GUARD_TAG = "DevilRpgGuardiaFundadora"`, `esGuardiaFundadora(...)` (con slot),
    `asegurarGuardiaFundadora(...)`.
  - `VillageGenerator.spawnGuardiasFundadores(level, center, objectiveIndex, saved)` con 4 plazas fijas en
    la barraca, re-equipado de fundadores existentes y `VillagerGuardGoal.equiparDesdeAlmacen(...)`.
  - `VillageSavedData.guardiasFundadoras` (`Map<Integer,Integer>` como máscara de slots creados) para no
    duplicar al cargar/repoblar.
  - Medido en A2: `founders=4/4`, `equipped=4/4` en la preparación del fixture y C4 sin pérdidas.
- **Duplicación**: dos mecanismos paralelos con **nombres y persistencia distintos** (`GuardiaFundadora` vs
  `GuardiaDeFundacion`, máscara vs Set). Portar el de NeoForge crearía **doble alta potencial** (dos marcas,
  dos spawns) o deixaría una de las dos sin usar: riesgo directo para C4.
- **Riesgo A2/C4**: A2 no usa estos métodos directamente, pero C4 mide la población (16 = 12 + 4) y la
  ausencia de duplicados/pérdidas tras dos recargas; un segundo mecanismo de fundación es exactamente el
  escenario que C4 vigila.
- **Valor verificable para AV**: **ninguno** (ya está la funcionalidad, con evidencia de aceptación).
- **Clasificación: DISCARD** (redundante y con riesgo de doble mecanismo).

### 3.3 `8655f4f8` — javadoc del equipo de la guardia

- **Objetivo**: corregir un comentario que decía que el equipo se ponía «del almacén pieza a pieza» y
  aclarar que lo pone `preGenerate` al marcar (`equiparAlGuardiaDeFundacion`).
- **Archivos**: `VillageGenerator.java` (6+/3−, solo comentario).
- **¿Aplica a AV?**: AV **no tiene** `equiparAlGuardiaDeFundacion`; su equipo se hace con
  `VillagerGuardGoal.equiparDesdeAlmacen` desde `spawnGuardiasFundadores`. El comentario sería **falso**
  en AV.
- **Clasificación: DISCARD** (obsoleto; el comentario correcto ya está en el código de AV).

### 3.4 `7c193f18` — encargo A1 en `CONTINUAR.md`

- **Objetivo**: dejar escrito, en la rama NeoForge, el encargo de los 4 guardias. El propio mensaje dice
  **«que se aplique A ESTA RAMA y no a DevilRpg-autonomous/feature/autonomous-village»**.
- **Archivos**: `docs/CONTINUAR.md` (+25).
- **¿Valor para AV?**: ninguno; es documentación de la otra rama y AV ya tiene la funcionalidad.
- **Clasificación: DISCARD** (histórico de la otra rama).

### 3.5 `9634d06c` — cierre I391+I392+I393

- **Objetivo**: cerrar tres reportes del 13-oct de la rama NeoForge con sus medidas.
- **Archivos**: `docs/CONTINUAR.md`, `docs/aldea-invariantes.md`, código ya incluido en su ancestro.
- **¿Valor para AV?**: nulo; reportes de otra línea.
- **Clasificación: DISCARD.**

### 3.6 `7c3e9585` — A0/oso (documentación)

- **Objetivo**: aclarar que lo del oso/ensillar no es un fallo verificado; solo documentación.
- **Archivos**: `docs/CONTINUAR.md`.
- **¿Valor para AV?**: nulo para Autonomous Village.
- **Clasificación: DISCARD.**

## 4. Dependencias entre commits

```text
35d539e4 (milicia mínima, parcial)
   └─ sustituido por c5f14507 (guardias de fundación, versión final)
        └─ 8655f4f8 (javadoc de c5f14507)
             └─ 7c193f18, 9634d06c, 7c3e9585 (documentación de esa línea)
```

Ningún commit posterior de la lista es utilizable sin su ancestro de código; y el ancestro (A1) ya está en AV
con otra implementación.

## 5. Comparación con la arquitectura autoritativa

| Aspecto | AV (autoritativa) | NeoForge (6 commits) |
|---|---|---|
| Marca de fundador | `FOUNDING_GUARD_TAG = "DevilRpgGuardiaFundadora"` | `GUARD_FOUNDING_TAG = "DevilRpgGuardiaDeFundacion"` |
| API | `esGuardiaFundadora(v)`, `esGuardiaFundadora(v, obj, slot)` | `esGuardiaDeFundacion(v)` |
| Creación | `VillageGenerator.spawnGuardiasFundadores(...)` | `spawnGuardiasDeFundacion(...)` |
| Equipo | `VillagerGuardGoal.equiparDesdeAlmacen(...)` | `equiparAlGuardiaDeFundacion(...)` |
| Persistencia anti-duplicado | `VillageSavedData.guardiasFundadoras` (máscara por slot) | `VillageSavedData.guardiaFundada` (`Set<Integer>`) |
| Puestos de profesión | **Intactos** (guardias adicionales) | **Intactos** en I390 (I389 sí los tocaba) |
| Evidencia | **A2 (founders 4/4, equipped 4/4) + C4** | Méritos de la otra rama; sin campaña en AV |

Conclusión: **misma funcionalidad, distinta implementación**; la de AV es la aceptada.

## 6. Análisis especial de `VillageManager` (riesgo alto)

- **Inicialización**: ambas versiones enganchan la creación de fundadores en la fundación; AV usa
  `preGenerate` + `spawnGuardiasFundadores` con máscara persistente; NeoForge usa `repartirGuardia` +
  `guardiaFundada`. Portar el segundo añadiría una **segunda pasada de fundación** sobre un mundo que ya
  tiene 4 fundadores → riesgo de 8 guardias o de marcas divergentes.
- **Persistencia/restauración**: AV guarda la máscara por slot (tolera entidades descargadas); NeoForge
  guarda un Set por aldea. Mezclar formatos sin migración puede **duplicar o perder** fundadores.
- **Ciclo de vida/chunks**: AV comprueba `isGuardiaFundadoraCreada` antes de crear y no duplica si la
  entidad no está cargada; el código NeoForge no se ha validado contra el orden de carga de C4.
- **POI/tickets/trabajadores**: el código NeoForge no toca POI directamente, pero **I389** sí altera qué
  aldeanos cubren puestos (riesgo C4). C4 es sensible a cambios en el reparto de puestos.
- **Eventos/mantenimiento**: no se observan hooks nuevos de eventos; el riesgo es de **doble mecanismo**,
  no de concurrencia.

## 7. Conflictos textuales y semánticos (simulación con `git merge-tree`)

Simulación **sin tocar el árbol** (`git merge-tree --write-tree --name-only HEAD <sha>`):

| Commit | Resultado |
|---|---|
| `35d539e4` | **CONFLICT** en `VillageManager.java` |
| `c5f14507` | **CONFLICT** en `VillageManager.java`, `VillageSavedData.java`, `VillageStorage.java`, `docs/CONTINUAR.md`, `docs/aldea-invariantes.md` |
| `8655f4f8` | **CONFLICT** (arrastra su ancestro: mismos ficheros) |
| `7c193f18` | **CONFLICT** en `VillageManager.java` (arrastra ancestro) |
| `9634d06c` | **CONFLICT** en docs y código arrastrado |
| `7c3e9585` | **CONFLICT** en docs y código arrastrado |

Todos los commits de código **conflictúan textualmente** con AV (la zona de fundación/reparto cambió con
A2/C4) y, además, **semánticamente** duplican una funcionalidad existente. Los commits de documentación
conflictúan en `docs/CONTINUAR.md` (AV lo ha reescrito en las campañas A2/C4).

## 8. Riesgos A2 y C4

- **A2 (CLOSED)**: los commits no tocan navegación ni asedio; el riesgo sería indirecto por población
  (I389 quita titulares de puestos; I390 añade 4 aldeanos). A2 mide 8 asaltantes, no trabajadores; no
  obstante, **no se debe reabrir** por una hipótesis.
- **C4 (PASS)**: riesgo **directo y alto**: C4 exige 12/12 puestos con propiedad única, sin duplicados ni
  pérdidas tras dos recargas. Un segundo mecanismo de guardias (o I389 tocando puestos) puede romperlo.
- **Rendimiento**: el código NeoForge añade pasadas de fundación; irrelevante frente al riesgo funcional.
- **NeoForge 1.21.1**: el código es de la misma versión; la incompatibilidad no es de API, es de
  **arquitectura duplicada**.
- **Carga parcial**: el mecanismo AV ya contempla entidades descargadas (máscara); el de NeoForge no está
  probado en ese escenario.

## 9. Evidencias y pruebas realizadas

**Hechos por inspección de código**: AV contiene `spawnGuardiasFundadores`, `esGuardiaFundadora`,
`guardiasFundadoras` y `equiparDesdeAlmacen`; NeoForge contiene los equivalentes con otros nombres.
**Hechos por compilación**: no se ha compilado el código NeoForge sobre AV (no se aplica).
**Hechos por tests**: los gates de AV (compileJava, compileTestJava, test, policyTest, lint) están verdes
en `fd58adef`; no se ha ejecutado nada nuevo.
**Hechos por runtime**: A2 midió `founders=4/4`, `equipped=4/4`; C4 midió 12/12 puestos, 0 duplicados,
0 pérdidas con 16 aldeanos (12 + 4).
**Hipótesis no verificadas**: ninguna necesaria para descartar; si se quisiera incorporar A1, haría falta
un mundo nuevo y dos ciclos de recarga para demostrar que no hay doble alta.

## 10. Clasificación individual

| Commit | Recomendación | Motivo |
|---|---|---|
| `35d539e4` | **DISCARD** | Contradice el diseño final (toca puestos); AV no lo necesita |
| `c5f14507` | **DISCARD** | Funcionalidad ya presente en AV con otra implementación; crear doble mecanismo |
| `8655f4f8` | **DISCARD** | Javadoc de un método inexistente en AV; comentario falso allí |
| `7c193f18` | **DISCARD** | Documentación de la otra rama; dice explícitamente no aplicarlo a AV |
| `9634d06c` | **DISCARD** | Cierres de reportes de la otra línea |
| `7c3e9585` | **DISCARD** | Documentación ajena a Autonomous Village |

## 11. Recomendación global

**No incorporar ninguno de los seis commits.** A1 (guardias de fundación armados, 4 adicionales, sin tocar
los 12 puestos) **ya está implementado y validado en AV**; los commits son una implementación paralela
anterior que conflictúa textual y semánticamente y que introduciría un segundo mecanismo con riesgo para
C4. La prioridad es preservar la aceptación A2/C4.

**Plan de integración**: ninguno (no hay cambios recomendados). Si en el futuro se quiere reutilizar algo de
la otra rama, el único candidato *documental* sería revisar `docs/aldea-invariantes.md` (AV ya tiene su
propio fichero del mismo nombre): no se recomienda portarlo sin reescribirlo contra la implementación AV.

## 12. Cambios descartados definitivamente y por qué

- **I389 (milicia desde puestos fijos)**: altera el contrato de 12 puestos, criterio C4. Descartado.
- **I390 (A1 en su versión NeoForge)**: duplica la funcionalidad existente con marcas/APIs distintas.
  Descartado.
- **Javadoc de I390**: describe una API que AV no tiene. Descartado.
- **Documentación de la línea NeoForge (3 commits)**: no aplica a AV. Descartada.

## 13. Incertidumbres que requerirían prueba adicional

- Solo si el dueño decidiera **incorporar** A1 desde la otra rama: haría falta una campaña C4 completa
  (prepare + 2 reloads, mundo nuevo) demostrando que no hay guardias duplicados ni pérdidas. No es el caso
  con la recomendación actual (descartar).
- El `GuardHarness` de la otra rama (herramienta de diagnóstico) no se ha evaluado como herramienta;
  podría inspirar pruebas de guardias en AV, pero no es un cambio de producción.
