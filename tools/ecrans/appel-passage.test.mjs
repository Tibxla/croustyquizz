// Appel du Passage (#277) : le Joueur appelé et la salle le voient sans chercher, au Karaoké comme au Freestyle.
import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, terminerMicrotaches, chanson, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as manetteKaraoke } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as manetteFreestyle } from '../../src/main/resources/static/js/modes/freestyle/manette.mjs';
import { monter as salleFreestyle } from '../../src/main/resources/static/js/modes/freestyle/ecran.mjs';
import { creerEcranKaraoke } from '../../src/main/resources/static/js/modes/karaoke/ecran.mjs';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { ouvrirDialogue } from '../../src/main/resources/static/js/dialogue.mjs';
import { texteAppel } from '../../src/main/resources/static/js/modes/passage/salle.mjs';

const capture = { source: 'capture', type: 'capture' };
const appelEvenement = { source: 'evenement', type: 'passage-appele' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);
const heure = Date.parse('2026-11-27T21:00:00Z');
const lea = { joueurId: 'j', pseudo: 'Léa' }, bartho = { joueurId: 'b', pseudo: 'Bartholoméo' }, max = { joueurId: 'm', pseudo: 'Max' };
const appele = (interpretes = [lea], champs = {}) => ({ ...passage('appel', 15), interpretes, etat: 'APPELE', etapeId: 'e',
	calculeLe: heure, delaiAppelRestantMs: 27_000, ...champs });

async function ouvrirFiche(b) {
	const champ = b.principale.querySelector('input'), formulaire = b.principale.querySelector('form');
	champ.value = 'titre'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels.at(-1).resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click();
	b.appels.at(-1).resolve({ statut: 200, corps: { chanson, paroles: null } }); await terminerMicrotaches();
	return b.principale.querySelector('dialog');
}

test('l’appel du Passage s’écrit « À toi » pour un Interprète, et énumère ceux d’un Passage à plusieurs', () => {
	assert.deepEqual(texteAppel([lea]), { debut: 'À toi, ', noms: 'Léa', fin: ' !' });
	assert.deepEqual(texteAppel([lea, bartho]), { debut: 'À vous, ', noms: 'Léa et Bartholoméo', fin: ' !' });
	assert.equal(texteAppel([lea, bartho, max]).noms, 'Léa, Bartholoméo et Max');
});

test('un dialogue peut se fermer sans rendre le focus à son déclencheur', () => {
	const focus = []; let fermer;
	const dialogue = { ownerDocument: { activeElement: { isConnected: true, focus: () => focus.push('déclencheur') } },
		addEventListener: (nom, rappel) => { fermer = rappel; }, showModal() { this.open = true; },
		querySelector: () => ({ focus: () => focus.push('contrôle') }) };
	const ouverture = ouvrirDialogue(dialogue);
	ouverture.oublierDeclencheur();
	fermer();
	assert.deepEqual(focus, ['contrôle']);
});

test('Manette du Karaoké : à l’appel, la fiche ouverte se referme et la page amène le Joueur à « Je suis prêt »', async t => {
	const b = creerBanc(manetteKaraoke);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heure;
	b.connecter('synchronisee');
	b.module.afficher(vue(), capture);
	const fiche = await ouvrirFiche(b);
	assert.equal(fiche.open, true);
	b.document.defilements.length = 0;
	b.module.afficher(vue({ courant: appele(), peutConfirmerPresence: true }), appelEvenement);
	assert.equal(fiche.open, false);
	assert.equal(b.principale.querySelector('dialog'), null);
	const pret = bouton(b.principale, 'Je suis prêt');
	assert.equal(b.document.activeElement, pret, 'le focus va à l’action du moment, pas au résultat de recherche');
	const [defilement] = b.document.defilements;
	assert.ok(defilement.element.classList.contains('karaoke__scene-manette'));
	assert.equal(defilement.options.block, 'start');
	// Une nouvelle vue du même appel ne fait plus défiler la page.
	b.module.afficher(vue({ courant: appele(), peutConfirmerPresence: true }), { source: 'evenement', type: 'karaoke-file-modifiee' });
	assert.equal(b.document.defilements.length, 1);
});

