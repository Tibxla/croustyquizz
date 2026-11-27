// Règles d'affichage du Freestyle à plusieurs (#74), sans DOM : testées par node --test. Le serveur tire l'ordre
// des Couplets et décide de l'annonce ; les écrans ne font que les dire.

export const pseudos = interpretes => (interpretes ?? []).map(i => i.pseudo).join(', ');

export function texteOrdre(couplets) {
	return couplets ? `Ordre des Couplets : ${couplets.ordre.map(i => i.pseudo).join(' · ')}` : '';
}

export function texteCouplet(couplets) {
	if (!couplets) return '';
	const { courant, nombreCouplets } = couplets;
	return `Couplet ${courant.numero} sur ${nombreCouplets} · mesures ${courant.premiereMesure} à ${courant.derniereMesure}`;
}

/** Une mesure avant le changement, la salle nomme l'Interprète suivant. */
export function texteAnnonce(couplets) {
	return couplets?.suivantAnnonce && couplets.suivant ? `À ${couplets.suivant.interprete.pseudo} dans une mesure` : '';
}

/**
 * La Manette vibre une seule fois par annonce : à l'événement couplet-annonce, si le serveur la désigne
 * (monCoupletAnnonce), jamais sur une capture ni deux fois pour le même Couplet.
 */
export function cleVibration(bloc, origine) {
	const couplets = bloc?.courant?.couplets;
	if (origine?.source !== 'evenement' || origine.type !== 'couplet-annonce' || !bloc.monCoupletAnnonce || !couplets?.suivant) {
		return null;
	}
	return `${bloc.courant.etapeId}:${couplets.suivant.numero}`;
}
