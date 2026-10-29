# Draws DV Backpack's 16x16 textures (no external art). Every backpack is leather; the straps and buckles are grey
# and the game tints them in the tier's colour, the rolled sleeping bag in the colour picked for it.
#   textures/item/backpack.png          item sprite: the leather, not tinted
#   textures/item/backpack_straps.png   its straps and buckles, tinted per tier
#   textures/item/backpack_roll.png     its rolled sleeping bag, tinted in the bag's colour
#   textures/block/backpack_leather.png      leather for the bag, with stitching round the edge
#   textures/block/backpack_leather_dark.png darker leather for the lid and the pocket's flap
#   textures/block/backpack_strap.png   front straps (grey, tinted per tier)
#   textures/block/backpack_buckle.png  buckles (grey metal, tinted per tier)
#   textures/block/backpack_trim.png    the shoulder straps, plain dark leather
#   textures/item/upgrade_frame.png     the upgrade cards' frame; upgrade_face.png is the blank card's face
#   textures/block/sleeping_bag_*.png   the sleeping bag (grey, tinted): quilted top, side with a zip; pillow
#   textures/block/tank_glass.png     the tanks' glass, see-through with streaks
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-backpack-textures.ps1

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tex = Join-Path $root 'dvbackpack\src\main\resources\assets\dvbackpack\textures'

function Col([string]$hex) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb(255, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255)
}

# rows: one char per pixel; '.' is transparent, other chars come from $palette.
function Sprite([string]$path, [string[]]$rows, [hashtable]$palette) {
    foreach ($row in $rows) { if ($row.Length -ne 16) { throw "Row '$row' is $($row.Length) wide in $path" } }
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $c = [string]$rows[$y][$x]
            if ($c -ne '.') { $bmp.SetPixel($x, $y, (Col $palette[$c])) }
        }
    }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# Fills the whole texture with a colour, each pixel nudged up or down a little (cloth / leather grain).
function Noise([string]$path, [string]$hex, [int]$spread, [int]$seed, [scriptblock]$extra) {
    $rand = New-Object System.Random $seed
    $base = Col $hex
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $d = $rand.Next(-$spread, $spread + 1)
            $r = [Math]::Max(0, [Math]::Min(255, $base.R + $d))
            $g = [Math]::Max(0, [Math]::Min(255, $base.G + $d))
            $b = [Math]::Max(0, [Math]::Min(255, $base.B + $d))
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $r, $g, $b))
        }
    }
    if ($extra) { & $extra $bmp }
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# The item: a tall leather bag with a lid and a front pocket (L lid, B bag, P pocket, o outline: plain leather),
# two straps and buckles down the front (s, b, k: tinted per tier) and the sleeping bag rolled under it (r, d).
$item = @(
    '................',
    '...oooooooooo...',
    '...oLsLLLLsLo...',
    '...oLsLbbLsLo...',
    '...oooooooooo...',
    '...oBsBBBBsBo...',
    '...oBsBBBBsBo...',
    '...oBsBBBBsBo...',
    '...oBooooooBo...',
    '...oBoPbbPoBo...',
    '...oBoPPPPoBo...',
    '...oBooooooBo...',
    '...oooooooooo...',
    '..orkrrrrrrkro..',
    '..odkddddddkdo..',
    '..oooooooooooo..'
)
function Layer([string[]]$rows, [string]$keep) {
    $out = @()
    foreach ($row in $rows) {
        $line = ''
        foreach ($ch in $row.ToCharArray()) { $line += $(if ($keep.Contains([string]$ch)) { [string]$ch } else { '.' }) }
        $out += $line
    }
    , $out
}
Sprite (Join-Path $tex 'item\backpack.png') (Layer $item 'oLBP') @{ o = '#3A2414'; L = '#7A4E2C'; B = '#94603A'; P = '#86552F' }
Sprite (Join-Path $tex 'item\backpack_straps.png') (Layer $item 'sbk') @{ s = '#C8C8C8'; b = '#F4F4F4'; k = '#B4B4B4' }
Sprite (Join-Path $tex 'item\backpack_roll.png') (Layer $item 'rd') @{ r = '#E2E2E2'; d = '#B0B0B0' }

