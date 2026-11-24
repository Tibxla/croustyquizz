export function creerLecteurDePassage({ lecteur, sonde, attendreSonDeManche }) {
	let cible, actif = false;
	return {
		debloquer: lecteur.debloquer,
		async charger(fichier) {
			const preparation = cible = { fichier };
			try { return await sonde.charger(fichier); }
			finally { if (cible === preparation) sonde.detruire(); }
		},
		async lire(positionInitiale) {
			const preparation = cible;
			const autorise = await attendreSonDeManche();
			if (!preparation || preparation !== cible) return;
			if (!autorise) throw new Error('Le lecteur de la Manche est indisponible.');
			actif = true;
			if (positionInitiale === undefined) {
				await Promise.all([lecteur.charger(preparation.fichier), lecteur.lire()]);
				return;
			}
			await lecteur.charger(preparation.fichier);
			if (preparation !== cible) return;
			const position = positionInitiale();
			if (position === null) return;
			lecteur.positionner(position);
			await lecteur.lire();
		},
		pause: lecteur.pause,
		reprendre: lecteur.reprendre,
		position: lecteur.position,
		positionner: lecteur.positionner,
		volume: lecteur.volume,
		surFin: rappel => lecteur.surFin(() => { if (actif) rappel(); }),
		surErreur: rappel => lecteur.surErreur(() => { if (actif) rappel(); }),
		detruire() {
			cible = undefined;
			sonde.detruire();
			if (!actif) return;
			actif = false;
			lecteur.detruire();
		},
	};
}
