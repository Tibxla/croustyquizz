const instant = date => typeof date === 'number' ? date : Date.parse(date);

export function determinerLignes(lignes, positionMs, decalageMs) {
	const indexEnCours = lignes.findLastIndex(ligne => ligne.texte.trim() && positionMs >= ligne.positionMs + decalageMs);
	return { indexEnCours, avantPremiere: indexEnCours === -1 };
}

export function delaiAppelRestant({ delaiAppelRestantMs, calculeLe, maintenant, enPause }) {
	return enPause ? delaiAppelRestantMs : Math.max(0, delaiAppelRestantMs - (instant(maintenant) - instant(calculeLe)));
}

export function positionChronologique({ positionChronologiqueMs, calculeLe, maintenant, enPause, dureeReferenceMs }) {
	return enPause ? positionChronologiqueMs :
		Math.min(dureeReferenceMs, Math.max(0, positionChronologiqueMs + instant(maintenant) - instant(calculeLe)));
}

export function tempsAvantFinPrevue({ finPrevueLe, maintenant }) {
	return Math.max(0, instant(finPrevueLe) - instant(maintenant));
}

// Sur la salle (#276), une ligne chantée à 4,4vw tient environ 24 caractères par ligne. Au-delà, elle passe sur
// deux lignes : une seule voisine suit. Au-delà de deux lignes, elle passe en corps réduit et perd aussi la ligne
// déjà chantée, pour que la ligne en cours reste entière.
const LIGNE_LONGUE = 24, LIGNE_TRES_LONGUE = 48;

export function fenetreParoles(lignes, indexEnCours, avantPremiere) {
	if (avantPremiere) return { debut: 0, fin: 3, longueur: 'courte' };
	const taille = lignes[indexEnCours].texte.trim().length;
	const longueur = taille > LIGNE_TRES_LONGUE ? 'tres-longue' : taille > LIGNE_LONGUE ? 'longue' : 'courte';
	const debut = longueur === 'tres-longue' ? indexEnCours : Math.max(0, indexEnCours - 1);
	return { debut, fin: indexEnCours + (longueur === 'courte' ? 3 : 2), longueur };
}
