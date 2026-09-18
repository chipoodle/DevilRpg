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
> (`build/portones_todos_nw1.py`: el `FACING` de cada puerta de valla contra la valla que la rodea). **I26** (el
> hueco de subida al desván se mide con el `maxUpStep` del juego) tampoco: la comprueba `build/taberna_subida.py`
> contra el guardado, escalón por escalón. **I30** (la escalera de la barraca: por dónde se entra, por dónde se sale
> y en qué celda va el hueco del forjado) tampoco: la comprueba `build/barraca_subida.py`, que además simula el
> reparador de la migración 59 celda a celda. **I27** (el agua no se rellena) tampoco: se comprueba contra el guardado
> con `build/lago_pesquera.py` y `build/lago_repara.py`. **I28** (la campana, en la celda central del kiosco) tampoco:
> se comprueba con `build/kiosco_dump.py`, que vuelca la huella del kiosco capa a capa. **I31** (ningún puesto de
> trabajo de aldeano que no sea de un oficio del pueblo) tampoco: es un barrido de bloques, y se comprueba contra el
> guardado con `build/barraca_mesa.py` y `build/barraca_mesa_repara.py`.

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

> **Tercera vez, en el porche de la taberna y en la barraca (migración 55).** Dos faroles que se colocaban con la
> celda o el **estado** equivocados, medidos los dos en el guardado del jugador:
>
> - **El porche de la taberna.** El jugador: *"el pórtico está cortado con un espacio, ¿por qué? debería estar
>   completo"*. Aldea 2, cota 120, taberna en `1438,1428`: los **dos faroles de las puntas del alero**
>   (`(1435,123,1432)` y `(1435,123,1438)`, `lantern[hanging=true]` **con aire encima**) estaban colocados **en la
>   celda del escalón del toldo** y lo **sustituían** —y como el **plano guarda el último bloque de cada celda**, el
>   obrero reponía el farol, no el escalón—: el alero quedaba **cortado** en sus dos últimas celdas y los faroles
>   **colgando del aire**, las dos cosas a la vez.
> - **El dormitorio de la barraca.** `lantern[hanging=false]` **con aire debajo** y el tejado de tablones justo
>   encima (`(1369,126,1434)` y `(1369,126,1437)` en la aldea 2, y los mismos dos en la 0): faroles **posados**
>   flotando, sin cadena, a un bloque del techo. La celda era la **buena** (la de debajo del tejado); lo que estaba
>   mal era el **estado**.
>
> **Reglas que se añaden:** en una fila de geometría fija (el alero del toldo, como el hueco de la escalera en I16)
> **no se coloca nada más**: si algo tiene que colgar, va en la celda de **debajo** y de un bloque **sólido** (el
> **soffito** de tablones del porche). Y un farol **colgado** pasa siempre por `colgar`, con el apoyo garantizado
> (`Block.canSupportCenter`, que es la prueba del propio juego). **Y la autocomprobación se aprieta** (migración 55):
> `auditarFarolesFlotantes` mira **el lado que dice el `hanging` del propio farol** (colgado → bloque encima; posado →
> bloque debajo) en vez de los dos lados a la vez, que era lo que dejaba pasar estos dos casos: un `hanging=true`
> sobre un poste y un `hanging=false` bajo el tejado tenían "algo" al otro lado. **Ojo**: la auditoría de Python
> (`tools/audita_aldea.py`) sigue aceptando la **valla de debajo** (la regla del farol *posado*) sin leer el
> `hanging`, así que esos dos casos solo los canta el juego (o `build/faroles_hanging.py`).

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

### I26 · El hueco de una escalera se mide con el `maxUpStep` del juego: TRES celdas, no dos
El jugador: *"las escaleras para el 3er piso están bloqueadas por 2 bloques, dejando solo un espacio de un bloque
libre; se tienen que romper esos 2 bloques para que se pueda pasar"*. El hueco que abre el desván
(`abrirElHuecoDelDesvan`) dejaba **dos** celdas libres encima de cada escalón —el cuerpo y la cabeza, que es lo que
parecía bastar (I16)— y la escalera **no se subía**.

**Por qué.** El juego no sube un escalón "andando": al chocar con la contrahuella **levanta al jugador de golpe**
hasta `Entity.maxUpStep()` (0,6) y comprueba la **caja entera** en esa posición levantada (`Entity.collide`:
`aabb.expandTowards(dx, maxUpStep, dz)` para juntar los choques que se miran, `collectCandidateStepUpHeights` para
las alturas candidatas y `collideWithShapes`, que resuelve **la Y antes que la horizontal** y recorta la subida
contra el techo: `subida = techo - cabeza`). Es decir: el primer bloque **sólido** que tenga encima la **huella**
(la cara alta del escalón) tiene que estar a **1,8 + 0,6 = 2,4** bloques de ella; con bloques enteros, **tres**
celdas libres. Con dos, el techo queda a **2,0** y el que sube se queda **empujado contra la contrahuella**, con la
cabeza pegada al techo (parece que "no se puede pasar" aunque quepa de pie).

