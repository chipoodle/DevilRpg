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

> **Antes de tocar nada de la aldea, lee `docs/aldea-estados.md`**: ahí está la **máquina de estados** de una aldea
> (generada → avisada → descubierta → asediada → sellada / caída), **quién** cambia cada estado, **en qué línea del
> código** vive y **qué línea deja en el log**. Existe por el caso de la aldea "abandonada" (I86): costó una tarde
> reconstruir por qué había caído, cuando tenía que haber sido un minuto de búsqueda en el log.

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
> **Y LOS SITIOS TAMBIÉN** (el jugador lo preguntó al revisar el código: *"la constante `TRAZADO` no tiene la choza
> del pescador, ¿por qué?"*). `TRAZADO` es **la tabla de sitios que lee `trazado(center, i)`**, no un censo de la
> aldea: la **pesquera** (etapa G) se hizo con su **propia copia** de las coordenadas (`PESQUERA = {20, 44}`) y
> nunca entró en la tabla —y las **tres parcelas** estaban en las **dos** (`TRAZADO` *y* `FARM_PLOTS`), con las filas
> de la tabla sin que las leyera nadie—. Ahora el sitio de la pesquera vive **solo** en `TRAZADO[7]` (y `PESQUERA`
> lo lee), las filas muertas de las parcelas se quitaron, y el comentario de la tabla dice **dónde vive cada sitio
> que no está en ella** (`FARM_PLOTS`, `VillageStorage.OFFSET`, `ANEXO_DX`, `baseDeLaTaberna`, `PUNTOS_DE_LA_ARBOLEDA`),
> que es lo que evita que el siguiente lector caiga en la misma trampa. **No cambia ni un bloque del mundo**: los
> valores son los mismos.

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

**Y desde la etapa H vale para TODOS los goals del pueblo** (era el fallo en los nueve: el jugador lo vio después con
Isidoro, un granjero "Guardando lo suyo" y **moviéndose errático**: se rendía a los 140 ticks y volvía a elegir el
mismo objeto). El mecanismo vive en **un solo sitio**, `VillageManager`:

- `marcarPuntoFallido(villager, punto)` — se llama **al rendirse** (en `canContinueToUse`, cuando
  `stuckTicks >= STUCK_LIMIT`) y apunta el sitio en los **datos persistentes** del aldeano (no en el goal, que se
  pierde al descargar el chunk). Deja en el log `"no consigue llegar a <pos>: lo deja por 5 min"`, que es lo que
  permite ver **qué** sitio del pueblo es el inalcanzable.
- `esPuntoFallido(villager, punto)` — lo **salta** la búsqueda del goal durante **5 min**: en los que eligen entre
  varios candidatos (el granjero, el ganadero, el leñador, el recolector) dentro del bucle, y en los que navegan a un
  único destino calculado (el pescador, el cocinero, el herrero, la taberna) **al elegirlo** (si está aparcado, el
  goal **no arranca** y espera un rato).

Cableado en los **nueve**: recoger, granjero (sus cinco búsquedas), recolector, leñador (árbol, claro y restos),
ganadero (animal, pareja y objetos), pescador, cocinero, herrero (almacén y taller, también al cambiar de fase) y
taberna (su mesa).

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

**Y dos reparadores más en el mismo latido** (los otros dos lados del mismo problema, medidos en su guardado):

- **Tickets PERDIDOS** (`soltarTicketsPerdidos`): una estación con el ticket cogido (`free_tickets = 0`) pero **sin
  ningún aldeano que la tenga en el cerebro**. El juego solo suelta el ticket al morir el aldeano (I23), así que un
  ticket perdido deja la estación **muerta para siempre**. Medido: el **compostero del tercer bancal**
  (`1384,120,1448`) estaba así, y su titular (el tercer granjero) no lo habría podido reclamar nunca. Se sueltan los
  de los oficios a los que les **falta gente** (cupo contra titulares cargados), consultando los puestos ocupados
  **por el tipo del oficio** (`heldJobSite`): sin listas de coordenadas, vale para cualquier oficio.
- **Cada titular, con su estación** (`reclamarEstacionesDelPueblo`): un aldeano **con oficio pero sin `JOB_SITE`** va
  y reclama el puesto de su oficio (el libre más cercano; si no hay, uno con el ticket perdido, que se suelta y se
  vuelve a coger). Sin `JOB_SITE` vanilla **no le registra la actividad de trabajar** y el aldeano cae a IDLE. Medido:
  el **clérigo** tenía su soporte de pociones **libre** y el **ganadero** su telar con el ticket cogido sin dueño.
  Nunca se le quita el puesto a otro aldeano **cargado** que lo tenga en el cerebro.

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

### I38 · Un contador de atasco NO se comparte entre dos destinos (extiende I3)

I3 dice que el contador de paciencia solo sube cuando el aldeano **no se acerca**, y para eso guarda la distancia
más corta del viaje (`mejorDistancia`). Lo que faltaba: **de qué viaje**. Si un mismo goal navega a **dos destinos
distintos** (dos "piernas") y las dos usan el mismo `mejorDistancia`, la segunda pierna se mide contra la distancia
que se alcanzó en la **primera** — y si la primera terminó cerca (a 2-3 bloques) y la segunda empieza lejos (a 90),
**cada paso de la segunda cuenta como no acercarse**: en 6 s (`STUCK_LIMIT`) el goal se rinde y aparca el destino
**estando aún a mitad de camino** (I33 convierte eso en 5 min tirados).

**Medido** (arnés, aldea 2, con el clérigo): el clérigo llena las botellas en la orilla (`lleno 3 botella(s) de
agua`, a ~3 bloques del agua y a **~90** del soporte) y **6 s después aparca su propio soporte** con
`no consigue llegar a BlockPos{x=1397, y=121, z=1371}: lo deja por 5 min`, estando todavía en `1410,121,1415`
(a **57 bloques**): la poción no llegaba a hacerse nunca. Con `mejorDistanciaAgua` (contador propio de la orilla) y
la vuelta al soporte **medida de cero** al terminar de llenar, la misma partida hace la cadena entera en 96 s
(`va a llenar ... orilla` → `lleno 3 botella(s) de agua` → `guardo una pocion en el almacen: Potion of Poison`) y
**no** aparca nada. Las líneas literales de las dos medidas están en `tools/arnes/medidas-clerigo-agua.txt`.

**Regla:** cada pierna (cada destino distinto) tiene **su** `mejorDistancia` y **su** contador, y al **cambiar de
pierna** (llegar a una, aparcarla o darse la vuelta) los dos se ponen a cero. Vale para cualquier goal con ida y
vuelta (el clérigo: puesto ↔ agua) y para los que cambian de fase con destinos que no se parecen (el herrero ya lo
hacía al cambiar de fase).

### I39 · Un añadido (porche, alero, cobertizo) LLEGA al muro que lo cobija

El error de esta familia es **un bloque de más hacia fuera**: se construye el añadido creyendo que su fila de dentro
es "la del muro" cuando la del muro es **otra** celda, y queda una **columna de aire** entre los dos. No se ve como un
agujero: se ve como un techito **suelto**, apoyado en sus postes y **sin tocar** la casa.

**Medido** (guardado del jugador, aldea 2, taberna en `1438,1428`, cota `120`; lo reportó él: *"el techito que está en
la entrada de la taberna está incompleto porque no conecta con la pared"*): la **pared** está en `x = bx` y el porche
salía hasta `bx-2` y `bx-3`, así que **`bx-1` estaba de aire en todas sus alturas: 7 de 7 celdas**. Con la fila que
faltaba —escalón en `bx-1` a la altura de la de dentro y su tablón de soffito debajo, de punta a punta— el arnés
headless midió `celdas del toldo PEGADAS a la pared: **7/7**` (escalón + soffito + **pared sólida** al lado), y el
reparador cambió **14 celdas** (migración 63), las mismas que había predicho la simulación sobre el guardado.

**Regla:** cuando algo se apoya en una construcción, la celda **pegada** es `base-1` (no `base-2`) y se **comprueba en
el guardado** que no quede **aire** entre los dos; la prueba barata es que la celda del **muro** que va al lado sea
**sólida** (medida: `dark_oak_planks`). Si el añadido está mal en aldeas ya construidas, el arreglo es **aditivo**
(`colocarSiEstaVacio`) siempre que se pueda: rellenar la fila que falta no puede comerse lo que haya puesto el
jugador, mientras que **mover** el añadido obliga a borrar el viejo.

### I40 · La valla de un bancal NO tiene escalones (ni para andar ni para saltar)

La valla de roble mide **1,5**, y un aldeano **anda** hacia arriba **0,6** (`maxUpStep`) pero **salta** ~**1,25**. De
ahí salen los dos escalones que el jugador vio: *"siguen subiendo a la valla para poder entrar en vez de usar las
compuertas"*.

- **Lo que se PISA a `cota`** (un bloque sólido pegado por fuera) da una tapa a **`cota+1`**: de ahí al lomo de la
  valla hay **0,5** y se **sube andando**.
- **Lo que se pisa a `cota+0,5`** (una **losa** a ras del suelo) está a **1,0** del lomo: se **sube saltando**.

**Medido** (arnés, aldea 2, partida del jugador copiada) — las **dos** causas, y las dos eran construcción del pueblo:

1. **El COMPOSTERO del granjero** (su puesto de trabajo) estaba a `corner.x-2`, **pegado** a la valla
   (`corner.x-1`), con la tapa a `cota+1`: el granjero se subía por su propio compostero. Se veía en el guardado en
   los **tres** bancales (3 celdas de valla por bancal desde las que se podía subir, todas desde el compostero).
   **Migración 64**: el compostero pasa a `corner.x-3` (`COMPOSTERO_DX`), con una celda de aire entre él y la valla.
2. **Las LOSAS que tapan la ACEQUIA**, en sus **dos extremos**: la acequia va tapada con una losa (para que el agua
   no se congele y para que nadie se caiga dentro), la losa se pisa a `cota+0,5` y las **compuertas del bancal caen
   justo en la fila del medio** (el centro de los lados), así que el aldeano **saltaba la compuerta** desde la losa
   del extremo. Medido: **42** lecturas de un granjero de pie sobre la valla (`y = cota+1,5`) en una corrida de 4 min,
   **todas** en la fila de la acequia y en los extremos del anillo. **Migración 65**: los dos extremos de la acequia
   vuelven a ser **celdas de cultivo** (se quita la losa y el agua se vuelve tierra de cultivo regada), así que la
   capa que se pisa queda a la altura de la tierra (119,94) y desde ahí **no se llega** al lomo de la valla. De paso
   cada bancal gana **dos celdas plantables**.

**Regla:** alrededor de la valla de un bancal **nada que se pueda pisar** salvo el suelo del bancal. En concreto:
nada sólido pegado por fuera a la capa que se pisa (ni compostero, ni cofre, ni un poste), y la fila del agua va
tapada pero **no llega a la valla** (sus extremos son celdas de cultivo). Y el mismo patrón vale para cualquier
cerca del pueblo que quiera ser un cierre (el corral): si al lado hay una tapa, el cierre no cierra.

### I41 · El fuego se paga con LEÑA del almacén, y la RESERVA no se toca

Lo pidió el jugador: *"el smoker, el furnance y todos los aparatos donde se tenga que quemar necesitan ir por logs al
almacén para que se use de combustible y funcionen"*. Los dos aparatos que **queman** de verdad en el pueblo son el
**ahumador** del cocinero y la **fragua** del herrero (sus fundiciones); los hornos del mundo (cocina de la taberna,
desván de la posada y los dos de la herrería) son **decorativos** y no son estación de nadie (I31), así que no hay
nada que alimentar en ellos, y el **hogar** es un campfire de vanilla.

- **Un tronco por faena**: una tanda de cocina (hasta 8 piezas) y cada fundición (pepitas → lingote, chatarra →
  lingote, chatarra de oro → lingote) queman **un** tronco de los que el aldeano se ha traído del almacén.
- **La leña la trae el propio aparato**: el **cocinero** cruza el pueblo al almacén (`VillageStorage.puntoDeApoyo`) y
  se lleva hasta **4** troncos (`LENA_POR_VIAJE`); el **herrero** ya iba al almacén en su fase `RECOGER`, así que se
  lleva su tronco en el mismo viaje. Nada de combustible «de la nada».
- **La reserva de 32 (`RESERVA_LENA`) es un suelo duro para el fuego**: la madera es **también** la materia prima del
  herrero (1 tronco → 4 tablones → escudo, arco y flechas), así que `quitarLena` **solo** entrega el excedente por
  encima de la reserva. Con el almacén en la reserva el ahumador **se apaga** y lo dice en su etiqueta
  (*"Sin lena para el ahumador"*, con una línea de log una vez), y el herrero **no elige** las recetas que queman
  (se pone a lo que no gasta fuego: aserrar, palos, forjar). Ni el fuego ni el herrero **bajan de 32**.
- **La pierna de la leña tiene su propio contador de atasco** (extiende I38): son ~20-25 bloques de ida, así que con
  los 200 ticks (10 s) del puesto se rendiría a mitad de camino (el mismo fallo que se midió en el agua del clérigo).
  Su contador es `stuckLena` con `STUCK_LENA` = 20 s, y al volver a la cocina `mejorDistancia`/`stuckTicks` se
  **ponen a cero**: con el contador compartido, la vuelta parece «no acercarse».

**Medido** (arnés headless, partida del jugador copiada, día fijo) — las tres cosas:

```
[Village] El cocinero: cogio 4 tronco(s) del almacen para el ahumador (aldea 2; quedan 192 en el almacen)
[Village] El cocinero: 8 pieza(s) cocinadas con un tronco del almacen (aldea 2)      (x8 tandas)
[Village] El herrero de herramientas: Fundio 9 pepitas en un lingote (quemo un tronco del almacen)
--- y con el almacén en la reserva (34 troncos: solo 2 que quemar) ---
[Village] El cocinero: cogio 2 tronco(s) del almacen para el ahumador (aldea 2; quedan 32 en el almacen)
[Village] El cocinero no cocina: el almacen no tiene lena por encima de la reserva de 32 (aldea 2), asi que el
          ahumador se queda apagado
[Arnes] COMBUSTIBLE: lenaEnAlmacen=32 (reserva=32) carneCrudaEnDespensa=26    (clavado en 32, 20 lecturas)
[Village] El herrero de herramientas: Aserro un tronco en 4 tablones            (sin leña NO funde)
```

**Regla:** ningún aparato que queme funciona sin su tronco del almacén, y el fuego **nunca** toca la reserva. Todo lo
que se mide está en `tools/arnes/medidas-combustible.txt` y el barrido de la partida (aparatos + madera) en
`build/combustible_aldea.py`.

### I42 · La aldea ARRANCA con madera en el almacén (la remesa inicial de 128 troncos)

Lo pidió el jugador después de la regla del fuego: *"considera entonces que inicialmente tenga la aldea suficiente
madera en el almacén, unos 128 logs"*. Sin ella, una aldea **recién fundada** nace con el **ahumador apagado** y la
**fragua fría** (y sin tablones ni palos) hasta que el **leñador** tale los primeros árboles y los baje al almacén.

- **128 troncos de roble** (dos pilas completas, `REMESA_INICIAL_TRONCOS`), puestos **de una vez** en el almacén.
- **Solo al almacén VACÍO**: la misma regla que la remesa de la despensa (`VillagePantry.remesaInicial` → «si tiene
  cosas dentro, no se le añade nada»). Así una aldea **en marcha** —donde el recolector ya ha dejado algo— no recibe
  nada, y esto **no es un grifo de troncos** (habría que vaciar el almacén entero, y el pueblo lo mantiene con
  material).
- **Dónde se llama**: en el bloque de «asegurar» del **latido**, **justo después** de
  `VillageGenerator.asegurarAlmacen` (que es quien coloca el primer cofre doble). En la **misma pasada** en que el
  almacén nace ya tiene su madera, así que no hay ventana en la que el cocinero lo encuentre vacío. No hace falta
  migración: el bloque corre para aldeas nuevas y viejas (I6).

**Medido** (arnés: se vacía el almacén entero de la aldea 2, que es el caso de la aldea recién fundada):

```
[Arnes] REMESA: almacen vaciado (17 pila(s) fuera, 0 troncos antes): en la siguiente pasada del latido tiene que
        entrar la remesa inicial
[Arnes] COMBUSTIBLE: lenaEnAlmacen=0 (reserva=32) ...
[Village] almacen: remesa inicial de madera (128 troncos de roble para el fuego del cocinero, la fragua del herrero
          y su sierra)
[Village] El cocinero: cogio 4 tronco(s) del almacen para el ahumador (aldea 2; quedan 124 en el almacen)
[Arnes] COMBUSTIBLE: lenaEnAlmacen=124 ... (y no vuelve a subir: la remesa es UNA vez, no un grifo)
```

**Regla:** un almacén **vacío** es un almacén **sin madera**: se le pone la remesa inicial (128), una sola vez. Con
la reserva de 32, esa remesa deja **96** troncos para quemar y aserrar y **32** intocables para la madera del
herrero.

### I43 · Una cama son DOS POIs `HOME`: no se comparte, y el que no llega en 60 s la pierde

Lo vio el jugador en una captura: dos granjeros con la etiqueta **"Sin cama"** de pie en la huerta toda la noche, *"si
la aldea está repleta de ellas"*. Son **dos reglas de vanilla** las que se la quitaban, y las dos hay que respetarlas al
repartir camas:

- **Una cama son DOS puntos de interés `HOME`** (el bloque de la cabeza y el del pie), así que dos aldeanos pueden
  acabar con **una mitad cada uno**. El que se duerme pone `OCCUPIED` en las **dos** mitades y al otro —que está a ≤16
  bloques y **no** está durmiendo— el comportamiento `ValidateNearbyPoi` del cerebro le **borra el `HOME`**:
  `if (!poiManager.exists(pos, HOME)) erase(); else if (bedIsOccupied(level, pos, entity)) { erase(); release(pos); }`.
  Medido: Isidoro tenía la mitad de la cama del herrero (`1442/1443,131,1432`), el herrero se durmió y a Isidoro le
  borraron la cama; el latido se la volvía a dar (**la misma**) en bucle.
- **`SetWalkTargetFromBlockMemory`** (paquete `REST`, registrado para `HOME`): si el aldeano lleva **1200 ticks (60 s)**
  con `CANT_REACH_WALK_TARGET_SINCE` puesto —o sea, sin conseguir ruta a su cama—, hace `releasePoi(HOME)` +
  `erase()`. Es decir: **una cama a la que el aldeano no puede llegar se pierde sola en un minuto**, y el latido se la
  vuelve a dar… en bucle. Medido a resolución de tick: pérdida cada 60 s exactos, con la cama **libre, con POI y sin
  nadie durmiendo** (ni la rama (a) ni la (b) de `ValidateNearbyPoi`: el culpable era el minuto).

**Regla:** al aldeano sin cama solo se le da una cama que sea **suya y alcanzable**:

1. **No compartida**: ni la cama ni su **otra mitad** pueden estar en el cerebro de otro aldeano. El dueño se busca
   **alrededor de la cama** (`laTieneOtro`), no solo en la lista del censo (que se arma alrededor de la plaza): un
   granjero en su bancal o un leñador en la arboleda no están en esa lista y la cama parecía libre.
2. **No ocupada** (`BedBlock.OCCUPIED`, en cualquiera de las dos mitades) y **entera** (las dos mitades son camas).
3. **Alcanzable**: fuera de un bancal, solo si `villager.getNavigation().createPath(cama, 1).canReach()`. **Encerrado
   en un bancal** (cercado con valla y compuertas cerradas) sí vale la que más se acerca —es el rescate, y el goal del
   granjero lo saca por la compuerta (I44)—, pero **solo** en ese caso: una cama a la que no llega le cuesta el `HOME`
   a los 60 s. Medido: el herrero recibía una cama del dormitorio con **un muro de adoquín** de por medio (su ruta
   acababa a 2,00 bloques, y para acostarse hace falta ≤2,0) y no se dormía nunca.
4. **Y si el planificador no le deja dar los dos últimos pasos, el pueblo le lleva y le acuesta.** Hay camas a las que
   el juego **no le acerca a los 2,0** que pide `SleepInBed` aunque las tenga a la vista y pueda entrar en la
   habitación: **medido** con el herrero de herramientas (ruta de 25 nodos hasta `1447,120,1404`, dentro del
   dormitorio, y desde ahí las rutas a las celdas de al lado de su cama acababan a **2,00** y **3,00** bloques). En ese
   caso el reparto guarda una **celda de espera** (`celdaParaAcostarse`: la más cercana **alcanzable** desde la que
   **vea** la cama) y el latido hace dos cosas: le **manda** a ella (*"Yendo a dormir"*) y, cuando la tiene a la vista y
   a menos de 6 bloques, le **acuesta** (`acostarAlQueNoLlega` → `startSleeping`, la misma llamada que usa el juego).
   OJO con la línea de visión: **la propia cama no cuenta como obstáculo** (la mirada acaba dentro de su bloque, así que
   el raycast choca con ella y —si contara— no habría ninguna celda con vista: medido), solo bloquea un muro.
   Y la celda de espera **no exige que el planificador llegue**: vale la más cercana que el aldeano **vea** (a menos de
   6 bloques) y, si el planificador tampoco le lleva ahí, **se le mueve a ella** y se le acuesta. Una cama que se ve y
   está a un paso **no se descarta**: con camas de sobra, el que no duerme es el aldeano, no la cama.
   No es un teletransporte a ciegas: el aldeano **llega andando** (o se le acerca esos últimos bloques a la vista).

**Medido después** (misma partida, misma noche): **`CAMAS RESUMEN: adultos=11 conCama=11 COMPARTIDAS=0 SIN CAMA=0
DURMIENDO=11`** — el pueblo entero con cama **y durmiendo** — y **cero** pérdidas de cama en toda la corrida. Todo en
`tools/arnes/medidas-camas.txt`.

**Pendiente (dicho claramente)**: una **cama sin acceso** —con un muro o mobiliario que impide ponerse a ≤2,0— no se le
da a nadie y ese aldeano se queda sin cama. Es construcción/mobiliario del pueblo, no del reparto.

### I44 · El goal de portones RE-ELIGE, VALIDA su lista y hace REHACER el camino

Tres reglas que salieron del mismo encierro (el granjero que no podía salir del bancal a dormir):

1. **Re-elegir el portón si el que tiene no es el de al lado.** `portonMasCercano` solo se llama desde `canUse`, y
   `canUse` **no se vuelve a llamar mientras el goal está corriendo** (sigue mientras tenga un portón a menos de
   `RADIO`): un portón elegido a mala hora —la aldea a medio migrar, la casilla sin cargar— **se quedaba pegado para
   siempre**. Medido: el granjero tenía elegida la compuerta **norte** (a 7,09 bloques) en vez de la **este** (a
   **0,87**), así que «vigilaba» una puerta lejana y no abría la suya; por eso se pasaba la noche dentro del bancal con
   la cama al otro lado. Ahora, en `tick`, si `distancia > ABRIR` se vuelve a elegir la más cercana de verdad.
2. **Validar la lista guardada.** La lista de portones se cachea **una vez por goal** con la cota del momento: si el
   goal se creó con la aldea a medio migrar, sus posiciones caen al aire y el aldeano se queda **sin poder abrir
   ninguna puerta**. Ahora, si ninguna puerta de la lista está **cerca y es una puerta de verdad** (`FenceGateBlock`),
   se recalcula (como mucho una vez cada 5 s: la cota mira el terreno).
3. **Hacer REHACER el camino al abrir** (`abrir`): la ruta que traía el aldeano se calculó con la compuerta **cerrada**
   —el juego no le deja planificar a través de una puerta de valla cerrada—, así que acaba en su propia casilla y el
   aldeano **no se mueve**; la compuerta se cierra a los 5 s sin que nadie la cruce y vuelta a empezar. Al abrir se le
   borran `WALK_TARGET` y `PATH` (y se para la navegación) para que el cerebro vuelva a pedir el destino **con la
   compuerta ya abierta**.

**Y la celda de salida de un bancal es la de FUERA**, no la de dentro (`VillageGenerator.salidaDeLaParcela`): el goal
de portones solo abre si el destino del aldeano está **al otro lado** (`vaACruzar`); mandándolo a la celda de dentro
—la de entrar— la compuerta **no se abre** (medido: el granjero se quedaba en `1394,119,1452`, pegado a la valla).

### I45 · El aldeano CIERRA la puerta que cruza (y no toca las que no son suyas)

Lo pidió el jugador: *"los aldeanos cuando vayan a dormir tienen que cerrar la puerta porque todas la dejan abierta"*.
El juego tiene su comportamiento para cerrarlas (`InteractWithDoor` + la memoria `DOORS_TO_CLOSE`), pero **con los
aldeanos del pueblo no cierra nada**: medido con el arnés al empezar la noche, **8-9 puertas de madera abiertas** en el
recinto y ninguna se cerraba sola. Como con las puertas de valla ({@code VillagerGateGoal}, que el juego no deja abrir
a un aldeano), **el pueblo lo hace por su cuenta** (`VillagerDoorGoal`, sin banderas):

- Solo mira puertas **de madera** (`DoorBlock.isWoodenDoor`; las de hierro no las puede abrir un aldeano) **abiertas**
  y **pegadas al aldeano** (en su casilla o al lado: la que está cruzando).
- Se apunta que la ha **usado** cuando está **en el hueco** (a menos de 1,5). Una puerta que solo tiene al lado —o la
  que **el jugador** dejó abierta y el aldeano pasa por delante— **no se toca**: no es suya.
- Cuando ya ha pasado al otro lado, la cierra (`DoorBlock.setOpen(villager, level, estado, pos, false)`: cierra las dos
  mitades y suena) tras unos ticks de cortesía.
- Y **no se le cierra a un jugador al lado** (a menos de 2,5).

**Regla:** el pueblo cierra lo que **usa** (la puerta que cruza, y el portón de valla que abre, I44) y **no toca** lo
demás: las puertas que el jugador deja abiertas a propósito se quedan como están. Medido: **36 cierres** en una corrida
de día, cada uno con su aldeano al lado (`PUERTA CERRADA en 1443,120,1401 (aldeano(s) al lado: 9e0ed6e3)`), y el
contador de abiertas **baja de 8-9 a 4** (las que quedan son de la posada, que nadie cruza de día).

### I46 · Las CAMAS se reparten también a las CRÍAS y aunque haya bichos dentro de la aldea

Dos causas del mismo cartel, *"Mauricio sigue sin ir a buscar cama y hay varias en la taberna"* (con captura:
`Mauricio (Sin oficio) · Sin cama`):

1. **Las crías cuentan.** `reclamarCamasDelPueblo` las **saltaba** ("una cría duerme con el pueblo"), pero vanilla **sí**
   deja que una cría reclame `HOME` —en su propia aldea, Ubaldo y Nicasio (crías) la tienen— y la etiqueta de la
   **noche** mira la rama de descanso **antes** que la de cría, así que a una cría sin cama se le pone **"Sin cama"**:
   el jugador veía un cartel pidiendo una cama sobre un aldeano al que el reparto **ignoraba a propósito**. Mauricio
   (`2b322f2d`) y Leoncio (`2a04aa3e`) eran crías y se quedaban de pie.
2. **El reparto de camas NO depende del "latido en paz".** Vivía en `prepareRepairs` (dentro de `tickVillageLife`),
   detrás de `!isUnderAttack(...)` y de `hayEnemigosDentro(...) -> continue`: con un monstruo dentro del recinto —y la
   noche del asedio es cuando más falta hace una cama— **nadie** recibía cama (y, de paso, el estado del pueblo se
   quedaba congelado). Ahora va en `atenderCamasDelPueblo`, llamado desde `manageNearby` **fuera** de las dos guardas
   (solo se salta una aldea **caída**).

**Regla:** todo aldeano **adulto o cría** con `FENCE_RADIUS + 44` de la plaza acaba con **cama propia**, y la recibe
**también** con la aldea bajo asedio o con bichos dentro. **Quien mide tiene que contar lo mismo que el mod**: el censo
del arnés medía **64 bloques y solo adultos**, y con esos dos recortes decía `SIN CAMA=0` mientras el jugador veía lo
contrario (ahora: 106 y con crías). Medido (noche fija, un aldeano-zombi plantado dentro → `UN BICHO DENTRO: SI … latido
cortado`): **antes** `aldeanos=15 (adultos=11 crias=4) conCama=13 … SIN CAMA=2 2a04aa3e(none,cria) 2b322f2d(none,cria)
DURMIENDO=6`; **después** los dos reclaman cama (`1372,124,1433` y `1370,124,1433`) y `conCama=15 … SIN CAMA=0
DURMIENDO=12`. Y el mod avisa con **nombres** cuando alguien se queda sin cama (una vez por cambio, no cada latido).

### I47 · Una puerta NO se cierra con alguien dentro del hueco

El jugador lo vio: *"estaba en el estado «cerrando la puerta» pero un aldeano lo movió y empezó a caminar
erráticamente"*. El goal del pueblo (I45) cerraba **5 ticks después** de que el que la cruzó saliera del hueco, **sin
mirar quién más había dentro**: dos aldeanos cruzando seguidos (lo normal al irse a dormir) y la puerta se cerraba
**encima del segundo**, que queda atrapado contra su caja de colisión —vibra, anda a tirones y empuja al primero—.

**Regla:** antes de cerrar se comprueba que **no haya nadie en el hueco**: las **dos mitades** de la puerta y
**cualquier entidad** (no solo un jugador, que ya tenía su guarda de 2,5). Si hay alguien, no se cierra y se vuelve a
esperar. El arnés lo cuenta en su barrido —**centro de la entidad dentro de la celda** de la puerta: con la caja de
colisión rozando la celda salen falsos positivos—: `PUERTAS DE MADERA ABIERTAS … (cerradas CON alguien dentro: M)`.
Medido en el mismo mundo, con y sin la guarda: **sin** ella,
`PUERTA CERRADA CON ALGUIEN DENTRO en 1443,120,1401: villager pos=(1443.34,120.00,1401.52) velocidad=0.00` (atrapado y
parado: el estado del que se quejó el jugador) y el contador en **1**; **con** ella, ese caso es **0 en todos los
barridos** y las puertas se siguen cerrando (**8 cierres**, con la etiqueta "Cerrando la puerta" del goal puesta en el
que la cierra; el contador de abiertas baja de 6 a 4).

### I48 · El cocinero cocina EN la cocina, viendo el ahumador

Lo reportó el jugador con captura: *"El cocinero está cocinando FUERA de la taberna. Esto no debe ser así, debe estar
adentro"*. El goal daba por llegado al cocinero mirando **solo distancias** (`REACH = 6,5` a la casilla de la cocina y
8,0 al ahumador) y **nada del camino**, así que cocinaba desde la plaza (a 5,76 de la casilla, `VEelAhumador=NO`) y
desde el comedor a través del tabique (a 6,19, `VEelAhumador=NO`).

**Regla:** el cocinero trabaja **solo** desde la casilla de delante del ahumador (`REACH = 2,0`) y **viendo** el
ahumador: `VillageManager.hayVistaLibre` (rayo de colisión: vía libre o choque contra el propio objetivo; es la misma
comprobación que usa el sueño para no acostar a nadie a través de un muro). Y antes de apretar el alcance hay que
**comprobar que la ruta llega** (`ruta: a1=19n alcance=SI fin=1442,120,1429 dFin=0.00`): si no llegara, el goal se
rendiría con el aparcado de I33 y la aldea se quedaría **sin cocina**. Medido después: `dentroDeLaTaberna=SI
VEelAhumador=SI dCasilla=0,52` y `El cocinero: 8 pieza(s) cocinadas con un tronco del almacen`.

### I49 · El SELLO no puede tumbar el servidor (un spawn sin tipo no es un spawn)

`EntityJoinLevelEvent` entrega también lo que entra al mundo con `addFreshEntity` —mecánicas del mod, otros mods,
`/summon` de código—, y ahí `mob.getSpawnType()` es **`null`**. `esSpawnQueElSelloCorta` hacía `switch (tipo)` sobre él:
`NullPointerException` y **el servidor entero al suelo** (medido: al plantar el arnés un aldeano-zombi dentro de una
aldea protegida, `Cannot invoke "MobSpawnType.ordinal()" because "tipo" is null`). El sello solo corta lo que **sabe**
que es un spawn natural.

**Regla:** ningún camino del sello (ni del aura) puede **desreferenciar** el tipo de spawn: sin tipo, **pasa** (lo que
carga del guardado y lo que sueltan el jugador o el mod ya tenían su trato aparte). Un evento de entrada al mundo
**nunca** puede tirar el servidor.

### I50 · Una construcción de PLANTILLA no puede quedarse con un hueco que el PLANO no recuerde

Lo vio el jugador con captura: *"¿qué ves de extraño en esta casa? ¡si le falta completarse a la pared! corrígelo y
checa que el cofre no estorbe"*. Medido en su guardado: la casa `plains_medium_house_2` tenía **aire** en dos celdas de
su pared oeste (`1427,120,1392` y `1427,121,1392`, un boquete de 1x2 junto a la puerta) **donde su plantilla pide
adoquín**; y el **plano de la aldea tampoco tenía esas celdas**, porque el plano de una aldea **migrada** se captura
**escaneando el mundo** y el escaneo **descarta el aire**: el hueco ya estaba cuando se capturó y se volvió "lo
correcto". Con el hueco fuera del plano, el **obrero no tenía nada que reponer**: el agujero era invisible para el
pueblo.

**Regla:** cada construcción de plantilla (las 4 casas, la iglesia y la herrería) se compara con **su plantilla**
—`VillageGenerator.cerrarHuecosDeLasCasas`, con los mismos sorteos deterministas que `generate`— y se rellena **sólo lo
que la plantilla pide y el mundo tiene en AIRE**:

1. **Lo que ya hay no se toca** (cofres, camas, puestos de trabajo, lo del jugador): sólo se rellena el aire. Medido:
   el cofre de `1428,120,1392` —pegado al hueco— queda con sus 27 huecos y **los mismos objetos** antes y después, y el
   bloque nuevo se pone en la celda del hueco, a su lado.
2. **Si la plantilla no encaja con el mundo** (menos de la mitad de sus celdas coinciden), esa construcción no es la
   de esa plantilla y **no se toca nada**: un error de cálculo no puede llenar de bloques una casa ajena.
3. **Lo repuesto entra en el plano** (`Blueprint.conCelda`): si no, el obrero no lo mantendría (I8).
4. **Es idempotente** y lo llama el latido (aldea en paz): cada casa canta sus huecos **una sola vez**.

Medido: `2 hueco(s) … 1427,120,1392(cobblestone) 1427,121,1392(cobblestone)` en la casa del jugador y, de propina, **2
huecos en la fragua de la herrería** (`1423,120,1368/1369`, la **lava** de la plantilla de herrero, que el guardado
también había perdido) → `Aldea 2: 4 hueco(s) de las casas del juego tapados desde su plantilla`.

### I51 · La cosecha del granjero NO se queda en el suelo (la barre él)

Lo reportó el jugador: *"los granjeros están dejando muchos vegetales en el suelo cuando cosechan"*. Medido con el
arnés: **502 lecturas de vegetal tirado** en los bancales sobre 345 barridos, con **edad mediana 1077 ticks (54 s)**,
máxima **4597 (230 s)** y **3 zanahorias todavía al final** de la corrida (a punto de desaparecer a los 5 min). Y no era
sólo "no me cabe": la granjera tenía **2 huecos libres de 8**. Parte de lo que cae lo suelta el **propio juego** (su
faena `HarvestFarmland` cosecha con `destroyBlock(..., true)` y espera que el aldeano **pise** el objeto), y el
**recolector no puede entrar** en las parcelas (están cercadas y las compuertas de valla no las abre un aldeano).

**Regla:** en la huerta, **el que ensucia barre**: el granjero es el único que puede entrar en su bancal, así que
(`VillagerFarmGoal`):

1. **Barre su bancal** (`Tarea.RECOGER`): el objeto caído más cercano **dentro del bancal en el que está** —trigo,
   zanahoria, patata, betabel y las semillas que no le sobren—, y **sigue con el siguiente** mientras le quepa. Lo que
   no le quepa se queda en el suelo (nunca se borra nada del pueblo).
2. **No cosecha lo que no le cabe** (`leCabeLaCosecha` con `Block.getDrops` **antes** de romper la planta): se va antes
   a la despensa y la cosecha se queda en la planta. Si la despensa está llena y no le deja hueco, se apunta
   (`despensaNoTraga`) para no quedarse en un bucle de viajes: entonces cosecha y lo que sobre **lo barre él**.
3. **El betabel entra en la lista blanca del recolector** (`esDelPueblo`): el juego sólo deja recoger
   BETABEL_SEMILLAS, así que un betabel caído **no lo cogía nadie**.

Medido, mismo mundo y mismos barridos: de **502 lecturas (mediana 54 s; 340 por encima de 30 s)** a **63 lecturas
(0,38 por barrido; mediana 6 s; sólo 3 por encima de 30 s)**, y lo único que se ve caer son las **semillas del
compostero**, que se caen a propósito. Y el granjero no pierde su faena: en la corrida salen 143 etiquetas
"Recogiendo lo que se cayo", 115 "Cosechando" y sus viajes con "Guardo N en la despensa". **La faena del juego
(`HarvestFarmland`) no se quita** —la API de `Brain` no permite quitar una sola faena—, así que sigue soltando lo suyo;
lo que ya no pasa es que eso se quede ahí.

### I52 · El reloj de la comida de una CRÍA no corre (y un aviso dice lo que se ha MEDIDO)

El jugador, con la captura del cofre lleno de comida delante: *"me dice que la aldea pasa hambre y que la despensa
está vacía, sin embargo hay bastante comida"*.

**Medido** en su guardado y su log (aldea 2, centro `1414,1414`, cota `120`; `build/hambre_medida.py` vuelca la comida
de cada aldea y la marca `DevilRpgUltimaComida` de cada aldeano):

| Qué | Lo que decía |
|---|---|
| El chat (04:01:17) | `La aldea pasa hambre: la despensa esta vacia.` |
| El mismo latido | murió `Ubaldo (Sin oficio)`, *"de hambre (19 min sin comer)"*, en `1442,121,1439` (el hueco de la escalera de la taberna) |
| La despensa | `comida 64 puntos` en el latido (es `MAX_FOOD`, el tope del contador) y **986** en el canto del pescador; `Food: 64` en el guardado |
| Los adultos | los **once** con la **misma** marca `DevilRpgUltimaComida = 104400` (el `gameTime` del guardado es `104794`): el reparto les llegaba a todos |
| Las crías | con la marca del día que nacieron: Mauricio `86400`, Nicasio `92400` |

**La causa (dos capas).** Ubaldo era una **cría**:
1. **A su reloj nadie le daba cuerda.** `repartirRaciones` la salta a propósito (una cría mama de la aldea y no gasta
   ración) y `pasarHambre` también, así que su marca se estrenaba el día que nacía (la estrena `ultimaComida` la
   primera vez que el latido la ve) y **no se volvía a tocar en toda su infancia**. El día que **creció** —vanilla,
   **24000 ticks (20 min)**— dejó de ser cría y en el **primer latido** llevaba 20 min "sin comer": pasó el umbral de
   muerte (`STARVATION_DEATH_TICKS` = 10 min) y **murió en el acto**, con la despensa llena. Los **19 min** del log
   son exactamente la infancia de la cría (la marca se estrena hasta 10 s después de nacer).
2. **El aviso no miraba la despensa.** Se cantaba con `algunaBocaSinComer` y decía *"la despensa esta vacia"* sin
   leerla.

**Regla:** (a) a la cría **se le refresca la marca** mientras es cría (no basta con saltársela): su reloj de comida
**no corre** hasta que es adulta, y así el día que crece come como cualquier adulto; (b) el aviso **dice lo que se ha
contado** (cuántas bocas sin ración y cuántos puntos quedan): *"la despensa está vacía"* solo si de verdad no hay ni
un punto; y (c) la comida **no cuelga de un tick del mundo**: `repartirRaciones` salía de vacío si
`gameTime % EAT_INTERVAL_TICKS != 0` ("las raciones se reparten en el latido del minuto"), así que **un solo tick
perdido** —el jugador lejos, o el latido cortado con bichos dentro (I12/I46)— se llevaba por delante la comida de
**todo** el pueblo (y la siguiente no llegaba hasta el minuto siguiente). Ahora **la pide el aldeano que hace más
tiempo que no come** (`laMasVieja`): cuando ese cumple su intervalo, come el pueblo que esté esperando —el grupo
sigue sincronizado porque una comida los marca a todos a la vez, así que se sigue pagando **de una sola vez** y sin
regalar una hogaza por boca (ver el aviso del método)—.

**No tiene regla en el lint** (es el orden y el reloj de un bucle, no un patrón de texto): se comprueba **contra el
guardado** con `build/hambre_medida.py` (la comida de cada aldea y la marca de comida de cada aldeano, con el
`gameTime` del guardado para saber **quién** lleva sin comer).

### I53 · Un bancal sembrado se mantiene sembrado (el granjero también SIEMBRA la celda vacía)

El jugador, mirando su bancal: *"¿por qué hay partes de la parcela que no tienen plantado nada? se supone que los
granjeros deben tener todas ocupadas"*.

**Medido** en su guardado (`build/huerta_vacias.py`, celda a celda, aldea 2 cota `120`): los tres bancales tienen
**66, 69 y 71** de sus **72** celdas plantables ocupadas, y las **16** que faltan son `farmland` **con el hueco de
arriba libre** (sembrables y vacías), **casi todas en los carriles por los que se entra y se sale del bancal** (los
dos extremos de la acequia y las columnas de los lados). En la **aldea 0**, que lleva más tiempo sin verse, faltan
**147 de 216** (bancal 0: 70 de 72; bancal 1: 14 —13 vacías y una calva—; bancal 2: 63). *(Y ojo con lo que **no** es
un hueco: los cultivos de **edad 0-1** son dos píxeles verdes y desde arriba parecen tierra —en el bancal 1 eran
**30 de 69**, el 43%—; eso es lo normal, porque el granjero replanta cada celda que cosecha.)*

**Causa (y es de ORDEN, como I25 y I51).** El granjero tenía **dos** faenas de la tierra —cosechar lo maduro y labrar
la calva, que alternaban desde el arreglo de I25— y **sembrar iba DETRÁS de las dos**. Con tres bancales (216 celdas)
**siempre** hay algo maduro en alguno, así que el paso `PLANTAR` no se alcanzaba **nunca**. Y las celdas se vacían
solas: el **cerebro del aldeano** tiene su propia faena de granjero (`HarvestFarmland`, I51) y **solo replanta si
lleva semillas**, y lo que se **pisa** (I25) se vuelve a labrar pero **nadie lo siembra**. La parcela, entonces,
**solo perdía celdas**.

**Regla:** las **tres** faenas de la tierra **rotan** (`FAENAS_DE_LA_TIERRA` en `VillagerFarmGoal`: cosechar, labrar
y **sembrar**), así que una celda vacía se recupera en la siguiente vuelta en vez de esperar a que no quede nada
maduro (que no pasa nunca). La siembra sigue exigiendo lo de siempre: semillas **en la mano** (si no las tiene, el
paso de recambios lo manda a la despensa) y el hueco de arriba **libre** (I11: no se arranca ningún cultivo).

**No tiene regla en el lint** (es un orden de faenas, no un patrón de texto): se comprueba **contra el guardado** con
`build/huerta_vacias.py`, que vuelca los tres bancales celda a celda (cultivo con su **edad**, tierra vacía y calva)
y cuenta las celdas que no tienen nada.

### I54 · El HUECO de un portón es SAGRADO (ni un farol: deja la puerta INSERVIBLE)

El jugador: *"el ganadero quiere ir a la taberna y no puede, la única salida está obstruida por una lámpara"*.

**Medido** en su guardado (aldea 2, centro `1414,1414`, cota `120`; `build/anexo_porton.py` y, ya versionada, la
auditoría):

- El **portón del corral** (`1455,120,1414`, `oak_fence_gate[facing=west]`) tenía un farol **en la celda de la
  cabeza del cruce**: `(1455,121,1414)` = `lantern` **posado sobre la propia hoja** (`debajo=oak_fence_gate`); las
  dos celdas de al lado y las otras dos capas estaban libres.
- El **plano también pedía ese farol** (`palette[51] = lantern`, con sus vecinas fuera del plano), así que el
  obrero lo habría repuesto.
- La ganadera **Obdulia** estaba **dentro** del corral (`1460.9,120,1418.9`) y con el **almacén aparcado** de no
  poder llegar (`DevilRpgPuntoFallido` = `1461,121,1434`, el punto de apoyo del almacén).
- Auditados **los 14 portones** de las tres aldeas del guardado (12 de los bancales, el del corral y el del
  gallinero), el **único** tapado era ése.

**Por qué deja la puerta inservible.** El aldeano mide **1,95**: al cruzar ocupa la celda de la hoja **y la de
encima**, y un farol **tiene caja de colisión**. Con el farol ahí su caja no cabe, la **navegación no encuentra
camino** por el portón —así que el `VillageGateGoal` no llega ni a abrirlo, porque el aldeano no se acerca— y el que
está dentro **se queda encerrado**: no puede ir a la despensa, ni al almacén, ni a la taberna.

**De dónde salió.** El layout **viejo** de las luces de la cerca del corral ponía un farol en el **medio de cada
lado** de la valla (y otro en el centro), y el medio del lado **oeste es el portón**: quedó en el mundo y en el
plano. El código de hoy reparte los faroles por las **cuatro esquinas y los cuatro medios lados**
(`k = ±(ANEXO_RADIO − 2)`), que **no** pasa por el portón… pero tampoco lo quitaba. Y la autocomprobación
(`auditarFarolesFlotantes`) **no lo canta**: ese farol *sí* tiene apoyo; el problema es que el apoyo es la puerta.

**Regla:**
- **Nada sólido en el carril de un portón**: la hoja y las dos celdas de al lado, en la capa que se pisa **y en la de
  la cabeza**. En particular, `farolSobreElPoste` **no pone un farol sobre una puerta de valla** (`FenceGateBlock`):
  devuelve 0.
- Al que **ya** está, se le **muda el farol a un poste de al lado** (`despejarElHuecoDeLosPortones`, idempotente, en
  el latido): se quita del carril y se posa en el primer poste de la valla con el hueco libre (nunca se tira: la luz
  del pueblo se queda donde hacía falta); si no hay poste libre, se quita y ya.
- **Y la celda sale del PLANO** (`Blueprint.sinCelda`): si el plano sigue pidiendo el farol, el obrero lo repone en la
  siguiente pasada.
- Los portones salen de **una sola lista** (`VillageGenerator.todosLosPortones`, I4): la usan el goal que los abre
  (`VillageGateGoal`), el despeje del hueco y la auditoría.

**Se comprueba con `tools/audita_aldea.py`** (apartado **F**, *portones con el hueco tapado*: mira la capa de la
cabeza de las tres celdas de cada portón y **salta el del gallinero**, que es un hueco de un bloque a propósito —
los pollos pasan, los aldeanos no—) y con `build/portones_farol.py` y `build/anexo_porton.py`, que vuelcan el corral
y sus portones celda a celda.

> **De propina, I6: lo que ya está bien no se reconstruye.** `posarFarolesFlotantes` quitaba y volvía a poner **los
> doce faroles del corral en cada latido** —el log lo cantaba cada 10 s (*"14 faroles puestos en la cerca del corral
> anexo"*, para siempre)— porque daba por *flotante* cualquier farol que estuviera a 1-3 bloques del apoyo. Ahora un
> farol **a un bloque del apoyo y posado** es *su* farol y no se toca: solo se muda el que **cuelga** de un poste
> (I14) o el que quedó a 2-3 bloques.

### I55 · Lo que el CLIENTE necesita para dibujar va SINCRONIZADO (los datos persistentes no viajan)

El jugador: *"cambia el render de los guardias para que se vea que están usando armadura y las armas que llevan"*.

**Medido en el código**: el modelo y el renderer del guardia ya existían (`GuardVillagerModel` = cuerpo de jugador +
cabeza de aldeano, con las capas de vanilla `HumanoidArmorLayer` e `ItemInHandLayer`, y `GuardVillagerRenderer`
registrado **en lugar** del de vanilla, que delega en él para los aldeanos normales)… y **no se veía nada**:
`VillagerGuardGoal.esGuardia` leía la marca de los **datos persistentes** del aldeano
(`villager.getPersistentData()`), que son **solo del servidor**. En el cliente esa marca está **siempre vacía**, así
que `esGuardia` devolvía `false` para todos, el renderer se iba por la rama del aldeano normal y **ningún guardia
enseñaba su armadura ni su arma** (que sí llevaba puestas: el equipo de un mob lo manda el servidor en
`ClientboundSetEquipmentPacket`, `ServerEntity` lo hace para **cualquier** `LivingEntity`).

**Regla:** la marca de verdad sigue en los datos persistentes (servidor), y se **espeja** en una attachment
**sincronizada** (`ModCapabilities.VILLAGER_GUARD`: 0 = no es guardia, 1 = espadachín, 2 = arquero) con
`VillagerGuardGoal.sincronizarMarcaDeGuardia`, que **solo escribe cuando el valor cambia** (nada de un paquete por
aldeano cada latido) y la llaman el alistamiento, la baja y el reparto de la milicia (que pasa cada latido). Y las
lecturas (`esGuardia`/`tipoDe`) miran **las dos**: en el servidor manda la marca, en el cliente la copia.

**Ojo, la lección es general**: cualquier dato que el **render** necesite (o el HUD, o el nombre) **tiene que viajar
al cliente** — datos persistentes, `SavedData` o campos del servidor no llegan. Lo que ya viaja solo: el **equipo**
del mob (manos y armadura) y los atributos sincronizados. Se comprueba en juego (ver el modelo y el equipo puestos);
en el guardado, la copia sincronizada queda escrita en el aldeano, así que `build/aldeanos_equipo.py` puede
comprobar que el guardia la tiene.

### I56 · El herrero ROTA entre transformar materiales y fabricar (si no, la armadura no se hace NUNCA)

Lo pidió el jugador tras perder la milicia: *"revisa que el herrero correspondiente esté haciendo armaduras y armas
y que los guardias se estén equipando"*.

**Medido en su log** (aldea 2): la herrera de **herramientas** (Josefa, la que hace la armadura) se pasó la sesión
entre `Hizo 4 palos` y `Aserro un tronco en 4 tablones`, y en el **almacén no había ni una pieza de armadura**
(tenía **19 de cuero** y **8 lingotes de hierro**: material de sobra para cascos, petos, grebas y botas). Su
`elegirReceta` era una **cascada**: primero la transformación de materiales (pepitas y chatarra → lingotes, cuero
viejo y carne → cuero, troncos → tablones, tablones → palos) y **al final** la fabricación; y la transformación
**no se acaba nunca**, porque sus objetivos (32 tablones, 64 palos) se los come **el otro herrero** (el de armas
gasta palos en arcos y flechas y tablones en escudos). La fabricación de armadura no se alcanzaba **jamás**.

**Regla:** el herrero **alterna** una faena de **transformación** y una de **fabricación** (`turnoDeFabricar`, ver
`elegirReceta`): con las dos colas vivas, la armadura sale al mismo ritmo que el resto. Es la lección de **I53** otra
vez: *un paso que va detrás de otro que no termina nunca no se alcanza jamás*.

### I57 · La cama de un aldeano está en SU planta (y una cama que no alcanza se le CAMBIA)

El jugador: *"Zacarías según va a dormir pero está afuera y no toma cama"*.

**Medido en su guardado** (aldea 2, cota 120): Zacarías (`Sin oficio`) tenía por cama la de la **posada**
(`1446,125,1429`, segunda planta de la taberna) y estaba en la calle, en `(1446,120,1427)` — **la misma X/Z, una
planta más abajo**— con la etiqueta *"Yendo a dormir"* y sin acostarse. La celda de espera que le calculó el reparto
era `(1446,125,1427)`, también **arriba**. Y el log lo cantaba en bucle para varios aldeanos
(`no llega a su cama por el camino del juego: se le da 1446,125,1429 y se le mandará a 1446,125,1427`).

**Causa (dos capas).**
1. **La celda de espera podía estar en otra planta.** `celdaParaAcostarse` tiene dos pasadas: la primera exige que el
   aldeano **llegue andando** (`canReach`), y la segunda —el último recurso de I43— aceptaba **cualquier celda que
   viera**, pensada para *"los dos últimos pasos"* (el herrero que se quedaba a 2,00 bloques de su cama). Con una cama
   de la posada, esa segunda pasada elegía una celda **cinco bloques por encima** del aldeano: se le mandaba a ella,
   el planificador le dejaba abajo (o el cerebro le devolvía el destino a la cama, que no alcanza) y el aldeano se
   quedaba plantado **debajo** de su cama para siempre.
2. **Y nadie le cambiaba la cama.** `reclamarCamasDelPueblo` solo da cama al que **no tiene** `HOME`: un aldeano con
   una cama inalcanzable se quedaba en el bucle *reclamar → no llegar → vanilla le borra el HOME a los 60 s →
   reclamar la misma*.

**Regla:** (a) la celda de espera del último recurso tiene que estar a **menos de `PASO_A_LA_ESPERA` (3) bloques** y
en la **misma planta** (o pegada) que el aldeano: si no, esa cama **no es para él** y el reparto prueba la siguiente;
(b) el aldeano que **no se acerca** a su cama en `LATIDOS_PARA_RENUNCIAR_A_LA_CAMA` (6 latidos = 1 min, la regla de
I3: atascado = no acercarse) **la suelta** (con su **ticket**, I23: si no, la cama queda muerta para todos), se le
**aparca el punto** (I33) para que el reparto no se la vuelva a dar y se le busca otra; y (c) el reparto **salta las
camas aparcadas** de ese aldeano. Un aldeano que va andando a su cama desde lejos **no** pierde nada: el contador
solo sube cuando no se acerca.

**Se comprueba contra el guardado** con `build/aldeanos_equipo.py` (la cama de cada aldeano, si está durmiendo y su
posición: la cama y él tienen que estar en la misma planta) y en el log (`no consigue llegar a su cama … se le da
otra`).

> **Ampliación (el guardia bloqueado en la posada).** El jugador: *"el guardia se quedó bloqueado… dice que va rumbo
> al almacén pero no se mueve"*. Medido en su guardado (aldea 2): **Mauricio** (guardia espadachín) en
> `(1455,125,1435)` — la **posada** (segunda planta de la taberna) — con la etiqueta *"Yendo al almacén"* y sin
> moverse, y su cama en `(1452,125,1441)` (arriba también); y la celda de espera de otra cama de la posada
> (`1446,125,1429`) salía en **`1446,125,1427`**, que está **dos bloques al norte, FUERA del edificio** (los aldeanos
> aparecían en `1446,120,1427`: la calle, justo debajo). Dos causas, las dos de la *celda de espera*:
>
> 1. **`hayVistaLibre` daba por bueno un MURO.** El rayo de visión se acepta si el bloque golpeado está a
>    `distManhattan <= 1` del objetivo (la tolerancia que hace falta porque una **cama son dos bloques**), y el **muro
>    de la posada está a un bloque de las camas** que van pegadas a él: así que la celda de espera podía caer **al otro
>    lado de la pared**. **Regla:** solo valen el **propio objetivo** o **su otra mitad** (una cama y la cama de al
>    lado); un muro pegado **no** cuenta.
> 2. **La celda de espera no tenía que ser una celda donde se pueda ESTAR.** El latido **mueve al aldeano a ella**
>    (`acostarAlQueNoLlega`) cuando no llega andando, así que tiene que cumplir lo de siempre: nada sólido dentro,
>    nada sólido a la altura de la cabeza y **suelo firme debajo** (`celdaLibreParaAcostarse`). Sin eso, la celda
>    podía ser el **aire de fuera** (el aldeano se movía allí y **caía a la calle**) o el interior de un muro.
>
> Y el *síntoma* del guardia quieto es de **I5**: de noche el **cerebro** (actividad REST,
> `SetWalkTargetFromBlockMemory(HOME)`) le escribe el destino a **su cama** —sin comprobar si llega— y **pisa** el
> destino que le da el goal de la guardia, así que el aldeano se queda quieto con la etiqueta del mod puesta
> ("Yendo al almacén") mientras el juego lo manda a una cama que no alcanza. Con la cama bien repartida (y la de
> espera bien elegida) el aldeano duerme; y si no hay ninguna cama alcanzable, el reparto lo deja **sin `HOME`**, que
> es mejor que dejarlo clavado: sin cama el cerebro no lo manda a ninguna parte y puede seguir con su faena.
>
> **MEDIDO CON EL ARNÉS, y con dos correcciones que salieron de ahí** (aldea 2, noche fija, `MEDIR_NOCHE`):
>
> - **No se le puede exigir a la celda de espera que esté en SU PLANTA.** Se probó a pedir "misma planta y a ≤3
>   bloques" para no mover a nadie a través del techo, y el resultado fue el contrario: `CAMAS RESUMEN: … conCama=11
>   … **SIN CAMA=4**` — los aldeanos de abajo se quedaban **sin cama** porque todas las que quedaban libres eran las de
>   la posada y ninguna pasaba el filtro. Lo que hace falta es que la celda sea **una celda de verdad**
>   (`celdaLibreParaAcostarse`) y que **vea la cama de verdad**; con eso, el aldeano duerme. *(El jugador confirmó que
>   **él sí sube y baja** las escaleras de la torre andando, así que la geometría no era el problema: era el cerebro
>   tirando de una cama que el aldeano no alcanzaba.)*
> - **El aspecto que quedaba: la cama compartida.** `…COMPARTIDAS=1 [1452,125,1429+1452,125,1430: 9474201f+ffb99daa]`
>   — dos aldeanos con **media cama cada uno**—, porque `laTieneOtro` busca al dueño **alrededor de la cama** y el que
>   va andando hacia ella no está ahí. Ahora, además, se mira el **ticket de la otra mitad**
>   (`PoiManager.getCountInRange(…, Occupancy.IS_OCCUPIED)`): si esa mitad está cogida, es de alguien aunque no se le
>   vea.
> - Y con las dos cosas, la corrida del arnés acaba en **`conCama=15 (camas distintas ocupadas=15) COMPARTIDAS=0
>   SIN CAMA=0 DURMIENDO=15`** — los **15** aldeanos (crías incluidas) con su cama propia y **durmiendo**—, y en toda
>   la corrida `COMPARTIDAS` **no se pone a 1 ni una vez** (con la comprobación vieja salía `COMPARTIDAS=1
>   [1452,125,1429+1452,125,1430]`). Las celdas de espera que se ven en el log son ya **de dentro del edificio**, al
>   lado de la cama (`se le mandara a 1367,124,…`, `1453,125,…`).

### I59 · El SELLO no es lo primero: primero DEFIENDE el pueblo (y el rechazo se VE)

El jugador: *"llegaron unos zombies agresivos durante el día a la aldea, pero no pasó mucho tiempo y fueron
teletransportados a fuera. Esto se ve antinatural"*.

**Medido en su log** (aldea 2, protegida): el sello expulsaba **en cada latido** —`el sello ha expulsado a 1
hostil(es) que estaban dentro` a las 12:08:06, 12:08:16, 12:08:26, 12:08:36… toda la sesión, y también `7` y `2` de
una vez—, o sea que un bicho que **entraba andando** desaparecía **antes del latido siguiente** (10 s), sin que
**nadie** de la aldea lo tocara.

**Por qué existía (y hay que conservarlo).** El aura del sello corta los **spawns**, pero no a los que **ya están
dentro** (los que entraron antes de vencer el asedio, los que se cuelan por un portón abierto, los que se cargan del
guardado) y con uno dentro `hayEnemigosDentro` **corta el latido entero** (I12/I46) → el pueblo se queda congelado.
La expulsión era la **red de seguridad** de eso.

**Regla (lo que cambia, sin tocar lo demás):**
1. **Primero la milicia.** El sello **espera** `SELLO_ANTES_DE_EXPULSAR_TICKS` (2 min) con el intruso dentro. En ese
   rato el que trabaja es el pueblo: los guardias **persiguen a cualquier monstruo que esté dentro del recinto
   aunque esté lejos** (`VillagerGuardGoal.buscarEnemigo`: si no hay nadie en sus 16 bloques, se mira **todo el
   recinto**; el **aldeano-zombi** se queda fuera de esa búsqueda larga a propósito, que puede ser una curación en
   marcha del jugador). Así la defensa **se ve**, que es lo que el jugador pedía.
2. **Solo si sigue dentro** pasado ese tiempo, el sello lo **rechaza**: fuera del muro, en su misma dirección y **sin
   matarlo** (no hay botín gratis y la horda puede volver andando), con sus partículas, un **chillido** de sculk y un
   aviso al jugador (*"El sello de la aldea ha rechazado a los intrusos."*). El rechazo tiene que **leerse** como lo
   que es: un bicho que se esfuma sin explicación es lo que parecía un bug.
3. **Nada más se toca.** Los **asedios** (el del jugador y las **hordas del mundo**) siguen igual: mientras
   `isUnderAttack` es cierto **no se expulsa a nadie** (ésos están ahí a propósito, y la aldea tiene que pelear); y
   los zombis agresivos, sus reglas de spawn, su escalado y su botín **no cambian**.
4. El reloj del sello es **por aldea y por bicho** (`Intruso(aldea, uuid)`) y se olvida en cuanto el bicho **sale o
   muere**: si vuelve a entrar, cuenta de cero. Ojo con limpiarlo **solo** de la aldea que está mirando: el latido de
   una aldea no puede borrar el reloj de un intruso de la vecina (si no, ésa nunca lo rechazaría).

**No tiene regla en el lint** (es un temporizador y dos búsquedas, no un patrón de texto): se comprueba en el log
(`el sello ha expulsado a N hostil(es) que llevaban 120 s dentro (la milicia no pudo con ellos)`, que ya **no**
aparece en cada latido) y en juego (el bicho se queda y lo mata la guardia).

### I60 · El obrero repone lo que el PLANO recuerda (y el constructor es el aldeano SIN FAENA)

El jugador: *"la aldea ha tenido daños en su muralla y nadie ha ido a repararlo. El recolector está de flojo y así
ha estado durante todo el día"*.

**Medido** en su guardado (`build/obras_pendientes.py`, nuevo): de las **7.296** celdas del plano de la aldea 2
**una** estaba pendiente —un farol de la taberna que el propio pueblo retira en cada latido—, o sea que **ningún**
agujero de la muralla estaba apuntado. El obrero recorre **el plano** (`findRepairTarget`): **lo que no está en el
plano no existe para el pueblo** (I8, la misma lección de I50 con el hueco de la pared de una casa). Y el plano de
una aldea **migrada** es un **escaneo** del mundo: si la muralla ya estaba dañada cuando se capturó, esos agujeros
quedan fuera **para siempre**.

**Y el constructor no existía como tal**: `puedeSerObrero` **excluía al holgazán** (`NITWIT`, el **recolector**),
que es justo el aldeano **sin faena** al que se le pone la reparación a prioridad **3** (por delante de todo); con él
fuera, los tres obreros eran aldeanos **con oficio**, que reparan a prioridad **5** —la última—. El propio mod dice lo
contrario en `VillageStorage`: *"el constructor —que también es recolector—"*.

**Regla:**
1. **La muralla entra en el plano; si no está, se rehace.** La **migración 67** reconstruye el muro
   (`rehacerMuro`, que ya corría en el bloque de migración) y **tira el plano** para volver a capturarlo con la
   muralla **entera**: a partir de ahí el obrero mantiene lo que se rompa. Es lo mismo que ya se hacía con las casas
   (I50) y con la granja.
2. **El constructor del pueblo es el aldeano SIN FAENA** (el recolector/holgazán): puede ser obrero y se le elige **el
   primero** (su reparación va a prioridad 3, por delante de todo). Barre el suelo cuando **no hay obra**; mientras la
   haya, repara. (Al marcarlo como obrero se le quita la recogida, como a cualquier constructor sin faena.)
3. **Lo que el pueblo RETIRA sale del plano.** El farol de encima del primer escalón de la taberna (que el latido
   quita para poder subir) era **la única celda pendiente** de la aldea 2: el plano lo pedía, el obrero lo reponía y
   el reparador lo volvía a quitar, **cada 10 s** —el log lo cantaba toda la sesión con
   `quitado el farol de encima del primer escalon (1442, 123, 1439)`—. Ahora esa celda sale del plano (la misma regla
   que el farol del portón, I54): `quitarElFarolDeLaEscalera` **devuelve la celda** y el latido la borra del plano.

**Límite conocido (dicho a propósito)**: el obrero trabaja en la banda `REPAIR_MAX_UP`/`REPAIR_MAX_DOWN` (**+5/−6**
sobre la cota), así que **los tejados quedan fuera**: medido en la aldea 0, **27** losas de la placa del tejado de la
taberna (a **+11**) pendientes y sin nadie que las reponga. Subir a un tejado es otra obra, no una reparación de
planta.

**Se comprueba contra el guardado** con `build/obras_pendientes.py` (cuántas celdas del plano están pendientes, de
qué bloque, a qué distancia y a qué altura sobre la cota) y en el log (el tira y afloja del farol de la escalera
**desaparece**, y el obrero firma sus bloques con `Repuso …`).

### I61 · Un golem no aparece (ni se queda) en un piso de arriba

El jugador: *"los golems no deben spawnear en el 3er piso"*.

**Medido** en su guardado: de los **5** golems de hierro de las tres aldeas, **uno** estaba en
`(1446.8,131,1430.4)` de la aldea 2 → **+11** sobre la cota (`120`) = **el desván de la taberna** (el 3.er piso), y
al lado del aldeano que lo había sumado (Onofre, el cocinero, dormía en `(1442,131,1432)`, a **4** bloques).

**Por qué.** El golem de hierro de vanilla lo **suma un aldeano** cuando da el aviso de alarma, y lo hace **donde
está él**: si el aldeano está en la **posada** o en el **desván**, el golem sale **dentro de la casa** —no defiende
el pueblo y se queda atrapado arriba—. Y el golem del **mod** (`VillageGenerator.spawnIronGolem`) se coloca con
`spawnY` → `groundY`, que en una columna con una construcción devuelve **su tejado**.

**Regla:** un golem que entra al mundo **dentro de una aldea y por encima del suelo** se **baja a una casilla libre
a la cota**, al lado de la plaza (suelo firme y **seco**, dos celdas libres: nunca al agua ni dentro de un bloque).
Se hace en la **entrada al mundo** —el mismo sitio donde el sello corta los spawns, así que vale para las **dos**
vías (vanilla y mod)— y el **latido** lo repite para los golems que **ya** estaban en alto (el del guardado se baja
en la primera pasada, sin esperar a que se muera). Es **idempotente**: a un golem a nivel del suelo no se le toca.

**Se comprueba contra el guardado** (el `dy` de cada golem sobre la cota de su aldea: tiene que ser **0**) y en el
log (`un golem aparecio N bloque(s) por encima del suelo (dentro de un edificio): se le baja a ...`).

### I62 · La milicia aprende matando (y su tope es el del zombie más fuerte)

Lo pidió el jugador, después de una noche en la que *"aparecieron así de la nada zombies agresivos que mataron a
media aldea"*: *"los guardias se van haciendo más fuertes y con más salud conforme van matando enemigos, el tope es
prácticamente tan fuerte como el zombie agresivo más fuerte que puede generarse después de aplicarse todas las
reglas (máxima distancia desde el centro, tiempo en el mundo, etc.). La progresión es gradual, no tiene que ser tan
rápida"*.

**El tope no se escribe a mano: se calcula del perfil del zombie** (`AggressiveZombieSpawnProfile`), con la misma
cuenta que usa su escalado: `baseHealth * (1 + maxScaleMultiplier) * (1 + ThreatLevel.maxExtraDifficulty())`. Con
los valores de hoy (10 de vida, 0,7 de daño, +300 % de escala y +80 % de amenaza) sale **72 de vida y 5,04 de daño**
—por eso, si un día se toca el perfil, el tope de la milicia se mueve con él—.

**La progresión** (medida con `build/diag/tope_guardia.py`): 24 matanzas para el tope, un **nivel cada 3** (el nivel
se ve en su etiqueta: `Guardia espadachín · nv 3`):

| matanzas | nivel | vida | daño (atributo) | con la espada de hierro del pueblo |
|---|---|---|---|---|
| 0 | 1 | 20,0 | 2,00 | 6,00 |
| 3 | 2 | 26,5 | 2,38 | 6,38 |
| 6 | 3 | 33,0 | 2,76 | 6,76 |
| 12 | 5 | 46,0 | 3,52 | 7,52 |
| **24** | **9** | **72,0** | **5,04** | **9,04** |

**Regla:** los atributos se **recalculan** desde el contador de matanzas (`aplicarLoAprendido`), nunca se acumulan:
es lo que hace que el latido —que lo llama para cada guardia alistado— sea idempotente y que un guardia recargado
del guardado recupere lo suyo. Cuando la vida máxima sube, se le suma a la **actual** lo mismo que subió la máxima
(se fortalece sin curarse del todo: sigue con las heridas de la pelea). Cuenta la muerte de un {@code Monster}
cuando el **dueño del daño** es un aldeano de la guardia ({@code LivingDeathEvent} + {@code getSource().getEntity()},
así que también valen los **arqueros** con la flecha).

> **MEDIDO CON EL ARNÉS (modo `MEDIR_MILICIA`), y de ahí salió UN BUG GORDO**: la guardia subía de nivel pero su
> log salía con **`dano 0.0`** ✗ — y **`Villager` NO tiene el atributo de daño** (vanilla no lo necesita: los
> aldeanos no atacan), así que `getAttribute(ATTACK_DAMAGE)` era **null**, mi código no le ponía nada y **cada golpe
> de un guardia hacía CERO de daño**: la milicia era **decorativa** (medido: la guardia con la etiqueta *"Atacando"*
> y el zombi **sin morirse**). Arreglado en `InitModEventSubscriber.updateEntityAttributes`
> (`event.add(EntityType.VILLAGER, ATTACK_DAMAGE, 2.0)`), que es el evento de NeoForge para **añadir** atributos a
> un tipo existente sin pisarle los suyos.
>
> Con el arreglo, la corrida del arnés (aldea 2, guardia herida al 35 % y cuatro zombis flojos a su lado) da:
> `sube al nivel 2: 3 enemigo(s) → vida 26.5 y dano 2.38` · `sube al nivel 3: 6 enemigo(s) → vida 33.0 y dano 2.76`
> · `GUARDIA Genoveva nv=3 matanzas=6 vida=20.0/33.0 | etiqueta: … · nv 3 / Atacando` — es decir, **pelea con su IA,
> mata, sube de nivel y su etiqueta lo dice**, y la vida cuadra con la tabla de arriba (20,0 al acabar = los 7,0 de
> la herida + lo que le subió la máxima en 6 matanzas: **se fortalece sin curarse del todo**, que es la regla).

### I63 · El sello, para CADA bicho (no solo para el primero del grupo)

Medido en la partida del jugador (aldea 2): en el log, el sello **rechazó 9 de 9 anclas** de spawn
(`posicion … dentro de aldea protegida: no se spawnea`)… y en el guardado del jugador **no hay ni un monstruo dentro
del recinto**: los de alrededor están **todos fuera**, a 62-74 bloques del centro. O sea que la barrera **sí** corta
los spawns de dentro. Pero había un agujero real: `CustomSpawner` miraba el sello **solo para el ancla** y las demás
posiciones del grupo **se sorteaban otra vez** (`findSpawnPosition`) sin comprobación, así que un bicho del grupo
podía aparecer dentro aunque el ancla estuviera fuera.

**Regla:** el sello se comprueba para **cada** posición de spawn. Y la altura se mide con la **cota de la aldea**
(I12), no con `level.getSeaLevel()` con un margen de 96: con el nivel del mar, una cueva veinte bloques por debajo
de la plaza y una loma treinta por encima contaban como "dentro de la aldea" (el sello cortaba spawns que no eran de
la aldea y no distinguía bien el suelo del pueblo).

> **OJO, y esto es lo que de verdad pasó esa noche**: el sello **no levanta un muro**. Los bichos que se acumulan
> fuera (el guardado del jugador tiene una docena de esqueletos y zombis a 63-73 bloques, pegados a la muralla)
> **entran andando** por donde pueden y, si dentro no hay quien los pare, matan. La barrera impide que **aparezcan**
> dentro; de **defender** se encarga la **milicia** (I62) y el muro con sus portones.

### I64 · El clérigo es el SANADOR de la aldea

Lo pidió el jugador: *"el clérigo podría tener como task el curar a los soldados; que sea una especie de sanador"*.

**Regla:** el clérigo (además de hacer pociones en su soporte) **busca heridos y los cura**. En orden:
1. **un soldado herido** (la guardia es la que se pelea con los bichos) y, si no hay ninguno, **un vecino**;
2. se considera herido el que está **por debajo del 75 %** de su vida máxima (un arañazo no lo levanta de la silla),
   y siempre dentro de **32 bloques** (el término del pueblo);
3. va a por él **por el cerebro** (I5: `caminarHacia`), se planta a **2,5 bloques**, hace su faena (medio segundo) y
   le devuelve **8 de vida** con **corazones** (`ParticleTypes.HEART`) y el sonido de su oficio; deja el aviso en el
   log (`El clerigo cura a X (a -> b de N de vida)`) y en su etiqueta (`Curando a Quintin`);
4. después descansa **8 s** (`TICKS_ENTRE_CURACIONES`) antes de volver a curar: es un sanador, no una máquina.

**Cuándo**: la ronda del sanador **manda sobre el soporte y sobre el viaje al agua**, y de noche también sale **si la
aldea está en asalto** (que es cuando los guardias se hieren): lo que **no** hace es quedarse despierto por gusto —sin
asalto, de noche duerme como los demás (I28/I46)—. Su paciencia para llegar al herido es la del puesto (6 s, I33) y
si no llega lo deja y sigue con lo suyo.

**Lo que NO hace**: no le da pociones a la guardia (un aldeano no bebe). El clérigo **sana él**; las pociones que
prepara siguen siendo para el jugador y para el almacén (visión nocturna, veneno, arrojadizas).

### I65 · Un oficio se cuenta por TITULAR, no por tipo

Lo cazó el jugador en su log: **cada 10 s**, sin parar,

```
[Village] Aldea 2: aldeano sin oficio recupera el puesto de farmer
[Village] Aldea 2: f033ee63-… tenia el oficio de farmer de mas (el pueblo tiene 3 plaza(s)): se queda sin oficio…
```

y el mismo aldeano bailando entre el bancal y la milicia (`se alista en la guardia como espadachín` / `deja la
guardia y vuelve a su oficio`).

**La causa, de una línea**: `reponerProfesiones` apuntaba **un oficio por TIPO** (`!presentes.contains(profesion)`)
pero `slotDeProfesionFaltante` **gasta una plaza por cada VIVO** con ese oficio (el pueblo tiene **tres** granjeros):
con la lista de tipos, el primer bancal cubría "farmer" y los otros dos parecían **libres** → daba de alta un
granjero **de más** en cada latido → y `podarOficiosDuplicados`, que **sí** cuenta titulares, se lo quitaba acto
seguido. El pueblo se pasaba el día contratando y despidiendo al mismo granjero.

**Regla:** la lista que se le pasa a `slotDeProfesionFaltante` lleva **una entrada por aldeano con ese oficio** (es un
recuento, no un conjunto). **Medido con el arnés** (modo noche, 4 min sobre su partida): `recupera el puesto de` =
**0** y `tenia el oficio de … de mas` = **0** (antes salían ~24 de cada uno).

### I66 · El puesto del relevo nocturno se puede SALTAR

En el mismo log, el guardia `f033ee63` se quedaba en bucle: pasos **12, 13, 14, 15 y 16** seguidos con **el mismo**
`BlockPos{x=1354, y=120, z=1414}` y `atascado 200 ticks` por vuelta (y `no llego a … me salto el puesto` sin servir
de nada).

**La causa**: de noche el puesto sale del **reloj** (`(gameTime / RELEVO_TICKS + indice) % 4`, las cuatro puertas) y
**no** de `paso`, así que «saltarse el puesto» (`paso++`) no cambiaba el destino.

**Regla**: el puesto al que un guardia **no llega** se apunta con `marcarPuntoFallido` (I33) y, de noche, el relevo
**pasa a la siguiente puerta** (`puestoDeLaPuerta`, recorriendo las cuatro y saltándose las fallidas). Y el aviso
`nuevo puesto` **solo se canta cuando el puesto CAMBIA** (con el aviso en cada `start()` salían diez líneas por
segundo repitiendo el mismo sitio: medido con el arnés en modo noche).

### I67 · A la guardia no se le quita la cama por no ir a dormir

Tercera cosa del log del jugador: `SIN CAMA Genoveva de 16 aldeanos (camas del pueblo: 29)` y, acto seguido,
`los 16 aldeanos (crias incluidas) tienen cama` — un latido sí y otro no. Y los nombres eran **guardias**
(Genoveva, Prudencio).

**La causa**: `acostarAlQueNoLlega` da la cama por perdida si el aldeano no se **acerca** a ella en 6 latidos (I57),
y de noche el guardia está **de servicio** (ronda o puerta, I28): no se acerca a su cama porque no le toca, así que
se le quitaba y el reparto se la volvía a dar.

**Regla:** a un aldeano **de la guardia** no se le aplica el «renuncio a la cama» — conserva la suya para cuando le
toque descansar de verdad. **Medido con el arnés** (modo noche, 4 min sobre su partida): `no consigue llegar a su
cama` = **0**, el WARN `Aldea 2: SIN CAMA` = **0**, y el resumen acaba en
`aldeanos=14 conCama=14 COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=13`.

### I68 · El trigo de criar NO se hornea (el ganadero tiene que poder criar)

Pregunta del jugador: *"en el establo, cuando sacrifican una vaca, ¿sí sale cuero también? porque veo que casi no se
ha fabricado armaduras de cuero"*.

**El cuero SÍ sale**: el sacrificio es un `hurt` de verdad con el aldeano como atacante
(`VillagerAnimalFarmGoal.sacrificar` → `presa.hurt(damageSources().mobAttack(villager), MAX)`), o sea el **loot normal
de vanilla**, y el ganadero **recoge los drops** y los baja al almacén.

**Lo que pasaba es que no había nada que sacrificar**, y está medido en su guardado:
- el corral tiene **3 vacas** (el tope es **6**), así que **no hay exceso** y el ganadero **no sacrifica**;
- y la cría de vacas/ovejas se hace con **trigo** (`hayComidaParaCriar` pide **2** en la despensa y gasta 1 por
  animal)… y la despensa tenía **0 de trigo** (sí 432 zanahorias, 155 patatas, 261 semillas).
- **El pan se comía el trigo**: el granjero horneaba todo el trigo según llegaba (`Guardo 8 y horneo 2 pan(es)` en su
  log), así que nunca quedaban 2. (Desde el cambio que pidió el jugador —*"el cocimiento de los panes no lo debería
  hacer el granjero sino el COCINERO"*— quien hornea es el **cocinero**, en el ahumador de la taberna, y el granjero
  **solo deja el trigo** en la despensa; ver **I103**.)

Sin cría no hay exceso → sin exceso no hay sacrificio → sin sacrificio no hay cuero → sin cuero no hay armaduras.

**Regla:** el horneado **deja siempre `RESERVA_DE_TRIGO_PARA_CRIAR` (4) de trigo** en la despensa; el ganadero
siempre encuentra con qué criar. La constante vive en `VillagePantry` (donde está el horno, o sea el cocinero) y la
respeta `VillagerCookGoal.hornear`, que es quien hornea. Las raciones no lo tocan: el pan, la carne y las verduras van
**antes** que el trigo (`VillagePantry.repartirRaciones`, el trigo es el último recurso).

### I69 · El rebaño se queda en DOS por raza (la pareja), y lo que sobra al sacrificio

Lo pidió el jugador con captura del corral lleno de puercos: *"hay demasiados puercos, el ganadero tiene que
sacrificar, que queden 2 por raza"*.

**Medido en su guardado**: el corral tenía **7 puercos**, 3 vacas, 3 ovejas y 8 gallinas, y el almacén **sin cuero**.
El tope era **6** (y las gallinas 8), así que el corral se llenaba de animales que no daban nada.

**Regla**: el tope de **vacas, ovejas y puercos es 2 (la pareja)**: lo que sobra va al sacrificio
(`elegirSacrificio` ya elige la especie con más sobra y **nunca baja de la pareja**). Y la cría es **hasta el tope**,
no por debajo: con 2 animales, exigir "estar por debajo del tope" dejaría al rebaño clavado en 2 y sin crías que
sacrificar —ni carne, ni cuero, ni lana—. El ciclo queda **2 → 3 (cría) → sacrificio → 2**, así que la pareja se
queda siempre y cada cría acaba en carne.

**Las gallinas siguen con tope 8**: no se crían para carne, sino por los **huevos**. Si el jugador las quiere
también a dos, es cambiar `MAX_GALLINAS`.

### I70 · El huevo estrellado (lo cocina el cocinero, y lo come el pueblo)

Lo pidió el jugador: *"implementa que el cocinero cocine los huevos para hacer huevos estrellados, y puedan consumir
todos"*, con el icono que trajo (una imagen 16x16 ampliada; el fondo ya venía **transparente**, así que solo hubo que
reducirla a 16x16 con nearest).

**Cómo queda**:
- <b>Item</b> `devilrpg:huevo_estrellado` (comida: 6 de nutrición, 0,6 de saturación — como un pollo asado), con su
  <b>textura</b>, su <b>modelo</b>, sus <b>nombres</b> (inglés y español) y su hueco en la pestaña creativa.
- <b>Lo cocina el cocinero</b> en su ahumador: `Items.EGG` entra en su lista de crudos (`VillagerCookGoal.CRUDAS`) y
  `VillagePantry.cocinar` lo convierte en huevo estrellado, con su humo, su sonido y su "Cocino N piezas" —
  exactamente igual que la carne.
- <b>Y lo come el pueblo</b>: es comida **de la despensa** (`perteneceALaDespensa`) y cuenta como **cocinada**
  (4 puntos, `FOOD_PER_COOKED_MEAT`), así que entra en el contador de comida y en el reparto de raciones.
- <b>Y el jugador también</b>: se lo puede comer, y cocinarlo él en un ahumador (receta
  `data/devilrpg/recipe/huevo_estrellado.json`: huevo → huevo estrellado, 5 s).

**De dónde salen los huevos**: de las **gallinas del corral** (el recolector barre los huevos y los sube al almacén, y
de ahí pasan a la despensa). Con el tope de las gallinas en 8 (I69) el pueblo tiene huevos de sobra.

### I71 · Las semillas de sobra van a la COMPOSTA (no se apilan en la despensa)

Lo vio el jugador: *"en la despensa se están acumulando demasiadas semillas; lo ideal es que 2/3 partes las ocupen los
mismos granjeros para hacer composta y acelerar el proceso de cosecha"* (y dejó para después la idea de una sopa de
semillas con cuenco del cocinero).

**MEDIDO en su guardado**: la despensa tenía **261 semillas de trigo y 425 de betabel** (686) y los granjeros echaban
al compostero **16 por viaje** (`COMPOSTAR_MAX`). El grifo (el **recolector**, que barre cada semilla que sueltan las
cosechas) estaba abierto y el desagüe tapado: el montón solo podía crecer.

**Regla**: cada granjero se lleva **64 semillas** por viaje al compostero (`COMPOSTAR_MAX`) en vez de 16, y la despensa
solo guarda **`SEMILLAS_SOBRANTES_EN_DESPENSA` (32)** como **reserva de siembra** (los tres bancales necesitan ~27):
todo lo que pase de ahí acaba en **harina de huesos**, que es lo que abona el plantío y acelera la cosecha. Con tres
granjeros y 64 por viaje, el montón baja de verdad (cada visita saca varias harinas de hueso).

### I72 · El granjero manda sobre el militar, y cosechar manda sobre la rotación

Dos cosas del jugador con captura (3 parcelas, campos llenos de trigo maduro y solo 2 granjeros): *"hay 3 parcelas y
solo dos granjeros, ¿porque todavía no se ha designado un granjero? recuerda que tiene prioridad el granjero que el
militar a la hora de asignar. Y también están tardando mucho en cosechar, hay campos llenos"*.

**1) El guardia ocupaba una plaza de oficio.** `alistarGuardia` conservaba su oficio (y con él el **ticket de su
estación**), así que una plaza de granjero quedaba "cubierta" por un guardia que **no pisaba el bancal**: el reparto
veía 3 granjeros, el **tercer bancal se quedaba sin nadie** y el aldeano nuevo no podía reclamar la estación (el
ticket era del guardia). Arreglado: al alistarse, si su oficio es del pueblo se le **suelta la estación** y se queda
**sin oficio** (su plaza es para un granjero de verdad; si deja la guardia, el reparto le da otra), y
`reponerProfesiones` **no cuenta a los guardias** como titulares.

**2) La cosecha esperaba su turno.** Las tres faenas (cosechar / labrar / sembrar) **rotaban siempre**, así que con
los bancales llenos solo se cosechaba **uno de cada tres turnos**: el trigo se pasaba de maduro y el campo se veía
"lleno". Ahora **cosechar manda**: si hay cultivo maduro se cosecha ya; la rotación queda para labrar y sembrar, que
es cuando el bancal no tiene nada maduro (la mitad del ciclo de un campo sano).

### I73 · Los guardias no se quedan plantados en el almacén, y tienen TURNO

Lo reportó el jugador: *"¿por qué los guardias están yendo al almacén, se equipan y se quedan ahí parados sin hacer
nada? deberían estar patrullando, o turnándose para comer, o descanso, porque también necesitan descansar, o
entrenando en la sala de entrenamiento de sus barracas, pero turnados para que no dejen desprotegida la aldea. Si es
uno nada más pues sí puede tomarse sus tiempos, ni modo"*.

**1) El plantón en el almacén.** La casilla de apoyo del almacén se **aparca** cuando no se alcanza
(`marcarPuntoFallido`, I33)… pero el guardia seguía con `equipando = true` y el destino puesto en ella: se quedaba
**parado al lado** esperando una pieza que no podía recoger (en su log: `no consigue llegar a 1461,121,1434: lo deja
por 5 min` y el guardia clavado). **Arreglado**: si la casilla está aparcada, no va — se queda de **ronda** sin la
pieza que le falte y lo reintenta cuando el aparcamiento caduque (5 min).

**2) El turno de descanso.** El goal de la guardia corre **de seguido** (también de noche, I28), así que ningún otro
goal —taberna (comer), cama (dormir), barraca— llegaba a correr: el guardia no comía ni descansaba nunca. Ahora hay
**turnos**: de cada `TICKS_DE_SERVICIO` (90 s) de servicio, cada guardia se toma **uno**
(`(gameTime / TICKS_DE_SERVICIO) % guardias == su número`), así que **nunca se ausentan dos a la vez**; al cortarse el
goal mandan los demás (come en la taberna, duerme en su cama, entrena en la barraca) y vuelve solo al servicio. Con un
**solo** guardia el turno también le toca (el jugador lo acepta: *"ni modo"*). **Con un enemigo a la vista o la aldea
en asalto no hay descanso**: primero se pelea.

### I74 · El guardia ENTRENA en la barraca (y eso también le hace más fuerte, despacio)

Lo pidió el jugador tras I73: *"o entrenando en la sala de entrenamiento de sus barracas… si impleméntalo"*.

**Cómo funciona**: en sus turnos de descanso (I73), los **pares** entrena y los **impares** descansa de verdad (come en la
taberna o duerme), así que sigue habiendo relevos y la aldea no se queda sola. Entrenando: se va a la **diana** de la
barraca (`VillageGenerator.puestoDeEntrenamiento`, la celda de delante del `TARGET` del rincón suroeste), se pone
frente a ella, pega **cada 2 s** (con sonido y su etiqueta `Entrenando en la barraca`) y **suma progreso**:
`VillageManager.sumarEntrenamiento` apunta los ticks y **cada 5 minutos de diana cuentan como una matanza** para el
nivel y los atributos (I62). O sea: 24 matanzas para el tope = **dos horas de entrenamiento**, así que la milicia se
hace de verdad en las peleas y entrenar es un extra, no un atajo.

**Con asalto o con un enemigo a la vista no se entrena**: primero se pelea. Y si el guardia no encuentra la diana, sigue
con la ronda en vez de quedarse parado.

### I75 · El guardia no encadena viajes al almacén (el escudo que falta)

Con captura: *"Segismunda, se ve ciclada tratando de ir al almacén"*.

**MEDIDO en su guardado**: el almacén tenía **3 espadas de hierro, 3 arcos, 30 flechas… y 0 ESCUDOS**. Y un espadachín
solo está "equipado" con **espada de hierro + ESCUDO** (`VillagerGuardGoal.equipado`), así que iba, no lo encontraba
(el herrero forja el escudo en su turno, I55) y **volvía a intentarlo**: la etiqueta "Yendo al almacén" puesta en
bucle, y con la casilla aparcada (`esPuntoFallido`) o sin aparcar, el resultado era el mismo.

**Regla**: entre viaje y viaje al almacén hay **`TICKS_ENTRE_VIAJES` (2 min)**. Si el viaje no completa el equipo, el
guardia **vuelve a la ronda** (sin la pieza que le falte) en vez de encadenar viajes: la aldea no se queda sin
guardia y el escudo llega cuando el herrero lo forja.

### I76 · El herrero forja LO QUE MÁS FALTA (reparto uniforme de armas y armaduras)

Lo pidió el jugador: *"lo que quiero es que siempre haya una distribución uniforme de armas y armaduras disponibles,
es decir que los herreros evalúen viendo el almacén qué es lo que falta más y lo construyan, y así siempre estén
evaluando"*.

**Antes**: las recetas se elegían por **orden fijo** (espada → escudo → arco → flechas; casco → peto → grebas → botas)
con un tope por pieza. Con las espadas ya al tope, el herrero **no miraba el escudo**: MEDIDO en su guardado, el
almacén tenía **3 espadas y 0 escudos** mientras los guardias esperaban el escudo para completar su equipo (I75).

**Ahora**: cada vez que va a fabricar, el herrero **evalúa el almacén** y calcula el **hueco de cada pieza**
(`OBJETIVO - lo que hay`, contando hierro **y** cuero juntos en la armadura) y forja **la que más falta** de las que
puede hacer con el material que hay (`elQueMasFalta`). A igualdad de hueco gana la primera, así que el reparto es
**estable** entre latidos y no baila. Sigue evaluando en cada pieza, así que la distribución se mantiene sola.

### I77 · Los huevos llegan a la despensa, el wisp deja en paz a los gatos, y las crías crecen al triple

Tres cosas del jugador de una: *"todavía no veo cocinado ningún huevo estrellado y los huevos están en el almacén. El
wisp de distancia ataca a los gatos y no debería. Los niños deben crecer más rápido para suplir a los aldeanos
muertos en la noche"*.

**1) EL HUEVO NO LLEGABA A LA DESPENSA.** El ganadero deja los huevos del corral en el **almacén**, y el **cocinero**
saca lo crudo de la **despensa**: el puente es el **granjero**, y su lista de lo que se trae del almacén
(`VillagerFarmGoal`, junto a `perteneceALaDespensa`) **no tenía el huevo**, así que se quedaba en el almacén y el
cocinero no lo veía nunca (el huevo estrellado de I70 era, de hecho, imposible de cocinar). Añadido el huevo crudo a
las dos listas: el granjero lo trae y se queda en la despensa (no se reparte como ración: no es comida, es la materia
prima del cocinero, como el trigo).

**2) EL WISP Y LOS GATOS.** Su objetivo excluía a los "animales" con una lista (`esAnimal`) en la que el **gato** no
entra —es un `TamableAnimal`—, así que el wisp de distancia los perseguía. Ahora el `Cat` y el `Ocelot` están
excluidos **aparte**, en el propio predicado del objetivo (`SoulWispArcher.registerGoals`).

**3) LAS CRÍAS CRECEN AL TRIPLE.** `ageVillagers` solo miraba la **vejez**; las crías crecían a velocidad vanilla
(20 min). Ahora, mientras son crías, se les envejece un extra por latido
(`BABY_GROWTH_SPEEDUP = 3`), así que son adultas en **unos 7 minutos** y una noche mala se repone en un par de días.

### I78 · La milicia alterna espadachines y arqueros, y el entrenamiento se rinde si no llega

Dos cosas del jugador: *"se quedó ciclado un guardia al ir a entrenar"* y *"tampoco he visto ningún arquero; al crearse
deberían alternarse"*.

**1) EL ARQUERO.** `repartirGuardia` daba los **cuatro primeros** puestos a espadachines (`i < MILICIA_ESPADACHINES`)
y el resto a arqueros, así que con una milicia de dos o tres **no había ni un arquero** (y el jugador, con captura, no
veía ninguno). Ahora **se alternan** (`i % 2`): con la milicia llena salen los **4 espadachines y 3 arqueros** de
siempre —la formación de la marcha a la guarida— y con **dos** guardias ya hay **uno de cada**.

**2) EL CICLO AL ENTRENAR.** El viaje a la diana no tenía la disciplina de I3/I33 que sí tiene el resto de goals: si
la diana no era alcanzable (el guardia se quedaba empujando la pared de la barraca), seguía intentándolo con la
etiqueta "Yendo a entrenar" **para siempre**. Ahora, si no se acerca en `STUCK_LIMIT`, **se rinde**, **aparca la
diana** (`marcarPuntoFallido`) y vuelve a la ronda: entrenará cuando la diana sea alcanzable (log:
`no llego a la diana …: me vuelvo a la ronda`).

### I79 · El hongo y el liquen solo dañan a los ENEMIGOS (o a quien ataca)

Lo pidió el jugador: *"modifica el poder del hongo y el liquen para que no dañe a las entidades neutrales ni al
jugador, solo a los enemigos o aquellos que ataquen"*.

**El LIQUEN** (`SoulLichenBlock.applySoulLichenEffects`) hería a **cualquier** cosa que no fuera un minion del dueño
(la condición era `!(entity instanceof ITamableEntity && suDueño == owner)`): los animales de la granja, las mascotas
de otros… **y el propio jugador**. Ahora solo entra si es un **enemigo** (`Enemy`, los hostiles —incluidos los del
mod—) o alguien **atacando** (un `Mob` con `getTarget() != null`), y nunca el jugador ni los minions del dueño.

**El HONGO** (`ExplodingSporeBullet.explodeCreeper`) explotaba con `ExplosionInteraction.MOB`, que daña a **todo** lo
que pille el radio: los animales del corral, las mascotas y el dueño. Ahora la explosión va con `NONE` (**solo ruido y
partículas**) y el daño se reparte **a mano** por el radio, **solo a los enemigos** (mismo criterio) y sin tocar al
jugador.

### I80 · El puesto del corral también respeta el aparcamiento (el guardia ciclado patrullando)

Con captura: *"el espadachín se quedó como ciclado patrullando el corral"*.

`puestoDelCorral` era el **único** punto de la ronda que **no** miraba `esPuntoFallido`: cuando el guardia se rendía en
él (`no llego a …: me salto el puesto`, I33) el aparcamiento se apuntaba… y la vuelta siguiente **volvía a dárselo**,
así que se pasaba el día yendo, atascándose 10 s y volviendo a empezar, con la etiqueta "Patrullando el corral"
clavada.

**Regla**: si el puesto del corral está aparcado, el guardia **pasa al punto siguiente de la ronda** (`paso++`); el
aparcamiento caduca a los 5 minutos, así que volverá a probar el corral —y si sigue sin poder, lo aparcará otra vez,
sin bucle de 10 s—. Es el mismo remedio que ya llevaban el relevo nocturno (I66) y el viaje al almacén (I73/I75).

### I81 · Lo que el jugador (o un poder suyo) ataca, lo atacan sus MINIONS

Lo pidió el jugador: *"si bien ninguno de los minions ni plantas atacan a los neutrales, si yo, jugador, llego a
atacar alguno, o si alguno de los poderes atacan (como la enfermedad que genera el hongo y el liquen cuando se avienta
a alguna entidad), esta se vuelve enemigo y se debe atacar por los minions"*.

Los minions con objetivo propio (el <b>hongo</b> `ExplodingSporeBullet`, el <b>oso</b> `SoulBear` y el <b>wisp
arquero</b> `SoulWispArcher`) filtraban con `!esCriaturaPacificaONeutral(entity)` **a secas**: el neutral quedaba
descartado antes de mirar si el dueño estaba en la pelea (el arquero lo tenía a medias, con su propia lista de
animales).

**Regla**: el predicado pasa a ser
`(!esCriaturaPacificaONeutral(entity) || ITamableEntity.elDuenoLeEstaAtacando(this.getOwner(), entity)) && …` — es
decir, un neutral **sí** cuenta si el dueño ya le ha pegado. El ayudante es **compartido** (`ITamableEntity`) y mira
`owner.getLastHurtMob()` y `entity.getLastHurtByMob()`, así que valen **las manos y los poderes**: el daño del hongo y
del liquen va con el jugador como atacante (`playerAttack(owner)` / `explosion(…, owner)`, ver I79), así que el bicho
queda marcado igual y los minions van a por él.

**Barrido — esto falló DOS veces, y las dos por listas a mano.** La primera versión se aplicó a una lista escrita a
mano de tres minions (hongo, oso, wisp) y dejó **fuera al LOBO**; lo cazó el jugador: *"disparo un soul lichen a una
oveja fuera de la aldea, y empieza a recibir daño por la maldición, pero mis lobos no la atacan"*. El lobo entró en el
commit siguiente, junto con la pieza que faltaba: el liquen daña **por efecto, no por golpe**, así que
`LichenSeedBall.onHitEntity` tiene que marcar a mano `dueno.setLastHurtMob(livingEntity)` — sin eso el dueño "nunca le
pegó" y el predicado no tenía nada que ver. Y al preguntar el jugador *"el oso también, ¿lo checaste?"*, el barrido
**con `grep`** (no a mano) encontró **dos sitios más** con el predicado crudo en `SunflowerShulker`.

**Regla**: esta regla **no se aplica con listas a mano**. Se barre el mod entero:

```
grep -rn 'esCriaturaPacificaONeutral(' src/main/java
```

y cada acierto que sea un **predicado** (no la definición del ayudante en `ITamableEntity`, ni un bloque comentado)
tiene que llevar la coletilla del dueño. Barrido del 12-sep-2026: `ExplodingSporeBullet:122`, `SoulBear:152`,
`SoulWispArcher:88`, `SoulWolf:87`, `SunflowerShulker:717` — **todos con el permiso**; los dos "sin permiso" que
quedan son la **definición** (`ITamableEntity:142`) y un bloque **comentado** (`SunflowerShulker:169`).

**Ojo con `this` dentro de un `super(…)`**: en `ShulkerDefenseAttackGoal` (clase **estática** anidada) el lambda va
como argumento del `super`, así que ahí **no existe `this`** —el compilador suelta *"cannot reference this before
supertype constructor has been called"*— y hay que usar el parámetro del constructor (`p_33496_.getOwner()`), igual
que ya hacía la línea del `getOwnerUUID`.

### I82 · El liquen sin dueño no hace nada (crash de NullPointerException, arreglado)

**Crash medido** (con el guardado del jugador, al meterse un `Sunflower Shulker` en un liquen):

```
java.lang.NullPointerException: Cannot invoke "net.minecraft.world.entity.player.Player.getUUID()"
        because "owner" is null
    at SoulLichenBlock.applySoulLichenEffects(SoulLichenBlock.java:96)
    at SoulLichenBlock.entityInside(SoulLichenBlock.java:321)
```

**Causa**: lo introdujo el arreglo de I79 al comprobar "¿es un minion del dueño?" con `owner.getUUID()` **sin
comprobar `owner`**, y `applySoulLichenEffects` se llama con `owner` **null** (el liquen sin dueño, o el que pisa un
minion de otro jugador). **Regla**: sin dueño, el liquen **no hace nada** —no se puede saber quién es minion y quién
no—: una guarda al principio (`if (owner == null) return;`), mejor eso que tirar el servidor. Revisado el resto de
usos de `owner` de esa zona: los demás van dentro de un `instanceof Player owner` o ya comprobaban null.

### I83 · El cocinero fríe los huevos aunque la despensa esté llena

El jugador: *"no veo que el cocinero haga huevos estrellados"*. Y en su log se ve **por qué**: el granjero es el
**puente** que lleva la comida del almacén a la despensa, y cuando la despensa va **llena de verdura y semillas**
(medido antes: 432 zanahorias, 155 patatas, 261 semillas) los huevos **no le caben** (`despensaNoTraga`), así que el
cocinero nunca los veía.

**Regla**: si en la despensa no había nada que cocinar, el cocinero **fríe los huevos del ALMACÉN** (que es de donde
salen: los deja ahí el ganadero) y deja las tortillas **en el almacén**, de donde el granjero las sube a la despensa
como cualquier comida (y por eso su lista de acarreo incluye ya `esHuevoEstrellado`). Así el huevo estrellado sale
aunque la despensa esté a rebosar.

**Nota del log del jugador** (19:19–19:48, ya con todo dentro): el cocinero **Hipolito murió de viejo** a los 3 días
justo en esa sesión (`Un aldeano murio de viejo… Hipolito (Cocinero) died` y después `repuesto el puesto de butcher`),
así que además de lo de la despensa llena hay que darle tiempo al **cocinero nuevo**. Y se ve funcionando lo demás:
el herrero por hueco (`Hizo 4 flechas`), la milicia alternando (`se alista… espadachin` + `cambia de puesto… arquero`)
y el sello rechazando spawns.

### I84 · Los huevos fritos a la DESPENSA, más abono, y la tierra se atiende cada dos cosechas

Tres cosas del jugador: *"no, los huevos fritos se pueden quedar en la despensa, pues es su lugar para guardar. Más
bien haz que los granjeros saquen las semillas para que usen como composta y que fertilicen más, y que recojan más
cosecha porque todavía hay campos que no se ocupan al 100%"*.

**1) LAS TORTILLAS, A LA DESPENSA.** En I83 el cocinero dejaba los huevos estrellados en el **almacén**; el jugador
lo corrigió: la despensa es **su sitio**. Ahora del almacén solo sale el huevo **crudo** (que es donde lo deja el
ganadero) y el huevo estrellado se guarda **en la despensa**, con la comida del pueblo.

**2) MÁS ABONO.** La harina de huesos que se lleva por viaje sube de **16 a 64** (`HARINA_MAX`) y las plantas que
abona por salida, de **32 a 64** (`ABONAR_MAX`): el compostero (que ya se llena con 64 semillas por viaje, I71) se
convierte en abono de verdad y el plantío se abona a fondo, que es lo que acelera la cosecha.

**3) LA TIERRA, CADA DOS COSECHAS.** El jugador ve **celdas sin sembrar**. La causa era I72: al mandar la cosecha
siempre, el paso de **sembrar/labrar** no llegaba a correr y las celdas que se quedan vacías (alguien las pisa, o las
cosecha el juego sin replantar) seguían vacías. Ahora el granjero cosecha **dos veces** y a la tercera atiende la
tierra (`COSECHAS_POR_TIERRA = 2`): se cosecha rápido **y** se siembra lo que falta.

### I85 · La despensa son TODOS los cofres de la cocina (no solo el suyo)

El jugador corrigió mi diagnóstico: *"¿por qué dices que la despensa está llena si no está llena? Está a la mitad, es
un cofre doble, lo debería reconocer el cocinero. Además dentro de la cocina hay otro cofre que no se ocupa y también
debería poder ocuparlo, así como todos los demás"*. **Tiene razón en las dos cosas**:

- El cofre doble **sí** se leía entero (`ChestBlock.getContainer(…, true)` une las dos mitades): el problema no era
  capacidad, era que `despensa()` **devolvía el PRIMER contenedor que encontraba y ya** (`return c`), así que **el
  otro cofre de la cocina** —el del jugador— quedaba **invisible** para el pueblo: ni el cocinero sacaba de él, ni el
  granjero guardaba ahí.
- Y mi "la despensa está llena" era una **suposición** mal fundada: lo correcto era medirlo, y el jugador lo ha
  desmentido.

**Regla**: `VillagePantry.despensa` **une todos los contenedores de la cocina** (`CompoundContainer`, encadenado): el
cofre de la casa (sus dos mitades), el cofre viejo del kiosco si toca migrar, y **todo lo demás que haya en el radio
de la cocina**. Se recorren por posiciones y **se apunta también la otra mitad** de cada cofre doble
(`ChestBlock.getConnectedDirection`) para no contar el mismo contenedor dos veces. Así el cocinero cocina con lo que
haya en cualquiera de ellos y guarda donde quepa — y las raciones y el granjero ven la despensa completa.

**De paso**: los huevos estrellados vuelven a guardarse en la **despensa** (I84 consideró lleno un cofre que no lo
estaba; con la unión, el sitio deja de ser un problema).

### I86 · Una aldea NO cae con el jugador lejos: el asedio se PAUSA (no se pierde)

El jugador, con una aldea entera perdida: *"Me alejé de la aldea unos cientos de cubos, volando y regresé antes de que
nocheciera y cuando regresé ya estaba abandonada. Eso es un bug enorme!!"*, y precisó el mecanismo: *"cuando la aldea
está marcada como que fue invadida por zombis la primera vez que se llega, cambia a abandonada: todos los aldeanos
mueren y las construcciones quedan destruidas con telarañas. El bug aquí es que yo me alejé de la aldea y cuando
regresé se disparó esta función de aldea abandonada cuando no tendría que haber pasado"*.

**Medido en su guardado** (21-sep-2026, 21:02): `Fallen = [1]`, y la aldea **1** (990,990, cota 96) cayó el
**17-sep a las 18:50:09** —`Aldea 1 queda en ruinas: 1483 bloques cambiados` + `La aldea 1 ha CAÍDO y queda en
ruinas`—, con el chat del asedio **clásico** (*"La aldea cayó… El objetivo avanza."*). La última posición del jugador
al cerrar el juego es **(975, 110, 997)**: dentro de esa aldea en ruinas. Su aldea viva (la 2) está entera:
`health=18`, `food=64`, **18 aldeanos contados uno a uno en el guardado** (el mismo número que su `Health`), y esa
sesión **no** tiene ni una caída ni una muerte. La aldea caída (la 1) conserva **9 aldeanos vivos**: `ruin()` no
mata a nadie —el pueblo quedó escrito como caído **con su gente dentro**, y esos aldeanos se quedan sin pueblo que
los gestione—.

**Causa**: `VillageManager.tick` hacía `d.tickTicks++` **siempre**, sin mirar dónde estaba el jugador. Al irse con los
asediadores **dentro** del muro, el reloj (`GRACE_TICKS + SIEGE_TIMEOUT_TICKS` = 3 min 30 s) seguía corriendo; los
zombis, descargados, no morían; y **en el mismo tick de volver** se cargaban otra vez, volvían a contar como "dentro
del perímetro" (`allZombiesInsidePerimeter`) y el asedio se resolvía como **perdido**: `fallVillage` → `ruin()`
(aire, telarañas, piedra mohosa y ladrillo agrietado: exactamente las tres cosas que describe el jugador).

**Regla**: el reloj del asedio **solo corre con el jugador en la aldea** (`RADIO_ASEDIO_CON_JUGADOR` = 128 bloques del
centro). Si se va —o se desconecta— el asedio queda **EN PAUSA** (se dice en el log, con la distancia) y **al volver
se le da el tiempo entero otra vez**: ni el margen ya gastado ni el minuto final que corrió sin él. Las **hordas del
mundo** tampoco pueden tumbar una aldea sin nadie delante (`hayJugadorEnLaAldea`): la horda se queda donde está y, si
el jugador vuelve, la pelea sigue. **Un asedio es una pelea: sin el jugador delante no puede perderse.**

**Sin verificar en vivo** (el jugador tenía el juego abierto y lo cerró al enviar el informe): la prueba que falta es
dejarse asediar, irse a más de 128 bloques con los zombis dentro y volver — tiene que salir `EN PAUSA` y `el jugador
ha vuelto al asedio` en el log, y la aldea seguir en pie.

**La línea de tiempo, del log de ese día** (17-sep-2026), que es lo que cierra el caso:

```
18:46:25  [CHAT] Llegaste a la aldea... los monstruos se acercan.    <- arranca el asedio (start)
18:48:09  [CHAT] ¡Defiende la aldea de los monstruos!                <- sale la ola (GRACE_TICKS = 90 s)
18:50:09  [Village] La aldea 1 ha CAÍDO y queda en ruinas            <- 210 s despues de las 18:46:25
```

Los 210 s son **exactos**: `GRACE_TICKS` (90 s) + `SIEGE_TIMEOUT_TICKS` (120 s) contados desde que el jugador llegó;
los 14 s de más sobre el minuto teórico los explica el propio log (`Can't keep up! ... 229 ticks behind`). O sea:
llegó, arrancó el asedio, **se fue volando**, y el reloj le cobró la aldea a la espalda. El jugador lo contó como
*"cuando salí de la aldea no había ningún tipo de asedio"* y tiene razón **en lo que veía**: la ola sale **fuera de
la valla** (a 60-100 bloques del centro), así que yéndose nada más llegar no se ve un solo zombi, y la barra de
acción que informa del asedio (`informarDelAsedio`: *"quedan N y M DENTRO del muro · 1:23"*) no pudo avisarle porque
la caída se resolvió **en el mismo tick de volver**. Con el reloj en pausa, al volver se le da el tiempo entero y esa
barra sí puede avisarle.

**OJO al leer estos logs** (me costó un diagnóstico): el log del jugador y el del **arnés** (`gradlew runServer`,
mundo `world`) escriben los dos en `run/logs/latest.log`, así que un archivo puede **mezclar dos mundos y dos
procesos** (`New World (1)` y `world`) con las líneas desordenadas. Hay que fiarse de las **marcas de tiempo** y del
**nombre del almacén** (`ThreadedAnvilChunkStorage (…)`), nunca del orden ni de la sesión aparente.

### I87 · Las aldeas tienen NOMBRE, y su dirección no se regala

Lo pidió el jugador, y con un motivo muy práctico detrás (*"el problema es que no tengo las coordenadas para poder
regresar"*):

> *"Estaría bien que la piedra de invocación [dé] un libro o algo que vaya guardando las aldeas descubiertas (sólo las
> que uno ya haya entrado) junto con su estatus y sus coordenadas. También ya es necesario que cada aldea tenga su
> nombre (respetando el lore) y que arriba donde está la barra de objetivos, que no diga objetivo 1, 2 etc, sino aldea,
> y cuando se descubra que diga su nombre. Ahora hay una mecánica que hay que desarrollar y es que la siguiente aldea
> no va a aparecer su dirección hasta que uno obtenga algo del mundo o alguien de la primera aldea o piedra de
> invocación al inicio de esa misión… pero primero sin nombre y ya después con nombre cuando se descubra. Si una aldea
> vence el asedio inicial, el clérigo puede activar la dirección del siguiente objetivo. Si cae la aldea, tal vez que
> no aparezca en la barra de objetivos, y que solo salga un mensaje en el chat indicando su dirección sin decir cuántos
> bloques está y ya. Si la encuentra pues ya se actualiza su libro y la barra de objetivos (que ahora se llamará la
> barra de aldea)."*

**Reglas** (lo que quedó implementado):

1. **NOMBRE determinista** ({@code VillageNames.nombre(i)}): la aldea 0 es el primer nombre de la tabla, la 1 el
   segundo… Igual en servidor y cliente **sin sincronizar nada** (misma idea que las coordenadas de
   {@code ObjectiveTargets}). La tabla **la escribió el jugador**: 30 nombres con su lore, agrupados en tres bloques
   (aldeas del bosque → espíritus y animales → misteriosas y oscuras) y **en ese mismo orden**, que encaja con la
   escalada del mod (cuanto más lejos del círculo ritual, más podrida está la tierra): las primeras son verdes y las
   últimas son ceniza y niebla. Si la partida pasa de la tabla, se repiten con numeral (*Valleverde II*), nunca dos
   iguales.
2. **La barra de ALDEA** (antes "de objetivos", {@code VillageHudOverlay} + {@code "aldea"} como capa) tiene **tres
   estados**: <b>sin revelar → no hay barra</b>; <b>revelada y no visitada → {@code Aldea  (1.234 m) →}</b> (dirección
   sí, nombre no); <b>visitada → {@code Aldea de Valdehierro  (12 m) ↑}</b>.
3. **Descubrir = ENTRAR** (radio de llegada, {@code ARRIVE_RADIUS} = 24), no verla de lejos: al entrar se apunta en la
   capability (`aldeasVisitadas`, por jugador y sincronizada), sale el aviso *"Has llegado a …"* y queda con nombre en
   la barra y en el Diario.
4. **Ganar un asedio NO cambia de objetivo: la barra se queda en la aldea salvada, con su nombre.** Al vencer se
   anuncia el nombre (*"Has salvado Aldea de Valleverde…"*) y se pone al día el Diario, pero el objetivo **no avanza**,
   así que arriba se sigue leyendo **la aldea que acabas de salvar con su nombre** (`Aldea de Valleverde  (0 m) ↑`).
5. **El que pasa a la siguiente es el CLÉRIGO**: al **hablarle** (clic derecho) *después* de salvar el pueblo, el
   objetivo **avanza** a la siguiente aldea y el clérigo **revela su dirección y su distancia** — y la barra la enseña
   **sin nombre** (`Aldea  (1.234 m) →`), porque el nombre solo llega al **entrar** en ella. Vale cualquier clérigo de
   cualquier pueblo del mod. Lo pidió el jugador, palabra por palabra: *"una vez ganado el asedio APAREZCA en la barra
   de aldea el nombre de la aldea actual recién ganada y sólo cuando vaya con el clérigo cambie al siguiente objetivo
   que es la siguiente aldea y su distancia sin revelar aún el nombre"*.
   *(La **piedra de invocación** sigue revelando el objetivo actual: es el "inicio de la misión" y el seguro contra
   perderse. Antes, el clérigo revelaba solo al ganar y el objetivo avanzaba solo; las dos cosas se cambiaron.)*
6. **Si la aldea CAE no se revela nada y el objetivo sí avanza** (esa aldea ya no se puede salvar): ni barra ni
   distancia, solo el aviso en el chat con el **rumbo** (*"…hay otra aldea hacia el noreste… y no sabe cuánto queda"*),
   y el Diario se actualiza solo cuando la encuentre y entre. Es literalmente lo que pidió el jugador.
6. **Diario del Invocado, un LIBRO de verdad** (`DiarioDelInvocado`): lo entrega la piedra (y lo vuelve a dar/actualizar
   si lo tiene). **No es un objeto del mod: es un libro escrito de los del juego**, así que al abrirlo se abre la
   **interfaz de libro** normal, con su título (*Diario del Invocado*), su autor (*Los clérigos*) y sus páginas. Lo
   pidió el jugador: *"debe ser un libro que pueda leer… no que cuando le dé click aparezca en el chat lo que dice; eso
   no se ve natural"*. Las páginas se **reescriben** al abrirlo (y al salvar una aldea) con lo que el jugador sabe
   **ahora**: por aldea descubierta, **nombre, coordenadas, estado** (viva / en asedio / a salvo con el sello / EN
   RUINAS) y **a cuántos metros y hacia dónde** cae. Se reconoce por una **marca en sus datos**, no por el nombre (así
   renombrarlo no rompe nada), y el contenido sale de un solo sitio: `VillageManager.estadoDeLaAldea`.
7. **Siembra en partidas ya empezadas**: una aldea que el mundo ya dio por resuelta es una aldea en la que el jugador
   estuvo, así que se le apunta como visitada y revelada **una sola vez** (en cuanto tiene una apuntada, no se vuelve a
   mirar). Sin esto, su partida de siempre nacería con el Diario vacío y la barra sin saber hacia dónde ir.
8. **El estado de la aldea, en un solo sitio**: `VillageManager.estadoDeLaAldea(level, índice)` devuelve
   `EN RUINAS` / `en asedio` / `a salvo, con el sello puesto` / `viva, sin socorrer` (ese orden: una caída manda, y el
   asedio se mira antes que el sello). Lo usan el **Diario** y el **arnés**, así que no hay dos copias de la regla que
   se desincronicen. Y la **barra** dibuja lo que decide `VillageBarText` (compartido, para que el arnés mida el mismo
   texto que se ve), no una copia dentro del HUD.

**Lo que hay que recordar al tocar esto**: el descubrimiento y el revelado son **por jugador** y van en la capability
auxiliar (se sincronizan al cliente, que es quien dibuja la barra); el **estado** de la aldea sigue siendo del mundo
({@code VillageSavedData}), así que el Diario lo lee en el servidor al usarse. Y una aldea caída **no** revela la
siguiente a propósito: el camino de salida es la piedra (el viaje al círculo ritual), que nunca deja al jugador sin
dirección.

**Medido con el arnés** (`MEDIR_ALDEAS`, dos corridas el 22-sep-2026 sobre una **copia** de su partida; las líneas
crudas están en `tools/arnes/medidas-aldeas.txt`):

```
[Village] Diario del Invocado sembrado para [Minecraft]: 3 aldea(s) que ya resolvio esta partida
[Arnes] ALDEAS indice=0 visitadas=[0, 1, 2] reveladas=[0, 1, 2]
[Arnes] BARRA DE ALDEA (aldea 0): con NOMBRE: "Aldea de Valdehierro"
[Arnes] BARRA DE ALDEA (aldea 1): con NOMBRE: "Aldea de Fuenteclara"
[Arnes] BARRA DE ALDEA (aldea 3): OCULTA (ni direccion ni nombre: hay que leer la piedra o ganar un asedio)
[Arnes] DIARIO: Aldea de Fuenteclara  (990, 990) — EN RUINAS · a 599 m hacia el noreste
[Arnes] DIARIO: Aldea de Robledal  (1414, 1414) — a salvo, con el sello puesto · a 0 m hacia el sur
```

Es decir: el guardado viejo **carga sin una sola excepción**, la **siembra** apunta las tres aldeas que esa partida ya
resolvió, la **aldea que no está en el guardado sale OCULTA** en la barra (el caso que pidió el jugador: la que viene
después de una que cayó) y el **Diario** lista nombre, coordenadas, estado (la caída, EN RUINAS) y rumbo.

**Los dos revelados y los tres textos de la barra, TAMBIÉN medidos** (3.ª corrida del arnés, llamando a los **mismos
métodos** que corren en juego):

```
[Arnes] BARRA oculta      -> null
[Arnes] BARRA revelada    -> Aldea  (1234 m) ->
[Arnes] BARRA descubierta -> Aldea de Peñasalbas  (12 m) arriba
[Arnes] CLERIGO antes:   revelada(3)=false
[Arnes] CLERIGO despues: revelada(3)=true barra="Aldea  (1234 m) ->"
[Arnes] CLERIGO otra vez: revelada(3)=true (idempotente)
[Arnes] PIEDRA antes:    revelada(5)=false
[LoreStone] [Minecraft]: revelada la aldea 5 (hacia el suroeste)
[Arnes] PIEDRA despues:  revelada(5)=true barra="Aldea  (1234 m) ->"
```

Y esa medida **cazó un fallo de rastreo**: la piedra **no revelaba nada** con un jugador sin ancla (el de pega del
arnés) y se iba **en silencio**; ahora deja un `WARN` (`[LoreStone] <jugador> leyo la piedra pero NO tiene ancla ni
spawn: …`). En una partida de verdad el ancla la pone el mod al entrar al mundo.

**Pendiente**: la **tabla de nombres** definitiva (la pasa el jugador) y, en el juego abierto, la **barra dibujada**
(es del cliente: lo medido es el texto que decide `VillageBarText`, no el píxel) y el **clic** en la piedra (se mide el
método que corre el clic). El camino "NBT del jugador anfitrión de una partida vieja" tampoco se ha medido en caliente:
en un servidor dedicado no hay jugador de verdad (queda cubierto por construcción: `getIntArray` de una clave ausente
devuelve vacío y los conjuntos se vacían antes de llenarse).

**El asedio de principio a fin NO se puede medir headless** (intentado, 22-sep-2026): el reloj del asedio solo corre
con el jugador **en la lista del servidor** (I86) y un `FakePlayer` **no está en ella**, así que queda EN PAUSA y la ola
nunca sale (`hayAsedio(3)=true`, `agresivos=0`, `revelada(4)=false` en toda la corrida). Lo que **sí** dejó medido ese
intento: el cuarto estado, **`en asedio`**, y que el **Diario lo enseña** (`Aldea de Peñasalbas (1838, 1838) — en
asedio · a 599 m hacia el suroeste`). Ver al clérigo revelar **en el momento de la victoria** hay que jugarlo.

### I88 · La etiqueta (nombre y oficio) es SOLO de los aldeanos del MOD

El jugador: *"Estoy viendo que las villas normales (vanilla) tienen a sus aldeanos con las mismas etiquetas que la
villa de mi mod. No deberían de tener etiqueta de nombre y profesión. Eso sólo es para los aldeanos del mod"*.

**Causa**: `VillageManager.refrescarEtiquetas` —el refresco genérico, cada segundo desde el tick del jugador— recorría
**todos** los aldeanos a menos de 64 bloques y les ponía la etiqueta del mod con `nombreDe` + `nombreDeOficio`. Y
`nombreDe` **cae al nombre del UUID** cuando el aldeano no tiene uno asignado (para que ningún aldeano, ni una cría
recién nacida, salga sin nombre), así que a un aldeano de vanilla no le faltaba nada: bastaba con pasar cerca para que
el mod lo bautizara y le pusiera "Nombre (Oficio)" encima. Se etiquetaba por **cercanía**, no por **pertenencia**.

**Regla**: la etiqueta es un distintivo del **pueblo del mod**, y para eso hay una marca: `DEL_PUEBLO_TAG`
(`DevilRpgDelPueblo`), que pone **el latido** al adoptar a los aldeanos de la aldea (`repartirNombres`, que es a la vez
quien les reparte nombre propio). Sin la marca:

- **no** se les pone etiqueta —`etiqueta()` y `refrescarEtiquetas` la saltan, así que ni nombre, ni oficio, ni
  actividad—, y
- si el mod se la había puesto **antes** (partidas viejas, con la regla de cercanía), **se le quita**… pero **solo si la
  puso el mod** (`ACTIVIDAD_TAG`): un nombre puesto por el jugador con una etiqueta de nombre **no se toca**.

**Medido con el arnés** (22-sep-2026), quitando y devolviendo la marca a un aldeano de la aldea 2:

```
[Arnes] ETIQUETAS aldea 2: 17 aldeano(s), 17 del pueblo (marcados), 17 con etiqueta
[Arnes] ETIQUETAS ejemplo ANTES:        delPueblo=true  etiqueta="Bibiana (Guardia arquero / nv 1) / Patrullando el corral"
[Arnes] ETIQUETAS ejemplo SIN la marca: delPueblo=false etiqueta="(sin etiqueta)"
[Arnes] ETIQUETAS ejemplo CON la marca: delPueblo=true  etiqueta="Bibiana (Guardia arquero / nv 1) / De guardia"
```

**Consecuencia a tener en cuenta**: la marca la pone el latido, que corre cada 10 s para las aldeas a menos de 140
bloques, así que un aldeano recién llegado a un pueblo del mod puede estar unos segundos **sin** etiqueta. Es
preferible eso a etiquetar a quien no es del pueblo.

### I89 · La MURALLA sí se rompe; lo de dentro de la aldea no (y el campo de fuerza es de la aldea YA GANADA)

El jugador, viendo un asedio parado en la puerta: *"no entiendo por qué los zombies del asedio inicial no entran a la
aldea, ¿no tratan de llegar al centro? ¿no rompen la barda para entrar?"*. **Tenían razón: no podían.** Dos reglas se
sumaban:

- `MoveToVillageCenterGoal` (la marcha al centro) **se apaga en cuanto el asediador tiene un objetivo** al que atacar
  (`getTarget() != null → false`), y dentro de una aldea siempre hay algo a la vista (guardias, aldeanos, el golem), así
  que se quedaban plantados fuera.
- Y romper, no rompían **nada**: `dentroDeLaAldea` vetaba picar en todo el disco de `FENCE_RADIUS + 2` (64) y **la
  muralla está en el radio de la valla (62)**. El comentario del código lo decía: *"El muro, las casas, la huerta y el
  kiosco están todos dentro de ese disco"*.

**Regla**: el veto protege **la obra del pueblo** (casas, plaza, huerta, kiosco), pero hay una **banda** —el anillo de
la valla, `MURALLA_ANCHO` = 5 bloques hacia dentro y hacia fuera, y de la cota hacia arriba (`MURALLA_ALTO` = 6), nunca
hacia abajo— donde **sí se pica**: es la **brecha** por la que entra un asedio. La usan los **dos** caminos que rompen
bloques (`breakBlockTowards` de la marcha y `BreakBlockGoal`, el que se abre paso hacia un objetivo), con un solo
ayudante (`protegidoPorLaAldea`), porque **el veto estaba al revés**: de más en la marcha (la muralla) y **de menos** en
`BreakBlockGoal`, que **no comprobaba nada** y podía picar dentro del pueblo.

Lo que cierra el círculo: la muralla rota **la repara el obrero** (está en el plano, I50/3b.72), así que un asedio
deja huella y el pueblo la levanta otra vez.

**CORREGIDO (22-sep-2026, lo aclaró el jugador)**: *"Los zombies en el asedio inicial, cuando se llega a la aldea, SÍ
pueden romper todo lo necesario; pero cuando se gana el asedio la aldea genera un campo de fuerza que no permite spawneo
de ningún enemigo dentro, y tampoco les permite ROMPER NADA. Posteriores asaltos: se spawnean fuera de la aldea y van a
intentar entrar buscando el centro, pueden romper bloques y crear escaleras de bloques para llegar, pero una vez
entrando ya no pueden romper nada"*.

**La regla queda así** (`AggressiveZombieEntity.protegidoPorLaAldea`, con el nuevo `elAsedioYaSeGano`):

- **Asedio sin resolver** (el **inicial**, el que el jugador tiene que ganar): la aldea **no** tiene campo de fuerza →
  se rompe **lo que haga falta**, por dentro y por fuera, para llegar al centro y a los aldeanos.
- **Aldea ya ganada** (`VillageSavedData.isSiegeResolved`, que es lo que enciende el campo): dentro **no se rompe nada**;
  la **muralla** (el anillo de la valla) y todo lo de **fuera** siguen rompibles, para que los asaltos posteriores
  puedan entrar y hacerse escaleras — pero una vez dentro no tocan nada.

La otra mitad del campo (`expulsarHostilesDeLaAldea`, que vacía la aldea de bichos) **ya** estaba atada a
`isSiegeResolved` desde la etapa H; **la de romper no lo estaba**: la protección de dentro estaba activa **siempre**, y
por eso el asedio inicial se quedaba fuera sin poder picar. Un asaltante sin aldea asignada (`worldSiegeIndex < 0`)
tampoco tiene campo ✓.

**Medido con el arnés** (aldea 0 del jugador, que está **ganada**: `Resolved = [0]`; todo sobre una copia):

- El asaltante de la aldea **0** (ganada) picó **solo fuera de la aldea**: 3 bloques (2 de hierba y 1 tronco), todos a
  r = 66,7 (> 64) **y nada de dentro** ✓.
- Con un **cerco de piedra cerrado** (r=62, 3 de alto) el asaltante del asedio inicial se quedó **fuera** (r=61) y picó
  **2 bloques en 3 minutos**: la protección lo paraba, que es justo el fallo corregido. El cerco también enseñó que el
  anillo de su aldea tiene **huecos** (solo 7 bloques en todo el rumbo este, a la altura del suelo): sin cerco el
  asaltante entra **andando** (llegó a r=48, dentro) y lo mata la milicia.

**Dos fallos más, medidos y arreglados en la misma vuelta** (los dos dejaban el asedio parado aunque pudiera picar):

- El detector de atasco de `BreakBlockGoal` medía **la distancia al objetivo**: mientras el zombie **rodea** el muro esa
  distancia sigue bajando poco a poco, así que **no se disparaba nunca** (medido: rodeó un muro de piedra de 15 bloques
  y no picó nada en 3 minutos). Ahora se mide **por dónde está el zombie** (¿se ha movido 0,5 bloques?), en el goal que
  se abre paso hacia el objetivo **y** en el de la marcha al centro (que además exigía `getNavigation().isDone()`, y con
  una ruta viva que no lleva a ninguna parte tampoco picaba).
- El candidato a picar exigía `isSolid()` y **la valla no es un cubo**: el asaltante se quedaba de bruces contra la
  **valla de roble del bancal** sin picarla. Ahora se pregunta por la **forma de colisión** (no aire y con colisión).

**Y una línea de log nueva** —`[Siege] un asaltante de la aldea N pica X en Y`—: sin ella no se podía distinguir "no
pica" de "pica y el pueblo lo repone", y es lo que ha hecho medible todo esto.

**Y la otra mitad, ya medida** (segunda corrida del arnés, con el pueblo **sin aldeanos ni golems** —es una copia— para
que el asaltante no se fuera detrás de un vecino: medido, con aldeanos dentro se iba detrás de uno y llegaba a r=67 del
centro, **fuera** del pueblo, sin acercarse siquiera al cerco). Se le cierra al jugador de pega un **anillo de piedra**
(radio 7, 3 de alto) con él **dentro**, y se ponen dos asaltantes, uno de cada clase:

| asaltante | aldea | bloques picados |
|---|---|---|
| **asedio inicial** | `worldSiegeIndex = -1` (sin aldea → **sin** campo de fuerza) | **21** |
| **asalto posterior** | `worldSiegeIndex = 0` (aldea del jugador, **GANADA**) | **0** |

Los 21 del primero fueron: **8 `stone_bricks`** (el anillo de prueba), **4 `smooth_quartz`**, **4 `dark_oak_log`**,
**2 `bricks`**, **2 `oak_log`** y **1 `dark_oak_door`** — o sea, además del cerco, **la obra del pueblo por dentro**
(una casa, con su puerta): es exactamente el *"pueden romper todo lo necesario"* del asedio inicial. El de la aldea
ganada **no picó ni uno** ✓. Y la línea de log los distingue: `un asaltante de la aldea -1 pica …`.

**Pendiente**: el comportamiento **en juego** (el arnés mide asaltantes puestos a mano, no una oleada de verdad: el
reloj del asedio necesita un jugador real, ver I86), que lo verá el jugador.

### I90 · El cráter de un creeper también se tapa: el AGUJERO DEL SUELO

El jugador, con una captura de un hoyo **dentro** de la aldea y el recolector al lado: *"hay un hoyo que dejó un creeper
durante el asedio, ¿por qué nadie lo está reparando? Ahí está Leoncio el recolector, él debería de ser también
constructor"*.

**Por qué nadie lo reparaba**: la reparación va **por el plano** (`VillageGenerator.captureBlueprint`) y el cráter está en
**terreno natural**, que el generador no apunta. Medido en su partida: `OBRAS PENDIENTES: 0` —el plano **completo**— con
el hoyo abierto a la vista. El obrero daba la aldea por terminada.

**Regla**: el obrero repone, además del plano, las **celdas del suelo que faltan** dentro del recinto (radio de la valla,
62): una celda de **aire** por debajo de **la capa de tránsito**, con **suelo de aldea justo debajo** (`dirt`, `grass`,
`coarse_dirt`, `podzol`, `rooted_dirt`, `mud`, `gravel`, `sand`, `stone`, andesita, diorita, granito, `dirt_path`,
`farmland`) y hasta `AGUJERO_MAX_PROFUNDIDAD` = 4 hacia abajo. Se tapa **de abajo arriba** (cada celda tapada deja suelo
debajo de la de encima) con **hierba en la capa de arriba** (`cota - 1`) y **tierra** por debajo. Nunca se pisa una celda
que ya tenga bloque: si el jugador puso algo ahí, se queda.

Dos fallos medidos en el primer intento (los dos en el **mismo** sitio: la cota es la Y del **aire** sobre el suelo,
`VillageGenerator.groundY` devuelve `suelo + 1`):

- **El agujero era el propio aldeano.** La banda admitía `dy` hasta **+1**, así que el aire donde el aldeano tiene los
  **pies** —que tiene suelo debajo, la hierba— contaba como agujero. Medido con el arnés (`MEDIR_AGUJERO`, aldea 2 de la
  copia): los **tres** obreros devolvían `veObjetivo` = **su propia `blockPosition`**, o sea que su meta era poner un
  bloque **donde estaban de pie**. Ahora la banda es `cota - 1` hacia abajo, nunca la capa de arriba.
- **La hierba iba a la capa de abajo.** El bloque de la capa de arriba es `cota - 1`, no la cota: el cráter pedía
  `minecraft:dirt` en la celda del **césped** del suelo de la aldea. Ahora `cota - 1` es `grass_block` y por debajo,
  `dirt`.

**Medido de punta a punta** (misma corrida, cráter de 3x3x2 abierto por el arnés en `(1421, 120..119, 1421)` de la copia,
cota 120): `findRepairTarget` → `BlockPos{x=1421, y=119, z=1421}`; el cráter queda `... ... ...` en la capa de tránsito
y **`GGG GGG GGG` en `cota - 1`** (hierba, sin escalón) entre t=600 y t=800; y al final `veObjetivo=null` (la aldea vuelve
a estar completa). Lo tapó **Anselmo (Recolector)** con la etiqueta *"Reparando la aldea"* → *"Repuso tierra"* → otra vez
*"Recogiendo"*. Ver I91: el que lo tapó es el recolector **porque** su reparación va a prioridad 3.

**Alcance y límites (lo que esto NO distingue)**: por bloques, un cráter de creeper y un agujero **cavado por el
jugador** (o un hoyo natural) son lo mismo, así que la regla los tapa **todos**. No es un desastre de tapizado: el
terreno llano de la aldea es de radio 64 (`LEVEL_RADIUS = FENCE_RADIUS + 2`), el **talud** empieza en 64 (fuera del 62 que
mira la regla) y el generador **sella el suelo** al construir (`sellarSuelo`), así que una aldea sana no tiene casi nada
que la regla encuentre; y la búsqueda solo se paga cuando el plano **no** tiene nada pendiente.

**No medido**: el cráter concreto de la captura del jugador (celda a celda) y cuántos agujeros del suelo tiene una aldea
suya. Se mide con el arnés en modo `MEDIR_AGUJERO` (ver `tools/arnes/LEEME.md`).

### I91 · El RECOLECTOR es el constructor: su reparación va a prioridad 3

El jugador, en la misma captura: *"Ahí está Leoncio el recolector, él debería de ser también constructor"*. **Ya estaba
marcado como obrero** (medido: `Leoncio (Recolector)`, `Remigio (Clérigo)` y `Hortensia (Leñador)` marcados en su aldea)
y aun así **no reparaba**. El motivo era una **contradicción entre el comentario y el código**:

- `NITWIT` (el holgazán = el **recolector**) **sí** está en `VILLAGER_SPECIALTIES`: ocupa una **plaza del pueblo** y hay que
  reponerlo si falta. Por eso `VillageGenerator.esOficioDelPueblo(NITWIT)` devuelve **`true`**.
- Pero las dos preguntas de *"¿tiene faena?"* —la de `marcarObrero` (la prioridad) y la del orden de `vigilarObreros`
  (quién es obrero primero)— se hacían con `esOficioDelPueblo`. Resultado: al recolector **no** se le trataba como al
  aldeano sin faena, sino como a un oficio más → reparación a **prioridad 5** (la última, por detrás de su propio goal de
  recoger, 5, y del oficio de los demás, 4) y **nunca** entraba en la pasada 0 del reparto. Los comentarios de las dos
  funciones decían justo lo contrario (*"el holgazán/recolector… su reparación va a prioridad 3, por delante de todo"*).

**Medido (arnés, `MEDIR_AGUJERO`)**: el recolector tenía la marca de obrero y `veObjetivo` apuntando al cráter, pero en
las **tres** muestras (cada 10 s) sus goals activos eran `[5:VillagerCollectGoal* 2:VillagerGateGoal* …]`:
`VillagerRepairGoal` **no aparecía**. Es el goal selector de siempre: un goal de prioridad 5 no se evalúa mientras uno de
prioridad 4 tiene la bandera `MOVE`.

**Arreglo**: `VillageGenerator.tieneFaenaPropia(oficio)` = `esOficioDelPueblo(oficio) && oficio != NITWIT`, y las **dos**
preguntas de faena la usan. `esOficioDelPueblo` se queda como está en los otros cuatro usos (el reparto de puestos, la
reposición de profesiones), donde el recolector **sí** es un puesto del pueblo. Medido después: `3:VillagerRepairGoal`
entre sus goals, y **él** es quien tapa el cráter (I90).

### I92 · El leñador no sale de la aldea mientras su arboleda no esté poblada

El jugador, con la captura de **Hortensia (Leñador) "Yendo al arbol"**: *"todavía el leñador quiere ir afuera de la
aldea. Si el bosque dentro de la aldea no tiene todavía árboles que vaya al almacén por polvo de hueso a fertilizar el
árbol. El ir afuera es el último de los recursos"*.

**Medido en su partida** (aldea 0, `build/estado_lenador.py`, solo lectura): la **arboleda del pueblo** tenía **2 árboles
y 10 plantones** de sus doce plazas, y **fuera** había **243 árboles con la base a menos de 102 bloques**. La búsqueda
del leñador era **una sola, por distancia**, así que el árbol de fuera (a 25 bloques de él) le ganaba a los de su
arboleda (a **105**): de ahí que se fuera al monte con el bosque del pueblo a medias.

**Regla**:

- La búsqueda de árbol va <b>partida en dos</b> ({@code buscarArbol(level, dentro)}): primero <b>dentro</b> del recinto
  (la arboleda del pueblo y los árboles sueltos) y el monte de <b>fuera</b> solo <b>al final</b>, cuando dentro no queda
  nada que hacer (ni árbol, ni hueco que replantar, ni plantón que abonar, ni resto colgando, ni semillas que traer del
  almacén).
- La **arboleda del pueblo se mira siempre**, esté donde esté el aldeano: sus doce plazas se saben
  (`VillageGenerator.plantonesDeLaArboleda`), y el barrido normal es de 40 bloques **alrededor del aldeano**, así que
  una arboleda al otro lado del pueblo no se veía.
- Mientras la arboleda **no esté poblada** (menos de `ARBOLES_DE_LA_ARBOLEDA_ESTABLECIDA` = 6 de sus 12 plazas con
  árbol), el leñador **abona sus plantones**: va **andando** al **almacén** o a la **despensa** a por la harina de huesos
  (`RECOGER_HARINA`), se la lleva **en la mano** (`HARINA_POR_VIAJE` = 8) y la gasta en el plantón. Antes la cogía del
  cofre **a distancia** y solo mientras la arboleda no tuviera **ni un** árbol.

**Medido con el arnés** (`MEDIR_LENADOR`, su aldea): en **todas** las muestras el destino del leñador cae **dentro** de
la valla (`destinoDentro=true`) y la ronda es `Yendo por polvo de hueso` → `Cogio polvo de hueso (8)` → `Yendo a la
arboleda` → `Abono la arboleda` (harina del zurrón 8 → 2), con la arboleda subiendo de **2 a 3 árboles** y los plantones
bajando de 10 a 9. Con el código de antes, el **mismo** montaje daba `destinoDentro=false` (a ~110 bloques del centro).

**Dos fallos del primer intento, los dos medidos** (y por eso están escritos aquí): el abonado de la arboleda iba con el
**cooldown del barrido de claros**, que solo baja cuando `canUse` llega hasta él —y con el descanso de 120 ticks tardaba
~40 descansos (4 minutos) en volver a mirar la arboleda—; y la arboleda **no se veía** desde lejos (ver arriba).

### I93 · El polvo de hueso: lo recoge el recolector, lo muele el granjero y lo usa el leñador

El jugador: *"los granjeros tampoco nunca deben olvidar de hacer polvo de hueso además de cultivar, cosechar y entregar
vegetales"*.

**Medido en su partida**: **0 de polvo de hueso en toda la aldea** con **1 hueso** guardado y los **tres composteros a
nivel 1, 1 y 5** de 8 (ninguno había producido ni una harina). Y los **huesos no estaban** en la lista de lo que recoge
el recolector (`VillagerCollectGoal.esDelPueblo`), así que se quedaban en el suelo.

**Regla**:

- El **recolector** recoge los **huesos** (los sueltan los esqueletos que mata la milicia) y los guarda en el almacén.
- El **granjero** los **muele** en cada visita al kiosco: `VillagePantry.molerHuesos` los saca de la despensa y del
  almacén y deja el polvo de hueso en la despensa con la **receta de vanilla** (1 hueso = 3,
  `POLVO_DE_HUESO_POR_HUESO`).
- Y **no se olvida del compostero**: si la despensa está por debajo de `HARINA_MINIMA` (8) y lleva semillas de sobra, el
  compostero va **antes** que las faenas de la tierra. Era el paso que **no se alcanzaba nunca** —con los tres bancales
  (216 celdas) siempre hay algo maduro—, el mismo fallo que tuvo la siembra (ver el paso 3 de `canUse`).

**Medido con el arnés** (`MEDIR_LENADOR`): `El granjero: Hizo 27 polvo de hueso (de 9 hueso(s))` (los 8 sembrados más el
que ya tenía la aldea), con el anuncio en su etiqueta, y la harina pasando por el zurrón de los granjeros (`Abono la
huerta`) y por el del leñador.

**No medido**: el empujón del compostero (`HARINA_MINIMA`). En la corrida el pueblo tenía harina de sobra todo el rato
(el arnés se la repone para poder medir al leñador), así que esa regla no llegó a dispararse: está escrita y compilada,
pero **sin ver**.

### I94 · Los guardias revisan el almacén CADA DÍA y se ponen lo MEJOR (encantados primero)

El jugador: *"¿Por qué hay guardias que no tienen arma aun cuando en el almacén hay? Es de noche y están patrullando sin
equipo. Todos los días deben revisar una vez por lo menos el almacén y verificar si hay equipo para ellos, y si hay uno
mejor que lo cambien. Los equipos con encantamientos tienen prioridad. Los equipos viejos pueden ser reciclados por los
herreros para hacer equipo"*.

**Medido en su partida** (lectura del guardado): el **almacén** (cofre doble en rel (47,19)) tenía **1 espada de hierro,
1 escudo, 1 casco de cuero y 1 botas de cuero** para una milicia de 4-7: **solo uno** puede armarse y el resto patrulla
sin nada. Y el equipo que llega **no se reaprovecha**: el guardia armado no vuelve a mirar nunca.

**Lo que estaba mal** (`VillagerGuardGoal`):

- `equipado()` y `equipar()` pedían **`Items.IRON_SWORD` literal**: una espada de diamante, de oro, de piedra o
  **encantada** en el almacén no valía — el guardia se quedaba de brazos cruzados con el arma al lado.
- La armadura cogía **la primera** pieza del hueco, no la mejor (y el filtro del hueco se perdió al reescribirlo: un
  casco no vale de peto — corregido).
- **No había revisión diaria**: solo iba al almacén si le FALTABA equipo, así que un guardia con cuero no se cambiaba
  nunca a la de hierro.
- Y al coger la pieza se hacía `new ItemStack(s.getItem(), 1)`: **se perdían los encantamientos** (y el desgaste).

**Regla nueva**:

- **Puntuación por pieza** (`valorDeArma`, `valorDeArmadura`, `valorDeArco`, `valorDeEscudo`): material (cuero, oro,
  malla, hierro, diamante, netherita / madera, oro, piedra, hierro, diamante, netherita) **más `PRIORIDAD_ENCANTADO` =
  100 si está encantada** → *cualquier* pieza encantada gana a una sin encantar (lo pidió el jugador), y entre
  encantadas gana el material mejor.
- **Revisión diaria**: marca `DevilRpgEquipoRevisado` en el aldeano con el **día de juego** (`gameTime / 24000`); si el
  día ha cambiado, va al almacén aunque ya vaya equipado, y se apunta la revisión al llegar.
- **Se cambia solo si hay algo mejor**, se lleva la pieza **entera** (encantamientos y desgaste incluidos) y **deja la
  vieja en el almacén**, que es de donde el herrero la recicla (el reciclaje ya existía: hierro/malla → lingote, oro →
  lingote de oro, cuero viejo → cuero, con una reserva para no fundir el equipo de la milicia).
- Y el herrero **ya no funde lo encantado** (`contarChatarra`/`haySobranteChatarra`): lo encantado es de la guardia.
- El espadachín vale con **cualquier arma** (espada o hacha), no solo con la de hierro.

**MEDIDO (1ª corrida del arnés, `MEDIR_EQUIPO`, sobre una copia de su partida) — INCONCLUSA, y hay que decirlo**:
con el almacén sembrado a mano (espada de hierro normal, **espada de oro Filo V**, casco de diamante normal, **casco
de cuero Protección IV**, arco Potencia III, escudo y 32 flechas) y la revisión diaria marcada como pendiente en los
**5 guardias** de su aldea:

- Los 5 guardias estaban **sin nada** (`mano=[-] escudo=[-] casco=[-] peto=[-] grebas=[-] botas=[-] revisado=0`) ✓ el
  "antes" de la queja del jugador, medido.
- A los 30 s (t=600) el **almacén se había vaciado entero** (los 4+1 espadas, los cascos, el arco, el escudo: todo a 0)
  **pero los guardias seguían con todo vacío y su marca de revisión en 0** → o el arnés lee el equipo en el sitio
  equivocado, o lo que se llevó el material **no fueron los guardias**. No se puede afirmar ninguna de las dos cosas.

**CORRECCIÓN de lo anterior (importante, y era mi error)**: el almacén **no lo vació el mod, lo vació el ARNÉS**.
En `ticks == 600` el arnés vacía el almacén para su medida de la *remesa inicial de madera*, y `MEDIR_EQUIPO` no estaba
en la lista de modos excluidos: sembró el almacén a los 10 s y a los 30 s el propio montaje lo tiró. Arreglado (el modo
ya está excluido), y con eso la 2ª corrida es la buena.

**2ª corrida (almacén a salvo) — el fallo del guardia queda AL AIRE, sin diagnosticar**: el almacén mantiene el equipo
(espada de hierro ×4, espada de oro **Filo V**, casco de diamante, casco de cuero **Protección IV**, arco **Potencia
III**, escudo ×3) y los **5 guardias siguen sin nada y con su marca de revisión en 0** durante los 3800 ticks: no llegan
a `equipar` (que es quien marca y quien registra en el log). Y sin embargo el almacén **sí pierde** 2 espadas de hierro
(t=1400) y 1 escudo (t=2400) **sin una sola línea de "se equipo con"** → esos dos los coge **otro** camino, no el goal
del guardia (¿el alistamiento de la milicia?).

**ARREGLADO Y MEDIDO (3ª corrida del arnés)**: el equipo se revisa ahora **por CERCANÍA, desde el latido del pueblo**
(`VillagerGuardGoal.equiparSiEstaCercaDelAlmacen`, a 6 bloques o menos del almacén) y, si a un guardia le falta equipo,
el latido **le manda andando** al almacén (`caminarHacia`, sin teletransportes): así no depende de que el goal del
guardia consiga navegar, que era lo que fallaba.
<p>
Medido en su aldea (almacén sembrado a mano: espada de hierro, **espada de oro Filo V**, casco de diamante, **casco de
cuero Protección IV**, arco **Potencia III** y escudo): **los guardias se arman** —
`mano=[Iron Sword] escudo=[Shield] casco=[Iron Helmet]`, `mano=[Golden Sword(E)] escudo=[Shield] casco=[Leather
Cap(E)] botas=[Leather Boots]`, `mano=[Bow(E)]`, `casco=[Diamond Helmet]` — con la **marca de revisión en el día 2**
(`revisado=2`) y las líneas `se equipo con … : ENCANTADO/normal` en el log. Y lo importante: la **espada de oro
ENCANTADA** se la llevó un guardia **antes** que las de hierro (aunque el hierro es mejor material), que es exactamente
la prioridad que pidió el jugador ✓. El almacén se vació de equipo, que es lo que tiene que pasar: el pueblo se arma.

**Pendiente**: **1 de los 6 guardias** se quedó sin nada y sin revisar (no llegó a acercarse al almacén en la corrida:
el latido solo le manda si le falta equipo, así que con más tiempo debería armarse — hay que verlo en la corrida
larga); los **objetivos de armas y armadura del HERRERO** ("no están haciendo suficientes armaduras") siguen **sin
tocar**, que es la otra mitad de lo que pidió el jugador; y quién cogía 2 espadas y 1 escudo sin log en la corrida
anterior. El **modelo** del guardia con su equipo es del CLIENTE.

### I95 · Un suelo a la COTA es una plataforma de un bloque: o lleva ESCALÓN, o se construye a `cota - 1`

El jugador: *"el punto de apoyo del almacén (517,64,666) es inalcanzable"*.

**Medido** en su guardado (aldea 0, centro `470,646`, cota **63**; `build/almacen_mapa.py`, solo lectura): el
cobertizo del almacén tenía su suelo de **`stone_bricks` en `y=63`** —la **cota**, o sea la capa que se pisa— con el
**césped del pueblo en `y=62`**, así que la capa de tránsito del cobertizo era **64** y
`VillageStorage.puntoDeApoyo` devolvía exactamente `(517,64,666)`. Subir ahí es un escalón de **1,0** y el juego sube
**0,6 andando**: **ningún aldeano podía subir**. Lo dejaban aparcado 5 min (I33) **siete aldeanos distintos** —los dos
herreros, el cocinero, el ganadero, el leñador...— y los **seis guardias**, con la línea literal
`no consigue llegar a BlockPos{x=517, y=64, z=666}: lo deja por 5 min`. Y quedaba escrito en el propio aldeano: medido
en el ganadero **Zacarias**, `DevilRpgPuntoFallido = (517,64,666)` con `DevilRpgPuntoFallidoHasta = 80965` y el reloj
del mundo en `75127` —o sea, **aparcado en ese mismo momento**—.

**El kiosco es la otra plataforma a la cota y NO falla**, y esa diferencia es la regla: el kiosco tiene **escaleras en
sus cuatro entradas** (medido: `E` en las cuatro celdas centrales de sus lados, `y=63`), o sea que el escalón **se
sube**. Los otros dos cobertizos del pueblo —el del **corral anexo** y el **taller del leñador**— no tienen el
problema porque ponen el suelo a **`cota - 1`** y se entra **andando** (su código lo dice: *"El SUELO (cota - 1: la
capa que se pisa es la cota, I1)"*).

**Regla:** un suelo **a la cota** es una **plataforma de un bloque entero** y, si el pueblo tiene que subirse a ella,
**lleva su escalón** (como el kiosco) **o va a `cota - 1`** (como los dos cobertizos). El almacén se pasa a la segunda
convención (**migración 69**, `VillageGenerator.bajarElAlmacenAlSuelo`): el cobertizo se construye con el suelo a
`cota - 1`, el interior despejado de la cota hacia arriba, y `VillageStorage` lee **sus cofres y su punto de apoyo en
la COTA** (`pos`, `puntoDeApoyo`), no en `cota + 1`.

En las aldeas ya construidas lo **baja** un reparador, y es **idempotente** (su guardia es la capa de piedra del
cobertizo **viejo**, en la cota: si ahí no hay `stone_bricks`, no hay nada que bajar), **solo toca los bloques del
cobertizo** (piedra del suelo, troncos de los postes, tablones del tejado, el farol y sus cofres) y **saca lo de sus
cofres a la mano** antes de tirarlos (I6) para devolverlo al almacén nuevo; lo que no quepa se deja en el suelo del
cobertizo, donde lo recoge el recolector. Va **antes** de tirar el plano (I8).

### I96 · Un destino al otro lado de un PORTÓN de valla: el primer tramo es el PORTÓN

El juego **no le deja planificar el camino a un aldeano a través de una puerta de valla cerrada** (lo dice I44 y lo
mide el arnés: `createPath` devuelve `alcance=NO` con el destino al otro lado). De ahí salen dos fallos que se
sumaban, y los dos **medidos con el arnés** sobre su aldea (aldea 0, corral en `520,646`, gallinero en `512..516,
638..639`):

- **El aldeano no camina hacia el portón: se queda pegado a la valla.** El ganadero **Zacarias**, con el almacén al
  otro lado de la cerca del corral, se quedó **16 s clavado en `516,63,636`** —el rincón noroeste, **por fuera** de la
  valla— con el destino aparcado en `516,64,639`; y yendo a por un huevo del corralillo desde fuera del corral, lo
  mismo. El objetivo de su goal **sí** estaba al otro lado (por eso `vaACruzar` y el goal del portón no bastaban: el
  aldeano **nunca llegaba al portón**).
- **Y un portón que es un RODEO tampoco se abría.** El almacén está al **este** de la puerta **oeste** del corral: al
  ir del corral al almacén el aldeano **sale por el oeste y vuelve a rodear la cerca por el sur**, así que el destino
  final cae del **mismo lado del plano** de la puerta y `vaACruzar` decía que no iba a cruzar. Medido: el ganadero
  **oscilando** en `512,63,647` con `destino=511,63,646` / `destino=517,63,666` alternándose y el almacén a **19**
  bloques, sin cruzar nunca.

**Regla (dos piezas, las dos en un solo sitio):**

1. **`VillageGenerator.primerTramoDelPorton(level, center, nivel, desde, destino)`** devuelve **a qué portón hay que
   ir primero** (o `null` si no hay ninguno de por medio). Mira las cajas del anexo, **de fuera adentro**: para
   entrar en el **corralillo** desde fuera del corral, primero la **cerca grande**; para salir del corralillo,
   primero **su** portón; y solo cuenta si está **cerrado** (abierto, el camino se planifica solo). Los goals con un
   destino puede estar detrás de una cerca lo usan para **partir el viaje en piernas**, con **su propio contador de
   atasco cada una** (I38) y con el tramo del portón medido a `ALCANCE_PORTON` = 2,0 (< `ABRIR` = 2,6: al llegar ahí
   se le manda otra vez al destino real, que es lo que hace que el goal del portón se lo abra).
2. **`VillagerGateGoal.vaACruzar` también dice que sí cuando su destino ES la celda del portón**: a una puerta de
   valla solo se va para cruzarla, así que eso cubre el caso del **rodeo** (el destino final del mismo lado del
   plano). Con el portón abierto, el planificador ya traza el camino que pasa por él (y `abrir` le borra
   `WALK_TARGET`/`PATH` para que lo vuelva a pedir, I44).

**Y el portón del GALLINERO no lo bloquea el rebaño que vive dentro.** La guardia de "un animal pegado al portón" se
escribió para el portón **del corral** (el que sale fuera del recinto, I22) y se aplicaba **también** al del
gallinero, que va **de la caseta al corral** (de dentro adentro) y donde las gallinas **viven**: medido en su
guardado, **5 de las 8 gallinas** estaban a menos de 2,5 del portón del gallinero, así que no se abría. Para el
gallinero la guardia es el **hueco** (1,5: el animal tiene que estar **en la puerta**). Y la espera de "no para
siempre" pasa de **600** (30 s) a **120** (6 s) porque **tiene que ser más corta que la paciencia del aldeano que la
necesita** (160 ticks en el ganadero, 140 en el recolector y en la recogida): una espera más larga que la paciencia
es una espera **infinita**.

### I97 · Un destino FIJO aparcado no se vuelve a elegir (y lo que recoge el ganadero se mide con SU alcance)

Dos cosas del mismo reporte del jugador (*"el ganadero no coge los huevos del gallinero"*), las dos medido en su
partida (aldea 0, centro `470,646`, cota 63):

- **El ganadero tenía 4 huevos en el zurrón y su destino fijo aparcado.** `DevilRpgPuntoFallido = (517,64,666)` (I95)
  y la etiqueta de **otro** goal (*"Recogiendo lo suyo"*). El goal de su oficio elegía el destino fijo
  (`ENTREGAR` → `VillageStorage.puntoDeApoyo`) **sin mirar si estaba aparcado**: arrancaba, `tick` veía el
  aparcamiento, soltaba el destino y `canContinueToUse` lo paraba — y **al arrancar había CANCELADO al goal de
  recogida** (`VillagerPickupGoal`, prioridad 6, las **mismas banderas** `MOVE`/`LOOK`). Con el punto inalcanzable eso
  pasaba **cada tres ticks**: no podía entregar los huevos ni recoger más.
  **Regla:** un destino **fijo** (el almacén, el punto de apoyo del corral) que está **aparcado** hace que el goal
  **no arranque** y espere un rato, igual que ya hacía `VillagerPickupGoal.canUse` con su destino: no se arranca para
  abortar en el tick siguiente. Medido con el arnés tras el arreglo: el ganadero deja de parpadear, se le ve con el
  goal de recogida (`goals=[6:VillagerPickupGoal]`), llena el zurrón (4 → 8 huevos) y **entrega** (medido:
  `el ganadero guardo 8 cosa(s) de su oficio en la despensa` y **8** líneas de `N cosa(s) del corral al almacen`, que
  antes **no** salían porque el punto del almacén era inalcanzable).
- **El alcance con el que recoge del suelo es el SUYO** (ver arriba): el corral es su puesto de trabajo y tiene
  **valla de por medio**, así que lo que alcanza ahí lo mide su propio `REACH` (3,5), no el del recolector por oficio
  (2,5) ni el 1,8 que tenía. Lo medido, con el instrumento que toca en cada caso: **en vivo con el arnés** (con el
  alcance en 2,5) los huevos que están **encima de la paja** —los que con 1,8 no se alcanzaban **nunca**— ya se los
  lleva (se ve desaparecer el huevo viejo y subir el zurrón); y **celda a celda contra el guardado**
  (`build/gallinero_medida.py`, que aplica la misma distancia que el goal) el corralillo **entero** —la fila norte a
  **3,04** del pasillo y los de la paja a **3,35**— solo lo cubre **3,5**. Lo que no se ha visto en vivo es la corrida
  con el valor final (3,5): lo que hay medido de él es la cuenta celda a celda.

**Lo que queda pendiente (dicho claro):** las celdas del corralillo se recogen **desde el pasillo**, pero un huevo
que caiga **dentro de la valla** (en la celda de la propia valla, cosa que pasa cuando la gallina se queda pegada a
ella) sigue necesitando entrar. Y entrar al corralillo es poco fiable por construcción: su único portón es una
**puerta de valla** (el juego no planifica a través de ella cerrada, I96), las gallinas **viven** en él y la puerta se
cierra sola a los 5 s (I22). Lo que sí se midió es que, con el portón abierto, el ganadero **entra y trabaja dentro**
(`PORTON … open=true` en los volcados, con los huevos del corralillo desapareciendo y el zurrón subiendo).

### I98 · Una horda del mundo va por el RELOJ del mundo (y la presión se acumula en el latido)

El jugador: *"¿y qué pasó con los raids del mundo? ¿por qué no llega ninguno al pueblo?"*.

**Medido** en su partida y sus logs (22-23 sept): en **todos** los logs hay **una sola** línea de horda
(`[Horde] 2 spawneados de 8 cerca de Dev (distancia 946 prob 0.30)`, o sea **a por él**), **ni una** de
`[Horda] … a por la aldea`, y en el guardado **`Pressure = []`** (la presión de la aldea nunca pasó de 0). Tres
cosas, las tres arregladas:

1. **La presión solo se acumulaba DENTRO de la elección de objetivo** (`pickHordeTarget`), y esa función solo corre
   cuando ya ha tocado un **roll** de horda. Y el **primer** roll no cuenta: `accruePressure` arranca la base con
   `elapsed = 0` (presión 0) → la aldea solo podía ser elegida en el **segundo** roll, 20-40 min después.
2. **El reloj del roll vivía en memoria** (`HordeManager.TICKS`): **cerrar el juego lo ponía a cero**, así que el
   roll solo llegaba en sesiones más largas que el intervalo (13-20 min según la amenaza). Es la misma familia que
   I86 (*"un reloj que corre sin el jugador"*), pero al revés: aquí el reloj **se perdía** al cerrar.
3. **El guardado solo escribía las entradas de `Pressure` con ticks > 0** (`for entry : pressureTicks`): una aldea
   que acababa de empezar a contar tenía `Since` y `Ticks = 0`, o sea que **perdía su punto de partida** en cada
   carga y el abandono no llegaba a contar nunca.

**Regla:** la presión del abandono se acumula **con el reloj del mundo**, en el latido
(`VillageManager.acumularPresionDelAbandono`, cada `VILLAGE_POLL_TICKS` = 10 s, para las aldeas **generadas**, **no
caídas** y que **no estén ya bajo ataque**: los mismos requisitos que usa `pickHordeTarget` para elegirlas), y se
**persiste la unión** de `pressureTicks` y `pressureSince`. El roll de la horda es un **TURNO**
(`gameTime / intervalo`): se dispara cuando **cambia el turno**, así que va con el reloj del mundo y sobrevive al
cierre (lo único en memoria es el último turno, para detectar el cambio). Y la horda que va **a por una aldea** no
exige que el jugador esté fuera de la zona protegida —va contra el pueblo, no contra él—: la que mide la distancia
del jugador es la que va **a por él**.

**Medido con el arnés** (`MEDIR_HORDAS`, aldea 0, reloj puesto para que el turno cambie ~16 s después de arrancar;
`tools/arnes/LEEME.md`): la presión **sube sola** con los latidos (`9600 → 9800 → 10000 → 10200…`, o sea 8 min → 9),
el turno cambia de **0 a 1** justo en el borde del intervalo (`gameTime 21989`) y `pickHordeTarget` devuelve
**`aldea 0`** con la línea `[Horda] la aldea 0 lleva 8 min sin socorro: elegida como objetivo`. **Lo que NO se puede
medir headless** es el **spawneo** de la oleada: `HordeManager` usa `level.players()` y un jugador de pega **no está
en esa lista** (la misma limitación que el reloj del asedio, I86) — esa parte la ve el jugador en juego.

### I99 · El camino NO pisa lo que no es terreno (una celda es de UNA pieza)

El jugador, con captura: *"¿y por qué el kiosco tiene un bloque de tierra en vez de escaleras?"*.

**Medido** en su guardado (aldea 0, kiosco en `470,646`, cota 63; `build/kiosco_tierra.py`): de las cuatro
escaleras de las entradas, **tres están bien** (norte, este y oeste) y la del **sur** —la celda `(470,63,650)`, que
el constructor pone con `escalera(Direction.NORTH)`— era un **`dirt_path`**, y lo era **en el mundo y en el plano**.
Ese `dirt_path` **sobresale un bloque** (va en la cota, no a ras de suelo), que es exactamente lo que se ve en la
captura.

**Causa (de ORDEN):** los caminos se dibujan **después** del kiosco y `VillageGenerator.line()` pone el camino en
`groundY(px,pz) - 1`, o sea **sobre el bloque de superficie que encuentre**. En esa celda `groundY` ya veía la
**escalera** (sólida en la cota) → el camino se pintó **en su celda**, se la comió, y como el **plano se captura
escaneando el mundo**, el plano se quedó con el camino y el obrero lo daba por bueno: la escalera no volvía nunca.

**Regla:** `line()` **solo sustituye terreno natural** (`esTerrenoNatural`): una escalera, una losa, tierra labrada o
un bloque del jugador **paran el camino** (pasa de largo). Y `asegurarKiosco` **reafirma las cuatro escaleras de sus
entradas** (`asegurarLasEscalerasDelKiosco`, idempotente, una celda por entrada): corre en el latido, así que
**arregla las aldeas ya construidas sin migración** —igual que `asegurarHerreria`, `asegurarBarraca` y el propio
`asegurarKiosco`— y **no toca** lo que el jugador haya puesto ahí (lo deja y lo dice en el log).

### I100 · Lo que el pueblo entrega es el objeto DE VERDAD (no un equivalente vacío)

El jugador: *"al hacer clic con el botón secundario en el libro del invocado no me abre nada, ¿por qué no puedo ver
los pueblos descubiertos?"*.

**Medido** en su `playerdata` (`build/libro_jugador.py`): el libro de su inventario es un `minecraft:written_book`
con el NBT **`{count, Slot, id}` a secas**, o sea **sin un solo componente**: ni `written_book_content` (no tiene
contenido) ni la marca del mod. Con eso `DiarioDelInvocado.esElDiario` era **false** (el mod no lo reescribía) y
**no era el Diario** (así que no había aldeas que leer por ningún lado). Venía de la **recompensa por salvar una
aldea**: `VillageManager.grantReward` daba `new ItemStack(Items.WRITTEN_BOOK)` —con el comentario literal
`// receta (por ahora un libro genérico)`—.

**Regla:**
- La recompensa entrega **el Diario de verdad**, por la **misma puerta** que la piedra de invocación
  (`DiarioDelInvocado.entregarSiNoLoTiene`: se lo da si no lo tiene, lo pone al día si lo tiene, y si no le cabe lo
  suelta a sus pies). Un solo sitio para las dos (I4).
- **Un libro escrito se puede leer transformado**: el mod cancela el clic derecho **entero** mientras el jugador es
  hombre lobo (`onRightClickItem`: *no usar objetos*), y eso incluye los libros — que es lo que hacía que "no
  abriera nada" (el servidor nunca llegaba a `WrittenBookItem.use`, que es quien manda el paquete que abre la
  pantalla; comprobado en el bytecode de `ServerPlayer.openItemGui`). Los libros escritos quedan **exentos**: el
  estado normal del jugador es el hombre lobo y el Diario se tiene que poder leer cuando quiera (lo pidió así:
  *"debe ser un libro que pueda leer"*).

**Para una partida ya empezada** (la suya): el Diario se consigue **volviendo a leer la piedra de invocación** —
`entregarElDiario` no tiene guarda de "ya leída", así que si no lo tiene se lo da (y `loreStoneRead` ya estaba a 1,
o sea que no vuelve a dar experiencia).

### I101 · La aldea NO produce pedernal: las flechas dependen de lo que traiga el jugador

Lo preguntó el jugador: *"los herreros también hacen flechas y el arquero las usa y las dispara? … que en el almacén
haya flechas. ¿Qué otra cosa se necesitaría?"*.

**Medido en su guardado y sus logs** (aldea 0, cota 63; `build/flechas_medida.py`):

| Eslabón | Estado |
|---|---|
| El ganadero **sacrifica pollos** | ✅ **36** en dos días de logs (`El ganadero: sacrifica un Chicken`) → **plumas** |
| El herrero **hace flechas** | ✅ **56** tandas de `Hizo 4 flechas` (224 flechas) — receta `Flechando`: 1 palo + 1 pluma + 1 pedernal = 4, con objetivo `OBJETIVO_FLECHAS` = **64 en el almacén** |
| El **arquero** las usa | ✅ `VillagerGuardGoal`: saca `FLECHAS_POR_VIAJE` del **almacén** y dispara flechas de verdad (gasta una por disparo) |
| El **almacén**, hoy | ❌ **355 troncos, 32 tablones, 2 cuerdas, 30 palos** y **ni una pluma, ni un pedernal, ni una flecha** |
| Los **arqueros**, hoy | ❌ **2, 0 y 0 flechas** en el zurrón (Josefa, Dorotea, Aurelia) |
| El **pedernal** | ❌ **nadie lo produce**: no hay ninguna meta que cave grava y el recolector no lo barría |

**Regla (lo que se ha hecho):** el **recolector** barre también **pedernal y flechas** (`VillagerCollectGoal
.esDelPueblo`): antes solo los barría un herrero **a 20 bloques** de él, así que lo que caía fuera de ese radio
(también las flechas que sueltan los esqueletos que mata la milicia) se quedaba en el suelo.

**Lo que queda, dicho claro:** el **pedernal es el cuello de botella** y la aldea **no puede producirlo** (haría
falta una **cantera de grava**: una meta que cave grava, que hoy no existe). Como el agua embotellada del clérigo
(I87), hoy es material **que trae el jugador**: con pedernal en el almacén el herrero hace flechas (tiene palos y
las plumas salen de los pollos) y los arqueros se rearman solos.

> **ACTUALIZADO EN LA ETAPA I (I102):** el jugador contestó a esto pidiendo un **MINERO** que *"excave el suelo hacia
> abajo… y el cobblestone que recoja, que lo filtre en agua para sacar algunos pedernales"*. La aldea **sí** produce
> pedernal desde entonces: **4 adoquines → 1 pedernal** en la balsa de la caseta del minero (y la **grava** que pica
> da su 10 % de pedernal de vanilla). El cuello de botella pasa a ser la **madera de los marcos** (ver I102).

### I102 · La MINA del pueblo es del MINERO (y él arranca la cadena del hierro)

Lo pidió el jugador: *"mejor haz otra profesión que sea de minero, y que excave el suelo hacia abajo, haciendo
túneles, andamiajes, soportes, escaleras en espiral, todo para sacar minerales; luego en su lugar de trabajo fundirlos
y el cobblestone que recoja, que lo filtre en agua para sacar algunos pedernales. **Su lugar de trabajo no lo debe
regenerar ningún otro trabajador**, ya que se taladraría seguido; **quien puede regenerar lo que construya es el propio
minero**. Debe tener también su hora de comida y descansar como todo aldeano. El material necesario para construir,
como tablones, y las herramientas, que las saque del almacén. Debe haber un **herrero de herramientas** que haga
herramientas para que el minero haga su trabajo, similar a los otros 2 herreros."*

**La geometría (dónde y cómo), que es lo que más se puede equivocar:**

| Pieza | Regla | Por qué |
|---|---|---|
| **Solar y caseta** | Solar **11×11** en `rel (-9, +12)` (base `456,653`, eje `461,658`), a **15** de la plaza y **58** del almacén; caseta **7×7** con el **cortapiedras** (su puesto de trabajo), **horno**, **balsa de agua a ras del suelo**, cama y farol | El solar se midió libre con `build/solar_mina.py`; el cortapiedras es el puesto del oficio `MASON` (I31) y sin él el juego le borra el oficio |
| **Caracol** | Radio **4** (anillo de **32** celdas), **medio bloque de bajada por celda** (**16** por vuelta), de la cota a `MINA_FONDO = -58` (**240 pasos**) | Un bloque entero **no se sube** andando (I26/I95: `maxUpStep` 0,6) y la mina se recorre **en los dos sentidos** |
| **Pieza del caracol** | **Losa** en los pasos pares y **adoquín entero** en los impares | **No escaleras**: una escalera tiene la cara alta en **una** dirección (I26) y el anillo tiene **esquinas**; en una esquina el que sube sale por el lado del escalón y el siguiente está un bloque entero más abajo (**1,0, no se sube**). La losa es uniforme en las cuatro direcciones. Comprobado celda a celda (`build/mina_geometria.py`): **todos** los saltos son 0,5 |
| **Soportes** | Marco de **dos postes (2 de alto) y su viga** cada **16 escalones** (8 bloques) en el caracol, y cada **8 celdas** en las galerías | Con uno cada 4 escalones el pueblo no da abasto de tablones (8 marcos por vuelta): medido, lo asierra el herrero de herramientas |
| **Galerías** | Cada **8 bloques** de descenso, **24** celdas en cruz (norte, este, sur, oeste), con su marco y su antorcha | Es lo que "saca minerales" de verdad, y en cruz para no agujerear siempre el mismo lado |
| **Luz** | Antorcha en la pared cada **4 bloques** | Los bichos nacen a **luz 0** y la mina está **dentro de la muralla**: un zombi ahí dentro **corta el latido del pueblo entero** (I11) |
| **Agua y lava** | Una **bolsa** se sella con adoquín y el túnel sigue; **más de 12 celdas seguidas** (un mar, un acuífero) **paran la mina**, con **piedra labrada** de tope | Lo pidió el jugador: *"que selle las bolsas; si es un mar, que pare"* |

**Lo que hace el minero** (`VillagerMinerGoal`, prioridad 4 como los demás oficios): cava la siguiente celda (hueco
de paso de **tres** celdas, I26), **se lleva lo que pica** (adoquín, tierra, grava y **el botín de las vetas**), pica
además las **vetas que se le quedan a la vista** en las paredes del túnel, rellena el suelo hueco, pone el **marco** con
los tablones del almacén y la **antorcha**; cada cierto trabajo **sube** a su caseta a **fundir** el hierro, el cobre y
el oro crudos (con su carbón o con la leña del almacén, respetando `RESERVA_LENA`) y a **colar** el adoquín en la
balsa (**4 → 1 pedernal**), y **baja lingotes, pedernal, carbón y gemas al almacén**. Su **pico se gasta** (una unidad
de uso por celda, la durabilidad de verdad: 250 celdas el de hierro) y cuando se rompe va a por otro; los forja el
**herrero de HERRAMIENTAS** (3 lingotes + 2 palos, `OBJETIVO_PICOS = 2`), y **come y duerme** como todos
(`tieneHambre` / `estaDescansando`: no empieza faena en esas franjas).

**Reglas (lo que NO se puede romper):**

- **La mina es del minero** (lo pidió él): el pozo, los marcos y las galerías se construyen con `level.setBlock`
  **directo**, así que **no entran en el plano** y el **obrero no los repone** (I8). El **plano**, el **nivelado** y el
  **tapagujeros del suelo** (I90) **excluyen** la mina: el tapagujeros y el plano, con el radio **estrecho** del pozo
  (`MINA_POZO_RADIO = 6`: ahí solo ha tocado el arranque del caracol, y alrededor está la aldea y su suelo se repara
  igual); el **nivelado**, con la **zona** entera (`MINA_EXCLUSION_RADIO = 29`, que cubre las galerías) y **solo para
  el AIRE** —el terreno natural de esa zona se nivela como cualquier otro, o la mina dejaría un hoyo en la meseta
  desde el día en que se genera—. Sin esto el obrero rellenaba el pozo a los diez segundos (por bloques es idéntico a
  un cráter de creeper) y el minero cavaba contra él para siempre.
- **La CASETA sí es del pueblo**: se construye con `colocar` (entra en el plano, la mantiene el obrero) y es
  **idempotente** con testigo (su suelo de piedra labrada), así que llega a las aldeas ya construidas **sin
  migración**, como la herrería o el taller del leñador. Y como **cambia lo que se construye**, sube
  `CURRENT_LAYOUT` a **70** y el plano se vuelve a capturar (si no, el obrero no tendría nada que reponer de la
  caseta: es la lección de la 67, I8).
- **El minero NO toca lo que ha puesto el pueblo** (`elMineroPuedePicar`: aire, terreno natural o una pieza suya).
  El anillo va a **4** del eje y la caseta llega a **3**: el caracol pasa **pegado a la pared este de su propia
  caseta**, así que sin esa guarda el minero le abriría un boquete para poner los postes de un marco (y el obrero se
  pasaría la vida reponiéndolo). Es I24/I27 aplicada al pico.
- **El progreso se MIRA en el mundo, no se guarda** (`progresoDeLaMina`, `progresoDeLaGaleria`): cuántas celdas del
  caracol y de la galería tienen ya su pieza. Así, si el jugador rompe una celda del caracol, el minero **la vuelve a
  hacer** en vez de saltársela para siempre, y no hay ningún contador que se pueda desincronizar con la mina de
  verdad. La **galería se cava ANTES de poner la pieza de su celda**, que es la que mueve el progreso: una galería no
  se puede quedar a medias nunca.
- **El hierro no se puede quedar en crudo**: el minero funde lo que saca, y si no puede (sin pico con el que volver,
  o sin combustible) el **herrero funde el crudo del almacén** (`RAW_IRON`/`RAW_COPPER`/`RAW_GOLD` → lingote). Es lo
  que cierra el círculo del pueblo. Y para **arrancarlo** —una aldea vieja no recibe la remesa inicial del almacén—
  la **caseta trae una vez** un **pico de hierro y 6 lingotes** al almacén (`VillageStorage.asegurarElPicoDelMinero`,
  solo si no hay ningún pico): medido en el guardado del jugador, su aldea tenía **0 lingotes y 0 crudos**, así que
  sin eso el minero se quedaba plantado pidiendo herramienta para siempre.

**Fallos que costó (medidos con el arnés, `MEDIR_MINERO`):**

1. **La boca salía 13 bloques más allá**: la caseta ya trabaja con el **eje** de la mina y le pasaba el eje a un
   ayudante que espera el **centro de la aldea** (`celdaDelCaracol` suma `MINA_OFFSET` por dentro). Medido: la celda
   de la boca salía con **césped** y el minero no tenía por dónde empezar. Ahora hay **una sola cuenta**
   (`celdaDelCaracolDesdeElEje`) y la de centro y eje son la misma.
2. **Subía al taller después de CADA celda** (16 celdas en 136 s, casi todo andando): el disparador era "tiene
   adoquín y algo cavado". Ahora sube con **tanda hecha** (32 adoquines, mineral crudo, sin antorchas o zurrón
   lleno).
3. **En la galería el destino era la celda SIN cavar** (piedra): el caminante no puede ir a una celda por la que no
   se anda, así que el minero se quedaba plantado **en la superficie justo encima de la mina**, con la etiqueta
   "Bajando a la mina" y sin camino. El destino es la **última celda hecha**.
4. **Se quedaba "Picando" de pie en el cofre del almacén**: al acabar de cargar material no recalculaba la faena y el
   `destino` seguía siendo el almacén (etiqueta `Picando -> 517, 63, 666`, sin cavar una celda).
5. **La galería salía HACIA DENTRO** (la del paso 16, esquina suroeste, apuntaba al este: por donde va el caracol), así
   que su primera celda era la del paso 15 —ya cavada y con su pieza— y el minero **se comía su propio escalón**. El
   progreso oscilaba **16 → 15 → 16** y en el log salían en bucle `caracol paso 15` / `galeria … celda 1 de 24`. Ahora
   la dirección es **siempre hacia fuera** del pozo.
6. **Subía a vaciar el zurrón cada tres celdas**: se le llenaba de **tierra, arena y grava** (basura que no le sirve).
   Medido: **dos celdas de galería en once minutos**. Ahora no recoge basura (de la grava solo el 10 % de pedernal) y
   sube con tanda hecha.
7. **El `JOB_SITE` en disputa con el latido**: si en la aldea hay **otro cortapiedras**, el latido y el goal se lo
   quitaban y se lo daban **en cada pasada** (`el puesto de trabajo en 459, 63, 656 tenia el ticket PERDIDO … liberado
   y reclamado`, cada 10 s y para siempre). Ahora, si ya tiene **un** cortapiedras, no se toca.
8. **La comida paraba la mina**: con hambre y comida en el pueblo se para a comer (lo pidió el jugador), pero si la
   taberna no le da la ración se quedaba plantado en la puerta (medido: minutos sin cavar y sin comer). Ahora espera
   `ESPERA_DE_COMIDA_MAXIMA` (20 s) y, si no come, vuelve al tajo.

**Medido con el arnés** (`MEDIR_MINERO`, corridas sobre copias de su partida: aldea 0, cota 63, 240 pasos hasta el
fondo):

| Qué | Medida |
|---|---|
| **Caseta, boca y pico** | `caseta del minero y boca de la mina en 461, 63, 658`; el minero reclama su cortapiedras, va al almacén, coge un **pico de hierro** nuevo y a los **45 s** está en la primera celda |
| **El caracol** | pasos **1 → 16** (15 celdas, **8 bloques de descenso**) en **42 s**; la celda de la cara pasa de `grass_block` a `cobblestone_slab` (su pieza) |
| **La galería** | la del paso 16 (8 bloques): sale **hacia fuera** (celdas `457,54,663` y `457,54,664`, al **sur**), 2 celdas en **5 s**, con su marco y su antorcha |
| **El pedernal** | **6 pedernal** en el almacén (4 adoquines → 1, una colada cada 40 ticks en la balsa) |
| **La fabricación** | `hace 4 antorchas con un carbon y un palo` y `deja lo sacado en el almacen (pico si, tablones 11, antorchas 16)` |
| **El pico** | desgaste **18/250** tras 18 celdas (una unidad de uso por celda) |
| **Comida y descanso** | se le ve `Comiendo en la taberna` y de noche el goal no arranca (`estaDescansando`) |

**Lo que esta medida NO cubre, dicho claro**: el servidor headless corre a los ticks que le deja el equipo (en la
corrida larga, ~15 TPS y no 20), así que los tiempos de reloj son **del arnés**, no de su partida; y en una corrida
puede haber **dos albañiles** (el que planta el arnés y el que repone el latido), así que una muestra de "el minero"
puede ser de uno u otro. Lo que se ve **en el cliente** (la mina dibujada) no se mide aquí.

**SEGUNDA VUELTA: LA MINA SE MUDA AL DESCAMPADO (migración 71).** Nada más verla construida, el jugador pidió dos
cosas: *"mete más la cama del minero porque quedó fuera y bloquea la puerta. También mueve la cabaña del minero
porque está muy cerca del centro, ponlo más bien en un lugar cercano al muro y donde haya mucho espacio que no se
haya utilizado aún"*.

- **La cama estaba EN LA PARED**: el pie iba en `(c+1, c+2)` con la cabecera al sur, o sea que la cabecera caía en la
  celda de la **pared sur** —justo al lado de la puerta— y asomaba por el hueco (era la cama que se ve en su captura).
  Ahora va **dentro**, pegada a la pared **este**: pie en `(c+2, c-1)` y cabecera en `(c+2, c)`.
- **El solar se muda** de `rel (-9,+12)` (a **15** de la plaza) a `rel (+33,-29)` → eje `(503,617)`, base `(498,612)`,
  a **44** del centro y a **18** del muro. Lo eligió `build/solar_mina2.py` sobre su guardado: **625 de 625** celdas
  libres en un entorno de **25×25** (el descampado del noreste; también el sitio con más hueco de toda la aldea) y las
  columnas del caracol son las más **macizas** y secas de las candidatas junto al muro (la peor racha de agua es de
  **5** celdas, muy por debajo del tope de sellado del minero), o sea que la mina no se va a parar a los diez bloques.
- **La mina vieja se RETIRA** (`VillageGenerator.deshacerLaMinaVieja`, migración **71**): la caseta (su suelo de
  piedra labrada, las paredes, el tejado, el cortapiedras, el horno y la balsa) vuelve a ser **césped y aire**, y el
  **pozo** que el minero hubiera cavado se rellena (**césped** en la capa que se pisa, **piedra** por debajo). Sin
  esto quedarían **las dos casetas** —la vieja está en el plano y el obrero la repondría— y el pozo viejo sería un
  agujero en el suelo del pueblo que el tapagujeros iría rellenando a medias. La migración **tira el plano** y el
  nuevo se captura ya con la caseta nueva dentro (I8).

### I103 · La huerta se cosecha ANTES de compostar, y un bancal SIN DUEÑO se coge aunque su ticket esté perdido

El jugador, otra vez: *"otra vez los granjeros están dejando demasiadas parcelas sin cosechar. Ya no hay verduras para
comer pero las parcelas no están siendo cosechadas. ¿qué pasa?"*.

**Medido en su guardado** (`build/huerta_edades.py`, `build/granjeros_estado.py`, `build/cofres_aldea.py`): las tres
parcelas **llenas y sanas** (0 vacías, 0 pisoteadas) con **34 / 25 / 35 plantas maduras** sin cosechar, y la
**despensa con comida de sobra** (21 de ternera, 22 patatas, 16 zanahorias, 24 huevos, 15 huevos estrellados…): el
atraso era de la **huerta**, no de la comida. Y **medido con el arnés** (`MEDIR_HUERTA`, ampliado para volcar la tierra
cultivo a cultivo, los composteros con su dueño y los bichos dentro del recinto):

```
[Arnes] GRANJERO c38cb784 nombre=Ursula ... job=SIN PUESTO   <- la tercera granjera, sin estación
[Arnes] COMPOSTERO bancal 0 en 437,63,660 bloque=composter poi=SI dueno(s): NADIE   <- libre y sin dueño
[Arnes] TIERRA bancal 0: MADURAS 37 | creciendo 37 | VACIAS 0     <- y ahí se quedó CUATRO minutos
[Arnes] BICHOS DENTRO DEL RECINTO: 0                              <- el latido del pueblo sí corría
```

**Dos causas, las dos en el reparto del trabajo:**

1. **La estación se buscaba MAL cuando su ticket estaba perdido.** `reclamarEstacionesDelPueblo` pedía al gestor de
   puntos de interés **la estación ocupada MÁS CERCANA** (al centro de la aldea) y, si era de otro aldeano,
   **abandonaba**. Con el compostero del **bancal 0** con el ticket perdido y el del **bancal 1** (más cerca del
   centro) en manos de otro granjero, la tercera granjera se quedaba **sin puesto para siempre**: su bancal se quedaba
   sin nadie y con 37 plantas maduras que no bajaban ni una. **Regla:** de las estaciones de su oficio se elige
   **una LIBRE** y, si no hay, **una OCUPADA SIN DUEÑO** (el ticket perdido, que se suelta y se coge); la "más
   cercana" es el último recurso.
2. **El compostero iba ANTES que la cosecha, y con UNA semilla por viaje.** El paso del polvo de huesos (que existe
   desde I93, porque el leñador también lo gasta en la arboleda) estaba **por delante** de cosechar, y como el
   granjero va con las semillas justo por encima del tope (`SEMILLAS_MAX`), cada paseo echaba **una** semilla: su log
   lo cantaba (`Lleno el compostero con 1 semilla(s)`, una y otra vez). **Regla:** la faena que **produce** (cosechar,
   labrar, sembrar) va **antes** que la que **transforma** (compostar, abonar) — cuando se llega al compostero ya no
   queda nada maduro, así que el viaje no le quita el turno a nadie— y al compostero se va con un **puñado**
   (`SEMILLAS_MINIMAS_PARA_COMPOSTAR = 4`), salvo que falte harina de verdad.
3. **Y una red de seguridad**: un granjero **sin estación** ya no se reparte el bancal por UUID a secas (podía caer en
   el de un compañero y dejar otro sin nadie): se le da **el bancal que no tenga dueño**.

**Medido después** (misma corrida, ~9 min): `Ursula` **reclama su estación de farmer en 437,63,660** y los tres
composteros quedan con dueño; las maduras bajan **37 → 10 → 0**, **4 → 1 → 0** y **20 → 10 → 0**, las parcelas siguen
llenas (`VACIAS 0`) y la despensa sube de **267 a 466 puntos** con las verduras de **58 a 154**. Líneas literales en
`tools/arnes/medidas-huerta.txt` (segunda vuelta).

> **La lección, en una línea:** una estación **libre** no se coge por cercanía al centro sino **buscando la que no
> tiene dueño**, y la faena que **produce comida** va antes que la que **la transforma**.

**TERCERA VUELTA: LA BARRIDA DEL BANCAL (y el aldeano atrapado en su casa).** El jugador afinó lo que quería: *"lo
que quiero es que una vez que un granjero está en una parcela, revise TODA y coseche TODAS las que ya están maduras.
Si llegan a sobrar, que pare cuando llegue a su límite de capacidad y deje sin cosechar las que sobran, entonces es
cuando ya puede ir a la despensa a dejar todo"*. Tres cosas, las tres medidas con el arnés:

1. **La barrida** (`VillagerFarmGoal`, tarea `COSECHAR`): al cosechar una mata, el granjero busca **la siguiente
   madura de SU bancal** y sigue con ella **sin soltar la faena** (el mismo patrón que ya usaban abonar y recoger del
   suelo). Solo para cuando **no queda ninguna madura**, cuando la siguiente **ya no le cabe** (entonces el zurrón
   está lleno: a la despensa, que es justo lo que pidió) o cuando se le acaba el tiempo. Antes hacía **una celda por
   salida** y volvía a decidir, así que entre cosecha y cosecha se iba a la despensa cada 8 unidades y dejaba el resto
   de la parcela a medias.
2. **El tope de semillas era el tapón**: guardaba hasta **72** (`SEMILLAS_MAX` 8 + `SEMILLAS_PARA_COMPOSTAR` 64) —
   **1-2 huecos del zurrón**— y con los huecos llenos de semillas `leCabeLaCosecha` decía que no: la barrida se
   cortaba a las **8-11 unidades** y el bancal se quedaba en **12-15 maduras para siempre** (medido). Ahora
   `SEMILLAS_PARA_COMPOSTAR = 8` (tope 16) y el compostero se atiende en cuanto hay 4 de sobra.
3. **El rescate no la veía**: `rescatarAldeanosAtrapados` solo miraba a los aldeanos a **más de 4 bloques** de la
   cota; la granjera **Ursula**, congelada **dentro de su casa** en `428,65,669` (cota 63, su cama en el piso de
   arriba y la escalera debajo de los pies), quedaba **exenta** y no la rescataba nadie: su bancal se quedaba con
   **36 plantas maduras** y en el log salía `no consigue llegar a 440,63,668` cada 5 min. Ahora se exime al que está
   **en la calle por la altura de los pies** (`cota + 0,6`: lo que se sube andando, I26/I95), así que a los 30 s sin
   moverse se le baja a la plaza.

**Medido después**: bancal 0 **36 → 21 → 0** maduras (en cuanto pisó la calle), bancal 2 **15 → 9 → 6 → 0**, bancal 1
**1 → 0**, los tres con **74/74 celdas sembradas** (`VACIAS 0`) y la despensa de **267 a 525 puntos** con las
verduras de **58 a 193**. Líneas literales en `tools/arnes/medidas-huerta.txt` (apartados 3 y 4).

**CUARTA VUELTA: LA COMPUERTA SE ABRE Y SE CRUZA (y la huerta no es de los golems).** En su partida el jugador vio
*"cosechan y ponen guardando lo suyo… van y vienen unos cuantos bloques sin ir a la despensa pero perdiendo tiempo sin
terminar de cosechar todo"*, y su log lo canta:

```
[13:29:52] El granjero: Guardo 8 en la despensa
[13:29:52] c38cb784-... no consigue llegar a BlockPos{x=440, y=63, z=668}: lo deja por 5 min
[13:29:53] el granjero guardo 13 cosa(s) de su oficio en la despensa      <- el goal de "guardar lo suyo"
[13:30:06] c38cb784-... no consigue llegar a BlockPos{x=440, y=63, z=667}: lo deja por 5 min
```

**Lo que pasaba**: el granjero no conseguía cruzar la compuerta de su bancal y, al rendirse, el goal aparcaba **la
mata que iba a cosechar** (`stuckTicks = STUCK_LIMIT` en la pierna de la entrada → `canContinueToUse` aparcaba el
**objetivo**): cada intento fallido se llevaba por delante **una planta del borde** (440,668 → 440,667 → …) y, como el
goal de la huerta se rendía, entraba el de recoger, se iba a la despensa con la cosecha y volvía **a fallar en la
misma compuerta** — con **cuatro compuertas** por bancal, tres de ellas sin usar.

**Reglas (el jugador lo dijo claro: *"es una puerta!!! debe poder abrirla y cruzar sí o sí"*):**

- **Aparcar la ENTRADA, no la mata** (I33): cuando no puede cruzar, se marca la **entrada** como fallida y se
  **prueba otra compuerta** (`mejorEntradaLibre` ordena las cuatro por cercanía y salta las aparcadas). La mata sigue
  disponible: no se pierde ni una planta del borde por culpa de una compuerta.
- **Abrirla él mismo y rehacer el camino**: si ya está al lado (3 bloques), el granjero la abre por
  `VillagerGateGoal.abrirParaUnAldeano` (el juego no deja que un aldeano abra una puerta de valla: la abre el pueblo)
  y, **con la compuerta ya abierta, se le borran `WALK_TARGET` y `PATH`** para que la ruta nueva cruce — la que traía
  se calculó con ella cerrada y acababa en su propia casilla, pegado a la valla. Es el mismo remedio que ya usaba
  `VillagerGateGoal.abrir` (medido allí con Isidoro).
- **La huerta no es de los golems**: el golem de hierro lo pone el **juego** (`Villager.spawnGolemIfNeeded` lo crea
  **al lado del aldeano que lo convoca**), así que un granjero dentro de su bancal lo hacía **nacer en la huerta** — y
  la tierra de cultivo pisada se vuelve tierra y el bancal se pierde. `CommonForgeGolemEventSubscriber` corta el
  suceso: si el golem acaba de nacer sobre la huella de un bancal **no entra** (y queda en el log), y si viene del
  guardado **se le saca a la calle**. La red de seguridad para el que se cuele andando está en el latido
  (`VillageManager.sacarLosGolemsDeLaHuerta`, que también saca al que ya estaba dentro en el guardado del jugador).
  La prueba de "sobre la huerta" es solo X/Z (`VillageGenerator.sobreLaHuellaDeUnBancal`), que es lo bastante barata
  para un suceso de spawn.

**QUINTA VUELTA: EL PAN ES DEL COCINERO (y el viaje a la despensa vuelve con 16).** El jugador lo remató en la misma
tacada: *"el cocimiento de los panes no lo debería hacer el granjero sino el COCINERO"* y *"que los granjeros lleven de
una vez `LLEVAR_TRIGO = 16`"*.

- **Quien hornea es el cocinero**: el granjero ya **no** hornea nada (su log ya no dice `Guardo 8 y horneo 2 pan(es)`,
  solo `Guardo N en la despensa`); deja el trigo y se va. El pan lo hace `VillagerCookGoal.hornear` en el **ahumador
  de la taberna**, en la misma tanda que la cocina —un solo tronco paga las dos cosas— y con la misma receta de
  vanilla (`VillagePantry.WHEAT_PER_BREAD` = 3 de trigo por hogaza) y el mismo tope por visita
  (`HORNEAR_MAX` = 2 hogazas). El `canUse` del cocinero se enciende también cuando **no hay carne pero sí trigo de
  sobra** (`hayTrigoQueHornear`): antes, con la despensa llena de trigo y el corral sin exceso, el cocinero se quedaba
  de brazos cruzados.
- **La reserva de cría viaja con el horno**: `RESERVA_DE_TRIGO_PARA_CRIAR` (4) sale del granjero y se muda a
  `VillagePantry`, que es donde está el horno (regla de I68: el horneado **nunca** deja al ganadero sin trigo para
  criar → sin cría no hay cuero ni lana). Quien hornea es quien respeta el tope; por eso el tope vive con el horno.
- **Si el pan no cabe, el trigo vuelve al barril**: igual que en `cocinar`, la materia prima no se destruye porque la
  despensa esté llena de verdura (se devuelve el trigo y se hornea cuando haya hueco).
- **Cada viaje lleva 16**: `LLEVAR_TRIGO = 16` (eran 8) — el doble de comida por paseo a la taberna, que está a 40-55
  bloques. El hueco del zurrón aguanta 64, así que 16 no compromete la barrida del bancal (lo que la cortaba eran las
  semillas: ver `SEMILLAS_PARA_COMPOSTAR`).
- **Y EL RESCATE DEL ATRAPADO CUENTA LA CELDA EN HORIZONTAL (x,z)**: el arreglo de la vuelta anterior (eximir al que
  está "en la calle" por la altura de los pies) **no bastaba**, y lo cazó el arnés: la granjera **Ursula** se quedaba
  **botando** en la escalera de su casa (misma columna `428,669`, la Y oscilando entre **65 y 67** con la cota en 63)
  más de **80 s** con su bancal 0 en **29 matas maduras** que no bajaban, y en el log **no salía ni un solo**
  `estaba atascado dentro de una casa`. La causa: `rescatarAldeanosAtrapados` guardaba la celda con
  `blockPosition().asLong()`, que **incluye la Y**, así que **cada bote reiniciaba el contador** de "lleva 30 s
  quieto" (`ATRAPADO_TICKS`) y el rescate no disparaba **nunca**. Ahora la celda se mide con
  `BlockPos.asLong(x, 0, z)`: quieto = "no cambia de columna", que es lo que de verdad significa estar atrapado.
- **Y solo se baja al que NO sabe irse solo**: junto a "quieto 30 s y por encima de la calle" se exige ahora que **no
  tenga ruta** a la plaza (`createPath(...).canReach()`), porque estar 30 s en la misma columna **no** es estar
  atrapado si el aldeano puede caminar (está esperando o trabajando). En el **kiosco**, que va un bloque por encima de
  la calle y tiene dentro la despensa y el ahumador, sin este filtro se bajaba a la plaza al **cocinero** en plena
  faena (medido con el arnés: Teodoro llegó a **24 s** quieto en `498,664` antes de moverse por su cuenta). A Ursula
  **no** le quita el rescate: su ruta a la plaza acababa en su propia casilla (`a1=3n alcance=NO fin=428,68,665`).

**RESUELTO EN LA MISMA VUELTA (migración 72): la puerta de la taberna al almacén.** El jugador dijo que sí a abrir el
paso, y se abrió con una **puerta de servicio en el muro ESTE de la taberna** (`abrirElPasoDeLaTabernaAlAlmacen`), a la
cota, **una celda al sur del centro del cobertizo** (la columna despejada de la rejilla de postes de 3 en 3, que es
donde enfrente está el punto de apoyo) y con dos celdas de suelo llano al otro lado, así que **no hay escalón**. Es
idempotente (si ya hay puerta no escribe; si el paso ya está en aire no le pone una puerta a un boquete) y va **antes
de tirar el plano** (I8), o sea que la puerta entra en el plano y el obrero la mantiene.
Medido con el arnés, con la siembra de leña **apagada** para medir la cadena entera: la ruta al almacén pasa de
`a1=14n alcance=NO fin=511,63,666 dFin=6.00` a **`a1=24n alcance=SI fin=516,63,666 dFin=1.00`**, y el cocinero
**va él por la leña** (`cogio 4 tronco(s) del almacen` ×3), **cocina** (`8 pieza(s) cocinadas` ×8) y **hornea 16
hogazas** (`horneo 2 pan(es)` ×8) sin que se le sembrara nada. Ver `tools/arnes/medidas-huerta.txt`, apartado 6.

**Y QUEDA APUNTADO** (medido en esa misma corrida, no es de esta migración): el **leñador** falla al ir a su arboleda
(`426/432,63,629`, 17 veces) y los aldeanos **lejanos** (a 50-60 bloques, p. ej. el guardia en la barraca o la
recolectora en la plaza) sacan `almacen=NO(nulo)` simplemente porque el planificador no da ruta desde tan lejos; los
herreros y el minero, que vienen del norte, siguen llegando.

### I104 · Nadie trabaja de pie encima de una CAMA (el leñador que no salía de casa)

Lo pidió el jugador al ver el hallazgo de la vuelta anterior: *"corrige al leñador"*.

**Medido con el arnés (23-sep-2026, aldea 0)**: la leñadora **Tomasa** amanecía **de pie sobre su cama**
(`columna=428,665 y=67.56 pies=red_bed home=428,67,665`) y desde ahí **no tenía ruta a ninguna parte**:
`rutaAPlaza=a1=1n alcance=NO fin=428,68,665 dFin=44.70` — el planificador le devolvía **su propia casilla**. Se
pasaba el día dando pasitos por encima de la fila de camas (columnas 424/426/427/428, todas a `y=67.56`) con su
etiqueta en "Yendo al arbol" y el log llenándose cada 5 min de `no consigue llegar a BlockPos{x=426, y=63, z=629}`
(los huecos de su arboleda): **17 avisos y 0 troncos talados** en una corrida. Y el rescate de I103 **no lo veía**,
porque ése pide **30 s en la misma columna** y ella cambia de columna al caminar por las camas.

**La causa** es que **una cama no es una casilla de pie** para el planificador: el aldeano se despierta encima —las
camas de esa casa están pegadas unas a otras y el piso de arriba está lleno— y desde ahí no encuentra ni un nodo
válido. Probado primero **lo fino —bajarle a la casilla libre de al lado— y NO vale** (medido: se le teleporta al
hueco entre camas, tampoco tiene ruta porque el piso está desconectado, el cerebro lo empuja hacia su objetivo
andando en línea recta, se sube a la cama y vuelta a empezar: **20 avisos en 4 min** sin moverse del sitio).

**Regla:** `VillageManager.bajarDeLasCamas` — al aldeano adulto **de pie encima de una cama** que **no está durmiendo
ni en su franja de descanso** se le baja a **la calle** (la misma casilla de la plaza que usa el rescate) tras el
mismo margen de 30 s (`ATRAPADO_TICKS`); desde la calle va a trabajar por su cuenta. **Al que duerme no se le toca**
(dormir en la cama es legítimo).
**Medido después del arreglo** (misma copia, ~12 min): **una sola** bajada de la cama en toda la corrida, **6 árboles
talados**, **6 entregas al almacén** (`el lenador guardo 16 cosa(s) de su oficio`), y sus avisos de "no consigue
llegar a la arboleda" quedan en los **2 primeros** (antes de bajarle); el resto son árboles del monte a 100+ bloques
que aparca y sigue. Sin regresiones: tres bancales a 0 maduras, golems 0 y el cocinero con 7 viajes por leña y 160
horneadas. Ver `tools/arnes/medidas-huerta.txt`, apartado 7.

**Y QUEDA APUNTADO (la causa de fondo, sin arreglar)**: el **piso de arriba de esa casa está desconectado** del bajo
para el planificador (desde el hueco entre camas tampoco hay ruta), así que cada mañana hay que bajarle (una vez, no
en bucle). El origen es el **apiñamiento de camas** de esa casa (migración 14: "hasta 4 por casa"): el juego no
encuentra casilla libre al despertar y deja al aldeano **encima** de la cama. Arreglarlo del todo es de
plantilla/migración de casas, no de goals. El mismo patrón apareció en la **posada de la taberna** (`6fceef7a`
rescatado de `511,68,667`), así que mientras tanto la red de seguridad es este arreglo más el rescate de I103.

### I105 · El planificador no da ruta a más de ~56 bloques: los viajes largos van por TIRONES

Lo reportó el jugador con dos capturas: *"el minero no baja al almacén, se queda atorado en el segundo piso"*, con su
etiqueta en **"Yendo al almacen"** y la F3 en `512,68,667` (la galería de la posada de la taberna).

**La causa, medida en el bytecode del juego** (`javap` sobre `PathNavigation.createPath`): la región de búsqueda se
construye **alrededor del ALDEANO** con radio `FOLLOW_RANGE + 8`; el `FOLLOW_RANGE` de un aldeano son **48**, así que
**56 bloques**. A un destino más lejos **no le da ruta ninguna**, y sin camino el aldeano **empuja en línea recta**
hacia su objetivo: el minero entró en la taberna, subió la escalera del comedor y se quedó contra la pared este de la
galería — exactamente el sitio del que hubo que **rescatar al guardia Ubaldo** en la corrida anterior. Desde su mina
(`503,617`) el almacén (`517,63,666`) queda a **51** bloques de recta pero mucho más de camino (rodeando la taberna),
así que no había ruta. El mismo límite explica al **recolector** (`rutas[almacen=NO(nulo) plaza=SI(9)]` desde
`463,652`) y las **matas lejanas** que el leñador aparca (a 100+ bloques).

**Regla:** `VillageManager.tironHacia` — si el destino está a más de `ALCANCE_DE_LA_RUTA` (**40**, con margen sobre
los 56), al aldeano se le manda a un **tirón intermedio**: un punto a **28** (o 16, o 34) bloques **en dirección al
destino** que **tenga ruta** desde donde está (se prueba con `createPath`, buscando una casilla de pie en anillos de
3 por si el punto cae en una pared o un árbol); y si ninguno la tiene, a la **plaza del pueblo**, que está al alcance
desde cualquier parte de la aldea. **El aldeano sigue midiendo su llegada contra el destino de verdad**, no contra el
tirón: la faena no se adelanta (importante en el almacén, cuyas cajas se abren por distancia). El tirón se recalcula
solo cuando se llega al que tenía, así que no cuesta nada por tick.
**Medido** (MEDIR_MINERO, ~7 min): el minero pasa de quedarse en `512,68,667` (galería, `y=68`) a **bajar a la calle
y llegar al almacén** — `pos=515,63,662 destino=517,63,666` → `pos=516,63,663 | Cargando material` — y los avisos de
`no consigue llegar a 517,63,666` se quedan en **1** (el primero, antes del tirón). Ver
`tools/arnes/medidas-minero.txt`, apartado 2.

**Y LO QUE APARECIÓ DEBAJO (otra cosa, y es del jugador)**: ya en el almacén, el minero **espera** porque **no hay
pico** — `El minero: no hay pico en el almacen (lo forja el herrero de herramientas: 3 lingotes de hierro y 2 palos):
espera` (×7), su mano va `pico=SIN PICO(0/0)` y el almacén solo tiene **3 pepitas de hierro** (ni un lingote), así que
el herrero solo saca palos. Es el **cebo del pico** roto (sin pico no hay mineral → sin mineral no hay lingotes → sin
lingotes no hay pico): la aldea nace con su remesa (1 pico de hierro + 6 lingotes), pero en esta partida ya se gastó.
Como el pedernal de I101, eso es **del jugador**: con un pico (o 3 lingotes de hierro) en el almacén, el minero vuelve
a la mina. **Y desde I106 ya no hace falta que sea de hierro** (el herrero se forja el de madera solo).

### I106 · El pico del minero se forja del MEJOR material que el almacén pueda pagar

Lo pidió el jugador al ver el cebo del pico (I105): *"Que haga un pico de madera, y luego que piedra y luego hierro y
así sucesivamente"*.

**Regla** (`VillagerSmithGoal.recetaDePico`, que es lo primero en la lista del herrero de herramientas): se prueban de
**mejor a peor** —<b>diamante</b> (3 diamantes), <b>hierro</b> (3 lingotes), <b>piedra</b> (3 adoquines) y <b>madera</b>
(3 tablones), todos con 2 palos— y se forja **el primero que se pueda pagar** y del que no haya ya `OBJETIVO_PICOS`
(2) picos de ese nivel **o mejor**. Eso da las dos cosas que pidió:
- **arranca con lo que haya** (tablones y palos hay siempre: los trae el leñador) y **sube solo**: con el de madera el
  minero saca adoquín, con el de piedra saca hierro, con el de hierro saca diamante y con el de diamante, todo;
- **mejora** lo que hay: con dos picos de madera y hierro en el almacén, forja el de hierro.

**El oro queda fuera a propósito**: no sube de nivel sino que **baja** (un pico de oro no puede con el hierro) y el
minero no lo acepta (`VillagerMinerGoal.recoger` pide madera, piedra, hierro, diamante o netherite), así que forjarlo
sería dejar en el almacén un pico que nadie usa.

**Medido** (MEDIR_MINERO, copia de su partida, ~13 min, con el almacén sin picos ni lingotes como el suyo):
`El herrero de herramientas: Forjo un pico de madera` **×3**; el minero pasa a `pico=minecraft:wooden_pickaxe(1/59)`,
**vuelve a la mina** (`dCara` 23,56 → 10,31) y **vuelve a cavar** (`pasos=16` → `19`, con adoquín en el zurrón); al
gastarse el pico (59 usos) el herrero forja otro. Ver `tools/arnes/medidas-minero.txt`, apartado 3.

### I107 · La despensa se come VARIADA: no hay comida que no se coma

Lo pidió el jugador: *"revisa que todos los aldeanos coman toda la comida que se produce (zanahorias, betabel,
etc.)"*.

**Auditoría** (leída en el código, no supuesta): `VillagePantry.comida()` **cuenta bien** todo lo que produce el
pueblo (pan, carne cruda y cocinada, patata asada, huevo estrellado, vegetales y trigo) y el censo del latido llega a
`FENCE_RADIUS + 44` = **106** bloques, así que el que trabaja fuera del muro (el ganadero del anexo, a 43-57, el
leñador, el minero) **también come**. En la corrida de medida: **0 avisos de hambre** con 19 aldeanos.

**Pero había comida que no se comía nunca** (tres agujeros de verdad):
1. **Los HUEVOS ESTRELLADOS**: `comida()` los cuenta, pero `sacarComida` no los tenía en ninguna lista → se quedaban
   en la despensa para siempre (en su partida había **52**). Ahora entran en el grupo de lo cocinado.
2. **LOS VEGETALES**: el orden era fijo (pan → cocinado → vegetales → crudo → trigo) y, como el cocinero repone el pan
   tan rápido como se come, las zanahorias/patatas/betabeles **no se tocaban**: medido, en doce minutos subieron de
   **103 a 225** con la despensa entre 500 y 1140 puntos y el pan siempre a cero.
3. **LA PATATA ASADA** no cruzaba del almacén a la despensa (faltaba en el filtro del granjero, `VillageFarmGoal`):
   se contaba como comida pero se quedaba en el almacén.

**Regla:** `sacarComida` come **de lo que MÁS SOBRA** — el grupo con más puntos por encima de su reserva
(`RESERVA_PAN` 4, `RESERVA_COCIDA` 4, `RESERVA_VEGETAL` 16, `RESERVA_CRUDA` 8, y el trigo con
`RESERVA_DE_TRIGO_PARA_CRIAR` 4) — y, si ningún grupo tiene exceso, del orden de siempre: **la reserva es una
preferencia, no un candado** (la aldea no se queda sin comer teniendo comida). Así la despensa se mantiene **variada
y sin montones**: lo que se produce se come y lo que queda es la reserva del cocinero y del ganadero.
**Medido** (MEDIR_HUERTA, ~9 min): las verduras **bajan** (`207 → 191`; antes solo subían), el pan ya no se come al
instante (`0 → 36` en el cofre) y el trigo se acumula menos (300 semillas → 0, al compostero). Ver
`tools/arnes/medidas-huerta.txt`, apartado 8.

### I108 · La arboleda del pueblo se planta UNA vez (el que replanta es el leñador)

Lo pidió el jugador dentro de la misma auditoría: buscar **redundancias** entre los goals y el latido.

**Regla:** `VillageGenerator.asegurarArboleda` (que se llama **en cada latido**) solo planta si la arboleda está **a
cero**: es el **arranque** de una aldea que nace sin bosque (una islita, un desierto, una llanura pelada) y la red de
seguridad si algún día se queda sin nada. Antes rellenaba **gratis** cualquier hueco vacío en cada pasada, así que al
**leñador** le reponían el plantón en cuanto talaba un árbol del pueblo: su ciclo (semilla → plantón del almacén →
árbol → troncos) quedaba de adorno y **la madera salía de la nada**.
**Medido:** `arboleda del pueblo plantada` **0 veces** en la corrida (antes salía en cada pasada), y el leñador siguió
talando y **entregando al almacén** (`el lenador guardo 15/16 cosa(s) de su oficio`).
**Y lo que NO es redundancia** (comprobado con grep, para que quede escrito): las **antorchas** las hace y las coloca
**solo el minero** (el recolector solo las recoge del suelo y el latido no toca antorchas: las farolas del pueblo son
*lanterns* de la construcción); el **pan** lo hornea solo el cocinero (el granjero lo transporta y `feedVillagers` deja
un pan para la cría); los **tablones y palos** los hace solo el herrero de herramientas; y **fundir mineral** lo hacen
el minero (su horno) y el herrero (su fragua) **a propósito** (cada uno con lo que tiene a mano; el herrero es la red
de seguridad de lo que quede en el almacén).
**Queda apuntado (redundancia real, sin tocar):** el **`JOB_SITE`** lo escriben **dos sitios** — el latido
(`reclamarEstacionesDelPueblo`) y los propios goals (`reclamarElPuesto` del minero y del herrero, que además cogen el
ticket del POI). Hoy conviven porque los goals aceptan el puesto que ya tienen, pero lo suyo es que el latido reparta y
los goals solo lean.

### I109 · El CARBÓN VEGETAL vale como carbón (y sin carbón el minero no se queda en bucle)

Lo preguntó el jugador: *"el carbón para hacer antorchas se puede hacer quemando logs en el furnace, ¿no?"*. **Sí** (1
tronco → 1 carbón vegetal, la receta de vanilla) y hacía falta, porque el pueblo **no producía carbón**: las antorchas
se hacían **solo con `Items.COAL`** y el único carbón era el de una veta (o el que trajera el jugador).

**Regla:** `VillageStorage.esCarbon` vale para **carbón y carbón vegetal** (la antorcha de vanilla acepta los dos) y
el **minero**, en el taller de su caseta, **quema un tronco en el horno** cuando no le queda carbón y va justo de
antorchas; la leña la trae el leñador al almacén y él se la lleva cuando la necesita (el `recoger` la pide también
para esto).

**Y EL ARNÉS CAZÓ UN BUCLE** (con una siembra nueva, `vaciarElCarbonDelAlmacen`): sin carbón en el cofre,
`leFaltaDelAlmacen()` pedía carbón y palos para las antorchas, no los encontraba **nunca** y el minero se quedaba
**yendo y viniendo del almacén** — **59 líneas** `yendo: Yendo al almacen (Cargando material -> 517,63,666)` y la mina
**parada en el paso 19** (no llegaba al taller, así que tampoco podía fabricarse el carbón). Arreglado: **la leña
también cuenta** como material de antorchas (con un tronco se fabrica el carbón vegetal), así que una ida al almacén le
deja completo y se va a trabajar.
**Medido** (MEDIR_MINERO con el carbón del almacén vaciado, ~6 min): `quema un tronco en el horno y saca 1 de carbon
vegetal (para las antorchas)` **×2**, `hace 4 antorchas con un carbon y un palo` **×2**, su zurrón pasa por
`1x charcoal` → `8x torch`, y **vuelve a cavar** (`caracol paso 17` y `paso 18`). Ver
`tools/arnes/medidas-minero.txt`, apartado 4.

### I110 · El leñador remata los troncos que quedan COLGANDO (donde tala y en el monte cerca de él)

Lo pidió el jugador con una captura de troncos en el aire junto a la muralla: *"¿por qué el leñador no quita todos los
logs flotantes justo debajo del bosque donde tala? debería poder hacer eso"*.

**Lo que había** (escaneado su guardado con `tools/arnes/troncos_flotantes.py`, que aplica la misma prueba que el mod:
tronco de pie, sin nada construido pegado y que **no llega al suelo por troncos**): **216 restos**, de los cuales
**0 en la arboleda del pueblo** (ésos ya los limpiaba `buscarRestoColgando`) y **96 en el monte**, donde tala, que no
limpiaba **nadie**. Los "de dentro" que salen en ese escaneo son en su mayoría **estructuras** (los postes del
cobertizo del almacén, los marcos de la mina) — y por eso la búsqueda de restos **solo** miraba la arboleda: fuera no se
puede distinguir un resto de un poste del pueblo.

**Regla:**
- **Alrededor del tocón** (`limpiarAlrededorDelTocon`): después de cada tala se barre una caja de `RADIO_LIMPIEZA` (12)
  de ancho y desde la cota − 2 hasta la cota + `ALTURA_MAX` + 6, y se rematan los troncos colgando que haya dentro. Es
  lo que limpia "justo donde tala".
- **En el monte, cerca de él** (`buscarRestoEnElMonte`): además del barrido de la arboleda, se buscan restos en un
  radio de 20 alrededor del **propio leñador**, y **solo fuera de la muralla** (y como mucho a `FENCE_RADIUS` + 40 del
  centro): según va andando por el monte los va rematando. Se deja fuera **todo** lo de dentro —la muralla es de
  troncos de pie, y los postes de las casetas— para no desmontar nada construido.

**Medido** (MEDIR_LENADOR, con dos troncos flotantes colgados a mano junto a un árbol de la arboleda): el leñador taló
y `remató 2 tronco(s) que quedaban colgando alrededor del tocón 437,63,616`; el escaneo del mundo después de la corrida
da **216 → 216 flotantes con 3 árboles talados** (con el radio de 8 de la primera prueba quedaba 1 nuevo por corrida,
o sea que el de 12 sí alcanza lo que deja la tala) y **ni una** de las columnas de dentro cambió: el poste del almacén
en `(515,64,667)` sigue (1 → 1) y los **66** marcos de la mina a y=57 siguen (66 → 66). Ver
`tools/arnes/medidas-lenador.txt`.

**Y SE CAZÓ DE PASO (pendiente)**: en la primera corrida el leñador **no taló nada** porque se quedó **pegado a la
muralla** (`527,63,672`, r = 62, justo encima del muro) intentando llegar al almacén (`no consigue llegar a
517,63,666`); con la madera en el zurrón no hay faena. El problema es **el camino desde fuera de la muralla al
almacén** (familia de I103/I105) y queda apuntado para la vuelta siguiente. → **Resuelto en I112.**

### I111 · El minero no se queda en bucle con el zurrón lleno de RECADOS

Lo reportó el jugador con dos capturas: *"¿por qué el minero aparece como trabajando dentro de su choza pero realmente
no hace nada? Bajé a su cantera pero no ha terminado de hacer un hoyo ni nada, se quedó como con madera... aparece el
minero sólo entrando y saliendo de su choza pero no va a trabajar"*.

**La causa (un livelock de verdad)**: `hayQueSubir()` daba por "zurrón lleno" tener **dos huecos libres o menos** y
subía a entregar; pero el minero lleva **siempre** encima sus recados —pico, tablones, palos, carbón y leña: **seis o
siete huecos de los ocho**— y `entregar` era **todo o nada**: de esos no soltaba ni uno. Así que subía, no entregaba
nada, volvía, y volvía a estar "lleno": **entrando y saliendo de la caseta para siempre, sin cavar una celda** (y el
zurrón lleno de madera, que es justo lo que se veía en la captura).

**Regla:**
- `hayQueSubir()`: el zurrón lleno **solo cuenta si puede vaciarlo** (`hayParaEntregar()` = llevar **de sobra** de
  algo, aunque sea de los recados).
- `entregar()`: ya no es todo o nada; deja en el almacén **lo que lleva de sobra** de cada cosa según
  `cuantoSeQueda()` —pico **2**, tablones **16**, palos **8**, antorchas **16**, carbón **8**, leña **2**— y lo que no
  le quepa al almacén se queda en el zurrón (no se tira).

**Medido** (MEDIR_MINERO, copia de su partida, ~14 min): el zurrón pasa de **6-7 huecos de recados a `1x stick`**; el
minero **baja a la mina** y `faena: Picando` (`caracol paso 17` y `paso 18`, `pasos` de **16 → 19**), y sus viajes de
subida son para **entregar de verdad** (pedernal, carbón, antorchas y lo sacado: `2x` cada uno). El bucle de
"entrando y saliendo" desapareció. Ver `tools/arnes/medidas-minero.txt`, apartado 5.

### I112 · Para cruzar la muralla se va POR EL PORTÓN (y el atasco se mide contra el paso, no contra el destino)

Venía apuntado de I110: el leñador se quedaba **pegado a la muralla** en `527,63,672` intentando llegar al almacén
(`517,63,666`, a **11 bloques**) y, con la madera en el zurrón, no talaba nada. La ronda anterior le puso el tirón
(I105/I103) y **no lo arregló**; medido, el log se llenaba de `no consigue llegar a BlockPos{x=517, y=63, z=666}`.

**La causa, medida con los bloques del guardado** (`build/ruta_atasco.py`, la misma regla de *casilla de pie* que usa
el juego, sobre su partida): **la ruta existe**, pero es un **rodeo de 68 pasos que empieza yendo al ESTE** —
`527,672 → 533,672 → 533,646 → portón 532,646 → 531,646 → 519,647 → 517,651 → … → 517,666`. La muralla es un anillo
de troncos de radio 62 con **cuatro portones cardinales y nada más** (`VillageGenerator.entrance`), así que el camino
al almacén desde fuera pasa por el portón este. Y ahí estaba el fallo de verdad: **el goal mide el atasco con la
distancia en LÍNEA RECTA al destino**, que durante el rodeo **crece** → a los 160 ticks (8 s) se rendía, apuntaba el
almacén como punto fallido (I33) y volvía a empezar: **22 rendiciones y 0 entregas** en una corrida.

**Regla:**
- `VillageManager.pasoParaCruzarElMuro` (y `esDeDentroDelMuro`): si el aldeano y el destino están en **lados distintos**
  del anillo (radio `FENCE_RADIUS` medido en X/Z), el punto al que se camina es **la casilla de paso del portón del lado
  al que se va** (la de dentro o la de fuera). Se elige el portón que **menos rodeo** pide —distancia al aldeano +
  distancia al destino— y se exige que esa casilla **se pueda pisar** (`esCeldaDePie`); si ninguno de los cuatro vale,
  devuelve `null` y todo queda **como estaba** (la regla nunca empeora). Va **primero** en `tironHacia` y en
  `tironConMemoria`, así que la usan **los tres goals que llevan cosas al almacén**: el leñador (fase ENTREGAR), el
  recolector (`VillagerPickupGoal`, destino ALMACÉN) y el minero. *(Ojo con la palabra: "pierna" en estos apuntes
  (I38) es un **tramo de un mismo viaje** —la ida y la vuelta, o la puerta y la mata—. Aquí no son tramos de un
  viaje, son **tres aldeanos con su propio goal**: por eso no se llama pierna.)* Mientras cruza se
  apunta el portón en los datos del aldeano **solo para no repetir el aviso** en el log (`DevilRpgPorton`).
- **El atasco se mide contra el paso, no contra el destino**: `VillagerLumberjackGoal` y `VillagerPickupGoal` guardan
  `puntoDePaso`; cuando cambia (portón → almacén, al cruzar) la cuenta de progreso se **reinicia**, así que el rodeo
  cuenta como avance y la llegada se sigue midiendo contra el destino (la faena no se adelanta).

**Medido** (MEDIR_LENADOR, copia de su partida, 280 s de juego; el arnés ahora planta al leñador **en el atasco mismo**
—`CENTRO.offset(57,0,26)` = `527,63,672`— con 16 troncos en el zurrón):

| concepto | antes | ahora |
|---|---|---|
| `no consigue llegar` al almacén `517,63,666` | **22** | **0** |
| entregas del leñador en el almacén | 0 | **1** (16 troncos) |
| árboles talados en la corrida | 0 | **2** (7 y 6 troncos, con sus remates) |
| arboleda al final (t=5600) | — | 6 árboles, 6 plantones, **0 huecos** |

El log lo enseña paso a paso: `Llevando la madera va al otro lado del muro (517, 63, 666): cruza por el porton
531, 63, 646` → `destino=531,63,646` (va al portón) → a los 200 ticks `destino=517,63,666` (ya cruzó) → `Guardo 16 de
lo suyo` y `el lenador guardo 16 cosa(s) de su oficio en el almacen`. Los **6** atascos que quedan en la corrida son de
**otros** aldeanos y en otros sitios (bancales y la mina), **ninguno** en el almacén. Ver
`tools/arnes/medidas-lenador.txt`, apartado 6.

### I113 · El APARCADO (I33) no puede dejar al minero sin pico ni a la mina sin cavar

Lo preguntó el jugador después de I112: *"¿ya corregiste el problema del minero?"*. La respuesta medida ese mismo día:
I111 había arreglado **un** livelock del minero, pero **quedaba otro**, y era el que se ve en juego.
Ojo: el arnés ya lo decía en la primera corrida (`goals=[]`, `pico=SIN PICO`, `pasos` parado) y lo tomé por un efecto
del arranque; **no lo era**.

**La causa (medida)**: el **sitio aparcado** de I33. El minero **no tiene otra faena a la que pasar** —su destino sale
del **plan de la mina** (una celda del caracol, que es una sola) o es el **almacén**, que es su **única fuente de pico y
de recados**—, así que un aparcado de **5 minutos** no lo deja "seguir con lo demás": lo deja **plantado en la caseta**
con la etiqueta `Trabajando` y `goals=[]`. Medido con el arnés volcando todo lo que mira su `canUse` (turno, comida,
aparcado con su hora y la lista completa de goals):

```
[Arnes] MINERO-ESTADO t=40  descansando=false hambre=false comida=1258
        apoyo=BlockPos{x=517, y=63, z=666} aparcado=true aparcadoHasta=129790 gameTime=127118 picoEnMano=false
[Arnes] MINERO t=2400 pos=501,63,616 pico=SIN PICO(0/0) zurron=[0:2xstick] goals=[]     <- 120 s sin nada que hacer
[Village] El minero: pico nuevo: minecraft:wooden_pickaxe                              <- al caducar el aparcado
[Village] El minero: caracol paso 17 en 499, 53, 620
[Village] e9329a25-... no consigue llegar a BlockPos{x=499, y=53, z=620}: lo deja por 5 min
[Arnes] MINA t=2400..7920 pasos=18/240                                                 <- y 220 s congelada
```

**Regla** (`VillagerMinerGoal.comprobarDestino` + `VillageManager.olvidarPuntoFallido`): si el sitio al que tiene que ir
el minero está aparcado, **se olvida el aparcado** y se reintenta tras `ESPERA_TRAS_APARCADO` (600 ticks = 30 s). No
vuelve al bucle de empujar la pared —entre intento e intento está la espera, que es justo lo que el aparcado evitaba—
pero tampoco se queda media faena muerto.

**Medido** (MEDIR_MINERO, misma partida y mismo montaje, antes y después):

| concepto | antes | ahora |
|---|---|---|
| pico en la mano | t=3200 (160 s) | **t=1620 (81 s)** |
| caracol paso 17 / 18 | solo el 17 | **17 y 18** (en 3 s) |
| `pasos` (progreso real en el mundo) | 17 → 18 | **16 → 19** |
| seguido con `goals=[]` (almacén aparcado) | 140 s | 30 s (la espera) |
| `pasos` congelado (celda aparcada) | 220 s | **0** |
| después de entregar los 24 objetos | se quedaba | **vuelve a bajar a picar** |

**Lo que NO queda arreglado, dicho claro**: si una celda del caracol es **de verdad** inalcanzable, el minero ahora
**reintenta cada ~30 s** en vez de esperar 5 minutos, así que el log **repite** `no consigue llegar a <celda>`: eso es a
propósito (así se ve que hay algo real que arreglar en el mundo), pero ese trozo de mina **sigue sin cavarse** hasta que
el mundo cambie. Ver `tools/arnes/medidas-minero.txt`, apartado 6.

**Y PARA PODER ARREGLARLO HAY QUE SABER POR QUÉ** (lo pidió el jugador: *"¿cómo que no puede alcanzar una celda? ¿en
qué casos no podría?"*). El log decía **qué** sitio fallaba pero no **por qué**, así que `marcarPuntoFallido` ahora apunta
también **desde dónde** se rindió el aldeano y **si el planificador le da ruta** (`createPath`) hasta ese sitio. Es la
distinción que hacía falta: con `ruta=NO` hay algo en el **mundo** que tapia el camino (una losa que falta, agua, un
marco en medio: eso se arregla construyendo) y con `ruta=SI` la ruta **existe** y el problema es otro (el aldeano no
consigue **caminarla**).

**Medido en la corrida siguiente**: **26 rendiciones, las 26 con `ruta=SI` y ninguna con `ruta=NO`**. Es decir: hoy
**no** hay ningún sitio del pueblo al que el juego no sepa ir; los aldeanos se quedan a medio camino. Casos concretos:

```
[Village] e9329a25-… no consigue llegar a BlockPos{x=499, y=53, z=619} desde 501, 55, 621 (ruta=SI)   x3  <- el minero
[Village] d9c02179-… no consigue llegar a BlockPos{x=423, y=63, z=671} desde 451, 63, 669 (ruta=SI)       <- y OTROS TRES
[Village] bbd17505-… desde 437, 63, 653 · 6fceef7a-… desde 469, 63, 677 · 40186f40-… desde 430, 63, 648    GUARDIAS
[Village] 3209085d-… no consigue llegar a BlockPos{x=555, y=76, z=692} desde 553, 68, 690 (ruta=SI)       <- el leñador
```

Dos cosas que salen de ahí y quedan apuntadas: (1) el minero se rinde **a 3 bloques** de la celda, siempre en el mismo
sitio (`501,55,621`), dentro de un túnel de **una celda de ancho** con marcos de madera y escalones de medio bloque —
la ruta existe pero es difícil de **andar**; (2) **cuatro guardias distintos** se rinden en el **mismo** punto
(`423,63,671`) viniendo de cuatro sitios distintos: ése es un sitio del pueblo que hay que mirar.

### I114 · A UN BLOQUE NO SE CAMINA: se camina a una CASILLA DE PIE

Lo pidió el jugador: *"¿cómo que no puede alcanzar una celda? ¿en qué casos no podría? ¿no sería mejor que con el
adoquín que ha juntado se haga un camino o escaleras?"* y luego *"sigue con el punto 1 y 2 y revisa por qué los guardias
y el leñador se paran"*.

**El mecanismo común, medido** (con el log de I113 mejorado: `canReach` + los bloques de alrededor): cuando el
**destino de un goal es un BLOQUE** —un tronco, la pieza de una celda del caracol, la mata de trigo— el planificador
devuelve una ruta de **1 nodo** (`ruta=1 nodos hasta <su propia casilla> alcanza=NO`) y el aldeano **no da un paso**:
se queda donde estaba hasta rendirse. Medido: el leñador con `destino=jungle_log` se rindió a **32 bloques** sin
moverse; el minero con `destino=cobblestone_slab` a **3 bloques** de su propia celda. Y al revés: con el destino en
**aire con suelo firme** (`destino=air`) el planificador **sí** da ruta. (Cuando el bloque está cerca y en línea recta
el fallo no se ve, porque el cerebro del aldeano empuja hacia el objetivo y llega; por eso esto llevaba tanto tiempo
sin cazarse.)

**Regla:**
- **El que camina va a una casilla de pie; el que trabaja apunta al bloque.** En el leñador, `celdaDePieParaAlcanzar`
  busca la casilla (aire a los pies y a la cabeza, suelo firme) desde la que el tronco queda a `REACH` o menos, y se
  camina a **ésa** (cacheada por destino: buscarla son 100 celdas). Y un resto que **no se alcanza desde ninguna
  casilla de pie** (un tronco a 13 bloques del suelo) **no se elige**, en vez de mandarlo a empujar el aire.
- En el minero, la celda del caracol es la del **bloque de la pieza**; la de estar de pie es la de **encima**
  (`celdaDePieDelCaracol`). Se camina a ésa y se pica la otra.
- El **punto de la ronda de los guardias** no basta con que "quepa de pie": la ronda es un círculo de radio
  `RADIO_RONDA` que **atraviesa los edificios**. Se medían guardias rindiéndose **dentro de un recinto amurallado**
  (`423,63,671`, con `29 nodos hasta 423,63,673 alcanza=NO`) y en una casilla que era **`cave_air`** (un hueco de cueva
  a la altura del pueblo, `509,63,650`). Ahora `puestoLibre` exige **llegar** (`createPath(...).canReach()`), prueba un
  abanico más ancho (hasta 6 bloques) y, si no hay nada, manda al guardia a la **casilla de la calle** (la plaza).

**Medido** (MEDIR_MINERO, misma partida, antes y después):

| concepto | antes | ahora |
|---|---|---|
| rendiciones en la corrida | 26 | **16** |
| rendiciones del **leñador** | 6 (troncos de selva/roble en el monte, a 30+ bloques) | **0** |
| `destino` del minero | `cobblestone_slab` (un bloque) | `air` (casilla de pie) |
| `pasos` de la mina | 19 congelado | **18 → 19** |
| talas del leñador | — | `talo 5 tronco(s)` |

**LO QUE QUEDA SIN ARREGLAR (medido, no supuesto):**
1. **Guardias: 5 rendiciones** en el mismo recinto (`423,63,671`). La prueba de "llegar" **depende de dónde esté el
   guardia** al elegir el punto: desde `442,63,658` da `alcanza=SI` (32 nodos) y desde `486,63,648` da `alcanza=NO`
   (42 nodos que acaban en `445,64,664`, subido a un tejado). Siguiente paso: probar el punto **desde una referencia
   fija** (la calle), no desde donde el guardia esté en ese momento.
2. **El minero se rinde 3 veces** en `501,55/56,621`, ya con destino de aire: **no hay ruta desde ahí**
   (`ruta=1 nodos ... alcanza=NO`). Está **encajado fuera del túnel** y no puede volver a entrar; le toca el mismo
   patrón del tirón (I105/I112): si la ruta directa **no alcanza**, ir a un sitio del que sí haya ruta (la caseta o la
   boca) y replanificar desde allí.
3. **Granjeros**: `596e09a8` se rinde en `484,63,658` **desde `484,63,659`** (¡a UN bloque!) con `destino=wheat` y
   `alcanza=SI` — es el mismo patrón (la mata es un bloque) y le toca el mismo arreglo.

### I115 · El que se queda ENCAJADO sin ruta vuelve a un sitio del que SÍ haya ruta (y el punto de la ronda es de la aldea, no de cada guardia)

Segunda vuelta de I114, con la misma medida (MEDIR_MINERO, `arnes`, 24-25-sep-2026):

**Lo que se arregló y está medido:**

- **El minero encajado**: se rendía 3 veces desde `501,55,621` con `ruta=1 nodos ... alcanza=NO` — desde ahí **no hay
  ruta ninguna** (se había metido fuera del túnel). Ahora, cuando lleva `TICKS_PARA_VOLVER_A_LA_CASETA` (120) sin
  acercarse, **vuelve a la caseta** (de la que consta que se baja andando el caracol) y replanifica desde arriba:
  `El minero: yendo: Volviendo a la caseta (encajado)` → `pos=503,57,621 → 507,60,617 → 508,63,616` → y sigue picando.
  **Medido: 3 → 0 rendiciones del minero**, y la mina sigue avanzando (`pasos` 18 → 19 y de ahí no se queda).
- **La ronda no entra en un recinto**: el punto de la ronda se elige por geometría (un círculo de radio
  `RADIO_RONDA`) y caía **dentro de un recinto amurallado** (`423,63,671`). Ahora `puestoLibre` exige, además de caber
  de pie, **estar en LA CALLE** (un recorrido en anchura desde la plaza sobre casillas de pie: `calleDeLaPlaza`) y
  **llegar** (`createPath(...).canReach()`).
- **Un punto malo lo paga la ALDEA, no cada guardia**: medido, **seis guardias distintos** se rindieron en la misma
  casilla (tres de ellos en el **mismo segundo**, porque ya iban de camino). El aparcado de I33 es por aldeano, así que
  cada uno pagaba el fallo por su cuenta: ahora el primero que se rinde **avisa a los demás** (`PUNTOS_MALOS_DE_LA_RONDA`,
  5 min, por aldea).
- **No es atasco si hay ruta que llega**: en las rendiciones de guardias el log decía `alcanza=SI` **justo al
  rendirse** — se rendían **en mitad del rodeo** (rutas de 30-44 nodos que empiezan alejándose del puesto), que es la
  lección de I112 pero en el guardia. Ahora, a los `TICKS_PARA_PREGUNTAR_SI_HAY_RUTA` (60) ticks de "no me acerco" se
  pregunta **una vez** si hay ruta que alcanza (`VillageManager.hayRutaQueAlcanza`) y, si la hay, **se sigue andando**.

**Medido (misma partida, antes → ahora):**

| concepto | antes | ahora |
|---|---|---|
| rendiciones en la corrida | 26 | **10-13** |
| del **minero** | 3 | **0** |
| del **leñador** | 6 | **0** |
| de **guardias en `423,63,671`** | 6 | **6-7** (uno por guardia en la primera pasada; después, 5 min en paz) |

**LO QUE SIGUE SIN ARREGLARSE, y por qué** (esto es lo importante de esta vuelta): el recinto de `423,63,671` **pasa
todas mis pruebas** —cabe de pie, tiene cielo abierto, y mi recorrido en anchura **entra por la puerta** (una puerta
ABIERTA tiene forma de colisión vacía, así que cuenta como casilla de pie)—, pero el **planificador del juego** unas
veces le encuentra la puerta (`ruta=30 nodos hasta 423,63,671 alcanza=SI`) y otras no (`ruta=38 nodos hasta
430,63,666 alcanza=NO`), y las que fallan se quedan **en la pared de fuera**. O sea: el criterio del juego es
**inconsistente** para esa casilla y el mío no la distingue. Lo que queda por hacer es que la ronda **no elija puntos
dentro de ninguna construcción** usando el **plano** de la aldea (`blueprintState`, que ya existe), en vez de pruebas
locales: el plano sabe qué celdas son edificio y cuáles calle.

### I116 · El PLANO dice quién está dentro de un edificio (y a un tronco no se le busca la casilla: se descarta el árbol)

Tercera vuelta de I114/I115, y **ésta sí está medida como arreglo**:

- **La ronda no elige casillas de dentro de un edificio**: `VillagerGuardGoal.dentroDeUnaConstruccion` pregunta al
  **plano** (`VillageManager.blueprintState`): si la casilla tiene **3 o más vecinas** —las 4 de al lado y la de
  encima— que el plano quiere ocupadas, es un **hueco de dentro** y no calle. Medido con `build/plano_celda.py` sobre
  su guardado: la casilla `423,63,671` tiene **cinco** vecinas del plano (un **cofre**, una **diana** y tres
  **adoquines**) — es el hueco de dentro de un edificio del pueblo, y por eso el planificador del juego unas veces le
  encontraba la puerta y otras no. Una casilla de calle pegada a una pared tiene **una** vecina del plano: de ahí el
  umbral 3.
- **Al árbol que no se alcanza no se le manda a nadie**: el filtro de I114 estaba en los **restos** y en el monte, pero
  los árboles los elige `buscarArbol`; medido (3 rendiciones con `destino=jungle_log` en la corrida del 25-sep) volvían
  a colarse. Ahora `buscarArbol` **descarta** cualquier tronco sin casilla de pie desde la que se alcance
  (`celdaDePieParaAlcanzar`), y guarda esa casilla para caminar a ella.

**Medido (MEDIR_MINERO, misma partida; las tres corridas del día):**

| concepto | antes (I113) | tras I114 | **tras I116** |
|---|---|---|---|
| rendiciones en la corrida | 26 | 18 | **3** |
| de guardias en `423,63,671` | 6-8 | 8 | **1** |
| del leñador (troncos de selva) | 6 | 3 | **0** |
| del minero | 3 | 0 | **0** |
| `pasos` de la mina | 19 congelado | 18 → 19 | 18 → 19 |

**Lo que queda** (de las 3): dos son de **otro** aldeano (`21e951c8`) en `560,64,587` y `552,63,585` —fuera del muro, a
80+ bloques del centro— con `ruta=1 nodos ... alcanza=NO` (no hay ruta desde donde está: es el caso "el destino está
fuera y no se llega", no el de esta invariante), y **una** de guardia en `423,63,671` (el día que ningún candidato del
abanico pasa las pruebas y se cae al puesto pedido). Apuntadas, no arregladas.

### I117 · Al que REPARA también se le camina a una casilla de pie (y el portón vale para CUALQUIER faena)

Cuarta vuelta, sobre el **obrero** (`21e951c8` = Filomena, recolector con `DevilRpgBuilder`), que salió en el resumen de
I116: se rendía en `560,64,587` y `552,63,585` **sin moverse** de `521,63,612` y `519,63,609` (46 bloques), con
`destino=air` y `ruta=1 nodos ... alcanza=NO`.

- **`VillagerRepairGoal`**: se camina a la **casilla de pie** desde la que el hueco entra en el alcance
  (`sitioDeCamino`, cacheada por hueco: son 7×7×6 celdas). El **alcance se sigue midiendo al hueco** (es donde hay que
  poner el bloque) y el **atasco contra la casilla** a la que se va. **Medido: 2 rendiciones → 1**, y la que queda ya no
  es a 46 bloques: es un **vallado a 3 bloques** de ella (`517,63,639`, `oak_fence`) donde no hay casilla de pie cerca.
- **El portón vale para CUALQUIER faena** (extiende I112): el leñador se rendía **8 veces** en los árboles de **selva de
  fuera** (`547,65,703`, `546,65,684`, `548,67,694`...) estando él **dentro** del muro (él a 61 del centro, el árbol a
  96): la casilla de pie existe y se alcanza, **pero solo desde fuera**. Ahora `pasoDeCamino` cruza por el portón
  también en la fase de TALAR. **Medido: 8 → 0.**
- **La prueba del plano, a TODOS los candidatos** (no solo al punto ideal): el abanico de ±6 bloques alrededor del
  punto también cae dentro del edificio y por ahí se colaba.
- **Si no hay ningún candidato válido, el guardia se queda donde está** (antes se caía al puesto pedido, que es el de
  dentro del edificio).

**Y UN HALLAZGO QUE CAMBIA LA LECTURA**: las **6 rendiciones de guardias en `423,63,671`** que quedan **no son de la
ronda**. Cuando se rinde la ronda el guardia deja OTRA línea (`me salto el puesto y sigo la ronda`) y en estas corridas
no aparece; además la mayoría de esas 6 tienen `alcanza=SI` (hay ruta) y vienen de sitios distintos. Son de **otro goal
del mismo aldeano** —lo más probable, el de recoger cosas (`VillagerPickupGoal`), persiguiendo un objeto que cayó
**dentro** del edificio—. El log de `marcarPuntoFallido` **no dice qué goal se rindió**: eso es lo primero de la vuelta
siguiente (una línea: la etiqueta del aldeano, que cada goal ya escribe con `ponerActividad`), y luego decidir si el de
recoger debe perseguir objetos de dentro de las casas.

### I118 · El log de "no llegué" dice QUÉ GOAL se rindió (y con eso se supo que era la DIANA, no la ronda)

Quinta vuelta, y lo primero fue **poder preguntarlo**: `marcarPuntoFallido` ahora escribe también la **etiqueta** del
aldeano (nombre, oficio y **actividad**, que cada goal pone con `ponerActividad`). Sin eso el aviso solo traía el uuid y
no había forma de saber **qué** goal se rendía — que es lo que dejaba la duda de I117.

**Medido en la corrida siguiente** (MEDIR_MINERO) — y la respuesta **no era la que yo suponía**:

```
6fceef7a  423, 63, 671   -> Ubaldo (Guardia arquero · nv 1) / Yendo a entrenar
68287e43  423, 63, 671   -> Eufemia (Guardia espadachín · nv 1) / Yendo a entrenar
40186f40  435, 63, 626   -> Bibiana (Guardia espadachín · nv 2) / Patrullando la arboleda
689673d9  450, 63, 684   -> Saturnino (Granjero) / Entrando a la huerta
8a02cad0  526, 63, 642   -> Vicenta (Ganadero) / Cuidando el ganado
```

**No era la ronda ni el de recoger: es `Yendo a entrenar`.** Los guardias van a **la diana**, y la diana del pueblo
(`VillageGenerator.puestoDeEntrenamiento` → `423,63,671`) está **dentro de un edificio amurallado**: en el **plano** esa
zona tiene un **`target`** (la diana) y un **cofre**, con las paredes de adoquín alrededor. El guardia camina hacia la
diana —que es un **bloque**— y se queda en la pared de fuera.

**Intento que NO valió (y se retiró)**: se le puso al entrenamiento el mismo arreglo de I114/I117 (caminar a una casilla
de pie junto a la diana, descartando las que el plano marca dentro de un edificio). **Medido: no lo arregla** — siguen
6-7 rendiciones y ahora además con la línea propia del entrenamiento (`no llego a la diana … me vuelvo a la ronda`). La
razón es que la prueba del plano con umbral **3 vecinas** reconoce un rincón apretado, pero **no** el interior de un
recinto grande y abierto por arriba: las casillas de dentro tienen solo una o dos vecinas del plano. Y encima la
búsqueda de la casilla (100 candidatas × 6 consultas al plano, que es un recorrido lineal) corría **en cada tick**:
se retiró y el árbol quedó como estaba (compila y pasa el lint).

**Lo que queda por hacer, ya con el diagnóstico cerrado**: el problema **no es el caminante, es DÓNDE está la diana**.
O se coloca el puesto de entrenamiento en la calle (`puestoDeEntrenamiento` / la barraca), o el entrenamiento se salta
cuando la diana está techada/encerrada. Y para la prueba del plano, si se quiere usar en recintos grandes, hay que
cambiar el criterio: en vez de "3 vecinas", "¿está la casilla **dentro de la huella** de un edificio?" (mirando el
plano en un radio de 2-3), que es lo que distingue una plaza de un patio.

### I119 · Las dianas de la barraca van al PATIO (y el entrenamiento se mide contra el paso, no contra la diana)

Lo pidió el jugador: *"¿por qué no pones el puesto de entrenamiento en un campo abierto justo al lado de las
barracas? así haces las barracas de un solo nivel junto con sus camas"*. Hecho **lo primero** (el campo); lo del
un-piso queda pendiente y apuntado abajo.

**Lo que se ha movido:**
- `BARRACA_DIANA` y `BARRACA_DIANA_DOBLE` pasan del **interior** de la sala de armas (`422,671` y `426,666` en su
  aldea) al **patio del sur**: `424,674` (suelta) y `425/426,674` (la doble, apilada). El campo está **verificado
  libre** (`z 673..686` es tierra/ césped abierto, con el muro en la diagonal del oeste).
- `puestoDeEntrenamiento` pasa a ser **la casilla de pie delante de la diana** (`424,675`), en el campo.
- **Migración** `moverLasDianasAlPatioDeLaBarraca`: quita las dianas de dentro (**solo si ahí sigue habiendo un
  `TARGET`**: lo que ponga el jugador no se toca) y las pone en el patio (**solo si la celda está vacía**).
  **Idempotente** y **sin rehacer la barraca** (su testigo es el hogar, I15).
- **DÓNDE SE ENGANCHA IMPORTA**: puesta en el bloque de migraciones **no se ejecutaba** para su aldea (medido: 0 líneas
  y las dianas seguían dentro). Va **en la pasada que arregla la barraca ya construida en el sitio** —junto al barril y
  los faroles—, y ahí sí: `Barraca de 470,63,646: dianas al patio (3 fuera, patio puesto)` y el destino de los guardias
  cambia al patio (`424,63,675`).

**Medido, y lo que AÚN no está arreglado** (MEDIR_MINERO, varias corridas): los guardias **siguen rindiéndose 7 veces**
con `Yendo a entrenar`, ya **en el patio**. El log lo deja claro y descarta dos causas:

```
no consigue llegar a 424, 63, 675 desde 445, 63, 670 (ruta=23 nodos hasta 424, 63, 675 alcanza=SI;
    pies=air cabeza=air suelo=grass_block | destino=air encima=air) etiqueta="… / Yendo a entrenar"
```

El destino **es una casilla de pie del patio** y la ruta **sí alcanza** (`alcanza=SI`), pero el guardia **no avanza**.
Se le ha puesto el arreglo de I114 (caminar a la casilla, no al bloque) y el de I112/I115 (medir el atasco contra la
casilla a la que se va, no contra la diana) — y **sigue igual**, así que queda una causa por medir que no es ninguna de
esas dos: lo siguiente es registrar, en el momento de rendirse, **qué camino está siguiendo el cerebro** (su
`WALK_TARGET` y si la navegación tiene ruta viva), porque `createPath` dice que hay camino y el aldeano se queda quieto.

**Y ESO SE MIDIÓ, Y SON DOS CAUSAS DISTINTAS** (el aviso de `marcarPuntoFallido` lleva ya `cerebro=` y `nav=[…]`):

```
… no consigue llegar a 424,63,675 desde 513,63,657  (ruta=39 nodos hasta 476,63,661 alcanza=NO)
    etiqueta="Jacinto (Guardia espadachín · nv 1) / Yendo a entrenar"
    cerebro=517, 63, 666   nav=[10 nodos hasta 517, 63, 666 alcanza]      <- ¡va al ALMACÉN, no al patio!

… desde 458,63,665  cerebro=424, 63, 675  nav=[38 nodos hasta 424,63,675 alcanza]   <- lo correcto… y se rinde igual
… desde 459,63,649  cerebro=424, 63, 675  nav=[43 nodos hasta 427,63,675 NO alcanza] <- la ruta se queda 3 corta
```

1. **Alguien le pisa el destino al goal**: en el primer caso el cerebro del guardia apunta al **almacén**
   (`517,63,666`) mientras su goal cree que va a entrenar — el `WALK_TARGET` lo escribe el **cerebro del aldeano**
   (sus otras faenas, el paseo, la cama) y `caminarHacia` se lo pone cada tick, pero **no siempre gana**. Con el
   destino pisado, el goal no se acerca a la diana y se rinde a los 12 s.
2. **La ruta viva fluctúa**: cuando el cerebro sí apunta al puesto, la navegación unas veces trae una ruta que
   **alcanza** (38 nodos) y otras una que **se queda 3 bloques corta** (`43 nodos hasta 427,63,675 NO alcanza`). En la
   segunda, el aldeano empuja en línea recta y no avanza.

Las dos quedan medidas y **sin arreglar**; el arreglo que toca es el de siempre en este pueblo: si la ruta viva **no
alcanza**, ir primero a un sitio del que consta que se llega (la ronda/plaza) y replanificar — como hizo el minero
volviendo a su caseta (I115) — y, para el destino pisado, **reescribirlo también cuando el goal no está en su rama de
caminar** (o dejar de caminar y volver a la ronda en vez de quedarse midiendo contra una diana a la que ya no va).

**Y ARREGLADO, con la medida a favor**: en `entrenar`, a los `TICKS_PARA_COMPROBAR_SI_VA` (40) ticks de no acercarse se
comprueba **una vez** si de verdad va hacia el puesto —**ruta viva que alcance** y **cerebro apuntando al puesto**— y,
si no, **se vuelve a la ronda** (en vez de seguir 12 s empujando y aparcar la diana 5 min). **Medido: las rendiciones
`Yendo a entrenar` bajan de 7 a 2** (y el total de la corrida, de 13 a 9). Las 2 que quedan son del otro caso: ruta viva
que **sí** alcanza y el guardia, aun así, no avanza hasta el límite; queda apuntado.

### I122 · Las 2-3 rendiciones que quedan del entrenamiento: la ruta viva FLUCTÚA (intento medido y retirado)

Con el aviso ya detallado (`cerebro=` y `nav=[…]`) se volvieron a medir las que quedan (2-3 por corrida), y son **tres
sabores distintos**, todos con el destino en el patio y bien puesto:

```
… desde 498,63,656  cerebro=424, 63, 675  nav=[43 nodos hasta 461, 63, 664 NO alcanza]   <- la ruta viva se queda 40 corta
… desde 458,63,654  cerebro=460, 63, 654  nav=[sin ruta]                                 <- el cerebro apunta A OTRA PARTE
… desde 430,63,669  cerebro=424, 63, 675  nav=[16 nodos hasta 424, 63, 675 alcanza]      <- todo bien… y no avanza
```

O sea: **(1) la ruta viva fluctúa** —iba bien y el aviso la pilla corta—, **(2) el destino se pisa** de vez en cuando, y
**(3) hay bloqueo físico** (aldeanos, animales o el propio hueco) con la ruta buena delante.

**Intento que NO valió, y se retiró**: preguntar **en el momento de rendirse** si la ruta viva alcanza y el cerebro va
al puesto, y si sí **reiniciar el contador** (con tope de 3 reintentos) en vez de aparcar. **Medido: 2-3 → 3**, ninguna
mejora — y el propio log dice por qué: **cuando se rinde, la ruta casi nunca alcanza**, así que el reintento apenas se
dispara. El árbol quedó como estaba (compila y pasa el lint).

**Lo que queda apuntado para cuando se retome**: las tres causas piden cosas distintas — (1) y (2) son de **quién manda
en el `WALK_TARGET`** (el cerebro del aldeano escribe el suyo y `caminarHacia` no siempre gana: la solución sería
reafirmarlo y, si el goal no puede, volver a la ronda **sin** contar atasco mientras el destino no sea el suyo), y (3)
necesita que el puesto de entrenamiento **no esté en un paso estrecho**: se puede correr el puesto a una casilla del
patio con más aire (el campo es ancho: `z 673..686`) y volver a medir.

**Y (3) ERA ESO, pero por otra razón: LOS SEIS GUARDIAS IBAN A LA MISMA CASILLA.** El puesto de entrenamiento era
**una sola celda** para todos, así que se estorbaban entre ellos (y con los animales y los que cruzan el patio) con la
ruta buena delante — que es exactamente lo que decía el tercer sabor del log. Arreglado: `puestoDeEntrenamientoCacheado`
reúne **todas** las casillas de pie que alcanzan la diana, las ordena por cercanía y **cada guardia toma la suya por su
número** (`indice`), como el pueblo reparte ya los puestos de la arboleda y del corral (I4). **Medido: las rendiciones
`Yendo a entrenar` pasan de 2-7 a CERO** (y `no llego a la diana`, a cero también).

### I123 · La barraca de un piso EN SU PARTIDA: la palanca, y el testigo que estaba mal por el MATERIAL del escalón

Con el visto bueno del jugador se tiró de la palanca (`CURRENT_LAYOUT`, que además **exige el lint I9** al añadir
construcción). Lo que se hizo, y **lo que costó**:

- **`CURRENT_LAYOUT` 72 → 73**, con su entrada en la lista de versiones.
- **`vaciarLasArcasDeLaBarraca`**: antes de rehacerla, lo de **las dos arcas viejas** (a `nivel + BARRACA_PISO2_VIEJO`) se
  pasa al **almacén** con `VillageStorage.guardar`; rehacer una construcción **tira lo de dentro de sus cofres**.
- **Se despeja el volumen del piso viejo** (`nivel + 3 .. nivel + 8`, un bloque de más por lado) al construir: si no,
  quedan **flotando** el forjado, las paredes y el tejado viejos (I14).
- **Testigo nuevo** en `barracaConstruida`: el hogar del patio **más** la **ausencia del primer escalón**. No vale
  mirar el forjado: en el trazado de un piso **el tejado cae en la misma capa** (`nivel + 3`) que el forjado viejo.

**Y EL PRIMER INTENTO NO REHIZO NADA** (medido con el arnés y comprobando el mundo de la corrida): la barraca seguía
con las camas arriba (`y=67`) y la planta baja vacía. El motivo se cazó **leyendo su guardado**, sin gastar otra
corrida:

```
(428,63,670)  oak_stairs  facing=north half=bottom      <- la celda que miraba el testigo…
```

**Las escaleras de la barraca son de ROBLE, no de adoquín**, y el testigo preguntaba por `COBBLESTONE_STAIRS`: daba
"ya está" y no rehacía. Con **`instanceof StairBlock`** (cualquier escalera) sí. Es el fallo clásico de estas
invarianes: *un testigo no puede preguntar por un MATERIAL que no es el de la pieza*.

**MEDIDO, ya con el testigo bueno** (misma partida):

| comprobación | resultado |
|---|---|
| el log | `barraca de la milicia construida en 425,63,668` |
| **planta baja (`y=63`)** | **las 8 camas**, con el cofre al lado |
| `y=66` (tejado nuevo) | tablones cubriendo la huella, con su alero |
| `y=70` (donde estaba el piso viejo) | **completamente vacío** — nada flotando |
| censo de camas (`MEDIR_NOCHE`) | 19 aldeanos, **19 con cama**, 19 distintas, **0 compartidas, 0 sin cama**, 12 durmiendo |

**Detalle cosmético pendiente**: el aviso del log al construir sigue diciendo *"dos pisos: sala de armas abajo y 8
camas arriba"*; con el trazado de un piso esa frase ya no es cierta (es solo texto).

### I120 · La barraca de UN PISO con sus 8 camas: qué hay que tocar (estudio, aún sin hacer)

Lo pidió el jugador junto con lo del campo de entrenamiento: *"así haces las barracas de un solo nivel junto con sus
camas"*. Es la parte del encargo que **queda pendiente**, y esto es lo que hay y lo que costaría.

**Lo que hay hoy** (`VillageGenerator.barraca`, 9×9 con `BARRACA_RADIO = 4`):
- planta baja: la **sala de armas** (dos maniquíes de paca, el **arca** doble, el **hogar** del patio que es el
  **testigo** de `barracaConstruida`, y las dianas —ya movidas al patio, I119—);
- **escalera** de `BARRACA_ESCALONES = BARRACA_PISO2 = 4` escalones de medio bloque pegada al muro este, con su hueco
  en el forjado (el 4.º escalón lo borraba su propio hueco: migración 59);
- **forjado** (suelo del piso de arriba) a `yPiso2 - 1`;
- **dormitorio** a `yPiso2 = nivel + 4`: **8 camas** en dos filas de 4 contra las paredes largas, las **dos arcas**
  contra el muro oeste y **2 faroles colgados** del tejado;
- **tejado** a `yTejado` (con alero de un bloque) y dos faroles más en la puerta.

**Lo que pide el jugador**: las camas **abajo** y la barraca **de un solo nivel** (sin escalera ni forjado). En la
práctica:**`BARRACA_PISO2 = 0`** (o `1`, para que quepa un farol colgado del techo), las 8 camas en dos filas contra
las paredes largas de la planta baja, las arcas abajo, el tejado a `nivel + 3`, y los faroles colgando de ese tejado
(`BARRACA_FAROL_DY = 2`) en vez de a `nivel + 6`.

**La trampa, que es lo que hay que decidir antes de tocar** (y por eso esto es un estudio y no un parche): las
barracas que **ya existen** se reconocen por un **testigo** (el hogar). Cambiar el testigo hace que el mod **la vuelva
a levantar entera**, y los propios comentarios del mod avisan de lo que eso significa: *"rehacerla tiraría las camas y
lo de dentro de las arcas"* — y en la aldea del jugador esas arcas y esas 8 camas **ya están en uso** por los guardias.

Las dos salidas, y lo que cuesta cada una:

| salida | qué hace | riesgo |
|---|---|---|
| **(a) rehacer** (cambiar el testigo) | la pasada siguiente levanta la barraca de un piso | **pierde el contenido de las arcas** salvo que se vacíen antes; hay precedente para eso: `traspasarElArca` (mueve cofre a cofre sin tirar nada) y el almacén está al lado |
| **(b) migrar celda a celda** | bajar las 8 camas, quitar escalera y forjado, bajar el tejado y los faroles, sin rehacer la casa | conserva todo, pero es el trabajo más fino: cada celda a mano, con su idempotencia, y hay que **no dejar bloques flotando** (I14) al bajar el tejado |

**Lo que hay que medir cuando se haga** (con el arnés, sobre copia de su partida): que la barraca siga teniendo
**8 camas** y **ninguna flotando**, que los guardias **duerman** (la prueba de `MEDIR_NOCHE`, con el censo de camas:
*"aldeanos con cama, compartidas, sin cama, durmiendo"*), que no quede **forjado ni escalera** sueltos y que no haya
bloques huérfanos del piso viejo.

**Recomendación**: empezar por **(a) con las arcas vaciadas al almacén** (es la que respeta la forma en que este
proyecto ya cambia sus edificios: testigo + reconstrucción) y medir con `MEDIR_NOCHE`; si el censo de camas sale mal,
caer a (b).

### I121 · La barraca de un piso, HECHA para aldeas nuevas (`BARRACA_PISO2 = 0`)

El trazado de la barraca está **parametrizado** por `BARRACA_PISO2`, así que el un-piso salió con **tres cambios
pequeños** (comprobado: compila y pasa el lint):

1. **`BARRACA_PISO2 = 4 → 0`**: con eso salen solos las **8 camas y las dos arcas en la planta baja**, **sin escalera**
   (los escalones son `BARRACA_ESCALONES = BARRACA_PISO2`), **sin forjado**, y el **tejado a `nivel + 3`** con los
   faroles colgando de él (`BARRACA_FAROL_DY = BARRACA_PISO2 + 2 = 2`). La sala de armas (maniquíes y hogar) se queda
   donde estaba, en la planta baja.
2. **La fila de camas del sur vuelve a ser simétrica** (`-3, -1, 1, 3`): la cama del rincón sureste se había corrido al
   oeste **sólo** para no caer en la columna de la **escalera**; sin escalera, su sitio natural es el rincón… y ahí está
   el **maniquí sureste** (`+2,+2`), así que la cama va a `+3` (al lado, sin pisarlo). Sin este cambio, con un solo
   nivel la cama y el maniquí caían en la misma celda.
3. **El forjado sólo se coloca si hay piso arriba**: con `BARRACA_PISO2 = 0` el forjado caía en `nivel - 1`, que es el
   **suelo de piedra** de la sala de armas, y lo habría cambiado por tablones.

**Y PARA LA ALDEA QUE YA EXISTE** —que es la del jugador, con sus guardias durmiendo arriba— **lo dice el propio lint**
al compilar: *"Si el mundo ya construido tiene que rehacerse, sube `CURRENT_LAYOUT`"*. Ésa es la vía del proyecto, y
**no se ha hecho todavía** a propósito, porque es una migración grande sobre su partida y hay que decidirla con él:

- al subir `CURRENT_LAYOUT` la barraca **se vuelve a levantar** (con los dos pisos viejos encima hay que **limpiar el
  volumen de arriba**, o quedarían forjado y tejado **flotando**: I14) y hay que **vaciar antes las dos arcas** al
  almacén (con `traspasarElArca`, que no tira nada);
- y hay que **medirlo** con `MEDIR_NOCHE` (8 camas, ninguna compartida, nadie sin cama y todos durmiendo) más un
  escaneo de bloques huérfanos.

Así que el encargo queda: **el campo de entrenamiento y su migración, hechos y medidos** (7 → 2 rendiciones); **la
barraca de un piso, hecha para aldeas nuevas y probada a compilar/lint**; y **pendiente** (a) subir `CURRENT_LAYOUT`
con las arcas vaciadas y (b) cerrar las 2 rendiciones que quedan del entrenamiento.

### I124 · El aviso de "no llegué" dice también QUÉ GOALS van corriendo (25-sep-2026)

Lo pidió un jugador que vio a Leocadia *"caminando erráticamente, como balanceándose… como si hubiese dos tareas en
su cerebro en conflicto"*, y antes le costó ir al entrenamiento *"como si quisiese atravesar la barraca"*. Es el
mecanismo de **I119** (el `WALK_TARGET` lo escribe el **cerebro** y `caminarHacia` no siempre gana), así que lo
primero es **poder ver quién le manda**: el aviso de `marcarPuntoFallido` lista ahora **los goals que están
corriendo** (con la marca del que corre).

La regla que sale de ahí: **si en el aviso salen dos goals a la vez, el conflicto está en el selectores**; si sale
**uno solo**, el que le mueve es el **cerebro** (sus paseos y su "andar hacia donde mira"). Lo que en la primera
pasada quedó "por medir" es I125.

### I125 · No es atasco si el CEREBRO va a otra parte (y el conflicto, medido con los `goals=[...]`) (25-sep-2026)

Confirmado con los `goals=[...]` que salen ya en el aviso: los aldeanos que se rinden llevan **dos goals corriendo a
la vez** (`VillagerCollectGoal VillagerGateGoal`, `VillagerFarmGoal VillagerGateGoal`…) y **`VillagerGateGoal` es el
único que corre SIN NINGÚN FLAG** (`EnumSet.noneOf(Goal.Flag.class)`), así que el selector no puede serializarlo y va
**en paralelo** con la faena. Es literalmente lo que describió el jugador.

Lo que se hizo: `VillageManager.elCerebroVaA(villager, destino)` pregunta si el `WALK_TARGET` del cerebro es el del
goal, y `VillagerGuardGoal` —si no lo es— **reafirma el destino y no cuenta atasco** (con tope de **3
reafirmaciones** por destino, para que un mundo que de verdad no deja acabe rindiéndose y volviendo a la ronda).
**Medido**: no queda **ninguna** rendición de guardias; las que quedaban eran de una recolectora y un granjero, las
dos con el par de goals de arriba. Y quedaba apuntado lo que cierra I126: **que el goal de los portones no pelee**.

### I126 · LA MINA SELLADA: qué la tapaba (medido celda a celda) y las cuatro cosas que la abren (26-sep-2026)

Lo pidió el jugador: *"el minero no está bajando y está sellada la entrada"*. Aquí está **medido**, con el
instrumento nuevo (`tools/arnes/columna_mina.py`, que lee el guardado y no levanta servidor) y con el arnés.

**El síntoma, medido**: `[Arnes] MINA … pasos=16/240` **congelado 18.200 ticks** (una corrida entera) en su partida,
con el minero plantado en la superficie (`pos=499,63,621`, la faena a 8-9 bloques por debajo) alternando
`Bajando a la mina` / `Volviendo a la caseta (encajado)`. La **boca** estaba **abierta** (`507,63,613=air`,
`507,64,613=air`): el corte estaba **más abajo, en el propio caracol**.

**Lo que la tapaba, celda a celda** (las 17 piezas de los pasos 0 a 16 puestas y, aun así, **NO HAY RUTA** de pie
desde el suelo hasta la casilla de pie del paso 16 — el recorrido en anchura no pasaba de la superficie):

| dónde | qué había | quién lo puso |
|---|---|---|
| `507,62,614` … `507,62,620` (**7 celdas**) | **césped** en la capa que se pisa, encima del pozo | el **nivelado de la aldea** (`nivelar`): rellena con césped hasta `nivel - 1` y esa capa es justo la que el caracol cruza al salir a la superficie. `esCeldaDeLaMina` **excluye la superficie a propósito** ("la caseta es del pueblo"), así que el pozo se sellaba por arriba. La firma que lo delata: en los pasos 3-7 el relleno quedó en la **tercera** celda del hueco y las de debajo seguían siendo aire — que es exactamente lo que hace el bucle de `nivelar` (`sellarSuelo`, que tapa hacia abajo, habría rellenado también las de debajo) |
| `500,55,621` y `500,56,621` | **dos troncos** (`oak_log`) | el **marco de madera del paso 16**: sus postes van en las "paredes" (los dos lados del radio), pero el paso 16 es una **esquina del anillo** y una de esas paredes es **otra celda del caracol** (el paso 15), así que el marco **tapona el escalón de al lado**. Y no es un caso raro: **14 de los 15 marcos del caracol** caen en una esquina (los pasos 16, 32, 48… alternan la suroeste y la noreste) |

**Las cuatro cosas que lo abren** (las tres primeras, para que no vuelva a pasar; la cuarta, para las aldeas que ya
están así — la del jugador):

1. **`ponerElMarco` descarta los lados que caen en un paso** de la mina (`VillageGenerator.esCeldaDePasoDeLaMina`) en
   vez de renunciar al marco entero: queda el poste que sí tiene pared (el de fuera) y su viga. Renunciar al marco
   entero dejaba la mina **sin ningún soporte** (14 de 15 caen en esquina). Los marcos de las **galerías** no
   necesitan guarda: medido, **0 de sus postes** caen en un paso (van perpendiculares al avance).
2. **`nivelar` no rellena el pozo**: `actual.isAir() && (esCeldaDeLaMina(...) || estaSobreElPozo(center, celda))`.
   Sin la segunda condición, el relleno —que va **hasta `baseY - 1`**— le vuelve a poner césped encima.
3. **`sellarSuelo` tampoco**: la mina **sale a la superficie**, así que su columna tiene la capa del suelo hueca **a
   propósito** y este tapagujeros la rellenaba entera. Va en pareja con (2): arreglar solo `nivelar` no basta —
   `sellarSuelo` correría después y sellaría igual—, y eso se ve en el orden del código (una llama a la otra).
4. **`despejarElPozoDeLaMina`** (reparador idempotente, en el latido junto a `asegurarLaMinaDelPueblo`): devuelve el
   paso a **lo que el minero YA había cavado** —solo los pasos con su pieza puesta y, de la galería, solo las celdas
   ya abiertas— quitando de sus **tres celdas de hueco** (`above(1..3)`, las mismas que cava `picarLaCeldaDelCaracol`)
   el **terreno del pueblo** y los **postes del marco** que cayeron dentro. No excava nada nuevo y no toca nada que
   no sea del paso de la mina (la caseta queda como está).

**Y una quinta, que salió al medir y NO era el sello** (queda dicho con lo que se midió y lo que no):
`celdaDePieDelCaracol` devolvía **siempre `celda.above()`**, pero la huella de la **losa** (los pasos pares) está a
`y + 0,5`, así que el aldeano que se para encima tiene los pies **dentro de la propia celda de la losa**; el
`above()` sólo vale para el **adoquín** (los impares). Corregido, el destino del goal pasa de `499,55,621` a
`499,54,621` (medido en el log)… **y el minero seguía sin bajar** (la corrida con ese arreglo y el reparador de dos
celdas dio `pasos=16` otra vez y la sonda parada en el paso 5): o sea que **no** era la causa del sello. Se queda
porque es la celda de pie **de verdad** (y medido: no estorba), pero el arreglo que **abre** la mina es el de las
**tres** celdas del punto 4.

**MEDIDO, el antes y el después** (la misma partida, la misma copia, el mismo modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| ruta de pie al pozo (modelo de `ruta_atasco.py`) | **NO HAY RUTA** (14.326 casillas, no pasaba de la superficie) | **HAY RUTA: 26 pasos** |
| el reparador en el latido | — | `abierto el pozo de la mina (6 celda(s) que el pueblo habia vuelto a tapar y 2 poste(s) del marco dentro del paso)` |
| volcado `POZO` (los pasos 0..16) | `1:cobblestone/grass_block/air`, `15:cobblestone/oak_log/oak_log`… | **todos `air/air`** |
| sonda del planificador (`createPath` exacto) | paraba en el **paso 5** (`507,60,618=NO`) | **pasos 0…32 todos `SI`** |
| `[Arnes] MINA … pasos=N/240` | **16 congelado 18.200 ticks** | **16 → 32** (y subiendo), con el minero en `505,47,613` (**16 bloques bajo el suelo**) y la etiqueta `Abre galeria` |

**Lo que queda apuntado** (no medido): el planificador del juego **no usa la misma celda de pie para la losa que
para el adoquín** —en la sonda, el fin de la ruta de un paso de losa cae **una celda más arriba** que la de pie
física—, y eso es lo que hacía que la tercera celda del hueco (el techo) fuese **crítica**: por eso el reparador
tiene que despejar las **tres**, no dos.

**Y la mina, después de abrirse, se topa con otro sitio** (medido, y es lo que queda pendiente): con `pasos` ya en
32, el minero se queda **clavado 110.000 ticks** en la **galería del paso 32**, alternando **121 veces la celda 1 y
120 la 2** de esa galería sin que `progresoDeLaGaleria` avance, con **una** rotura de pico y **0** líneas de
`sella agua/lava` y de `la mina se PARA` (que es lo que el código dice que hace al topar con un mar: `cerrarLaMina`).
El mapa del sitio (`build/slice_mina.py 505 509 609 614 44 50 world`) lo enseña: la galería sale hacia el norte
desde `507,46,613` y ahí hay un **acuífero**. Queda por medir el mecanismo fino (si el agua vuelve a entrar entre la
comprobación y el picado, y por qué `sellosSeguidos` no llega a `SELLOS_MAXIMOS`), y de paso que el minero se queda
**sin pico** con el zurrón lleno de adoquín y con la ruta a su taller **2 corta**
(`rutaFaena=[a1=38n alcance=NO fin=501,63,613 dFin=2.00]`).

#### Los PORTONES: medido, y el arreglo RETIRADO (misma sesión)

El goal de las compuertas de valla es el único que corre **sin banderas**, así que va **en paralelo** con la faena
(medido con los `goals=[…]` de I124/I125), y al abrir una compuerta **borra el destino del cerebro**
(`navigation.stop()` + `WALK_TARGET` + `PATH`). Se instrumentó esa apertura (`[Gate] … alcanzaba=SI/NO`) y en una
corrida larga (**127.680 ticks**) resultó que **499 de 868 aperturas (57 %)** tenían la **ruta viva alcanzando ya**
el destino: ahí no había camino que rehacer y sí se le quitaba el destino a un aldeano que iba bien.

**Se probó el arreglo** (no borrar el destino cuando la ruta viva ya alcanza) **y NO arregla nada medible**: las
rendiciones del pueblo salen a **1,25 por 1.000 ticks** con el arreglo y a **1,37** sin él (el antes de esta sesión:
25 en 18.200 ticks). O sea: **el borrado no era la causa de que se rindieran** —lo que les hace rendirse es el
destino y la ruta de I119/I122/I125— y el par de goals corriendo a la vez, que es **real y está medido**, no basta
para perder el rumbo. **Regla del proyecto: lo que no arregla, se retira y se dice** → el arreglo se ha quitado y se
queda la **línea `[Gate]`**, que es la que lo midió (y que sigue sirviendo para ver, en cualquier corrida, en cuántas
aperturas se le está borrando el destino a alguien que ya iba llegando).

### I127 · EL ACUÍFERO DE LA GALERÍA: el agua se daba por "picada" y la mina no cerraba nunca (26-sep-2026)

Lo siguiente que salió al abrir el pozo (I126): con el minero ya bajando, se quedó **clavado en el paso 32** —
`pasos=32` durante **110.000 ticks**, **282 líneas** de `El minero: galeria` repartidas entre **la celda 1 (121
veces) y la 2 (120)** de la misma galería, **0** líneas de `sella agua/lava`, **0** de `la mina se PARA`, el pico
rompiéndose de tanto "picar" (1 vez `se le ha roto el pico`) y el zurrón lleno de adoquín. El mapa del sitio
(`build/slice_mina.py 505 509 609 614 44 50 world`) enseñó el acuífero: la galería del paso 32 sale hacia el norte
desde `507,46,613` y ahí hay **agua** (`W` en `505..507,611..612` a `y=45..46`).

**Eran TRES fallos encadenados**, y los tres medidos:

1. **EL AGUA SALÍA POR LA GUARDA DE "ES DEL PUEBLO"**. En `picarYRecoger` la comprobación de "no se toca"
   (`!VillageGenerator.elMineroPuedePicar(estado)`) iba **antes** de la del agua, y `elMineroPuedePicar` dice que
   **no** al agua (no es aire, ni terreno natural, ni una pieza de la mina). Resultado: una celda inundada devolvía
   `true` —"hecho"— **sin picar y sin sellar**. Arreglado: **el agua y la lava van primero**.
2. **LA GALERÍA PICABA LA MISMA CELDA DOS VECES**. Su bucle del hueco de paso empezaba en `dy = 0`, o sea que
   picaba **la celda de la galería** con `cuentaElSello = false`, y la llamada de abajo (la que sí cuenta) se
   encontraba **adoquín** —el sello que acababa de poner—, lo picaba en el acto y **devolvía el agua al túnel**.
   Medido con el arreglo del punto 1 puesto: 2 sellos y `1:water 2:cobblestone`, o sea el sello durando un tick.
   Arreglado: ese bucle solo abre **la celda de la cabeza** (el caracol ya lo hacía bien, `dy` desde 1).
3. **"13 SELLOS SEGUIDOS" NO LLEGA NUNCA CON UN ACUÍFERO**. El contador se **reinicia** en cuanto el minero pica
   una celda de roca virgen (`1, 2, 3, 1, 2, 3…` medido, 50 sellos y la galería siempre en `0/24`). Lo que de
   verdad distingue la **bolsa** del **mar** es **si el agua VUELVE a la misma celda**: un manantial aislado, al
   picarlo, se queda seco; un acuífero conectado lo vuelve a llenar. Arreglado con `selloDelMar`: **si la misma
   celda pide un segundo sello, es un mar** y la mina se cierra ahí mismo (con el tope de siempre, 12, como
   respaldo). Es lo que pidió el jugador: *"que selle las bolsas de agua o lava; si es un mar, que pare"*.
4. **Y EL TOPE HAY QUE MARCARLO DONDE EL PUEBLO LO LEE**: `cerrarLaMina` marcaba con **piedra labrada** la celda
   que se estaba picando, y `laMinaLlegoAlTope` mira **`celdaDelCaracol(paso)`**. Si el que se topaba con el mar era
   una **galería**, la mina se quedaba **sin tope** y el minero volvía a por la misma celda. Arreglado: `cerrarLaMina`
   marca **también** la celda del caracol de ese paso.

**Y dos arreglos que salieron de la lista de consecuencias** (preguntar quién más lee lo que toco):

- **`despejarElPozoDeLaMina` (I126) no puede tratar el adoquín como "terreno del pueblo"**: desde que el minero
  sella el agua **con adoquín**, el reparador le **volvía a abrir los sellos** en la pasada siguiente del latido. El
  nivelado y el tapagujeros rellenan con césped y tierra (y piedra), nunca con adoquín: fuera de la lista.
- **Sin pico no se cava, y el goal tiene que soltar la faena**: el `canUse` ya preguntaba por el pico al empezar la
  vuelta, pero **mientras el goal corre no se vuelve a preguntar**, así que con el pico roto el minero seguía
  "picando" (gastando tiempo y mano) en el sitio. Ahora `canContinueToUse` lo suelta y `canUse` lo manda al almacén
  a por otro.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| líneas `El minero: galeria` en el bucle | **282** (121 + 120 sobre las celdas 1 y 2) | **6** |
| `sella agua/lava` | **0** | **4** (y para) |
| `la mina se PARA … piedra labrada de tope` / `TOPE=` | **0** / `TOPE=NO` | **1** / **`TOPE=SI`** |
| volcado `GALERIA` del paso 32 | `1:water 2:water …` y `hechas=0/24` para siempre | `1:stone_bricks 2:water …` y la mina **cerrada** |
| el minero después | en bucle dentro de la mina, sin pico | **fuera**, en `515,63,662`, `Cargando material` |

### I128 · EL RECOLECTOR: dos trampas que le costaban 64 rendiciones (26-sep-2026)

El recolector (`VillagerCollectGoal`) era el que más se rendía de la aldea: **64 de las 159** rendiciones de una
corrida larga, y **62** de ellas con solo dos patrones, los dos medidos:

1. **COSAS ENCIMA DE UN TEJADO.** 20 rendiciones con destino `424/426/427, 67, 670`: `67` es **`cota + 4`** y el
   techo de la barraca está en **`y=66`** (medido con `build/slice_mina.py 421 429 667 673 65 68 "New World (2)"`:
   tablones en 66 y **aire en 67**), o sea que el objeto está **encima del tejado** y ahí no se sube. El filtro de
   altura era `item.getY() > cota + ALTURA_MAXIMA` con **4**, que justo lo dejaba pasar. Bajado a **3** (la calle
   es `cota`; con 4 se colaba el tejado).
2. **COSAS FUERA DEL MURO.** 35 rendiciones con destinos como `553,63,595` o `542,63,583` (a ~97 del centro, con
   la muralla en 62) y `ruta=1 nodos … alcanza=NO`. Medido con `tools/arnes/ruta_atasco.py 521 612 553 595 63`:
   **NO HAY RUTA** desde dentro hasta ese objeto, y el planificador del juego dice lo mismo (una ruta de **1 nodo**
   que no alcanza) porque **una puerta de valla cerrada no es navegable**. El recolector no usaba el paso por el
   portón que sí usan los demás goals (I112). Arreglado: cuando el objeto (o el almacén, si se quedó fuera) está al
   otro lado de la muralla, **primero se va al portón** (`VillageManager.pasoParaCruzarElMuro`) y al ponerse a su
   lado el goal de los portones se lo abre.

**MEDIDO**: **Filomena (la recolectora) pasa de 64 rendiciones a CERO** en la corrida con los dos arreglos, y las
rendiciones totales del pueblo bajan a **0,64 por 1.000 ticks** (el antes de esta sesión: 1,37).

### I129 · EL CAMINANTE DA POR LLEGADO LO QUE ESTÁ A UN BLOQUE (y el granjero se rendía en la puerta)

Lo pidió el jugador de la forma más clara: *"no puedes reparar al granjero… quiero que funcione YA toda la mecánica,
sin excusas; si algo del juego vanilla falla, reescríbelo con una versión propia que sí funcione"*.

**El fallo, medido** (26-sep-2026): el granjero que va a entrar en su bancal se quedaba **pegado a la compuerta** y se
rendía. Los avisos lo decían todo: **6 avisos `Entrando a la huerta`, TODOS a distancia 1** de la celda que
perseguían, y **5** líneas `El granjero no consigue entrar al bancal 1 por 484,63,658: … probara otra compuerta` (o
sea: aparcaba la entrada, probaba otra, y hasta las cuatro).

**La causa**: `VillageManager.caminarHacia` pone el destino en el cerebro con `WalkTarget(..., 1)` — **tolerancia de un
bloque**— y con esa tolerancia el planificador del juego da por **LLEGADO** un sitio que esté a **un paso**. Si el
aldeano ya está pegado a la celda (el caso exacto: el granjero junto a la compuerta y la celda de dentro a un paso),
la ruta que devuelve es de **un solo punto** —su propia celda—, así que **no da ni un paso**, su distancia no mejora
y el vigilante de "no me acerco" acaba aparcando la entrada. Eso explica también por qué **dos arreglos anteriores
fallaron** (están medidos y retirados en `medidas-mina-sellada.txt` §11): perdonar el contador de atasco no cambia que
el aldeano **no se mueva**, y mandarlo "un paso más adentro" tampoco, porque la tolerancia de 1 se aplica igual a ese
nuevo destino.

**El arreglo**: `VillageManager.caminarHaciaExacto(villager, celda, velocidad)`, un caminar con **tolerancia 0**
(`WalkTarget(..., 0)`) que **además le pide la ruta a la navegación a mano** (`getNavigation().moveTo(...)`), porque
el cerebro del aldeano puede volver a escribir su propio destino en el mismo tick (I119/I125) y con la ruta ya pedida
el caminante **no se queda quieto**. Se usa en los dos tramos del granjero que exigen **pisar** una celda: **entrar**
por la compuerta y **salir** del bancal.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| `no consigue entrar al bancal …` | **5** | **0** |
| avisos de `Entrando a la huerta` | **6** | **0** |
| el granjero | se rendía en la puerta | **`Cosechando`** (medido en el volcado `[Gate]`) y entregas a la despensa de **73, 71, 45 y 14** |

**Lo que deja apuntado** (mismo patrón, siguiente): **`Filomena (Recolector)`** con **19** rendiciones en
`Volviendo a la plaza` — se la manda a la plaza con `caminarHacia` y **no se mueve** porque ya está a un bloque. El
mismo `caminarHaciaExacto` vale para ese tramo y para los demás goals que mandan a una celda concreta.

### I130 · LA RECOLECTORA SE QUEDABA ENCERRADA EN EL BANCAL (una puerta de valla cerrada no es navegable)

Al arreglar al granjero (I129) salió la que quedaba arriba: **`Filomena (Recolector)`, 19 rendiciones** con la
etiqueta `Volviendo a la plaza`, y el aviso decía exactamente qué pasaba:

```
no consigue llegar a 470,63,646 (la plaza) desde 450,62,680 (dentro de un bancal) (ruta=1 nodos hasta 450,63,680
    alcanza=NO; pies=farmland cabeza=wheat suelo=dirt | destino=stone_bricks encima=bell)
    etiqueta="Filomena (Recolector) / Volviendo a la plaza" cerebro=470,63,646 nav=[sin ruta]
```

**La causa**: entra a los bancales a por lo que se cae —eso es su faena, y el jugador quiere que lo recoja— y luego
**no puede salir**, porque una **puerta de valla cerrada no es navegable** para el juego: el planificador le devuelve
una ruta de **un solo nodo que no alcanza** y se rinde con la plaza a 39 bloques. No es la tolerancia de I129: es que
**el mundo le cierra el paso** y **nadie le abre la compuerta** (el juego no deja que un aldeano abra una puerta de
valla: la abre el pueblo).

**El arreglo**: `VillageManager.abrirLaCompuertaDeAlLado(level, celda)` —la que tenía el granjero, ahora compartida— y
en la recolectora: si está **dentro de un bancal**, se le busca la **celda de dentro de la compuerta más cercana** (la
salida), se le manda ahí con `caminarHaciaExacto` y, en cuanto la tiene al lado, **se le abre** la compuerta; si esa
no vale (aparcada, I33), prueba otra. Es el mismo camino que hace el granjero para **entrar**.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| rendiciones del pueblo | **27** | **9** |
| `Filomena (Recolector) / Volviendo a la plaza` | **19** | **0** |
| la recolectora | se rendía dentro del bancal | **sale por la compuerta** (`abro el porton 484,63,659 · destino=470,63,646 rutaViva=24 nodos alcanzaba=SI`) y **entrega** (`El recolector guardo 9 cosa(s) en el almacen`) |

**Y con esto quedan 9** en una corrida: `Vicenta (Ganadero) / Cuidando el ganado` **3**, `Tomasa (Leñador) / Llevando
la madera` **2**, guardias **3** y `Valeriano (Granjero) / Guardando lo suyo` **1**. El siguiente por mirar es el
**ganadero**, con el mismo instrumento (el aviso ya trae `cerebro=`, `nav=` y `goals=[…]`).

### I131 · EL GANADERO PERSEGUÍA UNA CELDA QUE NO SE PISA (regla de I114, aplicada al ganado)

Con el granjero y la recolectora arreglados (I129/I130), las que quedaban eran del **ganadero**: 3 avisos con
`Cuidando el ganado` y el destino **a 4-6 bloques**, con **`ruta=1 nodos … alcanza=NO`**:

```
no consigue llegar a 525,63,651 desde 525,63,656 (ruta=1 nodos hasta 525,63,656 alcanza=NO;
    pies=air cabeza=air suelo=grass_block | destino=air encima=air)
    etiqueta="Vicenta (Ganadero) / Cuidando el ganado" cerebro=- nav=[sin ruta] goals=[VillagerAnimalFarmGoal]
```

**La causa**: el goal camina a la **celda cruda de la faena** —`presa.blockPosition()`, `pareja.blockPosition()`,
`suelto.blockPosition()`, el punto del rebaño— y esa celda **no siempre se pisa** (el animal está sobre una valla, en
la paja, en el abrevadero…). El planificador no puede meterlo en una celda que no es casilla de pie y devuelve una
ruta de **un solo punto**: el aldeano empuja, no se acerca, y aparca la faena. Es la lección de **I114** ("a un bloque
no se camina: se camina a una casilla de pie") **otra vez**, esta vez en el ganado.

**El arreglo**: `casillaDePieCercaDe(level, faena)` —si la celda de la faena ya se pisa, ella misma; si no, la casilla
de pie **más cercana** de su alrededor (radio 2)— usado **solo para caminar** (`destinoDelTramo`), porque el
`target` del goal lo usa `recoger()` para saber **qué objeto** coger y no se puede tocar.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| rendiciones del pueblo | **9** | **5** |
| `Cuidando el ganado` (ganadero) | **3** | **0** |
| `Llevando la madera` (leñador) | **2** | **0** |

**Y quedan 5, todas de una en una** (una corrida): `Eufemia (Guardia) / Yendo a entrenar`, `Onofre (Guardia) /
Patrullando la arboleda`, `Saturnino (Granjero) / Abono la huerta`, `Tomasa (Leñador) / Yendo al arbol` y
`Valeriano (Granjero) / Recogiendo lo que se cayo`. El repaso de la sesión, con la misma partida y el mismo modo:
**27 → 9 (granjero + recolectora) → 5 (ganadero)**.
### I135 · EL TOTAL DE RENDICIONES ES RUIDO: SE MIDE POR 1.000 TICKS, EN VENTANA FIJA Y CON MEDIA DE VARIAS CORRIDAS

**El problema.** Comparaba corridas por su **total** de `no consigue llegar` y salían 5, 9, 10, 12, 18… Eso **no es una
medida**, por dos razones independientes:

1. **Las corridas duran distinto.** Una de 4.000 ticks con 5 rendiciones va **peor** que una de 12.000 con 9: la
   cuenta buena es **por 1.000 ticks**, no el total. (El reloj sale de la propia línea del arnés, `[Arnes] … t=<ticks>`,
   que es la única marca fiable del log: ver la trampa de la rotación en `tools/arnes/LEEME.md`.)
2. **La muestra es pequeña (~10 avisos por corrida).** Con esos números una corrida sola **no distingue una mejora del
   azar**: hace falta la **media de varias corridas** y el **rango**, no un número suelto.

**La resolución**: `tools/arnes/rendiciones.py`, que mide **rendiciones por 1.000 ticks dentro de una ventana fija**
(`--desde`/`--hasta`, por defecto 2.000-12.000) y, con varias corridas, da **media, rango y desviación típica**.

**MEDIDO con las corridas de esta sesión** (misma partida, misma copia, mismos modos; cada una es una corrida):

| corrida (estado del código) | ventana | rendiciones | por 1.000 ticks |
|---|---|---|---|
| tras I129 (granjero) | 2.000-10.280 | 23 | **2,78** |
| tras I130 (recolectora) | 2.000-10.080 | 8 | **0,99** |
| tras I131 (ganadero) | 2.000-9.120 | 3 | **0,42** |
| tras la casilla de pie del granjero | 2.000-9.280 | 8 | **1,10** |
| tras el desatasco del aldeano metido | 2.000-4.120 | 2 | **0,94** |
| tras el pico/pepitas | 2.000-11.160 | 4 | **0,44** |
| tras I132 (la mina atraviesa el agua) | 2.000-10.800 | 24 | **2,73** |
| con la sonda del pico | 2.000-9.720 | 7 | **0,91** |

**TANDA de 8 corridas: media 1,29 · rango 0,42-2,78 · desviación típica 0,94.**

**SEGUNDA TANDA (27-sep-2026, tras I136/I137/I138, misma ventana `t=2.000-12.000` y medidas de los logs que ya había
—sin gastar corridas nuevas—)**: `medida-galeria-final.log` **0,50** (5 rendiciones) y `medida-balsa-final.log`
**0,60** (6) → **media 0,55 · rango 0,50-0,60 · desviación 0,07**.
<ul>
  <li>la tasa **baja a menos de la mitad** (1,29 → 0,55) y, sobre todo, es **mucho más estable** (desviación 0,94 →
      0,07): ya no hay corridas «malas» de 2,7;</li>
  <li>y **no queda ni un solo atasco repetido**: las 11 rendiciones de las dos corridas son **todas de 1**, y **5 son
      guardias en su ronda** (`Patrullando el corral` / `Patrullando la arboleda`, la clase de I115). Los atascos
      gordos —`Yendo a la taberna` 16, `Volviendo a la plaza` 19, `Cuidando el ganado` 3, `no consigue entrar al
      bancal` 5— están **a cero**.</li>
</ul>
(Con 2 corridas esto es **orientación**, no un valor exacto: para afirmarlo hacen falta 3-4. Lo que sí dice el
desglose es que **ya no hay un sitio concreto** donde se rinda el pueblo.)

**La consecuencia, dicha sin adornos**: la tasa **varía casi 7×** entre corridas comparables, así que **una corrida
sola no prueba nada** salvo que el cambio sea grande; con 8 corridas, el intervalo de confianza al 95 % es de unas
±0,7 rendiciones por 1.000 ticks. Por eso **la prueba de un arreglo no es el total**, sino el **criterio concreto**
que baja a cero (`no consigue entrar al bancal` 5 → 0, `Volviendo a la plaza` 19 → 0, `Cuidando el ganado` 3 → 0,
`TOPE` sí → no, `pico de hierro` forjado) y, cuando se quiera hablar del total, **la media de 3-4 corridas**.

**Y la herramienta se pagó sola en el primer uso**: la corrida de I132 era la más alta de la sesión (2,73) y **no era
la mina**: era **`16x Filomena (Recolector) / Yendo a la taberna`** — dos tercios del total de esa corrida, en un
atasco **nuevo**. Y nuevo **por mi propio arreglo**: al dejar de quedarse encerrada en los bancales (I130), la
recolectora **llega a la taberna**, y ahí se rinde. Es decir: el "ruido" escondía un **efecto colateral real**, que la
cuenta normalizada y el desglose por etiquetas han sacado a la luz. **Siguiente pendiente, ya con nombre y número.**

Lo que sigue es la nota original de la pregunta abierta:
atravesando el agua) es **la más alta de la sesión (2,73)** — hay que **mirar sus etiquetas** (`--etiquetas`) para ver
si el minero, ahora que trabaja mucho más rato, está arrastrando a más aldeanos a rendirse, o si fue una corrida mala.
### I134 · EL QUE MATA, LOOTEA (y así el hierro de los raids llega al herrero)

Lo pidió el jugador: *"vamos por la 2, además así se siente más real el guardia, que es como un jugador que sube de
experiencia y lootea"*.

**El problema medido** (I133 y medidas 20-22): el zombi de raid **ya soltaba pepitas** y el recolector **ya las
llevaba al almacén**, pero **no se acumulaban**: caían alrededor del zombi y las que el recolector no alcanzaba
**desaparecían a los 5 minutos** (6000 ticks), así que el almacén se quedaba en `1` pepita y el herrero nunca llegaba
a las **27** que pide un pico de hierro (forjaba picos de **madera**).

**El arreglo** (`AggressiveZombieEntity.dropCustomDeathLoot`): **si quien lo mata es un aldeano del pueblo —la
guardia—, las pepitas van a SU ZURRÓN en el acto**, como cuando un jugador recoge lo que mata. Si no le caben, se
caen al suelo (nunca se borra nada del pueblo). Ya no hay desaparición por tiempo y el aldeano las baja al almacén en
su siguiente viaje.

**MEDIDO** (modo `MEDIR_PEPITAS` con un zombi de raid cada 400 ticks y la muerte atribuida al aldeano más cercano):

```
[Arnes] PEPITAS t=8300 200 ticks después: en el suelo=0 en el ALMACEN=0 picos=2
[Village] El herrero de herramientas: Forjo un pico de madera
[Village] El herrero de herramientas: Forjo un pico de hierro     <-- LA CADENA CIERRA
```

El pico de **hierro** se forja con las pepitas de los raids que la guardia lootea: **eslabones 1, 2 y 3 completos**.
(Los `en el ALMACEN=0` de los últimos ciclos son porque el herrero **ya se las ha gastado**.)
### I133 · EL PICO: LO FORJA EL HERRERO, Y EL HIERRO SON **PEPITAS** DE LOS ZOMBIS DE RAID

Lo pidió el jugador: *"si el pico lo debe construir el herrero"* y *"los guardias, cuando maten zombis que vengan de
algún raid del mundo, conseguirán hierro; y que los zombis de raid suelten **pepitas** de hierro, no lingotes"*.

**Lo que ya estaba bien (medido)**: el zombi de raid **ya suelta pepitas**
(`AggressiveZombieEntity.dropCustomDeathLoot`: 1-2 `IRON_NUGGET`), y el minero **detecta la rotura del pico y va a
por otro** (`El minero: se le ha roto el pico (59 usos): va a por otro al almacen`). El eslabón roto era el
**herrero**: `recetaDePico` pedía **3 `IRON_INGOT`** y el almacén tenía **0 lingotes** (y el recolector **no recogía
las pepitas**: no estaban en su lista blanca), así que **no había pico nunca**.

**Los dos cambios**:
1. **`VillagerCollectGoal.esDelPueblo`**: se añade **`Items.IRON_NUGGET`** — el recolector recoge las pepitas del
   suelo (antes se quedaban tiradas y el herrero no las veía nunca).
2. **`VillagerSmithGoal.recetaDePico`**: si **no hay 3 lingotes** pero hay **27 pepitas** (vanilla: 9 pepitas = 1
   lingote), forja el **pico de hierro con pepitas**. El pico se sigue pidiendo **de mejor a peor**
   (diamante → hierro → piedra → madera) y con el **objetivo de 2 picos** ya existente (`OBJETIVO_PICOS`).

**MEDIDO** (modo `MEDIR_MINERO`, con el pico forzado a romperse cada 2.000 ticks): el minero rompe el pico **5
veces** y el herrero **forja picos** (`El herrero de herramientas: Forjo un pico de madera` ×3), que el minero se
lleva (por eso el almacén marca `0 pico(s)`: el que los necesita es él).

**Y lo que FALTA medir**: las **pepitas** salen solo de los **zombis de raid**, y en `MEDIR_MINERO` (que **barre los
bichos**) **no muere ninguno**: hubo `0` pepitas. Hace falta un modo del arnés que **plante un
`AggressiveZombieEntity`** y lo mate atribuido a la guardia (como `MEDIR_MILICIA`) para medir la cadena entera:
pepitas → alguien las levanta → almacén → el herrero las gasta en un pico.
### I132 · LA MINA **ATRAVIESA** EL AGUA (aísla, seca y sigue bajando) — cambia I127

El jugador corrigió el criterio que yo tenía: *"¿quién te dijo que debe cerrarse una mina cuando hay agua? Lo que debe
hacer es **seguir minando para abajo** y **construir paredes que aíslen la mina del agua**, **sacar lo que está
adentro** y **construir escaleras para llegar al fondo**"*. La instrucción vieja (*"si es un mar, que pare"*, que era
la que yo había implementado en I127 con `SELLOS_MAXIMOS` y `cerrarLaMina`) queda **sustituida por ésta**.

**Lo que se cambió** (dos cosas, y solo del minero):

1. **`picarYRecoger`, rama de fluido**: antes **sellaba la propia celda del túnel** con adoquín —y eso la dejaba
   **intransitable** (la mina acababa cerrándose)—. Ahora: **(a)** `aislarDelAgua` sella con adoquín **todo el fluido
   de la cáscara 3×3×3** alrededor de la celda (es el muro que la aísla, y sella también el agua de DELANTE, así que
   el túnel avanza por celdas ya secas); **(b)** la celda queda **de AIRE** (seca, transitable); **(c)** devuelve
   `true`, o sea **el túnel SIGUE**.
2. **La guarda de `prepararElPicado`**: `elMineroPuedePicar(agua)` dice que **no** y el goal **ni lo intentaba**
   (se plantaba 5 s con "la mina está tapada"). Ahora una celda **con fluido no cuenta como tapada**: es faena suya.

**MEDIDO** (misma partida y modo; antes: la mina se cerraba con `TOPE=SI` en el paso 32):

| | antes (I127) | ahora (I132) |
|---|---|---|
| el agua | se sellaba la celda del túnel → **intransitable** → `la mina se PARA … piedra labrada de tope`, `TOPE=SI` | `El minero: aisla el agua de 507,46,612 y el tunel sigue` |
| el túnel | `hechas=0/24` clavado en la celda 1 (agua) | **`1:air 2:air 3:air` y `hechas=3/24`**: atraviesa el acuífero |
| la mina | **se cerraba** | **`TOPE=NO`**: sigue bajando |

**Lo que queda de este encargo del jugador** (apuntado en `PENDIENTE.md`): **(a)** que **el herrero forje picos** (hoy
el almacén tiene `0 pico(s)` y `0 lingote(s)`: la rotura del pico se detecta bien, pero no hay de dónde sacarlo) y
**(b)** que **los guardias obtengan hierro de los zombis de los raids** para que ese hierro llegue al herrero.
### I136 · LA BOCA DE LA GALERÍA SON **TRES** CELDAS (y el muro no se cruza por debajo del suelo) (27-sep-2026)

**El atasco medido** (corrida larga del 26-sep, `build/medida-mina-fondo.log`): la galería del paso 32 se quedaba en
`hechas=3/24` **3.600 ticks**, con el `destino` en `507,46,610`, `nav=[sin ruta]`, el destino **borrado del cerebro**
(`destino=SIN DESTINO`) y `Volviendo a la caseta (encajado)` en bucle. El minero había abierto las celdas **1, 2 y 3
desde el propio anillo** (están a 1, 2 y 3 bloques de la celda del caracol: dentro de su alcance de **3,5**) y la celda
**4** está a **4,0**: fuera de alcance, así que **tenía que entrar** en la galería… y no podía.

**La causa, leída en el código de vanilla** (no supuesta; las fuentes están en la caché de NeoForge,
`WalkNodeEvaluator`/`PathFinder`):

1. El aldeano baja el caracol **de pie ENCIMA de la losa** del paso par: pies a `y+0,5`. Su **nodo** de ruta es
   `y+1` (lo fija `getStart()` con `floor(y+0.5)`) y su **caja** ocupa `y+0,5 … y+2,45`.
2. La galería va **a la misma Y que la celda del caracol**, así que con **dos** celdas de hueco (`y`, `y+1`) la celda
   `y+2` es **roca**. `getPathTypeWithinMobBB` mete en el tipo del nodo **todas** las celdas de la caja y una sola
   **BLOCKED** (malus −1) tumba el nodo entero: el vecino de `y+1` sale **BLOCKED**.
3. `findAcceptedNode` solo prueba el vecino **a la misma Y**, y el que sabe bajar medio bloque
   (`tryFindFirstGroundNodeBelow`) **solo se llama si el tipo es OPEN** → **no hay entrada**. Y la salida tampoco:
   `tryJumpOn` (el que sube medio bloque) exige que la celda **encima** de la de la galería sea pisable, que es justo
   la tercera.

**El arreglo**: `picarLaCeldaDeLaGaleria` abre **tres** celdas de hueco (`celda.above()` y `celda.above(2)`), las
mismas que el caracol; y `esCeldaDePasoDeLaMina` cuenta la tercera (`+2`) para que el **marco** del caracol no ponga un
poste dentro.

**MEDIDO** (misma partida, `MEDIR_MINERO`; el arnés **ya no cava a mano** la galería del paso 16: se quitó ese atajo
porque con el arreglo la cava el minero). Corrida buena: `build/medida-galeria-final.log`, 41.600 ticks:

| | antes (26-sep) | después (27-sep) |
|---|---|---|
| el minero **dentro** de la galería | nunca | `pos=499,54,634` (celda 12 de 24, t=1.000) |
| la galería del **paso 16** | la cavaba **el arnés a mano** | **la cava él: 2/24 → 24/24** |
| `hechas` de la galería del **paso 32** | **3/24 congelado 3.600 ticks** | **3 → 4 → … → 24/24** |
| `pasos` (el caracol) | **32 congelado** (la mina no bajaba más) | **32 durante la galería y luego 34 → … → 44**: la cara pasa de `y=46` a **`y=40`** |
| `TOPE=SI` / `se PARA` | — | **0** (la mina no se cierra) |
| `SONDA DE LA GALERÍA` (nueva) | no existía | `1:507,46,612=1n/SI` · `2=2n/SI` · `3=3n/SI` desde **dentro** de la boca |

**La trampa del instrumento nuevo** (y por qué hay que leerla con cuidado): `GroundPathNavigation.createPath` **imanta
el destino** —si la celda pedida es **aire**, baja hasta el primer bloque no-aire y devuelve la de encima; si es
**sólida**, sube hasta el primer aire y apunta **a la superficie**—. Por eso una sonda de una celda que aún es roca da
`SI fin=<la superficie>`: ahí `SI` **no** quiere decir nada. La lectura solo vale cuando `fin` es **la celda pedida**.

**Y el agua, que es la mitad del encargo del jugador** (*"construir paredes que aíslen la mina del agua"*): las tres
celdas de hueco abren el **techo** de la galería justo donde está el acuífero, y con el sello de antes (solo al **secar**
una celda de agua) el túnel se inundaba al subir el minero al taller: `hechas` **6 → 0** y **223 muestras (~8.900
ticks)** en cero. Se añadió el censo de agua del arnés (`AGUA`) y **dijo de dónde entraba**: las celdas mojadas eran
**las del propio túnel** (`507,46,605 … 507,46,611`) y **la de DELANTE** (la galería que aún es acuífero), con la pared
oeste ya sellada. La corrección: `aislarDelAgua` se llama **al abrir CUALQUIER celda** (no solo al secar una de agua) y
sella las 26 vecinas con fluido, y la exclusión se estrecha a
{@code VillageGenerator#esCeldaDePasoDelCaracol} — **solo el caracol** (un adoquín en un paso impar se leería como su
**pieza** y el paso se daría por hecho sin suelo: la lección de I127) y la **capa del suelo** (el agua del pueblo no se
tapia)—. Las celdas de **galería** sí se sellan: su contador cuenta **aire**, así que el adoquín no engaña a nadie y es
el **muro** que corta el acuífero. MEDIDO: la galería pasó de `hechas=13/24` a **21/24** con el agua **fuera** del túnel
(el censo se queda en `31 celdas con fluido`, todas en `x=505` y por debajo del suelo de la galería).

**Y el último bloqueo de esta cadena, que también salió medido**: con la galería en **21/24** el minero se quedó **243
muestras** en `Bajando a la mina` / `Volviendo a la caseta (encajado)`, con el destino del cerebro en **`470,63,583`** —
¡**fuera del pueblo**, por el **portón norte**!—. La causa: `pasoParaCruzarElMuro` decide con `esDeDentroDelMuro`, que
mira **solo X/Z**, y el final de la galería del paso 32 (que sale **hacia fuera** del anillo del caracol, 24 celdas)
cae a **65 bloques del centro**, o sea fuera del muro de radio 62… **a 17 bloques bajo el suelo**. El muro es una valla
**de la superficie**: por debajo de la capa del suelo no hay nada que cruzar (y el aldeano que está bajo tierra sale por
su propio sitio, el caracol, que está **dentro** del pueblo). Arreglado con esa guarda en `pasoParaCruzarElMuro`
(`villager.getBlockY() < cota-1 || destino.getY() < cota-1 → null`), y **medido**: la ruta del minero a la celda 21 de
la galería pasa a ser `22 nodos … alcanza=SI` (antes se iba al portón), la galería **se termina (24/24)** y el caracol
**sigue bajando** (pasos 34 → 44).

### I137 · LA CADENA DEL HIERRO DE LOS RAIDS, MEDIDA ESLABÓN A ESLABÓN (27-sep-2026)

Lo pidió el jugador: *"los guardias, al matar zombis que vengan de un raid del mundo, conseguirán hierro"*, y ese
hierro tiene que llegar al **herrero** para los picos. **La cadena entera está medida** (corrida
`build/medida-pepitas8.log`; datos crudos en `tools/arnes/medidas-pepitas.txt`):

> zombi de raid → **el guardia lo mata** → **lo lootea** (2 pepitas a su zurrón) → **lo deja en el almacén** → **el
> herrero forja el pico de HIERRO** → y el minero se lo lleva.

| eslabón | medida |
|---|---|
| (1) el **guardia** mata y **lootea** | `el que mato (Dorotea (Guardia espadachín · nv 1) … (GUARDIA)) lleva 2 pepitas (llevaba 0 antes)` |
| (2) llegan al **ALMACÉN** | `en el ALMACEN=2` → **20** de máximo, con **13 depósitos** de guardias (`deja en el almacen el hierro que ha loteado: 2 pepita(s)`) |
| (3) el herrero los **GASTA** | `[Village] El herrero de herramientas: Forjo un pico de hierro` y `picos (por material: … hierro 1)` |
| (4) el pico va al **MINERO** | el almacén acaba con `picos=0` (se lo llevó el minero: es de quien es) |

**Y ANTES DE NADA, TRES TRAMPAS DEL INSTRUMENTO** que daban las tres el mismo falso «no funciona» (las tres se
pagaron en corridas):

1. **`EN EL SUELO: 0` en las cuatro corridas del 26-sep**: el mod **no tira** las pepitas al suelo cuando las mata un
   aldeano del pueblo —se las da **al que mata**, `AggressiveZombieEntity.dropCustomDeathLoot`: *"el que mata,
   lootea"*—. Lo que hay que leer es **el zurrón del asesino**.
2. **La barredora del arnés** (`ticks % 20`, `discard()` de los monstruos dentro de 140 bloques) **descartaba el zombi
   plantado** ~40 ticks después; y `discard()` **no es morir**: ni muerte, ni botín, ni pepitas. El modo
   `MEDIR_PEPITAS` queda **fuera de la barredora**, como `MEDIR_MILICIA`.
3. **El radio del escaneo (140)**: el que llevaba el botín se iba **al muelle** y salía de la cuenta (`en zurrones=1 […]
   Yendo al muelle]` y dos muestras después `0`, **con la pepita todavía en su zurrón**) → **300**.

**Y el eslabón que FALTABA en el mod**: el guardia **no tenía ningún paso que dejara lo que looteaba**. Medido: el que
mataba llevaba sus pepitas en el zurrón **de t=300 a t=2.700** —con etiquetas `Yendo al almacen` y `Volviendo al
almacen` de por medio— mientras el almacén seguía a **0**; lo único que llegaba era cuando el que mataba era un
**herrero de armas** (cuyo goal sí tiene su «deja lo tuyo», `VillageSmithGoal`). Arreglo:
`VillagerGuardGoal.dejarElHierroEnElAlmacen` + el guardia **va** al almacén cuando lleva hierro (después del combate y
de la marcha —pelear manda— y antes de la ronda). Se deja **solo el hierro** (pepitas y lingotes): su arma, su escudo
y sus flechas no se tocan. **MEDIDO después**: 13 depósitos, el almacén de **0 a 20** pepitas, y con 27 el herrero
**forjó el pico de hierro**.

### I138 · LA BALSA ES DEL HERRERO DE HERRAMIENTAS (el minero solo pica) (27-sep-2026)

Lo pidió el jugador (la «opción C» del traspaso): *"pasar la balsa (colar adoquín → pedernal) y el acarreo al herrero de
herramientas para que el minero solo pique"*.

**El bucle medido que había que quitar**: la colada vivía en `VillagerMinerGoal.trabajarEnElTaller` y guardaba el
pedernal con `guardarEnInventario` —**al zurrón del minero**— mientras el umbral que miraba era el del **almacén**
(`VillageStorage.cuenta(…, FLINT)`): nunca se alcanzaba, así que encadenaba coladas. Medido en
`build/medida-galeria-final.log`: **33 coladas** seguidas (46 en otra corrida) con el almacén **clavado en 6 pedernal**,
y las últimas celdas de la galería a **5-8 minutos cada una** (celdas 21-24 abiertas a las 19:26, 19:33, 19:38 y 19:46).

**El traspaso**: la receta **«Colando»** (4 adoquines → 1 pedernal) vive ahora en `VillagerSmithGoal` y solo para el de
**herramientas** —es él quien necesita el pedernal: las flechas las hace el de armas—, con objetivo
`OBJETIVO_PEDERNAL = 16`. `Receta` gana el componente `enLaBalsa` y `puestoDeLaReceta(level)` manda al herrero a la
**balsa** de la caseta del minero en vez de a su mesa (si no hay balsa, la receta se descarta). Y como el ciclo del
herrero es **RECOGER → TRABAJAR → ENTREGAR**, el pedernal queda **en el almacén en cada faena**: eso es lo que hace que
el objetivo se cumpla y que el bucle **no exista por diseño**. En el minero se quita la rama de la balsa (con el porqué
medido en el sitio): el adoquín que saca lo **entrega** al almacén, de donde lo coge el herrero.

**MEDIDO** (misma partida, misma copia; datos crudos en `tools/arnes/medidas-balsa.txt`):

| | antes (`medida-galeria-final`) | después (`medida-balsa-final`) |
|---|---|---|
| coladas del **MINERO** | **33** (46 en otra corrida) | **0** |
| coladas del **HERRERO** | 0 | **10** (y para: el objetivo se alcanza) |
| pedernal del **ALMACÉN** | **6, clavado** (el objetivo no se alcanzaba nunca) | **6 → 16** (el objetivo, y ahí se queda) |
| la galería del paso 32 a **24/24** | **t≈37.800** | **t≈17.200** (menos de la mitad) |
| `pasos` (el caracol) | 40 en **t≈39.880** | **40 en t≈18.960**, con la cara en `y=42` |

O sea: el criterio del traspaso («que `hechas` suba y `pasos` siga creciendo sin que baje lo que produce el taller») se
cumple `hechas` **24/24**, `pasos` **40** y el pedernal del almacén **subiendo a su objetivo**; y el minero, que antes
se pasaba la corrida colando, **completa la misma galería en la mitad de ticks**.

### I144 · EL SEGUNDO POZO DE LA MINA: se elige **solo** cuando el primero se topa (28-sep-2026)

Lo pidió el jugador (*«estaría bien abrir un segundo pozo»*). **Implementado y medido.**

**La implementación es pequeña porque TODO depende de `centroDeLaMina`** (11 usos, todos en `VillageGenerator`):
`MINA_OFFSETS = {(33,0,-29), (-33,0,29)}` y `centroDeLaMina(center, pozo)`; `centroDeLaMina(center)` lee **el pozo
activo** de un caché por aldea (como el de `cotaDeLaPlaza`), así que **ninguna firma cambia** y el minero solo tuvo que
añadir **una línea** (`elegirElPozoActivo`, que devuelve el primer pozo, dando la vuelta desde el activo, que **no esté
terminado**; si todos lo están, se queda donde estaba). Y las **dos comprobaciones de exclusión**
(`esCeldaDeLaMina`, `estaSobreElPozo`) ahora miran **todos** los pozos: si no, el nivelado habría rellenado el caracol
del segundo con tierra y el tapagujeros, su boca. (lint I9 justificado: el pozo lo cava el minero, no se construye.)

**MEDIDO** (`build/medida-pozo2.log`; el arnés da el pozo 1 por terminado a los 20 s con su piedra labrada de tope):

| criterio | resultado |
|---|---|
| el minero **elige** el pozo 2 | el censo `POZO` pasa a medir **`eje=437, 675`** |
| el pozo 2 **se cava** | `caracol paso 12 … 16 en 437/436/…/433, 679` (**16 pasos**) |
| su boca | `441, 62, 671` sobre hierba, **0 celdas construidas** |
| su subsuelo | **0 celdas con agua** en el caracol (el pozo 1 tiene 2 y se le inundaron los pasos 35-44) |
| la separación | las zonas de exclusión de los dos pozos quedan **separadas 30 bloques** |

**Y UN ERROR DE MEDIDA MÍO, que queda escrito para que no se repita**: la primera tabla de candidatos la medí **desde
el eje del pozo 1 (503,617) creyendo que era el centro de la aldea**. El centro es **470,63,646** (el `CENTRO` del
arnés) y `503,617` es `centro + (33,-29)`, o sea **el eje del pozo 1**. Con ese error, el «suroeste» que elegí
(470,646) era **la propia plaza** y «el acuífero del pozo 1» un artefacto (lo que di con agua era `536,588`, que no es
el eje de nada). Medido bien, **los cuatro candidatos tienen 0 agua** en la galería del paso 16 y **el criterio que
decide es la separación**: el simétrico deja **30** bloques entre las dos zonas de exclusión y los otros dos solo
**8** (sus cilindros se solaparían). El offset elegido **era el correcto** y **no hubo que tocar el código**: el eje
que el juego calculó (**437,675**) es exactamente el medido, y su caracol **no tiene agua**.

### I143 · EL GRANJERO ENCERRADO AL ANOCHECER: **LA COMPUERTA LA ABRE EL PUEBLO, NO SU GOAL** (28-sep-2026)

**Lo reportó el jugador**: *«hay un granjero que se atoró en una de las parcelas. dice que va a la cama, ya se hizo de
noche pero no puede ir»*. El mod **ya tenía** un arreglo para este caso (la tarea `SALIR` del granjero, que al
anochecer lo manda a la compuerta), pero **seguía pasando** y **no estaba medido**.

**Por qué no se había cazado**: **todas** las corridas del arnés de esta sesión son **de DÍA** (`setDayTime(6000)`) y
este bug es **de noche**. Con el modo `MEDIR_NOCHE` (18.000) y su instrumento (`vigilarGranjerosEnElBancal`, que
imprime el bancal, los goals **que están corriendo**, el destino, la cama y **las cuatro compuertas**), el
diagnóstico salió en una corrida (`build/medida-noche-antes.log`):

```
EN-BANCAL 596e09a8 farmer dentro=2 … durmiendo=false REST=true home=428,63,671
          destino=SIN DESTINO goals=[VillagerGateGoal]
          compuertas: 446,63,679=cerrada 446,63,689=cerrada 441,63,684=cerrada 451,63,684=cerrada
[Village] … esta encerrado en un bancal … el goal del granjero lo saca por la compuerta al anochecer
```

**La causa, medida**: el granjero se queda **de pie y sin destino** dentro del bancal —**19 de 38 muestras**— porque
**su goal no llega ni a arrancar**: pegado a la valla, el `VillageGateGoal` está corriendo, los dos piden el flag de
movimiento `MOVE` y **el del portón tiene más prioridad**, así que le **roba el control**. La rama de SALIR confiaba
en que el portón se abriera solo (su comentario lo decía) y **el aviso del latido prometía algo que no podía pasar**
(*«el goal del granjero lo saca por la compuerta»*).

**Y el primer arreglo NO funcionó, medido**: abrir la compuerta **desde el goal** (en su rama de SALIR, como hace al
entrar) **no cambia ni una muestra** (19 de 38 otra vez), porque **ese `tick` no se ejecuta nunca**: el goal no
arranca. (En el «después» el destino era **la cama** —el `WALK_TARGET` del cerebro— o nada, **nunca la compuerta**,
que es lo que pondría su tarea de SALIR.)

**EL ARREGLO**: que la abra **el latido del pueblo**, que **sí** corre siempre y **ya** detectaba al encerrado —
`VillageManager.abrirLaCompuertaAlQueEstaEncerrado`, llamado desde `atenderCamasDelPueblo`: al aldeano que está
**dentro de un bancal y en su hora de descanso** se le abre la compuerta de **su** bancal (el mismo mecanismo que usa
el granjero al **entrar**: `abrirLaCompuertaDeAlLado` + `salidaDeLaParcela`). Así no depende de prioridades de goals.
Solo se abre mientras está dentro y descansando: lo que tarda en cruzar.

**MEDIDO** (`build/medida-noche-despues2.log`): las muestras dentro del bancal pasan de **38 a 4** (de ≈76 s a ≈8 s) y
las de `destino=SIN DESTINO` de **19 a 1**; la compuerta sale **ABIERTA** a los pocos segundos y el granjero se va a
dormir. Datos crudos en `tools/arnes/medidas-granjero-noche.txt`.

### I142 · LA MINA SE **ENCIENDE**: al cavar no basta, hay que **REPASAR** las antorchas que faltan (28-sep-2026)

**Lo reportó el jugador**: *«el minero no está poniendo antorchas en las paredes de las escaleras de caracol ni en
las galerías y debe poner porque se ve muy oscuro y es un punto peligroso para que spawneen mobs»*. Y era verdad.

**La causa, medida**: la antorcha se pone **al cavar la celda** (`ponerLaAntorcha` solo se llama en la rama
`if (floorMod(paso, CELDAS_POR_ANTORCHA) == 0)` del caracol y de la galería) y ese método **sale sin poner nada si en
ese momento el minero no lleva antorchas**. Y el minero **cava antes de tenerlas**: el **carbón sale de la mina**, así
que las primeras vueltas son a oscuras. Con los tiempos del log: cavó la celda 8 de la galería del paso 32 a las
**03:31:17** y fabricó las antorchas a las **03:34:50**. Y como **nunca volvía a pasar por esas celdas**, se quedaban
oscuras **para siempre**.

**El «antes», medido con el censo del arnés** (`build/medida-plaza3.log`): el caracol con sus pasos **0, 8, 16, 24 y
32** y la celda de la cabeza en `air` (ni una antorcha), la galería del paso 32 con sus celdas **8 y 16** en `+1=air`,
y el minero con **8 antorchas en el zurrón SIN GASTAR** (constantes en todas las muestras) — las tenía y no las ponía.

**El arreglo: la fase `ENCENDER`.** `buscarHuecoDeLuz` devuelve la primera celda de la mina **a la que le falta su
antorcha**, buscando **del frente hacia la boca** (lo más cerca del minero primero): en el caracol los pasos múltiplos
de `CELDAS_POR_ANTORCHA` **ya cavados**, y en las galerías abiertas las celdas **8, 16 y 24** ya cavadas. Si el minero
**lleva antorchas** y hay hueco, el `canUse` le pone la fase `ENCENDER` **antes** de seguir cavando, camina a la
casilla de pie de ese hueco y la pone (el alcance se mide a la antorcha, no a donde se para). Si no puede ponerla
(sin pared o sin antorchas) el hueco se **apunta como fallido** y sigue: no se queda en bucle con él. Como va del
frente hacia la boca, **enciende la mina entera en unas pocas vueltas** y sin desviarse apenas (el caracol se anda al
subir y al bajar al taller).

**MEDIDO** (`build/medida-antorchas.log` y `build/medida-luz-1.log`): los pasos del caracol **0, 8, 16, 24 y 32**
pasan de `air` a **`wall_torch`** (los cinco), la **celda 8 de la galería** de `air` a **`wall_torch`**, y el zurrón del
minero pasa de **8 antorchas sin gastar** a gastarlas (`El minero: encendio 499, 55, 629`, con la etiqueta `Enciende
la mina`; **8 encendidos** en una corrida, en la boca, en el caracol y en **tres galerías**). Y en la corrida lanzada
**sobre el guardado del jugador** (con su mina a oscuras) el repaso la enciende igual, **sin tocar el guardado**. De
paso quedó medido lo que decía el jugador: **el carbón no sale de la mina**, sale de la **leña del leñador quemada en
el horno** (`quema un tronco en el horno` **6 veces** en esa corrida).

**Y UNA PARTE QUE NO SE CUMPLÍA AL PRINCIPIO, ya corregida y medida**: la idea de **no bajar sin luz** (que
`hayQueSubir` pida antorchas también en la primera bajada, si el pueblo puede dárselas) **no llegaba a tiempo**: en
`medida-luz-2.log` el minero ya había cavado 8 celdas **sin una sola antorcha**. La causa, medida: al salir del
almacén la rama `RECOGER` **forzaba `fase = CAVAR`** (*«CARGADO: ahora HAY que recalcular la faena»*) y **se saltaba el
taller**, aunque `hayQueSubir` siguiera pidiendo luz. **Arreglo**: en esa rama, si le falta luz y **el pueblo puede
dársela** (antorchas hechas, o carbón/leña del almacén **o del zurrón**, siempre con palos), va al **taller** en vez de
a cavar. **MEDIDO** (`build/medida-luz-pico.log`), el orden es ahora: `quema un tronco en el horno` → `hace 4
antorchas` (dos veces) → **y solo después** `caracol paso 0`. O sea: **se hace la luz antes de bajar**.

### I141 · EL PICO DEL MINERO: EL CICLO **CERRADO Y MEDIDO** (y no hizo falta código) (27-sep-2026)

El §6 del traspaso llevaba desde el 26-sep abierto con la pregunta «el minero suelta la faena al romperse el pico,
pero **¿vuelve con otro?**». **La respuesta ya estaba en los logs de esta sesión**, y se ve **cuatro veces** (el
arnés deja el pico al borde de romperse cada 2.000 ticks, dentro de `medirElMinero`):

| corrida | roturas | `pico nuevo` en su mano | forjados por el herrero |
|---|---|---|---|
| `medida-tramo.log` | 6 | **6** | 6 |
| `medida-s7-final.log` | 6 | 5 | 5 |
| `medida-balsa-final.log` | 6 | 5 | 5 |
| `medida-s8.log` | 8 | **8** | 7 |

El ciclo, con las líneas del log (tal cual):

```
El minero: se le ha roto el pico (59 usos): va a por otro al almacen
El minero: yendo: Yendo al almacen (Cargando material -> 517, 63, 666)
MINERO t=3080 ... pico=SIN PICO(0/0) ... etiqueta=Wenceslao (Minero) | Cargando material
El minero: pico nuevo: minecraft:wooden_pickaxe (Cargando material -> 517, 63, 666)
```

O sea: **detecta la rotura, suelta la faena, va al almacén, espera si no hay pico y vuelve con uno** — los
5-8 picos nuevos por corrida son las 6-8 roturas forzadas menos las que pilló el final de la corrida.

**Y LA RESERVA YA EXISTE EN EL CÓDIGO** (por eso no hubo que tocar el mod): el **pico del minero va PRIMERO** en el
herrero de herramientas (`VillagerSmithGoal.recetaDeArmadura` → `recetaDePico` antes que la armadura, y con
`OBJETIVO_PICOS = 2`), y `recetaDePico` **baja de nivel** hasta el **pico de madera** (3 tablones y 2 palos hay
siempre) cuando no hay hierro — el «cebo del pico roto» que el proyecto ya documentó. MEDIDO aquí: el herrero forjó
**4 picos de madera y 2 de piedra** y el minero recibió **6 picos de madera** con el almacén en
`0 lingote(s), 0 crudo(s)`.

**LA PISTA QUE QUEDA (medida, no arreglada)**: el minero coge **picos de madera** aunque el herrero forje también de
**piedra** (`4 madera / 2 piedra` forjados, `6 madera` recibidos). El de madera pica **piedra**, así que la mina
avanza, pero **más despacio**. Si se quiere afinar, el siguiente paso es que el minero **prefiera el mejor pico** del
almacén; **no se toca ahora** porque no hay medida que lo pida (la mina avanza: `pasos` 32-44).

**Y LA MEJORA YA ESTÁ MEDIDA (28-sep-2026)**. Con el arnés poniendo en el almacén **madera primero y piedra después**
—que es justo lo que el comportamiento viejo cogía—, el minero recibió **`minecraft:stone_pickaxe` las 6 veces** y
**ninguna de madera** (`build/medida-luz-pico.log`). Así que el arreglo (pedirlos en orden de mejor a peor) está
**medido en los dos sentidos**: idéntico cuando solo hay un material, y **mejor** cuando hay varios.

### I140 · EL «PUNTO DE AHORA»: LA CASILLA DE PIE Y EL TRAMO, CON **SU** CONTADOR (y el intento global que se retiró)

**El problema de fondo, dicho sin adornos**: los «no consigue llegar» se estaban arreglando **goal a goal** (el
granjero, la recolectora, el ganadero, el leñador, el guardia…) y **cada arreglo dejaba a los demás igual de rotos**,
así que siempre salía el caso siguiente. Mirando los avisos que quedaban, las causas eran **dos**, y las dos valían
para **todo** el pueblo:

1. **SE CAMINABA A LA CELDA CRUDA.** Lo que un aldeano persigue muchas veces **no se pisa**: la celda de un objeto
   caído, un **cultivo**, una **valla**, **la campana del kiosco** (el punto de apoyo que devuelve el pueblo es
   `stone_bricks` con `bell` encima), una puerta… y el planificador **no da ruta** hasta una celda que no se pisa.
   MEDIDO en los avisos del arnés: `Vicenta (Ganadero) / Recogiendo el corral` a una valla (`513,63,640`),
   `Filomena (Recolector)` a la campana (`470,63,646`) y los cuatro goals que iban a por un objeto caído a **la celda
   del objeto**.
2. **LOS DESTINOS A MÁS DE ~56 BLOQUES NO TIENEN RUTA NINGUNA.** La región de búsqueda del planificador son
   `FOLLOW_RANGE + 8` = **56** bloques **alrededor del aldeano** y su heurística es una recta. MEDIDO: el recolector
   yendo al **almacén** desde la huerta (`446,62,688` → `517,63,666`, **70+ bloques**) con
   `ruta=38 nodos hasta 481,63,676 alcanza=NO`, en **bucle de 5-8 avisos** del mismo aldeano y la misma celda. El
   leñador y el minero ya lo resolvían a mano con el **tirón** (`VillageManager.tironHacia`); el resto de los goals,
   no.

**EL INTENTO QUE SE RETIRÓ, y por qué (una corrida pagada)**. Lo primero que probé fue meter las dos reglas **dentro de
`VillageManager.caminarHacia`** —el único sitio por el que pasan todos los caminos del pueblo— para que valieran para
todos los goals sin tocarlos. **NO FUNCIONA, y está medido**: con el aldeano caminando a un **tramo** y su goal
midiendo el atasco contra el **destino final** (`if (distancia < mejorDistancia - 0.5D)`, que es lo que hacen todos los
goals), el contador se dispara **antes** —«no me acerco» se está midiendo contra el sitio equivocado— y los avisos de
rendición **subieron de 10 a 11** y la tasa **de 0,60 a 1,21** (`build/medida-reglas-globales-fallida.log`). La regla
global es una **trampa**: quien camina y quien cuenta tienen que estar de acuerdo en **contra qué** se mide.

**EL ARREGLO BUENO, que es el patrón que el leñador ya tenía (I112/I38) y ahora está en un solo sitio**:
`VillageManager.elPuntoDeAhora(level, villager, destino)` devuelve **el sitio al que hay que caminar AHORA**:

* **una casilla de pie** (`casillaDePieCercaDe`, I114/I131) — idempotente: si el destino ya se pisa, no se toca nada;
* y si está **más lejos** que `ALCANCE_DE_LA_RUTA` (**40**, con margen sobre los 56 del planificador), **un tramo** a
  `PASOS_DEL_TIRON` (**28**) bloques en dirección al destino. Ese tramo **no pide ruta** (solo mira bloques), así que
  se puede calcular **cada tick** (el coste que obligaba a `tironConMemoria` no aplica aquí).

**Y EL CONTRATO, que es lo que hace que no rompa nada**: el goal camina a ese punto **y mide su atasco contra ÉL**
(reiniciando el contador cuando el punto cambia), no contra el destino final. **Eso** es lo que permite un viaje
largo: el aldeano va tramo a tramo y cada tramo tiene su propio contador, así que **nunca** se rinde «a mitad de
camino» por una distancia que no puede bajar. Aplicado a la rama que fallaba (`VillagerCollectGoal`, el viaje al
almacén desde la huerta). **MEDIDO** (`build/medida-tramo.log`, 15.440 ticks): el bucle `Saliendo de la huerta`
**8 → 1**; los avisos de rendición del pueblo **18 → 7**; y la tasa **0,30** por 1.000 ticks (contra el **1,21** del
intento global retirado).

**Lo que queda** (avisos sueltos, 1-2 cada uno, **sin bucles**): 2 del ganadero recogiendo algo sobre **mobiliario**
(una valla), 2 del **clérigo** yendo a su iglesia (su goal todavía camina al puesto crudo), 1 de `Patrullando la
aldea` y 1 de `Yendo a entrenar` (clase I119). El patrón del «punto de ahora» se les puede aplicar igual (es el mismo
contrato), pero **no hay medida que lo pida todavía**: no tienen bucle.

**LA SEGUNDA CORRECCIÓN, medida también** (`build/medida-tiron-final.log`): un tramo calculado a mano
—`celdaDePieHacia`, que solo mira bloques— **no comprueba que haya ruta hasta él**, así que puede dejar al aldeano
clavado con **el cerebro pisándole el rumbo**. MEDIDO: el recolector volviendo a la **plaza** (cuyo destino es **la
campana del kiosco**, `stone_bricks` con `bell` encima) se rindió **10-15 veces en bucle**, cada 240 ticks, con
`cerebro=531,63,646` y `nav=[sin ruta]`. Arreglo: **el tramo lo da el tirón del proyecto**
(`tironConMemoria` → `tironHacia`), que **prueba la ruta de cada tramo** (`createPath`), **cruza el muro por el
portón**, **cae al hub del pueblo** si ninguno tiene ruta y **camina con `caminarHaciaExacto`**, que le pide la ruta
**a mano** a la navegación (por eso el cerebro no le quita el rumbo). Y encima se le pone la **casilla de pie** del
destino. **MEDIDO: `Volviendo a la plaza` 15 → 1.**

**EL ESTADO DEL PUEBLO, medido con el código final**: **8 avisos de rendición, TODOS SUELTOS** (el mayor, **2**), de
**siete aldeanos distintos** y **NINGÚN BUCLE** (`build/medida-plaza3.log`, 17.880 ticks), con las **tres** clases que
se han arreglado a **cero**: `Volviendo a la plaza` **5 → 0**, `Yendo a la iglesia` **0** y `Patrullando` **0**. La
**tasa del pueblo** oscila (**0,10 · 0,20 · 0,30 · 0,50 · 0,60**; media **0,30**) contra el **1,29** de la tanda
anterior del proyecto y el **0,50-0,60** de la referencia de la mañana. El ciclo del pico, medido otra vez:
**7 roturas → 6 picos nuevos**.

**Y DOS CIERRES MÁS DE ESTA MISMA NOCHE, los dos por la misma regla**:

1. **EL AVANCE POR LA RUTA, EN EL PUEBLO** (`VillageManager.avanzaPorLaRuta`, invariantes I125/I139): el contador de
   atasco mide **solo la distancia en línea recta** y hay dos casos en los que esa recta no representa nada —**un
   rodeo** (la ronda del guardia) y **el cerebro yendo a su POI**—. La señal que dice la verdad es **el índice del
   nodo** que persigue la ruta viva (guardado en los datos del aldeano, así lo usa cualquier goal con una llamada); un
   **recálculo** del planificador reinicia el índice y **no** cuenta como avance (si contara, un aldeano empujando una
   pared se resetearía el contador solo: el bucle de I3). MEDIDO: el **clérigo 2 → 0** (`cerebro=452,64,603` con
   `destino=453,64,603`: iba a su POI mientras el goal medía contra el soporte) y el **guardia sigue en 0**.
2. **A LA PLAZA SE LLEGA AL PUNTO DE PIE, NO A LA CAMPANA**: el destino de «volver a la plaza» es **la campana**
   (`stone_bricks` con `bell` encima), que **no se pisa**: el aldeano que ya estaba en la plaza se quedaba a 3-4
   bloques de ella, la distancia no bajaba del alcance y el goal lo aparcaba **cada 240 ticks en bucle** (MEDIDO:
   **5 avisos**). Arreglo: si está a alcance del **punto de pie** de la plaza, la vuelta se acabó. MEDIDO: **5 → 0**.

**Y UNA CORRIDA TIRADA, que se apunta para no repetirla**: lancé una corrida **con la anterior todavía viva** (matar
el `gradlew` no mata el servidor) y los **dos** servidores escribieron en el **mismo** `latest.log` y el **mismo**
`run/world`: la medida parecía buena (0,30) y **no valía nada** — de hecho los «15 avisos de la plaza» que me
asustaron eran del **código viejo** de la otra corrida. El trámite de medida ya lleva el paso obligatorio: **cero
servidores vivos antes de lanzar**, y borrar el `latest.log` **sin** silenciar el error.

### I139 · EL ATASCO SE MIDE POR EL **AVANCE POR LA RUTA** (y el que va a por un objeto, por una CASILLA DE PIE)

Los dos atascos sueltos que quedaban en el pueblo, **medidos con el desglose por etiquetas** y arreglados los dos.

**1) LA RONDA DEL GUARDIA NO ES UN ATASCO (pero se rendía).** El contador de atasco miraba **solo la distancia en
línea recta** (`if (distancia < mejorDistancia - 0.5D)`), y la ronda es un **círculo**: en un rodeo la recta **sube**
aunque el guardia vaya bien por su camino. MEDIDO el 27-sep-2026 en los dos logs de la sesión: cinco guardias rendidos
con **`ruta=16-32 nodos … alcanza=SI`** y `nav=[… alcanza]`, o sea **con camino y andando** — un destino a 20 bloques
con una ruta de **30 nodos** (`Ubaldo / Patrullando el corral`). El arreglo: además de acercarse, cuenta como progreso
**consumir nodos de la ruta viva** (el nodo que persigue cambia), y **solo dentro de la MISMA ruta**: si el
planificador la ha vuelto a calcular (objeto `Path` nuevo) **no** cuenta, porque si no un guardia empujando una pared
—que recalcula cada pocos ticks— se resetearía el contador solo y no se rendiría nunca (el bucle de I3 que este
contador evita). MEDIDO: avisos de guardia patrullando **9 → 0** (`build/medida-guardia-ruta.log` contra
`build/medida-balsa-final.log`).

**2) AL OBJETO CAÍDO SE VA POR UNA CASILLA DE PIE.** Los que se rendían eran los goals que van a **recoger objetos**
(`VillagerCollectGoal` el recolector, `VillagerPickupGoal` los oficios, `VillagerAnimalFarmGoal` el ganadero,
`VillagerFarmGoal` el granjero) caminando a **`objetivo.blockPosition()`**: la celda **cruda** donde está el objeto. Y
lo que se cae puede quedar **encima de algo que no se pisa**, así que el planificador **no da ruta** hasta ahí. MEDIDO
el 27-sep-2026 (avisos del log):

| aviso | el objeto estaba sobre… |
|---|---|
| `Vicenta (Ganadero) / Recogiendo el corral` (x4) | la **mesa de la taberna** (`516,64,639`, `air` con `oak_planks` encima) y una **valla** (`513,63,640`, `destino=oak_fence`) |
| `Valeriano / Hipolito (Granjero) / Guardando lo suyo` | **dentro** del bancal, con `pies=farmland cabeza=wheat` (`ruta=1 nodos … alcanza=NO`) |
| `Filomena (Recolector) / Yendo a la taberna` | la mesa otra vez (`ruta=41 nodos … alcanza=NO`) |

Es la **misma regla de I114/I131** (se camina a una **casilla de pie**), aplicada a los cuatro goals, y además se les
llama a **`VillageManager.desatascarSiEstaEncajado`** antes de contar atasco (el ayudante que ya usaba el leñador desde
I122: el granjero con los pies **dentro de la farmland** no tiene ruta y se rendía por un atasco que no era suyo).

**3) Y LA RAÍZ DE LOS DOS ÚLTIMOS: `esCeldaDePie` NO DEJABA PISAR UN CULTIVO.** La regla pedía `isAir()` **a los pies**,
y un bancal tiene trigo o zanahorias **en la celda de los pies**: o sea que **no había ni una casilla de pie dentro del
bancal** — y un aldeano **sí anda por encima de los cultivos** (es lo que hace al cosechar; el planificador les da esos
nodos por buenos). Consecuencia medida: el granjero hundido en la farmland **no tenía a dónde salir** (ni
`desatascarSiEstaEncajado` encontraba casilla) y el que va a por un objeto caído **sobre el trigo**
(`destino=farmland encima=wheat`) no tenía casilla de pie cerca. Arreglo: `sePisaALosPies` = **aire + los cultivos**
(trigo, zanahorias, patatas, betabel, los tallos) **+ la hierba** (corta, alta, helecho). **NO** entra la **farmland**:
es el SUELO (sólido) y darla por pisable sería declarar bueno justo al aldeano hundido que hay que sacar.
Y la **misma regla** se le puso a `casillaPisableCercaDe` (la que usa el desatasco para elegir a dónde sacarlo): pedía
`isAir()` y **devolvía `null` dentro de un bancal** — medido: 14 desatascos reales en una corrida (puertas y vallas) y,
en cambio, un granjero hundido en la farmland en **bucle de 7 avisos**, el mismo aldeano y la misma celda cada ~13 s
(justo por encima del freno de 200 ticks del desatasco). Con las dos reglas unificadas hay a dónde sacarlo.

**MEDIDO** (misma partida; datos crudos en `tools/arnes/medidas-atascos-sueltos.txt`). Corrida buena:
`build/medida-s7-final.log`, 15.040 ticks — **5 avisos de rendición contra 18** de la referencia:

| criterio | antes (`medida-balsa-final`) | después (`medida-s7-final`) |
|---|---|---|
| `Patrullando el corral` / `Patrullando la aldea` | **9** avisos | **2** |
| `Recogiendo el corral` (el ganadero) | 4 | **0** |
| `Guardando lo suyo` (los oficios que recogen) | 5 | **0** |
| `Yendo a la taberna` (el recolector yendo a la mesa) | 1-2 | **0** |
| el granjero hundido en la farmland, en bucle | **7** avisos del mismo aldeano | **1** |
| **la tasa del pueblo** (I135, ventana 2.000-12.000) | 0,50-0,60 | **0,30** |

**Lo que NO queda a cero, y con su número** (corrida buena `medida-s7-final.log`, 5 avisos en 15.040 ticks; y
`medida-s8.log`, 10 avisos en 18.480):
- **los viajes largos sin tirón** (el que más pesa: **5** avisos del mismo aldeano, `Filomena (Recolector) / Saliendo
  de la huerta`): va al **almacén** desde la huerta —**70+ bloques**— y el planificador **no llega**
  (`ruta=38 nodos hasta 481,63,676 alcanza=NO`). Arreglo que toca: el **tirón** intermedio que ya usan el leñador y el
  minero (`VillageManager.tironHacia`), aplicado a los viajes del recolector al almacén;
- **el destino que es mobiliario** (2 avisos): el ganadero a una **valla** (`513,63,640`) y el recolector a **la campana
  del kiosco** (`470,63,646`, `stone_bricks` con `bell` encima): el punto de apoyo que devuelve el pueblo **no es una
  celda que se pise** y hay goals que caminan a él **crudo** → la regla de I114/I131, pero en el **destino de la faena**;
- **el hundimiento en la farmland** (1 aviso): el bucle desaparece (**46 desatascos reales** en una corrida, 40 de
  ellos de **puertas**) pero el aldeano **vuelve a meterse**: falta registrar el Y exacto y el movimiento anterior.

**Y EL AVISO DE RENDICIÓN, CUANDO EL CEREBRO VA A OTRA PARTE.** Los 2 avisos de guardia patrullando de la corrida
buena tenían `cerebro=` apuntando a un sitio **distinto** del `destino=` (I125: el paseo o el goal de los portones le
pisan el rumbo): el guardia **salta el puesto y sigue la ronda** —que es lo correcto— pero se contaba como
«rendición», y eso **ensuciaba el instrumento** de la tasa (I135 cuenta rendiciones). Arreglo: solo se apunta el punto
como fallido y se canta el aviso **si el cerebro va de verdad al destino** (`elCerebroVaA`). **MEDIDO**:
`Patrullando` **9 → 0** (`medida-s8.log`).

**Y DOS LECCIONES DE MÉTODO de esta cadena**: (1) una corrida intermedia **pareció una regresión** (11 avisos) y era el
instrumento diciendo la verdad —el desatasco disparaba (**14** desatascos reales) pero **no tenía a dónde sacarlo**—;
sin el desglose por etiqueta y sin el aviso `estaba METIDO en … lo saco a …` no se habría visto. Y (2) **la tasa
oscila** (0,30 · 0,40 · 0,60 en corridas comparables), que es exactamente lo que dice I135: la prueba de un arreglo es
**su etiqueta**, no el total.

## 2. Lista de consecuencias (obligatoria en cada cambio)Antes de escribir el commit, para CADA valor, bloque, contador o comportamiento que toco:


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
   antiguo), aldea **caída** (ruinas), **chunk descargado**, **jugador ausente** (¡un reloj que corre sin él puede
   perder una aldea entera: ver **I86**!), aldeas a **200 bloques** entre sí.
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
| `build/combustible_aldea.py` | **Los aparatos que queman y la madera del almacén** (I41/I42): barre el guardado y lista ahumador, hornos, hogar y soporte de pociones con sus coordenadas, más el contenido de los cofres con la madera separada (`COMBUSTIBLE`). |
| `build/cama_toolsmith.py` | **¿Por qué una cama no se puede usar?** (I43): imprime la rejilla de bloques alrededor de una cama en las capas que se pisan, que es lo que delata el muro o el mobiliario que impide ponerse a ≤2,0 para acostarse. |
| `build/mapa_cama.py` | **Mapa compacto de una zona del guardado** (una letra por bloque, con camas, vallas, compuertas, muros y suelos): es lo que enseñó la habitación tapiada del herrero. Ojo: los símbolos son de una letra (`O` = oak door **no** "abierta": para el estado, leer las propiedades). |
| `build/faroles_hanging.py` | **Faroles sin apoyo de verdad** (I14): mira la propiedad `hanging` contra su dirección, que es lo que **no** mira la auditoría de Python (una valla debajo vale para un farol *posado*, no para uno *colgado*; la de Java sí lo mira desde la migración 55). Dice qué reparador arregla cada uno. |
| `build/kiosco_dump.py` | **El kiosco entero, capa a capa** (I28): cuenta los bloques por capa, imprime la huella de `cota-2` a `cota+7` y localiza la **campana**, el **farol** y el **beacon** con sus propiedades (dónde están y en qué celda relativa al centro). |
| `build/aldeanos.py`, `build/aldeanos.py` | Aldeanos: profesión, inventario, posición (carpeta `entities/`). |
| `build/herreria.py`, `build/huecos_ore.py`, `build/solares*.py` | Herrería, huecos y minerales flotantes, solares libres. |
| `build/plantillas*.py`, `build/paleta.py` | Plantillas del juego: tamaños, puertas y qué bloques traen. |
| `tools/audita_aldea.py` (**versionada**) | **Auditoría de las aldeas enteras**: faroles y vallas flotando, cofres tapados, puertas incompletas, camas sueltas y **portones con el hueco tapado** (I54). Lee las PROPIEDADES de los bloques y saca las aldeas del guardado (índice, centro y cota): `--aldea N`, `--caidas`, `--resumen`, `--centro X Z --cota N`. |
| `tools/arnes/ruta_atasco.py` (**versionada**) | **¿HAY RUTA de pie entre dos celdas?** Recorrido en anchura sobre los bloques del guardado con la regla de *casilla de pie* (aire a los pies y a la cabeza, suelo firme debajo), movimientos a los 4 lados y **±1 de altura**: dice `HAY RUTA: N pasos` con el camino entero, o hasta dónde llega. **Es la herramienta que decidió I112** (del atasco `527,63,672` al almacén `517,63,666` hay ruta, pero son 68 pasos y **empieza yendo al lado contrario**). |
| `tools/arnes/portones_del_muro.py` (**versionada**) | **Los cuatro portones cardinales del muro**: aplica `esCeldaDePie` a la **casilla de paso de dentro y de fuera** de cada uno (a la cota del pueblo) y dice `pisable=SI/NO` con los bloques que hay. Es lo que comprueba que la regla de I112 tiene a dónde mandar al aldeano. |
| `build/aldeanos_todos.py` (ignorado) | **TODAS las entidades "villager" de un radio del guardado**, con su id real (`villager` **y** `zombie_villager`), sus `CustomName` (la etiqueta de dos líneas), su oficio, su cama y su **UUID formateado**. Es lo que distinguió "aldeano sin cama" de "cría sin cama" y de "aldeano-zombi dentro del recinto" (3b.61). |
| `build/plantilla_casa.py` (ignorado) | **La plantilla del juego, capa a capa**: lee los `.nbt` de `village/plains/houses/*` del jar del cliente (van comprimidos con gzip) y vuelca tamaño y vista de planta. Es lo que dice si un hueco de una casa "viene del juego" o lo perdió el mundo (3b.65/I50). |
| `build/plano_celda.py` (ignorado) | **El PLANO de la aldea del `devilrpg_villages.dat`**: saca `Blueprints -> [Index, Palette, Pos(long[]), State(int[])]` y contesta si una celda está en el plano (y con qué bloque) y qué dicen sus vecinas. Es lo que demostró que el hueco de la pared **no estaba en el plano** y por eso el obrero no lo reponía (3b.65/I50). |
| `build/casa_hueco.py` (ignorado) | **Vista de planta de una zona del guardado** (x/y/z por capas, con códigos por bloque) alrededor de la casa que señaló el jugador: así se ven el hueco de la pared, la puerta, la ventana y el cofre de al lado. |
| `build/huerta_items.py` (ignorado) | **Lo que hay tirado y cómo están los bancales**: cuenta las entidades de objeto del recinto por tipo (con edad y posición y si están dentro de un bancal) y vuelca los tres bancales capa a capa. Es la medida de partida de 3b.66/I51. |
| `build/cocina_medida.py` (ignorado) | **¿Desde DÓNDE cocinaba el cocinero?**: del log del arnés coge, para cada `N pieza(s) cocinadas`, la muestra `COCINERO pos=…` **inmediatamente anterior** (con `dentroDeLaTaberna`, `VEelAhumador`, `dCasilla`, `dAhumador` y la etiqueta). Es la medida de 3b.63/I48. |
| `build/hambre_medida.py` (ignorado) | **La comida de la aldea y el reloj de cada aldeano** (I52): la `Food` y el `StarvingSince` de cada asentamiento, el `gameTime` del guardado y la marca `DevilRpgUltimaComida` de **cada** aldeano con su oficio, su posición y los minutos que lleva sin comer. Es lo que distingue "la despensa está vacía" de "este aldeano no ha comido" —y lo que enseñó que las **crías** viven con la marca del día que nacieron hasta que crecen. |
| `build/huerta_vacias.py` (ignorado) | **Las celdas del bancal que no tienen nada** (I53): vuelca los tres bancales **celda a celda** (cultivo con su edad, tierra vacía, calva, acequia) con un mapa de una letra por celda y cuenta las que están `farmland` con el hueco de arriba libre. |
| `build/portones_farol.py`, `build/anexo_porton.py` (ignorados) | **El hueco de los portones y el farol que lo tapa** (I54): volcan los 14 portones de las tres aldeas (los 12 de los bancales, el del corral y el del gallinero), miran las **seis celdas** por las que se cruza cada uno (la hoja y las dos de al lado, en las dos capas) y dicen qué hay en ellas **y qué pide el PLANO**; `anexo_porton.py` pinta además el corral y sus dos portones capa a capa. |
| `build/aldeanos_equipo.py` (ignorado) | **Cada aldeano con su equipo**: su etiqueta (nombre + actividad), oficio, posición, **si está durmiendo**, su **cama** (`HOME`), su destino, **todo su inventario** y las marcas del mod; y el **contenido de los cofres** de la zona (donde el herrero deja lo que forja). Es la medida de I55/I56/I57 (la cama de otra planta, la armadura que no se fabrica, la espada que se queda en el cofre). |
| `build/obras_pendientes.py` (ignorado) | **Las obras pendientes de una aldea** (I60): recorre el **plano** celda a celda y lo compara con el mundo (con la regla de `necesitaReparacion`), dice cuántas celdas están pendientes, de qué bloque, a qué distancia del centro y a qué **altura sobre la cota** (para ver lo que se sale de la banda del obrero), y lista los aldeanos marcados como **obrero**. Es lo que demostró que la muralla dañada **no estaba en el plano**. |
| `build/almacen_mapa.py` (ignorado) | **El almacén, capa a capa** (I95): vuelca los bloques de su recuadro a las capas de la cota, encima y debajo, y enseña los bloques clave (el punto de apoyo, los cofres, los postes). Es lo que midió que el suelo estaba **en la cota** (un escalón de 1,0) con el césped del pueblo en `cota - 1`. |
| `build/gallinero_medida.py` (ignorado) | **¿Se alcanza lo que cae en el corralillo sin entrar en él?** (I96/I97): aplica **la misma cuenta que el goal** (distancia 3D de los pies del aldeano al centro de la celda del objeto) a **cada** celda del gallinero, con los dos alcances (el viejo y el nuevo), y cuenta las gallinas que tiene el portón pegadas. Es lo que midió que con 1,8 **no se alcanzaba ninguna** celda desde fuera y que con 3,5 se alcanzan **todas**. |
| `build/diag_ganadero.py`, `build/marcas_aldeano.py` (ignorados) | **El ganadero y sus marcas**: los animales y los objetos del corral con su edad, y el NBT **crudo** de un aldeano (dónde vive `DevilRpgPuntoFallido` y qué vale). Es lo que enseñó que el ganadero tenía **4 huevos** en el zurrón y el punto del almacén **aparcado**. |

Los scripts de `build/` no se versionan (está en `.gitignore`): son de lectura del guardado del jugador. Las
herramientas que sí merecen sobrevivir están **versionadas en `tools/`** (ver `tools/README.md`): `lint_aldea.py`,
`audita_aldea.py`, `nbtdump.py`, `finduuid.py` y `recover/NbtTool.java`. `build/nbtdump.py` es un **puente** al de
`tools/`, para que los ~100 scripts sueltos de `build/` sigan funcionando con **una sola copia** del lector.

Los goals de las etapas B-E (`VillagerAnimalFarmGoal`, `VillagerCookGoal`, `VillagerGuardGoal`,
`VillagerLumberjackGoal`, `VillagerSmithGoal`) están **dentro** del lint desde la etapa E: antes solo se vigilaban
los tres goals viejos, y son justo los que más caminan por el pueblo (I3, I6). Una excepción **justificada** se marca
en la propia línea o en las dos anteriores con `// lint:ok <clave> porque ...`: así se calla un aviso legítimo sin
apagar la regla para el resto del archivo.
