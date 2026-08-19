$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root
if (-not (Get-Command pyinstaller -ErrorAction SilentlyContinue)) {
    throw "未找到 PyInstaller。请先安装 packaging/pyinstaller/requirements-build.txt"
}
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $Root "build"), (Join-Path $Root "dist")
pyinstaller --clean --noconfirm (Join-Path $Root "packaging/pyinstaller/stzb-web.spec")
$Exe = Join-Path (Join-Path $Root "dist") "STZB助手-Web.exe"
if (-not (Test-Path $Exe)) { throw "没有生成 $Exe" }
Write-Host "已生成: $Exe"
