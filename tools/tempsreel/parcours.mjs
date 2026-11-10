import assert from 'node:assert/strict';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable, viderPromesses } from './adaptateur-pilotable.mjs';

export async function rejouerReconnexion(scenario) {
	const pilote = creerAdaptateurPilotable([scenario.etat.abonnementId]);
	const vues = [];
	const etats = [];
	const suivi = suivreVue({
		destination: `/topic/joueurs/${scenario.etat.vue.joueurId}`,
		routeEtat: '/etat/manette',
		surVue: (vue, { type }) => vues.push({ vue, type }),
		surEtat: (etat) => etats.push(etat),
	}, pilote.adaptations);
	try {
		pilote.connexions[0].surConnexion();
		assert.equal(pilote.requetes[0].abonnementId, scenario.etat.abonnementId);
		for (const message of scenario.tampon) pilote.connexions[0].surMessage(message);
		assert.equal(vues.length, 0, 'Aucune vue avant la capture retardée');
		pilote.requetes[0].resoudre({ statut: 200, corps: scenario.etat });
		await viderPromesses();
		assert.deepEqual(vues, [
			{ vue: scenario.etat.vue, type: 'capture' },
			{ vue: scenario.vueFinale, type: 'question-ouverte' },
		]);
		assert.equal(etats.at(-1), 'synchronisee');
		// Une réémission de la Révision finale ne doit pas rappeler la page.
		pilote.connexions[0].surMessage(scenario.tampon.find(m => m.revision === scenario.revisionFinale));
		assert.equal(vues.length, 2);
		assert.equal(suivi.maintenantServeur(), Date.parse(scenario.etat.heureServeur));
	} finally {
		suivi.arreter();
	}
}
