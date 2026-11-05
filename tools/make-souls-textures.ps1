# Draws DV Science of Souls' 16x16 item textures (no external art). The soul inside a jar and a collar shard's gem are grey
# and the game tints them in the colours of the creature's spawn egg.
#   textures/item/soul_jar.png           the jar: a glass bottle with a white stopper and soul sand in it
#   textures/item/soul_jar_soul.png      the soul filling it (tinted: the egg's body colour)
#   textures/item/soul_jar_spots.png     swirls in it (tinted: the egg's spot colour)
#   textures/item/soul_collar.png        a leather collar with a soul gem
#   textures/item/collar_shard*.png      a piece of a dead pet's collar; its gem is tinted
#   textures/item/soul_tube.png          an empty glass test tube; soul_essence*.png the same tube filled with an essence (tinted)
#   textures/item/soul_card_*.png        soul cards (the soul charms): a frame per tier and the dark rack the tubes stand in
#   textures/mob_effect/*.png             the climbing and lava walking effects' icons
#   textures/item/pure_soul_*.png, soul_coal.png   a bottle of pure soul; overlays on the game's bucket and coal
#   textures/block/pure_soul_*.png       the pure soul liquid, still and flowing (animated)
#   textures/block/soul_flow.png         souls flowing along the Soul Refiner's amethyst (animated)
#   textures/block/amethyst_crack_*.png  cracks over the Soul Refiner's amethyst as it wears
#   textures/block/refiner_*.png         gold inlays over the Soul Refiner's stone
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-souls-textures.ps1

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tex = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\item'
New-Item -ItemType Directory -Force $tex | Out-Null

function Col([string]$hex) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb(255, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255)
}

