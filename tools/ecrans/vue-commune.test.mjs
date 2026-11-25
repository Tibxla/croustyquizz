import test from 'node:test';
import assert from 'node:assert/strict';
import { decrireClassementDeSalle, decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';

test('la partie commune restitue les données du serveur, y compris les personnes déconnectées et les points corrigés', () => {
	const description = decrireVue({ soiree: { titre: 'Soirée de test', etat: 'EN_COURS', format: 'SOLO', pilotage: 'MANUEL', codeAcces: 'DEMO01' },
		modeEnCours: 'BLIND_TEST', mancheActive: { ordre: 2, etat: 'EN_PAUSE' },
		joueurs: [{ id: 'j1', pseudo: '<script>Joueur</script>', connecte: false }], joueurId: 'j1', eligiblePourMancheActive: false,
		classement: [{ concurrentId: 'j1', rang: 1, nom: 'Joueur', points: 850 }], dernierBuzz: { pseudo: 'Joueur' },
		programme: [{ ordre: 2, mode: 'BLIND_TEST', etat: 'EN_PAUSE' }], resultatPersonnel: { pointsGagnes: 850 },
	});
	assert.equal(description.manche, 'Blind test · Manche 3 · En pause');
	assert.deepEqual(description.joueurs, ['<script>Joueur</script> · déconnecté']);
	assert.deepEqual(description.classement, ['1. Joueur · 850 points']);
	assert.equal(description.personnel, '<script>Joueur</script> · Rang 1 · 850 points · Tu participeras à la prochaine Manche. · 850 points à la dernière correction');
	assert.deepEqual(description.programme, ['3. Blind test · En pause']);
});

test('la salle affiche dix lignes entre deux Manches et retire le classement pendant une Manche', () => {
	const classement = Array.from({ length: 12 }, (_, i) => ({ rang: i + 1, nom: `Joueur ${i + 1}`, points: 15 - i }));
	const vue = { soiree: { etat: 'EN_COURS' }, mancheActive: null, classement };
	const description = decrireClassementDeSalle(vue);
	assert.equal(description.titre, 'Classement général · Top 10');
	assert.equal(description.lignes.length, 10);
	assert.equal(description.lignes.at(-1), '10. Joueur 10 · 6 points');
	assert.equal(decrireClassementDeSalle({ ...vue, mancheActive: { ordre: 1, etat: 'EN_PAUSE' } }), null);
	assert.deepEqual(decrireClassementDeSalle({ ...vue, classement: [] }).lignes, []);
	assert.equal(decrireClassementDeSalle({ ...vue, soiree: { etat: 'OUVERTE' }, classement: [] }), null);
});

test('le podium garde tous les ex æquo aux rangs donnés par le serveur', () => {
	const vue = { soiree: { etat: 'TERMINEE' }, mancheActive: null, classement: [
		{ rang: 1, nom: 'Lou', points: 10 }, { rang: 1, nom: 'Sam', points: 10 },
		{ rang: 3, nom: 'Léa', points: 8 }, { rang: 3, nom: 'Maé', points: 8 },
		{ rang: 5, nom: 'Noémie', points: 1 },
	] };
	assert.deepEqual(decrireClassementDeSalle(vue), {
		titre: 'Podium final', podium: true,
		lignes: ['1. Lou · 10 points', '1. Sam · 10 points', '3. Léa · 8 points', '3. Maé · 8 points'],
	});
	assert.deepEqual(decrireClassementDeSalle({ ...vue, classement: [] }).lignes, []);
});

test('la Manette affiche le rang hors du Top 10 et indique les Joueurs encore sans classement', () => {
	const vue = { soiree: { format: 'SOLO' }, joueurs: [{ id: 'j', pseudo: 'Léa' }], joueurId: 'j',
		classement: [{ concurrentId: 'j', rang: 12, nom: 'Léa', points: 1 }] };
	assert.equal(decrireVue(vue).personnel, 'Léa · Rang 12 · 1 point');
	assert.equal(decrireVue({ ...vue, classement: [] }).personnel, 'Léa · Non classé · 0 point');
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
		{ numero: 12, rang: 1, titre: 'Les quais', interpretes: 'Alizé et Basile', note: '4,3 sur 5', votes: '7 votes',
			texte: '1. n° 12 · Les quais · Alizé et Basile · 4,3 sur 5 (7 votes)' },
		{ numero: 9, rang: null, titre: 'Le phare', interpretes: 'Joueur parti', note: null, votes: 'Aucun vote',
			texte: 'n° 9 · Le phare · Joueur parti · Aucun vote' },
	]);
});

test('le Palmarès du Karaoké prend la place du Classement de salle et du texte d’attente', () => {
	const classement = [{ rang: 1, nom: 'Lou', points: 10 }];
	const vue = { soiree: { etat: 'EN_COURS' }, modeEnCours: null, mancheActive: null, joueurs: [], classement };
	assert.equal(decrireVue(vue).attente, 'La prochaine Manche s’affichera ici.');
	assert.equal(decrireVue({ ...vue, palmaresKaraoke: [] }).attente, '');
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresKaraoke: [] }), null);
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresKaraoke: null }).titre, 'Classement général · Top 10');
	const terminee = { ...vue, soiree: { etat: 'TERMINEE' } };
	assert.equal(decrireVue(terminee).attente, '');
	assert.equal(decrireClassementDeSalle(terminee).titre, 'Podium final');
	assert.equal(decrireClassementDeSalle({ ...terminee, palmaresKaraoke: [] }), null);
	assert.equal(decrireVue({ ...vue, palmaresFreestyle: [] }).attente, '');
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresFreestyle: [] }), null);
});
