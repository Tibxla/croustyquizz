export function creerContexte({ acteur, soireeId, envoyer, resynchroniser, surEnvoi = () => {} }) {
	let etat = 'synchronisation';
	let dernierEnvoi = 0;
	const abonnes = new Set();
	return {
		changerEtat(nouveau) {
			etat = nouveau;
			for (const rappel of abonnes) rappel(etat);
		},
		contexte: {
			acteur, soireeId, resynchroniser,
			surEtatConnexion(rappel) {
				abonnes.add(rappel);
				rappel(etat);
				return () => abonnes.delete(rappel);
			},
			// surEnvoi(etat, detail) pilote le bandeau de la coquille ; surEnvoi(null) l'efface.
			// { annoncer: false } : le module affiche son propre statut près de l'action, la coquille se tait.
			async envoyer(methode, chemin, corps, { annoncer = true, ...options } = {}) {
				if (!annoncer || ['GET', 'HEAD'].includes(methode.toUpperCase())) return envoyer(methode, chemin, corps, options);
				const numero = ++dernierEnvoi;
				surEnvoi('en-cours', 'Envoi en cours…');
				try {
					const reponse = await envoyer(methode, chemin, corps, options);
					if (numero === dernierEnvoi) {
						const accepte = reponse.statut >= 200 && reponse.statut < 300;
						surEnvoi(accepte ? 'accepte' : 'refuse', accepte ? 'Envoi accepté par le serveur.' :
							(reponse.corps?.detail ?? `Envoi refusé (HTTP ${reponse.statut}).`));
					}
					return reponse;
				} catch (erreur) {
					if (numero === dernierEnvoi) {
						if (erreur.name === 'AbortError') surEnvoi(null);
						else surEnvoi('inconnu', "Confirmation indisponible : vérifie l'état affiché.");
					}
					throw erreur;
				}
			},
		},
	};
}
