# F1 — Hambre y taberna: la ración diaria no alimentaba y el goal no se desatascaba (24-nov-2026)

> **CIERRE (24-nov-2026):** F1 (hambre) queda **CERRADO** en `fa6a63f4` con la ración al rendirse;
> 72k con **0 muertes por hambre**. La única baja es la caída **F2**. Ver
> `2026-11-24-F1-CLOSURE-FALLBACK-RATION.md`.

**RUN_ID:** `av-f1-20261124-1240` / `av-f1-20261124-1245`
**Rama:** `feature/autonomous-village`
**Código probado (72k oficial):** `0e57b5695ea83560bfc62cab0acdf5d1091fc23f`
**Diagnóstico 12k:** mismo contenido, ejecutado antes del commit; el lanzador registró `36bb7cd0`
(la diferencia es solo el commit, no el código). La evidencia oficial es el 72k de `0e57b569`.
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
| `soak-f1-diag-20261124-1240` (después, 12k, pre-commit) | 12.000 | **SOAK_PASS** | 16 | 11/11 | 20→124 | **0** |
| `soak-f1-final-20261124-1245` (después, 72k, `0e57b569`) | 72.000 | **`SOAK_PASS_CON_BAJAS`** | 15 | 10/11 | 4–80 (final 20) | **1 (hambre)** |

El run de 72k bajó las muertes de **5 a 1**, pero **no las eliminó**: la víctima,
`Zenobia (Granjero)`, murió a las `630,63,530` con `goals=[VillagerTavernGoal]` y
`nav=[35 nodos, anda=true, llega=false]` — **el mismo cuello de botella** que antes. El diagnóstico de
12k no tuvo bajas; el de 72k sí. El fix de ración y desatasco mejora la supervivencia, pero **no
resuelve el acceso a la taberna** en ese punto.

**Conclusión:** F1 queda **ABIERTO**. La ración diaria ya alimenta y el goal se desatasca, pero las
campañas posteriores destaparon dos problemas más: la **dotación inicial de 20 puntos** (corregida a 256)
y el **destino único de la taberna** (corregido a la mesa por aldeano). Con ambas, el mundo que se hundía
bajó de 16 a **9 muertes**, pero sigue muriendo gente **yendo a la taberna** en `630,63,530` y en la
puerta; el acceso/ruta es el objetivo abierto. Informes:
`2026-11-24-F1-FOOD-COLLAPSE-AND-INITIAL-STOCK.md` y
`2026-11-24-F1-TAVERN-TABLE-DESTINATION.md`.

### 3.1 Campañas de despensa y mesa

| Run | Código | Ticks | Resultado | Población | Comida | Bajas |
|---|---|---:|---|---:|---|---:|
| `soak-f1-stable` (mundo nuevo) | `14bfeba9` | 72.000 | `SOAK_FAIL` | 1 | **0** | **29** (colapso alimentario) |
| `soak-f1-remesa` (mismo mundo, 256) | `552ecfc2` | 72.000 | `SOAK_FAIL` | 14 | **160–284** | **16** (taberna) |
| `soak-f1-table` (mesa por aldeano) | `80c0e76e` | 72.000 | `SOAK_FAIL` | 21 | **124–284** | **9** (taberna) |

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

## 5 · Diagnóstico de ruta (24-nov-2026)

Para no adivinar el choke se añadió instrumentación **opt-in**:

- `lanzar-soak.ps1 -Seed <long>`: escribe `level-seed` en `server.properties` para reproducir un mundo.
- `lanzar-soak.ps1 -Diag`: activa `-Ddevilrpg.taverndiag=true` (goal de taberna) y
  `-Dsoak.stuckdiag=true` (cualquier aldeano que no se mueve 400+ ticks).
- `SOAK_STUCK` registra UUID, profesión, posición, nodo siguiente, bloque del nodo, pies/suelo, número/índice
  de nodos, `done/reach`, `onGround`, hambre y goals corriendo.

Run `soak-tavern-seed-20261124-1330` con la **semilla del mundo que falló**
(`7454239611058706439`), 12.000 ticks, `-Diag`: **PASS, 0 bajas**, comida 20→42, 11/11. No hubo
`TAVERNDIAG` (ningún goal de taberna atascado en 12k); sí 49 `SOAK_STUCK` transitorios, varios con
`path=1 nodo … reach=false` en puertas de valla (`661,63,594`, `goals=[VillagerCollectGoal,
VillagerGateGoal]`), pero se recuperan y no mueren. El choke `630,63,530` **no se reprodujo en 12k**:
es intermitente. El siguiente paso es repetir el 72k con `-Seed … -Diag` y usar el `SOAK_STUCK` del
punto exacto para decidir entre puerta, valla o tramo de ruta.

### 5.1 El 72k con mesa por aldeano y diagnóstico (`soak-f1-table-diag`, semilla 3188…)

Resultado: **`SOAK_FAIL`**, 13 muertes, población 18, comida 56–? (final 56), cobertura final 11/11,
1 huérfano, 1 sin puesto transitorio. Las muertes siguen siendo `Yendo a la taberna` en
`705-727,63,549-569` (más una caída).

`TAVERNDIAG` capturó el punto exacto:

```text
uuid=... pos=704,63,553 next=703,63,553
nextBlock=dark_oak_door[facing=east,half=lower,hinge=left,open=true]
pathNodes=8 idx=1 done=false reach=true stuck=20..40
feet=air below=stone_bricks
```

Y el log del gestor:

```text
no consigue llegar a 699,63,551 desde 721,63,557
(ruta=6 nodos hasta 717,63,561 alcanza=NO;
 destino=dark_oak_fence encima=oak_pressure_plate)
```

**Causa raíz del acceso:** las **mesas** de la taberna (`dark_oak_fence` + `oak_pressure_plate`) no
tienen una casilla de pie alcanzable desde el este; el planificador da `alcanza=NO`, el goal se rinde,
descansa 40 ticks, reintenta y el aldeano se queda sin comer en la puerta abierta `703,63,553` (la
puerta está abierta; el problema es la mesa/ruta, no la puerta). No es hambre de despensa (comida 56),
ni el destino único (ya es la mesa): es **geometría/acceso de la taberna**.

**No se aplica un “alimentar a distancia” para taparlo**: el siguiente objetivo es corregir la mesa/
tramo/puerta y repetir el 72k con 0 muertes. El `-Seed`/`-Diag` queda listo.

---

## 6 · Riesgos y límites

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

---

## 7 · Veredicto

**F1 abierto:** la ración diaria y el desatasco funcionan, y la dotación inicial/mesa redujeron el
colapso (5→1→16→9), pero el **acceso a la taberna** sigue matando aldeanos con la despensa llena. No se
declara el soak aceptado; el siguiente objetivo es la ruta/puertas de la taberna.
