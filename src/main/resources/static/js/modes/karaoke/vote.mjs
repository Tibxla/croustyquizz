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
export const formaterNombreVotes = nombre => `${nombre} vote${nombre > 1 ? 's' : ''}`;

export function delaiVoteRestant(vote, maintenant, enPause) {
	return delaiAppelRestant({ delaiAppelRestantMs: vote.delaiRestantMs, calculeLe: vote.calculeLe, maintenant, enPause });
}
