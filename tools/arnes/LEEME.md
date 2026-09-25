# Arnés de la aldea (servidor headless)

## ¿Qué es esto, en cristiano?

Un **arnés** (*harness*, en inglés) es un **banco de pruebas**: un programa que se escribe **solo para medir**, no para
el juego. Este, en concreto, **levanta el mod sin abrir Minecraft**: arranca un servidor sin ventana (`gradlew
runServer`), pone un **jugador de pega** en la plaza de una aldea y deja correr el **latido de verdad** del pueblo,
volcando al log lo que hace cada aldeano, cada guardia, cada zombie y cada cofre. Luego yo **leo el log** y te digo qué
pasó con números, en vez de "creo que ya está".

Para qué sirve, con casos de verdad:

- **La despensa "llena"**: medí el cofre de verdad y resultó que era un cofre doble a medio llenar (I85).
- **La aldea que cayó "sola"**: la línea de tiempo del asedio salió del log (`18:46:25` llegada → `18:50:09` caída) y
  con ella la regla de la pausa (I86).
- **La piedra de invocación que "no hacía nada"**: el arnés lo destapó — con un jugador **sin ancla** la piedra se iba
  **en silencio**; ahora deja un aviso en el log (I87).
- **Los 1085 aldeanos** que aparecieron una vez eran **9**: el fallo estaba en mi script, no en el juego. De ahí la
  regla de oro: **si un número sorprende, sospecha primero del instrumento**.

**Lo que NO se puede medir con él** (y por eso a veces te digo "esto lo verás tú"): todo lo que se **dibuja** —la barra
de aldea, la pantalla del libro, el HUD— porque eso es del **cliente**, y el arnés no tiene ventana. Tampoco lo que
necesita un **jugador de verdad conectado** (por ejemplo, el reloj del asedio: solo corre con un jugador en la lista
del servidor, y el de pega no está en ella).

**Es temporal y no viaja en el mod**: se copia a `src/.../debug/`, se usa y **se borra** (el arnés fuerza chunks y
cambia reglas del mundo, así que no puede quedarse). Si algún día ves `src/main/java/com/chipoodle/devilrpg/debug/`, es
un resto mío que hay que borrar. Y **corre sobre una COPIA de la partida** (`run/world`), nunca sobre la tuya.

**Tú no tienes que hacer nada con esto**: juegas y me cuentas lo que ves; el arnés es mi forma de comprobar que lo que
arreglo funciona antes de dártelo.

> **COMPILA ANTES DE LANZARLO, y mira que el build diga `BUILD SUCCESSFUL`.** `runServer` compila por su cuenta, así
> que si el arnés (o el mod) **no compila**, la tarea falla pero el servidor **arranca igual con las clases a medias**
> y revienta al primer tick con `ClassNotFoundException` de una clase interna (`VillageManager$VillageDefense` fue la
> que nos lo enseñó; la causa era un método duplicado en el propio arnés). Si pasa: mata el servidor, borra
> `build/classes/java/main` y recompila.
>
> **Y NUNCA TOQUES EL BUILD CON EL JUEGO DEL JUGADOR ABIERTO.** Borrar o recompilar `build/classes/java/main` mientras
> su partida corre **le revienta el juego a él**: su JVM intenta cargar una clase que en ese instante no está y muere
> con `NoClassDefFoundError` de una clase interna. Pasó el **22-sep-2026**: su cliente se cayó a las **10:31:24**
> (`VillageManager.start` → `new VillageDefense`), justo mientras yo borraba y recompilaba entre las 10:30 y las
> 10:35. **Antes de tocar las clases**, comprueba que no hay ningún JVM del juego:
> `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` y mira la línea de comandos — `--launchTarget
> forgeclientdev` es su cliente, `forgeserverdev` es el arnés.

`GuardHarness.java` **no se compila desde aquí** (está fuera de `src/`): es la **copia de referencia** del arnés con
el que se midió el bug de la **guardia del corral** (invariantes I32/I33/I34 de `docs/aldea-invariantes.md`) **sin
abrir el juego**. Los goals del pueblo **no se guardan con la partida** (los repone el latido, que necesita un
jugador cerca), así que una partida abierta en un servidor sin jugadores **no tiene goals**: el arnés pone un
**jugador de pega** (`FakePlayerFactory`, en la plaza) y llama al latido de verdad.

## Cómo se usa

