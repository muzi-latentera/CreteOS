<# Reversible G1-only diagnostic; never installs/resets an adapter or reboots.
   Changes only active plan PCIe ASPM on AC/DC. Affects the plan's PCIe links,
   not just Wi-Fi; Off may increase power draw. Not proof of an AX210 fix. #>
param(
 [ValidateSet('Inspect','Apply','Restore')][string]$Mode='Inspect',
 [Parameter(Mandatory=$true)][string]$OutputFolder
)
$ErrorActionPreference='Stop'
if((Get-CimInstance Win32_ComputerSystem).Model -ne 'ONEXPLAYER G1 A'){throw 'G1 A only; review replacement hardware first'}
$nic=@(Get-PnpDevice -Class Net -PresentOnly|Where-Object InstanceId -like 'PCI\VEN_8086&DEV_2725&SUBSYS_00248086*')
if($nic.Count -ne 1){throw 'Expected one matching AX210'}
Add-Type -TypeDefinition @'
using System; using System.Runtime.InteropServices;
public static class G1PciePower {
 [DllImport("powrprof.dll")] public static extern uint PowerGetActiveScheme(IntPtr root,out IntPtr guid);
 [DllImport("powrprof.dll")] public static extern uint PowerReadACValueIndex(IntPtr root,ref Guid scheme,ref Guid subgroup,ref Guid setting,out uint value);
 [DllImport("powrprof.dll")] public static extern uint PowerReadDCValueIndex(IntPtr root,ref Guid scheme,ref Guid subgroup,ref Guid setting,out uint value);
 [DllImport("powrprof.dll")] public static extern uint PowerWriteACValueIndex(IntPtr root,ref Guid scheme,ref Guid subgroup,ref Guid setting,uint value);
 [DllImport("powrprof.dll")] public static extern uint PowerWriteDCValueIndex(IntPtr root,ref Guid scheme,ref Guid subgroup,ref Guid setting,uint value);
 [DllImport("powrprof.dll")] public static extern uint PowerSetActiveScheme(IntPtr root,ref Guid scheme);
 [DllImport("kernel32.dll")] public static extern IntPtr LocalFree(IntPtr ptr);
}
'@
function Check([uint32]$code){if($code -ne 0){throw "Windows power API returned $code"}}
[IntPtr]$ptr=[IntPtr]::Zero
Check ([G1PciePower]::PowerGetActiveScheme([IntPtr]::Zero,[ref]$ptr))
try {$scheme=[Guid][Runtime.InteropServices.Marshal]::PtrToStructure($ptr,[type][Guid])}finally{[void][G1PciePower]::LocalFree($ptr)}
$subgroup=[Guid]'501a4d13-42af-4429-9fd1-a8218c268e20'
$setting=[Guid]'ee12f906-d277-404b-b6da-e5fa1a576df5'
function Read-Values {
 [uint32]$ac=0;[uint32]$dc=0
 Check ([G1PciePower]::PowerReadACValueIndex([IntPtr]::Zero,[ref]$scheme,[ref]$subgroup,[ref]$setting,[ref]$ac))
 Check ([G1PciePower]::PowerReadDCValueIndex([IntPtr]::Zero,[ref]$scheme,[ref]$subgroup,[ref]$setting,[ref]$dc))
 return @{ac=$ac;dc=$dc}
}
function Write-Values($values){
 Check ([G1PciePower]::PowerWriteACValueIndex([IntPtr]::Zero,[ref]$scheme,[ref]$subgroup,[ref]$setting,[uint32]$values.ac))
 Check ([G1PciePower]::PowerWriteDCValueIndex([IntPtr]::Zero,[ref]$scheme,[ref]$subgroup,[ref]$setting,[uint32]$values.dc))
 Check ([G1PciePower]::PowerSetActiveScheme([IntPtr]::Zero,[ref]$scheme))
}
New-Item -ItemType Directory -Force -Path $OutputFolder|Out-Null
$before=Read-Values
$record=[ordered]@{time=(Get-Date).ToString('o');mode=$Mode;scheme=$scheme.ToString();before=$before;changed=$false;trialOnly=$true;gameLaunched=$false;adapterReset=$false}
if($Mode -ne 'Inspect'){
 if(Get-Process Moonlight,GeForceNOWStreamer -ErrorAction SilentlyContinue){throw 'Streaming client open; close it before this diagnostic'}
 $saved=Join-Path $OutputFolder 'before.json'
 if($Mode -eq 'Apply'){
  if(Test-Path -LiteralPath $saved){throw 'Prior diagnostic exists; review/restore it first'}
  @{time=$record.time;scheme=$scheme.ToString();values=$before;restore='Run this helper -Mode Restore with the same OutputFolder on this G1 only.'}|ConvertTo-Json -Depth 5|Set-Content -LiteralPath $saved -Encoding UTF8
  & powercfg /export (Join-Path $OutputFolder 'before-private.pow') $scheme.ToString()
  if($LASTEXITCODE -ne 0){throw 'Power plan export failed; no settings changed'}
  $desired=@{ac=0;dc=0}
 }else{
  $prior=Get-Content -LiteralPath $saved -Raw -Encoding UTF8|ConvertFrom-Json
  if($prior.scheme -ne $scheme.ToString()){throw 'Active plan differs from trial; review before restoring'}
  if($before.ac -ne 0 -or $before.dc -ne 0){throw 'ASPM changed since trial; refusing to overwrite later changes'}
  $desired=$prior.values
 }
 try{
  Write-Values $desired
  $after=Read-Values
  if($after.ac -ne $desired.ac -or $after.dc -ne $desired.dc){throw 'Power policy readback mismatch'}
  $record.changed=$true;$record.after=$after
 }catch{
  $failure=$_.Exception.Message
  try {Write-Values $before;$record.rollbackVerified=((Read-Values).ac -eq $before.ac -and (Read-Values).dc -eq $before.dc)}catch{$record.rollbackError=$_.Exception.Message}
  $record.error=$failure
  $record|ConvertTo-Json -Depth 6|Set-Content (Join-Path $OutputFolder ($Mode+'-result.json')) -Encoding UTF8
  throw $failure
 }
}
$record|ConvertTo-Json -Depth 6|Set-Content (Join-Path $OutputFolder ($Mode+'-result.json')) -Encoding UTF8
$record|ConvertTo-Json -Depth 6
