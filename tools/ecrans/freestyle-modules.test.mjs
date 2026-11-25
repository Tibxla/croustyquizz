import { test } from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { monter as monterConsole } from '../../src/main/resources/static/js/modes/freestyle/console.mjs';
import { monter as monterManette } from '../../src/main/resources/static/js/modes/freestyle/manette.mjs';
import { monter as monterSalle } from '../../src/main/resources/static/js/modes/freestyle/ecran.mjs';
import { positionDansGrille } from '../../src/main/resources/static/js/modes/freestyle/grille.mjs';
import { monterCatalogueInstrumentales } from '../../src/main/resources/static/js/modes/freestyle/catalogue-salle.mjs';
import { lireInstrumentales } from '../../src/main/resources/static/js/modes/freestyle/instrumentales.mjs';

const capture = { source: 'capture' };
const instrumentale = { instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120, departBeatMs: 250, dureeMs: 95000 };
const passage = { passageId: 'p', numero: 1, numeroModification: 1, instrumentale, position: 1,
	interpretes: [{ joueurId: 'j', pseudo: 'Jo' }], etapeId: 'e', etat: 'APPELE', delaiAppelRestantMs: 30000, calculeLe: new Date().toISOString() };
const grille = { nombreMesures: 8, mots: ['lumière', null, 'pierre', null, 'rivière', null, 'poussière', 'minuit']
	.map((mot, index) => ({ mesure: index + 1, temps: 3, positionMs: 1250 + 2000 * index, famille: index % 2 ? 'B' : 'A', mot })) };
const vue = (bloc = {}) => ({ soiree: { pilotage: 'MANUEL' }, freestyle: { mancheId: 'm', etat: 'EN_COURS',
	instrumentales: [instrumentale], passagesEnAttente: [], mesPassagesEnAttente: [], votes: [], nombreEnAttente: 0,
	...bloc } });
const bouton = (banc, libelle) => banc.principale.querySelectorAll('button').find(b => b.textContent === libelle);

test('la position audio choisit le temps, le mot visible et la mesure sans horloge', () => {
	assert.equal(positionDansGrille(grille, instrumentale, 249).temps, null);
	assert.equal(positionDansGrille(grille, instrumentale, 1249).mot, null);
	assert.equal(positionDansGrille(grille, instrumentale, 1250).mot, 'lumière');
	assert.equal(positionDansGrille(grille, instrumentale, 1749).temps, 3);
	assert.equal(positionDansGrille(grille, instrumentale, 1750).mot, null);
	assert.equal(positionDansGrille(grille, instrumentale, 3250).mesure, 2);
	assert.equal(positionDansGrille(grille, instrumentale, 3250).mot, null);
	assert.equal(positionDansGrille(grille, instrumentale, 16250).mesure, null);
	assert.equal(positionDansGrille(grille, instrumentale, 249).phase, 'avant');
	assert.equal(positionDansGrille(grille, instrumentale, 1250).phase, 'pendant');
	assert.equal(positionDansGrille(grille, instrumentale, 16250).phase, 'apres');
});

test('la Console appelle le premier Passage et attend le reçu', async () => {
	const banc = creerBanc(monterConsole, 'ANIMATEUR');
	try {
		banc.module.afficher(vue({ passagesEnAttente: [passage], peutAppeler: true, nombreEnAttente: 1 }), capture);
		assert.equal(bouton(banc, 'Appeler le suivant').disabled, true);
		banc.connecter('synchronisee');
		bouton(banc, 'Appeler le suivant').click();
		assert.equal(banc.appels[0].args[1], '/api/soirees/s/manches/m/passages/p/appel');
		assert.equal(banc.appels[0].args[2].numeroModification, 1);
		assert.ok(banc.appels[0].args[3].entetes['Idempotency-Key']);
		assert.match(banc.principale.textContent, /Envoi en cours/);
		banc.appels[0].resolve({ statut: 200, corps: {} });
		await terminerMicrotaches();
		assert.match(banc.principale.textContent, /Envoi accepté/);
	} finally { banc.module.demonter(); }
	assert.equal(banc.abonnee(), false);
});

