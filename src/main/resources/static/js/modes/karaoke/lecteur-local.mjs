// WAV PCM mono silencieux de 10 ms. Le geste de déblocage ne touche pas au fichier du Passage.
const SILENCE = 'data:audio/wav;base64,UklGRnQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YVAAAACAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgA==';

export function creerLecteurLocal({ document, creerUrl, revoquerUrl }) {
	const audio = document.createElement('audio');
	audio.preload = 'metadata';
	const fins = new Set(), erreurs = new Set();
	let temoin, libererFichier = () => {};

	function abonner(rappels, rappel) {
		rappels.add(rappel);
		return () => rappels.delete(rappel);
	}
	const lire = () => audio.play();
	return {
		debloquer() {
			if (!temoin) {
				temoin = document.createElement('audio');
				temoin.src = SILENCE;
			}
			return temoin.play();
		},
		charger(fichier) {
			libererFichier();
			return new Promise((resolve, reject) => {
				const url = creerUrl(fichier);
				let enAttente = true;
				function metadonnees() {
					enAttente = false;
					resolve({ dureeMs: Number.isFinite(audio.duration) && audio.duration > 0 ? Math.round(audio.duration * 1000) : null });
				}
				function fin() { for (const rappel of fins) rappel(); }
				function erreur() {
					if (enAttente) {
						enAttente = false;
						reject(new Error('Fichier audio illisible.'));
					} else for (const rappel of erreurs) rappel();
				}
				audio.addEventListener('loadedmetadata', metadonnees, { once: true });
				audio.addEventListener('ended', fin);
				audio.addEventListener('error', erreur);
				libererFichier = () => {
					libererFichier = () => {};
					audio.removeEventListener('loadedmetadata', metadonnees);
					audio.removeEventListener('ended', fin);
					audio.removeEventListener('error', erreur);
					audio.pause();
					audio.removeAttribute('src');
					audio.load();
					revoquerUrl(url);
					if (enAttente) resolve({ dureeMs: null });
				};
				audio.src = url;
			});
		},
		lire,
		pause: () => audio.pause(),
		reprendre: lire,
		position: () => Math.round(audio.currentTime * 1000),
		volume: valeur => { audio.volume = valeur; },
		surFin: rappel => abonner(fins, rappel),
		surErreur: rappel => abonner(erreurs, rappel),
		// Le port peut être réutilisé lors de la Manche suivante.
		detruire: () => {
			libererFichier();
			temoin?.pause();
		},
	};
}
