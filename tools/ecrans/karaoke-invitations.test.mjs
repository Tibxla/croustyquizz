import test from 'node:test';
import assert from 'node:assert/strict';
import { creerBanc, terminerMicrotaches, chanson, passage, vue } from './karaoke-dom-factice.mjs';
import { monter as manette } from '../../src/main/resources/static/js/modes/karaoke/manette.mjs';
import { monter as consoleKaraoke } from '../../src/main/resources/static/js/modes/karaoke/console.mjs';
import { estInscripteur, joindrePseudos, lignesInvitations, texteDesinscription, texteInvitation }
	from '../../src/main/resources/static/js/modes/karaoke/invitations.mjs';
import { invitesPossibles } from '../../src/main/resources/static/js/modes/karaoke/commun.mjs';

// Passages à plusieurs (#135, #138, #139). Le banc joue le Joueur « j » (Jo).
const capture = { source: 'capture', type: 'capture' }, evenement = { source: 'evenement', type: 'file-attente-modifiee' };
const bouton = (zone, texte) => zone.querySelectorAll('button').find(b => b.textContent === texte);
const jo = { joueurId: 'j', pseudo: 'Jo' }, basile = { joueurId: 'b', pseudo: 'Basile' }, celia = { joueurId: 'c', pseudo: 'Célia' };
const duo = (interpretes = [jo, basile]) => ({ ...passage('duo', 21), interpretes });
const recue = { passage: { passageId: 'invite', numeroModification: 0 }, numero: 22, chanson: { ...chanson, chansonId: 'invite' },
	inscrivant: basile, interpretes: [basile, celia] };

test('règles pures : inscripteur, pseudos joints, états et confirmations nommant les autres Interprètes', () => {
	assert.equal(estInscripteur(duo(), 'j'), true);
	assert.equal(estInscripteur(duo([basile, jo]), 'j'), false);
	assert.equal(joindrePseudos(['A']), 'A');
	assert.equal(joindrePseudos(['A', 'B', 'C']), 'A, B et C');
	assert.deepEqual(lignesInvitations([{ passageId: 'duo', invite: basile, etat: 'ACCEPTEE' },
		{ passageId: 'duo', invite: celia, etat: 'EN_ATTENTE' }, { passageId: 'autre', invite: celia, etat: 'REFUSEE' }], 'duo'),
	['Basile : a accepté', 'Célia : pas encore répondu']);
	assert.deepEqual(lignesInvitations(undefined, 'duo'), []);
	assert.equal(texteDesinscription(duo([jo]), 'j'), 'Retirer ton Passage 21 · Titre de démonstration ?');
	assert.equal(texteDesinscription(duo(), 'j'), 'Retirer ton Passage 21 · Titre de démonstration ? Basile perd aussi ce Passage.');
	assert.equal(texteDesinscription(duo([jo, basile, celia]), 'j'),
		'Retirer ton Passage 21 · Titre de démonstration ? Basile et Célia perdent aussi ce Passage.');
	assert.equal(texteDesinscription(duo([basile, jo]), 'j'),
		'Quitter ton Passage 21 · Titre de démonstration ? Il reste dans la File pour Basile.');
	assert.equal(texteInvitation(recue), "Basile t'invite à chanter, avec Célia.");
	assert.equal(texteInvitation({ ...recue, interpretes: [basile] }), "Basile t'invite à chanter.");
	assert.deepEqual(invitesPossibles([{ id: 'j' }, { id: 'b' }], 'j'), [{ id: 'b' }]);
	assert.deepEqual(invitesPossibles(undefined, 'j'), []);
});

test('Manette : l’invité accepte son Invitation, qui laisse place au ticket et à son reçu', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ invitationsRecues: [recue] }), capture); b.connecter('synchronisee');
	assert.match(b.principale.textContent, /Basile t'invite à chanter, avec Célia\./);
	assert.equal(b.principale.querySelector('.mon-ticket__etiquette').textContent, 'Invitation');
	bouton(b.principale, 'Accepter').click();
	assert.equal(b.appels.length, 1);
	assert.equal(b.appels[0].args[0], 'POST');
	assert.match(b.appels[0].args[1], /\/passages\/invite\/invitation$/);
	assert.equal(b.appels[0].args[2].reponse, 'ACCEPTER');
	assert.equal(b.appels[0].args[2].numeroModification, 0);
	assert.ok(b.appels[0].args[2].envoiId);
	assert.equal(bouton(b.principale, 'Refuser').disabled, true);
	assert.equal(bouton(b.principale, 'Accepter').className, 'bouton bouton--action');
	b.appels[0].resolve({ statut: 200, corps: { action: 'ACCEPTER' } }); await terminerMicrotaches();
	const accepte = { ...passage('invite', 22), interpretes: [basile, celia, jo] };
	b.module.afficher(vue({ invitationsRecues: [], mesPassagesEnAttente: [accepte] }), evenement);
	assert.equal(bouton(b.principale, 'Accepter'), undefined);
	assert.ok(bouton(b.principale, 'Quitter ce Passage'));
	assert.equal(bouton(b.principale, 'Me désinscrire'), undefined);
	assert.match(b.principale.textContent, /Envoi accepté par le serveur/);
});

