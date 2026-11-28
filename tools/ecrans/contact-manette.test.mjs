import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { monterContactManette } from '../../src/main/resources/static/js/contact-manette.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const MENTION = 'Le Bal fictif utilisera tes coordonnées pour ses offres par SMS.';
const DECLARATION = 'J’accepte de recevoir les offres de ce Lieu par SMS.';
const SOIREE = '32000000-0000-4000-8000-000000000001';
const LIEN = '/desinscription.html#cle=lien-fictif';

function interfaceDeContact(repondre) {
	const document = creerDocument();
	const ajouter = (parent, balise, ...attributs) => {
		const element = document.createElement(balise);
		for (const attribut of attributs) element.setAttribute(attribut, '');
		parent.append(element);
		return element;
	};
	const zone = ajouter(document.body, 'section');
	const invitation = ajouter(zone, 'p', 'data-contact-fin');
	const ouvrir = ajouter(zone, 'button');
	const dialogue = ajouter(zone, 'dialog');
	const formulaire = ajouter(dialogue, 'form');
	const mention = ajouter(formulaire, 'p', 'data-mention-contact');
	ajouter(formulaire, 'span', 'data-declaration-contact');
	const fermer = ajouter(dialogue, 'button', 'data-fermer-contact');
	const reessayer = ajouter(dialogue, 'button', 'data-reessayer-contact');
	const statut = ajouter(dialogue, 'p', 'data-statut-contact');
	const lien = ajouter(dialogue, 'a', 'data-lien-contact');
	const consentement = ajouter(formulaire, 'input');
	consentement.setAttribute('name', 'consentement');
	const envoyer = ajouter(formulaire, 'button', 'data-envoyer-contact');
	for (const [nom, valeur] of [['prenom', 'Camille'], ['nom', 'Exemple'], ['numero', '06 39 98 00 32']]) {
		const champ = ajouter(formulaire, 'input');
		champ.setAttribute('name', nom);
		champ.value = valeur;
	}
	formulaire.reset = () => { consentement.checked = false; };
	consentement.checked = false;
	const appels = [];
	const module = monterContactManette(zone, {
		soireeId: SOIREE,
		envoyer: async (methode, chemin, corps) => {
			appels.push({ methode, chemin, corps });
			return repondre(methode, corps);
		},
	});
	return { zone, invitation, ouvrir, dialogue, formulaire, mention, fermer, statut, consentement, envoyer, lien, appels, module, reessayer };
}

test('le formulaire attend la mention et laisse la case décochée avant un choix explicite', async () => {
	const ui = interfaceDeContact(methode => methode === 'GET'
		? { statut: 200, corps: { mention: MENTION, declaration: DECLARATION } }
		: { statut: 200, corps: { consentiLe: '2026-11-28T18:00:00Z', lienDeDesinscription: LIEN } });
	ui.module.afficher({ soiree: { etat: 'EN_COURS' } });
	assert.equal(ui.invitation.hidden, true);
	assert.equal(ui.dialogue.open, false);
	ui.module.afficher({ soiree: { etat: 'TERMINEE' } });
	assert.equal(ui.invitation.hidden, false);
	assert.equal(ui.dialogue.open, false, 'la fin de Soirée ne doit pas ouvrir le formulaire');

	ui.ouvrir.click();
	await setImmediate();
	assert.equal(ui.mention.textContent, MENTION);
	assert.equal(ui.consentement.checked, false);
	ui.formulaire.dispatchEvent({ type: 'submit' });
	assert.deepEqual(ui.appels.map(appel => appel.methode), ['GET']);

	ui.consentement.checked = true;
	ui.formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.deepEqual(ui.appels.at(-1), {
		methode: 'POST', chemin: `/api/soirees/${SOIREE}/contact`,
		corps: { prenom: 'Camille', nom: 'Exemple', numero: '06 39 98 00 32', mention: MENTION, declaration: DECLARATION, consentement: true },
	});
	assert.equal(ui.formulaire.hidden, true);
	assert.equal(ui.lien.hidden, false);
	assert.equal(ui.lien.getAttribute('href'), LIEN);
	assert.match(ui.statut.textContent, /Consentement est enregistré/);
	ui.fermer.click();
	assert.equal(ui.dialogue.open, false);
});

test('une nouvelle mention doit être lue et acceptée avant de renvoyer le formulaire', async () => {
	let lectures = 0;
	const ui = interfaceDeContact(methode => {
		if (methode === 'GET') return { statut: 200, corps: { mention: lectures++ ? 'Nouvelle mention.' : MENTION, declaration: DECLARATION } };
		return { statut: 409, corps: { code: 'MENTION_MODIFIEE' } };
	});
	ui.ouvrir.click();
	await setImmediate();
	ui.consentement.checked = true;
	ui.formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.equal(ui.mention.textContent, 'Nouvelle mention.');
	assert.equal(ui.consentement.checked, false);
	assert.match(ui.statut.textContent, /mention a changé/);
	assert.deepEqual(ui.appels.map(appel => appel.methode), ['GET', 'POST', 'GET']);
});
