import test from 'node:test';
import assert from 'node:assert/strict';
import { creerMachineEnvoi, peutReessayer } from '../../src/main/resources/static/js/modes/karaoke/envois.mjs';
import { creerUuid } from '../../src/main/resources/static/js/modes/karaoke/commun.mjs';

const commande = { methode: 'POST', chemin: '/passages', corps: { chansonId: 'c' }, mancheId: 'm', acteurType: 'JOUEUR', chansonId: 'c' };
const vider = async () => { for (let i = 0; i < 5; i++) await Promise.resolve(); };
function banc() {
	const appels = [], etats = [], delais = new Map(); let uuid = 0, delai = 0, captures = 0;
	const machine = creerMachineEnvoi({ envoyer: (...args) => new Promise((resolve, reject) => appels.push({ args, resolve, reject })),
		creerUuid: () => `cle${++uuid}`, programmerDelai: f => { delais.set(++delai, f); return delai; }, annulerDelai: d => delais.delete(d),
		delaiConfirmationMs: 10, resynchroniser: () => captures++, surEtat: e => etats.push(e) });
	return { machine, appels, etats, delais, captures: () => captures };
}

test('UUID natif ou repli cryptographique version 4 et variante RFC', () => {
	assert.equal(creerUuid({ randomUUID: () => 'natif' }), 'natif');
	assert.equal(creerUuid({ getRandomValues: octets => octets.fill(255) }), 'ffffffff-ffff-4fff-bfff-ffffffffffff');
});

test('le double appui est unique, le réessai reprend exactement le corps figé et la clé', async () => {
	const b = banc(), corps = { numeroModification: 3, avant: null, tour: 1 };
	const cmd = { ...commande, acteurType: 'ANIMATEUR', methode: 'PUT', corps };
	const { cle } = b.machine.envoyerCommande(cmd);
	assert.deepEqual(b.machine.envoyerCommande({ ...cmd, corps: { tour: 1, avant: null, numeroModification: 3 } }), { cle });
	assert.equal(b.appels.length, 1); corps.numeroModification = 9;
	assert.equal(b.appels[0].args[2].envoiId, undefined);
	assert.equal(b.appels[0].args[3].entetes['Idempotency-Key'], cle);
	b.appels[0].reject(new Error()); await vider();
	assert.equal(b.etats.at(-1).etat, 'inconnu'); assert.equal(b.captures(), 1);
	assert.deepEqual(b.machine.reessayer(cle), { cle }); b.machine.reessayer(cle);
	assert.equal(b.appels.length, 2); assert.deepEqual(b.appels[1].args.slice(0, 3), b.appels[0].args.slice(0, 3));
	assert.equal(b.appels[1].args[2].numeroModification, 3);
	b.appels[1].resolve({ statut: 200, corps: { action: 'DEPLACER' } }); await vider();
	assert.equal(b.etats.at(-1).etat, 'accepte'); assert.equal(b.machine.reessayer(cle), null);
	assert.notEqual(b.machine.envoyerCommande(cmd).cle, cle); b.machine.detruire();
});

test('délai sans abort, réponse définitive tardive et capture regroupée par microtâche', async () => {
	const b = banc();
	const { cle } = b.machine.envoyerCommande(commande);
	b.machine.envoyerCommande({ ...commande, corps: { chansonId: 'd' }, chansonId: 'd' });
	for (const rappel of [...b.delais.values()]) rappel();
	await vider(); assert.equal(b.captures(), 1); assert.equal(b.appels[0].args[3].signal.aborted, false);
	assert.equal(b.machine.retrouverInscription('m', 'c'), cle);
	b.machine.reessayer(cle);
	b.appels[0].resolve({ statut: 201, corps: { passageId: 'p' } }); await vider();
	b.appels[2].resolve({ statut: 503, corps: { detail: 'Panne' } }); await vider();
	assert.equal(b.etats.at(-1).etat, 'accepte'); b.machine.detruire();
});

test('503 confirmé par vue, oubli accepté absent et inconnue conservée', async () => {
	const b = banc(); const { cle } = b.machine.envoyerCommande(commande);
	b.appels[0].resolve({ statut: 503, corps: { code: 'SERVICE_INDISPONIBLE' } }); await vider();
	assert.equal(b.etats.at(-1).etat, 'indisponible');
	assert.deepEqual(b.machine.reconcilier([]), { acceptees: [], oubliees: [] });
	assert.deepEqual(b.machine.reconcilier([cle]), { acceptees: [cle], oubliees: [] });
	assert.deepEqual(b.etats.at(-1), { cle, etat: 'accepte' });
	assert.deepEqual(b.machine.reconcilier([]), { acceptees: [], oubliees: [cle] });
	assert.equal(b.machine.retrouverInscription('m', 'c'), null);
});

