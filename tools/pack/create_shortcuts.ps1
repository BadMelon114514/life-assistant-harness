# 创建桌面 / 开始菜单快捷方式（可单独运行）
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dist = Join-Path $root 'dist'
$exe = Join-Path $dist '生活助理Harness一键启动.exe'
$icon = Join-Path $dist 'icon.ico'
$linkName = '生活助理 Harness.lnk'

if (-not (Test-Path $exe)) { throw "缺少启动器：$exe，请先运行 build_exe.ps1" }

# 清理旧名称快捷方式（改名后避免重复）
$desktop = [Environment]::GetFolderPath('Desktop')
$programs = [Environment]::GetFolderPath('Programs')
foreach ($old in @('FarmingHarness 电商办公助手.lnk')) {
    $p = Join-Path $desktop $old
    if (Test-Path $p) { Remove-Item $p -Force }
}
$oldDir = Join-Path $programs 'FarmingHarness'
if (Test-Path $oldDir) { Remove-Item $oldDir -Recurse -Force }

$ws = New-Object -ComObject WScript.Shell
function New-Shortcut($path) {
    $sc = $ws.CreateShortcut($path)
    $sc.TargetPath = $exe
    $sc.WorkingDirectory = $root
    $sc.IconLocation = $icon
    $sc.Description = '生活助理 Harness（一键启动：Java 应用 + 上下文存储服务）'
    $sc.Save()
    Write-Output "已创建：$path"
}

New-Shortcut (Join-Path $desktop $linkName)
$startMenu = Join-Path $programs '生活助理 Harness'
New-Item -ItemType Directory -Force -Path $startMenu | Out-Null
New-Shortcut (Join-Path $startMenu $linkName)
