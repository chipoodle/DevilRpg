# F2 — Caída al volver al pueblo: protección fuera del asedio (24-nov-2026)

**RUN_ID:** `av-f2-20261124-1810` / `av-f2-20261124-1825`
**Rama:** `feature/autonomous-village`
**Código:** `ccee1943` (política de caída) + `7e2b4293` (ración a hambre crítica)
**Origen:** `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md` §5.

---

## 1 · Defecto F2

La única baja del 72k de cierre de F1 era una **caída**:

```text
Zacarias (Sin oficio) Volviendo al pueblo died
hit the ground too hard — 729,64,542
```

`VillagerReturnGoal` camina en línea recta al centro del pueblo (`caminarHacia(center)`), sin
protección ni waypoint; el terreno abrupto del este hace que el aldeano caiga. No era hambre.

## 2 · Corrección

- `VillageFallPolicy.protegeDeLaCaida(aldeanoDelPueblo, enAsedio)`:
  **un miembro del pueblo no recibe daño por caída fuera de un asedio**; en asedio la caída sigue
  contando (no se regala invulnerabilidad en la defensa).
- `VillageManager.hayAsedioEnLaAldeaDe(level, villager)`: busca el centro guardado
  (`CENTRO_TAG`) entre las aldeas generadas y consulta `hayAsedio`.
- `CommonForgeInteractionEventSubscriber.onLivingFallEvent`: si es `Villager` con `CENTRO_TAG` y la
  política lo permite, `event.setCanceled(true)`.
- No se teletransporta ni se cambia el goal de vuelta; solo se evita la muerte por el terreno.

Además, se añadió la **ración a hambre crítica** (`7e2b4293`): si el aldeano lleva 10 s en el goal de
taberna y el hambre ya es `FoodHungerPolicy.CRITICAL` (90), come una ración real en el sitio. Hacía
falta porque en `soak-f2-fall` dos aldeanos murieron de hambre **mientras la ruta reportaba avance**
(`stuckTicks` se reseteaba y nunca llegaba al tope). Con esto, la promesa de “nadie muere de hambre por
no llegar a la mesa” también cubre ese caso.

## 3 · Evidencia

| Run | Código | Ticks | Resultado | Población | Comida | Bajas | Hambre | Caída | Puesto |
|---|---|---:|---|---:|---|---:|---:|---:|---|
| `soak-f1-fallback` | `fa6a63f4` | 72.000 | `SOAK_PASS_CON_BAJAS` | 29 | 88 | 1 | 0 | 1 | 11/11 |
| `soak-f2-fall` | `ccee1943` | 72.000 | `SOAK_PASS_CON_BAJAS` | 28 | 98 | 2 | 2 | **0** | 11/11 |
| `soak-f2-final` | `7e2b4293` | 72.000 | `SOAK_FAIL` (solo puesto) | 30 | 72 | **0** | **0** | **0** | 10/11, 1 fletcher sin POI |
| `soak-poi-f3` (12k) | `7e2b4293` | 12.000 | **`SOAK_PASS`** | 30 | 288 | 0 | 0 | 0 | 11/11 |

- **F2 (caída) cerrado:** `soak-f2-fall` ya no tiene bajas por caída; `soak-f2-final` tiene **0 bajas
  totales**.
- **F1 (hambre) sigue cerrado:** 0 hambre en `soak-f2-final`.
- El único fallo restante es **F3**: un **fletcher** sin POI válido (`sin_puesto=1` durante 333
  muestras, 1 huérfano), con comida 72 y 0 muertes. El 12k de la misma semilla pasó limpio: el fallo
  de puesto es **intermitente**, no determinista.
- `SOAK_PASS_CON_BAJAS` de `soak-f2-fall` fue por 2 hambres; con la ración crítica desaparecieron.

## 4 · Criterios

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | 72k sin muertes por caída | `soak-f2-final`: 0 caídas | **PASS** |
| 2 | 72k sin muertes por hambre | `soak-f2-final`: 0 hambre | **PASS** |
| 3 | 72k sin muertes totales | `soak-f2-final`: 0 | **PASS** |
| 4 | Cobertura 11/11 en todas las muestras | fletcher `sin_puesto=1` 333 muestras | **NO (F3)** |
| 5 | Sin regresiones | `test` 1m13s/1m28s; persistencia 553 | PASS |

## 5 · Límites y siguiente (F3)

- **F3 abierta:** el fletcher (`c7230316…`) queda sin `JOB_SITE` válido en `610,63,508` durante todo
  un 72k; el 12k de la misma semilla pasa. No es hambre ni caída: es **job site/POI intermitente**.
  Puede ser el POI del fletcher no registrado tras una recarga/crecimiento de población, o un
  profesional extra sin estación.
- El soak global **no se acepta** hasta que la cobertura sea 11/11 en un 72k. No se declara la aldea
  perfecta; F1/F2 sí están cerrados por sus criterios.
- La ruta/terreno sigue siendo mejorable (el aldeano come en el sitio si no llega), pero ya no mata.
