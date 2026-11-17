import test from 'node:test';
import assert from 'node:assert/strict';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';

const json = new Headers({ 'Content-Type': 'application/json' });
const probleme = new Headers({ 'Content-Type': 'application/problem+json;charset=UTF-8' });

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
