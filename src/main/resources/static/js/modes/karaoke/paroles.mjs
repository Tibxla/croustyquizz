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
