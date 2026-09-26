# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero y sigue por **«Lo que está pendiente»**. El detalle largo, con los datos
> crudos de cada medida, está en `docs/aldea-invariantes.md` (**I119–I128**) y en `tools/arnes/LEEME.md`.
>
> **Para el jugador**: pégale a la sesión nueva algo como: *«Lee `docs/PENDIENTE.md` y sigue por lo que está
> pendiente; mide con el arnés y no te fíes de nada que no esté medido».*

## Estado (26-sep-2026, sesión de la mina: el pozo, el acuífero y el recolector)

Todo lo de abajo **compila** y pasa `python tools/lint_aldea.py --strict`.

**Hecho y MEDIDO con el arnés en esta sesión:**

| qué | medida |
|---|---|
| **la mina sellada** (I126) | `pasos=16` **congelado 18.200 ticks** y **NO HAY RUTA** al pozo. Los cortes: **césped del nivelado** en `507,62,614`…`507,62,620` y **dos troncos del marco del paso 16** en el paso del 15 |
| **el pozo, abierto** | el reparador salta en el latido (`6 celda(s) … y 2 poste(s)`), la **sonda** pasa de pararse en el paso 5 a `SI` en los pasos 0…32 y **`pasos` sube 16 → 32** con el minero 16 bloques bajo el suelo |
| **el acuífero del paso 32** (I127) | el agua **se daba por "picada"** (0 sellos en 110.000 ticks, 282 líneas de galería repitiendo las celdas 1 y 2) → ahora **se sella**, y **la mina CIERRA**: `sella agua/lava … (1 seguidas)`, `la mina se PARA en 507,46,612: piedra labrada de tope`, **`TOPE=SI`**, y el minero **fuera**, cargando material |
| **el recolector** (I128) | **64 → 0** rendiciones de Filomena (perseguía cosas **encima del tejado** —`y=67`, con el techo en 66— y **fuera del muro** —sin ruta: `ruta=1 nodos … alcanza=NO`, y `ruta_atasco.py` dice NO HAY RUTA—). Rendiciones del pueblo: **1,37 → 0,64 por 1.000 ticks** |
| **los portones** (I126) | medido que el borrado del destino ocurría con la ruta viva **ya alcanzando** en **499 de 868 (57 %)** aperturas, y que **no arreglarlo no cambia nada** → **intento RETIRADO**; queda la línea `[Gate]` que lo midió |

**Dos trampas de instrumento cazadas** (apuntadas en el LEEME): `run/logs/latest.log` **ROTA por tamaño** (hay que
juntar el `.gz` con `latest.log` o los números salen falsos), y el arnés **deja el JVM vivo**: matar el proceso del
`gradlew` **no** mata el servidor, y si sigue vivo **ocupa el puerto** y la corrida siguiente **no arranca** (se ve
como `Exception stopping the server … serverlevel2 is null` y un log que no cuadra). Comprobar SIEMPRE
`Get-CimInstance Win32_Process | Where-Object { $_.CommandLine -match 'fml.modFolders' }` antes de cada corrida.

## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 1. La MINA ya funciona de punta a punta (medido) — y el fantasma de la "caseta inalcanzable" NO era eso

Estado medido en la última corrida (`TOPE=SI` incluido): el minero **baja, cava 16 pasos (16 → 32), se topa con el
acuífero, sella, cierra la mina con su piedra labrada y SUBE a entregar** — acabó en `516,63,663` con la etiqueta
`Guardo 18 de lo suyo` y **0 picos rotos** (el viaje al almacén funciona).

**Y el "la caseta no se alcanza" era un fantasma de MI INSTRUMENTO**, cazado con la `SONDA DE LA CASETA` nueva (el
arnés pregunta al planificador del juego, celda a celda, de fuera adentro):

```
[Arnes] SONDA DE LA CASETA t=400 pos=499,54,621 apoyo=503,63,617 puesto=501,63,615
  503,63,621(air/grass_block)=28n/SI   503,63,620(air/stone_bricks)=29n/SI
  503,63,619=30n/SI   503,63,618=31n/SI   503,63,617(apoyo)=32n/SI
  501,63,614(stone_bricks/stone_bricks)=34n/NO fin=501,64,615
```

El **apoyo de la caseta SÍ se alcanza** (32 nodos, entrando por su puerta sur) y el modelo también lo encontraba (57
pasos). Lo que **nunca** se alcanza es la celda del **`JOB_SITE` = el CORTAPIEDRAS** (`501,63,615`, **un bloque**):
`reclamarElPuesto` se lo pone al cerebro en la celda del puesto, y el `WorkAtPoi` de vanilla manda al aldeano **a esa
celda**, que no se puede pisar → el `rutaFaena` que yo medía era **la ruta a un bloque** (`fin=… dFin=4,00/7,07`) y de
ahí salió el diagnóstico equivocado. Lo que sí provoca es una **pelea**: el goal escribe su destino cada tick y el
cerebro lo pisa con el del puesto → `mejorDistancia` no baja → `stuckTicks` sube → **"Volviendo a la caseta
(encajado)"** en bucle.

**Lo que toca**: que el puesto de trabajo del cerebro (o el `JOB_SITE`) **sea una casilla que se pise** (la de al lado
del cortapiedras), sin perder el ticket del POI (que es lo que le da la actividad de trabajar; ver el caso medido del
herrero en `VillagerSmithGoal`), **o** que el goal no cuente atasco mientras el cerebro vaya a su puesto (el patrón de
I125, `VillageManager.elCerebroVavaA`) — con cuidado de no quedarse sin el vigilante que hoy lo manda de vuelta a la
caseta.

