# TANDA RÁPIDA (30-sep-2026) — el banco de pruebas de 3 minutos
#
# POR QUÉ EXISTE: medir con la tanda de 20 minutos cuesta 80 minutos por pregunta (4 corridas) y eso es lo que ha hecho
# que dos semanas de trabajo parezcan eternas. Esta tanda arranca el pueblo, lo deja trabajar **3 minutos de reloj**
# (~2 minutos de juego, que es donde ocurren los primeros recados de cada oficio) y devuelve, además del total de
# avisos de rendición, **el recuento por etiqueta**: que es lo único que hace falta para saber si un arreglo sirve.
#
# Las 4 corridas largas se reservan para CONFIRMAR un arreglo que ya salió bien aquí (así lo pidió el jugador).
#
# Uso:  pwsh -NoProfile -File build\tanda-rapida.ps1 1 2      (dos corridas de 3 minutos = 6-7 minutos)
$ErrorActionPreference = 'Continue'
$env:GRADLE_USER_HOME = 'C:\Users\Christian\Documents\DevilRpg\.gradle-home'
Set-Location 'C:\Users\Christian\Documents\DevilRpg'
$MINUTOS = 3
# CONSERVAR EL MUNDO (3-oct-2026): con -Conservar NO se restaura `run\world` del guardado del jugador entre corridas.
# Hace falta porque la primera corrida PAGA LA MIGRACION (el trazado sube y el pueblo se rehace) y esa migracion ensucia
# la medida con sus avisos de rendicion: con -Conservar, la primera corrida deja el mundo ya migrado y las siguientes
# miden el pueblo ASENTADO. La partida del jugador NO se toca (siempre se trabaja sobre la copia `run\world`).
$Conservar = $false
$numeros = @()
foreach ($a in $args) { if ("$a" -eq '-Conservar') { $Conservar = $true } else { $numeros += $a } }

$arnes = 'src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java'
if (-not (Test-Path $arnes)) { Write-Output 'ABORTADO: falta el arnes (copiar tools\arnes\GuardHarness.java)'; exit 1 }

# =====================================================================================================================
# REGLA DE ORO (8-oct-2026): `run\saves` ES LA PARTIDA DEL JUGADOR. EL ARNES NUNCA ESCRIBE NI BORRA AHI.
# ---------------------------------------------------------------------------------------------------------------------
# POR QUE ESTA AQUI Y GRITANDO: en la sesion del 6-oct-2026 el agente (yo) borro `run\saves\New World` varias veces
# para «regenerar aldeas limpias», y **destruyo la partida del jugador** — `Remove-Item -Recurse -Force` NO pasa por la
# papelera de reciclaje, asi que no se pudo recuperar ✓. El banco de pruebas siempre ha trabajado sobre una COPIA
# (`run\world`); lo que estaba mal era que el agente borrase el ORIGEN.
# A partir de aqui: si el guardado del jugador no esta, la tanda SE ABORTA con un mensaje claro en vez de medir sobre un
# mundo cualquiera, y NUNCA se borra nada de `run\saves`.
# =====================================================================================================================
$guardadoDelJugador = 'run\saves\New World'
if (-not (Test-Path "$guardadoDelJugador\level.dat")) {
    Write-Output 'ABORTADO: no existe el guardado del jugador en run\saves\New World.'
    Write-Output '  El arnes mide sobre una COPIA (run\world) y necesita ese origen. NO teclees nunca un Remove-Item'
    Write-Output '  sobre run\saves: ahi vive la partida del jugador. Si quieres medir un mundo nuevo, crea el guardado'
    Write-Output '  en el juego (o copia otro) y vuelve a lanzar la tanda.'
    exit 1
}

