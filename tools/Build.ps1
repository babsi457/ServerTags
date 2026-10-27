param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
if (-not $JavaHome) {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $project '.tools/jdk') -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
    if ($localJdk) { $JavaHome = $localJdk.FullName }
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Bitte ein Java-25-JDK installieren und JAVA_HOME setzen.'
}
$oldJava = $env:JAVA_HOME
$oldGradle = $env:GRADLE_USER_HOME
Push-Location $project
try {
    $env:JAVA_HOME = $JavaHome
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $project '.tools/gradle-home' }
    & .\gradlew.bat build resourcePack distribution --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen: $LASTEXITCODE" }
    Write-Output "Fertige Dateien: $(Join-Path $project 'dist')"
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:GRADLE_USER_HOME = $oldGradle
}
