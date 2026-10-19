# 重新打包 FarmingHarness 为免安装 exe（jpackage 应用镜像 + PyInstaller 上下文存储 + C# 启动器 + 桌面快捷方式）
# 用法：powershell -ExecutionPolicy Bypass -File tools\pack\build_exe.ps1
# 注意：运行前先停止正在运行的 FarmingHarness（dist\FarmingHarness 文件会被占用）。

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dist = Join-Path $root 'dist'
$jar = Join-Path $root 'build\libs\farming-0.0.1-SNAPSHOT.jar'
$icon = Join-Path $dist 'icon.ico'
$python = 'E:\python\.venv\Scripts\python.exe'
$pyinstaller = Join-Path (Split-Path $python -Parent) 'pyinstaller.exe'

if (-not (Test-Path $jar)) { throw "缺少 $jar，请先执行 gradlew bootJar" }
New-Item -ItemType Directory -Force -Path $dist | Out-Null

Write-Host '[1/4] 生成图标'
& $python (Join-Path $PSScriptRoot 'make_icon.py')

Write-Host '[2/4] jpackage 应用镜像'
$appImage = Join-Path $dist 'FarmingHarness'
if (Test-Path $appImage) { Remove-Item -Recurse -Force $appImage }
$jpArgs = @(
    '--type', 'app-image',
    '--name', 'FarmingHarness',
    '--app-version', '1.0.0',
    '--vendor', '生活助理 Harness',
    '--input', (Join-Path $root 'build\libs'),
    '--main-jar', 'farming-0.0.1-SNAPSHOT.jar',
    '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
    '--dest', $dist,
    '--icon', $icon,
    '--java-options', '-Dlogging.file.name=logs/app_exe.log',
    '--java-options', '-Dfile.encoding=UTF-8'
)
& jpackage @jpArgs

Write-Host '[3/4] PyInstaller 上下文存储 exe'
$pyArgs = @(
    '--noconfirm', '--clean', '--onefile',
    '--name', 'context-store',
    '--distpath', $dist,
    '--workpath', (Join-Path $root 'build\pyinstaller'),
    '--specpath', (Join-Path $root 'build\pyinstaller'),
    '--paths', $root,
    '--add-data', "$root\context_store\schema.sql;context_store",
    '--icon', $icon,
    (Join-Path $PSScriptRoot 'ctx_entry.py')
)
& $pyinstaller @pyArgs
New-Item -ItemType Directory -Force -Path (Join-Path $dist 'context-store') | Out-Null
Move-Item (Join-Path $dist 'context-store.exe') (Join-Path $dist 'context-store\context-store.exe') -Force

Write-Host '[4/4] 编译启动器 + 创建快捷方式'
$cscArgs = @(
    '/nologo', '/target:winexe', '/optimize+',
    "/win32icon:$icon",
    "/out:$(Join-Path $dist '生活助理Harness一键启动.exe')",
    '/reference:System.dll', '/reference:System.Windows.Forms.dll',
    (Join-Path $PSScriptRoot 'launcher.cs')
)
& 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe' @cscArgs
& (Join-Path $PSScriptRoot 'create_shortcuts.ps1')
Write-Host "完成：$dist"
