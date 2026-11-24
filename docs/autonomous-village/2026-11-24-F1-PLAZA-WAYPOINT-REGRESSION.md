# F1 — El waypoint de plaza fue una regresión: 30 muertes y reversión (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1700` (12k) / `av-f1-20261124-1705` (72k)
**Rama:** `feature/autonomous-village`
**Código probado:** `f375446b` (waypoint de plaza)
**Reversión:** `af711875` (vuelve a `2ebf8ef4`, puerta oeste)
**Origen:** `2026-11-24-F1-TAVERN-ENTRANCE-WAYPOINT.md`.

---

## 1 · Hipótesis

Los 8 muertos del 72k oeste seguían viniendo del este/sureste. El centro de la plaza es el nudo de
caminos del pueblo; se probó un primer tramo a una casilla de pie de la plaza (`center`) antes de la
puerta oeste, con la esperanza de que la ruta usara los caminos en vez del terreno abrupto.

## 2 · Resultado

- **12k** (`soak-plaza-diag`, `-Diag`): **0 muertes**, población 30, comida 270–288. Parecía bueno.
- **72k** (`soak-f1-plaza`): **`SOAK_FAIL`**, **30 muertes**, población **0**, comida **268–292** (nunca
  faltó), 12 profesionales ausentes, 0 cobertura. **Todas** las bajas, salvo una caída, con
  `Yendo a la taberna`, en `599,63,519` (oeste, ~62 bloques del centro), `666,545`, `674,535` y
  `705-728,541-578`.

El waypoint de plaza **no** llevó a los aldeanos por un camino seguro: los mandó al **oeste** (599,519)
y a la zona centro-oeste, donde se atascaron y murieron con la despensa llena. El 12k no lo vio; el 72k
lo hizo catastrófico.

## 3 · Reversión

`git revert --no-edit f375446b` → `af711875`. Se vuelve al código de `2ebf8ef4`: entrada exterior solo
por la **puerta oeste**, sin tramo de plaza. El mejor resultado conocido sigue siendo
`soak-f1-west` (8 muertes, 22 de población, comida 164), no el de plaza (30).

**No se reintroduce el waypoint de plaza.**

## 4 · Hallazgo colateral: población 30 y fletcher sin puesto

El 12k de plaza mostró población **30** y un **fletcher** (`c7230316-312c-4d91-a4e6-ad90300446f1`) con
`con_puesto=10`, `sin_puesto=1` durante 49 muestras, y 1 huérfano. El libro lo devuelve a su estación de
`610,63,508`, pero el POI no queda válido. Es un defecto **distinto** de F1 (job site/POI), que explica
por qué el soak no puede aceptarse aunque no haya muertes. Se anota para la siguiente prioridad.

## 5 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | El waypoint de plaza no empeora | 72k: 30 muertes, población 0 | **NO: regresión** |
| 2 | La reversión restaura el mejor código | `af711875` = `2ebf8ef4`; compile/lint | PASS |
| 3 | Soak 72k con 0 muertes | pendiente (oeste 8) | **NO** |
| 4 | Sin regresiones de tests | `test` 1m17s antes de revertir; compile/lint después | PASS |

## 6 · Veredicto

**Regresión revertida.** El waypoint de plaza queda descartado; la entrada oeste sigue siendo la mejor
variante (13→8), pero F1 **no está cerrado**. El siguiente objetivo sigue siendo la ruta/terreno del
este y, por separado, el POI del fletcher.
