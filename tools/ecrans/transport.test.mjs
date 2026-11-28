import test from 'node:test';
import assert from 'node:assert/strict';
import { creerTransport } from '../../src/main/resources/static/js/transport.mjs';

const json = new Headers({ 'Content-Type': 'application/json' });
const probleme = new Headers({ 'Content-Type': 'application/problem+json;charset=UTF-8' });

test('la Console relit le jeton de la session avant chaque CONNECT, y compris après une reconnexion dans un autre onglet', async () => {
	const echanges = [];
	let jeton = 'session-initiale';
	let connexion;
	const transport = creerTransport({ sessionAnimateur: true }, {
		adresse: 'https://exemple.test/console.html',
		Client: class {
			constructor(options) { Object.assign(this, options); }
			activate() {
				connexion = (async () => {
					await this.beforeConnect?.();
					echanges.push(['CONNECT', this.connectHeaders['X-CSRF-TOKEN']]);
				})();
			}
			deactivate() {}
		},
		requete: async (url, options) => {
			echanges.push([options.method, url.pathname]);
			assert.equal(options.credentials, 'same-origin');
			assert.equal(options.cache, 'no-store');
			return { status: 200, headers: json, text: async () => JSON.stringify({ token: jeton }) };
		},
	});
	const premiere = transport.ouvrir({});
	await connexion;
	premiere.fermer();
	jeton = 'session-renouvelee';
	transport.ouvrir({});
	await connexion;
	assert.deepEqual(echanges, [
		['GET', '/api/csrf'], ['CONNECT', 'session-initiale'],
		['GET', '/api/csrf'], ['CONNECT', 'session-renouvelee'],
	]);
});

test('après CSRF_INVALIDE, la Console renouvelle le jeton et rejoue la même mutation une seule fois', async () => {
	const refusee = { statut: 403, corps: { code: 'CSRF_INVALIDE' } };
	for (const derniereReponse of [{ statut: 200, corps: { ok: true } }, refusee]) {
		const echanges = [];
		const signal = new AbortController().signal;
		const commande = { action: 'PAUSE' };
		const jetons = ['session-initiale', 'session-renouvelee'];
		const reponses = [refusee, derniereReponse];
		const transport = creerTransport({ sessionAnimateur: true }, {
			adresse: 'https://exemple.test/console.html',
			requete: async (url, options) => {
				assert.equal(options.signal, signal);
				if (options.method === 'GET') {
					echanges.push(url.pathname);
					return { status: 200, headers: json, text: async () => JSON.stringify({ token: jetons.shift() }) };
				}
				echanges.push([options.method, url.pathname, options.headers['X-CSRF-TOKEN'], options.headers['Idempotency-Key'], options.body]);
				commande.action = 'REPRISE';
				const { statut, corps } = reponses.shift();
				return { status: statut, headers: statut === 403 ? probleme : json, text: async () => JSON.stringify(corps) };
			},
		});
		assert.deepEqual(await transport.envoyer('POST', '/commande', commande, {
			entetes: { 'Idempotency-Key': 'envoi-1' }, signal,
		}), derniereReponse);
		assert.deepEqual(echanges, [
			'/api/csrf', ['POST', '/commande', 'session-initiale', 'envoi-1', '{"action":"PAUSE"}'],
			'/api/csrf', ['POST', '/commande', 'session-renouvelee', 'envoi-1', '{"action":"PAUSE"}'],
		]);
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

test('la Console transmet le CSRF aux mutations et distingue le 204, le Probleme et la panne réseau', async () => {
	const lectures = [];
	const reponses = [
		{ status: 204, headers: new Headers(), text: async () => '' },
		{ status: 409, headers: probleme, text: async () => '{"code":"COMMANDE_PERIMEE","detail":"Commande périmée."}' },
		{ status: 403, headers: probleme, text: async () => '{"code":"ACCES_REFUSE"}' },
	];
	const transport = creerTransport({ sessionAnimateur: true }, {
		adresse: 'https://exemple.test/console.html',
		requete: async (url, options) => {
			assert.equal(options.credentials, 'same-origin');
			assert.equal(options.headers.Authorization, undefined);
			assert.equal(options.headers['X-CSRF-TOKEN'], options.method === 'GET' ? undefined : 'csrf-session');
			if (url.pathname === '/api/csrf') {
				lectures.push(url.pathname);
				return { status: 200, headers: json, text: async () => '{"token":"csrf-session"}' };
			}
			if (!reponses.length) throw new Error('Hors ligne');
			return reponses.shift();
		},
	});
	assert.deepEqual(await transport.envoyer('DELETE', '/commande'), { statut: 204, corps: null });
	assert.deepEqual(await transport.envoyer('GET', '/commande'), { statut: 409, corps: { code: 'COMMANDE_PERIMEE', detail: 'Commande périmée.' } });
	assert.deepEqual(await transport.envoyer('POST', '/commande'), { statut: 403, corps: { code: 'ACCES_REFUSE' } });
	await assert.rejects(transport.envoyer('POST', '/commande'), /Hors ligne/);
	await assert.rejects(transport.envoyer('GET', 'https://autre.test/'), /rester sur CroustyQuizz/);
	assert.deepEqual(lectures, ['/api/csrf']);
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

test('la trame ERROR d’un CONNECT refusé transmet son motif déséchappé ; une erreur WebSocket n’en a pas', () => {
	let options;
	const appels = [];
	const transport = creerTransport({}, {
		adresse: 'https://exemple.test/ecran.html',
		Client: class { constructor(o) { options = o; } activate() {} },
	});
	transport.ouvrir({
		surConnexion: () => appels.push(['connexion']),
		surTrameErreur: motif => appels.push(['refus', motif]),
		surErreur: (...args) => appels.push(['erreur', ...args]),
	});
	// Avant CONNECTED, stompjs livre la valeur telle que Spring l'a échappée.
	options.onStompError({ command: 'ERROR', headers: { message: 'Accès refusé \\c rejoins la Soirée \\\\ avec son code.' }, body: '' });
	options.onStompError({ command: 'ERROR', headers: {}, body: '' });
	options.onWebSocketError(new Event('error'));
	// Après CONNECTED, stompjs déséchappe lui-même : la valeur passe telle quelle.
	options.onConnect();
	options.onStompError({ command: 'ERROR', headers: { message: 'Déjà lisible \\c tel quel' }, body: '' });
	assert.deepEqual(appels, [
		['refus', 'Accès refusé : rejoins la Soirée \\ avec son code.'], ['refus', undefined], ['erreur'],
		['connexion'], ['refus', 'Déjà lisible \\c tel quel'],
	]);
});
