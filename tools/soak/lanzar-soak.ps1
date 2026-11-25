param(
    [string]$Run = "",
    [int]$Ticks = 72000,
    [int]$TimeoutMinutes = 60,
    [long]$Seed = 0,
    [switch]$Diag,
    [switch]$PoiDiag
)

$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Run)) {
    $Run = "soak-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
}
if ($Ticks -lt 200 -or $Ticks -gt 200000) {
    Write-Output "ABORTO: -Ticks debe estar entre 200 y 200000 (recibido $Ticks)."
    exit 4
}
$base = Join-Path $raiz "build/soak/$Run"
$gameDir = Join-Path $base 'game'
if (Test-Path -LiteralPath (Join-Path $base 'fin.txt')) {
    Write-Output "ABORTO: el run '$Run' ya tiene fin.txt; usa un -Run nuevo."
    exit 3
}
New-Item -ItemType Directory -Force -Path $gameDir | Out-Null
if ($Seed -ne 0) {
    $props = Join-Path $gameDir 'server.properties'
    Set-Content -LiteralPath $props -Encoding ASCII -Value @(
        "level-seed=$Seed",
        'online-mode=false',
        'spawn-protection=0',
        'view-distance=10',
        'simulation-distance=10'
    )
    Write-Output "  seed=$Seed (server.properties)"
}
$sha = (& git -C $raiz rev-parse HEAD).Trim()
Set-Content -LiteralPath (Join-Path $base 'sha.txt') -Value $sha -Encoding UTF8

# Preflight: no cliente/servidor dev ajeno. No se mata nada a ciegas.
$ajenos = @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" -ErrorAction SilentlyContinue |
    Where-Object { ([string]$_.CommandLine) -match 'devlaunch\.Main|forgeserverdev|fml\.modFolders' })
if ($ajenos.Count -gt 0) {
    Write-Output "ABORTO: hay $($ajenos.Count) proceso(s) java dev ajenos; no los mato."
    $ajenos | ForEach-Object { Write-Output "  PID $($_.ProcessId): $($_.CommandLine)" }
    exit 2
}

Write-Output "=== SOAK run=$Run sha=$sha : $(Get-Date -Format 'HH:mm:ss') ==="
Write-Output "  ticks=$Ticks timeout=${TimeoutMinutes}m game=$gameDir"
$env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp -Dsoak.run=$Run -Dsoak.raiz=$raiz -Dsoak.ticks=$Ticks -Ddevilrpg.a2n.allowMaintenanceHook=true" + $(if ($Diag) { " -Ddevilrpg.taverndiag=true -Dsoak.stuckdiag=true -Dsoak.poidiag=true" } else { "" }) + $(if ($PoiDiag) { " -Dsoak.poidiag=true" } else { "" })
$log = Join-Path $base 'srv.log'
$srv = Start-Process -FilePath (Join-Path $raiz 'gradlew.bat') `
    -ArgumentList 'runServer', '--console=plain', '-PsoakHarness=true', '-Pa2nDiagnostics=true', `
        '-Pa2nMaintenanceHook=true', '-Pa2nMode=raid', "-Pa2nGameDirectory=build/soak/$Run/game" `
    -WorkingDirectory $raiz -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
    -PassThru -NoNewWindow

$fin = Join-Path $base 'fin.txt'
$limite = (Get-Date).AddMinutes($TimeoutMinutes)
while ((Get-Date) -lt $limite) {
    if (Test-Path -LiteralPath $fin) { break }
    try {
        if ($srv.HasExited) { break }
    } catch { break }
    Start-Sleep -Seconds 5
}
# Espera el cierre ordenado del servidor; si no, mata SOLO su arbol.
$cerrado = $false
try {
    $cerrado = $srv.WaitForExit(120000)
} catch { $cerrado = $false }
if (-not $cerrado) {
    Write-Output "  servidor no cerro en 120 s: taskkill /PID $($srv.Id) /T /F (solo su arbol)"
    & taskkill.exe /PID $srv.Id /T /F 2>&1 | ForEach-Object { Write-Output "    $_" }
    Start-Sleep -Seconds 3
}

$resultado = 'SOAK_SIN_RESULTADO'
if (Test-Path -LiteralPath $fin) {
    $texto = (Get-Content -LiteralPath $fin -Raw -ErrorAction SilentlyContinue)
    if (-not [string]::IsNullOrWhiteSpace($texto)) { $resultado = $texto.Trim() }
}
$scan = @()
if (Test-Path -LiteralPath $log) {
    $scan = @(Select-String -LiteralPath $log -Pattern 'Exception in thread|ServerHangWatchdog|BUILD FAILED' -ErrorAction SilentlyContinue)
}
$scanTexto = if ($scan.Count -eq 0) { 'sin excepciones no controladas' } else { "$($scan.Count) hallazgo(s)" }
Set-Content -LiteralPath (Join-Path $base 'launcher.txt') -Encoding UTF8 -Value @(
    "run=$Run", "sha=$sha", "ticks=$Ticks", "result=$resultado", "log_scan=$scanTexto",
    "fin=$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
)
if ($scan.Count -gt 0) {
    $scan | ForEach-Object { $_.Line } | Set-Content -LiteralPath (Join-Path $base 'log-scan.txt') -Encoding UTF8
    if ($resultado -like 'SOAK_PASS*') {
        $resultado = 'SOAK_FAIL_LOG "excepcion/watchdog en srv.log"'
        Set-Content -LiteralPath (Join-Path $base 'launcher-result.txt') -Value $resultado -Encoding UTF8
    }
}
Write-Output "=== FIN $Run : $resultado (log: $scanTexto) ==="
if ($resultado -like 'SOAK_PASS*') { exit 0 } else { exit 1 }
