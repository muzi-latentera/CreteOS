[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$PackRoot=(Split-Path $PSScriptRoot),[switch]$UseG1DisplayProfiles)
$ErrorActionPreference='Stop'
$pack=(Resolve-Path -LiteralPath $PackRoot).ProviderPath
$info=Get-Content -LiteralPath (Join-Path $pack 'PACKAGE.json') -Raw -Encoding UTF8|ConvertFrom-Json
foreach($item in @($info.required)){
 $path=[IO.Path]::GetFullPath((Join-Path $pack $item.path))
 if(!$path.StartsWith($pack.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe package path'}
 if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $item.sha256){throw ('Package verification failed: '+$item.path)}
}
$target=Join-Path $env:LOCALAPPDATA 'CreteOS'
if(!$PSCmdlet.ShouldProcess($target,'Install bootstrap if needed, deploy exact verified current executable and restore profiles; leave credentials, drivers and hardware settings alone')){return}
if($env:COMPUTERNAME -eq 'OXPG1'){throw 'This is the source G1. Use the recovery package on the replacement Windows machine.'}
if(Get-Process creteos -ErrorAction SilentlyContinue){throw 'Close CreteOS normally before setup.'}
$start=Get-Date
$stage=Join-Path $env:LOCALAPPDATA ('CreteOS-Bootstrap\migration\'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $stage -Force|Out-Null
$zip=Join-Path $pack 'Profile-Backup.zip'
# Reject traversing ZIP entries before extraction or installation.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($zip)
try{foreach($entry in $archive.Entries){$resolved=[IO.Path]::GetFullPath((Join-Path $stage $entry.FullName));if(!$resolved.StartsWith($stage.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe ZIP entry'}}}finally{$archive.Dispose()}
[IO.Compression.ZipFile]::ExtractToDirectory($zip,$stage)
$manifest=Get-Content -LiteralPath (Join-Path $stage 'manifest.json') -Raw -Encoding UTF8|ConvertFrom-Json
foreach($item in @($manifest.files)){
 $file=[IO.Path]::GetFullPath((Join-Path $stage $item.path))
 if(!$file.StartsWith($stage.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase) -or (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $item.sha256){throw 'Extracted profile verification failed'}
}
$currentExe=Join-Path $pack 'Recovery\creteos.exe'
if(!(Test-Path -LiteralPath (Join-Path $target 'creteos.exe'))){
 $installer=Join-Path $pack 'Installers\CreteOS-bootstrap-only.exe'
 $process=Start-Process -FilePath $installer -ArgumentList '/S' -WindowStyle Hidden -PassThru -Wait
 if($process.ExitCode -notin @(0,3010)){throw ('Bootstrap installer failed: '+$process.ExitCode)}
 if(!(Test-Path -LiteralPath (Join-Path $target 'creteos.exe'))){throw 'Bootstrap did not install to the current-user location; inspect before continuing'}
 # Only close a CreteOS instance created by this fresh install, normally.
 foreach($app in @(Get-Process creteos -ErrorAction SilentlyContinue)){
  if($app.StartTime -lt $start -or $app.Path -ne (Join-Path $target 'creteos.exe')){throw 'Unexpected running CreteOS instance; close it normally'}
  [void]$app.CloseMainWindow()
 }
 $deadline=(Get-Date).AddSeconds(15)
 while((Get-Process creteos -ErrorAction SilentlyContinue) -and (Get-Date) -lt $deadline){Start-Sleep -Milliseconds 250}
 if(Get-Process creteos -ErrorAction SilentlyContinue){throw 'Close the new CreteOS window normally, then rerun setup'}
}
Copy-Item -LiteralPath (Join-Path $target 'creteos.exe') -Destination (Join-Path $stage 'before-creteos.exe')
Copy-Item -LiteralPath $currentExe -Destination (Join-Path $target 'creteos.exe') -Force
if((Get-FileHash -LiteralPath (Join-Path $target 'creteos.exe') -Algorithm SHA256).Hash -ne $info.executableSHA256){throw 'Deployed executable mismatch'}
& (Join-Path $stage 'Restore-CreteOS.ps1') -Destination $target -KeepG1DisplayProfiles:$UseG1DisplayProfiles
@{time=(Get-Date).ToString('o');target=$target;executableSHA256=$info.executableSHA256;profileArchiveSHA256=$info.profileArchiveSHA256;g1DisplayProfiles=[bool]$UseG1DisplayProfiles;credentialsRestored=$false;hardwareSettingsApplied=$false;gamesLaunched=$false;manualSteps='Read Documentation/G1_SETUP_AND_RECOVERY.md and START-HERE.txt; install/authenticate streaming apps, fresh pairings and device-specific helper configuration.'}|ConvertTo-Json|Set-Content (Join-Path $stage 'installation-receipt.json') -Encoding UTF8
Write-Output ('CreteOS and saved profile restored. Receipt: '+$stage+'\installation-receipt.json')
Write-Output 'No app/game launched. Finish the documented streaming app/account/pairing and hardware-specific steps before enabling automatic startup.'
