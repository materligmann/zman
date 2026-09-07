# zman — serveur de temps juif

Un serveur HTTP en Go qui expose un timestamp en **temps juif**, détaché du
calendrier grégorien, de l'UTC et de la seconde SI, et un site public
trilingue (français, anglais, hébreu) servi par le même binaire. Le timestamp
fondamental est un entier : le nombre de **rega'im** (רגעים) écoulés depuis
l'epoch.

```
$ curl -s https://zman.technology/api/now
{"rega":4163021922875,"day":2113294,"hour":22,"chelek":8,"rega_in_chelek":27,
 "date":{"year":5786,"month":6,"month_name":"Elul","month_name_he":"אלול","day":25},
 "weekday":1,"weekday_name_he":"יום שני",
 "clock":{"source":"iers","dut1_age_days":4.6,"predicted":true}}
```

## Pourquoi UT1 et pas l'atome

Un jour halachique est une rotation réelle de la Terre, quelle que soit sa
durée. Le temps de référence est donc **UT1**, l'échelle qui suit la rotation
terrestre telle que mesurée par l'IERS, et non le temps atomique (TAI/UTC).
Les subdivisions du jour — heures, chalakim, rega'im — sont des
*proportions* du jour, pas des durées fixes : quand la Terre ralentit,
le rega s'allonge avec elle. Le manifeste du site (`/manifeste`) développe
l'argument, sources à l'appui.

