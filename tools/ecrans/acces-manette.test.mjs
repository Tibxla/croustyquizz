import test from 'node:test';
import assert from 'node:assert/strict';
import { adresseRetourArrivee, controlerJeton, creerAccesManette, reprendreAcces } from '../../src/main/resources/static/js/acces-manette.mjs';

const soiree = '00000001-0000-4000-8000-000000000001';
const autre = '00000001-0000-4000-8000-000000000002';
const joueur = '65c5618c-10e0-457e-8f8e-eb7295ba6c74';
const jeton = 'a'.repeat(43);

// Même interface que localStorage : getItem, setItem, removeItem, length et key.
function stockageFactice() {
	const valeurs = new Map();
	return {
		getItem: (cle) => valeurs.get(cle) ?? null,
		setItem: (cle, valeur) => valeurs.set(cle, String(valeur)),
		removeItem: (cle) => valeurs.delete(cle),
		get length() { return valeurs.size; },
		key: (i) => [...valeurs.keys()][i] ?? null,
		valeurs,
	};
}

test('l’accès d’une Soirée se range, se relit par Soirée, comme dernière Soirée ou par code, puis s’oublie', () => {
	const acces = creerAccesManette(stockageFactice());
	acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	acces.ranger(autre, { jeton: 'b'.repeat(43), joueurId: joueur, codeAcces: 'ZZZZZZ' });

	assert.deepEqual(acces.lire(soiree), { soireeId: soiree, jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	assert.equal(acces.derniere().soireeId, autre);
	assert.deepEqual(acces.parCode('K7PQ2M').map((a) => a.soireeId), [soiree]);
	assert.deepEqual(acces.parCode('AAAAAA'), []);

	acces.oublier(autre);
	assert.equal(acces.lire(autre), null);
	assert.equal(acces.derniere(), null);
	assert.equal(acces.lire(soiree).jeton, jeton);
});

test('un accès incomplet ou abîmé est ignoré, et un stockage refusé ne lève jamais', () => {
	const stockage = stockageFactice();
	const acces = creerAccesManette(stockage);
	stockage.setItem(`croustyquizz.manette.${soiree}`, '{pas du JSON');
	stockage.setItem(`croustyquizz.manette.${autre}`, JSON.stringify({ jeton: 'court', joueurId: joueur }));
	assert.equal(acces.lire(soiree), null);
	assert.equal(acces.lire(autre), null);
	assert.equal(acces.lire('pas-un-uuid'), null);

	const refuse = { getItem() { throw new Error('refusé'); }, setItem() { throw new Error('refusé'); },
		removeItem() { throw new Error('refusé'); }, get length() { throw new Error('refusé'); }, key() { return null; } };
	const sansStockage = creerAccesManette(refuse);
	assert.doesNotThrow(() => sansStockage.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' }));
	assert.equal(sansStockage.lire(soiree), null);
	assert.deepEqual(sansStockage.parCode('K7PQ2M'), []);
	assert.doesNotThrow(() => sansStockage.oublier(soiree));
	assert.equal(creerAccesManette(undefined).derniere(), null);
});

function vue(etat = 'EN_COURS') {
	return { vue: { soiree: { etat } } };
}

test('un QR code rescanné reprend le Joueur de sa Soirée, pas celui d’une autre', async () => {
	const acces = creerAccesManette(stockageFactice());
	acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	acces.ranger(autre, { jeton: 'b'.repeat(43), joueurId: joueur, codeAcces: 'ZZZZZZ' });
	const consultees = [];
	const consulter = async (id, secret) => { consultees.push([id, secret]); return { statut: 200, corps: vue() }; };

	assert.deepEqual(await reprendreAcces(acces, { codeAcces: 'K7PQ2M', consulter }), { resultat: 'reprise', soireeId: soiree });
	assert.deepEqual(consultees, [[soiree, jeton]]);
	assert.deepEqual(await reprendreAcces(acces, { codeAcces: 'AAAAAA', consulter }), { resultat: 'aucune' });
	assert.deepEqual(await reprendreAcces(acces, { consulter }), { resultat: 'reprise', soireeId: autre });
});

test('un jeton refusé ou une Soirée terminée est oublié ; une coupure garde le jeton', async () => {
	const acces = creerAccesManette(stockageFactice());
	acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });

	for (const statut of [0, 503]) {
		assert.deepEqual(await reprendreAcces(acces, { consulter: async () => ({ statut, corps: null }) }), { resultat: 'coupure' });
		assert.equal(acces.lire(soiree).jeton, jeton);
	}
	assert.deepEqual(await reprendreAcces(acces, { consulter: async () => ({ statut: 401, corps: null }) }), { resultat: 'aucune' });
	assert.equal(acces.lire(soiree), null);

	acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	assert.deepEqual(await reprendreAcces(acces, { codeAcces: 'K7PQ2M', consulter: async () => ({ statut: 200, corps: vue('TERMINEE') }) }),
		{ resultat: 'aucune' });
	assert.equal(acces.lire(soiree), null);
});

test('un code réutilisé après la fin d’une Soirée passe à l’accès suivant', async () => {
	const acces = creerAccesManette(stockageFactice());
	acces.ranger(autre, { jeton: 'b'.repeat(43), joueurId: joueur, codeAcces: 'K7PQ2M' });
	acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	const consulter = async (id) => ({ statut: 200, corps: vue(id === autre ? 'TERMINEE' : 'OUVERTE') });

	assert.deepEqual(await reprendreAcces(acces, { codeAcces: 'K7PQ2M', consulter }), { resultat: 'reprise', soireeId: soiree });
	assert.equal(acces.lire(autre), null);
});

test('au démarrage, seul un jeton refusé renvoie à l’arrivée ; une panne laisse le suivi reprendre', async () => {
	const routes = [];
	const envoyer = (statut) => async (methode, chemin) => { routes.push([methode, chemin]); return { statut, corps: null }; };
	assert.equal(await controlerJeton(envoyer(200), soiree), 'valide');
	assert.deepEqual(routes[0], ['GET', `/api/soirees/${soiree}/etat/manette`]);
	assert.equal(await controlerJeton(envoyer(401), soiree), 'refuse');
	assert.equal(await controlerJeton(envoyer(404), soiree), 'refuse');
	assert.equal(await controlerJeton(envoyer(503), soiree), 'indetermine');
	assert.equal(await controlerJeton(async () => { throw new TypeError('Failed to fetch'); }, soiree), 'indetermine');

	assert.equal(adresseRetourArrivee('K7PQ2M'), '/rejoindre.html#code=K7PQ2M&acces=refuse');
	assert.equal(adresseRetourArrivee(null), '/rejoindre.html#acces=refuse');
});
