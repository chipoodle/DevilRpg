<#
.SYNOPSIS
    Diagnostico de un run C4N (o de todos) sobre la evidencia ya escrita en build/c4n.

.DESCRIPTION
    Lee fin.txt, sha.txt, phase-<fase>.json, snapshot-<fase>.txt, jobsite-<fase>.txt,
    diagnostico-<fase>.json y los logs srv-<fase>.log[.err]. Escribe:
      build/av/diagnostico-<run>.json  (maquina)
      build/av/diagnostico-<run>.md    (persona)
    y saca por pantalla el veredicto y la tabla por fase.

    NO ejecuta Minecraft, NO toca build/c4n ni los mundos: solo lee y escribe en build/av.

    Estados que distingue por fase:
      OK                                  -> result PASS y sin invariantes incumplidas.
      INVARIANTE_INCUMPLIDA               -> fallo con invariantes reales incumplidas.
      FALLO_CON_CAUSA_CONOCIDA            -> fallo sin invariante duro, con causa documentada.
      FALLO_CON_CAUSA_DESCONOCIDA         -> fallo sin causa documentada.
      SIN_EVIDENCIA / EVIDENCIA_INVALIDA  -> no hay fase o el JSON no parsea.

.PARAMETER Run
    Nombre o sufijo del run: c4n-20261123-024 o 024.

.PARAMETER All
    Analiza todos los runs de build/c4n.

.PARAMETER OutDir
    Carpeta de salida. Por defecto build/av.

.PARAMETER FailOnError
    Sale con codigo 1 si algun run analizado no es C4N_PASS.

.EXAMPLE
    .\tools\av\diagnose.ps1 -Run 024

.EXAMPLE
    .\tools\av\diagnose.ps1 -All

.EXAMPLE
    .\tools\av\diagnose.ps1 -Run 024 -FailOnError
#>
param(
    [string]$Run = "",
    [switch]$All,
    [string]$OutDir = "",
    [switch]$FailOnError
)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$C4nDir = Join-Path $Raiz 'build\c4n'
if ([string]::IsNullOrWhiteSpace($OutDir)) { $OutDir = Join-Path $Raiz 'build\av' }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$PuestosEsperados = 12   # contrato del pueblo; si el phase trae `esperado`, manda ese valor
$FundadoresEsperados = 4 # VillageManager.MILICIA_FUNDADORES

function Get-Field {
    param($Objeto, [string]$Nombre, $Defecto = $null)
    if ($null -eq $Objeto) { return $Defecto }
    if ($Objeto -is [System.Collections.IDictionary]) {
        if ($Objeto.Contains($Nombre)) { return $Objeto[$Nombre] }
        return $Defecto
    }
    $prop = $Objeto.PSObject.Properties[$Nombre]
    if ($null -eq $prop -or $null -eq $prop.Value) { return $Defecto }
    return $prop.Value
}

function To-IntOrNull {
    param($Valor)
    if ($null -eq $Valor) { return $null }
    try { return [int]$Valor } catch { return $null }
}

function To-BoolOrNull {
    param($Valor)
    if ($null -eq $Valor) { return $null }
    if ($Valor -is [bool]) { return $Valor }
    $texto = ([string]$Valor).Trim().ToLowerInvariant()
    if ($texto -eq 'true') { return $true }
    if ($texto -eq 'false') { return $false }
    return $null
}

function Format-Causa {
    param($Causa)
    if ($null -eq $Causa) { return '' }
    $codigo = Get-Field $Causa 'codigo' ''
    $detalle = Get-Field $Causa 'detalle' ''
    if ([string]::IsNullOrWhiteSpace($codigo) -and [string]::IsNullOrWhiteSpace($detalle)) {
        return [string]$Causa
    }
    if ([string]::IsNullOrWhiteSpace($detalle)) { return [string]$codigo }
    return "$codigo :: $detalle"
}

