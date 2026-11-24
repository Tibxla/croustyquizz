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
