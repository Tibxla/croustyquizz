export const teintePassage = numero => ['orange', 'eau', 'bleu', 'rose'][numero % 4];

export function formaterDuree(dureeMs) {
	if (dureeMs === null) return 'Durée inconnue';
	const secondes = Math.floor(dureeMs / 1000);
	return `${Math.floor(secondes / 60)}:${String(secondes % 60).padStart(2, '0')}`;
}

export function formaterAttente(attente, personnel = false) {
	if (attente.etat === 'SUSPENDUE') return 'Attente suspendue';
	if (attente.etat !== 'ESTIMEE') return 'Attente à confirmer';
	if (attente.dureeMs === 0) return personnel ? 'Tu passes ensuite.' : 'Ensuite';
	if (attente.dureeMs < 60000) return "Dans moins d'une minute environ";
	const minutes = Math.ceil(attente.dureeMs / 60000);
	return `Dans environ ${minutes} minute${minutes > 1 ? 's' : ''}`;
}

export const tableMotifsKaraoke = Object.freeze({
	SANS_VOIX: 'Cette Chanson est sans voix.',
	PAROLES_NON_SYNCHRONISEES: 'Il faut des paroles synchronisées.',
	DUREE_INCONNUE: 'La durée manque.',
	ANIMATEUR: 'Consultation uniquement',
	PASSAGE_DEJA_INSCRIT: 'Tu attends déjà cette Chanson.',
	LIMITE_ATTEINTE: 'Ta limite de Passages est atteinte.',
});

export function etatInscription(fiche, bloc, synchronisee) {
	const code = bloc.motifInscriptionRefusee ?? fiche.motifIndisponibilite;
	return { possible: Boolean(!code && fiche.inscriptionPossible && bloc.peutInscrire && synchronisee),
		motif: code ? (Object.hasOwn(tableMotifsKaraoke, code) ? tableMotifsKaraoke[code] : code) : null };
}
