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
		return { ok: true, status: 200, headers: new Headers({ 'Content-Type': 'application/json' }),
			text: async () => JSON.stringify({ vue: { joueurs: [{ id: joueur }] } }) };
	});
	assert.equal(autorisation, `Bearer demo:ECRAN:${soiree}:${joueur}`);
	assert.equal(acces.jeton, `demo:JOUEUR:${soiree}:${joueur}`);
	assert.equal(acces.page, 'manette');
});

test('une page d’erreur HTML ou un Probleme donnent un refus lisible, jamais une SyntaxError', async () => {
	const html = { ok: false, status: 502, headers: new Headers({ 'Content-Type': 'text/html' }), text: async () => '<html>502 Bad Gateway</html>' };
	await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, async () => html), { message: 'Le serveur ne répond pas correctement.' });
	const portail = { ...html, ok: true, status: 200 };
	await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, async () => portail), { message: 'Le serveur ne répond pas correctement.' });
	const probleme = { ok: false, status: 404, headers: new Headers({ 'Content-Type': 'application/problem+json' }),
		text: async () => '{"code":"RESSOURCE_INTROUVABLE","detail":"Soirée introuvable."}' };
	await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, async () => probleme), { message: 'Soirée introuvable.' });
	const vide = { ok: false, status: 401, headers: new Headers(), text: async () => '' };
	await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, async () => vide), { message: 'Identité refusée (HTTP 401).' });
});
