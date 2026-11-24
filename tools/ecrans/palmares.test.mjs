import test from 'node:test';
import assert from 'node:assert/strict';
import { creerLignePalmaresSalle } from '../../src/main/resources/static/js/palmares.mjs';
import { decrireVue } from '../../src/main/resources/static/js/vue-commune.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

function rendreLigne(passage) {
	const { palmares } = decrireVue({ soiree: {}, joueurs: [], classement: [], palmaresKaraoke: [passage] });
	return creerLignePalmaresSalle(creerDocument(), palmares[0]);
}

test('la salle affiche les pseudos complets et fournit une seule phrase aux lecteurs d’écran', () => {
	const titre = 'La Chanson au titre très long que les Interprètes ont choisie pour cette Soirée de démonstration';
	const pseudo = 'Joueuse de démonstration Alizé';
	const ligne = rendreLigne({ numero: 10, rang: 1, chanson: { titre }, interpretes: [{ pseudo }, { pseudo: 'Basile' }],
		nombreVotes: 7, moyenneDixiemes: 43 });

	assert.equal(ligne.tagName, 'LI');
	assert.equal(ligne.getAttribute('data-teinte'), '2');
	const presentation = ligne.querySelector('[aria-hidden="true"]');
	assert.equal(presentation?.querySelector('.palmares__rang')?.textContent, '1.');
	assert.equal(presentation?.querySelector('.palmares__numero')?.textContent, 'n° 10');
	assert.equal(presentation?.querySelector('.palmares__interpretes')?.textContent, `${pseudo} et Basile`);
	assert.equal(presentation?.querySelector('.palmares__chanson')?.textContent, titre);
	assert.equal(presentation?.querySelector('.palmares__note')?.textContent, '4,3 sur 5');
	assert.equal(presentation?.querySelector('.palmares__votes')?.textContent, '7 votes');
	assert.deepEqual(ligne.children.filter(enfant => enfant.getAttribute('aria-hidden') !== 'true')
		.map(enfant => [enfant.className, enfant.textContent]), [
			['visuellement-cache', `1. n° 10 · ${titre} · ${pseudo} et Basile · 4,3 sur 5 (7 votes)`],
		]);
});

test('un Passage sans vote conserve sa souche et affiche « Aucun vote » sans rang ni note', () => {
	const ligne = rendreLigne({ numero: 9, rang: null, chanson: { titre: 'Le phare' }, interpretes: [{ pseudo: 'Alizé' }],
		nombreVotes: 0, moyenneDixiemes: null });

	assert.equal(ligne.getAttribute('data-teinte'), '1');
	assert.equal(Boolean(ligne.querySelector('.palmares__rang')), false);
	assert.equal(Boolean(ligne.querySelector('.palmares__note')), false);
	assert.equal(ligne.querySelector('.palmares__votes')?.textContent, 'Aucun vote');
	assert.equal(ligne.querySelector('.visuellement-cache')?.textContent, 'n° 9 · Le phare · Alizé · Aucun vote');
});
