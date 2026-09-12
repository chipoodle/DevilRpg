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
- **La recompensa paga puntos de habilidad**: salvar la aldea da hierro, cuero, un libro, 50 de XP vanilla y
  **puntos de habilidad** (`siegeSkillPoints`: 3 + 1 por cada 4 objetivos ya superados, tope 8). Son la misma
  moneda del árbol de skills (1 por nivel de experiencia) y llenar **todos** los árboles pide nivel 300+, así
  que las misiones también empujan la progresión. Se cobran con `PlayerExperienceCapability.addUnspentPoints`
  (que suma a `unspentPoints` y sincroniza con el cliente) y el jugador lo ve en el chat
  ("La aldea te lo agradece: +N puntos de habilidad."). Si la aldea **cae** no hay recompensa (ni puntos).
- **Las aldeas de objetivos ya superados siguen vivas** (`VillageManager.manageNearby`, llamada desde
  `ObjectiveManager.tick` junto a `LairManager.preGenerateNearby`): cualquier aldea a menos de
  `PRE_GENERATE_RADIUS` (140) del jugador se pre-genera, avisa y **puede asediarse**, aunque su objetivo ya
  esté superado. Antes, al avanzar de objetivo la aldea anterior dejaba de gestionarse: si volvías, no
  pre-generaba, no avisaba y no se podía asediar (aldeas "muertas" por el mundo). Los tres pasos son
  idempotentes (pre-generado, avisado y resuelto se guardan en `VillageSavedData`), así que llamarlo cada tick
  no repite nada.
- **El núcleo de la guarida también paga puntos de habilidad** (`lairSkillPoints`: 4 + 1 por cada 3 objetivos,
  tope 10): algo más que salvar una aldea porque asaltar la guarida es más duro y más largo.

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
- **3 cabañas** con interior de 3 bloques de alto, puerta, cama completa (pie+cabeza), escaleras alineadas
  a la puerta y cimientos con pilares si están sobre agua.
- **Caminos de 2 bloques de ancho** en el plano XZ, de tierra apisonada, a ras de suelo, que no pasan
  sobre las cabañas ni la campana.
- **Campana** en el centro (sobre soporte de piedra, columna limpia).
- **Golem de hierro** de guardia (Y fijada al suelo de la isla para no sofocarse).
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
  (2.5 + 0.12·puntos). Se dibuja con `textures/entity/frostball/freeze_texture.png` mediante un renderer
  billboard propio (`IceSpearRenderer`): al ser una textura de entidad (fuera de `textures/item` y
  `textures/block`) **no está en el atlas de bloques**, así que un modelo de item la mostraría como textura
  perdida — de ahí el quad a mano con `RenderType.entityCutoutNoCull`, que además siempre mira a la cámara y
  nunca se ve "de canto".
- Las partidas guardadas reciben las **skills nuevas** del mod automáticamente: al cargar, la capability de
  skills añade al NBT del jugador las claves que falten (puntos, nivel máximo, coste de maná, tipo de recurso
  e icono) tomándolas de una copia por defecto creada al construir la capability. Sin eso, una skill añadida
  después de guardar la partida aparecía como 0/0 en el árbol y reventaba con `NullPointerException` al
  pulsarla (su nivel máximo era `null`).

### 3b.5 Nota de diseño sobre el motor vanilla
Las villas **no** se generan con el motor vanilla (Jigsaw/`StructureTemplate`), porque ese sistema es
data-driven y coloca estructuras por bioma, no en una coordenada determinista del objetivo. Mantenemos el
generador propio de `VillageGenerator`. (Posible mejora futura: reutilizar `StructureTemplate` solo para
las cabañas.)

---

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

#### 3.1 Primer paso: las hordas apuntan al asentamiento, no al jugador (PLAN, no implementado)
Hoy **todo gira alrededor del jugador**: `AggressiveZombieEntity` tiene en el `targetSelector` prioridad **1**
un `NearestAttackableTargetGoal<Player>`, y `HordeManager.spawnHorde` elige un *jugador* y lanza la horda a
20–44 bloques de él. La aldea solo se asedia cuando el jugador **llega** (`VillageManager.start`). La
intención es que el mundo tenga **sus propios conflictos** y que el jugador sea quien decide intervenir.

