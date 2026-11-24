# Soak multi-día — herramienta, campaña y hallazgos (24-nov-2026)

**RUN_ID:** `av-soak-20261124-1130` … `av-soak-20261124-1120`
**Rama:** `feature/autonomous-village`
**Código de producción probado (fix animal):** `552e9b571256421398bb2e50fc7e9e42eab92a4b`
**Herramienta:** `tools/soak/SoakHarness.java` + `tools/soak/lanzar-soak.ps1` (opt-in
`-PsoakHarness=true`; excluye `debug/**`, compila `tools/soak`).
**Mundos:** `build/soak/<run>/game` (desechables; no se tocan `run/saves` ni mundos históricos).

Este documento **no declara aceptado el soak**: la herramienta funciona y el contrato de estabilidad
pasó, pero la campaña de 3 días encontró **muertes por hambre** en una aldea de 16 bocas. Eso es un
hallazgo abierto, no un PASS limpio.

---

## 1 · Qué mide el arnés

Un solo entry point, sin raid y sin jugador. Genera una aldea con `VillageManager.preGenerate`, fuerza
los chunks, ejecuta `VillageManager.ejecutarMantenimientoParaArnes` cada 200 ticks (la ruta real de
`tickVillageLife`) y corre `TICKS` ticks de ventana con **sprint** de Minecraft. Cada 200 ticks muestrea:

- población, 12 profesionales, `JOB_SITE` válido, duplicados, huérfanos;
- inactividad (sin goal y sin moverse; solo de día; 2.400 ticks = aviso, 12.000 = permanente);
- comida de despensa, leña, hierro, total del almacén;
- muertes por `LivingDeathEvent` (UUID, profesión, posición, causa);
- al cerrar: `saveEverything`, `soak.json`, `muestras.csv`, `fin.txt`, parada limpia.

Contrato completo en `tools/soak/LEEME.md`.

---

## 2 · Campañas

| Run | Ticks | Resultado | Población | Cobertura | Hallazgo |
|---|---:|---|---:|---:|---|
| `soak-smoke-20261124-1110` | 1.200 | FAIL | — | — | censo 11/12: el arnés contaba NITWIT como no-profesional; **defecto del arnés**, corregido |
| `soak-smoke2-20261124-1125` | 4.000 | PASS | 16 | 11/11 | primera validación del arnés |
| `soak-20261124-1130` | 72.000 | FAIL | 16 | 11/11 | `inactivos_largos=4` **acumulados**; la muestra final tenía 0. **Defecto del arnés** (métrica acumulativa), corregido |
| `soak-diag-20261124-1150` | 12.000 | FAIL | 15 | 10/11 | **Zacarías (Ganadero) asfixiado en 708,55,533** mientras `Recogiendo el corral`; **defecto real de producción** |
| `soak-diag2-20261124-1200` | 12.000 | PASS | 16 | 11/11 | tras el fix del ganadero: 0 muertes, 0 inactivos |
| `soak-20261124-1208` | 72.000 | PASS (criterio antiguo) | 15 | 10/11 | 1 muerte por hambre (Ximena, leñador); 0 duplicados/huérfanos; guardado OK |
| `soak-final-20261124-1120` | 72.000 | **SOAK_PASS_CON_BAJAS** | 11 | 8/11 | **5 muertes por hambre**; 0 duplicados/huérfanos; 0 inactivos permanentes; guardado OK |

Los runs con `FAIL` se conservan; no se borran ni se reescriben. El `sha.txt` de `soak-final` es
`552e9b57` (incluye el fix del ganadero). Los runs anteriores a ese commit son diagnósticos.

---

## 3 · Hallazgo 1 — asfixia del ganadero (corregido)

En `soak-diag-20261124-1150`, el ganadero murió dentro de una valla del corral:

```text
Zacarias (Ganadero) Recogiendo el corral ... x=708.50, y=55.00, z=533.50
message: '... suffocated in a wall'
```

`VillagerAnimalFarmGoal.tick()` solo llamaba a `desatascarSiEstaEncajado` en la rama de navegación.
Cuando ya estaba en el destino (recogiendo) y un animal lo empujaba contra la valla, no lo comprobaba;
el latido de aldea lo hace cada 10 s, demasiado tarde.

**Fix:** llamada a `VillageManager.desatascarSiEstaEncajado(villager)` al principio de **cada tick**
(mismo patrón que leñador, granjero, recolector, clérigo y recogida). Detalle:
`2026-11-24-ANIMAL-FARM-SUFFOCATION-FIX.md`.

**Evidencia:** `soak-diag2` (12k) y `soak-final` (72k) **no** reprodujeron la asfixia. La muerte es
estocástica (empujón de un animal); el patrón por tick es el mismo que ya usan los demás oficios.

---

## 4 · Hallazgo 2 — hambre: 5 muertes en 3 días (ABIERTO)

El run final (72.000 ticks, 3 días de juego) terminó con:

- **5 aldeanos muertos de hambre**: Nicasio (granjero), Anselmo (recolector/NITWIT), Aurelia (leñador),
  Bibiana (granjero) y Gervasio (guardia). Todos con el mensaje previo
  `[Village] Un aldeano de la aldea 0 ha muerto de hambre (10 min en hambre critica)`.