test('Manette : un refus de limite garde l’Invitation ouverte avec son motif, puis Refuser part', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ invitationsRecues: [recue] }), capture); b.connecter('synchronisee');
	bouton(b.principale, 'Accepter').click();
	b.appels[0].resolve({ statut: 409, corps: { code: 'LIMITE_ATTEINTE', detail: 'Ta limite de Passages en attente est atteinte.' } });
	await terminerMicrotaches();
	b.module.afficher(vue({ invitationsRecues: [recue] }), capture);
	assert.match(b.principale.textContent, /Ta limite de Passages en attente est atteinte/);
	assert.equal(bouton(b.principale, 'Refuser').disabled, false);
	bouton(b.principale, 'Refuser').click();
	assert.equal(b.appels[1].args[2].reponse, 'REFUSER');
});

test('Manette : une vue reçue pendant que Refuser a le focus le laisse sur Refuser', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ invitationsRecues: [recue] }), capture); b.connecter('synchronisee');
	bouton(b.principale, 'Refuser').focus();
	b.module.afficher(vue({ invitationsRecues: [recue] }), evenement);
	assert.equal(b.document.activeElement, bouton(b.principale, 'Refuser'));
	bouton(b.principale, 'Accepter').focus();
	b.module.afficher(vue({ invitationsRecues: [recue] }), evenement);
	assert.equal(b.document.activeElement, bouton(b.principale, 'Accepter'));
});

test('Manette : chaque bouton d’Invitation nomme sa Chanson et qui invite', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const autre = { ...recue, passage: { passageId: 'autre', numeroModification: 0 }, inscrivant: celia, interpretes: [celia],
		chanson: { ...chanson, chansonId: 'autre', titre: 'Autre titre' } };
	b.module.afficher(vue({ invitationsRecues: [recue, autre] }), capture);
	const noms = texte => b.principale.querySelectorAll('button').filter(x => x.textContent === texte).map(x => x.getAttribute('aria-label'));
	assert.deepEqual(noms('Accepter'), ["Accepter l'Invitation de Basile pour Titre de démonstration",
		"Accepter l'Invitation de Célia pour Autre titre"]);
	assert.deepEqual(noms('Refuser'), ["Refuser l'Invitation de Basile pour Titre de démonstration",
		"Refuser l'Invitation de Célia pour Autre titre"]);
});

test('Manette : l’inscripteur voit l’état de chaque Invitation et sa confirmation nomme les autres', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ mesPassagesEnAttente: [duo()], invitationsEmises: [
		{ passageId: 'duo', invite: basile, etat: 'ACCEPTEE' }, { passageId: 'duo', invite: celia, etat: 'REFUSEE' }] }), capture);
	b.connecter('synchronisee');
	assert.deepEqual(b.principale.querySelector('.karaoke__etat-invitations').querySelectorAll('li').map(li => li.textContent),
		['Basile : a accepté', 'Célia : a refusé']);
	assert.equal(b.principale.querySelector('.karaoke__interpretes').textContent, 'Jo, Basile');
	bouton(b.principale, 'Me désinscrire').click();
	assert.match(b.principale.querySelector('dialog').textContent, /Basile perd aussi ce Passage/);
	bouton(b.principale, 'Confirmer la désinscription').click();
	assert.match(b.appels[0].args[1], /\/passages\/duo\/desinscription$/);
});

test('Manette : l’invité accepté quitte le Passage par la désinscription, sans le retirer aux autres', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ mesPassagesEnAttente: [duo([basile, jo])], invitationsEmises: [] }), capture);
	b.connecter('synchronisee');
	assert.equal(b.principale.querySelector('.karaoke__etat-invitations'), null);
	bouton(b.principale, 'Quitter ce Passage').click();
	assert.match(b.principale.querySelector('dialog').textContent, /Il reste dans la File pour Basile/);
	bouton(b.principale, 'Confirmer le départ').click();
	assert.match(b.appels[0].args[1], /\/passages\/duo\/desinscription$/);
	assert.equal(b.appels[0].args[2].numeroModification, 2);
});

