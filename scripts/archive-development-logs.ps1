[CmdletBinding()]
param(
    [string]$WorkspaceRoot,
    [string]$SnapshotId = (Get-Date -Format "yyyyMMdd-HHmmss")
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $scriptDirectory
if ([string]::IsNullOrWhiteSpace($WorkspaceRoot)) {
    $WorkspaceRoot = Split-Path -Parent $repositoryRoot
}
$archiveRoot = Join-Path $repositoryRoot "archive"
$snapshotRoot = Join-Path $archiveRoot ("log-imports\" + $SnapshotId)
$maximumFileBytes = 95MB
$logExtensions = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($extension in ".log", ".txt", ".csv", ".err") {
    [void]$logExtensions.Add($extension)
}

if (Test-Path -LiteralPath $snapshotRoot) {
    throw "Snapshot already exists: $snapshotRoot"
}

$sourceGroups = @(
    [pscustomobject]@{
        Name = "runtime-logs"
        Root = Join-Path $WorkspaceRoot "logs"
        Filter = { param($file) $true }
    },
    [pscustomobject]@{
        Name = "build-logs"
        Root = Join-Path $WorkspaceRoot "build-logs"
        Filter = { param($file) $true }
    },
    [pscustomobject]@{
        Name = "test-logs"
        Root = Join-Path $WorkspaceRoot "test"
        Filter = { param($file) $logExtensions.Contains($file.Extension) }
    }
)

$sourceFiles = @()
foreach ($group in $sourceGroups) {
    if (-not (Test-Path -LiteralPath $group.Root -PathType Container)) {
        continue
    }

    foreach ($file in Get-ChildItem -LiteralPath $group.Root -File -Recurse) {
        if (& $group.Filter $file) {
            $sourceFiles += [pscustomobject]@{
                Group = $group.Name
                Root = $group.Root
                File = $file
            }
        }
    }
}

if ($sourceFiles.Count -eq 0) {
    throw "No development logs were found under $WorkspaceRoot"
}

$oversizedFiles = @($sourceFiles | Where-Object { $_.File.Length -gt $maximumFileBytes })
if ($oversizedFiles.Count -gt 0) {
    $paths = $oversizedFiles.File.FullName -join [Environment]::NewLine
    throw "Files exceed the 95 MiB archive guard:`n$paths"
}

$credentialPattern = "(?i)(authorization\s*:|bearer\s+[a-z0-9._-]{12,}|password\s*[=:]|passwd\s*[=:]|api[_-]?key\s*[=:]|secret\s*[=:]|github_pat_[a-z0-9_]+|ghp_[a-z0-9]+|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----)"
$sensitiveFiles = @()
foreach ($source in $sourceFiles) {
    if (Select-String -LiteralPath $source.File.FullName -Pattern $credentialPattern -Quiet -ErrorAction SilentlyContinue) {
        $sensitiveFiles += $source.File.FullName
    }
}
if ($sensitiveFiles.Count -gt 0) {
    throw "Possible credentials found; inspect before archiving:`n$($sensitiveFiles -join [Environment]::NewLine)"
}

# Build the index from origin/main, not the checked-out archive. This makes the
# decision reflect what is actually available after a fresh remote clone.
$existingByGitBlob = @{}
$remoteArchivePaths = @(
    "archive/build-logs",
    "archive/runtime-logs",
    "archive/test",
    "archive/log-imports"
)
$remoteEntries = & git -C $repositoryRoot -c core.quotePath=false ls-tree -r origin/main -- $remoteArchivePaths
if ($LASTEXITCODE -ne 0) {
    throw "Unable to read the origin/main archive tree"
}
foreach ($entry in $remoteEntries) {
    if ($entry -match '^\d+ blob ([0-9a-f]+)\s+(.+)$') {
        $gitBlob = $matches[1]
        if (-not $existingByGitBlob.ContainsKey($gitBlob)) {
            $existingByGitBlob[$gitBlob] = $matches[2]
        }
    }
}

$localImportRoot = Join-Path $archiveRoot "log-imports"
if (Test-Path -LiteralPath $localImportRoot -PathType Container) {
    foreach ($file in Get-ChildItem -LiteralPath $localImportRoot -File -Recurse) {
        if ($file.FullName.StartsWith($snapshotRoot + "\", [System.StringComparison]::OrdinalIgnoreCase)) {
            continue
        }

        $gitBlob = (& git -C $repositoryRoot hash-object --no-filters -- $file.FullName).Trim()
        if ($LASTEXITCODE -ne 0) {
            throw "Unable to hash $($file.FullName)"
        }
        if (-not $existingByGitBlob.ContainsKey($gitBlob)) {
            $existingByGitBlob[$gitBlob] = $file.FullName.Substring($repositoryRoot.Length + 1).Replace("\", "/")
        }
    }
}

$manifest = @()
foreach ($source in $sourceFiles | Sort-Object { $_.File.FullName }) {
    $relativeSource = $source.File.FullName.Substring($WorkspaceRoot.Length).TrimStart("\").Replace("\", "/")
    $relativeWithinGroup = $source.File.FullName.Substring($source.Root.Length).TrimStart("\")
    $sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $source.File.FullName).Hash
    $gitBlob = (& git -C $repositoryRoot hash-object --no-filters -- $source.File.FullName).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to hash $($source.File.FullName)"
    }

    if ($existingByGitBlob.ContainsKey($gitBlob)) {
        $status = "already-archived"
        $archivePath = $existingByGitBlob[$gitBlob]
    }
    else {
        $destination = Join-Path (Join-Path $snapshotRoot $source.Group) $relativeWithinGroup
        $destinationDirectory = Split-Path -Parent $destination
        [void](New-Item -ItemType Directory -Force -Path $destinationDirectory)
        Copy-Item -LiteralPath $source.File.FullName -Destination $destination
        $status = "copied"
        $archivePath = $destination.Substring($repositoryRoot.Length + 1).Replace("\", "/")
    }

    $manifest += [pscustomobject]@{
        SourcePath = $relativeSource
        Bytes = $source.File.Length
        SHA256 = $sha256
        GitBlob = $gitBlob
        Status = $status
        ArchivePath = $archivePath
    }
}

[void](New-Item -ItemType Directory -Force -Path $snapshotRoot)
$manifestPath = Join-Path $snapshotRoot "MANIFEST.csv"
$manifest | Export-Csv -LiteralPath $manifestPath -NoTypeInformation -Encoding UTF8

$copied = @($manifest | Where-Object { $_.Status -eq "copied" })
$copiedBytes = 0
if ($copied.Count -gt 0) {
    $copiedBytes = ($copied | Measure-Object -Property Bytes -Sum).Sum
}
$summary = @(
    "# Development Log Import $SnapshotId",
    "",
    "- Workspace: ``$WorkspaceRoot``",
    "- Scanned files: $($manifest.Count)",
    "- Newly copied paths: $($copied.Count)",
    "- Newly copied bytes: $copiedBytes",
    "- Existing archive references: $($manifest.Count - $copied.Count)",
    "- Credential scan: passed",
    "- Maximum-file-size guard: passed",
    "",
    "The manifest records every scanned source path, byte size, SHA-256, status, and archive location."
)
$summary | Set-Content -LiteralPath (Join-Path $snapshotRoot "README.md") -Encoding UTF8

Write-Output "Snapshot: $snapshotRoot"
Write-Output "Scanned: $($manifest.Count)"
Write-Output "Copied: $($copied.Count)"
Write-Output "Copied bytes: $copiedBytes"
