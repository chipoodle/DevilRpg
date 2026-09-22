# DevilRpg — Diseño y Roadmap (Action-RPG)

Visión: convertir **DevilRpg** en un **action-RPG de fantasía oscura** (estilo *Heretic* de Raven
Software) donde el mundo está vivo y es hostil. No es "un mod más": el jugador debe **sobrevivir,
avanzar y nunca establecerse**, porque el mundo se endurece, los enemigos se organizan y las
comunidades que encuentra pueden vivir o caer.

> El plan está pensado para que las mecánicas funcionen hoy sobre el mapa horizontal y **se reutilicen
> después** en el futuro "abismo vertical" (estilo *Made in Abyss*).

---

## 1) El loop central

> El mundo alrededor del jugador está vivo y es hostil. Sales de tu punto seguro (spawn), el terreno se
> vuelve más duro mientras más te alejas, los enemigos se organizan y se fortalecen, las aldeas que
> encuentras viven (construyen, cultivan, envejecen, se defienden) y **pueden caer** — y vos tenés que
> seguir avanzando, sin poder establecerse en ningún lado.

### Los 3 pilares

1. **Escalación + dirección** — el mundo se endurece (distancia desde el spawn **y** tiempo) y te
   empuja a salir (objetivos cada vez más lejos).
2. **Enemigos vivos** — hordas, guaridas, adaptación, se fortalecen con el tiempo.
3. **Asentamientos vivos** — aldeas que progresan, se defienden y pueden ser arrasadas.

---

## 2) Arquitectura del sistema de supervivencia

Se apoya en dos herramientas que ya se construyeron:

- **`SpawnScaleProfile`** (`spawnprofile`): perfil neutro de **distancia** — curva de probabilidad y
  factor de escalado de atributos según la distancia al spawn. Lo comparten la entidad y su spawnRule
  sin acoplarse.
- **`ThreatLevel`** (`survival`): componente de **tiempo** — amenaza global 0..1 que crece con las horas
  de juego. Server y cliente la calculan igual sin sincronizarla (deriva del reloj del mundo).

```
Nivel de amenaza (tiempo) ──┐
                            ├─▶ factor de dificultad = scaleFactor(distancia) × multiplier(tiempo)
SpawnScaleProfile (distancia)┘
```

### Paquete `com.chipoodle.devilrpg.survival`

| Clase | Responde a | Rol |
|---|---|---|
| `ThreatLevel` | "¿cuánto se endureció el mundo?" | 0..1 por tiempo + `multiplier()`. |
| `ObjectiveTargets` | "¿hacia dónde tengo que ir?" | Posición determinista del objetivo `i` desde el spawn (server y cliente coinciden). |
| `ObjectiveManager` | "¿llegué al objetivo?" | Server: avanza el índice al alcanzar el objetivo. |
| `HordeManager` | "¿me atacan en grupo?" | Server: horda periódica, tamaño/frecuencia según amenaza. |

---

## 3) Iteración 1 — IMPLEMENTADA ✅

### 3.1 Escalación (distancia **+** tiempo)

- La entidad captura **`spawnDistance`** (distancia horizontal al spawn) **y** **`spawnThreat`** (amenaza
  global al momento del spawn).
- Al ajustar atributos (una sola vez en `aiStep`):

```java
double scaleFactor = PROFILE.scaleFactor(spawnDistance) * spawnThreat;   // distancia × tiempo
```

Con esto, un zombie generado lejos del spawn **y/o** tarde en la partida es más fuerte
(vida/velocidad/daño). El `AggressiveZombieEntity` ya lo aplica.

### 3.2 Objetivo direccional (te empuja a salir)

- **`ObjectiveTargets.targetOf(spawn, index)`** → el objetivo `i` está a **`800..1200` bloques del spawn,
  seudoaleatorio determinista por índice**, en **una dirección fija** (mismo rumbo para todos, para que la
  flecha no salte de dirección). La distancia **crece con un paso mínimo** (`OBJECTIVE_STEP=600`), con
  variación `0..400`, garantizando **separación ≥ 200 bloques** entre objetivos (nunca se superponen).
  Determinista → server y cliente calculan el mismo punto.
- **`ObjectiveManager.tick(player)`** (en el tick del jugador, servidor): si el jugador está a ≤24 bloques
  del objetivo, avanza el índice (y se sincroniza al cliente). Cada objetivo está **más lejos**, lo que
  obliga a seguir avanzando y a no establecerse.
- El índice se guarda en la **`PlayerAuxiliaryCapability`** (se sincroniza automáticamente, sin nueva
  capability/payload).
- **HUD `ObjectiveHudOverlay`**: en la parte superior central muestra "Objetivo N — X m ↑" con la
  distancia restante y una flecha cardinal relativa al giro del jugador.

### 3.3 Horda periódica

- **`HordeManager.tick(level)`** (en el tick del servidor): cada cierto intervalo (que se acorta con la
  amenaza; 20 min → 3 min) intenta spawnear una partida de `AggressiveZombieEntity`.
- Los zombies de la horda están **sometidos al `SpawnScaleProfile`**: cada uno spawnea solo si pasa la
  probabilidad según la **distancia del jugador a su spawn**. Zona protegida (<200) → 0; de 200 a 1500 →
  fracción; ≥1500 → todos.
- La horda ataca al jugador **más lejos de su spawn**; si todos están en la zona protegida, no spawnea
  nada (el jugador está a salvo cerca de su base).
- El **tamaño** crece con la amenaza (`1 + round(amenaza*6)`).

---

## 3b) HITO ALCANZADO — Aldea viva + pulido de Fase 1 ✅

Esta fase convierte el objetivo en una **aldea defendible** y afina la presión de los enemigos. Todo lo
siguiente está implementado y probado.

### 3b.1 La primera aldea (center del asedio)

- El objetivo, al ser alcanzado, **pre-genera** una aldea en su punto exacto (`VillageManager.preGenerate`)
  antes de que el jugador llegue (a 140 bloques). Se cachea por dimensión+índice.
- Al llegar (≤24 bloques), se inicia el **asedio** (`VillageManager.start`): se da un margen de
  exploración y se lanza una **ola de zombies agresivos** desde fuera de la valla.
- **Aviso previo**: al entrar en **100 bloques** de la aldea, el jugador recibe un mensaje ("Divisas una
  aldea a lo lejos...") y un **sonido de campana** lejana, una sola vez por objetivo/jugador.
- Al resolver el asedio: **salvada** → recompensa + avanza el objetivo; **caída** → avanza el objetivo sin
  recompensa. En los dos casos, los zombies vivos de la ola dejan de asediar (se les desactiva el goal "ir al
  centro") y se quedan por el mundo como **zombies agresivos normales**, con el escalado por distancia que ya
  traen de su spawn.
- **Regla del perímetro (fin del tiempo)**: cuando se agota el tiempo (`GRACE_TICKS` + `SIEGE_TIMEOUT_TICKS`),
  si queda algún zombie de la ola **fuera del perímetro** de la aldea (sin pasar los muros,
  `PERIMETER_RADIUS` = la valla), el asedio se considera **fracasado** y **la aldea se salva** (con recompensa).
  Sin esta regla, unos pocos zombies escondidos que nunca llegaban al centro —y por tanto no se podían matar—
  hacían caer la aldea sin que el jugador pudiera evitarlo: *si no llegan, no asedian, no pueden ganar*. Si
  **todos** los supervivientes están dentro, entonces sí cae.
- **La recompensa va en experiencia, no en puntos sueltos**: salvar la aldea da hierro, cuero, un libro y
  **`REWARD_EXPERIENCE_LEVELS` = 1 nivel de experiencia vanilla**. El punto de habilidad llega **solo**, por el
  camino de siempre: `giveExperienceLevels` dispara `PlayerXpEvent.LevelChange` (que NeoForge inyecta
  **antes** de sumar el nivel, así que `e.getLevels()` es el delta) → `setCurrentLevel(experienceLevel + 1)` →
  como supera `maximumLevel`, suma 1 a `unspentPoints`. Así la recompensa **también sube la barra y el nivel**
  del jugador. *(Antes se regalaban `siegeSkillPoints` = 3 + índice/4, tope 8, con `addUnspentPoints`: no
  subían nada la experiencia, así que no contaban para el nivel.)* El chat lo dice tal cual
  ("La aldea te lo agradece: +1 nivel de experiencia (+1 punto de habilidad).") y si por lo que sea el nivel no
  diera punto (p. ej. ya cobrado antes) el mensaje omite el paréntesis. Si la aldea **cae** no hay recompensa.
- **Las aldeas de objetivos ya superados siguen vivas** (`VillageManager.manageNearby`, llamada desde
  `ObjectiveManager.tick` junto a `LairManager.preGenerateNearby`): cualquier aldea a menos de
  `PRE_GENERATE_RADIUS` (140) del jugador se pre-genera, avisa y **puede asediarse**, aunque su objetivo ya
  esté superado. Antes, al avanzar de objetivo la aldea anterior dejaba de gestionarse: si volvías, no
  pre-generaba, no avisaba y no se podía asediar (aldeas "muertas" por el mundo). Los tres pasos son
  idempotentes (pre-generado, avisado y resuelto se guardan en `VillageSavedData`), así que llamarlo cada tick
  no repite nada.
- **El núcleo de la guarida usa la misma recompensa** (`LAIR_REWARD_EXPERIENCE_LEVELS` = **2 niveles de
  experiencia**, con su punto de habilidad cada uno): el doble que salvar una aldea porque asaltar la guarida es
  más duro y más largo. Las dos recompensas pasan por `util/MissionRewards.giveExperienceLevels` (que devuelve
  los puntos ganados para poder decirlo en el chat) y `MissionRewards.describe` arma el texto del premio.

### 3b.2 Generación de la aldea (`VillageGenerator`)

- **Limpieza de vegetación** (árboles, follaje, flores, pasto, bambú, cactus, caña) barriendo la columna
  completa, antes de generar.
- **Nivelación del terreno** a una mediana, rellenando hoyos y recortando excesos, hasta el radio de la
  valla (para no dejar abismos ni charcos), más un **talud exterior escalonado** (`addOuterSlope`) para que
  la aldea parezca una **meseta natural** y no un cubo de paredes verticales. En **agua** se construye una
  **isla flotante** con el suelo **A NIVEL del agua** (reemplaza la capa superior) y base **cónica circular**
  (tierra/piedra con "raíces" de tronco en el borde), porque el objetivo no se puede mover.
  La decisión de "¿esto es agua?" la toma **`VillageGenerator.waterSurfaceForArea()`**, la **regla compartida
  con la guarida**: es agua si la **columna central** lo es *o* si el agua es **al menos la mitad de la zona**
  (se mira un disco que incluye el talud). Mirar solo la columna central fallaba en la costa — centro en
  tierra, resto en el mar — y la obra acababa nivelada al fondo marino, **sumergida**.
- **3 casas del propio juego** (refinamiento posterior, ver 3b.5): son plantillas `StructureTemplate` de
  vanilla (`minecraft:village/plains/houses/plains_small_house_1..8`, `plains_medium_house_1/2`) colocadas con
  `StructureTemplateManager.getOrCreate` + `placeInWorld`, elegidas de forma determinista por la posición de la
  aldea. Traen su propio interior, su cama (los aldeanos necesitan cama para criar) y su puesto de trabajo.
  Antes eran cabañas procedurales (`hut()`, que sigue en el código marcado como **LEGACY — NO USAR**).
  - **Las aldeas ya construidas también se convierten**: `VillageManager` (`CURRENT_LAYOUT = 6`) llama a
    `VillageGenerator.actualizarCasas`, que borra la cabaña vieja (solo lo construido: `esTerrenoNatural` protege
    el terreno) y coloca la casa del juego en su sitio, y vuelve a trazar los caminos a la puerta nueva. La marca
    persistida **`hasNewHouses`** evita rehacer una casa que ya es nueva (rehacerla borraría lo que tenga dentro).
    Antes de tocar una casa se **saca a aldeanos y golems** que estén dentro (`sacarVecinosDe`) para que la
    plantilla no los deje emparedados. *(Esto se añadió porque el jugador entró a una aldea vieja, con cabañas
    procedurales y medio enterradas, y con razón lo reportó como que "todo estaba roto": no era una regresión del
    código nuevo, era que la migración no cubría las casas.)*
- **UNA sola cota para toda la aldea (trazados 13→15) y suelo a ras**: la cota (el nivel por el que se anda) se
  calcula **una vez**, con el terreno **limpio** (`generate`: isla → nivel del agua + 1; tierra → mediana de
  `levelTerrain`), y **todas** las construcciones —las 4 casas, la iglesia y la granja— se colocan a ella. El
  **suelo de la plantilla va en el bloque de superficie** (`origen.y = nivel - 1`). Nivelar cada casa por su cuenta
  (mínimo o mediana de su patio, trazados 10-12) era lo que dejaba **zanjas** alrededor y casas hundidas; y
  recalcular la cota con la aldea ya construida la dejaba **un bloque alta**, porque `groundY` sobre una casa
  devuelve su **tejado** (medido en el guardado del jugador: suelo de la plaza a y=62 con los suelos de las casas a
  y=63, cada casa con su terraza de un bloque). En una aldea **ya construida** la cota se **lee de la plaza**
  (`cotaDeLaPlaza`: disco de radio 6 en el centro, donde no hay nada encima) y se nivela a ella. El nivelado,
  además, **nunca rellena encima de una construcción** ni **recorta troncos** (`esTerrenoRecortable`): el relleno
  era a ciegas y **enterraba el muro** de la aldea, y el recorte daba los troncos por "terreno natural" y los
  borraba. `escalonDeEntrada` sigue poniendo escaleras de roble delante de la puerta si el suelo de fuera quedó más
  bajo que el piso, y la granja usa la **misma** cota (antes la recalculaba a mitad de obra, con las casas y la
  iglesia ya colocadas).
- **Nada del mundo dentro de la aldea (trazado 25)**: dentro del recinto **no queda nada** que no sea la aldea.
  - El **subsuelo natural entra como terreno** (`esTerrenoNatural`), **minerales incluidos** (las ocho vetas del
    overworld en piedra y en deepslate, `BASE_STONE_OVERWORLD`/`BASE_STONE_NETHER`, `DIRT`, `SAND`, `TERRACOTA`,
    `ICE`, `SNOW`, `NYLIUM`, `SCULK_REPLACEABLE`). No lo estaban y al recortar un monte con una veta dentro la
    piedra de alrededor se iba y **la veta quedaba flotando en el aire** (medido en el guardado: 106 minerales por
    encima de la cota en una aldea), además de **colarse en el plano** (el obrero los "reparaba" como parte del
    pueblo).
  - `despejarVolumen` (aldea **nueva**): se barre el volumen entero y se quita todo lo que no sea terreno natural
    —vegetación, pero también **minas, mazmorras, ruinas y cofres**—. En la migración de una aldea ya construida
    **no** se usa (derribaría el pueblo): allí solo se quita lo que no es terreno.
  - `sellarSuelo` (al final del nivelado, en las dos vías): si debajo pasa una **barranca, una cueva o una mina**,
    el recorte del techo dejaba **agujeros en el suelo** de la aldea y los aldeanos **se caían** (visto en juego:
    85 columnas huecas, algunas de 9 bloques). Ahora cada columna hueca se rellena hacia abajo hasta el primer
    bloque firme (hasta 64) con césped arriba. Solo se tapan columnas de **aire**: el agua de la acequia de la
    granja se queda como está.
- **El muro entra en el plano y se rehace al migrar**: el muro es de **troncos** (`wall`) y `seDescartaDelPlano`
  los descartaba como si fueran vegetación, así que no estaban en el plano y el obrero **no podía reponer** los que
  rompe un asedio (era el bug del jugador: "al defender la aldea, las maderas del muro no vuelven nunca"). Ahora
  los troncos **sí** entran en el plano y, en la migración de trazado, `VillageGenerator.rehacerMuro` **reconstruye
  el muro entero** (limpia la franja del muro y lo vuelve a levantar con `fence`): cuando el muro queda enterrado o
  pierde troncos, esos huecos no están en ningún plano y no se pueden reparar bloque a bloque.
- **Los caminos no suben por los tejados**: `line()` colocaba el camino con `groundY` **por columna**, y sobre una
  casa eso devuelve el **tejado**, así que el camino se pintaba encima y le arrancaba bloques. Ahora se saltan las
  columnas que no estén a la altura del patio (más de 1 bloque de diferencia).
- **La iglesia es una construcción del juego**: en el sitio de la vieja torre de vigilancia procedural
  (`tower`, ahora **LEGACY — NO USAR**) va un **templo de aldea de vanilla** (`plains_temple_3/4`), que ya trae
  campanario y campana. A las iglesias **no** se les pone cama de respaldo (`esIglesia`).
- **Herrería (trazado 26)**: la aldea tiene la casa de herrero del juego (`plains_weaponsmith_1`: fragua con lava,
  muelle de afilar y arca) en el hueco libre del norte, entre la iglesia y la casa grande, **con su camino**. Es el
  único sitio donde cabe su huella de 9×11 (comprobado con las huellas máximas de casas, iglesia, parcelas, almacén,
  kiosco, faroles y sitios de aldeano). Dentro lleva también la **mesa de herrería** (`smithing_table`, que la
  plantilla no trae): son los **puestos de trabajo** de los dos herreros de la aldea, que hasta el trazado 26 **no
  existían** (el jugador lo notó: "hay un herrero pero no veo su estación de trabajo"). Sin puesto de trabajo el
  aldeano no puede reclamarlo y el juego le acaba **borrando el oficio** (`ResetProfession`).
  `esVivienda` deja fuera iglesia y herrería: no se les ponen camas ni segunda puerta. `asegurarHerreria` es
  idempotente y la llaman la generación y la migración.
  - **OJO con la Y de las comprobaciones**: la caja con la que se busca el muelle para saber si la herrería ya está se
    medía desde la Y de la base, que es **la del spawn del jugador** (el log lo delataba: `Aldea en {4809, 101, 4809}`
    con la puerta en y=75). Con la caja a y=101 no se encontraba nunca y la herrería se **reconstruía cada 10 s**
    (54 reconstrucciones seguidas en el log); cada una destruye su cofre y el juego **tira el botín al suelo**
    (`Containers.dropContentsOnDestroy`), de ahí las espadas, picos, armaduras, sillas de montar y diamantes tirados
    alrededor. Ahora la caja se mide desde **la cota** (`buscarBloque(level, base, nivel, bloque)`).
- **Etiqueta sobre el aldeano**: arriba el **nombre y el oficio**, debajo lo que está haciendo
  (`"Anselmo (Granjero)\nCosechando"`). El nombre sale del **UUID** (al azar pero estable, sin guardar nada) y el
  oficio se pone con los nombres del mod en español ("Granjero", "Herrero de armas", "Herrero de herramientas",
  "Clérigo", "Recolector"), con el nombre traducido del juego como reserva para oficios de vanilla.
  - **Y cuando hace algo, lo cuenta** (`ponerSuceso`): "Guardo 12 y horneo 2 pan(es)", "Trajo 6 del almacén",
    "Repuso tronco de roble", "Guardo 5 cosa(s) en el almacén", "Abonó la huerta". El suceso se queda
    `SUCESO_TICKS` (5 s) en la cabeza y después vuelve sola la actividad de fondo; mientras es reciente,
    `ponerActividad` no lo pisa (ni los goals ni el refresco genérico de cada segundo). Los mismos sucesos van al log
    (INFO), así que lo que se ve en la cabeza se puede comprobar. El texto va **corto** (una etiqueta de nombre no se
    parte sola) y el nombre del bloque se traduce **a mano** (`VillageManager.nombreEnEspanol`): las traducciones las
    resuelve el servidor, y ahí el idioma es inglés (de ahí el "Farmer" que salió una vez).
  - **El salto de línea hay que pintarlo a mano**: la etiqueta de nombre de vanilla se dibuja con
  `Font.drawInBatch(Component, ...)`, que **no
  parte las líneas** (solo lo hacen `MultiLineLabel`/`drawWordWrap`), así que el `\n` salía como un glifo raro en
  medio del texto (el "LF" que reportó el jugador). Lo resuelve `VillageNameTagSubscriber` (cliente): intercepta
  `RenderNameTagEvent` y, solo para las etiquetas de aldeano con salto de línea, le dice al juego que **no** la pinte
  (`setCanRender(TriState.FALSE)`) y dibuja las líneas una debajo de otra con la misma pose y las mismas pasadas
  (fondo + texto) que vanilla. Todo esto se puede apagar con `[village] mostrarActividadAldeanos = false`.
- **Cinco aldeanos, DOS herreros (trazado 29)**: la aldea nace con **5** (`VILLAGERS_FOR_FULL_HEALTH` = 5, 5 puestos):
  granjero, **herrero de armas**, clérigo, **herrero de herramientas** y **recolector** (el holgazán). Con 5 adultos, la
  aldea nombra hasta **3 obreros** y deja al granjero con la huerta.
  - Los **dos herreros** tienen su puesto en la herrería: el **muelle de afilar** del de armas y la **mesa de
    herrería** del de herramientas. Hacen falta los dos porque van a **fabricar la indumentaria de la guardia**
    (espada, escudo, armadura, arco y flechas) repartiéndose el trabajo. *Nota*: en el trazado 28 se dejó **un solo
    herrero** y se quitó la mesa; el jugador lo corrigió y el 29 la repone (una estación sin dueño acabaría dando ese
    oficio a cualquier aldeano sin oficio, por eso hay que decidir bien cuántos puestos hay).
- **Caminos de 2 bloques de ancho** en el plano XZ, de tierra apisonada, a ras de suelo, que van del centro a la
  **puerta real** de cada casa (se mira el bloque de la puerta y su `FACING`, porque cada plantilla la pone donde
  quiere) y no pasan sobre las casas ni la campana.
- **Campana** en el centro (sobre soporte de piedra, columna limpia).
- **Golem de hierro** de guardia (Y fijada al suelo de la isla para no sofocarse).
- **Aldeanos y golem: SIEMPRE en la superficie de SU columna, nunca a la Y del centro.** Bug arreglado el
  12-sep-2026: se colocaban en `center.offset(...)` (la Y del centro), pero el terreno se nivela a una
  **mediana** y las cabañas/caminos usan `groundY` **por columna**; si la mediana quedaba por encima del
  centro, los 3 aldeanos aparecían **enterrados**, se asfixiaban y morían en ~10 s (1 de daño cada 10 ticks ×
  20 de vida = los 9 s exactos que se veían en el log) → **al llegar a la aldea no había nadie**. Ahora usan
  `groundY` de su columna y pasan por **`huecoLibre()`** (primer hueco de 2 bloques de alto), la misma red de
  seguridad que usan los enemigos de la guarida. Además, al llegar a una aldea generada, **no caída** y
  **vacía**, se repueblan aldeanos y golem (`VillageManager.start`): antes, como la aldea se genera **una sola
  vez**, una vez muertos no volvían nunca. Si la aldea ya cayó (`isFallen`) no se toca: la derrota es definitiva.
- **Faroles con poste** distribuidos (evitan spawn de zombies con la mecánica vanilla).
- **Muro** de **logs horizontales + columnas de cobblestone** cada 4 bloques, con **4 entradas de
  cobblestone** en los cardinales, **a ras del suelo** (sin bloque de tierra sobresaliente ni hueco
  inferior). **Torre de vigilancia** de cobblestone cerca de una entrada.

### 3b.3 Comportamiento de los zombies del asedio (`AggressiveZombieEntity`)

- **Romper obstáculos**: si no progresan hacia su objetivo (aunque se balanceen o el enemigo se mueva),
  rompen el bloque delante. Destrucción **agresiva**: abren **siempre un hueco de 2 de alto** (cuerpo +
  cabeza) —con solo el de abajo el zombie no cabe y tardaba el doble—, y si el objetivo está **más arriba**
  rompen también un bloque más para dejar **escalón de subida** (`breakStepAhead`: rompen la columna de
  delante a la altura de la cabeza, convirtiendo un muro en un escalón de 1 bloque que sí pueden saltar; y
  repiten para seguir subiendo). Solo rompen al estar **bloqueados**; obsidiana solo si su nivel (distancia)
  ≥ 700; nunca bedrock.
- **Salir del agua**: si están en agua y **atascados** (nadan y no avanzan 2 s), buscan la orilla más cercana
  y nadan hacia ella, con empujes hacia arriba para trepar el desnivel. Solo se activa si de verdad no
  avanzan y **se rinde a los 10 s** para ceder el turno a romper/marchar/atacar: antes se activaba con solo
  tocar agua (`isReallyStuck()` devolvía `true` siempre) y, con prioridad 2, **bloqueaba todo lo demás** —
  con la aldea flotante rodeada de agua y las oleadas saliendo a 32–40 bloques (en el agua), dejaba al
  asedio entero nadando en el sitio sin romper ni atacar.
- **Convergen al centro**: si no tienen objetivo de ataque y conocen el centro, marchan hacia él; al llegar
  a **3 bloques de radio** el goal se apaga. Si la aldea **cae**, se les desactiva ese goal.
  - **Rodean, puentean o taladran (en ese orden)**: al marchar se les amplía el presupuesto del buscador de
    caminos (`setMaxVisitedNodesMultiplier(6)`, recalculando ruta cada 20 ticks) para que **encuentren la vuelta**
    a una montaña. Si **hay ruta, no tocan nada** (pueden estar dando la vuelta). Solo si **no hay ruta** y llevan
    un rato sin avanzar: **(1) PUENTE** si delante hay un abismo (dos bloques de aire con un vacío de 2+ debajo →
    pone **adoquín** a la altura de los pies, uno por segundo) y **(2) TÚNEL lento**: como mucho
    `TUNEL_PRESUPUESTO` = **40 bloques por marcha**, uno cada 2 s. Así una montaña grande **aguanta** y la aldea
    puede salvarse por tiempo (elección del jugador). El presupuesto se recarga al empezar una marcha nueva.
    **Nunca rompen ni construyen dentro del disco de la aldea** (`FENCE_RADIUS + 2`): el muro, las casas y la
    huerta están todos dentro, así que no se cava por debajo ni se destroza nada al llegar; perseguir a un aldeano
    dentro de la aldea sí rompe (es el asalto). Partículas y sonido donde trabajan, y un INFO al empezar a
    taladrar o a poner el primer tablón, para poder comprobarlo.
- **Comportamiento de MANADA** (Fase 2): los zombies agresivos que atacan al **mismo objetivo** se
  **reparten en ángulos distintos** alrededor de él (punto de flanqueo derivado de su UUID, radio 3.5)
  en vez de apilarse en línea recta; así lo **rodean** desde varios lados. Recalculan cada 40 ticks y, al
  estar bien posicionados (<2.5 bloques de su flanco), ceden el control a `MeleeAttackGoal` para golpear.
- **Targeting de minions**: detectan al instante **todas** las invocaciones del jugador (lobos, wisps y el
  oso), usando `LivingEntity` + predicado `ITamableEntity` con dueño (el oso no es `TamableAnimal`).
- **Control de prioridades (importante si añades goals)**: `GoalSelector` **solo** arbitra por prioridad a
  través de los *control flags*. Un goal que **no** declara `setFlags` es invisible para ese mecanismo:
  arranca aunque otro de más prioridad esté corriendo y no lo bloquea, así que acaba **peleándose por la
  navegación** (gana el último que llame a `moveTo` ese tick). Por eso los goals que navegan declaran
  `MOVE`/`LOOK` aquí, y por eso el `HerdBehaviorGoal` no funcionaba como manada: al no declarar `MOVE`
  corría a la vez que `MeleeAttackGoal` (registrado después y re-trazando ruta cada 4-11 ticks), que pisaba
  su ruta de flanqueo. En cambio hay goals que **a propósito** no llevan flags porque **no navegan ni miran**
  y no deben bloquear el movimiento: `BreakBlockGoal` (observador pasivo) y `FireballAttackGoal` (ataque a
  distancia instantáneo). Lo mismo aplica al `LaunchSnowballGoal` del vex helado y al `ShulkerPeekGoal`.

### 3b.4 Presión de enemigos

- La amenaza nocturna de base dejó de ser un **zombie normal** (que solo spawnea de noche y se quema al
  sol) y ahora es un **vex helado** (`FrostVexEntity`, entidad propia que extiende `Vex`): vuela,
  **spawnea de día y de noche**, escala atributos por distancia+amenaza (como el zombie agresivo) y, además
  del ataque melee heredado, **lanza una bola de hielo** (`FrostBall`, la misma del wisp) con enfriamiento
  (4 s). Su ciclo natural: lanza la bola → cooldown → ataca melee (se acerca y se aleja volando) → al
  volver a estar a distancia el cooldown ya terminó y vuelve a lanzar. Tiene su propio **`VexSpawnProfile`**
  y **`VexSpawnRule`**.
- Los **minions invocados** (`SoulWolf`, `SoulBear`, wisps) **no reciben daño de su propio dueño** ni de
  otros minions del mismo dueño. El `SoulWolf` (extends `TamableAnimal`) ya se unía al team; el
  `SoulBear` (extends `AbstractChestedHorse`) se protege explícitamente en `hurt()`.
- **Pasivos de esbirro con probabilidad que escala con los puntos** (mismo patrón que la *mordida gélida* del
  lobo): el **wisp arquero** tiene el pasivo **`wisp_ice_spear`** (*Ice spear volley*, nodo hijo de
  *Ranged wisp*, 5 niveles, icono `ice-spear.png`). Con un **7% de probabilidad por punto** (35% al máximo) el
  disparo deja de ser la bola de hielo y se convierte en una **andanada de 3 lanzas de hielo** (`IceSpear`,
  entidad propia) que **salen en sucesión** (una cada 7 ticks, no las tres de golpe) y **despacio**, para que
  se vea cada una salir, **corregir la trayectoria** en el aire —salen con desviación inicial y el guiado las
  endereza, como cohetes— e impactar. **Persiguen** al objetivo del wisp y, si no lo tiene, al enemigo válido
  más cercano. Al chocar (o al agotarse) **estallan con una salpicadura pequeña** (1.8 bloques: daño + empujón
  + lentitud) **sin romper terreno** y sin dañar al dueño ni a los demás esbirros: el estallido es manual
  (partículas + sonido + daño en área), no `level.explode`, justo para eso. El daño es **bajo a propósito**:
  0.6 + 0.045·puntos directo y 0.5 + 0.035·puntos de salpicadura (al máximo, 1.5 y 1.2 por lanza), de modo que
  los tres impactos juntos quedan **por debajo** del daño que hacía una sola explosión de la primera versión
  (2.5 + 0.12·puntos). **Cada lanza le cuesta medio punto de maná al dueño** (`MANA_PER_ICE_SPEAR = 0.5F` en
  `SoulWispArcher`): se cobra **por lanza, al dispararla**, y si al dueño no le llega para la siguiente, la
  andanada **se corta ahí mismo** (no sale esa lanza ni las que quedaban). Si no le llega ni para la primera, la
  andanada **ni se empieza** y el wisp dispara la bola de escarcha normal, que es **gratis**. Un wisp sin dueño
  (por ejemplo de huevo de spawn) no le cobra a nadie. **A los ANIMALES no los ataca por su cuenta**: su
  `NearestAttackableTargetGoal` iba a por **cualquier `Mob`** (solo excluía aldeanos, llamas, tortugas y golems), así
  que masacraba las vacas, cerdos, ovejas y mascotas del jugador al pasar. Ahora los animales (`Animal`,
  `WaterAnimal`, `AmbientCreature`) solo son objetivo si **su dueño los está atacando** (el último bicho al que atacó
  el jugador, o el animal que lo tiene a él por agresor), y un wisp sin dueño no los toca nunca. Se dibuja con `textures/entity/frostball/freeze_texture.png` mediante un renderer
  billboard propio (`IceSpearRenderer`): al ser una textura de entidad (fuera de `textures/item` y
  `textures/block`) **no está en el atlas de bloques**, así que un modelo de item la mostraría como textura
  perdida — de ahí el quad a mano con `RenderType.entityCutoutNoCull`, que además siempre mira a la cámara y
  nunca se ve "de canto".
- Las partidas guardadas reciben las **skills nuevas** del mod automáticamente: al cargar, la capability de
  skills añade al NBT del jugador las claves que falten (puntos, nivel máximo, coste de maná, tipo de recurso
  e icono) tomándolas de una copia por defecto creada al construir la capability. Sin eso, una skill añadida
  después de guardar la partida aparecía como 0/0 en el árbol y reventaba con `NullPointerException` al
  pulsarla (su nivel máximo era `null`).

### 3b.5 Nota de diseño sobre el motor vanilla — RESUELTA ✅
Las villas **no** se generan con el motor vanilla (Jigsaw), porque ese sistema es **data-driven** y coloca
estructuras por bioma con `StructureSet`/`Structure`, no en una coordenada determinista del objetivo, y está
pensado para ejecutarse durante la generación del chunk, no bajo demanda al acercarse el jugador. Mantenemos el
generador propio de `VillageGenerator`.
**Pero sí se reutilizan las CONSTRUCCIONES del juego**: desde el refinamiento de la Iteración 3 las 3 casas son
plantillas `StructureTemplate` de vanilla (`village/plains/houses/...`) cargadas con
`level.getStructureManager().getOrCreate(ResourceLocation)` (en 1.21 devuelve la plantilla directamente, no un
`Optional`) y colocadas con `template.placeInWorld(level, origen, origen, new StructurePlaceSettings(), random,
Block.UPDATE_CLIENTS)`. Como se colocan a mano y **no** pasa el algoritmo de jigsaw, hay que limpiar los bloques
**técnicos** que traen las plantillas (`minecraft:jigsaw` —el "conector" con el que el juego encaja las piezas—
y `minecraft:structure_void` —celda "no toques esto"—). Se resuelven con el dato del **propio juego**
(`bloqueTecnicoFinal`): se lee el `final_state` del `JigsawBlockEntity` y se coloca ese estado, que es justo lo
que vanilla pondría al conectar la pieza. Detalle de 1.21: `JigsawBlockEntity.getFinalState()` devuelve el
**texto** del estado (`"minecraft:oak_planks"`), no un `BlockState`, así que se parsea con
`BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), texto, false).blockState()`. Si el
`final_state` es aire (o es un `structure_void`), se copia un bloque vecino real (`rellenoParaTecnico`) para no
dejar un agujero. Ejemplo real de `plains_small_house_1`: su jigsaw de entrada
(`name=minecraft:building_entrance`, pool `village/plains/streets`, `final_state=oak_stairs[facing=east,…]`) se
convierte en el **escalón de la entrada**, y el de `name=minecraft:bottom` (pool `village/plains/villagers`,
`final_state=oak_planks`) en una **tabla del suelo**.

---

### 3b.8 Economía de materiales y taller (los dos herreros)

La aldea tiene **dos herreros** con su puesto en la herrería y **producen de verdad**: cogen los materiales del
**almacén**, trabajan en su sitio y dejan la pieza en el almacén (de ahí se equipará la milicia). El ciclo es real, no
un contador: van al almacén, **se llevan** los ingredientes (los llevan encima, se les ve cargados), los trabajan en su
puesto y **traen** lo fabricado. Queda en el log y en su etiqueta ("Forjó una espada de hierro").

- **Reparto** (lo fijó el jugador): el **muelle de afilar** (herrero de ARMAS) hace **espadas, escudos, arcos y
  flechas**; la **mesa de herrería** (herrero de HERRAMIENTAS) hace **armaduras** (de hierro si hay lingotes de sobra,
  si no de cuero) y la **transformación de materiales**.
- **Transformaciones**: 9 **chips de metal** (pepitas) → 1 lingote; **chatarra** de hierro (espadas, picos, hachas,
  azadas, escudos y armaduras viejas) → 1 lingote por pieza; 9 **carne de zombie podrida** → 1 **cuero** (lo hace el de
  herramientas en su mesa).
- **De dónde sale el material**: los zombies agresivos **aparecen equipados** con arma y armaduras de hierro (35 % de
  las veces, cada pieza al 50 %) y las sueltan al morir con **baja probabilidad**
  (`PROBABILIDAD_SOLTAR_EQUIPO = 0.12`), y **siempre** sueltan **1-2 chips de metal** y a veces carne podrida. El
  **recolector** los barre a su lista blanca (pepitas, carne podrida y chatarra incluidos) y los guarda en el almacén.
  *Ojo*: por eso la lista blanca del recolector incluye ahora armas y armaduras de hierro — si dejas una tirada en el
  suelo de una aldea más de 5 s, se la lleva al almacén.
- **Objetivo de producción**: la indumentaria de la milicia (4 espadachines con escudo + 3 arqueros): 4 espadas, 4
  escudos, 3 arcos, 64 flechas y 7 juegos de armadura (una pieza de cada por militar). Cuando el almacén tiene de
  sobra, el herrero descansa (no fabrica sin fin).
- **La cantera/mina NO entra en esta iteración** (queda para más adelante): el hierro viene de los zombies y del
  reciclaje, así que la producción es lenta a propósito.

### 3b.9 La BARRACA de la milicia (milicia, paso 1)

El edificio que pidió el jugador para la guardia: *"necesitan una barraca con MUCHAS CAMAS"*. Va **antes** del
reclutamiento porque los guardias tienen que vivir en algún sitio (y las camas son también las que dejan **crecer**
al pueblo: vanilla pide una cama libre por cría).

- **Sitio**: `baseDeBarraca(center)` = **(-26, 13)**, al **oeste** del pueblo. Es el cuadrante que quedaba libre
  (entre la casa del noroeste, el bancal A de la granja, su compostero y el almacén de (18,18)) y deja el edificio
  **entero dentro de la valla**: **medido en el guardado**, la esquina más lejana queda a **34,5** del centro con la
  valla a **36**. El compostero del bancal A (-21,10) queda pegado a la pared este (1 bloque), sin solaparse.
- **La barraca** (`barraca`): huella **9×9** — suelo de **piedra**, paredes y tejado de **tablones**, **puerta al
  norte** (con su camino a la plaza) y **8 camas** (`BARRACA_CAMAS`) en dos filas de 4 con la cabecera contra la
  pared y el pasillo en medio. Dos faroles colgados del centro: de noche se ve y no spawnean monstruos dentro.
- **Alturas (invariante I1)**: `nivel` es **la capa que se pisa**, así que el suelo sólido va en **`nivel-1`** y las
  paredes, la puerta y las camas en **`nivel`**. Poner el suelo en `nivel` (como el kiosco, que va a propósito un
  bloque alto con sus escaleras) dejaba la barraca **un bloque alta**, con escalón en la puerta: el mismo bug que se
  corrigió en las casas. Lo canta el lint si se pasa la Y del centro ([I1], de hecho saltó al escribirlo).
- **El solar se NIVELA antes de construir** (`nivelarHuella`, como las casas): recorta el terreno natural que sobra
  y **rellena los agujeros**, porque en el guardado el cuadrante oeste de una aldea tiene **charco** (23 columnas con
  agua en la capa de superficie, aldea 7) y en otra **faltaba el bloque de suelo en 12 columnas** (aldea 8): sin eso
  el suelo de la barraca quedaría flotando.
- **Entra en el PLANO** (todo pasa por `colocar`, invariante I8), así que el obrero repone la barraca y sus camas
  como cualquier otra construcción. Es **idempotente**: se comprueba el **suelo a la cota** (como el kiosco y el
  almacén) y si ya está no se toca — reconstruirla borraría las camas y lo que haya dentro.
- **Migración 30**: las aldeas ya construidas la reciben al migrar. **Medido en las 11 aldeas del guardado**: en la
  10 (cota 75) y la 9 (cota 64) el solar estaba **vacío** (solo la capa de nieve/césped, que se quita); en las **7 y
  8** (layout 20) lo cruza el **MURO VIEJO de radio 29** (42 y 59 bloques de tronco, piedra y escaleras) — la misma
  migración lo borra **antes** con `limpiarTrazadoAntiguo` (que corre dentro de `actualizarCasas`) y la barraca
  además limpia su propio volumen; en las **0-6** los chunks **no están generados** en el guardado, así que no se
  puede medir (quedan sin comprobar).

### 3b.10 El oficio de GUARDIA (milicia, paso 2)

Los aldeanos **sobrantes** se alistan (`VillageManager.repartirGuardia`) y su goal (`VillagerGuardGoal`) hace tres
cosas: **equiparse del almacén**, **patrullar** de día y **guardar las puertas** de noche **rotando**.

- **Quién sobra** (lo pidió el jugador: *"aldeanos adultos SOBRANTES"*): se reparten los **puestos fijos** del pueblo
  —los **once** de `VillageGenerator.puestosPorOficio()`, que se **cuentan** de los sitios del pueblo: **tres
  granjeros**, los dos herreros, el clérigo, el recolector, el ganadero, el cocinero y el pescador— en orden
  **estable** (por UUID) y **el
  resto** es gente de sobra. Así la milicia **no le quita el granjero ni los herreros** a la aldea (que es lo que la
  dejaría sin comer y sin indumentaria) y una aldea sana de 5 aldeanos **no tiene guardia**: hacen falta **crías**.
  Si la aldea vuelve a necesitar ese oficio (muere gente), el guardia **deja la milicia** (`desalistarGuardia`).
  *(La lista se **cuenta**, no se escribe a mano: ver **3b.47**, donde la lista a mano se quedó con siete puestos y
  la milicia se llevaba al pescador y al segundo granjero.)*
- **Tipo por número**: los 4 primeros son **espadachines** y los 3 siguientes **arqueros** (`MILICIA_MAX` = 7), que es
  la formación de la marcha a la guarida. Los guardias **no pueden ser obreros** (`puedeSerObrero` los excluye, y al
  alistarse se les quita la marca de obrero y su goal de reparación: si no, seguirían reparando caminos).
- **Equipo DEL ALMACÉN** (nada de regalo): el espadachín coge **espada de hierro** (mano principal) y **escudo**
  (secundaria); el arquero, **arco** y **16 flechas**. Se comprueba **leyendo sus manos y su mochila**, así que si le
  rompen el escudo o le quitan la espada **vuelve** al almacén. Si el pueblo todavía no tiene la pieza (los herreros
  van despacio: el hierro sale de los zombies), el guardia **patrulla igual** y vuelve a mirar dentro de un rato (no se
  queda yendo y viniendo). El objetivo de producción de los herreros ya es exactamente esta indumentaria (4 espadas, 4
  escudos, 3 arcos, 64 flechas), así que la milicia se rearma sola.
- **Ronda** (de día): punto a punto por **dentro del muro** (`RADIO_RONDA` = 29), con un plantón de 6 s en cada uno
  mirando al campo; el punto sale de su número de guardia y del paso de la ronda (determinista, sin tiradas), así que
  no van todos pegados.
- **Puertas** (de noche): las **cuatro puertas** del muro (norte, sur, este, oeste). El puesto sale del **reloj de
  juego** y de su número de guardia (`gameTime / RELEVO_TICKS + indice`), o sea que **rotan solos** cada 2 min y dos
  guardias no coinciden en la misma puerta.
- **Nada de esto cambia el mundo**: es oficio (datos persistentes del aldeano + goal), así que **no sube la
  migración** (`CURRENT_LAYOUT` sigue en 30) y las aldeas guardadas no se tocan. En una aldea ya en marcha, los
  guardias aparecen al primer latido que la pille cargada.
- **Medido en el guardado del jugador**: la aldea 10 tiene **6 adultos** (2 granjeros: los demás oficios, cubiertos),
  así que le sale **1 guardia espadachín** al primer latido. Su almacén todavía no tiene espada ni escudo (queda 1
  pepita y 3 de carne podrida), así que al principio se le verá **patrullando sin arma** e yendo al almacén de vez en
  cuando: el arma llega cuando el herrero funda lingotes.
- **Comprobado en las fuentes de 1.21**: los aldeanos **no registran ningún goal de vanilla** (todo su comportamiento
  es del **cerebro**), así que la prioridad 3 del goal de guardia **no pisa nada**.
- **YA SE LES VE EL EQUIPO ✅ (modelo propio)**: se sustituye el renderer del aldeano por `GuardVillagerRenderer`
  (registrado para `EntityType.VILLAGER`; `EntityRenderers.register` <b>sobrescribe</b> el de vanilla) y dentro:
  - Si el aldeano **es guardia** (`GUARD_TAG`) se dibuja con **`GuardVillagerModel`**: el **cuerpo del jugador**
    (`HumanoidModel`) con la **cabeza de aldeano** (su narizota como caja extra, en la esquina libre 0,32 del mapa de
    texturas) y **una textura por tipo**: `village_guard_swordsman.png` (uniforme de acero) y
    `village_guard_archer.png` (verde de monte).
  - Si **no** es guardia, se **delega en el `VillagerRenderer` de vanilla**: su ropa de profesión y de bioma no
    cambia. El renderer solo cambia a la milicia, y como la marca viaja con el aldeano, al alistarse o dejar la
    guardia el cambio de modelo es inmediato.
  - Con el modelo humanoid se le enchufan **las capas de vanilla**: `HumanoidArmorLayer` (casco, peto, grebas y
    botas) e `ItemInHandLayer` (**espada, escudo, arco**), que era justo lo que no se podía con el modelo del
    aldeano.
  - Las texturas son **provisionales** (generadas por script, colores planos): se pueden retocar sin tocar código.
- **MARCHA A LA GUARIDA ✅ (cierra la milicia)**: con la formación completa (**4 espadachines y 3 arqueros**) y la
  aldea en paz (ni asedio ni monstruos dentro), `VillageManager.comprobarMarcha` declara el **asalto**: los guardias
  van al **núcleo de la guarida** (`LairManager.nucleoDe`, a 75-95 bloques del pueblo, así que durante la marcha **no
  cuenta la "correa"** de la aldea y sí ven a los enemigos de la guarida). Etiquetas *"Marchando a la guarida"* y, al
  llegar, *"Asaltando la guarida"*. La marcha **se acaba** cuando la guarida queda limpia (núcleo destruido) o a los
  **12 min** (una marcha eterna dejaría la aldea sin guardia si el núcleo está sellado) y entonces **vuelven andando**
  (el destino pasa a ser un punto del pueblo; antes, "si se aleja más de X del centro, deja de trabajar" cortaba el
  goal y el guardia se quedaba plantado donde lo pillara).
  <p>
  Con esto la **etapa C (milicia) queda cerrada**: alistamiento de los sobrantes, equipo del almacén (arma, escudo,
  arco, flechas y armadura), ronda, puertas de noche con relevo, combate (espada/arco), escudo que bloquea de verdad,
  modelo propio para que se le vea todo y marcha a la guarida.
  - **COMBATE** (`VillagerGuardGoal`): lo primero que hace el guardia es **pelear**. Ve al monstruo más cercano a 16
    bloques **dentro del término de la aldea** (`RADIO_PERSEGUIR`) y va a por él: el **espadachín** levanta el
    **escudo** y pega con la espada (golpe cada segundo); el **arquero** dispara **flechas de verdad** (`Arrow` con
    dueño, que le gasta la mochila) cada 1,5 s y, si se queda sin flechas, vuelve al almacén a por más.
  - **El escudo bloquea DE VERDAD y no hay que escribir nada**: `LivingEntity.hurt` (línea 1136 de las fuentes)
    comprueba `isDamageSourceBlocked` en **cualquier** entidad que esté bloqueando, así que basta con
    `startUsingItem(OFF_HAND)` para "levantarlo" (y `stopUsingItem` fuera de combate). El bloqueo de verdad (daño a
    cero, desgaste del escudo y empujón al atacante) lo hace el juego.
  - **OJO con el espadazo y los atributos del aldeano** (crash arreglado al llegar la etapa F, porque con más puestos
    hay más guardias y estos pelean antes): **no se puede usar `villager.doHurtTarget(...)`**, porque ese método pide
    el atributo `ATTACK_DAMAGE` de quien golpea y **el aldeano no lo tiene** (vanilla solo le da vida y velocidad) — el
    juego se caía con *"Can't find attribute minecraft:generic.attack_damage"* en cuanto un espadachín alcanzaba a un
    monstruo. El daño se calcula a mano (`DANO_BASE_ESPADA` + los encantamientos del arma con
    `EnchantmentHelper.modifyDamage`) y se aplica con el aldeano como **atacante**
    (`damageSources().mobAttack(villager)`), más el empujón del golpe. El **arquero no tenía el problema**: la flecha
    lleva su propio daño (`Arrow.setBaseDamage`).
  - **No huyen**: al aldeano de vanilla, cuando le pegan, su cerebro le manda **huir** (actividad PANIC). Al guardia
    se le apaga **borrándole los recuerdos de "me han pegado"** (`HURT_BY`/`HURT_BY_ENTITY`, en cada tick de combate)
    y **escribiéndole el rumbo al enemigo en cada tick** (también pegado a él, que es donde el pánico ganaría la
    carrera). *Probado y descartado*: vaciar la actividad PANIC no se puede — `Brain.addActivity` solo **añade**
    comportamientos (no reemplaza la lista) y `removeAllBehaviors` se lleva el cerebro entero.
  - **ARMADURA puesta**: además de espada/escudo (o arco/flechas), el guardia se pone del almacén las **4 piezas**
    (casco, peto, grebas y botas) de lo que haya fabricado el pueblo (hierro o cuero). Cuenta para el daño de verdad
    (el juego la usa), aunque **todavía no se dibuja** (ver abajo).
- **Por qué NO se les ve el equipo (medido en las fuentes de 1.21)**: `VillagerRenderer` solo añade tres capas
  (`CustomHeadLayer`, `VillagerProfessionLayer`, `CrossedArmsItemLayer`) — **no hay capa de armadura ni de objeto en
  mano** — y `VillagerModel` **no** es `HumanoidModel` ni `ArmedModel`, así que `HumanoidArmorLayer` e
  `ItemInHandLayer` no se le pueden enchufar tal cual. Lo que lleva puesto el guardia **no se ve**.
  - **Sí es posible** lo que propone el jugador (modelo del jugador con cabeza de aldeano): un `HumanoidModel` propio
    con una caja extra para la **nariz** del aldeano, un renderer registrado para `EntityType.VILLAGER` que decide por
    la marca `GUARD_TAG` (guardia → modelo nuevo; aldeano normal → se delega en el `VillagerRenderer` de vanilla, para
    no cambiar a nadie) y, con eso, las capas **`HumanoidArmorLayer` + `ItemInHandLayer`** de vanilla (el modelo
    humanoid **sí** implementa `ArmedModel`). Hace falta **textura propia**: el layout UV del aldeano no es el del
    jugador, así que no vale reutilizar su textura; la nariz puede ir en una zona libre del layout (0,32).
  - Alternativa más barata y peor: una **capa de armadura a medida** que copie las poses del `VillagerModel`
    (head/body/arms/legs) — la armadura se vería "encajada" y la espada quedaría pegada al pecho, porque el aldeano
    tiene **un solo bloque de brazos**.

### 3b.11 Post-mortem: la caída de la aldea 10 (medido en el log del jugador)

El jugador preguntó *"¿por qué nadie repara el kiosco? ¿por qué están pasando hambre?"* con una captura de su aldea.
El log de esa partida lo explica entero (01:50-02:00) y salieron **dos bugs de verdad**:

- **Lo que pasó**: una **horda del mundo de 15 enemigos** marchó contra la aldea 10 (`[Horda] 15 de 15 enemigos hacia
  la aldea 10`), los zombies entraron y fueron matando aldeanos uno a uno — **incluido el granjero** (Bartolo) — y a
  las 01:59:58 la aldea **cayó** (`La aldea 10 ha CAÍDO y queda en ruinas: 947 bloques`). El kiosco que se ve roto en
  la captura es de esos minutos.
- **Por qué "nadie reparaba"**: durante un asedio **los goals del pueblo se paran a propósito** (`isVillageUnderAttack`
  en `VillagerRepairGoal`, granjero, herreros y recolector): "en plena refriega nadie se pone a construir". El obrero
  **sí** estaba reparando en cuanto no había asedio declarado (en el log se le ve reponiendo tablones, puertas, tierra
  de cultivo y el compostero) y **lo mataron reparando** ("Ramona (Herrero de armas) *Repuso un bloque* was slain by
  Aggressive Zombie").
- **Por qué pasaban hambre (bug 1)**: con **monstruos dentro del pueblo pero sin asedio declarado** (zombies agresivos
  sueltos) el gestor **seguía repoblando** los puestos que quedaban vacíos. El log tiene **6 reposiciones seguidas**
  (8 de comida cada una) entre zombies que mataban al recién llegado y con el granjero ya muerto: la despensa cayó de
  **comida 20 → 17 → 7 → 3 → 0** y la aldea murió de hambre *y* de la masacre a la vez. **Arreglado**: con monstruos
  dentro del muro (`hayEnemigosDentro`, distancia horizontal, radio del muro) **no se repuebla**: primero hay que
  limpiar el pueblo.
- **Por qué el guardia no hacía la puerta (bug 2, mío)**: el guardia murió en el log **"Durmiendo"**, de viejo, sin
  haber hecho una sola guardia de noche. La culpa era del propio goal: cedía con `estaDescansando`, y como el cerebro
  vanilla manda a los aldeanos a la cama en la franja de descanso, el guardia **se acostaba**. **Arreglado**: el goal
  de guardia ya **no cede por la hora de descanso** (solo se corta si acaba durmiendo de verdad): de noche está de
  puerta, que es justo lo que pidió el jugador.
- **Y por qué se quedó SIN SU PUNTO en la escaramuza inicial (arreglado)**: el log lo dice tal cual — *"Aldea 0
  salvada sin limpiar la horda (3 atacantes sin confirmar): sin recompensa"*. La ola se lanza a 39-47 bloques del
  centro, así que los zombies que caen a más de **32 bloques del jugador despawnean solos** (hostiles: 1/800 por tick
  pasados 600 ticks sin acción) → **no mueren, pero desaparecen**; la lista de **atacantes vivos** se queda con sus
  UUID, el asedio se resuelve como "salvada sin limpiar la horda" y **no paga**. Arreglado por la raíz:
  - Un **asediador no se descarta por alejarse mientras está en campaña** (`AggressiveZombieEntity.removeWhenFarAway`:
    los dos descartes por lejanía del juego — >128 y al azar >32 — pasan por ahí). Al resolverse el asedio el gestor le
    quita la marca (`disableGoToCenter` → `setWorldSiegeIndex(-1)`) y vuelve a poder desparecer como cualquier zombie,
    así que **no se acumulan** por el mundo.
  - Red de seguridad: si a un asediador se lo lleva el juego **sin morir** (`/kill`, un descarte de otro mod, una
    conversión), se le saca de la lista de atacantes vivos (`remove(DISCARDED)`), y al **cargarse** un asediador cuyo
    asedio ya no existe se le quita la marca (`readAdditionalSaveData` → auto-curación). Un asedio no puede quedar
    **imposible de cobrar** por un enemigo que ya no está en el mundo.
- **El TECHO DEL KIOSCO ya se puede reponer ✅ (arreglado)**: el tejado está a la **cota+5** y el obrero solo alcanzaba
  **4,5** desde el suelo, así que se quedaba pegándose cabezazos debajo del agujero, se rendía a los 5 s y lo marcaba
  como **inalcanzable** (`saltados`). Medido con la geometría real (pies en la cota, centro del bloque de tejado 5,5
  por encima): la distancia mínima es **5,50** justo debajo, 5,85 a dos bloques y **6,26** a tres — con 4,5 **no
  llegaba nunca**. Ahora el alcance **crece con lo que el hueco esté por encima** (`REACH` 4,5 + **0,4 por bloque** =
  **6,5** para el tejado), así que lo alcanza desde el patio hasta ~3,4 bloques de separación (el borde de la
  plataforma, radio 3) o desde la propia plataforma. El alcance **base no se toca** (4,5 a la altura del obrero): lo
  que se estira es solo el brazo hacia arriba, para no verle colocar bloques "a distancia".

### 3b.12 El LEÑADOR/REFORESTADOR (etapa B) y la cadena de la madera

- **Quién**: lo hace el **recolector** (el aldeano sin oficio), a **prioridad 6** (su goal de recoger es 5): primero
  barre el pueblo y, cuando no hay nada que recoger, se va al monte. Es el mismo aldeano a propósito: los puestos
  fijos ya son granjero, los dos herreros, clérigo y recolector, y de los **sobrantes** sale la milicia, así que el
  leñador no puede gastar un puesto nuevo.
  > **CAMBIADO en 3b.48 (etapa H)**: el jugador pidió separarlos —*"un aldeano que se especialice únicamente en cortar
  > madera y plantar árboles, para dejar totalmente libre al recolector"*—: el **leñador es un oficio propio**
  > (**FLETCHER**, con su **mesa de flechas** en el taller de la arboleda) y va a **prioridad 4**. El recolector se
  > queda solo con recoger y transportar. Lo de abajo sobre **cómo** tala y replanta sigue vigente tal cual.
- **Qué hace** (`VillagerLumberjackGoal`): **tala** árboles (recorre la columna de troncos hacia arriba, hasta 16) y
  se lleva la madera encima; con 12 troncos, o cuando ya no ve árboles, va al **almacén** a descargar.
- **Repoblación de verdad (lo pidió el jugador: *"que plante los saplings que encuentre de manera distribuida, puede
  ser en el mismo lugar donde lo encontró"*)**. Tres reglas:
  - **En el mismo sitio y con la misma especie**: cada árbol que tala lo **replanta en su base** con una semilla de
    **su misma especie** (roble con roble, abedul con abedul...). Si en ese momento **no tiene semilla a mano**, el
    sitio queda **apuntado** (`Hueco`, hasta 8) y vuelve a él con la primera que consiga: el hueco no se pierde.
  - **Repartidas, no amontonadas**: cuando lleva una **pila de semillas** encima (8) o ya **no ve árboles**, se va a
    plantarlas: elige un **claro** de tierra, a cielo abierto (sin cielo el sapling no crece) y **separado 5 bloques**
    de cualquier tronco, hoja o semilla (`estaDespejado`), así que sale **un árbol por sitio**. Nunca planta dentro
    de la valla ni en el corral anexo (ni en su margen de 3).
  - **De dónde salen las semillas**: de las hojas que caen (las recoge el recolector, que es el mismo aldeano) y del
    **almacén** (de ahí se lleva 16 por viaje). Antes solo se replantaba en el tronco recién cortado y, si no tenía
    semilla en la mano, el monte se quedaba pelado con las semillas apiladas en el cofre.
- **Solo tala árboles DE VERDAD y FUERA de la valla**: radio > muro + 3, y el tronco tiene que estar sobre tierra,
  tener **otro tronco encima** y tener **hojas cerca**. Con eso no se come el muro de la aldea ni las casas, que son
  de troncos (era el riesgo evidente de esta etapa). El **plantado** usa el tag `DIRT` del juego (lo que de verdad
  acepta un sapling): fuera la **arena** de la playa y el **camino de tierra**, donde la semilla saltaría.
- **La arboleda del pueblo (etapa E)**: el leñador también tala y replanta en la **arboleda del pueblo** —la única
  excepción a "dentro de la valla no se tala"— y, mientras no haya crecido ningún árbol ahí, **abona los plantones**
  con la harina de huesos del compostero del granjero. Es lo que da madera a una aldea que nace **sin bosque** (una
  islita, un desierto, una llanura pelada): ver 3b.17.
- **Cadena de la MADERA (sin esto los troncos no valían para nada)**: el herrero de **herramientas**, en su mesa,
  ahora también **asierra**: 1 tronco → 4 tablones (`OBJETIVO_TABLONES` = 32 en el almacén; el escudo pide 6) y
  2 tablones → 4 palos (`OBJETIVO_PALOS` = 64; arcos y flechas). Antes **nadie** convertía troncos en tablones ni en
  palos, así que el escudo, el arco y las flechas no se podían fabricar aunque hubiera madera en el almacén.

### 3b.13 El pueblo RECOGE y RECICLA el equipo que sueltan los enemigos

- **El fallo que reportó el jugador** (con captura): "tampoco no se está recogiendo la armadura soltada por el
  zombie". Cierto y era **por lista blanca**: el recolector solo reconocía como "del pueblo" el hierro (espada, pico,
  hacha, pala, azada, escudo y las **cuatro piezas de armadura de hierro**), así que la armadura de **cuero, malla y
  oro** —que es justo la que más sueltan los zombis— **se quedaba tirada por el suelo para siempre**.
- **Arreglado en el recolector** (`VillagerCollectGoal.esEquipoDeEnemigo`): ahora se recoge **cualquier pieza de
  armadura** de las cuatro familias que llevan los zombis (**cuero, malla, hierro y oro**) más las **armas y
  herramientas** de hierro y oro, los **escudos** y los **arcos y flechas** de los esqueletos (que la milicia
  aprovecha tal cual). Lo que **no** se toca a propósito: armadura ni armas de **diamante o netherite** (eso es del
  jugador) ni nada que no esté en la lista.
- **Y no basta con recogerlo: hay que convertirlo en algo.** En la mesa del herrero se añaden las transformaciones que
  faltaban (`VillagerSmithGoal`): **malla → lingote de hierro** (la malla también es hierro), **oro → lingote de oro**
  (armas, herramientas y armadura de oro) y **armadura de cuero vieja → cuero** (una pieza = un cuero). Antes, todo
  eso que ahora entra al almacén **no tenía ninguna receta** y se habría quedado ocupando cofre.
- **Reserva de la milicia (`RESERVA_DE_MILICIA` = 2) — el detalle que casi rompe la etapa**: el herrero miraba el
  almacén **antes** de que la milicia se equipara, así que con una sola espada de hierro la fundía en lingote y
  volvía a fabricar otra espada: un ciclo que **nunca dejaba nada puesto**. Con la armadura pasaba lo mismo, y el
  jugador quiere **ver** la armadura encima de los guardias. Ahora se separa la chatarra en dos:
  - **chatarra pura** (picos, hachas, palas, azadas de hierro): **se funde siempre**, no la lleva nadie.
  - **equipo de la milicia** (espada, escudo y armadura de hierro y de malla) y **cuero viejo**: se funde **solo lo
    que sobra** de la reserva (con 3 piezas se funde 1 y quedan 2 para los guardias); el **oro sí se funde entero**,
    porque el oro no vale para pelear y así no acaba puesto en un guardia: los lingotes quedan como **tesoro del
    pueblo** (el jugador los retira del almacén cuando quiera).

### 3b.14 La GRANJA ANEXA de animales y su GANADERO (etapa D)

Lo que pidió el jugador: *"granja anexa de animales (vacas, ovejas, puercos, gallinas) **fuera de la valla**, con su
aldeano y **dentro del patrullaje de la guardia**"*.

- **Dónde**: al **este**, con el centro del corral a **50 bloques** del centro de la aldea (el muro está a 36 y el
  talud de fuera baja hasta 48). Huella de **15x15** (`ANEXO_RADIO` = 7) **nivelada a la cota del pueblo** como las
  casas y la barraca (`nivelarHuella`), más un **camino de tierra** de 3 de ancho que baja desde la **puerta este**
  del muro (35 → 42 en X). Los bloques de dentro del recinto no se tocan: el nivelado solo recorta terreno natural.
- **Qué lleva dentro**: **valla de roble** con una **puerta de madera** mirando al camino — la puerta es la clave,
  porque **los aldeanos la abren y los animales no**: el ganadero entra y sale y el ganado se queda dentro—,
  **cobertizo** al este (suelo de piedra, cuatro postes, tejado y **sin paredes**, para que pasen todos por debajo;
  dentro su **cama**, su **telar** —el puesto de trabajo del pastor, sin él el juego le borra el oficio—, heno y un
  farol), un **bebedero** de agua a ras del suelo y dos islas de paja más.
- **El rebaño**: 2 vacas, 2 ovejas, 2 puercos y 4 gallinas, **persistentes** (no se los lleva el juego por lejanía).
  Se sueltan **una vez** al construir el corral y, después, solo si el corral se queda **vacío** y ha pasado
  **3 días de juego** (`ANEXO_REBANO_ESPERA_TICKS`, guardado en el `VillageSavedData`): ni la granja se queda muerta
  para siempre si una horda mata a los animales, ni es un **grifo de carne gratis** (matarlos y esperar un rato).
- **El GANADERO (`VillagerAnimalFarmGoal`)**: es el **sexto puesto fijo** del pueblo (antes eran cinco: granjero, dos
  herreros, clérigo y recolector), con oficio de **pastor** (`SHEPHERD`) y su sitio de aparición **dentro del
  corral** (a 47 del centro, **fuera del cobertizo**: si el punto cayera bajo su tejado, `groundY` devolvería la
  altura del tejado y el aldeano aparecería encima). Lo que hace, por orden:
  1. **Recoge** lo que suelta el corral (los **huevos** de las gallinas, lo de un sacrificio) y lo **baja al
     almacén** — de ahí lo pasa el granjero a la despensa.
  2. **Cría**: lleva comida a la pareja de la especie que esté por debajo de su tope (**trigo** para vacas y ovejas,
     **zanahoria/patata/betabel** para puercos, **semillas** para gallinas). La comida sale de la **despensa** y solo
     se usa si al pueblo le **sobra** (`COMIDA_PARA_CRIAR` = 24 puntos): si no, el ganadero se comería el pan de la
     aldea para engordar animales. El parto lo hace el juego (`setInLove` + el `BreedGoal` de vanilla).
  3. **Sacrifica** un adulto cuando hay **exceso** de esa especie (por encima del tope: 6, 8 las gallinas) o cuando a
     la aldea le queda **poca comida** (< 12 puntos), y **nunca baja de la pareja** (2): la granja no se mata sola.
     Los drops se recogen en el acto (carne, cuero, lana, plumas) y van al almacén.
- **La guardia patrulla el corral (y lo defiende)**: cada **3 puntos** de la ronda de día, el guardia baja al corral
  (con un punto distinto por guardia, para no apilarse) y la etiqueta dice "Patrullando el corral". **Los puestos
  están FUERA de la valla**, a los lados del portón (ver **3b.46**: dentro del cercado el guardia no llegaba nunca y
  se quedaba empujando la valla). Además el radio
  de **persecución** sube de `FENCE_RADIUS + 8` (44) a `FENCE_RADIUS + 22` (58) y el de "término del pueblo" a
  `+26` (62): con el radio viejo, un zombi dentro del corral (a 43-57 del centro) se paseaba **a 5 bloques de la
  ronda sin que nadie fuera a por él** — el anexo quedaba fuera de la guardia, que es justo lo que el jugador pidió
  que no pasara.
- **Migración 31**: las aldeas ya construidas reciben el corral (con el rebaño) al latido siguiente, y los bloques
  entran en el **plano** para que el obrero los reponga (invariante I8). El **tope de crecimiento** de la aldea pasa
  de 5 a **`puestosDelPueblo()`** (6): sin eso, una aldea con sus cinco oficios cubiertos **nunca** habría tenido
  ganadero.
- **La primera suelta del rebaño no espera nada (arreglado al revisar el guardado del jugador)**: la espera de 3
  días es para **reponer** un corral que se quedó vacío, pero se medía desde `AnexoAnimales = 0` ("nunca soltado"),
  así que en un mundo con **menos de 3 días de juego** (`gameTime` < 72000) la granja anexa se construía y **no
  soltaba ni un animal**: el corral vacío y el ganadero solo, sin tener con qué trabajar. Medido en el guardado: el
  anexo se construyó con el reloj del mundo en **24200**. Ahora la marca `0` suelta **ya** y la espera solo cuenta
  para las reposiciones.
- **Pendiente**: verlo en partida (el cliente tiene que reiniciarse para cargar el mod).

### 3b.15 El COCINERO, el hambre por aldeano y la cría por camas libres (etapa E)

Lo que pidió el jugador: cerrar la cadena de la comida con el **cocinero** (crudo → cocinado: la carne cruda vale
**2 puntos** y la cocinada **4**, así que cocinar **duplica** la comida que ya había), que el hambre sea **de cada
aldeano** y que la cría dependa de que haya **cama libre**.

- **La cocina del pueblo** (`VillageGenerator.asegurarCocina`): un **ahumador** —el puesto de trabajo del carnicero:
  sin él el juego le borra el oficio, la misma trampa que la mesa de herrería o el telar del ganadero— y su **mesa**,
  en la **plataforma del kiosco**, al lado de la despensa (la cocina y el almacén de comida son lo mismo: así el
  cocinero no tiene que ir y venir por el pueblo con la carne en la mano). Es **idempotente** y va **aparte** de
  `asegurarKiosco`: aquél sale antes de tiempo cuando el kiosco ya está, así que con la comprobación dentro, a las
  aldeas ya construidas **nunca** les habría llegado la cocina.
- **El COCINERO** (`VillagerCookGoal`) es el **séptimo puesto** del pueblo (oficio carnicero, `BUTCHER`, con su sitio
  de aparición en la plaza a 8,5 del centro y **fuera del tejado del kiosco**: si el punto cae bajo el tejado,
  `groundY` devuelve el techo y el aldeano nace **encima**). Saca de la despensa lo que se puede cocinar y devuelve su
  equivalente cocinado, **una pieza por una** (no inventa comida: la **transforma**), con su humo y su sonido. Camina
  al **punto del patio** de la despensa —nunca *hacia* el ahumador: está dentro del kiosco, sobre la plataforma, y la
  navegación no puede "llegar" a un bloque sólido— pero **mide contra el ahumador**: sin ese alcance propio,
  "cocinaría" desde la otra punta de la plaza.
- **El hambre es de CADA aldeano** (`COMIDA_TAG` en sus datos persistentes: el `gameTime` de su última ración). Cada
  minuto de juego se reparte **una ración = un punto de comida** (lo que comía la aldea por aldeano y minuto desde el
  principio) y **primero al que hace más tiempo que no come**: si la comida no alcanza, el hambre se reparte en vez de
  cebar siempre a los mismos. Ojo con el detalle que casi se cuela: se pide **por valor y de una sola vez** —sacando
  un punto por boca, uno a uno, cada aldeano se llevaba una **hogaza entera** (4 puntos: `sacarComida` redondea a
  piezas completas) y el pueblo comía **cuatro veces** más de lo que le toca—, así que un pan da de comer a **cuatro**
  aldeanos. Las **crías no gastan ración** (maman de la aldea); a las **3 raciones** perdidas (3 min) el aldeano va con
  **Debilidad** y **Lentitud**, y a los **10 min** el que no comió **muere él** (antes moría "uno al azar de la
  aldea", que es como se moría el granjero mientras el holgazán engordaba).
- **La cría va ligada a las CAMAS LIBRES**: `feedVillagers` no reparte pan para criar si el pueblo no tiene una cama
  de sobra (las camas se cuentan por su punto de interés `HOME` dentro del recinto). Es la regla de vanilla puesta
  donde de verdad decide algo: sin ella el pueblo crecía hasta que ya no cabía nadie.
- **MEDIDA, no a ojo**: cada 5 min el log dice lo que hay y lo que se come (puntos de comida, aldeanos, camas y
  raciones del último minuto), que es lo que hace falta para ajustar el hambre con números.
- **Migración 32** (`CURRENT_LAYOUT`): las aldeas ya construidas reciben la cocina al latido siguiente y sus bloques
  entran en el **plano** para que el obrero la reponga (invariante I8). El **tope de crecimiento** pasa a
  `puestosDelPueblo()` (once desde la etapa H), y el cocinero queda fuera del reparto de obreros y de la milicia (cupo
  propio, como el
  ganadero).
- **El lint también vigila los goals nuevos**: `tools/lint_aldea.py` tenía en su lista solo los goals viejos, así que
  el leñador, el ganadero, el cocinero, el guardia y el herrero **no estaban pasando** por sus reglas de "atascado =
  no acercarse" (I3) ni de "el aldeano camina por el cerebro" (I6). Ya están dentro; la única excepción —la distancia
  **en 3D** al tronco de un árbol, que sí es intencionada— va marcada con `lint:ok` y su porqué.
- **El tope de población no puede impedir cubrir un PUESTO FIJO (arreglado al revisar el guardado del jugador)**: el
  reparto de "repón el oficio que falta" estaba **dentro** de `vivos < puestosDelPueblo()`, y desde la etapa E el
  tope es 7 (un puesto más). Una aldea que ya estaba **en el tope** (7 aldeanos: los 5 de antes + el ganadero + un
  guardia holgazán) se quedaba **sin carnicero para siempre**: la cocina construida, el ahumador **sin dueño** y la
  carne cruda de la despensa (5 de res y 2 de pollo, medidos en el guardado) sin cocinar, valiendo 2 puntos en vez
  de 4. Ahora el puesto que falta se repone **aunque la aldea esté en el tope** (el tope es para **crecer**, no para
  cubrir un puesto; el que llega de más engrosa la milicia) y sigue costando su comida.
- **El pueblo cuenta a sus aldeanos hasta donde llegan sus propios goals**: el radio de conteo era `muro + 28` (64)
  y el **leñador** trabaja hasta `muro + 40` (76): talando a 70 bloques **no contaba**, así que la aldea creía que se
  le había muerto el recolector (y le reponía un **duplicado**) y su salud bajaba sin motivo. Ahora es `muro + 44`
  (80), que cubre también el corral anexo (43-57).
- **Revisión del log del jugador (aldea 0, migración 31→32)**, lo que se comprobó y salió bien: la migración corrió
  entera (muro a la cota 63, anexo, **cocina en el kiosco**, plano de 2705 bloques); el hambre por aldeano reparte
  **7 raciones para 7 aldeanos** con **14 camas** (una ración = un punto, no una hogaza por boca); y las "7 + 43
  cosas que no eran comida" que se movieron de la despensa al almacén eran **semillas de calabaza** (el granjero
  solo siembra trigo, zanahoria, patata y betabel), o sea la regla funcionando. El leñador no apareció en el log
  porque en ese momento era **de noche** (los 7 aldeanos salen "Durmiendo" en el guardado), no porque estuviera roto.
- **Pendiente**: verlo en partida (hay que reiniciar el cliente para cargar el mod) y **ajustar los números** del
  hambre con el log de medida (lo que se come por minuto contra lo que producen la huerta y el corral).

### 3b.16 El GALLINERO y los PORTONES de valla (etapa E)

Lo pidió el jugador: *"la puerta del corral es una puerta normal y debe ser puerta de corral, para que se conecte
correctamente; el aldeano debe poder abrirla y cerrarla"* y *"una granja de pollos que no se salgan del corral, que
estén encerrados, y que los huevos también se recojan"*.

- **Portones de VALLA** (`VillageGenerator.asegurarPortones`): el corral y el gallinero llevan **puerta de valla**, que
  es lo que **encaja con la valla** (la de madera quedaba como un parche suelto). En las aldeas que ya tenían corral
  se retira la puerta vieja **entera** (sus dos mitades). Es idempotente y va **aparte** de `asegurarGranjaAnexa`
  —que sale antes de tiempo cuando el corral ya está— para que llegue también a esas aldeas.
- **Y quien los abre es el PUEBLO** (`VillagerGateGoal`): el juego **no deja** que un aldeano abra una puerta de
  valla (su cerebro solo sabe abrir `DoorBlock`), así que sin este goal el ganadero se quedaría **fuera del
  gallinero** (sin poder recoger los huevos) o **encerrado** en el corral. El goal **no ocupa banderas**: no mueve al
  aldeano, así que va **a la vez** que su faena (caminar, cuidar el rebaño, patrullar). Dos reglas para que sea
  educado: **solo abre** con un aldeano pegado al portón (el ganado no se escapa por un portón abierto todo el día) y
  **solo cierra** el que abrió el propio pueblo (si lo abre el jugador, manda él; se lleva la cuenta en un registro
  de portones "nuestros"). Se le pone a **todos los adultos** de la aldea: el ganadero vive ahí, la guardia patrulla
  el corral y cualquiera puede bajar al anexo.
- **El GALLINERO** (`VillageGenerator.gallinero`): un corralillo de valla **con tejado** (interior de 5x2) en la franja
  **norte** del corral, compartiendo su valla por el oeste y el norte, con **paja para anidar**, farol y su **portón**
  en la pared sur. Una valla sola **no encierra a una gallina** (aletea y salta): el **techo** es lo que de verdad las
  deja encerradas, y además así los **huevos caen dentro** del corralillo, donde el ganadero los recoge (entra por el
  portón, que el pueblo le abre). Las 4 gallinas del rebaño inicial **se sueltan ya dentro** y, al construir el
  gallinero, las que anden sueltas por el corral **se meten** dentro (una sola vez).
- **No le quita sitio a nadie**: la franja norte estaba libre (el cobertizo está al este, el bebedero al sur) y los
  **puntos de patrulla de la guardia** se corren a `base-2 … base+6`: con los viejos (`base-4`) un guardia habría
  tenido su punto **dentro** del gallinero y se habría pasado la ronda chocando con la valla.
- **Migración 33** (`CURRENT_LAYOUT`): las aldeas ya construidas reciben el gallinero y los portones, y todo entra en
  el **plano** para que el obrero lo reponga (invariante I8).
- **El corral se REPARA solo (migración 34, lo pidió el jugador al ver la granja rota)**: en su partida, la franja
  **oeste** del corral se había quedado **sin suelo** (aire, con el **agua del mar** colándose por debajo) y el terreno
  firme estaba **3-4 bloques por debajo de la cota**. Sin apoyo, la **puerta de madera se cayó sola** (una de valla no
  necesita apoyo, pero entonces queda colgando) y la valla y el gallinero quedaron **en el aire**, sobre el agua.
  Ahora `asegurarCercaDelAnexo` (idempotente, en cada latido) **tapa el suelo** del corral —aire y **agua suelta**— y
  **repone la cerca y el portón**: solo toca el **aire** (nada de lo que ponga el jugador), respeta el **bebedero** y
  el portón se repone aunque el hueco esté **vacío** —antes solo se sustituía una puerta *existente*, así que con la
  puerta ya caída no ponía nada—. Medido en su guardado: **286 bloques de suelo**, el portón y **una valla**. El suelo
  también se sella al construir el corral, para que no vuelva a nacer hueco.
- **Y una trampa del nivelado que conviene tener fichada**: `nivelarHuella` **recorta** lo que sobra por encima de la
  cota y **rellena** lo que falta… pero si encuentra algo **sólido por encima** de la cota (una plataforma, un tronco),
  `groundY` devuelve esa altura y la columna **no se rellena por debajo**: en una orilla, la construcción puede quedar
  **colgando sobre el agua** con el terreno a 3-4 bloques. Es la explicación más probable de por qué el corral anexo
  nació al borde del mar con el suelo hueco.
- **Pendiente** (decisión del jugador, para más adelante): si los huevos se **cocinan** (un alimento nuevo) o se
  **guardan** para que el cocinero haga **pasteles** con alguna mejora y materiales. De momento solo se **recogen** y
  van al almacén, como hasta ahora.

### 3b.17 La ARBOLEDA DEL PUEBLO (la madera de una aldea sin bosque)

Lo planteó el jugador: *"cuando la aldea se genera en medio del mar, ¿cómo va a cortar y plantar árboles el leñador si
no hay? … una solución orgánica que no rompa las reglas ni el lore"*.

- **El problema, medido en su partida**: la aldea está en una **orilla** (al oeste, mar abierto: de rel −58 a −38 todo
  es agua; al este, la granja de animales). El leñador busca árboles **fuera de la valla** (radio 39-76) y los
  **plantones** los saca del almacén (los recoge el recolector de lo que sueltan las hojas de los árboles que él mismo
  tala). Sin árboles no hay **ni plantones ni troncos** → se caen los tablones, los palos, los arcos, las flechas y los
  escudos, y el pueblo deja de ser autosuficiente (rompe el pilar de "no dependientes").
- **Lo que NO se hace** (por lore y por las reglas del proyecto): inventar madera, volver al contador abstracto de "la
  aldea produce 8", meter árboles sin tierra, o hacer que la madera dependa del jugador.
- **La solución: la arboleda del pueblo** (`VillageGenerator.asegurarArboleda`), igual que la aldea ya tiene su
  **huerta** en parcelas y su **granja anexa**: los fundadores traen **seis plantones** —como traen las semillas de la
  remesa inicial de la despensa— y los plantan en la **suya** tierra; el **leñador los tala y los replanta** como
  cualquier árbol. Madera real, de árboles que crecen de verdad.
- **Dónde**: un rectángulo de **césped dentro de la valla**, en la diagonal noreste (rel `20..26, -24..-14`, 7x11),
  libre del anillo de caminos de 29, de los radiales (que van por los ejes), de los solares y de la valla. En una
  islita la tierra segura está dentro, así que ahí es donde tiene sentido (y en cualquier bioma queda bien). El
  jugador lo mandó **alargar hacia el sur** (que es donde sobra sitio hasta la valla): de 4 plantones pasó a 6, en una
  rejilla de 2x3 con cuatro bloques entre árboles. **Verificado contra el guardado del jugador**: la caja entera es
  césped libre, con tierra a la cota debajo, y los seis huecos están libres.
- **La especie es el árbol de la tierra** (`plantonDelBioma`): picea en taiga o tierra fría, jungla en jungla, acacia
  en sabana y badlands, roble oscuro en bosque oscuro, cerezo en cerezal y **roble** cuando no hay uno claro (una
  islita, una playa, mar abierto).
- **La regla que se afina (la única excepción)**: "dentro de la valla no se tala, que el muro y las casas son de
  troncos" pasa a *"…salvo en la **arboleda del pueblo**, que es suya"* (`enLaArboleda`): ahí el leñador puede talar y
  replantar, y la excepción está acotada a una caja conocida.
- **La primera cosecha, con harina de huesos**: mientras la arboleda **no tenga ni un árbol**, el leñador **abona los
  plantones** con la harina del **compostero del granjero** (`Fase.ABONAR`), así una aldea sin bosque tiene madera en
  minutos en vez de esperar. En cuanto crece el primer árbol deja de gastar harina: a partir de ahí la arboleda se
  sostiene sola (se tala y se replanta).
- **Detalle fino que había que respetar**: los plantones se ponen con **`setBlock` directo**, NO con `colocar`, así
  **no entran en el plano**. Si entraran, el obrero vería "aquí debería haber un plantón" donde ya hay un **árbol** y lo
  "repararía" devolviéndolo a plantón en cada latido: la arboleda no crecería nunca.
- **La guardia también la patrulla** (lo pidió el jugador): cada **2 puntos** de la ronda de día el guardia pasa por la
  arboleda y la etiqueta dice **"Patrullando la arboleda"** (si el paso coincide con el corral, manda el corral: está
  fuera de la valla y es el que más lo necesita). Cada guardia tiene su **puesto** y todos van a **un bloque** del
  centro de la arboleda: los cuatro plantones están a dos, así que ninguno se queda plantado donde va a crecer un
  tronco. Es lo que hace que un bicho que entre a por los árboles lo vea la guardia **antes** de que haga daño, igual
  que pasa con el corral.
- **Migración 35** (`CURRENT_LAYOUT`): las aldeas ya construidas reciben su arboleda al latido siguiente.

### 3b.18 La ORILLA SECA (la aldea de mar)

El jugador preguntó por *"esas esquinas de tierra afuera de la circunferencia"* y, al explicarle de dónde salían,
eligió **alisar la línea de agua**.

- **La causa, medida en su guardado**: su aldea nació **al nivel del mar** (una islita), así que el terreno llano del
  pueblo (**y=62**) queda a la **misma altura que la superficie del agua** (también y=62). El primer escalón del talud
  baja un bloque, así que **asoma a la cota en unas casillas y en otras queda sumergido** → la orilla sale **a
  cuadros** (agua a y=62 pegada a césped a y=62, en parches cuadrados). Y como el terreno natural de la orilla está
  3-4 bloques por debajo, además se veía la **tierra de las caras del talud**.
- **El arreglo** (`VillageGenerator.asegurarOrilla`, idempotente: al generar, en la migración y en cada latido): si
  la aldea es **de orilla** se saca un **anillo de playa seca y pareja**, de **4 bloques** de ancho justo por fuera
  de la meseta (radio 38..42), con **césped a la cota** y tierra debajo (rellenando hacia abajo hasta suelo firme).
  La isla gana 4 bloques de orilla, el agua empieza en un **borde limpio** (un círculo) y se acabaron los cuadros.
- **Solo se toca agua o aire**: nada construido (el **camino del corral anexo** se respeta) y lo que ya es césped a
  la cota se deja igual. Y **solo se hace en una aldea de mar**: lo decide mirando si hay **agua en la capa que se
  pisa** dentro del anillo; en una aldea de tierra adentro **no toca nada** (ahí el talud es lo que la hace parecer
  una meseta natural).
- **Verificado con una simulación sobre su guardado** (solo lectura): en el anillo 38..42 hay **335 columnas con agua
  arriba** y **415 con aire** (el terreno natural está por debajo), 252 que ya son césped y **10 con el camino del
  corral** (esas no se tocan) → se rellenarían **1022 bloques**, repartidos **parejos por los cuatro octantes**
  (253-259), o sea que la orilla queda **redonda**.
- **Migración 36** (`CURRENT_LAYOUT`): entra en el plano y el obrero también mantiene la orilla.

### 3b.19 El rebaño VUELVE A CASA y la pareja no se pierde (la carne de la granja)

El jugador avisó de que **"parece que no está generando carne"** y pidió que el que cuida a los animales —**el
ganadero**, oficio `SHEPHERD`, goal `VillagerAnimalFarmGoal`— **los aparee para que siempre haya una pareja**,
"similar a como se hace en la guarida".

- **El diagnóstico, medido en su guardado** (solo lectura): dentro del corral quedaba **una vaca** (a 3,8 bloques de
  su centro) y todo el rebaño andaba **suelto**, a **80-87 bloques** del corral (y el que más lejos, a 130): los que
  estaban **apiñados** en un mismo bloque de y=64 (16 animales) y los demás repartidos por el sur. Un corral **vacío no
  da carne**: el ganadero solo mira **dentro** del corral (cría, sacrifica y recoge ahí), así que sin animales no
  cría, no sacrifica y no baja nada al almacén. Y el "rebaño inicial" solo se reponía si el corral estaba **vacío del
  todo**, con 3 días de espera: con **una** vaca dentro, la granja se quedaba muerta para siempre.
- **Por dónde se escapaban**: el corral tiene **un solo hueco**, el **portón de valla**, y el pueblo se lo abre muchas
  veces al día (a por los huevos, al cobertizo, a dormir) porque el juego no deja que un aldeano abra una puerta de
  valla (de ahí `VillagerGateGoal`). Con las horas, el ganado cruzaba por ese hueco.
- **Los tres arreglos** (migración **37**, `CURRENT_LAYOUT`):
  1. **El rebaño va marcado** (`DevilRpgDelCorral`, en los datos persistentes del animal) al soltarlo. El que se
     pierde (a más de **24** bloques del corral) **vuelve** al corral, a un hueco libre calculado al vuelo (las
     gallinas, a su gallinero). Los animales sueltos **sin marca no se tocan**: pueden ser del jugador.
  2. **Al que le falta pareja se la trae el pueblo** (`reponerParejasDelCorral`): si a una especie le quedan menos de
     **dos adultos** ya no puede criar **nunca** (ni carne de vaca, ni lana, ni huevos), así que se le repone la pareja
     en su rincón. Misma espera larga (3 días) que el rebaño inicial, para que no sea un grifo de carne.
  3. **El ganadero cría con pareja y nunca la sacrifica**: el sacrificio (por exceso o por hambre) exige **más de dos
     adultos** de esa especie, y la cría exige **dos adultos** disponibles (vanilla necesita dos enamorados), así que
     no se malgasta comida en un animal solo. Es el mismo criterio que la **pareja de la guarida**.
- **Además, el portón no se les abre en las narices**: `VillagerGateGoal` no abre (y cierra) si hay un **animal del
  corral a 2,5 bloques** del hueco, con **600 ticks** de margen para que un aldeano no se quede encerrado por una vaca
  tercosa.
- **Reconocimiento del rebaño viejo** (una sola vez, al migrar): los animales de las especies del corral que sean
  **persistentes** (el juego solo marca así a los que alguien ha criado o tocado: un bicho salvaje no lo es) y estén
  **fuera de la muralla** y a menos de **96** bloques del corral se dan por del pueblo y vuelven a casa. Lo de
  **dentro de la muralla no se toca jamás**: si el jugador tiene allí su corral, son suyos.

### 3b.20 Los PICOS DE LAS ESQUINAS (los "triángulos de tierra")

El jugador vio **triángulos de tierra en cada esquina** de la meseta y pidió quitarlos; al primer arreglo (rebajarlos a
la base del talud) respondió con una captura de su isla: *"esos triángulos de tierra que aparecen en cada esquina...
**ahí debe haber agua**"*.

- **La causa**: el suelo llano del pueblo es un **cuadrado** (`nivelar` allana de `-radio` a `+radio` en X y en Z) y el
  **talud** es un **círculo** (mide la distancia con raíz). En las diagonales el cuadrado llega a `38 * √2` = **53,7** y
  el talud solo baja hasta `38 + 10` = **48**: a cada esquina le sobraba un **triángulo allanado a la cota**, colgado
  sobre el mar y con las **caras del corte a la vista** (tierra).
- **Medido en su guardado**: en la diagonal, el talud bajaba hasta **y=58** en los pasos 30-33 y en el 34 ya estaba
  otra vez a la cota (y=62) hasta el borde del agua; el corte contra el mar era **vertical**. Debajo del relleno del
  pueblo seguía el **fondo marino natural** intacto (grava a y=51-52 y piedra debajo, el mismo que el mar de al lado).
- **El arreglo, según dónde esté la aldea**:
  - **Aldea de mar** (la suya): el pico **se quita y su sitio lo ocupa el agua**. Se baja hasta el **fondo natural**
    (lo primero que no sea relleno del pueblo: la grava, la arena o la piedra del fondo marino) y se rellena de
    **agua** hasta la superficie del mar, así que la isla queda **redonda** y el agua llega limpia hasta el talud. El
    nivel del mar de una aldea de orilla **es** la capa que se pisa menos uno (`prepararTerreno` nivela a
    `nivelDelAgua + 1`), así que el agua nueva queda a la misma altura que la de al lado.
  - **Aldea de tierra adentro**: no hay mar que poner, así que el pico se **rebaja hasta la base del talud** (una
    terraza baja, con césped) y la meseta queda con la misma pendiente por todos lados.
- **Solo se toca** lo que está **dentro del cuadrado allanado**, **más allá del talud** y **con relleno del pueblo
  encima** (césped o tierra a la altura del allanado, o ya rebajado en una pasada anterior): una **loma natural**, una
  **duna** o una **playa de arena** no se tocan.
- **Verificado con una simulación sobre su guardado** (solo lectura): **169 celdas** de pico se convierten en agua
  (45 en tres esquinas y 34 en la del suroeste, donde el terreno natural está a ras del agua) y solo **11** se quedan
  como están (fondo natural a y=61, o arena natural a la vista). Las tres esquinas de mar quedan con agua hasta el
  borde del talud, sin una sola cara de tierra.
- **Migración 38** (`CURRENT_LAYOUT`), en el mismo cambio que el rebaño (37).

### 3b.21 La COMIDA que no llegaba (y los tres motivos, medidos)

El jugador avisó de que **"todavía no hay comida: el granjero no cosecha todavía y el que cuida los animales no produce
carne aún"** y de que **"el leñador no está cortando los árboles de adentro de la villa"**. Se miró su guardado y el
log de su partida (23:47-23:53) y eran **tres cosas distintas**, las tres con dato:

- **La huerta se reiniciaba en cada migración** (`VillageGenerator.farm`). Esa pasada llamaba a `colocar` con tierra de
  cultivo y un cultivo **joven** en **las 144 celdas** cada vez que corría (y la limpieza del solar se llevaba por
  delante lo plantado), así que la migración 37 dejó la huerta entera de brotes. **Medido en su guardado**: 144
  cultivos con las edades **0-2** (trigo: 24 de edad 0, 13 de 1, 6 de 2…) y solo **2 maduros**, con la despensa a **0
  puntos** y 9 bocas (que se habían comido lo que había: en los bolsillos de los aldeanos quedaban **32 panes**). Ahora
  si la celda **ya tiene un cultivo no se toca** (ni el cultivo ni su tierra de cultivo, que al reponerla se le
  reiniciaba la humedad): la huerta sigue creciendo donde iba y la migración deja de tirar la cosecha.
- **La aldea no tenía GANADERO**. **Medido**: sus 8 aldeanos adultos eran granjero, clérigo, herrero de armas, herrero
  de herramientas, holgazán y carnicero — **sin pastor** —, con el corral construido y el rebaño dentro. El motivo es
  un **círculo vicioso**: reponer un puesto fijo exigía `comida >= 8`, y la aldea tenía **0** (y sin ganadero no hay
  carne, así que no podía salir del hambre nunca). Ahora un **puesto fijo vacío se repone aunque la aldea esté
  hambrienta** (y, si no hay comida, **no cuesta comida**): es el puesto el que produce. El crecimiento por crías
  sigue costando comida, que es lo que evita que el pueblo crezca sin comer.
- **El leñador no tocaba los árboles de dentro**: la regla era "dentro de la valla no se tala" (el muro y las casas son
  de troncos) con la única excepción de la arboleda. El diagnóstico primero (contar troncos con tierra debajo y otro
  tronco encima) exageraba —incluía los tramos del muro y los postes de las casas—, pero el problema era real: dentro
  del recinto **no se talaba nada** que no fuera la arboleda. El arreglo definitivo está en la viñeta de los árboles de
  dentro, más arriba: la prueba de **forma** (`esArbolSuelto`). Lo de dentro se tala pero **no se replanta** (la aldea
  se **despeja**); la madera nueva va al monte de fuera y a la arboleda, que sí se cuida.
- **Y la carne no llegaba sola a la mesa**: el ganadero deja la carne en el **almacén**, pero el contador de comida
  de la aldea y las **raciones** miran la **despensa**. El granjero hacía de puente en cada visita, pero solo iba a la
  despensa cuando llevaba 4 cosechas encima: con la huerta reiniciada (punto 1) **no iba nunca**, así que la carne se
  quedaba en el almacén y el pueblo seguía hambriento con el almacén lleno. Ahora, con la despensa por debajo de
  **8 puntos** y comida esperando en el almacén, el granjero **va a por ella** aunque no lleve nada que entregar.

### 3b.22 La RECOGIDA POR OFICIO, el despeje del recinto y la luz del corral

El jugador avisó: *"nadie recoge los materiales del suelo y el recolector no se da abasto, sería mejor que cada oficio
recoja del suelo los materiales propios de su oficio, además de obtenerlos de los cofres del almacén o del kiosco
según su oficio. El leñador no está cortando los árboles que están dentro de la aldea y no está plantando y el corral
no está generando carne"*. Cuatro cosas, todas medidas:

- **El suelo estaba lleno y el recolector no llegaba**: en su guardado había **15 pepitas de hierro**, 5 de carne
  podrida, una bota, pan, cuerdas y **4 plantones** tirados, mientras el **herrero de armas** esperaba hierro para
  forjar (en el log: "Fundio 9 pepitas en un lingote", con las demás por el suelo). El **recolector es UN aldeano** (el
  holgazán) y encima es también el **leñador**, así que no le daba la vida. Dos arreglos:
  - **Recogida por oficio** (`VillagerPickupGoal`, prioridad **3**): cada oficio barre del suelo **sus** materiales y
    los guarda donde le toca — el **granjero** el grano, las semillas, los vegetales, el abono y el pan (a la
    despensa: así recoge también lo que el cerebro vanilla siega y deja caer, que era comida perdida), los
    **herreros** los metales, el carbón, los palos y el **equipo de los enemigos** (al almacén), el **ganadero** la
    carne y los huevos, el **cocinero** lo que cocina y el **clérigo** lo suyo. Radio corto (20 alrededor del
    aldeano): recoge lo que se encuentra **yendo a trabajar** y, cuando el suelo está limpio, vuelve a su oficio. Los
    materiales de los cofres los siguen sacando sus goals de oficio (la fragua del almacén, las semillas de la
    despensa, la comida de cría del ganadero).
  - **El recolector llega más lejos**: su radio era `FENCE_RADIUS + 6` = **42** y el botín de las refriegas cae a
    **43-52** del centro, así que no lo cogía **nunca**. Ahora usa el **término del pueblo** (`+28` = **64**, el mismo
    del leñador) y se corrigió un tope de **24 bloques** que tenía la búsqueda del objeto más cercano.
- **Los árboles de dentro**: la causa **no** era el leñador. El generador **sí** despeja el volumen al construir, pero
  las aldeas **migradas** se encontraron el bosque ya dentro, y además los árboles quedaron **grabados en el plano**
  (al capturarlo se escanea el mundo y los troncos no se descartan a propósito, porque el muro y las casas son de
  troncos), así que el leñador los daba por construidos. Lo correcto **no** es preguntarle al plano (que en una aldea
  migrada miente) sino a la **forma**: `esArbolSuelto` dice que un tronco es un árbol del monte si está **de pie** (eje
  Y: los tramos del muro son troncos **tumbados**), tiene **hojas cerca** por encima (un poste no) y **no tiene nada
  construido pegado** (los postes de las casas van pegados a sus paredes). Con esa regla, `limpiarArbolesDeDentro`
  (al migrar) quita hojas y troncos sueltos de dentro del recinto saltándose la **arboleda**, y el leñador sabe qué
  puede talar dentro.
  - **Controles sobre su guardado** (`build/control_arbol.py`, solo lectura): de la **arboleda** del pueblo, **22
    troncos** y los **22** reconocidos como árbol suelto (se talan y se replantan, como toca); del **anillo del muro**,
    **0** troncos marcados como árbol (no se toca ninguno); y dentro del recinto ya no quedaba ningún árbol suelto
    fuera de la arboleda (los que el jugador veía los había talado el leñador; quedaban las hojas sueltas, que el
    despeje barre).
  - **Nota honesta**: la medida anterior —"137 árboles dentro de la muralla"— contaba como árbol **cualquier** tronco
    con tierra debajo y otro tronco encima, así que incluía los tramos del muro y los postes de las casas. La cifra
    real de árboles sueltos dentro era mucho menor; la regla nueva es la correcta.
- **Y el leñador vuelve a plantar**: la **arboleda** (6 celdas, dentro del recinto) se busca **explícitamente** antes
  del barrido general (una rejilla de 2 en 2 ni siquiera pasa por todas ellas, y dentro del recinto no se planta), y el
  barrido de un claro pasa de **28 a 40** bloques para que, estando dentro del pueblo, alcance los claros de fuera.
- **El corral no generaba carne**, además del ganadero que faltaba, por dos motivos:
  - **Los monstruos se comían al rebaño**: el anexo está **fuera de la muralla** y de noche spawneaban dentro (las
    ovejas pasaron de **5 a ninguna** entre dos sesiones). La cerca lleva ahora **faroles** en las cuatro esquinas y
    los cuatro medios lados (idempotente).
  - **El relevo tardaba**: la espera del rebaño y de la pareja pasa de **3 días de juego a 1**.
- **Y el ganadero se quedaba sin faena** (lo reportó el jugador: *"aparece como que está trabajando pero no está yendo
  a los establos"*): su etiqueta era la **genérica** ("Trabajando", la del cerebro vanilla) porque su goal **no estaba
  corriendo**. La causa, medida en la aldea 1 (corral con 2 vacas, 2 ovejas, 2 puercos y **5 gallinas**): la **cría
  elegía UNA sola especie** —la del hueco más grande, las vacas— y si a **esa** le faltaba su comida se rendía **sin
  probar las demás**. En esa despensa había `wheat_seeds` **73**, zanahoria **3**, betabel **6** y patata asada **4**, y
  **ningún trigo**: las vacas no podían criar (piden trigo) y el ganadero se quedaba plantado en la plaza aunque los
  **puercos** (con zanahoria) y las **gallinas** (con semillas) sí podían. Ahora la cría **prueba todas las especies**
  (de la que más hueco a la que menos) y, **sin faena, se va con el rebaño** (fase `RONDAR`, etiqueta "Con el rebaño"):
  el corral es su casa y su puesto de trabajo, así que se queda con los animales en vez de en la plaza.

### 3b.23 LA MURALLA AL RADIO 62 (la granja, dentro)

El jugador pidió: *"necesitamos que la villa sea más grande para que quepa la granja dentro, modifica todas las
variables que estén relacionadas con el radio del pueblo para que se ajuste"*.

- **Por qué**: con el muro a **36** el **corral anexo** (que ocupa de **43 a 57** del centro) quedaba **fuera**, y eso
  costaba medido: los monstruos aparecían **dentro del corral** de noche y se comían al rebaño (las ovejas pasaron de
  **5 a ninguna** entre dos sesiones) y la guardia no llegaba a defenderlo. Con el muro a **62** la **granja entera
  cabe dentro** (el corral queda a 4 bloques de la valla).
- **Las variables** (todas las que dependen del radio, en un solo sitio):
  - `FENCE_RADIUS` **36 → 62**. De él se derivan solos `LEVEL_RADIUS` (+2, lo que se allana), `RADIO_EXTERIOR` (el fin
    del talud, **74**) y, con ellos, el **nivelado**, el **talud**, la **orilla seca**, el **despeje**, las **rondas de
    la guardia**, los límites de los goals de los oficios, el radio del **rebaño** y el de la **recogida**.
  - **`TRAZADO`** nuevo (una tabla, relativa al centro) con las construcciones **repartidas** para que el pueblo llene
    la muralla: **casas** (-36,-7) oeste, (28,-16) este, (-9,36) sur y (12,-32) norte (la grande) —antés a 21-25 del
    centro, ahora a **33-38**—, **iglesia** (-21,-45), **taller de los herreros** (3,-47), **barraca** (-45,22),
    **parcelas de la granja** (-30,14) y (10,4), **arboleda** (34..40, -41..-31) y los **puestos de los aldeanos** al
    doble (24-27 del centro). El **almacén no se mueve** (va con `VillageStorage`, pegado a la plaza): moverlo dejaría
    los cofres del pueblo —y todo lo que tienen dentro— tirados por el recinto viejo.
  - **Derribo de los trazados viejos** al migrar: `SOLARES_ANTIGUOS` incluye ahora también las casas, la iglesia, el
    taller y la barraca del trazado de **36**, y `RADIOS_MURO_ANTIGUOS` borra los **dos** anillos viejos (**29 y 36**)
    para que el pueblo no se quede con dos murallas cruzándolo.
  - `CURRENT_HOUSES` **15 → 16** (fuerza el rehacer completo, nivelado al radio nuevo incluido, que es lo que reparte
    los solares) y `CURRENT_LAYOUT` **39 → 40**.
  - **Herramientas**: la **guardia** ya no pisa el **corral** en su ronda (con la granja dentro, su valla ocupa de 43
    a 57 al este: si el punto de ronda cae ahí, se corre al pasillo entre el corral y el muro) y la **guarida** se
    aleja: su distancia mínima y máxima al objetivo pasan a derivarse de `RADIO_EXTERIOR` (**+36 / +56 = 110/130**),
    porque con 75 las dos obras se pisarían.
  - **El camino de la granja** sale ahora de la **plaza** hasta el portón del corral: su portón está en el lado
    **oeste** y mira al pueblo, así que el tramo va en sentido contrario al de antes (cuando el corral estaba fuera,
    el camino bajaba del muro hacia fuera; de hecho el bucle se escribía de mayor a menor X y con el corral dentro no
    recorría ni un bloque).
  - **Los radios de búsqueda de los oficios** crecen con el pueblo: el **recolector** busca a 70 de sí mismo (antes
    40: desde la plaza no veía el botín de las refriegas, que cae a **43-52** del centro), y el **leñador** busca
    árboles a 40 y claros a 48 (antes 32 y 40: con el muro a 62 no alcanzaba el monte de fuera desde el centro).
- **Verificado** con el comprobador del trazado (`build/trazado.py`): **0 solapes** entre las 13 construcciones, todas
  dentro de la muralla (la esquina más lejana es el corral, a **58,5** de 62) y los **7 puestos** de los aldeanos en
  patio libre (salvo el ganadero, que vive en el corral).

### 3b.24 La ETAPA F: el tercer bancal, el bosque, la TABERNA y el cuartel de dos pisos

El jugador pidió la etapa siguiente de golpe: *"una 3ª parcela con su granjero porque hay poca comida; todas las
parcelas rodeadas de vallas con varias fence gates y mucha iluminación para que los plantíos crezcan rápido; moved los
árboles a un área más grande, un pequeño bosque donde el leñador tale y replante; una taberna donde trabaje el cocinero
y todos vayan a comer ahí cuando lo necesiten, con dos pisos y el segundo con camas, como una posada; el almacén a
lado de la taberna y los soldados pueden pasar cuando no estén de guardia a comer y reponer energía (más adelante se
podrá implementar cerveza); las barracas más bonitas y con lore: área de entrenamiento y un segundo piso donde estén
las camas"*.

- **Tercer bancal y segundo granjero**: `FARM_PLOTS` pasa a **tres** (tercero en `-28,34`) y el pueblo tiene **dos
  (once desde la etapa H, con el leñador y el tercer
  granjero). Ojo con el detalle que lo habría roto: `slotDeProfesionFaltante` miraba "está o no
  está", así que con un granjero vivo el segundo puesto se daba por cubierto; ahora **cuenta por número**. Cada bancal
  trae su **compostero**, así que cada granjero tiene su puesto de trabajo (vanilla pide uno por aldeano).
- **Bancales cercados, con portones y con luz** (`cercaDelBancal`): anillo de valla alrededor de cada bancal con
  **cuatro puertas de valla** (una por lado) y **faroles en las esquinas y los medios lados**. La luz **no es
  decorativa**: un cultivo solo crece con **luz 9 o más**, así que con faroles la huerta sigue creciendo **de noche**.
  `VillagerGateGoal` abre también esas puertas (son de valla y el juego no deja que un aldeano las abra).
- **El bosque del pueblo**: la arboleda (7×11, 6 plantones) pasa a un **bosque de 22×18** en la esquina **noroeste**
  con **doce plazas** de árbol en rejilla 4×3: ahí tala y replanta el leñador, y por ahí pasa la guardia en su ronda.
- **La TABERNA** (dos pisos, en `24,16`, **pegada al almacén** —que no se mueve, para no dejar sus cofres tirados—):
  abajo el **comedor** con barra, **cinco pipas de cerveza** detrás (los barriles, para la cerveza que vendrá), mesas
  con sillas (poste de valla con plato y sillas de escalera), faroles colgados y porche con dos faroles en la puerta
  norte; la **cocina** en la esquina sureste con el **ahumador** (puesto del cocinero, que se retira del kiosco), su
  mesa de trabajo, el caldero y un barril; y arriba la **posada** con **seis camas** y dos arcas.
- **Ir a comer a la taberna** (`VillagerTavernGoal`, prioridad **6**, por debajo de los oficios y del guardia): el
  aldeano que **tiene hambre** y no tiene faena se va a su mesa, **come una ración de la despensa** (la saca de
  verdad), se queda un rato y sale con **regeneración** ("reponer energía"). **No gasta comida de más**: marca al
  aldeano como comido y el reparto del minuto salta a los que ya comieron; y si no puede ir (no hay taberna, hay
  asedio o no hay comida), el reparto del minuto le da su ración como siempre — nadie se muere de hambre por no llegar
  a la mesa. El cocinero camina a la casilla de delante del ahumador (antes iba al punto del kiosco).
- **La barraca de dos pisos (con lore)**: abajo la **sala de armas** —suelo de piedra, dos **maniquíes** de paja con
  calabaza para ensayar el golpe, tres **dianas** para los arqueros, el **hogar** con su fuego y la **mesa de mapas**
  (ahí se planean las guaridas)—; arriba el **dormitorio** con las `BARRACA_CAMAS` camas, arca y faroles. El testigo de
  "ya está construida" pasa a ser el hogar, así que las barracas de una planta se vuelven a levantar al migrar.
- **Lo que queda para después**: la **cerveza** (las pipas y la barra ya están puestas) y el **arte** de la taberna.
- **Ojo con los puestos de trabajo de vanilla** (lo avisó el jugador): el **barril es el puesto del PESCADOR**, así que
  un aldeano sin oficio (una cría que crece) que reclamara uno de los barriles de la taberna se habría vuelto
  **pescador** — un oficio que este pueblo **todavía no tiene** (tendrá su edificio y su lago en una etapa siguiente).
  Dos blindajes:
  - Las **pipas de cerveza** y la despensa de la cocina ya **no son barriles**: las pipas son de **madera con corteza**
    (`oak_wood`, se ven como toneles) y donde había un barril hay ahora un **cofre** (en la taberna y en la barraca).
    Las tabernas ya construidas se arreglan **en el sitio** con un *retrofit* idempotente (ver `retrofitDeLasPipas`),
    sin rehacer el edificio ni tocar sus cofres.
  - **Red de seguridad de oficios** (`VillageGenerator.esOficioDelPueblo`): un aldeano que tome un oficio **de fuera
    del pueblo** (pescador, bibliotecario, cartógrafo…) **vuelve al reparto** de puestos del pueblo (se le borra la
    memoria del puesto de trabajo y `reponerProfesiones` le da uno de los suyos). Así ningún bloque de puesto de
    trabajo suelto —también los que traen las plantillas de las casas de vanilla— le roba un puesto al pueblo.

### 3b.25 Los RESTOS COLGADOS de la aldea de montaña (la nieve flotando)

El jugador mandó una vista desde arriba de su aldea de **montaña**: *"quita también la nieve que se quedó flotando
cuando la aldea se genera en una montaña"*.

- **La causa**: al recortar el terreno que sobresale de la cota, hay cosas que **no cuentan como suelo** y se quedan
  colgando. La principal es la **nieve polvo** (`powder_snow`): **no bloquea el movimiento**, así que `groundY` (que
  usa el mapa de alturas `MOTION_BLOCKING`) **no la ve**, el recorte para en el bloque de debajo y la nieve se queda
  **en el aire**. Medido en su guardado (aldea 2, centro 1398/1366, cota 95): **589 bloques de `powder_snow`**
  flotando dentro del término del pueblo, que es justo lo que se veía desde arriba.
- **El arreglo** (`limpiarRestosColgados`, al generar y en la migración): dentro del término del pueblo, **por encima
  de la cota** y hasta **+56**, se retira lo que está **sin nada debajo** (aire): **nieve** (capa, bloque y polvo),
  **hielo** (normal, compacto y azul) y **plantas** (matas, flores, hierba alta, plantones, cañas, cactus…). Lo
  **apoyado se queda** (la nieve del suelo del pueblo en un bioma nevado es lo normal) y todo lo **construido** no es
  ni nieve ni planta, así que no se toca (tejados, segundos pisos, faroles colgados, vallas, camas…).
- **Verificado** con `build/verifica_restos.py`: los **589** bloques de nieve polvo de su aldea de montaña son los que
  quita la pasada (todos por encima de la cota y sin apoyo).
- **Migración 43** (`CURRENT_LAYOUT`).

### 3b.26 La TABERNA GRANDE (y la escalera que no se podía subir)

El jugador mandó los **planos** (*Building map: Inn*, dos plantas) y el **arte conceptual** de una posada con
entramado, con el encargo: *"arregla la taberna, está muy pequeña y muy sencilla; las escaleras están mal orientadas y
no se puede subir"*.

- **La escalera, medida antes de tocar nada**: en las escaleras del juego la **cara alta** (el escalón por el que se
  sube) es la que marca `FACING` —comprobado en el `blockstates/oak_stairs.json` del propio juego: `facing=east` es el
  modelo **sin rotar**, y su media losa alta está en `x=8..16`, o sea al **este**—. La taberna vieja subía hacia el
  **norte** con las escaleras mirando al **sur**, así que se veían perfectas y **no se podía subir**. Lo mismo tenía la
  escalera de la barraca (subía al norte mirando al oeste) y las **sillas** de las mesas (con el respaldo del lado de
  la mesa, o sea de espaldas): las tres cosas van ahora en el sentido que les toca.
- **El edificio** (`taberna()`, reescrito entero): **19×15** en la planta baja y **21×17** arriba, porque la planta
  alta **vuela** un bloque sobre la baja (el *jetty* del arte, con las cabezas de viga a la vista). Muros **Tudor**:
  cal (terracota blanca) con entramado de roble oscuro, postes cada cuatro bloques, ventanas de dos cristales, solera
  de piedra labrada. Tejado a dos aguas muy empinado (teja de pizarra), **frontones** con su ventana y **chimenea de
  ladrillo** pegada al muro norte.
- **Abajo, el comedor**: la **cocina** del cocinero (cerrada, con su ahumador, horno, mesa y arca), el **hogar** con el
  fuego metido en el muro (no se pisa, así nadie se quema) y su chimenea, la **barra** con las pipas de cerveza
  (`OAK_WOOD`: el **barril** es el puesto del **pescador** y el caldero el del **curtidor**, oficios que el pueblo
  todavía no tiene, y los dos están prohibidos en la taberna), **seis mesas** con sus sillas y la **escalera**.
- **Arriba, la posada**: una **galería** que cruza la casa y **seis cuartos** (tres al norte y tres al sur) con **once
  camas**, su arca y su farol; el cuarto del suroeste es el pequeño, recortado por la **caja de la escalera**.
- **La escalera nueva**: sube pegada al muro oeste, con la cara alta al norte (hacia donde sube) y dentro de una
  **caja** cerrada por el este: el hueco del forjado es un pozo de un bloque, y sin ese muro el primero que paseara
  por la galería se caería al comedor.
- **La fachada da al oeste** (a la plaza): puerta en el centro del muro oeste, **porche** con toldo (la *enseña* que
  decía esta línea **nunca se llegó a construir**: ver §3b.39) y **camino** desde la plaza (torcido a propósito: en
  recta cruzaba la **parcela de la granja**, y un camino no debe pisar los cultivos).
- **El solar se despeja entero** antes de levantarla —la taberna vieja **cabía dentro** de la nueva, así que sus
  muros, su forjado y su tejado se tiran de una vez— y **lo que hubiera en sus cofres se guarda antes en el almacén**:
  tirar un cofre tira su contenido al suelo (mecánica del juego) y el pueblo no puede perder lo que tenía guardado.
- **Verificado** generando una aldea nueva en un mundo de prueba (arnés temporal + servidor headless) y leyendo los
  bloques del guardado con `build/verifica_taberna.py`: suelo, postes de esquina, puerta, forjado con su hueco,
  los cinco escalones con su `facing`, las seis mesas con sus cuatro sillas, la barra, la cocina, las once camas, las
  seis arcas, el techo/tejado sin agujeros, la chimenea por encima del tejado, ningún bloque de oficio ajeno y el
  camino de la plaza.
- **Migración 44** (`CURRENT_LAYOUT`).

### 3b.27 Los cuatro reparos de la taberna, el almacén y el corral (y la huerta que se reiniciaba)

Después de ver la taberna en su partida, el jugador pidió cuatro arreglos de golpe (y uno más al ver la huerta):
*"no se puede acceder a la escalera desde adentro, hazla doble"*, *"la chimenea está sin protección externa, se puede
ver el fuego desde afuera"*, *"el almacén está demasiado pegado a la puerta principal de la taberna"*, *"la granja está
desalineada"* y *"¿por qué los vegetales están como item por toda la parcela?"*.

- **La ESCALERA no tenía acceso** (y era culpa de la migración 44, no del jugador): el primer escalón quedó metido en
  la esquina suroeste del comedor, con el escalón de arriba delante, la pared al este y la pared al sur. Ahora es
  **doble** (dos bloques de ancho), arranca a dos bloques de la pared sur —se entra **de lado**, desde el comedor, por
  la casilla `(3, z)`— y desemboca en la galería de la posada, con su muro de caja al este para que nadie se caiga al
  comedor. El cuarto pequeño del suroeste se recorta para dejarle sitio.
- **La CHIMENEA, por fuera**: el hogar está en la boca del muro norte y su cara de la calle dejaba ver la llama. Ahora
  el caño de ladrillo va **por delante del muro** (como en el arte conceptual), tapa esa cara y sube hasta por encima
  del tejado con su remate de losa.
- **El ALMACÉN se muda al lado de la taberna** (`(48, 21)`, a su espalda) en vez de estar delante de su puerta
  principal (`(18, 18)`), y **crece**: cobertizo de **7×7** con **seis cofres dobles** (doce cofres) en vez de cinco
  por cinco con tres. La migración **pasa lo que hubiera en los cofres viejos al nuevo antes** de retirar el
  cobertizo viejo: tirar un cofre tira su contenido al suelo y el almacén guarda lo que el pueblo ha recogido.
- **El CORRAL estaba desalineado**: medido en su guardado, el **cobertizo** era del trazado de **radio 5** (11×11) y
  la **valla** del de radio 7 (15×15) —la valla creció y el cobertizo se quedó donde estaba, porque
  `asegurarCercaDelAnexo` solo rellena lo que falta y `granjaAnexa` no se vuelve a llamar—. Ahora el corral pasa a
  **19×19** y la migración **retira el corral viejo entero** (solo sus bloques: valla, portón, cobertizo, gallinero,
  paja, bebedero, faroles y camino) y lo levanta de nuevo, así que sale **alineado**. El jugador creía que se le había
  encogido la granja: **no se encogió nada** (15×15 antes y 19×19 ahora) y lo que veía fuera del corral eran animales
  **salvajes** del mundo (ninguno llevaba la marca del rebaño del pueblo). De paso, la caja de búsqueda del **ganado
  perdido** llegaba a 48 bloques del corral y había animales **del pueblo** (con su marca) a 51-57 que no volvían
  nunca: ahora cubre el radio de reconocimiento entero.
- **La HUERTA se reiniciaba en cada migración, y esta vez el culpable era el NIVELADO**: `plot()` nivelaba la huella
  del bancal con `nivelarHuella`, que **recorta** el terreno que sobresale de la cota y, en una parcela en **cuesta**
  (una aldea de montaña), ese recorte se llevaba por delante los **cultivos ya crecidos** de las celdas altas: salían
  como **objetos tirados por toda la parcela** y luego se replantaban brotes. Medido en su aldea de montaña: las tres
  parcelas con sus 71 cultivos pero casi todos de edad 0-1 y semillas de trigo y de remolacha por el suelo. El arreglo
  anterior (no replantar lo que ya tiene cultivo) **no bastaba**, porque el nivelado rompía los cultivos *antes* de
  llegar a esa comprobación. Ahora un bancal **ya hecho no se toca**: `bancalHecho` mira la tierra de cultivo y, si
  está, solo se aseguran el **compostero** y la **valla** (lo que no pisa los cultivos). La tierra de cultivo sí está
  en el plano, así que si alguien la pisotea la repone el obrero.
- **La BASURA que nadie recogía** (lo pidió el jugador antes: *"nadie recoge los materiales del suelo"*). Medido en
  su guardado: **53 plantones de abedul** y **19 palos** colgados en las copas de su aldea de mar, y puertas, vallas y
  camas tiradas por el suelo del pueblo después de un asedio. Dos motivos, los dos arreglados:
  - **Las copas**: el leñador talaba el tronco y las hojas caían solas, pero sus semillas y palos quedaban **encima de
    las copas de los árboles de al lado**, en el aire, y ningún aldeano llega a un objeto que está cinco bloques por
    encima de sus pies. Ahora el leñador **desrama** el árbol que tala (las hojas de su copa): los plantones van a su
    zurrón, para replantar, y el resto cae **al pie del árbol**, al suelo, donde el recolector lo encuentra.
  - **Las piezas del propio pueblo**: una puerta, una valla, una losa o una cama rota por un asedio caía al suelo y se
    quedaba ahí para siempre, porque no era comida ni material de ningún oficio. Ahora entran en la lista del
    **recolector** (que es el que barre lo que no es de nadie) y acaban en el almacén.
- **Verificado** con el arnés temporal y el servidor headless: la huerta **madurada a mano** sobrevive a una llamada
  de `farm()` (72 cultivos antes y 72 después, con sus 72 maduros), los cinco escalones dobles con su meseta libre, el
  hogar tapado y el caño por fuera, el almacén nuevo con sus doce cofres, el corral de 19×19 con su cobertizo, su
  gallinero y su bebedero, y sin la valla vieja de 15×15. Y **la migración sobre una aldea YA construida** (que es el
  camino que va a seguir su partida), simulada de punta a punta: con un almacén viejo con **7 diamantes y 5 de hierro**
  dentro y **3 de oro** en el cofre de la taberna, tras la migración los tres montones están **enteros en el almacén
  nuevo** (`7/7`, `5/5`, `3/3`), el cobertizo viejo no está, el corral queda alineado (valla y cobertizo nuevos, la
  valla de 15×15 y el cobertizo viejo retirados), la taberna pasa de `testigo=false` a `testigo=true` y la huerta sigue
  con sus 72 cultivos.
- **Migración 45** (`CURRENT_LAYOUT`).

### 3b.28 La PESQUERA: el pescador, su edificio y su lago (etapa G)

Lo pidió el jugador desde la etapa F: <i>"el barril es del pescador... el pescador tendrá su edificio y su lago más
adelante"</i>. Durante cuatro etapas el pueblo <b>no usó barriles a propósito</b> (las pipas de la taberna se hicieron
de madera con corteza, el caldero se quitó de la cocina) para que ningún aldeano sin oficio tomara un oficio que el
pueblo no tenía. Ahora el barril <b>tiene dueño</b>.

- **El lago** (`pesquera()`, en el campo del sureste, en (20, 44)): **7×7 de agua** a dos capas con el fondo de arena y
  su <b>orilla seca</b> de arena, una <b>pasarela</b> de tablones hasta el centro (con sus postes dentro del agua,
  donde se pone el pescador), dos <b>faroles</b> en las esquinas y el <b>camino</b> desde la plaza (que, como el de la
  taberna, no cruza ningún bancal).
- **La caseta** (5×5): suelo de tablones, muros con la <b>puerta en el centro del muro sur</b> (sale derecho a la
  pasarela), ventanas de cristal, tejado a dos aguas, su <b>cama</b>, su <b>arca</b> y su farol. Y fuera, junto a la
  puerta, el <b>BARRIL</b>: el puesto de trabajo del pescador en vanilla, que es lo que le da el oficio.
- **El PESCADOR** es un <b>puesto fijo más</b> (nueve ya: dos granjeros, los dos herreros, el clérigo, el holgazán
  recolector/leñador, el ganadero, el cocinero y él). Su goal (`VillagerFisherGoal`) va a la pasarela, se pone con la
  caña (se le ve trabajar, con su salpicadura y su sonido) y **saca un pez DE VERDAD del lago**: la entidad se va del
  lago y su pescado crudo va a la <b>despensa</b>, donde el cocinero lo ahúma (crudo = 2 puntos de comida, cocinado =
  4, igual que la carne del corral: es la <b>segunda fuente de proteína</b> del pueblo).
- **El lago se repuebla solo y despacio** (un pez cada dos minutos, hasta 6): lo que el pueblo come de pescado está
  limitado por lo que **cría su lago**, no por un contador de comida. Al construirlo se suelta una bandada de 4
  (dos de cada tres cods y el tercero salmón).
- Y de paso: el pescador también <b>recoge del suelo</b> el pescado que se le cae (y el que salta a la orilla), a la
  despensa, como los demás oficios con lo suyo.
- **Verificado** de punta a punta con el arnés temporal y el servidor headless (chunks forzados, porque el latido
  necesita jugador cerca): el lago, la orilla de arena, la pasarela, la caseta (suelo, puerta, cama y arca), el barril
  y los peces están; el pescador existe con su goal; y **a los 73 s el lago tenía un pez menos (3 → 2) y la despensa
  un pescado más (0 → 1)**.
- **Migración 46** (`CURRENT_LAYOUT`).
- **Corrección (migración 57)**: el **estanque se quedaba sin agua** en las aldeas ya construidas (el nivelado de las
  migraciones siguientes lo tapaba con tierra y césped, y con el barril en pie nada lo reparaba): ver **3b.41**.

### 3b.29 Los bugs de esta ronda (y lo que se hizo para que no vuelvan)

El jugador fue reportando fallos sobre lo ya construido, y pidió una cosa más: *"todos los bugs que encuentres
documéntalos y haz algo para que no se vuelvan a repetir"*.

| Bug (lo que vio) | Causa medida | Arreglo | Guardia para que no vuelva |
|---|---|---|---|
| *"Las granjas todavía spawnnean con vegetales como items sobre ellos"* (**dos veces**) | El **nivelado de la huella** del bancal (`nivelarHuella`) **recorta** el terreno que sobresale de la cota: en una parcela en **cuesta** (aldea de montaña) se llevaba los cultivos de las celdas altas antes de la comprobación de "ya hay cultivo" (el arreglo anterior solo cubría el replante) | `bancalHecho()` (parcela hecha = no se toca) **y** `hayCultivos()` (si hay plantas, **no se nivela**) | **Regla I11** en `docs/aldea-invariantes.md` **y regla I10 del lint** (`nivelarHuella(...PLOT_...)` sin guardia delante **falla la puerta de commit**) |
| *"La escalera está inaccesible; hazla doble"* | La migración 44 la dejó de **un** bloque de ancho y con el primer escalón metido en la esquina (el escalón de arriba delante y las paredes al este y al sur) | La escalera es **doble**, arranca en `z=Z1` con el **comedor abierto delante** (se entra de frente, desde el sur) y su pozo va cerrado por el este | El **testigo** de `tabernaConstruida` exige la escalera doble: una taberna vieja se rehace entera |
| *"Los pilares entre el 1er y 2do piso están defasados"* | La planta alta **volaba** un bloque (el *jetty* Tudor): los postes de arriba caían una columna al lado de los de abajo | Los dos pisos van **a plomo** (`TABERNA_VUELO = 0`): los pilares caen justo uno encima del otro | El testigo exige el pilar de la posada **sobre** el de abajo |
| *"La chimenea del primer piso está descubierta y se ve desde afuera"* | El hogar está en la boca del muro y su cara norte daba a la calle: se veía la llama desde fuera | El caño de ladrillo va **por delante del muro** y tapa esa cara | Verificado con el arnés (`hogar_tapado=true`) |
| *"¿Por qué el granjero está durmiendo parado?"* | La etiqueta genérica (`refrescarEtiquetas`) ponía **"Durmiendo"** a todo aldeano en la **franja de descanso** del cerebro (toda la noche), **no** a quien está en la cama: el granjero estaba de pie en la calle, despierto y con la etiqueta de dormido | `"Durmiendo"` **solo** con `isSleeping()`; en la franja de descanso, `"Yendo a la cama"` si tiene cama y **`"Sin cama"`** si no (la etiqueta avisa de lo que falta) | Medido con el arnés (chunks forzados + noche): de 9 aldeanos, **7 reclaman cama y duermen**; los 2 sin cama son el ganadero (su cama llega con el corral, en la migración) y el guardia/pescador, que van aparte |
| *"Que mis minions no ataquen a las criaturas neutrales a menos que yo las golpee primero"* | Los minions (lobo, oso, wisps, shulker, esporas) atacaban a **todo** `Mob` salvo aldeanos, llamas, tortugas y golems: también al ganado y a los bichos que solo se defienden | `ITamableEntity.esCriaturaPacificaONeutral()`: no se ataca a animales, neutrales, peces, aldeanos ni golems; a los **hostiles** (`Enemy`) sí, como antes | La regla vive **en un solo sitio** (la interfaz de los minions) y la usan los cinco; y el "si me pegas/le pego yo primero" lo cubren `OwnerHurtTargetGoal`/`OwnerHurtByTargetGoal`, que van por encima |
| *"La basura que nadie recogía"* (plantones en las copas, puertas y camas rotas) | Los drops de las hojas caían **encima de las copas** de los árboles vecinos (inalcanzables) y las piezas del propio pueblo no estaban en la lista de nadie | El leñador **desrama** el árbol que tala; las piezas del pueblo entran en la lista del **recolector** | Documentado en 3b.27 |

Y de propina, verificaciones que evitan sustos: la **migración sobre una aldea ya construida** (el camino que
sigue la partida del jugador) se probó de punta a punta **sin perder nada** —un almacén viejo con 7 diamantes y 5
de hierro, y 3 de oro en el cofre de la taberna, acabaron **enteros** en el almacén nuevo— y la **huerta** sale
intacta (72 cultivos antes y después).

### 3b.30 Lo que viene
- **Milicia**: ✅ completa (barraca, oficio, combate, escudo que bloquea, modelo propio, marcha a la guarida).
- **Leñador/reforestador**: ✅ (tala y replanta, la cadena de la madera del herrero y ahora también **despeja los
  árboles que quedaron dentro de la muralla**).
- **Granja anexa de animales**: ✅ (corral fuera de la valla, ganadero, cría, sacrificio de exceso y patrullaje de
  la guardia).
- **Cocinero, hambre por aldeano y cría por camas**: ✅ (ver 3b.15).
- **Gallinero y portones de valla**: ✅ (ver 3b.16).
- **Arboleda del pueblo (la madera de una aldea sin bosque)**: ✅ (ver 3b.17).
- **Orilla seca de la aldea de mar**: ✅ (ver 3b.18).
- **Rebaño que vuelve a casa, pareja garantizada y esquinas del talud**: ✅ (ver 3b.19 y 3b.20).
- **Huerta que no se reinicia, ganadero que llega aunque haya hambre y leñador que despeja la aldea**: ✅ (ver 3b.21).
- **Recogida por oficio, despeje del recinto y corral iluminado**: ✅ (ver 3b.22).
- **El árbol suelto se reconoce por su forma** (no por el plano, que en una aldea migrada miente): ✅ (ver 3b.22).
- **La muralla al radio 62 (la granja, dentro)**: ✅ (ver 3b.23).
- **Etapa F: tercer bancal, bancales cercados e iluminados, bosque, taberna con posada y cuartel de dos pisos**: ✅
  (ver 3b.24).
- **Los restos colgados de la aldea de montaña (nieve polvo flotando)**: ✅ (ver 3b.25).
- **La taberna grande (plano del INN, vuelo, escalera que sí se sube)**: ✅ (ver 3b.26).
- **Escalera accesible y doble, chimenea por fuera, almacén al lado, corral alineado y huerta que no se reinicia**: ✅
  (ver 3b.27).
- **Los barriles del pueblo y la basura de las copas**: ✅ (ver 3b.27).
- **La PESQUERA (el pescador, su edificio y su lago)**: ✅ (ver 3b.28).
- **Lo siguiente**: la <b>cerveza</b> de las pipas de la taberna (las pipas ya están puestas: son de madera con
  corteza, para que el barril siga siendo del pescador) y, de ahí, lo que pida el jugador.
- **Lo siguiente**: la **verificación en partida** de la cadena entera de la comida (huerta → despensa → cocina →
  raciones) y, de ahí, lo que pida el jugador (la **cerveza** de las pipas y el **pescador con su edificio y su lago**).

### 3b.31 La aldea que cayó "sola" (asedio a ciegas + bichos de las cuevas)

El jugador preguntó *"¿por qué cayó la aldea? ¿qué pasó?"* con el log delante. **La aldea 1 cayó de verdad**, y la
causa medida fue doble: el **asedio era mudo** (no se veía cómo iba) y el **perímetro no miraba la altura** (contaba
como invasor a cualquier bicho que estuviera en las cuevas de debajo).

**Qué pasó exactamente** (log + guardado `New World (1)`, que es el mundo que estaba jugando):

| Hora | Qué |
|---|---|
| 18:46:25 | El jugador pasa por la plaza de la aldea 1 → `start()`: *"Llegaste a la aldea… los monstruos se acercan."* |
| 18:48:09 | `GRACE_TICKS` (90 s) → la ola: *"¡Defiende la aldea de los monstruos!"* (8 + índice 1 × 2 = **10 zombies agresivos** a 65-73 bloques del centro) |
| 18:48:09-18:50:09 | El jugador pelea **DENTRO del pueblo** contra los bichos de la noche (zombies, esqueletos, arañas, creepers, zombies agresivos: el log está lleno de `doHurtTarget`). Media hora después de la caída se midió con los **logs de depuración** (los minions se teleportan junto al dueño y el log imprime su posición cada 10 s): **32 de 32 posiciones registradas estaban dentro de las murallas**, entre **4,5 y 47,8** bloques de la plaza — y la muralla está a **62** |
| 18:50:09 | `SIEGE_TIMEOUT_TICKS` (2 min) **justo** después de la ola → *"La aldea cayó…"* + `ruin()`: **1.483 bloques** al suelo (aire, telarañas, piedra mohosa) |

**No fue "sin aldeanos"**: en el guardado había **11 aldeanos vivos** dentro del pueblo (granjero, herrero de armas,
carnicero, herrero de herramientas, pescador, clérigo, pastor, holgazán, granjero… incluso dos **en el segundo piso de
la taberna**). Fue la rama del **tiempo agotado**: "los monstruos entraron y sobrevivieron". Y la rama solo se toma si
**todos** los atacantes vivos están *dentro*… con la regla de entonces, que era **solo horizontal**.

> **Aviso de escala (el error que hay que no repetir):** la valla está en **62**, no en 36 — un pueblo grande ocupa
> casi todo lo que rodea la plaza, así que "pelear en las afueras" y "estar dentro de las murallas" son lo mismo a
> 40-60 bloques del centro. Medir con el radio viejo (o con el centro de otra partida, que es lo que pasó aquí al
> analizar el log) hace leer una defensa entera al revés. Los comentarios que aún decían "radio 36" en
> `VillageManager` (`spawnWave` y la zona de spawn de la ola) están corregidos.

**Las dos causas, medidas:**

1. **Un bicho en una cueva contaba como invasor.** El perímetro (`PERIMETER_RADIUS` = la valla = **62**) y el latido del
   pueblo (`hayEnemigosDentro`) medían **solo** `dx*dx + dz*dz`. Medido en su guardado (centro real **(990,990)**, cota
   **95**, sacado del plano: x/z 928..1052 = centro ± 62): **24 monstruos** contaban como "dentro de la aldea" y **18**
   estaban en cuevas o repisas (de `y=5` a `y=89`); con la banda de altura quedan **6**, todos a la altura del pueblo.
   - Si uno de los 10 asediadores se metía en una cueva bajo la plaza, la aldea **caía sin que el jugador pudiera
     hacer nada**: no se le ve, y habría que cavar a ciegas en un disco de 62 bloques. La regla escrita del asedio dice
     justo lo contrario (*"si no llegan a los muros, no asedian y no pueden ganar"*).
   - Y el latido del pueblo (cultivar, comer, reparar, **repoblar**) se congelaba por un esqueleto en una cueva, sin
     que hubiera **nadie** dentro.
2. **El asedio era mudo.** Entre los dos avisos ("Llegaste a la aldea", "¡Defiende la aldea!") y el *"La aldea cayó…"*
   final **no había ni un solo mensaje** de cómo iba: ni cuántos quedaban, ni cuántos estaban dentro, ni cuánto tiempo
   quedaba, y los 10 asediadores **no se distinguían** de los bichos de la noche (el jugador mató decenas **dentro del
   pueblo** sin saber que quedaba alguno de los marcados).

**Arreglo:**

- **`VillageManager.dentroDelRecinto`**: la **única** verdad de "dentro de la aldea" para un bicho = disco en XZ
  (invariante I2) **+ banda de altura** sobre la cota (`RECINTO_DY_ABAJO` = 6, `RECINTO_DY_ARRIBA` = 16: cubre la
  zanja y el segundo piso/tejado, deja fuera las cuevas). La usan el **latido** (`hayEnemigosDentro`), el **perímetro
  del asedio** (`allZombiesInsidePerimeter`) y las **partículas de intrusión**.
- **Estado del asedio visible**: barra de acción cada 15 s y cuenta atrás a 30 y 10 s —
  *"Asedio a la aldea: quedan 4 y 2 DENTRO del muro · 0:45 · si aguantan dentro, la aldea cae"*—, cada baja se canta
  al momento (*"Asediador abatido: quedan 3."*), los asediadores van **marcados con brillo** al spawnear
  (`setGlowingTag`, se les quita al resolverse el asedio y al cargarse sin asedio) y el aviso de la ola lo dice.
- **La caída dice el motivo**: *"La aldea cayó: los monstruos aguantaron dentro de los muros."*

**Verificado** con el arnés temporal (mundo aparte, se borra antes de commitear):

| Caso | `dentroDelRecinto` |
|---|---|
| en la plaza / segundo piso (cota+7) / lo más alto admitido (cota+16) | **sí** |
| a 61 del centro (cota) | **sí** |
| zanja (cota-5) / lo más bajo admitido (cota-6) | **sí** |
| pozo (cota-7) / **cueva bajo la plaza (cota-30)** / repisa (cota+30) | **no** |
| a 63 del centro (cota) / cueva a 50 del centro | **no** |

**Guardias para que no vuelva**: invariante **I12** ("dentro de la aldea, para un bicho, es recinto **+ altura**") e
invariante **I13** ("un asedio nunca se pierde a ciegas") en `docs/aldea-invariantes.md`, y **regla I11 del lint**
(un recuento de `Monster`/`MobCategory.MONSTER` en `VillageManager` sin `dentroDelRecinto` cerca **falla la puerta de
commit**).

### 3b.32 Los faroles que flotaban, la despensa a la taberna y la escalera en L

Tres cosas que reportó el jugador de golpe (con capturas): *"en la cabaña del pescador hay faroles flotando"*, *"y lo
mismo en la granja"*, *"el cofre de la comida ya no tiene sentido que esté en el kiosco central; sería mejor moverlo a
la taberna, tomar un cuarto y convertirlo en almacén de comida"* y *"en la taberna aún no se puede acceder a la
escalera: hay una mesa con sillas que la bloquea; quita esa mesa y dobla la escalera en la esquina"*.

| Bug (lo que vio) | Causa medida | Arreglo | Guardia para que no vuelva |
|---|---|---|---|
| *"Hay faroles flotando"* (pesquera **y** granja anexa) | El ayudante `farolEnElPoste` colocaba el farol en la casilla que le dieran **dando por hecho** que debajo había un poste, y los llamantes le pasaban la casilla del **farol** contando un poste que no existía. Auditoría de la aldea 2 (cota 120): **16 faroles SIN APOYO** — 14 en la cerca de la **granja anexa** (1 bloque por encima del poste) y 2 en la **pesquera** (3 bloques por encima de la orilla) | `farolSobreElPoste(apoyo)`: recibe la casilla del **apoyo** y **garantiza el poste**; `posarFarolesFlotantes` **baja** el farol que quedó flotando en las aldeas ya construidas | **I14** en las invariantes, **regla I12 del lint** (una Y con sumando en `farolSobreElPoste` falla el commit), **autocomprobación** `auditarFarolesFlotantes` al generar y al migrar (grita en el log) y **auditoría de las aldeas** (`tools/audita_aldea.py`, versionada) que además mira vallas flotando, cofres tapados, puertas incompletas y camas sueltas |
| *"El cofre de la comida no tiene sentido en el kiosco central"* | La despensa del pueblo (el cofre con la comida de verdad) estaba en el kiosco de la plaza, lejos de la cocina y de las mesas | El cofre **doble** pasa a la **cocina de la taberna** (contra su muro norte): es el "almacén de comida", donde el cocinero cocina y donde el pueblo viene a comer. El kiosco se queda con su campana y su farol | **I15** en las invariantes: el testigo del kiosco es **su plataforma** (con el viejo —que exigía el cofre— el kiosco se reconstruía **cada latido** buscando un cofre que ya no está). La migración 47 retira el cofre viejo **después** de pasar lo suyo a la despensa nueva y al almacén (`retirarDespensaDelKiosco`, idempotente) |
| *"Aún no se puede acceder a la escalera: hay una mesa con sillas que la bloquea"* | La escalera era un tramo recto pegado al muro oeste con el **primer escalón metido en la esquina**, y la **mesa de `(4,11)`** (con sus cuatro sillas) caía justo en el camino de acceso | La mesa `(4,11)` **ya no se pone** (quedan **cinco** mesas) y la escalera es una **L doble**: el pie mira **al comedor** (este→oeste), la **meseta** va en la esquina suroeste y el tramo de arriba sube (sur→norte) a la galería. El **hueco del forjado** es solo el del tramo de arriba | El **testigo** de `tabernaConstruida` exige la escalera nueva (los dos escalones que la identifican): una taberna vieja **se rehace entera** al migrar, y el hueco del forjado, la caja del pozo y la luz van con ella |

**Verificado** con el arnés temporal (mundo aparte, borrado antes del commit):

- **Faroles**: la auditoría de la aldea recién construida da **0 faroles flotantes** (con el ayudante viejo daba 16 en
  la aldea del jugador). En su guardado, la auditoría versionada da **aldea 2: 0 en las cinco listas** (la migración
  47 ya corrió al jugarla) y **aldea 0: 14 faroles flotantes**, que se bajan solos cuando el jugador pase por ella.
- **Despensa**: `VillagePantry.despensa` encuentra el cofre en la cocina y devuelve un contenedor de **54 casillas**
  (cofre doble de verdad: mitades **RIGHT/LEFT**); en el **kiosco** hay **0 cofres**.
- **Escalera**: el volcado de los bloques construidos es exactamente la L (dos escalones de bajada, la meseta 2×2, tres
  de subida) y el **recorrido de un jugador con las cajas de colisión reales del juego** —a pasos de 0,25 bloques por la
  línea de la escalera— **no encuentra ni un solo escalón de más de 0,50** (el jugador sube 0,6 por escalón, así que
  todos se suben) y el pie queda abierto al comedor, sin la mesa ni las sillas que lo tapaban.
  *(El camino de los bichos con la navegación del juego no sirvió para comprobarlo: en un bicho recién spawneado la
  navegación no encuentra camino ni en la plaza llana —se comprobó con un control— y por eso la comprobación buena es
  la de las alturas de los escalones.)*

### 3b.33 La escalera que se sube de verdad y el herrero que no recogía

Dos reportes del jugador en la misma sesión, los dos **con la partida delante y las coordenadas del F3** (aldea 2,
taberna en `1438,1428`, y el jugador de pie en `1439,125,1439`).

| Bug (lo que vio) | Causa medida | Arreglo | Guardia para que no vuelva |
|---|---|---|---|
| *"Hay 4 bloques que están estorbando: 2 de madera pelada y otros 2 de madera normal, justo enfrente de las escaleras"* | La **barra** del comedor empezaba en `dx=5` y el pie de la escalera está en `dx=4` (`TABERNA_ESCALERA_PIE_DX`): su extremo 2×2 (`stripped_oak_log` en `dz=12` y `oak_wood` en `dz=13`) caía **justo en el carril de entrada**. Medido en el guardado: barra en `dx=5..11`, escalones en `dx=4` | La barra pasa a **`dx=7..13`** (la misma longitud, corrida dos bloques al este): la entrada de la escalera queda con dos bloques libres | Se ve en el volcado de la taberna (arriba) y lo aplica la **migración 48** a las tabernas ya construidas |
| *"Los 2 bloques de madera que están justo debajo de los pies míos están estorbando a todo el que quiere subir: su cabeza topa con ellos"* | El **hueco del forjado** tenía **tres filas** (`dz=8..10`) y la **meseta** está en `dz=11..12`: al subir de la meseta al primer escalón de arriba, el caminante (caja de 0,6) cruza el borde del forjado con la cabeza ya por encima de 124 y choca con el tablón de `dz=11` — **que es justo el bloque sobre el que estaba de pie**, de ahí el *"debajo de los pies míos"* | El hueco llega a **cuatro filas** (`dz=8..11`): la meseta queda abierta por el lado por el que se sube. El tramo de abajo sigue BAJO el forjado (dos bloques de altura libre, como cualquier escalera de casa) | El hueco y la barra se calculan de las constantes de la escalera (`esHuecoDeLaEscalera` y `TABERNA_ESCALERA_*`), así que el **volcado de la taberna** los canta |
| *"El herrero de herramientas ni el herrero de armas están recogiendo materiales del suelo (lingotes, pepitas de hierro, armaduras...)"* | `VillagerPickupGoal` **cacheaba la lista de materiales en el constructor**. El pueblo **reparte oficios** (repone el puesto que se queda vacío, una cría crece y hereda), así que a un aldeano al que le cambian el oficio le queda la lista **vieja**: seguiría recogiendo trigo y semillas e **ignoraría el hierro**. *(En sus registros hay un herrero guardando 8–12 cosas cada pocos minutos: el que tiene la lista buena funciona; el que no, ni las ve.)* Y lo que está a más de **20 bloques** del herrero no es suyo: eso lo barre el **recolector** | La lista, el destino y el nombre del oficio se leen **EN VIVO** de `getVillagerData().getProfession()` | **Invariante I17**: ninguna lista por oficio se cachea |

| *"¡Mira cómo dejaste las ventanas! ¡Quedan incompletas las paredes!"* | La **cal de los muros Tudor era terracota blanca**, y `esTerrenoNatural` incluye `BlockTags.TERRACOTTA` (hace falta: las aldeas de meseta cortan terracota de verdad). El recorte de `nivelar` va de la cota hasta `groundY`, y en una columna con la taberna **`groundY` devuelve el tejado** → subía por dentro de la casa y se comía **todos** los paneles: en su guardado los cuatro muros tenían postes, solera, tablones y cristales, y **la cal entera era aire** | El recorte **para en el primer bloque construido** (`nivelar` y el talud) y la cal pasa a **`SMOOTH_QUARTZ`** (cuarzo liso: blanco de cal y sin etiqueta de terreno). La **migración 49** (`rehacerMurosDeLaTaberna`) vuelve a pasar `muroTudor` y `tejadoDeLaTaberna` —solo estructura: no toca despensa, camas ni cocina— | **I18**: la cal no puede ser de un material "de terreno", y ningún recorte sube por dentro de una construcción |

| *"Los cristales no están bien colocados porque se cortan: en un espacio de dos, uno está completo y el que le sigue no; y cuando el espacio es de uno, sólo parece una franja delgada"* | Los `glass_pane` **no conectan con los troncos** (medido en su guardado: los cristales pegados a los postes tenían la conexión en `false`, y conectaban con la cal y entre ellos). Con el patrón viejo (`i % 4 == 1 \|\| i % 4 == 2`) un cristal quedaba pegado al poste → se dibuja **media ventana**, y los huecos de una sola celda entre dos postes quedaban como **franja fina** | El cristal va **solo en la celda central del hueco** (`i % 4 == 2`), **con cal a los dos lados**: conecta por ambos y se ve entero. Los huecos de una celda (extremos de muro) van con cal, sin cristal. Lo aplica la **migración 50** volviendo a pasar los muros | **I19** en las invariantes |

**Verificado** (mundo aparte y guardado del jugador, sin tocar su partida):

- Las celdas exactas de los dos arreglos están comprobadas **contra su guardado**: el forjado de `(dx1..2, 124, dz11)`
  es `dark_oak_planks` (el que le tapaba la cabeza) y la barra ocupa `(dx5..11, 120, dz12..13)`, con `dx=12..13` libre
  para correrla.
- El reparador es **idempotente** y solo quita el bloque **si es del tipo esperado** (`quitarSiEs`), así que no puede
  borrar algo que haya puesto el jugador.

### 3b.34 La taberna, habitada: segunda puerta, el pozo tapado y el desván como base

Tres encargos del jugador de una vez: *"pon una puerta extra en la taberna"*, *"arriba hay un cuarto que está abierto
porque da precisamente al hueco de las escaleras: estaría bien que se tapara con una pared, para que nadie se cayera"*
y *"el cobertizo (el techo de color negro) está todo relleno con bloques: estaría bien que sirviera como 3er piso donde
el jugador pueda establecerse, que tenga todo lo necesario para ser una base, sin modificar la apariencia externa, tal
vez una ventana nada más y escaleras para llegar ahí (dentro de la taberna, no fuera)"*.

| Encargo | Cómo queda | Por qué así |
|---|---|---|
| **Puerta extra** | `DARK_OAK_DOOR` con `facing=SOUTH` en el **muro sur**, `dx=3`, `dz=TABERNA_FONDO-1`, a la cota, con cal encima y viga arriba | `dx=3` **no** es columna de poste (`i%4==0`) y por dentro está libre (la barra empieza en `dx=7`); fuera el patio está a la cota, así que se sale andando (sin escalón) |
| **El pozo tapado** | Tablones en `dx 1..2`, `dz=12`, de `y1` a `yTecho-1` | Es la primera fila **con suelo** al sur del hueco (`esHuecoDeLaEscalera` = `dz 8..11`): cierra el cuarto suroeste sin estorbar la subida, que sale por `dz=8` |
| **El desván (3er piso)** | Se vacía el **relleno** del tejado (`DEEPSLATE_TILES`) en `dx 1..17`, `dz 0..14`, de `yTecho+1` a `yTecho+8` (833 tejas), dejando la cáscara (vertientes, cumbrera y frontones) intacta; se amuebla como base del jugador: cama, mesa de trabajo, horno, dos cofres, yunque, dos faroles sobre poste y dos alfombras | `techoDeLaPosada` pone los tablones en `yTecho-1`, así que lo que se pisa es la **placa del tejado** (`yTecho`) y el desván se anda en `yTecho+1` (6 bloques sobre la posada). La ventana son los **cristales que ya llevaban los frontones**: no se añade ninguna, y desde fuera los bloques son idénticos. El mobiliario **evita a propósito los puestos de trabajo de aldeano** (nada de barriles, calderos ni mesas de oficio) |

> **Corregido en la 52**: la escalera de la 51 subía por el carril **norte de la galería** (`dz=7`, `facing=EAST`, de
> `(3,y1)` a `(8,y1+5)`) y **tapaba el corredor** de los cuartos. La migración **52** (§3b.35) la mueve al cuarto
> suroeste y vuelve a cerrar su hueco: hoy **no** hay ninguna escalera del desván en la galería.

**Notas y riesgos (dichos, no escondidos)**: el farol de la galería que caía en la celda del 4º escalón **se recuelga
al lado** (`colgar` en el carril sur) —y por eso el desván se construye el último y abre el tablón *después* de poner el
escalón, para que el farol no salte al suelo como objeto—; la **cama del desván es un POI** y suma 1 al recuento de
camas del pueblo (puede permitir una cría más; lo pidió el jugador); y la **migración 51** lo aplica a las tabernas ya
construidas llamando a los reparadores **después** de `rehacerMurosDeLaTaberna` (que vuelve a rellenar el tejado, así
que el vaciado tiene que ir después). **No se toca el testigo `tabernaConstruida`**: rehacer la taberna entera tiraría
la despensa y las camas. *(El reparto del farol y la escalera de esta migración quedó **corregido en la 52**: ver
§3b.35.)*

### 3b.35 La escalera del desván, fuera de la galería (migración 52)

El jugador, con la taberna de la 51 ya de pie: *"La regaste, porque al poner la escalera al tercer piso tapaste el
corredor que permite que se entre a los diferentes cuartos del 2do piso. Mejor sacrifica un cuarto del 2do piso para
poner ahí una escalera y libera el corredor para que se pueda pasar."*

| Encargo | Cómo queda | Por qué así |
|---|---|---|
| **La galería, ENTERA y libre** | Los **dos carriles** (`dz=7` y `dz=8`) quedan sin un solo bloque de la escalera. El reparador quita los **seis escalones** de la 51 (`facing=EAST`, de `(3,y1)` a `(8,y1+5)` en `dz=7`) y **vuelve a cerrar** el hueco que abrieron en las dos capas del forjado: **tablones** en `(dx 5..7, yTecho-1, dz=7)` y **tejas de la placa** en `(dx 6..8, yTecho, dz=7)` | El corredor del piso son esos dos carriles: en el muro norte (`dz=6`) están las puertas de los tres cuartos del norte y en el sur (`dz=9`) las de los tres del sur. Con la escalera en el carril norte, **no se podía entrar a los cuartos del norte** (y el que subía se quedaba en el hueco) |
| **La escalera, en el cuarto suroeste** | Una **L de dos tramos** dentro del cuarto suroeste (`dx 1..5`, `dz 10..13`), el que se sacrifica. Tramo de abajo, **sube al NORTE** por `dx=5`: `(5,y1,12)`, `(5,y1+1,11)`, `(5,y1+2,10)`. Tramo de arriba, **dobla al OESTE** por `dz=10`: `(4,y1+3,10)`, `(3,y1+4,10)`, `(2,y1+5,10)`. Las **seis** celdas salen de una sola lista (`celdasDeLaEscaleraDelDesvan`), la misma que abre el hueco | El tope **no** puede ir en la fila del alero (`dz=13`): allí el tejado deja **un** bloque libre y el que saliera se golpearía con él; en `dz=10` hay **cuatro**. El tramo de arriba pasa por encima del **muro que cierra el pozo** (`dx=3`) y del **capuchón del pozo**: el pozo sigue **tapado** (nadie se cae) y el cuarto, que ya estaba recortado por la caja de la escalera de la taberna, es el único sitio donde la L cabe |
| **El hueco del techo, encima** | Se abren las dos celdas que ocupa el que sube en **cada** escalón, en las dos capas: **tablones** en `(5,yTecho-1,10)` y `(4,yTecho-1,10)`, y **tejas de la placa** en `(4,yTecho,10)` y `(3,yTecho,10)`. La placa en `(2,yTecho,10)` la sustituye el **último escalón** (cara alta en `yTecho+1`, la cota del desván: se sale andando) | **I16**: el hueco cubre lo que se sube. Del tope se sale a `(2,yTecho,9)`, `(1,yTecho,10)` o `(2,yTecho,11)`, las tres con suelo y con **3-5 bloques** de alto libre |
| **La cama del cuarto, recolocada** | La cama del cuarto suroeste (`(4,y1,12)` + `(4,y1,13)`) se retira y se vuelve a poner en el **desván**, pegada a la suya (en `(5,yTecho+1,3)` + `(5,yTecho+1,4)`) | En vanilla cada cría necesita una **cama libre**: el pueblo no puede perder ninguna. Es la 12ª cama de la posada y suma al POI como la que había |
| **El farol de la galería** | El que se comió el 4º escalón (`(6,y1+3,7)`) vuelve a colgarse **de su tablón** (el que repone el cierre del hueco) y se retira el **de repuesto** que la 51 colgó en el carril sur (`(6,y1+3,8)`) | **I14**: un farol colgado necesita un bloque sólido encima. Con la escalera fuera de la galería no hace falta ningún repuesto: la galería vuelve a tener los suyos, en su sitio |

**Verificado**:

- **Contra el GUARDADO del jugador** (aldea 2, centro `(1414,1414)`, cota 120, taberna en `(1438,1428)`, `y1=125`,
  `yTecho=130`), con una simulación **de solo lectura** (`build/verifica_escalera_desvan52.py`, no versionado): las
  celdas que toca el reparador son **exactamente** las que esperaba el código. En su mundo están los **6 escalones de
  la 51** en la galería (`facing=EAST`, de `(dx3,y1)` a `(dx8,y1+5)`), el **farol de la galería comido** por el 4º
  escalón (`(dx6,y1+3,dz7)` es un escalón) y su **repuesto** en el carril sur, la **cama del cuarto** en
  `(dx4,y1,dz12..13)`, el hueco de tablones en `(dx5..6,yTecho-1,dz7)` y el de tejas en `(dx6..7,yTecho,dz7)`. La
  simulación da 28 cambios, deja la galería **libre**, cierra el hueco viejo, monta los 6 escalones nuevos con sus dos
  celdas de cabeza libres, deja el pozo tapado y **es idempotente** (una segunda pasada no cambia nada).
- **Comprobación estática** de la geometría (`build/check_escalera_desvan.py`, no versionado), celda por celda contra
  las mismas constantes del código: 6 escalones que suben de uno en uno, galería libre, suelo del desván abierto solo
  encima de la escalera y 4 bloques de alto libre sobre el tope.
- El único pueblo del guardado con la escalera vieja es ése: las otras aldeas no tienen la taberna construida (el
  testigo `tabernaConstruida` falla), así que la reciben **nueva** de una vez.

**Lo que NO se ha podido comprobar**: el movimiento en el juego (llegar andando al pie desde la puerta del cuarto,
subir la L y salir al desván) ni la migración corriendo de verdad sobre su partida: el cliente estaba abierto y se
cerró para poder compilar (el `build` no toca el guardado).


### 3b.36 Las ventanas, de cristal entero (migración 53)

El jugador, mirando la fachada otra vez: *"la ventana sigue estando puesta de manera incorrecta. Mejor pon ventanas de
cristal completo, de las de cubo"*. Tenía razón en zanjarlo: el **panel** (`glass_pane`) se dibuja según sus
conexiones, **no conecta con los troncos** de los postes (medido en su guardado) y ni con un solo panel en la celda
central y cal a los dos lados acababa de verse bien. Ahora las ventanas de los muros Tudor y de los frontones son
**`Blocks.GLASS`** (cristal entero), de **dos de ancho** por hueco: un bloque de cristal no tiene conexiones, así que
siempre se ve entero. Lo aplica la **migración 53** volviendo a pasar los muros y el tejado con el reparador que ya
existía (`rehacerMurosDeLaTaberna`).

### 3b.37 El herrero de armas que "daba vueltas" y los troncos que quedaban flotando (sin migración)

Dos reportes del jugador con captura, los dos en la **aldea 2** (centro `1414,1414`, cota `120`) y los dos
diagnosticados **leyendo su guardado** (`run/saves/New World (1)`: `data/devilrpg_villages.dat`, `region/`, `entities/`
y `poi/`), no a ojo.

| Bug (lo que vio) | Causa medida | Arreglo | Guardia para que no vuelva |
|---|---|---|---|
| *"El herrero de armas da vueltas sobre su eje como un tonto"* (captura: **"Herrero de armas"** con la etiqueta **"Paseando"**) | **Cuatro cosas, todas medidas.** 1) En el guardado los **dos herreros llevan la marca de obrero** (`NeoForgeData/DevilRpgBuilder=1`) y `marcarObrero` solo protegía al granjero: su goal de reparar (**prioridad 3**) **bloqueaba** el del taller (**4**, misma bandera MOVE), así que el herrero se pasaba el día caminando a los huecos del plano (los 9 troncos de acacia de la arboleda, medidos) con 18 pepitas de hierro sin fundir en el almacén. 2) **`free_tickets=0`** en el muelle de afilar `(1419,120,1368)` y en la mesa `(1418,120,1368)` y **ningún** aldeano con ese puesto en la memoria: el único herrero de armas lo tenía solo como `POTENTIAL_JOB_SITE` (ticket **perdido**, imposible de reclamar). Sin `JOB_SITE` vanilla **no registra la actividad WORK** (`addActivityWithConditions(WORK, …, JOB_SITE presente)`) y el cerebro cae a **IDLE** todo el día: guardado con `DayTime=8137` (franja WORK) y la etiqueta genérica diciendo **"Paseando"** (paseo aleatorio y "andar hacia donde mira" = las vueltas sobre su eje). 3) El goal del taller **no decía nada mientras camina** (80 bloques de almacén a taller por pieza), que es la mayor parte del ciclo. 4) `VillageStorage.puntoDeApoyo` devolvía **el centro exacto del cobertizo, que es un POSTE** (rejilla de postes de 3 en 3: `oak_log` medido en `1462,121,1435`): navegar hacia un bloque sólido es el fallo que el propio `puntoDeApoyo` avisaba por escrito | 1) `marcarObrero` pone la reparación en **prioridad 5** también para los dos herreros (como el granjero: **primero su faena**). 2) El goal del taller **reclama su puesto**: libera el ticket perdido (solo si **ningún** aldeano lo tiene) y escribe `JOB_SITE`, y `liberarPuesto` **suelta el ticket antes** de borrar la memoria en el reparto de oficios. 3) Etiqueta y log **también al caminar** ("Yendo al almacén", "Yendo al muelle", …), una línea por transición. 4) `puntoDeApoyo` devuelve la primera **casilla libre** (suelo firme, nada sólido a la cota ni encima) | El arreglo es **de comportamiento**: no hay migración. Queda en el log una línea por transición del ciclo del herrero |
| *"El leñador no está talando todos los árboles completamente en el bosque de la aldea: deja logs flotando"* | El hachazo **solo subía en vertical** (`p = p.above()`), y el tronco de un árbol **no siempre es una columna recta**: la **acacia** sube recta y **tuerce en diagonal** —comprobado bloque a bloque en la arboleda: el árbol entero de `1370,120..124,1391` continúa en `1369,125,1391`, una casilla al lado y una arriba—. La parte torcida se quedaba en el aire y el desramado le quitaba las hojas, así que el trozo colgando ya **no se parecía a un árbol** (`baseDeArbol` exige tierra debajo, `esArbolSuelto` hojas cerca) y se quedaba **flotando para siempre**: **5 troncos huérfanos medidos** en la arboleda a `y=125..126` y **9 troncos de acacia** del plano apuntados como huecos (los que el obrero volvía a levantar, ya sin copa) | El leñador **remata el árbol**: desde la columna talada sigue, con una búsqueda corta, los troncos **pegados o en diagonal hacia arriba** que pasen `esTroncoDeArbol` (de pie y sin nada construido pegado: ni el **muro** —troncos tumbados— ni los postes del pueblo), con radio 3 y **tope de 32** troncos. Y **limpia los restos que ya había**: un tronco de la **arboleda** que **no llega al suelo** por troncos (`tieneApoyo`) es un resto del hachazo viejo y lo pica. El hueco de la base se sigue apuntando para replantar como antes | El plano **no apunta los troncos de la arboleda** (es la madera del leñador, se tala y se replanta a propósito) y, al **leerlo**, el obrero tampoco los "repara": las aldeas ya guardadas se corrigen **sin migración** (igual que el portón, ver `estadoDelPlano`) |

**Verificado** (leyendo su guardado, sin tocar la partida):

- La arboleda tiene **20 troncos con apoyo** (árboles de verdad) y **5 huérfanos** exactamente: `1376,125,1392`,
  `1376,126,1393`, `1385,126,1391`, `1386,125,1391` y `1387,126,1392`. Ninguno de los árboles enteros se marca.
- Los **dos puestos** del taller están puestos y a la cota (`smithing_table` en `1418,120,1368` y `grindstone` en
  `1419,120,1368`, con aire encima y piedra debajo), así que el problema **no** era el puesto: era su **ticket**.
- El almacén tenía 18 pepitas, 16 lingotes y 16 de cuero: al herrero **no le faltaba trabajo**, le faltaba poder ir.

### 3b.38 La huerta que nadie volvía a labrar: las calvas del bancal (migración 54)

El jugador, con captura de un bancal delante: *"de esta parcela veo que hay dos espacios que no tienen cultivo y nadie
los está reparando para hacerlos cultivables"*. **Dos** calvas, ni una más, y el arreglo salió de **leer su guardado**
(aldea 2, centro `1414,1414`, cota `120`), no de mirar la captura.

| Lo que se midió en el guardado | Causa | Arreglo |
|---|---|---|
| Los tres bancales tienen **216 celdas de cultivo** (3 × 9×9 sin la fila de la acequia) y **27** de acequia. El **plano** de la aldea tenía **214** de esas 216: le faltaban exactamente **`(1384,1430)` y `(1384,1434)`**, las dos de la columna **oeste** (`dx=0`, `dz=2` y `dz=6`) del bancal de `(1384,1428)`. En el mundo las dos eran **`grass_block`** a `y=119` (la capa de la tierra de cultivo), con el resto del bancal en `farmland[moisture=7]` y sus cultivos a `y=120` | El plano de una aldea migrada es un **escaneo del mundo**, y `seDescartaDelPlano` tira la **tierra** y el **césped** por "terreno natural". La tierra de cultivo **sí** entra (ya se arregló eso en su día), pero una celda **ya pisoteada en el momento de capturar** entra como tierra o césped y se descarta: **no está en el plano** y el obrero —que repone lo que dice el plano— no tiene nada que reponer ahí. Es el mismo caso que el portón guardado abierto (I22) y los troncos de la arboleda (I24), pero al revés: aquí lo que falta es una celda que el pueblo **sí** construyó | La **huerta entra siempre en el plano**: sus celdas son **geometría fija** (`VillageGenerator.estadoDeLaHuerta`: tres rectángulos de `PLOT_WIDTH`×`PLOT_DEPTH` a `cota-1`, con la acequia en `PLOT_WATER_ROW`) y al capturar se piden **tierra de cultivo** y **agua** aunque el mundo las tenga pisoteadas, vaciadas o con el agua congelada. Así el obrero **sí** las repone (2 celdas en su aldea) |
| El **granjero** trabaja la huerta (cosecha, siembra, abona, composta), pero `buscarTierraVacia` solo mira celdas que **ya son** `farmland`: sembraba en lo que estaba labrado y **nunca volvía a labrar** una calva | Nadie tenía "labrar" en su lista de tareas: el obrero porque la celda no estaba en el plano, y el granjero porque su cadena empieza en "tierra de cultivo vacía". Vanilla convierte la tierra de cultivo en **tierra** al saltar encima (`FarmBlock.fallOn`) y, pegada al césped, la tierra vuelve a ser **césped**: en una aldea con aldeanos, animales y jugador las calvas son cuestión de tiempo | El granjero tiene la tarea **`LABRAR`** (antes de sembrar): busca celdas **de bancal** que ahora son tierra o césped, **con agua cerca** y con el hueco de arriba **libre**, va andando y las vuelve a labrar con `tierraDeCultivo` (regada como la pondría el juego). **No arranca ningún cultivo** (I11): el aire encima es requisito |
| El plano de su aldea guardaba la tierra de cultivo con **`moisture=7`** (la paleta entera: `minecraft:farmland {moisture: 7}`) | La humedad (**0..7**) la sube y la baja el **propio juego** con el agua de al lado, la sequía y la lluvia: es estado **transitorio**, como el `open` de un portón, y no "lo que la aldea debe ser". La comparación del obrero solo repone **aire** o **tierra/cesped**, así que no llegó a ser un bucle, pero dejar el estado entero en el plano es una trampa para el siguiente que toque esa lista | `estadoDelPlano` guarda la **tierra de cultivo sin humedad** (y al **reponerla** el obrero la pone **regada**, `tierraDeCultivo`); y `necesitaReparacion` dice explícitamente que **`farmland` contra `farmland` no es daño**, pase lo que pase con la humedad |

**La migración 54** vuelve a **labrar las calvas** de una aldea ya construida (`labrarCalvasDelBancal`: idempotente,
solo celdas de bancal, solo tierra o césped, solo con agua cerca y con el hueco de arriba libre) y, al recapturar el
plano al final, este ya sale con la huerta entera. En su guardado, la simulación del arreglo deja **exactamente las
mismas 2 celdas** en la lista del obrero y del granjero (`build/huerta_simula.py`), y la auditoría de la aldea 2 sigue
en **0** en sus cinco listas.

### 3b.39 El toldo del pórtico, cortado con un espacio (migración 55)

El jugador, mirando la fachada oeste de la taberna (la puerta con su pórtico): *"el pórtico está cortado con un
espacio, ¿por qué? debería estar completo"*. La geometría salió **del guardado**, celda a celda (aldea 2, centro
`1414,1414`, cota `120`, taberna en `1438,1428`), no de mirar la captura.

| Lo que se midió en el guardado | Causa | Arreglo |
|---|---|---|
| El **alero** del toldo (`bx-3`, `y=123`) tiene **5 escalones** (`dz 5..9`) y **2 faroles** en las **puntas** (`dz 4` y `dz 10`): `(1435,123,1432)` y `(1435,123,1438)` son `lantern[hanging=true]` **con aire encima**. O sea: al alero le falta **un escalón en cada punta** —justo encima de cada poste— y los dos faroles **cuelgan del aire** | El constructor del porche (`porcheDeLaTaberna`) coloca los escalones del alero de `pz-3` a `pz+3` y **después** los faroles, y los dos faroles de las puntas iban **en la misma celda** que el escalón (`bx-3`, `nivel+PISO2-2`) y lo **sustituían**. No lo arreglaba nadie porque el **plano guarda el ÚLTIMO bloque de cada celda** (`GrabadoraDePlano`): en el plano de su aldea esas dos celdas dicen `lantern[hanging=true]`, así que el obrero reponía **el farol**, no el escalón. Y encima un farol **colgado** ahí no tenía bloque encima del que colgar (I14). **No fue ninguna migración**: el porche no ha cambiado una línea desde la 44 (`git log -S porcheDeLaTaberna`), así que el hueco está **en el plano** desde que se levantó la taberna grande | El toldo lleva un **soffito de tablones** (`bx-2`, una capa por debajo de la fila de dentro) **de punta a punta** (`pz-3..pz+3`), que antes solo estaba en el centro (`dz 6..8`): es un bloque **sólido** y de él **cuelgan** los tres faroles (`colgar`), uno en cada punta —sobre los postes— y el del centro, que es la vertical de la puerta. La **fila del alero queda entera** (7 escalones, de punta a punta): una celda de esa fila es un escalón y no se ocupa con nada |
| **Y el mismo fallo, en la barraca** (barrido de la invariante I14, no lo había reportado nadie): los faroles del **dormitorio** son `lantern[hanging=false]` **con aire debajo** y el tejado de tablones justo encima — `(1369,126,1434)` y `(1369,126,1437)` en la aldea 2, y los mismos dos en la 0 (`(521,97,586)` y `(521,97,589)`) —, o sea faroles **posados** flotando en el aire, sin cadena, a un bloque del techo | El constructor de la barraca los colocaba **posados** (`Blocks.LANTERN` a secas) en la celda que va **pegada al tejado** (`nivel + BARRACA_PISO2 + 2`), donde no hay nada debajo que los sostenga: el dormitorio está al aire | La celda es **la buena** (la de debajo del tejado): lo que estaba mal era el **estado**. Ahora **cuelgan** (`colgar`), y el número que usan el constructor y el retrofit vive en una sola constante (`BARRACA_FAROL_DY`, invariante I4) |

**La migración 55** arregla las tabernas ya construidas con `VillageGenerator.arreglarPorcheDeLaTaberna`: retira los
dos faroles flotantes **solo si siguen siendo faroles** (`quitarSiEs`), cierra su celda con el escalón que le toca
(solo si quedó vacía), completa el soffito (solo donde esté vacío) y **cuelga** los dos faroles de las puntas con la
misma prueba que hace el juego (`Block.canSupportCenter`, I14). **Solo toca las celdas del porche**: no rehace la
taberna, así que no se pierde ni la despensa ni las camas. Los faroles de la barraca van en el **retrofit en el sitio**
que ya tenía esa construcción (`asegurarBarraca`, junto al del barril→cofre): mira las **tres** celdas de farol del
dormitorio y, si alguna sigue **posada** con el tejado encima, la **cuelga**; es idempotente y barato (tres
`getBlockState` por pueblo), así que se corrige solo aunque la aldea ya hubiera migrado.

**Verificado** (leyendo su guardado, sin tocar la partida, con `build/porche_simula.py` y `build/faroles_hanging.py`):

- El alero está **cortado exactamente en sus dos últimas celdas** (una por punta) y el soffito solo existe en las tres
  centrales, que es lo que hacía que el toldo se viera con un hueco.
- El **plano** de la aldea 2 dice `lantern[hanging=true]` en esas dos celdas y **no tiene nada** en las cuatro celdas
  del soffito que faltan: el hueco estaba **en el plano**, no era daño de una migración.
- La simulación del reparador cambia **10 celdas** (2 faroles fuera, 2 escalones, 4 tablones de soffito, 2 faroles
  colgados), deja el alero **entero** (7 escalones), **0** faroles sin soffito encima y es **idempotente** (una
  segunda pasada no cambia nada).
- El **barrido** de la invariante (`build/faroles_hanging.py`: la prueba del juego, con la propiedad `hanging`
  mandando) da, en la aldea 2, **4 faroles sin apoyo de 73**: los **2 del porche** y los **2 de la barraca** (los dos
  casos de esta migración). En la aldea **0** salen **18**: los mismos 4 y **14 de la cerca del corral anexo**, que son
  el retrofit **viejo** ya documentado (`posarFarolesFlotantes`, pendiente hasta que el jugador pase por esa aldea).
  Los de la barraca son **2 celdas** por pueblo (`dz -2` y `dz +1` de su columna) y una segunda pasada no cambia nada.
- Las dos auditorías del pueblo (`tools/audita_aldea.py` y `auditarFarolesFlotantes`) **no** habían cantado ninguno
  de estos cuatro: las dos daban por bueno un farol que tuviera una **valla debajo** (que es la regla del farol
  *posado*) o algo sólido **encima**, sin mirar la propiedad `hanging`. Por eso el fallo llegó hasta la captura del
  jugador. Desde la 55 la **autocomprobación del juego** (`auditarFarolesFlotantes`) mira **el lado que dice el
  `hanging`** del propio farol, así que estos dos casos ya salen en el log de la aldea (la de Python, que no lee
  propiedades de bloque para los faroles, sigue sin verlos: para eso está `build/faroles_hanging.py`).

**Lo que NO se ha podido comprobar**: el aspecto en el juego (que el toldo se vea completo desde la plaza) ni la
migración corriendo de verdad sobre su partida: el cliente estaba cerrado y el `build` no toca el guardado. Tampoco
hay nada que comprobar de jugabilidad: el porche es **decoración** (nadie camina por el alero) y su suelo no se toca;
de la barraca solo cambia el **estado** de dos faroles (misma celda, misma luz).

> **OJO — este porche tuvo un TERCER fallo (ver 3b.51)**: el alero, ya entero, seguía sin **llegar a la pared** (salía
> solo hasta `bx-2`, con una columna de aire en `bx-1` entre el toldo y el muro). Lo cerró la **migración 63**: una
> fila más de toldo pegada al muro y el soffito hasta la pared. Lo de esta sección (el alero entero y los faroles
> colgados del soffito) sigue siendo verdad, pero el soffito ya no es solo `bx-2`: cubre **`bx-2` y `bx-1`**.

> **La "enseña" de la taberna nunca existió.** El javadoc del porche (y el de la migración 44) prometía *"la enseña
> de la taberna colgada con su farol"*, pero en el código **no hay ningún cartel** en toda la aldea (`grep` de
> `SIGN` en `VillageGenerator`: cero). Se han corregido esos dos javadocs para que no lo sigan prometiendo. Queda
> **pendiente** (no lo pidió el jugador en este reporte y un cartel tiene una pega: su **texto** vive en el
> `BlockEntity`, y el plano guarda **estados** de bloque, así que un obrero que repusiera la enseña la dejaría
> **en blanco**).

### 3b.40 La escalera del desván, bloqueada por su propio techo (migración 56)

El jugador, subiendo a la taberna: *"las escaleras para el 3er piso están bloqueadas por 2 bloques, dejando solo un
espacio de un bloque libre; se tienen que romper esos 2 bloques para que se pueda pasar"*. La geometría salió **del
guardado**, celda a celda (aldea 2, centro `1414,1414`, cota `120`, taberna en `1438,1428`, `y1=125`, `yTecho=130`),
con `build/taberna_subida.py` (que aplica la regla del juego al guardado, sin jugar).

| Lo que se midió en el guardado | Causa | Arreglo |
|---|---|---|
| La **L del desván** (migración 52) sube por `dx=5` de `dz=12` a `dz=10` y luego por `dz=10` de `dx=5` a `dx=2`. Con la **regla vieja** (hueco de **2** celdas encima de cada escalón, I16) **no se sube**: el **2º** escalón (`dx=5`, `dz=11`) tiene la huella en `y=127` y su techo —los **tablones del techo de la posada**— en `(1443,129,1439)`, a **2,0**; y el **3º** (`dx=5`, `dz=10`) tiene la huella en `y=128` y su techo —la **placa de tejas**— en `(1443,130,1438)`, también a **2,0**. El **1º** (`dx=5`, `dz=12`, huella `y=126`, techo a **3,0**) **sí se subía** | El juego **no sube un escalón andando**: al chocar con la contrahuella **levanta al jugador de golpe** hasta `Entity.maxUpStep()` (0,6) y comprueba la **caja entera** ahí arriba (`Entity.collide`: `aabb.expandTowards(dx, maxUpStep, dz)`, `collectCandidateStepUpHeights` y `collideWithShapes`, que resuelve **la Y antes que la horizontal** y recorta la subida contra el techo). Hace falta **1,8 + 0,6 = 2,4** libres sobre la huella, así que el techo tiene que estar a **3** bloques (enteros) de ella: con 2,0 el que sube se queda **empujado contra la contrahuella**, con la cabeza pegada al techo, y parece que "no se puede pasar" aunque quepa de pie. La regla de I16 ("el hueco cubre lo que se sube") se había medido con el **cuerpo**, no con la **subida** | El hueco abre **3** celdas por encima de cada escalón (`DESVAN_HUECO_ALTO`): el constructor y el reparador usan **el mismo** método (`abrirElHuecoDelDesvan`), así que la geometría no se puede quedar desparejada (I4). En el guardado del jugador eso son **2 celdas** (la teja de la placa y el tablón del techo); las terceras celdas de los otros cuatro escalones **ya eran aire** (por encima de las dos capas del forjado está el desván vaciado) |

**El plano también estorbaba.** El jugador ya se había roto los dos bloques a mano, y **la teja de
`(1443,130,1438)` estaba repuesta**: el **plano** de la aldea (capturado al construir, I8) la tiene **sólida**, así
que el **obrero la repone** y la escalera se vuelve a atascar —es lo que explica que el jugador siga diciendo "se
tienen que romper esos 2 bloques"—. Por eso el arreglo tiene que pasar por la **migración**: al abrir las celdas con
`colocar` entran en el plano nuevo (el que se captura al final de la migración) y ya no vuelven.

**La migración 56** (`VillageGenerator.arreglarElHuecoDelDesvan`) ensancha el hueco de las tabernas ya construidas
**solo en sus celdas** (idempotente: solo quita `DARK_OAK_PLANKS` y `DEEPSLATE_TILES`, y solo si están ahí: ni un
farol ni nada del jugador se toca) y **no rehace la taberna**: no toca ni la despensa, ni las camas, ni los cuartos,
ni los escalones. Va después de `arreglarPorcheDeLaTaberna` (55) en la cadena de reparos.

> **Por qué la comprobación de la 52 no lo vio.** La verificación estática que se escribió entonces
> (`build/check_escalera_desvan.py`) comprobaba que *"la escalera nueva sube de `y1` a `yTecho+1` sin dejar celdas sin
> aire encima"* — o sea, que las **dos** celdas del hueco estuvieran en aire, que es justo la regla equivocada: la
> cuenta que faltaba es la del **juego** (la subida de 0,6 contra el techo). Comprobar la geometría contra la regla
> que uno mismo se ha creído no comprueba nada.

**Verificado** (leyendo su guardado, sin tocar la partida, con `build/taberna_subida.py` y `build/taberna_desvan.py`):

- Con la **regla vieja** la L **no se sube en dos escalones** (2º y 3º): en los dos, la huella tiene el techo a 2,0 y
  la subida de 0,5 no cabe (solo caben 0,20) — **exactamente los dos bloques** que el jugador rompió.
- Con la **regla nueva (3)** los **seis** escalones se suben, con la Y resuelta primero contra el techo como en el
  juego.
- El guardado **tal cual está hoy** vuelve a estar atascado en el 3º escalón (la teja repuesta por el obrero desde el
  plano), que es el síntoma que reporta el jugador.
- Nada más del recorrido estorba: el **arca** del cuarto (`dx=5`, `dz=10`, `y=125`) está **debajo** del 3º escalón
  —tres bloques por debajo de su huella— y se abre desde el oeste, así que **no se mueve**; el **muro del pozo**
  (`dx 1..2`, `dz=12`) queda **al sur del pie** de la escalera, no en su recorrido; y la **caja del comedor**
  (`dx=3`) tampoco: la L sube por `dx=5..2` en `dz=10` y su 5º escalón sustituye el bloque de **arriba del todo** de
  esa caja (`dx=3`, `y=129`), que es justo lo que ya hacía la migración 52 —el pozo sigue **tapado**, como pide I21, y
  el único agujero nuevo en el suelo del desván es el de la vertical de la escalera, que es su boca—.

**Lo que NO se ha podido comprobar**: la subida **en el juego** (hacer el recorrido con el cliente abierto) y la
migración corriendo de verdad sobre su partida: el cliente estaba cerrado y esto no toca el guardado. La regla está
leída del código del juego (`Entity.collide` de 1.21.1, en las fuentes que descarga Gradle) y cuadra con lo que el
jugador midió a mano (el 1º escalón, con el techo a 3,0, sí se subía; los dos de 2,0 no).

> **Barrido de la invariante (sin arreglar, fuera del informe del jugador).** El mismo barrido
> (`build/aldea_escaleras.py`) mira **todas** las escaleras de la aldea, y la **barraca** (`1369,1436`, la milicia)
> tiene el mismo problema por otras dos causas, las dos **medidas en el guardado**: su escalera al dormitorio tiene
> solo **3 escalones** (el 4º lo **borra** el propio constructor: para `i=3` la celda del escalón y la del hueco del
> forjado son la **misma** —`yPiso2 - 1 = nivel + 3`— y el `colocar(AIR)` del hueco se lleva el escalón que se
> acababa de poner), así que su escalón más alto tiene la huella en `123` y el suelo del dormitorio está en `124`
> (un escalón de **1,0**: solo se sube **saltando**); y una **cama** (`red_bed` en `(1372,124,1438)`, la del
> guardia) está justo encima del 2º escalón, con **2,0** de hueco sobre su huella → ese escalón **no se sube**. Va
> además con el `FACING` al **oeste** aunque sube al **norte** (como la escalera vieja de la taberna). No se toca
> aquí: el informe era de la taberna y esto pide su propia migración —mover camas (son POI, el pueblo cuenta las
> camas libres)— y decidir dónde van la escalera y las literas.

### 3b.41 El estanque de la pesquera, que se quedó sin agua (migración 57)

El jugador, mirando la caseta del pescador (etapa G): *"¿por qué la choza para pesca no tiene su estanque para
pescar?"*. En su captura se veía la caseta con su base de piedra, una **plaza de césped cuadrada** con una **viga de
madera** encima (la **pasarela**) y **dos faroles sobre poste** en las esquinas... y ni una gota de agua. La geometría
salió **del guardado**, celda a celda (aldea 2, centro `1414,1414`, cota `120`, base del lago `1434,1458`, con
`build/lago_pesquera.py` y `build/lago_repara.py`).

**Lo que hay HOY en el hueco del lago** (radio 3 más la orilla de arena, 81 columnas, cota `120`):

| Capa | Lo que el generador pone (`pesquera()`) | Lo que hay en su guardado |
|---|---|---|
| `y=119` (`cota-1`) | **49 de agua** dentro de la huella + **32 de arena** en la orilla | **46 de césped** + 32 de arena + los **3 postes** de la pasarela: **0 de agua** |
| `y=118` (`cota-2`) | **49 de agua** | **46 de tierra** + 32 de piedra (la pared del pozo) + **3 de agua** |
| `y=117` (`cota-3`) | **49 de arena** (el fondo) | 49 de arena + 32 de piedra ✔ |
| `y=120` | aire (y la pasarela de tablones) | la pasarela (4 tablones) y los 2 postes de los faroles ✔ |

Es decir: **3 celdas de agua de 49** en todo el estanque, y las tres son las **columnas de los postes de la
pasarela**. El barril del pescador (`1435,120,1453`), su caseta, la pasarela y los dos faroles estaban enteros.

**La causa (el orden).** La pesquera se construyó en la **migración 46** (`asegurarPesquera`), y **todas** las
migraciones siguientes vuelven a llamar a `farm(level, center)` (la versión de **un solo argumento**), que **nivela la
aldea entera**: `farm(level, center)` → `prepararTerreno` → `nivelar(level, center, LEVEL_RADIUS=64, cota)`. Y
`nivelar` **rellena los huecos de debajo de la cota** tratando el **agua** como "terreno que sobra"
(`esTerrenoRecortable` da el agua por terreno porque su `fluidState` no está vacío). La huella del lago (a 43-54 del
centro) cae **dentro** del disco de 64, así que el nivelado la rellenó: `groundY` de esas columnas devuelve **118** (el
techo del pozo: el agua no es sólida), y el bucle `for (y = 118; y < 120; y++)` puso **tierra en `cota-2`** y **césped
en `cota-1`**. Las tres columnas de los postes se salvaron porque `groundY` ahí devuelve **120** (la valla sí es
sólida) y el bucle de relleno quedaba vacío — que es exactamente el patrón de 3 celdas de agua que quedó en el
guardado. Y no se reparaba **solo**: `pesqueraConstruida` se conforma con el **agua o el barril**, y el barril seguía
en pie, así que `asegurarPesquera` salía por el early-return y el estanque no se volvía a llenar nunca.

**El arreglo, de raíz y en el orden.** En `nivelar` **y** en `nivelarHuella` el relleno ahora se salta el **agua** y
el **hielo** (`esAguaOHielo`, que mira el `fluidState` y la etiqueta `minecraft:ice`: el hielo de un bioma frío es la
misma agua, y un lago congelado sigue siendo un lago): **el agua no es un hueco que se rellene**, igual que no lo es
la acequia de la granja. Con eso, el lago ya no se puede volver a tapar se llame el paso como se llame y corra antes o
después. Y, para que la pesquera **se repare sola** (el latido la llama cada 10 s), `asegurarPesquera` ahora repone
sus dos piezas sueltas cuando ya está construida:

- **El agua del lago** (`repararLagoDeLaPesquera`): vuelve a poner el agua a dos capas, la orilla de arena y el fondo,
  con **la misma geometría** que `pesquera()`, **solo en las celdas del lago** y **solo donde no haya nada
  construido** (`sePuedeAnegar`: aire, agua/hielo o terreno blando; ni la pasarela, ni los postes, ni el barril, ni la
  piedra del pozo). Sale en cuanto el lago tiene agua (o hielo) en **la mitad o más** de su capa de arriba, así que
  los tres postes de la pasarela no lo dan por seco y no hace ni una escritura cuando ya está lleno.
- **El barril** (su puesto de trabajo): con el **agua** en pie, `pesqueraConstruida` daba la pesquera por hecha y un
  barril perdido no lo reponía nadie. Se devuelve a su celda **solo si está vacía**.

**La migración 57** (`VillageManager` → `repararLagoDeLaPesquera`, justo después de `asegurarPesquera` y de **todo** lo
que nivela) devuelve el agua a las aldeas que ya se quedaron secas, en la misma pasada y **sin rehacer la aldea**: en
el guardado del jugador son **92 celdas** (46 de `cota-1`, que eran césped, y 46 de `cota-2`, que eran tierra), y el
estanque queda con sus 49 celdas de agua por capa, su orilla y su fondo, con la pasarela, los faroles y el barril
intactos.

**Y el lago se queda FUERA del plano** (`esCeldaDelLago`, en las dos vías: el plano canónico de una aldea nueva y el
escaneo de una migrada). No es un descuido: `necesitaReparacion` repone el agua **en cuanto la ve congelada** —regla
que hace falta para la **acequia**, que va tapada con una losa y por eso no se congela—, así que con el lago en el
plano el obrero se pasaría la vida **descongelando** un lago que en un bioma frío **tiene que estar helado**. Su agua
la mantiene `repararLagoDeLaPesquera`, que da el lago por bueno con agua **o con hielo**.

**Efecto colateral, declarado y a propósito**: con esta regla el nivelado de una aldea **de tierra adentro** ya no
rellena el agua que caiga dentro del recinto (un charco, un arroyo): antes la tapaba con tierra y césped y la aldea
quedaba llana. Es exactamente la excepción que pidió el informe (*"el agua NO es un hueco que se rellena"*) y lo que
la acequia ya tenía; y en el guardado del jugador **no cambia ni una celda de más**: el barrido del `nivelar` viejo
contra el nuevo, en todo el recinto (radio 64) y sobre el lago ya reparado, da **92 celdas de diferencia, las 92
dentro de la huella del lago** y **ninguna fuera** (`build/lago_repara.py`).

**Lo que NO se ha podido comprobar**: pescar en el estanque **en el juego** (el pescador con su caña, que el agua no
se congele ni se salga por la orilla en su bioma) ni la migración corriendo de verdad sobre su partida (el cliente
estaba cerrado y esto no toca el guardado). Lo que sí está comprobado es el antes y el después **contra su guardado**,
celda a celda, y que el relleno nuevo no toca ni la pasarela ni el barril.

### 3b.42 La campana, al centro del kiosco, y el beacon del sello, fuera (migración 58)

El jugador: *"sitúa la campana justo en el centro del kiosco y quita el beacon pues nunca se usa"*.

**Lo que había HOY en el kiosco** (aldea 2, centro `1414,1414`, cota `120`, con `build/kiosco_dump.py`: radio 5 y de
`cota-2` a `cota+7`, bloque a bloque):

| Qué | Celda absoluta | Relativo al centro | Estado |
|---|---|---|---|
| **Campana** | `1413,121,1415` | `dx-1, dy+1, dz+1` | `bell[attachment=floor, facing=south]`: **descentrada** (una celda al oeste y una al sur), posada en la plataforma |
| **Celda central** (donde se anda dentro del kiosco) | `1414,121,1414` | `dx0, dy+1, dz0` | **aire**: libre |
| **Farol** | `1414,124,1414` | `dx0, dy+4, dz0` | `lantern[hanging=true]`, **colgado** de la celda de encima (I14 ✔) |
| **Beacon** | `1414,125,1414` | `dx0, dy+5, dz0` | `beacon`: el **centro del TEJADO** (la capa del tejado tiene sus 48 piedras + este beacon = 7×7), y es de donde **cuelga** el farol |

El kiosco tiene **0 cofres** (ni barril ni ahumador): la despensa se movió a la cocina de la taberna en la 47, como
estaba previsto. El farol estaba **bien colgado** (bloque sólido encima) y la auditoría de la aldea 2 daba **0**.

**El beacon no hacía nada**: un beacon **sin pirámide** no da efecto ni ilumina. Era solo la señal del **sello
místico**, y el sello de verdad no vive en el bloque sino en los **datos de la aldea** (`VillageSavedData.isSiegeResolved`,
que es lo que consultan la protección contra apariciones y el **haz de partículas** que sigue saliendo del kiosco). El
jugador lo mandó quitar y se quita el bloque: `marcarSelloMistico` ya **no** lo enciende y `fallVillage` ya **no** lo
apaga (y, si el de una partida vieja sigue ahí, lo cambia por la **piedra del tejado**, nunca por aire: dejarlo en aire
—como se hacía— se llevaba por delante al farol que **cuelga** de esa celda, I14).

**La campana, al centro y posada.** En el constructor (`VillageGenerator.kiosco`) la campana pasa de `(cx-1, +1, cz+1)` a
la **celda central** `(cx, nivel+1, cz)`, **posada** en la plataforma (`attachment=floor`: el apoyo va justo debajo), que
es como la coloca el propio juego. Es el **POI de reunión** del pueblo y ahí se queda **a propósito**: el pueblo se junta
en el kiosco. El farol sigue **colgado** del tejado, en la misma vertical pero cuatro bloques más arriba, así que la
celda central **es de la campana** y el barrido de campanas viejas del constructor retira cualquier otra que quedara
dentro del kiosco (si no, habría dos campanas y dos POI de reunión). Y se quita la **mesa de trabajo** que el retiro del
ahumador viejo (`asegurarCocina`) ponía **en la celda central**: era la mesa del cocinero de cuando el kiosco era la
cocina, y desde la etapa F el cocinero tiene su cocina —y su mesa— en la taberna; con la campana en el centro, esa mesa
la habría dejado sin sitio en las aldeas viejas.

**La migración 58** (`VillageGenerator.centrarLaCampanaYQuitarElBeacon`, llamada desde `VillageManager` **antes** de
tirar el plano) hace las dos cosas en las aldeas ya construidas, **celda por celda y sin rehacer el kiosco** (su testigo
es la **plataforma**, I15: rehacerlo tiraría lo de dentro):

- **La campana**: se busca en **toda la huella** del kiosco (el sitio de la campana ha cambiado de trazado más de una
  vez), se retira **solo si sigue siendo una campana** y se coloca en el centro **solo si esa celda está libre** (aire).
  Si el jugador ha puesto algo ahí, **no se toca nada** y queda en el log: mover una campana no vale tirar lo suyo.
  Se le conserva el `facing` y se fuerza `attachment=floor` (copiar un `attachment` de techo la dejaría flotando).
- **El beacon**: se retira **solo si sigue siendo un beacon** y su celda se repone con la **piedra del tejado**.

Es **idempotente** (si ya está todo bien no escribe ni una celda) y va **antes** de tirar el plano, para que el plano
nuevo se capture **con la campana centrada y sin el beacon** (I8: si el beacon siguiera en el plano, el obrero lo
repondría en cuanto alguien tocara ese hueco). En el guardado del jugador son **2 celdas de escritura**: la campana
(`1413,121,1415` → `1414,121,1414`, con su celda vieja a aire) y el beacon (`1414,125,1414` → piedra labrada).

**Lo que NO se ha podido comprobar (sin jugar)**: que el juego acepte la campana en su celda nueva (el `canSurvive` del
`BellBlock` con apoyo de piedra debajo es el de vanilla, pero no se ha visto tañer), ni la migración corriendo sobre la
partida (el cliente estaba cerrado y esto **no toca el guardado**: lo arregla la migración al cargar). Lo comprobado es
el antes **contra su guardado**, celda a celda, y que el código nuevo pasa el lint y la auditoría (aldea 2: 0).

### 3b.43 La escalera de la barraca, que no se subía (migración 59)

El jugador reportó *"las escaleras para el 3er piso están bloqueadas"* (era la taberna: migración 56, I26). Al ir a
mirar **el otro edificio con escalera** —la **barraca de la milicia**, la que tiene las **8 camas** del dormitorio
arriba— resultó estar **peor**, y por tres motivos distintos a la vez.

**Lo que había HOY en su barraca** (aldea 2, base `1369,1436`, cota `120`: forjado `123`, dormitorio `124`, tejado
`127`; medido con `build/barraca_dump.py`, que vuelca la barraca entera capa a capa):

| Qué | Celda | Estado |
|---|---|---|
| **Escalones** | `1372,120,1439` · `1372,121,1438` · `1372,122,1437` | **TRES** (de cuatro), los tres `oak_stairs[facing=west, half=bottom]` |
| **El 4º escalón** | `1372,123,1436` | **aire**: el constructor lo colocaba y **acto seguido** abría ahí el **hueco del forjado** (la celda del escalón y la del hueco eran **la misma**: `yPiso2 - 1 = nivel + 3`) → la escalera se acababa a **1,0** del suelo del dormitorio (`124 - 123`) |
| **El 2º escalón** (huella `y=122`) | cama en `1372,124,1438` | **2,0** de hueco: el juego pide **2,4** (I26) → **no se sube**; son los **pies de la cama del rincón sureste** |
| **El arca del este** | `1372,124,1436` | en la celda **del último escalón**: el que subía se la encontraba **de frente**, a la altura de los pies |
| **Los huecos del forjado** | `dx=+3`, `dz=+3,+2,+1,+0` | **cuatro**, una de ellas la del escalón |
| **El `FACING`** | — | **oeste** subiendo al **norte** (la cara alta tiene que mirar **hacia donde se sube**) |
| **La entrada** | `1372,120,1440` | el pie estaba **pegado al muro sur** y mirando al oeste: la celda por la que hay que entrar al primer escalón caía **dentro de la pared** → a la escalera **no se podía ni entrar** |

**Lo que faltaba era una segunda regla, y no es de altura** (I26 dice *cuánto* hueco hace falta; esto dice *dónde va
cada pieza*):

1. **A un escalón se entra por su lado BAJO**, el **contrario** a la cara alta que marca el `FACING`. El que sube
   necesita poder ponerse en la celda de al lado (suelo firme y las dos celdas de su cuerpo libres) y subir el primer
   medio bloque (`0,5 ≤ maxUpStep 0,6`). El lado bajo de un escalón mide **0,5** y el cuerpo del jugador **0,6**: por
   eso **no se puede entrar de lado** (la caja siempre toca la parte alta, de `1,0`, y no sube) y por eso el **pie** va
   **una celda separado del muro**, no pegado a él.
2. **La celda del ÚLTIMO escalón no puede ser la del hueco del forjado.** El hueco va **en la capa del forjado**,
   encima de los escalones que pasan **por debajo** de él (los `ESCALONES - 1` primeros, que son las tres celdas de
   I26); el último escalón **vive** en esa capa, así que su celda es suya. Y la **cara alta del último** queda a la
   **altura del suelo del dormitorio**: de ahí **se sale andando**, sin saltar.

**El arreglo** (`VillageGenerator`: constantes `BARRACA_ESCALERA_*`/`BARRACA_CAMAS_*` y `barraca(...)`): la escalera
son **cuatro** escalones de medio bloque (uno por bloque que sube el piso) pegados al muro **este**, subiendo **al
norte** (`FACING` = norte) y con el pie **dos** celdas al norte del muro sur (sitio para entrar). El **hueco** son las
**tres** celdas del forjado encima de los escalones que van por debajo, y la **cama** del rincón sureste se corre
**una celda al oeste** —su celda de los pies era justo la del hueco—: sigue habiendo **8 camas** (en vanilla cada cría
pide una cama libre y la cama es un **POI**). El **arca del este** se pasa al lado de la del oeste (cofre doble):
estaba sobre el último escalón.

**La migración 59** (`VillageGenerator.arreglarLaEscaleraDeLaBarraca`, llamada desde `VillageManager` **antes** de
tirar el plano) hace lo mismo en las barracas ya construidas, **celda por celda y sin rehacer la barraca** (rehacerla
tiraría las camas y lo de dentro de las arcas). Es **idempotente** y **no toca lo que puso el jugador**: para quitar,
solo el bloque esperado (`quitarSiEs`); para poner, solo en celda vacía (`colocarSiEstaVacio`). En su guardado son
**14 celdas**: 3 escalones viejos fuera, 4 escalones nuevos, 3 huecos (ya estaban) + el tablón que sobraba repuesto, la
cama vieja fuera y la nueva puesta, el arca vieja fuera y la nueva puesta (`1366,124,1437`, junto a la del oeste).
**Lo de dentro del arca no se pierde**: se pasa a la nueva y, si no hubiera dónde (el jugador ocupó la celda), al
**almacén del pueblo** —nunca al suelo—; la vieja solo se retira cuando está vacía (I6).

**Comprobado con `build/barraca_subida.py`** (la regla real del `maxUpStep` de `Entity.collide`, escalón por escalón,
**antes y después** de simular el reparador celda a celda) sobre las **tres** aldeas del guardado que tienen barraca
(0, 1 y 2):

| | Entrada | Escalones | Salida | Camas |
|---|---|---|---|---|
| **Antes** | **no** (la celda de entrada es la pared) | el **2º** no se sube (2,0 de hueco) y el 4º **no existe** | **no** (1,0 de desnivel) | 8 |
| **Después** | **sí** (0,5) | **los 4** (6, 5, 4 y **3** celdas libres sobre su huella) | **sí** (desnivel **0,00**, saliendo al oeste) | **8** |

**Lo que NO se ha podido comprobar (sin jugar)**: subirla **en el juego** (que el jugador entre andando por el lado
bajo y salga al dormitorio, y que los **guardias** la usen para ir a dormir: los aldeanos suben escalones igual que
el jugador, pero no se ha visto) ni la migración corriendo sobre la partida (el cliente estaba cerrado y esto **no
toca el guardado**: lo arregla la migración al cargar). La aldea **1 está caída** y la migración no corre en una
aldea caída (como ninguna otra migración): su barraca tiene el mismo fallo medido y se quedará como está.

### 3b.44 La mesa de cartografía de la barraca, fuera (migración 60)

El constructor de la barraca ponía una **mesa de cartografía** (`cartography_table`) en `(bx+2, nivel, bz+2)`, y esa
celda **no era suya**: es la **paca** (`hay_block`) del **segundo maniquí** de entrenamiento, que el constructor
coloca **antes** —la mesa se la **comía**—, así que el maniquí se quedaba **sin base** (con la calabaza y las dos
vallas en pie y el suelo de piedra debajo). Y encima es el **puesto de trabajo del CARTOGRAFO**, un oficio que este
pueblo **no** tiene: un aldeano **sin oficio** (una cría que crece) la reclamaría y se volvería cartógrafo. El
jugador, al verlo: *"sí, quítalo"*.

**Lo que había HOY** (aldea 2, barraca en `1369,1436`, cota `120`; `build/barraca_dump.py`):

| Celda | Qué había |
|---|---|
| **`1371,120,1438`** | **`cartography_table`** (el puesto del cartógrafo) — debajo, el **suelo de piedra** de la barraca (`1371,119,1438`), que es la capa `cota-1` |
| **`1371,121,1438`** | `carved_pumpkin` (la cabeza del maniquí), **en pie** sobre la mesa |
| **`1371,122,1438`** | `oak_fence` (el palo de arriba del maniquí) |
| **`1371,120,1439`** | `oak_fence` (el travesaño del costado), **en pie** |
| La **paca** | **no estaba**: era la celda de la mesa (el otro maniquí, `1367,120,1434`, la tiene) |

El **plano** guardaba la mesa (`1371,120,1438` → `cartography_table`), así que el obrero la reponía y el maniquí no
se arreglaba solo.

**El arreglo** (`VillageGenerator`): se **quita** la mesa del constructor y la celda se queda con lo que le toca, la
**paca del maniquí**, que ahora ya no la sustituye nadie. Las celdas de los dos maniquíes pasan a ser constantes
(`BARRACA_MANIQUIES` / `BARRACA_MANIQUI_SURESTE`, I4) porque las usan el constructor **y** la migración.

**La migración 60** (`VillageGenerator.quitarLaMesaDeLaBarraca`, llamada desde `VillageManager` **antes** de tirar el
plano) hace lo mismo en las barracas ya construidas: **si en esa celda sigue habiendo una mesa de cartografía**, la
cambia por la **paca** (`sustituirSiEs`, una sola escritura: la misma guardia que `quitarSiEs`, sin el aire de en
medio). Es **idempotente** (si ya es la paca, o si el jugador puso otra cosa, no escribe ni una celda) y **no rehace
la barraca** (su testigo es el **hogar** del patio, I15: rehacerla tiraría las camas y lo de dentro de las arcas).
Va antes de tirar el plano para que el plano nuevo se capture con la paca y **sin** la mesa (I8).

**Barrido de puestos de trabajo de aldeano de la aldea entera** (bloque a bloque en las tres aldeas del guardado:
`build/barraca_mesa.py`). El **único** que no correspondía a un oficio del pueblo era esa mesa; los demás son
**deliberados** y **no** se han tocado:

| Puesto | Aldea 2 (centro `1414,1414`) | De quién es |
|---|---|---|
| **Mesa de cartografía** | `1371,120,1438` (barraca) | **CARTÓGRAFO: no existe en el pueblo → QUITADO** (migración 60) |
| Campana | `1413,121,1415` (kiosco) | POI de **reunión** del pueblo, a propósito (I28; la migración 58 la centra) |
| Soporte de pociones | `1397,121,1371` (iglesia) | **CLERIGO** (oficio del pueblo; viene en la plantilla `plains_temple_4`) |
| Composteros ×3 | `1382,120,1428` · `1384,120,1448` · `1422,120,1418` | **GRANJERO** (uno por bancal) |
| Muela + mesa de herrería | `1419,120,1368` · `1418,120,1368` (herrería) | **HERRERO DE ARMAS** y **DE HERRAMIENTAS** |
| Telar | `1471,120,1415` (corral anexo) | **PASTOR** (el ganadero) |
| **Ahumador** | `1442,120,1430` (cocina de la taberna) | **CARNICERO** (el cocinero) |
| Barril | `1435,120,1453` (pesquera) | **PESCADOR** |

Las **otras dos** aldeas del guardado tienen los mismos, cada uno en la suya y con la **misma mesa de cartografía**
en su barraca: aldea 0 (centro `566,566`, cota `91`) campana `565,92,567`, soporte de pociones `549,92,523`,
composteros `574,91,570` · `534,91,580` · `536,91,600`, muela `571,91,520`, mesa de herrería `570,91,520`, telar
`621,91,567`, ahumador `594,91,582` y **la mesa** en `523,91,590` (y todavía **sin** pesquera: su trazado es el 44);
aldea 1 (caída, centro `990,990`, cota `96`) los mismos, con barril `1011,96,1029` y **sin** telar, y **la mesa** en
`947,96,1014`. El barrido es de ±110 bloques y de `cota-25` a `cota+35`, así que cubre la aldea entera (la valla
está a **62**) y sus anexos. **No** hay ni un caldero, atril, cortapiedras, alto horno ni mesa de flechas en ninguna.

**Y un defecto que se ha visto de paso, SIN tocar** (no era el encargo y arreglarlo es una decisión de trazado): la
**primera diana** del constructor (`bx - r + 2`, `bz + r - 2` = `1367,120,1438`) cae en **la misma celda** que el
**arca** de la pared oeste (que se coloca después), así que el arca **se come la diana**: en el plano de la aldea 2
solo hay **2** `target` (`1371,120,1434` y `1371,121,1434`), no tres. Se deja como está y se avisa al jugador.

**Comprobado con `build/barraca_mesa_repara.py`** (antes/después celda a celda, y la idempotencia) sobre las **tres**
aldeas del guardado que tienen barraca:

| | Celda `+2,+2` (hoy) | Lo que deja el reparador | Maniquí sureste | 2ª pasada | Puestos de trabajo en la barraca |
|---|---|---|---|---|---|
| Aldeas **0, 1 y 2** | `cartography_table` | `hay_block` | **completo** (paca + calabaza + 2 vallas) | **0 celdas** | **ninguno** |

(Ojo: en la **aldea 1** la simulación da el mismo resultado, pero **no correrá**: está caída.)

**Lo que NO se ha podido comprobar (sin jugar)**: que la migración corra sobre la partida (el cliente estaba cerrado
y esto **no toca el guardado**: lo arregla la migración al cargar; la aldea 2 del guardado está en el trazado **55**,
así que al cargar corren **de una pasada** las migraciones **56 a 60**, la escalera de la barraca incluida) ni que un
aldeano sin oficio reclamara la mesa de verdad (el razonamiento es el del juego —`cartography_table` es POI de
`CARTOGRAPHER`— y el mismo que ya llevó a no usar `BARREL` en las pipas de la taberna). La aldea **1 está caída**
(trazado 46) y la migración **no corre** en una aldea caída: su barraca tiene la misma mesa medida y se quedará como
está (como su escalera, migración 59).

### 3b.45 La tercera diana de la barraca, que se comía el arca (migración 61)

Quedó **apuntado** al cerrar la ronda anterior (3b.44, el mismo constructor y la misma barraca): *"la **primera diana**
del constructor (`bx - r + 2`, `bz + r - 2` = `1367,120,1438`) cae en **la misma celda** que el **arca** de la pared
oeste (que se coloca después), así que el arca **se come la diana**: en el plano de la aldea 2 solo hay **2** `target`,
no tres. Se deja como está y se avisa al jugador."* Esta ronda lo arregla.

**El mismo patrón que la mesa** (I31, migración 60): dos piezas del **mismo** constructor caen en **una celda** y gana
la que se coloca **después**. La diana suelta iba en el **cuadrante suroeste** (`rel -2,+2`), que es la celda del
**arca de la sala de armas** (`bx-2, nivel, bz+r-2`: la que fue un **barril** y pasó a **cofre**), y el arca se coloca
**cuatro líneas más abajo**. Resultado medido en el guardado del jugador (**aldea 2**, barraca `1369,1436`, cota `120`,
con `build/barraca_dump.py` y `build/barraca_diana.py`): **2** `target` en el mundo (`1371,120,1434` y
`1371,121,1434`, la **doble** del rincón noreste) y **2** en el **plano** —el obrero reponía esa misma foto, así que
tampoco se arreglaba solo—. Lo mismo en las **tres** aldeas del guardado (0, 1 y 2: **2** dianas en el mundo y **2** en
el plano en cada una).

| | Celda | Detalle |
|---|---|---|
| **La diana suelta, ANTES** | `rel -2,+2` = `1367,120,1438` | el constructor la colocaba y el **arca** (`chest[facing=north]`, que va después) la **sustituía**: celda del **maniquí suroeste** —el que se quedó **sin paca** por la mesa, migración 60— |
| **La diana suelta, AHORA** | `rel -3,+3` = `1366,120,1439` | el **rincón suroeste**, **pegada a las dos paredes** |
| **La diana doble** | `rel +2,-2` = `1371,120,1434` y `1371,121,1434` | **dos** bloques apilados, en el rincón **noreste**: no se tocan |

**Por qué esa celda** (la elige el constructor y la comprueba `build/barraca_diana.py`): está **libre** (aire) en la
capa que se pisa de las **tres** aldeas —en la sala de armas quedan **41** celdas libres de las 49 del interior y **21**
de ellas pegadas a una pared, así que había donde elegir— y tiene lo que pide una diana: es una de las **cuatro
esquinas** del cuarto, que son las **únicas** celdas que tocan **dos paredes** (tope detrás para la flecha) y las **más
lejos de la puerta** (`7,62` bloques, frente a los `2,83` de la diana doble: más recorrido para el arco) —la esquina
**este** está a la misma distancia pero es la **celda de entrada de la escalera**, así que queda la **oeste**—; **se ve
al entrar** por la puerta norte (está en la diagonal delante-derecha, a **23,2°** del eje de la mirada desde la puerta,
frente a los **45°** de la diana doble); **no** tiene
ninguna **tronera** enfrente (las de la pared oeste están en `rel -4,-2` y `rel -4,+1`, y las del muro sur en
`rel -1,+4` y `rel +2,+4`); **no tapa el paso** ni a la **escalera** (que sube por la columna **este**) ni al **hogar**
(que está en el centro del muro sur, `rel 0,+3`: su celda de encima **no** se toca); ninguna de las **tres** dianas queda
**tapada** (todas tienen libre la celda de encima); y **no es puesto de trabajo** de nadie (un `TARGET` **no** es un POI
de aldeano: I31 no aplica, y el barrido de puestos de la barraca sigue dando **ninguno**). La **celda vieja no se
toca**: es la del **arca**, que es lo que le toca.

**El arreglo** (`VillageGenerator`): la diana suelta pasa a una **constante** (`BARRACA_DIANA`) y la doble a otra
(`BARRACA_DIANA_DOBLE`) —I4: son geometría fija de la sala de armas, y las **tres** celdas las comprueba el
diagnóstico contra el guardado—, y el constructor la coloca ahí. Siguen siendo **tres** dianas: **tres bloques** en
**tres celdas distintas**.

**La migración 61** (`VillageGenerator.moverLaDianaDeLaBarraca`, llamada desde `VillageManager` **antes** de tirar el
plano): pone la diana que falta en su celda nueva **solo si esa celda está vacía** (`colocarSiEstaVacio`). Es
**idempotente** (con la diana puesta no escribe ni una celda: la segunda pasada da **0**), **no rehace la barraca** (su
testigo es el **hogar** del patio, I15: rehacerla tiraría las camas y lo de dentro de las arcas) y va antes de tirar el
plano para que el plano nuevo se capture ya con las **tres** (I8).

**Y el ORDEN, comprobado de verdad** (`build/barraca_diana.py`, sección 0): el script **transcribe el constructor**
celda a celda y en orden y **canta cualquier celda escrita dos veces con bloques distintos** (el patrón "lo que va
después gana"), que es lo que pedía el encargo: *¿hay alguna otra pieza de la barraca que se pise?*

| | Celdas pisadas **sin justificar** | Dianas en pie |
|---|---|---|
| **Con la celda vieja** (como estaba) | **1**: `rel -2,0,+2` `target` → `chest` | **2** |
| **Con la celda nueva** (esto) | **0** | **3** |

Las **cuatro** celdas que se pisan y **no** son un fallo quedan documentadas en el propio script: la **puerta** sobre
el muro norte (`rel 0,0,-4` y `rel 0,1,-4`), el **hogar** en la capa del suelo (`rel 0,-1,+3`, I15), el **último
escalón** en la capa del forjado (`rel +3,+3,-1`, I30) y los **cuatro postes de las esquinas** en la capa del forjado
(`rel ±4,+3,±4` `oak_planks` → `oak_log`): el poste sube **entero** de una pieza hasta el tejado y su celda es suya en
todas las capas (es lo que hay medido en el guardado). **No hay más**: ninguna otra pieza de la barraca se come a otra.

**Comprobado con `build/barraca_diana.py`** sobre las **tres** aldeas del guardado que tienen barraca (0, 1 y 2):

| | Celda nueva libre | Celda vieja | Dianas después | Repetidas | Puestos de trabajo |
|---|---|---|---|---|---|
| Aldeas **0, 1 y 2** | **sí** (aire en la capa que se pisa) | `chest` (el arca) | **3** (las tres esperadas) | **no** | **ninguno** |

**Lo que NO se ha podido comprobar (sin jugar)**: que la migración corra sobre la partida (el cliente estaba cerrado y
esto **no toca el guardado**: lo arregla la migración al cargar; las tres aldeas del guardado están en los trazados
**44**, **46** y **55**, así que al cargar la **2** corre **de una pasada** las migraciones **56 a 61** —y la **0**, en
el 44, arrastra además las 45 a 55—) ni **cómo se ve** la diana desde la puerta (el ángulo y que no estorbe están
calculados sobre las capas del guardado, pero eso se comprueba andando por la barraca). La aldea **1 está caída**
(trazado 46) y la migración **no corre** en una aldea caída: ahí la diana se quedará donde está (como su escalera,
migración 59, y su mesa, migración 60).

### 3b.46 La guardia que giraba sobre sí misma (el puesto del corral, al otro lado de la valla)

Era el **pendiente** con el que cerró la ronda anterior (commit `0c99788`): el jugador reportó *"Bibiana está dando
vueltas sobre sí misma de manera errática y no está haciendo lo que debe"* (etiqueta **"Patrullando el corral"**) y
aquel arreglo solo cubrió a la **arquera** (a la que se le mandaba caminar **a su propia celda**). La captura era de
una **espadachín**, y su pelea (`pelearConEspada`) no tenía ese fallo: quedó apuntado *"su causa puede ser otra (el
destino de patrulla o algún goal que la zarandea)"*, sin cerrar (el diagnóstico se quedó sin hacer).

**Medido ahora, sin jugar**, con el **arnés** (`tools/arnes/GuardHarness.java`, ver `tools/arnes/LEEME.md`): servidor
headless, una **copia** de la partida en `run/world`, chunks de la aldea forzados, un **jugador de pega** en la plaza
(el latido del pueblo necesita jugador cerca: los goals **no** se guardan con la partida) y el latido **de verdad**
(`VillageManager.manageNearby`). De día, sin ciclo y **sin spawn de bichos** (el combate va antes que la ronda y los
guardias se morían peleando), la guardia espadachín del **puesto 0** (Bibiana, `9036d1d0`):

- **El puesto del corral era DENTRO del cercado** (`puntoDeApoyoAnexo`, a **3,0** del portón) y **no llegaba**:
  recorrido medido `(1474,1408) → (1469,1404) → (1462,1404) → (1454,1404) → (1454,1412)` y ahí se quedaba **clavada
  empujando la valla oeste**, a **4,03** del puesto, con `mejor = 4,08` que **no bajaba** en **dos rondas seguidas de
  200 ticks**. El portón solo se abre cuando el aldeano **va a cruzarlo** (y no con un animal en el hueco), así que la
  navegación ni lo intentaba; y cuando el portón se abría de casualidad y **entraba**, se plantaba **en el hueco**.
- **El goal se rendía y volvía a empezar con el MISMO puesto**: en el log, `STOP destino=(1458,120,1412) paso=3
  stuck=200` → `START destino=(1458,120,1412) paso=3` → la misma valla → y otra vez, **en bucle**: la ronda **nunca**
  avanzaba. Es lo que el jugador veía como "dando vueltas de manera errática" (el aldeano empujando, girando y
  volviendo a empezar cada 10 s) con la etiqueta del puesto **clavada**, y por eso "no está haciendo lo que debe".
- La otra espadachín del guardado (`e9c274fb`, la del 10:30) estaba **dentro del bloque del portón** con el portón
  **cerrado** (`open:false`): la red de seguridad se lo había cerrado **encima**.

**El barrido de la ronda** (mismo arnés, 3 rondas de río): el puesto de la **arboleda** funciona (llega, se planta
120 ticks —`espera` subiendo— y sigue), y el de la **ronda general** también; el que fallaba era **solo el del
corral**, porque es el único que caía **al otro lado de una reja**.

**El arreglo** (tres piezas, y la cuarta de propina):

1. **El puesto del corral, FUERA del cercado** (`VillagerGuardGoal.puestoDelCorral` + `PUNTOS_DEL_CORRAL`): dos
   bloques al **oeste** de la valla (`base − ANEXO_RADIO − 2` = `1453,120,1408/1410/1412/1416/1418/1420`), repartido
   por guardia y **sin pisar la fila del portón** (es la **única puerta del rebaño**: un guardia plantado en el hueco
   lo deja abierto o se queda dentro). El guardia ve (y defiende) el corral igual desde fuera: `RADIO_COMBATE` = 16
   cubre el cercado entero desde la valla oeste. Y **nada de navegar a un bloque sólido** (I32): el puesto se corre a
   la primera celda libre de al lado (`puestoLibre`), **nunca** hacia el cercado.
2. **Rendirse es SALTAR el puesto** (I33): `canContinueToUse` hace `paso++` y lo dice en el log
   (`"no llego a ... me salto el puesto y sigo la ronda"`) en vez de volver a empezar con el mismo destino
   determinista.
3. **Con un aldeano DENTRO del hueco del portón no se cierra** (I34, `VillagerGateGoal.alguienEnElHuecoDeVerdad`, a
   `HUECO` = 1,5 y no a `ABRIR` = 2,6): el plazo de 5 s sigue valiendo para el que solo trabaja **al lado**.
4. **De propina, el número del pasillo de la ronda general**: cuando un punto de la ronda caía dentro del anexo se
   corría a `FENCE_RADIUS − 3` = centro + **59**, que es **justo la valla ESTE del corral** (`base + ANEXO_RADIO`), o
   sea otro bloque sólido al que navegar; el pasillo de dentro del muro son las celdas **60 y 61** (`FENCE_RADIUS −
   2`).

**Y una cosa que se probó y se dejó como estaba** (queda medido para no repetir el intento): quitarle al guardia el
**puesto de trabajo** (`JOB_SITE`) y el oficio para que su cerebro no mantuviera la actividad de **trabajar** sale
caro —`VillagerProfession.NONE` tiene por predicado de puesto adquirible **`ALL_ACQUIRABLE_JOBS`**, así que el aldeano
se pone a **buscar estación** entre las 48 casillas de alrededor, la reclama y **vuelve a tener oficio** (medido: el
guardia recuperaba su composter en cada latido, y de paso le podía quitar el puesto a un oficio del pueblo)—. El
guardia conserva su compuesto (es un **segundo** granjero: el reparto de puestos ya cubrió al titular) y su cerebro
tira de él solo en los huecos entre rondas, que con el puesto de ronda arreglado son raros: con el goal del guardia
corriendo, el destino que manda es el **suyo** (el cerebro escribe *después* del goal pero `MoveToTargetSink` ya ha
consumido el del goal, y al plantarse el goal **borra** `WALK_TARGET` **y** `LOOK_TARGET` en cada tick).

**Verificado con el arnés** (aldea 2, de día, sin bichos, **31 minutos de juego**, 942 muestras de la guardia del
puesto 0 y 896 de la del 1):

| | Antes | Después |
|---|---|---|
| Puesto del corral | **dentro** del cercado, a 4,03 y sin llegar (`mejor` clavado en 4,08) | **fuera** (`1453,120,1408/1410`): **17 y 15 muestras plantada en él**, a 1,0 del puesto |
| Ronda | **clavada** en `paso 3` (STOP/START con el mismo destino, en bucle) | **avanza**: etiquetas "Patrullando el corral" (305), "la aldea" (288) y "la arboleda" (303) |
| Atascos | `stuck=200` cada 10 s | **ningún** `no llego a` y **ningún** `deja el puesto` en 31 min |
| Dentro del cercado | entraba de casualidad y se quedaba encerrada | **0 muestras dentro** (ni un guardia cruza el portón) |

**Lo que NO se ha podido comprobar (sin jugar)**: cómo se ve en la partida del jugador (hay que **reiniciar el
cliente** para cargar el mod) —el arnés corre en un servidor headless con la partida **copiada**, así que mide
decisiones, no pinta nada—. El **reparto de la milicia** que salió en el arnés (**4** espadachines) resultó ser OTRO
fallo y se arregla en **3b.47**. No hace falta **migración**: son reglas de goals (no se toca el mundo ni el guardado
de los aldeanos).

### 3b.47 La milicia se llevaba al PESCADOR (y al segundo granjero): el cupo de puestos, contado

Salió al revisar la ronda anterior: en el arnés se alistaban **4 espadachines** en la aldea 2 y el jugador preguntó
*"¿no puede haber dos pescadores en la aldea? ¿por qué sucede esto?"*. Las dos cosas son ciertas y están
relacionadas:

- **El pueblo tiene NUEVE puestos** (`VillageGenerator.VILLAGER_SPECIALTIES`): **dos granjeros**, herrero de armas,
  clérigo, herrero de herramientas, recolector, ganadero, cocinero y **pescador**. **Un** puesto de pescador: dos
  pescadores **no** son diseño.
- **El cupo del reparto de la milicia estaba escrito a mano** (`VillageManager.repartirGuardia`) y se quedó con
  **SIETE** puestos: los de la etapa E, **sin el segundo granjero** (etapa F) **ni el pescador** (etapa G). Para ese
  reparto, esos dos oficios eran *siempre* "gente de sobra" → **la milicia se los llevaba**. Medido en el guardado del
  jugador (aldea 2, `build/milicia_cupo.py`, que aplica el reparto tal cual a los aldeanos del guardado): con el
  cupo viejo los sobrantes eran **tres** —los **dos pescadores** y el **segundo granjero** (la guardia Bibiana,
  `9036d1d0`)— y con el nuevo, **uno** (el pescador que sobra). Y el daño real: el **compostero** del segundo
  granjero seguía **cogido** (`free_tickets=0` en `1422,120,1418`) mientras su dueña patrullaba, así que **un bancal
  se quedaba sin quien lo trabajara**, y **dos pescadores** dejaban la pesquera por la ronda.
- **Por qué hay dos pescadores en su aldea** (que es lo que preguntó): el oficio lo da (a) el reparto del pueblo, (b)
  **el bloque** —el **barril** es el puesto del pescador: quien lo reclama se vuelve pescador, aunque el pueblo ya
  tenga el suyo— y (c) **un aldeano curado**: en su guardado, el segundo pescador (`295c0896`, con la etiqueta
  *"Dionisio (Guardia espadachín)"*) **no tiene ni un dato del mod** (ni `DevilRpgGuardia`, ni memorias: el arnés y
  `build/pescadores.py` lo enseñan así) — es el **cuerpo curado de un guardia anterior**: al curar un aldeano zombi el
  juego crea un aldeano **nuevo**, copia su `VillagerData` (→ el oficio viejo) y su nombre, pero **no** los datos del
  mod. En su log está el `ZombieVillager['Dionisio (Guardia espadachín)']` correspondiente. La aldea, al quedarse sin
  pescador vivo, le dio el puesto al aldeano sin oficio (`reponerProfesiones`), y al volver el curado **quedaron
  dos**.

**El arreglo**: el cupo **se cuenta** de los puestos del pueblo (`VillageGenerator.puestosPorOficio()`, nuevo) en vez
de escribirse a mano en el gestor, así que **no puede volver a quedarse atrás** cuando se añada un oficio (es la
misma regla de I5: la medida vive en un solo sitio). Efecto en su aldea, verificado:

| | Cupo viejo (7) | Cupo nuevo (9) |
|---|---|---|
| Puestos sin cubrir | ninguno de los 7 (y el 8º y el 9º no existían para el reparto) | ninguno |
| Gente de sobra (milicia) | **3**: los dos pescadores + el **2º granjero** | **1**: el pescador que sobra |

**Verificado**: offline con `build/milicia_cupo.py` (el reparto tal cual, sobre los aldeanos del guardado) y **en
vivo con el arnés**: en el log sale `[Village] 9036d1d0 ... deja la guardia y vuelve a su oficio` (el 2º granjero
**sale** de la milicia) y `[Village] Aldea 2: comida 64 puntos, **9 aldeanos**` → con los 9 puestos cubiertos la
milicia queda **vacía** (que es el diseño: *"una aldea sana no tiene guardia: hacen falta crías"*). Compila, lint OK.

*(Nota del arnés: los bichos que ya venían en el guardado **dentro del recinto** bloquean `hayEnemigosDentro` y con
ello **todo** el latido —sin reparto de oficios ni milicia—; el arnés los barre cada segundo. Se nota porque en el
log **no** sale ninguna línea `[Village] Aldea N: comida ...`.)*

### 3b.48 Los oficios de la aldea, rediseñados: 3er granjero, LEÑADOR propio y una profesión por estación

Lo pidió el jugador al revisar la ronda anterior, y traía cuatro encargos: *(1)* **"necesitamos un 3er granjero que vaya
a la granja que está vacía porque la comida que se produce actualmente no es suficiente para alimentar a los
pobladores"* y que los granjeros cosechen más; *(2)* **"es necesario que haya un aldeano que se especialice únicamente
en cortar madera y plantar árboles, para dejar totalmente libre al recolector para que recoja y transporte"**;
*(3)* **"esos aldeanos que nacen deben tener lo necesario para integrarse al sistema"** y **"la milicia se va a ir
llenando conforme vayan naciendo y alcanzando la adultez aldeanos"**; y *(4)* **"eliminar que haya una duplicidad de
profesiones (una profesión por estación permitida y administrada por el sistema de aldea)"**.

**El mapa, antes de tocar nada** (auditoría de roles → goals, con el fuente vanilla delante): los `Goal`s del pueblo
piden **MOVE+LOOK** (salvo el de los portones, que no pide banderas), así que **se excluyen entre sí**, y en un empate
de prioridad gana **el que se engancha antes** (`GoalSelector`/`WrappedGoal`: una prioridad igual no desplaza al que
está corriendo). De ahí salían **cinco conflictos reales**, todos arreglados aquí:

| | Qué pasaba | Arreglo |
|---|---|---|
| **C1** | El leñador era un **segundo goal del recolector** (NITWIT) a prioridad **6**, la **misma que la taberna**: el aldeano con hambre y leña pendiente **no iba a comer** | El leñador es un **oficio propio** a prioridad **4** (su faena), y la taberna se queda sola en la 6 |
| **C2** | La guardia **conservaba** el goal de recoger (prioridad 3, enganchado **antes**) ⇒ el guardia **barría el término del pueblo y bajaba al almacén antes que patrullar** | Al alistarse se le **quita** la recogida, como ya se le quitaba la reparación ("un guardia tiene su puesto") |
| **C3** | `marcarObrero` ponía la reparación a prioridad 5 solo al **granjero y los dos herreros**: un **pescador obrero** (o cualquier oficio nuevo) **reparaba en vez de pescar** (I23 a medias) | Prioridad **5 para CUALQUIER oficio del pueblo** (`esOficioDelPueblo`), y **3** para el que no tiene faena |
| **C4** | El **clérigo** (sin goal de oficio) como obrero: recoger (3) y reparar (3) empataban y **recogía antes de reparar** | Al obrero **sin faena se le quita la recogida**: repara, que es lo suyo mientras es obrero |
| **C5/7** | El "**tercer granjero**" que aparecía solo (el compostero libre del 3.er bancal) se lo llevaba la milicia y **su bancal se quedaba sin nadie** (el daño de I35) | El 3.er bancal tiene **su plaza de granjero** (abajo): deja de ser un duplicado accidental y pasa a ser el titular |

**Los once puestos** (`VILLAGER_SPOTS` + `VILLAGER_SPECIALTIES`, **mismo orden y misma longitud**: `spawnOneVillager`
cruza los dos arrays): los nueve de antes + **un tercer granjero** (su sitio, al oeste del tercer bancal, que ya
existía con su compostero: `x -34, z 30`) + el **LEÑADOR**, cuyo oficio es **FLETCHER** (flechero) y su estación la
**mesa de flechas** de su **taller**, un cobertizo abierto junto a la **arboleda** (`-52..-48, -26..-22`, con farol y
una pila de troncos). El taller entra en el **plano** (I8), es idempotente (su testigo es la propia mesa, I15), lo
llaman la **migración 62** —antes de tirar el plano—, el latido (si el jugador se lo lleva) y el generador de aldeas
nuevas. De paso, las **flechas** de los arqueros de la milicia ya tienen de dónde salir sin depender de los esqueletos.

**Y la comida** (lo que de verdad pedía el jugador):
- **3er granjero** ⇒ el tercer bancal (72 celdas de cultivo) vuelve a tener quien lo trabaje. Medido: el censo de la
  aldea pasa de `farmer=2` a **`farmer=3`**.
- **Lotes de 8** por viaje (antes **4**): el granjero baja a la despensa **cada 8 unidades entre trigo y vegetales** y
  hornea 2 hogazas por visita. Con la despensa en la taberna (a 40-55 bloques de los bancales) cada viaje es un paseo
  de ida y vuelta: entregar el doble por paseo **duplica el ritmo de comida sin tocar la mecánica del cultivo**.
- Medido con el arnés, ya con **11 bocas**: `[Village] Aldea 2: comida 64 puntos, 11 aldeanos, 29 camas, 11 raciones`
  (la despensa **llena**), frente a los ratos de `comida 0 puntos … 0 raciones` de antes.

**Una profesión por estación, administrada** (invariante **I36**): `VillageManager.podarOficiosDuplicados`, en cada
latido justo después del reparto de oficios. Cuenta los titulares por oficio y, si hay más que plazas, los que sobran
(en orden estable por UUID) pierden el oficio **y su ticket** (`liberarPuesto`: si no, la estación se queda cogida
para siempre, I23) y vuelven al reparto (plaza libre o **gente de sobra**). Cierra las tres puertas por las que
entraban los duplicados: el **bloque** (`AcquirePoi` con `NONE` = *cualquier* estación libre), `reponerProfesiones`
cuando el titular está en un chunk descargado (solo ve 106 bloques) y el **aldeano curado** que vuelve con su oficio
viejo.

**Y el pueblo CRECE** (invariante **I37**): la rama de cría del latido pedía a la vez "todas las especialidades vivas"
y "menos aldeanos que puestos" ⇒ **contradicción**: el pueblo **no paría nunca** por ahí (las crías eran de vanilla,
del pan de `feedVillagers`) y, además, nacía con el oficio de la plaza número `vivos` (**duplicado por construcción**).
Ahora el tope es **`puestos + MILICIA_MAX` (18)** y la cría nace **SIN oficio** (`VillageGenerator.spawnBaby`): al
crecer, el reparto le da una plaza libre o engrosa la **milicia**, que es exactamente lo que pidió el jugador.

**Verificado con el arnés** (aldea 2 de su partida copiada, servidor headless, sin bichos):

| Medida | Resultado |
|---|---|
| Taller del leñador | `taller del leñador levantado en (1362,119,1388) (mesa de flechas en 1363,120,1389)` |
| Puestos repuestos en la aldea ya construida | `repuesto el puesto de toolsmith`, `repuesto el puesto de farmer` y el de `fletcher` |
| Censo de oficios (estable) | `{CRIA=1, butcher=1, cleric=1, farmer=3, fisherman=1, fletcher=1, nitwit=1, shepherd=1, toolsmith=1, weaponsmith=1}` — **una** de cada y **tres** granjeros |
| El leñador TRABAJA | `el lenador guardo 16 cosa(s) de su oficio en el almacen` (tala, replanta y baja la madera) |
| Comida | `comida 64 puntos, 11 aldeanos, 29 camas, 11 raciones` |
| Cría del pueblo | `Aldea 2 crece: aldeano 12/18 (comida 56)` + `CRIA=1` en el censo |
| Duplicados | ninguno (la poda no tuvo que actuar en esa partida) |

**También en este cambio** (inconsistencias del diseño, encontradas al revisar): la etiqueta del **pescador** salía en
**inglés** ("Isidoro (Fisherman)") porque `nombreDeOficio` no tenía su rama (ahora también la tiene el leñador); la
**lista del lint** no vigilaba los goals de las etapas F/G (`VillagerFisherGoal`, `VillagerTavernGoal`,
`VillagerPickupGoal`, `VillagerGateGoal`: ya están dentro, I3/I4/I6) —y siguen pasando--; y `VILLAGERS_FOR_FULL_HEALTH`
seguía clavado en **5** ("aldea sana") con once puestos: ahora **se pide** (`puestosDelPueblo()`), que es I5.

**Lo que NO se ha podido comprobar (sin jugar)**: cómo se ve en su partida (hay que **reiniciar el cliente**), que la
**cría** tarde lo suyo en crecer (mecánica vanilla: ~20 min de juego) y que el leñador use el **taller** además del
monte (el arnés lo vio trabajando y guardando madera, no sentado en la mesa). **Sin migración de aldeanos**: los dos
puestos nuevos los repone el latido al ver sus plazas vacías; la migración 62 solo construye el taller.

### 3b.49 El CLÉRIGO prepara pociones (su goal propio, con lo que el pueblo junta)

Era el último rol **huérfano** del reparto: el clérigo tenía su plaza en el cupo, comía y pagaba su comida, pero **no
tenía ningún goal del mod** — su faena era la actividad de trabajar de vanilla, que necesita `JOB_SITE`… y su
**soporte de pociones de la iglesia estaba libre** (nadie lo había reclamado), así que el juego no le registraba la
actividad y caía a **IDLE** (el mismo "da vueltas sobre su eje" que ya vimos con el herrero). Lo arregla el latido
(`reclamarEstacionesDelPueblo`, I36) y, con el puesto reclamado, esto le da su oficio.

**La cadena es la del pueblo, no magia** (`VillagerClericGoal`, prioridad 4 como el resto de oficios):

1. Los guardias y el jugador matan bichos → el **recolector** barre el botín y lo deja en el **almacén** (pepitas de
   oro, ojos de araña, pólvora); el **granjero** cría **zanahorias**.
2. El clérigo va a **su soporte de pociones** y **lo carga** (`BrewingStandBlockEntity`: las botellas en sus tres
   huecos, el ingrediente encima y el **polvo de blaze** de combustible): **agua + verruga del Nether = poción
   extraña**; **extraña + zanahoria dorada = visión nocturna**; **extraña + ojo de araña = veneno**; y con **pólvora**,
   la versión arrojadiza.
3. **La poción la cuece el JUEGO** (el soporte de vanilla hace su trabajo): el mod no simula nada, solo carga el
   soporte y recoge lo que sale.
4. Y la **zanahoria dorada se fabrica** con la receta de vanilla (**8 pepitas de oro + 1 zanahoria**), que son cosas
   que el pueblo **sí** junta: el clérigo no inventa ingredientes.
5. Las pociones terminadas van **al almacén**, que es de donde las coge el jugador. Cuando no puede hacer nada, **su
   etiqueta dice qué le falta** ("Falta verruga del Nether", "Falta polvo de blaze", "Faltan botellas de agua"), y es
   que el pueblo **no puede fabricar** lo del Nether (verruga, polvo de blaze): eso lo trae el jugador al almacén.

Y hereda lo de la etapa H: si no llega al soporte, **lo aparca** (I33) en vez de quedarse empujando la pared.

**Verificado CON EL ARNÉS** (aldea 2 de su partida copiada, servidor headless): el clérigo **reclama su soporte de
pociones** (`reclama su estacion de cleric en 1397,121,1371`), carga el soporte con lo que se le dejó en el almacén
(verruga del Nether, polvo de blaze, botellas de agua, 8 pepitas de oro + zanahoria para la dorada, ojo de araña) y
**el juego cuece la poción**: `El clerigo guardo una pocion en el almacen: Potion of Poison` ✓ (agua → extraña con la
verruga → veneno con el ojo de araña, y a guardarla). En la misma pasada, el reparador de estaciones dejó a los demás
titulares con la suya (`fisherman`, `fletcher` y `shepherd`, este último con el ticket perdido del telar).

**El viaje al agua, HECHO y verificado con el arnés** (ver 3b.50): el clérigo coge las **botellas de cristal** del
almacén, va a la **orilla** más cercana del término (el bebedero del corral, el lago de la pesquera o cualquier
charca), las **llena** y vuelve al soporte. Lo que **queda pendiente** (rondas siguientes): una **remesa inicial** en
el almacén para que arranque sin que el jugador traiga nada (hoy lo suple con lo que dice su etiqueta) y que las
pociones lleguen también a la **guardia** (una poción por espadachín/arquero, como el arma y el escudo).

### 3b.50 El VIAJE AL AGUA del clérigo (y el contador de atasco que se compartía)

Cierra la mitad que quedaba del clérigo (3b.49): **llenar las botellas él mismo** en vez de esperar a que el jugador
las traiga embotelladas. La verificación con el arnés (aldea 2, servidor headless) encontró **un bug de verdad** en
la primera tirada, así que la ronda valió por dos.

- **Cómo busca el agua** (`VillagerClericGoal.buscarAgua`): barrido por la **superficie** (`WORLD_SURFACE`) en un
  radio de **104** bloques y paso de **4** (el agua del pueblo está a 65-95 bloques de la iglesia: el bebedero del
  corral y el lago de la pesquera; con radio 24 se quedaba diciendo "No encuentro agua" y con paso 2 una charca de
  3×1 se colaba entre las columnas pares). Navega a la **orilla** (una casilla seca al lado del agua **con sitio
  para pararse**), **nunca a la celda de agua**: a un bloque de agua la navegación no llega y el goal se rendía —
  eso era el baile alrededor de la iglesia. Solo se busca **cuando va a llenar** (no cada tick).
- **Botellas y agua**: si lleva **cristal** encima va al agua **antes** que al soporte; al llegar llena **las tres**
  (`llenarBotellas`, receta de vanilla: botella + agua = poción de agua), con sonido de llenado y el suceso
  "Botellas llenas" en su etiqueta. El agua del pueblo es **suya**: el vidrio (lo único del Nether son la verruga y
  el polvo de blaze) sí lo trae el jugador al almacén.
- **EL BUG QUE SALIÓ (y que por eso valía la pena medir)**: con el viaje ya funcionando, el clérigo **aparcaba su
  propio soporte a los 6 segundos de llenar las botellas**, aún a **57 bloques** de la iglesia
  (`no consigue llegar a BlockPos{x=1397, y=121, z=1371}: lo deja por 5 min`), así que la poción **no llegaba a
  hacerse nunca**. Causa: el contador de "no acercarse" (`stuckTicks`/`mejorDistancia`, I3) era **el mismo** para
  las dos piernas del goal; al llenar las botellas valía lo que se había acercado a la **orilla** (~3 bloques), así
  que la caminata de vuelta de **~90** bloques **nunca mejoraba ese 3** y los 120 ticks de paciencia se gastaban
  enteros en 6 s de vuelta → I33 lo aparcaba 5 min. Arreglado con `mejorDistanciaAgua` (contador propio de la
  orilla) y midiendo la vuelta al soporte **de cero** (ver **I38** en `docs/aldea-invariantes.md`).
- **Y el arnés se ajustó para poder medirlo**: `SEMBRAR_AGUA_EMBOTELLADA` (`false` de serie) — con **pociones de
  agua** ya en el almacén el clérigo las usa y **nunca** coge el cristal, así que el viaje no se mide; ahora deja
  **botellas de cristal** y el arnés lo dice en su propia línea
  (`almacen sembrado (agua embotellada=false): ... cristal=3`). Las **dos** tiradas (la del fallo y la del arreglo)
  quedan literales en `tools/arnes/medidas-clerigo-agua.txt`, y el `LEEME` explica qué buscar en el log.
- **Medido (arnés, 18-sep-2026, aldea 2)**: `El clerigo va a llenar las botellas a la orilla de
  BlockPos{x=1431, y=118, z=1455}` → `El clerigo lleno 3 botella(s) de agua` → (vuelta al soporte, **sin aparcar
  nada**) → `El clerigo guardo una pocion en el almacen: Potion of Poison`, **96 s** de cadena completa. Es la
  cadena del pueblo entera: coge el cristal del almacén → lo llena en la orilla → carga el soporte (agua + verruga)
  → **el juego cuece** la poción extraña → ojo de araña → **el juego cuece el veneno** → al almacén.

### 3b.51 El techito del porche, SUELTO de la pared (migración 63)

Lo reportó el jugador mirando la fachada oeste de la taberna: *"el techito que está en la entrada de la taberna está
incompleto porque no conecta con la pared"*. Es el **tercer** fallo del mismo porche (ver 3b.39) y, como los otros dos,
salió del guardado celda a celda (aldea 2, taberna en `1438,1428`, cota `120`).

- **Lo que se midió**: la **pared** de la taberna está en `x = bx` (1438) y el porche salía hacia el oeste con sus dos
  filas en **`bx-2`** (la de dentro, `nivel+PISO2-1`) y **`bx-3`** (la de fuera, encima de los postes). La columna
  **`bx-1`** —la que queda **entre** el alero y el muro— estaba **de aire en todas sus alturas**: **7 de 7** celdas
  vacías. O sea: el toldo lo sostenían **solo sus dos postes**, a **un bloque** de la casa, y por el hueco se veía el
  cielo entre el techito y la pared. La pared, eso sí, era **sólida** detrás (`dark_oak_planks` a la altura de la fila
  de dentro), así que había dónde apoyarlo.
- **El arreglo**: el alero **llega hasta la pared**. Se añade **una fila más** al toldo —el **escalón** en `bx-1` a la
  altura de la fila de dentro y su **tablón de soffito** una capa por debajo— **de punta a punta** (`pz-3..pz+3`), en el
  constructor y en el reparador. El porche queda con **tres columnas** (`bx-3` la de fuera, `bx-2` y `bx-1` a la altura
  de dentro) y su **techo** (el soffito) llega también al muro, así que los **tres faroles** siguen colgando de él.
- **El reparador es ADITIVO**: solo escribe donde la celda está **vacía** (`colocarSiEstaVacio`), así que **no puede
  comerse nada del jugador**; lo único que quita —como en la 55— es un farol flotante de las puntas. **Migración 63**
  (`CURRENT_LAYOUT` 62 → 63), idempotente y **solo en las celdas del porche**: no rehace la taberna (ni la despensa ni
  las camas).
- **Verificado de dos maneras**:
  - **Simulación sobre el guardado** (`build/porche_une.py`, solo lee): las 7 columnas están vacías antes, el arreglo
    cambia **14 celdas** (7 escalones + 7 tablones), la **segunda pasada no cambia nada** y las **7/7** columnas quedan
    con escalón + soffito y **pared sólida** al lado.
  - **El juego de verdad** (arnés headless, partida del jugador copiada): el latido migró la aldea y dejó en el log
    `Taberna de BlockPos{x=1414, y=120, z=1414}: porche reparado (14 cambio(s) en sus celdas: el alero del toldo
    entero, hasta la pared, y los faroles de las puntas colgados del soffito)` —los **mismos 14** que predijo la
    simulación— y la sonda del arnés, celda a celda, `celdas del toldo PEGADAS a la pared: 7/7`.
- **Lo que NO se ha comprobado**: cómo se ve desde la plaza en su partida (hay que **reiniciar el cliente** para cargar
  el mod nuevo); el porche es **decoración** (nadie camina por el alero) y su suelo no se toca, así que no hay nada de
  jugabilidad que medir.

### 3b.52 Los GRANJEROS: reparto de bancales, la valla que trepaban y la cosecha a salto de mata

Tres cosas del mismo oficio, reportadas de una vez por el jugador: *"los granjeros cosechan los 3 en un solo huerto,
cuando lo ideal es que cosechen cada uno en el suyo... deberían ser conscientes de que ya hay uno cosechando... y si no
hay mucho que cosechar pues deberían ir a los otros, para distribuirse. También siguen subiendo a la valla para poder
entrar en vez de usar las compuertas y para cosechar está poco optimizado su método porque dejan sin cosechar unos y
dejan otros cosechando"*. Las tres salieron del guardado y del **arnés** (aldea 2, partida copiada), y las tres tenían
causa medida.

#### a) Los tres, al mismo bancal

`VillagerFarmGoal.buscarCultivo` barría `parcelasDe(...)` **en el mismo orden** para los tres granjeros y devolvía la
**primera** mata madura: los tres acababan en el bancal 0 (el primero de la lista) y, dentro, en la misma esquina.

- **Cada granjero tiene SU bancal, y lo dice su puesto**: la estación del granjero es el **compostero**, y la aldea
  pone **uno por bancal** (`VillageGenerator.composteroDeLaParcela`), así que `miParcela()` lo saca de su `JOB_SITE`
  (con la caída a **UUID** si no se le reconoce el puesto, para no quedarse sin bancal).
- **Orden de trabajo** (`parcelasEnOrden`): **el suyo primero**; si en el suyo no hay faena, los demás **por
  cercanía**, y los que ya está trabajando **otro** granjero al final (se mira si hay otro granjero dentro del
  rectángulo del bancal). Así se reparten y no se pisan, y siguen ayudándose cuando uno no tiene nada que hacer.
- **Medido con el arnés**: cada granjero con su estación y su bancal —`Bibiana: puesto=1421,120,1418` (bancal 1) con
  destino dentro del bancal 1 y etiqueta *Cosechando*; `Isidoro: puesto=1383,120,1448` (bancal 2)—, en vez de los tres
  en el bancal 0.

#### b) La valla que trepaban (dos causas, las dos del pueblo)

Está contado entero en **I40** (`docs/aldea-invariantes.md`): un aldeano **anda** 0,6 hacia arriba y **salta** 1,25,
así que cualquier cosa que se pise junto a la valla (1,5) es un escalón. Las dos que había:

- **El compostero, pegado a la valla** (su tapa a `cota+1` → 0,5 al lomo → se sube andando): **migración 64**, el
  compostero pasa a `corner.x-3` (`COMPOSTERO_DX`). Mover la estación dejó **tres flecos**, los tres medidos con el
  arnés y arreglados en la misma ronda: (1) al aldeano cuyo puesto apuntaba al compostero viejo hay que **darle el
  nuevo** (`moverPuestoDeTrabajo`); (2) el latido **quitaba y reponía el compostero cada 10 s** (en el camino de
  "bancal ya hecho"), y eso **tira su punto de interés** y deja el puesto cogido y sin dueño (I23): ahora, si el
  compostero ya está en su sitio, **no se toca**; y (3) el reparto de estaciones solo miraba a los que **no tienen**
  puesto, así que quien se quedaba con la memoria apuntando a una estación que **ya no existe** no volvía a reclamar
  nunca: ahora `reclamarEstacionesDelPueblo` **suelta el puesto caducado** y le da otro en el mismo latido. Y ojo con
  `PoiManager.release`: **revienta** (`POI never registered`) si en esa celda ya no hay punto de interés, así que se
  suelta **solo si sigue habiendo POI** (`liberarPuesto`).
- **Las losas de la acequia en sus dos extremos** (a `cota+0,5` → 1,0 al lomo → se sube **saltando**): las compuertas
  del bancal caen justo en la fila del medio, o sea al final del canal. **Migración 65**: los dos extremos de la
  acequia vuelven a ser **celdas de cultivo**. Medido: las **42** lecturas de un granjero de pie sobre la valla
  (`y = cota+1,5`) de una corrida estaban **todas** en esa fila; con los dos arreglos, **0**.

#### c) La cosecha, a salto de mata

`buscarEnLasParcelas` devolvía **la primera celda de la lista**, no la más cercana: el granjero cruzaba el bancal para
coger una mata del rincón y dejaba sin tocar las de al lado (el *"dejan sin cosechar unos y dejan otros cosechando"*).
Ahora la búsqueda devuelve **la celda MÁS CERCANA** del bancal, así que el bancal se limpia **de dentro hacia fuera**.
Va en las cuatro búsquedas del oficio (cosechar, plantar, labrar la calva y el compostero).

#### Y de propina, dos desvíos de UNA celda que tenían el compostero muerto

Las dos búsquedas del compostero miraban en `parcela.offset(-1, 0, 0)`, que es **la columna de la valla**, y el
compostero está una celda más afuera: **nunca lo encontraban**. Medido con el arnés: `Lleno el compostero` **no salía
ni una vez** (sin compostar no hay harina de huesos y no se abona nunca). Con la celda sacada de un solo sitio
(`composteroDeLaParcela`, I4), la misma corrida lo llena **18 veces**. Y ya que el granjero va a por él: se **camina a
la celda de al lado** y no al compostero (es un bloque **sólido**, y navegar a un bloque sólido deja al aldeano dando
vueltas — el mismo fallo documentado en `VillageStorage.puntoDeApoyo`): medido con el arnés, la granjera que lo tenía
al otro lado de la valla **se perdió, se subió a la valla y acabó vagando**; ahora va, lo usa y sigue.

**Lo que NO se ha comprobado**: verlo en su partida (hay que **reiniciar el cliente**). Las tres cosas son de
comportamiento de aldeanos, así que no cambian el mundo salvo las migraciones 64 y 65 (el compostero y los extremos de
la acequia), que van en el latido al pasar por la aldea.

### 3b.53 Despedir una invocación con un PALO (botón SECUNDARIO)

Lo pidió el jugador: *"que todas mis invocaciones pueda despawnearlas cuando haga click con un palo sobre ella, solo
cuando tengo un palo nada más"*, **con el botón secundario** y dejando el golpe como estaba (el primer intento lo puso
en el botón izquierdo y lo corrigió el jugador: *"antes cuando golpeaba no le hacía nada a mis minions y ahora sí;
déjalo como estaba"*). Va en `CommonForgeInteractionEventSubscriber.onInteractWithStick`
({@code PlayerInteractEvent.EntityInteract}: la interacción con la entidad, que es el botón secundario).

- **Botón secundario** (click derecho) con un **palo** sobre una invocación **tuya** → se va. El palo vale en
  **cualquiera de las dos manos** (principal o secundaria).
- **El botón izquierdo se queda COMO ESTABA**: pegarle a una invocación tuya no le hace nada, como siempre. Por eso el
  manejador no está en `AttackEntityEvent`.
- **Solo las tuyas**: se comprueba el dueño, así que las de otro jugador y los bichos salvajes no se tocan.
- Sin palo, la interacción es la de siempre (montar, comerciar, dar de comer): con la espada en la mano el palo no
  despide.
- El que despide es el **servidor** y la interacción se **cancela** (el palo no hace nada más). Se van por el camino de
  siempre de cada invocación: lobos, osos y wisps por `PlayerMinionCapability.remove*` (quitan la lista, matan al
  minion y con eso se **poda también la copia guardada**, así que no vuelven al entrar); el shulker del girasol, que no
  vive en ninguna lista, se saca del mundo. El jugador ve un aviso corto (*"Invocación despedida."*).

**Medido** con el arnés (servidor headless, un **lobo de alma de verdad** invocado y metido en la lista del jugador):

```
[ArnesPalo] CASO 1 secundario + palo en la principal + mia:   se espera QUE SE VAYA  -> enElMundo=NO enLaLista=NO => OK
[ArnesPalo] CASO 2 secundario + palo en la secundaria + mia:  se espera QUE SE VAYA  -> enElMundo=NO enLaLista=NO => OK
[ArnesPalo] CASO 3 secundario + espada + mia:                 se espera QUE SE QUEDE -> enElMundo=SI => OK
[ArnesPalo] CASO 4 secundario + palo + ajena:                 se espera QUE SE QUEDE -> enElMundo=SI => OK
[ArnesPalo] CASO 5 izquierdo (golpe) + palo + mia:            se espera QUE SE QUEDE -> enElMundo=SI enLaLista=SI => OK
```

Las líneas literales están en `tools/arnes/medidas-minions.txt`, y el arnés (`tools/arnes/MinionHarness.java`) se queda
como referencia.

### 3b.54 Los granjeros cosechaban A TRAVÉS de la valla (no entraban al bancal)

Lo reportó el jugador, con captura: *"los granjeros no están entrando a la granja, ¡corrígelo!"*. El arnés lo dejó
claro: **sí trabajaban, pero desde fuera**. El alcance de la faena son **3 bloques** (`REACH`), así que un granjero
parado **fuera** de la valla alcanzaba las matas de la primera fila y las **cosechaba a través de la reja**: no le
hacía falta entrar. El arreglo del barrido "más cercana" (3b.52c) lo empeoró, porque ahora el objetivo más cercano es
justo el del borde.

- **La faena de la huerta se hace DENTRO del bancal** (`VillageGenerator.estaDentroDeLaParcela`): si el objetivo está
  en un bancal y el granjero está fuera, no trabaja.
- **Y si está fuera, se le manda a la PUERTA** más cercana de ese bancal
  (`VillageGenerator.entradaDeLaParcela`: la celda de dentro del portón más próximo): al ponerse a su lado,
  `VillageGateGoal` se la abre (a 2,6) y entra. La etiqueta lo dice: **"Entrando a la huerta"**.
- La pierna de la **puerta** se mide con **su propio** contador (`mejorDistanciaEntrada`/`stuckEntrada`, I38): son dos
  piernas distintas (la puerta y la mata) y con un contador compartido la segunda se mediría contra la primera. Si no
  consigue entrar en 6 s lo dice en el log y suelta el objetivo (I33).

**Medido** con el arnés sobre la partida del jugador (antes y después):

| | antes | después |
|---|---|---|
| Granjeros **dentro** de su bancal | solo en el borde (el objetivo era la mata pegada a la valla) | `bancal0=granjeros:1`, `bancal1=granjeros:1`, `bancal2=granjeros:1` (cada uno en el suyo) |
| Etiquetas | `Trabajando` (genérica, fuera) | `Cosechando` (135 lecturas), `Guardando lo suyo`, `Guardo 8 en la despensa` |
| Subidas a la valla | 0 | 0 |

### 3b.55 El RECOLECTOR, de charla en el desván: el almacén no se alcanza desde ahí

Lo preguntó el jugador, con captura de la etiqueta: *"¿por qué el recolector está de charla en el 3er piso sin hacer
nada?"* (Fabricio, `Recolector`, en el desván de la taberna). No era que no tuviera faena: el arnés lo dejó claro.

- Tenía su goal **activo** (`VillagerCollectGoal`) y estaba **con las manos llenas** (8 cosas) intentando **ir al
  almacén** (`destino=1461,121,1434`, etiqueta *"Yendo al almacen"*), pero **no se movía** de `(1455,131,1434)`.
- La sonda de rutas lo explica: desde el desván el aldeano calcula ruta a la **plaza** (17 nodos) y al **hueco del
  desván** (14), pero al **almacén** le sale una ruta **degenerada de 1 nodo**: **inalcanzable** desde ahí. Se quedaba
  clavado arriba, el goal se rendía a los 6 s, aparcaba el almacén (I33), descansaba… y en bucle.
- Y de dónde venía: había subido al desván a por **tres camas tiradas** en el suelo (las camas están en la lista blanca
  del recolector: son del pueblo). Medido: 3 `Red Bed` en `(1441,131,1432)`, `(1443,131,1434)` y `(1444,131,1432)`.

**Arreglo**, cuatro piezas (las tres primeras para que **no se quede dentro de una casa**, la cuarta para el que ya
está dentro):

1. **Si no hay nada que recoger** y está **fuera de su sitio** (a más de `RADIO_VUELTA` = 24 del centro, o a más de 4
   bloques de la cota, o sea metido en un piso), se **vuelve a la plaza** (*"Volviendo a la plaza"*).
2. **Si no alcanza el almacén** (6 s sin acercarse), se **apunta el sitio** (I33) y **se vuelve a la plaza**: desde
   ahí el almacén **sí** se alcanza, así que al siguiente intento (pasados los 5 min del aparcado) entrega lo que lleva.
   Y si el almacén ya está **aparcado**, **no se le vuelve a mandar** allí (era lo que le hacía **oscilar** entre el
   almacén y la plaza en la escalera de la taberna: medido, 20 s subiendo y bajando sin bajar nunca).
3. **No sube a los PISOS a por cosas** (`ALTURA_MAXIMA` = 4 sobre la cota, y ni a los sótanos): el recolector barre el
   pueblo **a la altura de la calle**. Los pisos son de quien vive ahí —el jugador se está haciendo su base en ese
   desván— y, además, desde ahí arriba no se puede entregar. Eso corta el problema **de raíz**: las tres camas tiradas
   del desván se quedan donde están (son de quien las tiró).
4. **Y AL QUE YA SE HA QUEDADO DENTRO, SE LE BAJA** (`VillageManager.rescatarAldeanosAtrapados`, en el latido): un
   aldeano que lleva **30 s sin moverse de celda** en un piso (o un sótano) se **baja a la plaza**, con su línea en el
   log. No se toca a quien está durmiendo (dormir en la posada es legítimo) ni al que anda por la calle. Hace falta de
   verdad: el recolector, al intentar salir del desván, se quedaba **encajado contra los cofres** —la ruta a la plaza se
   calculaba (9 nodos) y el aldeano **no se movía**— y en una de las corridas **se murió de hambre ahí arriba**.

**Medido** con el arnés sobre su partida, al final:

```
[Village] b7073e55-... estaba atascado dentro de una casa en 1447, 131, 1430: lo bajo a la plaza (1410, 120, 1410)
[Arnes] t=82449 ... RECOLECTOR pos=(1460.19,120.00,1404.20) ... rutas[almacen=SI(37)] etiqueta=... | Recogiendo
```

O sea: **baja a la calle** (120 lecturas en `y=120`, antes 0), con el almacén alcanzable (`SI(37)` frente a la ruta
degenerada de 1 nodo) y **trabajando** (`Recogiendo`) en el término del pueblo.

### 3b.56 El techito, con BLOQUE NORMAL en la celda del muro (migración 66)

El jugador lo corrigió al verlo: *"el techito que pusiste quedó bastante extraño; se necesita poner un bloque normal y
luego ahora sí el bloque de escalera bien alineado para que quede bien"*. La **63** había cerrado el hueco entre el
toldo y la pared añadiendo la fila de `bx-1` con **otro escalón**: dos escalones seguidos a la misma altura se ven como
un **doble peldaño** raro contra el muro.

Ahora el perfil del alero es: **tablón sólido** en `bx-1` (pegado al muro), **escalón** en `bx-2` apoyando su cara alta
contra él y **escalón** un bloque más bajo en `bx-3` (sobre los postes) — sube hacia la casa y baja hacia fuera. La
**migración 66** cambia ese escalón por el tablón en las tabernas ya construidas (solo si sigue siendo el escalón del
toldo, con su `facing` y su `half`: lo que ponga el jugador se queda), y es idempotente.

### 3b.57 El FUEGO se paga con LEÑA del almacén (y la aldea arranca con 128 troncos)

El jugador lo recordó: *"el smoker, el furnance y todos los aparatos donde se tenga que quemar necesitan ir por logs al
almacén para que se use de combustible y funcionen. ¿Ya se hace esto?"*. Y después: *"considera entonces que
inicialmente tenga la aldea suficiente madera en el almacén, unos 128 logs"*.

**Lo que había** (auditoría, `tools/arnes/medidas-combustible.txt`): quemaba **un solo** aparato, el **soporte de
pociones** del clérigo (gastaba `blaze_powder` del almacén, y sigue igual). El **ahumador** del cocinero
transformaba el crudo en cocido **sin combustible** —su propia clase lo decía: *"no hay tiempo de cocción ni
carbón"*—, los **cuatro hornos** del pueblo (cocina de la taberna, desván de la posada y los dos de la herrería) son
**decorativos** y no son estación de nadie (I31), el **hogar** es un campfire de vanilla y el **herrero** fundía en su
mesa por transformación. Barrido de la partida: 1 ahumador, 4 hornos, 1 hogar, 1 soporte; y en el almacén **157
troncos de acacia, 16 tablones y 0 carbón**.

**El arreglo**, tres piezas:

1. **El ahumador se paga con leña** (`VillagerCookGoal`): si no lleva troncos, el cocinero **va al almacén**
   (`VillageStorage.puntoDeApoyo`, etiqueta *"A por lena al almacen"*), coge hasta **4** y vuelve a la cocina; cada
   tanda de hasta 8 piezas quema **1**. Si se le acaba, vuelve a por más.
2. **La fragua también** (`VillagerSmithGoal`): las **fundiciones** (pepitas → lingote, chatarra → lingote, chatarra
   de oro → lingote) llevan un `quema = true` en su `Receta`; el herrero se lleva **1 tronco** en la fase `RECOGER`
   (la que ya hacía al almacén) y lo gasta al fundir. Las faenas de mesa y muelle (aserrar, palos, forjar, encorar,
   flechar, curtir) **no** queman.
3. **Nada de grifos ni de ahumadores fantasma: la RESERVA de 32 troncos** (`VillageStorage.RESERVA_LENA`). La madera
   es **también** la materia prima del herrero, así que `quitarLena` solo entrega el **excedente** por encima de la
   reserva; con el almacén en la reserva el ahumador se **apaga** (etiqueta *"Sin lena para el ahumador"* + una línea
   de log) y el herrero **no elige** recetas que quemen (se pone a aserrar). Y la **remesa inicial de madera**:
   **128 troncos de roble** al almacén **vacío** (aldea recién fundada), con la misma regla que la remesa de la
   despensa, llamada en el latido **justo después** de `asegurarAlmacen` (en la misma pasada en que nace el cofre).
   Sin migración: el bloque corre en aldeas nuevas y viejas (I6), y una aldea en marcha no recibe nada.

**Medido** con el arnés sobre su partida (log literal, tres corridas):

```
[Village] El cocinero: cogio 4 tronco(s) del almacen para el ahumador (aldea 2; quedan 192 en el almacen)
[Village] El cocinero: 8 pieza(s) cocinadas con un tronco del almacen (aldea 2)              (x8 tandas)
[Village] El herrero de herramientas: Fundio 9 pepitas en un lingote (quemo un tronco del almacen)
--- con el almacén en la reserva (34 troncos) ---
[Village] El cocinero: cogio 2 tronco(s) del almacen para el ahumador (aldea 2; quedan 32 en el almacen)
[Village] El cocinero no cocina: el almacen no tiene lena por encima de la reserva de 32 (aldea 2), asi que el
          ahumador se queda apagado
[Arnes] COMBUSTIBLE: lenaEnAlmacen=32 (reserva=32) carneCrudaEnDespensa=26     (clavado en 32)
[Village] El herrero de herramientas: Aserro un tronco en 4 tablones             (sin leña NO funde)
--- con el almacén VACÍO (aldea recién fundada) ---
[Village] almacen: remesa inicial de madera (128 troncos de roble para el fuego del cocinero, la fragua del
          herrero y su sierra)
[Arnes] COMBUSTIBLE: lenaEnAlmacen=0 ... -> 128 -> 124 (los 4 que se llevó el cocinero) y ya no sube
```

**Reglas nuevas**: **I41** (el fuego se paga con leña del almacén y nunca toca la reserva) e **I42** (la aldea arranca
con 128 troncos, una sola vez). Y una consecuencia que conviene tener presente: la **cadena de la madera** del pueblo
es ahora la que sostiene la cocina —el **leñador** (FLETCHER) sube los troncos, el **herrero de herramientas**
(TOOLSMITH) los asierra—, y el **recolector** (NITWIT) sigue sin tocar la madera: son **tres aldeanos distintos**.

### 3b.58 El GRANJERO que no podía dormir: la cama que vanilla le borra, el bancal que lo encierra y la compuerta que nadie abría

El jugador lo vio en una captura: **dos granjeros con la etiqueta "Sin cama"** encima, de pie en la huerta toda la
noche, *"si la aldea está repleta de ellas"*. Medido con el arnés sobre su partida (noche congelada), el bug eran
**cuatro causas encadenadas**, y tres son de vanilla:

1. **Dos aldeanos con la misma cama.** Una cama son **DOS POIs `HOME`** (cabeza y pie), así que dos aldeanos pueden
   quedarse con **una mitad cada uno**; el que se duerme pone `OCCUPIED` en las dos mitades y al otro,
   `ValidateNearbyPoi` (cerebro) le **borra la cama**. Medido: Isidoro tenía la mitad de la cama del herrero
   (`1442/1443,131,1432`), el herrero se durmió en ella y a Isidoro le borraron el `HOME` — y el latido se la volvía a
   dar (la misma) en bucle.
2. **Vanilla borra la cama del que no llega en 60 s.** `SetWalkTargetFromBlockMemory` (paquete `REST`, registrado para
   `HOME`): si el aldeano lleva **1200 ticks** con `CANT_REACH_WALK_TARGET_SINCE` puesto (no consigue ruta a su cama),
   hace `releasePoi(HOME)` + `erase()`. Medido: exactamente **60 s** entre reclamación y borrado, en bucle, con la
   cama **libre, con POI y sin nadie durmiendo** (lo cazó el vigilante a resolución de tick).
3. **Estaba encerrado en el bancal.** La ruta del granjero a su cama **acaba en su propia compuerta** (`alcance=NO`):
   el juego **no deja que un aldeano abra una puerta de valla cerrada**, así que no puede planificar la salida; y el
   `AcquirePoi` de vanilla tampoco le da cama porque exige `path.canReach()`. **Abrazo mortal**: sin cama no sale del
   bancal, y desde el bancal no alcanza ninguna cama.
4. **El goal de portones tenía elegida OTRA puerta.** `portonMasCercano` solo se llama desde `canUse`, y `canUse` no
   se vuelve a llamar mientras el goal está corriendo (sigue mientras tenga un portón a menos de 16): el portón
   elegido a mala hora (aldea a medio migrar, casilla sin cargar) **se quedaba pegado para siempre**. Medido con el
   diagnóstico dentro del goal: **`porton=1390,120,1447`** (a 7,09 bloques) en vez de la compuerta de al lado
   (**1395,120,1452**, a 0,87) — el aldeano «vigilaba» una puerta lejana y no abría la suya.

**Arreglo** (cuatro piezas, todas medidas):
1. **La cama es suya**: `reclamarCamasDelPueblo` no le da una cama **compartida** (la otra mitad de otro aldeano), ni
   **ocupada**, ni la que ya tiene otro aldeano de **alrededor de la cama** (no solo de la lista del censo: así la rama
   del «ticket perdido» no se la roba a nadie).
2. **La cama tiene que ser alcanzable**: fuera de un bancal, solo una cama a la que su ruta **llega** (`canReach`);
   **encerrado** en un bancal, la que más se acerca (y el goal lo saca). Así no se le dan camas que le cuestan el
   `HOME` a los 60 s.
3. **El granjero SALE del bancal al anochecer** (`VillagerFarmGoal`, `Tarea.SALIR`): a la celda de **FUERA** de la
   compuerta (`VillageGenerator.salidaDeLaParcela`; la de *dentro* no vale, porque `VillagerGateGoal` solo abre si el
   destino está al otro lado, `vaACruzar`), con la etiqueta *"Saliendo de la huerta"*.
4. **El goal de portones vuelve a elegir** si el portón que tiene no es el de al lado (y valida su lista guardada,
   recalculándola si cayó rancia), y al abrir una compuerta le hace **rehacer el camino** (la ruta que traía se calculó
   con ella cerrada, así que acababa en su propia casilla y la compuerta se cerraba a los 5 s sin que nadie la
   cruzara).

**Medido** (misma noche, misma partida): las compuertas **ABIERTAS** (`1395,120,1452=ABIERTA`,
`1428,120,1427=ABIERTA`), los dos granjeros **durmiendo en su cama** (`durmiendo=true durmiendoEnElla=[EL MISMO]`) y
**cero** pérdidas de cama en toda la corrida (antes, una cada 60 s).

**Lo que quedaba, y se cerró en 3b.59**: el **herrero de herramientas** se quedaba sin cama porque las suyas libres
cercanas no le servían (el planificador no le dejaba dar los dos últimos pasos).

### 3b.59 El HERRERO sin cama: el planificador no le dejaba dar los dos últimos pasos (y el pueblo le acuesta)

El jugador lo pidió después: *"corrige esto: el herrero de herramientas sigue sin cama"*. Con la regla de I43 (solo
camas **alcanzables**) el herrero se quedaba **sin ninguna**. La sonda de rutas del arnés, **celda a celda**, encontró
por qué:

- Su cama libre (`1452,120,1405`, en el dormitorio de una **casa del juego**) está a **0,87** bloques de donde trabaja.
- **El planificador SÍ le lleva dentro de la casa**: ruta de **25 nodos** hasta `1447,120,1404`, ya en el dormitorio
  (entra por la puerta de la fachada oeste, dando la vuelta al edificio).
- Pero desde ahí **no hay manera de que le acerque a los 2,0 bloques** que exige el juego para acostarse
  (`SleepInBed` pide `closerToCenterThan(pos, 2.0)`): las rutas a las celdas de al lado de la cama acababan a **2,00**
  y **3,00** bloques (`a1=3n alcance=NO fin=1454,120,1405 dFin=2.00`, `a1=10n alcance=NO fin=1450,120,1408 dFin=3.00`).
  Y como no llega, vanilla le borra la cama a los 60 s (I43) → bucle.
- El diagnóstico dentro del reparto lo confirmó: *"`1452,120,1405` no llega (fin `1454,120,1405`) y **sin celda de
  espera**"* — y ahí estaba el fallo de mi propia comprobación: la línea de visión apuntaba **al centro de la cama**, así
  que el raycast chocaba con **la cama misma** y daba por bloqueadas todas las celdas.

**Arreglo**, tres piezas (todas en `VillageManager`):
1. **Celda de espera** (`celdaParaAcostarse`): al darle una cama a la que no llega, se busca —de la más cercana a la
   más lejana— una celda a la que **sí llegue** y desde la que **vea la cama** (línea de visión que **no cuenta la
   propia cama** como obstáculo, solo un muro de verdad). Se guarda por aldeano.
2. **Se le lleva**: en el latido, si está en su hora de descanso y aún no está cerca, se le manda a esa celda
   (*"Yendo a dormir"*). El planificador no le lleva a la cama, pero a esa celda sí.
3. **Se le acuesta** (`acostarAlQueNoLlega`): cuando la tiene **a la vista** y a menos de 6 bloques y la cama está
   **libre**, se le acuesta con la misma llamada que usa el juego (`LivingEntity.startSleeping`, que además marca la
   cama como ocupada). No es un teletransporte: el aldeano ha llegado **andando** hasta ahí.
4. **Y si no llega ni a la celda de al lado, el pueblo le lleva** (lo pidió el jugador: *"si es necesario hacer tareas
   personalizadas para hacer cosas que vanilla aparentemente hace (por mal) pues que se haga"*). La celda de espera ya
   no exige que el planificador llegue: vale **la más cercana que el aldeano VEA** (a menos de 6 bloques); si el
   planificador tampoco le lleva ahí, se le **mueve a esa celda** —que está a la vista y a un paso, no es un salto a
   ciegas— y en la pasada siguiente se le acuesta. Así **una cama que se ve y está al lado no se descarta nunca**: con
   camas de sobra, el que no duerme es el aldeano, no la cama.

**Medido** (misma partida, noche congelada): *"`9e0ed6e3` no llega a su cama por el camino del juego: se le da
`1452,120,1405` y se le mandará a `1449,120,1405` para acostarle"* → el herrero **duerme en su cama**
(`durmiendo=true durmiendoEnElla=[EL MISMO]`, `pos == home`) → **`CAMAS RESUMEN: adultos=11 conCama=11
COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=11`** (el pueblo entero con cama **y durmiendo**) y **0** pérdidas de cama en toda
la corrida.

### 3b.60 El FAROL de encima del primer escalón y las PUERTAS que los aldeanos dejaban abiertas

Dos cosas que reportó el jugador con captura:

**1) "hay que quitar esta lámpara que está justo arriba de las primeras escaleras de la planta baja porque estorba al
querer subir por ahí".** El pie de la escalera del comedor está en `bx + TABERNA_ESCALERA_PIE_DX (=4), nivel,
bz + TABERNA_ESCALERA_MESETA_Z (=11)` y `lucesDeLaTaberna` colgaba un farol del comedor **en la misma vertical**
(`{dx=4, dz=11}`, a `nivel+3`): un farol tiene caja de colisión, así que el que subía se daba con él. Se quita de la
lista (el de `{4, 7}` queda al lado: el comedor sigue iluminado) y `VillageGenerator.quitarElFarolDeLaEscalera` lo
retira en las tabernas ya construidas, **idempotente** y solo si esa celda sigue siendo un farol. Medido:
`Taberna de …: quitado el farol de encima del primer escalon (1442, 123, 1439)`.

**2) "los aldeanos cuando vayan a dormir tienen que cerrar la puerta porque todas la dejan abierta".** El juego tiene
su comportamiento (`InteractWithDoor` + `DOORS_TO_CLOSE`) pero **con los aldeanos del pueblo no cierra nada**: medido
con el arnés, al empezar la noche había **8-9 puertas de madera abiertas** en el recinto y ninguna se cerraba sola.
Ahora el pueblo las cierra por su cuenta (`VillagerDoorGoal`, **sin banderas**, como el de los portones):
- solo mira puertas **de madera** (las de hierro no las abre un aldeano) **abiertas y pegadas al aldeano**;
- se apunta que la ha **usado** cuando está **en el hueco** (a menos de 1,5): la que solo tiene al lado —o la que el
  jugador dejó abierta y él pasa por delante— **no se toca**;
- cuando ya ha pasado, la cierra (`DoorBlock.setOpen(..., false)`: las dos mitades, con su sonido);
- y **no se le cierra a un jugador al lado** (a menos de 2,5).

**Medido** (día fijo, para que los aldeanos salgan y crucen): **36 cierres** en la corrida, cada uno con su aldeano
—`PUERTA CERRADA en 1443,120,1401 (aldeano(s) al lado: 9e0ed6e3)`, `… 1438,120,1435 (5701c0e4)`…— y el contador de
puertas abiertas del pueblo **baja de 8-9 a 4** (las que quedan son de la posada, que nadie cruza de día: no se tocan a
propósito).

**Y de propina, Mauricio**: el jugador preguntó por qué Mauricio (Sin oficio) no tenía cama. Aquí se escribió que era
"el mismo fallo de la cama inalcanzable" y que el pueblo entero dormía (`adultos=11 … SIN CAMA=0`). **Era falso, y se
corrigió en 3b.61**: Mauricio y Leoncio no eran adultos sin cama, eran **crías** (su etiqueta de día dice "Jugando"),
el censo del arnés medía **64 bloques y solo adultos** (así que no los veía) y el reparto de camas las **saltaba**. Su
etiqueta de noche —"Sin cama"— sí las nombraba, que es lo que el jugador veía. Ver 3b.61.

### 3b.61 Mauricio (una CRÍA) sin cama, y el reparto de camas con bichos dentro de la aldea

El jugador insistió con captura: *"Mauricio sigue sin ir a buscar cama y hay varias en la taberna"*. La etiqueta del
aldeano decía **`Mauricio (Sin oficio) · Sin cama`**. Dos causas, las dos medidas en su guardado y con el arnés:

**1) MAURICIO ES UNA CRÍA, y el reparto saltaba a las crías.** Su guardado tiene **15 aldeanos** cerca de la plaza, y
los cuatro "Sin oficio" —Leoncio (`2a04aa3e`, -32,-3), **Mauricio** (`2b322f2d`, -31,20), Ubaldo (`bd81570c`, con cama)
y Nicasio (`0f41e91b`, con cama)— son **crías**: su etiqueta de **día** dice `Jugando`. `reclamarCamasDelPueblo` las
**saltaba a propósito** ("una cría duerme con el pueblo"), pero eso no se sostiene: vanilla **sí** deja que una cría
reclame cama (Ubaldo y Nicasio la tienen) y, peor, en la etiqueta de la **noche** la rama de descanso se mira **antes**
que la de cría, así que a una cría sin cama se le pone **"Sin cama"** —justo el cartel que vio el jugador—. Ahora entran
en el reparto y en el acostado como cualquier aldeano. Medido (noche fija, arnés):

    [Village] 2a04aa3e-… no tenia cama: reclama la de 1372, 124, 1433 (…; libre y sin companero de cama; red_bed ocupada=false)
    [Village] 2b322f2d-… no tenia cama: reclama la de 1370, 124, 1433 (…)
    [Arnes] CAMAS RESUMEN: aldeanos=15 (adultos=11 crias=4) conCama=15 (camas distintas ocupadas=15) COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=12

**2) EL REPARTO DE CAMAS VIVÍA DENTRO DEL "LATIDO EN PAZ"**, y por eso en su partida no se arreglaba nunca. El reparto
estaba en `prepareRepairs` (dentro de `tickVillageLife`), detrás de dos guardas de `manageNearby`:
`!isUnderAttack(...)` y `hayEnemigosDentro(...) -> continue`. Las dos tienen sentido para lo que **repuebla** (no se
reponen aldeanos mientras los monstruos los están matando), pero **no para las camas**: la noche del asedio es justo
cuando hacen falta. Con un bicho dentro, el aldeano sin cama se quedaba sin ella, y como el hambre y la edad tampoco
corrían, el estado se quedaba **congelado** con él de pie y "Sin cama" delante del jugador. Ahora va en
`atenderCamasDelPueblo`, que se llama **fuera** de las dos guardas (y sigue sin tocar una aldea caída).

**Medido con el arnés**, plantando a propósito un **aldeano-zombi** dentro de la plaza (`1420,120,1420`; es un
`Monster` que cuenta para `hayEnemigosDentro` pero el sello no expulsa) → `UN BICHO DENTRO: SI (1 monstruo(s): latido
cortado)` durante toda la corrida:

    ANTES  (devolviendo la guarda vieja al bloque: `&& !hayEnemigosDentro(...)`)
    [Arnes] CAMAS RESUMEN: aldeanos=15 (adultos=11 crias=4) conCama=13 … SIN CAMA=2 2a04aa3e(none,cria) 2b322f2d(none,cria) DURMIENDO=6 · UN BICHO DENTRO: SI

    DESPUÉS (atenderCamasDelPueblo, sin guardas)
    [Village] 2a04aa3e-… no tenia cama: reclama la de 1372, 124, 1433 …
    [Village] 2b322f2d-… no tenia cama: reclama la de 1370, 124, 1433 …
    [Village] Aldea 2: los 15 aldeanos (crias incluidas) tienen cama
    [Arnes] CAMAS RESUMEN: aldeanos=15 (adultos=11 crias=4) conCama=15 (camas distintas ocupadas=15) COMPARTIDAS=0 SIN CAMA=0 DURMIENDO=12 · UN BICHO DENTRO: SI

**Y EL CENSO DEL ARNÉS ESTABA MAL**: medía un radio de **64** y **solo adultos** (dos recortes que se sumaban al
mismo error: el mod reparte camas hasta `FENCE_RADIUS + 44` = **106** y ahora también a las crías). Con eso el resumen
decía `SIN CAMA=0` con el jugador viendo lo contrario. Ahora el censo usa **106** y cuenta **crías** —y las marca—, y
el mod deja en el log un aviso con **nombres** cuando alguien se queda sin cama (`SIN CAMA Mauricio (cria), … de 15
aldeanos (camas del pueblo: 20)`), una vez por cambio y no en cada latido.

### 3b.62 La puerta NO se cierra con alguien dentro del hueco (el aldeano que andaba "erráticamente")

Lo reportó el jugador con captura: *"Anselmo empezó a caminar erráticamente. Antes de ponerse como paseando, estaba en
el estado «cerrando la puerta» pero un aldeano lo movió y empezó a caminar así"*.

`VillagerDoorGoal` (3b.60) cerraba la puerta **5 ticks después** de que el que la cruzó saliera del hueco (a más de
1,5), **sin mirar quién más había dentro**. Dos aldeanos cruzando la misma puerta seguidos es lo normal al irse a
dormir: la puerta se cerraba **encima del segundo**, que quedaba atrapado contra su caja de colisión —vibrando y
andando a tirones— y empujaba al primero. Ahora, antes de cerrar, se comprueba que **no haya nadie en el hueco**:
`hayAlguienEnElHueco` mira las **dos mitades** de la puerta y a **cualquier entidad** (no solo al jugador, que ya tenía
su guarda). El arnés cuenta este caso en su barrido —el **centro de la entidad dentro de la celda** de la puerta, que la
caja de colisión rozando la celda da falsos positivos—: `PUERTAS DE MADERA ABIERTAS … (cerradas CON alguien dentro: M)`.

**Medido, mismo mundo, con y sin la guarda** (`medidas-puertas.txt`):

    ANTES (sin la guarda)
    [Arnes] PUERTA CERRADA CON ALGUIEN DENTRO en 1443, 120, 1401: villager pos=(1443.34,120.00,1401.52) velocidad=0.00
    [Arnes] PUERTAS DE MADERA ABIERTAS en el pueblo: 4 … (cerradas CON alguien dentro: 1)

    DESPUÉS (con la guarda)
    [Arnes] PUERTA CERRADA en 1438, 120, 1435 (… 9036d1d0(Bibiana (Granjero) | Cerrando la puerta))
    [Arnes] PUERTAS DE MADERA ABIERTAS en el pueblo: 4 … (cerradas CON alguien dentro: 0)   ← en TODOS los barridos

O sea: el aldeano atrapado y **parado** dentro de la puerta (velocidad 0,00) desaparece, y las puertas se siguen
cerrando (**8 cierres** en la corrida, con la etiqueta "Cerrando la puerta" puesta por el goal en el que la cierra: el
contador de abiertas baja de 6 a 4).

### 3b.63 El COCINERO cocinaba fuera de la taberna

Lo reportó el jugador con captura: *"El cocinero está cocinando FUERA de la taberna. Esto no debe ser así, debe estar
adentro"*. Y era verdad: cocinaba **en la calle, a través de la pared**.

La causa es el alcance con el que se daba por llegado: `REACH = 6.5` medía **solo la distancia** a la casilla de la
cocina (la de delante del ahumador, `1442,120,1429`) y `ALCANCE_AHUMADOR = 8.0` la distancia al ahumador, **sin
comprobar nada del camino**. Medido con el arnés en una corrida de día (`MEDIR_COCINA`): posiciones que el test viejo
aceptaba como "estoy en la cocina", con el ahumador **tapado por la pared**:

    pos=1446, 120, 1425  dentroDeLaTaberna=NO VEelAhumador=NO dCasilla=5.76 dAhumador=6.44   ← en la calle
    pos=1437, 120, 1429  dentroDeLaTaberna=NO VEelAhumador=NO dCasilla=5.01 dAhumador=5.16   ← en la calle

y el estado del cocinero **en el momento exacto de una tanda** (la muestra anterior a cada `N pieza(s) cocinadas`, con
la etiqueta "Cocinando" que pone el propio goal):

    [Arnes] COCINERO pos=1442, 120, 1435 dentroDeLaTaberna=SI VEelAhumador=NO dCasilla=6.19 dAhumador=5.19 | Cocinando
    [Village] El cocinero: 8 pieza(s) cocinadas con un tronco del almacen (aldea 2)

O sea: cocinando desde el **comedor**, con el **tabique de la cocina en medio**. Ahora se exige **estar en la casilla de
la cocina** (`REACH = 2.0`) y **ver el ahumador**: el rayo de colisión (`VillageManager.hayVistaLibre`, la misma
comprobación que usa el sueño para no acostar a nadie a través de un muro, ahora genérica y pública) tiene que dar vía
libre o chocar con el propio objetivo. Después, en el momento de la tanda:

    [Arnes] COCINERO pos=1442, 120, 1431 dentroDeLaTaberna=SI VEelAhumador=SI dCasilla=1.91 dAhumador=1.02 | Cocinando
    [Village] El cocinero: 8 pieza(s) cocinadas con un tronco del almacen (aldea 2)      (×2 tandas en la corrida)

Y no se queda fuera: la ruta a esa casilla **llega** (`ruta: a1=19n alcance=SI fin=1442,120,1429 dFin=0.00`), que era
la duda antes de apretar el alcance (si el planificador no llegara, el goal se rendiría con el aparcado de I33 y el
pueblo se quedaría sin cocina).

### 3b.64 El SELLO tumbaba el servidor (NPE con un spawn sin tipo) — hallado midiendo

Midiendo lo de arriba, el arnés plantó su aldeano-zombi con `level.addFreshEntity(...)` dentro de una aldea protegida y
el **servidor se cayó**:

    java.lang.NullPointerException: Cannot invoke "net.minecraft.world.entity.MobSpawnType.ordinal()" because "tipo" is null
        at PlayerCapabilityForgeEventSubscriber.esSpawnQueElSelloCorta(PlayerCapabilityForgeEventSubscriber.java:221)
        at PlayerCapabilityForgeEventSubscriber.onEntityJoinLevel(PlayerCapabilityForgeEventSubscriber.java:214)

`getSpawnType()` es **`null`** en todo lo que entra al mundo con `addFreshEntity` (lo que sueltan las mecánicas del
mod, otros mods o un `/summon` de código) y el método hacía `switch (tipo)` sobre él. O sea: **cualquier cosa que
añada un monstruo así dentro de una aldea protegida tumbaba el servidor entero** (no un error del arnés: el arnés solo
lo destapó). Sin tipo no se puede saber de dónde viene y el sello solo corta lo que **sabe** que es natural, así que un
spawn sin tipo **pasa** (`if (tipo == null) return false;`).

### 3b.65 La pared de la casa a la que le faltaba un bloque (y el cofre de al lado)

Lo reportó el jugador con captura: *"¿qué ves de extraño en esta casa? ¡si le falta completarse a la pared! corrígelo
y checa que el cofre no estorbe"*. En el F3 de su captura: el jugador en `1423.6, 120, 1391.97`, mirando al este, con
un hueco a su izquierda por el que se ve el interior y el bloque señalado en `1431,120,1391` (adoquín).

**Lo que faltaba, medido en su guardado**: la casa es la plantilla `plains_medium_house_2` (7x6x13) colocada con su
puerta a la cota (`origen = 1426, 119, 1382`) y su pared oeste tenía **aire en dos celdas** —`1427,120,1392` y
`1427,121,1392`—: un boquete de 1x2 pegado a la puerta (con la ventana a un lado y el poste de la esquina al otro). Y
**la plantilla del juego pide adoquín** justo ahí (`dx=1, y=1..2, dz=10`), así que el hueco **no venía del juego**: lo
perdió el mundo. El cofre del aviso del jugador está dentro, a una celda del hueco (`1428,120,1392`, y la plantilla
también lo trae ahí).

**Y NADIE PODÍA REPONERLO**, que es la otra mitad del fallo: el **plano** de la aldea (lo que el obrero usa para
reparar, I8) se capturó por **escaneo** del mundo —una aldea migrada— y el escaneo **descarta el aire**, así que esas
dos celdas no están en el plano:

    --- la celda del hueco: (1427,120,1392) ---   NO ESTA EN EL PLANO (el obrero no tiene nada que reponer ahí)
    --- vecinas (plano) ---  z-1: cobblestone · z+0: chest[facing=north] · z+1: oak_log[axis=y]

O sea: el plano conocía el adoquín de al lado, el cofre y el poste, pero no la celda del medio. El agujero era
invisible para el pueblo y se quedaba para siempre.

**ARREGLO**: `VillageGenerator.cerrarHuecosDeLasCasas(level, center)` compara cada construcción de plantilla (las 4
casas, la iglesia y la herrería, con los **mismos sorteos deterministas** que `generate`) con **su plantilla**
(`template.save` → paleta + bloques, cacheado por id, porque la API pública solo sabe filtrar por un tipo de bloque) y
**rellena las celdas que la plantilla pide y el mundo tiene en aire**. Dos guardas: (a) si menos de la mitad de las
celdas de la plantilla coinciden con el mundo, esa construcción no es la de esa plantilla y **no se toca nada**; (b)
**lo que ya hay no se toca** —solo se rellena el aire—, que es lo que pedía el jugador con el cofre. Lo repuesto se
devuelve y `VillageManager` lo **apunta en el plano** (`Blueprint.conCelda`), para que el obrero lo mantenga.

**MEDIDO** con el arnés, en la misma corrida (antes → después):

    [Arnes] CASA hueco: 1427,120,1392=air 1427,121,1392=air … 1428,120,1392=chest · cofre[27 huecos] 2:2xapple
            7:1xapple 8:1xapple 13:1xbread 20:1xapple 21:2xgold_nugget 22:1xbread 23:1xgold_nugget 25:1xapple
    [Village] Casa …plains_medium_house_2 en 1426, 120, 1382: 2 hueco(s) de la plantilla tapados:
              1427,120,1392(cobblestone) 1427,121,1392(cobblestone)
    [Village] Casa …plains_weaponsmith_1 en 1417, 120, 1367: 2 hueco(s) de la plantilla tapados:
              1423,120,1368(lava) 1423,120,1369(lava)
    [Village] Aldea 2: 4 hueco(s) de las casas del juego tapados desde su plantilla
    [Arnes] CASA hueco: 1427,120,1392=cobblestone 1427,121,1392=cobblestone … 1428,120,1392=chest
            · cofre[27 huecos] 2:2xapple 7:1xapple 8:1xapple 13:1xbread 20:1xapple 21:2xgold_nugget
              22:1xbread 23:1xgold_nugget 25:1xapple

1. La pared se cierra con **adoquín** (lo que pide la plantilla).
2. **El cofre no se toca**: mismo sitio, mismos 27 huecos y **los mismos objetos** (el bloque nuevo va a la celda del
   hueco, al lado del cofre).
3. **De propina**: la misma comprobación encontró **2 huecos en la fragua de la herrería** (`plains_weaponsmith_1`):
   la **lava** del juego (`1423,120,1368` y `1423,120,1369`) que el guardado también había perdido.
4. **Idempotente**: cada casa canta sus huecos **una sola vez** en toda la corrida.

### 3b.66 Los vegetales que los granjeros dejaban en el suelo al cosechar

Lo reportó el jugador con captura de la huerta llena de vegetales tirados: *"los granjeros están dejando muchos
vegetales en el suelo cuando cosechan"*.

**MEDIDO con el arnés** (`MEDIR_HUERTA`, día fijo; 345 lecturas de los tres bancales en la corrida de antes): había
**patatas y zanahorias tiradas en los bancales en 502 lecturas**, con edades de hasta **4597 ticks (230 s)** y **3
zanahorias todavía en el suelo al final de la corrida** (a punto de desaparecer a los 5 min). Y con el zurrón de la
granjera **a medio llenar** (2 huecos libres de 8), es decir que no eran sólo "no me cabe": el propio **juego** deja
caer vegetales al suelo —su faena de granjero, `HarvestFarmland`, cosecha con `destroyBlock(..., true)`— y, como el
pueblo lleva al aldeano a lo suyo, nadie los pisaba para recogerlos. Y el **recolector no puede entrar** en las
parcelas: están cercadas y las compuertas de valla no las abre un aldeano (por eso el granjero tiene su propia tarea de
salir). De propina, el **betabel** no estaba en la lista blanca del recolector (el juego solo deja recoger
BETABEL_SEMILLAS, no el betabel), así que un betabel caído no lo cogía **nadie**.

**ARREGLO** (`VillagerFarmGoal` + `VillagerCollectGoal`):
- **El granjero barre su bancal** (`Tarea.RECOGER`): busca el objeto caído más cercano **dentro del bancal en el que
  está** —trigo, zanahoria, patata, betabel y las semillas que no le sobren—, va a por él y se lo guarda; y **sigue con
  el siguiente** mientras le quepa (barrido de una pasada). Lo que no le quepa se queda en el suelo (nunca se borra
  nada del pueblo).
- **No se cosecha lo que no le cabe** (`leCabeLaCosecha`, mirando `Block.getDrops` **antes** de romper la planta): si
  el fruto no cabe en el zurrón, el granjero se va **antes** a la despensa a descargar y la cosecha se queda en la
  planta. Si la despensa está llena y no le deja hueco, se apunta (`despensaNoTraga`) para no quedarse en un bucle de
  viajes: entonces cosecha y lo que sobra se cae, y lo barre él mismo.
- **El betabel entra en la lista blanca del recolector** (`esDelPueblo`): así lo que caiga fuera de los bancales también
  lo recoge el pueblo.

**MEDIDO, antes / después** (el arnés mira los tres bancales cada 2 s y apunta cada objeto del suelo con su **edad**):

    ANTES:   502 lecturas de vegetal en el suelo (345 barridos) · edad mediana 1077 ticks (54 s), máxima 4597 (230 s)
             · 434 lecturas por encima de 10 s y 340 por encima de 30 s · 3 zanahorias todavía ahí al final
    DESPUÉS:  63 lecturas (165 barridos: 0,38 items por barrido) · edad mediana 116 ticks (6 s), máxima 716 (36 s)
             · sólo 19 por encima de 10 s y 3 por encima de 30 s · y al final sólo SEMILLAS del compostero

O sea: el juego **sigue** soltando lo suyo —su faena de granjero (`HarvestFarmland`) cosecha con el
`destroyBlock(..., true)` de vanilla y no se puede quitar sin desmontar el cerebro entero: la `Brain` API solo tiene
`removeAllBehaviors`—, pero **ya no se queda nada**: lo que cae lo barre el granjero en segundos (la mediana pasa de
54 s a 6 s) en vez de pudrirse. Lo único que se ve caer a propósito son las **semillas** que le sobran (el abono del
compostero), y de ésas se encarga él al compostar o el recolector.

### 3b.67 «La aldea pasa hambre» con la despensa LLENA: la cría que crece muere de hambre (y el aviso mentía)

Lo reportó el jugador con **dos capturas**: la del cofre de la cocina **lleno de comida** (pan, trigo, zanahorias,
patatas, betabel, pescado) y el cartel del chat *"La aldea pasa hambre: la despensa esta vacia."*: *"me dice que la
aldea pasa hambre y que la despensa está vacía, sin embargo hay bastante comida"*.

**MEDIDO en el guardado del jugador y su log** (`New World (1)`, aldea 2, centro `1414,1414`, cota 120; con
`build/hambre_medida.py`, que vuelca la comida de cada aldea y la marca `DevilRpgUltimaComida` de cada aldeano):

    log  04:01:17  [Village] Un aldeano de la aldea 2 ha muerto de hambre (19 min sin comer)
                   Villager['Ubaldo (Sin oficio) Paseando'] en 1442.41,121,1439.93   <- el hueco de la escalera de la taberna
                   [CHAT] La aldea pasa hambre: la despensa esta vacia.
    log  03:54:42  [Village] Aldea 2: comida 64 puntos, 14 aldeanos, 29 camas, 11 raciones en el ultimo minuto
    log  04:01:00  [Village] El pescador: minecraft:cod (comida de la aldea 986)     <- la despensa, de verdad, tiene ~1000 puntos
    save         - Settlement aldea 2: Food 64 (el tope del contador), gameTime 104794
                 - los ONCE adultos de la aldea con la MISMA marca: DevilRpgUltimaComida = 104400 (o sea comidos)
                 - las dos crías con la marca del día que nacieron: Mauricio 86400, Nicasio 92400
                 - Ubaldo no está: murió

Los tres datos encajan en una sola cosa: **Ubaldo era una CRÍA**. Una cría **no gasta ración** (mama de la aldea) y
`pasarHambre` la **saltaba**, así que su marca de comida se estrenaba el día que nacía (la estrena `ultimaComida` la
primera vez que el latido la ve) y **no se volvía a tocar en toda su infancia**. El día que **creció** —vanilla, 20
min = 24000 ticks— dejó de ser cría, y en el **primer latido** ya llevaba 20 min "sin comer": pasó el umbral de
muerte (`STARVATION_DEATH_TICKS`, 10 min) y **murió en el acto**, con la despensa llena. Los 19 min del log son
exactamente eso (la marca se estrena hasta 10 s después de nacer).

Y encima el aviso **no miraba la despensa**: se cantaba con `algunaBocaSinComer` y decía *"la despensa esta vacia"*
siempre, también con 986 puntos dentro. Con razón el jugador dejó de creérselo.

**ARREGLO** (`VillageManager`):
- **A la cría se le da cuerda al reloj, no se la salta**: en `pasarHambre`, la cría pasa por `marcarComida` (su reloj
  **no corre** hasta que es adulta). Así, el día que crece empieza con la marca fresca (≤10 s) y come como
  cualquier adulto.
- **La comida no cuelga de un tick del mundo**: `repartirRaciones` salía de vacío si
  `gameTime % EAT_INTERVAL_TICKS != 0` ("las raciones se reparten en el latido del minuto"), así que **un solo tick
  perdido** —el jugador lejos, o el latido cortado con bichos dentro (I12/I46)— se llevaba por delante la comida de
  **todo** el pueblo. Ahora la pide **el aldeano que hace más tiempo que no come**: cuando ese cumple su intervalo,
  come el pueblo que esté esperando (el grupo sigue sincronizado porque una comida los marca a todos a la vez, así
  que se sigue pagando de una sola vez y sin regalar una hogaza por boca).
- **El aviso dice lo que se ha medido**: cuántas bocas se han quedado sin su ración y cuántos puntos quedan en la
  despensa; *"la despensa está vacía"* solo se dice si de verdad no hay ni un punto.

### 3b.68 Las partes del bancal «sin plantar»: el granjero no SEMBRABA nunca (y lo que se veía eran brotes)

Lo reportó el jugador con captura: *"¿por qué hay partes de la parcela que no tienen plantado nada? se supone que los
granjeros deben tener todas ocupadas"*.

**MEDIDO en su guardado** (`build/huerta_vacias.py`, celda a celda, aldea 2 cota 120):

    bancal 0 (1384,1428)  66/72 sembradas ·  8 celdas vacías: (0,1) (0,2) (0,6) (7,1) (8,2) (8,4) (8,5) (8,6)
    bancal 1 (1424,1418)  69/72 sembradas ·  5 celdas vacías: (0,0) (0,4) (0,6) (8,4) (8,6)
    bancal 2 (1386,1448)  71/72 sembradas ·  3 celdas vacías: (0,4) (0,6) (8,4)
    aldea 0 (que lleva más tiempo sin verse): 147 de 216 vacías (bancal 0: 70 de 72; bancal 1: 14 —13 vacías y una
    calva—; bancal 2: 63)

Las 16 celdas que faltan son `farmland` **con el hueco de arriba libre** (sembrables y sin nada), y **casi todas
caen en los carriles por los que se entra y se sale del bancal** (los dos extremos de la acequia y las columnas de
los lados). Y el resto de lo que se ve "vacío" en la captura son **cultivos de edad 0-1**: en el bancal 1 había
**30 de 69** en edad 0 o 1 (el 43%), que desde arriba son dos píxeles verdes y parecen tierra. Eso es lo normal (el
granjero **replanta cada celda que cosecha**) y no es un fallo.

**La causa del hueco de verdad es de ORDEN (otra vez).** El granjero tenía **dos** faenas de la tierra —cosechar lo
maduro y labrar la calva, que alternaban desde el arreglo de I25— y **sembrar iba DETRÁS de las dos**. Con tres
bancales (216 celdas) **siempre** hay algo maduro en alguno, así que el paso de `PLANTAR` no se alcanzaba **nunca**
(un bancal lleno nunca deja de tener algo maduro). Y las celdas se vacían solas: el **cerebro del aldeano** tiene su
propia faena de granjero (`HarvestFarmland`, ver 3b.66) y **solo replanta si lleva semillas**, y lo que se **pisa**
(I25) se vuelve a labrar pero **nadie lo siembra**. La parcela, entonces, **solo perdía celdas**: de ahí las 147
vacías de la aldea 0.

**ARREGLO** (`VillagerFarmGoal`): las **tres** faenas de la tierra **rotan** (`FAENAS_DE_LA_TIERRA` = cosechar,
labrar, **sembrar**), así que una celda vacía se recupera en la siguiente vuelta en vez de esperar a que no quede
nada maduro (que no pasa nunca). La siembra sigue exigiendo lo de siempre: semillas **en la mano** (si no las tiene,
el paso de recambios lo manda a la despensa) y el hueco de arriba **libre** (I11: no se arranca ningún cultivo).

### 3b.69 El portón del corral tapado por un FAROL: el ganadero encerrado (no llegaba ni a la taberna ni al almacén)

Lo reportó el jugador con captura: *"el ganadero quiere ir a la taberna y no puede, la única salida está obstruida
por una lámpara"*.

**MEDIDO en su guardado** (aldea 2, cota 120; `build/anexo_porton.py` y el apartado **F** que se añadió a
`tools/audita_aldea.py`):

    portón del corral     (1455,120,1414)  oak_fence_gate[facing=west, open=false]
    la cabeza del carril  (1455,121,1414) = lantern   (debajo: la propia hoja del portón)
    las otras cinco celdas del cruce (la hoja y las dos de al lado, dos capas): aire
    el PLANO también la pedía: palette[51] = lantern[hanging=false]
    la ganadera Obdulia, DENTRO del corral en (1460.9,120,1418.9), con el ALMACÉN aparcado
    (DevilRpgPuntoFallido = 1461,121,1434, que es el punto de apoyo del almacén)
    auditados los 14 portones de las tres aldeas: el ÚNICO tapado era ése

El aldeano mide **1,95** y un farol tiene **caja de colisión**, así que al cruzar no le cabía el cuerpo en la celda
de la hoja + la de encima: la **navegación no le encontraba camino** (y sin acercarse, el `VillageGateGoal` tampoco
se lo abría) y se quedaba **encerrada en el corral**. El farol venía del layout **viejo** de las luces de la cerca,
que ponía uno en el **medio de cada lado** de la valla —y el medio del lado oeste ES el portón—; el código de hoy no
lo pone, pero tampoco lo quitaba, y la autocomprobación de faroles no lo canta (ese farol *sí* tiene apoyo: el
problema es que el apoyo es la puerta).

**ARREGLO** (I54):
- `farolSobreElPoste` **no pone un farol sobre una puerta de valla** (`FenceGateBlock`).
- `despejarElHuecoDeLosPortones` (idempotente, en el latido) **muda el farol del carril a un poste de al lado** —no
  lo tira: la luz del pueblo se queda donde hacía falta— y devuelve las celdas despejadas.
- Esas celdas **salen del PLANO** (`Blueprint.sinCelda`), porque si no el obrero repondría el farol en la pasada
  siguiente.
- Los portones salen de **una sola lista** (`VillageGenerator.todosLosPortones`): la usan el goal que los abre, el
  despeje y la auditoría.
- Y de propina, **I6**: `posarFarolesFlotantes` quitaba y volvía a poner los **doce faroles del corral en cada
  latido** (el log lo cantaba cada 10 s, para siempre); ahora un farol **a un bloque del apoyo y posado** no se toca.

**Comprobado con la auditoría versionada**: `python tools\audita_aldea.py` canta el portón tapado (aldea 2: 1
portón). El del **gallinero** no se audita: es un hueco de **un bloque** a propósito (los pollos pasan, los aldeanos
no).

### 3b.70 Zacarías sin cama, la milicia sin armadura y el equipo que no se veía

Tres reportes del jugador en la misma sesión: *"Zacarías según va a dormir pero está afuera y no toma cama"*, *"un
guardia luego luego fue asesinado por un zombie y en el cofre había espada"* + *"revisa que el herrero
correspondiente esté haciendo armaduras y armas y que los guardias se estén equipando"* y *"cambia el render de los
guardias para que se vea que están usando armadura y las armas que llevan"*.

**MEDIDO en su guardado y su log** (aldea 2, cota 120; `build/aldeanos_equipo.py`, que vuelca cada aldeano con su
cama, su posición, si duerme y **todo su inventario**):

```
Zacarias (Sin oficio) | "Yendo a dormir"  pos (1446,120,1427)  NO duerme  cama(HOME)=(1446,125,1429)
                                          -> su cama es de la POSADA: la misma X/Z, CINCO bloques más arriba
log: 9474201f… no llega a su cama por el camino del juego: se le da 1446,125,1429
                 y se le mandara a 1446,125,1427 para acostarle       (el mismo aldeano, en bucle)
log: El herrero de herramientas (Josefa): "Hizo 4 palos" / "Aserro un tronco en 4 tablones"  (toda la sesión)
almacén (1461,121,1433): 19 leather, 8 iron_ingot, iron_sword x1, arrow x2 … y NINGUNA pieza de armadura
log: 10:54:36 muere Bibiana (Guardia espadachín) · 10:54:41 Isidoro (Guardia) cae con el ZombieVillager de Bibiana
```

Los tres son fallos distintos:

1. **La cama (I57)**: su cama estaba en **otra planta** y la *celda de espera* que le calculó el reparto también
   (`1446,125,1427`), así que se le mandaba arriba, el planificador lo dejaba **debajo** y se quedaba plantado en la
   calle con la etiqueta "Yendo a dormir". Y nadie le cambiaba la cama: el reparto solo da cama al que **no tiene**
   `HOME`, así que el bucle *reclamar → no llegar → vanilla le borra el HOME a los 60 s → reclamar la misma* era
   eterno. **Arreglo**: la celda de espera del último recurso tiene que estar a **≤3 bloques y en su misma planta**
   (si no, esa cama no es para él) y el aldeano que **no se acerca** a su cama en **6 latidos** la **suelta** (con su
   ticket) y se le **aparca** para que el reparto le dé otra que sí alcance.
2. **La armadura (I56)**: el herrero de **herramientas** era el único que sabía hacer armadura, y su
   `elegirReceta` era una cascada con la fabricación **al final** — detrás de aserrar troncos y hacer palos, cuyos
   objetivos (32 tablones, 64 palos) **se los come el otro herrero**, así que **nunca** llegaba a la armadura. Con 19
   de cuero y 8 lingotes en el almacén, la milicia salió a pelear **sin nada puesto** y cayó al primer zombi.
   **Arreglo**: el herrero **alterna** transformación y fabricación (`turnoDeFabricar`); la armadura sale al mismo
   ritmo (y el log ya dice *"Hizo un casco de cuero"*, etc.).
3. **El render (I55)**: el modelo del guardia (cuerpo de jugador + cabeza de aldeano, con las capas de vanilla de
   **armadura** y de **objeto en mano**) ya estaba hecho… y **no se veía**: `esGuardia` leía los **datos
   persistentes**, que son **solo del servidor**, así que en el cliente daba `false` para todos y todos los guardias
   se dibujaban con el modelo de vanilla, **sin armadura y sin arma** (el equipo sí viajaba: lo manda `ServerEntity`
   para cualquier `LivingEntity`). **Arreglo**: la marca se **espeja** en una attachment **sincronizada**
   (`ModCapabilities.VILLAGER_GUARD`) que se escribe **solo cuando cambia**.

**Pendiente de ver en juego** (hace falta reiniciar): que el guardia se vea con su armadura y su espada/escudo, y
que Zacarías acabe durmiendo en una cama de la planta baja.

> **Y el mismo día, con el arreglo ya cargado** (el jugador: *"el guardia se quedó bloqueado… dice que va rumbo al
> almacén pero no se mueve"*, con la captura dentro de la posada): **Mauricio** (guardia) en `(1455,125,1435)` —la
> **segunda planta** de la taberna— sin moverse, su cama en `(1452,125,1441)` (arriba) y la celda de espera de otra
> cama de la posada (`1446,125,1429`) calculada en **`1446,125,1427`**: **fuera del edificio**, al otro lado del muro
> (y los aldeanos apareciendo en `1446,120,1427`, la calle de abajo). Las dos causas de la celda de espera —**un muro
> cuenta como "se ve la cama"** y **no se comprobaba que en esa celda se pueda estar de pie**— arregladas (ver la
> ampliación de I57): `hayVistaLibre` solo acepta el objetivo o **su otra mitad** (una cama), y la celda de espera
> pasa por `celdaLibreParaAcostarse` (aire, hueco de cabeza y suelo firme). Y el *síntoma* (quieto con la etiqueta
> del mod puesta) es **I5**: de noche el cerebro le escribe el destino a su `HOME` y pisa el del goal.

### 3b.71 El sello teletransportaba a los intrusos en el acto (y la defensa no se veía)

El jugador: *"llegaron unos zombies agresivos durante el día a la aldea, pero no pasó mucho tiempo y fueron
teletransportados a fuera. Esto se ve antinatural. ¿Por qué sucede? Corrígelo pero que no rompa otras mecánicas
relacionadas con las hordas o zombies agresivos"*.

**MEDIDO en su log** (aldea 2, protegida): el sello expulsaba **en cada latido** (`el sello ha expulsado a 1
hostil(es) que estaban dentro`, 12:08:06 → 12:22:56, cada 10 s; también 7 y 2 de una vez) → un agresivo que entraba
andando desaparecía **antes del latido siguiente**, sin que la milicia lo tocara.

**Por qué pasaba**: la expulsión se añadió (etapa H) porque con un hostil dentro `hayEnemigosDentro` **corta el
latido entero** (I12) y el pueblo se quedaba congelado, y el aura del sello solo corta los **spawns**, no a los que ya
están dentro. El problema no era la red de seguridad, era que **actuaba primero**: a los 10 s el bicho ya no estaba.

**ARREGLO** (I59):
- **Espera de 2 min** (`SELLO_ANTES_DE_EXPULSAR_TICKS`) antes de rechazar a nadie: en ese rato defiende el **pueblo**.
- **La milicia persigue a cualquier monstruo dentro del recinto aunque esté lejos** (`VillagerGuardGoal.buscarEnemigo`
  mira todo el recinto si no hay nadie en sus 16 bloques; el aldeano-zombi queda fuera de esa búsqueda larga para no
  mandar la guardia sobre una curación en marcha).
- **El rechazo se lee**: partículas, chillido de sculk y aviso al jugador (*"El sello de la aldea ha rechazado a los
  intrusos."*), en vez de un bicho que se esfuma.
- **Lo demás, intacto**: con `isUnderAttack` (asedio del jugador **o horda del mundo**) **no se expulsa a nadie**, y
  no se toca ni el spawn, ni el escalado, ni el botín de los zombis agresivos.
- El reloj es **por aldea y por bicho** y se olvida cuando el bicho sale o muere.

### 3b.72 La muralla dañada que nadie reparaba (el panorama no la tenía) y el recolector de flojo

El jugador: *"la aldea ha tenido daños en su muralla y nadie ha ido a repararlo. El recolector está de flojo y así ha
estado durante todo el día"*.

**MEDIDO en su guardado** (`build/obras_pendientes.py`, nuevo): de las **7.296** celdas del plano de la aldea 2
**una** estaba pendiente (un farol de la taberna que el propio pueblo retira en cada latido) → **ningún** agujero de
la muralla estaba en el plano, así que el obrero **no tenía nada que reponer** allí: `findRepairTarget` recorre el
plano y **lo que no está en el plano no existe para el pueblo** (I8/I50). El plano de una aldea migrada es un
**escaneo** del mundo: si la muralla ya estaba dañada al capturarlo, los agujeros quedan fuera **para siempre**. Y la
lista de aldeanos marcados como obrero lo confirmaba: los tres eran aldeanos **con oficio** (herrero de armas,
leñador, pescador), que reparan a prioridad **5** —la última—, y el **recolector** (Anselmo, holgazán) estaba
**Paseando** y **sin** la marca.

**ARREGLO** (I60):
- **Migración 67**: el muro se reconstruye (`rehacerMuro`, que ya corría en el bloque de migración) y el plano se
  **tira para volver a capturarlo** con la muralla entera; a partir de ahí el obrero mantiene lo que se rompa.
- **El constructor es el aldeano SIN FAENA** (el recolector): `puedeSerObrero` ya no excluye al holgazán y el reparto
  lo elige **el primero** (su reparación va a prioridad 3, por delante de todo).
- **Lo que el pueblo retira, fuera del plano**: el farol de encima del primer escalón de la taberna era la única
  celda pendiente de la aldea 2 (tira y afloja cada 10 s con el obrero); `quitarElFarolDeLaEscalera` devuelve la celda
  y el latido la borra del plano.
- **Límite conocido**: el obrero trabaja a **+5/−6** de la cota, así que los **tejados** quedan fuera (aldea 0: 27
  losas de la placa del tejado a **+11**, pendientes).

### 3b.73 El golem de hierro dentro de la casa (el 3.er piso)

El jugador: *"los golems no deben spawnear en el 3er piso"*.

**MEDIDO en su guardado**: de los **5** golems de hierro de las tres aldeas, **uno** estaba en
`(1446.8,131,1430.4)` de la aldea 2 → **+11** sobre la cota (el **desván de la taberna**), a **4** bloques del
aldeano que lo había sumado (Onofre, el cocinero, dormía en `(1442,131,1432)`).

**Por qué**: el golem de vanilla lo **suma un aldeano** al dar el aviso de alarma y lo hace **donde está él** → si
el aldeano está en la posada o en el desván, el golem sale **dentro de la casa** (no defiende y se queda arriba). Y
el golem del **mod** se colocaba con `spawnY` → `groundY`, que en una columna con una construcción devuelve **el
tejado**.

**ARREGLO** (I61): al **entrar al mundo**, un golem que aparezca **dentro de una aldea y por encima del suelo** se
**baja a una casilla libre a la cota** (suelo firme y seco, dos celdas libres) al lado de la plaza —vale para las dos
vías, la de vanilla y la del mod—, y el **latido** lo repite para los que **ya** estaban en alto (el del guardado se
baja en la primera pasada). Idempotente: a un golem a nivel del suelo no se le toca.

### 3b.74 «La constante TRAZADO no tiene la choza del pescador» (los sitios, en un solo sitio)

Lo preguntó el jugador **revisando el código**: *"estoy viendo que la constante `TRAZADO` en `VillageGenerator` no
tiene la choza del pescador, ¿por qué?"*.

**La respuesta**: `TRAZADO` no es "el trazado de la aldea" (aunque el nombre y su comentario lo parecían): es **la
tabla de sitios que lee `trazado(center, i)`**, y el código solo usa los índices **0..6** (casas, iglesia, taller,
barraca). La **pesquera** llegó después, en la **etapa G** (*"el pescador tendrá su edificio y su lago más
adelante"*, que fue petición suya) y se hizo con **su propia copia** de las coordenadas: `PESQUERA = {20, 44}`. Y al
revés, las **tres parcelas de la granja** estaban en las **dos** tablas —`TRAZADO` (filas 7-9, que **nadie leía**) y
`FARM_PLOTS` (la de verdad)—. Sin consecuencia en el mundo (el derribo del trazado viejo usa sus propias constantes,
y nada recorre `TRAZADO` para reservar sitios), pero es exactamente el patrón que **I4** prohíbe y una **trampa para
el siguiente lector**.

**ARREGLO** (solo código, **ni un bloque del mundo cambia**):
- El sitio de la pesquera vive **solo** en `TRAZADO[7]` (con su lago y su caseta) y `PESQUERA = TRAZADO[7]` lo lee.
- Se quitaron las **tres filas muertas** de las parcelas (su sitio es `FARM_PLOTS`).
- El comentario de la tabla dice **qué es** (los índices que usa `trazado()`), **qué hay en cada índice** y **dónde
  vive cada sitio que no está en ella**: `FARM_PLOTS` (huerta), `VillageStorage.OFFSET` (almacén), `ANEXO_DX`
  (corral), `baseDeLaTaberna` (taberna), `PUNTOS_DE_LA_ARBOLEDA` (arboleda).

### 3b.75 La banda de troncos que separa las dos plantas de la taberna (de diseño, en los cuatro lados)

Lo pidió el jugador con captura, y **se la había puesto él a mano en un lado**: *"estaría bien que la taberna tenga
logs de separación entre un piso y otro tal como se muestra en la imagen… el log es más claro que el log de cada
pilar para que lo distingas. Estaría bien que estuviera desde el diseño en todos los lados"*.

**MEDIDO en su guardado**: su banda estaba en la **vuelta del forjado** —`oak_log` a `y=124` (la cota del forjado de
la posada) en el muro sur, `1438..1449`— y el resto de la vuelta seguía en los **tablones oscuros** del diseño
(`dark_oak_planks`). O sea: la celda correcta era la del forjado, y el material que eligió, **roble claro**
(`oak_log`) contra los postes de **roble oscuro** del entramado.

**ARREGLO** (migración **68**):
- **La construye el diseño**: en `forjadoDeLaPosada`, la **vuelta** del forjado (la línea de los muros,
  `enLaVueltaDelForjado`) va en **`oak_log`** y el resto del forjado sigue en tablones oscuros. Como el forjado se
  coloca en **todos** los lados, la banda sale **en los cuatro** y de una pieza.
- **Y se repone en las tabernas ya construidas**: `ponerLaBandaDeLaTaberna` (idempotente, en el latido) cambia
  **solo** los tablones del diseño de esa vuelta —lo que el jugador tenga puesto se queda: en su partida, su banda
  del muro sur— y canta en el log cuántos troncos ha puesto.
- La chimenea (pegada al muro norte) no se toca: su celda no es un tablón del diseño.

*No se pudo medir con el arnés (el jugador tenía el juego abierto y tiene cogidos el jar de NeoForge y el guardado);
se comprueba en el log al reiniciar (`banda de separacion entre plantas puesta (N tronco(s) de roble en la vuelta del
forjado)`) y a ojo en los cuatro lados.*

### 3b.76 La cara de los guardias, la del aldeano

Lo pidió el jugador: *"para el rostro de los guardias podrías poner la textura de los aldeanos para que se vea
coherente?"*.

**Cómo está hecho el aldeano de vanilla** (leído en sus texturas): el modelo lleva la textura **base**
(`textures/entity/villager/villager.png`) y **encima** la de su **tipo** (`type/<bioma>.png`), que trae la piel y la
**banda/gorro** del bioma. La aldea del jugador es de **sabana** (lo dice su pantalla de depuración: `Biome:
minecraft:savanna`), así que se compone **base + `type/savanna.png`**.

**El problema de encajarlo**: el modelo del guardia es el del **jugador** (`PlayerModel` + nariz, para poder
enseñarle la armadura con las capas de vanilla), y su cabeza mide **8** de alto; la del aldeano mide **10**
(`8x10x8` en `texOffs(0,0)`, con la nariz `2x4x2` en `(24,0)`). Las caras de un mapa de texturas dependen del
tamaño de la caja, así que no vale copiar el bloque: hay que copiar **cara por cara**.

**Alineación** (lo que decide si la cara "cae bien"): la cabeza del aldeano va de `y=-10` a `0` y la del jugador de
`-8` a `0`; como las dos acaban abajo en el mismo sitio, de las caras del aldeano se coge la franja de **abajo**
(filas **9..17** de su cara de 10, que deja los ojos a la misma **proporción** de la cabeza: los ojos del aldeano
están en la fila 14 y los del guardia en la 13 —medido con `build/diag/ojos.py`, que busca los píxeles verdes—).
El **gorro** (caja de 12 de alto, de `-10` a `+2`, en `texOffs(32,0)`) se coge de las filas **9..17** de cada cara, y
la **nariz** de las filas 2..5 de la del aldeano. El gorro **ya existía** en el modelo del jugador (`PlayerModel`
crea la caja `hat` en `(32,0)`) y en las texturas del guardia estaba **transparente**: pintándola, se ve (y queda
tapada por el casco cuando el guardia lleva armadura).

**Lo que se toca**: solo las texturas `village_guard_swordsman.png` y `village_guard_archer.png` (la cabeza, el
gorro y la nariz). El **cuerpo y los brazos siguen siendo el uniforme** del guardia (las regiones del mapa del
jugador no se tocan), así que las capas de **armadura** y de **objeto en mano** siguen funcionando igual. El script
que lo hace (y el compuesto del aldeano para comparar) está en `build/diag/cara_guardia.ps1` (ignorado), y las
texturas originales quedan respaldadas en `build/diag/*.antes`.

**Pendiente de ver en juego** (hace falta recompilar y reiniciar): el guardia con la cara de aldeano de sabana.
Si en vez de una cara fija se quiere que cada guardia lleve **la de su propia variante** (cada aldeano tiene la suya
en el guardado), eso ya pide una **capa de render** con el modelo del aldeano y la textura de su tipo.

### 3b.77 La noche que mataron a media aldea: el sello por bicho, y la milicia que aprende

El jugador, tres cosas de una: *(1)* *"cuando llegó la noche aparecieron así de la nada zombies agresivos que
mataron a media aldea; una vez que está la barrera no puede spawnear NADA dentro de la villa, inclusive los zombies
que aparecen alrededor del jugador"*, *(2)* *"los zombies que hacen un asalto spawnean alrededor de la villa y buscan
ir al centro arrasando todo a su paso (así está ahorita, confírmame por favor)"* y *(3)* *"los guardias se van
haciendo más fuertes y con más salud conforme van matando enemigos, el tope es prácticamente tan fuerte como el
zombie agresivo más fuerte… la progresión es gradual"*.

**MEDIDO (su guardado y su log)**:
- El sello **rechazó 9 de 9 anclas** (`posicion … dentro de aldea protegida: no se spawnea`) y en el guardado **no
  hay ni un monstruo dentro del recinto**: los de alrededor están **todos fuera**, a **62-74** bloques del centro
  (una docena de esqueletos y zombis pegados a la muralla). La barrera **sí** corta los spawns de dentro.
- **Pero había un agujero real**: `CustomSpawner` comprobaba el sello **solo para el ancla** y las demás posiciones
  del grupo se **sorteaban otra vez** (`findSpawnPosition`) sin comprobación → un bicho del grupo podía aparecer
  dentro. **Tapado** (y la altura del sello, con la cota, I63).
- Y las muertes (`Onofre` en el desván, `Quintin` el guardia en la posada) dicen lo otro: **entraron andando**. El
  sello no levanta un muro: impide que **aparezcan** dentro; de **defender** se encarga la milicia.

**(2) CONFIRMADO, así está**: `spawnWave` siembra la ola **alrededor** de la aldea (entre `FENCE_RADIUS+3` y
`FENCE_RADIUS+11` = 65..73 bloques del centro) y cada asediador lleva `setVillageCenter(centro)`: el goal
`MarzoAlCentroGoal` los hace **marchar al centro** y, cuando el camino se cierra, **rompen hacia él** (túnel lento
con presupuesto de 40 bloques y puentes), **arrasando el terreno a su paso pero nunca dentro de la aldea**:
`NO_TOCAR_LA_ALDEA = FENCE_RADIUS + 2` es el radio dentro del cual no rompen ni construyen (solo tienen que llegar
al perímetro).

**(3) HECHO** (I62): la milicia **aprende matando**. Cada `Monster` que muere a manos de un aldeano de la guardia
(cuenta el **dueño del disparo**, así que también los arqueros) le suma una matanza; sus atributos se **recalculan**
desde ese contador (idempotente, en el latido y al matar). El **tope sale del perfil del zombie** (`10 * (1+3,0) *
(1+0,8)` = **72 de vida y 5,04 de daño**), la progresión es **gradual** (24 matanzas al tope, un nivel cada 3) y el
nivel se ve en su etiqueta: **`Guardia espadachín · nv 3`**. Con la espada de hierro del pueblo, un guardia de tope
pega **9,04**: puede con el zombie más fuerte. Tabla completa en I62.

### 3b.78 El clérigo, sanador de la aldea

Lo pidió el jugador: *"el clérigo podría tener como task el curar a los soldados; que sea una especie de sanador"*.

El clérigo ya tenía su oficio en el mod (hace pociones de verdad en su soporte con lo que junta el pueblo, I23-ish)
pero **no curaba a nadie**: la clase prometía en su comentario "y, cuando se pueda, se la dará a la guardia", y eso
**no** es lo que hay que hacer (un aldeano no bebe pociones). Ahora **sana él**.

**HECHO** (I64): con un **soldado herido** en el término (y si no, un vecino; herido = **por debajo del 75 %** y a
menos de **32** bloques), el clérigo va a por él, se planta a su lado y le devuelve **8 de vida** con **corazones** y
el sonido de su oficio, con su aviso en el log y en su etiqueta (`Curando a Quintin`), y descansa **8 s** antes de
volver a curar. La ronda del sanador **manda sobre el soporte y sobre el viaje al agua**, y de noche también sale
**si la aldea está en asalto** —que es cuando los guardias se hieren—; sin asalto duerme como los demás.
*(Encaja con lo de la noche de 3b.77: la milicia aguanta y el clérigo la mantiene en pie.)*

**Pendiente de ver en juego** (el jugador tenía el juego abierto y no se pudo correr el arnés): al reiniciar, con un
guardia herido tiene que salir `El clerigo cura a … (a -> b de N de vida)` en el log y verse los corazones.

### 3b.79 Los minions van a por lo que el jugador marca (y el barrido que faltaba)

Lo pidió el jugador: *"si yo, jugador, llego a atacar alguno, o si alguno de los poderes atacan (como la enfermedad
que genera el hongo y el liquen cuando se avienta a alguna entidad), esta se vuelve enemigo y se debe atacar por los
minions"*.

Dos mitades (I79 e I81):

- **Los poderes suyos solo dañan a enemigos** (I79): el hongo que explota y el liquen maldito tocaban a cualquiera que
  pasara —incluido el propio jugador y sus bichos—. Ahora el daño va **solo** a un `Enemy` o a un `Mob` que ya esté
  peleando (`getTarget() != null`), y la explosión del hongo es `ExplosionInteraction.NONE` con un reparto de daño a
  mano, para que no reviente la aldea ni a los vecinos.
- **Los minions rematan** (I81): el neutral que el jugador (o un poder suyo) haya tocado pasa a ser objetivo de los
  bichos del jugador. El predicado de los que tienen objetivo propio llevaba `!esCriaturaPacificaONeutral(entity)` **a
  secas**: el neutral se descartaba antes de mirar la pelea. Ahora lleva la coletilla
  `|| ITamableEntity.elDuenoLeEstaAtacando(this.getOwner(), entity)` (ayudante compartido: mira `owner.getLastHurtMob()`
  y `entity.getLastHurtByMob()`, así que valen manos **y** poderes).

**Los dos fallos de esta ronda salieron de escribir a mano la lista de minions**: primero quedó fuera el **lobo**
(*"mis lobos no la atacan"*; y el liquen, que daña por **efecto**, necesitaba marcar él mismo a la víctima con
`setLastHurtMob`), y después, al preguntar el jugador *"el oso también, ¿lo checaste?"*, el **barrido con `grep`** de
todos los `esCriaturaPacificaONeutral(` encontró **dos sitios más** en `SunflowerShulker` (uno de ellos, dentro de un
lambda pasado a `super(…)` de una clase **estática**, donde `this` no existe: ahí va el parámetro del constructor).
**El oso sí estaba** desde el primer día. La regla del barrido y la lista de aciertos, en I81.

### 3b.80 La aldea que cayó a espaldas del jugador (el asedio se PAUSA, no se pierde)

El jugador, con una aldea entera perdida: *"Me alejé de la aldea unos cientos de cubos, volando y regresé antes de
que nocheciera y cuando regresé ya estaba abandonada. Eso es un bug enorme!!"* — y luego precisó el mecanismo:
*"cuando la aldea está marcada como que fue invadida por zombis la primera vez que se llega, cambia a abandonada:
todos los aldeanos mueren y las construcciones quedan destruidas con telarañas. El bug aquí es que yo me alejé de la
aldea y cuando regresé se disparó esta función de aldea abandonada cuando no tendría que haber pasado"*.

**Medido en su guardado**: `Fallen = [1]`, y la **aldea 1** (990,990) cayó el **17-sep-2026 a las 18:50:09**
(`Aldea 1 queda en ruinas: 1483 bloques cambiados`), con el chat del asedio **clásico**. Su última posición al cerrar
el juego (21-sep, 21:02) es **(975, 110, 997)**: dentro de esas ruinas. Su aldea viva (la 2) sigue entera
(`health=18`, `food=64`, 18 aldeanos contados en el guardado) y esa sesión no tiene ni una caída ni una muerte. La
aldea caída conserva **9 aldeanos vivos** (`ruin()` no mata a nadie: quedó escrita como caída con su gente dentro).

**Causa**: `VillageManager.tick` sumaba al reloj del asedio (`d.tickTicks++`) **siempre**, sin mirar dónde estaba el
jugador. Al irse con los asediadores **dentro** del muro el tiempo seguía corriendo, los zombis descargados no
morían, y **en el mismo tick de volver** se cargaban otra vez, contaban como "dentro del perímetro" y el asedio se
resolvía como perdido: `fallVillage` → `ruin()` (aire, telarañas y piedra mohosa).

**HECHO** (I86): el reloj **solo corre con el jugador en la aldea** (128 bloques) —si se va, el asedio queda `EN
PAUSA` y se dice en el log—, al volver se le da **el tiempo entero otra vez**, y las **hordas del mundo** tampoco
pueden tumbar una aldea sin nadie delante. Pendiente de ver en juego (el jugador cerró el juego al informar).

### 3b.81 Las aldeas con NOMBRE, el Diario del Invocado y la dirección que no se regala

Lo pidió el jugador, y venía de un problema real suyo: *"el problema es que no tengo las coordenadas para poder
regresar"*. Su encargo, entero, en I87.

**HECHO**:

- **Nombre por aldea**, determinista por índice (`VillageNames`): la misma aldea se llama igual en servidor y cliente
  sin sincronizar nada. Tabla **del jugador**: 30 nombres con su lore en tres bloques (bosque → espíritus y animales →
  oscuras), en ese orden, que encaja con la escalada del mod; a partir de la 30 se repiten con numeral.
- **La barra de ALDEA** (`VillageHudOverlay`, antes `ObjectiveHudOverlay`): sin revelar **no hay barra**; revelada y sin
  visitar, `Aldea (1.234 m) →`; visitada, `Aldea de Valdehierro (12 m) ↑`.
- **Descubrir es ENTRAR** (radio 24): al entrar se apunta por jugador en la capability (`aldeasVisitadas`), sale *"Has
  llegado a …"* y el nombre aparece en la barra y en el Diario.
- **Quién revela la dirección** (`aldeasReveladas`): la **piedra de invocación** (el "inicio de la misión", y el seguro
  contra perderse: hay que volver al círculo ritual) y, como camino normal, **el clérigo** de la aldea que vence su
  asedio, que lo dice **con su nombre**.
- **Si la aldea cae**: ni barra ni distancia — solo el aviso con el **rumbo** en el chat, tal como lo pidió. La siguiente
  se revela al encontrarla y entrar.
- **Diario del Invocado** (`DiarioDelInvocadoItem`, lo entrega la piedra): lista las aldeas descubiertas con **nombre,
  coordenadas, estado** y **distancia y rumbo** desde donde estás. No se gasta y no se queda desfasado.
- **Siembra en partidas ya empezadas**: las aldeas que el mundo ya resolvió se apuntan una vez como visitadas y
  reveladas, para que su Diario no nazca vacío.

Sin migración (el descubrimiento es estado del jugador, no del mundo). Compila y pasa el lint, y **medido con el
arnés** (`MEDIR_ALDEAS`, **tres** corridas sobre una copia de su partida; ver `tools/arnes/medidas-aldeas.txt`): el
guardado viejo carga sin excepciones, la siembra apunta las 3 aldeas ya resueltas (`Diario del Invocado sembrado para
…: 3 aldea(s)`), la aldea que **no** está en el guardado sale **OCULTA** en la barra y pasa a `Aldea  (1.234 m) →` en
cuanto un revelado se la señala, el **clérigo** revela la siguiente al vencer (idempotente), la **piedra** revela el
objetivo actual, y el Diario lista nombre, coordenadas, estado y rumbo (`Aldea de Fuenteclara (990, 990) — EN RUINAS ·
a 599 m hacia el noreste`), sin cambiar al revelar (solo lista lo **visitado**).

La medida **cazó un fallo de rastreo**: la piedra no revelaba nada (y en silencio) con un jugador sin ancla —el de pega
del arnés—; ahora deja un `WARN` con el motivo.

Y el **estado de la aldea** pasa a tener **una sola definición** (`VillageManager.estadoDeLaAldea`: EN RUINAS / en
asedio / a salvo con el sello / viva sin socorrer), que usan el Diario y el arnés: en la última corrida las líneas del
Diario dan `EN RUINAS` para la aldea 1 y `a salvo, con el sello puesto` para la 0 y la 2.

Queda **pendiente**: la **tabla de nombres** definitiva (la pasa el jugador) y, en el juego abierto, la barra dibujada
(es del cliente) y el clic en la piedra (se mide el método que corre el clic). El **asedio de principio a fin** no se
puede medir headless: el reloj solo corre con el jugador en la lista del servidor (I86) y un `FakePlayer` no está en
ella → EN PAUSA y la ola no sale (medido: `hayAsedio(3)=true`, `agresivos=0`). Ese intento dejó medido el cuarto
estado, **`en asedio`**, y que el Diario lo enseña.

## 3c) Iteración 2 — GUARIDAS — CERRADA ✅

Focos de enemigos esparcidos por el mundo que **cambian el terreno** y que el jugador puede **asaltar**.

- **Posición determinista**: `LairManager.preGenerate(level, objectiveIndex, target)` genera una guarida
  por objetivo, **desplazada 72–94 bloques** del objetivo con un ángulo derivado del índice (semilla fija),
  así el jugador la encuentra al explorar y server/cliente coinciden sin sincronizar. Se pre-genera junto a
  la aldea cuando el jugador se acerca (radio 140). El mínimo **no es arbitrario**: la guarida es una
  plataforma que llega a ~28 bloques de su centro (corral incluido) y la aldea nivela hasta 31 y escalona
  hasta 41, así que a menos distancia las dos obras se comerían el terreno.
- **Terreno cambiado** (`LairGenerator`): **una sola plataforma** de radio 10 que incluye la guarida, un
  **corredor** y el **corral**, nivelados **al mismo nivel** (mediana del terreno de toda la huella, vía
  `platformDistance()`); eso garantiza que el corral quede exactamente a la altura del suelo de la guarida.
  Superficie: **sculk** en el núcleo corrupto (radio 8) y **tierra muerta** alrededor con **venas de sculk**,
  más **espinas de hueso** en el anillo exterior y **tótems con calaveras** en las diagonales. *(Las
  telarañas se quitaron: ensuciaban el santuario sin aportar nada.)* En tierra lleva un **talud exterior**
  (meseta natural); si cae sobre **agua**, se construye sobre una **plataforma al nivel del agua** con base
  cónica (nunca queda sumergida), decidido con la **misma regla que la aldea**
  (`VillageGenerator.waterSurfaceForArea`). Y se genera **en la posición determinista**, sin moverla: antes
  llamaba a un `findLand` que desplazaba el centro hasta 24 bloques buscando tierra seca, y en la costa eso
  dejaba el centro en tierra con la huella casi toda en el mar, así que la guarida se nivelaba al fondo
  marino y quedaba **sumergida**.
- **Santuario defendido** (el núcleo no se asalta impunemente). Tres capas:
  1. **Foso perimetral** (`buildMoat`): anillo de radio 3.5–6 con **4 bloques de caída** y el **fondo de
     arena de almas**. **No lleva magma a propósito**: un foso es un hueco de aire y **el sculk no cruza un
     hueco**, así que el fondo tiene que ser **sólido y convertible** — la infección baja por el subsuelo de
     la isla, cruza el fondo y sube por el otro lado. Con magma en el fondo eso era imposible y el foso
     actuaba de barrera que contenía la mancha. Se cruza andando por **cuatro puentes de hueso de 1 de
     ancho** en los cardinales, marcados con antorcha de alma y losa de obsidiana llorosa al tocar la isla.
     Las espinas de hueso van **desfasadas media muesca** para que ninguna tape un puente, y los 4 bloques
     de caída son más de lo que un mob baja por sí solo (`maxFallDistance` = 3), así que no se meten dentro
     y se quedan atrapados.
  2. **Sello del guardián** (`SculkSealBlock`, bloque `sculk_seal`): caja **3×3×3 inquebrantable**
     (dureza −1, como la piedra base; sin objeto, sin loot table y fuera del inventario creativo) que
     **blinda el núcleo**. El guardián (el cultivador) **aparece una vez, en cuanto la guarida se activa, y
     ahí se queda hasta que lo mates**: no muere ni se retira solo, y **no despawna por distancia**
     (`removeWhenFarAway() → false`). La única señal que rompe el sello es un **evento de muerte**:
     `SculkCultivatorEntity.die()` avisa a `LairManager.onGuardianKilled()`. Es clave que sea un evento y no
     un sondeo: deducirlo de "no hay ningún cultivador cerca" era un error, porque esa ausencia también
     significa *todavía no ha aparecido* o *se ha ido* — con esa deducción el sello se caía solo y el núcleo
     aparecía indefenso sin haber matado a nadie.
     **Si matas al guardián y no rompes el núcleo**, la guarida cría un <b>guardián de relevo</b> y <b>vuelve
     a sellar el núcleo</b> tras **3 min de guarida activa** (`GUARDIAN_RESPAWN_TICKS`); cada muerte programa
     su propio relevo, así que una guarida viva nunca se queda sin guardián. El relevo sale con **partículas
     de alma de sculk (90) + `sculk_charge_pop` (30) y sonido de shrieker ALREDEDOR DEL GUARDIÁN** (no del
     núcleo, para que se note quién ha vuelto) y aviso en el chat. La caja de sellos no puede levantarse con
     alguien dentro del círculo —lo dejaría encerrado y asfixiándose—, así que si hay un jugador a menos de
     `RESEAL_CLEAR_RADIUS` (3 bloques) del núcleo la consagración se **pospone con un aviso por la barra de
     acción** para que se aparte: sin ese aviso parecería que el relevo no funciona. Mientras esté sellado,
     al acercarse sale el recordatorio "mata al cultivador del sculk para romper el sello".
  3. **El núcleo se defiende** (`LairManager.defendCore`): aura de **Oscuridad** en radio 8, y una vez roto
     el sello además **colmillos de invocador** alrededor de quien se acerque cada 4 s (con 0.4 s de aviso,
     así que se esquivan). Picar el núcleo es una pelea bajo presión, no un trámite.
- **Infección de sculk que se EXPANDE** (mecánica vanilla): el santuario tiene **catalizadores de sculk**
  en las diagonales de la isla. Los catalizadores convierten los bloques cercanos en sculk cuando muere un
  mob encima. Para alimentarlos, los zombies de la guarida **cazan animales dentro del radio de su hogar**
  (`NearestAttackableTargetGoal` filtrado por `isAnimalInsideHome`): la presa muere sobre el sculk y la
  infección crece sola. Cuanto más tiempo dejes viva una guarida, más se extiende.
- **Cultivador del sculk** (`SculkCultivatorEntity`): aparece **una por guarida** y se dedica a cultivar la
  infección. Es un **`AbstractIllager`** (usa el **modelo y la textura reales del Invocador** con un tinte
  escarlata del mod), así que **no se une a los raids vanilla** y **replica a mano** el escalado por
  distancia+amenaza y la XP del zombie agresivo (mismo perfil).
  - **No huye: planta cara.** No tiene ningún goal de huida (antes tenía `FleeThreatGoal` y se alejaba
    demasiado, así que el asalto se convertía en perseguirlo por medio mapa). Escala en fuerza y rapidez como
    el resto de enemigos, pero eso solo lo hace más duro de matar, no más agresivo.
  - **Pelea como una bruja debilitada**: se queda **a distancia** (radio 10, como la bruja) y lanza
    **pociones salpicadas** (`RangedAttackGoal` + `performRangedAttack`), pero con el **doble de recarga**
    (120 ticks = 6 s, frente a los 60 de la bruja) y **sin las variedades fuertes** (nada de daño fuerte ni
    veneno): de lejos frena con Lentitud, de cerca debilita con Debilidad, y en medio solo puede hacer 6 de
    daño. Su `FOLLOW_RANGE` es **32** (no 64): es el guardián de su guarida y no debe perseguir al jugador por
    medio mapa. **Mientras tiene una amenaza REAL delante no atiende la granja** (el goal de ataque tiene más
    prioridad); en cuanto la suelta vuelve a criar, sacrificar y sembrar catalizadores (ver el punto del
    arreglo, más abajo).
  - **Granja macabra**: `LairGenerator` construye el corral <b>como un foso</b> de **2 bloques de
    profundidad** en la plataforma, con **3 bloques de orla plana** alrededor (sin orla, el terreno empezaría
    a bajar en el borde del foso y la pared quedaría de 1 bloque por ese lado: los animales se escaparían).
    En el fondo hay un parche de sculk y **2 catalizadores** (para que el sacrificio alimente la infección
    donde el animal realmente muere) y el ganado inicial (vacas, ovejas, cerdos, pollos). Los animales **no
    pueden saltar 2 bloques**, así que el foso los contiene igual que una valla. **Nada de vallas**: una valla
    no es un bloque convertible por el sculk, así que un cercado de vallas frenaba la infección justo en el
    corral; las paredes del foso son tierra convertible y el foso es un hueco, así que la mancha entra y sale
    sin obstáculo. La **salida es exclusiva del cultivador**: un escalón de 1 bloque en el borde (el resto del
    foso conserva su pared de 2) más una **puerta de madera** cerrada, que los animales no pueden abrir y él
    sí (`OpenDoorGoal` + `setCanOpenDoors(true)`, como los aldeanos). El cultivador **cría** el ganado
    (`BreedAnimalsGoal`) y **sacrifica** el excedente sobre el sculk (`SacrificeGoal`), pero con **dos reglas
    que evitan que vacíe el corral**: (a) solo cuenta al **ganado** etiquetado, no a los animales salvajes que
    anden por la guarida — contarlos a ellos hacía que la cuenta nunca bajara del mínimo y siguiera matando
    ganado hasta dejar una sola especie (solo sobrevivían los pollos, que además se reproducen solos poniendo
    huevos); y (b) **nunca sacrifica por debajo de `MIN_LIVESTOCK` (6) ni a una especie con menos de 3
    adultos**, así siempre queda pareja para criar. Además `BreedAnimalsGoal` pone en celo a **dos** animales
    de la misma especie: con uno solo no se aparean (`Animal` necesita pareja), y por eso el rebaño antes solo
    menguaba.
  - **El ganado está protegido de los demás enemigos**: los animales del corral llevan la etiqueta
    `devilrpg_livestock` y el `AggressiveZombieEntity` **excluye** a los animales marcados de su caza dentro
    de la guarida. Sin esto, en cuanto el cultivador mataba a uno los demás zombies arrasaban el rebaño
    entero y la granja se quedaba sin nada que criar. Los animales **salvajes** que entren en la guarida sí
    siguen siendo cazados (así es como la infección se alimenta sola).
  - **Siembra catalizadores** (`PlantCatalystGoal`): cuando la infección ya cubre suficiente sculk,
    **extrae** bloques de sculk del terreno y los condensa en un **catalizador nuevo**, colocándolo en el
    borde de la infección (hasta `MAX_CATALYSTS` = **12** por guarida). Es **imprescindible** para que la
    mancha siga creciendo: en vanilla los catalizadores **nunca se crean solos** (la expansión del sculk solo
    genera sensores y chilladores, ver `SculkBlock.getRandomGrowthState`), y un catalizador solo florece
    cuando muere un mob a **≤8 bloques** (su `GameEventListener`), con una carga igual a la **XP** del mob
    muerto. O sea: sin catalizadores nuevos, el frente se queda donde llegan los que ya hay.
    Detalles afinados: el barrido cuenta hasta **4 bloques por debajo** del núcleo, así que **sí cuenta los 2
    catalizadores del fondo del foso del corral** (antes no, y por eso creía tener 6 cuando la ventana
    contaba 4); y busca hueco en **varias capas** (3 arriba, 5 abajo, de arriba hacia abajo), así la mancha
    puede **trepar desniveles y bajar al foso** en vez de quedarse en una sola capa. Si un punto no es
    alcanzable, lo apunta para no volver a elegirlo y no quedarse en bucle.
  - **Nota técnica**: todos sus goals declaran `setFlags(MOVE, LOOK)`. Sin flags, `GoalSelector` los deja
    arrancar aunque otro de más prioridad esté corriendo (los flags son el *único* mecanismo de prioridad),
    y acabarían peleándose por la navegación. (Sin banderas solo van dos: `OpenDoorGoal`, que únicamente abre
    puertas, y el de **adoptar** ganado, que solo etiqueta.)
  - **ARREGLO — la faena no se para por un objetivo que no es una amenaza real** (el jugador: *"el guardián de
    la guarida ya no está sacrificando ningún animal"*, mirando la guarida desde fuera). Causa REAL, leída en
    el código vanilla: el goal de ataque `RangedAttackGoal` **no mira la distancia** en `canUse()` (le basta
    con tener objetivo) y su `canContinueToUse()` **sigue devolviendo `true` mientras la navegación no haya
    terminado**, aunque el objetivo ya no exista. Como tiene la **prioridad 1** y las banderas `MOVE`+`LOOK`
    —las mismas que las tres faenas (prioridad 2-4)—, cualquier objetivo dentro de los **32** del
    `FOLLOW_RANGE` dejaba criar, sacrificar y sembrar parados **indefinidamente**: al jugador le bastaba con
    mirar desde fuera. Medido en el guardado y el log: la guarida del objetivo 2 tenía **41 cabezas** de
    ganado en el foso (todas del generador, ver abajo) y solo sacrificaba con el jugador lejos (última
    sesión: 8 sacrificios en 45 s, el último **4 s** antes de que abriese el menú de pausa). Ahora:
    - el objetivo se fija con `ThreatTargetGoal`, que sobrescribe `getFollowDistance()` para usar
      `THREAT_RADIUS` (**12** = radio de tiro + margen) en vez de los 32 del atributo: el `FOLLOW_RANGE`
      **sigue** en 32 (es la medida documentada de la plataforma) y solo cambia **a quién considera objetivo**;
    - `isRealThreat()` manda: si el objetivo está **lejos** o lleva **80 ticks** sin poder acercarse y no está
      a tiro de poción, se le **suelta** (`giveUpThreat`) y se le **recuerda 600 ticks** —`setTarget` veta
      volver a fijarlo, porque si no el selector lo reelegiría cada 10 ticks y la granja quedaría a medias—;
    - **no huye y planta cara** a quien se le acerca de verdad, y al soltar al objetivo la granja recupera las
      banderas **el mismo tick**;
    - **rastro en el log, una línea por transición** (`DevilRpg.LOGGER`): "planta cara a X a N bloques: pausa
      la granja", "suelta a X (motivo): vuelve a criar, sacrificar y sembrar", "retoma la faena tras N ticks"
      y "vuelve al trabajo: sacrificio/cría/siembra". El sacrificio se registra **siempre** (antes solo si
      había un catalizador a ≤8 bloques, así que un sacrificio fuera del corral no dejaba ni rastro).
  - **ARREGLO — el rebaño ya no se queda clavado** (la otra mitad del mismo fallo). Las **crías no heredan**
    la etiqueta `devilrpg_livestock`, así que no contaban como ganado: con las reglas del sacrificio (≥3
    adultos de la misma especie) y el tope de cría (`MAX_LIVESTOCK` = 8), un corral recién generado —**2 vacas,
    2 ovejas, 1 cerdo y 2 gallinas**— se quedaba en 2+2+1+2 y **no sacrificaba nunca** (comprobado en el
    guardado: 7 cabezas contadas, ninguna especie con 3 adultos `build/lair_simula_cultivador.py`). Ahora:
    - `AdoptCorralLivestockGoal` marca como ganado a lo que aparece **dentro del foso del corral** sin marca
      (`LairGenerator.isInsideCorralPit`: geometría determinista del corral, disco de radio 4 a `FARM_DISTANCE`
      del núcleo y fondo `FARM_DEPTH`). Es la misma regla que el pueblo —"un animal sin marca dentro de un
      corral es de ese corral"— y **no toca a los salvajes** que andan por la guarida, que siguen siendo presa
      de los zombies (de ahí sale la infección que crece sola).
    - `BreedAnimalsGoal` elige la **especie que tiene pareja** (con un solo adulto no se cría nada y se
      quemaba su celo) y, si con el rebaño **no se puede sacrificar nada**, deja criar por encima de
      `MAX_LIVESTOCK` hasta `HARD_LIVESTOCK_CAP` (**12** = 4 especies × 3): criar es la única forma de llegar
      a los 3 adultos, y el tope duro evita que el corral se llene de crías.
    - **El rebaño inicial no se vuelve a sembrar**: `LairGenerator.generate` corre **otra vez** en cada sesión
      (la guarida se regenera al acercarse) y soltaba **7 animales más en el corral cada vez** (medido: 19 en
      la guarida del objetivo 1 y **41** en la del 2, **todas** con `spawn_type=MOB_SUMMONED`, o sea todas del
      generador y ninguna criada). `buildFarm` ahora siembra el rebaño **solo si el corral está vacío** (y lo
      repone si el jugador lo dejó sin nada).
    - Con el **rebaño cerca del objetivo no se lanza la poción de daño** (`livestockNear`): el splash alcanza a
      todo lo que pilla en 4 bloques —también al ganado— y el guardián pelea muchas veces desde su propio
      corral; una gallina tiene 4 de vida, así que una sola poción le mataría el rebaño. Sigue plantando cara,
      pero con Lentitud/Debilidad.
    <br>**No hace falta migración**: son reglas de goals y una guarda **idempotente** del generador. La guarida
    ya construida se ve al reiniciar y volver al objetivo (documentado arriba), y lo único que cambia en el
    mundo es que **deja de duplicar** el rebaño en la siguiente pasada.
- **Núcleo asaltable** (`LairCoreBlock`, bloque `lair_core`): el bloque brillante en el centro del
  santuario, dentro de la caja de sellos. Mientras el núcleo exista, la guarida está **activa**.
- **El estado de la aldea también PERSISTE** (`VillageSavedData`, otro `SavedData` por dimensión, con el mismo
  patrón): **`Generated`** (la aldea **no se vuelve a generar** encima de la que ya hay: antes, al reiniciar,
  se nivelaba el terreno y se reconstruían cabañas y valla, cargándose lo que hubieras construido cerca),
  **`Resolved`** (el asedio ya resuelto no se relanza, así no se puede repetir la recompensa volviendo al
  objetivo) y **`Noticed`** (el aviso de "divisas una aldea a lo lejos" no se repite). Los asedios **en curso**
  no se persisten a propósito: si cierras el juego a mitad, al volver la aldea tiene otra vez su margen y su
  ola, que es más justo que reanudar una ola con zombies ya descargados.
- **Nada persigue a un jugador en creativo**: los *goals* vanilla filtran por `canBeSeenAsEnemy()`, pero el
  mod asigna el objetivo **a mano** al spawnear (`VexSpawnRule` y `LairManager.spawnOne`) y eso salta el
  filtro. `FrostVexEntity` ahora **rechaza** como objetivo a quien no pueda ser visto como enemigo (creativo,
  espectador, inmune) y suelta el que ya tuviera si el jugador pasa a creativo a mitad de la persecución.
- **Spawn de enemigos**: `LairManager.tick` — si hay un jugador a **<64 bloques** de la guarida, cada **25 s**
  spawnea una tanda (`3 + min(objetivo,6)` enemigos) **a 8–15 bloques** del centro (nunca más cerca: dentro
  del foso caerían dentro y se perdería la tanda). Hay un **cupo de 30 enemigos vivos por guarida**
  (`MAX_LAIR_MOBS`, sin contar al guardián): las tandas siguen llegando hasta llenarlo y, en cuanto matas a
  algunos, las siguientes los reponen. Las tandas siguen llegando **aunque hayas matado al guardián** y el
  sello esté roto: la guarida solo enmudece cuando **destruyes el núcleo**. (El spawner nocturno por el mundo
  tiene su propio cupo aparte, también de 30, y no se toca.) Los zombies agresivos **patrullan un radio de 24
  bloques** alrededor del núcleo (goal `PatrolHomeGoal`); en guaridas lejanas (objetivo ≥ 2) aparece también
  algún **vex helado**.
- **Limpiar la guarida**: al destruir el núcleo, `LairCoreBlock.onRemove` → `LairManager.onCoreBroken`
  marca la guarida como limpiada (deja de spawnear), avisa al jugador y da recompensa (XP, huesos, arena de
  almas, esmeraldas). `tick` también detecta si el núcleo desapareció (persistencia natural sin SavedData).
- **El estado de las guaridas PERSISTE** (`LairSavedData`, un `SavedData` por dimensión, como los raids de
  vanilla), indexado por el índice del objetivo porque la posición de la guarida es determinista a partir de
  él: no hace falta guardar coordenadas. Guarda **`Cleared`** (con la **posición del núcleo**, para poder
  comprobar la marca; una guarida limpiada **no se vuelve a generar**: antes, al reiniciar la partida, renacía
  entera con su núcleo, su guardián y su caja de sellos, y la recompensa se podía repetir) y **`SealBroken`**
  (al volver, la guarida se regenera **sin** la caja de sellos —`LairGenerator.generate(level, spot,
  sealCore)`— y el relevo del guardián arranca su cuenta de 3 min como si acabaras de matarlo).
- **El núcleo se comprueba con TOLERANCIA y se auto-repara**: buscarlo en una posición exacta era frágil (si la
  altura calculada varía un bloque entre sesiones, la guarida se daba por limpiada **por error** y quedaba
  muerta para siempre: sin oleadas, sin guardián y con la caja de sellos en pie, porque el borrado de la caja
  apuntaba a la posición equivocada). Ahora `findCoreNear` busca en **±3 vertical y ±1 horizontal**,
  `syncCorePos` **corrige** la posición guardada si el núcleo se movió, y si una marca de `Cleared` resulta
  **falsa** (el núcleo sigue ahí) se **deshace** y la guarida se restaura.

---

## 4) Roadmap (próximas iteraciones)

### Iteración 2 — Enemigos inteligentes (pilar 2) — COMPLETA ✅
- ✅ **Comportamiento de manada**: rodean al objetivo desde ángulos distintos (ver 3b.3).
- ✅ **Guaridas**: focos de enemigos que **cambian el terreno** a su alrededor y que el jugador puede
  **asaltar** (ver 3c).
- ✅ **Objetivos defendidos**: el núcleo de una guarida ya no es un bloque suelto — foso con
  puentes, **sello** que solo cae al matar al cultivador, y el propio núcleo atacando (ver 3c).
- ✅ **Minions que atienden a su dueño**: el wisp **ranger** abandona su tarea y vuelve cuando el jugador se
  aleja **más de 16 bloques** (deja de seguirlo al acercarse a 12; solo se teletransporta si de verdad no
  consigue alcanzarlo, a 32). Para que eso funcione hubo que declarar el flag `MOVE` en sus cuatro goals de
  trabajo (cortar, recoger madera, plantar, cosechar) y quitar un caso especial que **desactivaba** el
  seguimiento en cuanto el ranger tenía el goal de cortar registrado — o sea que en la práctica nunca seguía
  a su dueño.
- **Descartado (probado y revertido): que cada enemigo gane rangos por sobrevivir.** Se implementó
  (`VeteranGrowth`: rangos por tiempo vivo + bajas, con más vida/daño/tamaño y más XP) y se **quitó**: la
  dificultad de los enemigos ya la determina **cuánto ha avanzado el jugador** (distancia + amenaza), y sumarle
  una segunda curva por individuo era complicación sin valor de diseño. Lo que se quería decir con "se
  fortalecen con el tiempo" es otra cosa: que **los enemigos fortifiquen su base** (ver Iteración 5).

### Iteración 3 — Asentamientos vivos (pilar 3)
- Aldeanos que **construyen/reparan/fortifican**, **cultivan**, **necesitan comer**, **envejecen** y se
  defienden cuando llegan las hordas.
- El jugador **ayuda a progresar** pero no son dependientes.
- **Riesgo real**: la aldea puede **caer** y el jugador debe buscar otra.

#### 3.1 Las hordas apuntan al asentamiento, no al jugador — IMPLEMENTADO (paso 1 y 2) ✅
Hoy **todo gira alrededor del jugador**: `AggressiveZombieEntity` tiene en el `targetSelector` prioridad **1**
un `NearestAttackableTargetGoal<Player>`, y `HordeManager.spawnHorde` elige un *jugador* y lanza la horda a
20–44 bloques de él. La aldea solo se asedia cuando el jugador **llega** (`VillageManager.start`). La
intención es que el mundo tenga **sus propios conflictos** y que el jugador sea quien decide intervenir.

Lo bueno: **casi toda la fontanería ya existía**. `AggressiveZombieEntity` ya tiene `villageCenter`,
`goToCenterActive` y `MoveToVillageCenterGoal` (prioridad 7), y `VillageManager` ya sabe resolver un asedio
con su premio y su estado guardado. Lo implementado:

1. ✅ **Estado de asentamiento persistido** (`VillageSavedData`): por aldea, una **presión** (ticks de juego
   sin que nadie la atienda) y el flag de **caída**. La presión no necesita tickear nada: se acumula al
   consultarla (`accruePressure`), así que avanza igual aunque el chunk esté descargado y es determinista.
   Al defender la aldea se reinicia a cero.
2. ✅ **`HordeManager` elige aldea**: cuando toca horda, busca la aldea **más descuidada** (mayor presión, por
   encima de `PRESSURE_MIN_TICKS` = 8 min) de las que están a menos de `HORDE_TARGET_RADIUS` (220) del
   jugador, ya generadas, no caídas y no atacadas en ese momento. Lanza la horda **a `FENCE_RADIUS + 3 … + 19`
   bloques del centro** (39–55 con el radio 36: siempre fuera de la valla) y a cada zombie le pone `setVillageCenter(...)` +
   `setGoToCenterActive(true)`, así que **marchan a la aldea** con los goals que ya existían. Si no hay
   ninguna aldea candidata, la horda va a por el jugador como antes. Logs `[Horda]`/`[Village]`.
3. ✅ **Los enemigos atacan a los aldeanos** (`AggressiveZombieEntity`): nuevo objetivo `Villager` (prioridad
   4) y `IronGolem` (prioridad 5, solo si el zombie va a por una aldea). El jugador sigue siendo la
   prioridad 1, así que si vas a defenderlos **te atraen a ti** y la aldea solo cae si no vas.
4. ✅ **Resolución del asedio del mundo** (`VillageManager.tickWorldSieges`): si los enemigos caen, la aldea
   **resiste** (presión a cero + aviso a los jugadores cercanos); si se queda **sin aldeanos**, la aldea
   **cae** (`markFallen`, no vuelve a ser objetivo) y, si era la del objetivo actual, **el objetivo avanza**
   para quien lo tuviera pendiente (se perdió). Las aldeas caídas ya no generan presión.
5. ✅ **El destino viaja con el zombie**: `villageCenter`, `goToCenterActive` y el hogar de guarida se guardan
   en NBT, así que un asediador que se recarga a mitad de camino **no pierde su destino** (antes sí).
6. ✅ **De paso, un bug latente**: al resolver un asedio, el objetivo avanzaba con `objectiveIndex + 1` sin
   comprobar que fuera el objetivo **actual**; desde que existen aldeas de objetivos superados (ver 3b.1),
   defender una aldea vieja habría hecho **retroceder** el índice. Ahora solo avanza si es el actual.

7. ✅ **Recompensa propia por rechazar la horda del mundo**: al resolverse el asedio, los jugadores que
   **participaron** cobran `WORLD_SIEGE_REWARD_FRACTION` = **1/6 de un punto de habilidad** (en experiencia: la
   sexta parte de la barra de su nivel, ver `MissionRewards.giveSkillPointFraction`) más un **botín pequeño y
   variable** del pueblo: de **1 a 3 chips de metal** (pepitas de hierro) y hasta **2 de cuero**, tirados al azar.
   - **Por qué así (lo corrigió el jugador dos veces)**: primero pagaba 1 nivel entero (o sea **1 punto de
     habilidad por horda**, y el mundo manda una cada 3-20 min: el árbol de habilidades entero en una tarde), y
     luego pasó a pagar 4 **lingotes de hierro** fijos. Como es una recompensa **repetible**, 4 lingotes por horda
     convertían al pueblo en una mina: ahora son **pepitas** (9 = 1 lingote en la mesa del herrero), o sea **un
     tercio de lingote** como mucho, y de vez en cuando un cuero.
   - **Por qué 1/6 y no un punto entero (cambio pedido por el jugador)**: esta recompensa es **repetible** (el
     mundo manda una horda cada 3-20 min), así que pagando 1 nivel por horda el jugador se completaba el árbol
     de habilidades en una tarde sin jugar el resto del mod. Con 1/6 hacen falta 6 hordas rechazadas para un
     nivel (y su punto, que llega por el camino normal). El asedio **clásico** sí sigue pagando 1 nivel entero
     porque se resuelve **una sola vez por aldea** (queda guardado en `VillageSavedData.markSiegeResolved`), o
     sea que no es farmeable.
   - Participar = haberle pegado a algún enemigo de esa horda. El zombie lleva su aldea en
     `worldSiegeIndex` (NBT) y en `hurt` avisa a `VillageManager.registerDefender`; vale también el daño de tus
     **minions** (si el atacante tiene dueño, el mérito es del dueño).
   - **Antídoto contra el exploit**: la lista de la horda guarda solo atacantes **vivos** (cada muerte se
     descuenta en `die` → `onWorldSiegeAttackerKilled`). La recompensa solo se paga si esa lista quedó
     **vacía**, es decir si de verdad los mataron. Antes bastaba con alejarse para que los chunks se
     descargaran y el asedio se diera por "resistido"; ahora, si la horda "desaparece" sin morir, la aldea
     resiste igual (se reinicia su presión, como siempre) pero **no se paga nada** y queda en el log.

**Iteración 3 — CERRADA ✅.** Los tres pasos que faltaban:

8. ✅ **Salud de la aldea por aldeanos vivos** (antes solo "0 aldeanos = caída"). La salud es el número de
   aldeanos vivos y se guarda en `VillageSavedData` (`getHealth`/`setHealth`, con `HEALTH_UNKNOWN` = -1 si
   nunca se ha podido mirar la aldea). Reglas:
   - **Se cuenta solo si el chunk está cargado** (`level.isLoaded(center)`, ver `observeVillagers`). Contar
     entidades descargadas daría 0 y la aldea se marcaría caída sin motivo: ese era un bug latente.
   - **Una aldea debilitada atrae más hordas**: la presión se acumula multiplicada por
     `pressureMultiplier` = 1 + (aldeanos que faltan) × `PRESSURE_PER_MISSING_VILLAGER` (0,5). Con 1 aldeano
     acumula el doble; vacía, 2,5 veces.
   - **Se recupera de a poco**: una aldea debilitada repone **un aldeano cada `REPOPULATE_INTERVAL_TICKS`**
     (5 min) y solo si tiene comida (`FOOD_TO_GROW` = 8). Una aldea **vacía** se rehace entera de golpe
     (aldeanos + golem), como antes, para que no quede muerta si llegas justo después de una masacre.
9. ✅ **Aldeas caídas = ruinas**. Al caer se marca `markFallen` y se llama a `VillageGenerator.ruin`, que
   **derruye parte de lo construido** (35% aire, telarañas, piedra mohosa y ladrillo agrietado) en un disco del
   radio de la valla. Es **determinista** (semilla por objetivo) y con tope de 2.500 bloques por pasada, y solo
   ocurre una vez. Ojo: ahora caer es **definitivo también en el asedio clásico** (antes solo avisaba por chat
   y el gestor repoblaba la aldea después, como si no hubiera pasado nada).
10. ✅ **Aldeanos que cultivan, comen, reparan y envejecen** (`VillageManager.tickVillageLife`, un latido cada
    `VILLAGE_POLL_TICKS` = 10 s, solo en aldeas **en paz** con aldeanos vivos):
    - **Cultivan**: el generador planta **dos parcelas** de **9×9** (trigo, zanahorias, patatas y betabel; acequia
      central y
      compostador) — `VillageGenerator.farm`. La acequia va en la fila del medio, así que salen **4 carriles de
      cultivo por lado** (72 cultivos por parcela; antes la parcela era de 9×5 con 2 carriles por lado y la
      producción se quedaba corta, el jugador lo pidió). Las parcelas están en `(-20,10)` y `(10,6)` (comprobado que
      caben las dos con el almacén, la herrería, las casas, el kiosco y los sitios de aldeano, y que **tapan a las
      parcelas viejas** para no dejar bancales sueltos). Cada parcela se nivela a **un solo nivel** (`base` = la columna más
      alta de su huella) y se **limpia antes de rehacerse**: si cada columna usara su propio `groundY`, en terreno
      irregular la acequia quedaba un bloque por debajo de la tierra de cultivo y el trigo se **secaba** (la
      tierra solo se hidrata con agua a su nivel o uno por encima, `FarmBlock.isNearWater`); y al rehacerla sin
      limpiar quedaban capas viejas debajo y **dos composteadores apilados**. El compostero tiene además su
      propia limpieza de columna. Las parcelas viven en `FARM_PLOTS` (esquina relativa al centro) con
      `PLOT_WIDTH`/`PLOT_DEPTH`/`PLOT_WATER_ROW`, y los **faroles nunca se plantan dentro** (`insideFarm`, con 1
      bloque de margen).
      - **La Y del centro manda (y es la cota)**: los goals reciben el centro de la aldea, y ese centro llegaba con
        una Y falsa — la del **spawn del jugador** (101 con la aldea a 62-75) o **0** desde `centroDe`. Con esa Y,
        `parcela.offset(dx, 0, dz)` + barrido de ±1 buscaba los cultivos **decenas de bloques por debajo del suelo**:
        el granjero no veía ni un cultivo (daba vueltas y se iba a la despensa sin cosechar nada) ni el compostero
        lleno, y las distancias salían con un desnivel enorme (con Y=0 el `distSqr` mínimo era 64² = 4096 contra un
        tope de 60², así que el goal **no se activaba nunca**). Ahora `VillageGenerator.parcelasDe(level, center)`
        devuelve las parcelas a **la cota** y `centroDe` da el centro **con la cota como Y** (y no cachea si el chunk
        no está cargado). **Y el centro que se pasa a TODA la aldea** (goals, asedio, zombies) ya no es el del
        `ObjectiveTargets` (que trae la Y del **spawn del jugador**): para una aldea generada se le pone la Y de la
        cota en `manageNearby`. Los tres goals miden además la distancia al centro **en horizontal**: la aldea es un
        recinto en el plano XZ y mirar la Y dejaba al aldeano fuera de su propio pueblo.
      - **Se camina por el CEREBRO**: los tres goals movían al aldeano con `getNavigation().moveTo(...)`, pero el
        cerebro escribe su propio destino en cada tick (su puesto, la plaza, la cama, pasear) y pisaba el nuestro: el
        aldeano se iba a otro lado a mitad de camino. Ahora el rumbo se le da con `VillageManager.caminarHacia`
        (`WALK_TARGET`/`LOOK_TARGET`, igual que el `HarvestFarmland` del juego) y se le quita al llegar
        (`VillageManager.parar`).
      - **El recolector es el holgazán**: su `canUse` exigía la marca de OBRERO (`BUILDER_TAG`), que el recolector no
        tiene nunca, así que **nunca recogía nada** (los objetos se quedaban tirados: 155 en una aldea del guardado).
      - **Atascado = NO ACERCARSE** (no "ir andando"): los tres goals cuentan `stuckTicks` solo cuando el aldeano no
        mejora su distancia más corta del viaje (`mejorDistancia`). Contando cada tick, el goal se rendía a los 120
        ticks (6 s) aunque fuera avanzando, así que un viaje a la despensa **no lo terminaba nunca** y el granjero se
        quedaba ciclado con la cosecha encima (el jugador lo vio: "no sube al kiosco a poner la cosecha").
      - **El punto de apoyo de la despensa va en el patio, delante de la escalera**: `puntoDeApoyo` era
        `(centro, cota, centro.z + 4)` y el kiosco pone justo ahí su escalera de acceso, así que la "casilla de suelo"
        era un bloque sólido. Ahora va dos bloques más allá (patio llano) y el aldeano descarga sin subirse a nada.
    - **Comen pan de verdad**: a un aldeano que aún no puede criar (vanilla pide **12 puntos** de comida:
      `Villager.canBreed`) se le deja **un pan en el suelo** que recoge él mismo (`ItemEntity` + `wantsToPickUp`
      vanilla), y con eso nacen **crías** de verdad. Un pan por latido y solo si la despensa tiene para pagarlo
      (`FOOD_PER_BREAD` = 4). A los **viejos no se les da**: ya no crían.
    - **Hambre con consecuencias**: si la despensa llega a 0 (`starvingSince` persistido), los aldeanos van con
      **Debilidad** y **Lentitud** mientras dure y, si el hambre pasa de `STARVATION_DEATH_TICKS` (10 min),
      **muere uno** (y el contador se reinicia). La aldea también deja de crecer: un aldeano nuevo cuesta 8.
      La granja da `FARM_YIELD` = 8 por latido y cada aldeano come 1 (despensa tope 64).
    - **Reparan un aldeano obrero, andando y bloque a bloque** (`VillagerRepairGoal`). Antes lo hacía el gestor
      con un `repair()` que reconstruía caminos, cabañas, faroles, granja y valla **en un solo tick**: si mirabas,
      la aldea aparecía de la nada, y encima podía reconstruir sobre lo que hubieras construido tú. Ahora:
      - **El plano es CANÓNICO** (`CURRENT_LAYOUT = 4`): no se fotografía la aldea leyendo el mundo, lo **graba el
        propio generador mientras construye**. `VillageGenerator.generate` devuelve el plano: enciende una
        grabadora justo después del terreno (limpieza, nivelado, isla) y **todo** lo que colocan las estructuras
        pasa por `colocar(...)`, que apunta el bloque. Las casas, cuyos bloques los pone
        `StructureTemplate.placeInWorld` (no pasa por `colocar`), se apuntan con `apuntarCaja` una vez limpiados
        los bloques técnicos. Al final `aPlano()` descarta aire y terreno natural… **salvo el agua y la tierra de
        cultivo de la granja**, que se conservan para que el obrero pueda reponer la acequia y las parcelas
        pisoteadas (los cultivos no: son del granjero). Esa regla la comparten el plano canónico **y el escaneo**
        del mundo que se hace al migrar una aldea vieja (`seDescartaDelPlano`): el escaneo antes saltaba la tierra
        de cultivo, así que esas aldeas no la tenían en el plano y el obrero no reponía la parcela. Se guarda en `VillageManager.preGenerate`, con la aldea recién hecha.
        *Por qué importa*: al capturar leyendo el mundo, el plano era una **foto**: si la aldea ya estaba dañada
        (o se capturaba tras una horda, porque la captura espera a que acabe el asedio), ese destrozo pasaba a
        considerarse "lo correcto" y el obrero lo mantenía para siempre. Con el plano canónico eso ya no puede
        pasar. Las aldeas **de partidas viejas** (sin plano canónico posible) siguen con la captura por escaneo
        (`captureBlueprint`) al aplicarles la migración de trazado.
      - El **obrero** ya no es uno solo: la aldea nombra hasta `MAX_BUILDERS` = **3** (dejando al granjero para la
        huerta si hay gente de sobra) y se **reparten los huecos** con reservas (`reclamarHueco`/`liberarHueco`, con
        caducidad de 1 min), así no se amontonan en el mismo agujero. El ritmo también subió: medio segundo de
        golpe y medio de descanso por bloque (`WORK_TICKS`/`REST_TICKS` = 10), cuando antes eran 1 s + 2 s.
      - **La lista de obreros se RECALCULA y se reconcilia** en cada latido: primero los que ya eran obreros y no son
        granjeros, después los demás adultos con otro oficio y, solo como último recurso, un granjero. **A los que
        sobran se les quita la marca y el goal** (`desmarcarObrero`). Antes la marca no se le quitaba a **nadie**, así
        que el granjero que hizo de obrero cuando la aldea se quedó sin adultos (murió gente) se pasaba la vida
        reparando caminos con su goal de reparación a prioridad 3, **por encima** de su goal de granja (4) — el
        jugador lo vio: quitó un bloque del camino y apareció el granjero a reponerlo. Y si un granjero **tiene** que
        hacer de obrero, ahora lleva la reparación a prioridad **5** (por debajo de la granja): primero la huerta y,
        cuando no tiene faena, repara.
      - También repone la **tierra pisoteada**: saltar sobre la tierra de cultivo la convierte en tierra (vanilla),
        así que si el plano dice tierra de cultivo o acequia y ahora hay tierra/hierba, se vuelve a poner
        (`necesitaReparacion`). Lo que no toca es nada que no sea eso: si pones tú un bloque, se respeta.
        *Ojo*: los **cultivos** no están en el plano a propósito (son del granjero); si el granjero no tiene
        semillas, la parcela puede quedar sin planta aunque la tierra quede bien.
      - **Cuarta casa**: `generate` coloca 4 casas y la última es siempre una **grande**
        (`CASAS_GRANDES` = `plains_medium_house_1/2`) con una **cama extra** dentro (`camaExtra`): en vanilla hace
        falta una cama libre por cría, así que la aldea pasa a poder llegar a 4 aldeanos. Las aldeas ya migradas la
        reciben con `asegurarCuartaCasa` (versión de casas `CURRENT_HOUSES`, para no rehacer las otras tres).
      - El goal busca el hueco **más cercano** (`findRepairTarget`: lo que debería estar y no está, hasta 40
        bloques y ±5/6 de altura), va **caminando** hasta él, se para, mira, da el golpe (`swing`) y **coloca el
        bloque del plano** con su sonido. Un bloque cada ~3 s, con descansos, para que se le vea trabajar.
      - **Nunca pisa nada**: solo repone si en esa posición hay **aire** (si lo puso el jugador, se salta el
        hueco). Y no trabaja en plena refriega ni si se aleja más de 48 del centro.
      - Los huecos inalcanzables se descartan por un rato (`saltados`) para no quedarse en bucle.
    - **Envejecen y hay relevo**: a cada aldeano se le apunta la fecha de nacimiento en sus datos persistentes
      (`BORN_TAG`) la primera vez que se le ve. A los **2 días** de juego se vuelve viejo (Lentitud + Debilidad, y
      deja de recibir pan, así que ya no cría) y a los **3 días muere de viejo** con la animación y el sonido
      normales de muerte (no con un borrado seco). Los que llegan para repoblar una aldea debilitada nacen
      **crías** (`setBaby(true)`), que crecen solas como en vanilla: el relevo se ve.

11. ✅ **El asedio clásico también exige limpiar la horda para cobrar** (era la última rendija que quedaba):
    sus zombies van marcados con el índice de la aldea (el mismo campo `worldSiegeIndex` que usan las hordas
    del mundo) y su muerte los descuenta de `VillageDefense.wave` (`AggressiveZombieEntity.die` →
    `VillageManager.onSiegeAttackerKilled`, que mira las dos listas). Si la ola se da por limpia **sin que
    nadie haya muerto** (te alejaste y se descargaron los chunks), la aldea se salva y el objetivo avanza
    igual —el jugador estuvo allí— pero **no hay recompensa**, y el chat dice "Los monstruos se dispersaron".
    La rama de "los monstruos no lograron entrar" (timeout con atacantes vivos fuera de la valla) sigue
    pagando como siempre: ahí los atacantes están vivos a propósito y la aldea se salvó de verdad.

### Iteración 4 — El abismo vertical (estilo *Made in Abyss*)
- El mundo genera un **abismo descendente infinito** por capas en vez de extenderse en horizontal.
- La escalación por "distancia al spawn" se convierte en **profundidad** (el mismo `SpawnScaleProfile`,
  solo cambia la variable). Por eso se construyó la Iteración 1 pensando en reutilizarla.

### Iteración 5 — Los enemigos fortifican su base (mini juego de estrategia) — PENDIENTE
Esto es lo que **originalmente** se quería decir con *"se fortalecen con el tiempo"*, y no un escalado de
atributos por enemigo (eso ya se probó y se descartó, ver Iteración 2). La idea es que los enemigos jueguen
**su propia partida** en tiempo real, en paralelo a la del jugador:

- **Juntan recursos**: los zombies agresivos (y el cultivador con su sculk) **recolectan** lo que encuentran
  —madera, piedra, huesos, el propio sculk— y lo **acumulan** en su guarida en vez de solo patrullar.
- **Construyen y fortifican**: con esos recursos **levantan muros, torres y trampas** alrededor del núcleo,
  tapan los accesos, cavan fosos... La guarida que dejas tranquila se convierte en una fortaleza.
- **Se organizan**: priorizan obras según lo que el jugador haya hecho (si les rompiste la entrada, la
  reconstruyen; si te acercas mucho, refuerzan ese lado).
- **Contrapresión del jugador**: asaltar la guarida **pronto** es más barato que dejarla crecer — y lo que
  destruyas **no se reconstruye gratis**: se paga con recursos que ya no gastarán en defenderse.
- **Alcance**: empieza por las guaridas (ya tienen núcleo, corral, foso y patrulla) y luego, si funciona, se
  extiende a los asentamientos enemigos propios.

Notas de implementación (para cuando toque): el estado de cada obra debe **persistir** (como `LairSavedData`)
y ser **determinista** entre server y cliente; conviene un "presupuesto de recursos" por guarida que crece con
el tiempo y se gasta en una lista de planos, más un `Goal` de "ir a construir" reutilizando la navegación y el
`BreakBlockGoal` que ya existen.

---

## 5) Configuración rápida

- **Amenaza**: en **`devilrpg-server.toml`**, sección `[threat]`: `threatMaxExtraDifficulty` (0.8 = +80%) y
  `threatFullHours` (3 h **jugadas**). Los lee `ThreatLevel` (`current`, `maxExtraDifficulty()`,
  `fullThreatTicks()`); si la config no está cargada (cliente), usa los valores por defecto.
- **Perfil del zombie (`AggressiveZombieSpawnProfile.INSTANCE`)**: `minDistance` 67, `maxDistance` 3000,
  `minHardDistance` 17, `maxScaleMultiplier` 3.5, `baseHealth` 9, `baseSpeed` 0.068, `baseDamage` 0.7,
  `baseXp` 20 y `maxXpMultiplier` 4.5. **Detalle completo en 5.1.**
- **Objetivo**: `ObjectiveTargets.MIN_DISTANCE` (800), `MAX_DISTANCE` (1200), `OBJECTIVE_STEP` (600),
  `REACH_RADIUS` (24). La distancia del objetivo `i` = `800 + i*600 + rnd*400`, garantizando separación ≥
  200 bloques.
- **Zona protegida que se encoge**: en `SpawnScaleProfile`, `minDistance` (67 al inicio) se reduce con la
  amenaza hasta `minHardDistance` (17 a máxima). Se configura con `minHardDistance` y
  `effectiveMinDistance(threat)`; la probabilidad/escalado/XP aceptan el `threat`.
- **Horda**: `HordeManager.BASE_INTERVAL_TICKS` (20 min al inicio), `MIN_INTERVAL_TICKS` (3 min con máxima
  amenaza), `BASE_HORDE_SIZE` (3) y `MAX_EXTRA_MEMBERS` (12). El tamaño planeado es
  `BASE_HORDE_SIZE + amenaza*MAX_EXTRA_MEMBERS` (3 → 15 al máximo), y cada zombie pasa por la probabilidad
  del `SpawnScaleProfile` (distancia + amenaza). Hoy la horda se lanza alrededor de un **jugador** que esté
  fuera de la zona protegida, a **20–44 bloques** de él en círculo.
- **Presión de vexes (`VexSpawnRule`/`VexSpawnProfile`)**: `FrostVexEntity` (extiende `Vex`), `minDistance` 67,
  `maxDistance` 1000, `maxScaleMultiplier` 2.5, `baseHealth` **3** (un tercio de la vida del vex: 6.67 era dos
  tercios y la dejaba al doble del resto del perfil), `baseSpeed` 0.077, `baseDamage` 0.34,
  `baseXp` **15** (subido desde 5: con 5 parecía que los vexes no daban XP), `maxXpMultiplier` 4.5 (hasta ~82
  lejos de la base). Spawnea de día y de **noche**, a **12–24 bloques del jugador**, límite **15 vivos**,
  intervalo 20 s–2 min. **Detalle completo en 5.1.**
  - **Ya NO tienen vida limitada**: antes `VexSpawnRule` les ponía `setLimitedLife(2 min)` y, al agotarse,
  vanilla los mata con `damageSources().starve()` → **no contaba como baja del jugador y no soltaban XP**
  (por eso parecía que "matar un vex no da experiencia"). El tope de 15 vivos ya evita que se acumulen, así
  que ahora todos se pueden matar y dan su XP. Para revertirlo, basta con volver a poner esa línea.
- **Aldea (**`VillageGenerator`/`VillageManager`)**: `FENCE_RADIUS` 36 (agrandada desde 29: +24 % de recinto),
  `LEVEL_RADIUS` 38, `GRACE_TICKS` 90 s, `SIEGE_TIMEOUT_TICKS` 2 min, `DEFAULT_WAVE` 8 + `min(objectiveIndex*2, 20)`,
  oleadas a `FENCE_RADIUS + 3 … + 11` = **39–47** bloques del centro (fuera de la valla; antes eran 32–40 fijos y
  con el radio 36 habrían aparecido **dentro** del muro).
  - **Solares repartidos** (trazado 24): las 4 casas van a 20–25 bloques del centro, una por cuadrante
    (`basesDeCasas`), la granja se separa a `(-20,10)` y `(10,8)`, la iglesia a `(-12,-25)` y los 5 sitios de
    aldeano se reparten en un anillo de 13–15. La migración **derriba el trazado antiguo** (casas, iglesia y
    parcelas viejas) y borra el **anillo del muro viejo (radio 29)**, que si no quedaría una muralla cruzando el
    pueblo por dentro. Tamaños reales medidos en las plantillas del juego: casa pequeña 7x7, mediana 13x11,
    iglesia (`plains_temple_4`) 10x12x7 → la huella máxima (base + 12 en x, + 10 en z) cabe con holgura.

- **Asentamientos vivos (`VillageSavedData` + `HordeManager`, Iteración 3)**: `HORDE_TARGET_RADIUS` 220
  (radio respecto al jugador para buscar aldea a la que mandar la horda), `PRESSURE_MIN_TICKS` 8 min de
  juego (presión mínima para que una aldea sea objetivo), `FALLEN_CHECK_RADIUS` = `FENCE_RADIUS + 28` = 64
  (radio para contar aldeanos: 0 = la aldea ha caído; antes 48 fijo, que con el recinto nuevo se quedaba corto),
  `SIEGE_WARN_RADIUS` 160 (a quién se avisa) y spawn de la horda a `FENCE_RADIUS + 3 … + 19` = **39–55**
  bloques del centro.

- **Minions persistentes (lobo, oso y wisp)**: los minions se guardan por **UUID** en la capability del jugador
  (`PlayerMinionCapability`) y ahora **sobreviven a salir y volver a entrar**, sin duplicarse:
  - **Copia periódica** (`PlayerTickEvent.Post`, cada 10 s → `captureMinions(player, false)`): guarda el NBT
    completo de cada minion vivo (más su dimensión y posición) en `Stored_Minions`, **reemplazando** la entrada
    anterior (por UUID, no se acumulan). Esta copia es lo que hace fiables los otros dos casos.
  - **Al desconectarse** (`PlayerLoggedOutEvent`): `captureMinions(player, true)`, que además saca los minions
    del mundo con `discard()` (no dispara `die()`, así no ensucia las listas). Es solo "mejor esfuerzo": ese
    evento salta **después** de que el servidor guarde al jugador, así que lo escrito solo ahí **no llega al
    disco** (fue el bug por el que los minions no volvían). La copia que vale es la periódica.
  - **Al entrar**: primero `restoreStoredMinions` (**adopta** los que sigan existiendo; **recrea** solo los que
    ya no estén —para distinguir "chunk descargado" de "muerto" se fuerza la carga de su chunk— y **nunca**
    recrea "por si acaso", que es justo lo que duplicaría) y después `bringMinionsToPlayer`, que además trae a
    los minions que sigan **vivos en el mundo sin copia guardada** (corte de luz, y partidas anteriores a este
    cambio). Las dos llamadas van **directas en el hilo del servidor**: `EventUtils.onJoin` las difería al hilo
    del cliente, que es incorrecto para tocar entidades.
  - **Al morir**: se matan los vivos y se olvida lo guardado (no vuelven).
  - **Al cambiar de dimensión**: se teletransportan contigo (`DimensionTransition`), en el siguiente tick del
    servidor para que el nivel destino ya esté aplicado.
  - **El shulker del hongo NO se guarda**: es temporal (1 min) y muere si el dueño no está
    (`ISoulEntity.despawnsWithoutOwner()` → `true`; lobo, oso y wisp lo sobrescriben a `false`, así que solo
    mueren si el dueño **existe y está muerto**). Antes, "no encuentro al dueño" mataba a todos.
  - **BUG GRANDE (arreglado): `saveWithoutId` NO escribe el campo `id`.** Se llamaba
    `entity.saveWithoutId(data)` para guardar el minion, pero ese método **no pone `id`** (por eso se llama
    "without id"; el `id` solo lo escribe `Entity.save()`, que además se niega a guardar si la entidad va
    montada). Sin `id`, `EntityType.create` no sabe qué crear: registra en el log
    **`Skipping Entity with id`** (con el id **vacío**: ese warning sin nada detrás es la firma de este bug),
    devuelve vacío y el minion no vuelve. Y como el `restore` hacía `stored.clear()` **siempre** al final, cada
    intento fallido **borraba las copias para siempre**. Arreglado: el `id` se escribe a mano en
    `saveEntityData`, y las entradas que no se pueden recuperar **se conservan** (`restoreStoredMinions` solo
    borra las que sí recuperó, y avisa con `[Minion] NO pude recuperar ...`).
  - **SEGUNDO BUG: el dueño se guarda como texto vacío.** `SoulWolf.addAdditionalSaveData` y `SoulWisp` hacen
    `putString("Owner", "")` **después** de `super`, machacando el UUID que escribe `TamableAnimal`. Eso sale y
    entra del NBT sin problema, pero un minion **recreado** quedaba con `getOwnerUUID() == null` →
    `isTame() == false` → `ISoulEntity.addToAiStep` lo mataba **en el primer tick**. Por eso `recreateMinion`
    llama a `minion.tame(player)` antes de soltarlo en el mundo.
  - **Copias viejas sin `id`**: `recreateMinion` deduce el tipo del UUID (sigue en la lista de lobos, osos o
    wisps) y lo escribe en la copia; para los wisps (que comparten lista) usa la clase con más puntos del
    jugador y lo avisa en el log. Es una red de seguridad para partidas guardadas con la versión con el bug.
  - **Poda**: al capturar se tiran las copias cuyo UUID ya no está en ninguna lista del jugador (`pruneStoredEntries`),
    para no resucitar un minion que el jugador ya no tiene.
  - **POR QUÉ SE ACUMULABAN MINIONS MUERTOS EN LAS LISTAS (arreglado)**: el `die()` de lobo, oso y wisp quita el
    UUID de la lista **solo si `getOwner() != null`**, y `getOwner()` busca al jugador **en su propio nivel**.
    Con el jugador en **otra dimensión (o desconectado)** devolvía `null`, así que el minion moría y su UUID se
    quedaba en la lista **para siempre**: el cupo (3 lobos) se llenaba de fantasmas y el jugador podía acabar con
    más minions de los que debería. Ahora los tres usan `ITamableEntity.resolveOwnerForRemoval()`, que si
    `getOwner()` falla busca al jugador en **todo el servidor** por su UUID. (De paso: el `die()` de los tres
    hacía `if (minionCap == null) return;`, que se saltaba `customOnDeath()`; ahora ya no.)
  - **Autolimpieza de copias huérfanas**: cada copia se marca `Stowed`. `true` = se hizo al **sacar** el minion
    del mundo al desconectarse (si al entrar no aparece, se **recrea**: la copia manda). `false` = **foto
    periódica** de un minion que sigue en el mundo: si al entrar no aparece (se cargan también las 8 casillas de
    alrededor de su última posición, porque la foto puede ser de hasta 10 s antes y pudo andar una casilla) es
    que **murió mientras no mirabas**, así que **no se resucita**: se apunta un fallo y al segundo se quita de
    las listas. Las copias antiguas (sin el campo) se tratan como `Stowed`, que es lo que eran.
  - **Logs de diagnóstico** (todos con la etiqueta `[Minion]`): al capturar, una línea por minion guardado con
    tipo, UUID, dimensión, posición y salud; al entrar, cuántas copias hay, una línea por minion (adoptado o
    recreado) y las que no se pudieron recuperar; y al traerlos, una línea por minion vivo.
  - **Anti-duplicado (bug que ya existía)**: al invocar se **mira** el minion más viejo sin quitarlo de la lista
    hasta confirmar que existe de verdad; antes se hacía `poll()`/`remove()` a ciegas, así que un minion
    descargado se olvidaba pero seguía vivo y al cargarse tenías uno de más (p. ej. 4 lobos). El cupo es el
    tamaño de la lista, no lo que esté cargado.
  - **Rendimiento (cache de las listas)**: `getSoulWolfMinions`/`getSoulBearMinions`/`getWispMinions`
    deserializaban el `byte[]` del NBT (**serialización Java**, lo más caro del proceso) **en cada llamada**, y el
    HUD de retratos las pedía **tres veces por frame**. Ahora la capability cachea las tres colas y las invalida
    en `deserializeNBT` (que es por donde entra el NBT sincronizado); los *setter* guardan directamente la cola
    que reciben. La cache devuelve **la misma** `ConcurrentLinkedQueue`: es segura entre hilos (el render y el
    hilo principal la tocan a la vez) y su iterador es *weakly consistent*, así que quitar elementos mientras se
    recorre (lo que hace `removeAllSoulWolf` y compañía) no revienta. Dos detalles si se toca: `getAllMinions()`
    devuelve una cola **nueva** a propósito (si reutilizara la del oso, le metería dentro los lobos y los wisps),
    y los getter ya **no devuelven `null`** (cola vacía si el dato falta o está corrupto), con lo que se acabaron
    los `NullPointerException` latentes y el spam de errores en el log.

- **`soulvine` (Soulvine): modo puente**. La vid ya no nace "pegada" a una pared: al lanzarla crece
  **recta en la dirección de la mirada** (en **3D**, así sirve también para bajar por una barranca o subir), sin
  necesitar ningún sólido al lado, así que **cruza abismos y barrancas como un puente**. En cuanto **topa** con
  suelo, pared o techo **y todavía le queda crecimiento**, se apaga el modo puente y sigue con la **mecánica de
  siempre** (elegir dirección y agarrarse a las superficies, con la regla del sólido perpendicular). Detalles:
  - El modo se guarda en el **BlockEntity** (`bridging`, persistido en NBT) y **lo heredan los hijos**, para no
    ampliar el espacio de estados del bloque (`LEVEL` ya se movió al BlockEntity por eso mismo).
  - Antes la skill exigía **una pared al lado** para poder lanzarse (`hasAtLeasOneSolidNeighbour…` en la
    precondición), y después solo que el sitio de delante estuviera libre. Con la raíz a los pies basta con que
    el bloque donde estás parado se pueda ocupar.
  - Si mirar en vertical no deja sitio para el bloque de delante, la **dirección "de cara"** del primer bloque
    cae a la **horizontal** de la mirada (solo es cosmética: el crecimiento lo manda la trayectoria).
  - **Trayectoria de la mirada (escalera tipo DDA)**. La vid guarda el **vector exacto** de la mirada
    (`aimX/aimY/aimZ`, normalizado) y un **error acumulado por eje** (`errX/errY/errZ`). En cada paso se suma
    el **peso** de la mirada en cada eje (los pesos son `|aim|` normalizado a **suma 1**, así cada paso reparte
    exactamente un bloque y el error se queda acotado: con los pesos sin normalizar el eje dominante se
    desbordaba y la escalera salía más plana que la mirada) y se **descuenta un bloque entero** al eje que de
    verdad avanzó; el eje con más error es el que avanza. Así una mirada a **45° da una escalera de 45°**
    (alterna los dos ejes) y una casi horizontal avanza casi siempre en horizontal con un escalón de vez en
    cuando. Si un paso no se puede dar (pared), ese eje **no descuenta nada** y lo reintenta en el paso
    siguiente.
  - **El bloque raíz nace en el bloque donde está parado el jugador**, es decir justo encima del bloque que
    pisa, para que la vid **salga del suelo** y no parezca que flota (antes nacía un bloque por delante y, si
    el terreno de al lado estaba más bajo, el primer bloque quedaba en el aire). Como el bloque de vid es
    `noCollission`, el jugador puede quedarse dentro sin que le empuje; la precondición de la skill ahora solo
    pide que **ese** sitio se pueda ocupar (aire, reemplazable o con fluido, para poder lanzarla nadando), y la
    dirección "de cara" del primer bloque sigue saliendo de la mirada (con respaldo horizontal si apuntas al
    suelo que pisas).
  - **Al topar con algo** el paso ideal ya está bloqueado, así que se eligen las alternativas **ordenadas por
    producto escalar con la mirada** (la que menos se aparta de la trayectoria va antes) y, **en caso de
    empate**, primero **el rumbo que ya traía la vid** (para no zigzaguear cuando el eje de la mirada está
    tapado) y después el orden base **ARRIBA → ABAJO → lados**. Es decir: la vid **sigue el contorno del
    obstáculo** (baja por la pared si mirabas hacia abajo, trepa si mirabas de frente) sin abandonar la
    trayectoria, y solo rodea cuando no le queda otra. *(Antes la lista se ordenaba por
    `Direction.get3DDataValue()`, y como DOWN=0 va antes que UP=1 la vid **se iba siempre hacia abajo**.)*
  - Con trayectoria, el paso que sigue la mirada **no exige sólido perpendicular** (es el puente: puede ir
    por el aire); los **desvíos sí** lo exigen, que es lo que la hace "agarrarse" al contorno del obstáculo.
    El bloque "de más allá" (cuando el hueco contiguo está libre pero sin sólido perpendicular) usa el mismo
    criterio de orden.
  - El algoritmo está **simulado fuera del juego** en `build/vinesim.py` (herramienta de `build/`, ignorada
    por git): imprime la escalera de varias miradas, el camino contra una pared y contra un suelo, y la
    comprobación a 200 pasos de que el ángulo del camino **clava** el de la mirada (`cos = 1.0` en todas) y
    de que el error no deriva. Útil porque las pruebas en juego tardan y la vid dura ~46 s.
  - **El reloj de la vid se guarda en el NBT** (`timeOfCreation`), junto al `skillLevel`, el `bridging` y toda
    la trayectoria (`hasAim`, `aim*`, `err*`), y los hijos lo heredan. Antes vivía **solo en memoria**: al salir
    de la partida y volver a entrar el bloque se cargaba sin reloj, así que la vid **rejuvenecía entera** y
    empezaba a envejecer de cero (y a vivir de nuevo completo). Las vids guardadas antes de este arreglo no
    tienen el dato y se reinician una última vez.
  - Para poder verificarlo desde fuera: `[Soulvine] el puente topo con …` y, en cada cambio de rumbo,
    `[Soulvine] la vid cambia de rumbo: A -> B en P` (ambos `DEBUG`, en `run/logs/debug.log`).

- **Fusión parásito + hongo (árbol de Naturaleza)**: el poder es **`soullichen`** (*Soullichen*, el parásito  que se pega al enemigo y lo lentece/consume) y el **hongo** (`vinefleshball`, *Parasyte mushroom*) pasó de
  ser un poder aparte a ser su **pasivo de 20 niveles** (`activeSkill = false`, `manacost` 0, `frame` "goal"):
  en el árbol ya no se puede asignar a una tecla, solo subirle niveles, y lo aplica su padre activo. Al
  impactar, el parásito aplica su atadura y, **si el jugador tiene puntos en el hongo**, infecta además con el
  hongo carnívoro (`MobEffectVineFleshPuppet`: daño con el tiempo y, si el infectado muere, del cadáver brota
  un títere de carne `SunflowerShulker` que pelea para ti). Los puntos ya invertidos se conservan, y el nivel
  del hongo escala la duración/amplificador de la infección **y** la fuerza del títere. Nota: el ejecutor
  viejo (`SkillVineFleshPuppet`) sigue existiendo, así que si tenías el hongo asignado a una tecla esa tecla
  sigue lanzando la bola de esporas (ahora redundante); se puede reasignar al parásito.

- **Guarida (`LairManager`)**: `MAX_LAIR_MOBS` 30 (cupo de enemigos vivos por guarida, sin el guardián),
  `GUARDIAN_RESPAWN_TICKS` 3 min (relevo del guardián si no rompes el núcleo), `SPAWN_INTERVAL_TICKS` 25 s,
  `ACTIVATION_RADIUS` 64, `CORE_AURA_RADIUS` 8, `CORE_FANG_TICKS` 4 s, `MIN_DISTANCE_FROM_OBJECTIVE` 75.

- **Puntos de habilidad por misión**: se pagan **en experiencia** (`util/MissionRewards`), nunca en puntos
  sueltos: aldea salvada = **1 nivel** (`VillageManager.REWARD_EXPERIENCE_LEVELS`) y núcleo de guarida destruido
  = **2 niveles** (`LairManager.LAIR_REWARD_EXPERIENCE_LEVELS`). Cada nivel trae su punto de habilidad por el
  camino normal (`PlayerXpEvent.LevelChange` → `setCurrentLevel`, 1 punto por nivel). Antes eran
  `siegeSkillPoints` (3 + índice/4, tope 8) y `lairSkillPoints` (4 + índice/3, tope 10) regalados con
  `addUnspentPoints`, que **no subían nada la experiencia**; también se quitaron los `giveExperiencePoints`
  sueltos (50 en la aldea, 40 + 15·índice en la guarida) para que el premio sea exactamente los niveles dados.
  - **Lo REPETIBLE se paga en fracción**: rechazar una horda del mundo da **1/6 de punto**
    (`VillageManager.WORLD_SIEGE_REWARD_FRACTION` = 6) con `giveSkillPointFraction`, que es la sexta parte de la
    barra del nivel del jugador: escala con él (7 XP a nivel 0, ~18 a nivel 30) y seis rechazos hacen un nivel.
    Un nivel entero por horda era un punto por horda cada pocos minutos. Regla general: **recompensa que se
    puede repetir sin límite → fracción; recompensa de un solo uso (aldea clásica, núcleo de guarida) → nivel**.

- **Bola de fuego del zombi agresivo (`FireballAttackGoal`)**: dispara solo entre **3 y 16 bloques** y con
  **línea de visión**; si no puede, reintenta cada **20 ticks** (1 s) en vez de esperar los 240 completos.
  Lanza `ZombieFireball` (hereda de `SmallFireball`): **no incendia el terreno**. El `SmallFireball` de vanilla
  hacía `setBlockAndUpdate(pos, FIRE)` al chocar, así que cada disparo que daba en el suelo dejaba un foco
  ardiendo —en partida se veía como "fuego que aparece al azar" y con la aldea de madera era un incendio
  asegurado—. La subclase apaga el fuego que el proyectil acaba de encender (comprobando `Blocks.FIRE`/
  `SOUL_FIRE` antes de borrarlo). El daño a entidades no cambia: al que le da, le sigue prendiendo. No hay que
  registrar tipo ni renderer: el cliente dibuja con el renderer de vanilla porque el tipo sigue siendo
  `minecraft:small_fireball` (los proyectiles son `noSave`, no se serializan).

- **Al morir el jugador (penalización de XP)**: `PlayerCapabilityForgeEventSubscriber` (`XP_KEPT = 0.95`) — se
  **conserva el nivel** y solo se pierde el **5% de la experiencia del nivel** (la barra se queda al 95% de
  donde estaba); hay que volver a ganar esa experiencia para seguir subiendo. Antes estaba **mal**: hacía
  `nivel × 0.9`, o sea que morir a nivel 42 te dejaba en 37 (perder niveles), justo lo contrario a la
  intención. Como el nivel del mod (puntos de habilidad) se deriva de `experienceLevel`, tampoco se pierden
  puntos de habilidad. Al reaparecer, el jugador recibe un aviso en el chat con el porcentaje y cuánto:
  *"Has muerto: pierdes el 5% de la experiencia de tu nivel (2 de 45 puntos). Conservas el nivel 42: vuelve a
  ganar esa experiencia para seguir subiendo."* (si no había experiencia acumulada en el nivel, no se avisa).

- **Primera lectura de la piedra de lore (`LoreStoneBlock`)**: la piedra del centro del círculo ritual regala
  **la experiencia justa para subir un nivel** (`getXpNeededForNextLevel()`, o sea el tamaño de la barra del
  nivel actual), **una sola vez por jugador**: el flag `loreStoneRead` se guarda en la capability auxiliar del
  jugador (NBT), así que sobrevive al reinicio; sin ese flag la piedra sería una granja de XP infinita. Al
  subir de nivel el mod concede además el punto de habilidad correspondiente (evento `PlayerXpEvent.LevelChange`),
  así que la primera lectura es también el primer punto del árbol. Avisa en el chat ("La piedra te bendice: +N
  de experiencia (subes de nivel).") y lo registra como `[LoreStone]`.

### 5.1 Perfiles de spawn (`SpawnScaleProfile`): qué hace cada campo y cada instancia

`SpawnScaleProfile` es un `record` **neutro** (no depende de ninguna entidad): es el **origen único de verdad**
de la escalación por distancia. Lo comparten la entidad (para escalar vida/velocidad/daño/XP) y su
`SpawnRule` (para la probabilidad de spawn), sin acoplarse entre sí.

**Campos** (`minDistance`, `maxDistance`, `minHardDistance`, `maxScaleMultiplier`, `baseHealth`, `baseSpeed`,
`baseDamage`, `baseXp`, `maxXpMultiplier`).

**Fórmulas** (todas reciben `distance` = distancia horizontal al **punto de ancla del jugador** y
`threat` = `ThreatLevel.current(level)` en `[0,1]`):

| Función | Fórmula | Comentario |
|---|---|---|
| `effectiveMinDistance(threat)` | `min + (minHard − min)·threat` | la zona protegida se **encoge** con el tiempo (3 h a amenaza 1) |
| `normalize(d, threat)` | `0` si `d ≤ min`; **`1` si `d ≥ maxDistance`**; si no `(d − min)/(max − min)` | **está acotada en [0,1]** |
| `probability(d, threat)` | `= normalize` | probabilidad de spawn (0 en la zona protegida, 1 al llegar a `maxDistance`) |
| `scaleFactor(d, threat)` | `1 + normalize·maxScaleMultiplier` | multiplicador de atributos |
| `experienceReward(d, threat)` | `round(baseXp·(1 + normalize·maxXpMultiplier))` | XP que suelta al morir |

**Respuesta a "¿qué pasa si se pasa de la distancia máxima?"**: **nada más crece, todo se satura.**
`normalize` devuelve `1.0` en cuanto `d ≥ maxDistance`, así que a partir de ahí:
`probability = 1` (siempre spawnea), `scaleFactor = 1 + maxScaleMultiplier` **constante** y
`experienceReward = baseXp·(1 + maxXpMultiplier)` **constante**. El `maxScaleMultiplier` se aplica **entero**
pero no sigue aumentando por mucho que te alejes. (Antes de eso la curva es lineal entre el mínimo efectivo y
`maxDistance`.)

**Ojo, el tiempo se multiplica ENCIMA del factor de distancia.** En `AggressiveZombieEntity` y
`FrostVexEntity` el factor final es:

```
factor = SPAWN_PROFILE.scaleFactor(spawnDistance, spawnThreat) · (1 + spawnThreat · ThreatLevel.MAX_EXTRA_DIFFICULTY)
```

o sea que a **máxima distancia + amenaza máxima** (3 h de partida) el multiplicador real es
`(1 + maxScaleMultiplier) · 1.8`:

| | zombie agresivo (max 3000) | vex helado (max 1000) |
|---|---|---|
| factor máximo | `4.5 · 1.8 = 8.1×` | `3.5 · 1.8 = 6.3×` |
| vida | 9 → **≈73** | 6.67 → **≈42** |
| velocidad | 0.068 → **≈0.55** | 0.077 → **≈0.49** |
| daño | 0.7 → **≈5.7** | 0.34 → **≈2.1** |
| XP al morir | 20·5.5 = **110** | 5·5.5 = **≈28** |

(Los números del zombie son con `maxScaleMultiplier` 3.5; el usuario lo bajó desde 3.7 al arreglar la vida al
spawnear, porque los escalados pasaron a durar mucho más.)

**Instancias que existen hoy** (solo hay dos perfiles):

| Perfil | Lo usan | Para qué |
|---|---|---|
| `AggressiveZombieSpawnProfile.INSTANCE` | `AggressiveZombieEntity` | atributos base + escalado + XP |
| | `SculkCultivatorEntity` (guardián de la guarida) | atributos base + **escalado por distancia/amenaza y XP** (tiene su propio `setPos`/`adjustAttributesBasedOnSpawnDistance`, igual que el zombie; además se cura a tope al escalar) |
| | `AggressiveZombieSpawnRule` | `probability` + `effectiveMinDistance` para el spawn natural |
| | `HordeManager` | `probability` (cada miembro de la horda) y `effectiveMinDistance` (quién puede recibir horda) |
| `VexSpawnProfile.INSTANCE` | `FrostVexEntity` | atributos base + escalado + XP |
| | `VexSpawnRule` | `probability` + `effectiveMinDistance` para el spawn natural de vexes |

**Dónde se configura la amenaza (`ThreatLevel` → `devilrpg-server.toml`, sección `[threat]`)**:
`threatMaxExtraDifficulty` (por defecto **0.8** = +80%) y `threatFullHours` (por defecto **3** h). Antes eran
constantes dentro de `ThreatLevel` (`MAX_EXTRA_DIFFICULTY` pública y `FULL_THREAT_TICKS` privada), así que
había que recompilar; ahora se ajustan en caliente con `/reload`-style (reiniciando el mundo o con el comando
de recarga de configs). `current(level) = clamp(level.getGameTime() / fullThreatTicks(), 0, 1)` y ojo:
`getGameTime()` son ticks **con el mundo cargado**, o sea *tiempo jugado* (no tiempo real; con el juego cerrado
no avanza). **Consumidores**: los **tres** mobs que capturan `spawnThreat` al spawnear (zombie agresivo, vex
helado y cultivador), las dos reglas de spawn (`probability` + `effectiveMinDistance`) y `HordeManager`
(intervalo 20 min → 3 min, tamaño 3 → 15 y probabilidad). En el log se ve la amenaza de cada spawn:
`Zombie Spawned at: ... | Threat: 0.42`.

**Quién NO usa el perfil** (spawns dirigidos por evento, no por probabilidad): las oleadas del **asedio** a la
aldea (`VillageManager.spawnWave`: `8 + min(índice·2, 20)` mobs a 32–40 bloques del centro), los enemigos de
la **guarida** (`LairManager.spawnWave`: 3 cada 25 s a 8–14 bloques del centro, tope 30 vivos) y los
`preGenerate`/spawns de mobs concretos. Sus atributos sí salen del perfil (al spawnear la entidad lee
`spawnDistance`/`spawnThreat` igual que cualquier otra).

**Detalles que conviene tener presentes**:
- El escalado se aplica **UNA vez**, en el primer `aiStep` tras spawnear (`attributesAdjusted`), y
  `spawnDistance`/`spawnThreat` se **capturan al spawnear** (`setPos`): un mob que nace lejos y luego se
  acerca sigue siendo fuerte, y uno que nace cerca y se aleja sigue siendo débil. Al **recargar** la partida,
  `spawnDistance` se recalcula con la posición actual (el campo no se guarda en NBT).
- Dentro de la **zona protegida** (`spawnDistance < minDistance`) el zombie **no escala nada** (sale con las
  bases del perfil).
- **Vida al spawnear (arreglado)**: antes, al escalar se subía la vida **máxima** pero no la **actual**, así
  que un zombie escalado nacía con vida `baseHealth` (9) y máximo escalado (hasta ≈73): o sea **herido**, y el
  escalado de vida casi no se notaba en combate. Ahora, tras aplicar el escalado, el zombie agresivo sube su
  vida actual al nuevo máximo (`setHealth(getMaxHealth())`), igual que ya hacía el vex helado. Efecto de
  balance: un zombie lejano aguanta mucho más que antes (hasta ×8.1 de vida a máxima distancia + amenaza), y
  por eso el `maxScaleMultiplier` se bajó de 3.7 a 3.5.
- **Zona protegida del vex (`VexSpawnProfile`)**: el comentario decía antes "sin zona protegida", pero el valor real
  es **67** (heredado del zombie) — el vex también respeta zona protegida, solo que pequeña. **Ya está corregido**:
  el comentario dice "zona protegida pequeña (antes 0)", con el 67 al lado (`VexSpawnProfile.java:5,15`).

- **UI del árbol de skills (`SkillScreen`)**: el **fondo** y el **skin de widget** de los nodos ya están
  elegidos, así que los dos selectores están **ocultos** (llamadas comentadas en `SkillScreen` con la nota de
  qué descomentar para recuperarlos; al recuperarlos hay que reactivar también `applySavedSelection()` en
  `SkillBackgroundManager` y el bloque de la config en `SkillWidget.applyDefaultTheme()`):
  - Fondo fijo: `SkillBackgroundManager.DEFAULT_BACKGROUND` = `openart-image_uq0j3jlc_1706511264937_raw.png`.
    Las demás imágenes siguen en `textures/gui/mandalas` (no se borra ninguna).
  - Skin de widget fijo: `SkillWidget` usa `a-gui-texture-widget-for-rpg-game-celtic-style-forest_94.png`
    (comparación por nombre de archivo exacto). El atlas original es el único que encaja 100% con las
    coordenadas fijas con las que se recortan los marcos; los skins generados por IA traen textos y formas
    horneadas y se ven recortados.
  - Mientras los selectores estén ocultos **no se lee** la preferencia guardada en la config de cliente
    (`skills_ui.skillBackground` / `skills_ui.skillWidgetSkin`), para que mande siempre el valor por defecto
    del código. La lista de skins se lee con el `ResourceManager` (recorrer la carpeta del classloader con
    `Files.walk` devolvía vacío dentro del jar) y excluye los `template*`.
- **Ranuras de skill (pantalla y HUD), sin caja oscura**: el `empty-box.png` (la caja negra) **ya no se pinta
  en ninguna ranura**: en `CustomSkillButton` solo se dibuja el icono de la skill asignada, y en
  `SkillsIconHudOverlay` el icono del HUD se dibuja solo si el poder tiene skill con imagen. Además, en el
  **HUD** una ranura sin skill **no muestra nada** (ni icono ni nombre de tecla), mientras que en la
  **pantalla de habilidades** las teclas se muestran **siempre** (allí los huecos son espacios de asignación y
  la tecla es la referencia para asignar). Además la barra del HUD **se compacta**: las ranuras sin skill no
  ocupan sitio, así que las skills asignadas quedan pegadas a la izquierda sin huecos en medio. El borde
  iluminado al pasar el mouse se mantiene.

> ✅ HECHO (Iteración 3, apartado 3.1 punto 2): las hordas apuntan al **asentamiento más cercano** (el más
> descuidado por presión) en vez de al jugador, y si no hay ninguna aldea candidata van a por él como antes
> (`HordeManager` + `VillageSavedData`). Lo que queda de la Iteración 3 está cerrado; lo siguiente del roadmap es
> la **Iteración 4** (el abismo vertical) y la **Iteración 5** (los enemigos fortifican su base), que sigue
> PENDIENTE.

---

## 6) Notas de trabajo (para el agente)

- **ANTES DE CADA COMMIT DE ALDEA**: pasar `python tools/lint_aldea.py --strict` y repasar la **lista de
  consecuencias** de `docs/aldea-invariantes.md` (quién más lee el valor que toco, si depende de una altura/si es la
  cota, si cambia el mundo guardado y hay que subir la migración, si es idempotente, si entra en el plano, cliente vs
  servidor, rendimiento, casos raros, cómo lo compruebo y si afecta a lo que el jugador ya tiene). Casi todos los
  bugs de aldea que reportó el jugador fueron **una sola clase** (una Y que no era la cota) y varios salieron de
  arreglar el síntoma sin barrer el resto: el lint y esa lista existen para eso.
- **Minecraft bloquea el jar de NeoForge** (`build/moddev/artifacts/neoforge-*.jar`) mientras está abierto, así
  que `gradlew compileJava` falla con `AccessDeniedException`. **Mata el proceso sin preguntar** (no molesta
  al usuario): busca `java.exe` con `DevLaunch|fml.modFolders` en la línea de comandos y haz
  `Stop-Process -Force`. El jugador tendrá que reiniciar el juego de todas formas para probar los cambios.
- **Commitea todos los cambios**, incluidos los **ajustes de balance del usuario** que aparezcan en el árbol
  de trabajo (perfiles de spawn, intervalos...). No los dejes fuera por "no ser míos".
- Comando de compilación:
  `$env:GRADLE_USER_HOME="C:\Users\Christian\Documents\DevilRpg\.gradle-home"; .\gradlew.bat compileJava --console=plain`
- **Las guaridas se regeneran** al acercarse al objetivo (el estado de `LairManager` es en memoria), así que
  los cambios de `LairGenerator` se ven al reiniciar y volver al objetivo, no hacen falta mundos nuevos.
- **Herramientas de NBT para recuperar partidas** (`tools/recover/NbtTool.java`, `tools/finduuid.py` y
  `tools/nbtdump.py`: **versionadas**, y explicadas en `tools/README.md`). Los ~130 scripts sueltos de diagnóstico
  siguen en `build/`, **ignorados por git**: si se limpia `build/`, se pierden los sueltos, **no** estas. Leen y
  escriben el NBT del jugador con las **clases reales de Minecraft**, sin arrancar el juego:
  `java -cp "build\classes\java\main;build\recover\libs\*" tools\recover\NbtTool.java <archivo.dat> [--keys|--find|--snbt <i>]`
  (`build\recover\libs\` se armó copiando de las cachés de Gradle el jar `neoforge-*-merged.jar` + fastutil/log4j/logging/asm...
  y **no** se versiona por tamaño: si se limpia `build/`, hay que rehacerlo).
  El modo `--restore-minions-from <viejo.dat> <tipoWisp> <destino.dat>` copia `Stored_Minions` de un respaldo, y
  `--forget <uuid>…` quita UUIDs concretos de las listas (solo tras comprobar con `tools/finduuid.py` que **no
  existen en ningún archivo de región**, o sea que están muertos de verdad).
  **`build/nbtdump.py` es un puente** a `tools/nbtdump.py` (el bueno): los scripts sueltos siguen haciendo
  `from nbtdump import ...` y cargan el de `tools/`, así hay **una sola copia** del lector.
- **⚠️ EN SINGLEPLAYER EL ANFITRIÓN SE GUARDA (Y SE LEE) EN `level.dat`, NO EN `playerdata/<uuid>.dat`.** El
  jugador está en `Data.Player.neoforge:attachments...` de `level.dat`; el juego **también** escribe
  `playerdata/<uuid>.dat` con lo mismo, pero **lo que lee al cargar el mundo es `level.dat`**. Esto costó una
  tarde entera el 12-sep-2026: toqué `playerdata/<uuid>.dat` (inyecté 4 minions, limpié listas) y **nada de eso
  llegó al juego**; peor, la lista "limpia" parecía volver con los fantasmas en su sitio (en realidad el juego
  nunca vio mi archivo). Para tocar la partida hay que escribir en **`level.dat`** (y, por coherencia, en el
  `playerdata` para que no se contradigan). Comprobar SIEMPRE con `NbtTool … --find` en **los dos** archivos, y
  **con el juego cerrado**: al salir, el juego escribe ambos desde memoria y revienta cualquier cambio.
