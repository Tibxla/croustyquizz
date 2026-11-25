import test from 'node:test';
import assert from 'node:assert/strict';
import { creerAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';

test('le même lecteur joue la Musique d’attente pendant la préparation puis la Chanson au départ', { timeout: 3000 }, async t => {
	const attente = { name: 'Ambiance.ogg', webkitRelativePath: 'Musique/Attente/Ambiance.ogg' };
	const chanson = { name: 'Artiste - Chanson.wav', webkitRelativePath: 'Musique/Artiste - Chanson.wav' };
	let fichier, audible, debut = Promise.withResolvers();
	const lecteur = {
		async debloquer() {},
		async charger(source) { fichier = source; },
		async lire() { audible = fichier; debut.resolve(fichier); },
		position: () => 0,
		detruire() { audible = undefined; },
		surFin: () => () => {},
		surErreur: () => () => {},
	};
	const sonde = { async charger() { assert.equal(audible, attente); return { dureeMs: 1200 }; }, detruire() {} };
	const audio = creerAudioSalle({ lecteur, sonde, lecteurId: 'lecteur', coordonner: surAttente => creerCoordinationSon({
		verrous: navigator.locks, canal: new BroadcastChannel('croustyquizz-test-lecteur'), ouverteA: 100, id: 'salle', surAttente,
	}) });
	t.after(() => audio.detruire());
	audio.choisirDossier([attente, chanson]);
	assert.equal(audible, undefined);
	await audio.activerSon();
	assert.equal(await debut.promise, attente);
	assert.deepEqual(await audio.lecteur.charger(chanson), { dureeMs: 1200 });
	assert.equal(audible, attente);
	await audio.signalerSonDeManche(true);
	assert.equal(audible, undefined);
	await audio.lecteur.lire();
	assert.equal(audible, chanson);
	debut = Promise.withResolvers();
	audio.lecteur.detruire();
	await audio.signalerSonDeManche(false);
	assert.equal(await debut.promise, attente);
});

test('le catalogue mesure ses fichiers un par un, hors du lecteur de Passage', { timeout: 3000 }, async t => {
	const charges = [];
	let enCours = 0;
	const mesureur = {
		async charger(fichier) {
			enCours++;
			assert.equal(enCours, 1);
			charges.push(fichier.name);
			await new Promise(resolve => setTimeout(resolve, 5));
			enCours--;
			if (fichier.name === 'illisible.mp3') throw new Error('illisible');
			return { dureeMs: fichier.name === 'court.mp3' ? 1000 : null };
		},
		detruire() {},
	};
	const audio = creerAudioSalle({ lecteur: { detruire() {}, surFin: () => () => {}, surErreur: () => () => {} },
		sonde: { detruire() {} }, mesureur, lecteurId: 'lecteur',
		coordonner: () => ({ activer() {}, signalerSonDeManche() {}, attendreSonDeManche: async () => true, detruire() {} }) });
	t.after(() => audio.detruire());
	const durees = await Promise.all(['court.mp3', 'illisible.mp3', 'inconnu.mp3'].map(name => audio.mesurerDuree({ name })));
	assert.deepEqual(durees, [1000, null, null]);
	assert.deepEqual(charges, ['court.mp3', 'illisible.mp3', 'inconnu.mp3']);
});