function Read-JsonFile {
    param([string]$Ruta)
    if (-not (Test-Path -LiteralPath $Ruta -PathType Leaf)) {
        return [pscustomobject]@{ ok = $false; motivo = 'ausente'; raw = $null; data = $null }
    }
    $raw = $null
    try {
        $raw = Get-Content -LiteralPath $Ruta -Raw -ErrorAction Stop
    } catch {
        return [pscustomobject]@{ ok = $false; motivo = "lectura_fallida: $($_.Exception.Message)"; raw = $null; data = $null }
    }
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return [pscustomobject]@{ ok = $false; motivo = 'vacio'; raw = $raw; data = $null }
    }
    try {
        $data = $raw | ConvertFrom-Json -ErrorAction Stop
        return [pscustomobject]@{ ok = $true; motivo = ''; raw = $raw; data = $data }
    } catch {
        return [pscustomobject]@{ ok = $false; motivo = "json_invalido: $($_.Exception.Message)"; raw = $raw; data = $null }
    }
}

function Resolve-Run {
    param([string]$Nombre)
    if ([string]::IsNullOrWhiteSpace($Nombre)) { return $null }
    $directo = Join-Path $C4nDir $Nombre
    if (Test-Path -LiteralPath $directo -PathType Container) { return $directo }
    $candidatos = @()
    if ($Nombre -notmatch '^c4n-') {
        $conPrefijo = Join-Path $C4nDir "c4n-$Nombre"
        if (Test-Path -LiteralPath $conPrefijo -PathType Container) { return $conPrefijo }
        if (Test-Path -LiteralPath $C4nDir -PathType Container) {
            $candidatos = @(Get-ChildItem -LiteralPath $C4nDir -Directory |
                    Where-Object { $_.Name.EndsWith("-$Nombre") } | Sort-Object Name)
        }
    }
    if ($candidatos.Count -eq 1) { return $candidatos[0].FullName }
    if ($candidatos.Count -gt 1) {
        Write-Output "FALLO: '$Nombre' es ambiguo; candidatos: $(@($candidatos | ForEach-Object { $_.Name }) -join ', ')"
        return $null
    }
    return $null
}

function Get-RunsDisponibles {
    if (-not (Test-Path -LiteralPath $C4nDir -PathType Container)) { return @() }
    return @(Get-ChildItem -LiteralPath $C4nDir -Directory -Filter 'c4n-*' |
            Sort-Object Name | ForEach-Object { $_.Name })
}

function New-EsperadoDerivado {
    param($Data)
    $esperados = To-IntOrNull (Get-Field $Data 'esperados')
    if ($null -eq $esperados) { $esperados = $PuestosEsperados }
    return [pscustomobject][ordered]@{
        profesionales    = $esperados
        fundadores       = $FundadoresEsperados
        uuid             = $esperados
        goals            = $esperados
        duplicados       = 0
        huerfanos        = 0
        perdidas_aldeanos = 0
        reanudados       = $esperados
        origen           = 'derivado_del_contrato_del_pueblo'
    }
}

function New-ObservadoDerivado {
    param($Data)
    $esperados = To-IntOrNull (Get-Field $Data 'esperados')
    if ($null -eq $esperados) { $esperados = $PuestosEsperados }
    $guardias = Get-Field $Data 'guardias'
    $uuidSet = Get-Field $Data 'uuid_set' @()
    # OBSERVADO, no esperado: se usa `presentes` (o el UUID-set real) y si no hay dato queda null.
    $presentes = To-IntOrNull (Get-Field $Data 'presentes')
    if ($null -eq $presentes -and @($uuidSet).Count -gt 0) { $presentes = @($uuidSet).Count }
    return [pscustomobject][ordered]@{
        profesionales                  = $presentes
        fundadores                     = To-IntOrNull (Get-Field $guardias 'fundadores')
        uuid                           = @($uuidSet).Count
        goals_instalados               = To-IntOrNull (Get-Field $Data 'goals_instalados')
        presentes                      = $presentes
        perdidos                       = To-IntOrNull (Get-Field $Data 'perdidos')
        profesion_estable              = To-IntOrNull (Get-Field $Data 'profesion_estable')
        reanudados                     = To-IntOrNull (Get-Field $Data 'reanudados')
        activos                        = To-IntOrNull (Get-Field $Data 'activos')
        movidos                        = To-IntOrNull (Get-Field $Data 'movidos')
        muestras                       = To-IntOrNull (Get-Field $Data 'muestras')
        job_site_presentes             = To-IntOrNull (Get-Field $Data 'job_site_presentes')
        job_site_huerfanos             = To-IntOrNull (Get-Field $Data 'job_site_huerfanos')
        job_site_duplicados            = To-IntOrNull (Get-Field $Data 'job_site_duplicados')
        job_site_estables              = To-IntOrNull (Get-Field $Data 'job_site_estables')
        job_site_cambiados             = To-IntOrNull (Get-Field $Data 'job_site_cambiados')
        job_site_perdidos              = To-IntOrNull (Get-Field $Data 'job_site_perdidos')
        job_site_nuevos                = To-IntOrNull (Get-Field $Data 'job_site_nuevos')
        job_site_comparacion           = Get-Field $Data 'job_site_comparacion'
        job_site_al_cargar_presentes   = To-IntOrNull (Get-Field $Data 'job_site_al_cargar_presentes')
        job_site_reclamados_en_ventana = To-IntOrNull (Get-Field $Data 'job_site_reclamados_en_ventana')
        job_site_al_cargar_perdidos    = To-IntOrNull (Get-Field $Data 'job_site_al_cargar_perdidos')
        origen                         = 'derivado_de_campos_planos'
    }
}

