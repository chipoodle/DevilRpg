# Test-Aceptacion.ps1 — regresion de la aceptacion C4 (sin servidor, sin tocar build/c4n).
#
# Demuestra:
#   1) los runs historicos validos (1550/1602) conservan sus criterios medibles y quedan LEGACY_NO_MEDIDO
#      (no PASS) porque no hay estampa de reconciliacion;
#   2) el run fallido 020 sigue FAIL y no se convierte en PASS por campos ausentes;
#   3) un fin.txt=C4N_PASS sin phase-*.json NO da PASS;
#   4) un JSON incompleto NO da PASS;
#   5) evidencia mezclada u obsoleta da C4N_INCONSISTENTE;
#   6) un run sintetico con aceptacion completa da C4N_PASS (prueba de la via nueva).
#
# Uso: .\tools\av\Test-Aceptacion.ps1
param([switch]$KeepFixtures)

$ErrorActionPreference = 'Stop'
$Raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$AvDir = Join-Path $Raiz 'build\av'
$TestRuns = Join-Path $AvDir 'test-runs'
$TestOut = Join-Path $AvDir 'test-out'
. (Join-Path $PSScriptRoot 'aceptacion.ps1')

$fallos = New-Object System.Collections.Generic.List[string]
function Assert-True {
    param([bool]$Condicion, [string]$Mensaje)
    if ($Condicion) {
        Write-Output "OK   $Mensaje"
    } else {
        Write-Output "FAIL $Mensaje"
        $script:fallos.Add($Mensaje)
    }
}

# ------------------------------------------------------------------ fixtures sinteticos
if (Test-Path -LiteralPath $TestRuns) { Remove-Item -LiteralPath $TestRuns -Recurse -Force }
if (Test-Path -LiteralPath $TestOut) { Remove-Item -LiteralPath $TestOut -Recurse -Force }
New-Item -ItemType Directory -Force -Path $TestRuns, $TestOut | Out-Null

function New-AceptacionCompleta {
    $criterios = @()
    foreach ($nombre in $script:AvCriteriosObligatorios) {
        $criterios += [ordered]@{ nombre = $nombre; esperado = 'x'; observado = 'x'; ok = $true; obligatorio = $true }
    }
    return [ordered]@{
        veredicto = 'PASS'; ok = $true; ownership_demostrado = $true
        ownership_nota = 'cobertura 11/11 sintetica'
        criterios = $criterios
        opcionales = @([ordered]@{ nombre = 'cobertura_estaciones_11_11'; esperado = 11; observado = 11; ok = $true; obligatorio = $false })
        fallidos = @(); cobertura_obligatoria = $false
    }
}

