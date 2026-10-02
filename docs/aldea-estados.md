# La aldea por dentro: estados, relojes y rastro en el log

> **Por qué existe este documento.** El jugador dijo *"me alejé de la aldea unos cientos de cubos, volando y regresé
> antes de que nocheciera y cuando regresé ya estaba abandonada. ¡Eso es un bug enorme!"*, y **no se pudo saber por qué
> a la primera**: la aldea "abandonada" no era un estado que estuviera escrito en ninguna parte, la caída no salía en
> el log de la sesión que se miró (estaba en un log **rotado** y mezclado con el del arnés), y hubo que reconstruir la
> línea de tiempo a mano (I86). Este documento es para que **la próxima vez se sepa en un minuto**: qué estados tiene
> una aldea, quién cambia cada uno, **dónde está en el código** y **qué línea deja en el log**.

## 1. Los estados: quién los guarda y cómo se leen

### 1.1 Del MUNDO — `data/devilrpg_villages.dat` (`VillageSavedData`)

Se lee con `tools/nbtdump.py` (o con los scripts de `build/`). Claves del NBT:

| Clave | Qué es | Quién la escribe | Se lee con |
|---|---|---|---|
| `Generated` | aldeas ya construidas | `VillageManager.preGenerate` | `isGenerated(i)` |
| `Resolved` | aldeas cuyo asedio **ya se resolvió** (salvada o caída): no se vuelven a asediar | `markSiegeResolved` | `isSiegeResolved(i)` |
| `Fallen` | aldeas **caídas** (definitivo): no se repueblan ni se gestionan | `fallVillage` → `markFallen` | `isFallen(i)` |
| `Noticed` | `"<índice>:<uuid>"`: a ese jugador ya se le avisó de esa aldea | `noticeIfNear` | `isNoticed(i, uuid)` |
| `Pressure` | presión de horda por aldea (minutos sin atención) | `accruePressure` / `resetPressure` | — |
| `Settlement[]` | por aldea: `Health` (aldeanos censados), `Food`, `StarvingSince`, `Layout`, `CasasVersion`, `AnexoAnimales`, `RepopulatedAt` | el latido | `VillageSavedData.get…` |
| `Blueprints[]` | el **plano** de lo construido (paleta + posiciones): es de donde salen el centro y el nivel del pueblo | `preGenerate` y las migraciones | `getBlueprint` / `VillageManager.centroDe` |

**Al cargar**, una aldea de `Fallen` se añade también a `resolved` (`VillageSavedData.java:185`): una aldea caída no
vuelve a asediarse nunca.

### 1.2 Del JUGADOR — capability auxiliar (`PlayerAuxiliaryCapabilityImplementation`)

Viaja con el jugador (en un solo jugador: `level.dat`) y **se sincroniza al cliente**, que es quien dibuja la barra:

| Clave | Qué es | Quién la escribe |
|---|---|---|
| `objectiveIndex` | aldea que le toca | `setObjectiveIndex` (al resolverse un asedio) |
| `anchorPoint` / `spawnPoint` | el ancla del círculo ritual: con ella se calculan las posiciones de las aldeas | al entrar al mundo |
| `loreStoneRead` | ya leyó la piedra (la primera lectura da un nivel) | `LoreStoneBlock` |
| `aldeasVisitadas` | aldeas en las que **ha entrado** (descubiertas) | `visitarAldea` (al llegar, radio 24) + la siembra |
| `aldeasReveladas` | aldeas cuya **dirección** le han dado | la piedra / el clérigo / la siembra |

### 1.3 EN MEMORIA — se pierde al cerrar el juego (¡ojo!)

| Qué | Dónde | Consecuencia |
|---|---|---|
| Asedio clásico en curso (`DEFENSES`) | `VillageManager` (estático) | **cerrar el juego cancela el asedio a medias**: al volver, la aldea sigue sin resolver y el asedio **se relanza** al llegar (I86) |
| Hordas del mundo en curso (`WORLD_SIEGES`) | idem | igual: se pierden |
| Puntos fallidos de los aldeanos | **en la entidad** (`DevilRpgPuntoFallido`, 5 min) | estos **sí** persisten con el aldeano |

