// Module ES sans dépendance. Le client STOMP réel est chargé par la page.
// Les pages ne manipulent que des vues complètes, jamais les Révisions.
export function suivreVue({ destination, routeEtat, surVue, surEtat = () => {} }, adaptations = {}) {
	const {
		transport = transportStomp,
		nouvelIdentifiant = creerUuid,
		maintenant = () => performance.now(),
		programmer = (rappel, delai) => setTimeout(rappel, delai),
		annuler = (minuterie) => clearTimeout(minuterie),
		delaiReconnexion = 2000,
		delaiTentative = 250,
		nombreTentatives = 5,
		capaciteTampon = 64,
		delaiCapture = 10000,
	} = adaptations;
	let arrete = false;
	let courant;
	let minuterie;
	let heureCapture;
	let instantCapture;

	function actif(suivi) {
		return !arrete && courant === suivi;
	}

	function nettoyer() {
		const ancien = courant;
		courant = undefined;
		annuler(minuterie);
		ancien?.annulation.abort();
		ancien?.connexion?.fermer();
	}

	function recommencer(suivi) {
		if (!actif(suivi)) return;
		nettoyer();
		surEtat('reconnexion');
		if (!arrete) minuterie = programmer(ouvrir, delaiReconnexion);
	}

	function verifierRevision(revision) {
		if (!Number.isSafeInteger(revision) || revision < 0) throw new Error('Révision invalide');
	}

	function appliquer(suivi, message) {
		if (message.revision <= suivi.revision) return;
		if (message.revision !== suivi.revision + 1) throw new Error('Révision manquante');
		surVue(message.donnees, { type: message.type });
		suivi.revision = message.revision;
	}

	function recevoir(suivi, message) {
		if (!actif(suivi)) return;
		try {
			verifierRevision(message.revision);
			if (typeof message.type !== 'string' || !message.donnees) throw new Error('Vue absente');
			if (suivi.revision === undefined) {
				if (suivi.tampon.length >= capaciteTampon) throw new Error('Tampon plein');
				suivi.tampon.push(message);
			} else appliquer(suivi, message);
		} catch {
			recommencer(suivi);
		}
	}

	async function capturer(suivi, tentative = 1) {
		try {
			minuterie = programmer(() => recommencer(suivi), delaiCapture);
			const { statut, corps } = await transport.capturer(routeEtat, suivi.id, suivi.annulation.signal);
			if (!actif(suivi)) return;
			annuler(minuterie);
			if (statut === 409 && corps.code === 'ABONNEMENT_INACTIF' && tentative < nombreTentatives) {
				minuterie = programmer(() => {
					if (actif(suivi)) void capturer(suivi, tentative + 1);
				}, delaiTentative);
				return;
			}
			if (statut !== 200 || corps.abonnementId !== suivi.id) throw new Error('Capture incompatible');
			verifierRevision(corps.revision);
			const heure = Date.parse(corps.heureServeur);
			if (!Number.isFinite(heure) || !corps.vue) throw new Error('Capture invalide');
			heureCapture = heure;
			instantCapture = maintenant();
			surVue(corps.vue, { type: 'capture' });
			suivi.revision = corps.revision;
			for (const message of suivi.tampon.sort((a, b) => a.revision - b.revision)) {
				if (!actif(suivi)) return;
				appliquer(suivi, message);
			}
			suivi.tampon = [];
			if (actif(suivi)) surEtat('synchronisee');
		} catch {
			recommencer(suivi);
		}
	}

	function ouvrir() {
		if (arrete) return;
		const suivi = { id: nouvelIdentifiant(), tampon: [], annulation: new AbortController() };
		courant = suivi;
		suivi.connexion = transport.ouvrir({
			surConnexion() {
				if (!actif(suivi)) return;
				try {
					suivi.connexion.abonner(destination, suivi.id);
					if (actif(suivi)) void capturer(suivi);
				} catch {
					recommencer(suivi);
				}
			},
			surMessage: (message) => recevoir(suivi, message),
			surDeconnexion: () => recommencer(suivi),
			surErreur: () => recommencer(suivi),
		});
	}

	surEtat('synchronisation');
	ouvrir();
	return {
		arreter() {
			arrete = true;
			nettoyer();
		},
		maintenantServeur: () => heureCapture === undefined ? null : heureCapture + maintenant() - instantCapture,
	};
}

function creerUuid() {
	if (globalThis.crypto.randomUUID) return globalThis.crypto.randomUUID();
	// crypto.randomUUID n'est disponible qu'en contexte sécurisé, contrairement à getRandomValues.
	return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (chiffre) =>
		(chiffre ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (chiffre / 4)))).toString(16));
}

const transportStomp = {
	ouvrir({ surConnexion, surMessage, surDeconnexion, surErreur }) {
		const protocole = location.protocol === 'https:' ? 'wss:' : 'ws:';
		const client = new globalThis.StompJs.Client({
			brokerURL: `${protocole}//${location.host}/ws`,
			reconnectDelay: 0, // Une reprise appartient toujours à un nouvel abonnement.
			connectionTimeout: 10000,
			heartbeatIncoming: 10000,
			heartbeatOutgoing: 10000,
			onConnect: surConnexion,
			onWebSocketClose: surDeconnexion,
			onStompError: surErreur,
			onWebSocketError: surErreur,
		});
		client.activate();
		return {
			abonner(destination, id) {
				client.subscribe(destination, (trame) => {
					try { surMessage(JSON.parse(trame.body)); } catch { surErreur(); }
				}, { id });
			},
			fermer() { void client.deactivate(); },
		};
	},
	async capturer(route, abonnementId, signal) {
		const url = new URL(route, location.href);
		url.searchParams.set('abonnementId', abonnementId);
		const reponse = await fetch(url, { signal, cache: 'no-store' });
		return { statut: reponse.status, corps: await reponse.json() };
	},
};
