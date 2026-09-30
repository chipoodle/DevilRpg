# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero entero y sigue por **«Lo que está PENDIENTE»**. Cada apartado trae **lo
> que está medido**, **por qué se hizo así** y **el paso exacto que toca**. Los datos crudos de cada medida están en
> `tools/arnes/medidas-mina-sellada.txt` (§1–§28) y el porqué de cada regla, en `docs/aldea-invariantes.md`
> (**I119–I135**).
>
> **Para el jugador**: pégale a la sesión nueva: *«Lee `docs/PENDIENTE.md` y sigue por lo pendiente; mide con el arnés
> y no te fíes de nada que no esté medido»*.

## CÓMO SE TRABAJA AQUÍ (leer antes de tocar nada)

**La regla de oro**: nada entra sin **`gradlew compileJava`** + **`python tools/lint_aldea.py --strict`** + **una medida
del arnés**. Un intento que no arregla **se retira** y **se dice**. (En la última sesión se retiraron tres intentos,
con su número: 940 falsos positivos, 0 disparos, y una corrida a cero por instrumento ciego.)

**El arnés (una corrida ≈ 9-12 minutos de reloj)**:
1. Copiar `tools/arnes/GuardHarness.java` a `src/main/java/com/chipoodle/devilrpg/debug/GuardHarness.java`.
2. **Encender UN modo** en la copia (`MEDIR_MINERO`, `MEDIR_PEPITAS`, `MEDIR_MILICIA`, …) y **comprobar que está
   encendido**: en la última sesión se perdió una corrida por lanzarla con el modo apagado (`al borde: 0`).
3. `run/world` es **copia** del guardado del jugador: `run/saves/New World (2)` → `run/world` **antes** de cada
   corrida (el guardado del jugador **no se toca**). Borrar antes `run/logs/latest.log`.
4. `gradlew runServer` en segundo plano y leer `run/logs/latest.log`.

**Trampas ya pagadas (no repetirlas)**:
- **El log ROTA por tamaño**: si `latest.log` es pequeño, hay que leer también `<fecha>-N.log.gz` (gunzip y concatenar).
- **Matar `gradlew` NO mata el servidor**: el JVM sigue con el puerto y la corrida siguiente falla en silencio
  (`serverlevel2 is null`). Antes de lanzar:
  `Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'fml.modFolders' }`.
- **Al terminar**: apagar el modo, **borrar `debug/`** (y `build/classes/.../debug/`), restaurar `run/world` y
  comprobar que el `.jar` queda libre.

**La medida del pueblo (I135)**: el **total** de rendiciones (`no consigue llegar`) **es ruido** (5, 9, 10, 12, 18 en
corridas comparables). Se mide **por 1.000 ticks y por etiqueta**:
```
python tools/arnes/rendiciones.py --etiquetas build/medida-*.log
```
La prueba de un arreglo **no es el total**, es **el criterio concreto que baja a cero** (p. ej. `Yendo a la taberna`
16 → 0). Para hablar del total, **media de 3-4 corridas**. Tanda de referencia de la última sesión:
**media 1,29 · rango 0,42-2,78 · desviación 0,94** por 1.000 ticks.

## Estado (27-sep-2026 — sesión de los oficios: los 5 atascos, la mina, el hierro y la boca de la galería)

Todo **compila**, pasa `lint_aldea --strict`, el árbol está **limpio** (sin `debug/`, sin JVMs, `run/world`
restaurado) y **todo commiteado**.

**Y el titular de la sesión, medido**: la **tasa de rendiciones del pueblo** (I135, ventana fija 2.000-12.000) baja
de la **media 1,29** de la tanda anterior a **0,30** con el código final (**media de 3 corridas: 0,10 · 0,30 · 0,50**),
y el pueblo queda en **7 avisos, TODOS SUELTOS y ningún bucle** (contra 18 avisos con bucles de **16** y **19** de la
referencia de la mañana): los gordos (`Yendo a la taberna` 16, `Volviendo a la plaza` 19, `Cuidando el ganado` 3,
`no consigue entrar al bancal` 5) están **a cero**.

**Cerrado y MEDIDO en esta sesión** (con el criterio que lo prueba):

| qué | medida |
|---|---|
| **el granjero** (I129) | el caminante daba por **llegado** lo que está a 1 bloque → `caminarHaciaExacto` (tolerancia 0 + ruta pedida a mano): `no consigue entrar al bancal` **5 → 0**, avisos de huerta **6 → 0**, el granjero **`Cosechando`** |
| **la recolectora encerrada** (I130) | no podía **salir del bancal** (una puerta de valla cerrada **no es navegable**): `abrirLaCompuertaDeAlLado` + salir con paso exacto → `Volviendo a la plaza` **19 → 0** (total **27 → 9**) |
| **el ganadero** (I131) | perseguía la **celda cruda del animal** (que no se pisa) → `casillaDePieCercaDe` (regla I114): `Cuidando el ganado` **3 → 0** (total **9 → 5**) |
| **el aldeano METIDO en un bloque** | el criterio bueno es **la forma de colisión contra la altura de los pies** (con `esCeldaDePie`: 940 falsos positivos; con la caja: 0 disparos) → **2 desatascos reales**, 0 falsos positivos |
| **la mina ATRAVIESA el agua** (I132) | **aísla** la cáscara 3×3×3, **seca** la celda y **sigue** → `hechas 0 → 3/24`, **`TOPE=NO`**; y **la mina BAJA** (`pasos 16 → 32`, `y 54 → 46`) con **16 piezas de caracol** |
| **el pico y el hierro de los raids** (I133/I134) | el zombi suelta **pepitas** y **el que mata las lootea** (antes **desaparecían a los 5 min**); el herrero forja el pico **con 27 pepitas** → medido: **`Forjo un pico de HIERRO`** |
| **la taberna** | `VillagerTavernGoal` caminaba a la **celda de la mesa** (no pisable) → `casillaDePieCercaDe`: `Yendo a la taberna` **16 → 0** y la corrida **2,73 → 0,47** por 1.000 ticks |
| **la medida** (I135) | `tools/arnes/rendiciones.py`: rendiciones **por 1.000 ticks en ventana fija**, por etiqueta, con media y rango |
| **la boca de la galería** (I136) | el aldeano va **de pie sobre la losa** (nodo `y+1`) y con **dos** celdas de hueco el vecino sale **BLOCKED**: no entra ni sale → **tres** celdas. `hechas` **3/24 congelado 3.600 ticks → 24/24** y `pasos` **32 → 44** (la cara de `y=46` a `y=40`); y **las paredes contra el agua** al abrir cada celda |
| **la cadena del hierro de los raids** (I137) | el **guardia** mata, **lootea** (2 pepitas), **deja el hierro en el almacén** (13 depósitos, de 0 a **20** pepitas) y **el herrero forja el pico de HIERRO**; el eslabón que faltaba era que el guardia se lo quedaba en el zurrón |
| **la balsa, al herrero de herramientas** (I138) | era la faena que le comía el tiempo al minero (**33-46 coladas** con el pedernal en su zurrón y el almacén clavado en 6) → ahora cuela el **herrero**: coladas del minero **0**, del herrero **10**, el pedernal **6 → 16**, y la misma galería en **t≈17.200** en vez de **t≈37.800** |
| **los atascos sueltos** (I139) | (1) el guardia: el atasco se medía **solo por la recta** y la ronda es un círculo → cuenta el **avance por la ruta** (`Patrullando` **9 → 2**); (2) a por un objeto caído se iba a la **celda cruda** (la mesa, una valla) → `casillaDePieCercaDe` (`Recogiendo el corral` **4 → 0**, `Guardando lo suyo` **5 → 0**); (3) «pisable» no incluía **los cultivos** → el bancal no tenía ni una casilla de pie (el granjero hundido en bucle **7 → 1**) |

**El aviso de método que salió de aquí**: al normalizar la medida, el "ruido" escondía **16 rendiciones de un solo
aldeano en un solo sitio** (la taberna). La medida no era un trámite: **destapó el fallo**.
## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 0. Sesión del 27-sep-2026: la **boca de la galería** (el choque caracol ↔ galería) — **CERRADO y MEDIDO**

Lo ordenado era el **choque caracol ↔ galería** (el 68 % del tiempo del minero). **Medido y arreglado** — lo cuenta
**I136** y los datos crudos están en `tools/arnes/medidas-boca-galeria.txt` (corridas
`build/medida-galeria-3alto.log`, `medida-galeria-agua-1.log`, `medida-galeria-muro.log`):

| qué | medida |
|---|---|
| la **boca** (el diagnóstico viejo: «pica su propia losa») | **era un fantasma del instrumento**: `cara` es la celda del caracol del paso, y ésa lleva su pieza por definición |
| la **boca** (causa real) | el aldeano va **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y con **dos** celdas de hueco el vecino sale **BLOCKED** → no entra ni sale. Arreglo: **tres** celdas (I136) |
| **antes → después** | `hechas` de la galería del paso 32: **3/24 congelado 3.600 ticks** → **3 → … → 24/24**; y la galería del **paso 16** (que cavaba el arnés) ahora **la cava el minero: 2/24 → 24/24** |
| el **caracol** | `pasos` **32 congelado** → la galería se cierra y **sigue bajando: 34 → … → 44** (la cara de `y=46` a **`y=40`**), con `TOPE=SI`/`se PARA` = **0** |
| el **agua** | las tres celdas abren el techo en el acuífero y el túnel se inundaba (`hechas` 6 → **0**, 223 muestras). Con el censo nuevo `AGUA`: el agua entra por la **celda de delante** → `aislarDelAgua` al **abrir** cualquier celda, saltando solo el paso del caracol. Al final: **31 celdas con fluido, ninguna dentro del túnel** |
| el **muro** | con 21/24 el minero se iba al **portón norte** (243 muestras; el final de la galería cae «fuera del muro» a 17 bloques bajo el suelo): **bajo tierra no hay muro que cruzar** → la ruta a la celda 21 pasa a `22 nodos … alcanza=SI` |

