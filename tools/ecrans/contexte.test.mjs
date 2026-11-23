import test from 'node:test';
import assert from 'node:assert/strict';
import { creerContexte } from '../../src/main/resources/static/js/contexte.mjs';

test('les abonnés reçoivent la connexion courante puis se désabonnent et demandent une recapture', () => {
	const etats = []; let captures = 0;
	const { contexte, changerEtat } = creerContexte({ acteur: { type: 'JOUEUR' }, soireeId: 's1',
		envoyer() {}, resynchroniser: () => captures++ });
	const quitter = contexte.surEtatConnexion(etat => etats.push(etat));
	changerEtat('synchronisee'); quitter(); changerEtat('coupure');
	assert.deepEqual(etats, ['synchronisation', 'synchronisee']);
	contexte.resynchroniser(); assert.equal(captures, 1);
	assert.equal(contexte.soireeId, 's1');
});

test('une commande attend le serveur, restitue le Probleme et ne remplace jamais la vue', async () => {
	const envois = []; let repondre;
	const { contexte } = creerContexte({ envoyer: () => new Promise(r => { repondre = r; }),
		surEnvoi: (etat, detail) => envois.push({ etat, detail }) });
	const attente = contexte.envoyer('POST', '/commande', {});
	assert.deepEqual(envois, [{ etat: 'en-cours', detail: 'Envoi en cours…' }]);
	repondre({ statut: 409, corps: { detail: 'La Manche est terminée.' } });
	assert.equal((await attente).statut, 409);
	assert.deepEqual(envois.at(-1), { etat: 'refuse', detail: 'La Manche est terminée.' });
	const accepte = contexte.envoyer('POST', '/commande', {});
	assert.equal(envois.at(-1).etat, 'en-cours');
	repondre({ statut: 204, corps: null }); await accepte;
	assert.equal(envois.at(-1).etat, 'accepte');
});

test('un GET ne déclenche aucun état de commande ; une panne réseau donne un état inconnu, sans roue', async () => {
	const etats = [];
	const { contexte } = creerContexte({ envoyer: async methode => {
		if (methode === 'GET') return { statut: 200, corps: {} };
		throw new Error('Hors ligne');
	}, surEnvoi: (etat, detail) => etats.push({ etat, detail }) });
	await contexte.envoyer('GET', '/etat'); assert.deepEqual(etats, []);
	await assert.rejects(contexte.envoyer('POST', '/commande'), /Hors ligne/);
	assert.deepEqual(etats.at(-1), { etat: 'inconnu', detail: "Confirmation indisponible : vérifie l'état affiché." });
});

test('AbortError efface le bandeau et une réponse ancienne ne remplace pas le dernier envoi', async () => {
	const etats = [], reponses = [];
	const { contexte } = creerContexte({ envoyer: () => new Promise((resolve, reject) => reponses.push({ resolve, reject })), surEnvoi: (...e) => etats.push(e) });
	const ancien = contexte.envoyer('POST', '/a', {});
	const courant = contexte.envoyer('POST', '/b', {});
	reponses[1].resolve({ statut: 200, corps: {} }); await courant;
	reponses[0].resolve({ statut: 409, corps: { detail: 'Ancien refus' } }); await ancien;
	assert.equal(etats.at(-1)[0], 'accepte');
	const annule = contexte.envoyer('POST', '/c', {});
	assert.deepEqual(etats.at(-1), ['en-cours', 'Envoi en cours…']);
	reponses[2].reject(new DOMException('Annulation', 'AbortError'));
	await assert.rejects(annule, { name: 'AbortError' });
	assert.deepEqual(etats.at(-1), [null], 'un envoi annulé ne laisse pas « Envoi en cours… »');
});

test('un envoi annoncer: false n’écrit rien dans le bandeau et ne rend pas périmé l’envoi annoncé en vol', async () => {
	const etats = [], appels = [];
	const { contexte } = creerContexte({ envoyer: (...args) => new Promise((resolve, reject) => appels.push({ args, resolve, reject })),
		surEnvoi: (...e) => etats.push(e) });
	const annonce = contexte.envoyer('POST', '/a', {});
	const muet = contexte.envoyer('POST', '/b', {}, { entetes: { 'Idempotency-Key': 'k' }, annoncer: false });
	assert.deepEqual(etats, [['en-cours', 'Envoi en cours…']]);
	assert.deepEqual(appels[1].args[3], { entetes: { 'Idempotency-Key': 'k' } }, 'annoncer ne part pas vers le transport');
	appels[1].resolve({ statut: 409, corps: { detail: 'Refus du module' } }); await muet;
	appels[0].resolve({ statut: 204, corps: null }); await annonce;
	assert.deepEqual(etats, [['en-cours', 'Envoi en cours…'], ['accepte', 'Envoi accepté par le serveur.']]);
	const panne = contexte.envoyer('POST', '/c', {}, { annoncer: false });
	appels[2].reject(new Error('Hors ligne')); await assert.rejects(panne, /Hors ligne/);
	const annule = contexte.envoyer('POST', '/d', {}, { annoncer: false });
	appels[3].reject(new DOMException('Annulation', 'AbortError')); await assert.rejects(annule, { name: 'AbortError' });
	assert.equal(etats.length, 2);
});

test('un 503 ou un 5xx sans code du contrat donne un état inconnu, jamais un refus inventé', async () => {
	const etats = []; let reponse;
	const { contexte } = creerContexte({ envoyer: async () => reponse, surEnvoi: (etat, detail) => etats.push({ etat, detail }) });
	const inconnu = { etat: 'inconnu', detail: "Confirmation indisponible : vérifie l'état affiché." };
	for (const r of [
		{ statut: 502, corps: { detail: 'Le serveur ne répond pas correctement.' } },
		{ statut: 504, corps: null },
		{ statut: 503, corps: { code: 'SERVICE_INDISPONIBLE', detail: 'Réessaie dans un instant.' } },
	]) {
		reponse = r;
		assert.equal((await contexte.envoyer('POST', '/commande', {})).statut, r.statut);
		assert.deepEqual(etats.at(-1), inconnu, `HTTP ${r.statut}`);
	}
	reponse = { statut: 409, corps: { code: 'ETAPE_INACTIVE', detail: 'La Manche est terminée.' } };
	await contexte.envoyer('POST', '/commande', {});
	assert.deepEqual(etats.at(-1), { etat: 'refuse', detail: 'La Manche est terminée.' }, 'un 4xx reste un refus');
});

test('le contexte expose l’heure serveur recalée sans figer sa valeur', () => {
	let maintenant = 1000;
	const { contexte } = creerContexte({ maintenantServeur: () => maintenant });
	assert.equal(contexte.maintenantServeur(), 1000);
	maintenant = 2600;
	assert.equal(contexte.maintenantServeur(), 2600);
});
