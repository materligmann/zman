#!/usr/bin/env bash
# Déploiement idempotent sur Cloud Run.
#
# Variables (toutes optionnelles) :
#   GCP_PROJECT   projet GCP                (défaut essai-478723)
#   GCP_REGION    région Cloud Run          (défaut europe-west1)
#   SERVICE       nom du service            (défaut zman)
#   IERS_BUCKET   bucket GCS du cache IERS  (défaut <projet>-zman-iers)
#   DOMAIN        domaine personnalisé      (défaut aucun ; l'URL *.run.app est utilisée)
#   REPO_URL      URL du dépôt affichée     (défaut https://github.com/materligmann/zman)
#
# Relancer le script redéploie la version courante du code ; toutes les
# étapes de création vérifient d'abord l'existence de la ressource.
set -euo pipefail
cd "$(dirname "$0")"

PROJECT=${GCP_PROJECT:-essai-478723}
REGION=${GCP_REGION:-europe-west1}
SERVICE=${SERVICE:-zman}
BUCKET=${IERS_BUCKET:-${PROJECT}-zman-iers}
DOMAIN=${DOMAIN:-}
REPO_URL=${REPO_URL:-https://github.com/materligmann/zman}

log() { printf '\033[1m» %s\033[0m\n' "$*"; }

log "Projet $PROJECT, région $REGION, service $SERVICE"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com storage.googleapis.com --project "$PROJECT" --quiet

if ! gcloud storage buckets describe "gs://$BUCKET" --project "$PROJECT" >/dev/null 2>&1; then
  log "Création du bucket gs://$BUCKET (cache IERS partagé)"
  gcloud storage buckets create "gs://$BUCKET" --project "$PROJECT" --location "$REGION" \
    --uniform-bucket-level-access --public-access-prevention
else
  log "Bucket gs://$BUCKET présent"
fi

PROJECT_NUMBER=$(gcloud projects describe "$PROJECT" --format='value(projectNumber)')
SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
log "Accès objectAdmin sur le bucket pour $SA"
gcloud storage buckets add-iam-policy-binding "gs://$BUCKET" \
  --member "serviceAccount:$SA" --role roles/storage.objectAdmin --project "$PROJECT" --quiet >/dev/null

# Depuis 2024, Cloud Build s'exécute avec le compte de service Compute par
# défaut, qui n'a plus les droits de build d'office : on les lui donne.
log "Rôle cloudbuild.builds.builder pour $SA"
gcloud projects add-iam-policy-binding "$PROJECT" \
  --member "serviceAccount:$SA" --role roles/cloudbuild.builds.builder --quiet >/dev/null

ENV_VARS="IERS_CACHE_GCS=${BUCKET}/finals.all,IERS_CACHE_PATH=/tmp/finals.all,REPO_URL=${REPO_URL}"
if [ -n "$DOMAIN" ]; then
  ENV_VARS="${ENV_VARS},BASE_URL=https://${DOMAIN}"
fi

log "Build (Cloud Build, Dockerfile) et déploiement"
gcloud run deploy "$SERVICE" --source . --project "$PROJECT" --region "$REGION" \
  --allow-unauthenticated --port 8080 --memory 256Mi --cpu 1 --concurrency 80 \
  --min-instances 0 --max-instances 3 --timeout 30 --set-env-vars "$ENV_VARS" --quiet

URL=$(gcloud run services describe "$SERVICE" --project "$PROJECT" --region "$REGION" --format 'value(status.url)')
log "Service : $URL"

if [ -n "$DOMAIN" ]; then
  if ! gcloud beta run domain-mappings describe --domain "$DOMAIN" --project "$PROJECT" --region "$REGION" >/dev/null 2>&1; then
    log "Mapping du domaine $DOMAIN"
    gcloud beta run domain-mappings create --service "$SERVICE" --domain "$DOMAIN" --project "$PROJECT" --region "$REGION" --quiet
  fi
  log "Enregistrements DNS à créer pour $DOMAIN :"
  gcloud beta run domain-mappings describe --domain "$DOMAIN" --project "$PROJECT" --region "$REGION" \
    --format 'table(status.resourceRecords[].type,status.resourceRecords[].rrdata)'
fi

log "Vérification"
curl -sf "$URL/api/health" && echo
