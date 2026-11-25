import assert from 'node:assert/strict';
import test from 'node:test';

import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';
import { monter as monterConsole } from '../../src/main/resources/static/js/photos/console.mjs';
import { monter as monterEcran } from '../../src/main/resources/static/js/photos/ecran.mjs';
import { monter as monterManette, preparerJpeg } from '../../src/main/resources/static/js/photos/manette.mjs';

function banc() {
	const document = creerDocument();
	const zone = document.createElement('div');
	document.body.append(zone);
	const envois = [];
	let connexion;
	const contexte = {
		soireeId: 'soiree',
		envoyer: (...args) => new Promise(resolve => envois.push({ args, resolve })),
		surEtatConnexion(rappel) {
			connexion = rappel;
			rappel('synchronisee');
			return () => { connexion = null; };
		},
	};
	return { zone, envois, contexte, connecter: etat => connexion(etat) };
}

const ouverte = { soiree: { etat: 'OUVERTE' }, photos: { ouvertes: true, enAttente: [] } };
const jpeg = new Blob(['photo'], { type: 'image/jpeg' });

test('Manette : erreur de lecture, nouvel essai, puis confirmation du serveur', async () => {
	const { zone, envois, contexte } = banc();
	let cle = 0;
	const module = monterManette(zone, contexte, {
		cle: () => `cle-${++cle}`,
		preparer: async fichier => {
			if (fichier === 'illisible') throw new Error('Cette image est illisible.');
			return jpeg;
		},
	});
	module.afficher(ouverte);
	const champ = zone.querySelector('input');
	assert.ok(champ);
	champ.files = ['illisible'];
	champ.dispatchEvent({ type: 'change' });
	await terminerMicrotaches();
	assert.equal(zone.querySelector('.champ--erreur')?.textContent, 'Cette image est illisible.');
	assert.equal(envois.length, 0);
	champ.files = ['lisible'];
	champ.dispatchEvent({ type: 'change' });
	await terminerMicrotaches();
	assert.equal(envois.length, 1);
	assert.equal(envois[0].args[2], jpeg);
	assert.equal(envois[0].args[3].entetes['Idempotency-Key'], 'cle-1');
	assert.doesNotMatch(zone.textContent, /Photo envoyée/);
	envois[0].resolve({ statut: 201, corps: { photoId: 'photo' } });
	await terminerMicrotaches();
	assert.match(zone.textContent, /Photo envoyée/);
	module.afficher({ ...ouverte, photos: { ouvertes: false } });
	assert.equal(zone.hidden, true);
	module.demonter();
	assert.equal(zone.childElementCount, 0);
});

const fichier = (name, lastModified = 1) => ({ name, size: 12, lastModified, type: 'image/jpeg' });

function manette(options = {}) {
	const banc_ = banc();
	let cles = 0, preparations = 0;
	const module = monterManette(banc_.zone, banc_.contexte, {
		cle: () => `cle-${++cles}`,
		preparer: async () => { preparations++; return new Blob([`jpeg-${preparations}`], { type: 'image/jpeg' }); },
		...options,
	});
	module.afficher(ouverte);
	const champ = banc_.zone.querySelector('input');
	const choisir = async f => {
		champ.files = [f];
		champ.dispatchEvent({ type: 'change' });
		await terminerMicrotaches();
	};
	return { ...banc_, module, champ, choisir, preparations: () => preparations };
}

test('Manette : le sélecteur propose aussi la photothèque', () => {
	const { champ, module } = manette();
	assert.equal(champ.getAttribute('accept'), 'image/*');
	assert.equal(champ.getAttribute('capture'), null);
	module.demonter();
});

test('Manette : même fichier après une confirmation inconnue ou un 5xx, même JPEG et même clé', async () => {
	const { envois, choisir, preparations, module, zone } = manette();
	await choisir(fichier('salle.jpg'));
	envois[0].resolve(Promise.reject(new TypeError('réseau')));
	await terminerMicrotaches();
	assert.match(zone.textContent, /Impossible de confirmer/);
	await choisir(fichier('salle.jpg'));
	assert.equal(envois[1].args[3].entetes['Idempotency-Key'], 'cle-1');
	assert.equal(envois[1].args[2], envois[0].args[2]);
	assert.equal(preparations(), 1);
	envois[1].resolve({ statut: 503, corps: { detail: 'La mémoire des photos est pleine. Réessaie plus tard.' } });
	await terminerMicrotaches();
	assert.match(zone.textContent, /mémoire des photos est pleine/);
	await choisir(fichier('salle.jpg'));
	assert.equal(envois[2].args[3].entetes['Idempotency-Key'], 'cle-1');
	envois[2].resolve({ statut: 201, corps: { photoId: 'photo' } });
	await terminerMicrotaches();
	await choisir(fichier('salle.jpg'));
	assert.equal(envois[3].args[3].entetes['Idempotency-Key'], 'cle-2', 'un succès libère la clé');
	envois[3].resolve({ statut: 503, corps: { detail: 'Indisponible.' } });
	await terminerMicrotaches();
	await choisir(fichier('autre.jpg'));
	assert.equal(envois[4].args[3].entetes['Idempotency-Key'], 'cle-3', 'un autre fichier tire une clé neuve');
	assert.equal(preparations(), 3);
	module.demonter();
});