function New-FaseSintetica {
    param([string]$RunDir, [string]$Fase, [string[]]$UuidSet)
    $acept = New-AceptacionCompleta
    $data = [ordered]@{
        fase = $Fase; result = 'PASS'; fase_ok = $true; guardado_ok = $true
        roster_invariantes_ok = $true
        esperados = 12; presentes = 12; perdidos = 0; profesion_estable = 12
        goals_instalados = 12; reanudados = 12; activos = 12; movidos = 12; muestras = 120
        job_site_presentes = 11; job_site_huerfanos = 0; job_site_duplicados = 0
        job_site_estables = 11; job_site_cambiados = 0; job_site_perdidos = 0; job_site_nuevos = 0
        job_site_al_cargar_presentes = 11; job_site_al_cargar_duplicados = 0; job_site_al_cargar_huerfanos = 0
        uuid_set = $UuidSet
        guardias = [ordered]@{
            total = 4; fundadores = 4; fundadores_esperados = 4
            mascara_persistida = @($true, $true, $true, $true); mascara_bits = 15; mascara_completa = $true
            con_goal_guardia = 4; fundadores_con_goal = 4; fundadores_equipados = 4
            arqueros = 2; con_municion = 2; armadura_completa = 4; con_arma = 4; con_escudo = 2; detalle = @()
        }
        cobertura = [ordered]@{
            esperados = 11; cubiertos = 11; al_cargar = 11; al_cierre = 11
            nitwit_sin_estacion = $true; obligatoria = $false; faltantes = @(); detalle = @()
        }
        reconciliacion = [ordered]@{
            manager_invocado = $true; estampa_disponible = $true; estampa_reclamaciones = 11
            estampa_ultima_tick = 1300; estampa_tickets_perdidos = 0; estampa_promociones_potencial = 0
            estampa_potenciales_liberados = 0; estampa_potenciales_limpiados = 0
            mant_inicial_tick = 5; mant_periodico_ticks = 12; reclamaciones_log = 11
            reclamaciones_uuid = 11; aplazos_log = 0; tickets_perdidos_log = 0; log_leido = $true; log_error = ''; detalle = @()
        }
        aceptacion = $acept
        aceptacion_ok = $true; ownership_demostrado = $true
    }
    $data | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $RunDir "phase-$Fase.json") -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "done-$Fase.txt") -Value 'PASS' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "saved-$Fase.txt") -Value '1' -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "snapshot-$Fase.txt") -Value "# uuid|profession|pos|goal`n$($UuidSet[0])|farmer|0,0,0|installed" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "jobsite-$Fase.txt") -Value "# poi_long|poi|uuid|profesion|guardia|slot|tipo|tipo_poi`n1|0,0,0|$($UuidSet[0])|farmer|false|0|0|Optional[Reference{ResourceKey[minecraft:point_of_interest_type / minecraft:farmer]}]" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "srv-$Fase.log") -Value "[C4N] MANT_PERIODICO tick=200`n[Village] Aldea 0: $($UuidSet[0]) reclama su estacion de farmer en 0,0,0" -Encoding utf8
    Set-Content -LiteralPath (Join-Path $RunDir "diagnostico-$Fase.json") -Value (@{ run = (Split-Path -Leaf $RunDir) } | ConvertTo-Json) -Encoding utf8
}

function New-RunSintetico {
    param([string]$Nombre, [string[]]$UuidSet)
    $dir = Join-Path $TestRuns $Nombre
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Set-Content -LiteralPath (Join-Path $dir 'sha.txt') -Value ('a' * 40) -Encoding utf8
    Start-Sleep -Milliseconds 30
    foreach ($f in @('prepare', 'reload1', 'reload2')) { New-FaseSintetica $dir $f $UuidSet }
    Set-Content -LiteralPath (Join-Path $dir 'fin.txt') -Value 'C4N_PASS prepare=PASS reload1=PASS reload2=PASS' -Encoding utf8
    return $dir
}

$uuidA = @('11111111-1111-1111-1111-111111111111')
New-RunSintetico 'c4n-test-pass' $uuidA | Out-Null

# fixture: bloque de aceptacion "verde" pero cobertura 3/11 (el flag -ExigirCobertura debe cazarlo)
$dirCob = Join-Path $TestRuns 'c4n-test-cob3'
Copy-Item -LiteralPath (Join-Path $TestRuns 'c4n-test-pass') -Destination $dirCob -Recurse
foreach ($f in @('prepare', 'reload1', 'reload2')) {
    $p = Join-Path $dirCob "phase-$f.json"
    $j = Get-Content -LiteralPath $p -Raw | ConvertFrom-Json
    $j.cobertura.cubiertos = 3
    $j.aceptacion.ownership_demostrado = $false
    $j | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $p -Encoding utf8
}

# fin PASS sin phase JSON
$dir = Join-Path $TestRuns 'c4n-test-sin-fases'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Set-Content -LiteralPath (Join-Path $dir 'sha.txt') -Value ('b' * 40) -Encoding utf8
foreach ($f in @('prepare', 'reload1', 'reload2')) {
    Set-Content -LiteralPath (Join-Path $dir "done-$f.txt") -Value 'PASS' -Encoding utf8
}
Set-Content -LiteralPath (Join-Path $dir 'fin.txt') -Value 'C4N_PASS prepare=PASS reload1=PASS reload2=PASS' -Encoding utf8

