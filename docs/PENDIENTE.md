# POR DÓNDE SEGUIR — traspaso de sesión (aldea / DevilRpg)

> **Para la sesión nueva**: lee este fichero y sigue por **«Lo que está pendiente»**. El detalle largo, con los datos
> crudos de cada medida, está en `docs/aldea-invariantes.md` (**I119–I126**) y en `tools/arnes/LEEME.md`.
>
> **Para el jugador**: pégale a la sesión nueva algo como: *«Lee `docs/PENDIENTE.md` y sigue por lo que está
> pendiente; mide con el arnés y no te fíes de nada que no esté medido».*

## Estado (26-sep-2026, sesión de la mina sellada y los portones)

Todo lo de abajo **compila** y pasa `python tools/lint_aldea.py --strict`.

**Hecho y MEDIDO con el arnés en esta sesión:**

| qué | medida |
|---|---|
| **la mina sellada**: por qué el minero no bajaba | `pasos=16` **congelado 18.200 ticks** y **NO HAY RUTA** de pie al pozo (14.326 casillas, no pasaba de la superficie). Los cortes, celda a celda: **césped del nivelado** en `507,62,614`…`507,62,620` (7 celdas de la capa que se pisa, encima del pozo) y **dos troncos del marco del paso 16** en el paso del 15 (`500,55,621`, `500,56,621`) |
| **el pozo, abierto** | el reparador salta en el latido (`abierto el pozo de la mina (6 celda(s) … y 2 poste(s) del marco dentro del paso)`), el volcado `POZO` deja los pasos 0..16 en `air/air`, la **sonda del planificador** pasa de pararse en el paso 5 a dar **`SI` en los pasos 0…32**, y **`pasos` sube 16 → 32** con el minero en `505,47,613` (**16 bloques bajo el suelo**), 16 escalones y 2.496 líneas de galería cavadas |
| **el marco que se tapaba a sí mismo** | **14 de los 15 marcos** del caracol caen en una **esquina del anillo** y metían sus postes en el paso del escalón de al lado; los de las **galerías** no (0 de sus postes caen en un paso) |
| **los portones (`VillagerGateGoal`) NO eran el problema** | el borrado del destino ocurría con la **ruta viva ya alcanzando** en **499 de 868 aperturas (57 %)**, pero dejar de borrarlo **no cambia nada**: las rendiciones salen a **1,25 por 1.000 ticks** con el arreglo y a **1,37** sin él. **Intento RETIRADO** (regla del proyecto) y se queda la línea `[Gate]` que lo midió |

**Trampa de instrumento cazada en esta sesión** (apuntada en el LEEME): **`run/logs/latest.log` ROTA por tamaño**. La
corrida larga (127.680 ticks) quedó partida entre `2026-09-25-1.log.gz` (14 MB al descomprimir) y `latest.log`; contar
solo `latest.log` da números **falsos** (las 8 rendiciones o las 71 aperturas del trozo final). Y el patrón
`[Arnes] MINA t=` **sí** coincide: lo que no salía era porque el modo no estaba encendido en esa copia.

## Lo que está PENDIENTE (en este orden, como pidió el jugador)

### 1. La mina: **la galería del paso 32 se topa con un ACUÍFERO** (lo siguiente, ya localizado y medido)

Con el pozo abierto el minero ya baja y cava (16 → 32 pasos), pero se queda **clavado en el paso 32**:
`pasos=32` durante **~110.000 ticks**, con **282 líneas** de `El minero: galeria` repartidas entre **la celda 1 (121
veces) y la 2 (120)** del paso 32 —o sea `progresoDeLaGaleria` **no avanza**—, **una** vez "se le ha roto el pico",
0 líneas de `sella agua/lava` y 0 de `la mina se PARA`, y la ruta desde dentro de la mina a su taller
(`rutaFaena=[a1=38n alcance=NO fin=501,63,613 dFin=2.00]`) **se queda 2 corta**.

**Lo que ya se midió del sitio** (`build/slice_mina.py 505 509 609 614 44 50 world`): la galería del paso 32 sale
hacia el **norte** desde `507,46,613` y ahí hay **un acuífero** (`W` de agua en `505..507,611..612` a `y=45..46` y
alrededor). Falta por medir **el mecanismo fino**: por qué `progresoDeLaGaleria` no sube (¿el agua vuelve a entrar
entre la comprobación y el picado?) y por qué `sellosSeguidos` **no** llega a `SELLOS_MAXIMOS` (12) para cerrar la
mina con su piedra labrada, que es lo que el propio código dice que hace (`cerrarLaMina`). **Ojo**: `picarYRecoger`
devuelve `true` **sin picar nada** cuando la celda ya es aire, así que las "282 galerías" pueden ser repeticiones de
una celda que el agua rellena y se vuelve a vaciar.

