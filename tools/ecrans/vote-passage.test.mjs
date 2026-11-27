// Vote du public (#278) : la note choisie reste lisible sur la Manette, et la salle montre le vote au centre.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { creerBanc, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as salleFreestyle } from '../../src/main/resources/static/js/modes/freestyle/ecran.mjs';
import { creerEcranKaraoke } from '../../src/main/resources/static/js/modes/karaoke/ecran.mjs';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';

const capture = { source: 'capture', type: 'capture' };
const evenement = { source: 'evenement', type: 'vote-enregistre' };
const lireStatique = chemin => readFile(new URL(`../../src/main/resources/static/${chemin}`, import.meta.url), 'utf8');
const heure = Date.parse('2026-11-27T21:00:00Z');
const lea = { joueurId: 'j', pseudo: 'Léa' }, bartho = { joueurId: 'b', pseudo: 'Bartholoméo' };
const voteOuvert = (id = 'p', numero = 14, champs = {}) => ({ ...passage(id, numero), interpretes: [lea, bartho], etat: 'OUVERT',
	numeroModification: 3, delaiRestantMs: 26_000, calculeLe: new Date(heure).toISOString(), clotureLe: null, nombreVotes: 2,
	suivantDemarre: false, moyenneDixiemes: null, ...champs });
const voteClos = (vote, champs = {}) => ({ ...vote, etat: 'CLOS', delaiRestantMs: 0, clotureLe: new Date(heure).toISOString(),
	moyenneDixiemes: 43, nombreVotes: 7, ...champs });
const lignes = element => element.children.filter(enfant => !enfant.hidden).map(enfant => enfant.textContent);

