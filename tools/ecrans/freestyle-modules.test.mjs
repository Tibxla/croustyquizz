import { test } from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { monter as monterConsole } from '../../src/main/resources/static/js/modes/freestyle/console.mjs';
import { monter as monterManette } from '../../src/main/resources/static/js/modes/freestyle/manette.mjs';
import { monter as monterSalle } from '../../src/main/resources/static/js/modes/freestyle/ecran.mjs';
import { positionDansGrille } from '../../src/main/resources/static/js/modes/freestyle/grille.mjs';

const capture = { source: 'capture' };
const instrumentale = { instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120, departBeatMs: 250 };
const passage = { passageId: 'p', numero: 1, numeroModification: 1, instrumentale, position: 1,
	interpretes: [{ joueurId: 'j', pseudo: 'Jo' }], etapeId: 'e', etat: 'APPELE', delaiAppelRestantMs: 30000, calculeLe: new Date().toISOString() };
const grille = { nombreMesures: 8, mots: [
	{ mesure: 1, temps: 3, positionMs: 1250, famille: 'A', mot: 'lumière' },
	{ mesure: 2, temps: 3, positionMs: 3250, famille: 'B', mot: null },
] };
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
		contexte.audioSalle = { etat: () => ({ sonDebloque: true }), lecteur: media, surChangement: () => () => {} };
		contexte.catalogueInstrumentales = { fichier: () => fichier, surChangement: () => () => {} };
		contexte.signalerSonDeManche = async actif => { appels.push(actif); return actif; };
		contexte.maintenantServeur = () => captureLe;
		return monterSalle(zones, contexte);
	}, 'ECRAN');
	try {
		banc.connecter('synchronisee');
		const courant = { ...passage, etat: 'EN_COURS', positionChronologiqueMs: 1250,
			calculeLe: new Date(captureLe).toISOString(), finPrevueLe: new Date(captureLe + 15000).toISOString() };
		banc.module.afficher(vue({ courant, grille }), capture);
		await terminerMicrotaches();
		assert.ok(appels.includes(true));
		assert.ok(appels.includes('lire'));
		assert.equal(banc.principale.querySelector('.freestyle__mot').textContent, 'lumière');
		assert.equal(banc.principale.querySelector('.freestyle__mot').classList.contains('freestyle__mot--actif'), true);
		assert.match(banc.principale.querySelector('.freestyle__suivant').textContent, /^Ensuite : /);
		banc.module.afficher(vue({ courant: { ...courant, etat: 'EN_PAUSE' }, grille, etat: 'EN_PAUSE' }));
		assert.equal(appels.at(-1), 'pause');
		assert.equal(appels.includes(false), false);
		banc.connecter('coupure');
		banc.module.afficher(vue({ courant: { ...courant, positionChronologiqueMs: 1750 }, grille }), capture);
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
	const { lireInstrumentales } = await import('../../src/main/resources/static/js/modes/freestyle/instrumentales.mjs');
	const fichier = (dossier, name) => ({ name, webkitRelativePath: `Musique/${dossier}/${name}`, size: 10, lastModified: 42 });
	const selection = [
		fichier('ÍNSTRUMENTALES', 'À la lueur [120 bpm].mp3'),
		fichier('ÍNSTRUMENTALES', 'Sans tempo.mp3'),
		fichier('ÍNSTRUMENTALES', 'Nuit [88 bpm].wav'),
		fichier('Autres', 'Hors catalogue [130 bpm].mp3'),
		{ ...fichier('ÍNSTRUMENTALES', 'instrumentales.json'),
			text: async () => JSON.stringify({ 'Nuit [88 bpm].wav': { titre: 'Rythme de nuit', tempoBpm: 92, departBeatMs: 250 } }) },
	];
	const disponibles = await lireInstrumentales(selection);
	assert.equal(disponibles.length, 2);
	assert.deepEqual(disponibles.map(({ fiche: { instrumentaleId, ...fiche } }) => fiche), [
		{ titre: 'À la lueur', tempoBpm: 120, departBeatMs: 0 },
		{ titre: 'Rythme de nuit', tempoBpm: 92, departBeatMs: 250 },
	]);
	assert.deepEqual((await lireInstrumentales([...selection].reverse())).map(({ fiche }) => fiche.instrumentaleId),
		disponibles.map(({ fiche }) => fiche.instrumentaleId));
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
		const texte = classe => banc.principale.querySelector(`.${classe}`);
		assert.equal(texte('freestyle__interprete').textContent, 'Zoé');
		assert.equal(texte('freestyle__ordre').textContent, 'Ordre des Couplets : Zoé · Jo · Inès');
		assert.equal(texte('freestyle__couplet').textContent, 'Couplet 1 sur 4 · mesures 1 à 2');
		assert.equal(texte('freestyle__annonce').hidden, true);
		banc.module.afficher(vue({ courant: enScene(true), grille }), { source: 'evenement', type: 'couplet-annonce' });
		assert.equal(texte('freestyle__annonce').textContent, 'À Jo dans une mesure');
		assert.equal(texte('freestyle__annonce').hidden, false);
	} finally { banc.module.demonter(); }
});