```powershell
# 1) Copiar el arnés al mod (es lo único que se compila)
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java

# 2) Poner la partida del jugador como mundo del servidor (¡una COPIA! run/world se pisa)
Move-Item run\world run\world.antes
Copy-Item "run\saves\New World (1)" run\world -Recurse

# 3) Servidor headless (lee el log con Get-Content -Wait o Select-String)
$env:GRADLE_USER_HOME="C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat runServer --console=plain        # cancela con Ctrl+C cuando tengas la medida

# 4) Leer lo medido
Select-String run\logs\latest.log -Pattern '\[Arnes\]|\[Village\] Guardia|no llego a'

# 5) DESHACERLO (el arnés fuerza chunks, cambia reglas del mundo y pone un jugador de pega: no puede quedarse)
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Move-Item run\world.antes run\world
```

## Qué mide

### `MEDIR_MILICIA = true` — la milicia que aprende (I62) y el clérigo que sana (I64)

Pone el mundo de **día** (a diferencia de `MEDIR_NOCHE`), **no barre los bichos** (los que hay dentro son los que se
siembran, ¡ojo: con el barrido desaparecían en el mismo segundo y la medida no valía!) y a los **150 s** —cuando la
guardia ya está alistada: a los 30-60 s **todavía no hay guardias** y la siembra se quedaba sin heridos— hace esto:

- **hiere a tres guardias** al 35 % de su vida (para que el clérigo tenga a quién curar) y suelta **cuatro zombis
  flojos** (4 de vida, `NoAI`) **junto al primer guardia**;
- 2 s después les da un **golpe mortal atribuido a la guardia** (`mobAttack(guardia)`): es la prueba directa del
  enganche de `LivingDeathEvent`, **sin depender de la IA** (que se mide sola: la etiqueta pasa por *"Atacando"*);
- cada segundo vuelca **cada guardia** (`nv`, `matanzas`, vida/máxima, posición y etiqueta) y **el clérigo** (su
  etiqueta y el herido más cercano con su vida y a cuántos bloques está).

Lo que se busca en el log: `[Arnes] GUARDIA … nv=N matanzas=M vida=…/… | etiqueta: … / Atacando`,
`[Village] … sube al nivel N: … enemigo(s) y sus atributos son vida … y dano …` (el **daño tiene que ser > 0**:
un aldeano **no** trae el atributo de daño de fábrica, ver I62) y `[Village] El clerigo cura a X (a -> b de N de vida)`
con la etiqueta `Curando a X`.

**Ajustes del arnés** (arriba del archivo): `CENTRO` e `INDICE` son la aldea que se mide (la 2 es
`1414,120,1414`, índice 2) y el ancla del jugador se **calcula** con la misma cuenta que
`ObjectiveTargets.targetOf` (para que el objetivo 2 caiga en ese centro). Deja el mundo **de día**, **sin ciclo**,
sin spawn de bichos y **barriendo cada segundo los bichos que ya venían en el guardado** (`hayEnemigosDentro`
bloquea el latido del pueblo entero: sin barrerlos, no se reparten oficios ni se alista la guardia; se nota porque
en el log **no** sale ninguna línea `[Village] Aldea N: comida ...` ni ningún `nuevo puesto`).

- `BICHO_DENTRO = true` → **lo contrario, a propósito**: en vez de barrer los bichos se planta **UNO** dentro de la  aldea y se mantiene ahí. Es un **aldeano-zombi** (`NoAI`, invulnerable, persistente) porque es un `Monster` —cuenta
  para `hayEnemigosDentro`— y el sello **no lo expulsa** (`expulsarHostilesDeLaAldea` deja en paz a los aldeanos-zombi:
  puede ser una curación en marcha), así que el latido se queda **cortado** toda la corrida. Es lo que reproduce la
  partida del jugador (de noche y con bichos dentro) y lo que se midió en 3b.61. El arnés lo canta cada segundo:
  `CAMAS: … UN BICHO DENTRO: SI (1 monstruo(s): latido cortado)`.

- `SEMBRAR_AGUA_EMBOTELLADA = false` → para medir el **VIAJE AL AGUA** del clérigo (deja en el almacén
  **botellas de cristal**, no pociones de agua: con agua ya embotellada las usaría y **nunca** iría a la orilla).
  En `true` se mide la cadena de la poción sin el paseo.
- Lo que se busca en el log del clérigo (en este orden): `El clerigo va a llenar las botellas a la orilla de ...`
  → `El clerigo lleno N botella(s) de agua` → `El clerigo guardo una pocion en el almacen: Potion of ...`
  (y, si se atasca, `El clerigo se atasca yendo al agua en <pos> (orilla <pos>)` con el punto exacto).
