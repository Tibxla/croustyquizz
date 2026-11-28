import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { monterDesinscription } from '../../src/main/resources/static/js/desinscription.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const CLE = '32000000-0000-4000-8000-000000000001.' + 'A'.repeat(43);

function monter(hash, appeler) {
	const document = creerDocument();
	const zone = document.createElement('main');
	const bouton = document.createElement('button');
	const aide = document.createElement('p');
	aide.setAttribute('data-aide-desinscription', '');
	const statut = document.createElement('p');
	statut.setAttribute('role', 'status');
	zone.append(aide, bouton, statut);
	const remplacements = [];
	monterDesinscription(zone, {
		navigation: { hash, pathname: '/desinscription.html', search: '' },
		historique: { replaceState: (...args) => remplacements.push(args.at(-1)) },
		appeler,
	});
	return { bouton, aide, statut, remplacements };
}

test('retire la clé de l’adresse et attend la confirmation avant de l’envoyer dans le JSON', async () => {
	const appels = [];
	const ui = monter(`#cle=${CLE}`, async (...args) => {
		appels.push(args);
		return { statut: 204 };
	});
	assert.deepEqual(ui.remplacements, ['/desinscription.html']);
	assert.deepEqual(appels, []);
	assert.equal(ui.bouton.hidden, false);
	ui.bouton.click();
	await setImmediate();
	assert.deepEqual(appels, [['POST', '/api/contacts/desinscription', { corps: { cle: CLE } }]]);
	assert.match(ui.statut.textContent, /désinscription est confirmée/);
	assert.equal(ui.bouton.hidden, true);
});

test('un lien mal formé ne permet aucun envoi et une panne permet une tentative manuelle', async () => {
	const invalide = monter('#cle=inconnue', () => assert.fail('aucun appel attendu'));
	assert.equal(invalide.bouton.hidden, true);
	assert.match(invalide.statut.textContent, /invalide/);

	const ui = monter(`#cle=${CLE}`, async () => ({ statut: 0 }));
	ui.bouton.click();
	await setImmediate();
	assert.equal(ui.bouton.disabled, false);
	assert.match(ui.statut.textContent, /Impossible de confirmer/);
});