**Lo que toca ahora**: **(1)** los **atascos sueltos** del §7 (**hechos y medidos** en esta misma sesión: I139);
**(2)** volver a medir la **tasa de rendiciones** (`rendiciones.py --etiquetas`, media de 3-4 corridas) con la mina ya
desbloqueada — la primera corrida buena da **0,30** por 1.000 ticks (contra 1,29 de la tanda anterior).

**Y también CERRADO en esta sesión**: la **cadena del hierro de los raids** (§6b.2/§6b.3) — **I137**: el guardia mata,
lootea, **deja el hierro en el almacén** (13 depósitos; el almacén de 0 a 20 pepitas) y **el herrero forja el pico de
hierro**, que se lleva el minero. El eslabón que faltaba era el guardia (se quedaba el botín en el zurrón) y el
instrumento tenía tres trampas (contaba pepitas en el suelo, la barredora descartaba el zombi y el escaneo era de 140
bloques): está todo en `tools/arnes/medidas-pepitas.txt`.

**Y la opción C** (la balsa y su acarreo, al herrero de herramientas) — **I138**: coladas del minero **33-46 → 0**, las
hace el herrero (**10**, hasta el objetivo de 16 pedernales) y la misma galería se completa en **t≈17.200** en vez de
**t≈37.800** (`tools/arnes/medidas-balsa.txt`).

**Y los atascos sueltos del §7** — **I139**, los dos que quedaban:

* **el guardia no se rendía por falta de ruta**: sus avisos decían `ruta=16-32 nodos … alcanza=SI` y `nav=[… alcanza]`
  —un destino a 20 bloques con una ruta de **30 nodos**—. El contador medía **solo la distancia en línea recta** y la
  ronda es un **círculo**: en un rodeo la recta sube. Ahora cuenta también el **avance por la ruta viva**. MEDIDO:
  `Patrullando` **9 → 2**.
* **al objeto caído se iba a la celda CRUDA** (que puede ser la **mesa de la taberna**, una **valla** o el propio
  bancal): `casillaDePieCercaDe` en los cuatro goals que recogen **+** `desatascarSiEstaEncajado` antes de contar
  atasco. Y de raíz: **«pisable» no incluía los cultivos** (`esCeldaDePie` pedía aire y un bancal no tiene aire) →
  el granjero hundido en la farmland **no tenía a dónde salir**. MEDIDO: `Recogiendo el corral` **4 → 0**,
  `Guardando lo suyo` **5 → 0**, `Yendo a la taberna` **1-2 → 0**, el bucle del bancal **7 → 1**, y la **tasa del
  pueblo 0,50-0,60 → 0,30** por 1.000 ticks (`tools/arnes/medidas-atascos-sueltos.txt`).

### 8. Lo que queda, con su nombre y su número (27-sep-2026)

Con el código de hoy, la última corrida (`build/medida-clerigo2.log`, 15.400 ticks) deja **7 avisos de rendición,
TODOS SUELTOS** (el mayor, **2**) y **NINGÚN BUCLE** — contra los **18** de la referencia de la mañana, con bucles de
**16** y **19**—. La **tasa del pueblo** con este código: **0,30** de media (**0,10 · 0,30 · 0,50**, n=3) contra el
**1,29** de la tanda anterior. Eso es I135: la prueba de cada arreglo es **su etiqueta**, no el total.