function New-InvariantesDerivados {
    param($Data)
    $esperados = To-IntOrNull (Get-Field $Data 'esperados')
    if ($null -eq $esperados) { $esperados = $PuestosEsperados }
    $presentes = To-IntOrNull (Get-Field $Data 'presentes')
    $duplicados = To-IntOrNull (Get-Field $Data 'job_site_duplicados')
    $huerfanos = To-IntOrNull (Get-Field $Data 'job_site_huerfanos')
    $perdidos = To-IntOrNull (Get-Field $Data 'perdidos')
    $estables = To-IntOrNull (Get-Field $Data 'profesion_estable')
    $goals = To-IntOrNull (Get-Field $Data 'goals_instalados')
    $reanudados = To-IntOrNull (Get-Field $Data 'reanudados')
    return [pscustomobject][ordered]@{
        roster_invariantes_ok = $null
        roster_violaciones    = @()
        job_site_ok           = if ($null -ne $duplicados -and $null -ne $huerfanos) { ($duplicados -eq 0 -and $huerfanos -eq 0) } else { $null }
        censo_completo        = if ($null -ne $presentes) { ($presentes -eq $esperados) } else { $null }
        sin_duplicados        = if ($null -ne $duplicados) { ($duplicados -eq 0) } else { $null }
        sin_huerfanos         = if ($null -ne $huerfanos) { ($huerfanos -eq 0) } else { $null }
        sin_perdidas          = if ($null -ne $perdidos) { ($perdidos -eq 0) } else { $null }
        identidades           = if ($null -ne $estables) { ($estables -eq $esperados) } else { $null }
        goals                 = if ($null -ne $goals) { ($goals -eq $esperados) } else { $null }
        reanudados            = if ($null -ne $reanudados) { ($reanudados -eq $esperados) } else { $null }
        medido                = $false
        origen                = 'derivado_de_campos_planos'
    }
}

function Get-InvariantesIncumplidas {
    param($Invariantes)
    $lista = New-Object System.Collections.Generic.List[string]
    foreach ($nombre in @('job_site_ok', 'censo_completo', 'sin_duplicados', 'sin_huerfanos', 'sin_perdidas',
            'identidades', 'goals', 'reanudados')) {
        $valor = To-BoolOrNull (Get-Field $Invariantes $nombre)
        if ($valor -eq $false) { $lista.Add("invariante:$nombre=false") }
    }
    $roster = To-BoolOrNull (Get-Field $Invariantes 'roster_invariantes_ok')
    if ($roster -eq $false) { $lista.Add('invariante:roster_invariantes_ok=false') }
    foreach ($violacion in @(Get-Field $Invariantes 'roster_violaciones' @())) {
        if ($null -ne $violacion -and "$violacion" -ne '') { $lista.Add("roster:$violacion") }
    }
    return @($lista)
}