**El estado en una línea.** Para leerlo sin repetir reglas está
`VillageManager.estadoDeLaAldea(level, indice)`, que devuelve `EN RUINAS` / `en asedio` / `a salvo, con el sello
puesto` / `viva, sin socorrer` (en ese orden de prioridad). Lo usan el **Diario del Invocado** y el **arnés**, así que
"el estado de la aldea" tiene una sola definición en todo el mod. Medido: las líneas del Diario del jugador dan
`EN RUINAS` para la aldea 1 y `a salvo, con el sello puesto` para la 0 y la 2, y en un **asedio provocado a propósito**
sale `en asedio` (`Aldea de Peñasalbas (1838, 1838) — en asedio · a 599 m hacia el suroeste`).

## 2. La máquina de estados (quién la mueve y qué deja en el log)

```
                 (el jugador se acerca a 140)                (a 100)
   [no existe] ──────────────► GENERADA ──────────────► AVISADA ──────────┐
                                    │                                      │ (a 24 = "entrar")
                                    │                                      ▼
                                    │                                 DESCUBIERTA ──► (barra con NOMBRE,
                                    │                                      │          apuntada en el Diario)
                                    │  (a 24, sin asedio resuelto)         │
                                    ▼                                      ▼
                                 ASEDIADA ◄───────────────────────────────┘
                                  │    │
              (ola limpia o       │    │  (se agota el tiempo CON asediadores
               no llegaron a      │    │   vivos y DENTRO del muro, y el
               entrar)            │    │   jugador está en la aldea)
                                  ▼    ▼
                              SELLADA   CAÍDA ──► RUINAS (definitivo)
                                  │                    │
                                  └──► el clérigo revela la SIGUIENTE
                                                       └──► solo un aviso con el RUMBO
```

**Y el ciclo del MINERO** (etapa I, `VillagerMinerGoal`), que es el único goal del pueblo que **muta el mundo** a
propósito (cava su mina):

```
        (no tiene pico)                 (tanda hecha: 64 adoquines, mineral crudo,
              │                          sin antorchas o inventario lleno)
              ▼                                     │
   ┌──► RECOGER ────(carga: pico, tablones, ──► CAVAR ──(una celda: hueco de paso, pieza,
   │    almacén      carbón, palos, leña)      mina    suelo, veta de al lado, marco, antorcha)
   │        ▲                                     │
   │        │                                     ▼
   └──── ENTREGAR ◄──── TALLER ◄──────────────────┘
        (almacén)      (caseta: funde, cuela el adoquín en la balsa, hace antorchas)
```

- La **mina está tapada** por algo del pueblo (una casa, la pared de su caseta) o **cerrada** (el fondo, o un mar de
  agua/lava con su **piedra labrada** de tope): no hay faena y el goal se queda esperando.
- **De noche** (`estaDescansando`) y **con hambre** (`tieneHambre`) no empieza faena: come en la taberna y duerme en
  su cama de la caseta como cualquier aldeano.

