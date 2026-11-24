<#
.SYNOPSIS
    Colector de evidencia C4N: tabla por fase de todos los runs y resumen de fallos.

.DESCRIPTION
    Lee los phase-<fase>.json / done-<fase>.txt de build/c4n y saca:
      - una tabla por pantalla (run, fase, result, aceptacion, roster, presentes, duplicados,
        huerfanos, fundadores, cobertura de estaciones...),
      - un CSV en build/av/colector-<fecha>.csv (y build/av/colector.csv como "ultimo"),
      - un resumen de fases no PASS (result != PASS o aceptacion != PASS) y de evidencia ausente.

    La aceptacion se evalua con el mismo modulo que diagnose.ps1 (tools/av/aceptacion.ps1). Una fase sin
    `aceptacion` medible NO se cuenta como PASS: se marca NO_MEDIDO o LEGACY.

    Es tolerante: si falta un JSON, esta vacio o no parsea, la fila se emite con lo que haya y se marca
    la incidencia. NO ejecuta Minecraft y NO modifica build/c4n.

.PARAMETER Run
    Limita el colector a un run (nombre completo o sufijo, p. ej. 024).

.PARAMETER OutDir
    Carpeta de salida. Por defecto build/av.

.PARAMETER C4nDir
    Carpeta de runs (por defecto build\c4n); util para fixtures de test.

.PARAMETER ExigirCobertura
    Convierte la cobertura 11/11 en criterio obligatorio.

.EXAMPLE
    .\tools\av\collect.ps1

.EXAMPLE
    .\tools\av\collect.ps1 -Run 1550

.EXAMPLE
    .\tools\av\collect.ps1 -Run c4n-20261123-1800 -ExigirCobertura
