<#
.SYNOPSIS
    Lanza la campaña RUNTIME de navegación (NavScenarioHarness) en un mundo desechable.

.DESCRIPTION
    - Copia tools/nav/NavScenarioHarness.java a src/main/java/.../debug/ (como C4/A2) y verifica SHA-256.
    - Prepara build/av/nav-<run>/game con eula/server.properties de mundo SUPERFLAT desechable (nunca run/ ni run/saves).
    - Compila con -PnavHarness=true (solo el arnés de navegación; excluye el resto de entry points debug).
    - Ejecuta runServer de forma síncrona con watchdog y conserva fin.txt, índice, JSON por escenario y metrics-*.csv.

    Requisitos: no lanzar en paralelo con otra compilación (Gradle comparte build/ y lock).
    El coordinador es quien decide ejecutar esto; el arnés NO se ejecuta en un build normal.

.PARAMETER RunId
    Identificador del run. El directorio final es build/av/nav-<RunId> (si RunId ya empieza por nav-, se respeta).

.PARAMETER Rep
    Repetición lógica (se escribe en el índice y en metrics-*-r<rep>.csv). Cada repetición = un runServer nuevo.

.PARAMETER Scenarios
    Lista separada por comas de ids, o all. Ej.: paso_arriba,caida,romper

.PARAMETER Escala
    Porcentaje (10..400) que escala los presupuestos de ticks de cada escenario. 100 = campaña completa.

.PARAMETER TraceNav
    Activa NavTrace (eventos de producción) en nav-trace.log.

.EXAMPLE
    .\tools\nav\lanzar-nav-harness.ps1 -RunId runtime-20261123-1900 -Scenarios paso_arriba,caida -Escala 100
#>
param(
    [string]$RunId = ("runtime-" + (Get-Date -Format "yyyyMMdd-HHmmss")),
    [int]$Rep = 1,
    [string]$Scenarios = "all",
    [int]$Escala = 100,
    [int]$Port = 25579,
    [int]$TimeoutMinutes = 45,
    [bool]$TraceNav = $true,
    [bool]$Metrics = $true
)

$ErrorActionPreference = "Stop"
$raiz = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $raiz

if ($RunId -notmatch '^[A-Za-z0-9._-]+$') { throw "RunId con caracteres no permitidos: $RunId" }
if ($Rep -lt 1 -or $Rep -gt 99) { throw "Rep fuera de rango: $Rep" }
if ($Escala -lt 10 -or $Escala -gt 400) { throw "Escala fuera de rango (10..400): $Escala" }
if ($Scenarios -notmatch '^[A-Za-z0-9_,*.-]+$') { throw "Scenarios con caracteres no permitidos: $Scenarios" }

$runName = if ($RunId.StartsWith("nav-")) { $RunId } else { "nav-$RunId" }
$runRoot = Join-Path $raiz "build\av\$runName"
$gameDir = Join-Path $runRoot "game"
if (Test-Path -LiteralPath $runRoot) {
    throw "El directorio del run ya existe; no se sobrescribe evidencia: $runRoot"
}
New-Item -ItemType Directory -Force -Path $gameDir | Out-Null

# --- Sincronización del arnés canónico -> debug (como C4/A2) -------------------------------------
$origen = Join-Path $raiz "tools\nav\NavScenarioHarness.java"
$destino = Join-Path $raiz "src\main\java\com\chipoodle\devilrpg\debug\NavScenarioHarness.java"
if (!(Test-Path -LiteralPath $origen)) { throw "No existe el arnés canónico: $origen" }
New-Item -ItemType Directory -Force -Path (Split-Path $destino -Parent) | Out-Null
Copy-Item -LiteralPath $origen -Destination $destino -Force
$shaOrigen = (Get-FileHash -LiteralPath $origen -Algorithm SHA256).Hash
$shaDestino = (Get-FileHash -LiteralPath $destino -Algorithm SHA256).Hash
if ($shaOrigen -ne $shaDestino) { throw "La copia del arnés no conserva SHA-256: $shaOrigen != $shaDestino" }

# Compilación explícita con --rerun-tasks: Gradle no siempre detecta que la clase de debug desapareció
# (build/classes se comparte con otros arneses) y runServer arrancaba sin NavScenarioHarness.
Write-Host "Compilando NavScenarioHarness (--rerun-tasks)..."
& (Join-Path $raiz 'gradlew.bat') compileJava -PnavHarness=true --rerun-tasks --console=plain
if ($LASTEXITCODE -ne 0) { throw "compileJava -PnavHarness=true fallo (exit $LASTEXITCODE)" }
if (!(Test-Path -LiteralPath 'build/classes/java/main/com/chipoodle/devilrpg/debug/NavScenarioHarness.class')) {
    throw "compileJava termino pero no genero NavScenarioHarness.class"
}

# --- Mundo desechable ----------------------------------------------------------------------------
@("eula=true") | Set-Content -LiteralPath (Join-Path $gameDir "eula.txt") -Encoding ascii
@(
    "level-name=nav",
    "level-type=minecraft:flat",
    "generator-settings={}",
    "level-seed=av-nav-20261123",
    "server-port=$Port",
    "server-ip=127.0.0.1",
    "online-mode=false",
    "enable-command-block=false",
    "spawn-protection=0",
    "view-distance=6",
    "simulation-distance=8",
    "max-tick-time=-1",
    "allow-flight=true",
    "spawn-monsters=false",
    "difficulty=normal",
    "sync-chunk-writes=false",
    "motd=AV disposable nav scenario harness"
) | Set-Content -LiteralPath (Join-Path $gameDir "server.properties") -Encoding ascii