# JSON incompleto
$dir = Join-Path $TestRuns 'c4n-test-incompleto'
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Set-Content -LiteralPath (Join-Path $dir 'sha.txt') -Value ('c' * 40) -Encoding utf8
foreach ($f in @('prepare', 'reload1', 'reload2')) {
    Set-Content -LiteralPath (Join-Path $dir "phase-$f.json") -Value (@{ fase = $f; result = 'PASS'; fase_ok = $true } | ConvertTo-Json) -Encoding utf8
    Set-Content -LiteralPath (Join-Path $dir "done-$f.txt") -Value 'PASS' -Encoding utf8
}
Set-Content -LiteralPath (Join-Path $dir 'fin.txt') -Value 'C4N_PASS prepare=PASS reload1=PASS reload2=PASS' -Encoding utf8

# evidencia mezclada: dos UUID-set distintos
$dir = New-RunSintetico 'c4n-test-mezclado' $uuidA
$uuidB = @('22222222-2222-2222-2222-222222222222')
$p = Get-Content -LiteralPath (Join-Path $dir 'phase-reload1.json') -Raw | ConvertFrom-Json
$p.uuid_set = $uuidB
$p | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $dir 'phase-reload1.json') -Encoding utf8

# evidencia obsoleta: phase anterior a sha
$dir = New-RunSintetico 'c4n-test-obsoleto' $uuidA
$sha = Get-Item -LiteralPath (Join-Path $dir 'sha.txt')
$ph = Get-Item -LiteralPath (Join-Path $dir 'phase-prepare.json')
$ph.LastWriteTimeUtc = $sha.LastWriteTimeUtc.AddSeconds(-30)

# ------------------------------------------------------------------ diagnostico de fixtures
function Invoke-Diag {
    param([string]$Nombre, [switch]$FailOnError, [switch]$ExigirCobertura)
    $parametros = @{ C4nDir = $TestRuns; Run = $Nombre; OutDir = $TestOut }
    if ($FailOnError) { $parametros['FailOnError'] = $true }
    if ($ExigirCobertura) { $parametros['ExigirCobertura'] = $true }
    $out = & (Join-Path $PSScriptRoot 'diagnose.ps1') @parametros 2>&1
    $code = $LASTEXITCODE
    $json = Get-Content -LiteralPath (Join-Path $TestOut "diagnostico-$Nombre.json") -Raw | ConvertFrom-Json
    return [pscustomobject]@{ exit = $code; json = $json; salida = ($out -join "`n") }
}

$r = Invoke-Diag 'c4n-test-pass' -FailOnError
Assert-True ($r.json.veredicto -eq 'C4N_PASS') "fixture con aceptacion completa -> C4N_PASS (fue $($r.json.veredicto))"
Assert-True ($r.exit -eq 0) 'fixture PASS con -FailOnError sale 0'

$rCob = Invoke-Diag 'c4n-test-cob3' -ExigirCobertura
Assert-True ($rCob.json.veredicto -ne 'C4N_PASS') "bloque verde con cobertura 3/11 + -ExigirCobertura NO es PASS (fue $($rCob.json.veredicto))"
$cobObl = @($rCob.json.fases[0].aceptacion.criterios | Where-Object { $_.nombre -eq 'cobertura_estaciones_11_11' })
Assert-True ($cobObl.Count -eq 1 -and $cobObl[0].ok -eq $false) 'el flag añade cobertura como criterio obligatorio y falla'

