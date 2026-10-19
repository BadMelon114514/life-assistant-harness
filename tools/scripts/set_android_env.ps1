# Configure Android SDK environment variables for the current user (idempotent).
$sdk = 'D:\Android\Sdk'
if (-not (Test-Path (Join-Path $sdk 'platform-tools\adb.exe'))) {
    Write-Output "ERROR: adb not found under $sdk"
    exit 1
}

[Environment]::SetEnvironmentVariable('ANDROID_HOME', $sdk, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $sdk, 'User')

$entries = @(
    (Join-Path $sdk 'platform-tools'),
    (Join-Path $sdk 'emulator'),
    (Join-Path $sdk 'cmdline-tools\latest\bin')
)

$userPath = [Environment]::GetEnvironmentVariable('Path', 'User')
$parts = @()
if ($userPath) { $parts = @($userPath -split ';' | Where-Object { $_ -ne '' }) }

$added = @()
foreach ($e in $entries) {
    $exists = $false
    foreach ($p in $parts) {
        if ($p.TrimEnd('\') -ieq $e.TrimEnd('\')) { $exists = $true; break }
    }
    if (-not $exists) { $parts += $e; $added += $e }
}

if ($added.Count -gt 0) {
    [Environment]::SetEnvironmentVariable('Path', ($parts -join ';'), 'User')
}

# Apply to the current process as well.
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
foreach ($e in $entries) { $env:Path = $env:Path + ';' + $e }

Write-Output ("ANDROID_HOME=" + [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User'))
Write-Output ("ANDROID_SDK_ROOT=" + [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT', 'User'))
Write-Output ("PATH_ADDED=" + ($added -join ';'))
$machineUserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
Write-Output ("PATH_HAS_PLATFORM_TOOLS=" + ($machineUserPath -match 'platform-tools'))
Write-Output ("PATH_HAS_EMULATOR=" + ($machineUserPath -match 'emulator'))
