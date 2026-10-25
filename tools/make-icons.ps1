# Draws the DV mod icons (no external art).
#   art/icons/<mod>.png                       512x512, for Modrinth / CurseForge
#   <mod>/src/main/resources/<mod>_logo.png   128x128, shown in the Mods list (logoFile in mods.toml)
#   art/icons/sheet.png                       all icons side by side, for review
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File tools\make-icons.ps1

Add-Type -AssemblyName System.Drawing
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'art\icons'
New-Item -ItemType Directory -Force $out | Out-Null

function C([string]$hex, [int]$a = 255) {
    $v = [Convert]::ToInt32($hex.TrimStart('#'), 16)
    [System.Drawing.Color]::FromArgb($a, ($v -shr 16) -band 255, ($v -shr 8) -band 255, $v -band 255)
}

function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    $p
}

function Pen([string]$hex, [float]$width, [int]$a = 255) {
    $pen = New-Object System.Drawing.Pen((C $hex $a), $width)
    $pen.StartCap = 'Round'; $pen.EndCap = 'Round'; $pen.LineJoin = 'Round'
    $pen
}

function Brush([string]$hex, [int]$a = 255) { New-Object System.Drawing.SolidBrush((C $hex $a)) }

$LibPurple = '#9B6BFF'
$Outline = '#120F1C'

# The DV Lib mark: a rounded badge with cat ears and ":3" on it.
function Badge($g, [float]$x, [float]$y, [float]$w, [float]$h) {
    $ear = $h * 0.42
    $earW = $w * 0.30
    $ears = @(
        @(([System.Drawing.PointF]::new($x + $w * 0.08, $y + $h * 0.25)), ([System.Drawing.PointF]::new($x + $w * 0.10, $y - $ear * 0.55)), ([System.Drawing.PointF]::new($x + $w * 0.08 + $earW, $y + $h * 0.05))),
        @(([System.Drawing.PointF]::new($x + $w * 0.92, $y + $h * 0.25)), ([System.Drawing.PointF]::new($x + $w * 0.90, $y - $ear * 0.55)), ([System.Drawing.PointF]::new($x + $w * 0.92 - $earW, $y + $h * 0.05)))
    )
    $body = RoundRect $x $y $w $h ($h * 0.24)
    $outlinePen = Pen $Outline ($w * 0.07)
    foreach ($e in $ears) { $g.DrawPolygon($outlinePen, [System.Drawing.PointF[]]$e) }
    $g.DrawPath($outlinePen, $body)
    foreach ($e in $ears) { $g.FillPolygon((Brush $LibPurple), [System.Drawing.PointF[]]$e) }
    # inner ears
    foreach ($e in $ears) {
        $cx = ($e[0].X + $e[1].X + $e[2].X) / 3; $cy = ($e[0].Y + $e[1].Y + $e[2].Y) / 3
        $inner = $e | ForEach-Object { [System.Drawing.PointF]::new(($cx + ($_.X - $cx) * 0.45), ($cy + ($_.Y - $cy) * 0.45)) }
        $g.FillPolygon((Brush '#FFB3E6'), [System.Drawing.PointF[]]$inner)
    }
    $g.FillPath((Brush $LibPurple), $body)
    $font = New-Object System.Drawing.Font('Arial Black', ($h * 0.52), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
    $g.DrawString(':3', $font, (Brush '#FFFFFF'), (New-Object System.Drawing.RectangleF($x, ($y + $h * 0.04), $w, $h)), $fmt)
}

function Canvas([string]$accent) {
    $bmp = New-Object System.Drawing.Bitmap(512, 512)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'; $g.InterpolationMode = 'HighQualityBicubic'
    $bg = RoundRect 16 16 480 480 96
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Point(0, 0)), (New-Object System.Drawing.Point(512, 512)), (C '#2A2540'), (C '#15121F'))
    $g.FillPath($grad, $bg)
    $g.DrawPath((Pen $accent 12 200), $bg)
    @{ bmp = $bmp; g = $g }
}

$icons = [ordered]@{}

# --- DV Lib: the badge itself
$c = Canvas $LibPurple
Badge $c.g 96 176 320 250
$icons['dvlib'] = $c

