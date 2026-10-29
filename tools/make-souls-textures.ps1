# Draws DV Science of Souls' 16x16 item textures (no external art). The soul inside a jar and a collar shard's gem are grey
# and the game tints them in the colours of the creature's spawn egg.
#   textures/item/soul_jar.png           the jar: a glass bottle with a white stopper and soul sand in it
#   textures/item/soul_jar_soul.png      the soul filling it (tinted: the egg's body colour)
#   textures/item/soul_jar_spots.png     swirls in it (tinted: the egg's spot colour)
#   textures/item/soul_collar.png        a leather collar with a soul gem
#   textures/item/collar_shard*.png      a piece of a dead pet's collar; its gem is tinted
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
Write-Host "Textures written to $tex"
