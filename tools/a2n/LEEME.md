# A2N · Arnés de navegación autónoma (A2) — ubicación canónica y flujo de trabajo

> **Estado: EN RECONSTRUCCIÓN (Stage 7I).** Este documento define **dónde vive** el arnés y **cómo se ejecuta**.
> No afirma que la suite esté completa: en Stage 7H se perdió el arnés maestro (ver §6).

## 1 · Ubicación canónica (versionada)

| Qué | Ruta | ¿Versionado? |
|---|---|---|
| **Fuente canónica del arnés** | `tools/a2n/A2NavHarness.java` | **Sí** (como `tools/arnes/GuardHarness.java`) |
| Superviviente del arnés perdido (parcial, no compilable como suite) | `tools/a2n/A2NavHarness.java.partial` | Sí (testigo de la pérdida) |
| Copia dañada tal cual se encontró | `build/a2n/recovery/A2NavHarness-DAMAGED-7H-*.java` | No (`build/` ignorado) |
| Copia de compilación temporal | `src/main/java/com/chipoodle/devilrpg/debug/A2NavHarness.java` | No (se borra siempre) |
| Mundos, logs, salidas | `build/a2n/a2n-<AAAAMMDD-HHMMSS>/` | No (`build/` ignorado) |

**Regla de oro:** el arnés se **edita sólo en `tools/a2n/`** y se **copia** a `src/.../debug/` para compilar.
Nunca al revés. Un fichero en `build/` **no** es la fuente: `gradlew clean` o un borrado lo eliminan (fue la
causa de la pérdida de Stage 7H).

## 2 · Secuencia segura de ejecución

```powershell
# 0) Nunca Gradle y juego a la vez
(Get-CimInstance Win32_Process -Filter "Name like 'java%'").Count   # debe ser 0 (los daemons de Gradle también cuentan para el lanzador)
.\gradlew.bat --stop

# 1) Copiar la fuente canónica al paquete de compilación y compilar
Copy-Item tools\a2n\A2NavHarness.java src\main\java\com\chipoodle\devilrpg\debug\A2NavHarness.java -Force
.\gradlew.bat compileJava

# 2) Un modo por corrida (el lanzador crea mundo aislado en build\a2n\<run>\game)
.\build\a2n\lanzar-a2n.ps1 -Modo edges   -LimiteMinutos 10
.\build\a2n\lanzar-a2n.ps1 -Modo stage6e -NavCustom true -LimiteMinutos 12
.\build\a2n\lanzar-a2n.ps1 -Modo stage6f -NavCustom true -LimiteMinutos 10
.\build\a2n\lanzar-a2n.ps1 -Modo stairs  -NavCustom true -Presupuesto 900 -Semilla 12345 -LimiteMinutos 18

# 3) Limpiar SIEMPRE el fichero temporal de producción
Remove-Item src\main\java\com\chipoodle\devilrpg\debug\A2NavHarness.java -Force

# 4) Validación
.\gradlew.bat compileJava
python tools\lint_aldea.py --strict
git diff --check
```

El lanzador **aborta** si detecta cualquier proceso `java` vivo (incluido un daemon de Gradle): por eso el paso 0
ejecuta `--stop` antes de cada corrida.

## 3 · Modos del arnés

| Modo | Qué mide | Entidades | `-NavCustom` |
|---|---|---|---|
| `edges` | Validez de aristas del planificador: WALK llano, DROP seguro (3), DROP rechazado (4), subida 2, subida 3 rechazada | sonda | no |
| `stage6e` | Nodo `BREAK` de la ruta ejecutado sobre SU obstrucción + soporte lateral en la colocación | 1 | **sí** |
| `stage6f` | Validador de descenso: seguro / demasiado hondo / aterrizaje bloqueado / sin bloque / WALK llano | 1 | **sí** |
| `stairs` | Aceptación del acantilado: colocación verificada + travesía física + llegada al destino | 1 | **sí** |
| `stage7c`/`stage7d` | Escape controlado de 8 bloques con rampa lateral (fixture largo alcance) | 1-3 | **sí** |

