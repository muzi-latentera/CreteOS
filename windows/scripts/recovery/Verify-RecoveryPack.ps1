param([string]$PackRoot=(Split-Path $PSScriptRoot))
$ErrorActionPreference='Stop'
$root=(Resolve-Path -LiteralPath $PackRoot).ProviderPath
$manifest=Get-Content -LiteralPath (Join-Path $root 'MANIFEST.json') -Raw -Encoding UTF8|ConvertFrom-Json
if(@($manifest.files).Count -eq 0){throw 'Empty package manifest'}
foreach($item in @($manifest.files)){
 $path=[IO.Path]::GetFullPath((Join-Path $root $item.path))
 if(!$path.StartsWith($root.TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe manifest path'}
 if(!(Test-Path -LiteralPath $path) -or (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $item.sha256){throw ('Recovery package mismatch: '+$item.path)}
}
Write-Output ('Verified '+$manifest.files.Count+' recovery package files. This checks bytes, not fresh-device gameplay acceptance.')
