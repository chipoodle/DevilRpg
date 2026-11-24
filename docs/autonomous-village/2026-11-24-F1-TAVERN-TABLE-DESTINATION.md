# F1 — La taberna: todos al mismo punto de apoyo de la despensa (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1455` / `av-f1-20261124-1520`
**Rama:** `feature/autonomous-village`
**Código del fallo:** `552ecfc2` (remesa 256, mesa aún no usada)
**Código de la corrección:** `80c0e76e`
**Origen:** `2026-11-24-F1-FOOD-COLLAPSE-AND-INITIAL-STOCK.md`.

---

## 1 · Síntoma medido

Con la despensa ya en **160–284 puntos** (remesa de 256), el soak de 72k del mundo que antes colapsaba
(`soak-f1-remesa-20261124-1455`) volvió a fallar:

- **16 muertes**, población 16 → 14, 6 profesionales ausentes, cobertura 11 → 9.
- **Comida mínima 160, final 160**: no fue hambre de despensa.
- 15 de 16 muertes con actividad **`Yendo a la taberna`**, en `705-722,63,549-564`; una por caída
  (`Volviendo al pueblo hit the ground too hard`, `729,63,544`).
- 0 duplicados, 1 huérfano, 1 profesional sin puesto; guardado OK.

En el run limpio de 72k (`soak-tavern-final`, mismo código sin table fix) los `TAVERNDIAG` mostraban
varios aldeanos atascados en `681,63,570` con la puerta de la taberna **abierta** durante 140 ticks. Con
16 bocas, el atasco se convierte en muerte.

---

## 2 · Causa raíz

`VillagerTavernGoal.canUse` hacía, **siempre**:

```java
destinoComida = VillagePantry.puntoDeApoyo(level, center);
usandoDespensa = true;
```

Así que **todos** los aldeanos hambrientos caminaban al **mismo punto** de apoyo de la despensa. Las
mesas (`mesaDeEsteAldeano`, repartidas por UUID) **nunca se usaban** en la práctica: el `start()` y el
`tick()` elegían `destinoComida` porque `usandoDespensa` era `true`. El comentario decía "la mesa es
preferente", pero el código lo contradecía.

El `comer` retira la ración de la despensa **de forma remota** (`VillagePantry.sacarComida(level,
center, 1, 1)`), así que no hace falta ir al cofre: basta con llegar a la mesa. Al congregar a 16
aldeanos en una celda, la puerta de la taberna se convierte en un cuello de botella y los hambrientos
mueren esperando.

---

## 3 · Corrección

`canUse` elige primero **la mesa de este aldeano** (`mesaDeEsteAldeano`, por UUID); solo si no hay mesa
o está aparcada (`esPuntoFallido`) cae al punto de apoyo de la despensa. `canContinueToUse`, al
rendirse, marca como fallido **el destino real** (`mesa` o `destinoComida`), no siempre `mesa` (que era
`null` cuando se usaba la despensa).

**Archivo:** `src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerTavernGoal.java`.

No se toca el consumo, la despensa, C1, C4, POI ni la navegación. Las mesas ya existen: `canUse` exige
`tabernaConstruida`.

---

## 4 · Evidencia

| Run | Código | Ticks | Resultado | Población | Comida | Bajas |
|---|---|---:|---|---:|---|---:|
| `soak-f1-remesa` (antes, `552ecfc2`) | sin mesa | 72.000 | **SOAK_FAIL** | 14 | 160–284 | **16** |
| `soak-f1-table` (después, `80c0e76e`) | con mesa | 72.000 | ver informe | — | — | — |

**Límite:** no hay test headless (necesita `ServerLevel`); la validación es runtime. Aún quedan 4 mesas
para 16 aldeanos (varios por mesa) y la puerta sigue siendo un punto frágil; el objetivo es que no mueran.

---

## 5 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Reproducir la congregación | `soak-f1-remesa`: 16 muertes, comida 160 | PASS (reproducido) |
| 2 | La mesa es el destino preferente | Código: `mesaDeEsteAldeano` antes de `puntoDeApoyo` | PASS (código) |
| 3 | El 72k del mismo mundo no mata | `soak-f1-table` 72k | pendiente |
| 4 | Sin regresiones | `test` 4m17s, lint, diff | PASS |

---

## 6 · Veredicto

**Defecto real corregido** (destino único vs mesas repartidas), pendiente de la campaña de 72k. No se
declara F1 cerrado hasta que el mundo que falló pase con **0 muertes**.
