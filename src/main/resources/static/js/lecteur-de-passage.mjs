export function creerLecteurDePassage({ lecteur, sonde, attendreSonDeManche }) {
	let cible, actif = false;
	return {
		debloquer: lecteur.debloquer,
		async charger(fichier) {
			const preparation = cible = { fichier };
			try { return await sonde.charger(fichier); }
			finally { if (cible === preparation) sonde.detruire(); }
		},
		async lire() {
			const preparation = cible;
			const autorise = await attendreSonDeManche();
			if (!preparation || preparation !== cible) return;
			if (!autorise) throw new Error('Le lecteur de la Manche est indisponible.');
			actif = true;
			await Promise.all([lecteur.charger(preparation.fichier), lecteur.lire()]);
		},
		pause: lecteur.pause,
		reprendre: lecteur.reprendre,
		position: lecteur.position,
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