test('Manette : pendant l’envoi, le champ garde le focus et refuse un nouveau choix', async () => {
	const { envois, champ, choisir, module } = manette();
	champ.focus();
	await choisir(fichier('salle.jpg'));
	assert.equal(champ.disabled, false);
	assert.equal(champ.getAttribute('aria-disabled'), 'true');
	assert.equal(champ.ownerDocument.activeElement, champ);
	let empeche = false;
	champ.dispatchEvent({ type: 'click', preventDefault: () => { empeche = true; } });
	assert.ok(empeche, 'le sélecteur ne s’ouvre pas');
	await choisir(fichier('autre.jpg'));
	assert.equal(envois.length, 1);
	envois[0].resolve({ statut: 201, corps: { photoId: 'photo' } });
	await terminerMicrotaches();
	assert.equal(champ.getAttribute('aria-disabled'), 'false');
	module.demonter();
});

test('Manette : couper les envois masque le champ sans annuler l’envoi parti, dont la réponse s’affiche', async () => {
	const { envois, champ, choisir, module, zone } = manette();
	await choisir(fichier('salle.jpg'));
	module.afficher({ ...ouverte, photos: { ouvertes: false } });
	assert.equal(envois[0].args[3].signal.aborted, false);
	assert.equal(champ.hidden, true);
	assert.equal(zone.hidden, false);
	envois[0].resolve({ statut: 201, corps: { photoId: 'photo' } });
	await terminerMicrotaches();
	assert.match(zone.textContent, /Photo envoyée/);
	assert.equal(zone.hidden, false);
	module.afficher({ ...ouverte, photos: { ouvertes: true } });
	assert.equal(champ.hidden, false);
	module.demonter();
});

test('Compression : orientation EXIF, rapport conservé, qualité fixe et nouvelle taille sous 1,5 Mo', async () => {
	const dimensions = [];
	let ferme = false;
	const toile = {
		getContext: () => ({ drawImage: () => dimensions.push([toile.width, toile.height]) }),
		toBlob(rappel, type, qualite) {
			assert.equal(type, 'image/jpeg');
			assert.equal(qualite, 0.82);
			rappel({ size: dimensions.length === 1 ? 1_500_001 : 1_500_000 });
		},
	};
	const resultat = await preparerJpeg('original', {
		bitmap: async (fichier, options) => {
			assert.equal(fichier, 'original');
			assert.deepEqual(options, { imageOrientation: 'from-image' });
			return { width: 1000, height: 2000, close: () => { ferme = true; } };
		},
		canvas: () => toile,
	});
	assert.equal(resultat.size, 1_500_000);
	assert.deepEqual(dimensions, [[800, 1600], [640, 1280]]);
	assert.ok(ferme);
});

test('Console : file dans l’ordre, changement de réglage idempotent et libération des vignettes', async () => {
	const { zone, envois, contexte } = banc();
	contexte.chargerImage = async () => jpeg;
	const liberees = [];
	const module = monterConsole(zone, contexte, {
		cle: () => 'cle', urlObjet: () => 'blob:vignette', libererUrl: url => liberees.push(url),
	});
	module.afficher({ ...ouverte, photos: { ouvertes: true, enAttente: [
		{ photoId: 'b', pseudo: 'Béa' }, { photoId: 'a', pseudo: 'Ana' },
	] } });
	await terminerMicrotaches();
	assert.deepEqual(zone.querySelectorAll('li').map(ligne => ligne.querySelector('strong').textContent), ['Béa', 'Ana']);
	const interrupteur = zone.querySelector('input');
	interrupteur.checked = false;
	interrupteur.dispatchEvent({ type: 'change' });
	assert.equal(interrupteur.checked, true);
	assert.deepEqual(envois[0].args.slice(0, 3), ['PUT', '/api/soirees/soiree/photos/reglages', { ouvertes: false }]);
	assert.equal(envois[0].args[3].entetes['Idempotency-Key'], 'cle');
	module.afficher({ ...ouverte, photos: { ouvertes: false, enAttente: [] } });
	assert.equal(interrupteur.checked, false);
	assert.deepEqual(liberees, ['blob:vignette', 'blob:vignette']);
	module.demonter();
});

function consoleAvecFile(ids) {
	const banc_ = banc();
	banc_.contexte.chargerImage = async () => jpeg;
	const module = monterConsole(banc_.zone, banc_.contexte, { cle: () => 'cle', urlObjet: () => 'blob:v', libererUrl: () => {} });
	const vue = liste => ({ ...ouverte, photos: { ouvertes: true, enAttente: liste.map(id => ({ photoId: id, pseudo: id.toUpperCase() })) } });
	module.afficher(vue(ids));
	const lignes = () => banc_.zone.querySelectorAll('li');
	for (const ligne of lignes()) ligne.querySelector('img').dispatchEvent({ type: 'load' });
	return { ...banc_, module, vue, lignes };
}