test('la Manette peut laisser le hasard choisir, confirmer sa présence et envoyer une note', async () => {
	const banc = creerBanc(monterManette);
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ peutInscrire: true }), capture);
		bouton(banc, 'M’inscrire').click();
		assert.equal(banc.appels[0].args[2].instrumentaleId, null);
		assert.ok(banc.appels[0].args[2].envoiId);
		banc.appels[0].resolve({ statut: 201, corps: { passageId: 'p' } });
		await terminerMicrotaches();
		banc.module.afficher(vue({ courant: passage, peutConfirmerPresence: true }), capture);
		assert.equal(Boolean(banc.principale.querySelector('.mon-ticket--detachement')), false);
		bouton(banc, 'Je suis prêt').click();
		assert.match(banc.appels[1].args[1], /\/presence$/);
		assert.equal(banc.appels[1].args[2].etapeId, 'e');
		banc.appels[1].resolve({ statut: 200, corps: {} });
		await terminerMicrotaches();
		banc.module.afficher(vue({ votes: [{ ...passage, interpretes: [{ joueurId: 'autre', pseudo: 'Léa' }],
			etat: 'OUVERT', delaiRestantMs: 30000, peutVoter: true, scrutin: 'NOTE', maNote: null, monChoix: null }] }), capture);
		bouton(banc, '5').click();
		bouton(banc, 'Envoyer ma note').click();
		assert.match(banc.appels[2].args[1], /\/vote$/);
		assert.equal(banc.appels[2].args[2].note, 5);
	} finally { banc.module.demonter(); }
	assert.equal(banc.appels[2].args[3].signal.aborted, true);
});

