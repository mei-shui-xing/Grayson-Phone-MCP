[CmdletBinding()]
param(
    [switch]$FullTestSuite
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$sdkPath = Get-AndroidSdkPath
$env:ANDROID_HOME = $sdkPath
$env:ANDROID_SDK_ROOT = $sdkPath

Push-Location $repoRoot
try {
    $requiredArtifacts = @(
        (Join-Path $repoRoot 'vendor\ngrok-java\ngrok-java\target\ngrok-java-1.2.0-SNAPSHOT.jar'),
        (Join-Path $repoRoot 'vendor\ngrok-java\ngrok-java-native\target\ngrok-java-native-classes.jar'),
        (Join-Path $repoRoot 'vendor\ngrok-java\ngrok-java-native\target\ngrok-java-native-host.jar'),
        (Join-Path $repoRoot 'app\src\main\jniLibs\arm64-v8a\libngrok_java.so'),
        (Join-Path $repoRoot 'app\src\main\jniLibs\arm64-v8a\libcloudflared.so')
    )
    if ($requiredArtifacts.Where({ -not (Test-Path -LiteralPath $_) }).Count -gt 0) {
        & (Join-Path $PSScriptRoot 'Build-NativeDeps.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'Native dependency build failed.' }
    }
    $gradleArgs = @(':app:testGmsDebugUnitTest')
    if (-not $FullTestSuite) {
        $gradleArgs += @(
            '--tests', '*AppManagerTest',
            '--tests', '*AppManagementToolsIntegrationTest',
            '--tests', '*ActionExecutorImplTest',
            '--tests', '*MainViewModelTest',
            '--tests', '*ToolPermissionsIntegrationTest',
            '--tests', '*BuildMetadataTest'
        )
    }
    $gradleArgs += @('--max-workers=2', ':app:assembleGmsDebug', '--stacktrace')
    & (Join-Path $repoRoot 'gradlew.bat') @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed with exit code $LASTEXITCODE"
    }
    $apk = Get-ChildItem -Recurse -LiteralPath (Join-Path $repoRoot 'app\build\outputs\apk\gms\debug') -Filter '*.apk' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $apk) {
        throw 'Build succeeded but no GMS debug APK was found.'
    }
    Write-Host "APK: $($apk.FullName)"
}
finally {
    Pop-Location
}
