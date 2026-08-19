$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root
if (-not (Get-Command pyinstaller -ErrorAction SilentlyContinue)) {
    throw "未找到 PyInstaller。请先安装 packaging/pyinstaller/requirements-build.txt"
}
Remove-Item -Recurse -Force -ErrorAction SilentlyContinue build, dist
pyinstaller --clean --noconfirm packaging/pyinstaller/stzb-web.spec
$Exe = Join-Path $Root "distSTZB助手-WebSTZB助手-Web.exe"
if (-not (Test-Path $Exe)) { throw "没有生成 $Exe" }
Write-Host "已生成: $Exe"
