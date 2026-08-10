param(
    [string]$Clang = "clang"
)

$ErrorActionPreference = "Stop"
$sourcePath = Join-Path $PSScriptRoot "xkb-core-fallback.c"
$assetDir = Join-Path (Split-Path $PSScriptRoot -Parent) "assets\guest"
$outputPath = Join-Path $assetDir "xkb-core-fallback-x86_64.so"

New-Item -ItemType Directory -Path $assetDir -Force | Out-Null
& $Clang `
    --target=x86_64-linux-gnu `
    -fuse-ld=lld `
    -O2 `
    -fPIC `
    -fvisibility=hidden `
    -shared `
    -nostdlib `
    "-Wl,-soname,libwinlator-xkb-core.so" `
    "-Wl,--build-id=none" `
    "-Wl,--allow-shlib-undefined" `
    $sourcePath `
    -o $outputPath

if ($LASTEXITCODE -ne 0) {
    throw "clang failed with exit code $LASTEXITCODE"
}

Write-Output $outputPath
