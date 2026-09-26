#!/usr/bin/env python3
"""Génère les icônes des apps à partir du favicon (ז en Frank Ruhl Libre gras,
couleur accent, sur papier) et les polices TTF embarquées dans les apps.
Dépendances : fontTools, brotli, Pillow (pip install fonttools brotli pillow)."""
import os
from fontTools.ttLib import TTFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from PIL import Image, ImageChops, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..")
FONTS = os.path.join(ROOT, "web", "static", "fonts")
IOS = os.path.join(ROOT, "apps", "ios")
ANDROID = os.path.join(ROOT, "apps", "android", "app", "src", "main", "res")
PAPER, ACCENT = "#f7f3ea", "#8d2b1c"

# Polices TTF pour les apps (iOS et Android ne lisent pas le woff2).
for name, ios_name, android_name in [
    ("EBGaramond-latin", "EBGaramond-latin.ttf", "eb_garamond.ttf"),
    ("EBGaramond-italic-latin", "EBGaramond-italic-latin.ttf", "eb_garamond_italic.ttf"),
    ("FrankRuhlLibre-hebrew", "FrankRuhlLibre-hebrew.ttf", "frank_ruhl_libre.ttf"),
]:
    f = TTFont(os.path.join(FONTS, name + ".woff2")); f.flavor = None
    f.save(os.path.join(IOS, "Shared", "Fonts", ios_name))
    f.save(os.path.join(ANDROID, "font", android_name))

# Contour du glyphe ז, instancié en gras (wght 700).
from fontTools.varLib.instancer import instantiateVariableFont
font = instantiateVariableFont(TTFont(os.path.join(FONTS, "FrankRuhlLibre-hebrew.woff2")), {"wght": 700})
gs = font.getGlyphSet()
glyph = gs[font.getBestCmap()[ord("ז")]]
bp = BoundsPen(gs); glyph.draw(bp)
xmin, ymin, xmax, ymax = bp.bounds


def path(size, height_ratio):
    """Chemin SVG du glyphe centré dans un carré de côté size, hauteur = ratio × size."""
    s = size * height_ratio / (ymax - ymin)
    dx = size / 2 - s * (xmin + xmax) / 2
    dy = size / 2 + s * (ymin + ymax) / 2
    pen = SVGPathPen(gs)
    glyph.draw(TransformPen(pen, (s, 0, 0, -s, dx, dy)))
    return pen.getCommands()


def polygons(size, height_ratio):
    """Contours aplatis (pour Pillow)."""
    from fontTools.pens.recordingPen import DecomposingRecordingPen
    from fontTools.pens.basePen import BasePen
    s = size * height_ratio / (ymax - ymin)
    dx = size / 2 - s * (xmin + xmax) / 2
    dy = size / 2 + s * (ymin + ymax) / 2
    polys = []

    class Flat(BasePen):
        def _moveTo(self, p): polys.append([p])
        def _lineTo(self, p): polys[-1].append(p)
        def _curveToOne(self, p1, p2, p3):
            p0 = polys[-1][-1]
            for i in range(1, 17):
                t = i / 16
                polys[-1].append(tuple((1-t)**3*a + 3*(1-t)**2*t*b + 3*(1-t)*t**2*c + t**3*d for a, b, c, d in zip(p0, p1, p2, p3)))
        def _closePath(self): pass
    glyph.draw(TransformPen(Flat(gs), (s, 0, 0, -s, dx, dy)))
    return polys


# iOS : 1024 × 1024 opaque, le système arrondit les coins.
N, SS = 1024, 4
im = Image.new("RGB", (N * SS, N * SS), PAPER)
mask = Image.new("L", im.size, 0)
for poly in polygons(N * SS, 0.56):
    # Union des contours : ז n’a pas de contre-forme, et ses contours se chevauchent.
    layer = Image.new("L", im.size, 0); ImageDraw.Draw(layer).polygon(poly, fill=255)
    mask = ImageChops.lighter(mask, layer)
im.paste(Image.new("RGB", im.size, ACCENT), (0, 0), mask)
im = im.resize((N, N), Image.LANCZOS)
im.save(os.path.join(IOS, "Zman", "Assets.xcassets", "AppIcon.appiconset", "icon-1024.png"))

# Android : avant-plan adaptatif 108 dp (zone sûre de 66 dp au centre).
vector = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Généré par scripts/gen-app-icons.py : ז, Frank Ruhl Libre 700. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="@color/accent_icon"
        android:fillType="nonZero"
        android:pathData="{path(108, 0.38)}" />
</vector>
"""
open(os.path.join(ANDROID, "drawable", "ic_launcher_foreground.xml"), "w").write(vector)
print("ok")
