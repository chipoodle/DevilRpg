# consulta_local.ps1 — APOYO LOCAL EN PARALELO (el modelo del jugador, por Ollama)
#
# Le manda una FICHA (un fichero de texto con el prompt) al modelo local y devuelve su respuesta. Sirve para
# lanzar VARIAS consultas a la vez (una por ficha) con trabajos en segundo plano, mientras yo sigo trabajando.
#
#   .\tools\arnes\consulta_local.ps1 -Ficha build\ficha1.txt
#   .\tools\arnes\consulta_local.ps1 -Ficha build\ficha1.txt -Modelo deepseek-r1:14b
#
# REGLAS (las mismas que en el LEEME, y no son negociables):
#   - El modelo local PROPONE; la verdad la da COMPILAR + LINT + MEDIR con el arnés. Nada se commitea sin eso.
#   - No se le delega lo que hay que medir: las corridas del arnés son EN SERIE (un JVM, el .jar y una sola copia
#     de run/world). Lo que se le delega es leer, resumir, reseñar, y BORRAR código acotado que yo reviso.
#   - La primera llamada tras cargar el modelo tarda ~95 s (8,3 GB a la VRAM); luego ~3-7 s por respuesta corta.
param(
    [Parameter(Mandatory = $true)][string]$Ficha,
    [string]$Modelo = 'deepseek-coder-v2:16b',
    [int]$TimeoutSeg = 900
)

$prompt = Get-Content $Ficha -Raw
if (-not $prompt) { Write-Output "FALLO: la ficha $Ficha esta vacia"; exit 1 }
$body = @{ model = $Modelo; messages = @(@{ role = 'user'; content = $prompt }); stream = $false } |
    ConvertTo-Json -Depth 5
$t0 = Get-Date
try {
    $r = Invoke-RestMethod -Uri 'http://127.0.0.1:11434/v1/chat/completions' -Method Post -Body $body `
        -ContentType 'application/json' -TimeoutSec $TimeoutSeg
    $seg = [math]::Round(((Get-Date) - $t0).TotalSeconds, 1)
    Write-Output "== $Modelo  ($seg s, $($r.usage.completion_tokens) tokens) ficha=$Ficha =="
    Write-Output $r.choices[0].message.content.Trim()
} catch {
    Write-Output "FALLO en la consulta local ($Modelo): $($_.Exception.Message)"
    exit 1
}
