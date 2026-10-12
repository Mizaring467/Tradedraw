# R10 · Supervisor del grabador de ticks.
# Relanza record_ticks.js si cae y escribe un heartbeat cada 60 s en data/ticks/heartbeat.txt.
# Uso: .\scripts\ticks\supervisor.ps1 -Minutes 300
param(
    [int]$Minutes = 300,
    [string[]]$Rics = @('ETHUSD-OTC','SOLUSD-OTC','BTCUSD-OTC'),
    [string]$Repo = 'C:\Users\heidy\Tradedraw'
)

$ErrorActionPreference = 'Continue'
Set-Location $Repo
$hb = Join-Path $Repo 'data\ticks\heartbeat.txt'
$logDir = Join-Path $Repo 'loops\journal\live'
New-Item -ItemType Directory -Force -Path (Join-Path $Repo 'data\ticks') | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$deadline = (Get-Date).AddMinutes($Minutes)
$ricArgs = @('scripts\ticks\record_ticks.js') + $Rics + @('--minutes', "$Minutes")
$restarts = 0

while ((Get-Date) -lt $deadline) {
    # Latido
    ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' | restarts=' + $restarts) | Out-File -FilePath $hb -Encoding utf8

    $proc = Start-Process -FilePath 'node' -ArgumentList $ricArgs `
        -WorkingDirectory $Repo `
        -RedirectStandardOutput (Join-Path $logDir 'r10_ticks_stdout.log') `
        -RedirectStandardError  (Join-Path $logDir 'r10_ticks_stderr.log') `
        -NoNewWindow -PassThru

    $proc.Id | Out-File -FilePath (Join-Path $logDir 'r10_ticks.pid') -Encoding ascii

    # Heartbeat en bucle mientras vive el proceso
    while (-not $proc.HasExited -and (Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 60
        ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' | pid=' + $proc.Id + ' | running | restarts=' + $restarts) | Out-File -FilePath $hb -Encoding utf8
    }

    if ($proc.HasExited -and (Get-Date) -lt $deadline) {
        $restarts++
        ($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' | pid=' + $proc.Id + ' | EXITED -> relanzando (#' + $restarts + ')') | Out-File -FilePath $hb -Encoding utf8
        Start-Sleep -Seconds 5
    }
}

($(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' | SUPERVISOR FIN (deadline alcanzado) | restarts=' + $restarts) | Out-File -FilePath $hb -Encoding utf8
try { if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force } } catch {}
