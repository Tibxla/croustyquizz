import assert from 'node:assert/strict';
import test from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { lireCleInvitation, monterInvitationConsole, monterInvitationInvite } from '../../src/main/resources/static/js/invitation.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const CLE = 'abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG';
const ROUTE = '/api/animateurs/invitations';
const SESSION = '/api/animateurs/session';
const CLE_ONGLET = 'croustyquizz.invitation';

function creerOnglet() {
	const rangees = new Map();
	return { setItem: (k, v) => rangees.set(k, String(v)), getItem: k => rangees.get(k) ?? null, removeItem: k => rangees.delete(k) };
}

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
		if (route === SESSION) return json({ code: 'AUTHENTIFICATION_REQUISE' }, 401);
		if (route.endsWith('/consultation')) return json({ nomLieu: 'Le Balcon', ville: 'Nanterre', email: 'invite@exemple.test' });
		if (route === '/api/csrf') return json({ token: 'csrf-fictif' });
		return json({ id: 'animateur-fictif' }, 201);
	});
	const destinations = [];
	const onglet = creerOnglet();
	await monterInvitationInvite(racine, { hash: `#cle=${CLE}`, replace: url => destinations.push(url) }, onglet);
	assert.equal(onglet.getItem(CLE_ONGLET), CLE);
	assert.match(titre.textContent, /Le Balcon/);
	assert.equal(email.value, 'invite@exemple.test');
	assert.equal(formulaire.hidden, false);
	formulaire.dispatchEvent({ type: 'submit' });
	assert.equal(erreur.hidden, false);
	assert.equal(nom.getAttribute('aria-invalid'), 'true');
	assert.ok(document.activeElement === nom);
	assert.equal(appels.length, 2);

	nom.value = 'Émilie';
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.deepEqual(appels.map(appel => appel.route), [SESSION, `${ROUTE}/consultation`, '/api/csrf', `${ROUTE}/acceptation`]);
	assert.deepEqual(JSON.parse(appels[1].options.body), { cle: CLE });
	assert.deepEqual(JSON.parse(appels[3].options.body), { cle: CLE, nom: 'Émilie', motDePasse: 'Un mot de passe de test' });
	assert.deepEqual(destinations, ['/preparer-soiree.html']);
	assert.equal(onglet.getItem(CLE_ONGLET), null);
});

test('un lien expiré laisse le formulaire masqué', async t => {
	const { document, ajouter } = creerInterface();
	const racine = ajouter(document.body, 'section');
	const statut = ajouter(racine, 'p', { role: 'status' });
	const formulaire = ajouter(racine, 'form');
	formulaire.hidden = true;
	const appel = t.mock.method(globalThis, 'fetch', async route => route === SESSION
		? json({ code: 'AUTHENTIFICATION_REQUISE' }, 401) : json({ code: 'INVITATION_INVALIDE' }, 404));
	const onglet = creerOnglet();

	await monterInvitationInvite(racine, { hash: `#cle=${CLE}` }, onglet);
	assert.equal(appel.mock.callCount(), 2);
	assert.equal(formulaire.hidden, true);
	assert.match(statut.textContent, /expiré ou déjà utilisé/);
	assert.equal(onglet.getItem(CLE_ONGLET), null, 'rouvrir la page ne renvoie plus la clé morte');
});

function creerPageInvite() {
	const interfaceFactice = creerInterface();
	const { document, ajouter, champ } = interfaceFactice;
	const racine = ajouter(document.body, 'section');
	ajouter(racine, 'h1');
	const statut = ajouter(racine, 'p', { role: 'status' });
	const deconnexion = ajouter(racine, 'button', { 'data-deconnexion': '' });
	deconnexion.hidden = true;
	const formulaire = ajouter(racine, 'form');
	formulaire.hidden = true;
	champ(formulaire, 'email', '');
	const { entree: nom } = champ(formulaire, 'nom', 'Émilie');
	const { entree: motDePasse } = champ(formulaire, 'motDePasse', 'Un mot de passe de test');
	const bouton = ajouter(formulaire, 'button', { type: 'submit' });
	ajouter(formulaire, 'p', { role: 'alert' });
	return { ...interfaceFactice, racine, statut, deconnexion, formulaire, nom, motDePasse, bouton };
}