# --- DV Zoom: magnifying glass
$c = Canvas '#4FC3F7'; $g = $c.g
$g.DrawLine((Pen $Outline 70), 345, 345, 440, 440)
$g.DrawLine((Pen '#CFE8FF' 48), 345, 345, 440, 440)
$g.FillEllipse((Brush '#4FC3F7' 70), 175, 175, 200, 200)
$g.DrawEllipse((Pen $Outline 56), 175, 175, 200, 200)
$g.DrawEllipse((Pen '#4FC3F7' 34), 175, 175, 200, 200)
$g.DrawArc((Pen '#FFFFFF' 14 180), 205, 205, 140, 140, 200, 70)
Badge $g 40 66 150 118
$icons['dvzoom'] = $c

# --- DV Food: drumstick with a gold (saturation) outline
$c = Canvas '#FFB74D'; $g = $c.g
$g.DrawLine((Pen $Outline 62), 330, 330, 415, 415)
$g.DrawLine((Pen '#F2E8D5' 40), 330, 330, 415, 415)
$g.FillEllipse((Brush '#F2E8D5'), 400, 375, 52, 52); $g.FillEllipse((Brush '#F2E8D5'), 375, 400, 52, 52)
$state = $g.Save(); $g.TranslateTransform(265, 265); $g.RotateTransform(-45)
$g.DrawEllipse((Pen '#FFC830' 18), -125, -95, 250, 190)
$g.FillEllipse((Brush '#B5562A'), -125, -95, 250, 190)
$g.FillEllipse((Brush '#D9773E'), -105, -85, 190, 120)
$g.Restore($state)
Badge $g 40 66 150 118
$icons['dvfood'] = $c

# --- DV Lens: an eye
$c = Canvas '#2EC4B6'; $g = $c.g
$eye = New-Object System.Drawing.Drawing2D.GraphicsPath
$eye.AddBezier(110, 310, 200, 170, 380, 170, 470, 310)
$eye.AddBezier(470, 310, 380, 450, 200, 450, 110, 310)
$g.DrawPath((Pen $Outline 28), $eye)
$g.FillPath((Brush '#F4F7FB'), $eye)
$g.FillEllipse((Brush '#2EC4B6'), 220, 240, 140, 140)
$g.FillEllipse((Brush $Outline), 258, 278, 64, 64)
$g.FillEllipse((Brush '#FFFFFF'), 300, 268, 26, 26)
Badge $g 40 66 150 118
$icons['dvlens'] = $c

# --- DV Vein: pickaxe and ore bits
$c = Canvas '#90A4AE'; $g = $c.g
foreach ($gem in @(@(160, 300), @(215, 380), @(140, 400))) {
    $s = $g.Save(); $g.TranslateTransform($gem[0], $gem[1]); $g.RotateTransform(45)
    $g.FillRectangle((Brush $Outline), -24, -24, 48, 48); $g.FillRectangle((Brush '#3EE6D0'), -17, -17, 34, 34)
    $g.Restore($s)
}
# The head's midpoint (t = 0.5) sits on the top of the handle, so the pick reaches both ways.
$g.DrawLine((Pen $Outline 56), 200, 450, 395, 190)
$g.DrawLine((Pen '#8B5A2B' 34), 200, 450, 395, 190)
$head = New-Object System.Drawing.Drawing2D.GraphicsPath
$head.AddBezier(275, 160, 355, 115, 455, 200, 445, 315)
$g.DrawPath((Pen $Outline 66), $head)
$g.DrawPath((Pen '#B8C4D0' 42), $head)
Badge $g 40 66 150 118
$icons['dvvein'] = $c

# --- DV Slots: elytra wings
$c = Canvas '#B39DDB'; $g = $c.g
foreach ($side in -1, 1) {
    $w = New-Object System.Drawing.Drawing2D.GraphicsPath
    $cx = 300
    $w.AddBezier($cx, 210, $cx + $side * 60, 170, $cx + $side * 160, 190, $cx + $side * 175, 240)
    $w.AddBezier($cx + $side * 175, 240, $cx + $side * 150, 330, $cx + $side * 90, 420, $cx + $side * 20, 450)
    $w.AddLine($cx + $side * 20, 450, $cx, 210)
    $g.DrawPath((Pen $Outline 24), $w)
    $g.FillPath((Brush '#C9B6FF'), $w)
    # two feather lines following the outer edge
    foreach ($inset in 45, 90) {
        $f = New-Object System.Drawing.Drawing2D.GraphicsPath
        $f.AddBezier([float]($cx + $side * 20), [float](225 + $inset * 0.3), [float]($cx + $side * (150 - $inset)), [float](215 + $inset * 0.4),
                [float]($cx + $side * (140 - $inset)), [float](320 + $inset * 0.3), [float]($cx + $side * 25), [float](430 - $inset * 0.6))
        $g.DrawPath((Pen '#8E7CC3' 7), $f)
    }
}
Badge $g 40 66 150 118
$icons['dvslots'] = $c