| Transición | Quién la dispara (código) | Condición | Rastro en el log |
|---|---|---|---|
| → **Generada** | `preGenerate` (`VillageManager.java:804`) desde `ObjectiveManager.tick` | a menos de `PRE_GENERATE_RADIUS` (140) | `[Village] Aldea N pre-generada en … (plano de N bloques)` |
| → **Avisada** | `noticeIfNear` (`:785`) | a menos de `NOTICE_RADIUS` (100) | **solo chat**: `Divisas una aldea a lo lejos… la campana llama` (no deja línea en el log) |
| → **Descubierta** | `manageNearby` (`:837`, bloque de llegada) → `visitarAldea` | a menos de `ARRIVE_RADIUS` (24) | `[Village] <jugador> ha descubierto la aldea N (<nombre>)` + chat `Has llegado a …` |
| → **Asediada** | `start` (`:1205`) | a 24 bloques, sin asedio resuelto ni en curso | chat `Llegaste a la aldea... los monstruos se acercan.` y, `GRACE_TICKS` (90 s) después, `spawnWave` (`:1435`) + chat `¡Defiende la aldea de los monstruos!` |
| → **Reloj en pausa** | `tick` (`:1232`) | el jugador del asedio no está a menos de `RADIO_ASEDIO_CON_JUGADOR` (128) | `[Village] Asedio de la aldea N EN PAUSA: el jugador no esta en la aldea (a N bloques del centro)` y, al volver, `[Village] Aldea N: el jugador ha vuelto al asedio; se le da el tiempo entero otra vez` |
| Asediada → **Sellada** | `tick` (`:1232`) resolución: ola limpia (`isWaveCleared`) **o** fracaso del asedio (`siegeFailed`: se agotó el tiempo y quedaban atacantes FUERA) | — | chat `¡Has salvado la aldea!` / `Los monstruos se dispersaron…` / `Los monstruos no lograron entrar…`; `[Village] Aldea N salvada: N (quedan N puntos)` (recompensa) o `[Village] Aldea N salvada sin limpiar la horda (N atacantes sin confirmar)`; **y desde I87**: `[Village] Aldea N salvada: revelada la aldea N+1 a <jugador> (hacia el <rumbo>)` |
| → **Marca el sello** | `marcarSelloMistico` (`:5096`) | al salvarse | `[Village] Aldea en <pos>: sello místico activo (no aparecerán enemigos dentro)` |
| Asediada → **CAÍDA** | `fallVillage` (`:5064`) desde `tick` | se agotó `GRACE_TICKS + SIEGE_TIMEOUT_TICKS` (90 s + 2 min) **y** la ola sigue viva y **DENTRO** del recinto (`allZombiesInsidePerimeter`) | `[Village] Aldea N queda en ruinas: N bloques cambiados` + `[Village] La aldea N ha CAÍDO y queda en ruinas` + chat (desde I87: **con rumbo y sin distancia**) |
| → **RUINAS** | `VillageGenerator.ruin` (VillageGenerator) | solo desde `fallVillage` | (la línea de arriba) — **no mata a nadie**: cambia bloques (aire 35 %, telarañas 10 %, piedra mohosa 10 %, ladrillo agrietado 7 %, tope 2.500) y es **determinista** (semilla = `índice*31+7`) |
| Horda del mundo → | `startWorldSiege` (`:1690`) desde `HordeManager` | presión ≥ `PRESSURE_MIN_TICKS` (8 min) y el jugador a menos de `HORDE_TARGET_RADIUS` (220) | `[Horda] la aldea N lleva N min sin socorro: elegida como objetivo` + `[Horda] la aldea N esta siendo atacada: N enemigos marchan a por ella` |
| …**resiste** | `tickWorldSieges` (`:1706`) | la lista de atacantes vivos queda vacía | `[Horda] la aldea N resistio el ataque (presion reiniciada)` (+ recompensa a quien la defendió) |
| …**cae** | `tickWorldSieges` | **0 aldeanos censados Y hay un jugador en la aldea** | `[Village] La aldea N ha CAÍDO y queda en ruinas`; **si no hay nadie**: `[Horda] la aldea N se ha quedado sin aldeanos, pero NO cae: no hay ningun jugador alli que pueda defenderla` (I86) |
| **Repoblación / salud** | el latido (`VillageManager.java:918`, `:969`) | cada `REPOPULATE_INTERVAL_TICKS` (5 min) | `[Village] Aldea N estaba vacia: aldeanos y golem repuestos` / `[Village] Aldea N crece: aldeano N/M (comida N)` |
| **Censo y comida** | el latido | cada 5 min de juego | `[Village] Aldea N: comida N puntos, N aldeanos, N camas, N raciones en el ultimo minuto` |
| **Migración del trazado** | `VillageManager.java:2191` | al subir `CURRENT_LAYOUT` | `[Village] Aldea N: trazado actualizado a la version N (casas, muro, granja y plano)` + `plano guardado (N bloques)` |
| **Un aldeano no llega** | `marcarPuntoFallido` (`:4172`) | no se acerca en N latidos (I3/I33) | `[Village] <uuid> no consigue llegar a <pos>: lo deja por 5 min y sigue con lo demas` |
| **Muertes** | `pasarHambre` (`:4878`) / `ageVillagers` (`:4974`) | sin ración / 3 días de juego | `[Village] Un aldeano de la aldea N ha muerto de hambre (N min sin comer)` / `[Village] Un aldeano murio de viejo a los N dias de juego` |
| **Siembra del Diario** | `sembrarElDiarioSiHaceFalta` (`:5008`) | una vez, en partidas ya empezadas | `[Village] Diario del Invocado sembrado para <jugador>: N aldea(s) que ya resolvio esta partida` |
| **La MINA (etapa I)** | el latido: `VillageGenerator.asegurarLaMinaDelPueblo` | **idempotente** (testigo: el suelo de piedra labrada de la caseta); sube `CURRENT_LAYOUT` a **70** y el plano se vuelve a capturar | `[Village] Aldea en <pos>: caseta del minero y boca de la mina en <pos> (caracol de radio 4, fondo y=-58)` + `[Village] almacen: el pico del minero y 6 lingotes (la mina arranca: …)` |
| **El minero, celda a celda** | `VillagerMinerGoal` (`PRIORIDAD` 4) | mira **el mundo**, no un contador: `progresoDeLaMina` / `progresoDeLaGaleria` (una celda que abre galería no cuenta hasta que la galería está entera) | `[Village] El minero: caracol paso N en <pos> (y=Y)` · `[Village] El minero: galeria <pos> (paso N, celda M de 24)` · `[Village] El minero: marco de la galeria <pos> (celda M)` |
| **Sube a su taller** | el mismo goal (fases `RECOGER` → `CAVAR` → `TALLER` → `ENTREGAR`) | **tanda hecha**: mineral crudo, 64 adoquines, sin antorchas o inventario lleno (y siempre al acabarse la mina) | `[Village] El minero: cuela 4 adoquines en la balsa y saca un pedernal` · `[Village] El minero: funde N en <lingote>` · `[Village] El minero: deja lo sacado en el almacen (pico …, tablones …, antorchas …)` |
| **La mina se topa con un mar** | `VillagerMinerGoal.cerrarLaMina` | más de `SELLOS_MAXIMOS` (**12**) celdas **seguidas** de agua o lava (una bolsa se sella y el túnel sigue) | `[Village] El minero: sella agua/lava en <pos> (N seguidas)` y `[Village] El minero: la mina se PARA en <pos> (N celdas de agua/lava seguidas): piedra labrada de tope` |
| **El pico se rompe** | `VillagerMinerGoal.gastarElPico` | una unidad de uso por celda; 250 el de hierro | `[Village] El minero: se le ha roto el pico (N usos): va a por otro al almacen` |
| **La mina está tapada** | `VillagerMinerGoal.prepararElPicado` | la celda que le toca tiene algo del **pueblo** (el minero no lo cava: I24/I27 y I102) | `[Village] El minero: la mina esta tapada en <pos>` |
| **Le falta el pico** | `VillagerMinerGoal.recoger` | no hay pico en el almacén | `[Village] El minero: no hay pico en el almacen (lo forja el herrero de herramientas: 3 lingotes de hierro y 2 palos): espera` |
| **El herrero forja el pico** | `VillagerSmithGoal.recetaDeArmadura` (el de HERRAMIENTAS, y **antes** que la armadura) | `OBJETIVO_PICOS` (2) y 3 lingotes + 2 palos | `[Village] El herrero de herramientas: Forjo un pico de hierro` |
| **El herrero funde el crudo** | `VillagerSmithGoal.recetaDeTransformacion` | `RAW_IRON`/`RAW_COPPER`/`RAW_GOLD` en el almacén y leña por encima de la reserva | `[Village] El herrero de herramientas: Fundio mineral de hierro en un lingote` |
| → **Revelada** (la dirección) | `LoreStoneBlock.revelarLaAldeaDeLaPiedra` (al leer la piedra) **y** `VillageManager.elClerigoSenalaLaSiguiente` (al **salvar** una aldea) | — | `[LoreStone] <jugador>: revelada la aldea N (hacia el <rumbo>)` / `[Village] Aldea N salvada: revelada la aldea N+1 a <jugador> (hacia el <rumbo>)` |
| **La piedra NO revela nada** | `LoreStoneBlock.revelarLaAldeaDeLaPiedra`, salida temprana | el jugador no tiene **ancla ni spawn** (sin ellos no se puede calcular dónde cae la aldea) | `[LoreStone] <jugador> leyo la piedra pero NO tiene ancla ni spawn: no se puede calcular la aldea del objetivo N y no se revela nada` — **WARN** (lo cazó el arnés: con un jugador sin ancla la piedra callaba y parecía "no hacer nada") |
| **Se consulta el Diario** | `DiarioDelInvocadoItem.use` | al usar el objeto | `[Diario] <jugador> ha consultado el Diario del Invocado` (y las líneas en sí van al chat) |

