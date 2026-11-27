import { delaiAppelRestant } from './paroles.mjs';
export { formaterNote, formaterMoyenne, formaterNombreVotes } from '../../vue-commune.mjs';

export function etatVoteManette(vote, { joueurId, enPause }) {
	if (vote.etat === 'CLOS') return 'clos';
	if ((vote.interpretes ?? [vote.interprete]).some(i => i.joueurId === joueurId)) return 'interprete';
	if (vote.maNote != null || vote.monChoix != null) return 'enregistre';
	return enPause ? 'pause' : 'a-voter';
}

export function corpsVote({ envoiId, vote, note }) {
	return { envoiId, numeroModification: vote.numeroModification, note };
}

export function votesVisiblesSalle(bloc, maintenant) {
	const duree = bloc.configurationInterface.dureeAffichageMoyenneMs;
	return (bloc.votes ?? []).filter(vote => vote.etat === 'OUVERT' || !vote.suivantDemarre
		|| maintenant < Date.parse(vote.clotureLe) + duree).slice(0, 2);
}

export function delaiVoteRestant(vote, maintenant, enPause) {
	return delaiAppelRestant({ delaiAppelRestantMs: vote.delaiRestantMs, calculeLe: vote.calculeLe, maintenant, enPause });
}
