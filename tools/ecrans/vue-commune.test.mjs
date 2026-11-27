import test from 'node:test';
import assert from 'node:assert/strict';
import { decrireBarre, decrireClassementDeSalle, decrireConnexion, decrirePalmaresDeSalle, decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';

test('la partie commune restitue les données du serveur, y compris les personnes déconnectées et les points corrigés', () => {
	const vue1 = { soiree: { titre: 'Soirée de test', etat: 'EN_COURS', format: 'SOLO', pilotage: 'MANUEL', codeAcces: 'DEMO01' },
		modeEnCours: 'BLIND_TEST', mancheActive: { ordre: 2, etat: 'EN_PAUSE' },
		joueurs: [{ id: 'j1', pseudo: '<script>Joueur</script>', connecte: false }], joueurId: 'j1', eligiblePourMancheActive: false,
		classement: [{ concurrentId: 'j1', rang: 1, nom: 'Joueur', points: 850 }], dernierBuzz: { pseudo: 'Joueur' },
		programme: [{ ordre: 2, mode: 'BLIND_TEST', etat: 'EN_PAUSE' }], resultatPersonnel: { pointsGagnes: 850 },
	};
	const description = decrireVue(vue1);
	assert.equal(description.manche, 'Blind test · Manche 3 · En pause');
	assert.deepEqual(decrireVue(vue1, { ecran: 'console' }).joueurs, ['<script>Joueur</script> · déconnecté']);
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
	// Sans classement, la Manette ne dit rien plutôt qu'un « Non classé · 0 point » qui inquiète (#279).
	assert.equal(decrireVue({ ...vue, classement: [] }).personnel, 'Léa');
});

test('pendant une Manche sans points (Karaoké, Freestyle), la Manette ne montre pas le classement (#279)', () => {
	const vue = { soiree: { format: 'SOLO' }, joueurs: [{ id: 'j', pseudo: 'Léa' }], joueurId: 'j', mancheActive: { ordre: 0 },
		eligiblePourMancheActive: true, classement: [{ concurrentId: 'j', rang: 2, nom: 'Léa', points: 5 }] };
	for (const modeEnCours of ['KARAOKE', 'FREESTYLE']) assert.equal(decrireVue({ ...vue, modeEnCours }).personnel, 'Léa', modeEnCours);
	assert.equal(decrireVue({ ...vue, modeEnCours: 'QUIZ' }).personnel, 'Léa · Rang 2 · 5 points');
});

test('le Format et le Pilotage restent sur la Console, pas sur la Manette ni sur la salle (#279)', () => {
	const vue = { soiree: { etat: 'EN_COURS', format: 'SOLO', pilotage: 'MANUEL' }, joueurs: [], classement: [] };
	assert.equal(decrireVue(vue, { ecran: 'console' }).resume, 'En cours · Solo · Pilotage manuel');
	assert.equal(decrireVue(vue, { ecran: 'manette' }).resume, '');
	assert.equal(decrireVue(vue, { ecran: 'ecran' }).resume, '');
});

test('seule la Console voit qui est déconnecté ; ailleurs, les pseudos suffisent (#279)', () => {
	const vue = { soiree: {}, joueurs: [{ id: 'a', pseudo: 'Léa', connecte: true }, { id: 'b', pseudo: 'Max', connecte: false }], classement: [] };
	assert.deepEqual(decrireVue(vue, { ecran: 'console' }).joueurs, ['Léa · connecté', 'Max · déconnecté']);
	assert.deepEqual(decrireVue(vue, { ecran: 'ecran' }).joueurs, ['Léa', 'Max']);
	assert.deepEqual(decrireVue(vue, { ecran: 'manette' }).joueurs, ['Léa', 'Max']);
});

test('le Buzz ne s’affiche que hors Manche, et seulement s’il y en a un (#281, M4)', () => {
	const vue = { soiree: { etat: 'EN_COURS' }, joueurs: [], classement: [], modeEnCours: null, dernierBuzz: null };
	assert.equal(decrireVue(vue).buzz, '');
	assert.equal(decrireVue({ ...vue, dernierBuzz: { pseudo: 'Léa' } }).buzz, 'Dernier Buzz : Léa');
	assert.equal(decrireVue({ ...vue, modeEnCours: 'KARAOKE', dernierBuzz: { pseudo: 'Léa' } }).buzz, '');
});

test('l’état de connexion ne prend la parole qu’en cas de coupure (#279)', () => {
	assert.deepEqual(decrireConnexion('synchronisee'), { texte: 'En direct', coupure: false });
	assert.deepEqual(decrireConnexion('synchronisation'), { texte: 'Connexion…', coupure: false });
	assert.deepEqual(decrireConnexion('coupure'), { texte: 'Connexion perdue, on se reconnecte…', coupure: true });
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
	assert.equal(decrireVue(vue, { ecran: 'console' }).resume, 'En cours · Solo · Pilotage automatique');
	assert.equal(decrireVue({ ...vue, soiree: { ...vue.soiree, pilotage: 'MANUEL' } }, { ecran: 'console' }).resume,
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
		{ numero: 12, rang: 1, titre: 'Les quais', interpretes: 'Alizé et Basile', moyenne: '4,3', votes: '7 votes',
			texte: '1. n° 12 · Les quais · Alizé et Basile · 4,3 sur 5 (7 votes)' },
		{ numero: 9, rang: null, titre: 'Le phare', interpretes: 'Joueur parti', moyenne: null, votes: 'Aucun vote',
			texte: 'n° 9 · Le phare · Joueur parti · Aucun vote' },
	]);
});

