// Connexion temps réel partagée par toutes les pages.
// À charger après /webjars/stomp__stompjs/bundles/stomp.umd.min.js.
// Chaque message reçu est une enveloppe {type, donnees} (voir le package tempsreel).
const TempsReel = (() => {
	const canaux = {
		salle: (soireeId) => `/topic/soirees/${soireeId}/salle`,
		console: (soireeId) => `/topic/soirees/${soireeId}/console`,
		joueur: (joueurId) => `/topic/joueurs/${joueurId}`,
	};

	// surConnexion est rappelé à chaque (re)connexion : c'est là qu'on s'abonne.
	function connecter({ surConnexion, surDeconnexion } = {}) {
		const protocole = location.protocol === 'https:' ? 'wss:' : 'ws:';
		const client = new StompJs.Client({
			brokerURL: `${protocole}//${location.host}/ws`,
			reconnectDelay: 2000,
		});

		const connexion = {
			// gestionnaires : {"buzz-recu": (donnees) => ..., ...}
			ecouter(destination, gestionnaires) {
				return client.subscribe(destination, (trame) => {
					const enveloppe = JSON.parse(trame.body);
					gestionnaires[enveloppe.type]?.(enveloppe.donnees);
				});
			},
			envoyer(destination, corps) {
				client.publish({ destination, body: JSON.stringify(corps) });
			},
			estConnectee: () => client.connected,
		};

		client.onConnect = () => surConnexion?.(connexion);
		client.onWebSocketClose = () => surDeconnexion?.();
		client.activate();
		return connexion;
	}

	// crypto.randomUUID n'existe qu'en HTTPS ou sur localhost.
	function nouvelIdentifiant() {
		if (window.crypto?.randomUUID) {
			return crypto.randomUUID();
		}
		return '10000000-1000-4000-8000-100000000000'.replace(/[018]/g, (c) =>
			(c ^ (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))).toString(16));
	}

	return { canaux, connecter, nouvelIdentifiant };
})();
