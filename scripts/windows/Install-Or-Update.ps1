[CmdletBinding()]
param(
    [string]$ApkPath,
    [switch]$NoLaunch
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$adbPath = Get-AdbPath
$serial = Get-AuthorizedAndroidDevice

if (-not $ApkPath) {
    $apk = Get-ChildItem -Recurse -LiteralPath (Join-Path $repoRoot 'app\build\outputs\apk\gms\debug') -Filter '*.apk' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw 'No GMS debug APK found. Run Build-Debug.ps1 first.'
    }
    $ApkPath = $apk.FullName
}

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
& $adbPath -s $serial install -r $resolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed with exit code $LASTEXITCODE"
}

$appId = 'com.danielealbano.androidremotecontrolmcp.gms.debug'
if ($NoLaunch) {
    Write-Host "Installed $appId (not launched)."
} else {
    & $adbPath -s $serial shell monkey -p $appId -c android.intent.category.LAUNCHER 1 | Out-Host
    Write-Host "Installed and launched $appId"
}
