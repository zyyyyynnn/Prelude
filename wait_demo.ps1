$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
  try {
    $resp1 = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8081/api/health' -TimeoutSec 2 -ErrorAction Stop
    $resp2 = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:5174' -TimeoutSec 2 -ErrorAction Stop
    if ($resp1.StatusCode -eq 200 -and $resp2.StatusCode -eq 200) {
      Write-Host 'Both Demo are UP!'
      exit 0
    }
  } catch { }
  Start-Sleep -Seconds 3
}
Write-Host 'Timeout'
exit 1