function Get-Causas {
    param($Data, [string]$Clave)
    $salida = New-Object System.Collections.Generic.List[string]
    foreach ($causa in @(Get-Field $Data $Clave @())) {
        if ($null -ne $causa) { $salida.Add((Format-Causa $causa)) }
    }
    return @($salida)
}

function Get-EvidenciaLog {
    param([string]$RunDir, [string]$Fase)
    $log = Join-Path $RunDir "srv-$Fase.log"
    $patrones = 'AL_CARGAR|CARGA_COMPLETA|ROSTER_INVARIANTS|JOB_SITE_DUPLICADO|DUPLICADO_TICK|FALLO DE INSTRUMENTO|DIAG |MANT_INICIAL|FASE '
    if (-not (Test-Path -LiteralPath $log -PathType Leaf)) { return @() }
    try {
        return @(Select-String -LiteralPath $log -Pattern $patrones -Encoding utf8 -ErrorAction Stop |
                Select-Object -First 60 | ForEach-Object { $_.Line.Trim() })
    } catch {
        return @("no pude leer el log: $($_.Exception.Message)")
    }
}

function Get-Stderr {
    param([string]$RunDir, [string]$Fase)
    $err = Join-Path $RunDir "srv-$Fase.log.err"
    if (-not (Test-Path -LiteralPath $err -PathType Leaf)) { return @() }
    # El lanzador mata el servidor al terminar la fase, asi que Gradle siempre deja aqui su "BUILD FAILED".
    # Eso no es evidencia de fallo de la aldea: se filtra el ruido conocido y solo queda lo raro de verdad.
    $ruido = '^NOTE: Picked up JDK_JAVA_OPTIONS|^FAILURE: Build failed|^\* What went wrong:|^Execution failed for task|^> Process ''command|^> Run with --|^\* Try:|^> Get more help|^BUILD FAILED in|^\s*$'
    try {
        return @(Get-Content -LiteralPath $err -Tail 40 -ErrorAction Stop |
                Where-Object { $_ -and ($_ -notmatch $ruido) })
    } catch {
        return @()
    }
}

function Get-SnapshotInfo {
    param([string]$RunDir, [string]$Fase)
    $snap = Join-Path $RunDir "snapshot-$Fase.txt"
    if (-not (Test-Path -LiteralPath $snap -PathType Leaf)) {
        return [pscustomobject]@{ existe = $false; filas = 0; uuids = 0 }
    }
    $lineas = @(Get-Content -LiteralPath $snap -ErrorAction SilentlyContinue |
            Where-Object { $_ -and -not $_.StartsWith('#') })
    $uuids = @($lineas | ForEach-Object { ($_ -split '\|')[0] } | Sort-Object -Unique)
    return [pscustomobject]@{ existe = $true; filas = $lineas.Count; uuids = $uuids.Count }
}