test('Manette : la fiche coche trois invités au plus, sans soi, et n’ajoute invitesIds que s’il y en a', async t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const joueurs = ['j', 'b', 'c', 'd', 'e'].map(id => ({ id, pseudo: `Joueur ${id}`, connecte: true }));
	b.module.afficher({ ...vue(), joueurs }, capture); b.connecter('synchronisee');
	const champ = b.principale.querySelector('input'); champ.value = 'titre';
	b.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	b.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(b.principale, chanson.titre).click();
	b.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: true, motifIndisponibilite: null } }); await terminerMicrotaches();
	const cases = b.principale.querySelector('dialog').querySelectorAll('input');
	assert.deepEqual(cases.map(c => c.value), ['b', 'c', 'd', 'e']);
	for (const c of cases.slice(0, 3)) { c.checked = true; c.dispatchEvent({ type: 'change' }); }
	assert.equal(cases[3].disabled, true);
	bouton(b.principale, "M'inscrire").click();
	assert.deepEqual(b.appels[2].args[2].invitesIds, ['b', 'c', 'd']);

	const seul = creerBanc(manette);
	t.after(() => seul.module.demonter());
	seul.module.afficher({ ...vue(), joueurs: [{ id: 'j', pseudo: 'Jo', connecte: true }] }, capture); seul.connecter('synchronisee');
	const recherche = seul.principale.querySelector('input'); recherche.value = 'titre';
	seul.principale.querySelector('form').dispatchEvent({ type: 'submit' });
	seul.appels[0].resolve({ statut: 200, corps: { chansons: [chanson] } }); await terminerMicrotaches();
	bouton(seul.principale, chanson.titre).click();
	seul.appels[1].resolve({ statut: 200, corps: { chanson, inscriptionPossible: true, motifIndisponibilite: null } }); await terminerMicrotaches();
	assert.equal(seul.principale.querySelector('fieldset'), null);
	bouton(seul.principale, "M'inscrire").click();
	assert.equal('invitesIds' in seul.appels[2].args[2], false);
});

test('Manette : « Je suis prêt » garde seul l’action citron devant une Invitation', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ invitationsRecues: [recue, { ...recue, passage: { passageId: 'autre', numeroModification: 0 } }],
		peutConfirmerPresence: true }), capture);
	assert.deepEqual(b.principale.querySelectorAll('button').filter(x => x.textContent === 'Accepter').map(x => x.className),
		['bouton', 'bouton']);
	b.module.afficher(vue({ invitationsRecues: [recue, { ...recue, passage: { passageId: 'autre', numeroModification: 0 } }] }), capture);
	assert.deepEqual(b.principale.querySelectorAll('button').filter(x => x.textContent === 'Accepter').map(x => x.className),
		['bouton bouton--action', 'bouton']);
});

test('les pseudos se tronquent un par un et restent tous dans l’aria-label du ticket', t => {
	const b = creerBanc(manette);
	t.after(() => b.module.demonter());
	const longs = ['Alexandrine-Éléonore', 'Bartholomé-Maximilien', 'Clémentine-Apolline', 'Dieudonné-Marcellin']
		.map((pseudo, i) => ({ joueurId: i ? `x${i}` : 'j', pseudo }));
	b.module.afficher(vue({ mesPassagesEnAttente: [{ ...passage('groupe', 30), interpretes: longs }] }), capture);
	const ligne = b.principale.querySelector('.karaoke__interpretes');
	assert.deepEqual(ligne.querySelectorAll('.karaoke__pseudo').map(x => x.getAttribute('title')), longs.map(i => i.pseudo));
	assert.equal(ligne.textContent, longs.map(i => i.pseudo).join(', '));
	assert.match(b.principale.querySelector('.mon-ticket').getAttribute('aria-label'), /Dieudonné-Marcellin/);
});

test('Console : les invités sans réponse sont nommés à part des Interprètes', t => {
	const b = creerBanc(consoleKaraoke, 'ANIMATEUR');
	t.after(() => b.module.demonter());
	b.module.afficher(vue({ passagesEnAttente: [{ ...duo([jo]), position: 1 }],
		invitationsEnAttente: [{ passageId: 'duo', invite: basile }, { passageId: 'duo', invite: celia }] }), capture);
	const ticket = b.principale.querySelector('button.ticket');
	assert.equal(ticket.querySelector('.karaoke__interpretes').textContent, 'Jo');
	assert.match(ticket.textContent, /Invités sans réponse : Basile et Célia/);
	assert.match(ticket.getAttribute('aria-label'), /^Passage 21 · Jo · .* · Invités sans réponse : Basile et Célia$/);
});
