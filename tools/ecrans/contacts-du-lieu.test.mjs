import assert from 'node:assert/strict';
import test from 'node:test';
import { monterContactsDuLieu } from '../../src/main/resources/static/js/contacts-du-lieu.mjs';
import { retourApresConnexion } from '../../src/main/resources/static/js/compte.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const CONTACT = {
	prenom: 'Camille', nom: '<Exemple>', numero: '+33639980032',
	consentiLe: '2026-11-28T18:00:00Z', mention: 'Mention lue sur la Manette.',
};

function page() {
	const document = creerDocument();
	function balise(tag, data) {
		const element = document.createElement(tag);
		element.setAttribute(data, '');
		document.body.append(element);
		return element;
	}
	const statut = balise('p', 'data-statut-contacts');
	const tableau = balise('table', 'data-tableau-contacts');
	const corps = document.createElement('tbody');
	tableau.append(corps);
	const exportCsv = balise('a', 'data-export-contacts');
	exportCsv.setAttribute('href', '/api/contacts/export');
	return { document, statut, tableau, corps, exportCsv };
}

test('la page du Lieu affiche les coordonnées, la date et le texte accepté, puis rend le CSV accessible', async () => {
	const ui = page();
	const reponse = { statut: 200, ok: true, corps: { contacts: [CONTACT] } };
	await monterContactsDuLieu(ui.document, { consulter: async () => ({ ok: true }), appeler: async () => reponse });
	assert.equal(ui.tableau.hidden, false);
	assert.equal(ui.exportCsv.hidden, false);
	assert.equal(ui.exportCsv.getAttribute('href'), '/api/contacts/export');
	assert.match(ui.statut.textContent, /1 Contact/);
	assert.equal(ui.corps.querySelector('th').textContent, 'Camille <Exemple>');
	assert.equal(ui.corps.querySelector('td').textContent, '+33639980032');
	assert.equal(ui.corps.querySelector('time').getAttribute('datetime'), CONTACT.consentiLe);
	assert.equal(ui.corps.querySelector('details').querySelector('p').textContent, CONTACT.mention);

	reponse.corps.contacts = [];
	await monterContactsDuLieu(ui.document, { consulter: async () => ({ ok: true }), appeler: async () => reponse });
	assert.equal(ui.tableau.hidden, true);
	assert.equal(ui.corps.children.length, 0);
	assert.match(ui.statut.textContent, /Aucun Contact/);
});

test('une session expirée pendant le chargement renvoie à la connexion et masque les données', async () => {
	const ui = page();
	let redirections = 0;
	await monterContactsDuLieu(ui.document, {
		consulter: async () => ({ ok: true }),
		appeler: async () => ({ statut: 401 }),
		versConnexion: () => redirections++,
	});
	assert.equal(redirections, 1);
	assert.equal(ui.tableau.hidden, true);
	assert.equal(ui.exportCsv.hidden, true);
	assert.equal(retourApresConnexion('?retour=%2Fcontacts.html'), '/contacts.html');
});
