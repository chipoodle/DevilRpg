<#
.SYNOPSIS
    Agrega las métricas REALES de navegación (índice del arnés + volcados literales de
    NavPlannerMetrics) y, opcionalmente, la línea base sintética y el perfil A* sintético.

.DESCRIPTION
    Lee, sin inventar ni rellenar huecos:
      - `nav-escenarios-*.csv`  índice del arnés de navegación (contrato v1 acordado con `navegacion`).
      - `metrics-*.csv`         volcados LITERALES de `NavPlannerMetrics.dumpCsv()`.
      - `nav-baseline-*.txt`    líneas `BASELINE,...` de `NavSearchBaselineTest` (si se pide).
      - `PERF_RESUMEN,...`      perfil sintético del A* (si se pide).

    Reglas:
      * Una fila con `motivo=no_soportado_*` o `metrics_csv=-` y todo a cero NO es una medida: se
        lista en `NO_MEDIDO` y no entra en medias, sumas ni percentiles.
      * Si hay volcado literal, mandan sus filas `BUSQUEDA`/`ENTIDAD`; el índice solo aporta
        `nanos_pared`, `ticks`, `candidatos` y `encolados` (que el planner no pone en el volcado).
      * Si el índice y el volcado discrepan en expansiones/nanos (>1% y >5 unidades), se emite
        `AVISO_DISCREPANCIA` con los dos números. No se corrige nada en silencio.
      * `nanos_pared` es COTA SUPERIOR (preparación + ejecución), nunca "coste del planner".
      * No hay umbrales de tiempo que "aprueben": los números se imprimen.

.PARAMETER InputDir
    Carpeta a explorar. Por defecto `build/av` (incluye subcarpetas `nav-<run>`).

.PARAMETER Files
    Ficheros concretos (índices, volcados, baseline o logs PERF). Si se indica, no se auto-descubre.

.PARAMETER OutDir
    Carpeta de salida. Por defecto `build/av`.

.PARAMETER Tag
    Sufijo de los ficheros de salida. Por defecto `yyyyMMdd-HHmmss`.

.PARAMETER IncluirBaseline
    Añade las líneas `BASELINE,...` de `nav-baseline-*.txt` (por defecto busca `nav-baseline-ultimo.txt`).

.PARAMETER IncluirPerf
    Añade `PERF_RESUMEN,...` de `build/av/perf/*.txt` (perfil sintético antes/después).

.PARAMETER IncluirFixture
    Incluye rutas que contienen `fixture` (por defecto se excluyen: un fixture no es evidencia).

.PARAMETER Esperados
    Categorías de escenario que deben aparecer. Se comparan por sinónimos.

.EXAMPLE
    .\tools\perf\resumir-metricas.ps1

.EXAMPLE
    .\tools\perf\resumir-metricas.ps1 -IncluirPerf -IncluirBaseline
#>
[CmdletBinding()]
param(
    [string]$InputDir = "",
    [string[]]$Files = @(),
    [string]$OutDir = "",
    [string]$Tag = "",
    [switch]$IncluirBaseline,
    [switch]$IncluirPerf,
    [switch]$IncluirFixture,
    [switch]$SinConsola,
    [string[]]$Esperados = @(
        'campo_abierto', 'obstaculos', 'muro', 'laberinto', 'drop', 'break', 'build',
        'combinado', 'inaccesible', 'presupuesto_insuficiente',
        'multientidad_1', 'multientidad_2', 'multientidad_4', 'multientidad_8'
    )
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($InputDir)) { $InputDir = Join-Path $Raiz 'build\av' }
if (-not (Test-Path -LiteralPath $InputDir -PathType Container)) {
    throw "No existe InputDir: $InputDir"
}
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path $Raiz 'build\av' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if ([string]::IsNullOrWhiteSpace($Tag)) { $Tag = Get-Date -Format 'yyyyMMdd-HHmmss' }

$Avisos = New-Object System.Collections.Generic.List[string]
$NoMedidos = New-Object System.Collections.Generic.List[object]
$Registros = New-Object System.Collections.Generic.List[object]
$MetaEscenarioRep = @{}
$Dumps = @{}
$Fuentes = New-Object System.Collections.Generic.List[string]

# ================================================================================================
# Utilidades de conversión y estadística.
# ================================================================================================

function Convertir-Long($valor) {
    if ($null -eq $valor) { return $null }
    $texto = ([string]$valor).Trim()
    if ($texto -eq '' -or $texto -eq '-') { return $null }
    $numero = 0L
    if ([long]::TryParse($texto, [ref]$numero)) { return $numero }
    return $null
}

function Convertir-Bool($valor) {
    $texto = ([string]$valor).Trim().ToLowerInvariant()
    return ($texto -eq 'true' -or $texto -eq '1')
}

function Convertir-Decimal($valor) {
    if ($null -eq $valor) { return $null }
    $texto = ([string]$valor).Trim()
    if ($texto -eq '' -or $texto -eq '-') { return $null }
    $numero = 0.0
    if ([double]::TryParse($texto, [Globalization.NumberStyles]::Float,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$numero)) { return $numero }
    return $null
}

