[CmdletBinding()]
param(
    [ValidateSet('raid')][string]$Modo = 'raid',
    [int]$Semilla = 12345,
    [ValidateRange(1,240)][int]$LimiteMinutos = 90,
    [ValidateRange(1,20000)][int]$FaseA = 3000,
    [ValidateRange(1,30000)][int]$FaseB = 12000,
    [ValidateRange(1,100000)][int]$Presupuesto = 900,
    [ValidateRange(30,600)][int]$InicioServidorSegundos = 180,
    [switch]$AceptarEula
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$gradlew = Join-Path $repo 'gradlew.bat'
$branchExpected = 'feature/autonomous-village'
$runId = 'a2n-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
$runDir = Join-Path $repo "build\a2n\$runId"
$gameDir = Join-Path $runDir 'game'
$worldDir = Join-Path $gameDir 'a2n'
$stdoutLog = Join-Path $runDir 'srv.log'
$stderrLog = Join-Path $runDir 'srv.err.log'
$statusFile = Join-Path $runDir 'launcher-status.txt'
$finFile = Join-Path $runDir 'fin.txt'
$navTraceFile = Join-Path $runDir 'nav-trace.log'
$gradleProcess = $null
$serverPid = $null
$exitCode = 1
$startedAt = Get-Date
$envNames = @('JDK_JAVA_OPTIONS','ORG_GRADLE_PROJECT_a2nDiagnostics','ORG_GRADLE_PROJECT_a2nPopulationQueryHarness','ORG_GRADLE_PROJECT_a2nTraceNav','ORG_GRADLE_PROJECT_a2nTraceGlobalSearch')
$oldEnv = @{}
foreach ($n in $envNames) { $oldEnv[$n] = [Environment]::GetEnvironmentVariable($n,'Process') }

function Write-Status([string]$State,[string]$Detail='') {
    $pidValue = if ($null -eq $script:serverPid) {'NOT_IDENTIFIED'} else {[string]$script:serverPid}
    $gradleValue = if ($null -eq $script:gradleProcess) {'NOT_STARTED'} else {[string]$script:gradleProcess.Id}
    @("STATE=$State","RUN_ID=$script:runId","BRANCH=$script:branchExpected","SERVER_PID=$pidValue","GRADLE_PID=$gradleValue","STARTED=$($script:startedAt.ToString('o'))","UPDATED=$((Get-Date).ToString('o'))","DETAIL=$Detail") | Set-Content -LiteralPath $script:statusFile -Encoding UTF8
}
function Test-IsLauncherDescendant([int]$ProcessId,[int]$RootPid,[object[]]$Snapshot) {
    $current = $Snapshot | Where-Object { [int]$_.ProcessId -eq $ProcessId } | Select-Object -First 1
    for ($depth = 0; $depth -lt 16 -and $null -ne $current; $depth++) {
        $parentId = [int]$current.ParentProcessId
        if ($parentId -eq $RootPid) { return $true }
        $current = $Snapshot | Where-Object { [int]$_.ProcessId -eq $parentId } | Select-Object -First 1
    }
    return $false
}
function Find-OwnedServer {
    if ($null -eq $script:gradleProcess) { return $null }
    $snapshot = @(Get-CimInstance Win32_Process)
    $found = @($snapshot | Where-Object {
        $_.Name -match '^javaw?\.exe$' -and
        $_.CommandLine -and
        $_.CommandLine -match 'forgeserverdev|devlaunch\.Main|serverRunProgramArgs' -and
        $_.CreationDate -ge $script:startedAt.AddSeconds(-5) -and
        (Test-IsLauncherDescendant -ProcessId ([int]$_.ProcessId) -RootPid ([int]$script:gradleProcess.Id) -Snapshot $snapshot)
    })
    if ($found.Count -gt 1) { throw "Más de una JVM servidor desciende del proceso Gradle de esta corrida ($($script:gradleProcess.Id))." }
    if ($found.Count -eq 1) { return $found[0] }
    return $null
}
function Stop-OwnedServer {
    if ($null -ne $script:serverPid -and (Get-Process -Id $script:serverPid -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $script:serverPid -Force -ErrorAction SilentlyContinue
        try { Wait-Process -Id $script:serverPid -Timeout 20 -ErrorAction SilentlyContinue } catch {}
    }
}

try {
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { throw "No existe $gradlew" }
    $branch = (& git -C $repo branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne $branchExpected) { throw "Rama requerida=$branchExpected; actual=$branch" }
    $head = (& git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw "HEAD Git inválido: $head" }
    $dirty = @(& git -C $repo status --porcelain --untracked-files=no)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar git status.' }
    if ($dirty.Count -gt 0) { throw "Hay cambios tracked; no se inicia ni se altera el árbol: $([string]::Join([Environment]::NewLine,$dirty))" }
    if ($FaseB -le $FaseA) { throw 'FaseB debe ser mayor que FaseA.' }

    if (Test-Path -LiteralPath $runDir) { throw "El runId ya existe; no se reutilizan artefactos: $runDir" }
    New-Item -ItemType Directory -Path $gameDir -Force | Out-Null
    if (Test-Path -LiteralPath $worldDir) { throw "No se reutiliza mundo existente: $worldDir" }
    $eula = Join-Path $gameDir 'eula.txt'
    if ($AceptarEula) {
        Set-Content -LiteralPath $eula -Value 'eula=true' -Encoding ASCII
    } elseif (-not (Test-Path -LiteralPath $eula) -or (Get-Content -LiteralPath $eula -Raw) -notmatch '(?im)^\s*eula\s*=\s*true\s*$') {
        throw 'EULA no aceptada. Revisa https://aka.ms/MinecraftEULA y usa -AceptarEula solo si la aceptas.'
    }
    @("level-seed=$Semilla",'gamemode=survival','enable-command-block=false','enable-query=false','level-name=a2n','motd=DevilRpg A2 headless disposable run','difficulty=normal','max-tick-time=120000','max-players=8','online-mode=false','enable-status=false','allow-flight=true','view-distance=8','simulation-distance=8','server-port=25575','sync-chunk-writes=true','spawn-protection=0') | Set-Content -LiteralPath (Join-Path $gameDir 'server.properties') -Encoding ASCII

    $jdk = [string]$oldEnv['JDK_JAVA_OPTIONS']
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.a2n\.traceNav=\S+',''
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.a2n\.traceGlobalSearch=\S+',''
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.nav\.budget=\S+',''
    $opts = @()
    if (-not [string]::IsNullOrWhiteSpace($jdk.Trim())) { $opts += $jdk.Trim() }
    $opts += @("-Da2n.run=$runId",'-Ddevilrpg.a2n.traceNav=true','-Ddevilrpg.a2n.traceGlobalSearch=true',"-Ddevilrpg.nav.budget=$Presupuesto")
    $env:JDK_JAVA_OPTIONS = $opts -join ' '
    $env:ORG_GRADLE_PROJECT_a2nDiagnostics = 'true'
    $env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceNav = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch = 'true'

    $gradleArgs = @('--no-daemon','--console=plain','--stacktrace','runServer',"-Pa2nGameDirectory=$gameDir",'-Pa2nMode=raid',"-Pa2nPhaseA=$FaseA", "-Pa2nPhaseB=$FaseB", "-Pa2nRunId=$runId",'-Pa2nTraceNav=true','-Pa2nTraceGlobalSearch=true','-Pa2nDiagnostics=true','-Pa2nPopulationQueryHarness=true')
    Write-Status 'PREPARED' "HEAD=$head; seed=$Semilla; phaseA=$FaseA; phaseB=$FaseB; budget=$Presupuesto; world=$worldDir"
    $gradleArgumentLine = ($gradleArgs | ForEach-Object { if ($_ -match '\s') { [char]34 + $_.Replace([string][char]34, ([string][char]92 + [string][char]34)) + [char]34 } else { $_ } }) -join ' '
    $gradleProcess = Start-Process -FilePath $gradlew -ArgumentList $gradleArgumentLine -WorkingDirectory $repo -PassThru -NoNewWindow -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    Write-Status 'GRADLE_STARTED' 'Waiting for Forge/NeoForge server JVM in this launcher's Gradle process tree.'

    $startupEnd = (Get-Date).AddSeconds($InicioServidorSegundos)
    while ((Get-Date) -lt $startupEnd) {
        $owned = Find-OwnedServer
        if ($null -ne $owned) { $serverPid = [int]$owned.ProcessId; break }
        $gradleProcess.Refresh()
        if ($gradleProcess.HasExited) { throw "Gradle terminó antes de iniciar servidor. ExitCode=$($gradleProcess.ExitCode)" }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $serverPid) { throw "No se identificó JVM del servidor en $InicioServidorSegundos segundos." }
    Write-Status 'RUNNING' "Exact server PID=$serverPid"

    $deadline = (Get-Date).AddMinutes($LimiteMinutos)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $finFile) { break }
        if ($null -eq (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) { throw 'La JVM terminó sin fin.txt.' }
        $gradleProcess.Refresh()
        if ($gradleProcess.HasExited) { throw "Gradle terminó sin fin.txt. ExitCode=$($gradleProcess.ExitCode)" }
        Start-Sleep -Seconds 3
    }
    if (-not (Test-Path -LiteralPath $finFile)) { throw "Timeout: no existe fin.txt tras $LimiteMinutos minutos." }
    $fin = (Get-Content -LiteralPath $finFile -Raw).Trim()
    Write-Status 'FIN_MARKER_WRITTEN' $fin

    $stopEnd = (Get-Date).AddSeconds(45)
    while ((Get-Date) -lt $stopEnd -and (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 2 }
    if (Get-Process -Id $serverPid -ErrorAction SilentlyContinue) { Write-Status 'STOPPING_OWNED_SERVER' 'Arnés escribió fin.txt; JVM propia no cerró.'; Stop-OwnedServer }

    $gradleEnd = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $gradleEnd) { $gradleProcess.Refresh(); if ($gradleProcess.HasExited) { break }; Start-Sleep -Seconds 2 }
    $gradleProcess.Refresh()
    if (-not $gradleProcess.HasExited) {
        Write-Status 'STOPPING_OWNED_WRAPPER' "Stopping only Start-Process PID $($gradleProcess.Id)."
        Stop-Process -Id $gradleProcess.Id -Force -ErrorAction SilentlyContinue
        try { Wait-Process -Id $gradleProcess.Id -Timeout 20 -ErrorAction SilentlyContinue } catch {}
    }

    $log = if (Test-Path -LiteralPath $stdoutLog) { Get-Content -LiteralPath $stdoutLog -Raw } else { '' }
    $traceOk = $log -match '\[A2N\] JVM_TRACE traceNav=true traceGlobalSearch=true'
    $startOk = $log -match '\[A2N\] ARRANCADO modo=raid'
    if (-not $traceOk -or -not $startOk) { throw "Evidencia incompleta: serverStarted=$startOk traceConfirmed=$traceOk" }
    $summary = @("RUN_ID=$runId","HEAD=$head","BRANCH=$branch","MODE=raid","PHASE_A=$FaseA","PHASE_B=$FaseB","BUDGET=$Presupuesto","FIN=$fin","TRACE_CONFIRMED=$traceOk","SERVER_STARTED=$startOk","NAV_TRACE_EXISTS=$(Test-Path -LiteralPath $navTraceFile)","GRADLE_EXIT_CODE=$($gradleProcess.ExitCode)","ELAPSED_SECONDS=$([int]((Get-Date)-$startedAt).TotalSeconds)")
    $summary | Set-Content -LiteralPath (Join-Path $runDir 'launcher-summary.txt') -Encoding UTF8
    if ($fin -match '^COMPLETO\b' -and $gradleProcess.ExitCode -eq 0) { $exitCode = 0; Write-Status 'COMPLETE' $fin }
    else { $exitCode = 2; Write-Status 'HARNESS_FINISHED_NONPASS' $fin }
}
catch {
    $message = $_.Exception.Message
    try { Write-Status 'FAILED' $message } catch {}
    [Console]::Error.WriteLine($message)
    Stop-OwnedServer
    if ($null -ne $gradleProcess) {
        $gradleProcess.Refresh()
        if (-not $gradleProcess.HasExited) { Stop-Process -Id $gradleProcess.Id -Force -ErrorAction SilentlyContinue; try { Wait-Process -Id $gradleProcess.Id -Timeout 20 -ErrorAction SilentlyContinue } catch {} }
    }
    $exitCode = 1
}
finally {
    foreach ($n in $envNames) { [Environment]::SetEnvironmentVariable($n,$oldEnv[$n],'Process') }
    if (Test-Path -LiteralPath $runDir) { Write-Host "RUN_DIR=$runDir"; Write-Host "STATUS=$statusFile"; Write-Host "SERVER_LOG=$stdoutLog"; Write-Host "SERVER_ERR=$stderrLog"; Write-Host "FIN=$finFile" }
}
exit $exitCode
 -and
        $_.CommandLine -and
        $_.CommandLine -match 'forgeserverdev|devlaunch\.Main|serverRunProgramArgs' -and
        $_.CreationDate -ge $script:startedAt.AddSeconds(-5) -and
        (Test-IsLauncherDescendant -ProcessId ([int]$_.ProcessId) -RootPid ([int]$script:gradleProcess.Id) -Snapshot $snapshot)
    })
    if ($found.Count -gt 1) { throw "Más de una JVM servidor desciende del proceso Gradle de esta corrida ($($script:gradleProcess.Id))." }
    if ($found.Count -eq 1) { return $found[0] }
    return $null
}
function Stop-OwnedServer {
    if ($null -ne $script:serverPid -and (Get-Process -Id $script:serverPid -ErrorAction SilentlyContinue)) {
        Stop-Process -Id $script:serverPid -Force -ErrorAction SilentlyContinue
        try { Wait-Process -Id $script:serverPid -Timeout 20 -ErrorAction SilentlyContinue } catch {}
    }
}

