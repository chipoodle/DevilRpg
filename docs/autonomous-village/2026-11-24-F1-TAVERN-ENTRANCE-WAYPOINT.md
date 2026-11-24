# F1 — Entrada a la taberna: primera puerta exterior y el choke sur (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1545` / `av-f1-20261124-1605` / `av-f1-20261124-1630`
**Rama:** `feature/autonomous-village`
**Código del diagnóstico:** `80c0e76e` / `d0a282f2`
**Código de la corrección:** `39b5bf53` (waypoint) y `2ebf8ef4` (solo puerta oeste)
**Origen:** `2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`.

---

## 1 · El problema que se quería resolver

El 72k con mesa por aldeano (`soak-f1-table-diag`, 13 muertes) mostró que las mesas de la taberna
(`dark_oak_fence` + `oak_pressure_plate`) no tenían una casilla de pie alcanzable desde el este:

```text
no consigue llegar a 699,63,551 desde 721,63,557
(ruta=6 nodos hasta 717,63,561 alcanza=NO; destino=dark_oak_fence encima=oak_pressure_plate)
```

y el aldeano se quedaba en la puerta abierta `703,63,553` (`nextBlock=dark_oak_door open=true`).
No era hambre (comida 56) ni el destino único: era el **acceso**.

## 2 · Corrección: entrar por una casilla exterior de la puerta

`VillageGenerator.entradaExteriorDeLaTaberna` devuelve una casilla de pie **fuera** de la puerta
(oeste o sur, la más cercana); `VillagerTavernGoal.tick` la usa como primer tramo: si el aldeano no
está dentro de la huella de la taberna, camina a esa casilla; al pisarla, `enEntrada=true` y el
siguiente tramo es su mesa. No se come en la puerta. `dentroDeLaTaberna` distingue dentro/fuera.

La primera versión usaba **oeste o sur**. En el 72k `soak-f1-waypoint` los aldeanos del sur se
atascaron en `701,63,581` (sur de la taberna), con comida 173; 10 muertes, 8 de ellas
`Yendo a la taberna`. El waypoint sur los llevaba a una zona de terreno desfavorable. Se restringió a
la **puerta oeste/plaza** (`2ebf8ef4`), que es la principal.

## 3 · Evidencia

| Run | Código | Ticks | Resultado | Población | Comida | Bajas |
|---|---|---:|---|---:|---|---:|
| `soak-f1-table-diag` (sin waypoint) | `80c0e76e` | 72.000 | `SOAK_FAIL` | 18 | 56 | 13 |
| `soak-f1-waypoint` (oeste+sur) | `39b5bf53` | 72.000 | `SOAK_PASS_CON_BAJAS` | 20 | 173–? | 10 |
| `soak-f1-west` (solo oeste) | `2ebf8ef4` | 72.000 | **`SOAK_FAIL`** | 22 | 164–? | **8** |

El 12k con waypoint y `-Diag` (`soak-waypoint-diag`) dio **0 muertes**, población 30, comida 268–300,
pero falló por **1 huérfano transitorio** (`huerfanos_max=1`), no por hambre.

El 72k oeste bajó a **8 muertes** (de 13 sin waypoint), población 22, comida 164, 11/11 al final, 0
ausentes, huérfano/sin puesto **transitorios** (1 muestra). Las bajas ya no están en el sur
(`701,581`) sino en `709-728,63,541-578`, todas `Yendo a la taberna` (más una caída): el **terreno/ruta
del este** sigue siendo el choke, no la puerta ni la despensa.

**Conclusión:** el waypoint oeste mejora (13→8) pero **no cierra F1**. La puerta ya no es el problema
inmediato: la ruta desde el este/sureste cruza terreno abrupto y los aldeanos no llegan. La siguiente
campaña debe atacar la **ruta/terreno** (plaza/road waypoints o navegación), no el hambre.

## 4 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | El aldeano entra por una casilla exterior, no contra el muro | `entradaExteriorDeLaTaberna` + `enEntrada` | PASS (código) |
| 2 | No se come en la puerta | `distancia <= REACH && !destino.equals(objetivo)` → solo `enEntrada` | PASS (código) |
| 3 | El waypoint sur no es un choke | `soak-f1-waypoint`: 10 muertes en `701,581` | **NO**; se retira el sur |
| 4 | El waypoint oeste no mata | `soak-f1-west`: 8 muertes, todas `Yendo a la taberna` | **NO** |
| 5 | Sin regresiones | `test` 1m18s, lint, diff | PASS |

## 5 · Límites

- Sin test headless (necesita `ServerLevel`); validación runtime.
- La ruta del este y el terreno abrupto siguen siendo frágiles; el `SOAK_STUCK`/`TAVERNDIAG` queda
  listo para el siguiente choke.
- No se declara F1 cerrado hasta el 72k con **0 muertes**.
