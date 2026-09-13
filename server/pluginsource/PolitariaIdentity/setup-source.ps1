$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

$Cfr = Join-Path $Root "cfr-0.152.jar"
$Original = Join-Path $Root "PolitariaIdentity-original.jar"
$Decompiled = Join-Path $Root "decompiled"
$JavaDir = Join-Path $Root "src\main\java\ru\politaria\identity"

if (!(Test-Path $Cfr)) {
    Write-Host "Downloading CFR 0.152..."
    Invoke-WebRequest `
      -Uri "https://www.benf.org/other/cfr/cfr-0.152.jar" `
      -OutFile $Cfr
}

if (Test-Path $Decompiled) {
    Remove-Item $Decompiled -Recurse -Force
}

Write-Host "Decompiling original PolitariaIdentity..."
& java -jar $Cfr $Original --outputdir $Decompiled
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

New-Item -ItemType Directory -Force $JavaDir | Out-Null
Copy-Item (Join-Path $Decompiled "ru\politaria\identity\*.java") $JavaDir -Force

Write-Host ""
Write-Host "Source generated in src\main\java\ru\politaria\identity"
Write-Host "Next command: mvn clean package"
