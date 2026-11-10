import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import SwaggerParser from '@apidevtools/swagger-parser';
import { Parser, fromFile } from '@asyncapi/parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { parse } from 'yaml';

const dossier = new URL('../../docs/api/', import.meta.url);
const lire = async (nom) => parse(await readFile(new URL(nom, dossier), 'utf8'));
const ouvert = await lire('openapi.yaml');
const asynchrone = await lire('asyncapi.yaml');
const schemas = await lire('schemas.yaml');

function verifierReferencesLocales(value) {
  if (!value || typeof value !== 'object') return;
  if (value.$ref) {
    assert.match(value.$ref, /^(#\/|\.\/schemas\.yaml#\/\$defs\/)/,
      `Référence hors des contrats locaux : ${value.$ref}`);
  }
  for (const enfant of Object.values(value)) verifierReferencesLocales(enfant);
}
for (const document of [ouvert, asynchrone, schemas]) verifierReferencesLocales(document);

await SwaggerParser.validate(fileURLToPath(new URL('openapi.yaml', dossier)), {
  resolve: { http: false },
});
console.log('OpenAPI : structure et références valides.');

const { document, diagnostics } = await fromFile(
  new Parser(), fileURLToPath(new URL('asyncapi.yaml', dossier)),
).parse();
const erreurs = diagnostics.filter((diagnostic) => diagnostic.severity === 0);
assert.equal(erreurs.length, 0, JSON.stringify(erreurs, null, 2));
assert.ok(document, 'Le parseur AsyncAPI doit produire un document.');
for (const diagnostic of diagnostics) {
  console.log(`AsyncAPI (${diagnostic.code}) : ${diagnostic.message}`);
}
console.log('AsyncAPI : structure, canaux et références valides.');

const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const identifiant = 'urn:croustyquizz:contrats';
ajv.addSchema({ ...schemas, $id: identifiant });
const validations = new Map(Object.keys(schemas.$defs).map((nom) => [
  nom, ajv.getSchema(`${identifiant}#/$defs/${nom}`),
]));
for (const [nom, validation] of validations) assert.ok(validation, `Schéma introuvable : ${nom}`);

// Les schémas de préparation et d'accès ne doivent jamais devenir des
// dépendances d'une vue publique, même comme propriété optionnelle.
function dependances(nom, vues = new Set()) {
  if (vues.has(nom)) return vues;
  vues.add(nom);
  function parcourir(value) {
    if (!value || typeof value !== 'object') return;
    if (value.$ref?.startsWith('#/$defs/')) dependances(value.$ref.split('/').at(-1), vues);
    for (const enfant of Object.values(value)) parcourir(enfant);
  }
  parcourir(schemas.$defs[nom]);
  return vues;
}
for (const vue of ['VueSalle', 'VueManette', 'EvenementSalle', 'EvenementJoueur']) {
  const utilisees = dependances(vue);
  for (const prive of ['QuestionPreparee', 'QuestionSaisie', 'Csrf', 'AccesJoueur', 'AccesEcran', 'ConnexionAnimateur', 'InscriptionAnimateur']) {
    assert.ok(!utilisees.has(prive), `${vue} contient un schéma privé : ${prive}`);
  }
}

function valider(schema, valeur, contexte) {
  const validation = validations.get(schema);
  assert.ok(validation, `Schéma inconnu : ${schema}`);
  assert.ok(validation(valeur), `${contexte} : ${JSON.stringify(validation.errors, null, 2)}`);
}

function nomSchema(reference) {
  assert.match(reference, /^\.\/schemas\.yaml#\/\$defs\//);
  return reference.split('/').at(-1);
}

const operations = new Map();
for (const [chemin, element] of Object.entries(ouvert.paths)) {
  for (const [methode, operation] of Object.entries(element)) {
    assert.ok(operation.operationId, `${methode} ${chemin} sans operationId`);
    assert.ok(!operations.has(operation.operationId), `operationId dupliqué : ${operation.operationId}`);
    operations.set(operation.operationId, { ...operation, chemin, methode });
    const session = operation.security.some((securite) => 'sessionAnimateur' in securite);
    if (session && ['post', 'put', 'patch', 'delete'].includes(methode)) {
      assert.ok(operation.parameters?.some((p) => p.in === 'header' && p.name === 'X-CSRF-TOKEN' && p.required),
        `${operation.operationId} : protection CSRF manquante`);
    }
  }
}
assert.equal(ouvert.components.securitySchemes.jetonJoueur.scheme, 'bearer');
assert.equal(ouvert.components.securitySchemes.cleEcran.scheme, 'bearer');
assert.ok(Object.values(asynchrone.operations).every((operation) => operation.action === 'send'),
  'Le serveur ne reçoit aucune commande métier par STOMP.');

const exemples = await lire('exemples/parcours-quiz.yaml');
const projections = [];
function retenirProjection(canal, vue, revision, instant, contexte) {
  const destination = canal === 'joueur'
    ? `/topic/joueurs/${vue.joueurId}`
    : `/topic/soirees/${vue.soiree.id}/${canal}`;
  projections.push({ destination, vue, revision, instant: Date.parse(instant), contexte });
}

let nombreHttp = 0;
const reutilisations = new Map();
const couvertes = new Set();
for (const etape of exemples.http) {
  const operation = operations.get(etape.operationId);
  assert.ok(operation, `Opération inconnue : ${etape.operationId}`);
  couvertes.add(etape.operationId);
  const parametres = { ...exemples.parametresCommuns, ...etape.parametres };
  for (const parametre of operation.parameters ?? []) {
    if (!parametre.required && !(parametre.name in parametres)) continue;
    assert.ok(parametre.name in parametres, `${etape.nom} : ${parametre.name} manquant`);
    const validation = ajv.compile(parametre.schema);
    assert.ok(validation(parametres[parametre.name]), `${etape.nom} : paramètre ${parametre.name} invalide`);
  }
  if (operation.requestBody) {
    valider(nomSchema(operation.requestBody.content['application/json'].schema.$ref), etape.requete, etape.nom);
  } else {
    assert.equal(etape.requete, undefined, `${etape.nom} : corps non prévu par le contrat`);
  }
  let reponse = operation.responses[String(etape.statut)];
  assert.ok(reponse, `${etape.nom} : statut ${etape.statut} non décrit`);
  if (reponse.$ref) reponse = ouvert.components.responses[reponse.$ref.split('/').at(-1)];
  const contenu = reponse.content && Object.values(reponse.content)[0];
  if (contenu) valider(nomSchema(contenu.schema.$ref), etape.reponse, etape.nom);
  else assert.equal(etape.reponse, undefined, `${etape.nom} : réponse sans corps attendue`);
  const canal = { EtatSalle: 'salle', EtatConsole: 'console', EtatManette: 'joueur' }[
    contenu && nomSchema(contenu.schema.$ref)
  ];
  if (canal) {
    const { vue, revision, heureServeur } = etape.reponse;
    retenirProjection(canal, vue, revision, heureServeur, etape.nom);
  }
  if (etape.statut >= 400) assert.equal(etape.reponse.status, etape.statut, `${etape.nom} : statut d'erreur incohérent`);
  if (etape.reponse?.envoiId && parametres['Idempotency-Key']) {
    assert.equal(etape.reponse.envoiId, parametres['Idempotency-Key'], `${etape.nom} : clé de commande incohérente`);
  }
  if (etape.requete?.envoiId && etape.statut === 200) {
    assert.equal(etape.reponse.envoiId, etape.requete.envoiId, `${etape.nom} : identifiant d'envoi incohérent`);
    const chemin = operation.chemin.replace(/\{([^}]+)\}/g, (_, nom) => parametres[nom]);
    const cle = `${etape.acteur ?? 'joueur'}:${chemin}:${etape.requete.envoiId}`;
    if (reutilisations.has(cle)) assert.deepEqual(etape.reponse, reutilisations.get(cle), `${etape.nom} : accusé répété modifié`);
    else reutilisations.set(cle, etape.reponse);
  }
  nombreHttp++;
}
assert.deepEqual([...couvertes].sort(), [...operations.keys()].sort(), 'Chaque opération doit disposer d’un exemple.');

for (const exemple of exemples.evenements) {
  const canal = asynchrone.channels[exemple.canal];
  assert.ok(canal, `Canal inconnu : ${exemple.canal}`);
  const nomMessage = canal.messages.etat.$ref.split('/').at(-1);
  valider(nomSchema(asynchrone.components.messages[nomMessage].payload.$ref), exemple.message, exemple.nom);
  const { donnees, revision, emisLe } = exemple.message;
  retenirProjection(exemple.canal, donnees, revision, emisLe, exemple.nom);
}
for (const cas of exemples.cases) {
  const validation = validations.get(cas.schema);
  assert.ok(validation, `Schéma inconnu : ${cas.schema}`);
  assert.equal(validation(cas.valeur), cas.valide, `${cas.nom} : ${JSON.stringify(validation.errors)}`);
}

// Un exemple de reconnexion doit couvrir un événement antérieur au snapshot,
// un doublon et les événements tamponnés après sa révision.
const synchro = exemples.resynchronisation;
valider('EtatManette', synchro.etat, 'Snapshot de reconnexion');
retenirProjection('joueur', synchro.etat.vue, synchro.etat.revision, synchro.etat.heureServeur,
  'Snapshot de reconnexion');
assert.ok(synchro.etat.abonnementId);
let derniereRevision = synchro.etat.revision;
let vue = synchro.etat.vue;
let ignores = 0;
for (const evenement of synchro.tampon) {
  valider('EvenementJoueur', evenement, 'Événement de reconnexion');
  retenirProjection('joueur', evenement.donnees, evenement.revision, evenement.emisLe,
    'Événement de reconnexion');
  assert.equal(evenement.soireeId, vue.soiree.id);
  if (evenement.revision <= derniereRevision) { ignores++; continue; }
  assert.equal(evenement.revision, derniereRevision + 1, 'Trou de révision : reprendre une synchronisation');
  derniereRevision = evenement.revision;
  vue = evenement.donnees;
}
assert.ok(ignores >= 1, 'Le scénario doit exercer les doublons et événements déjà inclus.');
assert.equal(derniereRevision, synchro.revisionFinale);
assert.deepEqual(vue, synchro.vueFinale);

// Les captures HTTP et événements d'une même destination décrivent une seule
// chronologie. Les compteurs d'autres destinations restent indépendants.
const parDestination = Map.groupBy(projections, (projection) => projection.destination);
for (const [destination, captures] of parDestination) {
  const parRevision = new Map();
  for (const capture of captures) {
    const precedente = parRevision.get(capture.revision);
    if (precedente) {
      assert.deepEqual(capture.vue, precedente.vue,
        `${destination}, révision ${capture.revision} : vues différentes entre ` +
        `« ${precedente.contexte} » et « ${capture.contexte} »`);
    } else parRevision.set(capture.revision, capture);
  }
  captures.sort((a, b) => a.instant - b.instant || a.revision - b.revision);
  for (let i = 1; i < captures.length; i++) {
    const precedente = captures[i - 1];
    const capture = captures[i];
    assert.ok(capture.revision >= precedente.revision,
      `${destination} : chronologie incohérente entre « ${precedente.contexte} » ` +
      `(révision ${precedente.revision}) et « ${capture.contexte} » (révision ${capture.revision})`);
  }
}
console.log(`${validations.size} schémas, ${operations.size} opérations, ${nombreHttp} échanges HTTP, ` +
  `${exemples.evenements.length} événements et ${exemples.cases.length} cas de schéma vérifiés.`);
console.log('Scénario de resynchronisation : tampon, doublons et ordre des révisions vérifiés.');
console.log('Captures HTTP et événements : vues par révision et chronologie par destination vérifiées.');
console.log('Ces contrôles valident les contrats et leurs exemples ; ils ne testent pas un serveur implémenté.');
