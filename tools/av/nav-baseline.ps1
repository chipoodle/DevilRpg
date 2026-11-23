<#
.SYNOPSIS
    Ejecuta la linea base de navegacion (tarea Gradle navBaselineTest) y guarda su salida en build/av.

.DESCRIPTION
    Pensado para cuando el agente del planner anada `NavSearchBaselineTest` con la tarea `navBaselineTest`.

    - Detecta la tarea por el fuente de test (`src/test/**/*NavSearchBaseline*`) o por su registro en
      build.gradle(.kts). Si no existe, NO falla: avisa y sale con 0.
    - Si existe, lanza `gradlew.bat navBaselineTest --console=plain`, guarda toda la salida en
      `build/av/nav-baseline-<fecha>.txt` (y una copia `nav-baseline-ultimo.txt`) y devuelve el codigo de Gradle.
    - NO ejecuta Minecraft. No toca build/c4n ni run/saves.

    OJO: no lanzar mientras otro agente este compilando (Gradle usa el mismo build/ y el mismo daemon/cerrojo).

.PARAMETER Task
    Nombre de la tarea Gradle. Por defecto navBaselineTest.

.PARAMETER OutDir
    Carpeta de salida. Por defecto build/av.

.PARAMETER Force
    Lanza la tarea aunque la deteccion no la encuentre (util si se registra de forma dinamica).

.EXAMPLE
    .\tools\av\nav-baseline.ps1

.EXAMPLE
    .\tools\av\nav-baseline.ps1 -Force
#>
param(
    [string]$Task = 'navBaselineTest',
    [string]$OutDir = "",
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path $Raiz 'build\av' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-Sha {
    try {
        $sha = (& git -C $Raiz rev-parse HEAD 2>$null)
        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace([string]$sha)) { return ([string]$sha).Trim() }
    } catch { }
    return 'desconocido'
}

$evidencia = New-Object System.Collections.Generic.List[string]
$testSrc = @()
$testDir = Join-Path $Raiz 'src\test'
if (Test-Path -LiteralPath $testDir -PathType Container) {
    $testSrc = @(Get-ChildItem -LiteralPath $testDir -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -match 'NavSearchBaseline' } | ForEach-Object { $_.FullName.Substring($Raiz.Length + 1) })
}
foreach ($src in $testSrc) { $evidencia.Add("fuente_test=$src") }

$registro = @()
foreach ($gradle in @('build.gradle', 'build.gradle.kts')) {
    $ruta = Join-Path $Raiz $gradle
    if (Test-Path -LiteralPath $ruta -PathType Leaf) {
        $lineas = @(Select-String -LiteralPath $ruta -Pattern "navBaselineTest|$([regex]::Escape($Task))" -ErrorAction SilentlyContinue)
        if ($lineas.Count -gt 0) {
            $registro += "${gradle}:$($lineas[0].LineNumber)"
            $evidencia.Add("registro_gradle=$gradle línea $($lineas[0].LineNumber): $($lineas[0].Line.Trim())")
        }
    }
}

$existe = ($testSrc.Count -gt 0) -or ($registro.Count -gt 0)
if (-not $existe -and -not $Force) {
    Write-Output "TAREA_NO_DISPONIBLE: no encontre '$Task' (ni src/test/**/*NavSearchBaseline* ni su registro en build.gradle)."
    Write-Output '  No ejecuto Gradle y no escribo evidencia (para no dejar un fichero que parezca una medida real).'
    Write-Output '  Cuando el planner anada NavSearchBaselineTest + la tarea, vuelve a lanzar:'
    Write-Output '    .\tools\av\nav-baseline.ps1'
    Write-Output '  Si la tarea se registra de forma dinamica, fuerza con:'
    Write-Output '    .\tools\av\nav-baseline.ps1 -Force'
    exit 0
}
if (-not $existe -and $Force) {
    $evidencia.Add('deteccion=forzada_por_el_operador')
}

$gradlew = Join-Path $Raiz 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) {
    Write-Output "FALLO: no existe $gradlew; no puedo lanzar la tarea $Task"
    exit 1
}

$sello = Get-Date -Format 'yyyyMMdd-HHmmss'
$salida = Join-Path $OutDir "nav-baseline-$sello.txt"
$ultimo = Join-Path $OutDir 'nav-baseline-ultimo.txt'
$sha = Get-Sha

$cabecera = @(
    "# nav-baseline C4N/AV",
    "# fecha=$((Get-Date).ToString('o'))",
    "# sha=$sha",
    "# comando=.\gradlew.bat $Task --console=plain",
    "# tarea=$Task",
    "# deteccion=$($evidencia -join ' | ')",
    '# ------------------------------------------------------------'
)

Write-Output "Lanzando: .\gradlew.bat $Task --console=plain"
Write-Output "  salida: $salida"
Write-Output '  (no lances esto en paralelo con otra compilacion)'
$codigo = 1
try {
    # Se guarda todo junto (stdout+stderr) para que el fichero sea la evidencia completa.
    $lineas = & $gradlew $Task --console=plain 2>&1
    $codigo = if ($null -eq $LASTEXITCODE) { 0 } else { [int]$LASTEXITCODE }
    $cuerpo = @($lineas | ForEach-Object { [string]$_ })
} catch {
    $cuerpo = @("EXCEPCION_AL_LANZAR: $($_.Exception.Message)")
    $codigo = 1
}
$pie = @(
    '# ------------------------------------------------------------',
    "# exit_code=$codigo",
    "# resultado=$(if ($codigo -eq 0) { 'PASS' } else { 'FAIL' })"
)
$contenido = @($cabecera + $cuerpo + $pie) -join [Environment]::NewLine
Set-Content -LiteralPath $salida -Value $contenido -Encoding utf8
Set-Content -LiteralPath $ultimo -Value $contenido -Encoding utf8
Write-Output "Guardado: $salida"
Write-Output "Copia (ultimo): $ultimo"
Write-Output "exit_code=$codigo"
exit $codigo
