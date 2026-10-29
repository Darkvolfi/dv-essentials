# Makes DV Science of Souls' hybrid creature textures from the game's own (read from Minecraft's client jar in the
# Gradle cache), so each hybrid fits the model of the creature it is built on:
#   textures/entity/woolly_cow.png          the cow with its body under wool; _sheared is the plain cow
#   textures/entity/feathered_pig.png       the pig with a feathered body, and its wings below (64x64)
#   textures/entity/lava_horse.png          a basalt-dark horse with ember cracks; _glow is the cracks alone
#   textures/block/soul_furnace_front*.png  the furnace's front in blackstone with blue soul fire
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-souls-mobs.ps1

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\entity'
New-Item -ItemType Directory -Force $out | Out-Null
$jarPath = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'
if (-not (Test-Path $jarPath)) { throw "Minecraft's client jar not found: $jarPath (run a Gradle build once first)" }
$jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)

function Load([string]$name) {
    $stream = $jar.GetEntry("assets/minecraft/textures/$name.png").Open()
    $bmp = New-Object System.Drawing.Bitmap $stream
    $copy = New-Object System.Drawing.Bitmap $bmp
    $bmp.Dispose(); $stream.Dispose()
    return $copy
}

function Col([string]$hex, [int]$a = 255) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb($a, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255)
}

function Save($bmp, [string]$name) {
    $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
}

# Maps each pixel's brightness (0-1) onto a list of colour stops @(position, '#hex'), keeping its alpha.
function Gradient($bmp, $stops) {
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $v = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
            for ($i = 1; $i -lt $stops.Count; $i++) {
                if ($v -le $stops[$i][0] -or $i -eq $stops.Count - 1) {
                    $a = Col $stops[$i - 1][1]; $b = Col $stops[$i][1]
                    $t = [Math]::Max(0, [Math]::Min(1, ($v - $stops[$i - 1][0]) / ($stops[$i][0] - $stops[$i - 1][0])))
                    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A,
                        [int]($a.R + ($b.R - $a.R) * $t), [int]($a.G + ($b.G - $a.G) * $t), [int]($a.B + ($b.B - $a.B) * $t)))
                    break
                }
            }
        }
    }
}

# Fills a rectangle of the texture with $pattern (a scriptblock giving the colour at x, y).
function Fill($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h, [scriptblock]$pattern) {
    for ($y = $y0; $y -lt $y0 + $h; $y++) { for ($x = $x0; $x -lt $x0 + $w; $x++) { $bmp.SetPixel($x, $y, (& $pattern $x $y)) } }
}

# ---------- Woolly Cow: the cow with its body (box at 18,4: 12 wide, 18 long, 10 deep) bare, pinkish skin where its
# hide was, darker where its patches were; and its wool, a separate layer drawn puffed out over it.
$cow = Load 'entity/cow/cow'
for ($y = 4; $y -lt 32; $y++) {
    for ($x = 18; $x -lt 62; $x++) {
        if ($y -lt 14 -and ($x -lt 28 -or $x -ge 52)) { continue }   # not part of the body's top and bottom faces
        $c = $cow.GetPixel($x, $y)
        $dark = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) -lt 110
        $cow.SetPixel($x, $y, $(if ($dark) { Col '#C99A88' } else { Col '#EDC8B8' }))
    }
}
Save $cow 'woolly_cow'
$wool = Load 'block/white_wool'
$coat = New-Object System.Drawing.Bitmap 64, 32
for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 64; $x++) { $coat.SetPixel($x, $y, $wool.GetPixel($x % 16, $y % 16)) } }
Save $coat 'woolly_cow_wool'

# ---------- Feathered Pig: the pink pig on top, with soft white feathers over its back and sides (body box at 28,8:
# 10 wide, 16 long, 8 deep; the belly and both ends stay pink); below it the wings (box at 0,32) and the comb (16,32).
$pig = Load 'entity/pig/pig'
$feathered = New-Object System.Drawing.Bitmap 64, 64
$g = [System.Drawing.Graphics]::FromImage($feathered); $g.DrawImage($pig, 0, 0, 64, 32); $g.Dispose()
# Mostly plain, with a row of feather tips every fourth row, shifted every other row.
$feather = { param($x, $y) if (($y % 4) -eq 3 -and (($x + [Math]::Floor($y / 4) * 2) % 4) -lt 2) { Col '#E8DCCB' } else { Col '#FFF8EE' } }
Fill $feathered 28 16 8 16 $feather    # one side
Fill $feathered 46 16 8 16 $feather    # the other side
Fill $feathered 54 16 10 16 $feather   # the back
Fill $feathered 0 32 14 10 { param($x, $y) if ($y -ge 40) { Col '#D8C6B0' } else { & $feather $x $y } }
Fill $feathered 16 32 12 7 { param($x, $y) if ($y -ge 37) { Col '#A8201C' } else { Col '#D8322C' } }
Save $feathered 'feathered_pig'