function Mediana([object[]]$valores) {
    $limpios = @($valores | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($limpios.Count -eq 0) { return $null }
    $medio = [int][Math]::Floor($limpios.Count / 2)
    if (($limpios.Count % 2) -eq 1) { return $limpios[$medio] }
    return ($limpios[$medio - 1] + $limpios[$medio]) / 2.0
}

function Percentil([object[]]$valores, [int]$p) {
    $limpios = @($valores | Where-Object { $null -ne $_ } | ForEach-Object { [double]$_ } | Sort-Object)
    if ($limpios.Count -eq 0) { return $null }
    $indice = [int][Math]::Ceiling($p / 100.0 * $limpios.Count) - 1
    if ($indice -lt 0) { $indice = 0 }
    if ($indice -ge $limpios.Count) { $indice = $limpios.Count - 1 }
    return $limpios[$indice]
}

function Formato-Numero($valor, [int]$decimales = 1) {
    if ($null -eq $valor) { return '-' }
    return ([double]$valor).ToString("N$decimales", [Globalization.CultureInfo]::InvariantCulture)
}

function Formato-Entero($valor) {
    if ($null -eq $valor) { return '-' }
    return ([long]$valor).ToString('N0', [Globalization.CultureInfo]::InvariantCulture)
}

function Es-FicheroFixture([string]$ruta) {
    return ($ruta -match '(?i)fixture')
}

function Ruta-Relativa([string]$ruta) {
    $completa = [IO.Path]::GetFullPath($ruta)
    if ($completa.StartsWith($Raiz, [StringComparison]::OrdinalIgnoreCase)) {
        return $completa.Substring($Raiz.Length).TrimStart('\', '/')
    }
    return $completa
}

function Leer-Lineas([string]$ruta) {
    return @(Get-Content -LiteralPath $ruta -Encoding utf8)
}

# ================================================================================================
# 1. Volcados literales de NavPlannerMetrics.
# ================================================================================================

function Leer-Volcado([string]$ruta) {
    $lineas = Leer-Lineas $ruta
    $totales = $null
    $busquedas = New-Object System.Collections.Generic.List[object]
    $entidades = New-Object System.Collections.Generic.List[object]
    $seccion = ''
    foreach ($linea in $lineas) {
        if ([string]::IsNullOrWhiteSpace($linea)) { continue }
        $partes = $linea.Split(',')
        switch ($partes[0]) {
            'METRICS' {
                if ($partes.Count -ge 12 -and $null -ne (Convertir-Long $partes[1])) {
                    $totales = [pscustomobject]@{
                        Busquedas        = Convertir-Long $partes[1]
                        Completadas      = Convertir-Long $partes[2]
                        Fallidas         = Convertir-Long $partes[3]
                        Canceladas       = Convertir-Long $partes[4]
                        Expansiones      = Convertir-Long $partes[5]
                        Nanos            = Convertir-Long $partes[6]
                        PresupuestoAgot  = Convertir-Long $partes[7]
                        Replanificaciones= Convertir-Long $partes[8]
                        Reanudadas       = Convertir-Long $partes[9]
                        Entidades        = Convertir-Long $partes[10]
                    }
                }
                $seccion = 'METRICS'
            }
            'BUSQUEDA' {
                if ($partes[1] -eq 'id') { $seccion = 'BUSQUEDA'; continue }
                if ($partes.Count -lt 18) { continue }
                $busquedas.Add([pscustomobject]@{
                    Id                 = Convertir-Long $partes[1]
                    Tipo               = $partes[2]
                    Entidad            = $partes[3]
                    TickInicio         = Convertir-Long $partes[4]
                    Nanos              = Convertir-Long $partes[5]
                    Expansiones        = Convertir-Long $partes[6]
                    Presupuesto        = Convertir-Long $partes[7]
                    Avances            = Convertir-Long $partes[8]
                    NodosPorAvance     = Convertir-Long $partes[9]
                    UltimoSlice        = Convertir-Long $partes[10]
                    Reanudada          = Convertir-Bool $partes[11]
                    Replanificacion    = Convertir-Bool $partes[12]
                    Fallida            = Convertir-Bool $partes[13]
                    PresupuestoAgotado = Convertir-Bool $partes[14]
                    LongitudRuta       = Convertir-Long $partes[15]
                    Estado             = $partes[16]
                    Motivo             = ($partes[17..($partes.Count - 1)] -join ',')
                })
                $seccion = 'BUSQUEDA'
            }
            'ENTIDAD' {
                if ($partes[1] -eq 'entidad') { $seccion = 'ENTIDAD'; continue }
                if ($partes.Count -lt 14) { continue }
                $entidades.Add([pscustomobject]@{
                    Entidad             = $partes[1]
                    Busquedas           = Convertir-Long $partes[2]
                    Expansiones         = Convertir-Long $partes[3]
                    Nanos               = Convertir-Long $partes[4]
                    Fallos              = Convertir-Long $partes[5]
                    PresupuestoAgotado  = Convertir-Long $partes[6]
                    Replanificaciones   = Convertir-Long $partes[7]
                    Canceladas          = Convertir-Long $partes[8]
                    UltimaBusqueda      = Convertir-Long $partes[9]
                    UltimoTipo          = $partes[10]
                    UltimoTick          = Convertir-Long $partes[11]
                    UltimoMotivo        = $partes[12]
                    UltimaLongitudRuta  = Convertir-Long $partes[13]
                })
                $seccion = 'ENTIDAD'
            }
            default { }
        }
    }
    return [pscustomobject]@{
        Ruta      = ((Ruta-Relativa $ruta) -replace '\\', '/')
        Totales   = $totales
        Busquedas = $busquedas
        Entidades = $entidades
    }
}

# ================================================================================================
# 2. Índice del arnés de navegación (contrato v1).
# ================================================================================================

function Leer-Indice([string]$ruta) {
    # Validación de forma ANTES de Import-Csv: una fila con menos columnas desplaza el resto
    # y produciría métricas atribuidas a la columna equivocada. Mejor fallar que inventar.
    $lineas = @(Leer-Lineas $ruta | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($lineas.Count -eq 0) { return @() }
    $cabecera = $lineas[0].Split(',').Count
    for ($i = 1; $i -lt $lineas.Count; $i++) {
        $campos = $lineas[$i].Split(',').Count
        if ($campos -ne $cabecera) {
            throw "Índice malformado en $ruta línea $($i + 1): $campos campos, cabecera $cabecera. " +
                  "Revisa el contrato (coma, sin comas dentro de valores)."
        }
    }
    $filas = @(Import-Csv -LiteralPath $ruta -Encoding utf8)
    if ($filas.Count -eq 0) { return @() }
    $columnas = $filas[0].PSObject.Properties.Name
    foreach ($obligatoria in @('escenario', 'repeticion', 'entidad', 'metrics_csv', 'motivo')) {
        if ($columnas -notcontains $obligatoria) {
            throw "El índice $ruta no tiene la columna obligatoria '$obligatoria'. Columnas: $($columnas -join ', ')"
        }
    }
    return $filas
}

# ================================================================================================
# 3. Línea base sintética y perfil A*.
# ================================================================================================

function Leer-Baseline([string]$ruta) {
    foreach ($linea in (Leer-Lineas $ruta)) {
        if (-not $linea.StartsWith('BASELINE,')) { continue }
        $p = $linea.Split(',')
        if ($p.Count -lt 15 -or $p[1] -eq 'escenario') { continue }
        $Registros.Add([pscustomobject]@{
            Fuente               = 'BASELINE'
            Escenario            = $p[1]
            Repeticion           = Convertir-Long $p[2]
            Subtipo              = $p[3]
            Entidad              = '-'
            TipoBusqueda         = $p[3]
            Presupuesto          = Convertir-Long $p[4]
            Expansiones          = Convertir-Long $p[5]
            Nanos                = Convertir-Long $p[13]
            Avances              = $null
            NodosPorTick         = $null
            Reanudada            = $false
            Replanificacion      = $false
            Fallida              = -not (Convertir-Bool $p[10])
            PresupuestoAgotado   = Convertir-Bool $p[11]
            LongitudRuta         = Convertir-Long $p[12]
            Encontrado           = Convertir-Bool $p[10]
            Abierta             = $false
            Motivo               = ($p[14..($p.Count - 1)] -join ',')
            Candidatos           = $null
            Encolados            = $null
            NanosPared           = $null
            Ticks                = $null
            MetricsCsv           = (Ruta-Relativa $ruta)
            Pops                 = Convertir-Long $p[6]
            StalePops            = Convertir-Long $p[7]
            Reopens              = Convertir-Long $p[8]
            Frontera             = Convertir-Long $p[9]
        })
    }
}

function Leer-Perf([string]$ruta) {
    $nombre = Split-Path -Leaf $ruta
    $variante = if ($nombre -match '^antes') { 'antes' } elseif ($nombre -match '^despues') { 'despues' } else { 'otro' }
    foreach ($linea in (Leer-Lineas $ruta)) {
        if (-not $linea.StartsWith('PERF_RESUMEN,')) { continue }
        $p = $linea.Split(',')
        if ($p.Count -lt 15 -or $p[1] -eq 'escenario') { continue }
        $medianas = [pscustomobject]@{
            Fuente        = 'PERF_SINTETICO'
            Escenario     = $p[1]
            Repeticion    = (Ruta-Relativa $ruta)
            Subtipo       = "$($p[2])/$variante"
            Entidad       = '-'
            TipoBusqueda  = $p[2]
            Presupuesto   = $null
            Expansiones   = Convertir-Long $p[7]
            Nanos         = Convertir-Long $p[5]
            Avances       = Convertir-Long $p[14]
            NodosPorTick  = Convertir-Decimal $p[15]
            Reanudada     = $false
            Replanificacion = $false
            Fallida       = $false
            PresupuestoAgotado = $false
            LongitudRuta  = $null
            Encontrado    = $null
            Abierta       = $false
            Motivo        = "reps=$($p[3]);min=$($p[4]);mediana=$($p[5]);p95=$($p[6]);ns_exp=$($p[8]);archivo=$(Split-Path -Leaf $ruta)"
            Candidatos    = $null
            Encolados     = $null
            NanosPared    = $null
            Ticks         = $null
            MetricsCsv    = (Ruta-Relativa $ruta)
            Pops          = Convertir-Long $p[9]
            StalePops     = Convertir-Long $p[10]
            Reopens       = Convertir-Long $p[11]
            Frontera      = Convertir-Long $p[12]
            PerfMin       = Convertir-Long $p[4]
            PerfMediana   = Convertir-Long $p[5]
            PerfP95       = Convertir-Long $p[6]
        }
        $Registros.Add($medianas)
    }
}

# ================================================================================================
# 4. Descubrimiento de ficheros.
# ================================================================================================

$rutasIndices = New-Object System.Collections.Generic.List[string]
$rutasDumps = New-Object System.Collections.Generic.List[string]
$rutasBaseline = New-Object System.Collections.Generic.List[string]
$rutasPerf = New-Object System.Collections.Generic.List[string]

if ($Files.Count -gt 0) {
    foreach ($f in $Files) {
        if (-not (Test-Path -LiteralPath $f -PathType Leaf)) { throw "No existe el fichero: $f" }
        $nombre = Split-Path -Leaf $f
        if ($nombre -match '^nav-escenarios.*\.csv$' -or $nombre -match '^indice.*\.csv$') { $rutasIndices.Add((Resolve-Path $f).Path) }
        elseif ($nombre -match '^metrics.*\.csv$') { $rutasDumps.Add((Resolve-Path $f).Path) }
        elseif ($nombre -match '^nav-baseline.*\.txt$') { $rutasBaseline.Add((Resolve-Path $f).Path) }
        elseif ($nombre -match '\.txt$') { $rutasPerf.Add((Resolve-Path $f).Path) }
        else { throw "No sé clasificar el fichero $f (esperaba nav-escenarios*.csv, metrics*.csv, nav-baseline*.txt o un log PERF)" }
    }
} else {
    $candidatos = @(Get-ChildItem -LiteralPath $InputDir -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $IncluirFixture -or -not (Es-FicheroFixture $_.FullName) })
    # Un `nav-escenarios-<ts>.csv` y su copia `nav-escenarios-ultimo.csv` son el MISMO índice:
    # si existe la copia estable se usa solo esa, para no duplicar repeticiones.
    $porCarpeta = @($candidatos | Where-Object { $_.Name -match '^(nav-escenarios.*|indice.*)\.csv$' } |
            Group-Object DirectoryName)
    foreach ($carpeta in $porCarpeta) {
        $ultimo = @($carpeta.Group | Where-Object { $_.Name -eq 'nav-escenarios-ultimo.csv' })
        if ($ultimo.Count -gt 0) {
            $rutasIndices.Add($ultimo[0].FullName)
        } else {
            foreach ($archivo in $carpeta.Group) { $rutasIndices.Add($archivo.FullName) }
        }
    }
    foreach ($archivo in @($candidatos | Where-Object { $_.Name -match '^metrics.*\.csv$' })) {
        $rutasDumps.Add($archivo.FullName)
    }
    $baselines = @($candidatos | Where-Object { $_.Name -match '^nav-baseline.*\.txt$' } |
            Sort-Object LastWriteTime)
    if ($baselines.Count -gt 0) {
        $ultimo = @($baselines | Where-Object { $_.Name -eq 'nav-baseline-ultimo.txt' })
        if ($ultimo.Count -gt 0) { $rutasBaseline.Add($ultimo[0].FullName) }
        else { $rutasBaseline.Add($baselines[$baselines.Count - 1].FullName) }
    }
    if ($IncluirPerf) {
        $perfDir = Join-Path $Raiz 'build\av\perf'
        if (Test-Path -LiteralPath $perfDir -PathType Container) {
            # Por defecto solo los pares canónicos `*-final-*.txt` (misma metodología).
            # Los logs intermedios de experimentos se pueden pasar con -Files.
            $perfFiles = @(Get-ChildItem -LiteralPath $perfDir -File -Filter '*-final-*.txt' -ErrorAction SilentlyContinue)
            if ($perfFiles.Count -eq 0) {
                $perfFiles = @(Get-ChildItem -LiteralPath $perfDir -File -Filter '*.txt' -ErrorAction SilentlyContinue)
            }
            foreach ($archivo in $perfFiles) {
                if (-not (Es-FicheroFixture $archivo.FullName)) { $rutasPerf.Add($archivo.FullName) }
            }
        }
    }
}

foreach ($ruta in $rutasIndices) { $Fuentes.Add("INDICE  $(Ruta-Relativa $ruta)") }
foreach ($ruta in $rutasDumps) { $Fuentes.Add("DUMP    $(Ruta-Relativa $ruta)") }
if ($IncluirBaseline) { foreach ($ruta in $rutasBaseline) { $Fuentes.Add("BASELINE $(Ruta-Relativa $ruta)") } }
if ($IncluirPerf) { foreach ($ruta in $rutasPerf) { $Fuentes.Add("PERF    $(Ruta-Relativa $ruta)") } }

# ================================================================================================
# 5. Carga de volcados e índices.
# ================================================================================================

foreach ($ruta in $rutasDumps) {
    $volcado = Leer-Volcado $ruta
    $Dumps[((Ruta-Relativa $ruta) -replace '\\', '/')] = $volcado
    $Dumps[[IO.Path]::GetFileName($ruta)] = $volcado
}

foreach ($ruta in $rutasIndices) {
    $filas = Leer-Indice $ruta
    $porEscenarioRep = @{}
    foreach ($fila in $filas) {
        $medido = $true
        $motivo = [string]$fila.motivo
        if ($motivo -match '^no_soportado') { $medido = $false }
        if ([string]$fila.metrics_csv -eq '-' -and
            ($null -eq (Convertir-Long $fila.expansiones)) -and
            ($null -eq (Convertir-Long $fila.nanos_metricas))) { $medido = $false }
        if (-not $medido) {
            $NoMedidos.Add([pscustomobject]@{
                Origen    = (Ruta-Relativa $ruta)
                Escenario = $fila.escenario
                Repeticion= $fila.repeticion
                Entidad   = $fila.entidad
                Motivo    = $motivo
            })
            continue
        }
        $clave = "$($fila.escenario)|$($fila.repeticion)"
        if (-not $porEscenarioRep.ContainsKey($clave)) {
            $porEscenarioRep[$clave] = [pscustomobject]@{
                Escenario  = [string]$fila.escenario
                Repeticion = Convertir-Long $fila.repeticion
                Tipo       = [string]$fila.tipo_busqueda
                Presupuesto= Convertir-Long $fila.presupuesto
                Expansiones= 0L
                Nanos      = 0L
                Entidades  = New-Object System.Collections.Generic.List[string]
                NanosPared = $null
                Ticks      = $null
                Candidatos = $null
                Encolados  = $null
                Motivo     = $motivo
                MetricsCsv = [string]$fila.metrics_csv
                Filas      = New-Object System.Collections.Generic.List[object]
            }
        }
        $grupo = $porEscenarioRep[$clave]
        $grupo.Expansiones += [long]((Convertir-Long $fila.expansiones) | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } })
        $grupo.Nanos += [long]((Convertir-Long $fila.nanos_metricas) | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } })
        if ([string]$fila.entidad -ne '-') { $grupo.Entidades.Add([string]$fila.entidad) }
        $np = Convertir-Long $fila.nanos_pared
        if ($null -ne $np -and ($null -eq $grupo.NanosPared -or $np -gt $grupo.NanosPared)) { $grupo.NanosPared = $np }
        $tk = Convertir-Long $fila.ticks
        if ($null -ne $tk -and ($null -eq $grupo.Ticks -or $tk -gt $grupo.Ticks)) { $grupo.Ticks = $tk }
        $cand = Convertir-Long $fila.candidatos
        if ($null -ne $cand) { $grupo.Candidatos = $cand }
        $enc = Convertir-Long $fila.encolados
        if ($null -ne $enc) { $grupo.Encolados = $enc }
        $grupo.Filas.Add($fila)
    }

    foreach ($clave in $porEscenarioRep.Keys) {
        $grupo = $porEscenarioRep[$clave]
        $volcado = $null
        if ($grupo.MetricsCsv -ne '-' -and $grupo.MetricsCsv -ne '') {
            $rel = ($grupo.MetricsCsv -replace '\\', '/')
            $rutaAbs = Join-Path $Raiz ($rel -replace '/', '\')
            if (-not (Test-Path -LiteralPath $rutaAbs -PathType Leaf)) {
                $nombre = Split-Path -Leaf $rel
                if ($Dumps.ContainsKey($nombre)) { $volcado = $Dumps[$nombre] }
                else { $Avisos.Add("AVISO_METRICS_CSV_NO_ENCONTRADO,$($grupo.Escenario),rep$($grupo.Repeticion),$($grupo.MetricsCsv)") }
            } elseif ($Dumps.ContainsKey($rel)) {
                $volcado = $Dumps[$rel]
            } else {
                $volcado = Leer-Volcado $rutaAbs
                $Dumps[$rel] = $volcado
                $Dumps[[IO.Path]::GetFileName($rutaAbs)] = $volcado
            }
        }
        if ($null -ne $volcado -and $volcado.Busquedas.Count -gt 0) {
            $sumaExp = 0L
            $sumaNanos = 0L
            foreach ($b in $volcado.Busquedas) {
                $sumaExp += [long]($b.Expansiones | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } })
                $sumaNanos += [long]($b.Nanos | ForEach-Object { if ($null -eq $_) { 0 } else { $_ } })
                $Registros.Add([pscustomobject]@{
                    Fuente             = 'RUNTIME'
                    Escenario          = $grupo.Escenario
                    Repeticion         = $grupo.Repeticion
                    Subtipo            = $b.Tipo
                    Entidad            = $b.Entidad
                    TipoBusqueda       = $b.Tipo
                    Presupuesto        = $b.Presupuesto
                    Expansiones        = $b.Expansiones
                    Nanos              = $b.Nanos
                    Avances            = $b.Avances
                    NodosPorTick       = $b.NodosPorAvance
                    Reanudada          = $b.Reanudada
                    Replanificacion    = $b.Replanificacion
                    Fallida            = $b.Fallida
                    PresupuestoAgotado = $b.PresupuestoAgotado
                    LongitudRuta       = $b.LongitudRuta
                    Encontrado         = -not $b.Fallida -and $b.Estado -eq 'COMPLETADA'
                    Abierta           = $b.Estado -eq 'ABIERTA'
                    Motivo             = $b.Motivo
                    Candidatos         = $grupo.Candidatos
                    Encolados          = $grupo.Encolados
                    NanosPared         = $grupo.NanosPared
                    Ticks              = $grupo.Ticks
                    MetricsCsv         = $volcado.Ruta
                    Pops               = $null
                    StalePops          = $null
                    Reopens            = $null
                    Frontera           = $null
                })
            }
            if ($null -ne $grupo.Expansiones -and $grupo.Expansiones -gt 0) {
                $desvio = [Math]::Abs($sumaExp - $grupo.Expansiones)
                if ($desvio -gt 5 -and $desvio -gt 0.01 * $grupo.Expansiones) {
                    $Avisos.Add("AVISO_DISCREPANCIA,$($grupo.Escenario),rep$($grupo.Repeticion),exp_indice=$($grupo.Expansiones),exp_dump=$sumaExp")
                }
            }
            if ($null -ne $grupo.Nanos -and $grupo.Nanos -gt 0) {
                $desvio = [Math]::Abs($sumaNanos - $grupo.Nanos)
                if ($desvio -gt 5000 -and $desvio -gt 0.01 * $grupo.Nanos) {
                    $Avisos.Add("AVISO_DISCREPANCIA,$($grupo.Escenario),rep$($grupo.Repeticion),nanos_indice=$($grupo.Nanos),nanos_dump=$sumaNanos")
                }
            }
        } else {
            # Sin volcado: el índice es la única fuente. Una fila por entidad/rep.
            foreach ($fila in $grupo.Filas) {
                $Registros.Add([pscustomobject]@{
                    Fuente             = 'RUNTIME'
                    Escenario          = $grupo.Escenario
                    Repeticion         = $grupo.Repeticion
                    Subtipo            = $grupo.Tipo
                    Entidad            = [string]$fila.entidad
                    TipoBusqueda       = $grupo.Tipo
                    Presupuesto        = Convertir-Long $fila.presupuesto
                    Expansiones        = Convertir-Long $fila.expansiones
                    Nanos              = Convertir-Long $fila.nanos_metricas
                    Avances            = Convertir-Long $fila.avances
                    NodosPorTick       = $null
                    Reanudada          = $false
                    Replanificacion    = $false
                    Fallida            = -not (Convertir-Bool $fila.encontrado)
                    PresupuestoAgotado = Convertir-Bool $fila.presupuesto_agotado
                    LongitudRuta       = Convertir-Long $fila.longitud_ruta
                    Encontrado         = Convertir-Bool $fila.encontrado
                    Abierta           = $false
                    Motivo             = $motivo
                    Candidatos         = $grupo.Candidatos
                    Encolados          = $grupo.Encolados
                    NanosPared         = $grupo.NanosPared
                    Ticks              = $grupo.Ticks
                    MetricsCsv         = $grupo.MetricsCsv
                    Pops               = $null
                    StalePops          = $null
                    Reopens            = $null
                    Frontera           = $null
                })
            }
        }
    }
}