test('Manette du Karaoké : une fiche encore en chargement à l’appel ne s’ouvre plus et ne reprend pas le focus', async t => {
	const b = creerBanc(manetteKaraoke);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heure;
	b.connecter('synchronisee');
	b.module.afficher(vue(), capture);
	const champ = b.principale.querySelector('input'), formulaire = b.principale.querySelector('form');
	champ.value = 'titre'; formulaire.dispatchEvent({ type: 'submit' });
	b.appels.at(-1).resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click();
	const consultation = b.appels.at(-1);
	assert.match(b.principale.textContent, /Consultation en cours…/);
	b.module.afficher(vue({ courant: appele(), peutConfirmerPresence: true }), appelEvenement);
	assert.doesNotMatch(b.principale.textContent, /Consultation en cours…/);
	consultation.resolve({ statut: 200, corps: { chanson, paroles: null } }); await terminerMicrotaches();
	assert.equal(b.principale.querySelector('dialog'), null);
	assert.equal(b.document.activeElement, bouton(b.principale, 'Je suis prêt'));
});

test('Manette du Karaoké : un appel reçu par capture amène aussi le Joueur, une seule fois ; l’appel d’un autre ne change rien', async t => {
	const b = creerBanc(manetteKaraoke);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heure;
	b.connecter('synchronisee');
	b.module.afficher(vue(), capture);
	const fiche = await ouvrirFiche(b);
	b.document.defilements.length = 0;
	b.module.afficher(vue({ courant: appele([bartho]) }), appelEvenement);
	assert.equal(fiche.open, true, 'l’appel d’un autre Joueur laisse la fiche ouverte');
	assert.deepEqual(b.document.defilements, []);
	// Téléphone en veille : l'appel n'arrive que par la capture de reconnexion.
	b.module.afficher(vue({ courant: appele([lea], { passageId: 'autre' }), peutConfirmerPresence: true }), capture);
	assert.equal(fiche.open, false);
	assert.equal(b.document.activeElement, bouton(b.principale, 'Je suis prêt'));
	assert.equal(b.document.defilements.length, 1);
	assert.equal(b.document.defilements[0].options.behavior, 'auto', 'une capture ne rejoue aucune animation');
	assert.ok(!b.principale.querySelector('.mon-ticket--detachement'));
	b.module.afficher(vue({ courant: appele([lea], { passageId: 'autre' }), peutConfirmerPresence: true }), capture);
	assert.equal(b.document.defilements.length, 1, 'la même vue rejouée ne fait plus défiler');
});

test('Manette du Freestyle : l’appel amène aussi l’Interprète à « Je suis prêt »', t => {
	const b = creerBanc(manetteFreestyle);
	t.after(() => b.module.demonter());
	b.contexte.maintenantServeur = () => heure;
	b.connecter('synchronisee');
	const instrumentale = { instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120, departBeatMs: 0, dureeMs: 95000 };
	const bloc = champs => ({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'm', etat: 'EN_COURS', instrumentales: [instrumentale],
		passagesEnAttente: [], mesPassagesEnAttente: [], invitationsEmises: [], invitationsRecues: [], votes: [], nombreEnAttente: 0,
		peutInscrire: true, ...champs } });
	b.module.afficher(bloc({}), capture);
	const { chanson: _, ...sansChanson } = appele([lea, bartho]);
	b.module.afficher(bloc({ courant: { ...sansChanson, instrumentale, couplets: null }, peutConfirmerPresence: true }), appelEvenement);
	assert.equal(b.document.activeElement, bouton(b.principale, 'Je suis prêt'));
	assert.equal(b.document.defilements.length, 1);
});

