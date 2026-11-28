import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';

import { retourApresConnexion } from '../../src/main/resources/static/js/compte.mjs';
import {
	compterSegments, decrireBrouillon, decrireEnvoi, libelleEtat, libelleModeEnvoi, remplacerCaracteresSpeciaux,
} from '../../src/main/resources/static/js/campagnes-sms.mjs';

test('le compteur applique les limites du GSM et de l’Unicode', () => {
	for (const [unite, repetitions, attendu] of [
		['é', 160, 1], ['é', 161, 2], ['€', 153, 3], ['ç', 71, 2], ['🎤', 67, 3],
	]) {
		assert.equal(compterSegments(unite.repeat(repetitions)), attendu);
	}
});

test('le brouillon compte les caractères saisis et ajoute le lien d’exemple au compte des segments', () => {
	assert.deepEqual(decrireBrouillon('  '), { caracteres: 2, segments: 0, valide: false, unicode: false });
	assert.deepEqual(decrireBrouillon('a'.repeat(160)), { caracteres: 160, segments: 2, valide: true, unicode: false });
	assert.deepEqual(decrireBrouillon('🎤'.repeat(201)), { caracteres: 201, segments: 7, valide: false, unicode: true });
});

test('une apostrophe typographique fait passer le brouillon de 98 caractères à trois segments avec le lien STOP', () => {
	const texte = 'C’est jeudi !'.padEnd(98, 'a');
	assert.deepEqual(decrireBrouillon(texte), { caracteres: 98, segments: 3, valide: true, unicode: true });
	assert.deepEqual(decrireBrouillon(remplacerCaracteresSpeciaux(texte)),
		{ caracteres: 98, segments: 1, valide: true, unicode: false });
});

test('le remplacement adapte la ponctuation et conserve les autres caractères', () => {
	const texte = '«\u202fÉté…\u00a0» — “Quiz” – ç 🎤 €';
	const remplace = remplacerCaracteresSpeciaux(texte);
	assert.equal(remplace, '" Été... " - "Quiz" - ç 🎤 €');
	assert.equal(decrireBrouillon(remplace).unicode, true);
	assert.equal(decrireBrouillon("C'est jeudi ! {Quiz} à 5 €").unicode, false);
});

test('la confirmation reprend le nombre de destinataires, le coût et le créneau de Paris', () => {
	const apercu = {
		destinataires: 3, coutEstimeCentimes: 15, immediat: false, envoiPrevuLe: '2026-11-30T07:00:00Z',
	};
	const programme = decrireEnvoi(apercu);
	assert.equal(programme.action, 'Programmer');
	assert.match(programme.confirmation, /3 destinataires/);
	assert.match(programme.confirmation, /0,15\s€/);
	assert.match(programme.confirmation, /lundi 30 novembre.*08:00/);
	assert.equal(decrireEnvoi({ ...apercu, immediat: true }).action, 'Envoyer');
	assert.equal(decrireEnvoi({ ...apercu, destinataires: 0 }).possible, false);
});

test('chaque état de Campagne est lisible en français', () => {
	assert.deepEqual(['PROGRAMMEE', 'EN_COURS', 'TERMINEE'].map(libelleEtat),
		['Programmée', 'En cours', 'Terminée']);
});

test('le bandeau précise le contenu reçu en compte d’essai Twilio', () => {
	assert.equal(libelleModeEnvoi('SIMULE'), 'Les envois sont simulés.');
	assert.equal(libelleModeEnvoi('TWILIO'), 'Envoi réel par Twilio.');
	assert.equal(libelleModeEnvoi('TWILIO_ESSAI'),
		'Compte d\'essai Twilio : le SMS reçu est un message type de Twilio, pas ton texte, et seuls les numéros vérifiés le reçoivent.');
});

test('la page est accessible des Soirées du Lieu et après connexion', async () => {
	const lire = nom => readFile(new URL(`../../src/main/resources/static/${nom}`, import.meta.url), 'utf8');
	assert.match(await lire('soirees.html'), /href="\/campagnes-sms.html"[^>]*>Campagnes SMS/);
	assert.match(await lire('campagnes-sms.html'), /Consentement depuis leur Manette/);
	assert.equal(retourApresConnexion('?retour=%2Fcampagnes-sms.html'), '/campagnes-sms.html');
});