test('une vue confirme une inscription en cours sans fabriquer de reçu ni proposer de réessai', () => {
	const b = banc(); const { cle } = b.machine.envoyerCommande(commande);
	assert.deepEqual(b.machine.reconcilier([cle]), { acceptees: [cle], oubliees: [] });
	assert.deepEqual(b.etats.at(-1), { cle, etat: 'accepte' });
	assert.equal(b.machine.reessayer(cle), null); b.machine.detruire();
});

test('un ancien envoi expiré ne bloque pas un nouveau réessai après échec du précédent', async () => {
	const b = banc(); const { cle } = b.machine.envoyerCommande(commande);
	[...b.delais.values()][0](); await vider();
	b.machine.reessayer(cle); b.appels[1].reject(new Error()); await vider();
	b.machine.reessayer(cle); assert.equal(b.appels.length, 3);
	b.machine.detruire();
});

test('les refus transitoires sont réessayables, les refus définitifs libèrent l’inscription', async () => {
	for (const [statut, code, capture, reessai] of [[409, 'ETAPE_INACTIVE', true, true], [401, 'AUTHENTIFICATION_REQUISE', true, true], [403, 'ACCES_REFUSE', true, true], [409, 'COMMANDE_PERIMEE', true, false], [409, 'ETAT_INCOMPATIBLE', true, false], [403, 'CSRF_INVALIDE', false, false], [422, 'DONNEES_INVALIDES', false, false], [400, 'REQUETE_INVALIDE', false, false]]) {
		const b = banc(); const { cle } = b.machine.envoyerCommande(commande);
		b.appels[0].resolve({ statut, corps: { code, detail: 'Refus' } }); await vider();
		assert.equal(b.captures(), Number(capture)); assert.equal(b.etats.at(-1).detail, 'Refus');
		assert.equal(b.machine.retrouverInscription('m', 'c'), reessai ? cle : null);
		assert.equal(Boolean(b.machine.reessayer(cle)), reessai); b.machine.detruire();
	}
});

test('une désinscription ne se réconcilie pas ; oubli et démontage ignorent les réponses tardives', async () => {
	const b = banc(); const { cle } = b.machine.envoyerCommande({ ...commande, chansonId: undefined, passageId: 'p' });
	assert.deepEqual(b.machine.reconcilier([cle]), { acceptees: [], oubliees: [] });
	b.machine.oublier(cle); assert.equal(b.appels[0].args[3].signal.aborted, true);
	b.appels[0].resolve({ statut: 200, corps: {} }); await vider(); assert.equal(b.etats.length, 1);
	b.machine.envoyerCommande(commande); [...b.delais.values()][0](); b.machine.changerManche('autre'); await vider();
	assert.equal(b.captures(), 0); assert.equal(b.machine.retrouverInscription('m', 'c'), null);
	b.machine.detruire(); assert.equal(b.machine.envoyerCommande(commande), null);
});

test('une erreur 5xx sans code du contrat (page d’erreur d’un relais) reste réessayable, une 409 non', async () => {
	const b = banc();
	b.machine.envoyerCommande(commande);
	b.appels[0].resolve({ statut: 502, corps: { detail: 'Le serveur ne répond pas correctement.' } }); await vider();
	assert.equal(b.etats.at(-1).etat, 'indisponible');
	assert.equal(peutReessayer(b.etats.at(-1)), true);
	const autre = banc();
	autre.machine.envoyerCommande(commande);
	autre.appels[0].resolve({ statut: 409, corps: { code: 'PASSAGE_DEJA_INSCRIT', detail: 'Déjà inscrit.' } }); await vider();
	assert.equal(autre.etats.at(-1).etat, 'refuse');
	b.machine.detruire(); autre.machine.detruire();
});

test('une inscription acceptée par 2xx n’est oubliée qu’après avoir été vue dans la vue', async () => {
	const b = banc(); const { cle } = b.machine.envoyerCommande(commande);
	b.appels[0].resolve({ statut: 201, corps: { passageId: 'p' } }); await vider();
	assert.equal(b.etats.at(-1).etat, 'accepte');
	// Une vue plus ancienne, sans le Passage, arrive après la réponse : l'inscription reste connue.
	assert.deepEqual(b.machine.reconcilier([]), { acceptees: [], oubliees: [] });
	assert.equal(b.machine.retrouverInscription('m', 'c'), cle);
	assert.deepEqual(b.machine.reconcilier([cle]), { acceptees: [], oubliees: [] });
	assert.deepEqual(b.machine.reconcilier([]), { acceptees: [], oubliees: [cle] });
	b.machine.detruire();
});
