// Connexion temps réel partagée par toutes les pages.
// À charger après /webjars/stomp__stompjs/bundles/stomp.umd.min.js.
// Chaque message reçu est une enveloppe {type, donnees} (voir le package tempsreel).
const TempsReel = (() => {
	async function requete(url, corps) {
		const reponse = await fetch(url, corps === undefined ? {} : {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(corps),
		});
		if (!reponse.ok) {
			throw new Error(`Requête refusée par le serveur (HTTP ${reponse.status}).`);
		}
		return reponse.json();
	}

	function chargerCanaux(soireeId) {
		return requete(`/api/test/soirees/${encodeURIComponent(soireeId)}/canaux`);
	}

	// surConnexion est rappelé à chaque (re)connexion : c'est là qu'on s'abonne.
	function connecter({ canaux, surConnexion, surDeconnexion }) {
		const protocole = location.protocol === 'https:' ? 'wss:' : 'ws:';
		const client = new StompJs.Client({
			brokerURL: `${protocole}//${location.host}${canaux.connexion}`,
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

	return { chargerCanaux, requete, connecter, nouvelIdentifiant };
})();
