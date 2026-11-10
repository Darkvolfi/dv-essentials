# Makes the Block of Soul Coal's texture: the game's coal block (read from Minecraft's client jar in the Gradle cache)
# with its greys turned into the dark blues of soul coal's veins.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-soul-coal-block-texture.ps1
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\block\soul_coal_block.png'
$jar = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$stream = $zip.GetEntry('assets/minecraft/textures/block/coal_block.png').Open()
$src = New-Object System.Drawing.Bitmap $stream
$stream.Dispose(); $zip.Dispose()
$dst = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $l = $src.GetPixel($x, $y).GetBrightness()
        # the coal's darks stay near-black blue; its lighter specks take the veins' blues
        $c = if ($l -gt 0.17) { @(59, 107, 214) } elseif ($l -gt 0.11) { @(30, 58, 138) } elseif ($l -gt 0.07) { @(23, 38, 90) } else { @(14, 20, 48) }
        $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $c[0], $c[1], $c[2]))
    }
}
$dst.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
$src.Dispose(); $dst.Dispose()
Write-Host "Wrote $out"