> **Nota sobre las líneas**: los números (`:1234`) son del momento de escribir esto. Si no cuadran, **busca por el
> nombre del método** (la columna de la izquierda): el nombre es el que manda.

## 3. Los relojes, en un solo sitio

Si algo "no cuadra" con el tiempo, casi siempre es una de estas:

| Constante | Valor | Qué mide | Dónde |
|---|---|---|---|
| `VILLAGE_POLL_TICKS` | 200 (10 s) | el latido de la aldea (camas, oficios, censo, comida) | `VillageManager.java:160` |
| `GRACE_TICKS` | 90 s | margen de exploración antes de que salga la ola | `:86` |
| `SIEGE_TIMEOUT_TICKS` | 2 min | tiempo para limpiar la ola antes de que la aldea caiga | `:88` |
| `RADIO_ASEDIO_CON_JUGADOR` | 128 bloques | radio para que el asedio **corra** (fuera, EN PAUSA) — I86 | `:101` |
| `PRE_GENERATE_RADIUS` | 140 | a qué distancia se construye la aldea antes de llegar | `:80` |
| `NOTICE_RADIUS` | 100 | el aviso "Divisas una aldea a lo lejos" | `:84` |
| `ARRIVE_RADIUS` | 24 | "ha llegado" (y ahí arranca el asedio y el descubrimiento) | `:82` |
| `PERIMETER_RADIUS` | = valla (62) | "dentro del muro" (los únicos que pueden hacer caer la aldea) | `:120` |
| `RECINTO_DY_ABAJO` / `_ARRIBA` | −6 / +16 | banda de altura de "dentro de la aldea" (I12) | `:135` |
| `FALLEN_CHECK_RADIUS` | valla + 44 | hasta dónde se cuentan aldeanos (I11) | `:1558` |
| `REPOPULATE_INTERVAL_TICKS` | 5 min | reponer un aldeano en una aldea débil | `:153` |
| `PRESSURE_MIN_TICKS` | 8 min | presión mínima para que el mundo mande una horda | `:1550` |
| `HORDE_TARGET_RADIUS` | 220 | a qué distancia se elige la aldea de la horda | `:1548` |
| `DEFAULT_WAVE` / `MAX_WAVE_EXTRA` | 8 / +20 | tamaño de la ola | `:104` |
| `VILLAGER_OLD_AGE_TICKS` | 2 días | a partir de aquí el aldeano es viejo (lento y débil, no cría) | `:767` |
| `VILLAGER_LIFESPAN_TICKS` | 3 días | a partir de aquí **muere de viejo** | `:773` |
| `BABY_GROWTH_SPEEDUP` | 3 | las crías crecen al triple (vanilla: 20 min) | `:772` |
| `PUNTO_FALLIDO_TICKS` | 5 min | lo que se "aparca" un sitio al que un aldeano no llega | `:4158` |
| `VillagerMinerGoal.TICKS_POR_CELDA` | 25 (1,25 s) | lo que tarda el minero en picar y dejar hecha **una celda** del túnel | `VillagerMinerGoal.java` |
| `VillagerMinerGoal.CELDAS_POR_VIAJE` | 32 | celdas como mucho antes de subir a su taller y al almacén (además de los disparadores de `hayQueSubir`) | `:98` |
| `VillagerMinerGoal.ADOQUIN_PARA_SUBIR` | 64 (una pila) | adoquín que junta antes de subir a colarlo (16 pedernales) | `VillagerMinerGoal.java` |
| `VillageGenerator.MINA_SOPORTE_CADA` | 16 escalones | un **marco de madera** cada 8 bloques de descenso (y cada 8 celdas en las galerías) | `VillageGenerator.java` |
| `VillageGenerator.MINA_GALERIA_CADA` / `_LARGO` | 8 bloques / 24 celdas | cada cuánto se abre una galería y cuánto se adentra (**hacia fuera** del pozo) | `VillageGenerator.java` |
| `VillageGenerator.MINA_FONDO` | −58 | hasta dónde baja el caracol (240 pasos desde el nivel del pueblo (63)) | `VillageGenerator.java` |
| `VillagerMinerGoal.SELLOS_MAXIMOS` | 12 | celdas **seguidas** de agua/lava que sella antes de dar la mina por terminada | `VillagerMinerGoal.java` |

