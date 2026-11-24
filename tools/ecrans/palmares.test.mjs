import test from 'node:test';
import assert from 'node:assert/strict';
import { creerDocument, terminerMicrotaches } from './karaoke-dom-factice.mjs';

test('la salle distingue le rang, les Interprètes et la note, et laisse lire la ligne complète une seule fois', async t => {
	const document = creerDocument();
	document.body.dataset = { ecran: 'ecran' };
	const zone = document.createElement('section');
	zone.setAttribute('data-zone-palmares', '');
	const liste = document.createElement('ul');
	liste.setAttribute('data-palmares', '');
	const vide = document.createElement('p');
	vide.setAttribute('data-palmares-vide', '');
	const connexion = document.createElement('p');
	connexion.setAttribute('data-connexion', '');
	const erreur = document.createElement('p');
	erreur.setAttribute('data-erreur', '');
	const principale = document.createElement('div');
	principale.setAttribute('data-principale', '');
	zone.append(liste, vide);
	document.body.append(zone, connexion, erreur, principale);

	const soireeId = '00000000-0000-0000-0000-000000000001';
	const acteurId = '00000000-0000-0000-0000-000000000002';
	const titre = 'La Chanson au titre très long que les Interprètes ont choisie pour cette Soirée de démonstration';
	const vue = { soiree: {}, joueurs: [], classement: [], modeEnCours: null, palmaresKaraoke: [
		{ numero: 10, rang: 1, chanson: { titre }, interpretes: [{ pseudo: 'Alizé' }, { pseudo: 'Basile' }],
			nombreVotes: 7, moyenneDixiemes: 43 },
	] };
	const navigateur = {
		document,
		location: new URL(`https://exemple.test/ecran.html?soiree=${soireeId}`),
		sessionStorage: { getItem: () => `demo:ECRAN:${soireeId}:${acteurId}` },
		addEventListener: document.addEventListener,
		StompJs: { Client: class {
			constructor({ onConnect }) { this.connecter = onConnect; }
			activate() { queueMicrotask(this.connecter); }
			subscribe() {}
			deactivate() {}
		} },
		fetch: async url => ({ status: 200, headers: new Headers({ 'Content-Type': 'application/json' }),
			text: async () => JSON.stringify({ vue, abonnementId: url.searchParams.get('abonnementId'),
				revision: 1, heureServeur: '2026-11-24T20:00:00Z' }) }),
	};
	for (const [nom, valeur] of Object.entries(navigateur)) {
		const anciennePropriete = Object.getOwnPropertyDescriptor(globalThis, nom);
		Object.defineProperty(globalThis, nom, { value: valeur, configurable: true });
		t.after(() => {
			if (anciennePropriete) Object.defineProperty(globalThis, nom, anciennePropriete);
			else Reflect.deleteProperty(globalThis, nom);
		});
	}
	t.after(() => document.dispatchEvent({ type: 'pagehide' }));

	await import('../../src/main/resources/static/js/page.mjs');
	await terminerMicrotaches();
	assert.equal(connexion.textContent, 'Vue synchronisée');
	const ligne = liste.querySelector('li');
	assert.equal(ligne?.dataset?.teinte, '2');
	const presentation = ligne.querySelector('[aria-hidden="true"]');
	assert.equal(presentation?.querySelector('.palmares__rang')?.textContent, '1.');
	assert.equal(presentation?.querySelector('.palmares__numero')?.textContent, 'n° 10');
	assert.equal(presentation?.querySelector('.palmares__interpretes')?.textContent, 'Alizé, Basile');
	assert.equal(presentation?.querySelector('.palmares__chanson')?.textContent, titre);
	assert.equal(presentation?.querySelector('.palmares__note')?.textContent, '4,3 sur 5');
	assert.equal(presentation?.querySelector('.palmares__votes')?.textContent, '7 votes');
	assert.equal(ligne.querySelector('.visuellement-cache')?.textContent.includes(titre), true);
	assert.deepEqual(ligne.children.filter(enfant => enfant.getAttribute('aria-hidden') !== 'true')
		.map(enfant => enfant.textContent), [`1. n° 10 · ${titre} · Alizé, Basile · 4,3 sur 5 (7 votes)`]);
});
