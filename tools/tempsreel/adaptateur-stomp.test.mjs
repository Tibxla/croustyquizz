import test from 'node:test';
import assert from 'node:assert/strict';
import { setImmediate } from 'node:timers/promises';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable } from './adaptateur-pilotable.mjs';

function remplacerGlobal(t, nom, valeur) {
	const precedent = Object.getOwnPropertyDescriptor(globalThis, nom);
	Object.defineProperty(globalThis, nom, { configurable: true, writable: true, value: valeur });
	t.after(() => {
		if (precedent) Object.defineProperty(globalThis, nom, precedent);
		else delete globalThis[nom];
	});
}

test('l’adapter réel abonne STOMP avant HTTP avec le même UUID et arrête ses ressources', async (t) => {
	const clients = [];
	const requetes = [];
	const vues = [];
	const etats = [];
	remplacerGlobal(t, 'location', { protocol: 'https:', host: 'exemple.test', href: 'https://exemple.test/test/ecran.html' });
	remplacerGlobal(t, 'StompJs', { Client: class {
		constructor(options) { this.options = options; clients.push(this); }
		activate() { this.active = true; }
		subscribe(destination, recevoir, entetes) { this.abonnement = { destination, recevoir, entetes }; }
		deactivate() { this.active = false; }
	} });
	remplacerGlobal(t, 'fetch', (url, options) => {
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
	// Comme le vrai serveur, la capture annonce son JSON : le transport n'analyse que ce type de corps.
	requetes[0].resoudre({ status: 200, headers: new Headers({ 'Content-Type': 'application/json' }), text: async () => JSON.stringify({ revision: 0, abonnementId: id,
		heureServeur: '2026-11-10T20:00:35Z', vue: { buzz: [] },
	}) });
	await setImmediate();
	assert.deepEqual(vues, [
		{ vue: { buzz: [] }, source: 'capture', type: 'capture' },
		{ vue: { buzz: ['Alpha'] }, source: 'evenement', type: 'buzz-recu' },
	]);
	assert.equal(etats.at(-1), 'synchronisee');
	client.abonnement.recevoir({ body: 'JSON invalide' });
	assert.equal(etats.at(-1), 'coupure');
	assert.equal(client.active, false);
	assert.equal(requetes[0].options.signal.aborted, true);
});

test('la coquille annonce le refus du CONNECT sans annoncer de reconnexion', async (t) => {
	const soiree = '00000000-0000-0000-0000-000000000001';
	const annonces = [];
	const erreur = { hidden: true };
	const reprise = new EventTarget();
	const elements = {
		'[data-connexion]': { set textContent(message) { annonces.push(message); } },
		'[data-erreur]': erreur,
		'[data-reprise]': reprise,
		'[data-principale]': { replaceChildren() {} },
	};
	const fenetre = new EventTarget();
	const connexions = [];
	remplacerGlobal(t, 'document', {
		body: { dataset: { ecran: 'ecran' } },
		querySelector: selecteur => elements[selecteur] ?? null,
	});
	remplacerGlobal(t, 'location', new URL(`https://exemple.test/ecran.html?soiree=${soiree}`));
	remplacerGlobal(t, 'sessionStorage', { getItem: cle => cle === 'croustyquizz.jeton' ? `demo:ECRAN:${soiree}:${soiree}` : null });
	remplacerGlobal(t, 'addEventListener', fenetre.addEventListener.bind(fenetre));
	remplacerGlobal(t, 'StompJs', { Client: class {
		constructor(options) { connexions.push(options); }
		activate() {}
		deactivate() {}
	} });
	t.after(() => fenetre.dispatchEvent(new Event('pagehide')));

	await import('../../src/main/resources/static/js/page.mjs');
	const motif = 'Cette Soirée n’accepte pas d’Écran de démonstration.';
	connexions[0].onStompError({ headers: { message: motif } });

	assert.deepEqual(annonces, ['Synchronisation…', 'Connexion refusée.']);
	assert.deepEqual(erreur, { hidden: false, textContent: motif });
	reprise.dispatchEvent(new Event('click'));
	connexions[1].onWebSocketClose();
	assert.deepEqual(annonces.slice(-2), [
		'Synchronisation…', 'Connexion perdue. Reconnexion en cours… La vue affichée peut avoir changé.',
	]);
});
