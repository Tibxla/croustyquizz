// Plan de la Console pendant un Karaoké (#282) : le Passage courant, puis la File, puis le reste ; le Catalogue replié.
import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { monter as consoleFreestyle } from '../../src/main/resources/static/js/modes/freestyle/console.mjs';

const capture = { source: 'capture', type: 'capture' };
// Ordre de lecture des titres et des commandes de la zone principale, dans le DOM.
function ordre(zone) {
	const liste = [];
	const parcourir = element => {
		if (['H2', 'H3'].includes(element.tagName)) liste.push(element.textContent);
		else if (element.tagName === 'BUTTON' && !element.classList.contains('ticket')) liste.push(`[${element.textContent}]`);
		else if (element.tagName === 'FORM') liste.push('<formulaire>');
		if (element.tagName !== 'FORM') element.children.forEach(parcourir);
	};
	parcourir(zone);
	return liste;
}

test('Karaoké : le Passage courant ouvre la colonne, ses commandes suivent, puis la File, la limite en dernier', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ passagesEnAttente: [passage()] }), capture);
	const lus = ordre(b.principale);
	const position = texte => lus.indexOf(texte);
	assert.ok(position('Passage courant') < position('[Pause]'), lus.join(' / '));
	assert.ok(position('[Appeler le suivant]') < position("File d'attente"), lus.join(' / '));
	assert.ok(position("File d'attente") < position('<formulaire>'), lus.join(' / '));
});

test('Karaoké : le Catalogue de la Console est replié sous un résumé', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.module.afficher(vue(), capture);
	const replie = b.laterale.querySelector('details');
	assert.ok(replie, 'un <details> porte le Catalogue');
	assert.equal(replie.open, false);
	assert.match(replie.querySelector('summary').textContent, /Catalogue/);
	assert.ok(replie.querySelector('form'), 'la recherche est dans le repli');
});

test('Freestyle : le Passage courant ouvre aussi la colonne de la Console', t => {
	const b = creerBanc(consoleFreestyle, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	const lus = () => ordre(b.principale);
	b.module.afficher({ modeEnCours: 'FREESTYLE', freestyle: { mancheId: 'm', etat: 'EN_COURS', courant: null, votes: [],
		passagesEnAttente: [], nombreEnAttente: 0, instrumentales: [], invitationsEnAttente: [] } }, capture);
	assert.equal(lus()[0], 'Passage courant', lus().join(' / '));
});
