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
  (1 granjero, 1 herrero de armas, 1 de herramientas, 1 clérigo y 1 recolector) en orden **estable** (por UUID) y **el
  resto** es gente de sobra. Así la milicia **no le quita el granjero ni los herreros** a la aldea (que es lo que la
  dejaría sin comer y sin indumentaria) y una aldea sana de 5 aldeanos **no tiene guardia**: hacen falta **crías**.
  Si la aldea vuelve a necesitar ese oficio (muere gente), el guardia **deja la milicia** (`desalistarGuardia`).
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
  (con un punto distinto por guardia, para no apilarse) y la etiqueta dice "Patrullando el corral". Además el radio
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
  `puestosDelPueblo()` (7), y el cocinero queda fuera del reparto de obreros y de la milicia (cupo propio, como el
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
  granjeros** (ocho puestos fijos). Ojo con el detalle que lo habría roto: `slotDeProfesionFaltante` miraba "está o no
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
- **La fachada da al oeste** (a la plaza): puerta en el centro del muro oeste, **porche** con toldo y enseña, y
  **camino** desde la plaza (torcido a propósito: en recta cruzaba la **parcela de la granja**, y un camino no debe
  pisar los cultivos).
- **El solar se despeja entero** antes de levantarla —la taberna vieja **cabía dentro** de la nueva, así que sus
  muros, su forjado y su tejado se tiran de una vez— y **lo que hubiera en sus cofres se guarda antes en el almacén**:
  tirar un cofre tira su contenido al suelo (mecánica del juego) y el pueblo no puede perder lo que tenía guardado.
- **Verificado** generando una aldea nueva en un mundo de prueba (arnés temporal + servidor headless) y leyendo los
  bloques del guardado con `build/verifica_taberna.py`: suelo, postes de esquina, puerta, forjado con su hueco,
  los cinco escalones con su `facing`, las seis mesas con sus cuatro sillas, la barra, la cocina, las once camas, las
  seis arcas, el techo/tejado sin agujeros, la chimenea por encima del tejado, ningún bloque de oficio ajeno y el
  camino de la plaza.
- **Migración 44** (`CURRENT_LAYOUT`).

### 3b.27 Lo que viene
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
- **Lo siguiente**: la **verificación en partida** de la cadena entera de la comida (huerta → despensa → cocina →
  raciones) y, de ahí, lo que pida el jugador (la **cerveza** de las pipas y el **pescador con su edificio y su lago**).

## 3c) Iteración 2 — GUARIDAS ✅ (en curso)

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
  - **No pelea. Es un cobarde.** No tiene **ningún** goal de ataque ni `targetSelector`: literalmente solo
    trabaja y huye. Escala en fuerza y rapidez como el resto de enemigos, pero eso solo lo hace más duro de
    matar, no más agresivo. Sus tres labores más la huida:
  - **Pelea como una bruja debilitada**: se queda **a distancia** (radio 10, como la bruja) y lanza
    **pociones salpicadas** (`RangedAttackGoal` + `performRangedAttack`), pero con el **doble de recarga**
    (120 ticks = 6 s, frente a los 60 de la bruja) y **sin las variedades fuertes** (nada de daño fuerte ni
    veneno): de lejos frena con Lentitud, de cerca debilita con Debilidad, y en medio solo puede hacer 6 de
    daño. Su `FOLLOW_RANGE` es **32** (no 64): es el guardián de su guarida y no debe perseguir al jugador por
    medio mapa. **Antes huía** (`FleeThreatGoal`) y se alejaba demasiado, así que el asalto se convertía en
    perseguirlo; ahora planta cara. Mientras tiene objetivo no atiende la granja (el goal de ataque tiene más
    prioridad); al quedarse sin objetivo vuelve a criar, sacrificar y sembrar catalizadores.
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
    y acabarían peleándose por la navegación.
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
- El comentario de `VexSpawnProfile` dice "sin zona protegida" pero el valor real es **67** (heredado del
  zombie): el vex también respeta zona protegida, solo que pequeña. Comentario desactualizado.

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
- **Herramientas de NBT para recuperar partidas** (`build/recover/NbtTool.java` y `build/*.py`, **ignorados por
  git** porque `build/` está en `.gitignore`: si se limpia `build/`, se pierden). Leen y escriben el NBT del
  jugador con las **clases reales de Minecraft**, sin arrancar el juego:
  `java -cp "build\classes\java\main;build\recover\libs\*" build\recover\NbtTool.java <archivo.dat> [--keys|--find|--snbt <i>]`
  (`libs\` se armó copiando de las cachés de Gradle el jar `neoforge-*-merged.jar` + fastutil/log4j/logging/asm...).
  El modo `--restore-minions-from <viejo.dat> <tipoWisp> <destino.dat>` copia `Stored_Minions` de un respaldo, y
  `--forget <uuid>…` quita UUIDs concretos de las listas (solo tras comprobar con `build/finduuid.py` que **no
  existen en ningún archivo de región**, o sea que están muertos de verdad).
- **⚠️ EN SINGLEPLAYER EL ANFITRIÓN SE GUARDA (Y SE LEE) EN `level.dat`, NO EN `playerdata/<uuid>.dat`.** El
  jugador está en `Data.Player.neoforge:attachments...` de `level.dat`; el juego **también** escribe
  `playerdata/<uuid>.dat` con lo mismo, pero **lo que lee al cargar el mundo es `level.dat`**. Esto costó una
  tarde entera el 12-sep-2026: toqué `playerdata/<uuid>.dat` (inyecté 4 minions, limpié listas) y **nada de eso
  llegó al juego**; peor, la lista "limpia" parecía volver con los fantasmas en su sitio (en realidad el juego
  nunca vio mi archivo). Para tocar la partida hay que escribir en **`level.dat`** (y, por coherencia, en el
  `playerdata` para que no se contradigan). Comprobar SIEMPRE con `NbtTool … --find` en **los dos** archivos, y
  **con el juego cerrado**: al salir, el juego escribe ambos desde memoria y revienta cualquier cambio.
