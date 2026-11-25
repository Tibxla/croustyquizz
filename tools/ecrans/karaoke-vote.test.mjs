import test from 'node:test';
import assert from 'node:assert/strict';
import { corpsVote, etatVoteManette, formaterNote, formaterMoyenne, formaterNombreVotes, delaiVoteRestant, votesVisiblesSalle }
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

test('les écrans formatent la note, la moyenne et le nombre de votes', () => {
	assert.equal(formaterNote(4), '4 sur 5');
	assert.equal(formaterNombreVotes(0), 'Aucun vote');
	assert.equal(formaterNombreVotes(1), '1 vote');
	assert.equal(formaterNombreVotes(7), '7 votes');
	assert.deepEqual([43, 40, 50, 10].map(formaterMoyenne), ['4,3 sur 5', '4,0 sur 5', '5,0 sur 5', '1,0 sur 5']);
});

const clotureLe = '2026-11-18T21:00:30Z';
const cloture = Date.parse(clotureLe);
const clos = { passageId: 'a', etat: 'CLOS', clotureLe, suivantDemarre: true };
const avecVotes = (votes, autres = {}) => ({ configurationInterface: { dureeAffichageMoyenneMs: 10_000 }, votes, ...autres });
const idsVisibles = (bloc, maintenant) => votesVisiblesSalle(bloc, maintenant).map(vote => vote.passageId);

test('un Passage suivant déjà démarré laisse le vote visible jusqu’à dix secondes après la clôture', () => {
	const ouvert = { ...clos, etat: 'OUVERT', clotureLe: null };
	assert.deepEqual(idsVisibles(avecVotes([ouvert]), cloture - 1), ['a']);
	assert.deepEqual(idsVisibles(avecVotes([clos]), cloture + 9999), ['a']);
	assert.deepEqual(idsVisibles(avecVotes([clos]), cloture + 10_000), []);
});

test('même après dix secondes, le résultat attend le démarrage du suivant', () => {
	const vote = { ...clos, suivantDemarre: false };
	assert.deepEqual(idsVisibles(avecVotes([vote], { courant: { etat: 'APPELE' } }), cloture + 20_000), ['a']);
	assert.deepEqual(idsVisibles(avecVotes([vote], { courant: null }), cloture + 80_000), ['a']);
	assert.deepEqual(idsVisibles(avecVotes([clos]), cloture + 80_000), []);
});

test('la recapture après un saut puis un nouvel appel respecte le démarrage enregistré', () => {
	const autres = { courant: { passageId: 'c', etat: 'APPELE' } };
	assert.deepEqual(idsVisibles(avecVotes([clos], autres), cloture + 15_000), []);
	assert.deepEqual(idsVisibles(avecVotes([{ ...clos, suivantDemarre: false }], autres), cloture + 15_000), ['a']);
});

test('la salle garde au plus deux votes dans l’ordre reçu et retire ceux que la vue a remplacés', () => {
	const b = { passageId: 'b', etat: 'OUVERT' };
	const c = { passageId: 'c', etat: 'OUVERT' };
	assert.deepEqual(idsVisibles(avecVotes([c, b, clos]), cloture + 100), ['c', 'b']);
	assert.deepEqual(idsVisibles(avecVotes([b, clos]), cloture + 200), ['b', 'a']);
	const bClos = { ...b, etat: 'CLOS', clotureLe: '2026-11-18T21:00:33Z', suivantDemarre: false };
	assert.deepEqual(idsVisibles(avecVotes([bClos]), cloture + 3000), ['b']);
});

test('les dix secondes du résultat s’écoulent pendant une pause', () => {
	const b = { passageId: 'b', etat: 'OUVERT' };
	const bloc = avecVotes([b, clos], { etat: 'EN_PAUSE' });
	assert.deepEqual(idsVisibles(bloc, cloture + 9999), ['b', 'a']);
	assert.deepEqual(idsVisibles(bloc, cloture + 10_000), ['b']);
});

test('le compte à rebours se cale sur le serveur et se fige pendant la pause', () => {
	const calculeLe = '2026-11-18T21:00:00Z';
	const maintenant = Date.parse('2026-11-18T21:00:03Z');
	const ouvert = { ...vote, calculeLe, delaiRestantMs: 30_000 };
	assert.equal(delaiVoteRestant(ouvert, maintenant, false), 27_000);
	assert.equal(delaiVoteRestant(ouvert, maintenant, true), 30_000);
	assert.equal(delaiVoteRestant(ouvert, maintenant + 60_000, false), 0);
});