test('le Palmarès énumère trois Interprètes ou plus à la française', () => {
	const vue = { soiree: {}, joueurs: [], classement: [], palmaresKaraoke: [
		{ numero: 4, rang: 1, chanson: { titre: 'Le trio' }, interpretes: [{ pseudo: 'Alizé' }, { pseudo: 'Basile' }, { pseudo: 'Chloé' }],
			nombreVotes: 2, moyenneDixiemes: 50 },
	] };
	const [ligne] = decrireVue(vue).palmares;
	assert.equal(ligne.interpretes, 'Alizé, Basile et Chloé');
	assert.equal(ligne.texte, '1. n° 4 · Le trio · Alizé, Basile et Chloé · 5,0 sur 5 (2 votes)');
});

test('le Palmarès du Karaoké ou du Freestyle prend la place du Classement de salle et du texte d’attente', () => {
	const classement = [{ rang: 1, nom: 'Lou', points: 10 }];
	const vue = { soiree: { etat: 'EN_COURS' }, modeEnCours: null, mancheActive: null, joueurs: [], classement };
	assert.equal(decrireVue(vue).attente, 'La prochaine Manche s’affichera ici.');
	assert.equal(decrireVue({ ...vue, palmaresKaraoke: [] }).attente, '');
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresKaraoke: [] }), null);
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresKaraoke: null }).titre, 'Classement général · Top 10');
	assert.deepEqual(decrirePalmaresDeSalle({ ...vue, palmaresKaraoke: [] }), []);
	assert.equal(decrirePalmaresDeSalle({ ...vue, palmaresKaraoke: null }), null);
	assert.equal(decrireVue({ ...vue, palmaresFreestyle: [] }).attente, '');
	assert.equal(decrireClassementDeSalle({ ...vue, palmaresFreestyle: [] }), null);
});

