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
    $env:JDK_JAVA_OPTIONS = (@(($jdkOptions.Trim()), $requiredOptions) |
        ForEach-Object { $_ } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join ' '

    # Gradle convierte ORG_GRADLE_PROJECT_* en project properties; build.gradle las
    # propaga explícitamente al proceso runServer, sin depender del daemon padre.
    $env:ORG_GRADLE_PROJECT_a2nTraceNav = 'true'
    $env:ORG_GRADLE_PROJECT_a2nTraceGlobalSearch = 'true'

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
}

exit $exitCode