test('la Salle garde le signal de son en pause et recale la lecture après une capture', async () => {
	const fichier = { name: 'Lumière.mp3' };
	const appels = [];
	const captureLe = Date.parse('2026-11-24T20:00:06Z');
	let position;
	const media = {
		charger: async () => ({ dureeMs: 25000 }),
		lire: async positionInitiale => { position = positionInitiale(); appels.push('lire'); },
		reprendre: async () => appels.push('reprendre'),
		pause: () => appels.push('pause'), detruire: () => appels.push('arrêter'),
		position: () => position, positionner: ms => { position = ms; },
		surFin: () => () => {}, surErreur: () => () => {},
	};
	const banc = creerBanc((zones, contexte) => {
		zones.principale.ownerDocument.defaultView = { requestAnimationFrame: () => 1, cancelAnimationFrame() {} };
		contexte.audioSalle = { lecteurId: 'l', etat: () => ({ sonDebloque: true }), lecteur: media, surChangement: () => () => {} };
		contexte.catalogueInstrumentales = { fichier: () => fichier, surChangement: () => () => {} };
		contexte.signalerSonDeManche = async actif => { appels.push(actif); return actif; };
		contexte.maintenantServeur = () => captureLe;
		return monterSalle(zones, contexte);
	}, 'ECRAN');
	try {
		banc.connecter('synchronisee');
		const courant = { ...passage, etat: 'EN_COURS', positionChronologiqueMs: 1250,
			calculeLe: new Date(captureLe).toISOString(), finPrevueLe: new Date(captureLe + 15000).toISOString() };
		banc.module.afficher(vue({ courant, grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		assert.ok(appels.includes(true));
		assert.ok(appels.includes('lire'));
		assert.equal(banc.principale.querySelector('.freestyle__mot').textContent, 'lumière');
		assert.equal(banc.principale.querySelector('.freestyle__mot').classList.contains('freestyle__mot--actif'), true);
		assert.match(banc.principale.querySelector('.freestyle__suivant').textContent, /^Ensuite : /);
		banc.module.afficher(vue({ courant: { ...courant, etat: 'EN_PAUSE' }, grille, etat: 'EN_PAUSE', lecteurDesigneId: 'l' }));
		assert.equal(appels.at(-1), 'pause');
		assert.equal(appels.includes(false), false);
		banc.connecter('coupure');
		banc.module.afficher(vue({ courant: { ...courant, positionChronologiqueMs: 1750 }, grille, lecteurDesigneId: 'l' }), capture);
		banc.connecter('synchronisee');
		await terminerMicrotaches();
		assert.equal(appels.at(-1), 'reprendre');
		assert.equal(position, 1750);
		// Au quatrième temps, le mot de la mesure reste lisible mais ne s'allume plus.
		assert.equal(banc.principale.querySelector('.freestyle__mot').textContent, 'lumière');
		assert.equal(banc.principale.querySelector('.freestyle__mot').classList.contains('freestyle__mot--actif'), false);
	} finally { banc.module.demonter(); }
	assert.equal(appels.at(-1), false);
});

test('la détection lit le sous-dossier direct et le JSON prioritaire, sans exposer les fichiers dans les fiches', async () => {
	const mesurer = async fichier => (fichier.name.startsWith('Nuit') ? 61000 : 95000);
	const fichier = (dossier, name) => ({ name, webkitRelativePath: `Musique/${dossier}/${name}`, size: 10, lastModified: 42 });
	const selection = [
		fichier('ÍNSTRUMENTALES', 'À la lueur [120 bpm].mp3'),
		fichier('ÍNSTRUMENTALES', 'Sans tempo.mp3'),
		fichier('ÍNSTRUMENTALES', 'Nuit [88 bpm].wav'),
		fichier('Autres', 'Hors catalogue [130 bpm].mp3'),
		{ ...fichier('ÍNSTRUMENTALES', 'instrumentales.json'),
			text: async () => JSON.stringify({ 'Nuit [88 bpm].wav': { titre: 'Rythme de nuit', tempoBpm: 92, departBeatMs: 250 } }) },
	];
	const disponibles = await lireInstrumentales(selection, { mesurer });
	assert.equal(disponibles.length, 2);
	assert.deepEqual(disponibles.map(({ fiche: { instrumentaleId, ...fiche } }) => fiche), [
		{ titre: 'À la lueur', tempoBpm: 120, departBeatMs: 0, dureeMs: 95000 },
		{ titre: 'Rythme de nuit', tempoBpm: 92, departBeatMs: 250, dureeMs: 61000 },
	]);
	assert.deepEqual((await lireInstrumentales([...selection].reverse(), { mesurer })).map(({ fiche }) => fiche.instrumentaleId),
		disponibles.map(({ fiche }) => fiche.instrumentaleId));
	// Une durée illisible écarte la fiche : le serveur ne pourrait pas vérifier la longueur de la Grille.
	assert.deepEqual((await lireInstrumentales(selection, { mesurer: async () => null })), []);
});

test('l’identifiant opaque ne dépend ni du dossier choisi, ni de la casse, ni de la date du fichier', async () => {
	const mesurer = async () => 95000;
	const fichier = (chemin, size = 10, lastModified = 42) =>
		({ name: chemin.split('/').at(-1), webkitRelativePath: chemin, size, lastModified });
	const identifiant = async (...args) => (await lireInstrumentales([fichier(...args)], { mesurer }))[0].fiche.instrumentaleId;
	const reference = await identifiant('Musique/Instrumentales/Nuit [92 bpm].mp3');
	assert.equal(await identifiant('Clé USB/INSTRUMENTALES/Nuit [92 bpm].mp3', 10, 1_700_000_000_000), reference);
	assert.notEqual(await identifiant('Musique/Instrumentales/Nuit [92 bpm].mp3', 11), reference);
	assert.notEqual(await identifiant('Musique/Instrumentales/Nuit [93 bpm].mp3'), reference);
});

function creerSalle({ lecteurId = 'l', position = 0 } = {}) {
	const appels = [], rappels = {};
	const captureLe = Date.parse('2026-11-24T20:00:06Z');
	let horloge = captureLe, positionLecteur = position;
	const media = {
		charger: async () => { appels.push('charger'); return { dureeMs: 25000 }; },
		lire: async positionInitiale => { positionLecteur = positionInitiale(); appels.push('lire'); },
		reprendre: async () => appels.push('reprendre'),
		pause: () => appels.push('pause'), detruire: () => appels.push('arrêter'),
		position: () => positionLecteur, positionner: ms => { positionLecteur = ms; appels.push(`positionner ${ms}`); },
		surFin: rappel => { rappels.fin = rappel; return () => {}; }, surErreur: () => () => {},
	};
	const banc = creerBanc((zones, contexte) => {
		zones.principale.ownerDocument.defaultView = { requestAnimationFrame: () => 1, cancelAnimationFrame() {} };
		contexte.audioSalle = { lecteurId, etat: () => ({ sonDebloque: true }), lecteur: media, surChangement: () => () => {} };
		contexte.catalogueInstrumentales = { fichier: () => ({ name: 'Lumière.mp3' }), surChangement: () => () => {} };
		contexte.signalerSonDeManche = async actif => { appels.push(actif); return actif; };
		contexte.maintenantServeur = () => horloge;
		return monterSalle(zones, contexte);
	}, 'ECRAN');
	const courant = (positionChronologiqueMs = 1250, etat = 'EN_COURS') => ({ ...passage, etat, positionChronologiqueMs,
		calculeLe: new Date(captureLe).toISOString(), finPrevueLe: new Date(captureLe + 15000).toISOString() });
	return { banc, appels, rappels, courant, avancer: ms => { horloge += ms; }, positionLecteur: () => positionLecteur,
		texte: classe => banc.principale.querySelector(`.${classe}`).textContent,
		motActif: () => banc.principale.querySelector('.freestyle__mot').classList.contains('freestyle__mot--actif') };
}

test('une coupure pendant un Passage laisse jouer l’Instrumentale, que la capture suivante recale sans pause', async () => {
	const salle = creerSalle();
	const { banc, appels } = salle;
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ courant: salle.courant(), grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		assert.ok(appels.includes('lire'));
		const avantCoupure = appels.length;
		banc.connecter('coupure');
		banc.module.afficher(vue({ courant: salle.courant(), grille, lecteurDesigneId: 'l' }));
		await terminerMicrotaches();
		assert.deepEqual(appels.slice(avantCoupure), []);
		// À la reconnexion, le serveur est 2 s plus loin : on recale la position sans couper le son.
		banc.module.afficher(vue({ courant: salle.courant(3250), grille, lecteurDesigneId: 'l' }), capture);
		banc.connecter('synchronisee');
		await terminerMicrotaches();
		assert.deepEqual(appels.slice(avantCoupure), ['positionner 3250']);
		// Un écart sous le seuil ne touche pas au lecteur.
		banc.module.afficher(vue({ courant: salle.courant(3400), grille, lecteurDesigneId: 'l' }), capture);
		assert.deepEqual(appels.slice(avantCoupure), ['positionner 3250']);
	} finally { banc.module.demonter(); }
});

test('la fin du fichier garde le son de Manche et la dernière mesure jusqu’à passage-termine', async () => {
	const salle = creerSalle();
	const { banc, appels, rappels } = salle;
	try {
		banc.connecter('synchronisee');
		const vueEnCours = vue({ courant: salle.courant(16000), grille, lecteurDesigneId: 'l' });
		banc.module.afficher(vueEnCours, capture);
		await terminerMicrotaches();
		assert.ok(appels.includes('lire'));
		const avantFin = appels.length;
		rappels.fin();
		banc.module.afficher(vueEnCours);
		await terminerMicrotaches();
		assert.deepEqual(appels.slice(avantFin), []);
		const etatLecture = banc.principale.querySelector('[role="status"].freestyle__phase');
		assert.equal(etatLecture.textContent, '');
		banc.module.afficher(vue({ courant: { ...salle.courant(), etat: 'TERMINE', positionChronologiqueMs: null }, grille: null,
			lecteurDesigneId: 'l' }), { source: 'evenement', type: 'passage-termine' });
		assert.deepEqual(appels.slice(avantFin), ['arrêter', false]);
	} finally { banc.module.demonter(); }
});

test('un Écran de salle non désigné affiche la Grille sans jouer et cède la lecture quand la désignation change', async () => {
	const salle = creerSalle({ lecteurId: 'autre' });
	const { banc, appels } = salle;
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ courant: salle.courant(1250), grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		assert.deepEqual(appels, [true]);
		assert.equal(salle.texte('freestyle__mot'), 'lumière');
		assert.equal(salle.motActif(), true);
		assert.match(banc.principale.textContent, /Un autre Écran de salle joue l’Instrumentale/);
		// Après la Grille, la dernière mesure reste affichée, sans mot actif ni annonce.
		salle.avancer(16000);
		banc.module.afficher(vue({ courant: salle.courant(1250), grille, lecteurDesigneId: 'l' }));
		assert.match(salle.texte('freestyle__mesure'), /^Mesure 8 sur 8/);
		assert.equal(salle.texte('freestyle__mot'), 'minuit');
		assert.equal(salle.motActif(), false);
		assert.equal(salle.texte('freestyle__suivant'), '');
	} finally { banc.module.demonter(); }

	const lectrice = creerSalle();
	try {
		lectrice.banc.connecter('synchronisee');
		lectrice.banc.module.afficher(vue({ courant: lectrice.courant(0), grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		// Avant le départ du beat : mesure 1, aucun mot.
		assert.match(lectrice.texte('freestyle__mesure'), /^Mesure 1 sur 8/);
		assert.equal(lectrice.texte('freestyle__mot'), '');
		assert.ok(lectrice.appels.includes('lire'));
		lectrice.banc.module.afficher(vue({ courant: lectrice.courant(0), grille, lecteurDesigneId: 'autre' }));
		assert.deepEqual(lectrice.appels.slice(-2), ['pause', 'arrêter']);
		assert.equal(lectrice.appels.includes(false), false);
	} finally { lectrice.banc.module.demonter(); }
});

test('la Manette ne permet que le réessai du même envoi tant que l’inscription n’est pas confirmée', async () => {
	const banc = creerBanc(monterManette);
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ peutInscrire: true }), capture);
		bouton(banc, 'M’inscrire').click();
		const envoiId = banc.appels[0].args[2].envoiId;
		banc.appels[0].reject(new TypeError('réseau'));
		await terminerMicrotaches();
		banc.module.afficher(vue({ peutInscrire: true }), capture);
		assert.match(banc.principale.textContent, /Confirmation à vérifier/);
		assert.equal(bouton(banc, 'M’inscrire').disabled, true);
		assert.equal(banc.principale.querySelector('select').disabled, true);
		assert.equal(bouton(banc, 'Réessayer le même envoi').hidden, false);
		bouton(banc, 'M’inscrire').click();
		assert.equal(banc.appels.length, 1);
		bouton(banc, 'Réessayer le même envoi').click();
		assert.equal(banc.appels.length, 2);
		assert.equal(banc.appels[1].args[2].envoiId, envoiId);
		banc.appels[1].resolve({ statut: 201, corps: { passageId: 'p' } });
		await terminerMicrotaches();
		banc.module.afficher(vue({ peutInscrire: true }));
		assert.equal(bouton(banc, 'M’inscrire').disabled, false);
	} finally { banc.module.demonter(); }
});

