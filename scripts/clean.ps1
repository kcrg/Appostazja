[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$dotNetRoot = Join-Path $repoRoot 'src/Appostazja.DotNet'
$androidRoot = Join-Path $repoRoot 'src/Appostazja.Android'

$removed = 0

function Remove-MatchingDirectories {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string[]] $Names
    )

    if (-not (Test-Path -LiteralPath $Root)) {
        return
    }

    # Materialize first so removing parents does not invalidate recursive enumeration.
    $directories = @(
        Get-ChildItem -LiteralPath $Root -Directory -Force -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -in $Names } |
            Sort-Object { $_.FullName.Length } -Descending
    )

    foreach ($directory in $directories) {
        if (Test-Path -LiteralPath $directory.FullName) {
            Write-Host "Removing directory: $($directory.FullName)"
            Remove-Item -LiteralPath $directory.FullName -Recurse -Force
            $script:removed++
        }
    }
}

function Remove-MatchingFiles {
    param(
        [Parameter(Mandatory)] [string] $Root,
        [Parameter(Mandatory)] [string[]] $Patterns
    )

    if (-not (Test-Path -LiteralPath $Root)) {
        return
    }

    foreach ($pattern in $Patterns) {
        $files = @(Get-ChildItem -LiteralPath $Root -File -Force -Recurse -Filter $pattern -ErrorAction SilentlyContinue)
        foreach ($file in $files) {
            Write-Host "Removing file: $($file.FullName)"
            Remove-Item -LiteralPath $file.FullName -Force
            $script:removed++
        }
    }
}

Write-Host 'Cleaning .NET build and IDE artifacts...'
Remove-MatchingDirectories -Root $dotNetRoot -Names @(
    'bin',
    'obj',
    '.vs',
    'TestResults',
    'artifacts',
    'BenchmarkDotNet.Artifacts'
)
Remove-MatchingFiles -Root $dotNetRoot -Patterns @(
    '*.user',
    '*.suo',
    '*.rsuser',
    '*.binlog'
)

Write-Host 'Cleaning Android/Kotlin build and IDE artifacts...'
Remove-MatchingDirectories -Root $androidRoot -Names @(
    'build',
    '.gradle',
    '.idea',
    '.kotlin',
    'captures',
    '.externalNativeBuild',
    '.cxx'
)
Remove-MatchingFiles -Root $androidRoot -Patterns @('*.iml')

# Intentionally does not touch src/Appostazja.iOS.
Write-Host "Cleanup complete. Removed $removed item(s)."
