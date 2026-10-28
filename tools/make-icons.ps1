# Draws the DV mod icons as 32x32 pixel art (no external art), then scales them up crisp.
#   art/icons/<mod>.png                       512x512, for Modrinth / CurseForge
#   <mod>/src/main/resources/<mod>_logo.png   128x128, shown in the Mods list (logoFile in mods.toml)
#   art/icons/sheet.png                       all icons side by side, for review
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-icons.ps1
#
# Each shape is a predicate over pixel centres. Part() paints a shape with a 1px dark outline
# around it, so later parts sit on earlier ones with a clean border, like Minecraft item sprites.

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'art\icons'
New-Item -ItemType Directory -Force $out | Out-Null

$N = 32
$script:grid = $null

function Col([string]$hex) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb(255, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255).ToArgb()
}
$OUTLINE = Col '#120F1C'

function Set-Px([int]$x, [int]$y, [int]$c) { if ($x -ge 0 -and $y -ge 0 -and $x -lt $N -and $y -lt $N) { $script:grid[$x, $y] = $c } }

function Mask([scriptblock]$shape) {
    $m = New-Object 'bool[,]' $N, $N
    for ($y = 0; $y -lt $N; $y++) { for ($x = 0; $x -lt $N; $x++) { $m[$x, $y] = [bool](& $shape ($x + 0.5) ($y + 0.5)) } }
    , $m
}

function Paint($m, [int]$c) {
    for ($y = 0; $y -lt $N; $y++) { for ($x = 0; $x -lt $N; $x++) { if ($m[$x, $y]) { $script:grid[$x, $y] = $c } } }
}

# 1px outline on the 4 sides of the shape, then the shape itself.
function Part([scriptblock]$shape, [string]$hex) {
    $m = Mask $shape
    for ($y = 0; $y -lt $N; $y++) {
        for ($x = 0; $x -lt $N; $x++) {
            if ($m[$x, $y]) { continue }
            foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                $nx = $x + $d[0]; $ny = $y + $d[1]
                if ($nx -ge 0 -and $ny -ge 0 -and $nx -lt $N -and $ny -lt $N -and $m[$nx, $ny]) { $script:grid[$x, $y] = $OUTLINE; break }
            }
        }
    }
    Paint $m (Col $hex)
}

# Shapes (all take pixel-centre coordinates)
function RectS([float]$x, [float]$y, [float]$w, [float]$h) { { param($px, $py) $px -ge $x -and $px -lt $x + $w -and $py -ge $y -and $py -lt $y + $h }.GetNewClosure() }
function Disc([float]$cx, [float]$cy, [float]$r) { { param($px, $py) ($px - $cx) * ($px - $cx) + ($py - $cy) * ($py - $cy) -le $r * $r }.GetNewClosure() }
function SegDist([float]$px, [float]$py, [float]$x0, [float]$y0, [float]$x1, [float]$y1) {
    $dx = $x1 - $x0; $dy = $y1 - $y0
    # 0.0 / 1.0, not 0 / 1: with int literals PowerShell picks Math.Max(int, int) and rounds t away
    $t = [Math]::Max(0.0, [Math]::Min(1.0, [double]((($px - $x0) * $dx + ($py - $y0) * $dy) / ($dx * $dx + $dy * $dy))))
    [Math]::Sqrt([Math]::Pow($px - ($x0 + $t * $dx), 2) + [Math]::Pow($py - ($y0 + $t * $dy), 2))
}
function Seg([float]$x0, [float]$y0, [float]$x1, [float]$y1, [float]$r) { { param($px, $py) (SegDist $px $py $x0 $y0 $x1 $y1) -le $r }.GetNewClosure() }
function Poly([float[]]$pts) {
    {
        param($px, $py)
        $inside = $false; $n = $pts.Count / 2
        for ($i = 0; $i -lt $n; $i++) {
            $j = ($i + $n - 1) % $n
            $xi = $pts[2 * $i]; $yi = $pts[2 * $i + 1]; $xj = $pts[2 * $j]; $yj = $pts[2 * $j + 1]
            if ((($yi -gt $py) -ne ($yj -gt $py)) -and ($px -lt ($xj - $xi) * ($py - $yi) / ($yj - $yi) + $xi)) { $inside = -not $inside }
        }
        $inside
    }.GetNewClosure()
}
# thick quadratic curve, sampled as a polyline
function Curve([float]$x0, [float]$y0, [float]$cx, [float]$cy, [float]$x1, [float]$y1, [float]$r) {
    $pts = for ($i = 0; $i -le 16; $i++) { $t = $i / 16; ,@(((1 - $t) * (1 - $t) * $x0 + 2 * (1 - $t) * $t * $cx + $t * $t * $x1), ((1 - $t) * (1 - $t) * $y0 + 2 * (1 - $t) * $t * $cy + $t * $t * $y1)) }
    {
        param($px, $py)
        for ($i = 0; $i -lt $pts.Count - 1; $i++) { if ((SegDist $px $py $pts[$i][0] $pts[$i][1] $pts[$i + 1][0] $pts[$i + 1][1]) -le $r) { return $true } }
        $false
    }.GetNewClosure()
}

