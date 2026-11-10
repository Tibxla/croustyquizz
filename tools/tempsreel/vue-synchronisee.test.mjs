import test from 'node:test';
import assert from 'node:assert/strict';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable, viderPromesses } from './adaptateur-pilotable.mjs';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
const { parse } = createRequire(new URL('../api/package.json', import.meta.url))('yaml');
import { rejouerReconnexion } from './parcours.mjs';

const exemples = parse(await readFile(new URL('../../docs/api/exemples/parcours-quiz.yaml', import.meta.url), 'utf8'));

test('rejouer la capture retardée, les Révisions déjà couvertes et le doublon du parcours Quiz', async () => {
	await rejouerReconnexion(exemples.resynchronisation);
});

function demarrer(options = {}, adaptations = {}) {
	const pilote = creerAdaptateurPilotable();
	const vues = [];
	const etats = [];
	const suivi = suivreVue({ destination: '/topic/salle', routeEtat: '/etat/salle',
		surVue: (vue, contexte) => vues.push({ vue, ...contexte }),
		surEtat: etat => etats.push(etat), ...options,
	}, { ...pilote.adaptations, ...adaptations });
	pilote.connexions[0].surConnexion();
	return { ...pilote, vues, etats, suivi };
}

async function repondre(pilote, index = 0, revision = 9, vue = { phase: 'capture' }) {
	pilote.requetes[index].resoudre({ statut: 200, corps: {
		abonnementId: pilote.requetes[index].abonnementId, revision,
		heureServeur: '2026-11-10T20:00:35Z', vue,
	} });
	await viderPromesses();
}

test('attendre 250 ms entre cinq tentatives sur abonnement inactif puis reconnecter avec un UUID neuf', async () => {
	const p = demarrer();
	for (let tentative = 0; tentative < 5; tentative++) {
		assert.equal(p.requetes.length, tentative + 1);
		p.requetes[tentative].resoudre({ statut: 409, corps: { code: 'ABONNEMENT_INACTIF' } });
		await viderPromesses();
		if (tentative < 4) {
			await p.avancer(249);
			assert.equal(p.requetes.length, tentative + 1);
			assert.equal(p.connexions[0].fermee, false);
			await p.avancer(1);
		}
	}
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.etats.at(-1), 'reconnexion');
	await p.avancer(2000);
	p.connexions[1].surConnexion();
	assert.notEqual(p.requetes[5].abonnementId, p.requetes[0].abonnementId);
	await repondre(p, 5);
	assert.equal(p.etats.at(-1), 'synchronisee');
	p.suivi.arreter();
});

for (const pendantCapture of [true, false]) {
	test(`resynchroniser sur Révision manquante ${pendantCapture ? 'dans le tampon' : 'après capture'}`, async () => {
		const p = demarrer();
		if (!pendantCapture) await repondre(p);
		p.connexions[0].surMessage({ revision: 11, type: 'question-ouverte', donnees: { phase: 'incomplète' } });
		if (pendantCapture) await repondre(p);
		assert.equal(p.etats.at(-1), 'reconnexion');
		assert.ok(p.vues.every(v => v.vue.phase !== 'incomplète'));
		await p.avancer(2000);
		p.connexions[1].surConnexion();
		await repondre(p, 1, 11, { phase: 'récupérée' });
		assert.equal(p.vues.at(-1).vue.phase, 'récupérée');
		assert.equal(p.etats.at(-1), 'synchronisee');
		p.suivi.arreter();
	});
}

test('conserver 64 messages au maximum puis abandonner la capture et son abonnement', async () => {
	const p = demarrer();
	for (let i = 0; i < 64; i++) p.connexions[0].surMessage(exemples.resynchronisation.tampon[0]);
	assert.equal(p.connexions[0].fermee, false);
	p.connexions[0].surMessage(exemples.resynchronisation.tampon[0]);
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.requetes[0].signal.aborted, true);
	await repondre(p);
	assert.equal(p.vues.length, 0);
	assert.equal(p.etats.at(-1), 'reconnexion');
	p.suivi.arreter();
});

