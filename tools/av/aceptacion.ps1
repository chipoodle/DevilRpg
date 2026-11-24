# aceptacion.ps1 — evaluacion de aceptacion C4 compartida por diagnose.ps1 y collect.ps1.
#
# REGLA: la falta de evidencia NO es PASS. Un criterio obligatorio sin observado medible queda
# `medido=false` y el veredicto nunca es PASS. Los criterios opcionales se documentan aparte y no
# cambian el veredicto.
#
# Este fichero es puro (no lee build/c4n salvo los artefactos del run que se le pasa) y no modifica nada.

$script:AvPuestosContrato = 12
$script:AvFundadoresContrato = 4

$script:AvCriteriosObligatorios = @(
    'censo_profesionales_12_12',
    'profesionales_presentes_12_12',
    'fundadores_4_4',
    'uuid_duplicados_0',
    'perdidas_aldeanos_0',
    'job_site_duplicados_0',
    'job_site_huerfanos_0',
    'mascara_fundadores_0b1111',
    'fundadores_con_goal_4_4',
    'fundadores_equipados_4_4',
    'guardado_ok',
    'roster_invariantes_ok',
    'identidad_profesion_12_12',
    'goals_profesionales_12_12',
    'reanudados_12_12',
    'reconciliacion_manager_corrio'
)

$script:AvCriteriosOpcionales = @(
    'cobertura_estaciones_11_11',
    'reclamaciones_en_log',
    'reclamaciones_contador',
    'guardias_total',
    'municion_arqueros',
    'job_site_al_cargar_duplicados_0',
    'job_site_al_cargar_huerfanos_0'
)

