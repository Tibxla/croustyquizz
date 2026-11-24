const PREFIXE = 'croustyquizz:son:';
const PRESENCE = `${PREFIXE}fenetre:`;
const AUDIO = `${PREFIXE}lecture`;

export function creerCoordinationSon({ verrous, canal, ouverteA, id, surAttente }) {
	const candidature = `${PRESENCE}${ouverteA.toFixed(3).padStart(18, '0')}:${id}`;
	let presence, attente, manche, surveillance, revision = 0, fermeture;

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
		const premiere = held.filter(verrou => verrou.mode === 'exclusive' && verrou.name.startsWith(PRESENCE))
			.map(verrou => verrou.name).sort().at(-1);
		surveiller(premiere);
		const sonDeManche = [...held, ...pending].some(verrou => verrou.name === AUDIO && verrou.mode === 'shared');
		if (premiere !== candidature || sonDeManche) {
			taireAttente();
		} else if (!attente) {
			const nouvelle = attente = prendre(AUDIO, 'exclusive');
			if (await nouvelle.prise && attente === nouvelle) surAttente(true);
		}
	}
	function signalerChangement() {
		if (fermeture) return;
		canal.postMessage(null);
		void actualiser();
	}
	canal.onmessage = () => { void actualiser(); };

	return {
		activer(sonActive) {
			if (fermeture || sonActive === Boolean(presence)) return;
			if (sonActive) {
				presence = prendre(candidature, 'exclusive');
				void presence.prise.then(signalerChangement);
			} else {
				revision++;
				surveiller();
				taireAttente();
				const ancienne = presence;
				presence = undefined;
				void ancienne.liberer().then(signalerChangement);
			}
		},
		signalerSonDeManche(actif) {
			if (fermeture || actif === Boolean(manche)) return;
			revision++;
			if (actif) {
				taireAttente();
				// La lecture de Manche attend que la fenêtre audible ait rendu le verrou.
				manche = prendre(AUDIO, 'shared');
				void verrous.query().then(signalerChangement);
			} else {
				const ancienne = manche;
				manche = undefined;
				void ancienne.liberer().then(signalerChangement);
			}
		},
		attendreSonDeManche: () => manche?.prise ?? Promise.resolve(false),
		detruire() {
			if (fermeture) return fermeture;
			revision++;
			surveiller();
			fermeture = Promise.all([taireAttente(), manche?.liberer(), presence?.liberer()]).then(() => {
				canal.postMessage(null);
				canal.close();
			});
			return fermeture;
		},
	};
}
