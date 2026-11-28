const PREFIXE = 'croustyquizz:son:';

export function creerCoordinationSon({ verrous, canal, ouverteA, id, soireeId, surAttente, memoireLecteur }) {
	const portee = soireeId.toLowerCase();
	const presenceDeSoiree = `${PREFIXE}fenetre:${portee}:`;
	const verrouAudio = `${PREFIXE}lecture:${portee}`;
	const verrouLecteur = lecteurId => `${PREFIXE}lecteur:${portee}:${lecteurId}`;
	// Chaque fenêtre qui a des morceaux tient ce verrou, même sans son activé.
	const prefixeMorceaux = `${PREFIXE}morceaux:${portee}:`;
	const nomVerrouMorceaux = `${prefixeMorceaux}${id}`;
	const nomVerrouPublication = `${PREFIXE}publication:${portee}`;
	// Le rang passe avant l'ouverture : une fenêtre qui joue le dossier du Lieu (rang 1) l'emporte sur celles
	// qui n'ont que la Bibliothèque de base (rang 0), puis la plus récente de chaque rang (ADR 0011).
	const nomCandidature = rang => `${presenceDeSoiree}${rang}:${ouverteA.toFixed(3).padStart(18, '0')}:${id}`;
	let candidature = nomCandidature(0);
	let presence, attente, manche, surveillance, revision = 0, fermeture, verrouMorceaux, liberationMorceaux;

	function prendre(nom, mode) {
		const controle = new AbortController();
		const prise = Promise.withResolvers(), fin = Promise.withResolvers();
		const libere = verrous.request(nom, { mode, signal: controle.signal }, () => {
			prise.resolve(true);
			return fin.promise;
		}).catch(() => prise.resolve(false));
		return {
			prise: prise.promise,
			liberer() { controle.abort(); fin.resolve(); return libere; },
		};
	}
	// Le verrou reste pris tant que l'Écran existe, même entre ses Manches ou sans Musique d'attente.
	const lecteurPresent = prendre(verrouLecteur(id), 'exclusive');
	function taireAttente() {
		if (!attente) return;
		const ancienne = attente;
		attente = undefined;
		surAttente(false);
		return ancienne.liberer();
	}
	function surveiller(nom) {
		if (surveillance?.nom === nom) return;
		surveillance?.controle.abort();
		surveillance = undefined;
		if (!nom || nom === candidature) return;
		const controle = new AbortController();
		const tour = surveillance = { nom, controle };
		// Le navigateur libère la présence même si la fenêtre ferme sans envoyer de message.
		void verrous.request(nom, { mode: 'shared', signal: controle.signal }, () => {}).then(() => {
			if (surveillance !== tour) return;
			surveillance = undefined;
			void actualiser();
		}, () => {});
	}
	async function actualiser() {
		const tour = ++revision;
		if (!presence || manche || fermeture) return;
		const { held, pending } = await verrous.query();
		if (tour !== revision) return;
		const premiere = held.filter(verrou => verrou.mode === 'exclusive' && verrou.name.startsWith(presenceDeSoiree))
			.map(verrou => verrou.name).sort().at(-1);
		surveiller(premiere);
		const sonDeManche = [...held, ...pending].some(verrou => verrou.name === verrouAudio && verrou.mode === 'shared');
		if (premiere !== candidature || sonDeManche) {
			taireAttente();
		} else if (!attente) {
			const nouvelle = attente = prendre(verrouAudio, 'exclusive');
			if (await nouvelle.prise && attente === nouvelle) surAttente(true);
		}
	}
	function signalerChangement() {
		if (fermeture) return;
		canal.postMessage(null);
		void actualiser();
	}
	async function autresFenetresAvecMorceaux() {
		const { held, pending } = await verrous.query();
		return new Set([...held, ...pending].filter(verrou => verrou.mode === 'exclusive'
			&& verrou.name.startsWith(prefixeMorceaux) && verrou.name !== nomVerrouMorceaux).map(verrou => verrou.name));
	}
	canal.onmessage = () => { void actualiser(); };

	return {
		memoriserDesignation() { memoireLecteur?.retenir(id); },
		async attendreDisparitionLecteur(lecteurId, signal) {
			const nom = verrouLecteur(lecteurId);
			const { held, pending } = await verrous.query();
			signal.throwIfAborted();
			// Un lecteur inconnu des Web Locks peut encore jouer dans un autre navigateur. Seule une
			// présence constatée ou une désignation mémorisée dans cet onglet permet de conclure à sa disparition.
			if (memoireLecteur?.lire() !== lecteurId
				&& ![...held, ...pending].some(verrou => verrou.name === nom && verrou.mode === 'exclusive')) return false;
			await verrous.request(nom, { mode: 'shared', signal }, () => {});
			return true;
		},
		activer(sonActive, prioritaire = false) {
			const voulue = sonActive ? nomCandidature(prioritaire ? 1 : 0) : undefined;
			if (fermeture || voulue === (presence ? candidature : undefined)) return;
			let liberee = Promise.resolve();
			if (presence) {
				revision++;
				surveiller();
				taireAttente();
				const ancienne = presence;
				presence = undefined;
				liberee = ancienne.liberer();
				if (!voulue) void liberee.then(signalerChangement);
			}
			if (voulue) {
				candidature = voulue;
				const nouvelle = presence = prendre(candidature, 'exclusive');
				void liberee.then(() => nouvelle.prise).then(signalerChangement);
			}
		},
		signalerSonDeManche(actif) {
			if (fermeture || actif === Boolean(manche)) return;
			revision++;
			if (actif) {
				taireAttente();
				// La lecture de Manche attend que la fenêtre audible ait rendu le verrou.
				manche = prendre(verrouAudio, 'shared');
				void verrous.query().then(signalerChangement);
			} else {
				const ancienne = manche;
				manche = undefined;
				void ancienne.liberer().then(signalerChangement);
			}
		},
		attendreSonDeManche: () => manche?.prise ?? Promise.resolve(false),
		annoncerMorceaux(disponibles) {
			if (fermeture || disponibles === Boolean(verrouMorceaux)) return;
			if (disponibles) {
				verrouMorceaux = prendre(nomVerrouMorceaux, 'exclusive');
			} else {
				liberationMorceaux = verrouMorceaux.liberer();
				verrouMorceaux = undefined;
			}
		},
		async publierDisponibilite(morceauxDisponibles, signal, publier) {
			// Si deux fenêtres perdent leurs morceaux ensemble, chacune rend le sien avant de regarder les autres.
			if (!morceauxDisponibles) await liberationMorceaux;
			for (;;) {
				const autres = await verrous.request(nomVerrouPublication, { mode: 'exclusive', signal }, async () => {
					const avecMorceaux = morceauxDisponibles ? null : await autresFenetresAvecMorceaux();
					signal.throwIfAborted();
					if (avecMorceaux?.size) return avecMorceaux;
					await publier();
				});
				if (!autres) return;
				// Attendre hors du verrou de publication, puis revérifier en le reprenant.
				// Le navigateur libère aussi les verrous des fenêtres qui se ferment.
				await Promise.all([...autres].map(nom => verrous.request(nom, { mode: 'shared', signal }, () => {})));
				signal.throwIfAborted();
			}
		},
		detruire() {
			if (fermeture) return fermeture;
			revision++;
			surveiller();
			fermeture = Promise.all([
				taireAttente(), manche?.liberer(), presence?.liberer(), verrouMorceaux?.liberer(), lecteurPresent.liberer(),
			]).then(() => {
				canal.postMessage(null);
				canal.close();
			});
			return fermeture;
		},
	};
}
