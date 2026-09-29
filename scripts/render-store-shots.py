#!/usr/bin/env python3
"""Compose les captures des stores : une légende en haut, la capture de
l'appareil en dessous, aux couleurs du site.

    scripts/render-store-shots.py ios       # apps/ios/appstore/captures → appstore/out/<locale>/
    scripts/render-store-shots.py android   # apps/android/play/captures → play/out/<locale>/ (+ icône, bannière)

Entrées : captures brutes nommées <lang>-<n>-<nom>.png (lang = fr, en, he).
Dépendances : Pillow, python-bidi (pip install pillow python-bidi) ; Pillow
sans libraqm ne sait pas mettre l'hébreu dans l'ordre, python-bidi s'en charge.
"""
import os
import sys
from pathlib import Path

from bidi import get_display
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "apps" / "ios" / "Shared" / "Fonts"

PAPER, PAPER_DARK = "#f7f3ea", "#16140f"
INK, INK2, ACCENT = "#1d1a15", "#5a544a", "#8d2b1c"
INK_DARK, INK2_DARK, ACCENT_DARK = "#e9e2d3", "#a89f8c", "#d8785c"

CAPTIONS = {
    "clock": {
        "fr": ("L’heure juive, à la rega près", "Date hébraïque · heure · chelek · rega"),
        "en": ("Jewish time, to the rega", "Hebrew date · hour · chelek · rega"),
        "he": ("זמן עברי, עד לרגע", "תאריך עברי · שעה · חלק · רגע"),
    },
    "dark": {
        "fr": ("Réglée sur la rotation de la Terre", "UT1, mesurée par l’IERS, pas l’horloge atomique"),
        "en": ("Set by the Earth’s rotation", "UT1 as measured by the IERS, not the atomic clock"),
        "he": ("מכוון לפי סיבוב כדור הארץ", "כפי שהוא נמדד, ולא לפי השעון האטומי"),
    },
    "luach": {
        "fr": ("Le luach, mois par mois", "Roch Hodech, fêtes, omer et molad du mois"),
        "en": ("The luach, month by month", "Rosh Chodesh, festivals, Omer and the month’s molad"),
        "he": ("הלוח, חודש אחר חודש", "ראש חודש, מועדים, ספירת העומר ומולד החודש"),
    },
    "fetes": {
        "fr": ("Les fêtes et les jeûnes", "Reports compris, en Israël ou en diaspora"),
        "en": ("Festivals and fasts", "Postponements included, in Israel or the diaspora"),
        "he": ("מועדים ותעניות", "כולל הדחיות, בארץ ובחוץ לארץ"),
    },
    "molad": {
        "fr": ("Le molad, au chelek près", "Compte à rebours, annonces et structure de l’année"),
        "en": ("The molad, to the chelek", "Countdown, announcements and the year’s structure"),
        "he": ("המולד, עד לחלק", "ספירה לאחור, הכרזות ומבנה השנה"),
    },
    "lire": {
        "fr": ("Un temps de rotation", "Le manifeste et la méthode, avec leurs sources"),
        "en": ("Rotation time", "The manifesto and the method, with their sources"),
        "he": ("זמן של סיבוב", "המניפסט והשיטה, עם מקורותיהם"),
    },
    "widgets": {
        "fr": ("La date hébraïque en un coup d’œil", "Widgets pour l’écran d’accueil et l’écran verrouillé"),
        "en": ("The Hebrew date at a glance", "Widgets for the home and lock screens"),
        "he": ("התאריך העברי במבט אחד", "ווידג׳טים למסך הבית ולמסך הנעילה"),
    },
    "widget": {
        "fr": ("La date hébraïque en un coup d’œil", "Un widget pour l’écran d’accueil"),
        "en": ("The Hebrew date at a glance", "A widget for your home screen"),
        "he": ("התאריך העברי במבט אחד", "ווידג׳ט למסך הבית"),
    },
}

PLATFORMS = {
    # iPhone 6,9 pouces ; Play : rapport au plus 2:1 (les captures 1080 × 2424 sont recadrées).
    "ios": dict(size=(1320, 2868), captures=ROOT / "apps/ios/appstore/captures",
                out=ROOT / "apps/ios/appstore/out", locales={"fr": "fr-FR", "en": "en-US", "he": "he"}),
    "android": dict(size=(1080, 2160), captures=ROOT / "apps/android/play/captures",
                    out=ROOT / "apps/android/play/out", locales={"fr": "fr-FR", "en": "en-US", "he": "iw-IL"}),
}


def font(lang, size, weight=400):
    name = "FrankRuhlLibre-hebrew.ttf" if lang == "he" else "EBGaramond-latin.ttf"
    f = ImageFont.truetype(str(FONTS / name), size)
    try:
        f.set_variation_by_axes([weight])
    except OSError:
        pass
    return f


HEBREW_CMAP = set(TTFont(str(FONTS / "FrankRuhlLibre-hebrew.ttf")).getBestCmap())


