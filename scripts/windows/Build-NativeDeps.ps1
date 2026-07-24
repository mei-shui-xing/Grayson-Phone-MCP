[CmdletBinding()]
param(
    [switch]$IncludeX86_64
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'AndroidTools.ps1')

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$sdkPath = Get-AndroidSdkPath
$ndkVersion = '28.2.13676358'
$ndkRoot = Join-Path $sdkPath "ndk\$ndkVersion"
$ndkBin = Join-Path $ndkRoot 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$javaHome = 'C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot'
if ($env:JAVA_HOME -and (Test-Path -LiteralPath $env:JAVA_HOME)) {
    $javaHome = $env:JAVA_HOME
}
if (-not (Test-Path -LiteralPath (Join-Path $javaHome 'bin\jar.exe'))) {
    throw 'A JDK is required. Install JDK 17 or newer and set JAVA_HOME.'
}

$sdkManager = Join-Path $sdkPath 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $ndkRoot)) {
    if (-not (Test-Path -LiteralPath $sdkManager)) {
        throw "Android NDK $ndkVersion is missing and sdkmanager was not found."
    }
    & $sdkManager "ndk;$ndkVersion"
    if ($LASTEXITCODE -ne 0) { throw "NDK installation failed with exit code $LASTEXITCODE" }
}

$mavenVersion = '3.9.16'
$mavenHome = Join-Path $env:USERPROFILE "Tools\apache-maven-$mavenVersion"
$maven = Join-Path $mavenHome 'bin\mvn.cmd'
if (-not (Test-Path -LiteralPath $maven)) {
    $toolsRoot = Split-Path -Parent $mavenHome
    New-Item -ItemType Directory -Force -Path $toolsRoot | Out-Null
    $archive = Join-Path $env:TEMP "apache-maven-$mavenVersion-bin.zip"
    $download = "https://dlcdn.apache.org/maven/maven-3/$mavenVersion/binaries/apache-maven-$mavenVersion-bin.zip"
    Invoke-WebRequest -Uri $download -OutFile $archive
    $expected = (Invoke-RestMethod -Uri "$download.sha512").Trim().Split(' ')[0].ToUpperInvariant()
    $actual = (Get-FileHash -Algorithm SHA512 -LiteralPath $archive).Hash
    if ($actual -ne $expected) { throw 'Downloaded Maven archive failed SHA-512 verification.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolsRoot -Force
}

$cargoCommand = Get-Command cargo -ErrorAction SilentlyContinue
$rustupCommand = Get-Command rustup -ErrorAction SilentlyContinue
$goCommand = Get-Command go -ErrorAction SilentlyContinue
$cargo = if ($cargoCommand) { $cargoCommand.Source } else { $null }
$rustup = if ($rustupCommand) { $rustupCommand.Source } else { $null }
$go = if ($goCommand) { $goCommand.Source } else { $null }
if (-not $cargo -or -not $rustup) { throw 'Rust and rustup are required: https://rustup.rs/' }
if (-not $go) { throw 'Go is required: https://go.dev/dl/' }

$env:JAVA_HOME = $javaHome
$env:JAVA_11_HOME = $javaHome
$env:JAVA_17_HOME = $javaHome
$ngrokRoot = Join-Path $repoRoot 'vendor\ngrok-java'
Push-Location $ngrokRoot
try {
    & $maven compile -pl ngrok-java-native --also-make --global-toolchains toolchains.xml -q
    if ($LASTEXITCODE -ne 0) { throw "ngrok Java compilation failed with exit code $LASTEXITCODE" }
    & $maven package -pl ngrok-java -DskipTests --global-toolchains toolchains.xml -q
    if ($LASTEXITCODE -ne 0) { throw "ngrok Java packaging failed with exit code $LASTEXITCODE" }
    Push-Location 'ngrok-java-native\target\classes'
    try {
        & (Join-Path $javaHome 'bin\jar.exe') cf '..\ngrok-java-native-classes.jar' com
    }
    finally { Pop-Location }
}
finally { Pop-Location }

& $rustup target add aarch64-linux-android
if ($IncludeX86_64) { & $rustup target add x86_64-linux-android }

$nativeRoot = Join-Path $ngrokRoot 'ngrok-java-native'
$env:CC_aarch64_linux_android = Join-Path $ndkBin 'aarch64-linux-android21-clang.cmd'
$env:AR_aarch64_linux_android = Join-Path $ndkBin 'llvm-ar.exe'
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $env:CC_aarch64_linux_android
Push-Location $nativeRoot
try {
    & $cargo build --release --target aarch64-linux-android -j 1
    if ($LASTEXITCODE -ne 0) { throw "ngrok arm64 build failed with exit code $LASTEXITCODE" }
    & $cargo build --release -j 1
    if ($LASTEXITCODE -ne 0) { throw "ngrok Windows host build failed with exit code $LASTEXITCODE" }
    Push-Location 'target\release'
    try {
        & (Join-Path $javaHome 'bin\jar.exe') cf '..\ngrok-java-native-host.jar' 'ngrok_java.dll'
    }
    finally { Pop-Location }

    if ($IncludeX86_64) {
        $env:CC_x86_64_linux_android = Join-Path $ndkBin 'x86_64-linux-android21-clang.cmd'
        $env:AR_x86_64_linux_android = Join-Path $ndkBin 'llvm-ar.exe'
        $env:CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER = $env:CC_x86_64_linux_android
        & $cargo build --release --target x86_64-linux-android -j 1
        if ($LASTEXITCODE -ne 0) { throw "ngrok x86_64 build failed with exit code $LASTEXITCODE" }
    }
}
finally { Pop-Location }

function Ensure-CloudflaredPatch {
    $cloudflaredRoot = Join-Path $repoRoot 'vendor\cloudflared'
    $patchPath = Join-Path $repoRoot 'patches\cloudflared-android-dns.patch'
    if (-not (Test-Path -LiteralPath $patchPath)) {
        throw "Missing cloudflared Android DNS patch: $patchPath"
    }

    & git.exe -C $cloudflaredRoot apply --reverse --check $patchPath 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host 'cloudflared Android DNS patch already applied.'
        return
    }

    & git.exe -C $cloudflaredRoot apply --check $patchPath
    if ($LASTEXITCODE -ne 0) {
        throw 'cloudflared patch does not apply cleanly. Reset the submodule to the pinned commit and retry.'
    }

    & git.exe -C $cloudflaredRoot apply $patchPath
    if ($LASTEXITCODE -ne 0) {
        throw 'Failed to apply cloudflared Android DNS patch.'
    }
    Write-Host 'Applied cloudflared Android DNS patch.'
}

