import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { lireCleInvitation, monterInvitationConsole, monterInvitationInvite } from '../../src/main/resources/static/js/invitation.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const CLE = 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG';
const ROUTE = '/api/animateurs/invitations';

function json(corps, status = 200) {
	return Response.json(corps, { status });
}

function creerInterface() {
	const document = creerDocument();
	document.getElementById = id => document.querySelector(`#${id}`);
	function ajouter(parent, tag, attributs = {}) {
		const element = document.createElement(tag);
		for (const [nom, valeur] of Object.entries(attributs)) element.setAttribute(nom, valeur);
		parent.append(element);
		return element;
	}
	function champ(formulaire, nom, valeur) {
		const entree = ajouter(formulaire, 'input', { id: nom, name: nom });
		entree.name = nom;
		entree.value = valeur;
		entree.validity = { typeMismatch: false };
		const erreur = ajouter(formulaire, 'p', { id: `${nom}-erreur` });
		erreur.hidden = true;
		return { entree, erreur };
	}
	return { document, ajouter, champ };
}

test('la Console crée puis copie un lien qui porte la clé dans son fragment', async t => {
	const { document, ajouter, champ } = creerInterface();
	const bouton = ajouter(document.body, 'button');
	const dialogue = ajouter(document.body, 'dialog');
	const formulaire = ajouter(dialogue, 'form');
	champ(formulaire, 'email', 'invite@exemple.test');
	ajouter(formulaire, 'button', { type: 'submit' });
	const resultat = ajouter(dialogue, 'div', { 'data-lien-invitation': '' });
	resultat.hidden = true;
	const lien = ajouter(resultat, 'input');
	const copier = ajouter(resultat, 'button');
	const statut = ajouter(dialogue, 'p', { role: 'status' });
	ajouter(dialogue, 'button', { 'data-fermer-invitation': '' });
	const appels = [];
	t.mock.method(globalThis, 'fetch', async (route, options) => {
		appels.push({ route, options });
		return route === '/api/csrf' ? json({ token: 'csrf-fictif' }) : json({ cle: CLE }, 201);
	});
	const liensCopies = [];
	monterInvitationConsole(bouton, dialogue, 'https://croustyquizz.test', { writeText: texte => liensCopies.push(texte) });
	bouton.click();
	assert.equal(dialogue.open, true);
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.deepEqual(appels.map(appel => appel.route), ['/api/csrf', ROUTE]);
	assert.deepEqual(JSON.parse(appels[1].options.body), { email: 'invite@exemple.test' });
	assert.equal(appels[1].options.headers['X-CSRF-TOKEN'], 'csrf-fictif');
	assert.equal(resultat.hidden, false);
	assert.equal(lien.value, `https://croustyquizz.test/invitation.html#cle=${CLE}`);
	copier.click();
	await setImmediate();
	assert.deepEqual(liensCopies, [lien.value]);
	assert.match(statut.textContent, /Lien copié/);
	dialogue.close();
	assert.equal(lien.value, '');
	assert.ok(document.activeElement === bouton);
});

test('la Console explique le plafond des invitations en cours du Lieu', async t => {
	const { document, ajouter, champ } = creerInterface();
	const bouton = ajouter(document.body, 'button');
	const dialogue = ajouter(document.body, 'dialog');
	const formulaire = ajouter(dialogue, 'form');
	champ(formulaire, 'email', 'vingt-et-unieme@exemple.test');
	ajouter(formulaire, 'button', { type: 'submit' });
	const resultat = ajouter(dialogue, 'div', { 'data-lien-invitation': '' });
	resultat.hidden = true;
	ajouter(resultat, 'input');
	ajouter(resultat, 'button');
	const statut = ajouter(dialogue, 'p', { role: 'status' });
	ajouter(dialogue, 'button', { 'data-fermer-invitation': '' });
	t.mock.method(globalThis, 'fetch', async route => route === '/api/csrf'
		? json({ token: 'csrf-fictif' }) : json({ code: 'LIMITE_ATTEINTE', detail: 'Plafond.' }, 409));
	monterInvitationConsole(bouton, dialogue, 'https://croustyquizz.test', { writeText() {} });
	bouton.click();
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.match(statut.textContent, /vingt invitations en cours/);
	assert.equal(resultat.hidden, true);
});

