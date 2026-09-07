#!/usr/bin/env python3
"""Génère web/static/og.png (1200×630) à partir des polices embarquées.
Dépendances : fontTools, brotli, Pillow (pip install fonttools brotli pillow)."""
import os, tempfile
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(__file__), "..")
FONTS = os.path.join(ROOT, "web", "static", "fonts")
OUT = os.path.join(ROOT, "web", "static", "og.png")
W, H = 1200, 630

def ttf(name):
    path = os.path.join(tempfile.gettempdir(), name + ".ttf")
    f = TTFont(os.path.join(FONTS, name + ".woff2")); f.flavor = None; f.save(path)
    return path

im = Image.new("RGB", (W, H), "#f7f3ea"); d = ImageDraw.Draw(im)
he = ImageFont.truetype(ttf("FrankRuhlLibre-hebrew"), 250); he.set_variation_by_axes([700])
it = ImageFont.truetype(ttf("EBGaramond-italic-latin"), 44)
sc = ImageFont.truetype(ttf("EBGaramond-latin"), 26); sc.set_variation_by_axes([500])
d.line([(80, 100), (1120, 100)], fill="#d8d0bf", width=2)
d.line([(80, 530), (1120, 530)], fill="#d8d0bf", width=2)

def center(text, font, y, fill):
    w = d.textlength(text, font=font); d.text(((W - w) / 2, y), text, font=font, fill=fill)

center("ןמז", he, 120, "#1d1a15")  # « זמן » dessiné de droite à gauche sans libraqm
center("Un temps de rotation, pas un temps d’atome", it, 400, "#5a544a")

def spaced(text, font, y, fill, gap):
    widths = [d.textlength(c, font=font) for c in text]
    x = (W - sum(widths) - gap * (len(text) - 1)) / 2
    for c, w in zip(text, widths):
        d.text((x, y), c, font=font, fill=fill); x += w + gap

spaced("REGA’IM DEPUIS L’EPOCH  ·  UT1", sc, 468, "#8d2b1c", 5)
im.save(OUT, optimize=True)
print(OUT, im.size)