# --- DV Dark: crescent moon and stars
$c = Canvas '#FFE08A'; $g = $c.g
$disc = New-Object System.Drawing.Drawing2D.GraphicsPath; $disc.AddEllipse(150, 150, 300, 300)
$moon = New-Object System.Drawing.Region($disc)
$cut = New-Object System.Drawing.Drawing2D.GraphicsPath; $cut.AddEllipse(225, 105, 290, 290)
$moon.Exclude($cut)
$g.FillRegion((Brush '#FFE08A'), $moon)
foreach ($st in @(@(395, 395, 26), @(440, 300, 16), @(345, 455, 12))) {
    $x = $st[0]; $y = $st[1]; $r = $st[2]
    $pts = [System.Drawing.PointF[]]@(
        ([System.Drawing.PointF]::new($x, $y - $r)), ([System.Drawing.PointF]::new($x + $r * 0.28, $y - $r * 0.28)),
        ([System.Drawing.PointF]::new($x + $r, $y)), ([System.Drawing.PointF]::new($x + $r * 0.28, $y + $r * 0.28)),
        ([System.Drawing.PointF]::new($x, $y + $r)), ([System.Drawing.PointF]::new($x - $r * 0.28, $y + $r * 0.28)),
        ([System.Drawing.PointF]::new($x - $r, $y)), ([System.Drawing.PointF]::new($x - $r * 0.28, $y - $r * 0.28)))
    $g.FillPolygon((Brush '#FFF4C7'), $pts)
}
Badge $g 40 66 150 118
$icons['dvdark'] = $c

# --- DV Harvest: wheat ear
$c = Canvas '#E8C25A'; $g = $c.g
$g.DrawLine((Pen $Outline 26), 300, 465, 300, 215)
$g.DrawLine((Pen '#C9A23E' 14), 300, 465, 300, 215)
for ($i = 0; $i -lt 4; $i++) {
    $y = 225 + $i * 52
    foreach ($side in -1, 1) {
        $s = $g.Save(); $g.TranslateTransform((300 + $side * 26), $y); $g.RotateTransform($side * 35)
        $g.FillEllipse((Brush $Outline), -26, -40, 52, 80); $g.FillEllipse((Brush '#F2D27A'), -18, -32, 36, 64)
        $g.Restore($s)
    }
}
$g.FillEllipse((Brush $Outline), 274, 150, 52, 80); $g.FillEllipse((Brush '#F2D27A'), 282, 158, 36, 64)
Badge $g 40 66 150 118
$icons['dvharvest'] = $c

# --- write files
$sheet = New-Object System.Drawing.Bitmap((256 * $icons.Count), 256)
$sg = [System.Drawing.Graphics]::FromImage($sheet); $sg.InterpolationMode = 'HighQualityBicubic'; $sg.Clear((C '#FFFFFF'))
$i = 0
foreach ($id in $icons.Keys) {
    $bmp = $icons[$id].bmp
    $bmp.Save((Join-Path $out "$id.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $small = New-Object System.Drawing.Bitmap(128, 128)
    $smg = [System.Drawing.Graphics]::FromImage($small); $smg.InterpolationMode = 'HighQualityBicubic'; $smg.DrawImage($bmp, 0, 0, 128, 128)
    $res = Join-Path $root "$id\src\main\resources"
    $small.Save((Join-Path $res "${id}_logo.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $sg.DrawImage($bmp, $i * 256, 0, 256, 256)
    $i++
}
$sheet.Save((Join-Path $out 'sheet.png'), [System.Drawing.Imaging.ImageFormat]::Png)
"wrote $($icons.Count) icons"
