# lanzar-poi-harness.ps1 — ARNÉS DE PERSISTENCIA REAL DE POI/TICKETS (ServerLevel + PoiManager)
#
# Abre un mundo DESECHABLE (`build/av/<run>/game`), lo guarda y lo vuelve a abrir en OTRO JVM, en 4 fases:
#   prepare  → mundo virgen, aldea generada, ventana de gracia cruzada, sonda de tickets, fixtures, guardado
#   reload1  → reapertura con el chunk de un aldeano TARDÍO sin cargar: round-trip, carga parcial, reconciliación
#   reload2  → segunda recarga completa: round-trip otra vez e idempotencia
#   partial  → carga parcial agresiva (un solo chunk): ¿se liberan tickets de dueños aún no cargados?
#
# NO lanza el cliente ni toca run/saves del jugador: usa -Pa2nGameDirectory dentro de build/av.
# Compila SOLO el arnés (`-PpoiHarness=true` excluye todo debug/** salvo PoiPersistenceHarness/Report).
#
# Uso:
#   .\tools\poi\lanzar-poi-harness.ps1
#   .\tools\poi\lanzar-poi-harness.ps1 -Run poi-20261124-1200 -TimeoutMinutes 25
#   .\tools\poi\lanzar-poi-harness.ps1 -Fases prepare,reload1    # subconjunto (para depurar)
#   .\tools\poi\lanzar-poi-harness.ps1 -SinSelftest              # sin el autotest puro previo
#
# Salida: build/av/<run>/{sha.txt,git-status.txt,srv-<fase>.log,phase-<fase>.json,pois-<fase>.txt,
#         duenios-<fase>.txt,puestos-<fase>.txt,snapshot-<fase>.txt,eventos-<fase>.txt,
#         tickets-<fase>.txt,fixtures-<fase>.txt,saveddata-<fase>.txt,poi-dump.json,fin.txt}
#
# Veredicto:
#   POI_PASS        todas las fases: experimento_ok Y contrato_ok
#   POI_HALLAZGOS   todas las fases con experimento_ok, pero algún contrato_ok=false (defecto expuesto;
#                   se listan los códigos). Un run así es un RESULTADO VÁLIDO, no un fallo del arnés.
#   POI_FALLO       alguna fase no pudo medir (timeout, guardado fallido, JSON/marcador incoherente…)

param(
    [string]$Run = "",
    [int]$TimeoutMinutes = 20,
    [string[]]$Fases = @('prepare', 'reload1', 'reload2', 'partial'),
    [switch]$SinSelftest
)

$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if ([string]::IsNullOrWhiteSpace($Run)) {
    $Run = "poi-" + (Get-Date -Format 'yyyyMMdd-HHmmss')
}
$base = Join-Path $raiz "build/av/$Run"
New-Item -ItemType Directory -Force -Path $base | Out-Null
$gameDir = Join-Path $base 'game'
New-Item -ItemType Directory -Force -Path $gameDir | Out-Null
$sha = (& git rev-parse HEAD).Trim()
Set-Content -Path (Join-Path $base 'sha.txt') -Value $sha -Encoding UTF8
# El árbol de trabajo puede tener cambios de otros agentes (p.ej. reconciliación C4 en VillageManager):
# se deja constancia para que la evidencia diga EXACTAMENTE qué código se midió.
$gitStatus = (& git status --porcelain | Out-String).Trim()
$gitDiffStat = (& git diff --stat | Out-String).Trim()
Set-Content -Path (Join-Path $base 'git-status.txt') -Value ($gitStatus + "`n`n--- git diff --stat ---`n" + $gitDiffStat) -Encoding UTF8

Write-Output "=== POI run=$Run sha=$sha : $(Get-Date -Format 'HH:mm:ss') ==="
Write-Output "  juego: $gameDir (desechable; mismas 4 fases sobre el MISMO mundo)"
Write-Output "  fases: $($Fases -join ', ')"
if ($gitStatus) {
    Write-Output "  AVISO: working tree con cambios (ver git-status.txt); la evidencia no es HEAD limpio."
}

