# Draws DV Science of Souls' hybrid creatures and soul furnace fronts from scratch. Nothing is read from the game; only the
# layouts of the models they are drawn on are followed:
#   textures/entity/woolly_cow.png, _wool    bare pink skin, and a thick curly coat drawn puffed out over it
#   textures/entity/feathered_pig.png        a pink pig with a feathered back, wings and a comb (64x64)
#   textures/entity/lava_horse.png, _glow    basalt plates with ember cracks and a burning mane; _glow is what shines
#   textures/entity/wolf_fox.png             a wolf in a fox's coat
#   textures/block/soul_*_front*.png         furnace, smoker and blast furnace fronts in dark stone with blue soul fire
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-souls-mobs.ps1

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\entity'
$blocks = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\block'
New-Item -ItemType Directory -Force $out, $blocks | Out-Null

function Col([string]$hex) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb(255, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255)
}
function Save($bmp, [string]$name) { $bmp.Save((Join-Path $out "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png) }

# A repeatable pseudo-random number 0-99 for a pixel, so running the script again gives the same picture.
function Noise([int]$x, [int]$y) { (($x * 73 + $y * 151 + ($x * $y) * 7 + ($x -bxor $y) * 31) % 100) }

# Picks one colour per pixel from a list by noise: mostly the first, sometimes the later ones.
function Fur([string[]]$cols, [int]$x, [int]$y) {
    $n = Noise $x $y
    if ($n -lt 60) { return Col $cols[0] }
    if ($n -lt 85) { return Col $cols[1] }
    return Col $cols[$cols.Count - 1]
}

function Rect($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h, [string[]]$cols) {
    for ($y = $y0; $y -lt $y0 + $h; $y++) { for ($x = $x0; $x -lt $x0 + $w; $x++) { $bmp.SetPixel($x, $y, (Fur $cols $x $y)) } }
}

# Every part of a model is a box unwrapped from its corner (u, v) into six faces.
function Box($bmp, [int]$u, [int]$v, [int]$w, [int]$h, [int]$d, [string[]]$cols) {
    Rect $bmp ($u + $d) $v $w $d $cols
    Rect $bmp ($u + $d + $w) $v $w $d $cols
    Rect $bmp $u ($v + $d) $d $h $cols
    Rect $bmp ($u + $d) ($v + $d) $w $h $cols
    Rect $bmp ($u + $d + $w) ($v + $d) $d $h $cols
    Rect $bmp ($u + 2 * $d + $w) ($v + $d) $w $h $cols
}

function Paint($bmp, [int]$u, [int]$v, [string[]]$rows, [hashtable]$colours) {
    for ($y = 0; $y -lt $rows.Count; $y++) {
        for ($x = 0; $x -lt $rows[$y].Length; $x++) {
            $c = [string]$rows[$y][$x]
            if ($c -ne '.') { $bmp.SetPixel($u + $x, $v + $y, (Col $colours[$c])) }
        }
    }
}

# ---------- Woolly Cow: cream-and-rose skin, dark hooves, curved pale horns, a wide soft muzzle.
$skin = @('#EBC4B2', '#DDB09E', '#C99A88')
$cow = New-Object System.Drawing.Bitmap 64, 32
Box $cow 0 0 8 8 6 $skin
Box $cow 22 0 1 3 1 @('#F2E6C8', '#E2D2A8', '#C8B488')
Box $cow 18 4 12 18 10 $skin
Box $cow 52 0 4 6 1 @('#F0A8A0', '#E2908A', '#CC7C78')
Box $cow 0 16 4 12 4 $skin
Rect $cow 0 29 16 3 @('#3C2A24', '#32221D', '#281A16')
# Head, front (8x8 at 6,6).
Paint $cow 6 6 @('........', '........', 'wbwaawbw', '........', '.mmmmmm.', 'mmnmmnmm', 'mmmmmmmm', '.mmmmmm.') @{ w = '#F6F2EC'; b = '#1C1412'; a = '#D8AC9A'; m = '#F0B4A8'; n = '#7A4238' }
Save $cow 'woolly_cow'

# Its coat: cream curls, a darker ring around each.
$coat = New-Object System.Drawing.Bitmap 64, 32
for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 64; $x++) {
        $cx = ($x + 2 * ([Math]::Floor($y / 4) % 2)) % 4; $cy = $y % 4
        $c = if ($cx -eq 1 -and $cy -eq 1) { '#FFFDF4' } elseif ($cx -eq 3 -or $cy -eq 3) { '#D8CDB6' } else { '#F1E9D8' }
        $coat.SetPixel($x, $y, (Col $c))
    }
}
Save $coat 'woolly_cow_wool'

# ---------- Feathered Pig: a rosy head and legs, white feathers on back and sides, a red comb.
$pink = @('#F4B0B8', '#E89CA6', '#D88894')
$pig = New-Object System.Drawing.Bitmap 64, 64
Box $pig 0 0 8 8 8 $pink
Box $pig 16 16 4 3 1 @('#E07F8C', '#D07480', '#C26874')
Box $pig 28 8 10 16 8 $pink
Box $pig 0 16 4 6 4 $pink
Rect $pig 0 24 16 2 @('#7A4A44', '#6A3E3A', '#5A3430')
Paint $pig 8 8 @('........', '........', '.bw..wb.', '........', '........', '........', '........', '........') @{ b = '#1C1412'; w = '#F6F2EC' }
Paint $pig 17 17 @('nmmn', 'mmmm', 'mmmm') @{ n = '#7C3A44'; m = '#E07F8C' }
# Feathers: tips in staggered rows, each row one colour darker at the point.
function Feathers($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h) {
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $tip = ($y % 3) -eq 2 -and (($x + [Math]::Floor($y / 3)) % 2) -eq 0
            $bmp.SetPixel($x0 + $x, $y0 + $y, (Col $(if ($tip) { '#D9CBB4' } elseif (($y % 3) -eq 0) { '#FFFCF3' } else { '#F4ECDD' })))
        }
    }
}
Feathers $pig 28 16 8 16
Feathers $pig 46 16 8 16
Feathers $pig 54 16 10 16
# Wings (0,32) and comb (16,32).
Feathers $pig 0 32 14 10
Rect $pig 0 40 14 2 @('#CDB99E', '#BFAA90', '#B09C84')
Rect $pig 16 32 12 7 @('#D8322C', '#C42A26', '#A8201C')
Save $pig 'feathered_pig'