function New-FaseDiagnostico {
    param([string]$RunDir, [string]$Fase)

    $phasePath = Join-Path $RunDir "phase-$Fase.json"
    $lectura = Read-JsonFile $phasePath
    $data = if ($lectura.ok) { $lectura.data } else { $null }
    $donePath = Join-Path $RunDir "done-$Fase.txt"
    $done = $null
    if (Test-Path -LiteralPath $donePath -PathType Leaf) {
        $done = (Get-Content -LiteralPath $donePath -Raw -ErrorAction SilentlyContinue)
        if ($null -ne $done) { $done = $done.Trim() }
    }

    $result = Get-Field $data 'result'
    $origenResult = 'phase-<fase>.json'
    if ([string]::IsNullOrWhiteSpace([string]$result)) {
        if (-not [string]::IsNullOrWhiteSpace([string]$done)) {
            $result = [string]$done
            $origenResult = 'done-<fase>.txt'
        } else {
            $result = 'SIN_RESULTADO'
            $origenResult = 'ausente'
        }
    }

    $esperado = Get-Field $data 'esperado'
    if ($null -eq $esperado) { $esperado = New-EsperadoDerivado $data }
    $observado = Get-Field $data 'observado'
    if ($null -eq $observado) { $observado = New-ObservadoDerivado $data }
    $invariantes = Get-Field $data 'invariantes'
    if ($null -eq $invariantes) { $invariantes = New-InvariantesDerivados $data }

    $incumplidas = @(Get-InvariantesIncumplidas $invariantes)
    $conocidas = @(Get-Causas $data 'causa_conocida')
    $desconocidas = @(Get-Causas $data 'causa_desconocida')
    $falloTexto = Get-Field $data 'fallo'
    if ($result -eq 'FAIL' -and $conocidas.Count -eq 0 -and $desconocidas.Count -eq 0) {
        if (-not [string]::IsNullOrWhiteSpace([string]$falloTexto)) {
            # Un `fallo` de instrumento es una causa CONOCIDA: dice exactamente que precondicion se rompio.
            $conocidas = @("FALLO_DE_INSTRUMENTO :: $falloTexto")
        } else {
            $desconocidas = @('SIN_CLASIFICACION_EN_EL_JSON_ORIGINAL :: la fase fallo y el JSON no trae bloque de causas (formato anterior); revisar campos planos, snapshot y log')
        }
    }

    $estado = 'SIN_RESULTADO'
    $phaseExiste = (Test-Path -LiteralPath $phasePath -PathType Leaf)
    if (-not $lectura.ok -and $phaseExiste) {
        # El fichero existe pero no parsea: la evidencia es invalida, no "no hay evidencia".
        $estado = 'EVIDENCIA_INVALIDA'
    } elseif (-not $lectura.ok -and $null -eq $done) {
        $estado = 'SIN_EVIDENCIA'
    } elseif ($result -eq 'PASS') {
        if ($incumplidas.Count -gt 0) {
            $estado = 'INCONSISTENTE_PASS_CON_INVARIANTE'
        } else {
            $estado = 'OK'
        }
    } elseif ($incumplidas.Count -gt 0) {
        $estado = 'INVARIANTE_INCUMPLIDA'
    } elseif ($conocidas.Count -gt 0) {
        $estado = 'FALLO_CON_CAUSA_CONOCIDA'
    } elseif ($desconocidas.Count -gt 0) {
        $estado = 'FALLO_CON_CAUSA_DESCONOCIDA'
    } elseif ($result -eq 'FAIL') {
        $estado = 'FALLO_SIN_CAUSA_CLASIFICADA'
    }

    $faltantes = New-Object System.Collections.Generic.List[string]
    foreach ($f in @("phase-$Fase.json", "snapshot-$Fase.txt", "jobsite-$Fase.txt", "srv-$Fase.log", "diagnostico-$Fase.json")) {
        if (-not (Test-Path -LiteralPath (Join-Path $RunDir $f) -PathType Leaf)) { $faltantes.Add($f) }
    }
    if (-not $lectura.ok) { $faltantes.Add("json:$($lectura.motivo)") }

    return [pscustomobject][ordered]@{
        fase                    = $Fase
        result                  = [string]$result
        result_origen           = $origenResult
        estado                  = $estado
        guardado_ok             = To-BoolOrNull (Get-Field $data 'guardado_ok')
        fallo                   = Get-Field $data 'fallo'
        save_failed             = (Test-Path -LiteralPath (Join-Path $RunDir "save-failed-$Fase.txt") -PathType Leaf)
        esperado                = $esperado
        observado               = $observado
        invariantes             = $invariantes
        invariantes_incumplidas = @($incumplidas)
        causa_conocida          = @($conocidas)
        causa_desconocida       = @($desconocidas)
        job_site_detalle        = @(Get-Field $data 'job_site_detalle' @())
        guardias                = Get-Field $data 'guardias'
        notas                   = @(Get-Field $data 'notas' @())
        snapshot                = Get-SnapshotInfo $RunDir $Fase
        evidencia_log           = @(Get-EvidenciaLog $RunDir $Fase)
        stderr_ultimas          = @(Get-Stderr $RunDir $Fase)
        artefactos              = [pscustomobject][ordered]@{
            phase     = "phase-$Fase.json"
            snapshot  = "snapshot-$Fase.txt"
            jobsite   = "jobsite-$Fase.txt"
            diagnostico = "diagnostico-$Fase.json"
            log       = "srv-$Fase.log"
        }
        faltantes               = @($faltantes)
        json_valido             = $lectura.ok
    }
}

