// Reçus des envois des Modes à Passages (#279) : ils disent ce qui est enregistré, ou rien quand la vue change d'elle-même.
import test from 'node:test';
import assert from 'node:assert/strict';
import { texteEnvoi } from '../../src/main/resources/static/js/modes/karaoke/commun.mjs';

const accepte = commande => texteEnvoi({ etat: 'accepte', commande });

test('un envoi accepté dit ce qui est enregistré, sans parler du serveur', () => {
	assert.equal(accepte({ type: 'VOTER', chemin: '/p/vote' }), 'Note envoyée.');
	assert.equal(accepte({ type: 'VOTER', chemin: '/p/meilleur-interprete' }), 'Choix envoyé.');
	assert.equal(accepte({ type: 'REPONDRE_INVITATION', corps: { reponse: 'ACCEPTER' } }), 'C’est noté : tu es dans ce Passage.');
	assert.equal(accepte({ type: 'REPONDRE_INVITATION', corps: { reponse: 'REFUSER' } }), 'Invitation refusée.');
	assert.equal(accepte({ acteurType: 'ANIMATEUR', chemin: '/m/file-attente/limite', corps: {} }), 'Limite enregistrée.');
	assert.equal(accepte({ type: 'CORRIGER_CALAGE' }), 'Paroles recalées.');
	assert.equal(accepte({ type: 'MARQUER_DEBUT' }), 'Paroles recalées.');
	assert.equal(accepte({ acteurType: 'JOUEUR', chansonId: 'c', chemin: '/m/passages' }), 'C’est noté.');
	assert.equal(accepte({ type: 'INSCRIRE_FREESTYLE', acteurType: 'JOUEUR', chemin: '/m/passages' }), 'C’est noté.');
});

test('quand la vue change d’elle-même, le reçu se tait', () => {
	for (const type of ['PILOTER', 'APPELER', 'CONFIRMER_PRESENCE', 'REPRENDRE_EN_PAROLES_SEULES']) {
		assert.equal(accepte({ type, chemin: '/x' }), '', type);
	}
	assert.equal(accepte({ acteurType: 'JOUEUR', passageId: 'p', chemin: '/m/passages/p/desinscription' }), '');
	assert.equal(accepte({ acteurType: 'ANIMATEUR', chemin: '/m/passages/p/placement' }), '');
	assert.equal(accepte({ acteurType: 'ANIMATEUR', chemin: '/m/file-attente/priorite' }), '');
	assert.equal(accepte({ acteurType: 'ANIMATEUR', chemin: '/m/passages/p/retrait' }), '');
});

test('les autres états gardent leur libellé', () => {
	assert.equal(texteEnvoi(undefined), '');
	assert.equal(texteEnvoi({ etat: 'en-cours', commande: {} }), 'Envoi en cours…');
	assert.equal(texteEnvoi({ etat: 'inconnu', commande: {} }), 'Confirmation à vérifier.');
	assert.equal(texteEnvoi({ etat: 'refuse', detail: 'Non.', commande: {} }), 'Non.');
});