# Rounded dark tile with a 1px accent border.
function New-Icon([string]$accent) {
    $script:grid = New-Object 'int[,]' $N, $N
    # rounded square: distance to the inner 24x24 core; the border is the outer 1px ring
    $tile = { param($px, $py) $cx = [Math]::Max(4, [Math]::Min(28, $px)); $cy = [Math]::Max(4, [Math]::Min(28, $py)); ($px - $cx) * ($px - $cx) + ($py - $cy) * ($py - $cy) -le 16 }
    $inner = { param($px, $py) $cx = [Math]::Max(4, [Math]::Min(28, $px)); $cy = [Math]::Max(4, [Math]::Min(28, $py)); ($px - $cx) * ($px - $cx) + ($py - $cy) * ($py - $cy) -le 9 }
    Paint (Mask $tile) (Col $accent)
    Paint (Mask $inner) (Col '#221E33')
    Paint (Mask { param($px, $py) $py -lt 3 -and (& $inner $px $py) }) (Col '#2C2742') # soft top light
}

$icons = [ordered]@{}
function Save-Icon([string]$id) {
    $bmp = New-Object System.Drawing.Bitmap($N, $N)
    $grid = $script:grid
    for ($y = 0; $y -lt $N; $y++) {
        for ($x = 0; $x -lt $N; $x++) {
            $argb = $grid[$x, $y] # read first: a comma inside a method call's arguments would split them
            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($argb))
        }
    }
    $icons[$id] = $bmp
}

# ---------- DV Lib: CRT monitor with an upright ":3" cat face, on a case made of books
New-Icon '#9B6BFF'
Part (RectS 4 25 24 2) '#A0682F'                                  # shelf
$books = @(@(6, 19, '#E5534B'), @(10, 20, '#4FC3F7'), @(14, 19, '#FFD54F'), @(18, 21, '#81C784'), @(22, 20, '#9B6BFF'))
foreach ($b in $books) { Part (RectS $b[0] $b[1] 3 (24 - $b[1])) $b[2]; Set-Px ($b[0] + 1) ($b[1] + 1) (Col '#FFFFFF') }
Part (RectS 14 17 4 2) '#BDB5A4'                                  # neck
Part (RectS 5 4 22 12) '#D9D2C2'                                  # monitor body
Part (RectS 7 6 18 8) '#9B6BFF'                                   # screen
Set-Px 6 14 (Col '#7EE787')                                       # power light
$white = Col '#FFFFFF'
# Face is mirror-symmetric around x = 16 (between columns 15 and 16).
foreach ($p in @(@(13, 7), @(14, 7), @(13, 8), @(14, 8), @(17, 7), @(18, 7), @(17, 8), @(18, 8))) { Set-Px $p[0] $p[1] $white } # eyes: the ":" dots
# mouth "ω" (the "3" turned upright): three tips, two cups under the eyes
foreach ($p in @(@(12, 10), @(15, 10), @(16, 10), @(19, 10), @(13, 11), @(14, 11), @(17, 11), @(18, 11))) { Set-Px $p[0] $p[1] $white }
Save-Icon 'dvlib'

