import test from 'node:test';
import assert from 'node:assert/strict';
import { creerAudioSalle, monterAudioSalle } from '../../src/main/resources/static/js/audio-salle.mjs';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';
import { monterMusiqueDAttenteSalle } from '../../src/main/resources/static/js/pilotage-musique-attente.mjs';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

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
		verrous: navigator.locks, canal: new BroadcastChannel('croustyquizz-test-lecteur'), ouverteA: 100, id: 'salle', soireeId: 'soiree', surAttente,
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
const CATALOGUE_VIDE = { version: 1, licences: {}, entrees: [] };
const CATALOGUE = { version: 1,
	licences: { 'CC-BY-4.0': { nom: 'CC BY 4.0', url: 'https://creativecommons.org/licenses/by/4.0/deed.fr' } },
	entrees: [{ type: 'attente', titre: 'Ambiance', artiste: 'Kevin MacLeod', dureeMs: 3000, licence: 'CC-BY-4.0',
		source: 'https://example.org/ambiance', fichier: 'attente/kevin-macleod-ambiance.mp3', tailleOctets: 1200 }] };
const AVEC_CHANSON = { ...CATALOGUE, entrees: [...CATALOGUE.entrees, { type: 'chanson', numero: 1, titre: 'La lanterne',
	artiste: 'Les Lucioles', dureeMs: 4000, licence: 'CC-BY-4.0', source: 'https://example.org/la-lanterne',
	fichier: 'chansons/les-lucioles-la-lanterne.mp3', tailleOctets: 2400, lrc: 'chansons/les-lucioles-la-lanterne.lrc' }] };
const libre = { name: 'kevin-macleod-ambiance.mp3', size: 1200, url: '/bibliotheque/attente/kevin-macleod-ambiance.mp3',
	credit: { titre: 'Ambiance', artiste: 'Kevin MacLeod', licence: 'CC BY 4.0',
		urlLicence: 'https://creativecommons.org/licenses/by/4.0/deed.fr' } };

async function candidaturesDe(id) {
	const { held, pending } = await navigator.locks.query();
	return [...held, ...pending].map(verrou => verrou.name).filter(nom => nom.startsWith(PRESENCE) && nom.endsWith(`:${id}`));
}
// Délai court voulu : il précède une assertion d'absence (l'autre fenêtre ne se tait pas) et laisse passer
// les échanges du BroadcastChannel et des Web Locks déjà lancés. La requête des verrous reste le témoin déterministe.
const laisserPasser = () => new Promise(resolve => setTimeout(resolve, 20));

