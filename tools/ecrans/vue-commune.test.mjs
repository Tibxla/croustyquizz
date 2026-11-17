import test from 'node:test';
import assert from 'node:assert/strict';
import { decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';

test('la partie commune restitue les données du serveur, y compris les personnes déconnectées et les points corrigés', () => {
	const description = decrireVue({ soiree: { titre: 'Soirée de test', etat: 'EN_COURS', format: 'SOLO', pilotage: 'MANUEL', codeAcces: 'DEMO01' },
		modeEnCours: 'BLIND_TEST', mancheActive: { ordre: 2, etat: 'EN_PAUSE' },
		joueurs: [{ id: 'j1', pseudo: '<script>Joueur</script>', connecte: false }], joueurId: 'j1', eligiblePourMancheActive: false,
		classement: [{ rang: 1, nom: 'Joueur', points: 850 }], dernierBuzz: { pseudo: 'Joueur' },
		programme: [{ ordre: 2, mode: 'BLIND_TEST', etat: 'EN_PAUSE' }], resultatPersonnel: { pointsGagnes: 850 },
	});
	assert.equal(description.manche, 'Blind test · Manche 3 · En pause');
	assert.deepEqual(description.joueurs, ['<script>Joueur</script> · déconnecté']);
	assert.deepEqual(description.classement, ['1. Joueur · 850 points']);
	assert.equal(description.personnel, '<script>Joueur</script> · Tu participeras à la prochaine Manche. · 850 points à la dernière correction');
	assert.deepEqual(description.programme, ['3. Blind test · En pause']);
});
