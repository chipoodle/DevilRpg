# Aldea: invariantes y lista de consecuencias

Este documento existe por una razón concreta: **casi todos los bugs que reportó el jugador en la aldea fueron
una sola clase** (una altura que no era la cota) y, peor, varios salieron de arreglar el síntoma donde apuntaba el
informe en vez de arreglar la **invariante** y barrer el resto del código. Cada regla de aquí abajo está escrita
después de un bug **medido en juego**, con la evidencia, para que no vuelva.

En cada cambio en `world/Village*` o en los goals de aldeano hay que:

1. repasar la **lista de consecuencias** (§2), y
2. pasar el lint (`python tools/lint_aldea.py --strict`), que vigila las invariantes de §1.

---

## 1. Invariantes

> **Ojo con los números**: el lint (`tools/lint_aldea.py`) numera sus reglas **por su cuenta** y no coinciden con las
> de esta sección. La correspondencia, para que un aviso `I6 VillagerX.java:12` no se busque en la regla equivocada:
>
> | Lint | Qué vigila | Invariante |
> |---|---|---|
> | I1 | la Y del centro → `cotaDeLaPlaza` | I1 |
> | I2 | distancias horizontales (`dx*dx + dz*dz`) | I2 |
> | I3 | atascado = no acercarse (`stuckTicks++` sin progreso) | I3 |
> | I4 | tamaño de parcela a mano (`9`, `5`) | I4 |
> | I5 | medidas del kiosco a mano | I4 |
> | I6 | `getNavigation().moveTo` | I5 |
> | I7 | `parcelasDe(center)` sin la cota | I1 |
> | I8 | `setBlock`/`destroyBlock`/`colocar` en el latido | I6 |
> | I9 | construcción nueva sin subir `CURRENT_LAYOUT` | I7 |
> | I10 | nivelar la huella de una **parcela** sin preguntar antes | I11 |
> | I11 | contar bichos "dentro de la aldea" sin la **altura** | I12 |
> | I12 | farol **colgado del aire** (Y del farol en vez del apoyo) | I14 |
>
> I8, I9 y I10 de esta sección (abajo) **no tienen regla en el lint**: se vigilan a mano. **I22** (los portones del
> anexo y el rebaño que vuelve) tampoco: nació después del lint y se comprueba con la **auditoría del guardado**
> (`build/portones_todos_nw1.py`: el `FACING` de cada puerta de valla contra la valla que la rodea).

### I1 · Toda altura se mide con `cotaDeLaPlaza`, nunca con la Y del centro
El centro de una aldea viaja por todo el sistema (`ObjectiveTargets.targetOf`, `centroDe`) y **su Y no es la del
pueblo**: llega la del *spawn del jugador* (101 en la partida, con las aldeas a 62-75) o directamente **0** (en el
centro que se saca del plano). Con esa Y:
- el granjero buscaba los cultivos **30 bloques por encima del suelo** y no veía ninguno: daba vueltas y volvía a la
  despensa sin cosechar ("hay betabel y zanahoria y no los cosecha");
- el recolector no recogía nada (155 objetos tirados en una aldea);
- la comprobación "¿es muelle de la herrería?" buscaba en y=101 y no encontraba nada, así que **la herrería se
  reconstruía cada 10 s** (54 reconstrucciones seguidas en el log) y cada una **tiraba el botín de su cofre** al
  suelo (`Containers.dropContentsOnDestroy`): espadas, picos, armaduras, sillas de montar, diamantes.

**Regla:** la Y de cualquier cosa de la aldea (cultivos, cofres, composteros, puestos de trabajo, huecos, medidas de
bloques) sale de `VillageGenerator.cotaDeLaPlaza(level, center)`. Si hace falta un centro "con Y buena", se pide a
`VillageManager.centroDe` (que ya devuelve la **cota**) o se usa `parcelasDe(level, center)`.

### I2 · Las distancias "dentro de la aldea" son HORIZONTALES
La aldea es un **recinto en el plano XZ**. Midiendo en 3D, un aldeano a 30 bloques por encima del suelo cuenta como
"fuera de su pueblo" (el recolector se quedaba bloqueado en la puerta del goal) y un zombie dos bloques por encima
del suelo contaba como "no ha entrado" en el asedio, salvando la aldea de rebote.
**Regla:** `dx*dx + dz*dz` para "¿está dentro del radio?".
**Ojo (I12):** para un **bicho** eso no basta: además tiene que estar **a la altura del pueblo**. La horizontal sola
metía en el pueblo a los monstruos de las **cuevas** de debajo (ver I12).

