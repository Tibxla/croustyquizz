const INCONNU = "Confirmation indisponible : vérifie l'état affiché.";

export function creerContexte({ acteur, soireeId, envoyer, resynchroniser, maintenantServeur, surEnvoi = () => {} }) {
	let etat = 'synchronisation';
	let dernierEnvoi = 0;
	let annonceEnVol = false;
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
			// annoncer(reponse) peut masquer l'annonce finale si le module affiche lui-même cette réponse.
			// effacerAnnonce() retire le statut d'un envoi fini, par exemple quand une saisie locale bloque l'envoi suivant ;
			// il laisse « Envoi en cours… » tant qu'une commande annoncée attend sa réponse.
			async envoyer(methode, chemin, corps, { annoncer = true, ...options } = {}) {
				if (!annoncer || ['GET', 'HEAD'].includes(methode.toUpperCase())) return envoyer(methode, chemin, corps, options);
				const numero = ++dernierEnvoi;
				annonceEnVol = true;
				surEnvoi('en-cours', 'Envoi en cours…');
				try {
					const reponse = await envoyer(methode, chemin, corps, options);
					if (numero === dernierEnvoi) {
						annonceEnVol = false;
						const { statut, corps } = reponse;
						// Même règle qu'envois.mjs : un 503, ou un 5xx sans code du contrat (relais, passerelle),
						// peut suivre une commande appliquée ; on ne peut donc pas l'annoncer comme un refus.
						if (typeof annoncer === 'function' && !annoncer(reponse)) surEnvoi(null);
						else if (statut === 503 || (statut >= 500 && !corps?.code)) surEnvoi('inconnu', INCONNU);
						else if (statut >= 200 && statut < 300) surEnvoi('accepte', 'Envoi accepté par le serveur.');
						else surEnvoi('refuse', corps?.detail ?? `Envoi refusé (HTTP ${statut}).`);
					}
					return reponse;
				} catch (erreur) {
					if (numero === dernierEnvoi) {
						annonceEnVol = false;
						if (erreur.name === 'AbortError') surEnvoi(null);
						else surEnvoi('inconnu', INCONNU);
					}
					throw erreur;
				}
			},
			effacerAnnonce() {
				if (!annonceEnVol) surEnvoi(null);
			},
		},
	};
}
