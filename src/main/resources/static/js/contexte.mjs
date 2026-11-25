const INCONNU = "Confirmation indisponible : vérifie l'état affiché.";

export function creerContexte({ acteur, soireeId, envoyer, resynchroniser, maintenantServeur, surEnvoi = () => {} }) {
	let etat = 'synchronisation';
	let dernierEnvoi = 0;
	const abonnes = new Set();
	return {
		changerEtat(nouveau) {
			etat = nouveau;
			for (const rappel of abonnes) rappel(etat);
		},
		contexte: {
			acteur, soireeId, resynchroniser, maintenantServeur,
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
						const { statut, corps } = reponse;
						// Même règle qu'envois.mjs : un 503, ou un 5xx sans code du contrat (relais, passerelle),
						// peut suivre une commande appliquée ; on ne peut donc pas l'annoncer comme un refus.
						if (statut === 503 || (statut >= 500 && !corps?.code)) surEnvoi('inconnu', INCONNU);
						else if (statut >= 200 && statut < 300) surEnvoi('accepte', 'Envoi accepté par le serveur.');
						else surEnvoi('refuse', corps?.detail ?? `Envoi refusé (HTTP ${statut}).`);
					}
					return reponse;
				} catch (erreur) {
					if (numero === dernierEnvoi) {
						if (erreur.name === 'AbortError') surEnvoi(null);
						else surEnvoi('inconnu', INCONNU);
					}
					throw erreur;
				}
			},
		},
	};
}