function fenetreDeSalle(t, ouverteA, id, { illisibles = [], bibliotheque } = {}) {
	let fichier, debut = Promise.withResolvers();
	const fenetre = { audible: undefined, essais: [] };
	const lecteur = {
		async debloquer() {},
		// Comme `lecteur-local.mjs`, un fichier que le navigateur ne décode pas rejette le chargement.
		async charger(source) {
			fenetre.essais.push(source);
			if (illisibles.includes(source)) throw new Error('Fichier audio illisible.');
			fichier = source;
		},
		async lire() { fenetre.audible = fichier; debut.resolve(fichier); },
		position: () => 0,
		positionner() {},
		detruire() { fenetre.audible = undefined; },
		surFin: () => () => {},
		surErreur: () => () => {},
	};
	const audio = creerAudioSalle({ lecteur, sonde: { async charger() { return { dureeMs: null }; }, detruire() {} }, lecteurId: id,
		bibliotheque, coordonner: surAttente => creerCoordinationSon({ verrous: navigator.locks,
			canal: new BroadcastChannel('croustyquizz-test-sans-dossier'), ouverteA, id, soireeId: 'soiree', surAttente }) });
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

test('une fenêtre de salle dont tous les morceaux d’Attente sont illisibles rend la main à l’autre', { timeout: 3000 }, async t => {
	const ancienne = fenetreDeSalle(t, 100, 'ancienne');
	const morceau = morceauAttente('Ambiance');
	const premiere = ancienne.attendreLecture();
	ancienne.audio.choisirDossier([morceau]);
	await ancienne.audio.activerSon();
	assert.equal(await premiere, morceau);

	const abime = morceauAttente('Abîmé'), inconnu = morceauAttente('Format inconnu');
	const nouvelle = fenetreDeSalle(t, 200, 'nouvelle', { illisibles: [abime, inconnu] });
	const signaux = [];
	nouvelle.audio.surChangement(({ morceauxDisponibles }) => signaux.push(morceauxDisponibles));
	await nouvelle.audio.activerSon();
	const reprise = ancienne.attendreLecture();
	nouvelle.audio.choisirDossier([abime, inconnu]);
	assert.equal(await reprise, morceau, 'l’ancienne reprend la Musique d’attente');
	assert.deepEqual(new Set(nouvelle.essais), new Set([abime, inconnu]), 'les deux morceaux ont été essayés');
	assert.equal(nouvelle.audible, undefined);
	assert.deepEqual(await candidaturesDe('nouvelle'), [], 'sans morceau lisible, pas de candidature');
	assert.equal(nouvelle.audio.etat().morceauxDisponibles, false);
	assert.equal(signaux.at(-1), false, 'l’absence de morceaux est annoncée');
	assert.equal(nouvelle.audio.etat().sonDebloque, true, 'le son reste activé pour le son de Manche');

	// Un nouveau choix de dossier remet la fenêtre en lice.
	const autre = morceauAttente('Autre');
	const relais = nouvelle.attendreLecture();
	nouvelle.audio.choisirDossier([autre]);
	assert.equal(await relais, autre);
	assert.equal(nouvelle.audio.etat().morceauxDisponibles, true);
	assert.equal(ancienne.audible, undefined);
});

// Un faux serveur qui, comme `ServiceMusiqueDAttente`, garde le dernier signal de disponibilité reçu, quelle que soit
// la fenêtre de salle qui l'envoie : c'est ce que la Console affiche.
function serveurDeDisponibilite(avantEnregistrement) {
	const serveur = { dernier: null, envois: [] };
	let attentes = [];
	return Object.assign(serveur, {
		brancher(t, fenetre) {
			const { contexte, changerEtat } = creerContexte({ soireeId: 'soiree', async envoyer(methode, route, { morceauxDisponibles }) {
				const envoi = { fenetre: fenetre.audio.lecteurId, morceauxDisponibles };
				serveur.envois.push(envoi);
				await avantEnregistrement?.(envoi);
				serveur.dernier = morceauxDisponibles;
				for (const attente of attentes.filter(({ valeur }) => valeur === morceauxDisponibles)) attente.resolve();
				attentes = attentes.filter(({ valeur }) => valeur !== morceauxDisponibles);
				return { statut: 200 };
			} });
			const salle = monterMusiqueDAttenteSalle(fenetre.audio, contexte);
			t.after(() => salle.demonter());
			changerEtat('synchronisee');
			// Sans vue où l'Animateur laisse jouer la musique, la salle la garde muette.
			salle.afficher({ musiqueAttente: { etat: 'JOUE', morceauxDisponibles: serveur.dernier } });
			return changerEtat;
		},
		attendre(valeur) {
			if (serveur.dernier === valeur) return Promise.resolve();
			return new Promise(resolve => attentes.push({ valeur, resolve }));
		},
		absences: () => serveur.envois.filter(({ morceauxDisponibles }) => !morceauxDisponibles),
	});
}

test('les fenêtres de salle publient à tour de rôle et revérifient les morceaux avant une absence', { timeout: 3000 }, async t => {
	const premierEnvoi = Promise.withResolvers(), confirmation = Promise.withResolvers();
	const serveur = serveurDeDisponibilite(({ fenetre }) => {
		if (fenetre !== 'première') return;
		premierEnvoi.resolve();
		return confirmation.promise;
	});
	const premiere = fenetreDeSalle(t, 100, 'première');
	const deuxieme = fenetreDeSalle(t, 200, 'deuxième');
	const troisieme = fenetreDeSalle(t, 300, 'troisième');
	for (const fenetre of [premiere, deuxieme, troisieme]) serveur.brancher(t, fenetre);
	premiere.audio.choisirDossier([]);
	await premierEnvoi.promise;
	deuxieme.audio.choisirDossier([]);
	await laisserPasser();
	troisieme.audio.choisirDossier([morceauAttente('Ambiance')]);
	await laisserPasser();
	const envoisAvantConfirmation = [...serveur.envois];
	confirmation.resolve();
	assert.deepEqual(envoisAvantConfirmation, [{ fenetre: 'première', morceauxDisponibles: false }],
		'aucune autre écriture ne part avant la confirmation de la première');
	await serveur.attendre(true);
	assert.deepEqual(serveur.envois.slice(1), [{ fenetre: 'troisième', morceauxDisponibles: true }],
		'la deuxième fenêtre voit les morceaux de la troisième quand vient son tour');
});

test('la Console n’affiche aucun morceau dans Attente que si aucune fenêtre de salle n’en a', { timeout: 3000 }, async t => {
	const serveur = serveurDeDisponibilite();
	const ancienne = fenetreDeSalle(t, 100, 'ancienne');
	serveur.brancher(t, ancienne);
	const morceau = morceauAttente('Ambiance');
	const premiere = ancienne.attendreLecture();
	ancienne.audio.choisirDossier([morceau]);
	await ancienne.audio.activerSon();
	assert.equal(await premiere, morceau);
	await serveur.attendre(true);

	const abime = morceauAttente('Abîmé');
	const nouvelle = fenetreDeSalle(t, 200, 'nouvelle', { illisibles: [abime] });
	serveur.brancher(t, nouvelle);
	await nouvelle.audio.activerSon();
	nouvelle.audio.choisirDossier([{ name: 'Artiste - Chanson.mp3', webkitRelativePath: 'Musique/Artiste - Chanson.mp3' }]);
	await laisserPasser();
	assert.equal(serveur.dernier, true, 'un dossier sans Attente ne cache pas les morceaux de l’autre fenêtre');

	const reprise = ancienne.attendreLecture();
	nouvelle.audio.choisirDossier([abime]);
	assert.equal(await reprise, morceau);
	assert.equal(nouvelle.audio.etat().morceauxDisponibles, false);
	await laisserPasser();
	assert.equal(serveur.dernier, true, 'des morceaux illisibles non plus');

	// La plus récente prend la main, puis la rend avec un dossier vide : l'ancienne a toujours ses morceaux.
	const autre = morceauAttente('Autre');
	const relais = nouvelle.attendreLecture();
	nouvelle.audio.choisirDossier([autre]);
	assert.equal(await relais, autre);
	const retour = ancienne.attendreLecture();
	nouvelle.audio.choisirDossier([]);
	assert.equal(await retour, morceau);
	await laisserPasser();
	assert.deepEqual(serveur.absences(), [], 'aucune absence signalée tant qu’une fenêtre a des morceaux');

	// Plus aucune fenêtre n'a de morceaux : la Console l'apprend.
	ancienne.audio.choisirDossier([]);
	await serveur.attendre(false);
	assert.notDeepEqual(serveur.absences(), []);
});

test('la fenêtre de salle sans morceaux signale leur absence quand se ferme celle qui en avait', { timeout: 3000 }, async t => {
	const serveur = serveurDeDisponibilite();
	const ancienne = fenetreDeSalle(t, 100, 'ancienne');
	serveur.brancher(t, ancienne);
	const premiere = ancienne.attendreLecture();
	ancienne.audio.choisirDossier([morceauAttente('Ambiance')]);
	await ancienne.audio.activerSon();
	await premiere;
	await serveur.attendre(true);

	const nouvelle = fenetreDeSalle(t, 200, 'nouvelle');
	serveur.brancher(t, nouvelle);
	await nouvelle.audio.activerSon();
	nouvelle.audio.choisirDossier([]);
	await laisserPasser();
	assert.equal(serveur.dernier, true);

	await ancienne.audio.detruire();
	await serveur.attendre(false);
	assert.deepEqual(serveur.absences(), [{ fenetre: 'nouvelle', morceauxDisponibles: false }]);
});

test('une absence décidée pendant une coupure ne part pas si une autre fenêtre a pris des morceaux entre-temps', { timeout: 3000 }, async t => {
	const serveur = serveurDeDisponibilite();
	const ancienne = fenetreDeSalle(t, 100, 'ancienne');
	const changerEtat = serveur.brancher(t, ancienne);
	ancienne.audio.choisirDossier([morceauAttente('Ambiance')]);
	await serveur.attendre(true);
	changerEtat('coupure');
	ancienne.audio.choisirDossier([]);
	await laisserPasser();

	const nouvelle = fenetreDeSalle(t, 200, 'nouvelle');
	serveur.brancher(t, nouvelle);
	nouvelle.audio.choisirDossier([morceauAttente('Autre')]);
	await serveur.attendre(true);
	changerEtat('synchronisee');
	await laisserPasser();
	assert.deepEqual(serveur.absences(), [], 'la nouvelle fenêtre a des morceaux depuis la coupure');

	nouvelle.audio.choisirDossier([]);
	await serveur.attendre(false);
});

// Une page `ecran.html` réduite à sa zone audio, avec de faux éléments média et les vrais Web Locks de Node.
function ecranDeSalle(t, ouverteA, { verrous = navigator.locks, soireeId = 'soiree', bibliotheque = CATALOGUE_VIDE } = {}) {
	const document = creerDocument(), creer = document.createElement;
	let lecture = Promise.withResolvers();
	document.createElement = tag => {
		const element = creer(tag);
		if (tag !== 'audio' && tag !== 'video') return element;
		let source = '';
		Object.assign(element, { currentTime: 0, paused: true, load() {}, pause() { element.paused = true; } });
		Object.defineProperty(element, 'src', { get: () => source, set(valeur) {
			source = valeur;
			if (/^(blob:|\/bibliotheque\/)/.test(valeur)) queueMicrotask(() => element.dispatchEvent({ type: 'loadedmetadata' }));
		} });
		const retirer = element.removeAttribute.bind(element);
		element.removeAttribute = nom => { if (nom === 'src') source = ''; retirer(nom); };
		element.play = async () => {
			element.paused = false;
			if (tag === 'audio' && /^(blob:|\/bibliotheque\/)/.test(source)) lecture.resolve(source);
		};
		if (tag === 'audio') ecran.audio = element;
		return element;
	};
	document.defaultView = { navigator: verrous ? { locks: verrous } : {}, BroadcastChannel, performance: { timeOrigin: ouverteA },
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
		credit: element('p', 'data-credit-salle'),
	};
	document.body.append(zone);
	ecran.salle = monterAudioSalle(zone, soireeId, { bibliotheque });
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

test('deux Soirées du même navigateur signalent chacune la disponibilité de leurs morceaux', { timeout: 3000 }, async t => {
	const soireeA = 'ed44a21d-dc1c-4245-8bc3-ec7e91594210', soireeB = 'ed44a21d-dc1c-4245-8bc3-ec7e91594211';
	const envois = [];
	const envoiB = Promise.withResolvers(), confirmationB = Promise.withResolvers();
	function ouvrirSalle(ouverteA, soireeId) {
		const ecran = ecranDeSalle(t, ouverteA, { soireeId });
		const { contexte, changerEtat } = creerContexte({ soireeId,
			async envoyer(methode, route, { morceauxDisponibles }) {
				envois.push({ route, morceauxDisponibles });
				if (soireeId === soireeB) {
					envoiB.resolve();
					await confirmationB.promise;
				}
				return { statut: 200 };
			},
		});
		const salle = monterMusiqueDAttenteSalle(ecran.salle, contexte);
		t.after(() => salle.demonter());
		changerEtat('synchronisee');
		return ecran;
	}
	const premiere = ouvrirSalle(100, soireeA), deuxieme = ouvrirSalle(200, soireeB);
	deuxieme.choisirDossier([morceauAttente('Ambiance')]);
	await envoiB.promise;
	premiere.choisirDossier([]);
	await laisserPasser();
	const envoisPendantB = [...envois];
	confirmationB.resolve();
	assert.deepEqual(envoisPendantB, [
		{ route: `/api/soirees/${soireeB}/musique-attente/disponibilite`, morceauxDisponibles: true },
		{ route: `/api/soirees/${soireeA}/musique-attente/disponibilite`, morceauxDisponibles: false },
	], 'les morceaux et la publication en cours de l’autre Soirée ne bloquent pas mon absence');
});

test('deux Soirées jouent leur Musique d’attente indépendamment, même pendant le son de Manche de l’une', { timeout: 3000 }, async t => {
	const premiere = ecranDeSalle(t, 100, { soireeId: 'premiere', bibliotheque: CATALOGUE });
	const seconde = ecranDeSalle(t, 200, { soireeId: 'seconde', bibliotheque: CATALOGUE });
	const lecturePremiere = premiere.attendreLecture();
	premiere.activer.click();
	const morceau = await lecturePremiere;
	const lectureSeconde = seconde.attendreLecture();
	seconde.activer.click();
	assert.equal(await lectureSeconde, morceau);
	assert.equal(premiere.audio.paused, false, 'les deux Soirées jouent ensemble');
	assert.equal(await seconde.salle.signalerSonDeManche(true), true);
	assert.equal(seconde.audio.paused, true);
	assert.equal(premiere.audio.paused, false, 'le son de Manche ne suspend que sa Soirée');
});

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

test('sans Web Locks, en http:// hors localhost, l’Écran de salle joue seul la Musique d’attente', { timeout: 3000 }, async t => {
	const ecran = ecranDeSalle(t, 100, { verrous: null });
	const lecture = ecran.attendreLecture();
	ecran.activer.click();
	await ecran.sonActive();
	ecran.choisirDossier([morceauAttente('Ambiance')]);
	assert.equal(await lecture, 'blob:Ambiance.ogg');
	assert.equal(ecran.joue(), true);
});

test('sans dossier, la Bibliothèque de base joue en attente dès le son activé, et la fenêtre devient prête pour les Chansons', { timeout: 3000 }, async t => {
	const fenetre = fenetreDeSalle(t, 100, 'bibliotheque', { bibliotheque: AVEC_CHANSON });
	assert.equal(fenetre.audio.etat().morceauxDisponibles, true, 'la bibliothèque a des morceaux, sans aucun dossier');
	assert.equal(fenetre.audio.etat().fichiers, null, 'pas encore prête : le son n’est pas activé');
	assert.deepEqual(await candidaturesDe('bibliotheque'), []);
	const lecture = fenetre.attendreLecture();
	await fenetre.audio.activerSon();
	assert.deepEqual(await lecture, libre);
	assert.deepEqual(fenetre.audio.etat().fichiers, [], 'le clic rend la fenêtre prête pour les Chansons libres, sans fichier du Lieu');
	const sansChanson = fenetreDeSalle(t, 300, 'attente-seule', { bibliotheque: CATALOGUE });
	await sansChanson.audio.activerSon();
	assert.equal(sansChanson.audio.etat().fichiers, null, 'sans Chanson libre, rien à lire au Karaoké : la fenêtre attend un dossier');
	await terminerMicrotaches();
	assert.deepEqual(fenetre.audio.etat().morceauEnCours, libre);
	// Un dossier sans Attente garde les morceaux de la bibliothèque.
	fenetre.audio.choisirDossier([{ name: 'Artiste - Chanson.mp3', webkitRelativePath: 'Musique/Artiste - Chanson.mp3' }]);
	assert.equal(fenetre.audio.etat().morceauxDisponibles, true);
});

test('l’Écran de salle affiche le crédit du morceau de la bibliothèque qui joue, jamais celui d’un fichier du Lieu', { timeout: 3000 }, async t => {
	const ecran = ecranDeSalle(t, 100, { bibliotheque: Promise.resolve(CATALOGUE) });
	assert.equal(ecran.activer.hidden, false, 'Activer le son se propose dès l’ouverture');
	assert.equal(ecran.alerte.hidden, true);
	assert.equal(ecran.credit.hidden, true);
	await new Promise(resolve => { const retirer = ecran.salle.surChangement(({ morceauxDisponibles }) => {
		if (morceauxDisponibles) { retirer(); resolve(); } }); });
	assert.equal(ecran.aide.textContent, 'Bibliothèque de base');
	const lecture = ecran.attendreLecture();
	ecran.activer.click();
	assert.equal(await lecture, '/bibliotheque/attente/kevin-macleod-ambiance.mp3');
	await terminerMicrotaches();
	assert.equal(ecran.credit.hidden, false);
	assert.equal(ecran.credit.textContent, 'Musique : « Ambiance », Kevin MacLeod · CC BY 4.0');
	const relais = ecran.attendreLecture();
	ecran.choisirDossier([{ name: 'Kevin MacLeod - Ambiance.ogg', webkitRelativePath: 'Musique/Attente/Kevin MacLeod - Ambiance.ogg' }]);
	assert.equal(await relais, 'blob:Kevin MacLeod - Ambiance.ogg', 'le morceau du Lieu remplace son homonyme');
	await terminerMicrotaches();
	assert.equal(ecran.credit.hidden, true);
	assert.equal(ecran.aide.textContent, '1 fichier audio en plus de la Bibliothèque de base');
});

test('une fenêtre qui n’a que la Bibliothèque de base ne prend pas la Musique d’attente à celle qui joue le dossier du Lieu', { timeout: 3000 }, async t => {
	const lieu = fenetreDeSalle(t, 100, 'lieu', { bibliotheque: CATALOGUE });
	const morceau = morceauAttente('Ambiance du Lieu');
	lieu.audio.choisirDossier([morceau]);
	const premiere = lieu.attendreLecture();
	await lieu.audio.activerSon();
	const joue = await premiere;
	assert.ok([morceau, libre].some(m => JSON.stringify(m) === JSON.stringify(joue)));
	const nouvelle = fenetreDeSalle(t, 200, 'libre', { bibliotheque: CATALOGUE });
	await nouvelle.audio.activerSon();
	assert.equal((await candidaturesDe('libre')).length, 1, 'elle candidate : elle a des morceaux');
	await laisserPasser();
	assert.equal(nouvelle.audible, undefined, 'mais la fenêtre du dossier garde la main');
	assert.notEqual(lieu.audible, undefined);
	// Sans dossier ailleurs, la bibliothèque joue.
	const relais = nouvelle.attendreLecture();
	await lieu.audio.detruire();
	assert.deepEqual(await relais, libre);
});
