# Draws DV Science of Souls' 16x16 item textures (no external art). The soul inside a jar and a collar shard's gem are grey
# and the game tints them in the colours of the creature's spawn egg.
#   textures/item/soul_jar.png           the jar: a glass bottle with a white stopper and soul sand in it
#   textures/item/soul_jar_soul.png      the soul filling it (tinted: the egg's body colour)
#   textures/item/soul_jar_spots.png     swirls in it (tinted: the egg's spot colour)
#   textures/item/soul_collar.png        a leather collar with a soul gem
#   textures/item/collar_shard*.png      a piece of a dead pet's collar; its gem is tinted
#   textures/item/soul_essence*.png      a soul's essence: a glowing drop, its inside tinted
#   textures/item/*_soul_charm.png       soul charms, a pendant per tier
#   textures/mob_effect/*.png             the climbing and lava walking effects' icons
#   textures/item/pure_soul_*.png, soul_coal.png   a bottle of pure soul; overlays on the game's bucket and coal
#   textures/block/pure_soul_*.png       the pure soul liquid, still and flowing (animated)
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

# A soul essence: a glowing drop with a dark rim (o) and a glint (h); its inside (i) is tinted with the egg's body colour.
$drop = @(
    '................',
    '.......o........',
    '.......oo.......',
    '......oiio......',
    '......oiio......',
    '.....oiiiio.....',
    '.....oihiio.....',
    '....oihiiiio....',
    '....oihiiiio....',
    '....oiiiiiio....',
    '....oiiiiiio....',
    '.....oiiiio.....',
    '......oooo......',
    '................',
    '................',
    '................'
)
Sprite 'soul_essence' $drop @{ o = '#22343A'; h = '#FFFFFF' }
Sprite 'soul_essence_glow' $drop @{ i = '#D8D8D8' }

# Soul charms: a chain (m) holding a pendant framed in the tier's metal (f dark, g light) round a soul gem (e, h).
$charm = @(
    '...m........m...',
    '...m........m...',
    '....m......m....',
    '.....m....m.....',
    '......m..m......',
    '......ffff......',
    '.....fggggf.....',
    '....fgeeeegf....',
    '...fgeehheegf...',
    '...fgeehheegf...',
    '...fgeeeeeegf...',
    '....fgeeeegf....',
    '.....fgeegf.....',
    '......fggf......',
    '.......ff.......',
    '................'
)
$frames = @{ gold = @('#B07A12', '#F2C230'); diamond = @('#1E8C86', '#6FF0E8'); netherite = @('#2A2426', '#5A5052') }
foreach ($tier in 'gold', 'diamond', 'netherite') {
    Sprite "${tier}_soul_charm" $charm @{ m = '#8A8A92'; f = $frames[$tier][0]; g = $frames[$tier][1]; e = '#3FB8C8'; h = '#E0FFFF' }
}

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