test('la Console retire un Passage en attente après confirmation', async () => {
	const banc = creerBanc(monterConsole, 'ANIMATEUR');
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ passagesEnAttente: [{ ...passage, numeroModification: 4 }], nombreEnAttente: 1,
			peutAppeler: true, peutGererFile: true }), capture);
		bouton(banc, 'Retirer').click();
		const dialogue = banc.principale.querySelector('dialog');
		assert.match(dialogue.textContent, /Retirer le Passage 1 · Jo · À la lueur/);
		dialogue.querySelectorAll('button').find(b => b.textContent === 'Retirer').click();
		assert.equal(banc.appels[0].args[0], 'POST');
		assert.equal(banc.appels[0].args[1], '/api/soirees/s/manches/m/passages/p/retrait');
		assert.deepEqual(banc.appels[0].args[2], { numeroModification: 4 });
		assert.ok(banc.appels[0].args[3].entetes['Idempotency-Key']);
	} finally { banc.module.demonter(); }
});

test('la déclaration désigne la fenêtre et ne propose le réessai qu’après une confirmation inconnue ou un 5xx', async () => {
	const fichiers = [{ name: 'Nuit [92 bpm].mp3', webkitRelativePath: 'Musique/Instrumentales/Nuit [92 bpm].mp3', size: 10 }];
	const document = (await import('./karaoke-dom-factice.mjs')).creerDocument();
	let changer, connexion;
	const essai = async reponse => {
		const appels = [];
		const audio = { lecteurId: 'fenetre', selection: () => fichiers, mesurerDuree: async () => 95000,
			surChangement: rappel => { changer = rappel; return () => {}; } };
		const contexte = { soireeId: 's', surEtatConnexion: rappel => { connexion = rappel; rappel('synchronisee'); return () => {}; },
			envoyer: (...args) => { appels.push(args); return reponse(); } };
		const statut = document.createElement('p'), redeclarer = document.createElement('button');
		const catalogue = monterCatalogueInstrumentales(audio, contexte, statut, redeclarer);
		// L'empreinte passe par Web Crypto : attendre au-delà des microtâches.
		await new Promise(resolve => setTimeout(resolve, 20));
		return { appels, statut, redeclarer, catalogue };
	};

	const refus = await essai(async () => ({ statut: 422, corps: { detail: 'Fiches invalides.' } }));
	assert.equal(refus.appels[0][1], '/api/soirees/s/freestyle/instrumentales');
	assert.equal(refus.appels[0][2].lecteurId, 'fenetre');
	assert.equal(refus.appels[0][2].instrumentales[0].dureeMs, 95000);
	assert.equal(refus.statut.textContent, 'Fiches invalides.');
	assert.equal(refus.redeclarer.hidden, true);
	refus.redeclarer.click();
	assert.equal(refus.appels.length, 1);
	refus.catalogue.demonter();

	for (const reponse of [async () => { throw new TypeError('réseau'); }, async () => ({ statut: 503, corps: {} })]) {
		let tentative = 0;
		const panne = await essai(() => (tentative++ ? Promise.resolve({ statut: 200, corps: {} }) : reponse()));
		assert.equal(panne.redeclarer.hidden, false);
		panne.redeclarer.click();
		await new Promise(resolve => setTimeout(resolve, 20));
		assert.equal(panne.appels.length, 2);
		assert.equal(panne.appels[1][3].entetes['Idempotency-Key'], panne.appels[0][3].entetes['Idempotency-Key']);
		assert.equal(panne.redeclarer.hidden, true);
		panne.catalogue.demonter();
	}
	assert.ok(changer && connexion);
});