| lo que queda | medida y por qué NO es de las clases arregladas |
|---|---|
| **RESUELTO: los viajes largos sin tirón** | el recolector al **almacén** desde la huerta (**70+ bloques**, `alcanza=NO`) se rendía en **bucle de 5-8 avisos**. Arreglado con **`VillageManager.elPuntoDeAhora`** (casilla de pie + **tramo**) midiendo el atasco **contra el tramo** (I140). MEDIDO: `Saliendo de la huerta` **8 → 1** |
| **RESUELTO: el bucle de la PLAZA** | el destino de «volver a la plaza» es **la campana del kiosco** (no se pisa) y el tramo calculado a mano **no comprobaba la ruta**: **10-15 avisos en bucle** con `cerebro=531,63,646` y `nav=[sin ruta]`. Arreglado con el **tirón del proyecto** (`tironConMemoria`). MEDIDO: **15 → 1** |
| **RESUELTO: el clérigo** | `Yendo a la iglesia` **2 → 0**: estaba **metido en la puerta** de la iglesia (`pies=oak_door`) con la ruta buena → casilla de pie + **`desatascarSiEstaEncajado`** (I122/I140) |
| **el ganadero** → **RESUELTO Y MEDIDO** (29-sep-2026, ver §12) | `Recogiendo el corral` con algo caído sobre **mobiliario** (una valla, la mesa): el punto de ahora se puso (2 → 1) y el filtro de celdas no pisables del §12 lo dejó en **0 en la ventana** (medido con `rendiciones.py --etiquetas`). Del ganadero quedan **otras** ramas (`Bajando lo del corral` 2 en la ventana), que son la clase de I119/I122 |
| **RESUELTO: el clérigo y su POI** (clase **I125**) | `Yendo a la iglesia` **2 → 0**: estaba metido en la **puerta** (`pies=oak_door` → desatasco) y además el cerebro iba a su **POI** (`cerebro=452,64,603` con `destino=453,64,603`). El avance por la ruta está ahora en **`VillageManager.avanzaPorLaRuta`** (el índice del nodo de la ruta viva), compartido con el guardia |
| **RESUELTO: el bucle de la PLAZA (otra vez)** | el aldeano que **ya estaba** en la plaza se quedaba a 3-4 bloques de **la campana** (que no se pisa) y la vuelta se medía contra ella → **5 avisos en bucle**. Ahora la llegada se mide contra el **punto de pie**: **5 → 0** |
| **lo que queda, y ya no es un bucle** | **8 avisos, todos sueltos** (el mayor, 2), de **siete aldeanos distintos**: tropiezos puntuales de cada oficio (un granjero buscando recambios, un leñador guardando lo suyo, un guardia yendo a entrenar…). No hay ninguno repetido: **cada aldeano, una vez** |
| **LA TASA, con su media (28-sep-2026)** | **ANTES de los dos arreglos de abajo: 0,83** de media (0,10 · 1,50 · 1,00 · 0,70, cuatro corridas del MISMO código, con `medida-tasa-hoy` como la anómala buena). **DESPUÉS: 0,25** (0,30 · 0,20) y con el filtro del ganadero **0,40** → contra el **0,30** del código de antes de hoy y el **1,29** de la tanda anterior. **Y los dos bucles que la media destapó están a CERO**: `Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147) |
| **LA LECCIÓN: UNA CORRIDA NO DISTINGUE UN TROPIECO DE UN BUCLE** | el §8 daba esos dos bucles por «tropiezos puntuales» **porque se miró una corrida**; con cuatro, las etiquetas se repiten y la media sale **peor** que la del código anterior. Para afirmar algo, la media de 2-4 |
| **el hundimiento en la farmland** (0 avisos) | el bucle **desaparece** (46 desatascos reales en una corrida, 40 de **puertas**) y ya no se repite |
| `Yendo a entrenar` (0-1) | clase **I119**: **rendirse ahí es correcto**; lo que falta es que el puesto sea alcanzable |
| **la tasa** | **HECHA, con su dispersión**: las cinco corridas del código final dan **0,10 · 0,20 · 0,30 · 0,50 · 0,60** (media de la tanda buena, **0,30**) contra el **1,29** de la tanda anterior. La prueba de un arreglo es **su etiqueta** |

**Y DOS LECCIONES DE MÉTODO de esta sesión**, las dos pagadas con su corrida:
1. **Una regla del pueblo NO se mete en el método compartido si el que camina y el que cuenta no están de acuerdo.**
   Meter la casilla de pie y el tirón dentro de `caminarHacia` «para que valiera para todos» **empeoró** las cosas
   (avisos **10 → 11**, tasa **0,60 → 1,21**) porque los goals seguían midiendo el atasco contra el destino final. El
   arreglo bueno es el **contrato**: `elPuntoDeAhora` + medir **contra ese punto** (I140).
2. **La prueba de un arreglo es su etiqueta, no el total.** La corrida que pareció una regresión (11 avisos) estaba
   diciendo la verdad, y la que dio tasa 0,15 tenía solo 1 rendición en la ventana: con esa varianza, el total no
   distingue nada.

**Y el aviso de rendición del guardia, cuando el cerebro va a otra parte** — **arreglado y medido** en esta misma
sesión: los 2 avisos de guardia patrullando tenían `cerebro=` apuntando a **otro sitio** que el `destino=` (I125: el
paseo o los portones le pisan el rumbo); el guardia salta el puesto (correcto) pero **se contaba como rendición** y
ensuciaba la tasa. Ahora solo se apunta y se canta **si el cerebro va de verdad al destino**: `Patrullando` **9 → 0**.

### 9. El SEGUNDO POZO de la mina: **HECHO Y MEDIDO** (I144, 28-sep-2026)

Lo pidió el jugador: *«estaría bien abrir un segundo pozo»*. **Implementado, y el minero lo cava solo** cuando el
primero llega a su tope.

**MEDIDO** (`build/medida-pozo2.log`; el arnés da el pozo 1 por terminado a los 20 s con su piedra labrada de tope):
el censo `POZO` **pasa a medir el eje `437, 675`** (el segundo pozo), el minero **cava su caracol** (`caracol paso 12
… 16 en 437/436/…/433, 679`, **16 pasos**), y su solar sale **sin una celda de agua** (el pozo 1 tiene 2 y se le
inundaron los pasos 35-44) y **sin nada construido**. Crudo en `tools/arnes/medidas-pozo2.txt`.

**La implementación** (pequeña, porque todo depende de `centroDeLaMina`): `MINA_OFFSETS = {(33,0,-29), (-33,0,29)}`,
`centroDeLaMina(center, pozo)`, y `centroDeLaMina(center)` lee **el pozo activo** de un caché por aldea (como el de
`cotaDeLaPlaza`), así que **ninguna firma cambió** y el minero solo añadió **una línea** (`elegirElPozoActivo`). Las
**dos comprobaciones de exclusión** miran **todos** los pozos (si no, el nivelado habría rellenado el caracol del
segundo y el tapagujeros, su boca).

**Y UN ERROR DE MEDIDA MÍO, que queda escrito**: la primera tabla de candidatos la medí **desde el eje del pozo 1
(503,617) creyendo que era el centro de la aldea**. El centro es **470,63,646** y `503,617` es `centro + (33,-29)`, o
sea **el eje del pozo 1**; con ese error, el «suroeste» que elegí (470,646) era **la propia plaza** y «el acuífero del
pozo 1» un artefacto. Medido bien: **los cuatro candidatos tienen 0 agua** en la galería del paso 16 y **el criterio
que decide es la separación** — el simétrico `(-33,+29)` deja **30** bloques entre las dos zonas de exclusión y los
otros dos solo **8**. El offset elegido **era el correcto** (el eje que el juego calcula, `437,675`, es el medido) y
**no hubo que tocar el código**.


### 10. `Yendo a entrenar`: **ARREGLADO Y MEDIDO** (I145, 28-sep-2026) — los guardias entrenan

Era el único caso con etiqueta que seguía saliendo. **La causa**, medida en los logs: el puesto del patio
(`424,63,675`) queda a **68 bloques** del guardia y la región de búsqueda del planificador son **56** → caminando
**directo** (como hacía esa rama) **no hay ruta ninguna**; se rendía (con razón) y **no entrenaba**. **El arreglo**:
caminar con el **tirón** (`elPuntoDeAhora`, el contrato I140, con **la plaza** como hub —sin ella el tirón devuelve el
propio destino—) y medir el atasco **contra el punto**.

**MEDIDO** (`build/medida-entreno.log`): la marca `entrenado=` **sube en los 6 guardias** en la misma corrida
(+2.286 a +3.349 ticks cada uno, y esa marca solo sube **delante de la diana**), y el aviso baja de **uno por guardia**
a **2 en total**, los dos **transitorios** (se rinden una vez a 6-7 bloques del puesto y **en el turno siguiente
entran**). Detalle en `tools/arnes/medidas-entreno.txt` / la invariante **I145**.

**Lo que queda de este caso**: el último salto al puesto (los 6-7 bloques) no tiene ruta desde el tramo; es un tropiezo
puntual, no un bucle.

### 11. LA POSADA SIN ESCALERA: el latido sube aldeanos a una planta de la que NO SE PUEDE BAJAR (28-sep-2026) — **REVISADO el 29-sep-2026: la premisa no se sostiene y lo que queda es OTRA cosa**

> **AUDITORÍA DEL 29-sep-2026 (lo pidió el jugador: «¿cómo que pendientes documentados?»).** Tres cosas, medidas:
>
> 1. **La premisa (una planta SIN salida) no se sostiene en la aldea actual**: la taberna se construye **abriendo su
>    hueco de subida** —el propio log lo canta al levantarla: *«desván vaciado (833 teja(s) de relleno) y hueco de
>    subida abierto (6 celda(s)); se pisa en y=…»*—, así que su planta de arriba **tiene escalera**. El diagnóstico
>    original era de la **posada vieja** del mundo anterior (`511,68,667`) y se leyó mal el mapa de capas.
> 2. **Lo que queda medido hoy NO es eso**: en la ventana de `build/medida-tanda18.log` hay **3 rendiciones con la
>    etiqueta `Yendo a la taberna`** (2 granjeros y 1 pescador), y el aviso trae **`cerebro=566,64,566`** (¡la plaza!)
>    o **`cerebro=-`**: el aldeano **no iba a la taberna**, le ganaba el `WALK_TARGET` que escribe su propio cerebro
>    (I119/I122). El «destino» que imprime el aviso (`dark_oak_fence` + `oak_pressure_plate`) es **el mobiliario de la
>    mesa**, que por diseño no se pisa: el goal ya camina a `casillaDePieCercaDe` (I114/I131), así que ese dato no es
>    el fallo.
> 3. **Y LOS TRES ARREGLOS QUE SE PROBARON SE RETIRARON** (disciplina: lo que no mejora, fuera). Los tres, medidos en
>    la **misma ventana** y sobre el mismo guardado, contra el baseline de `medida-tanda18.log` (**7** rendiciones,
>    **0,70** por 1.000 ticks):
>
>    | intento | qué se hizo | ventana | total de la corrida | veredicto |
>    |---|---|---|---|---|
>    | **tanda 21** | guardián de I146 («si el cerebro va a otra parte, no cuentes el atasco») en taberna, recojo y ganadero + filtro de ítems en celdas no pisables | **19** (2,70) | 62 | las 7 de la taberna/recojo/ganadero se van a 0, pero aparecen **12 de granjeros** (`Abono la huerta` 5, `Labrando` 4, `Sembrando` 3) |
>    | **tanda 22** | quitarle el rumbo al cerebro (`parar`) y volver a mandar al aldeano, en los tres | **13** (1,30) | 64 | la taberna baja **3 → 1**, el ganadero NO se mueve (2+2) y aparecen granjeros (`Sembrando` 5) |
>    | **tanda 23** | **dejar de perseguir** (la dirección que arregló §12): la mesa cuya casilla de pie no existe se apunta como fallida; el ítem en celda no pisable no se elige | **35** (3,50) | 52 | peor: `Entrando a la huerta` **12**, `Labro la huerta` 7… y **vuelve** `Recogiendo el corral` |
>
>    **Las tres rutas —insistir, forzar el rumbo y rendirse antes— empeoran el pueblo.** El baseline (7, con **cada
>    aldeano una o dos veces y ninguna etiqueta repetida más de 2**) es el mejor estado medido. Y encaja con lo que el
>    propio doc tiene escrito en §8: **una corrida no distingue un tropiezo de un bucle**, y estos son **tropiezos
>    sueltos** (el mecanismo «rendirse = dejarlo por un rato», I33, funcionando), no bucles: los bucles de verdad
>    —`Saliendo de la huerta` (I146) y `Recogiendo el corral` (I147)— **están a cero** y así siguen.
>
> **CONCLUSIÓN: aquí se para.** El pendiente que queda NO es un bucle y no se arregla tocando estos goals: cada
> intento mueve el problema a la huerta. Lo que haría falta es averiguar **por qué** esos aldeanos concretos se quedan
> —el caso del ganadero es el más claro: `cerebro=-`, **sin destino ninguno en el cerebro**, con la etiqueta puesta— y
> atacarlo **con la media de 2-4 corridas** (§8), no con una. Queda escrito con sus números para no repetir los tres
> caminos que ya sabemos que van peor.


**El caso, medido** (`build/medida-tasa-hoy.log`): la leñadora daba **dos avisos** de rendición con
`nav=[sin ruta]`, **`cerebro=-`**, `suelo=dark_oak_planks` y su posición en **`511,68,667`** — y la ruta que tenía era
**de un solo nodo: ella misma**.

**La causa raíz, medida con el mapa de capas** (`build/slice_mina.py`, sobre el guardado del jugador):

* En **`y=68`** está la planta de arriba de la posada: sus **camas** (`R` = `red_bed`) y sus muros, **cerrada** por
  muros de tierra en todo el perímetro.
* En **`y=67`** —el suelo de esa planta— **es tierra maciza (`D`) en TODA la planta**, con troncos (`L`) en los
  bordes: **no hay un solo hueco de escalera**. Esa planta **no tiene salida**.

**Y el aldeano llegó ahí por el propio pueblo**: el latido (`VillageManager.acostarAlQueNoLlega` / la celda de espera
de I43) **mueve** al aldeano hasta su cama cuando no llega andando — y si la cama libre que le toca está **arriba**,
lo deja **arriba**, en una planta de la que el planificador **no encuentra salida** (`nav=[sin ruta]`, la ruta de un
nodo). Después de los avisos **acaba saliendo** (en esa corrida siguió con su oficio: `Guardando lo suyo`), así que no
es un bucle de los de antes, pero **el agujero está ahí**.

**Y NO se arregla con lo que ya se probó**: el doc de I43 ya dejó medido que exigir «misma planta y ≤3 bloques» para
la celda de espera **empeora** las cosas (`CAMAS RESUMEN: … SIN CAMA` porque las únicas libres eran las de la posada).

**Lo que toca** (elegir uno, ninguno hecho): **(a)** que el latido, si el aldeano está en una celda **sin ruta viva**
(el mismo caso que él creó al subirlo), lo **baje** a una celda con ruta —el mecanismo de mover los últimos bloques ya
existe y ya se usa para subirlo, así que es el arreglo coherente—; **(b)** que las camas de una planta **inaccesible**
no entren en el reparto (midiendo antes que no deja `SIN CAMA`); o **(c)** dar **escalera** a esa planta en el
generador (es construcción: obliga a migración del mundo ya construido).

### 12. EL GANADERO Y SU VALLA: el destino del corral es una celda que NO SE PISA (28-sep-2026) — **bucle medido**

El §8 daba esto por «tropiezo puntual» («`Recogiendo el corral` con algo caído sobre mobiliario»). **NO es puntual: es
un bucle**, y está medido en `build/medida-tanda1.log` (**7 rendiciones en la ventana**, 12 en toda la corrida, todas
de la misma ganadera):

```
no consigue llegar a 513, 63, 640 desde 513, 63, 641
   (ruta=1 nodos hasta 513, 63, 641 alcanza=NO; pies=air cabeza=air suelo=grass_block
    | destino=oak_fence encima=oak_fence)            <- el destino es una VALLA, con OTRA valla encima
