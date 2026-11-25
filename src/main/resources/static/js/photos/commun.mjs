export const routePhotos = soireeId => `/api/soirees/${soireeId}/photos`;

export function element(document, balise, attributs = {}, texte) {
	const noeud = document.createElement(balise);
	for (const [nom, valeur] of Object.entries(attributs)) noeud.setAttribute(nom, valeur);
	if (texte !== undefined) noeud.textContent = texte;
	return noeud;
}

export function creerImage(image, contexte, { urlObjet = blob => URL.createObjectURL(blob),
	libererUrl = url => URL.revokeObjectURL(url), surErreur = () => {} } = {}) {
	let requete, url;
	function effacer() {
		requete?.abort();
		requete = null;
		image.removeAttribute('src');
		if (url) libererUrl(url);
		url = null;
	}
	return {
		effacer,
		async charger(photoId) {
			effacer();
			const controleur = new AbortController();
			requete = controleur;
			try {
				const blob = await contexte.chargerImage(`${routePhotos(contexte.soireeId)}/${photoId}`, { signal: controleur.signal });
				if (controleur.signal.aborted) return false;
				url = urlObjet(blob);
				image.setAttribute('src', url);
				return true;
			} catch {
				if (!controleur.signal.aborted) surErreur();
				return false;
			}
		},
	};
}