**Medido en su guardado** (aldea 2, centro `1414,1414`, cota `120`, taberna en `1438,1428`, `y1=125`, `yTecho=130`,
`build/taberna_subida.py`): los **dos** escalones atascados eran el **2º** (`dx=5`, `dz=11`: huella en `y=127`, con
los tablones del techo de la posada a 2,0 — `(1443,129,1439)`) y el **3º** (`dx=5`, `dz=10`: huella en `y=128`, con
la placa de tejas a 2,0 — `(1443,130,1438)`): **exactamente** los dos bloques que el jugador rompió a mano. Y el
1º (`dx=5`, `dz=12`: huella en `y=126`, techo a **3,0**) **sí se subía**, que es lo que fija el umbral entre 2,0 y
3,0.

**Regla (migración 56):** el hueco de subida abre `DESVAN_HUECO_ALTO` = **3** celdas por encima de **cada** escalón
(las dos capas del forjado: tablones del techo de la posada y placa de tejas), y el reparador
(`arreglarElHuecoDelDesvan`) ensancha el hueco de las tabernas ya construidas **solo en esas celdas** (idempotente:
solo quita `DARK_OAK_PLANKS`/`DEEPSLATE_TILES`, nunca un farol ni nada del jugador). El mismo método lo usan el
constructor y el reparador, así que la geometría no se puede quedar desparejada (I4). **Y hace falta que corra
aunque el jugador ya se hubiera roto los bloques a mano**: el **plano** los tiene sólidos y el **obrero los
repone** (medido: la teja de `(1443,130,1438)` estaba repuesta). Al abrirlas con `colocar` entran en el plano nuevo
(I8) y ya no vuelven. Ojo con el tope que ya avisaba I21: la tercera celda del 3º escalón es la **placa del tejado**
(`yTecho`), así que el suelo del desván queda con **un agujero más** en la vertical de la escalera —los otros dos,
`dx=3` y `dx=4`, ya estaban—: es la boca de la escalera (se cae de `131` a la huella de `128`, tres bloques, sin
daño), no un descuido.

### I27 · El AGUA (y su hielo) no es un hueco que se rellene: un estanque de la aldea se repone
El jugador, mirando la caseta del pescador: *"¿por qué la choza para pesca no tiene su estanque para pescar?"*. El
**lago de la pesquera** (etapa G, base `1434,1458` en la aldea 2, cota `120`) era una **plaza de césped** con la
pasarela y los dos faroles encima.

**Medido en su guardado** (81 columnas del lago y su orilla, `build/lago_pesquera.py`): en la capa que se pisa
(`cota-1`) había **46 de césped**, 32 de arena y los 3 postes de la pasarela —**0 de agua**—; en `cota-2`, **46 de
tierra** y solo **3 de agua**; el fondo de arena de `cota-3` estaba intacto. Las tres celdas de agua que quedaban eran
justo las **columnas de los postes**: la firma del relleno.

**Causa (y es de ORDEN).** La pesquera se construyó en la **migración 46** y las migraciones siguientes vuelven a
llamar a `farm(level, center)` (un argumento) → `prepararTerreno` → `nivelar(..., LEVEL_RADIUS = 64, cota)`, que
**nivela la aldea entera**. `nivelar` **rellena los huecos de debajo de la cota** y daba por "terreno que sobra"
(`esTerrenoRecortable`) **cualquier** cosa con `fluidState`, **agua incluida**: como el lago está a 43-54 del centro
(cae dentro del disco de 64), el nivelado lo tapó —`groundY` de esas columnas devuelve el techo del pozo, así que el
bucle puso **tierra** en `cota-2` y **césped** en `cota-1`, y las columnas de los postes se salvaron porque ahí
`groundY` sí es sólido y el bucle quedaba vacío—. Y **no se reparaba solo** porque `pesqueraConstruida` se conforma con
el **agua O el barril**: con el barril en pie, `asegurarPesquera` salía por el early-return.

**Regla:**
- **El agua y el hielo NO son un hueco que se rellene.** Los pasos que rellenan terreno (`nivelar`, `nivelarHuella`)
  **se saltan** las celdas de agua o hielo (`esAguaOHielo`: `fluidState` no vacío **o** etiqueta `minecraft:ice`, que
  es la misma agua de un bioma frío congelada). Es la misma excepción que ya tenía la **acequia** de la granja.
- **Lo que el generador construye como agua se REPONE.** `repararLagoDeLaPesquera` devuelve el agua (dos capas), la
  orilla y el fondo de arena con la **misma geometría** que el constructor, **solo en las celdas del lago** y **solo**
  donde no haya nada construido (`sePuedeAnegar`: aire, agua/hielo o terreno blando; nunca la pasarela, los postes, el
  barril ni la piedra del pozo). Es **idempotente** y sale en cuanto el lago tiene agua/hielo en **la mitad o más** de
  su capa de arriba (los 3 postes de la pasarela no lo dan por seco). Lo llama el **latido** (dentro de
  `asegurarPesquera`) y la **migración 57**.
- **Un testigo no es una obra entera.** `pesqueraConstruida` (agua **o** barril) decide si hay que **rehacer** la
  pesquera; el **barril** (el puesto del pescador) y el **agua** se reponen aparte, porque con uno de los dos el
  testigo ya da la pesquera por hecha.