no consigue llegar a 514, 63, 640 desde 514, 63, 641
   (... | destino=oak_fence_gate encima=oak_fence)
```

**La causa**: el destino que se calcula para «recoger el corral» cae en una celda **ocupada por la valla** (y encima
tiene otra): **no es pisable**, así que la ruta es **de un nodo** y el aldeano **se rinde siempre**, en bucle.

**Y el mapa del corral lo confirma** (`build/slice_mina.py 508 520 634 646 62 64`, sobre el guardado): el corral tiene
**vallas dobles** (`O` en `y=63` **y** en `y=64`, el muro del corral) y dentro hay **fardos de heno** (`H`). El destino
`513,63,640` **es la valla sur**: o sea que hay un **ítem dentro de la valla** —un **huevo**—, inalcanzable.

**Y por eso es un bucle aunque haya «punto fallido»**: cada huevo que pone la gallina es un **ítem NUEVO**, así que el
punto apartado no cubre al siguiente. **El arreglo que toca**: al elegir el ítem suelto, **descartar los que están
dentro de un bloque** (su celda no es aire): no se pueden recoger y solo sirven para que el ganadero se rinda.
**Criterio**: `Recogiendo el corral` **7 → 0** en la ventana.

> **RESUELTO Y MEDIDO (29-sep-2026).** El arreglo está en `VillagerAnimalFarmGoal.buscarDropEnElCorral` (descarta el
> ítem si su celda **no es una casilla de pie**, `VillageManager.esCeldaDePie`, I147), y el criterio **se cumple**:
> medido con el instrumento de siempre, `python tools/arnes/rendiciones.py --etiquetas build/medida-tanda18.log`,
> **`Recogiendo el corral` ya NO aparece en la ventana (2.000–12.000)** y en la corrida entera (22.000+ ticks) queda
> **1**, contra las **7 en la ventana / 12 en la corrida** de la medida original. La ventana de esa corrida suma **7
> rendiciones** y ninguna es esta: `Bajando lo del corral` 2, `Recogiendo lo suyo` 2, `Yendo a la taberna` 3 (ver §11).

**Y EL RECOLECTOR EN EL BANCAL** (mismo log, 6 en la ventana): se rinde desde `484,62,658` con **`pies=farmland`** y
`cabeza=wheat` —o sea **encajada dentro del cultivo**, un bloque POR DEBAJO de la cota—, y ahí mismo tiene una ruta a la
campana que **SÍ alcanza** (`ruta=23 nodos … alcanza=SI`). Hay que ver si es el mismo falso positivo de I146 (el
cerebro en otra parte) o el desatasco del bancal.

### 1. El `JOB_SITE` del minero (el cortapiedras, un bloque): **medido, y no cuesta nada** (cerrado)

Medido en la corrida en la que la mina ya funciona de punta a punta (297 muestras del minero, `t=11.720`):

| qué | medida |
|---|---|
| muestras con `destino=SIN DESTINO` (el cerebro le borra el rumbo al pelear con el `WorkAtPoi`) | **48 / 297 (16 %)** |
| muestras con `goals=[]` (el goal, entre ciclos) | 33 / 297 |
| `Volviendo a la caseta (encajado)` | **0** (solo salía mientras la mina estaba clavada en el acuífero) |
| ciclo del minero | 16 pasos de caracol, 6 celdas de galería, 4 sellos, **1 tope**, 31 entregas en el almacén, **0 picos rotos** |

O sea: **la pelea del `JOB_SITE` es real pero NO bloquea nada** (ni un paso, ni una entrega); y el `rutaFaena` que me
hizo pensar lo contrario medía **la ruta a un bloque**, no a la caseta. Queda **cerrado** como cosmético, con una
**residuo** apuntado: con la mina ya en el **tope**, el minero se queda por el almacén (`516,63,663`) repitiendo
`deja lo sacado` / `Guardo 18 de lo suyo` — no hace nada útil porque **su mina está acabada**; si se quiere, lo
siguiente sería darle una faena de reserva (o que ayude en otra aldea/puesto), pero eso es una decisión de diseño,
no un fallo medido.


Estado medido en la última corrida (`TOPE=SI` incluido): el minero **baja, cava 16 pasos (16 → 32), se topa con el
acuífero, sella, cierra la mina con su piedra labrada y SUBE a entregar** — acabó en `516,63,663` con la etiqueta
`Guardo 18 de lo suyo` y **0 picos rotos** (el viaje al almacén funciona).

**Y el "la caseta no se alcanza" era un fantasma de MI INSTRUMENTO**, cazado con la `SONDA DE LA CASETA` nueva (el
arnés pregunta al planificador del juego, celda a celda, de fuera adentro):

```
[Arnes] SONDA DE LA CASETA t=400 pos=499,54,621 apoyo=503,63,617 puesto=501,63,615
  503,63,621(air/grass_block)=28n/SI   503,63,620(air/stone_bricks)=29n/SI
  503,63,619=30n/SI   503,63,618=31n/SI   503,63,617(apoyo)=32n/SI
  501,63,614(stone_bricks/stone_bricks)=34n/NO fin=501,64,615
