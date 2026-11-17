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

test('le motif du bloc prime sur la fiche, y compris un motif inconnu', () => {
	const fiche = { inscriptionPossible: true, motifIndisponibilite: null };
	assert.deepEqual(etatInscription(fiche, { peutInscrire: true }, false), { possible: false, motif: null });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: true }, true), { possible: true, motif: null });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: false, motifInscriptionRefusee: 'FUTUR' }, true), { possible: false, motif: 'FUTUR' });
	assert.deepEqual(etatInscription(fiche, { peutInscrire: false, motifInscriptionRefusee: 'constructor' }, true), { possible: false, motif: 'constructor' });
	assert.equal(tableMotifsKaraoke.ANIMATEUR, 'Consultation uniquement');
	assert.deepEqual(etatInscription({ inscriptionPossible: false, motifIndisponibilite: 'ANIMATEUR' }, { peutInscrire: true }, true), { possible: false, motif: 'Consultation uniquement' });
});