// Freestyle à plusieurs (#74).
const zoe = { joueurId: 'z', pseudo: 'Zoé' }, ines = { joueurId: 'i', pseudo: 'Inès' };
const trio = [{ joueurId: 'j', pseudo: 'Jo' }, ines, zoe];
const couplets = suivantAnnonce => ({ mesuresParCouplet: 2, nombreCouplets: 4, ordre: [zoe, { joueurId: 'j', pseudo: 'Jo' }, ines],
	courant: { numero: 1, interprete: zoe, premiereMesure: 1, derniereMesure: 2 },
	suivant: { numero: 2, interprete: { joueurId: 'j', pseudo: 'Jo' }, premiereMesure: 3, derniereMesure: 4 }, suivantAnnonce });
const enScene = suivantAnnonce => ({ ...passage, interpretes: trio, etat: 'EN_COURS', couplets: couplets(suivantAnnonce) });

test('la Manette invite des Joueurs présents et répond à une Invitation reçue', async () => {
	const banc = creerBanc(monterManette);
	try {
		banc.connecter('synchronisee');
		const joueurs = [{ id: 'j', pseudo: 'Jo' }, { id: 'i', pseudo: 'Inès' }, { id: 'z', pseudo: 'Zoé' }];
		banc.module.afficher({ ...vue({ peutInscrire: true, invitationsRecues: [{ passage: { passageId: 'q', numeroModification: 0 },
			numero: 2, instrumentale, inscrivant: ines, interpretes: [ines] }] }), joueurs }, capture);
		const cases = banc.principale.querySelectorAll('input');
		assert.deepEqual(cases.map(c => c.value), ['i', 'z'], 'jamais soi-même');
		assert.match(banc.principale.textContent, /Inès t'invite à rapper\./);
		cases[1].checked = true;
		bouton(banc, 'M’inscrire').click();
		assert.deepEqual(banc.appels[0].args[2].invitesIds, ['z']);
		bouton(banc, 'Accepter').click();
		assert.equal(banc.appels[1].args[1], '/api/soirees/s/manches/m/passages/q/invitation');
		assert.equal(banc.appels[1].args[2].reponse, 'ACCEPTER');
	} finally { banc.module.demonter(); }
});

test('seule la Manette annoncée vibre, une fois, à l’événement d’annonce', () => {
	const vibrations = [];
	const banc = creerBanc(monterManette);
	banc.document.defaultView = { navigator: { vibrate: duree => vibrations.push(duree) } };
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ courant: enScene(true), monCoupletAnnonce: true }), capture);
		assert.equal(vibrations.length, 0, 'jamais sur une capture');
		const annonce = { source: 'evenement', type: 'couplet-annonce' };
		banc.module.afficher(vue({ courant: enScene(true), monCoupletAnnonce: true }), annonce);
		banc.module.afficher(vue({ courant: enScene(true), monCoupletAnnonce: true }), annonce);
		assert.equal(vibrations.length, 1);
		assert.match(banc.principale.textContent, /À toi dans une mesure !/);
		banc.module.afficher(vue({ courant: enScene(true), monCoupletAnnonce: false }), annonce);
		assert.equal(vibrations.length, 1, 'une autre Manette ne vibre pas');
	} finally { banc.module.demonter(); }
});