- **El lago NO entra en el plano** (`esCeldaDelLago`, en las dos vías: el plano canónico de una aldea nueva y el
  escaneo de una migrada). `necesitaReparacion` repone el agua **en cuanto la ve congelada** —regla que hace falta
  para la **acequia**, que va tapada con una losa y por eso no se congela—, así que con el lago en el plano el obrero
  se pasaría la vida **descongelando** un lago que en un bioma frío **tiene que estar helado**. Quien lo mantiene es
  `repararLagoDeLaPesquera`, que da el lago por bueno con agua **o con hielo**.

**No tiene regla en el lint** (el patrón es un bucle de relleno, no una línea): se comprueba **contra el guardado**
(`build/lago_pesquera.py` y `build/lago_repara.py`, que simulan el reparador celda a celda y dicen qué toca y qué
**no** toca).

### I28 · La celda central del kiosco es DE LA CAMPANA (y el sello no es un bloque)
El jugador: *"sitúa la campana justo en el centro del kiosco y quita el beacon pues nunca se usa"*.

**Medido en su guardado** (aldea 2, centro `1414,1414`, cota `120`, `build/kiosco_dump.py`): la campana estaba en
`(1413,121,1415)` —**una celda al oeste y una al sur** del centro— y la celda central `(1414,121,1414)` estaba en
**aire**; el **farol** colgaba en `(1414,124,1414)` (`hanging=true`, con bloque sólido encima: **I14 ✔**) y el
**beacon** del sello ocupaba la **celda central del TEJADO** (`1414,125,1414`), que es de donde **cuelga** el farol.

- **La campana va en la celda central del kiosco y POSADA.** Es el **POI de reunión** del pueblo (`MEETING`) y en el
  kiosco es **a propósito**: el pueblo se junta ahí. Se coloca con `attachment = floor` porque su apoyo —la plataforma
  de piedra— va **justo debajo**; una campana **colgada** solo vale con un bloque **sólido encima** (I14). **Esa celda
  es suya**: no se pone nada más ahí (el farol va **colgado** del tejado, en la misma vertical y cuatro bloques más
  arriba; y la **mesa de trabajo** que el retiro del ahumador viejo ponía en el centro ya no se pone: el cocinero tiene
  su cocina y su mesa en la taberna).
- **El beacon del sello, fuera** (migración 58). Un beacon **sin pirámide no hace nada** (ni efecto ni luz): era solo
  la señal del **sello místico**, y el sello **no vive en el bloque** sino en los datos de la aldea
  (`VillageSavedData.isSiegeResolved`, que es lo que miran la protección y el haz de partículas de `efectosDeAldeas`,
  que sigue saliendo del kiosco igual). `marcarSelloMistico` ya **no** lo enciende y `fallVillage` ya **no** lo apaga:
  y si el de una partida vieja sigue ahí, se cambia por la **piedra del tejado**, **nunca por aire** (I14: de esa celda
  cuelga el farol).
- **Los reparadores son idempotentes y no tocan lo ajeno**: la campana se retira **solo si sigue siendo una campana** y
  se pone en el centro **solo si la celda está libre** (si el jugador puso algo ahí, no se mueve **nada**: nunca se deja
  al pueblo sin su POI de reunión); el beacon se quita **solo si sigue siendo un beacon**. Y **nunca se rehace el
  kiosco**: su testigo es la **plataforma** (I15). El reparador va **antes** de tirar el plano, para que el plano nuevo
  se capture ya centrado y **sin** el beacon (I8: si el beacon siguiera en el plano, el obrero lo repondría).

**No tiene regla en el lint** (es geometría de una celda, no un patrón de riesgo): se comprueba **contra el guardado**
(`build/kiosco_dump.py`, que vuelca la huella entera del kiosco capa a capa y localiza campana, farol y beacon).

### I29 · El guardián de la guarida es un GRANJERO: su faena solo se pausa por una amenaza REAL (cerca y alcanzable)
*(Aplica a la guarida (`SculkCultivatorEntity`), no a la aldea: se apunta aquí porque es la misma lección que I3/I5
—un contador o un radio mal puesto deja a un bicho **ciclando** y su trabajo no se hace nunca.)*

El jugador, mirando una guarida desde fuera: *"el guardián de la guarida ya no está sacrificando ningún animal"*, con
el corral lleno de animales vivos y restos por el suelo.

**Medido en su código y su guardado**:
- Vanilla: `RangedAttackGoal.canUse()` **no mira la distancia** (le basta con que `getTarget() != null`) y su
  `canContinueToUse()` sigue devolviendo `true` **mientras la navegación no haya terminado**. El goal tiene la
  prioridad 1 y las banderas `MOVE`+`LOOK`, y las tres faenas (prioridad 2-4) declaran **las mismas banderas**, así
  que `GoalSelector` las deja fuera mientras el de ataque corra: **el objetivo le bastaba para congelar la granja**, y
  el `NearestAttackableTargetGoal` lo mantenía hasta los **32** de `FOLLOW_RANGE`.
- El rebaño, además, no podía crecer: las **crías no heredan** `devilrpg_livestock`, así que no contaban como ganado;
  con `SPECIES_KEEP` = 3 y el corral recién generado (**2 vacas, 2 ovejas, 1 cerdo y 2 gallinas** =
  `build/lair_simula_cultivador.py`) **no había nunca una especie con 3 adultos** y no se sacrificaba nada.