# regresion Qwen: fundador con armadura parcial y reconciliacion sin actividad -> derivacion en FAIL
$fixture = Get-Content -LiteralPath (Join-Path $TestRuns 'c4n-test-pass\phase-prepare.json') -Raw | ConvertFrom-Json
$fixture.PSObject.Properties.Remove('aceptacion')
$fixture.PSObject.Properties.Remove('aceptacion_ok')
$fixture.guardias.PSObject.Properties.Remove('fundadores_equipados')
$fixture.guardias.detalle = @(
    'uuid=11111111-1111-1111-1111-111111111111 slot=0 tipo=0 fundador=true goal=true armadura=4/4 mano=minecraft:wooden_sword escudo=true flechas=0 equipado=true',
    'uuid=22222222-2222-2222-2222-222222222222 slot=1 tipo=1 fundador=true goal=true armadura=4/4 mano=minecraft:bow escudo=false flechas=16 equipado=true',
    'uuid=33333333-3333-3333-3333-333333333333 slot=2 tipo=0 fundador=true goal=true armadura=4/4 mano=minecraft:wooden_sword escudo=true flechas=0 equipado=true',
    'uuid=44444444-4444-4444-4444-444444444444 slot=3 tipo=1 fundador=true goal=true armadura=3/4 mano=minecraft:bow escudo=false flechas=16 equipado=false'
)
$fixture.reconciliacion.estampa_reclamaciones = 0
$fixture.reconciliacion.estampa_ultima_tick = -1
$fixture.reconciliacion | Add-Member -NotePropertyName estampa_actividad -NotePropertyValue 0 -Force
$fixture.reconciliacion.estampa_disponible = $true
$der = Get-AvAceptacion -Data $fixture -Fase 'prepare' -RunDir (Join-Path $TestRuns 'c4n-test-pass')
$eq = @($der.criterios | Where-Object { $_.nombre -eq 'fundadores_equipados_4_4' })
$rc = @($der.criterios | Where-Object { $_.nombre -eq 'reconciliacion_manager_corrio' })
Assert-True ($eq.Count -eq 1 -and $eq[0].observado -eq 3 -and $eq[0].ok -eq $false) 'armadura parcial 3/4 -> fundadores_equipados=3 y FAIL'
Assert-True ($rc.Count -eq 1 -and $rc[0].ok -eq $false) 'estampa sin actividad -> reconciliacion_manager_corrio FAIL'
Assert-True ($der.ok -eq $false) 'la derivacion con esos fallos no es PASS'

$r = Invoke-Diag 'c4n-test-sin-fases' -FailOnError
Assert-True ($r.json.veredicto -ne 'C4N_PASS') "fin PASS sin phase JSON NO es PASS (fue $($r.json.veredicto))"
Assert-True ($r.json.fases[0].estado -eq 'EVIDENCIA_INCOMPLETA') 'fase sin phase JSON -> EVIDENCIA_INCOMPLETA'
Assert-True ($r.json.fases[0].aceptacion_medida -eq $false) 'fase sin phase JSON -> aceptacion no medida'
Assert-True ($r.exit -eq 1) 'fin PASS sin phase JSON con -FailOnError sale 1'

$r = Invoke-Diag 'c4n-test-incompleto'
Assert-True ($r.json.veredicto -ne 'C4N_PASS') "JSON incompleto NO es PASS (fue $($r.json.veredicto))"
Assert-True (@($r.json.fases[0].campos_faltantes).Count -gt 0) 'JSON incompleto reporta campos_faltantes'

$r = Invoke-Diag 'c4n-test-mezclado'
Assert-True ($r.json.veredicto -eq 'C4N_INCONSISTENTE') "UUID-set mezclado -> C4N_INCONSISTENTE (fue $($r.json.veredicto))"
Assert-True ($r.json.evidencia_mezclada -eq $true) 'marca evidencia_mezclada'

$r = Invoke-Diag 'c4n-test-obsoleto'
Assert-True ($r.json.veredicto -eq 'C4N_INCONSISTENTE') "phase anterior a sha -> C4N_INCONSISTENTE (fue $($r.json.veredicto))"
Assert-True ($r.json.evidencia_obsoleta -eq $true) 'marca evidencia_obsoleta'

# ------------------------------------------------------------------ runs reales (solo lectura)
function Invoke-Real {
    param([string]$Run, [switch]$FailOnError, [switch]$ExigirCobertura)
    $parametros = @{ Run = $Run; C4nDir = (Join-Path $Raiz 'build\c4n'); OutDir = $TestOut }
    if ($FailOnError) { $parametros['FailOnError'] = $true }
    if ($ExigirCobertura) { $parametros['ExigirCobertura'] = $true }
    $out = & (Join-Path $PSScriptRoot 'diagnose.ps1') @parametros 2>&1
    return [pscustomobject]@{ exit = $LASTEXITCODE; salida = ($out -join "`n") }
}
$r1550 = Invoke-Real 'c4n-20261123-1550' -FailOnError
$j1550 = Get-Content -LiteralPath (Join-Path $TestOut 'diagnostico-c4n-20261123-1550.json') -Raw | ConvertFrom-Json
Assert-True ($j1550.veredicto -eq 'C4N_LEGACY_NO_MEDIDO') "1550 -> C4N_LEGACY_NO_MEDIDO (fue $($j1550.veredicto))"
Assert-True ($r1550.exit -eq 1) '1550 con -FailOnError sale 1 (legacy no es PASS)'
Assert-True (@($j1550.fases | Where-Object { $_.ownership_demostrado -eq $false }).Count -eq 3) '1550 declara ownership_demostrado=false'

