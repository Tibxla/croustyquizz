import test from 'node:test';
import assert from 'node:assert/strict';
import { monterTransition } from '../../src/main/resources/static/js/transition.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const finPrevueLe = '2026-11-25T20:30:15.000Z';
const vueTransition = (modeSuivant = 'QUIZ') => ({ transitionManche: { finPrevueLe, modeSuivant } });

function installer(ecran) {
	const document = creerDocument();
	const zone = document.createElement('section');
	const annonce = document.createElement('p');
	const delai = document.createElement('p');
	annonce.setAttribute('data-transition-annonce', '');
	delai.setAttribute('data-transition-delai', '');
	zone.append(annonce, delai);
	zone.hidden = true;
	let maintenant = Date.parse(finPrevueLe) - 12_000;
	const minuteries = new Map();
	let suivante = 0;
	const minuterie = {
		setInterval(rappel, delaiMs) { minuteries.set(++suivante, { rappel, delaiMs }); return suivante; },
		clearInterval(numero) { minuteries.delete(numero); },
	};
	const transition = monterTransition(zone, { ecran, maintenantServeur: () => maintenant, minuterie });
	return {
		zone, annonce, delai, transition, minuteries,
		avancer(ms) { maintenant += ms; for (const { rappel } of [...minuteries.values()]) rappel(); },
	};
}

test('l’Écran de salle affiche l’annonce et le compte à rebours calé sur l’heure du serveur', () => {
	const banc = installer('ecran');
	banc.transition.afficher(vueTransition());
	assert.equal(banc.zone.hidden, false);
	assert.equal(banc.annonce.textContent, 'Prochaine Manche : Quiz');
	assert.equal(banc.delai.textContent, 'dans 12 s');
	assert.deepEqual([...banc.minuteries.values()].map(m => m.delaiMs), [1000]);
	banc.avancer(1000);
	assert.equal(banc.delai.textContent, 'dans 11 s');
	banc.avancer(20_000);
	assert.equal(banc.delai.textContent, 'dans 0 s');
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