test('en fin de Soirée, le Podium final passe avant le Palmarès sur la salle, et la Console garde le Palmarès', () => {
	const classement = [{ rang: 1, nom: 'Lou', points: 10 }];
	const palmaresKaraoke = [{ numero: 2, rang: 1, chanson: { titre: 'Les quais' }, interpretes: [{ pseudo: 'Lou' }],
		nombreVotes: 1, moyenneDixiemes: 50 }];
	const terminee = { soiree: { etat: 'TERMINEE' }, modeEnCours: null, mancheActive: null, joueurs: [], classement };
	assert.equal(decrireVue(terminee).attente, '');
	assert.equal(decrireClassementDeSalle(terminee).titre, 'Podium final');
	const avecPalmares = { ...terminee, palmaresKaraoke };
	assert.deepEqual(decrireClassementDeSalle(avecPalmares),
		{ titre: 'Podium final', podium: true, lignes: ['1. Lou · 10 points'] });
	assert.equal(decrirePalmaresDeSalle(avecPalmares), null);
	assert.deepEqual(decrireVue(avecPalmares).palmares.map(ligne => ligne.texte),
		['1. n° 2 · Les quais · Lou · 5,0 sur 5 (1 vote)']);
});

test('en fin de Soirée, le Podium final passe aussi avant le Palmarès du Freestyle sur la salle (#224)', () => {
	const classement = [{ rang: 1, nom: 'Lou', points: 10 }];
	const palmaresFreestyle = [{ passageId: 'p1', numero: 3, rang: 1, instrumentale: { titre: 'Boom bap' },
		interprete: { pseudo: 'Lou' }, nombreVotes: 2, moyenneDixiemes: 45 }];
	const terminee = { soiree: { etat: 'TERMINEE' }, modeEnCours: null, mancheActive: null, joueurs: [], classement,
		palmaresFreestyle };
	assert.deepEqual(decrireClassementDeSalle(terminee),
		{ titre: 'Podium final', podium: true, lignes: ['1. Lou · 10 points'] });
	assert.equal(decrirePalmaresDeSalle(terminee), null);
	assert.deepEqual(decrireVue(terminee).palmares.map(ligne => ligne.texte),
		['1. n° 3 · Boom bap · Lou · 4,5 sur 5 (2 votes)']);
	const enCours = { ...terminee, soiree: { etat: 'EN_COURS' } };
	assert.equal(decrireClassementDeSalle(enCours), null);
	assert.deepEqual(decrirePalmaresDeSalle(enCours).map(ligne => ligne.texte), ['1. n° 3 · Boom bap · Lou · 4,5 sur 5 (2 votes)']);
});

test('la barre de pilotage dit où en est la Soirée, le code et le nombre de Joueurs (#282)', () => {
	const joueurs = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
	const base = { soiree: { etat: 'OUVERTE', codeAcces: 'K7P2QX' }, joueurs, mancheActive: null, modeEnCours: null };
	assert.deepEqual(decrireBarre(base), { mode: 'Avant le lancement', code: 'K7P2QX', joueurs: '3 Joueurs' });
	assert.deepEqual(decrireBarre({ ...base, soiree: { etat: 'PREPARATION', codeAcces: 'K7P2QX' }, joueurs: [] }),
		{ mode: 'Soirée pas encore ouverte', code: null, joueurs: 'Aucun Joueur' });
	assert.deepEqual(decrireBarre({ ...base, soiree: { etat: 'EN_COURS', codeAcces: 'K7P2QX' }, joueurs: [{ id: 'a' }],
		mancheActive: { ordre: 1, etat: 'EN_COURS' }, modeEnCours: 'KARAOKE' }), { mode: 'Karaoké · Manche 2', code: 'K7P2QX', joueurs: '1 Joueur' });
	assert.equal(decrireBarre({ ...base, soiree: { etat: 'EN_COURS' }, mancheActive: { ordre: 0, etat: 'EN_PAUSE' }, modeEnCours: 'QUIZ' }).mode,
		'Quiz · Manche 1 · En pause');
	assert.equal(decrireBarre({ ...base, soiree: { etat: 'EN_COURS' } }).mode, 'Entre deux Manches');
	assert.deepEqual(decrireBarre({ ...base, soiree: { etat: 'TERMINEE', codeAcces: 'K7P2QX' } }),
		{ mode: 'Soirée terminée', code: null, joueurs: '3 Joueurs' });
});
