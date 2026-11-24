import test from 'node:test';
import assert from 'node:assert/strict';
import { lireAcces, lireIdentiteEcran } from '../../src/main/resources/static/js/identite.mjs';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';

const json = new Headers({ 'Content-Type': 'application/json' });
const probleme = new Headers({ 'Content-Type': 'application/problem+json;charset=UTF-8' });

test('la clé reçue par l’Écran authentifie CONNECT et la capture du canal de sa Soirée', { timeout: 2000 }, async () => {
	const soiree = '00000001-0000-4000-8000-000000000000';
	const cle = 'a'.repeat(43);
	const stockage = new Map();
	const memoire = { lire: nom => stockage.get(nom) ?? null, ecrire: (nom, valeur) => stockage.set(nom, valeur) };
	const adresse = new URL(`https://exemple.test/ecran.html#soiree=${soiree}&cle=${cle}`);
	let adresseNettoyee;
	const { recherche, jeton } = lireIdentiteEcran(adresse, memoire, url => { adresseNettoyee = url; });
	assert.equal(adresseNettoyee, `/ecran.html?soiree=${soiree}`);
	assert.equal(lireIdentiteEcran(new URL(adresseNettoyee, adresse.origin), memoire).jeton, cle);
	const autreSoiree = '00000002-0000-4000-8000-000000000000';
	assert.equal(lireIdentiteEcran(new URL(`?soiree=${autreSoiree}`, adresse), memoire).jeton, null);

	let connexion;
	let abonnement;
	let capture;
	const abonnementId = '00000003-0000-4000-8000-000000000000';
	const transport = creerTransport({ jeton }, {
		adresse: adresse.href,
		Client: class {
			constructor(options) { connexion = options; }
			activate() {}
			deactivate() {}
			subscribe(destination, _rappel, { id }) { abonnement = { destination, id }; }
		},
		requete: async (url, options) => {
			capture = { route: `${url.pathname}${url.search}`, autorisation: options.headers.Authorization };
			return Response.json({ abonnementId, revision: 0, heureServeur: '2026-11-24T20:00:00Z', vue: {} });
		},
	});
	const { promise: synchronisee, resolve: resoudre } = Promise.withResolvers();
	const suivi = suivreVue({ ...lireAcces('ecran', recherche, jeton), surVue: () => {}, surEtat(etat) {
		if (etat === 'synchronisee') resoudre();
	} }, { transport, nouvelIdentifiant: () => abonnementId, programmer: () => {}, annuler: () => {} });
	try {
		connexion.onConnect();
		assert.equal(connexion.connectHeaders.Authorization, `Bearer ${cle}`);
		assert.deepEqual(abonnement, { destination: `/topic/soirees/${soiree}/salle`, id: abonnementId });
		assert.deepEqual(capture, { route: `/api/soirees/${soiree}/etat/salle?abonnementId=${abonnementId}`,
			autorisation: `Bearer ${cle}` });
		await synchronisee;
	} finally {
		suivi.arreter();
	}
});

test('Bearer authentifie CONNECT, capture et commandes sans perdre clé idempotente ni annulation', async () => {
	const requetes = [];
	const clients = [];
	const transport = creerTransport({ jeton: 'demo:JOUEUR:soiree:acteur' }, {
		adresse: 'https://exemple.test/manette.html',
		Client: class {
			constructor(options) { clients.push(options); }
			activate() {}
			deactivate() {}
		},
		requete: async (url, options) => {
			requetes.push({ url, options });
			return { status: 200, headers: json, text: async () => '{"ok":true}' };
		},
	});
	transport.ouvrir({});
	assert.equal(clients[0].connectHeaders.Authorization, 'Bearer demo:JOUEUR:soiree:acteur');
	const signal = new AbortController().signal;
	assert.deepEqual(await transport.envoyer('POST', '/commande', { action: 'PAUSE' }, {
		entetes: { 'Idempotency-Key': 'envoi-1' }, signal,
	}), { statut: 200, corps: { ok: true } });
	assert.equal(requetes[0].options.headers.Authorization, clients[0].connectHeaders.Authorization);
	assert.equal(requetes[0].options.headers['Idempotency-Key'], 'envoi-1');
	assert.equal(requetes[0].options.signal, signal);
	await transport.capturer('/etat?autre=oui', 'abonnement-1', signal);
	assert.equal(requetes[1].url.searchParams.get('abonnementId'), 'abonnement-1');
	assert.equal(requetes[1].options.headers.Authorization, clients[0].connectHeaders.Authorization);
	assert.equal(requetes[1].options.body, undefined);
});

test('la Console transmet session et CSRF sur HTTP et CONNECT ; 204, Probleme et réseau restent distincts', async () => {
	let optionsConnexion;
	const reponses = [
		{ status: 204, headers: new Headers(), text: async () => '' },
		{ status: 409, headers: probleme, text: async () => '{"code":"COMMANDE_PERIMEE","detail":"Commande périmée."}' },
	];
	const transport = creerTransport({ csrf: 'csrf-session' }, {
		adresse: 'https://exemple.test/console.html',
		Client: class { constructor(options) { optionsConnexion = options; } activate() {} },
		requete: async (_url, options) => {
			assert.equal(options.credentials, 'same-origin');
			assert.equal(options.headers['X-CSRF-TOKEN'], 'csrf-session');
			assert.equal(options.headers.Authorization, undefined);
			if (!reponses.length) throw new Error('Hors ligne');
			return reponses.shift();
		},
	});
	transport.ouvrir({});
	assert.deepEqual(optionsConnexion.connectHeaders, { 'X-CSRF-TOKEN': 'csrf-session' });
	assert.deepEqual(await transport.envoyer('DELETE', '/commande'), { statut: 204, corps: null });
	assert.deepEqual(await transport.envoyer('GET', '/commande'), { statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Commande périmée.' } });
	await assert.rejects(transport.envoyer('POST', '/commande'), /Hors ligne/);
	await assert.rejects(transport.envoyer('GET', 'https://autre.test/'), /rester sur CroustyQuizz/);
});

test('une page d’erreur HTML d’un proxy donne un refus lisible, jamais une SyntaxError ni un faux succès', async () => {
	const page = '<html><body><h1>502 Bad Gateway</h1></body></html>';
	const html = new Headers({ 'Content-Type': 'text/html; charset=utf-8' });
	const reponses = [
		{ status: 502, headers: html, text: async () => page },
		{ status: 500, headers: json, text: async () => '<html>tronqué' },
		{ status: 200, headers: html, text: async () => page },
		{ status: 200, headers: new Headers({ 'Content-Type': 'application/json; charset=utf-8' }), text: async () => '{"ok":true}' },
	];
	const transport = creerTransport({}, { adresse: 'https://exemple.test/manette.html', requete: async () => reponses.shift() });
	assert.deepEqual(await transport.envoyer('POST', '/commande', {}), { statut: 502, corps: { detail: 'Le serveur ne répond pas correctement.' } });
	assert.deepEqual(await transport.envoyer('POST', '/commande', {}), { statut: 500, corps: { detail: 'Le serveur ne répond pas correctement.' } });
	await assert.rejects(transport.envoyer('POST', '/commande', {}), { message: 'Le serveur ne répond pas correctement.' });
	assert.deepEqual(await transport.envoyer('GET', '/etat'), { statut: 200, corps: { ok: true } });
});
