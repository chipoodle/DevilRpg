# F1 — Hambre y taberna: la ración diaria no alimentaba y el goal no se desatascaba (24-nov-2026)

**RUN_ID:** `av-f1-20261124-1240` / `av-f1-20261124-1245`
**Rama:** `feature/autonomous-village`
**Código probado:** `0e57b5695ea83560bfc62cab0acdf5d1091fc23f`
**Origen:** soak oficial `soak-final-20261124-1120` (`552e9b57`): 5 muertes por hambre, 4/5 atascadas en
`630,63,530` y una dentro de la puerta `628,66,529`. Informe:
`2026-11-24-SOAK-MULTIDAY-CAMPAIGN.md` §4.

---

## 1 · Causas medidas

1. **La ración diaria no reducía el hambre personal.** `VillageManager.repartirRaciones` sacaba puntos
   reales de la despensa y llamaba a `marcarComida`, pero **nunca** a `reducirHambre`. El hambre solo
   bajaba en la taberna (`VillagerTavernGoal.comer`, 25 por punto) o antes de dormir
   (`comerAntesDeDormir`). Con **4 camas para 16 bocas**, la mayoría dependía de la taberna.
2. **El goal de la taberna no se desatascaba.** Los aldeanos hambrientos morían con
   `goals=[VillagerTavernGoal]`, `nav=[34-37 nodos, anda=true, llega=false]` y `2 s sin moverse` en
   `630,63,530`; uno quedó **dentro** de la puerta `628,66,529` (se abría cada 10 s, pero no salía). El
   goal solo comprobaba el desatasco en la rama de navegación, no al principio del `tick`; el latido
   general lo hace cada 10 s, demasiado tarde para el temporizador de muerte (10 min).
3. **Contexto:** despensa inicial de 20 puntos (`VillagePantry.remesaInicial`) y 16 bocas; la campaña
   de 3 días terminó con 6-77 puntos y 5 muertes. No se tocó la producción ni la despensa en este fix:
   primero se corrige que la comida que **sí** se saca alimente y que el aldeano no se quede atrapado.

---

## 2 · Corrección

### 2.1 La ración diaria alimenta de verdad

`VillageManager.repartirRaciones` ahora:

- ordena las bocas por **más tiempo sin comer** (antes no lo hacía, aunque el comentario lo prometía);
- reparte los puntos **realmente sacados** (`dados`), no los pedidos, y para en cuanto no alcanza;
- marca como comidas **solo** a las que reciben ración (antes marcaba a todas si se sacaba algo);
- `reducirHambre(v, 25 * dar)`, igual que la ración de la taberna.

Así una ración de 1 punto baja 25 de hambre; una de 4 baja 100. No se regala comida: cada punto sale
de la despensa y el `PantryCreditPolicy` sigue mandando.

### 2.2 El goal de la taberna se desatasca en cada tick

`VillagerTavernGoal.tick` llama a `VillageManager.desatascarSiEstaEncajado(villager)` al principio,
antes de navegar, igual que leñador, granjero, recolector, clérigo, ganadero y recogida. Si está dentro
de una puerta/valla, la abre o lo saca; si no, no toca nada.

**Archivos:** `VillageManager.java`, `VillagerTavernGoal.java`. No se tocó la producción de comida, la
despensa inicial, C1, C4, POI ni navegación.

---

## 3 · Evidencia

| Run | Ticks | Resultado | Población | Cobertura | Comida | Bajas |
|---|---:|---|---:|---:|---|---:|
| `soak-final-20261124-1120` (antes, `552e9b57`) | 72.000 | `SOAK_PASS_CON_BAJAS` | 11 | 8/11 | 6–77 | **5 (hambre)** |
| `soak-f1-diag-20261124-1240` (después, 12k) | 12.000 | **SOAK_PASS** | 16 | 11/11 | 20→124 | **0** |
| `soak-f1-final-20261124-1245` (después, 72k, `0e57b569`) | 72.000 | **`SOAK_PASS_CON_BAJAS`** | 15 | 10/11 | 4–80 (final 20) | **1 (hambre)** |

El run de 72k bajó las muertes de **5 a 1**, pero **no las eliminó**: la víctima,
`Zenobia (Granjero)`, murió a las `630,63,530` con `goals=[VillagerTavernGoal]` y
`nav=[35 nodos, anda=true, llega=false]` — **el mismo cuello de botella** que antes. El diagnóstico de
12k no tuvo bajas; el de 72k sí. El fix de ración y desatasco mejora la supervivencia, pero **no
resuelve el acceso a la taberna** en ese punto.

**Conclusión:** F1 queda **PARCIALMENTE RESUELTO**. La ración diaria ya alimenta y el goal se
desatasca, pero un aldeano sigue muriendo de hambre en la ruta a la taberna; el siguiente objetivo es
la **ruta/acceso** (destino, puerta, tramo y `primerTramoDelPorton`/`VillageErrands`), no el hambre.
No se declara el soak aceptado.

El diagnóstico de 12k: 0 duplicados, 0 huérfanos, 0 inactivos permanentes, guardado OK. La comida subió
de 20 a 124 (antes bajaba a 4-19 en el mismo tipo de run), lo que confirma que la ración diaria estaba
desperdiciándose para el hambre.

**Límite observado:** el diagnóstico sigue registrando aldeanos **dentro de la puerta `685,98,554`** de
la taberna (`estaba METIDO en la puerta ... se la abro`), aunque ya no mueren en 12k. La ruta/puerta de
la taberna sigue siendo un punto frágil; el fix de desatasco evita la muerte inmediata, no rediseña el
acceso. La campaña de 72k dirá si aguanta 3 días.

---

## 4 · Criterios de aceptación

| # | Criterio | Evidencia | Resultado |
|---|---|---|---|
| 1 | La ración diaria reduce el hambre | Código: `reducirHambre(25 * dar)`; 12k: comida 20→124 y 0 bajas | PASS (12k) |
| 2 | Solo se marca a quien recibe ración | Código: orden por `ultimaComida`, `restante`, `break` | PASS (revisión) |
| 3 | El goal de la taberna se desatasca cada tick | Código + 12k: sin muertes por atasco | PASS (12k) |
| 4 | Sin regresiones de asignaciones | 12k: 0 dup/huérfanos, 11/11, 0 inactivos | PASS (12k) |
| 5 | 3 días sin muertes por hambre | `soak-f1-final` 72k: **1 muerte**, mismo choke que antes | **NO** |

---

## 5 · Riesgos y límites

- **No hay test headless** de `repartirRaciones`/`VillagerTavernGoal` (necesitan `ServerLevel`); la
  evidencia es runtime (12k/72k).
- **Queda un choke real en `630,63,530`**: un granjero murió allí en el 72k (mismo punto que 4/5 del
  run anterior), con ruta de 35 nodos y `llega=false`. El bloque escaneado en la región guardada no
  aclara el atasco (el chunk no contenía el terreno vivo), así que la ruta/puerta sigue sin diagnóstico
  cerrado. El siguiente paso es un arnés de ruta de la taberna (nodos, puertas y tramo), no relajar el
  hambre.
- La despensa inicial de 20 puntos no se cambió; si el 72k falla por comida, el siguiente paso es
  producción/reparto/dotación, no relajar el hambre.
- C4/POI no se reejecutan: este cambio no toca `JOB_SITE` ni tickets.
