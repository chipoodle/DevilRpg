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

### 1. El `JOB_SITE` del minero (el cortapiedras, un bloque): **medido, y no cuesta nada** (cerrado)

Medido en la corrida en la que la mina ya funciona de punta a punta (297 muestras del minero, `t=11.720`):

| qué | medida |
|---|---|
| muestras con `destino=SIN DESTINO` (el cerebro le borra el rumbo al pelear con el `WorkAtPoi`) | **48 / 297 (16 %)** |
| muestras con `goals=[]` (el goal, entre ciclos) | 33 / 297 |
| `Volviendo a la caseta (encajado)` | **0** (solo salía mientras la mina estaba clavada en el acuífero) |
| ciclo del minero | 16 pasos de caracol, 6 celdas de galería, 4 sellos, **1 tope**, 31 entregas en el almacén, **0 picos rotos** |

O sea: **la pelea del `JOB_SITE` es real pero NO bloquea nada** (ni un paso, ni una entrega); y el `rutaFaena` que me
hizo pensar lo contrario medía **la ruta a un bloque**, no a la caseta. Queda **cerrado** como cosmético, con una
**residuo** apuntado: con la mina ya en el **tope**, el minero se queda por el almacén (`516,63,663`) repitiendo
`deja lo sacado` / `Guardo 18 de lo suyo` — no hace nada útil porque **su mina está acabada**; si se quiere, lo
siguiente sería darle una faena de reserva (o que ayude en otra aldea/puesto), pero eso es una decisión de diseño,
no un fallo medido.


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
(encajado)"** en bucle... **pero eso era mientras la mina estaba clavada en el acuífero**: con la mina funcionando
(la corrida del recuadro de arriba) **no sale ni una vez** y el minero cumple su ciclo entero. La conclusión medida es
la del recuadro: **cosmético, cerrado**.

### 2. Los GRANJEROS con la mata: **ARREGLADO y MEDIDO** (26-sep-2026)

**El fallo (medido, y no era la mata)**: cuando el granjero ya estaba pegado a la compuerta, el juego le daba el
destino **con tolerancia de 1 bloque** (`caminarHacia` pone `WalkTarget(..., 1)`), así que el planificador **lo daba
por llegado**, le devolvía una ruta de **un solo punto** (la celda donde él ya estaba) y **no daba ni un paso**: el
tramo de la compuerta no avanzaba, a los 120 ticks **aparcaba esa entrada** y probaba otra puerta —hasta las cuatro—
y se rendía. Medido: los **6 avisos de una corrida, todos a distancia 1**, y **5** `no consigue entrar al bancal`.

**El arreglo**: `VillageManager.caminarHaciaExacto(...)`, un caminar **con tolerancia 0** (y que además le pide la
ruta a la navegación a mano, porque el cerebro puede escribir su propio destino en el mismo tick). Se usa en los dos
tramos del granjero que exigen **pisar** una celda: **entrar** por la compuerta y **salir** del bancal.

**MEDIDO, antes y después** (misma partida, misma copia, modo `MEDIR_MINERO`):

| | antes | después |
|---|---|---|
| `no consigue entrar al bancal …` | **5** | **0** |
| avisos de `Entrando a la huerta` | **6** | **0** |
| el granjero trabajando | se rendía en la puerta | **`Valeriano (Granjero) / Cosechando`** y entregas a la despensa de **73, 71, 45 y 14** |

*(Lo que quedaba apuntado dos veces como "intento medido y retirado" —el contador de atasco y "un paso más adentro"—
está en `tools/arnes/medidas-mina-sellada.txt` §11: los dos fallaron y se quitaron, y fue esa medida la que dejó a la
vista que el problema era la tolerancia del caminante.)*

### 3. La RECOLECTORA: **ARREGLADA y MEDIDA** (se quedaba encerrada en el bancal)

**Medido**: **19 rendiciones** en `Volviendo a la plaza` con `ruta=1 nodos … alcanza=NO` **desde dentro de un bancal**
(`pies=farmland cabeza=wheat`): entra a los bancales a por lo que se cae —su faena— y **no puede salir**, porque una
**puerta de valla cerrada no es navegable** para el juego y nadie se la abre.

**El arreglo**: `VillageManager.abrirLaCompuertaDeAlLado(...)` (compartida con el granjero) + en la recolectora, si
está dentro de un bancal, mandarla a la **celda de dentro de la compuerta más cercana** con `caminarHaciaExacto` y
**abrírsela** en cuanto la tiene al lado (probando otra si esa está aparcada).

**MEDIDO**: rendiciones del pueblo **27 → 9**; `Filomena / Volviendo a la plaza` **19 → 0**; y ahora **sale y
entrega** (`abro el porton 484,63,659 · destino=470,63,646 rutaViva=24 nodos alcanzaba=SI`).

### 4. El GANADERO: **ARREGLADO y MEDIDO** (perseguía una celda que no se pisa)

