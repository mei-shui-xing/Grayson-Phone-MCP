Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-AndroidSdkPath {
    $candidates = @(
        $env:ANDROID_SDK_ROOT,
        $env:ANDROID_HOME,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    $sdkPath = $candidates | Select-Object -First 1
    if (-not $sdkPath) {
        throw 'Android SDK not found. Install Android SDK Platform-Tools or set ANDROID_SDK_ROOT.'
    }
    return (Resolve-Path -LiteralPath $sdkPath).Path
}

function Get-AdbPath {
    $sdkPath = Get-AndroidSdkPath
    $adbPath = Join-Path $sdkPath 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adbPath)) {
        throw "adb.exe not found at $adbPath"
    }
    return $adbPath
}

function Get-AuthorizedAndroidDevice {
    $adbPath = Get-AdbPath
    $lines = & $adbPath devices -l
    $devices = @($lines | Select-Object -Skip 1 | Where-Object { $_ -match '^\S+\s+' })
    $unauthorized = @($devices | Where-Object { $_ -match '\sunauthorized(?:\s|$)' })
    if ($unauthorized.Count -gt 0) {
        throw 'Android device detected but not authorized. Unlock the phone and approve the USB debugging prompt.'
    }
    $online = @($devices | Where-Object { $_ -match '\sdevice(?:\s|$)' })
    if ($online.Count -eq 0) {
        throw 'No authorized Android device found. Connect the phone by USB and enable USB debugging.'
    }
    if ($online.Count -gt 1) {
        throw 'More than one Android device is connected. Disconnect extras or pass commands to adb with -s manually.'
    }
    return ($online[0] -split '\s+')[0]
}