# ---------- Lava Horse: basalt plates. A crack runs along plate edges; where it runs the glow layer shines too.
$horse = New-Object System.Drawing.Bitmap 64, 64
$basalt = @('#2C2320', '#3A2F2A', '#1D1715')
Box $horse 0 32 10 10 22 $basalt   # body
Box $horse 0 35 4 12 7 $basalt     # neck
Box $horse 0 13 6 5 7 $basalt      # head
Box $horse 0 25 4 5 5 @('#3A2F2A', '#2C2320', '#1D1715')   # muzzle
Box $horse 19 16 2 3 1 $basalt     # ears
Box $horse 48 21 4 11 4 $basalt    # legs
Rect $horse 48 33 16 3 @('#141010', '#1D1715', '#0E0A0A')  # hooves
$fire = @('#FF8A1E', '#FFB02E', '#E0500E')
Box $horse 56 36 2 16 2 $fire      # mane
Box $horse 42 36 3 14 4 $fire      # tail
# Saddle, bridle and reins, for when it wears them: dark leather with brass.
$leather = @('#4A2C1A', '#3C2214', '#2E1A10')
Box $horse 26 0 10 9 9 $leather
Box $horse 1 1 6 5 6 $leather
Box $horse 19 0 4 5 2 $leather
Box $horse 29 5 1 2 2 @('#D8A840', '#C09030', '#A87C28')
$glow = New-Object System.Drawing.Bitmap 64, 64
# Plate edges: a wandering line every 5 pixels across and down, broken up by noise.
function IsCrack([int]$x, [int]$y) {
    $wx = $x + [Math]::Floor((Noise ([Math]::Floor($y / 3)) 7) / 34)
    $wy = $y + [Math]::Floor((Noise 11 ([Math]::Floor($x / 3))) / 34)
    (($wx % 6 -eq 0) -or ($wy % 6 -eq 0)) -and (Noise $x $y) -lt 62
}
for ($y = 13; $y -lt 64; $y++) {
    for ($x = 0; $x -lt 56; $x++) {
        if ($x -ge 42 -and $y -ge 36 -and $y -lt 54) { continue }   # the tail burns already
        if ($x -ge 48 -and $y -lt 36) { if ($y -ge 33) { continue } }   # not the hooves
        if ($horse.GetPixel($x, $y).A -eq 0 -or -not (IsCrack $x $y)) { continue }
        $ember = Col $(if ((Noise $y $x) -lt 25) { '#FFD04A' } else { '#FF7A1A' })
        $horse.SetPixel($x, $y, $ember)
        $glow.SetPixel($x, $y, $ember)
    }
}
foreach ($area in @(56, 36, 8, 18), @(42, 36, 14, 18)) {   # mane and tail shine whole
    for ($y = $area[1]; $y -lt $area[1] + $area[3]; $y++) {
        for ($x = $area[0]; $x -lt $area[0] + $area[2]; $x++) {
            $c = $horse.GetPixel($x, $y)
            if ($c.A -gt 0) { $glow.SetPixel($x, $y, $c) }
        }
    }
}
# Burning eyes on both sides of the head, next to its front.
foreach ($eye in @(5, 22), @(14, 22)) {
    $horse.SetPixel($eye[0], $eye[1], (Col '#FFE27A'))
    $glow.SetPixel($eye[0], $eye[1], (Col '#FFE27A'))
}
Save $horse 'lava_horse'
Save $glow 'lava_horse_glow'

