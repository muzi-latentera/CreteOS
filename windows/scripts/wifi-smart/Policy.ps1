function Get-WifiProfileFromLines {
 param([string[]]$Lines,[string]$Interface)
 $selected=$false;$connected=$false
 foreach($line in $Lines){
  if($line -match '^\s*Name\s*:\s*(.+)$'){$selected=$Matches[1].Trim() -eq $Interface;$connected=$false;continue}
  if(!$selected){continue}
  if($line -match '^\s*State\s*:\s*(.+)$'){$connected=$Matches[1].Trim() -eq 'connected';continue}
  if($connected -and $line -match '^\s*Profile\s*:\s*(.+)$'){return $Matches[1].Trim()}
 }
 return ''
}
function Get-WifiDecision {
 param([string]$Current,[string]$Gaming,[string]$Download,[bool]$Streaming,[bool]$Paused,[bool]$GamingLease,[string]$Override,[bool]$Demand,[double]$QuietSeconds,[double]$ResidenceSeconds,[bool]$StartupOrResume=$false)
 if($Current -ne $Gaming -and $Current -ne $Download){return [pscustomobject]@{Target=$Current;Reason='outside-home-or-disconnected'}}
 if($Paused){return [pscustomobject]@{Target=$Current;Reason='paused'}}
 # Preserve a live client connection. CreteOS performs the pre-launch switch separately.
 if($Streaming){return [pscustomobject]@{Target=$Current;Reason='protect-stream'}}
 if($GamingLease){return [pscustomobject]@{Target=$Gaming;Reason='gaming-launch'}}
 if($Override -eq 'Gaming'){return [pscustomobject]@{Target=$Gaming;Reason='manual-gaming'}}
 if($Override -eq 'Download'){return [pscustomobject]@{Target=$Download;Reason='manual-download'}}
 if($StartupOrResume){return [pscustomobject]@{Target=$Gaming;Reason='startup-resume-default'}}
 if($Demand){return [pscustomobject]@{Target=$Download;Reason='download-or-sustained-transfer'}}
 if($Current -eq $Download -and ($QuietSeconds -lt 120 -or $ResidenceSeconds -lt 300)){return [pscustomobject]@{Target=$Current;Reason='download-cooldown'}}
 return [pscustomobject]@{Target=$Gaming;Reason='idle-gaming-default'}
}