#>
param(
    [string]$Run = "",
    [string]$OutDir = "",
    [string]$C4nDir = "",
    [switch]$ExigirCobertura
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($C4nDir)) { $C4nDir = Join-Path $Raiz 'build\c4n' }
elseif (-not [System.IO.Path]::IsPathRooted($C4nDir)) { $C4nDir = Join-Path $Raiz $C4nDir }
$C4nDir = (Resolve-Path -LiteralPath $C4nDir -ErrorAction Stop).Path
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path $Raiz 'build\av' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
. (Join-Path $PSScriptRoot 'aceptacion.ps1')

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

        $aceptacion = Get-AvAceptacion -Data $data -Fase $fase -RunDir $runDir.FullName -ExigirCobertura:$ExigirCobertura
        $otrosFallos = @($aceptacion.criterios | Where-Object {
                $_.obligatorio -and $_.nombre -ne 'reconciliacion_manager_corrio' -and $_.ok -ne $true })
        $soloRecon = ($aceptacion.faltantes.Count -eq 1 -and $aceptacion.faltantes[0] -eq 'reconciliacion_manager_corrio' -and $otrosFallos.Count -eq 0)
        $aceptEstado = if ($aceptacion.ok) { 'PASS' } elseif ($soloRecon) { 'LEGACY' } elseif (-not $aceptacion.medido) { 'NO_MEDIDO' } else { 'FAIL' }
        $gDer = Get-AvGuardiasDerivado $guardias
        $cob = $aceptacion.cobertura
        $recon = $aceptacion.reconciliacion

        $fila = [pscustomobject][ordered]@{
            run                     = $nombre
            fase                    = $fase
            result                  = [string]$result
            result_origen           = $origenResult
            aceptacion_estado       = $aceptEstado
            aceptacion_ok           = $aceptacion.ok
            aceptacion_medida       = $aceptacion.medido
            aceptacion_origen       = $aceptacion.origen
            aceptacion_faltantes    = (@($aceptacion.faltantes) -join ';')
            ownership_demostrado    = $false
            cobertura_demostrada   = $aceptacion.coberturaDemostrada
            guardado_ok             = Get-Field $data 'guardado_ok'
            roster_invariantes_ok   = $rosterOk
            esperados               = To-IntOrNull (Get-Field $data 'esperados')
            presentes               = To-IntOrNull (Get-Field $data 'presentes')
            duplicados              = To-IntOrNull (Get-Field $data 'job_site_duplicados')
            huerfanos               = To-IntOrNull (Get-Field $data 'job_site_huerfanos')
            fundadores              = $gDer.fundadores
            mascara_bits            = $gDer.mascaraBits
            fundadores_con_goal     = $gDer.fundadoresConGoal
            fundadores_equipados    = $gDer.fundadoresEquipados
            cobertura_cubiertos     = To-IntOrNull $cob.cubiertos
            cobertura_esperados     = To-IntOrNull $cob.esperados
            reconciliacion_medida   = $recon.medido
            reconciliacion_efectiva = $recon.efectiva
            reclamaciones_estampa   = $recon.reclamaciones
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

        if ($fila.result -ne 'PASS' -or $aceptEstado -ne 'PASS' -or -not $lectura.ok) {
            $incidencias.Add([pscustomobject][ordered]@{
                    run        = $nombre
                    fase       = $fase
                    result     = $fila.result
                    aceptacion = $aceptEstado
                    faltan     = $fila.aceptacion_faltantes
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
$sello = Get-Date -Format 'yyyyMMdd-HHmmssfff'
$csvFecha = Join-Path $OutDir "colector-$sello.csv"
$n = 1
while (Test-Path -LiteralPath $csvFecha -PathType Leaf) {
    $csvFecha = Join-Path $OutDir "colector-$sello-$n.csv"
    $n++
}
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
Write-Output ("{0,-24} {1,-8} {2,-7} {3,-12} {4,-7} {5,-6} {6,-6} {7,-6} {8,-6} {9,-7}" -f
    'run', 'fase', 'result', 'acept', 'roster', 'pres', 'dup', 'huer', 'fund', 'cob')
Write-Output ("{0,-24} {1,-8} {2,-7} {3,-12} {4,-7} {5,-6} {6,-6} {7,-6} {8,-6} {9,-7}" -f
    ('-' * 24), ('-' * 8), ('-' * 7), ('-' * 12), ('-' * 7), ('-' * 6), ('-' * 6), ('-' * 6), ('-' * 6), ('-' * 7))
foreach ($f in $filas) {
    $cob = if ($null -ne $f.cobertura_cubiertos) { "$($f.cobertura_cubiertos)/$($f.cobertura_esperados)" } else { '-' }
    Write-Output ("{0,-24} {1,-8} {2,-7} {3,-12} {4,-7} {5,-6} {6,-6} {7,-6} {8,-6} {9,-7}" -f
        $f.run, $f.fase, $f.result, $f.aceptacion_estado, (Format-Celda $f.roster_invariantes_ok),
        (Format-Celda $f.presentes), (Format-Celda $f.duplicados), (Format-Celda $f.huerfanos),
        (Format-Celda $f.fundadores), $cob)
}

Write-Output "CSV: $csvFecha"
Write-Output "CSV (ultimo): $csvUltimo"

# ------------------------------------------------------------------ resumen de fallos
$fallos = @($filas | Where-Object { $_.result -ne 'PASS' -or $_.aceptacion_estado -ne 'PASS' })
Write-Output ''
Write-Output "=== RESUMEN DE FALLOS/NO-PASS (fases: $($fallos.Count)/$($filas.Count)) ==="
if ($fallos.Count -eq 0) {
    Write-Output '  Ninguna fase fallo y todas tienen aceptacion PASS.'
} else {
    $porResultado = $fallos | Group-Object aceptacion_estado | Sort-Object Name
    foreach ($g in $porResultado) { Write-Output "  aceptacion=$($g.Name): $($g.Count)" }
    foreach ($f in $fallos) {
        $linea = "  - $($f.run) / $($f.fase): result=$($f.result) aceptacion=$($f.aceptacion_estado)"
        if ($null -ne $f.faltan -and $f.faltan -ne '') { $linea += " faltan=$($f.faltan)" }
        if ($null -ne $f.presentes) { $linea += " presentes=$($f.presentes)" }
        if ($null -ne $f.duplicados) { $linea += " duplicados=$($f.duplicados)" }
        if ($null -ne $f.huerfanos) { $linea += " huerfanos=$($f.huerfanos)" }
        if ($null -ne $f.roster_invariantes_ok) { $linea += " roster_ok=$($f.roster_invariantes_ok)" }
        if ($null -ne $f.cobertura_cubiertos) { $linea += " cobertura=$($f.cobertura_cubiertos)/$($f.cobertura_esperados)" }
        Write-Output $linea
        if (-not $f.json_valido) { Write-Output "      json: $($f.json_motivo)" }
        # Si diagnose.ps1 ya corrio, se enganchan sus causas sin volver a calcularlas.
        $diagPath = Join-Path $OutDir "diagnostico-$($f.run).json"
        if (Test-Path -LiteralPath $diagPath -PathType Leaf) {
            try {
                $diag = Get-Content -LiteralPath $diagPath -Raw | ConvertFrom-Json -ErrorAction Stop
                if ([string](Get-Field $diag 'run' '') -ne $f.run) {
                    Write-Output "      (diagnostico-$($f.run).json es de otro run: se ignora)"
                    continue
                }
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
