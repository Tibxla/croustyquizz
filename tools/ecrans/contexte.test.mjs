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

test('un GET ne déclenche aucun état de commande ; une panne réseau reste une absence de confirmation', async () => {
	const etats = [];
	const { contexte } = creerContexte({ envoyer: async methode => {
		if (methode === 'GET') return { statut: 200, corps: {} };
		throw new Error('Hors ligne');
	}, surEnvoi: etat => etats.push(etat) });
	await contexte.envoyer('GET', '/etat'); assert.deepEqual(etats, []);
	await assert.rejects(contexte.envoyer('POST', '/commande'), /Hors ligne/);
	assert.deepEqual(etats, ['en-cours', 'refuse']);
});
