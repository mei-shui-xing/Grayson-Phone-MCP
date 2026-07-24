[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$adbPath = Get-AdbPath
$serial = Get-AuthorizedAndroidDevice
$appId = 'com.danielealbano.androidremotecontrolmcp.gms.debug'
$accessibilityClass = 'com.danielealbano.androidremotecontrolmcp.services.accessibility.McpAccessibilityService'

Write-Host 'Installed package:'
& $adbPath -s $serial shell pm list packages --user 0 $appId | Out-Host
Write-Host 'Foreground MCP service:'
& $adbPath -s $serial shell dumpsys activity services $appId |
    Select-String -Pattern 'McpServerService|isForeground|foregroundId' |
    Out-Host
Write-Host 'Accessibility service:'
& $adbPath -s $serial shell dumpsys accessibility |
    Select-String -Pattern $accessibilityClass |
    Out-Host
Write-Host 'TCP forward rules:'
& $adbPath -s $serial forward --list | Out-Host