Ensure-CloudflaredPatch

function Build-Cloudflared([string]$Abi, [string]$GoArch, [string]$Compiler) {
    $destination = Join-Path $repoRoot "app\src\main\jniLibs\$Abi"
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $env:GOOS = 'android'
    $env:GOARCH = $GoArch
    $env:CGO_ENABLED = '1'
    $env:CC = Join-Path $ndkBin $Compiler
    Push-Location (Join-Path $repoRoot 'vendor\cloudflared')
    try {
        & $go build -a -installsuffix cgo '-ldflags=-s -w -extldflags=-Wl,-z,max-page-size=16384' `
            -o (Join-Path $destination 'libcloudflared.so') '.\cmd\cloudflared'
        if ($LASTEXITCODE -ne 0) { throw "cloudflared $Abi build failed with exit code $LASTEXITCODE" }
    }
    finally { Pop-Location }
}

Build-Cloudflared 'arm64-v8a' 'arm64' 'aarch64-linux-android21-clang.cmd'
$armDestination = Join-Path $repoRoot 'app\src\main\jniLibs\arm64-v8a'
Copy-Item -Force -LiteralPath `
    (Join-Path $nativeRoot 'target\aarch64-linux-android\release\libngrok_java.so') `
    -Destination (Join-Path $armDestination 'libngrok_java.so')

if ($IncludeX86_64) {
    Build-Cloudflared 'x86_64' 'amd64' 'x86_64-linux-android21-clang.cmd'
    $x86Destination = Join-Path $repoRoot 'app\src\main\jniLibs\x86_64'
    Copy-Item -Force -LiteralPath `
        (Join-Path $nativeRoot 'target\x86_64-linux-android\release\libngrok_java.so') `
        -Destination (Join-Path $x86Destination 'libngrok_java.so')
}

Write-Host 'Native dependencies are ready.'
