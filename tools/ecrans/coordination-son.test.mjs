import test from 'node:test';
import assert from 'node:assert/strict';
import { creerCoordinationSon } from '../../src/main/resources/static/js/coordination-son.mjs';

test('la fenêtre activée la plus récente prend la main après le silence de l’ancienne', { timeout: 3000 }, async t => {
	const audibles = new Set();
	function fenetre(ouverteA, id) {
		let debut = Promise.withResolvers();
		const coordination = creerCoordinationSon({ verrous: navigator.locks,
			canal: new BroadcastChannel('croustyquizz-test-son'), ouverteA, id, soireeId: 'soiree',
			surAttente(autorisee) {
				if (autorisee) audibles.add(id);
				else audibles.delete(id);
				assert.ok(audibles.size <= 1);
				if (autorisee) debut.resolve();
			},
		});
		t.after(() => coordination.detruire());
		return { ...coordination, debut: () => debut.promise, attendreReprise() {
			debut = Promise.withResolvers();
			return debut.promise;
		} };
	}
	const ancienne = fenetre(100, 'ancienne');
	ancienne.activer(true);
	await ancienne.debut();
	const nouvelle = fenetre(200, 'nouvelle');
	nouvelle.activer(true);
	await nouvelle.debut();
	assert.deepEqual([...audibles], ['nouvelle']);
	const reprise = ancienne.attendreReprise();
	await nouvelle.detruire();
	await reprise;
	const rechargee = fenetre(300, 'rechargée');
	rechargee.activer(true);
	await rechargee.debut();
	assert.deepEqual([...audibles], ['rechargée']);
	const apresManche = rechargee.attendreReprise();
	ancienne.signalerSonDeManche(true);
	assert.equal(await ancienne.attendreSonDeManche(), true);
	assert.deepEqual([...audibles], [], 'la Manche peut jouer une fois l’autre fenêtre silencieuse');
	ancienne.signalerSonDeManche(false);
	await apresManche;
	assert.deepEqual([...audibles], ['rechargée']);
});