test('l’invité voit le Lieu puis crée son compte avec la clé dans les corps JSON', async t => {
	const { document, ajouter, champ } = creerInterface();
	const racine = ajouter(document.body, 'section');
	const titre = ajouter(racine, 'h1');
	ajouter(racine, 'p', { role: 'status' });
	const formulaire = ajouter(racine, 'form');
	formulaire.hidden = true;
	const { entree: email } = champ(formulaire, 'email', '');
	const { entree: nom, erreur } = champ(formulaire, 'nom', '');
	champ(formulaire, 'motDePasse', 'Un mot de passe de test');
	ajouter(formulaire, 'button', { type: 'submit' });
	ajouter(formulaire, 'p', { role: 'alert' });
	const appels = [];
	t.mock.method(globalThis, 'fetch', async (route, options) => {
		appels.push({ route, options });
		if (route.endsWith('/consultation')) return json({ nomLieu: 'Le Balcon', ville: 'Nanterre', email: 'invite@exemple.test' });
		if (route === '/api/csrf') return json({ token: 'csrf-fictif' });
		return json({ id: 'animateur-fictif' }, 201);
	});
	const destinations = [];
	await monterInvitationInvite(racine, { hash: `#cle=${CLE}`, replace: url => destinations.push(url) });
	assert.match(titre.textContent, /Le Balcon/);
	assert.equal(email.value, 'invite@exemple.test');
	assert.equal(formulaire.hidden, false);
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(erreur.hidden, false);
	assert.equal(nom.getAttribute('aria-invalid'), 'true');
	assert.ok(document.activeElement === nom);
	assert.equal(appels.length, 1);

	nom.value = 'Émilie';
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.deepEqual(appels.map(appel => appel.route), [`${ROUTE}/consultation`, '/api/csrf', `${ROUTE}/acceptation`]);
	assert.deepEqual(JSON.parse(appels[0].options.body), { cle: CLE });
	assert.deepEqual(JSON.parse(appels[2].options.body), { cle: CLE, nom: 'Émilie', motDePasse: 'Un mot de passe de test' });
	assert.deepEqual(destinations, ['/preparer-soiree.html']);
});

test('un lien expiré laisse le formulaire masqué', async t => {
	const { document, ajouter } = creerInterface();
	const racine = ajouter(document.body, 'section');
	const statut = ajouter(racine, 'p', { role: 'status' });
	const formulaire = ajouter(racine, 'form');
	formulaire.hidden = true;
	const appel = t.mock.method(globalThis, 'fetch', async () => json({ code: 'INVITATION_INVALIDE' }, 404));

	await monterInvitationInvite(racine, { hash: `#cle=${CLE}` });
	assert.equal(appel.mock.callCount(), 1);
	assert.equal(formulaire.hidden, true);
	assert.match(statut.textContent, /expiré ou déjà utilisé/);
});

test('la clé d’invitation quitte l’adresse et reste dans l’onglet', () => {
	const rangees = new Map();
	const memoire = { setItem: (k, v) => rangees.set(k, v), getItem: k => rangees.get(k) ?? null };
	const remplacements = [];
	const historique = { replaceState: (etat, titre, adresse) => remplacements.push(adresse) };
	const cle = 'A'.repeat(43);
	const navigation = { hash: `#cle=${cle}`, pathname: '/invitation.html', search: '' };
	assert.equal(lireCleInvitation(navigation, memoire, historique), cle);
	assert.deepEqual(remplacements, ['/invitation.html']);
	assert.equal(lireCleInvitation({ hash: '', pathname: '/invitation.html', search: '' }, memoire, historique), cle);
});
