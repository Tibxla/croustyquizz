import test from 'node:test';
import assert from 'node:assert/strict';
import { lireAcces, preparerIdentite } from '../../src/main/resources/static/js/identite.mjs';

const soiree = '00000001-0000-4000-8000-000000000000';
const joueur = '65c5618c-10e0-357e-8f8e-eb7295ba6c74';
test('les trois pages utilisent la portée du jeton de démonstration, sans accepter un autre acteur ou une autre Soirée', () => {
	const acces = lireAcces('manette', `?soiree=${soiree}`, `demo:JOUEUR:${soiree}:${joueur}`);
	assert.equal(acces.destination, `/topic/joueurs/${joueur}`);
	assert.equal(acces.routeEtat, `/api/soirees/${soiree}/etat/manette`);
	assert.deepEqual(acces.acteur, { type: 'JOUEUR', identifiant: joueur, soireeId: soiree });
	assert.throws(() => lireAcces('console', `?soiree=${soiree}`, `demo:JOUEUR:${soiree}:${joueur}`), /identité/);
	assert.throws(() => lireAcces('manette', `?soiree=${joueur}`, `demo:JOUEUR:${soiree}:${joueur}`), /Soirée/);
	assert.throws(() => lireAcces('manette', '', ''), /Soirée/);
	assert.equal(lireAcces('console', `?soiree=${soiree}`, null).destination, `/topic/soirees/${soiree}/console`);
});

test('le Joueur de démonstration prend son UUID dans la projection du serveur', async () => {
	let autorisation;
	const acces = await preparerIdentite('JOUEUR', soiree, joueur, async (url, options) => {
		autorisation = options.headers.Authorization;
		assert.equal(url, `/api/soirees/${soiree}/etat/salle`);
		return { ok: true, json: async () => ({ vue: { joueurs: [{ id: joueur }] } }) };
	});
	assert.equal(autorisation, `Bearer demo:ECRAN:${soiree}:${joueur}`);
	assert.equal(acces.jeton, `demo:JOUEUR:${soiree}:${joueur}`);
	assert.equal(acces.page, 'manette');
});
