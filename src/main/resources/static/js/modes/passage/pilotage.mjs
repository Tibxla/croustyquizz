import { passageActif } from '../karaoke/presentation.mjs';

export const prochainPassage = bloc => bloc.passagesEnAttente.find(passage => passage.position === 1);

export function appelerSuivant(ui, commande) {
	const passage = prochainPassage(ui.bloc);
	if (!passage) return;
	ui.commande(commande('POST', `/passages/${encodeURIComponent(passage.passageId)}/appel`,
		{ numeroModification: passage.numeroModification }, { type: 'APPELER', passageId: passage.passageId }));
}

export function piloter(ui, commande, action) {
	const courant = passageActif(ui.bloc.courant);
	ui.commande(commande('POST', '/commandes', { action, ...(courant ? { etapeId: courant.etapeId } : {}) }, { type: 'PILOTER' }));
}
