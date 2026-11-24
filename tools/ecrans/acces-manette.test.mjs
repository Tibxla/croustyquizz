import test from 'node:test';
import assert from 'node:assert/strict';
import { adresseRetourArrivee, controlerJeton, creerAccesManette, reprendreAcces, stockagesDuNavigateur } from '../../src/main/resources/static/js/acces-manette.mjs';

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

// Un stockage qui lit mais refuse toute écriture : quota atteint, ou navigation privée stricte.
function stockagePlein() {
	return { ...stockageFactice(), setItem() { throw new DOMException('plein', 'QuotaExceededError'); } };
}

test('sans stockage durable, l’accès se range dans l’onglet et se relit, se retrouve par code puis s’oublie', () => {
	const durable = stockagePlein();
	const onglet = stockageFactice();
	const acces = creerAccesManette([durable, onglet]);

	assert.equal(acces.disponible(), true);
	assert.equal(acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' }), true);
	assert.equal(acces.lire(soiree).jeton, jeton);
	assert.equal(acces.derniere().soireeId, soiree);
	assert.deepEqual(acces.parCode('K7PQ2M').map((a) => a.soireeId), [soiree]);
	// La coquille de la Manette relit le même accès avec les stockages par défaut du navigateur.
	assert.equal(creerAccesManette([durable, onglet]).lire(soiree).joueurId, joueur);

	acces.oublier(soiree);
	assert.equal(acces.lire(soiree), null);
	assert.equal(onglet.valeurs.size, 0);
});

test('aucun stockage utilisable : l’accès le dit avant l’arrivée, et ranger échoue sans lever', () => {
	const acces = creerAccesManette([stockagePlein(), stockagePlein()]);
	assert.equal(acces.disponible(), false);
	assert.equal(acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' }), false);
	assert.equal(acces.lire(soiree), null);
	assert.equal(creerAccesManette([]).disponible(), false);
});

test('un stockage durable utilisable reste le premier choix ; la sonde ne laisse rien derrière elle', () => {
	const durable = stockageFactice();
	const onglet = stockageFactice();
	const acces = creerAccesManette([durable, onglet]);
	assert.equal(acces.disponible(), true);
	assert.equal(acces.ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' }), true);
	assert.ok(durable.valeurs.has(`croustyquizz.manette.${soiree}`));
	assert.equal(onglet.valeurs.size, 0);
	assert.deepEqual([...durable.valeurs.keys()].sort(), ['croustyquizz.manette-derniere', `croustyquizz.manette.${soiree}`]);
});

test('le navigateur qui refuse l’accès à localStorage (SecurityError) ne fait pas lever la page', () => {
	const onglet = stockageFactice();
	const fenetre = { get localStorage() { throw new DOMException('refusé', 'SecurityError'); }, sessionStorage: onglet };
	const stockages = stockagesDuNavigateur(fenetre);
	assert.deepEqual(stockages, [onglet]);
	assert.equal(creerAccesManette(stockages).ranger(soiree, { jeton, joueurId: joueur, codeAcces: 'K7PQ2M' }), true);
	assert.deepEqual(stockagesDuNavigateur({}), []);
});

function vue(etat = 'EN_COURS', { joueurId = joueur, codeAcces = 'K7PQ2M' } = {}) {
	return { vue: { soiree: { etat, codeAcces }, joueurId } };
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

// L'ancienne page d'arrivée gardait le jeton et la Soirée dans le stockage de l'onglet, sans l'UUID du Joueur.
function ancienOnglet(valeurs = { 'croustyquizz.jeton': jeton, 'croustyquizz.soiree': soiree }) {
	const stockage = stockageFactice();
	for (const [cle, valeur] of Object.entries(valeurs)) stockage.setItem(cle, valeur);
	return stockage;
}

test('une Manette ouverte avant le déploiement reprend son Joueur depuis l’ancien stockage de l’onglet', async () => {
	const acces = creerAccesManette(stockageFactice());
	const ancienStockage = ancienOnglet();
	const consultees = [];
	const consulter = async (id, secret) => { consultees.push([id, secret]); return { statut: 200, corps: vue() }; };

	assert.deepEqual(await reprendreAcces(acces, { consulter, ancienStockage }), { resultat: 'reprise', soireeId: soiree });
	assert.deepEqual(consultees, [[soiree, jeton]]);
	assert.deepEqual(acces.lire(soiree), { soireeId: soiree, jeton, joueurId: joueur, codeAcces: 'K7PQ2M' });
	assert.equal(ancienStockage.valeurs.size, 0);
});

test('un QR code d’une autre Soirée ne reprend pas l’ancien accès, mais le garde dans le nouveau stockage', async () => {
	const acces = creerAccesManette(stockageFactice());
	const consulter = async () => ({ statut: 200, corps: vue() });

	assert.deepEqual(await reprendreAcces(acces, { codeAcces: 'ZZZZZZ', consulter, ancienStockage: ancienOnglet() }), { resultat: 'aucune' });
	assert.equal(acces.lire(soiree).joueurId, joueur);
});

test('un ancien accès refusé ou terminé est effacé ; une coupure le garde ; une identité de démonstration est ignorée', async () => {
	for (const corps of [{ statut: 401, corps: null }, { statut: 404, corps: null }, { statut: 200, corps: vue('TERMINEE') }]) {
		const acces = creerAccesManette(stockageFactice());
		const ancienStockage = ancienOnglet();
		assert.deepEqual(await reprendreAcces(acces, { consulter: async () => corps, ancienStockage }), { resultat: 'aucune' });
		assert.equal(ancienStockage.valeurs.size, 0);
		assert.equal(acces.lire(soiree), null);
	}

	const ancienStockage = ancienOnglet();
	assert.deepEqual(await reprendreAcces(creerAccesManette(stockageFactice()),
		{ consulter: async () => ({ statut: 0, corps: null }), ancienStockage }), { resultat: 'coupure' });
	assert.equal(ancienStockage.valeurs.get('croustyquizz.jeton'), jeton);

	const demo = ancienOnglet({ 'croustyquizz.jeton': `demo:JOUEUR:${soiree}:${joueur}` });
	const consulter = async () => assert.fail('une identité de démonstration ne se consulte pas');
	assert.deepEqual(await reprendreAcces(creerAccesManette(stockageFactice()), { consulter, ancienStockage: demo }), { resultat: 'aucune' });
	assert.equal(demo.valeurs.size, 1);
});

test('un ancien accès que le nouveau stockage refuse n’est pas effacé', async () => {
	const refuse = { ...stockageFactice(), setItem() { throw new DOMException('plein', 'QuotaExceededError'); } };
	const ancienStockage = ancienOnglet();
	await reprendreAcces(creerAccesManette(refuse), { consulter: async () => ({ statut: 200, corps: vue() }), ancienStockage });
	assert.equal(ancienStockage.valeurs.get('croustyquizz.jeton'), jeton);
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
