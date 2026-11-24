# F1 — Colapso alimentario en un segundo mundo y dotación inicial de la despensa (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1420` / `av-f1-20261124-1455`
**Rama:** `feature/autonomous-village`
**Código del fallo:** `14bfeba9` (F1 ración/taberna + diagnóstico)
**Código de la corrección:** `552ecfc2` (remesa inicial 256 puntos)
**Origen:** `2026-11-24-F1-HUNGER-TAVERN-FIX.md` y `2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` §4.

---

## 1 · Lo que pasó

Tras las correcciones de ración y desatasco, el soak de 72k **en el mismo mundo** que había fallado dio
**SOAK_PASS** (`soak-tavern-final-20261124-1400`: 16 aldeanos, 11/11, 0 bajas, comida 4–66). Un
**segundo mundo** (semilla nueva, `soak-f1-stable-20261124-1420`) se hundió:

| Run | Ticks | Resultado | Población | Cobertura | Comida | Bajas |
|---|---:|---|---:|---:|---|---:|
| `soak-tavern-final` (mismo mundo) | 72.000 | **SOAK_PASS** | 16 | 11/11 | 4–66 | **0** |
| `soak-f1-stable` (mundo nuevo) | 72.000 | **SOAK_FAIL** | **1** | 1/11 | **0 (0 durante 85 muestras)** | **29** |

En el segundo run murieron 29 aldeanos; 20+ con `ha muerto de hambre` y actividades
`Yendo a la taberna`, `Comiendo en la taberna`, `Cerrando la puerta`, `Yendo a entrenar`, etc. Se
registró además una muerte por caída (`Volviendo al pueblo hit the ground too hard`, `729,64,542`) y
quedaron 1 huérfano y 1 profesional sin puesto. La despensa llegó a **0** y permaneció 85 muestras
(17.000 ticks) sin comida.

**No es un fallo de asignaciones**: 0 duplicados; el libro limpió las bajas. Es **alimento**.

---

## 2 · Causa raíz: la aldea generada nace con 20 puntos, la aceptación C1 usó 256

`VillagePantry.remesaInicial` daba:

- 12 semillas, 3 zanahorias, 3 patatas, 4 harina de hueso y **2 panes = 8 puntos**; con las verduras,
  **20 puntos** de comida.
- La aceptación Food 3.1/3.2 (`FOOD-ECONOMY-BLOCK-3.1-ACCEPTANCE.md`) se hizo con la despensa en
  **256 puntos** y crecía a 411 en 24.000 ticks. La aldea generada, en cambio, nacía con 20 para 16
  bocas.

En el mundo favorable, la granja producía lo suficiente y el colchón de 20 aguantaba; en el segundo, la
producción fue muy inferior (`FARMER-GROW=8`, `cocin=11`, `C1-MEAL=108`) y el pueblo se quedó a 0. Los
granjeros, con hambre crítica (prioridad CRITICAL de la taberna), dejan de cultivar para ir a comer; sin
comida, no hay cosecha: **espiral de hambre**. La ración diaria y el desatasco no pueden compensar un
colchón inicial 12,8 veces menor que el del fixture aceptado.

---

## 3 · Corrección propuesta

`VillagePantry.remesaInicial` pasa a **64 panes = 256 puntos** (más las semillas/zanahorias/patatas/
harina de hueso de siempre). No se toca la producción, el consumo, C1, C4 ni la navegación. Solo se
alinea la **dotación inicial** con la que la aceptación C1 ya daba por buena.

**Pendiente de validar:** `soak-f1-remesa-20261124-1455`, 72.000 ticks, **misma semilla del mundo que
se hundió** (`3188456493687723571`). Resultado: **`SOAK_FAIL`**, pero ya **no por comida**: la despensa
se mantuvo en **160–284 puntos** y, sin embargo, murieron **16 aldeanos**. Todos, salvo una caída,
mientras `Yendo a la taberna` en `705-722,63,549-564`. Eso destapó el segundo defecto: el goal de la
taberna mandaba a **todos** al mismo punto de apoyo de la despensa (`usandoDespensa=true` siempre), sin
usar las mesas. La corrección está en `2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`.

**Conclusión intermedia:** la dotación inicial de 20 puntos era una causa real del colapso (con 256 la
comida no bajó de 160), pero **no era la única**: la congregación en un solo punto de la taberna mataba
igual. El objetivo F1 sigue abierto hasta que el 72k con ambas correcciones pase.

---

## 4 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Reproducir el colapso | `soak-f1-stable`: 29 bajas, comida 0, 85 muestras | PASS (reproducido) |
| 2 | La aldea generada empieza con la dotación C1 | Código: 64 panes = 256 puntos | PASS (código) |
| 3 | El mismo mundo no colapsa con 256 | `soak-f1-remesa`: comida 160–284, pero **16 muertes** en la taberna | **NO: destapó el defecto de destino** |
| 4 | Sin regresiones | `test` 3m35s, lint, diff-check | PASS |

---

## 5 · Límites y riesgos

- **Es un cambio de balance** (más comida inicial), justificado por la discrepancia con el fixture
  aceptado; no se declara “aldea autosuficiente” hasta que el 72k del mismo mundo pase.
- Si el 72k pasa, aún queda la **granja**: la producción del segundo mundo fue baja; un colchón mayor
  puede retrasar el problema, no demostrar que la granja sea suficiente a largo plazo.
- La **puerta de la taberna** (`681,63,570` / `630,63,530`) sigue atascando aldeanos de forma
  transitoria; el `SOAK_STUCK`/`TAVERNDIAG` está listo para medirla.
- Sin test headless de `remesaInicial` (necesita contenedor); la validación es runtime (soak).
