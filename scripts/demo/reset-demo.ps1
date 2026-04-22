[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$resetUrl = 'http://127.0.0.1:8081/api/demo/reset'

Invoke-RestMethod -Method Post -Uri $resetUrl | Out-Null
Write-Host "Demo data reset: $resetUrl"
