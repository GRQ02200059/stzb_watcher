param([string]$Version = "dev")
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
if (-not $IsWindows) { throw "Run this script on Windows x64." }
if ($Version -notmatch '^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$') { throw "Invalid package version: $Version" }
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

function Invoke-Checked([scriptblock]$Command) {
    $global:LASTEXITCODE = 0
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "Command failed ($LASTEXITCODE): $Command" }
}

$Output = Join-Path $Root "dist/windows"
$Work = Join-Path $Root "build/windows"
$Logs = Join-Path $Root "build/windows-logs"
New-Item -ItemType Directory -Force $Logs | Out-Null
Start-Transcript -Path (Join-Path $Logs "build.log") -Force
try {
    foreach ($Path in @($Output, $Work)) {
        if (Test-Path $Path) { Remove-Item -Recurse -Force $Path }
        New-Item -ItemType Directory -Force $Path | Out-Null
    }
    Invoke-Checked { python -m pip check }
    Invoke-Checked { python -m unittest test.test_packaged_runtime test.test_web_launcher test.test_battle_engine_adapter test.test_windows_web_build_config test.test_windows_release_workflow test.test_windows_java_manifest }
    # The mirrored engine test expects a ZIP which Git intentionally ignores.
    # Reconstruct its one required entry from the checked-in JSON, preserving local ZIPs.
    @'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
root = Path('battle-engine/src/test/resources/assent/cfg')
name = 'cap_20260311222842345_0000000b_zlib.json'
if not (root / 'paper.zip').exists():
    payload = (root / 'paper/11' / name).read_bytes()
    with ZipFile(root / 'paper.zip', 'x', compression=ZIP_DEFLATED) as archive:
        archive.writestr('0000000b/' + name, payload)
'@ | python -
    if ($LASTEXITCODE -ne 0) { throw 'Cannot prepare tracked engine test fixture' }
    Invoke-Checked { gradle --no-daemon -p battle-engine test installDist }
    Invoke-Checked { python -m PyInstaller --clean --noconfirm --distpath $Output --workpath $Work (Join-Path $Root "packaging/pyinstaller/stzb-web.spec") }

    $Bundle = Join-Path $Output "STZB-Web"
    $Exe = Join-Path $Bundle "STZB助手-Web.exe"
    if (-not (Test-Path $Exe)) { throw "Missing $Exe" }
    $Runtime = Join-Path $Bundle "runtime"
    New-Item -ItemType Directory -Force (Join-Path $Runtime "battle-engine") | Out-Null
    Copy-Item -Recurse (Join-Path $Root "battle-engine/build/install/stzb-battle-engine/lib") (Join-Path $Runtime "battle-engine/lib")
    $Jlink = Join-Path $env:JAVA_HOME "bin/jlink.exe"
    Invoke-Checked { & $Jlink --add-modules java.se,jdk.unsupported,jdk.crypto.ec --strip-debug --no-header-files --no-man-pages --compress=2 --output (Join-Path $Runtime "java") }
    Invoke-Checked { python packaging/scripts/prepare_java.py (Join-Path $Runtime "java/bin/java.exe") }
    Copy-Item (Join-Path $Root "packaging/README-Windows.txt") $Bundle
    Invoke-Checked { & (Join-Path $Runtime "java/bin/java.exe") -version }

    $Commit = git rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw "Cannot resolve build commit" }
    $Dirty = git status --porcelain --untracked-files=no
    if ($LASTEXITCODE -ne 0) { throw "Cannot determine source state" }
    $Files = @(Get-ChildItem $Bundle -Recurse -File | Sort-Object FullName | ForEach-Object {
        @{
            path = [IO.Path]::GetRelativePath($Bundle, $_.FullName).Replace('\', '/')
            sha256 = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            bytes = $_.Length
        }
    })
    $Info = @{
        version = $Version; commit = $Commit.Trim(); dirty = [bool]$Dirty
        target = "windows-x64"; builtAt = [DateTime]::UtcNow.ToString("o")
        javaLauncherCodePage = "UTF-8 (adapted PE manifest)"
        python = (python --version); java = (Get-Content (Join-Path $Runtime "java/release") -Raw)
        dependencies = @(python -m pip freeze); files = $Files
        validation = "candidate; see separate smoke report"
    }
    $Info | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $Bundle "build-info.json") -Encoding utf8
    $PackageName = "STZB-Web-Windows-x64-$Version-$($Commit.Substring(0, 8))"
    $Zip = Join-Path $Output "$PackageName.zip"
    Compress-Archive -Path $Bundle -DestinationPath $Zip -CompressionLevel Optimal
    "$((Get-FileHash $Zip -Algorithm SHA256).Hash.ToLowerInvariant())  $PackageName.zip" |
        Set-Content (Join-Path $Output "SHA256SUMS.txt") -Encoding ascii
    Write-Host "Candidate package: $Zip (requires independent smoke verification)"
} finally {
    Stop-Transcript
}
