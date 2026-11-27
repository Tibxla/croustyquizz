export function monterBuzz(bouton, contexte) {
	let disponible = false;
	let synchronisee = false;
	let envoiId;
	let requete;
	const route = `/api/soirees/${contexte.soireeId}/buzz`;

	function actualiser() {
		bouton.hidden = !disponible;
		bouton.disabled = !disponible || !synchronisee || Boolean(requete);
		bouton.textContent = envoiId && !requete ? 'Réessayer le Buzz' : 'Buzzer';
	}

	async function buzzer() {
		if (bouton.disabled) return;
		envoiId ??= crypto.randomUUID();
		requete = new AbortController();
		actualiser();
		try {
			const { statut, corps } = await contexte.envoyer('POST', route, { envoiId }, { signal: requete.signal, recu: 'Buzz envoyé.' });
			if (statut === 503 || (statut >= 500 && !corps?.code)) contexte.resynchroniser();
			else envoiId = undefined;
		} catch (erreur) {
			if (erreur.name !== 'AbortError') contexte.resynchroniser();
		} finally {
			requete = undefined;
			actualiser();
		}
	}

	const quitter = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		actualiser();
	});
	bouton.addEventListener('click', buzzer);
	return {
		afficher(vue) {
			disponible = vue.modeEnCours === null && ['OUVERTE', 'EN_COURS'].includes(vue.soiree.etat);
			actualiser();
		},
		demonter() {
			requete?.abort();
			quitter();
			bouton.removeEventListener('click', buzzer);
		},
	};
}
