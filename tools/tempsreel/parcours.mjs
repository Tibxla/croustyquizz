import assert from 'node:assert/strict';
import { suivreVue } from '../../src/main/resources/static/js/tempsreel.js';
import { creerAdaptateurPilotable, viderPromesses } from './adaptateur-pilotable.mjs';

export async function rejouerReconnexion(scenario) {
	const final = scenario.tampon.find(m => m.revision === scenario.revisionFinale);
	assert.ok(final, 'La Révision finale doit être présente dans le tampon');
	assert.ok(scenario.revisionFinale > scenario.etat.revision);
	assert.equal(final.type, scenario.typeEvenementFinal);
	assert.deepEqual(final.donnees, scenario.vueFinale);
	const pilote = creerAdaptateurPilotable([scenario.etat.abonnementId]);
	const vues = [];
	const etats = [];
	const suivi = suivreVue({
		destination: scenario.destination,
		routeEtat: scenario.routeEtat,
		surVue: (vue, { type }) => vues.push({ vue, type }),
		surEtat: (etat) => etats.push(etat),
	}, pilote.adaptations);
	try {
		pilote.connexions[0].surConnexion();
		assert.equal(pilote.requetes[0].abonnementId, scenario.etat.abonnementId);
		assert.deepEqual(pilote.connexions[0].abonnements, [{
			destination: scenario.destination, abonnementId: scenario.etat.abonnementId,
		}]);
		assert.equal(pilote.requetes[0].route, scenario.routeEtat);
		for (const message of scenario.tampon) pilote.connexions[0].surMessage(message);
		assert.equal(vues.length, 0, 'Aucune vue avant la capture retardée');
		pilote.requetes[0].resoudre({ statut: 200, corps: scenario.etat });
		await viderPromesses();
		assert.deepEqual(vues, [
			{ vue: scenario.etat.vue, type: 'capture' },
			{ vue: scenario.vueFinale, type: scenario.typeEvenementFinal },
		]);
		assert.equal(etats.at(-1), 'synchronisee');
		// Une réémission de la Révision finale ne doit pas rappeler la page.
		pilote.connexions[0].surMessage(final);
		assert.equal(vues.length, 2);
		assert.equal(etats.at(-1), 'synchronisee');
		assert.equal(suivi.maintenantServeur(), Date.parse(scenario.etat.heureServeur));
	} finally {
		suivi.arreter();
	}
}