# ---------- Wolf Fox: a wolf model in a fox's coat. Rust fur, cream throat, black socks, a white tail tip.
$rust = @('#D9772B', '#C96A24', '#B55C1E')
$creamy = @('#F2E6CF', '#E8DBC2', '#DCCEB2')
$wf = New-Object System.Drawing.Bitmap 64, 32
Box $wf 0 0 6 6 4 $rust     # head
Box $wf 16 14 2 2 1 @('#2A201C', '#1F1815', '#16120F')   # ears
Box $wf 18 14 6 9 6 $rust   # body
Box $wf 21 0 8 6 7 $rust    # shoulders
Box $wf 0 18 2 8 2 @('#2A201C', '#1F1815', '#16120F')    # legs: black socks
Rect $wf 0 20 8 5 $rust
Box $wf 9 18 2 8 2 $rust    # tail
Rect $wf 9 25 8 3 $creamy   # its tip
Rect $wf 24 20 6 9 $creamy  # belly (the body's front face)
$faceColours = @{ o = '#D9772B'; a = '#D9772B'; c = '#F2E6CF'; w = '#F6F2EC'; b = '#16120F'; d = '#3A241C' }
Paint $wf 4 4 @('oooooo', 'oooooo', 'wbaabw', 'oooooo', 'cooooc', 'cccccc') $faceColours   # head, front (6x6)
Paint $wf 4 14 @('cbc', 'cdc', 'ddd') $faceColours                                         # snout, front (3x3): nose, a line under it, the mouth
Paint $wf 4 10 @('ooo', 'ooo', 'ooo', 'ooo') $faceColours                                  # snout, top (3x4)
Paint $wf 7 10 @('ccc', 'ccc', 'ccc', 'ccc') $faceColours                                  # snout, bottom (3x4)
Paint $wf 0 14 @('oooo', 'cccc', 'cddd') $faceColours                                      # snout, one side (4x3): the mouth runs back from the front
Paint $wf 7 14 @('oooo', 'cccc', 'dddc') $faceColours                                      # snout, other side (4x3)
Save $wf 'wolf_fox'

