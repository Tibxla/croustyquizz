import test from 'node:test';
import assert from 'node:assert/strict';
import { terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { creerMusiqueDAttente } from '../../src/main/resources/static/js/musique-attente.mjs';

function banc({ illisibles = [], refusDeLecture = [], surSonBloque, surMorceauxEpuises } = {}) {
	let fichier, position, fin, erreur;
	const lectures = [];
	const musique = creerMusiqueDAttente({ hasard: () => 0, surSonBloque, surMorceauxEpuises, lecteur: {
		async charger(candidat) {
			if (illisibles.includes(candidat)) throw new Error('Fichier audio illisible.');
			fichier = candidat;
			position = 0;
		},
		async lire() {
			lectures.push([fichier, position]);
			const refus = refusDeLecture.shift();
			if (refus) throw new DOMException('Lecture refusée.', refus);
		},
		position: () => position,
		positionner: valeur => { position = valeur; },
		detruire() { fichier = undefined; },
		surFin(rappel) { fin = rappel; return () => { fin = undefined; }; },
		surErreur(rappel) { erreur = rappel; return () => { erreur = undefined; }; },
	} });
	return { musique, lectures, fichier: () => fichier, avancer: ms => { position += ms; }, finir: () => fin(),
		echouer: () => erreur?.(new Error('Lecture interrompue.')) };
}

test('la Musique d’attente se tait pendant un son de Manche et reprend le même morceau', async () => {
	const b = banc();
	b.musique.choisirFichiers(['A']);
	assert.deepEqual(b.lectures, []);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	b.avancer(1250);
	b.musique.autoriserLecture(false);
	assert.equal(b.fichier(), undefined);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 1250]]);
});

test('la liste mélange une fois les morceaux et revient au premier après le dernier', async () => {
	const b = banc();
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, []);
	b.musique.choisirFichiers(['A', 'B', 'C']);
	await terminerMicrotaches();
	for (let i = 0; i < 3; i++) {
		b.finir();
		await terminerMicrotaches();
	}
	assert.deepEqual(b.lectures.map(([fichier]) => fichier), ['B', 'C', 'A', 'B']);
});

test('la Musique d’attente prévient une seule fois quand elle a écarté tous ses morceaux illisibles', async () => {
	let epuisements = 0;
	const b = banc({ illisibles: ['A', 'B'], surMorceauxEpuises: () => { epuisements++; } });
	b.musique.autoriserLecture(true);
	b.musique.choisirFichiers(['A', 'B']);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, []);
	assert.equal(epuisements, 1);
	b.musique.autoriserLecture(false);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.equal(epuisements, 1, 'une liste déjà vide ne prévient plus');
});

test('un morceau seul qui échoue deux fois après le chargement épuise la Musique d’attente', async () => {
	let epuisements = 0;
	const b = banc({ surMorceauxEpuises: () => { epuisements++; } });
	b.musique.choisirFichiers(['A']);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	b.echouer();
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 0]]);
	assert.equal(epuisements, 0);
	b.echouer();
	await terminerMicrotaches();
	assert.equal(epuisements, 1);
	b.musique.autoriserLecture(false);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 0]]);
	assert.equal(epuisements, 1);
});

test('plusieurs morceaux dont la lecture échoue sont écartés à leur deuxième passage', async () => {
	let epuisements = 0;
	const b = banc({ surMorceauxEpuises: () => { epuisements++; } });
	b.musique.choisirFichiers(['A', 'B']);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	for (let i = 0; i < 2; i++) {
		b.echouer();
		await terminerMicrotaches();
	}
	assert.deepEqual(b.lectures, [['B', 0], ['A', 0], ['B', 0]]);
	assert.equal(epuisements, 0);
	for (let i = 0; i < 2; i++) {
		b.echouer();
		await terminerMicrotaches();
	}
	assert.deepEqual(b.lectures, [['B', 0], ['A', 0], ['B', 0], ['A', 0]]);
	assert.equal(epuisements, 1);
});

test('un morceau lu jusqu’au bout est de nouveau essayé après une erreur de lecture', async () => {
	let epuisements = 0;
	const b = banc({ surMorceauxEpuises: () => { epuisements++; } });
	b.musique.choisirFichiers(['A']);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	b.echouer();
	await terminerMicrotaches();
	b.finir();
	await terminerMicrotaches();
	b.echouer();
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 0], ['A', 0], ['A', 0]]);
	assert.equal(epuisements, 0);
});

test('NotSupportedError compte comme une erreur de lecture et NotAllowedError signale le son bloqué', async () => {
	let epuisements = 0, blocages = 0;
	const b = banc({ refusDeLecture: ['NotSupportedError', 'NotAllowedError', 'NotSupportedError'],
		surSonBloque: () => { blocages++; }, surMorceauxEpuises: () => { epuisements++; } });
	b.musique.choisirFichiers(['A']);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 0]]);
	assert.equal(blocages, 1);
	assert.equal(epuisements, 0);
	b.musique.autoriserLecture(true);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['A', 0], ['A', 0], ['A', 0]]);
	assert.equal(epuisements, 1);
});

test('un morceau illisible parmi des lisibles est écarté sans tarir la Musique d’attente', async () => {
	let epuisements = 0;
	const b = banc({ illisibles: ['A'], surMorceauxEpuises: () => { epuisements++; } });
	b.musique.autoriserLecture(true);
	b.musique.choisirFichiers(['A', 'B']);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['B', 0]]);
	b.finir();
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['B', 0], ['B', 0]]);
	assert.equal(epuisements, 0);
});

test('une erreur en cours de lecture passe au morceau suivant et conserve le morceau interrompu', async () => {
	let epuisements = 0;
	const b = banc({ illisibles: ['A'], surMorceauxEpuises: () => { epuisements++; } });
	b.musique.autoriserLecture(true);
	b.musique.choisirFichiers(['A', 'B', 'C']);
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['B', 0]]);
	b.avancer(1250);
	b.echouer();
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['B', 0], ['C', 0]]);
	b.finir();
	await terminerMicrotaches();
	assert.deepEqual(b.lectures, [['B', 0], ['C', 0], ['B', 0]]);
	assert.equal(epuisements, 0);
});
