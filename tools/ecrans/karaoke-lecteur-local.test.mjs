import test from 'node:test';
import assert from 'node:assert/strict';
import { creerDocument } from './karaoke-dom-factice.mjs';
import { creerLecteurLocal } from '../../src/main/resources/static/js/modes/karaoke/lecteur-local.mjs';
import { estLecteurDeChanson, METHODES_LECTEUR_DE_CHANSON } from '../../src/main/resources/static/js/modes/karaoke/lecteur-de-chanson.mjs';

function banc() {
	const document = creerDocument(), audios = [], revoquees = [];
	const creer = document.createElement;
	document.createElement = tag => {
		const audio = creer(tag);
		audio.currentTime = 0; audio.lectures = 0; audio.pauses = 0;
		audio.play = async () => { audio.lectures++; if (audio.refus) throw audio.refus; };
		audio.pause = () => audio.pauses++;
		audio.load = () => {};
		audios.push(audio);
		return audio;
	};
	const lecteur = creerLecteurLocal({ document, creerUrl: fichier => 'blob:' + fichier.name,
		revoquerUrl: url => revoquees.push(url) });
	return { lecteur, audios, audio: audios[0], revoquees };
}

test('le port local prépare les métadonnées sans jouer et arrondit les millisecondes', async () => {
	const { lecteur, audio } = banc();
	assert.equal(METHODES_LECTEUR_DE_CHANSON.length, 10);
	assert.equal(estLecteurDeChanson(lecteur), true);
	assert.equal(estLecteurDeChanson(null), false);
	const charge = lecteur.charger({ name: 'a.wav' });
	audio.duration = 4.1236;
	audio.dispatchEvent({ type: 'loadedmetadata' });
	assert.deepEqual(await charge, { dureeMs: 4124 });
	assert.equal(audio.lectures, 0);
	await lecteur.lire();
	audio.currentTime = 1.1246;
	lecteur.pause();
	await lecteur.reprendre();
	assert.equal(lecteur.position(), 1125);
	assert.equal(audio.lectures, 2);
});

test('durée inconnue, fin, erreur et nettoyage des URL au remplacement et au démontage', async () => {
	const { lecteur, audio, revoquees } = banc();
	let fins = 0, erreurs = 0;
	lecteur.surFin(() => fins++);
	lecteur.surErreur(() => erreurs++);
	const premier = lecteur.charger({ name: 'a.wav' });
	audio.duration = Infinity;
	audio.dispatchEvent({ type: 'loadedmetadata' });
	assert.deepEqual(await premier, { dureeMs: null });
	audio.dispatchEvent({ type: 'ended' });
	assert.equal(fins, 1);
	audio.dispatchEvent({ type: 'error' });
	assert.equal(erreurs, 1);
	const second = lecteur.charger({ name: 'b.ogg' });
	audio.dispatchEvent({ type: 'error' });
	await assert.rejects(second);
	assert.deepEqual(revoquees, ['blob:a.wav']);
	lecteur.detruire();
	assert.deepEqual(revoquees, ['blob:a.wav', 'blob:b.ogg']);
	audio.dispatchEvent({ type: 'ended' });
	audio.dispatchEvent({ type: 'error' });
	assert.deepEqual([fins, erreurs], [1, 1]);
});

test('le silence débloque le même élément qui lira le fichier, sans lancer ni remplacer le fichier après chargement', async () => {
	const { lecteur, audio, audios } = banc();
	await lecteur.debloquer();
	assert.equal(audios.length, 1);
	assert.equal(audio.lectures, 1);
	assert.match(audio.src, /^data:audio\/wav;base64,/);
	const charge = lecteur.charger({ name: 'a.wav' });
	audio.duration = 1;
	audio.dispatchEvent({ type: 'loadedmetadata' });
	await charge;
	await lecteur.debloquer();
	assert.equal(audio.src, 'blob:a.wav');
	assert.equal(audio.lectures, 1);
	await lecteur.lire();
	assert.equal(audio.lectures, 2);
	lecteur.detruire();
});

test('le déblocage rend le refus de play et permet un nouveau geste sur le même élément', async () => {
	const { lecteur, audio, audios } = banc();
	audio.refus = new DOMException('Bloqué', 'NotAllowedError');
	await assert.rejects(lecteur.debloquer(), { name: 'NotAllowedError' });
	audio.refus = null;
	await lecteur.debloquer();
	assert.equal(audios.length, 1);
	assert.equal(audio.lectures, 2);
});
