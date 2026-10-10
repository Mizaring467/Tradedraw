# Orquestador de Loop TradeDraw (herdr) — multi-agente
# Uso:
#   .\scripts\orchestrate_loop.ps1 -Round 1
#   .\scripts\orchestrate_loop.ps1 -Action dispatch -Agent codex -TaskFile .\workflows\rounds\r1_codex.md
#   .\scripts\orchestrate_loop.ps1 -Action build
#   .\scripts\orchestrate_loop.ps1 -Action install
#   .\scripts\orchestrate_loop.ps1 -Action monitor
#   .\scripts\orchestrate_loop.ps1 -Action status
#
# Requiere: HERDR_ENV=1, herdr en PATH, adb conectado, dispositivo UGU8KFUKM7E6CE8D.

param(
    [ValidateSet('dispatch','build','test','install','monitor','status','audit')]
    [string]$Action = 'dispatch',
    [ValidateSet('codex','agy','hermes','opencode')]
    [string]$Agent = 'codex',
    [string]$TaskFile,
    [string]$Task,
    [int]$Round = 1,
    [int]$TimeoutMs = 1800000
)

$ErrorActionPreference = 'Stop'
$Repo = 'C:\Users\heidy\Tradedraw'
$Serial = 'UGU8KFUKM7E6CE8D'
$Curl = 'curl.exe'

function Write-Step($msg) { Write-Host "`n=== $msg ===" -ForegroundColor Cyan }

function Invoke-AgentPrompt([string]$name, [string]$text, [int]$timeout) {
    Write-Host ">> prompt -> $name ($timeout ms)" -ForegroundColor Yellow
    & herdr agent prompt $name $text --wait --timeout $timeout
    if ($LASTEXITCODE -ne 0) { Write-Warning "agent prompt devolvio exit=$LASTEXITCODE para $name" }
}

function Invoke-Build {
    Write-Step "BUILD assembleDebug"
    Push-Location $Repo
    try { & .\gradlew.bat assembleDebug --no-daemon --no-configuration-cache } finally { Pop-Location }
}

function Invoke-UnitTests {
    Write-Step "TESTS unitarios"
    Push-Location $Repo
    try { & .\gradlew.bat testDebugUnitTest --no-daemon } finally { Pop-Location }
}

function Invoke-Install {
    Write-Step "INSTALL en $Serial"
    $apk = Join-Path $Repo 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw "APK no encontrado: $apk (corre build primero)" }
    & adb -s $Serial install -r $apk
    & adb -s $Serial shell am start -n 'com.example.tradedraw/.MainActivity'
}

function Invoke-Monitor {
    Write-Step "MONITOR 25 min (telemetria)"
    & adb -s $Serial forward tcp:8080 tcp:8080 | Out-Null
    Push-Location $Repo
    try { & python scripts\monitor_25m_session.py } finally { Pop-Location }
}

function Get-Status {
    Write-Step "STATUS /status (HTTP bridge)"
    & adb -s $Serial forward tcp:8080 tcp:8080 | Out-Null
    & $Curl -s --max-time 5 'http://127.0.0.1:8080/status'
}

function Invoke-Audit {
    Write-Step "AUDITORIA de journal (agy)"
    $prompt = @"
Audita la ultima sesion de TradeDraw en modo Demo contra .agents/skills/master_traders_skill/SKILL.md.
Lee loops/journal/live/trade_journal_live.csv y loops/journal/live/live_session_25m_report.json.
Calcula: N, Wins, Losses, Win Rate, EV, Max Drawdown, violaciones de ventana cronologica (:06-:57 o :58-:59), 
trades en regimen SIDEWAYS y ordenes SELL vs BUY.
Emite veredicto PASS/FAIL por hipotesis H1 (timing :01-:03), H2 (veto chop), H3 (sin inversion adaptativa).
Responde en espanol, max 30 lineas, con la tabla de metricas.
"@
    Invoke-AgentPrompt 'agy' $prompt $TimeoutMs
}

switch ($Action) {
    'dispatch' {
        if (-not $Task -and $TaskFile) {
            if (-not (Test-Path $TaskFile)) { throw "TaskFile no existe: $TaskFile" }
            $Task = Get-Content $TaskFile -Raw
        }
        if (-not $Task) { throw "Falta -Task o -TaskFile" }
        Invoke-AgentPrompt $Agent $Task $TimeoutMs
    }
    'build'   { Invoke-Build }
    'test'    { Invoke-UnitTests }
    'install' { Invoke-Install }
    'monitor' { Invoke-Monitor }
    'status'  { Get-Status }
    'audit'   { Invoke-Audit }
}