### I3 · Atascado = NO ACERCARSE (un contador de paciencia no cuenta los ticks de camino)
Los goals llevan `stuckTicks` y `canContinueToUse` se corta a `STUCK_LIMIT`. Contando **cada tick** mientras el
aldeano camina, el goal se rendía a los 120 ticks (6 s) aunque fuera avanzando: un viaje a la despensa (20-30
bloques) no lo terminaba **nunca** y el granjero se quedaba ciclado con la cosecha encima ("no sube al kiosco a
poner la cosecha").
**Regla:** se guarda la distancia más corta del viaje (`mejorDistancia`) y el contador solo sube cuando **no** se
acerca.

### I4 · Los tamaños y medidas compartidas son constantes, no números a mano
`PLOT_WIDTH`/`PLOT_DEPTH` los usan el generador **y** los goals (los goals tenían `dx < 9`, `dz < 5` escritos a
mano, así que al agrandar la parcela a 9x9 el granjero seguía mirando solo un trozo). El radio del kiosco se pide
con `kioscoRadio()`.
**Regla:** un número que usan dos sitios vive en un solo sitio y se expone.

### I5 · El aldeano camina POR EL CEREBRO
Los aldeanos son mobs de cerebro: en cada tick el cerebro escribe su propio destino (su puesto, la plaza, la cama,
pasear) y **pisa** la navegación manual. Con `getNavigation().moveTo(...)` el aldeano se iba a otro lado a mitad de
camino ("primero da vueltas y se va a otro lado antes de recogerlos").
**Regla:** `VillageManager.caminarHacia(...)` al ir y `VillageManager.parar(...)` al llegar.

### I6 · Lo que corre en el latido tiene que ser IDEMPOTENTE y no destruir contenedores
El latido de la aldea corre cada 10 s para siempre. Cualquier "asegurar" que no detecte que la obra ya está hecha
se convierte en una máquina de reconstruir; y reconstruir un cofre **tira su contenido** al suelo (mecánica de
vanilla) y vacía la despensa de la aldea.
**Regla:** comprobar con un bloque testigo buscado **a la cota** (no por la Y del centro) y no tocar lo que ya está.

### I7 · Cambiar lo que se construye obliga a subir la migración
Las aldeas ya construidas no se rehacen solas: la migración solo corre si `layout < CURRENT_LAYOUT` (o
`casasVersion < CURRENT_HOUSES`). Un arreglo solo de código **no llega** a las aldeas existentes: hay que subir la
versión, y si cambian las casas, también `CURRENT_HOUSES`.
**Regla:** si cambia el trazado, la parcela, un edificio o el aspecto del mundo ya guardado → subir la versión (el
lint avisa con I9 si se toca `VillageGenerator` y no se sube en el mismo diff).

### I8 · Lo que construye el generador tiene que entrar en el PLANO
El obrero repone lo que dice el plano. Lo que se construye **después** de capturarlo (o con `level.setBlock` en vez
de `colocar`) no está en el plano y no se repone nunca; y lo que es "terreno natural" tampoco entra. Esto ya dio
dos bugs: el muro de troncos (no se reponía) y los minerales (flotando **y** en el plano).

### I9 · Los marcadores de tiempo «0 = nunca» se resuelven YA la primera vez
Las marcas de `VillageSavedData` que significan "nunca ha pasado esto" valen **0**, pero el reloj del mundo
(`gameTime`) también empieza en 0: compararlas directamente (`gameTime - marca >= ESPERA`) hace que la **primera
vez** se trate como si acabara de pasar. Medido en el guardado del jugador: el **rebaño del corral anexo** se soltaba
solo si `gameTime - AnexoAnimales >= 3 días`, y con el mundo recién creado (reloj en 24200) la granja anexa se
construía y **no soltaba ni un animal**.
**Regla:** la marca se lee aparte (`boolean nunca = marca == 0L`) y el caso "nunca" se resuelve **ya**; la espera es
solo para las **repeticiones**. Igual que se hace con `AnexoAnimales` y con `StarvingSince`.

### I10 · El tope de población es para CRECER, no para cubrir un PUESTO FIJO
`puestosDelPueblo()` limita cuánta gente **nace** en la aldea, no cuántos puestos se cubren. Si el reparto de
"repón el oficio que falta" se mete dentro del tope, una aldea que ya está **llena** (y le sobra alguien: un
guardia, un repetido) **nunca** recibe el puesto que se le añade por migración. Medido en el guardado del jugador:
aldea con 7 aldeanos y tope 7 (etapa E), **sin carnicero**, la cocina construida, el ahumador sin dueño y la carne
cruda de la despensa sin cocinar (2 puntos en vez de 4).
**Regla:** el puesto vacío se repone **aunque el pueblo esté en el tope** (el que llega de más engrosa la milicia);
el tope solo se aplica a la cría (`vivos < puestosDelPueblo()`).

### I11 · Una parcela ya hecha NO se toca (y lo que crece dentro, menos)
Este fallo volvió **dos veces** y las dos con el mismo síntoma, que es lo que lo hace peligroso: *"la granja
todavía spawnnea con vegetales como items sobre ellos"*.

- **Capa 1 (`plot()` replantaba todo).** Cada vez que corría (generar, migrar) replantaba las 144 celdas: los
  cultivos maduros se sustituían por brotes y lo que había se quedaba **tirado por la parcela**. Medido en el
  guardado del jugador: 144 cultivos y **solo 2 maduros** justo después de una migración. Arreglo: si la celda ya
  tiene un cultivo, se deja.
- **Capa 2 (el NIVELADO se los llevaba antes).** El arreglo anterior **no bastaba**: `plot()` empieza nivelando la
  huella con `nivelarHuella`, que **recorta** el terreno que sobresale de la cota y, en una parcela en **cuesta**
  (una aldea de montaña), ese recorte se llevaba por delante los cultivos de las celdas altas **antes** de llegar a
  la comprobación de "ya hay cultivo". Medido en su aldea de montaña: las tres parcelas con sus 71 cultivos pero
  **casi todos de edad 0-1** y semillas de trigo y de remolacha por el suelo. Arreglo: `bancalHecho()` (si hay
  tierra de cultivo, la parcela está hecha: solo se aseguran el compostero y la valla) **y**, como segunda capa,
  `hayCultivos()` (aunque falte tierra, si hay plantas dentro **no se nivela nada**).
- **Capa 3 (el mismo recorte, en otra parcela).** El nivelado de la huella de **cualquier** construcción con un
  margen de terraza (`MARGEN_TERRAZA`) puede solaparse con una parcela vecina: por eso la regla del lint **I10**
  vigila *cualquier* `nivelarHuella(...PLOT_...)` y exige el guardia por delante.

**Regla:** el nivelado de la huella de una parcela **solo** se hace cuando la parcela **no existe todavía**; si
tiene tierra labrada o cultivos, no se toca (la tierra de cultivo está en el plano y la repone el obrero).
El lint (**I10**) lo comprueba: un `nivelarHuella` sobre `PLOT_*` sin `bancalHecho()`/`hayCultivos()` delante
**falla la puerta de commit**.

### I12 · "Dentro de la aldea", para un BICHO, es recinto **+ altura**
Nació de una **aldea caída en juego**. Un bicho "dentro del pueblo" se contaba **solo** con la distancia horizontal
(radio de la valla), y en una aldea de montaña eso mete en el pueblo a **todo lo que vive en las cuevas de debajo**.
Medido en el guardado del jugador justo después de la caída (aldea 1, cota 95, centro `(990,990)`): **24** monstruos
contaban como "dentro" y **18** estaban en cuevas o repisas (`y=5` … `y=89`); con la banda de altura quedan **6**,
todos a la altura del pueblo. Dos consecuencias, las dos vistas:

- **El latido del pueblo se congelaba.** `hayEnemigosDentro` es la puerta de cultivar, comer, reparar y **repoblar**:
  con un esqueleto en una cueva a 60 bloques por debajo de la plaza, la aldea entera dejaba de trabajar (y no se
  repoblaba) sin que hubiera **nadie** dentro.
- **La aldea caía sin que el jugador pudiera hacer nada.** Al agotarse el tiempo del asedio, si **todos** los
  atacantes vivos estaban dentro, la aldea caía y quedaba en **ruinas para siempre**. Un asediador que se metía en
  una cueva bajo la plaza contaba como invasor: no se le ve, y habría que cavar a ciegas en 62 bloques de radio. La
  regla escrita del asedio dice justo lo contrario (*"si no llegan a los muros, no asedian y no pueden ganar"*).

**Regla:** todo recuento de bichos "dentro de la aldea" (latido, perímetro del asedio, partículas de intrusión) pasa
por `VillageManager.dentroDelRecinto`: disco en **XZ** (I2) **y** una banda de altura sobre `cotaDeLaPlaza`
(`RECINTO_DY_ABAJO` = 6 por debajo, `RECINTO_DY_ARRIBA` = 16 por encima: cubre la zanja y el segundo piso/tejado sin
meter las cuevas). El lint (**I11**) vigila que no se vuelva a contar un `Monster`/`MobCategory.MONSTER` sin el
ayudante cerca.

### I13 · Un asedio NUNCA se pierde a ciegas (ni se gana en silencio)
El jugador vio caer su aldea **peleando dentro de ella** (medido con los logs: **32 de 32 posiciones suyas dentro de
las murallas**, entre 4,5 y 47,8 bloques de la plaza, con la valla a **62**): mató decenas de bichos y el chat solo le
dijo *"La aldea cayó…"*, sin decir **por qué** ni cuántos atacantes quedaban dentro. Con la ola de 10 asediadores
mezclada entre los bichos de la noche y sin ninguna marca, no había forma de saber a quién tenía que matar.
**Regla:** mientras hay asedio, el jugador recibe (barra de acción, cada `SIEGE_STATUS_INTERVAL` = 15 s y en la cuenta
atrás de `SIEGE_WARN_SECONDS` = 30 y 10 s) **cuántos atacantes quedan, cuántos están DENTRO del muro y cuánto tiempo
queda**; cada baja se canta al momento; los asediadores van **marcados con brillo** (`setGlowingTag`, se les quita al
resolverse el asedio y al cargarse sin asedio) y el mensaje de la caída dice el **motivo** ("los monstruos aguantaron
dentro de los muros").

### I14 · Nada queda **colgado del aire** (un farol va SOBRE su apoyo)
El jugador lo vio dos veces y en dos sitios distintos: *"en la cabaña del pescador hay faroles flotando"* y *"y lo
mismo en la granja"*. La auditoría de la aldea entera (aldea 2, cota 120) encontró **16 faroles sin apoyo**:
**14** en la cerca de la **granja anexa** (1 bloque por encima del poste) y **2** en la **pesquera** (3 bloques por
encima de la orilla del lago). Causa: el ayudante `farolEnElPoste` colocaba el farol en la casilla que le dieran y
**daba por hecho** que debajo había un poste; los llamantes le pasaban la casilla del **farol** contando un poste que
no existía (`nivel + 2`).
**Regla:** el ayudante (**`farolSobreElPoste`**) recibe la casilla del **APOYO** (el poste, o el suelo) y **garantiza
el poste** si falta; el farol va encima. Lo que cuelga (`colgar`) necesita un bloque sólido **encima**. Guardias:
regla **I12 del lint** (una Y con sumando en `farolSobreElPoste` falla la puerta de commit), la
**autocomprobación** `VillageGenerator.auditarFarolesFlotantes` (se ejecuta al generar y al migrar y **grita en el
log** cualquier farol sin apoyo, con su posición) y el **retrofit** `posarFarolesFlotantes` (baja el farol que quedó
flotando en una aldea ya construida; el plano se recaptura después, así que el obrero repone la posición buena).

> La misma auditoría (`tools/audita_aldea.py`, **versionada**; antes estaba en `build/`, fuera de git) comprueba
> además: faroles y **vallas** flotando, **cofres tapados** (un bloque encima: no se pueden abrir), **puertas
> incompletas** (sin su mitad) y **camas sueltas** (sin cabecera). Saca las aldeas **del propio guardado**
> (`data/devilrpg_villages.dat`: índice, centro y cota), así que las audita todas de una pasada. Medido tras el
> arreglo: **aldea 2: 0 en las cinco listas** (la migración 47 corrió al jugarla) y **aldea 0: 14 faroles**
> pendientes hasta que el jugador pase por ella (el retrofit los baja al migrar).

### I15 · La despensa vive donde se cocina y se come (y el kiosco no sabe de ella)
El jugador: *"el cofre de la comida ya no tiene sentido que esté en el kiosco central... sería mejor moverlo a la
taberna, tomar un cuarto y convertirlo en almacén de comida"*. Desde la **migración 47** la despensa es el **cofre
doble de la cocina de la taberna** (`VillagePantry` deriva su sitio de `TABERNA_DESPENSA`), y el kiosco se queda con
su campana y su farol.
**Regla:** el testigo del kiosco (`asegurarKiosco`) es **su plataforma**, nunca la despensa: con el testigo viejo
—que exigía el cofre— el kiosco se reconstruía **en cada latido** buscando un cofre que ya no está en él, y
reconstruirlo tira lo de dentro. El cofre viejo se retira en la migración **después** de pasar lo suyo a la despensa
nueva y, lo que no quepa, al almacén (`retirarDespensaDelKiosco`, idempotente).

### I16 · El hueco del forjado llega hasta la meseta (y la barra no tapa la entrada)
La escalera de la taberna sube en L: un tramo bajo el forjado, una **meseta** y un tramo que sale al piso de arriba.
**Regla:** el **hueco del forjado** (`esHuecoDeLaEscalera`) cubre el tramo que se sube **y la meseta** (`dz` del tope
a `tope + 3`): quien sube cruza el borde del forjado con la cabeza ya por encima de su altura, así que un hueco corto
—tres filas— le hacía **golpearse con el borde** (lo reportó el jugador: *"los 2 bloques que están justo debajo de los
pies míos estorban a todo el que quiere subir, su cabeza topa con ellos"*, y esos 2 bloques eran el propio forjado
sobre el que estaba de pie). Y la **barra** del comedor no puede empezar antes de `dx=7`: su extremo 2×2 quedaba justo
en el carril de entrada del pie de la escalera (`dx=5..6`), *"4 bloques que están estorbando, 2 de madera pelada y 2
de madera normal"*. Las dos cosas las repara la **migración 48** (`arreglarEscaleraDeLaTaberna`) **solo en esas
celdas** —nunca rehaciendo la taberna, que borraría la despensa y las camas— y solo quita el bloque si es del tipo
esperado (`quitarSiEs`).

### I17 · Las listas por oficio se leen EN VIVO (el pueblo cambia oficios)
El pueblo **reparte oficios**: repone el puesto que se queda vacío y una cría crece y hereda. **Regla:** ningún goal
puede **cachear** la profesión del aldeano en su constructor. `VillagerPickupGoal` sí lo hacía, así que a un aldeano
al que le cambiaban el oficio le quedaba la **lista vieja** y seguía recogiendo lo del oficio anterior (un **herrero**
nuevo recogiendo trigo e **ignorando el hierro**: el bug que reportó el jugador como *"los herreros no recogen
materiales del suelo"*). La lista, el destino y el nombre del oficio se leen **en vivo** de
`getVillagerData().getProfession()`. Recordatorio del alcance: el herrero solo barre **lo que se encuentra a ≤20
bloques** de él; lo que está lejos lo barre el **recolector**.

---

### I18 · La cal de los muros no puede ser de un material "de terreno"
`esTerrenoNatural` incluye `BlockTags.TERRACOTTA` (hace falta: las aldeas de meseta recortan terracota de verdad),
así que **un muro no puede usar terracota**: el recorte del nivelado se la come. **Regla:** la cal de los muros Tudor
es **`SMOOTH_QUARTZ`** (blanco de cal y sin etiqueta de terreno) y **ningún recorte sube por dentro de una
construcción**: `nivelar` (y el talud) cortan hasta el **primer bloque que no sea terreno** y paran, porque `groundY`
de una columna con una construcción devuelve **su altura** (el tejado) — sin esa parada el recorte subía por dentro de
la casa y borraba los paneles (el jugador lo vio como *"quedan incompletas las paredes"*: los cuatro muros de la
taberna se quedaron con postes, solera, tablones y cristales, y **toda la cal era aire**). Lo repara la **migración
49** (`rehacerMurosDeLaTaberna`), que vuelve a pasar solo los constructores de estructura.

### I19 · Las ventanas de la taberna son de CRISTAL ENTERO (no paneles)
Un `glass_pane` se dibuja según sus cuatro conexiones, y **no conecta con los troncos** (medido en el guardado del
jugador: los cristales pegados a los postes tenían esa conexión en `false`). Por eso el cristal pegado al poste se veía
**cortado** (media ventana) y uno solo entre dos postes quedaba como una **franja fina**: el jugador lo reportó como
*"en un espacio de dos, un cristal está completo pero el que le sigue no; y cuando el espacio es de uno, el cristal
sólo parece una franja delgada"*. Se probó primero a dejar **un panel en la celda central** con cal a los dos lados, y
seguía viéndose mal, así que el jugador lo zanjó: *"mejor pon ventanas de cristal completo, de las de cubo"*.
**Regla (migración 53):** las ventanas son **`Blocks.GLASS`** (cristal entero), **dos de ancho** por hueco (`muroTudor`:
`i % 4 == 1 || i % 4 == 2` en la fila de la ventana) y **tres seguidas en los frontones** (`yTecho+2`,
`|dz - cumbrera| <= 1`), que es la ventana del desván. Un bloque de cristal **no tiene conexiones**: siempre se ve
entero.

### I20 · El desván se vacía DESPUÉS de reparar el tejado
El **desván** (el hueco bajo el tejado) es un tercer piso: su suelo es la **placa del tejado** (`yTecho`) y se anda en
`yTecho+1` —`techoDeLaPosada` pone los tablones en `yTecho-1`, **no** en `yTecho`—. **Regla:** el vaciado del relleno
(`desvanDeLaTaberna`) tiene que correr **después** de cualquier cosa que vuelva a pasar el tejado
(`rehacerMurosDeLaTaberna` llama a `tejadoDeLaTaberna`), o lo rellenará otra vez; y solo se quitan las tejas del
relleno (`DEEPSLATE_TILES`), nunca las escaleras de las vertientes, la cumbrera ni las columnas de los frontones.

### I21 · La escalera del desván NO ocupa el corredor de la posada
El corredor del segundo piso son los **dos carriles** de la galería (`dz=7` y `dz=8`): en el muro **norte** (`dz=6`)
están las puertas de los tres cuartos del norte y en el **sur** (`dz=9`) las de los tres del sur. La escalera del
desván de la migración 51 subía en recto por el carril norte y **tapaba el paso a los cuartos** —el jugador lo reportó:
*"al poner la escalera al tercer piso tapaste el corredor que permite que se entre a los diferentes cuartos del 2do
piso; mejor sacrifica un cuarto del 2do piso para poner ahí una escalera y libera el corredor"*—.
**Regla:** la escalera del desván sube **dentro del cuarto suroeste** (`dx 1..5`, `dz 10..13`), en **L** (tramo de abajo
al norte por `dx=5`, tramo de arriba al oeste por `dz=10`, con la **cara alta mirando hacia donde se SUBE**) y **sin
pisar ni una celda de la galería**; el **hueco del techo** va encima de ella (I16) y **no** donde estaba antes; y el
cuarto que se sacrifica **entrega su cama al desván** (en vanilla cada cría necesita una **cama libre**: el recuento no
puede bajar), nunca se pierde. La geometría vive en las constantes `DESVAN_ESCALERA_*` y en **una sola lista de celdas**
(`celdasDeLaEscaleraDelDesvan`) que usan la escalera y su hueco, para que no se puedan quedar desparejados.
**Cuidado con el tope:** no puede ir en la fila del alero (`dz=13`, un solo bloque libre: el que sale se golpea con el
tejado) ni meterse en la **caja de la escalera** (`dx=3`, `dz 8..11`) ni en el **pozo** (`dx 1..2`, `dz 8..11`): la L
pasa **por encima** del muro del pozo y deja el pozo **tapado** (nadie se cae al comedor).

### I22 · Un portón del anexo NO se queda abierto (y el rebaño vuelve ANDANDO)
El jugador: *"cuando el ganadero entra al corral, deja la puerta abierta y los animales se salen; dale la capacidad
para meterlos de vuelta"*. Medido en su guardado (`New World (1)`, aldea 2, corral a `(1464,1414)`): el **portón del
corral estaba abierto** (`open:true`, sin redstone) y el pueblo tenía **3 animales marcados fuera de la valla** (la
vaca a **12,1** bloques de la base del corral, la oveja a **13,5** —las dos **dentro** de la muralla— y un puerco a
**24,0**). Cuatro causas, las cuatro arregladas:
- **El portón se abría por estar cerca, no por cruzar.** El cierre era "a más de 3 bloques", pero el puesto del
  ganadero (`.puntoDeApoyoAnexo`) está a **3,0** clavados del portón: la condición `distancia > 3.0` no se cumplía
  **nunca** y el portón se quedaba abierto toda la faena.
- **"Ya está en casa" era un radio de 26.** El corral tiene 9: el animal que se salía y se quedaba pastando al lado de
  la valla contaba como dentro y **no volvía**.
- **Un portón abierto sobrevive al guardado.** La lista de "portones que abrió el pueblo" vive en memoria, así que tras
  cargar la partida **nadie** lo cerraba (y el portón guardado estaba abierto, justo el caso real).
- **Y el PLANO lo guardaba abierto.** El plano guarda el `BlockState` entero: el de la aldea 2 tenía el portón del
  corral con `open:true` (capturado mientras el fallo lo dejaba así), así que cada vez que un asedio se llevaba el
  portón el **obrero lo reconstruía abierto**.

**Regla:** (a) el portón **solo se abre si el aldeano va a cruzar** (su destino del cerebro, `WALK_TARGET`, está al
otro lado del plano) y **se cierra en cuanto el lado cambia** —se apunta de qué lado estaba al abrirlo—, con un tope
de **5 s** por si no lo cruza; el eje de cruce es el del `FACING` de la puerta (la valla va perpendicular: comprobado
en su guardado, el del corral mira al oeste y se cruza en X, el del gallinero al sur y se cruza en Z, y los 12 de los
bancales también). (b) El **latido** cierra cualquier portón del anexo que lleve abierto **más de 5 s sin ningún
jugador a 7 bloques** (mira solo esas dos casillas: es idempotente y barato) — eso es lo que tapa el agujero del
guardado y el caso de que no quede ningún aldeano con el portón a la vista. (c) El rebaño **vuelve andando**
(`VuelveAlCorralGoal`, prioridad 4: por debajo de huir/criar/comida y por encima de pasear): va al portón **por
fuera** con la puerta cerrada, se le abre al llegar y se le cierra detrás; **solo** si se atasca se le mete a mano.
(d) "Dentro del corral" es el **rectángulo de la valla** (`enElCorral`), no un radio, y la búsqueda cubre el **recinto
entero** más el radio de reconocimiento del corral. (e) El **plano** guarda y el obrero repone una puerta de valla
**siempre cerrada** (`VillageGenerator.estadoDelPlano`, aplicado al capturar **y** al leer el plano, así que las aldeas
ya guardadas se arreglan **sin migración**: el estado abierto/cerrado de un portón es transitorio, no "lo que la aldea
debe ser"). Los animales **del jugador** (sin la marca `DevilRpgDelCorral`) y los que van **montados o atados** no se
tocan.

### I23 · Un puesto de trabajo se reclama ENTERO: memoria **y** ticket
Un punto de interés (POI) de vanilla da **un ticket** por puesto: el aldeano que trabaja ahí tiene la memoria
`JOB_SITE` **y** el ticket cogido (`free_tickets` baja a 0). **Regla:** borrar la memoria **no** libera el ticket —
`Brain.eraseMemory(JOB_SITE)` a secas deja el puesto **cogido sin dueño para siempre**, y nadie puede volver a
reclamarlo (vanilla solo lo suelta al morir el aldeano, `Villager.releaseAllPois`): hay que **soltar el POI**
(`PoiManager.release`, como hace `VillageManager.liberarPuesto`) **antes** de borrar la memoria.
Y un goal de oficio **no puede dar por hecho** que su puesto está reclamado: si encuentra su estación y no la tiene en
el cerebro, la reclama (`VillagerSmithGoal.reclamarElPuesto`), liberando antes el ticket si está cogido y **ningún**
aldeano lo tiene.
**Medido** en el guardado del jugador (`New World (1)`, aldea 2, centro `1414,1414`): el **muelle de afilar**
(`1419,120,1368`) y la **mesa de herrería** (`1418,120,1368`) tenían `free_tickets=0` y **ningún** aldeano con ese
sitio en la memoria —el único herrero de armas lo tenía solo como `POTENTIAL_JOB_SITE` y el de herramientas, ni eso—.
Sin `JOB_SITE` vanilla **no registra la actividad WORK** (`addActivityWithConditions(WORK, …, JOB_SITE presente)`), el
cerebro se cae a **IDLE** todo el día (guardado con `DayTime=8137`, franja de trabajo) y el aldeano se pasa el rato en
las conductas de IDLE —paseo aleatorio y "andar hacia donde mira"— con la etiqueta genérica **"Paseando"**: es lo que
el jugador describió como *"da vueltas sobre su eje como un tonto"*. El mismo día se vio la otra mitad: los **dos
herreros** llevaban la marca de **obrero** y su goal de reparar (prioridad **3**) **bloqueaba** el de su oficio (**4**,
misma bandera `MOVE`), así que **un aldeano con faena fija lleva la reparación por DEBAJO de su oficio** (prioridad 5,
como el granjero).

### I24 · Un árbol se tala ENTERO: el tronco no siempre es una columna recta
El hachazo que sube en vertical (`p = p.above()`) deja **el árbol a medias**: la **acacia** sube recta y **tuerce en
diagonal** (comprobado bloque a bloque en la arboleda del jugador: el árbol de `1370,120..124,1391` continúa en
`1369,125,1391`, una casilla al lado y una arriba), y las ramas de un roble grande salen de lado. Lo que queda
colgando, además, **pierde las hojas** al desramar, así que ya **no se reconoce como árbol** (`baseDeArbol` exige
tierra debajo, `esArbolSuelto` hojas cerca) y se queda **flotando para siempre** (medido: **5 troncos huérfanos** en la
arboleda del jugador a `y=125..126`).
**Regla:** tras la columna, el leñador **remata** lo que cuelga con una búsqueda corta —troncos **pegados o en
diagonal hacia arriba**, dentro de un radio pequeño y con un **tope** de troncos— y cada tronco tiene que pasar
`VillageGenerator.esTroncoDeArbol` (**de pie**, eje Y, y **sin nada construido pegado**): eso deja fuera el **MURO**
(troncos tumbados, eje X/Z) y los postes de las casas. Y un tronco que **no llega al suelo** por otros troncos
(`tieneApoyo`) es un **resto** del hachazo viejo: se limpia, pero **solo en la arboleda del pueblo** (fuera, un poste
del pueblo sin hojas es indistinguible de un resto).
**Y el plano no apunta los troncos de la arboleda:** esa arboleda es la madera del leñador (se tala y se replanta a
propósito), así que el obrero no los "repara" (ni al capturar el plano ni al leerlo, sin migración: el del jugador
tenía **9 troncos de acacia** apuntados como huecos, y reponerlos era levantar troncos sin copa).

### I25 · La tierra de cultivo del bancal se vuelve a LABRAR (y su humedad no es daño)
Vanilla convierte la **tierra de cultivo en tierra** en cuanto alguien salta encima (`FarmBlock.fallOn`) y la tierra,
pegada al césped, vuelve a ser **césped**: en la aldea conviven aldeanos, animales y el jugador, así que los bancales
se quedan con **calvas**. El jugador las vio y las reportó con captura: *"de esta parcela veo que hay dos espacios que
no tienen cultivo y nadie los está reparando para hacerlos cultivables"*.
**Medido en su guardado** (aldea 2, centro `1414,1414`, cota `120`): los tres bancales tienen **216** celdas de cultivo
y al **plano** le faltaban exactamente **2**, `(1384,1430)` y `(1384,1434)` —las dos de la columna oeste del bancal de
`(1384,1428)`—, que en el mundo eran **`grass_block`** a `y=119`. Dos reglas, entonces:

- **La huerta entra SIEMPRE en el plano.** Sus celdas son **geometría fija** (las parcelas de `FARM_PLOTS`, a `cota-1`,
  con la acequia en `PLOT_WATER_ROW`): al capturar, `VillageGenerator.estadoDeLaHuerta` pide **tierra de cultivo** (o
  **agua**) aunque el mundo las tenga pisoteadas, vaciadas o con el agua congelada. Sin esto, el plano de una aldea
  **migrada** —que es un **escaneo** del mundo— se queda **sin** las celdas que ya estaban pisoteadas al capturarlo
  (el `seDescartaDelPlano` las da por "terreno natural") y el obrero **no tiene nada que reponer** ahí.
- **El granjero vuelve a labrar.** Su cadena empezaba en "tierra de cultivo vacía" (`buscarTierraVacia` solo mira
  celdas que **ya** son `farmland`), así que sembraba en lo labrado y **nunca** labraba una calva. Ahora tiene la tarea
  `LABRAR` **antes de sembrar**: celdas de bancal que son tierra o césped, **con agua cerca** y con el hueco de arriba
  **libre** (el aire encima es lo que garantiza que **no arranca ningún cultivo**, I11). La migración **54** las labra
  en las aldeas ya construidas con `labrarCalvasDelBancal` (idempotente y con las mismas condiciones).

**Y la humedad (`moisture` 0..7) NO es "daño".** La sube y la baja el propio juego (agua al lado, sequía, lluvia), así
que es estado **transitorio**, igual que el `open` de un portón (I22): el plano la guarda **sin humedad**
(`estadoDelPlano`), al **reponer** la tierra el obrero la pone **regada** como la pondría el juego
(`tierraDeCultivo`, la misma cuenta que `FarmBlock.isNearWater`) y `necesitaReparacion` dice explícitamente que
**`farmland` contra `farmland` no se repara**, pase lo que pase con la humedad. Sin esa frase, cualquier retoque de la
lista de "tierra pisoteada" convertiría las **214** celdas de cultivo de la huerta en una cola de reparación eterna.
**No tiene regla en el lint**: se comprueba con la auditoría del guardado (`build/huerta_simula.py`).

## 2. Lista de consecuencias (obligatoria en cada cambio)
Antes de escribir el commit, para CADA valor, bloque, contador o comportamiento que toco:

1. **¿Quién más LEE lo que cambio?** Buscar todos los usos (`grep`) y revisarlos uno a uno. *(Fallo real: cambié el
   sentido de `stuckTicks` y no miré los tres `canContinueToUse` que lo leen → granjero, recolector y obrero
   ciclados.)*
2. **¿Depende de una ALTURA?** ¿Es la cota (I1)? ¿Y si el centro/base trae otra Y?
3. **¿Cambia el estado del mundo ya guardado?** → migración (I7) y cómo se repara una aldea que ya existe.
4. **¿Corre en el latido o en un goal repetido?** → ¿es idempotente? (I6) ¿puede destruir un contenedor? ¿puede
   dejar objetos tirados?
5. **¿Rompe el plano o la reparación?** (I8)
6. **¿Cliente o servidor?** ¿Hay que renderizar o sincronizar algo? ¿Es código solo-cliente? (la etiqueta de dos
   líneas necesitó un `RenderNameTagEvent` en el cliente porque `Font.drawInBatch` no parte líneas).
7. **¿Rendimiento?** ¿Escaneos por tick? ¿Cuántas columnas/bloques por pasada? ¿Se puede cachear?
8. **Casos raros** (mirar los que ya nos han mordido): aldea **sobre agua** (islita), bioma **frío** (agua que se
   congela), **montaña** (recorte y minerales), **cueva/barranca** debajo (agujeros), aldea **vieja** (layout
   antiguo), aldea **caída** (ruinas), **chunk descargado**, **jugador ausente**, aldeas a **200 bloques** entre sí.
9. **¿Cómo lo COMPRUEBO?** Guardado (bloques y entidades reales), log del juego, o lint. Si no puedo comprobarlo,
   **decirlo claramente** en vez de dar por hecho que funciona.
10. **¿Afecta a lo que el jugador YA tiene?** Aldeas existentes, inventarios, cofres, granjas sembradas.
11. **¿Alguien CUENTA aldeanos?** (salud de la aldea, qué oficios quedan vivos, raciones repartidas). El radio de
    conteo tiene que **cubrir hasta donde llegan los goals**, no el muro: el leñador trabaja hasta `muro + 40` (76) y
    con el radio viejo (`muro + 28` = 64) un leñador talando a 70 bloques **no contaba** y la aldea le reponía un
    **duplicado** creyendo que se le había muerto el recolector. Es el mismo error que I1, pero en horizontal.

---

## 3. Herramientas

| Herramienta | Para qué |
|---|---|
| `python tools/lint_aldea.py --strict` | Vigila I1-I12 en el código. Puerta antes de commitear. |
| `build/inventario.py` | Inventario de estructuras de una aldea en el guardado (qué edificios hay y dónde). |
| `build/huertadiag.py`, `build/huerta_simula.py` | **Bancales**: qué dice el plano y qué hay en el mundo celda por celda (qué calvas faltan en el plano) y qué celdas repondría el obrero / labraría el granjero (I25). |
| `build/granjaestado.py`, `build/farmdiag.py`, `build/columnas.py`, `build/perfilcol.py` | Estado de la granja (cultivos, edades, cotas) y columnas crudas. |
| `build/items.py`, `build/contenedores.py` | Objetos en el suelo por tipo y contenido de cofres/despensa/almacén. |
| `build/aldeanos.py`, `build/aldeanos.py` | Aldeanos: profesión, inventario, posición (carpeta `entities/`). |
| `build/herreria.py`, `build/huecos_ore.py`, `build/solares*.py` | Herrería, huecos y minerales flotantes, solares libres. |
| `build/plantillas*.py`, `build/paleta.py` | Plantillas del juego: tamaños, puertas y qué bloques traen. |
| `tools/audita_aldea.py` (**versionada**) | **Auditoría de las aldeas enteras**: faroles y vallas flotando, cofres tapados, puertas incompletas y camas sueltas (lee las PROPIEDADES de los bloques). Saca las aldeas del guardado (índice, centro y cota): `--aldea N`, `--caidas`, `--resumen`, `--centro X Z --cota N`. |

Los scripts de `build/` no se versionan (está en `.gitignore`): son de lectura del guardado del jugador. Las
herramientas que sí merecen sobrevivir están **versionadas en `tools/`** (ver `tools/README.md`): `lint_aldea.py`,
`audita_aldea.py`, `nbtdump.py`, `finduuid.py` y `recover/NbtTool.java`. `build/nbtdump.py` es un **puente** al de
`tools/`, para que los ~100 scripts sueltos de `build/` sigan funcionando con **una sola copia** del lector.

Los goals de las etapas B-E (`VillagerAnimalFarmGoal`, `VillagerCookGoal`, `VillagerGuardGoal`,
`VillagerLumberjackGoal`, `VillagerSmithGoal`) están **dentro** del lint desde la etapa E: antes solo se vigilaban
los tres goals viejos, y son justo los que más caminan por el pueblo (I3, I6). Una excepción **justificada** se marca
en la propia línea o en las dos anteriores con `// lint:ok <clave> porque ...`: así se calla un aviso legítimo sin
apagar la regla para el resto del archivo.
