param([string]$OutputPath = '', [switch]$Force)

$ErrorActionPreference = 'Stop'
$project = Split-Path -Parent $PSScriptRoot
$versionLine = Get-Content -LiteralPath (Join-Path $project 'gradle.properties') |
    Where-Object { $_ -match '^version=' } | Select-Object -First 1
if (-not $versionLine) { throw 'Projektversion fehlt in gradle.properties.' }
$version = ($versionLine -split '=', 2)[1].Trim()

if (-not $OutputPath) {
    $OutputPath = Join-Path $project "dist/ServerTags-$version-GitHub.zip"
}
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
if ((Test-Path -LiteralPath $OutputPath) -and -not $Force) {
    throw "Datei existiert bereits: $OutputPath. Mit -Force ersetzen."
}

$files = [Collections.Generic.List[IO.FileInfo]]::new()
$rootFiles = @(
    '.editorconfig', '.gitattributes', '.gitignore',
    'build.gradle', 'settings.gradle', 'gradle.properties',
    'gradlew', 'gradlew.bat', 'README.md', 'CHANGELOG.md', 'LICENSE'
)
foreach ($name in $rootFiles) {
    $files.Add((Get-Item -LiteralPath (Join-Path $project $name)))
}
foreach ($directory in @('src', 'gradle', 'examples', 'tools')) {
    Get-ChildItem -LiteralPath (Join-Path $project $directory) -Recurse -File |
        Where-Object { $_.Extension -in @('.java', '.json', '.png', '.txt', '.properties', '.ps1', '.jar') } |
        ForEach-Object { $files.Add($_) }
}
foreach ($name in @('ICONS.md', 'TESTING.md', 'ASSETS.md', 'PUBLISHING.md', 'icons-preview.png')) {
    $files.Add((Get-Item -LiteralPath (Join-Path $project "docs/$name")))
}

Add-Type -AssemblyName System.IO.Compression
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($OutputPath)) | Out-Null
$stream = [IO.File]::Open($OutputPath, [IO.FileMode]::Create, [IO.FileAccess]::Write)
try {
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($file in ($files | Sort-Object FullName -Unique)) {
            $relative = $file.FullName.Substring($project.Length + 1).Replace('\', '/')
            $entry = $zip.CreateEntry("ServerTags/$relative", [IO.Compression.CompressionLevel]::Optimal)
            $entry.LastWriteTime = [DateTimeOffset]::new(1980, 1, 1, 0, 0, 0, [TimeSpan]::Zero)
            $mode = if ($relative -eq 'gradlew') { 33261 } else { 33188 }
            $entry.ExternalAttributes = $mode -shl 16
            $input = [IO.File]::OpenRead($file.FullName)
            $output = $entry.Open()
            try { $input.CopyTo($output) }
            finally { $output.Dispose(); $input.Dispose() }
        }
    } finally { $zip.Dispose() }
} finally { $stream.Dispose() }

$hash = (Get-FileHash -LiteralPath $OutputPath -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText("$OutputPath.sha256", "$hash`n", [Text.UTF8Encoding]::new($false))
Write-Output "Quellcode: $OutputPath"
Write-Output "SHA-256: $hash"