try {
    if (-not (Test-Path -LiteralPath $gradlew -PathType Leaf)) { throw "No existe $gradlew" }
    $branch = (& git -C $repo branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne $branchExpected) { throw "Rama requerida=$branchExpected; actual=$branch" }
    $head = (& git -C $repo rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-f]{40}$') { throw "HEAD Git inválido: $head" }
    $dirty = @(& git -C $repo status --porcelain --untracked-files=no)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo verificar git status.' }
    if ($dirty.Count -gt 0) { throw "Hay cambios tracked; no se inicia ni se altera el árbol: $([string]::Join([Environment]::NewLine,$dirty))" }
    if ($FaseB -le $FaseA) { throw 'FaseB debe ser mayor que FaseA.' }

    if (Test-Path -LiteralPath $runDir) { throw "El runId ya existe; no se reutilizan artefactos: $runDir" }
    New-Item -ItemType Directory -Path $gameDir -Force | Out-Null
    if (Test-Path -LiteralPath $worldDir) { throw "No se reutiliza mundo existente: $worldDir" }
    $eula = Join-Path $gameDir 'eula.txt'
    if ($AceptarEula) {
        Set-Content -LiteralPath $eula -Value 'eula=true' -Encoding ASCII
    } elseif (-not (Test-Path -LiteralPath $eula) -or (Get-Content -LiteralPath $eula -Raw) -notmatch '(?im)^\s*eula\s*=\s*true\s*$') {
        throw 'EULA no aceptada. Revisa https://aka.ms/MinecraftEULA y usa -AceptarEula solo si la aceptas.'
    }
    @("level-seed=$Semilla",'gamemode=survival','enable-command-block=false','enable-query=false','level-name=a2n','motd=DevilRpg A2 headless disposable run','difficulty=normal','max-tick-time=120000','max-players=8','online-mode=false','enable-status=false','allow-flight=true','view-distance=8','simulation-distance=8','server-port=25575','sync-chunk-writes=true','spawn-protection=0') | Set-Content -LiteralPath (Join-Path $gameDir 'server.properties') -Encoding ASCII

    $jdk = [string]$oldEnv['JDK_JAVA_OPTIONS']
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.a2n\.traceNav=\S+',''
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.a2n\.traceGlobalSearch=\S+',''
    $jdk = $jdk -replace '(?<!\S)-Ddevilrpg\.nav\.budget=\S+',''
    $opts = @()
    if (-not [string]::IsNullOrWhiteSpace($jdk.Trim())) { $opts += $jdk.Trim() }
    $opts += @("-Da2n.run=$runId",'-Ddevilrpg.a2n.traceNav=true','-Ddevilrpg.a2n.traceGlobalSearch=true',"-Ddevilrpg.nav.budget=$Presupuesto")
    $env:JDK_JAVA_OPTIONS = $opts -join ' '
    $env:ORG_GRADLE_PROJECT_a2nDiagnostics = 'true'
    $env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceNav = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch = 'true'

    $gradleArgs = @('--no-daemon','--console=plain','--stacktrace','runServer',"-Pa2nGameDirectory=$gameDir",'-Pa2nMode=raid',"-Pa2nPhaseA=$FaseA", "-Pa2nPhaseB=$FaseB", "-Pa2nRunId=$runId",'-Pa2nTraceNav=true','-Pa2nTraceGlobalSearch=true','-Pa2nDiagnostics=true','-Pa2nPopulationQueryHarness=true')
    Write-Status 'PREPARED' "HEAD=$head; seed=$Semilla; phaseA=$FaseA; phaseB=$FaseB; budget=$Presupuesto; world=$worldDir"
    $gradleArgumentLine = ($gradleArgs | ForEach-Object { if ($_ -match '\s') { [char]34 + $_.Replace([string][char]34, ([string][char]92 + [string][char]34)) + [char]34 } else { $_ } }) -join ' '
    $gradleProcess = Start-Process -FilePath $gradlew -ArgumentList $gradleArgumentLine -WorkingDirectory $repo -PassThru -NoNewWindow -RedirectStandardOutput $stdoutLog -RedirectStandardError $stderrLog
    Write-Status 'GRADLE_STARTED' 'Waiting for JVM identified by exclusive runId and trace flags.'

    $startupEnd = (Get-Date).AddSeconds($InicioServidorSegundos)
    while ((Get-Date) -lt $startupEnd) {
        $owned = Find-OwnedServer
        if ($null -ne $owned) { $serverPid = [int]$owned.ProcessId; break }
        $gradleProcess.Refresh()
        if ($gradleProcess.HasExited) { throw "Gradle terminó antes de iniciar servidor. ExitCode=$($gradleProcess.ExitCode)" }
        Start-Sleep -Seconds 2
    }
    if ($null -eq $serverPid) { throw "No se identificó JVM del servidor en $InicioServidorSegundos segundos." }
    Write-Status 'RUNNING' "Exact server PID=$serverPid"

    $deadline = (Get-Date).AddMinutes($LimiteMinutos)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path -LiteralPath $finFile) { break }
        if ($null -eq (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) { throw 'La JVM terminó sin fin.txt.' }
        $gradleProcess.Refresh()
        if ($gradleProcess.HasExited) { throw "Gradle terminó sin fin.txt. ExitCode=$($gradleProcess.ExitCode)" }
        Start-Sleep -Seconds 3
    }
    if (-not (Test-Path -LiteralPath $finFile)) { throw "Timeout: no existe fin.txt tras $LimiteMinutos minutos." }
    $fin = (Get-Content -LiteralPath $finFile -Raw).Trim()
    Write-Status 'FIN_MARKER_WRITTEN' $fin

    $stopEnd = (Get-Date).AddSeconds(45)
    while ((Get-Date) -lt $stopEnd -and (Get-Process -Id $serverPid -ErrorAction SilentlyContinue)) { Start-Sleep -Seconds 2 }
    if (Get-Process -Id $serverPid -ErrorAction SilentlyContinue) { Write-Status 'STOPPING_OWNED_SERVER' 'Arnés escribió fin.txt; JVM propia no cerró.'; Stop-OwnedServer }

    $gradleEnd = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $gradleEnd) { $gradleProcess.Refresh(); if ($gradleProcess.HasExited) { break }; Start-Sleep -Seconds 2 }
    $gradleProcess.Refresh()
    if (-not $gradleProcess.HasExited) {
        Write-Status 'STOPPING_OWNED_WRAPPER' "Stopping only Start-Process PID $($gradleProcess.Id)."
        Stop-Process -Id $gradleProcess.Id -Force -ErrorAction SilentlyContinue
        try { Wait-Process -Id $gradleProcess.Id -Timeout 20 -ErrorAction SilentlyContinue } catch {}
    }

    $log = if (Test-Path -LiteralPath $stdoutLog) { Get-Content -LiteralPath $stdoutLog -Raw } else { '' }
    $traceOk = $log -match '\[A2N\] JVM_TRACE traceNav=true traceGlobalSearch=true'
    $startOk = $log -match '\[A2N\] ARRANCADO modo=raid'
    if (-not $traceOk -or -not $startOk) { throw "Evidencia incompleta: serverStarted=$startOk traceConfirmed=$traceOk" }
    $summary = @("RUN_ID=$runId","HEAD=$head","BRANCH=$branch","MODE=raid","PHASE_A=$FaseA","PHASE_B=$FaseB","BUDGET=$Presupuesto","FIN=$fin","TRACE_CONFIRMED=$traceOk","SERVER_STARTED=$startOk","NAV_TRACE_EXISTS=$(Test-Path -LiteralPath $navTraceFile)","GRADLE_EXIT_CODE=$($gradleProcess.ExitCode)","ELAPSED_SECONDS=$([int]((Get-Date)-$startedAt).TotalSeconds)")
    $summary | Set-Content -LiteralPath (Join-Path $runDir 'launcher-summary.txt') -Encoding UTF8
    if ($fin -match '^COMPLETO\b' -and $gradleProcess.ExitCode -eq 0) { $exitCode = 0; Write-Status 'COMPLETE' $fin }
    else { $exitCode = 2; Write-Status 'HARNESS_FINISHED_NONPASS' $fin }
}
catch {
    $message = $_.Exception.Message
    try { Write-Status 'FAILED' $message } catch {}
    Write-Error $message
    Stop-OwnedServer
    if ($null -ne $gradleProcess) {
        $gradleProcess.Refresh()
        if (-not $gradleProcess.HasExited) { Stop-Process -Id $gradleProcess.Id -Force -ErrorAction SilentlyContinue; try { Wait-Process -Id $gradleProcess.Id -Timeout 20 -ErrorAction SilentlyContinue } catch {} }
    }
    $exitCode = 1
}
finally {
    foreach ($n in $envNames) { [Environment]::SetEnvironmentVariable($n,$oldEnv[$n],'Process') }
    if (Test-Path -LiteralPath $runDir) { Write-Host "RUN_DIR=$runDir"; Write-Host "STATUS=$statusFile"; Write-Host "SERVER_LOG=$stdoutLog"; Write-Host "SERVER_ERR=$stderrLog"; Write-Host "FIN=$finFile" }
}
exit $exitCode
