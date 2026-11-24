export const METHODES_LECTEUR_DE_CHANSON = Object.freeze([
	'debloquer', 'charger', 'lire', 'pause', 'reprendre', 'position', 'volume', 'surFin', 'surErreur', 'detruire',
]);

export function estLecteurDeChanson(objet) {
	return METHODES_LECTEUR_DE_CHANSON.every(methode => typeof objet?.[methode] === 'function');
}
