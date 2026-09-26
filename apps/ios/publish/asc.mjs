#!/usr/bin/env node
/**
 * App Store Connect, en ligne de commande (repris de l'outillage de Midbar).
 *
 *   node publish/asc.mjs register    # identifiants de l'app et du widget + capacité App Groups
 *   node publish/asc.mjs setup       # catégories, âge, droits, prix (gratuit), disponibilité
 *   node publish/asc.mjs listing     # textes (fr, en, he) et captures de la version courante
 *   node publish/asc.mjs review      # coordonnées et notes pour l'examinateur
 *   node publish/asc.mjs status
 *   node publish/asc.mjs release [--no-submit] [--wait minutes]
 *   node publish/asc.mjs withdraw    # retire la soumission en attente (pour envoyer un autre build)
 *
 * L'API ne sait pas créer l'app elle-même : une fois, dans App Store Connect →
 * Apps → « + » → Nouvelle app (iOS, nom « Zman », langue principale français,
 * identifiant studio.100-8.zman, SKU zman-ios). Ni remplir la section
 * « Confidentialité de l'app » (Aucune donnée collectée), à faire dans la page.
 *
 * `release` prend la version et le build de project.yml, attend que le build
 * envoyé par release.sh soit traité, crée la version si besoin, y attache le
 * build, écrit les nouveautés et soumet à l'examen. Chaque commande reprend là
 * où elle en était : relancer ne casse rien.
 *
 * Aucune dépendance : le jeton ES256 est signé avec le module crypto de Node.
 * Clé et identifiants dans publish/.env (ASC_KEY_ID, ASC_ISSUER_ID) et
 * publish/AuthKey_<KEY_ID>.p8 — jamais dans git.
 */
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const IOS = path.resolve(HERE, "..");
const BUNDLE_ID = "studio.100-8.zman";
const WIDGET_ID = "studio.100-8.zman.widget";
const API = "https://api.appstoreconnect.apple.com";
const COPYRIGHT = `Copyright © ${new Date().getFullYear()} Mathias Erligmann. All rights reserved.`;

const env = Object.fromEntries(
  fs.readFileSync(path.join(HERE, ".env"), "utf8").split("\n")
    .filter((l) => l.includes("=")).map((l) => l.split("=").map((s) => s.trim()))
);
const KEY = fs.readFileSync(path.join(HERE, `AuthKey_${env.ASC_KEY_ID}.p8`), "utf8");

function token() {
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const head = b64({ alg: "ES256", kid: env.ASC_KEY_ID, typ: "JWT" });
  const body = b64({ iss: env.ASC_ISSUER_ID, iat: now, exp: now + 15 * 60, aud: "appstoreconnect-v1" });
  const sig = crypto.sign("sha256", Buffer.from(`${head}.${body}`), { key: KEY, dsaEncoding: "ieee-p1363" });
  return `${head}.${body}.${sig.toString("base64url")}`;
}