# ---------- DV Zoom: telescope (spyglass)
New-Icon '#4FC3F7'
Part (Seg 6 26 15 17 1.6) '#8B5A2B'                               # eyepiece end
Part (Seg 13 19 21 11 2.3) '#C8963E'                              # tube
Part (Seg 20 12 25 7 3.0) '#E2B65A'                               # wide front
Part (Disc 25.5 6.5 1.6) '#7FDBFF'                                # lens
foreach ($p in @(@(14, 17), @(15, 18), @(19, 12), @(20, 13))) { Set-Px $p[0] $p[1] (Col '#7A4E1F') }  # rings
Set-Px 22 9 (Col '#FFF1C1'); Set-Px 23 8 (Col '#FFF1C1')          # shine
Save-Icon 'dvzoom'

# ---------- DV Food: drumstick
New-Icon '#FFB74D'
Part (Seg 17 17 23 23 1.3) '#F2E8D5'                              # bone
Part (Disc 24.5 22 1.8) '#F2E8D5'; Part (Disc 22 24.5 1.8) '#F2E8D5'
Part (Disc 13 13 7.5) '#B5562A'                                   # meat
Paint (Mask (Disc 11.5 11.5 4.5)) (Col '#D9773E')
Paint (Mask (Disc 10 10 1.6)) (Col '#F0A060')
Save-Icon 'dvfood'

# ---------- DV Lens: eye
New-Icon '#2EC4B6'
Part { param($px, $py) $t = ($px - 16) / 12; [Math]::Abs($py - 16) -le 7 * (1 - $t * $t) } '#F4F7FB'
Paint (Mask (Disc 16 16 4.6)) (Col '#2EC4B6')
Paint (Mask (Disc 16 16 2.2)) (Col '#120F1C')
Set-Px 17 14 $white; Set-Px 18 14 $white
Save-Icon 'dvlens'

# ---------- DV Vein: crossed pickaxe and hoe (layout from the user's reference picture)
# Thick handles crossing in the middle and reaching the bottom corners; the pickaxe head a big arc over
# the top right, the hoe blade coming down the left side from its handle top.
New-Icon '#90A4AE'
$wood = '#8B5A2B'; $woodLight = '#B07A3E'; $metal = '#C3CDD8'
# The two handles are exact mirror images (x -> 32 - x); each lit edge runs parallel, 0.55px off centre.
Part (Seg 9 9 25 25 1.3) $wood                                      # hoe handle
Paint (Mask (Seg 9.4 8.6 25.4 24.6 0.4)) (Col $woodLight)           # its lit edge
Part (Curve 9.5 7.5 3.5 7.5 4.5 17.5 1.4) $metal                    # hoe blade, down the left side
Part (Seg 23 9 7 25 1.3) $wood                                      # pickaxe handle
Paint (Mask (Seg 22.6 8.6 6.6 24.6 0.4)) (Col $woodLight)           # its lit edge
Part (Curve 15.5 5.5 26.5 5 26.5 18.5 1.6) $metal                   # pickaxe head, over the top right; 3px clear of the hoe
Save-Icon 'dvvein'

# ---------- DV Slots: chestplate with elytra wings behind
New-Icon '#B39DDB'
$wing = @(13, 10, 8, 8, 4, 11, 4, 20, 7, 27, 9, 24, 11, 26, 13, 20)
Part (Poly $wing) '#C9B6FF'
Part (Poly ($wing | ForEach-Object -Begin { $i = 0 } -Process { if ($i++ % 2 -eq 0) { 32 - $_ } else { $_ } })) '#C9B6FF'
foreach ($x in 6, 9, 22, 25) { for ($y = 13; $y -le 22; $y++) { Set-Px $x $y (Col '#8E7CC3') } }
Part (Poly @(9, 9, 13, 9, 16, 12, 19, 9, 23, 9, 24, 14, 21, 14, 21, 25, 11, 25, 11, 14, 8, 14)) '#D5DEE7'
Paint (Mask (RectS 9 9 4 3)) (Col '#AEBBC8'); Paint (Mask (RectS 19 9 4 3)) (Col '#AEBBC8')
for ($y = 14; $y -le 24; $y++) { Set-Px 16 $y (Col '#A9B6C3') }
for ($x = 11; $x -le 20; $x++) { Set-Px $x 20 (Col '#A9B6C3') }
Save-Icon 'dvslots'

