import { delaiAppelRestant } from './paroles.mjs';

export function etatVoteManette(vote, { joueurId, enPause }) {
	if (vote.etat === 'CLOS') return 'clos';
	if (vote.interpretes.some(i => i.joueurId === joueurId)) return 'interprete';
	if (vote.maNote != null) return 'enregistre';
	return enPause ? 'pause' : 'a-voter';
}

export function corpsVote({ envoiId, vote, note }) {
	return { envoiId, numeroModification: vote.numeroModification, note };
}

export const formaterNote = note => `${note} sur 5`;
export const formaterMoyenne = moyenneDixiemes => formaterNote((moyenneDixiemes / 10).toFixed(1).replace('.', ','));
export const formaterNombreVotes = nombre => nombre === 0 ? 'Aucun vote' : `${nombre} vote${nombre > 1 ? 's' : ''}`;

export function votesVisiblesSalle(bloc, maintenant) {
	const duree = bloc.configurationInterface.dureeAffichageMoyenneMs;
	return (bloc.votes ?? []).filter(vote => vote.etat === 'OUVERT' || !vote.suivantDemarre
		|| maintenant < Date.parse(vote.clotureLe) + duree).slice(0, 2);
}

export function delaiVoteRestant(vote, maintenant, enPause) {
	return delaiAppelRestant({ delaiAppelRestantMs: vote.delaiRestantMs, calculeLe: vote.calculeLe, maintenant, enPause });
}
