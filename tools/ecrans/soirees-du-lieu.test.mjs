import assert from 'node:assert/strict';
import { test } from 'node:test';
import { afficherSoireesDuLieu, destinationDeSoiree, monterSoireesDuLieu, regrouperSoirees }
	from '../../src/main/resources/static/js/soirees-du-lieu.mjs';
import { creerDocument } from './karaoke-dom-factice.mjs';

const soiree = (id, etat, titre = `Soirée ${id}`) => ({
	id, titre, format: 'SOLO', pilotage: 'MANUEL', etat, codeAcces: etat === 'PREPARATION' ? null : 'K7M2QX',
});

test('regroupe les Soirées en cours, à venir puis passées, dans l’ordre reçu du serveur', () => {
	const groupes = regrouperSoirees([
		soiree('a', 'PREPARATION'), soiree('b', 'TERMINEE'), soiree('c', 'OUVERTE'), soiree('d', 'EN_COURS'),
		soiree('e', 'TERMINEE'),
	]);
	assert.deepEqual(groupes.map(({ titre, soirees }) => [titre, soirees.map(({ id }) => id)]), [
		['En cours', ['d']],
		['À venir', ['a', 'c']],
		['Passées', ['b', 'e']],
	]);
	assert.deepEqual(regrouperSoirees([]), []);
});

test('mène à la préparation avant le lancement, puis à la Console', () => {
	assert.deepEqual(destinationDeSoiree(soiree('a b', 'PREPARATION')),
		{ adresse: '/preparer-soiree.html?soiree=a+b', action: 'Préparer' });
	assert.deepEqual(destinationDeSoiree(soiree('c', 'OUVERTE')),
		{ adresse: '/preparer-soiree.html?soiree=c', action: 'Préparer' });
	assert.deepEqual(destinationDeSoiree(soiree('d', 'EN_COURS')),
		{ adresse: '/console.html?soiree=d', action: 'Ouvrir la Console' });
	assert.deepEqual(destinationDeSoiree(soiree('e', 'TERMINEE')),
		{ adresse: '/console.html?soiree=e', action: 'Revoir dans la Console' });
});

test('affiche chaque Soirée avec son état en toutes lettres et un lien nommé, sans interpréter le titre', () => {
	const document = creerDocument();
	const zone = document.createElement('div');
	document.body.append(zone);
	afficherSoireesDuLieu(zone, [soiree('a', 'OUVERTE', '<img src=x> Quiz de Noël'), soiree('b', 'TERMINEE')]);

	const titres = zone.querySelectorAll('h2').map(titre => titre.textContent);
	assert.deepEqual(titres, ['À venir', 'Passées']);
	const liens = zone.querySelectorAll('a');
	assert.deepEqual(liens.map(lien => lien.getAttribute('href')),
		['/preparer-soiree.html?soiree=a', '/console.html?soiree=b']);
	assert.equal(liens[0].textContent, 'Préparer');
	assert.equal(liens[0].getAttribute('aria-label'), 'Préparer la Soirée « <img src=x> Quiz de Noël »');
	assert.match(zone.textContent, /<img src=x> Quiz de Noël/);
	assert.match(zone.textContent, /Ouverte · Solo · Pilotage manuel · Code K7M2QX/);
	assert.match(zone.textContent, /Terminée/);
	assert.equal(zone.querySelectorAll('img').length, 0);
});

test('annonce un Lieu sans Soirée', () => {
	const document = creerDocument();
	const zone = document.createElement('div');
	document.body.append(zone);
	afficherSoireesDuLieu(zone, []);
	assert.equal(zone.querySelectorAll('a').length, 0);
	assert.match(zone.textContent, /Aucune Soirée pour ce Lieu/);
});

test('le code d’accès n’apparaît que pour une Soirée qu’on peut encore rejoindre', () => {
	const document = creerDocument();
	const zone = document.createElement('div');
	document.body.append(zone);
	afficherSoireesDuLieu(zone, [soiree('a', 'EN_COURS'), soiree('b', 'OUVERTE'), soiree('c', 'TERMINEE')]);
	const details = zone.querySelectorAll('small').map(detail => detail.textContent);
	assert.deepEqual(details, [
		'En cours · Solo · Pilotage manuel · Code K7M2QX',
		'Ouverte · Solo · Pilotage manuel · Code K7M2QX',
		'Terminée · Solo · Pilotage manuel',
	]);
});

function creerPageSoirees(recherche = '') {
	const document = creerDocument();
	document.location = new URL(`/soirees.html${recherche}`, 'https://croustyquizz.test');
	document.defaultView = { history: { replaceState(_etat, _titre, adresse) { document.location = new URL(adresse); } } };
	const accueil = document.createElement('p');
	accueil.setAttribute('data-lieu-cree', '');
	accueil.textContent = 'Ton Lieu est créé.';
	accueil.hidden = true;
	const statut = document.createElement('p');
	statut.setAttribute('data-statut-soirees', '');
	const zone = document.createElement('div');
	zone.setAttribute('data-soirees', '');
	zone.setAttribute('aria-busy', 'true');
	document.body.append(accueil, statut, zone);
	return { document, accueil, statut, zone };
}

test('souhaite la bienvenue après l’inscription, une seule fois', async t => {
	t.mock.method(globalThis, 'fetch', async route => route === '/api/animateurs/session'
		? Response.json({ id: 'animateur', nom: 'Camille', lieuId: 'lieu' }) : Response.json({ soirees: [] }));
	const arrivee = creerPageSoirees('?inscription=1');
	await monterSoireesDuLieu(arrivee.document);
	assert.equal(arrivee.accueil.hidden, false);
	assert.equal(arrivee.document.location.search, '');

	const rechargement = creerPageSoirees(arrivee.document.location.search);
	await monterSoireesDuLieu(rechargement.document);
	assert.equal(rechargement.accueil.hidden, true);
	assert.match(rechargement.zone.textContent, /Aucune Soirée pour ce Lieu/);
});

for (const [cas, reponse] of [
	['une erreur du serveur', () => Response.json({ code: 'ERREUR_INATTENDUE' }, { status: 500 })],
	['une panne réseau', () => { throw new TypeError('fetch failed'); }],
]) {
	test(`après ${cas}, la liste n’est plus annoncée en chargement et l’échec s’affiche`, async t => {
		const { document, statut, zone } = creerPageSoirees();
		t.mock.method(globalThis, 'fetch', async route => route === '/api/animateurs/session'
			? Response.json({ id: 'animateur', nom: 'Camille', lieuId: 'lieu' }) : reponse());
		await monterSoireesDuLieu(document);
		assert.equal(zone.getAttribute('aria-busy'), 'false');
		assert.equal(statut.hidden, false);
		assert.match(statut.textContent, /Recharge la page/);
	});
}

test('une liste chargée n’est plus annoncée en chargement', async t => {
	const { document, statut, zone } = creerPageSoirees();
	t.mock.method(globalThis, 'fetch', async route => route === '/api/animateurs/session'
		? Response.json({ id: 'animateur', nom: 'Camille', lieuId: 'lieu' }) : Response.json({ soirees: [] }));
	await monterSoireesDuLieu(document);
	assert.equal(zone.getAttribute('aria-busy'), 'false');
	assert.equal(statut.hidden, true);
	assert.match(zone.textContent, /Aucune Soirée pour ce Lieu/);
});
