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
  (por ejemplo de huevo de spawn) no le cobra a nadie. Se dibuja con `textures/entity/frostball/freeze_texture.png` mediante un renderer
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
   jugador, ya generadas, no caídas y no atacadas en ese momento. Lanza la horda **a 32–48 bloques del
   centro** (justo fuera de la valla de 29) y a cada zombie le pone `setVillageCenter(...)` +
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

**Pendiente de esta iteración** (siguientes pasos): que defender una aldea de una horda del mundo dé
recompensa propia (hoy solo reinicia la presión y avisa); "salud" de la aldea por aldeanos vivos (hoy solo
0 aldeanos = caída); aldeas caídas con aspecto de ruinas; y el resto del pilar 3 (aldeanos que construyen,
reparan, cultivan, comen y envejecen).

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
  `maxDistance` 1000, `maxScaleMultiplier` 2.5, `baseHealth` 6.67, `baseSpeed` 0.077, `baseDamage` 0.34,
  `baseXp` **15** (subido desde 5: con 5 parecía que los vexes no daban XP), `maxXpMultiplier` 4.5 (hasta ~82
  lejos de la base). Spawnea de día y de **noche**, a **12–24 bloques del jugador**, límite **15 vivos**,
  intervalo 20 s–2 min. **Detalle completo en 5.1.**
  - **Ya NO tienen vida limitada**: antes `VexSpawnRule` les ponía `setLimitedLife(2 min)` y, al agotarse,
  vanilla los mata con `damageSources().starve()` → **no contaba como baja del jugador y no soltaban XP**
  (por eso parecía que "matar un vex no da experiencia"). El tope de 15 vivos ya evita que se acumulen, así
  que ahora todos se pueden matar y dan su XP. Para revertirlo, basta con volver a poner esa línea.
- **Aldea (**`VillageGenerator`/`VillageManager`)**: `FENCE_RADIUS` 29, `LEVEL_RADIUS` 31,
  `GRACE_TICKS` 90 s, `SIEGE_TIMEOUT_TICKS` 2 min, `DEFAULT_WAVE` 8 + `min(objectiveIndex*2, 20)`,
  oleadas a 32–40 bloques del centro (fuera de la valla).

- **Asentamientos vivos (`VillageSavedData` + `HordeManager`, Iteración 3)**: `HORDE_TARGET_RADIUS` 220
  (radio respecto al jugador para buscar aldea a la que mandar la horda), `PRESSURE_MIN_TICKS` 8 min de
  juego (presión mínima para que una aldea sea objetivo), `FALLEN_CHECK_RADIUS` 48 (radio para contar
  aldeanos: 0 = la aldea ha caído), `SIEGE_WARN_RADIUS` 160 (a quién se avisa) y spawn de la horda a
  `FENCE_RADIUS + 3 … + 19` = **32–48** bloques del centro.

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

- **Puntos de habilidad por misión**: aldea salvada = `siegeSkillPoints` (3 + índice/4, tope 8); núcleo de
  guarida destruido = `lairSkillPoints` (4 + índice/3, tope 10).

- **Bola de fuego del zombi agresivo (`FireballAttackGoal`)**: dispara solo entre **3 y 16 bloques** y con
  **línea de visión**; si no puede, reintenta cada **20 ticks** (1 s) en vez de esperar los 240 completos.

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