Salidas por corrida en `build\a2n\<run>\`: `srv.log` (log del servidor, **la evidencia**), `fin.txt` (veredicto),
`estado.txt`, `a2n.log`. Marcadores estables en `srv.log`: `REGRESION-ARISTAS OK|FALLO`,
`FIXTURE-ESCALERA ACEPTACION`, `FIXTURE-6E COLOCACION`, `STAGE7*`, `FALLO DE INSTRUMENTO`, `RUN INVALIDO`.

## 4 · Contratos de aceptación (no cambiar para que pase)

- `edges`: 5/5 y `FALLO DE INSTRUMENTO 0`.
- `stage6e`: 4/4; en particular **B**: el nodo `BREAK` de la ruta **rompe su obstrucción** (`muroAire=true`) y la
  ruta continúa. La guarda de ruta activa **no** debe interceptar acciones autorizadas `BREAK`/`BRIDGE`.
- `stage6f`: 5 casos (descenso válido, exceso de caída, aterrizaje bloqueado, aterrizaje sin bloque, WALK llano).
- `stairs`: `destinoAlcanzado=true`, `bloqueColocadoVerificado=true`, `escalonesTravesados>=1`,
  `colocacionesComprobadasFallo=0`.

## 5 · Protecciones

- `FALLO DE INSTRUMENTO 0` **no** es aceptación por sí solo: hay que leer los casos y el veredicto de `fin.txt`.
- Nunca se ejecuta el asedio completo ni la caza desde aquí sin autorización expresa del stage.
- La fuente canónica no se toca desde `build/`: si aparece divergencia, gana `tools/a2n/` y se copia de nuevo.

## 6 · Pérdida de Stage 7H (qué pasó y qué falta)

Una edición de limpieza sobre `build\a2n\A2NavHarness.java` (fichero **ignorado** por git) eliminó ~2 200 líneas
contiguas. No existe copia recuperable: no hay otra copia en el árbol, ni artefactos `.class`, ni respaldos de
editor, ni historial (el fichero nunca estuvo versionado). Se conserva el superviviente:

- `build\a2n\recovery\A2NavHarness-DAMAGED-7H-20261017-161148.java` — 110 187 B, 1 892 líneas,
  SHA-256 `2C3EBA9ED6E936B595E26650B796A624838B5CB4FF6CFD7B1804BE7A13552127`.

**Superviviente (exacto)**: los fixtures `stage7b`/`stage7c`/`stage7d`/`stage7e` y las utilidades comunes
(`log`, `bAire`, `bSolido`, `crearZombie`, `prepararAldea`, `forzarChunks`, `dentroDeVentana`, `comprobarCaso`,
`invalidar`, `cerrar`) y el despacho de modos posterior al corte.

**Perdido (reconstruir)**: `edges`, `stage6e`, `stage6f`, `stairs`, el despacho de esos cuatro modos y las
constantes/arranque de fase que vivían en las líneas eliminadas. **Se reconstruye desde los contratos de §4, no
desde los logs**: un log dice lo que se midió, no lo que se ejecutó.

**Criterio de equivalencia**: una prueba reconstruida **no** se declarará equivalente a la perdida hasta que
reproduzca los mismos contratos y sus resultados queden registrados.

## 7 · Estado de reconstrucción (Stage 7J)

**`edges` NO reconstruido todavía.** Evidencia dura de por qué el superviviente no sirve como base compilable:
al copiarlo tal cual a `src/.../debug/` y compilar, `javac` falla **desde la primera línea del cuerpo superviviente**
(`A2NavHarness.java:151,159,177,182,199,203-209 … error: cannot find symbol`). El corte de Stage 7H dejó el fichero
estructuralmente roto al principio del tramo conservado, no sólo sin helpers al final. Conclusión medida:
**la reconstrucción se escribe desde cero** contra las APIs actuales y los contratos de §4; el `.partial` queda como
testigo histórico, no como base de código.

**Orden de reconstrucción acordado (uno por corrida, sin afirmar equivalencia):**
1. `edges` — sólo `NavPlanner.plan` + `NavObstruction` + una sonda `AggressiveZombieEntity`; 5 casos de §4.
2. `stage6e` — BREAK autorizado sobre su nodo (verifica la exención 7H) + soporte lateral.
3. `stage6f` — validador de descenso (5 casos).
4. `stairs` — colocación verificada + travesía física posterior.
5. `stage7c`/`stage7d` — acantilado controlado (reutilizable del `.partial`: sus fixtures sobreviven, pero dependen
   de utilidades que se perdieron, así que también se reescriben).

**Estado por modo:** `edges` PENDIENTE · `stage6e` PENDIENTE · `stage6f` PENDIENTE · `stairs` PENDIENTE ·
`stage7c/d` PENDIENTE. Ningún modo está verificado en Stage 7J y **no se declara equivalencia** con el arnés perdido.

**Lo que sigue intocado por esta pérdida:** el código de producción (navegación) compila y sus SHA-256 están
registrados en el informe del stage; la evidencia histórica medida (logs de Stages 6F, 7A, 7B, 7C, 7D, 7E, 7G)
sigue en `build/a2n/a2n-*/srv.log` y es la única fuente de resultados reales hasta que haya arnés.

## 8 · Stage 7K · arnés mínimo RECONSTRUIDO (`edges`)

**Fuente canónica:** `tools/a2n/A2NavHarness.java` (escrita desde cero; **no** es equivalente al arnés perdido).
Bootstrap: `@EventBusSubscriber(modid = DevilRpg.MODID)` + `ServerStartedEvent` + `ServerTickEvent.Pre`; modo por
`-Da2n.mode`; cierre con `fin.txt`/`estado.txt` en `-Da2n.raiz` y `server.halt(false)`.

**Contratos implementados y medidos** (`a2n-20261018-010019`, `build\a2n\<run>\srv.log`):

| Caso | Contrato | Resultado |
|---|---|---|
| 1 | WALK llano válido | **OK** — `ruta=4`, `todasLlano=true`, sin agotar presupuesto |
| 2 | DROP de 3 válido | **OK** — `ruta=6`, `conDROP=true`, `caidaMax=3` |
| 3 | DROP de 4 RECHAZADO | **OK** — `ruta=0`, `exp=376/900`, `alcanzada=false` |
| 4 | subida de 2 válida | **FALLO (fixture)** — `pre=false` → `FALLO DE INSTRUMENTO: sin soporte bajo 708,186,568` |
| 5 | subida de 3 RECHAZADA | **OK** — `ruta=0` |

**4/5 ejecutados y aprobados.** El caso 4 **no** es un fallo de producción: la geometría de mi fixture no coloca
bloque de soporte bajo la celda meta (`y+2`), y la precondición lo detecta y lo reporta como
`FALLO DE INSTRUMENTO` en vez de contarlo como aprobado — que es exactamente el comportamiento exigido. Pendiente
sencillo para el próximo stage: `solido(meta4.getX(), y + 1, z0)` en lugar de los dos bloques actuales, recompilar
y volver a correr hasta 5/5.

**Limitaciones declaradas:** sólo `edges`; `stage6e`, `stage6f`, `stairs` y `stage7c/d` **NO** existen en este
arnés. No se ha tocado producción. `stage6e` sigue en ROJO histórico y la exención BREAK/BRIDGE del Stage 7H sigue
sin prueba.

## 9 · Stage 7L · `edges` 5/5 (caso 4 corregido)

**Corrección exacta aplicada** en `tools/a2n/A2NavHarness.java` (fichero con **LF**, sin CRLF — por eso los anclajes
con `\r\n` no aplicaban): se eliminaron las dos líneas erróneas del caso 4
(`solido(meta4.getX(), y - 1, meta4.getZ())` y `solido(meta4.getX() - 1, y, meta4.getZ())`) y se colocó, **después**
del bucle que limpia el aire de la columna,
`solido(meta4.getX(), y + 1, meta4.getZ())` — soporte con colisión **inmediatamente debajo** de la meta `y+2`.
Los cinco contratos y sus resultados esperados **no** se tocaron; las precondiciones siguen siendo las mismas y un
fallo de instrumentación **no** cuenta como aprobado.

**Corrida:** `a2n-20261018-034632` · log `build\a2n\a2n-20261018-034632\srv.log` · comando
`.\build\a2n\lanzar-a2n.ps1 -Modo edges -LimiteMinutos 12` (mundo aislado `…\game\a2n`, `run\saves\New World` intacto).

| Caso | Contrato | Resultado medido |
|---|---|---|
| 1 | WALK llano válido | **OK** `ruta=4 todasLlano=true presupuesto=false` |
| 2 | DROP de 3 válido | **OK** `ruta=6 conDROP=true caidaMax=3` |
| 3 | DROP de 4 RECHAZADO | **OK** `ruta=0 exp=376/900 alcanzada=false` |
| 4 | subida de 2 válida | **OK** `pre=true ruta=12 subidaMax=2` |
| 5 | subida de 3 RECHAZADA | **OK** `ruta=0 exp=376/900 alcanzada=false` |

`RESUMEN ARNES: modo=edges casos=5/5`, **0 fallos de instrumentación**, **0 JVMs** al terminar.
Los cinco casos llegaron al planificador de producción (`NavPlanner.plan`), incluido el 4.

**Limitaciones que siguen vigentes:** sólo existe el modo `edges`. `stage6e`, `stage6f`, `stairs` y `stage7c/d`
**no** están reconstruidos; la exención `BREAK`/`BRIDGE` del Stage 7H sigue **sin prueba** y `stage6e` sigue
históricamente en ROJO. Este arnés es **nuevo**: no es equivalente al perdido en Stage 7H.

## 10 · Stage 7M · marcador `fin.txt` reparado (causa: RUTA, no contenido)

**Diagnóstico con evidencia.** `-Da2n.raiz=D:\Documents\DevilRpg-autonomous` es la **raíz del repositorio**, NO el
directorio del run. El arnés hacía `Path.of(a2n.raiz).resolve("fin.txt")` → escribía en la raíz del repo, y el
lanzador comprueba `$base = $raiz\build\a2n\$runId`. Prueba: tras la corrida `a2n-20261018-034632` existían
`fin.txt` (50 B, `COMPLETO (edges) - casos=5/5 fallosDeInstrumento=0`) y `estado.txt` (1 330 B) **en la raíz**, y
NINGÚN `fin.txt` en `build\a2n\a2n-20261018-034632\`. Por tanto el marcador **nunca estuvo vacío**: se escribía en
otro sitio. (El "fin.txt vacío" que reporté en 7L era `Get-Content` de un fichero **inexistente** en el run dir.)

**Corrección mínima** (`cerrar()`), alineada con el diseño del lanzador (`$raiz\build\a2n\$runId`):

```java
Path raiz = Path.of(System.getProperty("a2n.raiz", "."));
String run = System.getProperty("a2n.run", "sin-run");
Path base = raiz.resolve("build").resolve("a2n").resolve(run);
Files.createDirectories(base);
Files.writeString(base.resolve("fin.txt"), veredicto + " (" + MODE + ") - " + detalle, UTF_8);
Files.writeString(base.resolve("estado.txt"), DETALLE.toString(), UTF_8);
```

Se eliminó además la escritura residual de `estado.txt` en la raíz y los dos artefactos sueltos que había dejado en
la raíz del repo (generados, no versionados).

**Verificación** — corrida `a2n-20261018-040250`, log `build\a2n\a2n-20261018-040250\srv.log`:

- `build\a2n\a2n-20261018-040250\fin.txt` **existe** y contiene
  `COMPLETO (edges) - casos=5/5 fallosDeInstrumento=0` — **idéntico** al veredicto de `srv.log`
  (`FIN (COMPLETO (edges) - casos=5/5 fallosDeInstrumento=0)`) ✔
- `estado.txt` del run: existe ✔ · raíz del repo: **sin** `fin.txt`/`estado.txt` ✔
- Cinco casos: **5/5 OK** (1 WALK, 2 DROP-3, 3 DROP-4 rechazado, 4 subida 2 `ruta=12 subidaMax=2`, 5 subida 3 rechazada) ✔
- `RESUMEN ARNES: modo=edges casos=5/5`, 0 fallos de instrumentación, 0 JVMs al terminar ✔

**Limitaciones vigentes:** sólo existe el modo `edges`; `stage6e`/`stage6f`/`stairs`/`stage7c-d` **no** están
reconstruidos; la exención `BREAK`/`BRIDGE` del Stage 7H sigue **sin prueba** (`stage6e` históricamente ROJO). Este
arnés es nuevo y **no** es equivalente al perdido en Stage 7H.

## 11 · Stage 7N · modo `stage6e` RECONSTRUIDO (BREAK autorizado de un nodo de ruta)

**Contrato exigido (escrito antes de implementar):** el ejecutor de producción debe (1) aceptar una ruta con un
nodo `BREAK`/`BRIDGE`, (2) llegar a ese nodo y despachar su acción autorizada, (3) **no** ser rechazado por la
guarda de ruta activa (exención Stage 7H), (4) producir un resultado **verificado en el mundo** (celda en aire),
(5) continuar/replanificar después sin bucle sin fin, y (6) seguir protegiendo la ruta activa frente a acciones
genéricas. Fallo de instrumentación ≠ aprobado.

**Geometría del fixture (aislada, determinista):** losa + cámara **5×5×3 cerrada con techo** de piedra alrededor del
ente en `spawn+(700,40,700)`; el ente nace en el centro `796,184,668`; el objetivo (fuera) está en `803,184,668`.
La única salida posible es **romper** un muro: el nodo `BREAK` se toma de la **propia ruta de producción** (nunca se
elige a mano). Chunks forzados; mundo desechable del run.

**Corrida:** `a2n-20261018-040638` · log `build\a2n\a2n-20261018-040638\srv.log` ·
comando `.\build\a2n\lanzar-a2n.ps1 -Modo stage6e -NavCustom true -LimiteMinutos 12`.

| Caso | Contrato | Resultado medido |
|---|---|---|
| 6e-1 | la ruta contiene nodo BREAK/BRIDGE | **OK** `muro=798,184,668` |
| 6e-2 | alcanzó el nodo y despachó su acción | **OK** `ultimaAccionDeRuta=798,184,668:BREAK-YA-RESUELTO-AVANZA` |
| 6e-3 | bloque ELIMINADO del mundo | **OK** `aire=true` |
| 6e-4 | la guarda NO rechazó la acción autorizada | **OK** `replanteosPorGuarda=0` |
| 6e-5 | continuó tras la rotura (sin bucle) | **OK** `continuo=true llego=true moves=77 estado=ARRIVED` |
| 6e-6 | la guarda protege ruta activa y no celdas ajenas | **OK** `guardaProtege=true` |

`RESUMEN ARNES: modo=stage6e casos=6/6` · **0 fallos de instrumentación** · marcador
`build\a2n\a2n-20261018-040638\fin.txt` =
`COMPLETO (stage6e) - casos=6/6 fallosDeInstrumento=0 | muro=798,184,668 roto=true replanteosGuarda=0 estado=ARRIVED`
(idéntico al veredicto de `srv.log`).

**¿Se ejerció de verdad la exención `BREAK`/`BRIDGE` del Stage 7H?** **Sí, en ejecución** (no por inspección): el
nodo activo era `798,184,668` (celda de la ruta), la acción autorizada se ejecutó (`BREAK-YA-RESUELTO-AVANZA`), el
bloque desapareció y `replanteosPorGuarda=0` — es decir, la guarda **no** interceptó la acción autorizada, mientras
6e-6 confirma que la guarda **sí** sigue marcando como protegida la celda del nodo activo (y no una ajena). Con
esto la regresión histórica `stage6e` del Stage 7G queda **resuelta por evidencia nueva**.

**`edges` sigue verde tras añadir el modo:** corrida `a2n-20261018-040955`,
`fin.txt` = `COMPLETO (edges) - casos=5/5 fallosDeInstrumento=0`.

**Limitaciones:** sólo existen `edges` y `stage6e`; `stage6f`, `stairs` y `stage7c/d` **no** están reconstruidos.
Este arnés es **nuevo** y **no** es equivalente al perdido en Stage 7H; los contratos se han reconstruido desde los
informes 6E/7G/7H, no desde el código original.

## 12 · Stage 7O · contrato del modo `stage6f` (escrito ANTES de implementarlo)

**Fuente:** informes Stage 6F/7C/7E y APIs de producción (`NavExecutor.validarDescenso`, `Descenso.valido/rechazo`,
`NavPlanner`/`NavObstruction`). **No** se declara equivalencia con el arnés perdido.

**Contrato (6 casos; el 7 es de instrumentación):**
1. **d1** un descenso a un aterrizaje con soporte, holgura y **camino de caída despejado** es VÁLIDO.
2. **d2** un descenso que **supera la caída segura** (4 > 3) es RECHAZADO, con motivo que mencione `caida`.
3. **d3** un descenso con el **aterrizaje bloqueado** es RECHAZADO, con motivo que mencione `holgura`.
4. **d4** un descenso **sin soporte** bajo el aterrizaje es RECHAZADO, con motivo que mencione `soporte`.
5. **d5** un candidato **NO descendente** (`dy=0`) es RECHAZADO, con motivo que mencione `DESCIENDE`.
6. **d6/d7** en ejecución: la ruta contiene un nodo `DROP`; la ruta **NO avanza** más allá de ese nodo mientras el
   ente siga por encima del aterrizaje (`indiceDeRuta` estable), y **sí** se observa el aterrizaje físico después.
7. Si la ruta nunca contiene `DROP`, se reporta **FALLO DE INSTRUMENTO**, nunca aprobado.

**Precondiciones verificadas por caso:** chunk cargado; celda de aterrizaje y su cabeza en aire (o sólidas cuando el
caso lo exige); soporte presente/ausente según el caso; ente creado y situado. Un fallo de precondición cuenta como
`FALLO DE INSTRUMENTO` y **no** como aprobado.

**Geometría:** plataforma superior en `spawn+(800,40,800)` con el ente encima; repisa inferior a 2 bloques de
desnivel a `+2` en x; celdas de prueba adicionales para d2..d5 construidas y destruidas alrededor. Chunks forzados,
mundo desechable del run.

**LÍMITE DECLARADO (honestidad):** este fixture cubre el **validador de descenso** de producción y el ciclo
`DROP`→aterrizaje físico. **NO** cubre todavía: plan de construcción multi-paso, colocación verificada con soporte
lateral, ni rechazo de candidatos por `dy=0` dentro del *plan de construcción* (esa puerta vive en
`descensoValido`, no en `validarDescenso`); esos casos quedan PENDIENTES y se reportarán como tales.

## 13 · Stage 7O · `stage6f` implementado y **EN ROJO** (fixture defectuoso + hallazgo a investigar)

**Resultado:** corrida `a2n-20261018-044255` · `fin.txt` = `INVALIDO (stage6f) - casos=0/7
fallosDeInstrumento=7 | rutaDrop=true avanzoAntes=true aterrizo=false`. **Ningún caso aprobado y NO se han
debilitado aserciones.**

**Causa 1 (defecto de fixture, demostrada):** el validador de producción exige que la columna del aterrizaje sea
**contigua** al ente (`|dx|,|dz| <= 1`) y mis casos d1..d5 estaban a `dx=2,4,6,8,10` → los cinco fueron rechazados
por `el aterrizaje no esta en una columna contigua`, no por el motivo que cada caso quería medir. Arreglo previsto:
repartir los cinco casos en cinco **columnas contiguas distintas** — d1 `(+1,0)`, d2 `(0,-1)`, d3 `(-1,0)`,
d4 `(0,+1)`, d5 `(+1,+1)` — cada una con su geometría de soporte/holgura/desnivel.

**Causa 2 (defecto de instrumentación, demostrado):** d6 marcó `avanzoAntes=true` por el **primer muestreo**
(`s6fIdxPrevio` arranca en `-1` y el primer índice real fue `1`). El detector sólo debe comparar índices entre
muestreos consecutivos del **mismo** nodo DROP.

**Hallazgo a investigar (posible defecto de producción, no concluido):** en la corrida el planificador propuso
`route=[WALK@897,184,768 DROP@898,182,768]`, es decir un nodo `DROP` a **2 columnas** del ente, y el ejecutor lo
rechazó al ejecutarlo (estado `RECOVERING`, `rec=3`, sin movimiento). Si se confirma, planificador y ejecutor no
coinciden en la contigüidad del `DROP`; **no** se ha corregido producción en este stage.

**Limitaciones:** d6/d7 (ciclo DROP→aterrizaje) y todo el plan de construcción multi-paso **no** quedan verificados.
Los otros dos modos siguen verdes: `edges` 5/5 y `stage6e` 6/6 (re-ejecutados tras añadir `stage6f`).

## 14 · Stage 7P · fixture y muestreo de `stage6f` reparados → **6/7** (d5 no observable por API pública)

**Fix A (geometría):** d1..d5 pasan a columnas **contiguas** distintas respecto al ente — d1 `(+1,0)`, d2 `(0,-1)`,
d3 `(-1,0)`, d4 `(0,+1)`, d5 `(+1,+1)` — cada una con su propio soporte/holgura/desnivel y limpiando **solo** su
columna (antes se limpiaba `z0` fijo y las pruebas quedaban a dx=2..10, que producción rechaza por "columna no
contigua"). **Fix B (muestreo d6):** se guarda `s6fNodoDrop` + `s6fIdxBaseline`; la **primera** observación de un
nodo `DROP` fija la referencia y **no** cuenta como avance; solo se comparan muestreos posteriores del **mismo**
nodo.

**Corrida:** `a2n-20261018-045523` · log `build\a2n\a2n-20261018-045523\srv.log` ·
`fin.txt` = `INVALIDO (stage6f) - casos=6/7 fallosDeInstrumento=1 | rutaDrop=true avanzoAntes=false aterrizo=true`.

| Caso | Resultado | Medida |
|---|---|---|
| d1 descenso seguro | **OK** | `dx=1 dy=-2 NATURAL OK` |
| d2 caída > límite | **OK** | `la caida 4 supera la caida segura 3` |
| d3 aterrizaje bloqueado | **OK** | `el aterrizaje no tiene holgura de pies y cuerpo` |
| d4 sin soporte | **OK** | `el aterrizaje no tiene soporte con colision` |
| d5 NO descendente | **NO OBSERVABLE** | `rechazo={null}` → `validarDescenso` **acepta** un destino al mismo nivel |
| d6 no avanza antes de aterrizar | **OK** | `avanzoAntes=false` |
| d7 aterrizaje físico | **OK** | `aterrizo=true pos=897,182,768` |

**d5 es límite de cobertura, no defecto demostrado:** la puerta `dy >= 0 → rechazo` vive en `descensoValido(...)`
(**privada**, usada por el plan de construcción), NO en `validarDescenso(...)`. Con la API pública actual **no se
puede observar**; queda declarado como no verificado (no se cuenta como aprobado y no se ha relajado la aserción ni
tocado producción).

**Estado de los otros modos:** no se re-ejecutaron en este stage porque `stage6f` no alcanzó los siete casos
válidos (regla del enunciado). Últimos marcadores verdes: `edges` 5/5 (`a2n-20261018-044503`) y `stage6e` 6/6
(`a2n-20261018-044629`).

**Arnés reconstruido, NO equivalente al perdido en Stage 7H.**

## 15 · Stage 7Q · d5: SÍ existe camino observable público (decisión: A identificada, NO ejecutada)

**Camino de código (evidencia, `NavExecutor.java`):**
- `descensoValido(celdaBloque)` privado en **L472**; se invoca en la puerta de candidatos de
  `buscarEscalonDeDescenso` (**L570**: `esCeldaColocable(cand) && bajadaSegura(...) && descensoValido(cand)`) y en la
  del objetivo (**L586**).
- Cuando el candidato NO desciende (`dy >= 0`) deja el motivo en **L483**:
  `ultimoRechazoDeDescenso = "<celda> -> el escalon no DESCIENDE (dy=N)"`.
- Ese campo es **público por API existente**: `descensoDiag()` (**L2116**) lo emite como
  `ultimoRechazoDeCandidato={...}`, y `celdasConstruidas()` (**L238**) dice si se colocó algo.

**Decisión: A está DISPONIBLE, pero NO se implementa ni se verifica en este stage.** No hace falta reflexión, ni
copiar el predicado, ni añadir API pública: el rechazo ya es observable. El test que lo cerraría es determinista y
concreto:

1. llanura plana con el ente encima y la misión **3 bloques por debajo** (dispara `tocaConstruir`);
2. dejar correr el ciclo de construcción un número acotado de ticks;
3. aserciones: `descensoDiag()` **contiene** `no DESCIENDE` y `celdasConstruidas()` **no** contiene la celda del
   candidato (el rechazo impidió colocar), con precondiciones (llanura construida, misión válida) y
   `FALLO DE INSTRUMENTO` si el ciclo de construcción no llega a ejecutarse.

**Estado de d5: NO VERIFICADO.** `stage6f` sigue en **6/7** y **NO** se etiqueta 7/7 ni aceptación global. Los seis
casos verdes (d1–d4, d6, d7) siguen siendo los únicos con evidencia de ejecución
(`a2n-20261018-045523`). Cerrar d5 exige implementar el fixture anterior y ejecutarlo; **no** exige tocar producción.

**Arnés reconstruido, NO equivalente al perdido en Stage 7H.**

## 16 · Stage 7R · d5 intentado por el ciclo de construcción real → sigue **NO VERIFICADO** (6/7)

**Fixture añadido (según §15):** llano plano de 7×7 con el ente encima en `916,184,768`; **misión sólida 3 bloques
por debajo** en `919,181,768` (dentro de un bloque de roca maciza) para disparar `tocaConstruir`. La aserción usa
solo API pública: `descensoDiag()` (debe contener `no DESCIENDE`) y `celdasConstruidas()` (no debe contener el
candidato); si el ciclo no se observa → `FALLO DE INSTRUMENTO`.

**Corrida:** `a2n-20261018-050928` · log `build\a2n\a2n-20261018-050928\srv.log` ·
`fin.txt` = `INVALIDO (stage6f) - casos=6/7 fallosDeInstrumento=2 | d5Ciclo=false rutaDrop=true
avanzoAntes=false aterrizo=true`.

**Resultado por caso:** d1 **OK**, d2 **OK**, d3 **OK**, d4 **OK**, d6 **OK** (`avanzoAntes=false`), d7 **OK**
(`aterrizo=true pos=897,182,768`), **d5 NO VERIFICADO**.

**Evidencia exacta del fallo de d5 (instrumentación, no producción):**
`descensoDiag()` = `intentosDeDescenso=0 fallosDeDescenso=0 ultimoDescenso={-} ultimoRechazoDeCandidato={-}` y
`celdasConstruidas()` **vacía**. Es decir: **el ciclo de construcción nunca llegó a la puerta de descenso**
(`descensoValido` no se ejecutó: `ultimoRechazoDeCandidato` sigue en `-`), así que no hubo rechazo que observar. La
causa inmediata es que `buscarEscalonDeDescenso` retorna antes de esa puerta (no hay candidato que pase
`esCeldaColocable`/`bajadaSegura`, o `pasoDeConstruccion` no se invoca en ese estado); **no** se ha debordado
ninguna aserción y **no** se ha tocado producción.

**Qué haría falta para cerrar d5:** conseguir que el fixture alcance `buscarEscalonDeDescenso` con al menos un
candidato que pase las puertas previas (p. ej. una cara lateral de muro junto al llano, para que haya celda
colocable) y solo entonces el `dy>=0` la rechace; alternativa: exponer en diagnóstico el motivo de
`pasoDeConstruccion` (`planConstruccionDiag()` ya existe y es público — usarlo como evidencia intermedia). Sin eso,
d5 **no** se puede dar por aprobado.

**Estado:** `stage6f` **6/7**; `edges` y `stage6e` **no** se re-ejecutaron (regla del enunciado: solo si stage6f
llega a 7/7). Últimos marcadores verdes: `edges` 5/5 (`a2n-20261018-044503`), `stage6e` 6/6
(`a2n-20261018-044629`). Arnés reconstruido, **NO** equivalente al perdido en Stage 7H.

## 17 · Stage 7S · d5: puerta de construcción aún NO alcanzada (sigue 6/7)

**Investigación (predicados previos, `NavExecutor.java`):**
- `bajadaSegura(pies, celda)` (L631) = `pies - (celda.y + 1) <= maxSafeDrop` → **solo acota por arriba**; NO bloquea
  candidatos al mismo nivel (devuelve -1 y pasa). Por tanto, un candidato al nivel del ente **sí** llegaría a
  `descensoValido` y allí `dy = +1` produciría `no DESCIENDE`.
- `esCeldaColocable(celda)` (L688) exige aire en celda/+1/+2, no invadir al ente y soporte (terreno, construido o
  **lateral**) → en un llano con suelo sólido, un candidato adyacente al nivel del ente **pasa**.
- `buscarEscalonDeDescenso()` (L528) exige `placement != null`, `planMision != null` y `desnivelMision > 0`.

**Por qué no se ejecuta:** en el llano abierto la búsqueda **agota presupuesto** (`hitBudget`) y `tryPlan` vuelve
**antes** de pasar a `RECOVERING`, así que `recover()` —y con él `pasoDeConstruccion()`— nunca corre. Se cambió el
fixture a una **cámara 5×5×3 cerrada** con misión sólida 3 abajo para forzar "sin ruta" sin agotar presupuesto…
y el resultado siguió igual: `descensoDiag()` = `intentosDeDescenso=0 fallosDeDescenso=0
ultimoRechazoDeCandidato={-}` y `celdasConstruidas()` vacía.

**Corrida `a2n-20261018-051356`** · log `build\a2n\a2n-20261018-051356\srv.log` ·
`fin.txt` = `INVALIDO (stage6f) - casos=6/7 fallosDeInstrumento=2 | d5Ciclo=false rutaDrop=true
avanzoAntes=false aterrizo=true`. d1–d4, d6, d7 **OK**; **d5 NO VERIFICADO**.

**Instrumentación que FALTA (causa de no poder cerrar el diagnóstico):** no se ha registrado
`planConstruccionDiag()` (ya **público**) en la traza de d5. Es el dato que diría exactamente dónde se detiene el
ciclo (`fase`, `escalon`, `intentos`, `agotada`, `busquedas`, `ultimaBusqueda.motivo`). Sin él sólo se puede decir
que `pasoDeConstruccion()`/`buscarEscalonDeDescenso()` no llegan a la puerta; **no** que la puerta rechace mal.

**Siguiente paso exacto (sin tocar producción):** añadir a d5 `planConstruccionDiag()` en el volcado, volver a
correr, y con ese motivo decidir si el bloqueo es de estado del ejecutor (p. ej. no entra en `RECOVERING`), de
`tocaConstruir` (`mob.y > planMision.y` / `construccionAgotada`) o de que no hay candidato que pase
`esCeldaColocable`. **d5 no se cuenta como aprobado y `stage6f` no es 7/7.**

**Sin re-ejecutar `edges`/`stage6e`** (regla: sólo si d5 llega a su condición y `stage6f` da 7/7). Últimos verdes:
`edges` 5/5 (`a2n-20261018-044503`), `stage6e` 6/6 (`a2n-20261018-044629`). Arnés reconstruido, **NO** equivalente
al perdido en Stage 7H.

## 18 · Stage 7T · d5: el ciclo de construcción SÍ se ejecuta; la puerta `descensoValido` sigue sin alcanzarse

**Instrumentación añadida (solo diagnóstico, API pública):** muestreo cada 20 ticks de
`planConstruccionDiag()` (+ nº de cambios) y de los estados vistos del ejecutor del llano, volcados en la evidencia
de d5 junto a `descensoDiag()` y `celdasConstruidas()`.

**Evidencia medida (corrida `a2n-20261018-051902`, log `build\a2n\a2n-20261018-051902\srv.log`):**
`construccion={fase=NOT_STARTED … busquedas=1 … evaluados=6 … primerCandidato=917,181,768
primerRechazo=holgura 2 (arriba+2) no es aire}` · `estados={ PLANNING RECOVERING ARRIVED}` · `colocadas=0`.
Por tanto: (1) el ejecutor **sí** entra en `RECOVERING`, (2) `pasoDeConstruccion()`/`buscarEscalonDeDescenso()`
**sí** se ejecutan, (3) **ningún** candidato pasa `esCeldaColocable` → `descensoValido` **nunca** se llama
(`ultimoRechazoDeCandidato={-}`). Diagnóstico aplicable: **caso 4** del enunciado.

**Hallazgo estructural (de código):** `buscarEscalonDeDescenso()` sólo enumera celdas `y − nivel` con `nivel >= 1`
(`orden=[3,2,1]` / `[2,1]`), es decir **siempre por debajo del ente**; `dy = 0` solo puede aparecer con un candidato
de `nivel = 1`. Esa es la única vía real a la rama `no DESCIENDE`.

**Ajustes de geometría probados (solo fixture):** techo a `y+4` (corrida `…052113`) y suelo de 2 bloques
(`…052337`): `busquedas=4 intentos=4/40`, primer rechazo pasa a `holgura 1 (arriba) no es aire` → el candidato de
`nivel 1` sigue sin pasar la elegibilidad; `colocadas=0`. **d5 NO verificado; `stage6f` NO es 7/7.**

**Bloqueo exacto:** falta una geometría en la que el candidato de **nivel 1** (celda a `y−1`, con soporte debajo y
holgura arriba/+2) pase `esCeldaColocable` y llegue a `descensoValido` → `dy = (y−1+1) − y = 0` → `no DESCIENDE`.
El siguiente intento debe construir eso explícitamente (p. ej. repisa de 1 bloque adosada a una pared lateral, con
dos bloques de soporte debajo y sin techo sobre la columna del candidato).

**Sin re-ejecutar `edges`/`stage6e`** (regla del stage). Últimos verdes: `edges` 5/5 (`a2n-20261018-044503`),
`stage6e` 6/6 (`a2n-20261018-044629`). Arnés reconstruido, **NO** equivalente al perdido en Stage 7H.

## 19 · Stage 7X · contrato de `stairs` RECUPERADO de la evidencia (sin implementar todavía)

**Fuentes (evidencia, no código):** `LEEME.md` §4 (contrato escrito al reconstruir la tabla de modos en Stage 7M) y el
log superviviente de la aceptación Stage 6F `build\a2n\a2n-20261016-184130\srv.log` (geometría medida). El código del
modo se perdió en Stage 7H; **esto NO es una invención**, es el contrato documentado, pero **no se ha implementado ni
ejecutado** en este stage.

**Geometría (medida en `a2n-20261016-184130`, semilla 12345):**
- `llanuraY=80` · `mesetaY=86` · `bordeX=120` · `caraX=119` · `desnivel=6` · `caidaInsegura(>3)=true` · `sinRepisas=true` · `sinLabio=true`
- ente en `120,87,-8` (pies Air, cabeza Air, debajo Stone) · destino en `110,81,-8` (pies Air, debajo Stone)
- muro perimetral `dz±7, x±21, alto Y79..Y89` (**1232** bloques): la **única** salida de la meseta a la llanura es la cara
- caedizos eliminados `228` bloques (`Y81..Y220`): sin eso la grava/arena cae sobre el ente y lo asfixia
- cara despejada `Y81..Y85` en `X_CARA=119` (15 celdas) + labio `Y86` (3), todas deben ser **Air**

**Flujo esperado (observado en aquel log):** colocación verificada con **soporte lateral**
(`pone un ESCALON ADHERIDO A UNA CARA en 119, 83, -8 (soporteDebajo=false soporteLateral=true)`) → travesía física
del escalón → descenso por etapas → llegada al destino.

**Contrato de aceptación (`LEEME.md` §4):** `destinoAlcanzado=true`, `bloqueColocadoVerificado=true`,
`escalonesTravesados>=1`, `colocacionesComprobadasFallo=0`.

**Estado: NO IMPLEMENTADO, NO EJECUTADO.** Queda pendiente escribir el modo en `tools/a2n/A2NavHarness.java`
(construcción de mundo según la geometría de arriba, spawn, aserciones por colocación usando `celdasConstruidas()`
y `escalonesTravesadosDelPlan()`, llegada vía `objective()` + posición real, marcador por run) y ejecutarlo. Hasta
entonces, `stairs` **no** cuenta como regresión verde ni recuperada en ejecución.

**Modo multi-paso de construcción (objetivo 2 del stage): tampoco implementado.** Propuesta para revisión: meseta con
cara vertical y destino 4-6 bloques por debajo; verificar (1) ≥2 celdas en `celdasConstruidas()` conectadas,
(2) cada colocación presente en el mundo antes de contarla, (3) `escalonesTravesadosDelPlan()` **no** avanza al
colocar (sólo al pisar), (4) el escalón siguiente no se autoriza antes de la travesía del anterior,
(5) sin caída insegura ni daño, (6) presupuestos acotados (`intentos/40`, `total/160`) y motivo terminal explícito.
