import { creerTicket, texteEnvoi } from '../karaoke/commun.mjs';
import { peutReessayer } from '../karaoke/envois.mjs';
import { texteInvitation } from '../karaoke/invitations.mjs';

// Invitations reçues : le contenu, qui invite, et deux gestes, Accepter ou Refuser (#138). Commun aux Modes à Passages.
export function installerInvitations(ui, { verbe = 'chanter' } = {}) {
	const { e, bouton } = ui;
	const titreInvitations = e('h2', '', 'Invitations'); titreInvitations.tabIndex = -1;
	const invitations = e('div', 'karaoke__invitations');
	titreInvitations.hidden = invitations.hidden = true;
	ui.principale.append(titreInvitations, invitations);
	function rendre(titre) {
		const recues = ui.bloc.invitationsRecues ?? [];
		titreInvitations.hidden = invitations.hidden = !recues.length;
		const fiches = recues.map((invitation, rang) => {
			const cible = invitation.passage;
			const ticket = creerTicket(ui, { ...invitation, passageId: cible.passageId }, { personnel: true, courant: true });
			ticket.querySelector('.mon-ticket__etiquette').textContent = 'Invitation';
			const bas = ticket.querySelector('.mon-ticket__bas');
			const envoi = ui.dernierEnvoi(e => e.commande.type === 'REPONDRE_INVITATION' && e.commande.passageId === cible.passageId);
			const repondre = reponse => ui.commande({ type: 'REPONDRE_INVITATION', methode: 'POST',
				chemin: `${ui.racine()}/passages/${encodeURIComponent(cible.passageId)}/invitation`,
				corps: { numeroModification: cible.numeroModification, reponse }, mancheId: ui.bloc.mancheId,
				acteurType: 'JOUEUR', passageId: cible.passageId });
			// Une seule action citron : « Je suis prêt » et la note à donner passent avant la première Invitation.
			const autreAction = rang > 0 || ui.bloc.peutConfirmerPresence || (ui.bloc.votes ?? []).some(vote => vote.peutVoter);
			const accepter = bouton('Accepter', () => repondre('ACCEPTER'), autreAction ? 'bouton' : 'bouton bouton--action');
			const refuser = bouton('Refuser', () => repondre('REFUSER'));
			for (const action of [accepter, refuser]) {
				action.setAttribute('data-passage-id', cible.passageId);
				action.disabled = !ui.mutationsPermises || envoi?.etat === 'en-cours' || Boolean(peutReessayer(envoi));
			}
			const actions = e('div', 'karaoke__commandes'); actions.append(accepter, refuser);
			const etat = e('p', '', texteEnvoi(envoi)); etat.setAttribute('role', 'status');
			bas.append(e('p', 'karaoke__texte-invitation', texteInvitation(invitation, verbe)), actions, etat);
			if (ui.reessaiPossible(envoi)) bas.append(bouton('Réessayer le même envoi', () => ui.reessayer(envoi.cle)));
			return ticket;
		});
		ui.remplacerListe(invitations, fiches, titre);
	}
	return { rendre };
}
