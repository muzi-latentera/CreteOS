<# Controlled G1 AX210 driver trial. Inspect is read-only; Apply requires elevation.
   Uses exported, hash-verified signed packages. Does not remove driver packages,
   reset network profiles, change BIOS/power settings or restart Windows. #>
param(
 [ValidateSet('Inspect','Previous','Current')][string]$Mode='Inspect',
 [Parameter(Mandatory=$true)][string]$PackageRoot,
 [Parameter(Mandatory=$true)][string]$OutputFolder
)
$ErrorActionPreference='Stop'
$hardware='PCI\VEN_8086&DEV_2725&SUBSYS_00248086'
$packageName=if($Mode -eq 'Current'){'oem6.inf'}else{'oem106.inf'}
$expectedVersion=if($Mode -eq 'Current'){'24.80.0.2'}else{'23.150.0.4'}
$root=(Resolve-Path -LiteralPath $PackageRoot).ProviderPath
New-Item -ItemType Directory -Force -Path $OutputFolder|Out-Null
$reportPath=Join-Path $OutputFolder ('driver-trial-'+$Mode+'.json')
$r=[ordered]@{started=(Get-Date).ToString('o');mode=$Mode;phase='validating';changed=$false;rebootRequested=$false;expectedVersion=$expectedVersion}
function Save-Report {$r|ConvertTo-Json -Depth 7|Set-Content -LiteralPath $reportPath -Encoding UTF8}
function Get-Driver {return @(Get-CimInstance Win32_PnPSignedDriver|Where-Object {$_.DeviceClass -eq 'NET' -and $_.DeviceID.StartsWith($hardware,[StringComparison]::OrdinalIgnoreCase)})}
try {
 $manifest=Get-Content -LiteralPath (Join-Path $root 'manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
 foreach($item in @($manifest.files)){
  $file=[IO.Path]::GetFullPath((Join-Path $root $item.path))
  if(!$file.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Package manifest path leaves recovery folder'}
  if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.sha256){throw 'Package hash verification failed'}
 }
 $inf=Join-Path $root ($packageName+'\netwtw6e.inf')
 $cat=Join-Path $root ($packageName+'\Netwtw6e.cat')
 $sig=Get-AuthenticodeSignature -LiteralPath $cat
 if($sig.Status -ne 'Valid'){throw 'Driver catalog signature invalid'}
 $text=Get-Content -LiteralPath $inf -Raw
 if(!$text.Contains($hardware) -or $text -notmatch ('DriverVer\s*=\s*[^,]+,'+[regex]::Escape($expectedVersion))){throw 'Unexpected hardware or driver version in selected package'}
 $before=@(Get-Driver)
 $r.matchingDeviceCount=$before.Count
 if($before.Count -ne 1){throw 'Expected exactly one matching AX210 network device'}
 $r.before=$before|Select-Object DeviceName,DeviceID,DriverVersion,InfName
 $r.package=$packageName;$r.infSHA256=(Get-FileHash -LiteralPath $inf -Algorithm SHA256).Hash
 $r.catalogSHA256=(Get-FileHash -LiteralPath $cat -Algorithm SHA256).Hash;$r.catalogSignature=$sig.Status.ToString()
 $r.manifestSHA256=(Get-FileHash -LiteralPath (Join-Path $root 'manifest.json') -Algorithm SHA256).Hash
 $r.admin=([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
 if($Mode -eq 'Inspect'){$r.phase='verified-plan';Save-Report;exit 0}
 if(!$r.admin){throw 'Administrator approval is required'}
 if(Get-Process Moonlight,GeForceNOWStreamer,asobideck -ErrorAction SilentlyContinue){throw 'Streaming client is open; driver change deferred'}
 if($before[0].DriverVersion -eq $expectedVersion){$r.phase='already-selected';Save-Report;exit 0}
 $wifi=Join-Path $env:LOCALAPPDATA 'CreteOS-Bootstrap\WiFiSmart'
 foreach($name in @('config.json','control.json')){if(Test-Path (Join-Path $wifi $name)){Copy-Item -LiteralPath (Join-Path $wifi $name) -Destination (Join-Path $OutputFolder ('before-'+$name)) -Force}}
 $task=Get-ScheduledTask -TaskName 'CreteOS WiFi Smart' -ErrorAction SilentlyContinue
 $running=$task -and [string]$task.State -eq 'Running'
 $r.taskWasRunning=[bool]$running;$r.phase='applying';Save-Report
 try {
  if($running){Stop-ScheduledTask -TaskName 'CreteOS WiFi Smart'}
  Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class CreteAx210Trial { [DllImport("newdev.dll",CharSet=CharSet.Unicode,SetLastError=true)] [return:MarshalAs(UnmanagedType.Bool)] public static extern bool UpdateDriverForPlugAndPlayDevicesW(IntPtr parent,string hardware,string inf,uint flags,[MarshalAs(UnmanagedType.Bool)]out bool reboot); }'
  [bool]$reboot=$false
  $ok=[CreteAx210Trial]::UpdateDriverForPlugAndPlayDevicesW([IntPtr]::Zero,$hardware,$inf,1,[ref]$reboot)
  $errorCode=[Runtime.InteropServices.Marshal]::GetLastWin32Error()
  $r.apiSucceeded=$ok;$r.win32Error=if($ok){0}else{$errorCode};$r.rebootRequired=$reboot
  $r.changed=$ok;$r.phase='checking';Save-Report
  Start-Sleep -Seconds 12
  $r.after=Get-Driver|Select-Object DeviceName,DeviceID,DriverVersion,InfName
  $r.devices=@(Get-CimInstance Win32_PnPEntity|Where-Object {$_.PNPDeviceID -eq $before[0].DeviceID}|Select-Object Name,ConfigManagerErrorCode,Status)
  # Win32_PnPSignedDriver may lag behind a successful rebind; use live PnP properties.
  $version=Get-PnpDeviceProperty -InstanceId $before[0].DeviceID -KeyName 'DEVPKEY_Device_DriverVersion'
  $selectedInf=Get-PnpDeviceProperty -InstanceId $before[0].DeviceID -KeyName 'DEVPKEY_Device_DriverInfPath'
  $r.liveDriverVersion=[string]$version.Data;$r.liveDriverInf=[string]$selectedInf.Data
  $r.wlan=@(& netsh wlan show interfaces)
  $r.verifiedSelected=($r.liveDriverVersion -eq $expectedVersion -and $r.devices.Count -eq 1 -and @($r.devices|Where-Object ConfigManagerErrorCode -ne 0).Count -eq 0)
  if(!$ok){throw ('Driver API failed, Win32 '+$errorCode)}
  $r.phase=if($r.verifiedSelected){'selected-observation-pending'}else{'restart-or-review-required'}
 }finally{if($running){Start-ScheduledTask -TaskName 'CreteOS WiFi Smart';$r.taskRestored=$true}}
 $r.finished=(Get-Date).ToString('o');Save-Report
}catch{$r.phase='error';$r.error=$_.Exception.Message;$r.finished=(Get-Date).ToString('o');Save-Report;exit 1}
