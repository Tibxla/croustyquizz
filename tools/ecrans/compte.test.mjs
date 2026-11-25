import assert from 'node:assert/strict';
import { test } from 'node:test';
import { retourApresConnexion, adresseConnexion } from '../../src/main/resources/static/js/compte.mjs';
import { validerChamp, erreurDeCompte } from '../../src/main/resources/static/js/formulaire-compte.mjs';

test('la connexion revient à la page protégée demandée', () => {
	const destination = '/console.html?soiree=00000001-0000-4000-8000-000000000000';
	assert.equal(retourApresConnexion(adresseConnexion(destination).split('?')[1]), destination);
	assert.equal(retourApresConnexion('?retour=%2Fpreparer-soiree.html'), '/preparer-soiree.html');
	assert.equal(retourApresConnexion('?retour=%2Fsoirees.html'), '/soirees.html');
});

test('sans retour, la connexion ouvre les Soirées du Lieu', () => {
	assert.equal(retourApresConnexion(''), '/soirees.html');
});

test('la connexion écarte les URL absolues et les chemins hors du parcours', () => {
	for (const retour of ['https://tiers.test', 'https://croustyquizz.invalid/console.html', '//tiers.test',
		'/\\tiers.test', '/api/animateurs/session', '/connexion.html']) {
		assert.equal(retourApresConnexion(new URLSearchParams({ retour })), '/soirees.html');
	}
});

test('le formulaire distingue les saisies de connexion et d’inscription', () => {
	assert.equal(validerChamp({ name: 'motDePasse', value: 'é'.repeat(12) }, true), '');
	assert.match(validerChamp({ name: 'motDePasse', value: 'é'.repeat(11) }, true), /12 à 128/);
	assert.equal(validerChamp({ name: 'motDePasse', value: 'a' }, false), '');
	assert.match(validerChamp({ name: 'email', value: 'invalide', validity: { typeMismatch: true } }, false), /valide/);
	assert.match(validerChamp({ name: 'ville', value: '   ' }, true), /ville/);
});

test('les refus du compte indiquent le champ à corriger', () => {
	assert.equal(erreurDeCompte('EMAIL_DEJA_UTILISE').champ, 'email');
	assert.equal(erreurDeCompte('IDENTIFIANTS_INVALIDES').champ, 'motDePasse');
	assert.equal(erreurDeCompte('CSRF_INVALIDE').champ, undefined);
});
