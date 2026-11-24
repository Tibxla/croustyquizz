import test from 'node:test';
import assert from 'node:assert/strict';
import { corpsVote, etatVoteManette, formaterNote, formaterNombreVotes, delaiVoteRestant }
	from '../../src/main/resources/static/js/modes/karaoke/vote.mjs';

const vote = { etat: 'OUVERT', interpretes: [{ joueurId: 'interprete' }], maNote: null };

test('l’état du vote affiche dans l’ordre la clôture, l’Interprète, la note, la pause puis la demande', () => {
	const pause = { joueurId: 'interprete', enPause: true };
	assert.equal(etatVoteManette({ ...vote, etat: 'CLOS', maNote: 4 }, pause), 'clos');
	assert.equal(etatVoteManette(vote, pause), 'interprete');
	assert.equal(etatVoteManette({ ...vote, maNote: 4 }, { ...pause, joueurId: 'public' }), 'enregistre');
	assert.equal(etatVoteManette(vote, { ...pause, joueurId: 'public' }), 'pause');
	assert.equal(etatVoteManette(vote, { joueurId: 'public', enPause: false }), 'a-voter');
});

test('le corps du vote reprend le compteur du Passage et exclut les données de la Manette', () => {
	assert.deepEqual(corpsVote({ envoiId: 'envoi', vote: { ...vote, numeroModification: 3 }, note: 4 }),
		{ envoiId: 'envoi', numeroModification: 3, note: 4 });
});

test('la Manette formate la note et la Console accorde le nombre de votes', () => {
	assert.equal(formaterNote(4), '4 sur 5');
	assert.equal(formaterNombreVotes(0), '0 vote');
	assert.equal(formaterNombreVotes(1), '1 vote');
	assert.equal(formaterNombreVotes(7), '7 votes');
});

test('le compte à rebours se cale sur le serveur et se fige pendant la pause', () => {
	const calculeLe = '2026-11-18T21:00:00Z';
	const maintenant = Date.parse('2026-11-18T21:00:03Z');
	const ouvert = { ...vote, calculeLe, delaiRestantMs: 30_000 };
	assert.equal(delaiVoteRestant(ouvert, maintenant, false), 27_000);
	assert.equal(delaiVoteRestant(ouvert, maintenant, true), 30_000);
	assert.equal(delaiVoteRestant(ouvert, maintenant + 60_000, false), 0);
});