function Get-AvField {
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

function ConvertTo-AvInt {
    param($Valor)
    if ($null -eq $Valor) { return $null }
    if ($Valor -is [bool]) { return $null }
    try { return [int]$Valor } catch { return $null }
}

function ConvertTo-AvBool {
    param($Valor)
    if ($null -eq $Valor) { return $null }
    if ($Valor -is [bool]) { return $Valor }
    $texto = ([string]$Valor).Trim().ToLowerInvariant()
    if ($texto -eq 'true') { return $true }
    if ($texto -eq 'false') { return $false }
    return $null
}

function New-AvCriterio {
    param([string]$Nombre, $Esperado, $Observado, $Ok, [bool]$Obligatorio = $true, [string]$Origen = 'derivado')
    [pscustomobject][ordered]@{
        nombre      = $Nombre
        esperado    = $Esperado
        observado   = $Observado
        ok          = $Ok
        obligatorio = $Obligatorio
        origen      = $Origen
    }
}

function Get-AvBinario {
    param([int]$Bits)
    $texto = [Convert]::ToString(($Bits -band 15), 2)
    return ('0' * (4 - $texto.Length)) + $texto
}

function Get-AvGuardiasDerivado {
    param($Guardias)
    if ($null -eq $Guardias) {
        return [pscustomobject][ordered]@{
            medido = $false; fundadores = $null; total = $null; conGoal = $null
            fundadoresConGoal = $null; fundadoresEquipados = $null; arqueros = $null
            conMunicion = $null; mascaraBits = $null; detalle = @()
        }
    }
    $total = ConvertTo-AvInt (Get-AvField $Guardias 'total')
    $fundadores = ConvertTo-AvInt (Get-AvField $Guardias 'fundadores')
    $conGoal = ConvertTo-AvInt (Get-AvField $Guardias 'con_goal_guardia')
    $detalle = @(Get-AvField $Guardias 'detalle' @())

    $fundadoresConGoal = ConvertTo-AvInt (Get-AvField $Guardias 'fundadores_con_goal')
    if ($null -eq $fundadoresConGoal -and $null -ne $fundadores -and $null -ne $total -and $total -eq $fundadores) {
        # En la guardia de fundacion todos los guardias son fundadores.
        $fundadoresConGoal = $conGoal
    }
    if ($null -eq $fundadoresConGoal -and $detalle.Count -gt 0) {
        $n = 0
        $vistos = 0
        foreach ($linea in $detalle) {
            $texto = [string]$linea
            if ($texto -match 'fundador=true') {
                $vistos++
                if ($texto -match 'goal=true') { $n++ }
            }
        }
        if ($vistos -gt 0) { $fundadoresConGoal = $n }
    }

    $fundadoresEquipados = ConvertTo-AvInt (Get-AvField $Guardias 'fundadores_equipados')
    if ($null -eq $fundadoresEquipados -and $detalle.Count -gt 0) {
        $n = 0
        $vistos = 0
        $parseOk = $true
        foreach ($linea in $detalle) {
            $texto = [string]$linea
            if ($texto -notmatch 'fundador=true') { continue }
            $vistos++
            $mTipo = [regex]::Match($texto, 'tipo=(\d+)')
            $mArm = [regex]::Match($texto, 'armadura=(\d+)/4')
            $mMano = [regex]::Match($texto, 'mano=([^ ]+)')
            $mEsc = [regex]::Match($texto, 'escudo=(true|false)')
            if (-not $mTipo.Success -or -not $mArm.Success -or -not $mMano.Success -or -not $mEsc.Success) {
                $parseOk = $false
                break
            }
            $tipo = [int]$mTipo.Groups[1].Value
            $armadura = [int]$mArm.Groups[1].Value
            $mano = $mMano.Groups[1].Value
            $escudo = $mEsc.Groups[1].Value -eq 'true'
            if ($armadura -ne 4) { continue }
            if ($tipo -eq 1) {
                if ($mano -eq 'minecraft:bow') { $n++ }
            } elseif ($tipo -eq 0) {
                if ($mano -ne 'none' -and $escudo) { $n++ }
            } else {
                $parseOk = $false
                break
            }
        }
        if ($parseOk -and $vistos -gt 0) { $fundadoresEquipados = $n }
    }

    $mascaraBits = ConvertTo-AvInt (Get-AvField $Guardias 'mascara_bits')
    if ($null -eq $mascaraBits) {
        $mascaraCompleta = ConvertTo-AvBool (Get-AvField $Guardias 'mascara_completa')
        if ($mascaraCompleta -eq $true) { $mascaraBits = 15 }
    }
    if ($null -eq $mascaraBits) {
        $persistida = @(Get-AvField $Guardias 'mascara_persistida' @())
        if ($persistida.Count -eq 4) {
            $bits = 0
            for ($i = 0; $i -lt 4; $i++) {
                if (ConvertTo-AvBool $persistida[$i]) { $bits = $bits -bor (1 -shl $i) }
            }
            $mascaraBits = $bits
        }
    }

    $medido = ($null -ne $fundadores) -and ($null -ne $mascaraBits)
    return [pscustomobject][ordered]@{
        medido             = $medido
        fundadores         = $fundadores
        total              = $total
        conGoal            = $conGoal
        fundadoresConGoal  = $fundadoresConGoal
        fundadoresEquipados = $fundadoresEquipados
        arqueros           = ConvertTo-AvInt (Get-AvField $Guardias 'arqueros')
        conMunicion        = ConvertTo-AvInt (Get-AvField $Guardias 'con_municion')
        mascaraBits        = $mascaraBits
        detalle            = $detalle
    }
}

function Get-AvCoberturaDerivada {
    param([string]$RunDir, [string]$Fase, [int]$Puestos = 12)
    $snap = Join-Path $RunDir "snapshot-$Fase.txt"
    $job = Join-Path $RunDir "jobsite-$Fase.txt"
    if (-not (Test-Path -LiteralPath $snap -PathType Leaf) -or -not (Test-Path -LiteralPath $job -PathType Leaf)) {
        return [pscustomobject][ordered]@{
            medido = $false; esperados = $null; cubiertos = $null; alCargar = $null
            origen = 'sin_snapshot_o_jobsite'; faltantes = @(); detalle = @(); nitwitSinEstacion = $true
        }
    }
    $estaciones = @{
        'farmer' = 'farmer'; 'fisherman' = 'fisherman'; 'shepherd' = 'shepherd'; 'butcher' = 'butcher'
        'cleric' = 'cleric'; 'weaponsmith' = 'weaponsmith'; 'toolsmith' = 'toolsmith'
        'mason' = 'mason'; 'fletcher' = 'fletcher'
    }
    $porUuid = @{}
    foreach ($linea in @(Get-Content -LiteralPath $job -ErrorAction SilentlyContinue)) {
        if ([string]::IsNullOrWhiteSpace($linea) -or $linea.StartsWith('#')) { continue }
        $partes = $linea.Split('|')
        if ($partes.Length -lt 8) { continue }
        $porUuid[$partes[2]] = [pscustomobject]@{ prof = $partes[3]; tipoPoi = $partes[7] }
    }
    $esperados = 0
    $cubiertos = 0
    $faltantes = New-Object System.Collections.Generic.List[string]
    $detalle = New-Object System.Collections.Generic.List[string]
    foreach ($linea in @(Get-Content -LiteralPath $snap -ErrorAction SilentlyContinue)) {
        if ([string]::IsNullOrWhiteSpace($linea) -or $linea.StartsWith('#')) { continue }
        $partes = $linea.Split('|')
        if ($partes.Length -lt 2) { continue }
        $uuid = $partes[0]
        $prof = $partes[1]
        if (-not $estaciones.ContainsKey($prof)) { continue }
        $esperados++
        $fila = $porUuid[$uuid]
        $cubierto = $false
        if ($null -ne $fila -and $fila.tipoPoi -match ([regex]::Escape('minecraft:' + $estaciones[$prof]))) {
            $cubierto = $true
            $cubiertos++
        } else {
            $faltantes.Add("prof=$prof uuid=$uuid motivo=" + $(if ($null -eq $fila) { 'sin_job_site' } else { 'poi_no_corresponde' }))
        }
        $detalle.Add("uuid=$uuid prof=$prof tipo_poi=" + $(if ($null -eq $fila) { 'none' } else { $fila.tipoPoi }) + " cubierto=$cubierto")
    }
    return [pscustomobject][ordered]@{
        medido           = ($esperados -gt 0)
        esperados        = $esperados
        cubiertos        = $cubiertos
        alCargar         = $null
        origen           = 'derivado_de_snapshot_y_jobsite'
        faltantes        = @($faltantes)
        detalle          = @($detalle)
        nitwitSinEstacion = $true
    }
}

function Get-AvReconciliacionDerivada {
    param([string]$RunDir, [string]$Fase, $Data)
    $recon = Get-AvField $Data 'reconciliacion'
    $logPath = Join-Path $RunDir "srv-$Fase.log"
    $reclamaLog = 0
    $mantLog = 0
    if (Test-Path -LiteralPath $logPath -PathType Leaf) {
        try {
            $reclamaLog = @(Select-String -LiteralPath $logPath -Pattern 'reclama su estacion de' -ErrorAction Stop).Count
            $mantLog = @(Select-String -LiteralPath $logPath -Pattern 'MANT_INICIAL|MANT_PERIODICO|prepare centro=' -ErrorAction Stop).Count
        } catch { }
    }
    if ($null -ne $recon) {
        $estampa = ConvertTo-AvBool (Get-AvField $recon 'estampa_disponible')
        $reclamos = ConvertTo-AvInt (Get-AvField $recon 'estampa_reclamaciones')
        $ultima = ConvertTo-AvInt (Get-AvField $recon 'estampa_ultima_tick')
        $inicioUltima = ConvertTo-AvInt (Get-AvField $recon 'estampa_inicio_ultima_tick')
        $actividad = ConvertTo-AvInt (Get-AvField $recon 'estampa_actividad')
        if ($null -eq $actividad) {
            $suma = 0
            foreach ($campo in @('estampa_reclamaciones', 'estampa_tickets_perdidos', 'estampa_promociones_potencial',
                    'estampa_potenciales_liberados', 'estampa_potenciales_limpiados',
                    'estampa_puestos_invalidos_limpiados')) {
                $v = ConvertTo-AvInt (Get-AvField $recon $campo)
                if ($null -ne $v) { $suma += $v }
            }
            $actividad = $suma
        }
        $medido = ($estampa -eq $true) -and ($null -ne $reclamos)
        $efectiva = $null
        if ($medido) {
            $avanceUltima = ($null -ne $ultima -and $null -ne $inicioUltima -and $ultima -gt $inicioUltima)
            $efectiva = ($reclamos -gt 0) -or $avanceUltima -or ($null -ne $actividad -and $actividad -gt 0)
        }
        return [pscustomobject][ordered]@{
            medido        = $medido
            efectiva      = $efectiva
            origen        = 'fase_json'
            estampa       = ($estampa -eq $true)
            reclamaciones = $reclamos
            ultimaTick    = $ultima
            inicioUltimaTick = $inicioUltima
            actividad     = $actividad
            reclamacionesLog = $reclamaLog
            mantenimientoLog = $mantLog
            managerInvocado  = ConvertTo-AvBool (Get-AvField $recon 'manager_invocado')
            detalle       = @(Get-AvField $recon 'detalle' @())
        }
    }
    # Run historico: NO hay estampa. La invocacion del mantenimiento y las lineas de log se registran,
    # pero la reconciliacion EFFECTIVA queda NO_MEDIDA (no se convierte en PASS).
    return [pscustomobject][ordered]@{
        medido        = $false
        efectiva      = $null
        origen        = 'historico_sin_estampa'
        estampa       = $false
        reclamaciones = $null
        ultimaTick    = $null
        inicioUltimaTick = $null
        actividad     = $null
        reclamacionesLog = $reclamaLog
        mantenimientoLog = $mantLog
        managerInvocado  = ($mantLog -gt 0)
        detalle       = @()
    }
}

function Get-AvAceptacion {
    param($Data, [string]$Fase, [string]$RunDir, [switch]$ExigirCobertura)

    $puestos = $script:AvPuestosContrato
    $fundadoresEsp = $script:AvFundadoresContrato
    $bloque = Get-AvField $Data 'aceptacion'

    if ($null -ne $bloque) {
        $raw = @(Get-AvField $bloque 'criterios' @())
        $rawOpc = @(Get-AvField $bloque 'opcionales' @())
        $crit = New-Object System.Collections.Generic.List[object]
        $opc = New-Object System.Collections.Generic.List[object]
        $faltantes = New-Object System.Collections.Generic.List[string]
        $invalido = $null
        foreach ($c in $raw) {
            $nombre = [string](Get-AvField $c 'nombre' '')
            $esperado = Get-AvField $c 'esperado'
            $observado = Get-AvField $c 'observado'
            $ok = ConvertTo-AvBool (Get-AvField $c 'ok')
            if ([string]::IsNullOrWhiteSpace($nombre) -or $null -eq $esperado -or $null -eq $observado -or $null -eq $ok) {
                $invalido = "criterio_incompleto:$nombre"
                break
            }
            $oblig = ConvertTo-AvBool (Get-AvField $c 'obligatorio' $true)
            if ($null -eq $oblig) { $oblig = $true }
            $crit.Add((New-AvCriterio $nombre $esperado $observado $ok $oblig 'fase_json'))
        }
        foreach ($c in $rawOpc) {
            $nombre = [string](Get-AvField $c 'nombre' '')
            $ok = ConvertTo-AvBool (Get-AvField $c 'ok')
            if (-not [string]::IsNullOrWhiteSpace($nombre)) {
                $opc.Add((New-AvCriterio $nombre (Get-AvField $c 'esperado') (Get-AvField $c 'observado') $ok $false 'fase_json'))
            }
        }
        $agregoCobertura = $false
        if ($ExigirCobertura) {
            $tieneCobertura = @($crit | Where-Object { $_.nombre -eq 'cobertura_estaciones_11_11' }).Count -gt 0
            if (-not $tieneCobertura) {
                $cobData = Get-AvField $Data 'cobertura'
                $cub = ConvertTo-AvInt (Get-AvField $cobData 'cubiertos')
                $esp = ConvertTo-AvInt (Get-AvField $cobData 'esperados')
                if ($null -ne $cub -and $null -ne $esp) {
                    $crit.Add((New-AvCriterio 'cobertura_estaciones_11_11' $esp $cub ($cub -eq $esp) $true 'fase_json'))
                } else {
                    $crit.Add((New-AvCriterio 'cobertura_estaciones_11_11' 11 'NO_MEDIDO' $false $true 'fase_json'))
                }
                $agregoCobertura = $true
            }
        }
        if ($null -ne $invalido) {
            return [pscustomobject][ordered]@{
                fase = $Fase; origen = 'fase_json'; medido = $false; ok = $false
                criterios = @($crit); opcionales = @($opc); faltantes = @($invalido)
                inconsistente = $true; error = $invalido; ownershipDemostrado = $false
                cobertura = $null; reconciliacion = $null
            }
        }
        $mapa = @{}
        foreach ($c in $crit) { if ($c.obligatorio) { $mapa[$c.nombre] = $c } }
        foreach ($nombre in $script:AvCriteriosObligatorios) {
            if (-not $mapa.ContainsKey($nombre)) { $faltantes.Add($nombre) }
        }
        $okTodo = ($faltantes.Count -eq 0)
        foreach ($c in $mapa.Values) { if (-not $c.ok) { $okTodo = $false } }
        $medido = ($faltantes.Count -eq 0)
        $decOk = ConvertTo-AvBool (Get-AvField $bloque 'ok')
        $inconsistente = $false
        if (-not $agregoCobertura -and $null -ne $decOk -and $decOk -ne $okTodo) { $inconsistente = $true; $okTodo = $false }
        $coberturaDemostrada = ConvertTo-AvBool (Get-AvField $bloque 'cobertura_demostrada')
        if ($null -eq $coberturaDemostrada) {
            $coberturaDemostrada = ConvertTo-AvBool (Get-AvField $bloque 'ownership_demostrado')
        }
        if ($null -eq $coberturaDemostrada) { $coberturaDemostrada = $false }
        $cobertura = Get-AvField $Data 'cobertura'
        if ($ExigirCobertura -and $null -ne $cobertura) {
            $cubOwn = ConvertTo-AvInt (Get-AvField $cobertura 'cubiertos')
            $espOwn = ConvertTo-AvInt (Get-AvField $cobertura 'esperados')
            if ($null -ne $cubOwn -and $null -ne $espOwn) { $coberturaDemostrada = ($cubOwn -eq $espOwn) }
        }
        $recon = Get-AvReconciliacionDerivada $RunDir $Fase $Data
        return [pscustomobject][ordered]@{
            fase = $Fase; origen = 'fase_json'; medido = $medido; ok = ($okTodo -and $medido)
            criterios = $crit.ToArray(); opcionales = $opc.ToArray(); faltantes = $faltantes.ToArray()
            inconsistente = $inconsistente; error = ''; ownershipDemostrado = $false
            coberturaDemostrada = $coberturaDemostrada
            cobertura = $cobertura; reconciliacion = $recon
        }
    }

    # --- derivacion desde campos planos/observados (runs historicos) -----------------------------
    $esp = Get-AvField $Data 'esperado'
    $obs = Get-AvField $Data 'observado'
    $inv = Get-AvField $Data 'invariantes'
    $g = Get-AvGuardiasDerivado (Get-AvField $Data 'guardias')
    $cobertura = Get-AvCoberturaDerivada $RunDir $Fase $puestos
    $recon = Get-AvReconciliacionDerivada $RunDir $Fase $Data
    $alCargar = Get-AvField $Data 'cobertura'

    if ($null -ne $alCargar) {
        $cub = ConvertTo-AvInt (Get-AvField $alCargar 'cubiertos')
        if ($null -ne $cub) {
            $cobertura.cubiertos = $cub
            $cobertura.esperados = ConvertTo-AvInt (Get-AvField $alCargar 'esperados')
            $cobertura.alCargar = ConvertTo-AvInt (Get-AvField $alCargar 'al_cargar')
            $cobertura.origen = 'fase_json'
        }
    }

    $presentes = ConvertTo-AvInt (Get-AvField $obs 'presentes' (Get-AvField $Data 'presentes'))
    $censo = ConvertTo-AvInt (Get-AvField $Data 'esperados' (Get-AvField $obs 'profesionales'))
    $perdidos = ConvertTo-AvInt (Get-AvField $obs 'perdidos' (Get-AvField $Data 'perdidos'))
    $profEstable = ConvertTo-AvInt (Get-AvField $obs 'profesion_estable' (Get-AvField $Data 'profesion_estable'))
    $goals = ConvertTo-AvInt (Get-AvField $obs 'goals_instalados' (Get-AvField $Data 'goals_instalados'))
    $reanudados = ConvertTo-AvInt (Get-AvField $obs 'reanudados' (Get-AvField $Data 'reanudados'))
    $dup = ConvertTo-AvInt (Get-AvField $obs 'job_site_duplicados' (Get-AvField $Data 'job_site_duplicados'))
    $huer = ConvertTo-AvInt (Get-AvField $obs 'job_site_huerfanos' (Get-AvField $Data 'job_site_huerfanos'))

    $fundadores = $g.fundadores
    if ($null -eq $fundadores) { $fundadores = ConvertTo-AvInt (Get-AvField $obs 'fundadores') }
    $guardadoOk = ConvertTo-AvBool (Get-AvField $Data 'guardado_ok' (Get-AvField $obs 'guardado_ok'))
    $rosterOk = ConvertTo-AvBool (Get-AvField $Data 'roster_invariantes_ok' (Get-AvField $inv 'roster_invariantes_ok'))
    $uuidDuplicados = ConvertTo-AvInt (Get-AvField $Data 'uuid_duplicados')
    if ($null -eq $uuidDuplicados -and $null -ne $rosterOk) {
        $viol = @(Get-AvField $inv 'roster_violaciones' @())
        $uuidDuplicados = @($viol | Where-Object { "$_" -like 'uuid_duplicado:*' }).Count
    }

    $criterios = New-Object System.Collections.Generic.List[object]
    $opcionales = New-Object System.Collections.Generic.List[object]
    $faltantes = New-Object System.Collections.Generic.List[string]

    function Add-Obligatorio($nombre, $esperado, $observado, $ok) {
        if ($null -eq $observado -or $ok -eq $null) {
            $criterios.Add((New-AvCriterio $nombre $esperado 'NO_MEDIDO' $null $true 'derivado'))
            $faltantes.Add($nombre)
        } else {
            $criterios.Add((New-AvCriterio $nombre $esperado $observado $ok $true 'derivado'))
        }
    }

    Add-Obligatorio 'censo_profesionales_12_12' $puestos $censo ($censo -eq $puestos)
    Add-Obligatorio 'profesionales_presentes_12_12' $puestos $presentes ($presentes -eq $puestos)
    Add-Obligatorio 'fundadores_4_4' $fundadoresEsp $fundadores ($fundadores -eq $fundadoresEsp)
    Add-Obligatorio 'uuid_duplicados_0' 0 $uuidDuplicados ($uuidDuplicados -eq 0)
    Add-Obligatorio 'perdidas_aldeanos_0' 0 $perdidos ($perdidos -eq 0)
    Add-Obligatorio 'job_site_duplicados_0' 0 $dup ($dup -eq 0)
    Add-Obligatorio 'job_site_huerfanos_0' 0 $huer ($huer -eq 0)
    $mascaraObservada = $null
    $mascaraOk = $null
    if ($null -ne $g.mascaraBits) {
        $mascaraObservada = '0b' + (Get-AvBinario $g.mascaraBits) + " ($($g.mascaraBits))"
        $mascaraOk = ($g.mascaraBits -eq 15)
    }
    Add-Obligatorio 'mascara_fundadores_0b1111' '0b1111' $mascaraObservada $mascaraOk
    Add-Obligatorio 'fundadores_con_goal_4_4' $fundadoresEsp $g.fundadoresConGoal ($g.fundadoresConGoal -eq $fundadoresEsp)
    Add-Obligatorio 'fundadores_equipados_4_4' $fundadoresEsp $g.fundadoresEquipados ($g.fundadoresEquipados -eq $fundadoresEsp)
    Add-Obligatorio 'guardado_ok' $true $guardadoOk ($guardadoOk -eq $true)
    Add-Obligatorio 'roster_invariantes_ok' $true $rosterOk ($rosterOk -eq $true)
    Add-Obligatorio 'identidad_profesion_12_12' $puestos $profEstable ($profEstable -eq $puestos)
    Add-Obligatorio 'goals_profesionales_12_12' $puestos $goals ($goals -eq $puestos)
    Add-Obligatorio 'reanudados_12_12' $puestos $reanudados ($reanudados -eq $puestos)

    $reconOk = $recon.efectiva
    $reconObs = 'NO_MEDIDO (historico sin estampa)'
    if ($recon.medido) {
        $reconObs = "estampa=true, reclamaciones=$($recon.reclamaciones), ultima_tick=$($recon.ultimaTick)"
    } elseif ($recon.origen -eq 'fase_json') {
        $reconObs = 'NO_MEDIDO (estampa no disponible)'
    }
    if ($null -ne $reconOk) {
        $criterios.Add((New-AvCriterio 'reconciliacion_manager_corrio' $true $reconObs ([bool]$reconOk) $true 'derivado'))
    } else {
        $criterios.Add((New-AvCriterio 'reconciliacion_manager_corrio' $true $reconObs $null $true 'derivado'))
        $faltantes.Add('reconciliacion_manager_corrio')
    }

    $cobOk = $null
    if ($null -ne $cobertura.cubiertos -and $null -ne $cobertura.esperados) {
        $cobOk = ($cobertura.cubiertos -eq $cobertura.esperados)
        $cobCriterio = New-AvCriterio 'cobertura_estaciones_11_11' $cobertura.esperados $cobertura.cubiertos `
            ([bool]$cobOk) ([bool]$ExigirCobertura) 'derivado'
        if ($ExigirCobertura) { $criterios.Add($cobCriterio) } else { $opcionales.Add($cobCriterio) }
    } else {
        $cobCriterio = New-AvCriterio 'cobertura_estaciones_11_11' 11 'NO_MEDIDO' $null ([bool]$ExigirCobertura) 'derivado'
        if ($ExigirCobertura) { $criterios.Add($cobCriterio); $faltantes.Add('cobertura_estaciones_11_11') }
        else { $opcionales.Add($cobCriterio) }
    }

    $opcionales.Add((New-AvCriterio 'reclamaciones_en_log' '>=1' $recon.reclamacionesLog ($recon.reclamacionesLog -gt 0) $false 'derivado'))
    $opcionales.Add((New-AvCriterio 'reclamaciones_contador' '>=1' $recon.reclamaciones $($null -ne $recon.reclamaciones -and $recon.reclamaciones -gt 0) $false 'derivado'))
    $opcionales.Add((New-AvCriterio 'guardias_total' $fundadoresEsp $g.total ($g.total -eq $fundadoresEsp) $false 'derivado'))
    $opcionales.Add((New-AvCriterio 'municion_arqueros' $g.arqueros $g.conMunicion ($null -ne $g.arqueros -and $g.conMunicion -eq $g.arqueros) $false 'derivado'))
    $opcionales.Add((New-AvCriterio 'job_site_al_cargar_duplicados_0' 0 (Get-AvField $Data 'job_site_al_cargar_duplicados' 'NO_MEDIDO') `
        ((ConvertTo-AvInt (Get-AvField $Data 'job_site_al_cargar_duplicados')) -eq 0) $false 'derivado'))
    $opcionales.Add((New-AvCriterio 'job_site_al_cargar_huerfanos_0' 0 (Get-AvField $Data 'job_site_al_cargar_huerfanos' 'NO_MEDIDO') `
        ((ConvertTo-AvInt (Get-AvField $Data 'job_site_al_cargar_huerfanos')) -eq 0) $false 'derivado'))
    $medido = ($faltantes.Count -eq 0)
    $okTodo = $medido
    foreach ($c in $criterios) { if ($c.obligatorio -and $c.ok -ne $true) { $okTodo = $false } }
    $coberturaDemostrada = ($null -ne $cobertura.cubiertos -and $null -ne $cobertura.esperados -and $cobertura.cubiertos -eq $cobertura.esperados)
    $salida = [ordered]@{
        fase = $Fase; origen = 'derivado'; medido = $medido; ok = ($okTodo -and $medido)
        criterios = $criterios.ToArray(); opcionales = $opcionales.ToArray(); faltantes = $faltantes.ToArray()
        inconsistente = $false; error = ''; ownershipDemostrado = $false
        coberturaDemostrada = $coberturaDemostrada
        cobertura = $cobertura; reconciliacion = $recon
    }
    return [pscustomobject]$salida
}