function New-RunDiagnostico {
    param([string]$RunDir)

    $nombre = Split-Path -Leaf $RunDir
    $finPath = Join-Path $RunDir 'fin.txt'
    $fin = $null
    if (Test-Path -LiteralPath $finPath -PathType Leaf) {
        $fin = (Get-Content -LiteralPath $finPath -Raw -ErrorAction SilentlyContinue)
        if ($null -ne $fin) { $fin = $fin.Trim() }
    }
    $sha = 'desconocido'
    $shaPath = Join-Path $RunDir 'sha.txt'
    if (Test-Path -LiteralPath $shaPath -PathType Leaf) {
        $sha = (Get-Content -LiteralPath $shaPath -Raw -ErrorAction SilentlyContinue)
        if (-not [string]::IsNullOrWhiteSpace([string]$sha)) { $sha = $sha.Trim() } else { $sha = 'desconocido' }
    }

    $fases = @()
    foreach ($fase in @('prepare', 'reload1', 'reload2')) {
        $fases += New-FaseDiagnostico $RunDir $fase
    }

    $veredicto = $null
    $veredictoOrigen = $null
    if (-not [string]::IsNullOrWhiteSpace([string]$fin)) {
        $veredicto = (([string]$fin) -split '\s+')[0]
        $veredictoOrigen = 'fin.txt'
    } else {
        $resultados = @($fases | ForEach-Object { $_.result })
        if (@($resultados | Where-Object { $_ -eq 'PASS' }).Count -eq 3) {
            $veredicto = 'C4N_PASS'
        } elseif (@($resultados | Where-Object { $_ -eq 'SIN_RESULTADO' }).Count -eq 3) {
            $veredicto = 'SIN_EVIDENCIA'
        } else {
            $veredicto = 'C4N_FAIL'
        }
        $veredictoOrigen = 'derivado_de_fases'
    }

    $faltantes = New-Object System.Collections.Generic.List[string]
    foreach ($f in @('fin.txt', 'sha.txt')) {
        if (-not (Test-Path -LiteralPath (Join-Path $RunDir $f) -PathType Leaf)) { $faltantes.Add($f) }
    }

    return [pscustomobject][ordered]@{
        run              = $nombre
        sha              = $sha
        veredicto        = $veredicto
        veredicto_origen = $veredictoOrigen
        fin              = $fin
        generado         = (Get-Date).ToString('o')
        reproducir       = [pscustomobject][ordered]@{
            comando    = '.\tools\c4n\lanzar-c4n.ps1 -Run <nuevo>'
            diagnostico = ".\tools\av\diagnose.ps1 -Run $nombre"
            colector   = '.\tools\av\collect.ps1'
            nota       = 'ejecutar desde la raiz del repo; el runtime del servidor lo lanza el operador'
        }
        fases            = $fases
        faltantes        = @($faltantes)
    }
}

function Format-Valor {
    param($Valor)
    if ($null -eq $Valor) { return '?' }
    if ($Valor -is [bool]) { if ($Valor) { return 'true' } else { return 'false' } }
    if ($Valor -is [System.Collections.IEnumerable] -and $Valor -isnot [string]) {
        return (@($Valor) -join ', ')
    }
    return [string]$Valor
}

