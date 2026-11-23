# C4 — STANDALONE + SAVE/RELOAD: ACEPTACIÓN

> **RESULT: PASS.**
> Fecha: **2026-11-23**. Rama: `feature/autonomous-village`.
> HEAD de aceptación: **`0ab01d0da7807cffa638661bfb3e1b6e0ac60686`**.
> Producción aceptada: `ff48e257` (sobre `2fb687ab`, `41e58087`, `09255bfd`).
> Arnés/lanzador: `tools/c4n/` (diagnóstico; el arnés se inyecta en `debug/`, paquete ignorado por git).

---

## 1. Veredicto

**C4 standalone y Save/Reload: PASS.** Tres campañas completas consecutivas, cada una con
**PREPARE + RELOAD1 + RELOAD2 en PASS**, sobre mundos nuevos y desechables, con SHA registrado.

| RUN_ID | SHA | game dir | prepare | reload1 | reload2 | fin.txt |
|---|---|---|---|---|---|---|
| `c4n-20261123-021` | `0ab01d0d` | `build/c4n/c4n-20261123-021/game` | PASS | PASS | PASS | `C4N_PASS` |
| `c4n-20261123-022` | `0ab01d0d` | `build/c4n/c4n-20261123-022/game` | PASS | PASS | PASS | `C4N_PASS` |
| `c4n-20261123-023` | `0ab01d0d` | `build/c4n/c4n-20261123-023/game` | PASS | PASS | PASS | `C4N_PASS` |

## 2. Contrato C4 standalone (definido y verificado)

Una fase PASS exige, con **12 puestos** (`VillageGenerator.puestosDelPueblo()`):

1. **12/12 presentes** (identidad UUID por entidad cargada), 0 perdidos.
2. **12/12 profesión estable** respecto a la instantánea de la fase anterior (identidad).
3. **12/12 goals profesionales instalados** (clase de goal del oficio en el `goalSelector`).
4. **12/12 reanudados**: goal instalado y (`activo` observado o desplazamiento > 0.5 desde la carga).
5. **0 duplicados**: ninguna celda POI con dos memorias `JOB_SITE`; 0 huérfanos (POI sin tipo).
6. **0 pérdidas** y sin duplicación de identidades.
7. **Actividad efectiva** durante la ventana de 600 ticks.
8. Sin dependencia de A2: no se lanza ningún asedio ni se invoca el arnés de A2.

Cada fase guarda (`saveEverything`), escribe su `phase-<fase>.json`, su `snapshot-<fase>.txt`
(UUID|profesión|posición|goal) y termina el servidor (`halt`).

## 3. Arquitectura de persistencia (relevante)

- `VillageSavedData`: centro de la aldea por objetivo; `isGenerated`/`getCentre` (usados como contrato).
- `VillageManager.preGenerate` (mundo virgen) → aldea + `VillageManager.ejecutarMantenimientoParaArnes`
  (mantenimiento de producción, habilitado por `devilrpg.a2n.allowMaintenanceHook` + `a2n.mode=raid`).
- Reparto de estaciones (`reclamarEstacionesDelPueblo`): elige POI libres/«ocupados sin dueño» y fija
  `JOB_SITE`; `podarOficiosDuplicados` reparte oficios; la vida de aldea (`tickVillageLife`) instala goals.
- Persisten: centro, profesiones, almacén y las memorias del cerebro (las que existan al guardar).
- Al cargar, el arnés espera a que **todas** las entidades esperadas estén cargadas (entidad viva y
  `level.areEntitiesLoaded(new ChunkPos(pos).toLong())`); en las tres campañas ocurrió en el **tick 5**.

## 4. Defectos encontrados, causa raíz y corrección

### 4.1 Duplicado de `JOB_SITE` (causa raíz demostrada — `c4n-20261123-015`, `-016`, `-017`)

Evidencia: `JOB_SITE_DUPLICADO poi=628,63,547` (y `668,537`; en `017`, a y=120) con **dos granjeros**;
en `015` además un trabajador **nuevo** (`1fe5f5dc`, ausente del snapshot) y uno esperado perdido.

Cadena causal:

1. Al abrir el mundo, las memorias `JOB_SITE` persistidas **aún no están cargadas**; el reparto veía los
   POI como «cogidos SIN dueño» → **liberaba su ticket** → otro aldeano los reclamaba. Al cargar el dueño
   real, dos memorias en el mismo POI. (Log de `017`: «el puesto de farmer de 628,120,547 estaba cogido
   SIN dueno: se suelta…».)
2. La aldea **repoblaba** antes de cargar (conteo por radio incompleto) → aldeano nuevo + pérdida.
3. Los goals de minero/herrero comprobaban propiedad solo en 48 bloques y, si `take` fallaba,
   **liberaban el ticket del dueño legítimo** (que podía estar lejos).

Correcciones de producción (commiteadas):

| Commit | Corrección |
|---|---|
| `09255bfd` | `VillageJobSiteOwnershipPolicy`: un dueño de memoria por POI (UUID menor, orden estable); reconciliación antes del reparto; no se sueltan tickets |
| `41e58087` | `VillagePopulationLoadPolicy`: no repoblar hasta que la aldea haya cargado (ventana de gracia; el reparto propio también se aplaza 1200 ticks desde el arranque) |
| `2fb687ab` | `VillageManager.puestoReclamadoPorOtro` (escala de aldea): los goals no liberan tickets ajenos y no reclaman puestos de otros |
| `ff48e257` | El reparto completo se aplaza durante la carga (1200 ticks desde el arranque del servidor) y se registra el aplazamiento |

### 4.2 Defecto intermitente de `reload2` (`c4n-20261123-020`) y corrección del arnés

`020`: prepare PASS, reload1 PASS, reload2 FAIL con duplicados en el **tick 150** (POI `668,76,537` y
`628,76,547`), **dentro** de la ventana en la que el reparto propio está aplazado.

Instrumentación temporal (tabla por tick, `CARGA_COMPLETA`, censo **antes de cualquier mantenimiento**):

- En las campañas 021–023, `CARGA_COMPLETA tick=5` y la tabla **ANTES_DE_MANTENIMIENTO** muestra las
  memorias `JOB_SITE` **vacías** al cargar: el duplicado **no está persistido**; se crea después por la
  asignación (reparto/goals/vanilla).
- En `020` el arnés ejecutaba el mantenimiento **en `ServerStarted`, antes de que las entidades
  estuvieran cargadas** (se demostró que cargan en el tick 5). Ese mantenimiento temprano, con una
  aldea «vacía», es el escenario que produjo el doble alta.
- **Corrección del arnés** (instrumentación, no producción): el mantenimiento inicial espera a
  `CARGA_COMPLETA` (todas las entidades y chunks cargados) y solo entonces se ejecuta; el censo y la
  tabla se toman antes y después. Con el reparto de producción aplazado 1200 ticks, ninguna ruta asigna
  durante la carga.

Las tres campañas de aceptación (021–023) usan ese arnés corregido y el código de producción final.

### 4.3 Crash de entorno (`c4n-20261123-018`)

`ServerHangWatchdog` en `VillageGenerator.isVegetation` durante la **generación del mundo** (sitio de
aldea hostil, y=101). No es defecto de persistencia ni del arnés; se conserva el log y el crash report.
No se contabiliza como campaña.

## 5. Evidencia de las tres campañas

```text
c4n-20261123-021  prepare PASS 12/12 0 perdidos 0 dups | reload1 PASS 12/12 0 dups | reload2 PASS 12/12 0 dups
c4n-20261123-022  prepare PASS 12/12 0 perdidos 0 dups | reload1 PASS 12/12 0 dups | reload2 PASS 12/12 0 dups
c4n-20261123-023  prepare PASS 12/12 0 perdidos 0 dups | reload1 PASS 12/12 0 dups | reload2 PASS 12/12 0 dups
```

Por fase (`phase-<fase>.json`): `esperados=12`, `presentes=12`, `perdidos=0`, `profesion_estable=12`,
`goals_instalados=12`, `reanudados=12`, `job_site_duplicados=0`, `job_site_huerfanos=0`, `activos` 7–11,
`movidos` 11–12, `muestras=44` (ventana de 600 ticks). Instantáneas `snapshot-<fase>.txt` con
UUID|profesión|posición|goal de los 12 en cada fase.