## 4. Receta de diagnóstico (lo que se hizo para el caso de la aldea abandonada)

**Síntoma: "la aldea está abandonada / no queda nadie".** En este orden:

1. **¿Está caída en los datos?** `build/estado_aldea.py` (o `tools/nbtdump.py` sobre
   `data/devilrpg_villages.dat`): mira `Fallen`, `Resolved`, `Health`, `Food` de cada aldea.
2. **¿Cuándo cayó?** Busca en **todos** los logs, **incluidos los `.log.gz`**:
   `ha CAÍDO|queda en ruinas`. Si hay caída, apunta la hora.
3. **Comprueba la aritmética del reloj**: `hora de "Llegaste a la aldea"` + 90 s + 2 min ≈
   hora de la caída. Si cuadra, la aldea cayó **por el asedio clásico agotando el tiempo con
   asediadores dentro**; mira si el jugador estaba lejos (I86).
4. **¿De quién era ese log?** El log del jugador y el del **arnés** (`gradlew runServer`, mundo
   `world`) escriben los dos en `run/logs/latest.log`, así que un archivo **mezcla dos mundos y
   dos procesos**, con líneas desordenadas. Fíjate en la hora y en el nombre del almacén
   (`ThreadedAnvilChunkStorage (…)`), **nunca** en el orden.
