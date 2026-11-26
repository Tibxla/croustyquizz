// `surMorceau` reçoit le morceau qui joue, puis null quand il se tait : l'Écran de salle en affiche le crédit.
export function creerMusiqueDAttente({ lecteur, hasard = Math.random, surSonBloque = () => {}, surMorceauEcarte = () => {},
	surMorceau = () => {} }) {
	let fichiers = [], index = 0, position = 0, autorisee = false, lecture, annonce = null;

	function annoncer(morceau) {
		if (annonce === morceau) return;
		annonce = morceau;
		surMorceau(morceau);
	}

	function liberer() {
		if (!lecture) return;
		if (lecture.chargee) position = lecteur.position();
		lecture = undefined;
		lecteur.detruire();
		annoncer(null);
	}
	function passerAuSuivant() {
		if (!lecture) return;
		liberer();
		index = (index + 1) % fichiers.length;
		position = 0;
		void jouer();
	}
	function traiterErreur(erreur, echecDeChargement = false) {
		if (erreur?.name === 'NotAllowedError') {
			liberer();
			surSonBloque();
			return;
		}
		if (!echecDeChargement) {
			passerAuSuivant();
			return;
		}
		liberer();
		// Un fichier qui ne se charge pas est écarté jusqu'au prochain choix du dossier.
		const [ecarte] = fichiers.splice(index, 1);
		if (index === fichiers.length) index = 0;
		position = 0;
		surMorceauEcarte(ecarte, fichiers.length);
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
			if (tentative === lecture) annoncer(fichiers[index]);
		} catch (erreur) {
			if (tentative !== lecture) return;
			traiterErreur(erreur, !tentative.chargee);
		}
	}

	const retirerFin = lecteur.surFin(passerAuSuivant);
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
