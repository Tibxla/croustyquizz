import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import * as sonEcran from '../../src/main/resources/static/js/son-ecran.mjs';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';

test('l’Écran signale le son coupé au chargement puis son activation après le geste', async () => {
	const signaux = [];
	const audio = creerAudioSalle({
		lecteur: {
			debloquer: async () => {},
			surFin: () => () => {},
			surErreur: () => () => {},
		},
		coordonner: () => ({ activer() {} }),
	});
	const signal = sonEcran.monterSignalSonEcran(audio, {
		soireeId: 'soiree',
		envoyer(methode, route, corps, options) {
			signaux.push({ methode, route, corps, options });
			return Promise.resolve({ statut: 200, corps });
		},
	});

	assert.equal(signaux.length, 0);
	signal.synchroniser('premier');
	await audio.activerSon();
	assert.deepEqual(signaux.map(({ corps }) => corps), [
		{ abonnementId: 'premier', sonActive: false },
		{ abonnementId: 'premier', sonActive: true },
	]);
	assert.ok(signaux.every(({ methode, route, options }) =>
		methode === 'PUT' && route === '/api/soirees/soiree/son-ecran' && !options.annoncer));

	signal.synchroniser();
	assert.ok(signaux[0].options.signal.aborted);
	signal.synchroniser('second');
	assert.deepEqual(signaux.at(-1).corps, { abonnementId: 'second', sonActive: true });
	signal.demonter();
	assert.ok(signaux.at(-1).options.signal.aborted);
});

test('la Console affiche l’alerte seulement quand le son de l’Écran est coupé', () => {
	const statut = { textContent: '' };
	sonEcran.afficherAlerteSonEcran(statut, { sonEcran: 'COUPE' });
	assert.equal(statut.textContent, 'Le son de l’Écran est coupé : touche Activer le son sur la télé.');
	for (const vue of [{ sonEcran: 'ACTIVE' }, { sonEcran: 'ABSENT' }, {}]) {
		sonEcran.afficherAlerteSonEcran(statut, { sonEcran: 'COUPE' });
		sonEcran.afficherAlerteSonEcran(statut, vue);
		assert.equal(statut.textContent, '');
	}
});
