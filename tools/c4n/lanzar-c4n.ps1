param(
    [string]$Run = "",
    [int]$TimeoutMinutes = 15,
    [int]$TrabajoTicks = 600,
    [switch]$ExigirCobertura,
    [switch]$SinMantenimiento
)

$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Run)) {
    $Run = "c4n-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
}
if ($TrabajoTicks -lt 1 -or $TrabajoTicks -gt 20000) {
    Write-Output "ABORTO: -TrabajoTicks debe estar entre 1 y 20000 (recibido $TrabajoTicks)."
    exit 4
}
$base = Join-Path $raiz "build/c4n/$Run"
New-Item -ItemType Directory -Force -Path $base | Out-Null
$gameDir = Join-Path $base 'game'
New-Item -ItemType Directory -Force -Path $gameDir | Out-Null
$sha = (& git rev-parse HEAD).Trim()
Set-Content -Path (Join-Path $base 'sha.txt') -Value $sha -Encoding UTF8

Write-Output "=== C4N run=$Run sha=$sha : $(Get-Date -Format 'HH:mm:ss') ==="
Write-Output "  juego: $gameDir (desechable, mismo mundo para las 3 fases)"
Write-Output "  trabajoTicks=$TrabajoTicks exigirCobertura=$([bool]$ExigirCobertura)"
if ($TrabajoTicks -lt 1800 -and $ExigirCobertura) {
    Write-Output "  AVISO: -ExigirCobertura con $TrabajoTicks ticks (<1800): la gracia de carga es 1200; la cobertura 11/11 puede ser inalcanzable."
}

# ------------------------------------------------------------------ arnes temporal
$destinoArnes = Join-Path $raiz 'src/main/java/com/chipoodle/devilrpg/debug/C4StandaloneHarness.java'
Copy-Item -Path (Join-Path $raiz 'tools/c4n/C4StandaloneHarness.java') -Destination $destinoArnes -Force
Write-Output "  arnes: $destinoArnes (TEMPORAL, ignorado por git)"

# NOTA (E · revision del arnes): el antiguo `init-c4n.gradle` era codigo muerto (nunca se pasaba con
# --init-script). El aislamiento real es `-Pa2nGameDirectory=build/c4n/$Run/game`, que YA se pasa abajo.
# No se genera mas ese fichero. Los `init-c4n.gradle` de runs antiguos son evidencia historica: no se tocan.

# ------------------------------------------------------------------ procesos: preflight y cierre seguro
function Get-DevJavaProcesses {
    @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" -ErrorAction SilentlyContinue | Where-Object {
            ([string]$_.CommandLine) -match 'devlaunch\.Main|forgeserverdev|fml\.modFolders'
        } | ForEach-Object {
            [pscustomobject]@{ Id = [int]$_.ProcessId; CommandLine = [string]$_.CommandLine }
        })
}

# Aborta si hay un cliente/servidor dev ajeno: NO se mata a ciegas (antes se mataba cualquier java con ese
# patron, incluido el cliente del jugador). Solo se cierra el arbol del gradlew que lanza este script.
function Assert-SinAjenos([string]$cuando) {
    $ajenos = @(Get-DevJavaProcesses)
    if ($ajenos.Count -gt 0) {
        Write-Output "ABORTO ($cuando): hay $($ajenos.Count) proceso(s) java de cliente/servidor dev ajenos."
        foreach ($p in $ajenos) {
            Write-Output "  PID $($p.Id): $($p.CommandLine)"
        }
        Write-Output '  No los mato. Cierra ese cliente/servidor (o espera a que termine) y vuelve a lanzar.'
        exit 2
    }
}