test('le public désigne le meilleur Interprète au lieu de noter', () => {
	const banc = creerBanc(monterManette);
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ votes: [{ ...passage, interpretes: [ines, zoe], etat: 'OUVERT', delaiRestantMs: 30000,
			peutVoter: true, scrutin: 'MEILLEUR_INTERPRETE', maNote: null, monChoix: null }] }), capture);
		assert.equal(bouton(banc, '5'), undefined);
		assert.match(banc.principale.textContent, /Désigne le meilleur Interprète/);
		bouton(banc, 'Zoé').click();
		bouton(banc, 'Envoyer mon choix').click();
		assert.match(banc.appels[0].args[1], /\/passages\/p\/meilleur-interprete$/);
		assert.equal(banc.appels[0].args[2].interpreteId, 'z');
		assert.equal(banc.appels[0].args[2].numeroModification, 1);
	} finally { banc.module.demonter(); }
});

test('la salle affiche l’ordre des Couplets, le Couplet courant et l’annonce du suivant', () => {
	const banc = creerBanc((zones, contexte) => {
		zones.principale.ownerDocument.defaultView = { requestAnimationFrame: () => 1, cancelAnimationFrame() {} };
		return monterSalle(zones, contexte);
	}, 'ECRAN');
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ courant: enScene(false), grille }), capture);
		// L'ordre et le Couplet sont dans la zone latérale, l'annonce reste près de la Grille.
		const texte = classe => banc.principale.querySelector(`.${classe}`) ?? banc.laterale.querySelector(`.${classe}`);
		assert.equal(texte('freestyle__interprete').textContent, 'Zoé');
		assert.equal(texte('freestyle__ordre').textContent, 'Ordre des Couplets : Zoé · Jo · Inès');
		assert.equal(texte('freestyle__couplet').textContent, 'Couplet 1 sur 4 · mesures 1 à 2');
		assert.equal(texte('freestyle__annonce').hidden, true);
		banc.module.afficher(vue({ courant: enScene(true), grille }), { source: 'evenement', type: 'couplet-annonce' });
		assert.equal(texte('freestyle__annonce').textContent, 'À Jo dans une mesure');
		assert.equal(texte('freestyle__annonce').hidden, false);
		banc.module.afficher(vue({ courant: { ...enScene(false), etat: 'TERMINE' }, grille: null }), capture);
		assert.equal(texte('freestyle__interprete').textContent, 'Jo, Inès, Zoé');
		assert.equal(texte('freestyle__ordre').hidden, true);
		assert.equal(texte('freestyle__couplet').hidden, true);
	} finally { banc.module.demonter(); }
});