### 2. La mina: el minero **sin pico** y su taller, con la ruta 2 corta

Medido en la misma corrida: `pico=SIN PICO`, el zurrón con **7 de 8 huecos llenos de adoquín** (64×5 + 37) y
`rutaFaena … alcance=NO fin=501,63,613 dFin=2.00`: la celda a la que le manda `puntoDeApoyoDeLaCaseta`
(`501,63,615`, dentro de la caseta) **no se alcanza desde fuera** (la ruta acaba 2 bloques antes). Es el patrón de
I95 (`el punto de apoyo del almacén es inalcanzable`) pero en la **caseta del minero**: hay que darle una **casilla de
pie** a la que sí se llegue (y desde la que se trabaje el horno y la balsa).

### 3. Las rendiciones que quedan (los destinos, no los portones)

Con el arreglo de los portones **retirado**, lo que queda es lo de I119/I122/I125 (el destino y la ruta viva). En la
corrida larga, las que más se repiten:

```
21x Filomena (Recolector) / Yendo a la taberna      19x Filomena / Recogiendo
18x Filomena / Volviendo a la plaza                 16x Tomasa (Leñador) / Llevando la madera
13x Vicenta (Ganadero) / Cuidando el ganado          4x Onofre (Guardia) / Patrullando el corral
```

Filomena sola son **62 de 159**. Merece una corrida con el volcado de la recolectora (destino, `cerebro=`, `nav=`,
goals) porque *"Yendo a la taberna"* y *"Volviendo a la plaza"* son **dos goals del pueblo**, no la faena.

### 4. Atascos sueltos ya apuntados (cuando se pueda)

- El **granjero** con la mata de trigo (la mata es un **bloque**: caminar hacia ella da ruta de 1 nodo) — el mismo
  patrón de I114. Medido en la corrida larga: **4 líneas** de `Entrando a la huerta` (antes eran 6 de 25).
- El aldeano que se queda **sin ruta** fuera del muro (`560,64,587`, `552,63,585`).

## Cómo se mide (comandos, tal cual)

```powershell
# 0) NUNCA compilar con el juego del jugador abierto (le revienta el cliente). Comprobar antes:
Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object { $_.CommandLine -match 'forgeclientdev' }

# 1) compilar + lint
$env:GRADLE_USER_HOME = "C:\Users\Christian\Documents\DevilRpg\.gradle-home"
.\gradlew.bat compileJava --console=plain          # tiene que decir BUILD SUCCESSFUL
python tools\lint_aldea.py --strict                 # tiene que decir OK (o justificar el aviso)

# 2) LA MINA SIN LEVANTAR SERVIDOR (lo primero que hay que mirar): la columna del pozo, celda a celda
python tools\arnes\columna_mina.py "New World (2)" 503 617 63 simular
python build\slice_mina.py 497 509 611 623 44 67 "New World (2)"   # mapa por capas del solar

# 3) arnés: copiarlo, encender UN modo y correr sobre una COPIA del mundo
New-Item -ItemType Directory -Force src\main\java\com\chipoodle\devilrpg\debug | Out-Null
Copy-Item tools\arnes\GuardHarness.java src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java -Force
#   (editar la copia: poner a true MEDIR_MINERO / MEDIR_LENADOR / MEDIR_NOCHE…)
Remove-Item run\world -Recurse -Force; Copy-Item 'run\saves\New World (2)' run\world -Recurse
Remove-Item run\logs\latest.log -Force
.\gradlew.bat runServer --console=plain            # ~10-15 min de reloj para una corrida útil

# 4) leer la medida  *** OJO: latest.log ROTA POR TAMAÑO ***
#    Si la corrida es larga, hay que juntar el .gz rotado con latest.log (ver más abajo) o los números salen falsos.
python tools\arnes\resumen_rendiciones.py run\logs\latest.log
Select-String -Path run\logs\latest.log -Pattern 'BOCA DE LA MINA|MINA t=|POZO t=|SONDA DEL POZO|\[Gate\]'

# 5) DESHACER (el arnés no puede quedarse: fuerza chunks y mete un jugador de pega)
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
   dice (hay tres así: dos en I119/I122 y el de los portones de esta sesión, en I126).
2. **La partida del jugador (`run/saves/New World (2)`) es de sólo lectura.** Todo se mide sobre la copia `run/world`.
3. **Apoyo local en paralelo**: `tools/arnes/consulta_local.ps1` (Ollama, `deepseek-coder-v2:16b`) — sirve para
   borradores y segundas opiniones con los **datos exactos** en la ficha; nunca para decidir ni para medir (la
   calibración está en `tools/arnes/LEEME.md`).
