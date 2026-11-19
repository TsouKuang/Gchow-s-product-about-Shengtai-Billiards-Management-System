$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$local=Join-Path $root '.local/cloud-deploy'
$key=Join-Path $local 'shengtai-server-rsa'
$known='C:\Users\tsouk\Documents\Codex\2026-11-04\jian\work\shengtai-known-hosts'
$endpoint='http://127.0.0.1:18080'
if(!(Test-Path -LiteralPath $key)){throw 'Deployment SSH key is missing'}
$listener=Get-NetTCPConnection -State Listen -LocalPort 18080 -ErrorAction SilentlyContinue
if(!$listener){
 $arguments=@('-N','-T','-L','127.0.0.1:18080:127.0.0.1:8080','-i',('"'+$key+'"'),'-o','IdentitiesOnly=yes','-o','BatchMode=yes','-o','ExitOnForwardFailure=yes','-o','ServerAliveInterval=30','-o','ServerAliveCountMax=3','-o','ConnectTimeout=8','-o','StrictHostKeyChecking=yes','-o',('UserKnownHostsFile='+$known),'root@39.108.142.18')
 $process=Start-Process -FilePath (Get-Command ssh.exe).Source -ArgumentList $arguments -WindowStyle Hidden -RedirectStandardError (Join-Path $local 'experiment-tunnel-error.log') -PassThru
 $process.Id|Set-Content -LiteralPath (Join-Path $local 'experiment-tunnel.pid')
}
$ready=$false
for($i=0;$i -lt 15;$i++){
 try {
  $health=Invoke-RestMethod -Uri "$endpoint/api/health" -TimeoutSec 2
  if($health.status -eq 'UP'){$ready=$true;break}
 } catch {}
 Start-Sleep -Milliseconds 500
}
if(!$ready){throw 'SSH experiment connection failed; see .local/cloud-deploy/experiment-tunnel-error.log'}
Write-Host 'Private cloud experiment connection is ready: http://127.0.0.1:18080'