$r1550c = Invoke-Real 'c4n-20261123-1550' -ExigirCobertura
$j1550c = Get-Content -LiteralPath (Join-Path $TestOut 'diagnostico-c4n-20261123-1550.json') -Raw | ConvertFrom-Json
Assert-True ($j1550c.veredicto -ne 'C4N_PASS') "1550 con -ExigirCobertura NO es PASS (fue $($j1550c.veredicto))"
$cobCrit = @($j1550c.fases[0].aceptacion.criterios | Where-Object { $_.nombre -eq 'cobertura_estaciones_11_11' })
Assert-True ($cobCrit.Count -eq 1 -and $cobCrit[0].ok -eq $false) 'con -ExigirCobertura la cobertura es criterio obligatorio y falla'

$r1602 = Invoke-Real 'c4n-20261123-1602'
$j1602 = Get-Content -LiteralPath (Join-Path $TestOut 'diagnostico-c4n-20261123-1602.json') -Raw | ConvertFrom-Json
Assert-True ($j1602.veredicto -eq 'C4N_LEGACY_NO_MEDIDO') "1602 -> C4N_LEGACY_NO_MEDIDO (fue $($j1602.veredicto))"

$r020 = Invoke-Real 'c4n-20261123-020' -FailOnError
$j020 = Get-Content -LiteralPath (Join-Path $TestOut 'diagnostico-c4n-20261123-020.json') -Raw | ConvertFrom-Json
Assert-True ($j020.veredicto -eq 'C4N_FAIL') "020 sigue FAIL (fue $($j020.veredicto))"
Assert-True ($r020.exit -eq 1) '020 con -FailOnError sale 1'
Assert-True ($j020.fases[2].estado -eq 'INVARIANTE_INCUMPLIDA') '020/reload2 conserva el fallo duro (duplicado)'

# ------------------------------------------------------------------ auditoria del fuente del arnes/lanzador
$harness = Get-Content -LiteralPath (Join-Path $Raiz 'tools\c4n\C4StandaloneHarness.java') -Raw
foreach ($nombre in $script:AvCriteriosObligatorios) {
    Assert-True ($harness.Contains('"' + $nombre + '"')) "arnes declara criterio $nombre"
}
Assert-True ($harness.Contains('Integer.getInteger("c4n.trabajoTicks", 600)')) 'arnes lee c4n.trabajoTicks'
Assert-True ($harness.Contains('POTENTIAL_JOB_SITE')) 'arnes captura POTENTIAL_JOB_SITE'
Assert-True ($harness.Contains('getFreeTickets')) 'arnes captura free_tickets del POI'
Assert-True ($harness.Contains('estampaDeReconciliacion')) 'arnes usa la estampa de reconciliacion'
$launcher = Get-Content -LiteralPath (Join-Path $Raiz 'tools\c4n\lanzar-c4n.ps1') -Raw
Assert-True ($launcher.Contains('-TrabajoTicks')) 'lanzador expone -TrabajoTicks'
Assert-True ($launcher.Contains('c4n.trabajoTicks')) 'lanzador pasa c4n.trabajoTicks'
Assert-True ($launcher.Contains('c4n.exigirCobertura')) 'lanzador pasa c4n.exigirCobertura'

# ------------------------------------------------------------------ resumen
Write-Output ''
if ($script:fallos.Count -eq 0) {
    Write-Output 'TEST_ACEPTACION: OK'
    $salida = 0
} else {
    Write-Output "TEST_ACEPTACION: FALLOS=$($script:fallos.Count)"
    foreach ($f in $script:fallos) { Write-Output "  - $f" }
    $salida = 1
}
if (-not $KeepFixtures -and (Test-Path -LiteralPath $TestRuns)) {
    # Solo se borra el arbol de fixtures de build/av; jamas build/c4n.
    $resuelto = (Resolve-Path -LiteralPath $TestRuns).Path
    if ($resuelto.StartsWith($AvDir, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resuelto -Recurse -Force
    }
}
exit $salida