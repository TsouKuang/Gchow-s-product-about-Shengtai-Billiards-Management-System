$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
if(Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue){return}
# MySQL on this computer requires an ASCII path. The junction points to the real desktop data.
$alias='C:\Users\tsouk\Documents\Codex\2026-11-04\jian\work\graduation-project'
if(!(Test-Path -LiteralPath $alias)){New-Item -ItemType Junction -Path $alias -Target $root|Out-Null}
$bin='C:\Program Files\MySQL\MySQL Server 9.3\bin\mysqld.exe'
$process=Start-Process -FilePath $bin -ArgumentList "--defaults-file=`"$alias\.local\mysql-current.ini`"",'--bind-address=127.0.0.1','--mysqlx=0' -WindowStyle Hidden -PassThru
$process.Id|Set-Content -LiteralPath (Join-Path $root '.local/mysql-current.pid')
for($i=0;$i -lt 20;$i++){
 if(Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue){return}
 if($process.HasExited){throw 'MySQL startup failed. Check .local/mysql-current-data/*.err'}
 Start-Sleep -Milliseconds 500
}
throw 'MySQL startup timed out'
