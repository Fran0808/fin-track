$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Add-Type -AssemblyName System.Drawing
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
public static class FinTrackBrandAssets {
    public static Rectangle Bounds(Bitmap source) {
        int left = source.Width, top = source.Height, right = -1, bottom = -1;
        for (int y = 0; y < source.Height; y++)
            for (int x = 0; x < source.Width; x++)
                if (source.GetPixel(x, y).A > 32) {
                    left = Math.Min(left, x); top = Math.Min(top, y);
                    right = Math.Max(right, x); bottom = Math.Max(bottom, y);
                }
        if (right < left) throw new InvalidOperationException("Logo contains no visible pixels");
        return Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
    }
    public static void Export(Bitmap source, Rectangle bounds, string path, int size, double coverage, bool background) {
        using (var output = new Bitmap(size, size, PixelFormat.Format32bppArgb))
        using (var graphics = Graphics.FromImage(output)) {
            graphics.Clear(background ? Color.White : Color.Transparent);
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            double scale = size * coverage / Math.Max(bounds.Width, bounds.Height);
            float width = (float)(bounds.Width * scale), height = (float)(bounds.Height * scale);
            graphics.DrawImage(source, new RectangleF((size - width) / 2, (size - height) / 2, width, height), bounds, GraphicsUnit.Pixel);
            output.Save(path, ImageFormat.Png);
        }
    }
}
'@

function Export-BrandAsset([string]$RelativePath, [int]$Size, [double]$Coverage, [bool]$Background = $false) {
    $destination = Join-Path $projectRoot $RelativePath
    New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
    [FinTrackBrandAssets]::Export($source, $bounds, $destination, $Size, $Coverage, $Background)
}

$source = [System.Drawing.Bitmap]::new((Join-Path $projectRoot 'assets/branding/fintrack-mark-v1.png'))
try {
    $bounds = [FinTrackBrandAssets]::Bounds($source)
    Export-BrandAsset 'ui/src/assets/fintrack-mark.png' 192 0.9
    Export-BrandAsset 'ui/public/favicon.png' 64 0.85
    Export-BrandAsset 'ui/public/apple-touch-icon.png' 180 0.65 $true
    Export-BrandAsset 'android/app/src/main/res/drawable-nodpi/fintrack_mark.png' 192 0.9
    Export-BrandAsset 'android/app/src/main/res/drawable-nodpi/fintrack_launcher_foreground.png' 432 0.58
} finally {
    $source.Dispose()
}
Write-Output 'Web and Android branding assets exported.'
