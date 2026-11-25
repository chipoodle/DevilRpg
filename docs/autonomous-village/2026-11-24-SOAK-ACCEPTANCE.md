# SOAK ACEPTADO — 72k con 0 muertes y 11/11, en dos semillas (24-nov-2026)

**RUN_ID:** `av-f3-20261124-2030`, `av-f4-20261124-2115`, `av-f4-20261124-2130`
**Rama:** `feature/autonomous-village`
**SHA exacto:** `bcf98f70c69d6a11abeb60506e21a275042d1b8e` (F4 incluida)
**Resultado:** **`SOAK_PASS` en las semillas `3188…` y `7454…`** (mismo SHA)

---

## 1 · Resultado

| Métrica | Semilla `3188…` (`soak-f4-confirm`) | Semilla `7454…` (`soak-f4-stability`) |
|---|---|---|
| Ticks | 72.000 | 72.000 |
| Resultado | **`SOAK_PASS`** | **`SOAK_PASS`** |
| Población final | 30 | 16 |
| Profesionales con puesto | **11/11** | **11/11** |
| Ausentes / duplicados / huérfanos / sin puesto | **0 / 0 / 0 / 0** | **0 / 0 / 0 / 0** |
| Inactivos permanentes | **0** | **0** |
| Comida | 108 | 164 |
| Guardado | **OK** | **OK** |

Dos mundos distintos, el **mismo SHA**, ambos con `SOAK_PASS` y sin ninguna baja. La aceptación
multi-día queda **estable en dos semillas**.


## 2 · La causa raíz de F3

El `-PoiDiag` capturó al verdadero profesional sin puesto:

```text
SOAK_POI_INVALID uuid=a3dd7940-… prof=farmer
pos=713,63,577 job=736, 69, 537
poi=farmer block=composter[level=0]
```

Un **granjero** tenía su `JOB_SITE` en un composter **`736,69,537`, a 75 bloques del centro y fuera de
la muralla (radio 62)**. El POI era del oficio correcto, así que `puestoDeTrabajoInvalido` lo aceptaba
(al comprobar el tipo solo dentro del muro). El aldeano quedaba `sin_puesto` y el composter, huérfano,
durante cientos de muestras.

**Corrección (`bfd5113b`):** un `JOB_SITE` **fuera del muro** es inválido aunque el POI sea del oficio.
`reconciliarPuestosInvalidos` lo suelta, el libro libera el ticket y el granjero reclama una estación
**dentro** del pueblo. Con eso, `sin_puesto_bajo_max` pasó de 148–333 a **0** y el final ya no es
“11/11 solo al final”: es 11/11 siempre.

## 3 · Historia hasta el PASS

| Hito | SHA | Resultado |
|---|---|---|
| Soak inicial 72k | `552e9b57` | `SOAK_PASS_CON_BAJAS`: 5 hambres, 0 dup/huérf. |
| Ración al rendirse (F1) | `fa6a63f4` | 1 muerte, **0 hambre**; F1 cerrado |
| Caída + ración crítica (F2) | `ccee1943`/`7e2b4293` | **0 muertes**, F2 cerrado; F3 333→148 |
| POI del taller (F3 parcial) | `743b81db`/`68741d29` | 0 muertes, 11/11 final, hueco inicial 148–241 |
| **JOB_SITE fuera del muro (F3)** | **`bfd5113b`** | **`SOAK_PASS`** (semilla 3188) |
| **Inactivo sin goal (F4)** | **`bcf98f70`** | **`SOAK_PASS` en las dos semillas** |

Gates en `bfd5113b`: `test` 4m11s; persistencia **553**, ownership **54**, skills **376**; lint y
`git diff --check` verdes.

## 4 · Criterios de aceptación

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | 72k sin muertes | `bajas=0` | **PASS** |
| 2 | 11/11 en todas las muestras | `sin_puesto_max=0`, `sin_puesto_bajo_max=0`, `huerfanos_max=0` | **PASS** |
| 3 | 0 duplicados | `duplicados_max=0` | **PASS** |
| 4 | 0 inactivos permanentes | `inactivos_permanentes=0` | **PASS** |
| 5 | Guardado OK | `guardado_ok=true`, sin excepciones | **PASS** |
| 6 | Gates de código | `test` 4m11s, 553/54/376, lint/diff | **PASS** |

## 5 · Límites

- **Estabilidad multi-mundo:** confirmada en **dos semillas** con el mismo SHA `bcf98f70`
  (`soak-f4-confirm` y `soak-f4-stability`), ambas `SOAK_PASS`.
- F1/F2/F3/F4 cerrados; **siguen abiertos** L2, L5–L8, skills runtime (servidor con jugador/UI/cliente)
  y la decisión de ramas/A1 del dueño.
- La ruta/terreno y el POI siguen siendo mejorables; el aldeano come en el sitio si no llega.