- Población 16 → **11**; 4 profesionales del censo ausentes; cobertura 11 → **8/11**.
- Comida de despensa: mínimo **6**, máximo 77, final 19. En varios momentos el log dice
  `16 boca(s) sin su racion y 42-63 punto(s) en la despensa`.
- Leña 100, hierro 0, almacén 280; **0 duplicados, 0 huérfanos, 0 inactivos permanentes**, guardado OK.

**Interpretación:** no es un fallo de asignaciones ni de `JOB_SITE` (el libro se limpió en cada muerte,
L1). La comida en la despensa (42-63 puntos) no era el único problema: los cinco murieron **mientras
intentaban comer**. Evidencia de las últimas trazas:

| Víctima | Profesión | Posición | Goal / traza |
|---|---|---|---|
| Nicasio | granjero | 630,63,530 | `VillagerTavernGoal`, `nav=[36 nodos, anda=true, llega=false]`, 2 s sin moverse, `cerebro=688,63,549` |
| Anselmo | recolector (NITWIT) | 630,63,531 | `VillagerTavernGoal`, `nav=[37 nodos, anda=true, llega=false]` |
| Aurelia | leñador | 628,66,529 | repetidos `estaba METIDO en la puerta 628,66,529: se la abro para que salga` hasta morir |
| Bibiana | granjero | 630,63,530 | `VillagerTavernGoal`, `nav=[34 nodos, anda=true, llega=false]` |
| Gervasio | guardia | 603,63,534 | `VillagerGuardGoal`/`VillagerGateGoal`, `sin ruta`, murió de hambre de guardia |

No es solo "poca comida": hay un **cuello de botella de navegación hacia la taberna** en `630,63,530`
(y una puerta en `628,66,529` que se abre pero no le deja salir). Además:

- La aldea tiene **4 camas para 16 bocas**; la comida antes de dormir (`comerAntesDeDormir`) solo
  alcanza a quienes duermen.
- `repartirRaciones` marca `ultimaComida` de las bocas, pero **no llama a `reducirHambre`**: el hambre
  personal no baja por esa ración; solo baja en la taberna o antes de dormir.
- `tieneHambre`/la taberna exige **1 h desde la última comida**, mientras el hambre sube 1 punto por
  latido de aldea (10 s): el aldeano llega a crítico mucho antes de que el goal vuelva a aceptarlo.
- El arnés llama a `tickVillageLife` cada 200 ticks, la misma cadencia de producción; el hambre
  observada no es un artefacto de frecuencia del arnés.

**Causas candidatas a demostrar (no todas confirmadas):** (a) atasco de ruta en el acceso a la taberna
(puerta/valla/giro), (b) raciones que no reducen el hambre personal, (c) puerta de comida de 1 h frente
a +1 de hambre/10 s, (d) solo 4 camas para 16. La siguiente campaña debe medirlas por separado antes de
tocar balance.

**No se ha corregido en esta campaña.** Es un objetivo nuevo, no una relajación del soak:

1. Medir producción/consumo y **eventos de comida** (tavern/bedtime) por día con el CSV y el log.
2. Determinar si el problema es **producción**, **reparto**, **ruta a la taberna**, **camas** o
   **cadencia de hambre**; corregir la causa demostrada sin debilitar C1.
3. Repetir el soak; el criterio propuesto es **0 muertes por hambre** en 3 días, no solo "comida > 0".

**Limitación del arnés:** sin jugador, la reposición de aldeanos (`VillageManager.manageNearby`) **no
se ejecuta**; una baja deja la cobertura en 10/11 o menos. El arnés reporta `ausentes_fin`, no la
convierte en PASS de cobertura. C4 cubre 12/12 y 11/11 con jugador/fixture.

---

## 5 · Otros criterios del contrato

| Criterio | Run final | Resultado |
|---|---|---|
| 12/12 profesionales censados | 12 | PASS |
| Duplicados de `JOB_SITE` | 0 | PASS |
| Huérfanos (`POI` inexistente/fuera del muro) | 0 | PASS |
| Profesional **presente** sin puesto válido | 0 | PASS |
| Inactividad permanente (12.000 ticks) | 0 | PASS |
| Comida nunca negativa; no 0 >30 muestras | 6–77, 0 muestras a 0 | PASS |
| Población final >0 | 11 | PASS |
| Guardado real | `saveEverything=true` | PASS |
| Excepciones/watchdog | ninguna | PASS |
| **Muertes por hambre** | **5** | **FALLO de sostenibilidad** |
| Reposición de bajas | no ejecutada (sin jugador) | **NO MEDIDO** |

---

## 6 · Veredicto

- **Herramienta de soak:** implementada, aislada, reproducible y con evidencia en
  `build/soak/**`; el smoke y dos diagnósticos validan el instrumento.
- **Contrato de estabilidad (asignaciones):** PASS en el run final: 0 duplicados, 0 huérfanos,
  0 inactivos permanentes, guardado OK.
- **Sostenibilidad alimentaria:** **NO ACEPTADA**. 5 muertes por hambre en 3 días con comida en la
  despensa. No se declara el soak completado ni la aldea autosuficiente.
- **Asfixia del ganadero:** corregida y no reproducida en los runs posteriores.

El siguiente objetivo es la **economía de comida a 16 bocas** (producción/reparto/dotación inicial),
con el soak como criterio de aceptación: 3 días, 0 muertes por hambre, 0 duplicados/huérfanos,
0 inactivos permanentes y guardado OK.
