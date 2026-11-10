import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable } from './adaptateur-pilotable.mjs';

test('l’adapter réel abonne STOMP avant HTTP avec le même UUID et arrête ses ressources', async (t) => {
	const clients = [];
	const requetes = [];
	const vues = [];
	const etats = [];
	function remplacerGlobal(nom, valeur) {
		const precedent = Object.getOwnPropertyDescriptor(globalThis, nom);
		Object.defineProperty(globalThis, nom, { configurable: true, writable: true, value: valeur });
		t.after(() => {
			if (precedent) Object.defineProperty(globalThis, nom, precedent);
			else delete globalThis[nom];
		});
	}
	remplacerGlobal('location', { protocol: 'https:', host: 'exemple.test', href: 'https://exemple.test/test/ecran.html' });
	remplacerGlobal('StompJs', { Client: class {
		constructor(options) { this.options = options; clients.push(this); }
		activate() { this.active = true; }
		subscribe(destination, recevoir, entetes) { this.abonnement = { destination, recevoir, entetes }; }
		deactivate() { this.active = false; }
	} });
	remplacerGlobal('fetch', (url, options) => {
		assert.ok(clients[0].abonnement, 'SUBSCRIBE précède la capture HTTP');
		return new Promise(resoudre => requetes.push({ url, options, resoudre }));
	});
	const { transport, ...adaptations } = creerAdaptateurPilotable().adaptations;
	const suivi = suivreVue({ destination: '/topic/salle', routeEtat: '/etat/salle?autre=valeur',
		surVue: (vue, contexte) => vues.push({ vue, ...contexte }), surEtat: etat => etats.push(etat),
	}, adaptations);
	t.after(() => suivi.arreter());
	const client = clients[0];
	assert.equal(client.options.brokerURL, 'wss://exemple.test/ws');
	assert.equal(client.options.reconnectDelay, 0);
	assert.equal(client.options.heartbeatIncoming, 10000);
	assert.equal(client.options.heartbeatOutgoing, 10000);
	client.options.onConnect();
	const id = client.abonnement.entetes.id;
	assert.match(id, /^[0-9a-f-]{36}$/);
	assert.equal(requetes[0].url.searchParams.get('abonnementId'), id);
	assert.equal(requetes[0].url.searchParams.get('autre'), 'valeur');
	assert.equal(requetes[0].options.cache, 'no-store');
	client.abonnement.recevoir({ body: JSON.stringify({ revision: 1, type: 'buzz-recu', donnees: { buzz: ['Alpha'] } }) });
	requetes[0].resoudre({ status: 200, json: async () => ({ revision: 0, abonnementId: id,
		heureServeur: '2026-11-10T20:00:35Z', vue: { buzz: [] },
	}) });
	await setImmediate();
	assert.deepEqual(vues, [
		{ vue: { buzz: [] }, type: 'capture' },
		{ vue: { buzz: ['Alpha'] }, type: 'buzz-recu' },
	]);
	assert.equal(etats.at(-1), 'synchronisee');
	client.abonnement.recevoir({ body: 'JSON invalide' });
	assert.equal(etats.at(-1), 'reconnexion');
	assert.equal(client.active, false);
	assert.equal(requetes[0].options.signal.aborted, true);
});