# Volcados sin índice (p. ej. `metrics-<fase>.txt` de C4): se agrupan por nombre de fichero.
$referenciados = @($Registros | Where-Object { $_.MetricsCsv } | ForEach-Object { $_.MetricsCsv } | Sort-Object -Unique)
$volcadosUnicos = @($Dumps.Values | Sort-Object Ruta -Unique)
foreach ($volcado in $volcadosUnicos) {
    if ($referenciados -contains $volcado.Ruta) { continue }
    if ($volcado.Busquedas.Count -eq 0) { continue }
    $nombre = [IO.Path]::GetFileNameWithoutExtension($volcado.Ruta)
    $escenario = $nombre
    $rep = $null
    if ($nombre -match '^metrics-(.+)-r(\d+)$') { $escenario = $Matches[1]; $rep = Convertir-Long $Matches[2] }
    elseif ($nombre -match '^metrics-(.+)$') { $escenario = $Matches[1] }
    foreach ($b in $volcado.Busquedas) {
        $Registros.Add([pscustomobject]@{
            Fuente             = 'DUMP'
            Escenario          = $escenario
            Repeticion         = $rep
            Subtipo            = $b.Tipo
            Entidad            = $b.Entidad
            TipoBusqueda       = $b.Tipo
            Presupuesto        = $b.Presupuesto
            Expansiones        = $b.Expansiones
            Nanos              = $b.Nanos
            Avances            = $b.Avances
            NodosPorTick       = $b.NodosPorAvance
            Reanudada          = $b.Reanudada
            Replanificacion    = $b.Replanificacion
            Fallida            = $b.Fallida
            PresupuestoAgotado = $b.PresupuestoAgotado
            LongitudRuta       = $b.LongitudRuta
            Encontrado         = $b.Estado -eq 'COMPLETADA'
            Abierta           = $b.Estado -eq 'ABIERTA'
            Motivo             = $b.Motivo
            Candidatos         = $null
            Encolados          = $null
            NanosPared         = $null
            Ticks              = $null
            MetricsCsv         = $volcado.Ruta
            Pops               = $null
            StalePops          = $null
            Reopens            = $null
            Frontera           = $null
        })
    }
}

