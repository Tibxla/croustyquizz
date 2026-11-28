import test from 'node:test';
import assert from 'node:assert/strict';
import { creerAudioSalle, monterAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

test('masquer le choix du dossier rend le focus au podium et l’activation du son respecte ce choix', async t => {
	const document = creerDocument();
	document.defaultView = { navigator: {}, crypto, URL };
	const creerElement = document.createElement;
	document.createElement = balise => Object.assign(creerElement(balise), {
		play: async () => {}, pause() {}, load() {},
	});
	const zone = document.createElement('div');
	const titre = document.createElement('h2');
	titre.textContent = 'Podium final';
	const choisir = document.createElement('button');
	choisir.setAttribute('data-choisir-dossier', '');
	const activer = document.createElement('button');
	activer.setAttribute('data-activer-son', '');
	const aide = document.createElement('p');
	aide.setAttribute('data-aide-son', '');
	const alerte = document.createElement('p');
	alerte.setAttribute('data-son-bloque', '');
	zone.append(choisir, activer, aide, alerte, document.createElement('input'));
	document.body.append(titre, zone);
	const audio = monterAudioSalle(zone, titre);
	t.after(() => audio.detruire());

	choisir.focus();
	audio.masquerChoix(true);
	assert.equal(choisir.hidden, true);
	assert.equal(document.activeElement === titre, true);

	activer.focus();
	await audio.activerSon();
	assert.equal(activer.hidden, true);
	assert.equal(choisir.hidden, true);
	assert.equal(document.activeElement === titre, true);

	audio.masquerChoix(false);
	assert.equal(choisir.hidden, false);
	assert.equal(document.activeElement === titre, true);
});

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
	audio.reglerMusiqueDAttente(true);
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
