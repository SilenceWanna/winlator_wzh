[CmdletBinding()]
param(
    [string]$WorkspaceRoot,
    [string]$InventoryId = (Get-Date -Format "yyyyMMdd-HHmmss")
)

$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$repositoryRoot = Split-Path -Parent $scriptDirectory
if ([string]::IsNullOrWhiteSpace($WorkspaceRoot)) {
    $WorkspaceRoot = Split-Path -Parent $repositoryRoot
}

$gamesRoot = Join-Path $WorkspaceRoot "games"
if (-not (Test-Path -LiteralPath $gamesRoot -PathType Container)) {
    throw "Games directory not found: $gamesRoot"
}

$outputRoot = Join-Path $repositoryRoot "archive\game-inventory"
$outputPath = Join-Path $outputRoot ("local-games-" + $InventoryId + ".csv")
if (Test-Path -LiteralPath $outputPath) {
    throw "Inventory already exists: $outputPath"
}

$rows = foreach ($item in Get-ChildItem -LiteralPath $gamesRoot -Force | Sort-Object Name) {
    if ($item.PSIsContainer) {
        $files = @(Get-ChildItem -LiteralPath $item.FullName -File -Recurse -Force -ErrorAction Stop)
        [pscustomobject]@{
            Name = $item.Name
            Type = "directory"
            Files = $files.Count
            Bytes = ($files | Measure-Object -Property Length -Sum).Sum
            SHA256 = ""
            RemoteContent = "excluded-commercial-game-content"
        }
    }
    else {
        [pscustomobject]@{
            Name = $item.Name
            Type = "file"
            Files = 1
            Bytes = $item.Length
            SHA256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $item.FullName).Hash
            RemoteContent = if ($item.Extension -ieq ".pdf") { "archived-separately" } else { "excluded-commercial-game-content" }
        }
    }
}

[void](New-Item -ItemType Directory -Force -Path $outputRoot)
$rows | Export-Csv -LiteralPath $outputPath -NoTypeInformation -Encoding UTF8

Write-Output "Inventory: $outputPath"
Write-Output "Entries: $($rows.Count)"
Write-Output "Files represented: $(($rows | Measure-Object -Property Files -Sum).Sum)"
Write-Output "Bytes represented: $(($rows | Measure-Object -Property Bytes -Sum).Sum)"