- **EL COMBUSTIBLE** (I41/I42, ver `medidas-combustible.txt`): el bloque de siembra (tick 600) se cambia según lo que
  se mida, y el arnés volca cada 2 s `[Arnes] COMBUSTIBLE: lenaEnAlmacen=N (reserva=32) carneCrudaEnDespensa=N` con
  el **cocinero** y los **herreros** (posición, troncos que llevan encima, destino y etiqueta). Las tres corridas
  hechas: (1) **con leña** —sembrar 40 troncos, 64 carnes crudas y 9 pepitas—; (2) **la reserva** —drenar el almacén
  en bucle hasta dejar 34 troncos (`VillageStorage.quitar` saca de UN stack), dar 32 tablones y 64 palos para que el
  herrero no asierre, y 40 carnes—; (3) **la remesa inicial** —vaciar el almacén entero (todas las pilas a `EMPTY`) y
  ver entrar los 128 troncos en la siguiente pasada del latido—.
- **LAS PUERTAS** (I45, ver `medidas-puertas.txt`): con `MEDIR_PUERTAS = true` el arnés fija el **día** (para que los
  aldeanos salgan y crucen puertas), se salta las siembras y volca cada 2 s
  `[Arnes] PUERTAS DE MADERA ABIERTAS en el pueblo: N <celdas>` y, cuando una pasa de abierta a cerrada,
  `[Arnes] PUERTA CERRADA en <celda> (aldeano(s) al lado: <uuid>)` — es la prueba de que las cierra el pueblo
  (`VillagerDoorGoal`). Lo que se busca es que el contador **baje** mientras el pueblo anda.
