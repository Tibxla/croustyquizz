import test from 'node:test';
import assert from 'node:assert/strict';
import { terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { creerMusiqueDAttente } from '../../src/main/resources/static/js/musique-attente.mjs';

function banc() {
	let fichier, position, fin;
	const lectures = [];
	const musique = creerMusiqueDAttente({ hasard: () => 0, lecteur: {
		async charger(candidat) { fichier = candidat; position = 0; },
		async lire() { lectures.push([fichier, position]); },
		position: () => position,
		positionner: valeur => { position = valeur; },
		detruire() { fichier = undefined; },
		surFin(rappel) { fin = rappel; return () => { fin = undefined; }; },
		surErreur() { return () => {}; },
	} });
	return { musique, lectures, fichier: () => fichier, avancer: ms => { position += ms; }, finir: () => fin() };
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
