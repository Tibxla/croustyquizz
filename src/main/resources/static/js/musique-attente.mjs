export function creerMusiqueDAttente({ lecteur, hasard = Math.random, surSonBloque = () => {}, surMorceauxEpuises = () => {} }) {
	let fichiers = [], index = 0, position = 0, autorisee = false, lecture;
	const erreursDeLecture = new Set();

	function liberer() {
		if (!lecture) return;
		if (lecture.chargee) position = lecteur.position();
		lecture = undefined;
		lecteur.detruire();
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
		const fichier = fichiers[index];
		// Écarter un fichier dès l’échec du chargement ou après deux erreurs de lecture sans fin de lecture entre les deux.
		if (!echecDeChargement && !erreursDeLecture.has(fichier)) {
			erreursDeLecture.add(fichier);
			passerAuSuivant();
			return;
		}
		liberer();
		erreursDeLecture.delete(fichier);
		fichiers.splice(index, 1);
		if (index === fichiers.length) index = 0;
		position = 0;
		if (fichiers.length) void jouer();
		else surMorceauxEpuises();
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
			traiterErreur(erreur, !tentative.chargee);
		}
	}

	const retirerFin = lecteur.surFin(() => {
		if (!lecture) return;
		erreursDeLecture.delete(fichiers[index]);
		passerAuSuivant();
	});
	const retirerErreur = lecteur.surErreur(erreur => { if (lecture) traiterErreur(erreur); });

	return {
		choisirFichiers(nouveauxFichiers) {
			liberer();
			erreursDeLecture.clear();
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