# ---------- Furnace fronts: dark coursed stone. Each machine has its own face; lit ones animate their blue fire.
function Stone($bmp, [int]$o) {
    for ($y = 0; $y -lt 16; $y++) {
        for ($x = 0; $x -lt 16; $x++) {
            $row = [Math]::Floor($y / 4)
            $joint = ($y % 4 -eq 3) -or ((($x + 4 * ($row % 2)) % 8) -eq 7)
            $c = if ($joint) { '#17141C' } else { @('#322C3A', '#2A2531', '#3A3443')[(Noise $x $y) % 3] }
            $bmp.SetPixel($x, $o + $y, (Col $c))
        }
    }
}
# Fire over a dark hollow, frame $f of $n; the columns rise and fall in turn.
function Fire($bmp, [int]$x0, [int]$y0, [int]$w, [int]$h, [int]$o, [int]$f, [int]$n, [bool]$lit) {
    for ($y = 0; $y -lt $h; $y++) {
        for ($x = 0; $x -lt $w; $x++) {
            $c = '#08070C'
            if ($lit) {
                $top = 1 + [Math]::Round(($h - 2) * (0.5 + 0.5 * [Math]::Sin(2 * [Math]::PI * ($x / 3.0 + $f / $n))) * 0.7)
                if ($y -ge $top) {
                    $depth = ($y - $top) / [Math]::Max(1, $h - 1 - $top)
                    $c = if ($depth -gt 0.75) { '#D8FDFF' } elseif ($depth -gt 0.45) { '#46E2F2' } elseif ($depth -gt 0.2) { '#14A8BC' } else { '#0B6470' }
                }
            } elseif ($y -eq $h - 1) { $c = '#1A3238' }
            $bmp.SetPixel($x0 + $x, $o + $y0 + $y, (Col $c))
        }
    }
}
function Front([string]$name, [int]$frames, [scriptblock]$face, [bool]$lit) {
    $bmp = New-Object System.Drawing.Bitmap 16, (16 * $frames)
    for ($f = 0; $f -lt $frames; $f++) { & $face $bmp ($f * 16) $f $frames $lit }
    $bmp.Save((Join-Path $blocks "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    $meta = Join-Path $blocks "$name.png.mcmeta"
    if ($frames -gt 1) { [System.IO.File]::WriteAllText($meta, '{"animation": {"frametime": 3, "interpolate": true}}') }
    elseif (Test-Path $meta) { Remove-Item $meta }
}
# Furnace: a pointed arch with a sill.
$furnaceFace = {
    param($bmp, $o, $f, $n, $lit)
    Stone $bmp $o
    Fire $bmp 4 7 8 6 $o $f $n $lit
    Paint $bmp 3 ($o + 4) @('....kk....', '...kffk...', '..kf..fk..', '.kf....fk.', '.f......f.', '.f......f.', '.f......f.', '.f......f.', '.f......f.') @{ k = '#17141C'; f = '#5A5268' }
    Paint $bmp 2 ($o + 13) @('ffffffffffff', 'kkkkkkkkkkkk') @{ k = '#17141C'; f = '#5A5268' }
}
# Smoker: a wide low mouth between dark timber beams, with a vent above.
$smokerFace = {
    param($bmp, $o, $f, $n, $lit)
    Stone $bmp $o
    Fire $bmp 3 8 10 4 $o $f $n $lit
    Paint $bmp 0 ($o + 1) @('wwwwwwwwwwwwwwww', 'xxxxxxxxxxxxxxxx') @{ w = '#5A3A24'; x = '#3C2616' }
    Paint $bmp 0 ($o + 12) @('wwwwwwwwwwwwwwww', 'xxxxxxxxxxxxxxxx') @{ w = '#5A3A24'; x = '#3C2616' }
    Paint $bmp 4 ($o + 5) @('k.k.k.k.k', 'k.k.k.k.k') @{ k = '#08070C' }
}
# Blast furnace: an iron-strapped front with a barred grate.
$blastFace = {
    param($bmp, $o, $f, $n, $lit)
    Stone $bmp $o
    Fire $bmp 3 5 10 7 $o $f $n $lit
    foreach ($bx in 5, 8, 11) { for ($y = 5; $y -lt 12; $y++) { $bmp.SetPixel($bx, $o + $y, (Col '#6A6474')) } }
    Paint $bmp 0 ($o + 3) @('iiiiiiiiiiiiiiii', 'dddddddddddddddd') @{ i = '#6A6474'; d = '#3C3844' }
    Paint $bmp 0 ($o + 12) @('iiiiiiiiiiiiiiii', 'dddddddddddddddd') @{ i = '#6A6474'; d = '#3C3844' }
    foreach ($rx in 1, 14) { $bmp.SetPixel($rx, $o + 3, (Col '#A8A0B4')); $bmp.SetPixel($rx, $o + 12, (Col '#A8A0B4')) }
}
Front 'soul_furnace_front' 1 $furnaceFace $false
Front 'soul_furnace_front_on' 4 $furnaceFace $true
Front 'soul_smoker_front' 1 $smokerFace $false
Front 'soul_smoker_front_on' 4 $smokerFace $true
Front 'soul_blast_furnace_front' 1 $blastFace $false
Front 'soul_blast_furnace_front_on' 4 $blastFace $true

Write-Host "Textures written to $out and $blocks"
