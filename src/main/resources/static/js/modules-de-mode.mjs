const dossiers = { QUIZ: 'quiz', BLIND_TEST: 'blindtest', KARAOKE: 'karaoke', FREESTYLE: 'freestyle' };

export function blocDuMode(mode) { return dossiers[mode]; }

export function cheminDuModule(mode, ecran) {
	if (!Object.hasOwn(dossiers, mode) || !['manette', 'console', 'ecran'].includes(ecran)) {
		throw new Error('Mode de jeu ou écran inconnu.');
	}
	return `./modes/${dossiers[mode]}/${ecran}.mjs`;
}

// Une importation tardive ne doit jamais monter un ancien mode.
export function creerGestionnaireDeMode({ ecran, zones, contexte, charger = chemin => import(chemin),
	surErreur = () => {} }) {
	let mode;
	let instance;
	let derniere;
	let generation = 0;
	let chargement;
	let arrete = false;
	function vider() {
		const ancienne = instance;
		instance = undefined;
		ancienne?.demonter();
		for (const zone of Object.values(zones)) zone.replaceChildren();
	}
	return {
		afficher(vue, origine) {
			if (arrete) return Promise.resolve();
			derniere = { vue, origine };
			// Bloc du mode explicitement nul (Manche terminée) : aucun module n'est monté, et le mode
			// est oublié pour que la Manche suivante du même mode remonte un module neuf.
			if (vue.modeEnCours && vue[blocDuMode(vue.modeEnCours)] === null) {
				if (mode !== undefined) { generation++; vider(); }
				mode = undefined;
				chargement = undefined;
				return Promise.resolve();
			}
			if (mode === vue.modeEnCours) {
				if (instance) {
					try { instance.afficher(vue, origine); } catch (erreur) { surErreur(erreur); }
				}
				return chargement ?? Promise.resolve();
			}
			mode = vue.modeEnCours;
			const tour = ++generation;
			vider();
			if (mode === null) return Promise.resolve();
			chargement = (async () => {
				try {
					const module = await charger(cheminDuModule(mode, ecran));
					if (arrete || tour !== generation) return;
					instance = module.monter(zones, contexte);
					instance.afficher(derniere.vue, derniere.origine);
				} catch (erreur) {
					if (tour === generation && !arrete) {
						vider();
						mode = undefined; // Une prochaine capture peut réessayer.
						surErreur(erreur);
					}
				}
			})();
			return chargement;
		},
		demonter() { arrete = true; generation++; vider(); },
	};
}
