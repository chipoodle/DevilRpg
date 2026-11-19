[CmdletBinding()]
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$LauncherArgs
)

$ErrorActionPreference = 'Stop'
$launcher = [System.IO.Path]::GetFullPath(
    (Join-Path $PSScriptRoot '..\..\build\a2n\lanzar-a2n.ps1')
)

if (-not (Test-Path -LiteralPath $launcher -PathType Leaf)) {
    throw "No existe el lanzador local requerido: $launcher"
}

$previousJdkJavaOptions = $env:JDK_JAVA_OPTIONS
$previousTraceNav = $env:ORG_GRADLE_PROJECT_a2nTraceNav
$previousTraceGlobalSearch = $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch
$previousA2Diagnostics = $env:ORG_GRADLE_PROJECT_a2nDiagnostics
$previousPopulationQueryHarness = $env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness
$exitCode = 1

try {
    # Elimina valores anteriores de estas dos propiedades para que no gane un false heredado.
    $jdkOptions = [string]$previousJdkJavaOptions
    $jdkOptions = $jdkOptions -replace '(?<!\S)-Ddevilrpg\.a2n\.traceNav=\S+', ''
    $jdkOptions = $jdkOptions -replace '(?<!\S)-Ddevilrpg\.a2n\.traceGlobalSearch=\S+', ''
    $requiredOptions = @(
        '-Ddevilrpg.a2n.traceNav=true',
        '-Ddevilrpg.a2n.traceGlobalSearch=true'
    )
    $effectiveOptions = @()
    if (-not [string]::IsNullOrWhiteSpace($jdkOptions.Trim())) {
        $effectiveOptions += $jdkOptions.Trim()
    }
    $effectiveOptions += $requiredOptions
    $env:JDK_JAVA_OPTIONS = $effectiveOptions -join ' '

    # Gradle convierte ORG_GRADLE_PROJECT_* en project properties; build.gradle las
    # propaga explícitamente al proceso runServer, sin depender del daemon padre.
    $env:ORG_GRADLE_PROJECT_a2nTraceNav = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch = 'true'

    # Selecciona el arnés canónico Phase55 sin reemplazar el A2NavHarness legacy trackeado.
    $env:ORG_GRADLE_PROJECT_a2nDiagnostics = 'true'
    $env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness = 'true'

    & $launcher @LauncherArgs
    if ($null -ne $LASTEXITCODE) {
        $exitCode = $LASTEXITCODE
    } else {
        $exitCode = 0
    }
}
finally {
    $env:JDK_JAVA_OPTIONS = $previousJdkJavaOptions
    $env:ORG_GRADLE_PROJECT_a2nTraceNav = $previousTraceNav
    $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch = $previousTraceGlobalSearch
    $env:ORG_GRADLE_PROJECT_a2nDiagnostics = $previousA2Diagnostics
    $env:ORG_GRADLE_PROJECT_a2nPopulationQueryHarness = $previousPopulationQueryHarness
}

exit $exitCode