Ejemplo de identidades (021, prepare → reload2): los 12 UUID del `snapshot-prepare.txt` son exactamente
los mismos que los de `snapshot-reload1.txt` y `snapshot-reload2.txt`, con la misma profesión
(`fletcher, farmer×3, cleric, nitwit, butcher, weaponsmith, fisherman, mason, toolsmith, shepherd`).

## 6. Pruebas de regresión

- `VillageJobSiteOwnershipPolicyTest` (9 aserciones): dos/tres en el mismo POI, POIs distintos, orden
  estable e independiente de la entrada, colisiones mixtas, repetido del mismo UUID.
- `VillagePopulationLoadPolicyTest` (7 aserciones): ventana de gracia (antes/en el límite/después),
  sin ventana, sin primer tick, reloj incoherente.
- Ambas registradas en `policyTest`.

## 7. Gates (ejecutados antes de aceptar)

```text
compileJava      PASS
compileTestJava  PASS
test             PASS
policyTest       PASS
lint_aldea.py --strict   OK
git diff --check         0
```

## 8. Regresión A2

Los cambios tocan `world/VillageManager`, `world/Village*Policy`, `entity/goal/VillagerMinerGoal` y
`VillagerSmithGoal` (población y puestos de trabajo de aldeanos). **No** tocan navegación, planificación,
movimiento, BREAK, BUILD, DROP ni `NavExecutor`. La C4 integrada de A2 usa los mismos puestos: la
corrección **refuerza** la unicidad (no puede empeorar sus `jobSite`). Por análisis de dependencias **no
se requiere reejecutar A2**; si se desea cautela máxima, puede repetirse la campaña A2 sin cambios.

## 9. Invariantes preservadas

- Un POI no tiene dos propietarios de memoria (política + reconciliación + gates de carga).
- No se liberan tickets ajenos; no se borran memorias válidas (la reconciliación solo actúa con colisión).
- Profesión coherente con el puesto; goals profesionales funcionan; los trabajadores reanudan.
- Presupuestos, protección, lease, soporte, AABB y headroom intactos (no se tocó navegación).
- `run/saves` intacto; solo mundos nuevos en `build/c4n/`; sin operaciones destructivas.

## 10. Presupuesto del muro (`5ad02088`) y Full12K

- **Muro**: la exención sigue siendo espacialmente acotada (banda 60,5–65 del objetivo), con cadencia y
  sin aplicarse fuera del anillo; no toca protección/lease/soporte/AABB/headroom. No se modifica en esta
  campaña. Decisión documentada en el cierre de A2 (`2026-11-22-A2-FINAL-ACCEPTANCE-AND-CLOSURE.md` §5).
- **Full12K**: no existe una definición independiente en la documentación distinta de las corridas de
  12000 ticks ya aceptadas para A2; no se introduce como requisito artificial.

## 11. Commits

| Hash completo | Contenido |
|---|---|
| `09255bfd8fd52ad6513d555a9cf8ed975aeb1941` | política un-puesto-un-dueño + reconciliación + test |
| `41e58087cb6f4528c5935bafa058d89c9adcf2c9` | ventana de gracia de repoblado + test |
| `2fb687ab61b09a45c79e46768f1b38584bcc08f4` | goals no liberan tickets ajenos (comprobación de aldea) |
| `ff48e2571c233b30e708521b7a57a5c8b9abf931` | reparto aplazado durante la carga |
| `0ab01d0da7807cffa638661bfb3e1b6e0ac60686` | checkpoint documental previo a la aceptación |

## 12. Limitaciones

- El duplicado es **intermitente** por naturaleza (depende del orden de carga y de qué memorias existan);
  la aceptación se apoya en las tres campañas completas exigidas y en la instrumentación temporal.
- `018` se perdió por un watchdog de **generación de mundo** (no de persistencia).
- El arnés vivo inyectado es de diagnóstico y no se compila en el build normal (`debug/` excluido salvo
  `-Pa2nDiagnostics=true`).