test('ignorer les réponses et messages d’un ancien abonnement après une coupure', async () => {
	const p = demarrer();
	p.connexions[0].surDeconnexion();
	await p.avancer(2000);
	p.connexions[1].surConnexion();
	await repondre(p, 1, 20, { phase: 'nouvelle' });
	await repondre(p, 0, 9, { phase: 'ancienne' });
	p.connexions[0].surMessage({ revision: 21, type: 'ancien', donnees: {} });
	p.connexions[0].surDeconnexion();
	assert.deepEqual(p.vues, [{ vue: { phase: 'nouvelle' }, type: 'capture' }]);
	assert.equal(p.etats.at(-1), 'synchronisee');
	p.suivi.arreter();
});

test('refuser une capture dont l’UUID ne correspond pas à la requête courante', async () => {
	const p = demarrer();
	p.requetes[0].resoudre({ statut: 200, corps: exemples.resynchronisation.etat });
	await viderPromesses();
	assert.equal(p.vues.length, 0);
	assert.equal(p.etats.at(-1), 'reconnexion');
	p.suivi.arreter();
});

test('recaler l’heure à chaque capture avec une horloge monotone', async () => {
	const p = demarrer();
	assert.equal(p.suivi.maintenantServeur(), null);
	await repondre(p);
	await p.avancer(123);
	assert.equal(p.suivi.maintenantServeur(), Date.parse('2026-11-10T20:00:35.123Z'));
	p.connexions[0].surDeconnexion();
	await p.avancer(2000);
	p.connexions[1].surConnexion();
	await repondre(p, 1);
	assert.equal(p.suivi.maintenantServeur(), Date.parse('2026-11-10T20:00:35Z'));
	p.suivi.arreter();
});

test('arrêter annule la capture, les minuteries et tous les rappels tardifs', async () => {
	const p = demarrer();
	p.suivi.arreter();
	p.suivi.arreter();
	await repondre(p);
	p.connexions[0].surDeconnexion();
	await p.avancer(10000);
	assert.equal(p.connexions.length, 1);
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.requetes[0].signal.aborted, true);
	assert.equal(p.vues.length, 0);
	assert.equal(p.minuteries.size, 0);
});

test('arrêter pendant l’attente du 409 empêche toute nouvelle tentative', async () => {
	const p = demarrer();
	p.requetes[0].resoudre({ statut: 409, corps: { code: 'ABONNEMENT_INACTIF' } });
	await viderPromesses();
	p.suivi.arreter();
	await p.avancer(10000);
	assert.equal(p.requetes.length, 1);
	assert.equal(p.connexions.length, 1);
	assert.equal(p.minuteries.size, 0);
});

test('une erreur de rendu de la page impose une nouvelle capture', async () => {
	const p = demarrer({ surVue() { throw new Error('Rendu interrompu'); } });
	await repondre(p);
	assert.equal(p.etats.at(-1), 'reconnexion');
	p.suivi.arreter();
});

for (const revision of [undefined, -1, 1.5, '10', Number.MAX_SAFE_INTEGER + 1]) {
	test(`refuser une Révision invalide dans la capture : ${revision}`, async () => {
		const p = demarrer();
		await repondre(p, 0, revision === undefined ? null : revision);
		assert.equal(p.vues.length, 0);
		assert.equal(p.etats.at(-1), 'reconnexion');
		p.suivi.arreter();
	});
}

test('refuser un message malformé avant la capture et reconnecter sur erreur réseau', async () => {
	const p = demarrer();
	p.connexions[0].surMessage({ revision: '10', donnees: {} });
	assert.equal(p.etats.at(-1), 'reconnexion');
	await p.avancer(2000);
	p.connexions[1].surConnexion();
	p.requetes[1].rejeter(new Error('Hors ligne'));
	await viderPromesses();
	assert.equal(p.connexions[1].fermee, true);
	p.suivi.arreter();
});

test('borner une capture HTTP qui ne répond jamais', async () => {
	const p = demarrer();
	await p.avancer(9999);
	assert.equal(p.connexions[0].fermee, false);
	await p.avancer(1);
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.requetes[0].signal.aborted, true);
	assert.equal(p.etats.at(-1), 'reconnexion');
	p.suivi.arreter();
});