if ($IncluirBaseline) { foreach ($ruta in $rutasBaseline) { Leer-Baseline $ruta } }
if ($IncluirPerf) { foreach ($ruta in $rutasPerf) { Leer-Perf $ruta } }

# ================================================================================================
# 6. Agregados por fuente + escenario + subtipo.
# ================================================================================================

$grupos = @($Registros | Group-Object { "$($_.Fuente)|$($_.Escenario)|$($_.Subtipo)" } | Sort-Object Name)
$FilasAgregadas = New-Object System.Collections.Generic.List[object]

foreach ($grupo in $grupos) {
    $items = @($grupo.Group)
    $nanos = @($items | ForEach-Object { $_.Nanos } | Where-Object { $null -ne $_ })
    $expansiones = @($items | ForEach-Object { $_.Expansiones } | Where-Object { $null -ne $_ })
    $avances = @($items | ForEach-Object { $_.Avances } | Where-Object { $null -ne $_ })
    $presupuestos = @($items | Where-Object { $null -ne $_.Presupuesto -and $_.Presupuesto -gt 0 } | ForEach-Object { $_.Presupuesto })
    $expTotal = if ($expansiones.Count -gt 0) { ($expansiones | Measure-Object -Sum).Sum } else { $null }
    $nanosTotal = if ($nanos.Count -gt 0) { ($nanos | Measure-Object -Sum).Sum } else { $null }
    $avancesTotal = if ($avances.Count -gt 0) { ($avances | Measure-Object -Sum).Sum } else { $null }
    $presupuestoTotal = if ($presupuestos.Count -gt 0) { ($presupuestos | Measure-Object -Sum).Sum } else { $null }
    $entidades = @($items | Where-Object { $_.Entidad -and $_.Entidad -ne '-' } | ForEach-Object { $_.Entidad } | Sort-Object -Unique)
    $reanudadas = @($items | Where-Object { $_.Reanudada }).Count
    $busquedas = $items.Count
    $fallos = @($items | Where-Object { $_.Fallida }).Count
    $agotados = @($items | Where-Object { $_.PresupuestoAgotado }).Count
    $replan = @($items | Where-Object { $_.Replanificacion }).Count
    $abiertas = @($items | Where-Object { $_.Abierta }).Count
    $umbral1 = @($nanos | Where-Object { $_ -lt 1000000 }).Count
    $umbral10 = @($nanos | Where-Object { $_ -ge 1000000 -and $_ -lt 10000000 }).Count
    $umbral100 = @($nanos | Where-Object { $_ -ge 10000000 -and $_ -lt 100000000 }).Count
    $umbralMas = @($nanos | Where-Object { $_ -ge 100000000 }).Count
    $FilasAgregadas.Add([pscustomobject]@{
        Fuente             = $items[0].Fuente
        Escenario          = $items[0].Escenario
        Subtipo            = $items[0].Subtipo
        Registros          = $busquedas
        Repeticiones       = @($items | ForEach-Object { $_.Repeticion } | Where-Object { $null -ne $_ } | Sort-Object -Unique).Count
        Entidades          = $entidades.Count
        Busquedas          = $busquedas
        ExpansionesTotal   = $expTotal
        ExpansionesMediana = Mediana $expansiones
        ExpansionesMax     = if ($expansiones.Count -gt 0) { ($expansiones | Measure-Object -Maximum).Maximum } else { $null }
        NanosTotal         = $nanosTotal
        NanosMediana       = Mediana $nanos
        NanosP95           = Percentil $nanos 95
        NanosPorExpansion  = if ($null -ne $nanosTotal -and $null -ne $expTotal -and $expTotal -gt 0) { $nanosTotal / $expTotal } else { $null }
        NodosPorTick       = if ($null -ne $expTotal -and $null -ne $avancesTotal -and $avancesTotal -gt 0) { $expTotal / $avancesTotal } else { $null }
        Reanudadas         = $reanudadas
        PctReanudadas      = if ($busquedas -gt 0) { 100.0 * $reanudadas / $busquedas } else { $null }
        Replanificaciones  = $replan
        Fallos             = $fallos
        PresupuestoAgotado = $agotados
        UsoPresupuestoPct  = if ($null -ne $presupuestoTotal -and $presupuestoTotal -gt 0 -and $null -ne $expTotal) { 100.0 * $expTotal / $presupuestoTotal } else { $null }
        ExpPorEntidad      = if ($null -ne $expTotal -and $entidades.Count -gt 0) { $expTotal / $entidades.Count } else { $null }
        NanosPorEntidad    = if ($null -ne $nanosTotal -and $entidades.Count -gt 0) { $nanosTotal / $entidades.Count } else { $null }
        Abiertas           = $abiertas
        NanosParedMax      = @($items | ForEach-Object { $_.NanosPared } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum
        TicksMax           = @($items | ForEach-Object { $_.Ticks } | Where-Object { $null -ne $_ } | Measure-Object -Maximum).Maximum
        BucketMenos1ms     = $umbral1
        Bucket1a10ms       = $umbral10
        Bucket10a100ms     = $umbral100
        BucketMas100ms     = $umbralMas
        Pops               = @($items | ForEach-Object { $_.Pops } | Where-Object { $null -ne $_ } | Measure-Object -Sum).Sum
        StalePops          = @($items | ForEach-Object { $_.StalePops } | Where-Object { $null -ne $_ } | Measure-Object -Sum).Sum
        Reopens            = @($items | ForEach-Object { $_.Reopens } | Where-Object { $null -ne $_ } | Measure-Object -Sum).Sum
    })
}

# ================================================================================================
# 7. Cobertura por categoría (sin inventar: reporta lo que falta).
# ================================================================================================

$sinonimos = @{
    'campo_abierto'            = 'campo_abierto|campo'
    'obstaculos'               = 'obstacul|bloqueo|terreno_dinamico|esquiv'
    'muro'                     = 'muro|muralla'
    'laberinto'                = 'laberinto'
    'drop'                     = 'drop|caida|paso_arriba|descenso'
    'break'                    = 'break|romper|rompible'
    'build'                    = 'build|construir|escalon'
    'combinado'                = 'combinad|romper_construir|caida_romper'
    'inaccesible'              = 'inaccesible|inalcanzable'
    'presupuesto_insuficiente' = 'presupuesto_agotado|presupuesto_insuficiente|budget'
    'multientidad_1'           = 'multientidad_1|multientidad1'
    'multientidad_2'           = 'multientidad_2|multientidad2'
    'multientidad_4'           = 'multientidad_4|multientidad4'
    'multientidad_8'           = 'multientidad_8|multientidad8'
}
$escenariosRuntime = @($FilasAgregadas | Where-Object { $_.Fuente -in @('RUNTIME', 'DUMP') } | ForEach-Object { $_.Escenario } | Sort-Object -Unique)
$cobertura = New-Object System.Collections.Generic.List[object]
foreach ($esperado in $Esperados) {
    $patron = if ($sinonimos.ContainsKey($esperado)) { $sinonimos[$esperado] } else { [regex]::Escape($esperado) }
    $coincidencias = @($escenariosRuntime | Where-Object { $_ -match $patron })
    $cobertura.Add([pscustomobject]@{
        Categoria = $esperado
        Presente  = ($coincidencias.Count -gt 0)
        Escenarios = if ($coincidencias.Count -gt 0) { $coincidencias -join ' | ' } else { '-' }
        Estado    = if ($coincidencias.Count -gt 0) { 'medido' } else { 'FALTA' }
    })
}

# ================================================================================================
# 8. Salidas.
# ================================================================================================

$columnasRegistro = @('fuente', 'escenario', 'repeticion', 'entidad', 'tipo_busqueda', 'subtipo',
    'presupuesto', 'expansiones', 'nanos', 'avances', 'nodos_por_tick', 'reanudada',
    'replanificacion', 'fallida', 'presupuesto_agotado', 'longitud_ruta', 'encontrado', 'abierta',
    'motivo', 'candidatos', 'encolados', 'nanos_pared', 'ticks', 'metrics_csv')
$csv = Join-Path $OutDir "perf-resumen-$Tag.csv"
$registrosOrdenados = @($Registros | Sort-Object Fuente, Escenario, Repeticion, Entidad)
$registrosSalida = foreach ($r in $registrosOrdenados) {
    [pscustomobject]@{
        fuente              = $r.Fuente
        escenario           = $r.Escenario
        repeticion          = $r.Repeticion
        entidad             = $r.Entidad
        tipo_busqueda       = $r.TipoBusqueda
        subtipo             = $r.Subtipo
        presupuesto         = $r.Presupuesto
        expansiones         = $r.Expansiones
        nanos               = $r.Nanos
        avances             = $r.Avances
        nodos_por_tick      = $r.NodosPorTick
        reanudada           = $r.Reanudada
        replanificacion     = $r.Replanificacion
        fallida             = $r.Fallida
        presupuesto_agotado = $r.PresupuestoAgotado
        longitud_ruta       = $r.LongitudRuta
        encontrado          = $r.Encontrado
        abierta             = $r.Abierta
        motivo              = $r.Motivo
        candidatos          = $r.Candidatos
        encolados           = $r.Encolados
        nanos_pared         = $r.NanosPared
        ticks               = $r.Ticks
        metrics_csv         = $r.MetricsCsv
    }
}
$registrosSalida | Export-Csv -LiteralPath $csv -NoTypeInformation -Encoding utf8

$md = Join-Path $OutDir "perf-resumen-$Tag.md"
$lineas = New-Object System.Collections.Generic.List[string]
$lineas.Add('# Resumen de métricas de navegación y rendimiento del planner')
$lineas.Add('')
$lineas.Add("- Generado: $((Get-Date).ToString('o'))")
$lineas.Add("- Raíz: ``$Raiz``")
$lineas.Add("- Entrada: ``$InputDir``")
$lineas.Add("- Registros: $($Registros.Count); agregados: $($FilasAgregadas.Count); no medidos: $($NoMedidos.Count); avisos: $($Avisos.Count)")
$lineas.Add('')
$lineas.Add('## Fuentes leídas')
$lineas.Add('')
foreach ($fuente in $Fuentes) { $lineas.Add("- ``$fuente``") }
if ($Fuentes.Count -eq 0) { $lineas.Add('- (ninguna: no hay índices, volcados ni baseline en la entrada)') }
$lineas.Add('')

$lineas.Add('## Agregado por escenario')
$lineas.Add('')
$lineas.Add('| fuente | escenario | subtipo | regs | reps | entidades | búsquedas | exp total | exp mediana | exp máx | nanos total | nanos mediana | nanos p95 | ns/exp | nodos/tick | reanudadas | replanif | fallos | presup. agotado | uso presup. % | exp/entidad | ns/entidad | abiertas | p50 <1ms | 1-10ms | 10-100ms | >=100ms |')
$lineas.Add('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
foreach ($f in $FilasAgregadas) {
    $lineas.Add("| $($f.Fuente) | $($f.Escenario) | $($f.Subtipo) | $($f.Registros) | $($f.Repeticiones) | $($f.Entidades) | $($f.Busquedas) | $(Formato-Entero $f.ExpansionesTotal) | $(Formato-Numero $f.ExpansionesMediana 1) | $(Formato-Entero $f.ExpansionesMax) | $(Formato-Entero $f.NanosTotal) | $(Formato-Entero $f.NanosMediana) | $(Formato-Entero $f.NanosP95) | $(Formato-Numero $f.NanosPorExpansion 0) | $(Formato-Numero $f.NodosPorTick 2) | $($f.Reanudadas) ($(Formato-Numero $f.PctReanudadas 0)%) | $($f.Replanificaciones) | $($f.Fallos) | $($f.PresupuestoAgotado) | $(Formato-Numero $f.UsoPresupuestoPct 1) | $(Formato-Numero $f.ExpPorEntidad 1) | $(Formato-Entero $f.NanosPorEntidad) | $($f.Abiertas) | $($f.BucketMenos1ms) | $($f.Bucket1a10ms) | $($f.Bucket10a100ms) | $($f.BucketMas100ms) |")
}
if ($FilasAgregadas.Count -eq 0) { $lineas.Add('| - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - | - |') }
$lineas.Add('')

$lineas.Add('## Cobertura de escenarios (campaña de navegación)')
$lineas.Add('')
$lineas.Add('| categoría | estado | escenarios observados |')
$lineas.Add('|---|---|---|')
foreach ($c in $cobertura) { $lineas.Add("| $($c.Categoria) | $($c.Estado) | $($c.Escenarios) |") }
$lineas.Add('')

$lineas.Add('## Filas NO medidas (excluidas de todos los agregados)')
$lineas.Add('')
if ($NoMedidos.Count -eq 0) {
    $lineas.Add('Ninguna.')
} else {
    $lineas.Add('| origen | escenario | rep | entidad | motivo |')
    $lineas.Add('|---|---|---|---|---|')
    foreach ($n in $NoMedidos) { $lineas.Add("| $($n.Origen) | $($n.Escenario) | $($n.Repeticion) | $($n.Entidad) | $($n.Motivo) |") }
}
$lineas.Add('')

$lineas.Add('## Avisos')
$lineas.Add('')
if ($Avisos.Count -eq 0) {
    $lineas.Add('Ninguno.')
} else {
    foreach ($a in $Avisos) { $lineas.Add("- ``$a``") }
}
$lineas.Add('')
$lineas.Add('## Cómo leer estas columnas')
$lineas.Add('')
$lineas.Add('- `exp mediana`, `nanos mediana` y `nanos p95` se calculan **por registro** (búsqueda si hay volcado; fila del índice si no).')
$lineas.Add('- `nodos/tick = exp total / avances total`; un avance es un slice de `planGlobalIncremental`, no un tick de reloj.')
$lineas.Add('- `nanos` es la suma de slices medidos por `NavPlannerMetrics`, no tiempo de pared entre ticks.')
$lineas.Add('- `nanos_pared` (índice) es cota superior (preparación + ejecución) y nunca se suma a `nanos`.')
$lineas.Add('- `uso presup. % = exp total / suma de presupuestos`: saturación del presupuesto duro, no calidad de ruta.')
$lineas.Add('- `ns/exp` y `exp/entidad` permiten comparar versiones en el MISMO escenario, no entre escenarios.')
$lineas.Add('- Una celda `-` significa dato no disponible; no se ha rellenado con 0.')
$lineas.Add('')
$lineas.Add('## Limitaciones')
$lineas.Add('')
$lineas.Add('- La campaña de `navegacion` es funcional: `laberinto` y `multientidad_2/4/8` pueden venir como `no_soportado_por_campana_funcional` (se listan en NO_MEDIDO).')
$lineas.Add('- `MAX_REGISTROS=512` en `NavPlannerMetrics`: si un volcado se toma después de más de 512 cierres, el detalle `BUSQUEDA` queda truncado; los totales no.')
$lineas.Add('- El volcado se toma al cerrar el escenario; búsquedas todavía abiertas aparecen como `ABIERTA` y se cuentan aparte.')
$lineas.Add('- `PERF_SINTETICO` (si se incluye) mide el núcleo A* sin Minecraft; no es el coste de mundo real (ver `tools/perf/LEEME.md`).')
$lineas.Add('')
$lineas.Add('## Reproducir')
$lineas.Add('')
$lineas.Add('```powershell')
$lineas.Add('.\tools\perf\resumir-metricas.ps1 -IncluirBaseline -IncluirPerf')
$lineas.Add('```')
Set-Content -LiteralPath $md -Value ($lineas -join [Environment]::NewLine) -Encoding utf8

# Copias estables para que otros agentes no dependan del sello.
Copy-Item -LiteralPath $csv -Destination (Join-Path $OutDir 'perf-resumen-ultimo.csv') -Force
Copy-Item -LiteralPath $md -Destination (Join-Path $OutDir 'perf-resumen-ultimo.md') -Force

# ================================================================================================
# 9. Consola.
# ================================================================================================

if (-not $SinConsola) {
    Write-Output "resumir-metricas: $($Registros.Count) registros, $($FilasAgregadas.Count) agregados, $($NoMedidos.Count) no medidos, $($Avisos.Count) avisos"
    if ($FilasAgregadas.Count -gt 0) {
        $FilasAgregadas | Select-Object Fuente, Escenario, Subtipo, Registros, ExpansionesTotal, NanosMediana,
            NanosPorExpansion, NodosPorTick, Reanudadas, Fallos, PresupuestoAgotado, ExpPorEntidad |
            Format-Table -AutoSize | Out-String -Width 240 | Write-Output
    }
    foreach ($a in $Avisos) { Write-Output $a }
    foreach ($n in $NoMedidos) { Write-Output "NO_MEDIDO,$($n.Escenario),rep$($n.Repeticion),$($n.Motivo)" }
    Write-Output "CSV: $csv"
    Write-Output "MD : $md"
}
