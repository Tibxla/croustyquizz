// Frontières réseau injectables ; aucun accès au DOM ni stockage d'identité ici.
export function creerTransport({ jeton, csrf } = {}, adaptations = {}) {
	const adresse = adaptations.adresse ?? globalThis.location?.href;
	const requete = adaptations.requete ?? ((...args) => fetch(...args));
	const entetesIdentite = () => ({
		...(jeton ? { Authorization: `Bearer ${jeton}` } : {}),
		...(csrf ? { 'X-CSRF-TOKEN': csrf } : {}),
	});
	async function envoyer(methode, chemin, corps, { entetes = {}, signal } = {}) {
		const url = new URL(chemin, adresse);
		if (url.origin !== new URL(adresse).origin) throw new Error('La requête doit rester sur CroustyQuizz.');
		const reponse = await requete(url, {
			method: methode, credentials: 'same-origin', cache: 'no-store', signal,
			headers: { Accept: 'application/json', ...entetes,
				...(corps === undefined ? {} : { 'Content-Type': 'application/json' }), ...entetesIdentite() },
			...(corps === undefined ? {} : { body: JSON.stringify(corps) }),
		});
		const texte = await reponse.text();
		return { statut: reponse.status, corps: texte ? JSON.parse(texte) : null };
	}
	return {
		envoyer,
		ouvrir({ surConnexion, surMessage, surDeconnexion, surErreur }) {
			const url = new URL('/ws', adresse);
			url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
			const Client = adaptations.Client ?? globalThis.StompJs.Client;
			const client = new Client({
				brokerURL: url.href, connectHeaders: entetesIdentite(), reconnectDelay: 0,
				connectionTimeout: 10000, heartbeatIncoming: 10000, heartbeatOutgoing: 10000,
				onConnect: surConnexion, onWebSocketClose: surDeconnexion,
				onStompError: surErreur, onWebSocketError: surErreur,
			});
			client.activate();
			return {
				abonner(destination, id) {
					client.subscribe(destination, trame => {
						try { surMessage(JSON.parse(trame.body)); } catch { surErreur(); }
					}, { id });
				},
				fermer() { void client.deactivate(); },
			};
		},
		capturer(route, abonnementId, signal) {
			const url = new URL(route, adresse);
			url.searchParams.set('abonnementId', abonnementId);
			return envoyer('GET', url, undefined, { signal });
		},
	};
}

// Compatibilité avec les pages du prototype, qui n'ont pas d'identité.
export const transportStomp = {
	ouvrir: rappels => creerTransport().ouvrir(rappels),
	capturer: (...args) => creerTransport().capturer(...args),
};