test('un Passage à plusieurs garde le lecteur désigné et continue de jouer pendant une coupure', async () => {
	const enSceneSur = (salle, position) => ({ ...salle.courant(position), interpretes: trio, couplets: couplets(false) });
	const salle = creerSalle();
	const { banc, appels } = salle;
	try {
		banc.connecter('synchronisee');
		banc.module.afficher(vue({ courant: enSceneSur(salle, 1250), grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		assert.ok(appels.includes('lire'));
		assert.equal(salle.texte('freestyle__interprete'), 'Zoé');
		const avantCoupure = appels.length;
		banc.connecter('coupure');
		banc.module.afficher(vue({ courant: enSceneSur(salle, 1250), grille, lecteurDesigneId: 'l' }));
		await terminerMicrotaches();
		assert.deepEqual(appels.slice(avantCoupure), []);
		banc.module.afficher(vue({ courant: enSceneSur(salle, 3250), grille, lecteurDesigneId: 'l' }), capture);
		banc.connecter('synchronisee');
		await terminerMicrotaches();
		assert.deepEqual(appels.slice(avantCoupure), ['positionner 3250']);
	} finally { banc.module.demonter(); }

	const autre = creerSalle({ lecteurId: 'autre' });
	try {
		autre.banc.connecter('synchronisee');
		autre.banc.module.afficher(vue({ courant: enSceneSur(autre, 1250), grille, lecteurDesigneId: 'l' }), capture);
		await terminerMicrotaches();
		assert.deepEqual(autre.appels, [true]);
		assert.match(autre.banc.principale.textContent, /Un autre Écran de salle joue l’Instrumentale/);
		assert.equal(autre.texte('freestyle__interprete'), 'Zoé');
	} finally { autre.banc.module.demonter(); }
});