# ------------------------------------------------------------------ aislamiento de clases de debug
# `-PpoiHarness=true` EXCLUYE todo debug/** salvo nuestro arnés, pero Gradle NO borra clases viejas de
# compilaciones anteriores (C4, OwnershipProbe, A2NavHarness...). Una C4StandaloneHarness.class vieja en
# build/classes halta el servidor en ServerStartedEvent. Se limpia SOLO el directorio de clases de debug
# (salida de build, regenerable; NO se toca build/av ni build/c4n ni run/). El siguiente compile lo recrea
# únicamente con las clases del arnés POI.
$debugOut = Join-Path $raiz 'build/classes/java/main/com/chipoodle/devilrpg/debug'
if (Test-Path -LiteralPath $debugOut) {
    Remove-Item -LiteralPath $debugOut -Recurse -Force
    Write-Output "  clases debug viejas eliminadas (aislamiento): $debugOut"
}
# OJO: borrar el directorio de clases y dejar que Gradle compile "incremental" puede restaurar SOLO
# las clases de los fuentes que changed (nos pasó: PoiPersistenceHarness.class sí, PoiPersistenceReport.class
# no -> NoClassDefFoundError en runtime). Se borra el estado incremental y se fuerza una compilación
# completa SIN `--rerun-tasks` (que borra build/classes/java/main entero y choca con otros builds).
$incremental = Join-Path $raiz 'build/tmp/compileJava'
if (Test-Path -LiteralPath $incremental) {
    Remove-Item -LiteralPath $incremental -Recurse -Force
}
Write-Output "  recompilando main completo (sin estado incremental) para restaurar TODAS las clases del arnés..."
& (Join-Path $raiz 'gradlew.bat') compileJava '-Pa2nDiagnostics=true' '-PpoiHarness=true' '--console=plain' 2>&1 |
    ForEach-Object { Write-Output "    $_" }
if ($LASTEXITCODE -ne 0) {
    Write-Output 'FALLO: no compila el arnés POI; no se lanza el servidor.'
    Set-Content -Path (Join-Path $base 'fin.txt') -Value 'POI_FALLO compilacion=FAIL' -Encoding UTF8
    exit 4
}
$claseReport = Join-Path $debugOut 'PoiPersistenceReport.class'
$claseHarness = Join-Path $debugOut 'PoiPersistenceHarness.class'
if (-not (Test-Path -LiteralPath $claseReport) -or -not (Test-Path -LiteralPath $claseHarness)) {
    Write-Output "FALLO: faltan clases del arnés tras compilar ($claseReport / $claseHarness)."
    Set-Content -Path (Join-Path $base 'fin.txt') -Value 'POI_FALLO compilacion=CLASES_AUSENTES' -Encoding UTF8
    exit 4
}

# ------------------------------------------------------------------ autotest puro (sin Minecraft)
function Get-JavaTool([string]$nombre) {
    $cmd = Get-Command $nombre -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    if ($env:JAVA_HOME) {
        $cand = Join-Path $env:JAVA_HOME "bin/$nombre.exe"
        if (Test-Path -LiteralPath $cand) { return $cand }
    }
    return $null
}

if (-not $SinSelftest) {
    $javac = Get-JavaTool 'javac'
    $java = Get-JavaTool 'java'
    if (-not $javac -or -not $java) {
        Write-Output '  autotest puro: OMITIDO (no hay javac/java en PATH ni JAVA_HOME)'
    } else {
        $out = Join-Path $base 'selftest-out'
        New-Item -ItemType Directory -Force -Path $out | Out-Null
        & $javac -encoding UTF-8 -d $out (Join-Path $raiz 'tools/poi/PoiPersistenceReport.java') 2>&1 |
            ForEach-Object { Write-Output "    javac: $_" }
        if ($LASTEXITCODE -ne 0) {
            Write-Output "FALLO: el autotest puro no compila; no se lanza el servidor."
            Set-Content -Path (Join-Path $base 'fin.txt') -Value 'POI_FALLO autotest=NO_COMPILA' -Encoding UTF8
            exit 4
        }
        $salida = & $java -cp $out 'com.chipoodle.devilrpg.debug.PoiPersistenceReport' 2>&1
        $texto = ($salida | Out-String)
        Set-Content -Path (Join-Path $base 'selftest.txt') -Value $texto -Encoding UTF8
        Write-Output "  autotest puro: $($texto.Trim())"
        if ($LASTEXITCODE -ne 0 -or $texto -notmatch 'PASS') {
            Write-Output "FALLO: el autotest puro no pasa; no se lanza el servidor."
            Set-Content -Path (Join-Path $base 'fin.txt') -Value 'POI_FALLO autotest=FAIL' -Encoding UTF8
            exit 4
        }
    }
}

