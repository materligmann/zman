#!/usr/bin/env python3
"""Convertit les pages du site (web/content/<lang>/*.html) en blocs JSON que
les apps affichent nativement : apps/ios/Zman/Texts/<lang>-<page>.json et,
les mêmes fichiers, apps/android/app/src/main/assets/texts/<lang>-<page>.json.

    scripts/gen-app-texts.py

Blocs : h1, lede, h2, h3, p, ul, ol, pre, table, sources. Le texte en ligne
est du Markdown (AttributedString sur iOS, AnnotatedString sur Android) : *italique*, **gras**, `code`, [liens](url).
Les appels de note deviennent des chiffres en exposant ; la figure SVG de la
méthode est omise (la liste qui la suit dit la même chose).
"""
import json
import re
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "web" / "content"
OUTS = [
    ROOT / "apps" / "ios" / "Zman" / "Texts",
    ROOT / "apps" / "android" / "app" / "src" / "main" / "assets" / "texts",
]
PAGES = ["manifeste", "methode", "api", "confidentialite"]
BASE = "https://zman.technology"
REPO = "https://github.com/materligmann/zman"
SUPERSCRIPT = str.maketrans("0123456789er", "⁰¹²³⁴⁵⁶⁷⁸⁹ᵉʳ")


def md_escape(s):
    return re.sub(r"([\\`*_\[\]])", r"\\\1", s)


class Page(HTMLParser):
    def __init__(self, lang):
        super().__init__(convert_charrefs=True)
        self.lang = lang
        self.blocks = []
        self.buf = None          # texte en ligne du bloc courant
        self.kind = None
        self.list = None         # (type, items) pour ul/ol/sources
        self.table = None        # lignes
        self.row = None
        self.skip = 0            # profondeur dans <svg>/<figure>
        self.inline = []         # pile des balises en ligne ouvertes
        self.code = 0
        self.pre = False
        self.sources = False
        self.item_id = None

    # -- tampon en ligne ------------------------------------------------------
    def start_buf(self, kind):
        self.kind, self.buf = kind, ""

    def flush(self):
        text = re.sub(r"[ \t\n]+", " ", self.buf).strip() if not self.pre else self.buf.strip("\n")
        kind, self.buf, self.kind = self.kind, None, None
        return kind, text

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("svg", "figure"):
            self.skip += 1
        if self.skip:
            return
        cls = a.get("class", "")
        if tag == "section" and "sources" in cls:
            self.sources = True
        elif tag in ("h1", "h2", "h3"):
            self.start_buf(tag)
        elif tag == "p":
            self.start_buf("lede" if "lede" in cls else "p")
        elif tag in ("ul", "ol"):
            self.list = ("sources" if self.sources else tag, [])
        elif tag == "li":
            self.item_id = a.get("id")
            self.start_buf("li")
        elif tag == "pre":
            self.pre = True
            self.start_buf("pre")
        elif tag == "table":
            self.table = []
        elif tag == "tr":
            self.row = []
        elif tag in ("td", "th"):
            self.start_buf(tag)
        elif self.buf is not None:
            self.inline.append((tag, a))
            if tag == "code" and not self.pre:
                self.code += 1
                self.buf += "`"
            elif tag in ("em", "i"):
                self.buf += "*"
            elif tag in ("b", "strong"):
                self.buf += "**"
            elif tag == "a" and not a.get("href", "").startswith("#"):
                self.buf += "["
            elif tag == "br":
                self.buf += "\n"
                self.inline.pop()

    def handle_endtag(self, tag):
        if tag in ("svg", "figure"):
            self.skip -= 1
            return
        if self.skip:
            return
        if tag in ("h1", "h2", "h3", "p") and self.buf is not None:
            kind, text = self.flush()
            if text:
                self.blocks.append({"t": kind, "s": text})
        elif tag == "li" and self.buf is not None:
            _, text = self.flush()
            self.list[1].append(text)
        elif tag in ("ul", "ol") and self.list:
            kind, items = self.list
            if kind == "sources":
                self.blocks.append({"t": "sources", "items": items})
            else:
                self.blocks.append({"t": kind, "items": items})
            self.list = None
        elif tag == "pre":
            _, text = self.flush()
            self.pre = False
            self.blocks.append({"t": "pre", "s": text})
        elif tag in ("td", "th") and self.buf is not None:
            _, text = self.flush()
            self.row.append(text)
        elif tag == "tr":
            self.table.append(self.row)
            self.row = None
        elif tag == "table":
            self.blocks.append({"t": "table", "rows": self.table})
            self.table = None
        elif tag == "section":
            self.sources = False
        elif self.inline and self.inline[-1][0] == tag:
            t, a = self.inline.pop()
            if tag == "code" and not self.pre:
                self.code -= 1
                self.buf += "`"
            elif tag in ("em", "i"):
                self.buf += "*"
            elif tag in ("b", "strong"):
                self.buf += "**"
            elif tag == "a":
                href = a.get("href", "")
                if not href.startswith("#"):
                    self.buf += f"]({self.url(href)})"

    def url(self, href):
        href = href.replace("{{.Base}}", BASE).replace("{{.Repo}}", REPO)
        if href.startswith("/"):
            # Lien interne : zman://<page> si la page est dans l'app.
            page = href.strip("/").split("/")[-1]
            return f"zman://{page}" if page in PAGES else f"{BASE}/{self.lang}{href}"
        return href

    def handle_data(self, data):
        if self.skip or self.buf is None:
            return
        data = data.replace("{{.Base}}", BASE).replace("{{.Repo}}", REPO)
        m = re.fullmatch(r'\{\{index \.Examples "([a-z-]+)"\}\}', data.strip())
        if m:
            data = (ROOT / "web" / "examples" / f"{m.group(1)}.txt").read_text().strip()
        if any(t == "sup" for t, _ in self.inline):
            self.buf += data.translate(SUPERSCRIPT)
            return
        if self.pre or self.code:
            self.buf += data
        else:
            self.buf += md_escape(data)


def main():
    for out in OUTS:
        out.mkdir(parents=True, exist_ok=True)
    for lang in ("fr", "en", "he"):
        for page in PAGES:
            p = Page(lang)
            p.feed((SRC / lang / f"{page}.html").read_text())
            text = json.dumps(p.blocks, ensure_ascii=False, indent=1) + "\n"
            for out in OUTS:
                (out / f"{lang}-{page}.json").write_text(text)
            print(f"{lang}-{page}.json : {len(p.blocks)} blocs")


if __name__ == "__main__":
    main()
