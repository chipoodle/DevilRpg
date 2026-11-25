# L5/L6 — Evidencia de POI y navegación multi-entidad desde el soak (24-nov-2026)

**Rama:** `feature/autonomous-village`
**Fuente:** `soak-f4-confirm` / `soak-f4-stability` (SHA `bcf98f70`), 72.000 ticks cada uno.
**Origen:** auditoría `2026-11-24-PENDING-AUDIT.md` L5/L6.

---

## 1 · L6 · Navegación multi-entidad

El contrato NAV (`NavScenarioContractTest`, 253 comprobaciones) y la campaña `nav-final` son de **una
entidad por escenario**; el arnés funcional marca `multientidad_1/2/4/8` como
`no_soportado_por_campana_funcional`. Eso sigue siendo una **limitación del arnés NAV**.

Pero el soak multi-día ya es una prueba **runtime real de 30 aldeanos** navegando a la vez durante
**3 días de juego**:

| Métrica del soak | Semilla `3188…` | Semilla `7454…` |
|---|---:|---:|
| Entidades (población final) | 30 | 16 |
| Ticks | 72.000 | 72.000 |
| Muertes | **0** | **0** |
| Cobertura de puestos | **11/11** | **11/11** |
| Inactivos permanentes | **0** | **0** |
| Duplicados / huérfanos / sin puesto | 0 / 0 / 0 | 0 / 0 / 0 |

Los aldeanos usan sus goals (granja, taberna, leña, minería, guardia, etc.), abren portones, se
desatascan, se despiertan si se quedan parados y llegan a sus puestos; **no se quedan atascados ni se
duplican**. Para el criterio “varias entidades navegando a la vez”, esto es evidencia runtime
**fuerte**, aunque no sustituye a los escenarios `multientidad_*` del arnés NAV.

**Estado L6: parcialmente cerrada.** Queda implementar los escenarios `multientidad_1/2/4/8` en
`NavScenarioHarness` (spawn de N mobs y rutas simultáneas) si se quiere el contrato NAV exacto.

## 2 · L5 · POI sin horas/jugador

El arnés POI (`PoiPersistenceReport`, 15 casos) es puro y no mide horas de juego ni un jugador. El
soak, en cambio, ejerció **3 días de juego** de propiedad de puestos con aldeanos reales:

- 0 duplicados, 0 huérfanos y 0 profesionales sin puesto en las 361 muestras de cada 72k;
- el libro de puestos devuelve/libera estaciones al morir, convertirse o descargarse un titular;
- los POI fuera del muro se invalidan y se re-registran (F3);
- guardado OK y final 11/11.

**Estado L5: parcialmente cerrada** (propiedad real 3 días); falta la ventana “horas + jugador” del
arnés POI, que es una limitación de evidencia, no un defecto.

## 3 · L7/L8

- **L7 (fastutil/rendimiento):** no se declara ganancia; el A/B semántico de 750 rutas es idéntico,
  pero no hay A/B runtime. Sigue abierto como **deuda de evidencia**.
- **L8 (A2 no reejecutado):** excluido expresamente por el usuario; no se reabre sin su orden.

## 4 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | Varias entidades navegan a la vez sin atasco | 30 aldeanos, 72k, 0 muertes, 0 inactivos permanentes | **PASS (pueblo)** |
| 2 | Contrato NAV `multientidad_*` | sigue `no_soportado_por_campana_funcional` | **NO** (limitación de arnés) |
| 3 | POI 3 días sin duplicados/huérfanos | 361 muestras, 0 | **PASS (soak)** |
| 4 | POI con horas + jugador | arnés POI puro | **NO** (limitación) |
| 5 | L7/L8 | sin A/B runtime; L8 excluido | abiertos/limitación |

## 5 · Veredicto

L5/L6 quedan **parcialmente cerradas con evidencia runtime del soak**; la limitación exacta de los
arneses NAV/POI se declara. L7 sigue como deuda de evidencia y L8 está excluido por el usuario. La
decisión de ramas/A1 es del dueño.