// Règles CSS : sélecteurs et corps, @media compris (la règle garde le nom de son @media).
function regles(css) {
	const resultat = [];
	const visiter = (texte, media) => {
		for (const m of texte.matchAll(/([^{}]+)\{((?:[^{}]|\{[^{}]*\})*)\}/g)) {
			const entete = m[1].trim();
			if (entete.startsWith('@media') || entete.startsWith('@container')) visiter(m[2], entete);
			else resultat.push({ selecteurs: entete.replace(/\/\*[\s\S]*?\*\//g, '').split(',').map(s => s.trim()), corps: m[2], media });
		}
	};
	visiter(css, null);
	return resultat;
}

test('Manette : la note choisie garde son aplat d’encre même survolée, et le survol du ticket est réservé à la souris', async () => {
	const css = regles(await lireStatique('css/karaoke.css'));
	const choisie = css.find(r => r.selecteurs.includes('.karaoke .karaoke__ticket-vote .bouton[aria-pressed="true"]:hover'));
	assert.ok(choisie, 'la note choisie survolée a sa règle, plus spécifique que le survol du ticket');
	assert.ok(choisie.selecteurs.includes('.karaoke .karaoke__ticket-vote .bouton[aria-pressed="true"]'));
	assert.match(choisie.corps, /background:\s*var\(--encre\)/);
	assert.match(choisie.corps, /color:\s*var\(--teinte\)/);
	assert.equal(choisie.media, null);
	// Le seul effet visible du survol sur un ticket (sa bordure) n'existe qu'avec une souris.
	const survolVisible = css.filter(r => r.selecteurs.some(s => s.startsWith('.karaoke .mon-ticket .bouton:hover')) && /border-color/.test(r.corps));
	assert.ok(survolVisible.length > 0);
	for (const regle of survolVisible) assert.equal(regle.media, '@media (hover: hover)');
	// Hors @media, le survol du ticket ne fait que neutraliser le fond brun commun, jamais la note choisie.
	const neutre = css.find(r => r.media === null && r.selecteurs.includes('.karaoke .mon-ticket .bouton:hover'));
	assert.doesNotMatch(neutre.corps, /color:/);
});

test('salle : le texte de la colonne passe à 1,6vw au moins, tickets du rouleau et bandeaux de vote compris', async () => {
	const fichiers = await Promise.all(['css/ecran-de-salle.css', 'css/karaoke.css', 'css/freestyle.css'].map(lireStatique));
	const colonne = fichiers.flatMap(regles).filter(r => r.selecteurs.some(s => /(^|\s)\.salle__cote\b|^\.bandeau-vote|\.salle \.talon__(aide|adresse)|\.salle \.freestyle \.freestyle__(ordre|couplet)/.test(s)));
	const tailles = colonne.flatMap(r => [...r.corps.matchAll(/font(?:-size)?:[^;]*?([\d.]+)vw/g)].map(m => [r.selecteurs.join(', '), Number(m[1])]));
	assert.ok(tailles.length >= 10);
	for (const [selecteur, taille] of tailles) assert.ok(taille >= 1.6, `${selecteur} : ${taille}vw`);
	const [salle] = fichiers;
	assert.match(salle, /\.salle__cote \{[^}]*font-size:\s*1\.6vw/);
});

function salleKaraoke() {
	const lecteur = { debloquer: async () => {}, charger: async () => ({ dureeMs: 2000 }), lire: async () => {}, pause() {},
		reprendre: async () => {}, position: () => 0, surFin: () => () => {}, surErreur: () => () => {}, detruire() {} };
	const audioSalle = creerAudioSalle({ lecteur, lecteurId: 'lecteur', sonde: { charger: async () => ({ dureeMs: 2000 }), detruire() {} },
		coordonner: () => ({ activer() {}, signalerSonDeManche() {}, attendreSonDeManche: async () => false, detruire() {} }) });
	const trames = new Map();
	let numero = 0;
	const fabrique = (zones, contexte) => {
		Object.assign(contexte, { audioSalle, signalerSonDeManche: audioSalle.signalerSonDeManche, maintenantServeur: () => heure + 4000 });
		return creerEcranKaraoke(zones, contexte, { document: zones.principale.ownerDocument, performance,
			requestAnimationFrame: f => { trames.set(++numero, f); return numero; }, cancelAnimationFrame: id => trames.delete(id) });
	};
	return { fabrique, trames };
}

test('salle du Karaoké : pendant le vote, la zone principale le montre, puis seulement le Passage suivant', t => {
	const { fabrique, trames } = salleKaraoke();
	const b = creerBanc(fabrique, 'ECRAN');
	t.after(() => b.module.demonter());
	const suivant = passage('suivant', 15);
	const ouvert = voteOuvert();
	b.module.afficher(vue({ courant: { ...passage(), etat: 'TERMINE', etapeId: 'e' }, votes: [ouvert], prochainsPassages: [suivant] }), capture);
	const bloc = b.principale.querySelector('.passage-salle--vote');
	assert.ok(bloc && !bloc.hidden);
	assert.deepEqual(lignes(bloc), ['Note ce Passage sur ta Manette', 'Léa et Bartholoméo', 'Titre de démonstration · Artiste fictif', '22 s2 votes']);
	assert.equal(bloc.querySelector('.passage-salle__secondes').textContent, '22 s');
	assert.equal(bloc.querySelector('.passage-salle__votes').textContent, '2 votes');
	assert.ok(b.principale.querySelector('.karaoke__prochain').hidden, 'le Passage suivant attend la fin du vote');
	assert.ok(b.principale.querySelector('.karaoke__invitation').hidden);
	assert.equal(b.laterale.querySelectorAll('.bandeau-vote').length, 0, 'le vote montré au centre ne se répète pas dans la colonne');
	assert.equal(b.laterale.querySelectorAll('.ticket').length, 2, 'le Passage suivant reste dans le rouleau');
	assert.equal(trames.size, 1, 'le compte à rebours avance');
	b.module.afficher(vue({ courant: { ...passage(), etat: 'TERMINE', etapeId: 'e' }, votes: [{ ...ouvert, nombreVotes: 3 }], prochainsPassages: [suivant],
		etat: 'EN_PAUSE' }), evenement);
	assert.equal(bloc.querySelector('.passage-salle__secondes').textContent, 'Pause · 26 s');
	assert.equal(bloc.querySelector('.passage-salle__votes').textContent, '3 votes');
	// À la clôture, le résultat revient dans la colonne et le Passage suivant prend la zone principale.
	b.module.afficher(vue({ courant: { ...passage(), etat: 'TERMINE', etapeId: 'e' }, votes: [voteClos(ouvert)], prochainsPassages: [suivant] }), evenement);
	assert.ok(bloc.hidden);
	assert.ok(!b.principale.querySelector('.karaoke__prochain').hidden);
	assert.match(b.laterale.querySelector('.bandeau-vote').textContent, /4,3 sur 5/);
});

test('salle du Karaoké : un Passage appelé garde la zone principale, le vote ouvert reste dans la colonne', t => {
	const { fabrique } = salleKaraoke();
	const b = creerBanc(fabrique, 'ECRAN');
	t.after(() => b.module.demonter());
	const appele = { ...passage('c', 15), etat: 'APPELE', etapeId: 'e', calculeLe: heure, delaiAppelRestantMs: 30_000 };
	b.module.afficher(vue({ courant: appele, votes: [voteOuvert()] }), capture);
	assert.ok(b.principale.querySelector('.passage-salle--vote').hidden);
	assert.ok(!b.principale.querySelector('.passage-salle--appel').hidden);
	assert.equal(b.laterale.querySelectorAll('.bandeau-vote').length, 1);
});

test('salle du Karaoké : dans la colonne, un bandeau prend la place d’un ticket, deux bandeaux celle du rouleau', t => {
	const { fabrique } = salleKaraoke();
	const b = creerBanc(fabrique, 'ECRAN');
	t.after(() => b.module.demonter());
	const enCours = { ...passage('c', 15), etat: 'EN_COURS', etapeId: 'e', calculeLe: heure, positionChronologiqueMs: 0 };
	const prochainsPassages = [passage('d', 16), passage('e', 17), passage('f', 18)];
	const rouleau = () => b.laterale.querySelector('.rouleau').querySelectorAll('.ticket').length;
	b.module.afficher(vue({ courant: enCours, prochainsPassages, nombreAutresPassages: 2 }), capture);
	assert.equal(rouleau(), 3);
	b.module.afficher(vue({ courant: enCours, prochainsPassages, nombreAutresPassages: 2, votes: [voteOuvert()] }), capture);
	assert.equal(rouleau(), 2);
	assert.match(b.laterale.textContent, /et 3 autres/);
	b.module.afficher(vue({ courant: enCours, prochainsPassages, nombreAutresPassages: 2, votes: [voteOuvert(), voteOuvert('q', 13)] }), capture);
	assert.equal(rouleau(), 0);
	assert.equal(b.laterale.querySelectorAll('.bandeau-vote').length, 2);
	assert.match(b.laterale.textContent, /et 5 autres/);
});

test('salle du Freestyle : la désignation du meilleur Interprète prend la zone principale pendant le vote', t => {
	const b = creerBanc((zones, contexte) => {
		zones.principale.ownerDocument.defaultView = { requestAnimationFrame: () => 1, cancelAnimationFrame() {} };
		contexte.maintenantServeur = () => heure + 4000;
		return salleFreestyle(zones, contexte);
	}, 'ECRAN');
	t.after(() => b.module.demonter());
	b.connecter('synchronisee');
	const instrumentale = { instrumentaleId: 'i', titre: 'À la lueur', tempoBpm: 120, departBeatMs: 0, dureeMs: 95000 };
	const { chanson: _, ...base } = voteOuvert('f', 3);
	const vote = { ...base, instrumentale, scrutin: 'MEILLEUR_INTERPRETE', voix: null, nombreVotes: 0 };
	const bloc = votes => ({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'm', etat: 'EN_COURS', instrumentales: [instrumentale], votes,
		passagesEnAttente: [], nombreEnAttente: 0, grille: null,
		courant: { passageId: 'f', numero: 3, numeroModification: 1, instrumentale, interpretes: [lea, bartho], etapeId: 'e', etat: 'TERMINE',
			delaiAppelRestantMs: null, demarreLe: null, finPrevueLe: null, positionChronologiqueMs: null, calculeLe: new Date(heure).toISOString(), couplets: null } } });
	b.module.afficher(bloc([vote]), capture);
	const panneau = b.principale.querySelector('.passage-salle--vote');
	assert.deepEqual(lignes(panneau), ['Désigne le meilleur Interprète sur ta Manette', 'Léa et Bartholoméo', 'À la lueur', '22 sAucun vote']);
	assert.ok(b.principale.querySelector('.freestyle__interprete').hidden);
	assert.ok(b.principale.querySelector('.freestyle__phase').hidden);
	assert.equal(b.laterale.querySelector('.karaoke__liste-votes').childElementCount, 0);
	const { scrutin: __, ...note } = vote;
	b.module.afficher(bloc([{ ...note, scrutin: 'NOTE', interpretes: [lea] }]), capture);
	assert.equal(lignes(panneau)[0], 'Note ce Passage sur ta Manette');
	b.module.afficher(bloc([voteClos(vote)]), capture);
	assert.ok(panneau.hidden);
	assert.equal(b.principale.querySelector('.freestyle__interprete').textContent, 'Léa, Bartholoméo');
});
