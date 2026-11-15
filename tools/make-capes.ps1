# Draws the ready-made DV Skin capes (64x32, vanilla cape layout) with no external art.
#   dvskin/src/main/resources/assets/dvskin/textures/capes/<name>.png     24 capes (8 DV themed, 16 dye colours)
#   dvskin/src/main/resources/assets/dvskin/lang/capes_en_us.txt, capes_tr_tr.txt   dvskin.cape.<name>=<Name>
#   AGENTS/ekran/capes-onizleme.png                                        preview, outer faces 8x (not in git)
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-capes.ps1
#
# Layout (checked against PlayerModel.createMesh and ElytraModel.createLayer, MC 1.20.1):
#   cape   = box 10x16x1 at texOffs(0,0):  top (1,0)  bottom (11,0)  sides (0,1)/(11,1)  OUTER (1,1)  inner (12,1)
#   elytra = box 10x20x2 at texOffs(22,0): the visible wing face is the BACK face (36,2), wing-shaped (stepped right edge, cut lower-left corner);
# The two regions do not overlap, so one 64x32 image serves both. The outer face is drawn once per size
# the same design is drawn on both (10x16 cape, 10x20 wing); inner faces and edges are plain.

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$capeDir = Join-Path $root 'dvskin\src\main\resources\assets\dvskin\textures\capes'
$langDir = Join-Path $root 'dvskin\src\main\resources\assets\dvskin\lang'
$shotDir = Join-Path $root 'AGENTS\ekran'
foreach ($d in $capeDir, $langDir, $shotDir) { New-Item -ItemType Directory -Force $d | Out-Null }

# Which elytra pixels the game itself draws (alpha > 0), read from Minecraft's client jar in the Gradle cache
Add-Type -AssemblyName System.IO.Compression.FileSystem
$mcJar = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($mcJar)
$stream = $zip.GetEntry('assets/minecraft/textures/entity/elytra.png').Open()
$vanillaElytra = New-Object System.Drawing.Bitmap $stream
$stream.Dispose(); $zip.Dispose()
$script:ElytraMask = New-Object 'bool[,]' 64, 32
for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 64; $x++) { $script:ElytraMask[$x, $y] = $vanillaElytra.GetPixel($x, $y).A -gt 0 } }

# ---------- colour helpers (ARGB ints)
function Col([string]$hex) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb(255, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255).ToArgb()
}
function ToInt([uint32]$u) { [BitConverter]::ToInt32([BitConverter]::GetBytes($u), 0) }
function Lum([int]$c) { ((($c -shr 16) -band 255) * 0.299 + (($c -shr 8) -band 255) * 0.587 + ($c -band 255) * 0.114) / 255 }
$BLACK = Col '#000000'; $WHITE = Col '#FFFFFF'
function MixC([int]$a, [int]$b, [double]$t) {
    $r = [uint32]4278190080
    foreach ($s in 16, 8, 0) {
        $x = ($a -shr $s) -band 255; $y = ($b -shr $s) -band 255
        $r = $r -bor ([uint32][Math]::Round($x + ($y - $x) * $t) -shl $s)
    }
    ToInt $r
}

