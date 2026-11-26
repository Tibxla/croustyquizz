const route = soireeId => `/api/soirees/${soireeId}/musique-attente`;

function decrireMusique({ etat, morceauxDisponibles }) {
	if (morceauxDisponibles === false) return 'Aucun morceau dans Attente';
	if (etat === 'COUPEE') return 'Musique d’attente coupée';
	return morceauxDisponibles ? 'La musique d’attente joue entre les sons de Manche' : 'Dossier d’attente pas encore signalé';
}

export function monterMusiqueDAttenteConsole(bouton, statut, contexte) {
	const fin = new AbortController();
	let musique, synchronisee = false, enCours = false;

	function actualiserBouton() {
		bouton.disabled = !musique || !synchronisee || enCours;
	}
	const seDesabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		actualiserBouton();
	});

	bouton.addEventListener('click', async () => {
		if (bouton.disabled) return;
		const etat = musique.etat === 'JOUE' ? 'COUPEE' : 'JOUE';
		enCours = true;
		actualiserBouton();
		try {
			const reponse = await contexte.envoyer('PUT', route(contexte.soireeId), { etat }, { signal: fin.signal });
			if (!fin.signal.aborted && reponse.statut === 200 && musique?.etat !== etat) {
				contexte.resynchroniser();
			}
		} catch {
			// Le bandeau de la coquille annonce le résultat de l’envoi.
		} finally {
			enCours = false;
			if (!fin.signal.aborted) actualiserBouton();
		}
	}, { signal: fin.signal });

	return {
		afficher(vue) {
			musique = vue.musiqueAttente;
			if (musique) {
				bouton.textContent = musique.etat === 'JOUE' ? 'Couper la musique' : 'Relancer la musique';
				const texte = decrireMusique(musique);
				if (statut.textContent !== texte) statut.textContent = texte;
			}
			actualiserBouton();
		},
		demonter() { fin.abort(); seDesabonner(); },
	};
}

export function monterMusiqueDAttenteSalle(audio, contexte) {
	const fin = new AbortController();
	let synchronisee = false, affichage, dernierDossier, dernierSignal, aSignaler, enCours = false, absenceEnAttente;

	function actualiserLecture() {
		const courant = affichage;
		if (!synchronisee || courant?.etat !== 'JOUE') {
			audio.reglerMusiqueDAttente(false);
			return;
		}
		void courant.modeAffiche.then(() => {
			if (!fin.signal.aborted && synchronisee && affichage === courant) audio.reglerMusiqueDAttente(true);
		});
	}

	async function signaler() {
		if (fin.signal.aborted || !synchronisee || enCours || aSignaler === undefined) return;
		const morceauxDisponibles = aSignaler, absence = absenceEnAttente;
		aSignaler = undefined;
		enCours = true;
		try {
			const signal = absence?.signal ?? fin.signal;
			await audio.publierDisponibilite(morceauxDisponibles, signal, () => {
				signal.throwIfAborted();
				if (!synchronisee) {
					aSignaler ??= morceauxDisponibles;
					return;
				}
				return contexte.envoyer('PUT', route(contexte.soireeId) + '/disponibilite', { morceauxDisponibles },
					{ signal: fin.signal, annoncer: false });
			});
		} catch {
			// Un signal perdu n'est pas renvoyé : le prochain changement du dossier ou de ses morceaux en donnera un nouveau.
		} finally {
			enCours = false;
			void signaler();
		}
	}

	const retirerDossier = audio.surChangement(({ fichiers, morceauxDisponibles }) => {
		// Le même dossier peut perdre ses morceaux quand la Musique d'attente les écarte comme illisibles. Sans dossier,
		// la Bibliothèque de base suffit à signaler des morceaux ; sans l'un ni l'autre, la fenêtre ne signale rien (ADR 0011).
		if (morceauxDisponibles == null || (fichiers === dernierDossier && morceauxDisponibles === dernierSignal)) return;
		dernierDossier = fichiers;
		dernierSignal = morceauxDisponibles;
		absenceEnAttente?.abort();
		absenceEnAttente = morceauxDisponibles ? undefined : new AbortController();
		aSignaler = morceauxDisponibles;
		void signaler();
	});
	const seDesabonner = contexte.surEtatConnexion(etat => {
		synchronisee = etat === 'synchronisee';
		if (!synchronisee) affichage = undefined;
		actualiserLecture();
		void signaler();
	});

	return {
		afficher(vue, modeAffiche = Promise.resolve()) {
			affichage = { etat: vue.musiqueAttente?.etat, modeAffiche };
			actualiserLecture();
		},
		demonter() { fin.abort(); absenceEnAttente?.abort(); retirerDossier(); seDesabonner(); },
	};
}