Lo bueno: **casi toda la fontanería ya existe**. `AggressiveZombieEntity` ya tiene `villageCenter`,
`goToCenterActive` y `MoveToVillageCenterGoal` (prioridad 7), y `VillageManager` ya sabe resolver un asedio
con su premio y su estado guardado. El trabajo es **de quién es el objetivo y quién dispara el asedio**:

1. **`SettlementRegistry`** (nuevo, o extender `VillageSavedData`): por cada aldea pre-generada guardar su
   centro, radio de valla, estado (`próspera` / `asediada` / `caída`) y una **presión** numérica. La presión
   crece con el **tiempo de juego** (determinista, así avanza aunque no haya nadie cerca ni el chunk esté
   cargado) y con los enemigos que sobrevivan cerca; se reinicia al salvar la aldea y salta al máximo cuando
   cae. Persistirla en `VillageSavedData` (hoy los asedios en curso **no** se persisten a propósito: con
   asedios dirigidos por el mundo, la presión **sí** debe persistir).
2. **`HordeManager` elige asentamiento, no jugador**: en lugar de `pickPlayer`, busca la aldea **más cercana**
   al ancla del jugador (o la de mayor presión) y lanza la horda **entre la aldea y el monte**, a 32–48
   bloques del centro y **fuera de la valla** (`FENCE_RADIUS` = 29). A cada zombie se le pone
   `setVillageCenter(aldea)` + `goToCenterActive = true`, así que **marchan a la aldea** usando los goals que
   ya existen. La probabilidad/tamaño siguen saliendo del `SpawnScaleProfile` (distancia al ancla + amenaza),
   como ahora.
3. **Objetivos del enemigo en modo asedio**: en `AggressiveZombieEntity`, cuando el zombie va a un
   asentamiento (`villageCenter != null`), subir la prioridad de atacar **aldeanos y defensas**
   (`Villager`, `IronGolem`, gatos/perros… o sea los mobs de la aldea) **por encima** del jugador, y dejar al
   jugador como objetivo normal si él les dispara (`HurtByTargetGoal`). Fuera de un asedio, el
   comportamiento actual no cambia (el jugador sigue siendo el objetivo prioritario).
4. **La defensa de la aldea**: campana de alarma (el jugador la oye desde lejos y el HUD/objetivo avisa),
   aldeanos refugiándose en las casas (vanilla ya lo hace al huir), golems defendiendo (vanilla), y la
   **muerte de aldeanos** bajando la "salud" del asentamiento. Si la presión llega al máximo y no hay nadie
   defendiendo, la aldea **cae**: los aldeanos supervivientes huyen, el asentamiento pasa a `caída` y deja de
   dar recompensas (y el jugador debe buscar otra, como pide el pilar 3).
5. **El jugador decide**: si va a defenderla a tiempo, asedio normal y premio actual (`siegeSkillPoints`);
   si la ignora, la pierde para siempre. Es la diferencia entre "la aldea espera a que llegues" y "la aldea
   vive su propia guerra".
6. **Alcance de esta primera entrega**: solo **hordas y zombies agresivos** (los vexes siguen siendo la
   presión personal de la noche alrededor del jugador). Los aldeanos que **construyen/reparan/cultivan/
   envejecen** son el resto de la Iteración 3 y van después.

Pendientes técnicos que hay que resolver en el camino: `villageCenter` **no se guarda en NBT** (un zombie
asediador que se recarga pierde su destino), el cupo de mobs y los logs (`[Village]`, `[Horda]`) para poder
seguir el asedio, y que el asedio dirigido por el mundo **no** dispare la recompensa si el jugador no está
cerca (o sí, como "noticia" en el chat).

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

- **Amenaza**: `ThreatLevel.MAX_EXTRA_DIFFICULTY` (0.8 = +80%) y `FULL_THREAT_TICKS` (3 h).
- **Perfil del zombie (`AggressiveZombieSpawnProfile.INSTANCE`)**: `minDistance` 67, `maxDistance` 3000,
  `minHardDistance` 17, `maxScaleMultiplier` 3.7, `baseHealth` 9, `baseSpeed` 0.068, `baseDamage` 0.7,
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
  `maxDistance` 1000, `maxScaleMultiplier` 2.5, `baseHealth` 6.67, `baseSpeed` 0.077, `baseDamage` 0.34,
  `baseXp` 5, `maxXpMultiplier` 4.5. Spawnea de día y de **noche**, a **12–24 bloques del jugador**, límite
  **15 vivos**, intervalo 20 s–2 min. **Detalle completo en 5.1.**