# rows: one char per pixel; chars not in $palette are transparent. Palette keys must differ beyond letter case
# (PowerShell hashtables ignore it).
function Sprite([string]$name, [string[]]$rows, [hashtable]$palette) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    for ($y = 0; $y -lt 16; $y++) {
        if ($rows[$y].Length -ne 16) { throw "Row $y of $name is $($rows[$y].Length) wide" }
        for ($x = 0; $x -lt 16; $x++) {
            $c = [string]$rows[$y][$x]
            if ($palette.ContainsKey($c)) { $bmp.SetPixel($x, $y, (Col $palette[$c])) }
        }
    }
    $bmp.Save((Join-Path $tex "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}

# The jar, shaped like the game's glass bottle: a white stopper (w, v), glass edge (g) with a glint (h), and soul sand
# (s, d) in its round bottom; i is the inside above the sand, where the soul goes.
$jar = @(
    '................',
    '......wwww......',
    '......wvvw......',
    '.......gg.......',
    '......gihg......',
    '.....giiiig.....',
    '....giiiiihg....',
    '...giiiiiiihg...',
    '...giiiiiiiig...',
    '...giiiiiiiig...',
    '...giiiiiiiig...',
    '...gssssssssg...',
    '...gsdsdsdsdg...',
    '....gssssssg....',
    '.....gggggg.....',
    '................'
)
Sprite 'soul_jar' $jar @{ w = '#F4F4F4'; v = '#CFCFCF'; g = '#A8D8E0'; h = '#F4FFFF'; s = '#5A4436'; d = '#46342A' }
# The soul fills the inside; a diagonal swirl in it takes the spots' colour.
$soul = @(); $spots = @()
for ($y = 0; $y -lt 16; $y++) {
    $a = ''; $b = ''
    for ($x = 0; $x -lt 16; $x++) {
        $inside = $jar[$y][$x] -eq 'i'
        $swirl = $inside -and ((($x + $y) % 4) -eq 0)
        $a += $(if ($inside -and -not $swirl) { 'a' } else { '.' })
        $b += $(if ($swirl) { 'b' } else { '.' })
    }
    $soul += $a; $spots += $b
}
Sprite 'soul_jar_soul' $soul @{ a = '#D8D8D8' }
Sprite 'soul_jar_spots' $spots @{ b = '#FFFFFF' }

# The collar: dark and light leather (k, l), a metal ring (m) and a soul gem (c, e).
Sprite 'soul_collar' @(
    '................',
    '................',
    '....kkkkkkkk....',
    '...kllllllllk...',
    '..kl........lk..',
    '..kl........lk..',
    '..kl........lk..',
    '..kl........lk..',
    '..kl........lk..',
    '...kll....llk...',
    '....kkk..kkk....',
    '......mccm......',
    '......ceec......',
    '.......cc.......',
    '................',
    '................'
) @{ k = '#3A2414'; l = '#7A4E2C'; m = '#C0C0C8'; c = '#4FD8E8'; e = '#B8F4FF' }

# The collar shard a dead pet leaves: a snapped piece of the collar (k, l), its ring (m), and the soul gem (c, e),
# which takes the colour of the creature whose soul it keeps.
$shard = @(
    '................',
    '................',
    '................',
    '.....kkkk.......',
    '...kkllllk......',
    '..kll....lk.....',
    '..kl......k.....',
    '..kl............',
    '..kl............',
    '..kl............',
    '...kl...........',
    '...kll..........',
    '....klm.........',
    '.....mcc........',
    '.....cec........',
    '......c.........'
)
Sprite 'collar_shard' $shard @{ k = '#3A2414'; l = '#7A4E2C'; m = '#C0C0C8' }
Sprite 'collar_shard_gem' $shard @{ c = '#B4B4B4'; e = '#FFFFFF' }

# A glass test tube (made from two glass and a quartz stopper), standing: the stopper (q, p shade), the glass's edges (g)
# and a glint (h). Filled, it is a soul's essence: the soul (i, layer 1) is tinted with the egg's body colour.
$tube = @(
    '................',
    '......qqqq......',
    '......pqqp......',
    '......g..g......',
    '......gh.g......',
    '......gh.g......',
    '......gh.g......',
    '......giig......',
    '......giig......',
    '......giig......',
    '......giig......',
    '......giig......',
    '......giig......',
    '......giig......',
    '.......gg.......',
    '................'
)
Sprite 'soul_tube' $tube @{ q = '#EDE6DA'; p = '#C9BFB0'; g = '#9FC4CC'; h = '#E6F6F8' }
Sprite 'soul_essence' $tube @{ q = '#EDE6DA'; p = '#C9BFB0'; g = '#9FC4CC'; h = '#E6F6F8' }
Sprite 'soul_essence_glow' $tube @{ i = '#D8D8D8' }

# Soul cards (what were soul charms), shaped like DV Backpack's upgrade cards so they sit in its upgrade slots the same
# way: a frame with rounded corners in the tier's metal (l dark, s a stitch of the light one), rivets (r) in the corners and
# a dark inside (i). The face is a dark rack with a gold shelf the tubes stand on; the tubes are the model's own.
$cardFrame = @()
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
    $cardFrame += $row
}
$metals = @{ gold = @('#B07A12', '#F2C230'); diamond = @('#1E8C86', '#6FF0E8'); netherite = @('#2A2426', '#6A6062') }
foreach ($tier in 'gold', 'diamond', 'netherite') {
    Sprite "soul_card_$tier" $cardFrame @{ o = '#14101A'; l = $metals[$tier][0]; s = $metals[$tier][1]; r = '#D8D8E0'; i = '#2A2034' }
}
$rack = @()
for ($y = 0; $y -lt 16; $y++) {
    $rack += $(if ($y -eq 13) { 'ssssssssssssssss' } elseif ($y -eq 14) { 'dddddddddddddddd' } elseif ($y % 4 -eq 1) { 'b.b.b.b.b.b.b.b.' } else { 'bbbbbbbbbbbbbbbb' })
}
Sprite 'soul_card_face' $rack @{ b = '#1E1826'; s = '#C9A13A'; d = '#8C6A1E' }

# Status effect icons (18x18, the 16x16 drawing in the middle): a spider's essence climbing, a lava horse's walking on lava.
$effects = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\mob_effect'
New-Item -ItemType Directory -Force $effects | Out-Null
function Icon([string]$name, [string[]]$rows, [hashtable]$palette) {
    $bmp = New-Object System.Drawing.Bitmap 18, 18
    for ($y = 0; $y -lt 16; $y++) {
        if ($rows[$y].Length -ne 16) { throw "Row $y of $name is $($rows[$y].Length) wide" }
        for ($x = 0; $x -lt 16; $x++) {
            $c = [string]$rows[$y][$x]
            if ($palette.ContainsKey($c)) { $bmp.SetPixel($x + 1, $y + 1, (Col $palette[$c])) }
        }
    }
    $bmp.Save((Join-Path $effects "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
Icon 'climbing' @(
    '................',
    '.k............k.',
    '..k..........k..',
    '...k..kkkk..k...',
    '....kkkkkkkk....',
    '.kk.kkrkkrkk.kk.',
    '...kkkkkkkkkk...',
    '.kk.kkkkkkkk.kk.',
    '...k.kkkkkk.k...',
    '..k...kkkk...k..',
    '.k....kkkk....k.',
    '.......kk.......',
    '................',
    '................',
    '................',
    '................'
) @{ k = '#2E2420'; r = '#C0202A' }
Icon 'lava_walking' @(
    '................',
    '................',
    '................',
    '.....bbb........',
    '.....bnb........',
    '.....bnb........',
    '.....bnbbbb.....',
    '.....bnnnnbb....',
    '.....bbbbbbbb...',
    '................',
    '.llylllyllllyll.',
    'llllylllllylllll',
    'lyllllllylllllly',
    'llllllllllllllll',
    'dllldllllldlllld',
    'dddddddddddddddd'
) @{ b = '#5A3A22'; n = '#8A5A34'; l = '#FF8A1A'; y = '#FFD040'; d = '#C04A10' }
Write-Host "Textures written to $tex"

# Pure soul is dark blue. A bottle of it: the jar's shape with a cork, the liquid filling it to the bottom.
Sprite 'pure_soul_bottle' $jar @{ w = '#8A6A44'; v = '#6A4E30'; g = '#A8D8E0'; h = '#F4FFFF'; i = '#1E3A8A'; s = '#172E6E'; d = '#4F7BD8' }

# The bucket and soul coal are the game's own bucket and coal (their models' first layer) with an overlay of ours: the
# liquid where the game's water bucket has its water, and veins on the coal's lighter pixels. Only where those pixels
# lie is read from the game's textures (in Gradle's cache, put there by the first build); none of its art is copied.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$assets = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($assets)
function GameTexture([string]$path) {
    $stream = $zip.GetEntry("assets/minecraft/textures/$path").Open()
    $bmp = New-Object System.Drawing.Bitmap $stream
    $stream.Dispose()
    $bmp
}
$bucket = GameTexture 'item/bucket.png'
$water = GameTexture 'item/water_bucket.png'
$coal = GameTexture 'item/coal.png'
$zip.Dispose()

$liquid = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $w = $water.GetPixel($x, $y)
        if ($w.A -gt 0 -and $w.ToArgb() -ne $bucket.GetPixel($x, $y).ToArgb()) {
            # the water's light and dark kept, in the soul's blues
            $light = $w.GetBrightness()
            $c = if ($light -gt 0.55) { '#4F7BD8' } elseif ($light -gt 0.4) { '#1E3A8A' } else { '#172E6E' }
            $liquid.SetPixel($x, $y, (Col $c))
        }
    }
}
$liquid.Save((Join-Path $tex 'pure_soul_bucket.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$liquid.Dispose()

$veins = New-Object System.Drawing.Bitmap 16, 16
for ($y = 0; $y -lt 16; $y++) {
    for ($x = 0; $x -lt 16; $x++) {
        $p = $coal.GetPixel($x, $y)
        if ($p.A -gt 0 -and $p.GetBrightness() -gt 0.16) { $veins.SetPixel($x, $y, (Col $(if ($p.GetBrightness() -gt 0.24) { '#3B6BD6' } else { '#1E3A8A' }))) }
    }
}
$veins.Save((Join-Path $tex 'soul_coal.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$veins.Dispose()
$bucket.Dispose(); $water.Dispose(); $coal.Dispose()

# The liquid itself, animated (16x16 frames, stacked): deep blue with slow lighter waves, a little see-through. The waves
# wrap at the tile's edges; the flowing one runs downwards. (The soul faces drifting in it are the game's soul particles.)
$blocks = Join-Path $root 'dvsouls\src\main\resources\assets\dvsouls\textures\block'
function Liquid([string]$name, [int]$frames, [bool]$flows) {
    $bmp = New-Object System.Drawing.Bitmap 16, (16 * $frames)
    for ($f = 0; $f -lt $frames; $f++) {
        $t = $f / $frames
        for ($y = 0; $y -lt 16; $y++) {
            for ($x = 0; $x -lt 16; $x++) {
                $v = if ($flows) { [Math]::Sin(2 * [Math]::PI * ($y / 16 - $t) + [Math]::Sin(2 * [Math]::PI * $x / 16)) }
                     else { [Math]::Sin(2 * [Math]::PI * ($x / 16 + $t)) * 0.5 + [Math]::Sin(2 * [Math]::PI * (($x + $y) / 16 - $t)) * 0.5 }
                $k = 0.75 + 0.25 * $v
                $r = [int](30 * $k + 40 * [Math]::Max(0, $v))
                $g = [int](58 * $k + 60 * [Math]::Max(0, $v))
                $b = [int][Math]::Min(255, 150 * $k + 70 * [Math]::Max(0, $v))
                $bmp.SetPixel($x, $f * 16 + $y, [System.Drawing.Color]::FromArgb(220, $r, $g, $b))
            }
        }
    }
    $bmp.Save((Join-Path $blocks "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Set-Content -Encoding ascii (Join-Path $blocks "$name.png.mcmeta") '{"animation": {"frametime": 2}}'
}
Liquid 'pure_soul_still' 32 $false
Liquid 'pure_soul_flow' 32 $true

# Souls flowing along the Soul Refiner's amethyst (animated, see-through): pale blue wisps with fading tails drifting
# right, one every other row, each row at its own place and speed so they don't move as one.
$flow = New-Object System.Drawing.Bitmap 16, (16 * 16)
for ($f = 0; $f -lt 16; $f++) {
    for ($y = 0; $y -lt 16; $y += 2) {
        $speed = 1 + ($y / 2) % 2
        $head = ($y * 7 + $f * $speed) % 16
        for ($t = 0; $t -lt 4; $t++) {
            $x = ($head - $t + 16) % 16
            $a = @(255, 190, 120, 60)[$t]
            $flow.SetPixel($x, $f * 16 + $y, [System.Drawing.Color]::FromArgb($a, 170 + 20 * (3 - $t), 215, 255))
        }
    }
}
$flow.Save((Join-Path $blocks 'soul_flow.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$flow.Dispose()
Set-Content -Encoding ascii (Join-Path $blocks 'soul_flow.png.mcmeta') '{"animation": {"frametime": 2}}'

# Cracks over the Soul Refiner's amethyst as it wears (model and screen), three stages, each with the last one's cracks
# and more: dark violet jagged lines walked from fixed starts, opaque so they draw the same in any render layer.
$crackDark = [System.Drawing.Color]::FromArgb(255, 42, 18, 64)
$starts = @(@(2, 3, 1, 1), @(13, 2, -1, 1), @(7, 14, 1, -1), @(0, 9, 1, 0), @(15, 11, -1, -1), @(9, 0, 0, 1))
$crack = New-Object System.Drawing.Bitmap 16, 16
for ($stage = 1; $stage -le 3; $stage++) {
    foreach ($s in $starts[(($stage - 1) * 2)..(($stage - 1) * 2 + 1)]) {
        $x = $s[0]; $y = $s[1]; $seed = $x * 31 + $y * 7
        for ($i = 0; $i -lt 4 + 2 * $stage; $i++) {
            if ($x -lt 0 -or $x -gt 15 -or $y -lt 0 -or $y -gt 15) { break }
            $crack.SetPixel($x, $y, $crackDark)
            $seed = ($seed * 1103515245 + 12345) -band 0x7FFFFFFF
            $x += $s[2]; $y += $s[3]
            if ($seed % 3 -eq 0) { if ($s[2] -eq 0) { $x += 1 - 2 * ($seed % 2) } else { $y += 1 - 2 * ($seed % 2) } }
        }
    }
    $crack.Save((Join-Path $blocks "amethyst_crack_$stage.png"), [System.Drawing.Imaging.ImageFormat]::Png)
}
$crack.Dispose()

# Gold inlays laid over the Soul Refiner's polished blackstone (the game's stone stays underneath, only these are ours).
# Each is drawn where its part's faces read the texture: the slab's sides rows 11-15, the box's front columns 0-5 and
# its sides 4-11 (rows 0-4), the tower's sides columns 4-11 (rows 0-10), the wall's front rows 0-10.
$inlay = @{ 'g' = '#C9A13A'; 'o' = '#8C6A1E' }
function Inlay([string]$name, [hashtable]$rows) {
    $bmp = New-Object System.Drawing.Bitmap 16, 16
    foreach ($y in $rows.Keys) {
        $row = $rows[$y]
        if ($row.Length -ne 16) { throw "Row $y of $name is $($row.Length) wide" }
        for ($x = 0; $x -lt 16; $x++) {
            $c = [string]$row[$x]
            if ($inlay.ContainsKey($c)) { $bmp.SetPixel($x, $y, (Col $inlay[$c])) }
        }
    }
    $bmp.Save((Join-Path $blocks "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
}
# the slab: a trim along its top, small diamonds along it
Inlay 'refiner_band' @{ 11 = 'gggggggggggggggg'; 12 = '...g.......g....'; 13 = '..gog.....gog...'; 14 = '...g.......g....' }
# the box: a trim along its bottom, a short bar on the front and on each side
Inlay 'refiner_box' @{ 2 = '..gg...gg.......'; 4 = 'gggggggggggg....' }
# the tower: a trim along each side's top and a small diamond under it
Inlay 'refiner_tower' @{ 0 = '....gggggggg....'; 4 = '.......g........'; 5 = '......gog.......'; 6 = '.......g........' }
# the wall: a small diamond seen under the amethyst
Inlay 'refiner_wall' @{ 7 = '.........g......'; 8 = '........gog.....'; 9 = '.........g......' }
# the soul furnaces: a frame round their sides and top, a diamond on each edge's middle
Inlay 'furnace_inlay' @{ 1 = '.gggggggggggggg.'; 2 = '.g.....gg.....g.'; 6 = '.g............g.'; 7 = '.gg..........gg.'
    8 = '.gg..........gg.'; 9 = '.g............g.'; 13 = '.g.....gg.....g.'; 14 = '.gggggggggggggg.'
    3 = '.g............g.'; 4 = '.g............g.'; 5 = '.g............g.'; 10 = '.g............g.'; 11 = '.g............g.'; 12 = '.g............g.' }