- Y `LairGenerator.generate` corre **otra vez** en cada sesión (la guarida se regenera al acercarse): volvía a sembrar
  **7 animales por sesión**, todos con `spawn_type=MOB_SUMMONED` (19 en una guarida y **41** en otra del guardado).

**La regla**: un bicho con un trabajo que depende de una bandera tiene que **medir él mismo** cuándo el objetivo es
real, y **soltarlo** cuando no lo es. Concretamente: radio de amenaza **corto** (12, no los 32 del atributo que se
documenta aparte), **soltar** lo inalcanzable (80 ticks sin acortar distancia) con **memoria** para no reelegirlo cada
10 ticks (600), y **devolver la faena el mismo tick** en que se suelta. Y lo que **crece** tiene que **contarse**: si un
recién nacido no lleva la marca, hay que ponérsela (dentro del corral), o el tope de cría y el mínimo de sacrificio se
quedan en una trampa sin salida.

**No tiene regla en el lint** (es comportamiento de goals, no un patrón de texto): se comprueba **contra el guardado y
el log** (`build/lair_simula_cultivador.py` reproduce el rebaño que ve `livestock()` y dice si habría víctima y si
habría cría, con las reglas viejas y las nuevas; `build/lair_historial_sacrificios.py` saca de los logs **cuándo
sacrificó por última vez cada guarida**).

### I30 · Una escalera se entra por su lado BAJO, y su ÚLTIMO escalón no comparte celda con el hueco del forjado
*(La otra mitad de I26. I26 dice **cuánto hueco** hace falta encima de cada huella; esta dice **dónde va cada pieza**:
el hueco, el pie y el tope.)*

El jugador reportó *"las escaleras para el 3er piso están bloqueadas"* (era la taberna, I26) y, al ir a mirar la
**barraca de la milicia** —el otro edificio con escalera y el que tiene las **8 camas** arriba—, la suya estaba peor.

**Medido en su guardado** (aldea 2, base `1369,1436`, cota `120`: forjado `123`, dormitorio `124`, tejado `127`; con
`build/barraca_dump.py` y `build/barraca_subida.py`, que aplica la regla del `maxUpStep` de I26 **escalón por
escalón**):

| Qué | Lo que había |
|---|---|
| **Escalones** | **TRES** (`1372,120,1439`, `1372,121,1438`, `1372,122,1437`), los tres con `facing=west` |
| **El 4º escalón** | **no existía**: el constructor lo ponía en `(1372,123,1436)` y acto seguido abría el **hueco del forjado** en **la misma celda** (`yPiso2 - 1 = nivel + 3`) → quedaba en aire y la escalera **se acababa a 1,0** del suelo del dormitorio (`124 - 123`): **solo se subía saltando** |
| **El 2º escalón** (`huella y=122`) | una **cama** encima (`1372,124,1438`, los pies del rincón sureste): **2,0** de hueco, y el juego pide **2,4** → **no se subía** (es el mismo umbral que midió I26 entre 2,0 y 3,0) |
| **El arca del este** | `(1372,124,1436)`, justo **en la celda del último escalón**: el que subía se la encontraba de frente, a la altura de los pies |
| **El `FACING`** | **al oeste subiendo al norte** (la cara alta tiene que mirar **hacia donde se SUBE**) |
| **La entrada** | con el pie **pegado al muro sur** y mirando al oeste, la celda por la que hay que entrar al primer escalón caía **dentro de la pared**: a esa escalera **no se podía ni entrar** |
| **El hueco del forjado** | **cuatro** celdas abiertas (`dx=+3`, `dz=+3,+2,+1,+0`), una de ellas la del escalón |

**Las dos reglas que faltaban** (y que I26 no cubría):

1. **A un escalón se entra por su lado BAJO**, que es el **contrario** a la cara alta que marca el `FACING`: el que
   sube tiene que poder ponerse en la celda de al lado (suelo firme y las dos celdas de su cuerpo libres) y subir el
   primer medio bloque (`0,5 ≤ maxUpStep 0,6`). Por eso el **pie** de la escalera va **una celda separado del muro**
   (no pegado a él) y no se entra de lado: el lado bajo de un escalón mide **0,5** y el cuerpo del jugador **0,6**, así
   que entrando por el costado la caja **siempre** toca la parte alta (`1,0`) y no sube.
2. **La celda del ÚLTIMO escalón no puede ser la del hueco del forjado.** El hueco va **en la capa del forjado**
   (`yPiso2 - 1`), encima de los escalones que pasan **por debajo** de él; el último escalón **vive** en esa capa, así
   que su celda es suya. Y como el hueco son **tres celdas enteras** por escalón (I26), el hueco son
   `ESCALONES - 1` celdas —no cuatro— y el constructor y el reparador usan **la misma lista de celdas** para la
   escalera y para su hueco (I4/I16: no se pueden quedar desparejados).