function repondre(acceptation, { session = json({ code: 'AUTHENTIFICATION_REQUISE' }, 401) } = {}) {
	return async (route, options) => {
		if (route === SESSION) return (options?.method ?? 'GET') === 'GET' ? session : new Response(null, { status: 204 });
		if (route.endsWith('/consultation')) return json({ nomLieu: 'Le Balcon', ville: 'Nanterre', email: 'invite@exemple.test' });
		if (route === '/api/csrf') return json({ token: 'csrf-fictif' });
		return acceptation;
	};
}

test('une clé consommée entre la consultation et l’acceptation est oubliée de l’onglet', async t => {
	const { racine, statut, formulaire, motDePasse, document } = creerPageInvite();
	t.mock.method(globalThis, 'fetch', repondre(json({ code: 'INVITATION_INVALIDE' }, 404)));
	const onglet = creerOnglet();
	await monterInvitationInvite(racine, { hash: `#cle=${CLE}` }, onglet);
	assert.equal(onglet.getItem(CLE_ONGLET), CLE);
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.equal(formulaire.hidden, true);
	assert.equal(motDePasse.value, '');
	assert.match(statut.textContent, /expiré ou déjà utilisé/);
	assert.ok(document.activeElement === statut);
	assert.equal(onglet.getItem(CLE_ONGLET), null);
});

test('un e-mail inscrit entre-temps masque le formulaire et propose la connexion', async t => {
	const { racine, statut, formulaire, motDePasse, document } = creerPageInvite();
	t.mock.method(globalThis, 'fetch', repondre(json({ code: 'EMAIL_DEJA_UTILISE' }, 409)));
	const onglet = creerOnglet();
	await monterInvitationInvite(racine, { hash: `#cle=${CLE}` }, onglet);
	formulaire.dispatchEvent({ type: 'submit' });
	await setImmediate();
	assert.equal(formulaire.hidden, true);
	assert.equal(motDePasse.value, '');
	assert.equal(statut.hidden, false);
	assert.match(statut.textContent, /Un compte existe déjà pour invite@exemple\.test/);
	const lien = statut.querySelector('a');
	assert.equal(lien.href, '/connexion.html');
	assert.ok(document.activeElement === statut);
	assert.equal(onglet.getItem(CLE_ONGLET), null);
});

test('un Animateur déjà connecté se déconnecte avant de voir l’invitation', async t => {
	const { racine, statut, deconnexion, formulaire } = creerPageInvite();
	const appels = [];
	const repondeur = repondre(json({ id: 'animateur-fictif' }, 201),
		{ session: json({ id: 'animateur-connecte', nom: 'Camille', lieuId: 'lieu-fictif' }) });
	t.mock.method(globalThis, 'fetch', async (route, options) => {
		appels.push(`${options?.method ?? 'GET'} ${route}`);
		return repondeur(route, options);
	});
	const onglet = creerOnglet();
	await monterInvitationInvite(racine, { hash: `#cle=${CLE}` }, onglet);
	assert.match(statut.textContent, /connecté au compte de Camille/);
	assert.equal(deconnexion.hidden, false);
	assert.equal(formulaire.hidden, true);
	assert.deepEqual(appels, [`GET ${SESSION}`]);
	assert.equal(onglet.getItem(CLE_ONGLET), CLE);

	deconnexion.click();
	await setImmediate();
	await setImmediate();
	assert.deepEqual(appels, [`GET ${SESSION}`, 'GET /api/csrf', `DELETE ${SESSION}`, `POST ${ROUTE}/consultation`]);
	assert.equal(deconnexion.hidden, true);
	assert.equal(formulaire.hidden, false);
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
