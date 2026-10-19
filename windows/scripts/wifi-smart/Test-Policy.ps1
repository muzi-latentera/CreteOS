. (Join-Path $PSScriptRoot 'Policy.ps1')
$g='Hyperoptic_Extra';$d='Hyperoptic Fibre E433'
$cases=@(
 @{Name='startup defaults gaming before resumed transfer';Current=$d;Demand=$true;StartupOrResume=$true;Expected=$g;Reason='startup-resume-default'},
 @{Name='resume protects active stream';Current=$d;Streaming=$true;StartupOrResume=$true;Expected=$d;Reason='protect-stream'},
 @{Name='manual download mode survives automatic startup rule';Current=$d;Override='Download';StartupOrResume=$true;Expected=$d;Reason='manual-download'},
 @{Name='idle uses gaming';Current=$g;Expected=$g;Reason='idle-gaming-default'},
 @{Name='download chooses fibre';Current=$g;Demand=$true;Expected=$d;Reason='download-or-sustained-transfer'},
 @{Name='active stream beats download';Current=$g;Demand=$true;Streaming=$true;Expected=$g;Reason='protect-stream'},
 @{Name='never disconnect existing stream on fibre';Current=$d;Demand=$true;Streaming=$true;Expected=$d;Reason='protect-stream'},
 @{Name='prelaunch lease beats download';Current=$d;Demand=$true;GamingLease=$true;Expected=$g;Reason='gaming-launch'},
 @{Name='short transfer gap stays fibre';Current=$d;QuietSeconds=60;ResidenceSeconds=600;Expected=$d;Reason='download-cooldown'},
 @{Name='minimum residence avoids flapping';Current=$d;QuietSeconds=200;ResidenceSeconds=200;Expected=$d;Reason='download-cooldown'},
 @{Name='completed transfer returns gaming';Current=$d;QuietSeconds=180;ResidenceSeconds=600;Expected=$g;Reason='idle-gaming-default'},
 @{Name='manual download override';Current=$g;Override='Download';Expected=$d;Reason='manual-download'},
 @{Name='manual gaming override';Current=$d;Demand=$true;Override='Gaming';Expected=$g;Reason='manual-gaming'},
 @{Name='pause preserves current';Current=$g;Demand=$true;Paused=$true;Expected=$g;Reason='paused'},
 @{Name='away network untouched';Current='Hotel';Demand=$true;GamingLease=$true;Expected='Hotel';Reason='outside-home-or-disconnected'},
 @{Name='disconnected wifi untouched';Current='';Demand=$true;Expected='';Reason='outside-home-or-disconnected'}
)
$result=@(foreach($case in $cases){
 $args=@{Current=$case.Current;Gaming=$g;Download=$d;Streaming=$false;Paused=$false;GamingLease=$false;Override='';Demand=$false;QuietSeconds=600;ResidenceSeconds=600}
 foreach($key in @('Streaming','Paused','GamingLease','Override','Demand','QuietSeconds','ResidenceSeconds','StartupOrResume')){if($case.ContainsKey($key)){$args[$key]=$case[$key]}}
 $actual=Get-WifiDecision @args
 if($actual.Target -ne $case.Expected -or $actual.Reason -ne $case.Reason){throw ('FAIL '+$case.Name+': '+($actual|ConvertTo-Json -Compress))}
 @{test=$case.Name;passed=$true}
})
$adapterCases=@(
 @{Name='selected second adapter';Lines=@('Name : Other','State : connected','Profile : Hotel','Name : Wi-Fi 2','State : connected','Profile : Hyperoptic_Extra');Expected=$g},
 @{Name='selected disconnected adapter';Lines=@('Name : Other','State : connected','Profile : Hotel','Name : Wi-Fi 2','State : disconnected','Name : Another','State : connected','Profile : Wrong');Expected=''},
 @{Name='adapter missing';Lines=@('Name : Other','State : connected','Profile : Hotel');Expected=''}
)
foreach($case in $adapterCases){
 $actual=Get-WifiProfileFromLines -Lines $case.Lines -Interface 'Wi-Fi 2'
 if($actual -ne $case.Expected){throw ('FAIL '+$case.Name)}
 $result+=@{test=$case.Name;passed=$true}
}
$errors=$null;$tokens=$null
[void][Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'WiFiSmart.ps1'),[ref]$tokens,[ref]$errors)
if($errors.Count){throw ($errors|Out-String)}
@{passed=$result.Count;parserErrors=$errors.Count;cases=$result}|ConvertTo-Json -Depth 4
