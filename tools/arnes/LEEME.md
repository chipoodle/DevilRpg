# Arnés de la aldea (servidor headless)

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

- `[Arnes]` cada segundo: **posición**, `yRot`, **destino del cerebro** (`WALK_TARGET`), oficio y la **etiqueta**
  (lo que el jugador ve sobre la cabeza).
- `[Village] Guardia ...: nuevo puesto ... (paso N)`: cada vez que el guardia cambia de puesto de la ronda → sirve
  para ver **si la ronda avanza** (el bug era que se quedaba en el mismo `paso` **para siempre**).
- `[Village] Guardia ...: no llego a ... me salto el puesto`: el guardia se rindió en un puesto (I33).
- `[Village] Guardia ...: deja el puesto ... (atascado N ticks)`: el goal se cortó.

**Ajustes del arnés** (arriba del archivo): `CENTRO` e `INDICE` son la aldea que se mide (la 2 es
`1414,120,1414`, índice 2) y el ancla del jugador se **calcula** con la misma cuenta que
`ObjectiveTargets.targetOf` (para que el objetivo 2 caiga en ese centro). Deja el mundo **de día**, **sin ciclo**,
sin spawn de bichos y **barriendo cada segundo los bichos que ya venían en el guardado** (`hayEnemigosDentro`
bloquea el latido del pueblo entero: sin barrerlos, no se reparten oficios ni se alista la guardia; se nota porque
en el log **no** sale ninguna línea `[Village] Aldea N: comida ...` ni ningún `nuevo puesto`).

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
- **EL SUEÑO Y LAS CAMAS** (I43/I44, ver `medidas-camas.txt`): con `MEDIR_NOCHE = true` el arnés fija la **noche**
  (18000), **rejuvenece** a los aldeanos cada 10 s (el mod les da fecha de nacimiento y a los 3 días de juego mueren de
  viejos: en una corrida larga eso repuebla la aldea a mitad de la medida) y saca:
  `[Arnes] CAMAS RESUMEN: adultos=N conCama=N COMPARTIDAS=N SIN CAMA=… DURMIENDO=N` (el **criterio de "arreglado"**:
  nadie sin cama, ninguna cama compartida por dos aldeanos y **todos durmiendo**) y, por aldeano,
  `CAMA <uuid> … home=… durmiendo=…`;
  `[Arnes] PERDIDA-TICK / RECLAMADA-TICK`, el **vigilante a resolución de tick**: al perder la cama imprime cómo estaba
  **en el tick anterior** (POI, `OCCUPIED`, quién dormía en ella) y qué memorias le quedan —es lo que identifica al
  culpable—; `[Arnes] EN-BANCAL`, para el que está metido en un bancal (su `WALK_TARGET`, sus goals corriendo y el
  estado de las **cuatro compuertas**); `[Arnes] CAMA CANDIDATA` con `rutaDetallada` (nodos, **si ALCANZA**
  `canReach` y **dónde acaba** la ruta: distingue «no hay ruta» de «la ruta se queda corta»); y `[Arnes] SONDA` +
  `RUTA a <celda>`, la **sonda de rutas celda a celda** de un aldeano sin cama (prueba el camino a las celdas que
  importan y dice dónde se corta).

