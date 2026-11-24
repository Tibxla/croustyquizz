/** Le lecteur fournit la position du fichier en millisecondes, introduction comprise. */
export function positionDansGrille(grille, { tempoBpm, departBeatMs }, positionMs) {
	const dureeTemps = 60_000 / tempoBpm;
	const ecoule = positionMs - departBeatMs;
	const numeroTemps = Math.floor((ecoule + 0.5) / dureeTemps);
	if (!Number.isFinite(numeroTemps) || ecoule < 0 || numeroTemps >= grille.nombreMesures * 4) {
		return { mesure: null, temps: null, progression: 0, mot: null };
	}
	const mesure = Math.floor(numeroTemps / 4) + 1;
	const temps = numeroTemps % 4 + 1;
	const motDeLaMesure = grille.mots[mesure - 1];
	const mot = temps === motDeLaMesure.temps && positionMs >= motDeLaMesure.positionMs ? motDeLaMesure.mot : null;
	const progression = Math.max(0, Math.min(1, ecoule / dureeTemps - numeroTemps));
	return { mesure, temps, progression, mot };
}
