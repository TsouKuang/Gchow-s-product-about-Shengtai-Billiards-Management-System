$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../..')).Path
$backendId=Get-Content -LiteralPath (Join-Path $root 'logs/backend.pid') -ErrorAction SilentlyContinue
$listener=Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if($listener -and ($listener.OwningProcess -ne $backendId -or (Get-Process -Id $backendId).ProcessName -ne 'java')){
 throw '8080 由其他进程使用，请先关闭占用端口的程序。'
}
if(!(Get-NetTCPConnection -LocalPort 3306 -State Listen -ErrorAction SilentlyContinue)){
 throw '请先启动本机 MySQL 3306。'
}
Write-Host '小程序 AppID wx9bce388a1f85a83b'
$secret=Read-Host '请输入该小程序的 AppSecret（输入时隐藏）' -AsSecureString
if($secret.Length -eq 0){throw 'AppSecret 不能为空。'}
$secret | ConvertFrom-SecureString | Set-Content -LiteralPath (Join-Path $root '.local/wechat-secret.dpapi')
if($listener){Stop-Process -Id $backendId; Wait-Process -Id $backendId -ErrorAction SilentlyContinue}
& (Join-Path $root 'Start-Shengtai.ps1')
for($attempt=0;$attempt -lt 20;$attempt++){
 try{
  $health=Invoke-RestMethod 'http://127.0.0.1:8080/api/health' -TimeoutSec 2
  if($health.wechatLoginConfigured){
   Write-Host '后端已加载微信登录配置。'
   return
  }
 }catch{}
 Start-Sleep -Seconds 1
}
throw '微信登录配置未就绪，请检查后台启动日志。'