```

El **apoyo de la caseta SÍ se alcanza** (32 nodos, entrando por su puerta sur) y el modelo también lo encontraba (57
pasos). Lo que **nunca** se alcanza es la celda del **`JOB_SITE` = el CORTAPIEDRAS** (`501,63,615`, **un bloque**):
`reclamarElPuesto` se lo pone al cerebro en la celda del puesto, y el `WorkAtPoi` de vanilla manda al aldeano **a esa
celda**, que no se puede pisar → el `rutaFaena` que yo medía era **la ruta a un bloque** (`fin=… dFin=4,00/7,07`) y de
ahí salió el diagnóstico equivocado. Lo que sí provoca es una **pelea**: el goal escribe su destino cada tick y el
cerebro lo pisa con el del puesto → `mejorDistancia` no baja → `stuckTicks` sube → **"Volviendo a la caseta
(encajado)"** en bucle... **pero eso era mientras la mina estaba clavada en el acuífero**: con la mina funcionando
(la corrida del recuadro de arriba) **no sale ni una vez** y el minero cumple su ciclo entero. La conclusión medida es
la del recuadro: **cosmético, cerrado**.

### 2. Los GRANJEROS con la mata: **ARREGLADO y MEDIDO** (26-sep-2026)

**El fallo (medido, y no era la mata)**: cuando el granjero ya estaba pegado a la compuerta, el juego le daba el
destino **con tolerancia de 1 bloque** (`caminarHacia` pone `WalkTarget(..., 1)`), así que el planificador **lo daba
por llegado**, le devolvía una ruta de **un solo punto** (la celda donde él ya estaba) y **no daba ni un paso**: el
tramo de la compuerta no avanzaba, a los 120 ticks **aparcaba esa entrada** y probaba otra puerta —hasta las cuatro—
y se rendía. Medido: los **6 avisos de una corrida, todos a distancia 1**, y **5** `no consigue entrar al bancal`.

**El arreglo**: `VillageManager.caminarHaciaExacto(...)`, un caminar **con tolerancia 0** (y que además le pide la
ruta a la navegación a mano, porque el cerebro puede escribir su propio destino en el mismo tick). Se usa en los dos
tramos del granjero que exigen **pisar** una celda: **entrar** por la compuerta y **salir** del bancal.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| `no consigue entrar al bancal …` | **5** | **0** |
| avisos de `Entrando a la huerta` | **6** | **0** |
| el granjero trabajando | se rendía en la puerta | **`Valeriano (Granjero) / Cosechando`** y entregas a la despensa de **73, 71, 45 y 14** |

*(Lo que quedaba apuntado dos veces como "intento medido y retirado" —el contador de atasco y "un paso más adentro"—
está en `tools/arnes/medidas-mina-sellada.txt` §11: los dos fallaron y se quitaron, y fue esa medida la que dejó a la
vista que el problema era la tolerancia del caminante.)*

### 3. La RECOLECTORA: **ARREGLADA y MEDIDA** (se quedaba encerrada en el bancal)

**Medido**: **19 rendiciones** en `Volviendo a la plaza` con `ruta=1 nodos … alcanza=NO` **desde dentro de un bancal**
(`pies=farmland cabeza=wheat`): entra a los bancales a por lo que se cae —su faena— y **no puede salir**, porque una
**puerta de valla cerrada no es navegable** para el juego y nadie se la abre.

**El arreglo**: `VillageManager.abrirLaCompuertaDeAlLado(...)` (compartida con el granjero) + en la recolectora, si
está dentro de un bancal, mandarla a la **celda de dentro de la compuerta más cercana** con `caminarHaciaExacto` y
**abrírsela** en cuanto la tiene al lado (probando otra si esa está aparcada).

**MEDIDO**: rendiciones del pueblo **27 → 9**; `Filomena / Volviendo a la plaza` **19 → 0**; y ahora **sale y
entrega** (`abro el porton 484,63,659 · destino=470,63,646 rutaViva=24 nodos alcanzaba=SI`).

### 4. El GANADERO: **ARREGLADO y MEDIDO** (perseguía una celda que no se pisa)

**Medido**: `Vicenta / Cuidando el ganado` **3 avisos**, destino a 4-6 bloques con `ruta=1 nodos … alcanza=NO`. Arreglado con `casillaDePieCercaDe` (la regla de I114: se camina a una **casilla de pie**, no a la celda cruda del animal): **`Cuidando el ganado` 3 → 0** y las rendiciones del pueblo **9 → 5**. Detalle en **I131**. Lo de abajo queda como el análisis original:: el destino es una **celda de aire a 4-6 bloques**
(`525,63,651`, `526,63,648`, `524,63,642`, todas con `grass_block` debajo) y el planificador devuelve **`ruta=1 nodos
… alcanza=NO`**, o sea **no se llega**. `cerebro=-` (sin destino en el cerebro) y `nav=[sin ruta]`:

```
no consigue llegar a 525,63,651 desde 525,63,656 (ruta=1 nodos … alcanza=NO; suelo=grass_block)
    etiqueta="Vicenta (Ganadero) / Cuidando el ganado" cerebro=- nav=[sin ruta] goals=[VillagerAnimalFarmGoal]
