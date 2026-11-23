import { rejouerReconnexion } from '../tempsreel/parcours.mjs';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import SwaggerParser from '@apidevtools/swagger-parser';
import { Parser, fromFile } from '@asyncapi/parser';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import { parse } from 'yaml';

const dossier = new URL('../../docs/api/', import.meta.url);
// Les parcours partagent leurs vues complètes par ancres YAML ; garder une borne
// d’expansion tout en acceptant les trois projections du parcours Karaoké.
const lire = async (nom) => parse(await readFile(new URL(nom, dossier), 'utf8'), { maxAliasCount: 1000 });
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
// Le fichier local ne sort que vers la Console. Paroles et lecture restent hors de la Manette pour la
// légèreté de sa vue, pas pour le secret : la vue salle est lisible par un Joueur.
const interditsParVue = {
  salle: ['FichierAudioLocal'],
  console: ['ParolesDuPassage'],
  joueur: ['LectureDeChanson', 'ParolesDuPassage', 'FichierAudioLocal'],
};
for (const [vue, destination] of [['VueSalle', 'salle'], ['VueConsole', 'console'], ['VueManette', 'joueur'],
  ['EvenementSalle', 'salle'], ['EvenementConsole', 'console'], ['EvenementJoueur', 'joueur']]) {
  const utilisees = dependances(vue);
  for (const prive of ['QuestionPreparee', 'QuestionSaisie', 'Csrf', 'AccesJoueur', 'AccesEcran', 'ConnexionAnimateur',
    'InscriptionAnimateur', ...interditsParVue[destination]]) {
    assert.ok(!utilisees.has(prive), `${vue} contient un schéma interdit : ${prive}`);
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
// chansonId est borné deux fois, en chemin et dans InscriptionPassage : les bornes restent identiques.
const chansonIdChemin = operations.get('consulterChanson').parameters.find((p) => p.name === 'chansonId').schema;
const chansonIdCorps = schemas.$defs.InscriptionPassage.properties.chansonId;
assert.deepEqual([chansonIdChemin.minLength, chansonIdChemin.maxLength],
  [chansonIdCorps.minLength, chansonIdCorps.maxLength], 'chansonId : bornes différentes entre chemin et corps');
assert.equal(chansonIdCorps.maxLength, 128, 'chansonId : 128 caractères au plus');
assert.equal(ouvert.components.securitySchemes.jetonJoueur.scheme, 'bearer');
assert.equal(ouvert.components.securitySchemes.cleEcran.scheme, 'bearer');
assert.ok(Object.values(asynchrone.operations).every((operation) => operation.action === 'send'),
  'Le serveur ne reçoit aucune commande métier par STOMP.');

const parcours = await Promise.all(['quiz', 'karaoke', 'passage-karaoke'].map(async nom => ({
  nom, exemples: await lire(`exemples/parcours-${nom}.yaml`),
})));
const couvertes = new Set();
let nombreHttp = 0;
let nombreEvenements = 0;
let nombreCas = 0;
for (const { nom, exemples } of parcours) {
  const projections = [];
  function retenirProjection(canal, vue, revision, instant, contexte) {
    const destination = canal === 'joueur'
      ? `/topic/joueurs/${vue.joueurId}`
      : `/topic/soirees/${vue.soiree.id}/${canal}`;
    projections.push({ destination, vue, revision, instant: Date.parse(instant), contexte });
    // Le courant du Passage suit l'état de la Manche porté par le bloc : échéance d'appel nulle exactement
    // en pause, état EN_PAUSE seulement dans une Manche en pause.
    const courant = vue.karaoke?.courant;
    if (courant) {
      const enPause = vue.karaoke.etat === 'EN_PAUSE';
      if (courant.etat === 'APPELE') {
        assert.equal(courant.echeanceAppelLe === null, enPause, `${contexte} : échéance d'appel incohérente avec la pause`);
      }
      if (courant.etat === 'EN_PAUSE') assert.ok(enPause, `${contexte} : Passage EN_PAUSE hors d'une Manche en pause`);
    }
  }

  const reutilisations = new Map();
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
    if (etape.statut >= 400) {
      assert.equal(etape.reponse.status, etape.statut, `${etape.nom} : statut d'erreur incohérent`);
      if (operation.tags.includes('Karaoké')) {
        assert.ok(operation.description.includes(etape.reponse.code),
          `${etape.nom} : code de refus absent de la description de l’opération`);
      }
    }
    if (etape.reponse?.envoiId && parametres['Idempotency-Key']) {
      assert.equal(etape.reponse.envoiId, parametres['Idempotency-Key'], `${etape.nom} : clé de commande incohérente`);
    }
    const envoiId = etape.requete?.envoiId ?? parametres['Idempotency-Key'];
    if (envoiId && etape.reponse?.envoiId && [200, 201].includes(etape.statut)) {
      assert.equal(etape.reponse.envoiId, envoiId, `${etape.nom} : identifiant d'envoi incohérent`);
    }
    if (envoiId) {
      const chemin = operation.chemin.replace(/\{([^}]+)\}/g, (_, nom) => parametres[nom]);
      const cle = `${etape.acteur ?? 'joueur'}:${chemin}:${envoiId}`;
      const canonique = valeur => valeur && typeof valeur === 'object'
        ? (Array.isArray(valeur) ? valeur.map(canonique)
          : Object.fromEntries(Object.keys(valeur).sort().map(cle => [cle, canonique(valeur[cle])]))) : valeur;
      const empreinte = JSON.stringify([operation.methode, canonique(etape.requete)]);
      const precedent = reutilisations.get(cle);
      const transitoire = [400, 401, 403, 503].includes(etape.statut) || etape.accesRefuse === true
        || etape.reponse?.code === 'ETAPE_INACTIVE';
      if (!transitoire && precedent) {
        if (empreinte === precedent.empreinte) {
          assert.equal(etape.statut, precedent.statut, `${etape.nom} : statut répété modifié`);
          assert.deepEqual(etape.reponse, precedent.corps, `${etape.nom} : reçu répété modifié`);
        } else assert.equal(etape.reponse?.code, 'ENVOI_REUTILISE', `${etape.nom} : contenu réutilisé`);
      } else if (!transitoire) reutilisations.set(cle, {
        empreinte, statut: etape.statut, corps: etape.reponse,
      });
    }
    nombreHttp++;
  }

  const dernierEvenement = new Map();
  for (const exemple of exemples.evenements) {
    const canal = asynchrone.channels[exemple.canal];
    assert.ok(canal, `Canal inconnu : ${exemple.canal}`);
    const nomMessage = canal.messages.etat.$ref.split('/').at(-1);
    valider(nomSchema(asynchrone.components.messages[nomMessage].payload.$ref), exemple.message, exemple.nom);
    const { donnees, revision, emisLe } = exemple.message;
    // Le Quiz illustre des extraits ; un parcours marqué complet ne peut pas
    // omettre une publication ni changer les Joueurs au détour d’une autre cause.
    if (exemples.evenementsComplets) {
      const destination = exemple.canal === 'joueur' ? donnees.joueurId : exemple.canal;
      const cle = `${donnees.soiree.id}:${destination}`;
      const precedent = dernierEvenement.get(cle);
      if (precedent) {
        assert.equal(revision, precedent.revision + 1,
          `${exemple.nom} : Révisions non consécutives sur ${destination}`);
        if (exemple.message.type !== 'joueurs-modifies') {
          assert.deepEqual(donnees.joueurs, precedent.donnees.joueurs,
            `${exemple.nom} : liste des Joueurs modifiée sans joueurs-modifies`);
        }
        if (donnees.mancheActive && donnees.mancheActive.id !== precedent.donnees.mancheActive?.id) {
          assert.equal(exemple.message.type, 'manche-demarree',
            `${exemple.nom} : nouvelle Manche sans publication manche-demarree`);
        }
      }
      dernierEvenement.set(cle, exemple.message);
    }
    retenirProjection(exemple.canal, donnees, revision, emisLe, exemple.nom);
  }
  for (const cas of exemples.cases) {
    const validation = validations.get(cas.schema);
    assert.ok(validation, `Schéma inconnu : ${cas.schema}`);
    assert.equal(validation(cas.valeur), cas.valide, `${cas.nom} : ${JSON.stringify(validation.errors)}`);
    // Un témoin corrige uniquement la donnée annoncée comme cause du refus.
    // Si le témoin échoue, le cas négatif masque au moins un autre défaut.
    if (cas.correction) {
      assert.equal(cas.valide, false, `${cas.nom} : correction réservée aux cas de refus`);
      const temoin = structuredClone(cas.valeur);
      const { chemin, valeur } = cas.correction;
      const parent = chemin.slice(0, -1).reduce((objet, champ) => objet[champ], temoin);
      assert.ok(Object.hasOwn(parent, chemin.at(-1)), `${cas.nom} : chemin de correction inconnu`);
      parent[chemin.at(-1)] = valeur;
      valider(cas.schema, temoin, `${cas.nom} : le témoin corrigé doit être valide`);
    }
  }
  if (nom === 'quiz') {
    // Les blocs sont facultatifs sur les trois destinations, avec la même règle R1.
    for (const [schema, operationId] of [
      ['VueSalle', 'retrouverEtatSalle'],
      ['VueConsole', 'retrouverEtatConsole'],
      ['VueManette', 'retrouverEtatManette'],
    ]) {
      const exemple = exemples.http.find((e) => e.operationId === operationId && e.statut === 200);
      const vue = { ...exemple.reponse.vue, modeEnCours: 'QUIZ', quiz: { phase: 'ATTENTE' } };
      valider(schema, vue, `${schema} : bloc Quiz inchangé`);
      const validation = validations.get(schema);
      for (const modeEnCours of [null, 'QUIZ', 'BLIND_TEST', 'KARAOKE', 'FREESTYLE']) {
        const sansQuiz = { ...vue, modeEnCours, ...(schema === 'VueManette' && modeEnCours === 'KARAOKE'
          ? { derniereReponse: null, resultatPersonnel: null } : {}) };
        delete sansQuiz.quiz;
        valider(schema, sansQuiz, `${schema} : ${modeEnCours}, bloc Quiz absent`);
        valider(schema, { ...sansQuiz, quiz: null, blindtest: null, karaoke: null, freestyle: null },
          `${schema} : ${modeEnCours}, blocs nuls`);
        assert.equal(validation({ ...vue, modeEnCours }), modeEnCours === 'QUIZ',
          `${schema} : seul QUIZ peut porter un bloc Quiz non nul`);
      }
      const sansMode = { ...vue };
      delete sansMode.modeEnCours;
      assert.equal(validation(sansMode), false, `${schema} : modeEnCours obligatoire`);
      assert.equal(validation({ ...vue, modeEnCours: 'INCONNU' }), false,
        `${schema} : Mode de jeu inconnu`);
      for (const bloc of ['blindtest', 'karaoke', 'freestyle']) {
        assert.equal(validation({ ...vue, [bloc]: {} }), false,
          `${schema} : le bloc ${bloc} ne peut pas être rempli dans une vue Quiz`);
      }
    }

    // Le résultat personnel du Quiz reste privé avant correction. L'absence du
    // bloc Quiz ne doit pas déclencher cette condition par vérité vide de « if ».
    const manette = exemples.http.find((e) => e.operationId === 'retrouverEtatManette' && e.statut === 200).reponse.vue;
    const resultatPersonnel = { etapeId: exemples.parametresCommuns.etapeId, juste: true, pointsGagnes: 650 };
    const quizOuvert = manette.quiz;
    const quizPause = { phase: 'EN_PAUSE', question: quizOuvert.question, tempsRestantMs: 1000 };
    for (const quiz of [quizOuvert, quizPause, { phase: 'ATTENTE' }]) {
      valider('VueManette', { ...manette, quiz }, `Manette sans résultat pendant ${quiz.phase}`);
      assert.equal(validations.get('VueManette')({ ...manette, quiz, resultatPersonnel }), false,
        `Résultat anticipé refusé pendant ${quiz.phase}`);
    }
    const sansQuiz = { ...manette, resultatPersonnel };
    delete sansQuiz.quiz;
    valider('VueManette', sansQuiz, 'La confidentialité conditionnelle exige un bloc Quiz présent');
    valider('VueManette', { ...sansQuiz, quiz: null }, 'La confidentialité conditionnelle exige un bloc Quiz non nul');
    console.log('Vues par blocs : trois destinations, modes, blocs facultatifs et confidentialité vérifiés.');

  }

  // Valider les échanges puis les livrer au vrai module ES de Vue synchronisée.
  const synchro = exemples.resynchronisation;
  const canalSynchro = synchro.destination.endsWith('/salle') ? 'salle'
    : synchro.destination.endsWith('/console') ? 'console' : 'joueur';
  const [schemaEtat, schemaEvenement] = {
    salle: ['EtatSalle', 'EvenementSalle'], console: ['EtatConsole', 'EvenementConsole'],
    joueur: ['EtatManette', 'EvenementJoueur'],
  }[canalSynchro];
  valider(schemaEtat, synchro.etat, 'Capture de reconnexion');
  retenirProjection(canalSynchro, synchro.etat.vue, synchro.etat.revision, synchro.etat.heureServeur,
    'Capture de reconnexion');
  assert.ok(synchro.etat.abonnementId);
  for (const evenement of synchro.tampon) {
    valider(schemaEvenement, evenement, 'Événement de reconnexion');
    retenirProjection(canalSynchro, evenement.donnees, evenement.revision, evenement.emisLe,
      'Événement de reconnexion');
    assert.equal(evenement.soireeId, synchro.etat.vue.soiree.id);
  }
  await rejouerReconnexion(synchro);

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
  nombreEvenements += exemples.evenements.length;
  nombreCas += exemples.cases.length;
  console.log(`Parcours ${nom} : ${exemples.http.length} échanges HTTP, ` +
    `${exemples.evenements.length} événements et ${exemples.cases.length} cas de schéma vérifiés.`);
}
assert.deepEqual([...couvertes].sort(), [...operations.keys()].sort(), 'Chaque opération doit disposer d’un exemple.');
console.log(`${validations.size} schémas, ${operations.size} opérations, ${nombreHttp} échanges HTTP, ` +
  `${nombreEvenements} événements et ${nombreCas} cas de schéma vérifiés.`);
console.log('Scénario de resynchronisation : tampon, doublons et ordre des révisions vérifiés.');
console.log('Captures HTTP et événements : vues par révision et chronologie par destination vérifiées.');
console.log('Ces contrôles valident les contrats et leurs exemples ; ils ne testent pas un serveur implémenté.');

// Le socle de test a un contrat séparé : ne pas le confondre avec les accès métier.
await SwaggerParser.validate(fileURLToPath(new URL('prototype/openapi.yaml', dossier)), {
  resolve: { http: false },
});
const prototype = await fromFile(new Parser(),
  fileURLToPath(new URL('prototype/asyncapi.yaml', dossier))).parse();
assert.ok(prototype.document);
assert.equal(prototype.diagnostics.filter((d) => d.severity === 0).length, 0,
  JSON.stringify(prototype.diagnostics));
assert.ok(Object.values((await lire('prototype/asyncapi.yaml')).operations)
  .every((operation) => operation.action === 'send'));
console.log('Prototype : contrats HTTP et événements STOMP valides.');
