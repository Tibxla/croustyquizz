import assert from 'node:assert/strict';
import { test } from 'node:test';
import { afficherSoireesDuLieu, destinationDeSoiree, regrouperSoirees }
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
