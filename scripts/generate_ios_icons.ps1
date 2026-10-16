Add-Type -AssemblyName System.Drawing

$srcPath = "public/assets/logo-icon/app-icon.png"
$destDir = "iosApp/Assets.xcassets/AppIcon.appiconset"

if (!(Test-Path -Path $destDir)) {
    New-Item -ItemType Directory -Force -Path $destDir | Out-Null
}

$srcBitmap = [System.Drawing.Bitmap]::new($srcPath)

$sizes = @(
    @{ name = "icon-1024.png"; width = 1024; height = 1024 },
    @{ name = "icon-180.png"; width = 180; height = 180 },
    @{ name = "icon-167.png"; width = 167; height = 167 },
    @{ name = "icon-152.png"; width = 152; height = 152 },
    @{ name = "icon-120.png"; width = 120; height = 120 },
    @{ name = "icon-87.png"; width = 87; height = 87 },
    @{ name = "icon-80.png"; width = 80; height = 80 },
    @{ name = "icon-76.png"; width = 76; height = 76 },
    @{ name = "icon-60.png"; width = 60; height = 60 },
    @{ name = "icon-58.png"; width = 58; height = 58 },
    @{ name = "icon-40.png"; width = 40; height = 40 },
    @{ name = "icon-29.png"; width = 29; height = 29 },
    @{ name = "icon-20.png"; width = 20; height = 20 }
)

foreach ($s in $sizes) {
    $targetPath = Join-Path $destDir $s.name
    $destBitmap = New-Object System.Drawing.Bitmap($s.width, $s.height)
    $graphics = [System.Drawing.Graphics]::FromImage($destBitmap)
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $graphics.DrawImage($srcBitmap, 0, 0, $s.width, $s.height)
    $destBitmap.Save($targetPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose()
    $destBitmap.Dispose()
    Write-Output "Generated $($s.name) ($($s.width)x$($s.height))"
}

$srcBitmap.Dispose()

# Create Contents.json for Xcode Asset Catalog
$contentsJson = @"
{
  "images": [
    {
      "idiom": "iphone",
      "size": "20x20",
      "scale": "2x",
      "filename": "icon-40.png"
    },
    {
      "idiom": "iphone",
      "size": "20x20",
      "scale": "3x",
      "filename": "icon-60.png"
    },
    {
      "idiom": "iphone",
      "size": "29x29",
      "scale": "2x",
      "filename": "icon-58.png"
    },
    {
      "idiom": "iphone",
      "size": "29x29",
      "scale": "3x",
      "filename": "icon-87.png"
    },
    {
      "idiom": "iphone",
      "size": "40x40",
      "scale": "2x",
      "filename": "icon-80.png"
    },
    {
      "idiom": "iphone",
      "size": "40x40",
      "scale": "3x",
      "filename": "icon-120.png"
    },
    {
      "idiom": "iphone",
      "size": "60x60",
      "scale": "2x",
      "filename": "icon-120.png"
    },
    {
      "idiom": "iphone",
      "size": "60x60",
      "scale": "3x",
      "filename": "icon-180.png"
    },
    {
      "idiom": "ipad",
      "size": "20x20",
      "scale": "1x",
      "filename": "icon-20.png"
    },
    {
      "idiom": "ipad",
      "size": "20x20",
      "scale": "2x",
      "filename": "icon-40.png"
    },
    {
      "idiom": "ipad",
      "size": "29x29",
      "scale": "1x",
      "filename": "icon-29.png"
    },
    {
      "idiom": "ipad",
      "size": "29x29",
      "scale": "2x",
      "filename": "icon-58.png"
    },
    {
      "idiom": "ipad",
      "size": "40x40",
      "scale": "1x",
      "filename": "icon-40.png"
    },
    {
      "idiom": "ipad",
      "size": "40x40",
      "scale": "2x",
      "filename": "icon-80.png"
    },
    {
      "idiom": "ipad",
      "size": "76x76",
      "scale": "1x",
      "filename": "icon-76.png"
    },
    {
      "idiom": "ipad",
      "size": "76x76",
      "scale": "2x",
      "filename": "icon-152.png"
    },
    {
      "idiom": "ipad",
      "size": "83.5x83.5",
      "scale": "2x",
      "filename": "icon-167.png"
    },
    {
      "idiom": "ios-marketing",
      "size": "1024x1024",
      "scale": "1x",
      "filename": "icon-1024.png"
    }
  ],
  "info": {
    "version": 1,
    "author": "xcode"
  }
}
"@

Set-Content -Path (Join-Path $destDir "Contents.json") -Value $contentsJson -Encoding UTF8

# Also create root Contents.json for Assets.xcassets
$rootContentsJson = @"
{
  "info": {
    "version": 1,
    "author": "xcode"
  }
}
"@
Set-Content -Path "iosApp/Assets.xcassets/Contents.json" -Value $rootContentsJson -Encoding UTF8
Write-Output "Assets.xcassets generated successfully!"
