/**
 * Le lecteur fournit la position du fichier en millisecondes, introduction comprise.
 * `phase` vaut `avant` avant le départ du beat, `pendant` sur la Grille et `apres` une fois la Grille finie.
 */
export function positionDansGrille(grille, { tempoBpm, departBeatMs }, positionMs) {
	const dureeTemps = 60_000 / tempoBpm;
	const ecoule = positionMs - departBeatMs;
	const numeroTemps = Math.floor((ecoule + 0.5) / dureeTemps);
	const horsGrille = phase => ({ phase, mesure: null, temps: null, progression: 0, mot: null });
	if (!Number.isFinite(numeroTemps) || ecoule < 0) return horsGrille('avant');
	if (numeroTemps >= grille.nombreMesures * 4) return horsGrille('apres');
	const mesure = Math.floor(numeroTemps / 4) + 1;
	const temps = numeroTemps % 4 + 1;
	const motDeLaMesure = grille.mots[mesure - 1];
	const mot = temps === motDeLaMesure.temps && positionMs >= motDeLaMesure.positionMs ? motDeLaMesure.mot : null;
	const progression = Math.max(0, Math.min(1, ecoule / dureeTemps - numeroTemps));
	return { phase: 'pendant', mesure, temps, progression, mot };
}
