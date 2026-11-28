import test from 'node:test';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { creerUuid } from '../../src/main/resources/static/js/uuid.mjs';

const formeV4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

test('randomUUID est employé quand le navigateur le fournit', () => {
	assert.equal(creerUuid({ randomUUID: () => 'natif', getRandomValues: () => assert.fail('repli inutile') }), 'natif');
});

test('sans randomUUID (http:// hors contexte sécurisé), le repli pose la version 4 et la variante RFC', () => {
	assert.equal(creerUuid({ getRandomValues: octets => octets.fill(255) }), 'ffffffff-ffff-4fff-bfff-ffffffffffff');
	assert.equal(creerUuid({ getRandomValues: octets => octets.fill(0) }), '00000000-0000-4000-8000-000000000000');
});

test('le repli sur de vrais octets aléatoires rend des UUID v4 distincts', () => {
	const sansRandomUuid = { getRandomValues: octets => webcrypto.getRandomValues(octets) };
	const identifiants = new Set(Array.from({ length: 200 }, () => creerUuid(sansRandomUuid)));
	assert.equal(identifiants.size, 200);
	for (const identifiant of identifiants) assert.match(identifiant, formeV4);
});

test('sans argument, creerUuid emploie le crypto global', () => {
	assert.match(creerUuid(), formeV4);
});
