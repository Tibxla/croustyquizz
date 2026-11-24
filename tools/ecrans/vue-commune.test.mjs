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

test('l’avertissement du retardataire ne dépend que de eligiblePourMancheActive, quel que soit le Mode de jeu', () => {
	// Le serveur met ce champ à vrai quand le Mode de jeu de la Manche active admet les retardataires (Karaoké, Freestyle).
	const vue = { soiree: {}, mancheActive: { ordre: 0 }, joueurs: [{ id: 'j', pseudo: 'Jo' }], joueurId: 'j', classement: [] };
	for (const modeEnCours of ['QUIZ', 'BLIND_TEST', 'KARAOKE', 'FREESTYLE']) {
		assert.match(decrireVue({ ...vue, modeEnCours, eligiblePourMancheActive: false }).personnel, /Tu participeras/, modeEnCours);
		assert.equal(decrireVue({ ...vue, modeEnCours, eligiblePourMancheActive: true }).personnel, 'Jo', modeEnCours);
	}
	assert.equal(decrireVue({ ...vue, mancheActive: null, modeEnCours: null, eligiblePourMancheActive: false }).personnel, 'Jo');
});

test('le résumé de la Console affiche le Pilotage de la Soirée', () => {
	const vue = { soiree: { etat: 'EN_COURS', format: 'SOLO', pilotage: 'AUTOMATIQUE' }, joueurs: [], classement: [] };
	assert.equal(decrireVue(vue).resume, 'En cours · Solo · Pilotage automatique');
	assert.equal(decrireVue({ ...vue, soiree: { ...vue.soiree, pilotage: 'MANUEL' } }).resume,
		'En cours · Solo · Pilotage manuel');
});

test('le Palmarès distingue champ absent, champ nul et liste vide', () => {
	const vue = { soiree: {}, joueurs: [], classement: [] };
	assert.equal(decrireVue(vue).palmares, null);
	assert.equal(decrireVue({ ...vue, palmaresKaraoke: null }).palmares, null);
	assert.deepEqual(decrireVue({ ...vue, palmaresKaraoke: [] }).palmares, []);
});

test('le Palmarès affiche le rang éventuel, le numéro, les Interprètes et le résultat du vote', () => {
	const vue = { soiree: {}, joueurs: [], classement: [], palmaresKaraoke: [
		{ numero: 12, rang: 1, chanson: { titre: 'Les quais' }, interpretes: [{ pseudo: 'Alizé' }, { pseudo: 'Basile' }],
			nombreVotes: 7, moyenneDixiemes: 43 },
		{ numero: 9, rang: null, chanson: { titre: 'Le phare' }, interpretes: [{ pseudo: 'Joueur parti' }],
			nombreVotes: 0, moyenneDixiemes: null },
	] };
	assert.deepEqual(decrireVue(vue).palmares, [
		{ numero: 12, rang: 1, titre: 'Les quais', interpretes: 'Alizé, Basile', note: '4,3 sur 5', votes: '7 votes',
			texte: '1. n° 12 · Les quais · Alizé, Basile · 4,3 sur 5 (7 votes)' },
		{ numero: 9, rang: null, titre: 'Le phare', interpretes: 'Joueur parti', note: null, votes: 'Aucun vote',
			texte: 'n° 9 · Le phare · Joueur parti · Aucun vote' },
	]);
});