function Wait-ServerExit($proc, [int]$segundos) {
    if (-not $proc) { return $true }
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        try { $proc.Refresh() } catch { return $true }
        if ($proc.HasExited) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Stop-OurServer($proc, [string]$fase) {
    if (-not $proc) { return }
    if (Wait-ServerExit $proc 60) {
        Write-Output "  fase ${fase}: gradlew termino solo"
        return
    }
    # Ultimo recurso y SOLO el arbol de ESTE lanzamiento: nunca otros java del sistema.
    Write-Output "  fase ${fase}: gradlew sigue vivo tras 60 s; taskkill /PID $($proc.Id) /T /F (solo su arbol)"
    & taskkill.exe /PID $proc.Id /T /F 2>&1 | ForEach-Object { Write-Output "    $_" }
    Start-Sleep -Seconds 3
}

function Wait-Done([string]$fase, [int]$minutos, $proc, [datetime]$inicio) {
    $archivo = Join-Path $base "done-$fase.txt"
    $limite = (Get-Date).AddMinutes($minutos)
    while ((Get-Date) -lt $limite) {
        if (Test-Path -LiteralPath $archivo) {
            # No se borran marcadores antiguos: solo cuenta el que se escribio DESPUES de lanzar esta fase.
            $item = Get-Item -LiteralPath $archivo -ErrorAction SilentlyContinue
            if ($item -and $item.LastWriteTime -ge $inicio) {
                return $true
            }
        }
        # Solo se rinde si el proceso lanzador lleva tiempo terminado y no hay marcador nuevo.
        try {
            if ($proc -and $proc.HasExited -and ((Get-Date) - $inicio).TotalMinutes -gt 3) {
                Start-Sleep -Seconds 5
                $item = Get-Item -LiteralPath $archivo -ErrorAction SilentlyContinue
                return ($item -and $item.LastWriteTime -ge $inicio)
            }
        } catch { }
        Start-Sleep -Seconds 5
    }
    return $false
}
Assert-SinAjenos 'antes de empezar'
# Un run id NUNCA se reutiliza: si ya hay evidencia (fin.txt, phase-*.json o done-*.txt), se aborta sin borrar nada.
$evidenciaPrevia = @(Get-ChildItem -LiteralPath $base -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'fin.txt' -or $_.Name -like 'phase-*.json' -or $_.Name -like 'done-*.txt' })
if ($evidenciaPrevia.Count -gt 0) {
    Write-Output "ABORTO: el run '$Run' ya tiene evidencia ($($evidenciaPrevia.Count) fichero(s)); usa un -Run nuevo."
    Write-Output '  No se borra nada: la evidencia anterior se conserva.'
    exit 3
}
$resultados = @{}
$fases = @('prepare', 'reload1', 'reload2')
foreach ($fase in $fases) {
    Assert-SinAjenos "antes de $fase"
    $log = Join-Path $base "srv-$fase.log"
    $env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp -Dc4n.run=$Run -Dc4n.phase=$fase -Dc4n.raiz=$raiz -Dc4n.trabajoTicks=$TrabajoTicks -Ddevilrpg.a2n.allowMaintenanceHook=true" + $(if($SinMantenimiento){" -Dc4n.mantInicial=false -Dc4n.mantPeriodico=false"}else{""}) + $(if($ExigirCobertura){" -Dc4n.exigirCobertura=true"}else{""})
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: lanzando runServer"
    $inicioFase = Get-Date
    $srv = Start-Process -FilePath (Join-Path $raiz 'gradlew.bat') `
        -ArgumentList 'runServer', '--console=plain', '-Pa2nDiagnostics=true', '-Pa2nMaintenanceHook=true', '-Pa2nMode=raid', '-Pa2nProbeOnly=true', "-Pa2nGameDirectory=build/c4n/$Run/game" `
        -WorkingDirectory $raiz -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -PassThru -NoNewWindow
    $ok = Wait-Done $fase $TimeoutMinutes $srv $inicioFase
    # Cierre seguro: primero se espera a que gradlew/server terminen solos; solo si no, se mata SU arbol.
    Stop-OurServer $srv $fase
    $json = Join-Path $base "phase-$fase.json"
    $done = Join-Path $base "done-$fase.txt"
    $res = 'SIN_RESULTADO'
    $doneRes = 'SIN_MARCADOR'
    if (Test-Path -LiteralPath $done) {
        $doneTexto = Get-Content -LiteralPath $done -Raw -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($doneTexto)) {
            $doneRes = $doneTexto.Trim()
        }
    }
    if (Test-Path -LiteralPath $json) {
        $jsonItem = Get-Item -LiteralPath $json
        if (-not $ok -or $jsonItem.LastWriteTime -lt $inicioFase) {
            # No se acepta un JSON de una ejecucion anterior aunque contenga PASS.
            $res = 'JSON_ANTIGUO'
        } else {
            try {
                $data = Get-Content -LiteralPath $json -Raw | ConvertFrom-Json
                $res = [string]$data.result
            } catch {
                $res = 'JSON_INVALIDO'
            }
        }
    }
    # El marcador de fin y el JSON deben coincidir en PASS; si no, la fase NO es PASS.
    if ($doneRes -ne 'PASS' -and $res -eq 'PASS') {
        $res = "DONE_$doneRes"
    }
    $resultados[$fase] = $res
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: done=$ok done_res=$doneRes result=$res"
}

$pasan = @($fases | Where-Object { $resultados[$_] -eq 'PASS' }).Count
$veredicto = if ($pasan -eq 3) { 'C4N_PASS' } else { 'C4N_FAIL' }
$detalle = ($fases | ForEach-Object { "$_=$($resultados[$_])" }) -join ' '
Set-Content -Path (Join-Path $base 'fin.txt') -Value "$veredicto $detalle" -Encoding UTF8
Write-Output "=== FIN $Run : $veredicto $detalle ==="
