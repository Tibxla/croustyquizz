export function monterSignalSonEcran(audio, contexte) {
	let courant;
	const route = `/api/soirees/${contexte.soireeId}/son-ecran`;

	async function signaler() {
		const connexion = courant;
		const { sonDebloque } = audio.etat();
		if (!connexion || connexion.enCours || connexion.dernierSignal === sonDebloque) return;
		connexion.enCours = true;
		connexion.dernierSignal = sonDebloque;
		try {
			await contexte.envoyer('PUT', route,
				{ abonnementId: connexion.abonnementId, sonActive: sonDebloque },
				{ signal: connexion.fin.signal, annoncer: false });
		} catch {
			// Le prochain changement de son ou la prochaine connexion signalera l’état courant.
		} finally {
			connexion.enCours = false;
			if (courant === connexion) void signaler();
		}
	}

	const seDesabonner = audio.surChangement(() => { void signaler(); });
	function synchroniser(abonnementId) {
		if (courant?.abonnementId === abonnementId) return;
		courant?.fin.abort();
		courant = abonnementId ? { abonnementId, fin: new AbortController() } : undefined;
		void signaler();
	}

	return {
		synchroniser,
		demonter() { synchroniser(); seDesabonner(); },
	};
}

export function afficherAlerteSonEcran(statut, vue) {
	const texte = vue.sonEcran === 'COUPE' ? 'Le son de l’Écran est coupé : touche Activer le son sur la télé.' : '';
	if (statut.textContent !== texte) statut.textContent = texte;
}
