# Makes DV Glass's connected-glass textures from the game's own glass textures (read from Minecraft's client jar
# in the Gradle cache). Each glass gets 5 variants, used per quarter of a face:
#   full        frame on all four sides (a lone block looks like vanilla)
#   horizontal  joined left and right: only the top and bottom frame lines
#   vertical    joined above and below: only the left and right frame lines
#   corner      joined on both sides but not diagonally: only the corner dots
#   none        joined all round: no frame
# and the same 5 again as clear_* for the borderless setting, with the streaks inside the glass removed.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-glass-textures.ps1
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'dvglass\src\main\resources\assets\dvglass\textures\block'
New-Item -ItemType Directory -Force $out | Out-Null
$jar = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'

$colors = 'white', 'orange', 'magenta', 'light_blue', 'yellow', 'lime', 'pink', 'gray', 'light_gray', 'cyan', 'purple', 'blue', 'brown', 'green', 'red', 'black'
$names = @('glass', 'tinted_glass') + ($colors | ForEach-Object { "${_}_stained_glass" })
# Which frame lines each variant keeps: left, right, top, bottom; corner keeps only the four corner pixels.
$variants = [ordered]@{
    full       = @($true, $true, $true, $true)
    horizontal = @($false, $false, $true, $true)
    vertical   = @($true, $true, $false, $false)
    corner     = @($false, $false, $false, $false)
    none       = @($false, $false, $false, $false)
}

$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$count = 0
foreach ($name in $names) {
    $stream = $zip.GetEntry("assets/minecraft/textures/block/$name.png").Open()
    $src = New-Object System.Drawing.Bitmap $stream
    $stream.Dispose()
    # The glass's own colour: the most common pixel inside the frame (clear for plain glass).
    $fill = $src.GetPixel(1, 1); $best = 0; $seen = @{}
    for ($y = 1; $y -lt 15; $y++) { for ($x = 1; $x -lt 15; $x++) {
        $c = $src.GetPixel($x, $y); $k = $c.ToArgb(); $seen[$k] = 1 + $seen[$k]
        if ($seen[$k] -gt $best) { $best = $seen[$k]; $fill = $c }
    } }
    foreach ($clear in $false, $true) {
        foreach ($v in $variants.Keys) {
            $keep = $variants[$v]
            $dst = New-Object System.Drawing.Bitmap 16, 16
            for ($y = 0; $y -lt 16; $y++) {
                for ($x = 0; $x -lt 16; $x++) {
                    $side = ($x -eq 0 -and $keep[0]) -or ($x -eq 15 -and $keep[1])   # on a kept left/right line
                    $flat = ($y -eq 0 -and $keep[2]) -or ($y -eq 15 -and $keep[3])   # on a kept top/bottom line
                    $edge = $x -eq 0 -or $x -eq 15 -or $y -eq 0 -or $y -eq 15
                    $corner = ($x -eq 0 -or $x -eq 15) -and ($y -eq 0 -or $y -eq 15)
                    if ($side -and $flat) { $c = $src.GetPixel($x, $y) }
                    elseif ($side) { $c = $src.GetPixel($x, [Math]::Min(14, [Math]::Max(1, $y))) }  # line runs on past the dropped corner
                    elseif ($flat) { $c = $src.GetPixel([Math]::Min(14, [Math]::Max(1, $x)), $y) }
                    elseif ($v -eq 'corner' -and $corner) { $c = $src.GetPixel($x, $y) }
                    elseif ($edge -or $clear) { $c = $fill }
                    else { $c = $src.GetPixel($x, $y) }
                    $dst.SetPixel($x, $y, $c)
                }
            }
            $file = if ($clear) { "${name}_clear_$v.png" } else { "${name}_$v.png" }
            $dst.Save((Join-Path $out $file), [System.Drawing.Imaging.ImageFormat]::Png)
            $dst.Dispose(); $count++
        }
    }
    $src.Dispose()
}
$zip.Dispose()
Write-Host "Wrote $count textures to $out"