def runs(text, lang, f):
    """Découpe le texte (déjà dans l'ordre visuel) en suites d'une même police :
    le sous-ensemble hébreu n'a ni chiffres, ni ponctuation latine, ni point médian."""
    if lang != "he":
        return [(text, f)]
    latin = font("fr", f.size, 400)
    out = []
    for ch in text:
        g = f if ord(ch) in HEBREW_CMAP else latin
        if out and out[-1][1] is g:
            out[-1] = (out[-1][0] + ch, g)
        else:
            out.append((ch, g))
    return out


def width(draw, text, lang, f):
    return sum(draw.textlength(t, font=g) for t, g in runs(text, lang, f))


def fit(draw, text, lang, size, weight, max_width):
    """La plus grande taille (≤ size) qui tient sur la largeur."""
    while size > 20:
        f = font(lang, size, weight)
        if width(draw, text, lang, f) <= max_width:
            return f
        size -= 2
    return font(lang, size, weight)


def centered(draw, y, text, f, fill, page_width, lang="fr"):
    x = (page_width - width(draw, text, lang, f)) / 2
    for t, g in runs(text, lang, f):
        draw.text((x, y), t, font=g, fill=fill)
        x += draw.textlength(t, font=g)


def compose(shot: Path, lang: str, kind: str, size) -> Image.Image:
    W, H = size
    dark = kind == "dark"
    bg = PAPER_DARK if dark else PAPER
    ink, ink2, accent = (INK_DARK, INK2_DARK, ACCENT_DARK) if dark else (INK, INK2, ACCENT)
    im = Image.new("RGB", size, bg)
    d = ImageDraw.Draw(im)

    title, sub = CAPTIONS[kind][lang]
    if lang == "he":
        title, sub = get_display(title), get_display(sub)
    s = W / 1320
    ft = fit(d, title, lang, int(92 * s), 500, W * 0.88)
    fs = fit(d, sub, lang, int(46 * s), 400, W * 0.86)
    centered(d, int(170 * s), title, ft, ink, W, lang)
    centered(d, int(170 * s) + ft.size + int(34 * s), sub, fs, ink2, W, lang)
    rule_y = int(170 * s) + ft.size + fs.size + int(84 * s)
    d.line([(W * 0.44, rule_y), (W * 0.56, rule_y)], fill=accent, width=max(2, int(4 * s)))

    # La capture, en carte arrondie avec une ombre douce ; le bas déborde.
    src = Image.open(shot).convert("RGB")
    sw = int(W * 0.80)
    sh = int(src.height * sw / src.width)
    src = src.resize((sw, sh), Image.LANCZOS)
    top = rule_y + int(80 * s)
    radius = int(64 * s)
    mask = Image.new("L", (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, sw - 1, sh - 1], radius=radius, fill=255)
    shadow = Image.new("L", size, 0)
    ImageDraw.Draw(shadow).rounded_rectangle([(W - sw) // 2, top + int(18 * s), (W + sw) // 2, top + sh + int(18 * s)],
                                             radius=radius, fill=110 if not dark else 200)
    shadow = shadow.filter(ImageFilter.GaussianBlur(int(40 * s)))
    im.paste(Image.new("RGB", size, "#000000"), (0, 0), shadow)
    border = Image.new("RGB", (sw + 6, sh + 6), "#3a3428" if dark else "#d8d0bf")
    bmask = Image.new("L", border.size, 0)
    ImageDraw.Draw(bmask).rounded_rectangle([0, 0, sw + 5, sh + 5], radius=radius + 3, fill=255)
    im.paste(border, ((W - sw) // 2 - 3, top - 3), bmask)
    im.paste(src, ((W - sw) // 2, top), mask)
    return im


def render(platform: str) -> None:
    cfg = PLATFORMS[platform]
    shots = sorted(cfg["captures"].glob("*.png"))
    if not shots:
        sys.exit(f"aucune capture dans {cfg['captures']}")
    for shot in shots:
        lang, n, kind = shot.stem.split("-", 2)
        out = cfg["out"] / cfg["locales"][lang]
        out.mkdir(parents=True, exist_ok=True)
        compose(shot, lang, kind, cfg["size"]).save(out / f"{n}-{kind}.png", optimize=True)
        print(f"{out.relative_to(ROOT)}/{n}-{kind}.png")
    if platform == "android":
        android_graphics(cfg["out"])


def android_graphics(out: Path) -> None:
    """Icône 512 et bannière 1024 × 500 de la fiche Play."""
    icon = Image.open(ROOT / "apps/ios/Zman/Assets.xcassets/AppIcon.appiconset/icon-1024.png").convert("RGB")
    icon.resize((512, 512), Image.LANCZOS).save(out / "icon-512.png")
    W, H = 1024, 500
    im = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(im)
    he = font("he", 190, 700)
    centered(d, 70, get_display("זמן"), he, INK, W, "he")
    sub = font("en", 40, 400)
    centered(d, 330, "Jewish time · temps juif", sub, INK2, W)
    d.line([(W * 0.44, 305), (W * 0.56, 305)], fill=ACCENT, width=3)
    im.save(out / "feature-1024x500.png")
    print(f"{out.relative_to(ROOT)}/icon-512.png, feature-1024x500.png")


if __name__ == "__main__":
    for p in sys.argv[1:] or ["ios", "android"]:
        render(p)
