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

# The DV Lib mark, a "library computer": a CRT monitor showing an upright ":3" cat face
# (dots as eyes on top, the 3 as the mouth), standing on a row of books that forms the case.
# $s is the width; the mark is about 1.13 * $s tall.
function Badge($g, [float]$x, [float]$y, [float]$s) {
    $line = Pen $Outline ($s * 0.045)

    # books (the case) and the shelf they stand on
    $bx = $x + $s * 0.07; $bw = $s * 0.86 / 5
    $bh = $s * 0.36; $bBottom = $y + $s * 0.72 + $bh
    $colors = '#E5534B', '#4FC3F7', '#FFD54F', '#81C784', '#9B6BFF'
    $heights = 1.0, 0.86, 1.0, 0.93, 0.8
    for ($i = 0; $i -lt 5; $i++) {
        $h = $bh * $heights[$i]
        $rect = [System.Drawing.RectangleF]::new($bx + $i * $bw, $bBottom - $h, $bw, $h)
        $g.FillRectangle((Brush $colors[$i]), $rect)
        $g.DrawRectangle($line, $rect.X, $rect.Y, $rect.Width, $rect.Height)
        $g.DrawLine((Pen '#FFFFFF' ($s * 0.02) 150), [float]($rect.X + $bw * 0.25), [float]($rect.Y + $h * 0.22), [float]($rect.X + $bw * 0.75), [float]($rect.Y + $h * 0.22))
    }
    $shelf = [System.Drawing.RectangleF]::new($x, $bBottom, $s, $s * 0.08)
    $g.FillRectangle((Brush '#A0682F'), $shelf)
    $g.DrawRectangle((Pen $Outline ($s * 0.025)), $shelf.X, $shelf.Y, $shelf.Width, $shelf.Height)

    # monitor neck and CRT body
    $neck = [System.Drawing.RectangleF]::new($x + $s * 0.42, $y + $s * 0.62, $s * 0.16, $s * 0.1)
    $g.FillRectangle((Brush '#BDB5A4'), $neck)
    $g.DrawRectangle($line, $neck.X, $neck.Y, $neck.Width, $neck.Height)
    $body = RoundRect $x $y $s ($s * 0.64) ($s * 0.1)
    $g.FillPath((Brush '#D9D2C2'), $body)
    $g.DrawPath($line, $body)
    $sw = $s * 0.76; $sh = $s * 0.46
    $screen = RoundRect ($x + $s * 0.12) ($y + $s * 0.08) $sw $sh ($s * 0.07)
    $g.FillPath((Brush $LibPurple), $screen)
    $g.DrawPath((Pen $Outline ($s * 0.03)), $screen)
    $g.FillEllipse((Brush '#7EE787'), $x + $s * 0.06, $y + $s * 0.56, $s * 0.04, $s * 0.04) # power light

    # upright ":3": rotate the text a quarter turn so the colon sits on top as eyes
    $font = New-Object System.Drawing.Font('Arial Black', ($sh * 0.78), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
    $fmt = New-Object System.Drawing.StringFormat
    $fmt.Alignment = 'Center'; $fmt.LineAlignment = 'Center'
    $state = $g.Save()
    $g.TranslateTransform(($x + $s * 0.12 + $sw / 2), ($y + $s * 0.08 + $sh / 2))
    $g.RotateTransform(90)
    $g.DrawString(':3', $font, (Brush '#FFFFFF'), [System.Drawing.RectangleF]::new(-$sh, -$sw / 2, $sh * 2, $sw), $fmt)
    $g.Restore($state)
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
Badge $c.g 106 90 300
$icons['dvlib'] = $c

# --- DV Zoom: magnifying glass
$c = Canvas '#4FC3F7'; $g = $c.g
$g.DrawLine((Pen $Outline 70), 345, 345, 440, 440)
$g.DrawLine((Pen '#CFE8FF' 48), 345, 345, 440, 440)
$g.FillEllipse((Brush '#4FC3F7' 70), 175, 175, 200, 200)
$g.DrawEllipse((Pen $Outline 56), 175, 175, 200, 200)
$g.DrawEllipse((Pen '#4FC3F7' 34), 175, 175, 200, 200)
$g.DrawArc((Pen '#FFFFFF' 14 180), 205, 205, 140, 140, 200, 70)
Badge $g 32 30 145
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
Badge $g 32 30 145
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
Badge $g 32 30 145
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
Badge $g 32 30 145
$icons['dvvein'] = $c

# --- DV Slots: a chestplate with elytra wings spread behind it (armor and elytra at once)
$c = Canvas '#B39DDB'; $g = $c.g
$cx = 290
foreach ($side in -1, 1) {
    # wing: from the shoulder out to a high tip, then a long feathered trailing edge back in
    $w = New-Object System.Drawing.Drawing2D.GraphicsPath
    $w.AddBezier($cx + $side * 40, 260, $cx + $side * 105, 215, $cx + $side * 150, 210, $cx + $side * 168, 232)
    $w.AddBezier($cx + $side * 168, 232, $cx + $side * 172, 320, $cx + $side * 150, 400, $cx + $side * 110, 455)
    $w.AddLine($cx + $side * 110, 455, $cx + $side * 90, 425)
    $w.AddLine($cx + $side * 90, 425, $cx + $side * 75, 452)
    $w.AddLine($cx + $side * 75, 452, $cx + $side * 58, 405)
    $w.AddLine($cx + $side * 58, 405, $cx + $side * 40, 260)
    $w.CloseFigure()
    $g.DrawPath((Pen $Outline 22), $w)
    $g.FillPath((Brush '#C9B6FF'), $w)
    foreach ($t in 0.35, 0.7) {
        $g.DrawLine((Pen '#8E7CC3' 8), [float]($cx + $side * (60 + 100 * $t)), [float](240 + 15 * $t), [float]($cx + $side * (55 + 50 * $t)), [float](395 + 30 * $t))
    }
}
# chestplate: wide shoulders, V neck, narrower waist
$chest = New-Object System.Drawing.Drawing2D.GraphicsPath
$chest.AddLines([System.Drawing.PointF[]]@(
    [System.Drawing.PointF]::new($cx - 95, 230), [System.Drawing.PointF]::new($cx - 35, 225),
    [System.Drawing.PointF]::new($cx, 265), [System.Drawing.PointF]::new($cx + 35, 225),
    [System.Drawing.PointF]::new($cx + 95, 230), [System.Drawing.PointF]::new($cx + 100, 300),
    [System.Drawing.PointF]::new($cx + 65, 305), [System.Drawing.PointF]::new($cx + 65, 430),
    [System.Drawing.PointF]::new($cx - 65, 430), [System.Drawing.PointF]::new($cx - 65, 305),
    [System.Drawing.PointF]::new($cx - 100, 300)))
$chest.CloseFigure()
$g.DrawPath((Pen $Outline 22), $chest)
$g.FillPath((Brush '#D5DEE7'), $chest)
$g.DrawLine((Pen '#A9B6C3' 10), $cx, 280, $cx, 420)
$g.DrawLine((Pen '#A9B6C3' 10), ($cx - 65), 360, ($cx + 65), 360) # belt line
# shoulder plates make it read as armor, not a shirt
foreach ($side in -1, 1) {
    $plate = RoundRect ($cx + $side * 72 - 32) 214 64 50 16
    $g.DrawPath((Pen $Outline 14), $plate)
    $g.FillPath((Brush '#AEBBC8'), $plate)
}
Badge $g 32 30 145
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
Badge $g 32 30 145
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
Badge $g 32 30 145
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
