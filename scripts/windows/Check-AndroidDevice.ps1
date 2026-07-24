[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$adbPath = Get-AdbPath
$serial = Get-AuthorizedAndroidDevice

Write-Host "Serial: $serial"
$properties = @(
    'ro.product.manufacturer',
    'ro.product.model',
    'ro.build.version.release',
    'ro.build.version.sdk',
    'ro.vivo.os.version',
    'ro.vivo.os.build.display.id',
    'ro.product.cpu.abilist'
)
foreach ($property in $properties) {
    $value = (& $adbPath -s $serial shell getprop $property).Trim()
    Write-Host "$property=$value"
}
& $adbPath -s $serial shell wm size | Out-Host
& $adbPath -s $serial shell wm density | Out-Host
& $adbPath -s $serial shell dumpsys activity activities |
    Select-String -Pattern 'topResumedActivity|mResumedActivity' |
    Select-Object -First 2 |
    Out-Host
