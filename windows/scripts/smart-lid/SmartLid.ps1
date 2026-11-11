[CmdletBinding()]
param(
 [ValidateSet('Install','Launch','Guard','Remove')][string]$Mode='Install',
 [ValidateRange(5,600)][int]$DelaySeconds=30,
 [string[]]$DockIds,
 [string]$Executable=(Join-Path $PSScriptRoot 'SmartLid.exe')
)
$ErrorActionPreference='Stop'
$root=Join-Path $env:LOCALAPPDATA 'CreteOS-Bootstrap\SmartLid'
$configFile=Join-Path $root 'config.json'
$heartbeat=Join-Path $root 'heartbeat'
$workerLog=Join-Path $root 'worker.log'
$supervisorLog=Join-Path $root 'supervisor.log'
$runner=Join-Path $root 'SmartLid.ps1'
$exe=Join-Path $root 'SmartLid.exe'
$launcher=Join-Path $root 'LaunchHidden.vbs'
$startTask='CreteOS Smart Lid'
$guardTask='CreteOS Smart Lid Recovery'
$power=Join-Path $env:SystemRoot 'System32\powercfg.exe'
$ps=Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'

function Invoke-PowerCfg {
 $info=[Diagnostics.ProcessStartInfo]::new($power,(($args|ForEach-Object {'"{0}"' -f $_}) -join ' '))
 $info.UseShellExecute=$false
 $info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true
 $process=[Diagnostics.Process]::Start($info)
 try{
  $output=$process.StandardOutput.ReadToEnd()
  $process.WaitForExit()
  if($process.ExitCode -ne 0){throw "powercfg $($args[0]) failed ($($process.ExitCode))"}
  $output
 } finally {$process.Dispose()}
}
function Get-ActivePlan {
 $result=(Invoke-PowerCfg /getactivescheme) -join ' '
 $match=[regex]::Match($result,'[\da-fA-F-]{36}')
 if(!$match.Success){throw 'Active power plan could not be identified'}
 return $match.Value
}
function Write-Log([string]$message){
 Add-Content -LiteralPath $supervisorLog -Value ("$(Get-Date -Format o) $message")
}
function Restore-Plan($config){
 if((Get-ActivePlan) -eq $config.ManagedPlan){
  Invoke-PowerCfg /setactive $config.OriginalPlan|Out-Null
  if((Get-ActivePlan) -ne $config.OriginalPlan){throw 'Original power plan was not restored'}
  Write-Log 'Original Windows power plan restored'
 }
}
function Test-Heartbeat([datetime]$after){
 $file=Get-Item -LiteralPath $heartbeat -ErrorAction SilentlyContinue
 return $null -ne $file -and $file.LastWriteTimeUtc -ge $after
}
function New-Action([string]$runMode){
 $trace=Join-Path $root ("task-"+$runMode+".log")
 $command='& "'+$runner+'" -Mode '+$runMode+' *> "'+$trace+'"'
 $encoded=[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
 $wscript=Join-Path $env:SystemRoot 'System32\wscript.exe'
 New-ScheduledTaskAction -Execute $wscript -Argument ('//B //Nologo "'+$launcher+'" "'+$ps+'" '+$encoded) -WorkingDirectory $root
}
function Remove-Tasks {
 foreach($name in $startTask,$guardTask){
  if(Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue){
   Stop-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue
   Unregister-ScheduledTask -TaskName $name -Confirm:$false
  }
 }
}

if($Mode -ne 'Install'){
 $config=Get-Content -LiteralPath $configFile -Raw|ConvertFrom-Json
}
if($Mode -eq 'Guard'){
 if((Get-ActivePlan) -ne $config.ManagedPlan -or (Test-Heartbeat ([datetime]::UtcNow.AddSeconds(-25)))){return}
 # A wake can briefly expose the pre-hibernate heartbeat before the desktop responds.
 Start-Sleep -Seconds 8
 if(!(Test-Heartbeat ([datetime]::UtcNow.AddSeconds(-25)))){
  Write-Log 'Recovery task detected a stale or missing heartbeat'
  Restore-Plan $config
 }
 return
}
if($Mode -eq 'Remove'){
 Disable-ScheduledTask -TaskName $startTask -ErrorAction SilentlyContinue|Out-Null
 Restore-Plan $config
 Remove-Tasks
 Get-Process SmartLid -ErrorAction SilentlyContinue|Where-Object Path -eq $exe|Stop-Process
 Invoke-PowerCfg /delete $config.ManagedPlan|Out-Null
 Write-Log 'Smart Lid disabled and managed power plan removed'
 return
}
if($Mode -eq 'Launch'){
 $worker=$null
 try {
  Restore-Plan $config
  if((Get-ActivePlan) -ne $config.OriginalPlan){throw 'A different Windows power plan is selected'}
  if(!(Get-ScheduledTask -TaskName $guardTask).Settings.Enabled){throw 'Recovery task is disabled'}
  $started=[datetime]::UtcNow
  $arguments=@('--live',$config.DelaySeconds,'0',('"{0}"' -f $workerLog),('"{0}"' -f $heartbeat),$config.ManagedPlan)
  $arguments+=@($config.DockIds|ForEach-Object {'"{0}"' -f $_})
  $worker=Start-Process -FilePath $exe -ArgumentList ($arguments -join ' ') -WindowStyle Hidden -PassThru -RedirectStandardError (Join-Path $root 'worker-error.log')
  $deadline=$started.AddSeconds(12)
  while(!(Test-Heartbeat $started)){
   if($worker.WaitForExit(250) -or [datetime]::UtcNow -gt $deadline){throw 'Live observer did not report a known lid and dock state'}
  }
  if((Get-ActivePlan) -ne $config.OriginalPlan){throw 'Power plan changed while starting Smart Lid'}
  Invoke-PowerCfg /setactive $config.ManagedPlan|Out-Null
  if((Get-ActivePlan) -ne $config.ManagedPlan){throw 'Managed power plan did not activate'}
  Write-Log "Live Smart Lid active. Observer PID $($worker.Id). Delay $($config.DelaySeconds) seconds"
  while(!$worker.WaitForExit(3000)){
   if((Get-ActivePlan) -ne $config.ManagedPlan){break}
  }
  if($worker.HasExited){Write-Log "Observer exited with code $($worker.ExitCode)"}
 } catch {
  Write-Log $_.Exception.Message
  throw
 } finally {
  if($worker -and !$worker.HasExited){Stop-Process -Id $worker.Id -ErrorAction SilentlyContinue}
  Restore-Plan $config
 }
 return
}

if((Get-ScheduledTask -TaskName $startTask -ErrorAction SilentlyContinue) -or (Get-ScheduledTask -TaskName $guardTask -ErrorAction SilentlyContinue)){
 throw 'Smart Lid is already configured. Use -Mode Remove before reinstalling.'
}
if(!$DockIds){
 $present=@(Get-PnpDevice -PresentOnly|Where-Object InstanceId -match '^USB\\VID_(343C&PID_0000|0B95&PID_1790)\\')
 if($present.Count -ne 2 -or @($present|Where-Object InstanceId -match 'VID_343C').Count -ne 1){
  throw 'Connect the TV dock and rerun, or supply the two confirmed device instance IDs.'
 }
 $DockIds=@($present.InstanceId)
}
$DockIds=@($DockIds|Select-Object -Unique)
if($DockIds.Count -lt 2 -or @($DockIds|Where-Object {$_ -notmatch '^USB\\VID_[0-9A-F]{4}&PID_[0-9A-F]{4}\\[^"\r\n]+$'}).Count){
 throw 'Supply two or more confirmed USB device instance IDs'
}
$Executable=(Resolve-Path -LiteralPath $Executable).ProviderPath
if(!((Invoke-PowerCfg /a) -join "`n" -match '(?im)^\s+Hibernate\s*$')){throw 'Windows hibernation is unavailable'}
$original=Get-ActivePlan
$managed=[guid]::NewGuid().ToString()
New-Item -ItemType Directory -Path $root -Force|Out-Null
Copy-Item -LiteralPath $Executable -Destination $exe -Force
Copy-Item -LiteralPath $PSCommandPath -Destination $runner -Force
@"
Set shell = CreateObject("WScript.Shell")
command = Chr(34) & WScript.Arguments(0) & Chr(34) & " -NoProfile -NonInteractive -ExecutionPolicy Bypass -EncodedCommand " & WScript.Arguments(1)
WScript.Quit shell.Run(command, 0, True)
"@ | Set-Content -LiteralPath $launcher -Encoding ASCII
$config=[pscustomobject]@{OriginalPlan=$original;ManagedPlan=$managed;DelaySeconds=$DelaySeconds;DockIds=$DockIds}
$config|ConvertTo-Json|Set-Content -LiteralPath $configFile -Encoding UTF8
$created=$false
try {
 Invoke-PowerCfg /duplicatescheme $original $managed|Out-Null
 $created=$true
 Invoke-PowerCfg /changename $managed 'CreteOS Smart Lid'|Out-Null
 Invoke-PowerCfg /setacvalueindex $managed SUB_BUTTONS LIDACTION 0|Out-Null
 Invoke-PowerCfg /setdcvalueindex $managed SUB_BUTTONS LIDACTION 0|Out-Null
 $identity=[Security.Principal.WindowsIdentity]::GetCurrent().Name
 $user=New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
 $guard=New-ScheduledTaskPrincipal -UserId $identity -LogonType S4U -RunLevel Limited
 $startSettings=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -ExecutionTimeLimit ([timespan]::Zero)
 $guardSettings=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -MultipleInstances IgnoreNew -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Seconds 35)
 $schedule=New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
 try {
  Register-ScheduledTask -TaskName $guardTask -Action (New-Action Guard) -Trigger $schedule -Principal $guard -Settings $guardSettings|Out-Null
 } catch {
  $guard=$user
  Register-ScheduledTask -TaskName $guardTask -Action (New-Action Guard) -Trigger $schedule -Principal $guard -Settings $guardSettings|Out-Null
 }
 Register-ScheduledTask -TaskName $startTask -Action (New-Action Launch) -Trigger (New-ScheduledTaskTrigger -AtLogOn -User $identity) -Principal $user -Settings $startSettings|Out-Null
 Start-ScheduledTask -TaskName $startTask
 $deadline=(Get-Date).AddSeconds(20)
 while((Get-ActivePlan) -ne $managed -and (Get-Date) -lt $deadline){Start-Sleep -Milliseconds 500}
 if((Get-ActivePlan) -ne $managed){throw 'Smart Lid startup did not activate the managed plan'}
 Write-Log "Installed. Recovery task logon type: $($guard.LogonType)"
} catch {
 try {Restore-Plan $config} finally {
  Remove-Tasks
  if($created){Invoke-PowerCfg /delete $managed|Out-Null}
 }
 throw
}