### 2. Los GRANJEROS con la mata (ahora son los que más se rinden)

En la corrida final, **5 de las 11 rendiciones** son de granjeros (`Hipolito` 3 y `Saturnino` 2) y todas iguales —
el patrón de **I114** ("a un bloque no se camina"), con el destino siendo **la mata**:

```
no consigue llegar a 448,63,664 desde 449,63,664 (ruta=1 nodos hasta 449,63,664 alcanza=SI;
    pies=oak_fence_gate cabeza=air suelo=grass_block | destino=wheat encima=air)
    etiqueta="Hipolito (Granjero) / Entrando a la huerta" cerebro=448,63,664 nav=[sin ruta]
    goals=[VillagerFarmGoal VillagerGateGoal]
```

O sea: el granjero está **metido en la compuerta** (`pies=oak_fence_gate`) a **1 bloque de la mata**, la ruta que le
da el juego es de **1 nodo** (su propia celda, porque el destino es un bloque de trigo) y se rinde. En el leñador y el
obrero esto ya está arreglado (se camina a una **casilla de pie**, I114): lo que falta es hacerlo en **la cosecha y la
siembra del granjero** (y tener en cuenta que el aviso aparca **la mata**, no la entrada).

**INTENTO MEDIDO Y RETIRADO** (26-sep-2026): sospeché del contador de "no me acerco" —se acumula mientras va de
lejos y el vigilante `canContinueToUse` se evalúa antes del `tick`— y le perdoné el contador cuando ya está al
alcance de su faena (más un reinicio en la pierna de la compuerta). **Medido: NO mejora** (5 de 12 rendiciones de
huerta antes, **6 de 14** después), así que se ha **retirado** y el árbol queda como estaba. La pista que deja: **no
es el contador**, es la **ruta al bloque** (I114 de verdad) o el acceso a la parcela; lo siguiente que hay que medir
es la ruta viva del granjero en el momento de rendirse (`nav=[…]` sale `sin ruta` o apuntando a otro sitio).

### 3. El pico, cuando se rompe

Medido que **suelta la faena** (no sigue "picando" en el sitio) y que en la corrida final **no rompió ninguno**
(`se le ha roto el pico` = 0; y el almacén tenía **0 picos**, así que el herrero no los tiene hechos). Falta medir
el caso completo: romperlo y ver que **vuelve con otro** (y que el herrero los forje).

### 4. Atascos sueltos ya apuntados (cuando se pueda)

- El aldeano que se queda **sin ruta** fuera del muro (`560,64,587`, `552,63,585`).
- La **recolectora** aún se rinde 1 vez por corrida (ya no 64): mirar el caso suelto que queda.


## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Y NUNCA lanzar el arnés con un
#    JVM del arnés anterior vivo (ocupa el puerto y la corrida nueva no arranca). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'forgeclientdev|fml.modFolders' } |
    ForEach-Object { "$($_.ProcessId) $($_.CommandLine.Substring(0,80))" }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) LA MINA SIN LEVANTAR SERVIDOR (lo primero que hay que mirar): la columna del pozo, celda a celda
python tools\arnes\columna_mina.py "New World (2)" 503 617 63 simular
python build\slice_mina.py 497 509 611 623 44 67 "New World (2)"   # mapa por capas del solar
python tools\arnes\ruta_atasco.py 515 662 503 617 63 "New World (2)"   # ¿hay ruta de pie a la caseta?

# 3) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force
.\gradlew.bat runServer --console=plain            # ~10-15 min de reloj para una corrida útil

# 4) leer la medida  *** OJO: latest.log ROTA POR TAMAÑO (juntar el .gz) ***
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|POZO t=|GALERIA t=|SONDA DEL POZO|\[Gate\]|sella agua|la mina se PARA'

# 5) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
#    Y OJO: matar el gradlew NO mata el servidor. Matar el PROCESO del servidor y comprobar que no queda ninguno.
Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
    Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
    ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
Remove-Item src\main\java\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item build\classes\java\main\com\chipoodle\devilrpg\debug -Recurse -Force
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
# y comprobar que el .jar queda libre:
[System.IO.File]::Open((Resolve-Path 'build\moddev\artifacts\neoforge-21.1.249.jar'),'Open','ReadWrite','None').Close()
```

**Para juntar un log rotado** (el `Name` del `.gz` cambia cada día; se mira el más reciente):

```powershell
$in = (Resolve-Path (Get-ChildItem run\logs\*.log.gz | Sort-Object LastWriteTime | Select-Object -Last 1)).Path
$out = Join-Path (Resolve-Path 'build').Path 'log-completo.log'
$e = [System.IO.File]::OpenRead($in); $s = [System.IO.File]::Create($out)
$gz = New-Object System.IO.Compression.GZipStream($e, [System.IO.Compression.CompressionMode]::Decompress)
$gz.CopyTo($s); $gz.Close(); $s.Close(); $e.Close()
Get-Content run\logs\latest.log | Out-File -Encoding utf8 -Append $out
```

## Reglas de este proyecto que no se negocian

1. **Nada entra sin `compileJava` + `lint_aldea --strict` + medida.** Y si un intento **no** arregla, se **retira** y se
   dice (hay cuatro así: dos en I119/I122, el de los portones en I126 y el "contar los sellos como hechos" de I127).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).