# ---------- DV Dark: crescent moon and stars
New-Icon '#FFE08A'
Part { param($px, $py) (($px - 15) * ($px - 15) + ($py - 16) * ($py - 16) -le 81) -and -not (($px - 19.5) * ($px - 19.5) + ($py - 12.5) * ($py - 12.5) -le 64) } '#FFE08A'
foreach ($s in @(@(24, 21), @(25, 9), @(20, 26))) {
    foreach ($d in @(@(0, 0), @(1, 0), @(-1, 0), @(0, 1), @(0, -1))) { Set-Px ($s[0] + $d[0]) ($s[1] + $d[1]) (Col '#FFF4C7') }
}
Save-Icon 'dvdark'


# ---------- DV Map: parchment map with terrain patches and red waypoint pin
New-Icon '#66BB6A'
Part (RectS 6 8 20 18) '#E8D5B0'                                  # parchment sheet
Paint (Mask (RectS 8 10 8 7)) (Col '#7CB342')                     # green land
Paint (Mask (RectS 16 11 8 12)) (Col '#4FC3F7')                   # river/sea
Paint (Mask (RectS 8 18 10 6)) (Col '#AED581')                    # lower meadow
for ($y = 8; $y -lt 26; $y++) { Set-Px 13 $y (Col '#C8B08A'); Set-Px 19 $y (Col '#C8B08A') } # fold lines
Part (Poly @(16, 17, 13, 11, 19, 11)) '#E5534B'                   # red pin tip
Part (Disc 16 9.5 3.2) '#E5534B'                                  # red pin head
Set-Px 15 9 $white; Set-Px 16 9 $white
Save-Icon 'dvmap'

# ---------- DV Team: heraldic shield & banner with gold star
New-Icon '#E5534B'
Part (Poly @(8, 6, 24, 6, 24, 17, 16, 26, 8, 17)) '#D32F2F'       # shield body
Paint (Mask (Poly @(16, 7, 23, 7, 23, 16.5, 16, 24.5))) (Col '#B71C1C') # shield right half shade
Part (RectS 15 7 2 17) '#FFD54F'                                  # gold vertical stripe
Part (RectS 9 13 14 2) '#FFD54F'                                  # gold horizontal cross
Part (Disc 16 14 2.5) '#FFF1C1'                                   # emblem core
Save-Icon 'dvteam'

# ---------- write files (nearest-neighbour scaling keeps the pixels sharp)
function Scale($bmp, [int]$size) {
    $big = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($big)
    $g.InterpolationMode = 'NearestNeighbor'; $g.PixelOffsetMode = 'Half'
    $g.DrawImage($bmp, 0, 0, $size, $size)
    $big
}
$sheet = New-Object System.Drawing.Bitmap((256 * $icons.Count), 256)
$sg = [System.Drawing.Graphics]::FromImage($sheet); $sg.InterpolationMode = 'NearestNeighbor'; $sg.PixelOffsetMode = 'Half'; $sg.Clear([System.Drawing.Color]::White)
$i = 0
foreach ($id in $icons.Keys) {
    (Scale $icons[$id] 512).Save((Join-Path $out "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    (Scale $icons[$id] 128).Save((Join-Path $root "$id\src\main\resources\${id}_logo.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $sg.DrawImage($icons[$id], $i * 256, 0, 256, 256)
    $i++
}
$sheet.Save((Join-Path $out 'sheet.png'), [System.Drawing.Imaging.ImageFormat]::Png)
"wrote $($icons.Count) icons"
