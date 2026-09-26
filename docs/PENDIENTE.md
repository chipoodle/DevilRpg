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

### 1. La CASETA DEL MINERO no se alcanza desde fuera (y el minero se queda en "Volviendo a la caseta")

Medido, con dos orígenes distintos:

```
desde dentro de la mina (499,54,620):  rutaFaena=[a1=38n alcance=NO fin=503,63,613 dFin=4.00]
desde el almacén        (515,63,662):  rutaFaena=[a1=43n alcance=NO fin=502,63,622 dFin=7.07]
```

El destino es la **casilla de apoyo de la caseta** (`puntoDeApoyoDeLaCaseta` = el eje `503,63,617`, dentro) y el
planificador **no llega**: acaba fuera (2 bloques al sur de la puerta en un caso, al norte de la caseta en el otro).
La caseta **sí tiene** su hueco de puerta de 1x2 (`503,63..64,620`, medido con `slice_mina.py`: el interior
`502..505,615..619` es aire y la puerta está abierta), y **el modelo SÍ encuentra ruta**:
`python tools\arnes\ruta_atasco.py 515 662 503 617 63` → **HAY RUTA: 57 pasos**. O sea que el problema **no es el
mundo** (la caseta y su puerta están bien) **sino el planificador del juego**, que se rinde por el camino: eso es lo
que hay que medir (¿le falta alcance de búsqueda? ¿el rodeo del anillo del caracol?). Consecuencia: cuando al minero
le toca el taller (`Fase.TALLER`) o volver, oscila `Bajando a la mina` / `Volviendo a la caseta (encajado)`.

**Lo que toca**: darle al goal una **casilla de pie a la que SÍ se llegue** para el taller (como se hizo con el
almacén en I95 y con la ronda en I112/I115) y, si esa casilla no alcanza el horno y la balsa (alcance 3,5), **abrir
un acceso** a la caseta por el lado que el planificador sí recorra.

### 2. El minero y el pico: cuando se le rompe, ¿va a por otro?

Se arregló que **suelte la faena** (`canContinueToUse` con `sin pico`), pero **no está medido** que el viaje al
almacén termine con un pico en la mano (en la corrida del acuífero se le vio `Cargando material` en `515,63,662`).
Medir: que el almacén tenga picos (los forja el herrero) y que el minero vuelva con uno.

### 3. Atascos sueltos ya apuntados (cuando se pueda)

- El **granjero** con la mata de trigo (la mata es un **bloque**: caminar hacia ella da ruta de 1 nodo) — el mismo
  patrón de I114. Medido en la corrida final: **4 líneas** de `Entrando a la huerta` (3 de Hipolito y 1 de
  Saturnino), y son **las que más quedan**.
- El aldeano que se queda **sin ruta** fuera del muro (`560,64,587`, `552,63,585`).

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

