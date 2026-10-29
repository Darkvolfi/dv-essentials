# Makes DV New Crafts' copper textures by recolouring the game's own iron ones (read from Minecraft's client jar in
# the Gradle cache): grey metal becomes copper, the wooden handles stay as they are, and the copper torch's and
# lantern's flame turns green.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-newcrafts-textures.ps1

Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression.FileSystem
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tex = Join-Path $root 'dvnewcrafts\src\main\resources\assets\dvnewcrafts\textures'
$jarPath = Join-Path $env:USERPROFILE '.gradle\caches\forge_gradle\minecraft_repo\versions\1.20.1\client-extra.jar'
if (-not (Test-Path $jarPath)) { throw "Minecraft's client jar not found: $jarPath (run a Gradle build once first)" }
$jar = [System.IO.Compression.ZipFile]::OpenRead($jarPath)

# Copper from dark to light, by the grey pixel's brightness (0-1).
$copper = @(
    @(0.00, 0x2A, 0x0F, 0x06),
    @(0.25, 0x5E, 0x25, 0x13),
    @(0.45, 0x9C, 0x45, 0x29),
    @(0.60, 0xC1, 0x61, 0x3B),
    @(0.75, 0xE2, 0x7C, 0x4E),
    @(0.88, 0xF5, 0x9E, 0x70),
    @(1.00, 0xFF, 0xD3, 0xB0)
)

function Copper([double]$v) {
    for ($i = 1; $i -lt $copper.Count; $i++) {
        $a = $copper[$i - 1]; $b = $copper[$i]
        if ($v -le $b[0]) {
            $t = ($v - $a[0]) / ($b[0] - $a[0])
            return @(
                [int]($a[1] + ($b[1] - $a[1]) * $t),
                [int]($a[2] + ($b[2] - $a[2]) * $t),
                [int]($a[3] + ($b[3] - $a[3]) * $t))
        }
    }
    return @(0xFF, 0xD3, 0xB0)
}

function Load([string]$name) {
    $entry = $jar.GetEntry("assets/minecraft/textures/$name.png")
    if (-not $entry) { throw "No texture $name in the client jar" }
    $stream = $entry.Open()
    $bmp = New-Object System.Drawing.Bitmap $stream
    $copy = New-Object System.Drawing.Bitmap $bmp
    $bmp.Dispose(); $stream.Dispose()
    return $copy
}

# $metal: turn grey pixels to copper. $flame: turn warm, bright pixels (yellow/orange flame) green.
function Recolour([string]$from, [string]$to, [bool]$metal, [bool]$flame) {
    $bmp = Load $from
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            $c = $bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $max = [Math]::Max($c.R, [Math]::Max($c.G, $c.B)) / 255.0
            $min = [Math]::Min($c.R, [Math]::Min($c.G, $c.B)) / 255.0
            $sat = if ($max -eq 0) { 0 } else { ($max - $min) / $max }
            $hue = $c.GetHue()
            # The flame first: its pale centre would otherwise pass for grey metal.
            if ($flame -and $hue -le 65 -and $max -ge 0.7) {
                # Green flame: the red and green swap places and blue drops, so yellow turns lime and white stays pale.
                $g = [Math]::Max($c.R, $c.G)
                $r = [int]([Math]::Min($c.R, $c.G) * 0.55)
                $b = [int]($c.B * 0.6)
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $r, $g, $b))
            } elseif ($metal -and ($sat -lt 0.2 -or ($hue -ge 180 -and $hue -le 260 -and $sat -lt 0.55))) {
                # Iron is plain grey; the chain and the lantern's frame are a bluish grey.
                # Brightness as the eye sees it, so the shading keeps its contrast.
                $v = (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B) / 255.0
                $rgb = Copper $v
                $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $rgb[0], $rgb[1], $rgb[2]))
            }
        }
    }
    $path = Join-Path $tex "$to.png"
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    # Animated textures (the lantern's frames stacked) need their .mcmeta, or the strip is drawn as one picture.
    $meta = $jar.GetEntry("assets/minecraft/textures/$from.png.mcmeta")
    if ($meta) {
        $reader = New-Object System.IO.StreamReader $meta.Open()
        [System.IO.File]::WriteAllText("$path.mcmeta", $reader.ReadToEnd())
        $reader.Dispose()
    }
}

foreach ($tool in 'sword', 'pickaxe', 'axe', 'shovel', 'hoe', 'helmet', 'chestplate', 'leggings', 'boots', 'nugget') {
    Recolour "item/iron_$tool" "item/copper_$tool" $true $false
}
Recolour 'models/armor/iron_layer_1' 'models/armor/copper_layer_1' $true $false
Recolour 'models/armor/iron_layer_2' 'models/armor/copper_layer_2' $true $false
Recolour 'block/iron_bars' 'block/copper_bars' $true $false
Recolour 'block/chain' 'block/copper_chain' $true $false
Recolour 'item/chain' 'item/copper_chain' $true $false
Recolour 'block/lantern' 'block/copper_lantern' $true $true
Recolour 'item/lantern' 'item/copper_lantern' $true $true
Recolour 'block/torch' 'block/copper_torch' $false $true
Recolour 'particle/flame' 'particle/copper_flame' $false $true

$jar.Dispose()
Write-Host "Textures written to $tex"
