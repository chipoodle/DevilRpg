param(
    [Parameter(Mandatory = $true)]
    [string]$Run,
    [string]$ProbeRun = "",
    [int]$Ticks = 2400,
    [int]$Cada = 200,
    [int]$TimeoutMinutes = 25,
    [switch]$Force
)

# ---------------------------------------------------------------------------------------------------------------------
# Sonda de propiedad de puestos C4 (SOLO DIAGNÓSTICO).
#
# Copia el mundo de un run C4 a build/av/probe-game (NUNCA toca build/c4n/<Run>/game), copia
# tools/av/VillageOwnershipProbe.java al árbol de debug (ignorado por git), lanza runServer sobre la copia y espera a
# que la sonda escriba build/av/ownership-probe-<ProbeRun>/fin.txt. Después restaura cualquier fichero de debug que
# hubiera que apartar y para el servidor. NO se ejecuta desde aquí: lo lanza el operador.
#
# Ejemplo:
#   .\tools\av\lanzar-ownership-probe.ps1 -Run c4n-20261123-1550 -ProbeRun 1550-long -Ticks 2400 -Force
# ---------------------------------------------------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($ProbeRun)) {
    $ProbeRun = "probe-" + $Run
}
$runDir = Join-Path $raiz "build/c4n/$Run"
$origenGame = Join-Path $runDir 'game'
$origenWorld = Join-Path $origenGame 'world'
if (-not (Test-Path -LiteralPath $origenWorld)) {
    Write-Output "ABORTO: no existe el mundo del run C4 '$Run' en $origenWorld"
    exit 2
}
$probeGame = Join-Path $raiz 'build/av/probe-game'
$salida = Join-Path $raiz "build/av/ownership-probe-$ProbeRun"
$debugDir = Join-Path $raiz 'src/main/java/com/chipoodle/devilrpg/debug'
$destinoProbe = Join-Path $debugDir 'VillageOwnershipProbe.java'
$cuarentenaDir = Join-Path $raiz 'build/av/scratch'

Write-Output "=== OWNERSHIP PROBE run=$Run probe=$ProbeRun ticks=$Ticks cada=$Cada ==="
Write-Output "  origen (solo lectura): $origenWorld"
Write-Output "  copia de trabajo:      $probeGame"
Write-Output "  salida:                $salida"

# ------------------------------------------------------------------ preflight: no matar servidores ajenos
function Get-DevJavaProcesses {
    @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" -ErrorAction SilentlyContinue | Where-Object {
            ([string]$_.CommandLine) -match 'devlaunch\.Main|forgeserverdev|fml\.modFolders'
        } | ForEach-Object {
            [pscustomobject]@{ Id = [int]$_.ProcessId; CommandLine = [string]$_.CommandLine }
        })
}
function Assert-SinAjenos([string]$cuando) {
    $ajenos = @(Get-DevJavaProcesses)
    if ($ajenos.Count -gt 0) {
        Write-Output "ABORTO ($cuando): hay $($ajenos.Count) proceso(s) java de cliente/servidor dev ajenos."
        foreach ($p in $ajenos) { Write-Output "  PID $($p.Id): $($p.CommandLine)" }
        Write-Output '  No los mato. Cierra ese cliente/servidor y vuelve a lanzar.'
        exit 2
    }
}

