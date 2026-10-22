$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'Sessions.ps1')
$folder=Join-Path ([IO.Path]::GetTempPath()) ('crete-wifi-tests-'+[guid]::NewGuid())
New-Item -ItemType Directory $folder|Out-Null
try {
 $epoch=[DateTime]::SpecifyKind([DateTime]'1970-01-01',[DateTimeKind]::Utc)
 $now=([DateTime]::UtcNow-$epoch).TotalSeconds
 @{ownerPid=$PID;createdUnix=$now}|ConvertTo-Json|Set-Content (Join-Path $folder '1-1.json')
 if(!(Test-WifiSessions $folder)){throw 'Live owner must hold the network'}
 if(Test-WifiSessions $folder '1-1'){throw 'Preflight must exclude its own lease'}
 @{ownerPid=$PID;createdUnix=$now}|ConvertTo-Json|Set-Content (Join-Path $folder '1-2.json')
 if(!(Test-WifiSessions $folder '1-1')){throw 'Second active session must prevent switching'}
 Remove-Item -LiteralPath (Join-Path $folder '1-1.json'),(Join-Path $folder '1-2.json')
 @{ownerPid=2147483647;createdUnix=$now}|ConvertTo-Json|Set-Content (Join-Path $folder 'dead.json')
 if(Test-WifiSessions $folder){throw 'Dead owner must not hold network'}
 @{ownerPid=$PID;createdUnix=0}|ConvertTo-Json|Set-Content (Join-Path $folder 'reused.json')
 if(Test-WifiSessions $folder){throw 'Reused PID must not hold network'}
 'bad json'|Set-Content (Join-Path $folder 'partial.json')
 if(Test-WifiSessions $folder){throw 'Invalid lease must not hold network'}
 foreach($preference in @('Gaming','Download','Current')) {
  foreach($away in @('Hotel','')) {
   if((Get-WifiSessionTarget $preference $away 'Extra' 'Fibre') -ne $away){throw 'Away/disconnected Wi-Fi changed'}
  }
 }
 if((Get-WifiSessionTarget 'Gaming' 'Fibre' 'Extra' 'Fibre') -ne 'Extra'){throw 'Moonlight default'}
 if((Get-WifiSessionTarget 'Download' 'Extra' 'Extra' 'Fibre') -ne 'Fibre'){throw 'Cloud default'}
 if((Get-WifiSessionTarget 'Current' 'Fibre' 'Extra' 'Fibre') -ne 'Fibre'){throw 'Keep current'}
 'PASS: live, concurrent, excluded, dead, reused PID and malformed leases; home/away/current defaults'
} finally {
 # Only files created inside this exact test directory are removed.
 Get-ChildItem -LiteralPath $folder -File|Remove-Item
 Remove-Item -LiteralPath $folder
}