- **Aldea (**`VillageGenerator`/`VillageManager`)**: `FENCE_RADIUS` 29, `LEVEL_RADIUS` 31,
  `GRACE_TICKS` 90 s, `SIEGE_TIMEOUT_TICKS` 2 min, `DEFAULT_WAVE` 8 + `min(objectiveIndex*2, 20)`,
  oleadas a 32–40 bloques del centro (fuera de la valla).

- **Guarida (`LairManager`)**: `MAX_LAIR_MOBS` 30 (cupo de enemigos vivos por guarida, sin el guardián),
  `GUARDIAN_RESPAWN_TICKS` 3 min (relevo del guardián si no rompes el núcleo), `SPAWN_INTERVAL_TICKS` 25 s,
  `ACTIVATION_RADIUS` 64, `CORE_AURA_RADIUS` 8, `CORE_FANG_TICKS` 4 s, `MIN_DISTANCE_FROM_OBJECTIVE` 75.

- **Puntos de habilidad por misión**: aldea salvada = `siegeSkillPoints` (3 + índice/4, tope 8); núcleo de
  guarida destruido = `lairSkillPoints` (4 + índice/3, tope 10).

- **Bola de fuego del zombi agresivo (`FireballAttackGoal`)**: dispara solo entre **3 y 16 bloques** y con
  **línea de visión**; si no puede, reintenta cada **20 ticks** (1 s) en vez de esperar los 240 completos.

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
| factor máximo | `4.7 · 1.8 = 8.46×` | `3.5 · 1.8 = 6.3×` |
| vida | 9 → **≈76** | 6.67 → **≈42** |
| velocidad | 0.068 → **≈0.58** | 0.077 → **≈0.49** |
| daño | 0.7 → **≈5.9** | 0.34 → **≈2.1** |
| XP al morir | 20·5.5 = **110** | 5·5.5 = **≈28** |

**Instancias que existen hoy** (solo hay dos perfiles):

| Perfil | Lo usan | Para qué |
|---|---|---|
| `AggressiveZombieSpawnProfile.INSTANCE` | `AggressiveZombieEntity` | atributos base + escalado + XP |
| | `SculkCultivatorEntity` (guardián) | **solo las bases** (`baseHealth/Speed/Damage`) — el guardián no escala por distancia, vive en su guarida |
| | `AggressiveZombieSpawnRule` | `probability` + `effectiveMinDistance` para el spawn natural |
| | `HordeManager` | `probability` (cada miembro de la horda) y `effectiveMinDistance` (quién puede recibir horda) |
| `VexSpawnProfile.INSTANCE` | `FrostVexEntity` | atributos base + escalado + XP |
| | `VexSpawnRule` | `probability` + `effectiveMinDistance` para el spawn natural de vexes |

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
- **Quirk conocido (sin arreglar a propósito)**: al escalar se sube la vida **máxima** pero no la **actual**,
  así que un zombie escalado nace con vida `baseHealth` y máximo escalado (o sea, herido). El vex sí se cura a
  tope (`setHealth(getMaxHealth())`) en su propio método. Arreglarlo es una línea en el zombie agresivo, pero
  cambia el balance (los escalados durarían bastante más), así que queda a decisión del diseño.
- El comentario de `VexSpawnProfile` dice "sin zona protegida" pero el valor real es **67** (heredado del
  zombie): el vex también respeta zona protegida, solo que pequeña. Comentario desactualizado.

- **UI del árbol de skills (`SkillScreen`)**: abajo hay dos parejas de botones, la izquierda para el **fondo**
  (`SkillBackgroundManager`) y la derecha para el **skin de widget** de los nodos (`SkillWidget`), cada una con
  su etiqueta `nombre  n/total`; `Def` devuelve el skin de widget al por defecto. La elección de los dos se
  guarda en la **config de cliente** (`skills_ui.skillBackground` y `skills_ui.skillWidgetSkin`), así que
  sobrevive al reinicio. La lista de skins se lee con el `ResourceManager` (recorrer la carpeta del classloader
  con `Files.walk` devolvía vacío dentro del jar) y excluye los `template*`.

> TODO (siguiente): que las hordas apunten al **asentamiento más cercano** en vez de al jugador, para
> conectar con la Iteración 3.

---

## 6) Notas de trabajo (para el agente)

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
