param([ValidateSet('Monitor','Status','DryRun','PrepareGaming','Gaming','Download','Auto','Pause')][string]$Mode='Monitor')
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Policy.ps1')
$configPath=Join-Path $PSScriptRoot 'config.json'
$controlPath=Join-Path $PSScriptRoot 'control.json'
$statePath=Join-Path $PSScriptRoot 'status.json'
$logPath=Join-Path $PSScriptRoot 'events.log'
$config=Get-Content $configPath -Raw|ConvertFrom-Json
$homeProfiles=@([string]$config.GamingProfile,[string]$config.DownloadProfile)
$connectLock=New-Object Threading.Mutex($false,'Local\CreteOS-WiFiSmart-Connect')
function Read-Control {
 try { return Get-Content $controlPath -Raw|ConvertFrom-Json } catch { return [pscustomobject]@{Paused=$false;Override='';OverrideUntilUtc='';GamingUntilUtc=''} }
}
function Save-Json($Path,$Value) {
 $tmp=$Path+'.tmp-'+$PID
 [IO.File]::WriteAllText($tmp,($Value|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
 Move-Item -LiteralPath $tmp -Destination $Path -Force
}
function Write-Event($Event,$Reason,$Network) {
 if((Test-Path $logPath) -and (Get-Item $logPath).Length -gt 524288){Copy-Item $logPath ($logPath+'.previous') -Force;[IO.File]::WriteAllText($logPath,'')}
 $line=@{time=[DateTime]::UtcNow.ToString('o');event=$Event;reason=$Reason;network=$Network}|ConvertTo-Json -Compress
 Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
}
function Current-Profile {
 $lines=& "$env:SystemRoot\System32\netsh.exe" wlan show interfaces
 $match=$lines|Select-String '^\s*Profile\s*:\s*(.+)$'|Select-Object -First 1
 if($match){return $match.Matches[0].Groups[1].Value.Trim()}
 return ''
}
function Is-Streaming {return [bool](Get-Process Moonlight,GeForceNOWStreamer -ErrorAction SilentlyContinue)}
function Available($Profile) {
 $lines=& "$env:SystemRoot\System32\netsh.exe" wlan show networks "interface=$($config.Interface)"
 return [bool]($lines|Where-Object {$_ -match '^\s*SSID\s+\d+\s*:\s*(.+)$' -and $Matches[1].Trim() -eq $Profile})
}
function Refresh-VisibleProfiles {
 try {
  if(-not ('CreteOSWifiNativeScan' -as [type])){
   Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class CreteOSWifiNativeScan { [DllImport("wlanapi.dll")] public static extern uint WlanOpenHandle(uint v, IntPtr r, out uint n, out IntPtr h); [DllImport("wlanapi.dll")] public static extern uint WlanScan(IntPtr h, ref Guid g, IntPtr s, IntPtr i, IntPtr r); [DllImport("wlanapi.dll")] public static extern uint WlanCloseHandle(IntPtr h, IntPtr r); }'
  }
  [uint32]$version=0;[IntPtr]$handle=[IntPtr]::Zero
  if([CreteOSWifiNativeScan]::WlanOpenHandle(2,[IntPtr]::Zero,[ref]$version,[ref]$handle) -ne 0){return}
  try {$guid=[Guid]$config.InterfaceGuid;$result=[CreteOSWifiNativeScan]::WlanScan($handle,[ref]$guid,[IntPtr]::Zero,[IntPtr]::Zero,[IntPtr]::Zero)}finally{[void][CreteOSWifiNativeScan]::WlanCloseHandle($handle,[IntPtr]::Zero)}
  if($result -eq 0){Start-Sleep -Seconds 2}else{Write-Event 'scan-unavailable' ([string]$result) ''}
 } catch {Write-Event 'scan-unavailable' 'scan-api-error' ''}
}
function Connect-Profile($Target,$Reason,[bool]$Preparing=$false) {
 $locked=$false
 try {
  try {$locked=$connectLock.WaitOne(1000)}catch [Threading.AbandonedMutexException]{$locked=$true}
  if(!$locked){return $false}
  $current=Current-Profile
  if($current -eq $Target){return $true}
  if($homeProfiles -notcontains $current -or (Is-Streaming)){Write-Event 'held' 'outside-home-or-active-stream' $current;return $false}
  if(!(Available $Target)){Refresh-VisibleProfiles}
  if(!(Available $Target)){Write-Event 'unavailable' $Reason $Target;return $false}
  if(Is-Streaming){Write-Event 'held' 'stream-started-during-preparation' $current;return $false}
  & "$env:SystemRoot\System32\netsh.exe" wlan connect "name=$Target" "interface=$($config.Interface)" *> $null
  if($LASTEXITCODE -ne 0){Write-Event 'failed' $Reason $Target;return $false}
  $deadline=[DateTime]::UtcNow.AddSeconds(6)
  do {
   Start-Sleep -Milliseconds 350
   if((Current-Profile) -eq $Target){Write-Event 'connected' $Reason $Target;return $true}
  } while([DateTime]::UtcNow -lt $deadline)
  Write-Event 'failed' 'connection-not-confirmed' $Target
  # Reconnect the previous saved home profile after a failed attempt, if still disconnected.
  if(!(Current-Profile)){& "$env:SystemRoot\System32\netsh.exe" wlan connect "name=$current" "interface=$($config.Interface)" *> $null}
  return $false
 } finally {if($locked){$connectLock.ReleaseMutex()}}
}
function Set-Control($Change) {
 $gate=New-Object Threading.Mutex($false,'Local\CreteOS-WiFiSmart-Control')
 $locked=$false
 try {
  try {$locked=$gate.WaitOne(2000)}catch [Threading.AbandonedMutexException]{$locked=$true};if(!$locked){throw 'Wi-Fi control is busy; try again'}
  $control=Read-Control
  foreach($name in $Change.Keys){$control|Add-Member -NotePropertyName $name -NotePropertyValue $Change[$name] -Force}
  Save-Json $controlPath $control
 } finally {if($locked){$gate.ReleaseMutex()};$gate.Dispose()}
}
function Until-Active($Text) {
 try {return [DateTime]::Parse([string]$Text).ToUniversalTime() -gt [DateTime]::UtcNow} catch{return $false}
}
function Steam-Downloading {
 if(!(Get-Process steam -ErrorAction SilentlyContinue)){return $false}
 $path='C:\Program Files (x86)\Steam\logs\content_log.txt'
 if(!(Test-Path $path)){return $false}
 $stamp=(Get-Item $path).LastWriteTimeUtc
 if($script:steamStamp -ne $stamp){
  $states=@{}
  foreach($line in (Get-Content $path -Tail 2500 -ErrorAction SilentlyContinue)){
   if($line -match 'AppID\s+(\d+)\s+App update changed\s*:\s*(.*)$'){$states[$Matches[1]]=$Matches[2]}
  }
  $script:steamActive=[bool](@($states.Values|Where-Object {$_ -match '\bDownloading\b'}).Count)
  $script:steamStamp=$stamp
 }
 return [bool]$script:steamActive
}
function Receive-Counter {
 $adapter=[Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()|Where-Object {$_.Id.Trim('{}') -eq ([string]$config.InterfaceGuid).Trim('{}')}|Select-Object -First 1
 if(!$adapter -or $adapter.OperationalStatus -ne 'Up'){return $null}
 return [double]$adapter.GetIPv4Statistics().BytesReceived
}
if($Mode -eq 'Status'){if(Test-Path $statePath){Get-Content $statePath -Raw}else{'No monitor status yet'};exit 0}
if($Mode -eq 'PrepareGaming') {
 $current=Current-Profile;$control=Read-Control
 if(!$config.Enabled -or $control.Paused -or $homeProfiles -notcontains $current){exit 0}
 Set-Control @{GamingUntilUtc=[DateTime]::UtcNow.AddMinutes(2).ToString('o')}
 if($current -eq $config.GamingProfile){exit 0}
 if(Connect-Profile $config.GamingProfile 'creteos-pre-launch' $true){Start-Sleep -Milliseconds 500;exit 0}
 exit 2
}
if($Mode -in @('Gaming','Download','Auto','Pause')) {
 if($Mode -eq 'Pause'){Set-Control @{Paused=$true;Override='';OverrideUntilUtc=''};Write-Event 'mode' 'paused' (Current-Profile);exit 0}
 Set-Control @{Paused=$false;Override=$(if($Mode -eq 'Auto'){''}else{$Mode});OverrideUntilUtc=$(if($Mode -eq 'Auto'){''}else{[DateTime]::UtcNow.AddHours(2).ToString('o')})}
 if($Mode -ne 'Auto'){
  $target=if($Mode -eq 'Gaming'){$config.GamingProfile}else{$config.DownloadProfile}
  if(!(Connect-Profile $target ('manual-'+$Mode.ToLower()))){exit 2}
 }
 Write-Event 'mode' $Mode (Current-Profile);exit 0
}
$instance=New-Object Threading.Mutex($false,'Local\CreteOS-WiFiSmart-Monitor')
$owns=$false
try {
 try {$owns=$instance.WaitOne(0)}catch [Threading.AbandonedMutexException]{$owns=$true};if(!$owns){exit 0}
 $startupPending=$true;$powerEvents=$false
 try {Register-WmiEvent -Class Win32_PowerManagementEvent -SourceIdentifier 'CreteOSWiFiPower' -ErrorAction Stop|Out-Null;$powerEvents=$true}catch{}
 $lastRx=Receive-Counter;$lastSample=[DateTime]::UtcNow;$lastDemand=$lastSample;$residence=$lastSample;$priorProfile=Current-Profile
 $busySamples=0;$lastAttempt=[DateTime]::MinValue;$lastDO=[DateTime]::MinValue;$doActive=$false;$lastReason=''
 do {
  try {
   $now=[DateTime]::UtcNow;$current=Current-Profile;$control=Read-Control
   $streaming=Is-Streaming;$counter=Receive-Counter;$elapsed=($now-$lastSample).TotalSeconds
   if($powerEvents){
    foreach($event in @(Get-Event -SourceIdentifier 'CreteOSWiFiPower' -ErrorAction SilentlyContinue)){
     if([int]$event.SourceEventArgs.NewEvent.EventType -in @(7,18)){$startupPending=$true;$lastRx=$null;$busySamples=0}
     Remove-Event -EventIdentifier $event.EventIdentifier -ErrorAction SilentlyContinue
    }
   }
   $rate=0
   if($null -ne $lastRx -and $null -ne $counter -and $elapsed -ge 1 -and $elapsed -le 90 -and $counter -ge $lastRx){$rate=($counter-$lastRx)/$elapsed}
   $lastRx=$counter;$lastSample=$now
   if($elapsed -gt 45){$startupPending=$true;$lastDemand=$now;$busySamples=0;$residence=$now}
   if($current -ne $priorProfile){$residence=$now;$priorProfile=$current;$busySamples=0}
   if($rate -ge 262144){$busySamples++}else{$busySamples=0}
   $steam=Steam-Downloading
   if(($now-$lastDO).TotalSeconds -ge 60 -and !$streaming){
    try {$doActive=[bool](@(Get-DeliveryOptimizationStatus -ErrorAction Stop|Where-Object {[string]$_.Status -eq 'Downloading'}).Count)}catch{$doActive=$false}
    $lastDO=$now
   }
   $demand=($steam -or $doActive -or $busySamples -ge 2)
   # Smaller continued transfers keep download mode during variable-speed sections.
   if($demand -or $rate -ge 65536){$lastDemand=$now}
   $override=if(Until-Active $control.OverrideUntilUtc){[string]$control.Override}else{''}
   $decision=Get-WifiDecision -Current $current -Gaming $config.GamingProfile -Download $config.DownloadProfile -Streaming $streaming -Paused (!$config.Enabled -or [bool]$control.Paused) -GamingLease (Until-Active $control.GamingUntilUtc) -Override $override -Demand $demand -QuietSeconds ($now-$lastDemand).TotalSeconds -ResidenceSeconds ($now-$residence).TotalSeconds -StartupOrResume $startupPending
   $state=[ordered]@{time=$now.ToString('o');mode=$Mode;current=$current;target=$decision.Target;reason=$decision.Reason;receiveMbps=[Math]::Round($rate*8/1000000,2);steamDownload=$steam;windowsDownload=$doActive;streamGuard=$streaming;paused=[bool]$control.Paused;override=$override;startupOrResume=$startupPending;powerEvents=$powerEvents}
   if($decision.Reason -eq 'startup-resume-default' -and $decision.Target -eq $current){$startupPending=$false}
   if($Mode -eq 'DryRun'){$state|ConvertTo-Json;break}
   if($decision.Target -ne $current -and ($now-$lastAttempt).TotalSeconds -ge 90){
    $lastAttempt=$now
    if(Connect-Profile $decision.Target $decision.Reason){if($decision.Reason -eq 'startup-resume-default'){$startupPending=$false};$residence=[DateTime]::UtcNow;$priorProfile=$decision.Target;$busySamples=0;$lastRx=Receive-Counter;$lastSample=[DateTime]::UtcNow}
   }
   Save-Json $statePath $state
   if($lastReason -ne $decision.Reason){Write-Event 'decision' $decision.Reason $current;$lastReason=$decision.Reason}
  } catch {Write-Event 'error' $_.Exception.Message '';if($Mode -eq 'DryRun'){throw}}
  if($Mode -eq 'Monitor'){Start-Sleep -Seconds 15}
 } while($Mode -eq 'Monitor')
} finally {if($powerEvents){Unregister-Event -SourceIdentifier 'CreteOSWiFiPower' -ErrorAction SilentlyContinue};if($owns){$instance.ReleaseMutex()};$instance.Dispose();$connectLock.Dispose()}

