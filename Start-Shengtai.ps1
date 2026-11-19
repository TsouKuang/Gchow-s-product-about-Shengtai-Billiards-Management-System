$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'scripts/windows/Start-Database.ps1')
$configFile=Join-Path $PSScriptRoot '.local/mysql3306-credentials.json'
if(!(Test-Path -LiteralPath $configFile)){throw '缺少本机私有数据库配置，请设置MySQL环境变量并使用Start-Local.ps1。'}
if(!(Get-NetTCPConnection -State Listen -LocalPort 3306 -ErrorAction SilentlyContinue)){throw 'MySQL 3306尚未启动，请先启动原MySQL实例。不要使用旧3307开发实例。'}
$settings=Get-Content -LiteralPath $configFile -Raw | ConvertFrom-Json
$env:SPRING_PROFILES_ACTIVE='mysql'
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/billiard_db?serverTimezone=Asia/Shanghai&characterEncoding=utf8'
$env:DB_USER=$settings.username
$env:DB_PASSWORD=$settings.appPassword
$env:ADMIN_PASSWORD='Admin123!'
$env:DEMO_ENABLED='true'
$wechatConfig=Join-Path $PSScriptRoot '.local/wechat-secret.dpapi'
if(Test-Path -LiteralPath $wechatConfig){
 $secret=(Get-Content -LiteralPath $wechatConfig -Raw).Trim() | ConvertTo-SecureString
 $env:WECHAT_APPID='wx9bce388a1f85a83b'
 $env:WECHAT_SECRET=[System.Net.NetworkCredential]::new('', $secret).Password
}
if(!$env:JAVA_HOME){$localJava='C:\Users\tsouk\.jdks\corretto-23.0.2';if(Test-Path -LiteralPath "$localJava/bin/java.exe"){$env:JAVA_HOME=$localJava}}
try { & (Join-Path $PSScriptRoot 'Start-Local.ps1') }
finally {Remove-Item Env:DB_PASSWORD,Env:WECHAT_SECRET -ErrorAction SilentlyContinue}
