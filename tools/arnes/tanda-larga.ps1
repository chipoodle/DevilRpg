# tanda-larga.ps1 — EL LOTE LARGO (el banco de 20 minutos), VERSIONADO
#
#   .\tools\arnes\tanda-larga.ps1 121 122 123 124          (cuatro corridas de 20 min = 80 min)
#   .\tools\arnes\tanda-larga.ps1 125 -Conservar           (no restaura el mundo: mide el pueblo YA ASENTADO)
#
# QUE HACE: restaura `run\world` desde el guardado del jugador (que NO se toca nunca), arranca el servidor con el
# arnes, lo deja 20 minutos y recoge `build\medida-tandaN.log`. Es el banco que da la TASA por corrida entera; el
# banco rapido (tanda-rapida.ps1, 3 min) es el que se usa para iterar.
#
# CIERRE LIMPIO (3-oct-2026): antes esta tanda MATABA el servidor de golpe (`Stop-Process`) y el mundo NO se guardaba:
# el trazado nuevo de la aldea no llegaba a disco y la corrida siguiente volvia a migrar (medido: 5-6 avisos de
# rendicion con migracion contra 2-4 sin ella, y el pueblo rehaciendose entero cada vez). Ahora se le dice al arnes a
# que tick parar (`build\arnes-parar.txt`) y el servidor CIERRA COMO SE DEBE: guarda el mundo y sale solo. Por eso
# `-Conservar` ya sirve de verdad: la primera corrida paga la migracion, las siguientes miden el pueblo asentado.
# Si el arnes no cerrara (o no estuviera el fichero), se mata como antes y se dice en el registro.
param(
    [switch]$Conservar
)
$ErrorActionPreference = 'Continue'
$env:GRADLE_USER_HOME = 'C:\Users\Christian\Documents\DevilRpg\.gradle-home'
Set-Location 'C:\Users\Christian\Documents\DevilRpg'
$MINUTOS = 20

$arnes = 'src\main\java\com\chipoodle\devilrpg\debug\GuardHarness.java'
if (-not (Test-Path $arnes)) { Write-Output 'ABORTADO: falta el arnes (copiar tools\arnes\GuardHarness.java)'; exit 1 }

$numeros = @()
foreach ($a in $args) { if ("$a" -ne '-Conservar') { $numeros += $a } }

foreach ($i in $numeros) {
    Write-Output "=== CORRIDA $i : $(Get-Date -Format 'HH:mm:ss') ==="
    $vivos = (Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' }).Count
    if ($vivos -gt 0) { Write-Output "ABORTADO en la corrida ${i}: $vivos servidor(es) vivo(s)"; exit 1 }
    if (-not $Conservar -or -not (Test-Path run\world\level.dat)) {
        Remove-Item run\world -Recurse -Force -ErrorAction SilentlyContinue
        Copy-Item 'run\saves\New World' run\world -Recurse
    } else {
        Write-Output "   (mundo conservado: el pueblo ya no migra, se mide asentado)"
    }
    Remove-Item run\logs\latest.log -Force -ErrorAction SilentlyContinue
    # 20 ticks/s, con 10 s de margen para que el guardado termine antes de que se acabe el tiempo de reloj.
    [int]$tickParar = [int]($MINUTOS * 60 * 20 * 0.85)
    Set-Content -Path run\arnes-parar.txt -Value $tickParar -Encoding ascii
    # CANDADO DEL ARNES (3-oct-2026): ver `GuardHarness`. Sin esta variable el arnes no hace NADA, asi que un
    # `runClient` normal (o el boton de Gradle) no lo ejecuta nunca mas.
    $env:DEVILRPG_ARNES = '1'
    $p = Start-Process -FilePath '.\gradlew.bat' -ArgumentList 'runServer','--console=plain' -PassThru -NoNewWindow `
        -RedirectStandardOutput "build\runserver-tanda$i.txt" -RedirectStandardError "build\runserver-tanda$i.err"
    Start-Sleep -Seconds ($MINUTOS * 60)
    # Y SE LE ESPERA: si cierra solo, el mundo esta guardado. Solo se mata si no ha cerrado.
    $espera = 0
    while (-not $p.HasExited -and $espera -lt 180) { Start-Sleep -Seconds 5; $espera += 5 }
    Get-CimInstance Win32_Process -Filter "Name like 'java%'" |
        Where-Object { $_.CommandLine -match 'fml.modFolders|forgeserverdev' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
    Start-Sleep -Seconds 8
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
    $log = "build\medida-tanda$i.log"
    Copy-Item run\logs\latest.log $log -Force
    $cierre = (Get-Content $log | Select-String -Pattern 'CIERRE LIMPIO').Count
    if ($cierre -gt 0) { Write-Output "   (cierre limpio: el mundo quedo guardado)" }
    else { Write-Output "   (AVISO: no hubo cierre limpio; el mundo NO se guardo)" }
    $n = (Get-Content $log | Select-String -Pattern 'no consigue llegar').Count
    Write-Output "corrida $i acabada: $n avisos de rendicion"
}
Write-Output "=== TANDA ACABADA $(Get-Date -Format 'HH:mm:ss') ==="
