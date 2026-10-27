param(
    [switch]$AcceptMinecraftEula,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$LoaderVersion = '',
    [string]$FabricApiVersion = '',
    [ValidatePattern('^run-test(?:-[a-z0-9.-]+)?$')][string]$TestDirectoryName = 'run-test'
)
$ErrorActionPreference = 'Stop'
if (-not $AcceptMinecraftEula) { throw 'Für diesen lokalen Testserver die Minecraft-EULA prüfen und -AcceptMinecraftEula übergeben.' }
$project = Split-Path -Parent $PSScriptRoot
$properties = @{}
Get-Content -LiteralPath (Join-Path $project 'gradle.properties') | ForEach-Object {
    if ($_ -match '^([^#=]+)=(.*)$') { $properties[$matches[1].Trim()] = $matches[2].Trim() }
}
if (-not $LoaderVersion) { $LoaderVersion = $properties['loader_version'] }
if (-not $FabricApiVersion) { $FabricApiVersion = $properties['fabric_api_version'] }
$modVersion = $properties['version']
$testRoot = Join-Path $project $TestDirectoryName
$testMods = Join-Path $testRoot 'mods'
if (-not $JavaHome) {
    $jdk = Get-ChildItem -LiteralPath (Join-Path $project '.tools/jdk') -Directory -ErrorAction SilentlyContinue |
        Where-Object Name -Like 'jdk-25*' | Select-Object -First 1
    if ($jdk) { $JavaHome = $jdk.FullName }
}
if (-not $JavaHome) { throw 'JAVA_HOME muss auf ein Java-25-JDK zeigen.' }
$oldJava = $env:JAVA_HOME
$oldGradle = $env:GRADLE_USER_HOME
Push-Location $project
try {
    $env:JAVA_HOME = $JavaHome
    if (-not $env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME = Join-Path $project '.tools/gradle-home' }
    & .\gradlew.bat build serverTestJar --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Test-Build fehlgeschlagen.' }
    New-Item -ItemType Directory -Force $testMods | Out-Null
    # Only remove managed test dependencies so an earlier run cannot load two versions.
    Get-ChildItem -LiteralPath $testMods -Filter '*.jar' -File | Where-Object {
        $_.Name -match '^(servertags-.+|fabric-api-.+)\.jar$'
    } | ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    function Download([string]$Url, [string]$Destination) {
        & curl.exe -fL --silent --show-error $Url -o $Destination
        if ($LASTEXITCODE -ne 0) { throw "Download fehlgeschlagen: $Url" }
    }
    Download "https://meta.fabricmc.net/v2/versions/loader/26.3/$LoaderVersion/1.1.2/server/jar" (Join-Path $testRoot 'fabric-server-launch.jar')
    Download "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$FabricApiVersion/fabric-api-$FabricApiVersion.jar" (Join-Path $testMods "fabric-api-$FabricApiVersion.jar")
    Download 'https://cdn.modrinth.com/data/gG7VFbG0/versions/HlsRTW5F/TAB%20v6.2.1.jar' (Join-Path $testMods 'TAB-6.2.1.jar')
    if ((Get-FileHash -LiteralPath (Join-Path $testMods 'TAB-6.2.1.jar') -Algorithm SHA1).Hash -ne 'CB772026895BFA856C29A47B6951E1F4910A0AA0') { throw 'TAB-Prüfsumme stimmt nicht.' }
    Copy-Item -LiteralPath "build/libs/servertags-$modVersion.jar","build/libs/servertags-test-harness-$modVersion.jar" -Destination $testMods -Force
    [IO.File]::WriteAllText((Join-Path $testRoot 'eula.txt'), "eula=true`n")
    [IO.File]::WriteAllText((Join-Path $testRoot 'server.properties'), "server-ip=127.0.0.1`nserver-port=25585`nonline-mode=false`nenable-status=false`nlevel-name=test-world`nview-distance=2`nsimulation-distance=2`nmax-players=2`nspawn-protection=0`n")
    $resultFile = Join-Path $testRoot 'integration-results.txt'
    if (Test-Path -LiteralPath $resultFile) { Remove-Item -LiteralPath $resultFile }
    Set-Location $testRoot
    & (Join-Path $JavaHome 'bin/java.exe') -Xms512M -Xmx1G "-Dservertags.test.loader=$LoaderVersion" "-Dservertags.test.fabricApi=$FabricApiVersion" -jar fabric-server-launch.jar nogui
    if ($LASTEXITCODE -ne 0) { throw 'Testserver fehlgeschlagen.' }
    if (-not (Test-Path -LiteralPath $resultFile)) { throw 'Testserver hat kein Ergebnis geschrieben.' }
    $results = Get-Content -LiteralPath $resultFile
    if ($results -match '^FAIL' -or -not ($results -contains 'PASS ALL SERVER INTEGRATION TESTS')) { throw ($results -join "`n") }
    $results
} finally {
    Pop-Location
    $env:JAVA_HOME = $oldJava
    $env:GRADLE_USER_HOME = $oldGradle
}
