// Menu replié (#282, #280) : le « Plus » de la Console et le menu de la Manette, au clavier comme au doigt.
import test from 'node:test';
import assert from 'node:assert/strict';
import { monterMenu } from '../../src/main/resources/static/js/menu.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

function banc() {
	const document = creerDocument();
	const bouton = document.createElement('button');
	const panneau = document.createElement('div');
	panneau.id = 'menu-plus';
	const premier = document.createElement('button'), second = document.createElement('button');
	const ailleurs = document.createElement('button');
	panneau.append(premier, second);
	document.body.append(bouton, panneau, ailleurs);
	const menu = monterMenu(bouton, panneau);
	return { document, bouton, panneau, premier, second, ailleurs, menu };
}

test('le menu est fermé au départ et relié à son bouton', () => {
	const { bouton, panneau, menu } = banc();
	assert.equal(panneau.hidden, true);
	assert.equal(bouton.getAttribute('aria-expanded'), 'false');
	assert.equal(bouton.getAttribute('aria-controls'), 'menu-plus');
	assert.equal(menu.ouvert, false);
	menu.demonter();
});

test('ouvrir donne le focus au premier contrôle, Échap ferme et rend le focus au bouton', () => {
	const { document, bouton, panneau, premier, menu } = banc();
	bouton.click();
	assert.equal(panneau.hidden, false);
	assert.equal(bouton.getAttribute('aria-expanded'), 'true');
	assert.ok(document.activeElement === premier);
	const echap = { type: 'keydown', key: 'Escape', preventDefault() { this.empeche = true; } };
	panneau.dispatchEvent(echap);
	assert.equal(echap.empeche, true);
	assert.equal(panneau.hidden, true);
	assert.equal(bouton.getAttribute('aria-expanded'), 'false');
	assert.ok(document.activeElement === bouton);
	menu.demonter();
});

test('un second appui sur le bouton referme le menu', () => {
	const { bouton, panneau, menu } = banc();
	bouton.click(); bouton.click();
	assert.equal(panneau.hidden, true);
	menu.demonter();
});

test('choisir une commande du menu le referme et rend le focus au bouton', () => {
	const { document, bouton, panneau, second, menu } = banc();
	let choisi = false;
	second.addEventListener('click', () => { choisi = true; });
	bouton.click();
	second.click();
	panneau.dispatchEvent({ type: 'click', target: second }); // Le faux DOM ne propage pas : le clic remonte à la main.
	assert.equal(choisi, true);
	assert.equal(panneau.hidden, true);
	assert.ok(document.activeElement === bouton);
	menu.demonter();
});

test('un appui hors du menu le referme sans voler le focus', () => {
	const { document, bouton, panneau, ailleurs, menu } = banc();
	bouton.click();
	ailleurs.focus();
	document.dispatchEvent({ type: 'click', target: ailleurs });
	assert.equal(panneau.hidden, true);
	assert.ok(document.activeElement === ailleurs);
	// Un appui dans le menu ou sur son bouton ne compte pas comme « dehors ».
	bouton.click();
	document.dispatchEvent({ type: 'click', target: panneau });
	assert.equal(panneau.hidden, false);
	menu.demonter();
});

test('le démontage retire les écouteurs', () => {
	const { bouton, panneau, menu } = banc();
	menu.demonter();
	bouton.click();
	assert.equal(panneau.hidden, true);
});