test('Console : l’interrupteur porte son libellé visible comme nom accessible', () => {
	const { zone, module } = consoleAvecFile([]);
	const interrupteur = zone.querySelector('input');
	const etiquette = zone.querySelector('label');
	assert.equal(interrupteur.getAttribute('aria-labelledby'), null);
	assert.equal(etiquette.getAttribute('for'), interrupteur.id);
	assert.equal(etiquette.textContent, 'Envois ouverts');
	assert.equal(etiquette.querySelector('span').getAttribute('aria-hidden'), null);
	module.demonter();
});

test('Console : pendant une modération, le bouton garde le focus, puis la ligne suivante le reçoit', async () => {
	const { zone, envois, module, vue, lignes } = consoleAvecFile(['a', 'b', 'c']);
	const refuser = lignes()[0].querySelectorAll('button')[1];
	refuser.focus();
	refuser.dispatchEvent({ type: 'click' });
	assert.equal(envois.length, 1);
	assert.equal(refuser.disabled, false);
	assert.equal(refuser.getAttribute('aria-disabled'), 'true');
	refuser.dispatchEvent({ type: 'click' });
	assert.equal(envois.length, 1, 'la garde ignore un second clic');
	assert.equal(zone.ownerDocument.activeElement, refuser);
	envois[0].resolve({ statut: 200, corps: {} });
	await terminerMicrotaches();
	module.afficher(vue(['b', 'c']));
	assert.equal(zone.ownerDocument.activeElement, lignes()[0].querySelectorAll('button')[1]);
	assert.match(zone.ownerDocument.activeElement.getAttribute('aria-label'), /de B$/);

	const accepterC = lignes()[1].querySelectorAll('button')[0];
	accepterC.focus();
	module.afficher(vue(['b']));
	assert.equal(zone.ownerDocument.activeElement, lignes()[0].querySelectorAll('button')[0], 'sans suivante, la précédente');
	module.afficher(vue([]));
	assert.equal(zone.ownerDocument.activeElement, zone.querySelector('h2'));
	assert.equal(zone.querySelector('h2').getAttribute('tabindex'), '-1');
	module.demonter();
});

test('Console : l’interrupteur reste focalisable pendant l’envoi et ignore un nouveau changement', () => {
	const { zone, envois, module } = consoleAvecFile([]);
	const interrupteur = zone.querySelector('input');
	interrupteur.focus();
	interrupteur.checked = false;
	interrupteur.dispatchEvent({ type: 'change' });
	assert.equal(interrupteur.getAttribute('aria-disabled'), 'true');
	assert.equal(interrupteur.disabled, false);
	let empeche = false;
	interrupteur.dispatchEvent({ type: 'click', preventDefault: () => { empeche = true; } });
	assert.ok(empeche);
	interrupteur.checked = false;
	interrupteur.dispatchEvent({ type: 'change' });
	assert.equal(envois.length, 1);
	assert.equal(interrupteur.checked, true);
	module.demonter();
});

test('Salle : seule la photo courante est lue et le créneau ne repart pas au rechargement de la vue', async () => {
	const { zone, contexte } = banc();
	const chargements = [], liberees = [], minuteurs = [];
	contexte.maintenantServeur = () => Date.parse('2026-11-24T20:00:05Z');
	contexte.chargerImage = async chemin => { chargements.push(chemin); return jpeg; };
	const module = monterEcran(zone, contexte, {
		urlObjet: () => 'blob:affiche', libererUrl: url => liberees.push(url),
		programmer: (action, delai) => { minuteurs.push({ action, delai }); return () => {}; },
	});
	const affiche = { photos: { photoId: 'a', echeance: '2026-11-24T20:00:08Z' }, mancheActive: null };
	module.afficher(affiche);
	await terminerMicrotaches();
	zone.querySelector('img').dispatchEvent({ type: 'load' });
	assert.equal(zone.hidden, false);
	assert.equal(minuteurs.at(-1).delai, 3000);
	module.afficher(affiche);
	assert.deepEqual(chargements, ['/api/soirees/soiree/photos/a']);
	module.afficher({ ...affiche, mancheActive: { id: 'manche' } });
	assert.equal(zone.hidden, true);
	assert.deepEqual(liberees, ['blob:affiche']);
	module.demonter();
});

test('Transport : JPEG envoyé brut et téléchargé avec la même authentification', async () => {
	const appels = [];
	const transport = creerTransport({ jeton: 'secret' }, {
		adresse: 'https://exemple.test/manette.html',
		requete: async (url, options) => {
			appels.push({ chemin: url.pathname, ...options });
			return appels.length === 1
				? new Response('{"photoId":"photo"}', { status: 201, headers: { 'Content-Type': 'application/json' } })
				: new Response(jpeg);
		},
	});
	assert.equal((await transport.envoyer('POST', '/photos', jpeg)).statut, 201);
	assert.equal(appels[0].body, jpeg);
	assert.equal(appels[0].headers['Content-Type'], 'image/jpeg');
	assert.equal((await transport.chargerImage('/photos/photo')).type, 'image/jpeg');
	assert.equal(appels[1].headers.Authorization, 'Bearer secret');
	assert.equal(appels[1].cache, 'no-store');
});