Concrètement : `UT1 = UTC + DUT1`, où DUT1 est lu dans `finals2000A.all`
(Bulletin A de l'IERS), interpolé linéairement à l'instant courant.

## Unités

| Unité | Définition |
|---|---|
| 1 jour | 24 heures, à partir de **18:00 temps moyen de Jérusalem** (convention du calendrier fixe) |
| 1 heure | 1 080 chalakim (חלקים) — Rambam, *Hilchot Kiddush HaChodesh* 6:2 |
| 1 chelek | 76 rega'im (רגעים) — *ibid.* 10:1 |
| 1 jour | 25 920 chalakim = **1 969 920 rega'im**, par définition |

À titre indicatif seulement, 1 chelek ≈ 3⅓ s et 1 rega ≈ 43,86 ms sur un
jour moyen. Ces équivalences ne sont utilisées nulle part dans l'API.

> **Note sur le rega.** Berachot 7a définit le rega comme 1/58 888 d'heure.
> On retient la définition du Rambam (1/76 de chelek) parce qu'elle
> subdivise le chelek en entier, ce qui rend le timestamp entier et
> l'arithmétique exacte.

## Epoch

L'epoch (rega 0) est le **début de la semaine du molad Tohu** : le samedi
soir 18:00 (temps moyen de Jérusalem) qui précède le molad BaHaRaD
(ב״ד ר״ד). Le jour 0 est ce dimanche, le jour 1 est le lundi, qui est
aussi le **1 Tishrei de l'an 1**.

```
molad Tohu = jour 2 (lundi), 5 h, 204 ch
           = 1 × 25 920 + 5 × 1 080 + 204 = 31 524 chalakim
           = 2 395 824 rega'im
```

Le numéro de jour `d` est lié au *fixed date* de Reingold & Dershowitz par
`RD = d − 1 373 428` : c'est ainsi que le pont vers l'horloge système est
calibré (voir `pkg/clock`), et c'est vérifié par des tests croisés.

## Architecture

```
cmd/server/     main, config, câblage site + API
pkg/rega/       type Rega int64, conversions, constantes
pkg/luach/      calendrier fixe, arithmétique pure sur numéros de jours
pkg/clock/      interface Clock + implémentation UT1 (seul pont seconde/UTC → rega)
pkg/iers/       finals.all : téléchargement, parsing, cache disque, cache GCS, interpolation DUT1
pkg/api/        handlers HTTP sous /api/, JSON et texte, CORS, rate limiting
pkg/web/        site : templates, langues, assets, sitemap, en-têtes de sécurité
web/            contenu embarqué (embed.FS) : templates/, content/{fr,en,he}/, i18n/, static/, examples/
```

Un seul binaire, un seul process : `/api/*` est l'API, tout le reste est le
site. Les anciens chemins racine (`/now`, `/rega/…`, `/health`) répondent
par une redirection 301 vers `/api/…`.

## Endpoints de l'API

JSON par défaut ; `Accept: text/plain` (ou `?format=text`) pour du texte.
CORS ouvert, `Cache-Control: max-age=1` sur `/api/now`, rate limiting par IP.

| Endpoint | Réponse |
|---|---|
| `GET /api/now` | instant courant : rega, jour, heure, chelek, rega, date, jour de la semaine, qualité de l'horloge |
| `GET /api/rega/{n}` | décode un entier en date/heure (date `null` pour un jour < 1) |
| `GET /api/date/{year}/{month}/{day}` | `start` et `end` (exclusif) du jour, en rega'im |
| `GET /api/molad/{year}/{month}` | molad du mois, en rega et en (jour, heure, chelek) |
| `GET /api/year/{year}` | longueur, embolismique ou non, Rosh Hashana, mois et longueurs |
| `GET /api/health` | état du cache IERS ; 503 si dégradé |

Mois : numérotation biblique, Nissan = 1 … Adar = 12, Adar II = 13. Dans une
année embolismique, le mois 12 est Adar I. Jours de la semaine : dimanche = 0.

La page `/api` du site documente chaque endpoint avec une réponse réelle,
générée par `make examples` (qui lance un serveur local et interroge chaque
route ; les fichiers résultants sont dans `web/examples/` et embarqués).

## Le site

- `/` : l'horloge. Le navigateur interroge `/api/now` toutes les 10 s et
  interpole localement entre deux réponses à la cadence moyenne d'un rega.
  Aucune date grégorienne ni heure civile sur cette page (un test le vérifie).
- `/manifeste` : le pourquoi, ~2 100 mots en français, traduit en anglais et
  en hébreu, sources liées.
- `/methode` : epoch, dehiyot, chaîne de mesure (schéma SVG inline), prédictions, tests.
- `/api` : documentation de l'API.

Langues : `fr` (défaut), `en`, `he`. La langue est choisie par le cookie
`lang`, sinon par `Accept-Language`. Le sélecteur envoie `?lang=xx`, qui pose
le cookie et redirige. Les URL explicites `/fr/…`, `/en/…`, `/he/…` existent
pour le partage et le sitemap (avec `hreflang`). L'hébreu est servi avec
`<html lang="he" dir="rtl">` et une feuille de style en propriétés logiques.

Pas de framework, pas de build : HTML + CSS + un fichier JS vanilla, le tout
dans `embed.FS`. Polices auto-hébergées (pas d'appel à Google Fonts).

## « Détaché des nations », concrètement

La seconde SI, l'UTC et le grégorien n'existent que dans **deux packages** :

- `pkg/iers` lit `finals.all`, indexé par MJD, et fournit DUT1 en secondes ;
- `pkg/clock` lit l'horloge système, ajoute DUT1 et la longitude de
  Jérusalem (35,2137° E → +2 h 20 min 51,288 s), et convertit la durée
  écoulée depuis l'epoch en rega'im par arithmétique entière.

Tout le reste (`pkg/rega`, `pkg/luach`, `pkg/api`, `pkg/web`) ne manipule
que des entiers de rega'im, de jours, d'heures et de chalakim. Un test
(`TestNoForeignUnits`) vérifie que les réponses de l'API ne contiennent ni
champ ni mot `unix`, `utc`, `iso`, `gregorian`, `seconds`. L'interface
`clock.Clock` (`Now()`, `Quality()`) est le seul contrat vu par l'API :
elle est prévue pour être remplacée par un capteur solaire ou stellaire sans
toucher au reste.

## Lancer

```
go run ./cmd/server          # http://localhost:8080
make test                    # go vet + go test ./...
make examples                # régénère web/examples/ (réponses réelles de la page /api)
make og                      # régénère web/static/og.png (python3 + fontTools + Pillow)
```

Variables d'environnement :

| Variable | Défaut | Rôle |
|---|---|---|
| `PORT` | `8080` | port d'écoute |
| `BASE_URL` | déduite de la requête | URL publique (canonical, sitemap, OG) |
| `REPO_URL` | `https://github.com/materligmann/zman` | lien vers le dépôt affiché sur le site |
| `IERS_CACHE_PATH` | `./cache/finals.all` | cache disque de `finals.all` |
| `IERS_CACHE_GCS` | aucun | cache partagé `bucket/objet` sur GCS (jeton du serveur de métadonnées, sans SDK) |
| `IERS_URL` | IERS puis USNO | URL(s) séparées par des virgules |
| `IERS_REFRESH_HOURS` | `168` | rafraîchissement |
| `RATE_LIMIT_RPS` / `RATE_LIMIT_BURST` | `20` / `60` | limite par IP sur l'API (0 désactive) |

Go ≥ 1.22, `net/http` de la stdlib, aucune dépendance externe.

## Déploiement (Cloud Run)

Site en ligne : **https://zman.technology** (mapping Cloud Run, certificat
géré ; URL technique https://zman-350480259244.europe-west1.run.app ;
projet GCP `essai-478723`, région `europe-west1`, service `zman`).
Le domaine est chez Hostinger : huit enregistrements `A`/`AAAA` sur `@`
vers les adresses données par `gcloud beta run domain-mappings describe`.
Redéployer : `DOMAIN=zman.technology ./deploy.sh`. Le projet n'avait ni
Compute Engine ni Cloud Run : le choix s'est porté sur Cloud Run, le binaire
étant sans état hormis le cache IERS, stocké dans un bucket GCS partagé entre
instances (et re-téléchargé chez l'IERS s'il a plus d'une semaine).

```
./deploy.sh                          # idempotent : APIs, bucket, IAM, build, deploy
DOMAIN=zman.example ./deploy.sh      # idem + mapping de domaine et BASE_URL
GCP_PROJECT=autre ./deploy.sh        # autre projet
```

Le script active les APIs (`run`, `cloudbuild`, `artifactregistry`,
`storage`), crée le bucket `<projet>-zman-iers` s'il manque, donne
`roles/storage.objectAdmin` dessus au compte de service Compute par défaut
(celui de Cloud Run), puis `gcloud run deploy --source .` (Cloud Build avec
le `Dockerfile`, image distroless statique, utilisateur non root,
256 Mi, 0 à 3 instances). Relancer le script redéploie la version courante.
`cloudbuild.yaml` fait la même chose pour un déclencheur sur push.

En-têtes : CSP stricte (`default-src 'self'`, ni script ni style externes),
`nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Permissions-Policy`,
HSTS derrière HTTPS. Assets statiques versionnés (`?v=<révision>`) avec
`Cache-Control: immutable` un an ; pages `private, max-age=300` ;
`/api/now` `max-age=1`. `robots.txt` et `sitemap.xml` sont générés à la
volée avec les alternates `hreflang`.

## Choix tranchés

### Backend
- **Epoch** : début de la semaine du molad Tohu (samedi 18:00 JMT), et non
  le 1 Tishrei de l'an 1 lui-même, afin que le jour 0 soit un dimanche et
  que `jour mod 7` donne directement le jour de la semaine. Selon la
  tradition, la création proprement dite commence le 25 Elul de l'an 1 ;
  la « semaine de la création » de l'epoch est celle du molad Tohu, année
  fictive du calendrier.
- **Heures** : le jour commence à 18:00 temps moyen de Jérusalem, comme
  dans le calendrier fixe (les dehiyot supposent cette convention). Pas
  d'heures saisonnières, pas de coucher réel : c'est le temps *civil*
  halachique, pas un zmanim.
- **Temps de Jérusalem** : temps moyen local à la longitude 35,2137° E,
  soit UT1 + 8 451,288 s. Pas de fuseau horaire.
- **Source IERS** : `https://datacenter.iers.org/data/9/finals2000A.all`
  en premier, puis `https://maia.usno.navy.mil/ser7/finals.all` en secours
  (même format, colonnes fixes). Cache disque, cache GCS optionnel,
  rafraîchissement hebdomadaire, conservation du dernier fichier si le
  réseau échoue.
- **Prédictions** : les lignes marquées `P` (Bulletin A) sont utilisées
  telles quelles quand l'instant dépasse la dernière mesure ; la réponse
  porte `predicted: true` et `dut1_age_days` donne l'âge de la dernière
  mesure. Au-delà de la dernière ligne, la dernière valeur est conservée.
  Un saut de seconde intercalaire entre deux lignes n'est pas interpolé.
- **Sans données** : si ni cache ni réseau ne sont disponibles au
  démarrage, le serveur tourne en mode `fallback` (DUT1 = 0, UT1 ≈ UTC,
  erreur < 1 s soit une vingtaine de rega'im), `/api/health` renvoie 503 et
  la source est annoncée dans chaque `/api/now`.
- **`end` de `/api/date`** est exclusif : c'est le `start` du lendemain.
- **Jour 0** : `/api/rega/{n}` accepte tout entier ; pour un jour antérieur
  au 1 Tishrei 1, `date` vaut `null`.

### Site et déploiement
- **Cloud Run** plutôt qu'une VM : rien n'existait sur le projet, le
  binaire est sans état, et le cache IERS tient dans un objet GCS. Le
  client GCS est écrit en HTTP pur (jeton du serveur de métadonnées) pour
  rester sans dépendance.
- **Polices** : EB Garamond (latin, variable, chiffres tabulaires via
  `tnum`) et Frank Ruhl Libre (hébreu, variable, 21 signes de nikoud
  vérifiés dans la fonte), auto-hébergées en woff2 sous-ensemble, licence
  OFL. Chiffres de l'horloge en `font-variant-numeric: tabular-nums`.
- **Palette** : papier `#f7f3ea`, encre `#1d1a15`, un seul accent
  (rouge de rubrique `#8d2b1c`) ; mode sombre par `prefers-color-scheme`.
- **Langues** : slugs identiques dans les trois langues (`/manifeste`,
  `/methode`, `/api`), préfixe de langue optionnel, cookie `lang` posé
  uniquement par le sélecteur. Défaut : français.
- **Hébreu** : traduction complète, termes halachiques en hébreu
  (שעה זמנית, חלקים, רגעים, דחיות, מולד תוהו, בית דין, סנהדרין), aucun
  chiffre en gematria dans la prose technique, gematria pour la date de
  l'horloge.
- **Sources** : le Sifré ne couvre pas l'Exode ; pour Exode 12:2 le
  manifeste cite Rashi, la Mekhilta de Rabbi Yishmael (Bo 1) et Rosh
  Hashana 22a (« cette attestation vous est confiée »). La CGPM 2022 est
  citée exactement : la résolution *décide* l'augmentation de la tolérance
  UT1−UTC en 2035 au plus tard et *demande* une valeur assurant la
  continuité d'UTC pour au moins un siècle.
- **Licences (proposition, à trancher)** : données sous CC BY 4.0 avec
  attribution « zman », comme hebcal ; code sous MIT. Aucun fichier
  `LICENSE` n'est ajouté tant que la décision n'est pas prise ; le site
  annonce les licences comme « proposées ».
- **Image Open Graph** : PNG 1200×630 statique, généré par
  `scripts/gen-og.py` avec les polices embarquées (pas de build step).
- **Dépôt** : le lien pointe vers `github.com/materligmann/zman`, à créer
  (le projet n'est pas encore sous git).
