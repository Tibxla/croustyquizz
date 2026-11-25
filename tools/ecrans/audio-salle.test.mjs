import test from 'node:test';
import assert from 'node:assert/strict';
import { creerAudioSalle, monterAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

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

// Préfixe des verrous de présence de `coordination-son.mjs` ; chaque test vérifie d'abord qu'il trouve la fenêtre qui joue.
const PRESENCE = 'croustyquizz:son:fenetre:';
const morceauAttente = nom => ({ name: `${nom}.ogg`, webkitRelativePath: `Musique/Attente/${nom}.ogg` });

async function candidaturesDe(id) {
	const { held, pending } = await navigator.locks.query();
	return [...held, ...pending].map(verrou => verrou.name).filter(nom => nom.startsWith(PRESENCE) && nom.endsWith(`:${id}`));
}
// Délai court voulu : il précède une assertion d'absence (l'autre fenêtre ne se tait pas) et laisse passer
// les échanges du BroadcastChannel et des Web Locks déjà lancés. La requête des verrous reste le témoin déterministe.
const laisserPasser = () => new Promise(resolve => setTimeout(resolve, 20));

function fenetreDeSalle(t, ouverteA, id) {
	let fichier, debut = Promise.withResolvers();
	const fenetre = { audible: undefined };
	const lecteur = {
		async debloquer() {},
		async charger(source) { fichier = source; },
		async lire() { fenetre.audible = fichier; debut.resolve(fichier); },
		position: () => 0,
		positionner() {},
		detruire() { fenetre.audible = undefined; },
		surFin: () => () => {},
		surErreur: () => () => {},
	};
	const audio = creerAudioSalle({ lecteur, sonde: { async charger() { return { dureeMs: null }; }, detruire() {} }, lecteurId: id,
		coordonner: surAttente => creerCoordinationSon({ verrous: navigator.locks,
			canal: new BroadcastChannel('croustyquizz-test-sans-dossier'), ouverteA, id, surAttente }) });
	t.after(() => audio.detruire());
	audio.reglerMusiqueDAttente(true);
	return Object.assign(fenetre, { audio, attendreLecture() {
		debut = Promise.withResolvers();
		return debut.promise;
	} });
}

test('une fenêtre de salle sans dossier active le son sans prendre la Musique d’attente à l’autre', { timeout: 3000 }, async t => {
	const ancienne = fenetreDeSalle(t, 100, 'ancienne');
	const morceau = morceauAttente('Ambiance');
	const premiere = ancienne.attendreLecture();
	ancienne.audio.choisirDossier([morceau]);
	await ancienne.audio.activerSon();
	assert.equal(await premiere, morceau);
	assert.equal((await candidaturesDe('ancienne')).length, 1);

	const nouvelle = fenetreDeSalle(t, 200, 'nouvelle');
	await nouvelle.audio.activerSon();
	assert.equal(nouvelle.audio.etat().sonDebloque, true, 'le son est activé, pour le son de Manche');
	assert.deepEqual(await candidaturesDe('nouvelle'), [], 'sans dossier, pas de candidature');
	await laisserPasser();
	assert.equal(ancienne.audible, morceau, 'l’ancienne continue de jouer');

	// Le son de Manche d’une fenêtre sans dossier, comme le Blind test, fait taire l’autre puis la laisse reprendre.
	assert.equal(await nouvelle.audio.signalerSonDeManche(true), true);
	assert.equal(ancienne.audible, undefined);
	const reprise = ancienne.attendreLecture();
	await nouvelle.audio.signalerSonDeManche(false);
	assert.equal(await reprise, morceau);

	// Un dossier sans morceau dans Attente n’a rien à jouer : la fenêtre ne candidate toujours pas.
	nouvelle.audio.choisirDossier([{ name: 'Artiste - Chanson.mp3', webkitRelativePath: 'Musique/Artiste - Chanson.mp3' }]);
	assert.deepEqual(await candidaturesDe('nouvelle'), []);
	await laisserPasser();
	assert.equal(ancienne.audible, morceau);

	const autre = morceauAttente('Autre');
	const relais = nouvelle.attendreLecture();
	nouvelle.audio.choisirDossier([autre]);
	assert.equal(await relais, autre, 'avec un dossier, la plus récente prend la main');
	assert.equal(ancienne.audible, undefined);
	// La fenêtre qui avait la main la rend si son nouveau dossier n'a rien dans Attente.
	const retour = ancienne.attendreLecture();
	nouvelle.audio.choisirDossier([]);
	assert.equal(await retour, morceau);
	assert.equal(nouvelle.audible, undefined);
});

// Une page `ecran.html` réduite à sa zone audio, avec de faux éléments média et les vrais Web Locks de Node.
function ecranDeSalle(t, ouverteA) {
	const document = creerDocument(), creer = document.createElement;
	let lecture = Promise.withResolvers();
	document.createElement = tag => {
		const element = creer(tag);
		if (tag !== 'audio' && tag !== 'video') return element;
		let source = '';
		Object.assign(element, { currentTime: 0, paused: true, load() {}, pause() { element.paused = true; } });
		Object.defineProperty(element, 'src', { get: () => source, set(valeur) {
			source = valeur;
			if (valeur.startsWith('blob:')) queueMicrotask(() => element.dispatchEvent({ type: 'loadedmetadata' }));
		} });
		const retirer = element.removeAttribute.bind(element);
		element.removeAttribute = nom => { if (nom === 'src') source = ''; retirer(nom); };
		element.play = async () => {
			element.paused = false;
			if (tag === 'audio' && source.startsWith('blob:')) lecture.resolve(source);
		};
		if (tag === 'audio') ecran.audio = element;
		return element;
	};
	document.defaultView = { navigator: { locks: navigator.locks }, BroadcastChannel, performance: { timeOrigin: ouverteA },
		crypto: globalThis.crypto, URL: { createObjectURL: fichier => `blob:${fichier.name}`, revokeObjectURL() {} } };
	const zone = document.createElement('div');
	zone.hidden = true;
	const element = (tag, attribut) => {
		const enfant = document.createElement(tag);
		if (attribut) enfant.setAttribute(attribut, '');
		zone.append(enfant);
		return enfant;
	};
	const ecran = {
		choisir: element('button', 'data-choisir-dossier'), choix: element('input'), aide: element('p', 'data-aide-son'),
		alerte: element('p', 'data-son-bloque'), activer: element('button', 'data-activer-son'),
	};
	document.body.append(zone);
	ecran.salle = monterAudioSalle(zone);
	t.after(() => ecran.salle.detruire());
	ecran.salle.reglerMusiqueDAttente(true);
	return Object.assign(ecran, {
		zone,
		joue: () => !ecran.audio.paused && ecran.audio.src.startsWith('blob:'),
		attendreLecture() {
			lecture = Promise.withResolvers();
			return lecture.promise;
		},
		sonActive() {
			if (ecran.salle.etat().sonDebloque) return Promise.resolve();
			return new Promise(resolve => {
				const retirer = ecran.salle.surChangement(({ sonDebloque }) => { if (sonDebloque) { retirer(); resolve(); } });
			});
		},
		choisirDossier(fichiers) {
			ecran.choix.files = fichiers;
			ecran.choix.dispatchEvent({ type: 'change' });
		},
	});
}

test('l’Écran de salle qui annule « Choisir le dossier » laisse jouer celui qui a le dossier', { timeout: 3000 }, async t => {
	const ancien = ecranDeSalle(t, 100);
	assert.equal(ancien.zone.hidden, false);
	const premiere = ancien.attendreLecture();
	ancien.activer.click();
	await ancien.sonActive();
	ancien.choisirDossier([morceauAttente('Ambiance'), { name: 'notes.txt', webkitRelativePath: 'Musique/notes.txt' }]);
	assert.equal(await premiere, 'blob:Ambiance.ogg');
	assert.equal(ancien.aide.textContent, '1 fichier audio');
	assert.equal(ancien.choix.value, '');
	assert.equal((await candidaturesDe(ancien.salle.lecteurId)).length, 1);

	const nouveau = ecranDeSalle(t, 200);
	let selecteurOuvert = false;
	nouveau.choix.addEventListener('click', () => { selecteurOuvert = true; });
	nouveau.choisir.click();
	assert.equal(selecteurOuvert, true);
	// Le sélecteur est annulé : aucun événement change.
	await nouveau.sonActive();
	assert.equal(nouveau.activer.hidden, true);
	assert.equal(nouveau.alerte.hidden, true);
	assert.equal(nouveau.aide.textContent, '');
	assert.deepEqual(await candidaturesDe(nouveau.salle.lecteurId), []);
	await laisserPasser();
	assert.equal(ancien.joue(), true, 'l’ancien Écran continue de jouer');

	const relais = nouveau.attendreLecture();
	nouveau.choisirDossier([morceauAttente('Autre')]);
	assert.equal(await relais, 'blob:Autre.ogg');
	assert.equal(ancien.joue(), false);
});
