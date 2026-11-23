<#
.SYNOPSIS
    Colector de evidencia C4N: tabla por fase de todos los runs y resumen de fallos.

.DESCRIPTION
    Lee los phase-<fase>.json / done-<fase>.txt de build/c4n y saca:
      - una tabla por pantalla (run, fase, result, roster_invariantes_ok, presentes, duplicados,
        huerfanos, fundadores, job_site_presentes...),
      - un CSV en build/av/colector-<fecha>.csv (y build/av/colector.csv como "ultimo"),
      - un resumen de fallos por run/fase.

    Es tolerante: si falta un JSON, esta vacio o no parsea, la fila se emite con lo que haya y se marca
    la incidencia. NO ejecuta Minecraft y NO modifica build/c4n.

.PARAMETER Run
    Limita el colector a un run (nombre completo o sufijo, p. ej. 024).

.PARAMETER OutDir
    Carpeta de salida. Por defecto build/av.

.EXAMPLE
    .\tools\av\collect.ps1

.EXAMPLE
    .\tools\av\collect.ps1 -Run 024
#>
param(
    [string]$Run = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$C4nDir = Join-Path $Raiz 'build\c4n'
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path $Raiz 'build\av' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-Field {
    param($Objeto, [string]$Nombre, $Defecto = $null)
    if ($null -eq $Objeto) { return $Defecto }
    $prop = $Objeto.PSObject.Properties[$Nombre]
    if ($null -eq $prop -or $null -eq $prop.Value) { return $Defecto }
    return $prop.Value
}

function To-IntOrNull {
    param($Valor)
    if ($null -eq $Valor) { return $null }
    try { return [int]$Valor } catch { return $null }
}

function Read-JsonFile {
    param([string]$Ruta)
    if (-not (Test-Path -LiteralPath $Ruta -PathType Leaf)) {
        return [pscustomobject]@{ ok = $false; motivo = 'ausente'; data = $null }
    }
    try {
        $raw = Get-Content -LiteralPath $Ruta -Raw -ErrorAction Stop
        if ([string]::IsNullOrWhiteSpace($raw)) {
            return [pscustomobject]@{ ok = $false; motivo = 'vacio'; data = $null }
        }
        $data = $raw | ConvertFrom-Json -ErrorAction Stop
        return [pscustomobject]@{ ok = $true; motivo = ''; data = $data }
    } catch {
        return [pscustomobject]@{ ok = $false; motivo = "json_invalido: $($_.Exception.Message)"; data = $null }
    }
}

function Resolve-RunDir {
    param([string]$Nombre)
    if ([string]::IsNullOrWhiteSpace($Nombre)) { return $null }
    $directo = Join-Path $C4nDir $Nombre
    if (Test-Path -LiteralPath $directo -PathType Container) { return $directo }
    if ($Nombre -notmatch '^c4n-' -and (Test-Path -LiteralPath $C4nDir -PathType Container)) {
        $candidatos = @(Get-ChildItem -LiteralPath $C4nDir -Directory |
                Where-Object { $_.Name.EndsWith("-$Nombre") } | Sort-Object Name)
        if ($candidatos.Count -eq 1) { return $candidatos[0].FullName }
        if ($candidatos.Count -gt 1) {
            Write-Output "FALLO: '$Nombre' es ambiguo; candidatos: $(@($candidatos | ForEach-Object { $_.Name }) -join ', ')"
        }
    }
    return $null
}

# ------------------------------------------------------------------ runs a recorrer
$runDirs = @()
if (-not [string]::IsNullOrWhiteSpace($Run)) {
    $dir = Resolve-RunDir $Run
    if ($null -eq $dir) {
        Write-Output "FALLO: no existe el run '$Run' en $C4nDir"
        exit 1
    }
    $runDirs = @(Get-Item -LiteralPath $dir)
} else {
    if (-not (Test-Path -LiteralPath $C4nDir -PathType Container)) {
        Write-Output "FALLO: no existe $C4nDir"
        exit 1
    }
    $runDirs = @(Get-ChildItem -LiteralPath $C4nDir -Directory -Filter 'c4n-*' | Sort-Object Name)
}

# ------------------------------------------------------------------ tabla
$filas = New-Object System.Collections.Generic.List[object]
$incidencias = New-Object System.Collections.Generic.List[object]

foreach ($runDir in $runDirs) {
    $nombre = $runDir.Name
    $sha = 'desconocido'
    $shaPath = Join-Path $runDir.FullName 'sha.txt'
    if (Test-Path -LiteralPath $shaPath -PathType Leaf) {
        $sha = (Get-Content -LiteralPath $shaPath -Raw -ErrorAction SilentlyContinue)
        if (-not [string]::IsNullOrWhiteSpace([string]$sha)) { $sha = $sha.Trim() } else { $sha = 'desconocido' }
    }
    $fin = $null
    $finPath = Join-Path $runDir.FullName 'fin.txt'
    if (Test-Path -LiteralPath $finPath -PathType Leaf) {
        $fin = (Get-Content -LiteralPath $finPath -Raw -ErrorAction SilentlyContinue)
        if ($null -ne $fin) { $fin = $fin.Trim() }
    }

    foreach ($fase in @('prepare', 'reload1', 'reload2')) {
        $lectura = Read-JsonFile (Join-Path $runDir.FullName "phase-$fase.json")
        $data = if ($lectura.ok) { $lectura.data } else { $null }

        $result = Get-Field $data 'result'
        $origenResult = 'phase'
        if ([string]::IsNullOrWhiteSpace([string]$result)) {
            $donePath = Join-Path $runDir.FullName "done-$fase.txt"
            if (Test-Path -LiteralPath $donePath -PathType Leaf) {
                $result = (Get-Content -LiteralPath $donePath -Raw -ErrorAction SilentlyContinue)
                if ($null -ne $result) { $result = $result.Trim() }
                $origenResult = 'done'
            } else {
                $result = 'SIN_RESULTADO'
                $origenResult = 'ausente'
            }
        }

        $guardias = Get-Field $data 'guardias'
        $inv = Get-Field $data 'invariantes'
        $rosterOk = Get-Field $inv 'roster_invariantes_ok'
        if ($null -eq $rosterOk) { $rosterOk = Get-Field $data 'roster_invariantes_ok' }

        $fila = [pscustomobject][ordered]@{
            run                     = $nombre
            fase                    = $fase
            result                  = [string]$result
            result_origen           = $origenResult
            guardado_ok             = Get-Field $data 'guardado_ok'
            roster_invariantes_ok   = $rosterOk
            esperados               = To-IntOrNull (Get-Field $data 'esperados')
            presentes               = To-IntOrNull (Get-Field $data 'presentes')
            duplicados              = To-IntOrNull (Get-Field $data 'job_site_duplicados')
            huerfanos               = To-IntOrNull (Get-Field $data 'job_site_huerfanos')
            fundadores              = To-IntOrNull (Get-Field $guardias 'fundadores')
            job_site_presentes      = To-IntOrNull (Get-Field $data 'job_site_presentes')
            job_site_estables       = To-IntOrNull (Get-Field $data 'job_site_estables')
            job_site_cambiados      = To-IntOrNull (Get-Field $data 'job_site_cambiados')
            job_site_perdidos       = To-IntOrNull (Get-Field $data 'job_site_perdidos')
            job_site_nuevos         = To-IntOrNull (Get-Field $data 'job_site_nuevos')
            goals_instalados        = To-IntOrNull (Get-Field $data 'goals_instalados')
            reanudados              = To-IntOrNull (Get-Field $data 'reanudados')
            activos                 = To-IntOrNull (Get-Field $data 'activos')
            movidos                 = To-IntOrNull (Get-Field $data 'movidos')
            sha                     = $sha
            fin                     = $fin
            json_valido             = $lectura.ok
            json_motivo             = $lectura.motivo
        }
        $filas.Add($fila)

        if ($fila.result -ne 'PASS' -or -not $lectura.ok) {
            $incidencias.Add([pscustomobject][ordered]@{
                    run        = $nombre
                    fase       = $fase
                    result     = $fila.result
                    json       = if ($lectura.ok) { 'ok' } else { $lectura.motivo }
                    roster_ok  = $rosterOk
                    presentes  = $fila.presentes
                    duplicados = $fila.duplicados
                    huerfanos  = $fila.huerfanos
                    fin        = $fin
                })
        }
    }
}

if ($filas.Count -eq 0) {
    Write-Output "FALLO: no hay fases que colectar en $C4nDir"
    exit 1
}

# ------------------------------------------------------------------ CSV
$sello = Get-Date -Format 'yyyyMMdd-HHmmss'
$csvFecha = Join-Path $OutDir "colector-$sello.csv"
$csvUltimo = Join-Path $OutDir 'colector.csv'
$filas | Export-Csv -LiteralPath $csvFecha -NoTypeInformation -Encoding utf8
$filas | Export-Csv -LiteralPath $csvUltimo -NoTypeInformation -Encoding utf8

# ------------------------------------------------------------------ tabla por pantalla (anchos fijos: no se trunca el run)
function Format-Celda {
    param($Valor)
    if ($null -eq $Valor) { return '-' }
    if ($Valor -is [bool]) { if ($Valor) { return 'true' } else { return 'false' } }
    return [string]$Valor
}
Write-Output ("{0,-24} {1,-8} {2,-14} {3,-7} {4,-6} {5,-6} {6,-6} {7,-6} {8,-7} {9,-8}" -f
    'run', 'fase', 'result', 'roster', 'pres', 'dup', 'huer', 'fund', 'js_pres', 'js_estab')
Write-Output ("{0,-24} {1,-8} {2,-14} {3,-7} {4,-6} {5,-6} {6,-6} {7,-6} {8,-7} {9,-8}" -f
    ('-' * 24), ('-' * 8), ('-' * 14), ('-' * 7), ('-' * 6), ('-' * 6), ('-' * 6), ('-' * 6), ('-' * 7), ('-' * 8))
foreach ($f in $filas) {
    Write-Output ("{0,-24} {1,-8} {2,-14} {3,-7} {4,-6} {5,-6} {6,-6} {7,-6} {8,-7} {9,-8}" -f
        $f.run, $f.fase, $f.result, (Format-Celda $f.roster_invariantes_ok), (Format-Celda $f.presentes),
        (Format-Celda $f.duplicados), (Format-Celda $f.huerfanos), (Format-Celda $f.fundadores),
        (Format-Celda $f.job_site_presentes), (Format-Celda $f.job_site_estables))
}

Write-Output "CSV: $csvFecha"
Write-Output "CSV (ultimo): $csvUltimo"

# ------------------------------------------------------------------ resumen de fallos
$fallos = @($filas | Where-Object { $_.result -ne 'PASS' })
Write-Output ''
Write-Output "=== RESUMEN DE FALLOS (fases no PASS: $($fallos.Count)/$($filas.Count)) ==="
if ($fallos.Count -eq 0) {
    Write-Output '  Ninguna fase fallo.'
} else {
    $porResultado = $fallos | Group-Object result | Sort-Object Name
    foreach ($g in $porResultado) { Write-Output "  $($g.Name): $($g.Count)" }
    foreach ($f in $fallos) {
        $linea = "  - $($f.run) / $($f.fase): result=$($f.result)"
        if ($null -ne $f.presentes) { $linea += " presentes=$($f.presentes)" }
        if ($null -ne $f.duplicados) { $linea += " duplicados=$($f.duplicados)" }
        if ($null -ne $f.huerfanos) { $linea += " huerfanos=$($f.huerfanos)" }
        if ($null -ne $f.roster_invariantes_ok) { $linea += " roster_ok=$($f.roster_invariantes_ok)" }
        Write-Output $linea
        if (-not $f.json_valido) { Write-Output "      json: $($f.json_motivo)" }
        # Si diagnose.ps1 ya corrio, se enganchan sus causas sin volver a calcularlas.
        $diagPath = Join-Path $OutDir "diagnostico-$($f.run).json"
        if (Test-Path -LiteralPath $diagPath -PathType Leaf) {
            try {
                $diag = Get-Content -LiteralPath $diagPath -Raw | ConvertFrom-Json -ErrorAction Stop
                $faseDiag = @($diag.fases | Where-Object { $_.fase -eq $f.fase } | Select-Object -First 1)
                if ($faseDiag.Count -gt 0) {
                    foreach ($c in @($faseDiag[0].causa_conocida)) {
                        $codigo = Get-Field $c 'codigo' $c
                        Write-Output "      causa_conocida: $codigo"
                    }
                    foreach ($c in @($faseDiag[0].causa_desconocida)) {
                        $codigo = Get-Field $c 'codigo' $c
                        Write-Output "      causa_desconocida: $codigo"
                    }
                }
            } catch {
                Write-Output "      (diagnostico-$($f.run).json ilegible)"
            }
        }
    }
}

$sinEvidencia = @($filas | Where-Object { $_.result -eq 'SIN_RESULTADO' })
if ($sinEvidencia.Count -gt 0) {
    Write-Output ''
    Write-Output "=== SIN EVIDENCIA ($($sinEvidencia.Count) fase(s)) ==="
    foreach ($f in $sinEvidencia) { Write-Output "  - $($f.run) / $($f.fase)" }
}

exit 0