function Write-Markdown {
    param($Diagnostico, [string]$Ruta)

    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine("# Diagnostico $($Diagnostico.run)")
    [void]$sb.AppendLine()
    [void]$sb.AppendLine("- **Veredicto:** $($Diagnostico.veredicto)  (origen: $($Diagnostico.veredicto_origen))")
    [void]$sb.AppendLine("- **SHA:** $($Diagnostico.sha)")
    [void]$sb.AppendLine("- **Generado:** $($Diagnostico.generado)")
    if ($Diagnostico.fin) { [void]$sb.AppendLine("- **fin.txt:** $($Diagnostico.fin)") }
    [void]$sb.AppendLine('- **Reproducir:** `.\tools\c4n\lanzar-c4n.ps1 -Run <nuevo>`')
    [void]$sb.AppendLine("- **Diagnostico:** ``.\tools\av\diagnose.ps1 -Run $($Diagnostico.run)``")
    if ($Diagnostico.faltantes.Count -gt 0) {
        [void]$sb.AppendLine("- **Faltan (nivel run):** $($Diagnostico.faltantes -join ', ')")
    }
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('## Tabla por fase')
    [void]$sb.AppendLine()
    [void]$sb.AppendLine('| Fase | Result | Estado | Esperado | Observado | Invariante incumplida | Causa conocida | Causa desconocida |')
    [void]$sb.AppendLine('|---|---|---|---|---|---|---|---|')
    foreach ($f in $Diagnostico.fases) {
        $esperado = "prof=$(Format-Valor $f.esperado.profesionales), fund=$(Format-Valor $f.esperado.fundadores), uuid=$(Format-Valor $f.esperado.uuid), goals=$(Format-Valor $f.esperado.goals)"
        $observado = "prof=$(Format-Valor $f.observado.profesionales), fund=$(Format-Valor $f.observado.fundadores), pres=$(Format-Valor $f.observado.presentes), goals=$(Format-Valor $f.observado.goals_instalados), dup=$(Format-Valor $f.observado.job_site_duplicados), huer=$(Format-Valor $f.observado.job_site_huerfanos)"
        $inc = if ($f.invariantes_incumplidas.Count -gt 0) { $f.invariantes_incumplidas -join '<br>' } else { '-' }
        $con = if ($f.causa_conocida.Count -gt 0) { $f.causa_conocida -join '<br>' } else { '-' }
        $des = if ($f.causa_desconocida.Count -gt 0) { $f.causa_desconocida -join '<br>' } else { '-' }
        [void]$sb.AppendLine("| $($f.fase) | $($f.result) | $($f.estado) | $esperado | $observado | $inc | $con | $des |")
    }
    [void]$sb.AppendLine()

    foreach ($f in $Diagnostico.fases) {
        [void]$sb.AppendLine("### $($f.fase)")
        [void]$sb.AppendLine()
        [void]$sb.AppendLine("- **Estado:** $($f.estado) (result: $($f.result), origen: $($f.result_origen))")
        [void]$sb.AppendLine("- **Guardado:** ok=$(Format-Valor $f.guardado_ok), save_failed=$($f.save_failed)")
        if ($f.fallo) { [void]$sb.AppendLine("- **Fallo de instrumento:** ``$($f.fallo)``") }
        [void]$sb.AppendLine("- **Esperado:** $((ConvertTo-Json $f.esperado -Compress -Depth 5))")
        [void]$sb.AppendLine("- **Observado:** $((ConvertTo-Json $f.observado -Compress -Depth 5))")
        [void]$sb.AppendLine("- **Invariantes:** $((ConvertTo-Json $f.invariantes -Compress -Depth 6))")
        if ($f.invariantes_incumplidas.Count -gt 0) {
            [void]$sb.AppendLine("- **Invariante incumplida:**")
            foreach ($i in $f.invariantes_incumplidas) { [void]$sb.AppendLine("  - $i") }
        }
        if ($f.causa_conocida.Count -gt 0) {
            [void]$sb.AppendLine("- **Causa conocida:**")
            foreach ($c in $f.causa_conocida) { [void]$sb.AppendLine("  - $c") }
        }
        if ($f.causa_desconocida.Count -gt 0) {
            [void]$sb.AppendLine("- **Causa desconocida:**")
            foreach ($c in $f.causa_desconocida) { [void]$sb.AppendLine("  - $c") }
        }
        if ($f.guardias) {
            [void]$sb.AppendLine("- **Guardias:** total=$(Format-Valor $f.guardias.total), fundadores=$(Format-Valor $f.guardias.fundadores), mascara=$(Format-Valor $f.guardias.mascara_persistida), goal=$(Format-Valor $f.guardias.con_goal_guardia), armadura_completa=$(Format-Valor $f.guardias.armadura_completa)")
        }
        if ($f.job_site_detalle.Count -gt 0) {
            [void]$sb.AppendLine("- **Puestos (detalle):**")
            foreach ($d in $f.job_site_detalle) { [void]$sb.AppendLine("  - $d") }
        }
        if ($f.snapshot.existe) {
            [void]$sb.AppendLine("- **Snapshot:** $($f.snapshot.filas) fila(s), $($f.snapshot.uuids) UUID")
        } else {
            [void]$sb.AppendLine('- **Snapshot:** ausente')
        }
        if ($f.evidencia_log.Count -gt 0) {
            [void]$sb.AppendLine('- **Evidencia del log:**')
            foreach ($l in $f.evidencia_log) { [void]$sb.AppendLine("  - ``$l``") }
        }
        if ($f.stderr_ultimas.Count -gt 0) {
            [void]$sb.AppendLine('- **stderr (ultimas lineas):**')
            foreach ($l in $f.stderr_ultimas) { [void]$sb.AppendLine("  - ``$l``") }
        }
        if ($f.faltantes.Count -gt 0) {
            [void]$sb.AppendLine("- **Faltan:** $($f.faltantes -join ', ')")
        }
        [void]$sb.AppendLine()
    }
    Set-Content -LiteralPath $Ruta -Value $sb.ToString() -Encoding utf8
}

