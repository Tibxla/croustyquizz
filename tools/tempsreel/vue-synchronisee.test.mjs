import test from 'node:test';
import assert from 'node:assert/strict';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable, viderPromesses } from './adaptateur-pilotable.mjs';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
const { parse } = createRequire(new URL('../api/package.json', import.meta.url))('yaml');
import { rejouerReconnexion } from './parcours.mjs';

test('la recapture demandée annule l’ancien flux et livre une capture puis des événements identifiés', async () => {
	const p = demarrer();
	await repondre(p);
	p.suivi.resynchroniser();
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.etats.at(-1), 'synchronisation');
	p.connexions[1].surConnexion();
	assert.notEqual(p.requetes[0].abonnementId, p.requetes[1].abonnementId);
	await repondre(p, 1, 20);
	p.connexions[1].surMessage({ revision: 21, type: 'question-ouverte', donnees: { phase: 'ouverte' } });
	assert.deepEqual(p.vues.at(-2), { vue: { phase: 'capture' }, source: 'capture', type: 'capture' });
	assert.deepEqual(p.vues.at(-1), { vue: { phase: 'ouverte' }, source: 'evenement', type: 'question-ouverte' });
	p.suivi.arreter();
	p.suivi.resynchroniser();
	assert.equal(p.connexions.length, 2);
});

test('une reconnexion accepte une capture à la Révision 0 après éviction', async () => {
	const p = demarrer();
	await repondre(p, 0, 42, { phase: 'podium' });
	p.connexions[0].surDeconnexion();
	await p.avancer(2000);
	p.connexions[1].surConnexion();
	await repondre(p, 1, 0, { phase: 'podium reconstruit' });
	assert.deepEqual(p.vues.at(-1), { vue: { phase: 'podium reconstruit' }, source: 'capture', type: 'capture' });
	assert.equal(p.etats.at(-1), 'synchronisee');
	p.suivi.arreter();
});

const exemples = parse(await readFile(new URL('../../docs/api/exemples/parcours-quiz.yaml', import.meta.url), 'utf8'));

test('rejouer la capture retardée, les Révisions déjà couvertes et le doublon du parcours Quiz', async () => {
	await rejouerReconnexion(exemples.resynchronisation);
});

test('rejouer la capture retardée, les Révisions déjà couvertes et le doublon du parcours Karaoké', async () => {
	const karaoke = parse(await readFile(new URL('../../docs/api/exemples/parcours-karaoke.yaml', import.meta.url), 'utf8'), { maxAliasCount: 1000 });
	await rejouerReconnexion(karaoke.resynchronisation);
});

test('rejouer la capture retardée, les Révisions déjà couvertes et le doublon du parcours du Passage', async () => {
	const passage = parse(await readFile(new URL('../../docs/api/exemples/parcours-passage-karaoke.yaml', import.meta.url), 'utf8'), { maxAliasCount: 1000 });
	await rejouerReconnexion(passage.resynchronisation);
});

test('rejouer la reconnexion de la Manette pendant le Vote du public', async () => {
	const vote = parse(await readFile(new URL('../../docs/api/exemples/parcours-vote-karaoke.yaml', import.meta.url), 'utf8'), { maxAliasCount: 1000 });
	await rejouerReconnexion(vote.resynchronisation);
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
	assert.equal(p.etats.at(-1), 'coupure');
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
		assert.equal(p.etats.at(-1), 'coupure');
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
	assert.equal(p.etats.at(-1), 'coupure');
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
	assert.deepEqual(p.vues, [{ vue: { phase: 'nouvelle' }, source: 'capture', type: 'capture' }]);
	assert.equal(p.etats.at(-1), 'synchronisee');
	p.suivi.arreter();
});

test('refuser une capture dont l’UUID ne correspond pas à la requête courante', async () => {
	const p = demarrer();
	p.requetes[0].resoudre({ statut: 200, corps: exemples.resynchronisation.etat });
	await viderPromesses();
	assert.equal(p.vues.length, 0);
	assert.equal(p.etats.at(-1), 'coupure');
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
	assert.equal(p.etats.at(-1), 'coupure');
	p.suivi.arreter();
});

for (const revision of [undefined, -1, 1.5, '10', Number.MAX_SAFE_INTEGER + 1]) {
	test(`refuser une Révision invalide dans la capture : ${revision}`, async () => {
		const p = demarrer();
		await repondre(p, 0, revision === undefined ? null : revision);
		assert.equal(p.vues.length, 0);
		assert.equal(p.etats.at(-1), 'coupure');
		p.suivi.arreter();
	});
}

test('refuser un message malformé avant la capture et reconnecter sur erreur réseau', async () => {
	const p = demarrer();
	p.connexions[0].surMessage({ revision: '10', donnees: {} });
	assert.equal(p.etats.at(-1), 'coupure');
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
	assert.equal(p.etats.at(-1), 'coupure');
	p.suivi.arreter();
});

function demarrerSansConnexion() {
	const pilote = creerAdaptateurPilotable();
	const vues = [];
	const etats = [];
	const refus = [];
	const suivi = suivreVue({ destination: '/topic/salle', routeEtat: '/etat/salle',
		surVue: (vue, contexte) => vues.push({ vue, ...contexte }),
		surEtat: etat => etats.push(etat), surRefus: motif => refus.push(motif),
	}, pilote.adaptations);
	return { ...pilote, vues, etats, refus, suivi };
}

test('un CONNECT refusé affiche son motif et attend une reprise explicite malgré les demandes de capture', async () => {
	const p = demarrerSansConnexion();
	p.connexions[0].surTrameErreur('Cette Soirée n’accepte pas d’Écran de démonstration.');
	p.connexions[0].surDeconnexion();
	p.suivi.resynchroniser();
	await p.avancer(60000);
	assert.deepEqual(p.refus, ['Cette Soirée n’accepte pas d’Écran de démonstration.']);
	assert.equal(p.etats.at(-1), 'coupure');
	assert.equal(p.connexions.length, 1);
	assert.equal(p.connexions[0].fermee, true);
	assert.equal(p.minuteries.size, 0);
	p.suivi.resynchroniser({ explicite: true });
	assert.equal(p.connexions.length, 2);
	assert.equal(p.etats.at(-1), 'synchronisation');
	p.connexions[1].surConnexion();
	await repondre(p);
	assert.equal(p.etats.at(-1), 'synchronisee');
	p.suivi.arreter();
});

test('un refus sans motif affiche un motif générique', () => {
	const p = demarrerSansConnexion();
	p.connexions[0].surTrameErreur(undefined);
	assert.deepEqual(p.refus, ['Le serveur a refusé la connexion en temps réel.']);
	p.suivi.arreter();
});

test('une trame ERROR après CONNECTED reste une coupure suivie de la reconnexion', async () => {
	const p = demarrerSansConnexion();
	p.connexions[0].surConnexion();
	p.connexions[0].surTrameErreur('Trame illisible.');
	assert.deepEqual(p.refus, []);
	assert.equal(p.etats.at(-1), 'coupure');
	await p.avancer(2000);
	assert.equal(p.connexions.length, 2);
	p.suivi.arreter();
});
