param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
function Get-ArchiveHash($Path) {
    $sha = [Security.Cryptography.SHA256]::Create()
    $stream = [IO.File]::OpenRead($Path)
    try { [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
    finally { $stream.Dispose(); $sha.Dispose() }
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$packages = @(
    @{ name='ludusavi'; file='ludusavi-v0.31.0-win64.zip'; url='https://github.com/mtkennerly/ludusavi/releases/download/v0.31.0/ludusavi-v0.31.0-win64.zip'; hash='f47a8ad8c708f01d2eb124704973beffab205e292f5287a10fc4a101f8d68706'; license='https://raw.githubusercontent.com/mtkennerly/ludusavi/8844d7b/LICENSE' },
    @{ name='rclone'; file='rclone-v1.76.0-windows-amd64.zip'; url='https://downloads.rclone.org/v1.76.0/rclone-v1.76.0-windows-amd64.zip'; hash='8b4fd2737ade4e70c057ef4a49deb7cffe94b0fb9d7587c189d149823f21a1dc'; license='https://raw.githubusercontent.com/rclone/rclone/v1.76.0/COPYING' }
)
foreach ($package in $packages) {
    $archive = Join-Path $OutputDirectory $package.file
    if (!(Test-Path $archive) -or (Get-ArchiveHash $archive) -ne $package.hash) {
        & curl.exe --fail --location --silent --show-error --retry 2 $package.url -o $archive
        if ($LASTEXITCODE -or (Get-ArchiveHash $archive) -ne $package.hash) { throw "Invalid $($package.name) archive" }
    }
    $expanded = Join-Path $OutputDirectory $package.name
    New-Item -ItemType Directory -Force -Path $expanded | Out-Null
    & tar.exe -xf $archive -C $expanded
    if ($LASTEXITCODE) { throw "Could not unpack $($package.name)" }
    $exe = Get-ChildItem $expanded -Recurse -File -Filter ($package.name + '.exe') | Select-Object -First 1
    if (!$exe) { throw "Missing $($package.name) executable" }
    Copy-Item $exe.FullName (Join-Path $OutputDirectory $exe.Name) -Force
    $license = Get-ChildItem $expanded -Recurse -File | Where-Object Name -Match '^LICENSE' | Select-Object -First 1
    $licensePath = Join-Path $OutputDirectory ($package.name + '-LICENSE.txt')
    if ($license) {
        Copy-Item $license.FullName $licensePath -Force
    } else {
        & curl.exe --fail --location --silent --show-error $package.license -o $licensePath
        if ($LASTEXITCODE) { throw "Missing $($package.name) license" }
    }
}
