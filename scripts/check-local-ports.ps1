$ports = 55432,56379,58080,53000,53001,53002
$listeners = Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $ports -contains $_.LocalPort } | Select-Object LocalAddress,LocalPort,OwningProcess
if ($listeners) { $listeners | Format-Table -AutoSize; Write-Host "One or more Suabhi ports are already in use. Change the corresponding *_HOST_PORT in .env." -ForegroundColor Yellow } else { Write-Host "All reserved Suabhi host ports are available." -ForegroundColor Green }