$sha = "desconocido"
try {
    $sha = (& git -C $raiz rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0) { $sha = "desconocido" }
} catch { $sha = "desconocido" }
$sha = ([string]$sha).Trim()
@(
    "runId=$RunId",
    "runName=$runName",
    "rep=$Rep",
    "escenarios=$Scenarios",
    "escala=$Escala",
    "serverPort=$Port",
    "sha=$sha",
    "harnessSha256=$shaOrigen",
    "worldType=superflat-disposable",
    "gameDirectory=build/av/$runName/game",
    "headless=true",
    "runSavesTouched=false"
) | Set-Content -LiteralPath (Join-Path $runRoot "launcher-manifest.txt") -Encoding utf8
@($sha) | Set-Content -LiteralPath (Join-Path $runRoot "sha.txt") -Encoding ascii

$gameDirArg = "build/av/$runName/game"
$gradleArgs = @(
    "runServer",
    "-PnavHarness=true",
    "-PnavHarnessRun=$RunId",
    "-PnavHarnessRep=$Rep",
    "-PnavHarnessScenarios=$Scenarios",
    "-PnavHarnessEscala=$Escala",
    "-Pa2nGameDirectory=$gameDirArg",
    "-Pa2nTraceNav=$($TraceNav.ToString().ToLowerInvariant())",
    "-PnavMetrics=$($Metrics.ToString().ToLowerInvariant())",
    "--no-daemon",
    "--offline",
    "--console=plain"
)

Write-Host "AV nav harness run: $runName (rep=$Rep escala=$Escala%)"
Write-Host "Mundo desechable: $gameDir"
Write-Host "SHA: $sha"
Write-Host "Arnes: $destino (sha256=$shaOrigen)"
Write-Host "Lanzando gradle runServer (headless, sin cliente). Watchdog=${TimeoutMinutes}min"

$logPath = Join-Path $runRoot "launcher.log"
$cmdFile = Join-Path $runRoot "launch.cmd"
$gradleLinea = ".\gradlew.bat " + ($gradleArgs -join " ") + ' > "' + $logPath + '" 2>&1'
@(
    "@echo off",
    "cd /d `"$raiz`"",
    "call $gradleLinea",
    "exit /b %ERRORLEVEL%"
) | Set-Content -LiteralPath $cmdFile -Encoding ascii
$inicio = Get-Date
$proceso = Start-Process -FilePath $env:ComSpec -ArgumentList @("/d", "/c", $cmdFile) -PassThru -NoNewWindow
$limite = $inicio.AddMinutes($TimeoutMinutes)
while (-not $proceso.HasExited -and (Get-Date) -lt $limite) {
    Start-Sleep -Seconds 2
    try { $proceso.Refresh() } catch { break }
}
if (-not $proceso.HasExited) {
    try {
        & (Join-Path $env:WINDIR "System32\taskkill.exe") /PID $proceso.Id /T /F 2>&1 | Out-Null
    } catch { }
    throw "runServer excedio ${TimeoutMinutes} min; arbol terminado. Revisar $logPath"
}
$gradleExit = $proceso.ExitCode

$finPath = Join-Path $runRoot "fin.txt"
if (!(Test-Path -LiteralPath $finPath)) {
    Get-Content -LiteralPath $logPath -Tail 80 | ForEach-Object { Write-Host $_ }
    throw "El servidor termino sin fin.txt (gradleExit=$gradleExit). Revisar $logPath"
}
$veredicto = (Get-Content -LiteralPath $finPath -Raw).Trim()
Write-Host "Veredicto: $veredicto"

$pass = 0; $fail = 0; $invalid = 0; $timeout = 0
$resultado = Join-Path $runRoot "resultado.json"
if (Test-Path -LiteralPath $resultado) {
    try {
        $json = Get-Content -LiteralPath $resultado -Raw | ConvertFrom-Json
        $pass = @($json.veredictos | Where-Object { $_.veredicto -eq 'PASS' }).Count
        $fail = @($json.veredictos | Where-Object { $_.veredicto -eq 'FAIL' }).Count
        $invalid = @($json.veredictos | Where-Object { $_.veredicto -eq 'INVALID' }).Count
        $timeout = @($json.veredictos | Where-Object { $_.veredicto -eq 'TIMEOUT' }).Count
    } catch { Write-Warning "No pude parsear resultado.json: $($_.Exception.Message)" }
}
$metricas = Join-Path $runRoot "metrics-agregado.csv"
if (Test-Path -LiteralPath $metricas) {
    Write-Host "Metricas agregadas:"
    Get-Content -LiteralPath $metricas | ForEach-Object { Write-Host "  $_" }
}
Write-Host "Resumen: PASS=$pass FAIL=$fail INVALID=$invalid TIMEOUT=$timeout"
Write-Host "Evidencia: $runRoot"

if ($gradleExit -ne 0) { throw "Gradle runServer termino con codigo $gradleExit" }
if ($veredicto -notmatch '^NAV_PASS') {
    throw "Campaña no aceptada: $veredicto. Revisar $runRoot\resultado.json y $gameDir\logs\latest.log"
}
Write-Host "NAV PASS: evidencia en $runRoot"