# ------------------------------------------------------------------ seleccion de runs
$runs = @()
if ($All) {
    $runs = @(Get-RunsDisponibles)
} elseif (-not [string]::IsNullOrWhiteSpace($Run)) {
    $dir = Resolve-Run $Run
    if ($null -eq $dir) {
        Write-Output "FALLO: no existe el run '$Run' en $C4nDir"
        Write-Output "Runs disponibles:"
        Get-RunsDisponibles | ForEach-Object { Write-Output "  $_" }
        exit 1
    }
    $runs = @((Split-Path -Leaf $dir))
} else {
    Write-Output 'Uso: .\tools\av\diagnose.ps1 -Run <c4n-...|n> | -All [-FailOnError]'
    Write-Output 'Runs disponibles:'
    Get-RunsDisponibles | ForEach-Object { Write-Output "  $_" }
    exit 1
}

if ($runs.Count -eq 0) {
    Write-Output "FALLO: no hay runs en $C4nDir"
    exit 1
}

$resultados = @()
foreach ($nombre in $runs) {
    $runDir = Join-Path $C4nDir $nombre
    try {
        $diag = New-RunDiagnostico $runDir
    } catch {
        Write-Output "AVISO: no pude diagnosticar $nombre : $($_.Exception.Message)"
        continue
    }
    $outJson = Join-Path $OutDir "diagnostico-$nombre.json"
    $outMd = Join-Path $OutDir "diagnostico-$nombre.md"
    try {
        $diag | ConvertTo-Json -Depth 14 | Set-Content -LiteralPath $outJson -Encoding utf8
        Write-Markdown $diag $outMd
    } catch {
        Write-Output "AVISO: no pude escribir el diagnostico de $nombre : $($_.Exception.Message)"
        continue
    }
    $resultados += $diag
    $fases = ($diag.fases | ForEach-Object { "$($_.fase)=$($_.result)/$($_.estado)" }) -join ' '
    $shaCorto = [string]$diag.sha
    if ([string]::IsNullOrEmpty($shaCorto)) { $shaCorto = '?' } else { $shaCorto = $shaCorto.Substring(0, [Math]::Min(8, $shaCorto.Length)) }
    Write-Output "$($diag.run)  $($diag.veredicto)  sha=$shaCorto  $fases"
}

# resumen
Write-Output ''
Write-Output "=== RESUMEN ($($resultados.Count) run(s)) ==="
$porVeredicto = $resultados | Group-Object veredicto | Sort-Object Name
foreach ($g in $porVeredicto) { Write-Output "  $($g.Name): $($g.Count)" }
$conFallos = @($resultados | Where-Object { $_.veredicto -notmatch 'C4N_PASS' })
if ($conFallos.Count -gt 0) {
    Write-Output '  Runs con fallo o sin evidencia:'
    foreach ($d in $conFallos) {
        $malas = @($d.fases | Where-Object { $_.result -ne 'PASS' } | ForEach-Object { $_.fase })
        Write-Output "    - $($d.run): veredicto=$($d.veredicto) fases_no_pass=$($malas -join ',')"
    }
}
Write-Output "Salida: $OutDir"

if ($FailOnError -and $conFallos.Count -gt 0) { exit 1 }
exit 0
