param(
    [string]$Run = "",
    [int]$TimeoutMinutes = 15,
    [switch]$SinMantenimiento
)

$ErrorActionPreference = 'Stop'
$raiz = (Get-Location).Path
if ([string]::IsNullOrWhiteSpace($Run)) {
    $Run = "c4n-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
}
$base = Join-Path $raiz "build/c4n/$Run"
New-Item -ItemType Directory -Force -Path $base | Out-Null
$gameDir = Join-Path $base 'game'
New-Item -ItemType Directory -Force -Path $gameDir | Out-Null
$sha = (& git rev-parse HEAD).Trim()
Set-Content -Path (Join-Path $base 'sha.txt') -Value $sha -Encoding UTF8

Write-Output "=== C4N run=$Run sha=$sha : $(Get-Date -Format 'HH:mm:ss') ==="
Write-Output "  juego: $gameDir (desechable, mismo mundo para las 3 fases)"

# ------------------------------------------------------------------ arnes temporal
$destinoArnes = Join-Path $raiz 'src/main/java/com/chipoodle/devilrpg/debug/C4StandaloneHarness.java'
Copy-Item -Path (Join-Path $raiz 'tools/c4n/C4StandaloneHarness.java') -Destination $destinoArnes -Force
Write-Output "  arnes: $destinoArnes (TEMPORAL, ignorado por git)"

# ------------------------------------------------------------------ init script (gameDirectory unico)
$init = Join-Path $base 'init-c4n.gradle'
$initContenido = @"
allprojects { p ->
    p.tasks.matching { it.name == 'runServer' }.configureEach { t ->
        try {
            t.gameDirectory = p.file('build/c4n/$Run/game')
            p.logger.lifecycle('[C4N] gameDirectory del runServer = {}', t.gameDirectory)
        } catch (Exception e) {
            p.logger.lifecycle('[C4N] NO pude aislar el gameDirectory: {}', e.toString())
        }
    }
}
"@
Set-Content -Path $init -Value $initContenido -Encoding UTF8

function Get-ServerPids {
    @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" | Where-Object {
        ([string]$_.CommandLine) -match 'devlaunch\.Main|forgeserverdev|fml\.modFolders'
    } | ForEach-Object { [int]$_.ProcessId })
}

function Stop-ServerTree {
    $pids = Get-ServerPids
    foreach ($pid0 in $pids) {
        if (Get-Process -Id $pid0 -ErrorAction SilentlyContinue) {
            Stop-Process -Id $pid0 -Force -ErrorAction SilentlyContinue
        }
    }
    Start-Sleep -Seconds 3
}

function Wait-Done([string]$fase, [int]$minutos, $proc) {
    $archivo = Join-Path $base "done-$fase.txt"
    $limite = (Get-Date).AddMinutes($minutos)
    $inicio = Get-Date
    while ((Get-Date) -lt $limite) {
        if (Test-Path $archivo) {
            return $true
        }
        # Solo se rinde si el proceso lanzador lleva tiempo terminado y no hay marcador.
        if ($proc -and $proc.HasExited -and ((Get-Date) - $inicio).TotalMinutes -gt 3) {
            Start-Sleep -Seconds 5
            return (Test-Path $archivo)
        }
        Start-Sleep -Seconds 5
    }
    return $false
}
$resultados = @{}
$fases = @('prepare', 'reload1', 'reload2')
foreach ($fase in $fases) {
    $archivoDone = Join-Path $base "done-$fase.txt"
    Remove-Item -LiteralPath $archivoDone -ErrorAction SilentlyContinue
    $log = Join-Path $base "srv-$fase.log"
    $env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp -Dc4n.run=$Run -Dc4n.phase=$fase -Dc4n.raiz=$raiz -Ddevilrpg.a2n.allowMaintenanceHook=true" + $(if($SinMantenimiento){" -Dc4n.mantInicial=false -Dc4n.mantPeriodico=false"}else{""})
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: lanzando runServer"
    $srv = Start-Process -FilePath (Join-Path $raiz 'gradlew.bat') `
        -ArgumentList 'runServer', '--console=plain', '-Pa2nDiagnostics=true', '-Pa2nMaintenanceHook=true', '-Pa2nMode=raid', "-Pa2nGameDirectory=build/c4n/$Run/game" `
        -WorkingDirectory $raiz -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -PassThru -NoNewWindow
    $ok = Wait-Done $fase $TimeoutMinutes $srv
    Stop-ServerTree
    $json = Join-Path $base "phase-$fase.json"
    $res = 'SIN_RESULTADO'
    if (Test-Path $json) {
        try {
            $data = Get-Content $json -Raw | ConvertFrom-Json
            $res = [string]$data.result
        } catch {
            $res = 'JSON_INVALIDO'
        }
    }
    $resultados[$fase] = $res
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: done=$ok result=$res"
}

$pasan = @($fases | Where-Object { $resultados[$_] -eq 'PASS' }).Count
$veredicto = if ($pasan -eq 3) { 'C4N_PASS' } else { 'C4N_FAIL' }
$detalle = ($fases | ForEach-Object { "$_=$($resultados[$_])" }) -join ' '
Set-Content -Path (Join-Path $base 'fin.txt') -Value "$veredicto $detalle" -Encoding UTF8
Write-Output "=== FIN $Run : $veredicto $detalle ==="
