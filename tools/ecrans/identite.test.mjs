import test from 'node:test';
import assert from 'node:assert/strict';
import { choisirPilotage, lireAcces, preparerIdentite } from '../../src/main/resources/static/js/identite.mjs';

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

test('un vrai jeton de Joueur, opaque, s’accompagne de l’UUID rangé par la page d’arrivée', () => {
	const jeton = 'a'.repeat(43);
	const acces = lireAcces('manette', `?soiree=${soiree}`, jeton, joueur);
	assert.equal(acces.destination, `/topic/joueurs/${joueur}`);
	assert.equal(acces.routeEtat, `/api/soirees/${soiree}/etat/manette`);
	assert.deepEqual(acces.acteur, { type: 'JOUEUR', identifiant: joueur, soireeId: soiree });
	assert.throws(() => lireAcces('manette', `?soiree=${soiree}`, jeton, null), /Rejoins/);
	assert.throws(() => lireAcces('manette', `?soiree=${soiree}`, 'trop-court', joueur), /identité/);
	const ecran = lireAcces('ecran', `?soiree=${soiree}`, 'b'.repeat(43));
	assert.equal(ecran.destination, `/topic/soirees/${soiree}/salle`);
	assert.deepEqual(ecran.acteur, { type: 'ECRAN', soireeId: soiree });
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

test('l’Écran de salle ouvert depuis la préparation utilise la clé du fragment', () => {
	const cle = 'c'.repeat(43);
	const recherche = `?soiree=${soiree}`;
	const acces = lireAcces('ecran', recherche, `demo:ECRAN:${soiree}:${joueur}`, undefined, `#cle=${cle}`);
	assert.equal(acces.cleEcran, cle);
	assert.equal(acces.destination, `/topic/soirees/${soiree}/salle`);
	assert.deepEqual(acces.acteur, { type: 'ECRAN', soireeId: soiree });
	assert.throws(() => lireAcces('ecran', recherche, null, undefined, '#cle=trop-court'), /clé/);
});

test('le rang 3 ouvre la Manette du troisième Joueur de la projection', async () => {
	const troisieme = '10000000-0000-4000-8000-000000000003';
	const joueurs = [{ id: joueur }, { id: '10000000-0000-4000-8000-000000000002' }, { id: troisieme }];
	const acces = await preparerIdentite('JOUEUR', soiree, joueur, async () => Response.json({ vue: { joueurs } }), 3);
	assert.deepEqual(acces, { jeton: `demo:JOUEUR:${soiree}:${troisieme}`, page: 'manette' });
	assert.equal(lireAcces(acces.page, `?soiree=${soiree}`, acces.jeton).destination, `/topic/joueurs/${troisieme}`);
});

test('un rang invalide est refusé avant toute requête', async () => {
	const requete = () => assert.fail('La requête ne doit pas partir.');
	for (const rang of [0, 5, 1.5, NaN]) {
		await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, requete, rang),
			{ message: 'Ce Joueur de démonstration n’existe pas dans cette Soirée.' });
	}
});

test('un Joueur absent de la projection est refusé', async () => {
	const requete = async () => Response.json({ vue: { joueurs: [{ id: joueur }] } });
	await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, requete, 3),
		{ message: 'Ce Joueur de démonstration n’existe pas dans cette Soirée.' });
});

test('une entrée présente sans UUID valide garde le message historique', async () => {
	for (const entree of [{}, { id: 'invalide' }]) {
		const requete = async () => Response.json({ vue: { joueurs: [{ id: joueur }, entree] } });
		await assert.rejects(preparerIdentite('JOUEUR', soiree, joueur, requete, 2),
			{ message: 'Le serveur n’a pas fourni de Joueur de démonstration.' });
	}
});

test('l’Animateur vérifie la Soirée par la capture de la Console, avec son propre jeton', async () => {
	let appel;
	const acces = await preparerIdentite('ANIMATEUR', soiree, joueur, async (url, options) => {
		appel = { url, autorisation: options.headers.Authorization };
		return Response.json({ vue: {} });
	});
	assert.deepEqual(appel, { url: `/api/soirees/${soiree}/etat/console`, autorisation: `Bearer demo:ANIMATEUR:${soiree}:${joueur}` });
	assert.deepEqual(acces, { jeton: `demo:ANIMATEUR:${soiree}:${joueur}`, page: 'console' });
});

test('l’Animateur et l’Écran de salle ignorent le rang du Joueur', async () => {
	const requete = async () => Response.json({ vue: { joueurs: [] } });
	for (const [role, page] of [['ANIMATEUR', 'console'], ['ECRAN', 'ecran']]) {
		assert.deepEqual(await preparerIdentite(role, soiree, joueur, requete, 5),
			{ jeton: `demo:${role}:${soiree}:${joueur}`, page });
	}
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

test('le choix du Pilotage est envoyé avec le jeton Animateur et le corps prévu', async () => {
	const jeton = `demo:ANIMATEUR:${soiree}:${joueur}`;
	await choisirPilotage(soiree, jeton, 'AUTOMATIQUE', async (url, options) => {
		assert.equal(url, `/api/test/soirees/${soiree}/pilotage`);
		assert.equal(options.method, 'PUT');
		assert.deepEqual(options.headers, {
			Authorization: `Bearer ${jeton}`, 'Content-Type': 'application/json',
		});
		assert.equal(options.body, '{"pilotage":"AUTOMATIQUE"}');
		return Response.json({ soireeId: soiree, pilotage: 'AUTOMATIQUE' });
	});
});

test('le refus du Pilotage reprend le détail du serveur ou le statut HTTP', async () => {
	const detail = 'La Soirée a commencé : son Pilotage ne change plus.';
	await assert.rejects(choisirPilotage(soiree, 'jeton', 'MANUEL', async () => Response.json({ detail }, {
		status: 409, headers: { 'Content-Type': 'application/problem+json' },
	})), { message: detail });
	await assert.rejects(choisirPilotage(soiree, 'jeton', 'MANUEL', async () => new Response(null, { status: 502 })),
		{ message: 'Pilotage refusé (HTTP 502).' });
});
