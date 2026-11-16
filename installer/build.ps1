# Builds the AirHub installer: target\installer\AirHub-<version>-setup.exe
#   1. mvn clean package       - tests and the jar with all libraries (H2, FlatLaf)
#   2. jdeps                   - Java modules the program needs
#   3. jpackage --type app-image - AirHub.exe launcher + jar + reduced Java runtime (jlink)
#   4. ISCC                    - Inno Setup installer from the image and the documentation
# Run from any directory: powershell -ExecutionPolicy Bypass -File installer\build.ps1 [-SkipTests]
param(
    [switch]$SkipTests,
    [string]$Maven = "mvn",
    [string]$Iscc = ""
)
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root

function Find-Tool([string]$name, [string[]]$candidates) {
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    throw "$name not found"
}

$Maven = Find-Tool $Maven @(
    "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd")
if (-not $Iscc) {
    $Iscc = Find-Tool "ISCC.exe" @(
        "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe",
        "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
        "$env:ProgramFiles\Inno Setup 6\ISCC.exe")
}

# version is defined only in pom.xml; jpackage accepts only digits, so -SNAPSHOT is dropped for it
$version = ([xml](Get-Content pom.xml -Encoding UTF8)).project.version
$numericVersion = $version -replace "-.*$", ""
$jar = "airhub-$version.jar"
$out = "target\installer"
Write-Host "AirHub $version"

# jpackage makes the launcher read-only, which mvn clean cannot delete: the old output is removed here
if (Test-Path $out) { Remove-Item -Recurse -Force $out }

# 1. jar
$mvnArgs = @("-q", "clean", "package")
if ($SkipTests) { $mvnArgs += "-DskipTests" }
& $Maven @mvnArgs
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }

# 2. modules: only the main jar goes into the image, the shaded jar already contains H2 and FlatLaf
New-Item -ItemType Directory -Force "$out\input", "$out\docs" | Out-Null
Copy-Item "target\$jar" "$out\input\"
$modules = (& jdeps --multi-release 25 --ignore-missing-deps --print-module-deps "$out\input\$jar").Trim()
if ($LASTEXITCODE -ne 0) { throw "jdeps failed" }
Write-Host "Modules: $modules"

# 3. program image; runtime\bin\java.exe is kept for the administrator's tools (backup, password reset)
if (Test-Path "$out\image") { Remove-Item -Recurse -Force "$out\image" }
& jpackage --type app-image --dest "$out\image" --name AirHub `
    --input "$out\input" --main-jar $jar --main-class org.grebenyk.airhub.App `
    --app-version $numericVersion --vendor "Grebenyk Artem" --description "AirHub airport management" `
    --copyright "(c) 2026 Grebenyk Artem" --icon installer\airhub.ico `
    --add-modules $modules `
    --jlink-options "--strip-debug --no-header-files --no-man-pages" `
    --java-options "--enable-native-access=ALL-UNNAMED"
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
Get-ChildItem "$out\image" -Recurse -File | Where-Object IsReadOnly | ForEach-Object { $_.IsReadOnly = $false }

# documentation is optional: PDF files from docs\ are installed into {app}\docs
Get-ChildItem docs -Filter "AirHub*.pdf" -ErrorAction SilentlyContinue | Copy-Item -Destination "$out\docs\"

# 4. installer
& $Iscc /Q "/DAppVersion=$version" "/DImageDir=$root\$out\image\AirHub" "/DDocsDir=$root\$out\docs" installer\airhub.iss
if ($LASTEXITCODE -ne 0) { throw "Inno Setup compilation failed" }
Get-Item "$out\AirHub-$version-setup.exe" | Select-Object Name, Length
