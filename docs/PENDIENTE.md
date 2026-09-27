# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero y sigue por **«Lo que está pendiente»**. El detalle largo, con los datos
> crudos de cada medida, está en `docs/aldea-invariantes.md` (**I119–I128**) y en `tools/arnes/LEEME.md`.
>
> **Para el jugador**: pégale a la sesión nueva algo como: *«Lee `docs/PENDIENTE.md` y sigue por lo que está
> pendiente; mide con el arnés y no te fíes de nada que no esté medido».*

## Estado (26-sep-2026, sesión de la mina: el pozo, el acuífero y el recolector)

Todo lo de abajo **compila** y pasa `python tools/lint_aldea.py --strict`.

**Hecho y MEDIDO con el arnés en esta sesión:**

| qué | medida |
|---|---|
| **la mina sellada** (I126) | `pasos=16` **congelado 18.200 ticks** y **NO HAY RUTA** al pozo. Los cortes: **césped del nivelado** en `507,62,614`…`507,62,620` y **dos troncos del marco del paso 16** en el paso del 15 |
| **el pozo, abierto** | el reparador salta en el latido (`6 celda(s) … y 2 poste(s)`), la **sonda** pasa de pararse en el paso 5 a `SI` en los pasos 0…32 y **`pasos` sube 16 → 32** con el minero 16 bloques bajo el suelo |
| **el acuífero del paso 32** (I127) | el agua **se daba por "picada"** (0 sellos en 110.000 ticks, 282 líneas de galería repitiendo las celdas 1 y 2) → ahora **se sella**, y **la mina CIERRA**: `sella agua/lava … (1 seguidas)`, `la mina se PARA en 507,46,612: piedra labrada de tope`, **`TOPE=SI`**, y el minero **fuera**, cargando material |
| **el recolector** (I128) | **64 → 0** rendiciones de Filomena (perseguía cosas **encima del tejado** —`y=67`, con el techo en 66— y **fuera del muro** —sin ruta: `ruta=1 nodos … alcanza=NO`, y `ruta_atasco.py` dice NO HAY RUTA—). Rendiciones del pueblo: **1,37 → 0,64 por 1.000 ticks** |
| **los portones** (I126) | medido que el borrado del destino ocurría con la ruta viva **ya alcanzando** en **499 de 868 (57 %)** aperturas, y que **no arreglarlo no cambia nada** → **intento RETIRADO**; queda la línea `[Gate]` que lo midió |

**Dos trampas de instrumento cazadas** (apuntadas en el LEEME): `run/logs/latest.log` **ROTA por tamaño** (hay que
juntar el `.gz` con `latest.log` o los números salen falsos), y el arnés **deja el JVM vivo**: matar el proceso del
`gradlew` **no** mata el servidor, y si sigue vivo **ocupa el puerto** y la corrida siguiente **no arranca** (se ve
como `Exception stopping the server … serverlevel2 is null` y un log que no cuadra). Comprobar SIEMPRE
`Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'fml.modFolders' }` antes de cada corrida.

## Lo que está PENDIENTE (en este orden, como pidió el jugador)

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
### 6. El PICO al romperse: **MEDIDO — el minero hace lo correcto; el que falla es el HERRERO**

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
### 6b. LO QUE PIDIÓ EL JUGADOR PARA LA MINA (hecho lo primero; falta lo demás)

1. **La mina atraviesa el agua** — **HECHO y MEDIDO** (I132): ya no se cierra cuando hay agua; **aísla** con paredes
   (cáscara 3×3×3 de adoquín: sella también el agua de delante, así el túnel avanza por celdas secas), **seca** la
   celda (queda de aire, transitable) y **sigue bajando**. Medido: `1:air 2:air 3:air`, `hechas=3/24` (antes clavado
   en `0/24`) y **`TOPE=NO`** (antes `TOPE=SI`, la mina se cerraba).
2. **Que el pico lo haga el HERRERO** — **HECHO y MEDIDO (el pico, sí; las pepitas, falta la medida)**:
   el herrero de herramientas **forja picos** (medido: `Forjo un pico de madera` x3 y el minero los repone tras **5
   roturas**). Y para que el eslabón del **hierro** no se quede en nada se han hecho dos cambios:
   **(a)** el **recolector** recoge las **pepitas de hierro** del suelo (`esDelPueblo` no las tenía: se quedaban
   tiradas); **(b)** el **pico de hierro se puede forjar con 27 pepitas** cuando no hay 3 lingotes (vanilla: 9 pepitas
   = 1 lingote). Antes exigía 3 `IRON_INGOT` y con `0 lingote(s)` **no había pico nunca**.
   **Lo que falta medir**: las **pepitas** no salen en el modo `MEDIR_MINERO` porque **no muere ningún zombi de raid**
   (el arnés barre los bichos). Receta: un modo que **plante un `AggressiveZombieEntity`** y lo mate atribuido a la
   guardia (como hace `MEDIR_MILICIA`) y cuente: (a) las pepitas que suelta, (b) que alguien las levante, (c) que
   lleguen al almacén y (d) que el herrero las gaste en un pico.3. **Hierro de los zombis de los raids** — **pendiente**: el jugador dice que los guardias, al matar zombis que
   vengan de un **raid del mundo**, conseguirán **hierro**; ese hierro es el que tiene que llegar al herrero para los
   picos. Hay que medir: (a) que el zombi de raid **suelte hierro** (o que se le añada al botín), (b) que el
   **recolector/guardia lo levante**, (c) que acabe en el **almacén**, y (d) que el herrero lo use.

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

### 6d. La mina YA BAJA (medido). Y la medida del tiempo del minero **corrige el diagnóstico**

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
**Orden de trabajo (el jugador decidió la opción C)**: primero el choque caracol ↔ galería (es el 68 % de su
tiempo); después **pasar el taller y la balsa al herrero de herramientas** para que el minero **solo mine**. Criterio:
`hechas` sube de `3/24`, `pasos` sigue creciendo y no baja lo que produce el taller.


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
### 7. Atascos sueltos ya apuntados (cuando se pueda)

- El aldeano que se queda **sin ruta** fuera del muro (`560,64,587`, `552,63,585`).
- La **recolectora** aún se rinde 1 vez por corrida (ya no 64): mirar el caso suelto que queda.


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
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force
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