5. **Cuenta los aldeanos de verdad**: en 1.21 las entidades están en `entities/`, **no** en
   `region/` (leer solo `region/` da 0). Y no cuentes dos veces: `build/cuenta_aldeanos1.py` lo
   hace bien (el primer script decía 1085 aldeanos donde había 9 — sumaba las dos carpetas).
6. **¿Y si no está caída?** Entonces "abandonada" era otra cosa: puede ser una aldea **caída
   antigua** que el jugador no recordaba (con nieve y telarañas del `ruin()`), o que estaba de
   noche con los aldeanos dentro. Se distingue mirando `Fallen` y la hora del mundo.

**Síntoma: el juego se cae con `NoClassDefFoundError` / `ClassNotFoundException` de una clase interna.**
No es el mod: alguien ha **borrado o recompilado `build/classes/java/main` con el juego abierto**, y su JVM
intentó cargar una clase que en ese instante no estaba. Pasó el **22-sep-2026**: el cliente del jugador se
cayó a las **10:31:24** (`VillageManager.start` → `new VillageDefense`) mientras el build se rehacía entre
las 10:30 y las 10:35. Se arregla recompilando entero **con el juego cerrado** (y el mundo no sufre daño:
el log enseña la partida guardada entera). **Regla: antes de tocar `build/classes`, comprobar que no hay
ningún JVM del juego vivo** (`--launchTarget forgeclientdev` es su cliente).