# ---------- a face = int[w,h] being drawn; Px / Spr write into $script:F
$script:F = $null; $script:FW = 0; $script:FH = 0
function Px([int]$x, [int]$y, [int]$c) { if ($x -ge 0 -and $y -ge 0 -and $x -lt $script:FW -and $y -lt $script:FH) { $script:F[$x, $y] = $c } }
function Box([int]$x, [int]$y, [int]$w, [int]$h, [int]$c) { for ($j = 0; $j -lt $h; $j++) { for ($i = 0; $i -lt $w; $i++) { Px ($x + $i) ($y + $j) $c } } }
# rows of characters; '.' is transparent, other characters index $pal
function Spr([string[]]$rows, [hashtable]$pal, [int]$x0, [int]$y0) {
    for ($j = 0; $j -lt $rows.Count; $j++) {
        for ($i = 0; $i -lt $rows[$j].Length; $i++) {
            $ch = [string]$rows[$j][$i]
            if ($ch -ne '.') { Px ($x0 + $i) ($y0 + $j) $pal[$ch] }
        }
    }
}
function SprC([string[]]$rows, [hashtable]$pal, [int]$dy) {   # horizontally centred, $dy rows from the vertical centre
    $x0 = [int][Math]::Floor(($script:FW - $rows[0].Length) / 2)
    $y0 = [int][Math]::Floor(($script:FH - $rows.Count) / 2) + $dy
    Spr $rows $pal $x0 $y0
}
# Flat-ish body: base colour with a soft vertical shade, 1px darker border all round.
function New-Face([int]$w, [int]$h, [int]$base, [int]$border) {
    $script:FW = $w; $script:FH = $h; $script:F = New-Object 'int[,]' $w, $h
    for ($y = 0; $y -lt $h; $y++) {
        $c = MixC $base $BLACK (0.22 * $y / ($h - 1))
        for ($x = 0; $x -lt $w; $x++) { $script:F[$x, $y] = $c }
    }
    for ($x = 0; $x -lt $w; $x++) { $script:F[$x, 0] = $border; $script:F[$x, ($h - 1)] = $border }
    for ($y = 0; $y -lt $h; $y++) { $script:F[0, $y] = $border; $script:F[($w - 1), $y] = $border }
}
function Hash([int]$x, [int]$y) { (($x * 73 + $y * 151 + ($x * $y) * 31 + 17) % 11) }

# ---------- the capes: name -> { base; border; edge/inner colour; deco block }
$themes = [ordered]@{}

$themes['souls'] = @{ base = '#1B2A4E'; border = '#0E1630'; deco = {
    param($w, $h)
    $p = @{ c = (Col '#7FEAF7'); e = (Col '#0E1630'); g = (Col '#3A66B8'); s = (Col '#4F7BD0') }
    SprC @('.cccc.', 'cccccc', 'ceccec', 'cccccc', 'cccccc', 'c.cc.c') $p -1
    # blue flames standing along the hem: dark base, bright middle, pale tip
    $heights = @(2, 4, 3, 4, 2, 4, 3, 2)
    for ($i = 0; $i -lt $heights.Count; $i++) {
        $hh = $heights[$i]
        for ($k = 0; $k -lt $hh; $k++) {
            $col = if ($k -eq $hh - 1) { Col '#C4F4FF' } elseif ($k -ge 1) { Col '#4FB4F0' } else { $p.g }
            Px (1 + $i) ($h - 2 - $k) $col
        }
    }
} }

$themes['map'] = @{ base = '#D9C28F'; border = '#8B6A3A'; deco = {
    param($w, $h)
    $p = @{ r = (Col '#C0392B'); l = (Col '#BFA46E'); d = (Col '#8B6A3A'); g = (Col '#7CA24A') }
    for ($y = 2; $y -lt $h - 1; $y += 1) { for ($x = 1; $x -lt $w - 1; $x++) { if (($x % 3 -eq 0) -xor ($y % 3 -eq 0)) { if (($x + $y) % 2 -eq 0) { Px $x $y $p.l } } } }
    Spr @('ggg', 'ggg') $p 1 ($h - 4)                                     # a green patch
    Spr @('r.r', '.r.', 'r.r') $p ($w - 5) 2                              # the X
    $path = @(@(2, ($h - 6)), @(3, ($h - 7)), @(4, ($h - 8)), @(5, ($h - 9)))
    foreach ($q in $path) { Px $q[0] $q[1] $p.d }
    Px ($w - 3) ($h - 3) $p.d; Px ($w - 4) ($h - 3) $p.d
} }

$themes['team'] = @{ base = '#A82727'; border = '#5E1212'; deco = {
    param($w, $h)
    # the DV Team logo: shield, gold cross, darker right half, pale core
    $p = @{ g = (Col '#E8B93C'); r = (Col '#E04040'); d = (Col '#8E1C1C'); w = (Col '#FFF1C1') }
    SprC @('gggggggg', 'grrggddg', 'grrggddg', 'gggwwggg', 'gggwwggg', 'grrggddg', '.grggdg.', '..gggg..', '...gg...') $p 0
} }

$themes['glass'] = @{ base = '#A8D8E4'; border = '#E4F7F9'; deco = {
    param($w, $h)
    # like vanilla glass: pale frame, clear blue pane, evenly spaced diagonal glints of equal length
    $light = Col '#F4FFFF'
    $starts = @(@(2, 5), @(5, 8))
    if ($h -gt 16) { $starts += @(@(2, 14), @(5, 17)) }
    foreach ($s in $starts) { for ($k = 0; $k -lt 4; $k++) { Px ($s[0] + $k) ($s[1] - $k) $light } }
} }

