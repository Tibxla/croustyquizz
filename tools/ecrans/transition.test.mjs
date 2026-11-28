import test from 'node:test';
import assert from 'node:assert/strict';
import { monterTransition } from '../../src/main/resources/static/js/transition.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const finPrevueLe = '2026-11-25T20:30:15.000Z';
const vueTransition = (modeSuivant = 'QUIZ') => ({ transitionManche: { finPrevueLe, modeSuivant } });

function installer(ecran, avance = 12_000) {
	const document = creerDocument();
	const zone = document.createElement('section');
	const annonce = document.createElement('p');
	const delai = document.createElement('p');
	annonce.setAttribute('data-transition-annonce', '');
	delai.setAttribute('data-transition-delai', '');
	zone.append(annonce, delai);
	zone.hidden = true;
	let maintenant = Date.parse(finPrevueLe) - avance;
	const minuteries = new Map();
	let suivante = 0;
	const minuterie = {
		setTimeout(rappel, delaiMs) { minuteries.set(++suivante, { rappel, echeance: maintenant + delaiMs }); return suivante; },
		clearTimeout(numero) { minuteries.delete(numero); },
	};
	const transition = monterTransition(zone, { ecran, maintenantServeur: () => maintenant, minuterie });
	const prochaine = () => [...minuteries].sort(([, a], [, b]) => a.echeance - b.echeance)[0];
	// Déclenche la prochaine minuterie avec un retard, comme un navigateur occupé.
	function declencher(retard = 0) {
		const [numero, { rappel, echeance }] = prochaine();
		minuteries.delete(numero);
		maintenant = echeance + retard;
		rappel();
	}
	return {
		zone, annonce, delai, transition, minuteries, declencher,
		avancer(ms) {
			const cible = maintenant + ms;
			while (minuteries.size > 0 && prochaine()[1].echeance <= cible) declencher();
			maintenant = cible;
		},
	};
}

test('l’Écran de salle affiche l’annonce et le compte à rebours calé sur l’heure du serveur', () => {
	const banc = installer('ecran');
	banc.transition.afficher(vueTransition());
	assert.equal(banc.zone.hidden, false);
	assert.equal(banc.annonce.textContent, 'Prochaine Manche : Quiz');
	assert.equal(banc.delai.textContent, 'dans 12 s');
	assert.equal(banc.minuteries.size, 1);
	banc.avancer(1100);
	assert.equal(banc.delai.textContent, 'dans 11 s');
	banc.avancer(20_000);
	assert.equal(banc.delai.textContent, 'dans 0 s');
	assert.equal(banc.minuteries.size, 0, 'plus aucune minuterie une fois à zéro');
});

test('chaque seconde s’affiche une fois, même quand les minuteries tirent en retard', () => {
	const banc = installer('ecran', 12_300);
	banc.transition.afficher(vueTransition());
	const vus = [banc.delai.textContent];
	// Retards sous la seconde, marge comprise : au-delà, la minuterie a forcément manqué une seconde.
	const retards = [0, 400, 0, 950, 10, 700, 0, 900, 300, 0, 500, 0, 0, 0];
	for (let i = 0; banc.minuteries.size > 0; i++) {
		assert.ok(i < 30, 'le compte à rebours s’arrête');
		banc.declencher(retards[i % retards.length]);
		vus.push(banc.delai.textContent);
	}
	assert.deepEqual(vus, Array.from({ length: 14 }, (_, i) => `dans ${13 - i} s`));
});

test('une seule minuterie vit pendant la transition et disparaît avec elle ou au départ de la page', () => {
	const banc = installer('console');
	banc.transition.afficher(vueTransition());
	banc.transition.afficher(vueTransition(null));
	assert.equal(banc.minuteries.size, 1);
	assert.equal(banc.annonce.textContent, 'Transition : Fin de la Soirée');
	banc.transition.afficher({ transitionManche: null });
	assert.equal(banc.minuteries.size, 0);
	assert.equal(banc.zone.hidden, true);
	banc.transition.afficher(vueTransition());
	assert.equal(banc.minuteries.size, 1);
	banc.transition.demonter();
	assert.equal(banc.minuteries.size, 0);
	assert.equal(banc.zone.hidden, true);
});

test('la Manette annonce la prochaine Manche ou la fin de la Soirée', () => {
	const banc = installer('manette');
	banc.transition.afficher(vueTransition('KARAOKE'));
	assert.equal(`${banc.annonce.textContent} ${banc.delai.textContent}`, 'Prochaine Manche dans 12 s');
	banc.transition.afficher(vueTransition(null));
	assert.equal(`${banc.annonce.textContent} ${banc.delai.textContent}`, 'Fin de la Soirée dans 12 s');
});