**Regla (migración 59):** la escalera de la barraca son **`BARRACA_ESCALONES` = 4** escalones de medio bloque (uno por
bloque que sube el piso, `BARRACA_PISO2`) en la columna del muro este, subiendo **al norte** (`FACING` = norte) con el
pie **dos** celdas al norte del muro sur, y con la **cara alta del último** a la altura del **suelo del dormitorio**
(`124`), así que del último escalón **se sale andando**. El **hueco** se abre en las `ESCALONES - 1` celdas del
forjado encima de los escalones que van por debajo. La **cama** que estaba encima del hueco se **recoloca** una celda
al oeste (es un **POI**: el pueblo no puede perder ninguna, vanilla pide una **cama libre** por cría, y siguen siendo
**8**), y el **arca** del este pasa al lado de la del oeste **con todo lo de dentro** (reemplazar un cofre tira su
contenido, I6). El reparador es **idempotente**, **no rehace la barraca** (rehacerla tiraría las camas y las arcas) y
va **antes** de tirar el plano, para que el plano nuevo traiga la escalera buena (I8).

**Comprobado** con `build/barraca_subida.py` sobre su guardado, **aldea por aldea** (0, 1 y 2 tienen barraca): antes
—la entrada no vale, el 2º escalón no se sube y no se sale andando— y después —se entra andando (`0,5`), **los cuatro**
escalones se suben (6, 5, 4 y **3** celdas libres sobre su huella) y se sale andando (desnivel **0,00**)—, con las
**8** camas en pie. La aldea **1 está caída**: su barraca tiene el mismo fallo medido, pero la migración **no corre**
en una aldea caída (como ninguna otra).

**No tiene regla en el lint** (es geometría de celdas, no un patrón de texto): se comprueba contra el guardado con
`build/barraca_subida.py` (`python build\barraca_subida.py todas`), que simula la entrada, cada escalón, la salida y
el paso del reparador **celda a celda**.

> **La misma lección, con las dianas de la sala de armas (migración 61).** La **primera diana** se colocaba en la celda
> del **maniquí suroeste**, que es también la del **arca** de la sala de armas, y el arca se coloca **después**: se la
> comía (el constructor creía poner **tres** y en el mundo —y en el **plano**— solo había **dos**). Ahora la diana
> suelta va **pegada a las dos paredes del rincón suroeste** (`BARRACA_DIANA`) y el reparador la devuelve ahí **solo si
> la celda está vacía**; la celda vieja **no se toca** (es la del arca). Es el mismo patrón de I14 (tercera vez) e I28
> —**una celda es de UNA pieza**—, con la particularidad de que aquí la pieza comida era **mobiliario**, no estructura.
> Se comprueba con `build/barraca_diana.py`, que **transcribe el constructor** celda a celda y en orden y **canta
> cualquier celda escrita dos veces** con bloques distintos (con la celda vieja salía **1** —diana→arca— y con la nueva
> **0**, dejando fuera las cuatro que sí son a propósito: la puerta sobre el muro, el hogar en el suelo, el último
> escalón en la capa del forjado y los postes de las esquinas).

### I31 · Ningún puesto de trabajo de aldeano que no sea de un oficio del pueblo

Un **puesto de trabajo** de vanilla (`barrel`, `cauldron`, `smoker`, `blast_furnace`, `grindstone`, `loom`,
`smithing_table`, `lectern`, `composter`, `stonecutter`, `brewing_stand`, `cartography_table`, `fletching_table`) es
un **POI**: el aldeano **sin oficio** que lo encuentre lo **reclama** y se vuelve de ese oficio (una cría que crece,
por ejemplo). Y el pueblo **reparte SUS oficios** (`VillageManager.slotDeProfesionFaltante`): un oficio de fuera no
es un extra, es un aldeano que **deja de hacer lo suyo** —y el pueblo se queda sin el suyo—, y encima el puesto se
queda **cogido sin dueño** para siempre (I23: vanilla solo suelta el ticket al morir el aldeano).

**Regla:** en la aldea solo hay puestos de trabajo de los oficios **del pueblo**, cada uno en el sitio que le toca:

- **compostero** en cada bancal (FARMER), **muela** y **mesa de herrería** en la herrería (WEAPONSMITH/TOOLSMITH),
  **telar** en el corral anexo (SHEPHERD, el ganadero), **ahumador** en la cocina de la taberna (BUTCHER, el
  cocinero), **barril** en la pesquera (FISHERMAN), **soporte de pociones** en la iglesia (CLERIC, viene en la
  plantilla `plains_temple_4`) y la **campana** del kiosco, que es el POI de **reunión** y va ahí a propósito (I28).
- **Nada más**: ni mesa de cartografía, ni atril, ni cortapiedras, ni alto horno, ni caldero, ni mesa de flechas. El
  mobiliario que se les parezca se hace con bloques que **no** son POI: las **pipas** de la taberna son `OAK_WOOD`
  **a propósito** (un `BARREL` sería el puesto del pescador) y el **horno normal** del desván no es puesto de nadie
  (el del cocinero es el **ahumador**).

**Medido** (aldea 2, centro `1414,1414`, cota `120`; `build/barraca_mesa.py`, que barre los bloques de las **tres**
aldeas del guardado): el único puesto que **no** era de un oficio del pueblo era la **mesa de cartografía de la
barraca** (`1371,120,1438`), y además caía en la celda de la **paca** del maniquí de entrenamiento sureste (el
constructor coloca el maniquí **antes** y la mesa se lo comía: el maniquí se quedaba **sin base**, con la calabaza y
las dos vallas en pie). La quita la **migración 60** (`quitarLaMesaDeLaBarraca`): idempotente, de **una celda** y sin
rehacer la barraca (su testigo es el hogar, I15) —si esa celda sigue siendo la mesa, pasa a ser la paca; lo que haya
puesto el jugador no se toca—.

