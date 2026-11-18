// Aucun protocole simulé : seulement les frontières réseau, temps et UUID.
export function creerAdaptateurPilotable(identifiants = []) {
	const connexions = [];
	const requetes = [];
	const minuteries = new Map();
	let numero = 0;
	let instant = 0;
	let numeroMinuterie = 0;
	const adaptations = {
		nouvelIdentifiant: () => identifiants[numero++] ?? `00000000-0000-4000-8000-${String(numero).padStart(12, '0')}`,
		maintenant: () => instant,
		programmer(rappel, delai) {
			const id = ++numeroMinuterie;
			minuteries.set(id, { rappel, echeance: instant + delai, delai });
			return id;
		},
		annuler: (id) => minuteries.delete(id),
		transport: {
			ouvrir(rappels) {
				const connexion = {
					...rappels,
					abonnements: [],
					fermee: false,
					abonner(destination, abonnementId) { this.abonnements.push({ destination, abonnementId }); },
					fermer() { this.fermee = true; },
				};
				connexions.push(connexion);
				return connexion;
			},
			capturer(route, abonnementId, signal) {
				return new Promise((resoudre, rejeter) => requetes.push({ route, abonnementId, signal, resoudre, rejeter }));
			},
		},
	};
	return {
		adaptations, connexions, requetes, minuteries,
		async avancer(duree) {
			instant += duree;
			for (const [id, minuterie] of [...minuteries]) {
				if (minuterie.echeance <= instant) {
					minuteries.delete(id);
					minuterie.rappel();
				}
			}
			await viderPromesses();
		},
	};
}

export async function viderPromesses() {
	// Les captures passent par await transport puis installation de la vue.
	await Promise.resolve();
	await Promise.resolve();
}