# ------------------------------------------------------------------ procesos: preflight y cierre seguro
function Get-DevJavaProcesses {
    @(Get-CimInstance Win32_Process -Filter "Name like 'java%'" -ErrorAction SilentlyContinue | Where-Object {
            ([string]$_.CommandLine) -match 'devlaunch\.Main|forgeserverdev|fml\.modFolders'
        } | ForEach-Object {
            [pscustomobject]@{ Id = [int]$_.ProcessId; CommandLine = [string]$_.CommandLine }
        })
}

function Assert-SinAjenos([string]$cuando) {
    $ajenos = @(Get-DevJavaProcesses)
    if ($ajenos.Count -gt 0) {
        Write-Output "ABORTO ($cuando): hay $($ajenos.Count) proceso(s) java de cliente/servidor dev ajenos."
        foreach ($p in $ajenos) {
            Write-Output "  PID $($p.Id): $($p.CommandLine)"
        }
        Write-Output '  No los mato. Cierra ese cliente/servidor y vuelve a lanzar.'
        exit 2
    }
}

function Wait-ServerExit($proc, [int]$segundos) {
    if (-not $proc) { return $true }
    $limite = (Get-Date).AddSeconds($segundos)
    while ((Get-Date) -lt $limite) {
        try { $proc.Refresh() } catch { return $true }
        if ($proc.HasExited) { return $true }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Stop-OurServer($proc, [string]$fase) {
    if (-not $proc) { return $true }
    # Cierre ordenado: el arnés hace saveEverything y halt(false); el JVM debe salir solo.
    if (Wait-ServerExit $proc 150) {
        Write-Output "  fase ${fase}: el servidor termino solo (cierre ordenado)"
        return $true
    }
    Write-Output "  fase ${fase}: sigue vivo tras 150 s; taskkill /PID $($proc.Id) /T /F (solo su arbol)"
    & taskkill.exe /PID $proc.Id /T /F 2>&1 | ForEach-Object { Write-Output "    $_" }
    Start-Sleep -Seconds 3
    return $false
}

function Wait-Done([string]$fase, [int]$minutos, $proc, [datetime]$inicio) {
    $archivo = Join-Path $base "done-$fase.txt"
    $limite = (Get-Date).AddMinutes($minutos)
    while ((Get-Date) -lt $limite) {
        if (Test-Path -LiteralPath $archivo) {
            $item = Get-Item -LiteralPath $archivo -ErrorAction SilentlyContinue
            if ($item -and $item.LastWriteTime -ge $inicio) {
                return $true
            }
        }
        try {
            if ($proc -and $proc.HasExited -and ((Get-Date) - $inicio).TotalMinutes -gt 2) {
                Start-Sleep -Seconds 5
                $item = Get-Item -LiteralPath $archivo -ErrorAction SilentlyContinue
                return ($item -and $item.LastWriteTime -ge $inicio)
            }
        } catch { }
        Start-Sleep -Seconds 5
    }
    return $false
}

Assert-SinAjenos 'antes de empezar'
$evidenciaPrevia = @(Get-ChildItem -LiteralPath $base -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -eq 'fin.txt' -or $_.Name -like 'phase-*.json' -or $_.Name -like 'done-*.txt' })
if ($evidenciaPrevia.Count -gt 0) {
    Write-Output "ABORTO: el run '$Run' ya tiene evidencia ($($evidenciaPrevia.Count) fichero(s)); usa un -Run nuevo."
    exit 3
}

$resultados = @{}
$experimentos = @{}
$contratos = @{}
$codigos = @{}
foreach ($fase in $Fases) {
    if (@('prepare', 'reload1', 'reload2', 'partial') -notcontains $fase) {
        Write-Output "ABORTO: fase desconocida '$fase'."
        exit 3
    }
    Assert-SinAjenos "antes de $fase"
    $log = Join-Path $base "srv-$fase.log"
    $env:JDK_JAVA_OPTIONS = "-Djdk.net.unixdomain.tmpdir=C:\Windows\Temp -Dpoi.run=$Run -Dpoi.phase=$fase -Dpoi.raiz=$raiz -Ddevilrpg.a2n.allowMaintenanceHook=true"
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: lanzando runServer (poiHarness)"
    $inicioFase = Get-Date
    $srv = Start-Process -FilePath (Join-Path $raiz 'gradlew.bat') `
        -ArgumentList 'runServer', '--console=plain', '-Pa2nDiagnostics=true', '-PpoiHarness=true', `
        '-Pa2nMaintenanceHook=true', '-Pa2nMode=raid', "-Pa2nGameDirectory=build/av/$Run/game" `
        -WorkingDirectory $raiz -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
        -PassThru -NoNewWindow
    $ok = Wait-Done $fase $TimeoutMinutes $srv $inicioFase
    $cerradoOrdenado = Stop-OurServer $srv $fase
    $json = Join-Path $base "phase-$fase.json"
    $done = Join-Path $base "done-$fase.txt"
    $res = 'SIN_RESULTADO'
    $exp = $false
    $con = $false
    $doneRes = 'SIN_MARCADOR'
    if (Test-Path -LiteralPath $done) {
        $doneTexto = Get-Content -LiteralPath $done -Raw -ErrorAction SilentlyContinue
        if (-not [string]::IsNullOrWhiteSpace($doneTexto)) {
            $doneRes = $doneTexto.Trim()
        }
    }
    if (Test-Path -LiteralPath $json) {
        $jsonItem = Get-Item -LiteralPath $json
        if (-not $ok -or $jsonItem.LastWriteTime -lt $inicioFase) {
            $res = 'JSON_ANTIGUO'
        } else {
            try {
                $data = Get-Content -LiteralPath $json -Raw | ConvertFrom-Json
                $res = [string]$data.result
                $exp = [bool]$data.experimento_ok
                $con = [bool]$data.contrato_ok
                if ($data.hallazgos_codigos) {
                    $codigos[$fase] = (@($data.hallazgos_codigos) -join ',')
                }
            } catch {
                $res = 'JSON_INVALIDO'
            }
        }
    }
    if ($doneRes -ne 'PASS' -and $res -eq 'PASS') {
        $res = "DONE_$doneRes"
    }
    $resultados[$fase] = $res
    $experimentos[$fase] = $exp
    $contratos[$fase] = $con
    if (-not $cerradoOrdenado) {
        $resultados[$fase] = "$res/CIERRE_FORZADO"
    }
    Write-Output "[$(Get-Date -Format 'HH:mm:ss')] FASE ${fase}: done=$ok result=$res experimento=$exp contrato=$con"
}

# ------------------------------------------------------------------ dump independiente de POI en disco
$poiDump = Join-Path $base 'poi-dump.json'
$python = Get-Command python -ErrorAction SilentlyContinue
$centroArg = @()
$ultima = $Fases[$Fases.Count - 1]
$savedFinal = Join-Path $base "saveddata-$ultima.txt"
if (Test-Path -LiteralPath $savedFinal) {
    $lineaCentro = Get-Content -LiteralPath $savedFinal | Where-Object { $_ -like 'centro=*' } | Select-Object -First 1
    if ($lineaCentro) {
        # centro=661, 63, 533  ->  --centro 661,533
        $partes = ($lineaCentro.Substring('centro='.Length).Trim() -split ',')
        if ($partes.Count -ge 3) {
            $centroArg = @('--centro', "$($partes[0].Trim()),$($partes[2].Trim())", '--radio', '80')
        }
    }
}
if ($python) {
    try {
        & $python.Source (Join-Path $raiz 'tools/poi/poi-dump.py') (Join-Path $gameDir 'world') `
            --tipo job @centroArg --json |
            Set-Content -Path $poiDump -Encoding UTF8
        Write-Output "  dump independiente de POI: $poiDump"
    } catch {
        Write-Output "  dump independiente de POI: OMITIDO ($($_.Exception.Message))"
    }
}

$pasan = @($Fases | Where-Object { $resultados[$_] -eq 'PASS' }).Count
$experimentoOk = @($Fases | Where-Object { $experimentos[$_] }).Count
$veredicto = if ($pasan -eq $Fases.Count) {
    'POI_PASS'
} elseif ($experimentoOk -eq $Fases.Count) {
    'POI_HALLAZGOS'
} else {
    'POI_FALLO'
}
$detalle = ($Fases | ForEach-Object {
        $cod = if ($codigos.ContainsKey($_) -and $codigos[$_]) { " hallazgos=[$($codigos[$_])]" } else { '' }
        "$_=$($resultados[$_]) experimento=$($experimentos[$_]) contrato=$($contratos[$_])$cod"
    }) -join ' | '
Set-Content -Path (Join-Path $base 'fin.txt') -Value "$veredicto $detalle" -Encoding UTF8
Write-Output "=== FIN $Run : $veredicto ==="
Write-Output "  $detalle"
Write-Output "  evidencia: $base"
if ($veredicto -eq 'POI_HALLAZGOS') {
    Write-Output '  NOTA: el arnés midió bien; el contrato de POI/tickets NO se cumple. Son defectos, no un fallo del arnés.'
}