**Medido**: `Vicenta / Cuidando el ganado` **3 avisos**, destino a 4-6 bloques con `ruta=1 nodos … alcanza=NO`. Arreglado con `casillaDePieCercaDe` (la regla de I114: se camina a una **casilla de pie**, no a la celda cruda del animal): **`Cuidando el ganado` 3 → 0** y las rendiciones del pueblo **9 → 5**. Detalle en **I131**. Lo de abajo queda como el análisis original:: el destino es una **celda de aire a 4-6 bloques**
(`525,63,651`, `526,63,648`, `524,63,642`, todas con `grass_block` debajo) y el planificador devuelve **`ruta=1 nodos
… alcanza=NO`**, o sea **no se llega**. `cerebro=-` (sin destino en el cerebro) y `nav=[sin ruta]`:

```
no consigue llegar a 525,63,651 desde 525,63,656 (ruta=1 nodos … alcanza=NO; suelo=grass_block)
    etiqueta="Vicenta (Ganadero) / Cuidando el ganado" cerebro=- nav=[sin ruta] goals=[VillagerAnimalFarmGoal]
```

Es **la misma clase que la recolectora (I130)**: su faena está **al otro lado de una cerca** (el corral: va a por un
animal o a su punto de apoyo) y **la puerta de valla cerrada no es navegable** → no hay ruta. **El arreglo es el
mismo**: en `VillagerAnimalFarmGoal`, si la celda a la que va está al otro lado del **portón del corral**, mandarla a
la celda de dentro del portón con **`caminarHaciaExacto`** y **abrírselo** con
`VillageManager.abrirLaCompuertaDeAlLado` (probando el del gallinero si el del corral está aparcado).

**LEÑADOR — `Tomasa / Llevando la madera` (2 avisos)**: la ruta al almacén **SÍ alcanza**
(`ruta=14 nodos hasta 517,63,666 alcanza=SI`, `cerebro=517,63,666`, `nav=[14 nodos … alcanza]`) y **aun así se
rinde**: la clave está en sus pies — **`pies=dark_oak_fence`**: está **metida DENTRO de un bloque de valla** (con una
placa de presión encima, `cabeza=oak_pressure_plate`), así que **no puede moverse** por mucho que tenga ruta. Es un
caso físico (¿empujada por un animal, ¿encajada al cruzar?), no de tolerancia ni de puerta: **lo que toca es que el
goal detecte que está encajada** (la celda de los pies no es una casilla de pie) y, en vez de rendirse y aparcar el
almacén, **dé un paso de desatasco** (o se teletransporte 1 bloque si el juego no la deja salir).


### 5. El LEÑADOR: **dos intentos MEDIDOS y RETIRADOS** (y ya se sabe lo que NO es)

1. **Con `esCeldaDePie`**: 940 falsos positivos (daba por encajado a quien estaba de pie **sobre una valla**) y las
   rendiciones subieron a 18 → retirado.
2. **Con la CAJA DE COLISIÓN** (el criterio correcto para "metido dentro", con freno de 200 ticks): disparó **0
   veces** —ni un falso positivo— pero **tampoco caza el caso**, y las rendiciones quedaron en 12 con
   `Tomasa (Leñador) / Llevando la madera` **x4** → retirado.

**Lo que esto deja MEDIDO**: el leñador **no está encajado** (su caja no corta la valla, solo pasa por su celda) y
tiene la ruta **buena** (`nav=[14 nodos … alcanza]`): es el caso de **I119/I122** ("la ruta alcanza y el aldeano no
se mueve"). Para atacarlo hay que medir lo que **aún no se ha medido**: su **velocidad y posición tick a tick**
mientras tiene esa ruta buena (¿empuja contra la valla? ¿le tapa el paso un animal?).


Se probó un desatasco automático (`desatascarSiEstaEncajado`: si los pies no son una casilla de pie, sacarlo a la
más cercana) llamado desde el leñador. **Medido: 940 desatascos en una corrida** —teleportaba aldeanos que estaban
**bien**, de pie sobre una **valla o una placa** (`estaba ENCAJADO en 510,64,669 (pies=air)`) porque
`esCeldaDePie` no acepta una valla como suelo— y las rendiciones **subieron a 18**. **Retirado**; el árbol está como
estaba. Lo que queda apuntado: el criterio de "encajado" **no puede ser `esCeldaDePie`** (falsos positivos con
vallas, placas y losas): hay que mirarlo con la **caja de colisión** de la entidad contra el bloque de los pies, o
exigiendo que **no se haya movido en N ticks**, y **nunca teleportar por sistema**.
### 5. El pico, cuando se rompe

Medido que **suelta la faena** (no sigue "picando" en el sitio) y que en la corrida final **no rompió ninguno**
(`se le ha roto el pico` = 0; y el almacén tenía **0 picos**, así que el herrero no los tiene hechos). Falta medir
el caso completo: romperlo y ver que **vuelve con otro** (y que el herrero los forje).

### 6. Atascos sueltos ya apuntados (cuando se pueda)

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

