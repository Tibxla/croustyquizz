import test from 'node:test';
import assert from 'node:assert/strict';
import { afficherClassementDeSalle, decrireClassementDeSalle, decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

function creerZoneClassement() {
	const document = creerDocument();
	const zone = document.createElement('section');
	zone.hidden = true;
	const message = document.createElement('p');
	message.textContent = 'Aucun point attribué.';
	zone.append(document.createElement('h2'), document.createElement('ol'), message);
	return zone;
}

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

	const zone = creerZoneClassement();
	assert.equal(afficherClassementDeSalle(zone, vue), true);
	assert.equal(zone.querySelector('ol').childElementCount, 10);
	assert.equal(zone.querySelector('ol').children.at(-1).textContent, '10. Joueur 10 · 6 points');
	assert.equal(afficherClassementDeSalle(zone, { ...vue, mancheActive: { ordre: 1, etat: 'EN_PAUSE' } }), false);
	assert.equal(zone.hidden, true);
});

test('le podium réunit les ex æquo sur la même marche et distingue le rang, les noms et les points', () => {
	const zone = creerZoneClassement();
	const vue = { soiree: { etat: 'TERMINEE' }, mancheActive: null, classement: [
		{ rang: 1, nom: 'Lou', points: 10 }, { rang: 1, nom: 'Sam', points: 10 },
		{ rang: 3, nom: 'Léa', points: 1 }, { rang: 4, nom: 'Maé', points: 0 },
	] };
	assert.equal(afficherClassementDeSalle(zone, vue, { source: 'capture' }), true);
	assert.equal(zone.hidden, false);
	assert.equal(zone.querySelector('h2').textContent, 'Podium final');
	assert.equal(zone.querySelector('p').hidden, true);
	assert.deepEqual(zone.querySelector('ol').children.map(marche => marche.getAttribute('data-rang')), ['1', '3']);
	assert.deepEqual(zone.querySelector('ol').children.map(marche => ({
		rang: marche.querySelector('.salle__podium-rang')?.textContent,
		noms: marche.querySelectorAll('.salle__podium-nom').map(nom => nom.textContent),
		points: marche.querySelectorAll('.salle__podium-points').map(points => points.textContent),
	})), [
		{ rang: '1', noms: ['Lou', 'Sam'], points: ['10 points', '10 points'] },
		{ rang: '3', noms: ['Léa'], points: ['1 point'] },
	]);
});

test('chaque marche affiche jusqu’à trois noms et annonce combien de concurrents restent', () => {
	const zone = creerZoneClassement();
	const classement = Array.from({ length: 5 }, (_, i) => ({ rang: 1, nom: `Joueur ${i + 1}`, points: 10 }));
	for (const nombre of [2, 3, 4, 5]) {
		afficherClassementDeSalle(zone, { soiree: { etat: 'TERMINEE' }, classement: classement.slice(0, nombre) },
			{ source: 'capture' });
		const marche = zone.querySelector('.salle__podium-marche');
		assert.equal(marche.getAttribute('data-nombre'), String(nombre));
		assert.deepEqual(marche.querySelectorAll('.salle__podium-nom').map(nom => nom.textContent),
			nombre === 2 ? ['Joueur 1', 'Joueur 2'] : ['Joueur 1', 'Joueur 2', 'Joueur 3']);
		const lignes = marche.querySelector('.salle__podium-concurrents').children;
		assert.equal(lignes.length, Math.min(nombre, 4));
		if (nombre > 3) assert.equal(lignes.at(-1).textContent, nombre === 4 ? 'et 1 autre' : 'et 2 autres');
	}
});

test('une capture de la fin de Soirée affiche le podium immobile, même avant un événement', () => {
	const vue = { soiree: { etat: 'TERMINEE' }, classement: [{ rang: 1, nom: 'Lou', points: 10 }] };
	const zone = creerZoneClassement();
	const entree = 'salle__classement--entree';
	afficherClassementDeSalle(zone, vue, { source: 'capture' });
	assert.equal(zone.classList.contains(entree), false);
	afficherClassementDeSalle(zone, vue, { source: 'evenement', type: 'classement-modifie' });
	assert.equal(zone.classList.contains(entree), false);
});

test('un événement reçu pendant l’entrée du podium conserve ses marches jusqu’à la dernière fin d’animation', () => {
	const vue = { soiree: { etat: 'TERMINEE' }, classement: [
		{ rang: 1, nom: 'Lou', points: 10 }, { rang: 2, nom: 'Sam', points: 5 },
	] };
	const evenement = { source: 'evenement', type: 'soiree-terminee' };
	const zone = creerZoneClassement();
	const entree = 'salle__classement--entree';

	afficherClassementDeSalle(zone, vue, evenement);
	assert.equal(zone.classList.contains(entree), true);
	const liste = zone.querySelector('ol');
	const [premiere, deuxieme] = liste.children;
	const vueSuivante = { ...vue, classement: vue.classement.map(ligne => ({ ...ligne })) };
	afficherClassementDeSalle(zone, vueSuivante, { source: 'evenement', type: 'classement-modifie' });
	assert.equal(zone.classList.contains(entree), true);
	assert.equal(liste.children[0] === premiere && liste.children[1] === deuxieme, true);
	afficherClassementDeSalle(zone, vueSuivante, { source: 'capture' });
	assert.equal(zone.classList.contains(entree), true);

	liste.dispatchEvent({ type: 'animationend', animationName: 'entree-marche', target: deuxieme });
	assert.equal(zone.classList.contains(entree), true);
	liste.dispatchEvent({ type: 'animationend', animationName: 'entree-marche', target: premiere });
	assert.equal(zone.classList.contains(entree), false);
	afficherClassementDeSalle(zone, vueSuivante, evenement);
	assert.equal(zone.classList.contains(entree), false);
	assert.equal(liste.children[0] === premiere, true);
});

test('quitter la fin de Soirée pendant l’entrée retire l’animation', () => {
	const vue = { soiree: { etat: 'TERMINEE' }, classement: [{ rang: 1, nom: 'Lou', points: 10 }] };
	const zone = creerZoneClassement();
	afficherClassementDeSalle(zone, vue, { source: 'evenement', type: 'soiree-terminee' });
	afficherClassementDeSalle(zone, { ...vue, soiree: { etat: 'EN_COURS' } }, { source: 'capture' });
	assert.equal(zone.classList.contains('salle__classement--entree'), false);
});

test('sans point attribué, la fin de Soirée affiche le message à la place des marches', () => {
	const zone = creerZoneClassement();
	afficherClassementDeSalle(zone, { soiree: { etat: 'TERMINEE' }, classement: [] }, { source: 'capture' });
	assert.equal(zone.querySelector('ol').hidden, true);
	const message = zone.querySelector('p');
	assert.equal(message.hidden, false);
	assert.equal(message.textContent, 'Aucun point attribué.');
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
