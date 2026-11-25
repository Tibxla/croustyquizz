import test from 'node:test';
import assert from 'node:assert/strict';
import { determinerLignes, delaiAppelRestant, positionChronologique, tempsAvantFinPrevue } from '../../src/main/resources/static/js/modes/karaoke/paroles.mjs';

test('le calage conserve les indices source et ignore les lignes blanches', () => {
	const lignes = [' ', 'Première', '', 'Deuxième', 'Troisième'].map((texte, i) =>
		({ texte, positionMs: [0, 12000, 18000, 20000, 20000][i] }));
	assert.deepEqual(determinerLignes(lignes, 13499, 1500), { indexEnCours: -1, avantPremiere: true });
	assert.deepEqual(determinerLignes(lignes, 13500, 1500), { indexEnCours: 1, avantPremiere: false });
	assert.deepEqual(determinerLignes(lignes, 11000, -1000), { indexEnCours: 1, avantPremiere: false });
	assert.deepEqual(determinerLignes(lignes, 18000, 0), { indexEnCours: 1, avantPremiere: false });
	assert.deepEqual(determinerLignes(lignes, 20000, 0), { indexEnCours: 4, avantPremiere: false });
	assert.equal(determinerLignes(lignes, 150000, 1500).indexEnCours, 4);
});

test('la chronologie part du calcul serveur et se fige en pause', () => {
	const calculeLe = '2026-11-17T21:00:00.000Z';
	const maintenant = Date.parse(calculeLe) + 7000;
	const temps = { calculeLe, maintenant };
	assert.equal(delaiAppelRestant({ ...temps, delaiAppelRestantMs: 10000 }), 3000);
	assert.equal(delaiAppelRestant({ ...temps, delaiAppelRestantMs: 500 }), 0);
	assert.equal(delaiAppelRestant({ ...temps, delaiAppelRestantMs: 500, enPause: true }), 500);
	assert.equal(positionChronologique({ ...temps, positionChronologiqueMs: 3000, dureeReferenceMs: 12000 }), 10000);
	assert.equal(positionChronologique({ ...temps, positionChronologiqueMs: 3000, dureeReferenceMs: 8000 }), 8000);
	assert.equal(positionChronologique({ ...temps, positionChronologiqueMs: 3000, dureeReferenceMs: 8000, enPause: true }), 3000);
	assert.equal(tempsAvantFinPrevue({ finPrevueLe: '2026-11-17T21:00:34.000Z', maintenant }), 27000);
	assert.equal(tempsAvantFinPrevue({ finPrevueLe: calculeLe, maintenant }), 0);
});