$themes['music'] = @{ base = '#3E2A6E'; border = '#241540'; deco = {
    param($w, $h)
    $p = @{ n = (Col '#D9B8FF'); s = (Col '#7A5CC4') }
    SprC @('.nnnnnn.', '.n....n.', '.n....n.', '.n....n.', 'nn...nn.', 'nn...nn.') $p -1
    foreach ($q in @(@(2, 2), @(($w - 3), 2), @(3, ($h - 3)), @(($w - 3), ($h - 4)))) { Px $q[0] $q[1] $p.s }
} }

$themes['vein'] = @{ base = '#6E7076'; border = '#3A3B40'; deco = {
    param($w, $h)
    $tones = @((Col '#6E7076'), (Col '#63656B'), (Col '#7A7C83'), (Col '#585A60'))
    for ($y = 1; $y -lt $h - 1; $y++) { for ($x = 1; $x -lt $w - 1; $x++) {
        $t = Mix2 $tones[(Hash $x $y) % 4] (Col '#000000') (0.22 * $y / ($h - 1)); Px $x $y $t } }
    $ore = Col '#5EE6D8'; $hi = Col '#C9FFF8'; $dk = Col '#2A9C92'
    for ($y = 2; $y -lt $h - 2; $y++) {
        $x = [int][Math]::Round($w / 2 - 0.5 + 2 * [Math]::Sin($y * 0.8))
        Px $x $y $ore; Px ($x + 1) $y $ore
        if ($y % 4 -eq 1) { Px ($x - 1) $y $dk; Px ($x + 2) $y $dk }
        if ($y % 5 -eq 2) { Px $x $y $hi }
    }
} }

$themes['backpack'] = @{ base = '#8A5530'; border = '#4E2D17'; deco = {
    param($w, $h)
    $p = @{ s = (Col '#5A3419'); t = (Col '#B9814D'); f = (Col '#A0622F'); b = (Col '#E0E0E8') }
    Box 1 1 ($w - 2) 3 $p.f                                               # lid
    Box 2 4 2 ($h - 5) $p.s; Box ($w - 4) 4 2 ($h - 5) $p.s              # shoulder straps
    Box 1 ([int]($h / 2)) ($w - 2) 2 $p.s                                 # cross strap
    for ($y = 5; $y -lt $h - 1; $y += 2) { Px 2 $y $p.t; Px ($w - 3) $y $p.t }
    Spr @('bbbb', 'b..b', 'bbbb') $p ([int][Math]::Floor(($w - 4) / 2)) ([int]($h / 2) - 1)
} }

$themes['dv'] = @{ base = '#14111C'; border = '#FF9A2E'; deco = {
    param($w, $h)
    $o = Col '#FF9A2E'; $sh = Col '#7A3E0A'
    $D = @('###.', '#..#', '#..#', '#..#', '#..#', '###.'); $V = @('#..#', '#..#', '#..#', '#..#', '.##.', '.##.')
    $x0 = [int][Math]::Floor(($w - 9) / 2); $y0 = [int][Math]::Floor(($h - 6) / 2) - 2
    if ($h -gt 16) { $D = @('....', '....', '....', '....', '....', '....'); $x0 = -2; $y0 = 8 }   # wing: only the V, it is mirrored on the other wing and the rim is dark
    foreach ($pass in 0) {
        $c = if ($pass) { $sh } else { $o }; $off = $pass
        foreach ($set in @(@($D, 0), @($V, 5))) {
            for ($j = 0; $j -lt 6; $j++) { for ($i = 0; $i -lt 4; $i++) { if ($set[0][$j][$i] -eq '#') { Px ($x0 + $set[1] + $i + $off) ($y0 + $j + $off) $c } } }
        }
    }
    for ($x = 2; $x -lt $w - 2; $x++) { Px $x ($h - 4) $o }
    Px 1 ($h - 4) $sh; Px ($w - 2) ($h - 4) $sh
} }

