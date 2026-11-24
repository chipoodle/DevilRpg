<#
.SYNOPSIS
    Ejecuta el perfil reproducible del núcleo A* (`NavSearchPerfTest`) y lo archiva en `build/av/perf`.

.DESCRIPTION
    - Registra la tarea `navPerfTest` con `tools/perf/navperf.init.gradle` (NO edita build.gradle).
    - Calienta JIT y mide 11 repeticiones por defecto; imprime `PERF`, `PERF_RESUMEN`, `PERF_FASE`,
      `PERF_AB` y `PERF_METRICAS` con medianas, mínimo y p95.
    - Compara la implementación de producción con el oráculo congelado `NavSearchBoxedBench` y falla
      si la ruta (hash FNV de estado/pies/transición/acción/soporte), expansiones, pops o reaperturas
      difieren.
    - Guarda toda la salida en `build/av/perf/nav-perf-<fecha>.txt` (y `nav-perf-ultimo.txt`).
    - No lanza Minecraft ni toca `run/saves`, `build/c4n` ni la evidencia histórica.

.PARAMETER Reps
    Repeticiones medidas por escenario. Por defecto 11.

.PARAMETER Warmup
    Pasadas de calentamiento por escenario. Por defecto 4.

.PARAMETER FaseReps
    Repeticiones de la atribución de fases. Por defecto 5.

.PARAMETER Tag
    Sello de los ficheros de salida. Por defecto `yyyyMMdd-HHmmss`.

.EXAMPLE
    .\tools\perf\medir-nav.ps1

.EXAMPLE
    .\tools\perf\medir-nav.ps1 -Reps 21 -Warmup 6
#>
[CmdletBinding()]
param(
    [int]$Reps = 11,
    [int]$Warmup = 4,
    [int]$FaseReps = 5,
    [string]$Tag = ""
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Gradlew = Join-Path $Raiz 'gradlew.bat'
$Init = Join-Path $Raiz 'tools\perf\navperf.init.gradle'
$SalidaDir = Join-Path $Raiz 'build\av\perf'
New-Item -ItemType Directory -Force -Path $SalidaDir | Out-Null
if ([string]::IsNullOrWhiteSpace($Tag)) { $Tag = Get-Date -Format 'yyyyMMdd-HHmmss' }
$Salida = Join-Path $SalidaDir "nav-perf-$Tag.txt"
$Ultimo = Join-Path $SalidaDir 'nav-perf-ultimo.txt'

if (-not (Test-Path -LiteralPath $Gradlew -PathType Leaf)) { throw "No existe $Gradlew" }
if (-not (Test-Path -LiteralPath $Init -PathType Leaf)) { throw "No existe $Init" }

Write-Output "Lanzando navPerfTest (reps=$Reps warmup=$Warmup fases=$FaseReps)"
Write-Output "  salida: $Salida"
$lineas = & $Gradlew -I $Init navPerfTest "-PnavPerfReps=$Reps" "-PnavPerfWarmup=$Warmup" `
    "-PnavPerfFaseReps=$FaseReps" --console=plain --offline 2>&1
$codigo = if ($null -eq $LASTEXITCODE) { 1 } else { [int]$LASTEXITCODE }
$contenido = @($lineas | ForEach-Object { [string]$_ })
Set-Content -LiteralPath $Salida -Value ($contenido -join [Environment]::NewLine) -Encoding utf8
Copy-Item -LiteralPath $Salida -Destination $Ultimo -Force
$ok = ($codigo -eq 0) -and (@($contenido | Where-Object { $_ -match 'NAV_SEARCH_PERF_TEST: OK' }).Count -gt 0)
Write-Output "exit_code=$codigo ok=$ok"
Write-Output "Guardado: $Salida"
if (-not $ok) { exit 1 }
exit 0
