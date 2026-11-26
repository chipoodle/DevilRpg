# Rendimiento — picos de 2 s y ruido de log (25-nov-2026)

**Rama:** `feature/autonomous-village`
**Partida:** `New World`, sesión del 25-nov-2026 (09:31–09:35).

---

## 1 · Lo que dice el log

`run/logs/latest.log` tiene **tres** avisos de tick:

```text
09:32:01.231  Can't keep up! Running 2032ms or 40 ticks behind
09:33:33.836  Can't keep up! Running 2087ms or 41 ticks behind
09:35:13.903  Can't keep up! Running 2105ms or 42 ticks behind
```

No son un bucle continuo: coinciden con **tres eventos pesados**:

1. **09:32:01 — login/generación:** entrada del jugador, guarida 0 pre-generada, inicio de asedio,
   3 lobos recreados y portones.
2. **09:33:31–33 — nacimiento de la oleada:** 8 asediadores creados **en el mismo tick**, cada uno con
   su `CADENA-DIAG`/`JUMPUP` y su primera búsqueda de navegación. 2 s después, el aviso.
3. **09:35:13 — guardado:** `Saving and pausing game` y guardado de los tres niveles; el aviso sale
   justo después.

Después de cada pico el servidor sigue; no hay avisos cada 90 s ni un goal que bloquee el tick.

## 2 · Ruido que sí se puede quitar

Conteo de mensajes del mod en la sesión:

| Mensaje | Líneas |
|---|---:|
| `[Village] PARAR ...` (guardias aparcando) | **120** |
| `[Village] La casa ... no encaja` (generación) | 32 |
| `[Sculk] Sacrificio` | 12 |
| `[Siege] CADENA-DIAG` | 8 |
| `[JUMPUP] MISSIONCHANGE` | 8 |
| `[Porton] ...` | ~20 |
| `[Rumbo] peticiones=1107` | 1 resumen |

`[Rumbo] peticiones=1107 · ya iban andando=821` confirma **286 peticiones de ruta reales** en el
periodo: los guardias y oficios piden ruta con frecuencia, pero la mayoría ya iban andando y no se
recalculan.

## 3 · Correcciones aplicadas

- **Leñador sin faena:** antes `canUse` devolvía `true` con `restTicks=0` y repetía **búsqueda de
  árboles/composteros + logs en cada tick**. Ahora descansa **40 ticks (2 s)** y no escanea.
- **`[LENADOR-DISCOVER]`/`[LUMBER-HARINA-SOURCE]`** pasan a **DEBUG**; dejan de inundar el log INFO.
- **Harina inicial:** `remesaInicialHarina` ya no se reintenta cada tick: solo cada **200 ticks** si
  el almacén aún no tiene hueco.
- No se tocó la IA de guardias, el spawn de la oleada ni el guardado.

## 4 · Corrección posterior: oleadas escalonadas

La oleada del asedio clásico ya procesa **un atacante por tick** mediante
`iniciarOleada` / `spawnWaveAttacker` en `VillageManager`.

La horda del mundo seguía teniendo el bucle síncrono: creaba, insertaba e inicializaba todos sus
zombies en un solo tick. Se corrigió en `HordeManager` con una cola por dimensión:

- cada tick consume un índice de la oleada y crea/inserta como máximo **un** zombie;
- se conservan el tamaño calculado, la probabilidad, el objetivo y el libro mayor UUID;
- el asedio del mundo se registra cuando termina la oleada, no antes;
- no se reduce el número de enemigos ni se debilita su IA.

La protección de aldeas y el cierre terminal de asedios se documentan en el cambio del 26-nov-2026.
El resultado de rendimiento queda **pendiente de medición runtime**: el reparto temporal reduce el
trabajo concentrado, pero no demuestra por sí solo que no haya otros picos.

El pico de login y el de guardado siguen siendo eventos distintos (generación/carga e I/O de guardado).
Si persisten congelamientos fuera de la aparición de oleadas, hace falta un perfil de ticks (spark o
similar) para identificar el método caliente.

## 5 · Evidencia de gates

- Gate completo: **BUILD SUCCESSFUL** (1m39s); skills 416, persistencia 553, ownership 59.
- `compileJava` y `lint_aldea --strict` verdes; `git diff --check` 0.