- **EL SUEÑO Y LAS CAMAS** (I43/I44/I46, ver `medidas-camas.txt`): con `MEDIR_NOCHE = true` el arnés fija la **noche**
  (18000), **rejuvenece** a los aldeanos cada 10 s (el mod les da fecha de nacimiento y a los 3 días de juego mueren de
  viejos: en una corrida larga eso repuebla la aldea a mitad de la medida) y saca:
  `[Arnes] CAMAS RESUMEN: aldeanos=N (adultos=A crias=C) conCama=N COMPARTIDAS=N SIN CAMA=… DURMIENDO=N` (el **criterio
  de "arreglado"**: nadie sin cama —**crías incluidas**—, ninguna cama compartida por dos aldeanos y todos durmiendo)
  y, por aldeano, `CAMA <uuid> nombre=… home=… durmiendo=…`;
  **OJO CON EL CENSO**: el mod reparte camas hasta `FENCE_RADIUS + 44` (**106**) y **también a las crías**, así que el
  arnés mide ese mismo radio y cuenta crías (con 64 y solo adultos decía `SIN CAMA=0` mientras el jugador veía "Sin
  cama" encima de una cría: es el error que se corrigió en 3b.61);
  `[Arnes] PERDIDA-TICK / RECLAMADA-TICK`, el **vigilante a resolución de tick**: al perder la cama imprime cómo estaba
  **en el tick anterior** (POI, `OCCUPIED`, quién dormía en ella) y qué memorias le quedan —es lo que identifica al
  culpable—; `[Arnes] EN-BANCAL`, para el que está metido en un bancal (su `WALK_TARGET`, sus goals corriendo y el
  estado de las **cuatro compuertas**); `[Arnes] CAMA CANDIDATA` con `rutaDetallada` (nodos, **si ALCANZA**
  `canReach` y **dónde acaba** la ruta: distingue «no hay ruta» de «la ruta se queda corta»); y `[Arnes] SONDA` +
  `RUTA a <celda>`, la **sonda de rutas celda a celda** de un aldeano sin cama (prueba el camino a las celdas que
  importan y dice dónde se corta).
- **LA COCINA** (I48, ver `medidas-cocina.txt`): con `MEDIR_COCINA = true` el arnés pone el mundo de **día**, siembra a
  los 20 s **40 troncos** en el almacén y **32 carnes crudas** en la despensa (sin eso el cocinero no tiene nada que
  hacer) y volca cada 2 s `[Arnes] COCINERO pos=… dentroDeLaTaberna=SI/NO VEelAhumador=SI/NO dCasilla=… dAhumador=…
  destino=… goals=[…] etiqueta=…` con la **ruta** a la casilla de la cocina. Es lo que distingue "cocina dentro" de
  "cocina a través de la pared" (el bug de 3b.63: el cocinero cocinaba desde la plaza con `VEelAhumador=NO`).

**La puerta cerrada con alguien dentro** (I47, ver `medidas-puertas.txt`): además del contador de puertas abiertas, cada
barrido cuenta `PUERTAS DE MADERA ABIERTAS … (cerradas CON alguien dentro: M)` y canta cada caso con la **posición de la
entidad** (`PUERTA CERRADA CON ALGUIEN DENTRO en <celda>: villager pos=(…) velocidad=…`). El criterio es el **centro de
la entidad dentro de la celda** de la puerta (con la caja de colisión rozando la celda salen falsos positivos: un aldeano
en la celda de al lado toca la puerta con el hombro). Y cada `PUERTA CERRADA` dice **quién** la cerró, con su etiqueta:
`PUERTA CERRADA en 1438,120,1435 (aldeano(s) al lado: 9036d1d0(Bibiana (Granjero) | Cerrando la puerta))`.

- **LA CASA DEL HUECO** (I50, ver `medidas-casa-hueco.txt`): con `MEDIR_HUECO_CASA = true` el arnés imprime cada 2 s el
  bloque de las **dos celdas de la pared** que le faltaban a la casa del jugador (`1427,120..121,1392`), el de sus
  vecinas (la ventana y el poste) y el **cofre** de al lado **con sus objetos** (`CASA hueco: … 1428,120,1392=chest ·
  cofre[27 huecos] 2:2xminecraft:apple …`): es lo que enseña, en la misma corrida, el antes (aire) y el después
  (adoquín) y que el cofre no se toca.
- **LA HUERTA** (I51, ver `medidas-huerta.txt`): con `MEDIR_HUERTA = true` (modo de día) el arnés volca cada 2 s, **por
  bancal**, cada objeto del suelo (`HUERTA bancal N: M objeto(s) en el suelo: <objeto>x<n>@<celda>(edad <ticks>)`) y,
  **por granjero**, su posición, el bancal en el que está y su **zurrón** hueco a hueco con los **huecos libres**
  (`GRANJERO <uuid> … huecosLibres=N/8 zurron: 0:8xBone Meal 1:8xBeetroot Seeds …`). La edad del objeto es la medida que
  dice si algo "se queda" en el suelo (mediana de 54 s antes del arreglo y de 6 s después).
  **AMPLIADO EN I103** (la huerta que NO se cosechaba): además vuelca, por bancal, la **tierra cultivo a cultivo**
  (`TIERRA bancal N: MADURAS … | creciendo … | VACIAS … | pisoteadas …`), la **comida de la despensa y del almacén**
  (`COMIDA: despensa N punto(s) [trigo … semillas … harina … vegetales …]`), los **bichos dentro del recinto**
  (`BICHOS DENTRO DEL RECINTO: N`) —que es lo que decide si el latido del pueblo corre— y, por granjero, su **puesto
  de trabajo** (`job=`/`potencial=`), si es guardia (`guardia=`) y su **destino**, más los **tres composteros con su
  dueño** (`COMPOSTERO bancal N en <celda> bloque=composter poi=SI dueno(s): …`). Fue lo que destapó que la tercera
  granjera estaba `job=SIN PUESTO` con el compostero del bancal 0 **libre y sin dueño**, y que su bancal se quedaba
  con 37 plantas maduras que no bajaban ni una en cuatro minutos.

### `MEDIR_ALDEAS = true` — las aldeas con NOMBRE, el revelado y el DIARIO DEL INVOCADO (I87)

Modo de **solo lectura** (no siembra, no barre bichos, no cambia la hora): deja correr el latido con el jugador de
pega y vuelca cada 10 s lo que sabe ese jugador y lo que **dibujaría la barra de aldea**:

```
[Village] Diario del Invocado sembrado para <jugador>: N aldea(s) que ya resolvio esta partida
[Arnes] NOMBRE aldea N = "Aldea de …"        <- la tabla entera (orden y CODIFICACION: comprobar en bytes)
[Arnes] ALDEAS indice=N visitadas=[…] reveladas=[…]
[Arnes] ALDEA N nombre="…" visitada=true/false revelada=true/false centro=… estado="…"
[Arnes] BARRA DE ALDEA (aldea N): con NOMBRE: "…" | sin nombre: "Aldea" | OCULTA (ni direccion ni nombre…)
[Arnes] RUMBO a la aldea N: <rumbo>
[Arnes] DIARIO: <una linea por aldea descubierta, tal cual las lee el jugador>
```

Lo que se busca: que un guardado **viejo** cargue sin reventar; que la **siembra** apunte las aldeas que esa partida ya
resolvió; que una aldea **que no está en el guardado** salga `OCULTA` en la barra (es el caso de la que viene después
de una que cayó: lo pidió el jugador); y que el Diario liste **nombre, coordenadas, estado y rumbo**.

Medida guardada en `medidas-aldeas.txt` (**3 corridas**, 22-sep-2026). La 3.ª añade el **escenario de los revelados**,
que llama a los **mismos métodos** que corren en juego (`VillageManager.elClerigoSenalaLaSiguiente` y
`LoreStoneBlock.revelarLaAldeaDeLaPiedra`), comprueba los **tres textos** de la barra (`VillageBarText`) y que los dos
revelados son idempotentes. **No cubre** la barra dibujada (es del cliente: lo medido es el texto, no el píxel) ni el
**clic** en la piedra (se mide el método que el clic ejecuta).

**OJO con el jugador de pega**: no trae **ancla** en la capability (el arnés se la pasa por parámetro al latido), así
que el escenario se la pone antes de medir. Sin ella, la piedra **no revela nada** — y así se descubrió que lo hacía
en silencio: ahora deja un `WARN` con el motivo.

### `MEDIR_ASEDIO_VIVO = true` — el asedio de verdad (y por qué NO se puede terminar headless)

Arranca el asedio del objetivo **3** (una aldea que no existe: se genera ahí, así que su asedio está sin resolver),
fuerza sus chunks y deja al jugador de pega dentro. **Lo que mide de verdad**: que el asedio existe
(`hayAsedio(3)=true`), que el estado de la aldea es `en asedio` y que **el Diario lo enseña así**, y que **sin un
jugador de verdad el reloj no corre**: el reloj solo avanza con el jugador del asedio **en la lista del servidor**
(I86) y un `FakePlayer` **no está en ella** → `distanciaAlCentro` = `MAX_VALUE` → EN PAUSA → la ola nunca sale
(medido: `agresivos=0` toda la corrida y `revelada(4)=false`). Para ver al **clérigo revelar al vencer** hay que
**jugar el asedio** con un cliente conectado.

La 1.ª corrida destapó además que el jugador de pega **no carga chunks**: la aldea 3 se descargaba
(`aldeanos3=11` → `0`). El modo fuerza sus chunks al arrancar y con eso se mantienen los 11 aldeanos.

### `MEDIR_AGUJERO = true` — el cráter de un creeper (I90)

Abre un **cráter de 3×3×2** en el suelo de la aldea y vuelca sus dos capas con letras (`.` = aire, `G` = hierba,
`D` = tierra, `P` = camino) cada 10 s, además de lo que ve el buscador del obrero y **qué está haciendo cada
constructor** (sus goals, con su prioridad y si están corriendo). **Lo que mide**: que el agujero se tapa **de abajo
arriba** (la capa del suelo queda de **hierba** y la capa de tránsito se queda en **aire**, sin escalón), **quién** lo
tapa y **por qué** antes no lo tapaba nadie (medido: los tres obreros devolvían como destino **su propia posición**, y
el recolector tenía la reparación a prioridad 5 y no llegaba a ejecutarla nunca).

### `MEDIR_MURO = true` — la brecha en la muralla (I89)

Pone un asaltante **sin objetivo** fuera de la muralla (radio 66) para que corra la **marcha** (la que taladra) y va
volcando la línea de bloques entre él y la valla (radios 66..56). **Pendiente**: la brecha está escrita y compilada,
pero no se ha llegado a ver en juego.

### `MEDIR_ALMACEN_Y_HUEVOS = true` — el almacén y los huevos del gallinero (I95/I96/I97)

Corre sobre la **aldea del jugador** (aldea 0, centro `470,646`, cota 63) y mide las dos cosas que reportó:
*"el punto de apoyo del almacén (517,64,666) es inalcanzable"* y *"el ganadero no coge los huevos del gallinero"*.

Cada 2 s vuelca:

```
[Arnes] ALMACEN t=… apoyo=517, 63, 666 (cota=63; suelo debajo=Stone Bricks | dos debajo=Dirt)
        ruta=a1=17n alcance=SI fin=517, 63, 666 dFin=0.00 | COFRE: 127 objeto(s) […]
[Arnes] GANADERO t=… pos=… dApoyo=… zurron=[…] goals=[…] destino=… nav=… etiqueta="…" aparcado=…
[Arnes] HUEVOS t=…: N en el suelo [celda(edad N)…] | PORTON … open=… gallinas: N en el hueco, N a 2.5
```

- **`ruta=…`** es `rutaDetallada` del aldeano al punto de apoyo: **la prueba del caminante del juego**
  (`createPath` + `canReach` + dónde acaba), que es lo que distingue "el punto existe" de "se llega a él".
- **`aparcado=`** es `DevilRpgPuntoFallido` del aldeano: el sitio que dejó por 5 min (I33).
- **`PORTON … open=`** y las **gallinas del hueco** son el estado del portón del gallinero.
- A los 10 s (y luego cada 20 s) **siembra 2 huevos**: uno en el **suelo** del corralillo (`513,63,638`) y otro
  **encima de la paja** (`516,64,639`), que es el caso que se quedaba a 1,803 del alcance viejo.

**OJO con el instrumento** (nos mordió en la primera corrida): el recuento de huevos va con la caja **alrededor de la
base del corral**; con `AABB(CENTRO).inflate(40)` el corral cae **fuera** y el contador decía "0 huevos" siempre.

**Medido** (3 corridas sobre una copia de su partida, ver `docs/aldea-invariantes.md` I95/I96/I97): con el código de
antes el destino salía `aparcado=517, 64, 666`, el ganadero parpadeaba entre goals y los huevos del corralillo
llegaban a **2.000 ticks** de edad; con los arreglos la ruta da `alcance=SI fin=517,63,666`, el portón del gallinero
se abre (`open=true`), el zurrón se llena y se **entrega** (ocho líneas de `N cosa(s) del corral al almacen`).

> **Y OJO AL TERMINAR: COMPRUEBA QUE EL JVM DEL JUEGO SE HA MUERTO.** Con `.\gradlew.bat runServer | Out-Null` el
> *wrapper* de Gradle termina (y el job se da por acabado) **pero el JVM del servidor sigue vivo** y deja
> **bloqueado** `build/moddev/artifacts/neoforge-21.1.249.jar`: el siguiente `runClient` (el del jugador) revienta con
> `AccessDeniedException ... is locked by: <pid>`. Pasó el **22-sep-2026 a las 12:28 a. m.** Antes de dar por cerrada
> una corrida: `Get-CimInstance Win32_Process -Filter "Name='java.exe'"` y mata lo que lleve `fml.modFolders`
> (o `forgeserverdev`), y comprueba que el `.jar` se puede abrir en escritura.

### `MEDIR_HORDAS = true` — las hordas del mundo (I98)

Corre en la **aldea 0** (centro `470,646`, cota 63) y mide que la **presión del abandono** se acumule **sola** (en el
latido, cada 10 s, con el reloj del mundo) y que la aldea sea **elegida como objetivo** de una horda.

**El montaje** (importante, o no mide nada): hay que copiar el mundo con `level.dat -> Data.Time` puesto a un valor
que haga que el **turno** del roll (`gameTime / intervalo`) cambie unos **16 s** después de arrancar — con el reloj a
**21629** y el intervalo de ~21957 ticks cambia en ~330 ticks (con `tools/nbtedit.py`, que hace la prueba de ida y
vuelta) — y el modo **siembra la presión** de la aldea 0 a **8 min exactos** a los 20 ticks
(`accruePressure(0, gameTime - 9600)`), así que la aldea ya pasa el umbral y encima se ve cómo la presión **sigue
subiendo**.

Lo que se busca en el log:

```
[Arnes] HORDAS t=20 ANTES: presion(aldea 0)=0 ticks (0 min) | gameTime=21649 intervalo=21955 turno=0 …
[Arnes] HORDAS t=160 gameTime=21789 turno=0 presion(aldea 0)=9680 ticks (8 min) …
[Arnes] HORDAS t=360 gameTime=21989 turno=1 presion(aldea 0)=9880 ticks (8 min) …
[Horda] la aldea 0 lleva 8 min sin socorro: elegida como objetivo
[Arnes] HORDAS t=100 OBJETIVO ELEGIDO = aldea 0 centro BlockPos{x=470, y=63, z=646}
```

**Y su límite, dicho claro**: el **spawneo** de la oleada **no se puede medir headless** — `HordeManager` usa
`level.players()` y el jugador de pega **no está en esa lista** (la misma limitación que el reloj del asedio, I86):
lo que se mide es la **presión** y la **elección**; la marcha la ve el jugador en juego.

### `MEDIR_MINERO = true` — la mina del minero (I102)

Corre sobre la **aldea del jugador** (aldea 0, centro `470,646`, cota 63), deja el mundo **de día** (de noche el
minero descansa) y barre los bichos (uno dentro del recinto corta el latido del pueblo entero). A los **10 s** se
asegura de que hay **minero**: si no hay ninguno con el oficio `MASON`, lo planta con la puerta del propio mod
(`VillageGenerator.spawnOneVillager(level, CENTRO, 11, false)`: el sitio 11 es el albañil).

Cada 2 s vuelca:

```
[Arnes] MINA t=… pasos=N/240 cara=457, 54, 662 (y=54 · 8 bloques por debajo del suelo)
        bloqueDeLaCara=cobblestone_slab TOPE=NO caseta=461, 63, 658 puesto=459, 63, 656 balsa=… horno=…
[Arnes] MINERO t=… pos=… cara=… dCara=… destino=… pico=minecraft:iron_pickaxe(18/250)
        zurron=[0:16xminecraft:oak_planks 1:4xminecraft:coal …] goals=[VillagerMinerGoal VillagerGateGoal]
        etiqueta=Yolanda (Minero) | Bajando a la mina
[Arnes] ALMACEN DE LA MINA t=…: 0 adoquin, 2 carbon, 0 lingote(s), 0 crudo(s), 6 pedernal, 0 pico(s) | leña=370
```

- **`pasos=N/240`** es `VillageGenerator.progresoDeLaMina`, o sea el avance **real medido en el mundo** (no un
  contador): el número tiene que **subir** con el tiempo (cada paso baja 0,5 y una vuelta son 32 pasos = 16 bloques).
- **`TODOS=[…]`** es la lista **completa** de goals del aldeano, con un `*` en el que **está corriendo** (`goals=[…]` es
  solo los que corren). Es la diferencia entre "no tiene el goal puesto" y "lo tiene pero no puede empezar".
- **`MINERO-ESTADO`** vuelca **todo lo que mira el `canUse` del minero**: si está descansando, si tiene hambre, la
  comida del pueblo, el punto de apoyo del almacén, si ese punto está **aparcado** (I33) **y con qué hora**, el
  `gameTime` y si lleva pico en la mano. Se añadió midiendo I113: con `goals=[]` a secas no se sabía **por qué** el
  minero estaba parado, y la respuesta estaba en `aparcado=true aparcadoHasta=<gameTime>` (el aparcado son **5 min**:
  mientras dura, el `canUse` devuelve `false` y el aldeano se queda quieto con la etiqueta "Trabajando"). Regla que sale
  de ahí: **si un goal dice "no puedo", el arnés tiene que poder decir POR QUÉ en la misma línea.**
- **`bloqueDeLaCara`** es lo que hay en la celda que le toca: `grass_block`/`stone` (faena pendiente),
  `cobblestone_slab`/`cobblestone` (esa ya está hecha) o `stone_bricks` (**tope**: la mina se cerró).
- **`TOPE=SI`** = la mina está terminada (el fondo `-58` o un mar de agua/lava sellado con su piedra labrada).
- El **zurrón** dice qué se está llevando (tablones de los marcos, carbón y palos de las antorchas, adoquín que luego
  cuela) y el **pico con su desgaste** dice si de verdad está picando.
- Y cada 10 s se vuelcan **las dos casetas** (la **vieja** de `461,658` y la **nueva** de `503,617`, después de la
  migración 71 que la mueve al descampado del noreste) y **cuántas celdas del plano** hay en cada solar: es lo que
  comprueba que la mina se ha **mudado** de verdad (la vieja vuelve a ser césped y aire, la nueva tiene su
  cortapiedras, su horno y su **cama dentro**, y el plano pasa de 0 a 181 celdas en el solar nuevo).

**Lo que se busca**: que `pasos` **suba**, que en el log salgan `El minero: caracol paso N en …` y
`El minero: galeria … (paso N, celda M de 24)`, y que en el **almacén** aparezcan **pedernal** (4 adoquines → 1, en la
balsa) y **lingotes** (los funde él en su horno). Lo que **no** se puede medir aquí: si el jugador ve bien la mina (eso
es del cliente) ni cuánto tarda en juego real (el servidor headless corre a los ticks que le deja el equipo).

**Y los fallos que cazó este modo** (por eso existe): la primera corrida dio `pasos=16` con la celda de la boca en
**césped** —el eje de la mina se estaba pasando a un ayudante que espera el **centro de la aldea**— y, arreglado eso,
el progreso **oscilaba 16 → 15 → 16** porque la galería salía **hacia dentro** y el minero se comía su propio
escalón; después, con el zurrón llenándose de **tierra y grava**, subía a vaciarlo **cada tres celdas** (dos celdas de
galería en once minutos). Y en la segunda vuelta midió **la mudanza**: la caseta vieja retirada (césped y aire, 0
celdas en el plano) y la nueva en `503,617` con la cama **dentro** (181 celdas en el plano).

### `MEDIR_LENADOR = true` — el leñador, su arboleda y el polvo de hueso (I92/I93)

Corre sobre la **aldea del jugador** (aldea 0) y vuelca cada 10 s: el estado de la **arboleda del pueblo** (árboles,
plantones y huecos de sus doce plazas), los **huesos y el polvo de hueso** de la despensa, y dónde está y **a dónde
camina** el leñador (flechero) y los granjeros, diciendo si su destino cae **dentro** de la valla (62) o **fuera**.

**DÓNDE SE LE PLANTA** lo decide `PLANTAR_AL_LENADOR_EN_LA_PLAZA`: en `true` se le lleva a la plaza (para medir sus
**limpiezas** de restos, I110) y en `false` se le planta **EN EL ATASCO DEL MURO** (ver abajo).

A los 10 s le siembra al pueblo **8 huesos y 16 de polvo de hueso**, y le va **reponiendo** el polvo de hueso cada
10 s (el granjero se lo lleva para abonar la huerta en cuanto lo ve, así que sin reponerlo solo se mediría el caso "no
hay"). **Lo que mide**:

- que el **granjero muele** los huesos (la receta de vanilla, 1 hueso = 3 de polvo): `El granjero: Hizo 27 polvo de
  hueso (de 9 hueso(s))`;
- que el **leñador no sale** de la aldea mientras su arboleda no esté poblada: `destinoDentro=true` en **todas** las
  muestras y la ronda `Yendo por polvo de hueso` → `Cogio polvo de hueso (8)` → `Yendo a la arboleda` → `Abono la
  arboleda` (con la harina del zurrón bajando 8 → 2);
- y que la arboleda **crece** con eso: en la corrida, 2 árboles → **3** y los plantones de 10 → 9.

Con el código de antes, el **mismo** montaje daba `destinoDentro=false` (Hortensia se iba al monte a ~110 bloques del
centro con su arboleda a 105 y 16 de harina esperando en la despensa).

### Las dos herramientas de la MURALLA (I112), que son de lectura del guardado

No levantan servidor: leen los bloques del guardado y contestan preguntas que si no se contestan a ojo se contestan mal.

```powershell
# ¿HAY RUTA de pie entre dos celdas? (la misma regla que el juego: aire a los pies y a la cabeza, suelo firme debajo)
python tools\arnes\ruta_atasco.py 527 672 517 666 63          # -> "HAY RUTA: 68 pasos" y el camino entero
python tools\arnes\ruta_atasco.py 527 672 517 666 63 "New World (1)"

# Los CUATRO portones del muro: ¿se puede pisar la casilla de dentro y la de fuera?
python tools\arnes\portones_del_muro.py
```

`ruta_atasco.py` es el que **decidió I112**: enseñó que del atasco del leñador al almacén **sí hay ruta**, pero de **68
pasos y empezando hacia el lado contrario** — que es justo lo que rompía la cuenta de "no me acerco = atascado".
`portones_del_muro.py` comprueba la otra mitad: que la casilla a la que manda la regla nueva **existe y se pisa** en los
cuatro portones (medido en su partida: los cuatro, dentro y fuera, `pisable=SI` sobre `grass_block`).

**Cuidado con el modelo** (nos mordió en la primera pasada): el recorrido en anchura **no** es el planificador del
juego. Deja subir 1 bloque (y bajar), **no** modela nadar, y con `dy` de hasta 2 bloques decía que se podía **saltar la
valla** de un corral y daba por buena una ruta que el juego no da. Si un resultado sorprende, sospecha primero del
instrumento (regla de oro de este LEEME).

#### `PLANTAR_AL_LENADOR_EN_LA_PLAZA = false` — EL ATASCO DEL MURO (I112)

Se le planta en **`CENTRO.offset(57, 0, 26)` = `527,63,672`** (el sitio exacto donde se quedaba pegado a la muralla en
la partida del jugador) con **16 troncos** en el zurrón y la mochila vacía: así arranca **en fase ENTREGAR** y lo que
se mide es si **cruza el portón** y **descarga en el almacén** — el almacén queda a **11 bloques** pero con la muralla
en medio. Lo que hay que buscar en el log:

```
[Arnes] LENADOR: plantado EN EL ATASCO DEL MURO 527, 63, 672 con 16 troncos (venia en …)
[Village] Llevando la madera va al otro lado del muro (517, 63, 666): cruza por el porton 531, 63, 646
[Arnes] LENADOR LENADOR … pos=… destino=531,63,646 zurron=[madera=16] etiqueta="… / Llevando la madera"   <- al PORTÓN
[Arnes] LENADOR LENADOR … destino=517,63,666 zurron=[madera=0]  etiqueta="… / Guardo 16 de lo suyo"        <- ENTREGÓ
[Village] el lenador guardo 16 cosa(s) de su oficio en el almacen
```

**El criterio**: `no consigue llegar a BlockPos{x=517, y=63, z=666}` **cero** veces (antes: 22), al menos una entrega y
que después vuelva a su faena (`Yendo al arbol`, `Abono la arboleda`, `talo N tronco(s)`). Medida en
`medidas-lenador.txt`, apartado 6.


