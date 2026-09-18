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
`ObjectiveTargets.targetOf` (para que el objetivo 2 caiga en ese centro). Deja el mundo **de día**, **sin ciclo** y
**sin spawn de bichos** (el combate va antes que la ronda y los guardias se morían peleando: así solo se mide la
ronda).