# 16 plain dye capes with a tiny DV mark on the back
$dyes = [ordered]@{
    white = 'F9FFFE'; orange = 'F9801D'; magenta = 'C74EBD'; light_blue = '3AB3DA'; yellow = 'FED83D'; lime = '80C71F'
    pink = 'F38BAA'; gray = '474F52'; light_gray = '9D9D97'; cyan = '169C9C'; purple = '8932B8'; blue = '3C44AA'
    brown = '835432'; green = '5E7C16'; red = 'B02E26'; black = '1D1D21'
}
$colorDeco = {
    param($w, $h)
    $my = if ($h -gt 16) { 9 } else { 3 }   # the wing is narrower at the top: start lower
    $base = $script:F[([int]($w / 2)), 5]
    $mark = if ((Lum $base) -lt 0.5) { MixC $base $WHITE 0.45 } else { MixC $base $BLACK 0.40 }
    $D = @('##.', '#.#', '#.#', '#.#', '##.'); $V = @('#.#', '#.#', '#.#', '#.#', '.#.')
    for ($j = 0; $j -lt 5; $j++) { for ($i = 0; $i -lt 3; $i++) {
        if ($D[$j][$i] -eq '#') { Px (1 + $i + 1) ($my + $j) $mark }
        if ($V[$j][$i] -eq '#') { Px (1 + 4 + $i + 1) ($my + $j) $mark } } }
}
foreach ($k in $dyes.Keys) {
    $b = Col ('#' + $dyes[$k])
    $themes["color_$k"] = @{ base = ('#' + $dyes[$k]); border = ('{0:X6}' -f ((MixC $b $BLACK 0.28) -band 0xFFFFFF)); deco = $colorDeco }
}
function Mix2([int]$a, [int]$b, [double]$t) { MixC $a $b $t }

# ---------- compose one 64x32 texture
function Blit($dst, $face, [int]$ox, [int]$oy) {
    $w = $face.GetLength(0); $h = $face.GetLength(1)
    for ($y = 0; $y -lt $h; $y++) { for ($x = 0; $x -lt $w; $x++) { $dst[($ox + $x), ($oy + $y)] = $face[$x, $y] } }
}
function Fill($dst, [int]$x, [int]$y, [int]$w, [int]$h, [int]$c) { for ($j = 0; $j -lt $h; $j++) { for ($i = 0; $i -lt $w; $i++) { $dst[($x + $i), ($y + $j)] = $c } } }