# ------------------------------------------------------------------ copia del mundo (nunca el original)
function Assert-DentroDeBuildAv([string]$ruta) {
    $resuelto = (Resolve-Path -LiteralPath $ruta).Path
    $permitido = (Resolve-Path -LiteralPath (Join-Path $raiz 'build/av')).Path
    if (-not $resuelto.StartsWith($permitido + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw "ruta de trabajo fuera de build/av: $resuelto"
    }
    return $resuelto
}

if (Test-Path -LiteralPath $probeGame) {
    if (-not $Force) {
        Write-Output "ABORTO: ya existe $probeGame. Usa -Force para reemplazarlo (solo se borra esa copia de build/av)."
        exit 3
    }
    $resuelto = Assert-DentroDeBuildAv $probeGame
    Write-Output "  reemplazando copia anterior: $resuelto"
    Remove-Item -LiteralPath $resuelto -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $probeGame | Out-Null
Copy-Item -LiteralPath $origenWorld -Destination (Join-Path $probeGame 'world') -Recurse -Force
foreach ($nombre in @('server.properties', 'config', 'defaultconfigs', 'mods', 'ops.json', 'whitelist.json')) {
    $origen = Join-Path $origenGame $nombre
    if (Test-Path -LiteralPath $origen) {
        Copy-Item -LiteralPath $origen -Destination (Join-Path $probeGame $nombre) -Recurse -Force
    }
}
# El session.lock copiado no estorba (el servidor original está parado), pero se quita por limpieza.
$lock = Join-Path $probeGame 'world/session.lock'
if (Test-Path -LiteralPath $lock) { Remove-Item -LiteralPath $lock -Force }

# ------------------------------------------------------------------ fuente de la sonda y cuarentena de otros debug
New-Item -ItemType Directory -Force -Path $debugDir | Out-Null
New-Item -ItemType Directory -Force -Path $cuarentenaDir | Out-Null
$cuarentena = @()
$probePrevio = $null
$codigo = 1
$jdkPrevio = $env:JDK_JAVA_OPTIONS
try {
    # Se apartan los debug que podrían competir por el arranque/parada. Se restauran en `finally`.
    foreach ($nombre in @('OwnershipProbe.java', 'C4StandaloneHarness.java')) {
        $src = Join-Path $debugDir $nombre
        if (Test-Path -LiteralPath $src) {
            $dst = Join-Path $cuarentenaDir "$nombre.pre-ownership-probe"
            Move-Item -LiteralPath $src -Destination $dst -Force
            $cuarentena += [pscustomobject]@{ Origen = $src; Destino = $dst }
            Write-Output "  apartado temporalmente: $nombre"
        }
    }
    if (Test-Path -LiteralPath $destinoProbe) {
        $probePrevio = Join-Path $cuarentenaDir 'VillageOwnershipProbe.java.pre-ownership-probe'
        Move-Item -LiteralPath $destinoProbe -Destination $probePrevio -Force
    }
    Copy-Item -LiteralPath (Join-Path $raiz 'tools/av/VillageOwnershipProbe.java') -Destination $destinoProbe -Force
    Write-Output "  sonda copiada a: $destinoProbe (ignorada por git)"

    New-Item -ItemType Directory -Force -Path $salida | Out-Null
    $log = Join-Path $salida 'srv-ownership.log'
    $logErr = Join-Path $salida 'srv-ownership.log.err'
    $fin = Join-Path $salida 'fin.txt'

    Assert-SinAjenos 'antes de lanzar la sonda'
    $env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp" +
        " -Dprobe.run=$ProbeRun -Dprobe.ticks=$Ticks -Dprobe.cada=$Cada -Dprobe.raiz=$raiz" +
        " -Dprobe.objetivo=0 -Dprobe.mantenimiento=true" +
        " -Ddevilrpg.a2n.allowMaintenanceHook=true -Da2n.mode=raid"
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] lanzando runServer sobre la copia"
    $inicio = Get-Date
    $srv = Start-Process -FilePath (Join-Path $raiz 'gradlew.bat') `
        -ArgumentList 'runServer', '--console=plain', '-Pa2nDiagnostics=true', '-Pa2nMaintenanceHook=true', '-Pa2nMode=raid', "-Pa2nGameDirectory=build/av/probe-game" `
        -WorkingDirectory $raiz -RedirectStandardOutput $log -RedirectStandardError $logErr `
        -PassThru -NoNewWindow

    $limite = (Get-Date).AddMinutes($TimeoutMinutes)
    $terminado = $false
    while ((Get-Date) -lt $limite) {
        if (Test-Path -LiteralPath $fin) {
            $item = Get-Item -LiteralPath $fin
            if ($item.LastWriteTime -ge $inicio) {
                $terminado = $true
                break
            }
        }
        try {
            if ($srv.HasExited) {
                Start-Sleep -Seconds 3
                if (Test-Path -LiteralPath $fin) {
                    $item = Get-Item -LiteralPath $fin
                    $terminado = $item.LastWriteTime -ge $inicio
                }
                break
            }
        } catch { }
        Start-Sleep -Seconds 5
    }

    # Cierre seguro: primero se espera a que gradlew/server terminen solos; solo si no, se mata SU árbol.
    $esperado = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $esperado) {
        try { $srv.Refresh() } catch { break }
        if ($srv.HasExited) { break }
        Start-Sleep -Seconds 2
    }
    try {
        if (-not $srv.HasExited) {
            Write-Output "  el servidor no terminó solo; taskkill /PID $($srv.Id) /T /F (solo su árbol)"
            & taskkill.exe /PID $srv.Id /T /F 2>&1 | ForEach-Object { Write-Output "    $_" }
        }
    } catch { }

    if (-not $terminado) {
        Write-Output "SIN_MARCADOR: no apareció $fin dentro de $TimeoutMinutes min. Revisa $log"
        $codigo = 4
    } else {
        $textoFin = Get-Content -LiteralPath $fin -Raw
        Write-Output "=== FIN sonda ==="
        Write-Output $textoFin.Trim()
        Write-Output "  salida completa: $salida"
        if ($textoFin -match 'onceDeOnce=true') {
            Write-Output 'RESULTADO: 11/11 puestos con JOB_SITE válido y NITWIT sin estación.'
            $codigo = 0
        } else {
            Write-Output 'RESULTADO: la sonda terminó pero NO declara onceDeOnce=true. Revisa final.json/resumen.txt.'
            $codigo = 5
        }
    }
}
finally {
    # Restaurar los debug apartados y retirar la copia de la sonda.
    $env:JDK_JAVA_OPTIONS = $jdkPrevio
    if (Test-Path -LiteralPath $destinoProbe) { Remove-Item -LiteralPath $destinoProbe -Force -ErrorAction SilentlyContinue }
    if ($probePrevio -and (Test-Path -LiteralPath $probePrevio)) {
        Move-Item -LiteralPath $probePrevio -Destination $destinoProbe -Force -ErrorAction SilentlyContinue
    }
    foreach ($item in $cuarentena) {
        if (Test-Path -LiteralPath $item.Destino) {
            Move-Item -LiteralPath $item.Destino -Destination $item.Origen -Force -ErrorAction SilentlyContinue
        }
    }
}
exit $codigo
