# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero y sigue por **«Lo que está pendiente»**. El detalle largo, con los datos
> crudos de cada medida, está en `docs/aldea-invariantes.md` (**I119–I125**) y en `tools/arnes/LEEME.md`.
>
> **Para el jugador**: pégale a la sesión nueva algo así: *«Lee `docs/PENDIENTE.md` y sigue por lo que está pendiente;
> mide con el arnés y no te fíes de nada que no esté medido».*

## Estado (25-sep-2026, último commit `4216086`)

Todo lo de abajo está **commiteado**, compila y pasa `python tools/lint_aldea.py --strict`.

**Hecho y MEDIDO con el arnés:**

| qué | medida |
|---|---|
| dianas y puesto de entrenamiento **al patio del sur** de la barraca (migración para las ya construidas) | `dianas al patio (3 fuera, patio puesto)`; el destino de los guardias ya es el patio |
| guardias **entrenando** | **6-7 rendiciones → 0** (`Yendo a entrenar`) |
| **barraca de un piso** con sus 8 camas | reconstruida en su partida: 8 camas abajo, tejado a `nivel+3`, `y=70` vacío; censo `19 con cama / 0 compartidas / 0 sin cama / 12 durmiendo`. La palanca es `CURRENT_LAYOUT = 73` |
| el aviso de «no llegué» | dice **qué goal**, los **bloques**, hacia dónde camina el **cerebro** (`cerebro=`), si su ruta **alcanza** (`nav=[…]`) y **qué goals corren** (`goals=[…]`) |

**Cazado de paso y ya apuntado**: los aldeanos que se rinden llevan **dos goals corriendo a la vez**
(`VillagerCollectGoal`+`VillagerGateGoal`, `VillagerFarmGoal`+`VillagerGateGoal`), y `VillagerGateGoal` es el único
que corre **sin ningún flag** (`EnumSet.noneOf(Goal.Flag.class)`): el selector no puede serializarlo y le pelea el
destino al otro → el jugador lo ve como *«caminando erráticamente, como balanceándose… dos tareas en su cerebro en
conflicto»*.

## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 1. Que el `VillagerGateGoal` no pelee (empezar por aquí)

- Fichero: `src/main/java/com/chipoodle/devilrpg/entity/goal/VillagerGateGoal.java`.
- Dato: `setFlags(EnumSet.noneOf(Goal.Flag.class))` (línea ~175) → corre **en paralelo** a cualquier goal con `MOVE`.
  Además, en `abrir(...)` (~línea 350) hace `navigation.stop()` + borra `WALK_TARGET` y `PATH` — **a propósito** para
  que la ruta se recalcule con la compuerta ya abierta (está documentado y medido, no quitarlo a lo bruto).
- Las dos salidas a valorar: **(a)** darle el flag `MOVE` (que el selector los serialize) o **(b)** dejarlo en paralelo
  pero que **sólo** toque el destino/camino cuando el que va a cruzar el portón sea él.
- Medir: corrida del arnés + `python tools/arnes/resumen_rendiciones.py run\logs\latest.log` y mirar los `goals=[…]`.

### 2. La mina sellada (lo pidió el jugador: «el minero no está bajando y está sellada la entrada»)

- **Ya medido y descartado**: la **boca** (`bocaDeLaMina`) es **`507,62,613`** y está **ABIERTA**: `507,63,613=air`,
  `507,64,613=air`, y el `cobblestone_slab` de la boca es lo que el plano quiere. Encima (3 arriba) está el tejado de
  la caseta. **No es el obrero reponiendo nada.**
- **Siguiente paso exacto**: medir la **columna de la boca hacia abajo** (las celdas del caracol desde `507,62,613`
  hasta la cara actual) diciendo en cada una si es **aire**, **pieza** o **roca**: así se ve **dónde se corta** el paso.
- **Ojo con el instrumento**: la línea `[Arnes] MINA t=… TOPE=… bloqueDeLaCara=…` **no salía** en mis búsquedas
  (`Select-String`), y el volcado nuevo `[Arnes] BOCA DE LA MINA t=…` **sí**: sospechar del patrón de búsqueda antes
  de dar por hecho que no se imprime.

### 3. Atascos sueltos ya apuntados (cuando se pueda)

- El **granjero** con la mata de trigo (la mata es un **bloque**: caminar hacia ella da ruta de 1 nodo) — el mismo
  patrón de I114, que en el leñador y el obrero ya está arreglado.
- El aldeano que se queda **sin ruta** fuera del muro (`560,64,587`, `552,63,585` en el momento de medirlo).

## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'forgeclientdev' }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force
.\gradlew.bat runServer --console=plain            # ~9 min de reloj para una corrida útil

# 3) leer la medida
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|CAMAS RESUMEN'

# 4) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item build\classes\java\main\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
# y comprobar que el .jar queda libre:
[System.IO.File]::Open((Resolve-Path 'build\moddev\artifacts\neoforge-21.1.249.jar'),'Open','ReadWrite','None').Close()
```

## Reglas de este proyecto que no se negocian

1. **Nada entra sin `compileJava` + `lint_aldea --strict` + medida.** Y si un intento **no** arregla, se **retira** y se
   dice (hay dos así, documentados en I119/I122).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).