function Build([string]$name, $t) {
    $base = Col $t.base; $border = Col $t.border
    $tex = New-Object 'int[,]' 64, 32            # zero = fully transparent
    $inner = MixC $base $BLACK 0.35
    $edge = MixC $base $BLACK 0.30
    # cape
    New-Face 10 16 $base $border; & $t.deco 10 16; $capeFace = $script:F
    Blit $tex $capeFace 1 1
    Fill $tex 12 1 10 16 $inner
    Fill $tex 1 0 10 1 $edge; Fill $tex 11 0 10 1 $edge; Fill $tex 0 1 1 16 $edge; Fill $tex 11 1 1 16 $edge
    # elytra: the wing's visible face is the box's BACK face (36,2); only the wing-shaped part is used
    # (stepped right edge, cut lower-left corner). Every other elytra face is one flat dark tone (template: red/green/blue strips).
    $right = @(5, 6, 7, 7, 7, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9)
    $left = @(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 2, 3)
    $wEdge = MixC $base $BLACK 0.45   # flat dark theme tone for the rim and every thin face: keeps the wing looking thin
    New-Face 10 20 $base $wEdge; & $t.deco 10 20; $wing = $script:F
    for ($fy = 0; $fy -lt 20; $fy++) {
        for ($fx = 0; $fx -lt 10; $fx++) {
            $in = ($fx -ge $left[$fy]) -and ($fx -le $right[$fy])
            if (-not $in) { continue }
            $isEdge = ($fy -eq 0) -or ($fy -eq 19) -or ($fx -eq $left[$fy]) -or ($fx -eq $right[$fy]) -or ($fx -le $right[$fy - 1] -eq $false) -or ($fx -lt $left[$fy - 1]) -or ($fx -lt $left[$fy + 1]) -or ($fx -gt $right[$fy + 1])
            $tex[(36 + $fx), (2 + $fy)] = $(if ($isEdge) { $wEdge } else { $wing[$fx, $fy] })
        }
    }
    # The game's own elytra is see-through everywhere but the wing's outer face and a few rim pixels; a solid inner
    # face and rims make the wing look like a thick block. Use the vanilla elytra's alpha as the mask: transparent
    # where it is, our wing (or the flat rim tone) where it isn't.
    for ($y = 0; $y -lt 22; $y++) { for ($x = 22; $x -lt 46; $x++) {
        if ($script:ElytraMask[$x, $y]) { if ($tex[$x, $y] -eq 0) { $tex[$x, $y] = $wEdge } } else { $tex[$x, $y] = 0 }
    } }
    $bmp = New-Object System.Drawing.Bitmap(64, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 64; $x++) {
        $v = $tex[$x, $y]
        $bmp.SetPixel($x, $y, $(if ($v -eq 0) { [System.Drawing.Color]::FromArgb(0, 0, 0, 0) } else { [System.Drawing.Color]::FromArgb($v) }))
    } }
    $bmp.Save((Join-Path $capeDir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp
}

$bitmaps = [ordered]@{}
foreach ($k in $themes.Keys) { $bitmaps[$k] = Build $k $themes[$k] }

# ---------- preview: the 24 outer cape faces, 8x, side by side on a dark background
$cell = 80; $gap = 8
$prev = New-Object System.Drawing.Bitmap(($bitmaps.Count * ($cell + $gap) + $gap), (16 * 8 + 2 * $gap))
$g = [System.Drawing.Graphics]::FromImage($prev)
$g.Clear([System.Drawing.Color]::FromArgb(255, 40, 40, 46)); $g.InterpolationMode = 'NearestNeighbor'; $g.PixelOffsetMode = 'Half'
$i = 0
foreach ($k in $bitmaps.Keys) {
    $g.DrawImage($bitmaps[$k], (New-Object System.Drawing.Rectangle(($gap + $i * ($cell + $gap)), $gap, $cell, 128)), (New-Object System.Drawing.Rectangle(1, 1, 10, 16)), [System.Drawing.GraphicsUnit]::Pixel)
    $i++
}
$prev.Save((Join-Path $shotDir 'capes-onizleme.png'), [System.Drawing.Imaging.ImageFormat]::Png)

# ---------- language lists (UTF-8, no BOM; lang JSONs are written by the mod code)
function U([string]$s) { [regex]::Unescape($s) }
$en = [ordered]@{
    souls = 'Souls'; map = 'Map'; team = 'Team'; glass = 'Glass'; music = 'Music'; vein = 'Vein'; backpack = 'Backpack'; dv = 'DV'
    color_white = 'White'; color_orange = 'Orange'; color_magenta = 'Magenta'; color_light_blue = 'Light Blue'; color_yellow = 'Yellow'; color_lime = 'Lime'
    color_pink = 'Pink'; color_gray = 'Gray'; color_light_gray = 'Light Gray'; color_cyan = 'Cyan'; color_purple = 'Purple'; color_blue = 'Blue'
    color_brown = 'Brown'; color_green = 'Green'; color_red = 'Red'; color_black = 'Black'
}
$tr = [ordered]@{
    souls = (U 'Ruhlar'); map = 'Harita'; team = (U 'Takım'); glass = 'Cam'; music = (U 'Müzik'); vein = 'Damar'; backpack = (U 'Sırt Çantası'); dv = 'DV'
    color_white = 'Beyaz'; color_orange = 'Turuncu'; color_magenta = (U 'Eflatun'); color_light_blue = (U 'Açık Mavi'); color_yellow = (U 'Sarı'); color_lime = (U 'Limon Yeşili')
    color_pink = 'Pembe'; color_gray = 'Gri'; color_light_gray = (U 'Açık Gri'); color_cyan = (U 'Camgöbeği'); color_purple = 'Mor'; color_blue = 'Mavi'
    color_brown = 'Kahverengi'; color_green = (U 'Yeşil'); color_red = (U 'Kırmızı'); color_black = 'Siyah'
}
$utf8 = New-Object System.Text.UTF8Encoding($false)
foreach ($pair in @(@('en_us', $en), @('tr_tr', $tr))) {
    $lines = foreach ($k in $themes.Keys) { "dvskin.cape.$k=$($pair[1][$k])" }
    [System.IO.File]::WriteAllText((Join-Path $langDir "capes_$($pair[0]).txt"), (($lines -join "`n") + "`n"), $utf8)
}
"wrote $($bitmaps.Count) capes"