**No tiene regla en el lint** (es un barrido de bloques, no un patrón de texto): se comprueba **contra el guardado**
con `build/barraca_mesa.py` (los puestos de la aldea con sus coordenadas, aldea por aldea) y
`build/barraca_mesa_repara.py` (el reparador de la migración 60, celda a celda y con la idempotencia).

### I32 · Un puesto de RONDA es una celda LIBRE y del MISMO lado de la reja

Un destino de ronda tiene que ser una celda donde el aldeano **quepa de pie** (nada sólido en la celda ni a la altura
de la cabeza, y suelo firme debajo) y que esté **del lado de la valla en el que está él**: si el puesto cae **dentro
de un cercado con portón**, el guardia no llega (el portón solo se abre cuando el aldeano **va a cruzarlo** y no con
un animal en el hueco, así que la navegación ni lo intenta) y se queda **empujando la valla**. Es el mismo fallo que
el pueblo ya tenía documentado para el almacén y el ahumador (*"la navegación no puede llegar a un bloque sólido y el
aldeano se queda dando vueltas alrededor"*, ver `VillageStorage.puntoDeApoyo`), pero con un **cercado** de por medio.

**Medido** (arnés, aldea 2, centro `1414,1414`, cota `120`, de día, guardia espadachín del puesto 0): el puesto del
corral era **dentro** del cercado (`puntoDeApoyoAnexo`, a **3,0** del portón) y el guardia se quedaba en
`1454,3,120,1412,3` —pegado a la valla oeste, a **4,0** del puesto— con `mejor = 4,08` que **no bajaba**, en **dos
rondas seguidas de 200 ticks**. Y cuando el portón se abría de casualidad y entraba, se plantaba **en el hueco del
portón** y la red de seguridad se lo cerraba **encima** (en el guardado del jugador la otra espadachín estaba en
`1455.62,120,1414.67`, que es el bloque del portón, con el portón `open:false`).

**Regla:** los puestos de la guardia alrededor del **corral anexo** van **fuera** de la valla (`base − ANEXO_RADIO −
2`), repartidos **a los lados del portón** (`PUNTOS_DEL_CORRAL`, la fila del portón **no se pisa**: es la única
puerta del rebaño) y **corridos a la primera celda libre** si el jugador ha puesto algo ahí (`puestoLibre`, que
**nunca** corre hacia el cercado). Y el puesto de la ronda general que caiga dentro del anexo se corre al **pasillo
de dentro del muro** (`FENCE_RADIUS − 2` = centro + 60, las dos celdas entre la valla ESTE del corral y la muralla);
`FENCE_RADIUS − 3` era **la propia valla** del corral (centro + 59).

### I33 · Rendirse en un puesto es SALTARLO (extiende I3)

I3 dice que un goal se rinde cuando **no se acerca**, no cuando pasa el tiempo. Falta la otra mitad: **qué hace al
rendirse**. Si el destino se recalcula **determinista** (una ronda, un punto fijo), volver a empezar con el **mismo**
destino es un **bucle infinito**: el aldeano empuja el mismo obstáculo cada 10 s para siempre y **nunca** avanza (el
jugador lo ve "dando vueltas sobre sí misma de manera errática" y con la etiqueta del puesto **clavada**).

**Medido** (arnés, aldea 2, de día): `STOP destino=(1458,120,1412) paso=3 stuck=200` → `START destino=(1458,120,1412)
paso=3` → otra vez la valla → otra vez `stuck=200`… en bucle; y las **dos** espadachines del guardado aparecían a la
vez en el **mismo paso** (el del corral) con la etiqueta "Patrullando el corral", o sea ninguna había avanzado.

**Regla:** un goal de ronda que se rinde **salta el puesto** (`paso++` y `return false`), y lo dice en el log
(`"no llego a ... me salto el puesto y sigo la ronda"`). El siguiente paso puede volver a intentarlo: la aldea
cambia sola (el jugador tala, construye, rompe...). **No tiene regla en el lint**: `stuckTicks < LIMIT` en
`canContinueToUse` aparece en **nueve** goals y en los de faena el destino **se vuelve a elegir** en cada arranque
(no hay bucle); una regla de texto daría nueve falsos positivos.

### I34 · Con un aldeano DENTRO del hueco de un portón NO se cierra (ni por el plazo)

La red de seguridad de los portones del anexo cierra un portón abierto más de 5 s **aunque haya alguien delante**
(esa regla existe para el aldeano que **trabaja al lado** del portón y no lo cruza nunca). Con un aldeano **dentro
del hueco** (`HUECO` = 1,5, no `ABRIR` = 2,6), cerrarlo lo deja **atrapado en el bloque** del portón: empuja y gira
sobre sí mismo y no puede salir hasta que alguien lo abra.

**Medido** (guardado del jugador, aldea 2): la guardia espadachín del puesto 1 estaba en `1455.62,120,1414.67` —el
bloque del portón del corral— con el portón **`open:false`**.

**Regla:** si hay un aldeano a `HUECO` del portón, no se cierra **por plazo**; el plazo sigue valiendo para el que
solo está **al lado** (`ABRIR`).

### I35 · El cupo de puestos de la MILICIA se CUENTA de los sitios del pueblo (no se escribe a mano)

El reparto de la milicia (`VillageManager.repartirGuardia`) cubre **un cupo por oficio** y lo que sobra es milicia. Ese
cupo era una **lista escrita a mano** en el gestor y se quedó con **SIETE** puestos (los de la etapa E): cuando
llegaron el **segundo granjero** (etapa F) y el **pescador** (etapa G) nadie la subió, así que para el reparto esos
dos oficios eran **siempre** "gente de sobra" y **la milicia se los llevaba** — y el daño no es cosmético: el
**compostero** del segundo granjero seguía **cogido** (`free_tickets=0`) mientras su dueño patrullaba, así que **un
bancal se quedaba sin nadie**, y dos pescadores dejaban la pesquera por la ronda.

**Medido** (aldea 2 del guardado del jugador, `build/milicia_cupo.py`, que aplica el reparto tal cual): con el cupo
viejo los sobrantes eran **3** (los dos pescadores y el segundo granjero, `9036d1d0`); con el nuevo, **1** (el
pescador que sobra). Y en vivo con el arnés: `[Village] 9036d1d0 ... deja la guardia y vuelve a su oficio` (el 2º
granjero **sale** de la milicia) y `Aldea 2: comida 64 puntos, 9 aldeanos` → con los 9 puestos cubiertos la milicia
queda **vacía** (es el diseño: hacen falta **crías**).

**Regla:** el cupo se **cuenta** de los sitios del pueblo (`VillageGenerator.puestosPorOficio()`, que cuenta
`VILLAGER_SPECIALTIES`) y **nunca** se escribe una lista paralela en el gestor (es I5 otra vez: la medida vive en un
sitio). Así no puede quedarse atrás cuando se añada un oficio. Y ojo con el detalle de por qué hay que contar
**por plaza** y no "¿tiene ese oficio?": el oficio lo da también **el bloque** (el `barril` es del pescador: quien lo
reclama se vuelve pescador, aunque el pueblo ya tenga el suyo) y **un aldeano curado** vuelve con su `VillagerData`
viejo y **sin** los datos del mod (medido: el segundo pescador de la aldea 2, `295c0896`, con la etiqueta *"Dionisio
(Guardia espadachín)"*, **sin** `DevilRpgGuardia` ni memorias: es el cuerpo curado de un guardia anterior, y en el log
está su `ZombieVillager`). Con plazas contadas, el que sobra es **uno** y el titular conserva su puesto.
*(Desde la etapa H el cupo son **once** plazas y, además, la aldea **poda** los duplicados: ver **I36**.)*

### I36 · Una profesión por estación, y la aldea la ADMINISTRA (poda de duplicados)

De dónde salen los titulares de más (**medido y deducido del código**, no supuesto):

1. **El bloque da el oficio**: el paquete `CORE` de vanilla trae `AcquirePoi` → `AssignProfessionFromJobSite`, y
   `VillagerProfession.NONE` tiene por adquirible **`ALL_ACQUIRABLE_JOBS`**: cualquier aldeano **sin oficio** reclama
   una estación **libre** y se vuelve de ese oficio. La aldea 2 tenía **un compostero libre** (el 3.er bancal) ⇒ el
   primer aldeano sin oficio que pasara por ahí se volvía un **tercer granjero** (y ese sobrante se lo llevaba la
   milicia ⇒ compostero cogido sin dueño trabajándolo: el daño de I35).
2. **`reponerProfesiones` da una plaza cuando cree que falta**: cuenta solo los aldeanos **cargados** dentro de
   `FALLEN_CHECK_RADIUS` (`muro + 44` = 106). Si el titular está en un chunk descargado o fuera del radio, el pueblo
   le da su plaza a otro y quedan **dos**. (Es la consecuencia 11 de la lista de abajo, ahora con salida.)
3. **Un aldeano curado** vuelve con su `VillagerData` (oficio) viejo y **sin** los datos del mod.

**Regla:** el pueblo **administra** sus oficios: en cada latido (`VillageManager.podarOficiosDuplicados`, justo
después de `reponerProfesiones`) se cuentan los titulares **por oficio** y, si hay más que plazas, los que sobran —en
orden **estable** por UUID— pierden el oficio **y su ticket** (`liberarPuesto`, I23: si no, la estación se queda
cogida para siempre) y vuelven al reparto: plaza libre si la hay y, si no, **gente de sobra** (la milicia o un
obrero). Con eso **"una profesión por estación"** deja de depender de quién esté cargado.

**Medido** (arnés, aldea 2, partida del jugador): censo de oficios estable
`{CRIA=1, butcher=1, cleric=1, farmer=3, fisherman=1, fletcher=1, nitwit=1, shepherd=1, toolsmith=1, weaponsmith=1}`
—**una** de cada, **tres** granjeros (sus tres plazas)— durante todo el rato que duró la medida.

### I37 · La aldea CRECE por encima de sus puestos (y las crías nacen SIN oficio)

La milicia se llena **con los hijos del pueblo**, que es como lo quiere el jugador: *"la milicia se va a ir llenando
conforme vayan naciendo y alcanzando la adultez aldeanos"*. Para eso hacen falta las dos mitades, y las dos estaban
rotas:

- **El camino de cría era INALCANZABLE**: la rama que hace nacer una cría pedía a la vez `slotDeProfesionFaltante < 0`
  (todas las especialidades vivas ⇒ ≥ tantos adultos como puestos) **y** `vivos < puestosDelPueblo()` ⇒
  **contradicción**: el pueblo no paría nunca por ahí y las crías que había eran las de vanilla (`feedVillagers`
  reparte pan para que críen). Ahora el tope es **`puestos + MILICIA_MAX`** (11 + 7 = 18): el pueblo cubre sus
  puestos y sigue creciendo para llenar la milicia.
- **Y nacía CON oficio**: `spawnOneVillager(..., vivos, true)` usaba el número de aldeanos vivos como **índice de
  plaza**, así que el aldeano número 10 nacía con el oficio de la plaza 10 = un oficio **duplicado** por construcción.
  Ahora la cría nace **SIN OFICIO** (`VillageGenerator.spawnBaby`): al crecer, el reparto le da una plaza si queda
  libre y, si no, es **gente de sobra** (milicia u obrero). Así el pueblo puede tener más aldeanos que puestos sin
  romper I36.

**Medido** (arnés, aldea 2): `[Village] Aldea 2 crece: aldeano 12/18 (comida 56)` con `CRIA=1` en el censo — la cría
nació por el camino del pueblo (no por vanilla) y sin oficio.

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
| `tools/arnes/GuardHarness.java` (**versionada**, ver `tools/arnes/LEEME.md`) | **Arnés de la aldea en un servidor headless**: copia la partida a `run/world`, fuerza los chunks, mete un jugador de pega y deja correr el **latido de verdad** (`VillageManager.manageNearby`), volcando en el log lo que hace la guardia cada segundo. Es lo que midió I32/I33 (la valla del corral) sin jugar. |
| `build/inventario.py` | Inventario de estructuras de una aldea en el guardado (qué edificios hay y dónde). |
| `build/taberna_subida.py` | **¿Se sube la escalera del desván?**: aplica la regla del `maxUpStep` del juego (I26) a cada escalón, contra el guardado, sin jugar; y compara la regla vieja (2 celdas) con la nueva (3). |
| `build/barraca_subida.py` | **¿Se sube la escalera de la barraca?** (I30, la otra mitad de I26): aplica la regla del `maxUpStep` a cada escalón **y** comprueba la **entrada** (el lado bajo), la **salida** (a la altura del suelo del dormitorio) y las **8 camas**, antes y después de simular el reparador de la migración 59 **celda a celda**. `todas` = aldeas 0, 1 y 2 de una pasada. |
| `build/barraca_dump.py` | **La barraca entera, capa a capa**: cuenta escalones (con su Y y su `facing`), camas, mobiliario, el forjado (huecos) y la vertical de cada escalón. |
| `build/barraca_mesa.py` | **Puestos de trabajo de aldeano** (I31): los barre **bloque a bloque** en las tres aldeas del guardado, con sus coordenadas, y dice qué hay en la celda de la mesa de cartografía de la barraca y qué dice el **plano** de ella. |
| `build/barraca_mesa_repara.py` | **El reparador de la migración 60** (la mesa de cartografía → la paca del maniquí), **celda a celda** y con la idempotencia: antes/después de la celda, el maniquí completo y que no quede ningún puesto de trabajo en la barraca. |
| `build/barraca_diana.py` | **Las tres dianas de la barraca y el ORDEN de colocación** (migración 61): **transcribe el constructor** celda a celda y **canta cualquier celda escrita dos veces con bloques distintos** ("lo que va después gana"), además de simular el reparador de la diana (celda nueva libre en las tres aldeas, idempotencia, la celda vieja del arca sin tocar) y contar las dianas del **mundo** y del **plano**. |
| `build/huertadiag.py`, `build/huerta_simula.py` | **Bancales**: qué dice el plano y qué hay en el mundo celda por celda (qué calvas faltan en el plano) y qué celdas repondría el obrero / labraría el granjero (I25). |
| `build/granjaestado.py`, `build/farmdiag.py`, `build/columnas.py`, `build/perfilcol.py` | Estado de la granja (cultivos, edades, cotas) y columnas crudas. |
| `build/items.py`, `build/contenedores.py` | Objetos en el suelo por tipo y contenido de cofres/despensa/almacén. |
| `build/faroles_hanging.py` | **Faroles sin apoyo de verdad** (I14): mira la propiedad `hanging` contra su dirección, que es lo que **no** mira la auditoría de Python (una valla debajo vale para un farol *posado*, no para uno *colgado*; la de Java sí lo mira desde la migración 55). Dice qué reparador arregla cada uno. |
| `build/kiosco_dump.py` | **El kiosco entero, capa a capa** (I28): cuenta los bloques por capa, imprime la huella de `cota-2` a `cota+7` y localiza la **campana**, el **farol** y el **beacon** con sus propiedades (dónde están y en qué celda relativa al centro). |
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
