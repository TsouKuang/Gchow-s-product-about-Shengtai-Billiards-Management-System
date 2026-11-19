$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{(Get-Command java.exe).Source}
Push-Location "$root/backend"
try {
 if(!(Test-Path target/billiard-server-0.1.0.jar)) {
  $mavenHome=if($env:MAVEN_HOME){$env:MAVEN_HOME}else{[Environment]::GetEnvironmentVariable('MAVEN_HOME','User')}
  $mvn=if($mavenHome){Join-Path $mavenHome 'bin/mvn.cmd'}else{(Get-Command mvn.cmd).Source}
  & $mvn -B package
  if($LASTEXITCODE -ne 0){throw 'Backend build failed'}
 }
} finally {Pop-Location}
Push-Location "$root/admin-web"
try {
 if(!(Test-Path node_modules/vite/bin/vite.js)) {
  & npm.cmd ci
  if($LASTEXITCODE -ne 0){throw 'Frontend install failed'}
 }
} finally {Pop-Location}
New-Item -ItemType Directory -Force "$root/logs" | Out-Null
if(!(Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)) {
 $p=Start-Process -FilePath $java -ArgumentList '-jar','target/billiard-server-0.1.0.jar' -WorkingDirectory "$root/backend" -WindowStyle Hidden -RedirectStandardOutput "$root/logs/backend.log" -RedirectStandardError "$root/logs/backend-error.log" -PassThru
 $p.Id | Set-Content "$root/logs/backend.pid"
}
if(!(Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue)) {
 $p=Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList 'node_modules/vite/bin/vite.js','--host','127.0.0.1' -WorkingDirectory "$root/admin-web" -WindowStyle Hidden -RedirectStandardOutput "$root/logs/frontend.log" -RedirectStandardError "$root/logs/frontend-error.log" -PassThru
 $p.Id | Set-Content "$root/logs/frontend.pid"
}
Write-Host 'PC: http://127.0.0.1:5173 ; API: http://127.0.0.1:8080/api/health'