**Síntoma: "la piedra de invocación no hace nada".** Busca `[LoreStone]` en el log: si sale
`NO tiene ancla ni spawn`, el jugador no tiene ancla (sin ella no se puede calcular dónde cae la
aldea y no se revela nada). Si sale `revelada la aldea N (hacia el …)`, reveló bien y lo que falla
es la barra (cliente) o el Diario. Ojo: la barra **no dibuja nada** si esa aldea no está revelada
ni visitada — es a propósito (I87), no un fallo.

**Síntoma: "un aldeano no llega / se queda ciclado".** Busca `no consigue llegar a <pos>` y
`me salto el puesto`: es el **punto fallido** (I3/I33/I66/I73/I80), y el sitio concreto que falla
viene en la propia línea.

**Síntoma: "la despensa está llena / el cocinero no cocina".** La despensa son **todos** los
cofres de la cocina (I85): mide con `build/estado_aldea.py` antes de creerte un contador.

**Síntoma: "mataron a media aldea de noche".** Mira el sello (`Resolved`), la milicia y las
líneas del asedio; el censo cada 5 min (`comida N puntos, N aldeanos`) dice cómo iba la aldea.

## 5. Lo que NO se guarda (y por eso se pierde al cerrar)

- **Asedios en curso** (clásico y del mundo): viven en memoria. Cerrar el juego **cancela** el
  asedio; al volver, la aldea sigue **sin resolver** y el asedio se relanza al llegar. (Con el
  reloj en pausa de I86 no se pierde nada por irse, pero cerrar el juego sí borra el progreso del
  asedio.) Y el reloj solo corre si el jugador del asedio está **en la lista del servidor**
  (conectado) **y** a menos de 128 bloques: un asedio no avanza para alguien que no está.
- **Los goals de los aldeanos**: los repone el latido (necesita un jugador cerca).
- **Los contadores de atasco** de los goals: vuelven a empezar.

## 6. Herramientas (todas de solo lectura)

| Herramienta | Para qué |
|---|---|
| `tools/nbtdump.py` | leer cualquier NBT del guardado (el `devilrpg_villages.dat`, `level.dat`…) |
| `build/estado_aldea.py` | estado de las 3 aldeas + aldeanos/entidades de cada una |
| `build/cuenta_aldeanos1.py` | censo **correcto** por aldea (solo `entities/`, sin duplicar) |
| `build/compara_aldeas.py` | nieve/hielo y "firma" de bloques por aldea |
| `build/donde_jugador.py` | dónde estaba el jugador al cerrar (de `level.dat`) |
| `tools/audita_aldea.py` | inconsistencias reales de lo construido (faroles sin apoyo, cofres tapados…) |
| `tools/arnes/GuardHarness.java` | servidor headless con **jugador de pega** y la partida copiada: mide el latido de verdad (`MEDIR_ALDEAS` para I87, ver `LEEME.md`) |
| `build/cuenta_bloques.py`, `build/entidades_aldea.py` | qué bloques/entidades hay exactamente en la aldea |

**Regla de oro de la medida**: si una cifra sorprende (1085 aldeanos, 0 aldeanos, un "abandonada"
sin caída), **sospecha primero del instrumento** y compruébalo por una segunda vía antes de
tocarlo. Las tres trampas que ya nos han mordido: `region/` en vez de `entities/`, contar dos
veces las dos carpetas, y fiarse del orden de un log mezclado.
