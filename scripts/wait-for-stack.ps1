# wait-for-stack.ps1 -- Windows equivalent of wait-for-stack.sh
# Polls the docker-compose stack until /api/actuator/health reports overall UP
# AND the db component status is UP.
param(
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = 'Stop'
$start = Get-Date
$healthUrl = 'http://localhost:8080/api/actuator/health'

Write-Host "[wait-for-stack] polling $healthUrl (timeout ${TimeoutSeconds}s)..."

while ($true) {
    $elapsed = ((Get-Date) - $start).TotalSeconds
    if ($elapsed -ge $TimeoutSeconds) {
        Write-Host "[wait-for-stack] TIMEOUT after ${TimeoutSeconds}s"
        try { docker compose ps } catch { }
        exit 1
    }

    try {
        $body = Invoke-RestMethod -Uri $healthUrl -TimeoutSec 5 -ErrorAction Stop
        if ($body.status -eq 'UP' -and $body.components.db.status -eq 'UP') {
            Write-Host "[wait-for-stack] OK after $([math]::Round($elapsed))s -- overall UP, db UP"
            $body | ConvertTo-Json -Depth 5
            exit 0
        }
    } catch {
        # not ready yet
    }
    Start-Sleep -Seconds 3
}
