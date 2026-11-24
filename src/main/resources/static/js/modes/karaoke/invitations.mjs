// Règles d'affichage des Passages à plusieurs (#135), sans DOM : testées par node --test.

const ETATS = { EN_ATTENTE: 'pas encore répondu', ACCEPTEE: 'a accepté', REFUSEE: 'a refusé', EXPIREE: 'sans réponse' };

/** L'inscripteur est le premier Interprète du Passage. */
export const estInscripteur = (passage, joueurId) => passage.interpretes[0]?.joueurId === joueurId;

export function joindrePseudos(pseudos) {
	if (pseudos.length < 2) return pseudos.join('');
	return `${pseudos.slice(0, -1).join(', ')} et ${pseudos.at(-1)}`;
}

/** Une ligne par Invitation émise pour ce Passage, dans l'ordre d'émission. */
export function lignesInvitations(invitationsEmises, passageId) {
	return (invitationsEmises ?? []).filter(i => i.passageId === passageId)
		.map(i => `${i.invite.pseudo} : ${ETATS[i.etat] ?? i.etat}`);
}

/** Texte de confirmation de la désinscription, qui nomme les autres Interprètes (#139). */
export function texteDesinscription(passage, joueurId) {
	const titre = `ton Passage ${passage.numero} · ${passage.chanson.titre}`;
	const autres = passage.interpretes.filter(i => i.joueurId !== joueurId).map(i => i.pseudo);
	if (!estInscripteur(passage, joueurId)) {
		return `Quitter ${titre} ? Il reste dans la File pour ${joindrePseudos(autres)}.`;
	}
	if (!autres.length) return `Retirer ${titre} ?`;
	return `Retirer ${titre} ? ${joindrePseudos(autres)} ${autres.length > 1 ? 'perdent' : 'perd'} aussi ce Passage.`;
}

export function texteInvitation(invitation, verbe = 'chanter') {
	const avec = invitation.interpretes.filter(i => i.joueurId !== invitation.inscrivant.joueurId).map(i => i.pseudo);
	return `${invitation.inscrivant.pseudo} t'invite à ${verbe}${avec.length ? `, avec ${joindrePseudos(avec)}` : ''}.`;
}