# ---------- Lava Horse: the black horse darkened to basalt, with ember-orange cracks (also drawn alone, to glow).
$horse = Load 'entity/horse/horse_black'
Gradient $horse @(@(0.0, '#120C0A'), @(0.3, '#2A201C'), @(1.0, '#5A4A42'))
$glow = New-Object System.Drawing.Bitmap 64, 64
$rand = New-Object System.Random 5
for ($y = 0; $y -lt 64; $y++) {
    for ($x = 0; $x -lt 64; $x++) {
        if ($horse.GetPixel($x, $y).A -eq 0 -or $rand.NextDouble() -ge 0.07) { continue }
        $ember = if ($rand.Next(3) -eq 0) { Col '#FFD04A' } else { Col '#FF7A1A' }
        $horse.SetPixel($x, $y, $ember)
        $glow.SetPixel($x, $y, $ember)
    }
}
Save $horse 'lava_horse'
Save $glow 'lava_horse_glow'


# ---------- Soul Furnace: the furnace's front in blackstone, its fire turned to blue soul fire (the lit one is
# animated, so its .mcmeta goes along).
$blocks = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\block'
New-Item -ItemType Directory -Force $blocks | Out-Null
foreach ($pair in @(@('furnace_front', 'soul_furnace_front'), @('furnace_front_on', 'soul_furnace_front_on'))) {
    $front = Load "block/$($pair[0])"
    for ($y = 0; $y -lt $front.Height; $y++) {
        for ($x = 0; $x -lt $front.Width; $x++) {
            $c = $front.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $max = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)); $min = [Math]::Min($c.R, [Math]::Min($c.G, $c.B))
            $v = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
            # Fire is the coloured pixels, and the near-white ones in its heart (the bottom rows, below the stone ledge).
            if (($max -gt 0 -and ($max - $min) / $max -gt 0.3) -or ($v -gt 0.72 -and $y -ge 11)) {
                # Fire: its brightness on soul fire's colours, deep teal through cyan to white, so its dark embers
                # stay dark instead of turning a pale blue that reads as stone.
                $stops = @(@(0.0, '#06323A'), @(0.35, '#0B6470'), @(0.55, '#14A8BC'), @(0.75, '#46E2F2'), @(0.9, '#BDFAFF'), @(1.0, '#FFFFFF'))
                for ($i = 1; $i -lt $stops.Count; $i++) {
                    if ($v -le $stops[$i][0] -or $i -eq $stops.Count - 1) {
                        $a = Col $stops[$i - 1][1]; $b = Col $stops[$i][1]
                        $t = [Math]::Max(0, [Math]::Min(1, ($v - $stops[$i - 1][0]) / ($stops[$i][0] - $stops[$i - 1][0])))
                        $front.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A,
                            [int]($a.R + ($b.R - $a.R) * $t), [int]($a.G + ($b.G - $a.G) * $t), [int]($a.B + ($b.B - $a.B) * $t)))
                        break
                    }
                }
            } else {
                # Stone: blackstone's dark purple-grey.
                $front.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, [int](18 + 70 * $v), [int](15 + 62 * $v), [int](22 + 78 * $v)))
            }
        }
    }
    $front.Save((Join-Path $blocks "$($pair[1]).png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $meta = $jar.GetEntry("assets/minecraft/textures/block/$($pair[0]).png.mcmeta")
    if ($meta) {
        $reader = New-Object System.IO.StreamReader $meta.Open()
        [System.IO.File]::WriteAllText((Join-Path $blocks "$($pair[1]).png.mcmeta"), $reader.ReadToEnd())
        $reader.Dispose()
    }
}

$jar.Dispose()
Write-Host "Textures written to $out"
