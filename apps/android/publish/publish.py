#!/usr/bin/env python3
"""Publie Zman sur Google Play par l'API Android Publisher.

    .venv/bin/python publish.py listing            # fiche : textes + visuels (fr-FR, en-US)
    .venv/bin/python publish.py upload [--track internal|alpha|beta|production] [--notes "…"] [--draft]
    .venv/bin/python publish.py status             # pistes et versions en ligne

Prérequis, une fois : l'app créée dans la Play Console, et le compte de service
(publish/play-service-account.json) invité dans « Utilisateurs et autorisations »
avec les droits de publication et de gestion de la fiche. Voir ../play/README.md.

Tout passe par une « edit » : on ouvre, on modifie, on commit — Google n'applique
rien avant le commit, et une edit non commitée est simplement abandonnée.

Première release d'une app jamais publiée : Google exige un brouillon (--draft),
que l'on envoie pour examen depuis la console (Production → Examiner la
version → Lancer le déploiement). Ensuite, les releases partent directement.
"""

import argparse
import mimetypes
import sys
from pathlib import Path

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

ROOT = Path(__file__).resolve().parent
ANDROID = ROOT.parent
PLAY = ANDROID / "play"
PACKAGE = "studiocentmoinshuit.zman"
SA_KEY = ROOT / "play-service-account.json"
AAB = ANDROID / "app/build/outputs/bundle/release/app-release.aab"

# Locales de la fiche : dossier play/listing/<locale>/ avec title.txt,
# short.txt, full.txt, et play/out/<locale>/ pour les captures.
LOCALES = ["fr-FR", "en-US", "iw-IL"]


def service():
    creds = service_account.Credentials.from_service_account_file(
        str(SA_KEY), scopes=["https://www.googleapis.com/auth/androidpublisher"])
    return build("androidpublisher", "v3", credentials=creds, cache_discovery=False)


def commit(edits, edit_id: str) -> bool:
    """Commit l'edit. Renvoie True si Google l'a envoyée en examen lui-même.

    Quand une version est en cours d'examen ou vient d'être refusée, Google
    refuse d'envoyer automatiquement : il faut commiter avec
    `changesNotSentForReview`, puis cliquer « Envoyer pour examen » dans la
    console (Play Console → Présentation de la publication). On le fait pour
    l'utilisateur plutôt que d'échouer, et on le lui dit.
    """
    try:
        edits.commit(packageName=PACKAGE, editId=edit_id).execute()
        return True
    except HttpError as e:
        if e.resp.status != 400 or b"changesNotSentForReview" not in e.content:
            raise
        edits.commit(packageName=PACKAGE, editId=edit_id, changesNotSentForReview=True).execute()
        print("ATTENTION : Google n'a pas envoyé ces changements en examen (une version est en "
              "examen ou vient d'être refusée). Dans la Play Console → Présentation de la "
              "publication → « Envoyer les modifications pour examen ».")
        return False


def read(path: Path, limit: int) -> str:
    text = path.read_text(encoding="utf-8").strip()
    if len(text) > limit:
        sys.exit(f"{path.name} : {len(text)} caractères, maximum {limit}")
    return text


def cmd_listing(args) -> None:
    api = service()
    edits = api.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]

    # Coordonnées de la fiche (Store settings → Coordonnées).
    edits.details().update(packageName=PACKAGE, editId=edit_id, body={
        "defaultLanguage": "fr-FR",
        "contactEmail": "hello@100-8.studio",
        "contactWebsite": "https://zman.technology",
    }).execute()
    print("coordonnées : hello@100-8.studio, https://zman.technology")

    for locale in LOCALES:
        folder = PLAY / "listing" / locale
        edits.listings().update(
            packageName=PACKAGE, editId=edit_id, language=locale,
            body={
                "language": locale,
                "title": read(folder / "title.txt", 30),
                "shortDescription": read(folder / "short.txt", 80),
                "fullDescription": read(folder / "full.txt", 4000),
            }).execute()
        print(f"fiche {locale} : textes envoyés")

        # Visuels : on remplace tout (deleteall puis upload), pour que la fiche
        # reflète exactement play/out/ — pas d'ancienne capture qui traîne.
        for kind, files in {
            "icon": [PLAY / "out" / "icon-512.png"],
            "featureGraphic": [PLAY / "out" / "feature-1024x500.png"],
            "phoneScreenshots": sorted((PLAY / "out" / locale).glob("*.png")),
        }.items():
            files = [f for f in files if f.exists()]
            if not files:
                continue
            edits.images().deleteall(packageName=PACKAGE, editId=edit_id,
                                     language=locale, imageType=kind).execute()
            for f in files:
                edits.images().upload(
                    packageName=PACKAGE, editId=edit_id, language=locale, imageType=kind,
                    media_body=MediaFileUpload(str(f), mimetype=mimetypes.guess_type(f.name)[0]),
                ).execute()
            print(f"fiche {locale} : {kind} ({len(files)})")

    commit(edits, edit_id)
    print("fiche mise à jour ✓")


def cmd_upload(args) -> None:
    if not AAB.exists():
        sys.exit(f"AAB introuvable : {AAB} — lancer ./gradlew bundleRelease")
    api = service()
    edits = api.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]

    bundle = edits.bundles().upload(
        packageName=PACKAGE, editId=edit_id,
        media_body=MediaFileUpload(str(AAB), mimetype="application/octet-stream", resumable=True),
    ).execute()
    code = bundle["versionCode"]
    print(f"AAB envoyé : versionCode {code}")

    # Sans --notes, les nouveautés viennent de play/listing/<locale>/whatsnew.txt :
    # le nouveautés de la version, par langue.
    def notes_for(loc: str) -> str:
        if args.notes:
            return args.notes
        f = PLAY / "listing" / loc / "whatsnew.txt"
        return f.read_text().strip() if f.exists() else ""
    notes = [{"language": loc, "text": notes_for(loc)} for loc in LOCALES if notes_for(loc)]
    edits.tracks().update(
        packageName=PACKAGE, editId=edit_id, track=args.track,
        body={"track": args.track, "releases": [{
            "versionCodes": [str(code)],
            "status": "draft" if args.draft else "completed",
            "releaseNotes": notes,
        }]}).execute()
    commit(edits, edit_id)
    if args.draft:
        print(f"version {code} en BROUILLON sur la piste « {args.track} » : à envoyer pour examen dans la console ✓")
    else:
        print(f"version {code} publiée sur la piste « {args.track} » ✓")


def cmd_status(args) -> None:
    api = service()
    edits = api.edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    tracks = edits.tracks().list(packageName=PACKAGE, editId=edit_id).execute()
    for t in tracks.get("tracks", []):
        for r in t.get("releases", []):
            print(f"{t['track']:12} {r.get('status'):10} versionCodes={r.get('versionCodes')} "
                  f"{r.get('name', '')}")
    if not tracks.get("tracks"):
        print("aucune version sur aucune piste")
    edits.delete(packageName=PACKAGE, editId=edit_id).execute()


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("listing").set_defaults(fn=cmd_listing)
    up = sub.add_parser("upload")
    up.add_argument("--track", default="internal", choices=["internal", "alpha", "beta", "production"])
    up.add_argument("--notes", default="")
    up.add_argument("--draft", action="store_true", help="release en brouillon (obligatoire pour la toute première)")
    up.set_defaults(fn=cmd_upload)
    sub.add_parser("status").set_defaults(fn=cmd_status)
    args = p.parse_args()
    if not SA_KEY.exists():
        sys.exit(f"clé du compte de service absente : {SA_KEY}")
    args.fn(args)


if __name__ == "__main__":
    main()