async function api(method, url, body) {
  const full = url.startsWith("http") ? url : `${API}${url.startsWith("/v") ? "" : "/v1"}${url}`;
  const res = await fetch(full, {
    method,
    headers: { Authorization: `Bearer ${token()}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) {
    const e = data.errors?.[0];
    // Apple détaille les causes d'un refus de soumission dans meta.associatedErrors.
    const assoc = Object.values(e?.meta?.associatedErrors ?? {}).flat()
      .map((a) => `\n    - ${a.code}: ${a.detail}`).join("");
    throw new Error(`${method} ${url} → ${res.status} ${e?.code ?? ""} ${e?.detail ?? text.slice(0, 300)}${assoc}`);
  }
  return data;
}

async function all(url) {
  const out = [];
  for (let next = url; next; ) {
    const r = await api("GET", next);
    out.push(...r.data);
    next = r.links?.next;
  }
  return out;
}

let appIdCache;
async function appId() {
  if (appIdCache) return appIdCache;
  const r = await api("GET", `/apps?filter[bundleId]=${BUNDLE_ID}&fields[apps]=name`);
  if (!r.data[0]) throw new Error(`app ${BUNDLE_ID} absente : la créer d'abord dans App Store Connect (Apps → « + »)`);
  return (appIdCache = r.data[0].id);
}

function projectVersion() {
  const yml = fs.readFileSync(path.join(IOS, "project.yml"), "utf8");
  const get = (k) => yml.match(new RegExp(`${k}:\\s*"?([\\d.]+)"?`))[1];
  return { version: get("MARKETING_VERSION"), build: get("CURRENT_PROJECT_VERSION") };
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const read = (p) => fs.readFileSync(p, "utf8").trim();

// --- Identifiants ------------------------------------------------------------

async function register() {
  for (const [identifier, name] of [[BUNDLE_ID, "Zman"], [WIDGET_ID, "Zman Widget"]]) {
    let b = (await api("GET", `/bundleIds?filter[identifier]=${identifier}`)).data
      .find((x) => x.attributes.identifier === identifier);
    if (!b) {
      b = (await api("POST", "/bundleIds", {
        data: { type: "bundleIds", attributes: { identifier, name, platform: "IOS" } },
      })).data;
      console.log(`  ${identifier} enregistré`);
    } else {
      console.log(`  ${identifier} présent`);
    }
    const caps = (await api("GET", `/bundleIds/${b.id}/bundleIdCapabilities`)).data;
    if (!caps.some((c) => c.attributes.capabilityType === "APP_GROUPS")) {
      await api("POST", "/bundleIdCapabilities", {
        data: {
          type: "bundleIdCapabilities",
          attributes: { capabilityType: "APP_GROUPS" },
          relationships: { bundleId: { data: { type: "bundleIds", id: b.id } } },
        },
      });
      console.log(`  ${identifier} : App Groups activé`);
    }
  }
  console.log("✓ identifiants prêts (le groupe group.studio.100-8.zman est créé par Xcode à l'archivage)");
}

// --- Réglages de l'app -------------------------------------------------------

async function setup() {
  const id = await appId();

  // Droits sur le contenu : aucun contenu de tiers.
  await api("PATCH", `/apps/${id}`, {
    data: { type: "apps", id, attributes: { contentRightsDeclaration: "DOES_NOT_USE_THIRD_PARTY_CONTENT" } },
  });
  console.log("  droits : pas de contenu de tiers");

  // Catégories, sur l'appInfo encore modifiable.
  const info = await editableAppInfo(id);
  await api("PATCH", `/appInfos/${info.id}`, {
    data: {
      type: "appInfos", id: info.id,
      relationships: {
        primaryCategory: { data: { type: "appCategories", id: "REFERENCE" } },
        secondaryCategory: { data: { type: "appCategories", id: "UTILITIES" } },
      },
    },
  });
  console.log("  catégories : Référence, Utilitaires");

  // Classification d'âge : rien à déclarer (une horloge et un calendrier).
  const decl = (await api("GET", `/appInfos/${info.id}/ageRatingDeclaration`)).data;
  const attrs = {};
  const levels = ["alcoholTobaccoOrDrugUseOrReferences", "contests", "gamblingSimulated", "horrorOrFearThemes",
    "matureOrSuggestiveThemes", "medicalOrTreatmentInformation", "profanityOrCrudeHumor",
    "sexualContentGraphicAndNudity", "sexualContentOrNudity", "violenceCartoonOrFantasy",
    "violenceRealistic", "violenceRealisticProlongedGraphicOrSadistic", "gunsOrOtherWeapons"];
  const flags = ["gambling", "unrestrictedWebAccess", "lootBox", "messagingAndChat", "parentalControls",
    "ageAssurance", "userGeneratedContent", "advertising", "healthOrWellnessTopics"];
  for (const k of levels) if (k in decl.attributes) attrs[k] = "NONE";
  for (const k of flags) if (k in decl.attributes) attrs[k] = false;
  await api("PATCH", `/ageRatingDeclarations/${decl.id}`, {
    data: { type: "ageRatingDeclarations", id: decl.id, attributes: attrs },
  });
  console.log(`  âge : ${Object.keys(attrs).length} réponses « aucun »`);

  // Prix : gratuit, base États-Unis.
  const schedule = await api("GET", `/apps/${id}/appPriceSchedule`).catch(() => null);
  const hasPrice = schedule && (await api("GET", `/appPriceSchedules/${schedule.data.id}/manualPrices?limit=1`).catch(() => ({ data: [] }))).data.length;
  if (!hasPrice) {
    const points = await all(`/apps/${id}/appPricePoints?filter[territory]=USA&limit=200`);
    const free = points.find((p) => Number(p.attributes.customerPrice) === 0);
    await api("POST", "/appPriceSchedules", {
      data: {
        type: "appPriceSchedules",
        relationships: {
          app: { data: { type: "apps", id } },
          baseTerritory: { data: { type: "territories", id: "USA" } },
          manualPrices: { data: [{ type: "appPrices", id: "${free}" }] },
        },
      },
      included: [{
        type: "appPrices", id: "${free}", attributes: { startDate: null },
        relationships: { appPricePoint: { data: { type: "appPricePoints", id: free.id } } },
      }],
    });
    console.log("  prix : gratuit");
  } else {
    console.log("  prix : déjà fixé");
  }

  // Disponibilité : tous les pays, et les futurs.
  const existing = await api("GET", `/apps/${id}/appAvailabilityV2`).catch(() => null);
  if (!existing) {
    const territories = await all("/territories?limit=200");
    await api("POST", "/v2/appAvailabilities", {
      data: {
        type: "appAvailabilities",
        attributes: { availableInNewTerritories: true },
        relationships: {
          app: { data: { type: "apps", id } },
          territoryAvailabilities: { data: territories.map((t) => ({ type: "territoryAvailabilities", id: `\${${t.id}}` })) },
        },
      },
      included: territories.map((t) => ({
        type: "territoryAvailabilities", id: `\${${t.id}}`, attributes: { available: true },
        relationships: { territory: { data: { type: "territories", id: t.id } } },
      })),
    });
    console.log(`  disponibilité : ${territories.length} pays`);
  } else {
    console.log("  disponibilité : déjà fixée");
  }
  console.log("✓ réglages de l'app à jour. Reste, dans la page : Confidentialité de l'app → Aucune donnée collectée.");
}

async function editableAppInfo(id) {
  const infos = (await api("GET", `/apps/${id}/appInfos?fields[appInfos]=state,appStoreState`)).data;
  return infos.find((i) => ["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED"].includes(i.attributes.state ?? i.attributes.appStoreState)) ?? infos[0];
}

// --- Version -----------------------------------------------------------------

async function ensureVersion(id, version) {
  let v = (await api("GET", `/apps/${id}/appStoreVersions?filter[versionString]=${version}&filter[platform]=IOS`)).data[0];
  if (!v) {
    // Une app neuve a déjà une version 1.0 vide : on la renomme plutôt que d'en créer une.
    const open = (await api("GET", `/apps/${id}/appStoreVersions?filter[appStoreState]=PREPARE_FOR_SUBMISSION&filter[platform]=IOS`)).data[0];
    if (open) {
      v = (await api("PATCH", `/appStoreVersions/${open.id}`, {
        data: { type: "appStoreVersions", id: open.id, attributes: { versionString: version, releaseType: "AFTER_APPROVAL" } },
      })).data;
    } else {
      v = (await api("POST", "/appStoreVersions", {
        data: {
          type: "appStoreVersions",
          attributes: { platform: "IOS", versionString: version, releaseType: "AFTER_APPROVAL" },
          relationships: { app: { data: { type: "apps", id } } },
        },
      })).data;
      console.log(`  version ${version} créée`);
    }
  }
  return v;
}

async function status() {
  const id = await appId();
  const { version, build } = projectVersion();
  console.log(`project.yml : ${version} (${build})\n`);
  const v = await api("GET", `/apps/${id}/appStoreVersions?limit=5&fields[appStoreVersions]=versionString,appStoreState,createdDate`);
  for (const x of v.data) console.log(`version ${x.attributes.versionString.padEnd(6)} ${x.attributes.appStoreState}`);
  const b = await api("GET", `/builds?filter[app]=${id}&sort=-uploadedDate&limit=5&fields[builds]=version,processingState,uploadedDate,expired,preReleaseVersion&include=preReleaseVersion&fields[preReleaseVersions]=version`);
  const pre = Object.fromEntries((b.included ?? []).map((p) => [p.id, p.attributes.version]));
  console.log();
  for (const x of b.data) {
    const a = x.attributes;
    const pv = pre[x.relationships?.preReleaseVersion?.data?.id] ?? "?";
    console.log(`build ${pv} (${a.version}) ${a.processingState.padEnd(10)} ${a.uploadedDate.slice(0, 16)}${a.expired ? " (expiré)" : ""}`);
  }
}

async function waitBuild(id, version, build, minutes) {
  const deadline = Date.now() + minutes * 60 * 1000;
  for (;;) {
    // Le numéro de build n'est unique que dans sa version : filtrer sur les deux.
    // Une erreur passagère de l'API ne doit pas interrompre l'attente.
    const r = await api("GET", `/builds?filter[app]=${id}&filter[preReleaseVersion.version]=${version}&filter[version]=${build}&limit=1&fields[builds]=version,processingState,uploadedDate`)
      .catch((e) => { console.log(`  (${e.message.slice(0, 80)}…)`); return { data: [] }; });
    const b = r.data[0];
    if (b?.attributes.processingState === "VALID") return b;
    if (["FAILED", "INVALID"].includes(b?.attributes.processingState)) {
      throw new Error(`build ${build} : traitement ${b.attributes.processingState}`);
    }
    if (Date.now() > deadline) return null;
    console.log(`  build ${build} : ${b ? b.attributes.processingState : "pas encore reçu"}, on attend…`);
    await sleep(30_000);
  }
}

async function release(args) {
  const id = await appId();
  const { version, build } = projectVersion();
  console.log(`▸ Zman ${version} (${build})`);

  const b = await waitBuild(id, version, build, args["wait"] ? Number(args["wait"]) : 15);
  if (!b) {
    console.log("Le build n'est pas encore traité : relancer dans quelques minutes.");
    process.exit(2);
  }
  console.log(`  build ${version} (${b.attributes.version}) traité`);

  // Conformité à l'export : pas de chiffrement propre (ITSAppUsesNonExemptEncryption = NO).
  await api("PATCH", `/builds/${b.id}`, {
    data: { type: "builds", id: b.id, attributes: { usesNonExemptEncryption: false } },
  }).catch(() => {});

  const v = await ensureVersion(id, version);
  console.log(`  version ${version} : ${v.attributes.appStoreState}`);
  if (!["PREPARE_FOR_SUBMISSION", "DEVELOPER_REJECTED", "REJECTED", "METADATA_REJECTED"].includes(v.attributes.appStoreState)) {
    console.log("  cette version n'est plus modifiable, rien d'autre à faire.");
    return;
  }
  await api("PATCH", `/appStoreVersions/${v.id}/relationships/build`, { data: { type: "builds", id: b.id } });
  console.log("  build attaché");
  await api("PATCH", `/appStoreVersions/${v.id}`, {
    data: { type: "appStoreVersions", id: v.id, attributes: { copyright: COPYRIGHT } },
  });

  // Pas de nouveautés sur une première version : Apple refuse le champ.
  const first = (await api("GET", `/apps/${id}/appStoreVersions?limit=10`)).data.length === 1;
  if (!first) {
    const locs = (await api("GET", `/appStoreVersions/${v.id}/appStoreVersionLocalizations`)).data;
    for (const l of locs) {
      const f = path.join(LISTING, l.attributes.locale, "whatsnew.txt");
      if (!fs.existsSync(f)) continue;
      await api("PATCH", `/appStoreVersionLocalizations/${l.id}`, {
        data: { type: "appStoreVersionLocalizations", id: l.id, attributes: { whatsNew: read(f) } },
      });
      console.log(`  nouveautés ${l.attributes.locale} écrites`);
    }
  }

  if (args["no-submit"]) {
    console.log("  soumission non demandée (--no-submit).");
    return;
  }
  let sub = (await api("GET", `/reviewSubmissions?filter[app]=${id}&filter[platform]=IOS&filter[state]=READY_FOR_REVIEW,WAITING_FOR_REVIEW,IN_REVIEW,UNRESOLVED_ISSUES`)).data[0];
  if (!sub) {
    sub = (await api("POST", "/reviewSubmissions", {
      data: {
        type: "reviewSubmissions",
        attributes: { platform: "IOS" },
        relationships: { app: { data: { type: "apps", id } } },
      },
    })).data;
  }
  // Une soumission peut rester vide si l'ajout de la version a échoué : on la complète.
  const items = (await api("GET", `/reviewSubmissions/${sub.id}/items`)).data;
  if (!items.length) {
    await api("POST", "/reviewSubmissionItems", {
      data: {
        type: "reviewSubmissionItems",
        relationships: {
          reviewSubmission: { data: { type: "reviewSubmissions", id: sub.id } },
          appStoreVersion: { data: { type: "appStoreVersions", id: v.id } },
        },
      },
    });
  }
  if (sub.attributes.state === "READY_FOR_REVIEW") {
    await api("PATCH", `/reviewSubmissions/${sub.id}`, {
      data: { type: "reviewSubmissions", id: sub.id, attributes: { submitted: true } },
    });
    console.log(`✓ Zman ${version} soumise à l'examen d'Apple.`);
  } else {
    console.log(`  soumission déjà ${sub.attributes.state}`);
  }
}

/** Retire la soumission pas encore examinée : la version redevient modifiable, `release` resoumet. */
async function withdraw() {
  const id = await appId();
  const sub = (await api("GET", `/reviewSubmissions?filter[app]=${id}&filter[platform]=IOS&filter[state]=READY_FOR_REVIEW,WAITING_FOR_REVIEW,UNRESOLVED_ISSUES`)).data[0];
  if (!sub) { console.log("aucune soumission en attente"); return; }
  await api("PATCH", `/reviewSubmissions/${sub.id}`, {
    data: { type: "reviewSubmissions", id: sub.id, attributes: { canceled: true } },
  });
  console.log(`✓ soumission ${sub.attributes.state} retirée ; \`release\` resoumet.`);
}

// --- La fiche : textes et captures, par langue ------------------------------

const LOCALES = ["fr-FR", "en-US", "he"];
const SITE = "https://zman.technology";
const LINK_LANG = { "fr-FR": "fr", "en-US": "en", he: "he" };
const LISTING = path.join(IOS, "appstore", "listing");
const SHOTS = path.join(IOS, "appstore", "out");
const DISPLAY_TYPE = "APP_IPHONE_67"; // 6,9 pouces : 1320 × 2868

function listingOf(locale) {
  const f = (name) => read(path.join(LISTING, locale, `${name}.txt`));
  const l = LINK_LANG[locale];
  return {
    name: f("name"), subtitle: f("subtitle"), keywords: f("keywords"),
    promotionalText: f("promo"), description: f("description"),
    privacyPolicyUrl: `${SITE}/${l}/confidentialite`,
    supportUrl: `${SITE}/${l}/`, marketingUrl: `${SITE}/${l}/`,
  };
}

async function upsertLocalization(list, type, locale, attributes, parentRel) {
  const existing = list.find((l) => l.attributes.locale === locale);
  if (existing) {
    await api("PATCH", `/${type}/${existing.id}`, { data: { type, id: existing.id, attributes } });
    return existing.id;
  }
  const created = await api("POST", `/${type}`, {
    data: { type, attributes: { locale, ...attributes }, relationships: parentRel },
  });
  return created.data.id;
}

async function uploadScreenshots(localizationId, locale) {
  const dir = path.join(SHOTS, locale);
  if (!fs.existsSync(dir)) { console.log(`  ${locale} : pas de captures dans ${path.relative(IOS, dir)}`); return; }
  const files = fs.readdirSync(dir).filter((f) => f.endsWith(".png")).sort();
  const sets = await api("GET", `/appStoreVersionLocalizations/${localizationId}/appScreenshotSets?include=appScreenshots`);
  let set = sets.data.find((s) => s.attributes.screenshotDisplayType === DISPLAY_TYPE);
  if (!set) {
    set = (await api("POST", "/appScreenshotSets", {
      data: {
        type: "appScreenshotSets",
        attributes: { screenshotDisplayType: DISPLAY_TYPE },
        relationships: { appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: localizationId } } },
      },
    })).data;
  }
  // On remplace tout : l'ordre des captures est celui des fichiers.
  for (const old of sets.included ?? []) {
    if (set.relationships?.appScreenshots?.data?.some((d) => d.id === old.id)) {
      await api("DELETE", `/appScreenshots/${old.id}`);
    }
  }
  for (const file of files) {
    const bytes = fs.readFileSync(path.join(dir, file));
    const shot = (await api("POST", "/appScreenshots", {
      data: {
        type: "appScreenshots",
        attributes: { fileName: file, fileSize: bytes.length },
        relationships: { appScreenshotSet: { data: { type: "appScreenshotSets", id: set.id } } },
      },
    })).data;
    for (const op of shot.attributes.uploadOperations) {
      const chunk = bytes.subarray(op.offset, op.offset + op.length);
      const headers = Object.fromEntries(op.requestHeaders.map((h) => [h.name, h.value]));
      const r = await fetch(op.url, { method: op.method, headers, body: chunk });
      if (!r.ok) throw new Error(`envoi ${file} : ${r.status}`);
    }
    await api("PATCH", `/appScreenshots/${shot.id}`, {
      data: {
        type: "appScreenshots", id: shot.id,
        attributes: { uploaded: true, sourceFileChecksum: crypto.createHash("md5").update(bytes).digest("hex") },
      },
    });
    console.log(`  ${locale} : ${file} envoyée`);
  }
}

async function listing(args) {
  const id = await appId();
  const { version } = projectVersion();
  const locales = args.locale ? [args.locale] : LOCALES;

  const info = await editableAppInfo(id);
  const infoLocs = (await api("GET", `/appInfos/${info.id}/appInfoLocalizations`)).data;
  const v = await ensureVersion(id, version);

  for (const locale of locales) {
    const t = listingOf(locale);
    console.log(`▸ ${locale}`);
    try {
      await upsertLocalization(infoLocs, "appInfoLocalizations", locale,
        { name: t.name, subtitle: t.subtitle, privacyPolicyUrl: t.privacyPolicyUrl },
        { appInfo: { data: { type: "appInfos", id: info.id } } });
      console.log(`  nom « ${t.name} », sous-titre « ${t.subtitle} »`);
    } catch (e) {
      if (!/INVALID_STATE/.test(e.message)) throw e;
      console.log("  nom et sous-titre gelés (version en attente de revue) : inchangés");
    }
    // Relue à chaque langue : ajouter une langue à l'appInfo l'ajoute aussi à la version.
    const verLocs = (await api("GET", `/appStoreVersions/${v.id}/appStoreVersionLocalizations`)).data;
    const locId = await upsertLocalization(verLocs, "appStoreVersionLocalizations", locale,
      { description: t.description, keywords: t.keywords, promotionalText: t.promotionalText,
        supportUrl: t.supportUrl, marketingUrl: t.marketingUrl },
      { appStoreVersion: { data: { type: "appStoreVersions", id: v.id } } });
    console.log(`  description (${t.description.length} car.), mots-clés, liens`);
    if (!args["no-screenshots"]) await uploadScreenshots(locId, locale);
  }
  console.log(`✓ fiche ${version} à jour pour ${locales.join(", ")}.`);
}

// --- Examen : coordonnées et notes ------------------------------------------

async function review() {
  const id = await appId();
  const { version } = projectVersion();
  const v = await ensureVersion(id, version);
  const contact = JSON.parse(read(path.join(HERE, "review-contact.json")));
  const attributes = {
    ...contact,
    demoAccountRequired: false,
    notes: read(path.join(IOS, "appstore", "review-notes.txt")),
  };
  const existing = await api("GET", `/appStoreVersions/${v.id}/appStoreReviewDetail`).catch(() => null);
  if (existing?.data) {
    await api("PATCH", `/appStoreReviewDetails/${existing.data.id}`, {
      data: { type: "appStoreReviewDetails", id: existing.data.id, attributes },
    });
  } else {
    await api("POST", "/appStoreReviewDetails", {
      data: {
        type: "appStoreReviewDetails", attributes,
        relationships: { appStoreVersion: { data: { type: "appStoreVersions", id: v.id } } },
      },
    });
  }
  console.log("✓ informations pour l'examen à jour");
}

const [cmd, ...rest] = process.argv.slice(2);
const args = {};
for (let i = 0; i < rest.length; i++) {
  if (rest[i].startsWith("--")) {
    const k = rest[i].slice(2);
    args[k] = rest[i + 1] && !rest[i + 1].startsWith("--") ? rest[++i] : true;
  }
}
const commands = { register, setup, listing, review, status, release, withdraw };
try {
  if (!commands[cmd]) {
    console.log("usage : asc.mjs register | setup | listing [--locale fr-FR] [--no-screenshots] | review | status | release [--no-submit] [--wait minutes] | withdraw");
    process.exit(1);
  }
  await commands[cmd](args);
} catch (e) {
  console.error("✗", e.message);
  process.exit(1);
}