# The block model's textures.
Noise (Join-Path $tex 'block\backpack_leather.png') '#94603A' 7 7 {
    param($bmp)
    for ($i = 0; $i -lt 16; $i++) {
        foreach ($p in @(@($i, 0), @($i, 15), @(0, $i), @(15, $i))) { $bmp.SetPixel($p[0], $p[1], (Col '#5E3A20')) }
    }
}
Noise (Join-Path $tex 'block\backpack_leather_dark.png') '#7A4E2C' 6 8 {
    param($bmp)
    for ($i = 0; $i -lt 16; $i += 2) { $bmp.SetPixel($i, 1, (Col '#A67650')); $bmp.SetPixel($i, 14, (Col '#A67650')) }
}
Noise (Join-Path $tex 'block\backpack_strap.png') '#C4C4C4' 6 9 {
    param($bmp)
    # Stitches down both edges of the strap.
    for ($i = 0; $i -lt 16; $i += 2) { foreach ($x in 0, 15) { $bmp.SetPixel($x, $i, (Col '#E8E8E8')) } }
}
Noise (Join-Path $tex 'block\backpack_buckle.png') '#E0E0E0' 4 10 {
    param($bmp)
    for ($i = 0; $i -lt 16; $i++) { foreach ($p in @(@($i, 0), @(0, $i))) { $bmp.SetPixel($p[0], $p[1], (Col '#FFFFFF')) }; foreach ($p in @(@($i, 15), @(15, $i))) { $bmp.SetPixel($p[0], $p[1], (Col '#8C8C8C')) } }
}
Noise (Join-Path $tex 'block\backpack_trim.png') '#4A3628' 6 11 $null
# Upgrade cards: a leather frame with rounded corners, a stitch round it and metal rivets in the corners.
# The middle (pixels 3-12) is parchment, covered by the card's face: the blank card's ruled parchment, or the
# game's own texture of a block or item (crafting table top, furnace front, hopper...), which the item model points
# at; an item's see-through pixels show the parchment.
$frame = @()
for ($y = 0; $y -lt 16; $y++) {
    $row = ''
    for ($x = 0; $x -lt 16; $x++) {
        $edge = [Math]::Min([Math]::Min($x, 15 - $x), [Math]::Min($y, 15 - $y))
        $corner = ($x -eq 0 -or $x -eq 15) -and ($y -eq 0 -or $y -eq 15)
        if ($corner) { $row += '.' }
        elseif (($x -eq 2 -or $x -eq 13) -and ($y -eq 2 -or $y -eq 13)) { $row += 'r' }
        elseif ($edge -eq 0) { $row += 'o' }
        elseif ($edge -eq 1 -and (($x + $y) % 2 -eq 0)) { $row += 's' }
        elseif ($edge -le 2) { $row += 'l' }
        else { $row += 'i' }
    }
    $frame += $row
}
Sprite (Join-Path $tex 'item\upgrade_frame.png') $frame @{ o = '#2E1C0E'; l = '#8A5A34'; s = '#C99A66'; r = '#D8D8E0'; i = '#E8D9B0' }
Noise (Join-Path $tex 'item\upgrade_face.png') '#E8D9B0' 6 5 {
    param($bmp)
    # A few faint ruled lines, like an empty form.
    foreach ($y in 4, 8, 12) { for ($x = 2; $x -lt 14; $x++) { $bmp.SetPixel($x, $y, (Col '#C8B890')) } }
}
# The sleeping bag, grey (the game tints it in the colour picked for it): quilted on top, a darker side with a
# lighter zip; and a white pillow, not tinted.
Noise (Join-Path $tex 'block\sleeping_bag_top.png') '#D4D4D4' 6 21 {
    param($bmp)
    for ($i = 0; $i -lt 16; $i++) { foreach ($q in 3, 7, 11, 15) { $bmp.SetPixel($q, $i, (Col '#A8A8A8')); $bmp.SetPixel($i, $q, (Col '#A8A8A8')) } }
}
Noise (Join-Path $tex 'block\sleeping_bag_side.png') '#B4B4B4' 5 22 {
    param($bmp)
    for ($x = 0; $x -lt 16; $x++) { $bmp.SetPixel($x, 9, (Col '#F4F4F4')) }
}
Noise (Join-Path $tex 'block\sleeping_bag_pillow.png') '#E6E4DA' 5 23 $null
# The tanks' glass: pale and see-through, with two bright streaks down it and a faint mark every 4 pixels.
$glass = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $c = [System.Drawing.Color]::FromArgb(45, 210, 235, 255)
        if ($y % 4 -eq 0) { $c = [System.Drawing.Color]::FromArgb(90, 225, 245, 255) }
        if ($x -eq 0 -or $x -eq 15) { $c = [System.Drawing.Color]::FromArgb(120, 190, 215, 235) }
        if ($x -eq 3 -or $x -eq 4 -or $x -eq 11) { $c = [System.Drawing.Color]::FromArgb(150, 255, 255, 255) }
        $glass.SetPixel($x, $y, $c)
    }
}
$glass.Save((Join-Path $tex 'block\tank_glass.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$glass.Dispose()
Write-Host "Textures written to $tex"
