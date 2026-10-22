# Leases are removed by the owning Rust lifecycle on success, error or cancel.
# A killed CreteOS cannot leave a permanent hold; PID reuse is rejected too.
function Test-WifiSessionOwner($Lease) {
 try {
  $owner=Get-Process -Id ([int]$Lease.ownerPid) -ErrorAction Stop
  $epoch=[DateTime]::SpecifyKind([DateTime]'1970-01-01',[DateTimeKind]::Utc)
  return ($owner.StartTime.ToUniversalTime()-$epoch).TotalSeconds -le [double]$Lease.createdUnix
 } catch { return $false }
}
function Test-WifiSessions([string]$Folder,[string]$Exclude='') {
 foreach($file in @(Get-ChildItem -LiteralPath $Folder -Filter '*.json' -File -ErrorAction SilentlyContinue)) {
  if($file.BaseName -eq $Exclude){continue}
  try {$lease=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json} catch {continue}
  if(Test-WifiSessionOwner $lease){return $true}
 }
 return $false
}
function Get-WifiSessionTarget([string]$Preference,[string]$Current,[string]$Gaming,[string]$Download) {
 if($Current -ne $Gaming -and $Current -ne $Download){return $Current}
 switch($Preference){'Gaming'{return $Gaming};'Download'{return $Download};default{return $Current}}
}