foreach ($i in $numeros) {
    Write-Output "=== CORRIDA RAPIDA $i : $(Get-Date -Format 'HH:mm:ss') ==="
    $vivos = (Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' }).Count
    if ($vivos -gt 0) { Write-Output "ABORTADO en la corrida ${i}: $vivos servidor(es) vivo(s)"; exit 1 }
    if (-not $Conservar -or -not (Test-Path run\world\level.dat)) {
        # SOLO se borra la COPIA (run\world). `run\saves` no se toca jamas.
        Remove-Item run\world -Recurse -Force -ErrorAction SilentlyContinue
        Copy-Item $guardadoDelJugador run\world -Recurse
    } else {
        Write-Output "   (mundo conservado: el pueblo ya no migra, se mide asentado)"
    }
    Remove-Item run\logs\latest.log -Force -ErrorAction SilentlyContinue
    # CIERRE LIMPIO: se le dice al arnes a que tick parar (20 ticks/s, con 10 s de margen). Asi el servidor GUARDA el
    # mundo y el trazado nuevo de la aldea queda en disco: las corridas siguientes, con -Conservar, miden el pueblo
    # ASENTADO. Sin esto el banco mataba el proceso y el mundo no se guardaba (y cada corrida volvia a migrar).
    [int]$tickParar = [int]($MINUTOS * 60 * 20 * 0.85)
    Set-Content -Path run\arnes-parar.txt -Value $tickParar -Encoding ascii
    # CANDADO DEL ARNES (3-oct-2026): el arnes SOLO se ejecuta si esta variable esta puesta (ver GuardHarness). Asi,
    # aunque su fichero vuelva a copiarse a `src/main/java`, un `runClient` normal NO lo ejecuta nunca: se colo en la
    # partida del jugador y le vacio el almacen (`[Arnes] REMESA: almacen vaciado (0 pila(s) fuera...)`).
    $env:DEVILRPG_ARNES = '1'
    $p = Start-Process -FilePath '.\gradlew.bat' -ArgumentList 'runServer','--console=plain' -PassThru -NoNewWindow `
        -RedirectStandardOutput "build\runserver-rapida$i.txt" -RedirectStandardError "build\runserver-rapida$i.err"
    Start-Sleep -Seconds ($MINUTOS * 60)
    # Y SE LE ESPERA: si cierra solo, el mundo esta guardado. Solo se mata si no ha cerrado.
    # OJO: la espera era de 120 s y el guardado de salida (una aldea entera, con su trazado) puede tardar mas: en la
    # corrida 4 de esta ronda el servidor NO llego a cerrar en 120 s y la corrida se quedo SIN `CIERRE LIMPIO`, o sea
    # sin valor (la regla del banco). Se le da margen.
    $espera = 0
    while (-not $p.HasExited -and $espera -lt 300) { Start-Sleep -Seconds 5; $espera += 5 }
    Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 8
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    $log = "build\rapida-$i.log"
    Copy-Item run\logs\latest.log $log -Force
    $cierre = (Get-Content $log | Select-String -Pattern 'CIERRE LIMPIO').Count
    if ($cierre -gt 0) { Write-Output "   (cierre limpio: el mundo quedo guardado)" }
    else { Write-Output "   (AVISO: no hubo cierre limpio; el mundo NO se guardo)" }

    $avisos = Get-Content $log | Select-String -Pattern 'no consigue llegar'
    Write-Output "corrida rapida $i : $($avisos.Count) avisos de rendicion"
    # EL RECUENTO POR ETIQUETA: es lo que dice si el arreglo ha servido.
    $etiquetas = @{}
    foreach ($a in $avisos) {
        $m = [regex]::Match($a.Line, 'etiqueta="([^"]*)"')
        $e = if ($m.Success) { $m.Groups[1].Value } else { '(sin etiqueta)' }
        $e = $e -replace '^\w+ \(([^)]*)\) / ', ''
        if ($etiquetas.ContainsKey($e)) { $etiquetas[$e]++ } else { $etiquetas[$e] = 1 }
    }
    if ($etiquetas.Count -eq 0) { Write-Output '   (ninguna etiqueta)' }
    foreach ($k in ($etiquetas.Keys | Sort-Object { -$etiquetas[$_] })) {
        Write-Output ("   {0,2}x  {1}" -f $etiquetas[$k], $k)
    }
    # Y LAS TRAZAS DE LA REPARACION QUE IMPORTAN (cimiento y huerta), que es lo que se esta midiendo ahora.
    Get-Content $log | Select-String -Pattern 'CIMIENTO|huerta ASENTADA|REPARADA \(quitados' |
        Select-Object -First 3 | ForEach-Object {
            $t = ($_.Line -replace '.*\[devilrpg/\]: ', '')
            if ($t.Length -gt 150) { $t = $t.Substring(0, 150) }
            Write-Output "   traza: $t"
        }

    # EL TRABAJO DEL PUEBLO (30-sep-2026) — LA METRICA QUE SI DISTINGUE.
    # Contar rendiciones en 3 minutos es demasiado ruidoso (salen de 0 a 4) y no sirve para saber si un arreglo mejora
    # algo. Lo que SI es estable es cuanto TRABAJA el pueblo: si los aldeanos dejan de dar vueltas y de rendirse, los
    # sucesos de cada oficio suben. Esto se mira ANTES y DESPUES de cada arreglo.
    $trabajo = [ordered]@{
        'granja'    = (Get-Content $log | Select-String -Pattern 'El granjero:').Count
        'ganado'    = (Get-Content $log | Select-String -Pattern 'El ganadero:').Count
        'pescador'  = (Get-Content $log | Select-String -Pattern 'El pescador:').Count
        'herreria'  = (Get-Content $log | Select-String -Pattern 'El herrero|Forjo|Fundio').Count
        'cocina'    = (Get-Content $log | Select-String -Pattern 'El cocinero|pieza\(s\) cocinadas').Count
        'minero'    = (Get-Content $log | Select-String -Pattern 'El minero').Count
        'guardia'   = (Get-Content $log | Select-String -Pattern 'ENTRENO|entrenado').Count
        'lenador'   = (Get-Content $log | Select-String -Pattern 'El lenador|lenador').Count
    }
    $linea = ($trabajo.GetEnumerator() | ForEach-Object { "$($_.Key) $($_.Value)" }) -join '  '
    Write-Output "   TRABAJO: $linea"
}
Write-Output "=== TANDA RAPIDA ACABADA $(Get-Date -Format 'HH:mm:ss') ==="
