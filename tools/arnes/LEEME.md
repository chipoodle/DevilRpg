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