```

Es **la misma clase que la recolectora (I130)**: su faena está **al otro lado de una cerca** (el corral: va a por un
animal o a su punto de apoyo) y **la puerta de valla cerrada no es navegable** → no hay ruta. **El arreglo es el
mismo**: en `VillagerAnimalFarmGoal`, si la celda a la que va está al otro lado del **portón del corral**, mandarla a
la celda de dentro del portón con **`caminarHaciaExacto`** y **abrírselo** con
`VillageManager.abrirLaCompuertaDeAlLado` (probando el del gallinero si el del corral está aparcado).

**LEÑADOR — `Tomasa / Llevando la madera` (2 avisos)**: la ruta al almacén **SÍ alcanza**
(`ruta=14 nodos hasta 517,63,666 alcanza=SI`, `cerebro=517,63,666`, `nav=[14 nodos … alcanza]`) y **aun así se
rinde**: la clave está en sus pies — **`pies=dark_oak_fence`**: está **metida DENTRO de un bloque de valla** (con una
placa de presión encima, `cabeza=oak_pressure_plate`), así que **no puede moverse** por mucho que tenga ruta. Es un
caso físico (¿empujada por un animal, ¿encajada al cruzar?), no de tolerancia ni de puerta: **lo que toca es que el
goal detecte que está encajada** (la celda de los pies no es una casilla de pie) y, en vez de rendirse y aparcar el
almacén, **dé un paso de desatasco** (o se teletransporte 1 bloque si el juego no la deja salir).


### 5. El LEÑADOR metido en un bloque: **ARREGLADO y MEDIDO** (a la tercera)

El caso que se midió era: ruta **buena** al almacén (`nav=[14 nodos … alcanza]`) y aun así se rendía con
`pies=dark_oak_fence`, o sea **con los pies dentro de una valla**. Dos criterios fallaron antes (los dos retirados):

| criterio | medido |
|---|---|
| `esCeldaDePie` | **940 falsos positivos**: exige suelo `isSolid()` y quien está **de pie encima** de una valla o una placa no lo cumple → lo daba por encajado y lo bajaba un bloque (y volvía a subir) |
| la **caja** del aldeano contra la forma | **0 disparos**: una valla es un **poste fino en el centro** de la celda y pegado al borde la caja no lo corta |

**El criterio bueno**: comparar la **forma de colisión** de la celda de los pies con la **altura de los pies** del
aldeano —si la forma sube por encima de sus pies, está **DENTRO** del bloque— y sacarlo a la casilla más cercana
donde se pueda estar de pie (pies y cabeza libres y el bloque de abajo con forma, sin exigir `isSolid`), con **freno
de 200 ticks** por aldeano. **MEDIDO**: 2 desatascos, los dos **reales**, sin un solo falso positivo:

```
estaba METIDO en 510,63,667 (dentro de dark_oak_fence): lo saco a 509,63,666
estaba METIDO en 512,63,666 (dentro de dark_oak_door):  lo saco a 511,63,666
```

y **`Tomasa (Leñador) / Llevando la madera` desaparece** de los avisos. Está en `VillageManager.desatascarSiEstaEncajado`
y se llama desde el leñador (los demás goals pueden usarlo igual: es un ayudante compartido).


1. **Con `esCeldaDePie`**: 940 falsos positivos (daba por encajado a quien estaba de pie **sobre una valla**) y las
   rendiciones subieron a 18 → retirado.
2. **Con la CAJA DE COLISIÓN** (el criterio correcto para "metido dentro", con freno de 200 ticks): disparó **0
   veces** —ni un falso positivo— pero **tampoco caza el caso**, y las rendiciones quedaron en 12 con
   `Tomasa (Leñador) / Llevando la madera` **x4** → retirado.

**Lo que esto deja MEDIDO**: el leñador **no está encajado** (su caja no corta la valla, solo pasa por su celda) y
tiene la ruta **buena** (`nav=[14 nodos … alcanza]`): es el caso de **I119/I122** ("la ruta alcanza y el aldeano no
se mueve"). Para atacarlo hay que medir lo que **aún no se ha medido**: su **velocidad y posición tick a tick**
mientras tiene esa ruta buena (¿empuja contra la valla? ¿le tapa el paso un animal?).


Se probó un desatasco automático (`desatascarSiEstaEncajado`: si los pies no son una casilla de pie, sacarlo a la
más cercana) llamado desde el leñador. **Medido: 940 desatascos en una corrida** —teleportaba aldeanos que estaban
**bien**, de pie sobre una **valla o una placa** (`estaba ENCAJADO en 510,64,669 (pies=air)`) porque
`esCeldaDePie` no acepta una valla como suelo— y las rendiciones **subieron a 18**. **Retirado**; el árbol está como
estaba. Lo que queda apuntado: el criterio de "encajado" **no puede ser `esCeldaDePie`** (falsos positivos con
vallas, placas y losas): hay que mirarlo con la **caja de colisión** de la entidad contra el bloque de los pies, o
exigiendo que **no se haya movido en N ticks**, y **nunca teleportar por sistema**.
### 6. El PICO al romperse: **CERRADO Y MEDIDO** (27-sep-2026) — el minero **vuelve con otro**

> **CERRADO en la sesión del 27-sep, y sin tocar una línea del mod**: la pregunta que quedaba abierta («suelta la
> faena, **¿pero vuelve con otro pico?**») **ya estaba medida en los logs de la sesión**, cuatro veces, porque el
> arnés deja el pico al borde de romperse cada 2.000 ticks dentro de `medirElMinero` (invariante **I141**).
>
> | corrida | roturas | `pico nuevo` en su mano | forjados por el herrero |
> |---|---|---|---|
> | `medida-tramo.log` | 6 | **6** | 6 |
> | `medida-s7-final.log` | 6 | 5 | 5 |
> | `medida-balsa-final.log` | 6 | 5 | 5 |
> | `medida-s8.log` | 8 | **8** | 7 |
>
> El ciclo, tal cual sale en el log: `se le ha roto el pico (59 usos): va a por otro al almacen` → `Yendo al almacen`
> → espera (`pico=SIN PICO` con `Cargando material`) → **`El minero: pico nuevo: minecraft:wooden_pickaxe`**.
>
> **Y la reserva ya existía en el código** (por eso no hubo que añadir nada): el **pico va PRIMERO** en el herrero de
> herramientas (`recetaDeArmadura` → `recetaDePico`, con `OBJETIVO_PICOS = 2`) y `recetaDePico` **baja de nivel** hasta
> el **pico de madera** cuando no hay hierro — el «cebo del pico roto». MEDIDO: **4 picos de madera y 2 de piedra**
> forjados, **6 de madera** recibidos por el minero, con el almacén en `0 lingote(s), 0 crudo(s)`.
>
> **La pista que queda (el pico de madera)** — **el problema MEDIDO, el arreglo puesto, y la mejora SIN medir**
> (28-sep-2026, y así se dice): el minero cogía el pico con un filtro que aceptaba los cinco materiales y
> `VillageStorage.quitar` devuelve **el primero que cumpla**, así que **con madera y piedra en el almacén se llevaba
> la madera**. MEDIDO (`build/medida-plaza3.log`): el herrero forjó **4 madera y 2 piedra** y el minero recibió **6 de
> madera** — **ignoró los dos de piedra**. Arreglo: pedirlos **en orden de mejor a peor** y quedarse el primero que
> haya (idéntico con un solo material; la corrida del 28-sep, con el almacén a **0 adoquín**, dio **8 de madera**,
> igual que antes). **LO QUE FALTA ES LA MEDIDA DE LA MEJORA**: en esa corrida no había ningún pico mejor que
> preferir. **Cómo medirlo**: desde el arnés, **poner un pico de piedra en el almacén** al empezar, y leer
> `pico nuevo:` → el criterio es que diga **`stone_pickaxe`** con la madera también en el almacén.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):


Con la receta del arnés (dejar el pico al borde cada 2.000 ticks) **medido** en una corrida: el pico se rompió
**4 veces** y las cuatro el mod hizo lo que tiene que hacer:

```
[Arnes] PICO t=2000 al borde de romperse (58/59)
[Village] El minero: se le ha roto el pico (59 usos): va a por otro al almacen
```

**Pero el almacén no tiene de dónde darlo**: `ALMACEN DE LA MINA t=9000: 2 adoquin, 0 carbon, 0 lingote(s),
0 crudo(s), 6 pedernal, 0 pico(s)`. O sea que el eslabón roto es **el herrero de herramientas** (no forja picos) y,
detrás, **el hierro**: no hay lingotes **ni mineral crudo**, porque la mina de esta partida **se cerró en el
acuífero** (I127) y el hierro salía de ahí. Lo que toca medir ahora: si el herrero **tiene con qué** (hierro en el
almacén) y, si no lo tiene, **de dónde debería salir** (¿la mina se cierra demasiado pronto? ¿falta reserva de
picos?). Y una pieza de diseño que sale de aquí: **el almacén debería tener SIEMPRE un pico de reserva para el
minero** (hoy `VillageStorage.asegurarElPicoDelMinero` le da **uno** al construir la mina y nunca más).


El minero ya **suelta la faena** al quedarse sin pico (`canContinueToUse`), pero **no está medido** que vuelva con
otro: en las corridas de esta sesión **no rompió ninguno** (`se le ha roto el pico` = 0). Hay que **forzar el caso**
desde el arnés, **dentro de `medirElMinero` y solo ahí** (el mismo ancla `var wt = v.getBrain()...` existe en **6**
volcados: si se pega en todos, se daña el pico desde 6 sitios a la vez):

```java
var enMano = v.getMainHandItem();
if (ticks % 2000 == 0 && !enMano.isEmpty() && (enMano.is(Items.WOODEN_PICKAXE)
        || enMano.is(Items.STONE_PICKAXE) || enMano.is(Items.IRON_PICKAXE))) {
    enMano.setDamageValue(Math.max(0, enMano.getMaxDamage() - 1));
    DevilRpg.LOGGER.info("[Arnes] PICO t={} al borde de romperse ({}/{})", ticks,
            enMano.getDamageValue(), enMano.getMaxDamage());
}
```

**Qué se lee**: `se le ha roto el pico` → suelta la faena → `Yendo al almacen` / `Cargando material` → los **picos
del almacén** (`ALMACEN DE LA MINA … pico(s)=N`, que los forja el herrero de herramientas) → y en las líneas
`MINERO` vuelve a salir `pico=minecraft:…` en la mano. Si el almacén no tiene picos, el que hay que mirar es **el
herrero**, no el minero.

**Y OJO CON EL INSTRUMENTO**: en esta sesión lancé esa corrida con `MEDIR_MINERO = false` y la medida salió en blanco
(`al borde: 0`): **comprobar que el modo está encendido en la copia del arnés** antes de lanzar.
### 6b. LO QUE PIDIÓ EL JUGADOR PARA LA MINA (**los cuatro HECHOS y MEDIDOS**)

1. **La mina atraviesa el agua** — **HECHO y MEDIDO** (I132): ya no se cierra cuando hay agua; **aísla** con paredes
   (cáscara 3×3×3 de adoquín: sella también el agua de delante, así el túnel avanza por celdas secas), **seca** la
   celda (queda de aire, transitable) y **sigue bajando**. Medido: `1:air 2:air 3:air`, `hechas=3/24` (antes clavado
   en `0/24`) y **`TOPE=NO`** (antes `TOPE=SI`, la mina se cerraba).
2. **Que el pico lo haga el HERRERO** — **HECHO y MEDIDO** (27-sep-2026, la cadena entera): ver **I137**. El herrero
   de herramientas **forja picos** y el del **hierro** lo mide la cadena completa: **el guardia mata el zombi de raid,
   lo lootea** (`Dorotea (Guardia espadachín · nv 1) … lleva 2 pepitas`), **lo deja en el almacén** (13 depósitos; el
   almacén de **0 a 20** pepitas) y **el herrero forja el pico de HIERRO** (`Forjo un pico de hierro`, y el almacén con
   `picos (por material: … hierro 1)`), que se lleva el minero. Y hacían falta dos cambios que ya estaban: **(a)** el
   **recolector** recoge las **pepitas de hierro** del suelo (`esDelPueblo` no las tenía); **(b)** el **pico de hierro
   se puede forjar con 27 pepitas** cuando no hay 3 lingotes (vanilla: 9 pepitas = 1 lingote).
3. **Hierro de los zombis de los raids** — **HECHO y MEDIDO** (27-sep-2026, I137): la cadena que pidió el jugador
   (raid → guardia → hierro → el herrero) está medida eslabón a eslabón. **El eslabón que faltaba en el mod era el
   guardia**: looteaba y **se quedaba el hierro en el zurrón** (medido: de t=300 a t=2.700 con el almacén a 0, porque
   su goal no tenía ningún paso que lo dejara) → arreglado con `VillagerGuardGoal.dejarElHierroEnElAlmacen` (el guardia
   **va** al almacén cuando lleva hierro). Y el instrumento tenía **tres trampas** que daban un falso "no funciona":
   contaba pepitas **en el suelo** (el mod se las da **al que mata**), la **barredora** del arnés **descartaba** el
   zombi plantado (`discard()` no es morir: ni botín) y el escaneo de zurrones era de **140** bloques (el que las
   llevaba se iba al muelle). Todo en `tools/arnes/medidas-pepitas.txt`.
4. **La LUZ de la mina** (28-sep-2026, **I142**) — *«el minero no está poniendo antorchas en las paredes de las
   escaleras de caracol ni en las galerías … se ve muy oscuro y es un punto peligroso para que spawneen mobs»*.
   **Era verdad y está medido**: la antorcha se pone **al cavar la celda**, y `ponerLaAntorcha` **sale sin poner nada
   si en ese momento no lleva** — y el minero **cavaba antes de tenerlas** (el log: cavó la celda 8 de la galería a
   las 03:31 y fabricó las antorchas a las 03:34) y **nunca repasaba**. Censo del arnés: el caracol con sus pasos
   **0, 8, 16, 24 y 32** en `air` (ni una antorcha), la galería con sus celdas 8 y 16 en `+1=air`, y el minero con
   **8 antorchas en el zurrón SIN GASTAR**. Arreglo, dos partes: **(a) la fase `ENCENDER`** (`buscarHuecoDeLuz`)
   repasa **del frente hacia la boca** y pone la antorcha que falte antes de seguir cavando; **(b) no se baja sin
   luz**: `hayQueSubir` pedía luz solo con `celdasCavadas > 0`, así que la **primera bajada** era a oscuras → ahora la
   pide también en el primer viaje **si el pueblo puede dársela** (antorcha hecha, o **carbón/carbón vegetal de un
   tronco** —la leña del leñador— o leña, siempre con palos; sin esa guarda, subir sería un bucle). MEDIDO: los 5
   pasos del caracol y la celda 8 de la galería pasan de `air` a **`wall_torch`**, y el minero **gasta** las antorchas
   (`El minero: encendio 499, 55, 629`, etiqueta `Enciende la mina`).

### 6c. La RECOLECTORA en la TABERNA: **ARREGLADA y MEDIDA** (16 → 0)

El atasco más grande que quedaba. **Causa medida**: `VillagerTavernGoal` caminaba a la **celda de la mesa** con el
caminar de tolerancia 1, y esa celda **no es pisable / está un nivel más arriba**, así que el planificador devolvía
`ruta=1 nodos … alcanza=NO` y el aldeano empujaba hasta rendirse (`destino 516,64,639 desde 516,63,641`). Es la regla
de **I114/I131**: **se camina a una casilla de pie**.

**Arreglo**: `VillagerManager.casillaDePieCercaDe(level, mesa)` en el caminar de `VillagerTavernGoal`.

**MEDIDO** (con `python tools/arnes/rendiciones.py --etiquetas`):

| | antes | después |
|---|---|---|
| `Yendo a la taberna` | **16** | **0** |
| rendiciones de la corrida | 24 en 10.800 ticks (**2,73**/1.000) | **3** en 8.400 ticks (**0,47**/1.000) |
| lo que queda | — | 3 casos sueltos: `Yendo al arbol` 1, `Cosechando` 1, `Patrullando el corral` 1 |


Al medir por tasa y desglosar por etiqueta (I135) salió el atasco **más grande que queda**, y es **nuevo**: al dejar de
quedarse encerrada en los bancales (I130), **Filomena llega a la taberna** y ahí se rinde:
**`16x Filomena (Recolector) / Yendo a la taberna`** en una sola corrida (dos tercios de su total, 2,73 por 1.000
ticks). Es el sitio por donde hay que empezar: leer su aviso con `cerebro=`, `nav=` y `pies=` (probablemente otra vez
el patrón de "no puedo entrar/salir de un recinto" o "la celda no se pisa"), y **medir con
`python tools/arnes/rendiciones.py --etiquetas`**: el criterio de éxito es que esas 16 bajen a 0.

### 6d. La mina YA BAJA (medido). Y el choque caracol ↔ galería: **ARREGLADO Y MEDIDO** (27-sep-2026)

> **CERRADO en la sesión del 27-sep.** Lo de abajo queda como el diagnóstico viejo, y **una de sus hipótesis era un
> fantasma del instrumento**: `cara=507,46,613` con `bloqueDeLaCara=cobblestone_slab` **no** era el minero «picando su
> propia losa» —`cara` es siempre la celda del caracol del paso que toca, y ésa lleva su **pieza** por definición (es
> una losa en los pasos pares)—. La causa de verdad, el arreglo y la medida están en **I136** y en
> `tools/arnes/medidas-boca-galeria.txt`; en una línea:
>
> * **la boca de la galería**: el aldeano baja el caracol **de pie sobre la losa** (nodo `y+1`, caja `y+0,5…y+2,45`) y
>   con **dos** celdas de hueco el vecino sale **BLOCKED** (la tercera celda es roca) → **no se puede ni entrar ni
>   salir**. Arreglo: **tres** celdas de hueco, como el caracol. MEDIDO: la galería del paso 32 pasó de **3/24
>   congelado 3.600 ticks** a **abrirse sola hasta 24/24** (y el caracol, que estaba clavado en `pasos=32`, **sigue
>   bajando: 34 → 44**, la cara de `y=46` a `y=40`), y la del paso 16 (**que antes cavaba el arnés a mano**) la
>   cava **el minero: 2/24 → 24/24**.
> * **el agua** (el encargo del jugador): las tres celdas abren el techo justo en el acuífero → el túnel se inundaba
>   (`hechas` 6 → **0** y 223 muestras en cero). Con el censo nuevo (`AGUA`) se midió que el agua entra por **la celda
>   de DELANTE** → `aislarDelAgua` se llama **al abrir cualquier celda** y sella las vecinas con fluido, saltándose
>   **solo** el paso del caracol (un adoquín en un paso impar se leería como su pieza) y la capa del suelo.
> * **el muro**: con la galería en 21/24 el minero se quedaba **243 muestras** yendo al **portón norte** (el final de la
>   galería cae a 65 bloques del centro, «fuera del muro», **a 17 bloques bajo el suelo**). Arreglado: **por debajo de
>   la capa del suelo no hay muro que cruzar**.
>
> **Y EL TALLER/BALSA, HECHO Y MEDIDO EN LA MISMA SESIÓN** (I138, `tools/arnes/medidas-balsa.txt`): era la
> **opción C** que quedaba (pasar la balsa y el acarreo al herrero de herramientas). El bucle estaba medido —el
> minero **33-46 coladas** con el pedernal en su zurrón y el almacén **clavado en 6**— y el arreglo es el traspaso:
> la receta «Colando» es del **herrero de herramientas** y su ciclo la deja en el almacén. MEDIDO: coladas del minero
> **33-46 → 0**, las del herrero **10** (y para: el pedernal llega a su objetivo de **16**), y la galería del paso 32
> se completa en **t≈17.200** en vez de **t≈37.800**.

**El diagnóstico viejo, tal cual se escribió** (léase con lo de arriba en la mano):

**Medido** (del log de la corrida larga, sin gastar otra): de 25 muestras de faena, `Picando` **17 (68 %)**,
`Cargando material` 5 (20 %), `En el taller` **2 (8 %)**, `Bajando lo sacado` 1. Y en toda la corrida el taller hizo
**4 coladas** de 4 adoquines. **El taller no es el coste**: lo que pesa es el **acarreo** y, sobre todo, que hay **17
muestras de `Picando` con la galería clavada en `hechas=3/24`**.

**Y la pista ya estaba en los datos**: durante el estancamiento, `cara=507,46,613` con
**`bloqueDeLaCara=cobblestone_slab`** — el minero estaba «picando» **una losa de su propia mina** (una pieza del
caracol), y eso **no cuenta como avance** (`progresoDeLaGaleria` cuenta **solo aire**). Hipótesis a confirmar por
medida: **la escalera del caracol y el trazado de la galería del paso 32 se pisan**.

**DÓNDE ESTÁ EL CÓDIGO (localizado, para no volver a buscarlo)**:
`VillagerMinerGoal` — el **destino** de cada paso lo decide `celdaDePieDelCaracol(center, nivel, paso)`
(líneas **435-437**: usa `VillageGenerator.esLosaDelCaracol(paso)` para saber si se pisa la losa o la celda de
encima), y la fase **`CAVAR`** (etiqueta `"Picando"`, línea **592**) es la que elige **qué celda se pica**. El marco
de los postes ya usa la guarda `VillageGenerator.esCeldaDePasoDeLaMina` (línea **1116**).

**EL ARREGLO QUE TOCA** (en la fase `CAVAR`, que es el corazón del minero — no se toca a ciegas): si la celda que va a
picar **ya es una pieza protegida de la mina** (una losa del caracol, un poste del marco), **no se pica**: se **avanza
el paso** (o se re-planifica el trazado), en vez de quedarse 17 muestras de `Picando` sobre su propia losa. Hay que
**leer el bloque de `CAVAR` entero** (~líneas 470-600) antes de tocar: ahí están la secuencia de picado, el sello y el
avance del paso.
**Orden de trabajo (el jugador decidió la opción C)**: **hecho** — el choque caracol ↔ galería (I136) y el traspaso de
la **balsa** al herrero de herramientas (I138, con la medida en `tools/arnes/medidas-balsa.txt`). El criterio que se
pedía (`hechas` sube de `3/24`, `pasos` sigue creciendo y no baja lo que produce el taller) está medido: `hechas`
**24/24**, `pasos` **40** y el pedernal del almacén **de 6 a 16**.


**MEDIDO** (corrida larga, `MEDIR_MINERO`): la mina **desciende** —la parte del encargo que faltaba—:

| t | pasos | la cara | profundidad |
|---|---|---|---|
| 40 | 16/240 | 499,54,621 | 8 por debajo del suelo |
| 1.320 | 17 | 499,53,620 | 9 |
| 2.600 | 19 | 499,52,618 | 10 |
| 3.880 | 21 | 499,51,616 | 11 |
| 5.160 | 24 | 499,50,613 | 12 |
| 6.440 | 32 | 507,46,613 | **16** |

Con **`TOPE=NO`** y **0 cierres**: el agua ya no la para (I132) y la escalera del caracol funciona (**16 piezas** de
caracol colocadas). Eso es lo que pediste: *"seguir minando para abajo … y construir escaleras para llegar al fondo"*.

**Y lo que se ve después, que es el siguiente arreglo**: de t=6.440 a t=10.080 (unos **3.600 ticks**, 3 minutos) se
queda en `pasos=32` con la galería en **`hechas=3/24`**, y **no está atascado**: está **en el taller**, midiendo
`50` líneas de "deja lo sacado / guardo" y `cuela 4 adoquines en la balsa y saca un pedernal` repetido. O sea: **la
faena del taller le come el tiempo de la galería**. Toca decidir el orden (la galería manda) o **ponerle un tope a los
viajes al taller** — y medirlo con el mismo instrumento: que `hechas` suba de 3/24 y que `pasos` siga creciendo.
### 7. Atascos sueltos: **ARREGLADOS Y MEDIDOS** (I139, 27-sep-2026)

Eran **dos clases**, las dos medidas con el desglose por etiquetas y las dos con su criterio:

1. **La ronda del guardia no es un atasco** (pero se rendía): el contador medía **solo la distancia en línea recta** y
   la ronda es un **círculo** —los avisos lo decían: `ruta=16-32 nodos … alcanza=SI`, `nav=[… alcanza]`, o sea **con
   camino y andando**—. Arreglo: cuenta como progreso **consumir nodos de la ruta viva** (y **solo dentro de la misma
   ruta**, para no desactivar el contador en el guardia que empuja una pared). MEDIDO: avisos de guardia patrullando
   **9 → 0**.
2. **Al objeto caído se va por una CASILLA DE PIE**: los goals que recogen (`CollectGoal`, `PickupGoal`,
   `AnimalFarmGoal`, `FarmGoal`) caminaban a `objetivo.blockPosition()`, y lo que se cae puede quedar **encima de algo
   que no se pisa** (la **mesa de la taberna** `516,64,639`, una **valla**, **dentro** de un bancal con
   `pies=farmland`): el planificador no da ruta hasta ahí y el aldeano se rendía. Arreglo: `casillaDePieCercaDe`
   (regla I114/I131) **y** `desatascarSiEstaEncajado` antes de contar atasco (el ayudante del leñador, I122).

Lo que queda y **no es de esta clase**: `Filomena (Recolector) / Yendo al almacen` con `ruta=33 nodos … alcanza=NO`
(el almacén inalcanzable desde la taberna/kiosco: la pierna ya documentada como rota en esta aldea) y `Yendo a
entrenar` de los guardias (1 por guardia, con `nav` que no alcanza el puesto de entrenamiento: la clase de I119).

**MEDIDO** (misma partida; datos crudos en `tools/arnes/medidas-atascos-sueltos.txt`). Corrida buena
`build/medida-s7-final.log` (15.040 ticks): **5 avisos de rendición contra 18**, y la **tasa del pueblo en 0,30** por
1.000 ticks (la mejor de la sesión; la referencia era 0,50-0,60 y la tanda anterior del proyecto 1,29):

| criterio | antes | después |
|---|---|---|
| `Patrullando el corral` / `Patrullando la aldea` | **9** avisos | **2** |
| `Recogiendo el corral` (el ganadero) | 4 | **0** |
| `Guardando lo suyo` (los oficios que recogen) | 5 | **0** |
| `Yendo a la taberna` | 1-2 | **0** |
| el granjero hundido en la farmland, en bucle | **7** (el mismo aldeano) | **1** |
| **la tasa del pueblo** (I135, ventana 2.000-12.000) | 0,50-0,60 | **0,30** |

**Lo que NO queda a cero** (y por qué): **2** de guardia patrullando (hay destinos que no se alcanzan de verdad), **1**
del granjero con los pies en la farmland (el bucle desaparece, el hundimiento se repite) y **1** de `Yendo a entrenar`
(clase I119). Y de método: la corrida intermedia **pareció una regresión** (11 avisos) y era el instrumento diciendo
la verdad —el desatasco disparaba (14 desatascos reales) pero **no tenía a dónde sacarlo** porque exigía aire, y
dentro de un bancal no hay aire—.

Lo que queda **ya no es un sitio concreto**: las **11 rendiciones** de las dos corridas de hoy son **todas de 1**, y
**5 son guardias en su ronda** (la clase de I115, el rodeo del círculo de la ronda). Los atascos gordos están a cero
(`Yendo a la taberna` 16 → **0**, `Volviendo a la plaza` 19 → **0**, `Cuidando el ganado` 3 → **0**, `no consigue
entrar al bancal` 5 → **0**).

| etiqueta suelta (1x) | corrida |
|---|---|
| `X (Guardia …) / Patrullando el corral` · `Patrullando la arboleda` | **5 de 11** (las dos) |
| `Valeriano (Granjero) / Buscando recambios` | galería |
| `Hipolito (Granjero) / Yendo a la taberna` · `Vicenta (Ganadero) / Cuidando el ganado` | balsa |

**Y el diagnóstico del patrón que queda, medido en los mismos dos logs** (los avisos traen `ruta`, `nav`, `pies` y
`goals`, que es todo lo que hace falta). Ojo: esto son los **avisos sueltos** de los dos logs completos, así que
aparecen más casos que las 11 del desglose (que cuenta en la ventana `t=2.000-12.000` y agrupa por aldeano + etiqueta):

| quién / etiqueta | aviso |
|---|---|
| `Ubaldo (Guardia arquero) / Patrullando el corral` | `ruta=30 nodos … alcanza=SI` · `nav=[30 nodos … alcanza]` · `goals=[VillagerGateGoal VillagerGuardGoal]` |
| `Ubaldo / Patrullando el corral` (otra vez) | `ruta=32 nodos … alcanza=SI` |
| `Eufemia (Guardia espadachín) / Patrullando el corral` | `ruta=29 nodos … alcanza=SI` |
| `Onofre (Guardia espadachín) / Patrullando la arboleda` | `ruta=20 nodos … alcanza=SI` |
| `Dorotea (Guardia arquero) / Patrullando la arboleda` | `ruta=16 nodos … alcanza=SI` |
| `Hipolito (Granjero) / Recogiendo lo que se cayó` | `ruta=3 nodos … alcanza=NO; pies=farmland` (un bancal) |
| `Valeriano (Granjero) / Buscando recambios` | `ruta=11 nodos … alcanza=NO; cerebro=488,64,659` (el cerebro va **un bloque por encima**: la clase de I114/I131, `casillaDePieCercaDe`) |
| `Vicenta (Ganadero) / Recogiendo el corral` | `ruta=2 nodos … alcanza=NO` |

O sea: **el guardia NO se rinde por falta de ruta** —la tiene, y de 16 a 32 nodos— sino por el **contador de «no me
acero»** mientras anda el **rodeo** de la ronda (I115: la ronda es un círculo y el camino da vueltas). El arreglo que
toca es **medir el atasco contra el avance por la RUTA VIVA, no contra la distancia en línea recta** (que es lo que
sube en un rodeo), y el criterio de éxito es que esas etiquetas bajen a 0 **sin subir las demás**.

**Y la tasa del pueblo (I135) con la mina ya desbloqueada**: media **0,55** por 1.000 ticks (rango 0,50-0,60) contra la
**1,29** de la tanda anterior, y **sin una sola corrida mala** (desviación 0,94 → 0,07). Con 2 corridas es orientación:
para afirmarlo, 3-4.


## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Y NUNCA lanzar el arnés con un
#    JVM del arnés anterior vivo (ocupa el puerto y la corrida nueva no arranca). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'forgeclientdev|fml.modFolders' } |
    ForEach-Object { "$($_.ProcessId) $($_.CommandLine.Substring(0,80))" }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) LA MINA SIN LEVANTAR SERVIDOR (lo primero que hay que mirar): la columna del pozo, celda a celda
python tools\arnes\columna_mina.py "New World (2)" 503 617 63 simular
python build\slice_mina.py 497 509 611 623 44 67 "New World (2)"   # mapa por capas del solar
python tools\arnes\ruta_atasco.py 515 662 503 617 63 "New World (2)"   # ¿hay ruta de pie a la caseta?

# 3) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
#    *** ANTES DE LANZAR: CERO SERVIDORES VIVOS *** (27-sep-2026: lancé una corrida con la anterior todavía
#    corriendo y los DOS servidores escribieron en el MISMO `latest.log` y el MISMO `run/world`: la corrida
#    entera se tiró, y encima la medida parecía buena. Matar el `gradlew` NO mata el servidor.)
$vivos = (Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' }).Count
if ($vivos -gt 0) { "ABORTAR: hay $vivos servidor(es) vivo(s)" }
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   *** Y COMPROBARLO ANTES DE LANZAR *** (28-sep-2026: preparé una corrida después de borrar el `debug/` y SIN
#   volver a copiarlo → el servidor arrancó sin jugador de pega, el pueblo no se gestionó y el log no crecía:
#   **20 minutos tirados**. Señal de alarma: `latest.log` se queda en unos pocos KB y termina en el arranque.)
if (-not (Test-Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java)) { "ABORTAR: falta el arnes" }
Select-String -Path src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Pattern 'MEDIR_[A-Z_]+ = true'
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force            # SIN `-ErrorAction SilentlyContinue`: si está en uso, ABORTAR
.\gradlew.bat runServer --console=plain            # ~10-15 min de reloj para una corrida útil

# 4) leer la medida  *** OJO: latest.log ROTA POR TAMAÑO (juntar el .gz) ***
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|POZO t=|GALERIA t=|SONDA DEL POZO|\[Gate\]|sella agua|la mina se PARA'

# 5) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
#    Y OJO: matar el gradlew NO mata el servidor. Matar el PROCESO del servidor y comprobar que no queda ninguno.
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item build\classes\java\main\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
# y comprobar que el .jar queda libre:
[System.IO.File]::Open((Resolve-Path 'build\moddev\artifacts\neoforge-21.1.249.jar'),'Open','ReadWrite','None').Close()
```

**Para juntar un log rotado** (el `Name` del `.gz` cambia cada día; se mira el más reciente):

```powershell
$in = (Resolve-Path (Get-ChildItem run\logs\*.log.gz | Sort-Object LastWriteTime | Select-Object -Last 1)).Path
$out = Join-Path (Resolve-Path 'build').Path 'log-completo.log'
$e = [System.IO.File]::OpenRead($in); $s = [System.IO.File]::Create($out)
$gz = New-Object System.IO.Compression.GZipStream($e, [System.IO.Compression.CompressionMode]::Decompress)
$gz.CopyTo($s); $gz.Close(); $s.Close(); $e.Close()
Get-Content run\logs\latest.log | Out-File -Encoding utf8 -Append $out
```

## Reglas de este proyecto que no se negocian

1. **Nada entra sin `compileJava` + `lint_aldea --strict` + medida.** Y si un intento **no** arregla, se **retira** y se
   dice (hay cuatro así: dos en I119/I122, el de los portones en I126 y el "contar los sellos como hechos" de I127).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).

