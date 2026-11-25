export function creerMusiqueDAttente({ lecteur, hasard = Math.random, surSonBloque = () => {} }) {
	let fichiers = [], index = 0, position = 0, autorisee = false, lecture;

	function liberer() {
		if (!lecture) return;
		if (lecture.chargee) position = lecteur.position();
		lecture = undefined;
		lecteur.detruire();
	}
	function traiterErreur(erreur) {
		liberer();
		if (erreur?.name === 'NotAllowedError') {
			surSonBloque();
			return;
		}
		// Un fichier illisible est écarté jusqu'au prochain choix du dossier.
		fichiers.splice(index, 1);
		if (index === fichiers.length) index = 0;
		position = 0;
		void jouer();
	}

	async function jouer() {
		if (!autorisee || !fichiers.length || lecture) return;
		const tentative = lecture = { chargee: false };
		try {
			await lecteur.charger(fichiers[index]);
			if (tentative !== lecture) return;
			tentative.chargee = true;
			if (position > 0) lecteur.positionner(position);
			await lecteur.lire();
		} catch (erreur) {
			if (tentative !== lecture) return;
			traiterErreur(erreur);
		}
	}

	const retirerFin = lecteur.surFin(() => {
		if (!lecture) return;
		liberer();
		index = (index + 1) % fichiers.length;
		position = 0;
		void jouer();
	});
	const retirerErreur = lecteur.surErreur(erreur => { if (lecture) traiterErreur(erreur); });

	return {
		choisirFichiers(nouveauxFichiers) {
			liberer();
			fichiers = [...nouveauxFichiers];
			for (let i = fichiers.length - 1; i > 0; i--) {
				const j = Math.floor(hasard() * (i + 1));
				[fichiers[i], fichiers[j]] = [fichiers[j], fichiers[i]];
			}
			index = position = 0;
			void jouer();
		},
		autoriserLecture(valeur) {
			autorisee = valeur;
			if (autorisee) void jouer();
			else liberer();
		},
		detruire() {
			autorisee = false;
			liberer();
			retirerFin();
			retirerErreur();
		},
	};
}
