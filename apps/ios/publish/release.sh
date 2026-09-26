#!/usr/bin/env bash
# Archive, signe et envoie Zman (app + widget) sur App Store Connect.
#
#   publish/release.sh            # archive + envoi (version/build de project.yml)
#   publish/release.sh --archive  # archive seulement, sans envoi
#
# Prérequis : publish/AuthKey_<KEY_ID>.p8 et publish/.env (ASC_KEY_ID, ASC_ISSUER_ID).
# Rôle Admin : Xcode gère certificat, profils et groupe d'apps dans le cloud
# (-allowProvisioningUpdates), rien à installer dans le trousseau.
# Incrémenter CURRENT_PROJECT_VERSION dans project.yml avant chaque envoi.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source publish/.env; set +a
KEY="publish/AuthKey_${ASC_KEY_ID}.p8"
[ -f "$KEY" ] || { echo "clé absente : $KEY"; exit 1; }

AUTH=(-allowProvisioningUpdates -authenticationKeyPath "$PWD/$KEY"
      -authenticationKeyID "$ASC_KEY_ID" -authenticationKeyIssuerID "$ASC_ISSUER_ID")
ARCHIVE="build/Zman.xcarchive"

echo "▸ xcodegen"
xcodegen generate --quiet
VERSION=$(grep 'MARKETING_VERSION:' project.yml | head -1 | tr -d '" ' | cut -d: -f2)
BUILD=$(grep 'CURRENT_PROJECT_VERSION:' project.yml | head -1 | tr -d '" ' | cut -d: -f2)
echo "▸ archive Zman $VERSION ($BUILD)"
rm -rf "$ARCHIVE"
xcodebuild -project Zman.xcodeproj -scheme Zman -configuration Release \
  -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" archive "${AUTH[@]}" \
  | grep -E "error:|warning: .*signing|\*\* ARCHIVE" || true
[ -d "$ARCHIVE" ] || { echo "archive absente : échec"; exit 1; }
GOT=$(/usr/libexec/PlistBuddy -c "Print :CFBundleVersion" "$ARCHIVE/Products/Applications/Zman.app/Info.plist")
[ "$GOT" = "$BUILD" ] || { echo "✗ l'archive porte le build $GOT, project.yml dit $BUILD"; exit 1; }
[ "${1:-}" = "--archive" ] && { echo "✓ archive prête : $ARCHIVE"; exit 0; }

echo "▸ envoi à App Store Connect"
LOG=build/export.log
xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist publish/ExportOptions.plist \
  -exportPath build/export "${AUTH[@]}" > "$LOG" 2>&1 || true
grep -E "error:|Upload|EXPORT" "$LOG" || true
grep -q "EXPORT SUCCEEDED" "$LOG" || { echo "✗ envoi échoué (journal : $LOG)"; exit 1; }
echo "✓ Zman $VERSION ($BUILD) envoyé ; traitement par Apple en quelques minutes, puis : node publish/asc.mjs release"
