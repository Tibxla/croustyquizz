import test from 'node:test';
import assert from 'node:assert/strict';
import { teintePassage, formaterDuree, formaterAttente, etatInscription, tableMotifsKaraoke } from '../../src/main/resources/static/js/modes/karaoke/presentation.mjs';

test('les tickets suivent leur numéro et les durées tronquent les millisecondes', () => {
	assert.deepEqual([1, 12, 13, 14, 15].map(teintePassage), ['eau', 'orange', 'eau', 'bleu', 'rose']);
	assert.equal(formaterDuree(61999), '1:01');
	assert.equal(formaterDuree(9000), '0:09');
	assert.equal(formaterDuree(null), 'Durée inconnue');
});

test('l’attente est une estimation sans décompte, personnelle à zéro', () => {
	const attente = dureeMs => ({ etat: 'ESTIMEE', dureeMs });
	assert.equal(formaterAttente(attente(0), true), 'Tu passes ensuite.');
	assert.equal(formaterAttente(attente(0)), 'Ensuite');
	assert.equal(formaterAttente(attente(59999)), "Dans moins d'une minute environ");
	assert.equal(formaterAttente(attente(60000)), 'Dans environ 1 minute');
	assert.equal(formaterAttente(attente(60001)), 'Dans environ 2 minutes');
	assert.equal(formaterAttente({ etat: 'SUSPENDUE' }), 'Attente suspendue');
	assert.equal(formaterAttente({ etat: 'INCONNUE' }), 'Attente à confirmer');
});

test('le motif de la fiche passe avant celui du bloc, qui reste affiché seul, y compris un motif inconnu', () => {
	const fiche = { inscriptionPossible: true, motifIndisponibilite: null };
	assert.deepEqual(etatInscription(fiche, { peutInscrire: true }, false), { possible: false, motif: null });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: true }, true), { possible: true, motif: null });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: false, motifInscriptionRefusee: 'FUTUR' }, true), { possible: false, motif: 'FUTUR' });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: false, motifInscriptionRefusee: 'constructor' }, true), { possible: false, motif: 'constructor' });
	assert.equal(tableMotifsKaraoke.ANIMATEUR, 'Consultation uniquement');
	assert.deepEqual(etatInscription({ inscriptionPossible: false, motifIndisponibilite: 'ANIMATEUR' }, { peutInscrire: true }, true), { possible: false, motif: 'Consultation uniquement' });
});

test('les motifs de la Chanson priment sur le quota du bloc, dans l’ordre des gardes du contrat', () => {
	const quota = { peutInscrire: false, motifInscriptionRefusee: 'LIMITE_ATTEINTE' };
	for (const code of ['SANS_VOIX', 'PAROLES_NON_SYNCHRONISEES', 'DUREE_INCONNUE', 'PASSAGE_DEJA_INSCRIT']) {
		assert.deepEqual(etatInscription({ inscriptionPossible: false, motifIndisponibilite: code }, quota, true),
			{ possible: false, motif: tableMotifsKaraoke[code] }, code);
	}
	assert.deepEqual(etatInscription({ inscriptionPossible: true, motifIndisponibilite: null }, quota, true),
		{ possible: false, motif: 'Ta limite de Passages est atteinte.' });
});

test('les décomptes arrondissent au plafond et le décalage comporte un signe et un dixième', async () => {
	const { formaterTempsRestant, formaterDecalage } = await import('../../src/main/resources/static/js/modes/karaoke/presentation.mjs');
	assert.deepEqual([0, 26999, 59000, 59001, 61442].map(formaterTempsRestant), ['0 s', '27 s', '59 s', '1:00', '1:02']);
	assert.deepEqual([1500, -349, -50, 1599].map(formaterDecalage), ['+1,5 s', '−0,3 s', '0,0 s', '+1,6 s']);
});