function salleKaraoke() {
	const lecteur = { debloquer: async () => {}, charger: async () => ({ dureeMs: 2000 }), lire: async () => {}, pause() {},
		reprendre: async () => {}, position: () => 0, surFin: () => () => {}, surErreur: () => () => {}, detruire() {} };
	const audioSalle = creerAudioSalle({ lecteur, lecteurId: 'lecteur', sonde: { charger: async () => ({ dureeMs: 2000 }), detruire() {} },
		coordonner: () => ({ activer() {}, signalerSonDeManche() {}, attendreSonDeManche: async () => false, detruire() {} }) });
	return (zones, contexte) => {
		Object.assign(contexte, { audioSalle, signalerSonDeManche: audioSalle.signalerSonDeManche, maintenantServeur: () => heure + 3000 });
		return creerEcranKaraoke(zones, contexte, { document: zones.principale.ownerDocument, performance,
			requestAnimationFrame: () => 1, cancelAnimationFrame() {} });
	};
}

test('salle du Karaoké : la zone principale écrit qui est appelé, avec la Chanson, et le compte à rebours en second', t => {
	const b = creerBanc(salleKaraoke(), 'ECRAN');
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ courant: appele([lea, bartho]), prochainsPassages: [passage('p2', 16)] }), appelEvenement);
	const bloc = b.principale.querySelector('.passage-salle--appel');
	assert.ok(bloc && !bloc.hidden);
	const lignes = bloc.children.map(enfant => enfant.textContent);
	assert.deepEqual(lignes, ['À vous, Léa et Bartholoméo !', 'Titre de démonstration · Artiste fictif', 'Prépare-toi · 24 s']);
	assert.equal(bloc.querySelector('.passage-salle__noms').textContent, 'Léa et Bartholoméo');
	assert.ok(b.principale.querySelector('.karaoke__invitation').hidden, '« Prépare-toi » seul ne remplace plus le nom');
	b.module.afficher(vue({ courant: appele([lea]), etat: 'EN_PAUSE' }), capture);
	assert.deepEqual(bloc.children.map(enfant => enfant.textContent).slice(0, 1), ['À toi, Léa !']);
	assert.equal(bloc.querySelector('.passage-salle__temps').textContent, 'Prépare-toi · Pause');
	b.module.afficher(vue({ courant: { ...appele([lea]), etat: 'EN_COURS', positionChronologiqueMs: 0 } }), capture);
	assert.ok(bloc.hidden);
});

test('salle du Freestyle : l’appel nomme les Interprètes avec l’Instrumentale', t => {
	const b = creerBanc((zones, contexte) => {
		zones.principale.ownerDocument.defaultView = { requestAnimationFrame: () => 1, cancelAnimationFrame() {} };
		contexte.maintenantServeur = () => heure + 3000;
		return salleFreestyle(zones, contexte);
	}, 'ECRAN');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	const instrumentale = { instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120, departBeatMs: 0, dureeMs: 95000 };
	const { chanson: _, ...sansChanson } = appele([lea, bartho, max]);
	b.module.afficher({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'm', etat: 'EN_COURS', instrumentales: [instrumentale], votes: [],
		passagesEnAttente: [], nombreEnAttente: 0, grille: null, courant: { ...sansChanson, instrumentale, couplets: null } } }, appelEvenement);
	const bloc = b.principale.querySelector('.passage-salle--appel');
	assert.deepEqual(bloc.children.map(enfant => enfant.textContent),
		['À vous, Léa, Bartholoméo et Max !', 'À la lueur', 'Prépare-toi · 24 s']);
	assert.ok(b.principale.querySelector('.freestyle__interprete').hidden);
	assert.ok(!bloc.classList.contains('passage-salle--noms-longs'));
	// Trois pseudos longs restent tous visibles : le bloc passe en corps réduit au lieu de couper le dernier.
	const longs = [1, 2, 3].map(n => ({ joueurId: `d${n}`, pseudo: `Joueur de démonstration ${n}` }));
	b.module.afficher({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'm', etat: 'EN_COURS', instrumentales: [instrumentale], votes: [],
		passagesEnAttente: [], nombreEnAttente: 0, grille: null, courant: { ...sansChanson, interpretes: longs, instrumentale, couplets: null } } }, capture);
	assert.ok(bloc.classList.contains('passage-salle--noms-tres-longs'));
});
